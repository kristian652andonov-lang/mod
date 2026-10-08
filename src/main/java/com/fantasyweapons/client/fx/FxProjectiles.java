package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Vfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Links the client visuals of a server-simulated projectile to its seed, so the impact payload (same seed) can end
 * the flight visuals exactly where the server says it hit.
 */
public final class FxProjectiles {
    private static final Map<Long, List<Vfx>> LIVE = new HashMap<>();

    private FxProjectiles() {
    }

    public static void track(long seed, Vfx... vfx) {
        List<Vfx> list = LIVE.computeIfAbsent(seed, k -> new ArrayList<>());
        for (Vfx v : vfx) list.add(v);
        if (LIVE.size() > 256) LIVE.values().removeIf(l -> l.stream().allMatch(Vfx::isDead));
    }

    public static void end(long seed) {
        List<Vfx> list = LIVE.remove(seed);
        if (list != null) list.forEach(Vfx::kill);
    }

    public static void clear() {
        LIVE.clear();
    }
}
