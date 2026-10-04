package com.fantasyweapons.weapons.infernochain;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * INFERNOCHAIN — The Drake's Burning Coil. A transforming chainblade: SWORD form (entered with the artist's
 * "Retraction" = {@code retract}) and CHAINBLADE form (entered with "Transform" = {@code transform}, attacking with
 * "Chainblade attack" = {@code whip}). Chainblade attacks reach much further, hit far harder and sear their targets.
 * <pre>
 *             [INFERNO LASH] (5)
 *             /            \
 *     [HELLHOOK] (10)    [OVERHEAT] (15, passive)
 *             \            /
 *          [CINDER CYCLONE] (30, chainblade)
 *                   |
 *           [DRAKE'S WRATH] (100, ultimate)
 * </pre>
 */
public final class Infernochain {
    public static final String SWORD = "sword";
    public static final String CHAINBLADE = "chainblade";

    public static final String INFERNO_LASH = "inferno_lash";
    public static final String HELLHOOK = "hellhook";
    public static final String OVERHEAT = "overheat";
    public static final String CINDER_CYCLONE = "cinder_cyclone";
    public static final String DRAKES_WRATH = "drakes_wrath";

    private Infernochain() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("infernochain")
                .name("Infernochain", "The Drake's Burning Coil")
                .lore("A drake's skull still smoulders in its pommel. Unlock the blade and the drake's spine uncoils into a lash of burning steel.")
                .element(Element.FIRE)
                .rarity(Rarity.MYTHIC)
                .type(WeaponClass.CHAINBLADE)
                .damage(110f, 2.2f)
                .crit(0.15f, 2.0f)
                .theme(0xFF5A1F, 0xFFD38A)
                // SWORD is entered with the artist's "retract" (Retraction), CHAINBLADE with "transform" (Transform);
                // chainblade attacks use "whip" (Chainblade attack).
                .form(new WeaponForm(SWORD, "Sword", "FORM", 0xFF7A2F, 0xFFD38A, "retract", "idle", "attack", 0f, 1.0f, 1.0f))
                .form(new WeaponForm(CHAINBLADE, "Chainblade", "FORM", 0xFF2A10, 0xFFB15A, "transform", "chain_idle", "whip", 4.0f, 1.4f, 0.8f))
                .onHit(InfernochainAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(INFERNO_LASH, AbilityKind.ACTIVE)
                        .name("Inferno Lash")
                        .description("Sword: a blazing sweep that hurls nearby enemies back. Chainblade: the burning chain lashes across a huge arc, searing everything it touches.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.6, 0.6, 6.0)
                        .param("sword_range", 5, "Sword form sweep range")
                        .param("sword_angle", 170, "Sword form sweep angle (degrees)")
                        .fraction("sword_bonus", 0.3, "Extra damage of the sword form sweep")
                        .param("chain_range", 9, "Chainblade form lash range")
                        .param("chain_range_per_level", 0.5, "Extra chainblade range per level")
                        .param("chain_angle", 220, "Chainblade form lash angle (degrees)")
                        .param("sear", 2, "Seared stacks applied (chainblade)")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("CHAIN RANGE", AbilityText.scaled("chain_range", "chain_range_per_level", "m", 1))
                        .stat("SWORD RANGE", AbilityText.param("sword_range", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("CHAIN RANGE", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "attack")
                        .formAnimations(CHAINBLADE, "whip")
                        .executor(InfernochainAbilities::infernoLash)
                        .build())
                .ability(AbilityDefinition.builder(HELLHOOK, AbilityKind.ACTIVE)
                        .name("Hellhook")
                        .description("Hurl the blade's burning tip on its chain. It hooks the first enemy and drags it to you — or bites into a wall and hauls you after it.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, INFERNO_LASH)
                        .standard(2.2, 0.4, 8.0)
                        .param("range", 18, "Hook range")
                        .param("range_per_level", 1, "Extra range per level")
                        .param("speed", 2.4, "Hook speed (blocks per tick)")
                        .param("sear", 3, "Seared stacks on the hooked enemy")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RANGE", AbilityText.scaled("range", "range_per_level", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RANGE", 1, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "whip")
                        .executor(InfernochainAbilities::hellhook)
                        .build())
                .ability(AbilityDefinition.builder(OVERHEAT, AbilityKind.PASSIVE)
                        .name("Overheat")
                        .description("Every blow heats the blade (+6% damage and attack speed per stack). At full heat the next hit triggers a Meltdown: a fiery blast around the target.")
                        .unlock(15).levels(5, 10).cost(1, 1)
                        .node(3, 1, INFERNO_LASH)
                        .param("duration", 6, "Seconds each heat stack lasts")
                        .param("meltdown", 1.2, "Meltdown damage as a multiple of the triggering hit")
                        .param("meltdown_per_level", 0.2, "Extra meltdown multiple per level")
                        .param("radius", 4, "Meltdown radius")
                        .stat("HEAT", c -> "+6% DMG / STACK (5)")
                        .stat("MELTDOWN", AbilityText.percent("meltdown", "meltdown_per_level"))
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .upgrades(Upgrades.create().perLevel("% MELTDOWN", 20, "").build())
                        .build())
                .ability(AbilityDefinition.builder(CINDER_CYCLONE, AbilityKind.ACTIVE)
                        .name("Cinder Cyclone")
                        .description("CHAINBLADE. Whirl the burning chain around you: a cyclone of fire that shreds and sears everything in reach and drags stragglers in.")
                        .unlock(30).levels(5, 15).cost(2, 1)
                        .node(2, 2, HELLHOOK, OVERHEAT)
                        .requiresForm(CHAINBLADE)
                        .standard(6.0, 1.2, 14.0)
                        .param("radius", 6.5, "Cyclone radius")
                        .param("duration", 3, "Seconds of whirling")
                        .param("duration_per_level", 0.3, "Extra seconds per level")
                        .param("pull", 0.12, "Pull strength on enemies at the edge")
                        .stat("TOTAL DAMAGE", AbilityText.damage())
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 1))
                        .stat("RADIUS", AbilityText.param("radius", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("DURATION", 0.3, "s").cooldownPerLevel(0.05).build())
                        .animations("charge", "chain_idle")
                        .executor(InfernochainAbilities::cinderCyclone)
                        .build())
                .ability(AbilityDefinition.builder(DRAKES_WRATH, AbilityKind.ULTIMATE)
                        .name("Drake's Wrath")
                        .description("ULTIMATE. The drake in the pommel awakens. Its burning spine of chain rises behind you, roars, and dives along your aim, ending in an inferno that scorches the ground.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 3, CINDER_CYCLONE)
                        .standard(14.0, 3.0, 100.0)
                        .param("range", 30, "Dive distance")
                        .param("rise", 24, "Ticks the drake rises before diving")
                        .param("radius", 8, "Explosion radius")
                        .param("radius_per_level", 1, "Extra explosion radius per level")
                        .param("burn", 4, "Seconds the scorched path keeps burning")
                        .fraction("dive_fraction", 0.35, "Damage fraction of the dive")
                        .fraction("explosion_fraction", 0.45, "Damage fraction of the explosion")
                        .fraction("burn_fraction", 0.2, "Damage fraction of the scorched path")
                        .stat("EXPLOSION", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("explosion_fraction")))
                        .stat("DIVE", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("dive_fraction")))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 1, "m").build())
                        .animations("charge", "ability_activation", "ability_execution")
                        .executor(InfernochainAbilities::drakesWrath)
                        .build())
                .build();
    }
}
