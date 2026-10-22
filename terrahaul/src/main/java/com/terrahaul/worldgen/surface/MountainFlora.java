package com.terrahaul.worldgen.surface;

import static com.terrahaul.worldgen.surface.Fades.smoothstep;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Vegetation zones of mountain flanks: where forest, the tree line, krummholz and alpine meadows
 * lie, and where their soil covers the rock. Like {@code VolcanoZones} it is a deterministic
 * function of the block column (the terrain model's fields, its smoothed height field and cheap
 * seeded noise, never block reads), so the surface rule lays soil exactly where the
 * {@code terrahaul:mountain_flora} placement filter plants trees, shrubs and flowers.
 * <p>
 * <b>The tree line</b> is measured from the valley floors around the mountain ({@link #valleyLevel})
 * rather than from sea level: a range standing on high ground carries its forests higher (the
 * mass elevation effect), so every mountain has wooded lower slopes unless its climate is too
 * cold or too dry for trees. How far above the valleys the forest reaches depends on the climate,
 * higher in warm and wet air, lower in cold and dry air (see {@link #band}), and on the ground:
 * <ul>
 *   <li>slopes facing south (towards +z) get more sun and carry trees higher than shaded ones;</li>
 *   <li>gullies and hollows are sheltered and send tongues of trees up the slope, exposed ridges
 *       and spurs push the line down;</li>
 *   <li>noise at three scales (about 90, 28 and 9 blocks) makes it ragged.</li>
 * </ul>
 * <p>
 * <b>Zones</b>, by the margin below the line ({@link Zone#margin}, in blocks; negative above it):
 * <ul>
 *   <li><b>montane forest</b> on the lower flanks, where the local biome's broadleaf trees mix
 *       with the conifers;</li>
 *   <li><b>forest</b>: closed conifer forest up to about 70 blocks below the line, thinning out
 *       towards it;</li>
 *   <li><b>tree line</b>: scattered groups of small trees from about 70 blocks below the line to
 *       a little above it;</li>
 *   <li><b>krummholz</b>: stunted, wind-bent dwarf conifers and low needle bushes from just below
 *       the line to about 50 blocks above it;</li>
 *   <li><b>alpine meadow</b>: grass, moss, flowers and low shrubs above that, where the snow
 *       leaves room for them, up to about 160 blocks above the line.</li>
 * </ul>
 * Trees thin out from about 53 degrees and keep off ground of 60 degrees and more (the smoothed
 * slope, {@link Fades#smoothSlope}; the placement filter also checks the slope at the tree itself);
 * avalanche tracks (gullies below steep, high ground) and exposed ridges stay open, with grass
 * and shrubs instead of trees. The zones fade out with the mountain weight towards the foothills,
 * where the biomes' own vegetation takes over, and in hot, dry climates (desert mountains stay
 * bare).
 * <p>
 * <b>Soil</b> ({@link Zone#soil}): within the forest and krummholz bands, and in patches of the
 * alpine meadows, the ground of slopes up to about 58 degrees (meadows: 45 degrees) is soil
 * instead of the bare rock and snow of the mountain mosaic, so trees and plants have something to
 * grow on. On steeper faces in the same bands, up to about 69 degrees, patches of grass and moss
 * cover the treads of the steps over rock risers ({@link Zone#faceCover}), with shrubs and grass
 * on them but no trees. Sheer faces, crags and everything above the meadows keep the mosaic.
 * Below the tree line the soil takes the place of the mosaic's snow patches; in the krummholz and
 * the meadows above it only fills the ground between them. On steep ground the soil follows the
 * trees: where they thin out it breaks up into patches between crags.
 * <p>
 * Cost: about fifteen reads of the cached height field and eight noise samples per column, and
 * seventeen probes of the model per 64-block grid node of the valley level, which are cached.
 */
public final class MountainFlora {
    /** Noise slices of the flora fields (the mosaic uses slices up to 3100). */
    private static final double SLICE = 3300.0;
    /** Horizontal distance up the slope at which avalanche tracks look for steep ground above. */
    private static final double RELEASE_REACH = 26.0;
    /** Spacing of the grid on which the valley level is measured, in blocks. */
    private static final int LEVEL_GRID = 64;
    /** Radii of the two rings of points the valley level is the lowest ground of. */
    private static final double[] LEVEL_RINGS = {160.0, 320.0};
    /** Slots of the cache of valley levels at grid nodes (a power of two). */
    private static final int LEVEL_CACHE = 4096;

    private static final ThreadLocal<Zone> ZONE = ThreadLocal.withInitial(Zone::new);
    /**
     * Valley levels at grid nodes, one cache per terrain model shared by all worker threads (a
     * node is computed from the model alone, so a race only computes it twice). Each thread keeps
     * the last model's cache at hand.
     */
    private static final Map<TerrainModel, AtomicReferenceArray<Node>> LEVELS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Levels> LAST_LEVELS = ThreadLocal.withInitial(Levels::new);

    /** A grid node and its valley level. */
    private record Node(int gx, int gz, double level) {
    }

    /** A thread's shortcut to the cache of the model it last asked for (held weakly, so a closed world's model can go). */
    private static final class Levels {
        WeakReference<TerrainModel> model = new WeakReference<>(null);
        AtomicReferenceArray<Node> nodes;
    }

    private MountainFlora() {
    }

    /** The zones a placement filter can select (see {@link Zone#weight}). */
    public enum Kind {
        /** Any mountain column where something grows: a cheap per-chunk test that skips the rest. */
        ANY("any"),
        /** Closed forest below the tree line. */
        FOREST("forest"),
        /** The lowest flanks, where broadleaf trees of the biome mix with the conifers. */
        MONTANE("montane"),
        /** Scattered groups of small trees along the tree line. */
        EDGE("edge"),
        /** Dwarf, wind-bent conifers and needle bushes just above the tree line. */
        KRUMMHOLZ("krummholz"),
        /** Alpine meadows, avalanche tracks and clearings: grass, flowers and shrubs. */
        ALPINE("alpine"),
        /** The ground under the trees and in the krummholz: ferns, moss, berries, shrubs, logs. */
        UNDERSTORY("understory"),
        /** The grassy treads of faces too steep for trees: shrubs, grass and ferns. */
        LEDGE("ledge"),
        /** Anywhere on the vegetated flanks, rocky or not: boulders and loose rocks. */
        FLANK("flank");

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

    /** The zones of one column. Instances are reused per thread; copy what must be kept. */
    public static final class Zone {
        /** How much the column belongs to vegetated mountain flanks (0..1; 0 elsewhere). */
        public double share;
        /** Y of the tree line here. */
        public double line;
        /** Blocks below the tree line (negative above it). */
        public double margin;
        /** Smoothed slope (rise per block over 8 blocks). */
        public double steep;
        /** Height of the surroundings above the column: positive in gullies, negative on ridges. */
        public double hollow;
        /** Humidity of the climate (-1..1). */
        public double humidity;
        /** Base temperature of the climate (-1..1), before altitude cooling. */
        public double warmth;
        public double forest;
        public double montane;
        public double edge;
        public double krummholz;
        public double alpine;
        /** Green treads of faces too steep for soil and trees (0..1), for shrubs and grass. */
        public double ledge;
        /** How much of an avalanche track the column lies in (0..1). */
        public double avalanche;
        /**
         * Positive where the ground is vegetated soil rather than rock or snow; the larger, the
         * further inside a soil patch. It is the smaller of {@link #soilCover} and
         * {@link #soilSlope}.
         */
        public double soil;
        /** Positive inside the soil patches of the vegetation, whatever the slope. */
        public double soilCover;
        /** Positive where the slope is gentle enough for the soil to hold. */
        public double soilSlope;
        /**
         * Positive inside the patches of grass and moss on the treads of steep faces, whatever
         * their slope: the surface rule greens the topmost block of such columns (up to about 69
         * degrees, where the steps are still treads rather than a sheer face).
         */
        public double faceCover;
        /**
         * Whether the soil takes the place of the mosaic's snow (below the tree line) or only fills
         * the gaps between its patches (krummholz and meadows).
         */
        public boolean overSnow;
        /**
         * How far the column lies inside the forest (0..1): below the tree line the snow patches
         * of the rock between the trees thin out to a quarter, so the forest stands below the
         * snow line and not in snowfields; the krummholz above keeps them.
         */
        public double thaw;
        /** Top block of the soil, a {@link Fades} material code (grass, podzol, coarse dirt or moss). */
        public int top;
        /** Depth of the soil (dirt under the top block) in blocks, about 2 to 4. */
        public double depth;

        void clear() {
            share = 0.0;
            line = 0.0;
            margin = 0.0;
            forest = 0.0;
            montane = 0.0;
            edge = 0.0;
            krummholz = 0.0;
            alpine = 0.0;
            ledge = 0.0;
            avalanche = 0.0;
            soil = -1.0;
            soilCover = -1.0;
            soilSlope = -1.0;
            faceCover = -1.0;
            overSnow = false;
            thaw = 0.0;
            top = Fades.GRASS;
            depth = 0.0;
        }

        /** The weight of a zone kind (0..1). */
        public double weight(Kind kind) {
            return switch (kind) {
                case ANY, FLANK -> share;
                case FOREST -> forest;
                case MONTANE -> montane;
                case EDGE -> edge;
                case KRUMMHOLZ -> krummholz;
                case ALPINE -> alpine;
                case UNDERSTORY -> Math.max(Math.max(forest, edge), krummholz);
                case LEDGE -> ledge;
            };
        }
    }

    /**
     * Whether any mountain flora can grow near {@code (x, z)}: a cheap test of one cached model
     * column (use the middle of a chunk) that placement filters run first.
     */
    public static boolean near(TerrainModel model, int x, int z) {
        ColumnSample c = model.sample(x & ~3, z & ~3);
        return Math.max(c.mountain, c.alpine) > 0.08 && aridity(c) < 0.95 && c.volcano < 0.3;
    }

    /** How mountainous the column is for the flora: the mountain part of {@link Fades#mountainShare}. */
    private static double share(ColumnSample c) {
        return smoothstep(0.12, 0.4, Math.max(c.mountain, c.alpine))
            * (1.0 - smoothstep(0.05, 0.25, c.canyon)) * (1.0 - smoothstep(0.05, 0.3, c.volcano));
    }

    /** Hot, dry climates (0..1), as the terrain model judges them: deserts and badlands. */
    private static double aridity(ColumnSample c) {
        return smoothstep(0.1, 0.6, c.baseTemperature) * smoothstep(0.1, -0.5, c.humidity);
    }

    /**
     * Height of the tree line above the valley floors in a climate, before the ground and the
     * noise move it: about 175 blocks in a temperate climate, up to about 290 in warm, wet air and
     * down to a few dozen in polar, dry air.
     */
    static double band(double warmth, double humidity) {
        double b = 175.0 + 120.0 * warmth + 50.0 * Math.max(-1.0, Math.min(0.8, humidity));
        return Math.max(30.0, Math.min(320.0, b));
    }

    /** The zones of column {@code (x, z)} whose ground is at {@code y}; the result is reused per thread. */
    public static Zone at(TerrainModel model, int x, int z, int y) {
        return at(model, x, z, y, Double.NaN);
    }

    /**
     * The zones of column {@code (x, z)} whose ground is at {@code y}, with its smoothed slope
     * when the caller has it ({@code NaN} otherwise); the result is reused per thread.
     */
    public static Zone at(TerrainModel model, int x, int z, int y, double smoothSlope) {
        Zone out = ZONE.get();
        out.clear();
        ColumnSample c = model.sample((x + 2) & ~3, (z + 2) & ~3);
        double share = share(c) * (1.0 - aridity(c));
        if (share <= 0.0) {
            return out;
        }
        double warmth = c.baseTemperature;
        double humidity = c.humidity;
        double floor = Math.max(c.mountainFloor, valleyLevel(model, x, z));
        out.share = share;
        out.warmth = warmth;
        out.humidity = humidity;

        // slope and aspect from the smoothed terrain, as the surface rule measures them
        double h = model.interpolatedHeight(x, z);
        double gx = model.interpolatedHeight(x + 4, z) - model.interpolatedHeight(x - 4, z);
        double gz = model.interpolatedHeight(x, z + 4) - model.interpolatedHeight(x, z - 4);
        double g = Math.sqrt(gx * gx + gz * gz);
        double steep = Double.isNaN(smoothSlope) ? g / 8.0 : smoothSlope;
        out.steep = steep;
        // facing south (downhill towards +z): sunnier, the trees climb higher
        double sunny = g > 1.0E-6 ? -gz / g * smoothstep(0.15, 0.6, steep) : 0.0;
        double hollow = Fades.hollow(model::interpolatedHeight, x, z);
        out.hollow = hollow;

        double n1 = noise(model, 0, x, z, 90.0, 0.0);
        double n2 = noise(model, 1, x, z, 28.0, 40.0);
        double n3 = noise(model, 2, x, z, 9.0, 80.0);
        double line = floor + band(warmth, humidity)
            + 16.0 * sunny
            + 1.8 * Math.max(-15.0, Math.min(15.0, hollow))
            + 34.0 * n1 + 14.0 * n2 + 5.0 * n3;
        out.line = line;
        double m = line - y;
        out.margin = m;

        // ---- avalanche tracks: gullies below steep, high ground, swept clear of trees -----------
        double avalanche = 0.0;
        if (m > -30.0 && hollow > 1.5 && g > 1.0E-6) {
            double rise = (model.interpolatedHeight((int) Math.round(x + gx / g * RELEASE_REACH), (int) Math.round(z + gz / g * RELEASE_REACH)) - h)
                / RELEASE_REACH;
            // not every couloir runs: some keep their trees (a track is tens of blocks wide and
            // runs a long way down, so its noise is coarse)
            double runs = smoothstep(-0.25, 0.15, noise(model, 3, x, z, 60.0, 120.0));
            avalanche = smoothstep(1.5, 6.0, hollow) * smoothstep(1.05, 1.6, rise) * runs;
        }
        out.avalanche = avalanche;

        // ---- trees -----------------------------------------------------------------------------
        // dry climates carry open woodland rather than closed forest
        double moist = 0.45 + 0.55 * smoothstep(-0.75, 0.1, humidity);
        double treeSlope = 1.0 - smoothstep(1.35, 1.75, steep);
        // exposed ridges and spurs: thin soil and wind
        double ridge = 1.0 - 0.6 * smoothstep(3.0, 11.0, -hollow);
        double open = (1.0 - avalanche) * ridge * treeSlope * share;
        out.forest = smoothstep(0.0, 70.0, m) * moist * open;
        out.montane = smoothstep(110.0, 190.0, m) * moist * open;
        out.edge = smoothstep(-12.0, 8.0, m) * (1.0 - smoothstep(25.0, 70.0, m)) * open;
        out.krummholz = smoothstep(-52.0, -22.0, m) * (1.0 - smoothstep(0.0, 25.0, m))
            * (1.0 - smoothstep(1.35, 1.8, steep)) * (1.0 - 0.7 * avalanche) * share;
        // meadows above the krummholz, and the grass of avalanche tracks and clearings below it
        double meadow = Math.max(smoothstep(-165.0, -105.0, m) * (1.0 - smoothstep(-30.0, 0.0, m)), 0.8 * avalanche * smoothstep(-30.0, 0.0, m));
        out.alpine = meadow * (1.0 - smoothstep(1.0, 1.45, steep)) * share;

        // ---- soil -------------------------------------------------------------------------------
        // Forest and krummholz soil holds on steeper ground (up to about 58 degrees, with a
        // wandering limit so the crags between have ragged edges); meadow turf only on gentler
        // slopes, in patches. Two scales of noise shape the edges of the patches; gullies hold
        // soil and water, ridges shed them.
        double ns = noise(model, 4, x, z, 14.0, 160.0) * 0.7 + noise(model, 5, x, z, 5.0, 200.0) * 0.3;
        double gully = 0.12 * Math.max(-1.0, Math.min(1.0, hollow / 6.0));
        double wooded = smoothstep(-55.0, -25.0, m) * share * (0.3 + 0.7 * moist);
        double grass = out.alpine;
        double limit = wooded > grass ? 1.62 + 0.15 * ns : 1.05 + 0.2 * ns;
        // where the trees thin out on steep ground the soil breaks up into patches between crags
        double cover = Math.max(wooded * (0.25 + 0.75 * treeSlope), grass);
        out.soilCover = cover - 0.3 + 0.35 * ns + gully;
        out.soilSlope = limit - steep;
        out.soil = Math.min(out.soilCover, out.soilSlope);
        // steeper faces in the same bands: grass and moss on the treads of their steps, shrubs
        // and grass on them, in patches; risers and sheer faces stay rock
        out.faceCover = Math.max(wooded, meadow * share) - 0.35 + 0.35 * ns + gully;
        if (out.soil <= 0.0 && out.faceCover > 0.0) {
            out.ledge = smoothstep(0.0, 0.25, out.faceCover) * smoothstep(1.3, 1.7, steep) * (1.0 - smoothstep(2.2, 2.7, steep));
        }
        // below the tree line the soil takes the place of the snow patches; above it, in the
        // krummholz and the meadows, the snow patches stay and the soil fills the gaps
        out.overSnow = wooded > 0.0 && wooded >= grass && m > -5.0;
        out.thaw = smoothstep(-15.0, 45.0, m) * share;
        if (Math.max(out.soilCover, out.faceCover) > 0.0) {
            ground(model, x, z, out, wooded, grass);
        }
        return out;
    }

    /**
     * The top block and depth of the soil, in patches of one material each (two smooth noise
     * fields, no per-block randomness): under the conifers needle litter (podzol) and grass, moss
     * where it is humid, coarse dirt where it is dry, steep or exposed; in the krummholz more bare,
     * stony soil; meadows mostly turf with mossy hollows. The soil is thinner on steep ground.
     */
    private static void ground(TerrainModel model, int x, int z, Zone out, double wooded, double grass) {
        double p = noise(model, 6, x, z, 12.0, 240.0);
        double q = noise(model, 7, x, z, 7.0, 280.0);
        double dry = 0.25 * smoothstep(0.9, 1.5, out.steep) + 0.2 * smoothstep(3.0, 10.0, -out.hollow) - 0.2 * out.humidity;
        double wet = out.humidity + 0.04 * Math.max(-10.0, Math.min(10.0, out.hollow));
        int top;
        if (wooded >= grass) {
            boolean krumm = out.margin < 0.0;
            if (p - dry < -0.55) {
                top = Fades.COARSE_DIRT;
            } else if (p > (krumm ? 0.5 : 0.3)) {
                top = Fades.PODZOL;
            } else if (q > 0.5 && wet > -0.25) {
                top = Fades.MOSS;
            } else {
                top = Fades.GRASS;
            }
        } else if (q > 0.45 && wet > 0.0) {
            top = Fades.MOSS;
        } else if (p - dry < -0.6) {
            top = Fades.COARSE_DIRT;
        } else {
            top = Fades.GRASS;
        }
        out.top = top;
        out.depth = 2.0 + 1.5 * (0.5 + 0.5 * p) - 0.8 * smoothstep(1.0, 1.6, out.steep);
    }

    /**
     * Level of the valley floors around {@code (x, z)}: the lowest model ground on two rings of
     * eight points, 160 and 320 blocks away, but not below the sea or the range's own valley
     * floors ({@link ColumnSample#mountainFloor}). On a lone mountain it is the plain at its foot,
     * in a high massif the floors of the valleys between its peaks. It is measured on a grid of
     * {@value #LEVEL_GRID} blocks and interpolated between the nodes, so it changes smoothly.
     */
    public static double valleyLevel(TerrainModel model, int x, int z) {
        int gx = Math.floorDiv(x, LEVEL_GRID);
        int gz = Math.floorDiv(z, LEVEL_GRID);
        double fx = (x - gx * LEVEL_GRID) / (double) LEVEL_GRID;
        double fz = (z - gz * LEVEL_GRID) / (double) LEVEL_GRID;
        double l00 = nodeLevel(model, gx, gz);
        double l10 = nodeLevel(model, gx + 1, gz);
        double l01 = nodeLevel(model, gx, gz + 1);
        double l11 = nodeLevel(model, gx + 1, gz + 1);
        return (l00 * (1.0 - fx) + l10 * fx) * (1.0 - fz) + (l01 * (1.0 - fx) + l11 * fx) * fz;
    }

    private static double nodeLevel(TerrainModel model, int gx, int gz) {
        Levels last = LAST_LEVELS.get();
        if (last.model.get() != model) {
            last.nodes = LEVELS.computeIfAbsent(model, m -> new AtomicReferenceArray<>(LEVEL_CACHE));
            last.model = new WeakReference<>(model);
        }
        int slot = (int) ((gx * 0x9E3779B1L + gz * 0x85EBCA77L) >>> 11) & (LEVEL_CACHE - 1);
        Node node = last.nodes.get(slot);
        if (node != null && node.gx == gx && node.gz == gz) {
            return node.level;
        }
        // the far points are probed without lakes and bypass the model's column cache, which
        // holds the chunk being worked on; the node's own floor comes from the cached column
        int x = gx * LEVEL_GRID;
        int z = gz * LEVEL_GRID;
        double floor = model.sample(x, z).mountainFloor;
        double low = model.probeHeight(x, z);
        for (int ring = 0; ring < LEVEL_RINGS.length; ring++) {
            for (int k = 0; k < 8; k++) {
                double a = (k + 0.5 * ring) * Math.PI / 4.0;
                low = Math.min(low, model.probeHeight(x + (int) Math.round(LEVEL_RINGS[ring] * Math.cos(a)),
                    z + (int) Math.round(LEVEL_RINGS[ring] * Math.sin(a))));
            }
        }
        double level = Math.max(low, Math.max(floor, model.seaLevel()));
        last.nodes.set(slot, new Node(gx, gz, level));
        return level;
    }

    /** Smooth 2D noise in about [-1, 1] on the flora's own noise slices. */
    private static double noise(TerrainModel model, int channel, int x, int z, double scale, double slice) {
        return model.detail3(channel, x / scale, SLICE + slice, z / scale);
    }
}
