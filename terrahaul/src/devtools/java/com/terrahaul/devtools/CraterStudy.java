package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.VolcanoZones;
import com.terrahaul.worldgen.terrain.WaterBody;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Close study of one volcano's crater, run through {@code VolcanoStudy craters}: a contoured top
 * view of the crater and its rim, a view looking down into the crater from the south, and two
 * charts around the crater (the crest height of the rim against the lake level, and the distance
 * of the crest and of the shore from the centre).
 * <p>
 * Water and lava are filled the way the water solver fills them: a block column holds liquid when
 * one of its four model corners lies inside the crater's body and the column's ground is below
 * that body's level. The printed numbers judge the shape: how much the crest radius, crest height
 * and shore radius vary around the crater (a perfectly round crater has 0), how many separate
 * pools and islands the lake has, and the freeboard, the lowest crest above the lake (it must be
 * positive, or the lake would spill).
 */
final class CraterStudy {
    private static final int N = 640;
    private static final int VIEW_W = 900;
    private static final int CHART_H = 230;
    private static final int RAYS = 720;

    private CraterStudy() {
    }

    /**
     * Surveys every crater whose volcano lies within {@code half} blocks of the origin on the
     * crater's own surface ({@link TerrainModel#volcanoSurface}, without the terrain around it),
     * one block apart, and prints a line per crater and a summary: the steepest step between
     * neighbouring blocks (and how many steps exceed 3 and 4 blocks), for crater lakes and lava
     * pools the depth, the freeboard below the lowest crest, the pools, the islands and how high
     * the highest stands above the liquid, and how much the outer shore's distance from the
     * centre varies (0 for a disc), with the crater's own account of its shape (outline scale,
     * wall share, how far the slope budget raised the floor).
     */
    static void survey(TerrainModel model, long seed, int half) {
        List<TerrainModel.VolcanoSite> sites = new ArrayList<>();
        for (int x = -half; x < half; x += 2000) {
            for (int z = -half; z < half; z += 2000) {
                TerrainModel.VolcanoSite v = model.nearestVolcano(x, z, 2000);
                if (v != null && Math.abs(v.x) <= half && Math.abs(v.z) <= half && sites.stream().noneMatch(o -> o.x == v.x && o.z == v.z)) {
                    sites.add(v);
                }
            }
        }
        sites.sort((a, b) -> a.x != b.x ? Double.compare(a.x, b.x) : Double.compare(a.z, b.z));
        int steep3 = 0;
        int steep4 = 0;
        int liquids = 0;
        int lakes = 0;
        int above = 0;
        int twoPools = 0;
        List<Double> depths = new ArrayList<>();
        List<Double> shores = new ArrayList<>();
        double maxShare = 0.0;
        double worstStep = 0.0;
        for (TerrainModel.VolcanoSite v : sites) {
            double[] r = surveyOne(model, v);
            String kind = v.waterLevel != ColumnSample.NO_WATER ? "lake" : v.lavaLevel != ColumnSample.NO_WATER ? "lava" : "dry";
            String account = v.describeCrater();
            System.out.printf(Locale.ROOT, "%d %.0f,%.0f %s r0 %.0f depth %.0f reach %.0f | step max %.2f at %.0f,%.0f, >3: %d, >4: %d | %s | %s%n",
                seed, v.x, v.z, kind, v.craterRadius, v.craterDepth, v.craterReach, r[0], r[1], r[2], (long) r[3], (long) r[4],
                Double.isNaN(r[5]) ? "no liquid" : String.format(Locale.ROOT, "depth %.0f, freeboard %.1f, pools %d, islands %d (highest %.1f above), shore cv %.3f",
                    r[5], r[6], (long) r[7], (long) r[8], r[9], r[10]), account);
            steep3 += r[3] > 0 ? 1 : 0;
            steep4 += r[4] > 0 ? 1 : 0;
            worstStep = Math.max(worstStep, r[0]);
            int at = account.indexOf("wall share ");
            maxShare = Math.max(maxShare, Double.parseDouble(account.substring(at + 11).split("\\.\\.")[1].split(",")[0]));
            if (!Double.isNaN(r[5])) {
                liquids++;
                lakes += kind.equals("lake") ? 1 : 0;
                above += r[9] >= 2.0 ? 1 : 0;
                twoPools += r[7] >= 2 ? 1 : 0;
                depths.add(r[5]);
                shores.add(r[10]);
            }
        }
        depths.sort(null);
        shores.sort(null);
        System.out.printf(Locale.ROOT, "seed %d: %d craters, %d with liquid (%d lakes); steepest step %.2f; craters with steps over 3 blocks %d, over 4 blocks %d; "
                + "widest wall share %.2f; liquid craters with an island or ridge 2+ blocks above the liquid %d, with two pools or more %d; "
                + "liquid depth median %.0f (%.0f..%.0f); shore cv median %.3f (%.3f..%.3f)%n",
            seed, sites.size(), liquids, lakes, worstStep, steep3, steep4, maxShare, above, twoPools,
            median(depths), depths.isEmpty() ? Double.NaN : depths.get(0), depths.isEmpty() ? Double.NaN : depths.get(depths.size() - 1),
            median(shores), shores.isEmpty() ? Double.NaN : shores.get(0), shores.isEmpty() ? Double.NaN : shores.get(shores.size() - 1));
    }

    private static double median(List<Double> a) {
        return a.isEmpty() ? Double.NaN : a.get(a.size() / 2);
    }

    /**
     * One crater of the survey: {steepest step, its x, z, steps over 3, steps over 4, liquid
     * depth, freeboard, pools, islands, highest island above the liquid, shore cv}, the liquid
     * entries NaN for a dry crater.
     */
    private static double[] surveyOne(TerrainModel model, TerrainModel.VolcanoSite v) {
        int reach = (int) Math.ceil(v.craterReach) + 1;
        int w = 2 * reach + 1;
        int x0 = (int) Math.floor(v.x) - reach;
        int z0 = (int) Math.floor(v.z) - reach;
        double[] h = new double[w * w];
        boolean[] inside = new boolean[w * w];
        IntStream.range(0, w).parallel().forEach(j -> {
            double[] out = new double[8];
            for (int i = 0; i < w; i++) {
                h[j * w + i] = model.volcanoSurface(v, x0 + i, z0 + j, out);
                inside[j * w + i] = out[0] >= 0.0;
            }
        });
        double worst = 0.0;
        int wx = 0;
        int wz = 0;
        long over3 = 0;
        long over4 = 0;
        double r2 = v.craterReach * v.craterReach;
        for (int j = 0; j < w; j++) {
            for (int i = 0; i < w; i++) {
                double dx = x0 + i - v.x;
                double dz = z0 + j - v.z;
                if (dx * dx + dz * dz >= r2) {
                    continue;
                }
                double d = 0.0;
                if (i > 0) {
                    d = Math.abs(h[j * w + i] - h[j * w + i - 1]);
                }
                if (j > 0) {
                    d = Math.max(d, Math.abs(h[j * w + i] - h[(j - 1) * w + i]));
                }
                if (d > worst) {
                    worst = d;
                    wx = x0 + i;
                    wz = z0 + j;
                }
                over3 += d > 3.0 ? 1 : 0;
                over4 += d > 4.0 ? 1 : 0;
            }
        }
        double level = v.waterLevel != ColumnSample.NO_WATER ? v.waterLevel : v.lavaLevel;
        double[] result = {worst, wx, wz, over3, over4, Double.NaN, Double.NaN, 0, 0, Double.NaN, Double.NaN};
        if (level == ColumnSample.NO_WATER) {
            return result;
        }
        // the liquid as the solver fills the crater: columns inside the crater whose ground is below the top liquid block
        double surface = level - 1.0;
        double[][] liquid = new double[w][w];
        double bottom = Double.MAX_VALUE;
        for (int j = 0; j < w; j++) {
            for (int i = 0; i < w; i++) {
                boolean wet = inside[j * w + i] && h[j * w + i] < surface;
                liquid[j][i] = wet ? level : Double.NaN;
                if (wet) {
                    bottom = Math.min(bottom, h[j * w + i]);
                }
            }
        }
        int[] parts = components(liquid, w);
        double highest = highestIsland(liquid, h, w);
        highest = highest == -Double.MAX_VALUE ? Double.NaN : highest - surface;
        double[] shore = new double[RAYS];
        for (int a = 0; a < RAYS; a++) {
            double ang = a * Math.PI * 2.0 / RAYS;
            shore[a] = Double.NaN;
            for (double r = 0.0; r < reach - 1; r += 1.0) {
                int i = (int) Math.floor(v.x + Math.cos(ang) * r) - x0;
                int j = (int) Math.floor(v.z + Math.sin(ang) * r) - z0;
                if (!Double.isNaN(liquid[j][i])) {
                    shore[a] = r;
                }
            }
        }
        double rimLow = Double.MAX_VALUE;
        for (int a = 0; a < 4 * RAYS; a++) {
            double ang = a * Math.PI * 2.0 / (4 * RAYS);
            double rim = model.craterRimRadius(v, (int) Math.round(v.x + Math.cos(ang) * (v.craterReach + 2.0)), (int) Math.round(v.z + Math.sin(ang) * (v.craterReach + 2.0)));
            double[] out = new double[8];
            rimLow = Math.min(rimLow, model.volcanoSurface(v, (int) Math.round(v.x + Math.cos(ang) * rim), (int) Math.round(v.z + Math.sin(ang) * rim), out));
        }
        result[5] = surface - bottom;
        result[6] = rimLow - surface;
        result[7] = parts[0];
        result[8] = parts[1];
        result[9] = highest;
        result[10] = cv(shore);
        return result;
    }

    /** Height of the highest dry ground not connected to the border of the area (islands). */
    private static double highestIsland(double[][] liquid, double[] h, int n) {
        int[] label = new int[n * n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        double best = -Double.MAX_VALUE;
        for (int start = 0; start < n * n; start++) {
            if (label[start] != 0 || !Double.isNaN(liquid[start / n][start % n])) {
                continue;
            }
            label[start] = 1;
            queue.add(start);
            boolean border = false;
            double top = -Double.MAX_VALUE;
            int size = 0;
            while (!queue.isEmpty()) {
                int k = queue.poll();
                size++;
                int i = k % n;
                int j = k / n;
                top = Math.max(top, h[k]);
                border |= i == 0 || j == 0 || i == n - 1 || j == n - 1;
                int[] nb = {i > 0 ? k - 1 : -1, i < n - 1 ? k + 1 : -1, j > 0 ? k - n : -1, j < n - 1 ? k + n : -1};
                for (int m : nb) {
                    if (m >= 0 && label[m] == 0 && Double.isNaN(liquid[m / n][m % n])) {
                        label[m] = 1;
                        queue.add(m);
                    }
                }
            }
            if (!border && size >= 6) {
                best = Math.max(best, top);
            }
        }
        return best;
    }

    static void study(TerrainModel model, TerrainModel.VolcanoSite v, File out) throws Exception {
        // the same window before and after a change of the crater shape, so views compare 1:1
        double reach = 1.75 * v.craterRadius + 30.0;
        double span = 2.0 * reach;
        double bpp = span / N;
        double minX = v.x - reach;
        double minZ = v.z - reach;
        int sl = model.seaLevel();
        double[][] h = new double[N][N];
        // the same ground at the pixel's exact position, for shading without stripes where pixels
        // and blocks do not line up
        double[][] smooth = new double[N][N];
        // liquid level per pixel (exclusive top, as ColumnSample#bodyLevel), or NaN when dry
        double[][] liquid = new double[N][N];
        boolean[][] lava = new boolean[N][N];
        int[][] color = new int[N][N];
        IntStream.range(0, N).parallel().forEach(j -> {
            ColumnSample s = new ColumnSample();
            VolcanoZones.Zone zone = new VolcanoZones.Zone();
            for (int i = 0; i < N; i++) {
                int x = (int) Math.floor(minX + (i + 0.5) * bpp);
                int z = (int) Math.floor(minZ + (j + 0.5) * bpp);
                double hh = model.interpolatedHeight(x, z);
                h[j][i] = hh;
                smooth[j][i] = heightAt(model, minX + (i + 0.5) * bpp, minZ + (j + 0.5) * bpp);
                double level = Double.NaN;
                boolean isLava = false;
                for (int c = 0; c < 4; c++) {
                    ColumnSample cs = model.sample((x & ~3) + (c & 1) * 4, (z & ~3) + (c >> 1) * 4);
                    if (cs.bodyInside && (cs.bodyKind == WaterBody.CRATER || cs.bodyKind == WaterBody.LAVA)
                        && (Double.isNaN(level) || cs.bodyLevel > level)) {
                        level = cs.bodyLevel;
                        isLava = cs.bodyKind == WaterBody.LAVA;
                    }
                }
                if (!Double.isNaN(level) && Math.ceil(hh) < level) {
                    liquid[j][i] = level;
                    lava[j][i] = isLava;
                } else {
                    liquid[j][i] = Double.NaN;
                }
                model.sampleInto(x & ~3, z & ~3, s);
                VolcanoZones.compute(model, x, z, zone);
                color[j][i] = VolcanoStudy.baseColor(model, s, zone, x, (int) Math.floor(hh), z, hh, sl);
            }
        });

        // ---- top view: hill shade, contours every few blocks, liquid shaded by depth ---------------
        double minH = Double.MAX_VALUE;
        double maxH = -Double.MAX_VALUE;
        for (double[] row : h) {
            for (double x : row) {
                minH = Math.min(minH, x);
                maxH = Math.max(maxH, x);
            }
        }
        int contour = maxH - minH > 240 ? 16 : 8;
        int[][] shaded = new int[N][N];
        BufferedImage top = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                int c;
                if (!Double.isNaN(liquid[j][i])) {
                    double depth = liquid[j][i] - h[j][i];
                    double k = Math.max(0.45, 1.0 - depth / 60.0);
                    c = lava[j][i] ? VolcanoStudy.rgb(240, 110 * k + 20, 20) : VolcanoStudy.rgb(60 * k, 120 * k, 205 * k + 30);
                } else {
                    double hl = h[j][Math.max(0, i - 1)];
                    double hu = h[Math.max(0, j - 1)][i];
                    double sl2 = smooth[j][Math.max(0, i - 1)];
                    double su = smooth[Math.max(0, j - 1)][i];
                    double shade = Math.max(0.5, Math.min(1.4, 1.0 + ((smooth[j][i] - sl2) + (smooth[j][i] - su)) / bpp * 0.35));
                    c = VolcanoStudy.scale(color[j][i], shade);
                    int band = (int) Math.floor(h[j][i] / contour);
                    if (i > 0 && j > 0 && (band != (int) Math.floor(hl / contour) || band != (int) Math.floor(hu / contour))) {
                        c = VolcanoStudy.scale(c, 0.62);
                    }
                }
                shaded[j][i] = c;
                top.setRGB(i, j, c);
            }
        }

        // ---- rays from the centre: the crest of the rim and the outer shore ----------------------
        double[] crestH = new double[RAYS];
        double[] crestR = new double[RAYS];
        double[] shoreR = new double[RAYS];
        double rMax = reach - 2.0;
        IntStream.range(0, RAYS).parallel().forEach(a -> {
            double ang = a * Math.PI * 2.0 / RAYS;
            double best = -Double.MAX_VALUE;
            double bestR = 0.0;
            double shore = Double.NaN;
            for (double r = 0.0; r < rMax; r += 1.0) {
                int x = (int) Math.floor(v.x + Math.cos(ang) * r);
                int z = (int) Math.floor(v.z + Math.sin(ang) * r);
                double hh = model.interpolatedHeight(x, z);
                if (r > 0.3 * v.craterRadius && hh > best) {
                    best = hh;
                    bestR = r;
                }
                int i = (int) ((x - minX) / bpp);
                int j = (int) ((z - minZ) / bpp);
                if (i >= 0 && j >= 0 && i < N && j < N && !Double.isNaN(liquid[j][i])) {
                    shore = r;
                }
            }
            crestH[a] = best;
            crestR[a] = bestR;
            shoreR[a] = shore;
        });
        double level = Double.NaN;
        boolean lavaLake = false;
        long liquidPx = 0;
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                if (!Double.isNaN(liquid[j][i])) {
                    level = Double.isNaN(level) ? liquid[j][i] : Math.max(level, liquid[j][i]);
                    lavaLake |= lava[j][i];
                    liquidPx++;
                }
            }
        }
        int lowest = 0;
        for (int a = 1; a < RAYS; a++) {
            if (crestH[a] < crestH[lowest]) {
                lowest = a;
            }
        }
        int[] parts = components(liquid, N);
        String stats = String.format(Locale.ROOT,
            "crater r %.0f depth %.0f | crest radius cv %.3f (min/max %.2f) | crest height %.0f..%.0f (sd %.1f) | %s | shore radius cv %s | pools %d islands %d",
            v.craterRadius, v.craterDepth, cv(crestR), min(crestR) / max(crestR), min(crestH), max(crestH), sd(crestH),
            Double.isNaN(level) ? "no lake" : String.format(Locale.ROOT, "%s level %.0f, freeboard %.1f, area %.0f blocks2",
                lavaLake ? "lava" : "lake", level - 1.0, min(crestH) - (level - 1.0), liquidPx * bpp * bpp),
            Double.isNaN(level) ? "-" : String.format(Locale.ROOT, "%.3f", cv(shoreR)), parts[0], parts[1]);
        System.out.println(out.getName() + String.format(Locale.ROOT, " (volcano %.0f %.0f h %.0f %s): ", v.x, v.z, v.height,
            v.lava ? "lava" : v.waterLevel != ColumnSample.NO_WATER ? "lake" : "dry") + stats);

        // mark the lowest point of the crest on the top view
        Graphics2D tg = top.createGraphics();
        tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double la = lowest * Math.PI * 2.0 / RAYS;
        int li = (int) ((v.x + Math.cos(la) * crestR[lowest] - minX) / bpp);
        int lj = (int) ((v.z + Math.sin(la) * crestR[lowest] - minZ) / bpp);
        tg.setColor(Color.RED);
        tg.setStroke(new BasicStroke(2f));
        tg.drawOval(li - 7, lj - 7, 14, 14);
        tg.dispose();

        BufferedImage view = view(smooth, color, liquid, lava, bpp);
        BufferedImage img = new BufferedImage(N + VIEW_W, N + CHART_H + 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(top, 0, 0, null);
        g.drawImage(view, N, 0, null);
        int w2 = img.getWidth() / 2;
        chart(g, 0, N + 4, w2 - 8, CHART_H - 8, crestH, Double.isNaN(level) ? Double.NaN : level - 1.0, lavaLake,
            "rim crest height around the crater (0 = east, clockwise), line: liquid surface");
        double[][] radii = {crestR, shoreR};
        chartRadii(g, w2, N + 4, w2 - 8, CHART_H - 8, radii, v.craterRadius,
            "distance from the centre: rim crest (black), outer shore (blue), dashed: crater radius");
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        g.drawString(String.format(Locale.ROOT, "volcano at %.0f %.0f  height %.0f  radius %.0f  %s  |  top view %.0f blocks wide, contours every %d blocks, red: lowest crest",
            v.x, v.z, v.height, v.radius, v.lava ? "lava crater" : v.waterLevel != ColumnSample.NO_WATER ? "crater lake" : "dry crater", span, contour),
            6, img.getHeight() - 24);
        g.drawString(stats, 6, img.getHeight() - 8);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    /** The model's interpolated ground at a fractional position, as the noise chunk interpolates it. */
    private static double heightAt(TerrainModel model, double x, double z) {
        int x0 = ((int) Math.floor(x)) & ~3;
        int z0 = ((int) Math.floor(z)) & ~3;
        double fx = (x - x0) / 4.0;
        double fz = (z - z0) / 4.0;
        double h00 = model.sample(x0, z0).height;
        double h10 = model.sample(x0 + 4, z0).height;
        double h01 = model.sample(x0, z0 + 4).height;
        double h11 = model.sample(x0 + 4, z0 + 4).height;
        double a = h00 + (h10 - h00) * fx;
        double b = h01 + (h11 - h01) * fx;
        return a + (b - a) * fz;
    }

    /** Liquid components larger than a few pixels, and dry components not touching the border (islands). */
    private static int[] components(double[][] liquid, int n) {
        int[] label = new int[n * n];
        int pools = 0;
        int islands = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int next = 1;
        for (int start = 0; start < n * n; start++) {
            if (label[start] != 0) {
                continue;
            }
            boolean wet = !Double.isNaN(liquid[start / n][start % n]);
            label[start] = next;
            queue.add(start);
            int size = 0;
            boolean border = false;
            while (!queue.isEmpty()) {
                int k = queue.poll();
                size++;
                int i = k % n;
                int j = k / n;
                border |= i == 0 || j == 0 || i == n - 1 || j == n - 1;
                int[] nb = {i > 0 ? k - 1 : -1, i < n - 1 ? k + 1 : -1, j > 0 ? k - n : -1, j < n - 1 ? k + n : -1};
                for (int m : nb) {
                    if (m >= 0 && label[m] == 0 && !Double.isNaN(liquid[m / n][m % n]) == wet) {
                        label[m] = next;
                        queue.add(m);
                    }
                }
            }
            next++;
            if (wet && size >= 12) {
                pools++;
            } else if (!wet && !border && size >= 6) {
                islands++;
            }
        }
        return new int[]{pools, islands};
    }

    /** Looking down into the crater from high above its southern flank. */
    private static BufferedImage view(double[][] h, int[][] color, double[][] liquid, boolean[][] lava, double bpp) {
        int imgH = N;
        BufferedImage img = new BufferedImage(VIEW_W, imgH, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < imgH; y++) {
            double t = y / (double) imgH;
            int sky = VolcanoStudy.rgb(120 + 80 * t, 165 + 60 * t, 235);
            for (int x = 0; x < VIEW_W; x++) {
                img.setRGB(x, y, sky);
            }
        }
        double top = -Double.MAX_VALUE;
        for (double[] row : h) {
            for (double x : row) {
                top = Math.max(top, x);
            }
        }
        double camX = N / 2.0;
        double camZ = N * 1.18;
        double camH = top + 0.32 * N * bpp;
        double fov = Math.toRadians(58);
        double focal = (VIEW_W / 2.0) / Math.tan(fov / 2.0);
        // pitch down: the centre of the crater lands a little below the middle of the image
        double centreDist = camZ - N / 2.0;
        double horizon = 0.55 * imgH - (camH - h[N / 2][N / 2]) / (centreDist * bpp) * focal;
        int[] ybuffer = new int[VIEW_W];
        Arrays.fill(ybuffer, imgH);
        for (double dist = camZ - N + 1; dist < camZ - 1; dist += Math.max(0.35, dist * 0.002)) {
            double halfW = Math.tan(fov / 2) * dist;
            double zf = camZ - dist;
            for (int sx = 0; sx < VIEW_W; sx++) {
                double xf = camX - halfW + (2.0 * halfW) * sx / VIEW_W;
                int ix = (int) xf;
                int iz = (int) zf;
                if (ix < 1 || ix >= N || iz < 1 || iz >= N) {
                    continue;
                }
                double surface = Double.isNaN(liquid[iz][ix]) ? h[iz][ix] : liquid[iz][ix] - 1.0;
                int sy = (int) ((camH - surface) / (dist * bpp) * focal + horizon);
                if (sy < ybuffer[sx]) {
                    int c = color[iz][ix];
                    if (!Double.isNaN(liquid[iz][ix])) {
                        double k = Math.max(0.45, 1.0 - (liquid[iz][ix] - h[iz][ix]) / 60.0);
                        c = lava[iz][ix] ? VolcanoStudy.rgb(240, 110 * k + 20, 20) : VolcanoStudy.rgb(60 * k, 120 * k, 205 * k + 30);
                    } else {
                        // light from the south-west, so walls facing the camera are lit
                        double gx = h[iz][ix] - h[iz][ix - 1];
                        double gz = h[iz][ix] - h[iz - 1][ix];
                        double shade = Math.max(0.45, Math.min(1.45, 1.0 + (gx * 0.4 + gz * 0.6) / bpp * 0.6));
                        c = VolcanoStudy.scale(c, shade);
                    }
                    for (int y = Math.max(0, sy); y < ybuffer[sx] && y < imgH; y++) {
                        img.setRGB(sx, y, c);
                    }
                    ybuffer[sx] = Math.max(0, sy);
                }
            }
        }
        return img;
    }

    private static void chart(Graphics2D g, int x0, int y0, int w, int hh, double[] values, double line, boolean lava, String title) {
        double lo = Math.min(min(values), Double.isNaN(line) ? Double.MAX_VALUE : line) - 4.0;
        double hi = max(values) + 4.0;
        frame(g, x0, y0, w, hh, title, lo, hi);
        int top = y0 + 18;
        int ph = hh - 22;
        if (!Double.isNaN(line)) {
            g.setColor(lava ? new Color(230, 100, 20) : new Color(40, 90, 210));
            int y = (int) (top + ph * (1.0 - (line - lo) / (hi - lo)));
            g.drawLine(x0 + 40, y, x0 + w, y);
        }
        g.setColor(Color.BLACK);
        polyline(g, values, x0 + 40, top, w - 40, ph, lo, hi);
    }

    private static void chartRadii(Graphics2D g, int x0, int y0, int w, int hh, double[][] series, double radius, String title) {
        double lo = 0.0;
        double hi = 0.0;
        for (double[] s : series) {
            hi = Math.max(hi, max(s));
        }
        hi = Math.max(hi, radius) * 1.08;
        frame(g, x0, y0, w, hh, title, lo, hi);
        int top = y0 + 18;
        int ph = hh - 22;
        g.setColor(Color.GRAY);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4f, 4f}, 0f));
        int y = (int) (top + ph * (1.0 - (radius - lo) / (hi - lo)));
        g.drawLine(x0 + 40, y, x0 + w, y);
        g.setStroke(new BasicStroke(1f));
        g.setColor(Color.BLACK);
        polyline(g, series[0], x0 + 40, top, w - 40, ph, lo, hi);
        g.setColor(new Color(40, 90, 210));
        polyline(g, series[1], x0 + 40, top, w - 40, ph, lo, hi);
    }

    private static void frame(Graphics2D g, int x0, int y0, int w, int hh, String title, double lo, double hi) {
        g.setColor(new Color(245, 245, 245));
        g.fillRect(x0 + 40, y0 + 18, w - 40, hh - 22);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString(title, x0 + 40, y0 + 13);
        g.drawString(String.format(Locale.ROOT, "%.0f", hi), x0 + 2, y0 + 28);
        g.drawString(String.format(Locale.ROOT, "%.0f", lo), x0 + 2, y0 + hh - 4);
    }

    private static void polyline(Graphics2D g, double[] values, int x0, int y0, int w, int hh, double lo, double hi) {
        int px = -1;
        int py = -1;
        for (int a = 0; a < values.length; a++) {
            if (Double.isNaN(values[a])) {
                px = -1;
                continue;
            }
            int x = x0 + (int) ((long) a * w / values.length);
            int y = (int) (y0 + hh * (1.0 - (values[a] - lo) / (hi - lo)));
            if (px >= 0) {
                g.drawLine(px, py, x, y);
            }
            px = x;
            py = y;
        }
    }

    private static double min(double[] a) {
        double m = Double.MAX_VALUE;
        for (double x : a) {
            if (!Double.isNaN(x)) {
                m = Math.min(m, x);
            }
        }
        return m;
    }

    private static double max(double[] a) {
        double m = -Double.MAX_VALUE;
        for (double x : a) {
            if (!Double.isNaN(x)) {
                m = Math.max(m, x);
            }
        }
        return m;
    }

    private static double mean(double[] a) {
        double s = 0.0;
        int n = 0;
        for (double x : a) {
            if (!Double.isNaN(x)) {
                s += x;
                n++;
            }
        }
        return n == 0 ? Double.NaN : s / n;
    }

    private static double sd(double[] a) {
        double m = mean(a);
        double s = 0.0;
        int n = 0;
        for (double x : a) {
            if (!Double.isNaN(x)) {
                s += (x - m) * (x - m);
                n++;
            }
        }
        return n == 0 ? Double.NaN : Math.sqrt(s / n);
    }

    /** Coefficient of variation: 0 for a perfect circle. */
    private static double cv(double[] a) {
        return sd(a) / mean(a);
    }
}
