package net.lax1dude.eaglercraft.v1_8.sp.server.worldedit;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

/**
 * Strict, lossless-by-validation conversion between Minecraft 26.2 block
 * states and WorldEdit's native block-state model.
 *
 * <p>Unknown block types, missing properties, and values that cannot be
 * represented on the other side fail explicitly. Silently dropping a
 * property would make clipboard, paste, and undo operations destructive.</p>
 */
public final class WorldEditBlockStateConverter {

    private WorldEditBlockStateConverter() {
    }

    public static com.sk89q.worldedit.world.block.BlockState toWorldEdit(
            final net.minecraft.world.level.block.state.BlockState minecraftState
    ) {
        Objects.requireNonNull(minecraftState, "minecraftState");

        final Identifier id = Objects.requireNonNull(
                BuiltInRegistries.BLOCK.getKey(minecraftState.getBlock()),
                "Minecraft block is not registered"
        );
        final BlockType worldEditType = BlockTypes.get(id.toString());
        if (worldEditType == null) {
            throw new IllegalArgumentException("WorldEdit has no registered block type for " + id);
        }

        com.sk89q.worldedit.world.block.BlockState result = worldEditType.getDefaultState();
        for (Property<?> minecraftProperty : minecraftState.getProperties()) {
            final com.sk89q.worldedit.registry.state.Property<?> worldEditProperty =
                    worldEditType.getProperty(minecraftProperty.getName());
            if (worldEditProperty == null) {
                throw new IllegalArgumentException(
                        "WorldEdit block " + id + " has no property " + minecraftProperty.getName()
                );
            }
            final String value = minecraftPropertyValueName(minecraftState, minecraftProperty);
            result = withWorldEditProperty(result, worldEditProperty, value);
        }
        return result;
    }

    public static net.minecraft.world.level.block.state.BlockState toMinecraft(
            final com.sk89q.worldedit.world.block.BlockState worldEditState
    ) {
        Objects.requireNonNull(worldEditState, "worldEditState");

        final Identifier id = Identifier.parse(worldEditState.getBlockType().id());
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("Minecraft has no registered block for " + id);
        }
        final Block block = BuiltInRegistries.BLOCK.getValue(id);

        net.minecraft.world.level.block.state.BlockState result = block.defaultBlockState();
        for (Map.Entry<com.sk89q.worldedit.registry.state.Property<?>, Object> entry : worldEditState.getStates().entrySet()) {
            final String propertyName = entry.getKey().name();
            final Property<?> minecraftProperty =
                    result.getBlock().getStateDefinition().getProperty(propertyName);
            if (minecraftProperty == null) {
                throw new IllegalArgumentException("Minecraft block " + id + " has no property " + propertyName);
            }
            result = withMinecraftProperty(result, minecraftProperty, String.valueOf(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String minecraftPropertyValueName(
            final net.minecraft.world.level.block.state.BlockState state,
            final Property property
    ) {
        return property.getName(state.getValue(property));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static com.sk89q.worldedit.world.block.BlockState withWorldEditProperty(
            final com.sk89q.worldedit.world.block.BlockState state,
            final com.sk89q.worldedit.registry.state.Property property,
            final String value
    ) {
        final Object parsed = property.getValueFor(value);
        if (parsed == null) {
            throw new IllegalArgumentException(
                    "WorldEdit property " + property.name() + " does not accept value " + value
            );
        }
        return state.with(property, parsed);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static net.minecraft.world.level.block.state.BlockState withMinecraftProperty(
            final net.minecraft.world.level.block.state.BlockState state,
            final Property property,
            final String value
    ) {
        final Optional parsed = property.getValue(value);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException(
                    "Minecraft property " + property.getName() + " does not accept value " + value
            );
        }
        return state.setValue(property, (Comparable) parsed.get());
    }
}
