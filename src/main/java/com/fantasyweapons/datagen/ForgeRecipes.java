package com.fantasyweapons.datagen;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.forge.ForgeRecipe;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.Weapons;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Legendary Crafting Table's recipes. Every weapon is laid out on the 9x9 grid in its own shape: its refined material (R) down
 * the blade, a catalyst (C) at its heart, an accent (A) around it and a handle (H), each chosen for the weapon's element
 * and the dimension its ore comes from - eyes of ender for the Voidfang, an end crystal for the Starforge, netherite for
 * the Nether's weapons, a heavy core for the Monolith... Armor is the usual shapes in the set's refined material (5, 8,
 * 7 and 4) bound with a material fitting its look.
 */
final class ForgeRecipes {
    private ForgeRecipes() {
    }

    /**
     * @param second a second accent (B): the Infernochain's chains, the dark half of the Eclipse Reaper (which splits its
     *               accents down the middle: light on the left, dark on the right)
     */
    record Theme(ItemLike catalyst, ItemLike accent, ItemLike handle, ItemLike second, boolean split) {
        Theme(ItemLike catalyst, ItemLike accent, ItemLike handle) {
            this(catalyst, accent, handle, null, false);
        }
    }

    /** Each weapon's catalyst, accent and handle. */
    static final Map<String, Theme> WEAPONS = new LinkedHashMap<>();
    /** Each armor set's binding (the Eclipse Reaper's splits into light and dark). */
    static final Map<ArmorSet, ItemLike[]> ARMOR = new LinkedHashMap<>();

    static {
        // the End
        WEAPONS.put("voidfang", new Theme(Items.ENDER_EYE, Items.CRYING_OBSIDIAN, Items.END_ROD));
        WEAPONS.put("starforge", new Theme(Items.END_CRYSTAL, Items.AMETHYST_SHARD, Items.END_ROD));
        // the Nether
        WEAPONS.put("infernochain", new Theme(Items.NETHERITE_INGOT, Items.MAGMA_BLOCK, Items.BLAZE_ROD, Items.CHAIN, false));
        WEAPONS.put("doomcleaver", new Theme(Items.NETHERITE_INGOT, Items.REDSTONE_BLOCK, Items.BLAZE_ROD));
        WEAPONS.put("soulreaper", new Theme(Items.GHAST_TEAR, Items.SOUL_LANTERN, Items.BLAZE_ROD));
        // the Overworld
        WEAPONS.put("solaris", new Theme(Items.GOLD_BLOCK, Items.GLOWSTONE, Items.BLAZE_ROD));
        WEAPONS.put("frostrend", new Theme(Items.DIAMOND, Items.PACKED_ICE, Items.BREEZE_ROD));
        WEAPONS.put("stormbreaker", new Theme(Items.LIGHTNING_ROD, Items.COPPER_BLOCK, Items.BREEZE_ROD));
        WEAPONS.put("gravebite", new Theme(Items.ECHO_SHARD, Items.BONE_BLOCK, Items.BONE));
        WEAPONS.put("bloomfall", new Theme(Items.SPORE_BLOSSOM, Items.MOSS_BLOCK, Items.BAMBOO));
        WEAPONS.put("eclipse_reaper", new Theme(Items.CLOCK, Items.GLOWSTONE, Items.BLAZE_ROD, Items.OBSIDIAN, true));
        WEAPONS.put("aetherlance", new Theme(Items.HEART_OF_THE_SEA, Items.GOLD_INGOT, Items.BREEZE_ROD));
        WEAPONS.put("monolith", new Theme(Items.HEAVY_CORE, Items.OBSIDIAN, Items.BREEZE_ROD));
        // the mage's focuses: a staff's haft (H) is a rod, a spellbook's spine is leather
        WEAPONS.put("cinderspire", new Theme(Items.NETHERITE_INGOT, Items.MAGMA_BLOCK, Items.BLAZE_ROD));
        WEAPONS.put("pyre_codex", new Theme(Items.GHAST_TEAR, Items.BLAZE_POWDER, Items.LEATHER));
        WEAPONS.put("rimeheart", new Theme(Items.DIAMOND, Items.BLUE_ICE, Items.BREEZE_ROD));
        WEAPONS.put("frostbound_grimoire", new Theme(Items.DIAMOND, Items.PACKED_ICE, Items.LEATHER));
        WEAPONS.put("tidecaller", new Theme(Items.HEART_OF_THE_SEA, Items.PRISMARINE, Items.BREEZE_ROD));
        WEAPONS.put("codex_of_tides", new Theme(Items.NAUTILUS_SHELL, Items.PRISMARINE_CRYSTALS, Items.LEATHER));
        WEAPONS.put("thornwake", new Theme(Items.SPORE_BLOSSOM, Items.MOSS_BLOCK, Items.BAMBOO));
        WEAPONS.put("verdant_almanac", new Theme(Items.GOLDEN_APPLE, Items.FLOWERING_AZALEA, Items.LEATHER));
        WEAPONS.put("sunwarden", new Theme(Items.GOLD_BLOCK, Items.GLOWSTONE, Items.BLAZE_ROD));
        WEAPONS.put("lumen_psalter", new Theme(Items.GLOWSTONE, Items.GOLD_INGOT, Items.LEATHER));
        WEAPONS.put("gloamreach", new Theme(Items.ENDER_EYE, Items.CRYING_OBSIDIAN, Items.END_ROD));
        WEAPONS.put("umbral_codex", new Theme(Items.SCULK_CATALYST, Items.CRYING_OBSIDIAN, Items.LEATHER));

        ARMOR.put(ArmorSet.VOIDFANG, new ItemLike[]{Items.BLACK_WOOL});        // charcoal cloth
        ARMOR.put(ArmorSet.SOLARIS, new ItemLike[]{Items.GOLD_INGOT});         // white and gold plate
        ARMOR.put(ArmorSet.FROSTREND, new ItemLike[]{Items.IRON_INGOT});       // silver plate
        ARMOR.put(ArmorSet.DOOMCLEAVER, new ItemLike[]{Items.CHAIN});          // black steel, spikes and chains
        ARMOR.put(ArmorSet.STORMBREAKER, new ItemLike[]{Items.COPPER_INGOT});  // conductive steel
        ARMOR.put(ArmorSet.GRAVEBITE, new ItemLike[]{Items.BONE_BLOCK});       // iron caged in bone
        ARMOR.put(ArmorSet.SOULREAPER, new ItemLike[]{Items.GRAY_WOOL});       // a long robe and cowl
        ARMOR.put(ArmorSet.BLOOMFALL, new ItemLike[]{Items.LEATHER});          // earth-brown cloth and leather
        ARMOR.put(ArmorSet.ECLIPSE_REAPER, new ItemLike[]{Items.GOLD_INGOT, Items.OBSIDIAN}); // light and dark
        ARMOR.put(ArmorSet.STARFORGE, new ItemLike[]{Items.BLUE_WOOL});        // star-flecked navy cloth
        ARMOR.put(ArmorSet.AETHERLANCE, new ItemLike[]{Items.QUARTZ});         // sleek white plate
        ARMOR.put(ArmorSet.MONOLITH, new ItemLike[]{Items.OBSIDIAN});          // stone and obsidian slabs
        ARMOR.put(ArmorSet.INFERNOCHAIN, new ItemLike[]{Items.NETHER_BRICK});  // demonic plate, crimson panels
    }

    /**
     * The weapon shapes, by class (9x9; R refined, C catalyst, A accent, H handle, B second accent): the blade or head is all
     * refined cores (11 for a lance up to 18 for a colossal blade), with only a guard, a pommel and the haft of other things.
     */
    static String[] shape(WeaponClass c) {
        return switch (c) {
            // a staff: a crown of cores round the catalyst on a long haft
            case STAFF -> new String[]{
                    "   RRR   ",
                    "  RRCRR  ",
                    "   RRR   ",
                    "    R    ",
                    "    H    ",
                    "    H    ",
                    "    H    ",
                    "    H    ",
                    "    A    "};
            // a spellbook laid open: pages of cores either side of a leather spine, the catalyst at its heart
            case TOME -> new String[]{
                    "         ",
                    "   ARA   ",
                    "  RRHRR  ",
                    "  RRCRR  ",
                    "  RRHRR  ",
                    "   ARA   ",
                    "         ",
                    "         ",
                    "         "};
            case LONGSWORD -> new String[]{
                    "    R    ",
                    "   RRR   ",
                    "   RRR   ",
                    "   RRR   ",
                    "    R    ",
                    "  ARCRA  ",
                    "    H    ",
                    "    H    ",
                    "    A    "};
            case GREATSWORD -> new String[]{
                    "    R    ",
                    "   RRR   ",
                    "  RRRRR  ",
                    "   RRR   ",
                    "   RRR   ",
                    " AARCRAA ",
                    "    H    ",
                    "    H    ",
                    "    A    "};
            case BATTLEAXE -> new String[]{
                    "   RRR   ",
                    "  RRRRH  ",
                    "  RRRCH  ",
                    "  RRRRH  ",
                    "      H  ",
                    "      H  ",
                    "      A  ",
                    "         ",
                    "         "};
            case SCYTHE -> new String[]{
                    "  ARRRRA ",
                    " RRRRRRH ",
                    "RR    CH ",
                    "       H ",
                    "      H  ",
                    "      H  ",
                    "     H   ",
                    "     A   ",
                    "         "};
            case WARHAMMER -> new String[]{
                    " ARRRRRA ",
                    " RRRCRRR ",
                    " ARRRRRA ",
                    "    H    ",
                    "    H    ",
                    "    H    ",
                    "    H    ",
                    "    A    ",
                    "         "};
            case LANCE -> new String[]{
                    "    R    ",
                    "    R    ",
                    "   RRR   ",
                    "   RRR   ",
                    "    R    ",
                    "  ARCRA  ",
                    "    H    ",
                    "    H    ",
                    "    A    "};
            case COLOSSAL -> new String[]{
                    "  RRRRR  ",
                    "  RRRRR  ",
                    "  RRCRR  ",
                    "   RRR   ",
                    "   ARA   ",
                    "    H    ",
                    "    H    ",
                    "   AAA   ",
                    "         "};
            case CHAINBLADE -> new String[]{
                    "    R    ",
                    "   RRR   ",
                    "   RRR   ",
                    "   RRR   ",
                    "  ARCRA  ",
                    "    H    ",
                    "   B B   ",
                    "    B    ",
                    "   B B   "};
        };
    }

    /** The armor shapes (R refined material, A binding): 9, 15, 13 and 8 cores, the binding only at the edges. */
    static String[] shape(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> new String[]{
                    "  ARRRA  ",
                    "  RRARR  ",
                    "  R   R  "};
            case CHESTPLATE -> new String[]{
                    " AR   RA ",
                    " RRR RRR ",
                    "  RRARR  ",
                    "  ARRRA  "};
            case LEGGINGS -> new String[]{
                    "  ARRRA  ",
                    "  RRARR  ",
                    "  RR RR  ",
                    "  RA AR  "};
            default -> new String[]{
                    "  R   R  ",
                    " RR   RR ",
                    " AR   RA "};
        };
    }

    static void build(RecipeOutput out) {
        for (var def : Weapons.legendaries()) {
            Theme t = WEAPONS.get(def.id());
            Item refined = ModItems.REFINED.get(Material.ofWeapon(def.id())).get();
            String[] rows = t.split() ? split(shape(def.weaponClass())) : shape(def.weaponClass());
            Map<Character, ItemLike> key = new LinkedHashMap<>();
            key.put('R', refined);
            key.put('C', t.catalyst());
            key.put('A', t.accent());
            key.put('H', t.handle());
            if (t.second() != null) key.put('B', t.second());
            accept(out, def.id(), rows, key, ModItems.WEAPONS.get(def.id()).get(), weaponTime(def.weaponClass()));
        }
        for (ArmorSet set : ArmorSet.values()) {
            ItemLike[] binding = ARMOR.get(set);
            for (ArmorItem.Type type : ArmorSet.PIECES) {
                String[] rows = binding.length > 1 ? split(shape(type)) : shape(type);
                Map<Character, ItemLike> key = new LinkedHashMap<>();
                key.put('R', ModItems.REFINED.get(set.material()).get());
                key.put('A', binding[0]);
                if (binding.length > 1) key.put('B', binding[1]);
                accept(out, set.itemId(type), rows, key, ModItems.ARMOR.get(set).get(type).get(), armorTime(type));
            }
        }
    }

    /** Accents right of the middle become the second accent. */
    static String[] split(String[] rows) {
        String[] outRows = new String[rows.length];
        for (int r = 0; r < rows.length; r++) {
            char[] cs = rows[r].toCharArray();
            for (int c = cs.length / 2 + 1; c < cs.length; c++) if (cs[c] == 'A') cs[c] = 'B';
            outRows[r] = new String(cs);
        }
        return outRows;
    }

    /** How long a weapon takes at the table, in ticks: the heavier its class, the longer (1.5 to 5 minutes). */
    static int weaponTime(WeaponClass c) {
        int seconds = switch (c) {
            case LONGSWORD, LANCE -> 180;
            case SCYTHE, CHAINBLADE -> 210;
            case BATTLEAXE -> 240;
            case GREATSWORD -> 270;
            case WARHAMMER, COLOSSAL -> 300;
            case STAFF -> 180;
            case TOME -> 150;
        };
        return seconds * 20;
    }

    /** How long an armor piece takes, in ticks: from a minute for boots to two and a half for a chestplate. */
    static int armorTime(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 90;
            case CHESTPLATE -> 150;
            case LEGGINGS -> 120;
            default -> 60;
        } * 20;
    }

    private static void accept(RecipeOutput out, String name, String[] rows, Map<Character, ItemLike> key, ItemLike result, int time) {
        Map<Character, Ingredient> used = new LinkedHashMap<>();
        String all = String.join("", rows);
        key.forEach((ch, item) -> {
            if (all.indexOf(ch) >= 0) used.put(ch, Ingredient.of(item));
        });
        List<String> pattern = new ArrayList<>(List.of(rows));
        out.accept(FantasyWeapons.id("forging/" + name), new ForgeRecipe(ShapedRecipePattern.of(used, pattern), new ItemStack(result), time), null);
    }
}
