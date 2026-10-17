package com.fantasyweapons.material;

import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;

/**
 * The 13 legendary materials, one per weapon. Each has three forms: the ore block found in the world, the raw material
 * it drops and the refined material the Arcane Refinery makes of it. Models, animations and textures are named after
 * the weapon ({@code geo/block/voidfang_ore}, {@code geo/item/voidfang_raw}, {@code geo/item/voidfang_refined}).
 */
public enum Material {
    VOID_CRYSTAL("voidfang", "Void Crystal", "void_crystal_ore", "Void Crystal Ore", "void_crystal", "Void Crystal", "void_core", "Void Core",
            0xB04DFF, "irregular", "The End's outer islands, inside end stone",
            "Almost-black metallic stone split by thin violet cracks, its crystals broken into floating sections."),
    SOLARITE("solaris", "Solarite", "solarite_ore", "Solarite Ore", "solarite_crystal", "Solarite Crystal", "solarite_core", "Solarite Core",
            0xFFB030, "warm", "Badlands and deserts, between Y 0 and 60",
            "Dark volcanic stone holding a small star: golden cracks radiate from a white-hot core."),
    FROSTHEART("frostrend", "Frostheart Crystal", "frostheart_ore", "Frostheart Ore", "frostheart_shard", "Frostheart Shard", "frostheart_crystal",
            "Frostheart Crystal", 0x7FE8FF, "slow", "Frozen peaks and ice spikes, between Y 60 and 200",
            "Blue-grey stone crusted with frost, with branching ice crystals deep blue at the root and white at the tips."),
    BLOODSTONE("doomcleaver", "Bloodstone", "bloodstone_ore", "Bloodstone Ore", "bloodstone_fragment", "Bloodstone Fragment", "bloodstone_core",
            "Bloodstone Core", 0xFF3424, "heartbeat", "The Nether's crimson forests",
            "Black metallic rock threaded with blood-like veins; its blade crystals pulse with a slow, heavy heartbeat."),
    THUNDERCRYST("stormbreaker", "Thundercryst", "thundercryst_ore", "Thundercryst Ore", "thundercryst", "Thundercryst", "thundercryst_core",
            "Thundercryst Core", 0x3FF0FF, "electric", "Windswept hills and stony or jagged peaks, above Y 90",
            "Dark metallic blue stone with zigzag crystals like frozen lightning; energy runs quickly along their veins."),
    NECROSTONE("gravebite", "Necrostone", "necrostone_ore", "Necrostone Ore", "necrostone_shard", "Necrostone Shard", "necrostone_heart",
            "Necrostone Heart", 0x5DFF7A, "uneven", "The Deep Dark, around ancient cities",
            "Ancient rune-carved black stone with a skull-shaped shadow behind a window; shards fade from purple to spectral cyan."),
    SOULSHARD("soulreaper", "Soulshard", "soulshard_ore", "Soulshard Ore", "soulshard", "Soulshard", "soulshard_core", "Soulshard Core",
            0x5DE0FF, "flow", "The Nether's soul sand valleys",
            "Dark grey stone with curved, scythe-like crystals; light moves slowly from indigo to soul-blue."),
    VERDANT("bloomfall", "Verdant Core", "verdant_ore", "Verdant Ore", "verdant_core", "Verdant Core", "verdant_heart", "Verdant Heart",
            0x7DFF4A, "organic", "Lush caves",
            "Dark earth with green crystals grown together with roots; leaf-shaped plates sprout and the glow swells slowly."),
    ECLIPSITE("eclipse_reaper", "Eclipsite", "eclipsite_ore", "Eclipsite Ore", "eclipsite", "Eclipsite", "eclipse_core", "Eclipse Core",
            0xE8D9A8, "alternate", "Any overworld cave between Y -20 and 20 (rare)",
            "Split stone, half black and half pale; dark and light crystals meet at a window where light and dark alternate."),
    ASTRALITE("starforge", "Astralite", "astralite_ore", "Astralite Ore", "astralite", "Astralite", "astralite_core", "Astralite Core",
            0x8F7BFF, "cosmic", "The End's highland islands, near their tops",
            "Black and navy stone dotted with star points; crystals like pieces of night sky with fragments orbiting slowly."),
    AETHERITE("aetherlance", "Aetherite", "aetherite_ore", "Aetherite Ore", "aetherite", "Aetherite", "aetherite_core", "Aetherite Core",
            0x5FE8FF, "smooth", "Under meadows and cherry groves, high in the hills",
            "Pale, almost metallic stone with clean straight crystals banded in gold; energy flows from cyan to white and gold."),
    TITANSTONE("monolith", "Titanstone", "titanstone_ore", "Titanstone Ore", "titanstone", "Titanstone", "titanstone_core", "Titanstone Core",
            0xFFAE3A, "heavy", "The deepslate layer, between Y -64 and -30",
            "Brown-grey rune-cut stone broken by massive amber slabs; molten energy in the cracks pulses like a giant heartbeat."),
    INFERNITE("infernochain", "Infernite", "infernite_ore", "Infernite Ore", "infernite", "Infernite", "infernite_core", "Infernite Core",
            0xFF6A1A, "heat", "The Nether's basalt deltas, right beside lava",
            "Black volcanic rock with a molten core showing through glowing cracks; flame-shaped crystals pulse with heat.");

    private final String weapon;
    private final String materialName;
    private final String oreId, oreName, rawId, rawName, refinedId, refinedName;
    private final int color;
    private final String pulse;
    private final String found;
    private final String look;

    Material(String weapon, String materialName, String oreId, String oreName, String rawId, String rawName, String refinedId, String refinedName,
             int color, String pulse, String found, String look) {
        this.weapon = weapon;
        this.materialName = materialName;
        this.oreId = oreId;
        this.oreName = oreName;
        this.rawId = rawId;
        this.rawName = rawName;
        this.refinedId = refinedId;
        this.refinedName = refinedName;
        this.color = color;
        this.pulse = pulse;
        this.found = found;
        this.look = look;
    }

    /** Id of the weapon this material belongs to; also the prefix of its asset names. */
    public String weapon() {
        return weapon;
    }

    public WeaponDefinition weaponDefinition() {
        return Weapons.get(weapon);
    }

    public Element element() {
        return weaponDefinition().element();
    }

    public String materialName() {
        return materialName;
    }

    public String oreId() {
        return oreId;
    }

    public String oreName() {
        return oreName;
    }

    public String rawId() {
        return rawId;
    }

    public String rawName() {
        return rawName;
    }

    public String refinedId() {
        return refinedId;
    }

    public String refinedName() {
        return refinedName;
    }

    /** The material's glow colour (RGB). */
    public int color() {
        return color;
    }

    /** The rhythm its glow pulses with. */
    public String pulse() {
        return pulse;
    }

    /** Where its ore is found. */
    public String found() {
        return found;
    }

    public String look() {
        return look;
    }

    /** Asset name of the ore block model, e.g. {@code voidfang_ore}. */
    public String oreAsset() {
        return weapon + "_ore";
    }

    public String rawAsset() {
        return weapon + "_raw";
    }

    public String refinedAsset() {
        return weapon + "_refined";
    }

    /** Prefix of the material's tools' ids and asset names, e.g. {@code void_crystal} for {@code void_crystal_pickaxe}. */
    public String toolPrefix() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The name its tools go by, e.g. "Void Crystal" (the Frostheart Crystal's tools are just "Frostheart"). */
    public String toolName() {
        StringBuilder out = new StringBuilder();
        for (String word : toolPrefix().split("_")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    /** How many of a raw material, and of its additive, one refining takes. */
    public static final int REFINE_COUNT = 8;

    /**
     * What the refinery melts in with this material to refine it, chosen by its element and the land it comes from:
     * magma for Infernite, ender pearls for Void Crystal, glowstone for Solarite, packed ice for Frostheart...
     */
    public net.minecraft.world.item.Item additive() {
        return switch (this) {
            case VOID_CRYSTAL -> net.minecraft.world.item.Items.ENDER_PEARL;
            case SOLARITE -> net.minecraft.world.item.Items.GLOWSTONE_DUST;
            case FROSTHEART -> net.minecraft.world.item.Items.PACKED_ICE;
            case BLOODSTONE -> net.minecraft.world.item.Items.NETHER_WART;
            case THUNDERCRYST -> net.minecraft.world.item.Items.COPPER_INGOT;
            case NECROSTONE -> net.minecraft.world.item.Items.BONE;
            case SOULSHARD -> net.minecraft.world.item.Items.SOUL_SAND;
            case VERDANT -> net.minecraft.world.item.Items.MOSS_BLOCK;
            case ECLIPSITE -> net.minecraft.world.item.Items.PHANTOM_MEMBRANE;
            case ASTRALITE -> net.minecraft.world.item.Items.POPPED_CHORUS_FRUIT;
            case AETHERITE -> net.minecraft.world.item.Items.FEATHER;
            case TITANSTONE -> net.minecraft.world.item.Items.COBBLED_DEEPSLATE;
            case INFERNITE -> net.minecraft.world.item.Items.MAGMA_BLOCK;
        };
    }

    /**
     * The material a weapon is forged from and infused with: its own, or for a mage's staff or spellbook (which have no
     * ore of their own) the legendary material of its element.
     */
    public static Material ofWeapon(String weaponId) {
        for (Material m : values()) if (m.weapon.equals(weaponId)) return m;
        WeaponDefinition def = Weapons.get(weaponId);
        if (def == null) return null;
        return switch (def.element()) {
            case FIRE -> INFERNITE;
            case ICE -> FROSTHEART;
            case WATER -> AETHERITE;
            case NATURE -> VERDANT;
            case LIGHT, SOLAR -> SOLARITE;
            case VOID -> VOID_CRYSTAL;
            default -> null;
        };
    }
}
