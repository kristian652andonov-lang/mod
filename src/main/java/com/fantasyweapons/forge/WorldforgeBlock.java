package com.fantasyweapons.forge;

import com.fantasyweapons.machine.ModelShape;
import com.fantasyweapons.registry.ModBlockEntities;
import com.fantasyweapons.registry.ModBlocks;
import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The Worldforge: the altar the two ultimate weapons are forged on, the artist's model seven blocks wide, seven deep and
 * nearly seven high. This block is its core, in the middle of the bottom layer; it owns the block entity (the 11x11 grid,
 * see {@link WorldforgeMenu}) and draws the whole forge. Every other block of its 7x7x7 space is a
 * {@link WorldforgePartBlock}: solid where the model is (its tiers can be climbed, its pylons and spires walked round),
 * empty elsewhere, so nothing is built into it. It needs all that space clear to be placed, unfolds when it is, and comes
 * apart as a whole.
 */
public class WorldforgeBlock extends BaseEntityBlock {
    public static final MapCodec<WorldforgeBlock> CODEC = simpleCodec(WorldforgeBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CRAFTING = BooleanProperty.create("crafting");
    public static final Component TITLE = Component.literal("The Worldforge");
    /** Blocks from the core to the edge, and layers from the bottom to the top. */
    public static final int REACH = 3, LAYERS = 7, SIDE = 2 * REACH + 1;
    /** The still parts of the model: the tiers, the altar and anvil, the seven pylons, the four spires and the gantry. */
    private static final Set<String> SOLID = Set.of("worldforge_tier_0", "worldforge_tier_1", "worldforge_tier_2", "worldforge_altar", "worldforge_anvil",
            "worldforge_pylon_fire", "worldforge_pylon_ice", "worldforge_pylon_nature", "worldforge_pylon_lightning", "worldforge_pylon_light",
            "worldforge_pylon_void", "worldforge_pylon_soul", "worldforge_spire_0", "worldforge_spire_1", "worldforge_spire_2", "worldforge_spire_3",
            "worldforge_gantry");
    /** Per facing (2D data value), per cell ({@link #cell}): the model inside that block. */
    private static final Supplier<VoxelShape[][]> SHAPES = Suppliers.memoize(WorldforgeBlock::bake);

    public WorldforgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CRAFTING, false));
    }

    /** The index of the cell {@code (dx, dy, dz)} blocks from the core (dx and dz from -3 to 3, dy from 0 to 6). */
    public static int cell(int dx, int dy, int dz) {
        return (dx + REACH) + (dz + REACH) * SIDE + dy * SIDE * SIDE;
    }

    /** Less than this of a box reaching into a block (sideways) is a rounding sliver, not a part of the model there. */
    private static final double SLIVER = 1 / 24.0;

    private static VoxelShape[][] bake() {
        List<AABB> north = ModelShape.boxes("worldforge", SOLID);
        VoxelShape[][] baked = new VoxelShape[4][SIDE * SIDE * LAYERS];
        for (int f = 0; f < 4; f++) {
            Direction facing = Direction.from2DDataValue(f);
            List<AABB> turned = new ArrayList<>(north.size());
            for (AABB box : north) turned.add(turn(box, facing));
            for (int dy = 0; dy < LAYERS; dy++) {
                for (int dx = -REACH; dx <= REACH; dx++) {
                    for (int dz = -REACH; dz <= REACH; dz++) {
                        List<AABB> local = new ArrayList<>();
                        for (AABB box : turned) {
                            // a model face that lies on a block's edge is widened by half a pixel when it is snapped to the grid:
                            // that sliver is left to the block it belongs to
                            if (box.maxX <= dx + SLIVER || box.minX >= dx + 1 - SLIVER || box.maxY <= dy || box.minY >= dy + 1
                                    || box.maxZ <= dz + SLIVER || box.minZ >= dz + 1 - SLIVER) continue;
                            local.add(box.move(-dx, -dy, -dz));
                        }
                        baked[f][cell(dx, dy, dz)] = ModelShape.cell(local);
                    }
                }
            }
        }
        return baked;
    }

    /** A box of the north-facing model turned to face {@code facing} about the core's centre (as the renderer turns the model). */
    static AABB turn(AABB box, Direction facing) {
        Direction right = facing.getClockWise(), back = facing.getOpposite();
        double[] xs = {box.minX - 0.5, box.maxX - 0.5}, zs = {box.minZ - 0.5, box.maxZ - 0.5};
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double x : xs) {
            for (double z : zs) {
                double wx = 0.5 + x * right.getStepX() + z * back.getStepX(), wz = 0.5 + x * right.getStepZ() + z * back.getStepZ();
                minX = Math.min(minX, wx);
                maxX = Math.max(maxX, wx);
                minZ = Math.min(minZ, wz);
                maxZ = Math.max(maxZ, wz);
            }
        }
        return new AABB(minX, box.minY, minZ, maxX, box.maxY, maxZ);
    }

    /** What of the forge, facing {@code facing}, lies in the block {@code (dx, dy, dz)} from its core. */
    public static VoxelShape shape(Direction facing, int dx, int dy, int dz) {
        return SHAPES.get()[facing.get2DDataValue()][cell(dx, dy, dz)];
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CRAFTING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape(state.getValue(FACING), 0, 0, 0);
    }

    // ------------------------------------------------------------------------------------------------------------
    // placing and taking apart
    // ------------------------------------------------------------------------------------------------------------

    /** Every position of the forge whose core is at {@code core}, the core's own first. */
    public static List<BlockPos> space(BlockPos core) {
        List<BlockPos> all = new ArrayList<>(SIDE * SIDE * LAYERS);
        all.add(core);
        for (int dy = 0; dy < LAYERS; dy++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) if (dx != 0 || dy != 0 || dz != 0) all.add(core.offset(dx, dy, dz));
            }
        }
        return all;
    }

    /** Placed facing the player, only if its whole 7x7x7 space is clear (the player is told if it is not). */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos core = ctx.getClickedPos();
        boolean clear = core.getY() + LAYERS - 1 < level.getMaxBuildHeight();
        for (BlockPos p : space(core)) {
            if (!clear) break;
            if (!level.getBlockState(p).canBeReplaced(ctx) || !level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(p),
                    e -> e instanceof LivingEntity).isEmpty()) clear = false;
        }
        if (!clear) {
            if (!level.isClientSide && ctx.getPlayer() != null) {
                ctx.getPlayer().displayClientMessage(Component.literal("The Worldforge needs a clear space 7 wide, 7 deep and 7 high, centred on where it stands")
                        .withStyle(s -> s.withColor(0xFF5A4A)), true);
            }
            return null;
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        placeParts(level, pos, state.getValue(FACING));
        if (level.getBlockEntity(pos) instanceof WorldforgeBlockEntity forge) forge.placed();
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.2f, 0.5f);
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.5f, 0.4f);
            level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.6f, 0.5f);
        }
    }

    /** Fills the forge's space around a core at {@code core} with its parts. */
    public void placeParts(Level level, BlockPos core, Direction facing) {
        WorldforgePartBlock part = ModBlocks.WORLDFORGE_PART.get();
        for (int dy = 0; dy < LAYERS; dy++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    // placed without shape updates so the half-built forge does not take itself apart
                    level.setBlock(core.offset(dx, dy, dz), part.at(facing, dx, dy, dz), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    /** Whether the part at {@code pos} belongs to the forge whose core is at {@code core}. */
    public boolean owns(BlockGetter level, BlockPos core, Direction facing) {
        BlockState c = level.getBlockState(core);
        return c.getBlock() == this && c.getValue(FACING) == facing;
    }

    /** Broken, the forge gives back what lay on its grid, its Forged slot and what a forging under way had taken, and its parts go. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof WorldforgeBlockEntity forge) {
                for (var stack : forge.drops()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, stack);
            }
            if (!level.isClientSide) {
                Direction facing = state.getValue(FACING);
                WorldforgePartBlock part = ModBlocks.WORLDFORGE_PART.get();
                for (BlockPos p : space(pos)) {
                    if (p.equals(pos)) continue;
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() == part && part.core(s, p).equals(pos) && s.getValue(WorldforgePartBlock.FACING) == facing) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return open(level, pos, player);
    }

    /** Opens the forge whose core is at {@code core}. */
    public InteractionResult open(Level level, BlockPos core, Player player) {
        if (!level.isClientSide && level.getBlockEntity(core) instanceof WorldforgeBlockEntity forge) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new WorldforgeMenu(id, inventory, ContainerLevelAccess.create(level, core), forge), TITLE));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorldforgeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.WORLDFORGE.get(), level.isClientSide ? WorldforgeBlockEntity::clientTick : WorldforgeBlockEntity::serverTick);
    }

    /** A low hum always, a heartbeat while it forges. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean crafting = state.getValue(CRAFTING);
        if (random.nextInt(crafting ? 20 : 60) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 3, pos.getZ() + 0.5, crafting ? SoundEvents.WARDEN_HEARTBEAT : SoundEvents.BEACON_AMBIENT,
                    SoundSource.BLOCKS, crafting ? 1.2f : 0.5f, crafting ? 0.6f : 0.5f, false);
        }
    }
}
