package com.terrahaul.worldgen.terrain;

import static com.terrahaul.worldgen.noise.TMath.bump;
import static com.terrahaul.worldgen.noise.TMath.clamp;
import static com.terrahaul.worldgen.noise.TMath.clamp01;
import static com.terrahaul.worldgen.noise.TMath.lerp;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.Fractal;
import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;
import com.terrahaul.worldgen.noise.TMath;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Terrahaul terrain model: a deterministic function from a block column {@code (x, z)} to its
 * terrain height, water, landform classification and the climate parameters given to the vanilla
 * multi-noise biome source.
 * <p>
 * The world is assembled from layered fields at different scales:
 * <ol>
 *   <li>domain warping, so no shape follows a straight grid line;</li>
 *   <li>continentalness (oceans, shelves, coasts, inland), with archipelagos and mushroom isles;</li>
 *   <li>erosion, combined with long "belts" that create coherent mountain ranges;</li>
 *   <li>relief: mountains in several geological styles, foothills, hills, plateaus, windswept
 *       uplands, gentle plains and wetlands;</li>
 *   <li>volcanoes, canyons, coastal cliffs;</li>
 *   <li>carving by rivers (with valleys and floodplains) and lakes;</li>
 *   <li>climate: temperature cooled by altitude, humidity, and biome-consistent outputs.</li>
 * </ol>
 * Everything is two-dimensional and evaluated per column, which keeps silhouettes coherent, avoids
 * floating terrain and lets chunk generation cache the result for the whole column height.
 * The 3D overhang detail is layered on top by {@code TerrainDensityFunction}.
 * <p>
 * Thread safety: all noise objects are immutable; the per-column cache is thread-local and the
 * lake-site cache is a concurrent map, so one instance serves every chunk-generation thread.
 */
public final class TerrainModel {
    // continentalness profile: base ground height for a given continentalness
    private static final double[] PROFILE_C = {-1.00, -0.80, -0.60, -0.455, -0.30, -0.22, -0.19, -0.16, -0.13, -0.11, 0.03, 0.30, 0.60, 1.00};
    /** Lowest trench floor, well above the lava sea and bedrock at the world bottom. */
    private static final double MIN_TRENCH_FLOOR = -440.0;
    // deep ocean floors lie around Y -60..-160 (abyssal plains), ocean floors around Y -24..30
    private static final double[] PROFILE_H = {-160, -125, -70, -24, 30, 46, 51, 58.5, 63.6, 65.5, 71, 84, 100, 118};

    private static final int CACHE_BITS = 5;
    private static final int CACHE_SIZE = 1 << (CACHE_BITS * 2);
    private static final int CACHE_MASK = (1 << CACHE_BITS) - 1;

    private final long seed;
    private final TerrainSettings settings;
    private final int seaLevel;
    private final double hs;

    private final Fractal warpX;
    private final Fractal warpZ;
    private final Fractal warpX2;
    private final Fractal warpZ2;
    private final Fractal continent;
    private final Fractal archipelago;
    private final Fractal islands;
    private final Fractal erosionNoise;
    private final Fractal beltNoise;
    private final Fractal beltSegments;
    private final Fractal rangeIntensity;
    private final Fractal colossalNoise;
    private final Fractal styleA;
    private final Fractal styleB;
    private final Fractal alpineRidges;
    private final Fractal ancientDomes;
    private final Fractal mountainDetail;
    private final MountainRelief mountainRelief;
    /** Strength (0..1) of the colossal massif of each massif grid cell, by cell. */
    private final ConcurrentHashMap<Long, Double> massifSites = new ConcurrentHashMap<>();
    private final Fractal valleyNoise;
    private final Fractal meadowNoise;
    private final Fractal hillNoise;
    private final Fractal hillAmplitude;
    private final Fractal plateauNoise;
    private final Fractal plateauHeight;
    private final Fractal windsweptNoise;
    private final Fractal plainsNoise;
    private final Fractal plainsDetail;
    private final Fractal swampNoise;
    private final Fractal riverNoise;
    private final Fractal riverWarpX;
    private final Fractal riverWarpZ;
    private final Fractal riverPresence;
    private final Fractal greatRiver;
    private final Fractal streamPresence;
    private final Fractal canyonNoise;
    private final Fractal canyonRegion;
    private final Fractal canyonWidth;
    private final Fractal coastCliffNoise;
    private final Fractal fjordNoise;
    private final Fractal temperatureNoise;
    private final Fractal temperatureDetail;
    private final Fractal humidityNoise;
    private final Fractal variantNoise;
    private final Fractal strataNoise;
    private final SimplexNoise lakeShape;
    private final SimplexNoise[] detail3d = new SimplexNoise[8];
    private final long volcanoSeed;
    private final long lakeSeed;
    private final long greatLakeSeed;
    private final long pillarSeed;
    private final long geologySeed;
    private final long siteSeed;
    private final Fractal trenchNoise;
    private final Fractal trenchRegion;
    private final Fractal trailWarpX;
    private final Fractal trailWarpZ;
    private final Fractal trailWiggle;
    private final long trailSeed;
    private final long hornSeed;

    private final ThreadLocal<Scratch> scratch;
    private final ConcurrentHashMap<Long, LakeSite> lakeSites = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VolcanoSite> volcanoSites = new ConcurrentHashMap<>();
    private static final VolcanoSite NO_VOLCANO = new VolcanoSite();
    private final ConcurrentHashMap<Long, WaterfallSite> waterfallSites = new ConcurrentHashMap<>();
    private static final WaterfallSite NO_WATERFALL = new WaterfallSite();
    private final ConcurrentHashMap<Long, FormationSite> formationSites = new ConcurrentHashMap<>();
    private static final FormationSite NO_FORMATION = new FormationSite();

    public TerrainModel(long seed, TerrainSettings settings) {
        this.seed = seed;
        this.settings = settings;
        this.seaLevel = settings.seaLevel();
        this.hs = settings.horizontalScale();
        int salt = 0;
        this.warpX = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.5);
        this.warpZ = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.5);
        this.warpX2 = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.warpZ2 = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.continent = new Fractal(SeedMixer.derive(seed, ++salt), 6, 2.0, 0.45);
        this.archipelago = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.islands = new Fractal(SeedMixer.derive(seed, ++salt), 4, 2.1, 0.5);
        this.erosionNoise = new Fractal(SeedMixer.derive(seed, ++salt), 5, 2.0, 0.5);
        this.beltNoise = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.45);
        this.beltSegments = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.rangeIntensity = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.colossalNoise = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.5);
        this.styleA = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.styleB = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.alpineRidges = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.1, 0.45);
        this.ancientDomes = new Fractal(SeedMixer.derive(seed, ++salt), 5, 2.0, 0.45);
        this.mountainDetail = new Fractal(SeedMixer.derive(seed, ++salt), 5, 2.0, 0.5);
        this.mountainRelief = new MountainRelief(SeedMixer.derive(seed, 0x6D6F756E7461696EL));
        this.valleyNoise = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.45);
        this.meadowNoise = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.hillNoise = new Fractal(SeedMixer.derive(seed, ++salt), 5, 2.0, 0.5);
        this.hillAmplitude = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.plateauNoise = new Fractal(SeedMixer.derive(seed, ++salt), 4, 2.0, 0.5);
        this.plateauHeight = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.windsweptNoise = new Fractal(SeedMixer.derive(seed, ++salt), 4, 2.0, 0.5);
        this.plainsNoise = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.5);
        this.plainsDetail = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.swampNoise = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.5);
        this.riverNoise = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.42);
        this.riverWarpX = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.2, 0.45);
        this.riverWarpZ = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.2, 0.45);
        this.riverPresence = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.greatRiver = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.streamPresence = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.canyonNoise = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.45);
        this.canyonRegion = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.canyonWidth = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.coastCliffNoise = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.5);
        this.fjordNoise = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.45);
        this.temperatureNoise = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.45);
        this.temperatureDetail = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.humidityNoise = new Fractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.45);
        this.variantNoise = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.strataNoise = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.lakeShape = new SimplexNoise(SeedMixer.derive(seed, ++salt));
        for (int i = 0; i < detail3d.length; i++) {
            this.detail3d[i] = new SimplexNoise(SeedMixer.derive(seed, 1000 + i));
        }
        this.volcanoSeed = SeedMixer.derive(seed, ++salt);
        this.lakeSeed = SeedMixer.derive(seed, ++salt);
        this.greatLakeSeed = SeedMixer.derive(seed, ++salt);
        this.pillarSeed = SeedMixer.derive(seed, ++salt);
        this.geologySeed = SeedMixer.derive(seed, ++salt);
        this.siteSeed = SeedMixer.derive(seed, ++salt);
        this.trenchNoise = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.4);
        this.trenchRegion = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.trailWarpX = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.trailWarpZ = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.trailWiggle = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.trailSeed = SeedMixer.derive(seed, ++salt);
        this.hornSeed = SeedMixer.derive(seed, ++salt);
        this.scratch = ThreadLocal.withInitial(Scratch::new);
    }

    public long seed() {
        return seed;
    }

    public TerrainSettings settings() {
        return settings;
    }

    public int seaLevel() {
        return seaLevel;
    }

    /** 3D simplex noise channel {@code 0..3} used for overhangs, crags and sea caves. */
    /**
     * Whether a trail may be laid on this column: dry land above sea level, away from cliffs,
     * canyons, volcanoes, wetlands, beaches, steep mountains and deserts.
     */
    public static boolean trailGround(ColumnSample s, int seaLevel) {
        return s.height > seaLevel + 1.5 && s.waterLevel == ColumnSample.NO_WATER && s.river <= 0.0 && s.lake <= 0.0
            && s.riverValley < 0.9 && s.cliff < 0.25 && s.canyon < 0.15 && s.mountain < 0.55 && s.steepness < 0.5
            && s.volcano < 0.15 && s.swamp < 0.55 && s.beach < 0.4 && !(s.baseTemperature > 0.6 && s.humidity < -0.1);
    }

    // =========================================================================================
    // Mountains (section 5 of the column computation)
    // =========================================================================================

    /** Share of a range's height that lies below its valley floors (the crests rise above them). */
    private static final double MOUNTAIN_FLOOR = 0.16;
    /** Spacing of pyramidal horn peaks in ordinary ranges. */
    private static final double HORN_CELL = 1100.0;
    /** Spacing of colossal massifs (only those inside colossal regions rise). */
    private static final double MASSIF_CELL = 5600.0;
    /** Reach of a massif's arêtes as a fraction of {@link #MASSIF_CELL}. */
    private static final double MASSIF_REACH = 0.68;
    /** Offset of the massif grid's hash seed from the horns' one. */
    private static final long MASSIF_CHANNEL = 101L;
    /** Extra relief of mountain walls over ordinary ranges. */
    private static final double WALL_BOOST = 0.3;
    /** Greatest relief of the ridges and peaks on a massif's flanks (half-way up), as a share of its height. */
    private static final double MASSIF_RELIEF = 0.22;
    /** Spacing of the coarsest gullies and spurs on mountain flanks, and their greatest depth. */
    private static final double GULLY_CELL = 540.0;
    private static final double GULLY_DEPTH = 45.0;
    /** Median gradient of 3-octave fBm noise, in noise units per unit of its input. */
    private static final double NOISE_GRADIENT = 2.9;
    /** Step of the finite differences that give the gradient of the mountain relief. */
    private static final double FD = 24.0;
    /** Width of a mountain wall's face as a multiple of its height (a face of about 50 degrees). */
    private static final double WALL_FACE = 0.85;
    /** Height above the valley floors up to which horns rise; taller ranges carry them on their crests. */
    private static final double HORN_HEIGHT = 600.0;
    /** Widest side of a main valley's trough (see {@link #rangeShape}). */
    private static final double TROUGH_WIDTH = 420.0;
    /** Spacing of the lattice on which the blurred mountain mask is worked out (see {@link #blurredMask}). */
    private static final double MASK_LATTICE = 96.0;
    /** Size of the per-thread cache of the mask's lattice points (a power of two). */
    private static final int MASK_CACHE = 4096;
    /** Size of the per-thread cache of massif strengths (a power of two). */
    private static final int MASSIF_CACHE = 64;

    /**
     * Section 5 of the column computation: mountain ranges and colossal massifs. Returns the height
     * of the mountains above the base profile and leaves the weights that later sections and the
     * classification use in {@code mt}. It is a method of its own because the JIT stops compiling
     * {@code computeColumn} well once its bytecode approaches 8000 bytes.
     *
     * @param wx      x at the continental warp (where the large fields are sampled)
     * @param rx      x at the gentle warp of local relief
     * @param vRaw    the main valley field at {@code (rx, rz)}
     * @param beltRaw the mountain belt field at {@code (wx, wz)}
     * @param base    the base profile's height
     */
    private double mountains(double wx, double wz, double rx, double rz, double vRaw, double cEff, double rawC, double reliefLand,
                             double beltRaw, double beltSeg, double beltStrength, double base, MountainState mt) {
        double es = settings.erosionScale() * hs;
        double bs = es * 2.5;
        double ms = settings.mountainScale() * hs;
        double erosionLow = erosionNoise.fbm(wx / es, wz / es, 2) * 1.35;
        double rangeI = 0.5 + 0.5 * rangeIntensity.fbm(wx / (es * 2.6), wz / (es * 2.6));
        double colossalRegion = smoothstep(0.6, 0.84, 0.5 + 0.5 * colossalNoise.fbm(wx / (es * 3.3), wz / (es * 3.3)))
            * smoothstep(-0.02, 0.28, cEff);
        // relief of an ordinary range; colossal regions raise massifs on top of it (see below)
        double amp = settings.mountainHeight() * (0.32 + 0.95 * rangeI);
        // the mountain mask, blurred in proportion to the range's height (see blurredMask), and
        // its slope, which the gullies below follow on the flanks of a range
        double[] maskGrad = mt.maskGrad;
        double mountainE = blurredMask(wx, wz, mt);
        double maskGx = maskGrad[0];
        double maskGz = maskGrad[1];
        // mountains fade out over a few hundred blocks towards the coast (judged on the raw
        // continentalness, which island boosts do not compress), so a range ends in foothills
        // instead of rising from the beach as a 400-block wall
        double coastFade = reliefLand * smoothstep(-0.2, 0.12, rawC);
        double mountain = mountainE * coastFade;
        // colossal regions on mountain belts or ranges, where the massifs rise
        double colossal = colossalRegion * Math.max(beltStrength, mountainE);

        double gA = styleA.fbm(wx / (es * 1.7), wz / (es * 1.7));
        // mountain walls stand along the belt lines, on long stretches of them (tens of km)
        double wallU = 0.0;
        double wallNx = 0.0;
        double wallNz = 0.0;
        double wallSide = 0.0;
        // the wall as a landform reported to the classification: its crest and face only
        double wallFace = 0.0;
        double wallW = beltSeg * smoothstep(0.0, 0.25, styleB.fbm(wx / (es * 4.0) + 50.0, wz / (es * 4.0) - 20.0)) * coastFade;
        if (wallW > 0.0) {
            // signed distance from the belt line, and which side carries the face (it changes
            // over tens of kilometres)
            double beltGx = (beltNoise.fbm((wx + 8.0) / bs, wz / bs) - beltRaw) / 8.0;
            double beltGz = (beltNoise.fbm(wx / bs, (wz + 8.0) / bs) - beltRaw) / 8.0;
            double bScale = lineScale(beltRaw, Math.sqrt(beltGx * beltGx + beltGz * beltGz), bs);
            wallNx = beltGx * bScale;
            wallNz = beltGz * bScale;
            wallU = beltRaw * bScale;
            wallSide = smoothstep(-0.12, 0.12, styleB.fbm(wx / (es * 5.0) - 23.0, wz / (es * 5.0) + 61.0));
            // the wall's own landform reaches from the far end of its back slope to the foot of
            // its face and the foreland in front: its weight covers all of that, so neither face
            // nor slope is cut off where the weight ends. Across all of it the range keeps the
            // height it has on the belt line: the mask falls off within a few hundred blocks of
            // the line and would squeeze the wall into a loaf with two steep sides.
            double lineMask = smoothstep(-0.2, -0.82, clamp(erosionLow - 1.4 * beltSeg, -1.0, 1.0)) * coastFade;
            double height = amp * lineMask * (1.0 - MOUNTAIN_FLOOR) * (1.0 + WALL_BOOST);
            double front = WALL_FACE * height + 80.0;
            double back = 200.0 + 2.4 * height;
            // (judged on the field value at its typical scale, which never changes faster than
            // the distance itself)
            double bandU = beltRaw * bs / NOISE_GRADIENT;
            wallW *= lerp(wallSide, wallBand(bandU, front, back), wallBand(-bandU, front, back));
            wallFace = wallW * lerp(wallSide, wallCore(wallU, front, back), wallCore(-wallU, front, back));
            mountain = Math.max(mountain, wallW * lineMask);
        }
        // mountain walls are young, alpine ranges
        double alpineW = Math.max(wallW, smoothstep(-0.28, 0.22, gA + 0.7 * (rangeI - 0.5) + 1.2 * colossal));
        double ancientW = 1.0 - alpineW;

        double shape = 0.0;
        double meadow = 0.0;
        double mountainHeight = 0.0;
        // colossal massifs: a huge summit pyramid whose arêtes reach out 3000-4800 blocks,
        // added on top of the range, so subsidiary peaks and ridges of the range stand on its
        // flanks and it rises from a base far wider than it is high
        double massif = massif(rx, rz, mt, true);
        if (mountain > 0.002 || massif > 0.0) {
            // massifs are young, alpine mountains
            alpineW = Math.max(alpineW, smoothstep(0.0, 0.3, massif));
            ancientW = 1.0 - alpineW;
            double ampEff = amp * (alpineW + ancientW * 0.62);
            double m13 = Math.pow(mountain, 1.3);
            // valley floors rise with the range; the crests stand `relief` blocks above them,
            // higher along mountain walls, and higher still on the flanks of a massif, which
            // thereby carry subsidiary peaks, ridges and valleys instead of a smooth cone. High
            // on a massif the range gives way to the massif's own summit pyramid: peaks standing
            // on its steep upper slopes would add their faces to it, as walls of 80 degrees.
            double floorH = ampEff * (0.07 * Math.pow(mountain, 0.65) + MOUNTAIN_FLOOR * m13);
            double relief = ampEff * m13 * (1.0 - MOUNTAIN_FLOOR) * (1.0 + WALL_BOOST * wallW) * (1.0 - 0.8 * smoothstep(0.15, 0.6, mt.massifRise))
                + MASSIF_RELIEF * settings.colossalHeight() * coastFade * 4.0 * massif * (1.0 - mt.massifRise);
            // the main valleys follow the zero lines of the valley field, as the streams do (see
            // lineScale for the distance to them). Gradients are kept for the samples of the
            // neighbouring columns.
            double vs = ms * 1.8;
            double vgx = (valleyNoise.fbm((rx + 8.0) / vs, rz / vs) - vRaw) / 8.0;
            double vgz = (valleyNoise.fbm(rx / vs, (rz + 8.0) / vs) - vRaw) / 8.0;
            double vScale = lineScale(vRaw, Math.sqrt(vgx * vgx + vgz * vgz), vs);
            vgx *= vScale;
            vgz *= vScale;
            double valley = vRaw * vScale;
            meadow = smoothstep(0.22, 0.55, meadowNoise.fbm(rx / (ms * 2.2), rz / (ms * 2.2)))
                * (1.0 - 0.6 * alpineW) * smoothstep(0.25, 0.6, mountain) * (1.0 - colossal);
            double massifScale = settings.colossalHeight() * coastFade;
            double s0 = rangeShape(rx, rz, Math.abs(valley), wallU, wallSide, alpineW, ancientW, wallW, relief, meadow, mt, true);
            double uneroded = floorH + relief * s0 + massifScale * massif;
            // gullies and spurs down every flank; some slopes are more deeply furrowed than others,
            // the floors of the main valleys and meadows stay smooth (the flank of a massif under
            // a valley floor would otherwise furrow it lengthwise), and so, half, do the glaciated
            // slopes high on the colossal massifs
            double furrows = 0.55 + 0.9 * (0.5 + 0.5 * mountainDetail.fbm(rx / (ms * 1.2), rz / (ms * 1.2), 2));
            // (on the face of a mountain wall they are deeper: buttresses and couloirs)
            double gullyDepth = Math.min(GULLY_DEPTH * (1.0 + 1.5 * wallW), (0.1 + 0.05 * wallW) * (relief + massifScale * massif)) * furrows
                * smoothstep(0.02, 0.25, Math.max(s0, massif)) * (1.0 - 0.8 * meadow) * smoothstep(0.0, 0.15, troughT(Math.abs(valley), relief))
                * (1.0 - 0.5 * smoothstep(1000.0, 1500.0, base + uneroded));
            double gully = 0.0;
            if (gullyDepth > 0.5) {
                // the uneroded relief (range and massif) at the corners of a triangle of radius FD
                // around the column (z+, then the two corners towards z-)
                double[] relief3 = mt.relief3;
                for (int k = 0; k < 3; k++) {
                    double ox = k == 0 ? 0.0 : k == 1 ? -0.866 * FD : 0.866 * FD;
                    double oz = k == 0 ? FD : -0.5 * FD;
                    double sk = rangeShape(rx + ox, rz + oz, Math.abs(valley + ox * vgx + oz * vgz),
                        wallU + ox * wallNx + oz * wallNz, wallSide, alpineW, ancientW, wallW, relief, meadow, mt, false);
                    relief3[k] = relief * sk + (massif > 0.0 ? massifScale * massif(rx + ox, rz + oz, mt, false) : 0.0);
                }
                // gradient of the uneroded mountains in blocks per block (the plane through the
                // corners), and how consistent it is across the triangle: the corners' mean lies
                // below the column's own value on crests and summits and above it in valley
                // bottoms, where the fall line turns, and matches it on an even flank. The gullies
                // follow this smoothed gradient (an exact one flips across a crest and would leave
                // seams there) and fade out where it is not consistent, so crests stay sharp and
                // gullies head just below them.
                double here = relief * s0 + massifScale * massif;
                double gx = (relief3[2] - relief3[1]) * 0.57735 / FD;
                double gz = (relief3[0] - 0.5 * (relief3[1] + relief3[2])) / (1.5 * FD);
                double bend = Math.abs((relief3[0] + relief3[1] + relief3[2]) / 3.0 - here);
                double coherence = 1.0 - bend / (0.8 * FD * Math.sqrt(gx * gx + gz * gz) + 1.0E-6);
                double dMask = 1.3 * (ampEff * MOUNTAIN_FLOOR * Math.pow(mountain, 0.3) + relief * s0 / Math.max(mountain, 0.05)) * coastFade;
                double[] d = mt.fall;
                d[0] = gx + dMask * maskGx;
                d[1] = gz + dMask * maskGz;
                gullyDepth *= smoothstep(0.05, 0.95, coherence);
                if (gullyDepth > 0.5) {
                    gully = mountainRelief.gullies(rx, rz, d, gullyDepth, GULLY_CELL * hs, 3);
                }
            }
            mountainHeight = uneroded + gully;
            shape = clamp01(Math.max(s0, massif) + gully / Math.max(relief + massifScale * massif, 1.0));
            // ground that stands high above its valley floors counts as mountain whatever the
            // mask says (a massif reaches far beyond it), so rivers, canyons, plateaus and hills
            // keep off tall flanks instead of cutting slots hundreds of blocks deep into them
            mountain = Math.max(mountain, smoothstep(40.0, 300.0, mountainHeight));
        }
        mt.mask = mountainE;
        mt.weight = mountain;
        mt.rangeI = rangeI;
        mt.alpine = alpineW;
        mt.ancient = ancientW;
        mt.wall = wallFace;
        mt.shape = shape;
        mt.meadow = meadow;
        return mountainHeight;
    }

    /** What {@link #mountains} hands back besides the height (one instance per thread). */
    private static final class MountainState {
        /** The blurred mountain mask before the coast fade (fjords follow it). */
        double mask;
        /** How mountainous the column is, 0..1. */
        double weight;
        /** Intensity of the range (0..1), which also raises coastal cliffs and windswept hills. */
        double rangeI;
        double alpine;
        double ancient;
        /** The crest and face of a mountain wall (its landform reaches further, see {@link #wallCore}). */
        double wall;
        /** Relative elevation within the range: 0 on valley floors, 1 on the highest crests. */
        double shape;
        double meadow;
        /** How far up its massif the column lies: 0 at the foot, 1 at the summit (see {@link #massif}). */
        double massifRise;
        /** Uneroded mountain relief at the three finite-difference neighbours of the column. */
        final double[] relief3 = new double[3];
        /** Gradient of the uneroded mountains, which the gullies follow (see {@link MountainRelief#gullies}). */
        final double[] fall = new double[2];
        /** Horn peaks near the column, on the ordinary and the twice as coarse grid (see {@link #rangeShape}). */
        final MountainRelief.Sites horns = new MountainRelief.Sites();
        final MountainRelief.Sites bigHorns = new MountainRelief.Sites();
        /** Colossal massifs near the column (see {@link #massif}). */
        final MountainRelief.Sites massifs = new MountainRelief.Sites();
        /** The gradient of the blurred mountain mask (see {@link #blurredMask}). */
        final double[] maskGrad = new double[2];
        final double[] splineWeights = new double[12];
        /** Direct-mapped cache of the blurred mask's lattice points (see {@link #latticeMask}). */
        final long[] maskKeys = new long[MASK_CACHE];
        final double[] maskValues = new double[MASK_CACHE];
        final boolean[] maskValid = new boolean[MASK_CACHE];
        /** Direct-mapped cache of massif strengths by massif grid cell (see {@link #massifStrength}). */
        final long[] massifKeys = new long[MASSIF_CACHE];
        final double[] massifValues = new double[MASSIF_CACHE];
        final boolean[] massifValid = new boolean[MASSIF_CACHE];
    }

    /**
     * Relative elevation (0 at the valley floors, 1 on the highest crests) of an ordinary mountain
     * range at a gently warped position, before gullies and massifs:
     * <ul>
     *   <li>alpine ranges: a network of V-shaped crests (ridged noise raised to a power, so the
     *       flanks are concave) that rise towards pyramidal horns (see
     *       {@link MountainRelief#peaks}) and sag into cols between them. Both keep their slopes
     *       whatever the relief: the crests grow further apart in tall ranges, and horns are at
     *       most {@link #HORN_HEIGHT} high (a second, twice as coarse grid of horns stands on tall
     *       ranges);</li>
     *   <li>ancient ranges: rounded, worn-down domes;</li>
     *   <li>mountain walls: a continuous crest along the belt line with a face of about 50 degrees
     *       on one side and a long back slope on the other (see {@link #wallProfile}). The crest
     *       rises into the summits of the range and sags into cols between them;</li>
     *   <li>main valleys: troughs along the valley lines whose sides widen with the relief, so a
     *       tall range is cut by broad valleys rather than slots;</li>
     *   <li>high meadows: shelves of gentle ground at about a third of the relief.</li>
     * </ul>
     *
     * @param valley distance to the nearest main valley line, in blocks
     * @param wallU  signed distance from the mountain wall's crest line, in blocks
     * @param relief height of the crests above the valley floors, in blocks
     */
    private double rangeShape(double x, double z, double valley, double wallU, double wallSide, double alpineW, double ancientW,
                              double wallW, double relief, double meadow, MountainState mt, boolean collect) {
        double ms = settings.mountainScale() * hs;
        double s = 0.0;
        double peaks = 0.0;
        if (alpineW > 0.01) {
            // sharpness 1: V-shaped crests and rounded valley bottoms, values spread evenly
            double wide = smoothstep(380.0, 700.0, relief);
            double ridges = wide < 1.0 ? alpineRidges.ridged(x / (ms * 1.7), z / (ms * 1.7), 1.0) : 0.0;
            if (wide > 0.0) {
                ridges = lerp(wide, ridges, alpineRidges.ridged(x / (ms * 3.1) + 71.3, z / (ms * 3.1) - 33.1, 1.0));
            }
            // horns are about 1.7 times as wide as high: faces of about 60 degrees at the top that
            // ease out towards the foot. Foothills get none (they would stand on the plain as
            // lone cones).
            // (the column's finite-difference neighbours reuse the peaks collected for it)
            double hornH = Math.min(relief, HORN_HEIGHT);
            double cell = HORN_CELL * hs;
            double reach = clamp(1.7 * hornH / cell, 0.35, 0.9) * cell;
            if (collect) {
                MountainRelief.collect(mt.horns, x, z, cell, 0.72, reach * MountainRelief.MAX_SPOKE + FD + 1.0, hornSeed, 0.5);
            }
            double horns = MountainRelief.peaks(mt.horns, x, z, reach, 2.0) * hornH * smoothstep(100.0, 300.0, relief);
            if (relief > HORN_HEIGHT) {
                double bigH = Math.min(relief, 2.0 * HORN_HEIGHT);
                double bigReach = clamp(0.85 * bigH / cell, 0.35, 0.9) * 2.0 * cell;
                if (collect) {
                    MountainRelief.collect(mt.bigHorns, x, z, 2.0 * cell, 0.72, bigReach * MountainRelief.MAX_SPOKE + FD + 1.0, hornSeed + 7L, 0.5);
                }
                double big = MountainRelief.peaks(mt.bigHorns, x, z, bigReach, 2.0) * bigH;
                horns = Math.max(horns, big * smoothstep(HORN_HEIGHT, 1.3 * HORN_HEIGHT, relief));
            }
            horns /= Math.max(relief, 1.0);
            // the crest network fills the whole relief, valley floors to ridge tops; the power
            // makes the flanks concave
            double crest = Math.pow(clamp01((ridges - 0.12) / 0.74), 1.3) * 0.88 * (0.66 + 0.34 * Math.min(1.0, horns * 1.6));
            peaks = smoothMax(crest, horns, 0.08);
            s = alpineW * peaks;
        }
        if (ancientW > 0.01) {
            double domes = 0.5 + 0.5 * ancientDomes.fbm(x / (ms * 1.5), z / (ms * 1.5), 3);
            s += ancientW * smoothstep(0.08, 0.92, domes) * 0.9;
        }
        if (wallW > 0.01) {
            // one continuous crest along the belt line, which carries the summits of the range and
            // sags into cols between them; in front of the face the range drops to a foreland, so
            // the face stands above it as one long escarpment
            double crestH = 0.62 + 0.38 * peaks;
            double front = WALL_FACE * crestH * relief + 80.0;
            double back = 200.0 + 2.4 * relief;
            double wall = lerp(wallSide, wallProfile(wallU, front, back), wallProfile(-wallU, front, back)) * crestH;
            double fore = lerp(wallSide, smoothstep(0.0, 1.5 * front, wallU), smoothstep(0.0, 1.5 * front, -wallU));
            s = lerp(wallW, s, Math.max(wall, s * (1.0 - 0.6 * fore)));
        }
        // main valleys: troughs with a flat floor and sides that are steepest at the bottom,
        // as wide as the relief they cut through. They only cut what rises above their sides,
        // so summits beside a valley keep their shape instead of being flattened into domes.
        // The valley lines lie only some 300-1100 blocks apart, so the sides are at most
        // TROUGH_WIDTH wide; in taller ranges the troughs are shallower instead of steeper (cut
        // to the full depth, they capped every summit between them at half the relief).
        double t = troughT(valley, relief);
        if (t < 1.0) {
            double depth = 0.96 * TROUGH_WIDTH / Math.max(TROUGH_WIDTH, 80.0 + 1.3 * relief);
            double trough = 1.0 - depth * Math.pow(1.0 - t, 1.5);
            s = smoothMin(s, trough, 0.06);
        }
        s = lerp(meadow * 0.85, s, 0.3);
        return clamp01(s);
    }

    /**
     * Colossal massifs (0..1 of the colossal height): huge pyramids on a jittered grid of
     * {@link #MASSIF_CELL} blocks, each with long arêtes reaching out 3000-4800 blocks (see
     * {@link MountainRelief#peaks}); between the arêtes the radius drops to about half, so valleys
     * radiate from the summit. The profile is concave: some 50 degrees below the summit, under 30
     * half-way down. Whether and how high a massif rises is decided once, around its centre, from
     * the colossal region and the mountain belts there, so the whole massif keeps its own smooth
     * shape instead of following the mask around it. Leaves in {@link MountainState#massifRise}
     * how far up the highest massif here the column lies.
     */
    private double massif(double x, double z, MountainState mt, boolean collect) {
        double cell = MASSIF_CELL * hs;
        double reach = cell * MASSIF_REACH;
        MountainRelief.Sites sites = mt.massifs;
        if (collect) {
            // (the column's finite-difference neighbours reuse the massifs collected for it)
            sites.clear();
            double range = reach * MountainRelief.MAX_SPOKE + FD + 1.0;
            int cx = (int) Math.floor(x / cell);
            int cz = (int) Math.floor(z / cell);
            // a site lies 0.2-0.8 cells into its cell, so a neighbouring column of cells can only
            // be within reach from the near side of this one
            double fx = x / cell - cx;
            double fz = z / cell - cz;
            double reachCells = range / cell;
            for (int dx = fx < reachCells - 0.2 ? -1 : 0; dx <= (fx > 1.2 - reachCells ? 1 : 0); dx++) {
                for (int dz = fz < reachCells - 0.2 ? -1 : 0; dz <= (fz > 1.2 - reachCells ? 1 : 0); dz++) {
                    long h = SeedMixer.hash2(hornSeed + MASSIF_CHANNEL, cx + dx, cz + dz);
                    double sx = MountainRelief.siteX(h, cx + dx, cell);
                    double sz = MountainRelief.siteZ(h, cz + dz, cell);
                    double d2 = (x - sx) * (x - sx) + (z - sz) * (z - sz);
                    if (d2 < range * range) {
                        double strength = massifStrength(cx + dx, cz + dz, h, cell, mt);
                        if (strength > 0.0) {
                            sites.put(sx, sz, h, d2, 0.45, strength);
                        }
                    }
                }
            }
        }
        double value = MountainRelief.peaks(sites, x, z, reach, 2.0);
        mt.massifRise = sites.rise;
        return value;
    }

    /**
     * Strength of the massif of one grid cell: the colossal region around its centre, on a
     * mountain belt or range, away from the coast (the same fields as sections 1-3 of the column
     * computation, without islands). Cached per thread, since every column within reach asks for
     * it several times.
     */
    private double massifStrength(int cx, int cz, long h, double cell, MountainState mt) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        int slot = (int) (SeedMixer.mix(key) & (MASSIF_CACHE - 1));
        if (mt.massifValid[slot] && mt.massifKeys[slot] == key) {
            return mt.massifValues[slot];
        }
        double es = settings.erosionScale() * hs;
        double bs = es * 2.5;
        double cs = settings.continentScale() * hs;
        double strength = 0.0;
        // the centre and four points a quarter of a cell around it: a massif may also stand
        // beside a range, its arêtes reaching into it
        for (int k = 0; k < 5; k++) {
            double x = MountainRelief.siteX(h, cx, cell) + (k == 1 ? 0.25 : k == 2 ? -0.25 : 0.0) * cell;
            double z = MountainRelief.siteZ(h, cz, cell) + (k == 3 ? 0.25 : k == 4 ? -0.25 : 0.0) * cell;
            double wx = x + 320.0 * warpX.fbm(x / (2600.0 * hs), z / (2600.0 * hs))
                + 24.0 * warpX2.fbm(x / (420.0 * hs), z / (420.0 * hs));
            double wz = z + 320.0 * warpZ.fbm(x / (2600.0 * hs) + 31.7, z / (2600.0 * hs) - 12.4)
                + 24.0 * warpZ2.fbm(x / (420.0 * hs) - 7.1, z / (420.0 * hs) + 3.3);
            double c = continent.fbm(wx / cs, wz / cs) * 2.4 + 0.02;
            double belt = smoothstep(0.0, 1.0, 1.0 - Math.abs(beltNoise.fbm(wx / bs, wz / bs)) * 4.5)
                * smoothstep(-0.25, 0.2, beltSegments.fbm(wx / (bs * 0.8), wz / (bs * 0.8)));
            double region = smoothstep(0.55, 0.8, 0.5 + 0.5 * colossalNoise.fbm(wx / (es * 3.3), wz / (es * 3.3)))
                * smoothstep(-0.02, 0.28, c);
            strength = Math.max(strength, region * Math.max(belt, mountainMask(wx, wz, es, 1.0)) * smoothstep(-0.2, 0.12, c));
        }
        // weak sites would only scatter middling pyramids over the lowlands
        strength = smoothstep(0.1, 0.6, strength);
        mt.massifKeys[slot] = key;
        mt.massifValues[slot] = strength;
        mt.massifValid[slot] = true;
        return strength;
    }

    /**
     * Position across the trough of a main valley at {@code valley} blocks from its line: 0 on its
     * flat floor, 1 at the top of its sides, which are as wide as the relief they cut through, up
     * to {@link #TROUGH_WIDTH}.
     */
    private static double troughT(double valley, double relief) {
        return clamp01((valley - (20.0 + 0.04 * relief)) / Math.min(80.0 + 1.3 * relief, TROUGH_WIDTH));
    }

    /** Polynomial smooth maximum: like {@code max(a, b)} but rounded over a width of {@code k}. */
    private static double smoothMax(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0.0) / k;
        return Math.max(a, b) + h * h * k * 0.25;
    }

    /** Polynomial smooth minimum: like {@code min(a, b)} but rounded over a width of {@code k}. */
    private static double smoothMin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0.0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    /**
     * Cross-section of a mountain wall around its crest line ({@code u = 0}): a concave face
     * {@code front} blocks wide on the positive side, about 50 degrees on average and 60-65
     * just below the crest, and a long back slope on the other side. The gullies later cut buttresses
     * and couloirs into the face.
     */
    private static double wallProfile(double u, double front, double back) {
        if (u >= 0.0) {
            return u < front ? Math.pow(1.0 - u / front, 1.6) : 0.0;
        }
        return -u < back ? Math.pow(1.0 + u / back, 1.2) : 0.0;
    }

    /**
     * Blocks per unit of a noise field (of scale {@code scale}, value {@code v}, gradient
     * {@code grad}) near one of its zero lines, which main valleys and mountain walls follow: the
     * distance to the line is the value times this. Near the line it is the inverse gradient, but
     * never more than the inverse of the median gradient: the plain estimate {@code |v| / |grad v|}
     * jumps by hundreds of blocks within a few dozen where the gradient is small (near saddles of
     * the noise). Further out, where the gradient changes more than the value, the estimate turns
     * into the value at the median scale, which never grows several times faster than the
     * distance (and squeezed valley sides and wall slopes into cliffs where it did).
     */
    private static double lineScale(double v, double grad, double scale) {
        double median = scale / NOISE_GRADIENT;
        return lerp(smoothstep(0.08, 0.25, Math.abs(v)), 1.0 / Math.max(grad, 1.0 / median), median);
    }

    /**
     * Weight of a mountain wall's landform across it: 1 from its back slope ({@code u < 0}, with
     * {@code back} blocks of slope) to the foot of its face ({@code front} blocks wide), fading
     * out over the foreland in front and the far end of the back slope.
     */
    private static double wallBand(double u, double front, double back) {
        return u >= 0.0 ? 1.0 - smoothstep(front + 150.0, front + 900.0, u) : 1.0 - smoothstep(0.6 * back, back, -u);
    }

    /** The crest and face of a mountain wall within its band (see {@link #wallBand}). */
    private static double wallCore(double u, double front, double back) {
        return u >= 0.0 ? 1.0 - smoothstep(0.6 * front, 1.1 * front, u) : 1.0 - smoothstep(0.1 * back, 0.3 * back, -u);
    }

    /**
     * The mountain mask at warped coordinates, blurred over a distance that grows with the range's
     * height: unblurred, a range could rise from lowland to its full height within 200 blocks, as
     * a sheer, bullet-shaped wall, and the blur gives tall mountains proportionally broad
     * foothills. Its gradient (per block) is left in {@code mt.maskGrad}.
     * <p>
     * The blurred mask is a smooth field of position alone, so it is worked out on a lattice of
     * {@link #MASK_LATTICE} blocks (see {@link #latticeMask}), cached per thread, and smoothed
     * between the lattice points with a quadratic B-spline, which leaves no kinks in the slopes
     * and needs only the nine lattice points around a column, so that sparse sampling (the finder)
     * stays cheap as well.
     */
    private double blurredMask(double wx, double wz, MountainState mt) {
        double fx = wx / MASK_LATTICE + 0.5;
        double fz = wz / MASK_LATTICE + 0.5;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        double[] w = mt.splineWeights;
        quadraticBSpline(fx - ix, w, 0);
        quadraticBSpline(fz - iz, w, 6);
        double value = 0.0;
        double gx = 0.0;
        double gz = 0.0;
        for (int b = 0; b < 3; b++) {
            double row = 0.0;
            double rowDx = 0.0;
            for (int a = 0; a < 3; a++) {
                double v = latticeMask(ix - 1 + a, iz - 1 + b, mt);
                row += w[a] * v;
                rowDx += w[3 + a] * v;
            }
            value += w[6 + b] * row;
            gx += w[6 + b] * rowDx;
            gz += w[9 + b] * row;
        }
        mt.maskGrad[0] = gx / MASK_LATTICE;
        mt.maskGrad[1] = gz / MASK_LATTICE;
        return clamp01(value);
    }

    /**
     * Quadratic B-spline weights of the three lattice points around the nearest one, {@code t}
     * (0..1) measured from half a cell before it, then their derivatives.
     */
    private static void quadraticBSpline(double t, double[] w, int offset) {
        double s = 1.0 - t;
        w[offset] = 0.5 * s * s;
        w[offset + 1] = 0.5 + t * s;
        w[offset + 2] = 0.5 * t * t;
        w[offset + 3] = -s;
        w[offset + 4] = 1.0 - 2.0 * t;
        w[offset + 5] = t;
    }

    /**
     * The blurred mountain mask at lattice point {@code (i, j)} (see {@link #blurredMask}): the
     * mask at the point and on a ring around it, as far out as the range there is high (more for
     * colossal regions). A belt's share of the mask is judged on the distance from its line (see
     * {@link #mountainMask}), which needs the gradient of the belt field.
     */
    private double latticeMask(int i, int j, MountainState mt) {
        long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        int slot = (int) (SeedMixer.mix(key) & (MASK_CACHE - 1));
        if (mt.maskValid[slot] && mt.maskKeys[slot] == key) {
            return mt.maskValues[slot];
        }
        double wx = i * MASK_LATTICE;
        double wz = j * MASK_LATTICE;
        double es = settings.erosionScale() * hs;
        double bs = es * 2.5;
        double belt = beltNoise.fbm(wx / bs, wz / bs, 2);
        double gx = beltNoise.fbm((wx + 8.0) / bs, wz / bs, 2) - belt;
        double gz = beltNoise.fbm(wx / bs, (wz + 8.0) / bs, 2) - belt;
        double widen = Math.min(1.0, NOISE_GRADIENT / bs * 8.0 / Math.max(1.0E-9, Math.sqrt(gx * gx + gz * gz)));
        double rangeI = 0.5 + 0.5 * rangeIntensity.fbm(wx / (es * 2.6), wz / (es * 2.6));
        double colossal = smoothstep(0.6, 0.84, 0.5 + 0.5 * colossalNoise.fbm(wx / (es * 3.3), wz / (es * 3.3)));
        double r = (120.0 + 0.3 * (settings.mountainHeight() * (0.32 + 0.95 * rangeI) + settings.colossalHeight() * colossal)) * hs;
        double rd = r * 0.7071;
        double sum = mountainMask(wx, wz, es, widen)
            + mountainMask(wx + r, wz, es, widen) + mountainMask(wx - r, wz, es, widen)
            + mountainMask(wx, wz + r, es, widen) + mountainMask(wx, wz - r, es, widen)
            + mountainMask(wx + rd, wz + rd, es, widen) + mountainMask(wx - rd, wz + rd, es, widen)
            + mountainMask(wx + rd, wz - rd, es, widen) + mountainMask(wx - rd, wz - rd, es, widen);
        double value = sum / 9.0;
        mt.maskKeys[slot] = key;
        mt.maskValues[slot] = value;
        mt.maskValid[slot] = true;
        return value;
    }

    /**
     * The unblurred mountain mask (0..1) from erosion and mountain belts at warped coordinates.
     * A belt's share follows the belt value scaled by {@code widen} (at most 1): where the belt
     * field is steeper than its median, the plain value would squeeze the belt into a ridge a few
     * hundred blocks wide that rises within a few dozen; scaled by the median over the actual
     * gradient, every belt is at least as wide as a typical one.
     */
    private double mountainMask(double wx, double wz, double es, double widen) {
        double e = erosionNoise.fbm(wx / es, wz / es, 2) * 1.35;
        double bs = es * 2.5;
        // (two octaves of the belt field: the blur makes up for the missing fine one)
        double beltLine = 1.0 - Math.abs(beltNoise.fbm(wx / bs, wz / bs, 2)) * 4.5 * widen;
        double belt = smoothstep(0.0, 1.0, beltLine) * smoothstep(-0.25, 0.2, beltSegments.fbm(wx / (bs * 0.8), wz / (bs * 0.8)));
        return smoothstep(-0.2, -0.82, clamp(e - 1.4 * belt, -1.0, 1.0));
    }

    /** Spacing of the trail network's Voronoi cells. */
    public static final double TRAIL_CELL = 288.0;

    /**
     * Distance in blocks from (x, z) to the centre line of the nearest trail, or a large value
     * where there is none. Trails follow the edges of a large, domain-warped Voronoi diagram;
     * about 60% of its edges are kept, so trails wind across the land, meet at junctions and end
     * in the open. Whether a trail is actually laid also depends on the ground (see the surface
     * rule): never through water, over cliffs or up steep mountains.
     */
    public double trailDistance(int x, int z) {
        double px = x + 40.0 * trailWarpX.fbm(x / (170.0 * hs), z / (170.0 * hs)) + 3.0 * trailWiggle.fbm(x / 23.0, z / 23.0);
        double pz = z + 40.0 * trailWarpZ.fbm(x / (170.0 * hs) + 9.1, z / (170.0 * hs) - 4.7) + 3.0 * trailWiggle.fbm(x / 23.0 + 50.0, z / 23.0 - 31.0);
        double cell = TRAIL_CELL * hs;
        int cx = (int) Math.floor(px / cell);
        int cz = (int) Math.floor(pz / cell);
        double d1 = Double.MAX_VALUE;
        double d2 = Double.MAX_VALUE;
        double f1x = 0.0;
        double f1z = 0.0;
        double f2x = 0.0;
        double f2z = 0.0;
        long id1 = 0L;
        long id2 = 0L;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long id = SeedMixer.hash2(trailSeed, cx + dx, cz + dz);
                double fx = (cx + dx + 0.15 + 0.7 * SeedMixer.unit(id)) * cell;
                double fz = (cz + dz + 0.15 + 0.7 * SeedMixer.unit(SeedMixer.mix(id))) * cell;
                double d = (px - fx) * (px - fx) + (pz - fz) * (pz - fz);
                if (d < d1) {
                    d2 = d1;
                    f2x = f1x;
                    f2z = f1z;
                    id2 = id1;
                    d1 = d;
                    f1x = fx;
                    f1z = fz;
                    id1 = id;
                } else if (d < d2) {
                    d2 = d;
                    f2x = fx;
                    f2z = fz;
                    id2 = id;
                }
            }
        }
        long edge = SeedMixer.mix(Math.min(id1, id2) * 31L + Math.max(id1, id2));
        if (SeedMixer.unit(edge) > 0.6) {
            return 1.0E9;
        }
        double ex = f2x - f1x;
        double ez = f2z - f1z;
        double len = Math.sqrt(ex * ex + ez * ez);
        if (len < 1.0E-6) {
            return 1.0E9;
        }
        double mx = (f1x + f2x) * 0.5;
        double mz = (f1z + f2z) * 0.5;
        return Math.abs(((px - mx) * ex + (pz - mz) * ez) / len);
    }

    public double detail3(int channel, double x, double y, double z) {
        return detail3d[channel].noise3(x, y, z);
    }

    /** Seed for deterministic decoration sites (waterfalls, arches, ...). */
    public long siteSeed() {
        return siteSeed;
    }

    // =========================================================================================
    // Public sampling API
    // =========================================================================================

    /**
     * Samples the column at {@code (x, z)} using the thread-local cache. The returned instance is
     * owned by the cache and stays valid only until the next sampling call on the same thread.
     */
    public ColumnSample sample(int x, int z) {
        Scratch sc = scratch.get();
        int idx = ((x >> 2) & CACHE_MASK) | (((z >> 2) & CACHE_MASK) << CACHE_BITS);
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        ColumnSample cached = sc.cache[idx];
        if (sc.cacheKeys[idx] == key && cached.x == x && cached.z == z && sc.cacheValid[idx]) {
            return cached;
        }
        computeColumn(x, z, cached, true, sc);
        sc.cacheKeys[idx] = key;
        sc.cacheValid[idx] = true;
        return cached;
    }

    /** Samples into a caller-owned instance, bypassing the cache. */
    public void sampleInto(int x, int z, ColumnSample out) {
        computeColumn(x, z, out, true, scratch.get());
    }

    /** Caves and carvers keep at least this many blocks below the surface of nearby surface water. */
    public static final int WATER_GUARD = 14;

    /**
     * Highest planned water surface (lakes, elevated rivers, crater lakes) within about 12 blocks
     * of {@code (x, z)}, or {@link ColumnSample#NO_WATER}.
     */
    public double nearbyWaterLevel(int x, int z) {
        double best = ColumnSample.NO_WATER;
        int bx = x & ~3;
        int bz = z & ~3;
        for (int dx = -12; dx <= 12; dx += 12) {
            for (int dz = -12; dz <= 12; dz += 12) {
                double w = sample(bx + dx, bz + dz).waterLevel;
                if (w != ColumnSample.NO_WATER && w > best) {
                    best = w;
                }
            }
        }
        return best;
    }

    /**
     * Terrain height at block resolution, interpolated between the 4-block grid corners exactly
     * like the noise chunk interpolates the density function, so it matches the generated ground.
     */
    public double interpolatedHeight(int x, int z) {
        int x0 = x & ~3;
        int z0 = z & ~3;
        double fx = (x - x0) / 4.0;
        double fz = (z - z0) / 4.0;
        double h00 = sample(x0, z0).height;
        double h10 = sample(x0 + 4, z0).height;
        double h01 = sample(x0, z0 + 4).height;
        double h11 = sample(x0 + 4, z0 + 4).height;
        return lerp(fz, lerp(fx, h00, h10), lerp(fx, h01, h11));
    }

    /** Preliminary surface level used by aquifers and surface rules (see NoiseChunkMixin). */
    public int preliminarySurface(int x, int z) {
        return (int) Math.floor(sample(x, z).height) - 3;
    }

    /**
     * Highest terrain surface (including carving tolerance) over the given block rectangle,
     * sampled at the interpolation grid. Used to skip filling air far above the terrain.
     */
    public double maxHeightInArea(int minX, int minZ, int maxX, int maxZ) {
        double max = -Double.MAX_VALUE;
        for (int x = minX & ~3; x <= maxX + 3; x += 4) {
            for (int z = minZ & ~3; z <= maxZ + 3; z += 4) {
                ColumnSample s = sample(x, z);
                max = Math.max(max, Math.max(s.height, s.waterLevel));
            }
        }
        return max;
    }

    // =========================================================================================
    // Column computation
    // =========================================================================================

    private void computeColumn(int x, int z, ColumnSample out, boolean withLakes, Scratch sc) {
        out.x = x;
        out.z = z;
        double[] d = sc.deriv;
        final double sl = seaLevel;

        // ---- 1. domain warp -------------------------------------------------------------------
        double wx = x + 320.0 * warpX.fbm(x / (2600.0 * hs), z / (2600.0 * hs))
            + 24.0 * warpX2.fbm(x / (420.0 * hs), z / (420.0 * hs));
        double wz = z + 320.0 * warpZ.fbm(x / (2600.0 * hs) + 31.7, z / (2600.0 * hs) - 12.4)
            + 24.0 * warpZ2.fbm(x / (420.0 * hs) - 7.1, z / (420.0 * hs) + 3.3);

        // ---- 2. continentalness ---------------------------------------------------------------
        double cs = settings.continentScale() * hs;
        double c = continent.fbm(wx / cs, wz / cs) * 2.4 + 0.02;
        if (c < -0.45) {
            c -= 0.28 * smoothstep(-0.45, -1.05, c);
        }
        double rawC = c;
        double archMask = smoothstep(0.12, 0.42, archipelago.fbm(wx / (cs * 1.4), wz / (cs * 1.4)));
        double island = 0.0;
        if (archMask > 0.0 && c < -0.12) {
            double iv = islands.fbm(wx / (560.0 * hs), wz / (560.0 * hs));
            island = smoothstep(0.16, 0.44, iv) * archMask * smoothstep(-1.05, -0.4, c);
        }
        double mushroom = 0.0;
        if (c < -0.7) {
            mushroom = mushroomIsland(x, z) * smoothstep(-0.7, -0.9, c);
            island = Math.max(island, mushroom);
        }
        out.island = island;
        double cEff = c;
        if (island > 0.0) {
            double cIsland = -0.3 + 0.55 * island;
            cEff = Math.max(c, lerp(smoothstep(0.0, 0.3, island), c, cIsland));
        }
        out.rawContinentalness = rawC;

        // ---- 3. erosion and mountain belts ----------------------------------------------------
        double es = settings.erosionScale() * hs;
        double e0 = erosionNoise.fbm(wx / es, wz / es) * 1.35 + 0.0;
        double bs = es * 2.5;
        double beltRaw = beltNoise.fbm(wx / bs, wz / bs);
        double beltLine = 1.0 - Math.abs(beltRaw) * 4.5;
        double beltCore = smoothstep(0.0, 1.0, beltLine);
        double beltSeg = smoothstep(-0.25, 0.2, beltSegments.fbm(wx / (bs * 0.8), wz / (bs * 0.8)));
        double beltStrength = beltCore * beltSeg;
        double erosion = clamp(e0 - 1.4 * beltStrength, -1.0, 1.0);

        double reliefLand = smoothstep(-0.2, -0.1, cEff);

        // ---- 4. base profile ------------------------------------------------------------------
        double base = TMath.curve(cEff, PROFILE_C, PROFILE_H);

        // ---- 5. mountains (see mountains) -----------------------------------------------------
        // local relief uses a gentle warp only: the strong continental warp would bend individual
        // ridges into swirls, while real ranges keep fairly straight ridge crests over a few km
        double rx = x + 0.22 * (wx - x);
        double rz = z + 0.22 * (wz - z);
        double ms = settings.mountainScale() * hs;
        double vRaw = valleyNoise.fbm(rx / (ms * 1.8), rz / (ms * 1.8));
        double valleyCut = smoothstep(0.0, 0.3, Math.abs(vRaw));
        double hillShape = 0.5;
        MountainState mt = sc.mountains;
        double mountainHeight = mountains(wx, wz, rx, rz, vRaw, cEff, rawC, reliefLand, beltRaw, beltSeg, beltStrength, base, mt);
        double mountainE = mt.mask;
        double mountain = mt.weight;
        double rangeI = mt.rangeI;
        double alpineW = mt.alpine;
        double ancientW = mt.ancient;
        double wallW = mt.wall;
        double shape = mt.shape;
        double meadow = mt.meadow;

        // ---- 6. hills, plateaus, windswept uplands, plains -----------------------------------
        double hillW = bump(erosion, -0.6, -0.16, 0.34) * reliefLand * (1.0 - mountain);
        double hillHeight = 0.0;
        if (hillW > 0.0) {
            double hillAmp = 14.0 + 66.0 * (0.5 + 0.5 * hillAmplitude.fbm(wx / (es * 1.8), wz / (es * 1.8)));
            hillShape = 0.5 + 0.5 * hillNoise.erodedFbm(rx / (600.0 * hs), rz / (600.0 * hs), 0.25, d);
            hillHeight = hillW * hillAmp * hillShape * (0.5 + 0.5 * valleyCut);
        }

        double plateauRegion = smoothstep(-0.05, 0.2, plateauHeight.fbm(wx / (es * 1.3) - 40.0, wz / (es * 1.3) + 13.0));
        double plateauBand = bump(erosion, -0.52, -0.31, -0.12) * smoothstep(-0.04, 0.12, cEff) * (1.0 - 0.75 * mountain) * plateauRegion;
        double plateau = 0.0;
        double plateauHeightValue = 0.0;
        double plateauEdge = 0.0;
        if (plateauBand > 0.0) {
            double ps = 1500.0 * hs;
            double pv = plateauNoise.fbm(rx / ps, rz / ps) + 0.16;
            double pH = 45.0 + 200.0 * (0.5 + 0.5 * plateauHeight.fbm(wx / (es * 2.0), wz / (es * 2.0)));
            double pMask;
            if (pv > 0.2) {
                pMask = 1.0;
            } else if (pv < -0.3) {
                pMask = 0.0;
            } else {
                // a mesa edge: a caprock cliff at most ~28 blocks high above a concave talus apron
                // whose width grows with the plateau's height, instead of a sheer 200-block wall
                double pvx = plateauNoise.fbm((rx + 4.0) / ps, rz / ps) + 0.16;
                double pvz = plateauNoise.fbm(rx / ps, (rz + 4.0) / ps) + 0.16;
                double grad = Math.sqrt((pvx - pv) * (pvx - pv) + (pvz - pv) * (pvz - pv)) / 4.0;
                double edgeDist = pv / Math.max(grad, 1.0E-7);
                double cap = Math.min(28.0, 0.22 * pH);
                double talusW = Math.max(20.0, (pH - cap) * 1.05);
                if (edgeDist >= 0.0) {
                    pMask = 1.0;
                } else if (edgeDist > -talusW) {
                    pMask = (1.0 - cap / pH) * Math.pow(1.0 + edgeDist / talusW, 1.6);
                } else {
                    pMask = 0.0;
                }
            }
            double top = TMath.terrace(pH + 14.0 * hillShape * smoothstep(0.02, 0.15, pv), 13.0, 0.72);
            plateau = plateauBand * pMask;
            plateauHeightValue = plateau * top;
            plateauEdge = plateauBand * (1.0 - Math.abs(2.0 * pMask - 1.0));
        }

        double windswept = bump(erosion, 0.4, 0.5, 0.6) * smoothstep(-0.02, 0.1, cEff) * reliefLand;
        double windHeight = windswept > 0.0
            ? windswept * (40.0 + 85.0 * rangeI) * windsweptNoise.ridged(rx / (430.0 * hs), rz / (430.0 * hs), 1.6)
            : 0.0;

        double plains = (3.6 * plainsNoise.fbm(rx / (720.0 * hs), rz / (720.0 * hs))
            + 1.4 * plainsDetail.fbm(x / (130.0 * hs), z / (130.0 * hs))) * reliefLand;

        double detail = (1.2 + 2.5 * hillW + 7.0 * mountain * shape + 3.0 * windswept) * plainsDetail.fbm(rx / (46.0 * hs) + 300.0, rz / (46.0 * hs) - 120.0);
        double h = base + mountainHeight + hillHeight + plateauHeightValue + windHeight + plains * (1.0 - mountain) + detail * reliefLand;

        // ocean floors: rolling abyssal plains, long ridges and rare seamounts instead of a flat bed;
        // kept well below sea level so no unintended islands appear
        double seaFloor = smoothstep(-0.34, -0.5, cEff);
        double oceanRidge = 0.0;
        if (seaFloor > 0.0) {
            double rolling = 7.0 * hillNoise.fbm(x / (240.0 * hs) + 711.0, z / (240.0 * hs) - 403.0);
            double ridge = windsweptNoise.ridged(wx / (1300.0 * hs) - 91.0, wz / (1300.0 * hs) + 57.0, 2.0);
            double seamount = smoothstep(0.6, 0.95, 0.5 + 0.5 * colossalNoise.fbm(wx / (900.0 * hs) + 333.0, wz / (900.0 * hs) - 222.0));
            double seabed = rolling + 34.0 * ridge * ridge + 70.0 * seamount * (0.6 + 0.4 * ridge);
            h = Math.min(h + seaFloor * seabed, Math.max(h, sl - 9.0));
            oceanRidge = seaFloor * smoothstep(0.82, 0.97, ridge);
        }

        // deep-sea trenches: long rifts in the abyssal plains whose floors drop to about
        // Y -230..-340. The cross-section is a steep-sided valley: a flat floor 40-80 blocks wide,
        // then walls that rise at up to ~58 degrees with benches and flatten towards the rim, so a
        // trench is several hundred blocks across instead of a vertical slot. The surface rule
        // lines them with magma and basalt; the ocean vent feature builds chimneys on the floor.
        double trench = 0.0;
        double trenchFloor = 0.0;
        double abyss = smoothstep(-0.5, -0.66, cEff);
        if (abyss > 0.0) {
            double ts = 3200.0 * hs;
            double tv = trenchNoise.fbm(wx / ts, wz / ts);
            if (Math.abs(tv) < 0.16) {
                double region = smoothstep(-0.1, 0.25, trenchRegion.fbm(wx / (5200.0 * hs) + 17.0, wz / (5200.0 * hs) - 41.0));
                if (region > 0.0) {
                    double tvx = trenchNoise.fbm((wx + 4.0) / ts, wz / ts);
                    double tvz = trenchNoise.fbm(wx / ts, (wz + 4.0) / ts);
                    double grad = Math.sqrt((tvx - tv) * (tvx - tv) + (tvz - tv) * (tvz - tv)) / 4.0;
                    double dist = Math.abs(tv) / Math.max(grad, 1.0E-7);
                    double drop = (170.0 + 100.0 * (0.5 + 0.5 * trenchRegion.fbm(wx / (1800.0 * hs) - 70.0, wz / (1800.0 * hs) + 25.0)))
                        * region * abyss;
                    double floorHalf = 20.0 + 20.0 * (0.5 + 0.5 * trenchRegion.fbm(wx / (900.0 * hs) + 5.0, wz / (900.0 * hs) - 9.0));
                    double wall = Math.max(30.0, drop * 0.8);
                    if (dist < floorHalf + wall) {
                        double sw = clamp01((dist - floorHalf) / wall);
                        double f = Math.pow(1.0 - sw, 1.3);
                        // benches every ~24 blocks of depth break up the walls
                        double depth = TMath.terrace(drop * f, 24.0, 0.55);
                        trenchFloor = Math.max(MIN_TRENCH_FLOOR, h - drop);
                        h = Math.max(trenchFloor, h - depth);
                        trench = f * smoothstep(0.0, 0.3, region * abyss);
                    }
                }
            }
        }

        // ---- 7. wetlands ---------------------------------------------------------------------
        double swamp = smoothstep(0.55, 0.72, erosion) * smoothstep(-0.11, 0.02, cEff) * (1.0 - mountain) * (1.0 - plateau);
        if (swamp > 0.0) {
            // a patchwork of shallow pools just below sea level (filled by the sea-level water
            // table) between low, grassy hummocks and mud flats
            double pools = swampNoise.fbm(x / (110.0 * hs), z / (110.0 * hs));
            double hummocks = Math.max(0.0, swampNoise.fbm(x / (17.0 * hs) + 40.0, z / (17.0 * hs) - 25.0));
            double wet = sl - 0.9 + 2.4 * pools + 1.6 * hummocks;
            h = lerp(swamp * 0.97, h, wet);
        }

        // ---- 8. coasts: cliffs and fjords -----------------------------------------------------
        double coastZone = bump(cEff, -0.26, -0.16, -0.05);
        double coastCliff = 0.0;
        if (coastZone > 0.0) {
            double cc = smoothstep(0.05, 0.35, coastCliffNoise.fbm(wx / (1900.0 * hs), wz / (1900.0 * hs)));
            // mountains reaching the coast slope down into the sea instead of being sliced into a
            // sheer wall hundreds of blocks high
            coastCliff = cc * coastZone * (0.35 + 0.65 * smoothstep(0.35, -0.5, erosion)) * (1.0 - swamp)
                * (1.0 - smoothstep(0.08, 0.35, mountain));
            if (coastCliff > 0.0) {
                // seaward of the cliff line the ground stays sea floor, landward it is at least the cliff top
                double t = (cEff + 0.2) / 0.1;
                double top = sl + 9.0 + 48.0 * cc * (0.4 + 0.6 * rangeI) + 30.0 * mountain;
                double landward = smoothstep(0.30, 0.36, t);
                double profile = lerp(landward, Math.min(h, base), Math.max(h, top));
                h = lerp(coastCliff, h, profile);
            }
        }
        // fjords: drowned glacial valleys with a U-shaped cross-section whose width grows with the
        // height of the mountains they cut, so their walls are steep but never a slot
        double fjord = 0.0;
        double fjordMountain = mountainE * reliefLand;
        if (fjordMountain > 0.15 && cEff < 0.1) {
            double strength = smoothstep(0.1, -0.08, cEff) * smoothstep(0.15, 0.4, fjordMountain);
            double fs = 900.0 * hs;
            double fv = fjordNoise.fbm(wx / fs, wz / fs);
            double fvx = fjordNoise.fbm((wx + 2.0) / fs, wz / fs);
            double fvz = fjordNoise.fbm(wx / fs, (wz + 2.0) / fs);
            double grad = Math.sqrt((fvx - fv) * (fvx - fv) + (fvz - fv) * (fvz - fv)) / 2.0;
            double dist = Math.abs(fv) / Math.max(grad, 1.0E-7);
            double floor = sl - 18.0 - 22.0 * strength;
            double floorHalf = 26.0 + 30.0 * strength;
            double wall = Math.max(70.0, (h - floor) * 1.1);
            if (strength > 0.0 && dist < floorHalf + wall) {
                double coldness = smoothstep(0.1, -0.4, temperatureBase(x, z));
                strength *= 0.35 + 0.65 * coldness;
                double sw = clamp01((dist - floorHalf) / wall);
                double profile = floor + (h - floor) * (1.0 - (1.0 - sw) * (1.0 - sw));
                if (profile < h) {
                    h = lerp(smoothstep(0.0, 0.55, strength), h, profile);
                }
                fjord = strength * (1.0 - sw);
            }
        }

        // ---- 9. volcanoes ---------------------------------------------------------------------
        double volcano = 0.0;
        double crater = 0.0;
        double volcanoHeight = sampleVolcano(x, z, base, sc);
        if (sc.volcanoInfluence > 0.0) {
            volcano = sc.volcanoInfluence;
            crater = sc.craterInfluence;
            if (sc.volcanoCraterMode) {
                h = lerp(volcano, h, volcanoHeight);
            } else {
                h = Math.max(h, volcanoHeight);
            }
        }

        double uncarved = h;

        // ---- 10. canyons ----------------------------------------------------------------------
        double canyon = 0.0;
        double canyonFloor = 0.0;
        double canyonWall = 0.0;
        double baseTemp = temperatureBase(x, z);
        double baseHum = humidityBase(x, z);
        double aridity = smoothstep(0.1, 0.6, baseTemp) * smoothstep(0.1, -0.5, baseHum);
        double canyonReg = smoothstep(0.32, 0.6,
            canyonRegion.fbm(wx / (es * 2.2), wz / (es * 2.2)) * 0.9 + 0.25 * aridity + 0.3 * plateau + 0.12 * (settings.canyonFrequency() - 1.0))
            * reliefLand * (1.0 - smoothstep(0.08, 0.3, mountain)) * (1.0 - swamp) * (1.0 - smoothstep(0.02, 0.25, volcano));
        if (canyonReg > 0.001) {
            double cScale = 2600.0 * hs;
            double cv = canyonNoise.fbm(wx / cScale, wz / cScale);
            double cvx = canyonNoise.fbm((wx + 3.0) / cScale, wz / cScale);
            double cvz = canyonNoise.fbm(wx / cScale, (wz + 3.0) / cScale);
            double grad = Math.sqrt((cvx - cv) * (cvx - cv) + (cvz - cv) * (cvz - cv)) / 3.0;
            double dist = Math.abs(cv) / Math.max(grad, 1.0E-7);
            double halfW = (38.0 + 120.0 * (0.5 + 0.5 * canyonWidth.fbm(wx / (1600.0 * hs), wz / (1600.0 * hs)))) * Math.sqrt(canyonReg);
            if (dist < halfW) {
                // most canyons are dry; about a third carry a shallow sea-level stream on their floor
                boolean wet = canyonRegion.fbm(wx / (es * 0.9) + 61.0, wz / (es * 0.9) - 17.0) > 0.25;
                double floor = Math.max(wet ? sl - 3.0 : sl + 4.0, base + 3.0 + 6.0 * smoothstep(0.0, 1.0, plains));
                // canyon depth is limited by its width so walls stay walls, not 300-block slots
                double relief = Math.min(h - floor, 40.0 + halfW * 0.6);
                floor = Math.max(floor, h - relief);
                if (relief > 18.0) {
                    double t = dist / halfW;
                    double profile;
                    if (t < 0.24) {
                        profile = floor;
                    } else {
                        // walls rise in steps of 8-18 blocks with ledges between them, like the
                        // benches of the Grand Canyon, instead of one sheer face
                        double wallT = (t - 0.24) / 0.76;
                        double step = Math.max(8.0, Math.min(18.0, relief / 6.0));
                        double stepped = TMath.terrace(Math.pow(wallT, 0.75) * relief, step, 0.72);
                        profile = floor + stepped;
                    }
                    double pillar = canyonPillar(x, z, halfW);
                    if (pillar > 0.0) {
                        profile = lerp(pillar, profile, h);
                    }
                    if (profile < h) {
                        double strength = smoothstep(18.0, 40.0, relief);
                        h = lerp(strength, h, profile);
                        canyon = strength * (1.0 - smoothstep(0.75, 1.0, t));
                        canyonFloor = floor;
                        canyonWall = strength * smoothstep(0.2, 0.32, t) * (1.0 - smoothstep(0.85, 1.0, t));
                    }
                }
            }
        }

        // ---- 11. rivers -------------------------------------------------------------------------
        double river = 0.0;
        double riverValley = 0.0;
        double riverHalfW = 0.0;
        double riverDist = 1.0E9;
        double riverLevel = sl;
        boolean dry = false;
        double waterLevel = ColumnSample.NO_WATER;
        double rs = settings.riverScale() * hs;
        double presence = smoothstep(-0.5, -0.18, riverPresence.fbm(x / (rs * 1.9), z / (rs * 1.9)));
        // rivers keep out of volcanoes so they never breach a crater rim and drain a crater lake
        double riverMask = presence * smoothstep(-0.16, -0.11, cEff) * (1.0 - smoothstep(0.42, 0.8, mountain)) * (1.0 - island)
            * (1.0 - smoothstep(0.02, 0.25, volcano));
        if (riverMask > 0.0) {
            double rwx = x + 280.0 * riverWarpX.fbm(x / (950.0 * hs), z / (950.0 * hs));
            double rwz = z + 280.0 * riverWarpZ.fbm(x / (950.0 * hs) + 9.1, z / (950.0 * hs) - 4.4);
            double rv = riverNoise.fbm(rwx / rs, rwz / rs);
            double rvx = riverNoise.fbm((rwx + 2.0) / rs, rwz / rs);
            double rvz = riverNoise.fbm(rwx / rs, (rwz + 2.0) / rs);
            double grad = Math.sqrt((rvx - rv) * (rvx - rv) + (rvz - rv) * (rvz - rv)) / 2.0;
            riverDist = Math.abs(rv) / Math.max(grad, 1.0E-7);
            double great = smoothstep(0.62, 0.86, 0.5 + 0.5 * greatRiver.fbm(x / (rs * 2.6), z / (rs * 2.6)))
                * smoothstep(0.25, 0.6, erosion) * smoothstep(0.5, 0.0, cEff);
            double normalHalfW = riverMask * (3.0 + 12.0 * smoothstep(0.55, -0.12, cEff)) * (1.0 - 0.55 * mountain);
            // great rivers are 100-200 blocks wide along their whole course and keep that width
            // down to the sea, only narrowing where the river itself fades out
            double greatW = smoothstep(0.0, 0.3, great);
            double greatHalfW = (50.0 + 50.0 * (0.5 + 0.5 * greatRiver.fbm(x / (rs * 0.7) + 31.7, z / (rs * 0.7) - 12.3)))
                * smoothstep(0.0, 0.45, riverMask);
            riverHalfW = settings.riverWidth() * lerp(greatW, normalHalfW, greatHalfW);
            great = greatW;
            if (riverHalfW > 1.0) {
                double depth = Math.min(2.5 + 0.13 * riverHalfW, 16.0);
                riverLevel = Math.floor(sl + Math.max(0.0, base - 70.0) * 0.75);
                double elevated = riverLevel - sl;
                dry = elevated > 1.0 && aridity > 0.55;
                double valleyW = riverHalfW * 2.2 + 26.0 + 140.0 * great + 70.0 * smoothstep(0.1, 0.5, erosion) + 40.0 * mountain;
                if (riverDist < riverHalfW + valleyW) {
                    double t = clamp01((riverDist - riverHalfW) / valleyW);
                    double bank = riverLevel + 1.0 + 1.6 * (0.5 + 0.5 * plains / 5.0);
                    double profile = bank + (h - bank) * Math.pow(smoothstep(0.0, 1.0, t), 0.85);
                    if (h > profile) {
                        h = profile;
                    }
                    if (elevated > 0.0 && t < 0.25 && riverDist >= riverHalfW) {
                        h = Math.max(h, riverLevel + 1.0);
                    }
                    riverValley = 1.0 - t;
                    if (riverDist < riverHalfW) {
                        double u = riverDist / riverHalfW;
                        double bed = riverLevel - depth * Math.pow(1.0 - u * u, 0.6);
                        if (dry) {
                            bed = riverLevel - 1.5 * Math.pow(1.0 - u * u, 0.6);
                        }
                        h = Math.min(h, bed);
                        river = 1.0 - u;
                        if (elevated > 0.5 && !dry) {
                            waterLevel = riverLevel;
                        }
                    }
                }
            }
        }

        // ---- 12. lakes --------------------------------------------------------------------------
        double lake = 0.0;
        int lakeType = LakeType.NONE;
        double lakeLevel = 0.0;
        double craterWater = sc.craterWaterLevel;
        double craterLava = sc.craterLavaLevel;
        if (withLakes) {
            sc.lakeResult = 0.0;
            h = applyLakes(x, z, h, sc);
            if (sc.lakeResult > 0.0) {
                lake = sc.lakeResult;
                lakeType = sc.lakeType;
                lakeLevel = sc.lakeLevel;
                if (lakeLevel > sl + 0.5 && sc.lakeInside && !(dry && riverValley > 0.15)) {
                    waterLevel = Math.max(waterLevel, lakeLevel);
                }
            }
        }
        if (crater > 0.0 && craterWater > sl && h < craterWater && h >= uncarved - 1.0) {
            waterLevel = Math.max(waterLevel, craterWater);
            lakeType = LakeType.CRATER;
            lake = Math.max(lake, crater);
            lakeLevel = craterWater;
        }

        h = TMath.softMax(h, settings.maxTerrainHeight() - 160.0, settings.maxTerrainHeight());
        out.lavaLevel = crater > 0.0 && craterLava != ColumnSample.NO_WATER && h < craterLava ? craterLava : ColumnSample.NO_WATER;
        if (waterLevel != ColumnSample.NO_WATER && waterLevel <= h + 0.25) {
            waterLevel = ColumnSample.NO_WATER;
        }

        // ---- 13. classification and climate ---------------------------------------------------
        out.height = h;
        out.uncarvedHeight = uncarved;
        out.waterLevel = waterLevel;
        out.mountain = mountain;
        out.peakness = mountain > 0.05 ? lerp(smoothstep(0.05, 0.4, mountain), hillShape * 0.6, shape) : hillShape * 0.6 * smoothstep(0.0, 0.5, hillW);
        out.alpine = alpineW * mountain;
        out.ancient = ancientW * mountain;
        out.wall = wallW * mountain;
        out.plateau = plateau;
        out.meadow = meadow;
        out.windswept = windswept;
        out.swamp = swamp;
        out.volcano = volcano;
        out.crater = crater;
        out.canyon = canyon;
        out.canyonFloor = canyonFloor;
        out.river = river;
        out.riverValley = riverValley;
        out.riverWidth = riverHalfW;
        out.riverLevel = riverLevel;
        out.riverDistance = riverHalfW > 0.0 ? riverDist / riverHalfW : 1.0E9;
        out.dryRiver = dry;
        out.lake = lake;
        out.lakeType = lakeType;
        out.lakeLevel = lakeLevel;
        out.coastCliff = coastCliff;
        out.fjord = fjord;
        out.trench = trench;
        out.trenchFloor = trenchFloor;
        out.oceanRidge = oceanRidge;
        out.trailDistance = trailDistance(x, z);
        out.overhangTop = Double.NaN;
        out.bigOverhangRim = Double.NaN;
        out.beach = (1.0 - coastCliff) * bump(h, sl - 3.0, sl + 1.0, sl + 5.0) * smoothstep(0.0, -0.16, cEff);
        out.stream = dry || aridity > 0.6 ? 0.0 : streamFactor(vRaw, mountain, hillW, erosion, x, z);
        out.cliff = clamp01(Math.max(Math.max(plateauEdge * 1.2, canyonWall), Math.max(coastCliff * smoothstep(sl, sl + 20.0, h),
            mountain * alpineW * smoothstep(0.5, 0.92, shape) * 0.7)));
        out.steepness = clamp01(Math.max(out.cliff, mountain * (0.3 + 0.7 * alpineW) * smoothstep(0.25, 0.8, shape)));
        out.geology = geologyAt(wx, wz, volcano, aridity, plateau);
        out.strata = 0.5 + 0.5 * strataNoise.fbm(wx / (320.0 * hs), wz / (320.0 * hs));

        // climate temperature: base field cooled with altitude
        double cooling = Math.max(0.0, h - 110.0) / 560.0 * settings.altitudeCooling();
        out.baseTemperature = baseTemp;
        out.temperature = clamp(baseTemp - cooling, -1.0, 1.0);
        out.humidity = clamp(baseHum + 0.12 * riverValley + 0.1 * lake, -1.0, 1.0);

        // continentalness handed to the biome source follows the final terrain so that ocean,
        // coast and inland biomes always sit on matching ground
        boolean seaWater = h < sl - 0.5 && river <= 0.0 && lake <= 0.0 && rawC < -0.05;
        double cOut;
        if (mushroom > 0.3 && h >= sl - 4.0) {
            cOut = -1.12;
        } else if (seaWater || fjord > 0.5 && h < sl - 2.0) {
            cOut = clamp(Math.min(rawC, -0.2), -1.04, -0.2);
            // deep ocean biomes below the ocean/deep-ocean profile boundary (about Y -25)
            if (h < sl - 88.0) {
                cOut = Math.min(cOut, -0.47);
            }
        } else if (h < sl + 4.5 && cEff < -0.06 && mountain < 0.3) {
            cOut = clamp(cEff, -0.185, -0.115);
        } else {
            cOut = Math.max(cEff, -0.105);
        }
        out.continentalness = clamp(cOut, -1.2, 1.0);

        double eOut = erosion;
        if (plateau > 0.5) {
            eOut = clamp(eOut, -0.37, -0.23);
        }
        out.erosion = eOut;

        // peaks-and-valleys: high on summits, low on valley floors, -1 in river channels
        double pvMountain = -0.55 + 1.65 * shape;
        double pvHills = -0.45 + 0.9 * hillShape * smoothstep(0.0, 0.5, hillW + plateau);
        double pv = lerp(smoothstep(0.1, 0.35, mountain), pvHills, pvMountain);
        if (riverValley > 0.0) {
            pv = Math.min(pv, lerp(riverValley, pv, -0.8));
        }
        double w = (clamp(pv, -1.0, 1.0) + 1.0) / 3.0;
        w = clamp(w, 0.06, 0.66);
        if (river > 0.0 && !dry) {
            w = 0.02;
        }
        double sign = variantNoise.fbm(x / (1900.0 * hs), z / (1900.0 * hs)) >= 0.0 ? 1.0 : -1.0;
        out.weirdness = sign * w;
    }

    // =========================================================================================
    // Climate fields
    // =========================================================================================

    public double temperatureBase(int x, int z) {
        double cl = settings.climateScale() * hs;
        return clamp(temperatureNoise.fbm(x / cl, z / cl) * 1.8 + 0.15 * temperatureDetail.fbm(x / (650.0 * hs), z / (650.0 * hs)), -1.0, 1.0);
    }

    public double humidityBase(int x, int z) {
        double cl = settings.climateScale() * hs * 0.8;
        return clamp(humidityNoise.fbm(x / cl + 100.0, z / cl - 60.0) * 1.8, -1.0, 1.0);
    }

    // =========================================================================================
    // Streams
    // =========================================================================================

    private double streamFactor(double vRaw, double mountain, double hillW, double erosion, int x, int z) {
        double region = Math.max(mountain, hillW) * (1.0 - smoothstep(0.5, 0.7, erosion));
        if (region <= 0.0) {
            return 0.0;
        }
        double sp = smoothstep(-0.3, 0.1, streamPresence.fbm(x / (2400.0 * hs), z / (2400.0 * hs)));
        return smoothstep(0.035, 0.0, Math.abs(vRaw)) * region * sp;
    }

    /**
     * Distance in blocks from {@code (x, z)} to the nearest stream centreline, or a large value.
     * Streams follow the zero lines of the mountain valley field, so they run along valley floors.
     */
    public double streamDistance(int x, int z) {
        double wx = x + 320.0 * warpX.fbm(x / (2600.0 * hs), z / (2600.0 * hs))
            + 24.0 * warpX2.fbm(x / (420.0 * hs), z / (420.0 * hs));
        double wz = z + 320.0 * warpZ.fbm(x / (2600.0 * hs) + 31.7, z / (2600.0 * hs) - 12.4)
            + 24.0 * warpZ2.fbm(x / (420.0 * hs) - 7.1, z / (420.0 * hs) + 3.3);
        double rx = x + 0.22 * (wx - x);
        double rz = z + 0.22 * (wz - z);
        double ms = settings.mountainScale() * hs * 1.8;
        double v = valleyNoise.fbm(rx / ms, rz / ms);
        double vx = valleyNoise.fbm((rx + 1.0) / ms, rz / ms);
        double vz = valleyNoise.fbm(rx / ms, (rz + 1.0) / ms);
        double g = Math.sqrt((vx - v) * (vx - v) + (vz - v) * (vz - v));
        return Math.abs(v) / Math.max(g, 1.0E-7);
    }

    // =========================================================================================
    // Volcanoes
    // =========================================================================================

    private double sampleVolcano(int x, int z, double base, Scratch sc) {
        sc.volcanoInfluence = 0.0;
        sc.craterInfluence = 0.0;
        sc.craterWaterLevel = ColumnSample.NO_WATER;
        sc.craterLavaLevel = ColumnSample.NO_WATER;
        sc.volcanoCraterMode = false;
        double cell = 4200.0 * hs;
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        double best = -Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                VolcanoSite v = volcanoSite(cx + dx, cz + dz, cell);
                if (v == null) {
                    continue;
                }
                double ddx = x - v.x;
                double ddz = z - v.z;
                double r = Math.sqrt(ddx * ddx + ddz * ddz);
                if (r >= v.radius) {
                    continue;
                }
                double angle = Math.atan2(ddz, ddx);
                double gullies = 1.0 + 0.07 * Math.sin(angle * v.ridges + v.phase) * smoothstep(v.craterRadius, v.radius * 0.6, r);
                double t = r / v.radius;
                double cone = v.height * Math.pow(1.0 - t, 1.65) * gullies;
                double rimHeight = v.height * Math.pow(1.0 - v.craterRadius / v.radius, 1.65);
                double profile = v.baseHeight + cone;
                double craterT = 0.0;
                if (r < v.craterRadius) {
                    double u = r / v.craterRadius;
                    profile = v.baseHeight + rimHeight - v.craterDepth * (1.0 - u * u);
                    craterT = 1.0 - u;
                    sc.volcanoCraterMode = true;
                    if (v.waterLevel != ColumnSample.NO_WATER) {
                        sc.craterWaterLevel = v.waterLevel;
                    } else if (v.lava) {
                        sc.craterLavaLevel = Math.floor(v.baseHeight + rimHeight - v.craterDepth * 0.82);
                    }
                }
                if (profile > best) {
                    best = profile;
                    sc.volcanoInfluence = smoothstep(1.0, 0.55, t);
                    sc.craterInfluence = craterT;
                }
            }
        }
        if (sc.volcanoInfluence <= 0.0) {
            sc.volcanoCraterMode = false;
        }
        return best;
    }

    private VolcanoSite volcanoSite(int cx, int cz, double cell) {
        long key = SeedMixer.hash2(volcanoSeed, cx, cz);
        VolcanoSite cached = volcanoSites.get(key);
        if (cached == null) {
            cached = computeVolcanoSite(cx, cz, cell);
            if (volcanoSites.size() > 8192) {
                volcanoSites.clear();
            }
            volcanoSites.put(key, cached);
        }
        return cached == NO_VOLCANO ? null : cached;
    }

    private VolcanoSite computeVolcanoSite(int cx, int cz, double cell) {
        double roll = SeedMixer.cellUnit(volcanoSeed, cx, cz, 0);
        double px = (cx + 0.2 + 0.6 * SeedMixer.cellUnit(volcanoSeed, cx, cz, 1)) * cell;
        double pz = (cz + 0.2 + 0.6 * SeedMixer.cellUnit(volcanoSeed, cx, cz, 2)) * cell;
        // macro conditions at the site: warm-ish land away from the poles, or volcanic islands
        int ix = (int) px;
        int iz = (int) pz;
        double c = macroContinentalness(ix, iz);
        double chance = (c > -0.1 ? 0.06 : 0.035) * settings.volcanoFrequency();
        if (roll > chance || c < -0.9) {
            return NO_VOLCANO;
        }
        VolcanoSite v = new VolcanoSite();
        v.x = px;
        v.z = pz;
        double size = SeedMixer.cellUnit(volcanoSeed, cx, cz, 3);
        v.height = 200.0 + 420.0 * size * size + (SeedMixer.cellUnit(volcanoSeed, cx, cz, 4) < 0.12 ? 280.0 : 0.0);
        v.radius = v.height * (2.2 + 1.1 * SeedMixer.cellUnit(volcanoSeed, cx, cz, 5)) * hs;
        v.craterRadius = v.radius * (0.1 + 0.08 * SeedMixer.cellUnit(volcanoSeed, cx, cz, 6));
        v.craterDepth = v.height * (0.12 + 0.18 * SeedMixer.cellUnit(volcanoSeed, cx, cz, 7));
        v.baseHeight = Math.max(TMath.curve(c, PROFILE_C, PROFILE_H), seaLevel - 30.0);
        v.ridges = 9 + (int) (SeedMixer.cellUnit(volcanoSeed, cx, cz, 8) * 8);
        v.phase = SeedMixer.cellUnit(volcanoSeed, cx, cz, 9) * Math.PI * 2.0;
        double lakeRoll = SeedMixer.cellUnit(volcanoSeed, cx, cz, 10);
        double rim = v.baseHeight + v.height * Math.pow(1.0 - v.craterRadius / v.radius, 1.65);
        v.waterLevel = lakeRoll < 0.45 ? Math.floor(rim - v.craterDepth * 0.35) : ColumnSample.NO_WATER;
        v.lava = lakeRoll > 0.7;
        return v;
    }

    /** Volcano information for the find command and decoration features. */
    public VolcanoSite nearestVolcano(int x, int z, double maxDistance) {
        double cell = 4200.0 * hs;
        int range = (int) Math.ceil(maxDistance / cell) + 1;
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        VolcanoSite best = null;
        double bestD = maxDistance * maxDistance;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                VolcanoSite v = volcanoSite(cx + dx, cz + dz, cell);
                if (v != null) {
                    double dd = (v.x - x) * (v.x - x) + (v.z - z) * (v.z - z);
                    if (dd < bestD) {
                        bestD = dd;
                        best = v;
                    }
                }
            }
        }
        return best;
    }

    /** Cheap continentalness (no relief) used for site decisions. */
    public double macroContinentalness(int x, int z) {
        double wx = x + 320.0 * warpX.fbm(x / (2600.0 * hs), z / (2600.0 * hs))
            + 24.0 * warpX2.fbm(x / (420.0 * hs), z / (420.0 * hs));
        double wz = z + 320.0 * warpZ.fbm(x / (2600.0 * hs) + 31.7, z / (2600.0 * hs) - 12.4)
            + 24.0 * warpZ2.fbm(x / (420.0 * hs) - 7.1, z / (420.0 * hs) + 3.3);
        double cs = settings.continentScale() * hs;
        double c = continent.fbm(wx / cs, wz / cs) * 2.4 + 0.02;
        if (c < -0.45) {
            c -= 0.28 * smoothstep(-0.45, -1.05, c);
        }
        return c;
    }

    // =========================================================================================
    // Mushroom islands
    // =========================================================================================

    /** Rare isolated islands far out in deep oceans that receive the mushroom fields biome. */
    private double mushroomIsland(int x, int z) {
        double cell = 11000.0 * hs;
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        double best = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int gx = cx + dx;
                int gz = cz + dz;
                if (SeedMixer.cellUnit(siteSeed ^ 0x5EED, gx, gz, 0) > 0.35) {
                    continue;
                }
                double px = (gx + 0.2 + 0.6 * SeedMixer.cellUnit(siteSeed ^ 0x5EED, gx, gz, 1)) * cell;
                double pz = (gz + 0.2 + 0.6 * SeedMixer.cellUnit(siteSeed ^ 0x5EED, gx, gz, 2)) * cell;
                double radius = (200.0 + 260.0 * SeedMixer.cellUnit(siteSeed ^ 0x5EED, gx, gz, 3)) * hs;
                double ddx = x - px;
                double ddz = z - pz;
                double r = Math.sqrt(ddx * ddx + ddz * ddz);
                if (r > radius * 1.5) {
                    continue;
                }
                double angle = Math.atan2(ddz, ddx);
                double edge = radius * (1.0 + 0.3 * lakeShape.noise2(Math.cos(angle) * 1.4 + gx, Math.sin(angle) * 1.4 + gz));
                best = Math.max(best, smoothstep(edge * 1.25, edge * 0.55, r));
            }
        }
        return best;
    }

    // =========================================================================================
    // Canyon pillars
    // =========================================================================================

    private double canyonPillar(int x, int z, double halfW) {
        double cell = 72.0 * hs;
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        double best = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int gx = cx + dx;
                int gz = cz + dz;
                if (SeedMixer.cellUnit(pillarSeed, gx, gz, 0) > 0.3) {
                    continue;
                }
                double px = (gx + SeedMixer.cellUnit(pillarSeed, gx, gz, 1)) * cell;
                double pz = (gz + SeedMixer.cellUnit(pillarSeed, gx, gz, 2)) * cell;
                double radius = (5.0 + 9.0 * SeedMixer.cellUnit(pillarSeed, gx, gz, 3)) * Math.min(1.0, halfW / 60.0);
                double r = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
                if (r < radius + 3.0) {
                    best = Math.max(best, smoothstep(radius + 3.0, radius - 1.0, r));
                }
            }
        }
        return best;
    }

    // =========================================================================================
    // Geology
    // =========================================================================================

    private int geologyAt(double wx, double wz, double volcano, double aridity, double plateau) {
        if (volcano > 0.25) {
            return Geology.VOLCANIC;
        }
        double cell = 2600.0 * hs;
        int cx = (int) Math.floor(wx / cell);
        int cz = (int) Math.floor(wz / cell);
        double bestD = Double.MAX_VALUE;
        int bestId = Geology.TEMPERATE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int gx = cx + dx;
                int gz = cz + dz;
                double px = (gx + SeedMixer.cellUnit(geologySeed, gx, gz, 1)) * cell;
                double pz = (gz + SeedMixer.cellUnit(geologySeed, gx, gz, 2)) * cell;
                double dd = (wx - px) * (wx - px) + (wz - pz) * (wz - pz);
                if (dd < bestD) {
                    bestD = dd;
                    double r = SeedMixer.cellUnit(geologySeed, gx, gz, 3);
                    bestId = r < 0.3 ? Geology.TEMPERATE : r < 0.5 ? Geology.GRANITE : r < 0.62 ? Geology.DARK : r < 0.78 ? Geology.LIMESTONE : Geology.SEDIMENTARY;
                }
            }
        }
        if (bestId != Geology.SEDIMENTARY && aridity > 0.6 && plateau > 0.3) {
            bestId = Geology.SEDIMENTARY;
        }
        return bestId;
    }

    // =========================================================================================
    // Lakes
    // =========================================================================================

    private double applyLakes(int x, int z, double h, Scratch sc) {
        sc.lakeResult = 0.0;
        sc.lakeInside = false;
        double cell = 700.0 * hs / Math.max(0.05, settings.lakeFrequency());
        h = applyLakeGrid(x, z, h, sc, cell, lakeSeed, false);
        double greatCell = 6000.0 * hs / Math.max(0.05, Math.sqrt(settings.lakeFrequency()));
        h = applyLakeGrid(x, z, h, sc, greatCell, greatLakeSeed, true);
        return h;
    }

    private double applyLakeGrid(int x, int z, double h, Scratch sc, double cell, long gridSeed, boolean great) {
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LakeSite lake = lakeSite(cx + dx, cz + dz, cell, gridSeed, great);
                if (lake == null) {
                    continue;
                }
                double ddx = x - lake.x;
                double ddz = z - lake.z;
                double r = Math.sqrt(ddx * ddx + ddz * ddz);
                if (r > lake.ringRadius) {
                    continue;
                }
                // the shoreline is a distorted level set in world space, giving organic outlines
                double warp = 0.32 * lakeShape.noise2(x / (lake.radius * 0.95) + lake.shapeOffset, z / (lake.radius * 0.95) - lake.shapeOffset)
                    + 0.1 * lakeShape.noise2(x / (lake.radius * 0.33) - lake.shapeOffset, z / (lake.radius * 0.33) + lake.shapeOffset);
                double edge = lake.radius * Math.max(0.35, 1.0 + warp);
                double shoreW = 10.0 + lake.radius * 0.25;
                if (r < edge) {
                    double u = r / edge;
                    double bed = lake.level - lake.depth * Math.pow(1.0 - u * u, 0.55);
                    h = Math.min(h, bed);
                    double inside = 1.0 - u;
                    if (inside > sc.lakeResult || !sc.lakeInside) {
                        sc.lakeResult = Math.max(inside, 0.001);
                        sc.lakeType = lake.type;
                        sc.lakeLevel = lake.level;
                        sc.lakeInside = true;
                    }
                } else {
                    double t = clamp01((r - edge) / shoreW);
                    double shore = lake.level + 1.0 + Math.max(0.0, h - lake.level - 1.0) * smoothstep(0.0, 1.0, t);
                    if (h > shore) {
                        h = shore;
                    }
                    // a rim guarantees the lake is enclosed above sea level
                    if (lake.level > seaLevel && r < lake.ringRadius) {
                        h = Math.max(h, lake.level + 1.0);
                    }
                }
            }
        }
        return h;
    }

    private LakeSite lakeSite(int cx, int cz, double cell, long gridSeed, boolean great) {
        long key = SeedMixer.hash2(gridSeed, cx, cz);
        LakeSite cached = lakeSites.get(key);
        if (cached != null) {
            return cached.active ? cached : null;
        }
        LakeSite computed = computeLakeSite(cx, cz, cell, gridSeed, great);
        if (lakeSites.size() > 16384) {
            lakeSites.clear();
        }
        lakeSites.put(key, computed);
        return computed.active ? computed : null;
    }

    private LakeSite computeLakeSite(int cx, int cz, double cell, long gridSeed, boolean great) {
        LakeSite lake = new LakeSite();
        double roll = SeedMixer.cellUnit(gridSeed, cx, cz, 0);
        double maxChance = great ? 0.3 : 0.65;
        if (roll > maxChance) {
            return lake;
        }
        lake.x = (cx + 0.25 + 0.5 * SeedMixer.cellUnit(gridSeed, cx, cz, 1)) * cell;
        lake.z = (cz + 0.25 + 0.5 * SeedMixer.cellUnit(gridSeed, cx, cz, 2)) * cell;
        ColumnSample center = new ColumnSample();
        Scratch sc = scratch.get();
        computeColumn((int) lake.x, (int) lake.z, center, false, sc);
        if (center.height < seaLevel + 1.0 || center.river > 0.0 || center.riverValley > 0.6 || center.canyon > 0.1
            || center.volcano > 0.2 || center.continentalness < -0.11) {
            return lake;
        }
        double size = SeedMixer.cellUnit(gridSeed, cx, cz, 3);
        double chance;
        if (great) {
            if (center.mountain > 0.3 || center.plateau > 0.5) {
                return lake;
            }
            lake.type = LakeType.GREAT;
            lake.radius = (220.0 + 380.0 * size) * hs;
            chance = 0.3;
        } else if (center.mountain > 0.25) {
            if (center.peakness > 0.55) {
                return lake;
            }
            lake.type = center.temperature < -0.3 ? LakeType.GLACIAL : LakeType.MOUNTAIN;
            lake.radius = (28.0 + 80.0 * size) * hs;
            chance = 0.5;
        } else if (center.swamp > 0.4) {
            lake.type = LakeType.MARSH;
            lake.radius = (30.0 + 70.0 * size) * hs;
            chance = 0.55;
        } else if (center.plateau > 0.5) {
            lake.type = LakeType.PLATEAU;
            lake.radius = (25.0 + 60.0 * size) * hs;
            chance = 0.35;
        } else {
            lake.type = LakeType.FOREST;
            lake.radius = (18.0 + 75.0 * size * size) * hs;
            chance = 0.4;
        }
        if (center.humidity < -0.45) {
            chance *= 0.35;
        }
        if (roll > chance) {
            return lake;
        }
        lake.ringRadius = lake.radius * 1.45 + 12.0;
        // level: one block below the lowest point of the surrounding ring, so the basin is closed
        double minRing = Double.MAX_VALUE;
        ColumnSample ring = new ColumnSample();
        int samples = 16;
        for (int i = 0; i < samples; i++) {
            double a = i * Math.PI * 2.0 / samples;
            int px = (int) (lake.x + Math.cos(a) * lake.radius * 1.3);
            int pz = (int) (lake.z + Math.sin(a) * lake.radius * 1.3);
            computeColumn(px, pz, ring, false, sc);
            if (ring.river > 0.0) {
                minRing = Math.min(minRing, seaLevel);
            }
            minRing = Math.min(minRing, ring.height);
        }
        if (minRing < seaLevel) {
            minRing = seaLevel;
        }
        lake.level = Math.floor(minRing) - 1.0;
        if (lake.level < seaLevel) {
            lake.level = seaLevel;
        }
        lake.depth = switch (lake.type) {
            case LakeType.MARSH -> 2.0 + lake.radius * 0.02;
            case LakeType.GLACIAL, LakeType.MOUNTAIN -> Math.min(9.0 + lake.radius * 0.18, 34.0);
            case LakeType.GREAT -> Math.min(14.0 + lake.radius * 0.05, 40.0);
            default -> Math.min(4.0 + lake.radius * 0.11, 18.0);
        };
        lake.shapeOffset = SeedMixer.cellUnit(gridSeed, cx, cz, 4) * 100.0;
        lake.active = true;
        return lake;
    }

    /** Lake lookup for the find command: nearest active lake centre within the grid cells around. */
    public LakeSite nearestLake(int x, int z, int rangeBlocks, boolean great) {
        double cell = great ? 6000.0 * hs / Math.max(0.05, Math.sqrt(settings.lakeFrequency())) : 700.0 * hs / Math.max(0.05, settings.lakeFrequency());
        long gridSeed = great ? greatLakeSeed : lakeSeed;
        int range = (int) Math.ceil(rangeBlocks / cell);
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        LakeSite best = null;
        double bestD = Double.MAX_VALUE;
        for (int r = 0; r <= range; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    LakeSite lake = lakeSite(cx + dx, cz + dz, cell, gridSeed, great);
                    if (lake != null) {
                        double dd = (lake.x - x) * (lake.x - x) + (lake.z - z) * (lake.z - z);
                        if (dd < bestD) {
                            bestD = dd;
                            best = lake;
                        }
                    }
                }
            }
            if (best != null && Math.sqrt(bestD) < (r - 0.5) * cell) {
                break;
            }
        }
        return best;
    }

    // =========================================================================================
    // Waterfall sites
    // =========================================================================================

    /** Grid spacing of waterfall candidate sites in blocks. */
    public static final int WATERFALL_CELL = 176;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    /**
     * Deterministic waterfall for a grid cell, or {@code null}. A waterfall needs a near-vertical
     * drop of at least 12 blocks (a cliff, canyon step, mesa rim, fjord or sea cliff) in a climate
     * that is not too dry. The whole plan (lip, curtain, plunge pool, feeding stream) is derived
     * from the terrain model, so the waterfall feature always builds what {@code /terrahaul find}
     * reports.
     */
    public WaterfallSite waterfallSite(int cellX, int cellZ) {
        long key = SeedMixer.hash2(siteSeed ^ 0xFA11L, cellX, cellZ);
        WaterfallSite cached = waterfallSites.get(key);
        if (cached == null) {
            cached = computeWaterfallSite(cellX, cellZ);
            if (waterfallSites.size() > 16384) {
                waterfallSites.clear();
            }
            waterfallSites.put(key, cached);
        }
        return cached == NO_WATERFALL ? null : cached;
    }

    private WaterfallSite computeWaterfallSite(int cellX, int cellZ) {
        long seedW = siteSeed ^ 0xFA11L;
        if (SeedMixer.cellUnit(seedW, cellX, cellZ, 0) > 0.6) {
            return NO_WATERFALL;
        }
        ColumnSample s = new ColumnSample();
        Scratch sc = scratch.get();
        WaterfallSite best = null;
        double bestScore = 0.0;
        double[] prof = new double[40];
        for (int attempt = 0; attempt < 6; attempt++) {
            int px = (int) ((cellX + 0.1 + 0.8 * SeedMixer.cellUnit(seedW, cellX, cellZ, 1 + attempt * 2)) * WATERFALL_CELL);
            int pz = (int) ((cellZ + 0.1 + 0.8 * SeedMixer.cellUnit(seedW, cellX, cellZ, 2 + attempt * 2)) * WATERFALL_CELL);
            computeColumn(px, pz, s, true, sc);
            if (s.height < seaLevel + 8 || s.humidity < -0.3 || s.baseTemperature > 0.75 && s.humidity < 0.0
                || s.waterLevel != ColumnSample.NO_WATER || s.river > 0.0 || s.lake > 0.0 || s.volcano > 0.3
                || s.cliff < 0.15 && s.steepness < 0.3 && s.canyon < 0.2 && s.coastCliff < 0.2 && s.plateau < 0.2) {
                continue;
            }
            double humidity = s.humidity;
            for (int d = 0; d < 4; d++) {
                int dx = DIRS[d][0];
                int dz = DIRS[d][1];
                // height profile from 12 blocks behind to 27 blocks beyond the candidate
                for (int k = -12; k < 28; k++) {
                    prof[k + 12] = interpolatedHeight(px + dx * k, pz + dz * k);
                }
                WaterfallSite w = planWaterfall(prof, px, pz, dx, dz, humidity, seedW, cellX, cellZ);
                if (w != null && w.score > bestScore) {
                    bestScore = w.score;
                    best = w;
                }
            }
        }
        return best == null ? NO_WATERFALL : best;
    }

    /** Fits a waterfall to a height profile along one direction, or returns {@code null}. */
    private WaterfallSite planWaterfall(double[] prof, int px, int pz, int dx, int dz, double humidity, long seedW, int cellX, int cellZ) {
        final int o = 12;
        double t0 = prof[o];
        // the ground behind the lip must not fall away (the stream comes from there)
        for (int k = 1; k <= 8; k++) {
            if (prof[o - k] < t0 - 2.0 || prof[o - k] > t0 + 30.0) {
                return null;
            }
        }
        // the lip: the last column of the high ground
        int lip = 0;
        double top = t0;
        while (lip < 6 && prof[o + lip + 1] >= t0 - 1.5) {
            lip++;
            top = Math.min(top, prof[o + lip]);
        }
        int face = lip + 1;
        if (prof[o + face] > top - 3.0) {
            return null;
        }
        // the foot: where the drop flattens out
        int foot = -1;
        for (int k = face; k < 26; k++) {
            if (prof[o + k] < top - 10.0 && prof[o + k] - prof[o + k + 1] < 1.0) {
                foot = k;
                break;
            }
        }
        if (foot < 0) {
            return null;
        }
        double bottom = prof[o + foot];
        double drop = top - bottom;
        // only near-vertical drops: the face may span at most a few blocks
        if (drop < 12.0 || foot - face > 1 + (int) (drop / 10.0)) {
            return null;
        }
        WaterfallSite w = new WaterfallSite();
        w.dirX = dx;
        w.dirZ = dz;
        w.x = px + dx * lip;
        w.z = pz + dz * lip;
        w.top = Math.floor(top);
        w.bottom = Math.floor(bottom);
        w.faceDist = foot - lip;
        double wet = Math.max(0.0, humidity + 0.3);
        int width = (int) Math.round(3.0 + drop / 9.0 + 5.0 * wet * SeedMixer.cellUnit(seedW, cellX, cellZ, 21));
        width = Math.min(13, width) | 1;
        // the cliff must carry the whole width: narrow the fall where the lip or the drop ends
        int half = width / 2;
        int px2 = -dz;
        int pz2 = dx;
        while (half > 0) {
            boolean ok = true;
            for (int side = -1; side <= 1; side += 2) {
                int lx = w.x + px2 * half * side;
                int lz = w.z + pz2 * half * side;
                double lipH = interpolatedHeight(lx, lz);
                double footH = interpolatedHeight(lx + dx * w.faceDist, lz + dz * w.faceDist);
                if (Math.abs(lipH - top) > 2.5 || footH > bottom + 4.0) {
                    ok = false;
                }
            }
            if (ok) {
                break;
            }
            half--;
        }
        w.width = half * 2 + 1;
        w.poolSurface = (int) w.bottom;
        w.poolAcross = half + 2.5 + Math.min(4.0, drop * 0.05);
        w.poolAlong = Math.min(9.0, 3.5 + drop * 0.06);
        w.poolDepth = (int) Math.min(6.0, 2.0 + drop / 25.0);
        w.channel = 8 + (int) (6.0 * SeedMixer.cellUnit(seedW, cellX, cellZ, 22));
        w.seed = SeedMixer.hash2(seedW, cellX, cellZ);
        w.score = drop * w.width * (1.0 + wet);
        return w;
    }

    // =========================================================================================
    // Rock formation sites (arches, spires, hoodoos, sea stacks)
    // =========================================================================================

    public static final int FORMATION_CELL = 384;
    public static final int ARCH = 1;
    public static final int SPIRE = 2;
    public static final int HOODOO = 3;
    public static final int SEA_STACK = 4;

    public FormationSite formationSite(int cellX, int cellZ) {
        long key = SeedMixer.hash2(siteSeed ^ 0xF0F0L, cellX, cellZ);
        FormationSite cached = formationSites.get(key);
        if (cached == null) {
            cached = computeFormationSite(cellX, cellZ);
            if (formationSites.size() > 16384) {
                formationSites.clear();
            }
            formationSites.put(key, cached);
        }
        return cached == NO_FORMATION ? null : cached;
    }

    private FormationSite computeFormationSite(int cellX, int cellZ) {
        long seedF = siteSeed ^ 0xF0F0L;
        double roll = SeedMixer.cellUnit(seedF, cellX, cellZ, 0);
        // dry red-rock and desert country gets formations in 70% of cells, elsewhere 45%
        if (roll > 0.7) {
            return NO_FORMATION;
        }
        boolean common = roll <= 0.45;
        int px = (int) ((cellX + 0.2 + 0.6 * SeedMixer.cellUnit(seedF, cellX, cellZ, 1)) * FORMATION_CELL);
        int pz = (int) ((cellZ + 0.2 + 0.6 * SeedMixer.cellUnit(seedF, cellX, cellZ, 2)) * FORMATION_CELL);
        ColumnSample s = new ColumnSample();
        computeColumn(px, pz, s, true, scratch.get());
        double pick = SeedMixer.cellUnit(seedF, cellX, cellZ, 3);
        boolean dry = s.baseTemperature > 0.4 && s.humidity < 0.15;
        int type = 0;
        if (s.height < seaLevel - 2 && s.height > seaLevel - 16 && s.coastCliff > 0.15) {
            type = common ? SEA_STACK : 0;
        } else if (s.height < seaLevel + 1 || s.waterLevel != ColumnSample.NO_WATER || s.river > 0.0 || s.lake > 0.0 || s.volcano > 0.2) {
            return NO_FORMATION;
        } else if (dry && (s.plateau > 0.2 || s.canyon > 0.1 || s.erosion > -0.4) && s.steepness < 0.5) {
            type = pick < 0.4 ? ARCH : HOODOO;
        } else if (!common) {
            return NO_FORMATION;
        } else if ((s.mountain > 0.3 || s.cliff > 0.3) && s.peakness < 0.7) {
            type = pick < 0.6 ? SPIRE : 0;
        } else if (s.coastCliff > 0.3 && s.height > seaLevel + 4) {
            type = pick < 0.5 ? ARCH : 0;
        } else if (s.erosion > -0.2 && s.mountain < 0.1 && pick < 0.12) {
            type = s.humidity < 0.2 ? ARCH : SPIRE;
        }
        if (type == 0) {
            return NO_FORMATION;
        }
        FormationSite f = new FormationSite();
        f.type = type;
        f.x = px;
        f.z = pz;
        f.ground = s.height;
        f.geology = s.geology;
        f.climate = s.baseTemperature > 0.55 && s.humidity < 0.0 ? (s.baseTemperature > 0.75 || s.geology == Geology.SEDIMENTARY ? 2 : 1) : 0;
        f.size = SeedMixer.cellUnit(seedF, cellX, cellZ, 4);
        f.angle = SeedMixer.cellUnit(seedF, cellX, cellZ, 5) * Math.PI;
        f.seed = SeedMixer.hash2(seedF, cellX, cellZ);
        return f;
    }

    public static String formationName(int type) {
        return switch (type) {
            case ARCH -> "arch";
            case SPIRE -> "spire";
            case HOODOO -> "hoodoo";
            case SEA_STACK -> "sea_stack";
            default -> "formation";
        };
    }

    // =========================================================================================
    // Data holders
    // =========================================================================================

    public static final class VolcanoSite {
        public double x;
        public double z;
        public double height;
        public double radius;
        public double craterRadius;
        public double craterDepth;
        public double baseHeight;
        public double waterLevel;
        public boolean lava;
        int ridges;
        double phase;
    }

    public static final class FormationSite {
        public int type;
        public int x;
        public int z;
        public double ground;
        public int geology;
        public int climate;
        public double size;
        public double angle;
        public long seed;
    }

    /**
     * A planned waterfall. {@code (x, z)} is the lip column, the last column of the high ground,
     * and {@code (dirX, dirZ)} the direction of the fall (a unit axis vector). The curtain hangs
     * in the next column; the plunge pool lies at the foot, {@code faceDist} columns out.
     */
    public static final class WaterfallSite {
        public int x;
        public int z;
        public int dirX;
        public int dirZ;
        /** Y of the lip's top block, which is also the stream's water surface. */
        public double top;
        public double bottom;
        public int width;
        public int faceDist;
        public int poolSurface;
        public double poolAcross;
        public double poolAlong;
        public int poolDepth;
        /** Length of the stream feeding the lip. */
        public int channel;
        public long seed;
        double score;
    }

    public static final class LakeSite {
        public boolean active;
        public double x;
        public double z;
        public double radius;
        public double ringRadius;
        public double level;
        public double depth;
        public int type;
        double shapeOffset;
    }

    private static final class Scratch {
        final double[] deriv = new double[2];
        final MountainState mountains = new MountainState();
        final ColumnSample[] cache = new ColumnSample[CACHE_SIZE];
        final long[] cacheKeys = new long[CACHE_SIZE];
        final boolean[] cacheValid = new boolean[CACHE_SIZE];
        double volcanoInfluence;
        double craterInfluence;
        double craterWaterLevel;
        double craterLavaLevel;
        boolean volcanoCraterMode;
        double lakeResult;
        int lakeType;
        double lakeLevel;
        boolean lakeInside;

        Scratch() {
            for (int i = 0; i < CACHE_SIZE; i++) {
                cache[i] = new ColumnSample();
            }
        }
    }
}
