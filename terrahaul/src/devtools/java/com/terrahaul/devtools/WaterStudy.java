package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import com.terrahaul.worldgen.terrain.WaterBody;
import com.terrahaul.worldgen.water.FallPlan;
import com.terrahaul.worldgen.water.FallPlanner;
import com.terrahaul.worldgen.water.WaterPlan;
import com.terrahaul.worldgen.water.WaterSolver;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Development tool for surface water: runs the game's {@link WaterSolver} on model ground, chunk by
 * chunk exactly as the feature does, and checks what the player would see.
 * <p>
 * Modes:
 * <ul>
 *   <li>{@code solve <seed> <regionsPerKind> <outDir>}: finds regions of each kind (elevated
 *       river, great river, lake, stream, crater), solves 5x5 chunks around each independently
 *       and checks the invariants on the assembled result (no source block facing air or above
 *       air, neighbouring chunks agreeing on every shared decision), prints statistics and writes
 *       top views.</li>
 * </ul>
 */
public final class WaterStudy {
    private static final int AREA_CHUNKS = 8;
    private static final int AREA = AREA_CHUNKS * 16;

    private WaterStudy() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        long seed = Long.parseLong(args[1]);
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        switch (mode) {
            case "solve" -> solve(model, Integer.parseInt(args[2]), new File(args[3]));
            case "map" -> waterMap(model, Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                Integer.parseInt(args[5]), new File(args[6]));
            case "rivers" -> rivers(model, Integer.parseInt(args[2]));
            case "lakes" -> lakes(model, Integer.parseInt(args[2]));
            case "falls" -> falls(model, Integer.parseInt(args[2]), new File(args[3]), args.length > 4 ? Integer.parseInt(args[4]) : 8,
                args.length > 5 ? args[5] : "all");
            case "fallinfo" -> {
                int x = Integer.parseInt(args[2]);
                int z = Integer.parseInt(args[3]);
                FallPlan p = null;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        FallPlan q = FallPlanner.of(model).fall(Math.floorDiv(x, FallPlanner.FALL_CELL) + dx, Math.floorDiv(z, FallPlanner.FALL_CELL) + dz);
                        if (q != null && (p == null || Math.abs(q.lipX - x) + Math.abs(q.lipZ - z) < Math.abs(p.lipX - x) + Math.abs(p.lipZ - z))) {
                            p = q;
                        }
                    }
                }
                if (p == null) {
                    System.out.println("no fall in that cell");
                    return;
                }
                System.out.printf(Locale.ROOT, "%s lip %d,%d dir %d,%d top %d pool %d hw %d bounds %d..%d x %d..%d%n", p.typeName(), p.lipX, p.lipZ,
                    p.dirX, p.dirZ, p.top, p.pool, p.halfWidth, p.minX(), p.maxX(), p.minZ(), p.maxZ());
                WaterPlan.Shape sh = new WaterPlan.Shape();
                for (int v : new int[]{0, 8, 16}) {
                    StringBuilder sb = new StringBuilder("v=" + v + ":");
                    for (int u = -20; u <= 60; u += 2) {
                        int xx = p.lipX + u * p.dirX - v * p.dirZ;
                        int zz = p.lipZ + u * p.dirZ + v * p.dirX;
                        int g = (int) Math.ceil(model.interpolatedHeight(xx, zz)) - 1;
                        sh.reset(g);
                        boolean c = p.shape(xx, zz, g, sh);
                        sb.append(String.format(Locale.ROOT, " %d:%d%s", u, g - p.pool, c ? "/" + (sh.ground - p.pool) : ""));
                    }
                    System.out.println(sb);
                }
            }
            case "canyonfalls" -> {
                int half = Integer.parseInt(args[2]);
                ColumnSample s = new ColumnSample();
                ColumnSample o = new ColumnSample();
                int found = 0;
                for (int x = -half; x <= half && found < 10; x += 16) {
                    for (int z = -half; z <= half && found < 10; z += 16) {
                        model.sampleInto(x, z, s);
                        if (!(s.bodyInside && s.bodyKind == WaterBody.RIVER && s.canyon > 0.3)) {
                            continue;
                        }
                        for (int a = 0; a < 8; a++) {
                            model.sampleInto(x + (int) (Math.cos(a * Math.PI / 4) * 40), z + (int) (Math.sin(a * Math.PI / 4) * 40), o);
                            if (o.bodyInside && o.bodyKind == WaterBody.RIVER && o.bodyLevel >= s.bodyLevel + 4) {
                                System.out.printf(Locale.ROOT, "canyon entry near %d,%d: canyon level %.0f, outside %.0f%n", x, z, s.bodyLevel, o.bodyLevel);
                                found++;
                                x += 400;
                                break;
                            }
                        }
                    }
                }
            }
            case "dump" -> dump(model, Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
            case "corners" -> {
                int x0 = Integer.parseInt(args[2]) & ~3;
                int z0 = Integer.parseInt(args[3]) & ~3;
                int n = Integer.parseInt(args[4]);
                ColumnSample s = new ColumnSample();
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n; i++) {
                        model.sampleInto(x0 + 4 * i, z0 + 4 * j, s);
                        System.out.printf(Locale.ROOT, "%d,%d h=%.2f kind=%d in=%s lvl=%.0f cont=%.2f lake=%.2f(%.0f) river=%.2f rlev=%.0f rw=%.1f dry=%s great=%.2f%n",
                            x0 + 4 * i, z0 + 4 * j, s.height, s.bodyKind, s.bodyInside, s.bodyLevel, s.bodyLevelCont, s.lake, s.lakeLevel, s.river,
                            s.riverLevel, s.riverWidth, s.dryRiver, s.riverGreat);
                    }
                }
            }
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    // =========================================================================================
    // solve
    // =========================================================================================

    private static final String[] KINDS = {"river", "great_river", "lake", "stream", "crater", "canyon_river", "dry_river"};

    /** Finds up to {@code perKind} well separated regions of each kind on a coarse grid around the origin. */
    private static List<int[]> findRegions(TerrainModel model, int perKind) {
        List<int[]> regions = new ArrayList<>();
        int[] found = new int[KINDS.length];
        ColumnSample s = new ColumnSample();
        ColumnSample o = new ColumnSample();
        int sl = model.seaLevel();
        for (int r = 0; r < 260; r++) {
            for (int a = -r; a <= r; a++) {
                for (int b = -r; b <= r; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) != r) {
                        continue;
                    }
                    int x = a * 64;
                    int z = b * 64;
                    model.sampleInto(x, z, s);
                    int kind = -1;
                    if (s.bodyInside && s.bodyKind == WaterBody.RIVER && s.riverGreat > 0.6 && s.river < 0.25) {
                        kind = 1;
                    } else if (s.bodyInside && s.bodyKind == WaterBody.RIVER && s.canyon > 0.2) {
                        kind = 5;
                    } else if (s.bodyInside && s.bodyKind == WaterBody.RIVER && s.bodyLevel > sl + 1) {
                        kind = 0;
                    } else if (s.river > 0.3 && s.dryRiver) {
                        kind = 6;
                    } else if (s.bodyInside && s.bodyKind == WaterBody.LAKE && s.bodyLevel > sl) {
                        // a lake's shore: some column 24 blocks away must be outside the lake
                        for (int d = 0; d < 4 && kind < 0; d++) {
                            model.sampleInto(x + (d == 0 ? 24 : d == 1 ? -24 : 0), z + (d == 2 ? 24 : d == 3 ? -24 : 0), o);
                            if (!o.bodyInside) {
                                kind = 2;
                            }
                        }
                    } else if (s.stream > 0.3 && s.height > sl + 4) {
                        kind = 3;
                    } else if (s.crater > 0.0 && s.bodyInside && s.crater < 0.5) {
                        kind = 4;
                    }
                    if (kind < 0 || found[kind] >= perKind) {
                        continue;
                    }
                    boolean far = true;
                    for (int[] reg : regions) {
                        if (Math.abs(reg[0] - x) < 1500 && Math.abs(reg[1] - z) < 1500) {
                            far = false;
                        }
                    }
                    if (far) {
                        regions.add(new int[]{x, z, kind});
                        found[kind]++;
                    }
                }
            }
            boolean done = true;
            for (int f : found) {
                done &= f >= perKind;
            }
            if (done) {
                break;
            }
        }
        return regions;
    }

    /** Assembled result of independent chunk solves over an area of whole chunks. */
    static final class Area {
        final int ax;
        final int az;
        final int w;
        final int h;
        final int[] ground;
        final int[] natural;
        final int[] level;
        final int[] upper;
        final int[] solidTop;
        final byte[] step;
        final byte[] kind;
        final boolean[] boulder;

        Area(int ax, int az, int w, int h) {
            this.ax = ax;
            this.az = az;
            this.w = w;
            this.h = h;
            int n = w * h;
            ground = new int[n];
            natural = new int[n];
            level = new int[n];
            upper = new int[n];
            solidTop = new int[n];
            step = new byte[n];
            kind = new byte[n];
            boulder = new boolean[n];
        }

        boolean contains(int x, int z) {
            return x >= ax && z >= az && x < ax + w && z < az + h;
        }

        int at(int x, int z) {
            return (z - az) * w + (x - ax);
        }

        boolean source(int k, int y) {
            return level[k] != WaterSolver.NONE && step[k] != WaterSolver.SILL && y > ground[k] && y < level[k];
        }

        boolean chute(int k, int y) {
            return step[k] == WaterSolver.CHUTE && y >= level[k] && y < upper[k];
        }

        boolean solid(int k, int y) {
            return y <= solidTop[k] || boulder[k] && y == solidTop[k] + 1;
        }
    }

    static final class Stats {
        long chunks;
        long nanos;
        long mismatches;
        long sourceFacingAir;
        long waterAboveAir;
        long waterColumns;
        long sills;
        long chutes;
        long streamColumns;
        long streamSills;
        final long[] bankHist = new long[8];
        final long[] stepHist = new long[8];
        final List<String> examples = new ArrayList<>();

        void example(String s) {
            if (examples.size() < 12) {
                examples.add(s);
            }
        }
    }

    /** Solves one chunk exactly as the feature does: with the plans near it. */
    static WaterSolver solveChunk(TerrainModel model, int cx, int cz) {
        List<WaterPlan> plans = new ArrayList<>();
        int m = WaterSolver.MARGIN;
        FallPlanner.plansNear(model, cx * 16 - m, cz * 16 - m, cx * 16 + 15 + m, cz * 16 + 15 + m, plans);
        WaterSolver ws = new WaterSolver();
        ws.solve(model, cx, cz, plans);
        return ws;
    }

    /**
     * Solves {@code nx * nz} chunks independently, assembles their own columns and checks that
     * every chunk's view of its neighbours' border columns matches their own result.
     */
    static Area solveArea(TerrainModel model, int ccx, int ccz, int nx, int nz, Stats st) {
        Area area = new Area(ccx * 16, ccz * 16, nx * 16, nz * 16);
        WaterSolver[][] solves = new WaterSolver[nx][nz];
        for (int a = 0; a < nx; a++) {
            for (int b = 0; b < nz; b++) {
                long t0 = System.nanoTime();
                WaterSolver ws = solveChunk(model, ccx + a, ccz + b);
                st.nanos += System.nanoTime() - t0;
                st.chunks++;
                solves[a][b] = ws;
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        int k = WaterSolver.index(i + WaterSolver.MARGIN, j + WaterSolver.MARGIN);
                        int t = area.at((ccx + a) * 16 + i, (ccz + b) * 16 + j);
                        area.ground[t] = ws.ground[k];
                        area.natural[t] = ws.natural[k];
                        area.level[t] = ws.level[k];
                        area.upper[t] = ws.upper[k];
                        area.solidTop[t] = ws.solidTop[k];
                        area.step[t] = ws.step[k];
                        area.kind[t] = ws.kind[k];
                        area.boulder[t] = ws.boulder[k];
                    }
                }
            }
        }
        for (int a = 0; a < nx; a++) {
            for (int b = 0; b < nz; b++) {
                WaterSolver ws = solves[a][b];
                for (int i = -2; i < 18; i++) {
                    for (int j = -2; j < 18; j++) {
                        if (i >= 0 && i < 16 && j >= 0 && j < 16) {
                            continue;
                        }
                        int x = (ccx + a) * 16 + i;
                        int z = (ccz + b) * 16 + j;
                        if (!area.contains(x, z)) {
                            continue;
                        }
                        int k = ws.indexOf(x, z);
                        int t = area.at(x, z);
                        if (ws.level[k] != area.level[t] || ws.step[k] != area.step[t] || ws.solidTop[k] != area.solidTop[t]
                            || ws.ground[k] != area.ground[t] || ws.upper[k] != area.upper[t] && ws.step[k] != WaterSolver.FLAT) {
                            st.mismatches++;
                            st.example(String.format(Locale.ROOT, "mismatch at %d,%d: level %d/%d step %d/%d top %d/%d",
                                x, z, ws.level[k], area.level[t], ws.step[k], area.step[t], ws.solidTop[k], area.solidTop[t]));
                        }
                    }
                }
            }
        }
        check(area, st);
        return area;
    }

    private static void solve(TerrainModel model, int perKind, File dir) throws Exception {
        dir.mkdirs();
        List<int[]> regions = findRegions(model, perKind);
        Stats total = new Stats();
        List<BufferedImage> tiles = new ArrayList<>();
        for (int[] reg : regions) {
            Stats st = new Stats();
            int ccx = Math.floorDiv(reg[0], 16) - AREA_CHUNKS / 2;
            int ccz = Math.floorDiv(reg[1], 16) - AREA_CHUNKS / 2;
            Area area = solveArea(model, ccx, ccz, AREA_CHUNKS, AREA_CHUNKS, st);
            accumulate(total, st);
            System.out.printf(Locale.ROOT, "%-12s at %7d %7d: %4d water cols, %3d sills, %3d chutes, banks %s, steps %s, mismatches %d, "
                    + "source-air %d, water-above-air %d, %.2f ms/chunk%n",
                KINDS[reg[2]], reg[0], reg[1], st.waterColumns, st.sills, st.chutes, hist(st.bankHist), hist(st.stepHist), st.mismatches,
                st.sourceFacingAir, st.waterAboveAir, st.nanos / 1.0e6 / st.chunks);
            for (String e : st.examples) {
                System.out.println("    " + e);
            }
            tiles.add(render(area, model.seaLevel(), KINDS[reg[2]]));
        }
        System.out.printf(Locale.ROOT, "TOTAL %d regions, %d chunks: %d water cols, %d sills, %d chutes, stream cols %d (sills %d), "
                + "banks %s, steps %s, mismatches %d, source-air %d, water-above-air %d, %.2f ms/chunk%n",
            regions.size(), total.chunks, total.waterColumns, total.sills, total.chutes, total.streamColumns, total.streamSills,
            hist(total.bankHist), hist(total.stepHist), total.mismatches, total.sourceFacingAir, total.waterAboveAir,
            total.nanos / 1.0e6 / Math.max(1, total.chunks));
        sheet(tiles, new File(dir, "solve_" + model.seed() + ".png"));
    }

    static void accumulate(Stats total, Stats st) {
        total.chunks += st.chunks;
        total.nanos += st.nanos;
        total.mismatches += st.mismatches;
        total.sourceFacingAir += st.sourceFacingAir;
        total.waterAboveAir += st.waterAboveAir;
        total.waterColumns += st.waterColumns;
        total.sills += st.sills;
        total.chutes += st.chutes;
        total.streamColumns += st.streamColumns;
        total.streamSills += st.streamSills;
        for (int i = 0; i < 8; i++) {
            total.bankHist[i] += st.bankHist[i];
            total.stepHist[i] += st.stepHist[i];
        }
        for (String e : st.examples) {
            total.example(e);
        }
    }

    static String hist(long[] h) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 1; i < h.length; i++) {
            sb.append(i == 1 ? "" : " ").append(i == h.length - 1 ? i + "+" : i).append(':').append(h[i]);
        }
        return sb.append(']').toString();
    }

    /** Invariants on the interior of an assembled area (its outermost ring has unknown neighbours). */
    static void check(Area area, Stats st) {
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        for (int z = area.az + 1; z < area.az + area.h - 1; z++) {
            for (int x = area.ax + 1; x < area.ax + area.w - 1; x++) {
                int k = area.at(x, z);
                if (area.kind[k] == WaterSolver.STREAM && area.level[k] != WaterSolver.NONE) {
                    st.streamColumns++;
                    if (area.step[k] == WaterSolver.SILL) {
                        st.streamSills++;
                    }
                }
                if (area.step[k] == WaterSolver.SILL) {
                    st.sills++;
                } else if (area.step[k] == WaterSolver.CHUTE) {
                    st.chutes++;
                }
                if (area.step[k] != WaterSolver.FLAT) {
                    st.stepHist[Math.min(7, area.upper[k] - area.level[k])]++;
                }
                if (area.level[k] == WaterSolver.NONE || area.step[k] == WaterSolver.SILL) {
                    int raise = area.solidTop[k] - (area.step[k] == WaterSolver.SILL ? area.upper[k] - 1 : area.ground[k]);
                    if (raise > 0) {
                        st.bankHist[Math.min(7, raise)]++;
                        if (raise > 3) {
                            st.example(String.format(Locale.ROOT, "bank of %d at %d,%d", raise, x, z));
                        }
                    }
                    continue;
                }
                st.waterColumns++;
                if (area.ground[k] + 1 > area.level[k] - 1) {
                    st.waterAboveAir++;
                    st.example("empty water column at " + x + "," + z);
                }
                for (int y = area.ground[k] + 1; y < area.level[k]; y++) {
                    for (int d = 0; d < 4; d++) {
                        int n = area.at(x + dx[d], z + dz[d]);
                        if (!area.solid(n, y) && !area.source(n, y) && !area.chute(n, y)) {
                            st.sourceFacingAir++;
                            st.example(String.format(Locale.ROOT, "source at %d,%d,%d (kind %d level %d) faces air at %d,%d (kind %d level %d top %d step %d)",
                                x, y, z, area.kind[k], area.level[k], x + dx[d], z + dz[d], area.kind[n], area.level[n], area.solidTop[n], area.step[n]));
                        }
                    }
                }
            }
        }
    }
    // =========================================================================================
    // falls and cascades
    // =========================================================================================

    /**
     * Waterfalls and cascades in {@code [-half, half]^2}: frequency per km^2 by climate, sizes,
     * planning cost, and the built result of every plan checked on the solved world: the curtain
     * falls freely from the lip into the pool, the channel holds water up to the lip, nothing
     * leaks; side and top views of the first {@code renders} plans.
     */
    private static void falls(TerrainModel model, int half, File dir, int renders, String which) throws Exception {
        dir.mkdirs();
        FallPlanner planner = FallPlanner.of(model);
        int sl = model.seaLevel();
        String[] classes = {"arid", "dry", "moderate", "humid"};
        long[] landCells = new long[4];
        long[] fallCount = new long[4];
        long[] cascadeCount = new long[4];
        long[] dropHist = new long[6];
        long[] widthHist = new long[5];
        long[] types = new long[4];
        long planNanos = 0;
        int cells = 0;
        List<FallPlan> plans = new ArrayList<>();
        ColumnSample s = new ColumnSample();
        for (boolean cascade : new boolean[]{false, true}) {
            int cell = cascade ? FallPlanner.CASCADE_CELL : FallPlanner.FALL_CELL;
            int n = half / cell;
            for (int cx = -n; cx < n; cx++) {
                for (int cz = -n; cz < n; cz++) {
                    model.sampleInto(cx * cell + cell / 2, cz * cell + cell / 2, s);
                    int c = s.humidity < -0.25 ? 0 : s.humidity < 0.1 ? 1 : s.humidity < 0.4 ? 2 : 3;
                    boolean land = s.height > sl + 2;
                    if (land && !cascade) {
                        landCells[c]++;
                    }
                    long t0 = System.nanoTime();
                    FallPlan p = cascade ? planner.cascade(cx, cz) : planner.fall(cx, cz);
                    planNanos += System.nanoTime() - t0;
                    cells++;
                    if (p == null) {
                        continue;
                    }
                    plans.add(p);
                    types[p.type]++;
                    if (cascade) {
                        cascadeCount[c]++;
                    } else {
                        fallCount[c]++;
                        int d = p.drop();
                        dropHist[d < 16 ? 0 : d < 31 ? 1 : d < 51 ? 2 : d < 91 ? 3 : 4]++;
                        int w = p.width();
                        widthHist[w == 1 ? 0 : w <= 5 ? 1 : w <= 11 ? 2 : w <= 17 ? 3 : 4]++;
                    }
                }
            }
        }
        double cellKm2 = FallPlanner.FALL_CELL * FallPlanner.FALL_CELL / 1.0e6;
        for (int c = 0; c < 4; c++) {
            System.out.printf(Locale.ROOT, "%-9s land cells %5d: waterfalls %4d (%.2f per km2), cascades %4d (%.2f per km2)%n", classes[c], landCells[c],
                fallCount[c], fallCount[c] / Math.max(1e-9, landCells[c] * cellKm2), cascadeCount[c], cascadeCount[c] / Math.max(1e-9, landCells[c] * cellKm2));
        }
        System.out.printf(Locale.ROOT, "types: curtain %d, gorge %d, into water %d, cascade %d; drops 8-15:%d 16-30:%d 31-50:%d 51-90:%d 91+:%d; "
                + "widths 1:%d 3-5:%d 7-11:%d 13-17:%d 19+:%d; planning %.1f ms per cell%n",
            types[0], types[1], types[2], types[3], dropHist[0], dropHist[1], dropHist[2], dropHist[3], dropHist[4],
            widthHist[0], widthHist[1], widthHist[2], widthHist[3], widthHist[4], planNanos / 1e6 / cells);
        // build every plan on the solved world and check it
        Stats total = new Stats();
        long curtainOk = 0;
        long curtainBad = 0;
        long frontOk = 0;
        long frontBad = 0;
        long channelBad = 0;
        long cascadeSteps = 0;
        List<BufferedImage> tiles = new ArrayList<>();
        int rendered = 0;
        for (FallPlan p : plans) {
            Stats st = new Stats();
            int ccx = Math.floorDiv(p.minX(), 16) - 1;
            int ccz = Math.floorDiv(p.minZ(), 16) - 1;
            int nx = Math.floorDiv(p.maxX(), 16) + 2 - ccx;
            int nz = Math.floorDiv(p.maxZ(), 16) + 2 - ccz;
            Area area = solveArea(model, ccx, ccz, nx, nz, st);
            accumulate(total, st);
            if (p.type == FallPlan.CASCADE) {
                for (int t = 0; t < area.w * area.h; t++) {
                    if (area.step[t] != WaterSolver.FLAT && area.kind[t] == WaterSolver.PLAN) {
                        cascadeSteps++;
                    }
                }
            } else {
                for (int v = -p.halfWidth; v <= p.halfWidth; v++) {
                    int x1 = p.lipX + p.dirX - v * p.dirZ;
                    int z1 = p.lipZ + p.dirZ + v * p.dirX;
                    int x0 = p.lipX - v * p.dirZ;
                    int z0 = p.lipZ + v * p.dirX;
                    int x2 = x1 + p.dirX;
                    int z2 = z1 + p.dirZ;
                    int k1 = area.at(x1, z1);
                    int k0 = area.at(x0, z0);
                    int k2 = area.at(x2, z2);
                    if (area.level[k0] != p.top + 1) {
                        channelBad++;
                        total.example(String.format(Locale.ROOT, "channel at %d,%d has level %d, expected %d", x0, z0, area.level[k0], p.top + 1));
                    }
                    if (area.step[k1] == WaterSolver.SILL) {
                        continue;
                    }
                    if (area.step[k1] == WaterSolver.CHUTE && area.upper[k1] == p.top + 1) {
                        curtainOk++;
                    } else {
                        curtainBad++;
                        total.example(String.format(Locale.ROOT, "curtain column %d,%d of fall at %d,%d: step %d upper %d level %d",
                            x1, z1, p.lipX, p.lipZ, area.step[k1], area.upper[k1], area.level[k1]));
                    }
                    // in front of the curtain the space must be open from the pool up to the lip
                    if (area.solidTop[k2] <= Math.max(p.pool + 1, area.level[k2] - 1) && area.step[k2] != WaterSolver.CHUTE) {
                        frontOk++;
                    } else {
                        frontBad++;
                    }
                }
            }
            if (rendered < renders && (which.equals("all") || which.equals("cascade") == (p.type == FallPlan.CASCADE))) {
                rendered++;
                System.out.printf(Locale.ROOT, "  %s at %d,%d (dir %d,%d): drop %d (top %d pool %d), width %d, view %d,%d,%d%n", p.typeName(),
                    p.lipX, p.lipZ, p.dirX, p.dirZ, p.drop(), p.top, p.pool, p.width(), p.viewX, p.viewY, p.viewZ);
                tiles.add(fallTile(model, area, p, sl));
            }
        }
        System.out.printf(Locale.ROOT, "built %d plans in %d chunks (%.2f ms/chunk): mismatches %d, source-air %d, water-above-air %d, banks %s, steps %s%n",
            plans.size(), total.chunks, total.nanos / 1e6 / Math.max(1, total.chunks), total.mismatches, total.sourceFacingAir, total.waterAboveAir,
            hist(total.bankHist), hist(total.stepHist));
        System.out.printf(Locale.ROOT, "curtain columns falling from the lip: %d ok, %d not; open in front: %d ok, %d not; channel columns off level: %d; "
            + "cascade step columns %d%n", curtainOk, curtainBad, frontOk, frontBad, channelBad, cascadeSteps);
        for (String e : total.examples) {
            System.out.println("    " + e);
        }
        sheet(tiles, new File(dir, "falls_" + model.seed() + ".png"));
    }

    /** Side view along the fall's axis (top) and top view (bottom) of a solved plan. */
    private static BufferedImage fallTile(TerrainModel model, Area area, FallPlan p, int sl) {
        BufferedImage top = render(area, sl, p.typeName() + " " + p.lipX + "," + p.lipZ);
        int sc = 4;
        int uMin = -24;
        int uMax = 64;
        int yMin = p.pool - 12;
        int yMax = p.top + 16;
        BufferedImage view = WaterView.render(model, area, p.viewX + 0.5, p.viewY + 1.62, p.viewZ + 0.5, p.lookX + 0.5, p.lookY + 0.5, p.lookZ + 0.5, 480, 320);
        int sideH = Math.max((yMax - yMin) * sc, view.getHeight());
        int w = Math.max(top.getWidth(), (uMax - uMin) * sc + view.getWidth());
        BufferedImage img = new BufferedImage(w, top.getHeight() + sideH, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(230, 230, 230));
        g.fillRect(0, 0, w, sideH);
        g.drawImage(view, (uMax - uMin) * sc, 0, null);
        boolean cascade = p.type == FallPlan.CASCADE;
        int dx = p.flowAxis()[0];
        int dz = p.flowAxis()[1];
        int ox = cascade ? p.lookX : p.lipX;
        int oz = cascade ? p.lookZ : p.lipZ;
        for (int u = uMin; u < uMax; u++) {
            int x = ox + u * dx;
            int z = oz + u * dz;
            if (!area.contains(x, z)) {
                continue;
            }
            int k = area.at(x, z);
            for (int y = yMin; y < yMax; y++) {
                int rgb;
                if (area.solid(k, y)) {
                    rgb = y > area.ground[k] ? 0x8C8C8C : y > area.natural[k] ? 0xA0522D : 0x6B5A3A;
                } else if (area.source(k, y)) {
                    rgb = 0x2050D0;
                } else if (area.chute(k, y)) {
                    rgb = 0x00E5FF;
                } else if (y <= area.natural[k]) {
                    rgb = 0xFFD890;
                } else {
                    continue;
                }
                g.setColor(new java.awt.Color(rgb));
                g.fillRect((u - uMin) * sc, (yMax - 1 - y) * sc, sc, sc);
            }
        }
        g.setColor(java.awt.Color.BLACK);
        g.drawString(p.typeName() + " drop " + p.drop() + " width " + p.width(), 4, 12);
        g.drawImage(top, 0, sideH, null);
        g.dispose();
        return img;
    }

    // =========================================================================================
    // lakes
    // =========================================================================================

    /** Lake sites in {@code [-half, half]^2}: how many are active, why the others were rejected, rim raises, cost. */
    private static void lakes(TerrainModel model, int half) {
        java.util.Map<String, Integer> reasons = new java.util.TreeMap<>();
        int active = 0;
        int activeGreat = 0;
        int seaLevelLakes = 0;
        long[] raiseHist = new long[6];
        double maxRaise = 0.0;
        long nanos = 0;
        int computed = 0;
        for (boolean great : new boolean[]{false, true}) {
            double cell = great ? 6000.0 : 700.0;
            int n = (int) Math.ceil(half / cell);
            for (int cx = -n; cx < n; cx++) {
                for (int cz = -n; cz < n; cz++) {
                    long t0 = System.nanoTime();
                    TerrainModel.LakeSite lake = model.lakeSiteRaw(cx, cz, great);
                    nanos += System.nanoTime() - t0;
                    computed++;
                    if (!lake.active) {
                        reasons.merge((great ? "great:" : "") + lake.reject, 1, Integer::sum);
                        continue;
                    }
                    if (great) {
                        activeGreat++;
                    } else {
                        active++;
                    }
                    if (lake.level <= model.seaLevel()) {
                        seaLevelLakes++;
                    }
                    raiseHist[(int) Math.min(5, Math.max(0, Math.ceil(lake.maxRaise)))]++;
                    maxRaise = Math.max(maxRaise, lake.maxRaise);
                    if (great || lake.maxRaise > 2.5) {
                        System.out.printf(Locale.ROOT, "  %s lake at %.0f,%.0f r=%.0f level=%.0f raise=%.1f rays=%d%n", great ? "great" : "small",
                            lake.x, lake.z, lake.radius, lake.level, lake.maxRaise, lake.rays);
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "active lakes: %d small, %d great (%d at sea level); rejected: %s%n", active, activeGreat, seaLevelLakes, reasons);
        System.out.printf(Locale.ROOT, "rim raise between shore and crest: <=0:%d <=1:%d <=2:%d <=3:%d <=4:%d >4:%d (max %.1f)%n",
            raiseHist[0], raiseHist[1], raiseHist[2], raiseHist[3], raiseHist[4], raiseHist[5], maxRaise);
        System.out.printf(Locale.ROOT, "site computation: %d sites, %.1f ms total, %.2f ms average%n", computed, nanos / 1e6, nanos / 1e6 / computed);
    }

    // =========================================================================================
    // dump: the solved columns around a point as text
    // =========================================================================================

    /**
     * Prints the solved columns of a {@code w x h} window centred on {@code (x, z)}: one cell per
     * column, a kind letter (S sea, R river, L lake, C crater, V lava, s stream, P plan, . dry,
     * # sill, ^ chute, b bank) and the water level (or ground) relative to sea level.
     */
    private static void dump(TerrainModel model, int x, int z, int w, int h) {
        int sl = model.seaLevel();
        StringBuilder head = new StringBuilder("      ");
        for (int i = 0; i < w; i++) {
            head.append(String.format(Locale.ROOT, "%4d", (x - w / 2 + i) % 1000));
        }
        System.out.println(head);
        java.util.Map<Long, WaterSolver> solves = new java.util.HashMap<>();
        for (int j = 0; j < h; j++) {
            int zz = z - h / 2 + j;
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%6d", zz));
            for (int i = 0; i < w; i++) {
                int xx = x - w / 2 + i;
                int cx = Math.floorDiv(xx, 16);
                int cz = Math.floorDiv(zz, 16);
                WaterSolver ws = solves.computeIfAbsent(((long) cx << 32) ^ (cz & 0xFFFFFFFFL), key -> {
                    WaterSolver s = new WaterSolver();
                    s.solve(model, cx, cz, List.<WaterPlan>of());
                    return s;
                });
                int k = ws.indexOf(xx, zz);
                char c;
                int v;
                if (ws.step[k] == WaterSolver.SILL) {
                    c = '#';
                    v = ws.upper[k] - sl;
                } else if (ws.level[k] != WaterSolver.NONE) {
                    c = ws.step[k] == WaterSolver.CHUTE ? '^' : "?SRLCVsP".charAt(ws.kind[k]);
                    v = ws.level[k] - sl;
                } else if (ws.solidTop[k] > ws.ground[k]) {
                    c = 'b';
                    v = ws.solidTop[k] - sl;
                } else {
                    c = '.';
                    v = ws.ground[k] - sl;
                }
                sb.append(String.format(Locale.ROOT, " %c%2d", c, Math.max(-9, Math.min(99, v))));
            }
            System.out.println(sb);
        }
    }

    // =========================================================================================
    // map and rivers
    // =========================================================================================

    /** A top view of the model's water: sea, ordinary rivers, great rivers, lakes, craters, dry beds. */
    private static void waterMap(TerrainModel model, int cx, int cz, int size, int bpp, File out) throws Exception {
        int px = size / bpp;
        int minX = cx - size / 2;
        int minZ = cz - size / 2;
        int sl = model.seaLevel();
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        java.util.stream.IntStream.range(0, px).parallel().forEach(j -> {
            ColumnSample s = new ColumnSample();
            ColumnSample w = new ColumnSample();
            for (int i = 0; i < px; i++) {
                int x = minX + i * bpp;
                int z = minZ + j * bpp;
                model.sampleInto(x, z, s);
                model.sampleInto(x - bpp, z - bpp, w);
                double shade = Math.max(0.55, Math.min(1.35, 1.0 + (s.height - w.height) / bpp * 0.35));
                int shadeL = (int) Math.floor(s.bodyLevel) & 1;
                int rgb;
                if (s.bodyInside && s.height < s.bodyLevel - 0.5) {
                    rgb = switch (s.bodyKind) {
                        case WaterBody.LAKE -> s.bodyLevel > sl ? rgb(110, 80 + 20 * shadeL, 230) : rgb(70, 60, 200);
                        case WaterBody.CRATER -> rgb(40, 170, 170);
                        case WaterBody.LAVA -> rgb(255, 110, 0);
                        default -> s.riverGreat > 0.3 ? rgb(40, 200 + 30 * shadeL, 225)
                            : s.bodyLevel > sl ? rgb(40, 90 + 40 * shadeL, 255) : rgb(30, 70, 190);
                    };
                } else if (s.height < sl - 0.5) {
                    double d = Math.min(1.0, (sl - s.height) / 120.0);
                    rgb = rgb((int) (40 - 25 * d), (int) (80 - 50 * d), (int) (175 - 60 * d));
                } else if (s.river > 0.0) {
                    rgb = s.dryRiver ? rgb(215, 180, 110) : rgb(150, 150, 120);
                } else if (s.stream > 0.3) {
                    rgb = rgb(120, 190, 240);
                } else {
                    double t = Math.min(1.0, Math.max(0.0, (s.height - sl) / 260.0));
                    int r = (int) ((60 + 140 * t) * shade);
                    int g = (int) ((130 + 70 * t) * shade);
                    int b = (int) ((60 + 140 * t) * shade);
                    if (s.riverValley > 0.3) {
                        g += 25;
                    }
                    rgb = rgb(r, g, b);
                }
                img.setRGB(i, j, rgb);
            }
        });
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    /** Statistics of rivers on a grid over {@code [-half, half]^2}. */
    private static void rivers(TerrainModel model, int half) {
        int sl = model.seaLevel();
        int step = 32;
        int n = 2 * half / step + 1;
        long[] counts = new long[16];
        long[] widthHist = new long[8];
        int[] widthEdges = {60, 100, 150, 200, 260, 330, 400};
        java.util.concurrent.atomic.AtomicLongArray acc = new java.util.concurrent.atomic.AtomicLongArray(32);
        java.util.List<String> examples = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.stream.IntStream.range(0, n).parallel().forEach(j -> {
            ColumnSample s = new ColumnSample();
            ColumnSample o = new ColumnSample();
            for (int i = 0; i < n; i++) {
                int x = -half + i * step;
                int z = -half + j * step;
                model.sampleInto(x, z, s);
                if (s.river <= 0.0 && !(s.bodyInside && s.bodyKind == WaterBody.RIVER)) {
                    continue;
                }
                boolean great = s.riverGreat > 0.6;
                if (s.river > 0.85) {
                    acc.incrementAndGet(great ? 0 : 1);
                    if (s.bodyInside && s.bodyLevel <= sl) {
                        acc.incrementAndGet(great ? 2 : 3);
                    }
                    if (s.dryRiver) {
                        acc.incrementAndGet(great ? 4 : 5);
                    }
                    if (great && s.bodyInside && s.rawContinentalness > -0.06) {
                        // water width across the river: shortest chord through the sample
                        double best = Double.MAX_VALUE;
                        for (int a = 0; a < 16; a++) {
                            double ang = Math.PI * a / 16.0;
                            double dx = Math.cos(ang);
                            double dz = Math.sin(ang);
                            double chord = 0.0;
                            for (int sign = -1; sign <= 1; sign += 2) {
                                int d = 0;
                                while (d < 600) {
                                    d += 4;
                                    model.sampleInto((int) Math.round(x + sign * dx * d), (int) Math.round(z + sign * dz * d), o);
                                    boolean wet = o.bodyInside && o.height < o.bodyLevel - 0.5 || o.height < sl - 0.5;
                                    if (!wet) {
                                        break;
                                    }
                                }
                                chord += d;
                            }
                            best = Math.min(best, chord);
                        }
                        int b = 0;
                        while (b < widthEdges.length && best >= widthEdges[b]) {
                            b++;
                        }
                        acc.incrementAndGet(8 + b);
                    }
                }
                // perched rivers: elevated water with ground well below it just beyond the banks
                if (s.bodyInside && s.bodyKind == WaterBody.RIVER && s.bodyLevel > sl + 1 && s.river > 0.5) {
                    acc.incrementAndGet(6);
                    double reach = s.riverWidth + 10.0;
                    double low = Double.MAX_VALUE;
                    for (int a = 0; a < 12; a++) {
                        double ang = Math.PI * 2 * a / 12.0;
                        model.sampleInto((int) Math.round(x + Math.cos(ang) * reach), (int) Math.round(z + Math.sin(ang) * reach), o);
                        if (!(o.bodyInside && o.bodyKind == WaterBody.RIVER)) {
                            low = Math.min(low, o.height);
                        }
                    }
                    if (low < s.bodyLevel - 3.0) {
                        acc.incrementAndGet(7);
                        if (examples.size() < 12) {
                            examples.add(String.format(Locale.ROOT, "perched river at %d,%d level %.0f ground nearby %.1f (canyon %.2f swamp %.2f fjord %.2f lake %.2f)",
                                x, z, s.bodyLevel, low, s.canyon, s.swamp, s.fjord, s.lake));
                        }
                    }
                }
                // dry beds next to water
                if (s.dryRiver && s.river > 0.5) {
                    acc.incrementAndGet(16);
                    boolean near = false;
                    for (int a = 0; a < 16 && !near; a++) {
                        double ang = Math.PI * 2 * a / 16.0;
                        for (int r = 8; r <= 32; r += 8) {
                            model.sampleInto((int) Math.round(x + Math.cos(ang) * r), (int) Math.round(z + Math.sin(ang) * r), o);
                            if (o.bodyInside && o.bodyKind == WaterBody.RIVER && o.height < o.bodyLevel - 0.5) {
                                near = true;
                                break;
                            }
                        }
                    }
                    if (near) {
                        acc.incrementAndGet(17);
                    }
                }
            }
        });
        for (int b = 0; b < 8; b++) {
            widthHist[b] = acc.get(8 + b);
        }
        System.out.printf(Locale.ROOT, "centre samples: great %d (sea level %d, dry %d), normal %d (sea level %d, dry %d)%n",
            acc.get(0), acc.get(2), acc.get(4), acc.get(1), acc.get(3), acc.get(5));
        StringBuilder sb = new StringBuilder("great river water width:");
        for (int b = 0; b < 8; b++) {
            sb.append(' ').append(b == 0 ? "<" + widthEdges[0] : b == 7 ? ">=" + widthEdges[6] : widthEdges[b - 1] + "-" + widthEdges[b]).append(':').append(widthHist[b]);
        }
        System.out.println(sb);
        System.out.printf(Locale.ROOT, "elevated channel samples %d, perched (ground >3 below the water beyond the banks) %d%n", acc.get(6), acc.get(7));
        System.out.printf(Locale.ROOT, "dry bed samples %d, with water within 32 blocks %d%n", acc.get(16), acc.get(17));
        for (String e : examples) {
            System.out.println("  " + e);
        }
    }

    // =========================================================================================
    // images
    // =========================================================================================

    private static BufferedImage render(Area area, int sl, String label) {
        int sc = 3;
        BufferedImage img = new BufferedImage(area.w * sc, area.h * sc, BufferedImage.TYPE_INT_RGB);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int t = 0; t < area.w * area.h; t++) {
            min = Math.min(min, area.solidTop[t]);
            max = Math.max(max, area.solidTop[t]);
        }
        for (int z = 0; z < area.h; z++) {
            for (int x = 0; x < area.w; x++) {
                int k = z * area.w + x;
                int rgb;
                double t = max == min ? 0.5 : (area.solidTop[k] - min) / (double) (max - min);
                int lv = area.level[k];
                if (area.step[k] == WaterSolver.SILL) {
                    rgb = area.boulder[k] ? 0x505050 : 0x8C8C8C;
                } else if (area.step[k] == WaterSolver.CHUTE) {
                    rgb = 0xE8F4FF;
                } else if (lv != WaterSolver.NONE) {
                    int shade = (lv & 1) == 0 ? 0 : 30;
                    rgb = switch (area.kind[k]) {
                        case WaterSolver.SEA -> 0x1F4FA0;
                        case WaterSolver.STREAM -> rgb(40 + shade, 150 + shade, 230);
                        case WaterSolver.LAVA -> 0xFF6A00;
                        case WaterSolver.LAKE, WaterSolver.CRATER -> rgb(30 + shade, 90 + shade, 200);
                        default -> rgb(30 + shade, 110 + shade, 215);
                    };
                } else if (area.solidTop[k] > area.ground[k]) {
                    rgb = 0xA0522D;
                } else {
                    int g = (int) (70 + 150 * t);
                    int stripe = (area.solidTop[k] & 1) == 0 ? 0 : 12;
                    rgb = rgb(g / 3 + stripe, g, g / 4);
                }
                for (int i = 0; i < sc; i++) {
                    for (int j = 0; j < sc; j++) {
                        boolean grid = x % 16 == 0 && i == 0 || z % 16 == 0 && j == 0;
                        img.setRGB(x * sc + i, z * sc + j, grid ? 0x602020 : rgb);
                    }
                }
            }
        }
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.drawString(label + " " + (area.ax + area.w / 2) + "," + (area.az + area.h / 2), 4, 12);
        g.dispose();
        return img;
    }

    static int rgb(int r, int g, int b) {
        return (Math.min(255, Math.max(0, r)) << 16) | (Math.min(255, Math.max(0, g)) << 8) | Math.min(255, Math.max(0, b));
    }

    private static void sheet(List<BufferedImage> tiles, File out) throws Exception {
        if (tiles.isEmpty()) {
            return;
        }
        int cols = Math.min(4, tiles.size());
        int rows = (tiles.size() + cols - 1) / cols;
        int w = tiles.get(0).getWidth();
        int h = tiles.get(0).getHeight();
        BufferedImage img = new BufferedImage(cols * w, rows * h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        for (int i = 0; i < tiles.size(); i++) {
            g.drawImage(tiles.get(i), (i % cols) * w, (i / cols) * h, null);
        }
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }
}
