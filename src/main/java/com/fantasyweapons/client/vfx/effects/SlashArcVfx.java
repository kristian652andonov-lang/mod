package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

/**
 * Custom slash mesh: a crescent that sweeps from angle a0 to a1 in its plane, leaving a fading trail, with a bright
 * cutting edge. The crescent is thicker in the middle and tapers at both ends.
 */
public class SlashArcVfx extends Vfx {
    private final Vec3 center;
    private final Vec3 ax;
    private final Vec3 ay;
    private final float radius;
    private final float thickness;
    private final float a0;
    private final float a1;
    private final int color;
    private final int edgeColor;
    /** Fraction of the lifetime spent sweeping (rest is fade-out). */
    private float sweepTime = 0.45f;
    /** Length of the visible trail behind the head, as a fraction of the arc. */
    private float trail = 0.75f;

    /**
     * @param ax first plane axis (angle 0), @param ay second plane axis (angle +90°)
     */
    public SlashArcVfx(Vec3 center, Vec3 ax, Vec3 ay, float radius, float thickness, float a0, float a1, int color, int edgeColor, int lifetime) {
        super(lifetime);
        this.center = center;
        this.ax = ax.normalize();
        this.ay = ay.normalize();
        this.radius = radius;
        this.thickness = thickness;
        this.a0 = a0;
        this.a1 = a1;
        this.color = color;
        this.edgeColor = edgeColor;
    }

    public SlashArcVfx sweep(float fraction, float trailLength) {
        this.sweepTime = fraction;
        this.trail = trailLength;
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        float head = easeOut(Math.min(1f, t / sweepTime));
        float fade = t < sweepTime ? 1f : 1f - easeIn((t - sweepTime) / (1 - sweepTime));
        float tail = Math.max(0, head - trail);
        if (head - tail < 0.01f) return;
        float start = a0 + (a1 - a0) * tail;
        float end = a0 + (a1 - a0) * head;
        int segs = ctx.segments(28);
        float baseA = ((color >>> 24) & 255) / 255f * fade;
        float edgeA = ((edgeColor >>> 24) & 255) / 255f * fade;
        // body: thickness tapers towards both ends of the visible arc
        for (int pass = 0; pass < 2; pass++) {
            boolean edge = pass == 1;
            var vc = edge ? ctx.additive(VfxTextures.SLASH) : ctx.energy(VfxTextures.SLASH);
            float th = edge ? thickness * 0.35f : thickness;
            float rOffset = edge ? thickness * 0.45f : 0;
            for (int i = 0; i < segs; i++) {
                float s0 = i / (float) segs, s1 = (i + 1) / (float) segs;
                float w0 = taper(s0) * th, w1 = taper(s1) * th;
                double b0 = start + (end - start) * s0, b1 = start + (end - start) * s1;
                Vec3 d0 = ax.scale(Math.cos(b0)).add(ay.scale(Math.sin(b0)));
                Vec3 d1 = ax.scale(Math.cos(b1)).add(ay.scale(Math.sin(b1)));
                float r = radius + rOffset;
                int c0 = Colors.alpha((edge ? edgeA : baseA) * (0.25f + 0.75f * s0), edge ? edgeColor : color);
                int c1 = Colors.alpha((edge ? edgeA : baseA) * (0.25f + 0.75f * s1), edge ? edgeColor : color);
                ctx.vertex(vc, center.add(d0.scale(r - w0)), s0, 0, c0);
                ctx.vertex(vc, center.add(d1.scale(r - w1)), s1, 0, c1);
                ctx.vertex(vc, center.add(d1.scale(r + w1 * 0.3f)), s1, 1, c1);
                ctx.vertex(vc, center.add(d0.scale(r + w0 * 0.3f)), s0, 1, c0);
            }
        }
    }

    private static float taper(float s) {
        return (float) Math.sin(Math.PI * Math.min(1, s * 1.0)) * 0.85f + 0.15f * s;
    }
}
