package com.terrahaul.worldgen.ruins;

import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where {@code /terrahaul find} sends a player to look at a site: a column near it where someone
 * can stand, worked out from the site's own plans and the terrain model. The command offers a
 * teleport to the point, so it must never lie inside a block, down a well shaft or in water: the
 * cells of the feet and the head must be free (air, or a plant one walks through), the cell below
 * must carry a player, and no water may lie within three blocks below.
 * <p>
 * The search starts from a column the caller prefers (for a trail ruin the trail in front of it,
 * for a post the path side of it) and goes outward ring by ring, nearest first, so the point is a
 * pure function of the site like everything else about it.
 */
final class Standpoint {
    /** Farthest the search goes from the preferred column. */
    static final int REACH = 8;
    /** Highest a standing point may lie above the terrain: a step up onto a floor or a fill. */
    private static final int RISE = 3;

    private final TerrainModel m;
    private final List<RuinPlan> plans;
    private final boolean underwater;
    /** Ground of each grouped plan's anchor column (posts are built relative to it). */
    private final int[] anchors;
    /** Raised decals by column. */
    private final Map<Long, List<RuinPlan.Decal>> decals = new HashMap<>();

    private Standpoint(TerrainModel m, List<RuinPlan> plans, boolean underwater) {
        this.m = m;
        this.plans = plans;
        this.underwater = underwater;
        this.anchors = new int[plans.size()];
        for (int i = 0; i < plans.size(); i++) {
            RuinPlan p = plans.get(i);
            anchors[i] = p.mode == RuinPlan.GROUPED ? ground(p.anchorX, p.anchorZ) : 0;
            for (RuinPlan.Decal d : p.decals) {
                decals.computeIfAbsent(key(d.x(), d.z()), k -> new ArrayList<>()).add(d);
            }
        }
    }

    /**
     * The standing point {x, y, z} nearest the preferred column (y is where the feet go), or that
     * column on its ground if nothing within {@link #REACH} blocks will do.
     */
    static int[] find(TerrainModel m, List<RuinPlan> plans, int x0, int z0, boolean underwater) {
        Standpoint s = new Standpoint(m, plans, underwater);
        for (int r = 0; r <= REACH; r++) {
            for (int[] d : ring(r)) {
                int y = s.stand(x0 + d[0], z0 + d[1]);
                if (y != Integer.MIN_VALUE) {
                    return new int[]{x0 + d[0], y, z0 + d[1]};
                }
            }
        }
        return new int[]{x0, s.ground(x0, z0) + 1, z0};
    }

    /** The offsets at Chebyshev distance {@code r}, nearest first; ties by x, then z. */
    private static List<int[]> ring(int r) {
        List<int[]> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                    out.add(new int[]{dx, dz});
                }
            }
        }
        out.sort((a, b) -> {
            int da = a[0] * a[0] + a[1] * a[1];
            int db = b[0] * b[0] + b[1] * b[1];
            return da != db ? Integer.compare(da, db) : a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]);
        });
        return out;
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    private int ground(int x, int z) {
        return (int) Math.floor(m.interpolatedHeight(x, z));
    }

    /** Y where the feet go in a column, or {@code Integer.MIN_VALUE} if no one can stand there. */
    private int stand(int x, int z) {
        if (!underwater && RuinSites.water(m, x, z)) {
            return Integer.MIN_VALUE;
        }
        int g = ground(x, z);
        for (int y = g + 1; y <= g + RISE; y++) {
            if (!support(at(x, y - 1, z, g)) || !free(at(x, y, z, g)) || !free(at(x, y + 1, z, g))) {
                continue;
            }
            boolean dry = true;
            for (int k = 1; k <= 3 && dry && !underwater; k++) {
                dry = at(x, y - k, z, g) != WATER_CELL;
            }
            if (dry) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    // what occupies a cell, beyond the cell codes of the plans
    private static final int TERRAIN = -1;
    private static final int EMPTY = -2;
    private static final int FILL = -3;
    private static final int WATER_CELL = -4;

    /**
     * What the world holds at a cell once the plans are built, as the feature builds them: the
     * first plan with a cell there decides; a soft cell never replaces the terrain; foundation
     * fill rises from the ground to the floor course; raised decals stand on their column.
     */
    private int at(int x, int y, int z, int g) {
        for (int i = 0; i < plans.size(); i++) {
            RuinPlan p = plans.get(i);
            boolean grouped = p.mode == RuinPlan.GROUPED;
            int py = grouped ? y - anchors[i] : y;
            int c = p.get(x, py, z);
            if (c != 0) {
                RuinPart part = RuinCell.part(c);
                // grouped posts never cut into the ground beside them
                boolean soft = RuinCell.soft(c) || grouped && py > 0;
                if (soft && y <= g) {
                    return TERRAIN;
                }
                if (part == RuinPart.AIR) {
                    return p.underwater ? WATER_CELL : EMPTY;
                }
                return part == RuinPart.WATER ? WATER_CELL : c;
            }
            if (!grouped && p.fill(x, z) != 0 && y > g && y < p.baseY && y >= p.baseY - RuinPlan.MAX_FILL) {
                return FILL;
            }
        }
        List<RuinPlan.Decal> ds = decals.get(key(x, z));
        if (ds != null) {
            for (RuinPlan.Decal d : ds) {
                if (d.dy() > 0 && g + d.dy() == y) {
                    return d.cell();
                }
            }
        }
        return y <= g ? TERRAIN : EMPTY;
    }

    /** Whether a cell carries a player standing on it (not a fence or wall top, which is higher). */
    private static boolean support(int c) {
        if (c == TERRAIN || c == FILL) {
            return true;
        }
        if (c < 0) {
            return false;
        }
        RuinPart p = RuinCell.part(c);
        return p.fullCube() || p.structural && p != RuinPart.FENCE && p != RuinPart.MASONRY_WALL;
    }

    /** Whether the feet or the head may be in a cell: air, or a plant one walks through. */
    private boolean free(int c) {
        if (c == EMPTY) {
            return true;
        }
        if (c == WATER_CELL) {
            return underwater;
        }
        if (c < 0) {
            return false;
        }
        return switch (RuinCell.part(c)) {
            case SHORT_GRASS, FERN, TALL_GRASS, FLOWER, DEAD_BUSH, MUSHROOM, MOSS_CARPET, CARPET, LITTER, VINE, GLOW_LICHEN -> true;
            case SEAGRASS, TALL_SEAGRASS, KELP, KELP_PLANT -> underwater;
            default -> false;
        };
    }
}
