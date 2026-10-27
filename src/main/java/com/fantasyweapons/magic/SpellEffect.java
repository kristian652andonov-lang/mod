package com.fantasyweapons.magic;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.world.AreaEffect;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A spell that lives on after its cast, run by a tick callback: a meteor still falling, a wall of flame, a vine
 * tether, a phoenix in flight, a delayed bloom. Like every {@link AreaEffect} it has no entity and costs the client
 * nothing; the visuals are sent once as an effect payload.
 */
public class SpellEffect extends AreaEffect {
    public interface Tick {
        void run(ServerPlayer owner, ItemStack weapon, SpellEffect spell);
    }

    @Nullable
    private Tick onTick;
    @Nullable
    private Tick onEnd;

    public SpellEffect(ServerLevel level, ServerPlayer owner, UUID weaponId, Vec3 pos, int lifetime) {
        super(level, owner, weaponId, pos, lifetime);
    }

    public SpellEffect(AbilityContext ctx, Vec3 pos, int lifetime) {
        this(ctx.level(), ctx.player(), ctx.data().idOrNil(), pos, lifetime);
    }

    /** Runs {@code action} after {@code ticks} (if the caster is still in the world). */
    public static void later(AbilityContext ctx, Vec3 pos, int ticks, Tick action) {
        AreaEffectManager.add(new SpellEffect(ctx, pos, Math.max(1, ticks)).onEnd(action));
    }

    public SpellEffect onTick(Tick t) {
        this.onTick = t;
        return this;
    }

    public SpellEffect onEnd(Tick t) {
        this.onEnd = t;
        return this;
    }

    public SpellEffect start() {
        AreaEffectManager.add(this);
        return this;
    }

    public int age() {
        return age;
    }

    public int lifetime() {
        return lifetime;
    }

    public ServerLevel level() {
        return level;
    }

    public void move(Vec3 to) {
        this.pos = to;
    }

    /** Ends the spell at the end of this tick (its end callback still runs). */
    public void finish() {
        this.finished = true;
    }

    /** Records a one-time touch on an entity; false if it was already touched. */
    public boolean once(int entityId) {
        return hitOnce.add(entityId);
    }

    /** Forgets the one-time touches (for spells that touch again every so often). */
    public void forgetTouches() {
        hitOnce.clear();
    }

    public ItemStack weaponOf(ServerPlayer owner) {
        return weapon(owner);
    }

    @Override
    protected void tick(ServerPlayer owner) {
        if (onTick != null) onTick.run(owner, weapon(owner), this);
    }

    @Override
    protected void onEnd(@Nullable ServerPlayer owner) {
        if (owner != null && onEnd != null) onEnd.run(owner, weapon(owner), this);
    }
}
