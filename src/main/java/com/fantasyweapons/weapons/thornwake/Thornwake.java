package com.fantasyweapons.weapons.thornwake;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * THORNWAKE — The nature mage's staff: healing that grows where it is planted, from a single flower to a great tree.
 * <pre>
 *            [LIFEBLOOM] (5)
 *             /            \
 *   [VERDANT PULSE] (10, passive)  [GRASPING GROVE] (20)
 *             \            /
 *        [WORLDTREE'S BOON] (100, ultimate)
 * </pre>
 */
public final class Thornwake {
    public static final String LIFEBLOOM = "lifebloom";
    public static final String VERDANT_PULSE = "verdant_pulse";
    public static final String GRASPING_GROVE = "grasping_grove";
    public static final String WORLDTREES_BOON = "worldtrees_boon";

    private Thornwake() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("thornwake")
                .name("Thornwake", "The Gardener's Staff")
                .lore("A branch of the first tree, still green after ten thousand years. Where its heel touches bare stone, moss creeps out; where it touches the wounded, flesh remembers how to grow.",
                        "the Verdant Annals")
                .element(Element.NATURE)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.STAFF)
                .damage(7.5f, 1.6f)
                .crit(0.06f, 1.7f)
                .theme(0x5BE063, 0xFFE08A)
                .onHit(ThornwakeAbilities::onHit)
                .ability(AbilityDefinition.builder(LIFEBLOOM, AbilityKind.ACTIVE)
                        .name("Lifebloom")
                        .description("Sow a seed where you point. It bursts into a great flower that breathes out healing every second to all allies around it.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(0, 0.5, 14.0)
                        .param("range", 26, "Cast distance")
                        .param("radius", 5, "Reach of the flower's healing")
                        .param("radius_per_level", 0.4, "Extra reach per ability level")
                        .param("duration", 8, "Seconds the flower blooms")
                        .fraction("heal", 0.05, "Healing every second, as a share of each ally's health")
                        .stat("HEAL / SECOND", c -> Math.round(c.param("heal") * 100 * (1 + 0.12 * Math.max(0, c.abilityLevel() - 1))) + "% HEALTH")
                        .stat("REACH", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("% HEALING", 12, "").perLevel("REACH", 0.4, "m").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(ThornwakeAbilities::lifebloom)
                        .build())
                .ability(AbilityDefinition.builder(VERDANT_PULSE, AbilityKind.PASSIVE)
                        .name("Verdant Pulse")
                        .description("Every bolt you land sends a pulse of life to the most wounded ally near you, or to you.")
                        .unlock(10).levels(5, 10).cost(1, 1)
                        .node(1, 1, LIFEBLOOM)
                        .param("radius", 12, "How far the pulse reaches")
                        .fraction("heal", 0.04, "Healing per pulse, as a share of the ally's health")
                        .fraction("heal_per_level", 0.01, "Extra healing per ability level")
                        .stat("HEAL / HIT", c -> Math.round((c.param("heal") + c.param("heal_per_level") * Math.max(0, c.abilityLevel() - 1)) * 100) + "% HEALTH")
                        .stat("REACH", AbilityText.param("radius", "m", 0))
                        .upgrades(Upgrades.create().perLevel("% HEALING", 1, "").build())
                        .build())
                .ability(AbilityDefinition.builder(GRASPING_GROVE, AbilityKind.ACTIVE)
                        .name("Grasping Grove")
                        .description("A thicket springs up where you point. Roots seize the enemies within and their thorns poison them, while allies among its leaves are filled with growing life.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(3, 1, LIFEBLOOM)
                        .standard(1.0, 0.6, 14.0)
                        .param("range", 24, "Cast distance")
                        .param("radius", 5, "Radius of the grove")
                        .param("radius_per_level", 0.4, "Extra radius per ability level")
                        .param("duration", 6, "Seconds the grove stands")
                        .param("root", 1.5, "Seconds an enemy is held by the roots")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("ROOTED", AbilityText.param("root", "s", 1))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.4, "m").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(ThornwakeAbilities::graspingGrove)
                        .build())
                .ability(AbilityDefinition.builder(WORLDTREES_BOON, AbilityKind.ULTIMATE)
                        .name("Worldtree's Boon")
                        .description("ULTIMATE. Raise a seedling of the Worldtree far ahead and watch it grow in moments into a vast tree. Its canopy heals every ally beneath it each second and washes away every curse.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 2, VERDANT_PULSE, GRASPING_GROVE)
                        .standard(0, 2.5, 100.0)
                        .param("range", 30, "Cast distance")
                        .param("radius", 14, "Reach of the canopy")
                        .param("radius_per_level", 1.5, "Extra reach per level")
                        .param("duration", 10, "Seconds the tree stands")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .fraction("heal", 0.07, "Healing every second, as a share of each ally's health")
                        .stat("HEAL / SECOND", c -> Math.round(c.param("heal") * 100) + "% HEALTH")
                        .stat("REACH", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("REACH", 1.5, "m").perLevel("DURATION", 1, "s").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(ThornwakeAbilities::worldtreesBoon)
                        .build())
                .build();
    }
}
