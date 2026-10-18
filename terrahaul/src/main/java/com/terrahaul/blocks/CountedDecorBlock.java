package com.terrahaul.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pebbles and pine cones: one to four small objects per block. Like sea pickles, using another one
 * on the block adds to the pile.
 */
public class CountedDecorBlock extends GroundDecorBlock {
    public static final MapCodec<CountedDecorBlock> CODEC = simpleCodec(CountedDecorBlock::new);
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, 4);
    private static final VoxelShape[] SHAPES = {
        Block.box(5.0, 0.0, 5.0, 11.0, 2.0, 11.0),
        Block.box(3.0, 0.0, 3.0, 13.0, 2.0, 13.0),
        Block.box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0),
        Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0)
    };

    public CountedDecorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(COUNT, 1));
    }

    @Override
    protected MapCodec<? extends CountedDecorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COUNT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        if (existing.is(this)) {
            return existing.setValue(COUNT, Math.min(4, existing.getValue(COUNT) + 1));
        }
        return super.getStateForPlacement(context);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(this.asItem()) && state.getValue(COUNT) < 4
            || super.canBeReplaced(state, context);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(COUNT) - 1];
    }
}
