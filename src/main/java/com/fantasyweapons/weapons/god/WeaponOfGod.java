package com.fantasyweapons.weapons.god;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * THE WEAPON OF GOD — the Great Sword of Obliteration and the Spell Book of Absolute Domination laid on the Worldforge
 * together, and what came out: a class of its own, The Last Fragment of Brilliant Light. Each of its six powers is the
 * sword's and the book's power of one element at once; its last three are its own. Every one is summoned slowly
 * ({@link DivineSummoning}) and must rest a long time after.
 * <pre>
 *                [JUDGEMENT OF THE FINAL DAWN] (light)
 *                /                                  \
 *   [THE ABYSS BEYOND DIMENSIONS] (void)      [THE HELLFIRE APOCALYPSE] (fire)
 *                |                                  |
 *   [THE BREAKING OF THE WORLD] (earth)       [WRATH OF THE WORLD TREE] (nature)
 *                \                                  /
 *                       [THE ETERNAL WINTER] (ice)
 *                /                                  \
 *        [TRIDENT FROM HEAVEN]                [DIMENSION BREAKAGE]
 *                \                                  /
 *                   [COMBINED POWER OF THE 6 GODS]
 * </pre>
 */
public final class WeaponOfGod {
    public static final String ID = "the_weapon_of_god";
    public static final String FINAL_DAWN = "judgement_of_the_final_dawn";
    public static final String ABYSS = "the_abyss_beyond_dimensions";
    public static final String HELLFIRE = "the_hellfire_apocalypse";
    public static final String BREAKING = "the_breaking_of_the_world";
    public static final String WORLD_TREE = "wrath_of_the_world_tree";
    public static final String WINTER = "the_eternal_winter";
    public static final String TRIDENT = "trident_from_heaven";
    public static final String SIX_GODS = "combined_power_of_the_six_gods";
    public static final String BREAKAGE = "dimension_breakage";

    /** The nine powers in order: the six elements' (light, void, fire, earth, nature, ice), then its own three. */
    public static final java.util.List<String> POWERS = java.util.List.of(FINAL_DAWN, ABYSS, HELLFIRE, BREAKING, WORLD_TREE, WINTER, TRIDENT, BREAKAGE, SIX_GODS);

    private WeaponOfGod() {
    }

    private static AbilityDefinition.Builder power(String id, String name, String description, int x, int y, String... parents) {
        return AbilityDefinition.builder(id, AbilityKind.ULTIMATE)
                .name(name)
                .description(description)
                .unlock(1).levels(5, 20).cost(3, 2)
                .node(x, y, parents)
                .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                .animations("charge", "heavy_attack", "recovery");
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder(ID)
                .name("The Weapon of God", "Where the Light Began")
                .lore("The Sword that ends things and the Book that commands them were laid on the altar together, and the fire would not take them apart again. What rose from it was older than either: a shard of the first light, from before the world had a shape. Every power the world has ever feared answers to it at once.",
                        "the Page before the First")
                .element(Element.DIVINITY)
                .rarity(Rarity.BRILLIANT_LIGHT)
                .geo("weapon_of_god")
                .type(WeaponClass.GREATSWORD)
                .damage(54f, 2.8f)
                .crit(0.2f, 2.6f)
                .theme(0xFFE29A, 0xFFFFFF)
                // ---- the six elements, the sword's and the book's power of each at once ----
                .ability(power(FINAL_DAWN, "Judgement of the Final Dawn",
                        "Summoned slowly, from the map. The Beam of Grant Execution falls from a circle so high it fades into the sky, erasing everything within 160 blocks to the bottom of the world; the moment it bites, four Great Slashes burst out of it across the land, cutting a cross into the world 448 blocks long.",
                        2, 0)
                        .standard(60.0, 0, 900.0)
                        .remote(32)
                        .param("radius", 160, "Radius the beam erases")
                        .param("delay", 8, "Seconds the circle takes to open before the beam falls")
                        .param("duration", 6, "Seconds the beam burns")
                        .param("length", 448, "How far each slash runs")
                        .param("width", 160, "How wide each slash sweeps")
                        .param("slashes", 4, "Great Slashes that burst out of the beam")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("SLASHES", AbilityText.param("length", "m", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .executor(FusedPowers::finalDawn)
                        .build())
                .ability(power(ABYSS, "The Abyss Beyond Dimensions",
                        "Summoned slowly, from the map. Space is lifted out of its three dimensions: the chunks are torn up, turned and shuffled. Then the Ultramassive Blackhole tears open over the broken land and drags all of it in, everything within 288 blocks, until nothing is left but the pit it fed on.",
                        1, 1, FINAL_DAWN)
                        .standard(60.0, 0, 900.0)
                        .remote(32)
                        .param("radius", 288, "Radius the black hole swallows")
                        .param("shatter_radius", 112, "Radius of the broken chunks")
                        .param("duration", 18, "Seconds the black hole feeds")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .executor(FusedPowers::abyss)
                        .build())
                .ability(power(HELLFIRE, "The Hellfire Apocalypse",
                        "Summoned slowly, from the map. The ground at the target splits and glows while the Arch Dragon of Hell bursts out of the earth behind you and flies there; it dives in the moment the earth erupts, and 192 blocks of land burn down to lava, magma and coal.",
                        3, 1, FINAL_DAWN)
                        .standard(60.0, 0, 900.0)
                        .remote(32)
                        .param("radius", 192, "Radius of the burning land")
                        .param("flight", 9, "Seconds the dragon flies before it dives")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .executor(FusedPowers::hellfire)
                        .build())
                .ability(power(BREAKING, "The Breaking of the World",
                        "Summoned slowly, from the map. The Grand Earthquake breaks the land outward from the target in plates and fissures for 288 blocks, and then three canyons of Tectonic Breakage tear across it, crossing at the target, down to bedrock and filling with lava.",
                        1, 2, ABYSS)
                        .standard(60.0, 0, 900.0)
                        .remote(32)
                        .param("radius", 288, "Radius of the earthquake")
                        .param("length", 384, "Length of each canyon")
                        .param("canyons", 3, "Canyons that tear across the land")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .executor(FusedPowers::breaking)
                        .build())
                .ability(power(WORLD_TREE, "Wrath of the World Tree",
                        "Summoned slowly, from the map. The Roots of the Great Tree burst out and seize everything within 192 blocks, and the Thornspire Cataclysm spirals out among them, impaling what the roots hold. Roots and thorns stay forever.",
                        3, 2, HELLFIRE)
                        .standard(60.0, 0, 900.0)
                        .remote(32)
                        .param("radius", 192, "Radius the roots and thorns take")
                        .param("roots", 56, "Giant roots that break out")
                        .param("spires", 120, "Thorn spires along the spiral")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .executor(FusedPowers::worldTree)
                        .build())
                .ability(power(WINTER, "The Eternal Winter",
                        "Summoned slowly, from the map. A storm cloud gathers over the target and rains spikes of ice as big as houses, and while they fall the sea rises and the Ice Age rolls out across 224 blocks. Behind the wave there is nothing but ice.",
                        2, 3, BREAKING, WORLD_TREE)
                        .standard(60.0, 0, 900.0)
                        .remote(32)
                        .param("radius", 224, "How far the tsunami rolls")
                        .param("spikes", 220, "Spikes of ice that fall")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .executor(FusedPowers::winter)
                        .build())
                // ---- its own ----
                .ability(power(TRIDENT, "Trident from Heaven",
                        "Summoned slowly, from the map. An enormous opening tears through the sky and a giant trident slowly comes out of it, then falls on the place you chose. Everything in its path is destroyed, and when it strikes, everything within 256 blocks.",
                        1, 4, WINTER)
                        .cost(4, 2)
                        .standard(70.0, 0, 1200.0)
                        .remote(40)
                        .param("radius", 256, "Radius destroyed where it strikes")
                        .param("open", 8, "Seconds the sky takes to tear open")
                        .param("emerge", 7, "Seconds the trident takes to come out of the sky")
                        .param("fall", 3, "Seconds it falls")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("RANGE", AbilityText.param("remote_range", " chunks", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .executor(HeavenTrident::cast)
                        .build())
                .ability(power(BREAKAGE, "Dimension Breakage",
                        "Summoned slowly, from the map. 4th Dimension Ascension, far greater: the figures of the fourth dimension fill the sky and everything within 512 blocks is folded into one enormous cube of the chunks, its sides turned sideways, its top hung upside down, the ground left as it was. The tesseract hangs in the middle for 30 seconds, then explodes and deletes everything within 512 blocks.",
                        3, 4, WINTER)
                        .cost(5, 2)
                        .standard(80.0, 0, 1800.0)
                        .remote(48)
                        .param("radius", 512, "Radius folded and then deleted")
                        .param("delay", 8, "Seconds before space breaks")
                        .param("hold", 30, "Seconds the tesseract hangs in the cube before it explodes")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("RANGE", AbilityText.param("remote_range", " chunks", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .executor(DimensionBreakage::cast)
                        .build())
                .ability(power(SIX_GODS, "Combined Power of the 6 Gods",
                        "Summoned slowly, from the map. An enormous magic circle in the colours of the fourth dimension opens in the sky, far larger than Grant Execution's, and six circles as large as that one turn round it, one in the colour of each power, tilted in toward it. Each pours its power into the middle, and a beam of every colour at once falls from it, destroying everything within 512 blocks.",
                        2, 5, TRIDENT, BREAKAGE)
                        .cost(5, 2)
                        .standard(80.0, 0, 1800.0)
                        .remote(48)
                        .param("radius", 512, "Radius the beam destroys")
                        .param("delay", 12, "Seconds the circles take to open and fill before the beam falls")
                        .param("duration", 8, "Seconds the beam burns")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("RANGE", AbilityText.param("remote_range", " chunks", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .executor(SixGods::cast)
                        .build())
                .build();
    }
}
