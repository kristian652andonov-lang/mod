package com.fantasyweapons.client.vfx;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.config.ClientConfig;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns every live client-side effect. Effects are plain objects (no entities, no vanilla particles), ticked at 20 Hz
 * and drawn in one pass after particles with the custom render types.
 */
public final class VfxManager {
    private static final List<Vfx> ACTIVE = new ArrayList<>();
    private static final List<Vfx> PENDING = new ArrayList<>();
    private static final ByteBufferBuilder BUFFER = new ByteBufferBuilder(1 << 20);
    private static final MultiBufferSource.BufferSource SOURCE = MultiBufferSource.immediate(BUFFER);
    private static boolean ticking;

    private VfxManager() {
    }

    public static <T extends Vfx> T add(T vfx) {
        int max = ClientConfig.get(ClientConfig.MAX_VFX_INSTANCES, 512);
        if (ACTIVE.size() + PENDING.size() >= max) return vfx;
        if (ticking) PENDING.add(vfx);
        else ACTIVE.add(vfx);
        return vfx;
    }

    public static void tick() {
        ticking = true;
        try {
            for (int i = 0; i < ACTIVE.size(); i++) {
                Vfx v = ACTIVE.get(i);
                try {
                    v.tick();
                } catch (RuntimeException e) {
                    FantasyWeapons.LOGGER.error("VFX {} failed to tick", v.getClass().getSimpleName(), e);
                    v.kill();
                }
            }
            ACTIVE.removeIf(Vfx::isDead);
        } finally {
            ticking = false;
        }
        ACTIVE.addAll(PENDING);
        PENDING.clear();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float time = (Util.getMillis() % 3_600_000L) / 1000f;
        VfxContext ctx = new VfxContext(camera.getPosition(), camera.getLeftVector(), camera.getUpVector(), camera.getLookVector(),
                partial, time, ClientConfig.quality().density(), SOURCE);
        for (Vfx v : ACTIVE) {
            if (v.isDead()) continue;
            try {
                v.render(ctx);
            } catch (RuntimeException e) {
                FantasyWeapons.LOGGER.error("VFX {} failed to render", v.getClass().getSimpleName(), e);
                v.kill();
            }
        }
        SOURCE.endBatch();
        if (mc.level == null) clear();
    }

    public static void clear() {
        ACTIVE.clear();
        PENDING.clear();
    }

    public static int count() {
        return ACTIVE.size();
    }
}
