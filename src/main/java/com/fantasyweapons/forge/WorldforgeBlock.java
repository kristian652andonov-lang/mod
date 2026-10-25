package com.fantasyweapons.forge;

import com.fantasyweapons.text.Tr;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The Worldforge: the altar the two ultimate weapons are forged on, the artist's model twenty-one blocks wide and deep and
 * some twenty high: a stepped base climbed by four stairs to an altar under a cage of ribs, a heart hanging in it, four
 * towers crowned with horned skulls at its corners. This block is its core, in the middle of the bottom layer; it owns the
 * block entity (the 11x11 grid, see {@link WorldforgeMenu}) and draws the whole forge. Every other block of its 21x22x21
 * space is a {@link WorldforgePartBlock}, shaped like the part of the model inside it - the tiers, stairs, altar, towers
 * and palisades are solid, the ribs and the air between them are not - so nothing is built into it. It needs all that
 * space clear to be placed, unfolds when it is, and comes apart as a whole.
 */
public class WorldforgeBlock extends BaseEntityBlock {
    public static final MapCodec<WorldforgeBlock> CODEC = simpleCodec(WorldforgeBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CRAFTING = BooleanProperty.create("crafting");
    public static final Component TITLE = Tr.c("The Worldforge");
    /** Blocks from the core to the edge, and layers from the bottom to the top. */
    public static final int REACH = 10, LAYERS = 22, SIDE = 2 * REACH + 1;
    /** The parts of the model that are solid: the tiers and stairs, the altar and anvil, the towers and their skulls, the palisades and friezes. */
    private static final Set<String> SOLID = solid();
    /** The shape of every cell of the forge for each facing, as ids into a table of the distinct shapes. */
    private static final Supplier<ShapeTable> SHAPES = Suppliers.memoize(WorldforgeBlock::bake);

    private static Set<String> solid() {
        Set<String> all = new java.util.HashSet<>(Set.of("worldforge_altar", "worldforge_anvil"));
        for (int i = 0; i < 4; i++) {
            for (String part : new String[]{"tier_", "stair_", "tower_", "tower_skull_top_", "palisade_", "frieze_", "bones_"}) all.add("worldforge_" + part + i);
        }
        return Set.copyOf(all);
    }

    /**
     * @param ids    per facing (2D data value), per cell ({@link #cell}): the id of its shape
     * @param shapes the distinct shapes, the empty one first
     */
    private record ShapeTable(short[][] ids, VoxelShape[] shapes) {
    }

    public WorldforgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CRAFTING, false));
    }

    /** The index of the cell {@code (dx, dy, dz)} blocks from the core (dx and dz from -REACH to REACH, dy from 0 up). */
    public static int cell(int dx, int dy, int dz) {
        return (dx + REACH) + (dz + REACH) * SIDE + dy * SIDE * SIDE;
    }

    /** Whether {@code pos} lies in the space of a forge whose core is at {@code core}. */
    public static boolean inside(BlockPos core, BlockPos pos) {
        int dx = pos.getX() - core.getX(), dy = pos.getY() - core.getY(), dz = pos.getZ() - core.getZ();
        return Math.abs(dx) <= REACH && Math.abs(dz) <= REACH && dy >= 0 && dy < LAYERS;
    }

    /** Less than this of a box reaching into a block (sideways) is a rounding sliver, not a part of the model there. */
    private static final double SLIVER = 1 / 24.0;

    /** Starts working out the forge's shapes in the background, so the first forge placed or loaded does not wait for them. */
    public static void warmUp() {
        java.util.concurrent.CompletableFuture.runAsync(SHAPES::get);
    }

    private static ShapeTable bake() {
        // tops a half pixel lower: the shapes are snapped out to the half-pixel grid, and a step snapped up past 0.6 of a
        // block could not be walked up
        List<AABB> north = new ArrayList<>();
        for (AABB b : ModelShape.boxes("worldforge", SOLID)) north.add(b.setMaxY(Math.max(b.minY + 1 / 32.0, b.maxY - 1 / 32.0)));
        int cells = SIDE * SIDE * LAYERS;
        short[][] ids = new short[4][cells];
        List<VoxelShape> shapes = new ArrayList<>();
        java.util.Map<List<AABB>, Integer> seen = new java.util.HashMap<>();
        shapes.add(Shapes.empty());
        seen.put(List.of(), 0);
        for (int f = 0; f < 4; f++) {
            Direction facing = Direction.from2DDataValue(f);
            // each turned box filed under every cell it reaches into
            List<List<AABB>> byCell = new ArrayList<>(cells);
            for (int i = 0; i < cells; i++) byCell.add(null);
            for (AABB box : north) {
                AABB t = turn(box, facing);
                int x0 = (int) Math.floor(t.minX + SLIVER), x1 = (int) Math.ceil(t.maxX - SLIVER) - 1;
                int z0 = (int) Math.floor(t.minZ + SLIVER), z1 = (int) Math.ceil(t.maxZ - SLIVER) - 1;
                int y0 = (int) Math.floor(t.minY), y1 = (int) Math.ceil(t.maxY) - 1;
                for (int dy = Math.max(0, y0); dy <= Math.min(LAYERS - 1, y1); dy++) {
                    for (int dx = Math.max(-REACH, x0); dx <= Math.min(REACH, x1); dx++) {
                        for (int dz = Math.max(-REACH, z0); dz <= Math.min(REACH, z1); dz++) {
                            int c = cell(dx, dy, dz);
                            if (byCell.get(c) == null) byCell.set(c, new ArrayList<>());
                            byCell.get(c).add(t.move(-dx, -dy, -dz));
                        }
                    }
                }
            }
            for (int c = 0; c < cells; c++) {
                List<AABB> local = byCell.get(c);
                if (local == null) continue;
                VoxelShape shape = ModelShape.cell(local);
                List<AABB> key = shape.toAabbs();
                Integer id = seen.get(key);
                if (id == null) {
                    id = shapes.size();
                    seen.put(key, id);
                    shapes.add(shape);
                }
                ids[f][c] = (short) (int) id;
            }
        }
        if (shapes.size() > WorldforgePartBlock.CAPACITY) {
            throw new IllegalStateException("The Worldforge's model has " + shapes.size() + " different block shapes, more than its parts can hold (" + WorldforgePartBlock.CAPACITY + ")");
        }
        return new ShapeTable(ids, shapes.toArray(VoxelShape[]::new));
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

    /** The id of the shape of the forge, facing {@code facing}, in the block {@code (dx, dy, dz)} from its core. */
    public static int shapeId(Direction facing, int dx, int dy, int dz) {
        return SHAPES.get().ids()[facing.get2DDataValue()][cell(dx, dy, dz)];
    }

    /** The shape with that id. */
    public static VoxelShape shapeOf(int id) {
        VoxelShape[] all = SHAPES.get().shapes();
        return id >= 0 && id < all.length ? all[id] : Shapes.empty();
    }

    /** What of the forge, facing {@code facing}, lies in the block {@code (dx, dy, dz)} from its core. */
    public static VoxelShape shape(Direction facing, int dx, int dy, int dz) {
        return shapeOf(shapeId(facing, dx, dy, dz));
    }

    /** How many different shapes the forge's blocks take. */
    public static int shapeCount() {
        return SHAPES.get().shapes().length;
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

    /** Placed facing the player, only if its whole 21x22x21 space is clear (the player is told if it is not). */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos core = ctx.getClickedPos();
        boolean clear = core.getY() + LAYERS - 1 < level.getMaxBuildHeight();
        BlockPos blocked = null;
        for (BlockPos p : space(core)) {
            if (!clear) break;
            if (!level.getBlockState(p).canBeReplaced(ctx)) {
                clear = false;
                blocked = p;
            }
        }
        if (clear) {
            AABB whole = new AABB(core.getX() - REACH, core.getY(), core.getZ() - REACH, core.getX() + REACH + 1, core.getY() + LAYERS, core.getZ() + REACH + 1);
            clear = level.getEntities((net.minecraft.world.entity.Entity) null, whole, e -> e instanceof LivingEntity && e != ctx.getPlayer()).isEmpty()
                    && (ctx.getPlayer() == null || !ctx.getPlayer().getBoundingBox().intersects(whole));
        }
        if (!clear) {
            if (!level.isClientSide && ctx.getPlayer() != null) {
                String where = blocked == null ? "" : " (" + level.getBlockState(blocked).getBlock().getName().getString() + " in the way at "
                        + blocked.getX() + ", " + blocked.getY() + ", " + blocked.getZ() + ")";
                Component message = blocked == null ? Tr.c("The Worldforge needs a clear space 21 wide, 21 deep and 22 high in front of you")
                        : Tr.c("The Worldforge needs a clear space 21 wide, 21 deep and 22 high in front of you (%s in the way at %s)",
                        level.getBlockState(blocked).getBlock().getName(), blocked.getX() + ", " + blocked.getY() + ", " + blocked.getZ());
                ctx.getPlayer().displayClientMessage(message.copy().withStyle(s -> s.withColor(0xFF5A4A)), true);
                com.fantasyweapons.FantasyWeapons.LOGGER.debug("Worldforge not placed at {}: {}", core, where.isEmpty() ? "entity or height" : where);
            }
            return null;
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        placeParts(level, pos, state.getValue(FACING), false);
        if (level.getBlockEntity(pos) instanceof WorldforgeBlockEntity forge) forge.placed();
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.6f, 0.5f);
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.5f, 0.4f);
            level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.8f, 0.5f);
        }
    }

    /**
     * Fills the forge's space around a core at {@code core} with its parts. {@code repair}: only cells that are empty or hold a
     * part already are set (a forge loaded from an older version takes its present shape without eating what was built round it).
     */
    public void placeParts(Level level, BlockPos core, Direction facing, boolean repair) {
        WorldforgePartBlock part = ModBlocks.WORLDFORGE_PART.get();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < LAYERS; dy++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    p.set(core.getX() + dx, core.getY() + dy, core.getZ() + dz);
                    BlockState want = part.withShape(shapeId(facing, dx, dy, dz));
                    if (repair) {
                        BlockState have = level.getBlockState(p);
                        if (have == want || !(have.isAir() || have.is(part))) continue;
                    }
                    // placed without shape updates so the half-built forge does not take itself apart
                    level.setBlock(p, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    /** The core of the forge {@code pos} belongs to (a part or the core itself), or null if none is found. */
    @Nullable
    public static BlockPos findCore(Level level, BlockPos pos) {
        WorldforgeBlock forge = ModBlocks.WORLDFORGE.get();
        for (BlockPos core : WorldforgeBlockEntity.loaded(level)) {
            if (inside(core, pos) && level.getBlockState(core).is(forge)) return core;
        }
        // not among the loaded forges (its core's chunk may only just be loading): look round for it
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < LAYERS; dy++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    m.set(pos.getX() + dx, pos.getY() - dy, pos.getZ() + dz);
                    if (!level.isLoaded(m)) continue;
                    if (level.getBlockState(m).is(forge)) return m.immutable();
                }
            }
        }
        return null;
    }

    /** Broken, the forge gives back what lay on its grid, its Forged slot and what a forging under way had taken, and its parts go. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof WorldforgeBlockEntity forge) {
                for (var stack : forge.drops()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, stack);
            }
            if (!level.isClientSide) {
                WorldforgePartBlock part = ModBlocks.WORLDFORGE_PART.get();
                for (BlockPos p : space(pos)) {
                    if (p.equals(pos)) continue;
                    if (level.getBlockState(p).is(part)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
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
