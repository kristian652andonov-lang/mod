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
 * petals fold shut and it sinks back into the earth.
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

        VertexConsumer leaf = ctx.solid(VfxTextures.PETAL_VEIN);
        // sepals: broad green leaves lying out under the bloom
        for (int i = 0; i < 6; i++) {
            Vec3 out = dir(i * Math.PI / 3 + 0.26 + spin);
            float tilt = (float) (0.05 + (1 - Math.max(bloom, 0.3f)) * 0.9);
            petal(ctx, leaf, c.add(0, 0.04, 0), out, size * 1.15f, size * 0.3f, tilt, -0.15f, 0.25f,
                    Colors.darken(SEPAL, 0.35f), SEPAL, 1f);
        }
        // three rings of petals: the outer ones open widest, the inner ones stay cupped round the heart
        float[] len = {1f, 0.78f, 0.55f};
        int[] count = {8, 8, 6};
        float[] lift = {0.08f, 0.16f, 0.26f};
        float[] openTilt = {0.12f, 0.45f, 0.9f};
        for (int ring = 0; ring < 3; ring++) {
            for (int i = 0; i < count[ring]; i++) {
                Vec3 out = dir(i * Math.PI * 2 / count[ring] + ring * 0.39 + spin);
                float tilt = openTilt[ring] + (1 - bloom) * (1.45f - openTilt[ring]);
                float curl = 0.55f * bloom + 0.1f;
                petal(ctx, leaf, c.add(0, lift[ring] * size * 0.3, 0), out, size * len[ring], size * 0.27f * len[ring], tilt, curl,
                        0.35f + 0.25f * ring, Colors.lerpRgb(petalColor, Colors.darken(petalColor, 0.35f), 0.3f * ring), tipColor, 1f);
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

    /**
     * One modelled petal (or leaf): a strip rising from {@code base} along {@code out}, starting {@code tilt} radians
     * above horizontal and bending a further {@code curl} radians towards its tip, widest a little past the middle
     * and cupped across its width. Coloured from {@code c0} at the base to {@code c1} at the tip and lit from above.
     */
    static void petal(VfxContext ctx, VertexConsumer vc, Vec3 base, Vec3 out, float len, float width, float tilt, float curl, float cup,
                      int c0, int c1, float alpha) {
        final int segs = 5;
        Vec3 side = UP.cross(out).normalize();
        Vec3[] l = new Vec3[segs + 1], m = new Vec3[segs + 1], r = new Vec3[segs + 1];
        int[] col = new int[segs + 1];
        Vec3 p = base;
        for (int k = 0; k <= segs; k++) {
            float s = k / (float) segs;
            double th = tilt + curl * Math.pow(s, 1.5);
            Vec3 d = out.scale(Math.cos(th)).add(UP.scale(Math.sin(th)));
            Vec3 n = side.cross(d).normalize().scale(-1);
            if (n.y < 0) n = n.scale(-1);
            float w = width * (s < 0.6f ? 0.22f + 0.78f * (float) Math.sin(s / 0.6f * Math.PI / 2) : 0.08f + 0.92f * (float) Math.cos((s - 0.6f) / 0.4f * Math.PI / 2));
            m[k] = p;
            l[k] = p.subtract(side.scale(w)).add(n.scale(cup * w));
            r[k] = p.add(side.scale(w)).add(n.scale(cup * w));
            float shade = 0.55f + 0.45f * (float) Math.max(0, n.dot(LIGHT));
            col[k] = Colors.argb(Math.round(255 * alpha), Colors.scale(Colors.lerpRgb(c0, c1, s), shade * 1.1f));
            p = p.add(d.scale(len / segs));
        }
        for (int k = 0; k < segs; k++) {
            float v1 = 1 - k / (float) segs, v0 = 1 - (k + 1) / (float) segs;
            ctx.quad(vc, l[k], m[k], m[k + 1], l[k + 1], 0f, v0, 0.5f, v1, col[k], col[k], col[k + 1], col[k + 1]);
            ctx.quad(vc, m[k], r[k], r[k + 1], m[k + 1], 0.5f, v0, 1f, v1, col[k], col[k], col[k + 1], col[k + 1]);
        }
    }
}
