package com.fantasyweapons.weapons.aetherlance;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * AETHERLANCE — Spear of the Celestial Court. Every thrust pierces; abilities fire energy from the lance tip.
 * <pre>
 *              [AETHER BOLT] (5)
 *              /           \
 *  [PIERCING CHARGE] (10)  [AETHER RESONANCE] (15, passive)
 *              \           /
 *          [CELESTIAL BARRAGE] (30)
 *                   |
 *           [JUDGEMENT RAY] (100, ultimate)
 * </pre>
 */
public final class Aetherlance {
    public static final String AETHER_BOLT = "aether_bolt";
    public static final String PIERCING_CHARGE = "piercing_charge";
    public static final String RESONANCE = "aether_resonance";
    public static final String BARRAGE = "celestial_barrage";
    public static final String JUDGEMENT_RAY = "judgement_ray";

    private Aetherlance() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("aetherlance")
                .name("Aetherlance", "Spear of the Celestial Court")
                .lore("Borne by the heralds of the Celestial Court, which sits above the sky and judges all that happens beneath it. Its point is a sliver of pure light. When its herald fell to earth the lance chose to stay, and it has never stopped looking up.",
                        "the Codex of the Celestial Court")
                .element(Element.ENERGY)
                .rarity(Rarity.MYTHIC)
                .type(WeaponClass.LANCE)
                .damage(7f, 2.2f)
                .crit(0.14f, 1.8f)
                .theme(0x5FF3FF, 0xF2FFFF)
                .onHit(AetherlanceAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(AETHER_BOLT, AbilityKind.ACTIVE)
                        .name("Aether Bolt")
                        .description("Energy gathers at the tip of the lance and is loosed as a bolt of pure aether that pierces through every enemy in its path.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.6, 0.8, 5.0)
                        .param("range", 36, "Bolt range")
                        .param("speed", 2.4, "Bolt speed (blocks per tick)")
                        .param("width", 0.9, "Bolt hit radius")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RANGE", AbilityText.param("range", "m", 0))
                        .stat("PIERCE", c -> "UNLIMITED")
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "ability_execution")
                        .executor(AetherlanceAbilities::aetherBolt)
                        .build())
                .ability(AbilityDefinition.builder(PIERCING_CHARGE, AbilityKind.ACTIVE)
                        .name("Piercing Charge")
                        .description("Couch the lance and charge. You surge forward in a streak of light, running through every enemy in your way.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, AETHER_BOLT)
                        .standard(3.4, 0.8, 9.0)
                        .param("distance", 10, "Charge distance")
                        .param("distance_per_level", 1, "Extra distance per level")
                        .param("width", 1.4, "Hit radius around you")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("DISTANCE", AbilityText.scaled("distance", "distance_per_level", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("DISTANCE", 1, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "heavy_attack")
                        .executor(AetherlanceAbilities::piercingCharge)
                        .build())
                .ability(AbilityDefinition.builder(RESONANCE, AbilityKind.PASSIVE)
                        .name("Aether Resonance")
                        .description("Your thrusts pierce through more enemies, and every few thrusts the lance releases a free Aether Bolt from its tip.")
                        .unlock(15).levels(5, 10).cost(1, 1)
                        .node(3, 1, AETHER_BOLT)
                        .param("extra_pierce", 2, "Extra enemies pierced by thrusts")
                        .param("extra_pierce_per_level", 1, "Extra pierce per level")
                        .param("thrusts", 4, "Thrusts per free bolt")
                        .fraction("bolt", 0.8, "Free bolt damage as a fraction of weapon damage")
                        .fraction("bolt_per_level", 0.15, "Extra free bolt damage per level")
                        .stat("EXTRA PIERCE", AbilityText.scaled("extra_pierce", "extra_pierce_per_level", "", 0))
                        .stat("FREE BOLT EVERY", AbilityText.param("thrusts", " THRUSTS", 0))
                        .upgrades(Upgrades.create().perLevel("PIERCE", 1, "").perLevel("% BOLT DAMAGE", 15, "").build())
                        .build())
                .ability(AbilityDefinition.builder(BARRAGE, AbilityKind.ACTIVE)
                        .name("Celestial Barrage")
                        .description("The lance's petals unfold and fire a fan of aether bolts, each piercing everything it meets.")
                        .unlock(30).levels(5, 15).cost(2, 1)
                        .node(2, 2, PIERCING_CHARGE, RESONANCE)
                        .standard(5.5, 1.4, 14.0)
                        .param("bolts", 5, "Bolts in the fan")
                        .param("bolts_per_level", 1, "Extra bolts per level")
                        .param("spread", 50, "Fan width in degrees")
                        .param("range", 30, "Bolt range")
                        .stat("TOTAL DAMAGE", AbilityText.damage())
                        .stat("BOLTS", AbilityText.scaled("bolts", "bolts_per_level", "", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("BOLT", 1, "").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_execution")
                        .executor(AetherlanceAbilities::barrage)
                        .build())
                .ability(AbilityDefinition.builder(JUDGEMENT_RAY, AbilityKind.ULTIMATE)
                        .name("Judgement Ray")
                        .description("ULTIMATE. The court passes judgement. A colossal ray pours from the lance tip for several seconds, burning through everything you aim at.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 3, BARRAGE)
                        .standard(15.0, 3.0, 95.0)
                        .param("range", 52, "Ray length")
                        .param("radius", 2.6, "Ray radius")
                        .param("duration", 4, "Seconds the ray lasts")
                        .param("duration_per_level", 1, "Extra seconds per level")
                        .param("rehit", 4, "Ticks between hits on the same enemy")
                        .stat("TOTAL DAMAGE / ENEMY", AbilityText.damage())
                        .stat("DURATION", AbilityText.scaled("duration", "duration_per_level", "s", 0))
                        .stat("RANGE", AbilityText.param("range", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("DURATION", 1, "s").build())
                        .animations("charge", "ability_activation", "ability_execution", "recovery")
                        .executor(AetherlanceAbilities::judgementRay)
                        .build())
                .build();
    }
}
