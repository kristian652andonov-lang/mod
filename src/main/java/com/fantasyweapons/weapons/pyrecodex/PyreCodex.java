package com.fantasyweapons.weapons.pyrecodex;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * PYRE CODEX — The Book of Pyres. The fire mage's spellbook: fire that leaps, spreads and walls the way.
 * <pre>
 *            [SEARING CHAIN] (5)
 *             /            \
 *   [WILDFIRE] (10, passive)  [WALL OF PYRES] (20)
 *             \            /
 *            [PHOENIX] (100, ultimate)
 * </pre>
 */
public final class PyreCodex {
    public static final String SEARING_CHAIN = "searing_chain";
    public static final String WILDFIRE = "wildfire";
    public static final String WALL_OF_PYRES = "wall_of_pyres";
    public static final String PHOENIX = "phoenix";

    private PyreCodex() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("pyre_codex")
                .name("Pyre Codex", "The Book of Pyres")
                .lore("Every page was a funeral pyre once, its smoke pressed flat between iron covers. Read a line aloud and the fire that burned it answers, hungry for more.",
                        "the Cinder Liturgy")
                .element(Element.FIRE)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.TOME)
                .damage(8f, 1.6f)
                .crit(0.1f, 1.7f)
                .theme(0xFF8A3A, 0xFFE0A0)
                .ability(AbilityDefinition.builder(SEARING_CHAIN, AbilityKind.ACTIVE)
                        .name("Searing Chain")
                        .description("Hurl a lash of fire at the foe in your sights. It burns through it and leaps on to the enemies nearest, setting each one ablaze.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.6, 0.4, 7.0)
                        .param("range", 28, "Reach of the first strike")
                        .param("jumps", 4, "Enemies the fire leaps on to")
                        .param("jumps_per_level", 0.5, "Extra leaps per ability level")
                        .param("jump_range", 7, "How far each leap reaches")
                        .fraction("falloff", 0.85, "Damage kept at each leap")
                        .fraction("sear", 0.07, "Burn damage per pulse as a fraction of weapon damage")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("LEAPS", c -> String.valueOf((int) Math.floor(c.param("jumps") + c.param("jumps_per_level") * Math.max(0, c.abilityLevel() - 1))))
                        .stat("RANGE", AbilityText.param("range", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("LEAPS", 0.5, "").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(PyreCodexAbilities::searingChain)
                        .build())
                .ability(AbilityDefinition.builder(WILDFIRE, AbilityKind.PASSIVE)
                        .name("Wildfire")
                        .description("A burning foe you slay bursts apart in flame, setting every enemy around it alight.")
                        .unlock(10).levels(5, 10).cost(1, 1)
                        .node(1, 1, SEARING_CHAIN)
                        .param("radius", 4, "Reach of the burst")
                        .param("radius_per_level", 0.5, "Extra reach per ability level")
                        .fraction("burst", 0.6, "Burst damage as a fraction of weapon damage")
                        .fraction("sear", 0.06, "Burn damage per pulse as a fraction of weapon damage")
                        .stat("BURST", c -> String.format("%,.0f", c.weaponDamage() * c.param("burst")))
                        .stat("REACH", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .upgrades(Upgrades.create().perLevel("REACH", 0.5, "m").build())
                        .build())
                .ability(AbilityDefinition.builder(WALL_OF_PYRES, AbilityKind.ACTIVE)
                        .name("Wall of Pyres")
                        .description("Raise a roaring wall of fire across the ground where you point. Enemies that touch it are thrown back burning, and it scorches all that stand in it.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(3, 1, SEARING_CHAIN)
                        .standard(0.6, 0.6, 12.0)
                        .param("range", 24, "Cast distance")
                        .param("length", 9, "Length of the wall")
                        .param("length_per_level", 1, "Extra length per ability level")
                        .param("duration", 6, "Seconds the wall burns")
                        .param("knockback", 0.9, "How hard the wall throws enemies back")
                        .stat("DAMAGE / HALF SECOND", AbilityText.damage())
                        .stat("LENGTH", AbilityText.scaled("length", "length_per_level", "m", 0))
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("LENGTH", 1, "m").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(PyreCodexAbilities::wallOfPyres)
                        .build())
                .ability(AbilityDefinition.builder(PHOENIX, AbilityKind.ULTIMATE)
                        .name("Phoenix")
                        .description("ULTIMATE. Call a phoenix of living flame over the place you point. It wheels above the battle, diving through every enemy in its path, then plunges into the heart of it in a storm of fire.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 2, WILDFIRE, WALL_OF_PYRES)
                        .standard(15.0, 2.0, 100.0)
                        .param("range", 32, "Cast distance")
                        .param("radius", 8, "Radius of the phoenix's flight")
                        .param("radius_per_level", 1, "Extra radius per level")
                        .param("duration", 6, "Seconds the phoenix flies")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .fraction("pass_fraction", 0.06, "Damage fraction each time it sweeps through an enemy")
                        .fraction("crash_fraction", 0.6, "Damage fraction of the final plunge")
                        .param("crash_radius", 7, "Radius of the final plunge")
                        .stat("PLUNGE", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("crash_fraction")))
                        .stat("PER PASS", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("pass_fraction")))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 1, "m").perLevel("DURATION", 1, "s").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(PyreCodexAbilities::phoenix)
                        .build())
                .build();
    }
}
