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
 * A Legendary Forge recipe ({@code fantasyweapons:forging}): a shaped pattern up to 9x9, laid out anywhere on the forge's
 * grid (mirrored too), making {@code result}. The legendary weapons and armor are forged this way.
 */
public record ForgeRecipe(ShapedRecipePattern pattern, ItemStack result) implements Recipe<CraftingInput> {
    /** The forge's grid. */
    public static final int SIZE = 9;

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
        return new ItemStack(ModItems.LEGENDARY_FORGE.get());
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
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ForgeRecipe::result)
        ).apply(i, ForgeRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, ForgeRecipe> STREAM_CODEC = StreamCodec.composite(
                ShapedRecipePattern.STREAM_CODEC, ForgeRecipe::pattern,
                ItemStack.STREAM_CODEC, ForgeRecipe::result,
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
