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
 * A custom animated vine/root: a tapered bark ribbon that grows along a curved path, sprouts leaves along its length
 * and optionally opens a flower at the tip; withers away at the end of its life.
 */
public class VineVfx extends Vfx {
    private final Vec3[] path;
    private final float width;
    private final int color;
    private final int leafColor;
    private final int growTicks;
    private int flowerColor = -1;
    private final List<float[]> leaves = new ArrayList<>(); // t along path, angle, size

    public VineVfx(Vec3[] path, float width, int color, int leafColor, int growTicks, int lifetime, long seed) {
        super(lifetime);
        this.path = path;
        this.width = width;
        this.color = color;
        this.leafColor = leafColor;
        this.growTicks = Math.max(1, growTicks);
        RandomSource r = RandomSource.create(seed);
        int n = Math.max(1, path.length / 3);
        for (int i = 0; i < n; i++) leaves.add(new float[]{0.2f + 0.75f * r.nextFloat(), r.nextFloat() * 6.283f, 0.25f + r.nextFloat() * 0.25f});
    }

    public VineVfx flower(int color) {
        this.flowerColor = color;
        return this;
    }

    /** Builds a curling path from a base point rising/creeping along a direction. */
    public static Vec3[] curl(Vec3 base, Vec3 dir, double length, double curl, int points, long seed) {
        RandomSource r = RandomSource.create(seed);
        Vec3[] basis = VfxContext.basis(dir);
        Vec3[] out = new Vec3[points];
        double phase = r.nextDouble() * 6.283;
        for (int i = 0; i < points; i++) {
            double t = i / (double) (points - 1);
            double a = phase + t * 4.5;
            Vec3 off = basis[0].scale(Math.cos(a) * curl * t).add(basis[1].scale(Math.sin(a) * curl * t));
            out[i] = base.add(dir.normalize().scale(length * t)).add(off);
        }
        return out;
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        float grow = easeOut(Math.min(1, time / growTicks));
        float wither = clamp01((time - (lifetime - 10)) / 10f);
        float alpha = (1 - wither) * ((color >>> 24) & 255) / 255f;
        if (alpha <= 0.01f) return;
        int count = Math.max(2, (int) Math.ceil(path.length * grow));
        Vec3[] pts = new Vec3[count];
        float[] widths = new float[count];
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) {
            pts[i] = path[Math.min(path.length - 1, i)];
            float t = i / (float) (path.length - 1);
            widths[i] = width * (1 - t * 0.85f) * (1 - wither * 0.5f);
            colors[i] = Colors.alpha(alpha, Colors.lerpRgb(color, Colors.brighten(color, 0.25f), t));
        }
        if (count < path.length) {
            // smooth growing tip
            float frac = path.length * grow - (count - 1);
            Vec3 a = path[count - 2], b = path[count - 1];
            pts[count - 1] = a.lerp(b, Math.max(0, Math.min(1, frac)));
        }
        ctx.ribbon(ctx.translucent(VfxTextures.BARK), pts, widths, colors, 0, 0.35f);

        var leafVc = ctx.translucent(VfxTextures.LEAF);
        for (float[] leaf : leaves) {
            if (leaf[0] > grow) continue;
            int idx = Math.min(path.length - 2, (int) (leaf[0] * (path.length - 1)));
            Vec3 p = path[idx];
            Vec3 along = path[idx + 1].subtract(p).normalize();
            Vec3[] basis = VfxContext.basis(along);
            Vec3 out = basis[0].scale(Math.cos(leaf[1])).add(basis[1].scale(Math.sin(leaf[1])));
            float s = leaf[2] * Math.min(1, (grow - leaf[0]) * 6) * (1 - wither);
            Vec3 c = p.add(out.scale(s * 0.6));
            ctx.plane(leafVc, c, out.scale(s * 0.6), along.scale(s * 0.35), Colors.alpha(alpha, leafColor));
        }
        if (flowerColor != -1 && grow >= 0.98f) {
            float open = easeOut(clamp01((time - growTicks) / 8f)) * (1 - wither);
            Vec3 tip = path[path.length - 1];
            Vec3 up = path[path.length - 1].subtract(path[path.length - 2]).normalize();
            Vec3[] basis = VfxContext.basis(up);
            var petal = ctx.translucent(VfxTextures.PETAL);
            for (int i = 0; i < 5; i++) {
                double a = i * Math.PI * 2 / 5 + time * 0.01;
                Vec3 out = basis[0].scale(Math.cos(a)).add(basis[1].scale(Math.sin(a)));
                Vec3 dir = out.scale(open).add(up.scale(1 - open * 0.8)).normalize();
                Vec3 side = up.cross(out).normalize();
                float len = 0.35f * (0.4f + 0.6f * open);
                ctx.plane(petal, tip.add(dir.scale(len * 0.5)), dir.scale(len * 0.5), side.scale(0.13f), Colors.alpha(alpha, flowerColor));
            }
            ctx.billboard(ctx.additive(VfxTextures.GLOW), tip, 0.35f * open, 0, Colors.alpha(alpha * 0.8f, 0xFFF3A0));
        }
    }
}
