package com.fantasyweapons.weapons.obliteration;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * THE GREAT SWORD OF OBLITERATION — one of the two ultimate weapons, forged on the Altar of Annihilation from every
 * legendary blade. Six abilities of six powers, each one breaking the world: the blocks are really cut, shattered,
 * melted, frozen and rebuilt (see {@link com.fantasyweapons.world.wreck.WorldWreck}).
 * <pre>
 *                 [THE GREAT SLASH] (light)
 *                /                         \
 *   [4TH DIMENSION ASCENSION] (void)   [THE ARCH DRAGON OF HELL] (fire)
 *               |                           |
 *   [THE GRAND EARTHQUAKE] (earth)      [THORNSPIRE CATACLYSM] (nature)
 *                \                         /
 *                       [ICE RAIN] (ice)
 * </pre>
 */
public final class GreatSwordOfObliteration {
    public static final String ID = "great_sword_of_obliteration";
    public static final String GREAT_SLASH = "the_great_slash";
    public static final String FOURTH_DIMENSION = "fourth_dimension_ascension";
    public static final String ARCH_DRAGON = "arch_dragon_of_hell";
    public static final String GRAND_EARTHQUAKE = "the_grand_earthquake";
    public static final String THORNSPIRE = "thornspire_cataclysm";
    public static final String ICE_RAIN = "ice_rain";

    private GreatSwordOfObliteration() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder(ID)
                .name("The Great Sword of Obliteration", "The End of All Things")
                .lore("Twenty-five legends were thrown into the altar's fire and only this came out. It does not cut what it strikes; it decides that the thing was never there. The ground forgets its shape around it, and the sky is afraid to be above it.",
                        "the Last Page of the World")
                .element(Element.OBLITERATION)
                .rarity(Rarity.APOCALYPTIC)
                .geo("worldreaver")
                .type(WeaponClass.GREATSWORD)
                .damage(36f, 2.6f)
                .crit(0.15f, 2.4f)
                .theme(0xFF2B3A, 0xFFF0F0)
                .ability(AbilityDefinition.builder(GREAT_SLASH, AbilityKind.ULTIMATE)
                        .name("The Great Slash")
                        .description("A slash of pure light leaves the blade and runs 64 blocks where you look. Everything in its path, stone, trees, houses and creatures, is cut in two, and what it touches is gone.")
                        .unlock(1).levels(5, 20).cost(3, 2)
                        .node(2, 0)
                        .standard(30.0, 1.5, 60.0)
                        .param("length", 64, "How far the slash runs")
                        .param("width", 64, "How wide the slash sweeps")
                        .param("thickness", 5, "Thickness of the cut through the world")
                        .param("speed", 4, "Blocks the slash travels per tick")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("LENGTH", AbilityText.param("length", "m", 0))
                        .stat("WIDTH", AbilityText.param("width", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("charge", "heavy_attack", "recovery")
                        .executor(ObliterationAbilities::greatSlash)
                        .build())
                .ability(AbilityDefinition.builder(FOURTH_DIMENSION, AbilityKind.ULTIMATE)
                        .name("4th Dimension Ascension")
                        .description("Cast from far away, through the map. Where it lands, the world is lifted out of its three dimensions: whole chunks are torn up from bedrock to the sky, turned upside down, tipped on their sides and shuffled out of order, and everything inside is crushed between them.")
                        .unlock(1).levels(5, 20).cost(3, 2)
                        .node(1, 1, GREAT_SLASH)
                        .standard(40.0, 0, 240.0)
                        .remote(32)
                        .param("radius", 64, "Radius of the broken chunks")
                        .param("delay", 4, "Seconds before space breaks")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("RANGE", AbilityText.param("remote_range", " chunks", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "recovery")
                        .executor(ObliterationAbilities::fourthDimension)
                        .build())
                .ability(AbilityDefinition.builder(ARCH_DRAGON, AbilityKind.ULTIMATE)
                        .name("The Arch Dragon of Hell")
                        .description("Call the Arch Dragon up from below the world: a serpent of hellfire 256 blocks long that dives where you look. The ground splits for 128 blocks around its strike, and nothing is left but coal, magma, lava and ruin.")
                        .unlock(1).levels(5, 20).cost(3, 2)
                        .node(3, 1, GREAT_SLASH)
                        .standard(45.0, 2.0, 180.0)
                        .param("range", 384, "How far away the dragon can strike")
                        .param("length", 256, "Length of the dragon")
                        .param("radius", 128, "Radius of the split ground")
                        .param("flight", 5, "Seconds the dragon flies before it strikes")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("RANGE", AbilityText.param("range", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "recovery")
                        .executor(ObliterationAbilities::archDragon)
                        .build())
                .ability(AbilityDefinition.builder(GRAND_EARTHQUAKE, AbilityKind.ULTIMATE)
                        .name("The Grand Earthquake")
                        .description("Drive the sword into the ground. The earth breaks for 256 blocks in every direction, torn apart by canyons and cracks; only the ground within a few steps of you holds.")
                        .unlock(1).levels(5, 20).cost(3, 2)
                        .node(1, 2, FOURTH_DIMENSION)
                        .standard(40.0, 2.5, 240.0)
                        .param("radius", 256, "Radius of the earthquake")
                        .param("safe_radius", 7, "Radius around you that the earthquake spares")
                        .param("duration", 10, "Seconds the earth keeps breaking")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("charge", "impact", "recovery")
                        .executor(ObliterationAbilities::grandEarthquake)
                        .build())
                .ability(AbilityDefinition.builder(THORNSPIRE, AbilityKind.ULTIMATE)
                        .name("Thornspire Cataclysm")
                        .description("Plant the sword and wake the oldest forest. Thorns the size of towers burst out of the earth in a spiral that runs 128 blocks out from you, impaling everything they rise through. The spires never wither: a forest of giant thorns stands there forever.")
                        .unlock(1).levels(5, 20).cost(3, 2)
                        .node(3, 2, ARCH_DRAGON)
                        .standard(38.0, 2.0, 180.0)
                        .param("radius", 128, "How far the spiral of thorns runs")
                        .param("safe_radius", 7, "Radius around you that stays clear")
                        .param("spires", 90, "Giant thorns that rise")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("SPIRES", AbilityText.param("spires", "", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("charge", "impact", "recovery")
                        .executor(ObliterationAbilities::thornspire)
                        .build())
                .ability(AbilityDefinition.builder(ICE_RAIN, AbilityKind.ULTIMATE)
                        .name("Ice Rain")
                        .description("Raise the sword to the sky and a storm cloud 128 blocks wide gathers over you. It rains ice: spikes as big as houses fall on everything below, shattering the ground, killing what they hit and freezing all of it into a field of ice and carnage.")
                        .unlock(1).levels(5, 20).cost(3, 2)
                        .node(2, 3, GRAND_EARTHQUAKE, THORNSPIRE)
                        .standard(36.0, 2.0, 150.0)
                        .param("radius", 128, "Radius of the storm cloud")
                        .param("duration", 12, "Seconds it rains ice")
                        .param("spikes", 160, "Giant ice spikes that fall")
                        .param("safe_radius", 7, "Radius around you the spikes spare")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("CHARGE", AbilityText.charge())
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("charge", "ability_activation", "recovery")
                        .executor(ObliterationAbilities::iceRain)
                        .build())
                .build();
    }
}
