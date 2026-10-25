package com.fantasyweapons.armor;

import com.fantasyweapons.text.Tr;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.weapon.Element;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The legendary armor sets: thirteen for the warriors, one per blade, each crafted from its weapon's refined material, and
 * six regalia for the Mage, one per element of the staffs and spellbooks. Every piece has its own bonus; wearing all
 * four unlocks the set ability. Models and textures: {@code geo/armor/<id>_armor}.
 */
public enum ArmorSet {
    VOIDFANG("voidfang", "Voidfang", Material.VOID_CRYSTAL, ArmorClass.ASSASSIN,
            "Tight charcoal cloth with a void-black mouth wrap, throwing knives and a rift shard drifting beside the head.",
            pieces(List.of(stat(ArmorStat.VOID_ABILITY_DAMAGE, 0.15)),
                    List.of(health(), stat(ArmorStat.VOID_DAMAGE, 0.10)),
                    List.of(stat(ArmorStat.COOLDOWN_REDUCTION, 0.10)),
                    List.of(speed(), stat(ArmorStat.TELEPORT_DISTANCE, 0.25))),
            "Master of the Void", "After a teleport, the next strike tears a rift that echoes the hit."),
    SOLARIS("solaris", "Solaris", Material.SOLARITE, ArmorClass.PALADIN,
            "White and gold plate around a sun core, a crowned open helm with a turning sun halo and broad gold-rayed pauldrons.",
            pieces(List.of(stat(ArmorStat.SOLAR_ABILITY_DAMAGE, 0.15)),
                    List.of(health(), armor(2)),
                    List.of(stat(ArmorStat.CHARGE_SPEED, 0.20)),
                    List.of(knockbackResistance())),
            "Crown of Dawn", "Fully charged strikes release a short radiant burst that burns nearby enemies."),
    FROSTREND("frostrend", "Frostrend", Material.FROSTHEART, ArmorClass.PALADIN,
            "Silver plate overgrown by ice crystals breaking out of it, a fur collar and an uneven crystal crown.",
            pieces(List.of(stat(ArmorStat.FREEZE_DURATION, 0.25)),
                    List.of(health(), stat(ArmorStat.FROST_RESISTANCE, 0.25)),
                    List.of(stat(ArmorStat.ICE_ABILITY_RANGE, 0.20)),
                    List.of(stat(ArmorStat.ICE_SPEED, 0.25))),
            "Heart of Winter", "Enemies that hit you are chilled and slowed."),
    DOOMCLEAVER("doomcleaver", "Doomcleaver", Material.BLOODSTONE, ArmorClass.BERSERKER,
            "Lopsided black steel slabs, spikes and chains, a horned helm with bone tusks and a skull pauldron over a blood-red core.",
            pieces(List.of(stat(ArmorStat.CRIT_DAMAGE, 0.20)),
                    List.of(health(), stat(ArmorStat.LIFE_STEAL, 0.05)),
                    List.of(knockbackResistance()),
                    List.of(stat(ArmorStat.HEAVY_ATTACK_DAMAGE, 0.15))),
            "Blood Frenzy", "Below half health, life steal and attack speed rise sharply."),
    STORMBREAKER("stormbreaker", "Stormbreaker", Material.THUNDERCRYST, ArmorClass.PALADIN,
            "Dark conductive steel with gold mechanics, zigzag channels from a cyan core and a helm crested with tesla nodes.",
            pieces(List.of(stat(ArmorStat.CHAIN_JUMPS, 1)),
                    List.of(health(), stat(ArmorStat.LIGHTNING_DAMAGE, 0.10)),
                    List.of(attackSpeed()),
                    List.of(speed())),
            "Eye of the Storm", "Every few hits, a lightning bolt strikes the target."),
    GRAVEBITE("gravebite", "Gravebite", Material.NECROSTONE, ArmorClass.BERSERKER,
            "Dark iron caged in bone: a ram-horned skull helm, a ribcage over a green soul core and a torn purple cloak.",
            pieces(List.of(stat(ArmorStat.SUMMON_DAMAGE, 0.20)),
                    List.of(health(), stat(ArmorStat.SOUL_DRAIN, 0.05)),
                    List.of(stat(ArmorStat.SUMMON_DURATION, 0.25)),
                    List.of(stat(ArmorStat.UNDEAD_RESISTANCE, 0.20))),
            "Legion of the Grave", "Slain enemies sometimes rise as a short-lived spectral ally."),
    SOULREAPER("soulreaper", "Soulreaper", Material.SOULSHARD, ArmorClass.ASSASSIN,
            "A wide cowl and bone half-mask with a soul-blue seam, a long split robe, a tattered cape and hidden wrist blades.",
            pieces(List.of(stat(ArmorStat.SOUL_HARVEST_RANGE, 0.25)),
                    List.of(health(), stat(ArmorStat.LIFE_DRAIN, 0.05)),
                    List.of(stat(ArmorStat.ABILITY_DURATION, 0.20)),
                    List.of(speed())),
            "Harvester of Souls", "Each kill stores a soul; stored souls empower the next ability."),
    BLOOMFALL("bloomfall", "Bloomfall", Material.VERDANT, ArmorClass.ASSASSIN,
            "Earth-brown cloth and leather grown through with roots and leaves, an antlered hood, a quiver and poison blooms.",
            pieces(List.of(stat(ArmorStat.POISON_DURATION, 0.25)),
                    List.of(health(), stat(ArmorStat.REGENERATION, 1)),
                    List.of(stat(ArmorStat.VINE_RANGE, 0.20)),
                    List.of(knockbackResistance())),
            "Heart of the Grove", "Standing still slowly heals you and roots nearby enemies in place."),
    ECLIPSE_REAPER("eclipse_reaper", "Eclipse Reaper", Material.ECLIPSITE, ArmorClass.PALADIN,
            "One set, two palettes: white, silver and gold in light mode, black and purple in dark mode, with an eclipse ring on the helm.",
            pieces(List.of(stat(ArmorStat.CELESTIAL_ABILITY_DAMAGE, 0.15)),
                    List.of(health(), stat(ArmorStat.MODE_SHIELD, 4)),
                    List.of(stat(ArmorStat.MODE_SWITCH_SPEED, 0.30)),
                    List.of(stat(ArmorStat.DARK_SPEED, 0.15))),
            "Totality", "Switching mode releases an eclipse wave and briefly grants both modes' bonuses."),
    STARFORGE("starforge", "Starforge", Material.ASTRALITE, ArmorClass.ASSASSIN,
            "Star-flecked navy cloth, a starfield circlet, a tall mage collar and a turning orrery ring above the head.",
            pieces(List.of(stat(ArmorStat.COSMIC_ABILITY_DAMAGE, 0.15)),
                    List.of(health(), stat(ArmorStat.GRAVITY_RESISTANCE, 0.30)),
                    List.of(stat(ArmorStat.ABILITY_RANGE, 0.15)),
                    List.of(stat(ArmorStat.FALL_IMMUNITY, 1))),
            "Gravity Well", "Heavy attacks pull nearby enemies toward the impact."),
    AETHERLANCE("aetherlance", "Aetherlance", Material.AETHERITE, ArmorClass.PALADIN,
            "Sleek white and silver plate with gold accents, cyan channels into every limb and a finned helm with a floating halo.",
            pieces(List.of(stat(ArmorStat.PROJECTILE_SPEED, 0.25)),
                    List.of(health(), stat(ArmorStat.ENERGY_DAMAGE, 0.10)),
                    List.of(attackSpeed()),
                    List.of(speed(), stat(ArmorStat.DASH_DISTANCE, 0.25))),
            "Perfect Precision", "Critical hits fire a small piercing energy bolt along the strike."),
    MONOLITH("monolith", "Monolith", Material.TITANSTONE, ArmorClass.BERSERKER,
            "The heaviest set: stone and obsidian slabs bound in bronze, amber runes, a great helm with one visor slit.",
            pieces(List.of(armor(2)),
                    List.of(health(), armor(3)),
                    List.of(knockbackResistance()),
                    List.of(stat(ArmorStat.SLAM_DAMAGE, 0.20))),
            "Immovable", "You cannot be knocked back, and heavy attacks send out a shockwave."),
    INFERNOCHAIN("infernochain", "Infernochain", Material.INFERNITE, ArmorClass.BERSERKER,
            "Black demonic plate with crimson panels, a burning visor and mechanical jaw, turning shoulder gears and crossed chains.",
            pieces(List.of(stat(ArmorStat.FIRE_ABILITY_DAMAGE, 0.15)),
                    List.of(health(), stat(ArmorStat.FIRE_RESISTANCE, 0.30)),
                    List.of(stat(ArmorStat.CHAIN_REACH, 0.20)),
                    List.of(speed(), stat(ArmorStat.BURN_ON_DASH, 1))),
            "Hellforged", "Chain attacks leave a burning trail that ignites enemies."),

    // ---- the Mage's regalia: one robe for each element of the staffs and spellbooks ----
    PYROMANCER("pyromancer", "Pyromancer", Material.INFERNITE, ArmorClass.MAGE,
            "Charred crimson robes trimmed in molten gold, a flame-crowned hood, smouldering mantles and three embers circling the wearer.",
            pieces(List.of(stat(ArmorStat.FIRE_ABILITY_DAMAGE, 0.15)),
                    List.of(health(), stat(ArmorStat.FIRE_RESISTANCE, 0.30)),
                    List.of(stat(ArmorStat.SPELL_DAMAGE, 0.10)),
                    List.of(speed())),
            "Living Flame", "Every few spell hits erupt in a nova of fire around the target."),
    CRYOMANCER("cryomancer", "Cryomancer", Material.FROSTHEART, ArmorClass.MAGE,
            "Pale blue robes stiff with frost, an icicle diadem, a fur-lined stole and shards of ice drifting about the shoulders.",
            pieces(List.of(stat(ArmorStat.ICE_ABILITY_DAMAGE, 0.15)),
                    List.of(health(), stat(ArmorStat.FROST_RESISTANCE, 0.25)),
                    List.of(stat(ArmorStat.FREEZE_DURATION, 0.25)),
                    List.of(stat(ArmorStat.ICE_SPEED, 0.25))),
            "Deep Freeze", "Spells crust their targets in frost; one frozen deep enough is locked in ice."),
    TIDEMANCER("tidemancer", "Tidemancer", Material.AETHERITE, ArmorClass.MAGE,
            "Sea-green robes that ripple like water, a coral-and-pearl circlet, a flowing stole and three droplets orbiting the hands.",
            pieces(List.of(stat(ArmorStat.HEALING_POWER, 0.15)),
                    List.of(health(), stat(ArmorStat.WATER_ABILITY_DAMAGE, 0.15)),
                    List.of(stat(ArmorStat.COOLDOWN_REDUCTION, 0.10)),
                    List.of(speed())),
            "Tidal Grace", "Your heals send out a wave that heals those around the healed and washes enemies away."),
    ARCHDRUID("archdruid", "Archdruid", Material.VERDANT, ArmorClass.MAGE,
            "Bark-brown robes grown through with moss and leaves, an antlered crown, a mantle of vines and spores drifting about.",
            pieces(List.of(stat(ArmorStat.HEALING_POWER, 0.10)),
                    List.of(health(), stat(ArmorStat.REGENERATION, 1)),
                    List.of(stat(ArmorStat.NATURE_ABILITY_DAMAGE, 0.15)),
                    List.of(knockbackResistance())),
            "Wild Growth", "Allies around you slowly heal, and your spells root what they strike."),
    LUMINARCH("luminarch", "Luminarch", Material.SOLARITE, ArmorClass.MAGE,
            "White and gold vestments, a radiant halo above a tall mitre, a sun-embroidered stole and motes of light circling.",
            pieces(List.of(stat(ArmorStat.HEALING_POWER, 0.15)),
                    List.of(health(), armor(2)),
                    List.of(stat(ArmorStat.LIGHT_ABILITY_DAMAGE, 0.15)),
                    List.of(speed(), stat(ArmorStat.CHARGE_SPEED, 0.10))),
            "Radiant Judgement", "Healing or shielding an ally calls a ray of light down on the nearest enemy."),
    UMBRAMANCER("umbramancer", "Umbramancer", Material.VOID_CRYSTAL, ArmorClass.MAGE,
            "Robes of living shadow edged in violet, a deep cowl with burning eyes, a tattered cape and three wisps of night.",
            pieces(List.of(stat(ArmorStat.VOID_ABILITY_DAMAGE, 0.15)),
                    List.of(health(), stat(ArmorStat.VOID_DAMAGE, 0.10)),
                    List.of(stat(ArmorStat.COOLDOWN_REDUCTION, 0.10)),
                    List.of(speed())),
            "Shadow Pact", "Each kill feeds you its life, and spreads a withering curse to the enemies around it.");

    /** Refined material cost of each piece (its forging pattern's cores). */
    public static int cost(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 9;
            case CHESTPLATE -> 15;
            case LEGGINGS -> 13;
            case BOOTS -> 8;
            case BODY -> 15;
        };
    }

    public static final List<ArmorItem.Type> PIECES = List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
            ArmorItem.Type.BOOTS);

    /**
     * One piece bonus: a stat or a vanilla attribute.
     *
     * @param text tooltip line
     */
    public record Bonus(Supplier<Holder<Attribute>> attribute, double amount, AttributeModifier.Operation operation, String text,
                        ArmorStat stat) {
        /** The tooltip line in the player's language. */
        @Override
        public String text() {
            return Tr.s(text);
        }
    }

    private final String id;
    private final String displayName;
    private final Material material;
    private final ArmorClass armorClass;
    private final String look;
    private final Map<ArmorItem.Type, List<Bonus>> bonuses;
    private final String abilityName;
    private final String abilityText;

    ArmorSet(String id, String displayName, Material material, ArmorClass armorClass, String look, Map<ArmorItem.Type, List<Bonus>> bonuses,
             String abilityName, String abilityText) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.armorClass = armorClass;
        this.look = look;
        this.bonuses = bonuses;
        this.abilityName = abilityName;
        this.abilityText = abilityText;
    }

    public String id() {
        return id;
    }

    /** The set's name in the player's language. */
    public String displayName() {
        return Tr.s(displayName);
    }

    /** The set's name in English whatever the language. */
    public String englishName() {
        return displayName;
    }

    public Material material() {
        return material;
    }

    public Element element() {
        return material.element();
    }

    public ArmorClass armorClass() {
        return armorClass;
    }

    public String look() {
        return Tr.s(look);
    }

    public List<Bonus> bonuses(ArmorItem.Type type) {
        return bonuses.getOrDefault(type, List.of());
    }

    public String abilityName() {
        return Tr.s(abilityName);
    }

    /** The set ability's name in English whatever the language. */
    public String englishAbilityName() {
        return abilityName;
    }

    public String abilityText() {
        return Tr.s(abilityText);
    }

    /** Registry id of a piece, e.g. {@code voidfang_helmet}. */
    public String itemId(ArmorItem.Type type) {
        return id + "_" + type.getName();
    }

    /** Display name of a piece, e.g. "Voidfang Helmet". */
    public String itemName(ArmorItem.Type type) {
        // the regalia are robes: a hood, a robe, vestments and sandals
        if (regalia()) return displayName + " " + switch (type) {
            case HELMET -> "Hood";
            case CHESTPLATE, BODY -> "Robe";
            case LEGGINGS -> "Vestments";
            case BOOTS -> "Sandals";
        };
        String piece = type.getName();
        return displayName + " " + Character.toUpperCase(piece.charAt(0)) + piece.substring(1).toLowerCase(Locale.ROOT);
    }

    /** The top-level bones of a piece in the set's model (the ones GeckoLib shows when that piece is worn). */
    public static List<String> bones(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> List.of("armorHead");
            case CHESTPLATE, BODY -> List.of("armorBody", "armorRightArm", "armorLeftArm");
            case LEGGINGS -> List.of("armorRightLeg", "armorLeftLeg");
            case BOOTS -> List.of("armorRightBoot", "armorLeftBoot");
        };
    }

    /** Asset name of the model, e.g. {@code voidfang_armor}. */
    public String asset() {
        return id + "_armor";
    }

    /** A blade's own set, or for a staff or spellbook the regalia of its element. */
    public static ArmorSet ofWeapon(String weaponId) {
        for (ArmorSet s : values()) if (s.id.equals(weaponId)) return s;
        var def = com.fantasyweapons.weapon.Weapons.get(weaponId);
        if (def == null || !def.weaponClass().magic()) return null;
        return switch (def.element()) {
            case FIRE -> PYROMANCER;
            case ICE -> CRYOMANCER;
            case WATER -> TIDEMANCER;
            case NATURE -> ARCHDRUID;
            case LIGHT, SOLAR -> LUMINARCH;
            case VOID -> UMBRAMANCER;
            default -> null;
        };
    }

    /** Whether this is one of the Mage's regalia. */
    public boolean regalia() {
        return armorClass == ArmorClass.MAGE;
    }

    // ------------------------------------------------------------------------------------------------------------
    // building blocks
    // ------------------------------------------------------------------------------------------------------------

    private static Map<ArmorItem.Type, List<Bonus>> pieces(List<Bonus> helmet, List<Bonus> chest, List<Bonus> legs, List<Bonus> boots) {
        Map<ArmorItem.Type, List<Bonus>> m = new EnumMap<>(ArmorItem.Type.class);
        m.put(ArmorItem.Type.HELMET, helmet);
        m.put(ArmorItem.Type.CHESTPLATE, chest);
        m.put(ArmorItem.Type.LEGGINGS, legs);
        m.put(ArmorItem.Type.BOOTS, boots);
        return m;
    }

    private static Bonus stat(ArmorStat stat, double amount) {
        return new Bonus(stat::holder, amount, AttributeModifier.Operation.ADD_VALUE, stat.describe(amount), stat);
    }

    private static Bonus health() {
        return new Bonus(() -> Attributes.MAX_HEALTH, 4, AttributeModifier.Operation.ADD_VALUE, "+4 Max Health", null);
    }

    private static Bonus armor(int points) {
        return new Bonus(() -> Attributes.ARMOR, points, AttributeModifier.Operation.ADD_VALUE, "+" + points + " Armor", null);
    }

    private static Bonus speed() {
        return new Bonus(() -> Attributes.MOVEMENT_SPEED, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, "+10% Movement Speed", null);
    }

    private static Bonus attackSpeed() {
        return new Bonus(() -> Attributes.ATTACK_SPEED, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, "+10% Attack Speed", null);
    }

    private static Bonus knockbackResistance() {
        return new Bonus(() -> Attributes.KNOCKBACK_RESISTANCE, 0.10, AttributeModifier.Operation.ADD_VALUE, "+10% Knockback Resistance", null);
    }
}
