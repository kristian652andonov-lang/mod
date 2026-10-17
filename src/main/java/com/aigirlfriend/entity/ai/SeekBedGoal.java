package com.aigirlfriend.entity.ai;

import com.aigirlfriend.companion.CommandMode;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.entity.SleepHelper;
import com.aigirlfriend.seat.SeatManager;
import com.aigirlfriend.util.WorldClock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Rest: at night, when she is at home or the owner is away/asleep without her, she finds a free bed and sleeps.
 * When exhausted during the day she sits down to rest whenever the owner stops for a while.
 */
public class SeekBedGoal extends Goal {
    private final GirlfriendEntity gf;
    @Nullable private BlockPos bed;
    private int timer;
    private int repath;

    public SeekBedGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (gf.tickCount % 40 != 0) return false;
        if (gf.isSleeping() || gf.isPassenger() || gf.isFainted() || gf.interactions().isActive() || gf.controller().fear.uneasy()) return false;
        if (restIfExhausted()) return false;
        if (!WorldClock.isNight(gf.level()) || gf.data().needs.energy > 85f) return false;
        Player owner = gf.getOwner();
        boolean ownerAway = owner == null || owner.distanceTo(gf) > 24 || owner.isSleeping();
        CommandMode c = gf.getCommand();
        if (!(c == CommandMode.HOME || ownerAway || (c == CommandMode.WAIT && gf.controller().isNearHome(12)))) return false;
        bed = findBed();
        return bed != null;
    }

    private boolean restIfExhausted() {
        if (!gf.data().needs.isExhausted() || gf.isSeated()) {
            if (gf.hasFlag(GirlfriendEntity.FLAG_GROUND_SIT) && gf.getCommand() != CommandMode.SIT
                    && (gf.data().needs.energy > 30f || ownerMovedAway())) gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, false);
            return false;
        }
        Player owner = gf.getOwner();
        if (owner != null && owner.distanceTo(gf) < 6 && gf.level().getGameTime() - gf.controller().ownerStillSince > 200 && gf.onGround()) {
            if (SeatManager.sitHere(gf) == null) gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, true);
            return true;
        }
        return false;
    }

    private boolean ownerMovedAway() {
        Player owner = gf.getOwner();
        return owner == null || owner.distanceTo(gf) > 6;
    }

    @Nullable
    private BlockPos findBed() {
        BlockPos center = gf.data().home != null && gf.controller().isNearHome(24) ? gf.data().home : gf.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-12, -3, -12), center.offset(12, 3, 12))) {
            BlockPos head = SleepHelper.bedHead(gf.level(), p);
            if (head == null || SleepHelper.isOccupied(gf.level(), head)) continue;
            double d = head.distSqr(gf.blockPosition());
            if (d < bestDist) {
                bestDist = d;
                best = head.immutable();
            }
        }
        return best;
    }

    @Override
    public void start() {
        timer = 0;
        repath = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return bed != null && timer < 600 && !gf.isSleeping() && !gf.controller().fear.uneasy() && !gf.interactions().isActive();
    }

    @Override
    public void stop() {
        bed = null;
        gf.getNavigation().stop();
    }

    @Override
    public void tick() {
        timer++;
        if (bed == null) return;
        Vec3 c = Vec3.atBottomCenterOf(bed);
        if (gf.position().distanceTo(c) > 1.8) {
            if (--repath <= 0) {
                repath = 20;
                gf.getNavigation().moveTo(c.x, c.y, c.z, 0.9);
            }
            return;
        }
        if (SleepHelper.isOccupied(gf.level(), bed)) {
            bed = null;
            return;
        }
        if (gf.isSeated()) SeatManager.standUp(gf);
        gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, false);
        gf.startSleeping(bed);
        gf.controller().say(com.aigirlfriend.dialogue.DialogueTrigger.GOODNIGHT);
    }
}
