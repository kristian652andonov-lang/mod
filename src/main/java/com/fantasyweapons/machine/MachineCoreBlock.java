package com.fantasyweapons.machine;

import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The core of a machine three blocks wide and two high (the Arcane Refinery, the Legendary Crafting Table): the bottom
 * middle block, which owns the block entity and draws the whole machine with its GeckoLib model. The other five blocks
 * are {@link MachinePartBlock}s; the machine is placed facing the player when there is room for all six and taken apart
 * as a whole.
 */
public abstract class MachineCoreBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final Supplier<? extends MachinePartBlock> parts;
    /** Per facing: the outline and the collision shape of each block of the machine (the core's last). */
    private final Supplier<VoxelShape[][][]> shapes = Suppliers.memoize(this::bakeShapes);

    protected MachineCoreBlock(Properties properties, Supplier<? extends MachinePartBlock> parts) {
        super(properties);
        this.parts = parts;
    }

    /**
     * The whole machine's shape when it faces north, in the core's block coordinates (so it reaches into the other five
     * blocks, and may stand a little past them where the model does): by default the full three by two blocks.
     */
    protected VoxelShape northShape() {
        return Block.box(-16, 0, 0, 32, 32, 16);
    }

    /** The outline of the machine (the whole of it) as seen from the block at {@code part} ({@code null}: the core). */
    public VoxelShape outline(Direction facing, @Nullable MachinePart part) {
        return shapes.get()[facing.get2DDataValue()][part == null ? MachinePart.values().length : part.ordinal()][0];
    }

    /** What of the machine is solid inside the block at {@code part} ({@code null}: the core). */
    public VoxelShape collision(Direction facing, @Nullable MachinePart part) {
        return shapes.get()[facing.get2DDataValue()][part == null ? MachinePart.values().length : part.ordinal()][1];
    }

    private VoxelShape[][][] bakeShapes() {
        VoxelShape north = northShape();
        VoxelShape[][][] baked = new VoxelShape[4][MachinePart.values().length + 1][];
        for (int f = 0; f < 4; f++) {
            Direction facing = Direction.from2DDataValue(f);
            VoxelShape whole = Shapes.empty();
            for (AABB box : north.toAabbs()) whole = Shapes.or(whole, Shapes.create(turn(box, facing)));
            for (int i = 0; i <= MachinePart.values().length; i++) {
                BlockPos at = i == MachinePart.values().length ? BlockPos.ZERO : MachinePart.values()[i].from(BlockPos.ZERO, facing);
                VoxelShape outline = whole.move(-at.getX(), -at.getY(), -at.getZ()).optimize();
                baked[f][i] = new VoxelShape[]{outline, Shapes.join(outline, Shapes.block(), BooleanOp.AND)};
            }
        }
        return baked;
    }

    /** A box of the north-facing shape turned to face {@code facing} about the core's centre (as the model is turned). */
    private static AABB turn(AABB box, Direction facing) {
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return outline(state.getValue(FACING), null);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return collision(state.getValue(FACING), null);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** Opens the machine (whose core is at {@code core}) for {@code player}. */
    public abstract InteractionResult open(Level level, BlockPos core, Player player);

    /** Placed facing the player, if all five other blocks of it have room. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() + 1 >= level.getMaxBuildHeight()) return null;
        for (MachinePart part : MachinePart.values()) {
            if (!level.getBlockState(part.from(pos, facing)).canBeReplaced(ctx)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        placeParts(level, pos, state.getValue(FACING));
    }

    /** Puts the five parts around a core at {@code core}. */
    public void placeParts(Level level, BlockPos core, Direction facing) {
        MachinePartBlock block = parts.get();
        for (MachinePart part : MachinePart.values()) {
            // placed without shape updates so the half-built structure does not take itself apart
            level.setBlock(part.from(core, facing), block.defaultBlockState().setValue(MachinePartBlock.FACING, facing).setValue(MachinePartBlock.PART, part),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (MachinePart part : MachinePart.values()) level.blockUpdated(part.from(core, facing), block);
    }

    /**
     * A core found without its parts (a Legendary Crafting Table placed before it was three blocks wide) builds them where
     * there is room for them, and is otherwise taken down and dropped as its item. Scheduled by the block entity on load.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        if (intact(level, pos, facing)) return;
        boolean room = pos.getY() + 1 < level.getMaxBuildHeight();
        for (MachinePart part : MachinePart.values()) {
            BlockState s = level.getBlockState(part.from(pos, facing));
            room &= s.canBeReplaced() || (s.getBlock() == parts.get() && s.getValue(MachinePartBlock.FACING) == facing && s.getValue(MachinePartBlock.PART) == part);
        }
        if (room) {
            placeParts(level, pos, facing);
        } else {
            Block.popResource(level, pos, new ItemStack(this));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    /** Whether all six blocks of the machine whose core is at {@code core} are in place. */
    public boolean intact(BlockGetter level, BlockPos core, Direction facing) {
        BlockState c = level.getBlockState(core);
        if (c.getBlock() != this || c.getValue(FACING) != facing) return false;
        for (MachinePart part : MachinePart.values()) {
            BlockState s = level.getBlockState(part.from(core, facing));
            if (s.getBlock() != parts.get() || s.getValue(MachinePartBlock.FACING) != facing || s.getValue(MachinePartBlock.PART) != part) return false;
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

    /** The comparator signal of the machine (read through any of its blocks). */
    public int signal(BlockState state, Level level, BlockPos pos) {
        return 0;
    }
}
