package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.stream.IntStream;

/**
 * Development tool: a square patch of the terrain model sampled on a regular grid and reduced to
 * the few fields the perspective renderer needs (height, water, climate and a colour class), so
 * areas of several million columns fit in memory. Heights are interpolated bilinearly between
 * grid points, like the noise chunk interpolates the density between its 4-block corners.
 */
final class HeightField {
    static final byte LAND = 0;
    static final byte SEA = 1;
    static final byte FRESH_WATER = 2;
    static final byte DRY_RIVER = 3;
    static final byte VOLCANIC = 4;
    static final byte CANYON = 5;
    static final byte BEACH = 6;
    static final byte SWAMP = 7;
    static final byte DESERT = 8;

    final int minX;
    final int minZ;
    final int n;
    final int bpp;
    final float[] height;
    final float[] water;
    final float[] temperature;
    final float[] humidity;
    /** Mountain relief class: mountain weight times peakness, used for peak biomes. */
    final float[] peak;
    final byte[] kind;
    final int seaLevel;

    private HeightField(int minX, int minZ, int n, int bpp, int seaLevel) {
        this.minX = minX;
        this.minZ = minZ;
        this.n = n;
        this.bpp = bpp;
        this.seaLevel = seaLevel;
        int size = n * n;
        height = new float[size];
        water = new float[size];
        temperature = new float[size];
        humidity = new float[size];
        peak = new float[size];
        kind = new byte[size];
    }

    /** Samples {@code n x n} columns {@code bpp} blocks apart centred on {@code (cx, cz)}, in parallel. */
    static HeightField sample(TerrainModel model, int cx, int cz, int n, int bpp) {
        HeightField f = new HeightField(cx - n * bpp / 2, cz - n * bpp / 2, n, bpp, model.seaLevel());
        IntStream.range(0, n).parallel().forEach(j -> {
            ColumnSample s = new ColumnSample();
            for (int i = 0; i < n; i++) {
                model.sampleInto(f.minX + i * bpp, f.minZ + j * bpp, s);
                f.set(j * n + i, s);
            }
        });
        return f;
    }

    /** Wraps an already sampled grid (rows are z, columns x). */
    static HeightField of(ColumnSample[][] g, int bpp, int seaLevel) {
        int n = g.length;
        HeightField f = new HeightField(g[0][0].x, g[0][0].z, n, bpp, seaLevel);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                f.set(j * n + i, g[j][i]);
            }
        }
        return f;
    }

    private void set(int k, ColumnSample s) {
        height[k] = (float) s.height;
        water[k] = (float) s.waterLevel;
        temperature[k] = (float) s.temperature;
        humidity[k] = (float) s.humidity;
        peak[k] = (float) (s.mountain * s.peakness);
        byte c = LAND;
        if (s.waterLevel > s.height) {
            c = FRESH_WATER;
        } else if (s.height < seaLevel - 0.3) {
            c = s.river > 0 || s.lake > 0 ? FRESH_WATER : SEA;
        } else if (s.dryRiver && s.river > 0) {
            c = DRY_RIVER;
        } else if (s.volcano > 0.4) {
            c = VOLCANIC;
        } else if (s.canyon > 0.3 || s.baseTemperature > 0.55 && s.humidity < -0.1 && (s.plateau > 0.3 || s.erosion < 0.05)) {
            c = CANYON;
        } else if (s.beach > 0.5) {
            c = BEACH;
        } else if (s.swamp > 0.5) {
            c = SWAMP;
        } else if (s.baseTemperature > 0.55 && s.humidity < 0.1) {
            c = DESERT;
        }
        kind[k] = c;
    }

    boolean contains(double x, double z, double margin) {
        double max = (n - 1) * bpp;
        return x - minX >= margin && z - minZ >= margin && x - minX <= max - margin && z - minZ <= max - margin;
    }

    /** Bilinearly interpolated terrain height at a block position (clamped to the patch). */
    double height(double x, double z) {
        double gx = Math.max(0.0, Math.min(n - 1.001, (x - minX) / bpp));
        double gz = Math.max(0.0, Math.min(n - 1.001, (z - minZ) / bpp));
        int i = (int) gx;
        int j = (int) gz;
        double fx = gx - i;
        double fz = gz - j;
        int k = j * n + i;
        double a = height[k] + (height[k + 1] - height[k]) * fx;
        double b = height[k + n] + (height[k + n + 1] - height[k + n]) * fx;
        return a + (b - a) * fz;
    }

    /** Index of the grid point nearest to a block position. */
    int index(double x, double z) {
        int i = (int) Math.round((x - minX) / bpp);
        int j = (int) Math.round((z - minZ) / bpp);
        i = Math.max(0, Math.min(n - 1, i));
        j = Math.max(0, Math.min(n - 1, j));
        return j * n + i;
    }
}
