package com.fantasyweapons.forge;

import com.fantasyweapons.machine.MachineCoreBlock;
import com.fantasyweapons.registry.ModBlockEntities;
import com.fantasyweapons.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Legendary Crafting Table's core: the bottom middle of a table three blocks wide and two high (see
 * {@link MachineCoreBlock}), drawn whole by the artist's GeckoLib model. Its 9x9 grid ({@link LegendaryCraftingMenu}) forges
 * the legendary weapons and armor; the table is {@link #CRAFTING} - and plays its crafting animation - while a recipe lies
 * on someone's grid and for a moment after an item is forged (see {@link LegendaryCraftingTableBlockEntity}).
 */
public class LegendaryCraftingTableBlock extends MachineCoreBlock {
    public static final MapCodec<LegendaryCraftingTableBlock> CODEC = simpleCodec(LegendaryCraftingTableBlock::new);
    public static final BooleanProperty CRAFTING = BooleanProperty.create("crafting");
    public static final Component TITLE = Component.literal("Legendary Crafting Table");

    public LegendaryCraftingTableBlock(Properties properties) {
        super(properties, ModBlocks.LEGENDARY_CRAFTING_TABLE_PART);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CRAFTING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CRAFTING);
    }

    /**
     * The table's shape, from the artist's model (facing north, in the core's pixels): the table itself, the dragon's
     * breath bottles at its sides, the two totems at its back and the star hanging over the grid.
     */
    @Override
    protected VoxelShape northShape() {
        return Shapes.or(Block.box(-4.5, 0, -2, 20.5, 18, 18),
                Block.box(-9.3, 0, 4.9, -4.5, 17, 11.1), Block.box(20.5, 0, 4.9, 25.3, 17, 11.1),
                Block.box(-4.1, 18, 12.9, 2.3, 28.5, 15.7), Block.box(13.7, 18, 12.9, 20.1, 28.5, 15.7),
                Block.box(2, 26, 3, 14, 34, 13));
    }

    @Override
    public InteractionResult open(Level level, BlockPos core, Player player) {
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new LegendaryCraftingMenu(id, inventory, ContainerLevelAccess.create(level, core)),
                    TITLE));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LegendaryCraftingTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.LEGENDARY_CRAFTING_TABLE.get(), LegendaryCraftingTableBlockEntity::serverTick);
    }

    /** Starlight drifting up from the grid while it crafts, and the enchanting table's hum. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(CRAFTING)) return;
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.6, pos.getY() + 1.3,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0, 0.02 + random.nextDouble() * 0.03, 0);
        }
        if (random.nextInt(24) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, net.minecraft.sounds.SoundEvents.BEACON_AMBIENT,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.4f + random.nextFloat() * 0.2f, false);
        }
    }
}
