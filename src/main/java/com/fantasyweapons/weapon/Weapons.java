package com.fantasyweapons.weapon;

import com.fantasyweapons.weapons.voidfang.Voidfang;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of the 13 weapon definitions, in the order of the design brief.
 * <p>
 * Weapons are implemented one at a time (each in {@code com.fantasyweapons.weapons.<name>}). Weapons whose ability
 * tree is not implemented yet are still real items with their model, animations, stats, progression and melee — they
 * simply have no abilities until their phase lands.
 */
public final class Weapons {
    private static final Map<String, WeaponDefinition> BY_ID = new LinkedHashMap<>();

    static {
        add(Voidfang.create());
        add(basic("solaris", "Solaris", "The Sun-Forged Greatsword", Element.SOLAR, Rarity.LEGENDARY, WeaponClass.GREATSWORD, 140, 2.3f));
        add(basic("frostrend", "Frostrend", "Blade of the Eternal Winter", Element.ICE, Rarity.LEGENDARY, WeaponClass.LONGSWORD, 105, 2.3f));
        add(basic("doomcleaver", "Doomcleaver", "Axe of the Crimson Hunger", Element.BLOOD, Rarity.MYTHIC, WeaponClass.BATTLEAXE, 170, 2.2f));
        add(basic("stormbreaker", "Stormbreaker", "Twin-Headed Tempest", Element.LIGHTNING, Rarity.LEGENDARY, WeaponClass.BATTLEAXE, 150, 2.2f));
        add(basic("gravebite", "Gravebite", "The Cursed Maw", Element.NECROMANCY, Rarity.MYTHIC, WeaponClass.BATTLEAXE, 150, 2.2f));
        add(basic("soulreaper", "Soulreaper", "Harvester of Souls", Element.SOUL, Rarity.MYTHIC, WeaponClass.SCYTHE, 120, 2.2f));
        add(basic("bloomfall", "Bloomfall", "Scythe of the Wild Bloom", Element.NATURE, Rarity.LEGENDARY, WeaponClass.SCYTHE, 110, 2.2f));
        add(WeaponDefinition.builder("eclipse_reaper")
                .name("Eclipse Reaper", "Where Sun and Moon Meet")
                .element(Element.CELESTIAL).rarity(Rarity.ANCIENT).type(WeaponClass.SCYTHE).damage(135, 2.3f)
                .theme(0xFFE7A0, 0xFFFFFF)
                .form(new WeaponForm("light", "Light", "MODE", 0xFFD978, 0xFFFFFF, "transform_reverse", "idle", "attack", 0f, 1.0f, 1.0f))
                .form(new WeaponForm("dark", "Dark", "MODE", 0x8B3DFF, 0xB0123A, "transform", "idle_dark", "attack", 0f, 1.0f, 1.0f))
                .build());
        add(basic("starforge", "Starforge", "Hammer of the Fallen Stars", Element.COSMIC, Rarity.ANCIENT, WeaponClass.WARHAMMER, 200, 2.4f));
        add(basic("aetherlance", "Aetherlance", "Spear of the Celestial Court", Element.ENERGY, Rarity.MYTHIC, WeaponClass.LANCE, 130, 2.2f));
        add(basic("monolith", "Monolith", "The Mountain's Edge", Element.EARTH, Rarity.ANCIENT, WeaponClass.COLOSSAL, 320, 2.5f));
        add(WeaponDefinition.builder("infernochain")
                .name("Infernochain", "The Drake's Burning Coil")
                .element(Element.FIRE).rarity(Rarity.MYTHIC).type(WeaponClass.CHAINBLADE).damage(110, 2.2f)
                .theme(0xFF5A1F, 0xFFD38A)
                // SWORD is entered with the artist's "retract" (Retraction), CHAINBLADE with "transform" (Transform);
                // chainblade attacks use "whip" (Chainblade attack).
                .form(new WeaponForm("sword", "Sword", "FORM", 0xFF7A2F, 0xFFD38A, "retract", "idle", "attack", 0f, 1.0f, 1.0f))
                .form(new WeaponForm("chainblade", "Chainblade", "FORM", 0xFF2A10, 0xFFB15A, "transform", "chain_idle", "whip", 4.0f, 1.15f, 0.8f))
                .build());
    }

    private Weapons() {
    }

    private static void add(WeaponDefinition def) {
        if (BY_ID.put(def.id(), def) != null) throw new IllegalStateException("Duplicate weapon " + def.id());
    }

    private static WeaponDefinition basic(String id, String name, String title, Element element, Rarity rarity, WeaponClass type,
                                          float damage, float heavy) {
        return WeaponDefinition.builder(id).name(name, title).element(element).rarity(rarity).type(type).damage(damage, heavy)
                .lifesteal(id.equals("doomcleaver") ? 0.06f : 0f).build();
    }

    public static List<WeaponDefinition> all() {
        return Collections.unmodifiableList(List.copyOf(BY_ID.values()));
    }

    public static WeaponDefinition get(String id) {
        return BY_ID.get(id);
    }
}
