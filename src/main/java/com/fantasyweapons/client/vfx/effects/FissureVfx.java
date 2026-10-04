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
 * A crack tearing open along the ground: a jagged dark gash with molten ground energy glowing inside it, a heat
 * curtain rising out of it and side cracks branching off. It races from its start to its end, stays open, and then
 * the glow dies and the gash closes. Purely visual; it never touches blocks.
 */
public class FissureVfx extends Vfx {
    private static final class Crack {
        final Vec3[] pts;
        final float[] jl, jr;
        final float width;
        final float start; // 0..1 of the growth phase at which this crack starts
        final float span;  // share of the growth phase it takes to grow

        Crack(Vec3[] pts, float[] jl, float[] jr, float width, float start, float span) {
            this.pts = pts;
            this.jl = jl;
            this.jr = jr;
            this.width = width;
            this.start = start;
            this.span = span;
        }
    }

    private final List<Crack> cracks = new ArrayList<>();
    private final int glow;
    private final int core;
    private final int growTicks;
    private float curtain = 0.9f;
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
        Crack main = build(path, width, 0, 1, r, ground, 0.55f);
        cracks.add(main);
        // side cracks branching off the main one
        int branches = Math.max(1, (int) (length(main.pts) / 2.5f));
        for (int b = 0; b < branches; b++) {
            int i = 1 + r.nextInt(Math.max(1, main.pts.length - 2));
            Vec3 at = main.pts[i];
            Vec3 along = main.pts[Math.min(main.pts.length - 1, i + 1)].subtract(main.pts[i - 1]);
            along = new Vec3(along.x, 0, along.z);
            if (along.lengthSqr() < 1e-6) continue;
            along = along.normalize();
            Vec3 side = new Vec3(-along.z, 0, along.x).scale(r.nextBoolean() ? 1 : -1);
            Vec3 dir = along.scale(0.6).add(side.scale(0.8)).normalize();
            double len = 0.8 + r.nextDouble() * 1.8 * Math.max(0.6, width);
            List<Vec3> bp = List.of(at, at.add(dir.scale(len * 0.5)).add(side.scale(0.15)), at.add(dir.scale(len)));
            float start = (float) i / main.pts.length;
            cracks.add(build(bp, width * 0.45f, start, 0.25f, r, ground, 0.8f));
        }
    }

    public FissureVfx curtain(float height) {
        this.curtain = height;
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

    /** Subdivides a path into ~0.45 block steps with lateral jitter (a crack never runs straight). */
    private static Crack build(List<Vec3> path, float width, float start, float span, RandomSource r, UnaryOperator<Vec3> ground, float jitter) {
        List<Vec3> out = new ArrayList<>();
        for (int s = 0; s < path.size() - 1; s++) {
            Vec3 a = path.get(s), b = path.get(s + 1);
            Vec3 d = b.subtract(a);
            Vec3 flat = new Vec3(d.x, 0, d.z);
            Vec3 side = flat.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : new Vec3(-flat.z, 0, flat.x).normalize();
            int steps = Math.max(1, (int) Math.ceil(d.length() / 0.45));
            for (int i = 0; i < steps; i++) {
                double k = (double) i / steps;
                double off = (s == 0 && i == 0) ? 0 : (r.nextDouble() - 0.5) * jitter * Math.max(0.5, width);
                out.add(a.add(d.scale(k)).add(side.scale(off)));
            }
        }
        out.add(path.get(path.size() - 1));
        Vec3[] pts = new Vec3[out.size()];
        float[] jl = new float[pts.length], jr = new float[pts.length];
        for (int i = 0; i < pts.length; i++) {
            pts[i] = ground.apply(out.get(i)).add(0, 0.03, 0);
            jl[i] = 0.5f + r.nextFloat() * 0.8f;
            jr[i] = 0.5f + r.nextFloat() * 0.8f;
        }
        return new Crack(pts, jl, jr, width, start, span);
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

        var dk = ctx.translucent(VfxTextures.WHITE);
        for (Crack c : cracks) strip(ctx, dk, c, grow, 1.0f, Colors.alpha(0.88f * darkA, dark), Colors.alpha(0.88f * darkA, dark), 0.02);
        var add = ctx.additive(VfxTextures.GLOW);
        for (Crack c : cracks) {
            strip(ctx, add, c, grow, 0.55f, Colors.alpha(0.95f * glowA * pulse, glow), Colors.alpha(0.95f * glowA * pulse, glow), 0.04);
        }
        var hot = ctx.additive(VfxTextures.WHITE);
        for (Crack c : cracks) strip(ctx, hot, c, grow, 0.18f, Colors.alpha(0.9f * glowA, core), Colors.alpha(0.9f * glowA, core), 0.05);
        // heat curtain rising out of the crack
        if (curtain > 0) {
            var cur = ctx.additive(VfxTextures.STREAK);
            for (Crack c : cracks) curtain(ctx, cur, c, grow, Colors.alpha(0.45f * glowA * pulse, glow), age);
        }
        // spark at the racing head
        if (grow < 1) {
            Crack m = cracks.get(0);
            Vec3 head = at(m, grow);
            var sp = ctx.additive(VfxTextures.FLASH);
            ctx.billboard(sp, head.add(0, 0.25, 0), 1.4f * Math.max(0.6f, m.width), age * 0.3f, Colors.alpha(0.9f, core));
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
            float h0 = curtain * c.width * (0.6f + 0.4f * (float) Math.sin(age * 0.4 + i * 1.3));
            float h1 = curtain * c.width * (0.6f + 0.4f * (float) Math.sin(age * 0.4 + (i + 1) * 1.3));
            float u0 = i * 0.2f - age * 0.03f, u1 = (i + 1) * 0.2f - age * 0.03f;
            ctx.quad(vc, a, b, b.add(0, h1, 0), a.add(0, h0, 0), u0, 0, u1, 1, color, color, clear, clear);
        }
    }
}
