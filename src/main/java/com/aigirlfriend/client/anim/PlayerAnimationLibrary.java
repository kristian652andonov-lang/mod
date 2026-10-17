package com.aigirlfriend.client.anim;

import com.aigirlfriend.AIGirlfriend;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads player animations from assets/&lt;namespace&gt;/player_animations/*.json (Blockbench "Bedrock animation" export
 * format). Animation names are matched without the "animation." / "player." prefixes, e.g. "animation.player.hug" -> "hug".
 * Resource packs can override or extend them.
 */
public final class PlayerAnimationLibrary extends SimpleJsonResourceReloadListener {
    private static final Map<String, BedrockAnimation> ANIMATIONS = new HashMap<>();

    public PlayerAnimationLibrary() {
        super(new Gson(), "player_animations");
    }

    @Nullable
    public static BedrockAnimation get(String name) {
        return ANIMATIONS.get(name);
    }

    public static String shortName(String full) {
        String s = full;
        if (s.startsWith("animation.")) s = s.substring("animation.".length());
        if (s.startsWith("player.")) s = s.substring("player.".length());
        return s;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        ANIMATIONS.clear();
        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            try {
                JsonObject root = file.getValue().getAsJsonObject();
                JsonObject anims = root.has("animations") ? root.getAsJsonObject("animations") : root;
                for (Map.Entry<String, JsonElement> a : anims.entrySet()) {
                    if (!a.getValue().isJsonObject()) continue;
                    String name = shortName(a.getKey());
                    ANIMATIONS.put(name, BedrockAnimation.parse(name, a.getValue().getAsJsonObject()));
                }
            } catch (Exception e) {
                AIGirlfriend.LOGGER.error("Failed to load player animation file {}", file.getKey(), e);
            }
        }
        AIGirlfriend.LOGGER.info("Loaded {} player animations", ANIMATIONS.size());
        com.aigirlfriend.client.render.ResourceCheck.clear();
    }
}
