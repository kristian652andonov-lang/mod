package com.fantasyweapons.weapons.soulreaper;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * SOULREAPER — Harvester of Souls. Its throws send the actual scythe model spinning out and back.
 * <pre>
 *             [REAPER'S THROW] (5)
 *              /             \
 *    [SOUL REND] (10)      [SOUL SIPHON] (15, passive)
 *              \             /
 *            [REAPING WHIRL] (30)
 *                    |
 *             [DEATH'S TOLL] (100, ultimate)
 * </pre>
 */
public final class Soulreaper {
    public static final String REAPERS_THROW = "reapers_throw";
    public static final String SOUL_REND = "soul_rend";
    public static final String SOUL_SIPHON = "soul_siphon";
    public static final String REAPING_WHIRL = "reaping_whirl";
    public static final String DEATHS_TOLL = "deaths_toll";

    private Soulreaper() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("soulreaper")
                .name("Soulreaper", "Harvester of Souls")
                .lore("The ferryman of the dead grew weary of waiting and made this scythe so that the dead would come to him sooner. Every scythe reaps. This one keeps what it reaps, and it is never full. On quiet nights you can hear them inside it, counting.",
                        "the Ferryman's Lament")
                .element(Element.SOUL)
                .rarity(Rarity.MYTHIC)
                .type(WeaponClass.SCYTHE)
                .damage(10f, 2.2f)
                .crit(0.12f, 1.9f)
                .theme(0x5A8CFF, 0xCFE0FF)
                .onHit(SoulreaperAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(REAPERS_THROW, AbilityKind.ACTIVE)
                        .name("Reaper's Throw")
                        .description("Hurl the scythe. It spins through everything in its path, drinking their souls, reaches its limit and flies back to your hand, cutting again.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.4, 0.6, 7.0)
                        .param("range", 14, "Throw distance")
                        .param("range_per_level", 1.5, "Extra distance per level")
                        .param("speed", 1.25, "Flight speed (blocks per tick)")
                        .fraction("drain", 0.15, "Fraction of damage dealt healed")
                        .stat("DAMAGE / PASS", AbilityText.damage())
                        .stat("RANGE", AbilityText.scaled("range", "range_per_level", "m", 1))
                        .stat("LIFE DRAIN", AbilityText.percent("drain", null))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RANGE", 1.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "attack")
                        .executor(SoulreaperAbilities::reapersThrow)
                        .build())
                .ability(AbilityDefinition.builder(SOUL_REND, AbilityKind.ACTIVE)
                        .name("Soul Rend")
                        .description("A vast reaping sweep that tears the souls half out of every enemy around you. Torn souls keep bleeding life into you.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, REAPERS_THROW)
                        .standard(3.0, 0.8, 8.0)
                        .param("range", 5.5, "Sweep reach")
                        .param("range_per_level", 0.5, "Extra reach per level")
                        .param("angle", 220, "Sweep width in degrees")
                        .fraction("drain", 0.06, "Soul drain per pulse (2/s) as a fraction of weapon damage")
                        .param("drain_duration", 4, "Seconds of soul drain")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("REACH", AbilityText.scaled("range", "range_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("REACH", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "heavy_attack")
                        .executor(SoulreaperAbilities::soulRend)
                        .build())
                .ability(AbilityDefinition.builder(SOUL_SIPHON, AbilityKind.PASSIVE)
                        .name("Soul Siphon")
                        .description("Your strikes siphon life. Enemies whose souls are already torn take extra damage from the scythe.")
                        .unlock(15).levels(5, 10).cost(1, 1)
                        .node(3, 1, REAPERS_THROW)
                        .fraction("steal", 0.06, "Melee life steal")
                        .fraction("steal_per_level", 0.02, "Extra life steal per level")
                        .fraction("torn_bonus", 0.25, "Extra damage against soul-drained enemies")
                        .stat("LIFE STEAL", AbilityText.percent("steal", "steal_per_level"))
                        .stat("VS TORN SOULS", AbilityText.percent("torn_bonus", null))
                        .upgrades(Upgrades.create().perLevel("% LIFE STEAL", 2, "").build())
                        .build())
                .ability(AbilityDefinition.builder(REAPING_WHIRL, AbilityKind.ACTIVE)
                        .name("Reaping Whirl")
                        .description("Release the scythe into a whirling orbit around you. It cuts down everything that comes close, then returns to your hand.")
                        .unlock(30).levels(5, 15).cost(2, 1)
                        .node(2, 2, SOUL_REND, SOUL_SIPHON)
                        .standard(5.0, 1.2, 14.0)
                        .param("duration", 4, "Seconds of orbiting")
                        .param("duration_per_level", 0.5, "Extra seconds per level")
                        .param("radius", 3.6, "Orbit radius")
                        .fraction("hit_fraction", 0.2, "Damage fraction per cut")
                        .param("rehit", 8, "Ticks before the same enemy can be cut again")
                        .fraction("drain", 0.1, "Fraction of damage dealt healed")
                        .stat("PER CUT", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("hit_fraction")))
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("DURATION", 0.5, "s").cooldownPerLevel(0.05).build())
                        .animations("charge", "attack")
                        .executor(SoulreaperAbilities::reapingWhirl)
                        .build())
                .ability(AbilityDefinition.builder(DEATHS_TOLL, AbilityKind.ULTIMATE)
                        .name("Death's Toll")
                        .description("ULTIMATE. The bell tolls for everyone around you. Their souls are marked, and the scythe hunts each of them down in turn before returning to you.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 3, REAPING_WHIRL)
                        .standard(15.0, 3.0, 95.0)
                        .param("radius", 18, "Marking radius")
                        .param("radius_per_level", 2, "Extra radius per level")
                        .param("max_targets", 10, "Enemies the scythe hunts")
                        .param("max_targets_per_level", 3, "Extra hunted enemies per level")
                        .param("mark_time", 1.5, "Seconds before the hunt begins")
                        .fraction("hit_fraction", 0.35, "Damage fraction per reaped enemy")
                        .fraction("drain", 0.2, "Fraction of damage dealt healed")
                        .stat("PER SOUL", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("hit_fraction")))
                        .stat("TARGETS", AbilityText.scaled("max_targets", "max_targets_per_level", "", 0))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("TARGETS", 3, "").perLevel("RADIUS", 2, "m").build())
                        .animations("charge", "ability_activation", "ability_execution", "recovery")
                        .executor(SoulreaperAbilities::deathsToll)
                        .build())
                .build();
    }
}
