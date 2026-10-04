package com.fantasyweapons.weapons.doomcleaver;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.FieldEffect;
import com.fantasyweapons.ability.kit.Kit;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Doomcleaver ability. */
public final class DoomcleaverAbilities {
    private DoomcleaverAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Bloodthirst (passive)
    // ------------------------------------------------------------------------------------------------------------

    public static void onMeleeHit(MeleeContext ctx) {
        AbilityDefinition bt = ctx.weapon().ability(Doomcleaver.BLOODTHIRST);
        int lvl = ctx.data().abilityLevel(bt);
        if (lvl <= 0) return;
        ServerPlayer p = ctx.player();
        float missing = 1f - p.getHealth() / Math.max(1f, p.getMaxHealth());
        float steal = (float) ((bt.param("low_health_steal") + bt.param("low_health_steal_per_level") * (lvl - 1)) * missing);
        float rage = StatusService.lifestealMultiplier(p);
        FWDamage.lifesteal(p, ctx.damageDealt(), steal * rage);
        if (!ctx.target().isAlive()) onKill(p, ctx.weapon(), ctx.data());
    }

    static void onKill(ServerPlayer p, WeaponDefinition def, WeaponData data) {
        AbilityDefinition bt = def.ability(Doomcleaver.BLOODTHIRST);
        int lvl = data.abilityLevel(bt);
        if (lvl <= 0 || !p.isAlive()) return;
        p.heal((float) (p.getMaxHealth() * (bt.param("kill_heal") + bt.param("kill_heal_per_level") * (lvl - 1))));
        Kit.fx(p.serverLevel(), FxPayload.of(FxIds.DOOMCLEAVER_FEED).caster(p.getId()).pos(p.position()).build());
    }

    /** Deals ability damage, heals by a fraction of it and triggers kill healing. */
    static float strike(ServerPlayer p, ItemStack weapon, WeaponDefinition def, WeaponData data, LivingEntity e, float damage, double heal, int flags) {
        float dealt = FWDamage.deal(p, weapon, e, damage, FWDamage.Kind.ABILITY, Element.BLOOD, flags);
        FWDamage.lifesteal(p, dealt, (float) heal * StatusService.lifestealMultiplier(p));
        if (dealt > 0 && !e.isAlive()) onKill(p, def, data);
        return dealt;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Crimson Cleave
    // ------------------------------------------------------------------------------------------------------------

    public static boolean crimsonCleave(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double range = ctx.scaled("range", "range_per_level");
        Vec3 origin = p.position().add(0, 0.8, 0);
        Vec3 look = Targeting.flatLook(p);
        List<Integer> hit = new ArrayList<>();
        for (LivingEntity e : Targeting.inCone(ctx.level(), p, origin, look, range, ctx.param("angle"), FWDamage.Kind.ABILITY)) {
            strike(p, ctx.stack(), ctx.weapon(), ctx.data(), e, ctx.damage(), ctx.param("heal"), 0);
            Kit.knock(e, origin, 0.5, 0.35);
            hit.add(e.getId());
        }
        Kit.fx(ctx.level(), FxPayload.of(FxIds.DOOMCLEAVER_CLEAVE).caster(p.getId()).pos(p.position()).dir(look).scale((float) range)
                .power((float) ctx.param("angle")).entities(hit).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), origin, ModSounds.HEAVY_IMPACT.get(), 1.4f, 0.7f);
        Kit.sound(ctx.level(), origin, ModSounds.BLOOD_RAGE.get(), 0.8f, 1.3f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Blood Rage
    // ------------------------------------------------------------------------------------------------------------

    public static boolean bloodRage(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float bonus = (float) ctx.scaled("bonus_damage", "bonus_damage_per_level");
        float cost = (float) (p.getHealth() * ctx.param("health_cost"));
        if (p.getHealth() - cost > 1f) p.setHealth(p.getHealth() - cost);
        StatusService.apply(p, StatusType.BERSERKER, duration, 1, bonus, p.getUUID());
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.DOOMCLEAVER_RAGE).caster(p.getId()).pos(p.position()).power(duration)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), p.position(), ModSounds.BLOOD_RAGE.get(), 1.5f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Sanguine Leap
    // ------------------------------------------------------------------------------------------------------------

    public static boolean sanguineLeap(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        if (p.isPassenger() || p.isFallFlying()) throw AbilityContext.fail("Cannot leap right now");
        Vec3 target = Kit.aimTargetOrGround(p, ctx.param("range"));
        Vec3 delta = target.subtract(p.position());
        Vec3 flat = new Vec3(delta.x, 0, delta.z);
        double dist = Math.min(ctx.param("range"), flat.length());
        // ballistic launch: horizontal speed covers the distance in ~14 ticks with an arcing jump
        int flight = 14;
        Vec3 dirFlat = flat.lengthSqr() < 1e-4 ? Targeting.flatLook(p) : flat.normalize();
        double vy = 0.9 + Math.max(-0.3, Math.min(0.6, delta.y * 0.08));
        Vec3 velocity = dirFlat.scale(dist / flight * 1.25).add(0, vy, 0);
        p.setDeltaMovement(velocity);
        p.hurtMarked = true;
        p.resetFallDistance();
        double radius = ctx.scaled("radius", "radius_per_level");
        float damage = ctx.damage();
        double healPer = ctx.param("heal_per_enemy");
        AreaEffectManager.add(new FieldEffect(ctx, p.position(), radius, 60, 1).follow(p, Vec3.ZERO).onTick((o, w, f, inside) -> {
            o.resetFallDistance();
            if (f.age() < 4 || !(o.onGround() || o.isInWater() || f.age() >= 58)) return;
            Vec3 land = o.position();
            List<Integer> hit = new ArrayList<>();
            for (LivingEntity e : Targeting.inRadius(o.serverLevel(), o, land.add(0, 0.8, 0), radius, FWDamage.Kind.ABILITY)) {
                strike(o, w, ctx.weapon(), ctx.data(), e, damage, 0, FWDamage.FLAG_HEAVY);
                Kit.knock(e, land, 0.9, 0.5);
                o.heal((float) (o.getMaxHealth() * healPer));
                hit.add(e.getId());
            }
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.DOOMCLEAVER_LEAP_LAND).caster(o.getId()).pos(land).scale((float) radius)
                    .entities(hit).seed(o.level().random.nextLong()).build());
            Kit.sound(o.serverLevel(), land, ModSounds.HEAVY_IMPACT.get(), 2f, 0.6f);
            Kit.sound(o.serverLevel(), land, ModSounds.EXPLOSION.get(), 1.2f, 1.1f);
            f.finish();
        }));
        Kit.fx(ctx.level(), FxPayload.of(FxIds.DOOMCLEAVER_LEAP).caster(p.getId()).pos(p.position()).dir(velocity)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), p.position(), ModSounds.WEAPON_SWING_HEAVY.get(), 1.3f, 0.6f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Crimson Apocalypse (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean crimsonApocalypse(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float total = ctx.damage();
        float drain = (float) (total * ctx.param("drain_fraction"));
        float burst = (float) (total * ctx.param("burst_fraction"));
        double heal = ctx.param("drain_heal");
        StatusService.apply(p, StatusType.BERSERKER, duration + 40, 1, 0.3f, p.getUUID());
        AreaEffectManager.add(new FieldEffect(ctx, p.position(), radius, duration, 10).follow(p, Vec3.ZERO)
                .onPulse((o, w, f, inside) -> {
                    List<Integer> ids = new ArrayList<>();
                    for (LivingEntity e : inside) {
                        strike(o, w, ctx.weapon(), ctx.data(), e, drain, heal, FWDamage.FLAG_DOT);
                        Kit.pull(e, o.position(), 0.12);
                        ids.add(e.getId());
                    }
                    if (!ids.isEmpty()) {
                        Kit.fx(o.serverLevel(), FxPayload.of(FxIds.DOOMCLEAVER_DRAIN).caster(o.getId()).pos(o.position()).entities(ids).build());
                    }
                })
                .onEnd((o, w, f) -> {
                    List<Integer> hit = new ArrayList<>();
                    for (LivingEntity e : Targeting.inRadius(o.serverLevel(), o, o.position().add(0, 1, 0), radius, FWDamage.Kind.ABILITY)) {
                        strike(o, w, ctx.weapon(), ctx.data(), e, burst, heal * 0.5, FWDamage.FLAG_HEAVY);
                        Kit.knock(e, o.position(), 1.2, 0.6);
                        hit.add(e.getId());
                    }
                    Kit.fx(o.serverLevel(), FxPayload.of(FxIds.DOOMCLEAVER_APOCALYPSE_END).caster(o.getId()).pos(o.position())
                            .scale((float) radius).entities(hit).seed(o.level().random.nextLong()).build());
                    Kit.sound(o.serverLevel(), o.position(), ModSounds.EXPLOSION.get(), 2.5f, 0.6f);
                    Kit.sound(o.serverLevel(), o.position(), ModSounds.BLOOD_RAGE.get(), 2f, 0.5f);
                }));
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.DOOMCLEAVER_APOCALYPSE).caster(p.getId()).pos(p.position()).scale((float) radius)
                .power(duration).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), p.position(), ModSounds.BLOOD_RAGE.get(), 2.5f, 0.45f);
        return true;
    }
}
