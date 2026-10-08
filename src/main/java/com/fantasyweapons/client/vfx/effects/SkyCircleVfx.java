package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A great mana circle opening in the sky over an ultimate, the source its rays and bolts pour out of: three nested
 * counter-rotating rune circles ringed by orbiting smaller ones, rim rings, a soft halo, a faint curtain of light
 * hanging from its rim and motes of light drawn up into it from the ground. It unfolds from the centre, and wherever
 * a ray leaves it ({@link #emit}) the circle flares at that spot.
 */
public class SkyCircleVfx extends Vfx {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int MOTES = 28;
    private final Vec3 center;
    private final float radius;
    private final float drop;
    private final int primary;
    private final int secondary;
    private final int core;
    private final List<float[]> flares = new ArrayList<>();

    /**
     * @param center the middle of the circle in the sky
     * @param drop   how far below it the ground lies (the motes rise from there)
     */
    public SkyCircleVfx(Vec3 center, float radius, float drop, int primary, int secondary, int core, int lifetime) {
        super(lifetime);
        this.center = center;
        this.radius = radius;
        this.drop = drop;
        this.primary = primary;
        this.secondary = secondary;
        this.core = core;
    }

    /** A ray leaves the circle at {@code at} (projected onto it): the spot flares for a moment. */
    public void emit(Vec3 at) {
        float dx = (float) (at.x - center.x), dz = (float) (at.z - center.z);
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d > radius * 0.95f) {
            dx *= radius * 0.95f / d;
            dz *= radius * 0.95f / d;
        }
        flares.add(new float[]{dx, dz, age});
    }

    /** Where a ray towards {@code target} should leave the circle: straight above it, kept inside the rim. */
    public Vec3 sourceAbove(Vec3 target) {
        double dx = target.x - center.x, dz = target.z - center.z;
        double d = Math.sqrt(dx * dx + dz * dz), max = radius * 0.9;
        if (d > max) {
            dx *= max / d;
            dz *= max / d;
        }
        return new Vec3(center.x + dx, center.y - 0.05, center.z + dz);
    }

    @Override
    public void tick() {
        super.tick();
        flares.removeIf(f -> age - f[2] > 12);
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        float open = easeOut(Math.min(1, time / 20f));
        float fade = clamp01((lifetime - time) / 16f);
        float a = Math.min(1, open * 1.4f) * fade;
        if (a <= 0.01f) return;
        Vec3[] b = VfxContext.basis(UP);
        Vec3 X = b[0], Z = b[1];
        // the rings unfold one after another from the middle out
        float rCore = radius * 0.3f * easeOut(clamp01(time / 10f));
        float rMid = radius * 0.62f * easeOut(clamp01((time - 4) / 12f));
        float rOuter = radius * open;

        // soft halo and the light it throws down
        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.disc(glow, center.add(0, 0.1, 0), X, Z, radius * 1.55f * open, 0, Colors.alpha(a * 0.2f, primary));
        ctx.disc(glow, center, X, Z, radius * 0.6f * open, 0, Colors.alpha(a * 0.16f, core));

        // the circles
        var runes = ctx.energy(VfxTextures.RUNE_CIRCLE);
        ctx.disc(runes, center, X, Z, rOuter, time * 0.012f, Colors.alpha(a, primary));
        if (rMid > 0.05f) ctx.disc(runes, center.add(0, -0.08, 0), X, Z, rMid, -time * 0.02f, Colors.alpha(a * 0.95f, secondary));
        if (rCore > 0.05f) ctx.disc(runes, center.add(0, -0.16, 0), X, Z, rCore, time * 0.035f, Colors.alpha(a, core));
        float sat = easeOut(clamp01((time - 8) / 12f));
        if (sat > 0) {
            ctx.satellites(runes, center.add(0, 0.05, 0), X, Z, rOuter * 1.13f, radius * 0.16f * sat, 6, -time * 0.008f, time * 0.05f,
                    Colors.alpha(a * sat, secondary));
            ctx.satellites(runes, center.add(0, -0.12, 0), X, Z, rMid * 0.82f, radius * 0.08f * sat, 4, time * 0.015f, -time * 0.07f,
                    Colors.alpha(a * sat * 0.9f, core));
        }

        // rim rings
        var ring = ctx.additive(VfxTextures.RING);
        int segs = ctx.segments(64);
        ctx.ring(ring, center, X, Z, rOuter * 1.0f, rOuter * 1.05f, segs, Colors.alpha(a * 0.9f, core));
        ctx.ring(ring, center, X, Z, rOuter * 1.22f, rOuter * 1.25f, segs, Colors.alpha(a * 0.5f * sat, primary));
        if (rMid > 0.05f) ctx.ring(ring, center.add(0, -0.08, 0), X, Z, rMid * 0.98f, rMid * 1.02f, segs, Colors.alpha(a * 0.7f, secondary));
        // ripples where rays leave the circle (their flashes come last, with the other flares)
        for (float[] f : flares) {
            float k = (time - f[2]) / 12f;
            if (k < 0 || k > 1) continue;
            ctx.ring(ring, center.add(f[0], -0.2, f[1]), X, Z, 0.4f + k * 2.4f, 0.7f + k * 2.8f, 24, Colors.alpha(a * (1 - k) * 0.8f, primary));
        }

        // a faint curtain of light hanging from the rim, fading out as it falls
        var curtain = ctx.additive(VfxTextures.WHITE);
        float h = Math.min(drop * 0.55f, 4.5f) * open;
        int cs = ctx.segments(48);
        for (int i = 0; i < cs; i++) {
            double a0 = i * Math.PI * 2 / cs, a1 = (i + 1) * Math.PI * 2 / cs;
            float shimmer = 0.6f + 0.4f * (float) Math.sin(time * 0.15 + i * 0.9);
            Vec3 p0 = center.add(X.scale(Math.cos(a0) * rOuter)).add(Z.scale(Math.sin(a0) * rOuter));
            Vec3 p1 = center.add(X.scale(Math.cos(a1) * rOuter)).add(Z.scale(Math.sin(a1) * rOuter));
            int top = Colors.alpha(a * 0.1f * shimmer, primary), bottom = Colors.alpha(0, primary);
            ctx.quad(curtain, p0, p1, p1.add(0, -h, 0), p0.add(0, -h, 0), 0, 0, 1, 1, top, top, bottom, bottom);
        }

        // motes of light drawn up from the ground into the circle
        var star = ctx.additive(VfxTextures.STAR);
        for (int i = 0; i < MOTES; i++) {
            float cyc = ((i * 0.618f) + time * 0.012f) % 1f;
            double ang = i * 2.39996 + cyc * 1.5;
            float rr = radius * (0.25f + 0.7f * ((i * 0.37f) % 1f)) * (1 - cyc * 0.3f);
            Vec3 p = center.add(X.scale(Math.cos(ang) * rr)).add(Z.scale(Math.sin(ang) * rr)).add(0, -drop * (1 - cyc), 0);
            float ma = a * Math.min(1, cyc * 4) * Math.min(1, (1 - cyc) * 6);
            ctx.billboard(star, p, 0.25f + 0.15f * (i % 3), time * 0.1f + i, Colors.alpha(ma * 0.8f, i % 2 == 0 ? core : secondary));
        }

        // the eye at the centre, and flares where rays leave the circle
        var flash = ctx.additive(VfxTextures.FLASH);
        ctx.billboard(flash, center.add(0, -0.3, 0), radius * 0.3f * open, time * 0.03f, Colors.alpha(a * 0.45f, core));
        for (float[] f : flares) {
            float k = (time - f[2]) / 12f;
            if (k < 0 || k > 1) continue;
            ctx.billboard(flash, center.add(f[0], -0.2, f[1]), 1.2f + k * 2.2f, k * 2, Colors.alpha(a * (1 - k), core));
        }
    }
}
