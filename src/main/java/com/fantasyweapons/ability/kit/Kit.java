package com.fantasyweapons.ability.kit;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.weapon.Element;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Small shared helpers for ability executors (server side). */
public final class Kit {
    private Kit() {
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static long seed(ServerLevel level) {
        return level.random.nextLong();
    }

    public static void fx(ServerLevel level, FxPayload payload) {
        Fx.near(level, payload);
    }

    /** Marks the ability as "active" for the HUD for {@code ticks}. */
    public static void active(AbilityContext ctx, int ticks) {
        ctx.player().getData(ModAttachments.ABILITY_RUNTIME).setActive(ctx.ability().id(), ctx.now() + ticks);
    }

    /** Top surface below (or at) {@code pos}, searching down up to {@code maxDown} blocks; falls back to {@code pos}. */
    public static Vec3 ground(ServerLevel level, Vec3 pos, int maxDown) {
        Vec3 g = groundOrNull(level, pos, maxDown);
        return g == null ? pos : g;
    }

    /** The first surface at or below {@code pos} within {@code maxDown} blocks, or null if there is none. */
    @org.jetbrains.annotations.Nullable
    public static Vec3 groundOrNull(ServerLevel level, Vec3 pos, int maxDown) {
        // start half a block up (points lying on the ground), unless that is inside a ceiling or wall
        Vec3 from = pos.add(0, 0.5, 0);
        if (solidAt(level, from)) from = pos.add(0, 0.02, 0);
        double bottom = Math.max(level.getMinBuildHeight(), from.y - maxDown - 0.5);
        HitResult hit = level.clip(new ClipContext(from, new Vec3(from.x, bottom, from.z), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    /**
     * The ground at a spot scattered around an area (a strike, a bolt, a meteor): the first surface from a few blocks
     * above the spot down a long way, or {@code fallback} (a point on the ground, e.g. the area's centre) if the spot
     * is over a void - so scattered effects never hang in the air over a cliff edge.
     */
    public static Vec3 groundAt(ServerLevel level, Vec3 spot, Vec3 fallback) {
        Vec3 g = groundOrNull(level, spot.add(0, 4, 0), 52);
        return g != null ? g : fallback;
    }

    /**
     * The ground under a player: their feet when standing, the first surface below when airborne (so area abilities cast
     * in mid-air land on the ground instead of floating with the caster).
     */
    public static Vec3 feet(ServerPlayer p) {
        if (p.onGround()) return p.position();
        Vec3 g = groundOrNull(p.serverLevel(), p.position(), 384);
        return g == null ? p.position() : g;
    }

    /**
     * Where the player aims within range, always on the ground: the surface below the aim point, or - aiming at the
     * sky, or over a drop with nothing below in reach - the ground nearest along the line of sight back towards the
     * player, and at worst the ground under the player. Abilities are never placed in mid-air.
     */
    public static Vec3 aimGround(ServerPlayer p, double range) {
        ServerLevel level = p.serverLevel();
        Vec3 aim = Targeting.aimPoint(p, range);
        Vec3 back = aim.subtract(p.getLookAngle().scale(0.3));
        Vec3 g = groundOrNull(level, back, 48);
        if (g != null) return g;
        Vec3 eye = p.getEyePosition();
        for (int i = 1; i <= 12; i++) {
            Vec3 at = back.lerp(eye, i / 12.0);
            g = groundOrNull(level, at, 48);
            if (g != null) return g;
        }
        return feet(p);
    }

    /** Aim point preferring the entity under the crosshair (the ground under it), else the ground at the aim point. */
    public static Vec3 aimTargetOrGround(ServerPlayer p, double range) {
        LivingEntity e = Targeting.crosshair(p, range, FWDamage.Kind.ABILITY);
        if (e == null) return aimGround(p, range);
        Vec3 g = groundOrNull(p.serverLevel(), e.position(), 48);
        return g == null ? aimGround(p, range) : g;
    }

    public static boolean solidAt(ServerLevel level, Vec3 pos) {
        BlockPos bp = BlockPos.containing(pos);
        return !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty();
    }

    /** Pushes {@code e} away from {@code from} (horizontal strength + lift). */
    public static void knock(LivingEntity e, Vec3 from, double strength, double lift) {
        if (strength <= 0 && lift <= 0) return;
        Vec3 away = e.position().subtract(from);
        away = new Vec3(away.x, 0, away.z);
        away = away.lengthSqr() < 1e-4 ? Vec3.ZERO : away.normalize();
        double resist = 1.0 - Math.min(1.0, e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
        e.push(away.x * strength * resist, lift * resist, away.z * strength * resist);
        e.hurtMarked = true;
    }

    /** Pulls {@code e} toward {@code to} with a capped speed. */
    public static void pull(LivingEntity e, Vec3 to, double strength) {
        Vec3 d = to.subtract(e.getBoundingBox().getCenter());
        double len = d.length();
        if (len < 0.4) return;
        Vec3 v = d.normalize().scale(Math.min(strength, len * 0.25));
        e.setDeltaMovement(e.getDeltaMovement().scale(0.7).add(v));
        e.hurtMarked = true;
        e.resetFallDistance();
    }

    /**
     * Damages everything in a sphere. Optional knockback away from the centre and a per-target callback.
     *
     * @return ids of entities that were hit
     */
    public static List<Integer> burst(ServerPlayer owner, ItemStack weapon, Vec3 center, double radius, float damage, Element element,
                                      int flags, double knockback, double lift, @Nullable Consumer<LivingEntity> each) {
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : Targeting.inRadius(owner.serverLevel(), owner, center, radius, FWDamage.Kind.ABILITY)) {
            FWDamage.deal(owner, weapon, e, damage, FWDamage.Kind.ABILITY, element, flags);
            knock(e, center, knockback, lift);
            if (each != null) each.accept(e);
            ids.add(e.getId());
        }
        return ids;
    }

    /** {@link #burst} with damage falling off to {@code edgeFraction} at the rim. */
    public static List<Integer> falloffBurst(ServerPlayer owner, ItemStack weapon, Vec3 center, double radius, float damage, float edgeFraction,
                                             Element element, int flags, double knockback, double lift, @Nullable Consumer<LivingEntity> each) {
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : Targeting.inRadius(owner.serverLevel(), owner, center, radius, FWDamage.Kind.ABILITY)) {
            double d = Targeting.closestPoint(e.getBoundingBox(), center).distanceTo(center) / Math.max(0.01, radius);
            float k = (float) (1 - (1 - edgeFraction) * Math.min(1, d));
            FWDamage.deal(owner, weapon, e, damage * k, FWDamage.Kind.ABILITY, element, flags);
            knock(e, center, knockback * k, lift * k);
            if (each != null) each.accept(e);
            ids.add(e.getId());
        }
        return ids;
    }
}
