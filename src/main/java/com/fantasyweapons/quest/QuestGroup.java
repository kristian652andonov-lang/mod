package com.fantasyweapons.quest;

import com.fantasyweapons.text.Tr;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Quests kept together under one node of the journal's map (a weapon's three, an ore's two), opened with a click. */
public record QuestGroup(String id, String title, Supplier<ItemStack> iconOf) {
    public ItemStack icon() {
        return iconOf.get();
    }

    /** The group's title in the player's language. */
    @Override
    public String title() {
        return Tr.s(title);
    }
}
