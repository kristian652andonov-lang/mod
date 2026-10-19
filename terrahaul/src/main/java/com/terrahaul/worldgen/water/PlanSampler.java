package com.terrahaul.worldgen.water;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.HashMap;

/**
 * The terrain model's grid corners as a planner sees them: a private cache, so planning a
 * waterfall (hundreds of samples) never evicts the samples of the model's per-thread cache that
 * chunk generation is using, and block-resolution heights interpolated exactly like the generated
 * ground. Not thread-safe; one instance per planning call.
 */
final class PlanSampler {
    private final TerrainModel model;
    private final HashMap<Long, ColumnSample> corners = new HashMap<>();

    PlanSampler(TerrainModel model) {
        this.model = model;
    }

    TerrainModel model() {
        return model;
    }

    /** The model sample of the grid corner at or below-left of {@code (x, z)}. */
    ColumnSample corner(int x, int z) {
        int cx = x & ~3;
        int cz = z & ~3;
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        ColumnSample s = corners.get(key);
        if (s == null) {
            s = new ColumnSample();
            model.sampleInto(cx, cz, s);
            corners.put(key, s);
        }
        return s;
    }

    /** Terrain height at block resolution, as {@link TerrainModel#interpolatedHeight} computes it. */
    double height(int x, int z) {
        int x0 = x & ~3;
        int z0 = z & ~3;
        double fx = (x - x0) / 4.0;
        double fz = (z - z0) / 4.0;
        double h00 = corner(x0, z0).height;
        double h10 = corner(x0 + 4, z0).height;
        double h01 = corner(x0, z0 + 4).height;
        double h11 = corner(x0 + 4, z0 + 4).height;
        // the same expression as the model's, so both round to the same blocks
        double a = h00 + (h10 - h00) * fx;
        double b = h01 + (h11 - h01) * fx;
        return a + (b - a) * fz;
    }

    /** Top solid block of the model ground. */
    int ground(int x, int z) {
        return (int) Math.ceil(height(x, z)) - 1;
    }

    /** The corner nearest to {@code (x, z)}, for the fields that are not interpolated. */
    ColumnSample nearest(int x, int z) {
        return corner(x + 2, z + 2);
    }

    /** Whether any of the four corners around the column is inside a water body. */
    boolean bodyWater(int x, int z) {
        int x0 = x & ~3;
        int z0 = z & ~3;
        return corner(x0, z0).bodyInside || corner(x0 + 4, z0).bodyInside || corner(x0, z0 + 4).bodyInside || corner(x0 + 4, z0 + 4).bodyInside;
    }
}
