package com.terrahaul.worldgen.water;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Places water that the noise fill cannot: elevated rivers and lakes above sea level, crater
 * lakes and lava pools, and small mountain streams and forest creeks carved at block resolution.
 * <p>
 * Water below sea level comes from the vanilla fluid system. Above sea level the terrain model
 * provides the water surface of each body; this feature fills the air between the bed and that
 * surface. Before placing anything it checks every water block's horizontal neighbours and lowers
 * the water where it would otherwise spill, so no water is ever placed unsupported; the placed
 * fluid is static (not ticked) until a player or block update disturbs it, exactly like vanilla
 * lakes.
 */
public final class SurfaceWaterFeature extends Feature<NoneFeatureConfiguration> {
    private static final int SIZE = 18;
    private static final int NONE = Integer.MIN_VALUE;

    public SurfaceWaterFeature(Codec<NoneFeatureConfiguration> codec) {
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
        int x0 = chunk.getMinBlockX();
        int z0 = chunk.getMinBlockZ();
        boolean any = false;
        for (int i = -4; i <= 20 && !any; i += 4) {
            for (int j = -4; j <= 20; j += 4) {
                ColumnSample s = model.sample(x0 + i, z0 + j);
                if (s.waterLevel != ColumnSample.NO_WATER || s.lavaLevel != ColumnSample.NO_WATER || s.stream > 0.0) {
                    any = true;
                    break;
                }
            }
        }
        if (!any) {
            return false;
        }

        int[][] ground = new int[SIZE][SIZE];
        int[][] fillTop = new int[SIZE][SIZE];
        boolean[][] lava = new boolean[SIZE][SIZE];
        boolean[][] stream = new boolean[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int x = x0 + i - 1;
                int z = z0 + j - 1;
                int g = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                ground[i][j] = g;
                fillTop[i][j] = NONE;
                double water = ColumnSample.NO_WATER;
                double lavaLevel = ColumnSample.NO_WATER;
                double streamFactor = 0.0;
                int cx = x & ~3;
                int cz = z & ~3;
                for (int a = 0; a <= 4; a += 4) {
                    for (int b = 0; b <= 4; b += 4) {
                        ColumnSample s = model.sample(cx + a, cz + b);
                        water = Math.max(water, s.waterLevel);
                        lavaLevel = Math.max(lavaLevel, s.lavaLevel);
                        streamFactor = Math.max(streamFactor, s.stream);
                    }
                }
                if (lavaLevel != ColumnSample.NO_WATER && g + 1 < lavaLevel) {
                    fillTop[i][j] = (int) lavaLevel;
                    lava[i][j] = true;
                } else if (water != ColumnSample.NO_WATER && g + 1 < water) {
                    fillTop[i][j] = (int) water;
                } else if (streamFactor > 0.0 && g > model.seaLevel()) {
                    double halfWidth = 0.8 + 1.6 * streamFactor;
                    double d = model.streamDistance(x, z);
                    if (d < halfWidth) {
                        // the stream replaces the top block: water at the old surface, bed below
                        stream[i][j] = true;
                        fillTop[i][j] = g + 1;
                        ground[i][j] = g - (d < halfWidth * 0.5 ? 2 : 1);
                    }
                }
            }
        }

        // containment: lower the water wherever a neighbour would let it run off
        for (int pass = 0; pass < 4; pass++) {
            boolean changed = false;
            for (int i = 1; i < SIZE - 1; i++) {
                for (int j = 1; j < SIZE - 1; j++) {
                    int top = fillTop[i][j];
                    if (top == NONE) {
                        continue;
                    }
                    for (int y = ground[i][j] + 1; y < top; y++) {
                        if (leaks(ground, fillTop, i + 1, j, y) || leaks(ground, fillTop, i - 1, j, y)
                            || leaks(ground, fillTop, i, j + 1, y) || leaks(ground, fillTop, i, j - 1, y)) {
                            fillTop[i][j] = y;
                            changed = true;
                            break;
                        }
                    }
                }
            }
            if (!changed) {
                break;
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState lavaState = Blocks.LAVA.defaultBlockState();
        boolean placed = false;
        for (int i = 1; i < SIZE - 1; i++) {
            for (int j = 1; j < SIZE - 1; j++) {
                int top = fillTop[i][j];
                if (top == NONE || top <= ground[i][j] + 1) {
                    continue;
                }
                int x = x0 + i - 1;
                int z = z0 + j - 1;
                if (stream[i][j]) {
                    BlockState bed = model.detail3(0, x / 4.0, 3.0, z / 4.0) > 0.0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
                    level.setBlock(pos.set(x, ground[i][j], z), bed, 2);
                }
                BlockState fluid = lava[i][j] ? lavaState : water;
                for (int y = ground[i][j] + 1; y < top; y++) {
                    pos.set(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || stream[i][j]) {
                        level.setBlock(pos, fluid, 2);
                        placed = true;
                    }
                }
                if (lava[i][j]) {
                    level.setBlock(pos.set(x, ground[i][j], z), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                }
            }
        }
        return placed;
    }

    private static boolean leaks(int[][] ground, int[][] fillTop, int i, int j, int y) {
        if (i < 0 || j < 0 || i >= SIZE || j >= SIZE) {
            return false;
        }
        return ground[i][j] < y && (fillTop[i][j] == NONE || fillTop[i][j] <= y);
    }
}
