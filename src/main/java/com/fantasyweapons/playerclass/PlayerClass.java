package com.fantasyweapons.playerclass;

import com.fantasyweapons.text.Tr;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * The classes a player can take: how each changes them ({@link #modifiers}) and what it can do ({@link #perks}, done in
 * {@link ClassService}).
 */
public enum PlayerClass {
    NONE("Wayfarer", 0xCFC3A6, "No calling yet", "", true, List.of(), List.of()),
    ASSASSIN("Assassin", 0xB06CFF, "Strike from the shadows",
            "Quick, quiet and deadly from behind. Frailer than the rest, the assassin ends a fight before it starts.", true,
            List.of(new Mod(Attributes.MOVEMENT_SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                    new Mod(Attributes.ATTACK_SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                    new Mod(Attributes.SAFE_FALL_DISTANCE, 3, AttributeModifier.Operation.ADD_VALUE),
                    new Mod(Attributes.MAX_HEALTH, -4, AttributeModifier.Operation.ADD_VALUE)),
            List.of("+15% movement and attack speed", "+60% damage striking from behind", "+25% damage to the wounded (below half health)",
                    "Vanish: unseen and swift for 3s after a kill", "Lands softly (+3 safe fall)", "-2 hearts")),
    BERSERKER("Berserker", 0xFF4A3A, "Fury without end",
            "The closer to death, the deadlier. The berserker trades armor for rage and drinks the life of every kill.", true,
            List.of(new Mod(Attributes.ATTACK_DAMAGE, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    new Mod(Attributes.MAX_HEALTH, 2, AttributeModifier.Operation.ADD_VALUE),
                    new Mod(Attributes.KNOCKBACK_RESISTANCE, 0.2, AttributeModifier.Operation.ADD_VALUE),
                    new Mod(Attributes.ARMOR, -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)),
            List.of("+15% damage, +1 heart", "Rage: up to +50% damage as health falls", "Bloodthirst: heals 1 heart on every kill",
                    "+20% knockback resistance", "-20% armor")),
    PALADIN("Paladin", 0xFFD970, "Shield of the light",
            "A wall of steel and faith. The paladin outlasts any foe, and the undead fear the light they carry.", true,
            List.of(new Mod(Attributes.MAX_HEALTH, 6, AttributeModifier.Operation.ADD_VALUE),
                    new Mod(Attributes.ARMOR, 4, AttributeModifier.Operation.ADD_VALUE),
                    new Mod(Attributes.ARMOR_TOUGHNESS, 2, AttributeModifier.Operation.ADD_VALUE),
                    new Mod(Attributes.KNOCKBACK_RESISTANCE, 0.3, AttributeModifier.Operation.ADD_VALUE),
                    new Mod(Attributes.MOVEMENT_SPEED, -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)),
            List.of("+3 hearts, +4 armor, +2 toughness", "Smite: +50% damage to the undead", "Aegis: -25% damage from the undead",
                    "Blessed: heals out of combat", "+30% knockback resistance", "-5% movement speed")),
    MAGE("Mage", 0x6FB8FF, "Master of the elements",
            "Fire, frost, tide, bloom, light and the void answer to the mage. Frail up close, but no foe is beyond the reach of a spell, and no friend beyond its mending.", true,
            List.of(new Mod(Attributes.MAX_HEALTH, -2, AttributeModifier.Operation.ADD_VALUE),
                    new Mod(Attributes.MOVEMENT_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                    new Mod(Attributes.ARMOR, -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)),
            List.of("Staffs and spellbooks of six elements", "+15% spell damage, healing and shields", "Arcane Ward: a 2-heart shield out of combat",
                    "+5% movement speed", "-1 heart, -10% armor"));

    /** A change to one of the player's attributes. */
    public record Mod(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
    }

    private final String displayName, tagline, lore;
    private final int color;
    private final boolean available;
    private final List<Mod> modifiers;
    private final List<String> perks;

    PlayerClass(String displayName, int color, String tagline, String lore, boolean available, List<Mod> modifiers, List<String> perks) {
        this.displayName = displayName;
        this.color = color;
        this.tagline = tagline;
        this.lore = lore;
        this.available = available;
        this.modifiers = modifiers;
        this.perks = perks;
    }

    /** The class's name in the player's language. */
    public String displayName() {
        return Tr.s(displayName);
    }

    /** The class's name in English whatever the language. */
    public String englishName() {
        return displayName;
    }

    public int color() {
        return color;
    }

    public String tagline() {
        return Tr.s(tagline);
    }

    public String lore() {
        return Tr.s(lore);
    }

    /** Whether it can be chosen. */
    public boolean available() {
        return available;
    }

    public List<Mod> modifiers() {
        return modifiers;
    }

    /** What it does, line by line; a line starting with "-" is a drawback. */
    public List<String> perks() {
        return perks.stream().map(Tr::s).toList();
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static PlayerClass byId(String id) {
        for (PlayerClass c : values()) if (c.id().equals(id)) return c;
        return NONE;
    }
}
