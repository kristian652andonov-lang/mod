package com.terrahaul.worldgen.features.marine;

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
 * Rock on the sea floors of Terrahaul worlds: bedrock ledges, boulders (talus under sea cliffs,
 * glacial erratics on cold shelves, boulder fields, kelp-forest rocks), gravel patches, cobbles,
 * pebbles and knee-high rocks, each rock in one colour family (see {@link RockRaster}).
 * <p>
 * Runs in the local modifications step, before structures, so shipwrecks and ruins overwrite rocks
 * rather than the other way round, and before vegetation, so kelp can hold on to the rocks.
 * Boulders are planned from the terrain model and built column by column by each chunk they
 * cover. Does nothing outside Terrahaul worlds; scaled by the rock and ground detail options.
 */
public final class SeafloorRocksFeature extends Feature<NoneFeatureConfiguration> {
    public SeafloorRocksFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model == null) {
            return false;
        }
        MarinePlanner planner = MarinePlanner.of(model);
        ChunkPos chunk = new ChunkPos(context.origin());
        if (!planner.mayHaveSea(chunk.getMinBlockX(), chunk.getMinBlockZ(), 14)) {
            return false;
        }
        MarineVolume volume = new MarineVolume(planner, chunk.getMinBlockX(), chunk.getMinBlockZ(), 16, 16, RockRaster.MARGIN);
        RockRaster.build(volume, TerrahaulConfig.rockDensity(), TerrahaulConfig.groundDetailDensity());
        return MarineBlocks.emitRocks(level, volume);
    }
}
