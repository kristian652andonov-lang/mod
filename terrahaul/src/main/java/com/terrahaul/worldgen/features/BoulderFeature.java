package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.surface.Fades;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
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
 * Irregular boulders: the union of two to four overlapping, randomly stretched ellipsoids whose
 * surface is roughened by smooth noise, partly sunk into the ground so they look like they belong
 * to the terrain.
 * <p>
 * In Terrahaul worlds the rock is the local province's single-colour family, as the surface rule
 * exposes it (grey on mountains, sandstone in deserts, red sandstone in hot canyon country, basalt
 * near volcanoes), shaded in broad patches of its stops by smooth noise rather than block by block.
 * Elsewhere each lobe takes one material from the configured provider. Humid places grow patches of
 * moss on the upper surfaces, again shaped by smooth noise.
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
        double mossChance = config.moss();
        TerrainModel model = TerrahaulGeneration.model(level);
        BlockState[] family = null;
        if (model != null && config.geology()) {
            ColumnSample s = model.sample(origin.getX() & ~3, origin.getZ() & ~3);
            int climate = s.baseTemperature > 0.55 && s.humidity < 0.0 ? (s.baseTemperature > 0.75 ? 2 : 1) : 0;
            boolean mountainous = s.mountain > 0.3 || s.alpine > 0.3;
            family = GeologyPalette.family(s.volcano > 0.3 ? Geology.VOLCANIC : s.geology, climate, mountainous);
            mossChance *= Math.max(0.0, Math.min(1.5, 0.6 + s.humidity));
        }
        int lobes = 2 + random.nextInt(3);
        double[][] lobe = new double[lobes][6];
        BlockState[] lobeMaterial = new BlockState[lobes];
        for (int i = 0; i < lobes; i++) {
            double r = radius * (i == 0 ? 1.0 : 0.55 + random.nextDouble() * 0.35);
            lobe[i][0] = i == 0 ? 0 : (random.nextDouble() - 0.5) * radius * 1.2;
            lobe[i][1] = i == 0 ? 0 : (random.nextDouble() - 0.4) * radius * 0.5;
            lobe[i][2] = i == 0 ? 0 : (random.nextDouble() - 0.5) * radius * 1.2;
            lobe[i][3] = r * (0.85 + random.nextDouble() * 0.4);
            lobe[i][4] = r * (0.65 + random.nextDouble() * 0.3);
            lobe[i][5] = r * (0.85 + random.nextDouble() * 0.4);
            // outside Terrahaul worlds one material per lobe, so the provider never speckles
            lobeMaterial[i] = config.material().getState(random, origin);
        }
        double off = random.nextDouble() * 256.0;
        double sink = radius * config.bury();
        int extent = Mth.ceil(radius * 1.9);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dx = -extent; dx <= extent; dx++) {
            for (int dz = -extent; dz <= extent; dz++) {
                for (int dy = -extent; dy <= extent; dy++) {
                    double inside = 0.0;
                    int owner = 0;
                    for (int i = 0; i < lobes; i++) {
                        double[] l = lobe[i];
                        double nx = (dx - l[0]) / l[3];
                        double ny = (dy + sink - l[1]) / l[4];
                        double nz = (dz - l[2]) / l[5];
                        double v = 1.0 - (nx * nx + ny * ny + nz * nz);
                        if (v > inside) {
                            inside = v;
                            owner = i;
                        }
                    }
                    int x = origin.getX() + dx;
                    int y = origin.getY() + dy;
                    int z = origin.getZ() + dz;
                    // coherent dents and bumps instead of randomly missing surface blocks
                    double rough = FormationShapes.ROUGH.noise3(x * 0.45 + off, y * 0.45, z * 0.45);
                    if (inside + 0.16 * rough <= 0.0) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (existing.is(Blocks.BEDROCK) || existing.is(BlockTags.LOGS) || existing.hasBlockEntity()) {
                        continue;
                    }
                    BlockState rock;
                    if (family != null) {
                        double shade = FormationShapes.ROUGH.noise3(x / 4.5 - off, y / 4.5 + 90.0, z / 4.5);
                        rock = family[Fades.stop(family.length, 0.62 + 0.45 * shade, 0.0)];
                    } else {
                        rock = lobeMaterial[owner];
                    }
                    level.setBlock(pos, rock, 2);
                    placed = true;
                }
            }
        }
        if (placed && mossChance > 0) {
            for (int dx = -extent; dx <= extent; dx++) {
                for (int dz = -extent; dz <= extent; dz++) {
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    // moss in patches a few blocks across, more of them the higher the chance
                    double patch = 0.5 + 0.5 * FormationShapes.ROUGH.noise3(x / 3.2 + off, 140.0, z / 3.2);
                    if (patch >= mossChance * 1.3) {
                        continue;
                    }
                    for (int dy = extent; dy >= -extent; dy--) {
                        pos.set(x, origin.getY() + dy, z);
                        BlockState state = level.getBlockState(pos);
                        if (!state.isAir()) {
                            if (dy >= 0 && state.isSolidRender(level, pos) && level.getFluidState(pos.above()).isEmpty()) {
                                level.setBlock(pos, Blocks.MOSS_BLOCK.defaultBlockState(), 2);
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
