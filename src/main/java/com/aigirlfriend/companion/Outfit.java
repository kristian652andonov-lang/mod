package com.aigirlfriend.companion;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Her outfits. Each is its own GeckoLib model + texture built on the shared skeleton in girlfriend.geo.json and drawn
 * by the outfit layer, so switching only changes which layer is drawn (animations, AI and position are untouched).
 * Generated originally by art/character/tools/build-assets.js.
 */
public enum Outfit {
    EVERYDAY("everyday", Set.of("hair", "hair_front", "hair_back", "chest_skin", "bust", "right_upper_arm", "right_lower_arm", "left_upper_arm", "left_lower_arm"), 0f),
    BEACH("beach", Set.of(), 0f),
    SLEEP("sleep", Set.of(), 0f);

    public final String id;
    /** Body bones hidden while this outfit is worn (they sit fully under opaque fabric). */
    public final Set<String> hiddenBodyBones;
    /** Z offset (px) applied to hair_front so the front strands lie on top of thick fabric. */
    public final float hairFrontPush;
    public final ResourceLocation model;
    public final ResourceLocation texture;

    Outfit(String id, Set<String> hiddenBodyBones, float hairFrontPush) {
        this.id = id;
        this.hiddenBodyBones = hiddenBodyBones;
        this.hairFrontPush = hairFrontPush;
        this.model = ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "geo/entity/girlfriend_" + id + ".geo.json");
        this.texture = ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "textures/entity/girlfriend/" + id + ".png");
    }

    public String key() {
        return "aigirlfriend.outfit." + id;
    }

    public static Outfit byIndex(int i) {
        Outfit[] v = values();
        return i >= 0 && i < v.length ? v[i] : EVERYDAY;
    }
}
