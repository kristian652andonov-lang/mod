package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Development tool: renders the terrain model to shaded relief maps without starting Minecraft.
 * Usage: {@code MapRenderer <seed> <centerX> <centerZ> <sizeBlocks> <blocksPerPixel> <outFile> [mode]}
 * where mode is {@code relief}, {@code climate}, {@code class} or {@code perspective}.
 */
public final class MapRenderer {
    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        int cx = Integer.parseInt(args[1]);
        int cz = Integer.parseInt(args[2]);
        int size = Integer.parseInt(args[3]);
        int bpp = Integer.parseInt(args[4]);
        String out = args[5];
        String mode = args.length > 6 ? args[6] : "relief";
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        int px = size / bpp;
        int minX = cx - size / 2;
        int minZ = cz - size / 2;
        long t0 = System.nanoTime();
        ColumnSample[][] grid = new ColumnSample[px][px];
        IntStream.range(0, px).parallel().forEach(j -> {
            for (int i = 0; i < px; i++) {
                ColumnSample s = new ColumnSample();
                model.sampleInto(minX + i * bpp, minZ + j * bpp, s);
                grid[j][i] = s;
            }
        });
        long t1 = System.nanoTime();
        System.out.printf(Locale.ROOT, "sampled %d columns in %.1f ms (%.2f us/column)%n", px * px, (t1 - t0) / 1e6, (t1 - t0) / 1e3 / (px * px));
        stats(grid, model.seaLevel());
        BufferedImage img = switch (mode) {
            case "perspective" -> Perspective.render(grid, bpp, model.seaLevel());
            case "section" -> section(grid, bpp, model.seaLevel());
            default -> flat(grid, bpp, model.seaLevel(), mode);
        };
        ImageIO.write(img, "png", new File(out));
        System.out.println("wrote " + out);
    }

    private static void stats(ColumnSample[][] g, int sl) {
        int n = 0;
        int land = 0;
        int mountain = 0;
        int river = 0;
        int lake = 0;
        int canyon = 0;
        int volcano = 0;
        int plateau = 0;
        int swamp = 0;
        double max = -1e9;
        double min = 1e9;
        int[] hist = new int[12];
        int[] eHist = new int[7];
        double[] eEdges = {-0.78, -0.375, -0.2225, 0.05, 0.45, 0.55};
        for (ColumnSample[] row : g) {
            for (ColumnSample s : row) {
                n++;
                if (s.height >= sl) {
                    land++;
                    int b = (int) Math.min(11, Math.max(0, (s.height - sl) / 100.0 * 1.0));
                    hist[b]++;
                    int e = 0;
                    while (e < 6 && s.erosion > eEdges[e]) {
                        e++;
                    }
                    eHist[e]++;
                }
                if (s.mountain > 0.5) mountain++;
                if (s.river > 0) river++;
                if (s.lake > 0) lake++;
                if (s.canyon > 0.3) canyon++;
                if (s.volcano > 0.3) volcano++;
                if (s.plateau > 0.5) plateau++;
                if (s.swamp > 0.5) swamp++;
                max = Math.max(max, s.height);
                min = Math.min(min, s.height);
            }
        }
        System.out.printf(Locale.ROOT, "land %.1f%% mountain %.1f%% river %.2f%% lake %.2f%% canyon %.2f%% volcano %.2f%% plateau %.1f%% swamp %.1f%% | height %.0f..%.0f%n",
            100.0 * land / n, 100.0 * mountain / n, 100.0 * river / n, 100.0 * lake / n, 100.0 * canyon / n, 100.0 * volcano / n, 100.0 * plateau / n, 100.0 * swamp / n, min, max);
        StringBuilder sb = new StringBuilder("land height histogram (per 100 above sea): ");
        for (int i = 0; i < hist.length; i++) {
            sb.append(String.format(Locale.ROOT, "%d:%.1f%% ", i, land == 0 ? 0 : 100.0 * hist[i] / land));
        }
        System.out.println(sb);
        sb = new StringBuilder("land erosion bands E0..E6: ");
        for (int i = 0; i < eHist.length; i++) {
            sb.append(String.format(Locale.ROOT, "E%d:%.1f%% ", i, land == 0 ? 0 : 100.0 * eHist[i] / land));
        }
        System.out.println(sb);
        double[] cs = new double[n];
        double[] es = new double[n];
        double[] hs = new double[n];
        int k = 0;
        for (ColumnSample[] row : g) {
            for (ColumnSample s : row) {
                cs[k] = s.rawContinentalness;
                es[k] = s.erosion;
                hs[k] = s.height;
                k++;
            }
        }
        java.util.Arrays.sort(cs);
        java.util.Arrays.sort(es);
        java.util.Arrays.sort(hs);
        sb = new StringBuilder("percentiles (rawC / E / height): ");
        for (double q : new double[]{0.01, 0.05, 0.25, 0.5, 0.75, 0.95, 0.99, 0.999}) {
            int i = (int) (q * (n - 1));
            sb.append(String.format(Locale.ROOT, "p%s=%.2f/%.2f/%.0f ", q, cs[i], es[i], hs[i]));
        }
        System.out.println(sb);
    }

    static int rgb(double r, double g, double b) {
        int ri = (int) Math.max(0, Math.min(255, r));
        int gi = (int) Math.max(0, Math.min(255, g));
        int bi = (int) Math.max(0, Math.min(255, b));
        return (ri << 16) | (gi << 8) | bi;
    }

    static double[] baseColor(ColumnSample s, int sl) {
        double h = s.height;
        boolean water = h < sl - 0.3 || s.waterLevel > h;
        if (water) {
            double surface = s.waterLevel > h ? s.waterLevel : sl;
            double depth = surface - h;
            double k = Math.min(1.0, depth / 90.0);
            if (s.river > 0 || s.lake > 0 || s.waterLevel > h) {
                return new double[]{40, 110 - 30 * k, 210 - 40 * k};
            }
            return new double[]{30 - 15 * k, 90 - 50 * k, 170 - 70 * k};
        }
        double temp = s.temperature;
        double hum = s.humidity;
        double rel = h - sl;
        if (s.dryRiver && s.river > 0) {
            return new double[]{200, 180, 130};
        }
        if (temp < -0.45 || (rel > 380 && temp < -0.15)) {
            return new double[]{240, 244, 250};
        }
        if (s.volcano > 0.4) {
            return new double[]{70, 62, 60};
        }
        if (s.canyon > 0.3 || (temp > 0.55 && hum < -0.1 && (s.plateau > 0.3 || s.erosion < 0.05))) {
            return new double[]{190, 110, 65};
        }
        if (s.beach > 0.5) {
            return new double[]{226, 212, 160};
        }
        if (s.mountain > 0.5 && s.peakness > 0.55) {
            return new double[]{135, 130, 125};
        }
        if (temp > 0.55 && hum < 0.1) {
            return new double[]{222, 205, 145};
        }
        if (s.swamp > 0.5) {
            return new double[]{70, 100, 60};
        }
        double green = 120 + 50 * hum;
        double red = 70 + 50 * temp - 30 * hum;
        return new double[]{red, green, 55 + 10 * hum};
    }

    /** 1:1 vertical cross-section through the middle row of the sampled grid (west to east). */
    private static BufferedImage section(ColumnSample[][] g, int bpp, int sl) {
        int n = g.length;
        ColumnSample[] row = g[n / 2];
        double max = sl;
        double min = sl;
        for (ColumnSample s : row) {
            max = Math.max(max, s.height);
            min = Math.min(min, s.height);
        }
        int top = (int) max + 20;
        int bottom = (int) min - 20;
        int height = (top - bottom) / bpp + 1;
        BufferedImage img = new BufferedImage(n, height, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < n; i++) {
            ColumnSample s = row[i];
            double[] c = baseColor(s, sl);
            for (int py = 0; py < height; py++) {
                int y = top - py * bpp;
                int rgb;
                if (y <= s.height) {
                    rgb = y > s.height - 4 * bpp ? rgb(c[0], c[1], c[2]) : rgb(110, 105, 100);
                } else if (y < sl || (s.waterLevel != ColumnSample.NO_WATER && y < s.waterLevel)) {
                    rgb = rgb(50, 90, 200);
                } else {
                    rgb = rgb(175, 205, 240);
                }
                img.setRGB(i, py, rgb);
            }
        }
        return img;
    }

    private static BufferedImage flat(ColumnSample[][] g, int bpp, int sl, String mode) {
        int n = g.length;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                ColumnSample s = g[j][i];
                double hl = g[j][Math.max(0, i - 1)].height;
                double hu = g[Math.max(0, j - 1)][i].height;
                double dx = (s.height - hl) / bpp;
                double dz = (s.height - hu) / bpp;
                double shade = 1.0 + (dx + dz) * 0.9;
                shade = Math.max(0.35, Math.min(1.6, shade));
                double[] c;
                switch (mode) {
                    case "climate" -> c = new double[]{128 + 127 * s.temperature, 60, 128 + 127 * s.humidity};
                    case "class" -> {
                        if (s.height < sl - 0.3) {
                            c = new double[]{20, 40, 120};
                        } else {
                            c = new double[]{
                                80 + 175 * Math.max(s.mountain, s.volcano),
                                80 + 175 * Math.max(s.plateau, s.canyon),
                                80 + 175 * Math.max(s.river, Math.max(s.lake > 0 ? 1 : 0, s.swamp))};
                        }
                    }
                    default -> c = baseColor(s, sl);
                }
                boolean water = s.height < sl - 0.3 || s.waterLevel > s.height;
                double sh = water ? 1.0 : shade;
                img.setRGB(i, j, rgb(c[0] * sh, c[1] * sh, c[2] * sh));
            }
        }
        return img;
    }
}
