package com.fantasyweapons.command;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * DEVELOPMENT ONLY. With {@code -Dfantasyweapons.serverscript=<file>} the server runs the commands in the file (one per
 * line, {@code #} comments) as soon as it has started, then shuts down. A line {@code wait <ticks>} lets the server run
 * that long before the next line (for what takes its time, such as the world's destruction). Used to check world
 * generation on a fresh world.
 */
public final class ServerScript {
    private static final Deque<String> lines = new ArrayDeque<>();
    private static int resumeAt = -1;

    private ServerScript() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        String file = System.getProperty("fantasyweapons.serverscript");
        if (file == null) return;
        try {
            lines.addAll(Files.readAllLines(Path.of(file)));
        } catch (Exception e) {
            FantasyWeapons.LOGGER.error("[serverscript] failed", e);
        }
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (resumeAt >= 0 && e.getServer().getTickCount() >= resumeAt) run(e.getServer());
        });
        run(event.getServer());
    }

    private static void run(MinecraftServer server) {
        resumeAt = -1;
        while (!lines.isEmpty()) {
            String line = lines.poll().strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            FantasyWeapons.LOGGER.info("[serverscript] {}", line);
            if (line.startsWith("wait ")) {
                resumeAt = server.getTickCount() + Integer.parseInt(line.substring(5).strip());
                return;
            }
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4), line);
            } catch (Exception e) {
                FantasyWeapons.LOGGER.error("[serverscript] failed", e);
            }
        }
        server.halt(false);
    }
}
