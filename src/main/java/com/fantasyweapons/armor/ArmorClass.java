package com.fantasyweapons.armor;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;

/**
 * The three kinds of legendary armor. Assassin sets are light and fast, Paladin sets balanced plate, Berserker sets the
 * heaviest; all are tougher than netherite.
 */
public enum ArmorClass {
    ASSASSIN("Assassin", new int[]{3, 7, 5, 3}, 2f, 0f, 18, SoundEvents.ARMOR_EQUIP_CHAIN),
    PALADIN("Paladin", new int[]{4, 9, 7, 4}, 3f, 0.05f, 15, SoundEvents.ARMOR_EQUIP_DIAMOND),
    BERSERKER("Berserker", new int[]{4, 10, 8, 4}, 4f, 0.1f, 12, SoundEvents.ARMOR_EQUIP_NETHERITE);

    /** Durability multiplier (netherite is 37). */
    public static final int DURABILITY = 40;

    private final String displayName;
    private final int[] defense;
    private final float toughness;
    private final float knockbackResistance;
    private final int enchantability;
    private final Holder<SoundEvent> equipSound;

    ArmorClass(String displayName, int[] defense, float toughness, float knockbackResistance, int enchantability, Holder<SoundEvent> equipSound) {
        this.displayName = displayName;
        this.defense = defense;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.enchantability = enchantability;
        this.equipSound = equipSound;
    }

    public String displayName() {
        return displayName;
    }

    public int defense(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> defense[0];
            case CHESTPLATE -> defense[1];
            case LEGGINGS -> defense[2];
            case BOOTS -> defense[3];
            case BODY -> defense[1];
        };
    }

    public float toughness() {
        return toughness;
    }

    public float knockbackResistance() {
        return knockbackResistance;
    }

    public int enchantability() {
        return enchantability;
    }

    public Holder<SoundEvent> equipSound() {
        return equipSound;
    }
}
