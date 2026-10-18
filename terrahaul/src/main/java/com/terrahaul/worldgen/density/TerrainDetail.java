package com.terrahaul.worldgen.density;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;

/**
 * The 3D part of the terrain density: overhanging ledges below cliff rims, alcoves in cliff faces,
 * crags on alpine summits and sea caves along rocky coasts.
 * <p>
 * It is only evaluated near the surface and only where the terrain model marks a landform that
 * should have it, which keeps both the cost and the risk of floating fragments low. Carving terms
 * only remove material. The two additive terms are attached by construction:
 * <ul>
 *   <li>the small alpine crag term stays well below the terrain gradient;</li>
 *   <li>ledges grow out of a cliff face, in a band just below the rim of a neighbouring column
 *       that is solid up to that rim;</li>
 *   <li>big curved overhangs continue the top of a tall, sheer cliff outwards over a
 *       quarter-circle underside (on some stretches of cliff only).</li>
 * </ul>
 */
public final class TerrainDetail {
    private static final double BAND = 6.0;
    /** Smallest drop to a neighbouring rim that can carry an overhang. */
    private static final double MIN_DROP = 10.0;

    private TerrainDetail() {
    }

    public static double density(TerrainModel model, ColumnSample s, int x, int y, int z) {
        double t = (s.height - y) * TerrainDensityFunction.SLOPE;
        if (t < 0.0 && t > -40.0 && (s.cliff > 0.05 || s.steepness > 0.35 || s.coastCliff > 0.2 || s.canyon > 0.2 || s.plateau > 0.1)) {
            t = Math.max(t + overhang(model, s, x, y, z), curvedOverhang(model, s, x, y, z));
        }
        if (t > BAND || t < -BAND) {
            return t;
        }
        double detail = 0.0;
        if (s.cliff > 0.02) {
            double n = model.detail3(0, x / 22.0, y / 14.0, z / 22.0) + 0.45 * model.detail3(1, x / 9.0, y / 7.0, z / 9.0);
            detail -= s.cliff * 1.9 * Math.max(0.0, n - 0.15);
        }
        if (s.alpine > 0.2 && s.peakness > 0.55) {
            detail += s.alpine * (s.peakness - 0.55) * 1.4 * model.detail3(2, x / 16.0, y / 22.0, z / 16.0);
        }
        int sl = model.seaLevel();
        if (s.coastCliff > 0.15 && y > sl - 8 && y < sl + 14) {
            double band = 1.0 - Math.abs(y - (sl + 3)) / 11.0;
            detail -= s.coastCliff * band * 2.2 * Math.max(0.0, model.detail3(3, x / 14.0, y / 6.0, z / 14.0) - 0.05);
        }
        return t + detail;
    }

    /**
     * Density added to a column at the foot of a cliff so that a ledge of rock juts out from the
     * face just below the neighbouring rim. Ledges are patchy (3D noise), 5-11 blocks thick and
     * reach about 3-6 blocks out from the face: the neighbour 4 blocks away carries a full ledge,
     * the one 8 blocks away only a weak one.
     */
    private static double overhang(TerrainModel model, ColumnSample s, int x, int y, int z) {
        if (Double.isNaN(s.overhangTop)) {
            computeRims(model, s, x, z);
        }
        double near = ledge(model, s.overhangTop, s.height, x, y, z);
        double far = s.overhangFar > s.overhangTop + 2.0 ? 0.6 * ledge(model, s.overhangFar, s.height, x, y, z) : 0.0;
        return Math.max(near, far);
    }

    private static double ledge(TerrainModel model, double top, double height, int x, int y, int z) {
        double drop = top - height;
        if (drop < MIN_DROP || y >= top) {
            return 0.0;
        }
        double thickness = 4.0 + 0.12 * Math.min(drop, 60.0);
        double centre = top - thickness * 0.6;
        double band = 1.0 - Math.abs(y - centre) / thickness;
        if (band <= 0.0) {
            return 0.0;
        }
        double n = model.detail3(4, x / 26.0, y / 12.0, z / 26.0) + 0.35 * model.detail3(5, x / 9.0, y / 6.0, z / 9.0);
        double patch = n < -0.25 ? 0.0 : n > 0.25 ? 1.0 : (n + 0.25) / 0.5;
        return band * patch * (drop * TerrainDensityFunction.SLOPE + 0.6) * 1.15;
    }

    /** Smallest cliff that can carry a big curved overhang. */
    private static final double BIG_DROP = 26.0;

    /**
     * Density of a big curved overhang at a column on the low side of a sheer cliff: the cliff top
     * continues outwards for up to 15 blocks, its underside curving from the face up and out along a
     * quarter circle to a lip 5-8 blocks thick, with a short drip edge at the tip. Positive inside
     * the overhang; very negative where there is none, so it only ever adds rock. A smooth 2D mask
     * picks about a third of cliff length, and the radius tapers to zero at the mask's edges.
     */
    private static double curvedOverhang(TerrainModel model, ColumnSample s, int x, int y, int z) {
        if (Double.isNaN(s.bigOverhangRim)) {
            computeBigOverhang(model, s, x, z);
        }
        double r = s.bigOverhangRadius;
        double d = s.bigOverhangDist;
        double rim = s.bigOverhangRim;
        if (r <= 0.0 || d >= r || y > rim) {
            return -1.0E3;
        }
        double drop = rim - s.height;
        double lip = Math.min(8.0, 5.0 + 0.06 * drop);
        double y0 = rim - r - lip;
        double under = y0 + Math.sqrt(r * r - (r - d) * (r - d));
        if (d > r - 3.0) {
            // drip edge: the tip hangs down a little
            under -= 2.0 * (d - (r - 3.0)) / 3.0;
        }
        // the top droops slightly towards the tip
        double top = rim - 0.12 * d - (d > r - 2.0 ? (d - (r - 2.0)) * 1.5 : 0.0);
        return Math.min(y - under, top - y) * TerrainDensityFunction.SLOPE;
    }

    /**
     * The big curved overhang over the column at {@code (x, z)} (a multiple of 4), as
     * {rim height, radius, distance from the face}, or {@code null} if there is none.
     */
    public static double[] curvedOverhang(TerrainModel model, int x, int z) {
        ColumnSample s = model.sample(x, z);
        if (Double.isNaN(s.bigOverhangRim)) {
            computeBigOverhang(model, s, x, z);
        }
        return s.bigOverhangRadius > 0.0 && s.bigOverhangDist < s.bigOverhangRadius
            ? new double[]{s.bigOverhangRim, s.bigOverhangRadius, s.bigOverhangDist} : null;
    }

    /**
     * Looks along the four axes, 4 to 16 blocks out, for a sheer cliff: a rise of at least
     * {@value #BIG_DROP} blocks, most of it within 4 blocks. The nearest one sets the rim and this
     * column's distance from its face.
     */
    private static void computeBigOverhang(TerrainModel model, ColumnSample s, int x, int z) {
        s.bigOverhangRim = s.height;
        s.bigOverhangRadius = 0.0;
        s.bigOverhangDist = 1.0E9;
        double mask = model.detail3(7, x / 170.0, 3.0, z / 170.0);
        if (mask < 0.22) {
            return;
        }
        double low = s.height;
        double bestDist = 1.0E9;
        double bestRim = low;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            double prev = low;
            for (int k = 4; k <= 16; k += 4) {
                double hk = model.sample(x + dir[0] * k, z + dir[1] * k).height;
                if (hk - low >= BIG_DROP) {
                    if (hk - prev >= 0.6 * (hk - low)) {
                        double mid = (low + hk) * 0.5;
                        double frac = Math.max(0.0, Math.min(1.0, (mid - prev) / (hk - prev)));
                        double dist = (k - 4) + 4.0 * frac;
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestRim = hk;
                        }
                    }
                    break;
                }
                prev = hk;
            }
        }
        if (bestDist >= 16.0) {
            return;
        }
        double drop = bestRim - low;
        double lip = Math.min(8.0, 5.0 + 0.06 * drop);
        double strength = mask >= 0.4 ? 1.0 : (mask - 0.22) / 0.18;
        // leave at least 8 blocks of headroom between the underside and the ground below
        double r = Math.min(Math.min(15.0, 0.4 * drop), drop - lip - 8.0) * strength;
        if (r < 5.0) {
            return;
        }
        s.bigOverhangRim = bestRim;
        s.bigOverhangDist = bestDist;
        s.bigOverhangRadius = r;
    }

    /**
     * Highest model surface 4 blocks away (any of the four directions) and 8 blocks away. The
     * offsets are multiples of 4, so the neighbouring samples never share a cache slot with
     * {@code s} itself.
     */
    private static void computeRims(TerrainModel model, ColumnSample s, int x, int z) {
        double near = Math.max(Math.max(model.sample(x + 4, z).height, model.sample(x - 4, z).height),
            Math.max(model.sample(x, z + 4).height, model.sample(x, z - 4).height));
        double far = Math.max(Math.max(model.sample(x + 8, z).height, model.sample(x - 8, z).height),
            Math.max(model.sample(x, z + 8).height, model.sample(x, z - 8).height));
        s.overhangFar = far;
        s.overhangTop = near;
    }
}
