package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.refinery.RefiningRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, FantasyWeapons.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, FantasyWeapons.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<RefiningRecipe>> REFINING = TYPES.register("refining",
            () -> RecipeType.simple(FantasyWeapons.id("refining")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RefiningRecipe>> REFINING_SERIALIZER = SERIALIZERS.register("refining",
            RefiningRecipe.Serializer::new);

    /** The Legendary Crafting Table's shaped 9x9 recipes. */
    public static final DeferredHolder<RecipeType<?>, RecipeType<com.fantasyweapons.forge.ForgeRecipe>> FORGING = TYPES.register("forging",
            () -> RecipeType.simple(FantasyWeapons.id("forging")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<com.fantasyweapons.forge.ForgeRecipe>> FORGING_SERIALIZER =
            SERIALIZERS.register("forging", com.fantasyweapons.forge.ForgeRecipe.Serializer::new);

    /** The Worldforge's shaped 11x11 recipes: the two ultimate weapons. */
    public static final DeferredHolder<RecipeType<?>, RecipeType<com.fantasyweapons.forge.WorldforgeRecipe>> WORLDFORGING = TYPES.register("worldforging",
            () -> RecipeType.simple(FantasyWeapons.id("worldforging")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<com.fantasyweapons.forge.WorldforgeRecipe>> WORLDFORGING_SERIALIZER =
            SERIALIZERS.register("worldforging", com.fantasyweapons.forge.WorldforgeRecipe.Serializer::new);

    /** A weapon crafted with refined cores of its material: one level higher for each. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<com.fantasyweapons.material.InfusionRecipe>> INFUSION_SERIALIZER =
            SERIALIZERS.register("weapon_infusion",
                    () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(com.fantasyweapons.material.InfusionRecipe::new));

    private ModRecipes() {
    }
}
