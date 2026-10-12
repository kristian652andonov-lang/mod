package com.fantasyweapons.refinery;

import com.fantasyweapons.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One of the five blocks around the refinery's core. Invisible (the core draws the whole machine); using it opens the
 * core, breaking it breaks the whole refinery, and hoppers against it reach the core's inventory.
 */
public class RefineryPartBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<RefineryPart> PART = EnumProperty.create("part", RefineryPart.class);

    public RefineryPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, RefineryPart.LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    public static BlockPos core(BlockState state, BlockPos pos) {
        return state.getValue(PART).core(pos, state.getValue(FACING));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return ArcaneRefineryBlock.intact(level, core(state, pos), state.getValue(FACING)) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return ArcaneRefineryBlock.open(level, core(state, pos), player);
    }

    /** Breaking any part breaks the whole refinery, which drops as one item. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos core = core(state, pos);
        if (!level.isClientSide && level.getBlockState(core).getBlock() instanceof ArcaneRefineryBlock) {
            if (!player.isCreative() && player.hasCorrectToolForDrops(state)) {
                Block.popResource(level, core, new ItemStack(ModItems.ARCANE_REFINERY.get()));
            }
            level.setBlock(core, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.ARCANE_REFINERY.get());
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockPos core = core(state, pos);
        BlockState c = level.getBlockState(core);
        return c.getBlock() instanceof ArcaneRefineryBlock b ? b.getAnalogOutputSignal(c, level, core) : 0;
    }
}
