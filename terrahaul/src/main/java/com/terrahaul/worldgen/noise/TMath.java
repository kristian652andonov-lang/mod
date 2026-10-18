package com.terrahaul.worldgen.noise;

/** Small shaping functions shared by the terrain model. */
public final class TMath {
    private TMath() {
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    /** Hermite smoothstep; edges may be given in either order. */
    public static double smoothstep(double edge0, double edge1, double v) {
        double t = clamp01((v - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    /** Smoother (quintic) step for transitions that must not show a visible crease. */
    public static double smootherstep(double edge0, double edge1, double v) {
        double t = clamp01((v - edge0) / (edge1 - edge0));
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** 0 outside [lo, hi], rising to 1 at {@code mid}. */
    public static double bump(double v, double lo, double mid, double hi) {
        if (v <= lo || v >= hi) {
            return 0.0;
        }
        return v < mid ? smoothstep(lo, mid, v) : smoothstep(hi, mid, v);
    }

    /**
     * Terrace function: flattens {@code v} into steps of height {@code step}; {@code sharpness} in
     * [0, 1] controls how much of each step is a flat bench (1 = vertical risers).
     */
    public static double terrace(double v, double step, double sharpness) {
        double k = v / step;
        double base = Math.floor(k);
        double f = k - base;
        double riserStart = sharpness;
        double rise = f < riserStart ? 0.0 : smoothstep(riserStart, 1.0, f);
        return (base + (1.0 - sharpness) * f + sharpness * rise) * step;
    }

    /** Soft upper limit: approaches {@code max} asymptotically instead of clipping flat. */
    public static double softMax(double v, double knee, double max) {
        if (v <= knee) {
            return v;
        }
        double range = max - knee;
        return knee + range * (1.0 - Math.exp(-(v - knee) / range));
    }

    /** Monotone piecewise-linear interpolation through control points, smoothed per segment. */
    public static double curve(double v, double[] xs, double[] ys) {
        if (v <= xs[0]) {
            return ys[0];
        }
        int last = xs.length - 1;
        if (v >= xs[last]) {
            return ys[last];
        }
        for (int i = 0; i < last; i++) {
            if (v < xs[i + 1]) {
                double t = (v - xs[i]) / (xs[i + 1] - xs[i]);
                // cosine-style smoothing keeps the profile C1-continuous without overshoot
                double s = t * t * (3.0 - 2.0 * t);
                double linear = ys[i] + (ys[i + 1] - ys[i]) * t;
                double smooth = ys[i] + (ys[i + 1] - ys[i]) * s;
                return 0.5 * (linear + smooth);
            }
        }
        return ys[last];
    }
}
