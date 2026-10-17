package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom branching lightning bolt: a jagged main channel subdivided by midpoint displacement, forked side branches,
 * a white-hot core and a coloured glow. The shape re-strikes (re-randomises) every couple of ticks so it crackles.
 */
public class LightningVfx extends Vfx {
    private final Vec3 from;
    private final Vec3 to;
    private final float width;
    private final int color;
    private final long seed;
    private int branches = 3;
    private float jag = 0.22f;
    private int restrike = 2;
    private final List<Vec3[]> bolts = new ArrayList<>();
    private final List<Float> widths = new ArrayList<>();

    public LightningVfx(Vec3 from, Vec3 to, float width, int color, int lifetime, long seed) {
        super(lifetime);
        this.from = from;
        this.to = to;
        this.width = width;
        this.color = color;
        this.seed = seed;
        build(0);
    }

    public LightningVfx branches(int n) {
        this.branches = n;
        build(0);
        return this;
    }

    public LightningVfx jag(float j) {
        this.jag = j;
        build(0);
        return this;
    }

    public LightningVfx restrike(int ticks) {
        this.restrike = Math.max(1, ticks);
        return this;
    }

    private void build(int strike) {
        bolts.clear();
        widths.clear();
        RandomSource r = RandomSource.create(seed + strike * 7919L);
        Vec3[] main = channel(from, to, jag, 5, r);
        bolts.add(main);
        widths.add(1f);
        for (int b = 0; b < branches; b++) {
            int idx = 2 + r.nextInt(Math.max(1, main.length - 4));
            Vec3 start = main[idx];
            Vec3 dir = to.subtract(from);
            double len = dir.length() * (0.2 + r.nextDouble() * 0.3);
            Vec3 rnd = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
            Vec3 end = start.add(dir.normalize().scale(len * 0.6).add(rnd.scale(len * 0.7)));
            bolts.add(channel(start, end, jag * 1.2f, 3, r));
            widths.add(0.45f);
        }
    }

    private static Vec3[] channel(Vec3 a, Vec3 b, float jag, int depth, RandomSource r) {
        List<Vec3> pts = new ArrayList<>();
        pts.add(a);
        pts.add(b);
        double offset = a.distanceTo(b) * jag;
        for (int d = 0; d < depth; d++) {
            List<Vec3> next = new ArrayList<>();
            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3 p = pts.get(i), q = pts.get(i + 1);
                Vec3 mid = p.add(q).scale(0.5);
                Vec3 seg = q.subtract(p);
                Vec3[] basis = VfxContext.basis(seg.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : seg);
                mid = mid.add(basis[0].scale((r.nextDouble() - 0.5) * 2 * offset)).add(basis[1].scale((r.nextDouble() - 0.5) * 2 * offset));
                next.add(p);
                next.add(mid);
            }
            next.add(pts.get(pts.size() - 1));
            pts = next;
            offset *= 0.5;
        }
        return pts.toArray(new Vec3[0]);
    }

    @Override
    public void tick() {
        super.tick();
        if (age % restrike == 0) build(age / restrike);
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        float flicker = 0.75f + 0.25f * (float) Math.sin((age + ctx.partial) * 3.7f);
        float fade = t < 0.1f ? t / 0.1f : 1 - easeIn((t - 0.1f) / 0.9f);
        float a = fade * flicker * ((color >>> 24) & 255) / 255f;
        if (a <= 0.01f) return;
        var glow = ctx.additive(VfxTextures.LIGHTNING);
        var core = ctx.additive(VfxTextures.LIGHTNING);
        for (int i = 0; i < bolts.size(); i++) {
            Vec3[] pts = bolts.get(i);
            float w = width * widths.get(i);
            for (int k = 0; k < pts.length - 1; k++) {
                ctx.beam(glow, pts[k], pts[k + 1], w * 3.2f, Colors.alpha(a * 0.45f, color), 0, 1);
                ctx.beam(core, pts[k], pts[k + 1], w, Colors.alpha(a, Colors.brighten(color, 0.75f)), 0, 1);
            }
        }
    }
}
