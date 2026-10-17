package com.fantasyweapons.weapons.lumenpsalter;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * LUMEN PSALTER — The light mage's spellbook: hymns that quicken, halos that steel, and an ascension that turns every
 * ally's blow into a ray of light.
 * <pre>
 *          [HYMN OF SWIFTNESS] (5)
 *             /            \
 *   [CHORUS] (10, passive)  [HALO OF FORTITUDE] (20)
 *             \            /
 *            [ASCENSION] (100, ultimate)
 * </pre>
 */
public final class LumenPsalter {
    public static final String HYMN_OF_SWIFTNESS = "hymn_of_swiftness";
    public static final String CHORUS = "chorus";
    public static final String HALO_OF_FORTITUDE = "halo_of_fortitude";
    public static final String ASCENSION = "ascension";

    private LumenPsalter() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("lumen_psalter")
                .name("Lumen Psalter", "The Singing Book")
                .lore("Its psalms were written by candlelight in a monastery that no longer exists, and they have never stopped being sung. Hold it open and you will hear the choir, and so will your friends.",
                        "the Matins of Lumen")
                .element(Element.LIGHT)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.TOME)
                .damage(6.5f, 1.6f)
                .crit(0.08f, 1.7f)
                .theme(0xFFF0B0, 0xFFFFFF)
                .ability(AbilityDefinition.builder(HYMN_OF_SWIFTNESS, AbilityKind.ACTIVE)
                        .name("Hymn of Swiftness")
                        .description("Sing a hymn over the ally in your sights (or yourself) and those around them: they run and leap further, work faster and strike quicker.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(0, 0.3, 12.0)
                        .param("range", 30, "Reach to an ally")
                        .param("radius", 5, "Allies around the target who hear the hymn")
                        .param("duration", 15, "Seconds the hymn lasts")
                        .fraction("attack_speed", 0.2, "Faster attacks")
                        .fraction("attack_speed_per_level", 0.05, "Faster still per ability level")
                        .stat("FASTER ATTACKS", AbilityText.percent("attack_speed", "attack_speed_per_level"))
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("% ATTACK SPEED", 5, "").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(LumenPsalterAbilities::hymnOfSwiftness)
                        .build())
                .ability(AbilityDefinition.builder(CHORUS, AbilityKind.PASSIVE)
                        .name("Chorus")
                        .description("Every ally you bless joins your chorus, and their voices strengthen you: more damage for each, up to five.")
                        .unlock(10).levels(5, 10).cost(1, 1)
                        .node(1, 1, HYMN_OF_SWIFTNESS)
                        .param("duration", 10, "Seconds each voice lasts")
                        .param("duration_per_level", 2, "Extra seconds per ability level")
                        .stat("PER VOICE", c -> "+4% DAMAGE")
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .upgrades(Upgrades.create().perLevel("DURATION", 2, "s").build())
                        .build())
                .ability(AbilityDefinition.builder(HALO_OF_FORTITUDE, AbilityKind.ACTIVE)
                        .name("Halo of Fortitude")
                        .description("Crown the ally in your sights (or yourself) with a halo: they shrug off much of every blow, cannot be knocked back, and are wrapped in a shield of light.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(3, 1, HYMN_OF_SWIFTNESS)
                        .standard(0, 0.5, 16.0)
                        .param("range", 30, "Reach to an ally")
                        .param("duration", 12, "Seconds the halo lasts")
                        .param("duration_per_level", 1, "Extra seconds per ability level")
                        .fraction("shield", 0.3, "Shield as a share of the ally's health")
                        .stat("SHIELD", c -> Math.round(c.param("shield") * 100 * (1 + 0.12 * Math.max(0, c.abilityLevel() - 1))) + "% HEALTH")
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("% SHIELD", 12, "").perLevel("DURATION", 1, "s").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(LumenPsalterAbilities::haloOfFortitude)
                        .build())
                .ability(AbilityDefinition.builder(ASCENSION, AbilityKind.ULTIMATE)
                        .name("Ascension")
                        .description("ULTIMATE. Raise every ally around the place you point into ascension. While it lasts each of their blows calls a ray of light down on its target, and they move as if borne up on wings.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 2, CHORUS, HALO_OF_FORTITUDE)
                        .standard(1.0, 2.0, 100.0)
                        .param("range", 30, "Cast distance")
                        .param("radius", 12, "Reach of the ascension")
                        .param("duration", 12, "Seconds it lasts")
                        .param("duration_per_level", 2, "Extra seconds per level")
                        .stat("RAY DAMAGE", AbilityText.damage())
                        .stat("REACH", AbilityText.param("radius", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("DURATION", 2, "s").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(LumenPsalterAbilities::ascension)
                        .build())
                .build();
    }
}
