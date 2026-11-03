# Eaglercraft WorldEdit adapter

This is the isolated integration module for the real WorldEdit 7.4.0 core API.

- It depends on the real Minecraft implementation in `:game`.
- It compiles against `worldedit-core` only; it does not pull in Bukkit, Fabric, or NeoForge.
- The dedicated server target will own the WorldEdit runtime dependency after a TeaVM link-compatibility check.
- The native `EaglerServerLevelBlockAccess` seam remains the only path for reading and writing Minecraft blocks.

The first implementation milestone is a WorldEdit `AbstractWorld` backed by `ServerLevel`, including complete block-state/property conversion. Block entities/NBT, biomes, actors, command registration, sessions, and undo are separate milestones; none should be represented as complete until implemented and tested.
