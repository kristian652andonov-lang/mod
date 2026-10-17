package com.aigirlfriend.devtest;

import com.aigirlfriend.AIGirlfriend;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development-only entry point (not part of the release jar). When the JVM is started with
 * {@code -Daigirlfriend.animtest=true} (the {@code runAnimTest} Gradle run), the client creates a flat test world,
 * spawns the companion and a mock "TestPlayer", plays every pose, gesture and interaction and saves screenshots.
 */
@Mod(value = AIGirlfriend.MOD_ID, dist = Dist.CLIENT)
public final class AnimTestHarnessMod {
    public AnimTestHarnessMod() {
        if (!Boolean.getBoolean("aigirlfriend.animtest")) return;
        AIGirlfriend.LOGGER.info("[animtest] harness enabled");
        NeoForge.EVENT_BUS.register(new AnimTestHarness());
    }
}
