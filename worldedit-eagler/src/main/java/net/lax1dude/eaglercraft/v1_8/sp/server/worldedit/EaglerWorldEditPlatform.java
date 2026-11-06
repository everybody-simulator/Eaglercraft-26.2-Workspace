package net.lax1dude.eaglercraft.v1_8.sp.server.worldedit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import com.sk89q.worldedit.extension.platform.AbstractPlatform;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extension.platform.Preference;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.registry.BiomeRegistry;
import com.sk89q.worldedit.world.registry.BlockCategoryRegistry;
import com.sk89q.worldedit.world.registry.BlockRegistry;
import com.sk89q.worldedit.world.registry.BundledBlockRegistry;
import com.sk89q.worldedit.world.registry.BundledItemRegistry;
import com.sk89q.worldedit.world.registry.EntityRegistry;
import com.sk89q.worldedit.world.registry.ItemCategoryRegistry;
import com.sk89q.worldedit.world.registry.ItemRegistry;
import com.sk89q.worldedit.world.registry.NullBiomeRegistry;
import com.sk89q.worldedit.world.registry.NullBlockCategoryRegistry;
import com.sk89q.worldedit.world.registry.NullEntityRegistry;
import com.sk89q.worldedit.world.registry.NullItemCategoryRegistry;
import com.sk89q.worldedit.world.registry.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/**
 * Minimal WorldEdit platform services for Eaglercraft's integrated server.
 * Minecraft block/property data is sourced from the live native registries.
 */
final class EaglerWorldEditPlatform extends AbstractPlatform {

    private final Registries registries = new EaglerRegistries();
    private final com.sk89q.worldedit.LocalConfiguration configuration =
            new com.sk89q.worldedit.LocalConfiguration() {
                @Override
                public void load() {
                    // This first browser integration keeps configuration in memory.
                }
            };

    @Override
    public Registries getRegistries() {
        return registries;
    }

    @Override
    public int getDataVersion() {
        return 0;
    }

    @Override
    public boolean isValidMobType(final String type) {
        try {
            return BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.parse(type));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public List<? extends com.sk89q.worldedit.world.World> getWorlds() {
        return new ArrayList<>(EaglerWorldEditBootstrap.loadedWorlds());
    }

    @Override
    public Player matchPlayer(final Player player) {
        return null;
    }

    @Override
    public com.sk89q.worldedit.world.World matchWorld(final com.sk89q.worldedit.world.World world) {
        return world instanceof EaglerWorldEditWorld ? world : null;
    }

    @Override
    public void registerCommands(final org.enginehub.piston.CommandManager commandManager) {
        // Commands are exposed through the native Brigadier dispatcher.
    }

    @Override
    public void setGameHooksEnabled(final boolean enabled) {
        // Player interaction hooks are added in the next integration stage.
    }

    @Override
    public com.sk89q.worldedit.LocalConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public String getVersion() {
        return "7.4.0";
    }

    @Override
    public String getPlatformName() {
        return "Eaglercraft";
    }

    @Override
    public String getPlatformVersion() {
        return "26.2";
    }

    @Override
    public Map<Capability, Preference> getCapabilities() {
        // The first integration stage has no click/interaction listener yet.
        return Map.of();
    }

    @Override
    public Set<SideEffect> getSupportedSideEffects() {
        return Set.of();
    }

    @Override
    public long getTickCount() {
        return 0L;
    }

    private static final class EaglerRegistries implements Registries {
        private final BlockRegistry blocks = new EaglerBlockRegistry();
        private final ItemRegistry items = new BundledItemRegistry();
        private final EntityRegistry entities = new NullEntityRegistry();
        private final BiomeRegistry biomes = new NullBiomeRegistry();
        private final BlockCategoryRegistry blockCategories = new NullBlockCategoryRegistry();
        private final ItemCategoryRegistry itemCategories = new NullItemCategoryRegistry();

        @Override
        public BlockRegistry getBlockRegistry() {
            return blocks;
        }

        @Override
        public ItemRegistry getItemRegistry() {
            return items;
        }

        @Override
        public EntityRegistry getEntityRegistry() {
            return entities;
        }

        @Override
        public BiomeRegistry getBiomeRegistry() {
            return biomes;
        }

        @Override
        public BlockCategoryRegistry getBlockCategoryRegistry() {
            return blockCategories;
        }

        @Override
        public ItemCategoryRegistry getItemCategoryRegistry() {
            return itemCategories;
        }
    }

    private static final class EaglerBlockRegistry implements BlockRegistry {
        private final BundledBlockRegistry fallback = new BundledBlockRegistry();

        @Override
        public Component getRichName(final BlockType blockType) {
            return TextComponent.of(blockType.id());
        }

        @Override
        public com.sk89q.worldedit.world.registry.BlockMaterial getMaterial(final BlockType blockType) {
            return fallback.getMaterial(blockType);
        }

        @Override
        public Map<String, ? extends Property<?>> getProperties(final BlockType blockType) {
            final Identifier id = Identifier.parse(blockType.id());
            if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                return Map.of();
            }
            final Block block = BuiltInRegistries.BLOCK.getValue(id);
            final Map<String, Property<?>> properties = new LinkedHashMap<>();
            for (net.minecraft.world.level.block.state.properties.Property<?> property
                    : block.getStateDefinition().getProperties()) {
                properties.put(property.getName(), new EaglerProperty(property));
            }
            return properties;
        }

        @Override
        public OptionalInt getInternalBlockStateId(final BlockState state) {
            return OptionalInt.empty();
        }
    }

    private static final class EaglerProperty implements Property<String> {
        private final String name;
        private final List<String> values;

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private EaglerProperty(
                final net.minecraft.world.level.block.state.properties.Property minecraftProperty) {
            this.name = minecraftProperty.getName();
            final List<String> names = new ArrayList<>();
            for (Object value : minecraftProperty.getPossibleValues()) {
                names.add(minecraftProperty.getName((Comparable) value));
            }
            this.values = List.copyOf(names);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<String> values() {
            return values;
        }

        @Override
        public String getValueFor(final String value) {
            return values.contains(value) ? value : null;
        }
    }
}
