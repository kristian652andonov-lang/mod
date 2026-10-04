package com.fantasyweapons.ability.kit;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.world.AreaEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Something that travels along a straight line at a fixed speed and hits every entity it passes exactly once
 * (slash waves, spike eruptions, ground fissures). Optionally follows the terrain height.
 */
public class SweepEffect extends AreaEffect {
    public interface HitHandler {
        void onHit(ServerPlayer owner, ItemStack weapon, LivingEntity target, SweepEffect sweep);
    }

    public interface StepHandler {
        void onStep(ServerPlayer owner, ItemStack weapon, Vec3 head, SweepEffect sweep);
    }

    private final Vec3 start;
    private final Vec3 dir;
    private final double dist;
    private final double width;
    private final double speed;
    private final HitHandler onHit;
    @Nullable
    private StepHandler onStep;
    private boolean followGround;

    public SweepEffect(AbilityContext ctx, Vec3 start, Vec3 dir, double dist, double width, double speed, HitHandler onHit) {
        super(ctx.level(), ctx.player(), ctx.data().idOrNil(), start, (int) Math.ceil(dist / speed) + 1);
        this.start = start;
        this.dir = dir.normalize();
        this.dist = dist;
        this.width = width;
        this.speed = speed;
        this.onHit = onHit;
    }

    public SweepEffect followGround() {
        this.followGround = true;
        return this;
    }

    public SweepEffect onStep(StepHandler handler) {
        this.onStep = handler;
        return this;
    }

    public Vec3 dir() {
        return dir;
    }

    @Override
    protected void tick(ServerPlayer owner) {
        double a = Math.min(dist, speed * (age - 1));
        double b = Math.min(dist, speed * age);
        Vec3 from = start.add(dir.scale(a));
        Vec3 to = start.add(dir.scale(b));
        if (followGround) {
            from = Kit.ground(level, from.add(0, 2, 0), 6).add(0, 0.5, 0);
            to = Kit.ground(level, to.add(0, 2, 0), 6).add(0, 0.5, 0);
        }
        pos = to;
        ItemStack weapon = weapon(owner);
        if (onStep != null) onStep.onStep(owner, weapon, to, this);
        for (LivingEntity e : Targeting.alongPath(level, owner, from, to, width, FWDamage.Kind.ABILITY)) {
            if (hitOnce.add(e.getId())) onHit.onHit(owner, weapon, e, this);
        }
        if (b >= dist) finished = true;
    }
}
