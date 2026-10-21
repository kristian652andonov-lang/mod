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
 *   <li>an <b>outflow</b> stream traced down the land from the pool's lowest rim, across flats
 *       and around knolls, to the river, lake, sea or stream it joins, stepping down in small
 *       rapids; where it can join none within reach it ends in a small pond, but only on flat
 *       ground or in a hollow, never part way down a slope (such a site is no site).</li>
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
 * Every drop down to a ledge is at least three or four blocks: a step or two would be a rapid, not
 * falling water. Styles: a {@link #PLUNGE} from a real step of the land, which leaves its lip in
 * one tall drop (its first ledge may be cut back deeper under the lip, hidden behind the falling
 * column) and then comes down in ledges as long as the cut allows, a ragged column changing width
 * down the drop; {@link #TIERED} falls (long ledges, the water swinging from side to side); a
 * {@link #HORSETAIL} on steep faces, sliding down a staircase of short steps and widening as it
 * goes; and a {@link #FAN} of several strands of different widths over a long cliff band, split by
 * ribs of rock that spread and close in with the band.
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
    /** Longest tread. */
    private static final int MAX_TREAD = 5;
    /**
     * Deepest a plunge's first ledge below the lip may be cut at its back: the water leaves the
     * notch in one tall drop, and the basin it lands in, cut back into the cliff under the lip, is
     * full of water and hidden behind the falling column.
     */
    static final int MAX_LEAP_CUT = 13;
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
    /** Most two-block steps the outflow takes looking for the water it joins. */
    static final int MAX_OUT_STEPS = 110;
    /** Highest the ground ahead may rise above the outflow's surface for it to cut through. */
    private static final int MAX_OUT_RISE = 3;
    /** Most steps the outflow runs level, across a flat, before it gives up. */
    private static final int MAX_OUT_FLAT = 30;
    /** How far from the water it joins a stream's surface lies at that water's level. */
    private static final double JOIN_REACH = 6.0;
    /** Closest to a trail an outflow's course comes. */
    private static final double TRAIL_CLEARANCE = 13.0;
    /** Furthest the land may fall below a stream's last water around its end, if it joins no water. */
    private static final int MAX_END_FALL = 5;


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
    /**
     * Half-width of the water where a fan's ribs were placed: down the face their offsets spread
     * and close in with the band, so the strands between them widen, merge and pinch with it.
     */
    double ribHalf;

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
    /**
     * Radius of the small pond a stream that joins no water ends in, on the flat or in the hollow
     * where it stops (0 if it joins water).
     */
    double pondR;
    /** Length of the outflow in blocks. */
    double outLength;
    private int outMinX;
    private int outMinZ;
    private int outMaxX;
    private int outMaxZ;

    // ---- where the course may shape anything (see mayShape) -------------------------------------
    private static final int MASK_CELL = 8;
    private int maskW;
    private boolean[] mask;

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
                double spread = ribHalf > 0.0 ? clamp(halfAt(u) / ribHalf, 0.7, 1.6) : 1.0;
                b = (v - c - rockV[r] * spread - wob) / (rockB[r] * wid);
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

    /**
     * Share of a wall's deepest cut allowed {@code dist} blocks from the water: all of it close to
     * the water, fading to none at {@link #WALL_REACH}, so every cut blends into the slope around
     * it instead of stopping in a step.
     */
    static double cutFade(double dist) {
        return 1.0 - smoothstep(0.4 * WALL_REACH, WALL_REACH, dist);
    }

    /**
     * About how many blocks a point lies outside the plunge pool's rim (negative inside), along the
     * line from the pool's centre: {@code pr} is its {@link #poolRadius}, which grows in proportion
     * to the distance along that line, so the rim lies at {@code d / pr} and the point
     * {@code d (pr - 1) / pr} beyond it, the same in blocks along the long and the short axis and
     * past a lobe or a bay of the rim.
     */
    double poolDistance(double u, double v, double pr) {
        double d = Math.hypot(u - poolU, v - poolV);
        if (pr < 0.05) {
            return -Math.min(poolA, poolB);
        }
        return d * (pr - 1.0) / pr;
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
        if (!mayShape(x, z)) {
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
                    if (rise < WALL_REACH) {
                        // the cleft's sides rise steeply from the water, and what they cut fades
                        // out within WALL_REACH of it, so the cleft narrows into the slope
                        // instead of ending in a step where the cut stops
                        wall = Math.min(wall, Math.max(up + wallSlope * rise + jag(x, z, rise), natural - MAX_WALL_CUT * cutFade(rise)));
                    }
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
            double dr = poolDistance(u, v, pr);
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
                // slope, less and less away from the water: where the ground stands higher they
                // end in a short bank of rock that blends into the slope
                wall = Math.min(wall, Math.max(poolLevel + 1.3 * Math.max(0.0, dr) + jag(x, z, dr), natural - POOL_WALL_CUT * cutFade(dr)));
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
            if (dv < WALL_REACH) {
                // its sides cut no deeper than the plunge pool's, so a short notch through a shore
                // ridge never becomes a slot between sheer walls
                wall = Math.min(wall, Math.max(poolLevel + wallSlope * dv + jag(x, z, dv), natural - POOL_WALL_CUT * cutFade(dv)));
            }
        }
        // ---- outflow -------------------------------------------------------------------------------
        if (outN >= 2 && x >= outMinX && x <= outMaxX && z >= outMinZ && z <= outMaxZ) {
            double[] near = outflowNear(x, z);
            double best = near[0];
            int bestLevel = (int) near[1];
            double half = outHalf + 0.4 * noise(4, x * 0.3, z * 0.3, 83.0);
            boolean pond = near[2] <= 0.0 && !outJoins && best <= pondR + 0.4 * noise(4, x * 0.3, z * 0.3, 89.0);
            if ((best <= half && near[2] > 0.0 || pond) && pr > 1.0) {
                out.level = bestLevel;
                // the bed lies a couple of blocks below the water, or lower where the ground does
                // (the river, lake or sea the stream runs into keeps its own bed)
                out.ground = Math.min(natural, bestLevel - 2 - (best < half * 0.5 || pond && best < pondR - 1.0 ? 1 : 0));
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
                wall = Math.min(wall, Math.max(bestLevel + outWallSlope * Math.max(0.0, dv) + jag(x, z, dv), natural - OUT_WALL_CUT * cutFade(dv)));
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
        // clumps from smooth noise several blocks across, standing at different distances from
        // the water along its banks rather than in an even row; only the tops of a clump break
        // unevenly from column to column
        double reach = CRAG_REACH * (0.6 + 0.4 * model.detail3(6, x / 17.0, 143.0, z / 17.0));
        double amp = 1.0 - smoothstep(1.5, Math.max(2.5, reach), dist);
        double n = 0.8 * model.detail3(7, x / 8.0, 137.0, z / 8.0) + 0.4 * model.detail3(5, x / 4.5, 131.0, z / 4.5);
        double h = (n - 0.2) * 5.0 * amp;
        if (h < 1.0) {
            return 0;
        }
        h += 0.9 * (SeedMixer.unit(SeedMixer.hash2(seed ^ 0xC4A6L, x, z)) - 0.5);
        return (int) clamp(Math.floor(h), 1.0, max);
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
        if (!mayShape(x, z)) {
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
            if (near[0] <= outHalf + 0.4 * noise(4, x * 0.3, z * 0.3, 83.0) && near[2] > 0.0
                || near[2] <= 0.0 && !outJoins && near[0] <= pondR + 0.4 * noise(4, x * 0.3, z * 0.3, 89.0)) {
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
            best = Math.min(best, Math.max(0.0, poolDistance(u, v, poolRadius(u, v))));
        }
        return best;
    }

    /**
     * Whether a column lies under a plunge's leap: in its first ledge below the lip, which may be
     * cut deeper than the others (see {@link #MAX_LEAP_CUT}).
     */
    boolean inLeap(int x, int z) {
        if (style != PLUNGE || treads < 2) {
            return false;
        }
        double u = u(x, z);
        return u > 0.0 && u < faceEnd + 1.0 && treadAt(u, v(x, z)) == 1;
    }

    /** Whether a column lies on the face, between the lip and the pool. */
    boolean onFace(int x, int z) {
        double u = u(x, z);
        return u > 0.0 && u < faceEnd + 1.0;
    }

    /** Tallest single drop of the water down the face: from the channel, a ledge or the last ledge to the pool. */
    int maxRiser() {
        int best = channelLevel[0] - (treads > 1 ? treadLevel[1] : poolLevel);
        for (int i = 2; i <= treads; i++) {
            best = Math.max(best, treadLevel[i - 1] - (i == treads ? poolLevel : treadLevel[i]));
        }
        return best;
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
     * spreading down the drop and pinched here and there. It starts at least as wide as the channel feeding it. Its centre follows
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
                    // the strands spread down the drop, pinched here and there where the band
                    // narrows, and the whole fan leans a little one way and the other
                    w = halfW * (0.78 + 0.3 * dice.next()) * (0.9 + 0.4 * f);
                    drift = clamp(drift + (dice.next() - 0.5) * 0.9, -1.6, 1.6);
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
     * {@code waterLevel} then being its exclusive top), {@code limits} the rectangle
     * ({minX, minZ, maxX, maxZ}) the outflow must stay in. Returns {@code false} if the slope cannot
     * carry the fall without cutting or building more than a few blocks anywhere, or its outflow
     * would stop part way down a slope.
     */
    boolean fit(PlanSampler ps, int top, int foot, int waterLevel, double halfW, Dice dice, int[] limits) {
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
        return traceOutflow(ps, outAng, dice, limits);
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
            // a tread goes on past its length while the drop down to it is too small to read as
            // falling water (a step or two is a rapid, not a fall), as far as the cut allows
            int minRiser = style == TIERED || style == PLUNGE ? 4 : 3;
            // a plunge leaps from its lip: its first ledge may be cut deeper at the back
            boolean leap = style == PLUNGE && treads == 1;
            int maxCut = leap ? MAX_LEAP_CUT : MAX_TREAD_CUT;
            for (int len = 1; len <= Math.max(t, MAX_TREAD); len++) {
                if (len > t && facePrev - levelOk >= minRiser) {
                    break;
                }
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
                while (nMax - (level - 1) > maxCut && level < Math.min(facePrev - 1, nMin + 2)) {
                    level++;
                }
                if (nMax - (level - 1) > maxCut) {
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
            // (the basin under a plunge's leap a block deep: it is cut deep enough at the back)
            treadDepth[treads] = leap ? 1 : lenOk >= 2 || facePrev - levelOk >= 4 ? 2 : 1;
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

    /**
     * Tread length for the style: short steps on horsetails and fans, long ledges on tiered falls.
     * A plunge's treads are as long as the cut allows, so each drop down to the next is as tall as
     * the natural step: over a real cliff the water leaves the lip and falls free to its foot.
     */
    private int treadLength(Dice dice, int dropLeft) {
        double r = dice.next();
        return switch (style) {
            case TIERED -> r < 0.2 ? 1 : r < 0.55 ? 2 : r < 0.85 ? 3 : 4;
            case HORSETAIL -> r < 0.75 ? 1 : 2;
            case FAN -> r < 0.7 ? 1 : 2;
            default -> MAX_TREAD;
        };
    }

    /**
     * Follows the land down from the pool's outlet until the stream meets other water: a river, a
     * lake, the sea or a stream of the land's network, whose level its last point takes. Each step
     * of two blocks goes where the ground ahead is lowest, looking a few blocks further too, so the
     * stream finds its way across flats and around knolls instead of stopping on them; its surface
     * steps down with the ground in small rapids and runs level (cut into the ground, if need be)
     * over low rises. The stream may go {@value #MAX_OUT_STEPS} steps, within the rectangle
     * {@code limits} ({minX, minZ, maxX, maxZ}) the plan may reach; it ends without joining
     * anything ({@link #outJoins} false) at a trail, at a sink the land rises from on every side,
     * or where it runs out of room.
     */
    private boolean traceOutflow(PlanSampler ps, double outAng, Dice dice, int[] limits) {
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
        int level0 = level;
        int sinceDrop = 0;
        outJoins = false;
        for (int step = 0; step < MAX_OUT_STEPS; step++) {
            double bestH = Double.MAX_VALUE;
            double bx = hx;
            double bz = hz;
            double head = Math.atan2(hz, hx);
            for (int t = -4; t <= 4; t++) {
                // the ground two blocks ahead decides, the ground five and nine blocks ahead
                // shows the way down across a flat
                double ang = head + t * Math.PI / 9.0;
                double ca = Math.cos(ang);
                double sa = Math.sin(ang);
                double h = ps.height((int) Math.floor(x + 2.0 * ca), (int) Math.floor(z + 2.0 * sa))
                    + 0.5 * ps.height((int) Math.floor(x + 5.0 * ca), (int) Math.floor(z + 5.0 * sa))
                    + 0.25 * ps.height((int) Math.floor(x + 9.0 * ca), (int) Math.floor(z + 9.0 * sa)) + 0.12 * t * t;
                if (h < bestH) {
                    bestH = h;
                    bx = ca;
                    bz = sa;
                }
            }
            // turning gradually, so the stream bends rather than zigzags
            hx = 0.5 * hx + 0.5 * bx;
            hz = 0.5 * hz + 0.5 * bz;
            hl = Math.hypot(hx, hz);
            hx /= hl;
            hz /= hl;
            x += hx * 2.0;
            z += hz * 2.0;
            int ix = (int) Math.floor(x);
            int iz = (int) Math.floor(z);
            if (x < limits[0] || z < limits[1] || x > limits[2] || z > limits[3]) {
                break;
            }
            ColumnSample s = ps.nearest(ix, iz);
            if (s.trailDistance < TRAIL_CLEARANCE) {
                // the stream may not cut across a trail: it stops short of it, with room for the
                // pond it ends in and its banks
                break;
            }
            int joinLevel = WaterSolver.NONE;
            double body = ps.bodyLevel(ix, iz);
            if (!Double.isNaN(body)) {
                joinLevel = (int) body;
            } else if (ps.height(ix, iz) < model.seaLevel() - 0.5 || ps.ground(ix, iz) < model.seaLevel()) {
                // the sea, or ground below sea level by the coast, which the water table floods
                joinLevel = model.seaLevel();
            } else if (s.stream > 0.05) {
                model.streamInfo(ix, iz, info);
                if (info[0] < 1.25 + 1.5 * s.stream) {
                    joinLevel = (int) info[1];
                }
            }
            if (joinLevel != WaterSolver.NONE) {
                if (joinLevel > level || pts.size() < 2) {
                    // water the stream cannot run down into (a lake above it): no place for a fall
                    return false;
                }
                // the stream runs on into the water it joins at that water's level: its last
                // few blocks, within reach of that water, lie at its level, and above them it
                // comes down to it in steps of at most three blocks
                pts.add(new double[]{x, z, joinLevel});
                for (int k = pts.size() - 2; k >= 1; k--) {
                    double[] q = pts.get(k);
                    double need = Math.hypot(q[0] - x, q[1] - z) < JOIN_REACH ? joinLevel : pts.get(k + 1)[2] + 3;
                    if (q[2] <= need) {
                        break;
                    }
                    q[2] = need;
                }
                outJoins = true;
                break;
            }
            int g = ps.ground(ix, iz);
            if (g > level + MAX_OUT_RISE) {
                // a sink: the land rises ahead more than the stream may cut through
                break;
            }
            int lv = Math.min(level, g);
            sinceDrop = lv < level ? 0 : sinceDrop + 1;
            pts.add(new double[]{x, z, lv});
            level = lv;
            if (sinceDrop > MAX_OUT_FLAT) {
                break;
            }
        }
        if (!outJoins && level0 - level < 2 && pts.size() > 4) {
            // a stream that never got going: keep only its first stretch out of the pool
            pts.subList(4, pts.size()).clear();
        }
        pondR = 0.0;
        if (!outJoins) {
            // a stream that joins no water ends in a small pond, but only on flat ground or in a
            // hollow: where the land still falls away around its end, the water would just stop
            // part way down a slope, and the site is no good
            double[] last = pts.get(pts.size() - 1);
            int surface = (int) last[2] - 1;
            for (int r = 6; r <= 18; r += 6) {
                for (int k = 0; k < 16; k++) {
                    double ang = k * Math.PI / 8.0;
                    if (ps.ground((int) Math.floor(last[0] + r * Math.cos(ang)), (int) Math.floor(last[1] + r * Math.sin(ang))) < surface - MAX_END_FALL) {
                        return false;
                    }
                }
            }
            pondR = clamp(outHalf + 1.0 + 1.5 * dice.next(), 2.0, 3.5);
        }
        outN = pts.size();
        outX = new double[outN];
        outZ = new double[outN];
        outLevel = new int[outN];
        double x0 = Double.MAX_VALUE;
        double z0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double z1 = -Double.MAX_VALUE;
        outLength = 0.0;
        for (int k = 0; k < outN; k++) {
            outX[k] = pts.get(k)[0];
            outZ[k] = pts.get(k)[1];
            outLevel[k] = (int) pts.get(k)[2];
            if (k > 0) {
                outLength += Math.hypot(outX[k] - outX[k - 1], outZ[k] - outZ[k - 1]);
            }
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
        // the water's own extent: the winding channel, the band down the face with its frayed edge,
        // and the pool out to its farthest lobe; the walls beside them reach WALL_REACH further,
        // which the margin below adds
        double maxV = channelHalf + 3.0;
        for (int k = 0; k < envN; k++) {
            maxV = Math.max(maxV, Math.abs(envC[k]) + envW[k] + 1.0 + 0.25 * envW[k]);
        }
        double uLo = -channel - 2.0 * headR - 3.0;
        double uHi = Math.max(faceEnd + 2.0, poolU + poolA * 1.6 + 1.0);
        double vHi = Math.max(maxV + 2.0, Math.abs(poolV) + poolB * 1.6 + 1.0);
        double[][] corners = {{uLo, -vHi}, {uLo, vHi}, {uHi, -vHi}, {uHi, vHi}};
        for (double[] c : corners) {
            double x = worldX(c[0], c[1]);
            double z = worldZ(c[0], c[1]);
            x0 = Math.min(x0, x);
            z0 = Math.min(z0, z);
            x1 = Math.max(x1, x);
            z1 = Math.max(z1, z);
        }
        for (int b = 0; b < boulders; b++) {
            x0 = Math.min(x0, boulderX[b] - 2);
            z0 = Math.min(z0, boulderZ[b] - 2);
            x1 = Math.max(x1, boulderX[b] + 2);
            z1 = Math.max(z1, boulderZ[b] + 2);
        }
        int pad = WALL_REACH + 3;
        int coreX0 = (int) Math.floor(x0) - pad;
        int coreZ0 = (int) Math.floor(z0) - pad;
        int coreX1 = (int) Math.ceil(x1) + pad;
        int coreZ1 = (int) Math.ceil(z1) + pad;
        for (int k = 0; k < outN; k++) {
            x0 = Math.min(x0, outX[k]);
            z0 = Math.min(z0, outZ[k]);
            x1 = Math.max(x1, outX[k]);
            z1 = Math.max(z1, outZ[k]);
        }
        minX = (int) Math.floor(x0) - pad;
        minZ = (int) Math.floor(z0) - pad;
        maxX = (int) Math.ceil(x1) + pad;
        maxZ = (int) Math.ceil(z1) + pad;
        // a long outflow makes the bounds a large box that is mostly far from the water: the cells
        // of a coarse grid that the course may shape are the box around the lip, face and pool and
        // a band along the stream, as wide as its water, banks, crags and pond reach
        maskW = (maxX - minX) / MASK_CELL + 1;
        int maskH = (maxZ - minZ) / MASK_CELL + 1;
        mask = new boolean[maskW * maskH];
        markMask(coreX0, coreZ0, coreX1, coreZ1);
        int reach = (int) Math.ceil(Math.max(outHalf + 0.4, pondR + 0.4) + WALL_REACH) + 2;
        for (int k = 0; k < outN; k++) {
            int ox = (int) Math.floor(outX[k]);
            int oz = (int) Math.floor(outZ[k]);
            markMask(ox - reach, oz - reach, ox + reach, oz + reach);
        }
    }

    private void markMask(int x0, int z0, int x1, int z1) {
        int i0 = Math.max(0, (x0 - minX) / MASK_CELL);
        int j0 = Math.max(0, (z0 - minZ) / MASK_CELL);
        int i1 = Math.min(maskW - 1, (Math.min(x1, maxX) - minX) / MASK_CELL);
        int j1 = Math.min(mask.length / maskW - 1, (Math.min(z1, maxZ) - minZ) / MASK_CELL);
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                mask[j * maskW + i] = true;
            }
        }
    }

    /** Whether a cell of the coarse grid the course may shape meets the given rectangle. */
    boolean meets(int x0, int z0, int x1, int z1) {
        return meets(minX, minZ, maxX, maxZ, mask, maskW, x0, z0, x1, z1);
    }

    /** The cells of the coarse grid the course may shape, row by row (see {@link #mayShape}). */
    boolean[] mask() {
        return mask;
    }

    /** Width of {@link #mask} in cells. */
    int maskWidth() {
        return maskW;
    }

    /**
     * {@link #meets} of a course with the given bounds and {@link #mask}, for what is kept of a
     * course once it is gone (see {@code FallPlanner}'s reach).
     */
    static boolean meets(int minX, int minZ, int maxX, int maxZ, boolean[] mask, int maskW, int x0, int z0, int x1, int z1) {
        x0 = Math.max(x0, minX);
        z0 = Math.max(z0, minZ);
        x1 = Math.min(x1, maxX);
        z1 = Math.min(z1, maxZ);
        if (x0 > x1 || z0 > z1) {
            return false;
        }
        if (mask == null) {
            return true;
        }
        for (int j = (z0 - minZ) / MASK_CELL; j <= (z1 - minZ) / MASK_CELL; j++) {
            for (int i = (x0 - minX) / MASK_CELL; i <= (x1 - minX) / MASK_CELL; i++) {
                if (mask[j * maskW + i]) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a cell of the coarse grid the course may shape, grown by {@code margin} blocks, meets
     * the part of plan {@code o} that it may shape (see {@link FallPlan#overlaps}).
     */
    boolean touches(FallPlan o, int margin) {
        if (mask == null) {
            return o.meets(minX - margin, minZ - margin, maxX + margin, maxZ + margin);
        }
        int maskH = mask.length / maskW;
        for (int j = 0; j < maskH; j++) {
            for (int i = 0; i < maskW; i++) {
                if (!mask[j * maskW + i]) {
                    continue;
                }
                int x0 = minX + i * MASK_CELL;
                int z0 = minZ + j * MASK_CELL;
                if (o.meets(x0 - margin, z0 - margin, Math.min(x0 + MASK_CELL - 1, maxX) + margin, Math.min(z0 + MASK_CELL - 1, maxZ) + margin)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the course may shape a column at all: inside its bounds and in a cell of the coarse
     * grid near the lip, the face, the pool or the outflow. Every column {@link #shape} changes
     * passes; most of a long outflow's bounding box does not, and is skipped at once.
     */
    boolean mayShape(int x, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }
        return mask == null || mask[((z - minZ) / MASK_CELL) * maskW + (x - minX) / MASK_CELL];
    }

    String region(int x, int z) {
        double u = u(x, z);
        return u < 0.5 ? "channel" : inLeap(x, z) ? "leap" : u < faceEnd ? "face" : poolRadius(u, v(x, z)) < 1.6 ? "pool" : "out";
    }
}
