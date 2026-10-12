package com.fantasyweapons.refinery;

import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * An Arcane Refinery recipe ({@code fantasyweapons:refining}): one {@code ingredient} becomes {@code result} after
 * {@code time} ticks of fire (blaze rods burn it twice as fast).
 */
public record RefiningRecipe(Ingredient ingredient, ItemStack result, int time) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 400;

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public net.minecraft.core.NonNullList<Ingredient> getIngredients() {
        return net.minecraft.core.NonNullList.of(Ingredient.EMPTY, ingredient);
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ModItems.ARCANE_REFINERY.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.REFINING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.REFINING.get();
    }

    public static final class Serializer implements RecipeSerializer<RefiningRecipe> {
        private static final MapCodec<RefiningRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(RefiningRecipe::ingredient),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(RefiningRecipe::result),
                Codec.INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(RefiningRecipe::time)
        ).apply(i, RefiningRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, RefiningRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, RefiningRecipe::ingredient,
                ItemStack.STREAM_CODEC, RefiningRecipe::result,
                ByteBufCodecs.VAR_INT, RefiningRecipe::time,
                RefiningRecipe::new);

        @Override
        public MapCodec<RefiningRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RefiningRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
