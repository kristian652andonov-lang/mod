package com.fantasyweapons.weapons.tidecaller;

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
import com.fantasyweapons.weapon.Element;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Tidecaller spell. */
public final class TidecallerAbilities {
    private TidecallerAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Tidal Spring
    // ------------------------------------------------------------------------------------------------------------

    public static boolean tidalSpring(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Spells.aimAllyOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.param("duration") * 20);
        float heal = (float) ctx.param("heal");
        float power = Heals.power(ctx);
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() % 20 != 5) return;
            List<Integer> healed = new ArrayList<>();
            for (LivingEntity ally : Allies.inRadius(o, center, radius)) {
                Spells.cleanse(ally);
                Heals.heal(o, w, ally, heal, power);
                healed.add(ally.getId());
            }
            if (!healed.isEmpty()) Kit.fx(o.serverLevel(), FxPayload.of(FxIds.THORN_PULSE).caster(o.getId()).pos(center).level(3).entities(healed).build());
        }).start();
        Kit.fx(ctx.level(), FxPayload.of(FxIds.TIDE_SPRING).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.6f, 1.0f);
        Kit.sound(ctx.level(), center, SoundEvents.GENERIC_SPLASH, 1.2f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Riptide Surge
    // ------------------------------------------------------------------------------------------------------------

    public static boolean riptideSurge(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        float damage = ctx.damage();
        double knock = ctx.param("knockback");
        float shield = (float) ctx.param("shield");
        int shieldTicks = (int) Math.round(ctx.param("shield_duration") * 20);
        float power = Heals.power(ctx);
        // the ring of surf rolls outwards over a few ticks: each enemy and ally is met as it passes
        int travel = 8;
        new SpellEffect(ctx, center, travel + 1).onTick((o, w, s) -> {
            double r = radius * Math.min(1, s.age() / (double) travel);
            ServerLevel level = o.serverLevel();
            for (LivingEntity e : Targeting.inRadius(level, o, center.add(0, 0.6, 0), r, FWDamage.Kind.ABILITY)) {
                if (!s.once(e.getId())) continue;
                FWDamage.deal(o, w, e, damage, FWDamage.Kind.ABILITY, Element.WATER, 0);
                Kit.knock(e, center, knock, 0.35);
                Spells.status(o, w, e, StatusType.SOAKED, 80, 1, 0);
            }
            int blessed = 0;
            for (LivingEntity ally : Allies.inRadius(o, center.add(0, 0.6, 0), r)) {
                if (!s.once(ally.getId())) continue;
                Heals.barrier(o, ally, shield, power, shieldTicks);
                ally.extinguishFire();
                if (ally != o) blessed++;
            }
            if (blessed > 0) Heals.blessed(o, w, blessed, false);
        }).start();
        Kit.fx(ctx.level(), FxPayload.of(FxIds.TIDE_RIPTIDE).caster(p.getId()).pos(center).scale((float) radius).power(travel)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, SoundEvents.GENERIC_SPLASH, 2.0f, 0.6f);
        Kit.sound(ctx.level(), center, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.5f, 0.9f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Maelstrom Sanctuary (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean maelstromSanctuary(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Spells.aimAllyOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float heal = (float) ctx.param("heal");
        float shield = (float) ctx.param("shield");
        float power = Heals.power(ctx);
        float pulse = (float) (ctx.damage() * ctx.param("pulse_fraction"));
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() < 10) return;
            ServerLevel level = o.serverLevel();
            // the current: enemies are carried round the whirlpool and drawn towards its outer wall
            for (LivingEntity e : Targeting.inRadius(level, o, center, radius, FWDamage.Kind.ABILITY)) {
                Vec3 off = e.position().subtract(center);
                Vec3 flat = new Vec3(off.x, 0, off.z);
                double d = Math.max(0.5, flat.length());
                Vec3 tangent = new Vec3(-flat.z, 0, flat.x).normalize();
                Vec3 out = flat.normalize().scale((radius * 0.7 - d) * 0.03);
                e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(tangent.scale(0.32)).add(out));
                e.hurtMarked = true;
                if (s.age() % 20 == 0) {
                    FWDamage.deal(o, w, e, pulse, FWDamage.Kind.ABILITY, Element.WATER, 0);
                    Spells.status(o, w, e, StatusType.SOAKED, 40, 1, 0);
                }
            }
            if (s.age() % 20 == 0) {
                for (LivingEntity ally : Allies.inRadius(o, center, radius)) {
                    Heals.heal(o, w, ally, heal, power);
                    Spells.cleanse(ally);
                    if (s.age() % 40 == 0) Heals.barrier(o, ally, shield, power, 60);
                }
            }
        }).start();
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.TIDE_MAELSTROM).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 3f, 0.6f);
        Kit.sound(ctx.level(), center, SoundEvents.GENERIC_SPLASH, 2.4f, 0.5f);
        return true;
    }
}
