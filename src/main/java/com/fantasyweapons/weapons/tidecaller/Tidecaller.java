package com.fantasyweapons.weapons.tidecaller;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * TIDECALLER — The water mage's staff: healing springs and protecting waves, called up from afar.
 * <pre>
 *            [TIDAL SPRING] (5)
 *             /            \
 *   [UNDERTOW] (10, passive)  [RIPTIDE SURGE] (20)
 *             \            /
 *      [MAELSTROM SANCTUARY] (100, ultimate)
 * </pre>
 */
public final class Tidecaller {
    public static final String TIDAL_SPRING = "tidal_spring";
    public static final String UNDERTOW = "undertow";
    public static final String RIPTIDE_SURGE = "riptide_surge";
    public static final String MAELSTROM_SANCTUARY = "maelstrom_sanctuary";

    private Tidecaller() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("tidecaller")
                .name("Tidecaller", "Shepherd of the Deep")
                .lore("The drowned priests of Ys raised it from the seabed and the sea came with it. Wherever it strikes the earth, clear water wells up to mend the wounded and turn the blade.",
                        "the Litany of Ys")
                .element(Element.WATER)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.STAFF)
                .damage(7f, 1.6f)
                .crit(0.06f, 1.7f)
                .theme(0x3FA8FF, 0xCDEFFF)
                .ability(AbilityDefinition.builder(TIDAL_SPRING, AbilityKind.ACTIVE)
                        .name("Tidal Spring")
                        .description("Call a spring of clear water up out of the ground where you point. Allies standing in it are healed every second and washed clean of burns, poisons and curses.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(0, 0.5, 14.0)
                        .param("range", 26, "Cast distance")
                        .param("radius", 4.5, "Radius of the spring")
                        .param("radius_per_level", 0.3, "Extra radius per ability level")
                        .param("duration", 6, "Seconds the spring flows")
                        .fraction("heal", 0.05, "Healing every second, as a share of each ally's health")
                        .stat("HEAL / SECOND", c -> Math.round(c.param("heal") * 100 * (1 + 0.12 * Math.max(0, c.abilityLevel() - 1))) + "% HEALTH")
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("% HEALING", 12, "").perLevel("RADIUS", 0.3, "m").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(TidecallerAbilities::tidalSpring)
                        .build())
                .ability(AbilityDefinition.builder(UNDERTOW, AbilityKind.PASSIVE)
                        .name("Undertow")
                        .description("Your bolts wash over the allies they pass, healing them, and soak the enemies they strike.")
                        .unlock(10).levels(5, 10).cost(1, 1)
                        .node(1, 1, TIDAL_SPRING)
                        .fraction("ally_heal", 0.05, "Healing per bolt, as a share of the ally's health")
                        .fraction("ally_heal_per_level", 0.01, "Extra healing per ability level")
                        .stat("HEAL / BOLT", c -> Math.round((c.param("ally_heal") + c.param("ally_heal_per_level") * Math.max(0, c.abilityLevel() - 1)) * 100) + "% HEALTH")
                        .upgrades(Upgrades.create().perLevel("% HEALING", 1, "").build())
                        .build())
                .ability(AbilityDefinition.builder(RIPTIDE_SURGE, AbilityKind.ACTIVE)
                        .name("Riptide Surge")
                        .description("A ring of surf bursts out from the place you point: enemies are swept away and knocked down, and every ally it washes over is wrapped in a shield of water.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(3, 1, TIDAL_SPRING)
                        .standard(1.4, 0.5, 12.0)
                        .param("range", 24, "Cast distance")
                        .param("radius", 7, "Reach of the surge")
                        .param("radius_per_level", 0.5, "Extra reach per ability level")
                        .param("knockback", 1.4, "How far enemies are swept")
                        .fraction("shield", 0.2, "Shield (share of health) given to each ally")
                        .param("shield_duration", 6, "Seconds the shield lasts")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("ALLY SHIELD", c -> Math.round(c.param("shield") * 100) + "% HEALTH")
                        .stat("REACH", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("REACH", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(TidecallerAbilities::riptideSurge)
                        .build())
                .ability(AbilityDefinition.builder(MAELSTROM_SANCTUARY, AbilityKind.ULTIMATE)
                        .name("Maelstrom Sanctuary")
                        .description("ULTIMATE. Open a great whirlpool far ahead. Allies in its calm heal swiftly and are shielded again and again; enemies are caught in its current, dragged round and round and battered by the sea.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 2, UNDERTOW, RIPTIDE_SURGE)
                        .standard(4.0, 2.0, 100.0)
                        .param("range", 30, "Cast distance")
                        .param("radius", 10, "Radius of the whirlpool")
                        .param("radius_per_level", 1, "Extra radius per level")
                        .param("duration", 8, "Seconds the whirlpool turns")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .fraction("heal", 0.06, "Healing every second, as a share of each ally's health")
                        .fraction("shield", 0.08, "Shield (share of health) laid on allies every two seconds")
                        .fraction("pulse_fraction", 0.06, "Damage fraction to enemies inside, every second")
                        .stat("HEAL / SECOND", c -> Math.round(c.param("heal") * 100) + "% HEALTH")
                        .stat("PER SECOND", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("pulse_fraction")))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 1, "m").perLevel("DURATION", 1, "s").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(TidecallerAbilities::maelstromSanctuary)
                        .build())
                .build();
    }
}
