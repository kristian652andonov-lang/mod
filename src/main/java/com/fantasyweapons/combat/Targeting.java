package com.fantasyweapons.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

/**
 * Server-side target queries shared by all abilities. All queries filter through {@link FWDamage#canHarm}.
 */
public final class Targeting {
    private Targeting() {
    }

    public static List<LivingEntity> inRadius(ServerLevel level, Player attacker, Vec3 center, double radius, FWDamage.Kind kind) {
        AABB box = new AABB(center, center).inflate(radius);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!FWDamage.canHarm(attacker, e, kind)) continue;
            if (closestPoint(e.getBoundingBox(), center).distanceToSqr(center) <= radius * radius) out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return out;
    }

    /** Targets inside a horizontal cone (full 3D distance, horizontal angle). */
    public static List<LivingEntity> inCone(ServerLevel level, Player attacker, Vec3 origin, Vec3 dir, double range, double totalAngleDeg,
                                            FWDamage.Kind kind) {
        if (totalAngleDeg >= 359) return inRadius(level, attacker, origin, range, kind);
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1e-6) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        double cosHalf = Math.cos(Math.toRadians(totalAngleDeg / 2.0));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : inRadius(level, attacker, origin, range, kind)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(origin);
            Vec3 toFlat = new Vec3(to.x, 0, to.z);
            if (toFlat.lengthSqr() < 0.25) {
                out.add(e);
                continue;
            }
            if (toFlat.normalize().dot(flat) >= cosHalf) out.add(e);
        }
        return out;
    }

    /** Targets touched by a capsule swept from {@code start} to {@code end}. */
    public static List<LivingEntity> alongPath(ServerLevel level, Player attacker, Vec3 start, Vec3 end, double width, FWDamage.Kind kind) {
        AABB box = new AABB(start, end).inflate(width + 1);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!FWDamage.canHarm(attacker, e, kind)) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            double r = width + e.getBbWidth() * 0.5;
            if (distanceToSegmentSqr(c, start, end) <= r * r) out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(start)));
        return out;
    }

    /** The living entity under the crosshair within range, ignoring blocks behind it. */
    @Nullable
    public static LivingEntity crosshair(Player player, double range, FWDamage.Kind kind) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDist = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 clipped = eye.add(look.scale(maxDist));
        AABB box = player.getBoundingBox().expandTowards(look.scale(maxDist)).inflate(1.5);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, clipped, box,
                e -> FWDamage.canHarm(player, e, kind) && e.isPickable(), maxDist * maxDist);
        if (hit == null) {
            // forgiving aim: nearest valid target close to the aim line
            LivingEntity best = null;
            double bestScore = Double.MAX_VALUE;
            for (LivingEntity e : alongPath((ServerLevel) player.level(), player, eye, clipped, 1.25, kind)) {
                double d = distanceToSegmentSqr(e.getBoundingBox().getCenter(), eye, clipped);
                if (d < bestScore) {
                    bestScore = d;
                    best = e;
                }
            }
            return best;
        }
        return FWDamage.resolve(hit.getEntity());
    }

    /** Where the player is looking: first block hit or max range. */
    public static Vec3 aimPoint(Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        HitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    public static Vec3 closestPoint(AABB box, Vec3 p) {
        return new Vec3(clamp(p.x, box.minX, box.maxX), clamp(p.y, box.minY, box.maxY), clamp(p.z, box.minZ, box.maxZ));
    }

    public static double distanceToSegmentSqr(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        if (len < 1e-9) return p.distanceToSqr(a);
        double t = clamp(p.subtract(a).dot(ab) / len, 0, 1);
        return p.distanceToSqr(a.add(ab.scale(t)));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** Horizontal unit vector of an entity's facing. */
    public static Vec3 flatLook(Entity e) {
        Vec3 l = e.getLookAngle();
        Vec3 f = new Vec3(l.x, 0, l.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }
}
