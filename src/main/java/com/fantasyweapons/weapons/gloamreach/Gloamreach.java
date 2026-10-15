package com.fantasyweapons.weapons.gloamreach;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * GLOAMREACH — The void mage's staff: curses that do not strike but rot - slow, growing, inescapable.
 * <pre>
 *          [WITHERING CURSE] (5)
 *             /            \
 *   [DECAY] (10, passive)  [MIASMA] (20)
 *             \            /
 *          [DIRGE OF RUIN] (100, ultimate)
 * </pre>
 */
public final class Gloamreach {
    public static final String WITHERING_CURSE = "withering_curse";
    public static final String DECAY = "decay";
    public static final String MIASMA = "miasma";
    public static final String DIRGE_OF_RUIN = "dirge_of_ruin";

    private Gloamreach() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("gloamreach")
                .name("Gloamreach", "The Long Dusk")
                .lore("It was found standing upright in the ashes of a plague city, with nothing alive for a mile around it. It does not kill quickly. It has never needed to.",
                        "the Black Register")
                .element(Element.VOID)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.STAFF)
                .damage(7.5f, 1.6f)
                .crit(0.06f, 1.7f)
                .theme(0x9B4DFF, 0xE2C8FF)
                .ability(AbilityDefinition.builder(WITHERING_CURSE, AbilityKind.ACTIVE)
                        .name("Withering Curse")
                        .description("Curse the enemy in your sights. The curse eats at it twice a second and grows hungrier with every bite, and it drags at its limbs.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(0.22, 0.4, 8.0)
                        .param("range", 30, "Reach of the curse")
                        .param("duration", 10, "Seconds the curse lasts")
                        .param("duration_per_level", 1, "Extra seconds per ability level")
                        .stat("FIRST BITE", AbilityText.damage())
                        .stat("GROWS TO", c -> String.format("%,.0f", c.fullChargeDamage() * 3))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("DURATION", 1, "s").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(GloamreachAbilities::witheringCurse)
                        .build())
                .ability(AbilityDefinition.builder(DECAY, AbilityKind.PASSIVE)
                        .name("Decay")
                        .description("Every curse of the Gloamreach also rots its victim: it takes more damage from everything, and every healing it receives is halved.")
                        .unlock(10).levels(5, 10).cost(1, 1)
                        .node(1, 1, WITHERING_CURSE)
                        .fraction("vulnerability", 0.1, "Extra damage taken by the cursed")
                        .fraction("vulnerability_per_level", 0.03, "Extra per ability level")
                        .stat("MORE DAMAGE TAKEN", AbilityText.percent("vulnerability", "vulnerability_per_level"))
                        .stat("HEALING", c -> "-50%")
                        .upgrades(Upgrades.create().perLevel("% DAMAGE TAKEN", 3, "").build())
                        .build())
                .ability(AbilityDefinition.builder(MIASMA, AbilityKind.ACTIVE)
                        .name("Miasma")
                        .description("Breathe a cloud of void miasma over the place you point. Every second spent in it lays another layer of rot on an enemy, each eating at it and sapping its strength.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(3, 1, WITHERING_CURSE)
                        .standard(0.06, 0.6, 14.0)
                        .param("range", 26, "Cast distance")
                        .param("radius", 5, "Radius of the cloud")
                        .param("radius_per_level", 0.4, "Extra radius per ability level")
                        .param("duration", 8, "Seconds the cloud hangs")
                        .param("rot_duration", 6, "Seconds the rot lasts once it takes hold")
                        .stat("PER LAYER / HALF SECOND", AbilityText.damage())
                        .stat("LAYERS", c -> "UP TO 10")
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.4, "m").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(GloamreachAbilities::miasma)
                        .build())
                .ability(AbilityDefinition.builder(DIRGE_OF_RUIN, AbilityKind.ULTIMATE)
                        .name("Dirge of Ruin")
                        .description("ULTIMATE. Sound the dirge over everything far ahead. Every enemy that hears it is marked for ruin: it bleeds away twice a second, its steps drag, and nothing can heal it until the dirge is done.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 2, DECAY, MIASMA)
                        .standard(0.7, 2.0, 100.0)
                        .param("range", 30, "Cast distance")
                        .param("radius", 12, "Reach of the dirge")
                        .param("radius_per_level", 1, "Extra reach per level")
                        .param("duration", 12, "Seconds of ruin")
                        .param("duration_per_level", 2, "Extra seconds per level")
                        .stat("PER HALF SECOND", AbilityText.damage())
                        .stat("TOTAL", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("duration") * 2))
                        .stat("REACH", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("REACH", 1, "m").perLevel("DURATION", 2, "s").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(GloamreachAbilities::dirgeOfRuin)
                        .build())
                .build();
    }
}
