package com.aigirlfriend.entity.ai;

import com.aigirlfriend.companion.CommandMode;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.fear.FearState;
import com.aigirlfriend.seat.SeatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Avoidance first: when afraid she hides behind her owner (on the side away from the threat).
 * If the owner is not around, or the threat is right on top of her, she flees to a safer (lit, covered) spot.
 * Critically injured, she retreats and asks for help.
 */
public class FearResponseGoal extends Goal {
    private final GirlfriendEntity gf;
    private int recalc;
    @Nullable private Vec3 target;

    public FearResponseGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean shouldReact() {
        if (gf.isFainted() || gf.isPassenger() || gf.isSleeping()) return false;
        FearState f = gf.controller().fear;
        if (f.threatCenter == null) return false;
        boolean critical = gf.hasFlag(GirlfriendEntity.FLAG_CRITICAL) && f.uneasy();
        boolean staySafe = gf.getCommand() == CommandMode.STAY_SAFE && f.uneasy();
        return f.afraid() || critical || staySafe;
    }

    @Override
    public boolean canUse() {
        return shouldReact();
    }

    @Override
    public boolean canContinueToUse() {
        return shouldReact() || (target != null && !gf.getNavigation().isDone() && gf.controller().fear.level > 0.1f);
    }

    @Override
    public void start() {
        recalc = 0;
        if (gf.isSeated()) SeatManager.standUp(gf);
        gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, false);
    }

    @Override
    public void stop() {
        target = null;
        gf.setFlag(GirlfriendEntity.FLAG_HIDING, false);
        gf.getNavigation().stop();
    }

    @Override
    public void tick() {
        FearState f = gf.controller().fear;
        if (f.mainThreat != null && f.mainThreat.isAlive()) gf.getLookControl().setLookAt(f.mainThreat, 30f, 30f);
        if (--recalc > 0) return;
        recalc = 10;
        Vec3 threat = f.threatCenter != null ? f.threatCenter : gf.position();
        Player owner = gf.getOwner();
        double threatDist = f.mainThreat != null ? f.mainThreat.distanceTo(gf) : 99;
        boolean ownerUsable = owner != null && owner.distanceTo(gf) < 20 && owner.distanceToSqr(threat) > 4;
        double speed = f.panic() ? 1.45 : 1.3;
        if (ownerUsable && !(threatDist < 2.5 && owner.distanceTo(gf) > 6)) {
            Vec3 away = owner.position().subtract(threat);
            double len = away.horizontalDistance();
            Vec3 dir = len < 0.01 ? new Vec3(1, 0, 0) : new Vec3(away.x / len, 0, away.z / len);
            double hideDist = gf.getCommand() == CommandMode.STAY_SAFE ? 4.0 : 1.5;
            target = owner.position().add(dir.scale(hideDist));
            boolean hiding = gf.position().distanceTo(target) < 1.8;
            gf.setFlag(GirlfriendEntity.FLAG_HIDING, hiding);
            if (hiding) {
                gf.getNavigation().stop();
                gf.getLookControl().setLookAt(owner, 30f, 30f);
            } else {
                gf.getNavigation().moveTo(target.x, target.y, target.z, speed);
            }
        } else {
            gf.setFlag(GirlfriendEntity.FLAG_HIDING, false);
            target = findRefuge(threat);
            if (target != null) gf.getNavigation().moveTo(target.x, target.y, target.z, speed);
        }
    }

    @Nullable
    private Vec3 findRefuge(Vec3 threat) {
        BlockPos home = gf.data().home;
        if (home != null && gf.controller().isHomeDimension() && home.closerToCenterThan(gf.position(), 40) && home.distToCenterSqr(threat) > gf.position().distanceToSqr(threat)) {
            return Vec3.atBottomCenterOf(home);
        }
        Vec3 best = null;
        double bestScore = -1e9;
        int radius = (int) Math.min(16, GFConfig.FEAR_RADIUS.get());
        for (int i = 0; i < 8; i++) {
            Vec3 c = DefaultRandomPos.getPosAway(gf, radius, 6, threat);
            if (c == null) continue;
            BlockPos bp = BlockPos.containing(c);
            double score = c.distanceTo(threat) + gf.level().getBrightness(LightLayer.BLOCK, bp) * 0.6 + (gf.level().canSeeSky(bp) ? 0 : 4);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }
}
