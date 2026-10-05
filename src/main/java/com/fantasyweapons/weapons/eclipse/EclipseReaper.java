package com.fantasyweapons.weapons.eclipse;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import com.fantasyweapons.weapon.WeaponHooks;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * ECLIPSE REAPER — Where Sun and Moon Meet. Two modes switched with the artist's Transform animation
 * ("transform" into Dark, "transform_reverse" back to Light). Light hits hard and brands with light; Dark drains with
 * shadow. Enemies carrying both brands resonate.
 * <pre>
 *                [ECLIPSE DISC] (5)
 *               /               \
 *   [SOLAR FLARE] (10, light)  [UMBRAL VORTEX] (10, dark)
 *               \               /
 *              [EQUILIBRIUM] (20, passive)
 *                      |
 *              [TOTAL ECLIPSE] (100, ultimate)
 * </pre>
 */
public final class EclipseReaper {
    public static final String ECLIPSE_DISC = "eclipse_disc";
    public static final String SOLAR_FLARE = "solar_flare";
    public static final String UMBRAL_VORTEX = "umbral_vortex";
    public static final String EQUILIBRIUM = "equilibrium";
    public static final String TOTAL_ECLIPSE = "total_eclipse";
    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    private EclipseReaper() {
    }

    public static WeaponDefinition create() {
        WeaponHooks.onFormSwitch("eclipse_reaper", EclipseReaperAbilities::onFormSwitch);
        return WeaponDefinition.builder("eclipse_reaper")
                .name("Eclipse Reaper", "Where Sun and Moon Meet")
                .lore("Forged at the instant the moon swallowed the sun. It has never decided which of them it serves.")
                .element(Element.CELESTIAL).rarity(Rarity.ANCIENT).type(WeaponClass.SCYTHE).damage(10f, 2.3f)
                .crit(0.12f, 2.0f)
                .theme(0xFFE7A0, 0xFFFFFF)
                .form(new WeaponForm(LIGHT, "Light", "MODE", 0xFFD978, 0xFFFFFF, "transform_reverse", "idle", "attack", 0f, 1.0f, 1.0f))
                .form(new WeaponForm(DARK, "Dark", "MODE", 0x8B3DFF, 0xB0123A, "transform", "idle_dark", "attack", 0f, 1.0f, 1.0f))
                .onHit(EclipseReaperAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(ECLIPSE_DISC, AbilityKind.ACTIVE)
                        .name("Eclipse Disc")
                        .description("Hurl the scythe as a spinning disc that returns to you. In Light mode it strikes with radiant force and brands with light; in Dark mode it bleeds shadow into everything it cuts.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.6, 0.6, 6.0)
                        .param("range", 15, "Throw distance")
                        .param("range_per_level", 1.5, "Extra distance per level")
                        .param("speed", 1.3, "Flight speed")
                        .fraction("light_bonus", 0.3, "Light mode: extra burst damage")
                        .fraction("dark_dot", 0.05, "Dark mode: shadow damage per pulse (2/s) as a fraction of weapon damage")
                        .param("dark_duration", 4, "Dark mode: seconds of shadow damage")
                        .stat("DAMAGE / PASS", AbilityText.damage())
                        .stat("RANGE", AbilityText.scaled("range", "range_per_level", "m", 1))
                        .stat("LIGHT", c -> "+" + Math.round(c.param("light_bonus") * 100) + "% BURST")
                        .stat("DARK", c -> "SHADOW " + String.format("%,.0f", c.weaponDamage() * c.param("dark_dot")) + "/0.5s")
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RANGE", 1.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_execution")
                        .executor(EclipseReaperAbilities::eclipseDisc)
                        .build())
                .ability(AbilityDefinition.builder(SOLAR_FLARE, AbilityKind.ACTIVE)
                        .name("Solar Flare")
                        .description("LIGHT MODE. Erupt in a blinding nova of sunlight that scorches everything around you and brands it with light.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, ECLIPSE_DISC)
                        .requiresForm(LIGHT)
                        .standard(3.6, 1.0, 9.0)
                        .param("radius", 6, "Nova radius")
                        .param("radius_per_level", 0.5, "Extra radius per level")
                        .param("marks", 2, "Light brands applied")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "impact")
                        .executor(EclipseReaperAbilities::solarFlare)
                        .build())
                .ability(AbilityDefinition.builder(UMBRAL_VORTEX, AbilityKind.ACTIVE)
                        .name("Umbral Vortex")
                        .description("DARK MODE. Open a vortex of shadow where you aim. It drags enemies into its heart, devours their life and brands them with darkness.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(3, 1, ECLIPSE_DISC)
                        .requiresForm(DARK)
                        .standard(4.0, 1.2, 10.0)
                        .param("range", 22, "Cast distance")
                        .param("radius", 5, "Vortex radius")
                        .param("radius_per_level", 0.4, "Extra radius per level")
                        .param("duration", 3, "Seconds the vortex lasts")
                        .param("pull", 0.22, "Pull strength")
                        .stat("TOTAL DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.4, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_execution")
                        .executor(EclipseReaperAbilities::umbralVortex)
                        .build())
                .ability(AbilityDefinition.builder(EQUILIBRIUM, AbilityKind.PASSIVE)
                        .name("Equilibrium")
                        .description("Light and dark seek balance. Striking an enemy that carries both brands detonates them in an eclipse. Switching modes empowers your next strike.")
                        .unlock(20).levels(5, 12).cost(1, 1)
                        .node(2, 2, SOLAR_FLARE, UMBRAL_VORTEX)
                        .fraction("resonance", 1.2, "Eclipse detonation damage as a multiple of weapon damage")
                        .fraction("resonance_per_level", 0.25, "Extra detonation damage per level")
                        .fraction("switch_bonus", 0.4, "Damage bonus on the first strike after switching modes")
                        .param("switch_window", 4, "Seconds the switch bonus is held")
                        .stat("ECLIPSE BURST", c -> String.format("%,.0f", c.weaponDamage() * (c.param("resonance") + c.param("resonance_per_level") * Math.max(0, c.abilityLevel() - 1))))
                        .stat("SWITCH BONUS", AbilityText.percent("switch_bonus", null))
                        .upgrades(Upgrades.create().perLevel("% ECLIPSE BURST", 25, "").build())
                        .build())
                .ability(AbilityDefinition.builder(TOTAL_ECLIPSE, AbilityKind.ULTIMATE)
                        .name("Total Eclipse")
                        .description("ULTIMATE. The moon devours the sun. Beneath the black sun, beams of light and shadow strike every enemy in turn, and the eclipse ends in a blinding corona.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 3, EQUILIBRIUM)
                        .standard(15.0, 3.5, 100.0)
                        .param("radius", 14, "Radius of the eclipse")
                        .param("radius_per_level", 2, "Extra radius per level")
                        .param("duration", 6, "Seconds of the eclipse")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .param("interval", 5, "Ticks between beams")
                        .fraction("beam_fraction", 0.06, "Damage fraction of each beam")
                        .fraction("corona_fraction", 0.5, "Damage fraction of the final corona")
                        .stat("CORONA", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("corona_fraction")))
                        .stat("PER BEAM", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("beam_fraction")))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 2, "m").perLevel("DURATION", 1, "s").build())
                        .animations("charge", "ability_activation", "ability_execution", "recovery")
                        .executor(EclipseReaperAbilities::totalEclipse)
                        .build())
                .build();
    }
}
