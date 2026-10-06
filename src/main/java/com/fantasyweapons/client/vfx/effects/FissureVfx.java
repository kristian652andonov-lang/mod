package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A crack tearing open along the ground, the way real fractures look: a thin, dark rift whose course is jagged at
 * every scale (fractal midpoint displacement, never a regular zigzag), pinching and widening along its length, with a
 * faint ember glow deep inside, a little heat haze, and hairline side cracks forking off it. It races from its start
 * to its end, stays open, and then the glow dies and the gash closes. Purely visual; it never touches blocks.
 */
public class FissureVfx extends Vfx {
    private static final class Crack {
        final Vec3[] pts;
        final float[] jl, jr;
        final float width;
        final boolean hairline;
        final float start; // 0..1 of the growth phase at which this crack starts
        final float span;  // share of the growth phase it takes to grow

        Crack(Vec3[] pts, float[] jl, float[] jr, float width, float start, float span, boolean hairline) {
            this.pts = pts;
            this.jl = jl;
            this.jr = jr;
            this.width = width;
            this.hairline = hairline;
            this.start = start;
            this.span = span;
        }
    }

    private final List<Crack> cracks = new ArrayList<>();
    private final int glow;
    private final int core;
    private final int growTicks;
    private float curtain = 0.9f;
    private boolean glowing = true;
    private int dark = 0x120C08;

    /**
     * @param path   the main line of the crack (dropped to the ground by {@code ground})
     * @param ground maps a point to the ground surface below/above it (client-side terrain lookup)
     */
    public FissureVfx(List<Vec3> path, float width, int glow, int core, int growTicks, int lifetime, long seed, UnaryOperator<Vec3> ground) {
        super(lifetime);
        this.glow = glow;
        this.core = core;
        this.growTicks = Math.max(1, growTicks);
        RandomSource r = RandomSource.create(seed);
        // real cracks are thin: the requested width is the size of the disturbance, the gash itself is a fraction of it
        float w = width * WIDTH_SCALE;
        Crack main = build(path, w, 0, 1, r, ground, false);
        cracks.add(main);
        // side cracks forking off at uneven intervals, some of them forking again into hairlines
        float len = length(main.pts);
        for (float at = 0.6f + r.nextFloat() * 1.2f; at < len - 0.5f; at += 0.8f + r.nextFloat() * 2.2f) {
            int i = Math.max(1, Math.min(main.pts.length - 2, Math.round(at / len * (main.pts.length - 1))));
            Vec3 along = main.pts[i + 1].subtract(main.pts[i - 1]);
            along = new Vec3(along.x, 0, along.z);
            if (along.lengthSqr() < 1e-6) continue;
            along = along.normalize();
            Vec3 side = new Vec3(-along.z, 0, along.x).scale(r.nextBoolean() ? 1 : -1);
            double ang = 0.35 + r.nextDouble() * 0.8;
            Vec3 dir = along.scale(Math.cos(ang)).add(side.scale(Math.sin(ang))).normalize();
            double blen = (0.6 + r.nextDouble() * 1.6) * Math.max(0.7, width);
            Vec3 from = main.pts[i];
            Vec3 to = from.add(dir.scale(blen));
            float start = (float) i / main.pts.length;
            Crack br = build(List.of(from, to), w * (0.35f + 0.25f * r.nextFloat()), start, 0.2f + 0.15f * r.nextFloat(), r, ground, true);
            cracks.add(br);
            if (r.nextFloat() < 0.45f && br.pts.length > 3) {
                Vec3 f2 = br.pts[br.pts.length / 2];
                Vec3 d2 = dir.add(side.scale(r.nextBoolean() ? 0.9 : -0.9)).normalize();
                cracks.add(build(List.of(f2, f2.add(d2.scale(blen * 0.45))), w * 0.22f, start + 0.1f, 0.2f, r, ground, true));
            }
        }
    }

    /** The gash is this fraction of the requested width. */
    private static final float WIDTH_SCALE = 0.3f;

    public FissureVfx curtain(float height) {
        this.curtain = height;
        return this;
    }

    /** A plain fracture: dark gash and margin only, no ember glow, heat haze or spark. */
    public FissureVfx glowless() {
        this.glowing = false;
        this.curtain = 0;
        return this;
    }

    public FissureVfx darkColor(int rgb) {
        this.dark = rgb;
        return this;
    }

    private static float length(Vec3[] p) {
        float l = 0;
        for (int i = 1; i < p.length; i++) l += (float) p[i].distanceTo(p[i - 1]);
        return l;
    }

    /**
     * Fractal course: each segment is split at a midpoint pushed sideways by a share of its length, recursively, so
     * the crack wanders at large scale and is jagged at small scale like a real fracture.
     */
    private static Crack build(List<Vec3> path, float width, float start, float span, RandomSource r, UnaryOperator<Vec3> ground, boolean hairline) {
        List<Vec3> out = new ArrayList<>();
        out.add(path.get(0));
        for (int s = 0; s < path.size() - 1; s++) subdivide(out, path.get(s), path.get(s + 1), r, 0);
        Vec3[] pts = new Vec3[out.size()];
        float[] jl = new float[pts.length], jr = new float[pts.length];
        // width that pinches and swells smoothly along the crack, with a little roughness on each lip
        double ph1 = r.nextDouble() * 6.28, ph2 = r.nextDouble() * 6.28;
        for (int i = 0; i < pts.length; i++) {
            pts[i] = ground.apply(out.get(i)).add(0, 0.03, 0);
            double sw = 0.65 + 0.35 * Math.sin(i * 0.23 + ph1) + 0.2 * Math.sin(i * 0.71 + ph2);
            jl[i] = (float) Math.max(0.2, sw * (0.75 + r.nextFloat() * 0.5));
            jr[i] = (float) Math.max(0.2, sw * (0.75 + r.nextFloat() * 0.5));
        }
        return new Crack(pts, jl, jr, width, start, span, hairline);
    }

    private static void subdivide(List<Vec3> out, Vec3 a, Vec3 b, RandomSource r, int depth) {
        Vec3 d = b.subtract(a);
        double len = Math.sqrt(d.x * d.x + d.z * d.z);
        if (len < 0.16 || depth > 9) {
            out.add(b);
            return;
        }
        Vec3 side = new Vec3(-d.z, 0, d.x).scale(1 / Math.max(1e-6, len));
        double push = (r.nextDouble() - 0.5) * len * (depth < 2 ? 0.32 : 0.5);
        Vec3 mid = a.add(d.scale(0.4 + r.nextDouble() * 0.2)).add(side.scale(push));
        subdivide(out, a, mid, r, depth + 1);
        subdivide(out, mid, b, r, depth + 1);
    }

    @Override
    public void render(VfxContext ctx) {
        float age = this.age + ctx.partial;
        float grow = Math.min(1, age / growTicks);
        float t = progress(ctx.partial);
        // the glow dies first, then the gash closes
        float glowA = t < 0.6f ? 1 : clamp01(1 - (t - 0.6f) / 0.3f);
        float darkA = t < 0.75f ? 1 : clamp01(1 - (t - 0.75f) / 0.25f);
        float pulse = 0.8f + 0.2f * (float) Math.sin(age * 0.5f);

        // a soft dark margin where the lips have sunk, the black gash, then a faint ember glow deep inside it
        var dk = ctx.translucent(VfxTextures.WHITE);
        for (Crack c : cracks) strip(ctx, dk, c, grow, 2.4f, Colors.alpha(0.22f * darkA, dark), Colors.alpha(0.22f * darkA, dark), 0.015);
        for (Crack c : cracks) strip(ctx, dk, c, grow, 1.0f, Colors.alpha(0.95f * darkA, dark), Colors.alpha(0.95f * darkA, dark), 0.02);
        if (!glowing) return;
        var add = ctx.additive(VfxTextures.GLOW);
        for (Crack c : cracks) {
            if (c.hairline) continue;
            strip(ctx, add, c, grow, 0.75f, Colors.alpha(0.55f * glowA * pulse, glow), Colors.alpha(0.55f * glowA * pulse, glow), 0.03);
        }
        var hot = ctx.additive(VfxTextures.WHITE);
        for (Crack c : cracks) {
            if (!c.hairline) strip(ctx, hot, c, grow, 0.16f, Colors.alpha(0.6f * glowA * pulse, core), Colors.alpha(0.6f * glowA * pulse, core), 0.035);
        }
        // heat haze rising out of the main crack
        if (curtain > 0) {
            var cur = ctx.additive(VfxTextures.STREAK);
            for (Crack c : cracks) if (!c.hairline) curtain(ctx, cur, c, grow, Colors.alpha(0.22f * glowA * pulse, glow), age);
        }
        // a spark at the racing head
        if (grow < 1) {
            Crack m = cracks.get(0);
            Vec3 head = at(m, grow);
            var sp = ctx.additive(VfxTextures.FLASH);
            ctx.billboard(sp, head.add(0, 0.15, 0), 0.9f, age * 0.3f, Colors.alpha(0.75f, core));
        }
    }

    /** Visible fraction of a crack for the global growth value. */
    private static float visible(Crack c, float grow) {
        return clamp01((grow - c.start) / Math.max(0.01f, c.span));
    }

    private static Vec3 at(Crack c, float grow) {
        float f = visible(c, grow) * (c.pts.length - 1);
        int i = Math.min(c.pts.length - 2, (int) f);
        return c.pts[i].lerp(c.pts[i + 1], f - i);
    }

    private static void strip(VfxContext ctx, VertexConsumer vc, Crack c, float grow, float widthScale, int colorA,
                              int colorB, double lift) {
        float vis = visible(c, grow);
        if (vis <= 0) return;
        float f = vis * (c.pts.length - 1);
        int n = c.pts.length;
        Vec3 prevL = null, prevR = null;
        for (int i = 0; i < n; i++) {
            if (i > f + 1) break;
            Vec3 p = i > f ? c.pts[i - 1].lerp(c.pts[i], f - (i - 1)) : c.pts[i];
            Vec3 d = c.pts[Math.min(n - 1, i + 1)].subtract(c.pts[Math.max(0, i - 1)]);
            Vec3 side = new Vec3(-d.z, 0, d.x);
            side = side.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : side.normalize();
            // tapered: widest a little after the start, pointed at the end and at the growing head
            float s = (float) i / (n - 1);
            float taper = (float) Math.min(1, Math.sin(Math.PI * Math.min(1, s * 1.15 + 0.08)) * 1.4);
            float headTaper = clamp01((f - i + 1) / 2.5f);
            float w = c.width * widthScale * taper * Math.max(0.15f, headTaper) * 0.5f;
            Vec3 pp = p.add(0, lift, 0);
            Vec3 l = pp.add(side.scale(w * c.jl[i])), r = pp.subtract(side.scale(w * c.jr[i]));
            if (prevL != null) ctx.quad(vc, prevL, prevR, r, l, 0, 0, 1, 1, colorA, colorA, colorB, colorB);
            prevL = l;
            prevR = r;
        }
    }

    private void curtain(VfxContext ctx, VertexConsumer vc, Crack c, float grow, int color, float age) {
        float vis = visible(c, grow);
        if (vis <= 0) return;
        float f = vis * (c.pts.length - 1);
        int clear = color & 0xFFFFFF;
        for (int i = 0; i < c.pts.length - 1 && i < f; i++) {
            Vec3 a = c.pts[i], b = c.pts[i + 1];
            float h0 = curtain * c.width * 2.2f * (0.6f + 0.4f * (float) Math.sin(age * 0.4 + i * 1.3));
            float h1 = curtain * c.width * 2.2f * (0.6f + 0.4f * (float) Math.sin(age * 0.4 + (i + 1) * 1.3));
            float u0 = i * 0.06f - age * 0.03f, u1 = (i + 1) * 0.06f - age * 0.03f;
            ctx.quad(vc, a, b, b.add(0, h1, 0), a.add(0, h0, 0), u0, 0, u1, 1, color, color, clear, clear);
        }
    }
}
