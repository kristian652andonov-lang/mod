package com.fantasyweapons.weapons.solaris;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.FieldEffect;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.ability.kit.ProjectileEffect;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server-side gameplay for every Solaris ability. */
public final class SolarisAbilities {
    private SolarisAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Sunfire (passive) and burning
    // ------------------------------------------------------------------------------------------------------------

    public static void onMeleeHit(MeleeContext ctx) {
        AbilityDefinition sun = ctx.weapon().ability(Solaris.SUNFIRE);
        int lvl = ctx.data().abilityLevel(sun);
        if (lvl <= 0 || !ctx.target().isAlive()) return;
        float weaponDamage = com.fantasyweapons.progression.ProgressionMath.weaponDamage(ctx.weapon(), ctx.data());
        float perPulse = (float) (weaponDamage * (sun.param("burn") + sun.param("burn_per_level") * (lvl - 1)) * (ctx.heavy() ? 1.5 : 1));
        StatusService.apply(ctx.target(), StatusType.SOLAR_BURN, (int) Math.round(sun.param("duration") * 20), 1, perPulse, ctx.player().getUUID());
    }

    /** Ability burn: base fraction from the ability, increased by Sunfire levels. */
    static void ignite(ServerPlayer owner, WeaponDefinition def, WeaponData data, LivingEntity target, float weaponDamage, double fraction,
                       double seconds) {
        if (!target.isAlive()) return;
        AbilityDefinition sun = def.ability(Solaris.SUNFIRE);
        int lvl = data.abilityLevel(sun);
        double bonus = lvl > 0 ? sun.param("ability_burn_bonus") * lvl : 0;
        float perPulse = (float) (weaponDamage * fraction * (1 + bonus));
        StatusService.apply(target, StatusType.SOLAR_BURN, (int) Math.round(seconds * 20), 1, perPulse, owner.getUUID());
    }

    private static void ignite(AbilityContext ctx, LivingEntity target) {
        ignite(ctx.player(), ctx.weapon(), ctx.data(), target, ctx.weaponDamage(), ctx.param("burn"), ctx.param("burn_duration"));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Radiant Slash
    // ------------------------------------------------------------------------------------------------------------

    public static boolean radiantSlash(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double range = ctx.scaled("range", "range_per_level");
        double angle = ctx.param("angle");
        Vec3 origin = p.position().add(0, p.getBbHeight() * 0.55, 0);
        Vec3 look = ctx.look();
        List<Integer> hit = new ArrayList<>();
        float damage = ctx.damage();
        for (LivingEntity e : Targeting.inCone(ctx.level(), p, origin, look, range, angle, FWDamage.Kind.ABILITY)) {
            ctx.deal(e, damage, 0);
            ignite(ctx, e);
            Kit.knock(e, origin, 0.7, 0.2);
            hit.add(e.getId());
        }
        Kit.fx(ctx.level(), FxPayload.of(FxIds.SOLARIS_RADIANT_SLASH).caster(p.getId()).pos(origin).dir(look).scale((float) range)
                .power((float) angle).level(ctx.abilityLevel()).entities(hit).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), origin, ModSounds.FIRE_WHOOSH.get(), 1.3f, 0.8f);
        Kit.sound(ctx.level(), origin, ModSounds.SOLAR_BURST.get(), 0.7f, 1.4f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Solar Burst
    // ------------------------------------------------------------------------------------------------------------

    public static boolean solarBurst(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.scaled("radius", "radius_per_level");
        Vec3 ground = Kit.feet(p);
        List<Integer> hit = Kit.falloffBurst(p, ctx.stack(), ground.add(0, 1, 0), radius, ctx.damage(), 0.55f, Element.SOLAR, 0,
                ctx.param("knockback"), 0.45, e -> ignite(ctx, e));
        Kit.fx(ctx.level(), FxPayload.of(FxIds.SOLARIS_SOLAR_BURST).caster(p.getId()).pos(ground).scale((float) radius)
                .level(ctx.abilityLevel()).entities(hit).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), ground, ModSounds.SOLAR_BURST.get(), 1.6f, 1f);
        Kit.sound(ctx.level(), ground, ModSounds.EXPLOSION.get(), 1.2f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Supernova
    // ------------------------------------------------------------------------------------------------------------

    public static boolean supernova(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 look = ctx.look();
        Vec3 start = ctx.eye().subtract(0, 0.25, 0).add(look.scale(1.2));
        Vec3 vel = look.scale(ctx.param("speed"));
        double range = ctx.param("range");
        double radius = ctx.scaled("radius", "radius_per_level");
        float damage = ctx.damage();
        long seed = Kit.seed(ctx.level());
        ProjectileEffect star = new ProjectileEffect(ctx, start, vel, 1.1, range, 0, (o, e, pr) -> true)
                .onEnd((o, pos, any, pr) -> {
                    List<Integer> hit = Kit.falloffBurst(o, pr.weaponOf(o), pos, radius, damage, 0.5f, Element.SOLAR, FWDamage.FLAG_HEAVY,
                            1.5, 0.55, e -> ignite(o, ctx.weapon(), ctx.data(), e, ctx.weaponDamage(), ctx.param("burn"), ctx.param("burn_duration")));
                    Kit.fx(o.serverLevel(), FxPayload.of(FxIds.SOLARIS_SUPERNOVA_IMPACT).caster(o.getId()).pos(pos).scale((float) radius)
                            .entities(hit).seed(seed).build());
                    Kit.sound(o.serverLevel(), pos, ModSounds.EXPLOSION.get(), 2.2f, 0.8f);
                    Kit.sound(o.serverLevel(), pos, ModSounds.SOLAR_BURST.get(), 2.0f, 0.7f);
                });
        AreaEffectManager.add(star);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.SOLARIS_SUPERNOVA).caster(p.getId()).pos(start).dir(vel).power((float) range)
                .scale((float) radius).level(ctx.abilityLevel()).seed(seed).build());
        Kit.sound(ctx.level(), start, ModSounds.FIRE_WHOOSH.get(), 1.5f, 0.6f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Celestial Inferno (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean celestialInferno(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        int interval = Math.max(2, (int) ctx.param("interval"));
        int perStrike = (int) ctx.param("targets_per_strike");
        double pillar = ctx.param("pillar_radius");
        float total = ctx.damage();
        float strike = (float) (total * ctx.param("strike_fraction"));
        float collapse = (float) (total * ctx.param("collapse_fraction"));
        Vec3 sun = center.add(0, 10, 0);
        FieldEffect field = new FieldEffect(ctx, center, radius, duration, interval)
                .onPulse((o, weapon, f, inside) -> {
                    if (f.age() < 20) return; // the sun is still forming
                    List<LivingEntity> pool = new ArrayList<>(inside);
                    Collections.shuffle(pool, new java.util.Random(o.level().random.nextLong()));
                    List<Vec3> spots = new ArrayList<>();
                    for (int i = 0; i < Math.min(perStrike, pool.size()); i++) spots.add(pool.get(i).position());
                    while (spots.size() < Math.max(1, perStrike - 1)) {
                        double a = o.level().random.nextDouble() * Math.PI * 2, r = Math.sqrt(o.level().random.nextDouble()) * radius;
                        spots.add(Kit.groundAt(o.serverLevel(), center.add(Math.cos(a) * r, 0, Math.sin(a) * r), center));
                    }
                    for (Vec3 s : spots) {
                        Kit.burst(o, weapon, s.add(0, 1, 0), pillar, strike, Element.SOLAR, 0, 0.3, 0.3,
                                e -> ignite(o, ctx.weapon(), ctx.data(), e, ctx.weaponDamage(), 0.1, 4));
                        Kit.fx(o.serverLevel(), FxPayload.of(FxIds.SOLARIS_INFERNO_STRIKE).caster(o.getId()).pos(s).point(sun)
                                .scale((float) pillar).seed(o.level().random.nextLong()).build());
                    }
                    Kit.sound(o.serverLevel(), spots.get(0), ModSounds.SOLAR_BURST.get(), 1.4f, 1.2f + o.level().random.nextFloat() * 0.3f);
                })
                .onEnd((o, weapon, f) -> {
                    List<Integer> hit = Kit.falloffBurst(o, weapon, center.add(0, 1, 0), radius, collapse, 0.45f, Element.SOLAR,
                            FWDamage.FLAG_HEAVY, 1.8, 0.7, e -> ignite(o, ctx.weapon(), ctx.data(), e, ctx.weaponDamage(), 0.15, 6));
                    Kit.fx(o.serverLevel(), FxPayload.of(FxIds.SOLARIS_INFERNO_COLLAPSE).caster(o.getId()).pos(center).scale((float) radius)
                            .entities(hit).seed(o.level().random.nextLong()).build());
                    Kit.sound(o.serverLevel(), center, ModSounds.EXPLOSION.get(), 3f, 0.55f);
                    Kit.sound(o.serverLevel(), center, ModSounds.SOLAR_BURST.get(), 3f, 0.5f);
                });
        AreaEffectManager.add(field);
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.SOLARIS_INFERNO).caster(p.getId()).pos(center).point(sun).scale((float) radius)
                .power(duration).level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), sun, ModSounds.SOLAR_BURST.get(), 3f, 0.4f);
        return true;
    }
}
