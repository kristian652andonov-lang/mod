package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * "Voxel space" perspective renderer for the development tools: for every screen column a ray is
 * marched over the height field and the terrain it meets is drawn bottom to top, so silhouettes of
 * mountain ranges, cliffs and valleys can be judged the way a player would see them.
 * <p>
 * The terrain is lit by a low sun behind the camera's left shoulder (Lambert shading on the
 * surface normal), so faces are never all in shadow, and coloured roughly like the game does: snow
 * on cold, gentle ground, bare rock wherever the slope is too steep for snow or soil, vegetation
 * colours from the climate elsewhere, and aerial haze that grows with distance. Faces, arêtes and
 * gullies are therefore visible from their shading, not only from their outline.
 */
final class Perspective {
    private Perspective() {
    }

    /** Camera at the southern edge of the sampled grid looking north (MapRenderer's perspective mode). */
    static BufferedImage render(ColumnSample[][] g, int bpp, int sl) {
        HeightField f = HeightField.of(g, bpp, sl);
        double camX = f.minX + f.n * bpp / 2.0;
        double camZ = f.minZ + (f.n - 1) * bpp - 2.0;
        double camY = Math.max(f.height(camX, camZ), sl) + Double.parseDouble(System.getProperty("camHeight", "180"));
        return view(f, null, camX, camZ, camY, 0.0, 80.0, 0.32, 1600, 900);
    }

    /**
     * Renders the view from a camera.
     *
     * @param fine    detailed height field used wherever it covers the ray
     * @param coarse  optional wider, coarser field used beyond {@code fine}
     * @param yaw     viewing direction in degrees: 0 looks north (-z), 90 east (+x)
     * @param horizon screen height of the horizon as a fraction from the top
     */
    static BufferedImage view(HeightField fine, HeightField coarse, double camX, double camZ, double camY,
                              double yaw, double fovDeg, double horizon, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int horizonY = (int) (height * horizon);
        for (int y = 0; y < height; y++) {
            double t = Math.min(1.0, y / (double) Math.max(1, horizonY));
            int sky = MapRenderer.rgb(110 + 70 * t, 155 + 50 * t, 225 + 10 * t);
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, sky);
            }
        }
        double focal = (width / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2.0);
        // sun 38 degrees high, 140 degrees to the left of the viewing direction (north is -z)
        double az = Math.toRadians(yaw + 220.0);
        double el = Math.toRadians(38.0);
        double[] sun = {Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)};
        HeightField far = coarse != null ? coarse : fine;
        double maxDist = far.n * far.bpp * 1.2;
        double fogDist = maxDist * 0.45;
        int sl = fine.seaLevel;
        int[] column = new int[height];
        for (int sx = 0; sx < width; sx++) {
            double a = Math.atan((sx - width / 2.0) / focal);
            double dir = Math.toRadians(yaw) + a;
            double dx = Math.sin(dir);
            double dz = -Math.cos(dir);
            double cosA = Math.cos(a);
            int ybuf = height;
            Arrays.fill(column, -1);
            for (double t = 6.0; t < maxDist && ybuf > 0; t += Math.max(1.0, t * 0.0022)) {
                double px = camX + dx * t;
                double pz = camZ + dz * t;
                HeightField f;
                if (fine.contains(px, pz, fine.bpp)) {
                    f = fine;
                } else if (coarse != null && coarse.contains(px, pz, coarse.bpp)) {
                    f = coarse;
                } else {
                    continue;
                }
                double h = f.height(px, pz);
                int k = f.index(px, pz);
                byte kind = f.kind[k];
                boolean wet = kind == HeightField.SEA || kind == HeightField.FRESH_WATER;
                double surface = h;
                if (wet) {
                    surface = Math.max(h, f.water[k] > h ? f.water[k] : sl);
                }
                double depth = t * cosA;
                int sy = (int) (horizonY + (camY - surface) / depth * focal);
                if (sy >= ybuf) {
                    continue;
                }
                int color = shade(f, px, pz, h, k, kind, wet, surface, t, fogDist, sun);
                for (int y = Math.max(0, sy); y < ybuf; y++) {
                    img.setRGB(sx, y, color);
                }
                ybuf = Math.max(0, sy);
            }
        }
        return img;
    }

    private static int shade(HeightField f, double px, double pz, double h, int k, byte kind, boolean wet,
                             double surface, double dist, double fogDist, double[] sun) {
        double r;
        double g;
        double b;
        double light;
        if (wet) {
            double depth = Math.min(1.0, (surface - h) / 60.0);
            if (kind == HeightField.SEA) {
                r = 30 - 12 * depth;
                g = 95 - 45 * depth;
                b = 175 - 60 * depth;
            } else {
                r = 45;
                g = 115 - 30 * depth;
                b = 205 - 40 * depth;
            }
            light = 1.0;
        } else {
            double e = f.bpp;
            double hx = (f.height(px + e, pz) - f.height(px - e, pz)) / (2.0 * e);
            double hz = (f.height(px, pz + e) - f.height(px, pz - e)) / (2.0 * e);
            double slope = Math.sqrt(hx * hx + hz * hz);
            double len = Math.sqrt(hx * hx + 1.0 + hz * hz);
            double lambert = Math.max(0.0, (-hx * sun[0] + sun[1] - hz * sun[2]) / len);
            double temp = f.temperature[k];
            double hum = f.humidity[k];
            boolean snowy = temp < -0.45 || temp < 0.2 && f.peak[k] > 0.35;
            boolean stony = !snowy && temp < 0.55 && f.peak[k] > 0.35;
            double[] c;
            if (snowy && slope < 1.05) {
                c = new double[]{236, 240, 247};
            } else if (slope > 1.25 || snowy || stony) {
                c = new double[]{128, 124, 120};
            } else {
                c = switch (kind) {
                    case HeightField.DRY_RIVER -> new double[]{200, 180, 130};
                    case HeightField.VOLCANIC -> new double[]{70, 62, 60};
                    case HeightField.CANYON -> new double[]{190, 110, 65};
                    case HeightField.BEACH, HeightField.DESERT -> new double[]{222, 205, 145};
                    case HeightField.SWAMP -> new double[]{70, 100, 60};
                    default -> slope > 0.8
                        ? new double[]{110, 112, 96}
                        : new double[]{70 + 50 * temp - 30 * hum, 118 + 45 * hum, 52 + 10 * hum};
                };
            }
            r = c[0];
            g = c[1];
            b = c[2];
            light = 0.42 + 0.78 * lambert;
        }
        double fog = 1.0 - Math.exp(-dist / fogDist);
        return MapRenderer.rgb(r * light * (1 - fog) + 178 * fog, g * light * (1 - fog) + 198 * fog, b * light * (1 - fog) + 232 * fog);
    }
}
