package com.aigirlfriend.entity.ai;

import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.dialogue.DialogueTrigger;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.entity.SleepHelper;
import com.aigirlfriend.interaction.InteractionManager;
import com.aigirlfriend.interaction.InteractionType;
import com.aigirlfriend.util.WorldClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Bedtime (romantic stage and up): she takes the owner's hand and leads them to bed herself, then lies down
 * and waits for them. The owner can let go at any time by sneaking; she never forces them to sleep.
 */
public class LeadToBedGoal extends Goal {
    private enum Phase { APPROACH, LEAD, DONE }

    private final GirlfriendEntity gf;
    @Nullable private ServerPlayer owner;
    @Nullable private BlockPos bed;
    private Phase phase = Phase.APPROACH;
    private int timer;
    private int repath;

    public LeadToBedGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        var c = gf.controller();
        boolean requested = c.bedtimeRequested;
        if (!requested) {
            if (!GFConfig.LEAD_TO_BED.get() || !gf.data().relationship.stage().isRomantic()) return false;
            if (!WorldClock.isBedtime(gf.level()) || c.lastBedtimeLeadDay == WorldClock.day(gf.level())) return false;
            if (gf.tickCount % 40 != 0) return false;
        }
        if (gf.isSleeping() || gf.isFainted() || gf.isPassenger() || c.fear.uneasy()) return false;
        if (gf.interactions().isActive() && gf.interactions().current() != InteractionType.HOLD_HANDS) return false;
        if (!(gf.getOwner() instanceof ServerPlayer sp) || sp.isSleeping() || sp.distanceTo(gf) > 16) return false;
        BlockPos found = findBed(sp);
        if (found == null) {
            if (requested) {
                c.bedtimeRequested = false;
                c.say(DialogueTrigger.SLEEP_DECLINED, java.util.Map.of("nobed", "1"));
            }
            return false;
        }
        owner = sp;
        bed = found;
        return true;
    }

    @Nullable
    private BlockPos findBed(ServerPlayer sp) {
        BlockPos home = gf.data().home;
        if (home != null && gf.controller().isHomeDimension() && home.closerToCenterThan(gf.position(), 32)) {
            for (BlockPos p : BlockPos.betweenClosed(home.offset(-6, -2, -6), home.offset(6, 2, 6))) {
                BlockPos head = SleepHelper.bedHead(gf.level(), p);
                if (head != null && !SleepHelper.isOccupied(gf.level(), head)) return head.immutable();
            }
        }
        return gf.interactions().findBedNear(sp, 24);
    }

    @Override
    public void start() {
        phase = Phase.APPROACH;
        timer = 0;
        gf.controller().bedtimeRequested = false;
        gf.controller().lastBedtimeLeadDay = WorldClock.day(gf.level());
    }

    @Override
    public boolean canContinueToUse() {
        if (owner == null || bed == null || phase == Phase.DONE || !owner.isAlive() || owner.level() != gf.level()) return false;
        if (gf.controller().fear.afraid() || gf.isFainted()) return false;
        if (phase == Phase.LEAD && gf.interactions().current() != InteractionType.LEAD_TO_BED) return false;
        return timer < 2400;
    }

    @Override
    public void stop() {
        if (phase == Phase.LEAD) {
            if (gf.interactions().current() == InteractionType.LEAD_TO_BED) {
                gf.interactions().stop(InteractionManager.StopReason.CANCELLED_SELF);
            } else {
                // the owner let go of her hand: respect it
                gf.controller().say(DialogueTrigger.SLEEP_DECLINED);
            }
        }
        gf.getNavigation().stop();
        owner = null;
        bed = null;
    }

    @Override
    public void tick() {
        timer++;
        if (owner == null || bed == null) return;
        switch (phase) {
            case APPROACH -> {
                gf.getLookControl().setLookAt(owner, 30f, 30f);
                if (gf.distanceTo(owner) > 1.8) {
                    if (--repath <= 0) {
                        repath = 10;
                        gf.getNavigation().moveTo(owner, 1.1);
                    }
                } else {
                    if (gf.interactions().current() == InteractionType.HOLD_HANDS) gf.interactions().stop(InteractionManager.StopReason.REPLACED);
                    if (gf.interactions().start(InteractionType.LEAD_TO_BED, owner, true)) {
                        gf.controller().say(DialogueTrigger.LEAD_TO_BED);
                        phase = Phase.LEAD;
                        repath = 0;
                    } else {
                        phase = Phase.DONE;
                    }
                }
            }
            case LEAD -> {
                Vec3 bedCenter = Vec3.atBottomCenterOf(bed);
                double toBed = gf.position().distanceTo(bedCenter);
                double ownerGap = gf.distanceTo(owner);
                if (toBed < 1.6) {
                    gf.interactions().stop(InteractionManager.StopReason.COMPLETED);
                    lieDown();
                    phase = Phase.DONE;
                    return;
                }
                if (ownerGap > 3.2) {
                    gf.getNavigation().stop();
                    gf.getLookControl().setLookAt(owner, 30f, 30f);
                } else if (--repath <= 0) {
                    repath = 10;
                    gf.getNavigation().moveTo(bedCenter.x, bedCenter.y, bedCenter.z, 0.8);
                    if (gf.getNavigation().isDone() && toBed > 3) {
                        // cannot path there: give up gracefully
                        gf.interactions().stop(InteractionManager.StopReason.CANCELLED_SELF);
                        phase = Phase.DONE;
                    }
                }
                if (gf.tickCount % 60 < 12) gf.getLookControl().setLookAt(owner, 30f, 30f);
            }
            case DONE -> {}
        }
    }

    private void lieDown() {
        if (bed == null || owner == null) return;
        gf.startSleepingBeside(bed, SleepHelper.besidePosition(gf.level(), bed));
        gf.controller().waitingBed = bed;
        gf.controller().waitingBedSince = gf.level().getGameTime();
        gf.controller().say(DialogueTrigger.GOODNIGHT, java.util.Map.of("waiting", "1"));
    }
}
