package com.terrahaul.worldgen.ruins;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Trail geometry recovered from the terrain model's only public trail field,
 * {@link TerrainModel#trailDistance}: centre lines, tangents, junctions and ends.
 * <p>
 * The distance field is irregular in ways these helpers are built around: it is measured in warped
 * space (its gradient in world space varies between about 0.5 and 3), it jumps to "no trail" where
 * a Voronoi edge is dropped, and it widens into small plazas where several edges meet. So the
 * centre is found by Newton steps snapped to the best integer column, and directions come from
 * which points of a ring around a position lie on the trail, never from the gradient (which is
 * zero on the centre line).
 */
final class TrailGeometry {
    /** A ring sample at this distance or less lies on the drawn path. */
    static final double ON = 2.0;
    private static final int RING = 48;

    private TrailGeometry() {
    }

    /**
     * The integer column nearest the centre line of the trail passing near (x, z), or {@code null}
     * when no trail is within reach.
     */
    static int[] centre(TerrainModel m, double x, double z) {
        for (int it = 0; it < 3; it++) {
            int ix = (int) Math.round(x);
            int iz = (int) Math.round(z);
            double d = m.trailDistance(ix, iz);
            if (d > 8.0) {
                return null;
            }
            double ex = m.trailDistance(ix + 1, iz);
            double wx = m.trailDistance(ix - 1, iz);
            double sz = m.trailDistance(ix, iz + 1);
            double nz = m.trailDistance(ix, iz - 1);
            double gx = ex > 1e8 ? (wx > 1e8 ? 0 : d - wx) : wx > 1e8 ? ex - d : (ex - wx) * 0.5;
            double gz = sz > 1e8 ? (nz > 1e8 ? 0 : d - nz) : nz > 1e8 ? sz - d : (sz - nz) * 0.5;
            double g2 = gx * gx + gz * gz;
            if (g2 < 0.05 || d < 0.3) {
                break;
            }
            x -= d * gx / g2;
            z -= d * gz / g2;
        }
        int bx = (int) Math.round(x);
        int bz = (int) Math.round(z);
        double best = Double.MAX_VALUE;
        int rx = bx;
        int rz = bz;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                double d = m.trailDistance(bx + i, bz + j);
                if (d < best) {
                    best = d;
                    rx = bx + i;
                    rz = bz + j;
                }
            }
        }
        return best < 1.2 ? new int[]{rx, rz} : null;
    }

    /** The trail branches crossing a ring of radius {@code r}: their count and mid angles. */
    record Arcs(int count, double[] mids, double[] widths) {
        double dx(int i) {
            return Math.cos(mids[i]);
        }

        double dz(int i) {
            return Math.sin(mids[i]);
        }
    }

    /**
     * Counts the runs of on-trail samples on a ring around (x, z). A count of 2 is a plain stretch,
     * 1 a trail end, 3 or more a junction; -1 means the whole ring lies on the trail (a plaza).
     */
    static Arcs ring(TerrainModel m, double x, double z, double r) {
        boolean[] on = new boolean[RING];
        boolean any = false;
        boolean all = true;
        for (int i = 0; i < RING; i++) {
            double a = i * Math.PI * 2.0 / RING;
            on[i] = m.trailDistance((int) Math.round(x + r * Math.cos(a)), (int) Math.round(z + r * Math.sin(a))) < ON;
            any |= on[i];
            all &= on[i];
        }
        if (all) {
            return new Arcs(-1, new double[0], new double[0]);
        }
        if (!any) {
            return new Arcs(0, new double[0], new double[0]);
        }
        int start = 0;
        while (on[start]) {
            start++;
        }
        double[] mids = new double[8];
        double[] widths = new double[8];
        int count = 0;
        for (int k = 1; k <= RING; k++) {
            int i = (start + k) % RING;
            if (on[i] && !on[(i + RING - 1) % RING]) {
                int len = 0;
                while (on[(i + len) % RING]) {
                    len++;
                }
                if (count < mids.length) {
                    mids[count] = (i + (len - 1) * 0.5) * Math.PI * 2.0 / RING;
                    widths[count] = len * Math.PI * 2.0 / RING;
                }
                count++;
            }
        }
        if (count > mids.length) {
            return new Arcs(count, new double[0], new double[0]);
        }
        double[] m2 = new double[count];
        double[] w2 = new double[count];
        System.arraycopy(mids, 0, m2, 0, count);
        System.arraycopy(widths, 0, w2, 0, count);
        return new Arcs(count, m2, w2);
    }

    /** Angle between two directions given as angles, in [0, pi]. */
    static double angleBetween(double a, double b) {
        double d = Math.abs(a - b) % (Math.PI * 2.0);
        return d > Math.PI ? Math.PI * 2.0 - d : d;
    }

    /**
     * Unit tangent of a plain trail stretch at a centre column, or {@code null} at junctions,
     * ends, plazas and sharp bends: rings of radius 8 and 16 must each cross the trail exactly
     * twice, roughly opposite each other.
     */
    static double[] tangent(TerrainModel m, int cx, int cz) {
        Arcs a = ring(m, cx, cz, 8.0);
        if (a.count() != 2 || angleBetween(a.mids()[0], a.mids()[1]) < Math.toRadians(130)) {
            return null;
        }
        Arcs b = ring(m, cx, cz, 16.0);
        if (b.count() != 2 || angleBetween(b.mids()[0], b.mids()[1]) < Math.toRadians(125)) {
            return null;
        }
        double tx = b.dx(1) - b.dx(0);
        double tz = b.dz(1) - b.dz(0);
        double l = Math.hypot(tx, tz);
        if (l < 0.5) {
            return null;
        }
        tx /= l;
        tz /= l;
        // the short ring must agree with the long one, or the trail bends within the footprint
        double sx = a.dx(1) - a.dx(0);
        double sz = a.dz(1) - a.dz(0);
        double sl = Math.hypot(sx, sz);
        if (sl < 0.5 || Math.abs(sx * tx + sz * tz) / sl < Math.cos(Math.toRadians(25))) {
            return null;
        }
        return new double[]{tx, tz};
    }

    /**
     * Whether the trail is actually drawn at (x, z): close to its centre, on ground where trails
     * are laid, and gentle enough for the surface rule (it draws trails only where the ground
     * rises at most 3 blocks over 2).
     */
    static boolean laid(TerrainModel m, int x, int z) {
        if (m.trailDistance(x, z) >= ON) {
            return false;
        }
        if (!TerrainModel.trailGround(m.sample((x + 2) & ~3, (z + 2) & ~3), m.seaLevel())) {
            return false;
        }
        return slope(m, x, z) <= 3.0;
    }

    /** Largest height difference to the columns two blocks away, like the surface rule's slope. */
    static double slope(TerrainModel m, int x, int z) {
        double h = Math.floor(m.interpolatedHeight(x, z));
        double s = 0.0;
        s = Math.max(s, Math.abs(Math.floor(m.interpolatedHeight(x + 2, z)) - h));
        s = Math.max(s, Math.abs(Math.floor(m.interpolatedHeight(x - 2, z)) - h));
        s = Math.max(s, Math.abs(Math.floor(m.interpolatedHeight(x, z + 2)) - h));
        s = Math.max(s, Math.abs(Math.floor(m.interpolatedHeight(x, z - 2)) - h));
        return s;
    }

    /** Dry, buildable land: no water, river, lake or stream, no cliff, canyon, volcano or beach. */
    static boolean land(ColumnSample s, int seaLevel) {
        return s.height > seaLevel + 2.0 && s.waterLevel == ColumnSample.NO_WATER && s.river <= 0.0 && s.lake <= 0.0
            && s.riverValley < 0.95 && s.stream < 0.05 && s.cliff < 0.2 && s.canyon < 0.08 && s.volcano < 0.1 && s.beach < 0.3
            && s.swamp < 0.55 && s.steepness < 0.5 && s.coastCliff < 0.2;
    }

    /**
     * Walks along a trail from a centre column in direction {@code t}, collecting centre points
     * {x, z, tx, tz} about 4 blocks apart, until the trail ends, forks, turns back, stops being
     * laid or leaves the box. The first point is the start itself.
     */
    static List<double[]> march(TerrainModel m, int[] start, double[] t, int maxSteps, int minX, int minZ, int maxX, int maxZ) {
        List<double[]> out = new ArrayList<>();
        double x = start[0];
        double z = start[1];
        double tx = t[0];
        double tz = t[1];
        out.add(new double[]{x, z, tx, tz});
        for (int step = 0; step < maxSteps; step++) {
            int[] nc = centre(m, x + 4.0 * tx, z + 4.0 * tz);
            if (nc == null) {
                break;
            }
            double dx = nc[0] - x;
            double dz = nc[1] - z;
            double l = Math.hypot(dx, dz);
            if (l < 1.0 || (dx * tx + dz * tz) / l < 0.3) {
                break;
            }
            if (nc[0] < minX || nc[0] > maxX || nc[1] < minZ || nc[1] > maxZ || !laid(m, nc[0], nc[1])) {
                break;
            }
            if ((step & 3) == 3 && ring(m, nc[0], nc[1], 12.0).count() != 2) {
                break;
            }
            double ntx = tx + dx / l;
            double ntz = tz + dz / l;
            double nl = Math.hypot(ntx, ntz);
            tx = ntx / nl;
            tz = ntz / nl;
            x = nc[0];
            z = nc[1];
            out.add(new double[]{x, z, tx, tz});
        }
        return out;
    }
}
