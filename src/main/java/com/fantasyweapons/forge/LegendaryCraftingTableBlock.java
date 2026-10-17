package com.fantasyweapons.forge;

import com.fantasyweapons.machine.MachineCoreBlock;
import com.fantasyweapons.machine.ModelShape;
import com.fantasyweapons.registry.ModBlockEntities;
import com.fantasyweapons.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

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

    /** Shaped by the artist's model: the table and its bottles, and the two totems on it (the star floats free). */
    @Override
    protected List<AABB> northBoxes() {
        return ModelShape.boxes("legendary_crafting_table", Set.of("table", "legendary_crafting_table_totem_left", "legendary_crafting_table_totem_right"));
    }

    @Override
    public InteractionResult open(Level level, BlockPos core, Player player) {
        if (!level.isClientSide && level.getBlockEntity(core) instanceof LegendaryCraftingTableBlockEntity table) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new LegendaryCraftingMenu(id, inventory, ContainerLevelAccess.create(level, core),
                    table), TITLE));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Broken, the table gives back what lay on its grid, its Forged slot, and what a forging under way had taken. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LegendaryCraftingTableBlockEntity table) {
            for (var stack : table.drops()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, stack);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LegendaryCraftingTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.LEGENDARY_CRAFTING_TABLE.get(),
                level.isClientSide ? LegendaryCraftingTableBlockEntity::clientTick : LegendaryCraftingTableBlockEntity::serverTick);
    }

    /** Now and then the beacon's hum while it forges (its sights are the block entity's: see its clientTick). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(CRAFTING)) return;
        if (random.nextInt(24) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, net.minecraft.sounds.SoundEvents.BEACON_AMBIENT,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.4f + random.nextFloat() * 0.2f, false);
        }
    }
}
