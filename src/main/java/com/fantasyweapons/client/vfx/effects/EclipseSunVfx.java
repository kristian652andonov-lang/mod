package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

/**
 * A total eclipse hanging in the sky. A blazing sun appears and a black moon slides across it; at the moment it
 * covers the sun a "diamond ring" flashes on the rim, and then the black sun hangs there ringed by its corona - a
 * glowing halo, a bright inner ring and long filaments streaming out of it, slowly turning. At the end the moon slides
 * off the other side, the diamond ring flashes again and the sun blazes back. Everything faces the viewer.
 */
public class EclipseSunVfx extends Vfx {
    private static final int FILAMENTS = 18;
    private final Vec3 center;
    private final float radius;
    private final int sun;
    private final int corona;
    private final int shadow;
    private final int contact;
    private final float[] filLen = new float[FILAMENTS], filPhase = new float[FILAMENTS];

    /**
     * @param contact ticks the moon takes to cover the sun (and, at the end, to uncover it)
     */
    public EclipseSunVfx(Vec3 center, float radius, int sun, int corona, int shadow, int contact, int lifetime) {
        super(lifetime);
        this.center = center;
        this.radius = radius;
        this.sun = sun;
        this.corona = corona;
        this.shadow = shadow;
        this.contact = Math.max(4, contact);
        for (int i = 0; i < FILAMENTS; i++) {
            filLen[i] = 0.9f + (float) ((Math.sin(i * 12.9898) * 43758.5453) % 1 + 1) % 1 * 1.6f;
            filPhase[i] = i * 2.399f;
        }
    }

    public Vec3 center() {
        return center;
    }

    public float radius() {
        return radius;
    }

    /** 0 → 1 as the moon covers the sun, 1 during totality, 1 → 0 as it uncovers it at the end. */
    public float totality(float partial) {
        float time = age + partial;
        float in = clamp01(time / contact);
        float out = clamp01((lifetime - 10 - time) / contact);
        return Math.min(in, out);
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + partial(ctx);
        float appear = easeOut(Math.min(1, time / 8f));
        float fade = clamp01((lifetime - time) / 10f);
        float a = appear * fade;
        if (a <= 0.01f) return;
        float tot = totality(ctx.partial);
        boolean leaving = time > lifetime / 2f;
        // the moon's offset across the sun's face: it comes in from one side and leaves by the other
        float off = (1 - easeInOut(tot)) * radius * 2.05f * (leaving ? 1 : -1);
        Vec3 right = ctx.camLeft.scale(-1), up = ctx.camUp;
        Vec3 moon = center.add(right.scale(off * 0.85f)).add(up.scale(off * 0.35f));
        float spin = time * 0.004f;

        // the sun's light: blinding before totality, the corona during it
        float bright = 1 - easeInOut(tot);
        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.billboard(glow, center, radius * (5.5f + 2f * tot), 0, Colors.alpha(a * (0.18f + 0.12f * bright), corona));
        ctx.billboard(glow, center, radius * 3.2f, 0, Colors.alpha(a * (0.35f * tot + 0.5f * bright), tot > 0.5f ? corona : sun));
        ctx.billboard(ctx.additive(VfxTextures.FLASH), center, radius * 2.3f, spin, Colors.alpha(a * bright, sun));

        // corona: soft streamers of light pouring out of the rim, wide at the root and fading to nothing, slowly
        // turning; a bright, thin ring hugging the moon's edge and a pearly halo around it
        if (tot > 0.05f) {
            var soft = ctx.additive(VfxTextures.STREAK);
            for (int i = 0; i < FILAMENTS; i++) {
                double ang = filPhase[i] + spin * (i % 2 == 0 ? 1 : -0.6);
                float len = radius * filLen[i] * (0.85f + 0.15f * (float) Math.sin(time * 0.04 + i));
                Vec3 dir = right.scale(Math.cos(ang)).add(up.scale(Math.sin(ang)));
                Vec3 sideV = right.scale(-Math.sin(ang)).add(up.scale(Math.cos(ang)));
                int n = 6;
                Vec3[] pts = new Vec3[n];
                float[] w = new float[n];
                int[] col = new int[n];
                for (int k = 0; k < n; k++) {
                    float s = k / (float) (n - 1);
                    // streamers bend a little, like the sun's magnetic loops
                    double bendAmt = Math.sin(i * 1.7) * 0.25 * s * s * len;
                    pts[k] = center.add(dir.scale(radius * 0.9 + len * s)).add(sideV.scale(bendAmt));
                    w[k] = radius * (0.55f * (1 - s) + 0.08f);
                    col[k] = Colors.alpha(a * tot * 0.32f * (1 - s) * (1 - s), i % 3 == 0 ? sun : corona);
                }
                ctx.ribbon(soft, pts, w, col, 0, 1f / (n - 1));
            }
            var glow2 = ctx.additive(VfxTextures.GLOW);
            ctx.billboard(glow2, center, radius * 3.4f, 0, Colors.alpha(a * tot * 0.55f, 0xFFF6E0));
            int segs = ctx.segments(48);
            var ring = ctx.additive(VfxTextures.RING);
            ctx.ring(ring, center, right, up, radius * 0.99f, radius * 1.12f, segs, Colors.alpha(a * tot, 0xFFFFFF));
        }

        // the moon itself: a black disc, always in front of the sun
        ctx.billboard(ctx.translucent(VfxTextures.DISC), moon.add(ctx.camLook.scale(-0.05)), radius * 2.02f, 0, Colors.alpha(a * Math.min(1, 0.3f + tot * 1.2f), shadow));

        // the diamond ring: the last (and first) bead of sunlight on the rim as the moon closes over it
        float ring = Math.max(spike(tot, 0.97f), leaving ? spike(tot, 0.97f) : 0);
        if (ring > 0.01f) {
            Vec3 bead = center.add(right.scale((leaving ? 1 : -1) * radius * 0.92f)).add(up.scale((leaving ? 1 : -1) * radius * 0.38f));
            var flash = ctx.additive(VfxTextures.FLASH);
            ctx.billboard(flash, bead, radius * 3.5f * ring, spin * 4, Colors.alpha(a * ring, 0xFFFFFF));
            ctx.billboard(flash, bead, radius * 1.4f * ring, -spin * 6, Colors.alpha(a * ring, sun));
        }
    }

    private static float partial(VfxContext ctx) {
        return ctx.partial;
    }

    /** A sharp bump around {@code at} as {@code t} passes through it. */
    private static float spike(float t, float at) {
        float d = Math.abs(t - at) / 0.06f;
        return d >= 1 ? 0 : 1 - d * d;
    }
}
