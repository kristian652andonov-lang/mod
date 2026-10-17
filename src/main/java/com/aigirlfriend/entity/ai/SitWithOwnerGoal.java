package com.aigirlfriend.entity.ai;

import com.aigirlfriend.companion.CommandMode;
import com.aigirlfriend.companion.RelationshipStage;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.entity.SeatEntity;
import com.aigirlfriend.interaction.InteractionType;
import com.aigirlfriend.seat.SeatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/** When the owner sits down on a chair, bench, slab or the ground, she comes and sits next to them. */
public class SitWithOwnerGoal extends Goal {
    private final GirlfriendEntity gf;
    @Nullable private ServerPlayer owner;
    @Nullable private BlockPos seat;
    private int timer;
    private int repath;

    public SitWithOwnerGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (gf.tickCount % 10 != 0) return false;
        CommandMode cmd = gf.getCommand();
        if (cmd != CommandMode.FOLLOW && cmd != CommandMode.STAY_SAFE) return false;
        if (gf.isSeated() || gf.isPassenger() || gf.isSleeping() || gf.isFainted() || gf.interactions().isActive()) return false;
        if (gf.controller().fear.uneasy()) return false;
        if (!(gf.getOwner() instanceof ServerPlayer sp) || !(sp.getVehicle() instanceof SeatEntity ownerSeat) || sp.distanceTo(gf) > 12) return false;
        List<BlockPos> spots = SeatManager.seatsBeside(gf.level(), ownerSeat.getSeatBlock(), ownerSeat.getYRot());
        if (spots.isEmpty()) return false;
        owner = sp;
        seat = spots.get(0);
        return true;
    }

    @Override
    public void start() {
        timer = 0;
        repath = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null && seat != null && timer < 200 && !gf.isSeated() && SeatManager.isSeated(owner) && !gf.controller().fear.uneasy();
    }

    @Override
    public void stop() {
        owner = null;
        seat = null;
        gf.getNavigation().stop();
    }

    @Override
    public void tick() {
        timer++;
        if (owner == null || seat == null) return;
        gf.getLookControl().setLookAt(owner, 30f, 30f);
        double dist = gf.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(seat));
        if (dist > 1.6) {
            if (--repath <= 0) {
                repath = 10;
                gf.getNavigation().moveTo(seat.getX() + 0.5, seat.getY() + 1, seat.getZ() + 0.5, 1.0);
            }
            return;
        }
        gf.getNavigation().stop();
        boolean closeEnough = gf.data().relationship.stage().atLeast(RelationshipStage.FRIENDSHIP);
        if (closeEnough && gf.interactions().start(InteractionType.SIT_TOGETHER, owner, true)) {
            gf.controller().lastInitiativeTick = gf.level().getGameTime() - 1200;
        } else {
            SeatManager.sit(gf, seat, owner.getVehicle() != null ? owner.getVehicle().getYRot() : gf.getYRot());
        }
        timer = 1000;
    }
}
