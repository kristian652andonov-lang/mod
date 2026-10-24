package com.fantasyweapons.weapon;

/**
 * Custom rarity tiers. Rarity mostly drives presentation; it only nudges base damage slightly so that
 * weapon level stays the dominant source of power. The two highest are classes of their own, named on a line of their
 * own in the tooltip.
 */
public enum Rarity {
    RARE("Rare", "Rare", 0x4FA3FF, 0.90f),
    EPIC("Epic", "Epic", 0xB45CFF, 0.95f),
    LEGENDARY("Legendary", "Legendary", 0xFFA41C, 1.00f),
    MYTHIC("Mythic", "Mythic", 0xFF3B5C, 1.05f),
    ANCIENT("Ancient", "Ancient", 0x3DFFD0, 1.10f),
    /** The two ultimate weapons, forged from all the others: calamities that break the world itself. */
    CALAMITY("Calamity Class", "Calamity", 0xFF2436, 1.25f),
    /** The Weapon of God, forged from both calamities: the light the world was made from. */
    BRILLIANT_LIGHT("The Last Fragment of Brilliant Light", "Brilliant Light", 0xFFF0B0, 1.5f);

    private final String displayName, shortName;
    private final int color;
    private final float damageFactor;

    Rarity(String displayName, String shortName, int color, float damageFactor) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.color = color;
        this.damageFactor = damageFactor;
    }

    public String displayName() {
        return displayName;
    }

    /** The name where room is short (the HUD, the progression menu). */
    public String shortName() {
        return shortName;
    }

    public int color() {
        return color;
    }

    public float damageFactor() {
        return damageFactor;
    }

    /** One of the world-breaking weapons (the two calamities and the Weapon of God) rather than one of the legendary twenty-five. */
    public boolean ultimate() {
        return this == CALAMITY || this == BRILLIANT_LIGHT;
    }

    /** A class of its own, named on its own line rather than before the weapon's kind. */
    public boolean classTier() {
        return ultimate();
    }
}
