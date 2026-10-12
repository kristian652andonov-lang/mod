package com.fantasyweapons.command;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DEVELOPMENT ONLY. With {@code -Dfantasyweapons.serverscript=<file>} the server runs the commands in the file (one per
 * line, {@code #} comments) as soon as it has started, then shuts down. Used to check world generation on a fresh world.
 */
public final class ServerScript {
    private ServerScript() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        String file = System.getProperty("fantasyweapons.serverscript");
        if (file == null) return;
        MinecraftServer server = event.getServer();
        try {
            for (String line : Files.readAllLines(Path.of(file))) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                FantasyWeapons.LOGGER.info("[serverscript] {}", line);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4), line);
            }
        } catch (Exception e) {
            FantasyWeapons.LOGGER.error("[serverscript] failed", e);
        }
        server.halt(false);
    }
}
