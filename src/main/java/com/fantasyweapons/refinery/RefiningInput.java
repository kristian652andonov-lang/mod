package com.fantasyweapons.refinery;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** What lies in the refinery's crucible: the raw material and the additive melted in with it. */
public record RefiningInput(ItemStack raw, ItemStack additive) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return index == 0 ? raw : additive;
    }

    @Override
    public int size() {
        return 2;
    }
}
