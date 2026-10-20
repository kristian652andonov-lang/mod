package com.terrahaul.worldgen.features.lake;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Freshwater life in and around Terrahaul lakes (see {@link LakeLifeRaster}): seagrass meadows and
 * weed beds on the floor, dripleaf in lush shallows, lily pads on calm water, reeds and moss
 * carpets on the shore. Each plant grows on its column's real top, found in the world, so weed
 * holds on to the rocks and logs the floor feature placed; every plant goes into a water source
 * (or onto the bank) over the block it needs, so all of it survives. Does nothing outside
 * Terrahaul worlds.
 */
public final class LakeLifeFeature extends Feature<NoneFeatureConfiguration> {
    public LakeLifeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model == null) {
            return false;
        }
        LakePlanner planner = LakePlanner.of(model);
        ChunkPos chunk = new ChunkPos(context.origin());
        // the shore columns of a chunk may border a lake in the next one
        if (!planner.mayHaveLake(chunk.getMinBlockX(), chunk.getMinBlockZ(), 1)) {
            return false;
        }
        return LakeBlocks.grow(level, planner, chunk.getMinBlockX(), chunk.getMinBlockZ());
    }
}
