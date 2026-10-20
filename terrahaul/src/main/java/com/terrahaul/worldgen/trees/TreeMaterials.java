package com.terrahaul.worldgen.trees;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The vanilla blocks each {@link TreeSpecies} is built from, and the ground it may grow on. Kept apart
 * from the plain-Java species enum so that tree shapes can be built without Minecraft.
 */
final class TreeMaterials {
    private TreeMaterials() {
    }

    private static Block logBlock(TreeSpecies s) {
        return switch (s) {
            case OAK, SWAMP_OAK, AZALEA, DEAD -> Blocks.OAK_LOG;
            case BIRCH -> Blocks.BIRCH_LOG;
            case SPRUCE, PINE, FIR, DEAD_SPRUCE -> Blocks.SPRUCE_LOG;
            case JUNGLE -> Blocks.JUNGLE_LOG;
            case ACACIA -> Blocks.ACACIA_LOG;
            // fire-killed trees: the darkest bark in the game reads as charred wood
            case DARK_OAK, BURNED -> Blocks.DARK_OAK_LOG;
            case MANGROVE -> Blocks.MANGROVE_LOG;
            case CHERRY -> Blocks.CHERRY_LOG;
        };
    }

    private static Block woodBlock(TreeSpecies s) {
        return switch (s) {
            case OAK, SWAMP_OAK, AZALEA, DEAD -> Blocks.OAK_WOOD;
            case BIRCH -> Blocks.BIRCH_WOOD;
            case SPRUCE, PINE, FIR, DEAD_SPRUCE -> Blocks.SPRUCE_WOOD;
            case JUNGLE -> Blocks.JUNGLE_WOOD;
            case ACACIA -> Blocks.ACACIA_WOOD;
            case DARK_OAK, BURNED -> Blocks.DARK_OAK_WOOD;
            case MANGROVE -> Blocks.MANGROVE_WOOD;
            case CHERRY -> Blocks.CHERRY_WOOD;
        };
    }

    private static Block leafBlock(TreeSpecies s) {
        return switch (s) {
            case OAK, SWAMP_OAK, DEAD -> Blocks.OAK_LEAVES;
            case BIRCH -> Blocks.BIRCH_LEAVES;
            case SPRUCE, PINE, FIR, DEAD_SPRUCE -> Blocks.SPRUCE_LEAVES;
            case JUNGLE -> Blocks.JUNGLE_LEAVES;
            case ACACIA -> Blocks.ACACIA_LEAVES;
            case DARK_OAK, BURNED -> Blocks.DARK_OAK_LEAVES;
            case MANGROVE -> Blocks.MANGROVE_LEAVES;
            case CHERRY -> Blocks.CHERRY_LEAVES;
            case AZALEA -> Blocks.AZALEA_LEAVES;
        };
    }

    static BlockState log(TreeSpecies s, Direction.Axis axis) {
        return logBlock(s).defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    static BlockState wood(TreeSpecies s) {
        return woodBlock(s).defaultBlockState();
    }

    /** The bark-on-all-sides blocks of every species. */
    private static final Set<Block> WOOD_BLOCKS = Stream.of(TreeSpecies.values()).map(TreeMaterials::woodBlock).collect(Collectors.toSet());
    /** The log blocks of every species: never stripped ones, which only fallen logs and ruins carry. */
    private static final Set<Block> LOG_BLOCKS = Stream.of(TreeSpecies.values()).map(TreeMaterials::logBlock).collect(Collectors.toSet());

    /** Whether the block is one of the trees' bark-on-all-sides wood blocks (bends, junctions and limb ends). */
    static boolean isWoodBlock(BlockState state) {
        return WOOD_BLOCKS.contains(state.getBlock());
    }

    /** Whether the block is the log of one of the trees (in any direction, never a stripped log). */
    static boolean isTreeLog(BlockState state) {
        return LOG_BLOCKS.contains(state.getBlock());
    }

    static BlockState leaves(TreeSpecies s) {
        return leafBlock(s).defaultBlockState();
    }

    /** Second leaf kind mixed into the crown (flowering azalea); the normal leaves otherwise. */
    static BlockState altLeaves(TreeSpecies s) {
        return s == TreeSpecies.AZALEA ? Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState() : leaves(s);
    }

    /** The block state of a woody blueprint cell. */
    static BlockState woody(TreeSpecies s, int code) {
        return switch (code) {
            case TreeBlueprint.LOG_X -> log(s, Direction.Axis.X);
            case TreeBlueprint.LOG_Z -> log(s, Direction.Axis.Z);
            case TreeBlueprint.WOOD -> wood(s);
            default -> log(s, Direction.Axis.Y);
        };
    }

    /** Whether the tree may grow on the given ground block. */
    static boolean canGrowOn(TreeSpecies s, BlockState ground) {
        return switch (s) {
            case MANGROVE -> ground.is(Blocks.MUD) || ground.is(Blocks.MUDDY_MANGROVE_ROOTS) || ground.is(BlockTags.DIRT) || ground.is(Blocks.CLAY);
            case ACACIA, DEAD -> ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(Blocks.TERRACOTTA);
            case BURNED -> ground.is(BlockTags.DIRT) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.TUFF) || ground.is(Blocks.BASALT)
                || ground.is(Blocks.SMOOTH_BASALT) || ground.is(Blocks.BLACKSTONE);
            case AZALEA -> ground.is(BlockTags.DIRT) || ground.is(Blocks.CLAY) || ground.is(Blocks.MOSS_BLOCK);
            default -> ground.is(BlockTags.DIRT);
        };
    }
}
