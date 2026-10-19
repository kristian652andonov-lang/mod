package com.terrahaul.worldgen.noise;

/**
 * A {@link Fractal} that can also return its exact gradient. Built from the same seed and
 * parameters as a {@code Fractal}, it produces bit-identical values, so it can replace one without
 * changing the world.
 * <p>
 * Rivers are the zero lines of such fields, and their distance is estimated as
 * {@code |f| / |grad f|} for every column of a valley. Finite differences need two extra
 * evaluations of the field (and of the domain warp in front of it); the analytic gradient comes
 * with the value at about a third of that cost.
 */
public final class GradientFractal {
    private static final double ROT_SIN = Math.sin(0.6435);
    private static final double ROT_COS = Math.cos(0.6435);

    private final SimplexNoise[] octaves;
    private final double lacunarity;
    private final double gain;
    private final double normaliser;

    public GradientFractal(long seed, int octaveCount, double lacunarity, double gain) {
        this.octaves = new SimplexNoise[octaveCount];
        for (int i = 0; i < octaveCount; i++) {
            this.octaves[i] = new SimplexNoise(SeedMixer.derive(seed, i + 1));
        }
        this.lacunarity = lacunarity;
        this.gain = gain;
        double amp = 1.0;
        double total = 0.0;
        for (int i = 0; i < octaveCount; i++) {
            total += amp;
            amp *= gain;
        }
        this.normaliser = 1.0 / total;
    }

    /** Fractal Brownian motion, identical to {@link Fractal#fbm(double, double)}. */
    public double fbm(double x, double y) {
        double sum = 0.0;
        double amp = 1.0;
        double px = x;
        double py = y;
        for (SimplexNoise octave : octaves) {
            sum += amp * octave.noise2(px, py);
            double nx = (px * ROT_COS - py * ROT_SIN) * lacunarity + 17.31;
            double ny = (px * ROT_SIN + py * ROT_COS) * lacunarity - 9.73;
            px = nx;
            py = ny;
            amp *= gain;
        }
        return sum * normaliser;
    }

    /**
     * {@link #fbm(double, double)} together with its partial derivatives with respect to
     * {@code x} (stored in {@code out[0]}) and {@code y} ({@code out[1]}). Each octave is sampled
     * at {@code M p + c}, where {@code M} is the accumulated rotation and scaling, so its gradient
     * enters the sum as {@code M^T grad n}.
     */
    public double fbmGrad(double x, double y, double[] out) {
        double sum = 0.0;
        double gx = 0.0;
        double gy = 0.0;
        double amp = 1.0;
        double px = x;
        double py = y;
        // M = [[m00, m01], [m10, m11]] maps the input to the octave's coordinates
        double m00 = 1.0;
        double m01 = 0.0;
        double m10 = 0.0;
        double m11 = 1.0;
        for (SimplexNoise octave : octaves) {
            sum += amp * octave.noise2d(px, py, out);
            gx += amp * (m00 * out[0] + m10 * out[1]);
            gy += amp * (m01 * out[0] + m11 * out[1]);
            double nx = (px * ROT_COS - py * ROT_SIN) * lacunarity + 17.31;
            double ny = (px * ROT_SIN + py * ROT_COS) * lacunarity - 9.73;
            px = nx;
            py = ny;
            double n00 = (ROT_COS * m00 - ROT_SIN * m10) * lacunarity;
            double n01 = (ROT_COS * m01 - ROT_SIN * m11) * lacunarity;
            double n10 = (ROT_SIN * m00 + ROT_COS * m10) * lacunarity;
            double n11 = (ROT_SIN * m01 + ROT_COS * m11) * lacunarity;
            m00 = n00;
            m01 = n01;
            m10 = n10;
            m11 = n11;
            amp *= gain;
        }
        out[0] = gx * normaliser;
        out[1] = gy * normaliser;
        return sum * normaliser;
    }
}
