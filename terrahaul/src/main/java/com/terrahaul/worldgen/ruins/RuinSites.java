package com.terrahaul.worldgen.ruins;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.water.FallPlan;
import com.terrahaul.worldgen.water.FallPlanner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.BooleanSupplier;

/**
 * Where trail ruins, signposts, lamp posts and sunken ruins are: deterministic site functions of
 * the terrain model, shared by the ruins feature (which builds them) and {@code /terrahaul find}
 * (which reports them), so the finder never points at something that is not there.
 * <p>
 * Trail sites are surveyed per {@value #TRAIL_CELL}-block cell ({@link TrailSurvey}); sunken ruins
 * get at most one site per {@value #SUNKEN_CELL}-block cell. Every site lies entirely inside its
 * cell, so a chunk only ever looks at the one cell of each grid that contains it. Results are
 * cached per model; the cache only memoises, results never depend on evaluation order or thread.
 * <p>
 * What the terrain model cannot know (the biome a column really gets, vanilla structures nearby,
 * the {@code trails} config) comes from a {@link WorldFilter} that the game installs when a world
 * loads. Devtools run without one: biomes are then estimated from the climate and nothing is
 * excluded.
 */
public final class RuinSites {
    public static final int TRAIL_RUIN = 1;
    public static final int SIGNPOST = 2;
    public static final int PATH_LIGHTS = 3;
    public static final int SUNKEN_RUIN = 4;
    public static final String[] TYPE_NAMES = {"", "trail_ruin", "signpost", "path_lights", "sunken_ruin"};

    public static final int TRAIL_CELL = 256;
    public static final int SUNKEN_CELL = 384;

    // climate classes of trail ruins
    public static final int TEMPERATE = 0;
    public static final int COLD = 1;
    public static final int UPLAND = 2;
    public static final int HOT_DRY = 3;
    public static final int HOT_WET = 4;
    public static final String[] CLIMATE_NAMES = {"temperate", "cold", "upland", "hot_dry", "hot_wet"};

    /** Chance that a cell of sea floor gets a sunken ruin, if a suitable spot is found in it. */
    static final double P_SUNKEN = 0.3;

    /**
     * What only the running game knows. Implementations must be deterministic and thread-safe:
     * worldgen threads and the finder call them concurrently.
     */
    public interface WorldFilter {
        /** {@link RuinBiomes} category of the biome at a position. */
        int biome(int x, int y, int z);

        /** Whether a vanilla surface structure (village, outpost, temple, ...) may stand within {@code radius} blocks. */
        boolean structureNear(int x, int z, int radius);

        /** The {@code trails} config: trail ruins, signposts and lamp posts need trails. */
        boolean trails();

        /** The {@code geologicalFormations} config: signposts only point at arches that exist. */
        boolean formations();
    }

    private static final Map<TerrainModel, RuinSites> INSTANCES = Collections.synchronizedMap(new WeakHashMap<>());

    public static RuinSites of(TerrainModel model) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(model, RuinSites::new);
        }
    }

    /** Drops the sites of a model, for example when its world unloads (the filter holds world state). */
    public static void forget(TerrainModel model) {
        INSTANCES.remove(model);
    }

    final TerrainModel model;
    final int seaLevel;
    final long trailSeed;
    final long sunkenSeed;
    private volatile WorldFilter filter;
    private final ConcurrentHashMap<Long, TrailSurvey.Cell> trailCells = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Object> sunkenCells = new ConcurrentHashMap<>();
    /** Plans by site (identity: a site object is created once per cache fill and never equal to another). */
    private final ConcurrentHashMap<RuinSite, RuinPlan> plans = new ConcurrentHashMap<>();
    private static final Object NO_SITE = new Object();
    /** Survey statistics for the devtools: see {@link TrailSurvey} for the slots. */
    public final AtomicLongArray stats = new AtomicLongArray(32);

    private RuinSites(TerrainModel model) {
        this.model = model;
        this.seaLevel = model.seaLevel();
        this.trailSeed = SeedMixer.derive(model.siteSeed(), 0x2E1E5L);
        this.sunkenSeed = SeedMixer.derive(model.siteSeed(), 0x5D2E1E5L);
    }

    public TerrainModel model() {
        return model;
    }

    /**
     * Installs the world's filter. Sites depend on it (biomes decide materials and where trails
     * are visible), so every cache is cleared; the game installs it before generating anything.
     */
    public void install(WorldFilter f) {
        this.filter = f;
        trailCells.clear();
        sunkenCells.clear();
        plans.clear();
    }

    public WorldFilter filter() {
        return filter;
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | cz & 0xFFFFFFFFL;
    }

    /** The ruin, signposts and lamp posts planned in a trail cell. */
    public TrailSurvey.Cell trailCell(int cx, int cz) {
        long k = key(cx, cz);
        TrailSurvey.Cell cell = trailCells.get(k);
        if (cell == null) {
            cell = TrailSurvey.survey(this, cx, cz);
            if (trailCells.size() > 4096) {
                trailCells.clear();
            }
            trailCells.put(k, cell);
        }
        return cell;
    }

    /** The sunken ruin of a sea cell, or {@code null}. */
    public SunkenRuin sunken(int cx, int cz) {
        long k = key(cx, cz);
        Object s = sunkenCells.get(k);
        if (s == null) {
            SunkenRuin r = computeSunken(cx, cz);
            s = r == null ? NO_SITE : r;
            if (sunkenCells.size() > 4096) {
                sunkenCells.clear();
            }
            sunkenCells.put(k, s);
        }
        return s == NO_SITE ? null : (SunkenRuin) s;
    }

    /** The cached plan of a house or sunken ruin (plans are pure functions of their sites). */
    public RuinPlan plan(RuinSite site) {
        RuinPlan p = plans.get(site);
        if (p == null) {
            p = site instanceof TrailRuin t ? HousePlanner.plan(t) : SunkenPlanner.plan((SunkenRuin) site);
            if (plans.size() > 128) {
                plans.clear();
            }
            plans.put(site, p);
        }
        return p;
    }

    /**
     * Whether a site is built: trail types need trails switched on, and no site may share its
     * ground with a vanilla surface structure. The verdict is cached in the site.
     */
    public boolean allowed(RuinSite site) {
        byte v = site.verdict;
        if (v != 0) {
            return v == 1;
        }
        WorldFilter f = filter;
        boolean ok = f == null || (site.type == SUNKEN_RUIN || f.trails())
            && !f.structureNear(site.centreX(), site.centreZ(), site.radius() + 8);
        site.verdict = (byte) (ok ? 1 : 2);
        return ok;
    }

    // =========================================================================================
    // Climate, biome and materials
    // =========================================================================================

    /** Biome category at a column: from the world when a filter is installed, else estimated. */
    int biome(int x, int y, int z, ColumnSample s) {
        WorldFilter f = filter;
        if (f != null) {
            return f.biome(x, y, z);
        }
        return estimateBiome(s);
    }

    /** Rough stand-in for the biome source, from the same climate parameters it is given. */
    static int estimateBiome(ColumnSample s) {
        double t = s.temperature;
        double h = s.humidity;
        if (s.isSea(63)) {
            return t > 0.55 ? RuinBiomes.OCEAN_WARM : t > 0.2 ? RuinBiomes.OCEAN_LUKEWARM : t < -0.45 ? RuinBiomes.OCEAN_FROZEN
                : t < -0.15 ? RuinBiomes.OCEAN_COLD : RuinBiomes.OCEAN;
        }
        if (s.beach > 0.4) {
            return RuinBiomes.BEACH;
        }
        if (s.swamp > 0.6 && t > -0.2) {
            return t > 0.4 ? RuinBiomes.MANGROVE : RuinBiomes.SWAMP;
        }
        if (s.mountain > 0.55 && s.peakness > 0.6) {
            return t < -0.15 ? RuinBiomes.SNOWY_PEAKS : RuinBiomes.STONY;
        }
        // vanilla's middle-biome table: temperature and humidity bands
        if (t >= 0.55) {
            return RuinBiomes.DESERT;
        }
        if (s.meadow > 0.4) {
            return RuinBiomes.MEADOW;
        }
        if (s.windswept > 0.5) {
            return RuinBiomes.WINDSWEPT;
        }
        if (t >= 0.2) {
            return h < -0.1 ? RuinBiomes.SAVANNA : h < 0.1 ? RuinBiomes.FOREST : RuinBiomes.JUNGLE;
        }
        if (t < -0.45) {
            return RuinBiomes.SNOWY;
        }
        if (t < -0.15) {
            return h < -0.1 ? RuinBiomes.PLAINS : h < 0.1 ? RuinBiomes.FOREST : RuinBiomes.TAIGA;
        }
        return h < -0.1 ? RuinBiomes.PLAINS : h < 0.1 ? RuinBiomes.FOREST : h < 0.3 ? RuinBiomes.BIRCH : RuinBiomes.DARK_FOREST;
    }

    /** Climate class of a trail ruin: decides the kind of house and its building stone. */
    static int climateClass(ColumnSample s, int biome, int seaLevel) {
        switch (biome) {
            case RuinBiomes.SAVANNA -> {
                return HOT_DRY;
            }
            case RuinBiomes.JUNGLE, RuinBiomes.MANGROVE -> {
                return HOT_WET;
            }
            case RuinBiomes.SNOWY, RuinBiomes.TAIGA -> {
                return COLD;
            }
            case RuinBiomes.MEADOW, RuinBiomes.WINDSWEPT -> {
                return UPLAND;
            }
            default -> {
            }
        }
        if (s.temperature >= 0.2 && s.humidity < -0.1) {
            return HOT_DRY;
        }
        if (s.temperature >= 0.2 && s.humidity >= 0.1) {
            return HOT_WET;
        }
        if (s.temperature < -0.45) {
            return COLD;
        }
        if (s.height > seaLevel + 100 || s.windswept > 0.5 || s.meadow > 0.4 || s.mountain > 0.3) {
            return UPLAND;
        }
        return TEMPERATE;
    }

    /** Vernacular materials: local stone by geology and climate, local wood by biome. */
    static RuinMaterials houseMaterials(int climate, int biome, ColumnSample s, int template, long seed) {
        double r1 = SeedMixer.unit(SeedMixer.mix(seed ^ 0x57014EL));
        double r2 = SeedMixer.unit(SeedMixer.mix(seed ^ 0x3009L));
        double r3 = SeedMixer.unit(SeedMixer.mix(seed ^ 0x1EAFL));
        int stone;
        if (climate == HOT_DRY) {
            boolean sand = s.geology == Geology.SEDIMENTARY || s.humidity < -0.45;
            stone = sand ? (s.temperature > 0.45 && r1 < 0.35 ? RuinMaterials.RED_SANDSTONE : RuinMaterials.SANDSTONE) : RuinMaterials.MUD;
        } else if (climate == HOT_WET) {
            stone = RuinMaterials.MOSSY;
        } else {
            stone = switch (s.geology) {
                case Geology.GRANITE -> RuinMaterials.GRANITE;
                case Geology.DARK -> RuinMaterials.SLATE;
                case Geology.LIMESTONE -> RuinMaterials.LIMESTONE;
                case Geology.SEDIMENTARY -> r1 < 0.5 ? RuinMaterials.SANDSTONE : RuinMaterials.COBBLE;
                case Geology.VOLCANIC -> RuinMaterials.TUFF;
                default -> r1 < 0.22 ? RuinMaterials.ASHLAR : RuinMaterials.COBBLE;
            };
        }
        int wood = switch (biome) {
            case RuinBiomes.BIRCH -> RuinMaterials.BIRCH;
            case RuinBiomes.DARK_FOREST -> RuinMaterials.DARK_OAK;
            case RuinBiomes.TAIGA, RuinBiomes.SNOWY, RuinBiomes.WINDSWEPT, RuinBiomes.MEADOW -> RuinMaterials.SPRUCE;
            case RuinBiomes.CHERRY -> RuinMaterials.CHERRY;
            case RuinBiomes.JUNGLE -> RuinMaterials.JUNGLE;
            case RuinBiomes.SAVANNA -> RuinMaterials.ACACIA;
            case RuinBiomes.MANGROVE -> RuinMaterials.MANGROVE;
            case RuinBiomes.SWAMP -> r2 < 0.5 ? RuinMaterials.DARK_OAK : RuinMaterials.OAK;
            default -> switch (climate) {
                case COLD, UPLAND -> RuinMaterials.SPRUCE;
                case HOT_DRY -> RuinMaterials.ACACIA;
                case HOT_WET -> RuinMaterials.JUNGLE;
                default -> r2 < 0.12 ? RuinMaterials.BIRCH : RuinMaterials.OAK;
            };
        };
        if (template == HousePlanner.STILT_HUT && wood == RuinMaterials.JUNGLE && r3 < 0.6) {
            wood = RuinMaterials.BAMBOO;
        }
        int roofWood = switch (wood) {
            case RuinMaterials.OAK, RuinMaterials.BIRCH -> r3 < 0.5 ? RuinMaterials.SPRUCE : RuinMaterials.DARK_OAK;
            case RuinMaterials.CHERRY, RuinMaterials.BAMBOO -> RuinMaterials.DARK_OAK;
            case RuinMaterials.SPRUCE -> r3 < 0.3 ? RuinMaterials.DARK_OAK : RuinMaterials.SPRUCE;
            default -> wood;
        };
        int cover = RuinMaterials.ROOF_WOOD;
        if (stone == RuinMaterials.SLATE || wood == RuinMaterials.CHERRY && r3 < 0.5) {
            cover = RuinMaterials.ROOF_TILE;
        } else if (template == HousePlanner.BOTHY || template == HousePlanner.WATCHTOWER && r2 < 0.5) {
            cover = RuinMaterials.ROOF_STONE;
        }
        int leaves = wood == RuinMaterials.BAMBOO ? RuinMaterials.JUNGLE : wood == RuinMaterials.OAK && r1 > 0.7 ? RuinMaterials.AZALEA : wood;
        return new RuinMaterials(stone, wood, roofWood, cover, leaves);
    }

    /** Vanilla chest loot that fits the house. */
    static String lootTable(int climate, int biome, int template, RuinMaterials mat) {
        String base = "minecraft:chests/village/";
        if (template == HousePlanner.WATCHTOWER) {
            return base + "village_weaponsmith";
        }
        if (template == HousePlanner.BOTHY) {
            return base + "village_shepherd";
        }
        return switch (climate) {
            case COLD -> base + (biome == RuinBiomes.SNOWY ? "village_snowy_house" : "village_taiga_house");
            case UPLAND -> base + "village_taiga_house";
            case HOT_DRY -> base + (mat.stone == RuinMaterials.SANDSTONE || mat.stone == RuinMaterials.RED_SANDSTONE
                ? "village_desert_house" : "village_savanna_house");
            default -> base + "village_plains_house";
        };
    }

    // =========================================================================================
    // Sunken ruins
    // =========================================================================================

    private SunkenRuin computeSunken(int cx, int cz) {
        long s0 = sunkenSeed;
        if (SeedMixer.cellUnit(s0, cx, cz, 0) > P_SUNKEN) {
            return null;
        }
        ColumnSample s = new ColumnSample();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = (int) Math.floor((cx + 0.15 + 0.7 * SeedMixer.cellUnit(s0, cx, cz, 1 + attempt * 2)) * SUNKEN_CELL);
            int z = (int) Math.floor((cz + 0.15 + 0.7 * SeedMixer.cellUnit(s0, cx, cz, 2 + attempt * 2)) * SUNKEN_CELL);
            model.sampleInto(x, z, s);
            stats.incrementAndGet(20);
            if (!s.isSea(seaLevel)) {
                continue;
            }
            double depth = seaLevel - s.height;
            if (depth < 10 || depth > 48 || s.trench > 0.05 || s.oceanRidge > 0.2 || s.coastCliff > 0.25 || s.fjord > 0.3) {
                stats.incrementAndGet(21);
                continue;
            }
            long seed = SeedMixer.hash2(s0, cx * 31 + attempt, cz);
            double pick = SeedMixer.unit(SeedMixer.mix(seed));
            int kind = pick < 0.3 ? SunkenRuin.COLONNADE : pick < 0.52 ? SunkenRuin.GATE : pick < 0.8 ? SunkenRuin.QUARTER : SunkenRuin.TOWER;
            int[] size = SunkenPlanner.size(kind, seed);
            if (depth < size[2] + 3) {
                kind = SunkenRuin.QUARTER;
                size = SunkenPlanner.size(kind, seed);
                if (depth < size[2] + 3) {
                    continue;
                }
            }
            int vdir = (int) (SeedMixer.unit(SeedMixer.mix(seed + 7)) * 4);
            int vx = RuinCell.DX[vdir];
            int vz = RuinCell.DZ[vdir];
            boolean mirror = SeedMixer.unit(SeedMixer.mix(seed + 11)) < 0.5;
            int ux = mirror ? -vz : vz;
            int uz = mirror ? vx : -vx;
            int ox = x - ux * (size[0] / 2) - vx * (size[1] / 2);
            int oz = z - uz * (size[0] / 2) - vz * (size[1] / 2);
            Canvas.Frame f = new Canvas.Frame(ox, oz, ux, uz, vx, vz);
            int reach = SunkenPlanner.REACH;
            int minX = Math.min(f.x(-reach, -reach), f.x(size[0] + reach, size[1] + reach));
            int maxX = Math.max(f.x(-reach, -reach), f.x(size[0] + reach, size[1] + reach));
            int minZ = Math.min(f.z(-reach, -reach), f.z(size[0] + reach, size[1] + reach));
            int maxZ = Math.max(f.z(-reach, -reach), f.z(size[0] + reach, size[1] + reach));
            if (minX < cx * SUNKEN_CELL + 8 || maxX >= (cx + 1) * SUNKEN_CELL - 8 || minZ < cz * SUNKEN_CELL + 8 || maxZ >= (cz + 1) * SUNKEN_CELL - 8) {
                continue;
            }
            // a level floor: the platform may not bridge a drop
            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;
            List<Double> heights = new ArrayList<>();
            for (int u = -2; u <= size[0] + 1; u += 3) {
                for (int v = -2; v <= size[1] + 1; v += 3) {
                    double hh = model.interpolatedHeight(f.x(u, v), f.z(u, v));
                    lo = Math.min(lo, hh);
                    hi = Math.max(hi, hh);
                    heights.add(hh);
                }
            }
            if (hi - lo > (kind == SunkenRuin.COLONNADE ? 3.5 : 4.5)) {
                stats.incrementAndGet(22);
                continue;
            }
            if (formationNear(x, z, 48, TerrainModel.SEA_STACK)) {
                continue;
            }
            Collections.sort(heights);
            int floorY = (int) Math.floor(heights.get(heights.size() / 2));
            if (seaLevel - floorY < size[2] + 3) {
                continue;
            }
            int biome = biome(x, floorY, z, s);
            boolean warm = filter != null ? RuinBiomes.warmSea(biome) : s.temperature > 0.2;
            boolean frozen = filter != null ? biome == RuinBiomes.OCEAN_FROZEN : s.temperature < -0.45;
            double r = SeedMixer.unit(SeedMixer.mix(seed + 13));
            int stone = warm ? (s.humidity < 0.0 ? RuinMaterials.SEA_SANDSTONE : RuinMaterials.MARBLE)
                : r < 0.35 ? RuinMaterials.SEA_TUFF : RuinMaterials.SEA_STONE;
            RuinMaterials mat = new RuinMaterials(stone, RuinMaterials.OAK, RuinMaterials.OAK, RuinMaterials.ROOF_STONE, RuinMaterials.OAK);
            double decay = 0.35 + 0.55 * SeedMixer.unit(SeedMixer.mix(seed + 17));
            int rx = f.x(size[0] / 2, size[1] / 2);
            int rz = f.z(size[0] / 2, size[1] / 2);
            stats.incrementAndGet(23);
            return new SunkenRuin(rx, floorY + 2, rz, seed, minX, minZ, maxX, maxZ, kind, f, floorY, decay, warm, frozen, mat, size[0], size[1],
                seaLevel - floorY);
        }
        return null;
    }

    /** Whether a rock formation of the given type (0: any) stands within {@code dist} blocks. */
    boolean formationNear(int x, int z, int dist, int type) {
        int cell = TerrainModel.FORMATION_CELL;
        for (int fx = Math.floorDiv(x - dist, cell); fx <= Math.floorDiv(x + dist, cell); fx++) {
            for (int fz = Math.floorDiv(z - dist, cell); fz <= Math.floorDiv(z + dist, cell); fz++) {
                TerrainModel.FormationSite f = model.formationSite(fx, fz);
                if (f != null && (type == 0 || f.type == type) && Math.abs(f.x - x) <= dist && Math.abs(f.z - z) <= dist) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the lip of a waterfall or cascade lies within {@code dist} blocks. */
    boolean waterfallNear(int x, int z, int dist) {
        FallPlanner planner = FallPlanner.of(model);
        int cell = FallPlanner.FALL_CELL;
        for (int fx = Math.floorDiv(x - dist, cell); fx <= Math.floorDiv(x + dist, cell); fx++) {
            for (int fz = Math.floorDiv(z - dist, cell); fz <= Math.floorDiv(z + dist, cell); fz++) {
                FallPlan w = planner.fall(fx, fz);
                if (w != null && Math.abs(w.lipX - x) <= dist && Math.abs(w.lipZ - z) <= dist) {
                    return true;
                }
            }
        }
        cell = FallPlanner.CASCADE_CELL;
        for (int fx = Math.floorDiv(x - dist, cell); fx <= Math.floorDiv(x + dist, cell); fx++) {
            for (int fz = Math.floorDiv(z - dist, cell); fz <= Math.floorDiv(z + dist, cell); fz++) {
                FallPlan w = planner.cascade(fx, fz);
                if (w != null && Math.abs(w.lipX - x) <= dist && Math.abs(w.lipZ - z) <= dist) {
                    return true;
                }
            }
        }
        return false;
    }

    // =========================================================================================
    // Queries
    // =========================================================================================

    /** All sites of a type planned in one cell of its grid (devtools and the finder). */
    public List<RuinSite> sitesInCell(int type, int cx, int cz) {
        List<RuinSite> out = new ArrayList<>();
        if (type == SUNKEN_RUIN) {
            SunkenRuin s = sunken(cx, cz);
            if (s != null) {
                out.add(s);
            }
            return out;
        }
        TrailSurvey.Cell cell = trailCell(cx, cz);
        switch (type) {
            case TRAIL_RUIN -> {
                if (cell.ruin() != null) {
                    out.add(cell.ruin());
                }
            }
            case SIGNPOST -> out.addAll(cell.signposts());
            case PATH_LIGHTS -> {
                if (cell.lights() != null) {
                    out.add(cell.lights());
                }
            }
            default -> {
            }
        }
        return out;
    }

    /**
     * Nearest built site of a type, searching outward in square rings of cells, like the
     * finder's other site searches. Sites refused by the world filter are skipped.
     */
    public static TerrainFinder.Result find(TerrainModel model, int type, int x, int z, int radius, BooleanSupplier cancelled) {
        RuinSites sites = of(model);
        int cell = type == SUNKEN_RUIN ? SUNKEN_CELL : TRAIL_CELL;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        int rings = radius / cell + 1;
        for (int r = 0; r <= rings; r++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            RuinSite best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (RuinSite s : sites.sitesInCell(type, cx + dx, cz + dz)) {
                        double dd = (double) (s.x - x) * (s.x - x) + (double) (s.z - z) * (s.z - z);
                        if (dd < bestD && sites.allowed(s)) {
                            bestD = dd;
                            best = s;
                        }
                    }
                }
            }
            if (best != null) {
                return new TerrainFinder.Result(best.x, best.y, best.z, TYPE_NAMES[type]);
            }
        }
        return null;
    }
}
