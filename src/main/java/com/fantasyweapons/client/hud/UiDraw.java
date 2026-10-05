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
 * The look is an old tome: tooled leather inside bevelled bronze frames with gold inlay and gem-set corners, carved
 * bronze buttons, gold dividers, medallion sockets and crimson ribbon banners, with parchment-coloured ink.
 */
public final class UiDraw {
    public static final ResourceLocation FRAME = com.fantasyweapons.FantasyWeapons.id("textures/gui/fantasy/frame.png");
    public static final ResourceLocation LEATHER = com.fantasyweapons.FantasyWeapons.id("textures/gui/fantasy/leather.png");
    public static final ResourceLocation BUTTON = com.fantasyweapons.FantasyWeapons.id("textures/gui/fantasy/button.png");
    public static final ResourceLocation DIVIDER = com.fantasyweapons.FantasyWeapons.id("textures/gui/fantasy/divider.png");
    public static final ResourceLocation MEDALLION = com.fantasyweapons.FantasyWeapons.id("textures/gui/fantasy/medallion.png");
    public static final ResourceLocation BANNER = com.fantasyweapons.FantasyWeapons.id("textures/gui/fantasy/banner.png");
    public static final ResourceLocation BAR = com.fantasyweapons.FantasyWeapons.id("textures/gui/fantasy/bar.png");

    /** Parchment ink: values. */
    public static final int INK = 0xEFE2C4;
    /** Faded ink: labels. */
    public static final int INK_MUTED = 0xB49F7A;
    /** Gold leaf: headings. */
    public static final int GOLD = 0xE8C26A;
    public static final int GOLD_LIGHT = 0xFFE7A8;
    public static final int GOOD = 0x9CD67A;
    public static final int BAD = 0xE0705A;
    public static final int ARCANE = 0xC9A8F0;

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
     * Fantasy panel: tooled leather body darkened towards its edges, the faintest wash of the weapon's colour, inside
     * an ornate bronze frame with gem-set corners. {@code alpha} fades the whole panel.
     */
    public static void panel(GuiGraphics g, float x, float y, float w, float h, int theme, float alpha) {
        if (alpha <= 0.01f) return;
        tile(g, LEATHER, x + 1, y + 1, w - 2, h - 2, 48, Colors.alpha(0.97f * alpha, 0xFFFFFF));
        hGradient(g, x + 1, y + 1, x + w * 0.6f, y + h - 1, Colors.alpha(0.07f * alpha, theme), Colors.alpha(0f, theme));
        float e = Math.min(14, Math.min(w, h) * 0.3f);
        vGradient(g, x + 1, y + 1, x + w - 1, y + e, Colors.alpha(0.45f * alpha, 0x000000), 0);
        vGradient(g, x + 1, y + h - e, x + w - 1, y + h - 1, 0, Colors.alpha(0.5f * alpha, 0x000000));
        hGradient(g, x + 1, y + 1, x + e, y + h - 1, Colors.alpha(0.35f * alpha, 0x000000), 0);
        hGradient(g, x + w - e, y + 1, x + w - 1, y + h - 1, 0, Colors.alpha(0.35f * alpha, 0x000000));
        float b = Math.min(9, Math.min(w, h) / 2f);
        nineSlice(g, FRAME, x - 2, y - 2, w + 4, h + 4, b, 96, 96, 24, 0, 96, Colors.alpha(alpha, 0xFFFFFF));
    }

    /** Progress bar: a dark groove in a bronze frame, filled with a soft gradient and a gentle passing gleam. */
    public static void bar(GuiGraphics g, float x, float y, float w, float h, float fraction, int from, int to, float alpha, float shimmerTime) {
        fraction = Math.max(0, Math.min(1, fraction));
        rect(g, x, y, x + w, y + h, Colors.alpha(0.85f * alpha, 0x0E0804));
        float fw = w * fraction;
        if (fw > 0.01f) barFill(g, x, y, w, h, fw, from, to, alpha, shimmerTime);
        nineSlice(g, BAR, x - 2, y - 2, w + 4, h + 4, Math.min(3, (h + 4) / 2f), 64, 12, 5, 0, 12, Colors.alpha(alpha, 0xFFFFFF));
    }

    private static void barFill(GuiGraphics g, float x, float y, float w, float h, float fw, int from, int to, float alpha, float shimmerTime) {
        float fraction = fw / w;
        hGradient(g, x, y, x + fw, y + h, Colors.alpha(alpha, from), Colors.alpha(alpha, Colors.lerpRgb(from, to, fraction)));
        vGradient(g, x, y, x + fw, y + h * 0.45f, Colors.alpha(0.35f * alpha, 0xFFFFFF), Colors.alpha(0, 0xFFFFFF));
        if (shimmerTime >= 0) {
            float sx = x + ((shimmerTime * 0.25f) % 1.6f - 0.3f) * w;
            float sw = Math.max(6, w * 0.15f);
            float a = Math.max(x, sx - sw), b = Math.min(x + fw, sx + sw);
            if (b > a) {
                hGradient(g, a, y, (a + b) / 2, y + h, Colors.alpha(0, 0xFFF4D0), Colors.alpha(0.22f * alpha, 0xFFF4D0));
                hGradient(g, (a + b) / 2, y, b, y + h, Colors.alpha(0.22f * alpha, 0xFFF4D0), Colors.alpha(0, 0xFFF4D0));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // fantasy ornaments
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Draws a 9-slice from a region of a texture: corners of {@code border} display pixels stay unscaled, edges and
     * centre stretch. {@code texBorder} is the corner size in texture pixels; the region starts at row {@code v0} and
     * is {@code regionH} rows tall (for stacked button states).
     */
    public static void nineSlice(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, float border, int texW, int texH,
                                 int texBorder, int v0, int regionH, int argb) {
        float b = Math.min(border, Math.min(w, h) / 2f);
        float ub = texBorder / (float) texW, vb = texBorder / (float) texH;
        float vt = v0 / (float) texH, vbtm = (v0 + regionH) / (float) texH;
        float[] xs = {x, x + b, x + w - b, x + w};
        float[] ys = {y, y + b, y + h - b, y + h};
        float[] us = {0, ub, 1 - ub, 1};
        float[] vs = {vt, vt + vb, vbtm - vb, vbtm};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (xs[i + 1] - xs[i] <= 0.01f || ys[j + 1] - ys[j] <= 0.01f) continue;
                textureUv(g, tex, xs[i], ys[j], xs[i + 1] - xs[i], ys[j + 1] - ys[j], us[i], vs[j], us[i + 1], vs[j + 1], argb);
            }
        }
    }

    /** Fills a rectangle with a repeating texture, {@code tile} display pixels per repeat. */
    public static void tile(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, float tile, int argb) {
        textureUv(g, tex, x, y, w, h, x / tile, y / tile, (x + w) / tile, (y + h) / tile, argb);
    }

    /** A carved bronze button: state 0 normal, 1 hover, 2 disabled. */
    public static void button(GuiGraphics g, float x, float y, float w, float h, int state, float alpha) {
        nineSlice(g, BUTTON, x, y, w, h, Math.min(5, h / 2f), 64, 72, 8, state * 24, 24, Colors.alpha(alpha, 0xFFFFFF));
    }

    /** A gold flourish divider centred on {@code cx}. */
    public static void divider(GuiGraphics g, float cx, float y, float w, float alpha) {
        texture(g, DIVIDER, cx - w / 2, y - w / 24, w, w / 12, Colors.alpha(alpha, 0xFFFFFF));
    }

    /** A gold-rimmed round socket of radius {@code r}. */
    public static void medallion(GuiGraphics g, float cx, float cy, float r, int argb) {
        texture(g, MEDALLION, cx - r, cy - r, r * 2, r * 2, argb);
    }

    /** A crimson ribbon banner centred on {@code cx}, swallowtail ends keeping their proportions. */
    public static void banner(GuiGraphics g, float cx, float y, float w, float h, float alpha) {
        float end = h * 40f / 48f;
        float x = cx - w / 2;
        int c = Colors.alpha(alpha, 0xFFFFFF);
        textureUv(g, BANNER, x, y, end, h, 0, 0, 40 / 256f, 1, c);
        textureUv(g, BANNER, x + end, y, w - end * 2, h, 40 / 256f, 0, 216 / 256f, 1, c);
        textureUv(g, BANNER, x + w - end, y, end, h, 216 / 256f, 0, 1, 1, c);
    }

    /** "UNLOCKED AT" → "Unlocked At": labels read like a book, not a terminal. Numbers and symbols are kept as is. */
    public static String titleCase(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean start = true;
        for (char ch : s.toCharArray()) {
            if (Character.isLetter(ch)) {
                b.append(start ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
                start = false;
            } else {
                b.append(ch);
                start = ch == ' ' || ch == '(' || ch == '-' || ch == '/';
            }
        }
        return b.toString();
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
        int outline = Colors.argb((argb >>> 24) * 3 / 4, 0x1A0E06);
        float o = Math.max(0.5f, scale * 0.5f);
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2;
            text(g, s, x + (float) Math.cos(a) * o, y + (float) Math.sin(a) * o, outline, scale, false);
        }
        text(g, s, x, y, argb, scale, false);
    }

    public static int wrap(GuiGraphics g, String s, float x, float y, int maxWidth, int argb, float scale, int lineHeight) {
        return wrap(g, Component.literal(s), x, y, maxWidth, argb, scale, lineHeight);
    }

    /** Wrapped text with its own style (italic lore, coloured runs). */
    public static int wrap(GuiGraphics g, Component s, float x, float y, int maxWidth, int argb, float scale, int lineHeight) {
        if ((argb >>> 24) < 5) return 0;
        List<FormattedCharSequence> lines = font().split(s, (int) (maxWidth / scale));
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
