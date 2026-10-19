package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * A fallen trunk lying on the forest floor. It follows the ground, stops where the terrain drops
 * away (so it never bridges gaps in mid-air), and may keep a broken stump at its root end.
 * <p>
 * Decay comes in stretches rather than block by block: one section of the trunk may have lost its
 * bark and sunk a block into the ground, moss grows over one continuous run of its top, and
 * mushrooms sprout next to the rotten section. Nothing is placed in or removed from water.
 */
public final class FallenLogFeature extends Feature<FallenLogConfiguration> {
    public FallenLogFeature(Codec<FallenLogConfiguration> codec) {
        super(codec);
    }

    /** Air or plants a log may replace (not water: a log is not waterloggable). */
    private static boolean free(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isAir() || state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.REPLACEABLE_BY_TREES) && !state.is(BlockTags.LEAVES);
    }

    @Override
    public boolean place(FeaturePlaceContext<FallenLogConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        FallenLogConfiguration config = context.config();
        BlockPos origin = context.origin();
        BlockState ground = level.getBlockState(origin.below());
        if (!ground.is(BlockTags.DIRT) && !ground.is(BlockTags.SAND) && !ground.is(Blocks.GRAVEL)) {
            return false;
        }
        Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int length = config.length().sample(random);
        int baseY = origin.getY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // the whole log must find ground within one block of the start along its length
        int usable = 0;
        for (int i = 0; i < length; i++) {
            int x = origin.getX() + dir.getStepX() * i;
            int z = origin.getZ() + dir.getStepZ() * i;
            int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
            if (Math.abs(top - baseY) > 1 || !free(level.getBlockState(pos.set(x, top, z)))) {
                break;
            }
            usable++;
        }
        if (usable < 3) {
            return false;
        }
        // one rotten stretch (stripped and sunk), one mossy stretch
        int rotLength = random.nextFloat() < config.rot() * 2.0f ? 1 + random.nextInt(Math.min(3, usable - 1)) : 0;
        int rotStart = rotLength > 0 ? 1 + random.nextInt(Math.max(1, usable - rotLength)) : -1;
        int mossLength = random.nextFloat() < config.moss() * 1.6f ? 2 + random.nextInt(usable - 1) : 0;
        int mossStart = mossLength > 0 ? random.nextInt(Math.max(1, usable - mossLength + 1)) : -1;
        Direction.Axis axis = dir.getAxis();
        for (int i = 0; i < usable; i++) {
            int x = origin.getX() + dir.getStepX() * i;
            int z = origin.getZ() + dir.getStepZ() * i;
            int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
            boolean rotten = i >= rotStart && i < rotStart + rotLength;
            int y = rotten ? top - 1 : top;
            BlockState state = (rotten ? config.stripped() : config.log()).trySetValue(RotatedPillarBlock.AXIS, axis);
            level.setBlock(pos.set(x, y, z), state, 2);
            BlockPos above = pos.above();
            BlockState over = level.getBlockState(above);
            if (!free(over)) {
                continue;
            }
            boolean nearRot = rotLength > 0 && i >= rotStart - 1 && i <= rotStart + rotLength;
            if (i >= mossStart && i < mossStart + mossLength) {
                level.setBlock(above, Blocks.MOSS_CARPET.defaultBlockState(), 2);
            } else if (nearRot && random.nextFloat() < config.mushroom() * 4.0f) {
                level.setBlock(above, (random.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState(), 2);
            } else if (!over.isAir()) {
                // grass or flowers that the trunk now lies across
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        if (random.nextFloat() < config.stump()) {
            int x = origin.getX() - dir.getStepX();
            int z = origin.getZ() - dir.getStepZ();
            int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
            if (Math.abs(top - baseY) <= 1) {
                int h = 1 + random.nextInt(2);
                for (int dy = 0; dy < h; dy++) {
                    if (free(level.getBlockState(pos.set(x, top + dy, z)))) {
                        level.setBlock(pos, config.log().trySetValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), 2);
                    }
                }
            }
        }
        return true;
    }
}
