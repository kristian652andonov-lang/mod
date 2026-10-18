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
 * surface. Water surfaces step down along rivers, so before placing anything every column is
 * checked against its neighbours:
 * <ul>
 *   <li>where water would stand next to lower water, the lower column becomes a small weir of
 *       mossy rock (a rapid);</li>
 *   <li>where a bank is up to two blocks lower than the water, the bank is raised;</li>
 *   <li>only larger drops lower the water.</li>
 * </ul>
 * All decisions use the same data on both sides of a chunk border, so neighbouring chunks agree
 * and no water is ever placed unsupported. The placed fluid is static (not ticked) until a
 * player or block update disturbs it, exactly like vanilla lakes.
 */
public final class SurfaceWaterFeature extends Feature<NoneFeatureConfiguration> {
    /** Extra columns sampled around the chunk on each side. */
    private static final int MARGIN = 12;
    private static final int SIZE = 16 + 2 * MARGIN;
    private static final int NONE = Integer.MIN_VALUE;
    /** Highest step a rock weir may hold between two water surfaces. */
    private static final int MAX_WEIR = 4;
    /** Highest a bank may be raised to contain water. */
    private static final int MAX_BANK = 2;
    /** Ground lower than the model surface by more than this is a hole, not a lake bed. */
    private static final double MAX_DEPTH_ERROR = 6.0;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

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

        // raw water surface per column; MARGIN extra columns around the chunk let every chunk make
        // exactly the same decisions for the columns along its borders as its neighbours do
        int[][] ground = new int[SIZE][SIZE];
        int[][] raw = new int[SIZE][SIZE];
        boolean[][] lava = new boolean[SIZE][SIZE];
        boolean[][] stream = new boolean[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int x = x0 + i - MARGIN;
                int z = z0 + j - MARGIN;
                int g = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                ground[i][j] = g;
                raw[i][j] = NONE;
                // the nearest model corner decides whether the column belongs to a water body; the
                // water is only placed where the ground is about as deep as the model expects, so
                // holes carved by caves, ravines or overhangs never fill with floating water
                ColumnSample s = model.sample((x + 2) & ~3, (z + 2) & ~3);
                double water = s.waterLevel;
                double lavaLevel = s.lavaLevel;
                double streamFactor = s.stream;
                double expected = model.interpolatedHeight(x, z);
                if (g < expected - MAX_DEPTH_ERROR) {
                    water = ColumnSample.NO_WATER;
                    lavaLevel = ColumnSample.NO_WATER;
                }
                if (lavaLevel != ColumnSample.NO_WATER && g + 1 < lavaLevel) {
                    raw[i][j] = (int) lavaLevel;
                    lava[i][j] = true;
                } else if (water != ColumnSample.NO_WATER && g + 1 < water) {
                    raw[i][j] = (int) water;
                } else if (streamFactor > 0.0 && g > model.seaLevel()) {
                    double halfWidth = 0.8 + 1.6 * streamFactor;
                    double d = model.streamDistance(x, z);
                    if (d < halfWidth) {
                        // the stream replaces the top block: water at the old surface, bed below
                        stream[i][j] = true;
                        raw[i][j] = g + 1;
                        ground[i][j] = g - (d < halfWidth * 0.5 ? 2 : 1);
                    }
                }
            }
        }

        // stage 2: lower water where a drop is too large to bridge with a weir or a bank
        int[][] top = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(raw[i], 0, top[i], 0, SIZE);
        }
        // a change travels one column per pass; with at most MARGIN passes every column of this
        // chunk depends only on columns inside the sampled area, so neighbouring chunks, which
        // sample the same columns, reach exactly the same result along their shared border
        for (int pass = 0; pass < MARGIN; pass++) {
            boolean changed = false;
            for (int i = 1; i < SIZE - 1; i++) {
                for (int j = 1; j < SIZE - 1; j++) {
                    if (top[i][j] == NONE) {
                        continue;
                    }
                    int limited = top[i][j];
                    for (int[] d : DIRS) {
                        int ni = i + d[0];
                        int nj = j + d[1];
                        int limit = top[ni][nj] == NONE || top[ni][nj] <= ground[ni][nj] + 1
                            ? ground[ni][nj] + 1 + MAX_BANK + 1    // dry: a bank can rise MAX_BANK blocks
                            : top[ni][nj] + MAX_WEIR;               // water: a weir can hold MAX_WEIR blocks
                        limited = Math.min(limited, limit);
                    }
                    if (limited <= ground[i][j] + 1) {
                        limited = NONE;
                    }
                    if (limited != top[i][j]) {
                        top[i][j] = limited;
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }

        // stage 3: weirs and banks. A water column lower than a neighbour becomes a rock weir up to
        // the neighbour's surface; a dry column lower than a neighbour's surface becomes a bank.
        int[][] solidTop = new int[SIZE][SIZE];
        for (int i = MARGIN - 1; i <= SIZE - MARGIN; i++) {
            for (int j = MARGIN - 1; j <= SIZE - MARGIN; j++) {
                solidTop[i][j] = NONE;
                int need = NONE;
                for (int[] d : DIRS) {
                    int t = top[i + d[0]][j + d[1]];
                    if (t != NONE && t - 1 > need) {
                        need = t - 1;
                    }
                }
                if (need == NONE) {
                    continue;
                }
                boolean wet = top[i][j] != NONE && top[i][j] > ground[i][j] + 1;
                if (wet ? need >= top[i][j] : need > ground[i][j]) {
                    solidTop[i][j] = need;
                }
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState lavaState = Blocks.LAVA.defaultBlockState();
        boolean placed = false;
        for (int i = MARGIN; i < SIZE - MARGIN; i++) {
            for (int j = MARGIN; j < SIZE - MARGIN; j++) {
                int x = x0 + i - MARGIN;
                int z = z0 + j - MARGIN;
                if (solidTop[i][j] != NONE) {
                    placed |= raise(level, model, pos, x, z, ground[i][j], solidTop[i][j], top[i][j] != NONE, lava[i][j]);
                    continue;
                }
                int t = top[i][j];
                if (t == NONE || t <= ground[i][j] + 1) {
                    continue;
                }
                if (stream[i][j]) {
                    BlockState bed = model.detail3(0, x / 4.0, 3.0, z / 4.0) > 0.0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
                    level.setBlock(pos.set(x, ground[i][j], z), bed, 2);
                }
                BlockState fluid = lava[i][j] ? lavaState : water;
                for (int y = ground[i][j] + 1; y < t; y++) {
                    pos.set(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.getFluidState().isSource() || stream[i][j]) {
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

    /**
     * Raises a column to {@code solidTop}: a weir of grey, partly mossy rock where water would
     * otherwise stand next to lower water (a step in a river becomes a small rapid), or the
     * column's own soil where a bank is lower than the neighbouring water surface.
     */
    private static boolean raise(WorldGenLevel level, TerrainModel model, BlockPos.MutableBlockPos pos, int x, int z, int ground, int solidTop,
                                 boolean weir, boolean lava) {
        BlockState surface = level.getBlockState(pos.set(x, ground, z));
        boolean rock = weir || lava || !surface.is(BlockTags.DIRT) && !surface.is(BlockTags.SAND);
        for (int y = ground + 1; y <= solidTop; y++) {
            pos.set(x, y, z);
            BlockState existing = level.getBlockState(pos);
            if (!existing.isAir() && !existing.is(BlockTags.REPLACEABLE) && existing.getFluidState().isEmpty()) {
                continue;
            }
            BlockState block;
            if (rock) {
                double n = model.detail3(1, x / 3.0, y / 3.0, z / 3.0);
                block = lava ? Blocks.BASALT.defaultBlockState()
                    : n > 0.25 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n > -0.3 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            } else if (surface.is(BlockTags.SAND)) {
                block = surface;
            } else {
                block = y == solidTop ? (surface.is(Blocks.GRASS_BLOCK) ? surface : Blocks.GRASS_BLOCK.defaultBlockState()) : Blocks.DIRT.defaultBlockState();
            }
            level.setBlock(pos, block, 2);
        }
        if (!rock && surface.is(Blocks.GRASS_BLOCK)) {
            // the old grass top is buried under the bank now
            level.setBlock(pos.set(x, ground, z), Blocks.DIRT.defaultBlockState(), 2);
        }
        return true;
    }

}
