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
        // armor: the usual shapes, all of the set's refined material (helmet 5, chestplate 8, leggings 7, boots 4)
        for (var set : com.fantasyweapons.armor.ArmorSet.values()) {
            var refined = ModItems.REFINED.get(set.material()).get();
            for (var type : com.fantasyweapons.armor.ArmorSet.PIECES) {
                var b = ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.ARMOR.get(set).get(type).get());
                switch (type) {
                    case HELMET -> b.pattern("RRR").pattern("R R");
                    case CHESTPLATE -> b.pattern("R R").pattern("RRR").pattern("RRR");
                    case LEGGINGS -> b.pattern("RRR").pattern("R R").pattern("R R");
                    default -> b.pattern("R R").pattern("R R");
                }
                b.define('R', refined).unlockedBy("has_" + set.material().refinedId(), has(refined)).save(out);
            }
        }
        // weapons: the weapon's refined material shaped like the weapon, a netherite ingot at its heart and blaze-rod hafts
        for (var def : com.fantasyweapons.weapon.Weapons.all()) {
            var m = Material.ofWeapon(def.id());
            var refined = ModItems.REFINED.get(m).get();
            var b = ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.WEAPONS.get(def.id()).get());
            switch (def.weaponClass()) {
                case LONGSWORD -> b.pattern(" R ").pattern("RNR").pattern(" B ");
                case GREATSWORD -> b.pattern("RRR").pattern("RNR").pattern(" B ");
                case BATTLEAXE -> b.pattern("RR ").pattern("RN ").pattern(" B ");
                case SCYTHE -> b.pattern("RRR").pattern(" RN").pattern(" B ");
                case WARHAMMER -> b.pattern("RNR").pattern("RRR").pattern(" B ");
                case LANCE -> b.pattern(" RR").pattern(" NR").pattern("B  ");
                case COLOSSAL -> b.pattern("RRR").pattern("RNR").pattern("RBR");
                case CHAINBLADE -> b.pattern("RCR").pattern("CNC").pattern("RBR").define('C', Items.CHAIN);
            }
            b.define('R', refined).define('N', Items.NETHERITE_INGOT).define('B', Items.BLAZE_ROD)
                    .unlockedBy("has_" + m.refinedId(), has(refined)).save(out);
        }
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
