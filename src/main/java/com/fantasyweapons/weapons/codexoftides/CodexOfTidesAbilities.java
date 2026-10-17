package com.fantasyweapons.weapons.codexoftides;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.magic.Allies;
import com.fantasyweapons.magic.Heals;
import com.fantasyweapons.magic.SpellEffect;
import com.fantasyweapons.magic.Spells;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.status.StatusType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server-side gameplay for every Codex of Tides spell. */
public final class CodexOfTidesAbilities {
    private CodexOfTidesAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Bubble Aegis
    // ------------------------------------------------------------------------------------------------------------

    public static boolean bubbleAegis(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        LivingEntity ally = Allies.targetOrSelf(p, ctx.param("range"));
        int ticks = (int) Math.round(ctx.param("duration") * 20);
        float shield = ally.getMaxHealth() * (float) ctx.param("shield") * Heals.power(ctx);
        Heals.barrierFlat(p, ally, shield, ticks);
        var bubble = Spells.status(ctx, ally, StatusType.BUBBLE, ticks, 1, 0);
        bubble.extra = shield;
        Heals.blessed(p, ctx.stack(), ally == p ? 0 : 1, ally == p);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.MAGE_BLESS).caster(p.getId()).pos(Spells.focus(p)).point(ally.getBoundingBox().getCenter())
                .level(Spells.BLESS_BUBBLE).entities(ally.getId()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), ally.position(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.6f, 1.0f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Healing Rain
    // ------------------------------------------------------------------------------------------------------------

    public static boolean healingRain(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Spells.aimAllyOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float heal = (float) ctx.param("heal");
        float power = Heals.power(ctx);
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() < 15 || s.age() % 20 != 15) return; // the cloud gathers before it rains
            List<Integer> healed = new ArrayList<>();
            for (LivingEntity ally : Allies.inRadius(o, center.add(0, 1, 0), radius)) {
                ally.extinguishFire();
                Heals.heal(o, w, ally, heal, power);
                healed.add(ally.getId());
            }
            for (LivingEntity e : Targeting.inRadius(o.serverLevel(), o, center.add(0, 1, 0), radius, FWDamage.Kind.ABILITY)) {
                Spells.status(o, w, e, StatusType.SOAKED, 40, 1, 0);
            }
            if (!healed.isEmpty()) Kit.fx(o.serverLevel(), FxPayload.of(FxIds.THORN_PULSE).caster(o.getId()).pos(center).level(3).entities(healed).build());
        }).start();
        Kit.fx(ctx.level(), FxPayload.of(FxIds.TIDES_RAIN).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, SoundEvents.WEATHER_RAIN_ABOVE, 1.5f, 1.0f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Tide's Embrace (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean tidesEmbrace(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        LivingEntity first = Allies.targetOrSelf(p, ctx.param("range"));
        int bounces = (int) Math.round(ctx.scaled("bounces", "bounces_per_level"));
        double reach = ctx.param("bounce_range");
        float heal = (float) ctx.param("heal"), shield = (float) ctx.param("shield");
        float power = Heals.power(ctx);
        // the wave's path is chosen now, most wounded first; it reaches each ally a few ticks after the last
        List<LivingEntity> path = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        LivingEntity at = first;
        while (at != null && path.size() < bounces) {
            path.add(at);
            seen.add(at.getId());
            at = Allies.mostWounded(p, at.position(), reach, seen);
        }
        List<Vec3> points = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        points.add(Spells.focus(p));
        for (LivingEntity e : path) {
            points.add(e.getBoundingBox().getCenter());
            ids.add(e.getId());
        }
        for (int i = 0; i < path.size(); i++) {
            LivingEntity ally = path.get(i);
            SpellEffect.later(ctx, ally.position(), 6 + i * 6, (o, w, s) -> {
                if (!ally.isAlive()) return;
                Heals.heal(o, w, ally, heal, power);
                Heals.barrier(o, ally, shield, power, 160);
                Spells.cleanse(ally);
                o.serverLevel().playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.3f);
            });
        }
        // its run done, the wave breaks over the last ally as a great tide
        LivingEntity last = path.get(path.size() - 1);
        int breakAt = 6 + path.size() * 6 + 4;
        double breakRadius = ctx.param("break_radius"), push = ctx.param("break_push");
        float breakHeal = (float) ctx.param("break_heal");
        int veil = (int) Math.round(ctx.param("veil") * 20);
        SpellEffect.later(ctx, last.position(), breakAt, (o, w, s) -> {
            Vec3 where = last.isAlive() ? last.position() : s.pos();
            // the tide breaks on the ground beneath them, even when they are in the air
            Vec3 ground = Kit.groundOrNull(o.serverLevel(), where, 48);
            Vec3 c = ground != null ? ground : where;
            List<Integer> washed = new ArrayList<>();
            for (LivingEntity ally : Allies.inRadius(o, c, breakRadius)) {
                Heals.heal(o, w, ally, breakHeal, power);
                Heals.barrier(o, ally, breakHeal * 0.6f, power, veil);
                washed.add(ally.getId());
            }
            for (LivingEntity e : Targeting.inRadius(o.serverLevel(), o, c, breakRadius, FWDamage.Kind.ABILITY)) {
                Kit.knock(e, c, push, 0.45);
                Spells.status(o, w, e, StatusType.SOAKED, veil, 1, 0);
            }
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.TIDES_BREAK).caster(o.getId()).pos(c).scale((float) breakRadius).entities(washed).power(veil)
                    .seed(Kit.seed(o.serverLevel())).build());
            o.serverLevel().playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.PLAYERS, 2.2f, 0.6f);
            o.serverLevel().playSound(null, c.x, c.y, c.z, SoundEvents.TRIDENT_THUNDER.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.6f);
        });
        Heals.blessed(p, ctx.stack(), (int) path.stream().filter(e -> e != p).count(), path.contains(p));
        Kit.active(ctx, breakAt + 4);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.TIDES_EMBRACE).caster(p.getId()).pos(points.get(0)).points(points).entities(ids).power(6)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), p.position(), SoundEvents.TRIDENT_RIPTIDE_2.value(), 1.6f, 1.2f);
        return true;
    }
}
