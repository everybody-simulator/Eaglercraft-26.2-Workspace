package net.lax1dude.eaglercraft.v1_8.sp.server.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.AbstractWorld;

/**
 * Compile-time boundary to the genuine WorldEdit core API.
 *
 * This module intentionally depends on worldedit-core, not a Bukkit/Fabric/
 * NeoForge platform implementation. Minecraft-specific conversion and server
 * lifecycle integration will be implemented here without substituting a
 * home-grown selection/edit engine.
 */
public final class WorldEditCoreApi {

    private WorldEditCoreApi() {
    }

    /**
     * Return the WorldEdit world abstraction that the Eaglercraft adapter will
     * implement. Kept as a small explicit API probe so dependency resolution
     * and compiler compatibility can be checked before the larger adapter is
     * added.
     */
    public static Class<? extends AbstractWorld> worldBaseType() {
        return AbstractWorld.class;
    }

    public static BlockVector3 toWorldEditPosition(int x, int y, int z) {
        return BlockVector3.at(x, y, z);
    }
}
