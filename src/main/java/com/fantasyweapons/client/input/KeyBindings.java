package com.fantasyweapons.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Rebindable controls (Options → Controls → Fantasy Weapons). */
public final class KeyBindings {
    public static final String CATEGORY = "key.categories.fantasyweapons";

    /** Hold to charge, release to unleash the selected ability. */
    public static final KeyMapping ABILITY = new KeyMapping("key.fantasyweapons.ability", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    /** Switch form / mode (Infernochain, Eclipse Reaper). */
    public static final KeyMapping FORM = new KeyMapping("key.fantasyweapons.form", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY);
    /** Open the weapon progression menu. */
    public static final KeyMapping MENU = new KeyMapping("key.fantasyweapons.menu", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    /** Cycle the selected ability. */
    public static final KeyMapping CYCLE = new KeyMapping("key.fantasyweapons.cycle", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    private KeyBindings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ABILITY);
        event.register(FORM);
        event.register(MENU);
        event.register(CYCLE);
    }
}
