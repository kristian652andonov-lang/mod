package com.terrahaul.worldgen.terrain;

import static com.terrahaul.worldgen.noise.TMath.clamp;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;
import java.util.Locale;

/**
 * The magma chamber under one of {@link TerrainModel}'s volcanoes and the conduit that feeds its
 * crater: a cavern deep inside the cone, half full of lava, joined to the crater by a pipe of lava.
 * <p>
 * The chamber lies under the crater, about halfway between the volcano's foot and the crater
 * floor, so the outside of the volcano stays exactly as the terrain shaped it. It is a lens: a
 * bowl of a floor and a dome of a ceiling meeting along a lobed outline, described per column by
 * the first cell of the cavern ({@link #FLOOR}) and the first cell above it ({@link #CEILING}). The
 * lower part of the bowl holds a lake of lava with islands of rock; around it a shore of rock
 * rises to where the dome comes down to meet the floor, with pillars of rock standing between
 * floor and ceiling and ribs hanging from the dome. A giant chamber: some hundred to two hundred
 * and fifty blocks across and forty to sixty high, after the crater's size.
 * <p>
 * The <b>conduit</b>, a pipe of lava a few blocks wide, rises from the top of the dome to the
 * crater. Under a crater with lava ({@link #open}) it narrows to a throat and opens into the
 * crater's lake where its lava is deepest, so the crater's lava goes on down into the chamber; it
 * pours out of the dome as a fall of lava onto the chamber's lake. Under a dry crater or a crater
 * lake it ends, plugged, a few dozen blocks below the crater floor. The pipe wanders a little on its
 * way up and leans from the chamber towards the vent at most {@link #MAX_LEAN} blocks per block, so
 * its cross-sections always overlap.
 * <p>
 * Everything that is not cavern or conduit within {@link #WALL} blocks of them (Chebyshev distance)
 * becomes the chamber's wall of basalt, blackstone and magma ({@code MagmaChamberFeature}), which
 * seals the lava off from caves, aquifers and anything else; this class only says where the
 * cavity is. Sealing holds by construction: each column's lava fills its cavern from the floor up
 * to the lake level, so beside and below every lava cell lies either lava or a cell outside the
 * cavity, and the conduit never meets the cavern beside it (no column's cavern reaches above
 * {@link #top}, where the pipe begins), so lava in the pipe touches only lava, the wall, or the fall
 * below its mouth.
 * <p>
 * The plan is a pure function of the volcano site and the world seed, made once per site
 * ({@link TerrainModel#magmaChamber}); the per-column queries are a few noise samples.
 */
public final class MagmaChamber {
    /** What a cell of the cavity holds. */
    public static final int NONE = 0;
    public static final int LAVA = 1;
    public static final int AIR = 2;
    /** Falling lava: the fall from the conduit onto the chamber's lake. */
    public static final int FALL = 3;
    /** Thickness of the wall around the cavity, in blocks (Chebyshev distance). */
    public static final int WALL = 3;
    /** Indices into the array {@link #column} fills. */
    public static final int FLOOR = 0;
    public static final int CEILING = 1;
    /** 1 if the column lies under the conduit's mouth, where the lava falls from the dome. */
    public static final int UNDER_MOUTH = 2;
    public static final int COLUMN_OUT = 3;

    /** Steepest lean of the conduit, in blocks across per block up, wander included. */
    static final double MAX_LEAN = 0.6;
    /** Shortest conduit between the chamber's dome and its top. */
    private static final int MIN_CONDUIT = 36;
    /** Rock left at least between the chamber's wall and the ground above it. */
    private static final int MIN_COVER = 24;
    /** Depth of the plug of solid rock between a closed conduit and the crater floor. */
    private static final int PLUG = 28;
    /** Lava at least this deep (in blocks) is open lava, not a plate of crust flush with the lake. */
    private static final double OPEN_LAVA = 1.5;
    /** Height over which an open conduit narrows to its throat under the vent. */
    private static final double THROAT = 14.0;
    /** Candidate vents checked on the model's columns, best first, before the conduit is plugged. */
    private static final int VENT_CHECKS = 48;
    private static final int OUTLINE_SAMPLES = 360;
    private static final double TAU = Math.PI * 2.0;
    private static final long SALT = 0x3A6D_A7C4_A3B3_E5L;

    private final SimplexNoise noise;
    /** The volcano's centre, for the finder and descriptions. */
    public final double volcanoX;
    public final double volcanoZ;
    /** Centre of the chamber; the conduit leaves the dome right above it. */
    public final double x;
    public final double z;
    /** Mean radius of the chamber's outline, and the outline's radius per bearing. */
    public final double radius;
    private final float[] outline;
    private final double maxRadius;
    /** Height at which floor and ceiling meet along the outline. */
    public final double rim;
    /** Depth of the bowl under {@link #rim} and height of the dome over it. */
    private final double bowl;
    private final double dome;
    /** Exclusive top of the chamber's lava lake. */
    public final int lake;
    /** The dome's highest point, the cell above the cavern's air over the conduit's mouth. */
    public final int top;
    /** Where the conduit meets the crater (the vent), its highest cell (inclusive) and its radius. */
    public final double ventX;
    public final double ventZ;
    public final int conduitTop;
    public final double conduitRadius;
    /** Radius of an open conduit's throat, where it opens into the crater's lake. */
    public final double ventRadius;
    /** Whether the conduit reaches the crater's lava lake (otherwise it ends in a plug of rock). */
    public final boolean open;
    /** How far the conduit's axis wanders either way, and the phases of its wander. */
    private final double wander;
    private final double wanderX;
    private final double wanderZ;
    /** Lowest crater floor, below which lava may only lie inside the cavity. */
    public final int craterFloor;
    /** The cavity's bounds, inclusive (the wall adds {@link #WALL} on every side). */
    public final int minX;
    public final int maxX;
    public final int minY;
    public final int maxY;
    public final int minZ;
    public final int maxZ;
    /** Shares of the chamber the shore and the lake take, for the description. */
    private final double lakeShare;

    private MagmaChamber(Builder b) {
        noise = b.noise;
        volcanoX = b.volcanoX;
        volcanoZ = b.volcanoZ;
        x = b.x;
        z = b.z;
        radius = b.radius;
        outline = b.outline;
        maxRadius = b.maxRadius;
        rim = b.rim;
        bowl = b.bowl;
        dome = b.dome;
        lake = b.lake;
        top = b.top;
        ventX = b.ventX;
        ventZ = b.ventZ;
        conduitTop = b.conduitTop;
        conduitRadius = b.conduitRadius;
        ventRadius = b.ventRadius;
        open = b.open;
        wander = b.wander;
        wanderX = b.wanderX;
        wanderZ = b.wanderZ;
        craterFloor = b.craterFloor;
        lakeShare = b.lakeShare;
        double reach = conduitRadius + wander + 1.0;
        minX = (int) Math.floor(Math.min(x - maxRadius, Math.min(ventX, x) - reach)) - 1;
        maxX = (int) Math.ceil(Math.max(x + maxRadius, Math.max(ventX, x) + reach)) + 1;
        minZ = (int) Math.floor(Math.min(z - maxRadius, Math.min(ventZ, z) - reach)) - 1;
        maxZ = (int) Math.ceil(Math.max(z + maxRadius, Math.max(ventZ, z) + reach)) + 1;
        // the floor's bumps reach at most FLOOR_BUMP below the bowl
        minY = (int) Math.floor(rim - bowl - FLOOR_BUMP) - 1;
        maxY = conduitTop;
    }

    // =========================================================================================
    // Queries
    // =========================================================================================

    /** Whether the cavity or its wall reaches into the block box given (inclusive). */
    public boolean touches(int x0, int z0, int x1, int z1) {
        return x1 >= minX - WALL && x0 <= maxX + WALL && z1 >= minZ - WALL && z0 <= maxZ + WALL;
    }

    /** Most the floor rises or falls by its bumps, and most the dome's ribs hang below it. */
    private static final double FLOOR_BUMP = 3.0;
    private static final double RIB = 5.0;

    /**
     * The chamber's cavern in column {@code (bx, bz)}: {@code out[FLOOR]} its lowest cell,
     * {@code out[CEILING]} the first cell above it (equal when the column holds no cavern), and
     * {@code out[UNDER_MOUTH]} 1 under the conduit's mouth, where lava falls from the dome. In the
     * cavern, cells below {@link #lake} are lava and the rest air (falling lava under the mouth).
     */
    public void column(int bx, int bz, int[] out) {
        out[FLOOR] = 0;
        out[CEILING] = 0;
        out[UNDER_MOUTH] = 0;
        double dx = bx + 0.5 - x;
        double dz = bz + 0.5 - z;
        double d2 = dx * dx + dz * dz;
        if (d2 >= maxRadius * maxRadius) {
            return;
        }
        double d = Math.sqrt(d2);
        double rho = d / outlineAt(Math.atan2(dz, dx));
        if (rho >= 1.0) {
            return;
        }
        double q = rho * rho;
        // a bowl and a dome meeting along the outline
        double floor = rim - bowl * (1.0 - q);
        double ceiling = rim + dome * (1.0 - Math.pow(rho, 2.2));
        // the floor's blobs and hollows: islands in the lake, knolls on the shore, fading out at
        // the edge so the outline stays where the bowl and dome meet
        double edge = smoothstep(1.0, 0.82, rho);
        floor += FLOOR_BUMP * edge * (0.75 * noise.noise2(bx / 17.0 + 31.0, bz / 17.0 - 12.0)
            + 0.25 * noise.noise2(bx / 6.0 - 7.0, bz / 6.0 + 55.0));
        // ribs and hanging knobs under the dome, none around the conduit's mouth
        double mouth = Math.sqrt(d2) - (conduitRadius + 2.0);
        double ribs = Math.max(0.0, noise.noise2(bx / 9.0 + 140.0, bz / 9.0 - 60.0)) * (0.6 + 0.4 * noise.noise2(bx / 4.0 - 90.0, bz / 4.0 + 17.0));
        ceiling -= RIB * ribs * smoothstep(4.0, 12.0, mouth);
        // pillars of rock from floor to ceiling on the shore, flaring at both ends
        double pillar = smoothstep(0.58, 0.78, noise.noise2(bx / 14.0 - 230.0, bz / 14.0 + 410.0)) * smoothstep(0.5, 0.62, rho);
        if (pillar > 0.0) {
            double mid = 0.5 * (floor + ceiling);
            floor += (mid - floor) * pillar;
            ceiling -= (ceiling - mid) * pillar;
        }
        int f = (int) Math.ceil(floor);
        int c = Math.min(top, (int) Math.floor(ceiling));
        if (mouth <= 0.0) {
            // the conduit's mouth: the dome is level at its top, the bowl deep enough below it for
            // the fall to land in lava
            c = top;
            f = Math.min(f, lake - 3);
            if (d2 < conduitRadius * conduitRadius) {
                out[UNDER_MOUTH] = 1;
            }
        }
        if (c <= f) {
            return;
        }
        out[FLOOR] = f;
        out[CEILING] = c;
    }

    /** The cell {@code (bx, by, bz)} of the cavity: {@link #NONE}, {@link #LAVA}, {@link #AIR} or {@link #FALL}. */
    public int cell(int bx, int by, int bz, int[] scratch) {
        if (by >= top && by <= conduitTop) {
            double[] a = axis(by, new double[2]);
            double dx = bx + 0.5 - a[0];
            double dz = bz + 0.5 - a[1];
            double r = conduitRadius(by);
            return dx * dx + dz * dz < r * r ? LAVA : NONE;
        }
        column(bx, bz, scratch);
        if (by < scratch[FLOOR] || by >= scratch[CEILING]) {
            return NONE;
        }
        return by < lake ? LAVA : scratch[UNDER_MOUTH] != 0 ? FALL : AIR;
    }

    /**
     * Centre of the conduit's cross-section at height {@code by} (from {@link #top} to
     * {@link #conduitTop}) into {@code out}: from the chamber's centre straight towards the vent,
     * wandering a little between them but not at either end.
     */
    public double[] axis(int by, double[] out) {
        double t = conduitTop > top ? clamp((by - top) / (double) (conduitTop - top), 0.0, 1.0) : 0.0;
        double sway = wander * Math.sin(Math.PI * t);
        out[0] = x + (ventX - x) * t + sway * noise.noise2(by / 34.0 + wanderX, 5.0);
        out[1] = z + (ventZ - z) * t + sway * noise.noise2(by / 34.0 + wanderZ, -5.0);
        return out;
    }

    /**
     * Radius of the conduit at height {@code by}: an open conduit narrows over its top
     * {@value #THROAT} blocks to its throat, which opens into the crater's lake.
     */
    public double conduitRadius(int by) {
        if (!open || by <= conduitTop - THROAT) {
            return conduitRadius;
        }
        return conduitRadius + (ventRadius - conduitRadius) * smoothstep(conduitTop - THROAT, conduitTop, by);
    }

    /** Radius of the outline at a bearing. */
    private double outlineAt(double theta) {
        double p = (theta / TAU - Math.floor(theta / TAU)) * OUTLINE_SAMPLES;
        int i = Math.min((int) p, OUTLINE_SAMPLES - 1);
        double f = p - i;
        int j = i + 1 == OUTLINE_SAMPLES ? 0 : i + 1;
        return outline[i] + (outline[j] - outline[i]) * f;
    }

    /** A seeded 3D noise in -1..1 for the materials of the wall. */
    public double noise3(double nx, double ny, double nz) {
        return noise.noise3(nx, ny, nz);
    }

    /**
     * A spot to stand on the chamber's shore, looking over the lake: the shore column nearest the
     * lake's edge on the bearing towards the volcano's centre, or on the first of eight others that
     * has room to stand. Returns {x, y of the feet, z, yaw towards the chamber's centre} or null.
     */
    public double[] standPoint() {
        int[] col = new int[COLUMN_OUT];
        double base = Math.atan2(volcanoZ - z, volcanoX - x);
        for (int k = 0; k < 9; k++) {
            double a = base + (k == 0 ? 0.0 : (k - 1) * TAU / 8.0 + 0.3);
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            for (double r = radius * 0.3; r < maxRadius; r += 1.0) {
                int bx = (int) Math.floor(x + ca * r);
                int bz = (int) Math.floor(z + sa * r);
                column(bx, bz, col);
                if (col[CEILING] <= col[FLOOR]) {
                    continue;
                }
                // dry ground with headroom, a block or more above the lava
                if (col[FLOOR] >= lake + 1 && col[CEILING] - col[FLOOR] >= 4) {
                    double yaw = Math.toDegrees(Math.atan2(-(x - bx - 0.5), z - bz - 0.5));
                    return new double[]{bx + 0.5, col[FLOOR], bz + 0.5, yaw};
                }
            }
        }
        return null;
    }

    /** A one-line account of the chamber, for the development tools and the test report. */
    public String describe() {
        return String.format(Locale.ROOT, "chamber at %.0f %.0f, radius %.0f (max %.0f), floor %d..rim %.0f..top %d, lake top %d (%.0f%% of the radius), "
                + "conduit r %.1f from %d to %d, vent %.0f %.0f (%s), lean %.2f, crater floor %d",
            x, z, radius, maxRadius, (int) Math.ceil(rim - bowl), rim, top, lake - 1, 100.0 * lakeShare, conduitRadius, top, conduitTop,
            ventX, ventZ, open ? "open to the crater's lava" : "plugged", Math.hypot(ventX - x, ventZ - z) / Math.max(1, conduitTop - top), craterFloor);
    }

    // =========================================================================================
    // Planning
    // =========================================================================================

    /**
     * Plans the chamber of volcano {@code v}: chooses the vent in the crater, sizes the chamber
     * after the crater and places it as deep as the ground over it and the conduit's length allow.
     */
    static MagmaChamber plan(TerrainModel model, TerrainModel.VolcanoSite v) {
        Builder b = new Builder();
        long seed = SeedMixer.derive(model.siteSeed(), SALT);
        b.noise = new SimplexNoise(seed);
        long h = SeedMixer.hash2(seed, (int) Math.floor(v.x), (int) Math.floor(v.z));
        b.volcanoX = v.x;
        b.volcanoZ = v.z;
        CraterShape.Crater k = v.crater;
        double r0 = v.craterRadius;
        // the lowest the crater floor gets, its relief and the pit's basin included
        b.craterFloor = (int) Math.floor(v.baseHeight + k.floorRel - k.floorAmp - (k.pit ? k.pitDeep : 0.0)) - 1;
        b.conduitRadius = clamp(2.5 + r0 / 40.0, 3.0, 6.0);
        b.ventRadius = Math.max(2.5, 0.6 * b.conduitRadius);

        // ---- the vent: in the crater's lava lake, where its lava is deepest, or the centre ------
        double[] vent = v.lavaLevel != ColumnSample.NO_WATER ? findVent(model, v, b.ventRadius) : null;
        b.open = vent != null;
        b.ventX = vent != null ? vent[0] : v.x;
        b.ventZ = vent != null ? vent[1] : v.z;
        b.conduitTop = b.open ? (int) v.lavaLevel - 1 : b.craterFloor - PLUG;

        // ---- size --------------------------------------------------------------------------
        b.radius = clamp(0.5 * r0, 26.0, 96.0) * (0.85 + 0.3 * unit(h, 1));
        b.dome = clamp(0.32 * b.radius, 14.0, 32.0);
        b.bowl = clamp(0.3 * b.radius, 10.0, 26.0);
        b.outline = new float[OUTLINE_SAMPLES];
        double ox = 300.0 * unit(h, 2);
        double oz = 300.0 * unit(h, 3);
        double maxR = 0.0;
        for (int i = 0; i < OUTLINE_SAMPLES; i++) {
            double a = i * TAU / OUTLINE_SAMPLES;
            double c = Math.cos(a);
            double s = Math.sin(a);
            double r = b.radius * (1.0 + 0.15 * b.noise.noise2(c * 1.0 + ox, s * 1.0 + oz) + 0.05 * b.noise.noise2(c * 2.2 - oz, s * 2.2 + ox));
            b.outline[i] = (float) r;
            maxR = Math.max(maxR, r);
        }
        b.maxRadius = maxR + 1.0;
        b.lakeShare = 0.7 + 0.16 * unit(h, 4);
        b.wanderX = 400.0 * unit(h, 5);
        b.wanderZ = 400.0 * unit(h, 6);

        // ---- height: halfway between the volcano's foot and the crater floor, lower if the ground
        // over the chamber or the conduit's length ask for it --------------------------------------
        double floorRise = b.craterFloor - v.baseHeight;
        int top = (int) Math.floor(v.baseHeight + 0.45 * floorRise);
        top = Math.min(top, b.conduitTop - MIN_CONDUIT);
        for (int pass = 0; pass < 3; pass++) {
            // the chamber leans from the vent towards the volcano's centre, as far as the conduit's
            // lean allows
            int length = b.conduitTop - top;
            // the wander leans the pipe by up to its amplitude over the half-wave of its envelope
            // and over its noise's typical slope (2.2 per unit, read every 34 blocks); the rest of
            // the lean budget is room to shift the chamber. Even at the noise's steepest the pipe
            // moves less than its radius from one block to the next, so it never breaks apart
            b.wander = Math.min(4.0, 0.05 * length);
            double room = Math.max(0.0, MAX_LEAN * length - b.wander * (Math.PI + 2.2 * length / 34.0) - 1.0);
            double tx = (v.x - b.ventX) * 0.5;
            double tz = (v.z - b.ventZ) * 0.5;
            double shift = Math.hypot(tx, tz);
            double scale = shift > room ? room / shift : 1.0;
            b.x = b.ventX + tx * scale;
            b.z = b.ventZ + tz * scale;
            double ground = lowestGround(model, b.x, b.z, b.maxRadius + WALL + 4.0);
            int allowed = (int) Math.floor(ground) - MIN_COVER - WALL;
            if (top <= allowed) {
                break;
            }
            top = allowed;
        }
        b.top = top;
        b.rim = top - b.dome;
        b.lake = (int) Math.floor(b.rim - b.bowl * (1.0 - b.lakeShare * b.lakeShare));
        return new MagmaChamber(b);
    }

    /**
     * The vent: a point of the crater's lava lake where every column within the throat's radius
     * (and one block more) lies under the lake's lava, so the conduit's lava joins the lake and
     * nowhere meets dry ground or air above it. Of those points, the one over the deepest open lava
     * (lava over the crater floor rather than a plate of crust flush with it), nearer the centre on
     * a tie. Searched on the crater's own surface, then checked best first on the model's columns,
     * which also hold whatever else shapes the ground there and are what the crater's lava is filled
     * from. Null if no point of the lake passes (a lake too small for the throat, or another cone
     * burying the crater): the conduit is then plugged.
     */
    private static double[] findVent(TerrainModel model, TerrainModel.VolcanoSite v, double ventRadius) {
        CraterShape.Crater k = v.crater;
        double reach = 0.0;
        for (float e : k.edge) {
            reach = Math.max(reach, e);
        }
        int step = Math.max(2, (int) Math.round(reach / 60.0));
        int n = (int) Math.ceil(reach / step);
        int size = 2 * n + 1;
        double[] depth = new double[size * size];
        double[] out = new double[CraterShape.OUT];
        int cx = (int) Math.floor(v.x);
        int cz = (int) Math.floor(v.z);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double surface = model.volcanoSurface(v, cx + (i - n) * step, cz + (j - n) * step, out);
                depth[i * size + j] = out[0] >= 0.0 ? v.lavaLevel - surface : -1.0;
            }
        }
        double r = ventRadius + 1.0;
        int disc = (int) Math.ceil(r / step);
        java.util.List<double[]> candidates = new java.util.ArrayList<>();
        for (int i = disc; i < size - disc; i++) {
            for (int j = disc; j < size - disc; j++) {
                double least = Double.MAX_VALUE;
                double sum = 0.0;
                int open = 0;
                int points = 0;
                for (int di = -disc; di <= disc && least > 0.25; di++) {
                    for (int dj = -disc; dj <= disc; dj++) {
                        if ((di * di + dj * dj) * step * step <= r * r + step * step) {
                            double d = depth[(i + di) * size + j + dj];
                            least = Math.min(least, d);
                            sum += d;
                            open += d >= OPEN_LAVA ? 1 : 0;
                            points++;
                        }
                    }
                }
                if (least <= 0.25) {
                    continue;
                }
                // deep open lava is best, up to a few blocks; then nearer the centre
                double dist = Math.hypot(i - n, j - n) * step;
                double score = Math.min(sum / points, 6.0) + 2.0 * open / points - dist / Math.max(20.0, reach);
                candidates.add(new double[]{score, cx + (i - n) * step + 0.5, cz + (j - n) * step + 0.5});
            }
        }
        candidates.sort((a, b) -> Double.compare(b[0], a[0]));
        for (int c = 0; c < Math.min(VENT_CHECKS, candidates.size()); c++) {
            double[] p = candidates.get(c);
            if (checkVent(model, v, p[1], p[2], r)) {
                return new double[]{p[1], p[2]};
            }
        }
        return null;
    }

    /**
     * Whether every column within {@code r} blocks of a point lies under the crater's lava: the
     * model corners it is interpolated from all hold this volcano's lava, over ground below its top.
     */
    private static boolean checkVent(TerrainModel model, TerrainModel.VolcanoSite v, double px, double pz, double r) {
        for (int dx = (int) -Math.ceil(r); dx <= Math.ceil(r); dx++) {
            for (int dz = (int) -Math.ceil(r); dz <= Math.ceil(r); dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                int bx = (int) Math.floor(px) + dx;
                int bz = (int) Math.floor(pz) + dz;
                for (int c = 0; c < 4; c++) {
                    ColumnSample s = model.sample((bx & ~3) + (c & 1) * 4, (bz & ~3) + (c >> 1) * 4);
                    if (s.lavaLevel != v.lavaLevel || s.height >= v.lavaLevel - 0.25) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** The lowest model ground within {@code r} blocks of a point, on a coarse grid. */
    private static double lowestGround(TerrainModel model, double px, double pz, double r) {
        double low = Double.MAX_VALUE;
        int step = 8;
        int n = (int) Math.ceil(r / step);
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                if ((i * i + j * j) * step * step > (r + step) * (r + step)) {
                    continue;
                }
                low = Math.min(low, model.sample(((int) Math.floor(px) + i * step) & ~3, ((int) Math.floor(pz) + j * step) & ~3).height);
            }
        }
        return low;
    }

    private static double unit(long hash, int channel) {
        return SeedMixer.unit(SeedMixer.mix(hash + channel * 0xD1B54A32D192ED03L));
    }

    /** Values of a plan while it is being made. */
    private static final class Builder {
        SimplexNoise noise;
        double volcanoX;
        double volcanoZ;
        double x;
        double z;
        double radius;
        float[] outline;
        double maxRadius;
        double rim;
        double bowl;
        double dome;
        int lake;
        int top;
        double ventX;
        double ventZ;
        int conduitTop;
        double conduitRadius;
        double ventRadius;
        boolean open;
        double wander;
        double wanderX;
        double wanderZ;
        int craterFloor;
        double lakeShare;
    }
}
