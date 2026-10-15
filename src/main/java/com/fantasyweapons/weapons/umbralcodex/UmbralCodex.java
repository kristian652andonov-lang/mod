package com.fantasyweapons.weapons.umbralcodex;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * UMBRAL CODEX — The void mage's spellbook: hexes that drain, seeds that bloom inside the flesh, and a plague that
 * spreads from foe to foe on its own.
 * <pre>
 *          [HEX OF LEECHING] (5)
 *             /            \
 *   [CONTAGION] (10, passive)  [BANE SEEDS] (20)
 *             \            /
 *         [PLAGUE OF NIGHT] (100, ultimate)
 * </pre>
 */
public final class UmbralCodex {
    public static final String HEX_OF_LEECHING = "hex_of_leeching";
    public static final String CONTAGION = "contagion";
    public static final String BANE_SEEDS = "bane_seeds";
    public static final String PLAGUE_OF_NIGHT = "plague_of_night";

    private UmbralCodex() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("umbral_codex")
                .name("Umbral Codex", "The Book of Small Deaths")
                .lore("Bound in the skin of night itself, its letters move when no one is reading. Each spell in it is a little death, patient as a seed, waiting for warm flesh to grow in.",
                        "a page torn from the Codex itself")
                .element(Element.VOID)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.TOME)
                .damage(6.5f, 1.6f)
                .crit(0.08f, 1.7f)
                .theme(0xB070FF, 0xEED8FF)
                .ability(AbilityDefinition.builder(HEX_OF_LEECHING, AbilityKind.ACTIVE)
                        .name("Hex of Leeching")
                        .description("Hex the enemy in your sights with a thread of shadow. Twice a second it drains the foe's life away and pours a share of it into you.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(0.3, 0.4, 10.0)
                        .param("range", 30, "Reach of the hex")
                        .param("duration", 6, "Seconds the hex holds")
                        .param("duration_per_level", 0.5, "Extra seconds per ability level")
                        .stat("DRAIN / HALF SECOND", AbilityText.damage())
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 1))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("DURATION", 0.5, "s").cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(UmbralCodexAbilities::hexOfLeeching)
                        .build())
                .ability(AbilityDefinition.builder(CONTAGION, AbilityKind.PASSIVE)
                        .name("Contagion")
                        .description("When an enemy dies under any of your curses, its curses leap on to the enemies nearest to it.")
                        .unlock(10).levels(5, 10).cost(1, 1)
                        .node(1, 1, HEX_OF_LEECHING)
                        .param("jumps", 2, "Enemies the curses leap on to")
                        .param("jumps_per_level", 0.5, "Extra enemies per ability level")
                        .param("radius", 7, "How far the curses leap")
                        .stat("LEAPS TO", c -> String.valueOf((int) Math.floor(c.param("jumps") + c.param("jumps_per_level") * Math.max(0, c.abilityLevel() - 1))))
                        .stat("REACH", AbilityText.param("radius", "m", 0))
                        .upgrades(Upgrades.create().perLevel("ENEMIES", 0.5, "").build())
                        .build())
                .ability(AbilityDefinition.builder(BANE_SEEDS, AbilityKind.ACTIVE)
                        .name("Bane Seeds")
                        .description("Loose three seeds of the void that seek out the enemies before you and bury themselves in them. Each eats away at its host, then blooms out of it in a burst of shadow.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(3, 1, HEX_OF_LEECHING)
                        .standard(0.15, 0.5, 12.0)
                        .param("range", 28, "How far the seeds seek")
                        .param("seeds", 3, "Seeds loosed")
                        .param("grow", 4, "Seconds before a seed blooms")
                        .fraction("bloom", 1.4, "Bloom damage per seed, as a multiple of the cast's damage")
                        .stat("PER HALF SECOND", AbilityText.damage())
                        .stat("BLOOM", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("bloom") * 6))
                        .stat("SEEDS", AbilityText.param("seeds", "", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(UmbralCodexAbilities::baneSeeds)
                        .build())
                .ability(AbilityDefinition.builder(PLAGUE_OF_NIGHT, AbilityKind.ULTIMATE)
                        .name("Plague of Night")
                        .description("ULTIMATE. Tear open a rift of night far ahead and let the plague out. Every enemy near it sickens, wasting away and weakening, and the sickness spreads to every enemy that comes close to the sick.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 2, CONTAGION, BANE_SEEDS)
                        .standard(0.5, 2.0, 100.0)
                        .param("range", 30, "Cast distance")
                        .param("radius", 10, "Reach of the first outbreak")
                        .param("radius_per_level", 1, "Extra reach per level")
                        .param("duration", 15, "Seconds the plague lasts")
                        .param("duration_per_level", 3, "Extra seconds per level")
                        .stat("PER HALF SECOND", AbilityText.damage())
                        .stat("REACH", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("REACH", 1, "m").perLevel("DURATION", 3, "s").build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(UmbralCodexAbilities::plagueOfNight)
                        .build())
                .build();
    }
}
