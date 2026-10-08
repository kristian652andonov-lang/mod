package com.fantasyweapons.combat;

import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.weapon.WeaponDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Information about a landed melee hit, handed to weapon-specific on-hit hooks (passives).
 */
public record MeleeContext(ServerPlayer player, ItemStack stack, WeaponDefinition weapon, WeaponData data,
                           LivingEntity target, float damageDealt, boolean heavy, boolean crit, boolean primary) {
}
