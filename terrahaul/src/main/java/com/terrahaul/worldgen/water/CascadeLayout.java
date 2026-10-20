package com.terrahaul.worldgen.water;

import com.terrahaul.worldgen.noise.TMath;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.List;

/**
 * Where the water of a forest cascade goes and how it reshapes the ground: a pure function of the
 * plan, so every chunk the cascade spans builds the same columns (see {@link FallPlanner}, which
 * lays it out, and {@link FallPlan}, which hands it the columns).
 * <p>
 * The water follows a winding <i>course</i> down the slope, a polyline with a point every
 * {@value #COURSE_STEP} blocks. Each pool widens a stretch of the course into an irregular rounded
 * basin, the union of discs along the stretch (its half-width varies from the inflow through the
 * middle to the lip, it is fuller on one side, its outline wobbles and is moved in and out by
 * noise), and ends in a lip, where the water spills one to four blocks into the next pool down. A
 * lower pool keeps clear of the pools above it except in the lip it is fed from, so the water only
 * ever falls at the lips, sometimes split in two by a rock standing in the lip.
 * <p>
 * Around the pools the ground is held up to the water in a rim and a sloping apron where a pool
 * stands above the slope, and cut back into a hollow rising unevenly from the water where the
 * slope stands above it: in rock where the cut is deep or a patch of rock shows, in soil
 * elsewhere, with boulders lying on the banks.
 */
final class CascadeLayout {
    /** Distance between neighbouring points of a course. */
    static final double COURSE_STEP = 2.0;
    /** A lower pool's water keeps this far from the outline of any pool above, except in its lip. */
    static final double GUARD = 1.3;
    /** Columns up to this far beyond a pool's outline hold its water. */
    static final double RIM = 1.6;
    /** Width of the sloping apron beyond a rim that stands above the ground. */
    static final double APRON = 3.0;
    /** Furthest beyond its outline the hollow around a pool is cut back into the slope. */
    static final double BANK_REACH = 9.0;
    /** How far, in blocks, noise moves the outlines of pools in and out. */
    static final double OUTLINE_NOISE = 1.4;

    private final TerrainModel model;
    /** The course the water takes, from the head of the cascade down. */
    final double[] courseX;
    final double[] courseZ;
    /** Distance along the course of each point. */
    final double[] courseS;
    Pool[] pools = new Pool[0];
    /** Rocks standing in the water: in lips, where they split the fall, and in wide pools. */
    double[] stoneX = new double[0];
    double[] stoneZ = new double[0];
    double[] stoneR = new double[0];
    int[] stoneTop = new int[0];
    /** Boulders lying on the banks, {@code boulderH} blocks above the shaped ground at their middle. */
    double[] boulderX = new double[0];
    double[] boulderZ = new double[0];
    double[] boulderR = new double[0];
    int[] boulderH = new int[0];

    /** One pool: a stretch of the course widened into an irregular basin. */
    static final class Pool {
        /** The course points the pool spans; the last is its lip, where it spills into the next pool. */
        int from;
        int to;
        /** Exclusive top of the water. */
        int level;
        /** Extra depth towards the middle. */
        int depth;
        /** Half-widths at the inflow end, in the middle and at the lip. */
        double wIn;
        double wMid;
        double wOut;
        /** How much fuller the pool is on the left of the course (positive) or the right. */
        double skew;
        /** Amplitude and phase of a slow wobble of the outline along the course. */
        double wobble;
        double phase;
        /** Half-width of the lip the water spills over. */
        double lipHalf;
        /** Rise of the hollow around the pool with distance {@code e} from the water: {@code a e + b e^2}. */
        double bankA;
        double bankB;
        /** Fall of the apron per block outwards. */
        double apron;
        // derived by prepare(): what the pool can reach; where its lip is, the way the water
        // leaves it, and the stretch of the course the lip covers with its bounds
        double minX;
        double minZ;
        double maxX;
        double maxZ;
        double lipX;
        double lipZ;
        double lipDx;
        double lipDz;
        double lipS0;
        double lipS1;
        int lipK0;
        int lipK1;
        double lipMinX;
        double lipMinZ;
        double lipMaxX;
        double lipMaxZ;
        /** The discs whose union is the pool's outline. */
        double[] discX = new double[0];
        double[] discZ = new double[0];
        double[] discR = new double[0];
    }

    /** A layout along a course of {@code {x, z, ...}} points; the pools are added afterwards. */
    CascadeLayout(TerrainModel model, List<double[]> course) {
        this.model = model;
        int m = course.size();
        courseX = new double[m];
        courseZ = new double[m];
        courseS = new double[m];
        for (int k = 0; k < m; k++) {
            courseX[k] = course.get(k)[0];
            courseZ[k] = course.get(k)[1];
            courseS[k] = k == 0 ? 0.0 : courseS[k - 1] + Math.hypot(courseX[k] - courseX[k - 1], courseZ[k] - courseZ[k - 1]);
        }
    }

    int poolCount() {
        return pools.length;
    }

    /** The course from the head of the cascade to the lip of its lowest pool, as {@code {x, z}} points. */
    double[][] course() {
        int n = pools[pools.length - 1].to + 1;
        double[][] out = new double[n][];
        for (int k = 0; k < n; k++) {
            out[k] = new double[]{courseX[k], courseZ[k]};
        }
        return out;
    }

    /** What the cascade makes of the column at {@code (x, z)}; see {@link WaterPlan#shape}. */
    boolean shape(int x, int z, int natural, WaterPlan.Shape out) {
        int tiers = pools.length;
        double px = x + 0.5;
        double pz = z + 0.5;
        double n = outlineNoise(x, z);
        double[] e = new double[tiers];
        boolean near = false;
        for (int i = 0; i < tiers; i++) {
            e[i] = edge(i, px, pz, n);
            near |= e[i] <= BANK_REACH;
        }
        if (!near) {
            return false;
        }
        // the water: an upper pool owns the columns it shares with the next; a lower pool keeps
        // clear of the pools above it except in the lip it is fed from, which it fills in front of
        // the falling water
        for (int i = 0; i < tiers; i++) {
            Pool q = pools[i];
            boolean lip = i > 0 && inLip(i - 1, px, pz);
            if ((e[i] <= 0.0 || lip) && !guarded(i, e, px, pz)) {
                // rocks stand only in the water, never in its banks
                for (int r = 0; r < stoneX.length; r++) {
                    double dx = px - stoneX[r];
                    double dz = pz - stoneZ[r];
                    if (dx * dx + dz * dz <= stoneR[r] * stoneR[r]) {
                        out.ground = stoneTop[r];
                        out.role = WaterSolver.ROLE_BOULDER;
                        return true;
                    }
                }
                int bed = q.level - 2 - (int) Math.round(q.depth * TMath.smoothstep(0.0, 2.5, -e[i]));
                out.ground = lip ? Math.min(bed, q.level - 3) : bed;
                out.level = q.level;
                out.role = WaterSolver.ROLE_BED;
                return true;
            }
        }
        // the ground around: held up to the water in a rim and a sloping apron outside it, cut
        // back into a hollow that rises from the water where the slope stands above it
        int floor = Integer.MIN_VALUE;
        int carve = Integer.MAX_VALUE;
        int below = Integer.MAX_VALUE;
        double n2 = 0.8 * model.detail3(5, x / 9.0, 41.0, z / 9.0);
        for (int i = 0; i < tiers; i++) {
            Pool q = pools[i];
            if (e[i] > BANK_REACH) {
                continue;
            }
            if (e[i] <= 1.5) {
                below = Math.min(below, q.level);
            }
            if (e[i] <= RIM) {
                floor = Math.max(floor, q.level - 1);
            } else if (e[i] <= RIM + APRON) {
                floor = Math.max(floor, q.level - 1 - (int) Math.ceil((e[i] - RIM) * q.apron));
            }
            double d = Math.max(0.0, e[i]);
            carve = Math.min(carve, q.level - 1 + (int) Math.floor(Math.max(0.0, q.bankA * d + q.bankB * d * d + 1.3 * n2)));
        }
        int ground = Math.max(Math.min(natural, carve), floor);
        for (int b = 0; b < boulderX.length; b++) {
            double dx = px - boulderX[b];
            double dz = pz - boulderZ[b];
            double d2 = (dx * dx + dz * dz) / (boulderR[b] * boulderR[b]);
            if (d2 <= 1.0) {
                // rounded: lower towards the edge, never below a block
                out.ground = ground + Math.max(1, (int) Math.ceil(boulderH[b] * Math.sqrt(1.0 - d2) - 0.25));
                out.role = WaterSolver.ROLE_BOULDER;
                return true;
            }
        }
        // the ledge beside a lip, where a pool's rim stands above the next pool down, is the rock
        // step the water falls over, not a bank of soil
        boolean ledge = floor != Integer.MIN_VALUE && floor - below >= 1;
        if (ground == natural && !ledge) {
            return false;
        }
        out.ground = ground;
        // deep cuts show rock, and so do patches of the shallow ones; built-up ground is soil
        // with patches of mossy stone
        boolean rocky = model.detail3(7, x / 6.0, 57.0, z / 6.0) > 0.3;
        if (ledge) {
            out.role = ground <= natural ? WaterSolver.ROLE_ROCK : WaterSolver.ROLE_DAM;
        } else if (ground < natural) {
            out.role = natural - ground >= 4 || rocky ? WaterSolver.ROLE_ROCK : WaterSolver.ROLE_NATURAL;
        } else {
            out.role = ground - natural >= 2 || rocky ? WaterSolver.ROLE_DAM : WaterSolver.ROLE_NATURAL;
        }
        return true;
    }

    /** Noise that moves the outlines of the pools in and out, the same for every pool. */
    double outlineNoise(int x, int z) {
        return model.detail3(6, x / 5.0, 31.0, z / 5.0);
    }

    /**
     * Signed distance of the point {@code (px, pz)} beyond the outline of pool {@code i}: negative
     * inside, {@link Double#MAX_VALUE} beyond the reach of its banks. The outline is the union of
     * discs along the pool's stretch of the course (see {@link #prepare}), so a short pool is
     * round and a long one a winding run, never a thin blade across the course.
     */
    double edge(int i, double px, double pz, double n) {
        Pool q = pools[i];
        if (px < q.minX || px > q.maxX || pz < q.minZ || pz > q.maxZ) {
            return Double.MAX_VALUE;
        }
        double best = Double.MAX_VALUE;
        for (int d = 0; d < q.discR.length; d++) {
            // a disc can only do better than the best so far if the point is within
            // best + radius of its centre; the square root is taken only then
            double lim = best + q.discR[d];
            if (lim <= 0.0) {
                continue;
            }
            double dx = px - q.discX[d];
            double dz = pz - q.discZ[d];
            double d2 = dx * dx + dz * dz;
            if (d2 < lim * lim) {
                best = Math.sqrt(d2) - q.discR[d];
            }
        }
        return best - OUTLINE_NOISE * n;
    }

    /**
     * Half-width of a pool at fraction {@code t} of its stretch: from the inflow end through the
     * middle to the lip, before it is shifted to one side and wobbles.
     */
    static double width(Pool q, double t) {
        double w = q.wIn + (q.wOut - q.wIn) * t + (q.wMid - 0.5 * (q.wIn + q.wOut)) * hump(t);
        return Math.max(0.9, w);
    }

    /** How far towards the middle of its stretch a pool is at its widest: 0 at the ends, 1 in the middle. */
    private static double hump(double t) {
        return Math.pow(Math.sin(Math.PI * TMath.clamp(t, 0.0, 1.0)), 0.7);
    }

    /**
     * How far the water of pool {@code q} reaches from the course on one side ({@code side} +1
     * left, -1 right) at fraction {@code t} of its stretch, outline noise aside.
     */
    static double reach(Pool q, double t, double side) {
        double w = width(q, t);
        return w + side * q.skew * w * hump(t);
    }

    /**
     * Whether a point lies in the lip of pool {@code i}: the strip in front of the pool's end,
     * as wide as the lip, where the next pool's water meets this one's and the water falls.
     */
    boolean inLip(int i, double px, double pz) {
        return i < pools.length - 1 && inFront(i, px, pz);
    }

    /**
     * Whether a point lies in the strip in front of pool {@code i}'s end, where its lip would be:
     * within the lip's half-width of the course, from just inside the pool's end to a little
     * beyond its round end, where the next pool begins. The strip follows the course, so it
     * bends with it and always leads to the next pool.
     */
    boolean inFront(int i, double px, double pz) {
        Pool q = pools[i];
        if (px < q.lipMinX || px > q.lipMaxX || pz < q.lipMinZ || pz > q.lipMaxZ) {
            return false;
        }
        double half = q.lipHalf + 0.5;
        for (int k = q.lipK0; k <= q.lipK1; k++) {
            double ax = courseX[k];
            double az = courseZ[k];
            double dx = courseX[k + 1] - ax;
            double dz = courseZ[k + 1] - az;
            double len = courseS[k + 1] - courseS[k];
            if (len <= 0.0) {
                continue;
            }
            // only the part of the segment within the lip's stretch of the course
            double t0 = TMath.clamp((q.lipS0 - courseS[k]) / len, 0.0, 1.0);
            double t1 = TMath.clamp((q.lipS1 - courseS[k]) / len, 0.0, 1.0);
            double t = TMath.clamp(((px - ax) * dx + (pz - az) * dz) / (len * len), t0, t1);
            double cx = ax + t * dx - px;
            double cz = az + t * dz - pz;
            if (cx * cx + cz * cz <= half * half) {
                return true;
            }
        }
        return false;
    }

    /** Whether pool {@code i}'s water would come too close to a pool above it outside the lip it is fed from. */
    private boolean guarded(int i, double[] e, double px, double pz) {
        for (int k = 0; k < i; k++) {
            if (e[k] <= GUARD && !(k == i - 1 && inLip(k, px, pz))) {
                return true;
            }
        }
        return false;
    }

    /** The point at distance {@code s} along the course and the course's direction there: {@code {x, z, dx, dz}}. */
    double[] courseAt(double s) {
        int n = courseX.length;
        int k = 0;
        while (k < n - 2 && courseS[k + 1] < s) {
            k++;
        }
        double len = Math.max(1.0E-9, courseS[k + 1] - courseS[k]);
        double t = TMath.clamp((s - courseS[k]) / len, 0.0, 1.0);
        double dx = (courseX[k + 1] - courseX[k]) / len;
        double dz = (courseZ[k + 1] - courseZ[k]) / len;
        return new double[]{courseX[k] + t * (courseX[k + 1] - courseX[k]), courseZ[k] + t * (courseZ[k + 1] - courseZ[k]), dx, dz};
    }

    /** The middle of pool {@code i}'s stretch of the course and the course's direction there. */
    double[] poolMiddle(int i) {
        return courseAt(0.5 * (courseS[pools[i].from] + courseS[pools[i].to]));
    }

    /**
     * Derives the outline of each pool, what it can reach and the frames of the lips, once the
     * pools are laid out. The outline is the union of discs every half block along the pool's
     * stretch: their radius is the pool's half-width there, wobbling slowly, and their centres are
     * shifted to the side the pool is fuller on, most in the middle.
     */
    void prepare() {
        for (Pool q : pools) {
            double s0 = courseS[q.from];
            double len = courseS[q.to] - s0;
            int count = Math.max(1, (int) Math.ceil(len / 0.5)) + 1;
            q.discX = new double[count];
            q.discZ = new double[count];
            q.discR = new double[count];
            q.minX = Double.MAX_VALUE;
            q.minZ = Double.MAX_VALUE;
            q.maxX = -Double.MAX_VALUE;
            q.maxZ = -Double.MAX_VALUE;
            for (int d = 0; d < count; d++) {
                double t = count == 1 ? 0.5 : d / (double) (count - 1);
                double arc = t * len;
                double[] at = courseAt(s0 + arc);
                double w = width(q, t);
                double off = q.skew * w * hump(t);
                q.discX[d] = at[0] - at[3] * off;
                q.discZ[d] = at[1] + at[2] * off;
                q.discR[d] = Math.max(0.9, w * (1.0 + q.wobble * Math.sin(arc / 2.2 + q.phase)));
                double reach = q.discR[d] + OUTLINE_NOISE + BANK_REACH + 1.0;
                q.minX = Math.min(q.minX, q.discX[d] - reach);
                q.minZ = Math.min(q.minZ, q.discZ[d] - reach);
                q.maxX = Math.max(q.maxX, q.discX[d] + reach);
                q.maxZ = Math.max(q.maxZ, q.discZ[d] + reach);
            }
            // the lip faces the way the course goes on from the pool's end to where the next
            // pool begins (over that whole stretch, so a jink of the course does not turn it)
            int n = courseX.length;
            double end = courseS[q.to];
            double[] ahead = courseAt(Math.min(courseS[n - 1], end + q.wOut + 1.0));
            double dx = ahead[0] - courseX[q.to];
            double dz = ahead[1] - courseZ[q.to];
            if (Math.hypot(dx, dz) < 0.5) {
                dx = courseX[q.to] - courseX[q.to - 1];
                dz = courseZ[q.to] - courseZ[q.to - 1];
            }
            double l = Math.max(1.0E-9, Math.hypot(dx, dz));
            q.lipX = courseX[q.to];
            q.lipZ = courseZ[q.to];
            q.lipDx = dx / l;
            q.lipDz = dz / l;
            q.lipS0 = end - 1.0;
            q.lipS1 = Math.min(courseS[n - 1], end + q.wOut + 2.6);
            q.lipK0 = Math.max(0, q.to - 1);
            q.lipK1 = Math.min(q.to, n - 2);
            while (q.lipK1 < n - 2 && courseS[q.lipK1 + 1] < q.lipS1) {
                q.lipK1++;
            }
            double r = q.lipHalf + 1.0;
            q.lipMinX = Double.MAX_VALUE;
            q.lipMinZ = Double.MAX_VALUE;
            q.lipMaxX = -Double.MAX_VALUE;
            q.lipMaxZ = -Double.MAX_VALUE;
            for (int k = q.lipK0; k <= q.lipK1 + 1; k++) {
                q.lipMinX = Math.min(q.lipMinX, courseX[k] - r);
                q.lipMinZ = Math.min(q.lipMinZ, courseZ[k] - r);
                q.lipMaxX = Math.max(q.lipMaxX, courseX[k] + r);
                q.lipMaxZ = Math.max(q.lipMaxZ, courseZ[k] + r);
            }
        }
    }
}
