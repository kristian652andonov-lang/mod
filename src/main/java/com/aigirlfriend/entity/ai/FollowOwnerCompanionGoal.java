package com.aigirlfriend.entity.ai;

import com.aigirlfriend.companion.CommandMode;
import com.aigirlfriend.companion.Teleport;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.seat.SeatManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Follows at a comfortable distance, slightly behind and to one side (never onto the player's position).
 * Paths are only recomputed when the owner actually moved; real stuck detection triggers safe teleport
 * recovery, which is also used when she gets hopelessly separated.
 */
public class FollowOwnerCompanionGoal extends Goal {
    private final GirlfriendEntity gf;
    @Nullable private Player owner;
    private int repath;
    private boolean leftSide;
    private Vec3 lastOwnerPos = Vec3.ZERO;
    private double bestDistance;
    private int noProgressTicks;
    private long lastTeleport;

    public FollowOwnerCompanionGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean active() {
        CommandMode c = gf.getCommand();
        return c == CommandMode.FOLLOW || c == CommandMode.STAY_SAFE;
    }

    private double startDistance() {
        double d = GFConfig.FOLLOW_START.get();
        if (gf.getCommand() == CommandMode.STAY_SAFE) d += 3;
        if (gf.controller().fear.uneasy()) d *= 0.6;
        return d;
    }

    private double stopDistance() {
        double d = GFConfig.FOLLOW_STOP.get();
        if (gf.getCommand() == CommandMode.STAY_SAFE) d += 2.5;
        if (gf.data().personality.independence > 0.7f) d += 0.8;
        return d;
    }

    @Override
    public boolean canUse() {
        if (!active() || gf.isPassenger() || gf.isSleeping() || gf.isFainted() || gf.interactions().isActive()) return false;
        if (gf.hasFlag(GirlfriendEntity.FLAG_GROUND_SIT)) return false;
        Player o = gf.getOwner();
        if (o == null) return false;
        if (gf.distanceTo(o) < startDistance()) return false;
        owner = o;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (owner == null || !owner.isAlive() || owner.level() != gf.level() || !active() || gf.isPassenger() || gf.interactions().isActive()) return false;
        return gf.distanceTo(owner) > stopDistance();
    }

    @Override
    public void start() {
        repath = 0;
        leftSide = gf.getRandom().nextBoolean();
        bestDistance = Double.MAX_VALUE;
        noProgressTicks = 0;
        if (gf.isSeated()) SeatManager.standUp(gf);
    }

    @Override
    public void stop() {
        owner = null;
        gf.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (owner == null) return;
        gf.getLookControl().setLookAt(owner, 10f, gf.getMaxHeadXRot());
        double dist = gf.distanceTo(owner);

        if (GFConfig.TELEPORT_RECOVERY.get() && dist > GFConfig.TELEPORT_DISTANCE.get() && gf.level().getGameTime() - lastTeleport > 60) {
            if (Teleport.nearPlayer(gf, owner)) {
                lastTeleport = gf.level().getGameTime();
                resetProgress();
                return;
            }
        }
        // stuck detection
        if (dist < bestDistance - 0.5) {
            bestDistance = dist;
            noProgressTicks = 0;
        } else if (++noProgressTicks > GFConfig.STUCK_SECONDS.get() * 20) {
            noProgressTicks = 0;
            bestDistance = dist;
            if (GFConfig.TELEPORT_RECOVERY.get() && dist > 8 && Teleport.nearPlayer(gf, owner)) {
                lastTeleport = gf.level().getGameTime();
                resetProgress();
                return;
            }
            leftSide = !leftSide;
            gf.getJumpControl().jump();
            repath = 0;
        }

        boolean ownerMoved = owner.position().distanceToSqr(lastOwnerPos) > 1.0;
        if (--repath <= 0 || ownerMoved && repath < 6 || gf.getNavigation().isDone()) {
            repath = 10;
            lastOwnerPos = owner.position();
            Vec3 target = followPoint(owner);
            double speed = dist > 10 ? 1.35 : dist > 6 ? 1.15 : 1.0;
            if (gf.data().needs.isExhausted()) speed *= 0.85;
            if (!gf.getNavigation().moveTo(target.x, target.y, target.z, speed)) gf.getNavigation().moveTo(owner, speed);
        }
    }

    private void resetProgress() {
        bestDistance = Double.MAX_VALUE;
        noProgressTicks = 0;
        repath = 0;
    }

    /** A spot a little behind the owner, on her preferred side. */
    private Vec3 followPoint(Player o) {
        float yaw = o.getYRot() * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw));
        Vec3 side = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(leftSide ? -1 : 1);
        double behind = gf.getCommand() == CommandMode.STAY_SAFE ? 3.5 : 1.4;
        return o.position().add(back.scale(behind)).add(side.scale(1.1));
    }
}
