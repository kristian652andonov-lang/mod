package com.terrahaul.worldgen.trees;

import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Altitude of the tree line. Warm climates carry forests higher than cold ones; in Terrahaul
 * worlds the climate before altitude cooling is read from the terrain model, elsewhere from the
 * biome's base temperature. Trees thin out and shrink towards the line and stop above it.
 */
public final class TreeLine {
    private TreeLine() {
    }

    public static int treelineY(WorldGenLevel level, BlockPos pos) {
        double t;
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model != null) {
            ColumnSample s = model.sample(pos.getX() & ~3, pos.getZ() & ~3);
            t = (s.baseTemperature + 1.0) / 2.0;
        } else {
            t = (level.getBiome(pos).value().getBaseTemperature() + 0.5) / 2.0;
        }
        t = Math.max(0.0, Math.min(1.0, t));
        return (int) (230 + 330 * t);
    }
}
