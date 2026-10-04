package com.fantasyweapons.status;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom internal status effects. These are not vanilla MobEffects: no vanilla potion particles, no vanilla icons.
 * They are shown by the custom HUD and visualised by the custom VFX system.
 */
public enum StatusType {
    VOID_MARK("Void Mark", 0x9B4DFF, true, 10),
    SOLAR_BURN("Solar Burn", 0xFFB627, true, 1),
    FROSTBITE("Frostbite", 0x8FE3FF, true, 5),
    FROZEN("Frozen", 0xDDF8FF, true, 1),
    BERSERKER("Berserker", 0xE0213A, false, 1),
    SOUL_DRAIN("Soul Drain", 0x5A8CFF, true, 1),
    NATURE_POISON("Nature Poison", 0x6BE36F, true, 5),
    ECLIPSE_LIGHT("Eclipse Light", 0xFFE7A0, true, 3),
    ECLIPSE_DARKNESS("Eclipse Darkness", 0x8B3DFF, true, 3),
    GRAVITY_BOUND("Gravity Bound", 0x8A6CFF, true, 1),
    INFERNO_OVERHEAT("Inferno Overheat", 0xFF5A1F, false, 5),
    ROOTED("Rooted", 0x4CAF50, true, 1),
    STAGGERED("Staggered", 0xB08A5A, true, 1),
    SEARED("Seared", 0xFF3A10, true, 5);

    private final String displayName;
    private final int color;
    private final boolean harmful;
    private final int maxStacks;

    StatusType(String displayName, int color, boolean harmful, int maxStacks) {
        this.displayName = displayName;
        this.color = color;
        this.harmful = harmful;
        this.maxStacks = maxStacks;
    }

    public String displayName() {
        return displayName;
    }

    public int color() {
        return color;
    }

    public boolean harmful() {
        return harmful;
    }

    public int maxStacks() {
        return maxStacks;
    }

    public ResourceLocation icon() {
        return ResourceLocation.fromNamespaceAndPath(FantasyWeapons.MOD_ID, "textures/gui/status/" + name().toLowerCase() + ".png");
    }
}
