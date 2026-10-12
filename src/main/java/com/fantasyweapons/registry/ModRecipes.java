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

    private ModRecipes() {
    }
}
