package com.fantasyweapons.quest;

import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Something a quest asks for. Counted objectives are tallied as the player does them (mining, slaying, casting, wearing,
 * travelling: by key, see {@link QuestService#event}); the others are read from what the player has.
 */
public sealed interface QuestObjective {
    /** What it asks, as the journal writes it. */
    String label();

    /** How many it asks for. */
    int target();

    ItemStack icon();

    /** Counted as it happens (kept in the player's quest data) rather than read from the inventory. */
    default boolean counted() {
        return false;
    }

    /** How far the player is with it right now, for objectives read from what they have. */
    default int have(Player player) {
        return 0;
    }

    /** Done {@code target} times something reported by {@code key} (for instance "mine:fantasyweapons:solarite_ore"). */
    record Count(String key, int target, String label, Supplier<ItemStack> iconOf) implements QuestObjective {
        @Override
        public boolean counted() {
            return true;
        }

        @Override
        public ItemStack icon() {
            return iconOf.get();
        }
    }

    /** Holds {@code target} of an item. */
    record Collect(Supplier<? extends net.minecraft.world.level.ItemLike> item, int target, String label) implements QuestObjective {
        @Override
        public ItemStack icon() {
            return new ItemStack(item.get());
        }

        @Override
        public int have(Player player) {
            net.minecraft.world.item.Item it = item.get().asItem();
            int n = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.is(it)) n += s.getCount();
            }
            return n;
        }
    }

    /** Holds {@code target} items that pass {@code test} (any legendary tool, say). */
    record Holds(java.util.function.Predicate<ItemStack> test, int target, String label, Supplier<ItemStack> iconOf) implements QuestObjective {
        @Override
        public ItemStack icon() {
            return iconOf.get();
        }

        @Override
        public int have(Player player) {
            int n = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (test.test(s)) n += s.getCount();
            }
            return n;
        }
    }

    /** Has a legendary weapon (that one, or any when {@code weapon} is null) at level {@code target} or above. */
    record Reach(String weapon, int target, String label, Supplier<ItemStack> iconOf) implements QuestObjective {
        @Override
        public ItemStack icon() {
            return iconOf.get();
        }

        @Override
        public int have(Player player) {
            int best = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!(s.getItem() instanceof FantasyWeaponItem w)) continue;
                if (weapon != null && !w.definition().id().equals(weapon)) continue;
                best = Math.max(best, FantasyWeaponItem.data(s).level());
            }
            return best;
        }
    }
}
