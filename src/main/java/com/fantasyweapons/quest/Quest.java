package com.fantasyweapons.quest;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * One quest of the journal: what it asks ({@link #objectives}, all of them), what it gives when claimed ({@link #rewards} and
 * {@link #xp} experience points), and the quest that must be claimed before it opens ({@link #requires}).
 */
public record Quest(String id, Chapter chapter, String title, String description, Supplier<ItemStack> iconOf,
                    List<QuestObjective> objectives, List<Supplier<ItemStack>> rewards, int xp, @Nullable String requires) {
    public ItemStack icon() {
        return iconOf.get();
    }
}
