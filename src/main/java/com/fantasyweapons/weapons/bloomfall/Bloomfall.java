package com.fantasyweapons.weapons.bloomfall;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * BLOOMFALL — Scythe of the Wild Bloom. Poison, vines and area control.
 * <pre>
 *              [THORN SWEEP] (5)
 *              /           \
 *  [ENTANGLING ROOTS] (10)  [VENOM BLOOM] (15, passive)
 *              \           /
 *             [OVERGROWTH] (30)
 *                   |
 *         [WRATH OF THE WILD] (100, ultimate)
 * </pre>
 */
public final class Bloomfall {
    public static final String THORN_SWEEP = "thorn_sweep";
    public static final String ENTANGLING_ROOTS = "entangling_roots";
    public static final String VENOM_BLOOM = "venom_bloom";
    public static final String OVERGROWTH = "overgrowth";
    public static final String WRATH_OF_THE_WILD = "wrath_of_the_wild";

    private Bloomfall() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("bloomfall")
                .name("Bloomfall", "Scythe of the Wild Bloom")
                .lore("Shaped from a thorn of the World-Tree by the druids of the Veiled Grove, to defend the wild from axe and fire. Where it falls, the wild returns. Flowers open on the graves it makes, and the forest never forgets who carried it.",
                        "the Grove-Keepers' Oath")
                .element(Element.NATURE)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.SCYTHE)
                .damage(9f, 2.2f)
                .crit(0.1f, 1.8f)
                .theme(0x5BE063, 0xFFE08A)
                .onHit(BloomfallAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(THORN_SWEEP, AbilityKind.ACTIVE)
                        .name("Thorn Sweep")
                        .description("A wide reaping sweep that seeds the ground: thorny vines burst up along the arc, cutting and poisoning everything they touch.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.4, 0.6, 6.0)
                        .param("range", 6, "Sweep reach")
                        .param("range_per_level", 0.5, "Extra reach per level")
                        .param("angle", 180, "Sweep width in degrees")
                        .param("poison_stacks", 2, "Nature Poison stacks applied")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("REACH", AbilityText.scaled("range", "range_per_level", "m", 1))
                        .stat("POISON", AbilityText.param("poison_stacks", " stacks", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("REACH", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "heavy_attack")
                        .executor(BloomfallAbilities::thornSweep)
                        .build())
                .ability(AbilityDefinition.builder(ENTANGLING_ROOTS, AbilityKind.ACTIVE)
                        .name("Entangling Roots")
                        .description("Ancient roots tear out of the earth where you aim, wrapping every enemy and holding them fast while the thorns bite.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, THORN_SWEEP)
                        .standard(3.0, 1.0, 10.0)
                        .param("range", 22, "Cast distance")
                        .param("radius", 4.5, "Root radius")
                        .param("radius_per_level", 0.4, "Extra radius per level")
                        .param("root", 2.5, "Seconds enemies stay rooted")
                        .param("root_per_level", 0.25, "Extra root seconds per level")
                        .param("poison_stacks", 2, "Nature Poison stacks applied")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("ROOT", AbilityText.scaled("root", "root_per_level", "s", 2))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.4, "m").perLevel("ROOT", 0.25, "s").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_execution")
                        .executor(BloomfallAbilities::entanglingRoots)
                        .build())
                .ability(AbilityDefinition.builder(VENOM_BLOOM, AbilityKind.PASSIVE)
                        .name("Venom Bloom")
                        .description("Your strikes poison. Poisoned enemies that die burst into a cloud of spores that poisons everything nearby.")
                        .unlock(15).levels(5, 10).cost(1, 1)
                        .node(3, 1, THORN_SWEEP)
                        .fraction("poison", 0.03, "Poison damage per pulse per stack as a fraction of weapon damage")
                        .fraction("poison_per_level", 0.008, "Extra poison per level")
                        .param("burst_radius", 3.5, "Spore burst radius")
                        .param("burst_stacks", 2, "Poison stacks from a spore burst")
                        .stat("POISON / STACK", c -> String.format("%,.0f", c.weaponDamage() * (c.param("poison") + c.param("poison_per_level") * Math.max(0, c.abilityLevel() - 1))))
                        .stat("SPORE BURST", AbilityText.param("burst_radius", "m", 1))
                        .upgrades(Upgrades.create().perLevel("% POISON", 0.8, "").build())
                        .build())
                .ability(AbilityDefinition.builder(OVERGROWTH, AbilityKind.ACTIVE)
                        .name("Overgrowth")
                        .description("The battlefield turns wild. The ground around you erupts in vines, roots and flowers; enemies inside are slowed, poisoned and torn apart.")
                        .unlock(30).levels(5, 15).cost(2, 1)
                        .node(2, 2, ENTANGLING_ROOTS, VENOM_BLOOM)
                        .standard(5.5, 1.5, 16.0)
                        .param("radius", 8.5, "Radius of the overgrown ground")
                        .param("radius_per_level", 0.75, "Extra radius per level")
                        .param("duration", 7, "Seconds the overgrowth lasts")
                        .param("duration_per_level", 0.5, "Extra seconds per level")
                        .fraction("pulse_fraction", 0.1, "Damage fraction per pulse (2 pulses/s)")
                        .stat("TOTAL DAMAGE", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("pulse_fraction") * 2
                                * (c.param("duration") + c.param("duration_per_level") * Math.max(0, c.abilityLevel() - 1))))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.75, "m").perLevel("DURATION", 0.5, "s").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "ability_execution")
                        .executor(BloomfallAbilities::overgrowth)
                        .build())
                .ability(AbilityDefinition.builder(WRATH_OF_THE_WILD, AbilityKind.ULTIMATE)
                        .name("Wrath of the Wild")
                        .description("ULTIMATE. A colossal flower blooms where you aim and drags every enemy into its petals, then the earth around it explodes in thorns.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 3, OVERGROWTH)
                        .standard(15.0, 3.5, 95.0)
                        .param("range", 28, "Cast distance")
                        .param("radius", 13, "Radius of the bloom's pull")
                        .param("radius_per_level", 2, "Extra radius per level")
                        .param("pull_time", 3, "Seconds the flower pulls enemies in")
                        .param("pull", 0.3, "Pull strength")
                        .fraction("thorn_fraction", 0.8, "Damage fraction of the thorn eruption")
                        .param("poison_stacks", 5, "Poison stacks from the thorns")
                        .stat("THORN DAMAGE", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("thorn_fraction")))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 2, "m").build())
                        .animations("charge", "ability_activation", "ability_execution", "recovery")
                        .executor(BloomfallAbilities::wrathOfTheWild)
                        .build())
                .build();
    }
}
