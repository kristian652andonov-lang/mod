package com.fantasyweapons.weapons.rimeheart;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * RIMEHEART — The Heart of Rime. The ice mage's staff: defense, raised from afar - wards, walls and a bastion.
 * <pre>
 *            [GLACIAL WARD] (5)
 *             /            \
 *   [PERMAFROST] (10, passive)  [FROST RAMPART] (20)
 *             \            /
 *       [EVERFROST BASTION] (100, ultimate)
 * </pre>
 */
public final class Rimeheart {
    public static final String GLACIAL_WARD = "glacial_ward";
    public static final String PERMAFROST = "permafrost";
    public static final String FROST_RAMPART = "frost_rampart";
    public static final String EVERFROST_BASTION = "everfrost_bastion";

    private Rimeheart() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("rimeheart")
                .name("Rimeheart", "The Heart of Rime")
                .lore("At its crown beats a heart of glacier ice that has never once thawed. Those who stand beside its bearer feel the cold close around them like armor.",
                        "the Winterward Oaths")
                .element(Element.ICE)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.STAFF)
                .damage(8f, 1.6f)
                .crit(0.06f, 1.7f)
                .theme(0x8FE3FF, 0xE8FBFF)
                .ability(AbilityDefinition.builder(GLACIAL_WARD, AbilityKind.ACTIVE)
                        .name("Glacial Ward")
                        .description("Encase the ally in your sights (or yourself) in a ward of living ice: a shield that drinks blows, and frostbites whoever strikes them.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(0, 0.4, 12.0)
                        .param("range", 30, "Reach to an ally")
                        .fraction("shield", 0.35, "Shield as a share of the ally's health")
                        .param("duration", 8, "Seconds the ward lasts")
                        .stat("SHIELD", c -> Math.round(c.param("shield") * 100 * (1 + 0.12 * Math.max(0, c.abilityLevel() - 1))) + "% HEALTH")
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("RANGE", AbilityText.param("range", "m", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("% SHIELD", 12, "").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(RimeheartAbilities::glacialWard)
                        .build())
                .ability(AbilityDefinition.builder(PERMAFROST, AbilityKind.PASSIVE)
                        .name("Permafrost")
                        .description("While you hold the Rimeheart, allies around you take less damage, and less still from frostbitten foes.")
                        .unlock(10).levels(5, 10).cost(1, 1)
                        .node(1, 1, GLACIAL_WARD)
                        .param("radius", 8, "Reach of the aura")
                        .fraction("reduction", 0.08, "Damage taken reduced by")
                        .fraction("reduction_per_level", 0.02, "Extra reduction per ability level")
                        .stat("LESS DAMAGE", AbilityText.percent("reduction", "reduction_per_level"))
                        .stat("REACH", AbilityText.param("radius", "m", 0))
                        .upgrades(Upgrades.create().perLevel("% LESS DAMAGE", 2, "").build())
                        .build())
                .ability(AbilityDefinition.builder(FROST_RAMPART, AbilityKind.ACTIVE)
                        .name("Frost Rampart")
                        .description("Raise a wall of ice across the ground where you point. Enemies cannot pass it, and arrows and fire bolts shatter against it.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(3, 1, GLACIAL_WARD)
                        .standard(1.2, 0.6, 14.0)
                        .param("range", 24, "Cast distance")
                        .param("width", 8, "Width of the wall")
                        .param("width_per_level", 1, "Extra width per ability level")
                        .param("duration", 8, "Seconds the wall stands")
                        .param("duration_per_level", 1, "Extra seconds per ability level")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("WIDTH", AbilityText.scaled("width", "width_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("WIDTH", 1, "m").perLevel("DURATION", 1, "s").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(RimeheartAbilities::frostRampart)
                        .build())
                .ability(AbilityDefinition.builder(EVERFROST_BASTION, AbilityKind.ULTIMATE)
                        .name("Everfrost Bastion")
                        .description("ULTIMATE. A ring of towering ice spires bursts up far ahead. Allies within are warded against harm and shielded again and again; enemies within freeze by degrees, and no arrow crosses the ring.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 2, PERMAFROST, FROST_RAMPART)
                        .standard(6.0, 2.0, 100.0)
                        .param("range", 30, "Cast distance")
                        .param("radius", 9, "Radius of the ring")
                        .param("radius_per_level", 1, "Extra radius per level")
                        .param("duration", 10, "Seconds the bastion stands")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .fraction("ward", 0.35, "Damage allies inside are spared")
                        .fraction("shield", 0.1, "Shield (share of health) laid on allies every two seconds")
                        .fraction("pulse_fraction", 0.05, "Damage fraction to enemies inside, every second")
                        .stat("ALLIES TAKE", c -> "-" + Math.round(c.param("ward") * 100) + "%")
                        .stat("PER SECOND", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("pulse_fraction")))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 1, "m").perLevel("DURATION", 1, "s").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(RimeheartAbilities::everfrostBastion)
                        .build())
                .build();
    }
}
