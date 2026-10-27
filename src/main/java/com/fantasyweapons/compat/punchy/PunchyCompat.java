package com.fantasyweapons.compat.punchy;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.config.ClientConfig;
import com.fantasyweapons.registry.ModItems;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Map;

/**
 * Optional Punchy support. Punchy draws its own first-person arms and animations for whatever is held. That suits the
 * weapons and tools, which keep Punchy's animations, but the Wayfarer's Codex is lifted, opened and read right there in
 * the hands: in Punchy's hand it stays shut and cannot be read. Punchy leaves the items on its blacklist to the game, so
 * the Codex is put on it, as "dual-handed" so that Punchy's other arm stays out of the open book too.
 * <p>
 * It goes through Punchy's own settings (the entry shows up in its config screen) and only by reflection, so Punchy is
 * not needed to build or run. The client config's {@code punchy_codex_own_animation} takes the entry off again.
 */
public final class PunchyCompat {
    public static final String MOD_ID = "punchy";

    private PunchyCompat() {
    }

    private static java.lang.reflect.Method betterCombatCompat;
    private static boolean betterCombatCompatLooked;

    /** Whether Punchy animates Better Combat's attacks itself (its own Better Combat setting, off unless turned on). */
    public static boolean animatesBetterCombatAttacks() {
        try {
            if (!betterCombatCompatLooked) {
                betterCombatCompatLooked = true;
                betterCombatCompat = Class.forName("punchy.config.PunchyConfig").getMethod("isBetterCombatCompatEnabled");
            }
            return betterCombatCompat != null && (boolean) betterCombatCompat.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Puts the Codex on Punchy's blacklist (or takes it off), as the client config says. */
    @SuppressWarnings("unchecked")
    public static void sync() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        boolean keep = ClientConfig.get(ClientConfig.PUNCHY_CODEX_OWN_ANIMATION, true);
        String entry = ModItems.CODEX.getId().toString();
        try {
            Class<?> config = Class.forName("punchy.config.PunchyConfig");
            Object data = config.getMethod("snapshot").invoke(null);
            List<String> blacklist = (List<String>) data.getClass().getField("itemBlacklist").get(data);
            Map<String, Boolean> dualHanded = (Map<String, Boolean>) data.getClass().getField("blacklistApplyDualHanded").get(data);
            if (blacklist.contains(entry) == keep) return;
            if (keep) {
                blacklist.add(entry);
                dualHanded.put(entry, true);
            } else {
                blacklist.remove(entry);
                dualHanded.remove(entry);
            }
            config.getMethod("applyAndSave", data.getClass()).invoke(null, data);
            FantasyWeapons.LOGGER.info(keep ? "Punchy: the Wayfarer's Codex added to its item blacklist, so it opens and reads with its own animation"
                    : "Punchy: the Wayfarer's Codex taken off its item blacklist");
        } catch (ReflectiveOperationException | RuntimeException e) {
            FantasyWeapons.LOGGER.warn("Punchy: could not update its item blacklist; add \"{}\" to it in Punchy's settings so the Codex can be read",
                    entry, e);
        }
    }
}
