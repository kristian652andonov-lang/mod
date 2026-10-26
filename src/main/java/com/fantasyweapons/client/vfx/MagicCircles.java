package com.fantasyweapons.client.vfx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The mod's magic circles drawn as lines instead of a picture, so they stay sharp however big they are: every ring,
 * star, tick and rune is its own strip of {@link VfxTextures#LINE_CRISP}, as wide as the line it is, so a circle
 * hundreds of blocks across is as crisp as one at the caster's feet. The designs are the ones the circle textures were
 * drawn from ({@code tools/grand_circle.py}, {@code tools/gen_textures.py}), in a circle of radius 1.
 *
 * <p>The effects ask for a circle texture as they always have; {@link VfxContext} hands back a {@link Vertices} for it,
 * and {@link VfxContext#disc} draws the design in lines when it is handed one (still the texture when it is small).
 */
public final class MagicCircles {
    private MagicCircles() {
    }

    /** A design: rings and arcs ({@code cx, cy, r, width, alpha, from, to}) and strokes ({@code x0, y0, x1, y1, width, alpha}). */
    public static final class Design {
        private final float[] arcs;
        private final float[] strokes;
        /** Below this radius (blocks) the texture is drawn instead: its lines would be thinner than a pixel. */
        private final float smallest;

        private Design(Builder b, float smallest) {
            this.arcs = b.arcs.toArray();
            this.strokes = b.strokes.toArray();
            this.smallest = smallest;
        }

        /**
         * Draws the circle round {@code center} in the plane of {@code axisX}, {@code axisY}, {@code radius} blocks across
         * its edge, turned by {@code rotation} radians (as {@link VfxContext#disc}).
         */
        public void draw(VfxContext ctx, VertexConsumer vc, Vec3 center, Vec3 axisX, Vec3 axisY, float radius, float rotation, int argb) {
            double c = Math.cos(rotation), s = Math.sin(rotation);
            Vec3 ax = axisX.scale(c).add(axisY.scale(s)), ay = axisX.scale(-s).add(axisY.scale(c));
            for (int i = 0; i < arcs.length; i += 7) {
                float r = arcs[i + 2] * radius, w = arcs[i + 3] * radius * 0.5f;
                Vec3 at = center.add(ax.scale(arcs[i] * radius)).add(ay.scale(arcs[i + 1] * radius));
                float from = arcs[i + 5], to = arcs[i + 6];
                int segs = Math.max(3, (int) Math.ceil(segments(r) * (to - from) / (Math.PI * 2)));
                int col = fade(argb, arcs[i + 4]);
                ctx.arc(vc, at, ax, ay, Math.max(0, r - w), r + w, from, to, segs, (u, outer) -> col);
            }
            for (int i = 0; i < strokes.length; i += 6) {
                stroke(ctx, vc, center, ax, ay, radius, strokes[i], strokes[i + 1], strokes[i + 2], strokes[i + 3], strokes[i + 4], fade(argb, strokes[i + 5]));
            }
        }
    }

    /**
     * A written band of runes between two lines (the rune band texture's strip), along the arc from {@code from} to
     * {@code to} radians with {@code glyphs} runes on it, written only as far as {@code upto}.
     */
    public static void band(VfxContext ctx, VertexConsumer vc, Vec3 center, Vec3 ax, Vec3 ay, float rIn, float rOut, float from, float to,
                            float upto, int glyphs, int seed, int argb) {
        upto = Math.min(upto, to);
        if (upto <= from) return;
        float w = rOut - rIn, line = Math.max(0.06f, w * 0.05f);
        int segs = Math.max(3, (int) Math.ceil(segments(rOut) * (upto - from) / (Math.PI * 2)));
        ctx.arc(vc, center, ax, ay, rIn, rIn + line, from, upto, segs, (u, o) -> argb);
        ctx.arc(vc, center, ax, ay, rOut - line, rOut, from, upto, segs, (u, o) -> argb);
        float mid = (rIn + rOut) * 0.5f, size = w * 0.17f, stroke = Math.max(0.05f, w * 0.05f);
        float step = (to - from) / glyphs;
        for (int g = 0; g < glyphs; g++) {
            float a = from + (g + 0.5f) * step;
            if (a > upto) break;
            float[][] glyph = GLYPHS[Math.floorMod(seed * 31 + g * 7, GLYPHS.length)];
            // the glyph upright on the band: its x along the ring, its y out from the middle
            Vec3 out = ax.scale(Math.cos(a)).add(ay.scale(Math.sin(a)));
            Vec3 along = ax.scale(-Math.sin(a)).add(ay.scale(Math.cos(a)));
            Vec3 at = center.add(out.scale(mid));
            float[] st = glyph[0];
            for (int i = 0; i < st.length; i += 6) {
                stroke(ctx, vc, at, along, out, size, st[i], st[i + 1], st[i + 2], st[i + 3], stroke / size, argb);
            }
            float[] ar = glyph[1];
            for (int i = 0; i < ar.length; i += 7) {
                Vec3 p = at.add(along.scale(ar[i] * size)).add(out.scale(ar[i + 1] * size));
                float r = ar[i + 2] * size;
                ctx.arc(vc, p, along, out, Math.max(0, r - stroke * 0.4f), r + stroke * 0.4f, 0, (float) (Math.PI * 2), 12, (u, o) -> argb);
            }
        }
    }

    /** One straight stroke from (x0, y0) to (x1, y1) of the unit circle, {@code width} wide (in the circle's units). */
    private static void stroke(VfxContext ctx, VertexConsumer vc, Vec3 center, Vec3 ax, Vec3 ay, float radius, float x0, float y0, float x1, float y1,
                               float width, int argb) {
        double dx = x1 - x0, dy = y1 - y0, len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) return;
        dx /= len;
        dy /= len;
        double h = width * 0.5;
        // a little past each end, so the corners of a star close
        double ex = dx * h * 0.6, ey = dy * h * 0.6;
        Vec3 n = ax.scale(-dy * h * radius).add(ay.scale(dx * h * radius));
        Vec3 p0 = center.add(ax.scale((x0 - ex) * radius)).add(ay.scale((y0 - ey) * radius));
        Vec3 p1 = center.add(ax.scale((x1 + ex) * radius)).add(ay.scale((y1 + ey) * radius));
        ctx.quad(vc, p0.subtract(n), p1.subtract(n), p1.add(n), p0.add(n), 0, 0, 1, 1, argb, argb, argb, argb);
    }

    /** {@code argb} with its alpha scaled by {@code k}. */
    private static int fade(int argb, float k) {
        if (k >= 1) return argb;
        return (Math.round(((argb >>> 24) & 255) * k) << 24) | (argb & 0xFFFFFF);
    }

    /** Segments for a ring of radius {@code r} blocks: its straight pieces stray no more than about a twentieth of a block from the circle. */
    static int segments(float r) {
        float detail = Math.max(0.5f, Math.min(1.25f, com.fantasyweapons.config.ClientConfig.quality().density()));
        return Math.max(12, Math.min(512, (int) Math.ceil(Math.sqrt(Math.max(0, r)) * 11 * detail)));
    }

    // ------------------------------------------------------------------------------------------------------------
    // the designs
    // ------------------------------------------------------------------------------------------------------------

    /** Made-up runes for the rune bands (each its strokes, then its little circles), round (0, 0) on a lattice of step 1. */
    private static final float[][][] GLYPHS = glyphs(61);

    private static float[][][] glyphs(int n) {
        float[][][] out = new float[n][][];
        Random rng = new Random(3);
        for (int i = 0; i < n; i++) {
            Builder b = new Builder();
            b.glyph(0, 0, 1, 0, rng, 1);
            out[i] = new float[][]{b.strokes.toArray(), b.arcs.toArray()};
        }
        return out;
    }

    /** The ultimates' great circle (grand_circle). */
    public static final Design GRAND = grand();
    /** The smaller circles round it (sub_circle). */
    public static final Design SUB = sub();
    /** The rune circle every weapon draws under its powers (rune_circle). */
    public static final Design RUNE = rune();

    private static Design grand() {
        Builder b = new Builder();
        float w = 0.0059f;
        Random rng = new Random(7);
        b.ring(1, w + 0.004f);
        b.ring(0.965f, w);
        b.runeband(0.84f, 0.96f, 48, rng, w);
        b.ring(0.84f, w + 0.002f);
        b.dashed(0.81f, 96, 0.55f, w);
        b.ring(0.78f, w);
        for (int i = 0; i < 180; i++) {
            double a = Math.toRadians(i * 2);
            float l = i % 15 == 0 ? 0.05f : 0.02f;
            b.line((float) Math.cos(a) * 0.78f, (float) Math.sin(a) * 0.78f, (float) Math.cos(a) * (0.78f - l), (float) Math.sin(a) * (0.78f - l), w);
        }
        float[][] star = b.polygon(0.72f, 12, 5, 90, w);
        for (float[] p : star) b.circle(p[0], p[1], 0.035f, w);
        b.ring(0.55f, w + 0.002f);
        b.runeband(0.44f, 0.55f, 28, rng, w);
        b.ring(0.44f, w);
        b.polygon(0.42f, 6, 2, 90, w + 0.002f);
        b.polygon(0.3f, 4, 1, 45, w);
        b.polygon(0.3f, 4, 1, 0, w);
        b.ring(0.2f, w);
        b.dashed(0.17f, 24, 0.5f, w);
        b.ring(0.1f, w + 0.002f);
        b.polygon(0.1f, 3, 1, 90, w);
        return new Design(b, 0);
    }

    private static Design sub() {
        Builder b = new Builder();
        float w = 0.0118f;
        Random rng = new Random(11);
        b.ring(1, w + 0.004f);
        b.runeband(0.78f, 0.96f, 16, rng, w);
        b.ring(0.78f, w);
        float[][] tri = b.polygon(0.76f, 3, 1, 90, w + 0.004f);
        b.polygon(0.76f, 3, 1, -90, w);
        for (float[] p : tri) b.circle(p[0], p[1], 0.06f, w);
        b.ring(0.38f, w);
        b.dashed(0.3f, 12, 0.5f, w);
        b.ring(0.12f, w);
        return new Design(b, 0);
    }

    private static Design rune() {
        Builder b = new Builder();
        // the texture's 256 pixels are 2 units across, its circle of 0.47 of them the edge
        float px = 2 / 256f, k = 1 / 0.94f;
        b.ring(0.94f * k, 3 * px * k);
        b.ring(0.86f * k, 1.2f * px * k, 0.78f);
        b.ring(0.60f * k, 2 * px * k);
        b.ring(0.36f * k, 1.2f * px * k, 0.7f);
        Random rng = new Random(5);
        for (int i = 0; i < 36; i++) {
            double a = i / 36.0 * Math.PI * 2;
            if (i % 3 == 0) b.line((float) Math.cos(a) * 0.86f * k, (float) Math.sin(a) * 0.86f * k, (float) Math.cos(a) * 0.94f * k,
                    (float) Math.sin(a) * 0.94f * k, 2 * px * k);
            float gx = (float) Math.cos(a + 0.08) * 0.73f * k, gy = (float) Math.sin(a + 0.08) * 0.73f * k, g = 0.036f * k;
            float wd = 2 * px * k;
            switch (rng.nextInt(4)) {
                case 0 -> b.line(gx - g, gy, gx + g, gy, wd, 0.9f);
                case 1 -> b.circle(gx, gy, g * 0.7f, wd, 0.9f);
                case 2 -> {
                    b.line(gx, gy + g, gx + g, gy - g, wd * 0.6f, 0.9f);
                    b.line(gx + g, gy - g, gx - g, gy - g, wd * 0.6f, 0.9f);
                    b.line(gx - g, gy - g, gx, gy + g, wd * 0.6f, 0.9f);
                }
                default -> b.line(gx, gy - g, gx, gy + g, wd, 0.9f);
            }
        }
        for (int i = 0; i < 6; i++) {
            double a = i / 6.0 * Math.PI * 2, c = (i + 2) / 6.0 * Math.PI * 2;
            b.line((float) Math.cos(a) * 0.6f * k, (float) Math.sin(a) * 0.6f * k, (float) Math.cos(c) * 0.6f * k, (float) Math.sin(c) * 0.6f * k, 2 * px * k, 0.82f);
        }
        return new Design(b, 3);
    }

    // ------------------------------------------------------------------------------------------------------------
    // the effects' side: circle textures handed out as these
    // ------------------------------------------------------------------------------------------------------------

    /** DEVELOPMENT ONLY: draws the circles from their textures, as they were, to compare. */
    public static volatile boolean texturesOnly;

    /** The design drawn for a circle texture, or null for any other texture. */
    static Design design(ResourceLocation texture) {
        if (texture.equals(VfxTextures.GRAND_CIRCLE)) return GRAND;
        if (texture.equals(VfxTextures.SUB_CIRCLE)) return SUB;
        if (texture.equals(VfxTextures.RUNE_CIRCLE)) return RUNE;
        return null;
    }

    /** {@code vc} for {@code texture}, as a {@link Vertices} when the texture is a circle's (or the rune band's). */
    static VertexConsumer wrap(ResourceLocation texture, VertexConsumer vc, Supplier<VertexConsumer> lines) {
        Design d = design(texture);
        if (d != null) return new Vertices(vc, d, lines);
        if (texture.equals(VfxTextures.RUNE_BAND)) return new Vertices(vc, null, lines);
        return vc;
    }

    /**
     * A circle texture's vertices: whatever is drawn straight into it uses the texture, as before; a disc of it is drawn
     * in lines (into {@link #lines()}, the same kind of drawing with the line texture) once it is big enough.
     */
    public static final class Vertices implements VertexConsumer {
        private final VertexConsumer to;
        private final Design design;
        private final Supplier<VertexConsumer> lineSource;
        private VertexConsumer lines;

        Vertices(VertexConsumer to, Design design, Supplier<VertexConsumer> lines) {
            this.to = to;
            this.design = design;
            this.lineSource = lines;
        }

        /** The design, or null for the rune band. */
        public Design design() {
            return design;
        }

        /** Whether a disc this big is drawn in lines. */
        public boolean inLines(float radius) {
            return design != null && radius >= design.smallest && !texturesOnly;
        }

        public VertexConsumer lines() {
            if (lines == null) lines = lineSource.get();
            return lines;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            to.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            to.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            to.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            to.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            to.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            to.setNormal(x, y, z);
            return this;
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    /** Puts a design together: the drawing operations of the texture generator, in a unit circle (y up). */
    private static final class Builder {
        private final Floats arcs = new Floats();
        private final Floats strokes = new Floats();

        void ring(float r, float w) {
            ring(r, w, 1);
        }

        void ring(float r, float w, float alpha) {
            arcs.add(0, 0, r, w, alpha, 0, (float) (Math.PI * 2));
        }

        void circle(float x, float y, float r, float w) {
            circle(x, y, r, w, 1);
        }

        void circle(float x, float y, float r, float w, float alpha) {
            arcs.add(x, y, r, w, alpha, 0, (float) (Math.PI * 2));
        }

        void dashed(float r, int n, float frac, float w) {
            for (int i = 0; i < n; i++) {
                float a0 = (float) (i * Math.PI * 2 / n);
                arcs.add(0, 0, r, w, 1, a0, a0 + (float) (Math.PI * 2 / n * frac));
            }
        }

        void line(float x0, float y0, float x1, float y1, float w) {
            line(x0, y0, x1, y1, w, 1);
        }

        void line(float x0, float y0, float x1, float y1, float w, float alpha) {
            strokes.add(x0, y0, x1, y1, w, alpha);
        }

        /** The {@code n} corners on a circle of {@code r} (the first at {@code rotDeg}), each joined to the one {@code step} on. */
        float[][] polygon(float r, int n, int step, float rotDeg, float w) {
            float[][] p = new float[n][];
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(rotDeg + i * 360.0 / n);
                p[i] = new float[]{(float) (Math.cos(a) * r), (float) (Math.sin(a) * r)};
            }
            for (int i = 0; i < n; i++) line(p[i][0], p[i][1], p[(i + step) % n][0], p[(i + step) % n][1], w);
            return p;
        }

        /** A made-up rune round (cx, cy), {@code s} its lattice step, turned by {@code turn} radians: a few strokes on a 3x3 lattice. */
        void glyph(float cx, float cy, float s, double turn, Random rng, float w) {
            float[][] pts = new float[9][];
            double c = Math.cos(turn), sn = Math.sin(turn);
            for (int i = 0; i < 9; i++) {
                double x = (i % 3 - 1) * s, y = (1 - i / 3) * s * 1.3;
                pts[i] = new float[]{(float) (cx + x * c - y * sn), (float) (cy + x * sn + y * c)};
            }
            List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));
            java.util.Collections.shuffle(order, rng);
            int strokes = 2 + rng.nextInt(3);
            for (int k = 0; k < strokes; k++) {
                float[] a = pts[order.get(k)], b = pts[order.get(k + 1)];
                line(a[0], a[1], b[0], b[1], w);
            }
            if (rng.nextBoolean()) {
                float[] p = pts[order.get(4)];
                circle(p[0], p[1], s * 0.28f, w * 0.75f);
            }
        }

        /** {@code count} runes written round the band between {@code r0} and {@code r1}, each upright to the circle. */
        void runeband(float r0, float r1, int count, Random rng, float w) {
            float rm = (r0 + r1) / 2, s = (r1 - r0) * 0.28f;
            for (int i = 0; i < count; i++) {
                double a = i * Math.PI * 2 / count;
                glyph((float) Math.cos(a) * rm, (float) Math.sin(a) * rm, s, a - Math.PI / 2, rng, w);
            }
        }
    }

    /** A growing list of floats. */
    private static final class Floats {
        private float[] data = new float[256];
        private int size;

        void add(float... v) {
            if (size + v.length > data.length) data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, size + v.length));
            System.arraycopy(v, 0, data, size, v.length);
            size += v.length;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }
}
