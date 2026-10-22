package com.terrahaul.worldgen.surface;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.TerrainModel;

/**
 * The arithmetic behind Terrahaul's material fades, kept free of Minecraft types so that the
 * development tools can render and measure exactly what the surface rule and the rock formations
 * place (see {@code FadeStudy}).
 * <p>
 * A fade is a gradient through the stops of one single-colour family of blocks, for example tuff,
 * andesite and stone. {@link #stop} picks the stop for a position along the gradient, and the
 * boundaries between stops are moved by smooth noise ({@link #wobble}) so that they wander as wavy
 * bands and broad patches. Nothing here uses per-block random numbers: neighbouring blocks only
 * differ where a smooth field crosses a threshold between them, which is what keeps the fades free
 * of speckle.
 */
public final class Fades {
    /** Largest shift of a boundary between gradient stops, in stops, at a noise value of 1. */
    public static final double SHIFT = 0.45;
    /** Strata cycle in sedimentary rock (thin, regular beds), in blocks. */
    public static final double SEDIMENTARY_PERIOD = 16.0;
    /** Strata cycle in other rock, in blocks. */
    public static final double ROCK_PERIOD = 24.0;

    private Fades() {
    }

    /**
     * Index of the stop at position {@code t} (0..1) of a gradient with {@code stops} stops. The
     * boundaries between stops move by up to {@link #SHIFT} of a stop with the smooth noise
     * {@code n}, so neighbouring blocks almost always agree.
     */
    public static int stop(int stops, double t, double n) {
        double f = Math.max(0.0, Math.min(1.0, t)) * (stops - 1) + n * SHIFT;
        int i = (int) Math.floor(f + 0.5);
        return Math.max(0, Math.min(stops - 1, i));
    }

    /** Smooth 2D noise in about [-1, 1] at the given feature size. */
    public static double patch(TerrainModel model, int x, int z, double scale, int channel) {
        return model.detail3(channel, x / scale, channel * 17.0, z / scale);
    }

    /**
     * Smooth 3D noise in about [-1, 1] that bends the boundaries between gradient stops. Its
     * features are 5-6 blocks across: large enough that a boundary never breaks up into single
     * blocks, small enough that it does not look ruled.
     */
    public static double wobble(TerrainModel model, int x, int y, int z) {
        return model.detail3(6, x / 6.0, y / 4.5, z / 6.0);
    }

    // =========================================================================================
    // strata
    // =========================================================================================

    /**
     * Column part of the height of the strata: a slow tilt across hundreds of blocks, gentle
     * folds and small waves. It is a continuous function of the block position, so layers line up
     * from one column to the next (a value taken from the 4-block model grid would shift them in
     * 4-block steps and draw vertical seams on cliffs).
     */
    public static double strataWarp(TerrainModel model, int x, int z) {
        return 28.0 * model.detail3(7, x / 520.0, 401.0, z / 520.0)
            + 6.0 * patch(model, x, z, 70.0, 1)
            + 1.5 * patch(model, x, z, 19.0, 3);
    }

    /**
     * Position (about 0..1) in a rock family of the stratum at {@code (x, y, z)}. The family is
     * cycled as a triangle wave (fresh, weathered, fresh, ...) of the given period; the thickness
     * of single beds varies by noise along the height, and a second noise makes some beds thicker
     * or skip a stop, so the sequence is irregular like real bedding. Layers are horizontal apart
     * from {@code warp} (see {@link #strataWarp}).
     */
    public static double strata(TerrainModel model, int x, int y, int z, double warp, double period) {
        // the phase must keep rising with y (the noise derivative stays below the linear term)
        double phase = (y + warp) / period + 0.28 * model.detail3(2, x / 260.0, y / (period * 1.1) + 77.0, z / 260.0);
        double tri = Math.abs(phase - Math.floor(phase) - 0.5) * 2.0;
        return 1.0 - tri + 0.2 * model.detail3(3, x / 180.0, y / (period * 0.55) + 313.0, z / 180.0);
    }

    /**
     * How far the 3D noise moves the boundaries between beds, as a fraction of {@link #SHIFT}:
     * enough to make them wavy, little enough that a bed never dissolves into patches.
     */
    public static final double STRATA_WOBBLE = 0.4;

    /** Stop of a rock family in the strata at a block ({@code n}: {@link #wobble} there). */
    public static int strataStop(TerrainModel model, int stops, int x, int y, int z, double warp, double period, double n) {
        return stop(stops, strata(model, x, y, z, warp, period), n * STRATA_WOBBLE);
    }

    /**
     * Stop of a rock family for bare rock {@code d} blocks below an exposed top: broad patches
     * (about 18 blocks) of the paler stops at the surface, fresher rock a few blocks in.
     */
    public static int bareRockStop(TerrainModel model, int stops, int x, int z, int d, double n) {
        return stop(stops, 0.65 + 0.45 * patch(model, x, z, 18.0, 5) + d * 0.15, n * 0.25);
    }

    // =========================================================================================
    // mountains: rock in chaotic blobs, patchy snow, plants on ledges
    // =========================================================================================
    //
    // Level strata drew contour lines all the way round a mountain: every step of a slope cuts the
    // same beds at the same heights. Real mountain rock is a jumble instead: lenses of different
    // rock, tilted and broken, rubble and gravel in cracks, moss and grass on ledges, and snow that
    // lies in patches, fills gullies, clings to high faces and leaves ridges bare. Mountain rock is
    // therefore a mosaic of blobs from a few independent smooth 3D fields: each field picks one
    // material where it is high or low, so a blob is one block type with a ragged outline, and
    // because the fields are independent their blobs overlap and cut each other instead of nesting
    // in rings. Nothing is chosen per block at random, so there is no speckle.

    /** Ground of a mountain column, from its biome: what covers the rock. */
    public static final int GROUND_SOIL = 0;
    /** Bare rock (stony peaks). */
    public static final int GROUND_ROCK = 1;
    /** Snowy peaks and slopes. */
    public static final int GROUND_SNOW = 2;
    /** Deserts: sand, with rock where steep. */
    public static final int GROUND_SAND = 3;

    /** Not decided by the mosaic: the column's ordinary soil (or sand) profile applies. */
    public static final int SOIL = -1;
    /** The three stops of the province's rock family, palest first (see the surface rule's families). */
    public static final int ROCK_PALE = 0;
    public static final int ROCK_MID = 1;
    public static final int ROCK_FRESH = 2;
    /** A second rock of the province in large blobs: granite, andesite, terracotta or cobblestone. */
    public static final int ROCK_PROVINCE = 3;
    /** Broken rock: cobblestone (cobbled deepslate in dark provinces). */
    public static final int COBBLE = 4;
    public static final int MOSSY_COBBLE = 5;
    /** Gravel; the caller turns it into cobble where it would fall. */
    public static final int GRAVEL = 6;
    /** Earth tones showing through snow: terracotta (granite in granite provinces). */
    public static final int EARTH = 7;
    public static final int COARSE_DIRT = 8;
    public static final int DIRT = 9;
    public static final int MOSS = 10;
    public static final int GRASS = 11;
    public static final int SNOW = 12;
    /** Sand drifted onto ledges of desert mountains; the caller consolidates it where it would fall. */
    public static final int SAND = 13;
    /** Sandstone lenses of desert mountains. */
    public static final int SANDSTONE = 14;
    /** Needle litter of the mountain forests (see {@link MountainFlora}). */
    public static final int PODZOL = 15;

    /**
     * Per-column values of the mountain mosaic, computed once per column by {@link #mountain} and
     * read for every block of the column by {@link #mountainBlock}.
     */
    public static final class Mountain {
        /** How mountainous the column is (0..1): the share of blocks in the mosaic instead of strata. */
        public double share;
        /** One of the {@code GROUND_} constants. */
        public int ground;
        /** Share of the ground under snow here (0..1), after slope and gullies. */
        public double snow;
        /**
         * Snow test per depth below the surface (0..3): snow lies where the snow field's raw
         * value is below this (see {@link #snowAt} and {@link Fades#snowCut}).
         */
        public final double[] snowCut = new double[4];
        /** Positive: the column is a rock outcrop in soil or sand ground. */
        public double rock;
        /** Smoothed slope (rise per block over 8 blocks). */
        public double steep;
        /** Height of the surroundings above the column: positive in gullies, negative on ridges. */
        public double hollow;
        /** Vertical shift of the rock lenses, which tilts and bends them. */
        public double warp;
        /** How readily grass and moss grow on ledges (0..1). */
        public double green;
        /** Positive: moss rather than grass on ledges (humid ground). */
        public double moss;
        /** Plant patch field on ledges, in about [-1, 1]. */
        public double plants;
        /** Field of dirt and gravel patches on gentle rock, in about [-1, 1]. */
        public double scree;
        /** Threshold of the lens field below which the province's second rock shows. */
        public double provinceCut;
        /** Threshold of the lens fields above which the weathered stop shows. */
        public double paleCut;
        /** The stop that makes up most of the rock, and the one of the broad lenses between it. */
        public int majority;
        public int minority;
        /**
         * The snow field's raw value below which bare ground near the surface shows earth: the
         * thawed edges and holes of the snow patches (negative infinity where there is no snow).
         */
        public double earthCut;
        /** Threshold of the rubble field: low where rubble blobs are many and large, high where rare. */
        public double rubble;
        /** Boundary noise between the mosaic and the strata of the foothills. */
        public double edge;
        /**
         * Positive where the vegetation of the flanks has soil ({@link MountainFlora.Zone#soil},
         * set by the surface rule after {@link Fades#mountain}; 0 or less: none).
         */
        public double flora;
        /**
         * Whether that soil takes the place of the snow patches (below the tree line) or only fills
         * the gaps between them (krummholz and meadows).
         */
        public boolean floraOverSnow;
        /** Top block of the flora soil, a material code. */
        public int floraTop;
        /** Depth of the flora soil in blocks. */
        public double floraDepth;
        /**
         * Whether only the topmost block (the tread of a step) is soil and the risers below it
         * stay rock: on steep faces, and on stepped ground without a closed canopy.
         */
        public boolean floraTopOnly;
    }

    /** Height field that {@link #mountain} reads gullies and ridges from (the model's, or a test scene's). */
    @FunctionalInterface
    public interface Heights {
        double at(int x, int z);
    }

    /**
     * Fills the per-column values of the mosaic. {@code col} holds the column's interpolated
     * fields, {@code share} is its {@link #mountainShare} (positive), {@code ground} and
     * {@code biomeTemperature} (the base temperature of the biome at the top) come from the
     * biome. The gullies and ridges ({@link #hollow}, nine height reads) are only measured where
     * snow or plants can lie.
     *
     * @param smoothSlope {@link #smoothSlope} of the column
     * @param heights     the terrain height field, for {@link #hollow}
     */
    public static void mountain(TerrainModel model, int x, int z, ColumnSample col, double share, int ground,
                                double biomeTemperature, double smoothSlope, Heights heights, Mountain out) {
        out.share = share;
        out.ground = ground;
        out.steep = smoothSlope;
        // tilts of up to 30-40 degrees that turn over a few dozen blocks
        double tilt = plane(model, 2, x, z, 90.0, 2300.0);
        out.warp = 16.0 * tilt + 6.0 * plane(model, 3, x, z, 31.0, 2350.0);
        out.edge = share < 1.0 ? plane(model, 3, x, z, 30.0, 2650.0) : 0.0;
        // the province's second rock is common in some places and rare in others (the tilt
        // field serves again: nothing ties the two together that the eye could see)
        out.provinceCut = (col.geology == Geology.GRANITE ? -0.3 : -0.45) + 0.3 * tilt;
        // each family keeps its own colour: the pale limestone is mostly diorite (calcite, the
        // weathered stop, is rarer there, as its blobs looked like snow), the dark rock mostly
        // smooth basalt, the grey rock mostly stone
        boolean midMajority = col.geology == Geology.DARK || col.geology == Geology.LIMESTONE;
        out.majority = midMajority ? ROCK_MID : ROCK_FRESH;
        out.minority = midMajority ? ROCK_FRESH : ROCK_MID;
        // the weathered stop too is common on some slopes and rare on others
        out.paleCut = (col.geology == Geology.LIMESTONE ? 0.8 : 0.65) + 0.25 * plane(model, 5, x, z, 70.0, 2700.0);

        // Snow: the stony and the snowy peaks share one share of snowy ground, from the
        // temperature and the slope (see snowCover), so the snow line runs on across the edge
        // between them instead of stepping there; the band wanders with large-scale noise. Gullies collect snow,
        // ridges are blown bare, and a broad field makes whole slopes snowier or barer, so the
        // rock shows in blobs of every size.
        double broad = plane(model, 4, x, z, 38.0, 2400.0);
        double t = col.temperature + 0.07 * broad;
        double cover = 0.0;
        if (ground == GROUND_SOIL) {
            // Soil biomes take their snow from the biome as well: none in those that never see
            // snow (jungles, savannas) and none in those that vanilla covers with snow layers
            // anyway (groves, snowy taiga and plains). Elsewhere a few patches begin where the
            // warm biomes of the next temperature band end (vanilla's band edge at 0.2), lie
            // mostly in gullies at first and grow with the cold, so the temperate flanks below
            // the snowy peaks stay mostly green; towards the snowy biomes they take over the
            // snowfields of the temperate biomes, at least as many as there were.
            if (biomeTemperature > VANILLA_SNOW && biomeTemperature < NEVER_SNOW) {
                cover = 0.3 * smoothstep(0.2, -0.2, t) + 0.6 * smoothstep(-0.2, -0.47, t);
            }
        } else if (ground != GROUND_SAND) {
            cover = snowCover(t, smoothSlope);
        }
        // grass and moss below the snow, where it is not too dry
        out.green = ground == GROUND_SAND ? 0.0 : smoothstep(-0.2, 0.2, t) * smoothstep(-0.7, -0.2, col.humidity);
        out.hollow = cover > 0.0 || out.green > 0.0 ? hollow(heights, x, z) : 0.0;
        if (cover > 0.0) {
            // near the snow line, hollows only keep the snow there is: no snowy valley floors
            // below bare slopes
            double drift = Math.min(1.0, cover / 0.35);
            cover -= SNOW_SHED * smoothstep(1.3, 3.6, smoothSlope);
            cover += (0.3 * Math.max(-1.0, Math.min(1.0, out.hollow / 2.5)) + 0.15 * broad) * drift;
        }
        // even the snowiest ground keeps a few blotches of rock and earth
        setSnow(out, Math.max(0.0, Math.min(0.9, cover)));

        out.moss = col.humidity - 0.05 + 0.45 * plane(model, 7, x, z, 26.0, 2450.0);
        // two octaves each, so patches come in clusters and in every size; the broad octave is
        // shared with opposite signs, so plants gather where scree is scarce and the other way round
        double clusters = plane(model, 1, x, z, 45.0, 2520.0);
        out.plants = out.green > 0.0 ? 0.75 * plane(model, 0, x, z, 13.0, 2500.0) + 0.45 * clusters : 0.0;
        out.scree = 0.75 * plane(model, 1, x, z, 12.0, 2550.0) - 0.45 * clusters;
        // rubble goes with the scree: some slopes are strewn with large blobs of broken rock,
        // others hardly have any
        out.rubble = 0.62 + 0.16 * clusters;

        // rock outcrops in soil and sand: steep ground, with small blobs of rock poking through
        // near the threshold so the outcrops have ragged, broken edges
        if (ground == GROUND_SOIL || ground == GROUND_SAND) {
            double limit = ground == GROUND_SAND ? 1.1 - 0.35 * out.share : 0.95;
            out.rock = rockiness(model, x, z, smoothSlope, limit) + 0.15 * plane(model, 2, x, z, 8.0, 2600.0);
        } else {
            out.rock = 1.0;
        }
    }

    /**
     * Sets the share of snowy ground of a column and the cuts that follow from it: snow lenses
     * per depth (see {@link #snowAt}) and the thawed earth around them.
     */
    public static void setSnow(Mountain out, double snow) {
        out.snow = snow;
        for (int d = 0; d < out.snowCut.length; d++) {
            // snow lenses thin out at their edges: deeper blocks need a lower field value
            out.snowCut[d] = snowCut(out.snow - 0.09 * d);
        }
        // earth shows in the holes and along the thawed edges of the snow patches
        out.earthCut = out.snowCut[0] + EARTH_MARGIN;
    }

    /**
     * How mountainous a column is for the mosaic (0..1), from its interpolated fields and its
     * relief: foothills fade in from a mountain weight of 0.12 to 0.4, and high ground of other
     * landforms joins them, so that plateau massifs, windswept peaks, tall hills and rock towers
     * lose their contour bands too. That is high plateau and windswept ground as a whole, and
     * any steep ground well above the sea. Canyon walls keep their level beds and volcanoes
     * their own zones and banded rock, and sea cliffs keep their strata; all of them fade out
     * the mosaic smoothly, so it never ends along a seam.
     *
     * @param smoothSlope {@link #smoothSlope} of the column
     * @param aboveSea    height of the ground above the sea
     */
    public static double mountainShare(ColumnSample col, double smoothSlope, double aboveSea) {
        if (col.volcano >= 0.3 || col.canyon >= 0.25) {
            return 0.0;
        }
        double share = smoothstep(0.12, 0.4, Math.max(col.mountain, col.alpine));
        if (share < 1.0 && aboveSea > 30.0) {
            double landform = smoothstep(0.15, 0.45, Math.max(col.plateau, col.windswept));
            double relief = Math.max(landform, smoothstep(0.9, 1.6, smoothSlope)) * smoothstep(30.0, 70.0, aboveSea)
                * (1.0 - smoothstep(0.2, 0.5, col.coastCliff));
            share = Math.max(share, relief);
        }
        return share * (1.0 - smoothstep(0.05, 0.25, col.canyon)) * (1.0 - smoothstep(0.05, 0.3, col.volcano));
    }

    /**
     * Temperature above which no snow lies on rock. The snowy peaks begin at 0.2; below them the
     * snow thins out over a band of altitude up to this temperature.
     */
    public static final double SNOW_EDGE = 0.36;
    /** Base temperature of a biome at or below which vanilla lays snow layers on it everywhere. */
    public static final double VANILLA_SNOW = 0.15;
    /** Base temperature of a biome from which on it never snows (jungles 0.95, savannas and deserts 2). */
    public static final double NEVER_SNOW = 0.9;
    /** Largest loss of snow on the steepest ground. */
    private static final double SNOW_SHED = 0.4;
    /** How far past the snow patches' cut the thawed ground shows earth (in raw field units). */
    private static final double EARTH_MARGIN = 0.1;

    /**
     * Share of snowy ground at temperature {@code t} before steep ground sheds some of it and
     * gullies and ridges move it: none above {@link #SNOW_EDGE}, about half at 0.2 (where the
     * snowy peaks begin) and nine tenths in the coldest air. The stony and the snowy peaks share
     * it, so the snow line runs on across the edge between them instead of stepping. Gentle ground
     * in the cold is almost all white: vanilla lays a snow layer on every top in the snowy
     * biomes, and on gentle ground, where treads dominate, bare rock between white treads drew
     * stripes of snow and stone; steep ground, where risers dominate, shows its rock.
     */
    static double snowCover(double t, double smoothSlope) {
        return 0.62 * smoothstep(SNOW_EDGE, 0.12, t) + 0.3 * smoothstep(0.2, -0.45, t)
            + 0.25 * smoothstep(0.25, -0.05, t) * (1.0 - smoothstep(0.7, 1.5, smoothSlope));
    }

    /**
     * Whether the block belongs to the mountain mosaic or to the level strata of the foothills.
     * Where the share is partial, the boundary follows a smooth 3D field of blobs about 12 blocks
     * across, so the two meet in ragged, interlocking blobs rather than along a vertical seam.
     * (The wobble would change within a block or two on steep steps and fray the boundary.)
     */
    public static boolean inMosaic(TerrainModel model, Mountain m, int x, int y, int z) {
        if (m.share >= 1.0 || m.share <= 0.0) {
            return m.share > 0.0;
        }
        double blobs = model.detail3(2, x / 12.0, y / 12.0 + 3100.0, z / 12.0);
        return m.share > 0.5 + 0.4 * blobs + 0.25 * m.edge;
    }

    /**
     * Material of a mountain block. {@code d} is the depth below the nearest exposed surface,
     * {@code top} marks the topmost block of the column under open sky and {@code face} an
     * exposed face (see the surface rule).
     * <ol>
     *   <li>The soil of the mountain forests ({@link Mountain#flora}, see {@link MountainFlora})
     *       comes first: its top block, then dirt down to its depth, in place of rock and snow
     *       patches; faces keep their rock.</li>
     *   <li>Snow lies in patches of the snow field, as lenses up to 3 blocks thick (thinner at
     *       their edges), on gentle ground and faces alike. The soil of the krummholz and alpine
     *       meadows fills the ground between the patches.</li>
     *   <li>Gentle soil and sand ground that is not an outcrop returns {@link #SOIL}: the
     *       ordinary soil profile applies.</li>
     *   <li>On rock, ledges carry patches of grass or moss below the snow, gentle rock patches of
     *       coarse dirt, dirt and gravel; desert ledges collect sand.</li>
     *   <li>Everything else is the rock mosaic ({@link #rock}).</li>
     * </ol>
     */
    public static int mountainBlock(TerrainModel model, Mountain m, int x, int y, int z, int d, boolean top, boolean face) {
        boolean flora = m.flora > 0.0 && !face;
        if (flora && m.floraOverSnow) {
            // forests: soil where the mosaic would lay rock and snow patches, and
            // the rock mosaic under it
            return d < m.floraDepth && (top || !m.floraTopOnly) ? floraBlock(m, d, top) : rock(model, m, x, y, z, d);
        }
        // the snow field is read once and serves the earth at the snow's edges too
        double snowField = Double.NaN;
        if (d <= 3 && m.snowCut[0] != Double.NEGATIVE_INFINITY) {
            snowField = snowField(model, x, y, z);
            if (snowAt(model, m, x, y, z, d, snowField)) {
                return SNOW;
            }
        }
        if (flora) {
            // krummholz and alpine meadows: soil between the snow patches
            return d < m.floraDepth && (top || !m.floraTopOnly) ? floraBlock(m, d, top) : rock(model, m, x, y, z, d, snowField);
        }
        if ((m.ground == GROUND_SOIL || m.ground == GROUND_SAND) && !face && m.rock <= 0.0) {
            return SOIL;
        }
        if (top && m.steep < LEDGE) {
            // ledges and ridge tops: plants where the climate allows, ridges a little more
            double ridge = Math.max(0.0, -m.hollow / 3.0);
            if (m.green > 0.0 && m.plants + 0.25 * ridge > 1.2 - 0.6 * m.green) {
                return m.moss > 0.0 ? MOSS : GRASS;
            }
            if (m.ground == GROUND_SAND && m.scree > 0.1) {
                return SAND;
            }
        }
        if (d <= 1 && !face && m.steep < 1.1 && m.scree > 0.55 && m.snow < 0.15) {
            // gentle rock: patches of soil and scree, one material per patch
            return m.plants > 0.25 && m.green > 0.3 ? DIRT : m.moss > 0.2 ? COARSE_DIRT : GRAVEL;
        }
        return rock(model, m, x, y, z, d, snowField);
    }

    /**
     * A block of the flora soil ({@link Mountain#flora}): its top material on the topmost block,
     * dirt below and on the risers of small steps; under a coarse top the risers are coarse dirt,
     * and moss drapes over the edges of the steps it covers.
     */
    private static int floraBlock(Mountain m, int d, boolean top) {
        if (top) {
            return m.floraTop;
        }
        if (d == 0 && (m.floraTop == COARSE_DIRT || m.floraTop == MOSS)) {
            return m.floraTop;
        }
        return DIRT;
    }

    /**
     * Smoothed slope below which a column's top can hold plants or drifted sand: about 50
     * degrees. (The block slope would decide column by column and fray the patches.)
     */
    public static final double LEDGE = 1.25;

    /** {@link #rock} away from the snow (no earth from thawed ground). */
    public static int rock(TerrainModel model, Mountain m, int x, int y, int z, int d) {
        return rock(model, m, x, y, z, d, Double.NaN);
    }

    /**
     * The rock mosaic at a block. Two fields of tilted lenses ({@code big}, about 40 blocks wide
     * and 30 tall, and {@code mid}, about 16 blocks) pick between the stops of the province's
     * family and its second rock: the family's own majority stop and one other share the ground
     * in broad lenses; the weathered stop forms blobs where {@code mid} is high and the second
     * rock lenses where {@code big} is high and {@code mid} low, so both come in every size, from
     * small spots at the edges to lenses tens of blocks across, lie inside the majority stop
     * instead of nesting in rings, and are common on some slopes and rare on others. Near the
     * surface a third field adds blobs of rubble (broken rock, gravel and mossy rubble, about
     * 5-12 blocks), many on some slopes and few on others; on gentle ground earth shows in the
     * holes and along the edges of the snow patches ({@code snowField}: the snow field's coarse
     * value, NaN where unknown); desert mountains are half sandstone. The lenses lie along
     * {@link Mountain#warp}, so what remains of bedding is tilted, bent and interrupted.
     * <p>
     * The fields are read at the block itself, without the wobble: on a staircase of steep
     * steps the wobble changes within a block or two, and any part of it in the material
     * choice frays the outlines into single blocks.
     */
    public static int rock(TerrainModel model, Mountain m, int x, int y, int z, int d, double snowField) {
        double yy = y + m.warp;
        double big = model.detail3(0, x / 36.0, yy / 26.0 + 1500.0, z / 36.0);
        double mid = model.detail3(1, x / 15.0, yy / 14.0 + 1700.0, z / 15.0);
        if (d <= 1 && m.steep < 1.5 && snowField < m.earthCut && mid < 0.3) {
            // earth showing through the snow: terracotta-coloured soil and coarse dirt
            return big > 0.0 ? EARTH : COARSE_DIRT;
        }
        if (d <= 3) {
            double rubble = model.detail3(4, x / 16.0, yy / 14.0 + 1900.0, z / 16.0);
            if (rubble > m.rubble) {
                // the material of a blob depends only on the broad field, so each blob is one material
                if (m.green > 0.3 && m.moss > 0.0 && big < 0.1) {
                    return MOSSY_COBBLE;
                }
                return big > 0.25 && m.steep < 1.4 ? GRAVEL : COBBLE;
            }
        }
        if (m.ground == GROUND_SAND && big > -0.05) {
            return SANDSTONE;
        }
        if (mid + 0.3 * big > m.paleCut) {
            return ROCK_PALE;
        }
        if (0.6 * mid - big < m.provinceCut) {
            return ROCK_PROVINCE;
        }
        return big > -0.25 ? m.majority : m.minority;
    }

    /**
     * Whether snow lies at a block {@code d} blocks (at most 3) below the surface. The snow field
     * is about uniform in [0, 1] after its {@code tanh} (see {@link #snowCut}), so a share
     * {@code s} of the ground lies below {@code s}; deeper blocks need 0.09 more per block, so
     * snow forms lenses up to 3 blocks thick that thin out at their edges. Two octaves (blobs of
     * about 24 and 11 blocks) give snowfields and rock islands of every size; the blobs are
     * taller than wide, so on faces they hang as streaks. The finer octave can move the field by
     * at most {@link #SNOW_FINE} (simplex noise stays within about [-1, 1]), so it is only read
     * where it can change the answer. {@code v} is the coarse octave there ({@link #snowField}).
     */
    public static boolean snowAt(TerrainModel model, Mountain m, int x, int y, int z, int d, double v) {
        double cut = m.snowCut[d];
        if (cut == Double.NEGATIVE_INFINITY) {
            return false;
        }
        if (v + SNOW_FINE < cut) {
            return true;
        }
        if (v - SNOW_FINE >= cut) {
            return false;
        }
        return v + 0.3 * model.detail3(5, x / 11.0, y / 15.0 + 2900.0, z / 11.0) < cut;
    }

    /** Coarse octave of the snow field ({@code v} of {@link #snowAt}). */
    public static double snowField(TerrainModel model, int x, int y, int z) {
        return 0.5 * model.detail3(5, x / 24.0, y / 34.0 + 2100.0, z / 24.0);
    }

    /** Largest change of the snow field by its finer octave (0.3 times a little over 1). */
    private static final double SNOW_FINE = 0.33;

    /** Gain that spreads the snow field's raw value about evenly over [0, 1] after {@code tanh}. */
    private static final double SNOW_GAIN = 3.3;

    /**
     * The snow field's raw value below which a share {@code s} of the ground is snow: the
     * inverse of {@code 0.5 + 0.5 tanh(SNOW_GAIN v)}.
     */
    public static double snowCut(double s) {
        return s <= 0.0 ? Double.NEGATIVE_INFINITY : atanh(2.0 * Math.min(s, 0.999) - 1.0) / SNOW_GAIN;
    }

    private static double atanh(double v) {
        return 0.5 * Math.log((1.0 + v) / (1.0 - v));
    }

    /**
     * Height of the surroundings above a column: positive in gullies and hollows, negative on
     * ridges, spurs and summits. It averages the points 6 and 14 blocks away in the four axis
     * directions, so both narrow clefts and broad couloirs count.
     */
    public static double hollow(Heights h, int x, int z) {
        double near = h.at(x + 6, z) + h.at(x - 6, z) + h.at(x, z + 6) + h.at(x, z - 6);
        double far = h.at(x + 14, z) + h.at(x - 14, z) + h.at(x, z + 14) + h.at(x, z - 14);
        return near * 0.125 + far * 0.0625 - 0.75 * h.at(x, z);
    }

    /** Smooth 2D noise in about [-1, 1] at the given feature size, on its own slice of a channel. */
    private static double plane(TerrainModel model, int channel, int x, int z, double scale, double slice) {
        return model.detail3(channel, x / scale, slice, z / scale);
    }

    // =========================================================================================
    // slopes and the deepslate transition
    // =========================================================================================

    /** Gradient of the smoothed terrain at block resolution: rise per block, measured over 8 blocks. */
    public static double smoothSlope(TerrainModel model, int x, int z) {
        double gx = model.interpolatedHeight(x + 4, z) - model.interpolatedHeight(x - 4, z);
        double gz = model.interpolatedHeight(x, z + 4) - model.interpolatedHeight(x, z - 4);
        return Math.sqrt(gx * gx + gz * gz) / 8.0;
    }

    /**
     * How far past the slope at which ground turns to bare rock (or snow slides off) a column is;
     * negative below it. The limit wanders by +-0.3 with smooth noise, so outcrops and snowfields
     * form coherent areas with natural outlines.
     */
    public static double rockiness(TerrainModel model, int x, int z, double smoothSlope, double limit) {
        return smoothSlope - limit - 0.3 * patch(model, x, z, 12.0, 4);
    }

    /**
     * Whether rock at {@code (x, y, z)} lies in the deepslate zone. Like vanilla, the zone begins
     * between Y 0 and 8, but its upper boundary is a smooth, wavy surface instead of a per-block
     * random gradient.
     */
    public static boolean deep(TerrainModel model, int x, int y, int z) {
        if (y <= 0) {
            return true;
        }
        if (y >= 8) {
            return false;
        }
        return y < 4.0 + 4.6 * model.detail3(5, x / 11.0, y / 5.0 + 211.0, z / 11.0);
    }

    // =========================================================================================
    // column fields at block resolution
    // =========================================================================================

    /** Number of fields {@link #blend} interpolates; size of its scratch array. */
    public static final int BLEND_FIELDS = 24;

    /**
     * Replaces the continuous landform and climate fields of {@code out} with values interpolated
     * between the four model grid corners around {@code (x, z)}, like the ground height itself.
     * Thresholds on these fields (river beds, canyon floors, wetland mud, mountain soil) then
     * follow smooth curves instead of 4-block staircases. Categorical fields (geology, lake type,
     * water levels, floor heights) are left as they are.
     *
     * @param scratch an array of at least {@link #BLEND_FIELDS} values
     */
    public static void blend(TerrainModel model, int x, int z, ColumnSample out, double[] scratch) {
        int x0 = x & ~3;
        int z0 = z & ~3;
        double fx = (x - x0) * 0.25;
        double fz = (z - z0) * 0.25;
        java.util.Arrays.fill(scratch, 0, BLEND_FIELDS, 0.0);
        // each corner is read immediately: cache-owned samples are only valid until the next call
        accumulate(model.sample(x0, z0), (1.0 - fx) * (1.0 - fz), scratch);
        accumulate(model.sample(x0 + 4, z0), fx * (1.0 - fz), scratch);
        accumulate(model.sample(x0, z0 + 4), (1.0 - fx) * fz, scratch);
        accumulate(model.sample(x0 + 4, z0 + 4), fx * fz, scratch);
        int i = 0;
        out.cliff = scratch[i++];
        out.steepness = scratch[i++];
        out.mountain = scratch[i++];
        out.peakness = scratch[i++];
        out.alpine = scratch[i++];
        out.ancient = scratch[i++];
        out.wall = scratch[i++];
        out.plateau = scratch[i++];
        out.meadow = scratch[i++];
        out.windswept = scratch[i++];
        out.swamp = scratch[i++];
        out.volcano = scratch[i++];
        out.crater = scratch[i++];
        out.canyon = scratch[i++];
        out.river = scratch[i++];
        out.riverValley = scratch[i++];
        out.lake = scratch[i++];
        out.coastCliff = scratch[i++];
        out.beach = scratch[i++];
        out.trench = scratch[i++];
        out.oceanRidge = scratch[i++];
        out.temperature = scratch[i++];
        out.humidity = scratch[i++];
        out.baseTemperature = scratch[i];
    }

    private static void accumulate(ColumnSample s, double w, double[] a) {
        int i = 0;
        a[i++] += w * s.cliff;
        a[i++] += w * s.steepness;
        a[i++] += w * s.mountain;
        a[i++] += w * s.peakness;
        a[i++] += w * s.alpine;
        a[i++] += w * s.ancient;
        a[i++] += w * s.wall;
        a[i++] += w * s.plateau;
        a[i++] += w * s.meadow;
        a[i++] += w * s.windswept;
        a[i++] += w * s.swamp;
        a[i++] += w * s.volcano;
        a[i++] += w * s.crater;
        a[i++] += w * s.canyon;
        a[i++] += w * s.river;
        a[i++] += w * s.riverValley;
        a[i++] += w * s.lake;
        a[i++] += w * s.coastCliff;
        a[i++] += w * s.beach;
        a[i++] += w * s.trench;
        a[i++] += w * s.oceanRidge;
        a[i++] += w * s.temperature;
        a[i++] += w * s.humidity;
        a[i] += w * s.baseTemperature;
    }

    /** Smooth step from 0 at {@code e0} to 1 at {@code e1}. */
    public static double smoothstep(double e0, double e1, double v) {
        double t = Math.max(0.0, Math.min(1.0, (v - e0) / (e1 - e0)));
        return t * t * (3.0 - 2.0 * t);
    }
}
