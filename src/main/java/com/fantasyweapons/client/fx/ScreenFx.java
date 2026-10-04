package com.fantasyweapons.client.fx;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.client.vfx.Colors;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen overlays: brief colour flashes and lingering vignettes (e.g. standing inside the Void Dimension).
 * Drawn as a GUI layer below the HUD.
 */
public final class ScreenFx {
    private record Overlay(int color, float maxAlpha, int lifetime, boolean vignette, Vec3 center, double radius, int[] age) {
    }

    private static final List<Overlay> OVERLAYS = new ArrayList<>();
    private static final ResourceLocation VIGNETTE = FantasyWeapons.id("textures/gui/vignette.png");

    private ScreenFx() {
    }

    public static void flash(int color, float alpha, int lifetime) {
        OVERLAYS.add(new Overlay(color, alpha, lifetime, false, null, 0, new int[]{0}));
    }

    /** A vignette that is only visible while the local player is within {@code radius} of {@code center}. */
    public static void zoneVignette(int color, float alpha, int lifetime, Vec3 center, double radius) {
        OVERLAYS.add(new Overlay(color, alpha, lifetime, true, center, radius, new int[]{0}));
    }

    public static void tick() {
        OVERLAYS.removeIf(o -> ++o.age()[0] >= o.lifetime());
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (OVERLAYS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        for (Overlay o : OVERLAYS) {
            float t = Math.min(1f, (o.age()[0] + partial) / o.lifetime());
            if (o.center() != null && (mc.player == null || mc.player.position().distanceTo(o.center()) > o.radius())) continue;
            if (o.vignette()) {
                float a = o.maxAlpha() * Math.min(1, t * 8) * Math.min(1, (1 - t) * 6);
                RenderSystem.enableBlend();
                g.setColor(Colors.r(o.color()), Colors.g(o.color()), Colors.b(o.color()), a);
                g.blit(VIGNETTE, 0, 0, w, h, 0, 0, 256, 256, 256, 256);
                g.setColor(1, 1, 1, 1);
                RenderSystem.disableBlend();
            } else {
                float a = o.maxAlpha() * (1 - t) * (1 - t);
                g.fill(0, 0, w, h, Colors.alpha(a, o.color()));
            }
        }
    }
}
