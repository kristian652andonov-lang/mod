package com.fantasyweapons.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * One of the five blocks around a machine's core. Invisible (the core draws the whole machine); using it opens the
 * machine, breaking it breaks the whole machine (which drops as one item), and comparators read the machine through it.
 */
public class MachinePartBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<MachinePart> PART = EnumProperty.create("part", MachinePart.class);
    private final Supplier<? extends MachineCoreBlock> coreBlock;

    public MachinePartBlock(Properties properties, Supplier<? extends MachineCoreBlock> coreBlock) {
        super(properties);
        this.coreBlock = coreBlock;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, MachinePart.LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return coreBlock.get().outline(state.getValue(FACING), state.getValue(PART));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return coreBlock.get().collision(state.getValue(FACING), state.getValue(PART));
    }

    public static BlockPos core(BlockState state, BlockPos pos) {
        return state.getValue(PART).core(pos, state.getValue(FACING));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return coreBlock.get().intact(level, core(state, pos), state.getValue(FACING)) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return coreBlock.get().open(level, core(state, pos), player);
    }

    /** Breaking any part breaks the whole machine, which drops as one item. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos core = core(state, pos);
        if (!level.isClientSide && level.getBlockState(core).getBlock() == coreBlock.get()) {
            if (!player.isCreative() && player.hasCorrectToolForDrops(state)) Block.popResource(level, core, new ItemStack(coreBlock.get()));
            level.setBlock(core, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(coreBlock.get());
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockPos core = core(state, pos);
        BlockState c = level.getBlockState(core);
        return c.getBlock() == coreBlock.get() ? coreBlock.get().signal(c, level, core) : 0;
    }
}
