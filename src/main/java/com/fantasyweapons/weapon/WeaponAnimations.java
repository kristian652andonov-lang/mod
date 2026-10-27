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

    /** Plays a trigger, in the version of the form the weapon is in (see FantasyWeaponItem#formTrigger). */
    public static void trigger(ServerPlayer player, ItemStack stack, String trigger) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return;
        long id = GeoItem.getOrAssignId(stack, player.serverLevel());
        WeaponForm form = item.definition().form(FantasyWeaponItem.data(stack));
        item.triggerAnim(player, id, FantasyWeaponItem.CONTROLLER, FantasyWeaponItem.formTrigger(trigger, form));
    }

    /** Stops a trigger in whichever form's version it was played (the form may have changed since). */
    public static void stop(ServerPlayer player, ItemStack stack, String trigger) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return;
        long id = GeoItem.getOrAssignId(stack, player.serverLevel());
        item.stopTriggeredAnim(player, id, FantasyWeaponItem.CONTROLLER, trigger);
        for (WeaponForm form : item.definition().forms()) {
            String own = FantasyWeaponItem.formTrigger(trigger, form);
            if (!own.equals(trigger)) item.stopTriggeredAnim(player, id, FantasyWeaponItem.CONTROLLER, own);
        }
    }
}
