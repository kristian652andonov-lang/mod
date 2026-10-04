package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FantasyWeapons.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WEAPONS = REGISTER.register("weapons", () -> CreativeModeTab.builder()
            .title(Component.translatableWithFallback("itemGroup.fantasyweapons", "Fantasy Weapons"))
            .icon(() -> new ItemStack(ModItems.WEAPONS.get("voidfang").get()))
            .displayItems((params, output) -> ModItems.WEAPONS.values().forEach(item -> output.accept(item.get())))
            .build());

    private ModTabs() {
    }
}
