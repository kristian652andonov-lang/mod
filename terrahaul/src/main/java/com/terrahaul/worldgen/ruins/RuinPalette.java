package com.terrahaul.worldgen.ruins;

import com.terrahaul.blocks.GroundDecorBlock;
import com.terrahaul.blocks.RockBlock;
import com.terrahaul.registry.TerrahaulBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Turns plan cells into block states: a {@link RuinPart} role, combined with the site's
 * {@link RuinMaterials}, gives the block; the cell's facing, axis, half, open and weathering bits
 * give its properties. Weathering only ever swaps a block for its own mossy, cracked or (under the
 * sea) prismarine-encrusted form, so walls change colour in coherent patches, never per block.
 */
final class RuinPalette {
    private RuinPalette() {
    }

    private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** Wood sets, indexed like {@link RuinMaterials#OAK} ... {@link RuinMaterials#BAMBOO}. */
    private static final Block[] PLANKS = {Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.DARK_OAK_PLANKS,
        Blocks.ACACIA_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS, Blocks.BAMBOO_PLANKS};
    private static final Block[] LOGS = {Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.DARK_OAK_LOG, Blocks.ACACIA_LOG,
        Blocks.JUNGLE_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG, Blocks.BAMBOO_BLOCK};
    private static final Block[] STRIPPED = {Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_BIRCH_LOG,
        Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_MANGROVE_LOG,
        Blocks.STRIPPED_CHERRY_LOG, Blocks.STRIPPED_BAMBOO_BLOCK};
    private static final Block[] STAIRS = {Blocks.OAK_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.BIRCH_STAIRS, Blocks.DARK_OAK_STAIRS,
        Blocks.ACACIA_STAIRS, Blocks.JUNGLE_STAIRS, Blocks.MANGROVE_STAIRS, Blocks.CHERRY_STAIRS, Blocks.BAMBOO_STAIRS};
    private static final Block[] SLABS = {Blocks.OAK_SLAB, Blocks.SPRUCE_SLAB, Blocks.BIRCH_SLAB, Blocks.DARK_OAK_SLAB, Blocks.ACACIA_SLAB,
        Blocks.JUNGLE_SLAB, Blocks.MANGROVE_SLAB, Blocks.CHERRY_SLAB, Blocks.BAMBOO_SLAB};
    private static final Block[] FENCES = {Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.BIRCH_FENCE, Blocks.DARK_OAK_FENCE,
        Blocks.ACACIA_FENCE, Blocks.JUNGLE_FENCE, Blocks.MANGROVE_FENCE, Blocks.CHERRY_FENCE, Blocks.BAMBOO_FENCE};
    private static final Block[] DOORS = {Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.BIRCH_DOOR, Blocks.DARK_OAK_DOOR, Blocks.ACACIA_DOOR,
        Blocks.JUNGLE_DOOR, Blocks.MANGROVE_DOOR, Blocks.CHERRY_DOOR, Blocks.BAMBOO_DOOR};
    private static final Block[] TRAPDOORS = {Blocks.OAK_TRAPDOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.BIRCH_TRAPDOOR, Blocks.DARK_OAK_TRAPDOOR,
        Blocks.ACACIA_TRAPDOOR, Blocks.JUNGLE_TRAPDOOR, Blocks.MANGROVE_TRAPDOOR, Blocks.CHERRY_TRAPDOOR, Blocks.BAMBOO_TRAPDOOR};
    private static final Block[] SIGNS = {Blocks.OAK_SIGN, Blocks.SPRUCE_SIGN, Blocks.BIRCH_SIGN, Blocks.DARK_OAK_SIGN, Blocks.ACACIA_SIGN,
        Blocks.JUNGLE_SIGN, Blocks.MANGROVE_SIGN, Blocks.CHERRY_SIGN, Blocks.BAMBOO_SIGN};
    private static final Block[] HANGING_SIGNS = {Blocks.OAK_WALL_HANGING_SIGN, Blocks.SPRUCE_WALL_HANGING_SIGN, Blocks.BIRCH_WALL_HANGING_SIGN,
        Blocks.DARK_OAK_WALL_HANGING_SIGN, Blocks.ACACIA_WALL_HANGING_SIGN, Blocks.JUNGLE_WALL_HANGING_SIGN, Blocks.MANGROVE_WALL_HANGING_SIGN,
        Blocks.CHERRY_WALL_HANGING_SIGN, Blocks.BAMBOO_WALL_HANGING_SIGN};
    private static final Block[] LEAVES = {Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.DARK_OAK_LEAVES,
        Blocks.ACACIA_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.MANGROVE_LEAVES, Blocks.CHERRY_LEAVES, Blocks.JUNGLE_LEAVES,
        Blocks.FLOWERING_AZALEA_LEAVES};
    private static final String[] LITTER = {"oak_leaf_litter", "spruce_needle_litter", "birch_leaf_litter", "dark_oak_leaf_litter",
        "acacia_leaf_litter", "jungle_leaf_litter", "mangrove_leaf_litter", "azalea_leaf_litter", "jungle_leaf_litter", "azalea_leaf_litter"};

    /**
     * Stone families: main block, mossy, cracked, stairs, mossy stairs, slab, mossy slab, wall,
     * mossy wall, quoin, mossy quoin, cracked quoin, column pillar, capital, column base, trim,
     * trim stairs, trim slab, paving. Families without a mossy or cracked form repeat the plain one.
     */
    private static final Block[][] STONE = {
        // COBBLE
        {Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS,
            Blocks.COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE_WALL, Blocks.STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS,
            Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.COBBLESTONE},
        // ASHLAR
        {Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS,
            Blocks.STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL, Blocks.MOSSY_STONE_BRICK_WALL, Blocks.STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICKS,
            Blocks.SMOOTH_STONE, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_BRICKS},
        // GRANITE
        {Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS,
            Blocks.COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE_WALL, Blocks.POLISHED_GRANITE,
            Blocks.POLISHED_GRANITE, Blocks.POLISHED_GRANITE, Blocks.POLISHED_GRANITE, Blocks.POLISHED_GRANITE, Blocks.POLISHED_GRANITE,
            Blocks.POLISHED_GRANITE, Blocks.POLISHED_GRANITE_STAIRS, Blocks.POLISHED_GRANITE_SLAB, Blocks.COBBLESTONE},
        // SLATE
        {Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE_STAIRS,
            Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.COBBLED_DEEPSLATE_WALL,
            Blocks.COBBLED_DEEPSLATE_WALL, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS,
            Blocks.POLISHED_DEEPSLATE, Blocks.CHISELED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE,
            Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.POLISHED_DEEPSLATE_SLAB, Blocks.DEEPSLATE_TILES},
        // LIMESTONE
        {Blocks.CALCITE, Blocks.CALCITE, Blocks.CALCITE, Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_DIORITE_STAIRS,
            Blocks.POLISHED_DIORITE_SLAB, Blocks.POLISHED_DIORITE_SLAB, Blocks.DIORITE_WALL, Blocks.DIORITE_WALL, Blocks.POLISHED_DIORITE,
            Blocks.POLISHED_DIORITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_DIORITE,
            Blocks.POLISHED_DIORITE, Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_DIORITE_SLAB, Blocks.CALCITE},
        // SANDSTONE
        {Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB,
            Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_WALL, Blocks.SANDSTONE_WALL, Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE,
            Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS,
            Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE},
        // RED_SANDSTONE
        {Blocks.RED_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE, Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_STAIRS,
            Blocks.RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_WALL, Blocks.RED_SANDSTONE_WALL, Blocks.CUT_RED_SANDSTONE,
            Blocks.CUT_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE,
            Blocks.SMOOTH_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.SMOOTH_RED_SANDSTONE_SLAB,
            Blocks.SMOOTH_RED_SANDSTONE},
        // MUD
        {Blocks.MUD_BRICKS, Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB,
            Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_WALL, Blocks.MUD_BRICK_WALL, Blocks.MUD_BRICKS, Blocks.MUD_BRICKS, Blocks.PACKED_MUD,
            Blocks.MUD_BRICKS, Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB,
            Blocks.PACKED_MUD},
        // MOSSY
        {Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS,
            Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE_WALL,
            Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
            Blocks.CHISELED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_STAIRS,
            Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.MOSSY_COBBLESTONE},
        // TUFF
        {Blocks.TUFF_BRICKS, Blocks.TUFF_BRICKS, Blocks.TUFF, Blocks.TUFF_BRICK_STAIRS, Blocks.TUFF_BRICK_STAIRS, Blocks.TUFF_BRICK_SLAB,
            Blocks.TUFF_BRICK_SLAB, Blocks.TUFF_BRICK_WALL, Blocks.TUFF_BRICK_WALL, Blocks.POLISHED_TUFF, Blocks.POLISHED_TUFF, Blocks.POLISHED_TUFF,
            Blocks.POLISHED_TUFF, Blocks.CHISELED_TUFF_BRICKS, Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF, Blocks.POLISHED_TUFF_STAIRS,
            Blocks.POLISHED_TUFF_SLAB, Blocks.TUFF},
        // SEA_STONE
        {Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS,
            Blocks.STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL, Blocks.MOSSY_STONE_BRICK_WALL, Blocks.STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICKS,
            Blocks.SMOOTH_STONE, Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_BRICKS},
        // SEA_TUFF
        {Blocks.TUFF_BRICKS, Blocks.TUFF_BRICKS, Blocks.TUFF_BRICKS, Blocks.TUFF_BRICK_STAIRS, Blocks.TUFF_BRICK_STAIRS, Blocks.TUFF_BRICK_SLAB,
            Blocks.TUFF_BRICK_SLAB, Blocks.TUFF_BRICK_WALL, Blocks.TUFF_BRICK_WALL, Blocks.TUFF_BRICKS, Blocks.TUFF_BRICKS, Blocks.TUFF_BRICKS,
            Blocks.POLISHED_TUFF, Blocks.CHISELED_TUFF_BRICKS, Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF, Blocks.POLISHED_TUFF_STAIRS,
            Blocks.POLISHED_TUFF_SLAB, Blocks.TUFF_BRICKS},
        // MARBLE
        {Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB,
            Blocks.QUARTZ_SLAB, Blocks.DIORITE_WALL, Blocks.DIORITE_WALL, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ,
            Blocks.QUARTZ_PILLAR, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ_STAIRS,
            Blocks.SMOOTH_QUARTZ_SLAB, Blocks.SMOOTH_QUARTZ},
        // SEA_SANDSTONE
        {Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE_SLAB,
            Blocks.CUT_SANDSTONE_SLAB, Blocks.SANDSTONE_WALL, Blocks.SANDSTONE_WALL, Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE,
            Blocks.SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS,
            Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE}};
    private static final int MAIN = 0;
    private static final int MOSSY = 1;
    private static final int CRACKED = 2;
    private static final int STAIR = 3;
    private static final int MOSSY_STAIR = 4;
    private static final int SLAB = 5;
    private static final int MOSSY_SLAB = 6;
    private static final int WALL = 7;
    private static final int MOSSY_WALL = 8;
    private static final int QUOIN = 9;
    private static final int MOSSY_QUOIN = 10;
    private static final int CRACKED_QUOIN = 11;
    private static final int PILLAR = 12;
    private static final int CAPITAL = 13;
    private static final int BASE = 14;
    private static final int TRIM = 15;
    private static final int TRIM_STAIR = 16;
    private static final int TRIM_SLAB = 17;
    private static final int PAVING = 18;

    private static final String[] ROCK_STONE = {"stone", "stone", "granite", "deepslate", "diorite", "sandstone", "red_sandstone", "stone",
        "mossy", "tuff", "stone", "tuff", "diorite", "sandstone"};

    /** Whether a part's shape depends on its neighbours and should be fixed when the chunk completes. */
    static boolean shaped(RuinPart p) {
        return switch (p) {
            case FENCE, MASONRY_WALL, GLASS_PANE, IRON_BARS, MASONRY_STAIR, PLANK_STAIR, ROOF_STAIR, TRIM_STAIR -> true;
            default -> false;
        };
    }

    /**
     * The block state of a cell.
     *
     * @param sediment the floor material of the column, used for {@link RuinPart#SEDIMENT}
     * @return the state, or {@code null} for parts that place nothing
     */
    static BlockState state(int cell, RuinMaterials m, BlockState sediment) {
        RuinPart part = RuinCell.part(cell);
        Direction facing = DIRS[RuinCell.dir(cell)];
        int w = RuinCell.weathering(cell);
        boolean top = RuinCell.top(cell);
        boolean flag = RuinCell.flag(cell);
        int aux = RuinCell.aux(cell);
        Block[] stone = STONE[m.stone];
        int wood = m.wood;
        boolean charred = aux == 1;
        return switch (part) {
            case NONE -> null;
            case AIR -> Blocks.AIR.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
            case FOUNDATION, MASONRY, PAVING -> {
                if (w == RuinCell.ENCRUSTED) {
                    yield Blocks.PRISMARINE.defaultBlockState();
                }
                int slot = part == RuinPart.PAVING ? PAVING : MAIN;
                if (slot == MAIN || stone[PAVING] == stone[MAIN]) {
                    slot = w == RuinCell.MOSSY ? MOSSY : w == RuinCell.CRACKED ? CRACKED : MAIN;
                }
                yield stone[slot].defaultBlockState();
            }
            case QUOIN, CHIMNEY -> w == RuinCell.ENCRUSTED ? Blocks.PRISMARINE_BRICKS.defaultBlockState()
                : stone[w == RuinCell.MOSSY ? MOSSY_QUOIN : w == RuinCell.CRACKED ? CRACKED_QUOIN : QUOIN].defaultBlockState();
            case MASONRY_STAIR -> stairs(w == RuinCell.ENCRUSTED ? Blocks.PRISMARINE_STAIRS : stone[w == RuinCell.MOSSY ? MOSSY_STAIR : STAIR], facing, top);
            case MASONRY_SLAB -> slab(w == RuinCell.ENCRUSTED ? Blocks.PRISMARINE_SLAB : stone[w == RuinCell.MOSSY ? MOSSY_SLAB : SLAB], top);
            case MASONRY_WALL -> (w == RuinCell.ENCRUSTED ? Blocks.PRISMARINE_WALL : stone[w == RuinCell.MOSSY ? MOSSY_WALL : WALL]).defaultBlockState();
            case PILLAR -> axis(w == RuinCell.ENCRUSTED ? Blocks.PRISMARINE : stone[PILLAR], RuinCell.axis(cell));
            case CAPITAL -> (w == RuinCell.ENCRUSTED ? Blocks.DARK_PRISMARINE : stone[CAPITAL]).defaultBlockState();
            case COLUMN_BASE -> (w == RuinCell.ENCRUSTED ? Blocks.PRISMARINE_BRICKS : stone[BASE]).defaultBlockState();
            case TRIM -> axis(w == RuinCell.ENCRUSTED ? Blocks.PRISMARINE_BRICKS : stone[TRIM], RuinCell.axis(cell));
            case TRIM_STAIR -> stairs(w == RuinCell.ENCRUSTED ? Blocks.PRISMARINE_BRICK_STAIRS : stone[TRIM_STAIR], facing, top);
            case TRIM_SLAB -> slab(w == RuinCell.ENCRUSTED ? Blocks.PRISMARINE_BRICK_SLAB : stone[TRIM_SLAB], top);
            case SEA_LANTERN -> Blocks.SEA_LANTERN.defaultBlockState();
            case PLANKS -> (charred ? Blocks.DARK_OAK_PLANKS : PLANKS[wood]).defaultBlockState();
            case FLOOR -> PLANKS[wood == RuinMaterials.BAMBOO ? RuinMaterials.JUNGLE : wood].defaultBlockState();
            case PLANK_STAIR -> stairs(STAIRS[wood], facing, top);
            case PLANK_SLAB -> slab(SLABS[wood], top);
            case LOG -> axis(charred ? Blocks.DARK_OAK_LOG : LOGS[wood], RuinCell.axis(cell));
            case STRIPPED_LOG -> axis(charred ? Blocks.STRIPPED_DARK_OAK_LOG : STRIPPED[wood], RuinCell.axis(cell));
            case FENCE -> FENCES[wood].defaultBlockState();
            case ROOF -> roofBlock(m, stone).defaultBlockState();
            case ROOF_STAIR -> stairs(roofStairs(m, stone), facing, top);
            case ROOF_SLAB -> slab(roofSlab(m, stone), top);
            case THATCH, HAY -> axis(Blocks.HAY_BLOCK, RuinCell.axis(cell));
            case PACKED_MUD -> Blocks.PACKED_MUD.defaultBlockState();
            case SOIL -> switch (aux) {
                case 1 -> Blocks.COARSE_DIRT.defaultBlockState();
                case 2 -> Blocks.PODZOL.defaultBlockState();
                case 3 -> Blocks.DIRT.defaultBlockState();
                case 4 -> Blocks.MOSS_BLOCK.defaultBlockState();
                default -> Blocks.GRASS_BLOCK.defaultBlockState();
            };
            case SEDIMENT -> sediment != null ? sediment : Blocks.SAND.defaultBlockState();
            case DIRT_PATH -> Blocks.DIRT_PATH.defaultBlockState();
            case COARSE_DIRT -> Blocks.COARSE_DIRT.defaultBlockState();
            case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case MOSS_BLOCK -> Blocks.MOSS_BLOCK.defaultBlockState();
            case SUSPICIOUS_GRAVEL -> Blocks.SUSPICIOUS_GRAVEL.defaultBlockState();
            case SUSPICIOUS_SAND -> Blocks.SUSPICIOUS_SAND.defaultBlockState();
            case DOOR -> DOORS[wood].defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, top ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
                .setValue(BlockStateProperties.OPEN, flag)
                .setValue(BlockStateProperties.DOOR_HINGE, (aux & 1) == 1 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
            case TRAPDOOR -> TRAPDOORS[wood].defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM).setValue(BlockStateProperties.OPEN, flag);
            case GLASS_PANE -> Blocks.GLASS_PANE.defaultBlockState();
            case IRON_BARS -> Blocks.IRON_BARS.defaultBlockState();
            case LADDER -> Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            case LEAVES -> LEAVES[m.leaves].defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
            case VINE -> {
                BlockState v = Blocks.VINE.defaultBlockState();
                for (int d = 0; d < 4; d++) {
                    if ((aux & 1 << d) != 0) {
                        v = v.setValue(VineBlock.getPropertyForFace(DIRS[d]), true);
                    }
                }
                yield (aux & 16) != 0 ? v.setValue(VineBlock.UP, true) : v;
            }
            case GLOW_LICHEN -> {
                BlockState v = Blocks.GLOW_LICHEN.defaultBlockState();
                for (int d = 0; d < 4; d++) {
                    if ((aux & 1 << d) != 0) {
                        v = v.setValue(MultifaceBlock.getFaceProperty(DIRS[d]), true);
                    }
                }
                yield v;
            }
            case COBWEB -> Blocks.COBWEB.defaultBlockState();
            case MOSS_CARPET -> Blocks.MOSS_CARPET.defaultBlockState();
            case SHORT_GRASS -> Blocks.SHORT_GRASS.defaultBlockState();
            case FERN -> Blocks.FERN.defaultBlockState();
            case TALL_GRASS -> Blocks.TALL_GRASS.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, top ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
            case FLOWER -> switch (aux % 6) {
                case 1 -> Blocks.DANDELION.defaultBlockState();
                case 2 -> Blocks.OXEYE_DAISY.defaultBlockState();
                case 3 -> Blocks.AZURE_BLUET.defaultBlockState();
                case 4 -> Blocks.CORNFLOWER.defaultBlockState();
                case 5 -> Blocks.ALLIUM.defaultBlockState();
                default -> Blocks.POPPY.defaultBlockState();
            };
            case DEAD_BUSH -> Blocks.DEAD_BUSH.defaultBlockState();
            case MUSHROOM -> (aux == 1 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState();
            case SWEET_BERRY_BUSH -> Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1 + (aux % 3));
            case LITTER -> {
                var block = TerrahaulBlocks.LITTER.get(LITTER[m.leaves]);
                yield block == null ? Blocks.AIR.defaultBlockState() : block.get().defaultBlockState().setValue(GroundDecorBlock.FACING, facing);
            }
            case ROCK -> {
                String name = aux == 1 ? "mossy" : ROCK_STONE[m.stone];
                var block = TerrahaulBlocks.ROCKS.get(name);
                yield block == null ? Blocks.COBBLESTONE_SLAB.defaultBlockState()
                    : block.get().defaultBlockState().setValue(GroundDecorBlock.FACING, facing).setValue(RockBlock.VARIANT, flag ? 1 : 0);
            }
            case CAMPFIRE -> Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.LIT, flag).setValue(BlockStateProperties.SIGNAL_FIRE, false);
            case CRAFTING_TABLE -> Blocks.CRAFTING_TABLE.defaultBlockState();
            case CHEST -> Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            case BARREL -> Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, top ? Direction.UP : facing);
            case FURNACE -> Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            case BOOKSHELF -> Blocks.BOOKSHELF.defaultBlockState();
            case CAULDRON -> Blocks.CAULDRON.defaultBlockState();
            case FLOWER_POT -> switch (aux) {
                case 1 -> Blocks.POTTED_DEAD_BUSH.defaultBlockState();
                case 2 -> Blocks.POTTED_FERN.defaultBlockState();
                default -> Blocks.FLOWER_POT.defaultBlockState();
            };
            case COMPOSTER -> Blocks.COMPOSTER.defaultBlockState();
            case ANVIL -> (aux == 1 ? Blocks.CHIPPED_ANVIL : Blocks.DAMAGED_ANVIL).defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            case BED -> bed(aux).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BedBlock.PART, top ? BedPart.HEAD : BedPart.FOOT);
            case CARPET -> (aux == 1 ? Blocks.BROWN_CARPET : aux == 2 ? Blocks.RED_CARPET : Blocks.WHITE_CARPET).defaultBlockState();
            case DECORATED_POT -> Blocks.DECORATED_POT.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(DecoratedPotBlock.CRACKED, flag);
            case LANTERN -> (aux == 1 ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(BlockStateProperties.HANGING, flag);
            case CHAIN -> Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
            case STANDING_SIGN -> SIGNS[wood].defaultBlockState().setValue(StandingSignBlock.ROTATION, flag ? aux & 15 : signRotation(facing));
            case WALL_HANGING_SIGN -> HANGING_SIGNS[wood].defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            case SEAGRASS -> Blocks.SEAGRASS.defaultBlockState();
            case TALL_SEAGRASS -> Blocks.TALL_SEAGRASS.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, top ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
            case KELP -> Blocks.KELP.defaultBlockState().setValue(BlockStateProperties.AGE_25, 25);
            case KELP_PLANT -> Blocks.KELP_PLANT.defaultBlockState();
            case SEA_PICKLE -> Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, Math.max(1, Math.min(4, aux)));
            case CORAL_FAN -> coralFan(aux).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            case WET_SPONGE -> Blocks.WET_SPONGE.defaultBlockState();
        };
    }

    private static int signRotation(Direction facing) {
        return switch (facing) {
            case SOUTH -> 0;
            case WEST -> 4;
            case NORTH -> 8;
            default -> 12;
        };
    }

    private static Block bed(int aux) {
        return aux == 1 ? Blocks.BROWN_BED : aux == 2 ? Blocks.RED_BED : Blocks.WHITE_BED;
    }

    private static Block coralFan(int aux) {
        return switch (aux % 5) {
            case 1 -> Blocks.BRAIN_CORAL_WALL_FAN;
            case 2 -> Blocks.BUBBLE_CORAL_WALL_FAN;
            case 3 -> Blocks.FIRE_CORAL_WALL_FAN;
            case 4 -> Blocks.HORN_CORAL_WALL_FAN;
            default -> Blocks.TUBE_CORAL_WALL_FAN;
        };
    }

    private static Block roofBlock(RuinMaterials m, Block[] stone) {
        return switch (m.roofCover) {
            case RuinMaterials.ROOF_TILE -> Blocks.DEEPSLATE_TILES;
            case RuinMaterials.ROOF_STONE -> stone[MAIN];
            default -> PLANKS[m.roofWood];
        };
    }

    private static Block roofStairs(RuinMaterials m, Block[] stone) {
        return switch (m.roofCover) {
            case RuinMaterials.ROOF_TILE -> Blocks.DEEPSLATE_TILE_STAIRS;
            case RuinMaterials.ROOF_STONE -> stone[STAIR];
            default -> STAIRS[m.roofWood];
        };
    }

    private static Block roofSlab(RuinMaterials m, Block[] stone) {
        return switch (m.roofCover) {
            case RuinMaterials.ROOF_TILE -> Blocks.DEEPSLATE_TILE_SLAB;
            case RuinMaterials.ROOF_STONE -> stone[SLAB];
            default -> SLABS[m.roofWood];
        };
    }

    private static BlockState stairs(Block block, Direction facing, boolean top) {
        BlockState s = block.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        if (s.hasProperty(BlockStateProperties.HALF)) {
            s = s.setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
        }
        return s;
    }

    private static BlockState slab(Block block, boolean top) {
        BlockState s = block.defaultBlockState();
        return s.hasProperty(BlockStateProperties.SLAB_TYPE) ? s.setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM) : s;
    }

    private static BlockState axis(Block block, int axis) {
        BlockState s = block.defaultBlockState();
        if (!s.hasProperty(RotatedPillarBlock.AXIS)) {
            return s;
        }
        return s.setValue(RotatedPillarBlock.AXIS, axis == RuinCell.AXIS_X ? Direction.Axis.X : axis == RuinCell.AXIS_Z ? Direction.Axis.Z : Direction.Axis.Y);
    }
}
