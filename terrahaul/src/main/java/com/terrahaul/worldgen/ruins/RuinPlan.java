package com.terrahaul.worldgen.ruins;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * A ruin as pure data: a box of {@link RuinCell} codes in world coordinates and world orientation,
 * plus foundation columns, ground-hugging decals and block-entity data (loot, sign text).
 * <p>
 * A plan is a deterministic function of its site, so every chunk that a ruin covers computes or
 * looks up the same plan and builds only its own columns from it. Two height modes exist:
 * <ul>
 *   <li>{@link #ABSOLUTE} (houses, sunken ruins): cell {@code j} of a column lies at
 *       {@code minY + j}; foundation columns are filled from the actual ground up to
 *       {@link #baseY}, so the building stands on the terrain whatever the ground does;</li>
 *   <li>{@link #GROUPED} (signposts, lamp posts): cells are relative to the ground of the anchor
 *       column, read once. Grouped plans always lie inside one chunk.</li>
 * </ul>
 * Decals (rubble, footpaths, campfire rings) are placed relative to the ground of their own
 * column and only onto support, so they never float either.
 */
public final class RuinPlan {
    public static final int ABSOLUTE = 0;
    public static final int GROUPED = 1;
    /** Deepest a foundation reaches below the floor before giving up (cave mouths, steep spots). */
    public static final int MAX_FILL = 12;

    public final int mode;
    public final int minX;
    public final int minY;
    public final int minZ;
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    /** Floor level: the lowest course of the building; foundation columns fill up to below it. */
    public final int baseY;
    public final RuinMaterials materials;
    /** Under water: cleared cells become water and every waterloggable block is waterlogged. */
    public final boolean underwater;
    public int anchorX;
    public int anchorZ;

    private final int[] cells;
    private final int[] fill;
    public final List<Decal> decals = new ArrayList<>();
    public final List<Extra> extras = new ArrayList<>();

    /** A cell placed {@code dy} above the top block of its own column (0 replaces that block). */
    public record Decal(int x, int z, int dy, int cell) {
    }

    /** Block-entity data: a container's loot table, a brushable block's loot, or sign text. */
    public record Extra(int x, int y, int z, boolean groundRelative, String lootTable, long seed, SignSpec sign) {
    }

    /** Text of both faces of a sign, four lines each; {@code @key|Fallback} marks a translatable line. */
    public record SignSpec(String[] front, String[] back, boolean white, boolean glowing) {
    }

    public RuinPlan(int mode, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, int baseY, RuinMaterials materials, boolean underwater) {
        this.mode = mode;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.baseY = baseY;
        this.materials = materials;
        this.underwater = underwater;
        this.cells = new int[sizeX * sizeY * sizeZ];
        this.fill = new int[sizeX * sizeZ];
    }

    public int maxX() {
        return minX + sizeX - 1;
    }

    public int maxY() {
        return minY + sizeY - 1;
    }

    public int maxZ() {
        return minZ + sizeZ - 1;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x < minX + sizeX && y >= minY && y < minY + sizeY && z >= minZ && z < minZ + sizeZ;
    }

    private int index(int x, int y, int z) {
        return ((x - minX) * sizeZ + (z - minZ)) * sizeY + (y - minY);
    }

    public int get(int x, int y, int z) {
        return contains(x, y, z) ? cells[index(x, y, z)] : 0;
    }

    public void set(int x, int y, int z, int cell) {
        if (contains(x, y, z)) {
            cells[index(x, y, z)] = cell;
        }
    }

    /** Cell code that fills this column from the ground up to {@link #baseY} - 1 (0: no fill). */
    public int fill(int x, int z) {
        if (x < minX || x >= minX + sizeX || z < minZ || z >= minZ + sizeZ) {
            return 0;
        }
        return fill[(x - minX) * sizeZ + (z - minZ)];
    }

    public void setFill(int x, int z, int cell) {
        if (x >= minX && x < minX + sizeX && z >= minZ && z < minZ + sizeZ) {
            fill[(x - minX) * sizeZ + (z - minZ)] = cell;
        }
    }

    public boolean overlapsColumns(int x0, int z0, int x1, int z1) {
        return maxX() >= x0 && minX <= x1 && maxZ() >= z0 && minZ <= z1;
    }

    /** Number of cells that place something (anything but {@link RuinPart#NONE}). */
    public int countCells() {
        int n = 0;
        for (int c : cells) {
            if (c != 0) {
                n++;
            }
        }
        return n;
    }

    // =========================================================================================
    // Connectivity: nothing in a ruin floats
    // =========================================================================================

    private boolean structural(int x, int y, int z) {
        int c = get(x, y, z);
        return c != 0 && RuinCell.part(c).structural;
    }

    private boolean solidCube(int x, int y, int z) {
        int c = get(x, y, z);
        return c != 0 && RuinCell.part(c).fullCube();
    }

    /**
     * Removes every structural cell that is not connected through face-adjacent structural cells
     * to the floor course ({@link #baseY} and below), then every attachment whose carrier is gone.
     * Planners call this last, so a broken wall can never leave a floating lintel or roof piece.
     *
     * @return the number of cells removed
     */
    public int prune() {
        boolean[] reached = new boolean[cells.length];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = minX; x < minX + sizeX; x++) {
            for (int z = minZ; z < minZ + sizeZ; z++) {
                for (int y = minY; y <= Math.min(baseY, maxY()); y++) {
                    if (structural(x, y, z)) {
                        reached[index(x, y, z)] = true;
                        queue.add(new int[]{x, y, z});
                    }
                }
            }
        }
        // roof stairs step diagonally from row to row, like rafters: between two roof cells a
        // diagonal step counts as a connection, everything else must share a face
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}, {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            boolean roof = roof(get(p[0], p[1], p[2]));
            for (int k = 0; k < faces.length; k++) {
                int[] f = faces[k];
                int x = p[0] + f[0];
                int y = p[1] + f[1];
                int z = p[2] + f[2];
                if (contains(x, y, z) && !reached[index(x, y, z)] && structural(x, y, z) && (k < 6 || roof && roof(get(x, y, z)))) {
                    reached[index(x, y, z)] = true;
                    queue.add(new int[]{x, y, z});
                }
            }
        }
        int removed = 0;
        for (int x = minX; x < minX + sizeX; x++) {
            for (int z = minZ; z < minZ + sizeZ; z++) {
                for (int y = minY; y < minY + sizeY; y++) {
                    int i = index(x, y, z);
                    if (cells[i] != 0 && RuinCell.part(cells[i]).structural && !reached[i]) {
                        cells[i] = 0;
                        removed++;
                    }
                }
            }
        }
        // attachments can hang from each other (vines below vines, a lantern on a chain): repeat
        for (int pass = 0; pass < 4; pass++) {
            int before = removed;
            for (int x = minX; x < minX + sizeX; x++) {
                for (int z = minZ; z < minZ + sizeZ; z++) {
                    for (int y = minY; y < minY + sizeY; y++) {
                        int i = index(x, y, z);
                        int c = cells[i];
                        if (c == 0 || RuinCell.part(c).structural || RuinCell.part(c) == RuinPart.AIR || RuinCell.part(c) == RuinPart.WATER) {
                            continue;
                        }
                        if (!supported(x, y, z, c)) {
                            cells[i] = 0;
                            removed++;
                        }
                    }
                }
            }
            if (removed == before) {
                break;
            }
        }
        return removed;
    }

    /** Whether an attachment at (x, y, z) has what it needs to hang on or stand on. */
    public boolean supported(int x, int y, int z, int c) {
        RuinPart part = RuinCell.part(c);
        int dir = RuinCell.dir(c);
        if (RuinCell.top(c) && (part == RuinPart.DOOR || part == RuinPart.TALL_GRASS || part == RuinPart.TALL_SEAGRASS)) {
            return RuinCell.part(get(x, y - 1, z)) == part;
        }
        if (part == RuinPart.KELP || part == RuinPart.KELP_PLANT) {
            RuinPart below = RuinCell.part(get(x, y - 1, z));
            return below == RuinPart.KELP_PLANT || below.structural || y <= baseY;
        }
        if (part.standsOnFloor()) {
            int below = get(x, y - 1, z);
            return y - 1 <= baseY && mode == GROUPED || below != 0 && RuinCell.part(below).structural
                || RuinCell.part(below) == RuinPart.BED && part == RuinPart.BED;
        }
        return switch (part) {
            case VINE, GLOW_LICHEN -> {
                int faces = RuinCell.aux(c);
                for (int d = 0; d < 4; d++) {
                    if ((faces & 1 << d) != 0 && solidCube(x + RuinCell.DX[d], y, z + RuinCell.DZ[d])) {
                        yield true;
                    }
                }
                yield part == RuinPart.VINE && RuinCell.part(get(x, y + 1, z)) == RuinPart.VINE
                    || (faces & 16) != 0 && solidCube(x, y + 1, z) || (faces & 32) != 0 && solidCube(x, y - 1, z);
            }
            case LADDER, CORAL_FAN -> solidCube(x - RuinCell.DX[dir], y, z - RuinCell.DZ[dir]);
            case WALL_HANGING_SIGN -> {
                int side = RuinCell.clockwise(dir);
                yield solidCube(x + RuinCell.DX[side], y, z + RuinCell.DZ[side]) || solidCube(x - RuinCell.DX[side], y, z - RuinCell.DZ[side]);
            }
            case LANTERN -> RuinCell.flag(c) ? hangsFrom(get(x, y + 1, z)) : structural(x, y - 1, z);
            case CHAIN -> hangsFrom(get(x, y + 1, z)) || structural(x, y - 1, z);
            case LEAVES -> anyNeighbour(x, y, z, true);
            default -> anyNeighbour(x, y, z, false);
        };
    }

    private static boolean roof(int c) {
        RuinPart p = RuinCell.part(c);
        return c != 0 && (p == RuinPart.ROOF_STAIR || p == RuinPart.ROOF_SLAB || p == RuinPart.THATCH || p == RuinPart.ROOF);
    }

    private boolean hangsFrom(int above) {
        RuinPart p = RuinCell.part(above);
        return above != 0 && (p.structural || p == RuinPart.CHAIN);
    }

    private boolean anyNeighbour(int x, int y, int z, boolean leaves) {
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] f : faces) {
            int c = get(x + f[0], y + f[1], z + f[2]);
            if (c == 0) {
                continue;
            }
            RuinPart p = RuinCell.part(c);
            if (p.structural || leaves && p == RuinPart.LEAVES) {
                return true;
            }
        }
        return y <= baseY + 1 && mode == GROUPED;
    }

    /**
     * Structural cells not connected to the floor course (0 after {@link #prune()}); used by the
     * devtools to verify the guarantee independently.
     */
    public int floatingCells() {
        RuinPlan copy = new RuinPlan(mode, minX, minY, minZ, sizeX, sizeY, sizeZ, baseY, materials, underwater);
        System.arraycopy(cells, 0, copy.cells, 0, cells.length);
        return copy.prune();
    }

    /** Order-independent fingerprint of the plan, for determinism checks. */
    public long fingerprint() {
        long h = 0x9E3779B97F4A7C15L ^ minX * 31L ^ minY * 131L ^ minZ * 1031L;
        for (int i = 0; i < cells.length; i++) {
            h = h * 0x100000001B3L ^ cells[i];
        }
        for (int i = 0; i < fill.length; i++) {
            h = h * 0x100000001B3L ^ fill[i];
        }
        for (Decal d : decals) {
            h = h * 0x100000001B3L ^ (d.x() * 73856093L ^ d.z() * 19349663L ^ d.dy() * 83492791L ^ d.cell());
        }
        for (Extra e : extras) {
            h = h * 0x100000001B3L ^ (e.x() * 73856093L ^ e.y() * 19349663L ^ e.z() * 83492791L ^ e.seed());
        }
        return h;
    }
}
