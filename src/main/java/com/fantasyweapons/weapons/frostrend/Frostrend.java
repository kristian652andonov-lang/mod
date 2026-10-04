package com.fantasyweapons.weapons.frostrend;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * FROSTREND — Blade of the Eternal Winter.
 * <pre>
 *              [FROST SLASH] (5)
 *              /            \
 *    [ICE SPIKES] (10)    [FROSTBITE] (15, passive)
 *              \            /
 *            [GLACIAL DOMAIN] (30)
 *                    |
 *            [ABSOLUTE ZERO] (100, ultimate)
 * </pre>
 */
public final class Frostrend {
    public static final String FROST_SLASH = "frost_slash";
    public static final String ICE_SPIKES = "ice_spikes";
    public static final String FROSTBITE = "frostbite";
    public static final String GLACIAL_DOMAIN = "glacial_domain";
    public static final String ABSOLUTE_ZERO = "absolute_zero";

    private Frostrend() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("frostrend")
                .name("Frostrend", "Blade of the Eternal Winter")
                .lore("Carved from a glacier that never melted. Wounds it leaves freeze before they can bleed.")
                .element(Element.ICE)
                .rarity(Rarity.LEGENDARY)
                .type(WeaponClass.LONGSWORD)
                .damage(105f, 2.3f)
                .crit(0.12f, 1.8f)
                .theme(0x6FD8FF, 0xE8FBFF)
                .onHit(FrostrendAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(FROST_SLASH, AbilityKind.ACTIVE)
                        .name("Frost Slash")
                        .description("Release a crescent of biting cold that cuts through enemies, chills them to the bone and leaves a trail of ice crystals.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.2, 0.5, 4.5)
                        .param("range", 15, "Travel distance in blocks")
                        .param("range_per_level", 2, "Extra range per ability level")
                        .param("width", 1.7, "Half-width of the slash")
                        .param("speed", 1.6, "Blocks travelled per tick")
                        .param("frost_stacks", 2, "Frostbite stacks applied")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RANGE", AbilityText.scaled("range", "range_per_level", "m", 0))
                        .stat("FROSTBITE", AbilityText.param("frost_stacks", " stacks", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RANGE", 2, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "attack")
                        .executor(FrostrendAbilities::frostSlash)
                        .build())
                .ability(AbilityDefinition.builder(ICE_SPIKES, AbilityKind.ACTIVE)
                        .name("Ice Spikes")
                        .description("Plunge the blade into the ground: a line of jagged ice spikes erupts toward your target, impaling and launching everything in its path.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, FROST_SLASH)
                        .standard(3.0, 1.0, 8.0)
                        .param("length", 13, "Length of the eruption")
                        .param("length_per_level", 1.5, "Extra length per level")
                        .param("width", 1.5, "Half-width of the spike line")
                        .param("speed", 1.1, "Blocks per tick the eruption travels")
                        .param("launch", 0.65, "Upward launch strength")
                        .param("frost_stacks", 2, "Frostbite stacks applied")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("LENGTH", AbilityText.scaled("length", "length_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("LENGTH", 1.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_execution")
                        .executor(FrostrendAbilities::iceSpikes)
                        .build())
                .ability(AbilityDefinition.builder(FROSTBITE, AbilityKind.PASSIVE)
                        .name("Frostbite")
                        .description("Every strike stacks Frostbite, slowing the target more and more. At full stacks the target freezes solid and takes extra damage.")
                        .unlock(15).levels(5, 10).cost(1, 1)
                        .node(3, 1, FROST_SLASH)
                        .fraction("slow_per_stack", 0.08, "Movement slow per Frostbite stack")
                        .fraction("slow_per_stack_per_level", 0.01, "Extra slow per stack per level")
                        .param("duration", 5, "Seconds Frostbite lasts")
                        .param("freeze_duration", 1.5, "Seconds a target stays frozen at full stacks")
                        .param("freeze_duration_per_level", 0.25, "Extra freeze seconds per level")
                        .stat("SLOW / STACK", AbilityText.percent("slow_per_stack", "slow_per_stack_per_level"))
                        .stat("FREEZE", AbilityText.scaled("freeze_duration", "freeze_duration_per_level", "s", 2))
                        .stat("FROZEN TAKE", c -> "+15% DAMAGE")
                        .upgrades(Upgrades.create().perLevel("% SLOW PER STACK", 1, "").perLevel("FREEZE", 0.25, "s").build())
                        .build())
                .ability(AbilityDefinition.builder(GLACIAL_DOMAIN, AbilityKind.ACTIVE)
                        .name("Glacial Domain")
                        .description("Winter spreads from your feet. The ground freezes over, crystals burst from the frost, and enemies inside are chilled and cut by the cold.")
                        .unlock(30).levels(5, 15).cost(2, 1)
                        .node(2, 2, ICE_SPIKES, FROSTBITE)
                        .standard(5.0, 1.5, 15.0)
                        .param("radius", 7.5, "Radius of the frozen ground")
                        .param("radius_per_level", 0.75, "Extra radius per level")
                        .param("duration", 6, "Seconds the domain lasts")
                        .param("duration_per_level", 0.5, "Extra seconds per level")
                        .fraction("pulse_fraction", 0.12, "Damage fraction per pulse (2 pulses/s)")
                        .stat("TOTAL DAMAGE", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("pulse_fraction") * 2
                                * (c.param("duration") + c.param("duration_per_level") * Math.max(0, c.abilityLevel() - 1))))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.75, "m").perLevel("DURATION", 0.5, "s")
                                .cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "ability_execution")
                        .executor(FrostrendAbilities::glacialDomain)
                        .build())
                .ability(AbilityDefinition.builder(ABSOLUTE_ZERO, AbilityKind.ULTIMATE)
                        .name("Absolute Zero")
                        .description("ULTIMATE. Drop the world to absolute zero. Every enemy around you freezes solid in an instant, then the ice shatters with devastating force.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 3, GLACIAL_DOMAIN)
                        .standard(15.0, 3.5, 95.0)
                        .param("radius", 14, "Radius of the freeze")
                        .param("radius_per_level", 2, "Extra radius per level")
                        .param("freeze", 3, "Seconds enemies stay frozen before shattering")
                        .param("freeze_per_level", 0.5, "Extra freeze seconds per level")
                        .fraction("flash_fraction", 0.2, "Damage fraction of the initial flash-freeze")
                        .fraction("shatter_fraction", 0.8, "Damage fraction of the shatter")
                        .stat("SHATTER DAMAGE", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("shatter_fraction")))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("FREEZE", AbilityText.scaled("freeze", "freeze_per_level", "s", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 2, "m").perLevel("FREEZE", 0.5, "s").build())
                        .animations("charge", "ability_activation", "ability_execution", "recovery")
                        .executor(FrostrendAbilities::absoluteZero)
                        .build())
                .build();
    }
}
