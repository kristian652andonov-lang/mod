package com.fantasyweapons.weapons.solaris;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * SOLARIS — The Sun-Forged Greatsword.
 * <pre>
 *             [RADIANT SLASH] (5)
 *              /            \
 *    [SOLAR BURST] (10)   [SUNFIRE] (15, passive)
 *              \            /
 *              [SUPERNOVA] (30)
 *                    |
 *          [CELESTIAL INFERNO] (100, ultimate)
 * </pre>
 */
public final class Solaris {
    public static final String RADIANT_SLASH = "radiant_slash";
    public static final String SOLAR_BURST = "solar_burst";
    public static final String SUNFIRE = "sunfire";
    public static final String SUPERNOVA = "supernova";
    public static final String CELESTIAL_INFERNO = "celestial_inferno";

    private Solaris() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("solaris")
                .name("Solaris", "The Sun-Forged Greatsword")
                .lore("When the old sun was dying, the smiths of Ashkarra caught its final breath in a crucible and quenched a blade in it. The forge-city burned for a hundred years after. Its edge still remembers the heat of creation, and it will not suffer the dark to stand before it.",
                        "the Ashkarran Chronicles")
                .element(Element.SOLAR)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.GREATSWORD)
                .damage(11f, 2.3f)
                .crit(0.08f, 1.9f)
                .theme(0xFFB627, 0xFFF4C2)
                .onHit(SolarisAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(RADIANT_SLASH, AbilityKind.ACTIVE)
                        .name("Radiant Slash")
                        .description("Carve a colossal arc of sunfire in front of you, scorching and igniting everything it touches.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.4, 0.6, 5.0)
                        .param("range", 6.5, "Reach of the arc in blocks")
                        .param("range_per_level", 0.6, "Extra reach per ability level")
                        .param("angle", 150, "Width of the arc in degrees")
                        .fraction("burn", 0.06, "Burn damage per pulse (2 pulses/s) as a fraction of weapon damage")
                        .param("burn_duration", 3, "Seconds the target burns")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("REACH", AbilityText.scaled("range", "range_per_level", "m", 1))
                        .stat("ARC", AbilityText.param("angle", "°", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("REACH", 0.6, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "heavy_attack")
                        .executor(SolarisAbilities::radiantSlash)
                        .build())
                .ability(AbilityDefinition.builder(SOLAR_BURST, AbilityKind.ACTIVE)
                        .name("Solar Burst")
                        .description("Drive the blade into the ground and release a miniature sun: a blinding explosion that hurls enemies away and sets them ablaze.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, RADIANT_SLASH)
                        .standard(3.4, 1.0, 9.0)
                        .param("radius", 5.5, "Explosion radius")
                        .param("radius_per_level", 0.5, "Extra radius per ability level")
                        .param("knockback", 1.3, "Knockback strength")
                        .fraction("burn", 0.08, "Burn damage per pulse as a fraction of weapon damage")
                        .param("burn_duration", 4, "Seconds the targets burn")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "impact")
                        .executor(SolarisAbilities::solarBurst)
                        .build())
                .ability(AbilityDefinition.builder(SUNFIRE, AbilityKind.PASSIVE)
                        .name("Sunfire")
                        .description("Every strike ignites the target with Solar Burn. Burning enemies take extra solar damage, and Solaris' abilities burn hotter.")
                        .unlock(15).levels(5, 10).cost(1, 1)
                        .node(3, 1, RADIANT_SLASH)
                        .fraction("burn", 0.05, "Melee burn damage per pulse (2 pulses/s) as a fraction of weapon damage")
                        .fraction("burn_per_level", 0.015, "Extra burn per ability level")
                        .param("duration", 4, "Seconds the target burns")
                        .fraction("ability_burn_bonus", 0.2, "Extra burn damage from abilities per Sunfire level")
                        .fraction("solar_vulnerability", 0.15, "Extra solar damage taken by burning enemies")
                        .stat("BURN / PULSE", c -> String.format("%,.0f", c.weaponDamage() * (c.param("burn") + c.param("burn_per_level") * Math.max(0, c.abilityLevel() - 1))))
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("SOLAR VULNERABILITY", AbilityText.percent("solar_vulnerability", null))
                        .upgrades(Upgrades.create().perLevel("% BURN DAMAGE", 1.5, "").perLevel("% ABILITY BURN", 20, "").build())
                        .build())
                .ability(AbilityDefinition.builder(SUPERNOVA, AbilityKind.ACTIVE)
                        .name("Supernova")
                        .description("Condense the sun's fury into a blazing star and hurl it. It detonates on impact in a cataclysmic solar explosion.")
                        .unlock(30).levels(5, 15).cost(2, 1)
                        .node(2, 2, SOLAR_BURST, SUNFIRE)
                        .standard(6.5, 2.0, 16.0)
                        .param("range", 30, "Maximum flight distance")
                        .param("speed", 1.3, "Blocks per tick")
                        .param("radius", 6.5, "Explosion radius")
                        .param("radius_per_level", 0.75, "Extra radius per ability level")
                        .fraction("burn", 0.12, "Burn damage per pulse as a fraction of weapon damage")
                        .param("burn_duration", 5, "Seconds the targets burn")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("BLAST RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("RANGE", AbilityText.param("range", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("BLAST RADIUS", 0.75, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_execution")
                        .executor(SolarisAbilities::supernova)
                        .build())
                .ability(AbilityDefinition.builder(CELESTIAL_INFERNO, AbilityKind.ULTIMATE)
                        .name("Celestial Inferno")
                        .description("ULTIMATE. Call a second sun into the sky above the battlefield. It rains pillars of sunfire on every enemy below, then falls.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 3, SUPERNOVA)
                        .standard(16.0, 4.0, 100.0)
                        .param("range", 32, "Cast distance")
                        .param("radius", 13, "Radius of the burning zone")
                        .param("radius_per_level", 2, "Extra radius per level")
                        .param("duration", 6, "Seconds the sun burns overhead")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .param("interval", 6, "Ticks between sunfire pillars")
                        .param("targets_per_strike", 3, "Enemies struck by each wave of pillars")
                        .param("pillar_radius", 2.5, "Radius of each sunfire pillar")
                        .fraction("strike_fraction", 0.08, "Damage fraction of each pillar")
                        .fraction("collapse_fraction", 0.6, "Damage fraction of the falling sun")
                        .stat("COLLAPSE DAMAGE", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("collapse_fraction")))
                        .stat("PER PILLAR", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("strike_fraction")))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 2, "m").perLevel("DURATION", 1, "s").build())
                        .animations("charge", "ability_activation", "ability_execution", "recovery")
                        .executor(SolarisAbilities::celestialInferno)
                        .build())
                .build();
    }
}
