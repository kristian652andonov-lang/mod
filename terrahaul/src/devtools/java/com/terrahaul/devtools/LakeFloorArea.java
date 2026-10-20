package com.terrahaul.devtools;

import com.terrahaul.worldgen.features.lake.LakeColumn;
import com.terrahaul.worldgen.features.lake.LakeLifeRaster;
import com.terrahaul.worldgen.features.lake.LakePlanner;
import com.terrahaul.worldgen.features.lake.LakeRaster;
import com.terrahaul.worldgen.features.lake.LakeVolume;
import com.terrahaul.worldgen.terrain.LakeType;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Development tool: an area of lake floor assembled chunk by chunk from the lake rasters, the way
 * the features build it in the world (each chunk writes only its own columns; the floor feature
 * first, then the plants on each column's real top), plus renderers: top view, a diver's view and
 * a vertical section. Without {@link #applyFloor} and {@link #applyLife} it shows the lake as the
 * terrain and the water leave it.
 */
final class LakeFloorArea {
    final LakePlanner planner;
    final int minX;
    final int minZ;
    final int size;
    private final LakeColumn[] columns;
    private final Map<Long, Byte> cells = new HashMap<>();
    private final Map<Long, Integer> woods = new HashMap<>();
    /** True to raster the whole area as one volume (the reference of the seam test). */
    boolean whole;

    LakeFloorArea(LakePlanner planner, int minX, int minZ, int size) {
        this.planner = planner;
        this.minX = minX;
        this.minZ = minZ;
        this.size = size;
        this.columns = new LakeColumn[size * size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                LakeColumn c = new LakeColumn();
                planner.column(minX + x, minZ + z, c);
                columns[x * size + z] = c;
            }
        }
    }

    boolean in(int x, int z) {
        return x >= minX && x < minX + size && z >= minZ && z < minZ + size;
    }

    LakeColumn column(int x, int z) {
        if (in(x, z)) {
            return columns[(x - minX) * size + (z - minZ)];
        }
        LakeColumn c = new LakeColumn();
        planner.column(x, z, c);
        return c;
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y + 2048 & 0xFFF);
    }

    static int[] unkey(long k) {
        int x = (int) (k >> 38);
        x = x << 6 >> 6;
        int z = (int) (k >> 12 & 0x3FFFFFF);
        z = z << 6 >> 6;
        int y = (int) (k & 0xFFF) - 2048;
        return new int[]{x, y, z};
    }

    byte get(int x, int y, int z) {
        Byte b = cells.get(key(x, y, z));
        if (b != null) {
            return b;
        }
        return LakeVolume.natural(column(x, z), y);
    }

    void set(int x, int y, int z, byte code) {
        cells.put(key(x, y, z), code);
    }

    Map<Long, Byte> cells() {
        return cells;
    }

    int wood(int x, int y, int z) {
        Integer w = woods.get(key(x, y, z));
        return w == null ? 0 : w;
    }

    private static boolean open(byte code) {
        return code == LakeVolume.NAT_WATER || code == LakeVolume.NAT_AIR;
    }

    // =========================================================================================
    // Assembly
    // =========================================================================================

    /** Number of planned blocks the world rules turned down while copying (should stay small). */
    int refused;

    /**
     * Runs the floor raster for every chunk of the area and copies each chunk's core with the
     * feature's write rules: the bed's top block may change, every other block only goes into
     * open water (or air) and only onto something solid, bottom up; a column stops at the first
     * block it may not place.
     */
    void applyFloor(double rockDensity, double detailDensity) {
        if (whole) {
            LakeVolume v = new LakeVolume(planner, minX, minZ, size, size);
            LakeRaster.build(v, rockDensity, detailDensity);
            copyCore(v);
            return;
        }
        for (int cx = minX; cx < minX + size; cx += 16) {
            for (int cz = minZ; cz < minZ + size; cz += 16) {
                LakeVolume v = new LakeVolume(planner, cx, cz, 16, 16);
                LakeRaster.build(v, rockDensity, detailDensity);
                copyCore(v);
            }
        }
    }

    private void copyCore(LakeVolume v) {
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                if (!in(x, z)) {
                    continue;
                }
                int lo = v.storedMin(x, z);
                int hi = v.storedMax(x, z);
                LakeColumn c = column(x, z);
                for (int y = lo; y < hi; y++) {
                    byte code = v.get(x, y, z);
                    if (!LakeVolume.isPlanned(code)) {
                        continue;
                    }
                    byte here = get(x, y, z);
                    if (y <= c.floor) {
                        if (here == LakeVolume.NAT_SOLID) {
                            set(x, y, z, code);
                            wood(v, x, y, z, code);
                        }
                        continue;
                    }
                    boolean held = LakeVolume.isSolid(get(x, y - 1, z));
                    if (!open(here) || !held) {
                        refused++;
                        break;
                    }
                    set(x, y, z, code);
                    wood(v, x, y, z, code);
                }
            }
        }
    }

    private void wood(LakeVolume v, int x, int y, int z, byte code) {
        if (LakeVolume.isWood(code)) {
            LakeVolume.Item item = v.item(x, y, z);
            woods.put(key(x, y, z), item == null ? 0 : item.wood());
        }
    }

    /** The plants, the way the life feature grows them: on each column's real top, in water sources. */
    void applyLife() {
        LakeLifeRaster.Plant plant = new LakeLifeRaster.Plant();
        for (int x = minX; x < minX + size; x++) {
            for (int z = minZ; z < minZ + size; z++) {
                LakeColumn c = column(x, z);
                if (c.wet) {
                    int top = c.floor;
                    while (top < c.surf && LakeVolume.isSolid(get(x, top + 1, z))) {
                        top++;
                    }
                    if (top == c.surf) {
                        // a trunk lying awash: moss on its dry top
                        byte g = get(x, top, z);
                        if (get(x, top + 1, z) == LakeVolume.NAT_AIR && g >= LakeVolume.LOG_X && g <= LakeVolume.STRIPPED_Y) {
                            LakeLifeRaster.decide(planner, c, top, LakeLifeRaster.WOOD, plant);
                            if (plant.kind == LakeLifeRaster.MOSS_CARPET) {
                                set(x, top + 1, z, LakeVolume.MOSS_CARPET);
                            }
                        }
                        continue;
                    }
                    if (get(x, top + 1, z) != LakeVolume.NAT_WATER) {
                        continue;
                    }
                    int kind = kind(c, get(x, top, z));
                    if (kind < 0) {
                        continue;
                    }
                    LakeLifeRaster.decide(planner, c, top, kind, plant);
                    grow(c, x, z, top, plant);
                } else {
                    shore(c, x, z, plant);
                }
            }
        }
    }

    private static int kind(LakeColumn c, byte code) {
        if (code == LakeVolume.NAT_SOLID || code == LakeVolume.FILL) {
            return LakeLifeRaster.naturalBed(c);
        }
        if (code == LakeVolume.SAND) {
            return LakeLifeRaster.SOFT;
        }
        if (code == LakeVolume.GRAVEL) {
            return LakeLifeRaster.GRAVELLY;
        }
        if (code == LakeVolume.CLAY || code == LakeVolume.MUD || code == LakeVolume.MOSS) {
            return LakeLifeRaster.MUDDY;
        }
        if (code >= LakeVolume.LOG_X && code <= LakeVolume.PLANKS) {
            return LakeLifeRaster.WOOD;
        }
        if (code == LakeVolume.SLAB || code == LakeVolume.SLAB_TOP || code >= LakeVolume.STAIRS && code < LakeVolume.CHEST + 4
            || code == LakeVolume.BRICK_SLAB) {
            // not a full face to root on
            return -1;
        }
        return LakeLifeRaster.HARD;
    }

    private void grow(LakeColumn c, int x, int z, int top, LakeLifeRaster.Plant plant) {
        switch (plant.kind) {
            case LakeLifeRaster.SEAGRASS -> set(x, top + 1, z, LakeVolume.SEAGRASS);
            case LakeLifeRaster.TALL_SEAGRASS -> {
                if (top + 2 <= c.surf && get(x, top + 2, z) == LakeVolume.NAT_WATER) {
                    set(x, top + 1, z, LakeVolume.TALL_LOWER);
                    set(x, top + 2, z, LakeVolume.TALL_UPPER);
                } else {
                    set(x, top + 1, z, LakeVolume.SEAGRASS);
                }
            }
            case LakeLifeRaster.KELP -> {
                int head = top;
                while (head < plant.head && get(x, head + 1, z) == LakeVolume.NAT_WATER) {
                    head++;
                }
                for (int y = top + 1; y <= head; y++) {
                    set(x, y, z, y < head ? LakeVolume.KELP_PLANT : plant.mature ? LakeVolume.KELP_TOP : LakeVolume.KELP_GROWING);
                }
            }
            case LakeLifeRaster.SMALL_DRIPLEAF -> {
                byte up = get(x, top + 2, z);
                if (open(up)) {
                    set(x, top + 1, z, LakeVolume.SMALL_DRIP_LOWER);
                    set(x, top + 2, z, LakeVolume.SMALL_DRIP_UPPER);
                }
            }
            case LakeLifeRaster.BIG_DRIPLEAF -> {
                boolean clear = get(x, c.surf + 1, z) == LakeVolume.NAT_AIR;
                for (int y = top + 1; y <= c.surf && clear; y++) {
                    clear = get(x, y, z) == LakeVolume.NAT_WATER;
                }
                if (clear) {
                    for (int y = top + 1; y <= c.surf; y++) {
                        set(x, y, z, LakeVolume.BIG_DRIP_STEM);
                    }
                    set(x, c.surf + 1, z, LakeVolume.BIG_DRIP_HEAD);
                }
            }
            default -> {
            }
        }
        if (plant.lily && plant.kind != LakeLifeRaster.BIG_DRIPLEAF && plant.kind != LakeLifeRaster.SMALL_DRIPLEAF
            && get(x, c.surf + 1, z) == LakeVolume.NAT_AIR && !LakeVolume.isSolid(get(x, c.surf, z))) {
            set(x, c.surf + 1, z, LakeVolume.LILY_PAD);
        }
    }

    private void shore(LakeColumn c, int x, int z, LakeLifeRaster.Plant plant) {
        int top = c.floor;
        if (get(x, top + 1, z) != LakeVolume.NAT_AIR || get(x, top, z) != LakeVolume.NAT_SOLID) {
            return;
        }
        int type = -1;
        for (int d = 0; d < 4 && type < 0; d++) {
            LakeColumn n = column(x + LakeVolume.DIR_X[d], z + LakeVolume.DIR_Z[d]);
            if (n.wet && n.surf == top && get(x + LakeVolume.DIR_X[d], top, z + LakeVolume.DIR_Z[d]) == LakeVolume.NAT_WATER) {
                type = n.type;
            }
        }
        if (type < 0) {
            return;
        }
        LakeLifeRaster.shore(planner, c, type, true, plant);
        if (plant.kind == LakeLifeRaster.REEDS) {
            for (int y = top + 1; y <= top + plant.head; y++) {
                set(x, y, z, LakeVolume.REED);
            }
        } else if (plant.kind == LakeLifeRaster.MOSS_CARPET) {
            set(x, top + 1, z, LakeVolume.MOSS_CARPET);
        }
    }

    // =========================================================================================
    // Colours
    // =========================================================================================

    static final int[][] ROCKS = {
        {108, 109, 102}, {136, 136, 137}, {125, 125, 125},     // grey: tuff, andesite, stone
        {108, 109, 102}, {72, 72, 78}, {80, 80, 82},           // dark: tuff, smooth basalt, deepslate
        {224, 224, 216}, {188, 188, 188}, {125, 125, 125},     // pale: calcite, diorite, stone
        {222, 212, 168}, {216, 203, 155}, {216, 203, 155},     // sandy
        {72, 72, 78}, {80, 80, 86}, {42, 36, 41},              // volcanic: smooth basalt, basalt, blackstone
    };
    /** Bark and planks of oak, birch, spruce, jungle, acacia, dark oak, mangrove, cherry. */
    static final int[][] BARK = {{109, 85, 50}, {216, 215, 210}, {58, 37, 16}, {85, 67, 25}, {103, 96, 86}, {60, 46, 26}, {84, 66, 36}, {54, 33, 44}};
    static final int[][] PLANK = {{162, 130, 78}, {196, 176, 118}, {114, 84, 48}, {160, 115, 80}, {168, 90, 50}, {66, 43, 20}, {117, 54, 48}, {226, 178, 172}};

    /** The bed colour of an untouched lake floor (the surface rule's sediment, roughly). */
    static int[] bed(LakeColumn c) {
        return switch (LakeLifeRaster.naturalBed(c)) {
            case LakeLifeRaster.MUDDY -> c.type == LakeType.MARSH || c.temperature > 0.3 ? new int[]{70, 62, 58} : new int[]{160, 166, 179};
            case LakeLifeRaster.GRAVELLY -> new int[]{140, 132, 128};
            default -> new int[]{214, 204, 160};
        };
    }

    int[] colour(int x, int y, int z, byte code) {
        LakeColumn c = column(x, z);
        if (code == LakeVolume.NAT_SOLID) {
            if (!c.wet || y > c.surf) {
                return y == c.floor ? (c.temperature < -0.45 ? new int[]{235, 240, 245} : new int[]{92, 140, 64}) : new int[]{120, 104, 88};
            }
            return bed(c);
        }
        if (code == LakeVolume.FILL) {
            return bed(c);
        }
        if (code >= LakeVolume.ROCK && code < LakeVolume.ROCK + 15) {
            return ROCKS[code - LakeVolume.ROCK];
        }
        if (code >= LakeVolume.PEBBLES && code < LakeVolume.ROCK_DECOR + 10) {
            return new int[]{112, 112, 112};
        }
        if (LakeVolume.isWood(code) && code != LakeVolume.CHEST && code < LakeVolume.CHEST) {
            int w = wood(x, y, z);
            boolean log = code >= LakeVolume.LOG_X && code <= LakeVolume.LOG_Y;
            boolean stripped = code >= LakeVolume.STRIPPED_X && code <= LakeVolume.STRIPPED_Y;
            return log ? BARK[w] : stripped ? SeaFloorArea.shade(PLANK[w], 1.1) : PLANK[w];
        }
        if (code >= LakeVolume.CHEST && code < LakeVolume.CHEST + 4) {
            return new int[]{170, 115, 40};
        }
        return switch (code) {
            case LakeVolume.SAND -> new int[]{219, 207, 160};
            case LakeVolume.GRAVEL -> new int[]{140, 132, 128};
            case LakeVolume.CLAY -> new int[]{160, 166, 179};
            case LakeVolume.MUD -> new int[]{62, 56, 60};
            case LakeVolume.MOSS -> new int[]{89, 112, 45};
            case LakeVolume.COBBLE -> new int[]{122, 122, 122};
            case LakeVolume.MOSSY_COBBLE -> new int[]{98, 122, 80};
            case LakeVolume.GRANITE -> new int[]{154, 108, 90};
            case LakeVolume.DIORITE -> new int[]{196, 196, 196};
            case LakeVolume.SEAGRASS -> new int[]{62, 150, 52};
            case LakeVolume.TALL_LOWER, LakeVolume.TALL_UPPER -> new int[]{48, 128, 44};
            case LakeVolume.KELP_PLANT -> new int[]{86, 120, 34};
            case LakeVolume.KELP_TOP, LakeVolume.KELP_GROWING -> new int[]{110, 150, 40};
            case LakeVolume.LILY_PAD -> new int[]{30, 150, 40};
            case LakeVolume.REED -> new int[]{148, 196, 98};
            case LakeVolume.SMALL_DRIP_LOWER, LakeVolume.SMALL_DRIP_UPPER -> new int[]{92, 160, 52};
            case LakeVolume.BIG_DRIP_STEM -> new int[]{80, 130, 40};
            case LakeVolume.BIG_DRIP_HEAD -> new int[]{110, 190, 60};
            case LakeVolume.MOSS_CARPET -> new int[]{89, 112, 45};
            case LakeVolume.STONE_BRICKS -> new int[]{122, 121, 122};
            case LakeVolume.MOSSY_BRICKS -> new int[]{110, 122, 98};
            case LakeVolume.CRACKED_BRICKS -> new int[]{112, 111, 112};
            case LakeVolume.BRICK_SLAB -> new int[]{130, 129, 130};
            default -> new int[]{255, 0, 255};
        };
    }

    /** Lily pads, moss carpets and dripleaf heads: drawn as a thin plate at {@link #flatHeight}. */
    static boolean flat(byte code) {
        return code == LakeVolume.LILY_PAD || code == LakeVolume.MOSS_CARPET || code == LakeVolume.BIG_DRIP_HEAD;
    }

    static double flatHeight(byte code) {
        return code == LakeVolume.BIG_DRIP_HEAD ? 0.75 : 0.06;
    }

    /** Whether a ray at {@code (oy + dy * t)} crosses a flat code's plate in the cell between {@code t0} and {@code t1}. */
    static boolean hitsPlate(byte code, int y, double oy, double dy, double t0, double t1) {
        if (dy == 0.0) {
            return false;
        }
        double tp = (y + flatHeight(code) - oy) / dy;
        return tp >= t0 - 1.0E-9 && tp <= t1 + 1.0E-9;
    }

    /**
     * Whether a ray crosses the stem of a thin plant in the cell {@code (x, z)} between {@code t0}
     * and {@code t1}: plants are drawn as upright sticks, so the floor shows between them.
     */
    static boolean hitsStem(byte code, int x, int z, double ox, double oz, double dx, double dz, double t0, double t1) {
        double r = code == LakeVolume.TALL_LOWER || code == LakeVolume.TALL_UPPER || code == LakeVolume.REED ? 0.3 : 0.2;
        if (code >= LakeVolume.PEBBLES && code < LakeVolume.ROCK_DECOR + 10) {
            r = 0.35;
        }
        double cx = x + 0.5;
        double cz = z + 0.5;
        double h = dx * dx + dz * dz;
        double t = h < 1.0E-12 ? t0 : -((ox - cx) * dx + (oz - cz) * dz) / h;
        t = Math.max(t0, Math.min(t1, t));
        double px = ox + dx * t - cx;
        double pz = oz + dz * t - cz;
        return px * px + pz * pz < r * r;
    }

    static boolean thin(byte code) {
        return LakeVolume.isThin(code) || code == LakeVolume.BIG_DRIP_STEM;
    }

    static boolean visible(byte code) {
        return code != LakeVolume.NAT_WATER && code != LakeVolume.NAT_AIR;
    }

    static boolean opaque(byte code) {
        return visible(code) && !thin(code);
    }

    // =========================================================================================
    // Renderers
    // =========================================================================================

    private int columnTop(int x, int z) {
        LakeColumn c = column(x, z);
        int y = Math.max(c.floor, c.wet ? c.surf : c.floor) + 10;
        while (y > c.floor - 4 && !visible(get(x, y, z))) {
            y--;
        }
        return y;
    }

    /** Top view, {@code scale} pixels per block: the first block from above, under water tinted with depth. */
    BufferedImage top(int ax, int az, int n, int scale) {
        BufferedImage img = new BufferedImage(n * scale, n * scale, BufferedImage.TYPE_INT_RGB);
        for (int x = ax; x < ax + n; x++) {
            for (int z = az; z < az + n; z++) {
                LakeColumn c = column(x, z);
                int y = columnTop(x, z);
                int[] col = colour(x, y, z, get(x, y, z));
                double west = y - columnTop(x - 1, z);
                col = SeaFloorArea.shade(col, 1.0 + Math.max(-0.25, Math.min(0.25, west * 0.08)));
                int surf = c.wet ? c.surf : Integer.MIN_VALUE;
                if (y <= surf) {
                    // clear lake water: the floor shows through the first few blocks, then blue
                    double w = Math.min(0.82, 0.12 + (surf - y) / 22.0);
                    int[] tint = c.temperature > 0.4 ? new int[]{40, 110, 120} : new int[]{24, 66, 130};
                    col = new int[]{(int) (col[0] * (1 - w) + tint[0] * w), (int) (col[1] * (1 - w) + tint[1] * w), (int) (col[2] * (1 - w) + tint[2] * w)};
                }
                for (int i = 0; i < scale; i++) {
                    for (int j = 0; j < scale; j++) {
                        img.setRGB((x - ax) * scale + i, (z - az) * scale + j, SeaFloorArea.rgb(col));
                    }
                }
            }
        }
        return img;
    }

    /**
     * Isometric view of a square area seen from the south-east and above; water is drawn as a
     * light tint over what lies under it, so the floor and everything on it shows.
     */
    BufferedImage iso(int ax, int az, int n, int s, int yLow, int yHigh) {
        int w = 2 * n * s + 2 * s + 20;
        int h = n * s + (yHigh - yLow + 2) * s + 20;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int bg = SeaFloorArea.rgb(new int[]{200, 222, 240});
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                img.setRGB(i, j, bg);
            }
        }
        int ox = n * s + 10;
        int oy = (yHigh - yLow + 1) * s + 10;
        for (int sum = 0; sum <= 2 * (n - 1); sum++) {
            for (int y = yLow; y <= yHigh; y++) {
                for (int dx = Math.max(0, sum - n + 1); dx <= Math.min(n - 1, sum); dx++) {
                    int dz = sum - dx;
                    int x = ax + dx;
                    int z = az + dz;
                    byte code = get(x, y, z);
                    if (!visible(code)) {
                        continue;
                    }
                    if (!thin(code) && dx < n - 1 && dz < n - 1 && y < yHigh && opaque(get(x, y + 1, z)) && opaque(get(x + 1, y, z)) && opaque(get(x, y, z + 1))) {
                        continue;
                    }
                    int[] col = colour(x, y, z, code);
                    LakeColumn c = column(x, z);
                    if (c.wet && y <= c.surf) {
                        double f = Math.min(0.45, 0.12 + (c.surf - y) / 60.0);
                        col = new int[]{(int) (col[0] * (1 - f) + 30 * f), (int) (col[1] * (1 - f) + 80 * f), (int) (col[2] * (1 - f) + 160 * f)};
                    }
                    int sx = ox + (dx - dz) * s;
                    int sy = oy + (dx + dz) * s / 2 - (y - yLow) * s;
                    if (thin(code) && !flat(code)) {
                        sprite(img, sx, sy, s, SeaFloorArea.rgb(col));
                    } else {
                        cube(img, sx, sy, s, col, flat(code));
                    }
                }
            }
        }
        return img;
    }

    private static void cube(BufferedImage img, int sx, int sy, int s, int[] c, boolean plate) {
        int top = SeaFloorArea.rgb(c);
        int left = SeaFloorArea.rgb(SeaFloorArea.shade(c, 0.78));
        int right = SeaFloorArea.rgb(SeaFloorArea.shade(c, 0.6));
        int hs = s / 2;
        int drop = plate ? s - 1 : 0;
        for (int j = -hs; j <= hs; j++) {
            int half = s - Math.abs(j) * 2;
            for (int i = -half; i <= half; i++) {
                put(img, sx + i, sy + j + drop, top);
            }
        }
        if (plate) {
            return;
        }
        for (int i = 0; i <= s; i++) {
            int edge = sy + i / 2;
            for (int j = 1; j <= s; j++) {
                put(img, sx - s + i, edge + j, left);
                put(img, sx + s - i, edge + j, right);
            }
        }
    }

    private static void sprite(BufferedImage img, int sx, int sy, int s, int c) {
        for (int i = -s / 4; i <= s / 4; i++) {
            for (int j = -s / 2; j <= s / 2; j++) {
                if ((i + j & 1) == 0 || Math.abs(i) < s / 6 + 1) {
                    put(img, sx + i, sy + j + s / 2, c);
                }
            }
        }
    }

    private static void put(BufferedImage img, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, c);
        }
    }

    /** Vertical section along x at {@code z}, {@code scale} pixels per block. */
    BufferedImage section(int ax, int n, int z, int yLow, int yHigh, int scale) {
        BufferedImage img = new BufferedImage(n * scale, (yHigh - yLow + 1) * scale, BufferedImage.TYPE_INT_RGB);
        for (int x = ax; x < ax + n; x++) {
            for (int y = yLow; y <= yHigh; y++) {
                byte code = get(x, y, z);
                int[] col = visible(code) ? colour(x, y, z, code) : code == LakeVolume.NAT_WATER ? new int[]{40, 100, 180} : new int[]{205, 225, 245};
                for (int i = 0; i < scale; i++) {
                    for (int j = 0; j < scale; j++) {
                        img.setRGB((x - ax) * scale + i, (yHigh - y) * scale + j, SeaFloorArea.rgb(col));
                    }
                }
            }
        }
        return img;
    }

    /**
     * What a diver sees: a ray-cast view from {@code (cx, cy, cz)} along {@code (dx, dz)} tilted by
     * {@code pitch} degrees, with lake-water fog (green-brown in warm lakes, blue in cold ones),
     * shaded faces and light falling off with depth; above the surface, the sky. Plants are drawn
     * as thin, partly transparent blocks.
     */
    BufferedImage perspective(double cx, double cy, double cz, double dirX, double dirZ, double pitch, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double len = Math.hypot(dirX, dirZ);
        double fx = dirX / len;
        double fz = dirZ / len;
        double p = Math.toRadians(pitch);
        double[] fwd = {fx * Math.cos(p), Math.sin(p), fz * Math.cos(p)};
        double[] right = {-fz, 0, fx};
        double[] up = {fwd[1] * right[2] - fwd[2] * right[1], fwd[2] * right[0] - fwd[0] * right[2], fwd[0] * right[1] - fwd[1] * right[0]};
        if (up[1] < 0) {
            up = new double[]{-up[0], -up[1], -up[2]};
        }
        double tanHalf = Math.tan(Math.toRadians(35.0));
        LakeColumn here = column((int) Math.floor(cx), (int) Math.floor(cz));
        int[] fog = here.temperature > 0.35 ? new int[]{46, 104, 96} : here.temperature > -0.2 ? new int[]{34, 92, 128} : new int[]{30, 78, 132};
        double fogDist = here.temperature > 0.35 ? 26.0 : 34.0;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                double sx = (2.0 * (px + 0.5) / w - 1.0) * tanHalf * w / h;
                double sy = (1.0 - 2.0 * (py + 0.5) / h) * tanHalf;
                double rx = fwd[0] + sx * right[0] + sy * up[0];
                double ry = fwd[1] + sx * right[1] + sy * up[1];
                double rz = fwd[2] + sx * right[2] + sy * up[2];
                double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
                img.setRGB(px, py, SeaFloorArea.rgb(trace(cx, cy, cz, rx / rl, ry / rl, rz / rl, fog, fogDist)));
            }
        }
        return img;
    }

    private int[] trace(double ox, double oy, double oz, double dx, double dy, double dz, int[] fog, double fogDist) {
        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double tMaxX = dx != 0 ? ((dx > 0 ? x + 1 - ox : ox - x) / Math.abs(dx)) : Double.MAX_VALUE;
        double tMaxY = dy != 0 ? ((dy > 0 ? y + 1 - oy : oy - y) / Math.abs(dy)) : Double.MAX_VALUE;
        double tMaxZ = dz != 0 ? ((dz > 0 ? z + 1 - oz : oz - z) / Math.abs(dz)) : Double.MAX_VALUE;
        double tdx = dx != 0 ? 1.0 / Math.abs(dx) : Double.MAX_VALUE;
        double tdy = dy != 0 ? 1.0 / Math.abs(dy) : Double.MAX_VALUE;
        double tdz = dz != 0 ? 1.0 / Math.abs(dz) : Double.MAX_VALUE;
        double t = 0.0;
        int face = 1;
        int[] plant = null;
        double plantT = 0.0;
        int surf = column(x, z).surf;
        while (t < fogDist * 1.6) {
            byte code = get(x, y, z);
            if (code == LakeVolume.NAT_AIR) {
                // out of the water: the sky through the surface
                return blend(new int[]{170, 205, 235}, fog, Math.min(1.0, t / fogDist), plant, plantT, fogDist);
            }
            if (visible(code)) {
                int[] c = colour(x, y, z, code);
                if (flat(code)) {
                    if (plant == null && hitsPlate(code, y, oy, dy, t, Math.min(tMaxX, Math.min(tMaxY, tMaxZ)))) {
                        plant = SeaFloorArea.shade(c, 0.6);
                        plantT = t;
                    }
                } else if (thin(code)) {
                    if (hitsStem(code, x, z, ox, oz, dx, dz, t, Math.min(tMaxX, Math.min(tMaxY, tMaxZ)))) {
                        double depthLight = Math.max(0.4, 1.0 - (surf - y) / 45.0);
                        return blend(SeaFloorArea.shade(c, 0.9 * depthLight), fog, Math.min(1.0, t / fogDist), plant, plantT, fogDist);
                    }
                } else {
                    double shade = face == 1 ? 1.0 : face == 0 ? 0.78 : face == 2 ? 0.65 : 0.5;
                    double depthLight = Math.max(0.4, 1.0 - (surf - y) / 45.0);
                    return blend(SeaFloorArea.shade(c, shade * depthLight), fog, Math.min(1.0, t / fogDist), plant, plantT, fogDist);
                }
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tdx;
                face = 0;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tdy;
                face = dy < 0 ? 1 : 3;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tdz;
                face = 2;
            }
        }
        return blend(fog, fog, 1.0, plant, plantT, fogDist);
    }

    private static int[] blend(int[] c, int[] fog, double f, int[] plant, double plantT, double fogDist) {
        f = f * f * (3 - 2 * f);
        int[] out = {(int) (c[0] * (1 - f) + fog[0] * f), (int) (c[1] * (1 - f) + fog[1] * f), (int) (c[2] * (1 - f) + fog[2] * f)};
        if (plant != null) {
            double pf = Math.min(1.0, plantT / fogDist);
            pf = pf * pf * (3 - 2 * pf);
            int[] pc = {(int) (plant[0] * (1 - pf) + fog[0] * pf), (int) (plant[1] * (1 - pf) + fog[1] * pf), (int) (plant[2] * (1 - pf) + fog[2] * pf)};
            out = new int[]{(int) (out[0] * 0.4 + pc[0] * 0.6), (int) (out[1] * 0.4 + pc[1] * 0.6), (int) (out[2] * 0.4 + pc[2] * 0.6)};
        }
        return out;
    }

    /**
     * A view from just above the surface, like the player's: lily pads, reeds, rocks and posts
     * standing out of the water, the floor seen through the clear water near the shore.
     */
    BufferedImage surfaceView(double cx, double cy, double cz, double dirX, double dirZ, double pitch, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double len = Math.hypot(dirX, dirZ);
        double fx = dirX / len;
        double fz = dirZ / len;
        double p = Math.toRadians(pitch);
        double[] fwd = {fx * Math.cos(p), Math.sin(p), fz * Math.cos(p)};
        double[] right = {-fz, 0, fx};
        double[] up = {fwd[1] * right[2] - fwd[2] * right[1], fwd[2] * right[0] - fwd[0] * right[2], fwd[0] * right[1] - fwd[1] * right[0]};
        if (up[1] < 0) {
            up = new double[]{-up[0], -up[1], -up[2]};
        }
        double tanHalf = Math.tan(Math.toRadians(35.0));
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                double sx = (2.0 * (px + 0.5) / w - 1.0) * tanHalf * w / h;
                double sy = (1.0 - 2.0 * (py + 0.5) / h) * tanHalf;
                double rx = fwd[0] + sx * right[0] + sy * up[0];
                double ry = fwd[1] + sx * right[1] + sy * up[1];
                double rz = fwd[2] + sx * right[2] + sy * up[2];
                double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
                img.setRGB(px, py, SeaFloorArea.rgb(traceAir(cx, cy, cz, rx / rl, ry / rl, rz / rl)));
            }
        }
        return img;
    }

    private int[] traceAir(double ox, double oy, double oz, double dx, double dy, double dz) {
        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double tMaxX = dx != 0 ? ((dx > 0 ? x + 1 - ox : ox - x) / Math.abs(dx)) : Double.MAX_VALUE;
        double tMaxY = dy != 0 ? ((dy > 0 ? y + 1 - oy : oy - y) / Math.abs(dy)) : Double.MAX_VALUE;
        double tMaxZ = dz != 0 ? ((dz > 0 ? z + 1 - oz : oz - z) / Math.abs(dz)) : Double.MAX_VALUE;
        double tdx = dx != 0 ? 1.0 / Math.abs(dx) : Double.MAX_VALUE;
        double tdy = dy != 0 ? 1.0 / Math.abs(dy) : Double.MAX_VALUE;
        double tdz = dz != 0 ? 1.0 / Math.abs(dz) : Double.MAX_VALUE;
        double t = 0.0;
        int face = 1;
        double waterT = -1.0;
        int[] plant = null;
        int[] sky = {165, 200, 240};
        int[] haze = {190, 210, 230};
        while (t < 220.0) {
            byte code = get(x, y, z);
            if (code == LakeVolume.NAT_WATER && waterT < 0.0) {
                waterT = t;
            }
            if (visible(code)) {
                int[] c = colour(x, y, z, code);
                double tExit = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
                boolean solid = !thin(code) && !flat(code) || thin(code) && !flat(code) && hitsStem(code, x, z, ox, oz, dx, dz, t, tExit);
                boolean plate = flat(code) && hitsPlate(code, y, oy, dy, t, tExit);
                if (solid || plate) {
                    double shade = plate ? 1.0 : face == 1 ? 1.0 : face == 0 ? 0.8 : face == 2 ? 0.68 : 0.55;
                    c = SeaFloorArea.shade(c, shade);
                    if (waterT >= 0.0) {
                        // seen through the water: blue with the distance travelled in it
                        double wf = Math.min(1.0, 0.25 + (t - waterT) / 9.0);
                        c = mix(c, new int[]{38, 84, 150}, wf);
                    }
                    c = mix(c, haze, Math.min(0.85, t / 240.0));
                    return plant == null ? c : mix(c, plant, 0.55);
                }

            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tdx;
                face = 0;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tdy;
                face = dy < 0 ? 1 : 3;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tdz;
                face = 2;
            }
            if (y < -64 || y > 700) {
                break;
            }
        }
        int[] end = waterT >= 0.0 ? mix(new int[]{38, 84, 150}, haze, Math.min(0.85, waterT / 240.0)) : sky;
        return plant == null ? end : mix(end, plant, 0.55);
    }

    private static int[] mix(int[] a, int[] b, double f) {
        return new int[]{(int) (a[0] * (1 - f) + b[0] * f), (int) (a[1] * (1 - f) + b[1] * f), (int) (a[2] * (1 - f) + b[2] * f)};
    }
}
