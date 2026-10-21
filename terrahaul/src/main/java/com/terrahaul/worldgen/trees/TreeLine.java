package com.terrahaul.worldgen.trees;

import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.surface.MountainFlora;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Altitude of the tree line. Warm climates carry forests higher than cold ones; in Terrahaul
 * worlds the climate before altitude cooling is read from the terrain model, elsewhere from the
 * biome's base temperature. Trees thin out and shrink towards the line and stop above it.
 * <p>
 * On mountain flanks of Terrahaul worlds the line is the ragged one of {@link MountainFlora},
 * measured from the valley floors of the range and moved by the climate, the aspect, gullies and
 * ridges; it blends into the climate's line towards the foothills. The biomes' own trees therefore
 * end where the mountain forests do.
 */
public final class TreeLine {
    private TreeLine() {
    }

    /** Y of the tree line at {@code pos} (only its x and z matter). */
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
        double line = 230 + 330 * t;
        if (model != null) {
            MountainFlora.Zone f = MountainFlora.at(model, pos.getX(), pos.getZ(), pos.getY() - 1);
            if (f.share > 0.0) {
                line += f.share * (f.line - line);
            }
        }
        return (int) line;
    }

    /**
     * The size a tree at {@code pos} (the block above its ground) grows to at most, from
     * {@code size}: near the tree line trees stay young and small. On mountain flanks the classes
     * step down at 110, 50 and 12 blocks below the line, so the forest shrinks gradually into the
     * stunted trees of the tree line; elsewhere at 110 and 40 blocks below it.
     */
    public static TreeSize cap(WorldGenLevel level, BlockPos pos, TreeSize size) {
        int y = pos.getY();
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model != null) {
            MountainFlora.Zone f = MountainFlora.at(model, pos.getX(), pos.getZ(), y - 1);
            if (f.share > 0.5) {
                double margin = f.margin;
                if (margin < 12.0) {
                    return TreeSize.YOUNG;
                }
                TreeSize most = margin < 50.0 ? TreeSize.SMALL : margin < 110.0 ? TreeSize.MEDIUM : TreeSize.ANCIENT;
                return size.ordinal() > most.ordinal() ? most : size;
            }
        }
        int treeline = treelineY(level, pos);
        if (y > treeline - 40) {
            return TreeSize.YOUNG;
        }
        if (y > treeline - 110 && size.ordinal() > TreeSize.SMALL.ordinal()) {
            return TreeSize.SMALL;
        }
        return size;
    }
}
