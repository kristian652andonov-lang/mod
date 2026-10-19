package com.terrahaul.worldgen.terrain;

import static com.terrahaul.worldgen.noise.TMath.bump;
import static com.terrahaul.worldgen.noise.TMath.clamp;
import static com.terrahaul.worldgen.noise.TMath.clamp01;
import static com.terrahaul.worldgen.noise.TMath.lerp;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.Fractal;
import com.terrahaul.worldgen.noise.GradientFractal;
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
    private final GradientFractal riverNoise;
    private final GradientFractal riverWarpX;
    private final GradientFractal riverWarpZ;
    private final Fractal riverPresence;
    private final Fractal greatRiver;
    /** Course of the great-river network (zero lines) and where it is present. */
    private final GradientFractal greatCourse;
    private final Fractal greatPresence;
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
        this.riverNoise = new GradientFractal(SeedMixer.derive(seed, ++salt), 3, 2.0, 0.42);
        this.riverWarpX = new GradientFractal(SeedMixer.derive(seed, ++salt), 3, 2.2, 0.45);
        this.riverWarpZ = new GradientFractal(SeedMixer.derive(seed, ++salt), 3, 2.2, 0.45);
        this.riverPresence = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        this.greatRiver = new Fractal(SeedMixer.derive(seed, ++salt), 2, 2.0, 0.5);
        // fixed salts outside the running sequence, so adding them changed no other field
        this.greatCourse = new GradientFractal(SeedMixer.derive(seed, 7101), 3, 2.0, 0.45);
        this.greatPresence = new Fractal(SeedMixer.derive(seed, 7102), 2, 2.0, 0.5);
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
     * Highest planned surface water (lakes, elevated rivers, crater lakes, and streams at their
     * ground) within about 12 blocks of {@code (x, z)}, or {@link ColumnSample#NO_WATER}. The value
     * is memoised in the sample of the column's grid corner, because cave densities and carvers ask
     * for every block of a column. The neighbouring corners are at most three quarts away, so they
     * never share a cache slot with that corner.
     */
    public double nearbyWaterLevel(int x, int z) {
        int bx = x & ~3;
        int bz = z & ~3;
        ColumnSample corner = sample(bx, bz);
        double memo = corner.nearbyWater;
        if (!Double.isNaN(memo)) {
            return memo;
        }
        double best = ColumnSample.NO_WATER;
        for (int dx = -12; dx <= 12; dx += 4) {
            for (int dz = -12; dz <= 12; dz += 4) {
                if (Math.abs(dx) + Math.abs(dz) > 12) {
                    continue;
                }
                ColumnSample s = dx == 0 && dz == 0 ? corner : sample(bx + dx, bz + dz);
                if (s.waterLevel != ColumnSample.NO_WATER && s.waterLevel > best) {
                    best = s.waterLevel;
                }
                if (s.stream > 0.05 && s.height > seaLevel + 1.0 && Math.floor(s.height) > best) {
                    best = Math.floor(s.height);
                }
            }
        }
        corner.nearbyWater = best;
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
        double c = continentValue(wx, wz);
        double rawC = c;
        double archMask = smoothstep(0.12, 0.42, archipelago.fbm(wx / (cs * 1.4), wz / (cs * 1.4)));
        // the ocean's large structures (island regions, abyssal relief, trenches) fade in and out
        // with the low-pass continent field: they are hundreds of blocks high, and the full field can
        // fall from shelf to abyss depths within a hundred blocks, which left their edges as walls
        double cLow = c < -0.12 ? continentLow(wx, wz) : c;
        // islands stay away from the deepest ocean and fade out before the continental shore
        double islandRegion = archMask > 0.0 && c < -0.12
            ? archMask * smoothstep(-1.05, -0.4, cLow) * smoothstep(-0.12, -0.24, c) : 0.0;
        double island = 0.0;
        double islandShelf = ColumnSample.NO_WATER;
        // lower bound of the distance (in blocks) inland from an archipelago island's shore
        double islandInland = 0.0;
        if (islandRegion > 0.0 && c < -0.12) {
            // towards the edge of an archipelago (and the deepest ocean) islands become fewer and
            // smaller rather than lower: lowering them sank whole banks within a few dozen blocks
            // wherever continentalness changes quickly. At the edge the field is lowered so far that
            // neither islands nor their shelves remain.
            double fade = 1.0 - islandRegion;
            double iv = islands.fbm(wx / (ISLAND_SCALE * hs), wz / (ISLAND_SCALE * hs)) - 0.6 * fade - 3.0 * fade * fade * fade;
            island = smoothstep(0.16, 0.44, iv);
            double shore = islandShore(c);
            if (!Double.isNaN(shore)) {
                islandShelf = islandShelf(iv, shore);
                islandInland = Math.max(0.0, (iv - shore) * ISLAND_SCALE * hs / SHELF_FIELD_RATE);
            }
        }
        double mushroom = 0.0;
        if (cLow < -0.7) {
            mushroom = mushroomIsland(x, z) * smoothstep(-0.7, -0.9, cLow);
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
        // the island field lifts an island's ground to sea level within a few blocks of its shore;
        // relief on islands grows towards their interior instead, so hills do not end in cliffs
        double reliefInland = island > 0.0
            ? Math.min(reliefLand, Math.max(smoothstep(-0.2, -0.1, rawC),
                Math.max(smoothstep(0.0, ISLAND_RELIEF_RAMP, islandInland), smoothstep(0.35, 0.95, mushroom)))) : reliefLand;
        double hillW = bump(erosion, -0.6, -0.16, 0.34) * reliefInland * (1.0 - mountain);
        double hillHeight = 0.0;
        if (hillW > 0.0) {
            double hillAmp = 14.0 + 66.0 * (0.5 + 0.5 * hillAmplitude.fbm(wx / (es * 1.8), wz / (es * 1.8)));
            hillShape = 0.5 + 0.5 * hillNoise.erodedFbm(rx / (600.0 * hs), rz / (600.0 * hs), 0.25, d);
            hillHeight = hillW * hillAmp * hillShape * (0.5 + 0.5 * valleyCut);
        }

        // plateaus and windswept uplands are hundreds of blocks high; their extent follows the
        // low-pass erosion field, because the fine octaves would raise them from the lowland within
        // a few dozen blocks, as ramps steeper than any mesa edge. Both belong to continents: on an
        // island they would end at its shore as a wall into the sea.
        double erosionLow = clamp(erosionNoise.fbm(wx / es, wz / es, 3) * 1.35 - 1.4 * beltStrength, -1.0, 1.0);
        double plateauRegion = smoothstep(-0.05, 0.2, plateauHeight.fbm(wx / (es * 1.3) - 40.0, wz / (es * 1.3) + 13.0));
        // the plateau band follows only the first erosion octave, and plateaus give way to mountain
        // ranges (whose blurred mask changes as slowly): stacked on a range's flank, a plateau's
        // edge doubled its relief into one long, steep face
        double plateauBand = bump(erosionNoise.fbm(wx / es, wz / es, 1) * 1.35, -0.52, -0.31, -0.12)
            * smoothstep(-0.04, 0.12, rawC) * (1.0 - smoothstep(0.05, 0.35, mountain)) * plateauRegion;
        double plateau = 0.0;
        double plateauHeightValue = 0.0;
        double plateauEdge = 0.0;
        // height of the plateau's top here, whether or not this column is on its edge
        double plateauTop = 0.0;
        if (plateauBand > 0.0) {
            plateauHeightValue = plateau(rx, rz, wx, wz, plateauBand, hillShape, sc);
            plateau = sc.plateau;
            plateauTop = sc.plateauTop;
            plateauEdge = sc.plateauEdge;
        }

        double windswept = bump(erosionLow, 0.4, 0.5, 0.6) * smoothstep(-0.02, 0.1, rawC) * reliefLand;
        double windHeight = windswept > 0.0
            ? windswept * (40.0 + 85.0 * rangeI) * windsweptNoise.ridged(rx / (430.0 * hs), rz / (430.0 * hs), 1.6)
            : 0.0;

        double plains = (3.6 * plainsNoise.fbm(rx / (720.0 * hs), rz / (720.0 * hs))
            + 1.4 * plainsDetail.fbm(x / (130.0 * hs), z / (130.0 * hs))) * reliefInland;

        double detail = (1.2 + 2.5 * hillW + 7.0 * mountain * shape + 3.0 * windswept) * plainsDetail.fbm(rx / (46.0 * hs) + 300.0, rz / (46.0 * hs) - 120.0);
        double h = base + mountainHeight + hillHeight + plateauHeightValue + windHeight + plains * (1.0 - mountain) + detail * reliefInland;

        // ocean floors: rolling abyssal plains, long ridges and rare seamounts instead of a flat bed;
        // kept well below sea level so no unintended islands appear
        // (low-pass continentalness, raised like cEff where islands stand)
        double cLowEff = cLow + (cEff - c);
        double seaFloor = smoothstep(-0.34, -0.5, cLowEff);
        double oceanRidge = 0.0;
        if (seaFloor > 0.0) {
            double rolling = 7.0 * hillNoise.fbm(x / (240.0 * hs) + 711.0, z / (240.0 * hs) - 403.0);
            double ridge = windsweptNoise.ridged(wx / (1300.0 * hs) - 91.0, wz / (1300.0 * hs) + 57.0, 2.0);
            double seamount = smoothstep(0.6, 0.95, 0.5 + 0.5 * colossalNoise.fbm(wx / (900.0 * hs) + 333.0, wz / (900.0 * hs) - 222.0));
            double seabed = rolling + 34.0 * ridge * ridge + 70.0 * seamount * (0.6 + 0.4 * ridge);
            h = Math.min(h + seaFloor * seabed, Math.max(h, sl - 9.0));
            oceanRidge = seaFloor * smoothstep(0.82, 0.97, ridge);
        }

        // deep-sea trenches (see trench)
        h = trench(h, wx, wz, smoothstep(-0.5, -0.66, cLowEff), sc);
        double trench = sc.trench;
        double trenchFloor = sc.trenchFloor;

        // ---- 7. wetlands ---------------------------------------------------------------------
        // marshes belong to temperate and warm lowlands: in a frozen climate the biome source gives
        // snowy plains, so the landform fades out before the climate gets that cold
        double baseTemp = temperatureBase(x, z);
        double swamp = smoothstep(0.55, 0.72, erosion) * smoothstep(-0.11, 0.02, cEff) * (1.0 - mountain) * (1.0 - plateau)
            * smoothstep(-0.45, -0.32, baseTemp);
        // Marshes fill the low ground within about 30 blocks of sea level, where the water table
        // floods their pools: higher plains and hills next to a marsh keep their height (and are
        // no marsh) instead of being pulled down to it in a wall, and the wetland's flag and swamp
        // biome stay off them. Measured, the old fade kept the flag on plains 35 blocks above the
        // sea, where only one marsh column in ten held water.
        swamp *= smoothstep(sl + 30.0, sl + 12.0, h);
        if (swamp > 0.0) {
            // a patchwork of shallow pools just below sea level (filled by the sea-level water
            // table) between low, grassy hummocks and mud flats, small ponds in the hollows between
            // hummocks, and winding creeks that link the pools
            double pools = swampNoise.fbm(x / (110.0 * hs), z / (110.0 * hs));
            double hummocks = swampNoise.fbm(x / (17.0 * hs) + 40.0, z / (17.0 * hs) - 25.0);
            double creek = Math.abs(swampNoise.fbm(x / (75.0 * hs) - 90.0, z / (75.0 * hs) + 33.0, 2));
            // (about 45% of the marsh core stands in water 1-2 blocks deep; with more weight on the
            // large pool field two thirds did, as open lakes dotted with islets)
            double wet = sl - 0.2 + 1.6 * pools + 1.8 * Math.max(0.0, hummocks) - 1.6 * smoothstep(-0.28, -0.5, hummocks);
            wet = Math.min(wet, sl - 1.8 + 14.0 * creek);
            // the marsh's core lies at the water table, its fringe rises to the land around
            h = lerp(smoothstep(0.0, 0.6, swamp), h, wet);
        }

        // ---- 8. coasts: cliffs and fjords -----------------------------------------------------
        double coastZone = bump(cEff, -0.26, -0.16, -0.05);
        double coastCliff = 0.0;
        if (coastZone > 0.0) {
            double cc = smoothstep(0.05, 0.35, coastCliffNoise.fbm(wx / (1900.0 * hs), wz / (1900.0 * hs)));
            // mountains reaching the coast slope down into the sea instead of being sliced into a
            // sheer wall hundreds of blocks high; islands get beaches and their shelf instead
            coastCliff = cc * coastZone * (0.35 + 0.65 * smoothstep(0.35, -0.5, erosion)) * (1.0 - swamp)
                * (1.0 - smoothstep(0.08, 0.35, mountain)) * (1.0 - smoothstep(0.0, 0.1, island));
            if (coastCliff > 0.0) {
                double top = sl + 9.0 + 48.0 * cc * (0.4 + 0.6 * rangeI) + 30.0 * mountain;
                h = lerp(coastCliff, h, coastCliffProfile(rawC, h, base, top, wx, wz));
            }
        }
        // islands and submerged banks stand on a shelf that slopes down into the surrounding sea
        // floor, or a trench, instead of rising from it as sheer-sided slabs (applied after the
        // coastal cliffs, whose sea floor would cut into it, and before fjords, which cut through it)
        if (islandShelf > h) {
            // the shelf fades with the archipelago: its rise above the sea floor is limited by a
            // budget that shrinks to nothing at the edge of the island region (over hundreds of
            // blocks), where it would otherwise end as a wall above the abyss
            double raise = Math.min(islandShelf - h, SHELF_RAISE * islandRegion);
            trench *= clamp01(1.0 - raise / 40.0);
            h += raise;
        }
        // fjords (see fjord). Towards the sea a trough fades out offshore, where its floor and the
        // sea floor lie at the same depth: fading it with the land's relief left a wall across the
        // trough at the coastline. Archipelagos have none (judged on the slowly varying island
        // region: an island's own field rises within a few blocks of its shore and cut troughs off)
        double fjordMountain = mountainE * smoothstep(-0.36, -0.22, cEff) * (1.0 - islandRegion) * (1.0 - smoothstep(0.0, 0.15, island));
        h = fjordMountain > 0.12 && cEff < FJORD_INLAND ? fjord(h, x, z, wx, wz, cEff, fjordMountain, sc) : h;
        double fjord = fjordMountain > 0.12 && cEff < FJORD_INLAND ? sc.fjord : 0.0;

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
        double baseHum = humidityBase(x, z);
        double aridity = smoothstep(0.1, 0.6, baseTemp) * smoothstep(0.1, -0.5, baseHum);
        // plateaus favour canyons through their band, not their edge mask: the region decides the
        // canyon's depth, which jumped by a hundred blocks across a mesa edge and tilted the floor
        double canyonReg = smoothstep(0.32, 0.6,
            canyonRegion.fbm(wx / (es * 2.2), wz / (es * 2.2)) * 0.9 + 0.25 * aridity + 0.3 * plateauBand + 0.12 * (settings.canyonFrequency() - 1.0))
            * reliefInland * (1.0 - smoothstep(0.02, 0.3, mountain)) * (1.0 - swamp) * (1.0 - smoothstep(0.02, 0.25, volcano));
        if (canyonReg > 0.001) {
            // depth is bounded (the walls widen with it, see canyonWallRise); on high plateaus the
            // floor rises instead. The bound is measured from the land the canyon is cut into with
            // plateau edges filled up to the plateau's top: measured from each column's own height,
            // the floor tilted across canyons that cross an escarpment and their uphill wall became
            // one sheer face instead of a staircase
            h = canyon(h, x, z, wx, wz, canyonReg, base, plains, h - plateauHeightValue + plateauTop, sc);
            canyon = sc.canyon;
            canyonFloor = sc.canyonFloor;
            canyonWall = sc.canyonWall;
        }
        double preWater = h;

        // ---- 11. rivers -------------------------------------------------------------------------
        // Two networks: ordinary rivers along the zero lines of one noise field, and great rivers,
        // 100-200 blocks wide and widening into estuaries, along those of a broader one. A river's
        // surface, wetness and width are read at the foot point of the column on the centre line,
        // so all columns of a cross-section agree: the river descends in small steps that cross the
        // channel (riffles and rapids), never in steps running along it.
        // finding nearby lake sites may compute other columns with this scratch: read what the
        // earlier sections left in it, then collect the lakes before anything else uses it
        double craterWater = sc.craterWaterLevel;
        double craterLava = sc.craterLavaLevel;
        sc.lakeCount = 0;
        if (withLakes) {
            sc.lakeCount = collectLakes(x, z, true, sc, collectLakes(x, z, false, sc, 0));
        }
        double landMask = (1.0 - smoothstep(0.0, 0.35, island)) * (1.0 - smoothstep(0.02, 0.25, volcano));
        RiverTrace great = sc.greatTrace;
        RiverTrace normal = sc.normalTrace;
        great.hit = false;
        normal.hit = false;
        if (landMask > 0.0 && cEff > -0.5) {
            traceGreat(x, z, h, mountain, landMask, great, sc);
            if (great.hit) {
                h = carveGreat(x, z, h, great);
            }
            traceNormal(x, z, h, cEff, mountain, erosion, canyon, canyonFloor, landMask, normal, sc);
            if (normal.hit) {
                h = carveNormal(h, plains, normal);
            }
        }
        riverFields(h, normal, great, out, sc);
        double river = sc.fRiver;
        double riverValley = sc.fValley;
        double riverHalfW = sc.fHalfW;
        double riverDist = sc.fDist;
        double riverLevel = sc.fLevel;
        boolean dry = sc.fDry;
        int bodyKind = sc.fBodyInside ? WaterBody.RIVER : WaterBody.NONE;
        boolean bodyInside = sc.fBodyInside;
        double bodyLevel = sc.fBodyLevel;
        double bodyCont = sc.fBodyCont;
        double waterLevel = bodyInside && bodyLevel > sl ? bodyLevel : ColumnSample.NO_WATER;
        sc.riverWetChannel = bodyInside;
        sc.riverBankGuard = Math.max(bankGuard(normal), bankGuard(great));

        // ---- 12. lakes --------------------------------------------------------------------------
        double lake = 0.0;
        int lakeType = LakeType.NONE;
        double lakeLevel = 0.0;
        if (withLakes) {
            sc.lakeResult = 0.0;
            h = applyLakes(x, z, h, sc);
            if (sc.lakeResult > 0.0) {
                lake = sc.lakeResult;
                lakeType = sc.lakeType;
                lakeLevel = sc.lakeLevel;
                if (sc.lakeInside) {
                    // inside a lake its level wins over any river crossing it: the level was chosen
                    // to match the rivers at the lake's outlet
                    bodyKind = WaterBody.LAKE;
                    bodyInside = true;
                    bodyLevel = Math.max(sl, lakeLevel);
                    bodyCont = bodyLevel;
                    waterLevel = lakeLevel > sl + 0.5 ? lakeLevel : ColumnSample.NO_WATER;
                }
            }
        }
        if (crater > 0.0 && craterWater > sl && h < craterWater && h >= uncarved - 1.0) {
            waterLevel = craterWater;
            lakeType = LakeType.CRATER;
            lake = Math.max(lake, crater);
            lakeLevel = craterWater;
            bodyKind = WaterBody.CRATER;
            bodyInside = true;
            bodyLevel = craterWater;
            bodyCont = craterWater;
        }

        h = TMath.softMax(h, settings.maxTerrainHeight() - 160.0, settings.maxTerrainHeight());
        out.lavaLevel = crater > 0.0 && craterLava != ColumnSample.NO_WATER && h < craterLava ? craterLava : ColumnSample.NO_WATER;
        if (out.lavaLevel != ColumnSample.NO_WATER) {
            bodyKind = WaterBody.LAVA;
            bodyInside = true;
            bodyLevel = craterLava;
            bodyCont = craterLava;
        }
        if (waterLevel != ColumnSample.NO_WATER && waterLevel <= h + 0.25) {
            waterLevel = ColumnSample.NO_WATER;
        }
        out.bodyKind = bodyKind;
        out.bodyInside = bodyInside;
        out.bodyLevel = bodyLevel;
        out.bodyLevelCont = bodyCont;
        out.nearbyWater = Double.NaN;

        // ---- 13. classification and climate ---------------------------------------------------
        out.height = h;
        out.uncarvedHeight = uncarved;
        out.preWaterHeight = preWater;
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
        out.detailTop = Integer.MIN_VALUE;
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
        // wetland pools belong to the swamp biome around them, not to the sea
        boolean seaWater = h < sl - 0.5 && river <= 0.0 && lake <= 0.0 && rawC < -0.05 && swamp < 0.5;
        double cOut;
        if (mushroom > 0.3 && h >= sl - 4.0) {
            cOut = -1.12;
        } else if (seaWater || fjord > 0.5 && h < sl - 2.0) {
            cOut = clamp(Math.min(rawC, -0.2), -1.04, -0.2);
            // deep ocean biomes below the ocean/deep-ocean profile boundary (about Y -25)
            if (h < sl - 88.0) {
                cOut = Math.min(cOut, -0.47);
            }
        } else if (h < sl + 4.5 && cEff < -0.06 && mountain < 0.3 && swamp < 0.5) {
            cOut = clamp(cEff, -0.185, -0.115);
        } else {
            cOut = Math.max(cEff, -0.105);
        }
        out.continentalness = clamp(cOut, -1.2, 1.0);

        double eOut = erosion;
        if (swamp > 0.5) {
            // inland continentalness (above) and the highest erosion band: swamp and mangrove
            // swamp biomes on the marshes
            eOut = Math.max(eOut, 0.56);
        }
        if (plateau > 0.5) {
            eOut = clamp(eOut, -0.37, -0.23);
        }
        if (windswept > 0.5) {
            // windswept biomes on the windswept uplands, which follow the low-pass erosion
            eOut = clamp(eOut, 0.46, 0.54);
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
    // Plateaus, trenches, fjords and canyons (shaping helpers of computeColumn)
    // =========================================================================================

    /**
     * Plateaus (mesas) in a plateau band of strength {@code band}. Inside the plateau field the
     * edge is a mesa rim: a caprock cliff of 12-28 blocks above a concave talus apron a little
     * wider than the plateau is high, instead of a sheer 200-block wall. The distance to the rim
     * is the field's value over a rate that 95% of its gradients stay below: a local estimate
     * (value over gradient) jumps near the field's extrema and raised narrow fins and walls.
     * <p>
     * Where the band itself fades the plateau has no rim to stand behind, so its top is held under
     * a ramp that rises {@value #PLATEAU_RAMP_SLOPE} blocks per block at the band's 95th-percentile
     * rate of change: scaling the whole height with the band raised the tallest plateaus from the
     * lowland at 70 degrees over 200 blocks. The top stays flat (terraced) where the band is strong
     * enough to carry it. Returns the plateau's height above the land around and sets
     * {@code sc.plateau}, {@code sc.plateauTop} (the top here, rim or not) and {@code sc.plateauEdge}.
     */
    private double plateau(double rx, double rz, double wx, double wz, double band, double hillShape, Scratch sc) {
        double es = settings.erosionScale() * hs;
        double ps = 1500.0 * hs;
        double pv = plateauNoise.fbm(rx / ps, rz / ps) + 0.16;
        double pH = 45.0 + 200.0 * (0.5 + 0.5 * plateauHeight.fbm(wx / (es * 2.0), wz / (es * 2.0)));
        double edgeDist = pv * ps / PLATEAU_FIELD_RATE;
        double cap = Math.min(12.0 + 16.0 * (0.5 + 0.5 * plateauHeight.fbm(wx / (420.0 * hs) + 77.0, wz / (420.0 * hs) - 5.0)), 0.22 * pH);
        // the talus is 1.1 times as wide as it is high (54 degrees below the caprock, flattening
        // towards its foot): at 0.85 it reached 70 degrees where the field changes fastest
        double talusW = Math.max(16.0, (pH - cap) * 1.1);
        double pMask;
        if (edgeDist >= 0.0) {
            pMask = 1.0;
        } else if (edgeDist > -talusW) {
            pMask = (1.0 - cap / pH) * Math.pow(1.0 + edgeDist / talusW, 1.5);
        } else {
            pMask = 0.0;
        }
        double ramp = PLATEAU_RAMP_SLOPE * hs / PLATEAU_BAND_RATE * band;
        double top = TMath.terrace(Math.min(pH + 14.0 * hillShape * smoothstep(0.02, 0.15, pv), ramp), 13.0, 0.72);
        sc.plateau = band * pMask;
        sc.plateauTop = top;
        sc.plateauEdge = band * (1.0 - Math.abs(2.0 * pMask - 1.0));
        return pMask * top;
    }

    /**
     * Deep-sea trenches: long rifts in the abyssal plains ({@code abyss} > 0) whose floors drop to
     * about Y -230..-340. The cross-section is a steep-sided valley: a flat floor 40-80 blocks wide,
     * then walls that rise at up to ~58 degrees with benches and flatten towards the rim, so a
     * trench is several hundred blocks across instead of a vertical slot. The surface rule lines
     * them with magma and basalt; the ocean vent feature builds chimneys on the floor. Returns the
     * new ground height and sets {@code sc.trench} and {@code sc.trenchFloor}.
     */
    private double trench(double h, double wx, double wz, double abyss, Scratch sc) {
        sc.trench = 0.0;
        sc.trenchFloor = 0.0;
        if (abyss <= 0.0) {
            return h;
        }
        double ts = 3200.0 * hs;
        double tv = trenchNoise.fbm(wx / ts, wz / ts);
        if (Math.abs(tv) >= 0.16) {
            return h;
        }
        double region = smoothstep(-0.1, 0.25, trenchRegion.fbm(wx / (5200.0 * hs) + 17.0, wz / (5200.0 * hs) - 41.0));
        if (region <= 0.0) {
            return h;
        }
        double tvx = trenchNoise.fbm((wx + 4.0) / ts, wz / ts);
        double tvz = trenchNoise.fbm(wx / ts, (wz + 4.0) / ts);
        double grad = Math.sqrt((tvx - tv) * (tvx - tv) + (tvz - tv) * (tvz - tv)) / 4.0;
        // never over less than the gradient 95% of the field stays below: between two close
        // branches the gradient vanishes and the plain estimate left a thin spike
        grad = Math.max(grad, TRENCH_FIELD_RATE / ts);
        double dist = Math.abs(tv) / grad;
        double drop = (170.0 + 100.0 * (0.5 + 0.5 * trenchRegion.fbm(wx / (1800.0 * hs) - 70.0, wz / (1800.0 * hs) + 25.0)))
            * region * abyss;
        double floorHalf = 20.0 + 20.0 * (0.5 + 0.5 * trenchRegion.fbm(wx / (900.0 * hs) + 5.0, wz / (900.0 * hs) - 9.0));
        double wall = Math.max(40.0, drop * 0.9);
        // the trench only exists while |tv| < 0.16; where that band is narrow the floor and then
        // the depth shrink, so the walls flatten out before it instead of being cut off
        double reach = 0.15 / grad;
        if (floorHalf + wall > reach) {
            floorHalf = Math.max(6.0, Math.min(floorHalf, reach * 0.2));
            double avail = Math.max(0.0, reach - floorHalf);
            if (wall > avail) {
                drop *= avail / wall;
                wall = Math.max(avail, 1.0);
            }
        }
        if (dist >= floorHalf + wall) {
            return h;
        }
        double sw = clamp01((dist - floorHalf) / wall);
        double f = Math.pow(1.0 - sw, 1.3);
        // benches every 18-30 blocks of depth break up the walls
        double step = 18.0 + 12.0 * (0.5 + 0.5 * trenchRegion.fbm(wx / (700.0 * hs) - 31.0, wz / (700.0 * hs) + 12.0));
        double depth = TMath.terrace(drop * f, step, 0.55);
        sc.trenchFloor = Math.max(MIN_TRENCH_FLOOR, h - drop);
        sc.trench = f * smoothstep(0.0, 0.3, region * abyss);
        return Math.max(sc.trenchFloor, h - depth);
    }

    /**
     * Fjords: drowned glacial valleys with a U-shaped cross-section whose width grows with the
     * height of the mountains they cut, so their walls are steep but never a slot. They cut the
     * mountainous coasts of continents ({@code fjordMountain}), not islands. Inland the trough's
     * floor climbs (gently at first, then steeper) until it meets the mountains as a hanging valley;
     * fading the carving out instead left a wall hundreds of blocks high at the head of every fjord.
     * Returns the new ground height and sets {@code sc.fjord}.
     */
    private double fjord(double h, int x, int z, double wx, double wz, double cEff, double fjordMountain, Scratch sc) {
        sc.fjord = 0.0;
        double coldness = smoothstep(0.1, -0.4, temperatureBase(x, z));
        // where the trough runs out of the mountains it fades over a wide range of the mountain
        // mask, which can rise by 0.1 within 15 blocks at the edge of a range: a narrow range made
        // the trough end in a head wall as deep as the fjord. The climate only sets how deep and
        // wide the trough is.
        double presence = smoothstep(0.12, 0.7, fjordMountain);
        double strength = presence * (0.35 + 0.65 * coldness);
        double inland = clamp01((cEff + 0.08) / (FJORD_INLAND + 0.08));
        double floor = seaLevel - 18.0 - 22.0 * strength + 120.0 * inland + 420.0 * inland * inland * inland;
        if (h <= floor) {
            return h;
        }
        double fs = 900.0 * hs;
        double fv = fjordNoise.fbm(wx / fs, wz / fs);
        double fvx = fjordNoise.fbm((wx + 2.0) / fs, wz / fs);
        double fvz = fjordNoise.fbm(wx / fs, (wz + 2.0) / fs);
        double grad = Math.sqrt((fvx - fv) * (fvx - fv) + (fvz - fv) * (fvz - fv)) / 2.0;
        // distance to the trough's axis; never over less than the gradient 95% of the field stays
        // below, so it cannot jump where the gradient vanishes
        double dist = Math.abs(fv) / Math.max(grad, FJORD_FIELD_RATE / fs);
        double floorHalf = 26.0 + 30.0 * strength;
        double wall = Math.max(70.0, (h - floor) * 1.1);
        if (dist >= floorHalf + wall) {
            return h;
        }
        double sw = clamp01((dist - floorHalf) / wall);
        double profile = floor + (h - floor) * (1.0 - (1.0 - sw) * (1.0 - sw));
        sc.fjord = strength * (1.0 - sw) * (1.0 - smoothstep(0.15, 0.5, inland));
        return lerp(presence * (1.0 - smoothstep(0.55, 1.0, inland)), h, profile);
    }

    /**
     * Canyons in a canyon region of strength {@code canyonReg}: walls stepped into the world's
     * strata (see {@link #canyonWallRise}) down to a floor near the lowland level, at most
     * 70-200 blocks below {@code refH}, the land the canyon is cut into. Most canyons are dry;
     * about a quarter are cut down to sea level and carry a stream. Returns the new ground height
     * and sets {@code sc.canyon}, {@code sc.canyonFloor} and {@code sc.canyonWall}.
     */
    private double canyon(double h, int x, int z, double wx, double wz, double canyonReg, double base, double plains, double refH, Scratch sc) {
        sc.canyon = 0.0;
        sc.canyonFloor = 0.0;
        sc.canyonWall = 0.0;
        double cScale = 2600.0 * hs;
        double cv = canyonNoise.fbm(wx / cScale, wz / cScale);
        double cvx = canyonNoise.fbm((wx + 3.0) / cScale, wz / cScale);
        double cvz = canyonNoise.fbm(wx / cScale, (wz + 3.0) / cScale);
        double grad = Math.sqrt((cvx - cv) * (cvx - cv) + (cvz - cv) * (cvz - cv)) / 3.0;
        double dist = Math.abs(cv) / Math.max(grad, CANYON_FIELD_RATE / cScale);
        double width = 0.5 + 0.5 * canyonWidth.fbm(wx / (1600.0 * hs), wz / (1600.0 * hs));
        double floorHalf = (8.0 + 30.0 * width) * Math.sqrt(canyonReg);
        if (dist >= floorHalf + CANYON_REACH) {
            return h;
        }
        double es = settings.erosionScale() * hs;
        boolean wet = canyonRegion.fbm(wx / (es * 0.9) + 61.0, wz / (es * 0.9) - 17.0) > 0.25;
        int sl = seaLevel;
        double floor = Math.max(wet ? sl + 1.0 : sl + 4.0, base + 3.0 + 6.0 * smoothstep(0.0, 1.0, plains));
        floor = Math.max(floor, refH - (70.0 + 130.0 * width) * canyonReg);
        double relief = h - floor;
        if (relief <= 12.0) {
            return h;
        }
        // soft layers weather to 30-45 degree talus slopes, varying along the canyon
        double soft = 0.6 + 0.4 * (0.5 + 0.5 * canyonWidth.fbm(wx / (700.0 * hs) + 40.0, wz / (700.0 * hs) - 15.0));
        // benches wander a little, so the walls are not parallel to the canyon's axis
        double u = dist - floorHalf + 5.0 * canyonWidth.fbm(wx / (55.0 * hs) - 9.0, wz / (55.0 * hs) + 4.0);
        double profile = floor + (u > 0.0 ? canyonWallRise(floor, u, soft, relief + 1.0) : 0.0);
        double canyonW = canyonWallWidth(floor, relief, soft);
        profile = Math.max(profile, canyonButte(x, z, floor, relief, soft, dist, floorHalf));
        if (wet && floor < sl + 1.5 && dist < 1.5 + 2.5 * width) {
            // the stream: a shallow channel below sea level, filled by the water table
            profile = Math.min(profile, sl - 1.0 - 1.5 * (1.0 - dist / (1.5 + 2.5 * width)));
        }
        if (profile >= h) {
            return h;
        }
        double strength = smoothstep(12.0, 30.0, relief);
        double wallT = clamp01(u / Math.max(canyonW, 1.0));
        sc.canyon = strength * (1.0 - smoothstep(0.7, 1.0, wallT));
        sc.canyonFloor = floor;
        sc.canyonWall = strength * smoothstep(0.0, 6.0, u) * (1.0 - smoothstep(0.85, 1.0, wallT));
        return lerp(strength, h, profile);
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
    // Rivers
    // =========================================================================================

    /** Size of the great-river network relative to the ordinary one. */
    private static final double GREAT_SCALE = 3.2;

    /**
     * One river network's influence on a column, filled by {@link #traceNormal} and
     * {@link #traceGreat}. Distances are in blocks; the level is the continuous water surface
     * (exclusive top: the top water block is one below its floor).
     */
    private static final class RiverTrace {
        /** The column lies within this river's valley. */
        boolean hit;
        /** Great network only: width of the flat floodplain beside the channel. */
        double floodW;
        /** Distance to the centre line, as used for the channel. */
        double dist;
        /** Smoothed distance used for the valley (never larger than {@link #dist}). */
        double valleyDist;
        double halfW;
        double valleyW;
        double level;
        /** 1 for a river carrying water, falling to 0 for a dry wash. */
        double wet;
        /** Aridity at the foot point, which decides the wetness. */
        double arid;
        double depth;
        /** Great network only: 0..1 how fully developed the great river is here. */
        double great;
        /**
         * 0..1 strength of the carving: where a river network fades out, its valley becomes
         * shallower and its channel narrower until both vanish, so a river never ends in a wall.
         */
        double fade;

        /** The column is in the river's water channel (not only its valley). */
        boolean inside() {
            return hit && halfW > 1.0 && dist < halfW && wet > 0.2;
        }
    }

    /**
     * Floors of the field gradients used for valley distances: {@code |f| / |grad f|} measures the
     * distance to a zero line well near it, but between two rivers, where the field levels out,
     * and where the domain warp folds the field, the gradient changes quickly and the estimate
     * races ahead of the true distance, squeezing a valley side into a cliff. With the floor the
     * estimate stays smooth. Channels use a floor a quarter as large, which keeps their widths
     * true to within a few percent (ordinary rivers' gradients near their centre lines are
     * mostly 1-4E-3, great rivers' 1.5-10E-4).
     */
    private static final double NORMAL_GRAD_FLOOR = 1.0E-3;
    private static final double GREAT_GRAD_FLOOR = 2.5E-4;
    /** Farthest a great river's valley reaches from its centre line. */
    private static final double GREAT_REACH = 900.0;

    /** Field whose zero lines are the centre lines of great rivers: broad, slow meanders. */
    private double greatField(double x, double z) {
        double gwx = x + 520.0 * riverWarpX.fbm(x / (2600.0 * hs) + 41.7, z / (2600.0 * hs) - 19.3);
        double gwz = z + 520.0 * riverWarpZ.fbm(x / (2600.0 * hs) - 23.1, z / (2600.0 * hs) + 8.9);
        double gs = GREAT_SCALE * settings.riverScale() * hs;
        return greatCourse.fbm(gwx / gs, gwz / gs);
    }

    /**
     * Field whose zero lines are the centre lines of ordinary rivers, with its gradient in world
     * space ({@code sc.grad}).
     */
    private double riverFieldGrad(double x, double z, Scratch sc) {
        return warpedGrad(riverNoise, settings.riverScale() * hs, 280.0, 950.0 * hs, 0.0, 0.0, 9.1, -4.4, x, z, sc);
    }

    /** {@link #greatField} with its gradient in world space ({@code sc.grad}). */
    private double greatFieldGrad(double x, double z, Scratch sc) {
        return warpedGrad(greatCourse, GREAT_SCALE * settings.riverScale() * hs, 520.0, 2600.0 * hs, 41.7, -19.3, -23.1, 8.9, x, z, sc);
    }

    /**
     * A field {@code f(w(p) / scale)} behind the river domain warp
     * {@code w(p) = p + amp * (warpX(p / ws + ox), warpZ(p / ws + oz))}, and its gradient by the
     * chain rule: {@code grad f = J^T grad_w f}, with the warp's Jacobian {@code J}.
     */
    private double warpedGrad(GradientFractal field, double scale, double amp, double ws, double oxX, double oxZ, double ozX, double ozZ,
                              double x, double z, Scratch sc) {
        double[] d = sc.grad;
        double wxv = riverWarpX.fbmGrad(x / ws + oxX, z / ws + oxZ, d);
        double jxx = 1.0 + amp * d[0] / ws;
        double jxz = amp * d[1] / ws;
        double wzv = riverWarpZ.fbmGrad(x / ws + ozX, z / ws + ozZ, d);
        double jzx = amp * d[0] / ws;
        double jzz = 1.0 + amp * d[1] / ws;
        double f = field.fbmGrad((x + amp * wxv) / scale, (z + amp * wzv) / scale, d);
        double fx = d[0] / scale;
        double fz = d[1] / scale;
        d[0] = fx * jxx + fz * jzx;
        d[1] = fx * jxz + fz * jzz;
        return f;
    }

    /** Spacing of the grid the foot-point fields are interpolated from. */
    private static final int FOOT_GRID = 32;

    /**
     * Continentalness, base height, aridity and wetland weight at a river's foot point (the
     * nearest point of its centre line). Everything that decides a river's surface is read there
     * rather than at the column itself, so a whole cross-section shares one level.
     * <p>
     * These fields vary over hundreds of blocks, and the feet of neighbouring columns lie close
     * together on the same centre line, so they are interpolated bilinearly from a 32-block grid
     * whose points each thread keeps in a small cache: a few lookups instead of some fifty noise
     * evaluations per column. {@code full} also fills the wetland weight, the aridity and the
     * great-river field with its gradient (the great rivers need only the continentalness).
     */
    private void riverFoot(double fx, double fz, boolean full, Scratch sc) {
        double gfx = fx / FOOT_GRID;
        double gfz = fz / FOOT_GRID;
        int gx = (int) Math.floor(gfx);
        int gz = (int) Math.floor(gfz);
        double tx = gfx - gx;
        double tz = gfz - gz;
        double c = 0.0;
        double swamp = 0.0;
        double arid = 0.0;
        double great = 0.0;
        double greatX = 0.0;
        double greatZ = 0.0;
        for (int i = 0; i < 4; i++) {
            int ox = i & 1;
            int oz = i >> 1;
            double w = (ox == 0 ? 1.0 - tx : tx) * (oz == 0 ? 1.0 - tz : tz);
            int slot = footGridPoint(gx + ox, gz + oz, full, sc);
            c += w * sc.footGridC[slot];
            swamp += w * sc.footGridSwamp[slot];
            arid += w * sc.footGridArid[slot];
            great += w * sc.footGridGreat[slot];
            greatX += w * sc.footGridGreatX[slot];
            greatZ += w * sc.footGridGreatZ[slot];
        }
        sc.footC = c;
        sc.footBase = TMath.curve(c, PROFILE_C, PROFILE_H);
        sc.footSwamp = swamp;
        sc.footArid = arid;
        sc.footGreat = great;
        sc.footGreatX = greatX;
        sc.footGreatZ = greatZ;
    }

    /** Slot of the thread's foot-grid cache holding grid point {@code (gx, gz)}, computed if needed. */
    private int footGridPoint(int gx, int gz, boolean full, Scratch sc) {
        int slot = (gx & 15) | ((gz & 15) << 4);
        long key = ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
        if (sc.footGridKey[slot] == key && sc.footGridState[slot] >= (full ? 2 : 1)) {
            return slot;
        }
        int x = gx * FOOT_GRID;
        int z = gz * FOOT_GRID;
        double wx = x + 320.0 * warpX.fbm(x / (2600.0 * hs), z / (2600.0 * hs))
            + 24.0 * warpX2.fbm(x / (420.0 * hs), z / (420.0 * hs));
        double wz = z + 320.0 * warpZ.fbm(x / (2600.0 * hs) + 31.7, z / (2600.0 * hs) - 12.4)
            + 24.0 * warpZ2.fbm(x / (420.0 * hs) - 7.1, z / (420.0 * hs) + 3.3);
        double cs = settings.continentScale() * hs;
        double c = continent.fbm(wx / cs, wz / cs) * 2.4 + 0.02;
        if (c < -0.45) {
            c -= 0.28 * smoothstep(-0.45, -1.05, c);
        }
        sc.footGridC[slot] = c;
        sc.footGridSwamp[slot] = 0.0;
        sc.footGridArid[slot] = 0.0;
        sc.footGridGreat[slot] = 0.0;
        sc.footGridGreatX[slot] = 0.0;
        sc.footGridGreatZ[slot] = 0.0;
        if (full) {
            // the great-river field and its gradient, to grade the river to a great one
            sc.footGridGreat[slot] = greatFieldGrad(x, z, sc);
            sc.footGridGreatX[slot] = sc.grad[0];
            sc.footGridGreatZ[slot] = sc.grad[1];
            double es = settings.erosionScale() * hs;
            double bs = es * 2.5;
            double beltLine = 1.0 - Math.abs(beltNoise.fbm(wx / bs, wz / bs)) * 4.5;
            double belt = smoothstep(0.0, 1.0, beltLine) * smoothstep(-0.25, 0.2, beltSegments.fbm(wx / (bs * 0.8), wz / (bs * 0.8)));
            double erosion = clamp(erosionNoise.fbm(wx / es, wz / es) * 1.35 - 1.4 * belt, -1.0, 1.0);
            // wetlands lie at sea level: rivers descend to it before they reach one (a little
            // earlier than the wetland itself begins) instead of crossing it on a raised bank
            sc.footGridSwamp[slot] = smoothstep(0.38, 0.58, erosion);
            sc.footGridArid[slot] = smoothstep(0.1, 0.6, temperatureBase(x, z)) * smoothstep(0.1, -0.5, humidityBase(x, z));
        }
        sc.footGridKey[slot] = key;
        sc.footGridState[slot] = (byte) (full ? 2 : 1);
        return slot;
    }

    /**
     * Lowest the ground may be cut (by a lake's shore) on the banks of an elevated river, so the
     * river is never left standing above its surroundings; {@code -infinity} elsewhere.
     */
    private double bankGuard(RiverTrace t) {
        double top = Math.floor(t.level);
        if (!t.hit || t.wet <= 0.15 || top <= seaLevel || t.valleyDist >= t.halfW + 0.3 * t.valleyW) {
            return Double.NEGATIVE_INFINITY;
        }
        return top + 1.0;
    }

    /**
     * Rivers descend to the level of the lakes near them, about one block per 50 blocks from the
     * shore, so a river reaching or passing a lake meets it at its level: never a waterfall
     * along a shore. Lake levels are chosen from the rivers' ungraded levels, so a lake is never
     * above a river it touches.
     */
    private double gradeToLakes(double level, double fx, double fz, Scratch sc) {
        for (int i = 0; i < sc.lakeCount; i++) {
            LakeSite lake = sc.lakeBuf[i];
            double d = Math.hypot(fx - lake.x, fz - lake.z) - lake.radius * 1.42;
            // the grading eases out before the edge of the range in which lakes are collected,
            // so the level does not jump there
            double weight = smoothstep(LAKE_GRADE_RANGE - 100.0, 0.55 * LAKE_GRADE_RANGE, d);
            level -= weight * Math.max(0.0, level - (lake.level + 0.02 * Math.max(0.0, d)));
        }
        return level;
    }

    /**
     * Rivers descend to a great river they approach, about one block per 50 blocks, so they join
     * it at its level. Everything is read at the river's foot point and at the great river's
     * nearest point to it, so the whole cross-section is graded alike, and the grading eases out
     * with distance and where the great river fades out: the level never jumps.
     */
    private double gradeToGreat(double level, double fx, double fz, Scratch sc) {
        if (level <= seaLevel) {
            return level;
        }
        // the great field at the foot, interpolated by riverFoot like the other foot fields
        double gv = sc.footGreat;
        double gx = sc.footGreatX;
        double gz = sc.footGreatZ;
        double g2 = gx * gx + gz * gz + GREAT_GRAD_FLOOR * GREAT_GRAD_FLOOR;
        double distG = Math.abs(gv) / Math.sqrt(g2);
        double weight = smoothstep(GREAT_REACH - 100.0, 0.55 * GREAT_REACH, distG);
        if (weight <= 0.0) {
            return level;
        }
        double gfx = fx - gv * gx / g2;
        double gfz = fz - gv * gz / g2;
        riverFoot(gfx, gfz, false, sc);
        double cG = sc.footC;
        double gs = GREAT_SCALE * settings.riverScale() * hs;
        double mask = smoothstep(-0.55, -0.2, greatPresence.fbm(gfx / (1.4 * gs), gfz / (1.4 * gs))) * smoothstep(1.0, 0.72, cG);
        weight *= smoothstep(0.0, 0.06, mask) * smoothstep(-0.3, -0.17, cG);
        return level - weight * Math.max(0.0, level - (seaLevel + 0.02 * Math.max(0.0, distG - 60.0)));
    }

    /**
     * The river fields of a column (into {@code sc.f*}, and the great-river weight and aridity
     * into {@code out}): the river whose channel the column is in, or is closest to relative to
     * its width, sets them. A column is in a river's water if it is inside the channel of either
     * river, wet enough.
     */
    private void riverFields(double h, RiverTrace normal, RiverTrace great, ColumnSample out, Scratch sc) {
        sc.fRiver = 0.0;
        sc.fValley = 0.0;
        sc.fHalfW = 0.0;
        sc.fDist = 1.0E9;
        sc.fLevel = seaLevel;
        sc.fDry = false;
        sc.fBodyInside = false;
        sc.fBodyLevel = ColumnSample.NO_WATER;
        sc.fBodyCont = ColumnSample.NO_WATER;
        out.riverGreat = 0.0;
        out.riverAridity = 0.0;
        if (!normal.hit && !great.hit) {
            return;
        }
        double relN = normal.hit ? normal.dist / normal.halfW : Double.MAX_VALUE;
        double relG = great.hit ? great.dist / great.halfW : Double.MAX_VALUE;
        RiverTrace dom = relN <= relG ? normal : great;
        RiverTrace other = dom == normal ? great : normal;
        sc.fHalfW = dom.halfW;
        sc.fDist = dom.dist;
        sc.fLevel = Math.floor(dom.level);
        sc.fValley = (1.0 - clamp01((dom.valleyDist - dom.halfW) / dom.valleyW)) * dom.fade;
        sc.fDry = dom.wet <= 0.15 && dom.level > seaLevel + 1.0;
        out.riverGreat = dom == great ? great.great : 0.0;
        out.riverAridity = dom.arid;
        // (only where the channel is carved down to the river: where a valley fades out, its
        // shallow bed is no channel)
        if (dom.halfW > 1.0 && dom.dist < dom.halfW && h < dom.level + 1.0) {
            sc.fRiver = 1.0 - dom.dist / dom.halfW;
        }
        RiverTrace wetChannel = dom.inside() ? dom : other.hit && other.inside() ? other : null;
        if (wetChannel != null) {
            sc.fBodyInside = true;
            sc.fBodyCont = Math.max(seaLevel, wetChannel.level);
            sc.fBodyLevel = Math.floor(sc.fBodyCont);
        }
    }

    /** Water surface of a river whose foot point was evaluated by {@link #riverFoot}. */
    private double footLevel(Scratch sc, double baseOffset) {
        return seaLevel + 0.75 * Math.max(0.0, sc.footBase - baseOffset) * (1.0 - sc.footSwamp);
    }

    /** Wetness of a river at a given level: rivers above sea level run dry in arid climates. */
    private double footWetness(double arid, double level) {
        return level <= seaLevel + 1.0 ? 1.0 : smoothstep(0.62, 0.48, arid);
    }

    /**
     * Great rivers: 100-200 blocks wide inland, widening to an estuary of up to about 380 blocks at
     * the coast, at sea level over their whole course (the water table fills them), in a
     * floodplain several hundred blocks wide. Where the network fades out (inland, towards
     * mountains or where it is absent), the channel narrows and the valley grows shallower over a
     * few hundred blocks; the dry head of the valley rises to the land around it.
     */
    private void traceGreat(int x, int z, double h, double mountain, double landMask, RiverTrace t, Scratch sc) {
        if (mountain >= 0.75) {
            // the valley has faded out completely (see below)
            return;
        }
        double gv = greatFieldGrad(x, z, sc);
        double gx = sc.grad[0];
        double gz = sc.grad[1];
        double g2 = gx * gx + gz * gz + GREAT_GRAD_FLOOR * GREAT_GRAD_FLOOR;
        double glen = Math.sqrt(g2);
        double dist = Math.abs(gv) / glen;
        if (dist > GREAT_REACH) {
            return;
        }
        double channelDist = Math.abs(gv) / Math.sqrt(gx * gx + gz * gz + GREAT_GRAD_FLOOR * GREAT_GRAD_FLOOR / 16.0);
        double fx = x - gv * gx / g2;
        double fz = z - gv * gz / g2;
        // a great river is always wet and at sea level: of its foot point only the continentalness counts
        riverFoot(fx, fz, false, sc);
        double cG = sc.footC;
        double gs = GREAT_SCALE * settings.riverScale() * hs;
        // lowlands only, from far inland down to the coast, where they end in the shallow sea; the
        // width follows only these slowly varying fields, so the valley never narrows abruptly
        double mask = smoothstep(-0.55, -0.2, greatPresence.fbm(fx / (1.4 * gs), fz / (1.4 * gs))) * smoothstep(1.0, 0.72, cG);
        // towards mountains, islands and volcanoes, and where the course runs out to sea, the
        // valley grows shallower instead (the fields involved change too quickly to narrow a valley
        // this wide without cutting a wall into its side)
        double fade = smoothstep(0.0, 0.06, mask) * smoothstep(-0.3, -0.17, cG) * (1.0 - smoothstep(0.2, 0.75, mountain)) * landMask;
        if (fade <= 0.0) {
            return;
        }
        double w = smoothstep(0.0, 0.3, mask);
        double rs = settings.riverScale() * hs;
        double halfW = settings.riverWidth() * (54.0 + 30.0 * smoothstep(0.45, 0.0, cG) + 8.0 * greatRiver.fbm(fx / (0.7 * rs), fz / (0.7 * rs)))
            * w * (1.0 + 0.8 * smoothstep(-0.08, -0.16, cG));
        t.halfW = halfW;
        t.level = seaLevel;
        double valleyW = (1.5 * halfW + 160.0) * w + 26.0;
        t.floodW = 0.55 * valleyW;
        // the valley walls rise no steeper than about 32 degrees, so a great river reaching higher
        // ground cuts a wide valley into it instead of a gorge with sheer sides
        t.valleyW = t.floodW + Math.max(0.45 * valleyW, Math.min(1.6 * (h - t.level - 2.0), 400.0));
        t.wet = 1.0;
        t.arid = 0.0;
        t.depth = Math.min(4.0 + 0.06 * halfW, 14.0);
        t.great = w;
        t.dist = channelDist;
        t.valleyDist = dist;
        t.fade = fade;
        t.hit = dist < halfW + t.valleyW;
    }

    /**
     * Ordinary rivers, 6-30 blocks wide in valleys of their own. Their level follows the land
     * (continentalness) at the foot point, descends to sea level near coasts and wetlands, is
     * graded to a great river they join (about one block per 50) and drops to a canyon's floor
     * where the river reaches a canyon, which makes a river-wide waterfall at the canyon's rim.
     */
    private void traceNormal(int x, int z, double h, double cEff, double mountain, double erosion, double canyon, double canyonFloor, double landMask,
                             RiverTrace t, Scratch sc) {
        double rs = settings.riverScale() * hs;
        double pf = riverPresence.fbm(x / (rs * 1.9), z / (rs * 1.9));
        // the valley fades out only where the channel has narrowed away, and more gently: the
        // water runs until the river is a trickle (at its source, or high in the mountains), then
        // a dry valley carries on and grows shallower over several hundred blocks
        double fade = smoothstep(-0.7, -0.4, pf) * (1.0 - smoothstep(0.55, 1.0, mountain)) * landMask;
        if (fade <= 0.0) {
            return;
        }
        // full width up to the shore, so rivers open into the sea instead of pinching out
        double mask = smoothstep(-0.5, -0.18, pf) * smoothstep(-0.2, -0.14, cEff) * (1.0 - smoothstep(0.42, 0.8, mountain)) * landMask;
        double halfW = settings.riverWidth() * mask * (3.0 + 12.0 * smoothstep(0.55, -0.12, cEff)) * (1.0 - 0.55 * mountain);
        double valleyW0 = halfW * 2.2 + 26.0 + 70.0 * smoothstep(0.1, 0.5, erosion) + 40.0 * mountain;
        double rv = riverFieldGrad(x, z, sc);
        double gx = sc.grad[0];
        double gz = sc.grad[1];
        double g2 = gx * gx + gz * gz + NORMAL_GRAD_FLOOR * NORMAL_GRAD_FLOOR;
        double dist = Math.abs(rv) / Math.sqrt(g2);
        // valley sides no steeper than about 32 degrees where the river cuts into high ground (the
        // level is at least sea level, so this bound holds before the level is known)
        if (dist >= halfW + Math.max(valleyW0, Math.min(1.6 * (h - seaLevel - 2.0), 360.0))) {
            return;
        }
        double fx = x - rv * gx / g2;
        double fz = z - rv * gz / g2;
        riverFoot(fx, fz, true, sc);
        double level = footLevel(sc, 74.5);
        // (read before grading, which evaluates the great river's foot point with the same scratch)
        double arid = sc.footArid;
        level = gradeToGreat(level, fx, fz, sc);
        // (from where the canyon begins to lower the ground: the river falls over the rim, and its
        // banks are never cut by the canyon wall below its surface)
        if (canyon > 0.04) {
            level = Math.min(level, canyonFloor + 1.5);
        }
        level = gradeToLakes(level, fx, fz, sc);
        level = Math.max(seaLevel, level);
        double valleyW = Math.max(valleyW0, Math.min(1.6 * (h - level - 2.0), 360.0));
        if (dist >= halfW + valleyW) {
            return;
        }
        t.hit = true;
        t.dist = Math.abs(rv) / Math.sqrt(gx * gx + gz * gz + NORMAL_GRAD_FLOOR * NORMAL_GRAD_FLOOR / 16.0);
        t.valleyDist = dist;
        t.halfW = halfW;
        t.valleyW = valleyW;
        t.level = level;
        t.wet = footWetness(arid, t.level);
        t.arid = arid;
        // the channel shallows to nothing as the river narrows towards its source
        t.depth = Math.min(2.5 + 0.13 * halfW, 16.0) * smoothstep(1.0, 3.0, halfW);
        t.great = 0.0;
        t.fade = fade;
    }

    /**
     * Height of a river bed below the water surface (negative) by wetness: a dry wash, then a
     * gravel bar rising just above the surface where the water ends (it holds the water back),
     * then a channel deepening to its full depth.
     */
    private static double bedOffset(double wet, double depth) {
        if (wet <= 0.15) {
            return lerp(wet / 0.15, -1.5, 0.6);
        }
        if (wet <= 0.3) {
            return lerp((wet - 0.15) / 0.15, 0.6, -1.0);
        }
        return lerp((wet - 0.3) / 0.7, -1.0, -depth);
    }

    /** Valley, banks and channel of an ordinary river. */
    private double carveNormal(double h0, double plains, RiverTrace t) {
        double h = h0;
        double level = t.level;
        double top = Math.floor(level);
        double tt = clamp01((t.valleyDist - t.halfW) / t.valleyW);
        // a short floodplain with a low natural levee along the bank
        double bank = level + 1.0 + 1.6 * (0.5 + 0.5 * plains / 5.0) + 0.8 * bump(tt, 0.0, 0.05, 0.15);
        double profile = bank + (h - bank) * Math.pow(smoothstep(0.08, 1.0, tt), 0.85);
        if (h > profile) {
            h = profile;
        }
        if (top > seaLevel && t.wet > 0.15 && tt < 0.25 && t.dist >= t.halfW) {
            // banks hold water above sea level
            h = Math.max(h, top + 1.0);
        }
        if (t.halfW > 1.0 && t.dist < t.halfW) {
            double u = t.dist / t.halfW;
            h = Math.min(h, level + bedOffset(t.wet, t.depth) * Math.pow(1.0 - u * u, 0.6));
        }
        return lerp(t.fade, h0, h);
    }

    /** Floodplain, levees, oxbow ponds, channel, sand bars and islands of a great river. */
    private double carveGreat(int x, int z, double h0, RiverTrace t) {
        double h = h0;
        double level = t.level;
        double top = Math.floor(level);
        double out = t.valleyDist - t.halfW;
        double f = out / t.floodW;
        // a wide, nearly flat floodplain with a low natural levee along the bank
        double flood = level + 0.7 + 1.5 * (0.5 + 0.5 * plainsDetail.fbm(x / (90.0 * hs) + 77.0, z / (90.0 * hs) - 31.0))
            + 1.0 * bump(f, 0.0, 0.05, 0.14);
        double profile = out < t.floodW ? flood : flood + (h - flood) * Math.pow(smoothstep(t.floodW, t.valleyW, out), 1.2);
        if (h > profile) {
            h = profile;
        }
        if (top <= seaLevel + 2.0) {
            // backswamps: oxbow ponds a little below sea level on the floodplain, which the water
            // table fills
            double pond = smoothstep(0.42, 0.62, swampNoise.fbm(x / (140.0 * hs) + 12.0, z / (140.0 * hs) + 55.0)) * bump(f, 0.15, 0.5, 0.95);
            if (pond > 0.0) {
                h = Math.min(h, lerp(pond, h, seaLevel - 2.2));
            }
        }
        if (top > seaLevel && t.wet > 0.15 && t.dist >= t.halfW && f < 0.3) {
            h = Math.max(h, top + 1.0);
        }
        if (t.dist < t.halfW) {
            double u = t.dist / t.halfW;
            double bed = level + bedOffset(t.wet, t.depth) * Math.pow(1.0 - u * u, 0.6);
            // sand bars, some rising a block above the water as islands
            double bar = smoothstep(0.42, 0.8, plainsDetail.fbm(x / (45.0 * hs) + 211.0, z / (45.0 * hs) - 97.0)) * bump(u, 0.1, 0.5, 0.9);
            if (bar > 0.0) {
                bed = Math.max(bed, lerp(bar, bed, level - 1.0 + 1.8 * bar));
            }
            h = Math.min(h, bed);
        }
        return lerp(t.fade, h0, h);
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

    /**
     * The stream through {@code (x, z)}: {@code out[0]} is the distance to its centre line and
     * {@code out[1]} the exclusive top of its water. The surface is read at the foot point on the
     * centre line and rounded down to a multiple of 1, 2 or 3 blocks by the slope there, so each
     * cross-section is level and the stream descends as a chain of pools with small falls between
     * them: riffles on gentle slopes, step pools on steep ones. Must not be called while a column
     * is being computed (it samples the terrain).
     */
    public void streamInfo(int x, int z, double[] out) {
        double wx = x + 320.0 * warpX.fbm(x / (2600.0 * hs), z / (2600.0 * hs))
            + 24.0 * warpX2.fbm(x / (420.0 * hs), z / (420.0 * hs));
        double wz = z + 320.0 * warpZ.fbm(x / (2600.0 * hs) + 31.7, z / (2600.0 * hs) - 12.4)
            + 24.0 * warpZ2.fbm(x / (420.0 * hs) - 7.1, z / (420.0 * hs) + 3.3);
        double rx = x + 0.22 * (wx - x);
        double rz = z + 0.22 * (wz - z);
        double ms = settings.mountainScale() * hs * 1.8;
        double v = valleyNoise.fbm(rx / ms, rz / ms);
        double gx = valleyNoise.fbm((rx + 1.0) / ms, rz / ms) - v;
        double gz = valleyNoise.fbm(rx / ms, (rz + 1.0) / ms) - v;
        double g2 = Math.max(gx * gx + gz * gz, 1.0E-18);
        double glen = Math.sqrt(g2);
        out[0] = Math.abs(v) / glen;
        double fx = x - v * gx / g2;
        double fz = z - v * gz / g2;
        double tx = -gz / glen;
        double tz = gx / glen;
        double hf = heightAt(fx, fz);
        double slope = Math.abs(heightAt(fx + 3.0 * tx, fz + 3.0 * tz) - heightAt(fx - 3.0 * tx, fz - 3.0 * tz)) / 6.0;
        int q = slope < 0.3 ? 1 : slope < 0.7 ? 2 : 3;
        out[1] = q * Math.floor(hf / q);
    }

    /** {@link #interpolatedHeight} at a fractional position. */
    private double heightAt(double x, double z) {
        int x0 = ((int) Math.floor(x)) & ~3;
        int z0 = ((int) Math.floor(z)) & ~3;
        double fx = (x - x0) / 4.0;
        double fz = (z - z0) / 4.0;
        double h00 = sample(x0, z0).height;
        double h10 = sample(x0 + 4, z0).height;
        double h01 = sample(x0, z0 + 4).height;
        double h11 = sample(x0 + 4, z0 + 4).height;
        return lerp(fz, lerp(fx, h00, h10), lerp(fx, h01, h11));
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
                double craterWater = ColumnSample.NO_WATER;
                double craterLava = ColumnSample.NO_WATER;
                if (r < v.craterRadius) {
                    double u = r / v.craterRadius;
                    profile = v.baseHeight + rimHeight - v.craterDepth * (1.0 - u * u);
                    craterT = 1.0 - u;
                    sc.volcanoCraterMode = true;
                    if (v.waterLevel != ColumnSample.NO_WATER) {
                        craterWater = v.waterLevel;
                    } else if (v.lava) {
                        craterLava = Math.floor(v.baseHeight + rimHeight - v.craterDepth * 0.82);
                    }
                }
                if (profile > best) {
                    best = profile;
                    sc.volcanoInfluence = smoothstep(1.0, 0.55, t);
                    sc.craterInfluence = craterT;
                    // only the crater of the volcano that shapes this column may flood it; where
                    // a neighbouring cone overlaps a crater, its flank is not under the crater lake
                    sc.craterWaterLevel = craterWater;
                    sc.craterLavaLevel = craterLava;
                }
            }
        }
        if (sc.volcanoInfluence <= 0.0) {
            sc.volcanoCraterMode = false;
        }
        return best;
    }

    /**
     * Key of a site cell in the site caches (volcanoes, lakes, waterfalls, rock formations).
     * {@link SeedMixer#hash2} is no key: it gives the cells (x, z) and (-x, -z) the same value for
     * about a third of all cells, so whichever of the two was computed first was returned for both,
     * and lakes, volcanoes and waterfalls depended on the order in which chunks were generated
     * (measured after scanning two 16k-block areas: 3-6% of the lake cells held the site of their
     * mirror cell, which made the terrain differ between runs and lakes or waterfalls vanish).
     */
    private static long siteKey(long gridSeed, int cx, int cz) {
        return SeedMixer.mix(((long) cx << 32 | cz & 0xFFFFFFFFL) ^ gridSeed);
    }

    private VolcanoSite volcanoSite(int cx, int cz, double cell) {
        long key = siteKey(volcanoSeed, cx, cz);
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
        return continentValue(wx, wz);
    }

    /**
     * Low-pass continentalness (the three lowest octaves of {@link #continentValue}), which changes
     * several times more slowly than the full field.
     */
    private double continentLow(double wx, double wz) {
        double cs = settings.continentScale() * hs;
        double c = continent.fbm(wx / cs, wz / cs, 3) * 2.4 + 0.02;
        if (c < -0.45) {
            c -= 0.28 * smoothstep(-0.45, -1.05, c);
        }
        return c;
    }

    /** Continentalness at warped coordinates, before islands are added. */
    private double continentValue(double wx, double wz) {
        double cs = settings.continentScale() * hs;
        double c = continent.fbm(wx / cs, wz / cs) * 2.4 + 0.02;
        if (c < -0.45) {
            c -= 0.28 * smoothstep(-0.45, -1.05, c);
        }
        return c;
    }

    // =========================================================================================
    // Coasts and islands
    // =========================================================================================

    /** Feature size of the archipelago island field. */
    private static final double ISLAND_SCALE = 560.0;
    /**
     * Steepest change of the island field per {@link #ISLAND_SCALE} blocks (measured over 200,000
     * points: median 3.5, 95th percentile 7.1, maximum 13.1).
     */
    private static final double SHELF_FIELD_RATE = 13.4;
    /**
     * Change of the plateau field per 1500 blocks that 95% of its gradients stay below (measured
     * for a 4-octave field: median 3.3, 95th percentile 6.6).
     */
    private static final double PLATEAU_FIELD_RATE = 6.6;
    /**
     * Change of the plateau band per block that 95% of its gradients stay below where it fades
     * (measured over 40,000 points of two seeds: median 0.0045, 95th percentile 0.010, maximum 0.025).
     */
    private static final double PLATEAU_BAND_RATE = 0.010;
    /** Steepest rise of a plateau's top where its band fades, in blocks per block (about 60 degrees). */
    private static final double PLATEAU_RAMP_SLOPE = 1.8;
    /** Change of the trench field per 3200 blocks that 95% of its gradients stay below (2 octaves). */
    private static final double TRENCH_FIELD_RATE = 5.6;
    /** Change of the canyon field per 2600 blocks that 95% of its gradients stay below (3 octaves). */
    private static final double CANYON_FIELD_RATE = 5.9;
    /** Continentalness at which fjord troughs end inland. */
    private static final double FJORD_INLAND = 0.24;
    /** Change of the fjord field per 900 blocks that 95% of its gradients stay below (3 octaves). */
    private static final double FJORD_FIELD_RATE = 5.9;
    /** Continentalness of the foot of a coastal cliff. */
    private static final double CLIFF_LINE = -0.17;
    /**
     * The sea floor around an island or submerged bank. The island field lifts the ground from the
     * ocean floor to sea level within only 10-30 blocks at an island's edge, which made islands
     * sheer-sided slabs standing on the ocean floor (or in a trench). Instead, the shelf descends
     * from the island's shore (where the island's base height reaches two blocks below sea level),
     * deepening with the distance from it, gently at first and then at up to 45 degrees: islands
     * sit on a shallow platform whose flanks descend into the abyss. The distance is the field's shortfall below the shore value over the steepest
     * gradient the field reaches, a lower bound of the true distance that changes by at most a
     * block per block, so the shelf's slope is bounded everywhere (a local estimate, shortfall over
     * the local gradient, jumps wherever the field curves). The shelf never rises above two blocks
     * below sea level, so the land of the islands is untouched.
     */
    private double islandShelf(double iv, double shore) {
        double level = seaLevel - 2.0;
        if (iv >= shore) {
            return level;
        }
        double k = (shore - iv) * ISLAND_SCALE * hs / SHELF_FIELD_RATE;
        // a shallow platform (slope 0.4) that steepens to at most 45 degrees: a quadratic flank
        // reached 75 degrees a hundred blocks down and stood as a wall above the abyss
        if (k < SHELF_BREAK) {
            return level - (0.4 * k + 0.01 * k * k);
        }
        return level - (0.4 * SHELF_BREAK + 0.01 * SHELF_BREAK * SHELF_BREAK) - (k - SHELF_BREAK);
    }

    /**
     * Island-field value of the shore of archipelago islands at continentalness {@code c}: where
     * an island's base height reaches two blocks below sea level, or {@code NaN} where islands
     * here stay submerged.
     */
    private double islandShore(double c) {
        double level = seaLevel - 2.0;
        if (islandBase(c, 0.44) < level) {
            return Double.NaN;
        }
        // the island's base height grows with the island field: bisect for the shore
        double lo = 0.16;
        double hi = 0.44;
        for (int i = 0; i < 14; i++) {
            double mid = 0.5 * (lo + hi);
            if (islandBase(c, mid) < level) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    /** Distance from the shore (in blocks of the shelf's distance bound) where the shelf reaches 45 degrees. */
    private static final double SHELF_BREAK = 30.0;
    /**
     * Distance (in blocks of the shelf's distance bound) inland from an island's shore over which
     * its hills and plains rise to their full height: the island field itself can climb from the
     * shore to the island's core within a few blocks, which stood the hills at the shore as cliffs.
     */
    private static final double ISLAND_RELIEF_RAMP = 90.0;
    /** Largest rise of an island shelf above the sea floor, in the core of an archipelago. */
    private static final double SHELF_RAISE = 520.0;

    /** Base (continentalness profile) height of an island at island-field value {@code iv}. */
    private static double islandBase(double c, double iv) {
        double isl = smoothstep(0.16, 0.44, iv);
        double ce = Math.max(c, lerp(smoothstep(0.0, 0.3, isl), c, -0.3 + 0.55 * isl));
        return TMath.curve(ce, PROFILE_C, PROFILE_H);
    }

    /**
     * Ground of a cliffed coast at continentalness {@code c} (continental coasts only). The distance
     * to the cliff line is measured in blocks from the gradient of the continent field, so the shape
     * does not depend on how fast continentalness changes. Seaward lies the sea floor; landward
     * a wave-cut face of at most 22-38 blocks rises from it, then (on higher coasts) a narrow bench
     * and a steep but not sheer slope up to {@code top}, which falls back gently inland. Land that
     * is naturally higher near the cliff is held under the same slope-over-wall envelope, so no
     * plateau or hill meets the sea as a wall taller than the wave-cut face.
     */
    private double coastCliffProfile(double c, double h, double base, double top, double wx, double wz) {
        double gx = continentValue(wx + 4.0, wz) - c;
        double gz = continentValue(wx, wz + 4.0) - c;
        double grad = Math.sqrt(gx * gx + gz * gz) / 4.0;
        // coves and headlands: the cliff line wanders up to ~15 blocks either side of the smooth
        // continental iso-line; the offset changes by less than a block per block (for 95% of
        // the noise's gradients), so the distance never folds back into narrow fins
        // (the gradient floor keeps the distance from blowing up at the tips of peninsulas, where the
        // continental field has an extremum)
        double d = (c - CLIFF_LINE) / Math.max(grad, 2.5E-4 / hs)
            + 12.0 * coastCliffNoise.fbm(wx / (160.0 * hs) - 7.0, wz / (160.0 * hs) + 3.0)
            + 4.0 * coastCliffNoise.fbm(wx / (50.0 * hs) + 13.0, wz / (50.0 * hs) - 21.0);
        if (d <= 0.0) {
            return Math.min(h, base);
        }
        double foot = Math.min(base, seaLevel - 4.0);
        double n1 = 0.5 + 0.5 * coastCliffNoise.fbm(wx / (230.0 * hs) + 41.0, wz / (230.0 * hs) - 73.0);
        double n2 = 0.5 + 0.5 * coastCliffNoise.fbm(wx / (90.0 * hs) - 17.0, wz / (90.0 * hs) + 29.0);
        double total = Math.max(0.0, top - foot);
        double faceH = Math.min(total, 22.0 + 16.0 * n1);
        double faceW = faceH / 5.0;
        double rest = total - faceH;
        double bench = rest > 6.0 ? 2.0 + 7.0 * n2 : 0.0;
        double slopeW = rest / 1.05;
        double raise;
        double cap;
        if (d < faceW) {
            raise = foot + faceH * d / faceW;
            cap = raise;
        } else if (d < faceW + bench) {
            raise = foot + faceH + 0.3 * (d - faceW);
            cap = raise;
        } else {
            double u = d - faceW - bench;
            double benchTop = foot + faceH + 0.3 * bench;
            cap = benchTop + 1.05 * u;
            raise = u < slopeW ? benchTop + (top - benchTop) * smoothstep(0.0, 1.0, u / slopeW)
                : top - 0.06 * (u - slopeW);
        }
        return Math.min(Math.max(h, raise), cap);
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
    // Canyons: strata, walls and buttes
    // =========================================================================================

    /** Farthest a canyon wall reaches beyond the edge of the canyon floor, in blocks. */
    private static final double CANYON_REACH = 240.0;
    /** Mean thickness of the horizontal rock layers canyon walls are cut into. */
    private static final double STRATUM = 11.0;
    /** Slope of the resistant layers that stand as cliffs (blocks per block, about 78 degrees). */
    private static final double HARD_SLOPE = 4.8;
    private static final long STRATA_SALT = 0x57A7AL;

    /** Lower boundary (absolute elevation) of stratum {@code k}: every 11 blocks, jittered by 3.5. */
    private double stratumBase(int k) {
        return k * STRATUM + 7.0 * (SeedMixer.cellUnit(geologySeed ^ STRATA_SALT, k, 0, 0) - 0.5);
    }

    private boolean hardRoll(int k) {
        return SeedMixer.cellUnit(geologySeed ^ STRATA_SALT, k, 0, 1) < 0.42;
    }

    /**
     * Whether stratum {@code k} is a resistant, cliff-forming layer. At most two follow each other,
     * so no cliff band is taller than about 29 blocks.
     */
    private boolean hardStratum(int k) {
        return hardRoll(k) && !(hardRoll(k - 1) && hardRoll(k - 2));
    }

    private int stratumIndex(double y) {
        int k = (int) Math.floor(y / STRATUM);
        while (stratumBase(k) > y) {
            k--;
        }
        while (stratumBase(k + 1) <= y) {
            k++;
        }
        return k;
    }

    /**
     * Height a canyon wall has climbed above its floor at {@code floor} at the horizontal distance
     * {@code u} from the floor's edge, at most {@code max}. Like the walls of the Grand Canyon, it
     * is cut into horizontal strata shared by the whole world: resistant layers stand as cliffs of
     * 7-29 blocks, softer layers between them weather back to talus slopes ({@code soft} blocks per
     * block), so the wall is a staircase of cliffs and benches that grows wider the deeper the
     * canyon is, never a single sheer face.
     */
    private double canyonWallRise(double floor, double u, double soft, double max) {
        double y = floor;
        int k = stratumIndex(y);
        double left = u;
        while (y - floor < max) {
            double top = stratumBase(k + 1);
            double slope = hardStratum(k) ? HARD_SLOPE : soft;
            double w = (top - y) / slope;
            if (left <= w) {
                return Math.min(max, y + left * slope - floor);
            }
            left -= w;
            y = top;
            k++;
        }
        return max;
    }

    /** Horizontal width a canyon wall needs to climb {@code rise} blocks above {@code floor}. */
    private double canyonWallWidth(double floor, double rise, double soft) {
        double y = floor;
        int k = stratumIndex(y);
        double w = 0.0;
        while (y < floor + rise) {
            double top = Math.min(stratumBase(k + 1), floor + rise);
            w += (top - y) / (hardStratum(k) ? HARD_SLOPE : soft);
            y = top;
            k++;
        }
        return w;
    }

    /**
     * Elevation of a butte standing in a canyon (on a jittered 90-block grid, in about a quarter of
     * the cells), or a very low value. A butte is a flat-topped remnant of the land the canyon was
     * cut into, 3-10 blocks in radius at the top, at most 16-76 blocks high, with flanks stepped
     * into the same strata as the canyon walls; near a wall it merges with it as a spur.
     */
    private double canyonButte(int x, int z, double floor, double relief, double soft, double dist, double floorHalf) {
        double cell = 90.0 * hs;
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        double best = -1.0E9;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int gx = cx + dx;
                int gz = cz + dz;
                if (SeedMixer.cellUnit(pillarSeed, gx, gz, 0) > 0.28) {
                    continue;
                }
                double px = (gx + 0.15 + 0.7 * SeedMixer.cellUnit(pillarSeed, gx, gz, 1)) * cell;
                double pz = (gz + 0.15 + 0.7 * SeedMixer.cellUnit(pillarSeed, gx, gz, 2)) * cell;
                double rTop = 3.0 + 7.0 * SeedMixer.cellUnit(pillarSeed, gx, gz, 3);
                double height = Math.min(relief, 16.0 + 60.0 * SeedMixer.cellUnit(pillarSeed, gx, gz, 4));
                double r = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz)) - rTop;
                if (r <= 0.0) {
                    best = Math.max(best, floor + height);
                    continue;
                }
                double w = canyonWallWidth(floor, height, soft);
                if (r < w) {
                    best = Math.max(best, floor + canyonWallRise(floor, w - r, soft, height));
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

    /** Grid spacing of ordinary lake sites. */
    private double lakeCell(boolean great) {
        return great ? 6000.0 * hs / Math.max(0.05, Math.sqrt(settings.lakeFrequency())) : 700.0 * hs / Math.max(0.05, settings.lakeFrequency());
    }

    /**
     * Lakes around a column, applied in three passes so that lakes near each other never break
     * one another: first every shore is lowered towards its water, then every rim is raised where
     * it must hold water, then every basin is carved. No shore can cut a rim, no rim can fill a
     * basin.
     */
    private double applyLakes(int x, int z, double h, Scratch sc) {
        boolean riverWet = sc.riverWetChannel;
        double bankGuard = sc.riverBankGuard;
        int n = sc.lakeCount;
        for (int i = 0; i < n; i++) {
            LakeSite lake = sc.lakeBuf[i];
            double r = sc.lakeDist[i];
            double edge = sc.lakeEdgeBuf[i];
            if (r >= edge && r <= lake.ringRadius) {
                double t = clamp01((r - edge) / lake.shoreW);
                double shore = lake.level + 1.0 + Math.max(0.0, h - lake.level - 1.0) * smoothstep(0.0, 1.0, t);
                // nor may it cut the banks of a river above the lake
                shore = Math.max(shore, Math.min(h, bankGuard));
                if (h > shore) {
                    h = shore;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            LakeSite lake = sc.lakeBuf[i];
            double r = sc.lakeDist[i];
            double edge = sc.lakeEdgeBuf[i];
            // the rim holds the water out to the crest found by the ray scan, never beyond it, so
            // no artificial plateau ends in a cliff; wet river channels stay open as in- and outlets
            if (r >= edge && r <= lake.ringRadius && lake.level > seaLevel && !riverWet
                && (r < edge + 2.5 || r <= crestAt(lake, x, z) + 2.0)) {
                h = Math.max(h, lake.level + 1.0);
            }
        }
        sc.lakeResult = 0.0;
        sc.lakeInside = false;
        for (int i = 0; i < n; i++) {
            LakeSite lake = sc.lakeBuf[i];
            double r = sc.lakeDist[i];
            double edge = sc.lakeEdgeBuf[i];
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
            }
        }
        return h;
    }

    /** Distance beyond a lake's shores over which rivers are graded to its level. */
    private static final double LAKE_GRADE_RANGE = 900.0;

    /**
     * Adds the active lakes of one grid that shape the column, or whose level rivers through it
     * are graded to, to the scratch buffers.
     */
    private int collectLakes(int x, int z, boolean great, Scratch sc, int n) {
        double cell = lakeCell(great);
        long gridSeed = great ? greatLakeSeed : lakeSeed;
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        // neighbouring columns almost always share the cell: keep its 3x3 sites at hand instead of
        // looking them up in the shared map for every column
        int g = great ? 1 : 0;
        LakeSite[] near = sc.nearLakes[g];
        if (!sc.nearLakesValid[g] || sc.nearLakesX[g] != cx || sc.nearLakesZ[g] != cz) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    near[(dx + 1) * 3 + dz + 1] = lakeSite(cx + dx, cz + dz, cell, gridSeed, great);
                }
            }
            sc.nearLakesX[g] = cx;
            sc.nearLakesZ[g] = cz;
            sc.nearLakesValid[g] = true;
        }
        for (int i = 0; i < 9; i++) {
            LakeSite lake = near[i];
            if (lake == null) {
                continue;
            }
            double ddx = x - lake.x;
            double ddz = z - lake.z;
            double r = Math.sqrt(ddx * ddx + ddz * ddz);
            if (r > lake.ringRadius + LAKE_GRADE_RANGE) {
                continue;
            }
            sc.lakeBuf[n] = lake;
            sc.lakeDist[n] = r;
            sc.lakeEdgeBuf[n] = lakeEdge(lake, x, z);
            n++;
        }
        return n;
    }

    /** Shoreline radius of a lake in the direction of {@code (x, z)}: a distorted level set in world space. */
    private double lakeEdge(LakeSite lake, double x, double z) {
        double warp = 0.32 * lakeShape.noise2(x / (lake.radius * 0.95) + lake.shapeOffset, z / (lake.radius * 0.95) - lake.shapeOffset)
            + 0.1 * lakeShape.noise2(x / (lake.radius * 0.33) - lake.shapeOffset, z / (lake.radius * 0.33) + lake.shapeOffset);
        return lake.radius * Math.max(0.35, 1.0 + warp);
    }

    /** Radius of the lake's rim crest in the direction of {@code (x, z)}, between the scanned rays. */
    private static double crestAt(LakeSite lake, double x, double z) {
        double a = Math.atan2(z - lake.z, x - lake.x);
        if (a < 0.0) {
            a += Math.PI * 2.0;
        }
        double f = a / (Math.PI * 2.0) * lake.rays;
        int i0 = (int) Math.floor(f) % lake.rays;
        int i1 = (i0 + 1) % lake.rays;
        double t = f - Math.floor(f);
        return lerp(t, lake.crest[i0], lake.crest[i1]);
    }

    private LakeSite lakeSite(int cx, int cz, double cell, long gridSeed, boolean great) {
        LakeSite site = lakeSiteRaw(cx, cz, great);
        return site.active ? site : null;
    }

    /**
     * The lake site of a grid cell, also when it was rejected ({@link LakeSite#active} false and
     * {@link LakeSite#reject} giving the reason). Used by development tools.
     */
    public LakeSite lakeSiteRaw(int cx, int cz, boolean great) {
        // packed cell and grid, multiplied by an odd constant (a bijection) so that the map's
        // 32-bit hashes spread instead of colliding for every cell with the same cx ^ cz
        long key = (((long) cx << 32) ^ (cz & 0xFFFFFFFFL) ^ (great ? 0x4000000000000000L : 0L)) * 0x9E3779B97F4A7C15L;
        LakeSite cached = lakeSites.get(key);
        if (cached != null) {
            return cached;
        }
        LakeSite computed = computeLakeSite(cx, cz, lakeCell(great), great ? greatLakeSeed : lakeSeed, great);
        if (lakeSites.size() > 16384) {
            lakeSites.clear();
        }
        lakeSites.put(key, computed);
        return computed;
    }

    /**
     * The lake of a grid cell. A lake needs a site where natural ground can hold it, so up to
     * three positions are tried in turn; the first that passes the level scan is the lake.
     */
    private LakeSite computeLakeSite(int cx, int cz, double cell, long gridSeed, boolean great) {
        LakeSite lake = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            lake = tryLakeSite(cx, cz, cell, gridSeed, great, attempt);
            if (lake.active || "none".equals(lake.reject)) {
                return lake;
            }
        }
        return lake;
    }

    private LakeSite tryLakeSite(int cx, int cz, double cell, long gridSeed, boolean great, int attempt) {
        LakeSite lake = new LakeSite();
        lake.reject = "none";
        double roll = SeedMixer.cellUnit(gridSeed, cx, cz, 0);
        double maxChance = great ? 0.3 : 0.65;
        if (roll > maxChance) {
            return lake;
        }
        int channel = attempt == 0 ? 1 : 18 + 2 * attempt;
        lake.x = (cx + 0.25 + 0.5 * SeedMixer.cellUnit(gridSeed, cx, cz, channel)) * cell;
        lake.z = (cz + 0.25 + 0.5 * SeedMixer.cellUnit(gridSeed, cx, cz, channel + 1)) * cell;
        ColumnSample center = new ColumnSample();
        Scratch sc = scratch.get();
        computeColumn((int) lake.x, (int) lake.z, center, false, sc);
        lake.reject = "site";
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
        lake.reject = "roll";
        if (roll > chance) {
            return lake;
        }
        double aridity = smoothstep(0.1, 0.6, center.baseTemperature) * smoothstep(0.1, -0.5, humidityBase((int) lake.x, (int) lake.z));
        lake.reject = "arid";
        if (aridity > 0.5) {
            return lake;
        }
        lake.depth = switch (lake.type) {
            case LakeType.MARSH -> 2.0 + lake.radius * 0.02;
            case LakeType.GLACIAL, LakeType.MOUNTAIN -> Math.min(9.0 + lake.radius * 0.18, 34.0);
            case LakeType.GREAT -> Math.min(14.0 + lake.radius * 0.05, 40.0);
            default -> Math.min(4.0 + lake.radius * 0.11, 18.0);
        };
        lake.shapeOffset = SeedMixer.cellUnit(gridSeed, cx, cz, 4) * 100.0;
        lake.shoreW = 10.0 + lake.radius * 0.25;
        if (!great && nearGreatLake(lake)) {
            lake.reject = "overlap";
            return lake;
        }
        lake.reject = scanLevel(lake, great, center, sc);
        lake.active = lake.reject == null;
        return lake;
    }

    /** Whether a small lake would overlap the shores of an active great lake. */
    private boolean nearGreatLake(LakeSite lake) {
        double cell = lakeCell(true);
        int cx = (int) Math.floor(lake.x / cell);
        int cz = (int) Math.floor(lake.z / cell);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LakeSite g = lakeSite(cx + dx, cz + dz, cell, greatLakeSeed, true);
                if (g != null && Math.hypot(g.x - lake.x, g.z - lake.z) < g.ringRadius + lake.radius * 1.7 + 12.0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Chooses the lake's level from a dense scan of rays. Each ray runs from the outermost point
     * of the warped shoreline outwards; the water could escape along it over its highest point,
     * the crest, or join the first river or sea it reaches. The lowest of these over all rays is
     * the lake's spill level, so the lake is enclosed by natural ground and only needs its rim
     * topped up where the ground dips between shore and crest. A lake touching a river takes the
     * river's level there. Returns the reason for rejecting the site, or {@code null}.
     */
    private String scanLevel(LakeSite lake, boolean great, ColumnSample center, Scratch sc) {
        double radius = lake.radius;
        double maxEdge = radius * 1.42;
        double reach = radius * 1.5 + 16.0;
        double step = great ? 10.0 : 4.0;
        int rays = (int) clamp(Math.ceil(Math.PI * 2.0 * reach / (great ? 12.0 : 6.0)), 32, great ? 360 : 256);
        float[] crest = new float[rays];
        double[] cand = new double[rays];
        double[] low = new double[rays];
        boolean[] cliffy = new boolean[rays];
        int maxSamples = (int) Math.ceil(reach / step) + 2;
        double[] heights = new double[maxSamples];
        boolean[] steep = new boolean[maxSamples];
        ColumnSample ring = new ColumnSample();
        for (int i = 0; i < rays; i++) {
            double a = Math.PI * 2.0 * i / rays;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            double shore = 0.0;
            for (double r = radius * 0.3; r <= maxEdge + 1.0; r += 2.0) {
                if (r < lakeEdge(lake, lake.x + r * dx, lake.z + r * dz)) {
                    shore = r;
                }
            }
            double best = -Double.MAX_VALUE;
            double bestR = shore;
            int bestK = -1;
            int k = 0;
            for (double r = shore + step * 0.5; r <= reach && k < maxSamples; r += step) {
                computeColumn((int) Math.floor(lake.x + r * dx), (int) Math.floor(lake.z + r * dz), ring, false, sc);
                double allowed;
                boolean water = true;
                if (ring.bodyInside && ring.bodyKind == WaterBody.RIVER) {
                    allowed = ring.bodyLevel;
                } else if (ring.height < seaLevel - 0.5) {
                    allowed = seaLevel;
                } else {
                    // ground holds the water up to a block below its top
                    allowed = Math.floor(ring.height) - 1.0;
                    water = false;
                }
                heights[k] = water ? Double.MAX_VALUE : ring.height;
                steep[k] = ring.cliff > 0.35 || ring.canyon > 0.3;
                if (allowed > best) {
                    best = allowed;
                    bestR = r;
                    bestK = k;
                }
                k++;
                if (water) {
                    break;
                }
            }
            cand[i] = best;
            crest[i] = (float) bestR;
            double lo = Double.MAX_VALUE;
            boolean cl = false;
            for (int j = 0; j <= bestK; j++) {
                lo = Math.min(lo, heights[j]);
                cl |= steep[j];
            }
            low[i] = lo;
            cliffy[i] = cl;
        }
        double level = Double.MAX_VALUE;
        for (double c : cand) {
            level = Math.min(level, c);
        }
        level = Math.max(seaLevel, level);
        lake.level = level;
        lake.rays = rays;
        lake.crest = crest;
        double maxCrest = 0.0;
        double maxRaise = 0.0;
        boolean steepRim = false;
        for (int i = 0; i < rays; i++) {
            maxCrest = Math.max(maxCrest, crest[i]);
            if (low[i] != Double.MAX_VALUE && level > seaLevel) {
                maxRaise = Math.max(maxRaise, level + 1.0 - low[i]);
            }
            steepRim |= cliffy[i];
        }
        lake.maxRaise = maxRaise;
        lake.ringRadius = Math.max(maxEdge + lake.shoreW, maxCrest + 3.0) + 1.0;
        double[] sorted = cand.clone();
        java.util.Arrays.sort(sorted);
        if (level <= seaLevel && lake.type != LakeType.MARSH && center.height > seaLevel + 12.0) {
            return "drained";
        }
        // a lake in a valley may be overlooked by high ground, but its basin must not have to be
        // dug far below the ground it replaces: that would leave a pit, not a lake
        double interior = 0.0;
        int count = 0;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * (i + 0.5) / 8.0;
            for (double f = 0.0; f <= 0.61; f += 0.3) {
                computeColumn((int) Math.floor(lake.x + Math.cos(a) * radius * f), (int) Math.floor(lake.z + Math.sin(a) * radius * f), ring, false, sc);
                interior += ring.height;
                count++;
            }
        }
        interior /= count;
        if (interior - level > 6.0 + 0.12 * radius || sorted[rays / 2] - level > 25.0 + 0.3 * radius) {
            return "pit";
        }
        if (level > seaLevel && maxRaise > 3.0 + 0.02 * radius) {
            return "dam";
        }
        if (level > seaLevel && steepRim) {
            return "cliff";
        }
        return null;
    }
    /** Lake lookup for the find command: nearest active lake centre within the grid cells around. */
    public LakeSite nearestLake(int x, int z, int rangeBlocks, boolean great) {
        double cell = lakeCell(great);
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
    // Rock formation sites (arches, spires, hoodoos, sea stacks)
    // =========================================================================================

    public static final int FORMATION_CELL = 384;
    public static final int ARCH = 1;
    public static final int SPIRE = 2;
    public static final int HOODOO = 3;
    public static final int SEA_STACK = 4;

    public FormationSite formationSite(int cellX, int cellZ) {
        long key = siteKey(siteSeed ^ 0xF0F0L, cellX, cellZ);
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

    public static final class LakeSite {
        public boolean active;
        public double x;
        public double z;
        public double radius;
        /** Radius beyond which the lake no longer shapes the terrain. */
        public double ringRadius;
        /** Exclusive top of the water (the top water block is one below). */
        public double level;
        public double depth;
        public int type;
        /** Width of the band of shore lowered towards the water. */
        public double shoreW;
        /** Number of rays of the level scan, and the radius of the rim's crest along each. */
        public int rays;
        public float[] crest;
        /** Largest raise of the ground between shore and crest needed to hold the water. */
        public double maxRaise;
        /** Why an inactive site was rejected ({@code null} for active lakes). */
        public String reject;
        double shapeOffset;
    }

    private static final class Scratch {
        final double[] deriv = new double[2];
        final double[] grad = new double[2];
        final MountainState mountains = new MountainState();
        final ColumnSample[] cache = new ColumnSample[CACHE_SIZE];
        final long[] cacheKeys = new long[CACHE_SIZE];
        final boolean[] cacheValid = new boolean[CACHE_SIZE];
        double volcanoInfluence;
        double craterInfluence;
        double craterWaterLevel;
        double craterLavaLevel;
        boolean volcanoCraterMode;
        double plateau;
        double plateauTop;
        double plateauEdge;
        double trench;
        double trenchFloor;
        double fjord;
        double canyon;
        double canyonFloor;
        double canyonWall;
        double lakeResult;
        int lakeType;
        double lakeLevel;
        boolean lakeInside;
        final LakeSite[] lakeBuf = new LakeSite[18];
        int lakeCount;
        final double[] lakeDist = new double[18];
        final double[] lakeEdgeBuf = new double[18];
        /** The 3x3 lake sites around the last lake cell visited, per grid (small, great). */
        final LakeSite[][] nearLakes = new LakeSite[2][9];
        final int[] nearLakesX = new int[2];
        final int[] nearLakesZ = new int[2];
        final boolean[] nearLakesValid = new boolean[2];
        final RiverTrace greatTrace = new RiverTrace();
        final RiverTrace normalTrace = new RiverTrace();
        boolean riverWetChannel;
        // a column's river fields, filled by riverFields
        double fRiver;
        double fValley;
        double fHalfW;
        double fDist;
        double fLevel;
        boolean fDry;
        boolean fBodyInside;
        double fBodyLevel;
        double fBodyCont;
        double riverBankGuard;
        double footC;
        double footBase;
        double footArid;
        double footSwamp;
        double footGreat;
        double footGreatX;
        double footGreatZ;
        /** Cache of foot-grid points (see riverFoot); state 0 empty, 1 continentalness, 2 all fields. */
        final long[] footGridKey = new long[256];
        final byte[] footGridState = new byte[256];
        final double[] footGridC = new double[256];
        final double[] footGridSwamp = new double[256];
        final double[] footGridArid = new double[256];
        final double[] footGridGreat = new double[256];
        final double[] footGridGreatX = new double[256];
        final double[] footGridGreatZ = new double[256];

        Scratch() {
            for (int i = 0; i < CACHE_SIZE; i++) {
                cache[i] = new ColumnSample();
            }
        }
    }
}
