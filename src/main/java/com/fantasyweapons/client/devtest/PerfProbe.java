package com.fantasyweapons.client.devtest;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * DEVELOPMENT ONLY: once a second logs the client's worst and average frame time, the integrated server's worst and
 * average tick time and the destruction queue, so a heavy effect shows where its cost lands.
 */
final class PerfProbe {
    private static long lastFrame, worstFrame, frameSum;
    private static int frames, ticks;
    private static volatile long serverStart, serverWorst, serverSum;
    private static volatile int serverTicks;

    private PerfProbe() {
    }

    static void start() {
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> {
            long now = System.nanoTime();
            if (lastFrame != 0) {
                long d = now - lastFrame;
                worstFrame = Math.max(worstFrame, d);
                frameSum += d;
                frames++;
            }
            lastFrame = now;
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> serverStart = System.nanoTime());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            long d = System.nanoTime() - serverStart;
            serverWorst = Math.max(serverWorst, d);
            serverSum += d;
            serverTicks++;
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            if (++ticks % 20 != 0 || Minecraft.getInstance().level == null) return;
            FantasyWeapons.LOGGER.warn("perf: frames {} worst {} ms avg {} ms | server ticks {} worst {} ms avg {} ms | {}",
                    frames, worstFrame / 1_000_000, frames == 0 ? 0 : frameSum / frames / 1_000_000, serverTicks, serverWorst / 1_000_000,
                    serverTicks == 0 ? 0 : serverSum / serverTicks / 1_000_000, com.fantasyweapons.world.wreck.WorldWreck.stats());
            frames = 0;
            worstFrame = frameSum = 0;
            serverTicks = 0;
            serverWorst = serverSum = 0;
        });
    }
}
