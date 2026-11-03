# Eaglercraft WorldEdit adapter

This is the isolated integration module for the real WorldEdit 7.4.0 core API.

## Implemented source pieces

- `WorldEditCoreApi` verifies the core API boundary and `AbstractWorld` type.
- `EaglerServerLevelBlockAccess` holds a real Minecraft `ServerLevel`, reads native `BlockState` objects, writes through `Level#setBlock`, checks server-thread ownership, and provides a reversible live-world round-trip probe.
- `WorldEditBlockStateConverter` maps registered Minecraft block IDs and named state properties to and from WorldEdit's block-state model. It fails explicitly for unknown blocks, missing properties, or unrepresentable values instead of silently dropping state.

These are source implementations, not a claim that WorldEdit is already usable in-game. The bridge's smoke test has not yet been run inside a live Eaglercraft world, and the module has not yet passed a Gradle compile or TeaVM link.

## Remaining integration

1. Implement `EaglerWorldEditWorld extends AbstractWorld` using the native bridge and converter.
2. Preserve block-entity NBT and inventory contents for copy/paste and schematics.
3. Implement biome access, entity operations, and side-effect handling.
4. Register WorldEdit's actual command dispatcher and platform services with the integrated server.
5. Connect player selections, sessions, clipboard, undo, and history.
6. Verify server-worker compilation and browser runtime compatibility before adding WorldEdit to the production worker classpath.

The module depends on the real Minecraft implementation in `:game` and the WorldEdit core API only. It does not pull in Bukkit, Fabric, or NeoForge.
