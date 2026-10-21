package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.VolcanoZones;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Feature {@code terrahaul:volcano_thaw}: takes the snow layers that vanilla's
 * {@code freeze_top_layer} lays on every top block in cold air off a volcano's bare ground where
 * {@link VolcanoZones#snow} lets no snow lie: the hot ground of a crater that is not long dormant
 * and of an active volcano's lava flows, and the blobs of ash and scoria that break up the snow on
 * the cone. Without it the high cones of Terrahaul's tall volcanoes would be white all over
 * whatever the surface rule laid there, crater and lava shore included.
 * <p>
 * Vanilla only lays a snow layer where the air is cold enough, so a layer is taken as cold air and
 * judged as in a snowy biome. The feature runs once per chunk in the same step as
 * {@code freeze_top_layer}, after it, and only in chunks on a volcano; it changes nothing but snow
 * layers on top of bare volcanic ground.
 */
public final class VolcanoThawFeature extends Feature<NoneFeatureConfiguration> {
    public VolcanoThawFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = chunk.getMinBlockX() + dx;
                int z = chunk.getMinBlockZ() + dz;
                // a snow layer does not block motion: it stands just above the heightmap
                pos.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                if (!level.getBlockState(pos).is(Blocks.SNOW)) {
                    continue;
                }
                VolcanoZones.Zone zone = VolcanoZones.at(model, x, z);
                if (zone.presence <= 0.0 || !VolcanoZones.ground(model, zone, x, pos.getY() - 1, z).bare()
                    || VolcanoZones.snow(model, zone, x, z, model.sample(x & ~3, z & ~3).temperature, true)) {
                    continue;
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                changed = true;
            }
        }
        return changed;
    }
}
