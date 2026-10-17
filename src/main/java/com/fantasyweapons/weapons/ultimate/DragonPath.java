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

    /** How far (blocks) its back takes to settle towards the sky once it flies level, and how hard it banks into turns. */
    private static final double SETTLE = 45, BANK = 80;

    private final Vec3[] pts = new Vec3[SAMPLES + 1];
    private final double[] arc = new double[SAMPLES + 1];
    /** Its heading and which way its back faces at each sample: both change smoothly, and its back never flips over. */
    private final Vec3[] dirs = new Vec3[SAMPLES + 1], ups = new Vec3[SAMPLES + 1];
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
        for (int i = 0; i <= SAMPLES; i++) {
            Vec3 d = pts[Math.min(SAMPLES, i + 2)].subtract(pts[Math.max(0, i - 2)]);
            dirs[i] = d.lengthSqr() > 1e-10 ? d.normalize() : i == 0 ? startDir : dirs[i - 1];
        }
        orient();
    }

    /**
     * Which way its back faces along the path. Carried from sample to sample with as little twist as possible, so it
     * rolls only as the path turns and never flips over where the path runs straight up or down. It starts out so that
     * it comes out of its climb the right way up, and while it flies level its back settles towards the sky, leaning
     * into its turns.
     */
    private void orient() {
        Vec3 first = across(forward, dirs[0]);
        carry(first, false);
        int level = 0;
        while (level < SAMPLES && flatness(dirs[level]) < 0.7) level++;
        double turn = flatness(dirs[level]) < 0.3 ? 0 : angle(ups[level], sky(dirs[level]), dirs[level]);
        carry(rotate(first, dirs[0], turn), true);
    }

    private void carry(Vec3 first, boolean settle) {
        ups[0] = first;
        for (int i = 1; i <= SAMPLES; i++) {
            Vec3 t = dirs[i];
            Vec3 u = across(ups[i - 1], t);
            double h = flatness(t);
            if (settle && h > 0.25) {
                // lean into the turn: tip its back towards the centre of the curve
                int a = Math.max(0, i - 4), b = Math.min(SAMPLES, i + 4);
                Vec3 bend = dirs[b].subtract(dirs[a]).scale(1 / Math.max(1e-6, arc[b] - arc[a]));
                Vec3 left = UP.cross(t).normalize();
                Vec3 want = sky(t).add(left.scale(Mth.clamp(bend.dot(left) * BANK, -0.85, 0.85))).normalize();
                double k = 1 - Math.exp(-(arc[i] - arc[i - 1]) / SETTLE);
                double w = Mth.clamp((h - 0.25) / 0.45, 0, 1);
                u = rotate(u, t, angle(u, want, t) * k * w * w * (3 - 2 * w));
            }
            ups[i] = u;
        }
    }

    /** v made square to the unit vector t (any square vector if v runs along t). */
    private static Vec3 across(Vec3 v, Vec3 t) {
        Vec3 u = v.subtract(t.scale(v.dot(t)));
        if (u.lengthSqr() < 1e-8) u = (Math.abs(t.y) < 0.9 ? UP : new Vec3(1, 0, 0)).subtract(t.scale(Math.abs(t.y) < 0.9 ? t.y : t.x));
        return u.normalize();
    }

    /** How level a heading is: 1 flat, 0 straight up or down. */
    private static double flatness(Vec3 t) {
        return Math.sqrt(Math.max(0, 1 - t.y * t.y));
    }

    /** The sky as seen square to a heading. */
    private static Vec3 sky(Vec3 t) {
        return across(UP, t);
    }

    /** The signed angle from a to b about the axis (all square to it). */
    private static double angle(Vec3 a, Vec3 b, Vec3 axis) {
        return Math.atan2(axis.dot(a.cross(b)), a.dot(b));
    }

    /** v turned by an angle about the unit axis. */
    private static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v) * (1 - c)));
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
        int lo = sample(s);
        return pts[lo].lerp(pts[lo + 1], within(s, lo));
    }

    /** Its heading at {@code s} (straight on past either end). */
    public Vec3 direction(double s) {
        return smooth(dirs, s);
    }

    /** Which way its back faces at {@code s}: square to {@link #direction}, turning smoothly, never flipping over. */
    public Vec3 up(double s) {
        Vec3 t = direction(s);
        return across(smooth(ups, s), t);
    }

    private Vec3 smooth(Vec3[] along, double s) {
        if (s <= 0) return along[0];
        if (s >= total()) return along[SAMPLES];
        int lo = sample(s);
        Vec3 v = along[lo].lerp(along[lo + 1], within(s, lo));
        return v.lengthSqr() < 1e-8 ? along[lo] : v.normalize();
    }

    /** The last sample at or before {@code s} (inside the path). */
    private int sample(double s) {
        int lo = 0, hi = SAMPLES;
        while (hi - lo > 1) {
            int m = (lo + hi) >>> 1;
            if (arc[m] <= s) lo = m;
            else hi = m;
        }
        return lo;
    }

    private double within(double s, int lo) {
        return (s - arc[lo]) / Math.max(1e-6, arc[lo + 1] - arc[lo]);
    }

    /** Fraction of the strike's lead-up done: 0 at the cast, 1 when the head reaches the ground. */
    public float progress(float ticks) {
        return Mth.clamp(ticks / flight, 0, 1);
    }
}
