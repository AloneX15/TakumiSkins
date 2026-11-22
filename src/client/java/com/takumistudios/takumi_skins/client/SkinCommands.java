package com.takumistudios.takumi_skins.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client-side commands of Takumi Skins. Messages carry a Spanish fallback: on Fabric without Fabric API, Fabric Loader
 * does not load mod language files.
 */
public final class SkinCommands {
    private SkinCommands() {
    }

    /** Handles {@code takumiskins recargar|reload}; false for any other command, which then goes to the server. */
    public static boolean handle(String command) {
        String normalized = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("takumiskins recargar") && !normalized.equals("takumiskins reload")) {
            return false;
        }
        Component message;
        if (SkinRegistry.active()) {
            SkinRegistry.reloadAll();
            message = Component.translatableWithFallback("takumi_skins.command.reloaded",
                    "Takumi Skins: recargando las skins de todos.");
        } else {
            message = Component.translatableWithFallback("takumi_skins.command.inactive",
                    "Takumi Skins solo funciona si abres el juego desde Takumi Launcher.");
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(message);
        }
        return true;
    }
}
