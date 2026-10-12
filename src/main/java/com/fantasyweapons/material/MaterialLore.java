package com.fantasyweapons.material;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Tooltip lines shared by a material's three forms, tinted with the material's glow colour. */
public final class MaterialLore {
    private MaterialLore() {
    }

    public enum Form {
        ORE, RAW, REFINED
    }

    public static void append(Material m, Form form, List<Component> tooltip) {
        int light = mix(m.color(), 0xFFFFFF, 0.45f), ink = mix(0xD8C9A8, m.color(), 0.15f), faded = mix(m.color(), 0x9C8862, 0.55f);
        String weapon = m.weaponDefinition().displayName();
        tooltip.add(Component.literal("Legendary material of " + weapon).withStyle(s -> s.withColor(m.color())));
        for (String line : wrap(m.look(), 40)) tooltip.add(Component.literal(line).withStyle(s -> s.withColor(ink).withItalic(true)));
        tooltip.add(Component.empty());
        switch (form) {
            case ORE -> {
                tooltip.add(Component.literal("⚒ Found: " + m.found()).withStyle(s -> s.withColor(faded)));
                tooltip.add(Component.literal("⚒ Mine with a diamond pickaxe or better").withStyle(s -> s.withColor(faded)));
            }
            case RAW -> {
                tooltip.add(Component.literal("♨ Refine it in the Arcane Refinery").withStyle(s -> s.withColor(light)));
                tooltip.add(Component.literal("   to make " + m.refinedName()).withStyle(s -> s.withColor(faded)));
            }
            case REFINED -> {
                tooltip.add(Component.literal("✦ Crafts " + weapon + " armor").withStyle(s -> s.withColor(light)));
                tooltip.add(Component.literal("✦ Upgrades " + weapon).withStyle(s -> s.withColor(light)));
            }
        }
    }

    /** Tooltip border (start, end) and background for a material's items. */
    public static int[] tooltipColors(Material m) {
        int c = m.color();
        return new int[]{0xFF000000 | mix(c, 0xFFFFFF, 0.2f), 0xFF000000 | mix(c, 0x000000, 0.6f), 0xF0000000 | mix(c, 0x0A0604, 0.9f)};
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
