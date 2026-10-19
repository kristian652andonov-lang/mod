package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Development tool: finds distinct mountain summits with the terrain finder (the same search
 * {@code /terrahaul find} uses, so it also checks that the target is findable) and, for each,
 * prints shape statistics and renders a sheet with a near view from the south, a far view from the
 * west and north-south and east-west profiles through the summit at 1:1 scale.
 * <p>
 * The statistics are what "realistic" means numerically: the mean slope in rings around the summit
 * (a concave peak is steep near the top and gentle at the base), the share of steep and near-vertical
 * ground, the biggest drop over 24 blocks (a cut-off wall shows up as a drop of 70+ blocks) and the
 * number of subsidiary peaks around the main one.
 * Usage: {@code PeakStudy <seed> <outDir> [target=alpine_peaks] [count=4]}, or
 * {@code PeakStudy <seed> <outDir> at <x> <z>} for the top nearest to a position.
 */
public final class PeakStudy {
    private static final int FINE_HALF = 2600;
    private static final int COARSE_HALF = 9000;

    private PeakStudy() {
    }

    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        File dir = new File(args[1]);
        dir.mkdirs();
        String target = args.length > 2 ? args[2] : "alpine_peaks";
        int count = args.length > 3 ? Integer.parseInt(args[3]) : 4;
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        if (target.equals("at")) {
            // PeakStudy <seed> <outDir> at <x> <z>: study the local top nearest to a position
            int[] top = climbLocal(model, Integer.parseInt(args[3]), Integer.parseInt(args[4]));
            study(model, "at_" + args[3] + "_" + args[4], top[0], top[1], dir);
            return;
        }
        TerrainFinder.Target t = TerrainFinder.targets().get(target);
        List<int[]> summits = new ArrayList<>();
        // search from origins on growing square rings 7000 blocks apart, so the summits are spread
        // over the world rather than all being the one nearest to the spawn
        search:
        for (int ring = 0; ring <= 5; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    TerrainFinder.Result r = TerrainFinder.find(model, t, i * 7000, j * 7000, 5000, () -> false);
                    if (r == null) {
                        continue;
                    }
                    // walls are studied where the finder puts them, peaks at the summit of their mountain
                    int[] top = target.equals("mountain_wall") ? climbLocal(model, r.x(), r.z()) : climb(model, r.x(), r.z());
                    boolean known = false;
                    for (int[] s : summits) {
                        known |= Math.hypot(s[0] - top[0], s[1] - top[1]) < 1500.0;
                    }
                    if (!known) {
                        System.out.printf(Locale.ROOT, "%s found at %d %d %d (finder) -> summit %d %d%n", target, r.x(), r.y(), r.z(), top[0], top[1]);
                        summits.add(top);
                        if (summits.size() >= count) {
                            break search;
                        }
                    }
                }
            }
        }
        if (summits.isEmpty()) {
            System.out.println("no " + target + " found");
        }
        for (int i = 0; i < summits.size(); i++) {
            study(model, target + "_" + i, summits.get(i)[0], summits.get(i)[1], dir);
        }
    }

    /**
     * Hill-climbs from a point to the summit of its mountain: to the local top first, then on to
     * any higher point within 1500 blocks (a subsidiary summit would otherwise stop the climb).
     */
    static int[] climb(TerrainModel model, int x, int z) {
        int[] top = climbLocal(model, x, z);
        ColumnSample s = new ColumnSample();
        for (int pass = 0; pass < 3; pass++) {
            model.sampleInto(top[0], top[1], s);
            double best = s.height;
            int bx = top[0];
            int bz = top[1];
            for (int dx = -1500; dx <= 1500; dx += 32) {
                for (int dz = -1500; dz <= 1500; dz += 32) {
                    model.sampleInto(top[0] + dx, top[1] + dz, s);
                    if (s.height > best) {
                        best = s.height;
                        bx = top[0] + dx;
                        bz = top[1] + dz;
                    }
                }
            }
            if (bx == top[0] && bz == top[1]) {
                break;
            }
            top = climbLocal(model, bx, bz);
        }
        return top;
    }

    private static int[] climbLocal(TerrainModel model, int x, int z) {
        ColumnSample s = new ColumnSample();
        int px = x;
        int pz = z;
        for (int step = 64; step >= 2; step /= 2) {
            boolean moved = true;
            while (moved) {
                moved = false;
                model.sampleInto(px, pz, s);
                double best = s.height;
                int bx = px;
                int bz = pz;
                for (int[] d : new int[][]{{step, 0}, {-step, 0}, {0, step}, {0, -step}, {step, step}, {-step, -step}, {step, -step}, {-step, step}}) {
                    model.sampleInto(px + d[0], pz + d[1], s);
                    if (s.height > best) {
                        best = s.height;
                        bx = px + d[0];
                        bz = pz + d[1];
                    }
                }
                if (bx != px || bz != pz) {
                    px = bx;
                    pz = bz;
                    moved = true;
                }
            }
        }
        return new int[]{px, pz};
    }

    private static void study(TerrainModel model, String name, int px, int pz, File dir) throws Exception {
        ColumnSample s = new ColumnSample();
        model.sampleInto(px, pz, s);
        double summit = s.height;
        System.out.printf(Locale.ROOT, "%s: summit %d %.0f %d (mountain %.2f alpine %.2f ancient %.2f wall %.2f peakness %.2f temp %.2f)%n",
            name, px, summit, pz, s.mountain, s.alpine, s.ancient, s.wall, s.peakness, s.temperature);
        HeightField fine = HeightField.sample(model, px, pz, FINE_HALF * 2 / 4, 4);
        HeightField coarse = HeightField.sample(model, px, pz, COARSE_HALF * 2 / 16, 16);
        double base = summit;
        for (int dx = -2000; dx <= 2000; dx += 32) {
            for (int dz = -2000; dz <= 2000; dz += 32) {
                if (dx * dx + dz * dz <= 2000 * 2000) {
                    base = Math.min(base, Math.max(model.seaLevel(), fine.height(px + dx, pz + dz)));
                }
            }
        }
        double relief = summit - base;
        printStats(fine, px, pz, summit, base);

        // a near view at a distance that grows with the relief and a far view from another side,
        // each from the direction that sees most of the mountain
        double dNear = Math.max(1100.0, Math.min(2300.0, 1.6 * relief + 500.0));
        double aNear = bestDirection(fine, coarse, px, pz, summit, base, dNear, Double.NaN);
        BufferedImage near = view(fine, coarse, px + dNear * Math.sin(aNear), pz - dNear * Math.cos(aNear),
            Math.toDegrees(aNear) + 180.0, summit, base, 70.0);
        double dFar = Math.max(3500.0, Math.min(7000.0, 4.0 * relief + 2000.0));
        double aFar = bestDirection(fine, coarse, px, pz, summit, base, dFar, aNear);
        BufferedImage far = view(fine, coarse, px + dFar * Math.sin(aFar), pz - dFar * Math.cos(aFar),
            Math.toDegrees(aFar) + 180.0, summit, base, 55.0);
        BufferedImage ew = profile(fine, px, pz, 1, 0, summit, base);
        BufferedImage ns = profile(fine, px, pz, 0, 1, summit, base);
        BufferedImage out = new BufferedImage(near.getWidth() + far.getWidth(), near.getHeight() + ew.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(near, 0, 0, null);
        g.drawImage(far, near.getWidth(), 0, null);
        g.drawImage(ew, 0, near.getHeight(), null);
        g.drawImage(ns, ew.getWidth(), near.getHeight(), null);
        g.setColor(Color.BLACK);
        g.drawString(String.format(Locale.ROOT, "%s summit %d %.0f %d, relief %.0f; near view from %.0f deg (%.0f), far view from %.0f deg (%.0f); profiles W-E and N-S 1:1",
            name, px, summit, pz, relief, Math.toDegrees(aNear), dNear, Math.toDegrees(aFar), dFar), 8, 16);
        g.dispose();
        ImageIO.write(out, "png", new File(dir, name + ".png"));
    }

    /**
     * Of 24 directions (radians clockwise from north) at the given distance, the one from which
     * the summit's vertical line is visible furthest down, seen from 30 blocks above the ground;
     * ties go to the lower viewpoint. Directions within 70 degrees of {@code avoid} are skipped.
     */
    private static double bestDirection(HeightField fine, HeightField coarse, int px, int pz, double summit, double base, double dist, double avoid) {
        double best = 0.0;
        double bestScore = -Double.MAX_VALUE;
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI * 2.0 / 24.0;
            if (!Double.isNaN(avoid) && Math.abs(Math.IEEEremainder(a - avoid, Math.PI * 2.0)) < Math.toRadians(70.0)) {
                continue;
            }
            double cx = px + dist * Math.sin(a);
            double cz = pz - dist * Math.cos(a);
            HeightField f = fine.contains(cx, cz, 8) ? fine : coarse;
            double camY = Math.max(f.height(cx, cz), fine.seaLevel) + 30.0;
            int lowest = 10;
            for (int t = 10; t >= 0; t--) {
                double ty = base + (summit - base) * t / 10.0;
                boolean clear = true;
                for (int i = 1; i < 160 && clear; i++) {
                    double u = i / 160.0;
                    double x = cx + (px - cx) * u;
                    double z = cz + (pz - cz) * u;
                    HeightField g = fine.contains(x, z, 8) ? fine : coarse;
                    clear = g.height(x, z) <= camY + (ty - camY) * u + 2.0;
                }
                if (!clear) {
                    break;
                }
                lowest = t;
            }
            double score = (10 - lowest) - camY / 10000.0;
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    /** A view of the summit from a viewpoint raised to about a third of the relief (a foothill or a pass). */
    private static BufferedImage view(HeightField fine, HeightField coarse, double camX, double camZ, double yaw, double summit, double base, double fov) {
        HeightField f = fine.contains(camX, camZ, 8) ? fine : coarse;
        double ground = Math.max(f.height(camX, camZ), fine.seaLevel);
        double dist = Math.hypot(camX - (fine.minX + fine.n * fine.bpp / 2.0), camZ - (fine.minZ + fine.n * fine.bpp / 2.0));
        double camY = Math.max(ground + 40.0, base + 0.33 * (summit - base));
        int width = 1200;
        int height = 700;
        double focal = (width / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
        double rise = (summit - camY) / dist * focal;
        double horizon = Math.max(0.3, Math.min(0.85, (rise + 70.0) / height));
        return Perspective.view(fine, coarse, camX, camZ, camY, yaw, fov, horizon, width, height);
    }

    /** Profile through the summit along (dx, dz), 1:1 at 4 blocks per pixel, coloured snow/rock/soil by slope. */
    private static BufferedImage profile(HeightField f, int px, int pz, int dx, int dz, double summit, double base) {
        int w = 1200;
        double bpp = 4.0;
        double bottom = base - 60.0;
        int h = Math.max(260, (int) ((summit - bottom) / bpp) + 40);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 220, 245));
        g.fillRect(0, 0, w, h);
        double[] hs = new double[w];
        for (int i = 0; i < w; i++) {
            double o = (i - w / 2.0) * bpp;
            hs[i] = f.height(px + dx * o, pz + dz * o);
        }
        for (int i = 0; i < w; i++) {
            int top = h - (int) ((hs[i] - bottom) / bpp);
            double slope = Math.abs(hs[Math.min(w - 1, i + 1)] - hs[Math.max(0, i - 1)]) / (2.0 * bpp);
            double o = (i - w / 2.0) * bpp;
            int k = f.index(px + dx * o, pz + dz * o);
            boolean snowy = f.temperature[k] < -0.45 || f.temperature[k] < 0.2 && f.peak[k] > 0.35;
            if (hs[i] < f.seaLevel) {
                g.setColor(new Color(50, 90, 200));
                g.drawLine(i, h - (int) ((f.seaLevel - bottom) / bpp), i, h);
            }
            g.setColor(snowy && slope < 1.05 ? new Color(236, 240, 247) : slope > 1.25 || snowy ? new Color(110, 108, 106) : new Color(90, 120, 70));
            g.drawLine(i, Math.max(0, top), i, Math.max(0, top) + 2);
            g.setColor(new Color(95, 92, 90));
            g.drawLine(i, Math.max(0, top) + 3, i, h);
        }
        g.setColor(Color.BLACK);
        g.drawString((dx != 0 ? "W-E" : "N-S") + String.format(Locale.ROOT, " profile, %.0f blocks wide, 1:1", w * bpp), 8, 16);
        g.dispose();
        return img;
    }

    private static void printStats(HeightField f, int px, int pz, double summit, double base) {
        // mean radial slope in rings: concave peaks are steepest near the summit
        double[] ring = {0, 100, 300, 700, 1500, 2400};
        StringBuilder sb = new StringBuilder("  mean slope by distance from summit:");
        for (int r = 0; r < ring.length - 1; r++) {
            double sum = 0.0;
            int n = 0;
            for (int a = 0; a < 48; a++) {
                double ang = a * Math.PI * 2.0 / 48.0;
                for (double d = ring[r]; d < ring[r + 1]; d += 8.0) {
                    double h0 = f.height(px + Math.cos(ang) * d, pz + Math.sin(ang) * d);
                    double h1 = f.height(px + Math.cos(ang) * (d + 8.0), pz + Math.sin(ang) * (d + 8.0));
                    sum += Math.abs(h1 - h0) / 8.0;
                    n++;
                }
            }
            sb.append(String.format(Locale.ROOT, " %.0f-%.0f:%.0fdeg", ring[r], ring[r + 1], Math.toDegrees(Math.atan(sum / n))));
        }
        System.out.println(sb);
        // steepness distribution and sheer drops within 1500 blocks
        int total = 0;
        int s45 = 0;
        int s60 = 0;
        int s70 = 0;
        int s80 = 0;
        int sheer = 0;
        double maxDrop = 0.0;
        for (int dx = -1500; dx <= 1500; dx += 12) {
            for (int dz = -1500; dz <= 1500; dz += 12) {
                double x = px + dx;
                double z = pz + dz;
                double h = f.height(x, z);
                if (h < f.seaLevel || h < base + 0.15 * (summit - base)) {
                    continue;
                }
                total++;
                double gx = (f.height(x + 4, z) - f.height(x - 4, z)) / 8.0;
                double gz = (f.height(x, z + 4) - f.height(x, z - 4)) / 8.0;
                double sl = Math.sqrt(gx * gx + gz * gz);
                s45 += sl > 1.0 ? 1 : 0;
                s60 += sl > 1.73 ? 1 : 0;
                s70 += sl > 2.75 ? 1 : 0;
                s80 += sl > 5.67 ? 1 : 0;
                double drop = h - Math.min(Math.min(f.height(x + 24, z), f.height(x - 24, z)), Math.min(f.height(x, z + 24), f.height(x, z - 24)));
                maxDrop = Math.max(maxDrop, drop);
                sheer += drop > 72.0 ? 1 : 0;
            }
        }
        System.out.printf(Locale.ROOT, "  upper slopes: >45deg %.1f%% >60deg %.1f%% >70deg %.2f%% >80deg %.2f%%; drops >72 over 24 blocks %.2f%%, max drop over 24 blocks %.0f%n",
            100.0 * s45 / Math.max(1, total), 100.0 * s60 / Math.max(1, total), 100.0 * s70 / Math.max(1, total), 100.0 * s80 / Math.max(1, total),
            100.0 * sheer / Math.max(1, total), maxDrop);
        // subsidiary peaks: highest point within +-192 blocks and in the upper 60% of the relief
        int peaks = 0;
        StringBuilder ps = new StringBuilder();
        for (int dx = -2400; dx <= 2400; dx += 32) {
            for (int dz = -2400; dz <= 2400; dz += 32) {
                double h = f.height(px + dx, pz + dz);
                if (h < base + 0.4 * (summit - base)) {
                    continue;
                }
                boolean top = true;
                for (int ox = -192; ox <= 192 && top; ox += 32) {
                    for (int oz = -192; oz <= 192; oz += 32) {
                        if ((ox != 0 || oz != 0) && f.height(px + dx + ox, pz + dz + oz) >= h) {
                            top = false;
                            break;
                        }
                    }
                }
                if (top) {
                    peaks++;
                    if (peaks <= 8) {
                        ps.append(String.format(Locale.ROOT, " (%+d,%+d)%.0f", dx, dz, h));
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "  base %.0f relief %.0f; %d peaks in the upper 60%% within 2400:%s%n", base, summit - base, peaks, ps);
    }
}
