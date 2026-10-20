package com.terrahaul.worldgen.terrain;

import static com.terrahaul.worldgen.noise.TMath.clamp01;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ecological zones of Terrahaul's volcanoes: where the ground is bare rock and ash, where lava
 * flows ran, where old fires killed the forest, and where fertile volcanic soil carries a denser
 * forest than the land around the volcano.
 * <p>
 * The zones are a deterministic function of the block column, computed from the volcano's site
 * ({@link TerrainModel#nearestVolcano}) and cheap seeded noise, never from block reads. The surface
 * rule picks materials from them and the {@code terrahaul:volcano_zone} placement filter places
 * vegetation by them, so both always agree: snags stand on scorched ground, forests on soil, and
 * nothing grows on the bare cone.
 * <p>
 * From the summit down ({@code t} is the distance from the centre over the volcano's radius):
 * <ul>
 *   <li><b>crater and upper cone:</b> bare rock and ash. The crater is dark volcanic rock in
 *       patches (blackstone, basalt, smooth basalt, tuff, ash on its gentler floor, red and brown
 *       oxidised scoria), with plates of crust and magma beside a lava lake. The lower edge of the
 *       bare cone is lobed, reaches further down the barrancos (ash and scree wash down them) than
 *       along the ridges between them, and frays into patches of ash and grass over a band some
 *       fifty blocks wide. Active volcanoes (lava in the crater) have a large bare cone, long
 *       dormant ones (crater lakes) only a bare rim. Side cones are bare cinder cones of red and
 *       brown scoria;</li>
 *   <li><b>lava flows:</b> the tongues of rock the terrain raises on the cone ({@link ConeShape}),
 *       leaving the crater at a notch of its rim, meandering, widening downslope and ending in a
 *       lobed front, often deep inside the forest. Active volcanoes have several, dormant ones
 *       few or none;</li>
 *   <li><b>pioneer belt:</b> just below the bare cone, grass with ash patches, shrubs and young
 *       trees;</li>
 *   <li><b>forest:</b> the established flanks, wooded more densely than the surrounding land and
 *       fading into it at the foot of the volcano;</li>
 *   <li><b>burn scars:</b> old fire scars on the mid and lower flanks, shaped by smooth noise and
 *       more frequent towards the cone and on active volcanoes, plus a scorched margin along every
 *       lava flow that widens ahead of its front, where the lava ran into the forest. A scar has a
 *       core of dead snags on scorched ground and a fringe where burned and living trees mix.
 *       Scars are burned forest, so they fade out towards the climatic tree line; above it the
 *       flanks of tall volcanoes are alpine grassland up to the bare cone.</li>
 * </ul>
 * Every weight is continuous, so materials and vegetation fade into each other instead of meeting
 * at hard lines; only the margins of lava flows are sharp, as they are in nature.
 * <p>
 * In cold air, snow lies only where {@link #snow} allows: never on the hot ground of a crater that
 * is not long dormant or on the flows of an active volcano, and elsewhere on the bare cone in
 * patches broken up by ash and scoria, thickest high on the cone of a cold, dormant volcano.
 * <p>
 * Cost: one site lookup and a handful of 2D noise samples per column (about a microsecond with
 * the model columns cached); {@link #at} keeps the last column per thread, so the surface rule,
 * which asks once per block of a column, computes each column once.
 */
public final class VolcanoZones {
    /** Search radius for the volcano owning a column (larger than any volcano's radius). */
    private static final double MAX_RADIUS = 3200.0;
    private static final long SALT = 0x7A11_5CA2_0FF1_CE5EL;

    private static final Map<Long, SimplexNoise> NOISES = new ConcurrentHashMap<>();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private VolcanoZones() {
    }

    /** The zones a placement filter can select (see {@link Zone#weight}). */
    public enum Kind {
        /** Any column near a volcano; a cheap per-chunk test that skips everything else. */
        ANY("any"),
        /** Bare rock and ash: crater, upper cone and lava flows. */
        CONE("cone"),
        /** Grass with ash patches just below the bare cone. */
        PIONEER("pioneer"),
        /** Living forest on the flanks. */
        FOREST("forest"),
        /** Burn scars and the scorched margins of lava flows. */
        BURN("burn"),
        /**
         * Anywhere living trees may stand: 1 off volcanoes, fading out across burn scars and
         * towards the bare cone; for the biomes' own trees.
         */
        LIVING("living");

        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        public static Kind byName(String name) {
            for (Kind k : values()) {
                if (k.serializedName.equals(name)) {
                    return k;
                }
            }
            return null;
        }
    }

    /**
     * Ground of a volcano column, as the surface rule lays it (the block named in brackets). Bare
     * kinds cover the top few blocks of the column; scorched kinds only its top block, over the
     * normal soil profile.
     */
    public enum Ground {
        /** The biome's own ground (grass and soil): nothing volcanic. */
        SOIL(false),
        /** Loose ash and scoria (gravel; tuff where it cannot rest). */
        ASH(true),
        /** Consolidated ash, pale near the rim (tuff). */
        TUFF(true),
        /** Dark scoria (smooth basalt). */
        SCORIA(true),
        /** Columnar and blocky basalt: broken lava crust, crater walls (basalt). */
        BASALT(true),
        /** Fresh lava rock: lava flow cores and the crests between gullies (blackstone). */
        LAVA_ROCK(true),
        /** Red oxidised scoria: side cones, patches in the crater and on the upper cone (red terracotta). */
        RED_SCORIA(true),
        /** Brown oxidised scoria (brown terracotta). */
        BROWN_SCORIA(true),
        /** Rock altered by hot gases, pale and pinkish, around vents in the crater (granite). */
        ALTERED(true),
        /** Glowing crust next to a crater's lava lake (magma block). */
        MAGMA(true),
        /** Charred forest litter in burn scars (podzol). */
        CHARRED(false),
        /** Scorched bare soil (coarse dirt). */
        SCORCHED(false),
        /** Burned-out root mats (rooted dirt). */
        BURNT_ROOTS(false),
        /** Ash beds where a fire burned hottest (gravel). */
        CINDERS(false);

        private final boolean bare;

        Ground(boolean bare) {
            this.bare = bare;
        }

        /** True for bare rock and ash, which replace the soil profile; false for the top block only. */
        public boolean bare() {
            return bare;
        }
    }

    /** The zone weights of one column. All weights are 0 off volcanoes. */
    public static final class Zone {
        /**
         * How much the column is the volcano's own surface: 1 on the cone, 0 off volcanoes and
         * where other terrain (a mountain the cone leans against) rises above the cone.
         */
        public double presence;
        /** Distance from the volcano's centre over its radius; large off volcanoes. */
        public double t;
        /** Height of the cone above its foot over the volcano's height (0 at the foot, about 1 on the rim). */
        public double elevation;
        /** Bare rock and ash: 1 on the upper cone, falling to 0 across its frayed lower edge. */
        public double bare;
        /** Inside a lava flow: just above 0 at its margin, rising to 1 in its core; 0 outside. */
        public double flow;
        /** Burn intensity: 1 in the core of a burn scar, fading to 0 in living forest. */
        public double burn;
        /** Living forest on fertile soil (0..1), the weight of the extra trees. */
        public double forest;
        /** Pioneer belt below the bare cone (0..1). */
        public double pioneer;
        /** Barrancos of the cone: -1 on gully floors, 1 on the ridges between them. */
        public double gully;
        /**
         * Finer radial erosion furrows (-1 furrow, 1 rib), tens of blocks apart and wavy, that
         * streak the bare cone between its big gullies.
         */
        public double streak;
        /** Nearness to the crater rim: 1 on the rim and inside the crater, 0 well below it. */
        public double rim;
        /** On a side cone: 1 on its cone, fading to 0 at its foot. */
        public double sideCone;
        /** Inside the crater: 0 at the rim's crest, rising to 1 at the centre; 0 outside. */
        public double depth;
        /**
         * Inside the crater, the sectors of its wall where hot gases rose and oxidised the rock
         * (above about 0.5); 0 outside.
         */
        public double vents;
        /** Inside the crater. */
        public boolean crater;
        /** Surface of the crater's lava lake, or {@link ColumnSample#NO_WATER}; set inside lava craters. */
        public double lavaLevel;
        /** The volcano's activity: 0 long dormant (crater lake) .. 1 lava in the crater. */
        public double activity;

        void clear() {
            presence = 0.0;
            t = 9.0;
            elevation = 0.0;
            bare = 0.0;
            flow = 0.0;
            burn = 0.0;
            forest = 0.0;
            pioneer = 0.0;
            gully = 0.0;
            streak = 0.0;
            rim = 0.0;
            sideCone = 0.0;
            depth = 0.0;
            vents = 0.0;
            crater = false;
            lavaLevel = ColumnSample.NO_WATER;
            activity = 0.0;
        }

        /** True if the column lies on a lava flow. */
        public boolean inFlow() {
            return flow > 0.0;
        }

        /** Weight of a zone in [0, 1] (for {@link Kind#ANY}: 1 anywhere on a volcano). */
        public double weight(Kind kind) {
            return switch (kind) {
                case ANY -> presence > 0.0 ? 1.0 : 0.0;
                case CONE -> (inFlow() ? 1.0 : bare) * presence;
                case PIONEER -> pioneer;
                case FOREST -> forest;
                case BURN -> burn;
                case LIVING -> 1.0 - Math.max(weight(Kind.CONE), burn);
            };
        }
    }

    /**
     * True if {@code (x, z)} may lie on a volcano: a cheap test on the cached model column, used to
     * skip whole chunks before any per-position work.
     */
    public static boolean near(TerrainModel model, int x, int z) {
        return model.sample(x & ~3, z & ~3).volcano > 0.0;
    }

    /**
     * The zones of column {@code (x, z)}. The returned instance belongs to the calling thread and
     * stays valid until its next call; repeated calls for the same column (the surface rule asks
     * once per block of a column) reuse the result.
     */
    public static Zone at(TerrainModel model, int x, int z) {
        Scratch sc = SCRATCH.get();
        if (!sc.valid || sc.x != x || sc.z != z || sc.seedKey != model.siteSeed() || sc.settings != model.settings()) {
            compute(model, x, z, sc.zone, sc);
            sc.valid = true;
            sc.x = x;
            sc.z = z;
            sc.seedKey = model.siteSeed();
            sc.settings = model.settings();
        }
        return sc.zone;
    }

    /** Computes the zones of column {@code (x, z)} into a caller-owned instance (no caching). */
    public static void compute(TerrainModel model, int x, int z, Zone out) {
        compute(model, x, z, out, SCRATCH.get());
    }

    /**
     * The ground of column {@code (x, z)} whose top block is at {@code y}, for zones from
     * {@link #at}. Every choice comes from smooth noise (patches several blocks across, boundaries
     * that wander a little with height), never from per-block randomness, so materials form
     * single-colour patches and streaks:
     * <ul>
     *   <li>lava flows: blackstone cores broken into blocky basalt in places, inside smooth basalt
     *       margins;</li>
     *   <li>the crater: dark patches of blackstone, basalt, smooth basalt and tuff, ash on its
     *       gentler floor, red and brown oxidised scoria and pale altered rock in places; beside a
     *       lava lake magma crust, and on the lake plates of blackstone and basalt cracked with
     *       magma;</li>
     *   <li>side cones: red and brown oxidised scoria with dark cinders;</li>
     *   <li>the bare cone: radial streaks following its barrancos, light ash (gravel) and tuff down
     *       the gully floors, dark scoria and lava rock along the ridges, pale tuff around the rim,
     *       and scattered patches of oxidised scoria;</li>
     *   <li>the frayed edge of the cone: patches of the cone's ground between patches of soil;</li>
     *   <li>burn scars: patches of charred litter, scorched soil and root mats, with ash beds in
     *       the hottest cores, thinning out into grass across the scar's fringe.</li>
     * </ul>
     */
    public static Ground ground(TerrainModel model, Zone zone, int x, int y, int z) {
        if (zone.presence <= 0.0) {
            return Ground.SOIL;
        }
        // the surface rule asks once per block of a column, always with the column's top
        Scratch sc = SCRATCH.get();
        if (sc.groundZone == zone && sc.groundX == x && sc.groundY == y && sc.groundZ == z && sc.groundKey == model.siteSeed()) {
            return sc.ground;
        }
        Ground ground = chooseGround(sc.noise(model), zone, x, y, z);
        sc.groundZone = zone;
        sc.groundX = x;
        sc.groundY = y;
        sc.groundZ = z;
        sc.groundKey = model.siteSeed();
        sc.ground = ground;
        return ground;
    }

    private static Ground chooseGround(SimplexNoise noise, Zone zone, int x, int y, int z) {
        // a faint 3D term only roughens boundaries; the patches themselves are 10-30 blocks across
        double wobble = 0.04 * noise.noise3(x / 3.5, y / 3.5 + 500.0, z / 3.5);
        if (zone.inFlow()) {
            if (zone.flow + 0.15 * noise.noise2(x / 12.0 - 20.0, z / 12.0 + 45.0) + wobble <= 0.4) {
                return Ground.SCORIA;
            }
            // the core's crust broke into blocks of basalt in places
            return noise.noise2(x / 9.0 + 77.0, z / 9.0 - 31.0) > 0.35 ? Ground.BASALT : Ground.LAVA_ROCK;
        }
        if (zone.crater) {
            return craterGround(noise, zone, x, y, z, wobble);
        }
        if (zone.sideCone > 0.45 + 0.25 * noise.noise2(x / 15.0 + 40.0, z / 15.0 - 90.0)) {
            // a cinder cone of red and brown scoria with dark cinders and ash
            double p = noise.noise2(x / 24.0 - 140.0, z / 24.0 + 60.0) + wobble;
            return p > 0.3 ? Ground.RED_SCORIA : p > -0.05 ? Ground.BROWN_SCORIA : p > -0.45 ? Ground.SCORIA : Ground.ASH;
        }
        if (zone.bare * zone.presence > 0.5 + 0.3 * noise.noise2(x / 20.0 - 70.0, z / 20.0 + 23.0)) {
            // scattered patches of oxidised scoria, more of them towards the rim
            double rust = noise.noise2(x / 45.0 + 300.0, z / 45.0 - 210.0) + 0.1 * zone.rim + wobble;
            if (rust > 0.72) {
                return noise.noise2(x / 30.0 - 90.0, z / 30.0 + 45.0) > 0.0 ? Ground.RED_SCORIA : Ground.BROWN_SCORIA;
            }
            // radial streaks: ash down the gullies and erosion furrows, dark rock along the ribs, an
            // apron of loose ash fields towards the lower edge of the bare cone
            double s = 0.58 + 0.2 * zone.gully + 0.2 * zone.streak + 0.22 * noise.noise2(x / 40.0 + 210.0, z / 40.0 - 33.0) + wobble
                - 0.3 * smoothstep(0.95, 0.45, zone.bare);
            s += (0.31 - s) * 0.7 * zone.rim;
            return s < 0.24 ? Ground.ASH : s < 0.44 ? Ground.TUFF : s < 0.86 ? Ground.SCORIA : Ground.LAVA_ROCK;
        }
        double b = zone.burn;
        if (b <= 0.0 || b < 0.3 + 0.35 * (0.5 + 0.5 * noise.noise2(x / 18.0 + 300.0, z / 18.0 - 80.0))) {
            return Ground.SOIL;
        }
        if (noise.noise2(x / 40.0 - 150.0, z / 40.0 + 60.0) + 0.8 * (b - 0.95) > 0.62) {
            return Ground.CINDERS;
        }
        double tone = 0.55 + 0.55 * noise.noise2(x / 32.0 + 90.0, z / 32.0 + 260.0) - 0.25 * (b - 0.6) + wobble;
        return tone < 0.32 ? Ground.CHARRED : tone < 0.88 ? Ground.SCORCHED : Ground.BURNT_ROOTS;
    }

    /**
     * Inside the crater: dark volcanic rock in patches a dozen to thirty blocks across, oxidised
     * scoria and altered rock in places, and around a lava lake magma crust on its shore and plates
     * of blackstone and basalt, cracked with magma, on the lake itself.
     */
    private static Ground craterGround(SimplexNoise noise, Zone zone, int x, int y, int z, double wobble) {
        if (zone.lavaLevel != ColumnSample.NO_WATER && y <= zone.lavaLevel + 2.0) {
            if (y < zone.lavaLevel) {
                // a plate of crust, flush with the lava
                double crack = noise.noise2(x / 5.0 + 11.0, z / 5.0 - 9.0) + wobble;
                return crack > 0.45 ? Ground.MAGMA : crack > -0.15 ? Ground.LAVA_ROCK : Ground.BASALT;
            }
            return noise.noise2(x / 16.0 + 5.0, z / 16.0 + 140.0) > 0.15 ? Ground.MAGMA : Ground.LAVA_ROCK;
        }
        // oxidised and altered rock in a sector or two of the wall, where hot gases rose
        double rust = zone.vents + 0.3 * noise.noise2(x / 24.0 - 520.0, z / 24.0 + 140.0) + wobble - 0.4 * smoothstep(0.45, 0.75, zone.depth);
        if (rust > 0.62) {
            double p = noise.noise2(x / 30.0 + 230.0, z / 30.0 - 60.0);
            return p > 0.1 ? Ground.RED_SCORIA : p > -0.45 ? Ground.BROWN_SCORIA : Ground.ALTERED;
        }
        // streaks of ash and tuff down the wall between ribs of dark rock, and on the floor dark
        // rock with flats of ash
        double s = 0.55 + 0.3 * zone.streak + 0.22 * noise.noise2(x / 30.0 + 410.0, z / 30.0 - 260.0) + wobble
            - 0.2 * smoothstep(0.55, 0.85, zone.depth) * (0.5 + 0.5 * noise.noise2(x / 40.0 - 77.0, z / 40.0 + 31.0));
        return s < 0.22 ? Ground.ASH : s < 0.38 ? Ground.TUFF : s < 0.62 ? Ground.SCORIA : s < 0.8 ? Ground.BASALT : Ground.LAVA_ROCK;
    }

    /**
     * Whether snow may lie on the bare volcanic ground of zone {@code zone} at column
     * {@code (x, z)}, whose climate temperature is {@code temperature}, in a snowy biome or not.
     * Never on hot ground: inside a crater that is not long dormant, or on the lava flows of an
     * active volcano. In a snowy biome it lies in patches broken up by blobs of ash and scoria,
     * covering most of the upper cone of a cold, dormant volcano and less downhill and on active
     * ones; elsewhere in snowfields on cold ground. The surface rule keeps it off steep ground.
     */
    public static boolean snow(TerrainModel model, Zone zone, int x, int z, double temperature, boolean snowyBiome) {
        if (zone.crater && zone.activity > 0.4 || zone.inFlow() && zone.activity > 0.6) {
            return false;
        }
        SimplexNoise noise = SCRATCH.get().noise(model);
        double blobs = 0.5 + 0.5 * (0.65 * noise.noise2(x / 14.0 + 610.0, z / 14.0 - 330.0) + 0.35 * noise.noise2(x / 45.0 - 70.0, z / 45.0 + 820.0));
        if (snowyBiome) {
            double cover = 0.3 + 0.55 * smoothstep(0.35, 0.85, zone.elevation) - 0.5 * zone.activity - 0.3 * zone.sideCone;
            return blobs < clamp01(cover);
        }
        return temperature < -0.3 && blobs < Math.min(0.9, (-0.3 - temperature) / 0.17 * 0.8);
    }

    private static void compute(TerrainModel model, int x, int z, Zone o, Scratch sc) {
        o.clear();
        ColumnSample column = model.sample(x & ~3, z & ~3);
        if (column.volcano <= 0.0) {
            return;
        }
        // the climatic tree line, as TreeLine places it: warm climates carry forests higher
        double treeLine = 230.0 + 330.0 * clamp01((column.baseTemperature + 1.0) / 2.0);
        TerrainModel.VolcanoSite v = model.nearestVolcano(x, z, MAX_RADIUS * model.settings().horizontalScale());
        if (v == null) {
            return;
        }
        double dx = x - v.x;
        double dz = z - v.z;
        double r = Math.sqrt(dx * dx + dz * dz);
        double t = r / v.radius;
        if (t >= 1.0) {
            return;
        }
        Site s = sc.site(model, v);
        SimplexNoise noise = sc.noise(model);
        double theta = Math.atan2(dz, dx);

        // the cone surface exactly as TerrainModel shapes it, crater included; where the generated
        // ground lies far above it, other terrain (a mountain the volcano leans against) owns the column
        double[] crater = sc.crater;
        double cone = model.volcanoSurface(v, x, z, crater) - v.baseHeight;
        if (crater[0] >= 0.0) {
            o.crater = true;
            o.lavaLevel = v.lavaLevel;
        }
        double surface = model.interpolatedHeight(x, z);
        double above = surface - (v.baseHeight + cone);
        double presence = 1.0 - smoothstep(4.0, 24.0, above);
        if (presence <= 0.0) {
            return;
        }
        // the lava flows, barrancos and side cones as the terrain shapes them
        double[] shape = sc.cone;
        model.volcanoCone(v, x, z, shape);
        double gully = shape[2] * smoothstep(v.craterRadius, v.radius * 0.6, r);
        o.presence = presence;
        o.t = t;
        o.elevation = Math.max(0.0, cone / v.height);
        o.gully = gully;
        o.streak = Math.sin(theta * s.streaks + s.streakPhase + 1.8 * noise.noise2(x / 60.0 - 300.0, z / 60.0 + 120.0))
            * smoothstep(v.craterRadius, v.craterRadius * 1.5, r);
        o.activity = s.activity;
        o.sideCone = shape[3];
        // measured from the rim's own crest, which wanders in and out around the crater
        if (o.crater) {
            o.rim = 1.0;
            o.depth = crater[0];
            // furrows some twenty to thirty blocks apart down the inner wall, which bend as they run
            // and fade out on the floor rather than converge on the centre
            double c = Math.cos(theta);
            double sn = Math.sin(theta);
            double ring = Math.max(3.0, v.craterRadius / 18.0);
            o.streak = noise.noise2(c * ring + 50.0 + 0.8 * crater[0], sn * ring - 80.0 + 0.5 * crater[0])
                * (1.0 - smoothstep(0.3, 0.6, crater[0]));
            o.vents = noise.noise2(c * 1.3 + s.lobeX + 17.0, sn * 1.3 + s.lobeZ - 23.0);
        } else if (r < v.craterReach) {
            o.rim = smoothstep(crater[2] + 0.1 * v.radius, crater[2], crater[1]);
        } else if (r < v.craterReach + 0.1 * v.radius) {
            double rim = model.craterRimRadius(v, x, z);
            o.rim = smoothstep(rim + 0.1 * v.radius, rim, r);
        }

        // ---- bare cone: lobed edge, lower in the gullies, frayed into ash and grass patches ----
        double lobe = noise.noise2(Math.cos(theta) * 1.7 + s.lobeX, Math.sin(theta) * 1.7 + s.lobeZ);
        double edge = s.edge * (1.0 + 0.2 * lobe - 0.12 * gully);
        double fray = 0.5 * s.band * noise.noise2(x / 34.0 + 31.7, z / 34.0 - 12.9);
        double bare = o.crater ? 1.0 : smoothstep(edge + 0.5 * s.band, edge - 0.5 * s.band, t + fray);
        // side cones are bare cinder cones, wherever they stand
        bare = Math.max(bare, smoothstep(0.35, 0.75, o.sideCone));
        o.bare = bare;

        // ---- lava flows: the tongues of rock the terrain raises, and their scorched halo ---------
        double flow = o.crater ? 0.0 : shape[0];
        double halo = o.crater ? 0.0 : shape[1];
        o.flow = flow;

        // ---- burn scars ---------------------------------------------------------------------
        // position in the vegetated belt: 0 at the edge of the bare cone, 1 at the foot
        double belt = clamp01((t - edge) / Math.max(0.05, 1.0 - edge));
        double scar = 0.72 * noise.noise2(x / s.scarScale + s.scarX, z / s.scarScale + s.scarZ)
            + 0.28 * noise.noise2(x / (0.3 * s.scarScale) - 40.0, z / (0.3 * s.scarScale) + 77.0)
            + 0.08 * noise.noise2(x / 18.0 + 11.0, z / 18.0 - 9.0);
        double bias = 0.22 * s.activity + 0.2 * (1.0 - belt) - 0.17;
        double burn = Math.max(smoothstep(0.24, 0.46, scar + bias), halo);
        // scars are burned forest: they fade out at the volcano's foot and towards the tree line
        double foot = smoothstep(0.98, 0.84, t) * smoothstep(treeLine + 10.0, treeLine - 70.0, surface);
        double soil = (1.0 - bare) * (flow > 0.0 ? 0.0 : 1.0) * presence;
        o.burn = burn * soil * foot;

        // ---- living forest and the pioneer belt -------------------------------------------------
        double young = smoothstep(edge + 0.5 * s.band, edge + 0.5 * s.band + 0.13, t);
        o.forest = soil * (1.0 - o.burn) * young * smoothstep(1.0, 0.82, t);
        o.pioneer = soil * (1.0 - young) * (1.0 - o.burn);
    }

    /** Per-volcano parameters derived from its site and a seeded hash. */
    private static final class Site {
        TerrainModel.VolcanoSite volcano;
        double activity;
        double craterT;
        /** Mean distance (over the radius) of the lower edge of the bare cone. */
        double edge;
        /** Width of the frayed edge of the bare cone, over the radius. */
        double band;
        double lobeX;
        double lobeZ;
        double scarX;
        double scarZ;
        /** Size of the burn scar noise: larger scars on larger volcanoes. */
        double scarScale;
        /** Number of erosion furrows around the cone. */
        int streaks;
        double streakPhase;

        void init(TerrainModel model, TerrainModel.VolcanoSite v) {
            volcano = v;
            long h = SeedMixer.hash2(model.siteSeed() ^ SALT, (int) Math.floor(v.x), (int) Math.floor(v.z));
            activity = v.activity;
            craterT = v.craterRadius / v.radius;
            edge = craterT + 0.07 + 0.22 * activity;
            band = (40.0 + 0.03 * v.radius) / v.radius;
            lobeX = 400.0 * unit(h, 1);
            lobeZ = 400.0 * unit(h, 2);
            scarX = 400.0 * unit(h, 3);
            scarZ = 400.0 * unit(h, 4);
            scarScale = 220.0 + 0.08 * v.radius;
            // furrows about 50 blocks apart half way down the cone
            streaks = Math.max(24, (int) (v.radius * 0.5 * Math.PI * 2.0 / 50.0 * (0.8 + 0.4 * unit(h, 7))));
            streakPhase = Math.PI * 2.0 * unit(h, 8);
        }

        private static double unit(long hash, int channel) {
            return SeedMixer.unit(SeedMixer.mix(hash + channel * 0xD1B54A32D192ED03L));
        }
    }

    /** Per-thread state: the last column's zones, the last volcano's parameters and the noise. */
    private static final class Scratch {
        final Zone zone = new Zone();
        /** Crater influence, distance and rim radius from {@link TerrainModel#volcanoSurface}. */
        final double[] crater = new double[CraterShape.OUT];
        /** Lava flows, halo, barrancos and side cones from {@link TerrainModel#volcanoCone}. */
        final double[] cone = new double[ConeShape.OUT];
        boolean valid;
        int x;
        int z;
        long seedKey;
        TerrainSettings settings;
        final Site site = new Site();
        /** The last ground choice and its key (zone instance, column, top and world). */
        Zone groundZone;
        int groundX;
        int groundY;
        int groundZ;
        long groundKey;
        Ground ground;
        SimplexNoise noise;
        long noiseKey;
        boolean noiseValid;

        Site site(TerrainModel model, TerrainModel.VolcanoSite v) {
            if (site.volcano != v) {
                site.init(model, v);
            }
            return site;
        }

        SimplexNoise noise(TerrainModel model) {
            long key = SeedMixer.derive(model.siteSeed(), SALT);
            if (!noiseValid || noiseKey != key) {
                if (NOISES.size() > 16) {
                    NOISES.clear();
                }
                noise = NOISES.computeIfAbsent(key, SimplexNoise::new);
                noiseKey = key;
                noiseValid = true;
            }
            return noise;
        }
    }
}
