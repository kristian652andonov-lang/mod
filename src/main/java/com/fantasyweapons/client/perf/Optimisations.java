package com.fantasyweapons.client.perf;

import com.fantasyweapons.config.ClientConfig;

/**
 * The optimisations of the settings menu's Performance section, each switched on or off there (all on unless switched
 * off): {@link AtlasAnimations}, {@link com.fantasyweapons.client.render.ModelCulling}.
 */
public final class Optimisations {
    private Optimisations() {
    }

    /** Pause the animations of the mod's atlas textures no model shows. */
    public static boolean pauseUnseenAnimations() {
        return ClientConfig.get(ClientConfig.PERF_UNSEEN_ANIMATIONS, true);
    }

    /** Draw only the parts of the mod's models that show in each pass. */
    public static boolean onlyWhatShows() {
        return ClientConfig.get(ClientConfig.PERF_HIDDEN_PARTS, true);
    }

    /** Leave out parts of the mod's models smaller than a pixel on the screen. */
    public static boolean skipTinyDetails() {
        return ClientConfig.get(ClientConfig.PERF_TINY_DETAILS, true);
    }
}
