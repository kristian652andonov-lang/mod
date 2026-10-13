package com.fantasyweapons.forge;

import com.fantasyweapons.armor.FantasyArmorItem;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponClass;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the Legendary Crafting Table is doing at each stage of a forging, as the table's screen tells it: a weapon's
 * eight stages (its cores laid out and heated, its blade drawn and folded, its handle, its catalyst set, quenched and
 * woken) and an armor piece's six.
 */
public final class ForgeStages {
    private ForgeStages() {
    }

    /** How many stages forging {@code made} goes through. */
    public static int count(ItemStack made) {
        return made.getItem() instanceof FantasyArmorItem ? 6 : 8;
    }

    /** The line for stage {@code stage} of forging {@code made} from {@code recipe} (when it is known). */
    public static String line(ItemStack made, @Nullable ForgeRecipe recipe, int stage) {
        String name = made.getHoverName().getString();
        String core = "cores", catalyst = "catalyst";
        if (recipe != null) {
            Item[] parts = parts(recipe);
            if (parts[0] != null) core = new ItemStack(parts[0]).getHoverName().getString() + "s";
            if (parts[1] != null) catalyst = new ItemStack(parts[1]).getHoverName().getString();
        }
        if (made.getItem() instanceof FantasyArmorItem armor) {
            String piece = switch (armor.getType()) {
                case HELMET -> "helm";
                case CHESTPLATE -> "breastplate";
                case LEGGINGS -> "greaves";
                default -> "boots";
            };
            String set = armor.set().displayName();
            return switch (stage) {
                case 0 -> "Measuring the " + piece;
                case 1 -> "Heating the " + core;
                case 2 -> "Hammering out the plates";
                case 3 -> "Riveting the bindings";
                case 4 -> "Etching the runes of " + set;
                default -> "Polishing the " + piece;
            };
        }
        String blade = made.getItem() instanceof FantasyWeaponItem w ? blade(w.definition().weaponClass()) : "blade";
        return switch (stage) {
            case 0 -> "Laying out the " + core;
            case 1 -> "Heating the " + core + " in the forge-light";
            case 2 -> "Drawing out the " + blade;
            case 3 -> "Folding the " + blade + " a hundred times";
            case 4 -> "Forging the handle";
            case 5 -> "Setting the " + catalyst + " at its heart";
            case 6 -> "Quenching " + name;
            default -> "Waking " + name + "...";
        };
    }

    private static String blade(WeaponClass c) {
        return switch (c) {
            case BATTLEAXE -> "axe head";
            case SCYTHE -> "crescent blade";
            case WARHAMMER -> "hammer head";
            case LANCE -> "lance head";
            case COLOSSAL -> "great blade";
            default -> "blade";
        };
    }

    /** A recipe's refined core (its most used legendary material) and its catalyst (an item it takes only once). */
    private static Item[] parts(ForgeRecipe recipe) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (Ingredient ing : recipe.pattern().ingredients()) {
            ItemStack[] items = ing.getItems();
            if (items.length > 0) counts.merge(items[0].getItem(), 1, Integer::sum);
        }
        Item core = null, catalyst = null;
        for (var e : counts.entrySet()) {
            if (isRefined(e.getKey()) && (core == null || e.getValue() > counts.get(core))) core = e.getKey();
        }
        for (var e : counts.entrySet()) if (e.getValue() == 1 && e.getKey() != core) catalyst = e.getKey();
        return new Item[]{core, catalyst};
    }

    private static boolean isRefined(Item item) {
        for (Material m : Material.values()) if (com.fantasyweapons.registry.ModItems.REFINED.get(m).get() == item) return true;
        return false;
    }

    /** How long is left, as minutes and seconds. */
    public static String remaining(int progress, int total) {
        int seconds = Math.max(0, (total - progress + 19) / 20);
        return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }
}
