package com.fantasyweapons.weapons.monolith;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * MONOLITH — The Mountain's Edge. Extremely slow, enormous damage; every blow shakes the ground. Abilities drive the
 * sword into the earth and call {@link com.fantasyweapons.api.GroundSplitAbility} for the future ground split.
 * <pre>
 *             [EARTHSHATTER] (5)
 *             /             \
 *  [SEISMIC FISSURE] (10)  [MOUNTAIN'S WEIGHT] (15, passive)
 *             \             /
 *           [TECTONIC SLAM] (30)
 *                   |
 *            [WORLDBREAKER] (100, ultimate)
 * </pre>
 */
public final class Monolith {
    public static final String EARTHSHATTER = "earthshatter";
    public static final String SEISMIC_FISSURE = "seismic_fissure";
    public static final String MOUNTAINS_WEIGHT = "mountains_weight";
    public static final String TECTONIC_SLAM = "tectonic_slam";
    public static final String WORLDBREAKER = "worldbreaker";

    private Monolith() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("monolith")
                .name("Monolith", "The Mountain's Edge")
                .lore("Not forged but quarried. Only the mountain it was cut from has ever lifted it with ease.")
                .element(Element.EARTH)
                .rarity(Rarity.ANCIENT)
                .type(WeaponClass.COLOSSAL)
                .damage(320f, 2.5f)
                .crit(0.05f, 2.2f)
                .theme(0xB08A5A, 0xE8D9C0)
                .onHit(MonolithAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(EARTHSHATTER, AbilityKind.ACTIVE)
                        .name("Earthshatter")
                        .description("Drive the colossal blade into the ground. The earth cracks open around it and a shockwave of stone throws everything back.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(3.0, 1.2, 8.0)
                        .param("radius", 6, "Shockwave radius")
                        .param("radius_per_level", 0.5, "Extra radius per level")
                        .param("launch", 0.7, "Upward launch")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "impact")
                        .executor(MonolithAbilities::earthshatter)
                        .build())
                .ability(AbilityDefinition.builder(SEISMIC_FISSURE, AbilityKind.ACTIVE)
                        .name("Seismic Fissure")
                        .description("The blade bites into the earth and tears it open: a fissure races forward, and pillars of rock erupt along it, launching enemies.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, EARTHSHATTER)
                        .standard(3.6, 1.2, 10.0)
                        .param("length", 16, "Fissure length")
                        .param("length_per_level", 1.5, "Extra length per level")
                        .param("width", 1.8, "Fissure half-width")
                        .param("speed", 1.0, "Blocks per tick")
                        .param("launch", 0.85, "Upward launch")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("LENGTH", AbilityText.scaled("length", "length_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("LENGTH", 1.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "impact")
                        .executor(MonolithAbilities::seismicFissure)
                        .build())
                .ability(AbilityDefinition.builder(MOUNTAINS_WEIGHT, AbilityKind.PASSIVE)
                        .name("Mountain's Weight")
                        .description("Every blow lands with the weight of a mountain: an aftershock hits everything around the target and staggers it.")
                        .unlock(15).levels(5, 10).cost(1, 1)
                        .node(3, 1, EARTHSHATTER)
                        .fraction("aftershock", 0.35, "Aftershock damage as a fraction of the hit")
                        .fraction("aftershock_per_level", 0.08, "Extra aftershock per level")
                        .param("radius", 3, "Aftershock radius")
                        .param("stagger", 1, "Seconds hit enemies are slowed")
                        .stat("AFTERSHOCK", AbilityText.percent("aftershock", "aftershock_per_level"))
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .upgrades(Upgrades.create().perLevel("% AFTERSHOCK", 8, "").build())
                        .build())
                .ability(AbilityDefinition.builder(TECTONIC_SLAM, AbilityKind.ACTIVE)
                        .name("Tectonic Slam")
                        .description("Heave the Monolith skyward, leap after it and bring it down where you aim. Stone pillars burst from the impact.")
                        .unlock(30).levels(5, 15).cost(2, 1)
                        .node(2, 2, SEISMIC_FISSURE, MOUNTAINS_WEIGHT)
                        .standard(6.5, 1.6, 15.0)
                        .param("range", 16, "Maximum leap distance")
                        .param("radius", 7, "Impact radius")
                        .param("radius_per_level", 0.6, "Extra impact radius per level")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("IMPACT RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("IMPACT RADIUS", 0.6, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation")
                        .executor(MonolithAbilities::tectonicSlam)
                        .build())
                .ability(AbilityDefinition.builder(WORLDBREAKER, AbilityKind.ULTIMATE)
                        .name("Worldbreaker")
                        .description("ULTIMATE. Drive the Monolith deep into the world. Fissures tear outward in every direction, the ground heaves in aftershocks, and finally the earth erupts.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 3, TECTONIC_SLAM)
                        .standard(16.0, 4.0, 100.0)
                        .param("fissures", 6, "Fissures radiating outward")
                        .param("fissures_per_level", 2, "Extra fissures per level")
                        .param("length", 16, "Fissure length")
                        .param("aftershocks", 4, "Aftershocks")
                        .param("radius", 12, "Aftershock and eruption radius")
                        .fraction("fissure_fraction", 0.15, "Damage fraction of the fissures")
                        .fraction("aftershock_fraction", 0.07, "Damage fraction per aftershock")
                        .fraction("eruption_fraction", 0.5, "Damage fraction of the final eruption")
                        .stat("ERUPTION", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("eruption_fraction")))
                        .stat("FISSURES", AbilityText.scaled("fissures", "fissures_per_level", "", 0))
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("FISSURES", 2, "").build())
                        .animations("charge", "impact")
                        .executor(MonolithAbilities::worldbreaker)
                        .build())
                .build();
    }
}
