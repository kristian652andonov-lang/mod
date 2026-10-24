package com.fantasyweapons.client.devtest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * DEVELOPMENT ONLY. Watches the live effects while abilities are cast and logs every effect that belongs on the ground
 * - decals and shock rings lying flat, spikes, cracks, rifts, rubble, vines growing out of the ground, flowers - but
 * has no ground beneath it, i.e. was left floating in the air.
 */
final class GroundAudit {
    private static String label;
    private static final Set<Vfx> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<String, Double> found = new TreeMap<>();

    private GroundAudit() {
    }

    static void begin(String what) {
        flush();
        label = what;
        seen.clear();
        seen.addAll(VfxManager.active()); // whatever was already playing is not this cast's
    }

    static void flush() {
        if (label != null && !found.isEmpty()) FantasyWeapons.LOGGER.warn("[groundaudit] {} -> {}", label, found);
        else if (label != null) FantasyWeapons.LOGGER.info("[groundaudit] {} ok", label);
        found.clear();
        label = null;
    }

    static void tick(Minecraft mc) {
        if (label == null || mc.level == null) return;
        for (Vfx v : VfxManager.active()) {
            if (!seen.add(v)) continue;
            try {
                for (Vec3 p : groundPoints(v)) {
                    double h = above(mc.level, p);
                    if (h > 0.6) found.merge(v.getClass().getSimpleName() + String.format("@%.1f", p.y - mc.player.getY()), h, Math::max);
                }
            } catch (ReflectiveOperationException e) {
                FantasyWeapons.LOGGER.error("groundaudit", e);
            }
        }
    }

    /** The points of an effect that should touch the ground (none for effects that may legitimately float). */
    private static List<Vec3> groundPoints(Vfx v) throws ReflectiveOperationException {
        List<Vec3> out = new ArrayList<>();
        String n = v.getClass().getSimpleName();
        switch (n) {
            case "DecalVfx", "ShockwaveVfx" -> {
                Vec3 ax = get(v, "ax"), ay = get(v, "ay");
                java.util.function.Function<Float, Vec3> track = n.equals("DecalVfx") ? get(v, "track") : null;
                Vec3 c = track != null ? track.apply(1f) : get(v, "center");
                if (c != null && Math.abs(ax.cross(ay).normalize().y) > 0.9) out.add(c);
            }
            case "SoulRiftVfx", "FlowerVfx" -> out.add(get(v, "center"));
            case "SpikeVfx" -> {
                for (Object s : (List<?>) get(v, "spikes")) out.add(get(s, "base"));
            }
            case "FissureVfx" -> {
                for (Object c : (List<?>) get(v, "cracks")) out.add(((Vec3[]) get(c, "pts"))[0]);
            }
            case "EarthChunkVfx" -> {
                for (Object pc : (List<?>) get(v, "pieces")) {
                    Vec3 base = get(pc, "base"), hover = get(pc, "hoverBase"), pos = get(pc, "pos");
                    out.add(base != null ? base : hover != null ? hover : new Vec3(pos.x, (double) get(pc, "floor"), pos.z));
                }
            }
            case "VineVfx" -> {
                out.add(((Vec3[]) get(v, "path"))[0]);
            }
            default -> {
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object o, String field) throws ReflectiveOperationException {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return (T) f.get(o);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(field);
    }

    /** Height of {@code p} above the top of the first solid block below it (99 over the void). */
    private static double above(Level level, Vec3 p) {
        BlockPos b = BlockPos.containing(p.x, p.y + 0.3, p.z);
        for (int i = 0; i < 64; i++) {
            BlockPos q = b.below(i);
            var shape = level.getBlockState(q).getCollisionShape(level, q);
            if (!shape.isEmpty()) return p.y - (q.getY() + shape.max(net.minecraft.core.Direction.Axis.Y));
        }
        return 99;
    }
}
