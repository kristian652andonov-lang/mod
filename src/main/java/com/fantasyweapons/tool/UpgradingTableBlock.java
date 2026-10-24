package com.fantasyweapons.tool;

import com.fantasyweapons.machine.MachineCoreBlock;
import com.fantasyweapons.machine.MachinePart;
import com.fantasyweapons.machine.ModelShape;
import com.fantasyweapons.registry.ModBlockEntities;
import com.fantasyweapons.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The Legendary Upgrading Table: a workbench with a rune plate and vise, a lens on an arm, a grindstone and a copper cup,
 * one block wide and two high (its arch rises into the block above), drawn whole by the artist's GeckoLib model. A
 * legendary tool laid in its vise and the tool's refined material in its cup raise the tool's mining area, Fortune or
 * Efficiency a step at a time, for experience (see {@link UpgradingTableBlockEntity}). {@link #WORKING} while it upgrades.
 */
public class UpgradingTableBlock extends MachineCoreBlock {
    public static final MapCodec<UpgradingTableBlock> CODEC = simpleCodec(UpgradingTableBlock::new);
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    private static final List<MachinePart> PARTS = List.of(MachinePart.TOP);

    public UpgradingTableBlock(Properties properties) {
        super(properties, ModBlocks.UPGRADING_TABLE_PART);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WORKING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WORKING);
    }

    @Override
    public List<MachinePart> parts() {
        return PARTS;
    }

    /** The bench is a little wider than its block (the grindstone and the cup hang over its ends). */
    @Override
    protected double overhang() {
        return 0.35;
    }

    /** Shaped by the artist's model: the bench, its arch, the grindstone and the lens on its arm. */
    @Override
    protected List<AABB> northBoxes() {
        return ModelShape.boxes("legendary_upgrading_table", Set.of("table", "legendary_upgrading_table_grindstone",
                "legendary_upgrading_table_lens_arm", "legendary_upgrading_table_lens"));
    }

    @Override
    public InteractionResult open(Level level, BlockPos core, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(core) instanceof UpgradingTableBlockEntity be) {
            sp.openMenu(be, buf -> buf.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Broken mid-upgrade, the upgrade is finished first (it was paid for); then the tool and the material drop. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof UpgradingTableBlockEntity be) {
            be.finishNow();
            for (int i = 0; i < be.items().getSlots(); i++) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, be.items().getStackInSlot(i));
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UpgradingTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.UPGRADING_TABLE.get(), UpgradingTableBlockEntity::serverTick);
    }

    /** While it upgrades: enchanting glyphs drawn into the plate, and sparks off the grindstone. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING) || !(level.getBlockEntity(pos) instanceof UpgradingTableBlockEntity be)) return;
        Vec3 plate = be.spot(0, 18, -0.8), wheel = be.spot(9.4, 22.2, -0.8);
        for (int i = 0; i < 3; i++) {
            double dx = (random.nextDouble() - 0.5) * 2.2, dy = random.nextDouble() * 1.4 + 0.4, dz = (random.nextDouble() - 0.5) * 2.2;
            level.addParticle(ParticleTypes.ENCHANT, plate.x + dx, plate.y + dy, plate.z + dz, -dx, -dy, -dz);
        }
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, wheel.x, wheel.y + 0.05, wheel.z, (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.2,
                    (random.nextDouble() - 0.5) * 0.3);
        }
    }
}
