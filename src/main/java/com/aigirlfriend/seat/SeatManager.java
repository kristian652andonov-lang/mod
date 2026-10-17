package com.aigirlfriend.seat;

import com.aigirlfriend.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Finds and creates seats on blocks. Server side. */
public final class SeatManager {
    private SeatManager() {}

    /** Chair-like blocks you can sit on with a simple right-click. */
    public static boolean isChairLike(BlockState state) {
        if (state.getBlock() instanceof StairBlock) return state.getValue(StairBlock.HALF) == Half.BOTTOM;
        if (state.getBlock() instanceof SlabBlock) return state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        if (state.getBlock() instanceof CarpetBlock) return true;
        return state.is(BlockTags.WOOL_CARPETS);
    }

    /** Height of the sitting surface above the block's y, or NaN if one cannot sit here. */
    public static double surfaceHeight(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return Double.NaN;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return Double.NaN;
        double top = shape.max(Direction.Axis.Y);
        if (state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM) top = 0.5;
        if (state.getBlock() instanceof BedBlock) top = 0.5625;
        if (top > 1.01) return Double.NaN; // fences, walls
        // need head room for a seated character
        BlockPos above = pos.above();
        if (top >= 0.99) {
            if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) return Double.NaN;
        } else if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
            return Double.NaN;
        }
        BlockPos above2 = above.above();
        VoxelShape s2 = level.getBlockState(above2).getCollisionShape(level, above2);
        if (top >= 0.99 && !s2.isEmpty() && s2.min(Direction.Axis.Y) < 0.4) return Double.NaN;
        return top;
    }

    public static boolean isStillSeatable(Level level, BlockPos pos) {
        return !Double.isNaN(surfaceHeight(level, pos));
    }

    public static boolean isOccupied(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(SeatEntity.class, new AABB(pos).inflate(0.1), s -> s.getSeatBlock().equals(pos) && !s.getPassengers().isEmpty()).isEmpty();
    }

    /** Facing direction for a seat: stairs face away from their back, others keep the sitter's yaw. */
    public static float seatYaw(BlockState state, float fallbackYaw) {
        if (state.getBlock() instanceof StairBlock) return state.getValue(StairBlock.FACING).getOpposite().toYRot();
        return fallbackYaw;
    }

    /** Makes the entity sit on the given block. Returns the seat, or null if not possible. */
    @Nullable
    public static SeatEntity sit(LivingEntity entity, BlockPos pos, float fallbackYaw) {
        Level level = entity.level();
        double h = surfaceHeight(level, pos);
        if (Double.isNaN(h) || isOccupied(level, pos)) return null;
        BlockState state = level.getBlockState(pos);
        float yaw = seatYaw(state, fallbackYaw);
        double ox = 0, oz = 0;
        if (state.getBlock() instanceof StairBlock) {
            Direction back = state.getValue(StairBlock.FACING);
            ox = back.getStepX() * 0.12;
            oz = back.getStepZ() * 0.12;
        }
        Vec3 seatPos = new Vec3(pos.getX() + 0.5 + ox, pos.getY() + h, pos.getZ() + 0.5 + oz);
        if (entity.isPassenger()) entity.stopRiding();
        SeatEntity seat = SeatEntity.create(level, pos, seatPos, yaw);
        if (!level.addFreshEntity(seat)) return null;
        if (!entity.startRiding(seat, true)) {
            seat.discard();
            return null;
        }
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.yBodyRot = yaw;
        return seat;
    }

    /** Sits on whatever the entity is standing on. */
    @Nullable
    public static SeatEntity sitHere(LivingEntity entity) {
        BlockPos feet = BlockPos.containing(entity.getX(), entity.getY() - 0.2, entity.getZ());
        return sit(entity, feet, entity.getYRot());
    }

    public static boolean isSeated(LivingEntity entity) {
        return entity.getVehicle() instanceof SeatEntity;
    }

    public static void standUp(LivingEntity entity) {
        if (entity.getVehicle() instanceof SeatEntity seat) {
            entity.stopRiding();
            seat.discard();
        }
    }

    /**
     * Candidate seats next to another seat, ordered so the closest side-by-side spots come first.
     * Used by the companion to sit next to her owner.
     */
    public static List<BlockPos> seatsBeside(Level level, BlockPos ownerSeat, float ownerYaw) {
        List<BlockPos> result = new ArrayList<>();
        Direction facing = Direction.fromYRot(ownerYaw);
        Direction[] sides = {facing.getClockWise(), facing.getCounterClockWise()};
        for (Direction side : sides) {
            for (int dy : new int[]{0, -1, 1}) {
                BlockPos p = ownerSeat.relative(side).offset(0, dy, 0);
                if (!Double.isNaN(surfaceHeight(level, p)) && !isOccupied(level, p)) result.add(p);
            }
        }
        // further chairs around (a table setting)
        for (BlockPos p : BlockPos.betweenClosed(ownerSeat.offset(-2, -1, -2), ownerSeat.offset(2, 1, 2))) {
            if (p.equals(ownerSeat)) continue;
            BlockState st = level.getBlockState(p);
            if (isChairLike(st) && !result.contains(p) && !isOccupied(level, p) && !Double.isNaN(surfaceHeight(level, p))) result.add(p.immutable());
        }
        return result;
    }

    /** Height of the surface relative to the y the sitter will be at: used to keep paired seats level. */
    public static double absoluteSeatY(Level level, BlockPos pos) {
        double h = surfaceHeight(level, pos);
        return Double.isNaN(h) ? Double.NaN : pos.getY() + h;
    }
}
