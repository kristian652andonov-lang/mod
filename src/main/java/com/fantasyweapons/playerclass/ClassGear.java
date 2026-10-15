package com.fantasyweapons.playerclass;

import com.fantasyweapons.armor.ArmorClass;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.armor.FantasyArmorItem;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which class each legendary weapon and armor set answers to: the class of the set's armor (assassin, paladin or
 * berserker), and a weapon that of its own set. Anyone may forge them; only their class can wield or wear them. Players
 * in creative mode, and players tagged {@value #ANY_CLASS} by an operator, may use them all.
 */
public final class ClassGear {
    /** A player tag letting its bearer use the gear of every class (for operators, and the game's tests). */
    public static final String ANY_CLASS = "fantasyweapons.anyclass";
    /** When each player was last told they cannot use something (game time), so it is said once, not every swing. */
    private static final Map<UUID, Long> lastTold = new HashMap<>();

    private ClassGear() {
    }

    public static PlayerClass of(ArmorClass c) {
        return switch (c) {
            case ASSASSIN -> PlayerClass.ASSASSIN;
            case PALADIN -> PlayerClass.PALADIN;
            case BERSERKER -> PlayerClass.BERSERKER;
        };
    }

    public static PlayerClass ofWeapon(String weaponId) {
        var def = com.fantasyweapons.weapon.Weapons.get(weaponId);
        if (def != null && def.weaponClass().magic()) return PlayerClass.MAGE;
        ArmorSet set = ArmorSet.ofWeapon(weaponId);
        return set == null ? PlayerClass.NONE : of(set.armorClass());
    }

    /** The class a stack answers to, or {@link PlayerClass#NONE} for anything anyone may use. */
    public static PlayerClass of(ItemStack stack) {
        if (stack.getItem() instanceof FantasyWeaponItem weapon) return ofWeapon(weapon.definition().id());
        if (stack.getItem() instanceof FantasyArmorItem armor) return of(armor.set().armorClass());
        return PlayerClass.NONE;
    }

    public static boolean canUse(Player player, ItemStack stack) {
        PlayerClass need = of(stack);
        return need == PlayerClass.NONE || player.isCreative() || player.getTags().contains(ANY_CLASS) || ClassService.of(player) == need;
    }

    /** What the player is told when they try (in the gear's class colour). */
    public static Component refusal(Player player, ItemStack stack) {
        PlayerClass need = of(stack);
        String article = need == PlayerClass.ASSASSIN ? "an " : "a ";
        Component name = Component.literal(need.displayName()).withColor(need.color());
        if (ClassService.of(player) == PlayerClass.NONE) {
            return Component.literal("The " + stack.getHoverName().getString() + " answers only to " + article).withColor(0xE8DCC0).append(name)
                    .append(Component.literal(". Choose a class first.").withColor(0xE8DCC0));
        }
        return Component.literal("Only " + article).withColor(0xFF8A6A).append(name)
                .append(Component.literal(" can use the " + stack.getHoverName().getString() + ".").withColor(0xFF8A6A));
    }

    /** Tells the player they cannot use the stack (at most once a second). */
    public static void refuse(ServerPlayer player, ItemStack stack) {
        long now = player.level().getGameTime();
        Long last = lastTold.get(player.getUUID());
        if (last != null && now - last < 20) return;
        lastTold.put(player.getUUID(), now);
        player.displayClientMessage(refusal(player, stack), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.4f, 1.6f);
    }

    /** Takes off any armor the player's class cannot wear (back into their inventory, or dropped when it is full). */
    public static void stripArmor(ServerPlayer player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = player.getItemBySlot(slot);
            if (worn.isEmpty() || canUse(player, worn)) continue;
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(worn)) player.drop(worn, false);
            refuse(player, worn);
        }
    }

    /** The tooltip's lines: the gear's class, and whether the player reading it can use it. */
    public static void tooltip(@org.jetbrains.annotations.Nullable Player player, ItemStack stack, java.util.List<Component> tooltip) {
        PlayerClass need = of(stack);
        if (need == PlayerClass.NONE) return;
        tooltip.add(Component.literal("Class: ").withColor(0xB9AC92).append(Component.literal(need.displayName()).withColor(need.color())));
        if (player == null) return;
        if (canUse(player, stack)) {
            tooltip.add(Component.literal("✔ You can use this").withColor(0x8FE08A));
        } else if (ClassService.of(player) == PlayerClass.NONE) {
            tooltip.add(Component.literal("✖ Choose the " + need.displayName() + " class to use this").withColor(0xF07A62));
        } else {
            tooltip.add(Component.literal("✖ Your class (" + ClassService.of(player).displayName() + ") cannot use this").withColor(0xF07A62));
        }
    }

    public static void forget(UUID player) {
        lastTold.remove(player);
    }
}
