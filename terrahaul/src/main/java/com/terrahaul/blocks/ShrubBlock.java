package com.terrahaul.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Low bushes and shrubs. They grow on soil; the dry shrub also on sand and terracotta. Walkable
 * through like tall grass.
 */
public class ShrubBlock extends BushBlock {
    public static final MapCodec<ShrubBlock> CODEC = simpleCodec(ShrubBlock::new);
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0);

    public ShrubBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends ShrubBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA) || state.is(net.minecraft.world.level.block.Blocks.GRAVEL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.move(state.getOffset(level, pos).x, 0.0, state.getOffset(level, pos).z);
    }
}
