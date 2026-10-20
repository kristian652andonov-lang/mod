package com.terrahaul.worldgen.water;

import static com.terrahaul.worldgen.noise.TMath.clamp;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.List;

/**
 * The way a waterfall's water comes down a slope, fitted to the ground it finds there instead of
 * carved into it, and how it shapes the columns on its way (see {@link FallPlan}).
 * <p>
 * The course is described in a frame anchored at the lip: {@code u} runs down the fall (one of
 * sixteen compass directions, so falls follow the real fall line of the slope) and {@code v}
 * across it. From upstream to downstream:
 * <ol>
 *   <li>a <b>feeding channel</b> from a stream of the land's network or a small spring pool,
 *       winding a little and narrowing upstream, stepping down with the ground behind the lip in
 *       small rapids the way a mountain stream comes down to an edge;</li>
 *   <li>the <b>face</b>: a staircase of treads (ledges holding a block or two of water) and risers
 *       (falling water) laid along the natural profile. Each tread's water lies a little below the
 *       lowest ground under and beside it, so the water runs in a shallow rocky cleft, the ledge
 *       is cut only a few blocks into the slope at its back, and its front may be built up a block
 *       (a protruding rock lip the water shoots off). Steep faces give tall risers and short
 *       treads, gentler ones long ledges; the boundaries between treads are jagged across the
 *       fall, so the lip and every step break unevenly;</li>
 *   <li>the <b>strands</b>: the water spans a band around a centre line that swings from side to
 *       side down the face, with a width that changes down the drop and ragged edges; both change
 *       smoothly, so water on a ledge always has water or rock below its edge, never a drop to
 *       hold back. Rock outcrops inside the band split it into strands that rejoin below them;</li>
 *   <li>a modest, irregular <b>plunge pool</b> where the slope flattens, its basin dug a few blocks
 *       into the ground, with boulders on its rim and in its shallows (or the sea or a lake, which
 *       a fall from a coastal cliff drops straight into);</li>
 *   <li>an <b>outflow</b> stream traced down the steepest descent from the pool's lowest rim to the
 *       river, lake, sea or stream it joins, stepping down in small rapids.</li>
 * </ol>
 * The water's centre line follows the low line of the face, the way water finds the gully of a
 * slope, with each style's swings laid over it. Beside the water the ground is shaped as erosion
 * would: walls rise from the water at a steep, noisy slope (a V-shaped cleft around the face, a
 * small gorge along the outflow) and only where they are lower than the natural ground is anything
 * cut, so the cut is a narrow notch that fades into the slope, never a bowl. Where the ground beside
 * the water is lower than the water, a lip of rock up to {@value #MAX_FILL} blocks high holds it, and
 * crags of broken rock stand out of the slope beside the falling water and along the outflow. Only
 * where the water lands may the pool be dug a little deeper into high ground at the foot of the
 * face, so the fall always reaches it. The planner caps the volume cut.
 * <p>
 * Styles: a {@link #PLUNGE} on steep faces (tall risers, a ragged column changing width down the
 * drop), {@link #TIERED} falls (long ledges, the water swinging from side to side), a
 * {@link #HORSETAIL} sliding down a staircase of short steps and widening as it goes, and a
 * {@link #FAN} of several strands of different widths over a long cliff edge, split by outcrops.
 * <p>
 * Everything is a pure function of the plan, so every chunk builds the same columns.
 */
final class FallCourse {
    static final int PLUNGE = 0;
    static final int TIERED = 1;
    static final int HORSETAIL = 2;
    static final int FAN = 3;

    /** Farthest from the water that walls are cut, in blocks. */
    static final int WALL_REACH = 10;
    /** Deepest a tread or the channel may be cut below the natural ground at its back. */
    static final int MAX_TREAD_CUT = 9;
    /** Deepest a wall beside the water may be cut. */
    static final int MAX_WALL_CUT = 8;
    /** Highest a lip of rock beside the water may be built up above the natural ground. */
    static final int MAX_FILL = 3;
    /** Highest natural ground the plunge pool's water may be dug into, above its surface. */
    static final int POOL_SHORE = 5;
    /** How much higher the ground may stand where the falling water lands: it scours deeper there. */
    static final int LANDING_SHORE = 9;
    /** Deepest the sides of the plunge pool may be cut into the ground around it. */
    static final int POOL_WALL_CUT = 4;
    /** Deepest the banks of the outflow may be cut. */
    static final int OUT_WALL_CUT = 5;
    /** Farthest from the water that crags stand out of the slope. */
    static final double CRAG_REACH = 6.0;

    private final TerrainModel model;
    final long seed;
    final int lipX;
    final int lipZ;
    /** Unit vector pointing downstream. */
    final double ax;
    final double az;
    int style;
    /** The fall drops into the sea or a lake rather than a plunge pool. */
    boolean intoWater;
    /** How far the water lies below the lowest ground beside it: the depth of the cleft. */
    int incise = 1;

    // ---- feeding channel (u below the first tread) ----------------------------------------
    int channel;
    double headR;
    double channelHalf;
    int channelDepth = 2;
    /**
     * Exclusive top of the channel's water {@code j} blocks upstream of the lip: the channel
     * steps up with the ground behind the lip instead of running level in a trench.
     */
    int[] channelLevel = new int[1];

    // ---- face: tread 0 is the channel, tread `treads` the pool ------------------------------
    int treads;
    final double[] treadStart = new double[64];
    final double[] treadJitter = new double[64];
    final int[] treadLevel = new int[64];
    final int[] treadDepth = new int[64];
    /** Start of the pool along the axis (before jitter). */
    double faceEnd;
    /** While the face is laid: where the next tread starts along the axis, and the last tread's level. */
    private int faceA;
    private int facePrev;
    double faceJitter;

    // ---- the band of water on the face: centre and half-width, linear between knots --------
    int envN;
    final double[] envU = new double[32];
    final double[] envC = new double[32];
    final double[] envW = new double[32];

    // ---- outcrops splitting the strands: ellipses in the frame, across from the centre line --
    int rocks;
    final double[] rockU = new double[12];
    /** Offset across the fall from the water's centre line, so a rock keeps its place in the water. */
    final double[] rockV = new double[12];
    final double[] rockA = new double[12];
    final double[] rockB = new double[12];
    /** A long rib between strands: about as wide along most of its length, not lens-shaped. */
    final boolean[] rockRib = new boolean[12];

    // ---- plunge pool ---------------------------------------------------------------------------
    double poolU;
    double poolV;
    double poolA;
    double poolB;
    int poolDepth;
    /** Exclusive top of the pool's water. */
    int poolLevel;
    int boulders;
    final double[] boulderX = new double[8];
    final double[] boulderZ = new double[8];
    final double[] boulderR = new double[8];
    final int[] boulderTop = new int[8];

    // ---- outflow -------------------------------------------------------------------------------
    int outN;
    double[] outX = new double[0];
    double[] outZ = new double[0];
    int[] outLevel = new int[0];
    double outHalf;
    /** The outflow ends in other water (a river, lake, the sea or a stream). */
    boolean outJoins;
    private int outMinX;
    private int outMinZ;
    private int outMaxX;
    private int outMaxZ;

    // ---- walls ---------------------------------------------------------------------------------
    double wallSlope;
    double outWallSlope;

    int minX;
    int minZ;
    int maxX;
    int maxZ;

    FallCourse(TerrainModel model, long seed, int lipX, int lipZ, double ax, double az) {
        this.model = model;
        this.seed = seed;
        this.lipX = lipX;
        this.lipZ = lipZ;
        this.ax = ax;
        this.az = az;
    }

    // =========================================================================================
    // frame
    // =========================================================================================

    double u(double x, double z) {
        return (x - lipX) * ax + (z - lipZ) * az;
    }

    double v(double x, double z) {
        return (z - lipZ) * ax - (x - lipX) * az;
    }

    double worldX(double u, double v) {
        return lipX + u * ax - v * az;
    }

    double worldZ(double u, double v) {
        return lipZ + u * az + v * ax;
    }

    /** Top water block of the channel at the lip. */
    int top() {
        return channelLevel[0] - 1;
    }

    /** Top water block of the pool (or of the sea or lake the fall drops into). */
    int pool() {
        return poolLevel - 1;
    }

    // =========================================================================================
    // the course
    // =========================================================================================

    /** Smooth noise in about [-1, 1], decorrelated by {@code salt}. */
    private double noise(int channel, double a, double b, double salt) {
        return model.detail3(channel, a, salt + (seed & 1023) * 0.37, b);
    }

    /** Where tread {@code i} (1 .. treads, the last being the pool) begins at {@code v}. */
    private double boundary(int i, double v) {
        // the noise is clamped so a boundary never strays further than its jitter (see treadAt)
        if (i >= treads) {
            return faceEnd + faceJitter * clamp(noise(3, v * 0.33, 9.0, 41.0), -1.0, 1.0);
        }
        return treadStart[i] + treadJitter[i] * clamp(noise(3, v * 0.33, i * 5.3, 17.0), -1.0, 1.0);
    }

    /** Index of the tread holding {@code (u, v)}: 0 is the channel, {@code treads} the pool. */
    int treadAt(double u, double v) {
        // a boundary strays from its nominal place by less than half the treads on either side,
        // so the ranges of neighbouring boundaries never overlap: only the last boundary whose
        // range starts at or before u can lie beyond it, and only that one needs its noise
        int i = treads;
        while (i >= 1 && u < (i >= treads ? faceEnd - faceJitter : treadStart[i] - treadJitter[i])) {
            i--;
        }
        if (i == 0) {
            return 0;
        }
        return u >= boundary(i, v) ? i : i - 1;
    }

    /** Exclusive top of the water of the channel ({@code i = 0}), a tread or the pool at {@code u}. */
    int level(int i, double u) {
        if (i > 0) {
            return i >= treads ? poolLevel : treadLevel[i];
        }
        int j = (int) Math.round(-u);
        return channelLevel[Math.max(0, Math.min(channel, j))];
    }

    /** Centre line of the water at {@code u}: the channel winds, the band on the face swings. */
    double centreAt(double u) {
        if (u < 0.0) {
            // the channel winds a little, more the further it is from the lip
            return 1.4 * noise(2, u * 0.16, 3.0, 29.0) * smoothstep(0.0, 6.0, -u);
        }
        return envelope(envC, u);
    }

    /** Half-width of the water at {@code u}, before its ragged edge. */
    double halfAt(double u) {
        if (u < 0.0) {
            if (style == FAN) {
                // a stream spreading over the ledge behind a long edge in its last few blocks
                return Math.max(1.2, channelHalf * (1.0 - 0.7 * smoothstep(0.0, 5.0, -u)));
            }
            return Math.max(0.5, channelHalf * (1.0 - 0.45 * clamp(-u / Math.max(1, channel), 0.0, 1.0)));
        }
        return envelope(envW, u);
    }

    private double envelope(double[] values, double u) {
        if (u <= envU[0]) {
            return values[0];
        }
        for (int k = 1; k < envN; k++) {
            if (u <= envU[k]) {
                double t = (u - envU[k - 1]) / (envU[k] - envU[k - 1]);
                return values[k - 1] + (values[k] - values[k - 1]) * t;
            }
        }
        return values[envN - 1];
    }

    /** Whether a point past the face lies where the water from the last tread lands. */
    private boolean landing(double u, double v, double edge) {
        return u < edge + 2.5 && Math.abs(v - centreAt(faceEnd)) <= halfAt(faceEnd) + 0.5;
    }

    /** Distance of a point outside the water's ragged edge (negative inside). */
    private double outside(double u, double v) {
        double w = halfAt(u);
        // the edge frays more on wide water: spurs of water spill out, tongues of rock reach in
        double e = noise(4, u * 0.45, v * 0.45, 53.0);
        return Math.abs(v - centreAt(u)) - (w + (e > 0 ? (0.8 + 0.25 * w) * e : 0.5 * Math.min(2.5, w) * e));
    }

    /** How deep inside a rock outcrop a point lies (0 at its edge, negative outside). */
    private double rockInside(double u, double v) {
        if (rocks == 0) {
            return -1.0;
        }
        double best = -1.0;
        double c = centreAt(u);
        for (int r = 0; r < rocks; r++) {
            double a = (u - rockU[r]) / rockA[r];
            double aa = rockRib[r] ? a * a * a * a : a * a;
            if (aa > 2.5) {
                continue;
            }
            double b;
            if (rockRib[r]) {
                // a rib wanders a little and swells and thins along its length, so the strands
                // beside it are never parallel bands of even width
                double wob = 1.1 * noise(2, u * 0.13, r * 3.1, 151.0);
                double wid = 0.7 + 0.5 * noise(3, u * 0.21, r * 2.7, 157.0);
                b = (v - c - rockV[r] - wob) / (rockB[r] * wid);
            } else {
                b = (v - c - rockV[r]) / rockB[r];
            }
            if (aa + b * b > 2.5) {
                continue;
            }
            double d = 1.0 - Math.sqrt(aa + b * b) + 0.15 * noise(5, u * 0.6, v * 0.6, 61.0 + r);
            best = Math.max(best, d);
        }
        return best;
    }

    /** Whether rocks may stand in the water at {@code (u, tread i)}: on the face, and on a fan's lip. */
    private boolean rocky(int i, double u) {
        return i > 0 || style == FAN && u > -2.5;
    }

    /** Normalised radius of a point in the plunge pool, irregular around its rim. */
    double poolRadius(double u, double v) {
        double a = (u - poolU) / poolA;
        double b = (v - poolV) / poolB;
        double r = Math.sqrt(a * a + b * b);
        if (r > 3.0) {
            return r;
        }
        double ang = Math.atan2(b, a);
        // a few lobes and bays around the rim, and a ragged edge
        double lobes = 0.26 * noise(6, Math.cos(ang) * 1.3, Math.sin(ang) * 1.3, 71.0) + 0.1 * noise(6, Math.cos(ang) * 3.1, Math.sin(ang) * 3.1, 73.0);
        return r * (1.0 - lobes) + 0.1 * noise(5, u * 0.5, v * 0.5, 79.0);
    }

    // =========================================================================================
    // shape
    // =========================================================================================

    /**
     * Shapes a column (see {@link WaterPlan#shape}). Water first: spring, channel, face, pool and
     * outflow, each in its own stretch of the course; then the walls beside it, cut only where they
     * are lower than the natural ground, and lips of rock where the ground is lower than the water.
     */
    boolean shape(int x, int z, int natural, WaterPlan.Shape out) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }
        double u = u(x, z);
        double v = v(x, z);
        // ---- the spring pool at the head of the channel --------------------------------------
        if (headR > 0.0 && u < -channel + 1.0) {
            double hu = (u + channel + headR - 0.5) / headR;
            double hv = (v - centreAt(-channel)) / (headR * 0.85);
            double hr = Math.sqrt(hu * hu + hv * hv) + 0.12 * noise(6, x / 4.0, z / 4.0, 17.0);
            if (hr <= 1.0) {
                int head = channelLevel[channel];
                out.ground = head - 2 - (int) Math.round(1.5 * (1.0 - hr * hr));
                out.level = head;
                out.role = WaterSolver.ROLE_BED;
                return true;
            }
            if (hr <= 1.0 + 2.5 / headR && natural < channelLevel[channel] - 1) {
                // its rim, where the ground around it dips
                out.ground = Math.min(channelLevel[channel] - 1, natural + MAX_FILL);
                out.role = WaterSolver.ROLE_NATURAL;
                return true;
            }
        }
        double wall = Double.MAX_VALUE;
        double lipNeed = Double.MAX_VALUE;
        // how far the column lies from the falling water or the outflow, and how tall the crags
        // standing there may be
        double waterDist = Double.MAX_VALUE;
        double cragMax = 0.0;
        // ---- channel and face ------------------------------------------------------------------
        if (u >= -channel - 0.5 && u < faceEnd + 2.0) {
            int i = treadAt(u, v);
            if (i < treads) {
                double dv = outside(u, v);
                double rock = rocky(i, u) ? rockInside(u, v) : -1.0;
                if (dv <= 0.0 && rock < 0.0) {
                    int level = level(i, u);
                    int bed = level - 1 - (i == 0 ? channelDepth : treadDepth[i]);
                    if (natural - bed <= MAX_TREAD_CUT + 2) {
                        out.level = level;
                        out.ground = bed;
                        out.role = i == 0 ? WaterSolver.ROLE_BED : WaterSolver.ROLE_RIB;
                        return true;
                    }
                    // a knob of high ground inside the band: the water passes beside it
                    rock = 0.0;
                }
                // the rim stands at least a block over the highest water a block upstream, so a
                // riser's sides never sit lower than the ledge above them
                int up = level(treadAt(u - 1.0, v), u - 1.0);
                double rise = Math.max(0.0, dv);
                if (u > -2.0 && rock < 0.0) {
                    waterDist = rise;
                    cragMax = MAX_FILL;
                }
                if (rock >= 0.0) {
                    // an outcrop: rock standing a block or more out of the water around it
                    wall = Math.min(wall, up + 1.0 + 2.5 * rock + jag(x, z, 1.0));
                    lipNeed = up + 1.0;
                } else {
                    wall = Math.min(wall, up + wallSlope * rise + jag(x, z, rise));
                    if (rise < 1.5) {
                        lipNeed = up;
                    }
                }
            }
        }
        // ---- plunge pool -----------------------------------------------------------------------
        double pr = poolRadius(u, v);
        if (!intoWater) {
            double edge = boundary(treads, v);
            boolean past = u >= edge;
            // the basin keeps to the low ground: where the foot of the face or a slope beside it
            // stands high, the rock stays and the pool's outline bends around it, except right
            // where the water lands, which it scours deeper
            if (pr <= 1.0 && past && natural <= poolLevel + (landing(u, v, edge) ? LANDING_SHORE : POOL_SHORE)) {
                for (int b = 0; b < boulders; b++) {
                    if (Math.hypot(x + 0.5 - boulderX[b], z + 0.5 - boulderZ[b]) <= boulderR[b]) {
                        // a boulder standing in the shallows
                        out.ground = boulderTop[b];
                        out.role = WaterSolver.ROLE_RIB;
                        return true;
                    }
                }
                out.level = poolLevel;
                int basin = poolLevel - 2 - (int) Math.round((poolDepth - 1) * Math.sqrt(Math.max(0.0, 1.0 - pr * pr)));
                // shallower where the ground stands high, at the foot of the face
                out.ground = Math.max(basin, Math.min(poolLevel - 2, natural - MAX_TREAD_CUT - 1));
                out.role = WaterSolver.ROLE_BED;
                return true;
            }
            double dr = (pr - 1.0) * Math.min(poolA, poolB);
            if (dr < WALL_REACH && u >= faceEnd - 0.5) {
                for (int b = 0; b < boulders; b++) {
                    if (Math.hypot(x + 0.5 - boulderX[b], z + 0.5 - boulderZ[b]) <= boulderR[b]) {
                        // a boulder on the shore, standing on the natural ground
                        int g = Math.max(natural + 1, boulderTop[b]);
                        if (g - natural <= MAX_FILL) {
                            out.ground = g;
                            out.role = WaterSolver.ROLE_RIB;
                            return true;
                        }
                    }
                }
                // the basin's sides rise from the water, but cut no more than a few blocks into a
                // slope: where the ground stands higher they end in a short bank of rock
                wall = Math.min(wall, Math.max(poolLevel + 1.3 * Math.max(0.0, dr) + jag(x, z, dr), natural - POOL_WALL_CUT));
                if (dr < 1.5) {
                    lipNeed = Math.min(lipNeed, poolLevel);
                }
            }
        } else if (u >= boundary(treads, v) && u <= poolU + poolA) {
            // the notch through the beach or talus down to the sea or lake
            double dv = Math.abs(v - poolV) - poolB;
            if (dv <= 0.0) {
                if (natural <= poolLevel - 2 && u > faceEnd + 1.5) {
                    return false;
                }
                out.level = poolLevel;
                out.ground = Math.min(natural, poolLevel - 2);
                out.role = WaterSolver.ROLE_BED;
                return true;
            }
            wall = Math.min(wall, poolLevel + wallSlope * dv + jag(x, z, dv));
        }
        // ---- outflow -------------------------------------------------------------------------------
        if (outN >= 2 && x >= outMinX && x <= outMaxX && z >= outMinZ && z <= outMaxZ) {
            double[] near = outflowNear(x, z);
            double best = near[0];
            int bestLevel = (int) near[1];
            double half = outHalf + 0.4 * noise(4, x * 0.3, z * 0.3, 83.0);
            if (best <= half && near[2] > 0.0 && pr > 1.0) {
                out.level = bestLevel;
                out.ground = bestLevel - 2 - (best < half * 0.5 ? 1 : 0);
                out.role = WaterSolver.ROLE_BED;
                out.style = WaterSolver.STYLE_STREAM;
                return true;
            }
            // the stream's banks: a small gorge where the ground stands higher, cut a few blocks at most
            double dv = best - half;
            if (dv < waterDist) {
                waterDist = dv;
                cragMax = 2.0;
            }
            if (dv < WALL_REACH) {
                wall = Math.min(wall, Math.max(bestLevel + outWallSlope * Math.max(0.0, dv) + jag(x, z, dv), natural - OUT_WALL_CUT));
            }
        }
        // ---- walls: cut only where lower than the natural ground ---------------------------
        // and never deeper than a few blocks: where the ground beside rises steeply, the cleft's
        // wall becomes a short cliff instead of cutting further back
        wall = Math.max(wall, natural - MAX_WALL_CUT);
        if (wall < natural - 0.5) {
            // and never lower than the water beside it, whichever part of the course that is
            wall = Math.max(wall, waterBeside(x, z));
        }
        if (wall < natural - 0.5) {
            int g = (int) Math.floor(wall);
            out.ground = g;
            out.role = natural - g <= 2 ? WaterSolver.ROLE_NATURAL : WaterSolver.ROLE_ROCK;
            return true;
        }
        // ---- a lip of rock where the ground beside the water is lower than the water ---------
        if (lipNeed != Double.MAX_VALUE && natural < lipNeed - 1) {
            out.ground = (int) Math.min(lipNeed, natural + MAX_FILL);
            out.role = WaterSolver.ROLE_RIB;
            return true;
        }
        // ---- crags: broken rock standing out of the slope beside the fall and its stream -------
        if (waterDist > 1.0 && waterDist < CRAG_REACH) {
            int c = crag(x, z, waterDist, cragMax);
            if (c > 0) {
                out.ground = natural + c;
                out.role = WaterSolver.ROLE_ROCK;
                return true;
            }
        }
        return false;
    }

    /**
     * Height of the crag standing on a column {@code dist} blocks from the water (0 for none):
     * clumps of rock a few blocks across, one to {@code max} blocks out of the natural slope,
     * tallest close to the water where floods and spray strip the soil, thinning out with
     * distance, with a blocky part so their tops break unevenly.
     */
    private int crag(int x, int z, double dist, double max) {
        double amp = 1.0 - smoothstep(2.0, CRAG_REACH, dist);
        double n = 0.75 * model.detail3(5, x / 3.2, 131.0, z / 3.2) + 0.45 * model.detail3(7, x / 7.0, 137.0, z / 7.0)
            + 0.35 * (SeedMixer.unit(SeedMixer.hash2(seed ^ 0xC4A6L, x, z)) - 0.5);
        double h = (n - 0.15) * 4.0 * amp;
        return h < 1.0 ? 0 : (int) Math.min(max, Math.floor(h));
    }

    /**
     * The point of the outflow nearest to a column: {@code {distance, level there, 1 if the
     * column lies alongside the stream rather than beyond its end}}.
     */
    private double[] outflowNear(int x, int z) {
        double best = Double.MAX_VALUE;
        int bestLevel = 0;
        boolean inside = false;
        for (int k = 0; k + 1 < outN; k++) {
            double sx = outX[k + 1] - outX[k];
            double sz = outZ[k + 1] - outZ[k];
            double len2 = sx * sx + sz * sz;
            double t = ((x + 0.5 - outX[k]) * sx + (z + 0.5 - outZ[k]) * sz) / len2;
            boolean beyond = t > 1.0 && k + 2 == outN;
            t = clamp(t, 0.0, 1.0);
            double px = outX[k] + sx * t - x - 0.5;
            double pz = outZ[k] + sz * t - z - 0.5;
            double d = Math.sqrt(px * px + pz * pz);
            if (d < best) {
                best = d;
                bestLevel = t < 0.5 ? outLevel[k] : outLevel[k + 1];
                inside = !beyond;
            }
        }
        return new double[]{best, bestLevel, inside ? 1.0 : 0.0};
    }

    /**
     * Exclusive top of the water the course puts in a column before the natural ground has its say
     * (high ground may still keep it dry), or {@link WaterSolver#NONE}: the same decisions, in the
     * same order, as {@link #shape}.
     */
    private int nominalLevel(int x, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return WaterSolver.NONE;
        }
        double u = u(x, z);
        double v = v(x, z);
        if (headR > 0.0 && u < -channel + 1.0) {
            double hu = (u + channel + headR - 0.5) / headR;
            double hv = (v - centreAt(-channel)) / (headR * 0.85);
            if (Math.sqrt(hu * hu + hv * hv) + 0.12 * noise(6, x / 4.0, z / 4.0, 17.0) <= 1.0) {
                return channelLevel[channel];
            }
        }
        if (u >= -channel - 0.5 && u < faceEnd + 2.0) {
            int i = treadAt(u, v);
            if (i < treads && outside(u, v) <= 0.0 && (!rocky(i, u) || rockInside(u, v) < 0.0)) {
                return level(i, u);
            }
        }
        double pr = poolRadius(u, v);
        if (!intoWater) {
            if (pr <= 1.0 && u >= boundary(treads, v)) {
                return poolLevel;
            }
        } else if (u >= boundary(treads, v) && u <= poolU + poolA && Math.abs(v - poolV) <= poolB) {
            return poolLevel;
        }
        if (outN >= 2 && x >= outMinX && x <= outMaxX && z >= outMinZ && z <= outMaxZ && pr > 1.0) {
            double[] near = outflowNear(x, z);
            if (near[0] <= outHalf + 0.4 * noise(4, x * 0.3, z * 0.3, 83.0) && near[2] > 0.0) {
                return (int) near[1];
            }
        }
        return WaterSolver.NONE;
    }

    /** Top water block of the highest water beside a column, or a very low number if none. */
    private double waterBeside(int x, int z) {
        int need = Math.max(Math.max(nominalLevel(x + 1, z), nominalLevel(x - 1, z)), Math.max(nominalLevel(x, z + 1), nominalLevel(x, z - 1)));
        return need == WaterSolver.NONE ? -Double.MAX_VALUE : need - 1;
    }

    /**
     * Craggy variation of a wall: none right at the water, where the wall must hold it, growing to
     * a couple of blocks up and down within two blocks of it, with a blocky per-column part so the
     * rock reads as broken rather than smooth.
     */
    private double jag(int x, int z, double dist) {
        double amp = smoothstep(0.0, 2.0, dist);
        if (amp <= 0.0) {
            return 0.0;
        }
        double n = 1.8 * model.detail3(5, x / 3.3, 97.0, z / 3.3) + 1.0 * model.detail3(7, x / 8.0, 41.0, z / 8.0);
        double h = SeedMixer.unit(SeedMixer.hash2(seed ^ 0x1A6L, x, z)) - 0.5;
        return amp * (n + 1.6 * h);
    }

    /**
     * Distance from a column to the falling or tumbling water of the face or to the pool (0 in
     * it), for decoration: wet rock, moss, lichen and ferns grow where the spray reaches.
     */
    double spray(int x, int z) {
        double u = u(x, z);
        double v = v(x, z);
        double best = Double.MAX_VALUE;
        if (u > -2.0 && u < faceEnd + 3.0) {
            double uu = clamp(u, 0.0, faceEnd);
            double dv = Math.max(0.0, Math.abs(v - centreAt(uu)) - halfAt(uu));
            best = Math.hypot(u - uu, dv);
        }
        if (!intoWater) {
            best = Math.min(best, Math.max(0.0, poolRadius(u, v) - 1.0) * Math.min(poolA, poolB));
        }
        return best;
    }

    /** Whether a column lies on the face, between the lip and the pool. */
    boolean onFace(int x, int z) {
        double u = u(x, z);
        return u > 0.0 && u < faceEnd + 1.0;
    }

    /** Half-width of the widest water on the face. */
    double maxHalfWidth() {
        double w = channelHalf;
        for (int k = 0; k < envN; k++) {
            w = Math.max(w, envW[k]);
        }
        return w;
    }

    // =========================================================================================
    // fitting
    // =========================================================================================

    /** A small deterministic random source for fitting. */
    static final class Dice {
        private long state;

        Dice(long seed) {
            state = seed;
        }

        double next() {
            state = SeedMixer.next(state);
            return SeedMixer.unit(state);
        }
    }

    /**
     * Lowest natural ground of the columns whose centres lie between {@code uLo} and {@code uHi}
     * along the fall and within a block of the water across it, and highest ground under the
     * water itself, into {@code out}.
     */
    private void groundRange(PlanSampler ps, double uLo, double uHi, int[] out) {
        double c = centreAt((uLo + uHi) * 0.5);
        double w = halfAt((uLo + uHi) * 0.5);
        double reach = Math.abs(c) + w + 2.0;
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        int x0 = (int) Math.floor(Math.min(worldX(uLo, -reach), Math.min(worldX(uLo, reach), Math.min(worldX(uHi, -reach), worldX(uHi, reach)))));
        int x1 = (int) Math.ceil(Math.max(worldX(uLo, -reach), Math.max(worldX(uLo, reach), Math.max(worldX(uHi, -reach), worldX(uHi, reach)))));
        int z0 = (int) Math.floor(Math.min(worldZ(uLo, -reach), Math.min(worldZ(uLo, reach), Math.min(worldZ(uHi, -reach), worldZ(uHi, reach)))));
        int z1 = (int) Math.ceil(Math.max(worldZ(uLo, -reach), Math.max(worldZ(uLo, reach), Math.max(worldZ(uHi, -reach), worldZ(uHi, reach)))));
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double u = u(x, z);
                if (u < uLo || u >= uHi) {
                    continue;
                }
                double dv = Math.abs(v(x, z) - centreAt(u)) - halfAt(u);
                if (dv > 1.0) {
                    continue;
                }
                int g = ps.ground(x, z);
                lo = Math.min(lo, g);
                if (dv <= 0.0) {
                    hi = Math.max(hi, g);
                }
            }
        }
        if (hi == Integer.MIN_VALUE) {
            int g = ground(ps, (uLo + uHi) * 0.5, c);
            hi = g;
            lo = Math.min(lo, g);
        }
        out[0] = lo;
        out[1] = hi;
    }

    /** Natural ground (top solid block) at frame position {@code (u, v)}. */
    private int ground(PlanSampler ps, double u, double v) {
        return ps.ground((int) Math.round(worldX(u, v)), (int) Math.round(worldZ(u, v)));
    }

    /**
     * The band of water down the face, by style: a plunge's ragged column changing width down the
     * drop, a tiered fall swinging from side to side, a horsetail widening as it slides, a fan
     * keeping its width. It starts at least as wide as the channel feeding it. Its centre follows
     * the low line of the face, as water finds the gully of a slope, and the style's own swings
     * and drift are laid over that line.
     */
    private void envelope(PlanSampler ps, Dice dice, double halfW, int foot) {
        envN = 0;
        double low = 0.0;
        double drift = 0.0;
        double side = dice.next() < 0.5 ? -1.0 : 1.0;
        double bend = clamp(0.025 * foot, 0.5, 1.5);
        double u = 0.0;
        double prevU = 0.0;
        while (envN < envU.length) {
            double f = clamp(u / Math.max(1, foot), 0.0, 1.0);
            double w;
            double c;
            if (envN > 0) {
                low = lowLine(ps, u, low, (u - prevU) * (style == FAN ? 0.3 : 0.7));
            }
            switch (style) {
                case TIERED -> {
                    w = halfW * (0.75 + 0.45 * dice.next());
                    // the stream swings across the ledges, more often to the other side
                    side = dice.next() < 0.75 ? -side : side;
                    c = low + clamp(side * (0.4 + 1.1 * dice.next()) * Math.max(1.0, 0.7 * halfW), -2.5, 2.5);
                }
                case HORSETAIL -> {
                    // widening as it slides, but unevenly: pinched where it crosses a rib of rock,
                    // spread where the face is smooth
                    w = halfW * (0.8 + 1.3 * Math.sqrt(f)) * (0.7 + 0.6 * dice.next());
                    drift = clamp(drift + (dice.next() - 0.5) * 1.4, -2.0, 2.0);
                    c = low + drift;
                }
                case FAN -> {
                    w = halfW * (0.92 + 0.16 * dice.next());
                    drift = clamp(drift + (dice.next() - 0.5) * 0.5, -1.0, 1.0);
                    c = low + drift;
                }
                default -> {
                    // leaving its notch narrow, the column spreads within the first blocks of the
                    // drop and then swells and thins on the way down
                    double spread = smoothstep(0.0, Math.max(4.0, 0.25 * foot), u);
                    w = channelHalf + (halfW * (0.8 + 0.45 * dice.next() + 0.2 * f) - channelHalf) * spread;
                    drift = clamp(drift + (dice.next() - 0.5) * 1.0, -1.5, 1.5);
                    c = low + drift;
                }
            }
            if (envN == 0) {
                c = 0.0;
                w = Math.max(w, channelHalf);
            }
            envU[envN] = u;
            // and the whole fall bends a little on its way down
            envC[envN] = c + bend * noise(2, u * 0.06, 7.0, 37.0) * smoothstep(0.0, 8.0, u);
            envW[envN] = Math.max(0.5, w);
            envN++;
            if (u > foot + 12) {
                break;
            }
            prevU = u;
            u += style == TIERED ? 3.0 + 2.0 * dice.next() : 3.0 + 1.5 * dice.next();
        }
    }

    /**
     * Where the face is lowest across the fall at {@code u}, near the low line {@code from} found a
     * knot upstream and at most {@code reach} blocks from it, so the water follows the slope's own
     * gully without jumping across ribs; a little straightness is preferred on even ground.
     */
    private double lowLine(PlanSampler ps, double u, double from, double reach) {
        double best = from;
        double bestH = Double.MAX_VALUE;
        int steps = (int) Math.ceil(Math.max(1.0, reach));
        for (int k = -steps; k <= steps; k++) {
            double v = clamp(from + reach * k / steps, -8.0, 8.0);
            // two samples along the fall, so a single bump does not pull the line aside
            double h = 0.5 * (ground(ps, u, v) + ground(ps, u + 1.5, v)) + 0.2 * Math.abs(v - from);
            if (h < bestH) {
                bestH = h;
                best = v;
            }
        }
        return best;
    }

    /**
     * Lays the band of water, the face's treads from the lip to the foot of the slope, the pool
     * and the outflow. {@code top} is the channel's top water block at the lip, {@code foot} the
     * distance along the axis where the slope flattens out (or meets the sea or a lake,
     * {@code waterLevel} then being its exclusive top). Returns {@code false} if the slope cannot
     * carry the fall without cutting or building more than a few blocks anywhere.
     */
    boolean fit(PlanSampler ps, int top, int foot, int waterLevel, double halfW, Dice dice) {
        int sl = model.seaLevel();
        intoWater = waterLevel != WaterSolver.NONE;
        envelope(ps, dice, halfW, foot);
        treads = 1;
        // the pool's surface: the lowest natural ground around the foot, or the water there
        int poolTop;
        if (intoWater) {
            poolTop = waterLevel - 1;
        } else {
            poolTop = Integer.MAX_VALUE;
            for (int du = 0; du <= 4; du += 2) {
                for (int dv = -2; dv <= 2; dv += 2) {
                    poolTop = Math.min(poolTop, ground(ps, foot + du, centreAt(foot) + dv));
                }
            }
        }
        if (top - poolTop < 8) {
            return false;
        }
        faceA = 1;
        facePrev = top + 1;
        if (!layTreads(ps, dice, poolTop, foot + 10) || treads < 2 || facePrev - 1 - poolTop < 2) {
            return false;
        }
        finishFace();
        if (intoWater) {
            poolU = faceEnd;
            poolV = centreAt(faceEnd);
            poolA = foot - faceEnd + 3.0;
            poolB = halfAt(faceEnd) + 1.0;
            poolLevel = poolTop + 1;
            poolDepth = 2;
            treadLevel[treads] = poolLevel;
            return true;
        }
        double outAng = 0.0;
        for (int attempt = 0; ; attempt++) {
            // the plunge pool: modest, a little wider than the water landing in it
            double w = halfAt(faceEnd);
            double drop = top - poolTop;
            poolB = clamp(w + 1.5 + 2.0 * dice.next(), 3.0, style == FAN ? w + 3.0 : 7.0);
            // roundish rather than a bar across the foot of the face
            poolA = clamp(Math.max(0.65 * poolB, 2.2 + 0.06 * drop + 0.3 * w + 1.5 * dice.next()), 2.5, 7.0);
            poolU = faceEnd + 0.75 * poolA;
            poolV = centreAt(faceEnd);
            poolDepth = (int) clamp(Math.round(2 + drop / 20.0), 2, 4);
            // the surface sits at the lowest natural ground around the rim (its outlet), but always
            // two blocks or more below the last ledge
            int rimMin = Integer.MAX_VALUE;
            for (int k = 0; k < 16; k++) {
                double ang = k * Math.PI / 8.0;
                if (Math.cos(ang) < -0.3) {
                    continue;
                }
                int g = ground(ps, poolU + Math.cos(ang) * (poolA + 1.0), poolV + Math.sin(ang) * (poolB + 1.0));
                if (g < rimMin) {
                    rimMin = g;
                    outAng = ang;
                }
            }
            poolLevel = Math.min(rimMin, facePrev - 3) + 1;
            if (poolLevel - 1 <= sl) {
                return false;
            }
            if (landsInPool(ps)) {
                break;
            }
            // the foot of the face stands too high for the basin to reach it, so the water would
            // stop on the last ledge: the ledges go on down towards the pool's level
            if (attempt == 2 || !layTreads(ps, dice, poolLevel - 1, foot + 16)) {
                return false;
            }
            finishFace();
        }
        treadLevel[treads] = poolLevel;
        // boulders: in the shallows near the rim and on the shore, away from where the water lands
        boulders = 0;
        int n = 1 + (int) (dice.next() * 4.0);
        for (int b = 0; b < n && boulders < boulderX.length; b++) {
            double ang = (dice.next() - 0.5) * 1.6 * Math.PI;
            double rr = 0.75 + 0.5 * dice.next();
            double bu = poolU + Math.cos(ang) * poolA * rr;
            double bv = poolV + Math.sin(ang) * poolB * rr;
            if (bu < poolU - 0.3 * poolA) {
                continue;
            }
            boulderX[boulders] = worldX(bu, bv) + 0.5;
            boulderZ[boulders] = worldZ(bu, bv) + 0.5;
            boulderR[boulders] = 0.6 + 0.9 * dice.next();
            boulderTop[boulders] = poolLevel + (dice.next() < 0.5 ? 0 : 1);
            boulders++;
        }
        return traceOutflow(ps, outAng, dice);
    }

    /**
     * Lays treads down the face from {@link #faceA}, each a little below the lowest ground under and
     * beside it, until their water comes within a couple of blocks of {@code target} (the top water
     * block of what lies below). Returns {@code false} if the face cannot carry a tread without
     * cutting more than {@value #MAX_TREAD_CUT} blocks, or the treads run past {@code limit}.
     */
    private boolean layTreads(PlanSampler ps, Dice dice, int target, int limit) {
        int[] range = new int[2];
        while (treads < treadLevel.length - 2) {
            int t = treadLength(dice, facePrev - 1 - target);
            // ground under each column of the tread and just beside it
            int nMin = Integer.MAX_VALUE;
            int nMax = Integer.MIN_VALUE;
            int lenOk = 0;
            int levelOk = facePrev;
            for (int len = 1; len <= t; len++) {
                // the water stands below the ground just beside it (the cleft), and is cut no
                // deeper than the highest ground under it allows
                groundRange(ps, faceA + len - 1.5, faceA + len - 0.5, range);
                nMin = Math.min(nMin, range[0]);
                nMax = Math.max(nMax, range[1]);
                // a protruding ledge on tiered falls and plunges: its front built up a block
                int lip = (style == TIERED || style == PLUNGE) && len == t && t >= 2 && dice.next() < 0.5 ? 1 : 0;
                int level = Math.min(facePrev - 1, nMin + 1 - incise + lip);
                // where the ground across the fall is uneven, the cleft is shallower and the low
                // side gets a lip of rock rather than the high side a deep cut
                while (nMax - (level - 1) > MAX_TREAD_CUT && level < Math.min(facePrev - 1, nMin + 2)) {
                    level++;
                }
                if (nMax - (level - 1) > MAX_TREAD_CUT) {
                    break;
                }
                lenOk = len;
                levelOk = level;
            }
            if (lenOk == 0) {
                return false;
            }
            if (levelOk - 1 <= target + 1) {
                return true;
            }
            treadStart[treads] = faceA - 0.5;
            treadLevel[treads] = levelOk;
            treadDepth[treads] = lenOk >= 2 || facePrev - levelOk >= 4 ? 2 : 1;
            treads++;
            facePrev = levelOk;
            faceA += lenOk;
            if (faceA > limit) {
                return false;
            }
        }
        return true;
    }

    /** The end of the face after the last tread, and how unevenly each tread's boundary breaks. */
    private void finishFace() {
        faceEnd = faceA - 0.5;
        // boundaries break unevenly across the fall, never by more than half the tread on either side
        for (int i = 1; i < treads; i++) {
            double before = i == 1 ? 3.0 : treadStart[i] - treadStart[i - 1];
            double after = (i + 1 < treads ? treadStart[i + 1] : faceEnd) - treadStart[i];
            treadJitter[i] = Math.min(1.4, 0.42 * Math.min(before, after));
        }
        faceJitter = Math.min(1.0, 0.42 * (faceEnd - treadStart[treads - 1]));
    }

    /**
     * Whether the water leaving the last tread lands in the plunge pool: the columns just past the
     * face around the centre line lie inside the basin and low enough for it to be dug there.
     */
    private boolean landsInPool(PlanSampler ps) {
        double c = centreAt(faceEnd);
        double reach = Math.min(1.0, halfAt(faceEnd));
        for (double du = faceJitter + 0.6; du <= faceJitter + 1.6; du += 1.0) {
            for (double dv = -reach; dv <= reach; dv += Math.max(0.5, reach)) {
                double u = faceEnd + du;
                double v = c + dv;
                if (poolRadius(u, v) > 0.95 || ground(ps, u, v) > poolLevel + LANDING_SHORE - 1) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Tread length for the style: short steps on plunges and horsetails, long ledges on tiered falls. */
    private int treadLength(Dice dice, int dropLeft) {
        double r = dice.next();
        return switch (style) {
            case TIERED -> r < 0.2 ? 1 : r < 0.55 ? 2 : r < 0.85 ? 3 : 4;
            case HORSETAIL -> r < 0.75 ? 1 : 2;
            case FAN -> r < 0.7 ? 1 : 2;
            // a plunge drops in a few tall leaps from ledge to ledge
            default -> r < 0.4 || dropLeft < 6 ? 1 : r < 0.8 ? 2 : 3;
        };
    }

    /**
     * Follows the steepest descent from the pool's outlet to other water, stepping the outflow's
     * surface down with the ground in small rapids. The stream ends where it joins a river, lake,
     * the sea or a stream of the land's network, at that water's level; it may also end on flat
     * ground.
     */
    private boolean traceOutflow(PlanSampler ps, double outAng, Dice dice) {
        outHalf = clamp(0.9 + 0.25 * halfAt(faceEnd) + 0.5 * dice.next(), 0.9, 2.0);
        double hu = Math.cos(outAng);
        double hv = Math.sin(outAng);
        double sx = worldX(poolU + hu * poolA * 0.6, poolV + hv * poolB * 0.6) + 0.5;
        double sz = worldZ(poolU + hu * poolA * 0.6, poolV + hv * poolB * 0.6) + 0.5;
        double hx = hu * ax - hv * az;
        double hz = hu * az + hv * ax;
        double hl = Math.hypot(hx, hz);
        hx /= hl;
        hz /= hl;
        List<double[]> pts = new ArrayList<>();
        int level = poolLevel;
        pts.add(new double[]{sx, sz, level});
        double x = sx;
        double z = sz;
        double[] info = new double[2];
        int flat = 0;
        outJoins = false;
        for (int step = 0; step < 34; step++) {
            double bestH = Double.MAX_VALUE;
            double bx = hx;
            double bz = hz;
            for (int t = -2; t <= 2; t++) {
                double ang = Math.atan2(hz, hx) + t * Math.PI / 9.0;
                double nx = x + Math.cos(ang) * 2.0;
                double nz = z + Math.sin(ang) * 2.0;
                double h = ps.height((int) Math.floor(nx), (int) Math.floor(nz)) + 0.15 * Math.abs(t);
                if (h < bestH) {
                    bestH = h;
                    bx = Math.cos(ang);
                    bz = Math.sin(ang);
                }
            }
            // turning gradually, so the stream does not double back
            hx = 0.55 * hx + 0.45 * bx;
            hz = 0.55 * hz + 0.45 * bz;
            hl = Math.hypot(hx, hz);
            hx /= hl;
            hz /= hl;
            x += hx * 2.0;
            z += hz * 2.0;
            int ix = (int) Math.floor(x);
            int iz = (int) Math.floor(z);
            ColumnSample s = ps.nearest(ix, iz);
            if (s.trailDistance < 6.0) {
                // the stream sinks into the ground short of a trail rather than cutting across it
                break;
            }
            int joinLevel = WaterSolver.NONE;
            if (ps.bodyWater(ix, iz) || ps.height(ix, iz) < model.seaLevel() - 0.5) {
                joinLevel = s.bodyInside ? (int) s.bodyLevel : model.seaLevel();
            } else if (s.stream > 0.05) {
                model.streamInfo(ix, iz, info);
                if (info[0] < 1.25 + 1.5 * s.stream) {
                    joinLevel = (int) info[1];
                }
            }
            if (joinLevel != WaterSolver.NONE) {
                // the last point lies just short of the water it joins and takes its level, so
                // the two run together (the step down to it is made a block or two upstream)
                double[] last = pts.get(pts.size() - 1);
                if (joinLevel <= last[2] && last[2] - joinLevel <= 3 && pts.size() >= 2) {
                    last[2] = joinLevel;
                    outJoins = true;
                }
                break;
            }
            int g = ps.ground(ix, iz);
            int lv = Math.min(level, g);
            flat = lv == level ? flat + 1 : 0;
            pts.add(new double[]{x, z, lv});
            level = lv;
            if (flat >= 6 || level - 1 <= model.seaLevel()) {
                break;
            }
        }
        outN = pts.size();
        outX = new double[outN];
        outZ = new double[outN];
        outLevel = new int[outN];
        double x0 = Double.MAX_VALUE;
        double z0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double z1 = -Double.MAX_VALUE;
        for (int k = 0; k < outN; k++) {
            outX[k] = pts.get(k)[0];
            outZ[k] = pts.get(k)[1];
            outLevel[k] = (int) pts.get(k)[2];
            x0 = Math.min(x0, outX[k]);
            z0 = Math.min(z0, outZ[k]);
            x1 = Math.max(x1, outX[k]);
            z1 = Math.max(z1, outZ[k]);
        }
        int pad = WALL_REACH + 4;
        outMinX = (int) Math.floor(x0) - pad;
        outMinZ = (int) Math.floor(z0) - pad;
        outMaxX = (int) Math.ceil(x1) + pad;
        outMaxZ = (int) Math.ceil(z1) + pad;
        return true;
    }

    /** World bounds of everything the course may shape. */
    void bounds() {
        double x0 = Double.MAX_VALUE;
        double z0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double z1 = -Double.MAX_VALUE;
        double maxV = channelHalf + 2.5;
        for (int k = 0; k < envN; k++) {
            maxV = Math.max(maxV, Math.abs(envC[k]) + envW[k] + 1.0);
        }
        double uLo = -channel - 2.0 * headR - 3.0;
        double uHi = Math.max(faceEnd + 2.0, poolU + poolA * 1.4 + 1.0);
        double vHi = Math.max(maxV + 2.0, Math.abs(poolV) + poolB * 1.4 + 1.0);
        double[][] corners = {{uLo, -vHi}, {uLo, vHi}, {uHi, -vHi}, {uHi, vHi}};
        for (double[] c : corners) {
            double x = worldX(c[0], c[1]);
            double z = worldZ(c[0], c[1]);
            x0 = Math.min(x0, x);
            z0 = Math.min(z0, z);
            x1 = Math.max(x1, x);
            z1 = Math.max(z1, z);
        }
        for (int k = 0; k < outN; k++) {
            x0 = Math.min(x0, outX[k]);
            z0 = Math.min(z0, outZ[k]);
            x1 = Math.max(x1, outX[k]);
            z1 = Math.max(z1, outZ[k]);
        }
        for (int b = 0; b < boulders; b++) {
            x0 = Math.min(x0, boulderX[b] - 2);
            z0 = Math.min(z0, boulderZ[b] - 2);
            x1 = Math.max(x1, boulderX[b] + 2);
            z1 = Math.max(z1, boulderZ[b] + 2);
        }
        minX = (int) Math.floor(x0) - WALL_REACH - 3;
        minZ = (int) Math.floor(z0) - WALL_REACH - 3;
        maxX = (int) Math.ceil(x1) + WALL_REACH + 3;
        maxZ = (int) Math.ceil(z1) + WALL_REACH + 3;
    }

    String region(int x, int z) {
        double u = u(x, z);
        return u < 0.5 ? "channel" : u < faceEnd ? "face" : poolRadius(u, v(x, z)) < 1.6 ? "pool" : "out";
    }
}
