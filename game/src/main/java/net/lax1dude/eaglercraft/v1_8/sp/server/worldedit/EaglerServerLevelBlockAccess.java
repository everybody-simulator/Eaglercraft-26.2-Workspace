package net.lax1dude.eaglercraft.v1_8.sp.server.worldedit;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The native Minecraft-side boundary for the WorldEdit integration.
 *
 * This deliberately accepts and returns Minecraft BlockState objects. Conversion to and from
 * WorldEdit's independent block registry belongs in the WorldEdit platform adapter, not here.
 * Keeping this seam native ensures that reads and writes reach the real ServerLevel/LevelChunk
 * implementation instead of a parallel block store.
 */
public final class EaglerServerLevelBlockAccess {

    private EaglerServerLevelBlockAccess() {
    }

    /**
     * Read the real block state from a loaded/generated server world.
     *
     * WorldEdit edit operations are expected to run on the integrated server thread. This method
     * enforces that rule rather than allowing accidental asynchronous world access.
     */
    public static BlockState getBlockState(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        requireServerThread(level);
        requireBuildHeight(level, position);
        return level.getBlockState(position);
    }

    /**
     * Write a real block state through Level.setBlock, including normal neighbor and client
     * update behavior (flag 3). Returns Minecraft's write result.
     */
    public static boolean setBlockState(ServerLevel level, BlockPos position, BlockState state) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        requireServerThread(level);
        requireBuildHeight(level, position);
        return level.setBlock(position, state, 3);
    }

    /**
     * Live-world smoke test for the native access seam.
     *
     * The caller must supply an air position in a disposable/test area. The method refuses to
     * overwrite a non-air block, writes stone, reads it back from the real world, and restores
     * the original air state in a finally block.
     */
    public static boolean verifyReadWrite(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        requireServerThread(level);
        requireBuildHeight(level, position);

        BlockState original = level.getBlockState(position);
        if (!original.isAir()) {
            throw new IllegalArgumentException("Smoke-test position must be air; refusing to overwrite it");
        }

        BlockState probe = Blocks.STONE.defaultBlockState();
        if (!level.setBlock(position, probe, 3)) {
            return false;
        }

        boolean readBackMatches;
        try {
            readBackMatches = level.getBlockState(position).equals(probe);
        } finally {
            level.setBlock(position, original, 3);
        }

        return readBackMatches && level.getBlockState(position).equals(original);
    }

    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("WorldEdit world access must run on the integrated server thread");
        }
    }

    private static void requireBuildHeight(ServerLevel level, BlockPos position) {
        if (position.getY() < level.getMinY() || position.getY() >= level.getMaxY()) {
            throw new IllegalArgumentException("Block position is outside this dimension's build height: " + position);
        }
    }
}
