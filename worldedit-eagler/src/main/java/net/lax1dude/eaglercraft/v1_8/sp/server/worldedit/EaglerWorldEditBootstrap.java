package net.lax1dude.eaglercraft.v1_8.sp.server.worldedit;

import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.selector.limit.PermissiveSelectorLimits;
import com.sk89q.worldedit.session.SessionKey;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.event.platform.PlatformsRegisteredEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Boots a small, genuine WorldEdit-core command bridge inside the integrated
 * server worker. Selection, edit sessions, block changes and undo history are
 * WorldEdit core objects; EaglerWorldEditWorld is the native ServerLevel extent.
 *
 * This first vertical slice deliberately exposes only position selection,
 * set, undo and redo. Unsupported block-entity NBT remains rejected by the
 * world adapter rather than being silently discarded.
 */
public final class EaglerWorldEditBootstrap {

    private static final int MAX_BLOCK_CHANGES_PER_COMMAND = 1_000_000;
    private static final Map<UUID, LocalSession> SESSIONS = new HashMap<>();
    private static final Map<ServerLevel, EaglerWorldEditWorld> WORLDS = new IdentityHashMap<>();
    private static boolean platformInitialized;

    private EaglerWorldEditBootstrap() {
    }

    public static void install() {
        EaglerWorldEditCommandBridge.install(EaglerWorldEditBootstrap::registerCommands);
    }

    static Collection<EaglerWorldEditWorld> loadedWorlds() {
        return WORLDS.values();
    }

    private static void registerCommands(final CommandDispatcher<CommandSourceStack> dispatcher) {
        ensureWorldEditPlatform();
        dispatcher.register(net.minecraft.commands.Commands.literal("pos1")
                .requires(EaglerWorldEditBootstrap::canUse)
                .executes(context -> selectPosition(context.getSource(), true))
                .then(net.minecraft.commands.Commands.argument("position", StringArgumentType.greedyString())
                        .executes(context -> selectPosition(
                                context.getSource(), true,
                                StringArgumentType.getString(context, "position")))));

        dispatcher.register(net.minecraft.commands.Commands.literal("pos2")
                .requires(EaglerWorldEditBootstrap::canUse)
                .executes(context -> selectPosition(context.getSource(), false))
                .then(net.minecraft.commands.Commands.argument("position", StringArgumentType.greedyString())
                        .executes(context -> selectPosition(
                                context.getSource(), false,
                                StringArgumentType.getString(context, "position")))));

        dispatcher.register(net.minecraft.commands.Commands.literal("hpos1")
                .requires(EaglerWorldEditBootstrap::canUse)
                .executes(context -> selectLookPosition(context.getSource(), true)));

        dispatcher.register(net.minecraft.commands.Commands.literal("hpos2")
                .requires(EaglerWorldEditBootstrap::canUse)
                .executes(context -> selectLookPosition(context.getSource(), false)));

        dispatcher.register(net.minecraft.commands.Commands.literal("size")
                .requires(EaglerWorldEditBootstrap::canUse)
                .executes(context -> reportSelectionSize(context.getSource())));

        dispatcher.register(net.minecraft.commands.Commands.literal("chunk")
                .requires(EaglerWorldEditBootstrap::canUse)
                .executes(context -> selectCurrentChunk(context.getSource())));

        dispatcher.register(net.minecraft.commands.Commands.literal("set")
                .requires(EaglerWorldEditBootstrap::canUse)
                .then(net.minecraft.commands.Commands.argument("block", StringArgumentType.greedyString())
                        .executes(context -> setBlocks(
                                context.getSource(),
                                StringArgumentType.getString(context, "block")))));

        dispatcher.register(net.minecraft.commands.Commands.literal("undo")
                .requires(EaglerWorldEditBootstrap::canUse)
                .executes(context -> undo(context.getSource(), false)));

        dispatcher.register(net.minecraft.commands.Commands.literal("redo")
                .requires(EaglerWorldEditBootstrap::canUse)
                .executes(context -> undo(context.getSource(), true)));

        // Minecraft strips exactly one leading slash before Brigadier parses
        // chat commands. WorldEdit users type //set, so the parser receives
        // /set. Register slash-prefixed aliases that redirect to the same
        // command nodes while retaining the ordinary /set aliases as well.
        for (String name : new String[] {
                "pos1", "pos2", "hpos1", "hpos2", "size",
                "chunk", "set", "undo", "redo"
        }) {
            final com.mojang.brigadier.tree.CommandNode<CommandSourceStack> target =
                    dispatcher.getRoot().getChild(name);
            if (target == null) {
                throw new IllegalStateException("WorldEdit command was not registered: " + name);
            }
            dispatcher.register(net.minecraft.commands.Commands.literal("/" + name).redirect(target));
        }
    }


    private static synchronized void ensureWorldEditPlatform() {
        if (platformInitialized) {
            return;
        }

        final WorldEdit worldEdit = WorldEdit.getInstance();
        final EaglerWorldEditPlatform platform = new EaglerWorldEditPlatform();
        worldEdit.getPlatformManager().register(platform);

        // WorldEdit's generated BlockTypes facade reads this registry. Populate
        // it from Minecraft's real built-in registry before any block state is
        // converted or an EditSession is created.
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            final String key = id.toString();
            if (!BlockType.REGISTRY.keySet().contains(key)) {
                BlockType.REGISTRY.register(key, new BlockType(key));
            }
        }

        worldEdit.getEventBus().post(new PlatformsRegisteredEvent());
        platformInitialized = true;
    }

    private static boolean canUse(final CommandSourceStack source) {
        final ServerPlayer player = source.getPlayer();
        return player != null && source.getServer().getPlayerList().isOp(player.nameAndId());
    }

    private static int selectPosition(final CommandSourceStack source, final boolean primary)
            throws CommandSyntaxException {
        final ServerPlayer player = requirePlayer(source);
        final EaglerWorldEditWorld world = world(source.getLevel());
        final LocalSession session = session(player);
        final BlockPos nativePosition = player.blockPosition();
        final BlockVector3 position = BlockVector3.at(
                nativePosition.getX(), nativePosition.getY(), nativePosition.getZ());

        final com.sk89q.worldedit.regions.RegionSelector selector = session.getRegionSelector(world);
        if (primary) {
            selector.selectPrimary(position, PermissiveSelectorLimits.getInstance());
        } else {
            selector.selectSecondary(position, PermissiveSelectorLimits.getInstance());
        }
        source.sendSuccess(
                () -> Component.literal((primary ? "WorldEdit position 1" : "WorldEdit position 2")
                        + " set to " + position.getX() + ", " + position.getY() + ", " + position.getZ()),
                false);
        return 1;
    }

    private static int selectLookPosition(final CommandSourceStack source, final boolean primary)
            throws CommandSyntaxException {
        final ServerPlayer player = requirePlayer(source);
        final HitResult hit = player.pick(5.0D, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            throw error("No block is targeted. Aim at a block within five blocks and try again.");
        }
        final BlockPos target = ((BlockHitResult) hit).getBlockPos();
        return selectPosition(source, primary, target);
    }

    private static int selectPosition(
            final CommandSourceStack source,
            final boolean primary,
            final String coordinates
    ) throws CommandSyntaxException {
        final String[] parts = coordinates.trim().split("\\s+");
        if (parts.length != 3) {
            throw error("Expected three block coordinates: //pos1 <x> <y> <z>.");
        }
        final ServerPlayer player = requirePlayer(source);
        final BlockPos origin = player.blockPosition();
        final int x;
        final int y;
        final int z;
        try {
            x = parseCoordinate(parts[0], origin.getX());
            y = parseCoordinate(parts[1], origin.getY());
            z = parseCoordinate(parts[2], origin.getZ());
        } catch (NumberFormatException | ArithmeticException exception) {
            throw error("Coordinates must be whole block numbers or relative values such as ~, ~5, or ~-2.");
        }
        return selectPosition(source, primary, new BlockPos(x, y, z));
    }

    private static int parseCoordinate(final String token, final int origin) {
        if (token.equals("~")) {
            return origin;
        }
        if (token.startsWith("~")) {
            final String offset = token.substring(1);
            return Math.addExact(origin, offset.isEmpty() ? 0 : Integer.parseInt(offset));
        }
        return Integer.parseInt(token);
    }

    private static int selectPosition(
            final CommandSourceStack source,
            final boolean primary,
            final BlockPos nativePosition
    ) throws CommandSyntaxException {
        final ServerPlayer player = requirePlayer(source);
        final EaglerWorldEditWorld world = world(source.getLevel());
        final LocalSession session = session(player);
        final BlockVector3 position = BlockVector3.at(
                nativePosition.getX(), nativePosition.getY(), nativePosition.getZ());

        final com.sk89q.worldedit.regions.RegionSelector selector = session.getRegionSelector(world);
        if (primary) {
            selector.selectPrimary(position, PermissiveSelectorLimits.getInstance());
        } else {
            selector.selectSecondary(position, PermissiveSelectorLimits.getInstance());
        }
        source.sendSuccess(
                () -> Component.literal((primary ? "WorldEdit position 1" : "WorldEdit position 2")
                        + " set to " + position.getX() + ", " + position.getY() + ", " + position.getZ()),
                false);
        return 1;
    }

    private static int selectCurrentChunk(final CommandSourceStack source)
            throws CommandSyntaxException {
        final ServerPlayer player = requirePlayer(source);
        final EaglerWorldEditWorld world = world(source.getLevel());
        final LocalSession session = session(player);
        final BlockPos playerPosition = player.blockPosition();
        final int minX = (playerPosition.getX() >> 4) << 4;
        final int minZ = (playerPosition.getZ() >> 4) << 4;
        final BlockVector3 minimum = BlockVector3.at(minX, world.getMinY(), minZ);
        final BlockVector3 maximum = BlockVector3.at(
                minX + 15, world.getMaxY() - 1, minZ + 15);
        final com.sk89q.worldedit.regions.RegionSelector selector = session.getRegionSelector(world);
        selector.selectPrimary(minimum, PermissiveSelectorLimits.getInstance());
        selector.selectSecondary(maximum, PermissiveSelectorLimits.getInstance());
        source.sendSuccess(() -> Component.literal(
                "WorldEdit selected chunk " + (minX >> 4) + ", " + (minZ >> 4)
                        + " from Y=" + world.getMinY() + " to Y=" + (world.getMaxY() - 1)),
                false);
        return 1;
    }

    private static int reportSelectionSize(final CommandSourceStack source)
            throws CommandSyntaxException {
        final ServerPlayer player = requirePlayer(source);
        final Region region;
        try {
            region = session(player).getSelection(world(source.getLevel()));
        } catch (Exception exception) {
            throw error("Select both corners first with //pos1 and //pos2.");
        }
        final BlockVector3 dimensions = region.getDimensions();
        final int width = dimensions.getBlockX();
        final int height = dimensions.getBlockY();
        final int length = dimensions.getBlockZ();
        final long volume = (long) width * (long) height * (long) length;
        source.sendSuccess(() -> Component.literal(
                "WorldEdit selection: " + width + " x " + height + " x " + length
                        + " (" + volume + " blocks)"), false);
        return 1;
    }

    private static int setBlocks(final CommandSourceStack source, final String requestedBlock)
            throws CommandSyntaxException {
        final ServerPlayer player = requirePlayer(source);
        final EaglerWorldEditActor actor = actor(player);
        final EaglerWorldEditWorld world = world(source.getLevel());
        final LocalSession session = session(player);

        final Region region;
        try {
            region = session.getSelection(world);
        } catch (Exception exception) {
            throw error("Select both corners first with //pos1 and //pos2.");
        }

        final String blockName = requestedBlock.trim();
        if (blockName.isEmpty()) {
            throw error("A block name is required.");
        }
        final int stateStart = blockName.indexOf('[');
        final String identifierText = stateStart >= 0 ? blockName.substring(0, stateStart).trim() : blockName;
        final String propertiesText;
        if (stateStart >= 0) {
            if (!blockName.endsWith("]") || stateStart == blockName.length() - 1) {
                throw error("Invalid block-state properties. Use stone or oak_log[axis=y].");
            }
            propertiesText = blockName.substring(stateStart + 1, blockName.length() - 1);
        } else {
            propertiesText = "";
        }

        final Identifier blockId;
        try {
            blockId = Identifier.parse(identifierText.indexOf(':') >= 0
                    ? identifierText : "minecraft:" + identifierText);
        } catch (RuntimeException exception) {
            throw error("Invalid block identifier: " + identifierText);
        }
        if (!BuiltInRegistries.BLOCK.containsKey(blockId)) {
            throw error("Unknown Minecraft block: " + blockId);
        }

        final Block nativeBlock = BuiltInRegistries.BLOCK.getValue(blockId);
        final net.minecraft.world.level.block.state.BlockState nativeState;
        try {
            nativeState = parseBlockState(nativeBlock, propertiesText);
        } catch (IllegalArgumentException exception) {
            throw error(exception.getMessage());
        }
        final BlockState worldEditState;
        try {
            worldEditState = WorldEditBlockStateConverter.toWorldEdit(nativeState);
        } catch (RuntimeException exception) {
            throw error("WorldEdit cannot represent " + blockId + ": " + exception.getMessage());
        }

        final EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(world)
                .actor(actor)
                .maxBlocks(MAX_BLOCK_CHANGES_PER_COMMAND)
                .build();
        try {
            final int changed = editSession.setBlocks(region, new BlockPattern(worldEditState));
            session.remember(editSession);
            editSession.close();
            source.sendSuccess(() -> Component.literal(
                    "WorldEdit changed " + changed + " blocks to " + blockId), false);
            return changed;
        } catch (Exception exception) {
            editSession.close();
            throw error("WorldEdit edit failed: " + exception.getMessage());
        }
    }

    /**
     * Parse Minecraft's canonical block-state property syntax, for example
     * oak_log[axis=y] or oak_stairs[facing=north,half=top,shape=straight].
     * Property values are validated against the real 26.2 block definition.
     */
    private static net.minecraft.world.level.block.state.BlockState parseBlockState(
            final Block block,
            final String propertiesText
    ) {
        net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
        if (propertiesText.isEmpty()) {
            return state;
        }

        final java.util.Set<String> seen = new java.util.HashSet<>();
        for (String assignment : propertiesText.split(",", -1)) {
            final int equals = assignment.indexOf('=');
            if (equals <= 0 || equals == assignment.length() - 1
                    || assignment.indexOf('=', equals + 1) >= 0) {
                throw new IllegalArgumentException("Invalid block-state property: " + assignment);
            }

            final String propertyName = assignment.substring(0, equals).trim();
            final String propertyValue = assignment.substring(equals + 1).trim();
            if (!seen.add(propertyName)) {
                throw new IllegalArgumentException("Duplicate block-state property: " + propertyName);
            }

            final net.minecraft.world.level.block.state.properties.Property<?> property =
                    state.getBlock().getStateDefinition().getProperty(propertyName);
            if (property == null) {
                throw new IllegalArgumentException(
                        "Block " + BuiltInRegistries.BLOCK.getKey(block) + " has no property " + propertyName);
            }
            state = applyProperty(state, property, propertyValue);
        }
        return state;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static net.minecraft.world.level.block.state.BlockState applyProperty(
            final net.minecraft.world.level.block.state.BlockState state,
            final net.minecraft.world.level.block.state.properties.Property property,
            final String value
    ) {
        final java.util.Optional parsed = property.getValue(value);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid value '" + value + "' for block-state property " + property.getName());
        }
        return state.setValue(property, (Comparable) parsed.get());
    }

    private static int undo(final CommandSourceStack source, final boolean redo)
            throws CommandSyntaxException {
        final ServerPlayer player = requirePlayer(source);
        final EaglerWorldEditActor actor = actor(player);
        final LocalSession session = session(player);
        final EditSession changed = redo ? session.redo(null, actor) : session.undo(null, actor);
        if (changed == null) {
            source.sendFailure(Component.literal(redo
                    ? "There is no WorldEdit edit to redo."
                    : "There is no WorldEdit edit to undo."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(redo
                ? "WorldEdit redo completed."
                : "WorldEdit undo completed."), false);
        return 1;
    }

    private static ServerPlayer requirePlayer(final CommandSourceStack source) throws CommandSyntaxException {
        try {
            return source.getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            throw error("WorldEdit commands can only be used by a player.");
        }
    }

    private static LocalSession session(final ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new LocalSession());
    }

    private static EaglerWorldEditWorld world(final ServerLevel level) {
        return WORLDS.computeIfAbsent(level, EaglerWorldEditWorld::new);
    }

    private static EaglerWorldEditActor actor(final ServerPlayer player) {
        return new EaglerWorldEditActor(player);
    }

    private static CommandSyntaxException error(final String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }

    private static final class EaglerWorldEditActor implements Actor {
        private final ServerPlayer player;
        private final SessionKey sessionKey;

        private EaglerWorldEditActor(final ServerPlayer player) {
            this.player = player;
            this.sessionKey = new SessionKey() {
                @Override
                public UUID getUniqueId() {
                    return EaglerWorldEditActor.this.player.getUUID();
                }

                @Override
                public String getName() {
                    return EaglerWorldEditActor.this.player.getGameProfile().name();
                }

                @Override
                public boolean isActive() {
                    return EaglerWorldEditActor.this.player.isAlive();
                }

                @Override
                public boolean isPersistent() {
                    return false;
                }
            };
        }

        @Override
        public UUID getUniqueId() {
            return player.getUUID();
        }

        @Override
        public SessionKey getSessionKey() {
            return sessionKey;
        }

        @Override
        public String getName() {
            return player.getGameProfile().name();
        }

        @Override
        public String[] getGroups() {
            return new String[] { "eaglercraft" };
        }

        @Override
        public boolean hasPermission(final String permission) {
            return player.level().getServer().getPlayerList().isOp(player.nameAndId());
        }

        @Override
        public void checkPermission(final String permission) {
            if (!hasPermission(permission)) {
                throw new IllegalStateException("Missing permission: " + permission);
            }
        }

        @Override
        public void printRaw(final String message) {
            player.sendSystemMessage(Component.literal(message));
        }

        @Override
        public void printDebug(final String message) {
            player.sendSystemMessage(Component.literal(message));
        }

        @Override
        public void print(final String message) {
            player.sendSystemMessage(Component.literal(message));
        }

        @Override
        public void printError(final String message) {
            player.sendSystemMessage(Component.literal(message));
        }

        @Override
        public void print(final com.sk89q.worldedit.util.formatting.text.Component component) {
            player.sendSystemMessage(Component.literal(component.toString()));
        }

        @Override
        public boolean canDestroyBedrock() {
            return false;
        }

        @Override
        public boolean isPlayer() {
            return true;
        }

        @Override
        public java.io.File openFileOpenDialog(final String[] extensions) {
            return null;
        }

        @Override
        public java.io.File openFileSaveDialog(final String[] extensions) {
            return null;
        }

        @Override
        public void dispatchCUIEvent(final com.sk89q.worldedit.internal.cui.CUIEvent event) {
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }
    }
}
