package com.terrahaul.worldgen.trees;

import com.mojang.serialization.Codec;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tree species handled by the procedural tree generator, with the vanilla blocks each one uses.
 * Pine, fir and swamp oak are shape variants built from the spruce and oak block families.
 * Pale oak does not exist in Minecraft 1.21.1 (it was added in 1.21.4) and is therefore absent.
 */
public enum TreeSpecies implements StringRepresentable {
    OAK("oak", Blocks.OAK_LOG, Blocks.OAK_WOOD, Blocks.OAK_LEAVES, null),
    SWAMP_OAK("swamp_oak", Blocks.OAK_LOG, Blocks.OAK_WOOD, Blocks.OAK_LEAVES, null),
    BIRCH("birch", Blocks.BIRCH_LOG, Blocks.BIRCH_WOOD, Blocks.BIRCH_LEAVES, null),
    SPRUCE("spruce", Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, Blocks.SPRUCE_LEAVES, null),
    PINE("pine", Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, Blocks.SPRUCE_LEAVES, null),
    FIR("fir", Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, Blocks.SPRUCE_LEAVES, null),
    JUNGLE("jungle", Blocks.JUNGLE_LOG, Blocks.JUNGLE_WOOD, Blocks.JUNGLE_LEAVES, null),
    ACACIA("acacia", Blocks.ACACIA_LOG, Blocks.ACACIA_WOOD, Blocks.ACACIA_LEAVES, null),
    DARK_OAK("dark_oak", Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD, Blocks.DARK_OAK_LEAVES, null),
    MANGROVE("mangrove", Blocks.MANGROVE_LOG, Blocks.MANGROVE_WOOD, Blocks.MANGROVE_LEAVES, null),
    CHERRY("cherry", Blocks.CHERRY_LOG, Blocks.CHERRY_WOOD, Blocks.CHERRY_LEAVES, null),
    AZALEA("azalea", Blocks.OAK_LOG, Blocks.OAK_WOOD, Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES),
    DEAD("dead", Blocks.OAK_LOG, Blocks.OAK_WOOD, Blocks.OAK_LEAVES, null),
    DEAD_SPRUCE("dead_spruce", Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, Blocks.SPRUCE_LEAVES, null),
    /** Fire-killed trees on volcano slopes: charred, leafless trunks and snags. */
    BURNED("burned", Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD, Blocks.DARK_OAK_LEAVES, null);

    public static final Codec<TreeSpecies> CODEC = StringRepresentable.fromEnum(TreeSpecies::values);

    private final String name;
    private final Block log;
    private final Block wood;
    private final Block leaves;
    private final Block altLeaves;

    TreeSpecies(String name, Block log, Block wood, Block leaves, Block altLeaves) {
        this.name = name;
        this.log = log;
        this.wood = wood;
        this.leaves = leaves;
        this.altLeaves = altLeaves;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public BlockState log() {
        return log.defaultBlockState();
    }

    public BlockState wood() {
        return wood.defaultBlockState();
    }

    public BlockState leaves() {
        return leaves.defaultBlockState();
    }

    public BlockState altLeaves() {
        return altLeaves == null ? leaves.defaultBlockState() : altLeaves.defaultBlockState();
    }

    /** Whether the tree may grow on the given ground block. */
    public boolean canGrowOn(BlockState ground) {
        return switch (this) {
            case MANGROVE -> ground.is(Blocks.MUD) || ground.is(Blocks.MUDDY_MANGROVE_ROOTS) || ground.is(BlockTags.DIRT) || ground.is(Blocks.CLAY);
            case ACACIA, DEAD -> ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(Blocks.TERRACOTTA);
            case BURNED -> ground.is(BlockTags.DIRT) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.TUFF) || ground.is(Blocks.BASALT);
            case AZALEA -> ground.is(BlockTags.DIRT) || ground.is(Blocks.CLAY) || ground.is(Blocks.MOSS_BLOCK);
            default -> ground.is(BlockTags.DIRT);
        };
    }

    public boolean isConifer() {
        return this == SPRUCE || this == PINE || this == FIR || this == DEAD_SPRUCE;
    }
}
