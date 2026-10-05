package com.fantasyweapons.weapons.voidfang;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * VOIDFANG — Longsword of the Void.
 * <pre>
 *                 [VOID SLASH] (5)
 *                /             \
 *      [VOID BLINK] (10)    [VOID MARK] (15, passive)
 *                \             /
 *                 [RIFT TEAR] (20)
 *                       |
 *              [VOID EXECUTION] (50)
 *                       |
 *              [VOID DIMENSION] (100, ultimate)
 * </pre>
 * Milestones from the design brief: Enhanced Void Blink = Void Blink level 3 (weapon level 34),
 * Advanced Rift = Rift Tear level 4 (weapon level 74).
 */
public final class Voidfang {
    public static final String VOID_SLASH = "void_slash";
    public static final String VOID_BLINK = "void_blink";
    public static final String VOID_MARK = "void_mark";
    public static final String RIFT_TEAR = "rift_tear";
    public static final String VOID_EXECUTION = "void_execution";
    public static final String VOID_DIMENSION = "void_dimension";

    private Voidfang() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("voidfang")
                .name("Voidfang", "Fang of the Endless Void")
                .lore("Forged from a shard of the space between stars. Every cut it makes is a door that should never have been opened.")
                .element(Element.VOID)
                .rarity(Rarity.MYTHIC)
                .type(WeaponClass.LONGSWORD)
                .damage(8f, 2.4f)
                .crit(0.12f, 1.8f)
                .theme(0x9B4DFF, 0x4D1A99)
                .onHit(VoidfangAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(VOID_SLASH, AbilityKind.ACTIVE)
                        .name("Void Slash")
                        .description("Release a crescent of void energy that tears through every enemy in its path and marks them.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.2, 0.5, 4.0)
                        .param("range", 14, "Travel distance in blocks")
                        .param("range_per_level", 2, "Extra range per ability level")
                        .param("width", 1.6, "Half-width of the slash in blocks")
                        .param("width_per_level", 0.2, "Extra half-width per ability level")
                        .param("speed", 1.8, "Blocks travelled per tick")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RANGE", AbilityText.scaled("range", "range_per_level", "m", 0))
                        .stat("WIDTH", AbilityText.scaled("width", "width_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RANGE", 2, "m").perLevel("WIDTH", 0.2, "m")
                                .cooldownPerLevel(0.05).build())
                        .animations("charge", "attack")
                        .executor(VoidfangAbilities::voidSlash)
                        .build())
                .ability(AbilityDefinition.builder(VOID_BLINK, AbilityKind.ACTIVE)
                        .name("Void Blink")
                        .description("Dissolve into the void, blink forward through enemies and reappear mid-slash. A dimensional tear lingers where you stood.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, VOID_SLASH)
                        .standard(3.5, 1.5, 8.0)
                        .param("distance", 8, "Maximum blink distance at full charge (blocks)")
                        .param("distance_per_level", 2, "Extra blink distance per ability level")
                        .fraction("path_damage", 0.6, "Fraction of damage dealt to enemies you blink through")
                        .param("slash_radius", 2.8, "Radius of the arrival slash")
                        .param("slash_radius_per_level", 0.3, "Extra arrival radius per level")
                        .param("rift_duration", 2.0, "Seconds the rift lingers at the origin")
                        .param("rift_duration_per_level", 0.5, "Extra rift seconds per level")
                        .param("enhanced_level", 3, "Ability level at which the lingering rift starts damaging enemies (Enhanced Void Blink)")
                        .fraction("rift_damage", 0.18, "Enhanced rift: fraction of damage per pulse (2 pulses/s)")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("TELEPORT", AbilityText.scaled("distance", "distance_per_level", "m", 0))
                        .stat("ARRIVAL RADIUS", AbilityText.scaled("slash_radius", "slash_radius_per_level", "m", 1))
                        .stat("RIFT DURATION", AbilityText.scaled("rift_duration", "rift_duration_per_level", "s", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("TELEPORT DISTANCE", 2, "m").perLevel("RIFT DURATION", 0.5, "s")
                                .at(3, "ENHANCED: the rift now damages enemies").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "ability_execution")
                        .executor(VoidfangAbilities::voidBlink)
                        .build())
                .ability(AbilityDefinition.builder(VOID_MARK, AbilityKind.PASSIVE)
                        .name("Void Mark")
                        .description("Your strikes brand enemies with the void. Each mark makes them take more void damage; Rift Tear and Void Execution detonate the marks.")
                        .unlock(15).levels(5, 10).cost(1, 1)
                        .node(3, 1, VOID_SLASH)
                        .fraction("bonus_per_stack", 0.06, "Extra void damage taken per mark")
                        .fraction("bonus_per_stack_per_level", 0.02, "Extra bonus per mark per ability level")
                        .param("max_stacks", 3, "Maximum marks on one enemy")
                        .param("max_stacks_per_level", 1, "Extra maximum marks per ability level")
                        .param("duration", 8, "Seconds a mark lasts")
                        .param("detonate_damage", 0.6, "Detonation damage per mark (multiple of weapon damage)")
                        .stat("BONUS / MARK", AbilityText.percent("bonus_per_stack", "bonus_per_stack_per_level"))
                        .stat("MAX MARKS", AbilityText.scaled("max_stacks", "max_stacks_per_level", "", 0))
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("DETONATE / MARK", c -> String.format("%,.0f", c.weaponDamage() * c.param("detonate_damage")))
                        .upgrades(Upgrades.create().perLevel("% VOID DAMAGE PER MARK", 2, "").perLevel("MAX MARKS", 1, "").build())
                        .build())
                .ability(AbilityDefinition.builder(RIFT_TEAR, AbilityKind.ACTIVE)
                        .name("Rift Tear")
                        .description("Tear open a dimensional rift where you aim. It drags enemies in and shreds them, then collapses in a burst that detonates Void Marks.")
                        .unlock(20).levels(5, 18).cost(1, 1)
                        .node(2, 2, VOID_BLINK, VOID_MARK)
                        .standard(6.0, 2.5, 14.0)
                        .param("range", 22, "Maximum cast distance")
                        .param("radius", 4, "Rift radius")
                        .param("radius_per_level", 0.5, "Extra radius per level")
                        .param("duration", 3, "Rift lifetime in seconds")
                        .param("duration_per_level", 0.5, "Extra seconds per level")
                        .param("pull", 0.35, "Pull strength towards the centre")
                        .fraction("collapse_share", 0.5, "Share of the total damage dealt by the final collapse")
                        .param("advanced_level", 4, "Ability level at which the collapse also ruptures every marked enemy nearby (Advanced Rift)")
                        .stat("TOTAL DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.5, "m").perLevel("DURATION", 0.5, "s")
                                .at(4, "ADVANCED RIFT: collapse ruptures all marked enemies").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_execution")
                        .executor(VoidfangAbilities::riftTear)
                        .build())
                .ability(AbilityDefinition.builder(VOID_EXECUTION, AbilityKind.ACTIVE)
                        .name("Void Execution")
                        .description("Vanish and reappear behind your target to deliver a killing cut. Enemies below the execute threshold are erased instantly.")
                        .unlock(50).levels(5, 10).cost(2, 1)
                        .node(2, 3, RIFT_TEAR)
                        .standard(8.0, 2.0, 20.0)
                        .param("range", 18, "Target range")
                        .param("range_per_level", 2, "Extra range per level")
                        .fraction("execute_threshold", 0.15, "Health fraction under which non-boss enemies are executed")
                        .fraction("execute_threshold_per_level", 0.02, "Extra execute threshold per level")
                        .param("boss_multiplier", 1.5, "Damage multiplier against bosses (they cannot be executed)")
                        .fraction("mark_bonus", 0.15, "Extra damage per Void Mark consumed")
                        .param("cleave_radius", 3, "Radius of the follow-through cleave around the target")
                        .fraction("cleave_fraction", 0.4, "Cleave damage fraction")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("EXECUTE BELOW", AbilityText.percent("execute_threshold", "execute_threshold_per_level"))
                        .stat("RANGE", AbilityText.scaled("range", "range_per_level", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("% EXECUTE THRESHOLD", 2, "").perLevel("RANGE", 2, "m")
                                .cooldownPerLevel(0.05).build())
                        .animations("charge", "heavy_attack")
                        .executor(VoidfangAbilities::voidExecution)
                        .build())
                .ability(AbilityDefinition.builder(VOID_DIMENSION, AbilityKind.ULTIMATE)
                        .name("Void Dimension")
                        .description("ULTIMATE. Pull the battlefield into the void itself. Phantom blades cut down everything inside before the dimension collapses on them.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 4, VOID_EXECUTION)
                        .standard(14.0, 4.0, 90.0)
                        .param("radius", 12, "Radius of the void dimension")
                        .param("radius_per_level", 2, "Extra radius per level")
                        .param("duration", 6, "Seconds the dimension lasts")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .param("strikes", 10, "Phantom blade volleys")
                        .param("strikes_per_level", 3, "Extra volleys per level")
                        .fraction("strike_fraction", 0.12, "Damage fraction per volley per enemy")
                        .fraction("collapse_fraction", 0.6, "Damage fraction of the final collapse")
                        .stat("COLLAPSE DAMAGE", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("collapse_fraction")))
                        .stat("PER VOLLEY", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("strike_fraction")))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 2, "m").perLevel("DURATION", 1, "s")
                                .perLevel("VOLLEYS", 3, "").build())
                        .animations("charge", "ability_activation", "ability_execution", "recovery")
                        .executor(VoidfangAbilities::voidDimension)
                        .build())
                .build();
    }
}
