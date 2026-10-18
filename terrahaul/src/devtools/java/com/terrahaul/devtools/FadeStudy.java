package com.terrahaul.devtools;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.surface.Fades;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Development tool: renders and measures the material fades of {@code TerrainSurfaceRule} with
 * the same arithmetic ({@link Fades}), on the model terrain, without Minecraft.
 * <ol>
 *   <li>Strata on a vertical wall, three ways: as released (3-5 block bands with per-block
 *       dither), as in the first rework (bands shifted by a 4-block-grid value, 3.5-block noise)
 *       and now.</li>
 *   <li>A vertical section through real terrain at a finder target, with the rule's soil profile,
 *       outcrops and rock faces (exposure from the heightmap, as the rule computes it).</li>
 *   <li>The deepslate transition between Y 0 and 8: vanilla's per-block gradient and the smooth
 *       boundary.</li>
 *   <li>A top view of the surface around the target: grass, the coarse-dirt fringe, rock outcrops
 *       and snow on steep and high ground.</li>
 * </ol>
 * For every panel it prints speckle measures: the share of blocks that differ from at least three
 * of their four neighbours in the image plane, the number of islands of one or two blocks per
 * 10 000 blocks, and (for sections) how often horizontal neighbours differ compared with vertical
 * ones; level beds give a ratio well below 1, vertical striations a ratio near or above 1.
 * Colours are false-contrast greys (tuff dark, andesite middle, stone light) so the stops are
 * easy to tell apart. Usage: {@code FadeStudy <seed> <out.png> [target=canyon]}.
 */
public final class FadeStudy {
    private static final int AIR = -1;
    private static final int TUFF = 0;
    private static final int ANDESITE = 1;
    private static final int STONE = 2;
    private static final int DIRT = 3;
    private static final int COARSE = 4;
    private static final int GRASS = 5;
    private static final int DEEPSLATE = 6;
    private static final int SNOW = 7;
    private static final int[][] COLOURS = {
        {84, 92, 74}, {150, 150, 158}, {205, 205, 205}, {134, 96, 67}, {104, 74, 52}, {95, 159, 53}, {52, 52, 60}, {250, 252, 255}};

    private FadeStudy() {
    }

    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        File out = new File(args[1]);
        String targetName = args.length > 2 ? args[2] : "canyon";
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        TerrainFinder.Result r = TerrainFinder.find(model, TerrainFinder.targets().get(targetName), 0, 0, 30000, () -> false);
        int cx = r == null ? 0 : r.x();
        int cz = r == null ? 0 : r.z();
        System.out.printf(Locale.ROOT, "%s at %d %d%n", targetName, cx, cz);

        int w = 256;
        int h = 160;
        int y0 = (int) model.interpolatedHeight(cx, cz) - 80;
        int[][] released = new int[h][w];
        int[][] baseline = new int[h][w];
        int[][] now = new int[h][w];
        for (int i = 0; i < w; i++) {
            int x = cx - w / 2 + i;
            double warp = Fades.strataWarp(model, x, cz);
            ColumnSample nearest = model.sample((x + 2) & ~3, (cz + 2) & ~3);
            double strata = nearest.strata;
            for (int j = 0; j < h; j++) {
                int y = y0 + j;
                // rows top-down, like the picture
                released[h - 1 - j][i] = releasedStrata(model, x, y, cz, strata);
                baseline[h - 1 - j][i] = baselineStrata(model, x, y, cz, strata);
                now[h - 1 - j][i] = Fades.strataStop(model, 3, x, y, cz, warp, Fades.ROCK_PERIOD, Fades.wobble(model, x, y, cz));
            }
        }
        report("wall strata, as released", released);
        report("wall strata, first rework", baseline);
        report("wall strata, now", now);

        int[][] section = section(model, cx, cz, 384, 200);
        report("terrain section, now", section);

        int[][] vanillaDeep = new int[24][w];
        int[][] smoothDeep = new int[24][w];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < 24; j++) {
                int y = -8 + j;
                int x = cx + i;
                boolean vanilla = y <= 0 || y < 8 && SeedMixer.unit(SeedMixer.hash3(0xDEE95L, x, y, cz)) < (8 - y) / 8.0;
                vanillaDeep[23 - j][i] = vanilla ? DEEPSLATE : STONE;
                smoothDeep[23 - j][i] = Fades.deep(model, x, y, cz) ? DEEPSLATE : STONE;
            }
        }
        report("deepslate transition, vanilla gradient", vanillaDeep);
        report("deepslate transition, now", smoothDeep);

        int[][] top = topView(model, cx, cz, 384);
        report("top view, now", top);

        int scale = 2;
        BufferedImage img = new BufferedImage(Math.max(3 * w + 20, 384 * scale + 384 + 10) * 1, h * 2 + 200 * 2 + 24 * 4 + 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(225, 230, 235));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        int y = 0;
        draw(img, released, 0, y, 1);
        draw(img, baseline, w + 10, y, 1);
        draw(img, now, 2 * w + 20, y, 1);
        y += h + 10;
        draw(img, section, 0, y, scale);
        draw(img, top, 384 * scale + 10, y, 1);
        y += Math.max(200 * scale, 384) + 10;
        draw(img, vanillaDeep, 0, y, 2);
        draw(img, smoothDeep, w * 2 + 10, y, 2);
        g.setColor(Color.BLACK);
        g.drawString("released | first rework | now", 4, 12);
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    /** Strata of the released version: 5-block bands of a mixed palette with a dithered offset. */
    private static int releasedStrata(TerrainModel model, int x, int y, int z, double strata) {
        double white = SeedMixer.unit(SeedMixer.hash3(0x6A3F11C5L, x, y, z)) * 2.0 - 1.0;
        double dither = white * 0.6 + model.detail3(6, x / 6.0, y / 4.0, z / 6.0) * 0.55;
        double offset = strata * 37.0 + model.detail3(1, x / 60.0, 17.0, z / 60.0) * 3.0 + dither * 1.4;
        int band = (int) Math.floor((y + offset) / 5.0);
        int[] palette = {STONE, STONE, ANDESITE, STONE, STONE, ANDESITE, TUFF};
        return palette[Math.floorMod(band, palette.length)];
    }

    /** Strata of the first rework: a grid-sampled shift and 3.5-block boundary noise. */
    private static int baselineStrata(TerrainModel model, int x, int y, int z, double strata) {
        double warp = strata * 37.0 + Fades.patch(model, x, z, 70.0, 1) * 6.0 + Fades.patch(model, x, z, 19.0, 3) * 1.5;
        double phase = (y + warp) / 24.0;
        double tri = Math.abs(phase - Math.floor(phase) - 0.5) * 2.0;
        double n = model.detail3(6, x / 3.5, y / 3.5, z / 3.5);
        return Fades.stop(3, tri, n * 0.8);
    }

    /**
     * Vertical section along x through the model terrain with the rule's choices for grey rock:
     * rock faces where a block is exposed sideways (heightmap ring, as the rule does), outcrops on
     * steep ground, otherwise grass, dirt, coarse dirt, the tuff-andesite-stone fade and strata.
     */
    private static int[][] section(TerrainModel model, int cx, int cz, int w, int h) {
        int[] tops = new int[w + 40];
        int x0 = cx - w / 2 - 20;
        int maxTop = Integer.MIN_VALUE;
        for (int i = 0; i < tops.length; i++) {
            tops[i] = (int) Math.floor(model.interpolatedHeight(x0 + i, cz));
            maxTop = Math.max(maxTop, tops[i]);
        }
        int yTop = maxTop + 10;
        int[] ring = {1, 2, 3, 4, 6, 8, 11, 15};
        int[][] img = new int[h][w];
        for (int i = 0; i < w; i++) {
            int x = cx - w / 2 + i;
            int t = tops[i + 20];
            int slope = 0;
            for (int k = -2; k <= 2; k += 4) {
                slope = Math.max(slope, Math.abs((int) Math.floor(model.interpolatedHeight(x + k, cz)) - t));
                slope = Math.max(slope, Math.abs((int) Math.floor(model.interpolatedHeight(x, cz + k)) - t));
            }
            double smooth = Fades.smoothSlope(model, x, cz);
            double outcrop = Fades.rockiness(model, x, cz, smooth, 0.95);
            double soil = Math.max(1.5, 3.0 + 4.0 * Math.max(0.0, 1.0 - smooth / 2.5) + 1.5 * Fades.patch(model, x, cz, 23.0, 5));
            double warp = Fades.strataWarp(model, x, cz);
            int[] ringTop = new int[ring.length];
            for (int k = 0; k < ring.length; k++) {
                int d = ring[k];
                ringTop[k] = Math.min(Math.min(top(model, x + d, cz), top(model, x - d, cz)), Math.min(top(model, x, cz + d), top(model, x, cz - d)));
            }
            for (int j = 0; j < h; j++) {
                int y = yTop - j;
                if (y > t) {
                    img[j][i] = AIR;
                    continue;
                }
                int vertical = t - y;
                int lateral = Integer.MAX_VALUE;
                for (int k = 0; k < ring.length; k++) {
                    if (ringTop[k] < y) {
                        lateral = ring[k] - 1;
                        break;
                    }
                }
                int d = Math.min(vertical, lateral);
                double n = Fades.wobble(model, x, y, cz);
                int strata = Fades.strataStop(model, 3, x, y, cz, warp, Fades.ROCK_PERIOD, n);
                if (d > 15) {
                    img[j][i] = STONE;
                    continue;
                }
                boolean face = lateral < vertical - 1 || slope >= 6;
                if (face) {
                    img[j][i] = strata;
                } else if (outcrop > 0.0) {
                    img[j][i] = d <= 3 ? Fades.bareRockStop(model, 3, x, cz, d, n) : strata;
                } else if (d == 0 && vertical == 0) {
                    img[j][i] = outcrop > -0.12 ? COARSE : GRASS;
                } else {
                    double tt = Math.max(d, 1) / soil + 0.2 * n;
                    img[j][i] = tt < 0.75 ? DIRT : tt < 1.1 ? COARSE : tt < 2.2 ? Fades.stop(3, (tt - 1.1) / 1.1, n) : strata;
                }
            }
        }
        return img;
    }

    private static int top(TerrainModel model, int x, int z) {
        return (int) Math.floor(model.interpolatedHeight(x, z));
    }

    /** Top materials: snow on high, gentle ground, rock outcrops and their coarse-dirt fringe, grass. */
    private static int[][] topView(TerrainModel model, int cx, int cz, int size) {
        int[][] img = new int[size][size];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int x = cx - size / 2 + i;
                int z = cz - size / 2 + j;
                double smooth = Fades.smoothSlope(model, x, z);
                ColumnSample s = model.sample((x + 2) & ~3, (z + 2) & ~3);
                if (s.temperature < -0.45) {
                    img[j][i] = Fades.rockiness(model, x, z, smooth, 1.05) > 0.0 ? Fades.bareRockStop(model, 3, x, z, 0, Fades.wobble(model, x, (int) s.height, z)) : SNOW;
                    continue;
                }
                double r = Fades.rockiness(model, x, z, smooth, 0.95);
                img[j][i] = r > 0.0 ? Fades.bareRockStop(model, 3, x, z, 0, Fades.wobble(model, x, (int) s.height, z)) : r > -0.12 ? COARSE : GRASS;
            }
        }
        return img;
    }

    private static void report(String name, int[][] m) {
        int h = m.length;
        int w = m[0].length;
        long blocks = 0;
        long isolated = 0;
        long hDiff = 0;
        long vDiff = 0;
        long hPairs = 0;
        long vPairs = 0;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int c = m[j][i];
                if (c == AIR) {
                    continue;
                }
                blocks++;
                int differ = 0;
                int neighbours = 0;
                int[][] nb = {{i - 1, j}, {i + 1, j}, {i, j - 1}, {i, j + 1}};
                for (int[] p : nb) {
                    if (p[0] < 0 || p[1] < 0 || p[0] >= w || p[1] >= h || m[p[1]][p[0]] == AIR) {
                        continue;
                    }
                    neighbours++;
                    if (m[p[1]][p[0]] != c) {
                        differ++;
                    }
                }
                if (neighbours >= 3 && differ >= 3) {
                    isolated++;
                }
                if (i + 1 < w && m[j][i + 1] != AIR) {
                    hPairs++;
                    hDiff += m[j][i + 1] != c ? 1 : 0;
                }
                if (j + 1 < h && m[j + 1][i] != AIR) {
                    vPairs++;
                    vDiff += m[j + 1][i] != c ? 1 : 0;
                }
            }
        }
        int small = smallIslands(m);
        double ratio = vDiff == 0 ? 0.0 : (hDiff / (double) hPairs) / (vDiff / (double) vPairs);
        System.out.printf(Locale.ROOT, "%-40s blocks %6d  speckle %.2f%%  islands<=2 per 10k %.1f  horizontal/vertical change ratio %.2f%n",
            name, blocks, 100.0 * isolated / Math.max(1, blocks), 1.0e4 * small / Math.max(1, blocks), ratio);
    }

    /** 4-connected regions of one material with at most two blocks. */
    private static int smallIslands(int[][] m) {
        int h = m.length;
        int w = m[0].length;
        boolean[][] seen = new boolean[h][w];
        int count = 0;
        int[] stack = new int[w * h];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (seen[j][i] || m[j][i] == AIR) {
                    continue;
                }
                int c = m[j][i];
                int size = 0;
                int sp = 0;
                stack[sp++] = j * w + i;
                seen[j][i] = true;
                while (sp > 0) {
                    int p = stack[--sp];
                    size++;
                    int pi = p % w;
                    int pj = p / w;
                    int[][] nb = {{pi - 1, pj}, {pi + 1, pj}, {pi, pj - 1}, {pi, pj + 1}};
                    for (int[] q : nb) {
                        if (q[0] >= 0 && q[1] >= 0 && q[0] < w && q[1] < h && !seen[q[1]][q[0]] && m[q[1]][q[0]] == c) {
                            seen[q[1]][q[0]] = true;
                            stack[sp++] = q[1] * w + q[0];
                        }
                    }
                }
                if (size <= 2) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void draw(BufferedImage img, int[][] m, int ox, int oy, int s) {
        for (int j = 0; j < m.length; j++) {
            for (int i = 0; i < m[0].length; i++) {
                int c = m[j][i];
                int rgb = c == AIR ? 0xC8D7E6 : (COLOURS[c][0] << 16) | (COLOURS[c][1] << 8) | COLOURS[c][2];
                for (int a = 0; a < s; a++) {
                    for (int b = 0; b < s; b++) {
                        int px = ox + i * s + a;
                        int py = oy + j * s + b;
                        if (px < img.getWidth() && py < img.getHeight()) {
                            img.setRGB(px, py, rgb);
                        }
                    }
                }
            }
        }
    }
}
