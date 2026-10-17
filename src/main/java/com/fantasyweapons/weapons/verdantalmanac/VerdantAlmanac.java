package com.fantasyweapons.weapons.verdantalmanac;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * VERDANT ALMANAC — The nature mage's spellbook: healing that flows - vines that mend, pollen that restores, and a
 * spring that will not let its allies die.
 * <pre>
 *            [MENDING VINES] (5)
 *             /            \
 *   [PHOTOSYNTHESIS] (10, passive)  [POLLEN BLOOM] (20)
 *             \            /
 *          [SPRING ETERNAL] (100, ultimate)
 * </pre>
 */
public final class VerdantAlmanac {
    public static final String MENDING_VINES = "mending_vines";
    public static final String PHOTOSYNTHESIS = "photosynthesis";
    public static final String POLLEN_BLOOM = "pollen_bloom";
    public static final String SPRING_ETERNAL = "spring_eternal";

    private VerdantAlmanac() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("verdant_almanac")
                .name("Verdant Almanac", "The Book of Seasons")
                .lore("Each page records a spring that has not yet come. Read one aloud and it arrives early: the sick rise, the dead wood buds, and the hurt forget they were ever hurt.",
                        "the Gardener's Calendar")
                .element(Element.NATURE)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.TOME)
                .damage(6.5f, 1.6f)
                .crit(0.06f, 1.7f)
                .theme(0x7DE36A, 0xF6FFD8)
                .ability(AbilityDefinition.builder(MENDING_VINES, AbilityKind.ACTIVE)
                        .name("Mending Vines")
                        .description("Bind the ally in your sights (or yourself) to the book with a living vine. Through it, life flows into them twice a second while it holds.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(0, 0.3, 10.0)
                        .param("range", 30, "Reach to an ally")
                        .param("duration", 5, "Seconds the vine holds")
                        .fraction("heal", 0.035, "Healing every half second, as a share of the ally's health")
                        .stat("HEAL / SECOND", c -> Math.round(c.param("heal") * 200 * (1 + 0.12 * Math.max(0, c.abilityLevel() - 1))) + "% HEALTH")
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("% HEALING", 12, "").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(VerdantAlmanacAbilities::mendingVines)
                        .build())
                .ability(AbilityDefinition.builder(PHOTOSYNTHESIS, AbilityKind.PASSIVE)
                        .name("Photosynthesis")
                        .description("Under the open sky by day, the Almanac's healing grows stronger, and while you hold it in the sun you slowly mend.")
                        .unlock(10).levels(5, 10).cost(1, 1)
                        .node(1, 1, MENDING_VINES)
                        .fraction("bonus", 0.2, "Stronger healing in daylight")
                        .fraction("bonus_per_level", 0.05, "Extra per ability level")
                        .fraction("regen", 0.02, "Healing every two seconds in the sun, as a share of your health")
                        .stat("DAYLIGHT HEALING", c -> "+" + Math.round((c.param("bonus") + c.param("bonus_per_level") * Math.max(0, c.abilityLevel() - 1)) * 100) + "%")
                        .upgrades(Upgrades.create().perLevel("% DAYLIGHT HEALING", 5, "").build())
                        .build())
                .ability(AbilityDefinition.builder(POLLEN_BLOOM, AbilityKind.ACTIVE)
                        .name("Pollen Bloom")
                        .description("Scatter a golden cloud of pollen where you point. Allies who breathe it regenerate and work faster; enemies choke on it, poisoned.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(3, 1, MENDING_VINES)
                        .standard(0.4, 0.5, 14.0)
                        .param("range", 26, "Cast distance")
                        .param("radius", 6, "Radius of the cloud")
                        .param("radius_per_level", 0.4, "Extra radius per ability level")
                        .param("duration", 8, "Seconds the cloud hangs")
                        .stat("POISON / SECOND", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.4, "m").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(VerdantAlmanacAbilities::pollenBloom)
                        .build())
                .ability(AbilityDefinition.builder(SPRING_ETERNAL, AbilityKind.ULTIMATE)
                        .name("Spring Eternal")
                        .description("ULTIMATE. Call spring down on every ally around the place you point. For a while they heal every second, and the first blow that would kill each of them leaves them standing, whole again.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 2, PHOTOSYNTHESIS, POLLEN_BLOOM)
                        .standard(0, 2.0, 100.0)
                        .param("range", 30, "Cast distance")
                        .param("radius", 20, "Reach of the blessing")
                        .param("duration", 10, "Seconds spring lasts")
                        .param("duration_per_level", 2, "Extra seconds per level")
                        .fraction("heal", 0.045, "Healing every second, as a share of each ally's health")
                        .fraction("save", 0.4, "Health an ally is left with when spring saves them")
                        .stat("HEAL / SECOND", c -> Math.round(c.param("heal") * 100) + "% HEALTH")
                        .stat("SAVED AT", c -> Math.round(c.param("save") * 100) + "% HEALTH")
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("DURATION", 2, "s").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(VerdantAlmanacAbilities::springEternal)
                        .build())
                .build();
    }
}
