package com.terrahaul.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A medium, knee-high rock. Unlike the other ground details it has collision. */
public class RockBlock extends GroundDecorBlock {
    public static final MapCodec<RockBlock> CODEC = simpleCodec(RockBlock::new);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 1);
    private static final VoxelShape[] SHAPES = {Block.box(2.0, 0.0, 2.0, 14.0, 7.0, 14.0), Block.box(1.0, 0.0, 3.0, 15.0, 9.0, 13.0)};

    public RockBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }

    @Override
    protected MapCodec<? extends RockBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIANT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(VARIANT)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(VARIANT)];
    }

    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
}
