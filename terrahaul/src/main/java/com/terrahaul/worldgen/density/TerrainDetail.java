package com.terrahaul.worldgen.density;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;

/**
 * The 3D part of the terrain density: overhangs and alcoves on cliffs, crags on alpine summits and
 * sea caves along rocky coasts. It is only evaluated within 48 blocks of the surface and only where
 * the terrain model marks a landform that should have it, which keeps both the cost and the risk of
 * floating fragments low; carving terms are one-sided (they only remove material) except the small
 * alpine crag term, whose amplitude stays well below the terrain gradient.
 */
public final class TerrainDetail {
    private static final double BAND = 6.0;

    private TerrainDetail() {
    }

    public static double density(TerrainModel model, ColumnSample s, int x, int y, int z) {
        double t = (s.height - y) * TerrainDensityFunction.SLOPE;
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
}
