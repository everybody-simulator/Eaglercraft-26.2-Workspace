package net.lax1dude.eaglercraft.v1_8.sp.server.worldedit;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.biome.Biome;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.AbstractWorld;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import com.sk89q.worldedit.world.generation.TreeType;

/**
 * Initial WorldEdit World adapter for a real Minecraft ServerLevel.
 *
 * <p>Block reads and writes use Minecraft's native world and block-state
 * objects. Operations that would currently lose block-entity NBT are rejected
 * rather than silently copying or pasting incomplete inventories/sign data.
 * Entity spawning, item drops, and biome writes are not implemented yet.</p>
 */
public final class EaglerWorldEditWorld extends AbstractWorld {

    private final ServerLevel level;
    private final EaglerServerLevelBlockAccess blockAccess;

    public EaglerWorldEditWorld(final ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
        this.blockAccess = new EaglerServerLevelBlockAccess(level);
    }

    public ServerLevel getServerLevel() {
        return level;
    }

    @Override
    public String getName() {
        return level.dimension().identifier().toString();
    }

    @Override
    public String id() {
        return getName();
    }

    @Override
    public int getMinY() {
        return level.getMinY();
    }

    @Override
    public int getMaxY() {
        return level.getMaxY();
    }

    @Override
    public BlockVector3 getMinimumPoint() {
        return BlockVector3.at(-30000000, getMinY(), -30000000);
    }

    @Override
    public BlockVector3 getMaximumPoint() {
        // Minecraft's max build height is exclusive; WorldEdit bounds are inclusive.
        return BlockVector3.at(29999999, getMaxY() - 1, 29999999);
    }

    @Override
    public com.sk89q.worldedit.world.block.BlockState getBlock(final BlockVector3 position) {
        return WorldEditBlockStateConverter.toWorldEdit(blockAccess.getBlockState(toMinecraftPosition(position)));
    }

    @Override
    public BaseBlock getFullBlock(final BlockVector3 position) {
        final net.minecraft.world.level.block.state.BlockState state =
                blockAccess.getBlockState(toMinecraftPosition(position));
        if (state.hasBlockEntity()) {
            throw new UnsupportedOperationException(
                    "Block-entity NBT snapshots are not implemented yet at " + position
            );
        }
        return WorldEditBlockStateConverter.toWorldEdit(state).toBaseBlock();
    }

    @Override
    public <B extends BlockStateHolder<B>> boolean setBlock(
            final BlockVector3 position,
            final B block,
            final SideEffectSet sideEffects
    ) throws WorldEditException {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(sideEffects, "sideEffects");

        final BaseBlock fullBlock = block.toBaseBlock();
        if (!fullBlock.getNbtId().isEmpty()) {
            throw new UnsupportedOperationException(
                    "Block-entity NBT placement is not implemented yet at " + position
            );
        }

        final net.minecraft.world.level.block.state.BlockState minecraftState =
                WorldEditBlockStateConverter.toMinecraft(block.toImmutableState());
        return blockAccess.setBlockState(toMinecraftPosition(position), minecraftState);
    }

    @Override
    public Set<SideEffect> applySideEffects(
            final BlockVector3 position,
            final com.sk89q.worldedit.world.block.BlockState previousType,
            final SideEffectSet sideEffectSet
    ) {
        // Level#setBlock is used for every write, so vanilla lighting, neighbor
        // notifications, and client updates have already been performed.
        return Set.of();
    }

    @Override
    public int getBlockLightLevel(final BlockVector3 position) {
        return level.getBrightness(LightLayer.BLOCK, toMinecraftPosition(position));
    }

    @Override
    public boolean clearContainerBlockContents(final BlockVector3 position) {
        final BlockEntity blockEntity = level.getBlockEntity(toMinecraftPosition(position));
        if (blockEntity instanceof Container container) {
            container.clearContent();
            return true;
        }
        return false;
    }

    @Override
    public void dropItem(final Vector3 position, final BaseItemStack item, final int count) {
        throw new UnsupportedOperationException("WorldEdit item dropping is not implemented yet");
    }

    @Override
    public void dropItem(final Vector3 position, final BaseItemStack item) {
        throw new UnsupportedOperationException("WorldEdit item dropping is not implemented yet");
    }

    @Override
    public void simulateBlockMine(final BlockVector3 position) {
        // WorldEdit edits are direct state changes; no mining animation is required.
    }

    @Override
    public boolean generateTree(
            final TreeType type,
            final EditSession editSession,
            final BlockVector3 position
    ) throws MaxChangedBlocksException {
        return false;
    }

    @Override
    public void checkLoadedChunk(final BlockVector3 position) {
        blockAccess.getBlockState(toMinecraftPosition(position));
    }

    @Override
    public BiomeType getBiome(final BlockVector3 position) {
        final Holder<Biome> biome = level.getBiome(toMinecraftPosition(position));
        final String id = biome.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("minecraft:plains");
        final BiomeType type = BiomeTypes.get(id);
        return type != null ? type : Objects.requireNonNull(BiomeTypes.get("minecraft:plains"));
    }

    @Override
    public boolean setBiome(final BlockVector3 position, final BiomeType biome) {
        return false;
    }

    @Override
    public BlockVector3 getSpawnPosition() {
        final BlockPos spawn = level.getLevelData().getRespawnData().pos();
        return BlockVector3.at(spawn.getX(), spawn.getY(), spawn.getZ());
    }

    @Override
    public List<? extends Entity> getEntities(final Region region) {
        return List.of();
    }

    @Override
    public List<? extends Entity> getEntities() {
        return List.of();
    }

    @Override
    public Entity createEntity(final Location location, final BaseEntity entity) {
        return null;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof EaglerWorldEditWorld world && world.level == level;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(level);
    }

    private static BlockPos toMinecraftPosition(final BlockVector3 position) {
        Objects.requireNonNull(position, "position");
        return new BlockPos(position.getX(), position.getY(), position.getZ());
    }
}
