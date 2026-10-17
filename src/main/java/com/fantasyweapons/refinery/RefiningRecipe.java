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
import net.minecraft.world.level.Level;

/**
 * An Arcane Refinery recipe ({@code fantasyweapons:refining}): {@code count} of {@code ingredient}, with
 * {@code additiveCount} of {@code additive} melted in, become {@code result} after {@code time} ticks of fire (blaze
 * rods and lava burn it twice as fast).
 */
public record RefiningRecipe(Ingredient ingredient, int count, Ingredient additive, int additiveCount, ItemStack result, int time)
        implements Recipe<RefiningInput> {
    public static final int DEFAULT_TIME = 400;

    public RefiningRecipe(Ingredient ingredient, ItemStack result, int time) {
        this(ingredient, 1, Ingredient.EMPTY, 0, result, time);
    }

    @Override
    public boolean matches(RefiningInput input, Level level) {
        if (!ingredient.test(input.raw()) || input.raw().getCount() < count) return false;
        return additive.isEmpty() || additive.test(input.additive()) && input.additive().getCount() >= additiveCount;
    }

    @Override
    public ItemStack assemble(RefiningInput input, HolderLookup.Provider registries) {
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
        return additive.isEmpty() ? net.minecraft.core.NonNullList.of(Ingredient.EMPTY, ingredient)
                : net.minecraft.core.NonNullList.of(Ingredient.EMPTY, ingredient, additive);
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
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(RefiningRecipe::count),
                Ingredient.CODEC.optionalFieldOf("additive", Ingredient.EMPTY).forGetter(RefiningRecipe::additive),
                Codec.intRange(0, 64).optionalFieldOf("additive_count", 0).forGetter(RefiningRecipe::additiveCount),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(RefiningRecipe::result),
                Codec.INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(RefiningRecipe::time)
        ).apply(i, RefiningRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, RefiningRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, RefiningRecipe::ingredient,
                ByteBufCodecs.VAR_INT, RefiningRecipe::count,
                Ingredient.CONTENTS_STREAM_CODEC, RefiningRecipe::additive,
                ByteBufCodecs.VAR_INT, RefiningRecipe::additiveCount,
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
