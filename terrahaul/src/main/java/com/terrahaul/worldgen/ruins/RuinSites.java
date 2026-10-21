package com.terrahaul.worldgen.ruins;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;
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
 * The smooth fields that decide which stretches of trail are lit, and how their lamps look, live
 * here too: they are pure functions of position, shared by every cell.
 * <p>
 * What the terrain model cannot know (the biome a column really gets, vanilla structures nearby,
 * the {@code trails} config) comes from a {@link WorldFilter} that the game installs when a world
 * loads. Devtools run without one: biomes are then estimated from the climate and nothing is
 * excluded.
 */
public final class RuinSites {
    /** Any ruin beside a trail: a broken house or one of the other kinds of {@link TrailRuin}. */
    public static final int TRAIL_RUIN = 1;
    public static final int SIGNPOST = 2;
    public static final int PATH_LIGHTS = 3;
    public static final int SUNKEN_RUIN = 4;
    public static final String[] TYPE_NAMES = {"", "path_ruin", "signpost", "path_lights", "sunken_ruin"};

    /**
     * Threshold of the lit field: about a fifth of the laid trail is lit, in sections a few
     * hundred blocks long (some 8 to 9 lamp posts per 1000 blocks of trail, once the scraps where
     * a trail only grazes a lit patch are left dark).
     */
    static final double LIT_THRESHOLD = 0.42;

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
    public final AtomicLongArray stats = new AtomicLongArray(64);

    /** Smooth fields over the trail network: which stretches are lit, on which side, in what style. */
    private final SimplexNoise litNoise;
    private final SimplexNoise lampNoise;

    private RuinSites(TerrainModel model) {
        this.model = model;
        this.seaLevel = model.seaLevel();
        this.trailSeed = SeedMixer.derive(model.siteSeed(), 0x2E1E5L);
        this.sunkenSeed = SeedMixer.derive(model.siteSeed(), 0x5D2E1E5L);
        this.litNoise = new SimplexNoise(SeedMixer.derive(trailSeed, 0x11F7L));
        this.lampNoise = new SimplexNoise(SeedMixer.derive(trailSeed, 0x1A3BL));
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

    /** The ruins, signposts and lamp posts planned in a trail cell. */
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

    /** The cached plan of a trail ruin or sunken ruin (plans are pure functions of their sites). */
    public RuinPlan plan(RuinSite site) {
        RuinPlan p = plans.get(site);
        if (p == null) {
            p = site instanceof TrailRuin t ? planOf(t) : SunkenPlanner.plan((SunkenRuin) site);
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

    /** The plan of a trail ruin, by its kind. */
    static RuinPlan planOf(TrailRuin t) {
        return switch (t.kind) {
            case TrailRuin.HOUSE -> HousePlanner.plan(t);
            case TrailRuin.WATCHTOWER, TrailRuin.CHAPEL, TrailRuin.BRIDGE -> LandmarkPlanner.plan(t);
            default -> WaysidePlanner.plan(t);
        };
    }

    /** The canonical box of a trail ruin of a kind, variant and (for houses) style. */
    static RuinSpec spec(int kind, int variant, int style) {
        return switch (kind) {
            case TrailRuin.HOUSE -> HousePlanner.spec(variant, style);
            case TrailRuin.WATCHTOWER, TrailRuin.CHAPEL -> LandmarkPlanner.spec(kind, variant);
            default -> WaysidePlanner.spec(kind, variant);
        };
    }

    /** Name of a variant of a ruin that is not a house. */
    public static String variantName(int kind, int variant) {
        String[] names = switch (kind) {
            case TrailRuin.WATCHTOWER -> LandmarkPlanner.TOWER_NAMES;
            case TrailRuin.CHAPEL -> LandmarkPlanner.CHAPEL_NAMES;
            case TrailRuin.BRIDGE -> LandmarkPlanner.BRIDGE_NAMES;
            case TrailRuin.WELL -> WaysidePlanner.WELL_NAMES;
            case TrailRuin.WAGON -> WaysidePlanner.WAGON_NAMES;
            case TrailRuin.SHRINE -> WaysidePlanner.SHRINE_NAMES;
            case TrailRuin.CAMPSITE -> WaysidePlanner.CAMP_NAMES;
            case TrailRuin.FIELD -> WaysidePlanner.FIELD_NAMES;
            default -> HousePlanner.LAYOUT_NAMES;
        };
        return variant >= 0 && variant < names.length ? names[variant] : "?";
    }

    // =========================================================================================
    // Lit trails and their lamps
    // =========================================================================================

    /**
     * Whether the trail at a position belongs to a lit section: a smooth field whose patches are
     * several hundred blocks across, so a trail that enters one stays lit for a long way. The
     * small second octave only frays the edges; stronger, it would cut sections into scraps.
     */
    boolean lit(int x, int z) {
        double n = litNoise.noise2(x / 1200.0, z / 1200.0) + 0.1 * litNoise.noise2(x / 210.0 + 37.1, z / 210.0 - 11.3);
        return n > LIT_THRESHOLD;
    }

    /** The side of the trail the lamps keep to (+1 or -1), over runs of a few hundred blocks. */
    int lampSide(int x, int z) {
        return lampNoise.noise2(x / 380.0 + 13.7, z / 380.0) >= 0.0 ? 1 : -1;
    }

    /** Sections where the lamps alternate from one side of the trail to the other. */
    boolean alternating(int x, int z) {
        return lampNoise.noise2(x / 600.0 - 41.0, z / 600.0 + 7.0) > 0.35;
    }

    /**
     * A lamp post at a column: its style follows the climate and a broad field of local custom,
     * its decay and wood follow smooth fields too, so a lit section looks the same all along.
     */
    LightStretch.Post lampPost(int x, int z, int toTrail, int climate, int biome, ColumnSample s, boolean keeper) {
        double band = lampNoise.noise2(x / 1400.0 + 3.3, z / 1400.0 - 8.1);
        int style = switch (climate) {
            case COLD, UPLAND -> band < 0.0 ? LightStretch.CHAIN_LAMP : LightStretch.LAMP_ARM;
            case HOT_DRY -> LightStretch.POST_TOP;
            case HOT_WET -> LightStretch.BAMBOO;
            default -> biome == RuinBiomes.CHERRY || biome == RuinBiomes.MEADOW ? (band < 0.2 ? LightStretch.STONE_LANTERN : LightStretch.LAMP_ARM)
                : band < -0.3 ? LightStretch.STONE_LANTERN : band < 0.35 ? LightStretch.LAMP_ARM : LightStretch.POST_TOP;
        };
        long seed = SeedMixer.hash2(trailSeed ^ 0x11647L, x, z);
        double decay = Math.max(0.15, Math.min(0.9, 0.5 + 0.45 * lampNoise.noise2(x / 300.0 - 5.0, z / 300.0 + 22.0)
            + 0.1 * (SeedMixer.unit(seed) - 0.5)));
        double wet = Math.max(0.0, Math.min(1.0, (s.humidity + 0.6) / 1.2));
        boolean soul = (biome == RuinBiomes.DARK_FOREST || biome == RuinBiomes.TAIGA || biome == RuinBiomes.SWAMP)
            && lampNoise.noise2(x / 900.0 + 77.0, z / 900.0) > 0.3;
        // the wood of a region, not of a post: one seed per 512-block square
        long region = SeedMixer.hash2(trailSeed ^ 0x2E610L, x >> 9, z >> 9);
        RuinMaterials mat = materials(TrailRuin.HOUSE, HousePlanner.COTTAGE, HousePlanner.TIMBER_FRAME, climate, biome, s, region);
        return new LightStretch.Post(x, z, toTrail, style, decay, wet, soul, mat, seed, keeper);
    }

    // =========================================================================================
    // Climate, biome and materials
    // =========================================================================================

    /**
     * Whether a trail is drawn at a column: laid there ({@link TrailGeometry#laid}) in a biome that
     * shows trails (the surface rule draws none in deserts or on beaches, so nothing stands
     * beside them). The devtools measure trail length with it.
     */
    public boolean trailShown(int x, int z) {
        if (!TrailGeometry.laid(model, x, z)) {
            return false;
        }
        ColumnSample s = model.sample(x & ~3, z & ~3);
        return RuinBiomes.trailVisible(biome(x, (int) s.height, z, s));
    }

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

    /**
     * Vernacular materials: local stone by geology and climate, local wood by biome, adjusted to
     * the building's style (a log cabin of the local logs, adobe of mud brick or sandstone, a
     * raised house of bamboo or jungle wood).
     */
    static RuinMaterials materials(int kind, int variant, int style, int climate, int biome, ColumnSample s, long seed) {
        double r1 = SeedMixer.unit(SeedMixer.mix(seed ^ 0x57014EL));
        double r2 = SeedMixer.unit(SeedMixer.mix(seed ^ 0x3009L));
        double r3 = SeedMixer.unit(SeedMixer.mix(seed ^ 0x1EAFL));
        boolean house = kind == TrailRuin.HOUSE;
        int stone;
        if (climate == HOT_DRY || house && style == HousePlanner.ADOBE) {
            boolean sand = s.geology == Geology.SEDIMENTARY || s.humidity < -0.45 || !house && r2 < 0.4;
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
            // chapels and towers were built of dressed stone where the local stone allowed
            if ((kind == TrailRuin.CHAPEL || kind == TrailRuin.WATCHTOWER) && stone == RuinMaterials.COBBLE && r1 < 0.6) {
                stone = RuinMaterials.ASHLAR;
            }
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
        if (house && style == HousePlanner.LOG_CABIN && (wood == RuinMaterials.BIRCH || wood == RuinMaterials.CHERRY)) {
            // a log cabin is built of the big trees of the place
            wood = r3 < 0.5 ? RuinMaterials.SPRUCE : RuinMaterials.OAK;
        }
        if (house && style == HousePlanner.RAISED && r3 < 0.6 || kind == TrailRuin.SHRINE && climate == HOT_WET && r3 < 0.5) {
            wood = RuinMaterials.BAMBOO;
        } else if (house && style == HousePlanner.RAISED) {
            wood = RuinMaterials.JUNGLE;
        }
        int roofWood = switch (wood) {
            case RuinMaterials.OAK, RuinMaterials.BIRCH -> r3 < 0.5 ? RuinMaterials.SPRUCE : RuinMaterials.DARK_OAK;
            case RuinMaterials.CHERRY, RuinMaterials.BAMBOO -> RuinMaterials.DARK_OAK;
            case RuinMaterials.SPRUCE -> r3 < 0.3 ? RuinMaterials.DARK_OAK : RuinMaterials.SPRUCE;
            default -> wood;
        };
        int cover = RuinMaterials.ROOF_WOOD;
        if (stone == RuinMaterials.SLATE || wood == RuinMaterials.CHERRY && r3 < 0.5 || kind == TrailRuin.CHAPEL && r2 < 0.35) {
            cover = RuinMaterials.ROOF_TILE;
        } else if (house && style == HousePlanner.STONE && r2 < 0.5 || kind == TrailRuin.WATCHTOWER && r2 < 0.5
            || kind == TrailRuin.CHAPEL && r2 < 0.6) {
            cover = RuinMaterials.ROOF_STONE;
        }
        int leaves = wood == RuinMaterials.BAMBOO ? RuinMaterials.JUNGLE : wood == RuinMaterials.OAK && r1 > 0.7 ? RuinMaterials.AZALEA : wood;
        return new RuinMaterials(stone, wood, roofWood, cover, leaves);
    }

    /** Vanilla chest loot that fits the ruin, or {@code null} for ruins without a container. */
    static String lootTable(int kind, int variant, int climate, int biome, RuinMaterials mat) {
        String base = "minecraft:chests/village/";
        return switch (kind) {
            case TrailRuin.HOUSE -> {
                if (variant == HousePlanner.TOWER_HOUSE) {
                    yield base + "village_weaponsmith";
                }
                if (variant == HousePlanner.FARMHOUSE) {
                    yield base + "village_butcher";
                }
                yield switch (climate) {
                    case COLD -> base + (biome == RuinBiomes.SNOWY ? "village_snowy_house" : "village_taiga_house");
                    case UPLAND -> base + (variant == HousePlanner.HUT ? "village_shepherd" : "village_taiga_house");
                    case HOT_DRY -> base + (mat.stone == RuinMaterials.SANDSTONE || mat.stone == RuinMaterials.RED_SANDSTONE
                        ? "village_desert_house" : "village_savanna_house");
                    default -> base + "village_plains_house";
                };
            }
            case TrailRuin.WATCHTOWER -> base + "village_weaponsmith";
            case TrailRuin.CHAPEL -> base + "village_temple";
            case TrailRuin.WAGON -> "minecraft:chests/shipwreck_supply";
            case TrailRuin.CAMPSITE -> variant == WaysidePlanner.HUNTERS ? base + "village_tannery" : base + "village_fletcher";
            default -> null;
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

    /** No water in a column ({@link #waterKind}). */
    public static final int DRY = 0;
    /** The channel of a stream. */
    public static final int STREAM = 1;
    /** A river, a lake, a pond or the sea. */
    public static final int BROAD_WATER = 2;

    /**
     * Whether a column holds water: the sea, a river, a lake, or a stream (whose channel the
     * water solver digs within about one and a half blocks of its centre line, wider where it is
     * stronger; half a block more is counted to be safe).
     */
    public static boolean water(TerrainModel m, int x, int z) {
        return waterKind(m, x, z) != DRY;
    }

    /** What water a column holds: {@link #DRY}, a {@link #STREAM} or {@link #BROAD_WATER}. */
    public static int waterKind(TerrainModel m, int x, int z) {
        ColumnSample s = m.sample(x & ~3, z & ~3);
        if (s.waterLevel != ColumnSample.NO_WATER && s.waterLevel > s.height || s.river > 0.0 || s.lake > 0.0 || s.height < m.seaLevel()) {
            return BROAD_WATER;
        }
        // the water solver takes a stream's strength from the nearest grid corner, this test from
        // the one below: either may be the only one that knows a weak stream is there
        double strength = Math.max(s.stream, m.sample((x + 2) & ~3, (z + 2) & ~3).stream);
        return strength > 0.0 && m.streamDistance(x, z) < 1.25 + 1.5 * strength + 0.5 ? STREAM : DRY;
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
            case TRAIL_RUIN -> out.addAll(cell.ruins());
            case SIGNPOST -> out.addAll(cell.signposts());
            case PATH_LIGHTS -> out.addAll(cell.lights());
            default -> {
            }
        }
        return out;
    }

    /**
     * A finder selector: a site type, narrowed for trail ruins to one {@link TrailRuin} kind
     * ({@code kind} -1: any kind).
     */
    public static int selector(int type, int kind) {
        return type + 16 * (kind + 1);
    }

    /** Fewest posts of a lit section the finder leads to: a lone post at a cell border is not worth the walk. */
    static final int MIN_FOUND_POSTS = 3;

    /** Whether a site matches a selector. */
    public static boolean matches(RuinSite s, int selector) {
        int kind = (selector >> 4) - 1;
        if (s.type != (selector & 15)) {
            return false;
        }
        if (s instanceof LightStretch l) {
            return l.posts.length >= MIN_FOUND_POSTS;
        }
        return kind < 0 || s instanceof TrailRuin t && t.kind == kind;
    }

    /**
     * Nearest built site matching a {@link #selector}, searching outward in square rings of cells,
     * like the finder's other site searches. Sites refused by the world filter are skipped, the
     * same test the feature applies before building, so what is found is always there. A site in
     * ring r can be farther than one in ring r + 1, so the search goes on until a ring's nearest
     * possible site is farther than the best one found. The result is the site's {@link #visit}
     * point, a place to stand and look at it from.
     */
    public static TerrainFinder.Result find(TerrainModel model, int selector, String label, int x, int z, int radius, BooleanSupplier cancelled) {
        RuinSites sites = of(model);
        int type = selector & 15;
        int cell = type == SUNKEN_RUIN ? SUNKEN_CELL : TRAIL_CELL;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        int rings = radius / cell + 1;
        RuinSite best = null;
        double bestD = Double.MAX_VALUE;
        for (int r = 0; r <= rings; r++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            // every site of ring r lies at least (r - 1) cells from the origin
            double inner = Math.max(0, r - 1) * (double) cell;
            if (best != null && inner * inner > bestD) {
                break;
            }
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (RuinSite s : sites.sitesInCell(type, cx + dx, cz + dz)) {
                        double dd = (double) (s.x - x) * (s.x - x) + (double) (s.z - z) * (s.z - z);
                        if (dd < bestD && matches(s, selector) && sites.allowed(s)) {
                            bestD = dd;
                            best = s;
                        }
                    }
                }
            }
        }
        if (best == null) {
            return null;
        }
        int[] v = sites.visit(best);
        return new TerrainFinder.Result(v[0], v[1], v[2], label);
    }

    /**
     * Where the finder sends a player to look at a site from ({@link Standpoint}): the trail in
     * front of a trail ruin, the path side of a signpost or of the middle post of a lit stretch,
     * the floor of a sunken ruin. Never inside a block, down a shaft or in water. Worked out from
     * the site's plans when first asked, then kept in the site.
     */
    public int[] visit(RuinSite s) {
        int[] v = s.visit;
        if (v != null) {
            return v;
        }
        List<RuinPlan> plans = new ArrayList<>();
        int x0;
        int z0;
        if (s instanceof TrailRuin t) {
            plans.add(plan(t));
            x0 = t.trailX;
            z0 = t.trailZ;
        } else if (s instanceof Signpost p) {
            plans.add(SignpostPlanner.plan(this, p));
            x0 = p.x + 2 * RuinCell.DX[p.toTrail];
            z0 = p.z + 2 * RuinCell.DZ[p.toTrail];
        } else if (s instanceof LightStretch l) {
            // the middle post, at which the stretch is found
            LightStretch.Post mid = l.posts[0];
            for (LightStretch.Post p : l.posts) {
                if (p.x() == l.x && p.z() == l.z) {
                    mid = p;
                }
            }
            for (LightStretch.Post p : l.posts) {
                if (Math.abs(p.x() - mid.x()) <= 16 && Math.abs(p.z() - mid.z()) <= 16) {
                    plans.add(LampPostPlanner.plan(p));
                }
            }
            x0 = mid.x() + 2 * RuinCell.DX[mid.toTrail()];
            z0 = mid.z() + 2 * RuinCell.DZ[mid.toTrail()];
        } else {
            plans.add(plan(s));
            x0 = s.x;
            z0 = s.z;
        }
        v = Standpoint.find(model, plans, x0, z0, s instanceof SunkenRuin);
        s.visit = v;
        return v;
    }
}
