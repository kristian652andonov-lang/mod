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
    /** Gullies are cut {@code 1 + CUT_BIAS} times as deep as spurs are raised. */
    private static final double CUT_BIAS = 0.2;

    private final long gullySeed;

    MountainRelief(long seed) {
        this.gullySeed = SeedMixer.derive(seed, 1);
    }

    /**
     * Pyramidal peaks on a jittered grid of {@code cell} blocks; {@code occupancy} of the cells hold
     * one. Each peak has 3-5 arêtes at irregular angles and of different lengths: its radius peaks
     * in a cusp along every arête and drops to {@code face}-{@code face+0.25} of the reach between
     * them, so contour lines have sharp corners on the ridges and curve inwards between them like
     * cirque headwalls. The profile {@code (1-u)^power} is concave: steepest at the summit, easing
     * out towards the foot. Returns 0..1 (1 at the apex of the strongest peak).
     *
     * @param reach radius of the longest arêtes as a fraction of the cell
     */
    static double peaks(double x, double z, double cell, double occupancy, double reach, double face, double power, long seed) {
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        double maxReach = cell * reach * MAX_SPOKE;
        double best = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = SeedMixer.hash2(seed, cx + dx, cz + dz);
                if (SeedMixer.unit(h) >= occupancy) {
                    continue;
                }
                double px = x - siteX(h, cx + dx, cell);
                double pz = z - siteZ(h, cz + dz, cell);
                if (px * px + pz * pz < maxReach * maxReach) {
                    best = Math.max(best, peak(px, pz, h, cell * reach, face, power));
                }
            }
        }
        return best;
    }

    /** The longest arête relative to the reach of a peak: nothing lies beyond this radius. */
    static final double MAX_SPOKE = 1.25;

    /** Position of the peak in grid cell {@code cx} (see {@link #peaks}). */
    static double siteX(long h, int cx, double cell) {
        return (cx + 0.2 + 0.6 * SeedMixer.unit(SeedMixer.mix(h))) * cell;
    }

    static double siteZ(long h, int cz, double cell) {
        return (cz + 0.2 + 0.6 * SeedMixer.unit(SeedMixer.mix(SeedMixer.mix(h)))) * cell;
    }

    /**
     * One peak of {@link #peaks}, at offset {@code (px, pz)} from its apex, with its arêtes and
     * proportions drawn from the hash {@code h}. Returns 0..1.
     *
     * @param reach radius of the longest arêtes in blocks
     */
    static double peak(double px, double pz, long h, double reach, double face, double power) {
        long h3 = SeedMixer.mix(SeedMixer.mix(SeedMixer.mix(h)));
        long h4 = SeedMixer.mix(h3);
        double strength = 0.72 + 0.28 * SeedMixer.unit(h3);
        double faceR = face + 0.25 * SeedMixer.unit(h4);
        long a = SeedMixer.mix(h4);
        int count = 3 + (int) (SeedMixer.unit(a) * 2.999);
        double sector = TAU / count;
        a = SeedMixer.mix(a);
        double phase = SeedMixer.unit(a) * TAU;
        double angle = Math.atan2(pz, px);
        double spoke = 0.0;
        for (int k = 0; k < count; k++) {
            a = SeedMixer.mix(a);
            double phi = phase + sector * (k + 0.4 * (SeedMixer.unit(a) - 0.5));
            double length = 0.78 + 0.47 * SeedMixer.unit(SeedMixer.mix(a));
            double delta = angle - phi;
            delta -= TAU * Math.rint(delta / TAU);
            double c = 1.0 - Math.abs(delta) / (0.7 * sector);
            if (c > 0.0) {
                // c^2 falls linearly away from the arête: a corner in the contour, a sharp ridge
                spoke = Math.max(spoke, length * c * c);
            }
        }
        double radius = reach * (faceR + (1.0 - faceR) * spoke);
        double u = Math.sqrt(px * px + pz * pz) / radius;
        return u < 1.0 ? strength * Math.pow(1.0 - u, power) : 0.0;
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
