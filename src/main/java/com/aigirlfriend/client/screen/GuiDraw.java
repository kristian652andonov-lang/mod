package com.aigirlfriend.client.screen;

import com.aigirlfriend.AIGirlfriend;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Small drawing helpers shared by the wheel, status screen and HUD. */
public final class GuiDraw {
    public static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "textures/gui/icons.png");
    public static final int ICON_HEART = 32, ICON_FOOD = 33, ICON_ENERGY = 34, ICON_ATTENTION = 35, ICON_TRUST = 36,
            ICON_HEALTH = 37, ICON_MOON = 38, ICON_REQUEST = 39, ICON_MEMORY = 40, ICON_HOME = 41;

    private GuiDraw() {}

    /** Filled ring segment (annulus slice) from angle a0 to a1 (radians, 0 = right, clockwise). */
    public static void arc(GuiGraphics g, float cx, float cy, float r0, float r1, float a0, float a1, int argb) {
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = g.pose().last().pose();
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) / 0.08f));
        for (int i = 0; i < steps; i++) {
            float s = a0 + (a1 - a0) * i / steps;
            float e = a0 + (a1 - a0) * (i + 1) / steps;
            float cs = Mth.cos(s), ss = Mth.sin(s), ce = Mth.cos(e), se = Mth.sin(e);
            vc.addVertex(m, cx + cs * r0, cy + ss * r0, 0).setColor(argb);
            vc.addVertex(m, cx + ce * r0, cy + se * r0, 0).setColor(argb);
            vc.addVertex(m, cx + ce * r1, cy + se * r1, 0).setColor(argb);
            vc.addVertex(m, cx + cs * r1, cy + ss * r1, 0).setColor(argb);
        }
        g.flush();
    }

    public static void icon(GuiGraphics g, int index, int x, int y) {
        int u = (index % 16) * 16, v = (index / 16) * 16;
        g.blit(ICONS, x, y, (float) u, (float) v, 16, 16, 256, 256);
    }

    /** Rounded-looking panel. */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int bg, int border) {
        g.fill(x0 + 1, y0, x1 - 1, y1, bg);
        g.fill(x0, y0 + 1, x1, y1 - 1, bg);
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, border);
        g.fill(x0 + 1, y1 - 1, x1 - 1, y1, border);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, border);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, border);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x, y, x + w, y + h, 0xAA000000);
        int fw = Math.round((w - 2) * Mth.clamp(fraction, 0f, 1f));
        g.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, color);
        g.fill(x + 1, y + 1, x + 1 + fw, y + 2, 0x40FFFFFF);
    }

    public static int withAlpha(int rgb, float alpha) {
        return ((int) (Mth.clamp(alpha, 0f, 1f) * 255f) << 24) | (rgb & 0xFFFFFF);
    }
}
