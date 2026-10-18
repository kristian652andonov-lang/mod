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
    private static final double[] PROFILE_H = {-38, -22, 0, 20, 37, 46, 51, 58.5, 63.6, 65.5, 71, 84, 100, 118};

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
    private final SimplexNoise[] detail3d = new SimplexNoise[4];
    private final long volcanoSeed;
    private final long lakeSeed;
    private final long greatLakeSeed;
    private final long pillarSeed;
    private final long geologySeed;
    private final long siteSeed;

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
        this.alpineRidges = new Fractal(SeedMixer.derive(seed, ++salt), 6, 2.05, 0.46);
        this.ancientDomes = new Fractal(SeedMixer.derive(seed, ++salt), 5, 2.0, 0.45);
        this.mountainDetail = new Fractal(SeedMixer.derive(seed, ++salt), 5, 2.0, 0.5);
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
        double mountainE = smoothstep(-0.2, -0.82, erosion);
        double mountain = mountainE * reliefLand;

        // ---- 4. base profile ------------------------------------------------------------------
        double base = TMath.curve(cEff, PROFILE_C, PROFILE_H);

        // ---- 5. mountains ---------------------------------------------------------------------
        double rangeI = 0.5 + 0.5 * rangeIntensity.fbm(wx / (es * 2.6), wz / (es * 2.6));
        double colossal = smoothstep(0.6, 0.84, 0.5 + 0.5 * colossalNoise.fbm(wx / (es * 3.3), wz / (es * 3.3)))
            * smoothstep(-0.02, 0.28, cEff) * Math.max(beltStrength, mountainE);
        double amp = settings.mountainHeight() * (0.32 + 0.95 * rangeI) + settings.colossalHeight() * colossal;

        double gA = styleA.fbm(wx / (es * 1.7), wz / (es * 1.7));
        double gB = styleB.fbm(wx / (es * 1.7) + 50.0, wz / (es * 1.7) - 20.0);
        double alpineW = smoothstep(-0.28, 0.22, gA + 0.7 * (rangeI - 0.5) + 1.2 * colossal);
        double ancientW = 1.0 - alpineW;
        double wallW = beltStrength * smoothstep(0.05, 0.32, gB);

        // local relief uses a gentle warp only: the strong continental warp would bend individual
        // ridges into swirls, while real ranges keep fairly straight ridge crests over a few km
        double rx = x + 0.22 * (wx - x);
        double rz = z + 0.22 * (wz - z);
        double ms = settings.mountainScale() * hs;
        double vRaw = valleyNoise.fbm(rx / (ms * 1.8), rz / (ms * 1.8));
        double valleyCut = smoothstep(0.0, 0.3, Math.abs(vRaw));
        double shape = 0.0;
        double eroded = 0.5;
        double meadow = 0.0;
        double hillShape = 0.5;
        if (mountain > 0.002) {
            // taller ranges need proportionally wider bases: blend ridge fields at three fixed
            // scales by the local amplitude (a spatially varying scale would distort the noise)
            double ampPre = amp * (alpineW + ancientW * 0.62);
            double wideW = smoothstep(140.0, 460.0, ampPre);
            double hugeW = smoothstep(620.0, 1150.0, ampPre);
            double ridges = alpineRidges.ridged(rx / ms, rz / ms, 1.7);
            if (wideW > 0.0) {
                ridges = lerp(wideW, ridges, alpineRidges.ridged(rx / (ms * 2.6) + 71.3, rz / (ms * 2.6) - 33.1, 1.7));
            }
            if (hugeW > 0.0) {
                ridges = lerp(hugeW, ridges, alpineRidges.ridged(rx / (ms * 5.2) - 12.9, rz / (ms * 5.2) + 55.7, 1.6));
            }
            eroded = 0.5 + 0.5 * mountainDetail.erodedFbm(rx / (ms * 0.45), rz / (ms * 0.45), 0.35, d);
            double alpineShape = Math.pow(clamp01((ridges - 0.12) / 0.78), 1.25) * 0.9 + (eroded - 0.5) * 0.16 * ridges + 0.05;
            double domes = 0.5 + 0.5 * lerp(wideW, ancientDomes.fbm(rx / (ms * 1.5), rz / (ms * 1.5)),
                ancientDomes.fbm(rx / (ms * 3.4) + 19.0, rz / (ms * 3.4) - 7.0));
            double ancientShape = smoothstep(0.08, 0.92, domes) * 0.86 + eroded * 0.12;
            double wallShape = smoothstep(0.05, 0.95, beltLine) * (0.72 + 0.28 * ridges);
            shape = alpineShape * alpineW + ancientShape * ancientW;
            shape = lerp(wallW * 0.8, shape, Math.max(shape, wallShape));
            shape *= 0.42 + 0.58 * valleyCut;
            meadow = smoothstep(0.22, 0.55, meadowNoise.fbm(rx / (ms * 2.2), rz / (ms * 2.2)))
                * (1.0 - 0.6 * alpineW) * smoothstep(0.25, 0.6, mountain) * (1.0 - colossal);
            shape = lerp(meadow * 0.85, shape, 0.3 + 0.08 * eroded);
            // round off the very top so ridge intersections do not form single-column needles
            shape = clamp01(shape);
            shape = shape - 0.12 * smoothstep(0.72, 1.0, shape) * (shape - 0.72);
        }
        double ampEff = amp * (alpineW + ancientW * 0.62);
        double mountainHeight = ampEff * Math.pow(mountain, 1.3) * shape
            + ampEff * 0.1 * Math.pow(mountain, 0.65) * (0.4 + 0.6 * eroded);

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
            double pv = plateauNoise.fbm(rx / (1500.0 * hs), rz / (1500.0 * hs)) + 0.16;
            double pMask = smoothstep(-0.012, 0.012, pv);
            double pH = 45.0 + 200.0 * (0.5 + 0.5 * plateauHeight.fbm(wx / (es * 2.0), wz / (es * 2.0)));
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

        // ---- 7. wetlands ---------------------------------------------------------------------
        double swamp = smoothstep(0.55, 0.72, erosion) * smoothstep(-0.11, 0.02, cEff) * (1.0 - mountain) * (1.0 - plateau);
        if (swamp > 0.0) {
            double wet = sl + 0.4 + 2.6 * swampNoise.fbm(x / (110.0 * hs), z / (110.0 * hs));
            h = lerp(swamp * 0.93, h, wet);
        }

        // ---- 8. coasts: cliffs and fjords -----------------------------------------------------
        double coastZone = bump(cEff, -0.26, -0.16, -0.05);
        double coastCliff = 0.0;
        if (coastZone > 0.0) {
            double cc = smoothstep(0.05, 0.35, coastCliffNoise.fbm(wx / (1900.0 * hs), wz / (1900.0 * hs)));
            coastCliff = cc * coastZone * (0.35 + 0.65 * smoothstep(0.35, -0.5, erosion)) * (1.0 - swamp);
            if (coastCliff > 0.0) {
                // seaward of the cliff line the ground stays sea floor, landward it is at least the cliff top
                double t = (cEff + 0.2) / 0.1;
                double top = sl + 9.0 + 48.0 * cc * (0.4 + 0.6 * rangeI) + 30.0 * mountain;
                double landward = smoothstep(0.30, 0.36, t);
                double profile = lerp(landward, Math.min(h, base), Math.max(h, top));
                h = lerp(coastCliff, h, profile);
            }
        }
        double fjord = 0.0;
        if (mountain > 0.15 && cEff < 0.1) {
            double coldness = smoothstep(0.1, -0.4, temperatureBase(x, z));
            double fv = Math.abs(fjordNoise.fbm(wx / (900.0 * hs), wz / (900.0 * hs)));
            fjord = smoothstep(0.06, 0.0, fv) * smoothstep(0.1, -0.08, cEff) * smoothstep(0.15, 0.4, mountain) * (0.35 + 0.65 * coldness);
            if (fjord > 0.0) {
                double floor = sl - 18.0 - 22.0 * fjord;
                h = lerp(smoothstep(0.0, 0.55, fjord), h, Math.min(h, floor));
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
            * reliefLand * (1.0 - smoothstep(0.08, 0.3, mountain)) * (1.0 - swamp);
        if (canyonReg > 0.001) {
            double cScale = 2600.0 * hs;
            double cv = canyonNoise.fbm(wx / cScale, wz / cScale);
            double cvx = canyonNoise.fbm((wx + 3.0) / cScale, wz / cScale);
            double cvz = canyonNoise.fbm(wx / cScale, (wz + 3.0) / cScale);
            double grad = Math.sqrt((cvx - cv) * (cvx - cv) + (cvz - cv) * (cvz - cv)) / 3.0;
            double dist = Math.abs(cv) / Math.max(grad, 1.0E-7);
            double halfW = (38.0 + 120.0 * (0.5 + 0.5 * canyonWidth.fbm(wx / (1600.0 * hs), wz / (1600.0 * hs)))) * Math.sqrt(canyonReg);
            if (dist < halfW) {
                double floor = Math.max(sl - 3.0, base + 3.0 + 6.0 * smoothstep(0.0, 1.0, plains));
                // canyon depth is limited by its width so walls stay walls, not 300-block slots
                double relief = Math.min(h - floor, 40.0 + halfW * 1.5);
                floor = Math.max(floor, h - relief);
                if (relief > 18.0) {
                    double t = dist / halfW;
                    double profile;
                    if (t < 0.24) {
                        profile = floor;
                    } else {
                        double wallT = (t - 0.24) / 0.76;
                        double stepped = TMath.terrace(Math.pow(wallT, 0.75) * relief, Math.max(10.0, relief / 4.2), 0.78);
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
        double riverMask = presence * smoothstep(-0.16, -0.11, cEff) * (1.0 - smoothstep(0.42, 0.8, mountain)) * (1.0 - island);
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
            riverHalfW = settings.riverWidth() * riverMask
                * (3.0 + 12.0 * smoothstep(0.55, -0.12, cEff) + 100.0 * great) * (1.0 - 0.55 * mountain);
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
                if (lakeLevel > sl + 0.5 && sc.lakeInside) {
                    waterLevel = Math.max(waterLevel, lakeLevel);
                }
            }
        }
        if (crater > 0.0 && craterWater > sl && h < craterWater) {
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
        out.beach = (1.0 - coastCliff) * bump(h, sl - 3.0, sl + 1.0, sl + 5.0) * smoothstep(0.0, -0.16, cEff);
        out.stream = streamFactor(vRaw, mountain, hillW, erosion, x, z);
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
            if (h < sl - 26.0) {
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
     * Deterministic waterfall site for a grid cell, or {@code null}. A site exists where a cliff
     * or a steep valley side drops at least 14 blocks within 12 blocks in a humid enough climate.
     * The same computation is used by the waterfall feature and by {@code /terrahaul find}.
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
        if (SeedMixer.cellUnit(seedW, cellX, cellZ, 0) > 0.55) {
            return NO_WATERFALL;
        }
        ColumnSample s = new ColumnSample();
        Scratch sc = scratch.get();
        int bestX = 0;
        int bestZ = 0;
        int bestDir = -1;
        double bestDrop = 0.0;
        double bestTop = 0.0;
        double bestBottom = 0.0;
        // a few candidate points per cell, keep the one with the strongest drop
        for (int attempt = 0; attempt < 4; attempt++) {
            int px = (int) ((cellX + 0.1 + 0.8 * SeedMixer.cellUnit(seedW, cellX, cellZ, 1 + attempt * 2)) * WATERFALL_CELL);
            int pz = (int) ((cellZ + 0.1 + 0.8 * SeedMixer.cellUnit(seedW, cellX, cellZ, 2 + attempt * 2)) * WATERFALL_CELL);
            computeColumn(px, pz, s, true, sc);
            if (s.height < seaLevel + 10 || s.humidity < -0.35 || s.baseTemperature > 0.75 && s.humidity < 0.0
                || s.waterLevel != ColumnSample.NO_WATER || s.river > 0.0 || s.lake > 0.0 || s.volcano > 0.3
                || s.cliff < 0.25 && s.steepness < 0.35) {
                continue;
            }
            double top = s.height;
            for (int d = 0; d < DIRS.length; d++) {
                double len = d < 4 ? 12.0 : 8.5;
                int qx = px + (int) (DIRS[d][0] * len);
                int qz = pz + (int) (DIRS[d][1] * len);
                computeColumn(qx, qz, s, true, sc);
                double drop = top - Math.max(s.height, s.waterLevel == ColumnSample.NO_WATER ? s.height : s.waterLevel);
                if (drop > bestDrop) {
                    bestDrop = drop;
                    bestDir = d;
                    bestX = px;
                    bestZ = pz;
                    bestTop = top;
                    bestBottom = Math.max(s.height, seaLevel - 1.0);
                }
            }
        }
        if (bestDir < 0 || bestDrop < 14.0) {
            return NO_WATERFALL;
        }
        WaterfallSite w = new WaterfallSite();
        w.x = bestX;
        w.z = bestZ;
        w.dirX = DIRS[bestDir][0];
        w.dirZ = DIRS[bestDir][1];
        w.top = bestTop;
        w.bottom = bestBottom;
        w.width = 1 + (int) (SeedMixer.cellUnit(seedW, cellX, cellZ, 20) * (bestDrop > 60 ? 4 : 2));
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
        if (roll > 0.45) {
            return NO_FORMATION;
        }
        int px = (int) ((cellX + 0.2 + 0.6 * SeedMixer.cellUnit(seedF, cellX, cellZ, 1)) * FORMATION_CELL);
        int pz = (int) ((cellZ + 0.2 + 0.6 * SeedMixer.cellUnit(seedF, cellX, cellZ, 2)) * FORMATION_CELL);
        ColumnSample s = new ColumnSample();
        computeColumn(px, pz, s, true, scratch.get());
        double pick = SeedMixer.cellUnit(seedF, cellX, cellZ, 3);
        boolean dry = s.baseTemperature > 0.4 && s.humidity < 0.15;
        int type = 0;
        if (s.height < seaLevel - 2 && s.height > seaLevel - 16 && s.coastCliff > 0.15) {
            type = SEA_STACK;
        } else if (s.height < seaLevel + 1 || s.waterLevel != ColumnSample.NO_WATER || s.river > 0.0 || s.lake > 0.0 || s.volcano > 0.2) {
            return NO_FORMATION;
        } else if (dry && (s.plateau > 0.2 || s.canyon > 0.1 || s.erosion > -0.4) && s.steepness < 0.5) {
            type = pick < 0.55 ? ARCH : HOODOO;
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

    public static final class WaterfallSite {
        public int x;
        public int z;
        public int dirX;
        public int dirZ;
        public double top;
        public double bottom;
        public int width;
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
