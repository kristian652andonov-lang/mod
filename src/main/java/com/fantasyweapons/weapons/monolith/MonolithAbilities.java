package com.fantasyweapons.weapons.monolith;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Delayed;
import com.fantasyweapons.ability.kit.FieldEffect;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.ability.kit.SweepEffect;
import com.fantasyweapons.api.GroundSplitAbility;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.WeaponAnimations;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side gameplay for every Monolith ability. The blade-into-the-ground abilities ("plants") anchor the wielder
 * while the sword is in the earth, hit around the point where the blade went in and report every crack they open to
 * {@link GroundSplitAbility} — which, by default, leaves the terrain untouched.
 */
public final class MonolithAbilities {
    /** How far in front of the wielder the blade enters the ground. */
    public static final double PLANT_REACH = 1.2;
    /** Ticks from release until the blade hits the ground. */
    public static final int DRIVE_TICKS = 4;

    private static final ResourceLocation ANCHOR_SPEED = FantasyWeapons.id("monolith_anchor_speed");
    private static final ResourceLocation ANCHOR_KNOCKBACK = FantasyWeapons.id("monolith_anchor_knockback");
    private static final Map<UUID, Long> ANCHORED_UNTIL = new HashMap<>();

    private MonolithAbilities() {
    }

    public static void forget(UUID player) {
        ANCHORED_UNTIL.remove(player);
    }

    // ------------------------------------------------------------------------------------------------------------
    // shared mechanics
    // ------------------------------------------------------------------------------------------------------------

    /** Point on the ground where the blade is driven in. */
    static Vec3 plantPoint(ServerPlayer p) {
        Vec3 look = Targeting.flatLook(p);
        return Kit.ground(p.serverLevel(), p.position().add(look.scale(PLANT_REACH)).add(0, 1.5, 0), 4);
    }

    /**
     * The sword is driven into the ground for {@code hold} ticks: clients play the plant pose (and the item's
     * impact animation), the wielder is anchored (cannot walk, ignores knockback) until it is pulled free.
     */
    static void plant(AbilityContext ctx, ServerPlayer p, Vec3 point, int hold, boolean triggerImpact) {
        Kit.fx(p.serverLevel(), FxPayload.of(FxIds.MONOLITH_PLANT).caster(p.getId()).pos(point).power(hold).level(DRIVE_TICKS).build());
        if (triggerImpact) WeaponAnimations.trigger(p, ctx.stack(), "impact");
        anchor(ctx, p, hold);
        // long plants: the item's own recovery animation as the blade is wrenched free
        if (hold > 40) Delayed.schedule(ctx, hold - 10, (o, w) -> WeaponAnimations.trigger(o, w, "recovery"));
    }

    /** Transient attribute modifiers (never saved), lifted when the newest anchor expires. */
    static void anchor(AbilityContext ctx, ServerPlayer p, int ticks) {
        ANCHORED_UNTIL.merge(p.getUUID(), now(p) + ticks, Math::max);
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(ANCHOR_SPEED)) {
            speed.addTransientModifier(new AttributeModifier(ANCHOR_SPEED, -0.9, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null && !kb.hasModifier(ANCHOR_KNOCKBACK)) {
            kb.addTransientModifier(new AttributeModifier(ANCHOR_KNOCKBACK, 1.0, AttributeModifier.Operation.ADD_VALUE));
        }
        Delayed.schedule(ctx, ticks, (o, w) -> releaseIfDue(o));
    }

    private static long now(ServerPlayer p) {
        return p.serverLevel().getServer().overworld().getGameTime();
    }

    private static void releaseIfDue(ServerPlayer p) {
        Long until = ANCHORED_UNTIL.get(p.getUUID());
        if (until != null && until > now(p)) return; // a newer anchor will release it
        ANCHORED_UNTIL.remove(p.getUUID());
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(ANCHOR_SPEED);
        AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.removeModifier(ANCHOR_KNOCKBACK);
    }

    /** Horizontal directions for {@code count} cracks radiating from a point, evenly spread with some jitter. */
    static List<Vec3> spokes(RandomSource r, int count, Vec3 forward) {
        List<Vec3> out = new ArrayList<>();
        double base = Math.atan2(forward.z, forward.x);
        for (int i = 0; i < count; i++) {
            double a = base + i * Math.PI * 2 / count + (r.nextDouble() - 0.5) * (Math.PI / count);
            out.add(new Vec3(Math.cos(a), 0, Math.sin(a)));
        }
        return out;
    }

    /** Reports one crack to the ground-split hook. */
    static void split(AbilityContext ctx, Vec3 origin, Vec3 dir, double length, double width, double depth) {
        GroundSplitAbility.execute(new GroundSplitAbility.Context(ctx.player(), ctx.level(), ctx.stack(), origin, dir, length, width, depth,
                ctx.ability().id(), ctx.abilityLevel()));
    }

    static void stagger(ServerPlayer owner, LivingEntity e, int ticks) {
        if (e.isAlive()) StatusService.apply(e, StatusType.STAGGERED, ticks, 1, 0.5f, owner.getUUID());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Mountain's Weight (passive)
    // ------------------------------------------------------------------------------------------------------------

    public static void onMeleeHit(MeleeContext ctx) {
        if (!ctx.primary()) return;
        AbilityDefinition mw = ctx.weapon().ability(Monolith.MOUNTAINS_WEIGHT);
        int lvl = ctx.data().abilityLevel(mw);
        if (lvl <= 0) return;
        ServerPlayer p = ctx.player();
        LivingEntity target = ctx.target();
        Vec3 feet = target.position();
        double radius = mw.param("radius") * (ctx.heavy() ? 1.35 : 1);
        float fraction = (float) (mw.param("aftershock") + mw.param("aftershock_per_level") * (lvl - 1));
        float damage = ctx.damageDealt() * fraction;
        int stagger = (int) Math.round(mw.param("stagger") * 20);
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : Targeting.inRadius(p.serverLevel(), p, feet.add(0, 0.6, 0), radius, FWDamage.Kind.ABILITY)) {
            if (e != target) FWDamage.deal(p, ctx.stack(), e, damage, FWDamage.Kind.ABILITY, Element.EARTH, 0);
            Kit.knock(e, feet, e == target ? 0.2 : 0.55, 0.3);
            stagger(p, e, stagger);
            ids.add(e.getId());
        }
        Kit.fx(p.serverLevel(), FxPayload.of(FxIds.MONOLITH_AFTERSHOCK).caster(p.getId()).pos(Kit.ground(p.serverLevel(), feet.add(0, 0.5, 0), 3))
                .scale((float) radius).entities(ids).seed(p.level().random.nextLong()).build());
        Kit.sound(p.serverLevel(), feet, ModSounds.EARTH_IMPACT.get(), 1.1f, 1.2f);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Earthshatter
    // ------------------------------------------------------------------------------------------------------------

    public static boolean earthshatter(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 point = plantPoint(p);
        double radius = ctx.scaled("radius", "radius_per_level");
        float damage = ctx.damage();
        double launch = ctx.param("launch");
        long seed = Kit.seed(ctx.level());
        Vec3 forward = Targeting.flatLook(p);
        plant(ctx, p, point, 30, false);
        Delayed.schedule(ctx, DRIVE_TICKS, (o, w) -> {
            ServerLevel level = o.serverLevel();
            List<Vec3> ends = new ArrayList<>();
            RandomSource r = RandomSource.create(seed);
            for (Vec3 d : spokes(r, 7, forward)) {
                double len = radius * (0.75 + r.nextDouble() * 0.35);
                ends.add(point.add(d.scale(len)));
                split(ctx, point, d, len, 0.6, 2);
            }
            List<Integer> hit = Kit.falloffBurst(o, w, point.add(0, 0.8, 0), radius, damage, 0.55f, Element.EARTH, FWDamage.FLAG_HEAVY, 1.5, launch,
                    e -> stagger(o, e, 30));
            Kit.fx(level, FxPayload.of(FxIds.MONOLITH_SHATTER).caster(o.getId()).pos(point).scale((float) radius).points(ends).entities(hit).seed(seed)
                    .build());
            Kit.sound(level, point, ModSounds.EARTH_IMPACT.get(), 2.2f, 0.6f);
            Kit.sound(level, point, ModSounds.HEAVY_IMPACT.get(), 1.8f, 0.5f);
        });
        Kit.sound(ctx.level(), p.position(), ModSounds.WEAPON_SWING_HEAVY.get(), 1.5f, 0.5f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Seismic Fissure
    // ------------------------------------------------------------------------------------------------------------

    public static boolean seismicFissure(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 point = plantPoint(p);
        Vec3 dir = Targeting.flatLook(p);
        double length = ctx.scaled("length", "length_per_level");
        double width = ctx.param("width");
        double speed = ctx.param("speed");
        double launch = ctx.param("launch");
        float damage = ctx.damage();
        long seed = Kit.seed(ctx.level());
        int travel = (int) Math.ceil(length / speed);
        plant(ctx, p, point, DRIVE_TICKS + Math.min(30, travel) + 6, false);
        Delayed.schedule(ctx, DRIVE_TICKS, (o, w) -> {
            ServerLevel level = o.serverLevel();
            split(ctx, point, dir, length, width, 3);
            AreaEffectManager.add(new SweepEffect(ctx, point.add(0, 0.5, 0), dir, length, width, speed, (oo, ww, e, sweep) -> {
                FWDamage.deal(oo, ww, e, damage, FWDamage.Kind.ABILITY, Element.EARTH, FWDamage.FLAG_HEAVY);
                Vec3 v = e.getDeltaMovement();
                double resist = 1.0 - Math.min(1.0, e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                e.setDeltaMovement(v.x * 0.3 + dir.x * 0.25 * resist, launch * resist, v.z * 0.3 + dir.z * 0.25 * resist);
                e.hurtMarked = true;
                stagger(oo, e, 40);
            }).followGround());
            Kit.fx(level, FxPayload.of(FxIds.MONOLITH_FISSURE).caster(o.getId()).pos(point).dir(dir).scale((float) length).power((float) speed)
                    .level((int) Math.round(width * 10)).seed(seed).build());
            Kit.sound(level, point, ModSounds.EARTH_IMPACT.get(), 2f, 0.7f);
        });
        Kit.sound(ctx.level(), p.position(), ModSounds.WEAPON_SWING_HEAVY.get(), 1.5f, 0.55f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Tectonic Slam
    // ------------------------------------------------------------------------------------------------------------

    public static boolean tectonicSlam(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        if (p.isPassenger() || p.isFallFlying()) throw AbilityContext.fail("Cannot leap right now");
        Vec3 target = Kit.aimTargetOrGround(p, ctx.param("range"));
        Vec3 delta = target.subtract(p.position());
        Vec3 flat = new Vec3(delta.x, 0, delta.z);
        double dist = Math.min(ctx.param("range"), flat.length());
        int flight = 16;
        Vec3 dirFlat = flat.lengthSqr() < 1e-4 ? Targeting.flatLook(p) : flat.normalize();
        double vy = 1.15 + Math.max(-0.3, Math.min(0.6, delta.y * 0.08));
        Vec3 velocity = dirFlat.scale(dist / flight * 1.2).add(0, vy, 0);
        p.setDeltaMovement(velocity);
        p.hurtMarked = true;
        p.resetFallDistance();
        double radius = ctx.scaled("radius", "radius_per_level");
        float damage = ctx.damage();
        long seed = Kit.seed(ctx.level());
        boolean[] dived = {false};
        AreaEffectManager.add(new FieldEffect(ctx, p.position(), radius, 70, 1).follow(p, Vec3.ZERO).onTick((o, w, f, inside) -> {
            o.resetFallDistance();
            // past the apex the Monolith's weight drags its wielder down hard
            if (!dived[0] && f.age() > 6 && o.getDeltaMovement().y < 0.1 && !o.onGround()) {
                dived[0] = true;
                Vec3 v = o.getDeltaMovement();
                o.setDeltaMovement(v.x, -1.6, v.z);
                o.hurtMarked = true;
            }
            if (f.age() < 4 || !(o.onGround() || o.isInWater() || f.age() >= 68)) return;
            ServerLevel level = o.serverLevel();
            Vec3 land = Kit.ground(level, o.position().add(0, 0.5, 0), 3);
            Vec3 point = Kit.ground(level, land.add(Targeting.flatLook(o).scale(PLANT_REACH)).add(0, 1.5, 0), 4);
            plant(ctx, o, point, 26, true);
            List<Vec3> ends = new ArrayList<>();
            RandomSource r = RandomSource.create(seed);
            for (Vec3 d : spokes(r, 9, Targeting.flatLook(o))) {
                double len = radius * (0.8 + r.nextDouble() * 0.3);
                ends.add(point.add(d.scale(len)));
                split(ctx, point, d, len, 0.7, 2.5);
            }
            List<Integer> hit = Kit.falloffBurst(o, w, point.add(0, 0.8, 0), radius, damage, 0.5f, Element.EARTH, FWDamage.FLAG_HEAVY, 1.7, 0.85,
                    e -> stagger(o, e, 40));
            Kit.fx(level, FxPayload.of(FxIds.MONOLITH_SLAM).caster(o.getId()).pos(point).scale((float) radius).points(ends).entities(hit).seed(seed)
                    .build());
            Kit.sound(level, point, ModSounds.EARTH_IMPACT.get(), 2.6f, 0.5f);
            Kit.sound(level, point, ModSounds.EXPLOSION.get(), 1.6f, 0.6f);
            f.finish();
        }));
        Kit.fx(ctx.level(), FxPayload.of(FxIds.MONOLITH_LEAP).caster(p.getId()).pos(p.position()).dir(velocity).seed(seed).build());
        Kit.sound(ctx.level(), p.position(), ModSounds.WEAPON_SWING_HEAVY.get(), 1.6f, 0.45f);
        Kit.sound(ctx.level(), p.position(), ModSounds.EARTH_IMPACT.get(), 1.2f, 1.3f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Worldbreaker (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean worldbreaker(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        ServerLevel level = ctx.level();
        Vec3 point = plantPoint(p);
        double radius = ctx.param("radius");
        double length = ctx.param("length");
        int fissures = (int) Math.round(ctx.scaled("fissures", "fissures_per_level"));
        int aftershocks = (int) ctx.param("aftershocks");
        float total = ctx.damage();
        float fissureDamage = (float) (total * ctx.param("fissure_fraction"));
        float aftershockDamage = (float) (total * ctx.param("aftershock_fraction"));
        float eruptionDamage = (float) (total * ctx.param("eruption_fraction"));
        long seed = Kit.seed(level);
        int interval = 12;
        int eruptionAt = DRIVE_TICKS + 10 + aftershocks * interval;
        int duration = eruptionAt + 14;
        plant(ctx, p, point, duration, false);
        Kit.active(ctx, duration);

        RandomSource r = RandomSource.create(seed);
        List<Vec3> dirs = spokes(r, fissures, Targeting.flatLook(p));
        List<Vec3> ends = new ArrayList<>();
        for (Vec3 d : dirs) ends.add(point.add(d.scale(length * (0.85 + r.nextDouble() * 0.25))));

        Delayed.schedule(ctx, DRIVE_TICKS, (o, w) -> {
            for (int i = 0; i < dirs.size(); i++) {
                Vec3 d = dirs.get(i);
                double len = ends.get(i).distanceTo(point);
                split(ctx, point, d, len, 1.2, 4);
                AreaEffectManager.add(new SweepEffect(ctx, point.add(0, 0.5, 0), d, len, 1.6, 1.4, (oo, ww, e, sweep) -> {
                    FWDamage.deal(oo, ww, e, fissureDamage, FWDamage.Kind.ABILITY, Element.EARTH, FWDamage.FLAG_HEAVY);
                    Kit.knock(e, point, 0.4, 0.75);
                    stagger(oo, e, 40);
                }).followGround());
            }
            List<Integer> hit = Kit.burst(o, w, point.add(0, 0.8, 0), 3.5, fissureDamage, Element.EARTH, FWDamage.FLAG_HEAVY, 1.2, 0.5, null);
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.MONOLITH_WORLDBREAKER).caster(o.getId()).pos(point).scale((float) radius).points(ends)
                    .power(duration - DRIVE_TICKS).level(eruptionAt - DRIVE_TICKS).entities(hit).seed(seed).build());
            Kit.sound(o.serverLevel(), point, ModSounds.EARTH_IMPACT.get(), 3f, 0.4f);
            Kit.sound(o.serverLevel(), point, ModSounds.EXPLOSION.get(), 2f, 0.5f);
        });
        for (int k = 0; k < aftershocks; k++) {
            int at = DRIVE_TICKS + 10 + k * interval;
            float scale = 0.6f + 0.4f * (k + 1) / aftershocks;
            Delayed.schedule(ctx, at, (o, w) -> {
                double rr = radius * scale;
                List<Integer> hit = Kit.falloffBurst(o, w, point.add(0, 0.8, 0), rr, aftershockDamage, 0.6f, Element.EARTH, 0, 0.25, 0.45,
                        e -> stagger(o, e, 30));
                Kit.fx(o.serverLevel(), FxPayload.of(FxIds.MONOLITH_AFTERSHOCK).caster(o.getId()).pos(point).scale((float) rr).power(1)
                        .entities(hit).seed(o.level().random.nextLong()).build());
                Kit.sound(o.serverLevel(), point, ModSounds.EARTH_IMPACT.get(), 2f, 0.6f + 0.1f * scale);
            });
        }
        Delayed.schedule(ctx, eruptionAt, (o, w) -> {
            List<Integer> hit = Kit.falloffBurst(o, w, point.add(0, 0.8, 0), radius, eruptionDamage, 0.45f, Element.EARTH, FWDamage.FLAG_HEAVY, 2.0,
                    1.25, e -> stagger(o, e, 60));
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.MONOLITH_ERUPTION).caster(o.getId()).pos(point).scale((float) radius).points(ends)
                    .entities(hit).seed(seed).build());
            Kit.sound(o.serverLevel(), point, ModSounds.EXPLOSION.get(), 3f, 0.4f);
            Kit.sound(o.serverLevel(), point, ModSounds.EARTH_IMPACT.get(), 3f, 0.35f);
        });
        Kit.sound(level, p.position(), ModSounds.WEAPON_SWING_HEAVY.get(), 2f, 0.4f);
        return true;
    }
}
