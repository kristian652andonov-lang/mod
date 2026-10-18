package com.terrahaul.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A thin layer of fallen leaves or needles. Replaceable, like short grass. */
public class LeafLitterBlock extends GroundDecorBlock {
    public static final MapCodec<LeafLitterBlock> CODEC = simpleCodec(LeafLitterBlock::new);
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

    public LeafLitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends LeafLitterBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
