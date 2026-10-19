package com.terrahaul.devtools;

import com.terrahaul.worldgen.features.marine.MarineColumn;
import com.terrahaul.worldgen.features.marine.MarinePlanner;
import com.terrahaul.worldgen.features.marine.MarineVolume;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Development tool: an area of sea floor assembled chunk by chunk from the marine rasters, the
 * way the features build it in the world (each chunk writes only its own columns, in feature step
 * order), plus renderers: top view, isometric view and vertical section.
 */
final class SeaFloorArea {
    final MarinePlanner planner;
    final int minX;
    final int minZ;
    final int size;
    private final MarineColumn[] columns;
    private final Map<Long, Byte> cells = new HashMap<>();

    SeaFloorArea(MarinePlanner planner, int minX, int minZ, int size) {
        this.planner = planner;
        this.minX = minX;
        this.minZ = minZ;
        this.size = size;
        this.columns = new MarineColumn[size * size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                MarineColumn c = new MarineColumn();
                planner.column(minX + x, minZ + z, c);
                columns[x * size + z] = c;
            }
        }
    }

    boolean in(int x, int z) {
        return x >= minX && x < minX + size && z >= minZ && z < minZ + size;
    }

    MarineColumn column(int x, int z) {
        if (in(x, z)) {
            return columns[(x - minX) * size + (z - minZ)];
        }
        MarineColumn c = new MarineColumn();
        planner.column(x, z, c);
        return c;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y + 2048 & 0xFFF);
    }

    byte get(int x, int y, int z) {
        Byte b = cells.get(key(x, y, z));
        if (b != null) {
            return b;
        }
        return y <= column(x, z).floor ? MarineVolume.NAT_SOLID : MarineVolume.NAT_WATER;
    }

    void set(int x, int y, int z, byte code) {
        cells.put(key(x, y, z), code);
    }

    /**
     * Runs a raster for every chunk of the area and copies each chunk's core into the area with
     * the features' write rules: solid codes only replace water (or water plants), plants only go
     * into water above a solid block, carved water and air always apply.
     */
    void apply(int margin, Consumer<MarineVolume> raster) {
        if (whole) {
            MarineVolume v = new MarineVolume(planner, minX, minZ, size, size, margin);
            raster.accept(v);
            copyCore(v);
            return;
        }
        for (int cx = minX; cx < minX + size; cx += 16) {
            for (int cz = minZ; cz < minZ + size; cz += 16) {
                MarineVolume v = new MarineVolume(planner, cx, cz, 16, 16, margin);
                raster.accept(v);
                copyCore(v);
            }
        }
    }

    /** True to raster the whole area as one volume (the reference of the seam test). */
    boolean whole;

    /** Like {@link #apply}, for grotto volumes: dug-out blocks and what is placed in them always apply. */
    void applyDug(int margin, Consumer<MarineVolume> raster) {
        apply(margin, v -> {
            raster.accept(v);
            for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
                for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                    int hi = v.storedMax(x, z);
                    for (int y = v.storedMin(x, z); y < hi; y++) {
                        byte code = v.get(x, y, z);
                        if (in(x, z) && com.terrahaul.worldgen.features.marine.GrottoPlan.dug(code)) {
                            // dig first, so the plants placed in dug blocks pass the water test
                            set(x, y, z, y <= planner.surf ? MarineVolume.WATER : MarineVolume.AIR);
                        }
                    }
                }
            }
        });
    }

    /**
     * Kelp and seagrass the way the vegetation feature grows them: on each column's real top (the
     * floor or a rock on it), outside reef columns.
     */
    void applyVegetation() {
        com.terrahaul.worldgen.features.marine.SeaLifeRaster.Plant plant = new com.terrahaul.worldgen.features.marine.SeaLifeRaster.Plant();
        for (int x = minX; x < minX + size; x++) {
            for (int z = minZ; z < minZ + size; z++) {
                MarineColumn c = column(x, z);
                if (!c.sea || c.depth > 44 || com.terrahaul.worldgen.features.marine.SeaLifeRaster.reefColumn(c)) {
                    continue;
                }
                int top = c.floor;
                while (top < planner.surf && MarineVolume.isSolid(get(x, top + 1, z))) {
                    top++;
                }
                if (get(x, top + 1, z) != MarineVolume.NAT_WATER || !MarineVolume.isSolid(get(x, top, z))) {
                    continue;
                }
                byte ground = get(x, top, z);
                int kind = ground == MarineVolume.NAT_SOLID ? com.terrahaul.worldgen.features.marine.SeaLifeRaster.naturalFloor(c)
                    : ground == MarineVolume.GRAVEL ? com.terrahaul.worldgen.features.marine.SeaLifeRaster.GRAVELLY
                    : ground == MarineVolume.SAND ? com.terrahaul.worldgen.features.marine.SeaLifeRaster.SOFT
                    : com.terrahaul.worldgen.features.marine.SeaLifeRaster.HARD;
                com.terrahaul.worldgen.features.marine.SeaLifeRaster.decide(planner, c, top, kind, plant);
                switch (plant.kind) {
                    case com.terrahaul.worldgen.features.marine.SeaLifeRaster.SEAGRASS -> set(x, top + 1, z, MarineVolume.SEAGRASS);
                    case com.terrahaul.worldgen.features.marine.SeaLifeRaster.TALL_SEAGRASS -> {
                        if (get(x, top + 2, z) == MarineVolume.NAT_WATER && top + 2 <= planner.surf) {
                            set(x, top + 1, z, MarineVolume.TALL_LOWER);
                            set(x, top + 2, z, MarineVolume.TALL_UPPER);
                        } else {
                            set(x, top + 1, z, MarineVolume.SEAGRASS);
                        }
                    }
                    case com.terrahaul.worldgen.features.marine.SeaLifeRaster.KELP -> {
                        int head = top;
                        while (head < plant.head && get(x, head + 1, z) == MarineVolume.NAT_WATER) {
                            head++;
                        }
                        for (int y = top + 1; y <= head; y++) {
                            set(x, y, z, y < head ? MarineVolume.KELP_PLANT : plant.mature && head == plant.head ? MarineVolume.KELP_TOP : MarineVolume.KELP_GROWING);
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    /** Like the reef feature: leave columns alone that hold something else than water above the floor. */
    boolean guarded;

    void copyCore(MarineVolume v) {
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                if (!in(x, z)) {
                    continue;
                }
                int hi = v.storedMax(x, z);
                if (guarded) {
                    int top = Integer.MIN_VALUE;
                    for (int y = v.storedMin(x, z); y < hi; y++) {
                        if (MarineVolume.isPlanned(v.get(x, y, z))) {
                            top = y;
                        }
                    }
                    boolean clear = true;
                    for (int y = column(x, z).floor + 1; y <= top; y++) {
                        if (!MarineVolume.isWaterLike(get(x, y, z)) && !(get(x, y, z) >= MarineVolume.PEBBLES && get(x, y, z) < MarineVolume.GLOW_LICHEN)) {
                            clear = false;
                        }
                    }
                    if (!clear) {
                        continue;
                    }
                }
                for (int y = v.storedMin(x, z); y < hi; y++) {
                    byte code = v.get(x, y, z);
                    if (!MarineVolume.isPlanned(code)) {
                        continue;
                    }
                    byte here = get(x, y, z);
                    if (thin(code)) {
                        // plants: into water only, on what they need (as the features check in the world)
                        byte below = get(x, y - 1, z);
                        boolean wet = here == MarineVolume.NAT_WATER || here == MarineVolume.WATER || here == MarineVolume.AIR;
                        boolean held;
                        if (code >= MarineVolume.WALL_FAN && code < MarineVolume.PICKLE) {
                            int k = (code - MarineVolume.WALL_FAN) % 4;
                            held = MarineVolume.isSolid(get(x - MarineVolume.DIR_X[k], y, z - MarineVolume.DIR_Z[k]));
                        } else if (code >= MarineVolume.GLOW_LICHEN && code < MarineVolume.GLOW_LICHEN + 5) {
                            int k = code - MarineVolume.GLOW_LICHEN;
                            held = k == 0 ? MarineVolume.isSolid(get(x, y + 1, z)) : MarineVolume.isSolid(get(x + MarineVolume.DIR_X[k - 1], y, z + MarineVolume.DIR_Z[k - 1]));
                        } else {
                            held = MarineVolume.isSolid(below) || code == MarineVolume.TALL_UPPER && below == MarineVolume.TALL_LOWER
                                || code >= MarineVolume.KELP_PLANT && code <= MarineVolume.KELP_GROWING && below == MarineVolume.KELP_PLANT;
                        }
                        if (wet && held) {
                            set(x, y, z, code);
                        }
                    } else if (code == MarineVolume.WATER || code == MarineVolume.AIR || MarineVolume.isWaterLike(here) || writesFloor(code) && here == MarineVolume.NAT_SOLID) {
                        if (guarded && MarineVolume.isLive(code) && !wet(v, x, y, z)) {
                            code = (byte) (code - MarineVolume.LIVE + MarineVolume.DEAD);
                        }
                        set(x, y, z, code);
                    }
                }
            }
        }
    }

    /** The reef emitter's test: a neighbour planned as water that is still water here. */
    private boolean wet(MarineVolume v, int x, int y, int z) {
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
            byte planned = v.get(x + d[0], y + d[1], z + d[2]);
            boolean water = MarineVolume.isWaterLike(planned) || planned >= MarineVolume.PLANT && planned < MarineVolume.ROCK;
            if (water && y + d[1] <= planner.surf && MarineVolume.isWaterLike(get(x + d[0], y + d[1], z + d[2]))) {
                return true;
            }
        }
        return false;
    }

    private static boolean writesFloor(byte code) {
        return MarineVolume.isSolid(code);
    }

    /** Copies everything a volume planned for the whole region (seam test reference). */
    void copyAll(MarineVolume v) {
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                int hi = v.storedMax(x, z);
                for (int y = v.storedMin(x, z); y < hi; y++) {
                    byte code = v.get(x, y, z);
                    if (MarineVolume.isPlanned(code)) {
                        set(x, y, z, code);
                    }
                }
            }
        }
    }

    Map<Long, Byte> cells() {
        return cells;
    }

    // =========================================================================================
    // Colours
    // =========================================================================================

    static final int[][] CORAL = {{52, 92, 214}, {214, 94, 166}, {165, 40, 170}, {196, 44, 50}, {228, 205, 62}};

    int[] colour(int x, int y, int z, byte code) {
        if (code == MarineVolume.NAT_SOLID) {
            MarineColumn c = column(x, z);
            if (c.floor >= planner.surf) {
                return y == c.floor ? new int[]{96, 150, 72} : new int[]{120, 104, 88};
            }
            boolean warm = c.temperature > 0.5;
            if (c.depth < 14 && warm) {
                return new int[]{214, 200, 150};
            }
            return c.depth < 70 ? new int[]{150, 140, 128} : new int[]{120, 112, 110};
        }
        if (code >= MarineVolume.LIVE && code < MarineVolume.DEAD) {
            return CORAL[code - MarineVolume.LIVE];
        }
        if (code >= MarineVolume.DEAD && code < MarineVolume.PLANT) {
            return new int[]{132, 126, 120};
        }
        if (code >= MarineVolume.PLANT && code < MarineVolume.FAN) {
            return shade(CORAL[code - MarineVolume.PLANT], 1.2);
        }
        if (code >= MarineVolume.FAN && code < MarineVolume.DEAD_PLANT) {
            return shade(CORAL[code - MarineVolume.FAN], 1.3);
        }
        if (code >= MarineVolume.DEAD_PLANT && code < MarineVolume.WALL_FAN) {
            return new int[]{150, 145, 140};
        }
        if (code >= MarineVolume.WALL_FAN && code < MarineVolume.PICKLE) {
            return shade(CORAL[(code - MarineVolume.WALL_FAN) / 4], 1.3);
        }
        if (code >= MarineVolume.PICKLE && code < MarineVolume.SEAGRASS) {
            return new int[]{190, 240, 110};
        }
        if (code >= MarineVolume.ROCK && code < MarineVolume.ROCK + 15) {
            return ROCKS[code - MarineVolume.ROCK];
        }
        if (code >= MarineVolume.PEBBLES && code < MarineVolume.PEBBLES + 10) {
            return new int[]{110, 110, 110};
        }
        if (code >= MarineVolume.ROCK_DECOR && code < MarineVolume.ROCK_DECOR + 10) {
            return new int[]{118, 118, 118};
        }
        return switch (code) {
            case MarineVolume.SAND -> new int[]{219, 207, 160};
            case MarineVolume.GRAVEL -> new int[]{140, 132, 128};
            case MarineVolume.CALCITE -> new int[]{224, 224, 216};
            case MarineVolume.FRAME -> new int[]{255, 0, 255};
            case MarineVolume.COBBLE -> new int[]{122, 122, 122};
            case MarineVolume.MOSSY_COBBLE -> new int[]{98, 122, 80};
            case MarineVolume.GRANITE -> new int[]{154, 108, 90};
            case MarineVolume.DIORITE -> new int[]{196, 196, 196};
            case MarineVolume.SEAGRASS -> new int[]{62, 150, 52};
            case MarineVolume.TALL_LOWER, MarineVolume.TALL_UPPER -> new int[]{48, 128, 44};
            case MarineVolume.KELP_PLANT -> new int[]{86, 120, 34};
            case MarineVolume.KELP_TOP, MarineVolume.KELP_GROWING -> new int[]{110, 150, 40};
            case MarineVolume.GLOW_LICHEN, MarineVolume.GLOW_LICHEN + 1, MarineVolume.GLOW_LICHEN + 2, MarineVolume.GLOW_LICHEN + 3,
                MarineVolume.GLOW_LICHEN + 4 -> new int[]{150, 190, 160};
            case MarineVolume.WATER -> new int[]{40, 90, 200};
            case MarineVolume.AIR -> new int[]{230, 240, 250};
            default -> new int[]{0, 0, 0};
        };
    }

    static final int[][] ROCKS = {
        {108, 109, 102}, {136, 136, 137}, {125, 125, 125},     // grey: tuff, andesite, stone
        {108, 109, 102}, {72, 72, 78}, {80, 80, 82},           // dark: tuff, smooth basalt, deepslate
        {224, 224, 216}, {188, 188, 188}, {125, 125, 125},     // pale: calcite, diorite, stone
        {222, 212, 168}, {216, 203, 155}, {216, 203, 155},     // sandy
        {42, 36, 41}, {80, 80, 86}, {72, 72, 78},              // volcanic: blackstone, basalt, smooth basalt
    };

    static int[] shade(int[] c, double f) {
        return new int[]{clamp((int) (c[0] * f)), clamp((int) (c[1] * f)), clamp((int) (c[2] * f))};
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int rgb(int[] c) {
        return clamp(c[0]) << 16 | clamp(c[1]) << 8 | clamp(c[2]);
    }

    static boolean thin(byte code) {
        return code >= MarineVolume.PLANT && code < MarineVolume.ROCK || code >= MarineVolume.PEBBLES;
    }

    boolean visible(byte code) {
        return code != MarineVolume.NAT_WATER && code != MarineVolume.WATER && code != MarineVolume.AIR;
    }

    boolean opaque(byte code) {
        return visible(code) && !thin(code);
    }

    // =========================================================================================
    // Renderers
    // =========================================================================================

    /** Top view, one pixel per block: the first non-water block from above, darkened with depth. */
    BufferedImage top(int ax, int az, int n, int scale) {
        BufferedImage img = new BufferedImage(n * scale, n * scale, BufferedImage.TYPE_INT_RGB);
        for (int x = ax; x < ax + n; x++) {
            for (int z = az; z < az + n; z++) {
                MarineColumn c = column(x, z);
                int y = Math.max(c.floor + 40, planner.surf);
                while (y > c.floor - 4 && !visible(get(x, y, z))) {
                    y--;
                }
                byte code = get(x, y, z);
                int[] col = colour(x, y, z, code);
                // hill shading from the west
                double west = 0;
                int yw = Math.max(column(x - 1, z).floor + 40, planner.surf);
                while (yw > column(x - 1, z).floor - 4 && !visible(get(x - 1, yw, z))) {
                    yw--;
                }
                west = y - yw;
                double f = 1.0 + Math.max(-0.25, Math.min(0.25, west * 0.08));
                col = shade(col, f);
                int depth = planner.surf - y;
                if (depth > 0) {
                    double w = Math.min(0.8, depth / 40.0);
                    col = new int[]{(int) (col[0] * (1 - w) + 20 * w), (int) (col[1] * (1 - w) + 70 * w), (int) (col[2] * (1 - w) + 140 * w)};
                }
                for (int i = 0; i < scale; i++) {
                    for (int j = 0; j < scale; j++) {
                        img.setRGB((x - ax) * scale + i, (z - az) * scale + j, rgb(col));
                    }
                }
            }
        }
        return img;
    }

    /**
     * Isometric view of a square area seen from the south-east and above; water is left out and
     * deeper blocks are tinted blue.
     */
    BufferedImage iso(int ax, int az, int n, int s, int yLow, int yHigh) {
        int w = 2 * n * s + 2 * s + 20;
        int h = n * s + (yHigh - yLow + 2) * s + 20;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int bg = rgb(new int[]{16, 32, 52});
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
                    // skip fully hidden blocks
                    if (!thin(code) && dx < n - 1 && dz < n - 1 && y < yHigh && opaque(get(x, y + 1, z)) && opaque(get(x + 1, y, z)) && opaque(get(x, y, z + 1))) {
                        continue;
                    }
                    int[] col = colour(x, y, z, code);
                    int depth = planner.surf - y;
                    if (depth > 0) {
                        double f = Math.min(0.35, depth / 150.0);
                        col = new int[]{(int) (col[0] * (1 - f) + 20 * f), (int) (col[1] * (1 - f) + 60 * f), (int) (col[2] * (1 - f) + 120 * f)};
                    }
                    int sx = ox + (dx - dz) * s;
                    int sy = oy + (dx + dz) * s / 2 - (y - yLow) * s;
                    if (thin(code)) {
                        sprite(img, sx, sy, s, rgb(col));
                    } else {
                        cube(img, sx, sy, s, col);
                    }
                }
            }
        }
        return img;
    }

    private static void cube(BufferedImage img, int sx, int sy, int s, int[] c) {
        int top = rgb(c);
        int left = rgb(shade(c, 0.78));
        int right = rgb(shade(c, 0.6));
        int hs = s / 2;
        // top rhombus centred at (sx, sy)
        for (int j = -hs; j <= hs; j++) {
            int half = s - Math.abs(j) * 2;
            for (int i = -half; i <= half; i++) {
                set(img, sx + i, sy + j, top);
            }
        }
        // +z face (left) and +x face (right)
        for (int i = 0; i <= s; i++) {
            int edge = sy + i / 2;
            for (int j = 1; j <= s; j++) {
                set(img, sx - s + i, edge + j, left);
                set(img, sx + s - i, edge + j, right);
            }
        }
    }

    private static void sprite(BufferedImage img, int sx, int sy, int s, int c) {
        for (int i = -s / 3; i <= s / 3; i++) {
            for (int j = -s / 2; j <= s / 2; j++) {
                if ((i + j & 1) == 0 || Math.abs(i) < s / 5) {
                    set(img, sx + i, sy + j + s / 2, c);
                }
            }
        }
    }

    private static void set(BufferedImage img, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, c);
        }
    }

    private int[] sliceColour(int x, int y, int z) {
        byte code = get(x, y, z);
        if (code == MarineVolume.NAT_SOLID) {
            return new int[]{120, 104, 88};
        }
        if (code == MarineVolume.WATER) {
            return new int[]{80, 150, 240};
        }
        if (code == MarineVolume.AIR) {
            return new int[]{250, 250, 190};
        }
        if (code == MarineVolume.NAT_WATER) {
            return y <= planner.surf ? new int[]{30, 70, 150} : new int[]{205, 225, 245};
        }
        return colour(x, y, z, code);
    }

    /** Horizontal slice at height {@code y} (dug-out water light blue, open sea dark blue, air yellow). */
    BufferedImage slice(int ax, int az, int n, int y, int scale) {
        BufferedImage img = new BufferedImage(n * scale, n * scale, BufferedImage.TYPE_INT_RGB);
        for (int x = ax; x < ax + n; x++) {
            for (int z = az; z < az + n; z++) {
                int c = rgb(sliceColour(x, y, z));
                for (int i = 0; i < scale; i++) {
                    for (int j = 0; j < scale; j++) {
                        img.setRGB((x - ax) * scale + i, (z - az) * scale + j, c);
                    }
                }
            }
        }
        return img;
    }

    /** Vertical section along a direction from a point, {@code scale} pixels per block. */
    BufferedImage axis(double x0, double z0, double ux, double uz, int uMin, int uMax, int yLow, int yHigh, int scale) {
        BufferedImage img = new BufferedImage((uMax - uMin + 1) * scale, (yHigh - yLow + 1) * scale, BufferedImage.TYPE_INT_RGB);
        for (int u = uMin; u <= uMax; u++) {
            int x = (int) Math.floor(x0 + ux * u);
            int z = (int) Math.floor(z0 + uz * u);
            for (int y = yLow; y <= yHigh; y++) {
                int c = rgb(sliceColour(x, y, z));
                for (int i = 0; i < scale; i++) {
                    for (int j = 0; j < scale; j++) {
                        img.setRGB((u - uMin) * scale + i, (yHigh - y) * scale + j, c);
                    }
                }
            }
        }
        return img;
    }

    /**
     * What a diver sees: a ray-cast view from {@code (cx, cy, cz)} along {@code (dx, dz)} tilted by
     * {@code pitch} degrees, with underwater fog (turquoise in warm seas, blue elsewhere) and shaded
     * block faces. Plants are drawn as thin, partly transparent blocks.
     */
    BufferedImage perspective(double cx, double cy, double cz, double dirX, double dirZ, double pitch, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double len = Math.hypot(dirX, dirZ);
        double fx = dirX / len;
        double fz = dirZ / len;
        double p = Math.toRadians(pitch);
        // camera basis: forward, right, up
        double[] fwd = {fx * Math.cos(p), Math.sin(p), fz * Math.cos(p)};
        double[] right = {-fz, 0, fx};
        double[] up = {fwd[1] * right[2] - fwd[2] * right[1], fwd[2] * right[0] - fwd[0] * right[2], fwd[0] * right[1] - fwd[1] * right[0]};
        if (up[1] < 0) {
            up = new double[]{-up[0], -up[1], -up[2]};
        }
        double tanHalf = Math.tan(Math.toRadians(35.0));
        MarineColumn here = column((int) Math.floor(cx), (int) Math.floor(cz));
        boolean warm = here.temperature > 0.55;
        int[] fog = warm ? new int[]{38, 150, 170} : here.temperature > 0.2 ? new int[]{30, 110, 160} : new int[]{24, 72, 120};
        double fogDist = warm ? 38.0 : 30.0;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                double sx = (2.0 * (px + 0.5) / w - 1.0) * tanHalf * w / h;
                double sy = (1.0 - 2.0 * (py + 0.5) / h) * tanHalf;
                double rx = fwd[0] + sx * right[0] + sy * up[0];
                double ry = fwd[1] + sx * right[1] + sy * up[1];
                double rz = fwd[2] + sx * right[2] + sy * up[2];
                double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
                img.setRGB(px, py, rgb(trace(cx, cy, cz, rx / rl, ry / rl, rz / rl, fog, fogDist)));
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
        double plantAlpha = 0.0;
        int[] plant = null;
        double plantT = 0.0;
        while (t < fogDist * 1.6) {
            byte code = get(x, y, z);
            if (y > planner.surf && code != MarineVolume.AIR && !visible(code)) {
                // left the water: sky seen through the surface
                return blendFog(new int[]{170, 210, 240}, fog, Math.min(1.0, t / fogDist), plant, plantAlpha, plantT, fogDist);
            }
            if (visible(code)) {
                int[] c = colour(x, y, z, code);
                if (thin(code)) {
                    if (plant == null) {
                        plant = c;
                        plantAlpha = 0.6;
                        plantT = t;
                    }
                } else {
                    double shade = face == 1 ? 1.0 : face == 0 ? 0.78 : face == 2 ? 0.65 : 0.5;
                    // light falls off with depth below the surface
                    double depthLight = Math.max(0.45, 1.0 - (planner.surf - y) / 70.0);
                    int[] lit = shade(c, shade * depthLight);
                    return blendFog(lit, fog, Math.min(1.0, t / fogDist), plant, plantAlpha, plantT, fogDist);
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
        return blendFog(fog, fog, 1.0, plant, plantAlpha, plantT, fogDist);
    }

    private static int[] blendFog(int[] c, int[] fog, double f, int[] plant, double alpha, double plantT, double fogDist) {
        f = f * f * (3 - 2 * f);
        int[] out = {(int) (c[0] * (1 - f) + fog[0] * f), (int) (c[1] * (1 - f) + fog[1] * f), (int) (c[2] * (1 - f) + fog[2] * f)};
        if (plant != null) {
            double pf = Math.min(1.0, plantT / fogDist);
            pf = pf * pf * (3 - 2 * pf);
            int[] pc = {(int) (plant[0] * (1 - pf) + fog[0] * pf), (int) (plant[1] * (1 - pf) + fog[1] * pf), (int) (plant[2] * (1 - pf) + fog[2] * pf)};
            out = new int[]{(int) (out[0] * (1 - alpha) + pc[0] * alpha), (int) (out[1] * (1 - alpha) + pc[1] * alpha), (int) (out[2] * (1 - alpha) + pc[2] * alpha)};
        }
        return out;
    }

    /** Vertical section along x at {@code z}, {@code scale} pixels per block. */
    BufferedImage section(int ax, int n, int z, int yLow, int yHigh, int scale) {
        BufferedImage img = new BufferedImage(n * scale, (yHigh - yLow + 1) * scale, BufferedImage.TYPE_INT_RGB);
        for (int x = ax; x < ax + n; x++) {
            for (int y = yLow; y <= yHigh; y++) {
                byte code = get(x, y, z);
                int[] col = visible(code) ? colour(x, y, z, code) : y <= planner.surf ? new int[]{40, 100, 180} : new int[]{205, 225, 245};
                if (code == MarineVolume.WATER) {
                    col = new int[]{70, 130, 230};
                } else if (code == MarineVolume.AIR) {
                    col = new int[]{250, 250, 200};
                }
                for (int i = 0; i < scale; i++) {
                    for (int j = 0; j < scale; j++) {
                        img.setRGB((x - ax) * scale + i, (yHigh - y) * scale + j, rgb(col));
                    }
                }
            }
        }
        return img;
    }
}
