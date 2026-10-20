package com.terrahaul.worldgen.features.lake;

import static com.terrahaul.worldgen.features.lake.LakeVolume.BRICK_SLAB;
import static com.terrahaul.worldgen.features.lake.LakeVolume.CHEST;
import static com.terrahaul.worldgen.features.lake.LakeVolume.COBBLE;
import static com.terrahaul.worldgen.features.lake.LakeVolume.CRACKED_BRICKS;
import static com.terrahaul.worldgen.features.lake.LakeVolume.DIORITE;
import static com.terrahaul.worldgen.features.lake.LakeVolume.FENCE;
import static com.terrahaul.worldgen.features.lake.LakeVolume.FILL;
import static com.terrahaul.worldgen.features.lake.LakeVolume.GRANITE;
import static com.terrahaul.worldgen.features.lake.LakeVolume.GRAVEL;
import static com.terrahaul.worldgen.features.lake.LakeVolume.LOG_X;
import static com.terrahaul.worldgen.features.lake.LakeVolume.LOG_Y;
import static com.terrahaul.worldgen.features.lake.LakeVolume.LOG_Z;
import static com.terrahaul.worldgen.features.lake.LakeVolume.MOSS;
import static com.terrahaul.worldgen.features.lake.LakeVolume.MOSSY_BRICKS;
import static com.terrahaul.worldgen.features.lake.LakeVolume.MOSSY_COBBLE;
import static com.terrahaul.worldgen.features.lake.LakeVolume.NAT_AIR;
import static com.terrahaul.worldgen.features.lake.LakeVolume.NAT_WATER;
import static com.terrahaul.worldgen.features.lake.LakeVolume.PEBBLES;
import static com.terrahaul.worldgen.features.lake.LakeVolume.PLANKS;
import static com.terrahaul.worldgen.features.lake.LakeVolume.ROCK;
import static com.terrahaul.worldgen.features.lake.LakeVolume.ROCK_DECOR;
import static com.terrahaul.worldgen.features.lake.LakeVolume.SLAB;
import static com.terrahaul.worldgen.features.lake.LakeVolume.SLAB_TOP;
import static com.terrahaul.worldgen.features.lake.LakeVolume.STAIRS;
import static com.terrahaul.worldgen.features.lake.LakeVolume.STONE_BRICKS;
import static com.terrahaul.worldgen.features.lake.LakeVolume.STRIPPED_X;
import static com.terrahaul.worldgen.features.lake.LakeVolume.STRIPPED_Y;
import static com.terrahaul.worldgen.features.lake.LakeVolume.STRIPPED_Z;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.features.marine.MarinePlanner;
import com.terrahaul.worldgen.features.marine.RockRaster;
import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.LakeType;
import java.util.SplittableRandom;

/**
 * Rasterises what lies on a lake floor into a {@link LakeVolume}: the bed (shelves, gravel bars,
 * patches of sand, gravel, clay, mud and moss), bedrock ledges, boulders, sunken logs, the odd
 * discovery (a boat wreck, a drowned hut or tower, the pilings of an old pier) and pebbles and
 * knee-high rocks. Pure Java.
 * <p>
 * Everything is planned from the terrain model and position hashes, and each chunk builds only
 * the columns it owns. Every column's planned blocks form one run up from the floor, each block
 * resting on the one below, so nothing floats and the feature can check every block's support in
 * the world before it places it. Only lake water (and, for rocks, tower stumps and pier posts
 * rising out of the water, the air above it) is replaced; the bed itself only takes other
 * sediment, never anything that would let the water out.
 */
public final class LakeRaster {
    private static final int BOULDER_CELL = 11;
    /** Farthest a boulder block lies from its site. */
    private static final int BOULDER_REACH = 9;
    private static final int SKERRY_CELL = 36;
    private static final int SKERRY_REACH = 8;
    /** Rock islets per lake type: most in glacial, great and mountain lakes. */
    private static final double[] SKERRY_T = {0.0, 0.03, 0.2, 0.24, 0.0, 0.08, 0.06, 0.3};
    private static final int LOG_CELL = 9;
    private static final int LOG_REACH = 10;

    private final LakeVolume v;
    private final LakePlanner p;

    private LakeRaster(LakeVolume v) {
        this.v = v;
        this.p = v.planner();
    }

    /**
     * Plans the floor of the volume's core.
     *
     * @param rockDensity multiplier for boulders (the rock density option)
     * @param detailDensity multiplier for pebbles and knee-high rocks (the ground detail option)
     */
    public static void build(LakeVolume v, double rockDensity, double detailDensity) {
        LakeRaster r = new LakeRaster(v);
        r.beds();
        r.ledges();
        r.discoveries();
        r.boulders(rockDensity);
        r.skerries(rockDensity);
        r.logs();
        r.scatter(detailDensity);
    }

    private boolean open(byte code) {
        return code == NAT_WATER || code == NAT_AIR;
    }

    /**
     * Places a block at {@code y} so that it rests on something: open water between the planned
     * bed and {@code y} is filled with the bed's sediment first. Returns false (and places
     * nothing) if the cell is taken, or lies above the water and {@code aboveWater} is false.
     */
    private boolean stack(int x, int y, int z, byte code, int tint, boolean aboveWater) {
        if (!v.inCore(x, z)) {
            return false;
        }
        LakeColumn c = v.column(x, z);
        if (!c.wet || !open(v.get(x, y, z)) || y > c.surf && !aboveWater) {
            return false;
        }
        int below = y - 1;
        while (below > c.floor && open(v.get(x, below, z))) {
            below--;
        }
        byte support = v.get(x, below, z);
        if (!LakeVolume.isSolid(support)) {
            // a plant, a pebble or a fence post: nothing to build on
            return false;
        }
        if (LakeVolume.isBuilt(support) && (below + 1 < y || !LakeVolume.isBuilt(code))) {
            // sediment and rock never rest on wood or stonework (the terrain cleanup would take
            // them for loose fragments)
            return false;
        }
        for (int k = below + 1; k < y; k++) {
            v.set(x, k, z, FILL);
        }
        v.set(x, y, z, code);
        if (tint > 0) {
            v.setTint(x, y, z, tint);
        }
        return true;
    }

    /** Colour stop of a rock block: 0 at the top of the rock, 2 at its foot, with noisy boundaries. */
    private int stop(int x, int y, int z, int bottom, int top) {
        double t = top <= bottom ? 0.5 : (double) (y - bottom) / (top - bottom);
        t += 0.3 * p.noise3(LakePlanner.STRATA, x / 3.0, y / 3.0, z / 3.0);
        return t > 0.62 ? 0 : t > 0.3 ? 1 : 2;
    }

    /** Algae on a rock top in temperate, sunlit water, in coherent patches. */
    private boolean algae(LakeColumn c, int x, int y, int z) {
        return c.temperature > -0.5 && c.temperature < 0.5 && c.surf - y <= 14 && y <= c.surf
            && p.noise3(LakePlanner.ALGAE, x / 4.0, y / 4.0, z / 4.0) > 0.0;
    }

    // =========================================================================================
    // Bed
    // =========================================================================================

    /** Shelves and bars (fill up to the planned bed) and the patches on the bed's top block. */
    private void beds() {
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                LakeColumn c = v.column(x, z);
                if (!c.wet) {
                    continue;
                }
                for (int y = c.floor + 1; y <= c.bed; y++) {
                    v.set(x, y, z, c.fill);
                }
                if (c.patch != 0 && c.fill != GRAVEL) {
                    v.set(x, c.bed, z, c.patch);
                }
            }
        }
    }

    private void ledges() {
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                LakeColumn c = v.column(x, z);
                if (!c.wet || c.outcrop <= 0) {
                    continue;
                }
                int top = c.bed + c.outcrop;
                for (int y = c.bed + 1; y <= top; y++) {
                    byte code = (byte) (ROCK + c.family * 3 + stop(x, y, z, c.bed - 2, top));
                    if (y == top && algae(c, x, y, z)) {
                        code = MOSSY_COBBLE;
                    }
                    v.put(x, y, z, code);
                }
            }
        }
    }

    // =========================================================================================
    // Boulders
    // =========================================================================================

    private void boulders(double rockDensity) {
        if (rockDensity <= 0.0) {
            return;
        }
        long salt = p.salt(40);
        for (int gx = Math.floorDiv(v.coreMinX - BOULDER_REACH, BOULDER_CELL); gx <= Math.floorDiv(v.coreMinX + v.coreSizeX + BOULDER_REACH, BOULDER_CELL); gx++) {
            for (int gz = Math.floorDiv(v.coreMinZ - BOULDER_REACH, BOULDER_CELL); gz <= Math.floorDiv(v.coreMinZ + v.coreSizeZ + BOULDER_REACH, BOULDER_CELL); gz++) {
                long h = SeedMixer.hash2(salt, gx, gz);
                int sx = (int) Math.floor((gx + 0.1 + 0.8 * SeedMixer.cellUnit(h, 0, 0, 1)) * BOULDER_CELL);
                int sz = (int) Math.floor((gz + 0.1 + 0.8 * SeedMixer.cellUnit(h, 0, 0, 2)) * BOULDER_CELL);
                if (sx + BOULDER_REACH < v.coreMinX || sx - BOULDER_REACH >= v.coreMinX + v.coreSizeX
                    || sz + BOULDER_REACH < v.coreMinZ || sz - BOULDER_REACH >= v.coreMinZ + v.coreSizeZ) {
                    continue;
                }
                LakeColumn c = v.column(sx, sz);
                if (!c.wet || c.type == LakeType.MARSH) {
                    continue;
                }
                double field = smoothstep(-0.3, 0.4, p.noise(LakePlanner.FIELD, sx / 120.0, sz / 120.0));
                double density = LakePlanner.BOULDER_T[c.type] * (0.35 + 0.9 * c.rocky) * (0.3 + field);
                if (SeedMixer.unit(h) < 0.5 * density * rockDensity) {
                    boulder(sx, sz, c, h);
                }
            }
        }
    }

    /**
     * A boulder: two or three overlapping lumpy ellipsoids resting on the bed. Each column of it is
     * built from the bed up to the shape's top there, so the rock sits on the floor without
     * undercuts that would need the neighbouring columns to hold it. Small lakes get smaller
     * rocks; in the shallows of great and mountain lakes some break the surface as rock islets,
     * mossy on top where the climate is humid. Glacial lakes hold erratics of granite and diorite.
     * The whole boulder is dropped if any column of it would stand outside the lake, so every
     * chunk builds the same rock.
     */
    private void boulder(int sx, int sz, LakeColumn c, long h) {
        double cold = smoothstep(-0.15, -0.45, c.temperature);
        double u = SeedMixer.cellUnit(h, 0, 0, 3);
        double r = (1.2 + 2.2 * Math.pow(u, 1.6)) * (0.55 + 0.45 * c.size) + 0.5 * cold * SeedMixer.cellUnit(h, 0, 0, 4);
        r = Math.min(4.0, r);
        int water = c.surf - c.bed;
        boolean islet = water <= 3 && c.size > 0.4 && SeedMixer.cellUnit(h, 0, 0, 5) < 0.45
            && c.type != LakeType.CRATER && c.type != LakeType.MARSH;
        int maxTop = islet ? c.surf + 1 + (r > 2.4 ? 1 : 0) : c.surf - 1;
        int lobes = 2 + (SeedMixer.cellUnit(h, 0, 0, 6) < 0.5 ? 1 : 0);
        double[][] lobe = new double[lobes][6];
        for (int i = 0; i < lobes; i++) {
            double lr = r * (i == 0 ? 1.0 : 0.55 + 0.35 * SeedMixer.cellUnit(h, i, 1, 7));
            lobe[i][0] = i == 0 ? 0.0 : (SeedMixer.cellUnit(h, i, 2, 8) - 0.5) * r * 1.1;
            lobe[i][1] = i == 0 ? 0.0 : (SeedMixer.cellUnit(h, i, 3, 9) - 0.4) * r * 0.5;
            lobe[i][2] = i == 0 ? 0.0 : (SeedMixer.cellUnit(h, i, 4, 10) - 0.5) * r * 1.1;
            lobe[i][3] = lr * (0.9 + 0.35 * SeedMixer.cellUnit(h, i, 5, 11));
            lobe[i][4] = lr * (0.7 + 0.25 * SeedMixer.cellUnit(h, i, 6, 12));
            lobe[i][5] = lr * (0.9 + 0.35 * SeedMixer.cellUnit(h, i, 7, 13));
        }
        double cy = c.bed + 1 - r * (0.3 + 0.2 * SeedMixer.cellUnit(h, 0, 0, 14)) + r * 0.7;
        int erratic = -1;
        if (cold > 0.5 && (c.type == LakeType.GLACIAL || c.type == LakeType.MOUNTAIN) && SeedMixer.cellUnit(h, 0, 0, 15) < 0.25) {
            erratic = SeedMixer.cellUnit(h, 0, 0, 16) < 0.5 ? GRANITE : DIORITE;
        }
        int ext = 0;
        int yHi = Integer.MIN_VALUE;
        for (double[] l : lobe) {
            ext = Math.max(ext, (int) Math.ceil(Math.max(Math.abs(l[0]) + l[3], Math.abs(l[2]) + l[5]) * 1.1));
            yHi = Math.max(yHi, (int) Math.ceil(cy + l[1] + l[4] * 1.1));
        }
        ext = Math.min(ext, BOULDER_REACH);
        if (sx + ext < v.coreMinX || sx - ext >= v.coreMinX + v.coreSizeX || sz + ext < v.coreMinZ || sz - ext >= v.coreMinZ + v.coreSizeZ) {
            return;
        }
        int span = 2 * ext + 1;
        int[] tops = new int[span * span];
        double off = (h & 0xFFFF) * 0.37;
        for (int dx = -ext; dx <= ext; dx++) {
            for (int dz = -ext; dz <= ext; dz++) {
                LakeColumn col = v.column(sx + dx, sz + dz);
                int top = Integer.MIN_VALUE;
                int bottom = Integer.MAX_VALUE;
                for (int y = Math.min(yHi, maxTop); y >= col.bed - 2; y--) {
                    double inside = -1.0;
                    for (double[] l : lobe) {
                        double nx = (dx - l[0]) / l[3];
                        double ny = (y + 0.5 - cy - l[1]) / l[4];
                        double nz = (dz - l[2]) / l[5];
                        inside = Math.max(inside, 1.0 - (nx * nx + ny * ny + nz * nz));
                    }
                    if (inside > -0.18 && inside < 0.18) {
                        inside += 0.18 * p.noise3(LakePlanner.LUMPS, (sx + dx) / 2.5 + off, y / 2.5, (sz + dz) / 2.5);
                    }
                    if (inside > 0.0) {
                        top = Math.max(top, y);
                        bottom = Math.min(bottom, y);
                    }
                }
                // a column whose part of the rock hangs more than two blocks over the bed is left
                // out: the rock would need a skirt down to the floor there
                if (top < col.bed + 1 || bottom > col.bed + 3) {
                    top = Integer.MIN_VALUE;
                } else if (!col.wet || col.surf != c.surf) {
                    return;
                }
                tops[(dx + ext) * span + dz + ext] = top;
            }
        }
        for (int dx = -ext; dx <= ext; dx++) {
            for (int dz = -ext; dz <= ext; dz++) {
                int x = sx + dx;
                int z = sz + dz;
                int top = tops[(dx + ext) * span + dz + ext];
                if (top == Integer.MIN_VALUE || !v.inCore(x, z)) {
                    continue;
                }
                LakeColumn col = v.column(x, z);
                int from = col.bed + 1;
                boolean built = false;
                while (from <= top && LakeVolume.isSolid(v.get(x, from, z))) {
                    // rock never rests on wood or stonework: the terrain cleanup would take it for
                    // a loose fragment
                    built |= LakeVolume.isBuilt(v.get(x, from, z));
                    from++;
                }
                if (built || from <= top && !open(v.get(x, from, z))) {
                    continue;
                }
                for (int y = from; y <= top; y++) {
                    if (!open(v.get(x, y, z))) {
                        break;
                    }
                    byte code = erratic >= 0 ? (byte) erratic : (byte) (ROCK + col.family * 3 + stop(x, y, z, (int) Math.floor(cy - r), (int) Math.ceil(cy + r * 0.7)));
                    if (y == top && erratic < 0) {
                        if (y > col.surf && col.humidity > 0.1 && col.temperature > -0.35 && p.noise3(LakePlanner.ALGAE, x / 4.0, y / 4.0, z / 4.0) > -0.25) {
                            code = MOSS;
                        } else if (algae(col, x, y, z)) {
                            code = MOSSY_COBBLE;
                        }
                    }
                    v.set(x, y, z, code);
                }
            }
        }
    }

    // =========================================================================================
    // Rock islets
    // =========================================================================================

    private void skerries(double rockDensity) {
        if (rockDensity <= 0.0) {
            return;
        }
        long salt = p.salt(45);
        for (int gx = Math.floorDiv(v.coreMinX - SKERRY_REACH, SKERRY_CELL); gx <= Math.floorDiv(v.coreMinX + v.coreSizeX + SKERRY_REACH, SKERRY_CELL); gx++) {
            for (int gz = Math.floorDiv(v.coreMinZ - SKERRY_REACH, SKERRY_CELL); gz <= Math.floorDiv(v.coreMinZ + v.coreSizeZ + SKERRY_REACH, SKERRY_CELL); gz++) {
                long h = SeedMixer.hash2(salt, gx, gz);
                int sx = (int) Math.floor((gx + 0.15 + 0.7 * SeedMixer.cellUnit(h, 0, 0, 1)) * SKERRY_CELL);
                int sz = (int) Math.floor((gz + 0.15 + 0.7 * SeedMixer.cellUnit(h, 0, 0, 2)) * SKERRY_CELL);
                if (sx + SKERRY_REACH < v.coreMinX || sx - SKERRY_REACH >= v.coreMinX + v.coreSizeX
                    || sz + SKERRY_REACH < v.coreMinZ || sz - SKERRY_REACH >= v.coreMinZ + v.coreSizeZ) {
                    continue;
                }
                LakeColumn c = v.column(sx, sz);
                int water = c.surf - c.bed;
                if (!c.wet || water < 2 || water > 6 || c.size < 0.5) {
                    continue;
                }
                if (SeedMixer.unit(h) < SKERRY_T[c.type] * (0.5 + c.rocky) * Math.min(1.0, rockDensity)) {
                    skerry(sx, sz, c, h);
                }
            }
        }
    }

    /**
     * A rock islet: a whaleback of the local rock, smoothed by the ice, longer than wide, rising
     * from the bed out of the water by one to three blocks; moss on its top where the climate is
     * humid. Every column of it stands on the bed, and the whole islet is dropped if any column
     * would stand outside the lake.
     */
    private void skerry(int sx, int sz, LakeColumn c, long h) {
        double a = 2.5 + 3.0 * SeedMixer.cellUnit(h, 0, 0, 3);
        double b = a * (0.45 + 0.3 * SeedMixer.cellUnit(h, 0, 0, 4));
        double angle = SeedMixer.cellUnit(h, 0, 0, 5) * Math.PI;
        double ca = Math.cos(angle);
        double sa = Math.sin(angle);
        int peak = c.surf + 1 + (int) (SeedMixer.cellUnit(h, 0, 0, 6) * 2.5);
        int ext = (int) Math.ceil(a) + 1;
        int span = 2 * ext + 1;
        int[] tops = new int[span * span];
        double off = (h & 0xFFFF) * 0.29;
        for (int dx = -ext; dx <= ext; dx++) {
            for (int dz = -ext; dz <= ext; dz++) {
                double u = (dx * ca + dz * sa) / a;
                double w = (-dx * sa + dz * ca) / b;
                double q = u * u + w * w + 0.18 * p.noise3(LakePlanner.LUMPS, (sx + dx) / 3.0 + off, 0.0, (sz + dz) / 3.0);
                int top = Integer.MIN_VALUE;
                if (q < 1.0) {
                    LakeColumn col = v.column(sx + dx, sz + dz);
                    int t = (int) Math.floor(c.bed + (peak - c.bed + 0.5) * Math.sqrt(1.0 - q));
                    if (t >= col.bed + 1) {
                        if (!col.wet || col.surf != c.surf) {
                            return;
                        }
                        top = t;
                    }
                }
                tops[(dx + ext) * span + dz + ext] = top;
            }
        }
        for (int dx = -ext; dx <= ext; dx++) {
            for (int dz = -ext; dz <= ext; dz++) {
                int x = sx + dx;
                int z = sz + dz;
                int top = tops[(dx + ext) * span + dz + ext];
                if (top == Integer.MIN_VALUE || !v.inCore(x, z)) {
                    continue;
                }
                LakeColumn col = v.column(x, z);
                int from = col.bed + 1;
                boolean built = false;
                while (from <= top && LakeVolume.isSolid(v.get(x, from, z))) {
                    built |= LakeVolume.isBuilt(v.get(x, from, z));
                    from++;
                }
                if (built || from <= top && !open(v.get(x, from, z))) {
                    continue;
                }
                for (int y = from; y <= top; y++) {
                    byte code = (byte) (ROCK + col.family * 3 + stop(x, y, z, c.bed, peak));
                    if (y == top) {
                        if (y > col.surf && col.humidity > 0.1 && col.temperature > -0.35 && p.noise3(LakePlanner.ALGAE, x / 4.0, y / 4.0, z / 4.0) > -0.1) {
                            code = MOSS;
                        } else if (algae(col, x, y, z)) {
                            code = MOSSY_COBBLE;
                        }
                    }
                    v.set(x, y, z, code);
                }
            }
        }
    }

    // =========================================================================================
    // Sunken logs
    // =========================================================================================

    private void logs() {
        long salt = p.salt(50);
        for (int gx = Math.floorDiv(v.coreMinX - LOG_REACH, LOG_CELL); gx <= Math.floorDiv(v.coreMinX + v.coreSizeX + LOG_REACH, LOG_CELL); gx++) {
            for (int gz = Math.floorDiv(v.coreMinZ - LOG_REACH, LOG_CELL); gz <= Math.floorDiv(v.coreMinZ + v.coreSizeZ + LOG_REACH, LOG_CELL); gz++) {
                long h = SeedMixer.hash2(salt, gx, gz);
                int sx = (int) Math.floor((gx + 0.1 + 0.8 * SeedMixer.cellUnit(h, 0, 0, 1)) * LOG_CELL);
                int sz = (int) Math.floor((gz + 0.1 + 0.8 * SeedMixer.cellUnit(h, 0, 0, 2)) * LOG_CELL);
                if (sx + LOG_REACH < v.coreMinX || sx - LOG_REACH >= v.coreMinX + v.coreSizeX
                    || sz + LOG_REACH < v.coreMinZ || sz - LOG_REACH >= v.coreMinZ + v.coreSizeZ) {
                    continue;
                }
                LakeColumn c = v.column(sx, sz);
                if (!c.wet || c.type == LakeType.MARSH) {
                    continue;
                }
                int water = c.surf - c.bed;
                if (water > 22) {
                    continue;
                }
                // trees fall in from the shore and sink in the shallows; a few drift farther out
                double shore = 1.0 - smoothstep(0.1, 0.5, c.inner);
                double density = LakePlanner.LOG_T[c.type] * (0.25 + 0.75 * shore) * (water <= 12 ? 1.0 : 0.5);
                if (SeedMixer.unit(h) < 0.5 * density) {
                    log(sx, sz, c, h);
                }
            }
        }
    }

    /**
     * A fallen trunk lying on the bed along x or z. It follows the bed a block up or down at a
     * time and ends where the bed drops away, leaves the lake or meets a ledge, so every block of
     * it rests on the bed. One stretch may be half buried in the sediment and one stripped of its
     * bark; some trunks keep the stub of a branch. Small lakes get shorter logs.
     */
    private void log(int sx, int sz, LakeColumn c, long h) {
        int dir = (int) (SeedMixer.cellUnit(h, 0, 0, 3) * 4.0);
        int ux = LakeVolume.DIR_X[dir];
        int uz = LakeVolume.DIR_Z[dir];
        int len = 3 + (int) Math.round(SeedMixer.cellUnit(h, 0, 0, 4) * (2.0 + 4.0 * c.size));
        int[] ys = new int[len];
        int n = 0;
        for (int i = 0; i < len; i++) {
            LakeColumn col = v.column(sx + ux * i, sz + uz * i);
            if (!col.wet || col.surf != c.surf || col.outcrop > 0) {
                break;
            }
            int y = col.bed + 1;
            if (i > 0 && Math.abs(y - ys[i - 1]) > 1 || y > col.surf) {
                break;
            }
            ys[i] = y;
            n++;
        }
        if (n < 3) {
            return;
        }
        boolean alongX = ux != 0;
        int buriedFrom = SeedMixer.cellUnit(h, 0, 0, 5) < 0.3 ? 1 + (int) (SeedMixer.cellUnit(h, 0, 0, 6) * (n - 2)) : -1;
        int buriedLen = 1 + (int) (SeedMixer.cellUnit(h, 0, 0, 7) * 2.0);
        int rotFrom = SeedMixer.cellUnit(h, 0, 0, 8) < 0.35 ? (int) (SeedMixer.cellUnit(h, 0, 0, 9) * n) : -1;
        int rotLen = 1 + (int) (SeedMixer.cellUnit(h, 0, 0, 10) * 3.0);
        int branch = SeedMixer.cellUnit(h, 0, 0, 11) < 0.3 ? 1 + (int) (SeedMixer.cellUnit(h, 0, 0, 12) * (n - 2)) : -1;
        int tint = 0;
        for (int i = 0; i < n; i++) {
            int x = sx + ux * i;
            int z = sz + uz * i;
            if (!v.inCore(x, z)) {
                continue;
            }
            if (tint == 0) {
                tint = v.addItem(new LakeVolume.Item(sx, ys[0], sz, LakePlanner.woodGuess(c.temperature, c.humidity, h)));
            }
            boolean rotten = rotFrom >= 0 && i >= rotFrom && i < rotFrom + rotLen;
            byte code = rotten ? (alongX ? STRIPPED_X : STRIPPED_Z) : (alongX ? LOG_X : LOG_Z);
            LakeColumn col = v.column(x, z);
            boolean buried = buriedFrom >= 0 && i >= buriedFrom && i < buriedFrom + buriedLen && ys[i] - 1 == col.bed;
            if (buried) {
                // half sunk into the sediment: the log takes the place of the bed's top block
                v.set(x, ys[i] - 1, z, code);
                v.setTint(x, ys[i] - 1, z, tint);
                continue;
            }
            if (!stack(x, ys[i], z, code, tint, false)) {
                continue;
            }
            if (i == branch && ys[i] + 1 <= col.surf) {
                stack(x, ys[i] + 1, z, rotten ? STRIPPED_Y : LOG_Y, tint, false);
            }
        }
    }

    // =========================================================================================
    // Discoveries
    // =========================================================================================

    private void discoveries() {
        int cell = LakePlanner.DISCOVERY_CELL;
        int reach = 16;
        for (int cx = Math.floorDiv(v.coreMinX - reach, cell); cx <= Math.floorDiv(v.coreMinX + v.coreSizeX + reach, cell); cx++) {
            for (int cz = Math.floorDiv(v.coreMinZ - reach, cell); cz <= Math.floorDiv(v.coreMinZ + v.coreSizeZ + reach, cell); cz++) {
                LakePlanner.Discovery d = p.discovery(cx, cz);
                if (d == null) {
                    continue;
                }
                int r = d.reach();
                if (d.x() + r < v.coreMinX || d.x() - r >= v.coreMinX + v.coreSizeX || d.z() + r < v.coreMinZ || d.z() - r >= v.coreMinZ + v.coreSizeZ) {
                    continue;
                }
                int tint = v.addItem(new LakeVolume.Item(d.x(), d.y(), d.z(), d.wood()));
                switch (d.kind()) {
                    case LakePlanner.WRECK -> wreck(d, tint);
                    case LakePlanner.HUT -> hut(d);
                    case LakePlanner.TOWER -> tower(d);
                    case LakePlanner.PIER -> pier(d, tint);
                    default -> {
                    }
                }
            }
        }
    }

    private static int opposite(int dir) {
        return (dir + 2) & 3;
    }

    /**
     * A small rowing boat lying on the bed: a keel of planks, plank sides with a low gunwale of
     * slabs, a transom at the stern, a pointed bow with its stem post, a thwart across the middle,
     * sometimes a chest under the stern seat. One side is stove in, and sediment has drifted into
     * the hull and against it.
     */
    private void wreck(LakePlanner.Discovery d, int tint) {
        SplittableRandom rnd = new SplittableRandom(d.seed());
        int dir = d.dir();
        int ux = LakeVolume.DIR_X[dir];
        int uz = LakeVolume.DIR_Z[dir];
        int vx = -uz;
        int vz = ux;
        int len = d.a();
        int u0 = -len / 2;
        int y0 = d.y() + 1;
        int broken = rnd.nextBoolean() ? 1 : -1;
        int breakFrom = 1 + rnd.nextInt(Math.max(1, len - 3));
        int breakLen = 1 + rnd.nextInt(3);
        boolean chest = rnd.nextDouble() < 0.35;
        boolean stem = rnd.nextDouble() < 0.6;
        int seat = len / 2;
        for (int i = 0; i < len; i++) {
            int u = u0 + i;
            boolean bow = i == len - 1;
            boolean stern = i == 0;
            int cx = d.x() + ux * u;
            int cz = d.z() + uz * u;
            if (bow) {
                stack(cx, y0, cz, (byte) (STAIRS + opposite(dir)), tint, false);
                if (stem) {
                    stack(cx, y0 + 1, cz, FENCE, tint, false);
                }
                continue;
            }
            stack(cx, y0, cz, PLANKS, tint, false);
            if (stern) {
                stack(cx, y0 + 1, cz, PLANKS, tint, false);
                continue;
            }
            if (i == 1 && chest) {
                stack(cx, y0 + 1, cz, (byte) (CHEST + dir), tint, false);
            } else if (i == seat) {
                stack(cx, y0 + 1, cz, SLAB_TOP, tint, false);
            }
            for (int s = -1; s <= 1; s += 2) {
                int x = cx + vx * s;
                int z = cz + vz * s;
                boolean hole = s == broken && i >= breakFrom && i < breakFrom + breakLen;
                if (hole && rnd.nextDouble() < 0.6) {
                    continue;
                }
                stack(x, y0, z, PLANKS, tint, false);
                if (!hole) {
                    stack(x, y0 + 1, z, SLAB, tint, false);
                }
            }
        }
        // sediment drifted against the broken side
        for (int i = -1; i <= len; i++) {
            int u = u0 + i;
            if (rnd.nextDouble() < 0.5) {
                stack(d.x() + ux * u + vx * broken * 2, d.z() + uz * u + vz * broken * 2, y0, FILL, 0);
            }
        }
    }

    private boolean stack(int x, int z, int y, byte code, int tint) {
        return stack(x, y, z, code, tint, false);
    }

    /** The stone of a drowned building: weathered in coherent patches, mossy under water. */
    private byte masonry(int x, int y, int z, boolean bricks, int surf) {
        double n = p.noise3(LakePlanner.WEATHER, x / 3.5, y / 3.5, z / 3.5);
        if (!bricks) {
            return n > 0.1 && y <= surf ? MOSSY_COBBLE : COBBLE;
        }
        if (n > 0.2 && y <= surf) {
            return MOSSY_BRICKS;
        }
        return n < -0.35 ? CRACKED_BRICKS : STONE_BRICKS;
    }

    /**
     * The walls of a drowned hut, one to three courses high (the corners a course higher), with a
     * doorway, a hearth stack in one corner, sediment drifted inside, a fall of rubble along the
     * wall that collapsed, and sometimes a chest.
     */
    private void hut(LakePlanner.Discovery d) {
        SplittableRandom rnd = new SplittableRandom(d.seed());
        int dir = d.dir();
        int ux = LakeVolume.DIR_X[dir];
        int uz = LakeVolume.DIR_Z[dir];
        int vx = -uz;
        int vz = ux;
        int a = d.a();
        int b = d.b();
        int u0 = -a / 2;
        int u1 = u0 + a - 1;
        int v0 = -b / 2;
        int v1 = v0 + b - 1;
        int y0 = d.y() + 1;
        int maxTop = d.surf() - 1;
        boolean bricks = rnd.nextDouble() < 0.35 || d.family() == MarinePlanner.PALE;
        int door = u0 + 1 + rnd.nextInt(Math.max(1, a - 2));
        int fallen = rnd.nextInt(4);
        double decay = 0.3 + 0.6 * rnd.nextDouble();
        for (int u = u0; u <= u1; u++) {
            for (int w = v0; w <= v1; w++) {
                int x = d.x() + ux * u + vx * w;
                int z = d.z() + uz * u + vz * w;
                boolean edge = u == u0 || u == u1 || w == v0 || w == v1;
                if (!edge) {
                    if (p.noise(LakePlanner.SED_PATCH, x / 3.0 + 9.0, z / 3.0) > 0.0) {
                        stack(x, y0, z, FILL, 0, false);
                    }
                    continue;
                }
                if (w == v0 && u == door) {
                    continue;
                }
                boolean corner = (u == u0 || u == u1) && (w == v0 || w == v1);
                int side = w == v0 ? 0 : u == u1 ? 1 : w == v1 ? 2 : 3;
                double n = 0.5 + 0.5 * p.noise(LakePlanner.WEATHER, x / 4.0, z / 4.0);
                int height = 1 + (int) Math.floor(3.0 * n * (1.0 - decay * (side == fallen ? 0.9 : 0.4)));
                height = Math.min(height + (corner ? 1 : 0), 4);
                for (int y = y0; y < y0 + height && y <= maxTop; y++) {
                    if (!stack(x, y, z, masonry(x, y, z, bricks, d.surf()), 0, false)) {
                        break;
                    }
                }
            }
        }
        // the hearth stack in a back corner
        if (rnd.nextDouble() < 0.5) {
            int x = d.x() + ux * (u1 - 1) + vx * (v1 - 1);
            int z = d.z() + uz * (u1 - 1) + vz * (v1 - 1);
            int height = 2 + rnd.nextInt(3);
            for (int y = y0; y < y0 + height && y <= maxTop; y++) {
                if (!stack(x, y, z, masonry(x, y, z, bricks, d.surf()), 0, false) && !LakeVolume.isSolid(v.get(x, y, z))) {
                    break;
                }
            }
        }
        if (rnd.nextDouble() < 0.3) {
            int x = d.x() + ux * (u0 + 1) + vx * (v1 - 1);
            int z = d.z() + uz * (u0 + 1) + vz * (v1 - 1);
            int y = y0;
            while (LakeVolume.isSolid(v.get(x, y, z)) && y < maxTop) {
                y++;
            }
            stack(x, y, z, (byte) (CHEST + dir), v.addItem(new LakeVolume.Item(d.x(), d.y(), d.z(), d.wood())), false);
        }
        // rubble of the fallen wall
        int fx = fallen == 0 ? 0 : fallen == 1 ? 1 : fallen == 2 ? 0 : -1;
        int fz = fallen == 0 ? -1 : fallen == 2 ? 1 : 0;
        for (int k = 0; k < a + b; k++) {
            int u = u0 + rnd.nextInt(a);
            int w = v0 + rnd.nextInt(b);
            int du = fx * (a / 2 + 1 + rnd.nextInt(2));
            int dw = fz * (b / 2 + 1 + rnd.nextInt(2));
            int lu = fx != 0 ? du : u;
            int lw = fz != 0 ? dw : w;
            int x = d.x() + ux * lu + vx * lw;
            int z = d.z() + uz * lu + vz * lw;
            LakeColumn col = v.column(x, z);
            if (col.wet && col.bed + 1 <= maxTop) {
                stack(x, col.bed + 1, z, rnd.nextBoolean() ? COBBLE : masonry(x, col.bed + 1, z, bricks, d.surf()), 0, false);
            }
        }
    }

    /**
     * The stump of a drowned round tower: the wall stands up to nine courses on one side, where
     * it may rise out of the water, and has collapsed to a course or two on the other, into a fan
     * of rubble on the floor. A doorway faces away from the fall; sediment lies inside.
     */
    private void tower(LakePlanner.Discovery d) {
        SplittableRandom rnd = new SplittableRandom(d.seed());
        int r = d.b();
        int y0 = d.y() + 1;
        int ht = 5 + rnd.nextInt(5);
        int maxTop = d.surf() + 2;
        double theta0 = d.dir() * Math.PI / 2.0 + (rnd.nextDouble() - 0.5) * 1.2;
        double decay = 0.5 + 0.4 * rnd.nextDouble();
        double doorAngle = theta0 + Math.PI;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.hypot(dx, dz);
                int x = d.x() + dx;
                int z = d.z() + dz;
                if (dist > r + 0.4) {
                    continue;
                }
                if (dist < r - 0.6) {
                    if (p.noise(LakePlanner.SED_PATCH, x / 3.0 - 9.0, z / 3.0) > -0.2) {
                        stack(x, y0, z, FILL, 0, false);
                    }
                    continue;
                }
                double theta = Math.atan2(dz, dx);
                double toDoor = Math.abs(Math.atan2(Math.sin(theta - doorAngle), Math.cos(theta - doorAngle)));
                int h = Math.max(1, (int) Math.round(ht * (1.0 - decay * smoothstep(-0.2, 0.8, Math.cos(theta - theta0)))));
                boolean doorway = toDoor < 0.45 / r * 2.0;
                for (int y = y0; y < y0 + h && y <= maxTop; y++) {
                    if (doorway && y < y0 + 2) {
                        continue;
                    }
                    if (doorway && y == y0 + 2) {
                        // the lintel would bridge the doorway: the wall above it has fallen
                        break;
                    }
                    if (!stack(x, y, z, masonry(x, y, z, true, d.surf()), 0, true)) {
                        break;
                    }
                }
            }
        }
        double fx = Math.cos(theta0);
        double fz = Math.sin(theta0);
        for (int k = 1; k <= 5; k++) {
            for (int s = -2; s <= 2; s++) {
                int x = (int) Math.round(d.x() + fx * (r + k) - fz * s);
                int z = (int) Math.round(d.z() + fz * (r + k) + fx * s);
                double chance = 1.0 - k / 6.0 - Math.abs(s) * 0.15;
                if (rnd.nextDouble() >= chance) {
                    continue;
                }
                LakeColumn col = v.column(x, z);
                if (!col.wet) {
                    continue;
                }
                int height = k <= 2 ? 2 : 1;
                for (int y = col.bed + 1; y <= col.bed + height && y <= col.surf - 1; y++) {
                    byte code = k >= 4 && y == col.bed + 1 ? BRICK_SLAB : masonry(x, y, z, true, d.surf());
                    if (!stack(x, y, z, code, 0, false) || code == BRICK_SLAB) {
                        break;
                    }
                }
            }
        }
        if (rnd.nextDouble() < 0.25) {
            int y = y0;
            while (LakeVolume.isSolid(v.get(d.x(), y, d.z())) && y < d.surf() - 1) {
                y++;
            }
            stack(d.x(), y, d.z(), (byte) (CHEST + d.dir()), v.addItem(new LakeVolume.Item(d.x(), d.y(), d.z(), d.wood())), false);
        }
    }

    /**
     * The pilings of an old pier: two rows of posts reaching out from the shallows, most standing
     * a block out of the water, some rotted off below the surface, and a few planks of the deck
     * lying on the bed between them.
     */
    private void pier(LakePlanner.Discovery d, int tint) {
        SplittableRandom rnd = new SplittableRandom(d.seed());
        int dir = d.dir();
        int ux = LakeVolume.DIR_X[dir];
        int uz = LakeVolume.DIR_Z[dir];
        int vx = -uz;
        int vz = ux;
        for (int u = 1; u < d.a(); u += 2) {
            for (int s = -1; s <= 1; s += 2) {
                int x = d.x() + ux * u + vx * s;
                int z = d.z() + uz * u + vz * s;
                LakeColumn col = v.column(x, z);
                if (!col.wet) {
                    continue;
                }
                double roll = rnd.nextDouble();
                if (roll < 0.15) {
                    continue;
                }
                int top = roll < 0.65 ? col.surf + 1 : col.bed + 1 + rnd.nextInt(Math.max(1, col.surf - col.bed));
                byte code = rnd.nextDouble() < 0.3 ? STRIPPED_Y : LOG_Y;
                for (int y = col.bed + 1; y <= top; y++) {
                    if (!stack(x, y, z, code, tint, true)) {
                        break;
                    }
                }
            }
            if (rnd.nextDouble() < 0.3) {
                int x = d.x() + ux * u;
                int z = d.z() + uz * u;
                LakeColumn col = v.column(x, z);
                if (col.wet) {
                    stack(x, col.bed + 1, z, rnd.nextBoolean() ? PLANKS : SLAB, tint, false);
                }
            }
        }
    }

    // =========================================================================================
    // Scatter
    // =========================================================================================

    /** Pebbles and knee-high rocks matching the rock family. */
    private static int stoneOf(int family, double roll, boolean mossy) {
        return switch (family) {
            case MarinePlanner.DARK -> roll < 0.6 ? RockRaster.DEEPSLATE : RockRaster.TUFF;
            case MarinePlanner.PALE -> roll < 0.7 ? RockRaster.STONE_DIORITE : RockRaster.STONE;
            case MarinePlanner.SANDY -> RockRaster.SANDSTONE;
            case MarinePlanner.VOLCANIC -> RockRaster.BLACKSTONE;
            default -> mossy && roll < 0.35 ? RockRaster.MOSSY : roll < 0.65 ? RockRaster.STONE : RockRaster.ANDESITE;
        };
    }

    /** Single cobbles, pebbles and knee-high rocks on the bed, densest on rocky floors. */
    private void scatter(double detailDensity) {
        long salt = p.salt(41);
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                LakeColumn c = v.column(x, z);
                if (!c.wet || c.type == LakeType.MARSH) {
                    continue;
                }
                int y = c.bed + c.outcrop + 1;
                if (y > c.surf || v.get(x, y, z) != NAT_WATER || !LakeVolume.isSolid(v.get(x, y - 1, z))) {
                    continue;
                }
                long h = SeedMixer.hash2(salt, x, z);
                double r = SeedMixer.unit(h);
                double roll = SeedMixer.cellUnit(h, 0, 0, 1);
                boolean mossy = c.temperature > -0.4 && c.temperature < 0.5 && c.surf - y <= 12;
                double rocky = 0.25 + c.rocky;
                if (r < 0.002 * c.rocky) {
                    v.set(x, y, z, mossy ? MOSSY_COBBLE : COBBLE);
                } else if (r < 0.002 * c.rocky + 0.005 * rocky * detailDensity) {
                    v.set(x, y, z, (byte) (ROCK_DECOR + stoneOf(c.family, roll, mossy)));
                } else if (r < 0.002 * c.rocky + 0.022 * rocky * detailDensity) {
                    v.set(x, y, z, (byte) (PEBBLES + stoneOf(c.family, roll, mossy)));
                }
            }
        }
    }
}
