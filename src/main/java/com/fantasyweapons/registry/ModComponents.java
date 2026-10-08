package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.progression.WeaponData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FantasyWeapons.MOD_ID);

    /** Per-weapon progression. Persistent (saved with the item) and network-synchronised. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WeaponData>> WEAPON_DATA =
            REGISTER.registerComponentType("weapon_data", b -> b.persistent(WeaponData.CODEC).networkSynchronized(WeaponData.STREAM_CODEC));

    private ModComponents() {
    }
}
