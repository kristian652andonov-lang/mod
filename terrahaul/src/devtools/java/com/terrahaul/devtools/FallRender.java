package com.terrahaul.devtools;

import java.awt.image.BufferedImage;

/**
 * Lit voxel renderer for the waterfall devtool ({@link FallStudy}): the real 3D terrain of a
 * solved area with the water the solver placed, shaded so a waterfall can be judged the way a
 * player sees it rather than as a debug map.
 * <p>
 * A ray is traced through the block grid for every pixel. Still water is see-through for a few
 * blocks; falling water is drawn as pale streaky blocks that let a little of the rock behind
 * show; solid faces take a colour from the material the scene reports (grass, soil, stone with
 * faint strata bands, snow, gravel, sand, moss) and are lit by a low sun with a shadow ray, so
 * hollows cut into a slope read as hollows. Distance haze softens the background.
 */
final class FallRender {
    static final int AIR = 0;
    static final int STONE = 1;
    static final int GRASS = 2;
    static final int DIRT = 3;
    static final int SNOW = 4;
    static final int SAND = 5;
    static final int GRAVEL = 6;
    static final int WATER = 7;
    static final int FALLING = 8;
    static final int MOSS = 9;
    static final int CUT = 10;
    static final int COBBLE = 11;
    static final int MOSSY = 12;

    /** What the renderer asks of a scene. */
    interface Scene {
        /** Material of the block at the given position ({@link #AIR} for air). */
        int block(int x, int y, int z);

        /** Highest solid or water block anywhere in the scene, so rays above it can stop. */
        int ceiling();
    }

    private static final double MAX_DIST = 320.0;
    private static final double[] SUN = norm(-0.45, 0.72, -0.53);

    private FallRender() {
    }

    private static double[] norm(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    /** Perspective view from {@code e} towards {@code t}. */
    static BufferedImage view(Scene s, double ex, double ey, double ez, double tx, double ty, double tz, int width, int height, double fovDeg) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double fx = tx - ex;
        double fy = ty - ey;
        double fz = tz - ez;
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl;
        fy /= fl;
        fz /= fl;
        double rx = -fz;
        double rz = fx;
        double rl = Math.sqrt(rx * rx + rz * rz);
        rx /= rl;
        rz /= rl;
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;
        double tan = Math.tan(Math.toRadians(fovDeg) / 2.0);
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                double sx = (2.0 * (px + 0.5) / width - 1.0) * tan;
                double sy = (1.0 - 2.0 * (py + 0.5) / height) * tan * height / width;
                double dx = fx + rx * sx + ux * sy;
                double dy = fy + uy * sy;
                double dz = fz + rz * sx + uz * sy;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                img.setRGB(px, py, trace(s, ex, ey, ez, dx / dl, dy / dl, dz / dl));
            }
        }
        return img;
    }

    /** Sky colour for a ray direction: paler towards the horizon. */
    private static int sky(double dy) {
        double t = Math.max(0.0, Math.min(1.0, dy * 2.5));
        return rgb(176 - 70 * t, 204 - 50 * t, 236 - 6 * t);
    }

    private static int trace(Scene s, double ox, double oy, double oz, double dx, double dy, double dz) {
        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double tDx = dx != 0 ? 1.0 / Math.abs(dx) : Double.MAX_VALUE;
        double tDy = dy != 0 ? 1.0 / Math.abs(dy) : Double.MAX_VALUE;
        double tDz = dz != 0 ? 1.0 / Math.abs(dz) : Double.MAX_VALUE;
        double tMaxX = dx != 0 ? (dx > 0 ? x + 1 - ox : ox - x) * tDx : Double.MAX_VALUE;
        double tMaxY = dy != 0 ? (dy > 0 ? y + 1 - oy : oy - y) * tDy : Double.MAX_VALUE;
        double tMaxZ = dz != 0 ? (dz > 0 ? z + 1 - oz : oz - z) * tDz : Double.MAX_VALUE;
        int face = 1;
        double t = 0.0;
        // accumulated colour of the transparent media passed (water, falling water)
        double cr = 0;
        double cg = 0;
        double cb = 0;
        double through = 1.0;
        double waterRun = 0.0;
        int ceiling = s.ceiling();
        while (t < MAX_DIST && through > 0.03) {
            if (y > ceiling && dy >= 0) {
                break;
            }
            int m = s.block(x, y, z);
            if (m == WATER) {
                // the first block of a stretch takes the surface colour, deeper ones darken it
                double a = waterRun == 0.0 ? 0.42 : 0.16;
                int base = waterRun == 0.0 && face == 1 ? 0x3F76E4 : 0x2B56B8;
                double light = waterRun == 0.0 && face == 1 ? 1.0 : 0.8;
                cr += through * a * ((base >> 16) & 255) * light;
                cg += through * a * ((base >> 8) & 255) * light;
                cb += through * a * (base & 255) * light;
                through *= 1.0 - a;
                waterRun += 1.0;
            } else if (m == FALLING) {
                // streaks: falling water is whiter where it is broken up, bluer where it is a sheet
                double streak = hash3(x, (int) Math.floor(y * 0.35 + hash3(x, 0, z) * 4.0), z);
                double white = 0.55 + 0.45 * streak;
                int r = (int) (150 + 100 * white);
                int g = (int) (185 + 68 * white);
                int b = 250;
                double shade = face == 1 ? 1.0 : face == 0 ? 0.93 : face == 3 ? 0.86 : 0.8;
                double a = 0.78;
                cr += through * a * r * shade;
                cg += through * a * g * shade;
                cb += through * a * b * shade;
                through *= 1.0 - a;
                waterRun = 0.0;
            } else if (m != AIR) {
                int rgb = surface(s, m, x, y, z, face, dx, dy, dz);
                rgb = haze(rgb, t);
                return rgb((cr + through * ((rgb >> 16) & 255)), (cg + through * ((rgb >> 8) & 255)), (cb + through * (rgb & 255)));
            } else {
                waterRun = 0.0;
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tDx;
                face = 0;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tDy;
                face = dy < 0 ? 1 : 2;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDz;
                face = 3;
            }
        }
        int sk = sky(dy);
        return rgb(cr + through * ((sk >> 16) & 255), cg + through * ((sk >> 8) & 255), cb + through * (sk & 255));
    }

    /** Colour of a solid face: material, sun (with a shadow ray) and a little per-block texture. */
    private static int surface(Scene s, int m, int x, int y, int z, int face, double dx, double dy, double dz) {
        double nx = 0;
        double ny = 0;
        double nz = 0;
        switch (face) {
            case 0 -> nx = dx > 0 ? -1 : 1;
            case 1 -> ny = 1;
            case 2 -> ny = -1;
            default -> nz = dz > 0 ? -1 : 1;
        }
        double lambert = Math.max(0.0, nx * SUN[0] + ny * SUN[1] + nz * SUN[2]);
        if (lambert > 0.0 && shadowed(s, x + 0.5 + nx * 0.51, y + 0.5 + ny * 0.51, z + 0.5 + nz * 0.51)) {
            lambert = 0.0;
        }
        double light = 0.45 + 0.65 * lambert;
        int base = material(m, x, y, z, face == 1);
        double tex = 0.9 + 0.1 * hash3(x, y, z);
        double f = light * tex;
        return rgb(((base >> 16) & 255) * f, ((base >> 8) & 255) * f, (base & 255) * f);
    }

    private static boolean shadowed(Scene s, double ox, double oy, double oz) {
        int ceiling = s.ceiling();
        for (double t = 0.0; t < 96.0; t += 0.7) {
            double px = ox + SUN[0] * t;
            double py = oy + SUN[1] * t;
            double pz = oz + SUN[2] * t;
            if (py > ceiling + 1) {
                return false;
            }
            int m = s.block((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz));
            if (m != AIR && m != WATER && m != FALLING) {
                return true;
            }
        }
        return false;
    }

    private static int material(int m, int x, int y, int z, boolean top) {
        return switch (m) {
            case GRASS -> top ? 0x6A9F42 : 0x7B5A3A;
            case DIRT -> 0x7B5A3A;
            case SNOW -> 0xEEF2F8;
            case SAND -> 0xD8CC96;
            case GRAVEL -> 0x8E8680;
            case MOSS -> 0x5B7A2E;
            case COBBLE -> 0x6E6E6A;
            case MOSSY -> 0x5F7056;
            case CUT -> 0x9A8F84;
            default -> {
                // stone with faint strata bands, like the surface rule's bedding
                double band = Math.sin(y * 0.55 + Math.sin(x * 0.05 + z * 0.04) * 1.5);
                int g = (int) (122 + 10 * band);
                yield (g << 16) | (g << 8) | (g + 4);
            }
        };
    }

    private static int haze(int rgb, double t) {
        double f = Math.min(0.75, Math.max(0.0, (t - 30.0) / 380.0));
        return rgb(((rgb >> 16) & 255) * (1 - f) + 182 * f, ((rgb >> 8) & 255) * (1 - f) + 204 * f, (rgb & 255) * (1 - f) + 232 * f);
    }

    /** Deterministic hash of a block position in [0, 1). */
    static double hash3(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65536.0;
    }

    static int rgb(double r, double g, double b) {
        int ri = (int) Math.max(0, Math.min(255, r));
        int gi = (int) Math.max(0, Math.min(255, g));
        int bi = (int) Math.max(0, Math.min(255, b));
        return (ri << 16) | (gi << 8) | bi;
    }

    /**
     * Top-down view of a square of {@code size} blocks centred on {@code (cx, cz)}, {@code scale}
     * pixels per block: the topmost block of each column, hill-shaded from the heights around.
     */
    static BufferedImage top(Scene s, int cx, int cz, int size, int scale) {
        BufferedImage img = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB);
        int x0 = cx - size / 2;
        int z0 = cz - size / 2;
        int[] hy = new int[(size + 2) * (size + 2)];
        int[] mat = new int[(size + 2) * (size + 2)];
        int ceiling = s.ceiling();
        for (int j = 0; j < size + 2; j++) {
            for (int i = 0; i < size + 2; i++) {
                int x = x0 + i - 1;
                int z = z0 + j - 1;
                int y = ceiling + 1;
                int m = AIR;
                while (y > ceiling - 600) {
                    m = s.block(x, y, z);
                    if (m != AIR) {
                        break;
                    }
                    y--;
                }
                hy[j * (size + 2) + i] = y;
                mat[j * (size + 2) + i] = m;
            }
        }
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int k = (j + 1) * (size + 2) + i + 1;
                int m = mat[k];
                int rgb;
                if (m == WATER) {
                    rgb = 0x3466C8;
                } else if (m == FALLING) {
                    rgb = 0xEAF4FF;
                } else {
                    // light from the north-west
                    double gx = hy[k + 1] - hy[k - 1];
                    double gz = hy[k + size + 2] - hy[k - size - 2];
                    double light = Math.max(0.35, Math.min(1.3, 0.95 - 0.12 * (gx * -0.7 + gz * -0.7)));
                    int base = material(m, x0 + i, hy[k], z0 + j, true);
                    rgb = rgb(((base >> 16) & 255) * light, ((base >> 8) & 255) * light, (base & 255) * light);
                }
                for (int a = 0; a < scale; a++) {
                    for (int b = 0; b < scale; b++) {
                        img.setRGB(i * scale + a, j * scale + b, rgb);
                    }
                }
            }
        }
        return img;
    }
}
