package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import com.terrahaul.worldgen.terrain.VolcanoZones;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Development tool: finds volcanoes with the terrain finder and renders, for each, a top-down map
 * of the ground the surface rule lays on it ({@link VolcanoZones#ground}) with the trees the
 * {@code terrahaul:volcano_zone} filter would let through, next to a perspective view from the
 * south. Below them, two crops at one block per pixel (outlined in red on the map) show the lower
 * edge of the bare cone, on a lava flow if one crosses it, and a burn scar, so speckle and patch
 * sizes can be judged block by block. Prints area statistics of the zones and the cost of a zone
 * lookup, so the volcano ecology can be judged and tuned without launching Minecraft.
 * <p>
 * Map colours: grey and black shades are the bare cone (light ash, tuff, dark scoria, black lava
 * rock), orange the magma crust of lava craters, browns the scorched ground of burn scars, greens
 * the soil (darker where the extra forest is denser, yellow-green in the pioneer belt). Dots are
 * simulated trees: light green the biome's own trees, dark green the extra volcano forest, black
 * burned snags.
 * Usage: {@code VolcanoStudy <seed> <outDir> [count=4] [minHeight=0]}
 * <p>
 * {@code VolcanoStudy craters <seed> <outDir> [count=6]} studies the craters instead: for each
 * volcano a contoured top view of the crater (water and lava filled from the model corners as the
 * water solver fills them), a view looking down into it from the south, and the crest of the rim
 * around the crater against the lake level, with numbers for how irregular the rim and the shore
 * are and whether the lake stays below the lowest point of the rim.
 */
public final class VolcanoStudy {
    /**
     * Simulated trees per chunk before zone weights: a typical biome's own trees (filtered by the
     * {@code living} zone), the extra volcano forest and the snags, as in tools/datagen_features.py.
     */
    private static final double[] PASS_COUNT = {3.0, 4.5, 5.0};
    private static final VolcanoZones.Kind[] PASS_ZONE = {VolcanoZones.Kind.LIVING, VolcanoZones.Kind.FOREST, VolcanoZones.Kind.BURN};
    private static final Color[] PASS_COLOR = {new Color(40, 105, 40), new Color(18, 70, 22), new Color(15, 12, 10)};
    private static final int MAP = 760;
    private static final int CROP = 420;

    private VolcanoStudy() {
    }

    public static void main(String[] args) throws Exception {
        boolean craters = args[0].equals("craters");
        if (craters) {
            args = Arrays.copyOfRange(args, 1, args.length);
        }
        long seed = Long.parseLong(args[0]);
        File dir = new File(args[1]);
        dir.mkdirs();
        int count = args.length > 2 ? Integer.parseInt(args[2]) : craters ? 6 : 4;
        double minHeight = args.length > 3 ? Double.parseDouble(args[3]) : 0.0;
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        TerrainFinder.Target target = TerrainFinder.targets().get("volcano");
        List<TerrainModel.VolcanoSite> found = new ArrayList<>();
        // search outward from the origin and then from points on widening rings around it
        int ring = 0;
        while (found.size() < count && ring < 60) {
            int n = ring == 0 ? 1 : 8;
            for (int k = 0; k < n && found.size() < count; k++) {
                double a = Math.PI * 2.0 * k / n + ring * 0.7;
                int ox = (int) (Math.cos(a) * ring * 9000.0);
                int oz = (int) (Math.sin(a) * ring * 9000.0);
                TerrainFinder.Result r = TerrainFinder.find(model, target, ox, oz, 12000, () -> false);
                if (r == null) {
                    continue;
                }
                TerrainModel.VolcanoSite v = model.nearestVolcano(r.x(), r.z(), 50.0);
                if (v != null && v.height >= minHeight && found.stream().noneMatch(f -> f.x == v.x && f.z == v.z)) {
                    found.add(v);
                }
            }
            ring++;
        }
        if (craters) {
            for (int i = 0; i < found.size(); i++) {
                CraterStudy.study(model, found.get(i), new File(dir, "crater_" + seed + "_" + i + ".png"));
            }
            return;
        }
        timing(model, found);
        for (int i = 0; i < found.size(); i++) {
            study(model, found.get(i), new File(dir, "volcano_" + i + ".png"));
        }
    }

    /**
     * Average cost of a zone lookup plus ground choice per column, the way the surface rule runs
     * it: chunk by chunk, with the model columns of the chunk already cached (the rule samples
     * them itself before it reaches the volcano branch).
     */
    private static void timing(TerrainModel model, List<TerrainModel.VolcanoSite> found) {
        if (found.isEmpty()) {
            System.out.println("no volcanoes found");
            return;
        }
        TerrainModel.VolcanoSite v = found.get(0);
        long total = 0;
        int n = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int c = 0; c < 64; c++) {
                double a = c * 2.4;
                double rr = v.radius * (0.15 + 0.75 * (c % 8) / 8.0);
                int x0 = ((int) (v.x + Math.cos(a) * rr)) & ~15;
                int z0 = ((int) (v.z + Math.sin(a) * rr)) & ~15;
                for (int z = z0 - 4; z <= z0 + 20; z += 4) {
                    for (int x = x0 - 4; x <= x0 + 20; x += 4) {
                        model.sample(x, z);
                    }
                }
                long t0 = System.nanoTime();
                for (int z = z0; z < z0 + 16; z++) {
                    for (int x = x0; x < x0 + 16; x++) {
                        VolcanoZones.Zone zone = VolcanoZones.at(model, x, z);
                        VolcanoZones.ground(model, zone, x, 100, z);
                    }
                }
                if (pass == 1) {
                    total += System.nanoTime() - t0;
                    n += 256;
                }
            }
        }
        System.out.printf(Locale.ROOT, "zone lookup + ground: %.2f us per column (%d columns of 64 chunks, model columns cached)%n",
            total / 1e3 / n, n);
    }

    private static void study(TerrainModel model, TerrainModel.VolcanoSite v, File out) throws Exception {
        double span = v.radius * 2.16;
        double bpp = span / MAP;
        double minX = v.x - span / 2.0;
        double minZ = v.z - span / 2.0;
        int sl = model.seaLevel();
        double[][] height = new double[MAP][MAP];
        int[][] color = new int[MAP][MAP];
        VolcanoZones.Zone[][] zones = new VolcanoZones.Zone[MAP][MAP];
        int[][] treeLine = new int[MAP][MAP];
        IntStream.range(0, MAP).parallel().forEach(j -> {
            ColumnSample s = new ColumnSample();
            for (int i = 0; i < MAP; i++) {
                int x = (int) Math.floor(minX + i * bpp);
                int z = (int) Math.floor(minZ + j * bpp);
                model.sampleInto(x & ~3, z & ~3, s);
                double h = model.interpolatedHeight(x, z);
                VolcanoZones.Zone zone = new VolcanoZones.Zone();
                VolcanoZones.compute(model, x, z, zone);
                height[j][i] = h;
                zones[j][i] = zone;
                treeLine[j][i] = (int) (230 + 330 * Math.max(0.0, Math.min(1.0, (s.baseTemperature + 1.0) / 2.0)));
                color[j][i] = baseColor(model, s, zone, x, (int) Math.floor(h), z, h, sl);
            }
        });
        BufferedImage map = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < MAP; j++) {
            for (int i = 0; i < MAP; i++) {
                double hl = height[j][Math.max(0, i - 1)];
                double hu = height[Math.max(0, j - 1)][i];
                double shade = Math.max(0.55, Math.min(1.35, 1.0 + ((height[j][i] - hl) + (height[j][i] - hu)) / bpp * 0.6));
                map.setRGB(i, j, scale(color[j][i], shade));
            }
        }
        // ---- simulated trees: the filter's probabilities at random positions per chunk ----------
        Graphics2D g = map.createGraphics();
        int living = 0;
        int snags = 0;
        int forestChunks = 0;
        int burnChunks = 0;
        Random random = new Random(42);
        int chunks = (int) Math.ceil(span / 16.0);
        int cx0 = (int) Math.floor(minX / 16.0);
        int cz0 = (int) Math.floor(minZ / 16.0);
        for (int cz = cz0; cz < cz0 + chunks; cz++) {
            for (int cx = cx0; cx < cx0 + chunks; cx++) {
                int centerI = (int) ((cx * 16 + 8 - minX) / bpp);
                int centerJ = (int) ((cz * 16 + 8 - minZ) / bpp);
                if (centerI < 0 || centerJ < 0 || centerI >= MAP || centerJ >= MAP) {
                    continue;
                }
                VolcanoZones.Zone cz8 = zones[centerJ][centerI];
                forestChunks += cz8.forest > 0.5 ? 1 : 0;
                burnChunks += cz8.burn > 0.5 ? 1 : 0;
                for (int pass = 0; pass < 3; pass++) {
                    double expected = PASS_COUNT[pass];
                    int k = (int) expected + (random.nextDouble() < expected - (int) expected ? 1 : 0);
                    for (int a = 0; a < k; a++) {
                        int x = cx * 16 + random.nextInt(16);
                        int z = cz * 16 + random.nextInt(16);
                        int i = (int) ((x - minX) / bpp);
                        int j = (int) ((z - minZ) / bpp);
                        if (i < 0 || j < 0 || i >= MAP || j >= MAP) {
                            continue;
                        }
                        VolcanoZones.Zone zone = zones[j][i];
                        double w = zone.weight(PASS_ZONE[pass]);
                        if (random.nextDouble() >= w || height[j][i] > treeLine[j][i] || height[j][i] < sl) {
                            continue;
                        }
                        g.setColor(PASS_COLOR[pass]);
                        int d = Math.max(2, (int) Math.round(3.0 / bpp));
                        g.fillOval(i - d / 2, j - d / 2, d, d);
                        if (pass == 1) {
                            living++;
                        } else if (pass == 2) {
                            snags++;
                        }
                    }
                }
            }
        }
        g.dispose();
        String stats = stats(model, v, zones, height, minX, minZ, bpp, living, snags, forestChunks, burnChunks);
        System.out.println(out.getName() + ": " + stats);

        BufferedImage view = perspective(height, color, bpp, sl);
        // block-resolution crops: where the bare cone meets the forest (on a lava flow if there is
        // one), and the burn scar nearest the middle of the flanks
        int[] edge = edgePoint(model, v);
        int[] scar = scarPoint(zones, minX, minZ, bpp);
        BufferedImage cropA = crop(model, edge[0], edge[1]);
        BufferedImage cropB = crop(model, scar[0], scar[1]);
        BufferedImage img = new BufferedImage(MAP + view.getWidth(), MAP + CROP + 44, BufferedImage.TYPE_INT_RGB);
        Graphics2D o = img.createGraphics();
        o.setColor(Color.WHITE);
        o.fillRect(0, 0, img.getWidth(), img.getHeight());
        o.drawImage(map, 0, 0, null);
        o.drawImage(view, MAP, 0, null);
        o.drawImage(cropA, 0, MAP + 4, null);
        o.drawImage(cropB, CROP + 8, MAP + 4, null);
        o.setColor(Color.RED);
        for (int[] p : new int[][]{edge, scar}) {
            int i = (int) ((p[0] - minX) / bpp);
            int j = (int) ((p[1] - minZ) / bpp);
            int d = (int) (CROP / bpp);
            o.drawRect(i - d / 2, j - d / 2, d, d);
        }
        o.setColor(Color.BLACK);
        o.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        o.drawString(String.format(Locale.ROOT, "crops at 1 block per pixel: cone edge at %d %d, burn scar at %d %d", edge[0], edge[1], scar[0], scar[1]),
            2 * CROP + 20, MAP + 20);
        o.setColor(Color.BLACK);
        o.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        o.drawString(String.format(Locale.ROOT, "volcano at %.0f %.0f  height %.0f  radius %.0f  %s", v.x, v.z, v.height, v.radius,
            v.lava ? "lava crater" : v.waterLevel != ColumnSample.NO_WATER ? "crater lake" : "dry crater"), 6, img.getHeight() - 24);
        o.drawString(stats, 6, img.getHeight() - 8);
        o.dispose();
        ImageIO.write(img, "png", out);
    }

    private static String stats(TerrainModel model, TerrainModel.VolcanoSite v, VolcanoZones.Zone[][] zones, double[][] height,
                                double minX, double minZ, double bpp, int living, int snags, int forestChunks, int burnChunks) {
        int n = 0;
        int[] ground = new int[VolcanoZones.Ground.values().length];
        int bare = 0;
        int flow = 0;
        int pioneer = 0;
        int forest = 0;
        int burnCore = 0;
        int burnAny = 0;
        for (int j = 0; j < MAP; j++) {
            for (int i = 0; i < MAP; i++) {
                VolcanoZones.Zone z = zones[j][i];
                if (z.presence < 0.5 || z.t > 0.95 || height[j][i] < model.seaLevel()) {
                    continue;
                }
                n++;
                int x = (int) Math.floor(minX + i * bpp);
                int zz = (int) Math.floor(minZ + j * bpp);
                ground[VolcanoZones.ground(model, z, x, (int) Math.floor(height[j][i]), zz).ordinal()]++;
                bare += z.weight(VolcanoZones.Kind.CONE) > 0.5 ? 1 : 0;
                flow += z.inFlow() ? 1 : 0;
                pioneer += z.pioneer > 0.5 ? 1 : 0;
                forest += z.forest > 0.5 ? 1 : 0;
                burnCore += z.burn > 0.6 ? 1 : 0;
                burnAny += z.burn > 0.15 ? 1 : 0;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "land %%: cone %.0f flow %.1f pioneer %.0f forest %.0f burn>0.6 %.0f burn>0.15 %.0f | ground %%:",
            pct(bare, n), pct(flow, n), pct(pioneer, n), pct(forest, n), pct(burnCore, n), pct(burnAny, n)));
        for (VolcanoZones.Ground gr : VolcanoZones.Ground.values()) {
            if (ground[gr.ordinal()] > 0) {
                sb.append(String.format(Locale.ROOT, " %s %.1f", gr.name().toLowerCase(Locale.ROOT), pct(ground[gr.ordinal()], n)));
            }
        }
        sb.append(String.format(Locale.ROOT, " | trees/chunk: forest %.2f in forest chunks, snags %.2f in burn chunks",
            forestChunks == 0 ? 0.0 : living / (double) forestChunks, burnChunks == 0 ? 0.0 : snags / (double) burnChunks));
        return sb.toString();
    }

    /** A point on the lower edge of the bare cone, on a lava flow if one crosses it. */
    private static int[] edgePoint(TerrainModel model, TerrainModel.VolcanoSite v) {
        VolcanoZones.Zone zone = new VolcanoZones.Zone();
        int[] fallback = null;
        for (int k = 0; k < 64; k++) {
            double a = k * Math.PI * 2.0 / 64.0;
            boolean flow = false;
            for (double t = 0.12; t < 0.95; t += 0.004) {
                int x = (int) (v.x + Math.cos(a) * t * v.radius);
                int z = (int) (v.z + Math.sin(a) * t * v.radius);
                VolcanoZones.compute(model, x, z, zone);
                flow |= zone.inFlow();
                if (!zone.crater && !zone.inFlow() && zone.bare < 0.5 && zone.presence > 0.5) {
                    if (flow) {
                        return new int[]{x, z};
                    }
                    if (fallback == null) {
                        fallback = new int[]{x, z};
                    }
                    break;
                }
            }
        }
        return fallback != null ? fallback : new int[]{(int) v.x, (int) v.z};
    }

    /** The strongest burn closest to the middle of the flanks. */
    private static int[] scarPoint(VolcanoZones.Zone[][] zones, double minX, double minZ, double bpp) {
        double best = -1e9;
        int bi = MAP / 2;
        int bj = MAP / 2;
        for (int j = 0; j < MAP; j++) {
            for (int i = 0; i < MAP; i++) {
                VolcanoZones.Zone z = zones[j][i];
                double score = z.burn - 2.0 * Math.abs(z.t - 0.55);
                if (z.burn > 0.8 && score > best) {
                    best = score;
                    bi = i;
                    bj = j;
                }
            }
        }
        return new int[]{(int) (minX + bi * bpp), (int) (minZ + bj * bpp)};
    }

    /** {@value #CROP} by {@value #CROP} blocks at one block per pixel, with simulated trees as crowns. */
    private static BufferedImage crop(TerrainModel model, int cx, int cz) {
        int x0 = cx - CROP / 2;
        int z0 = cz - CROP / 2;
        int sl = model.seaLevel();
        double[][] h = new double[CROP][CROP];
        int[][] treeLine = new int[CROP][CROP];
        int[][] c = new int[CROP][CROP];
        VolcanoZones.Zone[][] zones = new VolcanoZones.Zone[CROP][CROP];
        IntStream.range(0, CROP).parallel().forEach(j -> {
            ColumnSample s = new ColumnSample();
            for (int i = 0; i < CROP; i++) {
                int x = x0 + i;
                int z = z0 + j;
                model.sampleInto(x & ~3, z & ~3, s);
                h[j][i] = model.interpolatedHeight(x, z);
                treeLine[j][i] = (int) (230 + 330 * Math.max(0.0, Math.min(1.0, (s.baseTemperature + 1.0) / 2.0)));
                VolcanoZones.Zone zone = new VolcanoZones.Zone();
                VolcanoZones.compute(model, x, z, zone);
                zones[j][i] = zone;
                c[j][i] = baseColor(model, s, zone, x, (int) Math.floor(h[j][i]), z, h[j][i], sl);
            }
        });
        BufferedImage img = new BufferedImage(CROP, CROP, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < CROP; j++) {
            for (int i = 0; i < CROP; i++) {
                double hl = h[j][Math.max(0, i - 1)];
                double hu = h[Math.max(0, j - 1)][i];
                double shade = Math.max(0.6, Math.min(1.3, 1.0 + ((h[j][i] - hl) + (h[j][i] - hu)) * 0.25));
                img.setRGB(i, j, scale(c[j][i], shade));
            }
        }
        Graphics2D g = img.createGraphics();
        Random random = new Random(7);
        for (int cz2 = Math.floorDiv(z0, 16); cz2 <= Math.floorDiv(z0 + CROP, 16); cz2++) {
            for (int cx2 = Math.floorDiv(x0, 16); cx2 <= Math.floorDiv(x0 + CROP, 16); cx2++) {
                for (int pass = 0; pass < 3; pass++) {
                    double expected = PASS_COUNT[pass];
                    int k = (int) expected + (random.nextDouble() < expected - (int) expected ? 1 : 0);
                    for (int a = 0; a < k; a++) {
                        int i = cx2 * 16 + random.nextInt(16) - x0;
                        int j = cz2 * 16 + random.nextInt(16) - z0;
                        if (i < 0 || j < 0 || i >= CROP || j >= CROP) {
                            continue;
                        }
                        double w = zones[j][i].weight(PASS_ZONE[pass]);
                        if (random.nextDouble() >= w || h[j][i] < sl || h[j][i] > treeLine[j][i]) {
                            continue;
                        }
                        if (pass < 2) {
                            int r = 3 + random.nextInt(3);
                            g.setColor(new Color(28 + random.nextInt(20), 88 + random.nextInt(30), 30));
                            g.fillOval(i - r, j - r, 2 * r, 2 * r);
                        } else {
                            g.setColor(new Color(20, 16, 14));
                            g.fillRect(i - 1, j - 1, 2, 2);
                        }
                    }
                }
            }
        }
        g.dispose();
        return img;
    }

    private static double pct(int k, int n) {
        return n == 0 ? 0.0 : 100.0 * k / n;
    }

    static int baseColor(TerrainModel model, ColumnSample s, VolcanoZones.Zone zone, int x, int y, int z, double h, int sl) {
        if (s.lavaLevel != ColumnSample.NO_WATER && s.lavaLevel > h) {
            return rgb(235, 95, 20);
        }
        if (s.waterLevel != ColumnSample.NO_WATER && s.waterLevel > h || h < sl) {
            return rgb(50, 90, 170);
        }
        VolcanoZones.Ground ground = VolcanoZones.ground(model, zone, x, y, z);
        return switch (ground) {
            case ASH, CINDERS -> rgb(150, 146, 140);
            case TUFF -> rgb(112, 114, 104);
            case SCORIA -> rgb(72, 72, 76);
            case LAVA_ROCK -> rgb(32, 30, 34);
            case MAGMA -> rgb(190, 80, 25);
            case CHARRED -> rgb(92, 62, 34);
            case SCORCHED -> rgb(122, 92, 62);
            case BURNT_ROOTS -> rgb(150, 116, 82);
            case SOIL -> soil(zone, s, h);
        };
    }

    private static int soil(VolcanoZones.Zone zone, ColumnSample s, double h) {
        double f = zone.forest;
        double p = zone.pioneer;
        double r = 104 - 50 * f + 40 * p;
        double gg = 150 - 30 * f + 15 * p;
        double b = 88 - 45 * f - 20 * p;
        return rgb(r, gg, b);
    }

    /** Voxel-space view from the southern edge of the map, looking north. */
    private static BufferedImage perspective(double[][] height, int[][] color, double bpp, int sl) {
        int n = height.length;
        int width = 1000;
        int imgH = MAP;
        BufferedImage img = new BufferedImage(width, imgH, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < imgH; y++) {
            double t = y / (double) imgH;
            int sky = rgb(120 + 80 * t, 165 + 60 * t, 235);
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, sky);
            }
        }
        double top = 0.0;
        for (double[] row : height) {
            for (double h : row) {
                top = Math.max(top, h);
            }
        }
        double camX = n / 2.0;
        double camZ = n + n * 0.25;
        double camH = Math.max(sl, height[n - 1][n / 2]) + (top - sl) * 0.75;
        double horizon = imgH * 0.30;
        double fov = Math.toRadians(62);
        double focal = (width / 2.0) / Math.tan(fov / 2.0);
        int[] ybuffer = new int[width];
        Arrays.fill(ybuffer, imgH);
        for (double dist = n * 0.25; dist < n * 1.4; dist += Math.max(0.5, dist * 0.003)) {
            double halfW = Math.tan(fov / 2) * dist;
            double zf = camZ - dist;
            if (zf < 1) {
                break;
            }
            for (int sx = 0; sx < width; sx++) {
                double xf = camX - halfW + (2.0 * halfW) * sx / width;
                int ix = (int) xf;
                int iz = (int) zf;
                if (ix < 1 || ix >= n || iz < 1 || iz >= n) {
                    continue;
                }
                double h = Math.max(height[iz][ix], sl);
                int sy = (int) ((camH - h) / (dist * bpp) * focal + horizon);
                if (sy < ybuffer[sx]) {
                    double hl = height[iz][ix - 1];
                    double hu = height[iz - 1][ix];
                    double shade = Math.max(0.5, Math.min(1.4, 1.0 + ((height[iz][ix] - hl) + (height[iz][ix] - hu)) / bpp * 0.5));
                    int c = scale(color[iz][ix], shade);
                    for (int y = Math.max(0, sy); y < ybuffer[sx] && y < imgH; y++) {
                        img.setRGB(sx, y, c);
                    }
                    ybuffer[sx] = Math.max(0, sy);
                }
            }
        }
        return img;
    }

    static int scale(int c, double k) {
        return rgb(((c >> 16) & 255) * k, ((c >> 8) & 255) * k, (c & 255) * k);
    }

    static int rgb(double r, double g, double b) {
        int ri = (int) Math.max(0, Math.min(255, r));
        int gi = (int) Math.max(0, Math.min(255, g));
        int bi = (int) Math.max(0, Math.min(255, b));
        return (ri << 16) | (gi << 8) | bi;
    }
}
