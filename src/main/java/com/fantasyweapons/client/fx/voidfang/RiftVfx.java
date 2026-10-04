package com.fantasyweapons.client.fx.voidfang;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Procedural dimensional tear: a jagged vertical lens of pure void (custom void shader) bordered by crackling energy
 * edges, with an outer glow. Opens with an elastic snap and closes by sealing from the ends inward.
 */
public class RiftVfx extends Vfx {
    private static final int SEGMENTS = 22;
    private final Vec3 center;
    private final Vec3 side;
    private final Vec3 up;
    private final float height;
    private final float width;
    private final int color;
    private final int edgeColor;
    private final float[] jagL = new float[SEGMENTS + 1];
    private final float[] jagR = new float[SEGMENTS + 1];
    private final float[] lean = new float[SEGMENTS + 1];
    private final float openTime;
    private final float closeTime;

    /**
     * @param facing direction the rift faces (its plane is perpendicular to this, kept vertical)
     */
    public RiftVfx(Vec3 center, Vec3 facing, float height, float width, int color, int edgeColor, int lifetime, long seed) {
        super(lifetime);
        this.center = center;
        Vec3 f = new Vec3(facing.x, 0, facing.z);
        if (f.lengthSqr() < 1e-6) f = new Vec3(0, 0, 1);
        f = f.normalize();
        this.side = new Vec3(-f.z, 0, f.x);
        this.up = new Vec3(0, 1, 0);
        this.height = height;
        this.width = width;
        this.color = color;
        this.edgeColor = edgeColor;
        RandomSource r = RandomSource.create(seed);
        float drift = 0;
        for (int i = 0; i <= SEGMENTS; i++) {
            jagL[i] = 0.7f + r.nextFloat() * 0.6f;
            jagR[i] = 0.7f + r.nextFloat() * 0.6f;
            drift += (r.nextFloat() - 0.5f) * 0.18f;
            lean[i] = drift;
        }
        this.openTime = Math.min(10f, lifetime * 0.2f) / lifetime;
        this.closeTime = Math.min(10f, lifetime * 0.25f) / lifetime;
    }

    private float openness(float t) {
        if (t < openTime) {
            float k = t / openTime;
            return (float) (1 + Math.sin(k * Math.PI * 1.5) * 0.15 * (1 - k)) * easeOut(k); // slight elastic overshoot
        }
        if (t > 1 - closeTime) return 1 - easeIn((t - (1 - closeTime)) / closeTime);
        return 1f;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        float open = openness(t);
        if (open <= 0.01f) return;
        float time = (age + ctx.partial);
        float flicker = 0.85f + 0.15f * (float) Math.sin(time * 1.7);

        Vec3[] left = new Vec3[SEGMENTS + 1];
        Vec3[] right = new Vec3[SEGMENTS + 1];
        Vec3[] mid = new Vec3[SEGMENTS + 1];
        float closing = t > 1 - closeTime ? (t - (1 - closeTime)) / closeTime : 0;
        for (int i = 0; i <= SEGMENTS; i++) {
            float s = i / (float) SEGMENTS;
            float y = (s - 0.5f) * height * (0.65f + 0.35f * Math.min(1, open * 1.3f));
            float profile = (float) Math.pow(Math.sin(Math.PI * s), 0.75);
            // sealing from the ends inward while closing
            float seal = closing > 0 ? clamp01((profile - closing) / Math.max(0.05f, 1 - closing)) : 1f;
            float w = width * 0.5f * profile * open * seal;
            float wob = (float) Math.sin(time * 0.35 + i * 0.9) * 0.06f * width;
            Vec3 c = center.add(up.scale(y)).add(side.scale(lean[i] * width + wob));
            mid[i] = c;
            left[i] = c.add(side.scale(-w * jagL[i]));
            right[i] = c.add(side.scale(w * jagR[i]));
        }

        // void interior
        var vi = ctx.voidInterior(VfxTextures.WHITE);
        int inner = Colors.alpha(0.95f, color);
        for (int i = 0; i < SEGMENTS; i++) {
            float v0 = i / (float) SEGMENTS, v1 = (i + 1) / (float) SEGMENTS;
            ctx.vertex(vi, left[i], 0.15f, v0, inner);
            ctx.vertex(vi, right[i], 0.85f, v0, inner);
            ctx.vertex(vi, right[i + 1], 0.85f, v1, inner);
            ctx.vertex(vi, left[i + 1], 0.15f, v1, inner);
        }

        // outer glow
        ctx.billboard(ctx.additive(VfxTextures.GLOW), center, Math.max(width, height) * 1.25f * open, 0, Colors.alpha(0.35f * flicker, color));

        // energy edges (both sides), plus a thin white-hot core line
        float[] widths = new float[SEGMENTS + 1];
        int[] cols = new int[SEGMENTS + 1];
        float[] coreW = new float[SEGMENTS + 1];
        int[] coreC = new int[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            float s = i / (float) SEGMENTS;
            float pr = (float) Math.sin(Math.PI * s);
            widths[i] = (0.12f + 0.22f * pr) * Math.max(0.4f, open) * Math.max(0.5f, width);
            cols[i] = Colors.alpha((0.55f + 0.45f * pr) * flicker * Math.min(1, open * 2), edgeColor);
            coreW[i] = widths[i] * 0.35f;
            coreC[i] = Colors.alpha(0.9f * pr * Math.min(1, open * 2), 0xFFFFFF);
        }
        float scroll = -time * 0.08f;
        var edge = ctx.energy(VfxTextures.RIFT_EDGE);
        ctx.ribbon(edge, left, widths, cols, scroll, 0.12f);
        ctx.ribbon(edge, right, widths, cols, -scroll, 0.12f);
        var core = ctx.additive(VfxTextures.LIGHTNING);
        ctx.ribbon(core, left, coreW, coreC, 0, 0.05f);
        ctx.ribbon(core, right, coreW, coreC, 0, 0.05f);
        if (open < 0.35f) {
            // a single bright seam while the rift is a thin crack
            ctx.ribbon(core, mid, coreW, coreC, 0, 0.05f);
        }
    }
}
