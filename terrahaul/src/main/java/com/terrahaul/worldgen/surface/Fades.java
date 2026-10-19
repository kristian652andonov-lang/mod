package com.terrahaul.worldgen.surface;

import com.terrahaul.worldgen.terrain.ColumnSample;
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
