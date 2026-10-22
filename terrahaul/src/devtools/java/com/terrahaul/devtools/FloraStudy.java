package com.terrahaul.devtools;

import com.terrahaul.worldgen.surface.Fades;
import com.terrahaul.worldgen.surface.MountainFlora;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Development tool: the climate and shape of mountain flanks, for the vegetation that climbs them.
 * <ul>
 *   <li>{@code survey <seed> [radius=6000] [step=32]}: histograms over the mountain columns
 *       within {@code radius} of the origin of base temperature, humidity, cooled temperature and
 *       smoothed slope.</li>
 *   <li>{@code transect <seed> x0 z0 x1 z1 [step=4]}: the fields and the flora zones
 *       ({@link MountainFlora}) along a line.</li>
 *   <li>{@code summits <seed> radius [step=32]}: mountain summits within {@code radius} of the
 *       origin (the highest column of each 256-block cell with a mountain weight above 0.6), with
 *       the floor, climate and tree line there, sorted by distance.</li>
 *   <li>{@code view <seed> sx sz angle [distance=80]}: cameras for the screenshot test that look
 *       up the flank below summit {@code (sx, sz)} in direction {@code angle} (degrees from +x):
 *       the target is the point on that line where the ground reaches the tree line (or the
 *       summit); the camera stands {@code distance} blocks further out, 10 blocks above its
 *       ground ("up"), or hovers there at the height of the tree line ("across"). Prints the
 *       profile and the shots as {@code x,y,z,yaw,pitch}.</li>
 *   <li>{@code bench <seed> cx cz}: the cost of the zones per column over 8x8 chunks around
 *       {@code (cx, cz)}, chunk by chunk as the surface rule asks for them, after the model's own
 *       columns are cached (so it measures the flora alone, valley levels included).</li>
 *   <li>{@code map <seed> cx cz size out.png}: a shaded top view of the flora zones, one pixel
 *       per block: dark green closed forest, mid green the tree line, olive krummholz, light
 *       green alpine meadow, pale where soil lies without a zone, grey rock, blue-white where
 *       avalanche tracks run; dimmed where the ground is not soil. Contours every 50 blocks.</li>
 * </ul>
 */
public final class FloraStudy {
    private FloraStudy() {
    }

    public static void main(String[] args) throws Exception {
        GameWorld world = GameWorld.of(args[1]);
        TerrainModel model = world.model;
        System.out.println(world);
        switch (args[0]) {
            case "survey" -> survey(model, args.length > 2 ? Integer.parseInt(args[2]) : 6000, args.length > 3 ? Integer.parseInt(args[3]) : 32);
            case "transect" -> transect(model, Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                Integer.parseInt(args[5]), args.length > 6 ? Integer.parseInt(args[6]) : 4);
            case "summits" -> summits(model, Integer.parseInt(args[2]), args.length > 3 ? Integer.parseInt(args[3]) : 32);
            case "view" -> view(model, Integer.parseInt(args[2]), Integer.parseInt(args[3]), Double.parseDouble(args[4]),
                args.length > 5 ? Integer.parseInt(args[5]) : 80);
            case "bench" -> bench(model, Integer.parseInt(args[2]), Integer.parseInt(args[3]));
            case "map" -> map(model, Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), new File(args[5]));
            default -> throw new IllegalArgumentException("unknown mode " + args[0]);
        }
    }

    private static void survey(TerrainModel model, int radius, int step) {
        int[] base = new int[20];
        int[] hum = new int[20];
        int[] temp = new int[20];
        int[] slope = new int[20];
        int n = 0;
        for (int x = -radius; x <= radius; x += step) {
            for (int z = -radius; z <= radius; z += step) {
                ColumnSample s = model.sample(x, z);
                if (Math.max(s.mountain, s.alpine) < 0.4 || s.height < model.seaLevel() + 40) {
                    continue;
                }
                n++;
                base[bin(s.baseTemperature)]++;
                hum[bin(s.humidity)]++;
                temp[bin(s.temperature)]++;
                slope[Math.min(19, (int) (Fades.smoothSlope(model, x, z) * 5.0))]++;
            }
        }
        System.out.printf(Locale.ROOT, "%d mountain columns%n", n);
        print("base temperature", base, n, -1.0, 0.1);
        print("humidity", hum, n, -1.0, 0.1);
        print("cooled temperature", temp, n, -1.0, 0.1);
        print("smoothed slope", slope, n, 0.0, 0.2);
    }

    private static int bin(double v) {
        return Math.max(0, Math.min(19, (int) Math.floor((v + 1.0) * 10.0)));
    }

    private static void print(String name, int[] h, int n, double lo, double w) {
        StringBuilder sb = new StringBuilder(name + ":");
        for (int i = 0; i < h.length; i++) {
            if (h[i] > 0) {
                sb.append(String.format(Locale.ROOT, " [%.1f..%.1f) %.1f%%", lo + i * w, lo + (i + 1) * w, 100.0 * h[i] / n));
            }
        }
        System.out.println(sb);
    }

    private static void summits(TerrainModel model, int radius, int step) {
        int cells = (2 * radius) / 256 + 1;
        int[][] best = new int[cells * cells][];
        double[] bestH = new double[cells * cells];
        for (int x = -radius; x <= radius; x += step) {
            for (int z = -radius; z <= radius; z += step) {
                ColumnSample s = model.sample(x, z);
                if (Math.max(s.mountain, s.alpine) < 0.6) {
                    continue;
                }
                int c = ((x + radius) / 256) * cells + (z + radius) / 256;
                if (best[c] == null || s.height > bestH[c]) {
                    best[c] = new int[]{x, z};
                    bestH[c] = s.height;
                }
            }
        }
        java.util.List<int[]> list = new java.util.ArrayList<>();
        for (int[] b : best) {
            if (b != null) {
                list.add(b);
            }
        }
        list.sort(java.util.Comparator.comparingDouble(b -> Math.hypot(b[0], b[1])));
        for (int[] b : list) {
            ColumnSample s = model.sample(b[0], b[1]);
            double h = s.height;
            double floor = s.mountainFloor;
            double base = s.baseTemperature;
            double hum = s.humidity;
            double alp = s.alpine;
            MountainFlora.Zone f = MountainFlora.at(model, b[0], b[1], (int) h);
            System.out.printf(Locale.ROOT, "%6d %6d top=%5.0f floor=%5.0f base=%5.2f hum=%5.2f alpine=%4.2f line=%5.0f%n",
                b[0], b[1], h, floor, base, hum, alp, f.line);
        }
    }

    private static void view(TerrainModel model, int sx, int sz, double angle, int distance) {
        double ax = Math.cos(Math.toRadians(angle));
        double az = Math.sin(Math.toRadians(angle));
        // the target: walking out from the summit, the first point at or below the tree line
        int tx = sx;
        int tz = sz;
        double ty = model.interpolatedHeight(sx, sz);
        for (int d = 0; d <= 600; d += 4) {
            int x = (int) Math.round(sx + ax * d);
            int z = (int) Math.round(sz + az * d);
            double h = model.interpolatedHeight(x, z);
            MountainFlora.Zone f = MountainFlora.at(model, x, z, (int) h);
            if (d % 20 == 0) {
                System.out.printf(Locale.ROOT, "  d=%3d %6d %6d h=%5.0f line=%5.0f m=%5.0f slope=%4.2f%n", d, x, z, h, f.line, f.margin, f.steep);
            }
            if (f.margin >= 0.0 && tx == sx && tz == sz) {
                tx = x;
                tz = z;
                ty = h;
            }
        }
        int cx = (int) Math.round(tx + ax * distance);
        int cz = (int) Math.round(tz + az * distance);
        double ground = model.interpolatedHeight(cx, cz);
        double dx = tx - cx;
        double dz = tz - cz;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        for (double cy : new double[]{ground + 10.0, Math.max(ground + 10.0, ty - 10.0)}) {
            // aim at a point 15 blocks above the tree line
            double pitch = -Math.toDegrees(Math.atan2(ty + 15.0 - cy, Math.hypot(dx, dz)));
            System.out.printf(Locale.ROOT, "target %d %.0f %d; %s shot %d,%.0f,%d,%.0f,%.0f%n", tx, ty, tz, cy == ground + 10.0 ? "up" : "across",
                cx, cy, cz, yaw, pitch);
        }
    }

    private static void bench(TerrainModel model, int cx, int cz) {
        int x0 = (cx & ~15) - 64;
        int z0 = (cz & ~15) - 64;
        double sink = 0.0;
        long model0 = System.nanoTime();
        for (int c = 0; c < 64; c++) {
            int bx = x0 + (c % 8) * 16;
            int bz = z0 + (c / 8) * 16;
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    sink += model.interpolatedHeight(bx + i, bz + j);
                }
            }
        }
        long t0 = System.nanoTime();
        for (int c = 0; c < 64; c++) {
            int bx = x0 + (c % 8) * 16;
            int bz = z0 + (c / 8) * 16;
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    int x = bx + i;
                    int z = bz + j;
                    sink += MountainFlora.at(model, x, z, (int) model.interpolatedHeight(x, z)).soil;
                }
            }
        }
        long t1 = System.nanoTime();
        System.out.printf(Locale.ROOT, "model heights %.1f ms per chunk; flora %.2f ms per chunk (%.2f us per column) [%s]%n",
            (t0 - model0) / 1e6 / 64, (t1 - t0) / 1e6 / 64, (t1 - t0) / 1e3 / 64 / 256, sink > 0 ? "" : "-");
    }

    private static void map(TerrainModel model, int cx, int cz, int size, File out) throws java.io.IOException {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int x0 = cx - size / 2;
        int z0 = cz - size / 2;
        double[] stats = new double[6];
        // smoothed slope of the flanks below the tree line, in bands of 0.2
        int[] slopes = new int[12];
        int below = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int x = x0 + i;
                int z = z0 + j;
                double h = model.interpolatedHeight(x, z);
                MountainFlora.Zone f = MountainFlora.at(model, x, z, (int) Math.floor(h));
                double[] c;
                if (f.share <= 0.0) {
                    c = new double[]{0.55, 0.6, 0.5};
                } else if (f.avalanche > 0.5) {
                    c = new double[]{0.75, 0.85, 0.95};
                } else {
                    double fo = Math.max(f.forest, f.montane);
                    double best = Math.max(Math.max(fo, f.edge), Math.max(f.krummholz, f.alpine));
                    if (best < 0.15) {
                        c = f.soil > 0.0 ? new double[]{0.7, 0.75, 0.55} : new double[]{0.6, 0.6, 0.62};
                    } else if (best == fo) {
                        c = new double[]{0.1, 0.38 - 0.12 * f.montane, 0.12};
                    } else if (best == f.edge) {
                        c = new double[]{0.25, 0.55, 0.2};
                    } else if (best == f.krummholz) {
                        c = new double[]{0.45, 0.5, 0.2};
                    } else {
                        c = new double[]{0.55, 0.78, 0.35};
                    }
                    if (f.soil <= 0.0) {
                        c = new double[]{0.45 + 0.3 * c[0], 0.45 + 0.3 * c[1], 0.47 + 0.3 * c[2]};
                    }
                    stats[0]++;
                    stats[1] += f.soil > 0.0 ? 1 : 0;
                    stats[2] += fo;
                    stats[3] += f.krummholz;
                    stats[4] += f.alpine;
                    stats[5] += f.edge;
                    if (f.share > 0.5 && f.margin > 0.0) {
                        slopes[Math.min(11, (int) (f.steep * 5.0))]++;
                        below++;
                    }
                }
                double sx = model.interpolatedHeight(x + 1, z) - model.interpolatedHeight(x - 1, z);
                double sz = model.interpolatedHeight(x, z + 1) - model.interpolatedHeight(x, z - 1);
                double shade = Math.max(0.35, Math.min(1.25, 0.9 - 0.25 * (sx + sz)));
                boolean contour = Math.floor(h / 50.0) != Math.floor(model.interpolatedHeight(x + 1, z) / 50.0)
                    || Math.floor(h / 50.0) != Math.floor(model.interpolatedHeight(x, z + 1) / 50.0);
                int r = (int) Math.max(0, Math.min(255, 255 * c[0] * shade * (contour ? 0.6 : 1.0)));
                int g = (int) Math.max(0, Math.min(255, 255 * c[1] * shade * (contour ? 0.6 : 1.0)));
                int b = (int) Math.max(0, Math.min(255, 255 * c[2] * shade * (contour ? 0.6 : 1.0)));
                img.setRGB(i, j, r << 16 | g << 8 | b);
            }
        }
        ImageIO.write(img, "png", out);
        double n = Math.max(1.0, stats[0]);
        System.out.printf(Locale.ROOT, "flank columns %.0f: soil %.0f%%, forest %.2f, edge %.2f, krummholz %.2f, alpine %.2f (mean weights)%n",
            stats[0], 100.0 * stats[1] / n, stats[2] / n, stats[5] / n, stats[3] / n, stats[4] / n);
        StringBuilder sb = new StringBuilder("smoothed slope below the tree line (" + below + " columns):");
        for (int i = 0; i < slopes.length; i++) {
            sb.append(String.format(Locale.ROOT, " %s%.1f %.0f%%", i == slopes.length - 1 ? ">" : "<", i == slopes.length - 1 ? i * 0.2 : (i + 1) * 0.2,
                100.0 * slopes[i] / Math.max(1, below)));
        }
        System.out.println(sb);
    }

    private static void transect(TerrainModel model, int x0, int z0, int x1, int z1, int step) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        for (double d = 0; d <= len; d += step) {
            int x = (int) Math.round(x0 + (x1 - x0) * d / len);
            int z = (int) Math.round(z0 + (z1 - z0) * d / len);
            ColumnSample s = model.sample(x, z);
            System.out.printf(Locale.ROOT, "%6d %6d h=%6.1f floor=%6.1f base=%5.2f t=%5.2f hum=%5.2f mtn=%4.2f alp=%4.2f peak=%4.2f",
                x, z, s.height, s.mountainFloor, s.baseTemperature, s.temperature, s.humidity, s.mountain, s.alpine, s.peakness);
            MountainFlora.Zone f = MountainFlora.at(model, x, z, (int) Math.floor(model.interpolatedHeight(x, z)));
            System.out.printf(Locale.ROOT, " | line=%5.0f m=%5.0f slope=%4.2f hol=%5.1f F=%4.2f M=%4.2f E=%4.2f K=%4.2f A=%4.2f av=%4.2f soil=%5.2f%n",
                f.line, f.margin, f.steep, f.hollow, f.forest, f.montane, f.edge, f.krummholz, f.alpine, f.avalanche, f.soil);
        }
    }
}
