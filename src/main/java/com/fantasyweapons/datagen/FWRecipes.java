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
        // the forge: a crafting table on an obsidian base, with an iron frame, gold fittings and a diamond
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.LEGENDARY_FORGE.get())
                .pattern("GDG").pattern("ICI").pattern("OOO")
                .define('G', Items.GOLD_INGOT).define('D', Items.DIAMOND).define('I', Items.IRON_BLOCK)
                .define('C', Items.CRAFTING_TABLE).define('O', Items.OBSIDIAN)
                .unlockedBy("has_diamond", has(Items.DIAMOND))
                .save(out);
        // the legendary weapons and armor are forged on its 9x9 grid
        ForgeRecipes.build(out);
        // infusion: a weapon and refined cores of its material, one level per core
        net.minecraft.data.recipes.SpecialRecipeBuilder.special(com.fantasyweapons.material.InfusionRecipe::new)
                .save(out, FantasyWeapons.id("weapon_infusion"));
        // raw -> refined, one per material
        for (Material m : Material.values()) {
            out.accept(FantasyWeapons.id("refining/" + m.refinedId()),
                    new RefiningRecipe(Ingredient.of(ModItems.RAW.get(m).get()), new ItemStack(ModItems.REFINED.get(m).get()), RefiningRecipe.DEFAULT_TIME),
                    null);
        }
    }
}
