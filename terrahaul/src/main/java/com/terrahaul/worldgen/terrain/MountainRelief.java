package com.terrahaul.worldgen.terrain;

import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;

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
    /** Spread of the slant of the gullies off the fall line (about 25 degrees either way). */
    private static final double SLANT = 0.9;
    /** Each finer scale of gullies is this many times closer together than the one before. */
    private static final double OCTAVE_SCALE = 2.3;
    /**
     * Greatest cross slope of the gullies of one scale (spur crest to gully floor over half their
     * spacing, in blocks per block) on a slope of 45 degrees, less on gentler ground: steep enough
     * that spurs are knife edges between V-shaped gullies rather than ripples on a smooth face.
     */
    private static final double INCISION = 0.8;
    /**
     * Steepest slope (blocks per block) the ground is meant to reach across the gullies of all
     * scales on a face, about 70 degrees, and the least cross slope they keep even on the
     * steepest faces: those are cut by couloirs too (left smooth, they hung down the mountain like
     * curtains). Gullies cut without this budget left gully sides of over 76 degrees, and slots,
     * wherever the scales lined up on steep ground.
     */
    private static final double MAX_SLOPE = 2.8;
    private static final double MIN_CROSS = 0.5;
    /** Size of the patches of ledgy rock (noise scale, blocks): ledges some 30-100 blocks long. */
    private static final double LEDGE_PATCH = 110.0;
    /** Mean height between the ledges of a patch (blocks; see {@link #ledges}). */
    private static final double LEDGE_STEP = 36.0;
    /** Greatest shift of the ledges' levels across a mountain (blocks). */
    private static final double LEDGE_SHIFT = 60.0;

    private final long gullySeed;
    /** Patches of ledgy rock and the levels of their ledges (see {@link #ledges}). */
    private final SimplexNoise ledgeNoise;

    MountainRelief(long seed) {
        this.gullySeed = SeedMixer.derive(seed, 1);
        this.ledgeNoise = new SimplexNoise(SeedMixer.derive(seed, 2));
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
     * {@code (1-u)^power} (see {@link #profile}) is concave: steepest at the summit, easing out
     * towards the foot. The
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

    /**
     * {@code t^power} for powers near 2, without {@code Math.pow} (it is worked out several times
     * for every peak near every column): {@code t^2} scaled by a linear factor that gives it the
     * slope of {@code t^power} at the summit ({@code t = 1}). Exact for 2, and within 0.01 of the
     * power for 2.3.
     */
    private static double profile(double t, double power) {
        return t * t * (1.0 + (power - 2.0) * (t - 1.0));
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
     * Clay John's "eroded terrain" shader). The stripes are triangle waves of their blended phase,
     * so spurs are knife edges and gullies V-shaped notches, as on real alpine faces, not the
     * rounded ribs a sine wave gives. Every kernel has its own spacing and slants a little off the
     * fall line, so the gullies neither repeat evenly nor run exactly parallel. Gentle ground gets
     * no gullies: the depth fades in with the slope. The sides of each scale's gullies are kept
     * within a slope budget that shrinks on steep faces, so the scales together stay below about
     * 70 degrees even where they line up (deeper, they cut slots and sheer gully walls). Summits
     * are kept pointed by the caller, which fades the depth out where no fall line prevails.
     * <p>
     * Low mountains have short flanks, across which the coarsest gullies would make a gentle
     * swell rather than a furrow: there ({@code fine} towards 1) the coarsest octave gives way to
     * the next finer one, which then takes its depth, and one finer octave is added at the end, so
     * a low flank is furrowed as steeply as a tall one. The scales stay fixed (a scale that changed
     * with the relief would stretch the stripes wherever the relief changes), only their depths
     * shift.
     *
     * @param g     the terrain gradient (blocks per block) on entry, and in {@code g[2]} how far
     *              the gullies may bend around the spurs of the coarser octaves (0..1, see below);
     *              the gradient including the gullies on return
     * @param depth depth of the coarsest gullies in blocks
     * @param rise  how far the spurs rise above the uneroded surface, as a share of the gullies'
     *              amplitude (0..1); the gullies are cut one amplitude deep whatever it is
     * @param fine  how far the depth shifts from the coarsest octave to the finer ones (0..1)
     * @return the height change in blocks (mostly negative)
     */
    double gullies(double x, double z, double[] g, double depth, double cell, int octaves, double rise, double fine) {
        double gx = g[0];
        double gz = g[1];
        double total = 0.0;
        double size = cell;
        double r2 = GULLY_RADIUS * GULLY_RADIUS;
        double share = 1.0;
        if (fine > 0.0) {
            octaves++;
        }
        // gentle ground has no gullies: there the fall line wanders, and stripes that follow it
        // would curl into worm-like patterns
        double baseSlope = Math.sqrt(gx * gx + gz * gz);
        double strength = smoothstep(0.12, 0.6, baseSlope);
        if (strength <= 0.0) {
            return 0.0;
        }
        // how steeply the sides of one scale's gullies may fall across them (spur crest to gully
        // floor over half their spacing): the steeper the slope, the more, but on a steep face
        // the gullies of all scales together must not add up to sheer walls, so there the budget
        // for their sides shrinks until the ground stays below about MAX_SLOPE even where the
        // scales line up. A depth beyond that cut slots with sides of over 76 degrees.
        double cross = Math.sqrt(Math.max(MIN_CROSS * MIN_CROSS, MAX_SLOPE * MAX_SLOPE - baseSlope * baseSlope));
        double incision = Math.min(INCISION * Math.min(baseSlope, 1.5), cross / (1.3 * octaves));
        for (int o = 0; o < octaves; o++) {
            // depth of this octave: each finer octave 2.5 times shallower, the coarsest one
            // handing its depth on to the others where `fine` says so, and no deeper than the
            // budget above allows for its spacing (the triangle falls 1 + rise amplitudes over
            // half the spacing)
            double amp = Math.min(depth * share * (o == 0 ? 1.0 - fine : 1.0 + 1.5 * fine), 0.5 * incision * size / (1.0 + rise));
            double slope = Math.sqrt(gx * gx + gz * gz);
            // (an octave shallower than a quarter of a block changes no block)
            if (slope > 1.0E-4 && amp * strength > 0.25) {
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
                        long h2 = SeedMixer.mix(h);
                        double qx = px - (ix + dx + 0.15 + 0.7 * SeedMixer.unit(h));
                        double qz = pz - (iz + dz + 0.15 + 0.7 * SeedMixer.unit(h2));
                        double d2 = qx * qx + qz * qz;
                        if (d2 >= r2) {
                            continue;
                        }
                        double w = 1.0 - d2 / r2;
                        w *= w;
                        // every kernel's stripes have their own spacing and slant a little off the
                        // fall line, so gullies neither repeat like corrugated sheet nor all run
                        // exactly parallel down a face
                        // (from the low bits of the two hashes, which the position leaves unused)
                        double slant = SLANT * ((h & 0x7FF) * (1.0 / 2048.0) - 0.5);
                        double freq = 0.75 + 0.55 * (h2 & 0x7FF) * (1.0 / 2048.0);
                        // the phase in turns, folded into a quarter turn either side of 0 or of
                        // 1/2, where short polynomials give its sine and cosine (Math.sin and
                        // Math.cos took a quarter of the filter's time)
                        double turns = (qx * (tx + slant * tz) + qz * (tz - slant * tx)) * freq;
                        turns -= Math.floor(turns + 0.5);
                        double flip = 1.0;
                        if (turns > 0.25) {
                            turns = 0.5 - turns;
                            flip = -1.0;
                        } else if (turns < -0.25) {
                            turns = -0.5 - turns;
                            flip = -1.0;
                        }
                        double p = turns * TAU;
                        double p2 = p * p;
                        double sin = p * (1.0 + p2 * (-1.0 / 6.0 + p2 * (1.0 / 120.0 + p2 * (-1.0 / 5040.0 + p2 * (1.0 / 362880.0)))));
                        double cos = 1.0 + p2 * (-0.5 + p2 * (1.0 / 24.0 + p2 * (-1.0 / 720.0 + p2 * (1.0 / 40320.0 + p2 * (-1.0 / 3628800.0)))));
                        sumW += w;
                        sumC += w * cos * flip;
                        sumS += w * sin;
                    }
                }
                if (sumW > 1.0E-6) {
                    // the blended stripes as a phase and an amplitude: a cosine profile would round
                    // every spur into a hump and every gully into a trough, so the profile is a
                    // triangle wave of the phase instead, with a knife edge on each spur (phase 0)
                    // and a V-shaped notch in each gully (phase +-pi). Where the stripes of the
                    // kernels cancel the amplitude, and with it the pattern, fades to nothing.
                    // Where the kernels agree fairly well, the amplitude is raised towards full
                    // depth; where they nearly cancel (a gully forks there, and the phase turns
                    // right round within a few blocks) it is left as it is, which bounds the
                    // slope of the pattern. (Raised there as well, it stood needles and slots at
                    // every fork.)
                    double mag = Math.sqrt(sumC * sumC + sumS * sumS) / sumW;
                    mag *= 1.0 + (1.0 - mag) * smoothstep(0.25, 0.6, mag);
                    double v = mag * (1.0 - Math.abs(atan2(sumS, sumC)) * (2.0 / Math.PI));
                    double a = amp * strength;
                    // the gullies are cut into the uneroded surface and the spurs between them
                    // rise above it by `rise` of the amplitude: on a flank the full amount, so a
                    // face is furrowed rather than simply lowered, but on a crest little, so the
                    // crest is notched by the gullies that cross it and not stacked with spires
                    total += a * (v > 0.0 ? v * rise : v);
                    // derivative of the stripes (kernel weights treated as constant), fed into
                    // the next octave so that its gullies bend around this octave's spurs; capped
                    // so the fall line turns by at most about 30 degrees
                    // (the bend depends on which way the gradient points, which is ambiguous on a
                    // crest, where the caller leaves it out)
                    double dv = -sumS / sumW * TAU / size * a * FEEDBACK * g[2];
                    dv = Math.max(-0.55 * baseSlope, Math.min(0.55 * baseSlope, dv));
                    gx += dv * tx;
                    gz += dv * tz;
                }
            }
            size /= OCTAVE_SCALE;
            share /= 2.5;
        }
        g[0] = gx;
        g[1] = gz;
        return total;
    }

    /**
     * Small ledges and shoulders on steep mountain ground: the slope climbs, eases off for a few
     * blocks (up to about 15) and climbs again. Each ledge is a bench cut into the slope: within
     * a step of height the lowest part keeps a fifth of the slope and the rest is steepened, so
     * ground is only ever lowered, by at most two fifths of a step, never stands out of the slope
     * as a wall, and every bench falls outwards (a bench that rose towards its edge read as a rice
     * terrace).
     * <p>
     * Ledges are patchy: they form only in scattered patches of rock (about a fifth of the steep
     * ground) and fade out along the slope at the patch edges, so no ledge runs around a mountain
     * as a contour ring. The levels are the height warped so that successive ledges lie 20-90
     * blocks of height apart rather than at an even step, and shifted by an amount that changes
     * slowly across the mountain, so a patch holds one to three ledges at irregular heights, not a
     * staircase, and patches far apart have different levels. The steepest faces get none (their
     * ledges would crowd together and their risers would be sheer walls). The caller cuts them
     * into the slope before its gullies, which then break them up: a ledge shows as a shoulder on a
     * spur and as a step in the floor of a gully.
     *
     * @param h      height of the ground being shaped, in blocks
     * @param slope  its gradient, smoothed over a few dozen blocks, in blocks per block
     * @param weight 0..1 where ledges may form at all
     * @return the change of height in blocks (zero or negative)
     */
    double ledges(double x, double z, double h, double slope, double weight) {
        // gentle ground needs no ledges (they would be broad terraces there)
        double w = weight * smoothstep(0.45, 0.85, slope) * (1.0 - smoothstep(1.8, 2.6, slope));
        if (w <= 0.0) {
            return 0.0;
        }
        double patch = smoothstep(0.15, 0.55, ledgeNoise.noise2(x / LEDGE_PATCH, z / LEDGE_PATCH));
        w *= patch;
        if (w <= 0.0) {
            return 0.0;
        }
        // the levels follow nothing that changes quickly across the ground: a bench is level
        // along a level of the warped height, and tilts as the shift and warp change. (With a step
        // that changed over a few hundred blocks, the levels of ground 600 blocks high, some twenty
        // steps up, swept by over a block per block: the ledges crowded into stripes every 15
        // blocks, and their benches rose towards their edges.)
        double shift = LEDGE_SHIFT * ledgeNoise.noise2(x / 1500.0 + 37.1, z / 1500.0 - 11.3);
        double hs = h + shift;
        // the warp: its slope swings between 0.4 and 1.6, so the ledges are spaced unevenly
        double phase = hs * (TAU / (2.3 * LEDGE_STEP));
        double t = (hs + 0.6 * LEDGE_STEP * 2.3 / TAU * Math.sin(phase)) / LEDGE_STEP;
        double rate = 1.0 + 0.6 * Math.cos(phase);
        double f = t - Math.floor(t);
        // share of a step the bench takes: narrow ledges at the patch edges, wider inside. The
        // bench keeps a fifth of the slope, the riser above it takes the rest.
        double k = 0.1 + 0.28 * patch;
        double g = f < k ? 0.2 * f : 0.2 * k + (f - k) * (1.0 - 0.2 * k) / (1.0 - k);
        return w * LEDGE_STEP * (g - f) / rate;
    }
}
