package com.terrahaul.worldgen.features.marine;

import static com.terrahaul.worldgen.noise.TMath.clamp;
import static com.terrahaul.worldgen.noise.TMath.lerp;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;
import com.terrahaul.worldgen.noise.TMath;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plans the life and rock of Terrahaul's sea floors from the terrain model alone: where coral reefs,
 * kelp forests, seagrass meadows and rocky ledges grow, which corals dominate a stretch of reef, and
 * the deterministic sites of patch reefs. Pure Java (no Minecraft classes), so the features, the
 * finder and the devtools all see exactly the same plan.
 * <p>
 * The habitats follow the real ones:
 * <ul>
 *   <li>coral reefs need warm, clear, sunlit water: they grow on warm shelves less than about 30
 *       blocks deep, away from river mouths and fjords. Next to the shore they form fringing reefs
 *       (a sandy lagoon, a reef flat just below the surface and a steep reef front); farther out
 *       they stand as patch reefs and coral heads;</li>
 *   <li>kelp forests grow in cool water 3-30 blocks deep, best on rock;</li>
 *   <li>seagrass meadows cover sandy floors 1-20 blocks deep, except under sea ice;</li>
 *   <li>bare rock shows where currents scour the floor: below sea cliffs, in fjords, on steep
 *       slopes, on glaciated cold shelves and on mid-ocean ridges.</li>
 * </ul>
 * One planner exists per terrain model; its noises are immutable and its site caches are
 * thread-safe, so the chunk workers share it.
 */
public final class MarinePlanner {
    private static final Map<TerrainModel, MarinePlanner> PLANNERS = Collections.synchronizedMap(new WeakHashMap<>());

    /** Rock families: one colour each, graded from the top stop to the bottom stop. */
    public static final int GREY = 0;
    public static final int DARK = 1;
    public static final int PALE = 2;
    public static final int SANDY = 3;
    public static final int VOLCANIC = 4;
    public static final String[] FAMILY_NAMES = {"grey", "dark", "pale", "sandy", "volcanic"};

    /** Coral species: one colour each. */
    public static final int TUBE = 0;
    public static final int BRAIN = 1;
    public static final int BUBBLE = 2;
    public static final int FIRE = 3;
    public static final int HORN = 4;
    public static final String[] SPECIES_NAMES = {"tube", "brain", "bubble", "fire", "horn"};

    // noise channels
    private static final int REGION = 0;
    private static final int FRONT = 1;
    private static final int BACK = 2;
    private static final int POOL = 3;
    private static final int CREST = 4;
    private static final int CHANNEL = 5;
    private static final int WARP_X = 6;
    private static final int WARP_Z = 7;
    private static final int ROUGH = 8;
    static final int CLUSTER = 9;
    private static final int SPECIES = 10; // 10..14
    private static final int KELP = 15;
    private static final int KELP_DETAIL = 16;
    static final int CLUMP = 17;
    private static final int MEADOW = 18;
    private static final int MEADOW_DETAIL = 19;
    private static final int BLOWOUT = 20;
    private static final int PROVINCE = 21;
    private static final int STRIKE = 22;
    private static final int STRIKE_MIX = 23;
    private static final int LEDGE = 24;
    static final int STRATA = 25;
    static final int ALGAE = 26;
    static final int FIELD = 27;
    static final int GRAVEL = 28;
    static final int LUMPS = 29;
    static final int CAVE = 30;
    private static final int NOISES = 31;

    /** Patch reef sites lie on a jittered grid of this many blocks. */
    public static final int PATCH_CELL = 72;

    private final TerrainModel model;
    private final SimplexNoise[] noise = new SimplexNoise[NOISES];
    private final long seed;
    private final int seaLevel;
    /** Top water block of the sea. */
    public final int surf;
    /** Top of reef flats and patch reefs: two blocks of water above them. */
    public final int crest;
    /** Highest solid coral block; plants may stand one block higher, just under the surface. */
    public final int coralTop;
    private final double strikeCos1;
    private final double strikeSin1;
    private final double strikeCos2;
    private final double strikeSin2;
    private final ConcurrentHashMap<Long, PatchReef> patchReefs = new ConcurrentHashMap<>();
    private static final PatchReef NO_PATCH_REEF = new PatchReef();

    private MarinePlanner(TerrainModel model) {
        this.model = model;
        this.seed = SeedMixer.derive(model.siteSeed(), 0x5EAF100DL);
        for (int i = 0; i < NOISES; i++) {
            noise[i] = new SimplexNoise(SeedMixer.derive(seed, i + 1));
        }
        this.seaLevel = model.seaLevel();
        this.surf = seaLevel - 1;
        this.crest = seaLevel - 3;
        this.coralTop = seaLevel - 2;
        // two fixed strike directions for bedrock ledges: a varying angle would drift in phase at
        // large coordinates and smear the ledges
        double angle = SeedMixer.cellUnit(seed, 0, 0, 7) * Math.PI * 2.0;
        strikeCos1 = Math.cos(angle);
        strikeSin1 = Math.sin(angle);
        strikeCos2 = Math.cos(angle + 1.22);
        strikeSin2 = Math.sin(angle + 1.22);
    }

    public static MarinePlanner of(TerrainModel model) {
        synchronized (PLANNERS) {
            return PLANNERS.computeIfAbsent(model, MarinePlanner::new);
        }
    }

    public TerrainModel model() {
        return model;
    }

    public int seaLevel() {
        return seaLevel;
    }

    /** Salted seed for the position hashes of one kind of object. */
    public long salt(long kind) {
        return SeedMixer.derive(seed, 0x1000L + kind);
    }

    double noise(int channel, double x, double z) {
        return noise[channel].noise2(x, z);
    }

    double noise3(int channel, double x, double y, double z) {
        return noise[channel].noise3(x, y, z);
    }

    // =========================================================================================
    // Columns
    // =========================================================================================

    /** Fills {@code c} with the plan of the column at {@code (x, z)}. */
    public void column(int x, int z, MarineColumn c) {
        c.x = x;
        c.z = z;
        double h = model.interpolatedHeight(x, z);
        c.height = h;
        c.floor = (int) Math.ceil(h) - 1;
        c.depth = surf - c.floor;
        // smooth fields: bilinear between the four model samples around the column, like the height
        int x0 = x & ~3;
        int z0 = z & ~3;
        double fx = (x - x0) * 0.25;
        double fz = (z - z0) * 0.25;
        double t = 0.0;
        double rv = 0.0;
        double cc = 0.0;
        double fj = 0.0;
        double tr = 0.0;
        double ri = 0.0;
        double be = 0.0;
        double vo = 0.0;
        double sw = 0.0;
        for (int corner = 0; corner < 4; corner++) {
            int dx = (corner & 1) * 4;
            int dz = (corner >> 1) * 4;
            double w = (dx == 0 ? 1.0 - fx : fx) * (dz == 0 ? 1.0 - fz : fz);
            ColumnSample s = model.sample(x0 + dx, z0 + dz);
            t += w * s.temperature;
            rv += w * s.riverValley;
            cc += w * s.coastCliff;
            fj += w * s.fjord;
            tr += w * s.trench;
            ri += w * s.oceanRidge;
            be += w * s.beach;
            vo += w * s.volcano;
            sw += w * s.swamp;
        }
        ColumnSample n = model.sample((x + 2) & ~3, (z + 2) & ~3);
        boolean inland = n.lake > 0.0 || n.river > 0.0 || n.waterLevel != ColumnSample.NO_WATER;
        c.geology = n.geology;
        c.sea = h < seaLevel - 0.5 && !inland && c.depth >= 1;
        c.temperature = t;
        c.riverValley = rv;
        c.coastCliff = cc;
        c.fjord = fj;
        c.trench = tr;
        c.oceanRidge = ri;
        c.beach = be;
        c.volcano = vo;
        double gx = (model.interpolatedHeight(x + 2, z) - model.interpolatedHeight(x - 2, z)) * 0.25;
        double gz = (model.interpolatedHeight(x, z + 2) - model.interpolatedHeight(x, z - 2)) * 0.25;
        c.gradX = gx;
        c.gradZ = gz;
        c.slope = Math.sqrt(gx * gx + gz * gz);
        c.family = family(c.geology, vo, ri, c.depth);

        reef(c, x, z, sw);
        life(c, x, z);
        rock(c, x, z);
        // seagrass roots in sediment: meadows thin out where the floor is scoured to rock
        c.meadow *= 1.0 - smoothstep(0.35, 0.75, c.rocky);
    }

    static int family(int geology, double volcano, double ridge, int depth) {
        if (volcano > 0.3 || ridge > 0.3 || geology == Geology.VOLCANIC) {
            return VOLCANIC;
        }
        if (depth > 90 || geology == Geology.DARK) {
            return DARK;
        }
        if (geology == Geology.LIMESTONE) {
            return PALE;
        }
        return geology == Geology.SEDIMENTARY ? SANDY : GREY;
    }

    /**
     * Coral reef fields of a column. A fringing reef follows the coast by depth: the sandy lagoon
     * next to the shore, then the reef flat (built up to {@link #crest} where the water is between
     * the back depth and the front depth), then the reef front, which falls steeply to the shelf.
     * Both depths wobble with noise, so the front edge winds in plan instead of following one depth
     * contour. Pools and winding sand channels break up the flat; where the reef strength fades
     * (cooler water, river mouths, cliffs, reef-free stretches of coast) the pools take over, so
     * the reef frays out instead of ending in a straight line.
     */
    private void reef(MarineColumn c, int x, int z, double swamp) {
        c.reef = 0.0;
        c.reefObjects = 0.0;
        c.front = 0.0;
        c.back = 0.0;
        c.platform = c.floor;
        double warm = smoothstep(0.38, 0.62, c.temperature);
        if (!c.sea || warm <= 0.0 || c.depth > 40 || c.trench > 0.0) {
            return;
        }
        double region = smoothstep(-0.5, -0.05, 0.75 * noise(REGION, x / 640.0, z / 640.0) + 0.25 * noise(REGION, x / 170.0 + 9.1, z / 170.0 - 4.3));
        double clear = (1.0 - smoothstep(0.08, 0.35, c.riverValley)) * (1.0 - smoothstep(0.1, 0.3, c.fjord)) * (1.0 - smoothstep(0.2, 0.5, swamp));
        double base = warm * region * clear;
        c.reef = base * (1.0 - smoothstep(0.3, 0.6, c.coastCliff));
        c.reefObjects = base * (1.0 - smoothstep(0.55, 0.85, c.coastCliff));
        c.front = clamp(8.5 + 3.0 * noise(FRONT, x / 48.0, z / 48.0) + 1.2 * noise(FRONT, x / 13.0 + 41.0, z / 13.0 - 7.0), 5.0, 13.0);
        c.back = 3.0 + 0.8 * noise(BACK, x / 30.0, z / 30.0);
        if (c.reef <= 0.2) {
            return;
        }
        double d = seaLevel - c.height;
        double top;
        if (d >= c.back && d <= c.front) {
            if (noise(POOL, x / 9.0, z / 9.0) > 0.55 - 1.1 * (1.0 - smoothstep(0.35, 0.75, c.reef)) || channel(x, z)) {
                return;
            }
            // the outer crest is the highest part of the flat; behind it the flat is partly a block lower
            top = d > c.front - 2.5 ? crest : flatLevel(x, z);
            // the flat is a mosaic of colonies: massive ones swell into low domes (microatolls
            // where they reach the low-water level), and the seams between colonies sink a block
            Colony col = colony(x, z);
            if (col.massive() && col.d1 < 0.6 * col.d2) {
                top += 1;
            } else if (col.d2 - col.d1 < 0.8 && SeedMixer.unit(SeedMixer.mix(col.id ^ col.id2)) < 0.5) {
                top -= 1;
            }
        } else if (d > c.front && d < c.front + 10.0) {
            if (channel(x, z)) {
                return;
            }
            // reef front: a steep, rough slope falling from the crest to the shelf
            double beyond = (d - c.front) / Math.max(c.slope, 0.04);
            top = Math.floor(crest - 1.6 * beyond - 0.3 * (noise(ROUGH, x / 5.0, z / 5.0) + 1.0));
        } else {
            return;
        }
        if (top > c.floor) {
            c.platform = (int) Math.min(top, coralTop);
        }
    }

    /** Level of a reef flat behind its crest: the crest or one block lower, in patches. */
    public int flatLevel(int x, int z) {
        return crest - (noise(CREST, x / 17.0, z / 17.0) > 0.2 ? 1 : 0);
    }

    /** Spacing of the colony mosaic on reef tops. */
    private static final double COLONY_CELL = 7.0;
    /** Spacing of the colony mosaic on reef walls (vertical distances count 1.5 times). */
    private static final double WALL_COLONY_CELL = 6.0;

    /**
     * A coral colony of the mosaic covering reef surfaces: one species, one colour. Live colonies
     * cover about two thirds of a reef; the rest is grey dead-coral pavement and, rarely, a
     * bleached colony.
     */
    public static final class Colony {
        /** Hash of the nearest colony seed, and of the second nearest. */
        public long id;
        public long id2;
        /** Distances to the nearest and second-nearest seed. */
        public double d1;
        public double d2;
        /** Position of the nearest seed. */
        public int seedX;
        public int seedZ;

        public boolean live() {
            return SeedMixer.cellUnit(id, 0, 0, 11) < 0.64;
        }

        public boolean bleached() {
            double k = SeedMixer.cellUnit(id, 0, 0, 11);
            return k >= 0.64 && k < 0.69;
        }

        /** Massive (brain-like) colonies grow into low domes. */
        public boolean massive() {
            return live() && SeedMixer.cellUnit(id, 0, 0, 12) < 0.4;
        }

        public double roll() {
            return SeedMixer.cellUnit(id, 0, 0, 13);
        }
    }

    /** The colony of the 2D mosaic covering reef tops at a column. */
    public Colony colony(int x, int z) {
        long salt = salt(2);
        int gx = Math.floorDiv(x, (int) COLONY_CELL);
        int gz = Math.floorDiv(z, (int) COLONY_CELL);
        Colony c = new Colony();
        c.d1 = Double.MAX_VALUE;
        c.d2 = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = SeedMixer.hash2(salt, gx + dx, gz + dz);
                double sx = (gx + dx + 0.1 + 0.8 * SeedMixer.cellUnit(h, 0, 0, 1)) * COLONY_CELL;
                double sz = (gz + dz + 0.1 + 0.8 * SeedMixer.cellUnit(h, 0, 0, 2)) * COLONY_CELL;
                double d = Math.sqrt((sx - x - 0.5) * (sx - x - 0.5) + (sz - z - 0.5) * (sz - z - 0.5));
                if (d < c.d1) {
                    c.d2 = c.d1;
                    c.id2 = c.id;
                    c.d1 = d;
                    c.id = h;
                    c.seedX = (int) Math.floor(sx);
                    c.seedZ = (int) Math.floor(sz);
                } else if (d < c.d2) {
                    c.d2 = d;
                    c.id2 = h;
                }
            }
        }
        return c;
    }

    /** The colony of the 3D mosaic covering reef walls and the sides of coral heads. */
    public Colony wallColony(int x, int y, int z) {
        long salt = salt(3);
        int gx = Math.floorDiv(x, (int) WALL_COLONY_CELL);
        int gy = Math.floorDiv(y, 4);
        int gz = Math.floorDiv(z, (int) WALL_COLONY_CELL);
        Colony c = new Colony();
        c.d1 = Double.MAX_VALUE;
        c.d2 = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long h = SeedMixer.hash3(salt, gx + dx, gy + dy, gz + dz);
                    double sx = (gx + dx + SeedMixer.cellUnit(h, 0, 0, 1)) * WALL_COLONY_CELL;
                    double sy = (gy + dy + SeedMixer.cellUnit(h, 0, 0, 2)) * 4.0;
                    double sz = (gz + dz + SeedMixer.cellUnit(h, 0, 0, 3)) * WALL_COLONY_CELL;
                    double ex = sx - x - 0.5;
                    double ey = (sy - y - 0.5) * 1.5;
                    double ez = sz - z - 0.5;
                    double d = Math.sqrt(ex * ex + ey * ey + ez * ez);
                    if (d < c.d1) {
                        c.d2 = c.d1;
                        c.id2 = c.id;
                        c.d1 = d;
                        c.id = h;
                        c.seedX = (int) Math.floor(sx);
                        c.seedZ = (int) Math.floor(sz);
                    } else if (d < c.d2) {
                        c.d2 = d;
                        c.id2 = h;
                    }
                }
            }
        }
        return c;
    }

    /** Winding sand channels (surge channels) 2-4 blocks wide crossing the reef flat and front. */
    private boolean channel(int x, int z) {
        double wx = x + 10.0 * noise(WARP_Z, x / 30.0 + 7.7, z / 30.0);
        double wz = z + 10.0 * noise(WARP_X, x / 30.0, z / 30.0 - 3.3);
        return Math.abs(noise(CHANNEL, wx / 34.0, wz / 34.0)) < 0.055;
    }

    /**
     * Kelp forests in cool water on 3-30-block-deep floors, in patches 50-200 blocks across with
     * bare gaps (urchin barrens); seagrass meadows on sandy floors 1-20 blocks deep with ragged
     * edges and bare-sand blowouts. Neither grows under sea ice or on a coral reef.
     */
    private void life(MarineColumn c, int x, int z) {
        c.kelp = 0.0;
        c.meadow = 0.0;
        if (!c.sea || c.trench > 0.0) {
            return;
        }
        double t = c.temperature;
        double d = c.depth;
        double noReef = 1.0 - smoothstep(0.05, 0.3, c.reefObjects);
        double cool = smoothstep(-0.62, -0.42, t) * (1.0 - smoothstep(0.12, 0.34, t));
        if (cool > 0.0 && d >= 3) {
            double depthOk = smoothstep(3.0, 6.0, d) * (1.0 - smoothstep(26.0, 36.0, d));
            double patch = smoothstep(-0.05, 0.35, noise(KELP, x / 100.0, z / 100.0) + 0.35 * noise(KELP_DETAIL, x / 33.0, z / 33.0));
            c.kelp = cool * depthOk * patch * noReef * (1.0 - smoothstep(0.1, 0.4, c.riverValley));
        }
        double tempOk = smoothstep(-0.52, -0.36, t);
        if (tempOk > 0.0 && d >= 1 && d < 26) {
            double depthOk = smoothstep(0.5, 2.0, d) * (1.0 - smoothstep(13.0, 20.0 + 4.0 * smoothstep(0.4, 0.7, t), d));
            double patch = smoothstep(-0.1, 0.25, noise(MEADOW, x / 65.0, z / 65.0) + 0.3 * noise(MEADOW_DETAIL, x / 14.0, z / 14.0));
            double flat = 1.0 - smoothstep(0.5, 1.0, c.slope);
            double blowout = noise(BLOWOUT, x / 11.0, z / 11.0) > 0.55 ? 0.0 : 1.0;
            c.meadow = tempOk * depthOk * patch * flat * blowout * (1.0 - 0.8 * smoothstep(0.4, 0.7, c.kelp));
        }
    }

    /**
     * Rockiness of the floor and the bedrock ledges on it: slabs of the rock beds that currents
     * have scoured free of sand, elongated along one of two fixed strike directions (which one
     * changes over hundreds of blocks), flat-topped and stepped at their edges, 1-6 blocks high.
     */
    private void rock(MarineColumn c, int x, int z) {
        c.rocky = 0.0;
        c.outcrop = 0;
        if (!c.sea) {
            return;
        }
        double cold = smoothstep(-0.15, -0.45, c.temperature);
        double r = Math.max(Math.max(0.9 * smoothstep(0.08, 0.3, c.coastCliff), 0.8 * smoothstep(0.1, 0.5, c.fjord)),
            Math.max(0.6 * smoothstep(0.12, 0.35, c.slope), 0.5 * cold * (1.0 - smoothstep(40.0, 60.0, c.depth))));
        r = Math.max(r, Math.max(0.7 * smoothstep(0.2, 0.6, c.oceanRidge), 0.35 * smoothstep(0.2, 0.6, noise(PROVINCE, x / 900.0, z / 900.0))));
        // kelp holdfasts need rock, so kelp forests stand on rocky floors
        r = Math.max(r, 0.5 * c.kelp);
        r *= (1.0 - c.beach) * (1.0 - smoothstep(0.05, 0.3, c.riverValley)) * (1.0 - smoothstep(0.1, 0.4, c.reefObjects));
        c.rocky = r;
        if (r < 0.05 || c.depth < 3 || c.trench > 0.2) {
            return;
        }
        double u1 = x * strikeCos1 + z * strikeSin1;
        double w1 = -x * strikeSin1 + z * strikeCos1;
        double u2 = x * strikeCos2 + z * strikeSin2;
        double w2 = -x * strikeSin2 + z * strikeCos2;
        double b1 = noise(STRIKE, u1 / 46.0, w1 / 14.0);
        double b2 = noise(STRIKE, u2 / 46.0 + 100.0, w2 / 14.0);
        double beds = lerp(smoothstep(-0.3, 0.3, noise(STRIKE_MIX, x / 600.0, z / 600.0)), b1, b2) + 0.3 * noise(LEDGE, x / 11.0, z / 11.0);
        double threshold = 0.66 - 0.42 * r;
        double o = smoothstep(threshold, threshold + 0.28, beds);
        if (o > 0.0) {
            c.outcrop = Math.max(0, Math.min(c.depth - 2, (int) Math.round(TMath.terrace(o * (1.5 + 5.0 * r), 2.0, 0.6))));
        }
    }

    // =========================================================================================
    // Coral colours
    // =========================================================================================

    /**
     * Picks a coral species for a colony. Every stretch of reef is dominated by one species and
     * shares most of the rest with a second and third one, which vary smoothly over 50-150 blocks,
     * so a reef shows two or three colours in coherent patches instead of a rainbow. Fire coral
     * (red) only grows in the shallow surge zone; tube (blue) and bubble (purple) prefer deeper
     * water.
     *
     * @param water free water blocks above the colony
     * @param roll uniform random value in [0, 1) of the colony
     * @param bias per-species weight multipliers of the colony form, or null
     */
    public int species(int x, int z, int water, double roll, double[] bias) {
        double[] v = new double[5];
        for (int i = 0; i < 5; i++) {
            v[i] = noise(SPECIES + i, x / 130.0, z / 130.0) + 0.25 * noise(SPECIES + i, x / 37.0 + 17.0, z / 37.0);
        }
        v[FIRE] += water <= 5 ? 0.15 : -0.7;
        v[TUBE] += water > 10 ? 0.2 : 0.0;
        v[BUBBLE] += water > 14 ? 0.2 : 0.0;
        v[HORN] += water < 8 ? 0.1 : 0.0;
        int[] order = {0, 1, 2, 3, 4};
        for (int i = 1; i < 5; i++) {
            for (int j = i; j > 0 && v[order[j]] > v[order[j - 1]]; j--) {
                int tmp = order[j];
                order[j] = order[j - 1];
                order[j - 1] = tmp;
            }
        }
        double[] w = new double[5];
        double[] rank = {6.0, 2.6, 1.0, 0.15, 0.15};
        double sum = 0.0;
        for (int i = 0; i < 5; i++) {
            int sp = order[i];
            w[sp] = rank[i] * (bias == null ? 1.0 : bias[sp]) * (sp == FIRE && water > 7 ? 0.1 : 1.0);
            sum += w[sp];
        }
        double r = roll * sum;
        for (int sp = 0; sp < 5; sp++) {
            r -= w[sp];
            if (r < 0.0) {
                return sp;
            }
        }
        return order[0];
    }

    /** The dominant species at a position; dead coral rubble of a reef takes its colour. */
    public int dominant(int x, int z) {
        return species(x, z, 4, 0.0, null);
    }

    // =========================================================================================
    // Patch reefs
    // =========================================================================================

    /**
     * A patch reef: an oval coral island 16-40 blocks across rising from a wide, flat sandy shelf
     * to the reef crest, with sloping sides, sometimes a sandy pool in its middle, and a ring of
     * coral heads around it.
     */
    public static final class PatchReef {
        public boolean active;
        public int x;
        public int z;
        /** Planned floor at the centre. */
        public int floor;
        public double radius;
        public double phaseA;
        public double phaseB;
        public boolean pool;
        public long seed;

        /** Outline radius in the direction {@code angle}. */
        public double edge(double angle) {
            return radius * (1.0 + 0.22 * (0.6 * Math.sin(3.0 * angle + phaseA) + 0.4 * Math.sin(7.0 * angle + phaseB)));
        }
    }

    /** The patch reef of grid cell {@code (cx, cz)}, or null. Cached. */
    public PatchReef patchReef(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        PatchReef cached = patchReefs.get(key);
        if (cached == null) {
            cached = computePatchReef(cx, cz);
            if (patchReefs.size() > 16384) {
                patchReefs.clear();
            }
            patchReefs.put(key, cached);
        }
        return cached.active ? cached : null;
    }

    private PatchReef computePatchReef(int cx, int cz) {
        long h = SeedMixer.hash2(salt(1), cx, cz);
        if (SeedMixer.unit(h) > 0.55) {
            return NO_PATCH_REEF;
        }
        int x = (int) Math.floor((cx + 0.15 + 0.7 * SeedMixer.cellUnit(h, 0, 0, 1)) * PATCH_CELL);
        int z = (int) Math.floor((cz + 0.15 + 0.7 * SeedMixer.cellUnit(h, 0, 0, 2)) * PATCH_CELL);
        MarineColumn c = new MarineColumn();
        column(x, z, c);
        double d = seaLevel - c.height;
        if (c.reef < 0.5 || d < 5.0 || d > 16.0 || c.slope > 0.07) {
            return NO_PATCH_REEF;
        }
        double radius = 8.0 + 12.0 * SeedMixer.cellUnit(h, 0, 0, 3);
        // the reef must stand in open water: a ring around it is sea of moderate depth
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int px = (int) Math.round(x + Math.cos(a) * (radius * 1.22 + 6.0));
            int pz = (int) Math.round(z + Math.sin(a) * (radius * 1.22 + 6.0));
            double ph = model.interpolatedHeight(px, pz);
            double pd = seaLevel - ph;
            ColumnSample s = model.sample((px + 2) & ~3, (pz + 2) & ~3);
            if (pd < 2.5 || pd > 24.0 || s.lake > 0.0 || s.river > 0.0) {
                return NO_PATCH_REEF;
            }
        }
        PatchReef p = new PatchReef();
        p.active = true;
        p.x = x;
        p.z = z;
        p.floor = c.floor;
        p.radius = radius;
        p.phaseA = SeedMixer.cellUnit(h, 0, 0, 4) * Math.PI * 2.0;
        p.phaseB = SeedMixer.cellUnit(h, 0, 0, 5) * Math.PI * 2.0;
        p.pool = SeedMixer.cellUnit(h, 0, 0, 6) < 0.3;
        p.seed = SeedMixer.mix(h);
        return p;
    }

    // =========================================================================================
    // Cheap chunk tests
    // =========================================================================================

    /**
     * True if any part of the square {@code [minX - margin, minX + 15 + margin]} (same in z) may
     * lie under the sea. Uses the model's quart cache only.
     */
    public boolean mayHaveSea(int minX, int minZ, int margin) {
        for (int x = (minX - margin) & ~3; x <= minX + 15 + margin + 3; x += 8) {
            for (int z = (minZ - margin) & ~3; z <= minZ + 15 + margin + 3; z += 8) {
                if (model.sample(x, z).height < seaLevel - 0.5) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if part of a chunk may be sea floor shallow enough for kelp and seagrass (up to 44 deep). */
    public boolean mayHaveShelf(int minX, int minZ) {
        for (int x = minX & ~3; x <= minX + 16; x += 4) {
            for (int z = minZ & ~3; z <= minZ + 16; z += 4) {
                double h = model.sample(x, z).height;
                if (h < seaLevel - 0.5 && h > seaLevel - 46.0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if a coral reef may grow in the square around a chunk (warm water up to 40 blocks deep). */
    public boolean mayHaveReef(int minX, int minZ, int margin) {
        for (int x = (minX - margin) & ~3; x <= minX + 15 + margin + 3; x += 8) {
            for (int z = (minZ - margin) & ~3; z <= minZ + 15 + margin + 3; z += 8) {
                ColumnSample s = model.sample(x, z);
                if (s.temperature > 0.36 && s.height < seaLevel - 0.5 && s.height > seaLevel - 44.0) {
                    return true;
                }
            }
        }
        return false;
    }
}
