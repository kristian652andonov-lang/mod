package com.terrahaul.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Fallen branch: three shapes (straight, forked, bent), optionally moss-covered. */
public class BranchBlock extends GroundDecorBlock {
    public static final MapCodec<BranchBlock> CODEC = simpleCodec(BranchBlock::new);
    public static final IntegerProperty SHAPE = IntegerProperty.create("shape", 0, 2);
    public static final BooleanProperty MOSSY = BooleanProperty.create("mossy");

    public BranchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SHAPE, 0).setValue(MOSSY, false));
    }

    @Override
    protected MapCodec<? extends BranchBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SHAPE, MOSSY);
    }
}
