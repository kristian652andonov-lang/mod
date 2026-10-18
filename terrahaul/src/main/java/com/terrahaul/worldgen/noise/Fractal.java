package com.terrahaul.worldgen.noise;

/**
 * Multi-octave noise built from independent {@link SimplexNoise} layers.
 * <p>
 * Each octave uses its own permutation and is rotated by an irrational angle so that the
 * summed field has no visible grid alignment, which is otherwise noticeable in long rivers and
 * mountain ridgelines at the continental scales Terrahaul uses.
 */
public final class Fractal {
    private static final double ROT_SIN = Math.sin(0.6435);
    private static final double ROT_COS = Math.cos(0.6435);

    private final SimplexNoise[] octaves;
    private final double lacunarity;
    private final double gain;
    private final double normaliser;

    public Fractal(long seed, int octaveCount, double lacunarity, double gain) {
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

    public int octaveCount() {
        return octaves.length;
    }

    /** Standard fractal Brownian motion, normalised to roughly [-1, 1]. */
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
     * Ridged multifractal in [0, 1]. Each octave is weighted by the previous one, which keeps the
     * crests sharp and lets detail accumulate along ridges while valleys stay comparatively smooth.
     */
    public double ridged(double x, double y, double sharpness) {
        double sum = 0.0;
        double amp = 1.0;
        double weight = 1.0;
        double px = x;
        double py = y;
        for (SimplexNoise octave : octaves) {
            double n = 1.0 - Math.abs(octave.noise2(px, py));
            n = Math.pow(Math.max(n, 0.0), sharpness);
            n *= weight;
            weight = Math.min(1.0, Math.max(0.0, n * 1.6));
            sum += n * amp;
            double nx = (px * ROT_COS - py * ROT_SIN) * lacunarity + 17.31;
            double ny = (px * ROT_SIN + py * ROT_COS) * lacunarity - 9.73;
            px = nx;
            py = ny;
            amp *= gain;
        }
        return Math.min(1.0, sum * normaliser);
    }

    /** Billowy noise in [0, 1]: rounded domes, used for ancient worn-down mountain ranges. */
    public double billow(double x, double y) {
        double sum = 0.0;
        double amp = 1.0;
        double px = x;
        double py = y;
        for (SimplexNoise octave : octaves) {
            sum += amp * Math.abs(octave.noise2(px, py));
            double nx = (px * ROT_COS - py * ROT_SIN) * lacunarity + 17.31;
            double ny = (px * ROT_SIN + py * ROT_COS) * lacunarity - 9.73;
            px = nx;
            py = ny;
            amp *= gain;
        }
        return Math.min(1.0, sum * normaliser);
    }

    /**
     * Derivative-damped fBm ("erosion fBm"). Octave contributions are divided by the accumulated
     * slope, so steep areas receive less fine detail than flat ones. The visual result resembles
     * terrain shaped by erosion: smooth talus slopes, crisp crests and branching gullies, without
     * running an actual erosion simulation (which would be non-local and could not be evaluated
     * one chunk at a time).
     *
     * @return value in roughly [-1, 1]
     */
    public double erodedFbm(double x, double y, double erosionStrength, double[] scratch) {
        double sum = 0.0;
        double amp = 1.0;
        double ddx = 0.0;
        double ddy = 0.0;
        double px = x;
        double py = y;
        double scale = 1.0;
        for (SimplexNoise octave : octaves) {
            double n = octave.noise2d(px, py, scratch);
            // derivatives in the un-rotated base frame are not required: only the magnitude is used
            ddx += scratch[0] * amp * scale;
            ddy += scratch[1] * amp * scale;
            sum += amp * n / (1.0 + erosionStrength * (ddx * ddx + ddy * ddy));
            double nx = (px * ROT_COS - py * ROT_SIN) * lacunarity + 17.31;
            double ny = (px * ROT_SIN + py * ROT_COS) * lacunarity - 9.73;
            px = nx;
            py = ny;
            amp *= gain;
            scale *= lacunarity;
        }
        return sum * normaliser;
    }
}
