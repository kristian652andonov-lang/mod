package com.fantasyweapons.datagen;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.forge.WorldforgeRecipe;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.weapons.domination.SpellBookOfDomination;
import com.fantasyweapons.weapons.obliteration.GreatSwordOfObliteration;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Worldforge's rites, one for each calamity (the Weapon of God is fused from the two on the Legendary Upgrading
 * Table). Each takes every
 * legendary weapon there is - the thirteen blades (a to m), the six staffs
 * (n to s) and the six spellbooks (t to y) - and a refined core of each of the thirteen materials (A to M), with the
 * hardest trophies of every dimension: nether stars (*), netherite blocks (#), a heavy core (+), totems of undying (!),
 * dragon's breath (&amp;), echo shards (%), end crystals (@), enchanted golden apples ($), wither skeleton skulls (^), a
 * beacon (~) and enchanted books (?). The sword's rite is laid out as a sword, the book's as an open book.
 */
final class WorldforgeRecipes {
    private WorldforgeRecipes() {
    }

    static final String[] SWORD = {
            "*abcdefghi*",
            "j%  #A#  %k",
            "l  #BCD#  m",
            "n  #EFG#  o",
            "p  #HIJ#  q",
            "r  #KLM#  s",
            "t   ###   u",
            "v^##+~+##^w",
            "x  ! # !  y",
            "&    #    &",
            "*@$  #  $@*"};

    static final String[] BOOK = {
            "*abcdefghi*",
            "j?????????k",
            "l?ABC&DEF?m",
            "n?GHI&JKL?o",
            "p?M@ & @$?q",
            "r?????????s",
            "t  %%~%%  u",
            "v  ^ + ^  w",
            "x !  #  ! y",
            "&    #    &",
            "*@$  #  $@*"};

    /** The ultimate weapons take ten minutes to forge. */
    static final int TIME = 12000;

    static void build(RecipeOutput out) {
        accept(out, GreatSwordOfObliteration.ID, SWORD);
        accept(out, SpellBookOfDomination.ID, BOOK);
    }

    static Map<Character, ItemLike> key() {
        Map<Character, ItemLike> key = new LinkedHashMap<>();
        List<String> legends = List.of("voidfang", "solaris", "frostrend", "doomcleaver", "stormbreaker", "gravebite", "soulreaper", "bloomfall",
                "eclipse_reaper", "starforge", "aetherlance", "monolith", "infernochain",
                "cinderspire", "rimeheart", "tidecaller", "thornwake", "sunwarden", "gloamreach",
                "pyre_codex", "frostbound_grimoire", "codex_of_tides", "verdant_almanac", "lumen_psalter", "umbral_codex");
        for (int i = 0; i < legends.size(); i++) key.put((char) ('a' + i), ModItems.WEAPONS.get(legends.get(i)).get());
        Material[] ms = Material.values();
        for (int i = 0; i < ms.length; i++) key.put((char) ('A' + i), ModItems.REFINED.get(ms[i]).get());
        key.put('*', Items.NETHER_STAR);
        key.put('#', Items.NETHERITE_BLOCK);
        key.put('+', Items.HEAVY_CORE);
        key.put('!', Items.TOTEM_OF_UNDYING);
        key.put('&', Items.DRAGON_BREATH);
        key.put('%', Items.ECHO_SHARD);
        key.put('@', Items.END_CRYSTAL);
        key.put('$', Items.ENCHANTED_GOLDEN_APPLE);
        key.put('^', Items.WITHER_SKELETON_SKULL);
        key.put('~', Items.BEACON);
        key.put('?', Items.ENCHANTED_BOOK);
        return key;
    }

    private static void accept(RecipeOutput out, String weapon, String[] rows) {
        int time = TIME;
        Map<Character, Ingredient> used = new LinkedHashMap<>();
        String all = String.join("", rows);
        key().forEach((ch, item) -> {
            if (all.indexOf(ch) >= 0) used.put(ch, Ingredient.of(item));
        });
        out.accept(FantasyWeapons.id("worldforging/" + weapon), new WorldforgeRecipe(ShapedRecipePattern.of(used, List.of(rows)),
                new ItemStack(ModItems.WEAPONS.get(weapon).get()), time), null);
    }
}
