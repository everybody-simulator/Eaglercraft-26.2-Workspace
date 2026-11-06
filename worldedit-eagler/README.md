# Eaglercraft WorldEdit adapter

This module integrates the real WorldEdit 7.4.0 core edit engine with the real
Minecraft 26.2 integrated-server world. It is not a parallel block simulation.

## Implemented integration

- `EaglerServerLevelBlockAccess` reads and writes native Minecraft
  `BlockState` objects through the owning `ServerLevel`, enforces server-thread
  access, validates world bounds, and provides a reversible read/write probe.
- `WorldEditBlockStateConverter` maps Minecraft block IDs and named state
  properties to WorldEdit's native block-state model. Unknown or unrepresentable
  data fails explicitly.
- `EaglerWorldEditWorld` is a WorldEdit `AbstractWorld` backed by a real
  `ServerLevel`. WorldEdit's `EditSession` performs the edits; the adapter
  routes each write into Minecraft's real level.
- `EaglerWorldEditPlatform` registers the Eaglercraft block/property registry
  and minimal platform services with WorldEdit core.
- `EaglerWorldEditBootstrap` installs the command registrar before the
  integrated server constructs its Brigadier dispatcher. The browser
  `ServerWorkerHost` explicitly installs it after Minecraft bootstrap.
- The current command slice is `//pos1`, `//pos2`, `//hpos1`, `//hpos2`,
  `//set <block>`, `//undo`, and `//redo`. `//chunk` selects the entire chunk containing the
  player, across the world's full build height. `//pos1` and `//pos2` accept either the player's
  current block position or three explicit absolute block coordinates, such
  as `//pos1 10 64 -20`; relative coordinates such as `//pos1 ~ ~5 ~-2`
  are also supported. `//size` reports the selected region dimensions and
  volume. Commands are restricted to server operators.
  `//set` accepts native Minecraft block-state properties, for example
  `//set oak_log[axis=y]` or `//set oak_stairs[facing=north,half=top]`;
  property names and values are validated against the real block definition.
  The wooden-axe selection wand and automatic click interception are not
  implemented yet; use `//hpos1` and `//hpos2` for ray-targeted selection.

## Known limitations

- Block-entity NBT snapshots and placement are rejected. Chests, signs,
  inventories, and other block entities are not yet safe for copy/paste.
- Full WorldEdit pattern expressions (random patterns, gradients, masks,
  percentages, and pattern composition) are not implemented. `//set` supports
  one block with explicit native Minecraft state properties.
- WorldEdit sessions are in-memory and keyed to player UUIDs. They do not
  persist across world/server restarts.
- Entity enumeration/spawning, item drops, biome writes, tree generation,
  and player interaction hooks are not implemented.
- The live in-world command path and reversible block round-trip probe still
  require browser runtime verification. A successful Java compile alone does
  not prove the TeaVM worker can initialize WorldEdit core or execute edits.

## Build and runtime verification

The adapter compile workflow checks `:game:compileJava`,
`:worldedit-eagler:compileJava`, and `:platform-teavm:compileJava`.
The standalone browser build additionally links the integrated-server worker
with the bootstrap reachable. Both workflows run automatically on relevant
changes to this module. The port is not considered verified until that link
succeeds and the commands are exercised in a live browser world.
