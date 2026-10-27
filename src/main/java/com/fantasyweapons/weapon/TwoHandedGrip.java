package com.fantasyweapons.weapon;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The two-handed weapons (every class but the longswords, the chainblade and the magic foci) are held in both hands, so
 * they never go into the off hand: the swap-hands key leaves one in the main hand and says why, and one put there some
 * other way (dragged into the off-hand slot) goes back to the main hand, or into the inventory. In the off hand one could
 * neither strike nor cast, and with Better Combat installed it is hidden from the game altogether (not drawn, no off-hand
 * slot on the hotbar), so a weapon swapped there by accident seemed to be gone.
 * <p>
 * The off hand is read straight from the inventory: Better Combat answers "empty" for a two-handed weapon there.
 */
public final class TwoHandedGrip {
    /** When each player was last told (game time), so a held swap key does not repeat it every tick. */
    private static final Map<Player, Long> TOLD = new WeakHashMap<>();

    private TwoHandedGrip() {
    }

    public static boolean isTwoHanded(ItemStack stack) {
        return stack.getItem() instanceof FantasyWeaponItem item && item.definition().weaponClass().twoHanded();
    }

    /** The swap-hands key: a two-handed weapon stays in the main hand. */
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack toOffHand = event.getItemSwappedToOffHand();
        if (isTwoHanded(toOffHand)) {
            event.setCanceled(true);
            if (player instanceof ServerPlayer sp) tell(sp, toOffHand);
            return;
        }
        // something hidden in the off hand (a two-handed weapon, to Better Combat) comes back to the main hand, rather
        // than being swapped for nothing and lost
        ItemStack held = player.getInventory().offhand.get(0);
        if (!held.isEmpty() && event.getItemSwappedToMainHand().isEmpty()) event.setItemSwappedToMainHand(held);
    }

    /** Every server tick: a two-handed weapon in the off hand goes to the main hand if it is free, else into the inventory. */
    public static void tick(ServerPlayer player) {
        var inventory = player.getInventory();
        ItemStack held = inventory.offhand.get(0);
        if (!isTwoHanded(held)) return;
        if (inventory.getSelected().isEmpty()) {
            inventory.setItem(inventory.selected, held);
        } else {
            int free = inventory.getFreeSlot();
            // a full inventory: it stays where it is (still in the off-hand slot of the inventory screen)
            if (free < 0) return;
            inventory.setItem(free, held);
        }
        inventory.offhand.set(0, ItemStack.EMPTY);
        tell(player, held);
    }

    private static void tell(ServerPlayer player, ItemStack weapon) {
        long now = player.level().getGameTime();
        Long last = TOLD.get(player);
        if (last != null && now - last < 20) return;
        TOLD.put(player, now);
        // sent as translatable text, so each player reads it in their own language
        player.displayClientMessage(Component.translatable("The %s is held in both hands: it cannot go in the off hand.",
                weapon.getHoverName().copy().setStyle(Style.EMPTY)).withColor(0xE8DCC0), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.5f, 0.8f);
    }
}
