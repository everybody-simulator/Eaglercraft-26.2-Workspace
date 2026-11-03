package net.lax1dude.eaglercraft.v1_8.sp.server.worldedit;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Narrow, native access seam between WorldEdit and a real Minecraft server
 * level. This class deliberately stores and returns Minecraft BlockState
 * instances; it does not translate blocks to numeric IDs or maintain a second
 * world representation.
 *
 * <p>All reads and writes are required to run on the owning server thread.
 * Higher-level WorldEdit scheduling must marshal work onto that thread before
 * calling this adapter.</p>
 */
public final class EaglerServerLevelBlockAccess {

    /** Vanilla update flags: notify clients and run neighbor updates. */
    public static final int DEFAULT_UPDATE_FLAGS = 3;

    private final ServerLevel level;

    public EaglerServerLevelBlockAccess(final ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    public ServerLevel getLevel() {
        return level;
    }

    /**
     * Read the actual block state from the loaded Minecraft world.
     *
     * @throws IllegalArgumentException if the position is outside valid world bounds
     * @throws IllegalStateException if called outside the server thread
     */
    public BlockState getBlockState(final BlockPos position) {
        requireUsablePosition(position);
        return level.getBlockState(position);
    }

    /**
     * Write an actual Minecraft block state through Level#setBlock, preserving
     * Minecraft's normal chunk, lighting, neighbor and client-update behavior.
     *
     * @return whether Minecraft accepted the change
     */
    public boolean setBlockState(final BlockPos position, final BlockState state) {
        Objects.requireNonNull(state, "state");
        requireUsablePosition(position);
        return level.setBlock(position, state, DEFAULT_UPDATE_FLAGS);
    }

    /**
     * A reversible live-world probe. It writes the requested state, reads it
     * back from the real Level, and restores the exact original state in a
     * finally block. Use a probe state different from the current block.
     *
     * <p>This must only be run in a disposable/test location because normal
     * Minecraft block update side effects are intentionally enabled.</p>
     */
    public boolean runBlockRoundTripSmokeTest(
            final BlockPos position,
            final BlockState probeState
    ) {
        Objects.requireNonNull(probeState, "probeState");
        requireUsablePosition(position);

        final BlockState original = level.getBlockState(position);
        if (original.equals(probeState)) {
            return false;
        }

        try {
            if (!level.setBlock(position, probeState, DEFAULT_UPDATE_FLAGS)) {
                return false;
            }
            return probeState.equals(level.getBlockState(position));
        } finally {
            // Avoid a needless no-op write when placement failed before changing the block.
            if (!original.equals(level.getBlockState(position))) {
                final boolean restored = level.setBlock(position, original, DEFAULT_UPDATE_FLAGS);
                if (!restored && !original.equals(level.getBlockState(position))) {
                    throw new IllegalStateException(
                            "WorldEdit smoke test could not restore the original block at " + position
                    );
                }
            }
        }
    }

    private void requireUsablePosition(final BlockPos position) {
        Objects.requireNonNull(position, "position");
        requireServerThread();
        if (!level.isInValidBounds(position)) {
            throw new IllegalArgumentException("Block position is outside valid world bounds: " + position);
        }
    }

    private void requireServerThread() {
        final MinecraftServer server = level.getServer();
        if (server == null) {
            throw new IllegalStateException("ServerLevel has no owning MinecraftServer");
        }
        if (!server.isSameThread()) {
            throw new IllegalStateException("Minecraft world access must run on the server thread");
        }
    }
}
