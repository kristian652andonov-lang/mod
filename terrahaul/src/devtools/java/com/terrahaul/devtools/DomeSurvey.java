package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

/**
 * Development tool: how rounded the mountains are where a player meets them, measured at random
 * mountain places rather than at finder targets (which pick the best-formed summits). It answers
 * "these don't look pointed at all, these look rounded": domed summits and shoulders, and even
 * slopes on which the block steps form concentric contour rings.
 * <p>
 * <b>Sites.</b> Points are drawn uniformly at random in a square around the origin and kept where
 * the column is mountain (weight at least 0.6, at least 60 blocks above the sea, dry, no volcano).
 * From each, the tool climbs to the nearest summit (steepest ascent on the 4-block grid, coarse
 * steps first, so small bumps on a flank are passed) and measures a disc of 160 blocks around it
 * (profiles reach 256 blocks). The heights are the model's at the corners of the 4-block grid,
 * which the game interpolates bilinearly, so the 2D surface is exactly the game's (the 3D crags
 * and overhangs are not included).
 * <p>
 * <b>Metrics</b> (blocks; slopes in blocks per block):
 * <ul>
 *   <li><b>summit drop</b> {@code drop8..drop256}: the summit's height minus the mean of 16 points
 *       8, 16, 32, 64, 128 and 256 blocks around it. A pointed summit with 45-60 degree faces drops
 *       16-28 blocks within 16; a dome a few. The cone indices compare the mean slope near the
 *       summit with the mean slope out to four times as far: {@code cone = (drop16/16) /
 *       (drop64/64)}, {@code cone32_128} and {@code cone64_256} likewise. About 1 on a cone,
 *       above 1 on a horn whose faces steepen towards the summit, about 0.25 on a paraboloid dome.
 *       {@code flat4} is the mean distance at which the ground has fallen 4 blocks below the
 *       summit (a pointed top: 2-5; a dome: 15 and more).</li>
 *   <li><b>uneroded summit</b>: the same on the surface without gullies, crest teeth and ledges
 *       ({@code udrop}, {@code ucone...}), which shows the shape the erosion terms are cut into:
 *       many summits are a tooth or spur 10-50 blocks high standing on a level or domed top.
 *       {@code rangeDrop}, {@code massifDrop} and {@code restDrop} split the uneroded drop into
 *       the range (relief times range shape), the colossal massif and the rest (base profile,
 *       valley floors that rise with the range, hills, carving), to see which forms the top.</li>
 *   <li><b>fits</b>: at every grid point a plane and a quadric are fitted by least squares to the
 *       9x9 corners within 16 blocks, and a quadric to the 5x5 within 8. The quadric's residual
 *       (RMS) measures the relief smaller than the window that a smooth surface lacks: gullies,
 *       spurs, ledges, teeth. {@code smoothConvex}: share of the upper ground (the top 40% of the
 *       site's relief) that is convex (the ground 16 blocks around lies on average more than a
 *       block below) and smooth over 32 blocks (residual under 0.5). {@code smoothFace}: share of
 *       the sloping ground (fitted slope over 0.35, about 19 degrees) with a 32-block residual
 *       under 0.5; {@code evenFace} with a plane residual under 1; {@code rough} with a residual
 *       over 2, and {@code res} the mean residual there. {@code fineSmooth}: share of the
 *       sloping ground whose 8-block residual is under 0.35: faces with nothing smaller than 16
 *       blocks on them, on which the block steps form regular contour rings ("terraced domes").</li>
 *   <li><b>crests</b>: grid points that are the highest within 12 blocks across the direction in
 *       which they are most convex over 32 blocks (and stand at least 3 blocks above the mean of
 *       the ground 32 blocks to both sides): {@code crestD8/32/128} is how far they stand above
 *       the ground that far to both sides (a knife-edge with 50-degree sides: D32 about 35),
 *       {@code rho = (D8/8) / (D32/32)}, about 1 on a cusp and 0.25 on a rounded crest; crests
 *       under 0.45 count as rounded, over 0.7 as cusped. {@code rho16_64} and {@code rho32_128}
 *       are the same at broader scales (across a diagonal the distances are 1.4 times longer).</li>
 *   <li><b>sub-peaks</b> per square kilometre: grid points that are the highest within 12 blocks and
 *       stand more than 6 blocks above the mean of the ground 32 blocks around.</li>
 *   <li><b>terms</b> ({@link TerrainModel#mountainTerms}), at the summit: gully and teeth term
 *       ({@code gullyTop}), how far a fall line prevails and the crest weight, the unfaded relief
 *       ({@code scale}), the range shape before and after the main valleys' troughs, horns
 *       against the crest network, the coast fade, the massif and the mask. Over the sloping
 *       ground of the disc: the gully depth asked for ({@code flankGd}) and cut ({@code
 *       flank|g|}), the shares without gullies, lowered by a trough, held by horns and with a
 *       crest weight over 0.5; over the upper ground: the share without gullies, the median slope
 *       of the uneroded surface and the share where it is under 0.6 (where the gullies are
 *       weakened).</li>
 *   <li><b>roundness</b> (0..1): the mean of {@code clamp((1.1 - cone32_128) / 0.7)} (the summit
 *       is convex over 32-128 blocks), {@code clamp((1.0 - ucone) / 0.7)} (the top under the
 *       teeth is a dome) and {@code clamp((fineSmooth - 0.2) / 0.6)} (smooth faces). ROUND at 0.5
 *       or more, POINTED at 0.2 or less.</li>
 * </ul>
 * <b>Style</b> (at the summit): {@code massif} (colossal massif term over 0.1), {@code wall} (mountain
 * wall), {@code ancient}, {@code horn} (alpine, the horns above the crest network at the summit) or
 * {@code crest} (alpine crest network); flags {@code +meadow}, {@code +coast} (coast fade under
 * 0.9), {@code +maskedge} (blurred mask under 0.6: foothills), {@code +trough} (the summit was
 * lowered by a main valley's trough), {@code +plateau}, {@code +windswept}, {@code +snowy}
 * (temperature under -0.15), {@code +frozen} (base temperature under -0.45), and the relief class
 * {@code low} (unfaded relief under 250), {@code mid} or {@code tall} (over 600).
 * <p>
 * <b>Cameras</b>: for each site a screenshot spec for {@code ClientScreenshotTest} is written: the
 * camera stands 70-150 blocks from the summit, 8 blocks above dry ground, on the side from which
 * the summit is seen 9-40 degrees up (as close to 25 as possible) with nothing in the way (or 30
 * blocks above the ground; failing that, any clear view of the summit, reported with a negative
 * distance), and looks a little below it. The AutoTest areas around the camera and the summit
 * are written too ({@code x,z,8}).
 * <p>
 * Usage:
 * <pre>
 *   DomeSurvey survey &lt;seed[,seed...]&gt; &lt;sitesPerSeed&gt; &lt;outDir&gt; [halfSize=40000] [rng=1]
 *   DomeSurvey sites &lt;sites.tsv&gt; &lt;outDir&gt;   (the start points of an earlier survey, measured again)
 *   DomeSurvey at &lt;seed&gt; &lt;x&gt; &lt;z&gt;           (one site, climbing from x z)
 *   DomeSurvey views &lt;sites.tsv&gt; &lt;outDir&gt; [id,id,...]
 *   DomeSurvey line &lt;seed&gt; &lt;x&gt; &lt;z&gt; &lt;angleDeg&gt; &lt;halfLength&gt; [step=4]
 *   DomeSurvey census &lt;seed&gt; &lt;columns&gt; [halfSize=40000] [rng=1]
 * </pre>
 * Seeds are world seeds (the model seed is derived as in the game, see {@link GameWorld}), or
 * {@code model:N}. {@code survey} and {@code sites} write {@code sites.tsv}, {@code details.txt},
 * {@code shots.txt} (one spec per site) and {@code areas.txt} into {@code outDir} and print the
 * table, summaries by style and flag, and the roundest and most pointed sites. After a change,
 * {@code sites} measures the same start points again (their summits and cameras may move; to see
 * the same views, reuse the old {@code shots.txt}).
 * <p>
 * {@code views} renders each site offline (the lit perspective of the model's height field, see
 * {@link Perspective}) from the camera of its in-game shot, 854x480 with a vertical field of view
 * of 80 degrees (the development client's window and options), into {@code outDir/dsNN.png}: the
 * same view as the screenshot, without blocks, 3D detail or vegetation.
 * <p>
 * {@code line} prints the terms of the mountain height along a line through a point (0 degrees runs
 * east, 90 south): the height, the surface without gullies and ledges, each term, the range shape
 * before and after the main valleys' troughs, horns against the crest network and the gully
 * controls, to see which term shapes a summit or shoulder.
 * <p>
 * {@code census} samples random mountain columns (weight at least 0.6) and reports how much of the
 * mountain ground, and of its upper part (range shape over 0.45), each term that flattens or
 * smooths the relief acts on: the crest network at its ceiling (the clamp of the ridged noise, a
 * level cap along the crest), meadows (the range shape pulled towards 0.3), the main valleys'
 * troughs (the range above them compressed), walls, ancient ranges, massifs, horns, no gullies,
 * no prevailing fall line (gullies faded on summits), crests (gullies halved), the coast fade and
 * the mask edge.
 */
public final class DomeSurvey {
    /** Roundness at and above which a site counts as ROUND, and at and below which as POINTED. */
    private static final double ROUND = 0.5;
    private static final double POINTED = 0.2;
    /** Grid spacing: the corners the game interpolates between. */
    private static final int G = 4;
    /** Radius of the measured disc around a summit, and the margin the fits and crest checks need (blocks). */
    private static final int INNER = 160;
    private static final int MARGIN = 64;
    /** Farthest the summit profile and the broad crest check reach (blocks). */
    private static final int REACH = 288;
    private static final int HALF = Math.max(INNER + MARGIN, REACH) / G;
    private static final int N = 2 * HALF + 1;
    /** Least-squares quadric over the 9x9 corners within 16 blocks: coefficients = Q * heights. */
    private static final double[][] Q = quadricProjection(4);
    /** The same over the 5x5 corners within 8 blocks. */
    private static final double[][] Q8 = quadricProjection(2);
    private static final String[] TERM_NAMES = {"uneroded", "gully", "ledge", "gullyDepth", "relief", "rangeS", "mask", "weight",
        "stand", "massif", "coastFade", "scale", "prevail", "crestW", "coherence", "horn", "crest", "preTrough", "troughT", "alpine",
        "ancient", "meadow"};

    private DomeSurvey() {
    }

    public static void main(String[] args) throws IOException {
        switch (args[0]) {
            case "survey" -> survey(args);
            case "sites" -> resurvey(args);
            case "views" -> views(args);
            case "line" -> line(args);
            case "census" -> census(args);
            case "at" -> {
                GameWorld world = GameWorld.of(args[1]);
                Site s = measure(world, 0, Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                System.out.println(header());
                System.out.println(s.row());
                System.out.println(s.detail());
                System.out.println("shot " + s.shot());
            }
            default -> throw new IllegalArgumentException("mode survey|sites|at|views|line|census");
        }
    }

    private static void survey(String[] args) throws IOException {
        String[] seeds = args[1].split(",");
        int count = Integer.parseInt(args[2]);
        Path out = Path.of(args[3]);
        int half = args.length > 4 ? Integer.parseInt(args[4]) : 40000;
        long rng = args.length > 5 ? Long.parseLong(args[5]) : 1L;
        List<Site> all = new ArrayList<>();
        for (String seedArg : seeds) {
            GameWorld world = GameWorld.of(seedArg);
            System.out.println("# " + world);
            List<int[]> starts = startPoints(world, count, half, rng);
            List<Site> sites = measureAll(world, starts, all.size());
            all.addAll(sites);
        }
        report(all, out);
    }

    private static void views(String[] args) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(args[1]));
        Path out = Path.of(args[2]);
        Files.createDirectories(out);
        java.util.Set<Integer> only = new java.util.HashSet<>();
        if (args.length > 3) {
            for (String id : args[3].split(",")) {
                only.add(Integer.parseInt(id));
            }
        }
        String[] h = lines.get(0).split("\t");
        int shotCol = java.util.Arrays.asList(h).indexOf("shot");
        Map<String, GameWorld> worlds = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] f = line.split("\t");
            int id = Integer.parseInt(f[0]);
            if (!only.isEmpty() && !only.contains(id)) {
                continue;
            }
            GameWorld world = worlds.computeIfAbsent(f[1], k -> GameWorld.of(k.startsWith("model") ? "model:" + k.substring(5) : k));
            String[] cam = f[shotCol].substring(f[shotCol].indexOf('@') + 1).split(",");
            double cx = Double.parseDouble(cam[0]);
            double cy = Double.parseDouble(cam[1]) + 1.62;
            double cz = Double.parseDouble(cam[2]);
            double yaw = Double.parseDouble(cam[3]);
            double pitch = Double.parseDouble(cam[4]);
            // fine field 1 block apart within 224 blocks of the camera, a coarse one 4 apart beyond
            HeightField fine = HeightField.sample(world.model, (int) Math.round(cx), (int) Math.round(cz), 448, 1);
            HeightField coarse = HeightField.sample(world.model, (int) Math.round(cx), (int) Math.round(cz), 400, 4);
            // (the development client's window and field of view: 854x480, 80 degrees vertically)
            double focal = 240.0 / Math.tan(Math.toRadians(40.0));
            double horizon = (240.0 - focal * Math.tan(Math.toRadians(pitch))) / 480.0;
            double hfov = 2.0 * Math.toDegrees(Math.atan(427.0 / focal));
            java.awt.image.BufferedImage img = Perspective.view(fine, coarse, cx, cz, cy, yaw + 180.0, hfov, horizon, 854, 480);
            javax.imageio.ImageIO.write(img, "png", out.resolve(String.format(Locale.ROOT, "ds%02d.png", id)).toFile());
            System.out.println("wrote " + out.resolve(String.format(Locale.ROOT, "ds%02d.png", id)));
        }
    }

    private static void line(String[] args) {
        GameWorld world = GameWorld.of(args[1]);
        TerrainModel model = world.model;
        int x0 = Integer.parseInt(args[2]);
        int z0 = Integer.parseInt(args[3]);
        double a = Math.toRadians(Double.parseDouble(args[4]));
        int half = Integer.parseInt(args[5]);
        int step = args.length > 6 ? Integer.parseInt(args[6]) : 4;
        double[] t = new double[TERM_NAMES.length];
        System.out.println("   d      x      z  height uneroded  gully  ledge  depth prevail crestW coher  relief  scale rangeS preTr  trouT   horn  crest massif   mask  coast alpine ancien meadow  wall");
        ColumnSample s = new ColumnSample();
        for (int d = -half; d <= half; d += step) {
            int x = x0 + (int) Math.round(d * Math.cos(a));
            int z = z0 + (int) Math.round(d * Math.sin(a));
            model.mountainTerms(x, z, t);
            model.sampleInto(x, z, s);
            System.out.printf(Locale.ROOT, "%4d %6d %6d %7.1f %8.1f %6.1f %6.1f %6.1f %7.2f %6.2f %5.2f %7.0f %6.0f %6.3f %5.3f %6.2f %6.3f %6.3f %6.3f %6.2f %6.2f %6.2f %6.2f %6.2f %5.2f%n",
                d, x, z, s.height, s.height - t[1] - t[2], t[1], t[2], t[3], t[12], t[13], t[14], t[4], t[11], t[5], t[17], t[18], t[15], t[16],
                t[9], t[6], t[10], t[19], t[20], t[21], s.wall / Math.max(1.0E-6, s.mountain));
        }
    }

    private static void census(String[] args) {
        GameWorld world = GameWorld.of(args[1]);
        TerrainModel model = world.model;
        int count = Integer.parseInt(args[2]);
        int half = args.length > 3 ? Integer.parseInt(args[3]) : 40000;
        long rng = args.length > 4 ? Long.parseLong(args[4]) : 1L;
        System.out.println("# " + world);
        List<int[]> pts = startPoints(world, count, half, rng);
        String[] names = {"crest network at its ceiling", "meadow over 0.15", "lowered by a valley trough", "mountain wall", "ancient", "colossal massif",
            "horn above crest network", "no gullies (depth < 2)", "gullies faded: prevail < 0.3", "crest weight over 0.5", "coast fade under 0.9",
            "mask under 0.6", "unfaded relief under 250", "unfaded relief over 600"};
        long[][] c = new long[2][names.length + 1];
        double[][] sums = new double[2][3];
        IntStream.range(0, pts.size()).parallel().forEach(k -> {
            double[] t = new double[TERM_NAMES.length];
            ColumnSample s = new ColumnSample();
            model.mountainTerms(pts.get(k)[0], pts.get(k)[1], t);
            model.sampleInto(pts.get(k)[0], pts.get(k)[1], s);
            double sat = 0.88 * (0.66 + 0.34 * Math.min(1.0, 1.6 * t[15]));
            boolean[] f = {
                t[19] > 0.5 && t[16] >= t[15] && t[16] >= 0.97 * sat,
                t[21] > 0.15,
                troughLowered(t[17], t[5], t[18], t[21]),
                s.wall / Math.max(s.mountain, 1.0E-6) > 0.3,
                t[20] > 0.5,
                t[9] > 0.1,
                t[19] > 0.5 && t[15] > t[16],
                t[3] < 2.0,
                t[12] < 0.3 && t[3] >= 2.0,
                t[13] > 0.5,
                t[10] < 0.9,
                t[6] < 0.6,
                t[11] < 250.0,
                t[11] > 600.0};
            for (int u = 0; u < 2; u++) {
                if (u == 1 && t[5] <= 0.45) {
                    continue;
                }
                synchronized (c) {
                    c[u][names.length]++;
                    for (int i = 0; i < f.length; i++) {
                        c[u][i] += f[i] ? 1 : 0;
                    }
                    sums[u][0] += t[3];
                    sums[u][1] += Math.abs(t[1]);
                    sums[u][2] += t[21];
                }
            }
        });
        for (int i = 0; i < names.length; i++) {
            System.out.printf(Locale.ROOT, "%-32s all %5.1f%%   upper %5.1f%%%n", names[i], 100.0 * c[0][i] / c[0][names.length], 100.0 * c[1][i] / Math.max(1, c[1][names.length]));
        }
        System.out.printf(Locale.ROOT, "columns %d (upper %d); mean gully depth %.1f / %.1f, mean |gully| %.1f / %.1f, mean meadow %.2f / %.2f%n",
            c[0][names.length], c[1][names.length], sums[0][0] / c[0][names.length], sums[1][0] / Math.max(1, c[1][names.length]),
            sums[0][1] / c[0][names.length], sums[1][1] / Math.max(1, c[1][names.length]), sums[0][2] / c[0][names.length], sums[1][2] / Math.max(1, c[1][names.length]));
    }

    private static void resurvey(String[] args) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(args[1]));
        Path out = Path.of(args[2]);
        Map<String, List<int[]>> byWorld = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.startsWith("id\t") || line.isBlank()) {
                continue;
            }
            String[] f = line.split("\t");
            byWorld.computeIfAbsent(f[1], k -> new ArrayList<>()).add(new int[]{Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[0])});
        }
        List<Site> all = new ArrayList<>();
        for (Map.Entry<String, List<int[]>> e : byWorld.entrySet()) {
            GameWorld world = GameWorld.of(e.getKey().startsWith("model") ? "model:" + e.getKey().substring(5) : e.getKey());
            System.out.println("# " + world);
            List<int[]> starts = e.getValue();
            Site[] sites = new Site[starts.size()];
            IntStream.range(0, starts.size()).parallel().forEach(i -> sites[i] = measure(world, starts.get(i)[2], starts.get(i)[0], starts.get(i)[1]));
            all.addAll(List.of(sites));
        }
        report(all, out);
    }

    /**
     * Random start points on mountains: drawn in batches, tested in parallel and kept in the order
     * they were drawn, so the list depends only on the seed, the count and the random seed.
     */
    private static List<int[]> startPoints(GameWorld world, int count, int half, long rng) {
        TerrainModel model = world.model;
        int sl = model.seaLevel();
        SplittableRandom random = new SplittableRandom(rng * 0x9E3779B97F4A7C15L ^ world.worldSeed);
        List<int[]> starts = new ArrayList<>();
        int tried = 0;
        while (starts.size() < count && tried < 200000) {
            int batch = 256;
            int[][] pts = new int[batch][2];
            for (int i = 0; i < batch; i++) {
                pts[i][0] = random.nextInt(-half, half) & ~3;
                pts[i][1] = random.nextInt(-half, half) & ~3;
            }
            boolean[] ok = new boolean[batch];
            IntStream.range(0, batch).parallel().forEach(i -> {
                ColumnSample s = new ColumnSample();
                model.sampleInto(pts[i][0], pts[i][1], s);
                ok[i] = s.mountain >= 0.6 && s.height >= sl + 60.0 && s.waterLevel == ColumnSample.NO_WATER && s.river <= 0.0
                    && s.lake <= 0.0 && s.volcano < 0.05;
            });
            for (int i = 0; i < batch && starts.size() < count; i++) {
                if (ok[i]) {
                    starts.add(pts[i]);
                }
            }
            tried += batch;
        }
        System.out.printf(Locale.ROOT, "# %d start points from %d random columns%n", starts.size(), tried);
        return starts;
    }

    private static List<Site> measureAll(GameWorld world, List<int[]> starts, int firstId) {
        Site[] sites = new Site[starts.size()];
        IntStream.range(0, starts.size()).parallel().forEach(i -> sites[i] = measure(world, firstId + i, starts.get(i)[0], starts.get(i)[1]));
        return List.of(sites);
    }

    // =========================================================================================
    // Measuring one site
    // =========================================================================================

    static final class Site {
        int id;
        String world;
        int x0;
        int z0;
        int sx;
        int sz;
        double top;
        double travel;
        double siteRelief;
        /** Summit drop at 8, 16, 32, 64, 128 and 256 blocks (mean and least of 16 directions). */
        double[] drop = new double[6];
        double[] dropMin = new double[6];
        /** {@code (drop32/32) / (drop128/128)} and {@code (drop64/64) / (drop256/256)}: the cone index at broader scales. */
        double cone2;
        double cone3;
        double cone;
        double flat4;
        double smoothConvex;
        double smoothFace;
        double evenFace;
        /** Share of the sloping ground whose quadric residual within 8 blocks is under 0.35, and its median residual. */
        double fineSmooth;
        double fineRes50;
        double rough;
        double meanRes;
        /** Median quadric residual of the upper ground and of the sloping ground, and the median slope of the sloping ground. */
        double resUpper50;
        double resSlope50;
        double slope50;
        int crests;
        double crestRounded;
        double crestCusped;
        double crestRho;
        /** Crest roundness at broader scales: {@code (D16/16)/(D64/64)} and {@code (D32/32)/(D128/128)}, and the share under 0.45. */
        double crestRho2;
        double crestRho3;
        double crestRounded2;
        double crestRounded3;
        double crestD128;
        double crestD32;
        double crestD8;
        double subpeaks;
        double udrop16;
        double udrop32;
        /** The same summit measures on the surface without gullies, crest teeth and ledges. */
        double[] udrop = new double[6];
        /** The uneroded summit drops split into the range, the massif and the rest (see {@link #measure}). */
        double[] rangeDrop = new double[6];
        double[] massifDrop = new double[6];
        double[] massDrop = new double[6];
        double ucone;
        double ucone2;
        double ucone3;
        int ucrests;
        double ucrestRounded3;
        double ucrestRho3;
        /**
         * Over the sloping ground of the disc: the share without gullies (depth under 2 blocks), the
         * share lowered by a main valley's trough, held by horns rather than the crest network, and
         * with a crest weight over 0.5; over the upper ground the share without gullies.
         */
        double flankNoGully;
        double flankCapped;
        double flankHorn;
        double flankCrestW;
        double upperNoGully;
        /**
         * Median slope of the uneroded surface over the upper ground, and the share of the upper
         * ground where it is under 0.6 (31 degrees), on which the gullies are weakened (see
         * {@code MountainRelief.gullies}: their strength fades in from 0.12 to 0.6).
         */
        double upperSlopeU50;
        double upperGentle;
        double roundness;
        String style;
        String flags;
        double[] terms = new double[TERM_NAMES.length];
        double ringPrevail;
        double ringCrestW;
        double ringGully;
        double flankGullyDepth;
        double flankGully;
        double flankLedge;
        double flankPrevail;
        double temperature;
        double camX;
        double camY;
        double camZ;
        double yaw;
        double pitch;
        double camDist;

        String verdict() {
            return roundness >= ROUND ? "ROUND" : roundness <= POINTED ? "POINTED" : "MIXED";
        }

        String shot() {
            return String.format(Locale.ROOT, "ds%02d@%.1f,%.1f,%.1f,%.1f,%.1f", id, camX, camY, camZ, yaw, pitch);
        }

        String row() {
            return String.format(Locale.ROOT, "%d\t%s\t%d\t%d\t%d\t%d\t%.0f\t%.0f\t%.0f\t%s\t%s\t%s\t%.2f\t%.1f\t%.1f\t%.1f\t%.1f\t%.1f\t%.1f\t%.2f\t%.2f\t%.2f\t%.1f\t%.1f\t%.1f\t%.2f\t%.2f\t%.2f\t%.1f\t%.1f\t%.1f\t%.1f\t%.1f\t%.1f\t%.1f\t%.0f\t%.0f\t%.0f\t%.0f\t%.2f\t%.2f\t%.2f\t%.2f\t%.0f\t%.2f\t%d\t%.0f\t%.0f\t%.2f\t%.2f\t%.2f\t%.0f\t%.0f\t%.1f\t%.1f\t%.1f\t%.0f\t%.0f\t%.2f\t%.1f\t%.1f\t%.1f\t%.0f\t%.0f\t%.2f\t%.0f\t%.0f\t%.0f\t%.0f\t%.2f\t%.2f\t%.2f\t%.0f\t%.0f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.0f\t%s",
                id, world, x0, z0, sx, sz, top, siteRelief, travel, verdict(), style, flags, roundness, drop[1], drop[2], drop[3], drop[4], drop[5], flat4, cone, cone2, cone3, udrop[1], udrop[3], udrop[4], ucone, ucone2, ucone3, rangeDrop[1], rangeDrop[3], rangeDrop[4], massifDrop[3], massDrop[1], massDrop[3], massDrop[4], 100.0 * smoothConvex, 100.0 * smoothFace, 100.0 * evenFace, 100.0 * rough, meanRes, resUpper50, resSlope50, slope50, 100.0 * fineSmooth, fineRes50, crests, 100.0 * crestRounded, 100.0 * crestCusped, crestRho, crestRho2, crestRho3, 100.0 * crestRounded2, 100.0 * crestRounded3, crestD8, crestD32, crestD128, subpeaks, 100.0 * ucrestRounded3, ucrestRho3, terms[1], flankGullyDepth, flankGully, 100.0 * flankNoGully, 100.0 * upperNoGully, upperSlopeU50, 100.0 * upperGentle, 100.0 * flankCapped, 100.0 * flankHorn, 100.0 * flankCrestW, terms[12], terms[13], ringPrevail, terms[11], terms[4], terms[5], terms[15], terms[16], terms[17], terms[18], terms[10], terms[9], terms[6], terms[19], terms[20], terms[21], temperature, camDist, shot());
        }

        String detail() {
            StringBuilder b = new StringBuilder();
            b.append(String.format(Locale.ROOT, "site %d (%s) start %d %d summit %d %d y=%.0f relief in disc %.0f, climbed %.0f blocks%n",
                id, world, x0, z0, sx, sz, top, siteRelief, travel));
            b.append(String.format(Locale.ROOT, "  drop 8/16/32/64: %.1f %.1f %.1f %.1f (least of 16 directions %.1f %.1f %.1f %.1f), flat4 %.1f, cone %.2f%n",
                drop[0], drop[1], drop[2], drop[3], dropMin[0], dropMin[1], dropMin[2], dropMin[3], flat4, cone));
            b.append(String.format(Locale.ROOT, "  drop 128/256: %.1f %.1f, cone at 32/128 %.2f, at 64/256 %.2f%n", drop[4], drop[5], cone2, cone3));
            b.append(String.format(Locale.ROOT, "  uneroded drop 16/32/64/128/256: %.1f %.1f %.1f %.1f %.1f, cone %.2f, at 32/128 %.2f, at 64/256 %.2f; crests %d rounded at 32/128 %.0f%%%n",
                udrop[1], udrop[2], udrop[3], udrop[4], udrop[5], ucone, ucone2, ucone3, ucrests, 100 * ucrestRounded3));
            b.append(String.format(Locale.ROOT, "  uneroded drop 16/64/128 by part: range %.1f %.1f %.1f, massif %.1f %.1f %.1f, rest %.1f %.1f %.1f%n",
                rangeDrop[1], rangeDrop[3], rangeDrop[4], massifDrop[1], massifDrop[3], massifDrop[4], massDrop[1], massDrop[3], massDrop[4]));
            b.append(String.format(Locale.ROOT, "  sloping ground: no gullies %.0f%%, lowered by a trough %.0f%%, horns %.0f%%, crest weight over 0.5 %.0f%%; upper ground without gullies %.0f%%%n",
                100 * flankNoGully, 100 * flankCapped, 100 * flankHorn, 100 * flankCrestW, 100 * upperNoGully));
            b.append(String.format(Locale.ROOT, "  crests at broader scales: rho 16/64 %.2f (rounded %.0f%%), 32/128 %.2f (rounded %.0f%%), D128 %.1f%n",
                crestRho2, 100 * crestRounded2, crestRho3, 100 * crestRounded3, crestD128));
            b.append(String.format(Locale.ROOT, "  median residual upper %.2f sloping %.2f, median slope %.2f; within 8 blocks: smooth %.0f%%, median residual %.2f%n",
                resUpper50, resSlope50, slope50, 100 * fineSmooth, fineRes50));
            b.append(String.format(Locale.ROOT, "  smoothConvex %.0f%% smoothFace %.0f%% evenFace %.0f%% rough %.0f%% res %.2f; crests %d rounded %.0f%% cusped %.0f%% rho %.2f D8 %.1f D32 %.1f; sub-peaks %.0f/km2%n",
                100 * smoothConvex, 100 * smoothFace, 100 * evenFace, 100 * rough, meanRes, crests, 100 * crestRounded, 100 * crestCusped,
                crestRho, crestD8, crestD32, subpeaks));
            b.append("  terms at summit:");
            for (int i = 0; i < TERM_NAMES.length; i++) {
                b.append(String.format(Locale.ROOT, " %s=%.3f", TERM_NAMES[i], terms[i]));
            }
            b.append(String.format(Locale.ROOT, "%n  ring 32: prevail %.2f crestW %.2f |gully| %.1f; flanks: gully depth %.1f |gully| %.1f |ledge| %.1f prevail %.2f; temperature %.2f%n",
                ringPrevail, ringCrestW, ringGully, flankGullyDepth, flankGully, flankLedge, flankPrevail, temperature));
            b.append(String.format(Locale.ROOT, "  style %s %s roundness %.2f %s", style, flags, roundness, verdict()));
            return b.toString();
        }
    }

    static String header() {
        return "id\tworld\tx0\tz0\tsx\tsz\ttop\trelief\tclimb\tverdict\tstyle\tflags\troundness\tdrop16\tdrop32\tdrop64\tdrop128\tdrop256\tflat4\tcone\tcone32_128\tcone64_256\tudrop16\tudrop64\tudrop128\tucone\tucone32_128\tucone64_256\trangeDrop16\trangeDrop64\trangeDrop128\tmassifDrop64\trestDrop16\trestDrop64\trestDrop128\tsmoothConvex%\tsmoothFace%\tevenFace%\trough%\tres\tresUp50\tresSlope50\tslope50\tfineSmooth%\tfineRes50\tcrests\tcrestRound%\tcrestCusp%\trho\trho16_64\trho32_128\tcrestRound16_64%\tcrestRound32_128%\tcrestD8\tcrestD32\tcrestD128\tsubpeaks/km2\tucrestRound32_128%\turho32_128\tgullyTop\tflankGd\tflank|g|\tflankNoGully%\tupperNoGully%\tupperSlopeU50\tupperGentle%\tflankTrough%\tflankHorn%\tflankCrestW%\tprevailTop\tcrestWTop\tringPrevail\tscale\treliefT\trangeS\thorn\tcrestNet\tpreTrough\ttroughT\tcoastFade\tmassif\tmask\talpine\tancient\tmeadow\ttemp\tcamDist\tshot";
    }

    /** Climbs from {@code (x, z)} to the nearest summit and measures the disc around it. */
    static Site measure(GameWorld world, int id, int x, int z) {
        TerrainModel model = world.model;
        Site site = new Site();
        site.id = id;
        site.world = world.label();
        site.x0 = x;
        site.z0 = z;
        // ---- climb: steepest ascent, coarse steps first --------------------------------------
        int cx = x & ~3;
        int cz = z & ~3;
        double h = model.sample(cx, cz).height;
        for (int step : new int[]{32, 16, 8, 4}) {
            for (int it = 0; it < 400; it++) {
                int bx = cx;
                int bz = cz;
                double bh = h;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        double v = model.sample(cx + dx * step, cz + dz * step).height;
                        if (v > bh + 1.0E-3) {
                            bh = v;
                            bx = cx + dx * step;
                            bz = cz + dz * step;
                        }
                    }
                }
                if (bx == cx && bz == cz) {
                    break;
                }
                cx = bx;
                cz = bz;
                h = bh;
            }
        }
        site.sx = cx;
        site.sz = cz;
        site.top = h;
        site.travel = Math.hypot(cx - x, cz - z);
        // ---- the grid ------------------------------------------------------------------------
        double[] g = new double[N * N];
        double[] water = new double[N * N];
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                ColumnSample s = model.sample(cx + (i - HALF) * G, cz + (j - HALF) * G);
                g[j * N + i] = s.height;
                water[j * N + i] = s.waterLevel;
            }
        }
        double top = g[HALF * N + HALF];
        site.top = top;
        // ---- summit drop ---------------------------------------------------------------------
        summitDrops(g, top, site.drop, site.dropMin);
        site.cone = (site.drop[1] / 16.0) / Math.max(1.0E-3, site.drop[3] / 64.0);
        site.cone2 = (site.drop[2] / 32.0) / Math.max(1.0E-3, site.drop[4] / 128.0);
        site.cone3 = (site.drop[3] / 64.0) / Math.max(1.0E-3, site.drop[5] / 256.0);
        double flat = 0.0;
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8.0;
            int r = 1;
            while (r < 64 && top - bilinear(g, r * Math.cos(a), r * Math.sin(a)) < 4.0) {
                r++;
            }
            flat += r;
        }
        site.flat4 = flat / 16.0;
        // ---- fits, crests, sub-peaks over the disc -------------------------------------------
        double low = top;
        for (int j = HALF - INNER / G; j <= HALF + INNER / G; j++) {
            for (int i = HALF - INNER / G; i <= HALF + INNER / G; i++) {
                if (inDisc(i, j)) {
                    low = Math.min(low, g[j * N + i]);
                }
            }
        }
        site.siteRelief = top - low;
        double upperFrom = top - 0.4 * site.siteRelief;
        int upper = 0;
        int convexSmooth = 0;
        int sloping = 0;
        int smooth = 0;
        int even = 0;
        int fineSmooth = 0;
        List<Double> fineRes = new ArrayList<>();
        int rough = 0;
        double resSum = 0.0;
        List<Double> resUpper = new ArrayList<>();
        List<Double> resSlope = new ArrayList<>();
        List<Double> slopes = new ArrayList<>();
        int crests = 0;
        int rounded = 0;
        int cusped = 0;
        double rhoSum = 0.0;
        double rho2Sum = 0.0;
        double rho3Sum = 0.0;
        int rounded2 = 0;
        int rounded3 = 0;
        double d128Sum = 0.0;
        double d8Sum = 0.0;
        double d32Sum = 0.0;
        int subpeaks = 0;
        int sea = model.seaLevel();
        double[] q = new double[6];
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int j = HALF - INNER / G; j <= HALF + INNER / G; j++) {
            for (int i = HALF - INNER / G; i <= HALF + INNER / G; i++) {
                if (!inDisc(i, j)) {
                    continue;
                }
                double v = g[j * N + i];
                if (v < sea + 4.0 || water[j * N + i] != ColumnSample.NO_WATER) {
                    continue;
                }
                // quadric and plane over the 9x9 corners within 16 blocks
                for (int c = 0; c < 6; c++) {
                    double acc = 0.0;
                    int k = 0;
                    for (int b = -4; b <= 4; b++) {
                        for (int a = -4; a <= 4; a++) {
                            acc += Q[c][k++] * g[(j + b) * N + i + a];
                        }
                    }
                    q[c] = acc;
                }
                double qRes = 0.0;
                double pRes = 0.0;
                double mean = 0.0;
                double su = 0.0;
                double sv = 0.0;
                double suu = 0.0;
                for (int b = -4; b <= 4; b++) {
                    for (int a = -4; a <= 4; a++) {
                        double hv = g[(j + b) * N + i + a];
                        mean += hv;
                        su += hv * a * G;
                        sv += hv * b * G;
                        suu += (double) a * G * a * G;
                    }
                }
                mean /= 81.0;
                double pb = su / suu;
                double pc = sv / suu;
                for (int b = -4; b <= 4; b++) {
                    for (int a = -4; a <= 4; a++) {
                        double u = a * G;
                        double w = b * G;
                        double hv = g[(j + b) * N + i + a];
                        double fq = q[0] + q[1] * u + q[2] * w + q[3] * u * u + q[4] * u * w + q[5] * w * w;
                        double fp = mean + pb * u + pc * w;
                        qRes += (hv - fq) * (hv - fq);
                        pRes += (hv - fp) * (hv - fp);
                    }
                }
                qRes = Math.sqrt(qRes / 81.0);
                pRes = Math.sqrt(pRes / 81.0);
                // the same quadric residual within 8 blocks: relief finer than 16 blocks
                double fRes = 0.0;
                for (int c = 0; c < 6; c++) {
                    double acc = 0.0;
                    int k = 0;
                    for (int b = -2; b <= 2; b++) {
                        for (int a = -2; a <= 2; a++) {
                            acc += Q8[c][k++] * g[(j + b) * N + i + a];
                        }
                    }
                    q[c] = acc;
                }
                for (int b = -2; b <= 2; b++) {
                    for (int a = -2; a <= 2; a++) {
                        double u = a * G;
                        double w = b * G;
                        double fq = q[0] + q[1] * u + q[2] * w + q[3] * u * u + q[4] * u * w + q[5] * w * w;
                        fRes += (g[(j + b) * N + i + a] - fq) * (g[(j + b) * N + i + a] - fq);
                    }
                }
                fRes = Math.sqrt(fRes / 25.0);
                // (q back to the 32-block fit)
                for (int c = 0; c < 6; c++) {
                    double acc = 0.0;
                    int k = 0;
                    for (int b = -4; b <= 4; b++) {
                        for (int a = -4; a <= 4; a++) {
                            acc += Q[c][k++] * g[(j + b) * N + i + a];
                        }
                    }
                    q[c] = acc;
                }
                double slope = Math.hypot(q[1], q[2]);
                // mean height of the ground 16 blocks around below the fitted centre
                double convex16 = -(q[3] + q[5]) * 128.0;
                if (v >= upperFrom) {
                    upper++;
                    resUpper.add(qRes);
                    if (convex16 > 1.0 && qRes < 0.5) {
                        convexSmooth++;
                    }
                }
                if (slope > 0.35) {
                    sloping++;
                    resSum += qRes;
                    resSlope.add(qRes);
                    slopes.add(slope);
                    if (qRes < 0.5) {
                        smooth++;
                    }
                    if (pRes < 1.0) {
                        even++;
                    }
                    if (qRes > 2.0) {
                        rough++;
                    }
                    fineRes.add(fRes);
                    if (fRes < 0.35) {
                        fineSmooth++;
                    }
                }
                // crest across the direction of the greatest convexity over 32 blocks
                int best = -1;
                double bestD = 3.0;
                for (int k = 0; k < 4; k++) {
                    double d32 = v - 0.5 * (g[(j + 8 * dirs[k][1]) * N + i + 8 * dirs[k][0]] + g[(j - 8 * dirs[k][1]) * N + i - 8 * dirs[k][0]]);
                    if (d32 > bestD) {
                        bestD = d32;
                        best = k;
                    }
                }
                if (best >= 0) {
                    boolean highest = true;
                    for (int o = 1; o <= 3 && highest; o++) {
                        highest = g[(j + o * dirs[best][1]) * N + i + o * dirs[best][0]] <= v
                            && g[(j - o * dirs[best][1]) * N + i - o * dirs[best][0]] <= v;
                    }
                    if (highest) {
                        double d8 = v - 0.5 * (g[(j + 2 * dirs[best][1]) * N + i + 2 * dirs[best][0]] + g[(j - 2 * dirs[best][1]) * N + i - 2 * dirs[best][0]]);
                        double rho = (d8 / 2.0) / (bestD / 8.0);
                        crests++;
                        rhoSum += Math.max(-1.0, Math.min(rho, 3.0));
                        d8Sum += d8;
                        d32Sum += bestD;
                        if (rho < 0.45) {
                            rounded++;
                        } else if (rho > 0.7) {
                            cusped++;
                        }
                        // the same across the crest at broader scales
                        double d16 = v - 0.5 * (g[(j + 4 * dirs[best][1]) * N + i + 4 * dirs[best][0]] + g[(j - 4 * dirs[best][1]) * N + i - 4 * dirs[best][0]]);
                        double d64 = v - 0.5 * (g[(j + 16 * dirs[best][1]) * N + i + 16 * dirs[best][0]] + g[(j - 16 * dirs[best][1]) * N + i - 16 * dirs[best][0]]);
                        double d128 = v - 0.5 * (g[(j + 32 * dirs[best][1]) * N + i + 32 * dirs[best][0]] + g[(j - 32 * dirs[best][1]) * N + i - 32 * dirs[best][0]]);
                        double rho2 = (d16 / 4.0) / Math.max(1.0E-3, d64 / 16.0);
                        double rho3 = (bestD / 8.0) / Math.max(1.0E-3, d128 / 32.0);
                        rho2 = d64 > 2.0 ? Math.max(-1.0, Math.min(rho2, 3.0)) : 1.0;
                        rho3 = d128 > 2.0 ? Math.max(-1.0, Math.min(rho3, 3.0)) : 1.0;
                        rho2Sum += rho2;
                        rho3Sum += rho3;
                        rounded2 += rho2 < 0.45 ? 1 : 0;
                        rounded3 += rho3 < 0.45 ? 1 : 0;
                        d128Sum += d128;
                    }
                }
                // sub-peaks
                boolean max = true;
                for (int b = -3; b <= 3 && max; b++) {
                    for (int a = -3; a <= 3 && max; a++) {
                        if ((a != 0 || b != 0) && g[(j + b) * N + i + a] > v) {
                            max = false;
                        }
                    }
                }
                if (max) {
                    double ring = 0.0;
                    for (int k = 0; k < 8; k++) {
                        double ang = k * Math.PI / 4.0;
                        ring += g[(j + (int) Math.round(8 * Math.sin(ang))) * N + i + (int) Math.round(8 * Math.cos(ang))];
                    }
                    if (v - ring / 8.0 > 6.0) {
                        subpeaks++;
                    }
                }
            }
        }
        site.smoothConvex = upper > 0 ? (double) convexSmooth / upper : 0.0;
        site.smoothFace = sloping > 0 ? (double) smooth / sloping : 0.0;
        site.evenFace = sloping > 0 ? (double) even / sloping : 0.0;
        site.fineSmooth = sloping > 0 ? (double) fineSmooth / sloping : 0.0;
        site.fineRes50 = median(fineRes);
        site.rough = sloping > 0 ? (double) rough / sloping : 0.0;
        site.meanRes = sloping > 0 ? resSum / sloping : 0.0;
        site.resUpper50 = median(resUpper);
        site.resSlope50 = median(resSlope);
        site.slope50 = median(slopes);
        site.crests = crests;
        site.crestRounded = crests > 0 ? (double) rounded / crests : 0.0;
        site.crestCusped = crests > 0 ? (double) cusped / crests : 0.0;
        site.crestRho = crests > 0 ? rhoSum / crests : 0.0;
        site.crestRho2 = crests > 0 ? rho2Sum / crests : 0.0;
        site.crestRho3 = crests > 0 ? rho3Sum / crests : 0.0;
        site.crestRounded2 = crests > 0 ? (double) rounded2 / crests : 0.0;
        site.crestRounded3 = crests > 0 ? (double) rounded3 / crests : 0.0;
        site.crestD128 = crests > 0 ? d128Sum / crests : 0.0;
        site.crestD8 = crests > 0 ? d8Sum / crests : 0.0;
        site.crestD32 = crests > 0 ? d32Sum / crests : 0.0;
        site.subpeaks = subpeaks / (Math.PI * INNER * INNER / 1.0E6);
        // ---- terms ---------------------------------------------------------------------------
        double[] t = site.terms;
        model.mountainTerms(cx, cz, t);
        // the terms at every corner, and the surface without its gullies, crest teeth and ledges
        double[] ug = new double[N * N];
        float[][] tg = new float[TERM_NAMES.length][N * N];
        double[] tt = new double[TERM_NAMES.length];
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                model.mountainTerms(cx + (i - HALF) * G, cz + (j - HALF) * G, tt);
                for (int k = 0; k < tt.length; k++) {
                    tg[k][j * N + i] = (float) tt[k];
                }
                ug[j * N + i] = g[j * N + i] - tt[1] - tt[2];
            }
        }
        double topU = ug[HALF * N + HALF];
        summitDrops(ug, topU, site.udrop, null);
        site.udrop16 = site.udrop[1];
        site.udrop32 = site.udrop[2];
        site.ucone = (site.udrop[1] / 16.0) / Math.max(1.0E-3, site.udrop[3] / 64.0);
        site.ucone2 = (site.udrop[2] / 32.0) / Math.max(1.0E-3, site.udrop[4] / 128.0);
        site.ucone3 = (site.udrop[3] / 64.0) / Math.max(1.0E-3, site.udrop[5] / 256.0);
        // the uneroded surface split into the range (relief times range shape), the colossal massif
        // and the rest (base profile, valley floors rising with the range, hills, carving): how far
        // each falls from the summit
        double colossal = model.settings().colossalHeight();
        double[] rangePart = new double[N * N];
        double[] massifPart = new double[N * N];
        double[] massPart = new double[N * N];
        for (int k = 0; k < N * N; k++) {
            rangePart[k] = tg[4][k] * tg[5][k];
            massifPart[k] = colossal * tg[10][k] * tg[9][k];
            massPart[k] = ug[k] - rangePart[k] - massifPart[k];
        }
        summitDrops(rangePart, rangePart[HALF * N + HALF], site.rangeDrop, null);
        summitDrops(massifPart, massifPart[HALF * N + HALF], site.massifDrop, null);
        summitDrops(massPart, massPart[HALF * N + HALF], site.massDrop, null);
        double[] uc = crestStats(ug, water, sea);
        site.ucrests = (int) uc[0];
        site.ucrestRounded3 = uc[7];
        site.ucrestRho3 = uc[5];
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8.0;
            int px = HALF + (int) Math.round(8 * Math.cos(a));
            int pz = HALF + (int) Math.round(8 * Math.sin(a));
            site.ringPrevail += tg[12][pz * N + px] / 16.0;
            site.ringCrestW += tg[13][pz * N + px] / 16.0;
            site.ringGully += Math.abs(tg[1][pz * N + px]) / 16.0;
        }
        // over the sloping ground of the disc: how deep the gullies were asked to be and cut, where
        // none are cut, and which part of the range shape holds the ground
        int flanks = 0;
        int noGully = 0;
        int capped = 0;
        int horned = 0;
        int crestHigh = 0;
        int upperN = 0;
        int upperNoGully = 0;
        int upperGentle = 0;
        List<Double> upperSlopeU = new ArrayList<>();
        for (int j = HALF - INNER / G; j <= HALF + INNER / G; j++) {
            for (int i = HALF - INNER / G; i <= HALF + INNER / G; i++) {
                int k = j * N + i;
                if (!inDisc(i, j) || water[k] != ColumnSample.NO_WATER) {
                    continue;
                }
                if (g[k] >= upperFrom) {
                    upperN++;
                    upperNoGully += tg[3][k] < 2.0 ? 1 : 0;
                    double ux = (ug[k + 1] - ug[k - 1]) / (2.0 * G);
                    double uz = (ug[k + N] - ug[k - N]) / (2.0 * G);
                    double us = Math.hypot(ux, uz);
                    upperSlopeU.add(us);
                    upperGentle += us < 0.6 ? 1 : 0;
                }
                double gx = (g[k + 1] - g[k - 1]) / (2.0 * G);
                double gz = (g[k + N] - g[k - N]) / (2.0 * G);
                if (Math.hypot(gx, gz) <= 0.35) {
                    continue;
                }
                flanks++;
                site.flankGullyDepth += tg[3][k];
                site.flankGully += Math.abs(tg[1][k]);
                site.flankLedge += Math.abs(tg[2][k]);
                site.flankPrevail += tg[12][k];
                noGully += tg[3][k] < 2.0 ? 1 : 0;
                capped += troughLowered(tg[17][k], tg[5][k], tg[18][k], tg[21][k]) ? 1 : 0;
                horned += tg[15][k] > tg[16][k] ? 1 : 0;
                crestHigh += tg[13][k] > 0.5 ? 1 : 0;
            }
        }
        if (flanks > 0) {
            site.flankGullyDepth /= flanks;
            site.flankGully /= flanks;
            site.flankLedge /= flanks;
            site.flankPrevail /= flanks;
            site.flankNoGully = (double) noGully / flanks;
            site.flankCapped = (double) capped / flanks;
            site.flankHorn = (double) horned / flanks;
            site.flankCrestW = (double) crestHigh / flanks;
        }
        site.upperNoGully = upperN > 0 ? (double) upperNoGully / upperN : 0.0;
        site.upperGentle = upperN > 0 ? (double) upperGentle / upperN : 0.0;
        site.upperSlopeU50 = median(upperSlopeU);
        // ---- style ---------------------------------------------------------------------------
        ColumnSample s = new ColumnSample();
        model.sampleInto(cx, cz, s);
        site.temperature = s.temperature;
        double weight = Math.max(s.mountain, 1.0E-6);
        String style;
        if (t[9] > 0.1) {
            style = "massif";
        } else if (s.wall / weight > 0.3) {
            style = "wall";
        } else if (t[20] > 0.5) {
            style = "ancient";
        } else if (t[15] >= t[16] && t[15] > 0.05) {
            style = "horn";
        } else {
            style = "crest";
        }
        StringBuilder flags = new StringBuilder();
        if (t[21] > 0.3) {
            flags.append("+meadow");
        }
        if (t[10] < 0.9) {
            flags.append("+coast");
        }
        if (t[6] < 0.6) {
            flags.append("+maskedge");
        }
        if (troughLowered(t[17], t[5], t[18], t[21])) {
            flags.append("+trough");
        }
        if (s.plateau > 0.2) {
            flags.append("+plateau");
        }
        if (s.windswept > 0.2) {
            flags.append("+windswept");
        }
        if (s.temperature < -0.15) {
            flags.append("+snowy");
        }
        if (s.baseTemperature < -0.45) {
            flags.append("+frozen");
        }
        flags.append(t[11] < 250.0 ? "+low" : t[11] > 600.0 ? "+tall" : "+mid");
        site.style = style;
        site.flags = flags.toString();
        // ---- roundness -----------------------------------------------------------------------
        // (the dome is a broad shape: the summit's profile over 32-128 blocks, the uneroded
        // summit under the gullies and teeth over 16-64, and faces smooth at the block scale)
        double a = clamp01((1.1 - site.cone2) / 0.7);
        double b = clamp01((1.0 - site.ucone) / 0.7);
        double c = clamp01((site.fineSmooth - 0.2) / 0.6);
        site.roundness = (a + b + c) / 3.0;
        camera(model, site, g, water);
        return site;
    }

    /**
     * The camera for an in-game view of the summit: 70-150 blocks away on dry ground (8 blocks
     * above it), from where the summit is seen 9-40 degrees up (as close to 25 as possible) with
     * a clear line of sight to it and to the ground half-way down its near side. Looks at a point
     * a quarter of the way from the summit down to the eye, so the summit stands in the upper
     * part of the picture. Minecraft's yaw: 0 looks south (+z), 90 west; pitch: negative up.
     */
    private static void camera(TerrainModel model, Site site, double[] g, double[] water) {
        double top = site.top;
        double bestScore = Double.MAX_VALUE;
        // (first pass: the views described above; second: any view of the summit at all, from
        // higher above the ground if need be)
        for (int pass = 0; pass < 2 && bestScore == Double.MAX_VALUE; pass++) {
            for (int lift : pass == 0 ? new int[]{8, 30} : new int[]{8, 30, 60}) {
                for (int dist : new int[]{110, 130, 150, 90, 70}) {
                    for (int k = 0; k < 24; k++) {
                        double a = k * Math.PI / 12.0;
                        double ox = dist * Math.cos(a);
                        double oz = dist * Math.sin(a);
                        int gi = HALF + (int) Math.round(ox / G);
                        int gj = HALF + (int) Math.round(oz / G);
                        if (water[gj * N + gi] != ColumnSample.NO_WATER) {
                            continue;
                        }
                        double ground = bilinear(g, ox, oz);
                        double eye = ground + lift + 1.62;
                        double elev = Math.toDegrees(Math.atan2(top - eye, dist));
                        if (pass == 0 ? elev < 9.0 || elev > 40.0 : elev < 2.0 || elev > 55.0) {
                            continue;
                        }
                        if (!clear(g, ox, oz, eye, 0.0, 0.0, top + 1.0)
                            || pass == 0 && !clear(g, ox, oz, eye, 0.5 * ox, 0.5 * oz, bilinear(g, 0.5 * ox, 0.5 * oz) + 2.0)) {
                            continue;
                        }
                        double score = Math.abs(elev - 25.0) + Math.abs(dist - 120) * 0.05 + (lift - 8) * 0.15;
                        if (score < bestScore) {
                            bestScore = score;
                            site.camX = site.sx + ox + 0.5;
                            site.camZ = site.sz + oz + 0.5;
                            site.camY = ground + lift;
                            site.camDist = pass == 0 ? dist : -dist;
                            double ty = top - 0.25 * (top - eye);
                            site.yaw = Math.toDegrees(Math.atan2(ox, -oz));
                            site.pitch = -Math.toDegrees(Math.atan2(ty - eye, dist));
                        }
                    }
                }
            }
        }
        if (bestScore == Double.MAX_VALUE) {
            // no view of the summit: from 130 blocks south, 40 blocks above the ground
            double ground = bilinear(g, 0.0, 130.0);
            site.camX = site.sx + 0.5;
            site.camZ = site.sz + 130.5;
            site.camY = Math.max(ground + 40.0, model.seaLevel() + 2.0);
            site.camDist = 0;
            double eye = site.camY + 1.62;
            site.yaw = 180.0;
            site.pitch = -Math.toDegrees(Math.atan2(top - 0.25 * (top - eye) - eye, 130.0));
        }
    }

    /** Whether the ground stays below the straight line from the eye to a point (offsets from the summit). */
    private static boolean clear(double[] g, double ex, double ez, double ey, double tx, double tz, double ty) {
        double len = Math.hypot(tx - ex, tz - ez);
        int steps = (int) Math.ceil(len / 2.0);
        for (int k = 2; k < steps - 1; k++) {
            double f = (double) k / steps;
            double x = ex + f * (tx - ex);
            double z = ez + f * (tz - ez);
            double y = ey + f * (ty - ey);
            if (bilinear(g, x, z) > y - 1.0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a main valley's trough lowered the range shape: the shape after the trough
     * ({@code after}) lies below the shape before it ({@code before}) with only the meadow's pull
     * towards 0.3 applied (see {@code TerrainModel.rangeShape}).
     */
    private static boolean troughLowered(double before, double after, double troughT, double meadow) {
        return troughT < 1.0 && before + (0.3 - before) * 0.85 * meadow - after > 0.02;
    }

    /** Mean (and least) drop from the summit to 16 points 8, 16, 32, 64, 128 and 256 blocks around it. */
    private static void summitDrops(double[] g, double top, double[] drop, double[] least) {
        int[] radii = {8, 16, 32, 64, 128, 256};
        for (int r = 0; r < radii.length; r++) {
            double sum = 0.0;
            double low = Double.MAX_VALUE;
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8.0;
                double d = top - bilinear(g, radii[r] * Math.cos(a), radii[r] * Math.sin(a));
                sum += d;
                low = Math.min(low, d);
            }
            drop[r] = sum / 16.0;
            if (least != null) {
                least[r] = low;
            }
        }
    }

    /**
     * Crest statistics of a grid over the disc (see the class comment): count, rounded and cusped
     * shares and mean rho at 8/32, mean rho at 16/64 and 32/128 and the rounded shares there, and
     * the mean D8, D32 and D128.
     */
    private static double[] crestStats(double[] g, double[] water, int sea) {
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        double[] r = new double[12];
        for (int j = HALF - INNER / G; j <= HALF + INNER / G; j++) {
            for (int i = HALF - INNER / G; i <= HALF + INNER / G; i++) {
                double v = g[j * N + i];
                if (!inDisc(i, j) || v < sea + 4.0 || water[j * N + i] != ColumnSample.NO_WATER) {
                    continue;
                }
                int best = -1;
                double bestD = 3.0;
                for (int k = 0; k < 4; k++) {
                    double d32 = v - across(g, i, j, dirs[k], 8);
                    if (d32 > bestD) {
                        bestD = d32;
                        best = k;
                    }
                }
                if (best < 0) {
                    continue;
                }
                boolean highest = true;
                for (int o = 1; o <= 3 && highest; o++) {
                    highest = g[(j + o * dirs[best][1]) * N + i + o * dirs[best][0]] <= v
                        && g[(j - o * dirs[best][1]) * N + i - o * dirs[best][0]] <= v;
                }
                if (!highest) {
                    continue;
                }
                double d8 = v - across(g, i, j, dirs[best], 2);
                double d16 = v - across(g, i, j, dirs[best], 4);
                double d64 = v - across(g, i, j, dirs[best], 16);
                double d128 = v - across(g, i, j, dirs[best], 32);
                double rho = Math.max(-1.0, Math.min((d8 / 2.0) / (bestD / 8.0), 3.0));
                double rho2 = d64 > 2.0 ? Math.max(-1.0, Math.min((d16 / 4.0) / (d64 / 16.0), 3.0)) : 1.0;
                double rho3 = d128 > 2.0 ? Math.max(-1.0, Math.min((bestD / 8.0) / (d128 / 32.0), 3.0)) : 1.0;
                r[0]++;
                r[1] += rho < 0.45 ? 1 : 0;
                r[2] += rho > 0.7 ? 1 : 0;
                r[3] += rho;
                r[4] += rho2;
                r[5] += rho3;
                r[6] += rho2 < 0.45 ? 1 : 0;
                r[7] += rho3 < 0.45 ? 1 : 0;
                r[8] += d8;
                r[9] += bestD;
                r[10] += d128;
            }
        }
        if (r[0] > 0) {
            for (int k = 1; k < 11; k++) {
                r[k] /= r[0];
            }
        }
        return r;
    }

    /** Mean height of the corners {@code steps} grid steps to both sides of {@code (i, j)} along {@code dir}. */
    private static double across(double[] g, int i, int j, int[] dir, int steps) {
        return 0.5 * (g[(j + steps * dir[1]) * N + i + steps * dir[0]] + g[(j - steps * dir[1]) * N + i - steps * dir[0]]);
    }

    private static boolean inDisc(int i, int j) {
        int di = i - HALF;
        int dj = j - HALF;
        return di * di + dj * dj <= (INNER / G) * (INNER / G);
    }

    /** The surface at an offset (blocks) from the summit, interpolated as the game does. */
    private static double bilinear(double[] g, double ox, double oz) {
        double fx = ox / G + HALF;
        double fz = oz / G + HALF;
        int i = (int) Math.floor(fx);
        int j = (int) Math.floor(fz);
        double tx = fx - i;
        double tz = fz - j;
        double a = g[j * N + i] + tx * (g[j * N + i + 1] - g[j * N + i]);
        double b = g[(j + 1) * N + i] + tx * (g[(j + 1) * N + i + 1] - g[(j + 1) * N + i]);
        return a + tz * (b - a);
    }

    private static double median(List<Double> v) {
        if (v.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(v);
        sorted.sort(null);
        return sorted.get(sorted.size() / 2);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : v > 1.0 ? 1.0 : v;
    }

    /** Least-squares projection onto 1, u, v, u^2, uv, v^2 over the corners within {@code r} steps (u, v in blocks). */
    private static double[][] quadricProjection(int r0) {
        int count = (2 * r0 + 1) * (2 * r0 + 1);
        double[][] phi = new double[count][6];
        int k = 0;
        for (int b = -r0; b <= r0; b++) {
            for (int a = -r0; a <= r0; a++) {
                double u = a * G;
                double v = b * G;
                phi[k++] = new double[]{1.0, u, v, u * u, u * v, v * v};
            }
        }
        double[][] m = new double[6][12];
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                double acc = 0.0;
                for (double[] p : phi) {
                    acc += p[r] * p[c];
                }
                m[r][c] = acc;
            }
            m[r][6 + r] = 1.0;
        }
        for (int c = 0; c < 6; c++) {
            int piv = c;
            for (int r = c + 1; r < 6; r++) {
                if (Math.abs(m[r][c]) > Math.abs(m[piv][c])) {
                    piv = r;
                }
            }
            double[] tmp = m[c];
            m[c] = m[piv];
            m[piv] = tmp;
            double d = m[c][c];
            for (int x = 0; x < 12; x++) {
                m[c][x] /= d;
            }
            for (int r = 0; r < 6; r++) {
                if (r != c) {
                    double f = m[r][c];
                    for (int x = 0; x < 12; x++) {
                        m[r][x] -= f * m[c][x];
                    }
                }
            }
        }
        double[][] proj = new double[6][count];
        for (int r = 0; r < 6; r++) {
            for (int p = 0; p < count; p++) {
                double acc = 0.0;
                for (int c = 0; c < 6; c++) {
                    acc += m[r][6 + c] * phi[p][c];
                }
                proj[r][p] = acc;
            }
        }
        return proj;
    }

    // =========================================================================================
    // Report
    // =========================================================================================

    private static void report(List<Site> all, Path out) throws IOException {
        Files.createDirectories(out);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("sites.tsv")))) {
            w.println(header());
            for (Site s : all) {
                w.println(s.row());
            }
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("details.txt")))) {
            for (Site s : all) {
                w.println(s.detail());
            }
        }
        try (PrintWriter shots = new PrintWriter(Files.newBufferedWriter(out.resolve("shots.txt")));
             PrintWriter areas = new PrintWriter(Files.newBufferedWriter(out.resolve("areas.txt")))) {
            for (Site s : all) {
                shots.println(s.world + "\t" + s.shot());
                areas.printf(Locale.ROOT, "%s\t%d,%d,8%n", s.world, (int) Math.round((s.camX + s.sx) / 2.0), (int) Math.round((s.camZ + s.sz) / 2.0));
            }
        }
        System.out.println(header());
        for (Site s : all) {
            System.out.println(s.row());
        }
        System.out.println();
        summary("all", all);
        Map<String, List<Site>> byStyle = new LinkedHashMap<>();
        for (Site s : all) {
            byStyle.computeIfAbsent(s.style, k -> new ArrayList<>()).add(s);
        }
        for (Map.Entry<String, List<Site>> e : byStyle.entrySet()) {
            summary("style " + e.getKey(), e.getValue());
        }
        for (String flag : new String[]{"+meadow", "+coast", "+maskedge", "+trough", "+snowy", "+frozen", "+low", "+mid", "+tall"}) {
            List<Site> with = all.stream().filter(s -> s.flags.contains(flag)).toList();
            if (!with.isEmpty()) {
                summary("flag " + flag, with);
            }
        }
        System.out.println();
        System.out.println("roundest:");
        all.stream().sorted(Comparator.comparingDouble((Site s) -> -s.roundness)).limit(12)
            .forEach(s -> System.out.printf(Locale.ROOT, "  %2d %-8s %-28s %.2f cone 32/128 %.2f uneroded cone %.2f fineSmooth %.0f%%  %s%n",
                s.id, s.style, s.flags, s.roundness, s.cone2, s.ucone, 100 * s.fineSmooth, s.shot()));
        System.out.println("most pointed:");
        all.stream().sorted(Comparator.comparingDouble((Site s) -> s.roundness)).limit(8)
            .forEach(s -> System.out.printf(Locale.ROOT, "  %2d %-8s %-28s %.2f cone 32/128 %.2f uneroded cone %.2f fineSmooth %.0f%%  %s%n",
                s.id, s.style, s.flags, s.roundness, s.cone2, s.ucone, 100 * s.fineSmooth, s.shot()));
    }

    private static void summary(String label, List<Site> sites) {
        int n = sites.size();
        long round = sites.stream().filter(s -> s.roundness >= ROUND).count();
        long pointed = sites.stream().filter(s -> s.roundness <= POINTED).count();
        System.out.printf(Locale.ROOT,
            "%-16s n=%3d ROUND %3.0f%% POINTED %3.0f%% | roundness %.2f drop16 %.1f drop32 %.1f drop64 %.1f flat4 %.1f cone %.2f udrop16 %.1f"
                + " | smoothConvex %.0f%% smoothFace %.0f%% evenFace %.0f%% rough %.0f%% res %.2f fineSmooth %.0f%% | crests round %.0f%% cusp %.0f%% D32 %.1f subpeaks %.0f"
                + " | flank gd %.1f |g| %.1f prevailTop %.2f | cone 32/128 %.2f 64/256 %.2f crests round 16/64 %.0f%% 32/128 %.0f%%"
                + " | uneroded cone %.2f 32/128 %.2f 64/256 %.2f crests round 32/128 %.0f%% | no gullies: flanks %.0f%% upper %.0f%%"
                + " | upper ground: uneroded slope %.2f, under 0.6 %.0f%%%n",
            label, n, 100.0 * round / n, 100.0 * pointed / n, avg(sites, s -> s.roundness), avg(sites, s -> s.drop[1]), avg(sites, s -> s.drop[2]),
            avg(sites, s -> s.drop[3]), avg(sites, s -> s.flat4), avg(sites, s -> s.cone), avg(sites, s -> s.udrop16),
            100 * avg(sites, s -> s.smoothConvex), 100 * avg(sites, s -> s.smoothFace), 100 * avg(sites, s -> s.evenFace),
            100 * avg(sites, s -> s.rough), avg(sites, s -> s.meanRes), 100 * avg(sites, s -> s.fineSmooth), 100 * avg(sites, s -> s.crestRounded), 100 * avg(sites, s -> s.crestCusped),
            avg(sites, s -> s.crestD32), avg(sites, s -> s.subpeaks), avg(sites, s -> s.flankGullyDepth), avg(sites, s -> s.flankGully),
            avg(sites, s -> s.terms[12]), avg(sites, s -> s.cone2), avg(sites, s -> s.cone3), 100 * avg(sites, s -> s.crestRounded2),
            100 * avg(sites, s -> s.crestRounded3), avg(sites, s -> s.ucone), avg(sites, s -> s.ucone2), avg(sites, s -> s.ucone3),
            100 * avg(sites, s -> s.ucrestRounded3), 100 * avg(sites, s -> s.flankNoGully), 100 * avg(sites, s -> s.upperNoGully),
            avg(sites, s -> s.upperSlopeU50), 100 * avg(sites, s -> s.upperGentle));
    }

    private static double avg(List<Site> sites, java.util.function.ToDoubleFunction<Site> f) {
        return sites.stream().mapToDouble(f).average().orElse(0.0);
    }
}
