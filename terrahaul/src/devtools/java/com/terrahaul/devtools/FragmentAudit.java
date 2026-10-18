package com.terrahaul.devtools;

import com.terrahaul.dev.FloatingFragments;
import com.terrahaul.worldgen.density.TerrainDetail;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Development tool: rebuilds the terrain's solid/air structure offline, exactly as the noise chunk
 * does it but without vanilla caves - the 3D terrain density ({@link TerrainDetail}) evaluated at
 * the corners of the 4x8x4 interpolation cells and trilinearly interpolated to blocks - and counts
 * floating fragments with {@link FloatingFragments}. This isolates what ledges, curved overhangs,
 * crags, alcoves and sea caves contribute; the in-game {@code AutoTest} measures the full result
 * including caves.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code FragmentAudit scan <seed> <x> <z> <size> [depth=60]}: one area;</li>
 *   <li>{@code FragmentAudit find <seed> <target> <count> <size>}: areas around the first
 *       {@code count} matches of a finder target (e.g. overhang, canyon, sea_cliff, plateau);</li>
 *   <li>{@code FragmentAudit slice <seed> <x> <z> <x|z> <halfWidth> <y0> <y1> <out.png>}: a vertical
 *       section through the simulated blocks (solid grey, air sky blue, the model's 2D surface
 *       marked red), scaled up to about 900 pixels.</li>
 * </ul>
 */
public final class FragmentAudit {
    private FragmentAudit() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        TerrainModel model = new TerrainModel(Long.parseLong(args[1]), TerrainSettings.DEFAULT);
        switch (mode) {
            case "scan" -> {
                int depth = args.length > 5 ? Integer.parseInt(args[5]) : 60;
                report(model, Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), depth);
            }
            case "find" -> {
                TerrainFinder.Target target = TerrainFinder.targets().get(args[2]);
                int count = Integer.parseInt(args[3]);
                int size = Integer.parseInt(args[4]);
                int ox = 0;
                int oz = 0;
                long blocks = 0;
                int fragments = 0;
                for (int i = 0; i < count; i++) {
                    TerrainFinder.Result r = TerrainFinder.find(model, target, ox, oz, 20000, () -> false);
                    if (r == null) {
                        System.out.println("no more " + args[2]);
                        break;
                    }
                    FloatingFragments.Result res = report(model, r.x(), r.z(), size, 60);
                    blocks += res.blocks();
                    fragments += res.fragments();
                    ox = r.x() + 1500 + 700 * (i % 3);
                    oz = r.z() - 900 + 1100 * (i % 2);
                }
                System.out.printf(Locale.ROOT, "TOTAL %s: %d fragments, %d blocks%n", args[2], fragments, blocks);
            }
            case "slice" -> slice(model, Integer.parseInt(args[2]), Integer.parseInt(args[3]), args[4].equals("x"),
                Integer.parseInt(args[5]), Integer.parseInt(args[6]), Integer.parseInt(args[7]), new File(args[8]));
            case "view" -> view(model, Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                Integer.parseInt(args[5]), new File(args[6]));
            default -> throw new IllegalArgumentException(mode);
        }
    }

    static FloatingFragments.Result report(TerrainModel model, int cx, int cz, int size, int depth) {
        Field f = Field.build(model, cx, cz, size);
        int[] top = new int[size * size];
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                top[dx * size + dz] = f.top(dx, dz);
            }
        }
        FloatingFragments.Result r = FloatingFragments.scan((x, y, z) -> f.solid(x - f.x0, y, z - f.z0), f.x0, f.z0, size, size, top, depth, 5);
        System.out.printf(Locale.ROOT, "area %d %d size %d (y %d..%d): %s%n", cx, cz, size, f.y0, f.y0 + (f.ny - 1) * 8, r.summary());
        for (FloatingFragments.Fragment fr : r.largest()) {
            System.out.println("    " + fr);
        }
        return r;
    }

    private static void slice(TerrainModel model, int cx, int cz, boolean alongX, int half, int y0, int y1, File out) throws Exception {
        int size = (half * 2 + 8) & ~3;
        Field f = Field.build(model, cx, cz, size);
        int w = half * 2 + 1;
        int hgt = y1 - y0 + 1;
        int sc = Math.max(1, Math.min(6, 900 / Math.max(w, hgt)));
        BufferedImage img = new BufferedImage(w * sc, hgt * sc, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < w; i++) {
            int bx = alongX ? cx - half + i : cx;
            int bz = alongX ? cz : cz - half + i;
            int surface = (int) Math.floor(model.interpolatedHeight(bx, bz));
            for (int y = y0; y <= y1; y++) {
                boolean solid = y >= f.y0 && y <= f.y0 + (f.ny - 1) * 8 && f.solid(bx - f.x0, y, bz - f.z0);
                int rgb = solid ? 0x808080 : (y < model.seaLevel() ? 0x3050C0 : 0xA8C8FF);
                if (y == surface) {
                    rgb = solid ? 0xC04040 : 0xFF6060;
                }
                for (int a = 0; a < sc; a++) {
                    for (int b = 0; b < sc; b++) {
                        img.setRGB(i * sc + a, (y1 - y) * sc + b, rgb);
                    }
                }
            }
        }
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    /**
     * Isometric block view of the simulated terrain of a {@code size}-block square, seen from the
     * side given by {@code facing} (0: from the south-east, 1: south-west, 2: north-west, 3:
     * north-east): grass-coloured tops, rock-coloured sides lit from the left, water blue.
     * Columns below {@code size / 2} blocks under the highest point are cut off.
     */
    private static void view(TerrainModel model, int cx, int cz, int size, int facing, File out) throws Exception {
        Field f = Field.build(model, cx, cz, size + 8);
        int ox = (cx - size / 2) - f.x0;
        int oz = (cz - size / 2) - f.z0;
        int hi = Integer.MIN_VALUE;
        for (int i = 0; i < size; i++) {
            for (int k = 0; k < size; k++) {
                hi = Math.max(hi, f.top(ox + i, oz + k));
            }
        }
        int sl = model.seaLevel();
        int lo = Math.max(f.y0 + 8, Math.min(hi - size, sl - 12));
        int sc = Math.max(3, 1500 / (size * 2));
        int w = size * 2 * sc + 16;
        int hgt = size * sc + (hi - lo) * sc * 6 / 5 + 16;
        BufferedImage img = new BufferedImage(w, hgt, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(0xB8D4F0));
        g.fillRect(0, 0, w, hgt);
        // screen axes: "a" runs to the lower right, "b" to the lower left; per facing, the grid
        // directions they follow (the near sides of the area are the ones drawn last)
        int[][] axes = {{1, 0, 0, 1}, {0, 1, -1, 0}, {-1, 0, 0, -1}, {0, -1, 1, 0}};
        int[] ax = axes[facing & 3];
        for (int depth = 0; depth < 2 * size - 1; depth++) {
            for (int a = Math.max(0, depth - size + 1); a <= Math.min(depth, size - 1); a++) {
                int b = depth - a;
                int i;
                int k;
                switch (facing & 3) {
                    case 1 -> {
                        i = size - 1 - b;
                        k = a;
                    }
                    case 2 -> {
                        i = size - 1 - a;
                        k = size - 1 - b;
                    }
                    case 3 -> {
                        i = b;
                        k = size - 1 - a;
                    }
                    default -> {
                        i = a;
                        k = b;
                    }
                }
                int bx = ox + i;
                int bz = oz + k;
                int stepAx = ax[0];
                int stepAz = ax[1];
                int stepBx = ax[2];
                int stepBz = ax[3];
                int colTop = Math.max(f.top(bx, bz), sl - 1);
                for (int y = lo; y <= colTop; y++) {
                    boolean solid = f.solid(bx, y, bz);
                    boolean water = !solid && y < sl;
                    if (!solid && (!water || y != sl - 1)) {
                        continue;
                    }
                    int u = w / 2 + (a - b) * sc;
                    int v = 8 + (a + b) * sc / 2 + (hi - y) * sc * 6 / 5;
                    if (water) {
                        fill(g, 0x3A6FD0, u, v, u + sc, v + sc / 2, u, v + sc, u - sc, v + sc / 2);
                        continue;
                    }
                    boolean airAbove = !f.solid(bx, y + 1, bz);
                    boolean airA = !f.solid(bx + stepAx, y, bz + stepAz);
                    boolean airB = !f.solid(bx + stepBx, y, bz + stepBz);
                    int hs = sc * 6 / 5;
                    if (airA) {
                        fill(g, 0x8A8076, u + sc, v + sc / 2, u + sc, v + sc / 2 + hs, u, v + sc + hs, u, v + sc);
                    }
                    if (airB) {
                        fill(g, 0x6A625A, u - sc, v + sc / 2, u, v + sc, u, v + sc + hs, u - sc, v + sc / 2 + hs);
                    }
                    if (airAbove) {
                        fill(g, y < sl + 2 ? 0xD8C890 : 0x7FA860, u, v, u + sc, v + sc / 2, u, v + sc, u - sc, v + sc / 2);
                    }
                }
            }
        }
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    private static void fill(java.awt.Graphics2D g, int rgb, int... xy) {
        int n = xy.length / 2;
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = xy[2 * i];
            ys[i] = xy[2 * i + 1];
        }
        g.setColor(new java.awt.Color(rgb));
        g.fillPolygon(xs, ys, n);
    }

    /** Corner densities of an area and their trilinear interpolation, as in the noise chunk. */
    static final class Field {
        int x0;
        int z0;
        int y0;
        int nx;
        int ny;
        double[] d;

        static Field build(TerrainModel model, int cx, int cz, int size) {
            Field f = new Field();
            f.x0 = (cx - size / 2) & ~3;
            f.z0 = (cz - size / 2) & ~3;
            f.nx = size / 4 + 2;
            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;
            ColumnSample s = new ColumnSample();
            for (int i = 0; i < f.nx; i++) {
                for (int k = 0; k < f.nx; k++) {
                    model.sampleInto(f.x0 + i * 4, f.z0 + k * 4, s);
                    lo = Math.min(lo, s.height);
                    hi = Math.max(hi, s.height);
                }
            }
            f.y0 = (int) Math.floor((lo - 80) / 8.0) * 8;
            int yTop = (int) Math.ceil((hi + 64) / 8.0) * 8;
            f.ny = (yTop - f.y0) / 8 + 1;
            f.d = new double[f.nx * f.nx * f.ny];
            IntStream.range(0, f.nx).parallel().forEach(i -> {
                for (int k = 0; k < f.nx; k++) {
                    int x = f.x0 + i * 4;
                    int z = f.z0 + k * 4;
                    for (int j = 0; j < f.ny; j++) {
                        ColumnSample c = model.sample(x, z);
                        f.d[(i * f.nx + k) * f.ny + j] = TerrainDetail.density(model, c, x, f.y0 + j * 8, z);
                    }
                }
            });
            return f;
        }

        /** Solidity at block offset (dx, y, dz) from the area origin. */
        boolean solid(int dx, int y, int dz) {
            int i = dx >> 2;
            int k = dz >> 2;
            int j = (y - y0) >> 3;
            if (i < 0 || k < 0 || j < 0 || i + 1 >= nx || k + 1 >= nx || j + 1 >= ny) {
                return j < 0;
            }
            double fx = (dx & 3) / 4.0;
            double fz = (dz & 3) / 4.0;
            double fy = ((y - y0) & 7) / 8.0;
            double c000 = d[(i * nx + k) * ny + j];
            double c001 = d[(i * nx + k) * ny + j + 1];
            double c010 = d[(i * nx + k + 1) * ny + j];
            double c011 = d[(i * nx + k + 1) * ny + j + 1];
            double c100 = d[((i + 1) * nx + k) * ny + j];
            double c101 = d[((i + 1) * nx + k) * ny + j + 1];
            double c110 = d[((i + 1) * nx + k + 1) * ny + j];
            double c111 = d[((i + 1) * nx + k + 1) * ny + j + 1];
            double v0 = lerp(fz, lerp(fx, c000, c100), lerp(fx, c010, c110));
            double v1 = lerp(fz, lerp(fx, c001, c101), lerp(fx, c011, c111));
            return lerp(fy, v0, v1) > 0.0;
        }

        int top(int dx, int dz) {
            for (int y = y0 + (ny - 1) * 8 - 1; y >= y0; y--) {
                if (solid(dx, y, dz)) {
                    return y;
                }
            }
            return Integer.MIN_VALUE;
        }

        private static double lerp(double t, double a, double b) {
            return a + (b - a) * t;
        }
    }
}
