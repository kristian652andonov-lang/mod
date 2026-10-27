package com.fantasyweapons.client.fx;

import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Colour theme lookup for effects (follows the weapon's element and current form/mode). */
public final class Themes {
    public record Theme(int primary, int secondary, int light) {
    }

    public static final Theme NEUTRAL = new Theme(0xC9B8FF, 0x7A5CFF, 0xFFFFFF);

    private Themes() {
    }

    public static Theme of(ItemStack stack) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return NEUTRAL;
        WeaponDefinition def = item.definition();
        WeaponData data = FantasyWeaponItem.data(stack);
        return new Theme(def.themePrimary(data), def.themeSecondary(data), def.element().light());
    }

    public static Theme ofEntity(int entityId) {
        var level = Minecraft.getInstance().level;
        if (level == null) return NEUTRAL;
        Entity e = level.getEntity(entityId);
        return e instanceof LivingEntity le ? of(le.getMainHandItem()) : NEUTRAL;
    }
}
