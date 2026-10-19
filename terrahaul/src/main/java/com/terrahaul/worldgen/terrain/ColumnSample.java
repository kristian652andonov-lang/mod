package com.terrahaul.worldgen.terrain;

/**
 * Everything the terrain model knows about one block column. Instances are mutable and reused
 * by the per-thread cache in {@link TerrainModel}; callers must copy values they want to keep.
 */
public final class ColumnSample {
    /** No elevated water body covers the column. */
    public static final double NO_WATER = -1.0E9;

    public int x;
    public int z;

    // --- geometry -------------------------------------------------------------------------
    /** Terrain surface height (top of the solid ground, before 3D detail and caves). */
    public double height;
    /** Height before rivers, lakes and canyons were carved; used for valley walls. */
    public double uncarvedHeight;
    /**
     * Height after canyons were carved but before rivers and lakes were, so audits can tell the
     * walls of water bodies from those of the landforms they cut through.
     */
    public double preWaterHeight;
    /**
     * Surface of a water body above sea level (elevated river or lake), or {@link #NO_WATER}.
     * Water at or below sea level is placed by the vanilla fluid system and is not recorded here.
     */
    public double waterLevel;
    /** Surface of a lava pool in a volcano crater, or {@link #NO_WATER}. */
    public double lavaLevel;
    /** Amplitude of 3D overhang carving on cliffs, 0..1. */
    public double cliff;
    /** Approximate slope class from the shaping functions, 0 (flat) .. 1 (cliff). */
    public double steepness;

    // --- classification -------------------------------------------------------------------
    public double mountain;
    /** Relative elevation inside mountain/hill terrain: 0 valley floor .. 1 summit. */
    public double peakness;
    public double alpine;
    public double ancient;
    public double wall;
    public double plateau;
    public double meadow;
    public double windswept;
    public double swamp;
    public double volcano;
    /** Volcano crater interior (0..1); lava or water pools may form here. */
    public double crater;
    public double canyon;
    /** Canyon floor elevation when {@link #canyon} is positive. */
    public double canyonFloor;
    /** 1 at a river centreline, falling to 0 at the bank. */
    public double river;
    /** 0..1 influence of the surrounding river valley or floodplain. */
    public double riverValley;
    /** River half-width in blocks (0 when no river is near). */
    public double riverWidth;
    /** Water level of the nearest major river. */
    public double riverLevel;
    /** Distance to the river centreline divided by its half width. */
    public double riverDistance;
    /** True when the river is in an arid region and runs dry. */
    public boolean dryRiver;
    /** 1 at a stream centreline (small creeks carved at block resolution by a feature). */
    public double stream;
    /**
     * Water body this corner belongs to ({@link WaterBody}); its level is valid when
     * {@link #bodyInside} is set, which marks the channel of a wet river, the interior of a lake or
     * the flooded part of a crater (lava for {@link WaterBody#LAVA}).
     */
    public int bodyKind;
    public boolean bodyInside;
    /**
     * Exclusive top of that body's surface (the top fluid block is one below), an integer; bodies
     * at sea level have the sea level. Surface water is placed from these by the water solver.
     */
    public double bodyLevel;
    /**
     * Unrounded river surface; interpolating it between corners places a river's steps at block
     * resolution. Equal to {@link #bodyLevel} for lakes and craters.
     */
    public double bodyLevelCont;
    /** 0..1: how much the river here belongs to the great-river network. */
    public double riverGreat;
    /** Aridity (0..1) where that river's surface is decided; dry riverbeds lie where it exceeds about 0.6. */
    public double riverAridity;
    /** Memo of {@code TerrainModel.nearbyWaterLevel} for this corner ({@code NaN}: not computed). */
    public double nearbyWater;
    public double lake;
    public int lakeType;
    public double lakeLevel;
    public double coastCliff;
    public double beach;
    public double island;
    public double fjord;
    /** Deep-sea trench strength (1 on the trench axis). */
    public double trench;
    /** Floor height of the trench this column belongs to. */
    public double trenchFloor;
    /** Crest of a mid-ocean ridge (0..1). */
    public double oceanRidge;
    /** Distance to the nearest trail centre line in blocks (large where there is none). */
    public double trailDistance;
    /**
     * Highest model surface 4 blocks away (any of the four axis directions), computed on demand by
     * the 3D terrain detail ({@code NaN} until then); {@link #overhangDir} is that direction.
     */
    public double overhangTop;
    /**
     * Lowest model surface within 20 blocks, used for biome depth and cave guards, or NaN until
     * {@code TerrainDensityFunction} first needs it; cached here because it takes 8 neighbour samples.
     */
    public double depthSurface;
    /** Lowest model surface 4 blocks away; set together with {@link #overhangTop}. */
    public double overhangFar;
    /** Index of the direction of {@link #overhangTop} in {@code TerrainDetail}'s direction table. */
    public int overhangDir;
    /**
     * Big curved overhang over this column, the largest of the four directions: rim height of the
     * cliff it hangs from, or NaN if not computed yet; {@link #bigOverhangDist} is this column's
     * distance from the cliff face and {@link #bigOverhangRadius} the radius of its curved
     * underside (0: none). The per-direction plans are in {@link #overhangPlan}.
     */
    public double bigOverhangRim;
    public double bigOverhangDist;
    public double bigOverhangRadius;
    /**
     * Per direction (4 entries of 4 values: cells to the rim column, rim height, radius, lip
     * thickness) the big curved overhangs this column belongs to; allocated and owned by
     * {@code TerrainDetail}.
     */
    public double[] overhangPlan;
    /**
     * The overhang plans of this column before the check that they continue along the cliff, with
     * the column's x and z in the last two entries (they depend on the position alone); allocated
     * and owned by {@code TerrainDetail}.
     */
    public double[] overhangRawPlan;
    /**
     * Raw 3D detail values at the interpolation corners of this column, cached by
     * {@code TerrainDetail} ({@code NaN}: not computed; allocated on first use); index 0 is the
     * corner at {@link #detailTop}, then every 8 blocks down.
     */
    public double[] detailCache;
    /** Corner height of {@code detailCache[0]}, or {@link Integer#MIN_VALUE} when the cache is empty. */
    public int detailTop = Integer.MIN_VALUE;
    /** Geology province index (see {@link Geology}). */
    public int geology;
    /** Fine strata selector noise in [0, 1). */
    public double strata;

    // --- climate parameters handed to the vanilla multi-noise biome source ------------------
    public double continentalness;
    public double erosion;
    public double weirdness;
    public double temperature;
    public double humidity;
    /** Climate temperature before altitude cooling. */
    public double baseTemperature;

    /** Internal continentalness before biome-consistency adjustments. */
    public double rawContinentalness;

    public void copyFrom(ColumnSample o) {
        x = o.x;
        z = o.z;
        height = o.height;
        uncarvedHeight = o.uncarvedHeight;
        preWaterHeight = o.preWaterHeight;
        waterLevel = o.waterLevel;
        lavaLevel = o.lavaLevel;
        cliff = o.cliff;
        steepness = o.steepness;
        mountain = o.mountain;
        peakness = o.peakness;
        alpine = o.alpine;
        ancient = o.ancient;
        wall = o.wall;
        plateau = o.plateau;
        meadow = o.meadow;
        windswept = o.windswept;
        swamp = o.swamp;
        volcano = o.volcano;
        crater = o.crater;
        canyon = o.canyon;
        canyonFloor = o.canyonFloor;
        river = o.river;
        riverValley = o.riverValley;
        riverWidth = o.riverWidth;
        riverLevel = o.riverLevel;
        riverDistance = o.riverDistance;
        dryRiver = o.dryRiver;
        stream = o.stream;
        bodyKind = o.bodyKind;
        bodyInside = o.bodyInside;
        bodyLevel = o.bodyLevel;
        bodyLevelCont = o.bodyLevelCont;
        riverGreat = o.riverGreat;
        riverAridity = o.riverAridity;
        nearbyWater = Double.NaN;
        lake = o.lake;
        lakeType = o.lakeType;
        lakeLevel = o.lakeLevel;
        coastCliff = o.coastCliff;
        beach = o.beach;
        island = o.island;
        fjord = o.fjord;
        trench = o.trench;
        trenchFloor = o.trenchFloor;
        oceanRidge = o.oceanRidge;
        trailDistance = o.trailDistance;
        // the lazily computed 3D detail caches belong to the model's own samples; a copy recomputes
        // them on demand
        overhangTop = Double.NaN;
        bigOverhangRim = Double.NaN;
        detailTop = Integer.MIN_VALUE;
        geology = o.geology;
        strata = o.strata;
        continentalness = o.continentalness;
        erosion = o.erosion;
        weirdness = o.weirdness;
        temperature = o.temperature;
        humidity = o.humidity;
        baseTemperature = o.baseTemperature;
        rawContinentalness = o.rawContinentalness;
    }

    /** True if the column's surface is under sea water. */
    public boolean isSea(int seaLevel) {
        return height < seaLevel - 0.5 && lake <= 0.0 && river <= 0.0;
    }
}
