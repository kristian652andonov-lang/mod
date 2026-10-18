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
 *       that is solid up to that rim.</li>
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
        if (t < 0.0 && t > -40.0 && (s.cliff > 0.05 || s.steepness > 0.35 || s.coastCliff > 0.2 || s.canyon > 0.2)) {
            t += overhang(model, s, x, y, z);
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
