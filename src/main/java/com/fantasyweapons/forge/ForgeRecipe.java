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
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/**
 * A Legendary Crafting Table recipe ({@code fantasyweapons:forging}): a shaped pattern up to 9x9, laid out anywhere on the table's
 * grid (mirrored too), making {@code result} after {@code time} ticks at the table. The legendary weapons and armor are
 * forged this way.
 */
public record ForgeRecipe(ShapedRecipePattern pattern, ItemStack result, int time) implements Recipe<CraftingInput> {
    /** The table's grid. */
    public static final int SIZE = 9;
    /** How long forging takes when a recipe doesn't say: two minutes. */
    public static final int DEFAULT_TIME = 2400;

    public ForgeRecipe(ShapedRecipePattern pattern, ItemStack result) {
        this(pattern, result, DEFAULT_TIME);
    }

    /**
     * Every forge recipe in one order both sides agree on (by id): a recipe is named across the network by its place in
     * this list.
     */
    public static java.util.List<net.minecraft.world.item.crafting.RecipeHolder<ForgeRecipe>> ordered(Level level) {
        var all = new java.util.ArrayList<>(level.getRecipeManager().getAllRecipesFor(ModRecipes.FORGING.get()));
        all.sort(java.util.Comparator.comparing(h -> h.id().toString()));
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
        return new ItemStack(ModItems.LEGENDARY_CRAFTING_TABLE.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.FORGING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.FORGING.get();
    }

    public static final class Serializer implements RecipeSerializer<ForgeRecipe> {
        private static final MapCodec<ForgeRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ShapedRecipePattern.MAP_CODEC.forGetter(ForgeRecipe::pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ForgeRecipe::result),
                com.mojang.serialization.Codec.intRange(1, 72000).optionalFieldOf("forging_time", DEFAULT_TIME).forGetter(ForgeRecipe::time)
        ).apply(i, ForgeRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, ForgeRecipe> STREAM_CODEC = StreamCodec.composite(
                ShapedRecipePattern.STREAM_CODEC, ForgeRecipe::pattern,
                ItemStack.STREAM_CODEC, ForgeRecipe::result,
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT, ForgeRecipe::time,
                ForgeRecipe::new);

        @Override
        public MapCodec<ForgeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ForgeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
