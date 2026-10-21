package com.terrahaul.worldgen.density;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.water.FallPlanner;

/**
 * Where the noise router's caves may open: the {@code cave_allowance} and {@code cavern_allowance}
 * outputs of {@link TerrainDensityFunction}, as plain functions of the terrain model so the
 * devtools evaluate exactly what the game does. An allowance of 1 lets caves form, 0 keeps the
 * ground solid; the router turns it into {@code terrahaul:caves/guard} ({@code 1.5 - 3 a}), so
 * caves open only where it is at least one half.
 */
public final class CaveGuards {
    private CaveGuards() {
    }

    /**
     * 1 where caves may form, fading to 0 under lake and river beds and near the sea floor, so cave
     * systems do not drain water bodies into the ground. Caves also stay out of everything above
     * the 2D surface (ledges and overhangs, which they could cut off their cliff) and out of the
     * top 32 blocks of cliff rims, which ledges and overhangs hang from. Under and around what a
     * waterfall or cascade builds (its pools, channel, outflow, banks and rock, see
     * {@link FallPlanner#guardLevel}) they keep as far below the lowest of it as they keep below a
     * lake, so its water never stands on a thin shelf over a tunnel.
     * <p>
     * {@code (x, z)} is a corner of the interpolation grid. The column is sampled again after the
     * plans are looked up: planning them uses the model's per-thread cache, which may evict the
     * column's sample.
     */
    public static double caveAllowance(TerrainModel model, int x, int y, int z) {
        double plan = ColumnSample.NO_WATER;
        ColumnSample s = model.sample(x, z);
        if (s.height - y >= 0.0) {
            plan = FallPlanner.guardLevel(model, x, z);
            s = model.sample(x, z);
        }
        int sea = model.seaLevel();
        double depthBelow = s.height - y;
        if (depthBelow < 0.0 || depthBelow < 32.0 && TerrainDetail.rimDrop(model, s, x, z) >= TerrainDetail.RIM_DROP) {
            return 0.0;
        }
        double guard = 1.0;
        if (s.lake > 0.0 || s.river > 0.0 || s.waterLevel != ColumnSample.NO_WATER) {
            guard = Math.min(1.0, Math.max(0.0, (depthBelow - 6.0) / 10.0));
        } else if (y > sea - 2 && y < s.height + 8.0) {
            // next to a lake or elevated river, caves stay well below its surface so they cannot
            // breach the shore and leave the water standing as a wall
            double water = model.nearbyWaterLevel(x, z);
            if (water != ColumnSample.NO_WATER) {
                guard = Math.min(1.0, Math.max(0.0, (water - TerrainModel.WATER_GUARD - y) / 10.0));
            }
        }
        if (plan != ColumnSample.NO_WATER) {
            guard = Math.min(guard, Math.max(0.0, (plan - TerrainModel.WATER_GUARD - y) / 10.0));
        }
        if (s.height < sea && s.waterLevel == ColumnSample.NO_WATER && s.lake <= 0.0 && s.river <= 0.0) {
            // under the sea, caves stay at least 40 blocks below the lowest nearby sea floor, so
            // they never undercut the floor into slabs or open sideways into trench walls
            double below = depthSurface(model, s, x, z) - y;
            guard = Math.min(guard, Math.max(0.0, (below - 40.0) / 24.0));
        }
        return guard;
    }

    /**
     * 1 where large caverns (vanilla "cheese" caves) may open, fading to 0 within 40 blocks of the
     * surface. Depth is measured from the lowest surface within 20 blocks, not just straight up, so
     * on steep slopes and cliffs caverns keep away from the face too. Near the surface they would
     * hollow out the ground into thin shells and leave slabs of terrain floating in the air; small
     * tunnels (cave entrances, spaghetti and noodle caves) still reach the surface.
     */
    public static double cavernAllowance(TerrainModel model, int x, int y, int z) {
        ColumnSample s = model.sample(x, z);
        if (s.height - y > 120.0) {
            return 1.0;
        }
        double below = depthSurface(model, s, x, z) - y;
        return Math.min(1.0, Math.max(0.0, (below - 40.0) / 20.0));
    }

    /**
     * The surface used for the biome depth parameter and the sea-floor and cavern guards: the
     * lowest model surface within 20 blocks. Biome lookups are jittered by a few blocks, so on
     * cliffs and mountain walls the plain column height would put the sample point tens of blocks
     * "underground" and pick cave biomes (dripstone caves, deep dark) for exposed rock faces.
     */
    public static double depthSurface(TerrainModel model, ColumnSample s, int x, int z) {
        if (Double.isNaN(s.depthSurface)) {
            double h = s.height;
            for (int r = 8; r <= 20; r += 12) {
                h = Math.min(h, model.sample(x + r, z).height);
                h = Math.min(h, model.sample(x - r, z).height);
                h = Math.min(h, model.sample(x, z + r).height);
                h = Math.min(h, model.sample(x, z - r).height);
            }
            // only cache for the column the sample belongs to (density is sampled on its corners)
            if (s.x == x && s.z == z) {
                s.depthSurface = h;
            }
            return h;
        }
        return s.depthSurface;
    }
}
