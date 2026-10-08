package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/**
 * A giant modelled flower: it pushes up out of the ground as a closed bud, then unfurls three rings of cupped,
 * curling petals over a collar of sepals, with glowing stamens and pollen rising from its heart. At the end the
 * petals fold shut and it sinks back into the earth. No two petals are alike - each has its own length, width,
 * angle, droop and tint, ruffled edges and a deep base fading to a pale tip - so it reads as a living flower.
 */
public class FlowerVfx extends Vfx {
    static final Vec3 UP = new Vec3(0, 1, 0);
    static final Vec3 LIGHT = new Vec3(0.3, 0.9, 0.3).normalize();
    private static final int SEPAL = 0x3F8F3A;

    private final Vec3 center;
    private final float radius;
    private final int petalColor;
    private final int tipColor;
    private final int coreColor;
    private int openTicks = 20;
    private int closeTicks = 8;

    public FlowerVfx(Vec3 center, float radius, int petalColor, int tipColor, int coreColor, int lifetime) {
        super(lifetime);
        this.center = center;
        this.radius = radius;
        this.petalColor = petalColor;
        this.tipColor = tipColor;
        this.coreColor = coreColor;
    }

    public FlowerVfx timing(int open, int close) {
        this.openTicks = Math.max(1, open);
        this.closeTicks = Math.max(8, close);
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        float rise = easeOut(Math.min(1, time / 10f));
        float open = easeInOut(clamp01((time - 6) / openTicks));
        float close = clamp01((time - (lifetime - closeTicks)) / closeTicks);
        float bloom = open * (1 - easeInOut(close));
        float size = radius * (0.35f + 0.65f * rise) * (1 - 0.5f * easeIn(close));
        Vec3 c = center.add(0, -radius * 0.25 * (1 - rise) - radius * 0.3 * easeIn(close), 0);
        float spin = time * 0.004f;

        VertexConsumer leaf = ctx.cutout(VfxTextures.LEAF_SOFT);
        // sepals: broad green leaves lying out under the bloom
        for (int i = 0; i < 6; i++) {
            float v = vary(i, 0);
            Vec3 out = dir(i * Math.PI / 3 + 0.26 + spin + (v - 0.5) * 0.3);
            float tilt = (float) (0.05 + (1 - Math.max(bloom, 0.3f)) * 0.9) + (v - 0.5f) * 0.12f;
            petal(ctx, leaf, c.add(0, 0.04, 0), out, size * 1.15f * (0.85f + 0.3f * vary(i, 1)), size * 0.3f * (0.85f + 0.3f * vary(i, 2)), tilt,
                    -0.15f - 0.15f * vary(i, 3), 0.25f, Colors.darken(SEPAL, 0.45f), Colors.scale(SEPAL, 0.8f + 0.2f * v),
                    Colors.lerpRgb(Colors.scale(SEPAL, 0.85f + 0.25f * v), 0x9AA85A, 0.25f), 1f, i * 31L);
        }
        VertexConsumer petals = ctx.cutout(VfxTextures.PETAL_SOFT);
        // three rings of petals: the outer ones open widest, the inner ones stay cupped round the heart
        float[] len = {1f, 0.78f, 0.55f};
        int[] count = {8, 8, 6};
        float[] lift = {0.08f, 0.16f, 0.26f};
        float[] openTilt = {0.12f, 0.45f, 0.9f};
        for (int ring = 0; ring < 3; ring++) {
            for (int i = 0; i < count[ring]; i++) {
                int id = ring * 16 + i + 8;
                float v = vary(id, 0);
                Vec3 out = dir(i * Math.PI * 2 / count[ring] + ring * 0.39 + spin + (vary(id, 1) - 0.5) * 0.35);
                float tilt = openTilt[ring] + (1 - bloom) * (1.45f - openTilt[ring]) + (vary(id, 2) - 0.5f) * 0.25f * bloom;
                float curl = (0.45f + 0.3f * vary(id, 3)) * bloom + 0.1f;
                int base = Colors.darken(petalColor, 0.35f + 0.15f * ring);
                int body = Colors.scale(Colors.lerpRgb(petalColor, Colors.darken(petalColor, 0.3f), 0.25f * ring), 0.85f + 0.25f * v);
                int tip = vary(id, 4) > 0.8f ? Colors.lerpRgb(tipColor, 0xC8A27A, 0.35f) : tipColor;
                petal(ctx, petals, c.add(0, lift[ring] * size * 0.3, 0), out, size * len[ring] * (0.85f + 0.3f * vary(id, 5)),
                        size * 0.27f * len[ring] * (0.8f + 0.4f * vary(id, 6)), tilt, curl, 0.35f + 0.25f * ring, base, Colors.lerpRgb(body, tip, 0.15f),
                        tip, 1f, id * 97L);
            }
        }

        // stamens: fine stalks with glowing pollen heads
        Vec3 heart = c.add(0, size * 0.12, 0);
        Vec3[] heads = new Vec3[12];
        for (int i = 0; i < 12; i++) {
            Vec3 out = dir(i * Math.PI * 2 / 12 + spin * 3);
            heads[i] = heart.add(out.scale(size * 0.12 * bloom)).add(0, size * (0.18 + 0.22 * bloom) + 0.05 * Math.sin(time * 0.1 + i), 0);
        }
        var stalk = ctx.solid(VfxTextures.WHITE);
        for (Vec3 h : heads) ctx.beam(stalk, heart, h, 0.035f * size / 3f + 0.02f, Colors.argb(255, Colors.lerpRgb(SEPAL, coreColor, 0.6f)), 0, 1);
        var glow = ctx.additive(VfxTextures.GLOW);
        for (Vec3 h : heads) ctx.billboard(glow, h, 0.22f + 0.1f * size / 3f, 0, Colors.alpha(0.95f * bloom + 0.05f, coreColor));
        ctx.billboard(glow, heart.add(0, size * 0.15, 0), size * 0.9f * bloom + 0.3f, 0, Colors.alpha(0.55f * (1 - close), coreColor));
        // pollen motes rising from the heart
        for (int i = 0; i < 16; i++) {
            float cyc = (time * 0.018f + i / 16f) % 1f;
            double a = i * 2.4 + time * 0.03;
            Vec3 p = heart.add(Math.cos(a) * size * 0.35 * cyc, 0.3 + cyc * size * 0.9, Math.sin(a) * size * 0.35 * cyc);
            ctx.billboard(glow, p, 0.2f, 0, Colors.alpha(bloom * (1 - cyc), coreColor));
        }
    }

    private static Vec3 dir(double a) {
        return new Vec3(Math.cos(a), 0, Math.sin(a));
    }

    /** A fixed pseudo-random 0..1 for petal {@code i}, property {@code k}. */
    private float vary(int i, int k) {
        long h = (long) (center.x * 73856093) ^ (long) (center.z * 19349663) ^ (i * 83492791L) ^ (k * 2654435761L);
        h ^= h >>> 17;
        h *= 0xED5AD4BBL;
        h ^= h >>> 11;
        return (h & 0xFFFF) / 65535f;
    }

    /**
     * One modelled petal (or leaf): a strip rising from {@code base} along {@code out}, starting {@code tilt} radians
     * above horizontal and bending a further {@code curl} radians towards its tip, widest a little past the middle
     * and cupped across its width. Coloured from {@code c0} at the base to {@code c1} at the tip and lit from above.
     */
    static void petal(VfxContext ctx, VertexConsumer vc, Vec3 base, Vec3 out, float len, float width, float tilt, float curl, float cup,
                      int c0, int c1, float alpha) {
        petal(ctx, vc, base, out, len, width, tilt, curl, cup, c0, Colors.lerpRgb(c0, c1, 0.55f), c1, alpha,
                (long) (base.x * 4711 + base.z * 1931 + out.x * 997));
    }

    /**
     * A petal coloured from a deep {@code c0} at its base through {@code c1} to a pale {@code c2} at the tip: a smooth,
     * cupped blade (five strips across, a dozen along) with a gently ruffled margin, softly lit from above and glowing a
     * little warmer where it is seen from underneath, as thin petals do against the light. Its outline - rounded tip,
     * narrow claw at the base - comes from the texture's alpha (drawn with a cutout render type).
     */
    static void petal(VfxContext ctx, VertexConsumer vc, Vec3 base, Vec3 out, float len, float width, float tilt, float curl, float cup,
                      int c0, int c1, int c2, float alpha, long seed) {
        final int segs = 12, cols = 5;
        Vec3 side = UP.cross(out).normalize();
        Vec3[][] grid = new Vec3[segs + 1][cols + 1];
        int[][] col = new int[segs + 1][cols + 1];
        double ph1 = (seed & 1023) / 1023.0 * 6.28, ph2 = ((seed >> 10) & 1023) / 1023.0 * 6.28;
        float twist = (((seed >> 20) & 255) / 255f - 0.5f) * 0.3f;
        Vec3 p = base;
        for (int k = 0; k <= segs; k++) {
            float s = k / (float) segs;
            double th = tilt + curl * Math.pow(s, 1.5);
            Vec3 d = out.scale(Math.cos(th)).add(UP.scale(Math.sin(th)));
            Vec3 n = side.cross(d).normalize().scale(-1);
            if (n.y < 0) n = n.scale(-1);
            // broad for most of its length; the texture rounds the tip and narrows the claw
            float w = width * (0.42f + 0.58f * smooth01(s / 0.38f)) * (1 - 0.3f * smooth01((s - 0.72f) / 0.28f));
            int rgb = s < 0.5f ? Colors.lerpRgb(c0, c1, s / 0.5f) : Colors.lerpRgb(c1, c2, (s - 0.5f) / 0.5f);
            for (int j = 0; j <= cols; j++) {
                float x = j / (float) cols * 2 - 1;                                   // -1 .. 1 across
                float ruffle = 1 + 0.06f * s * (float) Math.sin(s * 9 + (x < 0 ? ph1 : ph2)) * Math.abs(x);
                float lift = (cup * x * x + 0.1f * s * (float) Math.sin(s * 7 + ph2) * x * x) * w + twist * w * s * x;
                Vec3 q = p.add(side.scale(x * w * ruffle)).add(n.scale(lift));
                grid[k][j] = q;
                // the surface normal tips sideways with the cupping
                Vec3 nn = n.subtract(side.scale(2 * cup * x)).normalize();
                float lit = 0.62f + 0.38f * (float) Math.max(0, nn.dot(LIGHT));
                Vec3 view = ctx.cam.subtract(q);
                boolean under = view.dot(nn) < 0;
                int shaded = Colors.scale(rgb, lit);
                if (under) shaded = Colors.lerpRgb(shaded, Colors.lerpRgb(c2, 0xFFF4E0, 0.3f), 0.28f);   // light shining through
                // pale, thin margins
                shaded = Colors.lerpRgb(shaded, c2, 0.18f * x * x);
                col[k][j] = Colors.argb(Math.round(255 * alpha), shaded);
            }
            p = p.add(d.scale(len / segs));
        }
        for (int k = 0; k < segs; k++) {
            float v1 = 1 - k / (float) segs, v0 = 1 - (k + 1) / (float) segs;
            for (int j = 0; j < cols; j++) {
                float u0 = j / (float) cols, u1 = (j + 1) / (float) cols;
                ctx.quad(vc, grid[k][j], grid[k][j + 1], grid[k + 1][j + 1], grid[k + 1][j], u0, v0, u1, v1,
                        col[k][j], col[k][j + 1], col[k + 1][j + 1], col[k + 1][j]);
            }
        }
    }

    private static float smooth01(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
