package com.aigirlfriend.companion;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Gift categories, defined by item tags in data/aigirlfriend/tags/item/gifts/ so packs can extend them. */
public enum GiftCategory {
    FLOWERS("flowers"),
    SWEETS("sweets"),
    FOOD("food"),
    RARE_MINERALS("rare_minerals"),
    DECORATIVE("decorative"),
    ADVENTURE("adventure"),
    CUTE("cute"),
    MUSIC("music");

    public final String id;
    public final TagKey<Item> tag;

    GiftCategory(String id) {
        this.id = id;
        this.tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "gifts/" + id));
    }

    public String key() {
        return "aigirlfriend.gift." + id;
    }

    public String word() {
        return switch (this) {
            case FLOWERS -> "flowers";
            case SWEETS -> "sweets";
            case FOOD -> "good food";
            case RARE_MINERALS -> "shiny gems";
            case DECORATIVE -> "pretty decorations";
            case ADVENTURE -> "adventure gear";
            case CUTE -> "cute things";
            case MUSIC -> "music";
        };
    }

    /** Most specific category an item belongs to, or null if it is not a gift at all. */
    @Nullable
    public static GiftCategory of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (GiftCategory c : values()) {
            if (c != FOOD && stack.is(c.tag)) return c;
        }
        if (stack.is(ItemTags.FLOWERS)) return FLOWERS;
        if (stack.is(FOOD.tag) || stack.getFoodProperties(null) != null) return FOOD;
        return null;
    }

    public static GiftCategory byId(int id) {
        GiftCategory[] v = values();
        return id >= 0 && id < v.length ? v[id] : FLOWERS;
    }
}
