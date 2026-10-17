package com.fantasyweapons.ability.kit;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.world.AreaEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A lingering area (frost field, overgrowth, storm, black hole...). Optionally follows an entity. Calls {@code onTick}
 * every tick with the entities inside and {@code onPulse} every {@code interval} ticks.
 */
public class FieldEffect extends AreaEffect {
    public interface Handler {
        void run(ServerPlayer owner, ItemStack weapon, FieldEffect field, List<LivingEntity> inside);
    }

    public interface EndHandler {
        void run(ServerPlayer owner, ItemStack weapon, FieldEffect field);
    }

    private double radius;
    private final int interval;
    @Nullable
    private Handler onTick;
    @Nullable
    private Handler onPulse;
    @Nullable
    private EndHandler onEnd;
    private int followId = -1;
    private Vec3 followOffset = Vec3.ZERO;

    public FieldEffect(AbilityContext ctx, Vec3 center, double radius, int lifetime, int interval) {
        super(ctx.level(), ctx.player(), ctx.data().idOrNil(), center, lifetime);
        this.radius = radius;
        this.interval = Math.max(1, interval);
    }

    public FieldEffect onTick(Handler h) {
        this.onTick = h;
        return this;
    }

    public FieldEffect onPulse(Handler h) {
        this.onPulse = h;
        return this;
    }

    public FieldEffect onEnd(EndHandler h) {
        this.onEnd = h;
        return this;
    }

    public FieldEffect follow(Entity e, Vec3 offset) {
        this.followId = e.getId();
        this.followOffset = offset;
        return this;
    }

    public double radius() {
        return radius;
    }

    public void radius(double r) {
        this.radius = r;
    }

    /** Ends the field at the end of this tick (its end handler still runs). */
    public void finish() {
        this.finished = true;
    }

    /** Records a one-time hit on an entity; false if it was already hit by this field. */
    public boolean hitOnceAdd(int entityId) {
        return hitOnce.add(entityId);
    }

    public Vec3 center() {
        return pos;
    }

    public int age() {
        return age;
    }

    public int lifetime() {
        return lifetime;
    }

    @Override
    protected void tick(ServerPlayer owner) {
        if (followId >= 0) {
            Entity e = level.getEntity(followId);
            if (e == null || !e.isAlive()) {
                finished = true;
                return;
            }
            pos = e.position().add(followOffset);
        }
        if (onTick == null && (onPulse == null || age % interval != 0)) return;
        List<LivingEntity> inside = Targeting.inRadius(level, owner, pos, radius, FWDamage.Kind.ABILITY);
        ItemStack weapon = weapon(owner);
        if (onTick != null) onTick.run(owner, weapon, this, inside);
        if (onPulse != null && age % interval == 0) onPulse.run(owner, weapon, this, inside);
    }

    @Override
    protected void onEnd(@Nullable ServerPlayer owner) {
        if (owner != null && onEnd != null) onEnd.run(owner, weapon(owner), this);
    }
}
