package com.terrahaul.worldgen.water;

import com.terrahaul.worldgen.density.TerrainDetail;
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
    private final HashMap<Long, Double> densities = new HashMap<>();

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
        // spread by an odd multiplier: corners lie on a 4-block grid, and the plain key's hash
        // (x ^ z) would pile them into a few buckets
        long key = (((long) cx << 32) ^ (cz & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L;
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

    /**
     * Whether the generated 3D terrain is solid at a block: the terrain density interpolated over
     * the noise grid (four blocks across, eight high) exactly as the game does, so overhangs,
     * ledges and rock the heightmap does not show are seen.
     */
    boolean solid(int x, int y, int z) {
        int x0 = x & ~3;
        int z0 = z & ~3;
        int y0 = Math.floorDiv(y, 8) * 8;
        double fx = (x - x0) / 4.0;
        double fz = (z - z0) / 4.0;
        double fy = (y - y0) / 8.0;
        double v0 = lerp(fz, lerp(fx, density(x0, y0, z0), density(x0 + 4, y0, z0)), lerp(fx, density(x0, y0, z0 + 4), density(x0 + 4, y0, z0 + 4)));
        double v1 = lerp(fz, lerp(fx, density(x0, y0 + 8, z0), density(x0 + 4, y0 + 8, z0)),
            lerp(fx, density(x0, y0 + 8, z0 + 4), density(x0 + 4, y0 + 8, z0 + 4)));
        return lerp(fy, v0, v1) > 0.0;
    }

    /**
     * Solid blocks of the 3D terrain in a column from {@code from + 1} up to {@code from + span}:
     * what carving the column down to {@code from} removes above it (overhangs, ledges and crags
     * standing over the model surface). A cell of the interpolation grid whose eight corners are
     * all open is open throughout, so it is skipped without looking at its blocks.
     */
    int solidAbove(int x, int z, int from, int span) {
        int x0 = x & ~3;
        int z0 = z & ~3;
        int count = 0;
        int y = from + 1;
        int end = from + span;
        while (y <= end) {
            int y0 = Math.floorDiv(y, 8) * 8;
            if (density(x0, y0, z0) <= 0.0 && density(x0 + 4, y0, z0) <= 0.0 && density(x0, y0, z0 + 4) <= 0.0 && density(x0 + 4, y0, z0 + 4) <= 0.0
                && density(x0, y0 + 8, z0) <= 0.0 && density(x0 + 4, y0 + 8, z0) <= 0.0 && density(x0, y0 + 8, z0 + 4) <= 0.0
                && density(x0 + 4, y0 + 8, z0 + 4) <= 0.0) {
                y = y0 + 8;
                continue;
            }
            for (int top = Math.min(end, y0 + 7); y <= top; y++) {
                if (solid(x, y, z)) {
                    count++;
                }
            }
        }
        return count;
    }

    private double density(int x, int y, int z) {
        // grid indices packed without overlap (24 bits each across, 16 up), then spread
        long key = ((((long) (x >> 2) & 0xFFFFFFL) << 40) | (((long) (z >> 2) & 0xFFFFFFL) << 16) | ((y >> 3) & 0xFFFFL)) * 0x9E3779B97F4A7C15L;
        Double d = densities.get(key);
        if (d == null) {
            d = TerrainDetail.density(model, corner(x, z), x, y, z);
            densities.put(key, d);
        }
        return d;
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    /**
     * Level of the water body (a river or lake) one of the four corners around the column lies in,
     * or {@code NaN} if none does.
     */
    double bodyLevel(int x, int z) {
        int x0 = x & ~3;
        int z0 = z & ~3;
        for (int i = 0; i < 4; i++) {
            ColumnSample s = corner(x0 + 4 * (i & 1), z0 + 4 * (i >> 1));
            if (s.bodyInside) {
                return s.bodyLevel;
            }
        }
        return Double.NaN;
    }

    /** Whether any of the four corners around the column is inside a water body. */
    boolean bodyWater(int x, int z) {
        int x0 = x & ~3;
        int z0 = z & ~3;
        return corner(x0, z0).bodyInside || corner(x0 + 4, z0).bodyInside || corner(x0, z0 + 4).bodyInside || corner(x0 + 4, z0 + 4).bodyInside;
    }
}
