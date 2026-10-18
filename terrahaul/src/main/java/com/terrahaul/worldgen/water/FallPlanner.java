package com.terrahaul.worldgen.water;

import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.List;

/** Plans waterfalls and cascades (placeholder until the planner is written). */
public final class FallPlanner {
    private FallPlanner() {
    }

    /** Adds the plans whose bounds intersect the given block rectangle. */
    public static void plansNear(TerrainModel model, int minX, int minZ, int maxX, int maxZ, List<WaterPlan> out) {
    }
}
