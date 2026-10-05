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
    private static final int STREAKS = 26;
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
    private final float[] streakAngle = new float[STREAKS];
    private final float[] streakPhase = new float[STREAKS];
    private final long seed;
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
        for (int i = 0; i < STREAKS; i++) {
            streakAngle[i] = r.nextFloat() * 6.283f;
            streakPhase[i] = r.nextFloat();
        }
        this.seed = seed;
        this.openTime = Math.min(5f, lifetime * 0.15f) / lifetime;
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
            float y = (s - 0.5f) * height * (0.8f + 0.2f * Math.min(1, open * 1.5f));
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

        // a dark halo around the tear so it reads as a hole in the world, even in daylight
        ctx.billboard(ctx.translucent(VfxTextures.GLOW), center, Math.max(width * 2.2f, height * 1.15f) * open, 0,
                Colors.alpha(0.55f * Math.min(1, open * 1.5f), Colors.darken(color, 0.8f)));

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

        // crackling arcs leaping off the edges, re-rolled every few ticks
        int roll = (int) (time / 3);
        RandomSource ar = RandomSource.create(roll * 7919L + seed);
        for (int k = 0; k < 3; k++) {
            int i = 3 + ar.nextInt(SEGMENTS - 5);
            boolean l = ar.nextBoolean();
            Vec3 from = l ? left[i] : right[i];
            Vec3 out = side.scale(l ? -1 : 1).add(up.scale(ar.nextFloat() - 0.5f)).normalize();
            float len = (0.6f + ar.nextFloat()) * Math.max(1f, width * 0.8f) * open;
            Vec3[] pts = new Vec3[6];
            float[] aw = new float[6];
            int[] ac = new int[6];
            float fadeArc = 1 - ((time / 3) - roll);
            for (int j = 0; j < 6; j++) {
                float f = j / 5f;
                Vec3 jitter = j == 0 ? Vec3.ZERO : new Vec3(ar.nextGaussian(), ar.nextGaussian(), ar.nextGaussian()).scale(0.12 * len);
                pts[j] = from.add(out.scale(len * f)).add(jitter);
                aw[j] = 0.12f * (1 - f * 0.7f);
                ac[j] = Colors.alpha(0.9f * fadeArc * (1 - f * 0.6f) * Math.min(1, open * 2), edgeColor);
            }
            ctx.ribbon(core, pts, aw, ac, 0, 0.2f);
        }

        // matter spiralling into the tear
        var streak = ctx.additive(VfxTextures.SPARK);
        float reach = Math.max(height * 0.75f, width * 1.5f);
        for (int k = 0; k < STREAKS; k++) {
            float f = (streakPhase[k] + time * 0.035f) % 1f;          // 0 far away .. 1 swallowed
            float rr = (1 - f) * reach;
            double ang = streakAngle[k] + f * 2.2f;
            Vec3 p = center.add(side.scale(Math.cos(ang) * rr * 0.8)).add(up.scale(Math.sin(ang) * rr * 0.6));
            Vec3 dir = center.subtract(p);
            if (dir.lengthSqr() < 1e-4) continue;
            float a = Math.min(1, f * 4) * Math.min(1, (1 - f) * 6) * Math.min(1, open * 2);
            ctx.stretched(streak, p, dir, 0.3f + 0.7f * (1 - f), 0.12f, Colors.alpha(0.85f * a, k % 3 == 0 ? 0xFFFFFF : edgeColor));
        }
    }
}
