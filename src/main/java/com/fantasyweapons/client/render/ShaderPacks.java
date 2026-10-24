package com.fantasyweapons.client.render;

import com.fantasyweapons.FantasyWeapons;

import java.lang.reflect.Method;

/**
 * Whether a shader pack is drawing the world (Iris). A pack replaces the game's own shaders with its programs and, by
 * default, draws nothing made with a shader it does not know, which is every one of the effects' own; while a pack is
 * on, the effects draw with the game's shaders instead (see {@link FWRenderTypes}), which the pack replaces with its own.
 * Read through Iris's public API by reflection, so Iris is not needed to build or run.
 */
public final class ShaderPacks {
    private static Object api;
    private static Method inUse;
    private static boolean looked;

    private ShaderPacks() {
    }

    public static boolean inUse() {
        if (!looked) {
            looked = true;
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                inUse = type.getMethod("isShaderPackInUse");
            } catch (ClassNotFoundException e) {
                api = null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                FantasyWeapons.LOGGER.warn("Could not read Iris's API; the effects keep their own shaders", e);
                api = null;
            }
        }
        if (api == null) return false;
        try {
            return (boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            api = null;
            return false;
        }
    }
}
