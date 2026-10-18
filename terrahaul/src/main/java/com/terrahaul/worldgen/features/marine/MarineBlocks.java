package com.terrahaul.worldgen.features.marine;

import com.terrahaul.blocks.CountedDecorBlock;
import com.terrahaul.blocks.GroundDecorBlock;
import com.terrahaul.blocks.RockBlock;
import com.terrahaul.registry.TerrahaulBlocks;
import com.terrahaul.worldgen.noise.SeedMixer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;

/**
 * Turns the codes of a {@link MarineVolume} into blocks and writes them to the world under the
 * marine features' rules:
 * <ul>
 *   <li>only the core of a volume (the chunk being decorated) is written;</li>
 *   <li>solid planned blocks only replace water (or water plants, whose other halves and kelp
 *       stems are tidied up), except where a plan explicitly digs into the natural floor;</li>
 *   <li>plants, fans, pickles, seagrass and kelp only go into water sources, waterlogged, and only
 *       where the block they need below (or behind) is really there, so they always survive;</li>
 *   <li>bedrock and blocks with block entities are never touched, and a column whose real floor
 *       differs from the planned one by more than two blocks (3D cliff detail) is left alone.</li>
 * </ul>
 */
public final class MarineBlocks {
    private static final Block[] LIVE = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK};
    private static final Block[] DEAD = {Blocks.DEAD_TUBE_CORAL_BLOCK, Blocks.DEAD_BRAIN_CORAL_BLOCK, Blocks.DEAD_BUBBLE_CORAL_BLOCK, Blocks.DEAD_FIRE_CORAL_BLOCK,
        Blocks.DEAD_HORN_CORAL_BLOCK};
    private static final Block[] PLANT = {Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL, Blocks.FIRE_CORAL, Blocks.HORN_CORAL};
    private static final Block[] FAN = {Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN};
    private static final Block[] DEAD_PLANT = {Blocks.DEAD_TUBE_CORAL, Blocks.DEAD_BRAIN_CORAL, Blocks.DEAD_BUBBLE_CORAL, Blocks.DEAD_FIRE_CORAL, Blocks.DEAD_HORN_CORAL};
    private static final Block[] DEAD_FAN = {Blocks.DEAD_TUBE_CORAL_FAN, Blocks.DEAD_BRAIN_CORAL_FAN, Blocks.DEAD_BUBBLE_CORAL_FAN, Blocks.DEAD_FIRE_CORAL_FAN,
        Blocks.DEAD_HORN_CORAL_FAN};
    private static final Block[] WALL_FAN = {Blocks.TUBE_CORAL_WALL_FAN, Blocks.BRAIN_CORAL_WALL_FAN, Blocks.BUBBLE_CORAL_WALL_FAN, Blocks.FIRE_CORAL_WALL_FAN,
        Blocks.HORN_CORAL_WALL_FAN};
    private static final Direction[] FACING = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** Rock families, top colour first: grey, dark, pale, sandy, volcanic. */
    private static final BlockState[][] FAMILY = {
        {Blocks.ANDESITE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.TUFF.defaultBlockState()},
        {Blocks.TUFF.defaultBlockState(), Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState()},
        {Blocks.CALCITE.defaultBlockState(), Blocks.DIORITE.defaultBlockState(), Blocks.STONE.defaultBlockState()},
        {Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()},
        {Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y),
            Blocks.BLACKSTONE.defaultBlockState()},
    };

    private MarineBlocks() {
    }

    /** The block of a code at a position (facings, counts and variants come from a position hash). */
    public static BlockState state(byte code, int x, int y, int z) {
        long h = SeedMixer.hash3(0x5EAB10C5L, x, y, z);
        if (code >= MarineVolume.LIVE && code < MarineVolume.DEAD) {
            return LIVE[code - MarineVolume.LIVE].defaultBlockState();
        }
        if (code >= MarineVolume.DEAD && code < MarineVolume.PLANT) {
            return DEAD[code - MarineVolume.DEAD].defaultBlockState();
        }
        if (code >= MarineVolume.PLANT && code < MarineVolume.FAN) {
            return PLANT[code - MarineVolume.PLANT].defaultBlockState();
        }
        if (code >= MarineVolume.FAN && code < MarineVolume.DEAD_PLANT) {
            return FAN[code - MarineVolume.FAN].defaultBlockState();
        }
        if (code >= MarineVolume.DEAD_PLANT && code < MarineVolume.DEAD_FAN) {
            return DEAD_PLANT[code - MarineVolume.DEAD_PLANT].defaultBlockState();
        }
        if (code >= MarineVolume.DEAD_FAN && code < MarineVolume.WALL_FAN) {
            return DEAD_FAN[code - MarineVolume.DEAD_FAN].defaultBlockState();
        }
        if (code >= MarineVolume.WALL_FAN && code < MarineVolume.PICKLE) {
            int i = code - MarineVolume.WALL_FAN;
            return WALL_FAN[i / 4].defaultBlockState().setValue(BaseCoralWallFanBlock.FACING, FACING[i % 4]);
        }
        if (code >= MarineVolume.PICKLE && code < MarineVolume.SEAGRASS) {
            return Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, code - MarineVolume.PICKLE + 1)
                .setValue(SeaPickleBlock.WATERLOGGED, true);
        }
        if (code >= MarineVolume.ROCK && code < MarineVolume.ROCK + 15) {
            int i = code - MarineVolume.ROCK;
            return FAMILY[i / 3][i % 3];
        }
        if (code >= MarineVolume.PEBBLES && code < MarineVolume.PEBBLES + 10) {
            String stone = TerrahaulBlocks.STONES.get(code - MarineVolume.PEBBLES);
            return TerrahaulBlocks.PEBBLES.get(stone).get().defaultBlockState().setValue(CountedDecorBlock.COUNT, 1 + (int) ((h >>> 7) & 3))
                .setValue(GroundDecorBlock.FACING, FACING[(int) ((h >>> 11) & 3)]).setValue(GroundDecorBlock.WATERLOGGED, true);
        }
        if (code >= MarineVolume.ROCK_DECOR && code < MarineVolume.ROCK_DECOR + 10) {
            String stone = TerrahaulBlocks.STONES.get(code - MarineVolume.ROCK_DECOR);
            return TerrahaulBlocks.ROCKS.get(stone).get().defaultBlockState().setValue(RockBlock.VARIANT, (int) ((h >>> 7) & 1))
                .setValue(GroundDecorBlock.FACING, FACING[(int) ((h >>> 11) & 3)]).setValue(GroundDecorBlock.WATERLOGGED, true);
        }
        if (code >= MarineVolume.GLOW_LICHEN && code < MarineVolume.GLOW_LICHEN + 5) {
            int k = code - MarineVolume.GLOW_LICHEN;
            Direction face = k == 0 ? Direction.UP : FACING[k - 1];
            return Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(face), true);
        }
        return switch (code) {
            case MarineVolume.WATER -> Blocks.WATER.defaultBlockState();
            case MarineVolume.AIR -> Blocks.CAVE_AIR.defaultBlockState();
            case MarineVolume.SAND -> Blocks.SAND.defaultBlockState();
            case MarineVolume.GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case MarineVolume.CALCITE -> Blocks.CALCITE.defaultBlockState();
            case MarineVolume.COBBLE -> Blocks.COBBLESTONE.defaultBlockState();
            case MarineVolume.MOSSY_COBBLE -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case MarineVolume.GRANITE -> Blocks.GRANITE.defaultBlockState();
            case MarineVolume.DIORITE -> Blocks.DIORITE.defaultBlockState();
            case MarineVolume.SEAGRASS -> Blocks.SEAGRASS.defaultBlockState();
            case MarineVolume.TALL_LOWER -> Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.LOWER);
            case MarineVolume.TALL_UPPER -> Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER);
            case MarineVolume.KELP_PLANT -> Blocks.KELP_PLANT.defaultBlockState();
            case MarineVolume.KELP_TOP -> Blocks.KELP.defaultBlockState().setValue(KelpBlock.AGE, KelpBlock.MAX_AGE);
            case MarineVolume.KELP_GROWING -> Blocks.KELP.defaultBlockState().setValue(KelpBlock.AGE, 20 + (int) ((h >>> 7) % 5));
            default -> null;
        };
    }

    // =========================================================================================
    // World tests
    // =========================================================================================

    /** A water source block. */
    public static boolean waterSource(BlockState state) {
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    /** Water, or a plant living in water that may be replaced like water. */
    public static boolean waterLike(BlockState state) {
        return waterSource(state) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS) || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
            || state.getBlock() instanceof GroundDecorBlock && state.getValue(GroundDecorBlock.WATERLOGGED);
    }

    /** Natural ground that the marine features may dig into or sink rocks into. */
    public static boolean natural(BlockState state) {
        if (state.is(Blocks.BEDROCK) || state.hasBlockEntity()) {
            return false;
        }
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA)
            || state.is(Tags.Blocks.ORES) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.MUD) || state.is(Blocks.SANDSTONE)
            || state.is(Blocks.SMOOTH_SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.SMOOTH_RED_SANDSTONE) || state.is(Blocks.CALCITE)
            || state.is(Blocks.BASALT) || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.BLACKSTONE) || state.is(Blocks.COBBLESTONE)
            || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.PACKED_ICE);
    }

    /** Loose sediment a boulder may sink into or a gravel patch may replace. */
    private static boolean sediment(BlockState state) {
        return state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.MUD) || state.is(BlockTags.DIRT);
    }

    /**
     * Replaces a water-like block, tidying up what the replaced plant leaves behind: the other half
     * of tall seagrass, and the kelp above (removed) and below (capped with a fully grown head).
     */
    private static void replace(WorldGenLevel level, BlockPos.MutableBlockPos pos, BlockState old, BlockState state) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        level.setBlock(pos, state, 2);
        if (old.is(Blocks.TALL_SEAGRASS)) {
            boolean lower = old.getValue(TallSeagrassBlock.HALF) == DoubleBlockHalf.LOWER;
            BlockPos other = new BlockPos(x, lower ? y + 1 : y - 1, z);
            if (level.getBlockState(other).is(Blocks.TALL_SEAGRASS)) {
                level.setBlock(other, lower ? Blocks.WATER.defaultBlockState() : Blocks.SEAGRASS.defaultBlockState(), 2);
            }
        } else if (old.is(Blocks.KELP) || old.is(Blocks.KELP_PLANT)) {
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y + 1, z);
            while (level.getBlockState(p).is(Blocks.KELP) || level.getBlockState(p).is(Blocks.KELP_PLANT)) {
                level.setBlock(p, Blocks.WATER.defaultBlockState(), 2);
                p.move(Direction.UP);
            }
            p.set(x, y - 1, z);
            if (level.getBlockState(p).is(Blocks.KELP_PLANT)) {
                level.setBlock(p, Blocks.KELP.defaultBlockState().setValue(KelpBlock.AGE, KelpBlock.MAX_AGE), 2);
            }
        }
        pos.set(x, y, z);
    }

    /** True if a plant code may stand at {@code pos}: its support below or behind is really there. */
    private static boolean supported(WorldGenLevel level, BlockPos pos, byte code) {
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        if (code == MarineVolume.TALL_UPPER) {
            return under.is(Blocks.TALL_SEAGRASS);
        }
        if (code >= MarineVolume.KELP_PLANT && code <= MarineVolume.KELP_GROWING) {
            return under.is(Blocks.KELP_PLANT) || under.isFaceSturdy(level, below, Direction.UP) && !under.is(Blocks.MAGMA_BLOCK);
        }
        if (code >= MarineVolume.WALL_FAN && code < MarineVolume.PICKLE) {
            Direction facing = FACING[(code - MarineVolume.WALL_FAN) % 4];
            BlockPos behind = pos.relative(facing.getOpposite());
            return level.getBlockState(behind).isFaceSturdy(level, behind, facing);
        }
        if (code >= MarineVolume.PICKLE && code < MarineVolume.SEAGRASS) {
            return !under.getCollisionShape(level, below).getFaceShape(Direction.UP).isEmpty() || under.isFaceSturdy(level, below, Direction.UP);
        }
        if (code >= MarineVolume.GLOW_LICHEN && code < MarineVolume.GLOW_LICHEN + 5) {
            int k = code - MarineVolume.GLOW_LICHEN;
            Direction face = k == 0 ? Direction.UP : FACING[k - 1];
            BlockPos support = pos.relative(face);
            return level.getBlockState(support).isFaceSturdy(level, support, face.getOpposite());
        }
        return under.isFaceSturdy(level, below, Direction.UP) && !under.is(Blocks.MAGMA_BLOCK);
    }

    private static boolean isPlant(byte code) {
        return code >= MarineVolume.PLANT && code < MarineVolume.ROCK || code >= MarineVolume.PEBBLES;
    }

    /** Places a plant code into a water source (or, for glow lichen, air) if its support is there. */
    private static boolean plant(WorldGenLevel level, BlockPos.MutableBlockPos pos, byte code) {
        BlockState here = level.getBlockState(pos);
        boolean lichen = code >= MarineVolume.GLOW_LICHEN && code < MarineVolume.GLOW_LICHEN + 5;
        boolean wet = waterSource(here);
        if (!wet && !(lichen && here.isAir()) || !supported(level, pos, code)) {
            return false;
        }
        BlockState state = state(code, pos.getX(), pos.getY(), pos.getZ());
        if (lichen) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, wet);
        }
        level.setBlock(pos, state, 2);
        return true;
    }

    private static int floor(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    // =========================================================================================
    // Emitters
    // =========================================================================================

    /**
     * Writes a reef volume's core. Columns where the world holds something else than water above
     * the floor (a shipwreck, ruins, a boulder) are left alone, so structures keep their shape.
     */
    public static boolean emitReef(WorldGenLevel level, MarineVolume v) {
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                int lo = v.storedMin(x, z);
                int hi = v.storedMax(x, z);
                if (lo == Integer.MAX_VALUE) {
                    continue;
                }
                MarineColumn c = v.column(x, z);
                int wg = floor(level, x, z);
                int top = lo;
                for (int y = lo; y < hi; y++) {
                    if (MarineVolume.isPlanned(v.get(x, y, z))) {
                        top = y;
                    }
                }
                if (Math.abs(wg - c.floor) > 2 || !clearWater(level, pos, x, z, wg + 1, top + 1)) {
                    continue;
                }
                // a floor slightly lower than planned gets filled up under the reef
                if (wg < c.floor && MarineVolume.isSolid(v.get(x, c.floor + 1, z)) && MarineVolume.isPlanned(v.get(x, c.floor + 1, z))) {
                    for (int y = wg + 1; y <= c.floor; y++) {
                        BlockState old = level.getBlockState(pos.set(x, y, z));
                        if (waterLike(old)) {
                            replace(level, pos, old, Blocks.CALCITE.defaultBlockState());
                        }
                    }
                }
                placed |= solids(level, v, pos, x, z, lo, hi, wg, false);
                placed |= plants(level, v, pos, x, z, lo, hi);
            }
        }
        return placed;
    }

    /**
     * True if a planned coral block will have water next to it: a neighbour that the plan leaves
     * as water (or fills with a waterlogged plant) and that is still water in the world.
     */
    private static boolean wet(WorldGenLevel level, MarineVolume v, int x, int y, int z) {
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values()) {
            int nx = x + d.getStepX();
            int ny = y + d.getStepY();
            int nz = z + d.getStepZ();
            byte planned = v.get(nx, ny, nz);
            boolean water = MarineVolume.isWaterLike(planned) || planned >= MarineVolume.PLANT && planned < MarineVolume.ROCK;
            if (water && ny <= v.planner().surf && waterLike(level.getBlockState(n.set(nx, ny, nz)))) {
                return true;
            }
        }
        return false;
    }

    /** True if every block from {@code y0} to {@code y1} of the column is water or a water plant. */
    private static boolean clearWater(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int y0, int y1) {
        for (int y = y0; y < y1; y++) {
            if (!waterLike(level.getBlockState(pos.set(x, y, z)))) {
                return false;
            }
        }
        return true;
    }

    /** Writes the solid codes of a column bottom-up; {@code sink} lets rock replace sediment and natural ground. */
    private static boolean solids(WorldGenLevel level, MarineVolume v, BlockPos.MutableBlockPos pos, int x, int z, int lo, int hi, int wg, boolean sink) {
        boolean placed = false;
        int surf = v.planner().surf;
        for (int y = lo; y < hi; y++) {
            byte code = v.get(x, y, z);
            if (!MarineVolume.isPlanned(code) || isPlant(code) || code == MarineVolume.WATER || code == MarineVolume.AIR
                || sink && code == MarineVolume.GRAVEL && y == v.column(x, z).floor) {
                continue;
            }
            if (MarineVolume.isLive(code) && !wet(level, v, x, y, z)) {
                // the water the plan left next to this coral is taken (by a boulder placed
                // earlier): live coral without water would die, so it is dead coral from the start
                code = (byte) (code - MarineVolume.LIVE + MarineVolume.DEAD);
            }
            BlockState old = level.getBlockState(pos.set(x, y, z));
            BlockState state = state(code, x, y, z);
            if (state == null) {
                continue;
            }
            if (waterLike(old)) {
                replace(level, pos, old, state);
                placed = true;
            } else if (y > surf && old.isAir() && sink) {
                level.setBlock(pos, state, 2);
                placed = true;
            } else if (sink && y <= wg && (sediment(old) || natural(old) && y < wg)) {
                level.setBlock(pos, state, 2);
                placed = true;
            }
        }
        return placed;
    }

    private static boolean plants(WorldGenLevel level, MarineVolume v, BlockPos.MutableBlockPos pos, int x, int z, int lo, int hi) {
        boolean placed = false;
        for (int y = lo; y < hi; y++) {
            byte code = v.get(x, y, z);
            if (isPlant(code)) {
                placed |= plant(level, pos.set(x, y, z), code);
            }
        }
        return placed;
    }

    /**
     * Writes a rock volume's core: ledges and boulders (which sink into the floor), gravel patches
     * on loose floors, and cobbles, pebbles and rocks lying on the floor.
     */
    public static boolean emitRocks(WorldGenLevel level, MarineVolume v) {
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                int lo = v.storedMin(x, z);
                int hi = v.storedMax(x, z);
                if (lo == Integer.MAX_VALUE) {
                    continue;
                }
                MarineColumn c = v.column(x, z);
                int wg = floor(level, x, z);
                if (Math.abs(wg - c.floor) > 2) {
                    continue;
                }
                // gravel patches replace loose sediment that rests on something solid
                if (v.get(x, c.floor, z) == MarineVolume.GRAVEL && c.floor == wg) {
                    BlockState old = level.getBlockState(pos.set(x, wg, z));
                    BlockState under = level.getBlockState(pos.set(x, wg - 1, z));
                    if (sediment(old) && !old.is(Blocks.GRAVEL) && under.isFaceSturdy(level, pos, Direction.UP)) {
                        level.setBlock(pos.set(x, wg, z), Blocks.GRAVEL.defaultBlockState(), 2);
                        placed = true;
                    }
                }
                placed |= solids(level, v, pos, x, z, lo, hi, wg, true);
                placed |= plants(level, v, pos, x, z, lo, hi);
            }
        }
        return placed;
    }

    /**
     * Writes a grotto volume's core. A block is only dug out if it is natural ground and none of
     * its neighbours outside the grotto is air (a land cave or ravine the water would pour into)
     * or, for the air dome, water; and a column is only dug where the real rock above it is as
     * thick as planned.
     */
    public static boolean emitGrottos(WorldGenLevel level, MarineVolume v) {
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        int sl = v.planner().seaLevel();
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                int lo = v.storedMin(x, z);
                int hi = v.storedMax(x, z);
                if (lo == Integer.MAX_VALUE) {
                    continue;
                }
                int top = Integer.MIN_VALUE;
                for (int y = lo; y < hi; y++) {
                    if (GrottoPlan.dug(v.get(x, y, z))) {
                        top = y;
                    }
                }
                if (top == Integer.MIN_VALUE || !roofIntact(level, v, pos, x, z, top)) {
                    continue;
                }
                int surf = v.planner().surf;
                for (int y = lo; y <= top; y++) {
                    if (!GrottoPlan.dug(v.get(x, y, z))) {
                        continue;
                    }
                    boolean air = y > surf;
                    BlockState old = level.getBlockState(pos.set(x, y, z));
                    if (!natural(old) || !sealed(level, v, n, x, y, z, air)) {
                        continue;
                    }
                    level.setBlock(pos, air ? Blocks.CAVE_AIR.defaultBlockState() : Blocks.WATER.defaultBlockState(), 2);
                    placed = true;
                }
                // sand and gravel floors, the beach, coral and algae on the rim
                for (int y = lo; y < hi; y++) {
                    byte code = v.get(x, y, z);
                    if (code == MarineVolume.SAND || code == MarineVolume.GRAVEL || code == MarineVolume.MOSSY_COBBLE || MarineVolume.isLive(code)) {
                        BlockState old = level.getBlockState(pos.set(x, y, z));
                        if (natural(old) && (code == MarineVolume.SAND || code == MarineVolume.GRAVEL || touchesWater(level, n, x, y, z))) {
                            level.setBlock(pos, state(code, x, y, z), 2);
                        }
                    }
                }
                placed |= plants(level, v, pos, x, z, lo, hi);
            }
        }
        return placed;
    }

    private static boolean touchesWater(WorldGenLevel level, BlockPos.MutableBlockPos n, int x, int y, int z) {
        for (Direction d : Direction.values()) {
            if (level.getFluidState(n.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ())).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /**
     * No neighbour outside the planned grotto lets fluid move: a water block may not touch air
     * below sea level (a land cave the sea would pour into), an air block may not touch any fluid.
     */
    private static boolean sealed(WorldGenLevel level, MarineVolume v, BlockPos.MutableBlockPos n, int x, int y, int z, boolean air) {
        for (Direction d : Direction.values()) {
            int nx = x + d.getStepX();
            int ny = y + d.getStepY();
            int nz = z + d.getStepZ();
            BlockState s = level.getBlockState(n.set(nx, ny, nz));
            if (!air) {
                // below sea level the grotto only ever holds water, so air there is a land cave
                // (even inside the planned shape, where a column was left undug)
                if (s.isAir() && ny <= v.planner().surf || s.getFluidState().is(FluidTags.LAVA)) {
                    return false;
                }
            } else if (!GrottoPlan.dug(v.get(nx, ny, nz)) && !s.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The rock above the highest dug block of a column must really be there, at least four blocks
     * of it as planned from the terrain model (the 3D detail of cliffs can thin it). Columns of the
     * porch, under open water, need none.
     */
    private static boolean roofIntact(WorldGenLevel level, MarineVolume v, BlockPos.MutableBlockPos pos, int x, int z, int top) {
        MarineColumn c = v.column(x, z);
        if (c.floor <= top) {
            return true;
        }
        int need = Math.min(4, c.floor - top);
        for (int y = top + 1; y <= top + need; y++) {
            if (GrottoPlan.dug(v.get(x, y, z))) {
                continue;
            }
            BlockState s = level.getBlockState(pos.set(x, y, z));
            if (s.isAir() || !s.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
