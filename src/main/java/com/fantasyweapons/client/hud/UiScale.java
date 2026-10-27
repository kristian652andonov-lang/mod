package com.fantasyweapons.client.hud;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * How big the mod's interface is drawn, from the size of the window rather than the GUI scale alone: the same on screen
 * at every GUI scale (never tiny print at a small one, never enormous at a large one), and growing with the window.
 */
public final class UiScale {
    private UiScale() {
    }

    private static Window window() {
        return Minecraft.getInstance().getWindow();
    }

    /**
     * The HUD (the weapon panel, the set plaque, banners and toasts): as big as it is at GUI scale 2 on a window 1080
     * pixels high. A factor on the GUI units it was laid out in.
     */
    public static float hud() {
        Window w = window();
        return Mth.clamp(Math.max(1.5f, w.getHeight() / 540f) / (float) w.getGuiScale(), 0.4f, 2.5f);
    }

    /**
     * A menu laid out {@code w} by {@code h} units on a screen {@code screenW} by {@code screenH} GUI units: three pixels to
     * a unit on a window 1080 pixels high, more on bigger ones (whole pixels on the usual windows, so its writing stays
     * crisp; never fewer than three, so it stays readable on small ones), and never more than fits.
     */
    public static float menu(int screenW, int screenH, float w, float h) {
        Window win = window();
        float wanted = Math.max(3f, win.getHeight() / 360f) / (float) win.getGuiScale();
        float fit = Math.min((screenW - 4f) / w, (screenH - 4f) / h);
        return Math.max(0.25f, Math.min(wanted, fit));
    }
}
