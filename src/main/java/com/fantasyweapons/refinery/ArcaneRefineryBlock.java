package com.fantasyweapons.refinery;

import com.fantasyweapons.registry.ModBlockEntities;
import com.fantasyweapons.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Arcane Refinery's core: the bottom middle of a three-wide, two-high furnace (the other five blocks are
 * {@link RefineryPartBlock}s). It owns the inventory and the refining (see {@link ArcaneRefineryBlockEntity}) and is
 * drawn, all six blocks of it, by its GeckoLib model. {@link #LIT} while it is refining.
 */
public class ArcaneRefineryBlock extends BaseEntityBlock {
    public static final MapCodec<ArcaneRefineryBlock> CODEC = simpleCodec(ArcaneRefineryBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ArcaneRefineryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** Placed facing the player, if all five other blocks of it have room. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() + 1 >= level.getMaxBuildHeight()) return null;
        for (RefineryPart part : RefineryPart.values()) {
            if (!level.getBlockState(part.from(pos, facing)).canBeReplaced(ctx)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (RefineryPart part : RefineryPart.values()) {
            // placed without shape updates so the half-built structure does not take itself apart
            level.setBlock(part.from(pos, facing), ModBlocks.REFINERY_PART.get().defaultBlockState().setValue(RefineryPartBlock.FACING, facing)
                    .setValue(RefineryPartBlock.PART, part), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (RefineryPart part : RefineryPart.values()) level.blockUpdated(part.from(pos, facing), ModBlocks.REFINERY_PART.get());
    }

    /** Whether all six blocks of the refinery whose core is at {@code core} are in place. */
    static boolean intact(BlockGetter level, BlockPos core, Direction facing) {
        BlockState c = level.getBlockState(core);
        if (!(c.getBlock() instanceof ArcaneRefineryBlock) || c.getValue(FACING) != facing) return false;
        for (RefineryPart part : RefineryPart.values()) {
            BlockState s = level.getBlockState(part.from(core, facing));
            if (!(s.getBlock() instanceof RefineryPartBlock) || s.getValue(RefineryPartBlock.FACING) != facing || s.getValue(RefineryPartBlock.PART) != part) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return intact(level, pos, state.getValue(FACING)) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return open(level, pos, player);
    }

    static InteractionResult open(Level level, BlockPos core, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(core) instanceof ArcaneRefineryBlockEntity be) {
            sp.openMenu(be, buf -> buf.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ArcaneRefineryBlockEntity be) {
                for (int i = 0; i < be.items().getSlots(); i++) Containers.dropItemStack(level, pos.getX(), pos.getY() + 0.5, pos.getZ(), be.items().getStackInSlot(i));
            }
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ArcaneRefineryBlockEntity be
                ? net.neoforged.neoforge.items.ItemHandlerHelper.calcRedstoneFromInventory(be.items()) : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcaneRefineryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.ARCANE_REFINERY.get(), ArcaneRefineryBlockEntity::serverTick);
    }

    /** The fire crackling and the bellows breathing while it refines. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        if (random.nextInt(4) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1f,
                    0.8f + random.nextFloat() * 0.2f, false);
        }
        if (random.nextInt(16) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.5f,
                    0.6f + random.nextFloat() * 0.3f, false);
        }
    }
}
