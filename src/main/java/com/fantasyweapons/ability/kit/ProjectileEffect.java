package com.fantasyweapons.ability.kit;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.world.AreaEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A server-simulated projectile without an entity: straight, ballistic or homing; hits entities inside its radius
 * along each tick's swept path; optional piercing; stops at blocks. Clients receive one FX payload with the same
 * launch parameters and simulate the visuals.
 */
public class ProjectileEffect extends AreaEffect {
    public interface HitHandler {
        /** @return true if the projectile should stop */
        boolean onHit(ServerPlayer owner, LivingEntity target, ProjectileEffect projectile);
    }

    public interface EndHandler {
        void onEnd(ServerPlayer owner, Vec3 pos, boolean hitSomething, ProjectileEffect projectile);
    }

    private Vec3 velocity;
    private final double radius;
    private final double maxDistance;
    private double travelled;
    private int pierce;
    private double gravity;
    private int homingId = -1;
    private double turnRate;
    private boolean blockCollision = true;
    private final HitHandler onHit;
    @Nullable
    private EndHandler onEnd;
    private boolean hitAny;

    public ProjectileEffect(AbilityContext ctx, Vec3 start, Vec3 velocity, double radius, double maxDistance, int pierce, HitHandler onHit) {
        super(ctx.level(), ctx.player(), ctx.data().idOrNil(), start, (int) Math.ceil(maxDistance / Math.max(0.05, velocity.length())) + 40);
        this.velocity = velocity;
        this.radius = radius;
        this.maxDistance = maxDistance;
        this.pierce = pierce;
        this.onHit = onHit;
    }

    public ProjectileEffect gravity(double g) {
        this.gravity = g;
        return this;
    }

    public ProjectileEffect homing(@Nullable Entity target, double turnRate) {
        this.homingId = target == null ? -1 : target.getId();
        this.turnRate = turnRate;
        return this;
    }

    public ProjectileEffect passThroughBlocks() {
        this.blockCollision = false;
        return this;
    }

    public ProjectileEffect onEnd(EndHandler handler) {
        this.onEnd = handler;
        return this;
    }

    public Vec3 velocity() {
        return velocity;
    }

    @Override
    protected void tick(ServerPlayer owner) {
        if (homingId >= 0) {
            Entity t = level.getEntity(homingId);
            if (t != null && t.isAlive()) {
                Vec3 want = t.getBoundingBox().getCenter().subtract(pos).normalize().scale(velocity.length());
                velocity = velocity.add(want.subtract(velocity).scale(turnRate));
            }
        }
        velocity = velocity.add(0, -gravity, 0);
        Vec3 next = pos.add(velocity);
        boolean wall = false;
        if (blockCollision) {
            HitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (hit.getType() != HitResult.Type.MISS) {
                next = hit.getLocation().subtract(velocity.normalize().scale(0.1));
                wall = true;
            }
        }
        for (LivingEntity e : Targeting.alongPath(level, owner, pos, next, radius, FWDamage.Kind.ABILITY)) {
            if (!hitOnce.add(e.getId())) continue;
            hitAny = true;
            boolean stop = onHit.onHit(owner, e, this);
            if (stop || (pierce >= 0 && --pierce < 0)) {
                pos = Targeting.closestPoint(e.getBoundingBox(), pos);
                finished = true;
                return;
            }
        }
        travelled += next.distanceTo(pos);
        pos = next;
        if (wall || travelled >= maxDistance) finished = true;
    }

    @Override
    protected void onEnd(@Nullable ServerPlayer owner) {
        if (owner != null && onEnd != null) onEnd.onEnd(owner, pos, hitAny, this);
    }

    public net.minecraft.world.item.ItemStack weaponOf(ServerPlayer owner) {
        return weapon(owner);
    }
}
