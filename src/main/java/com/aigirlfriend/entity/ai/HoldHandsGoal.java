package com.aigirlfriend.entity.ai;

import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.interaction.InteractionType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Walks at the owner's right side, matching their pace, while holding hands. */
public class HoldHandsGoal extends Goal {
    private final GirlfriendEntity gf;
    private int repath;
    private Vec3 lastOwnerPos;
    private double ownerSpeed;
    private int stillTicks;
    private Vec3 moveDir;

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
    public void start() {
        lastOwnerPos = null;
        ownerSpeed = 0;
        stillTicks = 0;
        moveDir = null;
    }

    /** Every tick: the pace is measured per tick (goals otherwise tick every other tick, doubling the estimate). */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Player p = gf.interactions().partner();
        if (p == null) return;
        // the server barely knows a player's velocity, so measure how far they actually moved
        Vec3 ownerPos = p.position();
        Vec3 vel = lastOwnerPos == null ? Vec3.ZERO : ownerPos.subtract(lastOwnerPos);
        lastOwnerPos = ownerPos;
        if (vel.horizontalDistanceSqr() > 1.0) vel = Vec3.ZERO; // teleported
        double step = vel.horizontalDistance();
        stillTicks = step < 0.005 ? stillTicks + 1 : 0;
        ownerSpeed = stillTicks >= 2 ? 0 : ownerSpeed * 0.8 + step * 0.2; // player movement arrives unevenly per tick
        if (step > 0.005) moveDir = new Vec3(vel.x, 0, vel.z).normalize();

        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 spot = ownerPos.add(right.scale(0.72));
        double dist = gf.position().distanceTo(spot);
        if (ownerSpeed > 0.02 && moveDir != null) {
            // walk beside him at his pace: a little faster when she lags behind her spot, slower when ahead of it
            double behind = spot.subtract(gf.position()).dot(moveDir);
            double pace = ownerSpeed + Mth.clamp(behind * 0.3, -0.1, 0.15);
            double speed = speedFor(pace);
            if (dist > 2.5 || (gf.horizontalCollision && !gf.onGround())) {
                // fell behind or something is in the way: let the pathfinder find a way back
                if (--repath <= 0) {
                    repath = 4;
                    Vec3 t = spot.add(moveDir.scale(1.5));
                    gf.getNavigation().moveTo(t.x, t.y, t.z, speedFor(pace + 0.1));
                }
            } else {
                // steer straight at a point well ahead on his path so she never "arrives" and stops mid-walk
                gf.getNavigation().stop();
                Vec3 t = spot.add(moveDir.scale(4.0));
                gf.getMoveControl().setWantedPosition(t.x, gf.getY(), t.z, speed);
                if (gf.horizontalCollision && gf.onGround()) gf.getJumpControl().jump();
            }
        } else if (dist > 0.35) {
            if (dist < 3 && Math.abs(spot.y - gf.getY()) < 0.6) {
                // small step back to her place at his side (paths cannot do moves within one block)
                gf.getNavigation().stop();
                gf.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, speedFor(0.1));
            } else if (--repath <= 0) {
                repath = 4;
                gf.getNavigation().moveTo(spot.x, spot.y, spot.z, speedFor(0.12));
            }
        } else {
            gf.getNavigation().stop();
            gf.setYRot(p.getYRot());
            gf.yBodyRot = p.yBodyRot;
        }
        if (gf.tickCount % 80 < 15) gf.getLookControl().setLookAt(p, 30f, 30f);
        else gf.getLookControl().setLookAt(gf.getX() - Mth.sin(yaw) * 4, gf.getEyeY(), gf.getZ() + Mth.cos(yaw) * 4);
    }

    /**
     * Speed modifier that moves her about {@code blocksPerTick}. Mob ground speed grows with the square of
     * (attribute x modifier); measured on the server: about 2.2 x (attribute x modifier)^2 blocks per tick.
     */
    private double speedFor(double blocksPerTick) {
        double attr = Math.max(0.05, gf.getAttributeValue(Attributes.MOVEMENT_SPEED));
        return Mth.clamp(Math.sqrt(Math.max(0, blocksPerTick) / 2.2) / attr, 0.5, 2.0);
    }
}
