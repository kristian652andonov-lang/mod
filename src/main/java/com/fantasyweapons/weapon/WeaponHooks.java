package com.fantasyweapons.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Optional per-weapon server hooks that don't belong on every definition (e.g. reacting to a form switch). */
public final class WeaponHooks {
    public interface FormSwitch {
        void onSwitch(ServerPlayer player, ItemStack stack, WeaponForm newForm);
    }

    private static final Map<String, FormSwitch> FORM_SWITCH = new HashMap<>();

    private WeaponHooks() {
    }

    public static void onFormSwitch(String weaponId, FormSwitch hook) {
        FORM_SWITCH.put(weaponId, hook);
    }

    public static void fireFormSwitch(String weaponId, ServerPlayer player, ItemStack stack, WeaponForm newForm) {
        FormSwitch hook = FORM_SWITCH.get(weaponId);
        if (hook != null) hook.onSwitch(player, stack, newForm);
    }
}
