package com.fantasyweapons.ability;

import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Everything an executor needs, already validated by {@link AbilityService}.
 *
 * @param charge       charge fraction in [0, 1] measured by the server
 * @param weaponDamage current weapon damage (level + mastery applied)
 * @param target       where a remote ability was cast on the targeting map, or null for a normal cast
 */
public record AbilityContext(ServerPlayer player, ServerLevel level, ItemStack stack, FantasyWeaponItem item, WeaponDefinition weapon,
                             WeaponData data, AbilityDefinition ability, int abilityLevel, float charge, float weaponDamage, float mastery,
                             @org.jetbrains.annotations.Nullable Vec3 target) {

    public AbilityContext(ServerPlayer player, ServerLevel level, ItemStack stack, FantasyWeaponItem item, WeaponDefinition weapon,
                          WeaponData data, AbilityDefinition ability, int abilityLevel, float charge, float weaponDamage, float mastery) {
        this(player, level, stack, item, weapon, data, ability, abilityLevel, charge, weaponDamage, mastery, null);
    }

    /** Full ability damage for this cast (weapon damage × ability multiplier × level × charge × armor bonuses). */
    public float damage() {
        return ability.damageAt(weaponDamage, abilityLevel, charge) * com.fantasyweapons.armor.ArmorEffects.abilityDamage(player, weapon, ability);
    }

    public float chargeMultiplier() {
        return ability.chargeMultiplier(charge);
    }

    /** A parameter of the ability, adjusted by the caster's armor (reach, durations, projectile speed...). */
    public double param(String name) {
        return com.fantasyweapons.armor.ArmorEffects.param(player, weapon, ability, name, ability.param(name));
    }

    /** {@code base + perLevel * (abilityLevel - 1)} for two params, adjusted by the caster's armor like {@code base}. */
    public double scaled(String base, String perLevel) {
        double raw = ability.param(base) + ability.param(perLevel) * Math.max(0, abilityLevel - 1);
        return com.fantasyweapons.armor.ArmorEffects.param(player, weapon, ability, base, raw);
    }

    public Vec3 eye() {
        return player.getEyePosition();
    }

    public Vec3 look() {
        return player.getLookAngle();
    }

    public float deal(Entity target, float amount, int flags) {
        return FWDamage.deal(player, stack, target, amount, FWDamage.Kind.ABILITY, weapon.element(), flags);
    }

    public long now() {
        return level.getServer().overworld().getGameTime();
    }

    /** Aborts the cast with a reason shown to the player. */
    public static AbilityFailedException fail(String reason) {
        return new AbilityFailedException(reason);
    }
}
