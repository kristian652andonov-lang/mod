package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Weapon level-up: a rune circle on the ground that grows with the weapon's level, gaining a small orbiting circle at
 * levels 25, 50 and 75 (four, and a much larger circle, at the max level), and columns of light that shoot up to the
 * sky, then lift off the ground and dissolve upwards.
 */
public class LevelUpVfx extends Vfx {
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final int MOTES = 40;

    private final Vec3 ground;
    private final int color;
    private final int light;
    private final boolean max;
    private final float radius;
    private final int satellites;
    private final float height;
    private final int pillars;
    private final float[] moteAngle = new float[MOTES];
    private final float[] moteRadius = new float[MOTES];
    private final float[] moteSpeed = new float[MOTES];
    private final float[] moteOffset = new float[MOTES];

    public LevelUpVfx(Vec3 ground, int level, boolean max, boolean unlock, int color, int light, long seed) {
        super(max ? 125 : 75);
        this.ground = ground;
        this.color = color;
        this.light = light;
        this.max = max;
        float k = Math.min(1f, level / 100f);
        this.radius = max ? 6.5f : 1.6f + 2.6f * k + (unlock ? 0.3f : 0f);
        this.satellites = max ? 4 : level >= 75 ? 3 : level >= 50 ? 2 : level >= 25 ? 1 : 0;
        this.height = max ? 110f : 40f + 30f * k;
        this.pillars = max ? 12 : 6 + satellites;
        RandomSource r = RandomSource.create(seed);
        for (int i = 0; i < MOTES; i++) {
            moteAngle[i] = r.nextFloat() * (float) Math.PI * 2;
            moteRadius[i] = 0.3f + r.nextFloat() * radius * 0.9f;
            moteSpeed[i] = 0.12f + r.nextFloat() * 0.25f;
            moteOffset[i] = r.nextFloat() * 6f;
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        float t = progress(ctx.partial);
        float grow = easeOut(Math.min(1f, time / 12f));
        float hold = max ? 0.8f : 0.55f;
        float fade = t < hold ? 1f : 1f - easeInOut((t - hold) / (1f - hold));
        float a = Math.min(1f, time / 4f) * fade;
        if (a <= 0.003f) return;
        float r = radius * (0.35f + 0.65f * grow);
        int mid = Colors.lerpRgb(color, light, 0.35f);

        // Draws are grouped by render type: a consumer is only valid until the next getBuffer call.
        Vec3 c = ground.add(0, 0.05, 0);
        float orbit = r * (max ? 1.38f : 1.2f);
        float size = r * (max ? 0.27f : 0.32f);
        float[] satIn = new float[satellites];
        float[] satAng = new float[satellites];
        for (int i = 0; i < satellites; i++) {
            satIn[i] = easeOut(clamp01((time - 8 - i * 4) / 8f));
            satAng[i] = -time * 0.025f + (float) (i * Math.PI * 2 / satellites);
        }
        // max level: a tower of rune halos above the head, wide at the bottom and narrowing upwards, each tier
        // appearing a moment after the one below it
        float bob = 0.15f * (float) Math.sin(time * 0.08);
        float[] haloK = new float[HALO_R.length];
        Vec3[] halo = new Vec3[HALO_R.length];
        for (int k = 0; k < HALO_R.length; k++) {
            haloK[k] = max ? easeOut(clamp01((time - 10 - k * 5) / 15f)) : 0f;
            halo[k] = ground.add(0, HALO_Y[k] + bob * (1 + k * 0.3f), 0);
        }
        float hk = haloK[0];

        // ---- a dark, theme-coloured underlay so the runes read in daylight too ----
        var under = ctx.translucent(VfxTextures.RUNE_CIRCLE);
        ctx.disc(under, c.add(0, -0.005, 0), X, Z, r, time * 0.03f, Colors.alpha(0.45f * a, Colors.darken(color, 0.45f)));
        for (int i = 0; i < satellites; i++) {
            if (satIn[i] > 0) ctx.orbitDisc(under, c.add(0, 0.005, 0), X, Z, orbit, satAng[i], size * satIn[i], time * 0.08f + i,
                    Colors.alpha(0.45f * a * satIn[i], Colors.darken(color, 0.45f)));
        }

        // ---- soft glows under the circles ----
        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.disc(glow, c, X, Z, r * 1.5f, 0, Colors.alpha(0.3f * a, color));
        // each halo glows, and its rim is a band of light with some height so it still reads seen edge-on
        for (int k = 0; k < HALO_R.length; k++) {
            float hr = HALO_R[k] * haloK[k];
            if (hr <= 0.01f) continue;
            ctx.disc(glow, halo[k], X, Z, hr * 1.6f, 0, Colors.alpha(0.3f * a * haloK[k], color));
            rimBand(ctx, glow, halo[k], hr * 0.97f, 0.16f, Colors.alpha(0.75f * a * haloK[k], light));
        }
        for (int i = 0; i < satellites; i++) {
            if (satIn[i] > 0) ctx.orbitDisc(glow, c, X, Z, orbit, satAng[i], size * 1.5f * satIn[i], 0, Colors.alpha(0.35f * a * satIn[i], color));
        }

        // ---- rune circles: the big one, an inner one, and the small ones orbiting it (each appearing after the last) ----
        var runes = ctx.energy(VfxTextures.RUNE_CIRCLE);
        ctx.disc(runes, c, X, Z, r, time * 0.03f, Colors.alpha(0.95f * a, color));
        ctx.disc(runes, c.add(0, 0.01, 0), X, Z, r * 0.55f, -time * 0.05f, Colors.alpha(0.7f * a, mid));
        if (max) ctx.disc(runes, c.add(0, -0.01, 0), X, Z, r * 1.32f, -time * 0.015f, Colors.alpha(0.55f * a, mid));
        for (int i = 0; i < satellites; i++) {
            float in = satIn[i];
            if (in <= 0) continue;
            ctx.orbitDisc(runes, c.add(0, 0.01, 0), X, Z, orbit, satAng[i], size * in, time * 0.08f + i, Colors.alpha(0.95f * a * in, color));
            ctx.orbitDisc(runes, c.add(0, 0.02, 0), X, Z, orbit, satAng[i], size * 0.5f * in, -time * 0.12f, Colors.alpha(0.7f * a * in, mid));
        }
        // the halos: three stacked layers each so they have some thickness, turning in alternate directions
        for (int k = 0; k < HALO_R.length; k++) {
            float hr = HALO_R[k] * haloK[k];
            if (hr <= 0.01f) continue;
            float spin = (k % 2 == 0 ? 1 : -1) * time * (0.06f + 0.02f * k);
            for (int layer = -1; layer <= 1; layer++) {
                ctx.disc(runes, halo[k].add(0, layer * 0.05, 0), X, Z, hr * (1 - Math.abs(layer) * 0.04f), spin,
                        Colors.alpha((layer == 0 ? 0.95f : 0.55f) * a * haloK[k], layer == 0 ? light : color));
            }
        }
        if (hk > 0) ctx.satellites(runes, halo[0], X, Z, HALO_R[0] * 1.25f * hk, 0.4f * hk, 4, -time * 0.05f, time * 0.1f,
                Colors.alpha(0.85f * a * hk, color));
        // the track the small circles orbit on
        if (satellites > 0) ring(ctx, c, orbit, size * 0.05f, Colors.alpha(0.4f * a, color));

        // ---- columns of light to the sky (all soft halves first, then all cores) ----
        float rise = easeOut(Math.min(1f, time / (max ? 16f : 12f)));
        float lift = t < hold ? 0f : easeIn((t - hold) / (1f - hold)) * 0.7f;
        int n = pillars + 1;
        Vec3[] pb = new Vec3[n];
        float[] top = new float[n], bottom = new float[n], width = new float[n], pa = new float[n], ptime = new float[n];
        int[] tint = new int[n];
        pb[0] = ground;
        top[0] = height * rise;
        bottom[0] = height * lift;
        width[0] = max ? 2.2f : 1.0f + radius * 0.12f;
        pa[0] = a;
        ptime[0] = time;
        tint[0] = mid;
        for (int i = 0; i < pillars; i++) {
            float pr = easeOut(clamp01((time - 2 - i * 0.8f) / 12f));
            double ang = i * Math.PI * 2 / pillars + time * 0.01;
            pb[i + 1] = ground.add(Math.cos(ang) * r * 0.92, 0, Math.sin(ang) * r * 0.92);
            top[i + 1] = height * 0.55f * pr;
            bottom[i + 1] = height * 0.55f * lift;
            width[i + 1] = max ? 0.55f : 0.35f;
            pa[i + 1] = pr <= 0 ? 0 : a * 0.6f;
            ptime[i + 1] = time + i * 7;
            tint[i + 1] = color;
        }
        var soft = ctx.additive(VfxTextures.GLOW);
        for (int i = 0; i < n; i++) pillar(ctx, soft, pb[i], top[i], bottom[i], width[i] * 2.6f, pa[i] * 0.35f, color, 0.5f, 0.5f);
        var streak = ctx.energy(VfxTextures.STREAK);
        for (int i = 0; i < n; i++) {
            float scroll = -ptime[i] * 0.03f;
            pillar(ctx, streak, pb[i], top[i], bottom[i], width[i], pa[i] * 0.75f, tint[i], scroll, scroll + top[i] / 12f);
        }

        // ---- motes spiralling upwards ----
        float span = height * 0.35f;
        Vec3[] mp = new Vec3[MOTES];
        float[] ma = new float[MOTES];
        for (int i = 0; i < MOTES; i++) {
            float y = (moteOffset[i] + time * moteSpeed[i] * (max ? 2.2f : 1.5f)) % span;
            float f = y / span;
            ma[i] = a * (1 - f) * Math.min(1f, y * 2f);
            double ang = moteAngle[i] + time * 0.05f;
            float mr = moteRadius[i] * (0.6f + 0.4f * grow) * (1 - f * 0.5f);
            mp[i] = ground.add(Math.cos(ang) * mr, y, Math.sin(ang) * mr);
        }
        var moteGlow = ctx.additive(VfxTextures.GLOW);
        for (int i = 0; i < MOTES; i++) if (ma[i] > 0.01f) ctx.billboard(moteGlow, mp[i], 0.35f, 0, Colors.alpha(0.5f * ma[i], color));
        var spark = ctx.additive(VfxTextures.SPARK);
        for (int i = 0; i < MOTES; i++) if (ma[i] > 0.01f) ctx.billboard(spark, mp[i], 0.16f, time * 0.2f + i, Colors.alpha(ma[i], mid));
    }

    /** Heights above the ground and radii of the max-level halo tiers, widest at the bottom. */
    private static final float[] HALO_Y = {3.2f, 3.95f, 4.6f, 5.15f};
    private static final float[] HALO_R = {1.6f, 1.15f, 0.8f, 0.5f};

    /** A short vertical band of light around a horizontal circle's rim (soft at its top and bottom edges). */
    private static void rimBand(VfxContext ctx, VertexConsumer vc, Vec3 c, float radius, float height, int argb) {
        int segs = 32;
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
            Vec3 p0 = c.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), p1 = c.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            ctx.vertex(vc, p0.add(0, -height / 2, 0), 0.5f, 1f, argb);
            ctx.vertex(vc, p1.add(0, -height / 2, 0), 0.5f, 1f, argb);
            ctx.vertex(vc, p1.add(0, height / 2, 0), 0.5f, 0f, argb);
            ctx.vertex(vc, p0.add(0, height / 2, 0), 0.5f, 0f, argb);
        }
    }

    /** Thin flat ring on the ground (the track the small circles orbit on). */
    private static void ring(VfxContext ctx, Vec3 c, float orbit, float w, int argb) {
        ctx.ring(ctx.energy(VfxTextures.WHITE), c, X, Z, orbit - w, orbit + w, ctx.segments(48), argb);
    }

    /**
     * One layer of a camera-facing column of light from {@code base + bottom} to {@code base + top}: brightest at the
     * ground, fading to nothing towards the top.
     */
    private void pillar(VfxContext ctx, VertexConsumer vc, Vec3 base, float top, float bottom, float width, float alpha, int tint, float u0, float u1) {
        if (top - bottom < 0.05f || alpha <= 0.003f) return;
        Vec3 toCam = ctx.cam.subtract(base);
        Vec3 side = new Vec3(toCam.z, 0, -toCam.x);
        if (side.lengthSqr() < 1e-6) side = X;
        strip(ctx, vc, base, side.normalize(), bottom, top, width, alpha, tint, 14, u0, u1);
    }

    /** Vertical strip split into segments so alpha can fall off with height; u0/u1 run bottom to top. */
    private void strip(VfxContext ctx, VertexConsumer vc, Vec3 base, Vec3 side, float y0, float y1, float width, float alpha, int tint,
                       int segs, float u0, float u1) {
        Vec3 half = side.scale(width * 0.5);
        for (int s = 0; s < segs; s++) {
            float fa = s / (float) segs, fb = (s + 1) / (float) segs;
            float ya = y0 + (y1 - y0) * fa, yb = y0 + (y1 - y0) * fb;
            int ca = Colors.alpha(alpha * falloff(ya, y0, y1), tint);
            int cb = Colors.alpha(alpha * falloff(yb, y0, y1), tint);
            Vec3 pa = base.add(0, ya, 0), pb = base.add(0, yb, 0);
            float ua = u0 + (u1 - u0) * fa, ub = u0 + (u1 - u0) * fb;
            ctx.vertex(vc, pa.subtract(half), ua, 1, ca);
            ctx.vertex(vc, pb.subtract(half), ub, 1, cb);
            ctx.vertex(vc, pb.add(half), ub, 0, cb);
            ctx.vertex(vc, pa.add(half), ua, 0, ca);
        }
    }

    /** Bright at the bottom, gone at the top; the bottom edge feathers in once the column has lifted off the ground. */
    private float falloff(float y, float y0, float y1) {
        float f = clamp01(y / height);
        float up = (float) Math.pow(1 - f, 1.6);
        float tip = clamp01((y1 - y) / 3f);
        float foot = y0 <= 0.01f ? 1f : clamp01((y - y0) / Math.max(1f, (y1 - y0) * 0.35f));
        return up * tip * foot;
    }
}
