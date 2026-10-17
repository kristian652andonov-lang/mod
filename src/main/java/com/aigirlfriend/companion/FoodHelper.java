package com.aigirlfriend.companion;

import com.aigirlfriend.config.GFConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Which items she can eat and drink, driven by item food properties plus config allow/deny lists. */
public final class FoodHelper {
    private FoodHelper() {}

    public static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static boolean isBlacklisted(ItemStack stack) {
        return GFConfig.FOOD_BLACKLIST.get().contains(id(stack));
    }

    public static int extraNutrition(ItemStack stack) {
        String id = id(stack);
        for (String entry : GFConfig.FOOD_EXTRA.get()) {
            int eq = entry.indexOf('=');
            String key = eq > 0 ? entry.substring(0, eq).trim() : entry.trim();
            if (key.equals(id)) {
                try {
                    return eq > 0 ? Integer.parseInt(entry.substring(eq + 1).trim()) : 2;
                } catch (NumberFormatException e) {
                    return 2;
                }
            }
        }
        return -1;
    }

    public static boolean isFood(ItemStack stack, LivingEntity eater) {
        if (stack.isEmpty() || isDrink(stack)) return false;
        return stack.getFoodProperties(eater) != null || extraNutrition(stack) >= 0;
    }

    public static int nutrition(ItemStack stack, LivingEntity eater) {
        int extra = extraNutrition(stack);
        if (extra >= 0) return extra;
        FoodProperties fp = stack.getFoodProperties(eater);
        return fp == null ? 0 : fp.nutrition();
    }

    public static ItemStack leftover(ItemStack stack, LivingEntity eater) {
        FoodProperties fp = stack.getFoodProperties(eater);
        if (fp != null && fp.usingConvertsTo().isPresent()) return fp.usingConvertsTo().get().copy();
        if (stack.is(Items.MUSHROOM_STEW) || stack.is(Items.BEETROOT_SOUP) || stack.is(Items.RABBIT_STEW) || stack.is(Items.SUSPICIOUS_STEW)) return new ItemStack(Items.BOWL);
        return ItemStack.EMPTY;
    }

    public static boolean isDrink(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.POTION)) return true;
        return GFConfig.DRINKS.get().contains(id(stack));
    }
}
