package com.fantasyweapons.compat.punchy;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.config.ClientConfig;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Map;

/**
 * Optional Punchy support. Punchy draws its own first-person arms and plays its own animations for whatever is held,
 * which hid this mod's: the Wayfarer's Codex is lifted, opened and read right there in the hands, and the weapons and
 * tools have their own holds and swings. Punchy leaves the items on its blacklist to the game, so this mod's items are
 * put on it, as "dual-handed" so that the other hand is left alone too while one of them is held.
 * <p>
 * It goes through Punchy's own settings (they show up in its config screen) and only by reflection, so Punchy is not
 * needed to build or run. The client config's {@code punchy_keep_own_animations} takes the entry off again.
 */
public final class PunchyCompat {
    public static final String MOD_ID = "punchy";
    /** Punchy matches a bare namespace against every item of that mod. */
    private static final String ENTRY = FantasyWeapons.MOD_ID;

    private PunchyCompat() {
    }

    /** Puts this mod's items on Punchy's blacklist (or takes them off), as the client config says. */
    @SuppressWarnings("unchecked")
    public static void sync() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        boolean keep = ClientConfig.get(ClientConfig.PUNCHY_KEEP_OWN_ANIMATIONS, true);
        try {
            Class<?> config = Class.forName("punchy.config.PunchyConfig");
            Object data = config.getMethod("snapshot").invoke(null);
            List<String> blacklist = (List<String>) data.getClass().getField("itemBlacklist").get(data);
            Map<String, Boolean> dualHanded = (Map<String, Boolean>) data.getClass().getField("blacklistApplyDualHanded").get(data);
            if (blacklist.contains(ENTRY) == keep) return;
            if (keep) {
                blacklist.add(ENTRY);
                dualHanded.put(ENTRY, true);
            } else {
                blacklist.remove(ENTRY);
                dualHanded.remove(ENTRY);
            }
            config.getMethod("applyAndSave", data.getClass()).invoke(null, data);
            FantasyWeapons.LOGGER.info(keep ? "Punchy: Fantasy Weapons items added to its item blacklist, so they keep their own first-person animations"
                    : "Punchy: Fantasy Weapons items taken off its item blacklist");
        } catch (ReflectiveOperationException | RuntimeException e) {
            FantasyWeapons.LOGGER.warn("Punchy: could not update its item blacklist; add \"{}\" to it in Punchy's settings to keep the Codex and weapon animations",
                    ENTRY, e);
        }
    }
}
