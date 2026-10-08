package com.fantasyweapons.weapon;

/**
 * Elemental affinity of a weapon. Colours are ARGB-less RGB ints used by the HUD, menus and VFX.
 */
public enum Element {
    VOID("Void", 0x9B4DFF, 0x1A0633, 0xE2C8FF),
    SOLAR("Solar", 0xFFB627, 0x4A1D00, 0xFFF4C2),
    ICE("Ice", 0x6FD8FF, 0x0A2A44, 0xE8FBFF),
    BLOOD("Blood", 0xE0213A, 0x2A0006, 0xFF9AA8),
    LIGHTNING("Lightning", 0x5CB8FF, 0x0D1446, 0xE6F3FF),
    NECROMANCY("Necromancy", 0x5CFFB8, 0x0E0418, 0xC9FFE9),
    SOUL("Soul", 0x5A8CFF, 0x0B0620, 0xCFE0FF),
    NATURE("Nature", 0x5BE063, 0x0B2A0E, 0xFFE08A),
    CELESTIAL("Light & Dark", 0xFFE7A0, 0x14081F, 0xFFFFFF),
    COSMIC("Cosmic", 0x8A6CFF, 0x070A2E, 0xF0EDFF),
    ENERGY("Energy", 0x5FF3FF, 0x062433, 0xF2FFFF),
    EARTH("Earth", 0xB08A5A, 0x1E1812, 0xE8D9C0),
    FIRE("Fire", 0xFF5A1F, 0x1A0500, 0xFFD38A);

    private final String displayName;
    private final int primary;
    private final int dark;
    private final int light;

    Element(String displayName, int primary, int dark, int light) {
        this.displayName = displayName;
        this.primary = primary;
        this.dark = dark;
        this.light = light;
    }

    public String displayName() {
        return displayName;
    }

    public int primary() {
        return primary;
    }

    public int dark() {
        return dark;
    }

    public int light() {
        return light;
    }
}
