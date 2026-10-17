package com.aigirlfriend.entity.ai;

import com.aigirlfriend.companion.CommandMode;
import com.aigirlfriend.companion.Teleport;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** "Go home": walks home (in steps for long distances) and then potters around near home. */
public class ReturnHomeGoal extends Goal {
    private final GirlfriendEntity gf;
    private int repath;
    private int idle;

    public ReturnHomeGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return gf.getCommand() == CommandMode.HOME && gf.data().home != null && !gf.isPassenger() && !gf.isSleeping() && !gf.interactions().isActive();
    }

    @Override
    public void tick() {
        BlockPos home = gf.data().home;
        if (home == null) return;
        if (!gf.controller().isHomeDimension()) return;
        Vec3 h = Vec3.atBottomCenterOf(home);
        double dist = gf.position().distanceTo(h);
        if (dist > 7) {
            if (--repath > 0) return;
            repath = 20;
            if (dist > 96 && GFConfig.TELEPORT_RECOVERY.get() && gf.level().isLoaded(home) && gf.getOwner() == null) {
                Teleport.toSafeSpot(gf, home);
                return;
            }
            Vec3 step = dist > 40 ? gf.position().add(h.subtract(gf.position()).normalize().scale(32)) : h;
            gf.getNavigation().moveTo(step.x, step.y, step.z, 1.0);
            return;
        }
        if (gf.getNavigation().isDone() && --idle <= 0) {
            idle = 200 + gf.getRandom().nextInt(400);
            Vec3 wander = DefaultRandomPos.getPosTowards(gf, 6, 3, h, Math.PI / 2);
            if (wander != null && wander.distanceTo(h) < 8) gf.getNavigation().moveTo(wander.x, wander.y, wander.z, 0.6);
        }
    }
}
