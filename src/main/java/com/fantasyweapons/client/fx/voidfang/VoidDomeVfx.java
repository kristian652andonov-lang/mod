package com.fantasyweapons.client.fx.voidfang;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Void Dimension: a translucent dome of void around the battlefield, sealed by sorcery. Two counter-rotating rune
 * circles on the ground; a ring of great spell circles standing outside the dome like gates, each at the foot of a
 * rift seam; smaller circles set into the dome's surface; three rune-studded orbit rings wheeling round it at
 * different tilts; violet lightning crawling over the shell; and void fragments drifting up inside.
 */
public class VoidDomeVfx extends Vfx {
    private static final int SEAMS = 8;
    private static final int MOTES = 60;
    private static final int SURFACE = 9;
    private static final float[][] ORBITS = {{0.38f, 0f, 1f}, {-0.55f, 2.1f, -0.7f}, {0.72f, 4.2f, 0.5f}};
    private final Vec3 center;
    private final float radius;
    private final int color;
    private final int light;
    private final long seed;
    private final float[] seamAngle = new float[SEAMS];
    private final float[] moteA = new float[MOTES], moteR = new float[MOTES], moteY = new float[MOTES], moteS = new float[MOTES];

    public VoidDomeVfx(Vec3 center, float radius, int color, int light, int lifetime, long seed) {
        super(lifetime);
        this.center = center;
        this.radius = radius;
        this.color = color;
        this.light = light;
        this.seed = seed;
        RandomSource r = RandomSource.create(seed);
        for (int i = 0; i < SEAMS; i++) seamAngle[i] = (float) (i / (double) SEAMS * Math.PI * 2 + r.nextFloat() * 0.3);
        for (int i = 0; i < MOTES; i++) {
            moteA[i] = r.nextFloat() * 6.283f;
            moteR[i] = (float) Math.sqrt(r.nextFloat()) * radius * 0.95f;
            moteY[i] = r.nextFloat();
            moteS[i] = 0.5f + r.nextFloat();
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        float time = age + ctx.partial;
        float open = easeOut(Math.min(1, time / 12f));
        float close = t > 0.92f ? 1 - (t - 0.92f) / 0.08f : 1f;
        float k = open * close;
        if (k <= 0.01f) return;
        float r = radius * (0.25f + 0.75f * open);
        Vec3 ground = center.subtract(0, 0.95, 0);
        Vec3 ax = new Vec3(1, 0, 0), az = new Vec3(0, 0, 1);
        // the outer sorcery arrives a beat after the dome
        float late = easeOut(clamp01((time - 8) / 14f)) * close;

        // dome shell
        int lat = ctx.segments(16), lon = ctx.segments(28);
        ctx.sphere(ctx.voidInterior(VfxTextures.WHITE), center, r, lat, lon, Colors.alpha(0.42f * k, color), false);
        ctx.sphere(ctx.energy(VfxTextures.WHITE), center, r * 1.005f, lat, lon, Colors.alpha(0.75f * k, color), true);

        // ---- every rune circle, in one batch ----
        var runes = ctx.energy(VfxTextures.RUNE_CIRCLE);
        ctx.disc(runes, ground.add(0, 0.05, 0), ax, az, r * 0.98f, time * 0.02f, Colors.alpha(0.85f * k, color));
        ctx.disc(runes, ground.add(0, 0.07, 0), ax, az, r * 0.55f, -time * 0.035f, Colors.alpha(0.8f * k, light));
        ctx.satellites(runes, ground.add(0, 0.08, 0), ax, az, r * 0.77f, r * 0.13f, 4, -time * 0.012f, time * 0.06f, Colors.alpha(0.8f * k, light));
        if (late > 0.01f) {
            // great circles standing outside the dome like gates, one at the foot of each seam, facing outwards
            float gate = r * 0.2f * late;
            for (int i = 0; i < SEAMS; i++) {
                double a = seamAngle[i] + time * 0.004;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 side = new Vec3(-out.z, 0, out.x);
                Vec3 at = ground.add(out.scale(r * 1.1 + gate * 0.4)).add(0, gate * 1.15, 0);
                ctx.disc(runes, at, side, new Vec3(0, 1, 0), gate, (i % 2 == 0 ? 1 : -1) * time * 0.03f, Colors.alpha(0.9f * late, i % 2 == 0 ? light : color));
                ctx.disc(runes, at.add(out.scale(0.06)), side, new Vec3(0, 1, 0), gate * 0.55f, (i % 2 == 0 ? -1 : 1) * time * 0.05f, Colors.alpha(0.85f * late, light));
                ctx.satellites(runes, at, side, new Vec3(0, 1, 0), gate * 1.25f, gate * 0.2f, 3, time * 0.02f + i, -time * 0.08f, Colors.alpha(0.75f * late, light));
                // and a small circle on the ground beneath each gate
                ctx.disc(runes, ground.add(out.scale(r * 1.1 + gate * 0.4)).add(0, 0.06, 0), ax, az, gate * 0.9f, time * 0.04f, Colors.alpha(0.7f * late, color));
            }
            // circles set into the dome's surface, facing out
            for (int i = 0; i < SURFACE; i++) {
                double lon0 = i * Math.PI * 2 / SURFACE + time * 0.006;
                double lat0 = i % 3 == 0 ? 1.0 : 0.45;
                Vec3 n = new Vec3(Math.cos(lat0) * Math.cos(lon0), Math.sin(lat0), Math.cos(lat0) * Math.sin(lon0));
                Vec3[] b = VfxContext.basis(n);
                float sz = r * (lat0 > 0.9 ? 0.16f : 0.13f) * late;
                ctx.disc(runes, center.add(n.scale(r * 1.02)), b[0], b[1], sz, time * 0.05f * (i % 2 == 0 ? 1 : -1), Colors.alpha(0.85f * late, light));
            }
            // a crown circle over the top of the dome
            ctx.disc(runes, center.add(0, r * 1.04, 0), ax, az, r * 0.32f * late, -time * 0.025f, Colors.alpha(0.9f * late, color));
            ctx.satellites(runes, center.add(0, r * 1.05, 0), ax, az, r * 0.42f * late, r * 0.07f * late, 4, time * 0.02f, time * 0.07f,
                    Colors.alpha(0.8f * late, light));
            // runes riding the orbit rings
            for (float[] o : ORBITS) {
                Vec3[] ob = orbitBasis(o);
                float rr = r * 1.12f;
                for (int j = 0; j < 4; j++) {
                    double ang = time * 0.015 * o[2] + j * Math.PI / 2;
                    Vec3 p = center.add(ob[0].scale(Math.cos(ang) * rr)).add(ob[1].scale(Math.sin(ang) * rr));
                    Vec3[] pb = VfxContext.basis(p.subtract(center).normalize());
                    ctx.disc(runes, p, pb[0], pb[1], r * 0.07f * late, time * 0.1f, Colors.alpha(0.9f * late, light));
                }
            }
        }

        // ---- glows ----
        ctx.disc(ctx.additive(VfxTextures.GLOW), ground.add(0, 0.06, 0), ax, az, r * 1.1f, 0, Colors.alpha(0.25f * k, color));

        // ---- the orbit rings and the boundary ring on the ground ----
        var ring = ctx.additive(VfxTextures.RING);
        int segs = ctx.segments(64);
        ctx.ring(ring, ground.add(0, 0.09, 0), ax, az, r * 1.0f, r * 1.06f, segs, Colors.alpha(0.8f * k, light));
        if (late > 0.01f) {
            ctx.ring(ring, ground.add(0, 0.09, 0), ax, az, r * 1.26f, r * 1.29f, segs, Colors.alpha(0.6f * late, color));
            for (float[] o : ORBITS) {
                Vec3[] ob = orbitBasis(o);
                ctx.ring(ring, center, ob[0], ob[1], r * 1.1f, r * 1.14f, segs, Colors.alpha(0.55f * late, light));
            }
        }

        // ---- vertical rift seams on the boundary ----
        var seam = ctx.energy(VfxTextures.RIFT_EDGE);
        for (int i = 0; i < SEAMS; i++) {
            double a = seamAngle[i] + time * 0.004;
            Vec3 b = ground.add(Math.cos(a) * r * 0.97, 0, Math.sin(a) * r * 0.97);
            float h = r * (0.5f + 0.3f * (float) Math.sin(time * 0.1 + i));
            ctx.beam(seam, b, b.add(0, h, 0), 0.35f, Colors.alpha(0.7f * k, light), -time * 0.05f, -time * 0.05f + 1.5f);
        }

        // ---- lightning crawling over the shell, re-forked every few ticks ----
        if (late > 0.01f) {
            var bolt = ctx.additive(VfxTextures.LIGHTNING);
            int frame = age / 3;
            for (int arc = 0; arc < 4; arc++) {
                RandomSource rnd = RandomSource.create(seed * 31 + frame * 7919L + arc);
                if (rnd.nextFloat() < 0.35f) continue;
                double lon0 = rnd.nextDouble() * Math.PI * 2, lat0 = 0.1 + rnd.nextDouble() * 1.1;
                double dLon = (rnd.nextDouble() - 0.5) * 1.6, dLat = (rnd.nextDouble() - 0.5) * 0.9;
                Vec3 prev = null;
                for (int s = 0; s <= 10; s++) {
                    double f = s / 10.0;
                    double la = Math.max(0.02, Math.min(1.5, lat0 + dLat * f + (rnd.nextDouble() - 0.5) * 0.08));
                    double lo = lon0 + dLon * f + (rnd.nextDouble() - 0.5) * 0.08;
                    Vec3 n = new Vec3(Math.cos(la) * Math.cos(lo), Math.sin(la), Math.cos(la) * Math.sin(lo));
                    Vec3 p = center.add(n.scale(r * 1.015));
                    if (prev != null) ctx.beam(bolt, prev, p, 0.22f, Colors.alpha(0.9f * late, s % 3 == 0 ? 0xFFFFFF : light), 0, 1);
                    prev = p;
                }
            }
        }

        // ---- drifting fragments ----
        var mote = ctx.additive(VfxTextures.SHARD);
        int n = Math.round(MOTES * Math.min(1, ctx.density));
        for (int i = 0; i < n; i++) {
            float y = ((moteY[i] + time * 0.004f * moteS[i]) % 1f);
            double a = moteA[i] + time * 0.01 * moteS[i];
            Vec3 p = ground.add(Math.cos(a) * moteR[i], y * r * 0.9, Math.sin(a) * moteR[i]);
            float fadeY = Math.min(1, y * 5) * Math.min(1, (1 - y) * 3);
            ctx.billboard(mote, p, 0.35f * moteS[i], (float) a * 3, Colors.alpha(0.8f * k * fadeY, i % 3 == 0 ? light : color));
        }
    }

    /** Plane of an orbit ring: tilted by o[0] about an axis turned o[1] round the vertical. */
    private static Vec3[] orbitBasis(float[] o) {
        Vec3 tiltAxis = new Vec3(Math.cos(o[1]), 0, Math.sin(o[1]));
        Vec3 normal = new Vec3(0, 1, 0).scale(Math.cos(o[0])).add(tiltAxis.cross(new Vec3(0, 1, 0)).scale(Math.sin(o[0])));
        return VfxContext.basis(normal.normalize());
    }
}
