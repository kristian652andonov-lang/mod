package com.fantasyweapons.forge;

import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.registry.ModRecipes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A Worldforge recipe ({@code fantasyweapons:worldforging}): a shaped pattern up to 11x11 laid on the Worldforge's grid,
 * forged into {@code result} over {@code time} ticks. The two ultimate weapons are made this way.
 */
public record WorldforgeRecipe(ShapedRecipePattern pattern, ItemStack result, int time) implements Recipe<CraftingInput> {
    /** The Worldforge's grid. */
    public static final int SIZE = 11;
    /** How long forging takes when a recipe doesn't say: ten minutes. */
    public static final int DEFAULT_TIME = 12000;

    /** Every Worldforge recipe in one order both sides agree on (by id): a recipe is named across the network by its place here. */
    public static List<RecipeHolder<WorldforgeRecipe>> ordered(Level level) {
        var all = new ArrayList<>(level.getRecipeManager().getAllRecipesFor(ModRecipes.WORLDFORGING.get()));
        all.sort(Comparator.comparing(h -> h.id().toString()));
        return all;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return pattern.matches(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= pattern.width() && height >= pattern.height();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return pattern.ingredients();
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ModItems.WORLDFORGE.get());
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.WORLDFORGING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.WORLDFORGING.get();
    }

    public static final class Serializer implements RecipeSerializer<WorldforgeRecipe> {
        private static final MapCodec<WorldforgeRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ShapedRecipePattern.MAP_CODEC.forGetter(WorldforgeRecipe::pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(WorldforgeRecipe::result),
                com.mojang.serialization.Codec.intRange(1, 144000).optionalFieldOf("forging_time", DEFAULT_TIME).forGetter(WorldforgeRecipe::time)
        ).apply(i, WorldforgeRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, WorldforgeRecipe> STREAM_CODEC = StreamCodec.composite(
                ShapedRecipePattern.STREAM_CODEC, WorldforgeRecipe::pattern,
                ItemStack.STREAM_CODEC, WorldforgeRecipe::result,
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT, WorldforgeRecipe::time,
                WorldforgeRecipe::new);

        @Override
        public MapCodec<WorldforgeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, WorldforgeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
