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
        // the forge makes weapons of legend, and it takes a legend to build it: a trophy of each dimension's hardest
        // trial - the Wither's star, the Ender Dragon's breath, the totems of raids and mansions, a trial chamber's heavy
        // core and netherite from ancient debris - set around a smithing table
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.LEGENDARY_FORGE.get())
                .pattern("BSB").pattern("NTN").pattern("ZHZ")
                .define('B', Items.DRAGON_BREATH).define('S', Items.NETHER_STAR)
                .define('N', Items.NETHERITE_INGOT).define('T', Items.SMITHING_TABLE)
                .define('Z', Items.TOTEM_OF_UNDYING).define('H', Items.HEAVY_CORE)
                .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
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
