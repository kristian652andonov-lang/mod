package com.fantasyweapons.progression;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.config.ServerConfig;
import com.fantasyweapons.weapon.WeaponDefinition;

/**
 * Every progression formula lives here so balancing has one home. All coefficients come from {@link ServerConfig}.
 */
public final class ProgressionMath {
    private ProgressionMath() {
    }

    public static int maxLevel() {
        return ServerConfig.MAX_LEVEL.getOrDefault();
    }

    /**
     * EXP needed to advance from {@code level} to {@code level + 1}:
     * {@code base + linear*(L-1) + quadratic*(L-1)^2}, capped. Defaults reproduce 100, 150, 225, 325, ...
     */
    public static long expToNext(int level) {
        if (level >= maxLevel()) return 0;
        double n = level - 1;
        double raw = ServerConfig.EXP_BASE.getOrDefault()
                + ServerConfig.EXP_LINEAR.getOrDefault() * n
                + ServerConfig.EXP_QUADRATIC.getOrDefault() * n * n;
        double capped = Math.min(raw, ServerConfig.EXP_CAP_PER_LEVEL.getOrDefault());
        return Math.max(1, Math.round(capped));
    }

    /**
     * Melee damage of a weapon at a level. Uses a sub-quadratic power curve
     * {@code base * (1 + growth * (L-1)^exponent)} so the level-100 weapon is ~25x its level-1 self rather than an
     * unbounded linear multiple of the level.
     */
    public static float weaponDamage(WeaponDefinition def, int level, float masteryFraction) {
        double base = ServerConfig.baseDamage(def) * def.rarity().damageFactor() * ServerConfig.GLOBAL_DAMAGE_MULTIPLIER.getOrDefault();
        double scaled = base * Math.pow(ServerConfig.DAMAGE_GROWTH.getOrDefault(), Math.max(0, level - 1));
        return (float) (scaled * (1.0 + MasteryBonus.damageBonus(masteryFraction)));
    }

    public static float weaponDamage(WeaponDefinition def, WeaponData data) {
        return weaponDamage(def, data.level(), mastery(def, data));
    }

    public static float abilityDamage(float weaponDamage, double multiplier, double perLevel, int abilityLevel, float chargeMultiplier) {
        return (float) (weaponDamage * multiplier * (1.0 + perLevel * Math.max(0, abilityLevel - 1)) * chargeMultiplier);
    }

    /** The mastery points every weapon gets for reaching {@code newLevel} (see {@link #masteryPointsForLevel(WeaponDefinition, int)}). */
    public static int masteryPointsForLevel(int newLevel) {
        int pts = ServerConfig.MASTERY_POINTS_PER_LEVEL.getOrDefault();
        int milestone = ServerConfig.MASTERY_MILESTONE_INTERVAL.getOrDefault();
        if (milestone > 0 && newLevel % milestone == 0) pts += ServerConfig.MASTERY_MILESTONE_BONUS.getOrDefault();
        return pts;
    }

    /**
     * Mastery points {@code def} gets for reaching {@code newLevel}: the points every weapon gets, plus, for a weapon whose
     * ranks cost more than those add up to by the last level, an even share of the difference - so at its last level
     * any weapon can buy every rank of every ability.
     */
    public static int masteryPointsForLevel(WeaponDefinition def, int newLevel) {
        int pts = masteryPointsForLevel(newLevel);
        int max = maxLevel();
        if (newLevel < 2 || newLevel > max) return pts;
        int deficit = Math.max(0, upgradeCost(def) - basePointsUpTo(max));
        return pts + extraUpTo(deficit, newLevel, max) - extraUpTo(deficit, newLevel - 1, max);
    }

    /** All the mastery points {@code def} has been given for reaching {@code level} (from level 1). */
    public static int pointsUpTo(WeaponDefinition def, int level) {
        int max = maxLevel();
        int capped = Math.min(level, max);
        int deficit = Math.max(0, upgradeCost(def) - basePointsUpTo(max));
        return basePointsUpTo(capped) + extraUpTo(deficit, capped, max);
    }

    /** What it costs to buy every rank of every ability of {@code def}. */
    public static int upgradeCost(WeaponDefinition def) {
        int total = 0;
        for (AbilityDefinition a : def.abilities()) for (int l = 2; l <= a.maxLevel(); l++) total += a.costFor(l);
        return total;
    }

    /** What {@code def} has spent on the ranks it has bought. */
    public static int spent(WeaponDefinition def, WeaponData data) {
        int total = 0;
        for (AbilityDefinition a : def.abilities()) {
            int bought = Math.min(data.upgrades().getOrDefault(a.id(), 0), a.maxLevel() - 1);
            for (int k = 0; k < bought; k++) total += a.costFor(k + 2);
        }
        return total;
    }

    private static int basePointsUpTo(int level) {
        int total = 0;
        for (int l = 2; l <= level; l++) total += masteryPointsForLevel(l);
        return total;
    }

    private static int extraUpTo(int deficit, int level, int max) {
        return (int) ((long) deficit * Math.max(0, level - 1) / Math.max(1, max - 1));
    }

    /** Total purchasable ability upgrades on this weapon (used for mastery %). */
    public static int maxUpgrades(WeaponDefinition def) {
        int total = 0;
        for (AbilityDefinition a : def.abilities()) total += Math.max(0, a.maxLevel() - 1);
        return total;
    }

    /**
     * Mastery in [0, 1]: half from weapon level progress, half from ability upgrades purchased.
     */
    public static float mastery(WeaponDefinition def, WeaponData data) {
        int max = maxLevel();
        float levelPart = max <= 1 ? 1f : (data.level() - 1) / (float) (max - 1);
        int maxUp = maxUpgrades(def);
        float upgradePart = maxUp == 0 ? levelPart : Math.min(1f, data.totalUpgrades() / (float) maxUp);
        return Math.min(1f, 0.5f * levelPart + 0.5f * upgradePart);
    }

    /** Effective cooldown in ticks after mastery modifiers. */
    public static int cooldownTicks(AbilityDefinition ability, int abilityLevel, float mastery) {
        return Math.max(1, Math.round(ability.cooldownTicks(abilityLevel) * (1f - MasteryBonus.cooldownReduction(mastery))));
    }

    /** Effective charge duration in ticks after mastery modifiers. */
    public static int chargeTicks(AbilityDefinition ability, float mastery) {
        int base = ability.chargeTicks();
        if (base <= 0) return 0;
        return Math.max(2, Math.round(base / (1f + MasteryBonus.chargeSpeedBonus(mastery))));
    }

    public static boolean isUltimate(AbilityDefinition ability) {
        return ability.kind() == AbilityKind.ULTIMATE;
    }
}
