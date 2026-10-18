package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * Irregular boulders: the union of two to four overlapping, randomly stretched ellipsoids with a
 * jittered surface, partly sunk into the ground so they look like they belong to the terrain. The
 * rock follows the local geology (granite boulders in granite ranges, basalt near volcanoes, red
 * sandstone in hot canyon country) and humid areas grow moss on the upper surfaces.
 */
public final class BoulderFeature extends Feature<BoulderConfiguration> {
    public BoulderFeature(Codec<BoulderConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<BoulderConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BoulderConfiguration config = context.config();
        BlockPos origin = context.origin();
        BlockState ground = level.getBlockState(origin.below());
        if (ground.isAir() || !ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES) || ground.is(BlockTags.LOGS)) {
            return false;
        }
        double radius = Math.min(7.5, config.radius().sample(random));
        int geology = 0;
        int climate = 0;
        double mossChance = config.moss();
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model != null && config.geology()) {
            ColumnSample s = model.sample(origin.getX() & ~3, origin.getZ() & ~3);
            geology = s.geology;
            climate = s.baseTemperature > 0.55 && s.humidity < 0.0 ? (s.baseTemperature > 0.75 ? 2 : 1) : 0;
            mossChance *= Math.max(0.0, Math.min(1.5, 0.6 + s.humidity));
        }
        int lobes = 2 + random.nextInt(3);
        double[][] lobe = new double[lobes][6];
        for (int i = 0; i < lobes; i++) {
            double r = radius * (i == 0 ? 1.0 : 0.55 + random.nextDouble() * 0.35);
            lobe[i][0] = i == 0 ? 0 : (random.nextDouble() - 0.5) * radius * 1.2;
            lobe[i][1] = i == 0 ? 0 : (random.nextDouble() - 0.4) * radius * 0.5;
            lobe[i][2] = i == 0 ? 0 : (random.nextDouble() - 0.5) * radius * 1.2;
            lobe[i][3] = r * (0.85 + random.nextDouble() * 0.4);
            lobe[i][4] = r * (0.65 + random.nextDouble() * 0.3);
            lobe[i][5] = r * (0.85 + random.nextDouble() * 0.4);
        }
        double sink = radius * config.bury();
        int extent = Mth.ceil(radius * 1.9);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dx = -extent; dx <= extent; dx++) {
            for (int dz = -extent; dz <= extent; dz++) {
                for (int dy = -extent; dy <= extent; dy++) {
                    double inside = 0.0;
                    for (double[] l : lobe) {
                        double nx = (dx - l[0]) / l[3];
                        double ny = (dy + sink - l[1]) / l[4];
                        double nz = (dz - l[2]) / l[5];
                        inside = Math.max(inside, 1.0 - (nx * nx + ny * ny + nz * nz));
                    }
                    if (inside <= 0.0 || inside < 0.12 && random.nextFloat() < 0.5) {
                        continue;
                    }
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState existing = level.getBlockState(pos);
                    if (existing.is(Blocks.BEDROCK) || existing.is(BlockTags.LOGS) || existing.hasBlockEntity()) {
                        continue;
                    }
                    BlockState rock = model != null && config.geology()
                        ? GeologyPalette.rock(geology, climate, random)
                        : config.material().getState(random, pos);
                    level.setBlock(pos, rock, 2);
                    placed = true;
                }
            }
        }
        if (placed && mossChance > 0) {
            for (int dx = -extent; dx <= extent; dx++) {
                for (int dz = -extent; dz <= extent; dz++) {
                    if (random.nextFloat() >= mossChance) {
                        continue;
                    }
                    for (int dy = extent; dy >= -extent; dy--) {
                        pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        BlockState state = level.getBlockState(pos);
                        if (!state.isAir()) {
                            if (dy >= 0 && state.isSolidRender(level, pos)) {
                                level.setBlock(pos, random.nextFloat() < 0.6 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState(), 2);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return placed;
    }
}
