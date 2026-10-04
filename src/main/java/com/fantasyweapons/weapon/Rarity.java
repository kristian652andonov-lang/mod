package com.fantasyweapons.weapon;

/**
 * Custom rarity tiers. Rarity mostly drives presentation; it only nudges base damage slightly so that
 * weapon level stays the dominant source of power.
 */
public enum Rarity {
    RARE("Rare", 0x4FA3FF, 0.90f),
    EPIC("Epic", 0xB45CFF, 0.95f),
    LEGENDARY("Legendary", 0xFFA41C, 1.00f),
    MYTHIC("Mythic", 0xFF3B5C, 1.05f),
    ANCIENT("Ancient", 0x3DFFD0, 1.10f);

    private final String displayName;
    private final int color;
    private final float damageFactor;

    Rarity(String displayName, int color, float damageFactor) {
        this.displayName = displayName;
        this.color = color;
        this.damageFactor = damageFactor;
    }

    public String displayName() {
        return displayName;
    }

    public int color() {
        return color;
    }

    public float damageFactor() {
        return damageFactor;
    }
}
