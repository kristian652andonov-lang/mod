package com.aigirlfriend.registry;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.item.GirlfriendSpawnEggItem;
import com.aigirlfriend.item.KeepsakeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AIGirlfriend.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AIGirlfriend.MOD_ID);

    public static final DeferredItem<GirlfriendSpawnEggItem> GIRLFRIEND_SPAWN_EGG = ITEMS.register("girlfriend_spawn_egg",
            () -> new GirlfriendSpawnEggItem(ModEntities.GIRLFRIEND, 0xF6C6D8, 0x8A5A44, new Item.Properties()));

    // romance: the ring proposes, everything else is a gift she adores (see tags/item/special_gifts.json)
    public static final DeferredItem<KeepsakeItem> WEDDING_RING = keepsake("wedding_ring", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<KeepsakeItem> RED_ROSE = keepsake("red_rose", new Item.Properties());
    public static final DeferredItem<KeepsakeItem> ROSE_BOUQUET = keepsake("rose_bouquet", gift(Rarity.UNCOMMON));
    public static final DeferredItem<KeepsakeItem> FLOWER_CROWN = keepsake("flower_crown", gift(Rarity.UNCOMMON));
    public static final DeferredItem<KeepsakeItem> BOX_OF_CHOCOLATES = keepsake("box_of_chocolates", gift(Rarity.UNCOMMON)
            .food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6f).build()));
    public static final DeferredItem<KeepsakeItem> TEDDY_BEAR = keepsake("teddy_bear", gift(Rarity.UNCOMMON));
    public static final DeferredItem<KeepsakeItem> LOVE_LETTER = keepsake("love_letter", gift(Rarity.UNCOMMON));
    public static final DeferredItem<KeepsakeItem> HEART_NECKLACE = keepsake("heart_necklace", gift(Rarity.RARE));
    public static final DeferredItem<KeepsakeItem> GIFT_BOX = keepsake("gift_box", gift(Rarity.UNCOMMON));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.aigirlfriend"))
            .icon(() -> new ItemStack(WEDDING_RING.get()))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .displayItems((params, out) -> {
                out.accept(GIRLFRIEND_SPAWN_EGG.get());
                for (DeferredItem<KeepsakeItem> item : romanceItems()) out.accept(item.get());
            })
            .build());

    private static DeferredItem<KeepsakeItem> keepsake(String name, Item.Properties props) {
        return ITEMS.register(name, () -> new KeepsakeItem(props));
    }

    private static Item.Properties gift(Rarity rarity) {
        return new Item.Properties().stacksTo(16).rarity(rarity);
    }

    public static List<DeferredItem<KeepsakeItem>> romanceItems() {
        return List.of(WEDDING_RING, RED_ROSE, ROSE_BOUQUET, FLOWER_CROWN, BOX_OF_CHOCOLATES, TEDDY_BEAR, LOVE_LETTER, HEART_NECKLACE, GIFT_BOX);
    }

    public static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(GIRLFRIEND_SPAWN_EGG.get());
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) event.accept(BOX_OF_CHOCOLATES.get());
    }

    private ModItems() {}
}
