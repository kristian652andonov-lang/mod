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

/**
 * A modelled vine or root: a tapered, bark-textured stem (a real tube, lit from above) that grows along a curve,
 * unfolds leaves and small thorns along its length and can open a flower at its tip. At the end of its life it
 * withers: it thins and draws back towards its base.
 * <p>
 * Nothing about it is regular, so it reads as wood rather than plastic: the path kinks, the stem swells into knots and
 * its cross-section is lumpy, the bark grain spirals round it, its colour is blotched with moss and darker bark and
 * stained with soil where it leaves the ground, the light on it is soft, and it darkens with the light of the place
 * it grows (at night, in caves).
 */
public class VineVfx extends Vfx {
    private static final Vec3 LIGHT = new Vec3(0.3, 0.9, 0.3).normalize();
    private static final int SIDES = 8;
    private static final int MOSS = 0x56682E;
    private static final int SOIL = 0x3A2A1C;

    private final Vec3[] path;
    private final float width;
    private final int color;
    private final int leafColor;
    private final int growTicks;
    private int flowerColor = -1;
    private final List<float[]> leaves = new ArrayList<>(); // t along the vine, angle, size
    private final List<float[]> thorns = new ArrayList<>(); // t along the vine, angle
    /** Per ring: radius factor (knots, swellings), tint, moss; per ring and side: lumpiness. */
    private final float[] girth, tint, moss;
    private final float[][] lump;
    private final float light;
    private final boolean fromGround;

    public VineVfx(Vec3[] path, float width, int color, int leafColor, int growTicks, int lifetime, long seed) {
        super(lifetime);
        RandomSource r = RandomSource.create(seed);
        this.path = smooth(kink(path, width, r), 4);
        this.width = width;
        this.color = color;
        this.leafColor = leafColor;
        this.growTicks = Math.max(1, growTicks);
        int n = Math.max(2, path.length / 2);
        for (int i = 0; i < n; i++) leaves.add(new float[]{0.15f + 0.8f * (i + r.nextFloat() * 0.6f) / n, r.nextFloat() * 6.283f, 0.2f + r.nextFloat() * 0.3f,
                r.nextFloat()});
        for (int i = 0; i < n + 2; i++) thorns.add(new float[]{0.1f + 0.85f * r.nextFloat(), r.nextFloat() * 6.283f});
        int rings = this.path.length;
        girth = new float[rings];
        tint = new float[rings];
        moss = new float[rings];
        lump = new float[rings][SIDES];
        // a few knots where the stem swells, and slow swelling and thinning in between
        float[] knotAt = new float[1 + r.nextInt(3)];
        for (int k = 0; k < knotAt.length; k++) knotAt[k] = 0.15f + 0.7f * r.nextFloat();
        double ph = r.nextDouble() * 6.28, mossPh = r.nextDouble() * 6.28;
        for (int i = 0; i < rings; i++) {
            float f = i / (float) Math.max(1, rings - 1);
            float g = 1 + 0.12f * (float) Math.sin(f * 11 + ph) + 0.06f * (float) Math.sin(f * 27 + ph * 2);
            for (float k : knotAt) g += 0.38f * (float) Math.exp(-Math.pow((f - k) / 0.035, 2));
            girth[i] = g;
            tint[i] = 0.82f + 0.3f * r.nextFloat();
            moss[i] = (float) Math.max(0, Math.sin(f * 9 + mossPh)) * (0.4f + 0.6f * r.nextFloat());
            for (int sd = 0; sd < SIDES; sd++) lump[i][sd] = 0.86f + 0.28f * r.nextFloat();
        }
        // smooth the lumps along the stem so it is knobbly, not jagged
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 1; i < rings - 1; i++) {
                for (int sd = 0; sd < SIDES; sd++) lump[i][sd] = (lump[i - 1][sd] + lump[i][sd] * 2 + lump[i + 1][sd]) / 4f;
            }
        }
        com.fantasyweapons.client.vfx.GroundMaterial m = com.fantasyweapons.client.vfx.GroundMaterial.at(path[0]);
        this.light = m.light();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        this.fromGround = level != null && !level.getBlockState(net.minecraft.core.BlockPos.containing(path[0].x, path[0].y - 0.3, path[0].z)).isAir();
    }

    /** Knocks the control points about a little so the stem kinks and wanders instead of bending perfectly. */
    private static Vec3[] kink(Vec3[] p, float width, RandomSource r) {
        Vec3[] out = p.clone();
        for (int i = 1; i < out.length - 1; i++) {
            double k = width * (0.8 + r.nextDouble() * 0.9);
            out[i] = out[i].add(r.nextGaussian() * k, r.nextGaussian() * k * 0.6, r.nextGaussian() * k);
        }
        return out;
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

    /** Catmull-Rom resampling so the stem bends smoothly instead of in straight segments. */
    private static Vec3[] smooth(Vec3[] p, int sub) {
        if (p.length < 3) return p;
        Vec3[] out = new Vec3[(p.length - 1) * sub + 1];
        for (int i = 0; i < p.length - 1; i++) {
            Vec3 p0 = p[Math.max(0, i - 1)], p1 = p[i], p2 = p[i + 1], p3 = p[Math.min(p.length - 1, i + 2)];
            for (int s = 0; s < sub; s++) {
                double t = s / (double) sub, t2 = t * t, t3 = t2 * t;
                out[i * sub + s] = p0.scale(-0.5 * t3 + t2 - 0.5 * t).add(p1.scale(1.5 * t3 - 2.5 * t2 + 1))
                        .add(p2.scale(-1.5 * t3 + 2 * t2 + 0.5 * t)).add(p3.scale(0.5 * t3 - 0.5 * t2));
            }
        }
        out[out.length - 1] = p[p.length - 1];
        return out;
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        float wither = clamp01((time - (lifetime - 12)) / 12f);
        float grow = easeOut(Math.min(1, time / growTicks)) * (1 - 0.6f * easeIn(wither));
        float alpha = ((color >>> 24) & 255) / 255f * (1 - easeIn(wither) * 0.3f);
        if (grow <= 0.01f) return;
        int last = path.length - 1;
        float reach = last * grow;
        int count = Math.min(last, (int) Math.ceil(reach)) + 1;
        Vec3[] pts = new Vec3[count];
        for (int i = 0; i < count; i++) pts[i] = path[i];
        int i0 = Math.min(last - 1, (int) Math.floor(reach));
        pts[count - 1] = path[i0].lerp(path[i0 + 1], reach - i0);
        if (count < 2) return;

        // ---- stem: tapered tube with parallel-transported rings ----
        Vec3 tan0 = pts[1].subtract(pts[0]).normalize();
        Vec3[] b = VfxContext.basis(tan0);
        Vec3 nrm = b[0];
        Vec3[][] ring = new Vec3[count][SIDES];
        Vec3[][] nor = new Vec3[count][SIDES];
        float[] arc = new float[count];
        for (int i = 0; i < count; i++) {
            Vec3 t = pts[Math.min(count - 1, i + 1)].subtract(pts[Math.max(0, i - 1)]);
            t = t.lengthSqr() < 1e-10 ? tan0 : t.normalize();
            nrm = nrm.subtract(t.scale(nrm.dot(t)));
            nrm = nrm.lengthSqr() < 1e-8 ? VfxContext.basis(t)[0] : nrm.normalize();
            Vec3 bin = t.cross(nrm);
            float f = i / (float) last;
            float r = width * 0.5f * (1 - 0.82f * f) * (1 - 0.35f * wither) * girth[Math.min(i, girth.length - 1)];
            if (i == count - 1) r *= 0.35f;                       // pointed growing tip
            for (int s = 0; s < SIDES; s++) {
                double a = s * Math.PI * 2 / SIDES;
                Vec3 o = nrm.scale(Math.cos(a)).add(bin.scale(Math.sin(a)));
                nor[i][s] = o;
                ring[i][s] = pts[i].add(o.scale(r * lump[Math.min(i, lump.length - 1)][s]));
            }
            arc[i] = i == 0 ? 0 : arc[i - 1] + (float) pts[i].distanceTo(pts[i - 1]);
        }
        VertexConsumer bark = ctx.solid(VfxTextures.BARK);
        for (int i = 0; i < count - 1; i++) {
            float f0 = i / (float) last, f1 = (i + 1) / (float) last;
            for (int s = 0; s < SIDES; s++) {
                int s1 = (s + 1) % SIDES;
                // the grain winds slowly round the stem
                float tw0 = arc[i] * 0.35f, tw1 = arc[i + 1] * 0.35f;
                float v0 = s / (float) SIDES, v1 = (s + 1) / (float) SIDES;
                int ca = stemColor(i, f0, nor[i][s], lump[Math.min(i, lump.length - 1)][s], alpha);
                int cb = stemColor(i, f0, nor[i][s1], lump[Math.min(i, lump.length - 1)][s1], alpha);
                int cc = stemColor(i + 1, f1, nor[i + 1][s1], lump[Math.min(i + 1, lump.length - 1)][s1], alpha);
                int cd = stemColor(i + 1, f1, nor[i + 1][s], lump[Math.min(i + 1, lump.length - 1)][s], alpha);
                float u0 = arc[i] * 1.3f, u1 = arc[i + 1] * 1.3f;
                ctx.vertex(bark, ring[i][s], u0, v0 + tw0, ca);
                ctx.vertex(bark, ring[i][s1], u0, v1 + tw0, cb);
                ctx.vertex(bark, ring[i + 1][s1], u1, v1 + tw1, cc);
                ctx.vertex(bark, ring[i + 1][s], u1, v0 + tw1, cd);
            }
        }

        // ---- small thorns along the stem ----
        VertexConsumer wood = ctx.solid(VfxTextures.THORN);
        for (float[] th : thorns) {
            float at = th[0] * last;
            if (at > reach - 0.5f) continue;
            int i = Math.min(count - 2, (int) at);
            Vec3 t = pts[i + 1].subtract(pts[i]).normalize();
            Vec3[] tb = VfxContext.basis(t);
            Vec3 o = tb[0].scale(Math.cos(th[1])).add(tb[1].scale(Math.sin(th[1])));
            float r = width * 0.5f * (1 - 0.82f * th[0]);
            float len = (0.08f + 0.12f * (1 - th[0])) * Math.min(1, (reach - at) * 0.5f) * (1 - wither);
            if (len <= 0.01f) continue;
            Vec3 base = pts[i].lerp(pts[i + 1], at - i).add(o.scale(r * 0.8));
            Vec3 tip = base.add(o.scale(len)).add(t.scale(len * 0.6));
            Vec3 w1 = t.scale(r * 0.45), w2 = o.cross(t).normalize().scale(r * 0.45);
            int c = Colors.argb(Math.round(255 * alpha), Colors.lerpRgb(color, 0xD8C8A0, 0.35f));
            ctx.quad(wood, base.subtract(w1), base.add(w1), tip, tip, c);
            ctx.quad(wood, base.subtract(w2), base.add(w2), tip, tip, c);
        }

        // ---- leaves: folded along the midrib, drooping slightly, unfolding as the vine passes ----
        VertexConsumer leafVc = ctx.solid(VfxTextures.LEAF);
        for (float[] leaf : leaves) {
            float at = leaf[0] * last;
            if (at > reach) continue;
            int i = Math.min(count - 2, (int) at);
            Vec3 t = pts[i + 1].subtract(pts[i]).normalize();
            Vec3[] lb = VfxContext.basis(t);
            Vec3 out = lb[0].scale(Math.cos(leaf[1])).add(lb[1].scale(Math.sin(leaf[1])));
            float s = leaf[2] * Math.min(1, (reach - at) * 0.4f) * (1 - wither * 0.7f);
            if (s <= 0.02f) continue;
            float r = width * 0.5f * (1 - 0.82f * leaf[0]);
            Vec3 base = pts[i].lerp(pts[i + 1], at - i).add(out.scale(r));
            Vec3 axis = out.scale(0.75).add(t.scale(0.45)).add(0, -0.2, 0).normalize();
            Vec3 side = axis.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1e-6 ? lb[0] : side.normalize();
            Vec3 up = side.cross(axis).normalize();
            if (up.y < 0) up = up.scale(-1);
            Vec3 tip = base.add(axis.scale(s));
            Vec3 midA = base.add(axis.scale(s * 0.5));
            float fold = 0.35f;
            Vec3 l = midA.subtract(side.scale(s * 0.32)).add(up.scale(s * 0.32 * fold));
            Vec3 rr = midA.add(side.scale(s * 0.32)).add(up.scale(s * 0.32 * fold));
            float shade = (0.62f + 0.38f * (float) Math.max(0, up.dot(LIGHT))) * light;
            // no two leaves alike: some darker, some yellowing at the tip
            int body = Colors.scale(leafColor, 0.78f + 0.3f * leaf[3]);
            int tipCol = leaf[3] > 0.75f ? Colors.lerpRgb(body, 0xB8A64A, 0.45f) : body;
            int c0 = Colors.argb(Math.round(255 * alpha), Colors.scale(Colors.darken(body, 0.35f), shade));
            int c1 = Colors.argb(Math.round(255 * alpha), Colors.scale(tipCol, shade));
            // two halves of the leaf texture meeting at the midrib (u = 0.5)
            ctx.vertex(leafVc, base, 0.5f, 1f, c0);
            ctx.vertex(leafVc, l, 0f, 0.5f, c1);
            ctx.vertex(leafVc, tip, 0.5f, 0f, c1);
            ctx.vertex(leafVc, tip, 0.5f, 0f, c1);
            ctx.vertex(leafVc, base, 0.5f, 1f, c0);
            ctx.vertex(leafVc, tip, 0.5f, 0f, c1);
            ctx.vertex(leafVc, rr, 1f, 0.5f, c1);
            ctx.vertex(leafVc, rr, 1f, 0.5f, c1);
        }

        // ---- flower at the tip once fully grown ----
        if (flowerColor != -1 && grow >= 0.98f) {
            float open = easeOut(clamp01((time - growTicks) / 10f)) * (1 - easeIn(wither));
            if (open > 0.01f) {
                Vec3 tip = pts[count - 1];
                var petals = ctx.solid(VfxTextures.PETAL_VEIN);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + time * 0.01;
                    Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                    FlowerVfx.petal(ctx, petals, tip, out, 0.32f * (0.5f + 0.5f * open), 0.11f, 1.4f - open * 1.1f, 0.5f * open, 0.4f,
                            Colors.darken(flowerColor, 0.25f), Colors.lerpRgb(flowerColor, 0xFFFFFF, 0.45f), alpha);
                }
                ctx.billboard(ctx.additive(VfxTextures.GLOW), tip.add(0, 0.06, 0), 0.38f * open, 0, Colors.alpha(alpha * 0.85f, 0xFFF3A0));
            }
        }
    }

    private int stemColor(int ring, float f, Vec3 normal, float bump, float alpha) {
        int i = Math.min(ring, tint.length - 1);
        // soft light, darker in the hollows between the lumps
        float shade = (0.6f + 0.4f * (float) Math.max(0, normal.dot(LIGHT))) * (0.75f + 0.25f * (bump - 0.86f) / 0.28f);
        int rgb = Colors.lerpRgb(color, Colors.lerpRgb(color, leafColor, 0.4f), f * f);
        rgb = Colors.lerpRgb(rgb, MOSS, moss[i] * 0.45f);
        // stained with soil where it comes up out of the ground
        if (fromGround && f < 0.2f) rgb = Colors.lerpRgb(rgb, SOIL, (1 - f / 0.2f) * 0.6f);
        return Colors.argb(Math.round(255 * alpha), Colors.scale(rgb, shade * tint[i] * light));
    }
}
