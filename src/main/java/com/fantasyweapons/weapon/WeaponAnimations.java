package com.fantasyweapons.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;

/**
 * Server-side animation triggers. GeckoLib forwards them to every client tracking the player (and the player),
 * so everyone sees the same weapon animation.
 */
public final class WeaponAnimations {
    private WeaponAnimations() {
    }

    public static void trigger(ServerPlayer player, ItemStack stack, String trigger) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return;
        long id = GeoItem.getOrAssignId(stack, player.serverLevel());
        item.triggerAnim(player, id, FantasyWeaponItem.CONTROLLER, trigger);
    }

    public static void stop(ServerPlayer player, ItemStack stack, String trigger) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return;
        long id = GeoItem.getOrAssignId(stack, player.serverLevel());
        item.stopTriggeredAnim(player, id, FantasyWeaponItem.CONTROLLER, trigger);
    }
}
