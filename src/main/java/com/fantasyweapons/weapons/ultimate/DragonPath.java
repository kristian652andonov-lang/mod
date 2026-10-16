package com.fantasyweapons.weapons.ultimate;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Arch Dragon of Hell's flight, worked out the same way on the server (who it burns) and the client (where it is
 * drawn): it bursts up out of the rift behind the caster, climbs, sweeps round in a long arc high over the land, rears
 * up over the target and dives straight down into it. Positions are by arc length; before 0 it is still below the rift,
 * past {@link #total()} it is ploughing on into the ground.
 */
public final class DragonPath {
    private static final int SAMPLES = 480;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private final Vec3[] pts = new Vec3[SAMPLES + 1];
    private final double[] arc = new double[SAMPLES + 1];
    private final Vec3 rift, target, forward, side;
    private final Vec3 startDir, endDir;
    private final int flight;

    public DragonPath(Vec3 caster, Vec3 rift, Vec3 target, int flight, long seed) {
        this.rift = rift;
        this.target = target;
        this.flight = Math.max(20, flight);
        Vec3 f = new Vec3(target.x - caster.x, 0, target.z - caster.z);
        if (f.lengthSqr() < 1) f = new Vec3(target.x - rift.x, 0, target.z - rift.z);
        if (f.lengthSqr() < 1e-4) f = new Vec3(0, 0, 1);
        forward = f.normalize();
        side = new Vec3(-forward.z, 0, forward.x).scale((seed & 1) == 0 ? 1 : -1);
        double span = Math.max(40, Math.hypot(target.x - caster.x, target.z - caster.z));
        double sky = Math.max(caster.y, target.y);
        Vec3 mid = caster.add(target).scale(0.5);
        Vec3[] k = {
                rift.add(0, -90, 0),
                rift,
                rift.add(forward.scale(-25)).add(0, 105, 0),
                new Vec3(mid.x, sky + 165, mid.z).add(side.scale(95 + span * 0.25)),
                new Vec3(target.x, sky + 205, target.z).subtract(forward.scale(45)).add(side.scale(-25)),
                target,
                target.add(0, -120, 0)
        };
        // Catmull-Rom through the keys, from the rift to the target
        int spans = k.length - 3;
        for (int i = 0; i <= SAMPLES; i++) {
            double u = i / (double) SAMPLES * spans;
            int s = Math.min(spans - 1, (int) u);
            pts[i] = catmull(k[s], k[s + 1], k[s + 2], k[s + 3], u - s);
            arc[i] = i == 0 ? 0 : arc[i - 1] + pts[i].distanceTo(pts[i - 1]);
        }
        startDir = pts[1].subtract(pts[0]).normalize();
        endDir = pts[SAMPLES].subtract(pts[SAMPLES - 1]).normalize();
    }

    private static Vec3 catmull(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return p0.scale(-0.5 * t3 + t2 - 0.5 * t)
                .add(p1.scale(1.5 * t3 - 2.5 * t2 + 1))
                .add(p2.scale(-1.5 * t3 + 2 * t2 + 0.5 * t))
                .add(p3.scale(0.5 * t3 - 0.5 * t2));
    }

    public double total() {
        return arc[SAMPLES];
    }

    public int flight() {
        return flight;
    }

    public Vec3 rift() {
        return rift;
    }

    public Vec3 target() {
        return target;
    }

    public Vec3 forward() {
        return forward;
    }

    /** How far along the path the head is after {@code ticks}: it speeds up into the dive, then ploughs on at that speed. */
    public double head(float ticks) {
        double u = ticks / flight;
        if (u <= 1) return total() * (0.7 * u + 0.3 * u * u);
        return total() * (1 + 1.3 * (u - 1));
    }

    /** Blocks per tick the head moves at the moment it strikes. */
    public double strikeSpeed() {
        return total() * 1.3 / flight;
    }

    public Vec3 at(double s) {
        if (s <= 0) return pts[0].add(startDir.scale(s));
        if (s >= total()) return pts[SAMPLES].add(endDir.scale(s - total()));
        int lo = 0, hi = SAMPLES;
        while (hi - lo > 1) {
            int m = (lo + hi) >>> 1;
            if (arc[m] <= s) lo = m;
            else hi = m;
        }
        double t = (s - arc[lo]) / Math.max(1e-6, arc[hi] - arc[lo]);
        return pts[lo].lerp(pts[hi], t);
    }

    public Vec3 tangent(double s) {
        Vec3 d = at(s + 1.5).subtract(at(s - 1.5));
        return d.lengthSqr() < 1e-8 ? startDir : d.normalize();
    }

    /** A frame for the body at {@code s}: {tangent, right, up}, its "up" kept as near the sky as the tangent allows. */
    public Vec3[] frame(double s, Vec3 prevUp) {
        Vec3 t = tangent(s);
        Vec3 ref = prevUp != null ? prevUp : Math.abs(t.y) > 0.92 ? forward.scale(-1) : UP;
        Vec3 up = ref.subtract(t.scale(ref.dot(t)));
        if (up.lengthSqr() < 1e-6) up = forward.scale(-1).subtract(t.scale(-forward.dot(t)));
        up = up.normalize();
        return new Vec3[]{t, t.cross(up).normalize(), up};
    }

    /** Fraction of the strike's lead-up done: 0 at the cast, 1 when the head reaches the ground. */
    public float progress(float ticks) {
        return Mth.clamp(ticks / flight, 0, 1);
    }
}
