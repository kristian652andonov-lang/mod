package com.terrahaul.worldgen.features.marine;

import java.util.HashMap;
import java.util.Map;

/**
 * A plan of what the marine features build around one chunk, as block codes in a sparse voxel
 * buffer. The region covers the chunk being decorated (the core) plus a margin: everything in the
 * region is rasterised from the deterministic plan alone, so the margin tells the core what its
 * neighbours will hold (for exposure, wall fans, halos) without reading other chunks, and only
 * the core is written to the world. Cells nobody wrote keep a natural code derived from the
 * planned floor: solid at or below it, water above.
 * <p>
 * The same buffer is rendered by the devtools, so what they show is what the features build.
 */
public final class MarineVolume {
    // ---- block codes --------------------------------------------------------------------------
    /** Untouched: water at or below the sea surface (air above it). */
    public static final byte NAT_WATER = 0;
    /** Untouched natural ground. */
    public static final byte NAT_SOLID = 1;
    public static final byte WATER = 2;
    public static final byte AIR = 3;
    public static final byte SAND = 4;
    public static final byte GRAVEL = 5;
    public static final byte CALCITE = 6;
    /** Reef framework; the surface pass turns it into live coral, dead coral, sand or calcite. */
    public static final byte FRAME = 7;
    public static final byte COBBLE = 8;
    public static final byte MOSSY_COBBLE = 9;
    public static final byte GRANITE = 10;
    public static final byte DIORITE = 11;
    /** Live coral block, plus species (5 codes). */
    public static final byte LIVE = 16;
    /** Dead coral block, plus species. */
    public static final byte DEAD = 21;
    public static final byte PLANT = 26;
    public static final byte FAN = 31;
    public static final byte DEAD_PLANT = 36;
    public static final byte DEAD_FAN = 41;
    /** Wall fan, plus species * 4 + facing (0 north, 1 east, 2 south, 3 west). */
    public static final byte WALL_FAN = 46;
    /** Sea pickles, plus count - 1. */
    public static final byte PICKLE = 66;
    public static final byte SEAGRASS = 70;
    public static final byte TALL_LOWER = 71;
    public static final byte TALL_UPPER = 72;
    public static final byte KELP_PLANT = 73;
    /** Kelp head that has stopped growing (canopy at the surface). */
    public static final byte KELP_TOP = 74;
    /** Kelp head that may still grow a few blocks. */
    public static final byte KELP_GROWING = 75;
    /** Rock of a family, plus family * 3 + stop (stop 0 is the top colour). */
    public static final byte ROCK = 80;
    /** Waterlogged Terrahaul pebbles, plus stone index ({@code TerrahaulBlocks.STONES} order). */
    public static final byte PEBBLES = 100;
    /** Waterlogged Terrahaul knee-high rock, plus stone index. */
    public static final byte ROCK_DECOR = 110;
    /** Glow lichen, plus the face it grows on: 0 the block above, 1-4 the wall to the north, east, south, west. */
    public static final byte GLOW_LICHEN = 120;

    /** Horizontal facings used by wall fans: north, east, south, west. */
    public static final int[] DIR_X = {0, 1, 0, -1};
    public static final int[] DIR_Z = {-1, 0, 1, 0};

    private final MarinePlanner planner;
    public final int minX;
    public final int minZ;
    public final int sizeX;
    public final int sizeZ;
    /** The core: the columns this volume is built for and written to the world. */
    public final int coreMinX;
    public final int coreMinZ;
    public final int coreSizeX;
    public final int coreSizeZ;
    private final MarineColumn[] columns;
    private final int[] base;
    private final byte[][] cells;
    private final byte[][] tints;
    private final Map<Long, MarineColumn> outside = new HashMap<>();

    public MarineVolume(MarinePlanner planner, int coreMinX, int coreMinZ, int coreSizeX, int coreSizeZ, int margin) {
        this.planner = planner;
        this.coreMinX = coreMinX;
        this.coreMinZ = coreMinZ;
        this.coreSizeX = coreSizeX;
        this.coreSizeZ = coreSizeZ;
        this.minX = coreMinX - margin;
        this.minZ = coreMinZ - margin;
        this.sizeX = coreSizeX + 2 * margin;
        this.sizeZ = coreSizeZ + 2 * margin;
        this.columns = new MarineColumn[sizeX * sizeZ];
        this.base = new int[sizeX * sizeZ];
        this.cells = new byte[sizeX * sizeZ][];
        this.tints = new byte[sizeX * sizeZ][];
    }

    public MarinePlanner planner() {
        return planner;
    }

    public boolean inRegion(int x, int z) {
        return x >= minX && x < minX + sizeX && z >= minZ && z < minZ + sizeZ;
    }

    public boolean inCore(int x, int z) {
        return x >= coreMinX && x < coreMinX + coreSizeX && z >= coreMinZ && z < coreMinZ + coreSizeZ;
    }

    private int index(int x, int z) {
        return (x - minX) * sizeZ + (z - minZ);
    }

    /** The plan of a column, computed once per volume (also for columns outside the region). */
    public MarineColumn column(int x, int z) {
        if (inRegion(x, z)) {
            int i = index(x, z);
            MarineColumn c = columns[i];
            if (c == null) {
                c = new MarineColumn();
                planner.column(x, z, c);
                columns[i] = c;
            }
            return c;
        }
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        MarineColumn c = outside.get(key);
        if (c == null) {
            c = new MarineColumn();
            planner.column(x, z, c);
            outside.put(key, c);
        }
        return c;
    }

    /** The code at a position: what was planned there, or the natural code. */
    public byte get(int x, int y, int z) {
        if (inRegion(x, z)) {
            int i = index(x, z);
            byte[] col = cells[i];
            if (col != null) {
                int k = y - base[i];
                if (k >= 0 && k < col.length) {
                    return col[k];
                }
            }
        }
        return y <= column(x, z).floor ? NAT_SOLID : NAT_WATER;
    }

    /** Sets a code; ignored outside the region. */
    public void set(int x, int y, int z, byte code) {
        if (!inRegion(x, z)) {
            return;
        }
        int i = index(x, z);
        byte[] col = ensure(i, x, y, z);
        col[y - base[i]] = code;
    }

    /** Sets a code only where the cell holds water (or a water plant) at or below the sea surface. */
    public boolean put(int x, int y, int z, byte code) {
        if (y > planner.surf || !inRegion(x, z) || !isWaterLike(get(x, y, z))) {
            return false;
        }
        set(x, y, z, code);
        return true;
    }

    /** Species hint of a framework cell: 0 none, else 1 + primary + 5 * secondary. */
    public int tint(int x, int y, int z) {
        if (!inRegion(x, z)) {
            return 0;
        }
        int i = index(x, z);
        byte[] t = tints[i];
        if (t == null) {
            return 0;
        }
        int k = y - base[i];
        return k >= 0 && k < t.length ? t[k] : 0;
    }

    public void setTint(int x, int y, int z, int tint) {
        if (!inRegion(x, z)) {
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

    private byte[] ensure(int i, int x, int y, int z) {
        byte[] col = cells[i];
        if (col != null && y >= base[i] && y < base[i] + col.length) {
            return col;
        }
        int floor = column(x, z).floor;
        int lo = col == null ? Math.min(y, floor) - 4 : Math.min(base[i], y - 4);
        int hi = col == null ? Math.max(y, floor) + 8 : Math.max(base[i] + col.length, y + 8);
        byte[] grown = new byte[hi - lo];
        for (int k = 0; k < grown.length; k++) {
            grown[k] = lo + k <= floor ? NAT_SOLID : NAT_WATER;
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

    /** Lowest y with a stored code in a column, or {@code Integer.MAX_VALUE} if nothing was planned. */
    public int storedMin(int x, int z) {
        int i = index(x, z);
        return cells[i] == null ? Integer.MAX_VALUE : base[i];
    }

    /** One above the highest y with a stored code in a column. */
    public int storedMax(int x, int z) {
        int i = index(x, z);
        return cells[i] == null ? Integer.MIN_VALUE : base[i] + cells[i].length;
    }

    // ---- code classes ------------------------------------------------------------------------

    /** Water or a plant that lives in water and may be replaced like water. */
    public static boolean isWaterLike(byte code) {
        return code == NAT_WATER || code == WATER || code >= SEAGRASS && code <= KELP_GROWING;
    }

    /** Full solid blocks. */
    public static boolean isSolid(byte code) {
        return code == NAT_SOLID || code >= SAND && code <= DIORITE || code >= LIVE && code < PLANT || code >= ROCK && code < ROCK + 15;
    }

    /** Reef materials: framework, live or dead coral, calcite and reef sand. */
    public static boolean isReef(byte code) {
        return code == FRAME || code == CALCITE || code >= LIVE && code < PLANT;
    }

    public static boolean isLive(byte code) {
        return code >= LIVE && code < DEAD;
    }

    public static boolean isDead(byte code) {
        return code >= DEAD && code < PLANT;
    }

    /** True for codes that are not natural (planned by a feature). */
    public static boolean isPlanned(byte code) {
        return code != NAT_WATER && code != NAT_SOLID;
    }
}
