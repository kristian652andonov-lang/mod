package com.fantasyweapons.quest;

import com.fantasyweapons.text.Tr;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * One quest of the journal: what it asks ({@link #objectives}, all of them), what it gives when claimed ({@link #rewards} and
 * {@link #xp} experience points), the quest that must be claimed before it opens ({@link #requires}), and the group it is
 * shown under in the journal, if any.
 */
public record Quest(String id, Chapter chapter, String title, String description, Supplier<ItemStack> iconOf,
                    List<QuestObjective> objectives, List<Supplier<ItemStack>> rewards, int xp, @Nullable String requires,
                    @Nullable QuestGroup group) {
    public ItemStack icon() {
        return iconOf.get();
    }

    /** The quest's title in the player's language. */
    @Override
    public String title() {
        return Tr.s(title);
    }

    @Override
    public String description() {
        return Tr.s(description);
    }
}
