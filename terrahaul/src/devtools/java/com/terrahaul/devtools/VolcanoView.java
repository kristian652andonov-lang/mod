package com.terrahaul.devtools;

import com.terrahaul.worldgen.surface.Fades;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.VolcanoZones;
import com.terrahaul.worldgen.terrain.WaterBody;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Block-level views of a volcano the way a player sees it in game, run through
 * {@code VolcanoStudy views <seed> <outDir> [count=3]}: the ground is drawn in whole blocks, so
 * 1-block terraces and the contour rings of a smooth slope show exactly as they do in game, which
 * the smooth height-field views of the other tools hide. For every volcano it writes:
 * <ul>
 *   <li>{@code _rim}: standing a few blocks above the crater's rim on its southern side, looking
 *       down into the crater;</li>
 *   <li>{@code _flank}: standing on the outer flank part of the way down, looking up at the cone;</li>
 *   <li>{@code _top}: the crater and its rim from straight above at one block per pixel, shaded so
 *       that every 1-block step draws a line (concentric rings show as rings);</li>
 *   <li>{@code _wide}: the whole volcano from above, to judge ridges, gullies, lava flows and side
 *       cones.</li>
 * </ul>
 * Materials follow the surface rule's volcano branch ({@link VolcanoZones#ground} and its snow),
 * with the biome guessed from the climate temperature: a column colder than {@link #SNOWY} counts
 * as a snowy biome. Vanilla's snow layers are not drawn.
 */
final class VolcanoView {
    /** Climate temperature below which a column is taken to lie in a snowy biome. */
    private static final double SNOWY = -0.15;
    private static final int W = 1280;
    private static final int H = 720;
    /** Horizontal field of view, about Minecraft's default at 16:9. */
    private static final double FOV = Math.toRadians(100.0);

    private VolcanoView() {
    }

    /** Block grid of one area: top solid block, top liquid block and colours per column. */
    private static final class Grid {
        int minX;
        int minZ;
        int n;
        int[] top;
        int[] liquid;
        boolean[] lava;
        int[] color;
        int[] face;

        int index(int x, int z) {
            int i = x - minX;
            int j = z - minZ;
            return i < 0 || j < 0 || i >= n || j >= n ? -1 : j * n + i;
        }
    }

    static void render(TerrainModel model, TerrainModel.VolcanoSite v, File dir, String name) throws Exception {
        double[] out = new double[3];
        // ---- crater close-up from above --------------------------------------------------------
        int half = (int) (1.7 * v.craterRadius + 40.0);
        Grid crater = grid(model, (int) v.x - half, (int) v.z - half, 2 * half);
        ImageIO.write(top(crater, 1, false), "png", new File(dir, name + "_top.png"));
        ImageIO.write(top(crater, 1, true), "png", new File(dir, name + "_relief.png"));

        // ---- standing above the rim on its southern side, looking north into the crater -------------
        double bearing = Math.PI * 0.5;
        double rim = model.craterRimRadius(v, (int) (v.x + Math.cos(bearing) * 3.0 * v.radius), (int) (v.z + Math.sin(bearing) * 3.0 * v.radius));
        double camX = v.x + Math.cos(bearing) * (rim - 2.0);
        double camZ = v.z + Math.sin(bearing) * (rim - 2.0);
        double ground = model.interpolatedHeight((int) camX, (int) camZ);
        double camY = ground + 22.0;
        double floor = model.volcanoSurface(v, (int) v.x, (int) v.z, out);
        double pitch = -Math.atan2(camY - floor, rim) + 0.12;
        ImageIO.write(view(crater, camX, camY, camZ, bearing + Math.PI, pitch), "png", new File(dir, name + "_rim.png"));

        // ---- standing on the outer flank, looking up at the cone ---------------------------------
        double flankBearing = Math.PI * 0.5 + 0.55;
        double rc = model.craterRimRadius(v, (int) (v.x + Math.cos(flankBearing) * 3.0 * v.radius), (int) (v.z + Math.sin(flankBearing) * 3.0 * v.radius));
        double dist = Math.max(rc + 120.0, Math.min(rc + 520.0, rc + 0.3 * (v.radius - rc)));
        int span = (int) (dist + 160.0);
        Grid flank = grid(model, (int) v.x - span, (int) v.z - span, 2 * span);
        double fx = v.x + Math.cos(flankBearing) * dist;
        double fz = v.z + Math.sin(flankBearing) * dist;
        double fy = model.interpolatedHeight((int) fx, (int) fz) + 12.0;
        double crest = model.interpolatedHeight((int) (v.x + Math.cos(flankBearing) * rc), (int) (v.z + Math.sin(flankBearing) * rc));
        double up = Math.atan2(crest - fy, dist - rc) - 0.22;
        ImageIO.write(view(flank, fx, fy, fz, flankBearing + Math.PI, up), "png", new File(dir, name + "_flank.png"));

        // ---- the whole volcano from above ----------------------------------------------------------
        int bpp = Math.max(1, (int) Math.ceil(2.1 * v.radius / 1100.0));
        int wideHalf = (int) (1.05 * v.radius);
        Grid wide = sampled(model, (int) v.x - wideHalf, (int) v.z - wideHalf, 2 * wideHalf / bpp, bpp);
        ImageIO.write(top(wide, bpp, false), "png", new File(dir, name + "_wide.png"));
        ImageIO.write(top(wide, bpp, true), "png", new File(dir, name + "_widerelief.png"));
        System.out.printf(Locale.ROOT, "%s: volcano at %.0f %.0f height %.0f radius %.0f crater %.0f, %s; rim camera %.0f %.0f %.0f, flank camera %.0f %.0f %.0f%n",
            name, v.x, v.z, v.height, v.radius, v.craterRadius,
            v.lava ? "lava" : v.waterLevel != ColumnSample.NO_WATER ? "lake" : "dry", camX, camY, camZ, fx, fy, fz);
    }

    private static Grid grid(TerrainModel model, int minX, int minZ, int n) {
        return sampled(model, minX, minZ, n, 1);
    }

    /** A grid of {@code n} by {@code n} columns, {@code step} blocks apart. */
    private static Grid sampled(TerrainModel model, int minX, int minZ, int n, int step) {
        Grid g = new Grid();
        g.minX = minX;
        g.minZ = minZ;
        g.n = n;
        g.top = new int[n * n];
        g.liquid = new int[n * n];
        g.lava = new boolean[n * n];
        g.color = new int[n * n];
        g.face = new int[n * n];
        int sl = model.seaLevel();
        IntStream.range(0, n).parallel().forEach(j -> {
            ColumnSample s = new ColumnSample();
            VolcanoZones.Zone zone = new VolcanoZones.Zone();
            for (int i = 0; i < n; i++) {
                int x = minX + i * step;
                int z = minZ + j * step;
                double h = model.interpolatedHeight(x, z);
                int top = (int) Math.ceil(h) - 1;
                double level = Double.NaN;
                boolean lava = false;
                for (int c = 0; c < 4; c++) {
                    ColumnSample cs = model.sample((x & ~3) + (c & 1) * 4, (z & ~3) + (c >> 1) * 4);
                    if (cs.bodyInside && (cs.bodyKind == WaterBody.CRATER || cs.bodyKind == WaterBody.LAVA)
                        && (Double.isNaN(level) || cs.bodyLevel > level)) {
                        level = cs.bodyLevel;
                        lava = cs.bodyKind == WaterBody.LAVA;
                    }
                }
                int k = j * n + i;
                g.top[k] = top;
                if (!Double.isNaN(level) && top + 1 < level) {
                    g.liquid[k] = (int) level - 1;
                    g.lava[k] = lava;
                } else if (top < sl - 1) {
                    g.liquid[k] = sl - 1;
                } else {
                    g.liquid[k] = Integer.MIN_VALUE;
                }
                model.sampleInto(x & ~3, z & ~3, s);
                VolcanoZones.compute(model, x, z, zone);
                int slope = 0;
                for (int d = -2; d <= 2; d += 4) {
                    slope = Math.max(slope, Math.abs((int) Math.ceil(model.interpolatedHeight(x + d, z)) - 1 - top));
                    slope = Math.max(slope, Math.abs((int) Math.ceil(model.interpolatedHeight(x, z + d)) - 1 - top));
                }
                g.color[k] = material(model, s, zone, x, top, z, slope);
                g.face[k] = strata(model, x, top - 3, z);
            }
        });
        return g;
    }

    /**
     * The colour of the top block of a column, after the surface rule's volcano branch: faces of
     * steep columns show the volcanic strata, the volcano's ground its own colours, snow lies on
     * gentle ground in snowy biomes, and everything else is grass.
     */
    static int material(TerrainModel model, ColumnSample s, VolcanoZones.Zone zone, int x, int y, int z, int slope) {
        boolean snowy = s.temperature < SNOWY;
        double smooth = Fades.smoothSlope(model, x, z);
        if (slope >= 6) {
            return strata(model, x, y, z);
        }
        if (zone.presence > 0.0) {
            VolcanoZones.Ground ground = VolcanoZones.ground(model, zone, x, y, z);
            if (ground.bare()) {
                if (VolcanoZones.snow(model, zone, x, z, s.temperature, snowy)) {
                    if (snowy) {
                        return Fades.rockiness(model, x, z, smooth, 1.05) > 0.0 ? strata(model, x, y, z) : rgb(240, 244, 250);
                    }
                    if (smooth < 1.0) {
                        return rgb(240, 244, 250);
                    }
                }
                return groundColor(ground, x, z, model);
            }
            if (ground != VolcanoZones.Ground.SOIL && !snowy) {
                return groundColor(ground, x, z, model);
            }
        }
        if (snowy) {
            return Fades.rockiness(model, x, z, smooth, 1.05) > 0.0 ? strata(model, x, y, z) : rgb(240, 244, 250);
        }
        if (s.temperature < -0.3 && smooth < 1.0 && 0.5 + 0.5 * Fades.patch(model, x, z, 16.0, 6) < Math.min(0.9, (-0.3 - s.temperature) / 0.17 * 0.8)) {
            return rgb(240, 244, 250);
        }
        return rgb(92, 150, 58);
    }

    private static int groundColor(VolcanoZones.Ground ground, int x, int z, TerrainModel model) {
        return switch (ground) {
            case ASH, CINDERS -> rgb(132, 124, 122);
            case TUFF -> rgb(108, 109, 101);
            case SCORIA -> rgb(72, 72, 78);
            case BASALT -> rgb(88, 88, 94);
            case LAVA_ROCK -> rgb(40, 35, 40);
            case MAGMA -> rgb(150, 66, 30);
            case RED_SCORIA -> rgb(143, 61, 46);
            case BROWN_SCORIA -> rgb(77, 51, 35);
            case ALTERED -> rgb(149, 103, 85);
            case CHARRED -> rgb(91, 63, 24);
            case SCORCHED -> rgb(119, 85, 59);
            case BURNT_ROOTS -> rgb(144, 103, 76);
            case SOIL -> rgb(92, 150, 58);
        };
    }

    /** Colour of the volcanic strata (blackstone, basalt, smooth basalt) at a block. */
    private static int strata(TerrainModel model, int x, int y, int z) {
        int stop = Fades.strataStop(model, 3, x, y, z, Fades.strataWarp(model, x, z), Fades.ROCK_PERIOD, 0.0);
        return stop == 0 ? rgb(42, 36, 41) : stop == 1 ? rgb(86, 86, 92) : rgb(70, 70, 76);
    }

    // =========================================================================================
    // rendering
    // =========================================================================================

    /** A perspective view of the block grid from a camera, {@code yaw} 0 looking east (+x), {@code pitch} up. */
    private static BufferedImage view(Grid g, double camX, double camY, double camZ, double yaw, double pitch) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        double focal = (W / 2.0) / Math.tan(FOV / 2.0);
        double[] fwd = {Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch)};
        double[] right = {-Math.sin(yaw), 0.0, Math.cos(yaw)};
        double[] up = {
            right[1] * fwd[2] - right[2] * fwd[1],
            right[2] * fwd[0] - right[0] * fwd[2],
            right[0] * fwd[1] - right[1] * fwd[0]};
        if (up[1] < 0.0) {
            up[0] = -up[0];
            up[1] = -up[1];
            up[2] = -up[2];
        }
        double maxT = g.n * 1.5;
        IntStream.range(0, H).parallel().forEach(py -> {
            for (int px = 0; px < W; px++) {
                double sx = (px + 0.5 - W / 2.0) / focal;
                double sy = -(py + 0.5 - H / 2.0) / focal;
                double dx = fwd[0] + sx * right[0] + sy * up[0];
                double dy = fwd[1] + sx * right[1] + sy * up[1];
                double dz = fwd[2] + sx * right[2] + sy * up[2];
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= len;
                dy /= len;
                dz /= len;
                img.setRGB(px, py, trace(g, camX, camY, camZ, dx, dy, dz, maxT));
            }
        });
        return img;
    }

    private static int sky(double dy) {
        double t = Math.max(0.0, Math.min(1.0, dy * 2.0));
        return rgb(170 - 60 * t, 200 - 45 * t, 245 - 10 * t);
    }

    /** Marches a ray through the grid's columns cell by cell and shades the first face it meets. */
    private static int trace(Grid g, double ox, double oy, double oz, double dx, double dy, double dz, double maxT) {
        int ix = (int) Math.floor(ox);
        int iz = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double tDeltaX = Math.abs(dx) < 1e-12 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tDeltaZ = Math.abs(dz) < 1e-12 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double tMaxX = Math.abs(dx) < 1e-12 ? Double.MAX_VALUE : ((dx > 0 ? ix + 1 : ix) - ox) / dx;
        double tMaxZ = Math.abs(dz) < 1e-12 ? Double.MAX_VALUE : ((dz > 0 ? iz + 1 : iz) - oz) / dz;
        double tEnter = 0.0;
        int axis = -1;
        while (tEnter < maxT) {
            int k = g.index(ix, iz);
            double tExit = Math.min(tMaxX, tMaxZ);
            if (k >= 0) {
                int top = Math.max(g.top[k], g.liquid[k]) + 1;
                double yEnter = oy + dy * tEnter;
                if (axis >= 0 && yEnter <= top) {
                    // the side of this column, facing the cell the ray came from
                    int y = (int) Math.floor(yEnter);
                    int c;
                    if (g.liquid[k] != Integer.MIN_VALUE && y > g.top[k]) {
                        c = g.lava[k] ? rgb(220, 100, 20) : rgb(50, 90, 200);
                    } else {
                        c = y >= g.top[k] ? g.color[k] : g.face[k];
                    }
                    double shade = g.lava[k] && y > g.top[k] ? 1.0 : axis == 0 ? 0.78 : 0.62;
                    return fog(scale(c, shade), tEnter, maxT, dy);
                }
                double yExit = oy + dy * tExit;
                if (yExit <= top && dy < 0.0) {
                    double t = (top - oy) / dy;
                    double hx = ox + dx * t - ix;
                    double hz = oz + dz * t - iz;
                    boolean wet = g.liquid[k] != Integer.MIN_VALUE && g.liquid[k] >= g.top[k];
                    int c = wet ? g.lava[k] ? rgb(225, 105, 22) : rgb(55, 98, 205) : g.color[k];
                    double shade = wet && g.lava[k] ? 1.0 : occlusion(g, ix, iz, top, hx, hz);
                    return fog(scale(c, shade), t, maxT, dy);
                }
            } else if (tEnter > 4.0) {
                return sky(dy);
            }
            if (tMaxX < tMaxZ) {
                ix += stepX;
                tEnter = tMaxX;
                tMaxX += tDeltaX;
                axis = 0;
            } else {
                iz += stepZ;
                tEnter = tMaxZ;
                tMaxZ += tDeltaZ;
                axis = 1;
            }
        }
        return sky(dy);
    }

    /** Darkens a top face along the edges where the neighbouring column stands higher, like smooth lighting. */
    private static double occlusion(Grid g, int ix, int iz, int top, double hx, double hz) {
        double ao = 1.0;
        int[][] nb = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        double[] edge = {hx, 1.0 - hx, hz, 1.0 - hz};
        for (int i = 0; i < 4; i++) {
            int k = g.index(ix + nb[i][0], iz + nb[i][1]);
            if (k >= 0 && Math.max(g.top[k], g.liquid[k]) + 1 > top) {
                ao *= 1.0 - 0.3 * Math.max(0.0, 1.0 - edge[i] / 0.6);
            }
        }
        return ao;
    }

    private static int fog(int c, double t, double maxT, double dy) {
        double f = Math.min(0.85, Math.pow(t / maxT, 1.4));
        int s = sky(Math.max(0.0, dy) * 0.3);
        return rgb(((c >> 16) & 255) * (1 - f) + ((s >> 16) & 255) * f, ((c >> 8) & 255) * (1 - f) + ((s >> 8) & 255) * f,
            (c & 255) * (1 - f) + (s & 255) * f);
    }

    /**
     * Top-down view, one pixel per column, lit from the north-west so every step down draws a line;
     * {@code relief} draws the ground in one grey, to judge its shape without its materials.
     */
    private static BufferedImage top(Grid g, int bpp, boolean relief) {
        int n = g.n;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                boolean wet = g.liquid[k] != Integer.MIN_VALUE && g.liquid[k] >= g.top[k];
                int h = Math.max(g.top[k], g.liquid[k]);
                int hw = i > 0 ? Math.max(g.top[k - 1], g.liquid[k - 1]) : h;
                int hn = j > 0 ? Math.max(g.top[k - n], g.liquid[k - n]) : h;
                double shade = 1.0 + 0.35 * Math.max(-1.0, Math.min(1.0, ((h - hw) + (h - hn)) / (double) bpp));
                int c = wet ? g.lava[k] ? rgb(225, 105, 22) : rgb(55, 98, 205) : relief ? rgb(165, 165, 165) : g.color[k];
                img.setRGB(i, j, scale(c, wet ? 1.0 : Math.max(0.45, Math.min(1.4, shade))));
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
