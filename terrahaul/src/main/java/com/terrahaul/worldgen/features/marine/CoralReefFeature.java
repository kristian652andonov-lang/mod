package com.terrahaul.worldgen.features.marine;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Coral reefs in warm, shallow seas: fringing reefs with lagoon, reef flat and reef front, spurs
 * and grooves, patch reefs, coral heads, table corals, staghorn thickets, pillar corals and fan
 * gardens, each colony one species and colour, with seagrass meadows on the lagoon sand (see
 * {@link ReefRaster}). Replaces vanilla's warm ocean vegetation in Terrahaul worlds.
 * <p>
 * Runs in the vegetal decoration step, after shipwrecks and ruins have been placed: it only grows
 * into water and leaves columns holding a structure alone. Each chunk plans the reef of its own
 * columns plus a margin from the terrain model and writes only its own columns, so reefs spanning
 * many chunks join up seamlessly. Does nothing outside Terrahaul worlds.
 */
public final class CoralReefFeature extends Feature<NoneFeatureConfiguration> {
    public CoralReefFeature(Codec<NoneFeatureConfiguration> codec) {
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
        if (!planner.mayHaveReef(chunk.getMinBlockX(), chunk.getMinBlockZ(), ReefRaster.MARGIN + 2)) {
            return false;
        }
        MarineVolume volume = new MarineVolume(planner, chunk.getMinBlockX(), chunk.getMinBlockZ(), 16, 16, ReefRaster.MARGIN);
        ReefRaster.build(volume);
        return MarineBlocks.emitReef(level, volume);
    }
}
