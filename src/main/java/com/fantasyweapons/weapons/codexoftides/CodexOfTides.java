package com.fantasyweapons.weapons.codexoftides;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * CODEX OF TIDES — The water mage's spellbook: protective bubbles, healing rain and a wave that leaps from ally to ally.
 * <pre>
 *            [BUBBLE AEGIS] (5)
 *             /            \
 *   [EBB AND FLOW] (10, passive)  [HEALING RAIN] (20)
 *             \            /
 *          [TIDE'S EMBRACE] (100, ultimate)
 * </pre>
 */
public final class CodexOfTides {
    public static final String BUBBLE_AEGIS = "bubble_aegis";
    public static final String EBB_AND_FLOW = "ebb_and_flow";
    public static final String HEALING_RAIN = "healing_rain";
    public static final String TIDES_EMBRACE = "tides_embrace";

    private CodexOfTides() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("codex_of_tides")
                .name("Codex of Tides", "The Mariner's Mercy")
                .lore("Its pages never dry. Sailors lost at sea swear they heard it read aloud beneath the waves, and woke on the shore with their wounds closed and salt in their hair.",
                        "the Harbour Hymnal")
                .element(Element.WATER)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.TOME)
                .damage(6.5f, 1.6f)
                .crit(0.06f, 1.7f)
                .theme(0x5FC8FF, 0xE0F8FF)
                .ability(AbilityDefinition.builder(BUBBLE_AEGIS, AbilityKind.ACTIVE)
                        .name("Bubble Aegis")
                        .description("Wrap the ally in your sights (or yourself) in a bubble of water that drinks blows. When it bursts it washes over them, healing them; if it fades unbroken, all it still holds becomes healing.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(0, 0.4, 12.0)
                        .param("range", 30, "Reach to an ally")
                        .fraction("shield", 0.4, "Shield as a share of the ally's health")
                        .param("duration", 8, "Seconds the bubble lasts")
                        .stat("SHIELD", c -> Math.round(c.param("shield") * 100 * (1 + 0.12 * Math.max(0, c.abilityLevel() - 1))) + "% HEALTH")
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("RANGE", AbilityText.param("range", "m", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("% SHIELD", 12, "").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(CodexOfTidesAbilities::bubbleAegis)
                        .build())
                .ability(AbilityDefinition.builder(EBB_AND_FLOW, AbilityKind.PASSIVE)
                        .name("Ebb and Flow")
                        .description("Whatever the Codex heals, it also shields: a part of every heal stays on as a shield of water.")
                        .unlock(10).levels(5, 10).cost(1, 1)
                        .node(1, 1, BUBBLE_AEGIS)
                        .fraction("shield", 0.25, "Share of each heal kept as a shield")
                        .fraction("shield_per_level", 0.05, "Extra share per ability level")
                        .param("duration", 5, "Seconds the shield lasts")
                        .stat("KEPT AS SHIELD", AbilityText.percent("shield", "shield_per_level"))
                        .upgrades(Upgrades.create().perLevel("% KEPT", 5, "").build())
                        .build())
                .ability(AbilityDefinition.builder(HEALING_RAIN, AbilityKind.ACTIVE)
                        .name("Healing Rain")
                        .description("Gather a raincloud over the place you point. Its rain heals the allies beneath it every second and puts out their fires, and drenches the enemies.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(3, 1, BUBBLE_AEGIS)
                        .standard(0, 0.6, 16.0)
                        .param("range", 28, "Cast distance")
                        .param("radius", 6, "Radius of the rain")
                        .param("radius_per_level", 0.5, "Extra radius per ability level")
                        .param("duration", 7, "Seconds the rain falls")
                        .param("duration_per_level", 0.5, "Extra seconds per ability level")
                        .fraction("heal", 0.04, "Healing every second, as a share of each ally's health")
                        .stat("HEAL / SECOND", c -> Math.round(c.param("heal") * 100 * (1 + 0.12 * Math.max(0, c.abilityLevel() - 1))) + "% HEALTH")
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 1))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("% HEALING", 12, "").perLevel("RADIUS", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(CodexOfTidesAbilities::healingRain)
                        .build())
                .ability(AbilityDefinition.builder(TIDES_EMBRACE, AbilityKind.ULTIMATE)
                        .name("Tide's Embrace")
                        .description("ULTIMATE. Loose a living wave at the ally in your sights. It heals them deeply, shields them, then leaps on to the most wounded ally near, and on again, again and again. Its run done, it breaks over the last of them as a great tide: every ally in the surge is healed and veiled in water, every enemy hurled back and soaked.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 2, EBB_AND_FLOW, HEALING_RAIN)
                        .standard(0, 1.5, 90.0)
                        .param("range", 30, "Reach of the first wave")
                        .param("bounces", 10, "Allies the wave reaches")
                        .param("bounces_per_level", 2, "Extra allies per level")
                        .param("bounce_range", 14, "How far each leap reaches")
                        .fraction("heal", 0.35, "Healing at each ally, as a share of their health")
                        .fraction("shield", 0.15, "Shield (share of health) at each ally")
                        .param("break_radius", 8, "Reach of the tide when the wave breaks")
                        .fraction("break_heal", 0.15, "Healing in the tide, as a share of health")
                        .param("break_push", 1.6, "How hard the tide hurls enemies back")
                        .param("veil", 6, "Seconds the tide's veil of water (a shield) holds on the allies")
                        .stat("HEAL", c -> Math.round(c.param("heal") * 100) + "% HEALTH")
                        .stat("ALLIES", AbilityText.scaled("bounces", "bounces_per_level", "", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().perLevel("ALLIES", 2, "").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(CodexOfTidesAbilities::tidesEmbrace)
                        .build())
                .build();
    }
}
