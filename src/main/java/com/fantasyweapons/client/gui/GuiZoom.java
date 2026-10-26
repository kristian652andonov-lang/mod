package com.fantasyweapons.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Sizes a menu laid out for a fixed area ({@code w} by {@code h} GUI units) by the window (see {@link
 * com.fantasyweapons.client.hud.UiScale#menu}): the same on screen at every GUI scale, so it neither shrinks into tiny print
 * at a small one nor fills the window at a large one.
 */
final class GuiZoom {
    private final int w, h;
    float zoom = 1;

    GuiZoom(int w, int h) {
        this.w = w;
        this.h = h;
    }

    /** The zoom for a screen {@code screenW} by {@code screenH} GUI units. */
    static float fit(int screenW, int screenH, int w, int h) {
        return com.fantasyweapons.client.hud.UiScale.menu(screenW, screenH, w, h);
    }

    void fit(int screenW, int screenH) {
        zoom = fit(screenW, screenH, w, h);
    }

    /** The menu's top-left corner, in its own (zoomed) units. */
    int left(int screenW) {
        return (int) ((screenW / zoom - w) / 2);
    }

    int top(int screenH) {
        return (int) ((screenH / zoom - h) / 2);
    }

    /** A point of the screen in the menu's units. */
    double in(double screen) {
        return screen / zoom;
    }

    /** A point of the menu (in its units) on the screen. */
    double out(double menu) {
        return menu * zoom;
    }

    void push(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().scale(zoom, zoom, 1);
    }

    void pop(GuiGraphics g) {
        g.pose().popPose();
    }

    /** Scissoring takes screen units: a rectangle of the menu, zoomed. */
    void scissor(GuiGraphics g, float x0, float y0, float x1, float y1) {
        g.enableScissor((int) Math.floor(x0 * zoom), (int) Math.floor(y0 * zoom), (int) Math.ceil(x1 * zoom), (int) Math.ceil(y1 * zoom));
    }
}
