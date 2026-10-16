package com.fantasyweapons.forge;

import com.fantasyweapons.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of the Worldforge's space besides its core. All it holds is the shape of the part of the model inside it
 * (an id into the forge's table of shapes, split over two properties so the game's tables of block states stay small):
 * it is invisible (the core draws the forge), shaped like that part, using it opens the forge, breaking it breaks the
 * whole forge (which drops as one item), and it goes when its core does.
 */
public class WorldforgePartBlock extends Block {
    /** The shape id, as its high and low digits in base {@link #BASE}. */
    public static final IntegerProperty HI = IntegerProperty.create("hi", 0, 63), LO = IntegerProperty.create("lo", 0, 63);
    private static final int BASE = 64;
    /** How many shapes the parts can hold. */
    public static final int CAPACITY = BASE * BASE;

    public WorldforgePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HI, 0).setValue(LO, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HI, LO);
    }

    /** The part holding the shape with id {@code id}. */
    public BlockState withShape(int id) {
        return defaultBlockState().setValue(HI, id / BASE).setValue(LO, id % BASE);
    }

    public static int shapeId(BlockState state) {
        return state.getValue(HI) * BASE + state.getValue(LO);
    }

    /** Whether this part holds some of the model (and so can be seen to, aimed at and stood on). */
    public static boolean solid(BlockState state) {
        return shapeId(state) != 0;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return WorldforgeBlock.shapeOf(shapeId(state));
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos core = WorldforgeBlock.findCore(level, pos);
        return core == null ? InteractionResult.PASS : ModBlocks.WORLDFORGE.get().open(level, core, player);
    }

    /** Breaking any part breaks the whole forge, which drops as one item. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos core = WorldforgeBlock.findCore(level, pos);
            if (core != null) {
                if (!player.isCreative() && player.hasCorrectToolForDrops(state)) Block.popResource(level, core.above(), new ItemStack(ModBlocks.WORLDFORGE.get()));
                level.setBlock(core, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModBlocks.WORLDFORGE.get());
    }
}
