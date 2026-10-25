package com.fantasyweapons.playerclass;

import com.fantasyweapons.text.Tr;
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
            case MAGE -> PlayerClass.MAGE;
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
        if (warriors(stack)) {
            PlayerClass c = ClassService.of(player);
            return player.isCreative() || player.getTags().contains(ANY_CLASS) || isWarrior(c);
        }
        PlayerClass need = of(stack);
        return need == PlayerClass.NONE || player.isCreative() || player.getTags().contains(ANY_CLASS) || ClassService.of(player) == need;
    }

    /** The Great Sword of Obliteration answers to every warrior class (assassin, berserker and paladin), not to one (the Weapon of God answers to all). */
    public static boolean warriors(ItemStack stack) {
        return stack.getItem() instanceof FantasyWeaponItem w && warriors(w.definition().id());
    }

    public static boolean warriors(String weaponId) {
        var def = com.fantasyweapons.weapon.Weapons.get(weaponId);
        return def != null && def.rarity() == com.fantasyweapons.weapon.Rarity.CALAMITY && !def.weaponClass().magic();
    }

    public static boolean isWarrior(PlayerClass c) {
        return c == PlayerClass.ASSASSIN || c == PlayerClass.BERSERKER || c == PlayerClass.PALADIN;
    }

    /** Whether a weapon is gear of class {@code c} (the warriors' sword belongs to all three of them). */
    public static boolean answersTo(String weaponId, PlayerClass c) {
        var def = com.fantasyweapons.weapon.Weapons.get(weaponId);
        if (def != null && def.rarity() == com.fantasyweapons.weapon.Rarity.BRILLIANT_LIGHT) return c != PlayerClass.NONE;
        return warriors(weaponId) ? isWarrior(c) : ofWeapon(weaponId) == c;
    }

    /** What the player is told when they try (in the gear's class colour). */
    public static Component refusal(Player player, ItemStack stack) {
        if (warriors(stack)) {
            // sent as translatable text, so each player reads it in their own language
            return Component.translatable("The %s answers only to a warrior: an %s, %s or %s.", plainName(stack),
                    className(PlayerClass.ASSASSIN), className(PlayerClass.BERSERKER), className(PlayerClass.PALADIN)).withColor(0xFF8A6A);
        }
        PlayerClass need = of(stack);
        boolean an = need == PlayerClass.ASSASSIN;
        if (ClassService.of(player) == PlayerClass.NONE) {
            return Component.translatable(an ? "The %s answers only to an %s. Choose a class first." : "The %s answers only to a %s. Choose a class first.",
                    plainName(stack), className(need)).withColor(0xE8DCC0);
        }
        return Component.translatable(an ? "Only an %s can use the %s." : "Only a %s can use the %s.", className(need), plainName(stack)).withColor(0xFF8A6A);
    }

    /** A class's name in its colour, in the reader's language. */
    private static Component className(PlayerClass c) {
        return Component.translatable(c.englishName()).withColor(c.color());
    }

    /** The stack's name without its own colour, in the reader's language. */
    private static Component plainName(ItemStack stack) {
        return stack.getHoverName().copy().setStyle(net.minecraft.network.chat.Style.EMPTY);
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
        if (warriors(stack)) {
            tooltip.add(Component.literal(Tr.s("Class:") + " ").withColor(0xB9AC92).append(Component.literal(PlayerClass.ASSASSIN.displayName()).withColor(PlayerClass.ASSASSIN.color()))
                    .append(Component.literal(" · ").withColor(0xB9AC92)).append(Component.literal(PlayerClass.BERSERKER.displayName()).withColor(PlayerClass.BERSERKER.color()))
                    .append(Component.literal(" · ").withColor(0xB9AC92)).append(Component.literal(PlayerClass.PALADIN.displayName()).withColor(PlayerClass.PALADIN.color())));
            if (player == null) return;
            tooltip.add(canUse(player, stack) ? Component.literal("✔ " + Tr.s("You can use this")).withColor(0x8FE08A)
                    : Component.literal("✖ " + Tr.s("Only a warrior class can use this")).withColor(0xF07A62));
            return;
        }
        PlayerClass need = of(stack);
        if (need == PlayerClass.NONE) return;
        tooltip.add(Component.literal(Tr.s("Class:") + " ").withColor(0xB9AC92).append(Component.literal(need.displayName()).withColor(need.color())));
        if (player == null) return;
        if (canUse(player, stack)) {
            tooltip.add(Component.literal("✔ " + Tr.s("You can use this")).withColor(0x8FE08A));
        } else if (ClassService.of(player) == PlayerClass.NONE) {
            tooltip.add(Component.literal("✖ " + Tr.f("Choose the %s class to use this", need.displayName())).withColor(0xF07A62));
        } else {
            tooltip.add(Component.literal("✖ " + Tr.f("Your class (%s) cannot use this", ClassService.of(player).displayName())).withColor(0xF07A62));
        }
    }

    public static void forget(UUID player) {
        lastTold.remove(player);
    }
}
