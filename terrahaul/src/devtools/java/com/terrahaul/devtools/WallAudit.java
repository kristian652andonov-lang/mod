package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Development tool: audits the terrain model's surface for walls that look "cut off" - sheer faces
 * far taller than a natural cliff and slot-like cuts that are deeper than they are wide - and
 * attributes every hit to the landform that produced it, so each class can be fixed at its source.
 * <p>
 * The surface is sampled on the 4-block grid the noise chunk interpolates between, which is
 * exactly the generated 2D ground (3D detail such as ledges and overhangs is not included). Four
 * measures are taken at every grid point:
 * <ul>
 *   <li><b>sheer</b>: the height of an unbroken run of grid segments steeper than
 *       {@value #SHEER_SLOPE} blocks per block (76 degrees: a wall); a ledge or bench ends the run.
 *       Columns {@code sheer30..sheer100} count the top points of runs of at least that height;</li>
 *   <li><b>steep</b>: the same with {@value #STEEP_SLOPE} blocks per block (68 degrees), runs of at
 *       least 100 blocks: walls that are not quite vertical but still read as one huge face;</li>
 *   <li><b>drop6</b>: the surface falls more than 40 blocks within 6 blocks horizontally;</li>
 *   <li><b>slot</b>: a cross-section at least 20 blocks deep whose width at 90% of its depth is
 *       smaller than its depth (both walls steeper than about 63 degrees on average).</li>
 * </ul>
 * Counts are grid points (one per 4 blocks of wall length). Usage:
 * {@code WallAudit <outDir> <sizeBlocks> <seed>[@x,z] ...}. For each seed it prints the statistics
 * per landform class with the worst examples, writes them to {@code wallaudit_<seed>.txt}, all hits
 * to {@code wallaudit_<seed>.csv} and a marker map {@code wallaudit_<seed>.png}: filled dots mark
 * sheer faces of 40 blocks or more and steep faces of 100 or more, hollow squares mark slots,
 * coloured by class (see {@link #COLORS}).
 */
public final class WallAudit {
    static final double SHEER_SLOPE = 4.0;
    static final double STEEP_SLOPE = 2.5;
    static final String[] CLASSES = {"trench", "fjord", "volcano", "canyon", "coast_cliff", "plateau_edge", "lake", "river",
        "mountain_wall", "mountain", "swamp", "windswept", "sea_floor", "island_shelf", "other", "mountain_seam"};
    static final int LAKE = 6;
    static final int RIVER = 7;
    static final int SEA_FLOOR = 12;
    static final int ISLAND_SHELF = 13;
    static final int OTHER = 14;
    static final int MOUNTAIN_SEAM = 15;
    /** Marker colours: trench blue, fjord cyan, volcano red, canyon orange, coast yellow, plateau brown,
     *  lake light blue, river dark blue, wall magenta, mountain purple, swamp green, windswept olive,
     *  sea floor lilac, island shelf pink, other white, mountain seam black. */
    static final int[] COLORS = {0x2040FF, 0x00C0C0, 0xFF2020, 0xFF8000, 0xFFFF00, 0xC08040, 0x40A0FF, 0x0060FF,
        0xFF00FF, 0xA000A0, 0x00FF00, 0x909020, 0x8080FF, 0xFF90C0, 0xFFFFFF, 0x000000};
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

    private WallAudit() {
    }

    /** One audit hit at a grid point: a face (with its foot) or a slot floor. */
    record Hit(int cls, int kind, int x, int z, int fx, int fz, double height, double width) {
    }

    static final int SHEER = 0;
    static final int STEEP = 1;
    static final int DROP6 = 2;
    static final int SLOT = 3;

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        dir.mkdirs();
        int size = Integer.parseInt(args[1]);
        for (int a = 2; a < args.length; a++) {
            String spec = args[a];
            int cx = 0;
            int cz = 0;
            int at = spec.indexOf('@');
            if (at >= 0) {
                String[] c = spec.substring(at + 1).split(",");
                cx = Integer.parseInt(c[0]);
                cz = Integer.parseInt(c[1]);
                spec = spec.substring(0, at);
            }
            audit(Long.parseLong(spec), cx, cz, size, dir);
        }
    }

    static void audit(long seed, int cx, int cz, int size, File dir) throws Exception {
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        int sl = model.seaLevel();
        int n = size / 4;
        int minX = (cx - size / 2) & ~3;
        int minZ = (cz - size / 2) & ~3;
        float[] h = new float[n * n];
        long t0 = System.nanoTime();
        IntStream.range(0, n).parallel().forEach(j -> {
            ColumnSample s = new ColumnSample();
            for (int i = 0; i < n; i++) {
                model.sampleInto(minX + i * 4, minZ + j * 4, s);
                h[j * n + i] = (float) s.height;
            }
        });
        System.out.printf(Locale.ROOT, "seed %d: sampled %dx%d grid (%d blocks) around %d,%d in %.1f s%n", seed, n, n, size, cx, cz, (System.nanoTime() - t0) / 1e9);

        float[] sheer = new float[n * n];
        int[] sheerFoot = new int[n * n];
        float[] steep = new float[n * n];
        int[] steepFoot = new int[n * n];
        float[] drop6 = new float[n * n];
        IntStream.range(2, n - 2).parallel().forEach(j -> {
            for (int i = 2; i < n - 2; i++) {
                int p = j * n + i;
                float d6 = 0f;
                for (int[] d : DIRS) {
                    boolean diag = d[0] != 0 && d[1] != 0;
                    int q1 = p + d[1] * n + d[0];
                    int q2 = q1 + d[1] * n + d[0];
                    double drop = diag ? h[p] - h[q1] : h[p] - 0.5 * (h[q1] + h[q2]);
                    d6 = (float) Math.max(d6, drop);
                }
                drop6[p] = d6;
                long r = run(h, n, i, j, SHEER_SLOPE);
                sheer[p] = Float.intBitsToFloat((int) (r >>> 32));
                sheerFoot[p] = (int) r;
                r = run(h, n, i, j, STEEP_SLOPE);
                steep[p] = Float.intBitsToFloat((int) (r >>> 32));
                steepFoot[p] = (int) r;
            }
        });

        final int reach = 32;
        float[] slotDepth = new float[n * n];
        float[] slotWidth = new float[n * n];
        int[][] lines = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        IntStream.range(reach, n - reach).parallel().forEach(j -> {
            for (int i = reach; i < n - reach; i++) {
                int p = j * n + i;
                float hp = h[p];
                for (int[] d : lines) {
                    int step = d[1] * n + d[0];
                    if (hp > h[p + step] || hp > h[p - step] || hp >= Math.max(h[p + step], h[p - step]) - 0.5f) {
                        continue;
                    }
                    double len = d[0] != 0 && d[1] != 0 ? 5.657 : 4.0;
                    float maxA = hp;
                    float maxB = hp;
                    for (int k = 1; k <= reach; k++) {
                        maxA = Math.max(maxA, h[p + k * step]);
                        maxB = Math.max(maxB, h[p - k * step]);
                    }
                    double depth = Math.min(maxA, maxB) - hp;
                    if (depth < 20.0) {
                        continue;
                    }
                    double level = hp + 0.9 * depth;
                    double width = (crossing(h, p, step, reach, hp, level) + crossing(h, p, -step, reach, hp, level)) * len;
                    if (width < depth && depth > slotDepth[p]) {
                        slotDepth[p] = (float) depth;
                        slotWidth[p] = (float) width;
                    }
                }
            }
        });

        List<Hit> hits = new ArrayList<>();
        ColumnSample a = new ColumnSample();
        ColumnSample b = new ColumnSample();
        for (int p = 0; p < n * n; p++) {
            boolean isSheer = sheer[p] >= 30.0f;
            boolean isSteep = steep[p] >= 100.0f;
            boolean isDrop = drop6[p] > 40.0f;
            boolean isSlot = slotDepth[p] > 0.0f;
            if (!isSheer && !isSteep && !isDrop && !isSlot) {
                continue;
            }
            int x = minX + (p % n) * 4;
            int z = minZ + (p / n) * 4;
            model.sampleInto(x, z, a);
            int foot = isSheer ? sheerFoot[p] : isSteep ? steepFoot[p] : p;
            int fx = minX + (foot % n) * 4;
            int fz = minZ + (foot / n) * 4;
            model.sampleInto(fx, fz, b);
            int cls = classify(a, b, sl, isSheer ? sheer[p] : isSteep ? steep[p] : drop6[p]);
            if (isSheer) {
                hits.add(new Hit(cls, SHEER, x, z, fx, fz, sheer[p], 0.0));
            }
            if (isSteep) {
                hits.add(new Hit(cls, STEEP, x, z, minX + (steepFoot[p] % n) * 4, minZ + (steepFoot[p] / n) * 4, steep[p], 0.0));
            }
            if (isDrop) {
                hits.add(new Hit(cls, DROP6, x, z, x, z, drop6[p], 0.0));
            }
            if (isSlot) {
                hits.add(new Hit(classify(a, a, sl, slotDepth[p]), SLOT, x, z, x, z, slotDepth[p], slotWidth[p]));
            }
        }

        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "WallAudit seed %d, %d x %d blocks around %d,%d (grid points, 4 blocks of wall length each)", seed, size, size, cx, cz));
        out.add(String.format(Locale.ROOT, "%-14s %8s %8s %8s %8s %8s %8s %9s %8s %8s", "class", "sheer30", "sheer40", "sheer60", "sheer100", "maxSheer", "steep100", "drop6>40", "slots", "maxSlot"));
        int[] tot = new int[8];
        for (int c = 0; c < CLASSES.length; c++) {
            int[] k = new int[8];
            double maxSheer = 0;
            double maxSlot = 0;
            for (Hit hit : hits) {
                if (hit.cls != c) {
                    continue;
                }
                switch (hit.kind) {
                    case SHEER -> {
                        k[0]++;
                        k[1] += hit.height >= 40 ? 1 : 0;
                        k[2] += hit.height >= 60 ? 1 : 0;
                        k[3] += hit.height >= 100 ? 1 : 0;
                        maxSheer = Math.max(maxSheer, hit.height);
                    }
                    case STEEP -> k[4]++;
                    case DROP6 -> k[5]++;
                    default -> {
                        k[6]++;
                        maxSlot = Math.max(maxSlot, hit.height);
                    }
                }
            }
            for (int i = 0; i < 8; i++) {
                tot[i] += k[i];
            }
            if (k[0] + k[4] + k[5] + k[6] > 0) {
                out.add(String.format(Locale.ROOT, "%-14s %8d %8d %8d %8d %8.0f %8d %9d %8d %8.0f", CLASSES[c], k[0], k[1], k[2], k[3], maxSheer, k[4], k[5], k[6], maxSlot));
            }
        }
        out.add(String.format(Locale.ROOT, "%-14s %8d %8d %8d %8d %8s %8d %9d %8d", "TOTAL", tot[0], tot[1], tot[2], tot[3], "", tot[4], tot[5], tot[6]));
        out.add("worst examples per class (sheer/steep: top x z, height, foot x z; slot: floor x z, depth, width):");
        String[] kinds = {"sheer", "steep", "drop6", "slot"};
        for (int c = 0; c < CLASSES.length; c++) {
            final int cls = c;
            List<Hit> worst = hits.stream().filter(hh -> hh.cls == cls && hh.kind != DROP6)
                .sorted(Comparator.comparingDouble((Hit hh) -> -(hh.kind == STEEP ? hh.height * 0.4 : hh.height))).toList();
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            List<int[]> used = new ArrayList<>();
            for (Hit hh : worst) {
                boolean near = false;
                for (int[] u : used) {
                    if (Math.abs(u[0] - hh.x) < 256 && Math.abs(u[1] - hh.z) < 256) {
                        near = true;
                        break;
                    }
                }
                if (near) {
                    continue;
                }
                used.add(new int[]{hh.x, hh.z});
                sb.append(hh.kind == SLOT ? String.format(Locale.ROOT, " [slot %d %d d=%.0f w=%.0f]", hh.x, hh.z, hh.height, hh.width)
                    : String.format(Locale.ROOT, " [%s %d %d h=%.0f foot %d %d]", kinds[hh.kind], hh.x, hh.z, hh.height, hh.fx, hh.fz));
                if (++shown >= 4) {
                    break;
                }
            }
            if (shown > 0) {
                out.add("  " + CLASSES[c] + ":" + sb);
            }
        }
        for (String line : out) {
            System.out.println(line);
        }
        Files.write(new File(dir, "wallaudit_" + seed + ".txt").toPath(), out);
        render(h, n, sl, hits, minX, minZ, new File(dir, "wallaudit_" + seed + ".png"));
        try (PrintWriter w = new PrintWriter(new File(dir, "wallaudit_" + seed + ".csv"))) {
            w.println("class,kind,x,z,footX,footZ,height,width");
            for (Hit hh : hits) {
                w.printf(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%.1f,%.1f%n", CLASSES[hh.cls], kinds[hh.kind], hh.x, hh.z, hh.fx, hh.fz, hh.height, hh.width);
            }
        }
    }

    /**
     * The tallest run of segments steeper than {@code slope} that starts at grid point (i, j) and
     * goes downhill in one of eight directions, packed as {@code (floatBits(height) << 32) | foot}.
     * A run only starts where the segment above it (in the same direction) is not as steep, so each
     * face is counted once per column along it.
     */
    private static long run(float[] h, int n, int i, int j, double slope) {
        int p = j * n + i;
        float best = 0f;
        int bestFoot = p;
        for (int[] d : DIRS) {
            double len = d[0] != 0 && d[1] != 0 ? 5.657 : 4.0;
            int q1 = p + d[1] * n + d[0];
            if ((h[p] - h[q1]) / len < slope) {
                continue;
            }
            int up = p - d[1] * n - d[0];
            if ((h[up] - h[p]) / len >= slope) {
                continue;
            }
            int ci = i;
            int cj = j;
            double height = 0.0;
            while (true) {
                int ni = ci + d[0];
                int nj = cj + d[1];
                if (ni < 0 || nj < 0 || ni >= n || nj >= n) {
                    break;
                }
                double seg = h[cj * n + ci] - h[nj * n + ni];
                if (seg / len < slope) {
                    break;
                }
                height += seg;
                ci = ni;
                cj = nj;
            }
            if (height > best) {
                best = (float) height;
                bestFoot = cj * n + ci;
            }
        }
        return ((long) Float.floatToIntBits(best) << 32) | (bestFoot & 0xFFFFFFFFL);
    }

    /** Steps from p along {@code step} until the profile reaches {@code level}; fractional steps. */
    private static double crossing(float[] h, int p, int step, int reach, double floor, double level) {
        double prev = floor;
        for (int k = 1; k <= reach; k++) {
            double v = h[p + k * step];
            if (v >= level) {
                return k - 1 + (level - prev) / Math.max(1.0E-6, v - prev);
            }
            prev = v;
        }
        return reach;
    }

    /**
     * The landform responsible for a wall of height {@code wall} between {@code top} and
     * {@code foot}. Rivers and lakes are carved last, so a wall whose foot (or top) they lowered by
     * more than half its height is theirs, whatever landform they cut through. Otherwise the
     * strongest of the other landforms wins, weighted so that a weak fringe of one does not outrank
     * the core of another; mountains (and their walls) only win where nothing else is present.
     * A wall across which the mountain mask itself jumps (by more than 0.02 per block, three times
     * as fast as at the steepest edge of a range) is a seam in the mask, whatever stands there.
     */
    static int classify(ColumnSample top, ColumnSample foot, int sl, double wall) {
        double apart = Math.max(4.0, Math.hypot(top.x - foot.x, top.z - foot.z));
        if (Math.abs(top.mountain - foot.mountain) / apart > 0.02) {
            return MOUNTAIN_SEAM;
        }
        double waterCut = Math.max(foot.preWaterHeight - foot.height, top.preWaterHeight - top.height);
        if (waterCut > 0.5 * wall) {
            return Math.max(top.riverValley, foot.riverValley) > 0.0 || top.river > 0.0 || foot.river > 0.0 ? RIVER : LAKE;
        }
        double[] score = new double[CLASSES.length];
        score[0] = Math.max(top.trench, foot.trench) * 2.0;
        score[1] = Math.max(top.fjord, foot.fjord) * 2.0;
        score[2] = Math.max(Math.max(top.volcano, foot.volcano), Math.max(top.crater, foot.crater)) * 1.5;
        score[3] = Math.max(top.canyon, foot.canyon) * 2.0;
        score[4] = Math.max(top.coastCliff, foot.coastCliff) * 1.5;
        // a plateau edge (caprock and talus) lies between the plateau and the land below it; a
        // wall inside the plateau is caused by whatever stands on it
        score[5] = Math.abs(top.plateau - foot.plateau) * 2.5;
        score[6] = top.lake > 0.0 || foot.lake > 0.0 ? 1.2 : 0.0;
        score[7] = Math.max(top.riverValley, foot.riverValley) * 1.2;
        score[8] = Math.max(top.wall, foot.wall) * 1.2;
        // mountain flanks, and walls where the mountain mask itself jumps
        score[9] = Math.max(top.mountain, foot.mountain) * 0.8 + Math.abs(top.mountain - foot.mountain) * 3.0;
        score[10] = Math.max(top.swamp, foot.swamp);
        score[11] = Math.max(top.windswept, foot.windswept);
        int best = OTHER;
        double bestScore = 0.1;
        for (int c = 0; c < SEA_FLOOR; c++) {
            if (score[c] > bestScore) {
                bestScore = score[c];
                best = c;
            }
        }
        if (best == OTHER && top.height < sl && foot.height < sl) {
            return top.island > 0.0 || foot.island > 0.0 ? ISLAND_SHELF : SEA_FLOOR;
        }
        return best;
    }

    private static void render(float[] h, int n, int sl, List<Hit> hits, int minX, int minZ, File file) throws Exception {
        int ds = Math.max(1, n / 1024);
        int w = n / ds;
        BufferedImage img = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < w; j++) {
            for (int i = 0; i < w; i++) {
                int p = (j * ds) * n + i * ds;
                double v = h[p];
                double west = h[(j * ds) * n + Math.max(0, i * ds - ds)];
                double north = h[Math.max(0, j * ds - ds) * n + i * ds];
                double shade = Math.max(0.45, Math.min(1.5, 1.0 + ((v - west) + (v - north)) / (4.0 * ds) * 0.8));
                int rgb;
                if (v < sl) {
                    double k = Math.min(1.0, (sl - v) / 120.0);
                    rgb = MapRenderer.rgb(40 - 25 * k, 90 - 50 * k, 160 - 60 * k);
                } else {
                    double e = Math.min(1.0, (v - sl) / 700.0);
                    rgb = MapRenderer.rgb((110 + 110 * e) * shade * 0.8, (130 + 80 * e) * shade * 0.8, (100 + 120 * e) * shade * 0.8);
                }
                img.setRGB(i, j, rgb);
            }
        }
        for (Hit hh : hits) {
            if (hh.kind == DROP6 || hh.kind == SHEER && hh.height < 40.0) {
                continue;
            }
            int i = (hh.x - minX) / 4 / ds;
            int j = (hh.z - minZ) / 4 / ds;
            int color = COLORS[hh.cls];
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    if (hh.kind == SLOT && di == 0 && dj == 0) {
                        continue;
                    }
                    int pi = i + di;
                    int pj = j + dj;
                    if (pi >= 0 && pj >= 0 && pi < w && pj < w) {
                        img.setRGB(pi, pj, color);
                    }
                }
            }
        }
        ImageIO.write(img, "png", file);
    }
}
