package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.water.WaterSolver;
import java.awt.image.BufferedImage;

/**
 * A first-person view of a solved area for the water devtool: a voxel ray caster over the
 * solved columns (ground, banks, sills, water, chutes), so a waterfall or cascade can be judged
 * from the viewpoint {@code /terrahaul find} sends the player to. Outside the solved area the
 * model ground is used. Colours are rough stand-ins for the blocks: grass on natural tops, grey
 * rock on faces and carved walls, sand on beds, blue water, white falling water.
 */
final class WaterView {
    private WaterView() {
    }

    static BufferedImage render(TerrainModel model, WaterStudy.Area area, double ex, double ey, double ez, double tx, double ty, double tz,
                                int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double fx = tx - ex;
        double fy = ty - ey;
        double fz = tz - ez;
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl;
        fy /= fl;
        fz /= fl;
        // right = forward x up, up' = right x forward
        double rx = -fz;
        double rz = fx;
        double rl = Math.sqrt(rx * rx + rz * rz);
        rx /= rl;
        rz /= rl;
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;
        double tan = Math.tan(Math.toRadians(70) / 2.0);
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                double sx = (2.0 * (px + 0.5) / width - 1.0) * tan;
                double sy = (1.0 - 2.0 * (py + 0.5) / height) * tan * height / width;
                double dx = fx + rx * sx + ux * sy;
                double dy = fy + uy * sy;
                double dz = fz + rz * sx + uz * sy;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                img.setRGB(px, py, trace(model, area, ex, ey, ez, dx / dl, dy / dl, dz / dl));
            }
        }
        return img;
    }

    /** Amanatides-Woo traversal of the voxel grid along one ray. */
    private static int trace(TerrainModel model, WaterStudy.Area area, double ox, double oy, double oz, double dx, double dy, double dz) {
        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double tMaxX = dx != 0 ? ((dx > 0 ? x + 1 - ox : ox - x) / Math.abs(dx)) : Double.MAX_VALUE;
        double tMaxY = dy != 0 ? ((dy > 0 ? y + 1 - oy : oy - y) / Math.abs(dy)) : Double.MAX_VALUE;
        double tMaxZ = dz != 0 ? ((dz > 0 ? z + 1 - oz : oz - z) / Math.abs(dz)) : Double.MAX_VALUE;
        double tDx = dx != 0 ? 1.0 / Math.abs(dx) : Double.MAX_VALUE;
        double tDy = dy != 0 ? 1.0 / Math.abs(dy) : Double.MAX_VALUE;
        double tDz = dz != 0 ? 1.0 / Math.abs(dz) : Double.MAX_VALUE;
        int face = 1;
        double t = 0.0;
        double waterT = -1.0;
        int waterColor = 0;
        while (t < 220.0) {
            int c = cell(model, area, x, y, z);
            if (c != 0) {
                int rgb = color(model, area, c, x, y, z, face);
                if (c == 3 && waterT < 0) {
                    // see through the first stretch of still water to what is below it
                    waterT = t;
                    waterColor = rgb;
                } else if (c != 3) {
                    if (waterT >= 0) {
                        rgb = blend(rgb, waterColor, Math.min(0.85, 0.45 + 0.08 * (t - waterT)));
                    }
                    return fog(rgb, t);
                }
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
        int sky = 0x9CC4F0;
        return waterT >= 0 ? fog(waterColor, waterT) : sky;
    }

    /**
     * 0 air, 1 natural ground, 2 built or carved rock, 3 still water, 4 falling water, 5 mossy
     * rock (dams, boulders, rock outcrops of cascades), 6 soil a plan or the solver shaped (grass
     * on top, as the game grows it again), 7 the bed of a pool.
     */
    private static int cell(TerrainModel model, WaterStudy.Area area, int x, int y, int z) {
        if (!area.contains(x, z)) {
            int g = (int) Math.ceil(model.interpolatedHeight(x, z)) - 1;
            if (y <= g) {
                return 1;
            }
            return y < model.seaLevel() ? 3 : 0;
        }
        int k = area.at(x, z);
        if (area.solid(k, y)) {
            boolean raised = y > area.ground[k];
            boolean carved = area.ground[k] < area.natural[k];
            boolean filled = y > area.natural[k];
            if (raised) {
                return area.step[k] == WaterSolver.SILL ? 2 : area.planned[k] && area.role[k] == WaterSolver.ROLE_DAM ? 5 : 6;
            }
            if (!area.planned[k]) {
                return carved ? 2 : 1;
            }
            boolean cascade = area.style[k] == WaterSolver.STYLE_CASCADE;
            return switch (area.role[k]) {
                case WaterSolver.ROLE_BOULDER -> 5;
                case WaterSolver.ROLE_DAM -> filled || cascade ? 5 : 1;
                case WaterSolver.ROLE_ROCK -> cascade && y >= area.ground[k] - 6 ? 5 : 2;
                case WaterSolver.ROLE_BED -> area.level[k] != WaterSolver.NONE ? 7 : 2;
                case WaterSolver.ROLE_SHORE -> 6;
                // soil cut down by four blocks or more is down to rock
                case WaterSolver.ROLE_NATURAL -> carved && area.natural[k] - area.ground[k] >= 4 ? 2 : filled || carved ? 6 : 1;
                default -> carved ? 2 : 1;
            };
        }
        if (area.source(k, y) || area.level[k] != WaterSolver.NONE && area.kind[k] == WaterSolver.SEA && y < area.level[k] && y > area.ground[k]) {
            return 3;
        }
        if (area.chute(k, y)) {
            return 4;
        }
        return 0;
    }

    private static int color(TerrainModel model, WaterStudy.Area area, int c, int x, int y, int z, int face) {
        double shade = face == 1 ? 1.0 : face == 0 ? 0.78 : face == 3 ? 0.66 : 0.5;
        int base;
        switch (c) {
            case 3 -> base = 0x3466D6;
            case 4 -> {
                double n = model.detail3(3, x * 0.7, y * 0.35, z * 0.7);
                base = n > 0.2 ? 0xF4FAFF : 0xC6E2FF;
                shade = Math.max(shade, 0.85);
            }
            case 2 -> base = 0x8C8C88;
            case 5 -> base = face == 1 ? 0x597D34 : 0x737C68;
            case 7 -> base = 0xCBBD8E;
            default -> {
                boolean top = !area.contains(x, z) ? face == 1 : y == area.solidTop[area.at(x, z)] && face == 1;
                base = top ? (y < model.seaLevel() + 1 ? 0xD8C890 : 0x5E9E3E) : 0x7A6E60;
            }
        }
        // a little per-block variation stands in for textures, so block edges can be told apart
        shade *= 0.92 + 0.08 * ((x * 73856093 ^ y * 19349663 ^ z * 83492791) & 7) / 7.0;
        int r = (int) (((base >> 16) & 255) * shade);
        int g = (int) (((base >> 8) & 255) * shade);
        int b = (int) ((base & 255) * shade);
        return WaterStudy.rgb(r, g, b);
    }

    private static int blend(int a, int b, double f) {
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return WaterStudy.rgb(r, g, bl);
    }

    private static int fog(int rgb, double t) {
        return blend(rgb, 0xB8D4F0, Math.min(0.7, t / 320.0));
    }
}
