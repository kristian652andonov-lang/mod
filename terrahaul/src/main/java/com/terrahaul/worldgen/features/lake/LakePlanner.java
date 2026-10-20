package com.terrahaul.worldgen.features.lake;

import static com.terrahaul.worldgen.noise.TMath.clamp01;
import static com.terrahaul.worldgen.noise.TMath.lerp;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.features.marine.MarinePlanner;
import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;
import com.terrahaul.worldgen.noise.TMath;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.LakeType;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.WaterBody;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plans the floors of Terrahaul's lakes from the terrain model alone: where freshwater meadows,
 * weed beds, lily pads, reeds and lush shallows grow, which stretches of the bed are sand, gravel,
 * clay, mud or moss, where littoral shelves end in drop-offs, how rocky the floor is, and the
 * deterministic sites of what lies on it (see {@link LakeRaster}). Pure Java (no Minecraft
 * classes), so the features and the devtools see exactly the same plan.
 * <p>
 * The habitats follow real lakes, adapted to each lake's type and climate:
 * <ul>
 *   <li>the littoral zone, down to six to ten blocks (deeper in clear cold water), carries
 *       meadows of short and tall seagrass on soft beds; lily pads float in colonies over calm
 *       water a few blocks deep, reeds line the shore, and in warm humid country dripleaf and
 *       moss grow in the shallows;</li>
 *   <li>weed beds (kelp standing in for pondweed, sparser than kelp forests in the sea) grow in
 *       temperate and cold lakes four to twenty blocks deep; in the clear water of a great lake,
 *       fewer and larger beds of tall weed also stand on the deep plain, reaching up towards the
 *       light, so the open lake shows dark stands under its surface;</li>
 *   <li>great lakes hold shoals far from their shores: moraine mounds rising from the deep plain
 *       to a few blocks under the surface; the rest of their deep floor is gravel and clay;
 *       mountain and glacial lakes are rocky, with ledges, talus under steep shores, erratics and
 *       rock flour (clay);</li>
 *   <li>marsh lakes are shallow wetland pools with reeds, lily pads and a little weed only.</li>
 * </ul>
 * One planner exists per terrain model; its noises are immutable and its caches thread-safe, so
 * the chunk workers share it.
 */
public final class LakePlanner {
    private static final Map<TerrainModel, LakePlanner> PLANNERS = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TerrainModel, LakePlanner> eldest) {
            return size() > 8;
        }
    };
    private static volatile LakePlanner lastPlanner;

    // noise channels
    static final int MEADOW = 0;
    static final int MEADOW_DETAIL = 1;
    static final int BLOWOUT = 2;
    static final int WEED = 3;
    static final int WEED_DETAIL = 4;
    static final int CLUMP = 5;
    static final int LILY = 6;
    static final int REED = 7;
    static final int LUSH = 8;
    static final int MOSS = 9;
    static final int SED_SELECT = 10;
    static final int SED_PATCH = 11;
    static final int WARP_X = 12;
    static final int WARP_Z = 13;
    static final int BAR = 14;
    static final int SHELF = 15;
    static final int SHELF_DEPTH = 16;
    static final int DROP = 17;
    static final int FIELD = 18;
    static final int STRIKE = 19;
    static final int STRIKE_MIX = 20;
    static final int LEDGE = 21;
    static final int STRATA = 22;
    static final int ALGAE = 23;
    static final int LUMPS = 24;
    static final int PROVINCE = 25;
    static final int WEATHER = 26;
    static final int DEEP_BEDS = 27;
    private static final int NOISES = 28;

    /** Wood species, in {@code TerrahaulBlocks.WOODS} order. */
    public static final int OAK = 0;
    public static final int BIRCH = 1;
    public static final int SPRUCE = 2;
    public static final int JUNGLE = 3;
    public static final int ACACIA = 4;
    public static final int DARK_OAK = 5;
    public static final int MANGROVE = 6;
    public static final int CHERRY = 7;

    // per lake type (NONE, FOREST, MOUNTAIN, GREAT, MARSH, CRATER, PLATEAU, GLACIAL)
    private static final double[] MEADOW_T = {0.0, 0.95, 0.6, 1.0, 0.15, 0.35, 0.85, 0.3};
    private static final double[] WEED_T = {0.0, 0.5, 0.55, 0.8, 0.0, 0.15, 0.45, 0.3};
    private static final double[] LILY_T = {0.0, 0.9, 0.3, 0.45, 1.0, 0.2, 0.65, 0.0};
    private static final double[] REED_T = {0.0, 0.8, 0.35, 0.7, 1.0, 0.25, 0.6, 0.0};
    private static final double[] LUSH_T = {0.0, 1.0, 0.25, 0.5, 0.0, 0.2, 0.7, 0.0};
    private static final double[] ROCKY_T = {0.0, 0.12, 0.55, 0.2, 0.0, 0.5, 0.25, 0.7};
    /** Boulders and sunken logs per lake type (relative). */
    static final double[] BOULDER_T = {0.0, 0.2, 0.6, 0.35, 0.0, 0.45, 0.3, 0.75};
    static final double[] LOG_T = {0.0, 0.55, 0.2, 0.16, 0.0, 0.04, 0.32, 0.05};
    /** Chance that a discovery cell overlapping a lake of the type holds something. */
    private static final double[] DISCOVERY_T = {0.0, 0.32, 0.16, 0.12, 0.18, 0.05, 0.26, 0.06};

    /** Discoveries (ruins, wrecks, piers) lie on a jittered grid of this many blocks. */
    public static final int DISCOVERY_CELL = 96;

    private final TerrainModel model;
    private final SimplexNoise[] noise = new SimplexNoise[NOISES];
    private final long seed;
    private final int seaLevel;
    private final double strikeCos1;
    private final double strikeSin1;
    private final double strikeCos2;
    private final double strikeSin2;
    private final ConcurrentHashMap<Long, Double> radii = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Discovery> discoveries = new ConcurrentHashMap<>();
    private static final Discovery NO_DISCOVERY = new Discovery(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L);

    private LakePlanner(TerrainModel model) {
        this.model = model;
        this.seed = SeedMixer.derive(model.siteSeed(), 0x1A4EF100L);
        for (int i = 0; i < NOISES; i++) {
            noise[i] = new SimplexNoise(SeedMixer.derive(seed, i + 1));
        }
        this.seaLevel = model.seaLevel();
        double angle = SeedMixer.cellUnit(seed, 0, 0, 7) * Math.PI * 2.0;
        strikeCos1 = Math.cos(angle);
        strikeSin1 = Math.sin(angle);
        strikeCos2 = Math.cos(angle + 1.22);
        strikeSin2 = Math.sin(angle + 1.22);
    }

    public static LakePlanner of(TerrainModel model) {
        LakePlanner last = lastPlanner;
        if (last != null && last.model == model) {
            return last;
        }
        synchronized (PLANNERS) {
            last = PLANNERS.computeIfAbsent(model, LakePlanner::new);
        }
        lastPlanner = last;
        return last;
    }

    public TerrainModel model() {
        return model;
    }

    public int seaLevel() {
        return seaLevel;
    }

    /** Salted seed for the position hashes of one kind of object. */
    public long salt(long kind) {
        return SeedMixer.derive(seed, 0x2000L + kind);
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
    public void column(int x, int z, LakeColumn c) {
        // neighbouring chunks plan the same margin columns, usually on the same worker thread
        ColumnCache cache = columnCache.get();
        int i = (x & CACHE_MASK) | (z & CACHE_MASK) << CACHE_BITS;
        long key = (long) x << 32 | z & 0xFFFFFFFFL;
        LakeColumn hit = cache.columns[i];
        if (hit != null && cache.keys[i] == key) {
            c.copyFrom(hit);
            return;
        }
        compute(x, z, c);
        if (hit == null) {
            hit = new LakeColumn();
            cache.columns[i] = hit;
        }
        hit.copyFrom(c);
        cache.keys[i] = key;
    }

    private static final int CACHE_BITS = 6;
    private static final int CACHE_MASK = (1 << CACHE_BITS) - 1;
    private final ThreadLocal<ColumnCache> columnCache = ThreadLocal.withInitial(ColumnCache::new);

    private static final class ColumnCache {
        final long[] keys = new long[1 << 2 * CACHE_BITS];
        final LakeColumn[] columns = new LakeColumn[1 << 2 * CACHE_BITS];
    }

    private void compute(int x, int z, LakeColumn c) {
        c.x = x;
        c.z = z;
        double h = model.interpolatedHeight(x, z);
        c.height = h;
        c.floor = (int) Math.ceil(h) - 1;
        int x0 = x & ~3;
        int z0 = z & ~3;
        double fx = (x - x0) * 0.25;
        double fz = (z - z0) * 0.25;
        double t = 0.0;
        double hu = 0.0;
        double in = 0.0;
        double vo = 0.0;
        int level = Integer.MIN_VALUE;
        int type = LakeType.NONE;
        boolean other = false;
        // the water solver gives a column the water of a body any of its four corners is inside
        for (int corner = 0; corner < 4; corner++) {
            int dx = (corner & 1) * 4;
            int dz = (corner >> 1) * 4;
            double w = (dx == 0 ? 1.0 - fx : fx) * (dz == 0 ? 1.0 - fz : fz);
            ColumnSample s = model.sample(x0 + dx, z0 + dz);
            t += w * s.temperature;
            hu += w * s.humidity;
            in += w * Math.max(0.0, s.lake);
            vo += w * s.volcano;
            if (s.bodyInside) {
                if (s.bodyKind == WaterBody.LAKE || s.bodyKind == WaterBody.CRATER) {
                    int lv = (int) s.bodyLevel;
                    if (level != Integer.MIN_VALUE && lv != level) {
                        other = true;
                    }
                    level = Math.max(level, lv);
                    type = s.bodyKind == WaterBody.CRATER ? LakeType.CRATER : s.lakeType;
                } else {
                    // a river's mouth or a lava pool: the solver may build steps there
                    other = true;
                }
            }
        }
        ColumnSample n = model.sample((x + 2) & ~3, (z + 2) & ~3);
        // the sea floor features own the columns they take for open sea
        boolean inland = n.lake > 0.0 || n.river > 0.0 || n.waterLevel != ColumnSample.NO_WATER;
        boolean sea = h < seaLevel - 0.5 && !inland && seaLevel - 1 - c.floor >= 1;
        c.geology = n.geology;
        c.temperature = t;
        c.humidity = hu;
        c.inner = clamp01(in);
        c.lake = level != Integer.MIN_VALUE && !other && !sea && type != LakeType.NONE;
        if (c.lake) {
            c.surf = level - 1;
            c.depth = c.surf - c.floor;
            c.wet = c.depth >= 1;
            c.type = type;
            c.radius = lakeRadius(x, z, type);
            c.size = smoothstep(12.0, 60.0, c.radius);
        } else {
            c.surf = LakeColumn.NO_SURF;
            c.depth = 0;
            c.wet = false;
            c.type = LakeType.NONE;
            c.radius = 0.0;
            c.size = 0.0;
        }
        double gx = (model.interpolatedHeight(x + 2, z) - model.interpolatedHeight(x - 2, z)) * 0.25;
        double gz = (model.interpolatedHeight(x, z + 2) - model.interpolatedHeight(x, z - 2)) * 0.25;
        c.gradX = gx;
        c.gradZ = gz;
        c.slope = Math.sqrt(gx * gx + gz * gz);
        c.family = family(c.geology, vo, type);
        c.bed = c.floor;
        c.fill = 0;
        c.patch = 0;
        c.outcrop = 0;
        c.rocky = 0.0;
        c.meadow = 0.0;
        c.weed = 0.0;
        c.lilies = 0.0;
        c.lush = 0.0;
        if (c.wet) {
            rocky(c, x, z);
            double shoal = shoals(c, x, z);
            bed(c, x, z);
            // shoals are moraine: gravel and rock, with boulders and ledges on their flanks
            c.rocky = Math.max(c.rocky, 0.6 * shoal);
            life(c, x, z);
            outcrop(c, x, z);
        }
    }

    /** Rock family of a lake's boulders and ledges: the province's colour, basalt in craters. */
    static int family(int geology, double volcano, int type) {
        if (type == LakeType.CRATER || volcano > 0.3 || geology == Geology.VOLCANIC) {
            return MarinePlanner.VOLCANIC;
        }
        if (geology == Geology.DARK) {
            return MarinePlanner.DARK;
        }
        if (geology == Geology.LIMESTONE) {
            return MarinePlanner.PALE;
        }
        return geology == Geology.SEDIMENTARY ? MarinePlanner.SANDY : MarinePlanner.GREY;
    }

    /**
     * Radius of the lake around a column. Looked up once per 64-block tile from the tile's centre,
     * so every chunk asking about the tile gets the same answer.
     */
    public double lakeRadius(int x, int z, int type) {
        int tx = x >> 6;
        int tz = z >> 6;
        int kind = type == LakeType.CRATER ? 2 : type == LakeType.GREAT ? 1 : 0;
        long key = ((long) tx << 34) ^ ((tz & 0xFFFFFFFFL) << 2) ^ kind;
        Double cached = radii.get(key);
        if (cached != null) {
            return cached;
        }
        int cx = (tx << 6) + 32;
        int cz = (tz << 6) + 32;
        double r;
        if (kind == 2) {
            TerrainModel.VolcanoSite v = model.nearestVolcano(cx, cz, 1500.0);
            r = v == null ? 30.0 : v.craterRadius;
        } else {
            TerrainModel.LakeSite s = model.nearestLake(cx, cz, kind == 1 ? 7000 : 900, kind == 1);
            r = s == null ? 40.0 : s.radius;
        }
        if (radii.size() > 8192) {
            radii.clear();
        }
        radii.put(key, r);
        return r;
    }

    // =========================================================================================
    // Shoals
    // =========================================================================================

    /** Shoals of great lakes lie on a jittered grid of this many blocks. */
    public static final int SHOAL_CELL = 120;
    /** Farthest a shoal's flank reaches from its centre (its largest lobed and roughened radius). */
    private static final int SHOAL_REACH = 70;
    private static final Shoal NO_SHOAL = new Shoal(0.0, 0.0, 0.0, 0, 0.0);
    private final ConcurrentHashMap<Long, Shoal> shoals = new ConcurrentHashMap<>();

    /**
     * A shoal of a great lake: a broad moraine mound of gravel and rock rising from the deep floor
     * to two to five blocks under the surface, its outline lobed; weed and seagrass grow on its
     * crest, so it shows from a boat as a patch of shallow water far from the shore.
     */
    public record Shoal(double x, double z, double radius, int top, double phase) {
    }

    /** The shoal of a grid cell, or null. Planned from the terrain model's lakes alone. Cached. */
    public Shoal shoal(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        Shoal cached = shoals.get(key);
        if (cached == null) {
            cached = computeShoal(cx, cz);
            if (shoals.size() > 8192) {
                shoals.clear();
            }
            shoals.put(key, cached);
        }
        return cached == NO_SHOAL ? null : cached;
    }

    private Shoal computeShoal(int cx, int cz) {
        long h = SeedMixer.hash2(salt(9), cx, cz);
        if (SeedMixer.unit(h) > 0.6) {
            return NO_SHOAL;
        }
        int x = (int) Math.floor((cx + 0.2 + 0.6 * SeedMixer.cellUnit(h, 0, 0, 1)) * SHOAL_CELL);
        int z = (int) Math.floor((cz + 0.2 + 0.6 * SeedMixer.cellUnit(h, 0, 0, 2)) * SHOAL_CELL);
        ColumnSample s = model.sample((x + 2) & ~3, (z + 2) & ~3);
        if (!s.bodyInside || s.bodyKind != WaterBody.LAKE || s.lakeType != LakeType.GREAT) {
            return NO_SHOAL;
        }
        int surf = (int) s.bodyLevel - 1;
        int floor = (int) Math.ceil(model.interpolatedHeight(x, z)) - 1;
        if (surf - floor < 12) {
            return NO_SHOAL;
        }
        // broad mounds: the flanks of the tallest fall about one block per block
        double radius = 22.0 + 26.0 * SeedMixer.cellUnit(h, 0, 0, 3);
        // the whole mound lies in deep water of the lake, away from its shores
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int px = (int) Math.round(x + Math.cos(a) * radius * 1.4);
            int pz = (int) Math.round(z + Math.sin(a) * radius * 1.4);
            ColumnSample r = model.sample((px + 2) & ~3, (pz + 2) & ~3);
            if (!r.bodyInside || r.bodyKind != WaterBody.LAKE || (int) r.bodyLevel - 1 != surf || surf - (Math.ceil(r.height) - 1) < 8) {
                return NO_SHOAL;
            }
        }
        int top = surf - 2 - (int) (SeedMixer.cellUnit(h, 0, 0, 4) * 4.0);
        return new Shoal(x, z, radius, top, SeedMixer.cellUnit(h, 0, 0, 5) * Math.PI * 2.0);
    }

    /**
     * Raises the bed of a great lake's column over a shoal: a flat crest over the inner quarter,
     * then flanks falling to the floor. Returns how much of the shoal the column is (1 on the
     * crest, 0 off it).
     */
    private double shoals(LakeColumn c, int x, int z) {
        if (c.type != LakeType.GREAT || c.depth < 6) {
            return 0.0;
        }
        double best = 0.0;
        for (int cx = Math.floorDiv(x - SHOAL_REACH, SHOAL_CELL); cx <= Math.floorDiv(x + SHOAL_REACH, SHOAL_CELL); cx++) {
            for (int cz = Math.floorDiv(z - SHOAL_REACH, SHOAL_CELL); cz <= Math.floorDiv(z + SHOAL_REACH, SHOAL_CELL); cz++) {
                Shoal sh = shoal(cx, cz);
                if (sh == null || sh.top() >= c.surf) {
                    continue;
                }
                double dx = x + 0.5 - sh.x();
                double dz = z + 0.5 - sh.z();
                double a = Math.atan2(dz, dx);
                double rr = sh.radius() * (1.0 + 0.25 * Math.sin(3.0 * a + sh.phase()) + 0.12 * Math.sin(5.0 * a + 2.0 * sh.phase()))
                    + 4.0 * noise(SED_SELECT, x / 9.0 + 31.0, z / 9.0);
                double d = Math.hypot(dx, dz) / Math.max(4.0, rr);
                if (d >= 1.0) {
                    continue;
                }
                double f = 1.0 - smoothstep(0.25, 1.0, d);
                int h = (int) Math.floor(lerp(f, c.floor - 1, sh.top()) + 0.8 * noise(LEDGE, x / 6.0, z / 6.0));
                h = Math.min(h, c.surf - 1);
                if (h > c.bed) {
                    c.bed = h;
                    c.fill = LakeVolume.GRAVEL;
                }
                best = Math.max(best, f);
            }
        }
        return best;
    }

    /** How rocky a lake floor is: talus under steep shores, glacial floors, rock provinces. */
    private void rocky(LakeColumn c, int x, int z) {
        if (c.type == LakeType.MARSH) {
            return;
        }
        double steep = smoothstep(0.35, 1.0, c.slope);
        double province = smoothstep(0.15, 0.6, noise(PROVINCE, x / 260.0, z / 260.0));
        double cold = smoothstep(-0.2, -0.5, c.temperature);
        c.rocky = clamp01(ROCKY_T[c.type] + 0.45 * steep + 0.25 * province + 0.15 * cold);
    }

    /**
     * The bed: littoral shelves, gravel bars and patches of other sediment. Shelves build the
     * floor up, in stretches of shore, to a flat terrace one to three blocks under the surface
     * that ends in a drop-off of three to six blocks; where a stretch fades out the terrace sinks
     * towards the natural floor, so it never ends in a wall along the shore. Patches are irregular
     * blobs (warped noise) of the materials that set the bed apart from the lake's own sediment.
     */
    private void bed(LakeColumn c, int x, int z) {
        int type = c.type;
        boolean sediment = type == LakeType.GREAT || type == LakeType.FOREST || type == LakeType.PLATEAU;
        if (sediment && c.size > 0.2 && c.depth >= 2 && c.bed == c.floor) {
            double s = noise(SHELF, x / 55.0, z / 55.0) + 0.3 * noise(SHELF, x / 16.0 + 40.0, z / 16.0 - 7.0);
            double strength = smoothstep(0.55, 0.75, s);
            if (strength > 0.0) {
                int sd = 2 + (noise(SHELF_DEPTH, x / 23.0, z / 23.0) > 0.25 ? 1 : 0) - (noise(SHELF_DEPTH, x / 23.0 + 50.0, z / 23.0) > 0.5 ? 1 : 0);
                int drop = 3 + (int) Math.floor(3.0 * (0.5 + 0.5 * noise(DROP, x / 7.0, z / 7.0)) * 0.999);
                int top = c.surf - sd - (int) Math.round((1.0 - strength) * (drop + 1));
                if (c.floor < top && c.floor >= top - drop) {
                    c.bed = top;
                    c.fill = LakeVolume.FILL;
                }
            }
        }
        double wx = x + 7.0 * noise(WARP_X, x / 22.0, z / 22.0);
        double wz = z + 7.0 * noise(WARP_Z, x / 22.0, z / 22.0);
        int water = c.surf - c.bed;
        if (c.bed == c.floor && type != LakeType.MARSH && type != LakeType.CRATER && water >= 3 && water <= 9) {
            // gravel bars: low winding ridges in the shallows
            double ridge = 1.0 - Math.abs(noise(BAR, wx / 36.0, wz / 36.0));
            double region = smoothstep(0.0, 0.3, noise(BAR, x / 150.0 + 70.0, z / 150.0));
            if (ridge > 0.955 - 0.02 * region && region > 0.0) {
                c.bed = c.floor + (ridge > 0.985 && water >= 5 ? 2 : 1);
                c.fill = LakeVolume.GRAVEL;
                water = c.surf - c.bed;
            }
        }
        double blob = noise(SED_PATCH, wx / 12.0, wz / 12.0) + 0.35 * noise(SED_PATCH, wx / 4.5 + 30.0, wz / 4.5);
        double sel = noise(SED_SELECT, x / 80.0, z / 80.0);
        double t = c.temperature;
        boolean warmMud = t > 0.3 && c.humidity > 0.2 && water <= 3;
        boolean mossy = t > -0.2 && c.humidity > 0.05 && water <= 4;
        byte patch = 0;
        switch (type) {
            case LakeType.GREAT -> {
                if (warmMud && blob > 0.75) {
                    patch = LakeVolume.MUD;
                } else if (water >= 8 && blob > 0.8) {
                    patch = LakeVolume.CLAY;
                } else if (water <= 10 && blob < -0.85) {
                    patch = sel < 0.0 ? LakeVolume.GRAVEL : LakeVolume.SAND;
                }
            }
            case LakeType.FOREST, LakeType.PLATEAU -> {
                if (mossy && blob > 0.75 && c.humidity > 0.15) {
                    patch = LakeVolume.MOSS;
                } else if (warmMud && blob < -0.75) {
                    patch = LakeVolume.MUD;
                } else if (water >= 2 && blob > 0.85) {
                    patch = sel > 0.0 ? LakeVolume.CLAY : LakeVolume.SAND;
                } else if (blob < -0.95) {
                    patch = LakeVolume.GRAVEL;
                }
            }
            case LakeType.MOUNTAIN -> {
                if (water >= 10 && blob > 0.8) {
                    patch = LakeVolume.CLAY;
                } else if (water <= 6 && blob > 0.9 && sel > 0.0) {
                    patch = LakeVolume.SAND;
                }
            }
            case LakeType.GLACIAL -> {
                // rock flour settles as clay in the still deep water
                if (water >= 6 && blob > 0.7) {
                    patch = LakeVolume.CLAY;
                }
            }
            case LakeType.CRATER -> {
                if (blob > 0.8) {
                    patch = (byte) (LakeVolume.ROCK + MarinePlanner.VOLCANIC * 3 + (sel > 0.0 ? 0 : 2));
                } else if (blob < -0.85) {
                    patch = LakeVolume.GRAVEL;
                }
            }
            default -> {
            }
        }
        c.patch = patch;
    }

    /** Plant strengths by depth (above the planned bed), climate and lake type. */
    private void life(LakeColumn c, int x, int z) {
        int type = c.type;
        double t = c.temperature;
        int water = c.surf - c.bed;
        // seagrass meadows: clear cool water lets light, and the meadows, reach deeper
        double tempOk = 0.3 + 0.7 * smoothstep(-0.6, -0.3, t);
        double maxD = 6.0 + 3.0 * smoothstep(0.2, -0.3, t) + (type == LakeType.GREAT ? 2.0 : 0.0);
        double depthOk = smoothstep(0.5, 1.5, water) * (1.0 - smoothstep(maxD - 3.0, maxD + 2.0, water));
        if (tempOk > 0.0 && depthOk > 0.0) {
            double patch = smoothstep(0.0, 0.35, noise(MEADOW, x / 36.0, z / 36.0) + 0.35 * noise(MEADOW_DETAIL, x / 10.0, z / 10.0));
            double flat = 1.0 - smoothstep(0.5, 1.1, c.slope);
            double blowout = noise(BLOWOUT, x / 9.0, z / 9.0) > 0.62 ? 0.0 : 1.0;
            c.meadow = MEADOW_T[type] * tempOk * depthOk * patch * flat * blowout * (1.0 - 0.7 * smoothstep(0.4, 0.8, c.rocky));
        }
        // weed beds in temperate and cold water four to twenty blocks deep
        double cool = (0.5 + 0.5 * smoothstep(-0.8, -0.45, t)) * (1.0 - smoothstep(0.35, 0.6, t));
        double weedDepth = smoothstep(3.5, 6.0, water) * (1.0 - smoothstep(16.0, 23.0, water));
        if (cool > 0.0 && weedDepth > 0.0) {
            double patch = smoothstep(0.3, 0.6, noise(WEED, x / 50.0, z / 50.0) + 0.35 * noise(WEED_DETAIL, x / 15.0, z / 15.0));
            c.weed = WEED_T[type] * cool * weedDepth * patch;
        }
        if (type == LakeType.GREAT && cool > 0.0 && water > 14) {
            // the deep plain of a great lake: fewer, larger beds of tall weed, one or two hundred
            // blocks apart, so the open lake is not an empty expanse
            double beds = smoothstep(0.35, 0.65, noise(DEEP_BEDS, x / 85.0, z / 85.0) + 0.35 * noise(DEEP_BEDS, x / 24.0 + 60.0, z / 24.0 - 35.0));
            double deep = smoothstep(14.0, 20.0, water) * (1.0 - smoothstep(40.0, 46.0, water));
            c.weed = Math.max(c.weed, 0.6 * cool * beds * deep);
        }
        // lily pads in colonies over calm water up to four blocks deep; on a great lake only along
        // sheltered stretches of shore
        if (water >= 1 && water <= 4) {
            double warmEnough = smoothstep(-0.3, -0.05, t);
            double colony = smoothstep(0.25, 0.6, noise(LILY, x / 15.0, z / 15.0) + 0.3 * noise(LILY, x / 5.0 + 50.0, z / 5.0 - 20.0));
            double shelter = type == LakeType.GREAT ? smoothstep(0.15, 0.45, noise(LILY, x / 140.0 - 80.0, z / 140.0 + 15.0)) : 1.0;
            c.lilies = LILY_T[type] * warmEnough * (1.0 - smoothstep(2.5, 4.5, water)) * colony * shelter;
        }
        // lush shallows (dripleaf, moss) in warm humid country
        if (water >= 1 && water <= 3) {
            double climate = smoothstep(0.05, 0.4, t) * smoothstep(-0.05, 0.35, c.humidity);
            c.lush = LUSH_T[type] * climate * smoothstep(0.25, 0.6, noise(LUSH, x / 20.0, z / 20.0));
        }
    }

    /**
     * Bedrock ledges on rocky floors: slabs of the local rock elongated along one of two fixed
     * strike directions, flat-topped and stepped at their edges, one to four blocks high, always
     * two blocks or more under the surface.
     */
    private void outcrop(LakeColumn c, int x, int z) {
        int water = c.surf - c.bed;
        if (c.rocky < 0.3 || water < 4) {
            return;
        }
        double u1 = x * strikeCos1 + z * strikeSin1;
        double w1 = -x * strikeSin1 + z * strikeCos1;
        double u2 = x * strikeCos2 + z * strikeSin2;
        double w2 = -x * strikeSin2 + z * strikeCos2;
        double b1 = noise(STRIKE, u1 / 40.0, w1 / 12.0);
        double b2 = noise(STRIKE, u2 / 40.0 + 100.0, w2 / 12.0);
        double beds = lerp(smoothstep(-0.3, 0.3, noise(STRIKE_MIX, x / 500.0, z / 500.0)), b1, b2) + 0.3 * noise(LEDGE, x / 9.0, z / 9.0);
        double threshold = 0.72 - 0.35 * c.rocky;
        double o = smoothstep(threshold, threshold + 0.25, beds);
        if (o > 0.0) {
            int height = (int) Math.round(TMath.terrace(o * (1.0 + 3.0 * c.rocky), 2.0, 0.6));
            c.outcrop = Math.max(0, Math.min(water - 2, Math.min(4, height)));
        }
    }

    // =========================================================================================
    // Shore plants
    // =========================================================================================

    /** Strength of the reed beds on a dry shore column at {@code (x, z)} beside a lake of the type. */
    public double reeds(int x, int z, int type, double temperature) {
        double patch = smoothstep(0.15, 0.5, noise(REED, x / 18.0, z / 18.0) + 0.3 * noise(REED, x / 6.0 + 30.0, z / 6.0));
        return REED_T[type] * smoothstep(-0.4, -0.15, temperature) * patch;
    }

    /** Strength of moss carpets on a dry shore column in humid country. */
    public double shoreMoss(int x, int z, double temperature, double humidity) {
        return smoothstep(-0.25, 0.1, temperature) * smoothstep(0.0, 0.4, humidity) * smoothstep(0.4, 0.7, noise(MOSS, x / 14.0, z / 14.0));
    }

    // =========================================================================================
    // Wood
    // =========================================================================================

    /**
     * The tree species the climate of a place suggests, for logs, boats and piers; the feature
     * replaces it by the species of the biome there. {@code h} picks between mixed species.
     */
    public static int woodGuess(double temperature, double humidity, long h) {
        double r = SeedMixer.unit(h);
        if (temperature < -0.4) {
            return SPRUCE;
        }
        if (temperature < -0.1) {
            return r < 0.3 ? BIRCH : SPRUCE;
        }
        if (temperature > 0.55) {
            return humidity > 0.25 ? JUNGLE : ACACIA;
        }
        if (humidity > 0.5 && temperature > 0.15) {
            return r < 0.5 ? DARK_OAK : OAK;
        }
        return r < 0.3 ? BIRCH : OAK;
    }

    // =========================================================================================
    // Discoveries
    // =========================================================================================

    public static final int WRECK = 1;
    public static final int HUT = 2;
    public static final int TOWER = 3;
    public static final int PIER = 4;
    public static final String[] DISCOVERY_NAMES = {"none", "boat_wreck", "drowned_hut", "drowned_tower", "old_pier"};

    /**
     * Something to discover on a lake floor: a small rowing boat wreck, the walls of a drowned
     * hut, the stump of a drowned stone tower, or the pilings of an old pier. {@code y} is the
     * bed it stands on, {@code dir} the facing its length runs along (0 north, 1 east, 2 south,
     * 3 west), {@code a} and {@code b} its size along and across.
     */
    public record Discovery(int kind, int x, int y, int z, int dir, int a, int b, int surf, int wood, int family, long seed) {
        /** Farthest a block of it lies from {@code (x, z)}. */
        public int reach() {
            return kind == TOWER ? b + 6 : Math.max(a, b) + 3;
        }
    }

    /** The discovery of a grid cell, or null. Cached. */
    public Discovery discovery(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        Discovery cached = discoveries.get(key);
        if (cached == null) {
            cached = computeDiscovery(cx, cz);
            if (discoveries.size() > 8192) {
                discoveries.clear();
            }
            discoveries.put(key, cached);
        }
        return cached == NO_DISCOVERY ? null : cached;
    }

    /**
     * Tries up to eight spots of the cell in turn; the first that lies in a lake decides, from its
     * type, whether the cell holds anything, and the first spot whose depth and floor suit a kind
     * becomes the site. Piers stand in the shallows near the shore, the rest on fairly level beds
     * deep enough to cover them; drowned buildings stood on what was once the shore, so they lie
     * in water shallow enough to see them from a boat, while a boat may have sunk anywhere.
     */
    private Discovery computeDiscovery(int cx, int cz) {
        long h = SeedMixer.hash2(salt(7), cx, cz);
        LakeColumn c = new LakeColumn();
        boolean rolled = false;
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = (int) Math.floor((cx + 0.1 + 0.8 * SeedMixer.cellUnit(h, attempt, 1, 1)) * DISCOVERY_CELL);
            int z = (int) Math.floor((cz + 0.1 + 0.8 * SeedMixer.cellUnit(h, attempt, 2, 2)) * DISCOVERY_CELL);
            column(x, z, c);
            if (!c.wet) {
                continue;
            }
            if (!rolled) {
                rolled = true;
                if (SeedMixer.cellUnit(h, 0, 0, 3) > DISCOVERY_T[c.type] * (0.4 + 0.6 * c.size)) {
                    return NO_DISCOVERY;
                }
            }
            long s = SeedMixer.mix(h + attempt * 0x9E3779B97F4A7C15L);
            int water = c.surf - c.bed;
            double pick = SeedMixer.unit(s);
            int type = c.type;
            int wood = woodGuess(c.temperature, c.humidity, s * 31L);
            Discovery d = null;
            if (water <= 3 && type != LakeType.CRATER && type != LakeType.GLACIAL) {
                d = pier(c, s, wood);
            } else if (water >= 3 && type != LakeType.MARSH) {
                boolean stone = type != LakeType.GLACIAL && type != LakeType.CRATER && water >= 5 && water <= 15;
                if (pick < 0.45 || !stone) {
                    d = wreck(c, s, wood);
                } else if (pick < 0.75 || c.size < 0.5) {
                    d = hut(c, s, wood);
                } else {
                    d = tower(c, s, wood);
                }
            }
            if (d != null) {
                return d;
            }
        }
        return NO_DISCOVERY;
    }

    /** Lowest and highest planned bed over a rectangle of columns, or null if any of them is not lake water at least {@code minWater} deep. */
    private int[] bedRange(int x0, int z0, int x1, int z1, int minWater) {
        LakeColumn c = new LakeColumn();
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                column(x, z, c);
                if (!c.wet || c.surf - c.bed < minWater || c.outcrop > 0) {
                    return null;
                }
                lo = Math.min(lo, c.bed);
                hi = Math.max(hi, c.bed);
            }
        }
        return new int[]{lo, hi};
    }

    private static int median(int lo, int hi) {
        return (lo + hi + 1) / 2;
    }

    /** Bed range over the square of half-size {@code half} around a column, as {@link #bedRange}. */
    private int[] bedAround(LakeColumn c, int half, int minWater) {
        return bedRange(c.x - half, c.z - half, c.x + half, c.z + half, minWater);
    }

    /** A rowing boat, five to seven blocks long, centred on the site. */
    private Discovery wreck(LakeColumn c, long s, int wood) {
        int dir = (int) (SeedMixer.cellUnit(s, 0, 0, 1) * 4.0);
        int len = 5 + (int) (SeedMixer.cellUnit(s, 0, 0, 2) * 3.0);
        int[] r = bedAround(c, len / 2 + 2, 3);
        if (r == null || r[1] - r[0] > 1) {
            return null;
        }
        int y = median(r[0], r[1]);
        if (c.surf - (y + 2) < 1) {
            return null;
        }
        return new Discovery(WRECK, c.x, y, c.z, dir, len, 3, c.surf, wood, c.family, s);
    }

    /** The walls of a hut, five to seven by four to six blocks, centred on the site. */
    private Discovery hut(LakeColumn c, long s, int wood) {
        int dir = (int) (SeedMixer.cellUnit(s, 0, 0, 1) * 4.0);
        int a = 5 + (int) (SeedMixer.cellUnit(s, 0, 0, 2) * 3.0);
        int b = 4 + (int) (SeedMixer.cellUnit(s, 0, 0, 3) * 3.0);
        int[] r = bedAround(c, Math.max(a, b) / 2 + 2, 5);
        if (r == null || r[1] - r[0] > 2) {
            return null;
        }
        int y = median(r[0], r[1]);
        if (c.surf - (y + 4) < 1) {
            return null;
        }
        return new Discovery(HUT, c.x, y, c.z, dir, a, b, c.surf, wood, c.family, s);
    }

    /** The stump of a round tower of radius two or three, its rubble fanning out on one side. */
    private Discovery tower(LakeColumn c, long s, int wood) {
        int radius = SeedMixer.cellUnit(s, 0, 0, 2) < 0.5 ? 2 : 3;
        int[] r = bedAround(c, radius + 1, 4);
        if (r == null || r[1] - r[0] > 2) {
            return null;
        }
        // the rubble fan must lie in the lake too
        if (bedAround(c, radius + 6, 2) == null) {
            return null;
        }
        int dir = (int) (SeedMixer.cellUnit(s, 0, 0, 1) * 4.0);
        return new Discovery(TOWER, c.x, median(r[0], r[1]), c.z, dir, 0, radius, c.surf, wood, c.family, s);
    }

    /**
     * Two rows of posts reaching from the shallows out into the lake, along the floor's fall line
     * (snapped to an axis), until the water gets too deep for a pier.
     */
    private Discovery pier(LakeColumn c, long s, int wood) {
        if (c.slope < 0.04) {
            return null;
        }
        // downhill points away from the shore, out into the lake
        int dir = Math.abs(c.gradX) > Math.abs(c.gradZ) ? (c.gradX > 0 ? 3 : 1) : (c.gradZ > 0 ? 0 : 2);
        int ux = LakeVolume.DIR_X[dir];
        int uz = LakeVolume.DIR_Z[dir];
        int vx = -uz;
        int vz = ux;
        LakeColumn p = new LakeColumn();
        int len = 0;
        for (int u = 0; u < 12; u++) {
            boolean ok = true;
            for (int v = -1; v <= 1; v += 2) {
                column(c.x + ux * u + vx * v, c.z + uz * u + vz * v, p);
                ok &= p.wet && p.surf == c.surf && p.surf - p.bed <= 7 && p.outcrop == 0;
            }
            if (!ok) {
                break;
            }
            len = u + 1;
        }
        if (len < 5) {
            return null;
        }
        return new Discovery(PIER, c.x, c.bed, c.z, dir, len, 3, c.surf, wood, c.family, s);
    }

    // =========================================================================================
    // Cheap chunk tests
    // =========================================================================================

    /**
     * True if any part of the square {@code [minX - margin, minX + 15 + margin]} (same in z) may
     * lie in a lake. Uses the model's corner cache only.
     */
    public boolean mayHaveLake(int minX, int minZ, int margin) {
        for (int x = (minX - margin) & ~3; x <= minX + 15 + margin + 4; x += 4) {
            for (int z = (minZ - margin) & ~3; z <= minZ + 15 + margin + 4; z += 4) {
                ColumnSample s = model.sample(x, z);
                if (s.bodyInside && (s.bodyKind == WaterBody.LAKE || s.bodyKind == WaterBody.CRATER)) {
                    return true;
                }
            }
        }
        return false;
    }
}
