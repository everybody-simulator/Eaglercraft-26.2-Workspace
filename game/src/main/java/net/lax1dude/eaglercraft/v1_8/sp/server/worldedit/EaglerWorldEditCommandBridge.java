package net.lax1dude.eaglercraft.v1_8.sp.server.worldedit;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Optional server-worker hook for platform integrations that add commands.
 * The game module deliberately knows only this tiny interface, avoiding a
 * dependency cycle on optional server-side integrations.
 */
public final class EaglerWorldEditCommandBridge {

    @FunctionalInterface
    public interface Registrar {
        void register(CommandDispatcher<CommandSourceStack> dispatcher);
    }

    private static Registrar registrar;

    private EaglerWorldEditCommandBridge() {
    }

    public static void install(final Registrar commandRegistrar) {
        if (registrar != null && registrar != commandRegistrar) {
            throw new IllegalStateException("A WorldEdit command registrar is already installed");
        }
        registrar = commandRegistrar;
    }

    public static void registerCommands(final CommandDispatcher<CommandSourceStack> dispatcher) {
        final Registrar current = registrar;
        if (current != null) {
            current.register(dispatcher);
        }
    }
}
