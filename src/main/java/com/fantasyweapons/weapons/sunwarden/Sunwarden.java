package com.fantasyweapons.weapons.sunwarden;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * SUNWARDEN — The light mage's staff: it makes the people around it stronger - blessings of valor, hallowed ground
 * and the dawn itself.
 * <pre>
 *          [BLESSING OF VALOR] (5)
 *             /            \
 *   [RADIANCE] (10, passive)  [SANCTIFIED GROUND] (20)
 *             \            /
 *          [DAWNBRINGER] (100, ultimate)
 * </pre>
 */
public final class Sunwarden {
    public static final String BLESSING_OF_VALOR = "blessing_of_valor";
    public static final String RADIANCE = "radiance";
    public static final String SANCTIFIED_GROUND = "sanctified_ground";
    public static final String DAWNBRINGER = "dawnbringer";

    private Sunwarden() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("sunwarden")
                .name("Sunwarden", "Herald of the Dawn")
                .lore("Raised over the walls of Halcyon on the morning of the last siege. Those who saw its light that day fought like a hundred, and the songs say none of them felt afraid.",
                        "the Halcyon Canticles")
                .element(Element.LIGHT)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.STAFF)
                .damage(7.5f, 1.6f)
                .crit(0.08f, 1.7f)
                .theme(0xFFC94A, 0xFFF6D0)
                .ability(AbilityDefinition.builder(BLESSING_OF_VALOR, AbilityKind.ACTIVE)
                        .name("Blessing of Valor")
                        .description("Bless the ally in your sights (or yourself) with valor: for a while every blow they strike lands much harder.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(0, 0.4, 12.0)
                        .param("range", 30, "Reach to an ally")
                        .fraction("bonus", 0.25, "Extra damage dealt")
                        .fraction("bonus_per_level", 0.05, "Extra damage per ability level")
                        .param("duration", 12, "Seconds the blessing lasts")
                        .stat("MORE DAMAGE", AbilityText.percent("bonus", "bonus_per_level"))
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("% DAMAGE", 5, "").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(SunwardenAbilities::blessingOfValor)
                        .build())
                .ability(AbilityDefinition.builder(RADIANCE, AbilityKind.PASSIVE)
                        .name("Radiance")
                        .description("While you hold the Sunwarden its light falls on the allies around you, and their blows land harder.")
                        .unlock(10).levels(5, 10).cost(1, 1)
                        .node(1, 1, BLESSING_OF_VALOR)
                        .param("radius", 10, "Reach of the light")
                        .fraction("bonus", 0.06, "Extra damage dealt by allies in the light")
                        .fraction("bonus_per_level", 0.02, "Extra per ability level")
                        .stat("MORE DAMAGE", AbilityText.percent("bonus", "bonus_per_level"))
                        .stat("REACH", AbilityText.param("radius", "m", 0))
                        .upgrades(Upgrades.create().perLevel("% DAMAGE", 2, "").build())
                        .build())
                .ability(AbilityDefinition.builder(SANCTIFIED_GROUND, AbilityKind.ACTIVE)
                        .name("Sanctified Ground")
                        .description("Hallow the ground where you point. Allies standing on it are steeled against harm, mend and strike harder; the undead burn upon it.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(3, 1, BLESSING_OF_VALOR)
                        .standard(0.5, 0.6, 16.0)
                        .param("range", 26, "Cast distance")
                        .param("radius", 6, "Radius of the hallowed ground")
                        .param("radius_per_level", 0.5, "Extra radius per ability level")
                        .param("duration", 10, "Seconds the ground stays hallowed")
                        .fraction("bonus", 0.15, "Extra damage dealt by allies on it")
                        .stat("UNDEAD BURN / SECOND", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(SunwardenAbilities::sanctifiedGround)
                        .build())
                .ability(AbilityDefinition.builder(DAWNBRINGER, AbilityKind.ULTIMATE)
                        .name("Dawnbringer")
                        .description("ULTIMATE. Call the dawn down in a pillar of sunlight far ahead. Every ally it touches is filled with its light: far mightier, swifter and harder to harm. Enemies are blinded by it, and the undead burn.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 2, RADIANCE, SANCTIFIED_GROUND)
                        .standard(3.0, 2.0, 100.0)
                        .param("range", 30, "Cast distance")
                        .param("radius", 14, "Reach of the dawn")
                        .param("radius_per_level", 1, "Extra reach per level")
                        .param("duration", 15, "Seconds the blessing lasts")
                        .param("duration_per_level", 2, "Extra seconds per level")
                        .fraction("bonus", 0.6, "Extra damage dealt by blessed allies")
                        .stat("MORE DAMAGE", c -> "+" + Math.round(c.param("bonus") * 100) + "%")
                        .stat("UNDEAD DAMAGE", AbilityText.damage())
                        .stat("REACH", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("REACH", 1, "m").perLevel("DURATION", 2, "s").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(SunwardenAbilities::dawnbringer)
                        .build())
                .build();
    }
}
