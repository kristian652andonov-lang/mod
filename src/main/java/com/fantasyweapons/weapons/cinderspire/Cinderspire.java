package com.fantasyweapons.weapons.cinderspire;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * CINDERSPIRE — The Ember Spire. The fire mage's staff: pure offense, laid from afar.
 * <pre>
 *            [METEOR BRAND] (5)
 *             /            \
 *   [KINDLING] (10, passive)  [FLAME GEYSERS] (20)
 *             \            /
 *     [HEART OF THE VOLCANO] (100, ultimate)
 * </pre>
 */
public final class Cinderspire {
    public static final String METEOR_BRAND = "meteor_brand";
    public static final String KINDLING = "kindling";
    public static final String FLAME_GEYSERS = "flame_geysers";
    public static final String HEART_OF_THE_VOLCANO = "heart_of_the_volcano";

    private Cinderspire() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("cinderspire")
                .name("Cinderspire", "The Ember Spire")
                .lore("Carved from the throat of a sleeping volcano, the spire still dreams of the day the mountain woke. Point it, and the sky remembers how to burn.",
                        "the Ash Psalms")
                .element(Element.FIRE)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.STAFF)
                .damage(9f, 1.6f)
                .crit(0.08f, 1.8f)
                .theme(0xFF6A1F, 0xFFD38A)
                .onHit(CinderspireAbilities::onHit)
                .ability(AbilityDefinition.builder(METEOR_BRAND, AbilityKind.ACTIVE)
                        .name("Meteor Brand")
                        .description("Brand the ground far ahead with a burning sigil. A moment later a meteor tears down out of the sky onto it, blasting and searing everything near.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(3.6, 0.8, 8.0)
                        .param("range", 30, "Cast distance")
                        .param("radius", 4.5, "Blast radius")
                        .param("radius_per_level", 0.4, "Extra radius per ability level")
                        .param("delay", 22, "Ticks between the brand and the meteor")
                        .fraction("sear", 0.08, "Burn damage per pulse as a fraction of weapon damage")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("RANGE", AbilityText.param("range", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.4, "m").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(CinderspireAbilities::meteorBrand)
                        .build())
                .ability(AbilityDefinition.builder(KINDLING, AbilityKind.PASSIVE)
                        .name("Kindling")
                        .description("Every fire you land lays a Kindle mark on the foe. At five marks it combusts, bursting in flame that scorches everything around it.")
                        .unlock(10).levels(5, 10).cost(1, 1)
                        .node(1, 1, METEOR_BRAND)
                        .param("marks", 5, "Kindle marks that make a foe combust")
                        .fraction("combust", 1.2, "Combustion damage as a fraction of weapon damage")
                        .fraction("combust_per_level", 0.15, "Extra combustion damage per ability level")
                        .param("radius", 3, "Combustion radius")
                        .stat("COMBUSTION", c -> String.format("%,.0f", c.weaponDamage() * (c.param("combust") + c.param("combust_per_level") * Math.max(0, c.abilityLevel() - 1))))
                        .stat("MARKS", AbilityText.param("marks", "", 0))
                        .upgrades(Upgrades.create().perLevel("% COMBUSTION", 15, "").build())
                        .build())
                .ability(AbilityDefinition.builder(FLAME_GEYSERS, AbilityKind.ACTIVE)
                        .name("Flame Geysers")
                        .description("A row of fire geysers erupts across the ground where you point, one after another, hurling enemies into the air in columns of flame.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(3, 1, METEOR_BRAND)
                        .standard(2.8, 0.5, 10.0)
                        .param("range", 26, "Cast distance")
                        .param("count", 5, "Geysers in the row")
                        .param("count_per_level", 0.5, "Extra geysers per ability level")
                        .param("spacing", 2.2, "Blocks between geysers")
                        .param("radius", 1.9, "Radius of each geyser")
                        .param("lift", 0.95, "How high the geysers throw enemies")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("GEYSERS", c -> String.valueOf((int) Math.floor(c.param("count") + c.param("count_per_level") * Math.max(0, c.abilityLevel() - 1))))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("GEYSERS", 0.5, "").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(CinderspireAbilities::flameGeysers)
                        .build())
                .ability(AbilityDefinition.builder(HEART_OF_THE_VOLCANO, AbilityKind.ULTIMATE)
                        .name("Heart of the Volcano")
                        .description("ULTIMATE. Tear the ground open far ahead into a lake of fire. Magma bombs burst out of it and rain across the field, until the heart of the mountain erupts in a towering column of flame.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 2, KINDLING, FLAME_GEYSERS)
                        .standard(16.0, 2.0, 100.0)
                        .param("range", 32, "Cast distance")
                        .param("radius", 10, "Radius of the lake of fire")
                        .param("radius_per_level", 1.5, "Extra radius per level")
                        .param("duration", 7, "Seconds the volcano rages")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .param("interval", 5, "Ticks between waves of magma bombs")
                        .param("bombs", 2, "Magma bombs per wave")
                        .param("bomb_radius", 3, "Blast radius of each bomb")
                        .fraction("bomb_fraction", 0.07, "Damage fraction of each bomb")
                        .fraction("lava_fraction", 0.012, "Damage fraction to everything in the lake, every half second")
                        .fraction("eruption_fraction", 0.55, "Damage fraction of the final eruption")
                        .stat("ERUPTION", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("eruption_fraction")))
                        .stat("PER BOMB", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("bomb_fraction")))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 1.5, "m").perLevel("DURATION", 1, "s").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(CinderspireAbilities::heartOfTheVolcano)
                        .build())
                .build();
    }
}
