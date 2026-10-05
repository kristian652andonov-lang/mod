package com.fantasyweapons.weapons.stormbreaker;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Delayed;
import com.fantasyweapons.ability.kit.FieldEffect;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side gameplay for every Stormbreaker ability. */
public final class StormbreakerAbilities {
    private static final Map<UUID, Integer> STATIC_HITS = new HashMap<>();

    private StormbreakerAbilities() {
    }

    /**
     * Builds a lightning chain: starting at {@code first}, repeatedly jumps to the nearest valid enemy within range
     * that has not been hit yet. Never revisits an entity.
     */
    static List<LivingEntity> chain(ServerPlayer p, LivingEntity first, int jumps, double range, @Nullable Set<Integer> exclude) {
        List<LivingEntity> out = new ArrayList<>();
        Set<Integer> seen = exclude == null ? new HashSet<>() : new HashSet<>(exclude);
        LivingEntity cur = first;
        out.add(cur);
        seen.add(cur.getId());
        for (int i = 0; i < jumps; i++) {
            LivingEntity next = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : Targeting.inRadius(p.serverLevel(), p, cur.getBoundingBox().getCenter(), range, FWDamage.Kind.ABILITY)) {
                if (seen.contains(e.getId())) continue;
                double d = e.distanceToSqr(cur);
                if (d < best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) break;
            out.add(next);
            seen.add(next.getId());
            cur = next;
        }
        return out;
    }

    /** Damages a chain with per-jump falloff and sends the bolt visuals. */
    static void strikeChain(ServerPlayer p, ItemStack weapon, Vec3 from, List<LivingEntity> chain, float damage, double falloff, int flags) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            LivingEntity e = chain.get(i);
            float d = (float) (damage * Math.max(0.2, 1 - falloff * i));
            FWDamage.deal(p, weapon, e, d, FWDamage.Kind.ABILITY, Element.LIGHTNING, flags);
            ids.add(e.getId());
        }
        Kit.fx(p.serverLevel(), FxPayload.of(FxIds.STORMBREAKER_CHAIN).caster(p.getId()).pos(from).entities(ids)
                .seed(p.level().random.nextLong()).build());
    }

    static Vec3 axeTip(ServerPlayer p) {
        return p.getEyePosition().add(p.getLookAngle().scale(1.4)).subtract(0, 0.3, 0);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Static Charge (passive)
    // ------------------------------------------------------------------------------------------------------------

    public static void onMeleeHit(MeleeContext ctx) {
        AbilityDefinition sc = ctx.weapon().ability(Stormbreaker.STATIC_CHARGE);
        int lvl = ctx.data().abilityLevel(sc);
        if (lvl <= 0 || !ctx.primary()) return;
        int hits = STATIC_HITS.merge(ctx.player().getUUID(), ctx.heavy() ? 2 : 1, Integer::sum);
        if (hits < (int) sc.param("hits")) return;
        STATIC_HITS.remove(ctx.player().getUUID());
        float weaponDamage = ProgressionMath.weaponDamage(ctx.weapon(), ctx.data());
        float damage = (float) (weaponDamage * (sc.param("discharge") + sc.param("discharge_per_level") * (lvl - 1)));
        List<LivingEntity> c = chain(ctx.player(), ctx.target(), (int) sc.param("jumps"), 6, null);
        strikeChain(ctx.player(), ctx.stack(), ctx.target().getBoundingBox().getCenter().add(0, ctx.target().getBbHeight() * 0.3, 0), c, damage, 0.1, 0);
        Kit.sound(ctx.player().serverLevel(), ctx.target().position(), ModSounds.LIGHTNING.get(), 0.9f, 1.4f);
    }

    public static void forget(UUID player) {
        STATIC_HITS.remove(player);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Chain Lightning
    // ------------------------------------------------------------------------------------------------------------

    public static boolean chainLightning(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double range = ctx.param("range");
        LivingEntity first = Targeting.crosshair(p, range, FWDamage.Kind.ABILITY);
        if (first == null) {
            List<LivingEntity> cone = Targeting.inCone(ctx.level(), p, p.getEyePosition(), ctx.look(), range, 50, FWDamage.Kind.ABILITY);
            if (!cone.isEmpty()) first = cone.get(0);
        }
        if (first == null) throw AbilityContext.fail("No target in sight");
        int jumps = (int) Math.round(ctx.scaled("jumps", "jumps_per_level"));
        List<LivingEntity> c = chain(p, first, jumps, ctx.scaled("chain_range", "chain_range_per_level"), null);
        strikeChain(p, ctx.stack(), axeTip(p), c, ctx.damage(), ctx.param("falloff"), 0);
        Kit.sound(ctx.level(), first.position(), ModSounds.LIGHTNING.get(), 1.5f, 1.1f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Tempest Spin
    // ------------------------------------------------------------------------------------------------------------

    public static boolean tempestSpin(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        int pulses = Math.max(1, duration / 5);
        float perPulse = ctx.damage() / pulses;
        double pull = ctx.param("pull");
        AreaEffectManager.add(new FieldEffect(ctx, p.position(), radius, duration, 5).follow(p, Vec3.ZERO)
                .onTick((o, w, f, inside) -> {
                    for (LivingEntity e : inside) Kit.pull(e, o.position().add(0, 1, 0), pull);
                })
                .onPulse((o, w, f, inside) -> {
                    List<Integer> ids = new ArrayList<>();
                    for (LivingEntity e : inside) {
                        FWDamage.deal(o, w, e, perPulse, FWDamage.Kind.ABILITY, Element.LIGHTNING, 0);
                        ids.add(e.getId());
                    }
                    if (!ids.isEmpty()) {
                        Kit.fx(o.serverLevel(), FxPayload.of(FxIds.STORMBREAKER_ARCS).caster(o.getId()).pos(o.position().add(0, 1, 0))
                                .entities(ids).seed(o.level().random.nextLong()).build());
                    }
                }));
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.STORMBREAKER_SPIN).caster(p.getId()).pos(p.position()).scale((float) radius).power(duration)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), p.position(), ModSounds.LIGHTNING.get(), 1.4f, 0.7f);
        Kit.sound(ctx.level(), p.position(), ModSounds.WEAPON_SWING_HEAVY.get(), 1.2f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Thunderstrike
    // ------------------------------------------------------------------------------------------------------------

    public static boolean thunderstrike(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 target = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int delay = (int) ctx.param("delay");
        float damage = ctx.damage();
        float arc = (float) (damage * ctx.param("arc_fraction"));
        int arcs = (int) ctx.param("arcs");
        Kit.fx(ctx.level(), FxPayload.of(FxIds.STORMBREAKER_STRIKE_WARN).caster(p.getId()).pos(target).scale((float) radius).power(delay).build());
        Delayed.schedule(ctx, delay, (o, w) -> {
            ServerLevel level = o.serverLevel();
            List<Integer> hit = Kit.falloffBurst(o, w, target.add(0, 1, 0), radius, damage, 0.6f, Element.LIGHTNING, FWDamage.FLAG_HEAVY, 0.6, 0.6, null);
            Kit.fx(level, FxPayload.of(FxIds.STORMBREAKER_STRIKE).caster(o.getId()).pos(target).scale((float) radius).entities(hit)
                    .seed(level.random.nextLong()).build());
            // arcs from the impact to enemies just outside it
            Set<Integer> already = new HashSet<>(hit);
            List<LivingEntity> near = Targeting.inRadius(level, o, target.add(0, 1, 0), radius * 2.2, FWDamage.Kind.ABILITY);
            List<Integer> arcIds = new ArrayList<>();
            for (LivingEntity e : near) {
                if (arcIds.size() >= arcs) break;
                if (already.contains(e.getId())) continue;
                FWDamage.deal(o, w, e, arc, FWDamage.Kind.ABILITY, Element.LIGHTNING, 0);
                arcIds.add(e.getId());
            }
            if (!arcIds.isEmpty()) {
                Kit.fx(level, FxPayload.of(FxIds.STORMBREAKER_ARCS).caster(o.getId()).pos(target.add(0, 0.5, 0)).entities(arcIds)
                        .seed(level.random.nextLong()).build());
            }
            Kit.sound(level, target, ModSounds.LIGHTNING.get(), 3f, 0.7f);
            Kit.sound(level, target, ModSounds.EXPLOSION.get(), 1.5f, 1.3f);
        });
        Kit.sound(ctx.level(), p.position(), ModSounds.ABILITY_ACTIVATE.get(), 1f, 1.4f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Wrath of the Storm (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean wrathOfTheStorm(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        int interval = Math.max(2, (int) ctx.param("interval"));
        float total = ctx.damage();
        float bolt = (float) (total * ctx.param("bolt_fraction"));
        float fin = (float) (total * ctx.param("final_fraction"));
        // the storm stays where it was called down, on the ground below the caster
        Vec3 center = Kit.feet(p);
        AreaEffectManager.add(new FieldEffect(ctx, center, radius, duration, interval)
                .onPulse((o, w, f, inside) -> {
                    if (f.age() < 15) return; // the cloud is gathering
                    ServerLevel level = o.serverLevel();
                    Vec3 cloud = center.add(0, 12, 0);
                    Vec3 spot;
                    List<Integer> ids = new ArrayList<>();
                    if (!inside.isEmpty()) {
                        LivingEntity t = inside.get(level.random.nextInt(inside.size()));
                        spot = t.position();
                        List<LivingEntity> c = chain(o, t, 2, 5, null);
                        for (int i = 0; i < c.size(); i++) {
                            FWDamage.deal(o, w, c.get(i), bolt * (i == 0 ? 1f : 0.5f), FWDamage.Kind.ABILITY, Element.LIGHTNING, 0);
                            ids.add(c.get(i).getId());
                        }
                    } else {
                        double a = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * radius;
                        spot = Kit.ground(level, center.add(Math.cos(a) * r, 4, Math.sin(a) * r), 10);
                    }
                    Kit.fx(level, FxPayload.of(FxIds.STORMBREAKER_BOLT).caster(o.getId()).pos(spot).point(cloud).entities(ids)
                            .seed(level.random.nextLong()).build());
                    if (level.random.nextInt(2) == 0) Kit.sound(level, spot, ModSounds.LIGHTNING.get(), 1.6f, 0.9f + level.random.nextFloat() * 0.4f);
                })
                .onEnd((o, w, f) -> {
                    ServerLevel level = o.serverLevel();
                    Vec3 c = center;
                    List<Integer> hit = Kit.falloffBurst(o, w, c.add(0, 1, 0), radius, fin, 0.5f, Element.LIGHTNING, FWDamage.FLAG_HEAVY, 1.2, 0.7, null);
                    Kit.fx(level, FxPayload.of(FxIds.STORMBREAKER_WRATH_END).caster(o.getId()).pos(c).point(c.add(0, 12, 0)).scale((float) radius)
                            .entities(hit).seed(level.random.nextLong()).build());
                    Kit.sound(level, c, ModSounds.LIGHTNING.get(), 3.5f, 0.5f);
                    Kit.sound(level, c, ModSounds.EXPLOSION.get(), 2.5f, 0.8f);
                }));
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.STORMBREAKER_WRATH).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), p.position(), ModSounds.LIGHTNING.get(), 2.5f, 0.4f);
        return true;
    }
}
