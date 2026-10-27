package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.MagmaChamber;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Development tool: the magma chambers under volcanoes ({@link MagmaChamber}), checked and drawn
 * from the plan alone, without launching Minecraft.
 * <p>
 * For each volcano found near the origin (of the kind given, by what its crater holds) it prints
 * the volcano, the chamber's plan, how long planning took and where the {@code magma_chamber} and
 * {@code lava_crater} finder targets lead, and checks over every cell of the cavity:
 * <ul>
 *   <li>lava beside or above open air of the cavern (a wall of lava; must be 0), and falling lava
 *       that does not land in lava;</li>
 *   <li>how the lava and the air hang together: the lava (lake, conduit and fall) should be one
 *       body, the cavern's air one hall with perhaps a few small pockets;</li>
 *   <li>the rock over the cavity: columns where the model ground lies less than the wall's
 *       thickness above the highest cavity cell, apart from the vent, whose columns must all lie
 *       in the crater's lava lake (open conduits) or under the crater floor (plugged ones).</li>
 * </ul>
 * It renders a vertical section through the chamber's centre and the vent (lava orange, the fall
 * yellow, the cavern's air black, the wall grey, the ground brown, the crater's lava red above it,
 * sky blue) and a top view of the chamber's floor heights with the conduit's path.
 * <p>
 * A lava crater whose conduit could not be opened into its lake counts as a problem too.
 * <p>
 * Usage: {@code ChamberStudy <seed> <outDir> [count=4] [any|lava|lake|dry]}; the seed is a world
 * seed, or {@code model:N} for a terrain model seed (see {@link GameWorld}).
 */
public final class ChamberStudy {
    private ChamberStudy() {
    }

    public static void main(String[] args) throws Exception {
        GameWorld world = GameWorld.of(args[0]);
        File dir = new File(args[1]);
        dir.mkdirs();
        int count = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        String kind = args.length > 3 ? args[3] : "any";
        TerrainModel model = world.model;
        System.out.println(world);
        List<TerrainModel.VolcanoSite> found = new ArrayList<>();
        TerrainFinder.Target target = TerrainFinder.targets().get("volcano");
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
                String has = v == null ? "" : v.lavaLevel != ColumnSample.NO_WATER ? "lava" : v.waterLevel != ColumnSample.NO_WATER ? "lake" : "dry";
                if (v != null && (kind.equals("any") || kind.equals(has)) && found.stream().noneMatch(f -> f.x == v.x && f.z == v.z)) {
                    found.add(v);
                }
            }
            ring++;
        }
        int problems = 0;
        for (int i = 0; i < found.size(); i++) {
            problems += study(model, found.get(i), new File(dir, "chamber_" + world.label() + "_" + i + ".png"));
        }
        System.out.printf(Locale.ROOT, "%d volcanoes, %d problems%n", found.size(), problems);
    }

    private static int study(TerrainModel model, TerrainModel.VolcanoSite v, File out) throws Exception {
        String has = v.lavaLevel != ColumnSample.NO_WATER ? "lava" : v.waterLevel != ColumnSample.NO_WATER ? "lake" : "dry";
        System.out.printf(Locale.ROOT, "%nvolcano at %.0f %.0f: %s crater, height %.0f, radius %.0f, crater radius %.0f, base %.0f, lava %s, water %s%n",
            v.x, v.z, has, v.height, v.radius, v.craterRadius, v.baseHeight, level(v.lavaLevel), level(v.waterLevel));
        long t0 = System.nanoTime();
        MagmaChamber c = model.magmaChamber(v);
        double planMs = (System.nanoTime() - t0) / 1e6;
        System.out.printf(Locale.ROOT, "  %s%n  planned in %.1f ms, cavity box %d..%d x %d..%d x %d..%d%n", c.describe(), planMs,
            c.minX, c.maxX, c.minY, c.maxY, c.minZ, c.maxZ);
        for (String name : new String[]{"magma_chamber", "lava_crater"}) {
            TerrainFinder.Result r = TerrainFinder.find(model, TerrainFinder.targets().get(name), (int) v.x, (int) v.z, 3000, () -> false);
            System.out.printf(Locale.ROOT, "  finder %s: %s%n", name, r == null ? "none" : r.x() + " " + r.y() + " " + r.z());
        }

        // ---- the cavity, cell by cell -----------------------------------------------------------
        int nx = c.maxX - c.minX + 1;
        int ny = c.maxY - c.minY + 1;
        int nz = c.maxZ - c.minZ + 1;
        byte[] cells = new byte[nx * ny * nz];
        int[] col = new int[MagmaChamber.COLUMN_OUT];
        double[] axis = new double[2];
        long t1 = System.nanoTime();
        for (int iz = 0; iz < nz; iz++) {
            for (int ix = 0; ix < nx; ix++) {
                c.column(c.minX + ix, c.minZ + iz, col);
                for (int y = col[MagmaChamber.FLOOR]; y < col[MagmaChamber.CEILING]; y++) {
                    cells[idx(ix, y - c.minY, iz, nx, ny)] = (byte) (y < c.lake ? MagmaChamber.LAVA
                        : col[MagmaChamber.UNDER_MOUTH] != 0 ? MagmaChamber.FALL : MagmaChamber.AIR);
                }
            }
        }
        for (int y = c.top; y <= c.conduitTop; y++) {
            c.axis(y, axis);
            double rc = c.conduitRadius(y);
            for (int iz = 0; iz < nz; iz++) {
                for (int ix = 0; ix < nx; ix++) {
                    double dx = c.minX + ix + 0.5 - axis[0];
                    double dz = c.minZ + iz + 0.5 - axis[1];
                    if (dx * dx + dz * dz < rc * rc) {
                        cells[idx(ix, y - c.minY, iz, nx, ny)] = MagmaChamber.LAVA;
                    }
                }
            }
        }
        double rasterMs = (System.nanoTime() - t1) / 1e6;
        int lavaCells = 0;
        int airCells = 0;
        int fallCells = 0;
        int lavaWalls = 0;
        int badFalls = 0;
        String example = "";
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
        for (int iy = 0; iy < ny; iy++) {
            for (int iz = 0; iz < nz; iz++) {
                for (int ix = 0; ix < nx; ix++) {
                    int k = cells[idx(ix, iy, iz, nx, ny)];
                    if (k == MagmaChamber.AIR) {
                        airCells++;
                    } else if (k == MagmaChamber.FALL) {
                        fallCells++;
                        int below = get(cells, ix, iy - 1, iz, nx, ny, nz);
                        if (below != MagmaChamber.FALL && below != MagmaChamber.LAVA) {
                            badFalls++;
                        }
                    } else if (k == MagmaChamber.LAVA) {
                        lavaCells++;
                        for (int[] f : faces) {
                            if (get(cells, ix + f[0], iy + f[1], iz + f[2], nx, ny, nz) == MagmaChamber.AIR) {
                                lavaWalls++;
                                if (example.isEmpty()) {
                                    example = " e.g. at " + (c.minX + ix) + " " + (c.minY + iy) + " " + (c.minZ + iz);
                                }
                            }
                        }
                    }
                }
            }
        }
        int[] lavaParts = components(cells, nx, ny, nz, true);
        int[] airParts = components(cells, nx, ny, nz, false);
        System.out.printf(Locale.ROOT, "  cells: %d lava, %d fall, %d air (marked in %.0f ms); lava facing air: %d%s; falls not landing in lava: %d%n",
            lavaCells, fallCells, airCells, rasterMs, lavaWalls, example, badFalls);
        System.out.printf(Locale.ROOT, "  lava bodies: %d (largest %d cells); air spaces: %d (largest %d cells)%n",
            lavaParts[0], lavaParts[1], airParts[0], airParts[1]);

        // ---- the rock over the cavity --------------------------------------------------------------
        int thin = 0;
        int ventOutside = 0;
        double leastCover = Double.MAX_VALUE;
        ColumnSample s = new ColumnSample();
        for (int iz = 0; iz < nz; iz++) {
            for (int ix = 0; ix < nx; ix++) {
                int highest = Integer.MIN_VALUE;
                for (int iy = ny - 1; iy >= 0; iy--) {
                    if (cells[idx(ix, iy, iz, nx, ny)] != 0) {
                        highest = c.minY + iy;
                        break;
                    }
                }
                if (highest == Integer.MIN_VALUE) {
                    continue;
                }
                int x = c.minX + ix;
                int z = c.minZ + iz;
                double ground = model.interpolatedHeight(x, z);
                model.sampleInto(x & ~3, z & ~3, s);
                boolean underLake = s.lavaLevel == v.lavaLevel && v.lavaLevel != ColumnSample.NO_WATER;
                if (c.open && highest >= c.craterFloor) {
                    // the conduit's upper part, above the lowest crater floor: it must open into the crater's lava lake
                    if (!underLake || ground > c.conduitTop) {
                        ventOutside++;
                    }
                    continue;
                }
                if (c.open && underLake) {
                    // the conduit's shoulder under the throat: lava of the crater's lake lies over
                    // the rock above it, so a thin roof there seals lava from lava
                    continue;
                }
                double cover = ground - (highest + 1);
                leastCover = Math.min(leastCover, cover);
                if (cover < MagmaChamber.WALL + 2) {
                    thin++;
                }
            }
        }
        System.out.printf(Locale.ROOT, "  rock over the cavity: least %.1f blocks, %d columns under %d; vent columns outside the crater's lava: %d%n",
            leastCover, thin, MagmaChamber.WALL + 2, ventOutside);
        render(model, v, c, cells, nx, ny, nz, out);
        views(c, cells, nx, ny, nz);
        // small pools in hollows of the shore may stand apart; the lake, the fall and the conduit are one
        boolean joined = lavaParts[1] >= 0.95 * (lavaCells + fallCells);
        if (!joined) {
            System.out.println("  PROBLEM: the lake, the fall and the conduit are not one body of lava");
        }
        return (lavaWalls > 0 ? 1 : 0) + (badFalls > 0 ? 1 : 0) + (thin > 0 ? 1 : 0) + (ventOutside > 0 ? 1 : 0) + (joined ? 0 : 1)
            + (v.lavaLevel != ColumnSample.NO_WATER && !c.open ? 1 : 0);
    }

    /**
     * Viewpoints on the shore for screenshots: on sixteen bearings from the chamber's centre, the
     * first dry shore column with headroom from which the middle of the fall can be seen through
     * the cavern's air, with the camera (eye height) and the yaw and pitch that look at it.
     */
    private static void views(MagmaChamber c, byte[] cells, int nx, int ny, int nz) {
        int[] col = new int[MagmaChamber.COLUMN_OUT];
        double fallY = 0.5 * (c.lake + c.top);
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8.0;
            for (double r = c.radius * 0.4; r < c.radius * 1.3; r += 1.0) {
                int x = (int) Math.floor(c.x + Math.cos(a) * r);
                int z = (int) Math.floor(c.z + Math.sin(a) * r);
                c.column(x, z, col);
                if (col[MagmaChamber.FLOOR] < c.lake + 1 || col[MagmaChamber.CEILING] - col[MagmaChamber.FLOOR] < 5) {
                    continue;
                }
                double ex = x + 0.5;
                double ey = col[MagmaChamber.FLOOR] + 1.62;
                double ez = z + 0.5;
                // clear through air (or the fall) all the way to the middle of the fall
                double dx = c.x - ex;
                double dy = fallY - ey;
                double dz = c.z - ez;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                boolean clear = true;
                for (double t = 1.0; t < len - c.conduitRadius; t += 0.5) {
                    int k2 = get(cells, (int) Math.floor(ex + dx * t / len) - c.minX, (int) Math.floor(ey + dy * t / len) - c.minY,
                        (int) Math.floor(ez + dz * t / len) - c.minZ, nx, ny, nz);
                    if (k2 != MagmaChamber.AIR && k2 != MagmaChamber.FALL) {
                        clear = false;
                        break;
                    }
                }
                if (clear) {
                    double yaw = Math.toDegrees(Math.atan2(-dx, dz));
                    double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                    System.out.printf(Locale.ROOT, "  view %2d: %.1f,%.1f,%.1f,%.0f,%.0f (headroom %d, %.0f blocks from the fall)%n", k, ex, ey, ez, yaw, pitch,
                        col[MagmaChamber.CEILING] - col[MagmaChamber.FLOOR], Math.hypot(dx, dz));
                }
                break;
            }
        }
    }

    private static String level(double l) {
        return l == ColumnSample.NO_WATER ? "-" : String.format(Locale.ROOT, "%.0f", l);
    }

    private static int idx(int ix, int iy, int iz, int nx, int ny) {
        return (iz * ny + iy) * nx + ix;
    }

    private static int get(byte[] cells, int ix, int iy, int iz, int nx, int ny, int nz) {
        if (ix < 0 || iy < 0 || iz < 0 || ix >= nx || iy >= ny || iz >= nz) {
            return 0;
        }
        return cells[idx(ix, iy, iz, nx, ny)];
    }

    /** Number of 6-connected bodies of lava (lava and falls) or of air, and the size of the largest. */
    private static int[] components(byte[] cells, int nx, int ny, int nz, boolean lava) {
        boolean[] seen = new boolean[cells.length];
        int parts = 0;
        int largest = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int i = 0; i < cells.length; i++) {
            if (seen[i] || !matches(cells[i], lava)) {
                continue;
            }
            parts++;
            int size = 0;
            seen[i] = true;
            queue.add(i);
            while (!queue.isEmpty()) {
                int p = queue.poll();
                size++;
                int ix = p % nx;
                int iy = p / nx % ny;
                int iz = p / nx / ny;
                for (int[] d : steps) {
                    int qx = ix + d[0];
                    int qy = iy + d[1];
                    int qz = iz + d[2];
                    if (qx < 0 || qy < 0 || qz < 0 || qx >= nx || qy >= ny || qz >= nz) {
                        continue;
                    }
                    int q = idx(qx, qy, qz, nx, ny);
                    if (!seen[q] && matches(cells[q], lava)) {
                        seen[q] = true;
                        queue.add(q);
                    }
                }
            }
            largest = Math.max(largest, size);
        }
        return new int[]{parts, largest};
    }

    private static boolean matches(byte cell, boolean lava) {
        return lava ? cell == MagmaChamber.LAVA || cell == MagmaChamber.FALL : cell == MagmaChamber.AIR;
    }

    /** A vertical section through the chamber's centre and the vent, beside a top view of the chamber. */
    private static void render(TerrainModel model, TerrainModel.VolcanoSite v, MagmaChamber c, byte[] cells, int nx, int ny, int nz, File out)
        throws Exception {
        // the section runs along the line from the chamber's centre to the vent, extended both ways
        double ax = c.ventX - c.x;
        double az = c.ventZ - c.z;
        double len = Math.hypot(ax, az);
        if (len < 1.0) {
            ax = 1.0;
            az = 0.0;
            len = 1.0;
        }
        ax /= len;
        az /= len;
        int half = (int) Math.max(c.radius * 1.6, len + 40);
        int yTop = (int) Math.ceil(Math.max(c.conduitTop, v.baseHeight + v.height) + 20);
        int yBottom = c.minY - 20;
        int scale = 2;
        int w = (2 * half + 1) * scale;
        int h = (yTop - yBottom + 1) * scale;
        int side = (int) Math.ceil(c.radius * 1.4);
        int tw = (2 * side + 1) * scale;
        BufferedImage img = new BufferedImage(w + 20 + tw, Math.max(h, tw), BufferedImage.TYPE_INT_RGB);
        Color sky = new Color(150, 190, 230);
        Color ground = new Color(120, 92, 70);
        Color wall = new Color(70, 70, 76);
        Color lava = new Color(255, 120, 20);
        Color fall = new Color(255, 220, 60);
        Color air = new Color(10, 10, 12);
        Color crater = new Color(200, 40, 20);
        ColumnSample s = new ColumnSample();
        int[] colBuf = new int[MagmaChamber.COLUMN_OUT];
        for (int t = -half; t <= half; t++) {
            int x = (int) Math.floor(c.x + ax * t);
            int z = (int) Math.floor(c.z + az * t);
            double g = model.interpolatedHeight(x, z);
            model.sampleInto(x & ~3, z & ~3, s);
            for (int y = yBottom; y <= yTop; y++) {
                Color px;
                int k = cellAt(c, cells, nx, ny, nz, x, y, z);
                if (k == MagmaChamber.LAVA) {
                    px = lava;
                } else if (k == MagmaChamber.FALL) {
                    px = fall;
                } else if (k == MagmaChamber.AIR) {
                    px = air;
                } else if (y < g && nearCavity(c, cells, nx, ny, nz, x, y, z)) {
                    px = wall;
                } else if (y < g) {
                    px = ground;
                } else if (s.lavaLevel != ColumnSample.NO_WATER && y < s.lavaLevel) {
                    px = crater;
                } else {
                    px = sky;
                }
                int py = (yTop - y) * scale;
                int pxx = (t + half) * scale;
                for (int a = 0; a < scale; a++) {
                    for (int b = 0; b < scale; b++) {
                        img.setRGB(pxx + a, py + b, px.getRGB());
                    }
                }
            }
        }
        // top view: the chamber's floor (lava where it lies under the lake), the conduit's path
        for (int dz = -side; dz <= side; dz++) {
            for (int dx = -side; dx <= side; dx++) {
                int x = (int) Math.floor(c.x) + dx;
                int z = (int) Math.floor(c.z) + dz;
                c.column(x, z, colBuf);
                Color px;
                if (colBuf[MagmaChamber.CEILING] <= colBuf[MagmaChamber.FLOOR]) {
                    px = wall;
                } else if (colBuf[MagmaChamber.FLOOR] < c.lake) {
                    px = colBuf[MagmaChamber.UNDER_MOUTH] != 0 ? fall : lava;
                } else {
                    int shade = Math.max(30, Math.min(230, 120 + (colBuf[MagmaChamber.FLOOR] - c.lake) * 12));
                    px = new Color(shade, shade, shade);
                }
                for (int a = 0; a < scale; a++) {
                    for (int b = 0; b < scale; b++) {
                        img.setRGB(w + 20 + (dx + side) * scale + a, (dz + side) * scale + b, px.getRGB());
                    }
                }
            }
        }
        double[] axis = new double[2];
        for (int y = c.top; y <= c.conduitTop; y += 2) {
            c.axis(y, axis);
            int px = w + 20 + (int) ((axis[0] - Math.floor(c.x) + side) * scale);
            int py = (int) ((axis[1] - Math.floor(c.z) + side) * scale);
            if (px >= w + 20 && px < img.getWidth() && py >= 0 && py < img.getHeight()) {
                img.setRGB(px, py, Color.RED.getRGB());
            }
        }
        ImageIO.write(img, "png", out);
        System.out.println("  wrote " + out);
    }

    private static int cellAt(MagmaChamber c, byte[] cells, int nx, int ny, int nz, int x, int y, int z) {
        return get(cells, x - c.minX, y - c.minY, z - c.minZ, nx, ny, nz);
    }

    private static boolean nearCavity(MagmaChamber c, byte[] cells, int nx, int ny, int nz, int x, int y, int z) {
        int wall = MagmaChamber.WALL;
        for (int dy = -wall; dy <= wall; dy++) {
            for (int dz = -wall; dz <= wall; dz++) {
                for (int dx = -wall; dx <= wall; dx++) {
                    if (cellAt(c, cells, nx, ny, nz, x + dx, y + dy, z + dz) != 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
