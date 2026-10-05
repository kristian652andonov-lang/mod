package com.fantasyweapons.weapons.stormbreaker;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * STORMBREAKER — Twin-Headed Tempest.
 * <pre>
 *            [CHAIN LIGHTNING] (5)
 *             /             \
 *   [TEMPEST SPIN] (10)   [STATIC CHARGE] (15, passive)
 *             \             /
 *            [THUNDERSTRIKE] (30)
 *                   |
 *        [WRATH OF THE STORM] (100, ultimate)
 * </pre>
 */
public final class Stormbreaker {
    public static final String CHAIN_LIGHTNING = "chain_lightning";
    public static final String TEMPEST_SPIN = "tempest_spin";
    public static final String STATIC_CHARGE = "static_charge";
    public static final String THUNDERSTRIKE = "thunderstrike";
    public static final String WRATH_OF_THE_STORM = "wrath_of_the_storm";

    private Stormbreaker() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("stormbreaker")
                .name("Stormbreaker", "Twin-Headed Tempest")
                .lore("Forged on a mountaintop during a storm that raged for a hundred days, its twin heads struck by lightning between every blow of the hammer. The thunder never left it. Lift it to the sky and the sky lifts back.",
                        "the Ballad of the Hundred-Day Storm")
                .element(Element.LIGHTNING)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.BATTLEAXE)
                .damage(11f, 2.2f)
                .crit(0.1f, 1.9f)
                .theme(0x5CB8FF, 0xE6F3FF)
                .onHit(StormbreakerAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(CHAIN_LIGHTNING, AbilityKind.ACTIVE)
                        .name("Chain Lightning")
                        .description("Hurl a bolt at your target. It leaps from enemy to enemy, never striking the same one twice, losing a little power with every jump.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.4, 0.6, 5.0)
                        .param("range", 18, "Range of the first bolt")
                        .param("jumps", 4, "Number of chain jumps")
                        .param("jumps_per_level", 1, "Extra jumps per level")
                        .param("chain_range", 7, "Maximum distance of each jump")
                        .param("chain_range_per_level", 0.5, "Extra jump distance per level")
                        .fraction("falloff", 0.12, "Damage lost per jump")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("JUMPS", AbilityText.scaled("jumps", "jumps_per_level", "", 0))
                        .stat("JUMP RANGE", AbilityText.scaled("chain_range", "chain_range_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("JUMP", 1, "").perLevel("JUMP RANGE", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_execution")
                        .executor(StormbreakerAbilities::chainLightning)
                        .build())
                .ability(AbilityDefinition.builder(TEMPEST_SPIN, AbilityKind.ACTIVE)
                        .name("Tempest Spin")
                        .description("Spin the twin heads into a howling vortex of lightning. Everything around you is pulled in and shocked over and over.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, CHAIN_LIGHTNING)
                        .standard(3.2, 0.8, 9.0)
                        .param("radius", 4.5, "Radius of the vortex")
                        .param("radius_per_level", 0.4, "Extra radius per level")
                        .param("duration", 2, "Seconds of spinning")
                        .param("duration_per_level", 0.25, "Extra seconds per level")
                        .param("pull", 0.12, "Pull toward you")
                        .stat("TOTAL DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 2))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.4, "m").perLevel("DURATION", 0.25, "s").cooldownPerLevel(0.05).build())
                        .animations("charge", "spin")
                        .executor(StormbreakerAbilities::tempestSpin)
                        .build())
                .ability(AbilityDefinition.builder(STATIC_CHARGE, AbilityKind.PASSIVE)
                        .name("Static Charge")
                        .description("Every strike builds static. Every few hits the charge discharges: a bolt arcs from your target to the enemies around it.")
                        .unlock(15).levels(5, 10).cost(1, 1)
                        .node(3, 1, CHAIN_LIGHTNING)
                        .param("hits", 4, "Hits needed to discharge")
                        .fraction("discharge", 0.6, "Discharge damage as a fraction of weapon damage")
                        .fraction("discharge_per_level", 0.15, "Extra discharge damage per level")
                        .param("jumps", 2, "Enemies the discharge arcs to")
                        .stat("HITS / DISCHARGE", AbilityText.param("hits", "", 0))
                        .stat("DISCHARGE", c -> String.format("%,.0f", c.weaponDamage() * (c.param("discharge") + c.param("discharge_per_level") * Math.max(0, c.abilityLevel() - 1))))
                        .stat("ARCS TO", AbilityText.param("jumps", " ENEMIES", 0))
                        .upgrades(Upgrades.create().perLevel("% DISCHARGE DAMAGE", 15, "").build())
                        .build())
                .ability(AbilityDefinition.builder(THUNDERSTRIKE, AbilityKind.ACTIVE)
                        .name("Thunderstrike")
                        .description("Raise the axe to the sky and call down a colossal bolt where you aim. The strike explodes outward and arcs to nearby enemies.")
                        .unlock(30).levels(5, 15).cost(2, 1)
                        .node(2, 2, TEMPEST_SPIN, STATIC_CHARGE)
                        .standard(6.0, 1.8, 15.0)
                        .param("range", 28, "Cast distance")
                        .param("radius", 4.5, "Impact radius")
                        .param("radius_per_level", 0.5, "Extra impact radius per level")
                        .param("delay", 10, "Ticks between the warning sigil and the strike")
                        .param("arcs", 5, "Enemies the strike arcs to")
                        .fraction("arc_fraction", 0.35, "Damage fraction of each arc")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("ARCS", AbilityText.param("arcs", "", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "impact")
                        .executor(StormbreakerAbilities::thunderstrike)
                        .build())
                .ability(AbilityDefinition.builder(WRATH_OF_THE_STORM, AbilityKind.ULTIMATE)
                        .name("Wrath of the Storm")
                        .description("ULTIMATE. Tear open the sky. A thundercloud gathers above you and hurls lightning at every enemy beneath it, ending in one cataclysmic strike.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 3, THUNDERSTRIKE)
                        .standard(15.0, 3.5, 100.0)
                        .param("radius", 14, "Radius under the storm")
                        .param("radius_per_level", 2, "Extra radius per level")
                        .param("duration", 7, "Seconds the storm rages")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .param("interval", 5, "Ticks between bolts")
                        .fraction("bolt_fraction", 0.07, "Damage fraction of each bolt")
                        .fraction("final_fraction", 0.5, "Damage fraction of the final strike")
                        .stat("FINAL STRIKE", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("final_fraction")))
                        .stat("PER BOLT", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("bolt_fraction")))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 2, "m").perLevel("DURATION", 1, "s").build())
                        .animations("charge", "ability_activation", "ability_execution", "recovery")
                        .executor(StormbreakerAbilities::wrathOfTheStorm)
                        .build())
                .build();
    }
}
