package com.fantasyweapons.world;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ticks {@link AreaEffect}s per dimension on the server thread. */
public final class AreaEffectManager {
    private static final Map<ResourceKey<Level>, List<AreaEffect>> EFFECTS = new HashMap<>();
    private static final int MAX_PER_LEVEL = 512;

    private AreaEffectManager() {
    }

    public static void add(AreaEffect effect) {
        List<AreaEffect> list = EFFECTS.computeIfAbsent(effect.level.dimension(), k -> new ArrayList<>());
        if (list.size() >= MAX_PER_LEVEL) {
            FantasyWeapons.LOGGER.warn("Too many area effects in {}, dropping oldest", effect.level.dimension().location());
            list.remove(0);
        }
        list.add(effect);
    }

    public static void tick(ServerLevel level) {
        List<AreaEffect> list = EFFECTS.get(level.dimension());
        if (list == null || list.isEmpty()) return;
        List<AreaEffect> snapshot = new ArrayList<>(list);
        list.clear();
        for (AreaEffect e : snapshot) {
            boolean keep;
            try {
                keep = e.tickInternal();
            } catch (RuntimeException ex) {
                FantasyWeapons.LOGGER.error("Area effect {} crashed and was removed", e.getClass().getSimpleName(), ex);
                keep = false;
            }
            if (keep) list.add(e);
        }
    }

    public static int count(ServerLevel level) {
        List<AreaEffect> list = EFFECTS.get(level.dimension());
        return list == null ? 0 : list.size();
    }

    public static void clear() {
        EFFECTS.clear();
    }
}
