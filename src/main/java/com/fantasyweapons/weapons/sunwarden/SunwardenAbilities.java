package com.fantasyweapons.weapons.sunwarden;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.magic.Allies;
import com.fantasyweapons.magic.Heals;
import com.fantasyweapons.magic.SpellEffect;
import com.fantasyweapons.magic.Spells;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Sunwarden spell. */
public final class SunwardenAbilities {
    private SunwardenAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Radiance (passive): the bearer's light strengthens allies around them
    // ------------------------------------------------------------------------------------------------------------

    public static void aura(ServerPlayer p, ItemStack stack) {
        AbilityDefinition a = ((FantasyWeaponItem) stack.getItem()).definition().ability(Sunwarden.RADIANCE);
        int lvl = FantasyWeaponItem.data(stack).abilityLevel(a);
        if (lvl <= 0) return;
        float bonus = (float) Spells.scaled(a, lvl, "bonus", "bonus_per_level");
        for (LivingEntity ally : Allies.inRadius(p, p.position(), a.param("radius"))) Spells.status(p, stack, ally, StatusType.RADIANCE, 30, 1, bonus);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Blessing of Valor
    // ------------------------------------------------------------------------------------------------------------

    public static boolean blessingOfValor(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        LivingEntity ally = Allies.targetOrSelf(p, ctx.param("range"));
        int ticks = (int) Math.round(ctx.param("duration") * 20);
        Spells.status(ctx, ally, StatusType.VALOR, ticks, 1, (float) ctx.scaled("bonus", "bonus_per_level"));
        Heals.blessed(p, ctx.stack(), ally == p ? 0 : 1, ally == p);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.MAGE_BLESS).caster(p.getId()).pos(Spells.focus(p)).point(ally.getBoundingBox().getCenter())
                .level(Spells.BLESS_VALOR).entities(ally.getId()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), ally.position(), SoundEvents.BEACON_POWER_SELECT, 1.2f, 1.4f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Sanctified Ground
    // ------------------------------------------------------------------------------------------------------------

    public static boolean sanctifiedGround(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Spells.aimAllyOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.param("duration") * 20);
        float bonus = (float) ctx.param("bonus");
        float burn = ctx.damage();
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() % 20 != 6) return;
            Vec3 c = center.add(0, 0.8, 0);
            int blessed = 0;
            for (LivingEntity ally : Allies.inRadius(o, c, radius)) {
                Spells.effect(ally, MobEffects.DAMAGE_RESISTANCE, 40, 0);
                Spells.effect(ally, MobEffects.REGENERATION, 40, 0);
                Spells.status(o, w, ally, StatusType.VALOR, 30, 1, bonus);
                if (ally != o && s.once(ally.getId())) blessed++;
            }
            if (blessed > 0) Heals.blessed(o, w, blessed, false);
            for (LivingEntity e : Targeting.inRadius(o.serverLevel(), o, c, radius, FWDamage.Kind.ABILITY)) {
                if (e.isInvertedHealAndHarm()) {
                    FWDamage.deal(o, w, e, burn, FWDamage.Kind.ABILITY, Element.LIGHT, 0);
                    e.igniteForSeconds(2);
                }
            }
        }).start();
        Kit.fx(ctx.level(), FxPayload.of(FxIds.SUN_SANCTIFY).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, SoundEvents.BEACON_ACTIVATE, 1.6f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Dawnbringer (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean dawnbringer(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Spells.aimAllyOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int ticks = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float bonus = (float) ctx.param("bonus");
        float undead = ctx.damage();
        List<Integer> blessed = new ArrayList<>();
        for (LivingEntity ally : Allies.inRadius(p, center, radius)) {
            Spells.status(ctx, ally, StatusType.DAWN, ticks, 1, bonus);
            Spells.effect(ally, MobEffects.MOVEMENT_SPEED, ticks, 1);
            Spells.effect(ally, MobEffects.DAMAGE_RESISTANCE, ticks, 1);
            Spells.effect(ally, MobEffects.REGENERATION, ticks, 1);
            blessed.add(ally.getId());
        }
        if (!blessed.contains(p.getId())) {
            Spells.status(ctx, p, StatusType.DAWN, ticks, 1, bonus);
            blessed.add(p.getId());
        }
        List<Integer> hit = new ArrayList<>();
        for (LivingEntity e : Targeting.inRadius(ctx.level(), p, center, radius, FWDamage.Kind.ABILITY)) {
            Spells.effect(e, MobEffects.BLINDNESS, 60, 0);
            Spells.effect(e, MobEffects.GLOWING, ticks, 0);
            if (e.isInvertedHealAndHarm()) {
                ctx.deal(e, undead, FWDamage.FLAG_HEAVY);
                e.igniteForSeconds(5);
            }
            hit.add(e.getId());
        }
        Heals.blessed(p, ctx.stack(), blessed.size() - 1, true);
        Kit.active(ctx, ticks);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.SUN_DAWN).caster(p.getId()).pos(center).scale((float) radius).power(ticks).entities(blessed)
                .points(List.of()).level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.SOLAR_BURST.get(), 3f, 0.8f);
        Kit.sound(ctx.level(), center, SoundEvents.BEACON_POWER_SELECT, 3f, 0.6f);
        return true;
    }
}
