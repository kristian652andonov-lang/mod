package com.fantasyweapons.datagen;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Better Combat weapon attributes ({@code data/fantasyweapons/weapon_attributes}). The files are inert unless Better
 * Combat is installed; with it, every blade swings in its combos (see {@code compat/bettercombat}).
 * <p>
 * Each weapon class gets its own chain of attacks, matched to how the weapon fights here: hitbox, arc and finisher.
 * No idle pose is set, so the weapons keep their own stances; the swing trails take the weapon's element colours.
 * The long blades (greatswords, Monolith) stay off Better Combat's slams, which end with the blade buried in the
 * ground: they sweep and cleave instead.
 * Staffs and spellbooks get attributes without attacks, so Better Combat leaves their click (a bolt) alone. Of the
 * tools only the axes fight Better Combat's way, as vanilla axes do; pickaxes and shovels stay vanilla diggers.
 */
public class FWBetterCombat implements DataProvider {
    private final PackOutput.PathProvider paths;

    public FWBetterCombat(PackOutput out) {
        this.paths = out.createPathProvider(PackOutput.Target.DATA_PACK, "weapon_attributes");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (WeaponDefinition def : Weapons.all()) {
            writes.add(save(cache, def.id(), weapon(def)));
        }
        for (Material m : Material.values()) {
            JsonObject axe = new JsonObject();
            axe.addProperty("parent", "bettercombat:axe");
            JsonObject attributes = new JsonObject();
            attributes.add("trail_appearance", trail(m.element()));
            axe.add("attributes", attributes);
            writes.add(save(cache, m.toolPrefix() + "_axe", axe));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<?> save(CachedOutput cache, String id, JsonObject json) {
        return DataProvider.saveStable(cache, json, paths.json(ResourceLocation.fromNamespaceAndPath(FantasyWeapons.MOD_ID, id)));
    }

    private static JsonObject weapon(WeaponDefinition def) {
        JsonObject attributes = new JsonObject();
        attributes.addProperty("two_handed", def.weaponClass().twoHanded());
        JsonArray attacks = new JsonArray();
        switch (def.weaponClass()) {
            case LONGSWORD -> {
                attributes.addProperty("category", "sword");
                attacks.add(attack("HORIZONTAL_PLANE", 120, 1.0, "one_handed_slash_horizontal_right", "sword_slash", 1f));
                attacks.add(attack("HORIZONTAL_PLANE", 120, 1.0, "one_handed_slash_horizontal_left", "sword_slash", 1.05f));
                attacks.add(attack("FORWARD_BOX", 0, 1.25, "one_handed_stab", "sword_slash", 1.2f));
            }
            case GREATSWORD -> {
                attributes.addProperty("category", "claymore");
                attacks.add(attack("HORIZONTAL_PLANE", 150, 0.9, "two_handed_slash_horizontal_right", "claymore_swing", 1f));
                attacks.add(attack("FORWARD_BOX", 0, 1.0, "two_handed_stab_left", "claymore_stab", 1f));
                attacks.add(attack("VERTICAL_PLANE", 150, 1.3, "two_handed_slash_vertical_right", "claymore_slam", 1f));
            }
            case BATTLEAXE -> {
                attributes.addProperty("category", "double_axe");
                attacks.add(attack("HORIZONTAL_PLANE", 150, 1.0, "two_handed_slash_horizontal_right", "double_axe_swing", 1f));
                attacks.add(attack("VERTICAL_PLANE", 120, 1.1, "two_handed_slash_vertical_right", "double_axe_swing", 0.95f));
                attacks.add(attack("HORIZONTAL_PLANE", 360, 1.2, "two_handed_spin", "double_axe_swing", 0.85f));
            }
            case SCYTHE -> {
                attributes.addProperty("category", "scythe");
                attacks.add(attack("HORIZONTAL_PLANE", 160, 1.0, "two_handed_slash_horizontal_right", "scythe_slash", 1f));
                attacks.add(attack("HORIZONTAL_PLANE", 160, 1.0, "two_handed_slash_horizontal_left", "scythe_slash", 1.05f));
                attacks.add(attack("HORIZONTAL_PLANE", 360, 1.2, "two_handed_spin", "scythe_slash", 0.9f));
            }
            case WARHAMMER -> {
                attributes.addProperty("category", "hammer");
                attacks.add(attack("VERTICAL_PLANE", 120, 1.0, "two_handed_slash_vertical_right", "hammer_slam", 1f));
                attacks.add(attack("HORIZONTAL_PLANE", 180, 1.0, "two_handed_slash_horizontal_right", "mace_slash", 0.9f));
                attacks.add(attack("VERTICAL_PLANE", 150, 1.3, "two_handed_slam", "hammer_slam", 0.85f));
            }
            case LANCE -> {
                attributes.addProperty("category", "spear");
                attacks.add(attack("FORWARD_BOX", 0, 1.0, "two_handed_stab_right", "spear_stab", 1f));
                attacks.add(attack("FORWARD_BOX", 0, 1.0, "two_handed_stab_left", "spear_stab", 1.1f));
                attacks.add(attack("VERTICAL_PLANE", 120, 1.2, "two_handed_slash_vertical_right", "glaive_slash_quick", 1f));
            }
            case COLOSSAL -> {
                attributes.addProperty("category", "claymore");
                attacks.add(attack("HORIZONTAL_PLANE", 160, 1.0, "two_handed_slash_horizontal_right", "claymore_swing", 0.8f));
                attacks.add(attack("HORIZONTAL_PLANE", 160, 1.3, "two_handed_slash_horizontal_left", "claymore_slam", 0.75f));
            }
            case CHAINBLADE -> {
                attributes.addProperty("category", "sword");
                attacks.add(attack("HORIZONTAL_PLANE", 120, 1.0, "one_handed_slash_horizontal_right", "sword_slash", 1f));
                attacks.add(attack("HORIZONTAL_PLANE", 120, 1.0, "one_handed_slash_horizontal_left", "sword_slash", 1.05f));
                attacks.add(attack("HORIZONTAL_PLANE", 150, 1.2, "one_handed_swipe_horizontal_right", "sword_slash", 0.9f));
            }
            case STAFF -> attributes.addProperty("category", "staff");
            case TOME -> attributes.addProperty("category", "spellbook");
        }
        // a focus has no attacks: Better Combat then hands its click back to the game (and to MageBolts)
        if (!attacks.isEmpty()) {
            attributes.add("attacks", attacks);
            attributes.add("trail_appearance", trail(def.element()));
        }
        JsonObject json = new JsonObject();
        json.add("attributes", attributes);
        return json;
    }

    private static JsonObject attack(String hitbox, double angle, double damage, String animation, String sound, float pitch) {
        JsonObject attack = new JsonObject();
        attack.addProperty("hitbox", hitbox);
        attack.addProperty("angle", angle);
        attack.addProperty("damage_multiplier", damage);
        attack.addProperty("upswing", 0.5);
        attack.addProperty("animation", "bettercombat:" + animation);
        JsonObject swing = new JsonObject();
        swing.addProperty("id", "bettercombat:" + sound);
        if (pitch != 1f) swing.addProperty("pitch", pitch);
        attack.add("swing_sound", swing);
        return attack;
    }

    /** The swing trail in the element's colours: its light tone over its main colour, both glowing. */
    private static JsonObject trail(Element element) {
        JsonObject look = new JsonObject();
        look.add("primary", part(element.light(), 0xFF));
        look.add("secondary", part(element.primary(), 0xB0));
        JsonObject trail = new JsonObject();
        trail.add("default_appearance", look);
        trail.add("conditional", new JsonObject());
        return trail;
    }

    private static JsonObject part(int rgb, int alpha) {
        JsonObject part = new JsonObject();
        part.addProperty("color_rgba", ((long) (rgb & 0xFFFFFF) << 8) | alpha);
        part.addProperty("glows", true);
        return part;
    }

    @Override
    public String getName() {
        return "Fantasy Weapons: Better Combat weapon attributes";
    }
}
