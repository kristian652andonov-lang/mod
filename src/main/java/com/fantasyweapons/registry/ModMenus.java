package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.refinery.RefineryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(Registries.MENU, FantasyWeapons.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<RefineryMenu>> REFINERY = REGISTER.register("arcane_refinery",
            () -> IMenuTypeExtension.create(RefineryMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<com.fantasyweapons.forge.LegendaryForgeMenu>> FORGE = REGISTER.register("legendary_forge",
            () -> new MenuType<>(com.fantasyweapons.forge.LegendaryForgeMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {
    }
}
