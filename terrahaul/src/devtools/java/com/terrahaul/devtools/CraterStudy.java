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
import java.util.Arrays;
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
        int[] parts = components(liquid);
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
    private static int[] components(double[][] liquid) {
        int[] label = new int[N * N];
        int pools = 0;
        int islands = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int next = 1;
        for (int start = 0; start < N * N; start++) {
            if (label[start] != 0) {
                continue;
            }
            boolean wet = !Double.isNaN(liquid[start / N][start % N]);
            label[start] = next;
            queue.add(start);
            int size = 0;
            boolean border = false;
            while (!queue.isEmpty()) {
                int k = queue.poll();
                size++;
                int i = k % N;
                int j = k / N;
                border |= i == 0 || j == 0 || i == N - 1 || j == N - 1;
                int[] nb = {i > 0 ? k - 1 : -1, i < N - 1 ? k + 1 : -1, j > 0 ? k - N : -1, j < N - 1 ? k + N : -1};
                for (int n : nb) {
                    if (n >= 0 && label[n] == 0 && !Double.isNaN(liquid[n / N][n % N]) == wet) {
                        label[n] = next;
                        queue.add(n);
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
