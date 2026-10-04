package com.fantasyweapons.weapons.doomcleaver;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * DOOMCLEAVER — Axe of the Crimson Hunger. Normal attacks always steal life.
 * <pre>
 *             [CRIMSON CLEAVE] (5)
 *              /             \
 *    [BLOOD RAGE] (10)     [BLOODTHIRST] (15, passive)
 *              \             /
 *            [SANGUINE LEAP] (30)
 *                    |
 *         [CRIMSON APOCALYPSE] (100, ultimate)
 * </pre>
 */
public final class Doomcleaver {
    public static final String CRIMSON_CLEAVE = "crimson_cleave";
    public static final String BLOOD_RAGE = "blood_rage";
    public static final String BLOODTHIRST = "bloodthirst";
    public static final String SANGUINE_LEAP = "sanguine_leap";
    public static final String CRIMSON_APOCALYPSE = "crimson_apocalypse";

    private Doomcleaver() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("doomcleaver")
                .name("Doomcleaver", "Axe of the Crimson Hunger")
                .lore("Its heart still beats. Every life it takes feeds the one who wields it.")
                .element(Element.BLOOD)
                .rarity(Rarity.MYTHIC)
                .type(WeaponClass.BATTLEAXE)
                .damage(170f, 2.2f)
                .crit(0.1f, 1.9f)
                .lifesteal(0.06f)
                .theme(0xE0213A, 0xFF9AA8)
                .onHit(DoomcleaverAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(CRIMSON_CLEAVE, AbilityKind.ACTIVE)
                        .name("Crimson Cleave")
                        .description("Bring the axe down with all your weight. A wave of blood tears through the ground in front of you and the life it spills flows back into you.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.8, 0.7, 6.0)
                        .param("range", 7, "Reach of the blood wave")
                        .param("range_per_level", 0.6, "Extra reach per level")
                        .param("angle", 100, "Width of the wave in degrees")
                        .fraction("heal", 0.2, "Fraction of damage dealt returned as health")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("REACH", AbilityText.scaled("range", "range_per_level", "m", 1))
                        .stat("LIFE STEAL", AbilityText.percent("heal", null))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("REACH", 0.6, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "heavy_attack")
                        .executor(DoomcleaverAbilities::crimsonCleave)
                        .build())
                .ability(AbilityDefinition.builder(BLOOD_RAGE, AbilityKind.ACTIVE)
                        .name("Blood Rage")
                        .description("Pay in blood to become a berserker: more damage, faster swings, far stronger life steal and no knockback. The axe's heart burns red around you.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, CRIMSON_CLEAVE)
                        .param("charge_time", 0.4, "Seconds to reach full charge")
                        .fraction("min_charge", 0.25, "Minimum charge fraction needed to release")
                        .param("cooldown", 22, "Cooldown in seconds")
                        .fraction("cooldown_reduction_per_level", 0.05, "Cooldown reduction per ability level")
                        .param("duration", 8, "Seconds the rage lasts")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .fraction("bonus_damage", 0.25, "Extra damage while raging")
                        .fraction("bonus_damage_per_level", 0.05, "Extra damage per level")
                        .fraction("health_cost", 0.1, "Fraction of current health sacrificed")
                        .stat("BONUS DAMAGE", AbilityText.percent("bonus_damage", "bonus_damage_per_level"))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("ATTACK SPEED", c -> "+50%")
                        .stat("LIFE STEAL", c -> "x2.5")
                        .stat("COST", c -> Math.round(c.param("health_cost") * 100) + "% HEALTH")
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("% BONUS DAMAGE", 5, "").perLevel("DURATION", 1, "s").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation")
                        .executor(DoomcleaverAbilities::bloodRage)
                        .build())
                .ability(AbilityDefinition.builder(BLOODTHIRST, AbilityKind.PASSIVE)
                        .name("Bloodthirst")
                        .description("The closer you are to death, the more life every hit steals. Each kill feeds you a share of your maximum health.")
                        .unlock(15).levels(5, 10).cost(1, 1)
                        .node(3, 1, CRIMSON_CLEAVE)
                        .fraction("low_health_steal", 0.2, "Extra life steal at 0% health (scales with missing health)")
                        .fraction("low_health_steal_per_level", 0.04, "Extra low-health steal per level")
                        .fraction("kill_heal", 0.06, "Fraction of max health healed on kill")
                        .fraction("kill_heal_per_level", 0.01, "Extra kill heal per level")
                        .stat("MAX EXTRA STEAL", AbilityText.percent("low_health_steal", "low_health_steal_per_level"))
                        .stat("HEAL ON KILL", AbilityText.percent("kill_heal", "kill_heal_per_level"))
                        .upgrades(Upgrades.create().perLevel("% LOW-HEALTH STEAL", 4, "").perLevel("% HEAL ON KILL", 1, "").build())
                        .build())
                .ability(AbilityDefinition.builder(SANGUINE_LEAP, AbilityKind.ACTIVE)
                        .name("Sanguine Leap")
                        .description("Leap through the air and crash down axe-first. The impact erupts in a fountain of blood crystals that heals you for every enemy caught.")
                        .unlock(30).levels(5, 15).cost(2, 1)
                        .node(2, 2, BLOOD_RAGE, BLOODTHIRST)
                        .standard(5.5, 1.0, 12.0)
                        .param("range", 16, "Maximum leap distance")
                        .param("radius", 5, "Impact radius")
                        .param("radius_per_level", 0.5, "Extra impact radius per level")
                        .fraction("heal_per_enemy", 0.04, "Fraction of max health healed per enemy hit")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("IMPACT RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("HEAL / ENEMY", AbilityText.percent("heal_per_enemy", null))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("IMPACT RADIUS", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "impact")
                        .executor(DoomcleaverAbilities::sanguineLeap)
                        .build())
                .ability(AbilityDefinition.builder(CRIMSON_APOCALYPSE, AbilityKind.ULTIMATE)
                        .name("Crimson Apocalypse")
                        .description("ULTIMATE. A blood moon rises. Crimson tendrils latch onto every enemy around you and drain their life into you, then tear them apart.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 3, SANGUINE_LEAP)
                        .standard(15.0, 3.0, 100.0)
                        .param("radius", 13, "Radius of the blood moon")
                        .param("radius_per_level", 2, "Extra radius per level")
                        .param("duration", 5, "Seconds of draining")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .fraction("drain_fraction", 0.07, "Damage fraction per drain pulse (2 pulses/s)")
                        .fraction("burst_fraction", 0.5, "Damage fraction of the final burst")
                        .fraction("drain_heal", 0.35, "Fraction of drained damage healed")
                        .stat("BURST DAMAGE", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("burst_fraction")))
                        .stat("DRAIN / PULSE", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("drain_fraction")))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 2, "m").perLevel("DURATION", 1, "s").build())
                        .animations("charge", "ability_activation", "ability_execution", "recovery")
                        .executor(DoomcleaverAbilities::crimsonApocalypse)
                        .build())
                .build();
    }
}
