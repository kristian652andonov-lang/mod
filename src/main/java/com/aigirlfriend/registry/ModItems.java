package com.aigirlfriend.registry;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.item.GirlfriendSpawnEggItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AIGirlfriend.MOD_ID);

    public static final DeferredItem<GirlfriendSpawnEggItem> GIRLFRIEND_SPAWN_EGG = ITEMS.register("girlfriend_spawn_egg",
            () -> new GirlfriendSpawnEggItem(ModEntities.GIRLFRIEND, 0xF6C6D8, 0x8A5A44, new Item.Properties()));

    public static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(GIRLFRIEND_SPAWN_EGG.get());
    }

    private ModItems() {}
}
