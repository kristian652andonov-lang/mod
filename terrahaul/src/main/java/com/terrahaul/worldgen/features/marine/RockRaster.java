package com.terrahaul.worldgen.features.marine;

import static com.terrahaul.worldgen.features.marine.MarineVolume.COBBLE;
import static com.terrahaul.worldgen.features.marine.MarineVolume.DIORITE;
import static com.terrahaul.worldgen.features.marine.MarineVolume.GRANITE;
import static com.terrahaul.worldgen.features.marine.MarineVolume.GRAVEL;
import static com.terrahaul.worldgen.features.marine.MarineVolume.MOSSY_COBBLE;
import static com.terrahaul.worldgen.features.marine.MarineVolume.NAT_SOLID;
import static com.terrahaul.worldgen.features.marine.MarineVolume.NAT_WATER;
import static com.terrahaul.worldgen.features.marine.MarineVolume.PEBBLES;
import static com.terrahaul.worldgen.features.marine.MarineVolume.ROCK;
import static com.terrahaul.worldgen.features.marine.MarineVolume.ROCK_DECOR;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;

/**
 * Rasterises the rock of a {@link MarineVolume}: bedrock ledges, boulders, and the gravel,
 * cobbles, pebbles and knee-high rocks scattered on sea floors. Pure Java.
 * <p>
 * Every rock belongs to one colour family (see {@link MarinePlanner#FAMILY_NAMES}), graded from
 * its top colour down to its foot with coherent noise boundaries, like the land's material fades.
 * In cool, sunlit water the upper surfaces carry a coat of algae (mossy cobblestone in patches).
 * Boulders are densest below sea cliffs (talus), in fjords, on scoured slopes, on glaciated cold
 * shelves (where some are erratics of granite or diorite dropped by ice) and in kelp forests,
 * whose holdfasts need rock; they cluster in boulder fields. In the surf off a sandy beach (at most
 * {@link MarinePlanner#SURF_ZONE} blocks deep) boulders are of the beach's sandstone.
 * <p>
 * Boulders are planned on a jittered grid from position hashes and the terrain model, and each
 * chunk builds only the columns it owns. Every boulder is checked block by block for a connected
 * path to the floor, so no piece of one ever floats.
 */
public final class RockRaster {
    /** Stone indices of {@code TerrahaulBlocks.STONES}, for pebbles and knee-high rocks. */
    public static final int STONE = 0;
    public static final int MOSSY = 1;
    public static final int ANDESITE = 2;
    public static final int STONE_GRANITE = 3;
    public static final int STONE_DIORITE = 4;
    public static final int DEEPSLATE = 5;
    public static final int TUFF = 6;
    public static final int SANDSTONE = 7;
    public static final int BLACKSTONE = 9;

    private static final int BOULDER_CELL = 12;
    /** Farthest a boulder block lies from its site (radius at most 6, lobes and lumps included). */
    private static final int BOULDER_REACH = 13;
    private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    /** Margin around the core that a rock volume needs. */
    public static final int MARGIN = 0;

    private final MarineVolume v;
    private final MarinePlanner p;
    private final int surf;

    /** Grottos near the chunk, whose mouths rocks keep clear. */
    private final java.util.List<GrottoPlan.Grotto> grottos;

    private RockRaster(MarineVolume v) {
        this.v = v;
        this.p = v.planner();
        this.surf = p.surf;
        this.grottos = GrottoPlan.of(p).nearCorridors(v);
    }

    private boolean blocksGrotto(double x, double z, double margin) {
        for (GrottoPlan.Grotto g : grottos) {
            if (GrottoPlan.inCorridor(g, x, z, margin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Plans the rocks of the volume's core.
     *
     * @param rockDensity multiplier for boulders (the rock density option)
     * @param detailDensity multiplier for pebbles and knee-high rocks (the ground detail option)
     */
    public static void build(MarineVolume v, double rockDensity, double detailDensity) {
        RockRaster r = new RockRaster(v);
        r.ledges();
        r.boulders(rockDensity);
        r.scatter(detailDensity);
    }

    /** Colour stop of a rock block: 0 at the top of the rock, 2 at its foot, with noisy boundaries. */
    private int stop(int x, int y, int z, int bottom, int top) {
        double t = top <= bottom ? 0.5 : (double) (y - bottom) / (top - bottom);
        t += 0.3 * p.noise3(MarinePlanner.STRATA, x / 3.0, y / 3.0, z / 3.0);
        return t > 0.62 ? 0 : t > 0.3 ? 1 : 2;
    }

    /** Algae coat on an exposed rock top in cool, sunlit water, in coherent patches. */
    private boolean algae(MarineColumn c, int x, int y, int z) {
        return c.temperature < 0.38 && surf - y <= 20 && c.temperature > -0.6 && p.noise3(MarinePlanner.ALGAE, x / 4.0, y / 4.0, z / 4.0) > 0.0;
    }

    // =========================================================================================
    // Bedrock ledges
    // =========================================================================================

    private void ledges() {
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                MarineColumn c = v.column(x, z);
                if (c.outcrop <= 0 || blocksGrotto(x + 0.5, z + 0.5, 0.0)) {
                    continue;
                }
                int top = c.floor + c.outcrop;
                for (int y = c.floor + 1; y <= top; y++) {
                    byte code = (byte) (ROCK + c.family * 3 + stop(x, y, z, c.floor - 2, top));
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

    /** How likely a boulder is around a site: by depth, rockiness and kelp, in boulder fields. */
    private double density(MarineColumn c, int x, int z) {
        double base = c.depth < 25 ? 0.35 : c.depth < 70 ? 0.2 : 0.08;
        double d = base + 0.6 * c.rocky + 0.3 * smoothstep(0.15, 0.6, c.slope) + 0.4 * c.kelp;
        double field = smoothstep(-0.3, 0.4, p.noise(MarinePlanner.FIELD, x / 150.0, z / 150.0));
        return d * (0.25 + field) * (1.0 - smoothstep(0.1, 0.4, c.reefObjects));
    }

    private void boulders(double rockDensity) {
        long salt = p.salt(40);
        for (int gx = Math.floorDiv(v.coreMinX - BOULDER_REACH, BOULDER_CELL); gx <= Math.floorDiv(v.coreMinX + v.coreSizeX + BOULDER_REACH, BOULDER_CELL); gx++) {
            for (int gz = Math.floorDiv(v.coreMinZ - BOULDER_REACH, BOULDER_CELL); gz <= Math.floorDiv(v.coreMinZ + v.coreSizeZ + BOULDER_REACH, BOULDER_CELL); gz++) {
                long h = SeedMixer.hash2(salt, gx, gz);
                int sx = (int) Math.floor((gx + 0.15 + 0.7 * SeedMixer.cellUnit(h, 0, 0, 1)) * BOULDER_CELL);
                int sz = (int) Math.floor((gz + 0.15 + 0.7 * SeedMixer.cellUnit(h, 0, 0, 2)) * BOULDER_CELL);
                if (sx + BOULDER_REACH < v.coreMinX || sx - BOULDER_REACH >= v.coreMinX + v.coreSizeX
                    || sz + BOULDER_REACH < v.coreMinZ || sz - BOULDER_REACH >= v.coreMinZ + v.coreSizeZ) {
                    continue;
                }
                MarineColumn c = v.column(sx, sz);
                if (!c.sea || c.depth < 3 || c.slope > 1.2 || c.trench > 0.3 || blocksGrotto(sx + 0.5, sz + 0.5, 7.0)) {
                    continue;
                }
                if (SeedMixer.unit(h) < 0.55 * density(c, sx, sz) * rockDensity) {
                    boulder(sx, sz, c, h);
                }
            }
        }
    }

    /**
     * A boulder: two or three overlapping ellipsoids with a lumpy surface, sunk into the floor.
     * Shallow-water boulders are rounded and small, deep ones larger (dropstones); boulders below
     * sea cliffs may break the surface. The shape is built in a local box and every block without a
     * connected path to the floor is dropped, so the result is identical in every chunk.
     */
    private void boulder(int sx, int sz, MarineColumn c, long h) {
        double cold = smoothstep(-0.15, -0.45, c.temperature);
        double cliff = smoothstep(0.1, 0.4, c.coastCliff);
        double u = SeedMixer.cellUnit(h, 0, 0, 3);
        double big = Math.pow(u, 1.5);
        double r = c.depth < 25 ? 1.5 + 2.4 * big : c.depth < 70 ? 2.0 + 2.8 * big : 2.5 + 3.0 * big;
        r = Math.min(6.0, r + 0.6 * cold * SeedMixer.cellUnit(h, 0, 0, 4) + 0.8 * cliff * SeedMixer.cellUnit(h, 0, 0, 5));
        boolean surfRock = cliff > 0.5 && c.depth < 6;
        double maxTop = surfRock ? surf + 2 : surf - 1;
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
        double sink = r * (0.3 + 0.2 * SeedMixer.cellUnit(h, 0, 0, 14));
        double cy = c.floor + 1 - sink + r * 0.7;
        int family = c.family;
        // in the surf off a sandy beach a boulder is of the beach's sandstone, as the stones on the
        // sand beside it are, without an algae coat or an erratic of grey rock; volcanic coasts
        // keep their dark rock
        boolean beachRock = family != MarinePlanner.VOLCANIC && c.depth <= MarinePlanner.SURF_ZONE
            && MarinePlanner.sandyShore(p.model(), sx, sz, 32);
        if (beachRock) {
            family = MarinePlanner.SANDY;
        }
        int erratic = -1;
        if (!beachRock && cold > 0.5 && SeedMixer.cellUnit(h, 0, 0, 15) < 0.2) {
            erratic = SeedMixer.cellUnit(h, 0, 0, 16) < 0.5 ? GRANITE : DIORITE;
        }
        // the box holding every lobe, grown for the lumps on the surface
        int ext = 0;
        int yHi = Integer.MIN_VALUE;
        int yLo = Integer.MAX_VALUE;
        for (double[] l : lobe) {
            ext = Math.max(ext, (int) Math.ceil(Math.max(Math.abs(l[0]) + l[3], Math.abs(l[2]) + l[5]) * 1.1));
            yHi = Math.max(yHi, (int) Math.ceil(cy + l[1] + l[4] * 1.1));
            yLo = Math.min(yLo, (int) Math.floor(cy + l[1] - l[4] * 1.1));
        }
        int span = 2 * ext + 1;
        int y0 = yLo;
        int ySpan = Math.min(yHi, (int) maxTop) - yLo + 1;
        if (ySpan <= 0 || sx + ext < v.coreMinX || sx - ext >= v.coreMinX + v.coreSizeX || sz + ext < v.coreMinZ || sz - ext >= v.coreMinZ + v.coreSizeZ) {
            return;
        }
        boolean[] in = new boolean[span * span * ySpan];
        double off = (h & 0xFFFF) * 0.37;
        for (int dx = -ext; dx <= ext; dx++) {
            for (int dz = -ext; dz <= ext; dz++) {
                MarineColumn col = v.column(sx + dx, sz + dz);
                for (int dy = 0; dy < ySpan; dy++) {
                    int y = y0 + dy;
                    if (y < col.floor - 3) {
                        continue;
                    }
                    double inside = -1.0;
                    for (double[] l : lobe) {
                        double nx = (dx - l[0]) / l[3];
                        double ny = (y + 0.5 - cy - l[1]) / l[4];
                        double nz = (dz - l[2]) / l[5];
                        inside = Math.max(inside, 1.0 - (nx * nx + ny * ny + nz * nz));
                    }
                    // the lumps only matter near the surface of the ellipsoids
                    if (inside > -0.18 && inside < 0.18) {
                        inside += 0.18 * p.noise3(MarinePlanner.LUMPS, (sx + dx) / 2.5 + off, y / 2.5, (sz + dz) / 2.5);
                    }
                    if (inside > 0.0) {
                        in[((dx + ext) * span + (dz + ext)) * ySpan + dy] = true;
                    }
                }
            }
        }
        // anchoring: keep only blocks connected to the floor
        boolean[] kept = new boolean[in.length];
        int[] queue = new int[in.length];
        int head = 0;
        int tail = 0;
        for (int dx = -ext; dx <= ext; dx++) {
            for (int dz = -ext; dz <= ext; dz++) {
                int floor = v.column(sx + dx, sz + dz).floor;
                for (int dy = 0; dy < ySpan; dy++) {
                    int i = ((dx + ext) * span + (dz + ext)) * ySpan + dy;
                    if (in[i] && y0 + dy <= floor + 1) {
                        kept[i] = true;
                        queue[tail++] = i;
                    }
                }
            }
        }
        while (head < tail) {
            int i = queue[head++];
            int dy = i % ySpan;
            int dz = i / ySpan % span;
            int dx = i / (ySpan * span);
            for (int[] s : STEPS) {
                int nx = dx + s[0];
                int ny = dy + s[1];
                int nz = dz + s[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= span || ny >= ySpan || nz >= span) {
                    continue;
                }
                int j = (nx * span + nz) * ySpan + ny;
                if (in[j] && !kept[j]) {
                    kept[j] = true;
                    queue[tail++] = j;
                }
            }
        }
        // build the owned columns, top colour above, foot colour below, algae on the top
        for (int dx = -ext; dx <= ext; dx++) {
            for (int dz = -ext; dz <= ext; dz++) {
                int x = sx + dx;
                int z = sz + dz;
                if (!v.inCore(x, z)) {
                    continue;
                }
                MarineColumn col = v.column(x, z);
                int base = ((dx + ext) * span + (dz + ext)) * ySpan;
                int top = Integer.MIN_VALUE;
                int bottom = Integer.MAX_VALUE;
                for (int dy = 0; dy < ySpan; dy++) {
                    if (kept[base + dy]) {
                        top = Math.max(top, y0 + dy);
                        bottom = Math.min(bottom, y0 + dy);
                    }
                }
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                for (int y = bottom; y <= top; y++) {
                    if (!kept[base + y - y0]) {
                        continue;
                    }
                    byte code = erratic >= 0 ? (byte) erratic : (byte) (ROCK + family * 3 + stop(x, y, z, (int) Math.floor(cy - r), (int) Math.ceil(cy + r * 0.7)));
                    boolean exposedTop = y == top || !kept[base + y - y0 + 1];
                    if (exposedTop && y <= surf && erratic < 0 && !beachRock && algae(col, x, y, z)) {
                        code = MOSSY_COBBLE;
                    }
                    byte here = v.get(x, y, z);
                    if (y > surf ? here == NAT_WATER : MarineVolume.isWaterLike(here) || here == NAT_SOLID) {
                        v.set(x, y, z, code);
                    }
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
            case MarinePlanner.DARK -> roll < 0.6 ? DEEPSLATE : TUFF;
            case MarinePlanner.PALE -> roll < 0.7 ? STONE_DIORITE : STONE;
            case MarinePlanner.SANDY -> SANDSTONE;
            case MarinePlanner.VOLCANIC -> BLACKSTONE;
            default -> mossy && roll < 0.35 ? MOSSY : roll < 0.65 ? STONE : ANDESITE;
        };
    }

    /**
     * Gravel patches on shallow cool floors (rubble aprons around ledges), and single cobbles,
     * pebbles and knee-high rocks, densest on rocky floors; manganese nodules (dark pebbles) on the
     * abyssal plains.
     */
    private void scatter(double detailDensity) {
        long salt = p.salt(41);
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                MarineColumn c = v.column(x, z);
                if (!c.sea || c.depth < 2 || c.trench > 0.2 || c.reefObjects > 0.25 || blocksGrotto(x + 0.5, z + 0.5, 0.0)) {
                    continue;
                }
                int y = c.floor + 1;
                if (v.get(x, y, z) != NAT_WATER || v.get(x, c.floor, z) != NAT_SOLID) {
                    continue;
                }
                long h = SeedMixer.hash2(salt, x, z);
                double r = SeedMixer.unit(h);
                double roll = SeedMixer.cellUnit(h, 0, 0, 1);
                boolean cool = c.temperature < 0.38;
                boolean photic = c.depth <= 20;
                if (cool && c.depth <= 30 && p.noise(MarinePlanner.GRAVEL, x / 11.0, z / 11.0) > 0.52 - 0.35 * c.rocky) {
                    v.set(x, c.floor, z, GRAVEL);
                }
                if (c.depth > 90) {
                    if (r < 0.02 * detailDensity) {
                        v.set(x, y, z, (byte) (PEBBLES + BLACKSTONE));
                    }
                    continue;
                }
                if (c.depth > 60) {
                    continue;
                }
                double rocky = 0.5 + c.rocky;
                if (r < 0.002 * c.rocky) {
                    v.set(x, y, z, cool && photic ? MOSSY_COBBLE : COBBLE);
                } else if (r < 0.002 * c.rocky + 0.005 * rocky * detailDensity) {
                    v.set(x, y, z, (byte) (ROCK_DECOR + stoneOf(c.family, roll, cool && photic)));
                } else if (r < 0.002 * c.rocky + 0.02 * rocky * detailDensity) {
                    v.set(x, y, z, (byte) (PEBBLES + stoneOf(c.family, roll, cool && photic)));
                }
            }
        }
    }
}
