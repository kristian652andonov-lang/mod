package com.fantasyweapons.refinery;

import com.fantasyweapons.machine.MachineCoreBlock;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * The Arcane Refinery's core: the bottom middle of a three-wide, two-high furnace (see {@link MachineCoreBlock}). It owns
 * the inventory and the refining (see {@link ArcaneRefineryBlockEntity}) and is drawn, all six blocks of it, by its
 * GeckoLib model. {@link #LIT} while it is refining.
 */
public class ArcaneRefineryBlock extends MachineCoreBlock {
    public static final MapCodec<ArcaneRefineryBlock> CODEC = simpleCodec(ArcaneRefineryBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ArcaneRefineryBlock(Properties properties) {
        super(properties, ModBlocks.REFINERY_PART);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    /**
     * Shaped by the artist's model: its body and the machinery on it that only moves in place (the bellows, gears, valve,
     * pistons and lever); the flames, smoke, sparks and floating rune rings are left out.
     */
    @Override
    protected java.util.List<net.minecraft.world.phys.AABB> northBoxes() {
        return com.fantasyweapons.machine.ModelShape.boxes("arcane_refinery", java.util.Set.of("refinery", "arcane_refinery_bellows_pleat",
                "arcane_refinery_bellows_board", "arcane_refinery_gear_large", "arcane_refinery_gear_small", "arcane_refinery_valve",
                "arcane_refinery_piston_0", "arcane_refinery_piston_1", "arcane_refinery_lever"));
    }

    @Override
    public InteractionResult open(Level level, BlockPos core, Player player) {
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
        return signal(state, level, pos);
    }

    @Override
    public int signal(BlockState state, Level level, BlockPos pos) {
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
