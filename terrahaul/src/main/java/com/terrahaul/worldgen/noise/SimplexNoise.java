package com.terrahaul.worldgen.noise;

/**
 * Seeded simplex noise in two and three dimensions.
 * <p>
 * Terrahaul evaluates its large-scale terrain fields with its own noise rather than vanilla
 * {@code NormalNoise} because the terrain model needs single-octave samples with analytic
 * derivatives (for ridge sharpening and erosion-style damping), which vanilla noise does not expose.
 * The implementation is immutable after construction and therefore safe to share between the
 * concurrent chunk-generation worker threads.
 */
public final class SimplexNoise {
    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;
    /** Normalises the 2D kernel sum to roughly [-1, 1]. */
    private static final double SCALE_2D = 99.0;
    private static final double SCALE_3D = 32.0;

    private static final int GRAD_COUNT_2D = 24;
    private static final double[] GRAD2_X = new double[GRAD_COUNT_2D];
    private static final double[] GRAD2_Y = new double[GRAD_COUNT_2D];
    private static final int[][] GRAD3 = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };

    static {
        // Evenly spaced unit gradients give an isotropic result without the 45-degree bias of the
        // classic eight-gradient set, which matters when the noise draws ridgelines and rivers.
        for (int i = 0; i < GRAD_COUNT_2D; i++) {
            double a = (i + 0.5) * (Math.PI * 2.0 / GRAD_COUNT_2D);
            GRAD2_X[i] = Math.cos(a);
            GRAD2_Y[i] = Math.sin(a);
        }
    }

    private final short[] perm = new short[512];
    private final short[] perm2 = new short[512];
    private final short[] perm3 = new short[512];

    public SimplexNoise(long seed) {
        short[] source = new short[256];
        for (short i = 0; i < 256; i++) {
            source[i] = i;
        }
        long state = seed;
        for (int i = 255; i >= 0; i--) {
            state = SeedMixer.next(state);
            int r = (int) Long.remainderUnsigned(state, i + 1);
            short tmp = source[i];
            source[i] = source[r];
            source[r] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = source[i & 255];
            perm2[i] = (short) (perm[i] % GRAD_COUNT_2D);
            perm3[i] = (short) (perm[i] % 12);
        }
    }

    private static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /** 2D simplex noise in roughly [-1, 1]. */
    public double noise2(double x, double y) {
        double s = (x + y) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        int i1 = x0 > y0 ? 1 : 0;
        int j1 = 1 - i1;
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;
        int ii = i & 255;
        int jj = j & 255;
        double n = 0.0;
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 > 0) {
            int g = perm2[ii + perm[jj]];
            t0 *= t0;
            n += t0 * t0 * (GRAD2_X[g] * x0 + GRAD2_Y[g] * y0);
        }
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 > 0) {
            int g = perm2[ii + i1 + perm[jj + j1]];
            t1 *= t1;
            n += t1 * t1 * (GRAD2_X[g] * x1 + GRAD2_Y[g] * y1);
        }
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 > 0) {
            int g = perm2[ii + 1 + perm[jj + 1]];
            t2 *= t2;
            n += t2 * t2 * (GRAD2_X[g] * x2 + GRAD2_Y[g] * y2);
        }
        return SCALE_2D * n;
    }

    /**
     * 2D simplex noise with analytic partial derivatives.
     *
     * @param out receives {@code d/dx} at index 0 and {@code d/dy} at index 1
     * @return the noise value, identical to {@link #noise2(double, double)}
     */
    public double noise2d(double x, double y, double[] out) {
        double s = (x + y) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        int i1 = x0 > y0 ? 1 : 0;
        int j1 = 1 - i1;
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;
        int ii = i & 255;
        int jj = j & 255;
        double n = 0.0;
        double dx = 0.0;
        double dy = 0.0;
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 > 0) {
            int g = perm2[ii + perm[jj]];
            double gx = GRAD2_X[g];
            double gy = GRAD2_Y[g];
            double dot = gx * x0 + gy * y0;
            double t20 = t0 * t0;
            double t40 = t20 * t20;
            n += t40 * dot;
            double k = -8.0 * t20 * t0 * dot;
            dx += k * x0 + t40 * gx;
            dy += k * y0 + t40 * gy;
        }
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 > 0) {
            int g = perm2[ii + i1 + perm[jj + j1]];
            double gx = GRAD2_X[g];
            double gy = GRAD2_Y[g];
            double dot = gx * x1 + gy * y1;
            double t21 = t1 * t1;
            double t41 = t21 * t21;
            n += t41 * dot;
            double k = -8.0 * t21 * t1 * dot;
            dx += k * x1 + t41 * gx;
            dy += k * y1 + t41 * gy;
        }
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 > 0) {
            int g = perm2[ii + 1 + perm[jj + 1]];
            double gx = GRAD2_X[g];
            double gy = GRAD2_Y[g];
            double dot = gx * x2 + gy * y2;
            double t22 = t2 * t2;
            double t42 = t22 * t22;
            n += t42 * dot;
            double k = -8.0 * t22 * t2 * dot;
            dx += k * x2 + t42 * gx;
            dy += k * y2 + t42 * gy;
        }
        out[0] = SCALE_2D * dx;
        out[1] = SCALE_2D * dy;
        return SCALE_2D * n;
    }

    /** 3D simplex noise in roughly [-1, 1]. */
    public double noise3(double x, double y, double z) {
        double s = (x + y + z) * F3;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);
        int k = fastFloor(z + s);
        double t = (i + j + k) * G3;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        double z0 = z - (k - t);
        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1;
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1;
            }
        } else {
            if (y0 < z0) {
                i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1;
            } else if (x0 < z0) {
                i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1;
            } else {
                i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            }
        }
        double x1 = x0 - i1 + G3, y1 = y0 - j1 + G3, z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0 * G3, y2 = y0 - j2 + 2.0 * G3, z2 = z0 - k2 + 2.0 * G3;
        double x3 = x0 - 1.0 + 3.0 * G3, y3 = y0 - 1.0 + 3.0 * G3, z3 = z0 - 1.0 + 3.0 * G3;
        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;
        double n = 0.0;
        double t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 > 0) {
            int[] g = GRAD3[perm3[ii + perm[jj + perm[kk]]]];
            t0 *= t0;
            n += t0 * t0 * (g[0] * x0 + g[1] * y0 + g[2] * z0);
        }
        double t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 > 0) {
            int[] g = GRAD3[perm3[ii + i1 + perm[jj + j1 + perm[kk + k1]]]];
            t1 *= t1;
            n += t1 * t1 * (g[0] * x1 + g[1] * y1 + g[2] * z1);
        }
        double t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2;
        if (t2 > 0) {
            int[] g = GRAD3[perm3[ii + i2 + perm[jj + j2 + perm[kk + k2]]]];
            t2 *= t2;
            n += t2 * t2 * (g[0] * x2 + g[1] * y2 + g[2] * z2);
        }
        double t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3;
        if (t3 > 0) {
            int[] g = GRAD3[perm3[ii + 1 + perm[jj + 1 + perm[kk + 1]]]];
            t3 *= t3;
            n += t3 * t3 * (g[0] * x3 + g[1] * y3 + g[2] * z3);
        }
        return SCALE_3D * n;
    }
}
