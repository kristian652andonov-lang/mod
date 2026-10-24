package com.fantasyweapons.magic;

import com.fantasyweapons.combat.Targeting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Who a mage's healing and blessings reach: the caster, every other player who is not on an opposing team (in a world
 * without teams, everyone fights on the same side), and the caster's own tamed creatures.
 */
public final class Allies {
    private Allies() {
    }

    public static boolean is(ServerPlayer caster, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isSpectator()) return false;
        if (le == caster) return true;
        if (le instanceof Player p) {
            if (caster.isAlliedTo(p)) return true;
            return caster.getTeam() == null || p.getTeam() == null;
        }
        if (le instanceof OwnableEntity owned && caster.getUUID().equals(owned.getOwnerUUID())) return true;
        return caster.isAlliedTo(le);
    }

    /** Allies (the caster among them) within {@code radius} of {@code center}, nearest first. */
    public static List<LivingEntity> inRadius(ServerPlayer caster, Vec3 center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(center, center).inflate(radius);
        for (LivingEntity e : caster.serverLevel().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!is(caster, e)) continue;
            if (Targeting.closestPoint(e.getBoundingBox(), center).distanceToSqr(center) <= radius * radius) out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return out;
    }

    /** The ally (never the caster) under the crosshair within range, forgiving a near miss; null if there is none. */
    @Nullable
    public static LivingEntity crosshair(ServerPlayer caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        HitResult block = caster.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double max = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(max));
        AABB box = caster.getBoundingBox().expandTowards(look.scale(max)).inflate(1.5);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, end, box, e -> e != caster && is(caster, e) && e.isPickable(), max * max);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) return le;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : caster.serverLevel().getEntitiesOfClass(LivingEntity.class, box.inflate(1))) {
            if (e == caster || !is(caster, e)) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            double d = Targeting.distanceToSegmentSqr(c, eye, end);
            double r = 1.4 + e.getBbWidth() * 0.5;
            if (d <= r * r && d < bestScore) {
                bestScore = d;
                best = e;
            }
        }
        return best;
    }

    /** The ally under the crosshair, or the caster themselves. */
    public static LivingEntity targetOrSelf(ServerPlayer caster, double range) {
        LivingEntity e = crosshair(caster, range);
        return e != null ? e : caster;
    }

    /** The ally within {@code radius} of {@code center} missing the largest share of their health (skipping those listed). */
    @Nullable
    public static LivingEntity mostWounded(ServerPlayer caster, Vec3 center, double radius, Set<Integer> skip) {
        LivingEntity best = null;
        float worst = 2;
        for (LivingEntity e : inRadius(caster, center, radius)) {
            if (skip.contains(e.getId())) continue;
            float f = e.getHealth() / Math.max(1, e.getMaxHealth());
            if (f < worst) {
                worst = f;
                best = e;
            }
        }
        return best;
    }
}
