package com.fantasyweapons.weapons.ultimate;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * A web of great cracks running out from a point, the same on the server (which carves them into the ground) and on
 * every client (which makes them glow), built from one seed. Main cracks wander outward from the middle and fork;
 * each segment knows how far along the crack it lies, so the cracks can be grown outward over time.
 */
public final class CrackNetwork {
    /**
     * One straight piece of a crack, relative to the centre.
     *
     * @param from  distance along the crack from the centre at its start (blocks), for growing it over time
     * @param width half-width of the crack (blocks)
     * @param depth how deep it is carved (blocks)
     */
    public record Segment(double x0, double z0, double x1, double z1, double from, double width, double depth) {
        public double length() {
            return Math.hypot(x1 - x0, z1 - z0);
        }

        /** Distance of a point (relative to the centre) to this segment, and how far along it the nearest point lies. */
        public double distance(double x, double z, double[] along) {
            double dx = x1 - x0, dz = z1 - z0, len2 = dx * dx + dz * dz;
            double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - x0) * dx + (z - z0) * dz) / len2));
            if (along != null) along[0] = from + t * Math.sqrt(len2);
            double px = x0 + dx * t - x, pz = z0 + dz * t - z;
            return Math.sqrt(px * px + pz * pz);
        }
    }

    private final List<Segment> segments = new ArrayList<>();
    private final double reach;

    private CrackNetwork(double reach) {
        this.reach = reach;
    }

    public List<Segment> segments() {
        return segments;
    }

    public double reach() {
        return reach;
    }

    /**
     * @param mains  how many cracks run out of the middle
     * @param reach  how far they run (blocks)
     * @param width  half-width of a main crack where it starts
     * @param depth  how deep a crack is carved at the middle; it grows shallower outward, to a quarter of that at the edge
     */
    public static CrackNetwork radial(long seed, int mains, double reach, double width, double depth) {
        return radial(seed, mains, 0, reach, width, depth);
    }

    /** {@link #radial(long, int, double, double, double)}, the cracks starting {@code inner} out (round a hole). */
    public static CrackNetwork radial(long seed, int mains, double inner, double reach, double width, double depth) {
        CrackNetwork n = new CrackNetwork(reach);
        n.deepest = depth;
        RandomSource r = RandomSource.create(seed);
        for (int i = 0; i < mains; i++) {
            double ang = (i + r.nextDouble() * 0.6) * Math.PI * 2 / mains;
            double x = Math.cos(ang) * inner, z = Math.sin(ang) * inner;
            n.grow(r, x, z, ang, inner, (reach - inner) * (0.8 + r.nextDouble() * 0.35), width, depth, 0);
        }
        return n;
    }

    /** How deep a crack is at a distance from the middle: the full depth there, a quarter of it at the edge. */
    private double depthAt(double x, double z) {
        double k = Math.min(1, Math.hypot(x, z) / Math.max(1, reach));
        return deepest * (1 - 0.75 * k);
    }

    private double deepest;

    /** A crack wandering from (x, z) at heading {@code ang}, forking as it goes. */
    private void grow(RandomSource r, double x, double z, double ang, double from, double length, double width, double depth, int generation) {
        double step = Math.max(6, length / 14);
        double walked = 0;
        while (walked < length) {
            ang += (r.nextDouble() - 0.5) * 0.55;
            double s = Math.min(step * (0.7 + r.nextDouble() * 0.6), length - walked);
            double nx = x + Math.cos(ang) * s, nz = z + Math.sin(ang) * s;
            double k = 1 - walked / length;
            // deepest nearest the middle (a fork no deeper than the crack it left), shallowest at the edge
            double deep = Math.min(depthAt((x + nx) / 2, (z + nz) / 2) * (depth / Math.max(1, deepest)), depthAt((x + nx) / 2, (z + nz) / 2));
            segments.add(new Segment(x, z, nx, nz, from + walked, Math.max(1, width * (0.35 + 0.65 * k)), Math.max(3, deep)));
            x = nx;
            z = nz;
            walked += s;
            if (generation < 2 && r.nextDouble() < 0.18 && walked < length * 0.8) {
                double side = r.nextBoolean() ? 1 : -1;
                grow(r, x, z, ang + side * (0.5 + r.nextDouble() * 0.6), from + walked, (length - walked) * (0.4 + r.nextDouble() * 0.4),
                        width * 0.55, depth * 0.7, generation + 1);
            }
        }
    }

    /**
     * The crack nearest a point (relative to the centre) that has grown that far by {@code grownTo} (blocks along the
     * crack): its segment, or null if the point is in none. {@code out[0]} gets how far inside the crack the point is
     * (1 at its middle, 0 at its edge).
     */
    /**
     * The crack nearest a point, measured in its own widths, counting cracks up to {@code margin} widths away (so the
     * ground beside a crack can be seared by it): {@code out} gets the distance to its centre line, its half-width and
     * its depth. Null if none is that near.
     */
    public Segment near(double x, double z, double grownTo, double margin, double[] out) {
        Segment best = null;
        double bestRatio = Double.MAX_VALUE;
        double[] along = new double[1];
        for (Segment s : segments) {
            double m = s.width * (1 + margin);
            double minx = Math.min(s.x0, s.x1) - m, maxx = Math.max(s.x0, s.x1) + m;
            double minz = Math.min(s.z0, s.z1) - m, maxz = Math.max(s.z0, s.z1) + m;
            if (x < minx || x > maxx || z < minz || z > maxz) continue;
            double d = s.distance(x, z, along);
            if (along[0] > grownTo) continue;
            double ratio = d / s.width;
            if (ratio <= 1 + margin && ratio < bestRatio) {
                bestRatio = ratio;
                best = s;
                if (out != null) {
                    out[0] = d;
                    out[1] = s.width;
                    out[2] = s.depth;
                }
            }
        }
        return best;
    }

    public Segment at(double x, double z, double grownTo, double[] out) {
        Segment best = null;
        double bestK = 0;
        double[] along = new double[1];
        for (Segment s : segments) {
            // a cheap reject before the exact test
            double minx = Math.min(s.x0, s.x1) - s.width, maxx = Math.max(s.x0, s.x1) + s.width;
            double minz = Math.min(s.z0, s.z1) - s.width, maxz = Math.max(s.z0, s.z1) + s.width;
            if (x < minx || x > maxx || z < minz || z > maxz) continue;
            double d = s.distance(x, z, along);
            if (d > s.width || along[0] > grownTo) continue;
            double k = 1 - d / s.width;
            if (k > bestK) {
                bestK = k;
                best = s;
            }
        }
        if (out != null) out[0] = bestK;
        return best;
    }
}
