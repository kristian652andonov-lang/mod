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
 * Underwater grottos at the foot of sea cliffs, fjord walls and steep underwater flanks: an arched
 * mouth, a tunnel and a domed chamber with a sandy floor, glowing pickles and glow lichen, some
 * holding an air pocket over a hidden beach (see {@link GrottoPlan}).
 * <p>
 * Runs in the underground decoration step: after ores and vanilla's underwater magma, before
 * springs and vegetation (which then decorate the grottos too). Only natural ground is dug out, so
 * structures stay intact. Each chunk digs the columns it owns from the planned shape. Does nothing
 * outside Terrahaul worlds.
 */
public final class SeaGrottoFeature extends Feature<NoneFeatureConfiguration> {
    public SeaGrottoFeature(Codec<NoneFeatureConfiguration> codec) {
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
        GrottoPlan plan = GrottoPlan.of(planner);
        ChunkPos chunk = new ChunkPos(context.origin());
        var grottos = plan.near(chunk.getMinBlockX(), chunk.getMinBlockZ());
        if (grottos.isEmpty()) {
            return false;
        }
        MarineVolume volume = new MarineVolume(planner, chunk.getMinBlockX(), chunk.getMinBlockZ(), 16, 16, GrottoPlan.MARGIN);
        plan.build(volume);
        return MarineBlocks.emitGrottos(level, volume);
    }
}
