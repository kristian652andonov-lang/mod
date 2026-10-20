package com.terrahaul.worldgen.features.lake;

import com.mojang.serialization.Codec;
import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The floors of Terrahaul lakes: littoral shelves and drop-offs, gravel bars, patches of sand,
 * gravel, clay, mud and moss, bedrock ledges, boulders and rock islets, sunken logs, and now and
 * then a boat wreck, a drowned hut or tower or the pilings of an old pier (see
 * {@link LakeRaster}).
 * <p>
 * Runs in the local modifications step, after the surface water feature has filled the elevated
 * lakes and before the floating-terrain cleanup and the plants, so weed can hold on to the rocks
 * and logs. Everything is planned from the terrain model, and each chunk builds only its own
 * columns. Does nothing outside Terrahaul worlds; scaled by the rock and ground detail options.
 */
public final class LakeFloorFeature extends Feature<NoneFeatureConfiguration> {
    public LakeFloorFeature(Codec<NoneFeatureConfiguration> codec) {
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
        if (!planner.mayHaveLake(chunk.getMinBlockX(), chunk.getMinBlockZ(), 0)) {
            return false;
        }
        LakeVolume volume = new LakeVolume(planner, chunk.getMinBlockX(), chunk.getMinBlockZ(), 16, 16);
        LakeRaster.build(volume, TerrahaulConfig.rockDensity(), TerrahaulConfig.groundDetailDensity());
        return LakeBlocks.emitFloor(level, volume);
    }
}
