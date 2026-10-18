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
    /** Course of the great-river network (zero lines) and where it is present. */
    private final Fractal greatCourse;
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
        // fixed salts outside the running sequence, so adding them changed no other field
        this.greatCourse = new Fractal(SeedMixer.derive(seed, 7101), 3, 2.0, 0.45);
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

    /**
     * Pyramidal horn peaks on a jittered grid of {@code cell} blocks (about 70% of cells hold one).
     * Each horn is a cone whose radius varies with direction, which gives it three or four steep
     * faces separated by arêtes, like the Matterhorn or Ama Dablam. Returns 0..1, 1 at the apex.
     */
    private double hornField(double x, double z, double cell, int channel) {
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        double best = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = SeedMixer.hash2(hornSeed + channel * 7919L, cx + dx, cz + dz);
                if (SeedMixer.unit(h) > 0.7) {
                    continue;
                }
                long h1 = SeedMixer.mix(h);
                long h2 = SeedMixer.mix(h1);
                long h3 = SeedMixer.mix(h2);
                double sx = (cx + dx + 0.2 + 0.6 * SeedMixer.unit(h1)) * cell;
                double sz = (cz + dz + 0.2 + 0.6 * SeedMixer.unit(h2)) * cell;
                double px = x - sx;
                double pz = z - sz;
                double dist = Math.sqrt(px * px + pz * pz);
                double reach = cell * 0.64;
                if (dist >= reach * 1.32) {
                    continue;
                }
                double strength = 0.72 + 0.28 * SeedMixer.unit(h3);
                int faces = SeedMixer.unit(SeedMixer.mix(h3)) < 0.55 ? 3 : 4;
                double phase = SeedMixer.unit(h3 * 31L) * Math.PI * 2.0;
                double angle = Math.atan2(pz, px);
                // arêtes: the radius peaks sharply (a cusp, not a lobe) in the directions of the
                // ridges between the faces, so contour lines have corners and the edges stay crisp
                double cusp = 1.0 - Math.abs(Math.sin(faces * (angle - phase) * 0.5));
                double radius = reach * (0.82 + 0.5 * cusp * cusp * cusp);
                double t = 1.0 - dist / radius;
                if (t > 0.0) {
                    best = Math.max(best, strength * Math.pow(t, 1.6));
                }
            }
        }
        return best;
    }

    /** The unblurred mountain mask (0..1) from erosion and mountain belts at warped coordinates. */
    private double mountainMask(double wx, double wz, double es) {
        double e = erosionNoise.fbm(wx / es, wz / es, 3) * 1.35;
        double bs = es * 2.5;
        double beltLine = 1.0 - Math.abs(beltNoise.fbm(wx / bs, wz / bs)) * 4.5;
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
        // mountains follow the low-frequency part of the erosion field; the fine octaves would
        // otherwise switch between lowland and high mountain within a few hundred blocks
        double mountainE = smoothstep(-0.2, -0.82, clamp(erosionNoise.fbm(wx / es, wz / es, 3) * 1.35 - 1.4 * beltStrength, -1.0, 1.0));

        // ---- 4. base profile ------------------------------------------------------------------
        double base = TMath.curve(cEff, PROFILE_C, PROFILE_H);

        // ---- 5. mountains ---------------------------------------------------------------------
        double rangeI = 0.5 + 0.5 * rangeIntensity.fbm(wx / (es * 2.6), wz / (es * 2.6));
        double colossal = smoothstep(0.6, 0.84, 0.5 + 0.5 * colossalNoise.fbm(wx / (es * 3.3), wz / (es * 3.3)))
            * smoothstep(-0.02, 0.28, cEff) * Math.max(beltStrength, mountainE);
        double amp = settings.mountainHeight() * (0.32 + 0.95 * rangeI) + settings.colossalHeight() * colossal;
        // even so, a range could rise from lowland to its full height within 200 blocks, as a
        // sheer, bullet-shaped wall; blurring the mask over a distance that grows with the range's
        // height gives tall mountains proportionally broad foothills
        if (erosion < 0.45) {
            double r = (120.0 + 0.3 * amp) * hs;
            mountainE = (2.0 * mountainE + mountainMask(wx + r, wz, es) + mountainMask(wx - r, wz, es)
                + mountainMask(wx, wz + r, es) + mountainMask(wx, wz - r, es)) / 6.0;
        }
        // mountains fade out over a few hundred blocks towards the coast (judged on the raw
        // continentalness, which island boosts do not compress), so a range ends in foothills
        // instead of rising from the beach as a 400-block wall
        double mountain = mountainE * reliefLand * smoothstep(-0.2, 0.12, rawC);

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
            double ridges = alpineRidges.ridged(rx / ms, rz / ms, 2.0);
            double horns = hornField(rx, rz, 760.0 * hs, 0);
            if (wideW > 0.0) {
                ridges = lerp(wideW, ridges, alpineRidges.ridged(rx / (ms * 2.6) + 71.3, rz / (ms * 2.6) - 33.1, 2.0));
                horns = lerp(wideW, horns, hornField(rx, rz, 1700.0 * hs, 1));
            }
            if (hugeW > 0.0) {
                ridges = lerp(hugeW, ridges, alpineRidges.ridged(rx / (ms * 5.2) - 12.9, rz / (ms * 5.2) + 55.7, 1.9));
                horns = lerp(hugeW, horns, hornField(rx, rz, 3000.0 * hs, 2));
            }
            eroded = 0.5 + 0.5 * mountainDetail.erodedFbm(rx / (ms * 0.45), rz / (ms * 0.45), 0.35, d);
            // squaring the ridge field keeps its knife-edge crests but makes the flanks concave;
            // crests rise towards the horns and sag into cols between them, so the highest points
            // are pyramidal summits rather than long rounded ridge tops
            double ridgeShape = Math.pow(clamp01((ridges - 0.1) / 0.9), 2.0) * 0.84 * (0.62 + 0.38 * Math.min(1.0, horns * 1.6));
            double alpineShape = Math.max(ridgeShape, horns) + (eroded - 0.5) * 0.12 * ridges + 0.04;
            double domes = 0.5 + 0.5 * lerp(wideW, ancientDomes.fbm(rx / (ms * 1.5), rz / (ms * 1.5)),
                ancientDomes.fbm(rx / (ms * 3.4) + 19.0, rz / (ms * 3.4) - 7.0));
            double ancientShape = smoothstep(0.08, 0.92, domes) * 0.86 + eroded * 0.12;
            double wallShape = smoothstep(0.05, 0.95, beltLine) * (0.72 + 0.28 * ridges);
            shape = alpineShape * alpineW + ancientShape * ancientW;
            shape = lerp(wallW * 0.8, shape, Math.max(shape, wallShape));
            // valleys cut tall massifs less deeply, relative to their height, so their sides
            // do not become 1000-block walls
            shape *= 1.0 - 0.58 * (1.0 - valleyCut) * (1.0 - 0.55 * hugeW);
            meadow = smoothstep(0.22, 0.55, meadowNoise.fbm(rx / (ms * 2.2), rz / (ms * 2.2)))
                * (1.0 - 0.6 * alpineW) * smoothstep(0.25, 0.6, mountain) * (1.0 - colossal);
            shape = lerp(meadow * 0.85, shape, 0.3 + 0.08 * eroded);
            shape = clamp01(shape);
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
        double river = 0.0;
        double riverValley = 0.0;
        double riverHalfW = 0.0;
        double riverDist = 1.0E9;
        double riverLevel = sl;
        boolean dry = false;
        double waterLevel = ColumnSample.NO_WATER;
        int bodyKind = WaterBody.NONE;
        boolean bodyInside = false;
        double bodyLevel = ColumnSample.NO_WATER;
        double bodyCont = ColumnSample.NO_WATER;
        double riverGreat = 0.0;
        double landMask = (1.0 - island) * (1.0 - smoothstep(0.02, 0.25, volcano));
        RiverTrace great = sc.greatTrace;
        RiverTrace normal = sc.normalTrace;
        great.hit = false;
        normal.hit = false;
        if (landMask > 0.0 && cEff > -0.5) {
            traceGreat(x, z, h, mountain, landMask, great, sc);
            if (great.hit) {
                h = carveGreat(x, z, h, great);
            }
            traceNormal(x, z, h, cEff, mountain, erosion, canyon, canyonFloor, landMask, great, normal, sc);
            if (normal.hit) {
                h = carveNormal(h, plains, normal);
            }
        }
        if (normal.hit || great.hit) {
            // the river whose channel the column is in (or is closest to, relative to its width)
            // sets the column's river fields
            double relN = normal.hit ? normal.dist / normal.halfW : Double.MAX_VALUE;
            double relG = great.hit ? great.dist / great.halfW : Double.MAX_VALUE;
            RiverTrace dom = relN <= relG ? normal : great;
            RiverTrace other = dom == normal ? great : normal;
            riverHalfW = dom.halfW;
            riverDist = dom.dist;
            riverLevel = Math.floor(dom.level);
            riverValley = 1.0 - clamp01((dom.dist - dom.halfW) / dom.valleyW);
            dry = dom.wet <= 0.15 && dom.level > sl + 1.0;
            riverGreat = dom == great ? great.great : 0.0;
            RiverTrace wetChannel = dom.inside() ? dom : other.hit && other.inside() ? other : null;
            if (dom.dist < dom.halfW) {
                river = 1.0 - dom.dist / dom.halfW;
            }
            if (wetChannel != null) {
                bodyKind = WaterBody.RIVER;
                bodyInside = true;
                double cont = Math.max(sl, wetChannel.level);
                bodyCont = cont;
                bodyLevel = Math.floor(cont);
                if (bodyLevel > sl) {
                    waterLevel = bodyLevel;
                }
            }
        }
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
        out.riverGreat = riverGreat;
        out.nearbyWater = Double.NaN;

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
        /** Great network only: a great river is near enough to grade tributaries to it. */
        boolean near;
        /** Great network only: width of the flat floodplain beside the channel. */
        double floodW;
        double dist;
        double halfW;
        double valleyW;
        double level;
        /** 1 for a river carrying water, falling to 0 for a dry wash. */
        double wet;
        double depth;
        /** Great network only: 0..1 how fully developed the great river is here. */
        double great;
        /** Magnitude of the field gradient at the column (great network: grading tributaries). */
        double gradLen;

        boolean inside() {
            return hit && dist < halfW && wet > 0.2;
        }
    }

    /** Field whose zero lines are the centre lines of ordinary rivers. */
    private double riverField(double x, double z) {
        double rs = settings.riverScale() * hs;
        double rwx = x + 280.0 * riverWarpX.fbm(x / (950.0 * hs), z / (950.0 * hs));
        double rwz = z + 280.0 * riverWarpZ.fbm(x / (950.0 * hs) + 9.1, z / (950.0 * hs) - 4.4);
        return riverNoise.fbm(rwx / rs, rwz / rs);
    }

    /** Field whose zero lines are the centre lines of great rivers: broad, slow meanders. */
    private double greatField(double x, double z) {
        double gwx = x + 520.0 * riverWarpX.fbm(x / (2600.0 * hs) + 41.7, z / (2600.0 * hs) - 19.3);
        double gwz = z + 520.0 * riverWarpZ.fbm(x / (2600.0 * hs) - 23.1, z / (2600.0 * hs) + 8.9);
        double gs = GREAT_SCALE * settings.riverScale() * hs;
        return greatCourse.fbm(gwx / gs, gwz / gs);
    }

    /**
     * Continentalness, base height, aridity and wetland weight at a river's foot point (the
     * nearest point of its centre line). Everything that decides a river's surface is read there
     * rather than at the column itself, so a whole cross-section shares one level.
     */
    private void riverFoot(double fx, double fz, Scratch sc) {
        int x = (int) Math.floor(fx + 0.5);
        int z = (int) Math.floor(fz + 0.5);
        double wx = x + 320.0 * warpX.fbm(x / (2600.0 * hs), z / (2600.0 * hs))
            + 24.0 * warpX2.fbm(x / (420.0 * hs), z / (420.0 * hs));
        double wz = z + 320.0 * warpZ.fbm(x / (2600.0 * hs) + 31.7, z / (2600.0 * hs) - 12.4)
            + 24.0 * warpZ2.fbm(x / (420.0 * hs) - 7.1, z / (420.0 * hs) + 3.3);
        double cs = settings.continentScale() * hs;
        double c = continent.fbm(wx / cs, wz / cs) * 2.4 + 0.02;
        if (c < -0.45) {
            c -= 0.28 * smoothstep(-0.45, -1.05, c);
        }
        sc.footC = c;
        sc.footBase = TMath.curve(c, PROFILE_C, PROFILE_H);
        double es = settings.erosionScale() * hs;
        double bs = es * 2.5;
        double beltLine = 1.0 - Math.abs(beltNoise.fbm(wx / bs, wz / bs)) * 4.5;
        double belt = smoothstep(0.0, 1.0, beltLine) * smoothstep(-0.25, 0.2, beltSegments.fbm(wx / (bs * 0.8), wz / (bs * 0.8)));
        double erosion = clamp(erosionNoise.fbm(wx / es, wz / es) * 1.35 - 1.4 * belt, -1.0, 1.0);
        // wetlands lie at sea level: rivers descend to it before they reach one (a little earlier
        // than the wetland itself begins) instead of crossing it on a raised bank
        sc.footSwamp = smoothstep(0.38, 0.58, erosion);
        sc.footMountain = smoothstep(-0.2, -0.82, erosion);
        sc.footArid = smoothstep(0.1, 0.6, temperatureBase(x, z)) * smoothstep(0.1, -0.5, humidityBase(x, z));
    }

    /**
     * Lowest the ground may be cut (by a lake's shore) on the banks of an elevated river, so the
     * river is never left standing above its surroundings; {@code -infinity} elsewhere.
     */
    private double bankGuard(RiverTrace t) {
        double top = Math.floor(t.level);
        if (!t.hit || t.wet <= 0.15 || top <= seaLevel || t.dist >= t.halfW + 0.3 * t.valleyW) {
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
            if (level > lake.level) {
                level = Math.min(level, lake.level + 0.02 * Math.max(0.0, d));
            }
        }
        return level;
    }

    /** Water surface of a river whose foot point was evaluated by {@link #riverFoot}. */
    private double footLevel(Scratch sc, double baseOffset) {
        return seaLevel + 0.75 * Math.max(0.0, sc.footBase - baseOffset) * (1.0 - sc.footSwamp);
    }

    /** Wetness of a river at a given level: rivers above sea level run dry in arid climates. */
    private double footWetness(Scratch sc, double level) {
        return level <= seaLevel + 1.0 ? 1.0 : smoothstep(0.62, 0.48, sc.footArid);
    }

    /**
     * Great rivers: 100-200 blocks wide inland, widening to an estuary of up to about 380 blocks at
     * the coast, at sea level over most of their course (the water table fills them), in a
     * floodplain several hundred blocks wide.
     */
    private void traceGreat(int x, int z, double h, double mountain, double landMask, RiverTrace t, Scratch sc) {
        t.near = false;
        double gv = greatField(x, z);
        if (Math.abs(gv) >= 0.1) {
            return;
        }
        double gx = (greatField(x + 4.0, z) - gv) / 4.0;
        double gz = (greatField(x, z + 4.0) - gv) / 4.0;
        double g2 = gx * gx + gz * gz;
        if (g2 < 1.0E-18) {
            return;
        }
        double glen = Math.sqrt(g2);
        double dist = Math.abs(gv) / glen;
        if (dist > 1600.0) {
            return;
        }
        double fx = x - gv * gx / g2;
        double fz = z - gv * gz / g2;
        riverFoot(fx, fz, sc);
        double cG = sc.footC;
        double gs = GREAT_SCALE * settings.riverScale() * hs;
        // lowlands only, from far inland down to the coast, where they end in the shallow sea
        double mask = smoothstep(-0.25, 0.1, greatPresence.fbm(fx / (1.4 * gs), fz / (1.4 * gs))) * smoothstep(0.75, 0.5, cG)
            * smoothstep(-0.26, -0.18, cG) * (1.0 - smoothstep(0.1, 0.45, sc.footMountain)) * landMask;
        if (mask <= 0.0) {
            return;
        }
        // tapering over a long stretch where the network fades, like a river narrowing upstream
        double w = smoothstep(0.0, 0.6, mask);
        double rs = settings.riverScale() * hs;
        double halfW = settings.riverWidth() * (50.0 + 45.0 * smoothstep(0.42, 0.0, cG) + 8.0 * greatRiver.fbm(fx / (0.7 * rs), fz / (0.7 * rs)))
            * w * (1.0 + 0.9 * smoothstep(-0.03, -0.15, cG));
        if (halfW < 3.0) {
            return;
        }
        t.near = true;
        t.halfW = halfW;
        double gradedLevel = gradeToLakes(footLevel(sc, 92.0), fx, fz, sc);
        double valleyW = (1.5 * halfW + 160.0) * w + 26.0;
        t.floodW = 0.55 * valleyW;
        t.level = gradedLevel;
        // the valley walls rise no steeper than about 35 degrees, so a great river reaching higher
        // ground cuts a wide valley into it instead of a gorge with sheer sides
        t.valleyW = t.floodW + Math.max(0.45 * valleyW, Math.min(1.6 * (h - t.level - 2.0), 320.0));
        t.wet = footWetness(sc, t.level);
        t.depth = Math.min(4.0 + 0.06 * halfW, 14.0);
        t.great = w;
        t.gradLen = glen;
        t.dist = dist;
        t.hit = dist < halfW + t.valleyW;
    }

    /**
     * Ordinary rivers, 6-30 blocks wide in valleys of their own. Their level follows the land
     * (continentalness) at the foot point, descends to sea level near coasts and wetlands, is
     * graded to a great river they join (about one block per 50) and drops to a canyon's floor
     * where the river reaches a canyon, which makes a river-wide waterfall at the canyon's rim.
     */
    private void traceNormal(int x, int z, double h, double cEff, double mountain, double erosion, double canyon, double canyonFloor, double landMask,
                             RiverTrace great, RiverTrace t, Scratch sc) {
        double rs = settings.riverScale() * hs;
        double presence = smoothstep(-0.5, -0.18, riverPresence.fbm(x / (rs * 1.9), z / (rs * 1.9)));
        // full width up to the shore, so rivers open into the sea instead of pinching out
        double mask = presence * smoothstep(-0.2, -0.14, cEff) * (1.0 - smoothstep(0.42, 0.8, mountain)) * landMask;
        if (mask <= 0.0) {
            return;
        }
        double halfW = settings.riverWidth() * mask * (3.0 + 12.0 * smoothstep(0.55, -0.12, cEff)) * (1.0 - 0.55 * mountain);
        if (halfW <= 1.0) {
            return;
        }
        double valleyW0 = halfW * 2.2 + 26.0 + 70.0 * smoothstep(0.1, 0.5, erosion) + 40.0 * mountain;
        // valley sides no steeper than about 30 degrees where the river cuts into high ground
        double valleyW = Math.max(valleyW0, Math.min(1.8 * (h - seaLevel - 2.0), 240.0));
        // a cheap distance estimate in the warped space rejects most columns ...
        double rwx = x + 280.0 * riverWarpX.fbm(x / (950.0 * hs), z / (950.0 * hs));
        double rwz = z + 280.0 * riverWarpZ.fbm(x / (950.0 * hs) + 9.1, z / (950.0 * hs) - 4.4);
        double rv = riverNoise.fbm(rwx / rs, rwz / rs);
        double rvx = riverNoise.fbm((rwx + 2.0) / rs, rwz / rs);
        double rvz = riverNoise.fbm(rwx / rs, (rwz + 2.0) / rs);
        double gradRW = Math.sqrt((rvx - rv) * (rvx - rv) + (rvz - rv) * (rvz - rv)) / 2.0;
        if (Math.abs(rv) / Math.max(gradRW, 1.0E-9) > 2.5 * (halfW + valleyW) + 40.0) {
            return;
        }
        // ... then the gradient in world space gives the true distance and the foot point
        double gx = (riverField(x + 2.0, z) - rv) / 2.0;
        double gz = (riverField(x, z + 2.0) - rv) / 2.0;
        double g2 = gx * gx + gz * gz;
        if (g2 < 1.0E-18) {
            return;
        }
        double dist = Math.abs(rv) / Math.sqrt(g2);
        if (dist >= halfW + valleyW) {
            return;
        }
        double fx = x - rv * gx / g2;
        double fz = z - rv * gz / g2;
        riverFoot(fx, fz, sc);
        double level = footLevel(sc, 74.5);
        if (great.near) {
            double distG = Math.abs(greatField(fx, fz)) / great.gradLen;
            level = Math.min(level, great.level + 0.02 * Math.max(0.0, distG - great.halfW));
        }
        if (canyon > 0.3) {
            level = Math.min(level, canyonFloor + 1.5);
        }
        level = gradeToLakes(level, fx, fz, sc);
        level = Math.max(seaLevel, level);
        valleyW = Math.max(valleyW0, Math.min(1.8 * (h - level - 2.0), 240.0));
        if (dist >= halfW + valleyW) {
            return;
        }
        t.hit = true;
        t.dist = dist;
        t.halfW = halfW;
        t.valleyW = valleyW;
        t.level = level;
        t.wet = footWetness(sc, t.level);
        t.depth = Math.min(2.5 + 0.13 * halfW, 16.0);
        t.great = 0.0;
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
    private double carveNormal(double h, double plains, RiverTrace t) {
        double level = t.level;
        double top = Math.floor(level);
        double tt = clamp01((t.dist - t.halfW) / t.valleyW);
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
        if (t.dist < t.halfW) {
            double u = t.dist / t.halfW;
            h = Math.min(h, level + bedOffset(t.wet, t.depth) * Math.pow(1.0 - u * u, 0.6));
        }
        return h;
    }

    /** Floodplain, levees, oxbow ponds, channel, sand bars and islands of a great river. */
    private double carveGreat(int x, int z, double h, RiverTrace t) {
        double level = t.level;
        double top = Math.floor(level);
        double out = t.dist - t.halfW;
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
        if (top > seaLevel && t.wet > 0.15 && out >= 0.0 && f < 0.3) {
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
        return h;
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
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LakeSite lake = lakeSite(cx + dx, cz + dz, cell, gridSeed, great);
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
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL) ^ (great ? 0x4000000000000000L : 0L);
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
        final LakeSite[] lakeBuf = new LakeSite[18];
        int lakeCount;
        final double[] lakeDist = new double[18];
        final double[] lakeEdgeBuf = new double[18];
        final RiverTrace greatTrace = new RiverTrace();
        final RiverTrace normalTrace = new RiverTrace();
        boolean riverWetChannel;
        double riverBankGuard;
        double footC;
        double footBase;
        double footArid;
        double footSwamp;
        double footMountain;

        Scratch() {
            for (int i = 0; i < CACHE_SIZE; i++) {
                cache[i] = new ColumnSample();
            }
        }
    }
}
