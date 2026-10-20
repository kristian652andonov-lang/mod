package com.fantasyweapons.compat.bettercombat;

import net.bettercombat.api.CombatFlags;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The calls into Better Combat itself; only loaded through {@link BetterCombatCompat} once Better Combat is known to be installed. */
final class BetterCombatHooks {
    private BetterCombatHooks() {
    }

    static boolean drivesSwing(Player player, ItemStack stack) {
        if (CombatFlags.isAttackDisabled(player)) return false;
        WeaponAttributes attributes = WeaponRegistry.getAttributes(stack);
        return attributes != null && attributes.attacks() != null && attributes.attacks().length > 0;
    }

    static void setAttacksDisabled(ServerPlayer player, boolean disabled) {
        CombatFlags.setAttacksDisabled(player, disabled);
    }
}
