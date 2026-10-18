package com.terrahaul.worldgen.water;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

/**
 * Places the surface water the noise fill cannot: elevated rivers and lakes, crater lakes and lava
 * pools, mountain streams and forest creeks, waterfalls with their plunge pools and forest
 * cascades, and sea-level pools the water table left dry.
 * <p>
 * What goes where is decided by the {@link WaterSolver} from the terrain model alone, with a
 * margin around the chunk, so neighbouring chunks make identical decisions along their border:
 * rivers descend in rapids of rock sills and short chutes across the channel, streams in step
 * pools, and every source block is held by ground or water on all sides. This feature only turns
 * the solution into blocks, in its own chunk:
 * <ul>
 *   <li>shaped columns (dug streams, plunge pools, the bowl and channel of a waterfall) are
 *       carved down to their ground, natural blocks only, and filled up to it;</li>
 *   <li>banks and sills are built up, the water is placed, chutes get falling water;</li>
 *   <li>since real terrain differs from the model (caves, overhangs, 3D detail), every block that
 *       must hold water but is open, here or below the water, is sealed with rock.</li>
 * </ul>
 * The placed fluid is static (not ticked) until a player or block update disturbs it, like
 * vanilla lakes.
 */
public final class SurfaceWaterFeature extends Feature<NoneFeatureConfiguration> {
    private static final ThreadLocal<WaterSolver> SOLVER = ThreadLocal.withInitial(WaterSolver::new);
    private static final int M = WaterSolver.MARGIN;

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
        List<WaterPlan> plans = new ArrayList<>();
        FallPlanner.plansNear(model, x0 - M, z0 - M, x0 + 15 + M, z0 + 15 + M, plans);
        boolean water = !plans.isEmpty();
        boolean low = false;
        int sl = model.seaLevel();
        for (int i = -8; i <= 24 && !water; i += 4) {
            for (int j = -8; j <= 24; j += 4) {
                ColumnSample s = model.sample(x0 + i, z0 + j);
                if (s.bodyInside && (s.bodyLevel > sl || s.lavaLevel != ColumnSample.NO_WATER) || s.stream > 0.0) {
                    water = true;
                    break;
                }
                low |= s.height < sl - 0.5;
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (!water) {
            return low && fillPockets(level, model, pos, x0, z0);
        }
        WaterSolver ws = SOLVER.get();
        ws.solve(model, chunk.x, chunk.z, plans);
        boolean placed = false;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                placed |= column(level, model, ws, pos, i + M, j + M, x0 + i, z0 + j);
            }
        }
        return placed;
    }

    private boolean column(WorldGenLevel level, TerrainModel model, WaterSolver ws, BlockPos.MutableBlockPos pos, int i, int j, int x, int z) {
        int k = WaterSolver.index(i, j);
        int natural = ws.natural[k];
        int ground = ws.ground[k];
        int lv = ws.level[k];
        byte kind = ws.kind[k];
        byte step = ws.step[k];
        boolean lava = kind == WaterSolver.LAVA;
        int sl = model.seaLevel();
        boolean placed = false;
        BlockState top = level.getBlockState(pos.set(x, natural, z));
        WaterPalette palette = WaterPalette.at(model, x, z, top);

        if (kind == WaterSolver.SEA && step == WaterSolver.FLAT && !ws.shaped[k]) {
            // the water table's water: only pockets it left dry are filled
            return fillPocket(level, pos, x, z, ground, sl);
        }

        // ---- shaped ground: carved down to the plan or channel, filled up to it -------------
        if (ws.shaped[k]) {
            int carveTop = ws.kind[k] == WaterSolver.PLAN || ws.kind[k] == WaterSolver.DRY || ws.kind[k] == WaterSolver.SEA
                ? Math.max(natural, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1) : natural;
            for (int y = carveTop; y > ground; y--) {
                BlockState s = level.getBlockState(pos.set(x, y, z));
                if (!s.isAir() && WaterPalette.natural(s)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    placed = true;
                }
            }
            for (int y = Math.min(natural, ground); y <= ground; y++) {
                BlockState s = level.getBlockState(pos.set(x, y, z));
                if (!s.blocksMotion() || y == ground && lv != WaterSolver.NONE && WaterPalette.soil(s)) {
                    level.setBlock(pos, palette.shaped(ws.role[k], x, y, z, y == ground, lv != WaterSolver.NONE), 2);
                    placed = true;
                }
            }
        }

        // ---- sills, banks and boulders --------------------------------------------------------
        int solidTop = ws.solidTop[k];
        int from = step == WaterSolver.SILL ? ground + 1 : Math.min(ground, natural) + 1;
        if (solidTop > ground) {
            for (int y = from; y <= solidTop; y++) {
                BlockState s = level.getBlockState(pos.set(x, y, z));
                if (!s.blocksMotion() || step == WaterSolver.SILL && !s.getFluidState().isEmpty()) {
                    BlockState block = step == WaterSolver.SILL || lava ? palette.sill(x, y, z, lava)
                        : palette.bank(y == solidTop, ws.role[k] == WaterSolver.ROLE_DAM);
                    level.setBlock(pos, block, 2);
                    placed = true;
                }
            }
            if (step != WaterSolver.SILL && palette.isGrass(top) && natural < solidTop) {
                level.setBlock(pos.set(x, natural, z), Blocks.DIRT.defaultBlockState(), 2);
            }
        }
        if (ws.boulder[k]) {
            BlockState s = level.getBlockState(pos.set(x, solidTop + 1, z));
            if (!s.blocksMotion()) {
                level.setBlock(pos, palette.boulder(x, z), 2);
                placed = true;
            }
        }

        // ---- water ----------------------------------------------------------------------------
        if (lv != WaterSolver.NONE && step != WaterSolver.SILL) {
            BlockState fluid = lava ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState();
            BlockState bed = level.getBlockState(pos.set(x, ground, z));
            if (!bed.blocksMotion()) {
                level.setBlock(pos, lava ? Blocks.MAGMA_BLOCK.defaultBlockState() : palette.bed(x, ground, z), 2);
            } else if (lava) {
                level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
            } else if (palette.isGrass(bed) || bed.is(Blocks.SNOW_BLOCK)) {
                // grass and snow do not stay under water
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
            }
            for (int y = ground + 1; y < lv; y++) {
                BlockState s = level.getBlockState(pos.set(x, y, z));
                if (!s.blocksMotion() && !s.getFluidState().isSource() || ws.shaped[k] && WaterPalette.natural(s)) {
                    level.setBlock(pos, fluid, 2);
                    placed = true;
                }
            }
            if (step == WaterSolver.CHUTE) {
                int up = ws.upper[k];
                for (int y = lv; y < up; y++) {
                    BlockState s = level.getBlockState(pos.set(x, y, z));
                    if (!s.blocksMotion() || ws.shaped[k] && WaterPalette.natural(s)) {
                        level.setBlock(pos, chute(lava, y == up - 1), 2);
                        placed = true;
                    }
                }
            }
        }

        // ---- seal everything that must hold water but is open --------------------------------
        if (lv == WaterSolver.NONE || step == WaterSolver.SILL) {
            int need = ws.holdLevel(i, j);
            if (need != WaterSolver.NONE) {
                int holdFrom = ws.holdFrom(i, j);
                for (int y = holdFrom; y <= need; y++) {
                    BlockState s = level.getBlockState(pos.set(x, y, z));
                    if (!s.blocksMotion() && s.getFluidState().isEmpty()) {
                        level.setBlock(pos, y > solidTop ? palette.bank(y == need, false) : palette.seal(lava), 2);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }

    /** Falling water down a step, with the flowing block that spills over the upper surface on top. */
    private static BlockState chute(boolean lava, boolean top) {
        if (lava) {
            return top ? Fluids.FLOWING_LAVA.getFlowing(7, false).createLegacyBlock() : Fluids.FLOWING_LAVA.getFlowing(8, true).createLegacyBlock();
        }
        return top ? Fluids.FLOWING_WATER.getFlowing(7, false).createLegacyBlock() : Fluids.FLOWING_WATER.getFlowing(8, true).createLegacyBlock();
    }

    /**
     * Sea-level water the water table left out: shallow pools in wetlands and floodplains whose
     * ground lies below sea level between model corners. Filled from sea level down through open
     * air; the block below is sealed if it is open too.
     */
    private static boolean fillPocket(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int sl) {
        BlockState s = level.getBlockState(pos.set(x, sl - 1, z));
        if (!s.isAir()) {
            return false;
        }
        int y = sl - 1;
        while (y > ground && level.getBlockState(pos.set(x, y, z)).isAir()) {
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
            y--;
        }
        if (y == ground && !level.getBlockState(pos.set(x, y, z)).blocksMotion()) {
            level.setBlock(pos, Blocks.MUD.defaultBlockState(), 2);
        }
        return true;
    }

    /** Pocket filling for chunks without elevated water, from the model ground directly. */
    private static boolean fillPockets(WorldGenLevel level, TerrainModel model, BlockPos.MutableBlockPos pos, int x0, int z0) {
        int sl = model.seaLevel();
        boolean placed = false;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int ground = (int) Math.ceil(model.interpolatedHeight(x0 + i, z0 + j)) - 1;
                if (ground <= sl - 2) {
                    placed |= fillPocket(level, pos, x0 + i, z0 + j, ground, sl);
                }
            }
        }
        return placed;
    }
}
