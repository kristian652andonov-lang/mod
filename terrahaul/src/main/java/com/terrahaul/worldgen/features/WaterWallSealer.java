package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.FluidState;

/**
 * The last safeguard against walls of water: still water (or lava) that stands beside or above an
 * open cell is sealed off with rock in that cell.
 * <p>
 * Water placed during generation does not flow until a block next to it changes, so wherever
 * something opens the ground beside a lake, a river or the sea after the water was decided (a
 * ravine or cave carver cutting along a shore, a feature digging next to the water), the water
 * stands as a vertical wall over the opening. The surface water feature seals the water it places
 * itself and the carvers keep away from it, but sea-level water and openings made by other mods'
 * features have no such guard. This feature runs last in every chunk (the
 * {@code top_layer_modification} step), when every block of the chunk is final, and looks at the
 * still water above the ground of each column of the chunk and the columns around it: any face
 * of a source block that touches an open cell (air or a plant water would wash away) beside it or
 * below it gets that cell filled with rock like the water's bed, so the water ends at a natural
 * shore instead of in mid-air. Deep water is checked below every block (a cave cut under a bed) but
 * sideways only near its bed and where the ground of a neighbouring column lies lower than it,
 * which keeps the cost in the open sea low. Does nothing outside Terrahaul worlds.
 */
public final class WaterWallSealer extends Feature<NoneFeatureConfiguration> {
    private static final Direction[] OPEN_SIDES = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    /** How far above its bed deep water is checked sideways for caves cutting in. */
    private static final int BED_REACH = 12;

    public WaterWallSealer(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (TerrahaulGeneration.model(level) == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        int seaLevel = context.chunkGenerator().getSeaLevel();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        boolean changed = false;
        // the chunk's columns and the ring around it: water just across the border may face an
        // opening in this chunk (all cells touched lie within the 3x3 chunk area)
        for (int dx = -1; dx <= 16; dx++) {
            for (int dz = -1; dz <= 16; dz++) {
                changed |= column(level, chunk.getMinBlockX() + dx, chunk.getMinBlockZ() + dz, seaLevel, pos, side);
            }
        }
        return changed;
    }

    private static boolean column(WorldGenLevel level, int x, int z, int seaLevel, BlockPos.MutableBlockPos pos, BlockPos.MutableBlockPos side) {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        if (surface <= floor) {
            return false;
        }
        // the lowest ground beside the column: water deeper than it may face an opening
        int lowestSide = Integer.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            lowestSide = Math.min(lowestSide, level.getHeight(Heightmap.Types.WORLD_SURFACE, x + d.getStepX(), z + d.getStepZ()) - 1);
        }
        BlockState bed = level.getBlockState(pos.set(x, floor, z));
        boolean changed = false;
        for (int y = surface; y > floor; y--) {
            FluidState fluid = level.getFluidState(pos.set(x, y, z));
            if (fluid.isEmpty() || !fluid.isSource()) {
                continue;
            }
            // below: always (a cave cut under a river or lake bed); the sides: at and above sea
            // level, where they face lower ground, and near the bed, where caves cut in
            boolean sides = y >= seaLevel - 1 || y > lowestSide || y <= floor + BED_REACH;
            for (Direction d : OPEN_SIDES) {
                if (d != Direction.DOWN && !sides) {
                    continue;
                }
                side.setWithOffset(pos, d);
                BlockState there = level.getBlockState(side);
                if (isOpen(there)) {
                    level.setBlock(side, sealFor(bed), 2);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Air, or a plant without water in it that water would wash away. */
    private static boolean isOpen(BlockState state) {
        return state.isAir() || state.getFluidState().isEmpty() && state.canBeReplaced() && !state.blocksMotion() && !state.is(Blocks.SNOW);
    }

    /** Rock like the water's bed: sand and gravel would fall, plants and ice do not hold water. */
    private static BlockState sealFor(BlockState bed) {
        if (bed.is(Blocks.SAND) || bed.is(Blocks.SANDSTONE)) {
            return Blocks.SANDSTONE.defaultBlockState();
        }
        if (bed.is(Blocks.RED_SAND) || bed.is(Blocks.RED_SANDSTONE)) {
            return Blocks.RED_SANDSTONE.defaultBlockState();
        }
        if (FloatingTerrainCleanup.isTerrain(bed) && bed.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
            && !bed.is(Blocks.GRAVEL) && !bed.is(Blocks.SNOW_BLOCK) && !bed.is(Blocks.PACKED_ICE) && !bed.is(Blocks.MAGMA_BLOCK)
            && !bed.is(net.minecraft.tags.BlockTags.DIRT)) {
            return bed;
        }
        return Blocks.STONE.defaultBlockState();
    }
}
