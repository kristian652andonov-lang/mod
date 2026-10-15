package com.fantasyweapons.magic;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.playerclass.ClassService;
import com.fantasyweapons.playerclass.PlayerClass;
import com.fantasyweapons.progression.ExpService;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Healing, shields and blessings cast by the mage's weapons. Weapon damage grows a thousandfold over a hundred levels,
 * but health does not, so healing is measured against the target's own health: a share of it, made stronger by the
 * spell's level, the weapon's mastery and the Mage's arts. Healing and blessing others is how a healer's weapon grows.
 */
public final class Heals {
    /** Weapon experience for each point of health restored, and for each ally blessed. */
    private static final float EXP_PER_HEALTH = 3, EXP_PER_ALLY = 12;

    private Heals() {
    }

    /** How strong a cast's healing and shields are, for an ability of {@code ctx}. */
    public static float power(AbilityContext ctx) {
        return power(ctx.player(), ctx.weapon(), ctx.data(), ctx.abilityLevel());
    }

    /** {@code 1} at the spell's first level, +12% a level after it, up to +50% with full mastery, +15% for a Mage. */
    public static float power(ServerPlayer caster, WeaponDefinition def, WeaponData data, int abilityLevel) {
        float p = 1 + 0.12f * Math.max(0, abilityLevel - 1);
        p *= 1 + 0.5f * ProgressionMath.mastery(def, data);
        if (ClassService.of(caster) == PlayerClass.MAGE) p *= 1.15f;
        // the Verdant Almanac's Photosynthesis: stronger healing in daylight under the open sky
        var photo = def.ability("photosynthesis");
        if (photo != null && data.abilityLevel(photo) > 0 && caster.level().isDay() && caster.level().canSeeSky(caster.blockPosition().above())) {
            p *= 1 + (float) (photo.param("bonus") + photo.param("bonus_per_level") * (data.abilityLevel(photo) - 1));
        }
        return p;
    }

    /**
     * Heals {@code target} by {@code fraction} of its maximum health times {@code power}.
     *
     * @return the health actually restored
     */
    public static float heal(ServerPlayer caster, ItemStack weapon, LivingEntity target, float fraction, float power) {
        return healFlat(caster, weapon, target, target.getMaxHealth() * fraction * power);
    }

    /** Heals {@code target} by {@code amount} health; shows the number and rewards the weapon. */
    public static float healFlat(ServerPlayer caster, ItemStack weapon, LivingEntity target, float amount) {
        if (!target.isAlive() || amount <= 0) return 0;
        float before = target.getHealth();
        target.heal(amount);
        float healed = target.getHealth() - before;
        if (healed <= 0.01f) return 0;
        number(caster, target, healed);
        if (weapon.getItem() instanceof FantasyWeaponItem item) {
            ExpService.addExp(caster, weapon, Math.max(1, Math.round(healed * EXP_PER_HEALTH)), false, false);
            // the Codex of Tides' Ebb and Flow: what it heals it also shields
            WeaponDefinition def = item.definition();
            var ebb = def.ability("ebb_and_flow");
            int lvl = ebb == null ? 0 : FantasyWeaponItem.data(weapon).abilityLevel(ebb);
            if (lvl > 0) barrierFlat(caster, target, healed * (float) (ebb.param("shield") + ebb.param("shield_per_level") * (lvl - 1)),
                    (int) Math.round(ebb.param("duration") * 20));
        }
        return healed;
    }

    /** Wraps {@code target} in a shield of {@code fraction} of its maximum health times {@code power}, for {@code ticks}. */
    public static void barrier(ServerPlayer caster, LivingEntity target, float fraction, float power, int ticks) {
        barrierFlat(caster, target, target.getMaxHealth() * fraction * power, ticks);
    }

    /** A shield worth {@code health} points of absorption (golden hearts), fading after {@code ticks}. */
    public static void barrierFlat(ServerPlayer caster, LivingEntity target, float health, int ticks) {
        if (!target.isAlive() || health <= 0) return;
        StatusService.apply(target, StatusType.BARRIER, ticks, 1, health, caster.getUUID());
        // the status raises the absorption the target can hold; then the shield is filled
        target.setAbsorptionAmount(Math.max(target.getAbsorptionAmount(), Math.min(health, target.getMaxAbsorption())));
    }

    /** Rewards the weapon for blessing {@code allies} others (the caster themselves count for a third). */
    public static void blessed(ServerPlayer caster, ItemStack weapon, int allies, boolean self) {
        float exp = allies * EXP_PER_ALLY + (self ? EXP_PER_ALLY / 3 : 0);
        if (exp > 0 && weapon.getItem() instanceof FantasyWeaponItem) ExpService.addExp(caster, weapon, Math.round(exp), false, false);
    }

    /** A green number over the healed for the caster (and for the healed, if it is another player). */
    private static void number(ServerPlayer caster, LivingEntity target, float healed) {
        FxPayload p = FxPayload.of(FxIds.HEAL_NUMBER).caster(caster.getId()).pos(target.position().add(0, target.getBbHeight(), 0)).power(healed)
                .entities(target.getId()).build();
        Fx.only(caster, p);
        if (target instanceof ServerPlayer other && other != caster) Fx.only(other, p);
    }
}
