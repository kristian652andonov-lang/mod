package com.terrahaul.worldgen.features.lake;

import com.terrahaul.worldgen.features.marine.MarineBlocks;
import com.terrahaul.worldgen.features.marine.MarineVolume;
import com.terrahaul.worldgen.noise.SeedMixer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

/**
 * Turns the codes of a {@link LakeVolume} and the plant decisions of {@link LakeLifeRaster} into
 * blocks, under rules that keep every lake holding its water:
 * <ul>
 *   <li>only the chunk being decorated is written, and only columns whose real floor and water
 *       surface are where the plan expects them;</li>
 *   <li>the bed's top block only changes from one sediment to another solid block, and loose
 *       sand or gravel only goes onto something that holds it;</li>
 *   <li>every other block only replaces a water source (or, above the surface, air) and only
 *       rests on something solid, bottom up: a column stops at the first block it may not place,
 *       so nothing is left hanging;</li>
 *   <li>plants only go into water sources (or onto the shore) where the block they need is
 *       really there; blocks that can hold water are placed waterlogged under the surface.</li>
 * </ul>
 * Wood takes the species of the biome where the log, boat or pier lies, so it matches the
 * forest around the lake.
 */
public final class LakeBlocks {
    private static final Block[] LOG = {Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG, Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG,
        Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG};
    private static final Block[] STRIPPED = {Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_SPRUCE_LOG,
        Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_CHERRY_LOG};
    private static final Block[] PLANKS = {Blocks.OAK_PLANKS, Blocks.BIRCH_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS,
        Blocks.DARK_OAK_PLANKS, Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS};
    private static final Block[] SLABS = {Blocks.OAK_SLAB, Blocks.BIRCH_SLAB, Blocks.SPRUCE_SLAB, Blocks.JUNGLE_SLAB, Blocks.ACACIA_SLAB,
        Blocks.DARK_OAK_SLAB, Blocks.MANGROVE_SLAB, Blocks.CHERRY_SLAB};
    private static final Block[] STAIRS = {Blocks.OAK_STAIRS, Blocks.BIRCH_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.JUNGLE_STAIRS, Blocks.ACACIA_STAIRS,
        Blocks.DARK_OAK_STAIRS, Blocks.MANGROVE_STAIRS, Blocks.CHERRY_STAIRS};
    private static final Block[] FENCES = {Blocks.OAK_FENCE, Blocks.BIRCH_FENCE, Blocks.SPRUCE_FENCE, Blocks.JUNGLE_FENCE, Blocks.ACACIA_FENCE,
        Blocks.DARK_OAK_FENCE, Blocks.MANGROVE_FENCE, Blocks.CHERRY_FENCE};
    private static final Direction[] FACING = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private LakeBlocks() {
    }

    // =========================================================================================
    // Species
    // =========================================================================================

    /** The tree species of a biome, or {@code guess} where the biome says nothing about it. */
    static int biomeWood(Holder<Biome> biome, int guess) {
        String path = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        if (path.contains("cherry")) {
            return LakePlanner.CHERRY;
        }
        if (path.contains("mangrove")) {
            return LakePlanner.MANGROVE;
        }
        if (path.contains("dark_forest")) {
            return LakePlanner.DARK_OAK;
        }
        if (path.contains("birch")) {
            return LakePlanner.BIRCH;
        }
        if (path.contains("jungle")) {
            return LakePlanner.JUNGLE;
        }
        if (path.contains("savanna")) {
            return LakePlanner.ACACIA;
        }
        if (path.contains("taiga") || path.contains("grove") || path.contains("snowy") || path.contains("windswept") || path.contains("frozen")) {
            return LakePlanner.SPRUCE;
        }
        if (path.contains("forest") || path.contains("swamp") || path.contains("plains") || path.contains("meadow") || path.contains("river")) {
            return guess == LakePlanner.BIRCH && path.contains("forest") ? LakePlanner.BIRCH : LakePlanner.OAK;
        }
        return guess;
    }

    // =========================================================================================
    // States
    // =========================================================================================

    /**
     * The block of a code. {@code wood} is the species of wood codes, {@code sediment} what
     * {@link LakeVolume#FILL} copies, {@code wet} whether the block stands in the lake's water.
     */
    static BlockState state(byte code, int wood, BlockState sediment, int x, int y, int z, boolean wet) {
        long h = SeedMixer.hash3(0x1A4EB10CL, x, y, z);
        BlockState s;
        if (code >= LakeVolume.ROCK && code < LakeVolume.ROCK + 15) {
            s = MarineBlocks.state((byte) (MarineVolume.ROCK + code - LakeVolume.ROCK), x, y, z);
        } else if (code >= LakeVolume.PEBBLES && code < LakeVolume.PEBBLES + 10) {
            s = MarineBlocks.state((byte) (MarineVolume.PEBBLES + code - LakeVolume.PEBBLES), x, y, z);
        } else if (code >= LakeVolume.ROCK_DECOR && code < LakeVolume.ROCK_DECOR + 10) {
            s = MarineBlocks.state((byte) (MarineVolume.ROCK_DECOR + code - LakeVolume.ROCK_DECOR), x, y, z);
        } else if (code >= LakeVolume.STAIRS && code < LakeVolume.STAIRS + 4) {
            s = STAIRS[wood].defaultBlockState().setValue(StairBlock.FACING, FACING[code - LakeVolume.STAIRS]);
        } else if (code >= LakeVolume.CHEST && code < LakeVolume.CHEST + 4) {
            s = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, FACING[code - LakeVolume.CHEST]);
        } else {
            s = switch (code) {
                case LakeVolume.SAND -> Blocks.SAND.defaultBlockState();
                case LakeVolume.GRAVEL -> Blocks.GRAVEL.defaultBlockState();
                case LakeVolume.CLAY -> Blocks.CLAY.defaultBlockState();
                case LakeVolume.MUD -> Blocks.MUD.defaultBlockState();
                case LakeVolume.MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
                case LakeVolume.COBBLE -> Blocks.COBBLESTONE.defaultBlockState();
                case LakeVolume.MOSSY_COBBLE -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                case LakeVolume.GRANITE -> Blocks.GRANITE.defaultBlockState();
                case LakeVolume.DIORITE -> Blocks.DIORITE.defaultBlockState();
                case LakeVolume.FILL -> sediment;
                case LakeVolume.LOG_X -> LOG[wood].defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
                case LakeVolume.LOG_Z -> LOG[wood].defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
                case LakeVolume.LOG_Y -> LOG[wood].defaultBlockState();
                case LakeVolume.STRIPPED_X -> STRIPPED[wood].defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
                case LakeVolume.STRIPPED_Z -> STRIPPED[wood].defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
                case LakeVolume.STRIPPED_Y -> STRIPPED[wood].defaultBlockState();
                case LakeVolume.PLANKS -> PLANKS[wood].defaultBlockState();
                case LakeVolume.SLAB -> SLABS[wood].defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                case LakeVolume.SLAB_TOP -> SLABS[wood].defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
                case LakeVolume.FENCE -> FENCES[wood].defaultBlockState();
                case LakeVolume.STONE_BRICKS -> Blocks.STONE_BRICKS.defaultBlockState();
                case LakeVolume.MOSSY_BRICKS -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                case LakeVolume.CRACKED_BRICKS -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                case LakeVolume.BRICK_SLAB -> (((h >>> 9) & 1) == 0 ? Blocks.STONE_BRICK_SLAB : Blocks.MOSSY_STONE_BRICK_SLAB).defaultBlockState();
                default -> null;
            };
        }
        if (s != null && s.hasProperty(BlockStateProperties.WATERLOGGED)) {
            s = s.setValue(BlockStateProperties.WATERLOGGED, wet);
        }
        return s;
    }

    // =========================================================================================
    // World tests
    // =========================================================================================

    /** A water source block. */
    static boolean waterSource(BlockState state) {
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    /** Loose sediment of a lake bed that may change into another material. */
    static boolean sediment(BlockState state) {
        if (state.hasBlockEntity()) {
            return false;
        }
        return state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.MUD) || state.is(BlockTags.DIRT);
    }

    /** What a shelf or a drift copies from the bed: its sediment, sand where the bed is rock or soil. */
    static BlockState fillOf(BlockState floor) {
        if (floor.is(BlockTags.SAND) || floor.is(Blocks.GRAVEL) || floor.is(Blocks.CLAY) || floor.is(Blocks.MUD)) {
            return floor.getBlock().defaultBlockState();
        }
        return floor.is(BlockTags.DIRT) ? Blocks.DIRT.defaultBlockState() : Blocks.SAND.defaultBlockState();
    }

    private static int floor(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    /**
     * True if the lake stands over the column as planned: the real floor is the planned one, water
     * fills the block above it and the top water block is at the planned surface.
     */
    private static boolean asPlanned(WorldGenLevel level, BlockPos.MutableBlockPos pos, LakeColumn c) {
        int x = pos.getX();
        int z = pos.getZ();
        if (floor(level, x, z) != c.floor) {
            return false;
        }
        return waterSource(level.getBlockState(pos.set(x, c.floor + 1, z))) && waterSource(level.getBlockState(pos.set(x, c.surf, z)))
            && level.getBlockState(pos.set(x, c.surf + 1, z)).getFluidState().isEmpty();
    }

    // =========================================================================================
    // The floor
    // =========================================================================================

    /** Writes the core of a lake floor volume: bed, rocks, logs and discoveries. */
    public static boolean emitFloor(WorldGenLevel level, LakeVolume v) {
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Map<Integer, Integer> woods = new HashMap<>();
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                int lo = v.storedMin(x, z);
                int hi = v.storedMax(x, z);
                if (lo == Integer.MAX_VALUE) {
                    continue;
                }
                LakeColumn c = v.column(x, z);
                if (!c.wet || !asPlanned(level, pos.set(x, 0, z), c)) {
                    continue;
                }
                BlockState sediment = fillOf(level.getBlockState(pos.set(x, c.floor, z)));
                for (int y = lo; y < hi; y++) {
                    byte code = v.get(x, y, z);
                    if (!LakeVolume.isPlanned(code)) {
                        continue;
                    }
                    BlockState old = level.getBlockState(pos.set(x, y, z));
                    BlockState below = level.getBlockState(pos.set(x, y - 1, z));
                    boolean wet = y <= c.surf;
                    BlockState state = state(code, wood(level, v, woods, x, y, z, code), sediment, x, y, z, wet);
                    if (state == null) {
                        continue;
                    }
                    if (y <= c.floor) {
                        // the bed's own blocks: one sediment for another solid block
                        if (sediment(old) && (!(state.getBlock() instanceof FallingBlock) || !FallingBlock.isFree(below))) {
                            level.setBlock(pos.set(x, y, z), state, 2);
                            placed = true;
                        }
                        continue;
                    }
                    boolean free = wet ? waterSource(old) : old.isAir();
                    if (!free || !below.blocksMotion() || state.getBlock() instanceof FallingBlock && FallingBlock.isFree(below)) {
                        break;
                    }
                    level.setBlock(pos.set(x, y, z), state, 2);
                    placed = true;
                    if (code >= LakeVolume.CHEST && code < LakeVolume.CHEST + 4) {
                        loot(level, pos, v, x, y, z);
                    }
                }
            }
        }
        return placed;
    }

    private static int wood(WorldGenLevel level, LakeVolume v, Map<Integer, Integer> woods, int x, int y, int z, byte code) {
        if (!LakeVolume.isWood(code)) {
            return 0;
        }
        int tint = v.tint(x, y, z);
        LakeVolume.Item item = v.item(x, y, z);
        if (item == null) {
            return LakePlanner.OAK;
        }
        // the biome source itself, not the chunks: an item may lie beyond the chunks this one may read
        return woods.computeIfAbsent(tint, t -> biomeWood(level.getUncachedNoiseBiome(QuartPos.fromBlock(item.x()), QuartPos.fromBlock(item.y() + 1),
            QuartPos.fromBlock(item.z())), item.wood()));
    }

    /** Chests in boats and drowned buildings hold what sank with them. */
    private static void loot(WorldGenLevel level, BlockPos pos, LakeVolume v, int x, int y, int z) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainer container)) {
            return;
        }
        long seed = SeedMixer.hash3(v.planner().salt(70), x, y, z);
        // a chest on a boat's planks is its supplies, one in stonework what was left in the house
        String table = v.get(x, y - 1, z) == LakeVolume.PLANKS ? "minecraft:chests/shipwreck_supply" : "minecraft:chests/underwater_ruin_small";
        container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(table)), seed);
    }

    // =========================================================================================
    // Plants
    // =========================================================================================

    /** The kind of the block a plant would stand on, or -1 if nothing roots on it. */
    private static int kind(WorldGenLevel level, BlockPos pos, BlockState s) {
        if (s.is(BlockTags.SAND)) {
            return LakeLifeRaster.SOFT;
        }
        if (s.is(Blocks.GRAVEL)) {
            return LakeLifeRaster.GRAVELLY;
        }
        if (s.is(Blocks.MUD) || s.is(Blocks.CLAY) || s.is(Blocks.MOSS_BLOCK) || s.is(BlockTags.DIRT)) {
            return LakeLifeRaster.MUDDY;
        }
        if (s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS)) {
            return LakeLifeRaster.WOOD;
        }
        if (s.isFaceSturdy(level, pos, Direction.UP) && !s.is(Blocks.MAGMA_BLOCK)) {
            return LakeLifeRaster.HARD;
        }
        return -1;
    }

    /**
     * Grows the plants of one chunk: in each lake column on its real top (the bed, or a rock or
     * log on it), on the water above it, and on the dry shore columns at the water's edge.
     */
    public static boolean grow(WorldGenLevel level, LakePlanner planner, int x0, int z0) {
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        LakeColumn c = new LakeColumn();
        LakeColumn n = new LakeColumn();
        LakeLifeRaster.Plant plant = new LakeLifeRaster.Plant();
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                planner.column(x, z, c);
                if (c.wet) {
                    placed |= underwater(level, planner, pos, c, plant);
                } else {
                    // a dry column at the water's edge, its ground level with the water's top block
                    int type = -1;
                    for (int d = 0; d < 4 && type < 0; d++) {
                        planner.column(x + LakeVolume.DIR_X[d], z + LakeVolume.DIR_Z[d], n);
                        if (n.wet && n.surf == c.floor && waterSource(level.getBlockState(pos.set(n.x, n.surf, n.z)))) {
                            type = n.type;
                        }
                    }
                    if (type >= 0) {
                        placed |= shore(level, planner, pos, c, type, plant);
                    }
                }
            }
        }
        return placed;
    }

    private static boolean underwater(WorldGenLevel level, LakePlanner planner, BlockPos.MutableBlockPos pos, LakeColumn c, LakeLifeRaster.Plant plant) {
        int x = c.x;
        int z = c.z;
        int top = floor(level, x, z);
        if (top < c.floor - 2 || top > c.surf) {
            return false;
        }
        // climb over the rocks and logs the floor feature placed (the worldgen heightmap predates them)
        BlockState above = level.getBlockState(pos.set(x, top + 1, z));
        while (top < c.surf && !waterSource(above) && above.blocksMotion()) {
            top++;
            above = level.getBlockState(pos.set(x, top + 1, z));
        }
        if (top == c.surf) {
            // a trunk lying awash: only moss on its dry top
            BlockState ground = level.getBlockState(pos.set(x, top, z));
            if (!above.isAir() || !ground.is(BlockTags.LOGS)) {
                return false;
            }
            LakeLifeRaster.decide(planner, c, top, LakeLifeRaster.WOOD, plant);
            BlockState carpet = Blocks.MOSS_CARPET.defaultBlockState();
            if (plant.kind == LakeLifeRaster.MOSS_CARPET && carpet.canSurvive(level, pos.set(x, top + 1, z))) {
                level.setBlock(pos, carpet, 2);
                return true;
            }
            return false;
        }
        if (!waterSource(above) || !waterSource(level.getBlockState(pos.set(x, c.surf, z)))) {
            return false;
        }
        BlockState ground = level.getBlockState(pos.set(x, top, z));
        int kind = kind(level, pos, ground);
        if (kind < 0) {
            return false;
        }
        LakeLifeRaster.decide(planner, c, top, kind, plant);
        boolean placed = false;
        Direction facing = FACING[plant.facing];
        switch (plant.kind) {
            case LakeLifeRaster.SEAGRASS -> {
                level.setBlock(pos.set(x, top + 1, z), Blocks.SEAGRASS.defaultBlockState(), 2);
                placed = true;
            }
            case LakeLifeRaster.TALL_SEAGRASS -> {
                if (top + 2 <= c.surf && waterSource(level.getBlockState(pos.set(x, top + 2, z)))) {
                    level.setBlock(pos.set(x, top + 1, z), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.LOWER), 2);
                    level.setBlock(pos.set(x, top + 2, z), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER), 2);
                } else {
                    level.setBlock(pos.set(x, top + 1, z), Blocks.SEAGRASS.defaultBlockState(), 2);
                }
                placed = true;
            }
            case LakeLifeRaster.KELP -> {
                int head = top;
                while (head < plant.head && waterSource(level.getBlockState(pos.set(x, head + 1, z)))) {
                    head++;
                }
                if (head > top) {
                    for (int y = top + 1; y < head; y++) {
                        level.setBlock(pos.set(x, y, z), Blocks.KELP_PLANT.defaultBlockState(), 2);
                    }
                    int age = plant.mature && head == plant.head ? KelpBlock.MAX_AGE : plant.age;
                    level.setBlock(pos.set(x, head, z), Blocks.KELP.defaultBlockState().setValue(KelpBlock.AGE, age), 2);
                    placed = true;
                }
            }
            case LakeLifeRaster.SMALL_DRIPLEAF -> {
                BlockState lower = Blocks.SMALL_DRIPLEAF.defaultBlockState().setValue(SmallDripleafBlock.HALF, DoubleBlockHalf.LOWER)
                    .setValue(SmallDripleafBlock.FACING, facing).setValue(BlockStateProperties.WATERLOGGED, true);
                BlockState up = level.getBlockState(pos.set(x, top + 2, z));
                boolean upWet = waterSource(up);
                if ((upWet || up.isAir()) && lower.canSurvive(level, pos.set(x, top + 1, z))) {
                    level.setBlock(pos.set(x, top + 1, z), lower, 2);
                    level.setBlock(pos.set(x, top + 2, z), lower.setValue(SmallDripleafBlock.HALF, DoubleBlockHalf.UPPER)
                        .setValue(BlockStateProperties.WATERLOGGED, upWet), 2);
                    placed = true;
                }
            }
            case LakeLifeRaster.BIG_DRIPLEAF -> {
                boolean clear = ground.is(BlockTags.BIG_DRIPLEAF_PLACEABLE) && level.getBlockState(pos.set(x, c.surf + 1, z)).isAir();
                for (int y = top + 1; y <= c.surf && clear; y++) {
                    clear = waterSource(level.getBlockState(pos.set(x, y, z)));
                }
                if (clear) {
                    for (int y = top + 1; y <= c.surf; y++) {
                        level.setBlock(pos.set(x, y, z), Blocks.BIG_DRIPLEAF_STEM.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                            .setValue(BlockStateProperties.WATERLOGGED, true), 2);
                    }
                    level.setBlock(pos.set(x, c.surf + 1, z), Blocks.BIG_DRIPLEAF.defaultBlockState().setValue(BigDripleafBlock.FACING, facing)
                        .setValue(BlockStateProperties.WATERLOGGED, false), 2);
                    placed = true;
                }
            }
            default -> {
            }
        }
        if (plant.lily && plant.kind != LakeLifeRaster.BIG_DRIPLEAF && plant.kind != LakeLifeRaster.SMALL_DRIPLEAF) {
            BlockState lily = Blocks.LILY_PAD.defaultBlockState();
            BlockState surface = level.getBlockState(pos.set(x, c.surf, z));
            if (surface.getFluidState().isSourceOfType(Fluids.WATER) && level.getBlockState(pos.set(x, c.surf + 1, z)).isAir() && lily.canSurvive(level, pos)) {
                level.setBlock(pos, lily, 2);
                placed = true;
            }
        }
        return placed;
    }

    private static boolean shore(WorldGenLevel level, LakePlanner planner, BlockPos.MutableBlockPos pos, LakeColumn c, int type, LakeLifeRaster.Plant plant) {
        int x = c.x;
        int z = c.z;
        int top = c.floor;
        if (floor(level, x, z) != top || !level.getBlockState(pos.set(x, top + 1, z)).isAir()) {
            return false;
        }
        BlockState ground = level.getBlockState(pos.set(x, top, z));
        boolean grassy = ground.is(BlockTags.DIRT);
        if (!grassy && !ground.is(BlockTags.SAND)) {
            return false;
        }
        LakeLifeRaster.shore(planner, c, type, grassy, plant);
        if (plant.kind == LakeLifeRaster.REEDS) {
            BlockState cane = Blocks.SUGAR_CANE.defaultBlockState();
            boolean placed = false;
            for (int y = top + 1; y <= top + plant.head; y++) {
                if (!level.getBlockState(pos.set(x, y, z)).isAir() || !cane.canSurvive(level, pos)) {
                    break;
                }
                level.setBlock(pos, cane, 2);
                placed = true;
            }
            return placed;
        }
        if (plant.kind == LakeLifeRaster.MOSS_CARPET) {
            BlockState carpet = Blocks.MOSS_CARPET.defaultBlockState();
            if (carpet.canSurvive(level, pos.set(x, top + 1, z))) {
                level.setBlock(pos, carpet, 2);
                return true;
            }
        }
        return false;
    }
}
