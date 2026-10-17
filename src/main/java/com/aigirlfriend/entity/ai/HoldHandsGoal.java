package com.aigirlfriend.entity.ai;

import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.interaction.InteractionType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Walks at the owner's right side, matching their pace, while holding hands. */
public class HoldHandsGoal extends Goal {
    private final GirlfriendEntity gf;
    private int repath;

    public HoldHandsGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return gf.interactions().current() == InteractionType.HOLD_HANDS && gf.interactions().partner() != null;
    }

    @Override
    public void stop() {
        gf.getNavigation().stop();
    }

    @Override
    public void tick() {
        Player p = gf.interactions().partner();
        if (p == null) return;
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 vel = p.getDeltaMovement();
        Vec3 target = p.position().add(right.scale(0.72)).add(vel.x * 4, 0, vel.z * 4);
        double dist = gf.position().distanceTo(target);
        boolean ownerMoving = vel.horizontalDistanceSqr() > 0.0008;
        if (dist > 0.35 || ownerMoving) {
            if (--repath <= 0 || dist > 1.5) {
                repath = 4;
                double speed = p.isSprinting() ? 1.4 : dist > 2.5 ? 1.3 : 1.0;
                gf.getNavigation().moveTo(target.x, target.y, target.z, speed);
            }
        } else {
            gf.getNavigation().stop();
            gf.setYRot(p.getYRot());
            gf.yBodyRot = p.yBodyRot;
        }
        if (gf.tickCount % 80 < 15) gf.getLookControl().setLookAt(p, 30f, 30f);
        else gf.getLookControl().setLookAt(gf.getX() - Mth.sin(yaw) * 4, gf.getEyeY(), gf.getZ() + Mth.cos(yaw) * 4);
    }
}
