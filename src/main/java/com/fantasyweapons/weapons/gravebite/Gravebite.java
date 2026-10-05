package com.fantasyweapons.weapons.gravebite;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * GRAVEBITE — The Cursed Maw. The skull on the axe head opens its jaw (ability animations) and spits souls.
 * <pre>
 *              [SOUL VOLLEY] (5)
 *              /           \
 *    [GRAVE CHAINS] (10)  [SOUL HARVEST] (15, passive)
 *              \           /
 *             [DEATH'S MAW] (30)
 *                   |
 *       [LEGION OF THE DAMNED] (100, ultimate)
 * </pre>
 */
public final class Gravebite {
    public static final String SOUL_VOLLEY = "soul_volley";
    public static final String GRAVE_CHAINS = "grave_chains";
    public static final String SOUL_HARVEST = "soul_harvest";
    public static final String DEATHS_MAW = "deaths_maw";
    public static final String LEGION = "legion_of_the_damned";

    private Gravebite() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder("gravebite")
                .name("Gravebite", "The Cursed Maw")
                .lore("The skull is not decoration. It is still hungry, and it remembers every soul it has swallowed.")
                .element(Element.NECROMANCY)
                .rarity(Rarity.MYTHIC)
                .type(WeaponClass.BATTLEAXE)
                .damage(11f, 2.2f)
                .crit(0.1f, 1.9f)
                .theme(0x5CFFB8, 0xC9FFE9)
                .onHit(GravebiteAbilities::onMeleeHit)
                .ability(AbilityDefinition.builder(SOUL_VOLLEY, AbilityKind.ACTIVE)
                        .name("Soul Volley")
                        .description("The skull opens its jaws, gathers wailing souls and spits them at your foes. The souls hunt their targets down.")
                        .unlock(5).levels(5, 8).cost(1, 1)
                        .node(2, 0)
                        .standard(2.6, 1.0, 6.0)
                        .param("souls", 3, "Souls fired")
                        .param("souls_per_level", 1, "Extra souls per level")
                        .param("range", 22, "Seeking range")
                        .param("speed", 0.9, "Soul speed (blocks per tick)")
                        .stat("TOTAL DAMAGE", AbilityText.damage())
                        .stat("SOULS", AbilityText.scaled("souls", "souls_per_level", "", 0))
                        .stat("RANGE", AbilityText.param("range", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("SOUL", 1, "").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "ability_execution")
                        .executor(GravebiteAbilities::soulVolley)
                        .build())
                .ability(AbilityDefinition.builder(GRAVE_CHAINS, AbilityKind.ACTIVE)
                        .name("Grave Chains")
                        .description("Spectral chains burst from the earth where you aim, binding every enemy in place and crushing the life out of them.")
                        .unlock(10).levels(5, 12).cost(1, 1)
                        .node(1, 1, SOUL_VOLLEY)
                        .standard(3.0, 1.0, 10.0)
                        .param("range", 22, "Cast distance")
                        .param("radius", 4.5, "Binding radius")
                        .param("radius_per_level", 0.4, "Extra radius per level")
                        .param("bind", 2.5, "Seconds enemies stay bound")
                        .param("bind_per_level", 0.25, "Extra bind seconds per level")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.scaled("radius", "radius_per_level", "m", 1))
                        .stat("BIND", AbilityText.scaled("bind", "bind_per_level", "s", 2))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("RADIUS", 0.4, "m").perLevel("BIND", 0.25, "s").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_execution")
                        .executor(GravebiteAbilities::graveChains)
                        .build())
                .ability(AbilityDefinition.builder(SOUL_HARVEST, AbilityKind.PASSIVE)
                        .name("Soul Harvest")
                        .description("Every enemy Gravebite kills surrenders its soul: it mends your wounds and is kept in the skull. Stored souls join your next Soul Volley.")
                        .unlock(15).levels(5, 10).cost(1, 1)
                        .node(3, 1, SOUL_VOLLEY)
                        .fraction("heal", 0.03, "Fraction of max health healed per harvested soul")
                        .fraction("heal_per_level", 0.005, "Extra heal per level")
                        .param("max_souls", 3, "Souls the skull can store")
                        .param("max_souls_per_level", 1, "Extra stored souls per level")
                        .stat("HEAL / SOUL", AbilityText.percent("heal", "heal_per_level"))
                        .stat("STORED SOULS", AbilityText.scaled("max_souls", "max_souls_per_level", "", 0))
                        .upgrades(Upgrades.create().perLevel("STORED SOUL", 1, "").build())
                        .build())
                .ability(AbilityDefinition.builder(DEATHS_MAW, AbilityKind.ACTIVE)
                        .name("Death's Maw")
                        .description("The skull's spirit lunges out as a colossal spectral maw and bites down on everything in front of you, draining their souls.")
                        .unlock(30).levels(5, 15).cost(2, 1)
                        .node(2, 2, GRAVE_CHAINS, SOUL_HARVEST)
                        .standard(5.5, 1.4, 13.0)
                        .param("range", 7, "Bite reach")
                        .param("range_per_level", 0.5, "Extra reach per level")
                        .param("angle", 90, "Bite width in degrees")
                        .fraction("drain", 0.06, "Soul drain damage per pulse as a fraction of weapon damage")
                        .param("drain_duration", 4, "Seconds of soul drain")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("REACH", AbilityText.scaled("range", "range_per_level", "m", 1))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("REACH", 0.5, "m").cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "impact")
                        .executor(GravebiteAbilities::deathsMaw)
                        .build())
                .ability(AbilityDefinition.builder(LEGION, AbilityKind.ULTIMATE)
                        .name("Legion of the Damned")
                        .description("ULTIMATE. The skull vomits out a storm of the damned. Dozens of souls circle you and hurl themselves at every enemy in sight.")
                        .unlock(100).levels(3, 0).cost(5, 5)
                        .node(2, 3, DEATHS_MAW)
                        .standard(14.0, 3.0, 95.0)
                        .param("souls", 24, "Souls in the legion")
                        .param("souls_per_level", 6, "Extra souls per level")
                        .param("duration", 7, "Seconds the legion lasts")
                        .param("range", 18, "Hunting range")
                        .param("interval", 4, "Ticks between launched souls")
                        .stat("TOTAL DAMAGE", AbilityText.damage())
                        .stat("SOULS", AbilityText.scaled("souls", "souls_per_level", "", 0))
                        .stat("RANGE", AbilityText.param("range", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).perLevel("SOULS", 6, "").build())
                        .animations("charge", "ability_activation", "ability_execution", "recovery")
                        .executor(GravebiteAbilities::legion)
                        .build())
                .build();
    }
}
