package com.terrahaul.worldgen.trees;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/**
 * A tree under construction, in block coordinates relative to the trunk base; block centres sit at
 * integer coordinates. Shape generators ({@link TreeShapes}) draw trunks, limbs, leaf clusters and
 * roots with the primitives here; {@link ProceduralTreeFeature} then validates and places the result.
 * <p>
 * The blueprint is plain Java: it stores cell kinds ({@link #LEAF}, {@link #LOG_Y}, {@link #WOOD},
 * ...) rather than block states, so the development tool {@code TreeStudy} can build, measure and
 * render trees without Minecraft. Cells live in a grid of lazily allocated horizontal layers.
 * <p>
 * Every primitive clips to the blueprint's reach horizontally: features may only write within the
 * 3x3 chunk area around the chunk being decorated. {@link #MAX_REACH} (15 blocks from the origin)
 * is always safe; ancient trees, whose origin is moved near the middle of its chunk, may reach up to
 * {@link #ANCIENT_REACH}. Clipped cells are counted ({@link #clipped()}) so the development tools can
 * flag shapes that run into the limit.
 * <p>
 * {@link #finish()} completes a blueprint: it gives every leaf its vanilla {@code distance}, drops
 * leaves more than six blocks from wood (they would decay at once) and covers exposed log ends with
 * bark, so branches never show sawn rings at their tips or bends.
 */
public final class TreeBlueprint {
    public static final int MAX_REACH = 15;
    /**
     * Reach of giant pines, whose wide umbrella needs more room than 15 blocks: the feature keeps
     * their trunk at chunk-local 3..12 (3 - 18 = -15 and 12 + 18 = 30, plus one for vines: within the
     * writable -16..31).
     */
    public static final int WIDE_REACH = 18;
    public static final int ANCIENT_REACH = 22;
    /** Lowest stored layer, for prop roots and rooted dirt below the trunk base. */
    public static final int MIN_Y = -12;
    /** Highest stored layer. */
    public static final int MAX_Y = 127;

    public static final byte EMPTY = 0;
    public static final byte LEAF = 1;
    /** The species' second leaf kind (flowering azalea). */
    public static final byte ALT_LEAF = 2;
    public static final byte LOG_Y = 3;
    public static final byte LOG_X = 4;
    public static final byte LOG_Z = 5;
    /** Bark on all six sides: bends, junctions and exposed ends of limbs. */
    public static final byte WOOD = 6;
    /** Mangrove roots (prop roots); continued down to the ground on placement. */
    public static final byte PROP_ROOT = 7;
    /** Rooted dirt, placed only where it replaces soil. */
    public static final byte ROOTED_DIRT = 8;

    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final TreeSpecies species;
    private final RandomGenerator random;
    private final int reach;
    private final int width;
    private final byte[][] cells = new byte[MAX_Y - MIN_Y + 1][];
    private byte[][] distances;
    private final boolean[] trunkBase;
    /** Ground-following roots: per column, how far the root rises above the local ground (NaN: none). */
    private final double[] groundRise;
    private int height;
    private int minX = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private int minZ = Integer.MAX_VALUE;
    private int maxZ = Integer.MIN_VALUE;
    private int clipped;
    private int orphans;
    private int detached;

    public TreeBlueprint(TreeSpecies species, RandomGenerator random, int reach) {
        this.species = species;
        this.random = random;
        this.reach = reach;
        this.width = 2 * reach + 1;
        this.trunkBase = new boolean[width * width];
        this.groundRise = new double[width * width];
        Arrays.fill(groundRise, Double.NaN);
    }

    public TreeSpecies species() {
        return species;
    }

    public RandomGenerator random() {
        return random;
    }

    public int reach() {
        return reach;
    }

    /** One above the highest woody block. */
    public int height() {
        return height;
    }

    /** Cells dropped because they fell outside the reach or the stored layers. */
    public int clipped() {
        return clipped;
    }

    /** Leaves removed by {@link #finish()} because no log was within six blocks. */
    public int orphans() {
        return orphans;
    }

    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    public static boolean isWoody(int code) {
        return code >= LOG_Y && code <= WOOD;
    }

    public static boolean isLeaf(int code) {
        return code == LEAF || code == ALT_LEAF;
    }

    // ------------------------------------------------------------------------------------------
    // Cells
    // ------------------------------------------------------------------------------------------

    private boolean inBounds(int x, int y, int z) {
        return x >= -reach && x <= reach && z >= -reach && z <= reach && y >= MIN_Y && y <= MAX_Y;
    }

    private int column(int x, int z) {
        return (x + reach) * width + z + reach;
    }

    public int get(int x, int y, int z) {
        if (!inBounds(x, y, z)) {
            return EMPTY;
        }
        byte[] layer = cells[y - MIN_Y];
        return layer == null ? EMPTY : layer[column(x, z)];
    }

    private boolean set(int x, int y, int z, byte code) {
        if (!inBounds(x, y, z)) {
            clipped++;
            return false;
        }
        byte[] layer = cells[y - MIN_Y];
        if (layer == null) {
            layer = new byte[width * width];
            cells[y - MIN_Y] = layer;
        }
        layer[column(x, z)] = code;
        minX = Math.min(minX, x);
        maxX = Math.max(maxX, x);
        minY = Math.min(minY, y);
        maxY = Math.max(maxY, y);
        minZ = Math.min(minZ, z);
        maxZ = Math.max(maxZ, z);
        return true;
    }

    /** A log with the given axis code; existing wood is kept. */
    public void log(int x, int y, int z, byte axis) {
        if (!isWoody(get(x, y, z)) && set(x, y, z, axis)) {
            height = Math.max(height, y + 1);
        }
    }

    /** Bark on all sides; existing wood is kept. */
    public void wood(int x, int y, int z) {
        log(x, y, z, WOOD);
    }

    /** Removes whatever is at the cell (broken tops, hollows). */
    public void clear(int x, int y, int z) {
        if (get(x, y, z) != EMPTY) {
            set(x, y, z, EMPTY);
        }
    }

    public void leaf(int x, int y, int z) {
        if (get(x, y, z) == EMPTY) {
            set(x, y, z, LEAF);
        }
    }

    public void propRoot(int x, int y, int z) {
        int e = get(x, y, z);
        if (e == EMPTY || isLeaf(e)) {
            set(x, y, z, PROP_ROOT);
        }
    }

    public void rootedDirt(int x, int y, int z) {
        if (y < 0 && get(x, y, z) == EMPTY) {
            set(x, y, z, ROOTED_DIRT);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Trunks and limbs
    // ------------------------------------------------------------------------------------------

    static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    static int ceil(double v) {
        int i = (int) v;
        return v > i ? i + 1 : i;
    }

    static int round(double v) {
        return floor(v + 0.5);
    }

    private void trunkCell(int x, int y, int z) {
        log(x, y, z, LOG_Y);
        if (y == 0 && x >= -reach && x <= reach && z >= -reach && z <= reach) {
            trunkBase[column(x, z)] = true;
        }
    }

    /**
     * One horizontal layer of a trunk centred on {@code (cx, cz)}. Radii below 0.45 give a single
     * column, below 0.95 a 2x2 trunk (whose centre is the shared corner of its four columns), larger
     * radii a round cross-section. Layer 0 marks the trunk footprint used to anchor the tree.
     */
    public void trunkLayer(double cx, int y, double cz, double radius) {
        if (radius < 0.45) {
            trunkCell(round(cx), y, round(cz));
            return;
        }
        if (radius < 0.95) {
            int x0 = floor(cx);
            int z0 = floor(cz);
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    trunkCell(x0 + dx, y, z0 + dz);
                }
            }
            return;
        }
        int r = ceil(radius) + 1;
        int ix = round(cx);
        int iz = round(cz);
        for (int x = ix - r; x <= ix + r; x++) {
            for (int z = iz - r; z <= iz + r; z++) {
                double dx = x - cx;
                double dz = z - cz;
                if (dx * dx + dz * dz <= radius * radius + 0.05) {
                    trunkCell(x, y, z);
                }
            }
        }
    }

    /** Straight vertical column of trunk logs. */
    public void column(int x, int z, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            trunkCell(x, y, z);
        }
    }

    /**
     * A limb from {@code (x0,y0,z0)} to {@code (x1,y1,z1)} narrowing from radius {@code r0} to
     * {@code r1}. Thin limbs are drawn as face-connected lines of logs whose axis follows each run;
     * blocks where the line turns get bark on all sides. Thick limbs are swept as tapered capsules,
     * with logs along the limb's axis when it is nearly axis-aligned and bark blocks otherwise.
     */
    public void limb(double x0, double y0, double z0, double x1, double y1, double z1, double r0, double r1) {
        if (Math.max(r0, r1) < 0.62) {
            path(x0, y0, z0, x1, y1, z1, (byte) 0);
            return;
        }
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double len2 = dx * dx + dy * dy + dz * dz;
        if (len2 < 1e-6) {
            return;
        }
        double len = Math.sqrt(len2);
        byte code = WOOD;
        if (Math.abs(dy) / len > 0.88) {
            code = LOG_Y;
        } else if (Math.abs(dx) / len > 0.88) {
            code = LOG_X;
        } else if (Math.abs(dz) / len > 0.88) {
            code = LOG_Z;
        }
        double rm = Math.max(Math.max(r0, r1), 0.87);
        int bx0 = floor(Math.min(x0, x1) - rm);
        int bx1 = ceil(Math.max(x0, x1) + rm);
        int by0 = floor(Math.min(y0, y1) - rm);
        int by1 = ceil(Math.max(y0, y1) + rm);
        int bz0 = floor(Math.min(z0, z1) - rm);
        int bz1 = ceil(Math.max(z0, z1) + rm);
        for (int x = bx0; x <= bx1; x++) {
            for (int y = by0; y <= by1; y++) {
                for (int z = bz0; z <= bz1; z++) {
                    double t = ((x - x0) * dx + (y - y0) * dy + (z - z0) * dz) / len2;
                    t = Math.max(0.0, Math.min(1.0, t));
                    double px = x - (x0 + dx * t);
                    double py = y - (y0 + dy * t);
                    double pz = z - (z0 + dz * t);
                    // never thinner than a face-connected line
                    double r = Math.max(0.87, r0 + (r1 - r0) * t);
                    if (px * px + py * py + pz * pz <= r * r + 0.02) {
                        log(x, y, z, code);
                    }
                }
            }
        }
    }

    /**
     * A limb along a quadratic Bezier curve through the control point {@code (cx,cy,cz)}, drawn as
     * short {@link #limb} segments.
     */
    public void curve(double x0, double y0, double z0, double cx, double cy, double cz, double x1, double y1, double z1, double r0, double r1) {
        double approx = Math.sqrt((cx - x0) * (cx - x0) + (cy - y0) * (cy - y0) + (cz - z0) * (cz - z0))
            + Math.sqrt((x1 - cx) * (x1 - cx) + (y1 - cy) * (y1 - cy) + (z1 - cz) * (z1 - cz));
        int segments = Math.max(2, Math.min(12, ceil(approx / 1.6)));
        double px = x0;
        double py = y0;
        double pz = z0;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double u = 1.0 - t;
            double nx = u * u * x0 + 2 * u * t * cx + t * t * x1;
            double ny = u * u * y0 + 2 * u * t * cy + t * t * y1;
            double nz = u * u * z0 + 2 * u * t * cz + t * t * z1;
            double t0 = (i - 1) / (double) segments;
            limb(px, py, pz, nx, ny, nz, r0 + (r1 - r0) * t0, r0 + (r1 - r0) * t);
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    /**
     * Face-connected line of blocks from one point to another. With {@code rootCode} 0 the blocks
     * are logs (axis along each straight run, bark at bends); otherwise every block gets that code.
     */
    public void path(double x0, double y0, double z0, double x1, double y1, double z1, byte rootCode) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int cx = round(x0);
        int cy = round(y0);
        int cz = round(z0);
        int cap = 3 * (ceil(len) + 3) + 3;
        int[] xs = new int[cap];
        int[] ys = new int[cap];
        int[] zs = new int[cap];
        int n = 0;
        xs[n] = cx;
        ys[n] = cy;
        zs[n] = cz;
        n++;
        int steps = Math.max(1, ceil(len * 3.0));
        for (int i = 1; i <= steps && n < cap; i++) {
            double t = i / (double) steps;
            int tx = round(x0 + dx * t);
            int ty = round(y0 + dy * t);
            int tz = round(z0 + dz * t);
            while ((cx != tx || cy != ty || cz != tz) && n < cap) {
                // step along the axis whose move keeps the line closest to the true segment
                double best = Double.MAX_VALUE;
                int bx = cx;
                int by = cy;
                int bz = cz;
                if (cx != tx) {
                    double d = segmentDistance2(cx + Integer.signum(tx - cx), cy, cz, x0, y0, z0, dx, dy, dz);
                    if (d < best) {
                        best = d;
                        bx = cx + Integer.signum(tx - cx);
                        by = cy;
                        bz = cz;
                    }
                }
                if (cy != ty) {
                    double d = segmentDistance2(cx, cy + Integer.signum(ty - cy), cz, x0, y0, z0, dx, dy, dz);
                    if (d < best) {
                        best = d;
                        bx = cx;
                        by = cy + Integer.signum(ty - cy);
                        bz = cz;
                    }
                }
                if (cz != tz) {
                    double d = segmentDistance2(cx, cy, cz + Integer.signum(tz - cz), x0, y0, z0, dx, dy, dz);
                    if (d < best) {
                        bx = cx;
                        by = cy;
                        bz = cz + Integer.signum(tz - cz);
                    }
                }
                cx = bx;
                cy = by;
                cz = bz;
                xs[n] = cx;
                ys[n] = cy;
                zs[n] = cz;
                n++;
            }
        }
        for (int i = 0; i < n; i++) {
            if (rootCode != 0) {
                propRoot(xs[i], ys[i], zs[i]);
                continue;
            }
            byte in = i > 0 ? axisCode(xs[i] - xs[i - 1], ys[i] - ys[i - 1]) : 0;
            byte out = i < n - 1 ? axisCode(xs[i + 1] - xs[i], ys[i + 1] - ys[i]) : 0;
            byte code;
            if (in == 0 && out == 0) {
                code = Math.abs(dy) >= Math.max(Math.abs(dx), Math.abs(dz)) ? LOG_Y : Math.abs(dx) >= Math.abs(dz) ? LOG_X : LOG_Z;
            } else if (in == 0 || out == 0 || in == out) {
                code = in != 0 ? in : out;
            } else {
                code = WOOD;
            }
            log(xs[i], ys[i], zs[i], code);
        }
    }

    private static byte axisCode(int dx, int dy) {
        return dx != 0 ? LOG_X : dy != 0 ? LOG_Y : LOG_Z;
    }

    private static double segmentDistance2(double qx, double qy, double qz, double x0, double y0, double z0, double dx, double dy, double dz) {
        double len2 = dx * dx + dy * dy + dz * dz;
        double t = len2 < 1e-9 ? 0.0 : ((qx - x0) * dx + (qy - y0) * dy + (qz - z0) * dz) / len2;
        t = Math.max(0.0, Math.min(1.0, t));
        double px = qx - (x0 + dx * t);
        double py = qy - (y0 + dy * t);
        double pz = qz - (z0 + dz * t);
        return px * px + py * py + pz * pz;
    }

    // ------------------------------------------------------------------------------------------
    // Leaves
    // ------------------------------------------------------------------------------------------

    /**
     * Ellipsoidal leaf mass. The outer shell is thinned at random so clusters never look like
     * perfect spheres; {@code density} below 1 thins the interior too.
     */
    public void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, double density) {
        int x0 = floor(cx - rx);
        int x1 = ceil(cx + rx);
        int y0 = floor(cy - ry);
        int y1 = ceil(cy + ry);
        int z0 = floor(cz - rz);
        int z1 = ceil(cz + rz);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    double nx = (x - cx) / rx;
                    double ny = (y - cy) / ry;
                    double nz = (z - cz) / rz;
                    double d = nx * nx + ny * ny + nz * nz;
                    if (d > 1.0) {
                        continue;
                    }
                    if (d > 0.62 && random.nextDouble() < (d - 0.62) / 0.38 * 0.55) {
                        continue;
                    }
                    if (density < 1.0 && random.nextDouble() > density + (1.0 - density) * (1.0 - d)) {
                        continue;
                    }
                    leaf(x, y, z);
                }
            }
        }
    }

    /**
     * A puffy, cauliflower-like leaf cluster: a main ellipsoid with a few smaller lobes swelling
     * out of its upper and outer surface.
     *
     * @param flat vertical squash of the cluster (1 = round)
     */
    public void puff(double cx, double cy, double cz, double r, double flat, double density) {
        ellipsoid(cx, cy, cz, r, r * flat, r, density);
        int lobes = 2 + random.nextInt(3);
        for (int i = 0; i < lobes; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double e = -0.15 + random.nextDouble() * 0.95;
            double h = Math.sqrt(1.0 - e * e);
            double off = r * (0.42 + random.nextDouble() * 0.16);
            double lr = r * (0.5 + random.nextDouble() * 0.16);
            ellipsoid(cx + Math.cos(a) * h * off, cy + e * off * flat, cz + Math.sin(a) * h * off, lr, lr * flat * 1.05, lr, density);
        }
    }

    /**
     * A cloud pad: a flat-bottomed dome of leaves whose lowest layer is {@code y}, like the foliage
     * pads of acacias and bonsai-like oaks. Pads wider than 3 blocks get a few hidden twigs so
     * every leaf stays within reach of wood.
     */
    public void pad(double cx, int y, double cz, double r, double thick, double density) {
        int ri = ceil(r);
        int ix = round(cx);
        int iz = round(cz);
        double phase = random.nextDouble() * Math.PI * 2.0;
        for (int x = ix - ri; x <= ix + ri; x++) {
            for (int z = iz - ri; z <= iz + ri; z++) {
                double dx = x - cx;
                double dz = z - cz;
                // a lobed outline instead of a perfect circle
                double edge = r * (1.0 + 0.13 * Math.sin(Math.atan2(dz, dx) * 3.0 + phase));
                double d2 = (dx * dx + dz * dz) / (edge * edge);
                if (d2 > 1.0 || d2 > 0.7 && random.nextDouble() < (d2 - 0.7) / 0.3 * 0.6) {
                    continue;
                }
                int top = floor(thick * Math.sqrt(1.0 - d2) + 0.45);
                for (int dy = 0; dy <= top; dy++) {
                    if (density >= 1.0 || random.nextDouble() < density + (1.0 - density) * (1.0 - d2)) {
                        leaf(x, y + dy, z);
                    }
                }
                if (d2 < 0.3 && random.nextDouble() < 0.4) {
                    leaf(x, y - 1, z);
                }
            }
        }
        if (r > 3.0) {
            int spokes = 3 + random.nextInt(2);
            double a0 = random.nextDouble() * Math.PI * 2.0;
            for (int i = 0; i < spokes; i++) {
                double a = a0 + i * Math.PI * 2.0 / spokes;
                path(cx, y, cz, cx + Math.cos(a) * r * 0.55, y, cz + Math.sin(a) * r * 0.55, (byte) 0);
            }
        }
    }

    /** One flat layer of leaves (conifer whorls, acacia pads). */
    public void disc(double cx, int y, double cz, double radius, double density) {
        int r = ceil(radius);
        int ix = round(cx);
        int iz = round(cz);
        for (int x = ix - r; x <= ix + r; x++) {
            for (int z = iz - r; z <= iz + r; z++) {
                double dx = x - cx;
                double dz = z - cz;
                double d = (dx * dx + dz * dz) / (radius * radius);
                if (d > 1.0) {
                    continue;
                }
                double edge = d > 0.6 ? (d - 0.6) / 0.4 : 0.0;
                if (random.nextDouble() < edge * 0.7 || random.nextDouble() > density) {
                    continue;
                }
                leaf(x, y, z);
            }
        }
    }

    /**
     * A hanging strand of leaves: from {@code y} downwards, skips the leaves and wood already there
     * and then hangs up to {@code length} leaves below them (weeping crowns, cherry fringes).
     */
    public void strand(int x, int y, int z, int length) {
        int yy = y;
        int guard = 0;
        while (get(x, yy, z) != EMPTY && guard++ < 8) {
            yy--;
        }
        if (guard == 0 || guard > 8) {
            return;
        }
        for (int i = 0; i < length && get(x, yy, z) == EMPTY; i++, yy--) {
            leaf(x, yy, z);
        }
    }

    /** Turns some leaves into the species' second leaf kind (flowering azalea). */
    public void sprinkleAltLeaves(double fraction) {
        for (byte[] layer : cells) {
            if (layer == null) {
                continue;
            }
            for (int i = 0; i < layer.length; i++) {
                if (layer[i] == LEAF && random.nextDouble() < fraction) {
                    layer[i] = ALT_LEAF;
                }
            }
        }
    }

    /** Removes a random fraction of the leaves (sparse, sun-scorched crowns). */
    public void thinLeaves(double fraction) {
        if (fraction <= 0) {
            return;
        }
        for (byte[] layer : cells) {
            if (layer == null) {
                continue;
            }
            for (int i = 0; i < layer.length; i++) {
                if (isLeaf(layer[i]) && random.nextDouble() < fraction) {
                    layer[i] = EMPTY;
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Ground-following roots
    // ------------------------------------------------------------------------------------------

    /**
     * Marks a ground-following root column: the root rises {@code rise} blocks above that column's
     * own ground (0..1: flush with the surface, replacing the top soil block; negative down to -1.2:
     * just under it, shown as rooted dirt).
     */
    public void groundRoot(int x, int z, double rise) {
        if (x < -reach || x > reach || z < -reach || z > reach) {
            clipped++;
            return;
        }
        int c = column(x, z);
        if (trunkBase[c]) {
            return;
        }
        if (Double.isNaN(groundRise[c]) || rise > groundRise[c]) {
            groundRise[c] = rise;
        }
    }

    public double groundRise(int x, int z) {
        if (x < -reach || x > reach || z < -reach || z > reach) {
            return Double.NaN;
        }
        return groundRise[column(x, z)];
    }

    public boolean isTrunkBase(int x, int z) {
        return x >= -reach && x <= reach && z >= -reach && z <= reach && trunkBase[column(x, z)];
    }

    /**
     * A root system that follows the ground. The base of the trunk swells into a flare that is
     * highest along the main roots; each main root leaves the trunk as a buttress {@code buttress}
     * blocks high that drops away steeply, runs on as a low, meandering ridge with an occasional
     * knee, narrows as it goes and dives under the soil near its end, where it shows as rooted dirt.
     * Most main roots fork once. Heights are relative to the ground under each column, so roots hug
     * slopes instead of floating over them.
     *
     * @param c           trunk base centre (0.5 for 2x2 trunks, else 0)
     * @param trunkRadius trunk radius at the base
     * @param halfWidth   half the width of a root where it leaves the trunk
     */
    public void rootSystem(double c, int count, double trunkRadius, double length, double buttress, double halfWidth) {
        double start = trunkRadius < 0.45 ? 0.5 : trunkRadius < 0.95 ? 1.0 : trunkRadius;
        double a0 = random.nextDouble() * Math.PI * 2.0;
        double[] angles = new double[count];
        for (int i = 0; i < count; i++) {
            angles[i] = a0 + i * Math.PI * 2.0 / count + (random.nextDouble() - 0.5) * 0.6;
        }
        // the flare: a swelling around the base, lobed towards the roots
        double flareW = 0.9 + 0.5 * start + 0.25 * buttress;
        double flareH = 0.35 + 0.4 * buttress;
        int fr = ceil(start + flareW) + 1;
        int ic = round(c);
        for (int x = ic - fr; x <= ic + fr; x++) {
            for (int z = ic - fr; z <= ic + fr; z++) {
                double dx = x - c;
                double dz = z - c;
                double d = Math.sqrt(dx * dx + dz * dz) - start;
                if (d < 0 || d > flareW) {
                    continue;
                }
                double a = Math.atan2(dz, dx);
                double lobe = 0.0;
                for (double ra : angles) {
                    double da = Math.abs(Math.IEEEremainder(a - ra, Math.PI * 2.0));
                    lobe = Math.max(lobe, Math.exp(-da * da * count * count * 0.25));
                }
                double t = d / flareW;
                groundRoot(x, z, flareH * (0.45 + 0.55 * lobe) * Math.pow(1.0 - t, 1.5) - 0.3 * t);
            }
        }
        for (int i = 0; i < count; i++) {
            double len = length * (0.65 + 0.5 * random.nextDouble());
            double sx = c + Math.cos(angles[i]) * start * 0.85;
            double sz = c + Math.sin(angles[i]) * start * 0.85;
            double h0 = buttress * (0.7 + 0.5 * random.nextDouble());
            traceRoot(sx, sz, angles[i], len, h0, halfWidth, random.nextFloat() < 0.75);
        }
    }

    private void traceRoot(double x, double z, double angle, double len, double h0, double w0, boolean fork) {
        double wander = (random.nextDouble() - 0.5) * 0.9;
        double phase = random.nextDouble() * Math.PI * 2.0;
        double forkAt = 0.3 + 0.3 * random.nextDouble();
        int forkSide = random.nextBoolean() ? 1 : -1;
        double step = 0.4;
        double ridge = Math.min(0.85, 0.35 + 0.4 * w0);
        for (double s = 0.0; s <= len; s += step) {
            double t = s / len;
            double a = angle + wander * t + 0.3 * Math.sin(phase + t * 5.0);
            x += Math.cos(a) * step;
            z += Math.sin(a) * step;
            // a tall fin at the trunk, a low ridge with knees further out, below ground at the tip
            double rise = h0 * Math.pow(1.0 - t, 2.6) + ridge * (1.0 - t) - 1.6 * t * t
                + 0.6 * Math.max(0.0, Math.sin(phase * 1.7 + t * 11.0)) * (1.0 - t) * Math.min(1.0, w0);
            double w = Math.max(0.45, w0 * (1.0 - 0.7 * t));
            markRoot(x, z, w, rise);
            if (fork && t >= forkAt) {
                fork = false;
                traceRoot(x, z, a + forkSide * (0.55 + 0.4 * random.nextDouble()), (len - s) * 0.8, Math.max(0.0, rise) * 0.5, w * 0.75, false);
            }
        }
    }

    private void markRoot(double x, double z, double w, double rise) {
        int r = ceil(w);
        for (int ix = floor(x) - r; ix <= floor(x) + r + 1; ix++) {
            for (int iz = floor(z) - r; iz <= floor(z) + r + 1; iz++) {
                double dx = ix - x;
                double dz = iz - z;
                if (dx * dx + dz * dz > w * w + 0.15) {
                    continue;
                }
                // the root's crest is lower towards its edges
                double edge = Math.sqrt(dx * dx + dz * dz) / Math.max(w, 0.5);
                double h = rise - (rise > 1.0 ? (rise - 1.0) * 0.35 * edge : 0.0) - 0.4 * edge;
                if (ix >= -reach && ix <= reach && iz >= -reach && iz <= reach) {
                    groundRoot(ix, iz, h);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Completion
    // ------------------------------------------------------------------------------------------

    /**
     * Completes the blueprint: removes any wood that is not connected to the tree's footing (so a
     * shape can never leave a floating log), computes the vanilla {@code distance} of every leaf by
     * a breadth-first search from the wood, removes leaves farther than six blocks (they would decay
     * right after generation) and gives exposed log ends bark on all sides.
     */
    public void finish() {
        pruneDetached();
        computeDistances();
        barkEnds();
    }

    /** Wood blocks removed by {@link #finish()} because they were not connected to the footing. */
    public int detached() {
        return detached;
    }

    private int index(int x, int y, int z) {
        return ((y - MIN_Y) * width + x + reach) * width + z + reach;
    }

    private static boolean isStructural(int code) {
        return isWoody(code) || code == PROP_ROOT;
    }

    /**
     * Flood-fills the wood and prop roots face by face from the blocks at or below the trunk base
     * and removes what the fill does not reach.
     */
    private void pruneDetached() {
        if (minX > maxX) {
            return;
        }
        int count = 0;
        for (int y = minY; y <= maxY; y++) {
            byte[] layer = cells[y - MIN_Y];
            if (layer != null) {
                for (byte b : layer) {
                    if (isStructural(b)) {
                        count++;
                    }
                }
            }
        }
        // flags and queue indices are local to the bounding box, which is much smaller than the grid
        int sx = maxX - minX + 1;
        int sz = maxZ - minZ + 1;
        boolean[] reached = new boolean[sx * sz * (maxY - minY + 1)];
        int[] queue = new int[Math.max(1, count)];
        int head = 0;
        int tail = 0;
        for (int y = minY; y <= Math.min(0, maxY); y++) {
            byte[] layer = cells[y - MIN_Y];
            if (layer == null) {
                continue;
            }
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (isStructural(layer[column(x, z)])) {
                        int idx = ((y - minY) * sx + x - minX) * sz + z - minZ;
                        reached[idx] = true;
                        queue[tail++] = idx;
                    }
                }
            }
        }
        while (head < tail) {
            int idx = queue[head++];
            int z = idx % sz + minZ;
            int x = (idx / sz) % sx + minX;
            int y = idx / (sx * sz) + minY;
            for (int[] n : NEIGHBOURS) {
                int nx = x + n[0];
                int ny = y + n[1];
                int nz = z + n[2];
                if (isStructural(get(nx, ny, nz))) {
                    int ni = ((ny - minY) * sx + nx - minX) * sz + nz - minZ;
                    if (!reached[ni]) {
                        reached[ni] = true;
                        queue[tail++] = ni;
                    }
                }
            }
        }
        if (tail == count) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            byte[] layer = cells[y - MIN_Y];
            if (layer == null) {
                continue;
            }
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int c = column(x, z);
                    if (isStructural(layer[c]) && !reached[((y - minY) * sx + x - minX) * sz + z - minZ]) {
                        layer[c] = EMPTY;
                        detached++;
                    }
                }
            }
        }
    }

    private void computeDistances() {
        distances = new byte[cells.length][];
        int leaves = 0;
        for (byte[] layer : cells) {
            if (layer != null) {
                for (byte b : layer) {
                    if (isLeaf(b)) {
                        leaves++;
                    }
                }
            }
        }
        int[] queue = new int[Math.max(1, leaves)];
        int head = 0;
        int tail = 0;
        if (minX > maxX) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            byte[] layer = cells[y - MIN_Y];
            if (layer == null) {
                continue;
            }
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isLeaf(layer[column(x, z)])) {
                        continue;
                    }
                    for (int[] n : NEIGHBOURS) {
                        if (isWoody(get(x + n[0], y + n[1], z + n[2]))) {
                            setDistance(x, y, z, 1);
                            queue[tail++] = index(x, y, z);
                            break;
                        }
                    }
                }
            }
        }
        while (head < tail) {
            int idx = queue[head++];
            int z = idx % width - reach;
            int x = (idx / width) % width - reach;
            int y = idx / (width * width) + MIN_Y;
            int d = distance(x, y, z);
            if (d >= 6) {
                continue;
            }
            for (int[] n : NEIGHBOURS) {
                int nx = x + n[0];
                int ny = y + n[1];
                int nz = z + n[2];
                if (isLeaf(get(nx, ny, nz)) && distance(nx, ny, nz) == 0) {
                    setDistance(nx, ny, nz, d + 1);
                    queue[tail++] = index(nx, ny, nz);
                }
            }
        }
        for (int y = minY; y <= maxY; y++) {
            byte[] layer = cells[y - MIN_Y];
            if (layer == null) {
                continue;
            }
            byte[] dl = distances[y - MIN_Y];
            for (int i = 0; i < layer.length; i++) {
                if (isLeaf(layer[i]) && (dl == null || dl[i] == 0)) {
                    layer[i] = EMPTY;
                    orphans++;
                }
            }
        }
    }

    private void setDistance(int x, int y, int z, int d) {
        byte[] dl = distances[y - MIN_Y];
        if (dl == null) {
            dl = new byte[width * width];
            distances[y - MIN_Y] = dl;
        }
        dl[column(x, z)] = (byte) d;
    }

    /** The vanilla leaf distance (1..6) of a leaf after {@link #finish()}. */
    public int distance(int x, int y, int z) {
        if (distances == null || !inBounds(x, y, z)) {
            return 0;
        }
        byte[] dl = distances[y - MIN_Y];
        return dl == null ? 0 : dl[column(x, z)];
    }

    private void barkEnds() {
        if (minX > maxX) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            byte[] layer = cells[y - MIN_Y];
            if (layer == null) {
                continue;
            }
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int c = column(x, z);
                    byte code = layer[c];
                    if (code < LOG_Y || code > LOG_Z) {
                        continue;
                    }
                    boolean exposed;
                    if (code == LOG_Y) {
                        // the trunk's foot rests on the ground or on the anchoring logs below it
                        exposed = !isWoody(get(x, y + 1, z)) || y > 0 && !isWoody(get(x, y - 1, z));
                    } else if (code == LOG_X) {
                        exposed = !isWoody(get(x + 1, y, z)) || !isWoody(get(x - 1, y, z));
                    } else {
                        exposed = !isWoody(get(x, y, z + 1)) || !isWoody(get(x, y, z - 1));
                    }
                    if (exposed) {
                        layer[c] = WOOD;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Iteration
    // ------------------------------------------------------------------------------------------

    @FunctionalInterface
    public interface CellVisitor {
        void visit(int x, int y, int z, int code);
    }

    @FunctionalInterface
    public interface RootVisitor {
        void visit(int x, int z, double rise);
    }

    /** Visits every non-empty cell, bottom layer first. */
    public void forEachCell(CellVisitor visitor) {
        if (minX > maxX) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            byte[] layer = cells[y - MIN_Y];
            if (layer == null) {
                continue;
            }
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    byte code = layer[column(x, z)];
                    if (code != EMPTY) {
                        visitor.visit(x, y, z, code);
                    }
                }
            }
        }
    }

    public void forEachGroundRoot(RootVisitor visitor) {
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double rise = groundRise[column(x, z)];
                if (!Double.isNaN(rise)) {
                    visitor.visit(x, z, rise);
                }
            }
        }
    }

    /** Visits the trunk footprint at the base (cells marked by trunk layer 0). */
    public void forEachTrunkBase(RootVisitor visitor) {
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                if (trunkBase[column(x, z)]) {
                    visitor.visit(x, z, 0.0);
                }
            }
        }
    }
}
