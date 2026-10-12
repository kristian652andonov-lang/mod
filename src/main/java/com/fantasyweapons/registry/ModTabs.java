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

    /** The legendary materials (ore, raw, refined) and everything made of them. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COLLECTION = REGISTER.register("collection", () -> CreativeModeTab.builder()
            .title(Component.translatableWithFallback("itemGroup.fantasyweapons.collection", "Fantasy Weapons: Collection"))
            .icon(() -> new ItemStack(ModItems.REFINED.get(com.fantasyweapons.material.Material.VOID_CRYSTAL).get()))
            .withTabsBefore(WEAPONS.getId())
            .displayItems((params, output) -> {
                output.accept(ModItems.ARCANE_REFINERY.get());
                for (var m : com.fantasyweapons.material.Material.values()) {
                    output.accept(ModItems.ORES.get(m).get());
                    output.accept(ModItems.RAW.get(m).get());
                    output.accept(ModItems.REFINED.get(m).get());
                }
                for (var set : com.fantasyweapons.armor.ArmorSet.values()) {
                    for (var type : com.fantasyweapons.armor.ArmorSet.PIECES) output.accept(ModItems.ARMOR.get(set).get(type).get());
                }
            })
            .build());

    private ModTabs() {
    }
}
