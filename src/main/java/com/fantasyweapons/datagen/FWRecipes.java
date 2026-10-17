package com.fantasyweapons.datagen;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.refinery.RefiningRecipe;
import com.fantasyweapons.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.concurrent.CompletableFuture;

/** Crafting and refining recipes. */
public class FWRecipes extends RecipeProvider {
    public FWRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        // the refinery: a blast furnace in an iron and copper body, a lapis base and an amethyst focus on top
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ARCANE_REFINERY.get())
                .pattern("IAI").pattern("CBC").pattern("ILI")
                .define('I', Items.IRON_BLOCK).define('A', Items.AMETHYST_SHARD).define('C', Items.COPPER_BLOCK)
                .define('B', Items.BLAST_FURNACE).define('L', Items.LAPIS_BLOCK)
                .unlockedBy("has_blast_furnace", has(Items.BLAST_FURNACE))
                .save(out);
        // the Legendary Crafting Table makes weapons of legend, and it takes a legend to build it: a trophy of each
        // dimension's hardest trial - the totems of raids and mansions, the Wither's star, the Ender Dragon's breath, a
        // trial chamber's heavy core and netherite from ancient debris - laid out as the table itself is built
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.LEGENDARY_CRAFTING_TABLE.get())
                .pattern("ZSZ").pattern("BHB").pattern("N N")
                .define('Z', Items.TOTEM_OF_UNDYING).define('S', Items.NETHER_STAR)
                .define('B', Items.DRAGON_BREATH).define('H', Items.HEAVY_CORE)
                .define('N', Items.NETHERITE_INGOT)
                .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
                .save(out);
        // a lost Codex can be written again: a book, gilded, with an amethyst for its gem
        net.minecraft.data.recipes.ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CODEX.get())
                .requires(Items.BOOK).requires(Items.GOLD_INGOT).requires(Items.AMETHYST_SHARD)
                .unlockedBy("has_book", has(Items.BOOK))
                .save(out);
        // the Worldforge: the Legendary Crafting Table raised up on the netherite of a fortress' worth of debris, a heavy core
        // on either side, the Wither's star over it and the dragon's egg under it (the artist's suggested recipe)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.WORLDFORGE.get())
                .pattern("NSN").pattern("HTH").pattern("NEN")
                .define('N', Items.NETHERITE_BLOCK).define('S', Items.NETHER_STAR).define('H', Items.HEAVY_CORE)
                .define('T', ModItems.LEGENDARY_CRAFTING_TABLE.get()).define('E', Items.DRAGON_EGG)
                .unlockedBy("has_legendary_crafting_table", has(ModItems.LEGENDARY_CRAFTING_TABLE.get()))
                .save(out);
        // the Legendary Upgrading Table: a smithing table between an anvil and a grindstone, copper round it, experience
        // over it and netherite under it (the artist's suggested recipe)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.UPGRADING_TABLE.get())
                .pattern("CEC").pattern("ASG").pattern("NCN")
                .define('C', Items.COPPER_BLOCK).define('E', Items.EXPERIENCE_BOTTLE).define('A', Items.ANVIL)
                .define('S', Items.SMITHING_TABLE).define('G', Items.GRINDSTONE).define('N', Items.NETHERITE_INGOT)
                .unlockedBy("has_smithing_table", has(Items.SMITHING_TABLE))
                .save(out);
        // the legendary weapons, armor and tools are forged on its 9x9 grid
        ForgeRecipes.build(out);
        // and the two ultimate weapons on the Worldforge's 11x11
        WorldforgeRecipes.build(out);
        // infusion: a weapon and refined cores of its material, one level per core
        net.minecraft.data.recipes.SpecialRecipeBuilder.special(com.fantasyweapons.material.InfusionRecipe::new)
                .save(out, FantasyWeapons.id("weapon_infusion"));
        // 8 raw and 8 of its additive -> one refined core, one recipe per material
        for (Material m : Material.values()) {
            out.accept(FantasyWeapons.id("refining/" + m.refinedId()),
                    new RefiningRecipe(Ingredient.of(ModItems.RAW.get(m).get()), Material.REFINE_COUNT, Ingredient.of(m.additive()), Material.REFINE_COUNT,
                            new ItemStack(ModItems.REFINED.get(m).get()), RefiningRecipe.DEFAULT_TIME),
                    null);
        }
    }
}
