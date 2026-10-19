package com.terrahaul.worldgen.terrain;

import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;

/**
 * Building blocks of mountain relief for {@link TerrainModel}: pyramidal peaks with arêtes and an
 * analytic "gully erosion" filter. Both are pure functions of the column position, so they can be
 * evaluated one column at a time like the rest of the model.
 * <p>
 * Noise alone makes mountains that look like noise: rounded humps with the same texture
 * everywhere. Real alpine relief is organised by erosion: summits are pyramids where several
 * ridges meet, ridges are sharp, faces between them are hollowed into cirques and cut by couloirs,
 * and every flank is furrowed by gullies and spurs that run straight down the slope and branch
 * around each other. The peaks here supply the first part (star-shaped contours with a cusp at
 * every arête and a concave profile), and the gully filter the second.
 */
final class MountainRelief {
    private static final double TAU = Math.PI * 2.0;
    /** Kernel radius of the gully filter, in cells (jittered points sit within 0.25 of the cell centre). */
    private static final double GULLY_RADIUS = 1.25;
    /**
     * How strongly each octave's spurs bend the next octave's gullies. Some bending makes the
     * gullies branch; too much turns them into curls that no water would carve.
     */
    private static final double FEEDBACK = 0.4;
    /** Spread of the turn of an arête's bearing as it runs down from the summit (radians; half of it either way). */
    private static final double MEANDER = 0.32;
    /** Gullies are cut {@code 1 + CUT_BIAS} times as deep as spurs are raised. */
    private static final double CUT_BIAS = 0.2;

    private final long gullySeed;

    MountainRelief(long seed) {
        this.gullySeed = SeedMixer.derive(seed, 1);
    }

    /** The longest arête relative to the reach of a peak: nothing lies beyond this radius. */
    static final double MAX_SPOKE = 1.25;
    /** Most arêtes a peak has. */
    private static final int MAX_ARETES = 5;

    /**
     * Pyramidal peaks near a column, with their arêtes and proportions worked out (see
     * {@link #peaks}). They are collected once per column, and the column's finite-difference
     * neighbours reuse them.
     */
    static final class Sites {
        private static final int MAX = 9;
        final double[] x = new double[MAX];
        final double[] z = new double[MAX];
        /** Height of each peak (0.72..1 times the weight it was added with). */
        final double[] height = new double[MAX];
        /** Radius between the arêtes as a share of the reach. */
        final double[] face = new double[MAX];
        final double[] wander = new double[MAX];
        final double[] wave = new double[MAX];
        final double[] sector = new double[MAX];
        final int[] aretes = new int[MAX];
        final double[] bearing = new double[MAX * MAX_ARETES];
        final double[] length = new double[MAX * MAX_ARETES];
        final double[] dist = new double[MAX];
        int count;
        /** {@code (1-u)^power} of the peak that gave the last {@link #peaks} value (0 if none). */
        double rise;

        void clear() {
            count = 0;
        }

        /**
         * Adds the peak with hash {@code h} at {@code (sx, sz)}, {@code d2} squared blocks from the
         * column, keeping the nearest first. It has 3-5 arêtes at irregular angles and of different
         * lengths, and its radius between them is {@code faceMin}-{@code faceMin+0.25} of the reach.
         */
        void put(double sx, double sz, long h, double d2, double faceMin, double weight) {
            int i = count++;
            while (i > 0 && dist[i - 1] > d2) {
                move(i - 1, i);
                i--;
            }
            dist[i] = d2;
            x[i] = sx;
            z[i] = sz;
            long a = SeedMixer.mix(SeedMixer.mix(SeedMixer.mix(h)));
            height[i] = weight * (0.72 + 0.28 * SeedMixer.unit(a));
            a = SeedMixer.mix(a);
            face[i] = faceMin + 0.25 * SeedMixer.unit(a);
            a = SeedMixer.mix(a);
            int n = 3 + (int) (SeedMixer.unit(a) * 2.999);
            aretes[i] = n;
            sector[i] = TAU / n;
            a = SeedMixer.mix(a);
            double phase = SeedMixer.unit(a) * TAU;
            wave[i] = phase;
            wander[i] = MEANDER * (SeedMixer.unit(SeedMixer.mix(a + 1L)) - 0.5);
            for (int k = 0; k < n; k++) {
                a = SeedMixer.mix(a);
                bearing[i * MAX_ARETES + k] = phase + sector[i] * (k + 0.4 * (SeedMixer.unit(a) - 0.5));
                length[i * MAX_ARETES + k] = 0.78 + 0.47 * SeedMixer.unit(SeedMixer.mix(a));
            }
        }

        private void move(int from, int to) {
            dist[to] = dist[from];
            x[to] = x[from];
            z[to] = z[from];
            height[to] = height[from];
            face[to] = face[from];
            wander[to] = wander[from];
            wave[to] = wave[from];
            sector[to] = sector[from];
            aretes[to] = aretes[from];
            System.arraycopy(bearing, from * MAX_ARETES, bearing, to * MAX_ARETES, MAX_ARETES);
            System.arraycopy(length, from * MAX_ARETES, length, to * MAX_ARETES, MAX_ARETES);
        }
    }

    /**
     * Collects into {@code out} the peaks of a jittered grid of {@code cell} blocks
     * ({@code occupancy} of the cells hold one) that lie within {@code range} blocks of
     * {@code (x, z)}. A range up to 1.2 cells finds them all: the cells further away than one cell
     * lie at least 1.2 cells away.
     */
    static void collect(Sites out, double x, double z, double cell, double occupancy, double range, long seed, double face) {
        out.clear();
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = SeedMixer.hash2(seed, cx + dx, cz + dz);
                if (SeedMixer.unit(h) >= occupancy) {
                    continue;
                }
                double sx = siteX(h, cx + dx, cell);
                double sz = siteZ(h, cz + dz, cell);
                double d2 = (x - sx) * (x - sx) + (z - sz) * (z - sz);
                if (d2 < range * range) {
                    out.put(sx, sz, h, d2, face, 1.0);
                }
            }
        }
    }

    /**
     * Pyramidal peaks: the highest of the collected {@code sites} at {@code (x, z)} (0..1, 1 at the
     * apex of the highest peak). A peak's radius peaks in a cusp along every arête and drops
     * between them, so contour lines have sharp corners on the ridges and curve inwards between
     * them like cirque headwalls; the arêtes wander from side to side as they run down from the
     * summit (a straight ridge several kilometres long looks drawn with a ruler). The profile
     * {@code (1-u)^power} is concave: steepest at the summit, easing out towards the foot. The
     * nearest peaks come first, and the arêtes of the others are only worked out where they could
     * beat them (at the end of an arête of the greatest length).
     *
     * @param reach radius of the longest arêtes, in blocks
     */
    static double peaks(Sites sites, double x, double z, double reach, double power) {
        double best = 0.0;
        double rise = 0.0;
        for (int i = 0; i < sites.count; i++) {
            double px = x - sites.x[i];
            double pz = z - sites.z[i];
            double r = Math.sqrt(px * px + pz * pz);
            double face = sites.face[i];
            double far = r / (reach * (face + (1.0 - face) * MAX_SPOKE));
            if (far >= 1.0 || sites.height[i] * profile(1.0 - far, power) <= best) {
                continue;
            }
            double angle = atan2(pz, px) + sites.wander[i] * Math.sin(r / reach * 9.0 + sites.wave[i]);
            double sector = sites.sector[i];
            double spoke = 0.0;
            for (int k = 0; k < sites.aretes[i]; k++) {
                double delta = angle - sites.bearing[i * MAX_ARETES + k];
                delta -= TAU * Math.floor(delta / TAU + 0.5);
                double c = 1.0 - Math.abs(delta) / (0.7 * sector);
                if (c > 0.0) {
                    // c^2 falls linearly away from the arête: a corner in the contour, a sharp ridge
                    spoke = Math.max(spoke, sites.length[i * MAX_ARETES + k] * c * c);
                }
            }
            double u = r / (reach * (face + (1.0 - face) * spoke));
            if (u < 1.0) {
                double v = profile(1.0 - u, power);
                if (sites.height[i] * v > best) {
                    best = sites.height[i] * v;
                    rise = v;
                }
            }
        }
        sites.rise = rise;
        return best;
    }

    /** {@code t^power}, without {@code Math.pow} for the common power 2. */
    private static double profile(double t, double power) {
        return power == 2.0 ? t * t : Math.pow(t, power);
    }

    /** Position of the peak with hash {@code h} in grid cell {@code cx}: 0.2-0.8 cells into it. */
    static double siteX(long h, int cx, double cell) {
        return (cx + 0.2 + 0.6 * SeedMixer.unit(SeedMixer.mix(h))) * cell;
    }

    static double siteZ(long h, int cz, double cell) {
        return (cz + 0.2 + 0.6 * SeedMixer.unit(SeedMixer.mix(SeedMixer.mix(h)))) * cell;
    }

    /**
     * {@code Math.atan2} to within 1e-5 radians, several times faster (it is called for every
     * peak near every column). Continuous, like the exact function, except across the negative
     * x axis.
     */
    static double atan2(double y, double x) {
        double ax = Math.abs(x);
        double ay = Math.abs(y);
        double big = Math.max(ax, ay);
        if (big == 0.0) {
            return 0.0;
        }
        double a = Math.min(ax, ay) / big;
        double s = a * a;
        // minimax polynomial for atan on [0, 1]
        double r = ((((-0.0117212 * s + 0.05265332) * s - 0.11643287) * s + 0.19354346) * s - 0.33262347) * s * a + 0.99997726 * a;
        if (ay > ax) {
            r = Math.PI * 0.5 - r;
        }
        if (x < 0.0) {
            r = Math.PI - r;
        }
        return y < 0.0 ? -r : r;
    }

    /**
     * Gully erosion filter: furrows the terrain with gullies and spurs that run straight down the
     * slope, at {@code octaves} scales from {@code cell} blocks apart down, each 2.3 times finer.
     * <p>
     * Every octave blends stripe kernels centred on jittered grid points; each stripe pattern is
     * oriented along the local gradient, so its crests (spurs) and troughs (gullies) follow the fall
     * line. The gradient includes the previous octaves, so finer gullies run down the sides of the
     * coarser spurs and the pattern branches like a drainage network (an approach popularised by
     * Clay John's "eroded terrain" shader). Gentle ground gets no gullies: the depth fades in with
     * the slope. Summits and crests are kept sharp by the caller, which fades the depth out where
     * the fall line turns.
     *
     * @param g     the terrain gradient (blocks per block) on entry, smooth across crests; the
     *              gradient including the gullies on return
     * @param depth depth of the coarsest gullies in blocks
     * @return the height change in blocks (mostly negative)
     */
    double gullies(double x, double z, double[] g, double depth, double cell, int octaves) {
        double gx = g[0];
        double gz = g[1];
        double total = 0.0;
        double size = cell;
        double amp = depth;
        double r2 = GULLY_RADIUS * GULLY_RADIUS;
        // gentle ground has no gullies: there the fall line wanders, and stripes that follow it
        // would curl into worm-like patterns
        double baseSlope = Math.sqrt(gx * gx + gz * gz);
        double strength = smoothstep(0.12, 0.6, baseSlope);
        if (strength <= 0.0) {
            return 0.0;
        }
        for (int o = 0; o < octaves; o++) {
            double slope = Math.sqrt(gx * gx + gz * gz);
            if (slope > 1.0E-4) {
                // unit vector along the contour: the stripes vary across the slope, not down it
                double tx = -gz / slope;
                double tz = gx / slope;
                double px = x / size + o * 31.7;
                double pz = z / size - o * 17.3;
                int ix = (int) Math.floor(px);
                int iz = (int) Math.floor(pz);
                double sumW = 0.0;
                double sumC = 0.0;
                double sumS = 0.0;
                long seed = gullySeed + o * 0x9E3779B97F4A7C15L;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long h = SeedMixer.hash2(seed, ix + dx, iz + dz);
                        double qx = px - (ix + dx + 0.25 + 0.5 * SeedMixer.unit(h));
                        double qz = pz - (iz + dz + 0.25 + 0.5 * SeedMixer.unit(SeedMixer.mix(h)));
                        double d2 = qx * qx + qz * qz;
                        if (d2 >= r2) {
                            continue;
                        }
                        double w = 1.0 - d2 / r2;
                        w *= w;
                        double phase = (qx * tx + qz * tz) * TAU;
                        sumW += w;
                        sumC += w * Math.cos(phase);
                        sumS += w * Math.sin(phase);
                    }
                }
                if (sumW > 1.0E-6) {
                    double v = sumC / sumW;
                    double a = amp * strength;
                    // spurs rise a little, gullies are cut deeper: on average erosion removes rock
                    total += a * (v - CUT_BIAS);
                    // derivative of the stripes (kernel weights treated as constant), fed into
                    // the next octave so that its gullies bend around this octave's spurs; capped
                    // so the fall line turns by at most about 30 degrees
                    double dv = -sumS / sumW * TAU / size * a * FEEDBACK;
                    dv = Math.max(-0.55 * baseSlope, Math.min(0.55 * baseSlope, dv));
                    gx += dv * tx;
                    gz += dv * tz;
                }
            }
            size /= 2.3;
            amp /= 2.1;
        }
        g[0] = gx;
        g[1] = gz;
        return total;
    }
}
