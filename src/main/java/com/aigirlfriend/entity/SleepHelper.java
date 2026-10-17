package com.aigirlfriend.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Bed geometry helpers shared by the sleep and cuddle systems. */
public final class SleepHelper {
    private SleepHelper() {}

    /** Returns the HEAD block of the bed at pos, or null if pos is not a bed. */
    @Nullable
    public static BlockPos bedHead(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return null;
        if (state.getValue(BedBlock.PART) == BedPart.HEAD) return pos;
        BlockPos head = pos.relative(state.getValue(BedBlock.FACING));
        return level.getBlockState(head).getBlock() instanceof BedBlock ? head : null;
    }

    public static boolean isOccupied(Level level, BlockPos head) {
        BlockState state = level.getBlockState(head);
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.OCCUPIED);
    }

    @Nullable
    public static Direction facing(Level level, BlockPos head) {
        BlockState state = level.getBlockState(head);
        return state.getBlock() instanceof BedBlock ? state.getValue(BedBlock.FACING) : null;
    }

    /** A free bed directly beside this one with the same orientation (a "double bed"), or null. */
    @Nullable
    public static BlockPos adjacentFreeBed(Level level, BlockPos head) {
        Direction facing = facing(level, head);
        if (facing == null) return null;
        for (Direction side : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
            BlockPos other = head.relative(side);
            BlockState st = level.getBlockState(other);
            if (st.getBlock() instanceof BedBlock && st.getValue(BedBlock.PART) == BedPart.HEAD
                    && st.getValue(BedBlock.FACING) == facing && !st.getValue(BedBlock.OCCUPIED)) return other;
        }
        return null;
    }

    /** Where she lies when sharing one bed: shifted towards one edge so both fit. */
    public static Vec3 besidePosition(Level level, BlockPos head, boolean rightSide) {
        Direction facing = facing(level, head);
        if (facing == null) facing = Direction.NORTH;
        Direction side = rightSide ? facing.getClockWise() : facing.getCounterClockWise();
        return new Vec3(head.getX() + 0.5 + side.getStepX() * 0.42, head.getY() + 0.6875, head.getZ() + 0.5 + side.getStepZ() * 0.42);
    }

    public static Vec3 findStandUpPosition(LivingEntity entity, BlockPos bed) {
        Level level = entity.level();
        BlockState state = level.getBlockState(bed);
        if (state.getBlock() instanceof BedBlock) {
            return BedBlock.findStandUpPosition(entity.getType(), level, bed, state.getValue(BedBlock.FACING), entity.getYRot())
                    .orElseGet(() -> Vec3.atBottomCenterOf(bed.above()));
        }
        return Vec3.atBottomCenterOf(bed.above());
    }
}
