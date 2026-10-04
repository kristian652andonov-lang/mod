package com.fantasyweapons.client.hud;

import com.fantasyweapons.client.render.FWRenderTypes;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Immediate-mode drawing helpers for the custom HUD and menus (float coordinates, gradients in both directions,
 * glowing borders, additive glows, arcs, bars, scaled/outlined text). Each primitive flushes so draw order is exact.
 */
public final class UiDraw {
    private UiDraw() {
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    // ------------------------------------------------------------------------------------------------------------
    // shapes
    // ------------------------------------------------------------------------------------------------------------

    public static void rect(GuiGraphics g, float x1, float y1, float x2, float y2, int argb) {
        quad(g, x1, y1, x2, y2, argb, argb, argb, argb);
    }

    public static void vGradient(GuiGraphics g, float x1, float y1, float x2, float y2, int top, int bottom) {
        quad(g, x1, y1, x2, y2, top, top, bottom, bottom);
    }

    public static void hGradient(GuiGraphics g, float x1, float y1, float x2, float y2, int left, int right) {
        quad(g, x1, y1, x2, y2, left, right, right, left);
    }

    /** Corners: top-left, top-right, bottom-right, bottom-left. */
    public static void quad(GuiGraphics g, float x1, float y1, float x2, float y2, int tl, int tr, int br, int bl) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        vc.addVertex(m, x1, y1, 0).setColor(tl);
        vc.addVertex(m, x1, y2, 0).setColor(bl);
        vc.addVertex(m, x2, y2, 0).setColor(br);
        vc.addVertex(m, x2, y1, 0).setColor(tr);
        g.flush();
    }

    public static void border(GuiGraphics g, float x1, float y1, float x2, float y2, float t, int argb) {
        rect(g, x1, y1, x2, y1 + t, argb);
        rect(g, x1, y2 - t, x2, y2, argb);
        rect(g, x1, y1 + t, x1 + t, y2 - t, argb);
        rect(g, x2 - t, y1 + t, x2, y2 - t, argb);
    }

    /** Border with a soft outer glow made of fading layers. */
    public static void glowBorder(GuiGraphics g, float x1, float y1, float x2, float y2, int rgb, float alpha, float spread) {
        int layers = 4;
        for (int i = layers; i >= 1; i--) {
            float o = spread * i / layers;
            float a = alpha * 0.18f * (1 - (i - 1) / (float) layers);
            border(g, x1 - o, y1 - o, x2 + o, y2 + o, 1f, Colors.alpha(a, rgb));
        }
        border(g, x1, y1, x2, y2, 1f, Colors.alpha(alpha, rgb));
    }

    /**
     * Fantasy panel: translucent dark body, element-tinted gradient sheen, thin glowing frame and bright corner
     * accents. {@code alpha} fades the whole panel.
     */
    public static void panel(GuiGraphics g, float x, float y, float w, float h, int theme, float alpha) {
        int body = Colors.alpha(0.78f * alpha, Colors.darken(theme, 0.88f));
        int body2 = Colors.alpha(0.86f * alpha, Colors.darken(theme, 0.94f));
        vGradient(g, x, y, x + w, y + h, body, body2);
        hGradient(g, x, y, x + w * 0.5f, y + h, Colors.alpha(0.10f * alpha, theme), Colors.alpha(0f, theme));
        vGradient(g, x + 1, y + 1, x + w - 1, y + Math.min(10, h * 0.3f), Colors.alpha(0.12f * alpha, Colors.brighten(theme, 0.4f)), Colors.alpha(0, theme));
        glowBorder(g, x, y, x + w, y + h, theme, 0.75f * alpha, 2.5f);
        float c = Math.min(7, Math.min(w, h) * 0.25f);
        int accent = Colors.alpha(alpha, Colors.brighten(theme, 0.45f));
        rect(g, x - 1, y - 1, x + c, y + 1, accent);
        rect(g, x - 1, y - 1, x + 1, y + c, accent);
        rect(g, x + w - c, y - 1, x + w + 1, y + 1, accent);
        rect(g, x + w - 1, y - 1, x + w + 1, y + c, accent);
        rect(g, x - 1, y + h - 1, x + c, y + h + 1, accent);
        rect(g, x - 1, y + h - c, x + 1, y + h + 1, accent);
        rect(g, x + w - c, y + h - 1, x + w + 1, y + h + 1, accent);
        rect(g, x + w - 1, y + h - c, x + w + 1, y + h + 1, accent);
    }

    /** Progress bar with gradient fill, moving shimmer and a bright leading edge. */
    public static void bar(GuiGraphics g, float x, float y, float w, float h, float fraction, int from, int to, float alpha, float shimmerTime) {
        fraction = Math.max(0, Math.min(1, fraction));
        rect(g, x, y, x + w, y + h, Colors.alpha(0.55f * alpha, 0x05030A));
        border(g, x - 1, y - 1, x + w + 1, y + h + 1, 1, Colors.alpha(0.35f * alpha, from));
        float fw = w * fraction;
        if (fw <= 0.01f) return;
        hGradient(g, x, y, x + fw, y + h, Colors.alpha(alpha, from), Colors.alpha(alpha, Colors.lerpRgb(from, to, fraction)));
        vGradient(g, x, y, x + fw, y + h * 0.45f, Colors.alpha(0.35f * alpha, 0xFFFFFF), Colors.alpha(0, 0xFFFFFF));
        if (shimmerTime >= 0) {
            float sx = x + ((shimmerTime * 0.6f) % 1.4f - 0.2f) * w;
            float sw = Math.max(6, w * 0.12f);
            float a = Math.max(x, sx - sw), b = Math.min(x + fw, sx + sw);
            if (b > a) {
                hGradient(g, a, y, (a + b) / 2, y + h, Colors.alpha(0, 0xFFFFFF), Colors.alpha(0.45f * alpha, 0xFFFFFF));
                hGradient(g, (a + b) / 2, y, b, y + h, Colors.alpha(0.45f * alpha, 0xFFFFFF), Colors.alpha(0, 0xFFFFFF));
            }
        }
        rect(g, x + fw - 1, y - 1, x + fw, y + h + 1, Colors.alpha(alpha, Colors.brighten(to, 0.6f)));
    }

    /** Annular arc (radians, 0 = up, clockwise). */
    public static void arc(GuiGraphics g, float cx, float cy, float rIn, float rOut, float a0, float a1, int argb, int segs) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < segs; i++) {
            float b0 = a0 + (a1 - a0) * i / segs, b1 = a0 + (a1 - a0) * (i + 1) / segs;
            float s0 = (float) Math.sin(b0), c0 = (float) -Math.cos(b0), s1 = (float) Math.sin(b1), c1 = (float) -Math.cos(b1);
            emit(vc, m, cx + s0 * rIn, cy + c0 * rIn, cx + s0 * rOut, cy + c0 * rOut, cx + s1 * rOut, cy + c1 * rOut,
                    cx + s1 * rIn, cy + c1 * rIn, argb, argb, argb, argb);
        }
        g.flush();
    }

    /** Diamond (rotated square) — node shape for the ability tree. */
    public static void diamond(GuiGraphics g, float cx, float cy, float r, int argb) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        emit(vc, m, cx, cy - r, cx - r, cy, cx, cy + r, cx + r, cy, argb, argb, argb, argb);
        g.flush();
    }

    /** Thick line between two points. */
    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float width, int c1, int c2) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3) return;
        float nx = -dy / len * width * 0.5f, ny = dx / len * width * 0.5f;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        emit(vc, m, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, c1, c1, c2, c2);
        g.flush();
    }

    /**
     * Emits a coloured quad with the winding the culling GUI render type expects (negative shoelace area in
     * y-down screen space, same as {@code GuiGraphics.fill}); reverses the vertex order when needed.
     */
    private static void emit(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy,
                             int ca, int cb, int cc, int cd) {
        float area = (ax * by - bx * ay) + (bx * cy - cx * by) + (cx * dy - dx * cy) + (dx * ay - ax * dy);
        if (area <= 0) {
            vc.addVertex(m, ax, ay, 0).setColor(ca);
            vc.addVertex(m, bx, by, 0).setColor(cb);
            vc.addVertex(m, cx, cy, 0).setColor(cc);
            vc.addVertex(m, dx, dy, 0).setColor(cd);
        } else {
            vc.addVertex(m, dx, dy, 0).setColor(cd);
            vc.addVertex(m, cx, cy, 0).setColor(cc);
            vc.addVertex(m, bx, by, 0).setColor(cb);
            vc.addVertex(m, ax, ay, 0).setColor(ca);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // textures
    // ------------------------------------------------------------------------------------------------------------

    public static void texture(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, int argb) {
        texturedQuad(g, FWRenderTypes.guiTextured(tex), x, y, w, h, 0, 0, 1, 1, argb);
    }

    public static void textureUv(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
        texturedQuad(g, FWRenderTypes.guiTextured(tex), x, y, w, h, u0, v0, u1, v1, argb);
    }

    /** Additive soft glow centred on (cx, cy). */
    public static void glow(GuiGraphics g, float cx, float cy, float size, int argb) {
        texturedQuad(g, FWRenderTypes.guiAdditive(VfxTextures.GLOW), cx - size / 2, cy - size / 2, size, size, 0, 0, 1, 1, argb);
    }

    public static void additive(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, int argb) {
        texturedQuad(g, FWRenderTypes.guiAdditive(tex), x, y, w, h, 0, 0, 1, 1, argb);
    }

    private static void texturedQuad(GuiGraphics g, RenderType type, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(type);
        vc.addVertex(m, x, y, 0).setUv(u0, v0).setColor(argb);
        vc.addVertex(m, x, y + h, 0).setUv(u0, v1).setColor(argb);
        vc.addVertex(m, x + w, y + h, 0).setUv(u1, v1).setColor(argb);
        vc.addVertex(m, x + w, y, 0).setUv(u1, v0).setColor(argb);
        g.flush();
    }

    // ------------------------------------------------------------------------------------------------------------
    // text
    // ------------------------------------------------------------------------------------------------------------

    public static void text(GuiGraphics g, String s, float x, float y, int argb, float scale, boolean shadow) {
        if ((argb >>> 24) < 5) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font(), s, 0, 0, argb, shadow);
        g.pose().popPose();
    }

    public static void text(GuiGraphics g, Component s, float x, float y, int argb, float scale, boolean shadow) {
        if ((argb >>> 24) < 5) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font(), s, 0, 0, argb, shadow);
        g.pose().popPose();
    }

    public static void textCentered(GuiGraphics g, String s, float cx, float y, int argb, float scale, boolean shadow) {
        text(g, s, cx - font().width(s) * scale / 2f, y, argb, scale, shadow);
    }

    public static void textRight(GuiGraphics g, String s, float rx, float y, int argb, float scale, boolean shadow) {
        text(g, s, rx - font().width(s) * scale, y, argb, scale, shadow);
    }

    /** Heavy display text: dark outline + coloured fill (pixel-fantasy title look). */
    public static void title(GuiGraphics g, String s, float cx, float y, int argb, float scale) {
        if ((argb >>> 24) < 5) return;
        float x = cx - font().width(s) * scale / 2f;
        int outline = Colors.argb((argb >>> 24) * 3 / 4, 0x0A0612);
        float o = Math.max(0.5f, scale * 0.5f);
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2;
            text(g, s, x + (float) Math.cos(a) * o, y + (float) Math.sin(a) * o, outline, scale, false);
        }
        text(g, s, x, y, argb, scale, false);
    }

    public static int wrap(GuiGraphics g, String s, float x, float y, int maxWidth, int argb, float scale, int lineHeight) {
        List<FormattedCharSequence> lines = font().split(Component.literal(s), (int) (maxWidth / scale));
        float yy = y;
        for (FormattedCharSequence line : lines) {
            g.pose().pushPose();
            g.pose().translate(x, yy, 0);
            g.pose().scale(scale, scale, 1);
            g.drawString(font(), line, 0, 0, argb, false);
            g.pose().popPose();
            yy += lineHeight;
        }
        return lines.size();
    }
}
