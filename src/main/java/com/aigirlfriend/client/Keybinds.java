package com.aigirlfriend.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** All keybinds are configurable in Options > Controls > "AI Girlfriend Companion". */
public final class Keybinds {
    public static final String CATEGORY = "key.categories.aigirlfriend";

    public static final KeyMapping WHEEL = key("wheel", GLFW.GLFW_KEY_G);
    public static final KeyMapping STATUS = key("status", GLFW.GLFW_KEY_U);
    public static final KeyMapping TALK = key("talk", GLFW.GLFW_KEY_Y);
    public static final KeyMapping HOLD_HANDS = key("hold_hands", GLFW.GLFW_KEY_H);
    public static final KeyMapping SIT = key("sit", GLFW.GLFW_KEY_N);
    public static final KeyMapping CANCEL = key("cancel", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping HUG = key("hug", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping KISS = key("kiss", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping CUDDLE = key("cuddle", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping CARRY = key("carry", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping PET = key("pet", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping LAP = key("lap", InputConstants.UNKNOWN.getValue());

    private Keybinds() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.aigirlfriend." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping k : new KeyMapping[]{WHEEL, STATUS, TALK, HOLD_HANDS, SIT, CANCEL, HUG, KISS, CUDDLE, CARRY, PET, LAP}) event.register(k);
    }
}
