package com.aigirlfriend.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Cached "does this asset exist?" checks so optional textures (outfits, faces) can be added freely. */
public final class ResourceCheck {
    private static final Map<ResourceLocation, Boolean> CACHE = new HashMap<>();

    private ResourceCheck() {}

    public static boolean exists(ResourceLocation rl) {
        return CACHE.computeIfAbsent(rl, r -> Minecraft.getInstance().getResourceManager().getResource(r).isPresent());
    }

    public static void clear() {
        CACHE.clear();
    }
}
