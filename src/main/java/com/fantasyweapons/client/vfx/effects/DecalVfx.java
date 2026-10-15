package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A flat textured decal (rune circle, cracks, scorch) lying in a plane, with grow-in, optional spin and fade-out. */
public class DecalVfx extends Vfx {
    private final Vec3 center;
    private final Vec3 ax;
    private final Vec3 ay;
    private final float radius;
    private final int color;
    private final ResourceLocation texture;
    private float spin;
    private float growTime = 0.15f;
    private float fadeTime = 0.3f;
    private boolean energy;
    private boolean translucent;
    /** Drawn as a soft-rimmed, slightly ragged disc instead of a square (for textures that fill their square). */
    private boolean round;
    /** Small rune circles orbiting the rim; -1 = automatic (rune circles get 2-4 by size, other decals none). */
    private int satellites = -1;
    /** Where it is drawn each frame instead of {@link #center} (hidden while it gives null). */
    private java.util.function.Function<Float, Vec3> track;

    public DecalVfx(Vec3 center, Vec3 normal, float radius, int color, ResourceLocation texture, int lifetime) {
        super(lifetime);
        this.center = center;
        Vec3[] b = VfxContext.basis(normal);
        this.ax = b[0];
        this.ay = b[1];
        this.radius = radius;
        this.color = color;
        this.texture = texture;
    }

    public DecalVfx spin(float radiansPerTick) {
        this.spin = radiansPerTick;
        return this;
    }

    public DecalVfx timing(float grow, float fade) {
        this.growTime = grow;
        this.fadeTime = fade;
        return this;
    }

    public DecalVfx energy() {
        this.energy = true;
        return this;
    }

    /** Number of small circles orbiting the rim (0 disables them). */
    public DecalVfx satellites(int n) {
        this.satellites = n;
        return this;
    }

    /** Moves with {@code where} (given the partial tick), hidden while it gives null. */
    public DecalVfx follow(java.util.function.Function<Float, Vec3> where) {
        this.track = where;
        return this;
    }

    /** A ragged disc fading out at its rim: for tiling textures (soil, turf, noise) that would show a square. */
    public DecalVfx round() {
        this.round = true;
        return this;
    }

    /** Alpha-blended (for dark decals like cracks). */
    public DecalVfx translucent() {
        this.translucent = true;
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        Vec3 center = track == null ? this.center : track.apply(ctx.partial);
        if (center == null) return;
        float t = progress(ctx.partial);
        float grow = growTime <= 0 ? 1 : easeOut(Math.min(1, t / growTime));
        float fade = t > 1 - fadeTime ? 1 - (t - (1 - fadeTime)) / fadeTime : 1;
        float alpha = clamp01(fade) * Math.min(1, grow * 1.5f) * ((color >>> 24) & 255) / 255f;
        var vc = translucent ? ctx.translucent(texture) : (energy ? ctx.energy(texture) : ctx.additive(texture));
        float r = radius * (0.6f + 0.4f * grow);
        float time = age + ctx.partial;
        if (round) roundDisc(ctx, vc, center, r, spin * time, Colors.alpha(alpha, color));
        else ctx.disc(vc, center, ax, ay, r, spin * time, Colors.alpha(alpha, color));
        // smaller circles orbiting the rim, counter-rotating
        int n = satellites >= 0 ? satellites : texture.equals(VfxTextures.RUNE_CIRCLE) ? (radius >= 8 ? 4 : radius >= 4 ? 3 : 2) : 0;
        if (n > 0) {
            float dir = spin < 0 ? 1 : -1;
            float phase = dir * time * (Math.abs(spin) * 0.6f + 0.012f);
            float size = Math.min(2.4f, r * (n >= 4 ? 0.2f : 0.26f));
            ctx.satellites(vc, center, ax, ay, r * 1.12f, size, n, phase, -spin * 2.2f * time, Colors.alpha(alpha * 0.9f, color));
            if (!translucent) {
                ctx.satellites(ctx.additive(VfxTextures.GLOW), center, ax, ay, r * 1.12f, size * 1.25f, n, phase, 0, Colors.alpha(alpha * 0.22f, color));
            }
        }
    }

    private void roundDisc(VfxContext ctx, com.mojang.blaze3d.vertex.VertexConsumer vc, Vec3 c, float r, float rot, int argb) {
        int seg = ctx.segments(40);
        int clear = argb & 0x00FFFFFF;
        float seed = (System.identityHashCode(this) & 1023) * 0.37f;
        for (int i = 0; i < seg; i++) {
            float a0 = i * 6.2831855f / seg, a1 = (i + 1) * 6.2831855f / seg;
            float e0 = edge(a0, seed), e1 = edge(a1, seed);
            // the solid middle (a fan of quads folded to triangles), then the soft rim out to the ragged edge
            Vec3 m = c;
            Vec3 p0 = at(c, r * 0.72f * e0, a0 + rot), p1 = at(c, r * 0.72f * e1, a1 + rot);
            Vec3 q0 = at(c, r * e0, a0 + rot), q1 = at(c, r * e1, a1 + rot);
            float u0 = 0.36f * e0, u1 = 0.36f * e1, w0 = 0.5f * e0, w1 = 0.5f * e1;
            ctx.vertex(vc, m, 0.5f, 0.5f, argb);
            ctx.vertex(vc, m, 0.5f, 0.5f, argb);
            ctx.vertex(vc, p1, 0.5f + (float) Math.cos(a1) * u1, 0.5f + (float) Math.sin(a1) * u1, argb);
            ctx.vertex(vc, p0, 0.5f + (float) Math.cos(a0) * u0, 0.5f + (float) Math.sin(a0) * u0, argb);
            ctx.vertex(vc, p0, 0.5f + (float) Math.cos(a0) * u0, 0.5f + (float) Math.sin(a0) * u0, argb);
            ctx.vertex(vc, p1, 0.5f + (float) Math.cos(a1) * u1, 0.5f + (float) Math.sin(a1) * u1, argb);
            ctx.vertex(vc, q1, 0.5f + (float) Math.cos(a1) * w1, 0.5f + (float) Math.sin(a1) * w1, clear);
            ctx.vertex(vc, q0, 0.5f + (float) Math.cos(a0) * w0, 0.5f + (float) Math.sin(a0) * w0, clear);
        }
    }

    private Vec3 at(Vec3 c, double rr, double a) {
        return c.add(ax.scale(Math.cos(a) * rr)).add(ay.scale(Math.sin(a) * rr));
    }

    /** The rim's raggedness at angle a: a few overlapping waves, 0.82..1. */
    private static float edge(float a, float seed) {
        double n = Math.sin(a * 3 + seed) * 0.5 + Math.sin(a * 7 + seed * 1.7) * 0.3 + Math.sin(a * 13 + seed * 2.3) * 0.2;
        return (float) (0.91 + 0.09 * n);
    }
}
