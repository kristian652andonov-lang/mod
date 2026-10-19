package com.terrahaul.worldgen.density;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.Arrays;

/**
 * The 3D part of the terrain density: rough, recessed cliff faces with notched rims, crags on alpine
 * summits, notches at the waterline of sea cliffs, ledges below cliff rims and, on some cliffs, big
 * overhangs whose underside curves out from the face along a quarter circle to a flat top with a lip.
 * <p>
 * None of it may ever float. The noise chunk evaluates this density at the corners of 4x8x4 cells
 * and interpolates trilinearly in between. A trilinear function has no interior maximum, so every
 * solid region of a cell contains a solid corner, and two solid corners joined by a cell edge are
 * joined by solid blocks. It is therefore enough that every solid corner is linked to the ground by a
 * chain of solid corners along cell edges, and this holds by construction:
 * <ul>
 *   <li>The base density {@code (surface - y) / 8} grows by exactly 1 from one corner to the one 8
 *       blocks below it, and the detail term (alcoves, crags, sea notches) is never allowed to fall
 *       by more than {@value #DETAIL_STEP} from a corner to the corner below it (see
 *       {@link #detail}). So every solid corner of the ground has a solid corner below it, all the
 *       way down: carving can recess faces and notch rims from above, but never undercut rock.</li>
 *   <li>Ledges and overhangs are added on top with {@code max}, so carving never cuts them, and
 *       each of their solid corners is kept only if the corner next to it towards the cliff is
 *       solid too; the chain ends in the cliff's own ground. A ledge corner rests directly on the
 *       cliff column; an overhang corner rests on the overhang corner of the next column in, which
 *       is why the overhang is part of the density of every column under it, however far below
 *       the column's own surface lies.</li>
 * </ul>
 * Detail is only evaluated near the surface and where the terrain model marks steep ground, which
 * keeps the cost low.
 */
public final class TerrainDetail {
    /** Detail is evaluated within this many density units (8 blocks each) of the 2D surface. */
    private static final double BAND = 6.0;
    /** Largest drop of the detail term from one interpolation corner to the corner below it. */
    private static final double DETAIL_STEP = 0.85;
    /** Smallest drop to a neighbouring rim that can carry a ledge. */
    private static final double MIN_DROP = 10.0;
    /** Smallest cliff that can carry a big curved overhang. */
    private static final double BIG_DROP = 24.0;
    /**
     * Largest radius of a big overhang's curved underside: on tall cliffs the arc spans five
     * interpolation columns, enough for the curve to read as one.
     */
    private static final double MAX_RADIUS = 20.0;
    /** Farthest a column under a big overhang lies from its rim column, in cells. */
    private static final int MAX_CELLS = 5;
    /** Most the ground one cell behind a cliff's rim may rise above it for an overhang to hang from it. */
    private static final double FLAT_TOP = 4.0;
    /** Axis directions; neighbouring corner columns are always 4 blocks (one cell) apart. */
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private TerrainDetail() {
    }

    /** The terrain density at a corner of the interpolation grid. */
    public static double density(TerrainModel model, ColumnSample s, int x, int y, int z) {
        double t = (s.height - y) * TerrainDensityFunction.SLOPE;
        if (t > BAND) {
            return t;
        }
        double v = t >= -BAND ? t + detail(model, s, x, y, z) : t;
        if (t > -40.0 && (s.cliff > 0.05 || s.steepness > 0.35 || s.coastCliff > 0.2 || s.canyon > 0.2 || s.plateau > 0.1)) {
            v = Math.max(v, ledge(model, s, x, y, z));
        }
        // overhangs are added at every height: a column under an overhang may lie hundreds of
        // blocks below it, and leaving the overhang out there (while the columns further out, over
        // higher ground, kept their part chained to it) cut the overhang into floating pieces
        return Math.max(v, overhang(model, s, x, y, z));
    }

    /** Density of the ground alone (no ledges or overhangs) at a corner. */
    private static double ground(TerrainModel model, ColumnSample s, int x, int y, int z) {
        double t = (s.height - y) * TerrainDensityFunction.SLOPE;
        return t > BAND || t < -BAND ? t : t + detail(model, s, x, y, z);
    }

    // =========================================================================================
    // Detail: alcoves, crags, sea notches
    // =========================================================================================

    private static boolean detailColumn(ColumnSample s) {
        return s.cliff > 0.02 || s.alpine > 0.2 && s.peakness > 0.55 || s.coastCliff > 0.15;
    }

    /**
     * The detail term at a corner: its raw detail, raised where needed so that it is never more
     * than {@value #DETAIL_STEP} below the detail term of the corner above. That is the largest
     * of the raw detail of this corner and of every corner above it up to well above the surface,
     * each lowered by {@value #DETAIL_STEP} per corner of distance.
     */
    static double detail(TerrainModel model, ColumnSample s, int x, int y, int z) {
        if (!detailColumn(s)) {
            return 0.0;
        }
        if ((y & 7) != 0) {
            return rawDetail(model, s, x, y, z);
        }
        double best = cachedRawDetail(model, s, x, y, z);
        int limit = (int) Math.floor(s.height) + 16;
        double lowered = DETAIL_STEP;
        for (int yy = y + 8; yy <= limit; yy += 8) {
            best = Math.max(best, cachedRawDetail(model, s, x, yy, z) - lowered);
            lowered += DETAIL_STEP;
        }
        return best;
    }

    /** Raw detail of a corner, cached in the column sample (corners are visited many times). */
    private static double cachedRawDetail(TerrainModel model, ColumnSample s, int x, int y, int z) {
        int top = ((int) Math.floor(s.height) & ~7) + 64;
        if (s.detailTop != top || s.detailCache == null) {
            if (s.detailCache == null) {
                s.detailCache = new double[24];
            }
            Arrays.fill(s.detailCache, Double.NaN);
            s.detailTop = top;
        }
        int index = (top - y) >> 3;
        if (index < 0 || index >= s.detailCache.length) {
            return rawDetail(model, s, x, y, z);
        }
        double v = s.detailCache[index];
        if (Double.isNaN(v)) {
            v = rawDetail(model, s, x, y, z);
            s.detailCache[index] = v;
        }
        return v;
    }

    private static double rawDetail(TerrainModel model, ColumnSample s, int x, int y, int z) {
        double t = (s.height - y) * TerrainDensityFunction.SLOPE;
        if (t > BAND || t < -BAND) {
            return 0.0;
        }
        double d = 0.0;
        if (s.cliff > 0.02) {
            // alcoves and recesses in cliff faces; the noise is stretched horizontally so faces
            // break into bands like weathered strata instead of vertical flutes
            double n = model.detail3(0, x / 22.0, y / 14.0, z / 22.0) + 0.45 * model.detail3(1, x / 9.0, y / 7.0, z / 9.0);
            d -= s.cliff * 1.9 * Math.max(0.0, n - 0.15);
        }
        if (s.alpine > 0.2 && s.peakness > 0.55) {
            d += s.alpine * (s.peakness - 0.55) * 1.4 * model.detail3(2, x / 16.0, y / 22.0, z / 16.0);
        }
        int sl = model.seaLevel();
        if (s.coastCliff > 0.15 && y > sl - 8 && y < sl + 14) {
            double band = 1.0 - Math.abs(y - (sl + 3)) / 11.0;
            d -= s.coastCliff * band * 2.2 * Math.max(0.0, model.detail3(3, x / 14.0, y / 6.0, z / 14.0) - 0.05);
        }
        return d;
    }

    // =========================================================================================
    // Ledges
    // =========================================================================================

    /**
     * Density of a ledge jutting from a cliff face at a column at the foot of the cliff, in a band
     * just below the rim of the neighbouring cliff column. Ledges are patchy (3D noise), 5-11 blocks
     * thick and reach about 3-6 blocks out from the face. A corner of a ledge is kept only where the
     * cliff column behind it is solid at the same height.
     */
    private static double ledge(TerrainModel model, ColumnSample s, int x, int y, int z) {
        if (Double.isNaN(s.overhangTop)) {
            computeRims(model, s, x, z);
        }
        double top = s.overhangTop;
        double drop = top - s.height;
        if (drop < MIN_DROP || y >= top) {
            return -1.0E3;
        }
        double thickness = 4.0 + 0.12 * Math.min(drop, 60.0);
        double centre = top - thickness * 0.6;
        double band = 1.0 - Math.abs(y - centre) / thickness;
        if (band <= 0.0) {
            return -1.0E3;
        }
        double n = model.detail3(4, x / 26.0, y / 12.0, z / 26.0) + 0.35 * model.detail3(5, x / 9.0, y / 6.0, z / 9.0);
        double patch = n < -0.25 ? 0.0 : n > 0.25 ? 1.0 : (n + 0.25) / 0.5;
        if (patch <= 0.0) {
            return -1.0E3;
        }
        double f = (s.height - y) * TerrainDensityFunction.SLOPE + band * patch * (drop * TerrainDensityFunction.SLOPE + 0.6) * 1.15;
        if (f <= 0.0) {
            return f;
        }
        int nx = x + DIRS[s.overhangDir][0] * 4;
        int nz = z + DIRS[s.overhangDir][1] * 4;
        return ground(model, model.sample(nx, nz), nx, y, nz) > 0.0 ? f : -1.0;
    }

    /**
     * Highest and lowest model surface 4 blocks away in the four axis directions, and the
     * direction of the highest. The offsets are multiples of 4, so the neighbouring samples never
     * share a cache slot with {@code s} itself.
     */
    private static void computeRims(TerrainModel model, ColumnSample s, int x, int z) {
        double max = -Double.MAX_VALUE;
        double min = Double.MAX_VALUE;
        int dir = 0;
        for (int i = 0; i < 4; i++) {
            double h = model.sample(x + DIRS[i][0] * 4, z + DIRS[i][1] * 4).height;
            if (h > max) {
                max = h;
                dir = i;
            }
            min = Math.min(min, h);
        }
        s.overhangTop = max;
        s.overhangFar = min;
        s.overhangDir = dir;
    }

    /**
     * Depth of the drop next to this column: its surface minus the lowest surface 4 blocks away.
     * Columns with a drop of 10 blocks or more are cliff rims that ledges and overhangs may hang
     * from; the cave guard keeps caves out of their upper part.
     */
    public static double rimDrop(TerrainModel model, ColumnSample s, int x, int z) {
        if (Double.isNaN(s.overhangTop)) {
            computeRims(model, s, x, z);
        }
        return s.height - s.overhangFar;
    }

    /** Smallest {@link #rimDrop} of a column that ledges or overhangs can hang from. */
    public static final double RIM_DROP = MIN_DROP;

    // =========================================================================================
    // Big curved overhangs
    // =========================================================================================

    private static double overhang(TerrainModel model, ColumnSample s, int x, int y, int z) {
        double[] plan = plan(model, s, x, z);
        double best = -1.0E3;
        for (int dir = 0; dir < 4; dir++) {
            if (plan[dir * 4] > 0.0) {
                best = Math.max(best, overhangDir(model, s, plan, x, y, z, dir));
            }
        }
        return best;
    }

    /**
     * Density of the big curved overhang that hangs from the rim in direction {@code dir} over the
     * column at {@code (x, z)}, a whole number of cells out from the rim column. The underside
     * rises from the face along a quarter circle of radius {@code r} to a flat top {@code lip}
     * blocks thick (9-12, so that every column of it holds at least one interpolation corner and it
     * cannot fall apart between them), which droops a little and ends in a short drip edge. A solid
     * corner is kept only if the corner next to it towards the rim is solid: the next column's own
     * ground, its part of the same overhang, or the rim column itself.
     */
    private static double overhangDir(TerrainModel model, ColumnSample s, double[] plan, int x, int y, int z, int dir) {
        int k = (int) plan[dir * 4];
        double rim = plan[dir * 4 + 1];
        double r = plan[dir * 4 + 2];
        double lip = plan[dir * 4 + 3];
        // the face lies halfway between the rim column and the first column out
        double d = 4.0 * k - 2.0;
        double under = rim - r - lip + Math.sqrt(Math.max(0.0, r * r - (r - d) * (r - d)));
        if (d > r - 3.0) {
            under -= 2.0 * (d - (r - 3.0)) / 3.0;
        }
        double top = rim - 0.08 * d - (d > r - 2.0 ? (d - (r - 2.0)) * 1.5 : 0.0);
        double f = Math.min(y - under, top - y) * TerrainDensityFunction.SLOPE;
        if (f <= 0.0) {
            return f;
        }
        int ix = x + DIRS[dir][0] * 4;
        int iz = z + DIRS[dir][1] * 4;
        ColumnSample in = model.sample(ix, iz);
        if (ground(model, in, ix, y, iz) > 0.0) {
            return f;
        }
        if (k > 1) {
            double[] inPlan = plan(model, in, ix, iz);
            if ((int) inPlan[dir * 4] == k - 1 && overhangDir(model, in, inPlan, ix, y, iz, dir) > 0.0) {
                return f;
            }
        }
        return -1.0;
    }

    /**
     * The big curved overhangs a column belongs to, one plan per direction: looking 4 to 20 blocks
     * along the direction for the nearest rim of a cliff facing this way (a rise of at least
     * {@value #BIG_DROP} blocks within two cells, most of it within one, with level ground behind
     * the rim). Everything about an
     * overhang - whether there is one (a smooth 2D mask picks about a third of cliff length),
     * its radius and lip - depends only on the rim column and its two outer neighbours, so every
     * column under the same overhang agrees on its shape.
     */
    private static double[] plan(TerrainModel model, ColumnSample s, int x, int z) {
        if (s.overhangPlan != null && !Double.isNaN(s.bigOverhangRim)) {
            return s.overhangPlan;
        }
        if (s.overhangPlan == null) {
            s.overhangPlan = new double[16];
        }
        double[] plan = s.overhangPlan;
        Arrays.fill(plan, 0.0);
        s.bigOverhangRim = s.height;
        s.bigOverhangRadius = 0.0;
        s.bigOverhangDist = 1.0E9;
        for (int dir = 0; dir < 4; dir++) {
            int dx = DIRS[dir][0] * 4;
            int dz = DIRS[dir][1] * 4;
            for (int k = 1; k <= MAX_CELLS; k++) {
                int rx = x + dx * k;
                int rz = z + dz * k;
                double hRim = model.sample(rx, rz).height;
                double hOut = model.sample(rx - dx, rz - dz).height;
                double hOut2 = model.sample(rx - 2 * dx, rz - 2 * dz).height;
                double rise = hRim - Math.min(hOut, hOut2);
                if (hRim - hOut < 0.6 * BIG_DROP || rise < BIG_DROP) {
                    continue;
                }
                // the nearest cliff decides, whether it carries an overhang or not. Only the top of
                // a cliff does: the overhang continues the flat ground behind the rim out over the
                // drop, and hung from a rim in the middle of a steep slope it was a bulge with more
                // slope rising above it
                double mask = model.detail3(7, rx / 170.0, 3.0, rz / 170.0);
                if (mask >= 0.22 && model.sample(rx + dx, rz + dz).height <= hRim + FLAT_TOP) {
                    double lip = Math.min(12.0, 9.0 + 0.05 * rise);
                    double strength = mask >= 0.4 ? 1.0 : (mask - 0.22) / 0.18;
                    // leave at least 8 blocks of headroom between the underside and the ground below
                    double r = Math.min(Math.min(MAX_RADIUS, 0.45 * rise), rise - lip - 8.0) * strength;
                    if (r >= 6.0 && 4.0 * k - 2.0 < r) {
                        plan[dir * 4] = k;
                        plan[dir * 4 + 1] = hRim;
                        plan[dir * 4 + 2] = r;
                        plan[dir * 4 + 3] = lip;
                        if (r > s.bigOverhangRadius) {
                            s.bigOverhangRim = hRim;
                            s.bigOverhangRadius = r;
                            s.bigOverhangDist = 4.0 * k - 2.0;
                        }
                    }
                }
                break;
            }
        }
        return plan;
    }

    /**
     * The biggest curved overhang over the column at {@code (x, z)} (a multiple of 4), as
     * {rim height, radius, distance from the face}, or {@code null} if there is none.
     */
    public static double[] curvedOverhang(TerrainModel model, int x, int z) {
        ColumnSample s = model.sample(x, z);
        plan(model, s, x, z);
        return s.bigOverhangRadius > 0.0 ? new double[]{s.bigOverhangRim, s.bigOverhangRadius, s.bigOverhangDist} : null;
    }
}
