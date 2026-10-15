package com.fantasyweapons.weapons.frostbound;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * FROSTBOUND GRIMOIRE — The ice mage's spellbook: defense by stillness - foes frozen in place, blows turned aside.
 * <pre>
 *            [FROZEN TOMB] (5)
 *             /            \
 *   [RIME ARMOR] (10, passive)  [SHATTERGUARD] (20)
 *             \            /
 *         [ABSOLUTE WINTER] (100, ultimate)
 * </pre>
 */
public final class FrostboundGrimoire {
    public static final String FROZEN_TOMB = "frozen_tomb";
    public static final String RIME_ARMOR = "rime_armor";
    public static final String SHATTERGUARD = "shatterguard";
    public static final String ABSOLUTE_WINTER = "absolute_winter";

    private FrostboundGrimoire() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("frostbound_grimoire")
                .name("Frostbound Grimoire", "The Book That Keeps")
                .lore("Its clasp has been frozen shut for a thousand winters, yet it reads itself to whoever holds it. Every word is a stillness; every page, a moment that refuses to end.",
                        "the Glacier Scrolls")
                .element(Element.ICE)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.TOME)
                .damage(7f, 1.6f)
                .crit(0.08f, 1.7f)
                .theme(0xA8E0FF, 0xF4FEFF)
                .ability(AbilityDefinition.builder(FROZEN_TOMB, AbilityKind.ACTIVE)
                        .name("Frozen Tomb")
                        .description("Seal the enemy in your sights in a tomb of ice. It can neither move nor strike until the tomb shatters, wounding it.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(1.6, 0.5, 10.0)
                        .param("range", 28, "Reach of the spell")
                        .param("freeze", 2.5, "Seconds frozen")
                        .param("freeze_per_level", 0.25, "Extra seconds per ability level")
                        .stat("SHATTER DAMAGE", AbilityText.damage())
                        .stat("FROZEN FOR", AbilityText.scaled("freeze", "freeze_per_level", "s", 2))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("FROZEN", 0.25, "s").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(FrostboundAbilities::frozenTomb)
                        .build())
                .ability(AbilityDefinition.builder(RIME_ARMOR, AbilityKind.PASSIVE)
                        .name("Rime Armor")
                        .description("Every spell you cast from the grimoire lays a plate of rime over you: up to three, each sparing you a part of every blow.")
                        .unlock(10).levels(5, 10).cost(1, 1)
                        .node(1, 1, FROZEN_TOMB)
                        .param("duration", 8, "Seconds the rime lasts")
                        .param("duration_per_level", 1, "Extra seconds per ability level")
                        .stat("PER PLATE", c -> "-6% DAMAGE")
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .upgrades(Upgrades.create().perLevel("DURATION", 1, "s").build())
                        .build())
                .ability(AbilityDefinition.builder(SHATTERGUARD, AbilityKind.ACTIVE)
                        .name("Shatterguard")
                        .description("Three ice shards circle the ally in your sights (or you). Each turns one blow aside entirely, then flies at the attacker and freezes it.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(3, 1, FROZEN_TOMB)
                        .standard(0, 0.4, 14.0)
                        .param("range", 30, "Reach to an ally")
                        .param("shards", 3, "Shards circling the ally")
                        .param("duration", 12, "Seconds the shards circle")
                        .param("duration_per_level", 2, "Extra seconds per ability level")
                        .stat("BLOWS TURNED", AbilityText.param("shards", "", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("DURATION", 2, "s").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(FrostboundAbilities::shatterguard)
                        .build())
                .ability(AbilityDefinition.builder(ABSOLUTE_WINTER, AbilityKind.ULTIMATE)
                        .name("Absolute Winter")
                        .description("ULTIMATE. Call a blizzard down far ahead. Enemies caught in it slow, then freeze solid; allies within it are wrapped in frost that spares them a part of every blow.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 2, RIME_ARMOR, SHATTERGUARD)
                        .standard(10.0, 2.0, 100.0)
                        .param("range", 30, "Cast distance")
                        .param("radius", 10, "Radius of the blizzard")
                        .param("radius_per_level", 1, "Extra radius per level")
                        .param("duration", 8, "Seconds the blizzard rages")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .param("freeze", 2, "Seconds an enemy stays frozen once the cold takes it")
                        .fraction("ward", 0.25, "Damage allies inside are spared")
                        .fraction("pulse_fraction", 0.05, "Damage fraction to enemies inside, every second")
                        .fraction("freeze_fraction", 0.15, "Damage fraction as an enemy freezes solid")
                        .stat("PER SECOND", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("pulse_fraction")))
                        .stat("ALLIES TAKE", c -> "-" + Math.round(c.param("ward") * 100) + "%")
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 1, "m").perLevel("DURATION", 1, "s").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(FrostboundAbilities::absoluteWinter)
                        .build())
                .build();
    }
}
