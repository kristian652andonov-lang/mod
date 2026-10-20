package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import com.terrahaul.worldgen.water.FallPlan;
import com.terrahaul.worldgen.water.FallPlanner;
import com.terrahaul.worldgen.water.WaterPlan;
import com.terrahaul.worldgen.water.WaterSolver;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Development tool for waterfalls: how much ground each plan cuts away below the terrain model's
 * surface and in what shape, whether what it builds keeps the water invariants, and how it looks
 * in lit views of the real 3D terrain (see {@link FallRender}).
 * <p>
 * Modes ({@code java ... FallStudy <mode> <seed> ...}):
 * <ul>
 *   <li>{@code survey <seed> <half> <sitesOut>}: every waterfall in {@code [-half, half]^2} built on
 *       the solved world: cut volume (blocks removed below the model surface), fill, deepest cut,
 *       how far around the foot of the fall cut walls stand (an amphitheatre stands all around),
 *       rock built up over open air in the 3D terrain, the water invariants, planning cost; writes
 *       one site per line for {@code render}. Exits with status 1 if any plan cuts more than the
 *       planner's cap, digs a hole, builds over a gap or breaks a water invariant.</li>
 *   <li>{@code render <seed> <sites> <outDir> <prefix> [max]}: for each site, views from the front,
 *       from the side, from high up and from the finder's viewpoint, and a top view, with the same
 *       cameras every run (they depend on the site line only), so a site can be compared before and
 *       after a change; the plan found there now is measured and its numbers printed.</li>
 *   <li>{@code shot <seed> <x> <z> <out.png>}: close, larger views of the plan found nearest to
 *       {@code (x, z)} now, its cameras following the plan rather than a site line: from the
 *       finder's viewpoint, from in front of the face, from three quarters, and its earthworks.</li>
 *   <li>{@code cost <seed> <half>}: planning time per cell on a fresh planner.</li>
 *   <li>{@code dump <seed> <x> <z> <radius>}: the solved columns around a point, with the plans
 *       applied: water level, falling water, banks the solver raised and the shaped ground.</li>
 * </ul>
 * Only the parts of {@link FallPlan} that every version of it has are used, so the tool can be
 * run against older builds for comparisons.
 */
public final class FallStudy {
    /** Deepest a natural fall's earthworks may cut any column below the model surface. */
    private static final int MAX_DEPTH = 12;
    /** Widest arc around the foot of a fall in which cut walls six blocks or more may stand. */
    private static final int MAX_WALL_DEGREES = 90;

    private FallStudy() {
    }

    /** The planner's cap on the ground a fall may cut away (the same rule for older planners). */
    static int cutCap(int drop) {
        try {
            return FallPlanner.cutCap(drop);
        } catch (NoSuchMethodError e) {
            return 1400 + 30 * drop;
        }
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        long seed = Long.parseLong(args[1]);
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        switch (mode) {
            case "survey" -> survey(model, Integer.parseInt(args[2]), new File(args[3]));
            case "render" -> render(model, new File(args[2]), new File(args[3]), args[4], args.length > 5 ? Integer.parseInt(args[5]) : 99);
            case "cost" -> cost(model, Integer.parseInt(args[2]));
            case "shot" -> shot(model, Integer.parseInt(args[2]), Integer.parseInt(args[3]), new File(args[4]));
            case "dump" -> dump(model, Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    // =========================================================================================
    // solved regions
    // =========================================================================================

    /** Independent chunk solves assembled over whole chunks, with every field the views need. */
    static final class Region {
        final int ax;
        final int az;
        final int w;
        final int h;
        final WaterStudy.Area area;
        final byte[] role;
        final boolean[] shaped;
        final boolean[] planned;

        Region(int ax, int az, int w, int h) {
            this.ax = ax;
            this.az = az;
            this.w = w;
            this.h = h;
            area = new WaterStudy.Area(ax, az, w, h);
            role = new byte[w * h];
            shaped = new boolean[w * h];
            planned = new boolean[w * h];
        }

        boolean contains(int x, int z) {
            return area.contains(x, z);
        }

        int at(int x, int z) {
            return area.at(x, z);
        }
    }

    /**
     * Solves the chunks covering a block rectangle one by one, as the feature does, assembles
     * their own columns, counts every border decision two chunks disagree on and checks the water
     * invariants on the result.
     */
    static Region solve(TerrainModel model, int minX, int minZ, int maxX, int maxZ, WaterStudy.Stats st) {
        int ccx = Math.floorDiv(minX, 16);
        int ccz = Math.floorDiv(minZ, 16);
        int nx = Math.floorDiv(maxX, 16) - ccx + 1;
        int nz = Math.floorDiv(maxZ, 16) - ccz + 1;
        Region r = new Region(ccx * 16, ccz * 16, nx * 16, nz * 16);
        WaterStudy.Area a = r.area;
        WaterSolver[][] solves = new WaterSolver[nx][nz];
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) {
                long t0 = System.nanoTime();
                WaterSolver ws = WaterStudy.solveChunk(model, ccx + i, ccz + j);
                st.nanos += System.nanoTime() - t0;
                st.chunks++;
                solves[i][j] = ws;
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        int k = WaterSolver.index(bx + WaterSolver.MARGIN, bz + WaterSolver.MARGIN);
                        int t = a.at((ccx + i) * 16 + bx, (ccz + j) * 16 + bz);
                        a.ground[t] = ws.ground[k];
                        a.natural[t] = ws.natural[k];
                        a.level[t] = ws.level[k];
                        a.upper[t] = ws.upper[k];
                        a.solidTop[t] = ws.solidTop[k];
                        a.step[t] = ws.step[k];
                        a.kind[t] = ws.kind[k];
                        a.boulder[t] = ws.boulder[k];
                        r.role[t] = ws.role[k];
                        r.shaped[t] = ws.shaped[k];
                        r.planned[t] = ws.planned[k];
                    }
                }
            }
        }
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) {
                WaterSolver ws = solves[i][j];
                for (int bx = -2; bx < 18; bx++) {
                    for (int bz = -2; bz < 18; bz++) {
                        if (bx >= 0 && bx < 16 && bz >= 0 && bz < 16) {
                            continue;
                        }
                        int x = (ccx + i) * 16 + bx;
                        int z = (ccz + j) * 16 + bz;
                        if (!a.contains(x, z)) {
                            continue;
                        }
                        int k = ws.indexOf(x, z);
                        int t = a.at(x, z);
                        if (ws.level[k] != a.level[t] || ws.step[k] != a.step[t] || ws.solidTop[k] != a.solidTop[t] || ws.ground[k] != a.ground[t]
                            || ws.upper[k] != a.upper[t] && ws.step[k] != WaterSolver.FLAT) {
                            st.mismatches++;
                            st.example(String.format(Locale.ROOT, "mismatch at %d,%d", x, z));
                        }
                    }
                }
            }
        }
        WaterStudy.check(a, st);
        return r;
    }

    // =========================================================================================
    // the scene: real 3D terrain, shaped columns and water
    // =========================================================================================

    /** The world a player would see around a solved region, for {@link FallRender}. */
    static final class World implements FallRender.Scene {
        final TerrainModel model;
        final Region region;
        final FragmentAudit.Field field;
        final int fx0;
        final int fz0;
        final int fn;
        /** Top solid block of every column of the field's extent, after the water features. */
        final int[] top;
        final byte[] slope;
        final boolean[] cold;
        final boolean[] sandy;
        final int ceiling;
        final int sl;
        /** The waterfall whose decoration the view shows (wet rock, scoured sides), or {@code null}. */
        final FallPlan plan;
        /** Per region column: distance to the fall's water, and whether it lies on the face. */
        final float[] spray;
        final boolean[] face;
        boolean planCold;
        boolean planHumid;

        World(TerrainModel model, Region region, int cx, int cz, int size, FallPlan plan) {
            this.model = model;
            this.region = region;
            this.sl = model.seaLevel();
            spray = new float[region.w * region.h];
            face = new boolean[region.w * region.h];
            java.util.Arrays.fill(spray, Float.MAX_VALUE);
            FallPlan decorated = plan;
            if (plan != null && plan.type != FallPlan.CASCADE) {
                try {
                    for (int z = region.az; z < region.az + region.h; z++) {
                        for (int x = region.ax; x < region.ax + region.w; x++) {
                            if (x >= plan.minX() && x <= plan.maxX() && z >= plan.minZ() && z <= plan.maxZ()) {
                                int k = region.at(x, z);
                                spray[k] = (float) plan.spray(x, z);
                                face[k] = plan.onFace(x, z);
                            }
                        }
                    }
                    // the climate the game's decoration reads, at the middle of the fall
                    ColumnSample c = model.sample((plan.lookX + 2) & ~3, (plan.lookZ + 2) & ~3);
                    planCold = c.temperature < -0.2;
                    planHumid = c.humidity > 0.05 && !planCold;
                } catch (NoSuchMethodError e) {
                    // an older planner without these queries: no decoration
                    decorated = null;
                }
            }
            this.plan = decorated;
            field = FragmentAudit.Field.build(model, cx, cz, size);
            fx0 = field.x0;
            fz0 = field.z0;
            fn = (field.nx - 1) * 4;
            top = new int[fn * fn];
            slope = new byte[fn * fn];
            cold = new boolean[fn * fn];
            sandy = new boolean[fn * fn];
            int hi = Integer.MIN_VALUE;
            for (int j = 0; j < fn; j++) {
                for (int i = 0; i < fn; i++) {
                    int x = fx0 + i;
                    int z = fz0 + j;
                    int t;
                    if (region.contains(x, z)) {
                        int k = region.at(x, z);
                        t = region.shaped[k] ? region.area.solidTop[k] : Math.max(field.top(i, j), region.area.solidTop[k]);
                    } else {
                        t = field.top(i, j);
                    }
                    top[j * fn + i] = t;
                    hi = Math.max(hi, t);
                    ColumnSample c = model.sample((x + 2) & ~3, (z + 2) & ~3);
                    double peak = c.mountain * c.peakness;
                    cold[j * fn + i] = c.temperature < -0.45 || c.temperature < 0.2 && peak > 0.35;
                    sandy[j * fn + i] = c.beach > 0.4 && t < sl + 3 || t < sl;
                }
            }
            for (int j = 1; j < fn - 1; j++) {
                for (int i = 1; i < fn - 1; i++) {
                    int k = j * fn + i;
                    int d = Math.max(Math.max(Math.abs(top[k + 1] - top[k]), Math.abs(top[k - 1] - top[k])),
                        Math.max(Math.abs(top[k + fn] - top[k]), Math.abs(top[k - fn] - top[k])));
                    slope[k] = (byte) Math.min(100, d);
                }
            }
            int waterTop = Integer.MIN_VALUE;
            for (int t = 0; t < region.w * region.h; t++) {
                if (region.area.level[t] != WaterSolver.NONE) {
                    waterTop = Math.max(waterTop, Math.max(region.area.level[t], region.area.upper[t]));
                }
            }
            ceiling = Math.max(hi, waterTop) + 1;
        }

        @Override
        public int ceiling() {
            return ceiling;
        }

        @Override
        public int block(int x, int y, int z) {
            int m = raw(x, y, z);
            if (plan == null || m == FallRender.AIR || m == FallRender.WATER || m == FallRender.FALLING || !region.contains(x, z)) {
                return m;
            }
            int k = region.at(x, z);
            return region.planned[k] ? decorate(m, k, x, y, z) : m;
        }

        /**
         * What the game's waterfall decoration makes of a block: soil right beside the falling water
         * scoured to rock, rock near the spray mossy (as {@code WaterfallFeature} does it).
         */
        private int decorate(int m, int k, int x, int y, int z) {
            float sp = spray[k];
            if (sp > 7.0f) {
                return m;
            }
            int i = x - fx0;
            int j = z - fz0;
            int t = i >= 0 && j >= 0 && i < fn && j < fn ? top[j * fn + i] : region.area.solidTop[k];
            boolean scour = face[k] && sp <= 1.6f && (m == FallRender.GRASS || m == FallRender.DIRT) && y >= t - 3 && y >= plan.pool;
            boolean wet = !planCold && (m == FallRender.STONE || m == FallRender.COBBLE) && y > plan.pool && y <= Math.min(t, plan.top);
            if (!scour && !wet || y < t && !open(x, y, z)) {
                return m;
            }
            if (scour) {
                double n = model.detail3(4, x / 4.0, y / 3.0, z / 4.0);
                m = planCold ? FallRender.STONE : planHumid ? (n > 0.25 ? FallRender.MOSS : FallRender.MOSSY)
                    : n > 0.0 ? FallRender.STONE : FallRender.COBBLE;
                if (m == FallRender.MOSS || m == FallRender.MOSSY || planCold) {
                    return m;
                }
            }
            if (!planCold) {
                double n = model.detail3(4, x / 5.0, y / 5.0, z / 5.0) - 0.06 * sp;
                if (n > 0.12) {
                    return FallRender.MOSS;
                }
                if (n > -0.15) {
                    return FallRender.MOSSY;
                }
            }
            return m;
        }

        /** Whether a block has air or water beside it. */
        private boolean open(int x, int y, int z) {
            int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : sides) {
                int n = raw(x + d[0], y, z + d[1]);
                if (n == FallRender.AIR || n == FallRender.WATER || n == FallRender.FALLING) {
                    return true;
                }
            }
            return false;
        }

        /** The block before decoration. */
        int raw(int x, int y, int z) {
            int i = x - fx0;
            int j = z - fz0;
            boolean inField = i >= 0 && j >= 0 && i < fn && j < fn;
            if (region.contains(x, z)) {
                int k = region.at(x, z);
                WaterStudy.Area a = region.area;
                if (region.shaped[k]) {
                    if (a.solid(k, y)) {
                        return shapedMaterial(k, x, y, z, inField ? j * fn + i : -1);
                    }
                } else if (y <= a.solidTop[k] && y > a.natural[k] || inField && field.solid(i, y, j)) {
                    return naturalMaterial(x, y, z, inField ? j * fn + i : -1);
                }
                if (a.source(k, y)) {
                    return FallRender.WATER;
                }
                if (a.chute(k, y)) {
                    return FallRender.FALLING;
                }
                return y < sl && y > a.ground[k] ? FallRender.WATER : FallRender.AIR;
            }
            if (inField) {
                if (field.solid(i, y, j)) {
                    return naturalMaterial(x, y, z, j * fn + i);
                }
            } else if (y <= (int) Math.ceil(model.interpolatedHeight(x, z)) - 1) {
                return y >= (int) Math.ceil(model.interpolatedHeight(x, z)) - 1 ? FallRender.GRASS : FallRender.STONE;
            }
            return y < sl ? FallRender.WATER : FallRender.AIR;
        }

        private int naturalMaterial(int x, int y, int z, int f) {
            if (f < 0) {
                return FallRender.STONE;
            }
            int t = top[f];
            boolean steep = slope[f] >= 3;
            boolean exposedTop = !field.solid(x - fx0, y + 1, z - fz0);
            if (exposedTop && (y >= t - 1 || !steep)) {
                return cold[f] && !steep ? FallRender.SNOW : steep ? FallRender.STONE : sandy[f] ? FallRender.SAND : FallRender.GRASS;
            }
            if (!steep && y >= t - 3) {
                return sandy[f] ? FallRender.SAND : FallRender.DIRT;
            }
            return FallRender.STONE;
        }

        private int shapedMaterial(int k, int x, int y, int z, int f) {
            WaterStudy.Area a = region.area;
            boolean isTop = y == a.solidTop[k];
            boolean coldHere = f >= 0 && cold[f];
            switch (region.role[k]) {
                case WaterSolver.ROLE_BED -> {
                    if (!isTop) {
                        return FallRender.STONE;
                    }
                    return coldHere || FallRender.hash3(x >> 2, 7, z >> 2) > 0.5 ? FallRender.GRAVEL : FallRender.SAND;
                }
                case WaterSolver.ROLE_SHORE -> {
                    return isTop ? (coldHere ? FallRender.GRAVEL : FallRender.GRASS) : FallRender.DIRT;
                }
                case WaterSolver.ROLE_DAM, WaterSolver.ROLE_RIB -> {
                    return FallRender.COBBLE;
                }
                case WaterSolver.ROLE_ROCK -> {
                    return isTop && f >= 0 && slope[f] < 3 && !coldHere ? FallRender.GRASS : FallRender.STONE;
                }
                default -> {
                    if (a.step[k] == WaterSolver.SILL) {
                        return FallRender.COBBLE;
                    }
                    if (isTop) {
                        return coldHere ? FallRender.SNOW : f >= 0 && slope[f] >= 3 ? FallRender.STONE : FallRender.GRASS;
                    }
                    return y >= a.solidTop[k] - 3 ? FallRender.DIRT : FallRender.STONE;
                }
            }
        }
    }

    // =========================================================================================
    // what a plan cuts
    // =========================================================================================

    /** How a waterfall reshapes the ground, from the solved region around it. */
    static final class Cut {
        long removed;
        long added;
        int maxDepth;
        int deepColumns;
        int columns;
        /** Degrees around the foot of the fall in which cut walls of six blocks or more stand. */
        int wallDegrees;
        long water;
        /** Blocks removed in the channel, on the face, around the pool and along the outflow. */
        final java.util.TreeMap<String, Long> byPart = new java.util.TreeMap<>();

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "cut %6d blocks (deepest %2d, %4d columns, %3d of them 6+ deep, walls around the foot %3d deg), fill %4d, water %5d%s",
                removed, maxDepth, columns, deepColumns, wallDegrees, added, water, byPart.isEmpty() ? "" : " " + byPart);
        }
    }

    static Cut cut(Region r, FallPlan p) {
        Cut c = new Cut();
        WaterStudy.Area a = r.area;
        for (int x = Math.max(p.minX(), r.ax); x <= Math.min(p.maxX(), r.ax + r.w - 1); x++) {
            for (int z = Math.max(p.minZ(), r.az); z <= Math.min(p.maxZ(), r.az + r.h - 1); z++) {
                int k = a.at(x, z);
                if (!r.planned[k]) {
                    continue;
                }
                int topSolid = a.solidTop[k];
                int d = a.natural[k] - topSolid;
                if (d > 0) {
                    try {
                        c.byPart.merge(p.part(x, z), (long) d, Long::sum);
                    } catch (NoSuchMethodError e) {
                        // an older planner
                    }
                    c.removed += d;
                    c.columns++;
                    c.maxDepth = Math.max(c.maxDepth, d);
                    if (d >= 6) {
                        c.deepColumns++;
                    }
                } else if (d < 0 && a.level[k] == WaterSolver.NONE) {
                    c.added += -d;
                }
                if (a.level[k] != WaterSolver.NONE) {
                    c.water += Math.max(0, a.level[k] - 1 - a.ground[k]);
                }
            }
        }
        // around the foot of the fall (the middle of its pool, where the falling water lands):
        // rays every 10 degrees, a ray counts if it meets a column cut six or more blocks deep
        // within 20 blocks
        double fx = p.lipX + 0.5 + 2.0 * p.dirX;
        double fz = p.lipZ + 0.5 + 2.0 * p.dirZ;
        double sx = 0.0;
        double sz = 0.0;
        int n = 0;
        for (int x = Math.max(p.minX(), r.ax); x <= Math.min(p.maxX(), r.ax + r.w - 1); x++) {
            for (int z = Math.max(p.minZ(), r.az); z <= Math.min(p.maxZ(), r.az + r.h - 1); z++) {
                int k = a.at(x, z);
                if (r.planned[k] && a.level[k] == p.pool + 1 && Math.hypot(x - p.lipX, z - p.lipZ) < 80) {
                    sx += x + 0.5;
                    sz += z + 0.5;
                    n++;
                }
            }
        }
        if (n > 0 && p.type != FallPlan.SEA) {
            fx = sx / n;
            fz = sz / n;
        }
        int rays = 0;
        for (int deg = 0; deg < 360; deg += 10) {
            double ca = Math.cos(Math.toRadians(deg));
            double sa = Math.sin(Math.toRadians(deg));
            for (double d = 2.0; d <= 20.0; d += 0.5) {
                int x = (int) Math.floor(fx + ca * d);
                int z = (int) Math.floor(fz + sa * d);
                if (!r.contains(x, z)) {
                    break;
                }
                int k = a.at(x, z);
                if (r.planned[k] && a.natural[k] - a.solidTop[k] >= 6 && a.level[k] == WaterSolver.NONE) {
                    rays++;
                    break;
                }
            }
        }
        c.wallDegrees = rays * 10;
        return c;
    }

    /**
     * Columns a plan builds up (crags, rock lips, banks) where the real 3D terrain is open below
     * the model surface the building starts from, so what is built would stand over a gap:
     * {@code {count, deepest gap}}.
     */
    static int[] fillGaps(TerrainModel model, Region r, FallPlan p) {
        int size = Math.max(p.maxX() - p.minX(), p.maxZ() - p.minZ()) + 16;
        FragmentAudit.Field f = FragmentAudit.Field.build(model, (p.minX() + p.maxX()) / 2, (p.minZ() + p.maxZ()) / 2, size);
        WaterStudy.Area a = r.area;
        int count = 0;
        int deepest = 0;
        for (int x = Math.max(p.minX(), r.ax); x <= Math.min(p.maxX(), r.ax + r.w - 1); x++) {
            for (int z = Math.max(p.minZ(), r.az); z <= Math.min(p.maxZ(), r.az + r.h - 1); z++) {
                int k = a.at(x, z);
                if (!r.planned[k] || a.solidTop[k] <= a.natural[k]) {
                    continue;
                }
                // the fill starts at the model surface block itself, so only open air below it is a gap
                int y = a.natural[k] - 1;
                int gap = 0;
                while (gap < 12 && !f.solid(x - f.x0, y - gap, z - f.z0)) {
                    gap++;
                }
                if (gap > 0) {
                    count++;
                    deepest = Math.max(deepest, gap);
                }
            }
        }
        return new int[]{count, deepest};
    }

    // =========================================================================================
    // survey
    // =========================================================================================

    private static void survey(TerrainModel model, int half, File sitesOut) throws Exception {
        FallPlanner planner = FallPlanner.of(model);
        int n = half / FallPlanner.FALL_CELL;
        List<FallPlan> plans = new ArrayList<>();
        long t0 = System.nanoTime();
        int cells = 0;
        for (int cx = -n; cx < n; cx++) {
            for (int cz = -n; cz < n; cz++) {
                FallPlan p = planner.fall(cx, cz);
                cells++;
                if (p != null) {
                    plans.add(p);
                }
            }
        }
        double planMs = (System.nanoTime() - t0) / 1e6 / cells;
        WaterStudy.Stats total = new WaterStudy.Stats();
        List<Long> removed = new ArrayList<>();
        List<Integer> walls = new ArrayList<>();
        List<Integer> depths = new ArrayList<>();
        StringBuilder sites = new StringBuilder();
        int overCap = 0;
        int holes = 0;
        int totalGaps = 0;
        for (FallPlan p : plans) {
            WaterStudy.Stats st = new WaterStudy.Stats();
            Region r = solve(model, p.minX() - 4, p.minZ() - 4, p.maxX() + 4, p.maxZ() + 4, st);
            WaterStudy.accumulate(total, st);
            Cut c = cut(r, p);
            int[] gaps = fillGaps(model, r, p);
            totalGaps += gaps[0];
            removed.add(c.removed);
            walls.add(c.wallDegrees);
            depths.add(c.maxDepth);
            // the earthworks of a natural fall: within the cap, no column cut deep, no cut walls
            // standing around the foot of the fall (the amphitheatre of a quarried bowl)
            String flags = "";
            if (c.removed > cutCap(p.drop())) {
                overCap++;
                flags += "  OVER CAP " + cutCap(p.drop());
            }
            if (c.maxDepth > MAX_DEPTH || c.wallDegrees > MAX_WALL_DEGREES) {
                holes++;
                flags += "  HOLE";
            }
            if (gaps[0] > 0) {
                flags += "  GAPS " + gaps[0] + " (deepest " + gaps[1] + ")";
            }
            if (st.sourceFacingAir + st.mismatches + st.waterAboveAir > 0) {
                flags += "  BROKEN (source-air " + st.sourceFacingAir + ", mismatches " + st.mismatches + ", water over air " + st.waterAboveAir + ")";
            }
            System.out.printf(Locale.ROOT, "%-22s lip %6d %6d dir %2d %2d drop %3d (top %3d pool %3d) width %2d  %s%s%n", p.typeName(), p.lipX, p.lipZ,
                p.dirX, p.dirZ, p.drop(), p.top, p.pool, p.width(), c, flags);
            sites.append(String.format(Locale.ROOT, "%d %d %d %d %d %d %d %d %d %d%n", p.lipX, p.lipZ, p.dirX, p.dirZ, p.top, p.pool, p.width(),
                p.viewX, p.viewY, p.viewZ));
        }
        removed.sort(null);
        walls.sort(null);
        depths.sort(null);
        if (!plans.isEmpty()) {
            long sum = 0;
            for (long v : removed) {
                sum += v;
            }
            System.out.printf(Locale.ROOT, "%d waterfalls, planning %.2f ms per cell; cut volume mean %d median %d max %d; deepest cut median %d max %d; "
                    + "cut walls around the foot median %d max %d deg%n", plans.size(), planMs, sum / plans.size(), removed.get(removed.size() / 2),
                removed.get(removed.size() - 1), depths.get(depths.size() / 2), depths.get(depths.size() - 1), walls.get(walls.size() / 2),
                walls.get(walls.size() - 1));
        }
        System.out.printf(Locale.ROOT, "cut over the cap: %d plans; holes (a column cut more than %d deep, or cut walls more than %d deg around the foot): %d plans%n",
            overCap, MAX_DEPTH, MAX_WALL_DEGREES, holes);
        System.out.printf(Locale.ROOT, "invariants over %d chunks: mismatches %d, source-air %d, water-above-air %d, banks %s; built-up columns over open air "
                + "in the 3D terrain %d%n", total.chunks, total.mismatches, total.sourceFacingAir, total.waterAboveAir, WaterStudy.hist(total.bankHist), totalGaps);
        for (String e : total.examples) {
            System.out.println("    " + e);
        }
        if (sitesOut != null) {
            Files.writeString(sitesOut.toPath(), sites.toString());
        }
        // the limits are asserted, not just reported: a plan over the cut cap, a hole, rock built
        // over a gap or a broken water invariant fails the run
        long broken = overCap + holes + totalGaps + total.mismatches + total.sourceFacingAir + total.waterAboveAir;
        if (broken > 0) {
            System.out.println("FAILED: " + broken + " violations");
            System.exit(1);
        }
        System.out.println("OK: every plan within the cut cap, no holes, nothing built over a gap, water invariants hold");
    }

    // =========================================================================================
    // cost
    // =========================================================================================

    /**
     * Planning cost: every waterfall cell in {@code [-half, half]^2} planned on a fresh planner,
     * one at a time, with the time per cell (mean, slowest) and the share of cells with a fall.
     */
    private static void cost(TerrainModel model, int half) {
        // warm up the model's caches and the JIT on cells far away
        FallPlanner warm = FallPlanner.of(new TerrainModel(model.seed() + 1, TerrainSettings.DEFAULT));
        for (int i = 0; i < 40; i++) {
            warm.fall(1000 + i, 1000);
        }
        FallPlanner planner = FallPlanner.of(model);
        int n = half / FallPlanner.FALL_CELL;
        long total = 0;
        long slowest = 0;
        int cells = 0;
        int found = 0;
        for (int cx = -n; cx < n; cx++) {
            for (int cz = -n; cz < n; cz++) {
                long t0 = System.nanoTime();
                FallPlan p = planner.fall(cx, cz);
                long t = System.nanoTime() - t0;
                total += t;
                slowest = Math.max(slowest, t);
                cells++;
                if (p != null) {
                    found++;
                }
            }
        }
        System.out.printf(Locale.ROOT, "%d cells, %d waterfalls: planning %.2f ms per cell (with the neighbours each needs), slowest %.1f ms%n", cells, found,
            total / 1e6 / cells, slowest / 1e6);
    }

    // =========================================================================================
    // render
    // =========================================================================================

    /** The plan whose lip is nearest to {@code (x, z)}, within 96 blocks, or {@code null}. */
    static FallPlan planNear(TerrainModel model, int x, int z) {
        FallPlanner planner = FallPlanner.of(model);
        FallPlan best = null;
        double bestD = 96.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                FallPlan q = planner.fall(Math.floorDiv(x, FallPlanner.FALL_CELL) + dx, Math.floorDiv(z, FallPlanner.FALL_CELL) + dz);
                if (q != null && Math.hypot(q.lipX - x, q.lipZ - z) < bestD) {
                    bestD = Math.hypot(q.lipX - x, q.lipZ - z);
                    best = q;
                }
            }
        }
        return best;
    }

    private static void render(TerrainModel model, File sitesFile, File dir, String prefix, int max) throws Exception {
        dir.mkdirs();
        List<String> lines = Files.readAllLines(sitesFile.toPath());
        int index = 0;
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (++index > max) {
                break;
            }
            int[] v = Arrays.stream(line.trim().split("\\s+")).mapToInt(Integer::parseInt).toArray();
            int lipX = v[0];
            int lipZ = v[1];
            int dirX = v[2];
            int dirZ = v[3];
            int top = v[4];
            int pool = v[5];
            int drop = top - pool;
            String name = prefix + "_" + model.seed() + "_" + index;
            FallPlan p = planNear(model, lipX, lipZ);
            int reach = (int) Math.max(110, 1.6 * drop + 70);
            int minX = lipX - reach;
            int minZ = lipZ - reach;
            int maxX = lipX + reach;
            int maxZ = lipZ + reach;
            if (p != null) {
                minX = Math.min(minX, p.minX() - 8);
                minZ = Math.min(minZ, p.minZ() - 8);
                maxX = Math.max(maxX, p.maxX() + 8);
                maxZ = Math.max(maxZ, p.maxZ() + 8);
            }
            WaterStudy.Stats st = new WaterStudy.Stats();
            Region r = solve(model, minX, minZ, maxX, maxZ, st);
            int size = Math.max(maxX - minX, maxZ - minZ) + 48;
            World world = new World(model, r, (minX + maxX) / 2, (minZ + maxZ) / 2, size, p);
            String info;
            if (p != null) {
                Cut c = cut(r, p);
                info = String.format(Locale.ROOT, "%s lip %d,%d drop %d width %d | %s", p.typeName(), p.lipX, p.lipZ, p.drop(), p.width(), c);
            } else {
                info = "no waterfall here now";
            }
            System.out.printf(Locale.ROOT, "%s (site %d,%d drop %d): %s; source-air %d mismatches %d water-over-air %d%n", name, lipX, lipZ, drop, info,
                st.sourceFacingAir, st.mismatches, st.waterAboveAir);
            // the same cameras every run: they depend on the site line only
            double tx = lipX + 0.5 + 1.5 * dirX;
            double tz = lipZ + 0.5 + 1.5 * dirZ;
            double ty = (top + pool) / 2.0;
            double dist = Math.max(34.0, Math.min(90.0, 1.3 * drop + 22.0));
            double px = -dirZ;
            double pz = dirX;
            BufferedImage front = cam(world, tx + dirX * dist, tz + dirZ * dist, tx, ty, tz, pool + 0.5 * drop);
            BufferedImage side = cam(world, tx + dirX * dist * 0.35 + px * dist, tz + dirZ * dist * 0.35 + pz * dist, tx, ty, tz, top + 4.0);
            BufferedImage high = cam(world, tx + dirX * dist * 1.1 - px * dist * 0.6, tz + dirZ * dist * 1.1 - pz * dist * 0.6, tx, pool, tz,
                top + 0.7 * dist);
            BufferedImage viewpoint = FallRender.view(world, v[7] + 0.5, v[8] + 1.62, v[9] + 0.5, tx, ty, tz, 560, 360, 70);
            BufferedImage topView = FallRender.top(world, lipX + 12 * dirX, lipZ + 12 * dirZ, 120, 3);
            BufferedImage cutView = cutMap(r, topView, lipX + 12 * dirX, lipZ + 12 * dirZ, 120, 3);
            ImageIO.write(front, "png", new File(dir, name + "_front.png"));
            ImageIO.write(side, "png", new File(dir, name + "_side.png"));
            ImageIO.write(high, "png", new File(dir, name + "_high.png"));
            ImageIO.write(viewpoint, "png", new File(dir, name + "_viewpoint.png"));
            ImageIO.write(topView, "png", new File(dir, name + "_top.png"));
            ImageIO.write(cutView, "png", new File(dir, name + "_cut.png"));
            BufferedImage sheet = new BufferedImage(560 * 2 + 360, 360 * 2 + 20, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            g.drawImage(front, 0, 20, null);
            g.drawImage(side, 560, 20, null);
            g.drawImage(high, 0, 380, null);
            g.drawImage(viewpoint, 560, 380, null);
            g.drawImage(topView.getScaledInstance(360, 360, java.awt.Image.SCALE_SMOOTH), 1120, 20, null);
            g.drawImage(cutView.getScaledInstance(360, 360, java.awt.Image.SCALE_SMOOTH), 1120, 380, null);
            g.setColor(Color.WHITE);
            g.drawString(name + ": " + info, 4, 14);
            g.dispose();
            ImageIO.write(sheet, "png", new File(dir, name + ".png"));
        }
    }

    /**
     * The solved columns around {@code (x, z)}, one cell per column, heights relative to the
     * centre column's natural ground: {@code w} water (its top), {@code ^} falling water,
     * {@code b} a bank the solver raised (its top, then the raise), {@code .} dry ground, a capital
     * letter where the ground was shaped by a plan.
     */
    private static void dump(TerrainModel model, int x, int z, int radius) {
        WaterStudy.Stats st = new WaterStudy.Stats();
        Region r = solve(model, x - radius, z - radius, x + radius, z + radius, st);
        WaterStudy.Area a = r.area;
        int base = a.natural[a.at(x, z)];
        FallPlan plan = planNear(model, x, z);
        StringBuilder head = new StringBuilder("      ");
        for (int i = -radius; i <= radius; i++) {
            head.append(String.format(Locale.ROOT, "%6d", (x + i) % 1000));
        }
        System.out.println(head);
        for (int j = -radius; j <= radius; j++) {
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%6d", z + j));
            for (int i = -radius; i <= radius; i++) {
                int k = a.at(x + i, z + j);
                String cell;
                if (a.level[k] != WaterSolver.NONE) {
                    // capital: the plan itself puts water here (the rest the solver spread)
                    boolean own = false;
                    if (plan != null) {
                        WaterPlan.Shape sh = new WaterPlan.Shape();
                        sh.reset(a.natural[k]);
                        own = plan.shape(x + i, z + j, a.natural[k], sh) && sh.level != WaterSolver.NONE;
                    }
                    cell = String.format(Locale.ROOT, "%c%c%3d", a.step[k] == WaterSolver.CHUTE ? (own ? 'F' : '^') : own ? 'W' : 'w', (char) ('0' + a.kind[k]), a.level[k] - 1 - base);
                } else if (a.solidTop[k] > a.ground[k]) {
                    cell = String.format(Locale.ROOT, "b%3d%d", a.solidTop[k] - base, Math.min(9, a.solidTop[k] - a.ground[k]));
                } else {
                    cell = String.format(Locale.ROOT, "%c%3d", r.planned[k] ? 'S' : '.', a.solidTop[k] - base);
                }
                sb.append(String.format(Locale.ROOT, "%6s", cell));
            }
            System.out.println(sb);
        }
        System.out.println("centre natural " + base + "; " + st.chunks + " chunks, source-air " + st.sourceFacingAir + ", mismatches " + st.mismatches);
    }

    /** Close views of the plan nearest to {@code (x, z)}, in one 2x2 sheet (see the class comment). */
    private static void shot(TerrainModel model, int x, int z, File out) throws Exception {
        FallPlan p = planNear(model, x, z);
        if (p == null) {
            System.out.println("no waterfall near " + x + "," + z);
            return;
        }
        WaterStudy.Stats st = new WaterStudy.Stats();
        Region r = solve(model, p.minX() - 40, p.minZ() - 40, p.maxX() + 40, p.maxZ() + 40, st);
        int size = Math.max(p.maxX() - p.minX(), p.maxZ() - p.minZ()) + 140;
        World world = new World(model, r, (p.minX() + p.maxX()) / 2, (p.minZ() + p.maxZ()) / 2, size, p);
        Cut c = cut(r, p);
        String info = String.format(Locale.ROOT, "%s lip %d,%d drop %d width %d | %s", p.typeName(), p.lipX, p.lipZ, p.drop(), p.width(), c);
        System.out.println(info + String.format(Locale.ROOT, "; source-air %d mismatches %d water-over-air %d; view %d %d %d", st.sourceFacingAir,
            st.mismatches, st.waterAboveAir, p.viewX, p.viewY, p.viewZ));
        int w = 800;
        int h = 520;
        double tx = p.lookX + 0.5;
        double ty = p.lookY;
        double tz = p.lookZ + 0.5;
        int[] ax = p.flowAxis();
        double dist = Math.max(28.0, Math.min(80.0, 0.9 * p.drop() + 16.0));
        BufferedImage view = FallRender.view(world, p.viewX + 0.5, p.viewY + 1.62, p.viewZ + 0.5, tx, ty, tz, w, h, 70);
        BufferedImage front = camSized(world, tx + ax[0] * dist, tz + ax[1] * dist, tx, ty, tz, ty + 2.0, w, h);
        BufferedImage quarter = camSized(world, tx + ax[0] * dist * 0.7 - ax[1] * dist * 0.7, tz + ax[1] * dist * 0.7 + ax[0] * dist * 0.7, tx, ty,
            tz, ty + 6.0, w, h);
        BufferedImage topView = FallRender.top(world, p.lookX, p.lookZ, 130, 4);
        BufferedImage cutView = cutMap(r, topView, p.lookX, p.lookZ, 130, 4);
        BufferedImage sheet = new BufferedImage(2 * w, 2 * h + 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.drawImage(view, 0, 20, null);
        g.drawImage(front, w, 20, null);
        g.drawImage(quarter, 0, 20 + h, null);
        g.drawImage(cutView.getScaledInstance(h, h, java.awt.Image.SCALE_SMOOTH), w, 20 + h, null);
        g.drawImage(topView.getScaledInstance(w - h, w - h, java.awt.Image.SCALE_SMOOTH), w + h, 20 + h, null);
        g.setColor(Color.WHITE);
        g.drawString(info, 4, 14);
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
    }

    /** {@link #cam} at a given image size. */
    private static BufferedImage camSized(World w, double ex, double ez, double tx, double ty, double tz, double eyeY, int width, int height) {
        int gi = (int) Math.floor(ex) - w.fx0;
        int gj = (int) Math.floor(ez) - w.fz0;
        double ey = eyeY;
        if (gi >= 0 && gj >= 0 && gi < w.fn && gj < w.fn) {
            ey = Math.max(ey, w.field.top(gi, gj) + 3.0);
        }
        for (int lift = 0; lift < 30 && !clear(w, ex, ey, ez, tx, ty, tz); lift++) {
            ey += 3.0;
        }
        return FallRender.view(w, ex, ey, ez, tx, ty, tz, width, height, 70);
    }

    /**
     * The top view with the plan's earthworks over it: red where ground was removed below the
     * model surface (the deeper, the stronger), blue where it was built up.
     */
    private static BufferedImage cutMap(Region r, BufferedImage topView, int cx, int cz, int size, int scale) {
        BufferedImage img = new BufferedImage(topView.getWidth(), topView.getHeight(), BufferedImage.TYPE_INT_RGB);
        int x0 = cx - size / 2;
        int z0 = cz - size / 2;
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int x = x0 + i;
                int z = z0 + j;
                double tint = 0.0;
                int tintRgb = 0;
                if (r.contains(x, z)) {
                    int k = r.at(x, z);
                    int d = r.area.natural[k] - r.area.solidTop[k];
                    if (r.planned[k] && d > 0) {
                        tint = Math.min(0.9, 0.25 + d / 30.0);
                        tintRgb = 0xFF2010;
                    } else if (r.planned[k] && d < 0 && r.area.level[k] == WaterSolver.NONE) {
                        tint = Math.min(0.8, 0.3 - d / 10.0);
                        tintRgb = 0x2040FF;
                    }
                }
                for (int a = 0; a < scale; a++) {
                    for (int b = 0; b < scale; b++) {
                        int base = topView.getRGB(i * scale + a, j * scale + b);
                        int gray = (((base >> 16) & 255) + ((base >> 8) & 255) + (base & 255)) / 3;
                        double rr = gray * (1 - tint) + ((tintRgb >> 16) & 255) * tint;
                        double gg = gray * (1 - tint) + ((tintRgb >> 8) & 255) * tint;
                        double bb = gray * (1 - tint) + (tintRgb & 255) * tint;
                        img.setRGB(i * scale + a, j * scale + b, FallRender.rgb(rr, gg, bb));
                    }
                }
            }
        }
        return img;
    }

    /**
     * A view towards {@code t} from {@code e}, with the eye at {@code eyeY} or higher: a few blocks
     * over the ground there, and raised until the line of sight to the target clears the natural
     * terrain. Only the unshaped 3D terrain decides, so the camera of a site is the same whatever a
     * plan builds there.
     */
    private static BufferedImage cam(World w, double ex, double ez, double tx, double ty, double tz, double eyeY) {
        return camSized(w, ex, ez, tx, ty, tz, eyeY, 560, 360);
    }

    /** Whether the natural terrain leaves the line of sight open up to eight blocks before the target. */
    private static boolean clear(World w, double ex, double ey, double ez, double tx, double ty, double tz) {
        double len = Math.sqrt((tx - ex) * (tx - ex) + (ty - ey) * (ty - ey) + (tz - ez) * (tz - ez));
        for (double d = 1.0; d < len - 8.0; d += 0.5) {
            double f = d / len;
            int i = (int) Math.floor(ex + (tx - ex) * f) - w.fx0;
            int j = (int) Math.floor(ez + (tz - ez) * f) - w.fz0;
            if (i >= 0 && j >= 0 && i < w.fn && j < w.fn && w.field.solid(i, (int) Math.floor(ey + (ty - ey) * f), j)) {
                return false;
            }
        }
        return true;
    }
}
