package com.terrahaul.worldgen.features.lake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A plan of what the lake floor feature builds in one chunk, as block codes in a sparse voxel
 * buffer, like the sea floor's {@code MarineVolume}. Everything is rasterised from the
 * deterministic plan alone; cells nobody wrote keep a natural code derived from the planned floor
 * and the lake's surface: solid at or below the floor, water up to the surface, air above.
 * <p>
 * Wood codes carry the index of the {@link Item} they belong to (a log, a wreck, a pier), whose
 * species the feature picks from the forest around it. The same buffer is rendered by the
 * devtools, so what they show is what the feature builds.
 */
public final class LakeVolume {
    // ---- natural codes ------------------------------------------------------------------------
    /** Untouched: lake water (at or below the column's lake surface). */
    public static final byte NAT_WATER = 0;
    /** Untouched natural ground. */
    public static final byte NAT_SOLID = 1;
    /** Untouched: air above the lake surface or above dry ground. */
    public static final byte NAT_AIR = 2;

    // ---- bed materials ------------------------------------------------------------------------
    public static final byte SAND = 4;
    public static final byte GRAVEL = 5;
    public static final byte CLAY = 6;
    public static final byte MUD = 7;
    public static final byte MOSS = 8;
    public static final byte COBBLE = 9;
    public static final byte MOSSY_COBBLE = 10;
    public static final byte GRANITE = 11;
    public static final byte DIORITE = 12;
    /** The sediment of the floor below it, copied (shelves and drifts take the lake's own bed). */
    public static final byte FILL = 13;
    /** Rock of a family, plus family * 3 + stop (stop 0 is the top colour), like the sea floor's. */
    public static final byte ROCK = 16;
    /** Waterlogged Terrahaul pebbles, plus stone index ({@code TerrahaulBlocks.STONES} order). */
    public static final byte PEBBLES = 32;
    /** Waterlogged Terrahaul knee-high rock, plus stone index. */
    public static final byte ROCK_DECOR = 42;

    // ---- plants -------------------------------------------------------------------------------
    public static final byte SEAGRASS = 52;
    public static final byte TALL_LOWER = 53;
    public static final byte TALL_UPPER = 54;
    public static final byte KELP_PLANT = 55;
    /** Kelp head that has stopped growing. */
    public static final byte KELP_TOP = 56;
    /** Kelp head that may still grow a few blocks. */
    public static final byte KELP_GROWING = 57;
    public static final byte LILY_PAD = 58;
    /** Sugar cane, the reeds of the shore. */
    public static final byte REED = 59;
    public static final byte SMALL_DRIP_LOWER = 60;
    public static final byte SMALL_DRIP_UPPER = 61;
    public static final byte BIG_DRIP_STEM = 62;
    public static final byte BIG_DRIP_HEAD = 63;
    public static final byte MOSS_CARPET = 64;

    // ---- wood (species from the item) -----------------------------------------------------------
    /** Log lying along x, along z, upright. */
    public static final byte LOG_X = 70;
    public static final byte LOG_Z = 71;
    public static final byte LOG_Y = 72;
    /** Stripped (barkless, decayed) log along x, z, upright. */
    public static final byte STRIPPED_X = 73;
    public static final byte STRIPPED_Z = 74;
    public static final byte STRIPPED_Y = 75;
    public static final byte PLANKS = 76;
    /** Bottom slab, top slab. */
    public static final byte SLAB = 77;
    public static final byte SLAB_TOP = 78;
    public static final byte FENCE = 79;
    /** Stairs, plus facing (0 north, 1 east, 2 south, 3 west). */
    public static final byte STAIRS = 80;
    /** Chest with loot, plus facing. */
    public static final byte CHEST = 84;

    // ---- stonework ------------------------------------------------------------------------------
    public static final byte STONE_BRICKS = 90;
    public static final byte MOSSY_BRICKS = 91;
    public static final byte CRACKED_BRICKS = 92;
    public static final byte BRICK_SLAB = 93;

    /** Horizontal facings: north, east, south, west. */
    public static final int[] DIR_X = {0, 1, 0, -1};
    public static final int[] DIR_Z = {-1, 0, 1, 0};

    /** Something built of wood: where it lies, and the species the climate there suggests. */
    public record Item(int x, int y, int z, int wood) {
    }

    private final LakePlanner planner;
    public final int coreMinX;
    public final int coreMinZ;
    public final int coreSizeX;
    public final int coreSizeZ;
    private final LakeColumn[] columns;
    private final int[] base;
    private final byte[][] cells;
    private final byte[][] tints;
    private final Map<Long, LakeColumn> outside = new HashMap<>();
    public final List<Item> items = new ArrayList<>();

    public LakeVolume(LakePlanner planner, int coreMinX, int coreMinZ, int coreSizeX, int coreSizeZ) {
        this.planner = planner;
        this.coreMinX = coreMinX;
        this.coreMinZ = coreMinZ;
        this.coreSizeX = coreSizeX;
        this.coreSizeZ = coreSizeZ;
        this.columns = new LakeColumn[coreSizeX * coreSizeZ];
        this.base = new int[coreSizeX * coreSizeZ];
        this.cells = new byte[coreSizeX * coreSizeZ][];
        this.tints = new byte[coreSizeX * coreSizeZ][];
    }

    public LakePlanner planner() {
        return planner;
    }

    public boolean inCore(int x, int z) {
        return x >= coreMinX && x < coreMinX + coreSizeX && z >= coreMinZ && z < coreMinZ + coreSizeZ;
    }

    private int index(int x, int z) {
        return (x - coreMinX) * coreSizeZ + (z - coreMinZ);
    }

    /** The plan of a column, computed once per volume (also for columns outside the core). */
    public LakeColumn column(int x, int z) {
        if (inCore(x, z)) {
            int i = index(x, z);
            LakeColumn c = columns[i];
            if (c == null) {
                c = new LakeColumn();
                planner.column(x, z, c);
                columns[i] = c;
            }
            return c;
        }
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        LakeColumn c = outside.get(key);
        if (c == null) {
            c = new LakeColumn();
            planner.column(x, z, c);
            outside.put(key, c);
        }
        return c;
    }

    /** The natural code of a cell: ground at or below the floor, lake water up to the surface, air above. */
    public static byte natural(LakeColumn c, int y) {
        return y <= c.floor ? NAT_SOLID : c.wet && y <= c.surf ? NAT_WATER : NAT_AIR;
    }

    /** The code at a position: what was planned there, or the natural code. */
    public byte get(int x, int y, int z) {
        if (inCore(x, z)) {
            int i = index(x, z);
            byte[] col = cells[i];
            if (col != null) {
                int k = y - base[i];
                if (k >= 0 && k < col.length) {
                    return col[k];
                }
            }
        }
        return natural(column(x, z), y);
    }

    /** Sets a code; ignored outside the core. */
    public void set(int x, int y, int z, byte code) {
        if (!inCore(x, z)) {
            return;
        }
        int i = index(x, z);
        byte[] col = ensure(i, x, y, z);
        col[y - base[i]] = code;
    }

    /** Sets a code only into the lake's water or the air above it; returns whether it was set. */
    public boolean put(int x, int y, int z, byte code) {
        if (!inCore(x, z)) {
            return false;
        }
        byte here = get(x, y, z);
        if (here != NAT_WATER && here != NAT_AIR) {
            return false;
        }
        set(x, y, z, code);
        return true;
    }

    /** Index + 1 of the item a wood cell belongs to, 0 for none. */
    public int tint(int x, int y, int z) {
        if (!inCore(x, z)) {
            return 0;
        }
        int i = index(x, z);
        byte[] t = tints[i];
        if (t == null) {
            return 0;
        }
        int k = y - base[i];
        return k >= 0 && k < t.length ? t[k] & 0xFF : 0;
    }

    public void setTint(int x, int y, int z, int tint) {
        if (!inCore(x, z)) {
            return;
        }
        int i = index(x, z);
        ensure(i, x, y, z);
        byte[] t = tints[i];
        if (t == null || t.length != cells[i].length) {
            byte[] grown = new byte[cells[i].length];
            if (t != null) {
                System.arraycopy(t, 0, grown, 0, Math.min(t.length, grown.length));
            }
            tints[i] = t = grown;
        }
        t[y - base[i]] = (byte) tint;
    }

    /** The item of a wood cell, or null. */
    public Item item(int x, int y, int z) {
        int t = tint(x, y, z);
        return t == 0 || t > items.size() ? null : items.get(t - 1);
    }

    /** Registers an item; returns the tint of its cells. */
    public int addItem(Item item) {
        items.add(item);
        return Math.min(255, items.size());
    }

    private byte[] ensure(int i, int x, int y, int z) {
        byte[] col = cells[i];
        if (col != null && y >= base[i] && y < base[i] + col.length) {
            return col;
        }
        LakeColumn c = column(x, z);
        int lo = col == null ? Math.min(y, c.floor) - 2 : Math.min(base[i], y - 2);
        int hi = col == null ? Math.max(y, c.floor) + 8 : Math.max(base[i] + col.length, y + 8);
        byte[] grown = new byte[hi - lo];
        for (int k = 0; k < grown.length; k++) {
            grown[k] = natural(c, lo + k);
        }
        if (col != null) {
            System.arraycopy(col, 0, grown, base[i] - lo, col.length);
            byte[] t = tints[i];
            if (t != null) {
                byte[] tg = new byte[grown.length];
                System.arraycopy(t, 0, tg, base[i] - lo, t.length);
                tints[i] = tg;
            }
        }
        cells[i] = grown;
        base[i] = lo;
        return grown;
    }

    /** Lowest y with a stored code in a core column, or {@code Integer.MAX_VALUE} if nothing was planned. */
    public int storedMin(int x, int z) {
        int i = index(x, z);
        return cells[i] == null ? Integer.MAX_VALUE : base[i];
    }

    /** One above the highest y with a stored code in a core column. */
    public int storedMax(int x, int z) {
        int i = index(x, z);
        return cells[i] == null ? Integer.MIN_VALUE : base[i] + cells[i].length;
    }

    // ---- code classes ------------------------------------------------------------------------

    /** Full blocks and the solid parts of logs, boats and stonework: what holds water and carries what stands on it. */
    public static boolean isSolid(byte code) {
        return code == NAT_SOLID || code >= SAND && code <= FILL || code >= ROCK && code < ROCK + 15 || code >= LOG_X && code <= SLAB_TOP
            || code >= STAIRS && code < CHEST + 4 || code >= STONE_BRICKS && code <= BRICK_SLAB;
    }

    /** Thin things standing in water or on the ground: plants, pebbles, knee-high rocks, fences. */
    public static boolean isThin(byte code) {
        return code >= PEBBLES && code <= MOSS_CARPET || code == FENCE;
    }

    /** Wood and stonework: solid, but not terrain (nothing of the bed or rock may rest on it). */
    public static boolean isBuilt(byte code) {
        return code >= LOG_X && code < CHEST + 4 || code >= STONE_BRICKS && code <= BRICK_SLAB;
    }

    /** Codes that carry a wood species. */
    public static boolean isWood(byte code) {
        return code >= LOG_X && code < CHEST + 4;
    }

    /** True for codes that are not natural (planned by a feature). */
    public static boolean isPlanned(byte code) {
        return code != NAT_WATER && code != NAT_SOLID && code != NAT_AIR;
    }

    public static boolean isNaturalOpen(byte code) {
        return code == NAT_WATER || code == NAT_AIR;
    }
}
