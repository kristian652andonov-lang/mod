package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(FantasyWeapons.MOD_ID);

    public static final Map<String, DeferredItem<FantasyWeaponItem>> WEAPONS = new LinkedHashMap<>();

    static {
        for (WeaponDefinition def : Weapons.all()) {
            WEAPONS.put(def.id(), REGISTER.register(def.id(),
                    () -> new FantasyWeaponItem(def, new Item.Properties().stacksTo(1).fireResistant())));
        }
    }

    private ModItems() {
    }
}
