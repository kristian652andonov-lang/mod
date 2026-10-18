package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * "Voxel space" perspective renderer for the development map tool: a camera at the southern edge
 * of the sampled area looks north, so silhouettes of mountain ranges, cliffs and valleys can be
 * judged the way a player would see them.
 */
final class Perspective {
    private Perspective() {
    }

    static BufferedImage render(ColumnSample[][] g, int bpp, int sl) {
        int n = g.length;
        int width = 1600;
        int height = 900;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            double t = y / (double) height;
            int sky = MapRenderer.rgb(120 + 80 * t, 165 + 60 * t, 235);
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, sky);
            }
        }
        // camera
        double camX = n / 2.0;
        double camZ = n - 1;
        ColumnSample under = g[n - 1][n / 2];
        double camH = Math.max(under.height, sl) + Double.parseDouble(System.getProperty("camHeight", "180"));
        double horizon = height * 0.32;
        double fovRad = Math.toRadians(80);
        double focal = (width / 2.0) / Math.tan(fovRad / 2.0);
        int[] ybuffer = new int[width];
        Arrays.fill(ybuffer, height);
        double fov = Math.toRadians(80);
        for (double dist = 1.0; dist < n * 1.1; dist += Math.max(0.5, dist * 0.004)) {
            double halfW = Math.tan(fov / 2) * dist;
            double z = camZ - dist;
            if (z < 0) {
                break;
            }
            double fog = Math.min(1.0, dist / (n * 1.05));
            for (int sx = 0; sx < width; sx++) {
                double x = camX - halfW + (2.0 * halfW) * sx / width;
                int ix = (int) x;
                int iz = (int) z;
                if (ix < 1 || ix >= n || iz < 1 || iz >= n) {
                    continue;
                }
                ColumnSample s = g[iz][ix];
                boolean water = s.height < sl - 0.3 || s.waterLevel > s.height;
                double h = water ? Math.max(sl, s.waterLevel) : s.height;
                int sy = (int) ((camH - h) / (dist * bpp) * focal + horizon);
                if (sy < ybuffer[sx]) {
                    double[] c = MapRenderer.baseColor(s, sl);
                    double hl = g[iz][ix - 1].height;
                    double hu = g[iz - 1][ix].height;
                    double shade = water ? 1.0 : Math.max(0.4, Math.min(1.5, 1.0 + ((s.height - hl) + (s.height - hu)) / bpp * 0.7));
                    double r = c[0] * shade * (1 - fog) + 175 * fog;
                    double gg = c[1] * shade * (1 - fog) + 200 * fog;
                    double b = c[2] * shade * (1 - fog) + 235 * fog;
                    int color = MapRenderer.rgb(r, gg, b);
                    for (int y = Math.max(0, sy); y < ybuffer[sx] && y < height; y++) {
                        img.setRGB(sx, y, color);
                    }
                    ybuffer[sx] = Math.max(0, sy);
                }
            }
        }
        return img;
    }
}
