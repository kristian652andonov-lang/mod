package com.fantasyweapons.forge;

import com.fantasyweapons.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of the Worldforge's space besides its core, knowing where it lies from the core ({@link #X}, {@link #Y},
 * {@link #Z}, each 0 to 6 with the core at 3, 0, 3) and which way the forge faces. Invisible (the core draws the forge);
 * shaped like the part of the model inside it; using it opens the forge, breaking it breaks the whole forge (which drops
 * as one item), and it goes when its core does.
 */
public class WorldforgePartBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X = IntegerProperty.create("x", 0, 6);
    public static final IntegerProperty Y = IntegerProperty.create("y", 0, 6);
    public static final IntegerProperty Z = IntegerProperty.create("z", 0, 6);

    public WorldforgePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(X, 0).setValue(Y, 0).setValue(Z, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, X, Y, Z);
    }

    /** The part {@code (dx, dy, dz)} from the core of a forge facing {@code facing}. */
    public BlockState at(Direction facing, int dx, int dy, int dz) {
        return defaultBlockState().setValue(FACING, facing).setValue(X, dx + WorldforgeBlock.REACH).setValue(Y, dy).setValue(Z, dz + WorldforgeBlock.REACH);
    }

    public BlockPos core(BlockState state, BlockPos pos) {
        return pos.offset(WorldforgeBlock.REACH - state.getValue(X), -state.getValue(Y), WorldforgeBlock.REACH - state.getValue(Z));
    }

    private static VoxelShape shape(BlockState state) {
        return WorldforgeBlock.shape(state.getValue(FACING), state.getValue(X) - WorldforgeBlock.REACH, state.getValue(Y), state.getValue(Z) - WorldforgeBlock.REACH);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1f;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return ModBlocks.WORLDFORGE.get().owns(level, core(state, pos), state.getValue(FACING)) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return ModBlocks.WORLDFORGE.get().open(level, core(state, pos), player);
    }

    /** Breaking any part breaks the whole forge, which drops as one item. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos core = core(state, pos);
        if (!level.isClientSide && ModBlocks.WORLDFORGE.get().owns(level, core, state.getValue(FACING))) {
            if (!player.isCreative() && player.hasCorrectToolForDrops(state)) Block.popResource(level, core.above(), new ItemStack(ModBlocks.WORLDFORGE.get()));
            level.setBlock(core, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModBlocks.WORLDFORGE.get());
    }
}
