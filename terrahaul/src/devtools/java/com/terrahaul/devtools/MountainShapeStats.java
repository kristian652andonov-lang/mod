package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;

/**
 * Development tool: shape statistics of the mountains in a square area, the numbers behind "too
 * rounded" and "steep with small ledges":
 * <ul>
 *   <li><b>slopes</b> of mountain columns over an 8-block baseline (as {@link MapRenderer} measures
 *       them), for all of them and for the flanks (columns above 30% of the local relief, so flat
 *       valley floors do not count), the flanks also split by their local relief (under 250,
 *       250-600 and over 600 blocks: foothills and low ranges, ordinary ranges, tall ones);</li>
 *   <li><b>benches</b>: flank columns that are nearly level over 4 blocks (under 27 degrees) while
 *       the ground 16 blocks around them is steep (over 45 degrees on average) - ledges and
 *       shoulders. Their <b>continuity</b> is the share of benches whose neighbours 20 blocks to
 *       both sides along the contour are benches too: continuous contour terraces (rice terraces,
 *       ziggurats) score high, small scattered ledges low;</li>
 *   <li><b>roundness</b> of crests (columns above 70% of the local relief that stand above the
 *       ground 16 blocks around them): {@code (D4 / 4) / (D16 / 16)}, where {@code Dr} is how far
 *       the column stands above the mean of 8 points {@code r} blocks around it. On a cusp (an
 *       arête, a pointed summit) the ground falls linearly and the ratio is about 1; on a dome it
 *       falls quadratically and the ratio is about 0.25. Crests under 0.45 count as rounded, over
 *       0.7 as cusped. The mean {@code D4 / 4} (blocks per block) is the sharpness at the smallest
 *       scale the game resolves.</li>
 * </ul>
 * The local relief of a column is measured against the lowest and highest points within 320 blocks.
 * Usage: {@code MountainShapeStats <seed> <centerX> <centerZ> <sizeBlocks> [stride=64]}.
 */
public final class MountainShapeStats {
    private static final int COARSE = 32;
    private static final int WINDOW = 10;
    private static final double BENCH_SLOPE = 0.5;
    private static final double CONTEXT_SLOPE = 1.0;

    private MountainShapeStats() {
    }

    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        int cx = Integer.parseInt(args[1]);
        int cz = Integer.parseInt(args[2]);
        int size = Integer.parseInt(args[3]);
        int stride = args.length > 4 ? Integer.parseInt(args[4]) : 64;
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        int n = size / COARSE;
        int minX = cx - size / 2;
        int minZ = cz - size / 2;
        float[] coarse = new float[n * n];
        IntStream.range(0, n).parallel().forEach(j -> {
            ColumnSample s = new ColumnSample();
            for (int i = 0; i < n; i++) {
                model.sampleInto(minX + i * COARSE, minZ + j * COARSE, s);
                coarse[j * n + i] = (float) s.height;
            }
        });
        int step = Math.max(1, stride / COARSE);
        // counters: 0 mountain, 1 >45, 2 >60, 3 >75, 4 flank, 5 flank>45, 6 flank>60, 7 flank>75,
        // 8 steep context, 9 bench, 10 continuous bench, 11 crest, 12 rounded, 13 cusped,
        // 14 sum D4/4 (x1000), 15 sum |laplacian4| (x1000), 16 sum ratio (x1000), 17 flank sum slope deg (x1000)
        // 18.. flanks by local relief (under 250, 250-600, over 600 blocks): count, >45, >60, sum deg (x1000)
        AtomicLongArray c = new AtomicLongArray(30);
        int sl = model.seaLevel();
        IntStream.range(WINDOW, n - WINDOW).filter(j -> (j - WINDOW) % step == 0).parallel().forEach(j -> {
            ColumnSample s = new ColumnSample();
            for (int i = WINDOW; i < n - WINDOW; i += step) {
                int x = minX + i * COARSE;
                int z = minZ + j * COARSE;
                model.sampleInto(x, z, s);
                if (s.mountain <= 0.3 || s.height < sl + 4.0 || s.waterLevel != ColumnSample.NO_WATER || s.river > 0.0 || s.lake > 0.0) {
                    continue;
                }
                double h = s.height;
                double lo = h;
                double hi = h;
                for (int b = -WINDOW; b <= WINDOW; b++) {
                    for (int a = -WINDOW; a <= WINDOW; a++) {
                        if (a * a + b * b <= WINDOW * WINDOW) {
                            double v = Math.max(sl, coarse[(j + b) * n + i + a]);
                            lo = Math.min(lo, v);
                            hi = Math.max(hi, v);
                        }
                    }
                }
                double t = hi - lo > 30.0 ? (h - lo) / (hi - lo) : 0.0;
                double hE = height(model, s, x + 4, z);
                double hW = height(model, s, x - 4, z);
                double hS = height(model, s, x, z + 4);
                double hN = height(model, s, x, z - 4);
                double slope = Math.hypot(hE - hW, hS - hN) / 8.0;
                double deg = Math.toDegrees(Math.atan(slope));
                c.incrementAndGet(0);
                count(c, 1, deg, 45.0, 60.0, 75.0);
                if (t <= 0.3) {
                    continue;
                }
                c.incrementAndGet(4);
                count(c, 5, deg, 45.0, 60.0, 75.0);
                c.addAndGet(17, Math.round(deg * 1000.0));
                int band = 18 + 4 * (hi - lo < 250.0 ? 0 : hi - lo < 600.0 ? 1 : 2);
                c.incrementAndGet(band);
                c.addAndGet(band + 1, deg > 45.0 ? 1 : 0);
                c.addAndGet(band + 2, deg > 60.0 ? 1 : 0);
                c.addAndGet(band + 3, Math.round(deg * 1000.0));
                // ring of 8 points 16 blocks out: the steepness of the surroundings and D16
                double ringSlope = 0.0;
                double gx = 0.0;
                double gz = 0.0;
                for (int k = 0; k < 8; k++) {
                    double ang = k * Math.PI / 4.0;
                    int ox = (int) Math.round(16.0 * Math.cos(ang));
                    int oz = (int) Math.round(16.0 * Math.sin(ang));
                    double v = height(model, s, x + ox, z + oz);
                    gx += v * Math.cos(ang);
                    gz += v * Math.sin(ang);
                    double vx = height(model, s, x + ox + 2, z + oz) - height(model, s, x + ox - 2, z + oz);
                    double vz = height(model, s, x + ox, z + oz + 2) - height(model, s, x + ox, z + oz - 2);
                    ringSlope += Math.hypot(vx, vz) / 4.0;
                }
                ringSlope /= 8.0;
                if (ringSlope > CONTEXT_SLOPE) {
                    c.incrementAndGet(8);
                    double fx = height(model, s, x + 2, z) - height(model, s, x - 2, z);
                    double fz = height(model, s, x, z + 2) - height(model, s, x, z - 2);
                    if (Math.hypot(fx, fz) / 4.0 < BENCH_SLOPE) {
                        c.incrementAndGet(9);
                        double gl = Math.hypot(gx, gz);
                        if (gl > 1.0E-6) {
                            // along the contour of the surroundings
                            double tx = -gz / gl;
                            double tz = gx / gl;
                            if (bench(model, s, x + (int) Math.round(20.0 * tx), z + (int) Math.round(20.0 * tz))
                                && bench(model, s, x - (int) Math.round(20.0 * tx), z - (int) Math.round(20.0 * tz))) {
                                c.incrementAndGet(10);
                            }
                        }
                    }
                }
                if (t > 0.7) {
                    crest(model, s, x, z, c);
                }
            }
        });
        long m = Math.max(1, c.get(0));
        long f = Math.max(1, c.get(4));
        long ctx = Math.max(1, c.get(8));
        long bench = Math.max(1, c.get(9));
        long crest = Math.max(1, c.get(11));
        System.out.printf(Locale.ROOT, "mountain columns %d: >45deg %.1f%% >60deg %.1f%% >75deg %.2f%%%n",
            c.get(0), 100.0 * c.get(1) / m, 100.0 * c.get(2) / m, 100.0 * c.get(3) / m);
        System.out.printf(Locale.ROOT, "flanks (above 30%% of local relief) %d: mean %.1fdeg >45deg %.1f%% >60deg %.1f%% >75deg %.2f%%%n",
            c.get(4), c.get(17) / 1000.0 / f, 100.0 * c.get(5) / f, 100.0 * c.get(6) / f, 100.0 * c.get(7) / f);
        String[] bands = {"relief <250", "relief 250-600", "relief >600"};
        StringBuilder sb = new StringBuilder("flanks by local relief:");
        for (int b = 0; b < 3; b++) {
            long nb = Math.max(1, c.get(18 + 4 * b));
            sb.append(String.format(Locale.ROOT, " [%s: %d, mean %.1fdeg >45 %.1f%% >60 %.1f%%]", bands[b], c.get(18 + 4 * b),
                c.get(21 + 4 * b) / 1000.0 / nb, 100.0 * c.get(19 + 4 * b) / nb, 100.0 * c.get(20 + 4 * b) / nb));
        }
        System.out.println(sb);
        System.out.printf(Locale.ROOT, "steep surroundings %d (%.1f%% of flanks): benches %.2f%% of them, continuity %.1f%% of benches%n",
            c.get(8), 100.0 * c.get(8) / f, 100.0 * c.get(9) / ctx, 100.0 * c.get(10) / bench);
        System.out.printf(Locale.ROOT, "crests %d: rounded %.1f%% cusped %.1f%% mean ratio %.2f, mean slope beside the crest line %.1fdeg, mean |lap4| %.3f%n",
            c.get(11), 100.0 * c.get(12) / crest, 100.0 * c.get(13) / crest, c.get(16) / 1000.0 / crest,
            c.get(14) / 1000.0 / crest, c.get(15) / 1000.0 / crest);
    }

    /**
     * Crest roundness at a column high in its relief: of four directions the one across which the
     * ground is most convex over 8 blocks is taken as across the crest; the column is moved onto
     * the highest point within 12 blocks along it (onto the crest line; if the ground still rises
     * 12 blocks away, it is a slope and is skipped), and the crest's cross profile is measured there: {@code Dr = h - (h(+r) + h(-r)) / 2} for r = 4 and 16.
     */
    private static void crest(TerrainModel model, ColumnSample s, int x, int z, AtomicLongArray c) {
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        double h = height(model, s, x, z);
        int best = -1;
        double bestC = 2.0;
        for (int k = 0; k < 4; k++) {
            double cv = h - 0.5 * (height(model, s, x + 8 * dirs[k][0], z + 8 * dirs[k][1]) + height(model, s, x - 8 * dirs[k][0], z - 8 * dirs[k][1]));
            if (cv > bestC) {
                bestC = cv;
                best = k;
            }
        }
        if (best < 0) {
            return;
        }
        int dx = dirs[best][0];
        int dz = dirs[best][1];
        int px = x;
        int pz = z;
        double top = h;
        int at = 0;
        for (int o = -12; o <= 12; o++) {
            double v = height(model, s, x + o * dx, z + o * dz);
            if (v > top) {
                top = v;
                at = o;
            }
        }
        if (Math.abs(at) == 12) {
            // the ground still rises at the end of the search: a slope, not a crest
            return;
        }
        px = x + at * dx;
        pz = z + at * dz;
        double len = Math.hypot(dx, dz);
        double d4 = top - 0.5 * (height(model, s, px + 4 * dx, pz + 4 * dz) + height(model, s, px - 4 * dx, pz - 4 * dz));
        double d16 = top - 0.5 * (height(model, s, px + 16 * dx, pz + 16 * dz) + height(model, s, px - 16 * dx, pz - 16 * dz));
        if (d16 <= 4.0) {
            return;
        }
        double lap = Math.abs(height(model, s, px + 4, pz) + height(model, s, px - 4, pz) + height(model, s, px, pz + 4)
            + height(model, s, px, pz - 4) - 4.0 * top) / 16.0;
        double ratio = (d4 / 4.0) / (d16 / 16.0);
        c.incrementAndGet(11);
        if (ratio < 0.45) {
            c.incrementAndGet(12);
            if (Boolean.getBoolean("dump") && c.get(12) % 25 == 0) {
                model.sampleInto(px, pz, s);
                System.out.printf(Locale.ROOT, "rounded crest %d %d dir %d,%d h=%.0f d4=%.2f d16=%.2f ratio=%.2f alpine=%.2f ancient=%.2f wall=%.2f meadow=%.2f peak=%.2f%n",
                    px, pz, dx, dz, top, d4, d16, ratio, s.alpine, s.ancient, s.wall, s.meadow, s.peakness);
            }
        } else if (ratio > 0.7) {
            c.incrementAndGet(13);
        }
        c.addAndGet(14, Math.round(Math.toDegrees(Math.atan(d4 / (4.0 * len))) * 1000.0));
        c.addAndGet(15, Math.round(lap * 1000.0));
        c.addAndGet(16, Math.round(Math.max(-1.0, Math.min(ratio, 3.0)) * 1000.0));
    }

    private static void count(AtomicLongArray c, int at, double deg, double a, double b, double d) {
        if (deg > a) {
            c.incrementAndGet(at);
        }
        if (deg > b) {
            c.incrementAndGet(at + 1);
        }
        if (deg > d) {
            c.incrementAndGet(at + 2);
        }
    }

    private static boolean bench(TerrainModel model, ColumnSample s, int x, int z) {
        double fx = height(model, s, x + 2, z) - height(model, s, x - 2, z);
        double fz = height(model, s, x, z + 2) - height(model, s, x, z - 2);
        return Math.hypot(fx, fz) / 4.0 < BENCH_SLOPE;
    }

    private static double height(TerrainModel model, ColumnSample s, int x, int z) {
        model.sampleInto(x, z, s);
        return s.height;
    }
}
