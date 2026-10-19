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
 * moss on the upper surfaces, again shaped by smooth noise. Only the blocks connected to the core of
 * the boulder are placed, so the roughness never leaves loose bits or a detached lobe in the air.
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
        // only on firm ground: not on leaves, logs, bamboo or plants the heightmap counts as the top
        if (ground.isAir() || !ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES) || ground.is(BlockTags.LOGS)
            || !ground.isFaceSturdy(level, origin.below(), net.minecraft.core.Direction.UP)) {
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
        int n = 2 * extent + 1;
        // the shape first: which cells of the box are rock, and the most interior one as the core
        byte[] shape = new byte[n * n * n];
        int core = -1;
        double deepest = 0.0;
        for (int dx = -extent; dx <= extent; dx++) {
            for (int dz = -extent; dz <= extent; dz++) {
                for (int dy = -extent; dy <= extent; dy++) {
                    // how far inside the nearest lobe (negative outside all of them, so that the
                    // roughness below only dents and bumps the surface instead of scattering bits
                    // through the whole box)
                    double inside = Double.NEGATIVE_INFINITY;
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
                    // coherent dents and bumps instead of randomly missing surface blocks
                    double rough = FormationShapes.ROUGH.noise3((origin.getX() + dx) * 0.45 + off, (origin.getY() + dy) * 0.45, (origin.getZ() + dz) * 0.45);
                    if (inside + 0.16 * rough <= 0.0) {
                        continue;
                    }
                    int idx = ((dx + extent) * n + (dz + extent)) * n + (dy + extent);
                    shape[idx] = (byte) (owner + 1);
                    if (inside > deepest) {
                        deepest = inside;
                        core = idx;
                    }
                }
            }
        }
        if (core < 0) {
            return false;
        }
        keepConnected(shape, n, core);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dx = -extent; dx <= extent; dx++) {
            for (int dz = -extent; dz <= extent; dz++) {
                for (int dy = -extent; dy <= extent; dy++) {
                    int idx = ((dx + extent) * n + (dz + extent)) * n + (dy + extent);
                    if (shape[idx] >= 0) {
                        // empty, or not connected to the core
                        continue;
                    }
                    int x = origin.getX() + dx;
                    int y = origin.getY() + dy;
                    int z = origin.getZ() + dz;
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
                        rock = lobeMaterial[-shape[idx] - 1];
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
                            // only on the boulder itself, never on a branch or block above it
                            int idx = ((dx + extent) * n + (dz + extent)) * n + (dy + extent);
                            if (dy >= 0 && shape[idx] < 0 && state.isSolidRender(level, pos) && level.getFluidState(pos.above()).isEmpty()) {
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

    /**
     * Keeps the cells of the shape that are face-connected to the core: a kept cell holds
     * {@code -(owner + 1)}, a dropped one stays positive.
     */
    private static void keepConnected(byte[] shape, int n, int core) {
        int[] queue = new int[shape.length];
        int head = 0;
        int tail = 0;
        shape[core] = (byte) -shape[core];
        queue[tail++] = core;
        int[] steps = {n * n, -n * n, n, -n, 1, -1};
        while (head < tail) {
            int idx = queue[head++];
            int y = idx % n;
            int z = (idx / n) % n;
            int x = idx / (n * n);
            for (int k = 0; k < 6; k++) {
                // stay inside the box: no wrapping from one row of the grid to the next
                if ((k == 0 && x == n - 1) || (k == 1 && x == 0) || (k == 2 && z == n - 1) || (k == 3 && z == 0)
                    || (k == 4 && y == n - 1) || (k == 5 && y == 0)) {
                    continue;
                }
                int m = idx + steps[k];
                if (shape[m] > 0) {
                    shape[m] = (byte) -shape[m];
                    queue[tail++] = m;
                }
            }
        }
    }
}
