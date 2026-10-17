package com.aigirlfriend.entity;

import com.aigirlfriend.registry.ModEntities;
import com.aigirlfriend.seat.SeatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Invisible, non-saved entity that players and companions ride to sit on chairs, stairs, slabs, carpets,
 * beds, or the top of any block. It removes itself as soon as it is empty or its block disappears, so it can
 * never leak or duplicate.
 */
public class SeatEntity extends Entity {
    private BlockPos seatBlock = BlockPos.ZERO;
    private int emptyTicks;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public static SeatEntity create(Level level, BlockPos block, Vec3 pos, float yaw) {
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), level);
        seat.seatBlock = block.immutable();
        seat.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
        return seat;
    }

    public BlockPos getSeatBlock() {
        return seatBlock;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (getPassengers().isEmpty()) {
            if (++emptyTicks > 2) discard();
            return;
        }
        emptyTicks = 0;
        if (tickCount % 10 == 0 && !SeatManager.isStillSeatable(level(), seatBlock)) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return Vec3.ZERO;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Direction facing = Direction.fromYRot(getYRot());
        for (Direction d : new Direction[]{facing, facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite()}) {
            BlockPos target = seatBlock.relative(d);
            for (BlockPos p : new BlockPos[]{target, target.below(), target.above()}) {
                Vec3 v = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), p, true);
                if (v != null) return v;
            }
        }
        Vec3 above = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), seatBlock.above(), true);
        return above != null ? above : new Vec3(getX(), seatBlock.getY() + 1.0, getZ());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (passenger instanceof GirlfriendEntity) {
            passenger.setYRot(getYRot());
        }
    }
}
