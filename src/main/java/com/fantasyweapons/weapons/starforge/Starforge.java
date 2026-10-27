package com.fantasyweapons.weapons.starforge;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * STARFORGE — Hammer of the Fallen Stars. Meteors and gravity.
 * <pre>
 *             [GRAVITY SLAM] (5)
 *              /            \
 *   [METEOR STRIKE] (10)   [GRAVITY WELL] (15, passive)
 *              \            /
 *            [EVENT HORIZON] (30)
 *                   |
 *              [STARFALL] (100, ultimate)
 * </pre>
 */
public final class Starforge {
    public static final String GRAVITY_SLAM = "gravity_slam";
    public static final String METEOR_STRIKE = "meteor_strike";
    public static final String GRAVITY_WELL = "gravity_well";
    public static final String EVENT_HORIZON = "event_horizon";
    public static final String STARFALL = "starfall";

    private Starforge() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("starforge")
                .name("Starforge", "Hammer of the Fallen Stars")
                .lore("Its head is the heart of a fallen star, dragged from the crater by a hundred oxen and shaped over seven years by giants who would not say why. When it strikes, the sky remembers, and answers.",
                        "the Skyfall Sagas")
                .element(Element.COSMIC)
                .rarity(Rarity.ANCIENT)
                .type(WeaponClass.WARHAMMER)
                .damage(14f, 2.4f)
                .crit(0.08f, 2.0f)
                .theme(0x8A6CFF, 0xF0EDFF)
                .onHit(StarforgeAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(GRAVITY_SLAM, AbilityKind.ACTIVE)
                        .name("Gravity Slam")
                        .description("Slam the hammer down and invert gravity for an instant: enemies around you are yanked inward and crushed by the shockwave.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.8, 0.8, 6.0)
                        .param("radius", 5.5, "Slam radius")
                        .param("radius_per_level", 0.5, "Extra radius per level")
                        .param("pull", 0.9, "Pull strength toward you")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "heavy_attack")
                        .executor(StarforgeAbilities::gravitySlam)
                        .build())
                .ability(AbilityDefinition.builder(METEOR_STRIKE, AbilityKind.ACTIVE)
                        .name("Meteor Strike")
                        .description("Raise the hammer to the heavens. A meteor forms from gathering cosmic energy and crashes down where you aim; its gravity drags survivors into the crater.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, GRAVITY_SLAM)
                        .standard(4.6, 1.6, 12.0)
                        .param("range", 30, "Cast distance")
                        .param("radius", 5.5, "Impact radius")
                        .param("radius_per_level", 0.5, "Extra impact radius per level")
                        .param("fall", 16, "Ticks the meteor takes to fall")
                        .param("field", 3, "Seconds the gravity field lasts")
                        .param("field_pull", 0.2, "Gravity field pull strength")
                        .fraction("field_fraction", 0.25, "Share of damage dealt by the gravity field afterwards")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("IMPACT RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("GRAVITY FIELD", AbilityText.param("field", "s", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("IMPACT RADIUS", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "ability_execution")
                        .executor(StarforgeAbilities::meteorStrike)
                        .build())
                .ability(AbilityDefinition.builder(GRAVITY_WELL, AbilityKind.PASSIVE)
                        .name("Gravity Well")
                        .description("Heavy blows collapse space around the target, binding nearby enemies to it. Gravity-bound enemies take more cosmic damage.")
                        .unlock(15).levels(5, 10).cost(1, 1)
                        .node(3, 1, GRAVITY_SLAM)
                        .param("radius", 4, "Radius pulled into the well")
                        .param("duration", 2, "Seconds enemies stay bound")
                        .param("duration_per_level", 0.4, "Extra bound seconds per level")
                        .stat("WELL RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("BOUND FOR", AbilityText.scaled("duration", "duration_per_level", "s", 1))
                        .stat("BOUND TAKE", c -> "+20% COSMIC DAMAGE")
                        .upgrades(Upgrades.create().perLevel("BOUND TIME", 0.4, "s").build())
                        .build())
                .ability(AbilityDefinition.builder(EVENT_HORIZON, AbilityKind.ACTIVE)
                        .name("Event Horizon")
                        .description("Crack a star open where you aim and let it collapse into a black hole. Everything nearby spirals into it, then it implodes.")
                        .unlock(30).levels(5, 15).cost(2, 1)
                        .node(2, 2, METEOR_STRIKE, GRAVITY_WELL)
                        .standard(6.5, 2.0, 16.0)
                        .param("range", 24, "Cast distance")
                        .param("radius", 8, "Pull radius")
                        .param("radius_per_level", 0.75, "Extra pull radius per level")
                        .param("duration", 4, "Seconds before the implosion")
                        .param("pull", 0.32, "Pull strength")
                        .fraction("implosion_share", 0.6, "Share of the damage dealt by the implosion")
                        .stat("TOTAL DAMAGE", AbilityText.damage())
                        .stat("PULL RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("PULL RADIUS", 0.75, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_execution")
                        .executor(StarforgeAbilities::eventHorizon)
                        .build())
                .ability(AbilityDefinition.builder(STARFALL, AbilityKind.ULTIMATE)
                        .name("Starfall")
                        .description("ULTIMATE. Call the stars down. A rain of meteors pounds everything around you, and a final colossal meteor ends it.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 3, EVENT_HORIZON)
                        .standard(16.0, 4.0, 100.0)
                        .param("radius", 21, "Radius of the meteor shower")
                        .param("radius_per_level", 2, "Extra radius per level")
                        .param("meteors", 14, "Meteors in the shower")
                        .param("meteors_per_level", 4, "Extra meteors per level")
                        .param("duration", 5, "Seconds of meteor rain")
                        .param("meteor_radius", 3, "Impact radius of each small meteor")
                        .fraction("meteor_fraction", 0.07, "Damage fraction of each small meteor")
                        .fraction("final_fraction", 0.5, "Damage fraction of the colossal meteor")
                        .stat("FINAL METEOR", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("final_fraction")))
                        .stat("PER METEOR", c -> String.format("%,.0f", c.fullChargeDamage() * c.param("meteor_fraction")))
                        .stat("METEORS", AbilityText.scaled("meteors", "meteors_per_level", "", 0))
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("METEORS", 4, "").perLevel("RADIUS", 2, "m").build())
                        .animations("charge", "ability_activation", "ability_execution", "recovery")
                        .executor(StarforgeAbilities::starfall)
                        .build())
                .build();
    }
}
