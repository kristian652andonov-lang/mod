package com.terrahaul.worldgen.water;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The living surroundings of planned waterfalls and cascades ({@link FallPlanner}). The water,
 * the bowl and the pools are built earlier by {@link SurfaceWaterFeature}; this feature runs in
 * the vegetation step, on the final terrain, and decorates its own chunk only:
 * <ul>
 *   <li>rock kept wet by spray turns to moss and mossy stone in coherent patches, behind the
 *       curtain and on the bowl walls near it (not in cold climates);</li>
 *   <li>glow lichen grows on the walls just above the plunge pool;</li>
 *   <li>vines hang down the walls of the bowl where it is humid;</li>
 *   <li>ferns and moss carpets grow on the shores and on the dams of cascades;</li>
 *   <li>lily pads float on the still lowest pool of a cascade.</li>
 *   <li>ferns and moss carpets also grow on the banks, rocks and boulders around a cascade's
 *       pools.</li>
 * </ul>
 * Neighbouring chunks may not have built their part of a plan yet, so nothing here depends on
 * their blocks: whether rock beyond the chunk's edge is exposed is read from the plan, and
 * lichen and vines only attach to rock of this chunk.
 */
public final class WaterfallFeature extends Feature<NoneFeatureConfiguration> {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    public WaterfallFeature(Codec<NoneFeatureConfiguration> codec) {
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
        List<WaterPlan> plans = new ArrayList<>();
        FallPlanner.plansNear(model, chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ(), plans);
        if (plans.isEmpty()) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        WaterPlan.Shape shape = new WaterPlan.Shape();
        boolean placed = false;
        for (WaterPlan wp : plans) {
            FallPlan p = (FallPlan) wp;
            ColumnSample climate = model.sample((p.lookX + 2) & ~3, (p.lookZ + 2) & ~3);
            boolean cold = climate.temperature < -0.2;
            boolean humid = climate.humidity > 0.05 && !cold;
            for (int x = Math.max(p.minX(), chunk.getMinBlockX()); x <= Math.min(p.maxX(), chunk.getMaxBlockX()); x++) {
                for (int z = Math.max(p.minZ(), chunk.getMinBlockZ()); z <= Math.min(p.maxZ(), chunk.getMaxBlockZ()); z++) {
                    int natural = (int) Math.ceil(model.interpolatedHeight(x, z)) - 1;
                    shape.reset(natural);
                    if (!p.shape(x, z, natural, shape)) {
                        continue;
                    }
                    // the live heightmap: the ground as the water features left it (the
                    // world-generation heightmaps still show the ground before them)
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    placed |= p.type == FallPlan.CASCADE
                        ? decorateCascade(level, p, shape, pos, x, top, z, humid)
                        : decorateFall(level, model, p, shape, pos, x, top, z, humid, cold);
                }
            }
        }
        return placed;
    }

    private static boolean decorateFall(WorldGenLevel level, TerrainModel model, FallPlan p, WaterPlan.Shape shape, BlockPos.MutableBlockPos pos,
                                        int x, int top, int z, boolean humid, boolean cold) {
        boolean placed = false;
        int u = p.u(x, z);
        int v = p.v(x, z);
        // how close the column is to the curtain, which keeps nearby rock wet
        double spray = Math.hypot(Math.max(0, u - 1), Math.max(0, Math.abs(v) - p.halfWidth));
        if (!cold && spray <= 7.0) {
            // wet rock: every rock block of the column with an open side, from the pool up to the lip
            for (int y = p.pool + 1; y <= Math.min(top, p.top); y++) {
                BlockState s = level.getBlockState(pos.set(x, y, z));
                if (!isRock(s) || !openSide(level, model, p, pos, x, y, z)) {
                    continue;
                }
                double n = model.detail3(4, x / 5.0, y / 5.0, z / 5.0) - 0.06 * spray;
                if (n > 0.12) {
                    level.setBlock(pos.set(x, y, z), Blocks.MOSS_BLOCK.defaultBlockState(), 2);
                    placed = true;
                } else if (n > -0.15) {
                    level.setBlock(pos.set(x, y, z), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
                    placed = true;
                }
            }
        }
        if (shape.role == WaterSolver.ROLE_ROCK || u <= 0 && spray <= 3.0) {
            // glow lichen just above the pool, vines hanging from the bowl's rim where it is humid
            for (int y = p.pool + 1; y <= p.pool + 3; y++) {
                placed |= lichen(level, pos, x, y, z, hash(p, x, y * 31 + z) < 0.12);
            }
            if (humid && hash(p, x, z) < 0.18) {
                placed |= vines(level, pos, x, top + 1, z, 2 + (int) (7 * hash(p, z, x)));
            }
        }
        if (humid && (shape.role == WaterSolver.ROLE_SHORE || shape.role == WaterSolver.ROLE_NATURAL && u < 0)) {
            placed |= plants(level, pos, p, x, top, z);
        }
        return placed;
    }

    private static boolean decorateCascade(WorldGenLevel level, FallPlan p, WaterPlan.Shape shape, BlockPos.MutableBlockPos pos,
                                           int x, int top, int z, boolean humid) {
        if (shape.level != WaterSolver.NONE) {
            // lily pads on the lowest, stillest pool
            int surface = shape.level - 1;
            BlockState water = level.getBlockState(pos.set(x, surface, z));
            if (shape.level == p.pool + 1 && water.is(Blocks.WATER) && water.getFluidState().isSource() && hash(p, x, z) < 0.07
                && level.getBlockState(pos.set(x, surface + 1, z)).isAir()) {
                level.setBlock(pos, Blocks.LILY_PAD.defaultBlockState(), 2);
                return true;
            }
            return false;
        }
        if (humid && (shape.role == WaterSolver.ROLE_DAM || shape.role == WaterSolver.ROLE_NATURAL || shape.role == WaterSolver.ROLE_ROCK
            || shape.role == WaterSolver.ROLE_BOULDER)) {
            return plants(level, pos, p, x, top, z);
        }
        return false;
    }

    /** Ferns and moss carpets on a shore or dam top, if it is soil or moss with air above. */
    private static boolean plants(WorldGenLevel level, BlockPos.MutableBlockPos pos, FallPlan p, int x, int top, int z) {
        BlockState ground = level.getBlockState(pos.set(x, top, z));
        if (!level.getBlockState(pos.set(x, top + 1, z)).isAir()) {
            return false;
        }
        double h = hash(p, x * 3, z * 5);
        if (ground.is(Blocks.MOSS_BLOCK) && h < 0.45) {
            level.setBlock(pos.set(x, top + 1, z), Blocks.MOSS_CARPET.defaultBlockState(), 2);
            return true;
        }
        if (!ground.is(BlockTags.DIRT)) {
            return false;
        }
        if (h < 0.1 && level.getBlockState(pos.set(x, top + 2, z)).isAir()) {
            level.setBlock(pos.set(x, top + 1, z), Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), 2);
            level.setBlock(pos.set(x, top + 2, z), Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
            return true;
        }
        if (h < 0.35) {
            level.setBlock(pos.set(x, top + 1, z), Blocks.FERN.defaultBlockState(), 2);
            return true;
        }
        return false;
    }

    /** Glow lichen in an open block against a rock face of this chunk beside it. */
    private static boolean lichen(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, boolean grow) {
        if (!grow || !level.getBlockState(pos.set(x, y, z)).isAir()) {
            return false;
        }
        for (Direction d : SIDES) {
            int wx = x + d.getStepX();
            int wz = z + d.getStepZ();
            if (wx >> 4 != x >> 4 || wz >> 4 != z >> 4) {
                // the neighbouring chunk may still carve that rock away
                continue;
            }
            BlockState wall = level.getBlockState(pos.set(wx, y, wz));
            if (isRock(wall) || wall.is(Blocks.MOSS_BLOCK)) {
                level.setBlock(pos.set(x, y, z), Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d), true), 2);
                return true;
            }
        }
        return false;
    }

    /** A strand of vines hanging down from the top of a wall into the open air beside it. */
    private static boolean vines(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int length) {
        boolean placed = false;
        for (Direction d : SIDES) {
            int ax = x + d.getStepX();
            int az = z + d.getStepZ();
            if (ax >> 4 != x >> 4 || az >> 4 != z >> 4) {
                // the strand would hang in the neighbouring chunk, which decorates its own columns
                continue;
            }
            // the open air next to this column, below its top
            int yy = y - 1;
            if (!level.getBlockState(pos.set(ax, yy, az)).isAir() || !level.getBlockState(pos.set(x, yy, z)).isSolid()) {
                continue;
            }
            Direction toWall = d.getOpposite();
            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(toWall), true);
            for (int i = 0; i < length; i++) {
                if (!level.getBlockState(pos.set(ax, yy - i, az)).isAir() || !level.getBlockState(pos.set(x, yy - i, z)).isSolid()) {
                    break;
                }
                level.setBlock(pos.set(ax, yy - i, az), vine, 2);
                placed = true;
            }
            break;
        }
        return placed;
    }

    /**
     * Whether a block has air or water beside it. Beyond the chunk's edge the plan's shaped ground
     * stands in for the blocks, which that chunk may not have carved yet.
     */
    private static boolean openSide(WorldGenLevel level, TerrainModel model, FallPlan p, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        WaterPlan.Shape shape = null;
        for (Direction d : SIDES) {
            int nx = x + d.getStepX();
            int nz = z + d.getStepZ();
            if (nx >> 4 == x >> 4 && nz >> 4 == z >> 4) {
                BlockState s = level.getBlockState(pos.set(nx, y, nz));
                if (s.isAir() || !s.getFluidState().isEmpty()) {
                    return true;
                }
                continue;
            }
            if (shape == null) {
                shape = new WaterPlan.Shape();
            }
            int natural = (int) Math.ceil(model.interpolatedHeight(nx, nz)) - 1;
            shape.reset(natural);
            int ground = p.shape(nx, nz, natural, shape) ? shape.ground : natural;
            if (y > ground) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRock(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.CALCITE) || s.is(Blocks.SANDSTONE)
            || s.is(Blocks.RED_SANDSTONE) || s.is(BlockTags.TERRACOTTA);
    }

    private static double hash(FallPlan p, int a, int b) {
        return SeedMixer.unit(SeedMixer.hash2(p.seed ^ 0x5EEDL, a, b));
    }
}
