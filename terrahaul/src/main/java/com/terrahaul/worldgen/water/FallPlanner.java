package com.terrahaul.worldgen.water;

import static com.terrahaul.worldgen.noise.TMath.clamp;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.density.TerrainDetail;
import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plans waterfalls and forest cascades from the terrain model. Every plan is a pure function of
 * the world seed and its grid cell, cached per model, so the chunks a plan covers build exactly
 * the same thing and {@code /terrahaul find} reports only what exists.
 * <p>
 * <b>Waterfalls</b> ({@value #FALL_CELL}-block cells) look for a knickpoint: high ground that is
 * level or rising behind, falling steeply ahead in one of sixteen directions, where the face drops
 * at least {@value #MIN_STEEPNESS} blocks per block of run. The lip is placed at block resolution,
 * the channel's water one block below the lip's top, and the foot where the slope flattens out or
 * reaches the sea or a lake. The fall comes down that slope as it is: its ledges, cleft, crags, pool
 * and outflow are fitted to the natural ground (see {@link FallCourse}), never carved into it as a
 * bowl. A site that would need more than a few blocks cut or built anywhere, more ground cut in all
 * than {@link #cutCap(FallPlan)} allows (the 3D terrain's rock over the model surface that the carve
 * removes included), a cut reaching its bounds or standing far from its water, cut walls around the
 * foot of the fall, rock built over open air, water that would not run all the way down to its
 * pool, or an outflow that would stop part way down a slope, is no site. A real step of the land
 * (a cliff, a canyon's or a plateau's edge) makes a plunge, gentler faces tiered falls, steep ones
 * horsetails, long cliff bands in wet climates fans of strands. Falls fed by a stream or with high
 * ground behind them, in a hollow, over a real step and whose stream runs on to other water rank
 * first. Falls are more frequent and wider in wet climates, and where a canyon, fjord or sea cliff
 * gives the land a sudden edge.
 * <p>
 * <b>Cascades</b> ({@value #CASCADE_CELL}-block cells) follow a winding course down a moderate
 * humid slope, swinging from side to side and running along the slope for a stretch now and
 * then, and lay two to eight pools of different kinds along it, each one to four blocks below the
 * last.
 * <p>
 * Plans never overlap: a plan that overlaps a better one in a nearby cell is dropped, and a
 * cascade gives way to any waterfall. The decision uses only the unfiltered plans of the cells
 * around, so it never depends on the order in which cells are computed; when two threads plan the
 * same cell at once, both use the plan stored first.
 * <p>
 * Planning a cell has two parts. Laying out its candidates (the cheap look at the cell, the scan
 * for spots, each spot's course and its bounds) is the cheaper one; checking what each would
 * build (every column of its footprint against the 3D terrain, the view) is the costly one. A
 * cell's plan, if it has one, is one of its laid-out candidates, with the bounds it was laid out
 * with, so the bounds of all of them (the cell's <i>reach</i>) say where its plan can be without
 * checking any. Questions about a small area (the cave guard of a column, the plans near a chunk,
 * whether a neighbour's plan could overlap) only check the cells whose reach meets that area:
 * structure placement, {@code /locate} and explorer maps probe the ground at single columns far
 * apart and must not plan every cell around each of them in full. The reach is a pure function
 * of the cell too, and the laid-out candidates are kept for a while, so a cell planned after its
 * reach was asked for is not laid out twice.
 */
public final class FallPlanner {
    public static final int FALL_CELL = 256;
    public static final int CASCADE_CELL = 224;
    /** Furthest any part of a cascade reaches from the cell holding its first pool. */
    private static final int REACH = 120;
    /**
     * Furthest any part of a waterfall reaches from the cell holding its lip: its outflow may run
     * well beyond the cell to the water it joins.
     */
    private static final int FALL_REACH = 176;
    /**
     * How many cells away a waterfall that may overlap a cell's own lies: plans reach
     * {@value #FALL_REACH} blocks beyond their cells, so two cells apart may still meet.
     */
    private static final int FALL_NEIGHBOURS = 1 + (2 * FALL_REACH + 6) / FALL_CELL;
    private static final FallPlan NONE = new FallPlan(null, -1, 0L, 0, 0);
    private static final Map<TerrainModel, FallPlanner> PLANNERS = Collections.synchronizedMap(new WeakHashMap<>());

    private final TerrainModel model;
    private final ConcurrentHashMap<Long, FallPlan> rawFalls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, FallPlan> falls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, FallPlan> rawCascades = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, FallPlan> cascades = new ConcurrentHashMap<>();
    /** Reach of the cells whose reach was asked for before their plan (see {@link #reach}). */
    private final ConcurrentHashMap<Long, Footprint[]> fallReach = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Footprint[]> cascadeReach = new ConcurrentHashMap<>();
    /** Their laid-out candidates, until the cell is planned (see {@link Laid}). */
    private final Map<Long, Laid> laidFalls = laidCache();
    private final Map<Long, Laid> laidCascades = laidCache();
    private final long fallSeed;
    private final long cascadeSeed;

    private FallPlanner(TerrainModel model) {
        this.model = model;
        this.fallSeed = model.siteSeed() ^ 0xFA11L;
        this.cascadeSeed = model.siteSeed() ^ 0xCA5CL;
    }

    public static FallPlanner of(TerrainModel model) {
        synchronized (PLANNERS) {
            return PLANNERS.computeIfAbsent(model, FallPlanner::new);
        }
    }

    /**
     * Adds the active plans whose bounds intersect the given block rectangle. Only the cells whose
     * reach meets the rectangle are planned in full.
     */
    public static void plansNear(TerrainModel model, int minX, int minZ, int maxX, int maxZ, List<WaterPlan> out) {
        FallPlanner planner = of(model);
        for (int cx = Math.floorDiv(minX - FALL_REACH, FALL_CELL); cx <= Math.floorDiv(maxX + FALL_REACH, FALL_CELL); cx++) {
            for (int cz = Math.floorDiv(minZ - FALL_REACH, FALL_CELL); cz <= Math.floorDiv(maxZ + FALL_REACH, FALL_CELL); cz++) {
                if (!planner.mayMeet(false, cx, cz, minX, minZ, maxX, maxZ, false)) {
                    continue;
                }
                FallPlan p = planner.fall(cx, cz);
                if (p != null && p.minX <= maxX && p.maxX >= minX && p.minZ <= maxZ && p.maxZ >= minZ) {
                    out.add(p);
                }
            }
        }
        for (int cx = Math.floorDiv(minX - REACH, CASCADE_CELL); cx <= Math.floorDiv(maxX + REACH, CASCADE_CELL); cx++) {
            for (int cz = Math.floorDiv(minZ - REACH, CASCADE_CELL); cz <= Math.floorDiv(maxZ + REACH, CASCADE_CELL); cz++) {
                if (!planner.mayMeet(true, cx, cz, minX, minZ, maxX, maxZ, false)) {
                    continue;
                }
                FallPlan p = planner.cascade(cx, cz);
                if (p != null && p.minX <= maxX && p.maxX >= minX && p.minZ <= maxZ && p.maxZ >= minZ) {
                    out.add(p);
                }
            }
        }
    }

    /**
     * Furthest from a column, in four-block grid cells beyond the cells the column's grid corner
     * touches, that what a plan builds keeps caves and carvers away (see {@link #guardLevel}).
     */
    private static final int GUARD_CELLS = 2;

    /**
     * The lowest top of what a waterfall or cascade makes of the ground within about
     * {@value #GUARD_CELLS} grid cells (eight to eleven blocks) of the grid corner at or below-left
     * of {@code (x, z)}: the surface of its water, or the ground it shapes or builds on, plus one;
     * {@link ColumnSample#NO_WATER} if no plan shapes anything there. The noise router's caves and
     * the vanilla carvers keep {@link TerrainModel#WATER_GUARD} blocks and more below it, as they do
     * below a lake, so a plan's pool, channel, outflow and rock never stand on a thin shelf over a
     * tunnel: the plans are made from the model and the 3D terrain alone and cannot see the caves.
     * The lowest top, not the highest: beside a waterfall's face the caves keep below its pool, and
     * so below everything higher up the fall too.
     * <p>
     * A pure function of the model like the plans, so every chunk sees the same; memoised in the
     * sample of the grid corner, because the cave guard asks for every block of a column. It
     * plans only the cells whose reach meets the area, and decides whether a neighbour's plan
     * overrides a cell's only if the cell's plan marks the area, so where no plan can be near a
     * lookup costs little more than laying out the candidates of the cells around. Looking it up
     * may plan cells, which uses the model's per-thread cache: callers must not hold a sample from
     * {@link TerrainModel#sample} across the call.
     */
    public static double guardLevel(TerrainModel model, int x, int z) {
        int bx = x & ~3;
        int bz = z & ~3;
        double memo = model.sample(bx, bz).planGuard;
        if (!Double.isNaN(memo)) {
            return memo;
        }
        int cx = bx >> 2;
        int cz = bz >> 2;
        // the corner is shared by the cells (cx - 1, cz - 1) to (cx, cz); a margin around them
        int i0 = cx - 1 - GUARD_CELLS;
        int j0 = cz - 1 - GUARD_CELLS;
        int i1 = cx + GUARD_CELLS;
        int j1 = cz + GUARD_CELLS;
        int x0 = i0 << 2;
        int z0 = j0 << 2;
        int x1 = (i1 << 2) + 3;
        int z1 = (j1 << 2) + 3;
        FallPlanner planner = of(model);
        int lowest = Integer.MAX_VALUE;
        // the active plans that mark the area's cells (a plan marks only columns its course may
        // shape, which mayMeet looks at); an unfiltered plan that marks none of them, or none
        // lower than another plan does, cannot change the level whether it stays active or not,
        // so only then are the neighbours that could override it looked at
        for (int fx = Math.floorDiv(x0 - FALL_REACH, FALL_CELL); fx <= Math.floorDiv(x1 + FALL_REACH, FALL_CELL); fx++) {
            for (int fz = Math.floorDiv(z0 - FALL_REACH, FALL_CELL); fz <= Math.floorDiv(z1 + FALL_REACH, FALL_CELL); fz++) {
                if (planner.mayMeet(false, fx, fz, x0, z0, x1, z1, true)) {
                    int top = lowestTop(planner.rawFall(fx, fz), i0, j0, i1, j1);
                    if (top < lowest && planner.fall(fx, fz) != null) {
                        lowest = top;
                    }
                }
            }
        }
        for (int fx = Math.floorDiv(x0 - REACH, CASCADE_CELL); fx <= Math.floorDiv(x1 + REACH, CASCADE_CELL); fx++) {
            for (int fz = Math.floorDiv(z0 - REACH, CASCADE_CELL); fz <= Math.floorDiv(z1 + REACH, CASCADE_CELL); fz++) {
                if (planner.mayMeet(true, fx, fz, x0, z0, x1, z1, true)) {
                    int top = lowestTop(planner.rawCascade(fx, fz), i0, j0, i1, j1);
                    if (top < lowest && planner.cascade(fx, fz) != null) {
                        lowest = top;
                    }
                }
            }
        }
        double level = lowest == Integer.MAX_VALUE ? ColumnSample.NO_WATER : lowest;
        // (planning may have evicted the corner's sample: sample it again to keep the memo)
        model.sample(bx, bz).planGuard = level;
        return level;
    }

    /**
     * The lowest top a plan marks in the grid cells {@code (i0, j0)} to {@code (i1, j1)} (see
     * {@link FallPlan#guardTop}), or {@link Integer#MAX_VALUE} if it marks none or is {@code null}.
     */
    private static int lowestTop(FallPlan p, int i0, int j0, int i1, int j1) {
        int lowest = Integer.MAX_VALUE;
        if (p == null || p.maxX < i0 << 2 || p.minX > (i1 << 2) + 3 || p.maxZ < j0 << 2 || p.minZ > (j1 << 2) + 3) {
            return lowest;
        }
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                int top = p.guardTop(i, j);
                if (top != WaterSolver.NONE) {
                    lowest = Math.min(lowest, top);
                }
            }
        }
        return lowest;
    }

    // =========================================================================================
    // reach
    // =========================================================================================

    /**
     * The laid-out candidates of a cell, in the order the planner checks them (see
     * {@link #layFalls}, {@link #layCascades}); its plan is the first that passes the checks. The
     * sampler they were laid out with comes along when the cell is planned at once and is dropped
     * when they are kept for later, since it holds hundreds of samples.
     */
    private record Laid(PlanSampler ps, List<FallPlan> plans) {
    }

    /** Most cells whose laid-out candidates are kept for planning them later. */
    private static final int LAID_KEPT = 256;

    /** The reach of a cell none of whose candidates could become its plan. */
    private static final Footprint[] NO_REACH = new Footprint[0];

    private static Map<Long, Laid> laidCache() {
        return Collections.synchronizedMap(new java.util.LinkedHashMap<>(64, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Laid> eldest) {
                return size() > LAID_KEPT;
            }
        });
    }

    /**
     * What is kept of a laid-out candidate for its cell's reach: its bounds and, for a waterfall,
     * the cells of the coarse grid its course may shape ({@link FallCourse#mask}; cascades shape
     * anything in their bounds).
     */
    private record Footprint(int minX, int minZ, int maxX, int maxZ, boolean[] mask, int maskW) {
        static Footprint of(FallPlan p) {
            return p.course == null ? new Footprint(p.minX, p.minZ, p.maxX, p.maxZ, null, 0)
                : new Footprint(p.minX, p.minZ, p.maxX, p.maxZ, p.course.mask(), p.course.maskWidth());
        }

        /** As {@link FallPlan#meets} ({@code shape}) or a test of the bounds alone. */
        boolean meets(boolean shape, int x0, int z0, int x1, int z1) {
            if (minX > x1 || maxX < x0 || minZ > z1 || maxZ < z0) {
                return false;
            }
            return !shape || mask == null || FallCourse.meets(minX, minZ, maxX, maxZ, mask, maskW, x0, z0, x1, z1);
        }
    }

    /**
     * Whether the plan of a waterfall ({@code cascade} false) or cascade cell, if it has one, may
     * meet the block rectangle {@code (x0, z0)} to {@code (x1, z1)}: always {@code true} if it
     * does. With {@code shape} false this asks about the plan's bounds (as {@link #plansNear}
     * does); with {@code shape} true, about the part of them it may shape ({@link FallPlan#meets}):
     * what it marks for the cave guard, and what a neighbour's plan must meet to overlap it.
     * Once the cell is planned this is the plan itself; before, the cell's reach (see
     * {@link #reach}), so asking does not plan the cell.
     */
    private boolean mayMeet(boolean cascade, int cx, int cz, int x0, int z0, int x1, int z1, boolean shape) {
        long k = key(cx, cz);
        FallPlan raw = (cascade ? rawCascades : rawFalls).get(k);
        if (raw != null) {
            return raw != NONE && (shape ? raw.meets(x0, z0, x1, z1) : raw.minX <= x1 && raw.maxX >= x0 && raw.minZ <= z1 && raw.maxZ >= z0);
        }
        for (Footprint f : reach(cascade, cx, cz)) {
            if (f.meets(shape, x0, z0, x1, z1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The reach of a cell: the footprints of every laid-out candidate that could become its plan.
     * The plan is the first candidate to pass the checks and keeps the bounds and course it was
     * laid out with; a candidate reaching further from its cell than the chunks around would look
     * for it fails the first check, so it is left out.
     */
    private Footprint[] reach(boolean cascade, int cx, int cz) {
        long k = key(cx, cz);
        ConcurrentHashMap<Long, Footprint[]> map = cascade ? cascadeReach : fallReach;
        Footprint[] r = map.get(k);
        if (r != null) {
            return r;
        }
        Laid laid = cascade ? layCascades(cx, cz) : layFalls(cx, cz);
        int size = cascade ? CASCADE_CELL : FALL_CELL;
        int far = cascade ? REACH : FALL_REACH;
        int x0 = cx * size - far;
        int z0 = cz * size - far;
        int x1 = (cx + 1) * size + far;
        int z1 = (cz + 1) * size + far;
        List<Footprint> found = new ArrayList<>();
        if (laid != null) {
            for (FallPlan p : laid.plans) {
                // (the first test of fits() and fitsCascade())
                if (p.minX >= x0 && p.minZ >= z0 && p.maxX <= x1 && p.maxZ <= z1) {
                    found.add(Footprint.of(p));
                }
            }
        }
        r = found.isEmpty() ? NO_REACH : found.toArray(new Footprint[0]);
        if (r.length > 0) {
            (cascade ? laidCascades : laidFalls).put(k, new Laid(null, laid.plans));
        }
        if (map.size() > 16384) {
            map.clear();
        }
        Footprint[] known = map.putIfAbsent(k, r);
        return known != null ? known : r;
    }

    /**
     * The laid-out candidates to plan a cell with: those kept since its reach was asked for (taken,
     * so only one thread checks them), or laid out now; {@code null} if it has none.
     */
    private Laid laid(boolean cascade, int cx, int cz) {
        long k = key(cx, cz);
        if ((cascade ? cascadeReach : fallReach).get(k) == NO_REACH) {
            return null;
        }
        Laid kept = (cascade ? laidCascades : laidFalls).remove(k);
        if (kept != null) {
            return new Laid(new PlanSampler(model), kept.plans);
        }
        return cascade ? layCascades(cx, cz) : layFalls(cx, cz);
    }

    /** Collision-free key of a cell, spread by an odd multiplier so the maps' hashes do not cluster. */
    private static long key(int cx, int cz) {
        return (((long) cx << 32) ^ (cz & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L;
    }

    private static FallPlan cached(ConcurrentHashMap<Long, FallPlan> map, long key, java.util.function.Supplier<FallPlan> compute) {
        FallPlan p = map.get(key);
        if (p == null) {
            p = compute.get();
            if (p == null) {
                p = NONE;
            }
            if (map.size() > 16384) {
                map.clear();
            }
            // two threads may plan the same cell at once: both then use the plan stored first, so
            // one session never builds parts of two different plans of a cell
            FallPlan known = map.putIfAbsent(key, p);
            if (known != null) {
                p = known;
            }
        }
        return p == NONE ? null : p;
    }

    // =========================================================================================
    // active plans
    // =========================================================================================

    /** The waterfall of a cell, or {@code null}. */
    public FallPlan fall(int cx, int cz) {
        return cached(falls, key(cx, cz), () -> {
            FallPlan raw = rawFall(cx, cz);
            if (raw == null) {
                return null;
            }
            for (int dx = -FALL_NEIGHBOURS; dx <= FALL_NEIGHBOURS; dx++) {
                for (int dz = -FALL_NEIGHBOURS; dz <= FALL_NEIGHBOURS; dz++) {
                    // (a neighbour whose plan cannot come within the margin is not planned)
                    if (dx == 0 && dz == 0 || !mayMeet(false, cx + dx, cz + dz, raw.minX - 6, raw.minZ - 6, raw.maxX + 6, raw.maxZ + 6, true)) {
                        continue;
                    }
                    FallPlan o = rawFall(cx + dx, cz + dz);
                    if (o != null && o.overlaps(raw, 6) && beats(o, raw)) {
                        return null;
                    }
                }
            }
            return raw;
        });
    }

    /** The cascade of a cell, or {@code null}. */
    public FallPlan cascade(int cx, int cz) {
        return cached(cascades, key(cx, cz), () -> {
            FallPlan raw = rawCascade(cx, cz);
            if (raw == null) {
                return null;
            }
            for (int fx = Math.floorDiv(raw.minX - FALL_REACH, FALL_CELL); fx <= Math.floorDiv(raw.maxX + FALL_REACH, FALL_CELL); fx++) {
                for (int fz = Math.floorDiv(raw.minZ - FALL_REACH, FALL_CELL); fz <= Math.floorDiv(raw.maxZ + FALL_REACH, FALL_CELL); fz++) {
                    if (!mayMeet(false, fx, fz, raw.minX - 8, raw.minZ - 8, raw.maxX + 8, raw.maxZ + 8, true)) {
                        continue;
                    }
                    FallPlan o = rawFall(fx, fz);
                    if (o != null && o.overlaps(raw, 8)) {
                        return null;
                    }
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 || !mayMeet(true, cx + dx, cz + dz, raw.minX - 6, raw.minZ - 6, raw.maxX + 6, raw.maxZ + 6, true)) {
                        continue;
                    }
                    FallPlan o = rawCascade(cx + dx, cz + dz);
                    if (o != null && o.overlaps(raw, 6) && beats(o, raw)) {
                        return null;
                    }
                }
            }
            return raw;
        });
    }

    private static boolean beats(FallPlan a, FallPlan b) {
        if (a.score != b.score) {
            return a.score > b.score;
        }
        return a.cellX != b.cellX ? a.cellX < b.cellX : a.cellZ < b.cellZ;
    }

    private FallPlan rawFall(int cx, int cz) {
        return cached(rawFalls, key(cx, cz), () -> planFall(cx, cz));
    }

    private FallPlan rawCascade(int cx, int cz) {
        return cached(rawCascades, key(cx, cz), () -> planCascade(cx, cz));
    }

    // =========================================================================================
    // waterfalls
    // =========================================================================================

    /** The eight directions of the coarse candidate scan, {@code d} being {@code 2 d} sixteenths of a turn. */
    private static final int[][] AXES8 = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    /** Gentlest face a waterfall comes down: on gentler slopes water runs in rapids and cascades. */
    private static final double MIN_STEEPNESS = 1.2;
    /** Gentlest face a fall into the sea or a lake comes down. */
    private static final double MIN_STEEPNESS_INTO_WATER = 1.0;
    /** Gentlest face a horsetail slides down: on gentler ones its short steps would read as a creek. */
    private static final double HORSETAIL_STEEPNESS = 1.8;
    /** Smallest natural step (drop over three blocks of run) a plunge leaps from. */
    private static final int PLUNGE_STEP = 10;
    /** Shortest free drop a plunge must have somewhere on its face. */
    static final int PLUNGE_LEAP = 9;
    /** Least the land must rise behind a fall fed by a spring, within 48 blocks of the lip. */
    private static final double MIN_CATCHMENT = 4.0;
    /** Longest face from the lip to the pool, in blocks along the fall. */
    private static final int MAX_RUN = 44;
    /** Highest drop from the lip to the pool. */
    private static final int MAX_DROP = 64;
    /** Ground a waterfall may cut away (see {@link #cutCap}): a base, and so much per block of drop. */
    static final int CUT_BASE = 1400;
    static final int CUT_PER_DROP = 30;
    /** Deepest any column may be cut below the natural surface. */
    static final int MAX_CUT_DEPTH = 10;
    /** Highest any column may be built up above the natural surface. */
    private static final int MAX_FILL = 3;
    /** Highest bank the solver may have to raise beside the water. */
    private static final int MAX_BANK = 2;

    /**
     * Above the highest chance a cell can have to hold a waterfall ({@code (0.08 + 0.34) * 1.3},
     * see {@link #layFalls}), a roll rules the cell out before anything is sampled.
     */
    private static final double MAX_FALL_CHANCE = 0.55;

    /**
     * The waterfall of a cell: the first of its laid-out candidates (see {@link #layFalls}) that
     * fits its surroundings and has a place to watch it from.
     */
    private FallPlan planFall(int cx, int cz) {
        Laid laid = laid(false, cx, cz);
        if (laid == null) {
            return null;
        }
        for (FallPlan p : laid.plans) {
            if (checked(laid.ps, p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Lays out a cell's waterfall candidates, in the order {@link #planFall} checks them, or
     * returns {@code null} if the cell has none. Spots are tried in batches of six, best first: of
     * each batch the best fall that passes the checks, which are costly (every column of the
     * footprint, the view) and so run best first and stop at the first that passes; only if none
     * of a batch passes is the next tried (a stable sort keeps the candidates' order among equal
     * scores). Every batch is laid out here, so the bounds of all of them are known before any is
     * checked (see {@link #reach}).
     */
    private Laid layFalls(int cx, int cz) {
        double roll = SeedMixer.cellUnit(fallSeed, cx, cz, 0);
        if (roll > MAX_FALL_CHANCE) {
            return null;
        }
        PlanSampler ps = new PlanSampler(model);
        int sl = model.seaLevel();
        int x0 = cx * FALL_CELL;
        int z0 = cz * FALL_CELL;
        // a cheap look at the cell first: climate, relief and landforms that make edges
        double humMax = -1.0;
        double hMin = Double.MAX_VALUE;
        double hMax = -Double.MAX_VALUE;
        boolean relief = false;
        boolean edges = false;
        for (int i = 0; i < 5; i++) {
            int px = x0 + FALL_CELL / 2 + (i == 1 ? 80 : i == 2 ? -80 : 0);
            int pz = z0 + FALL_CELL / 2 + (i == 3 ? 80 : i == 4 ? -80 : 0);
            ColumnSample s = ps.corner(px, pz);
            humMax = Math.max(humMax, s.humidity);
            hMin = Math.min(hMin, s.height);
            hMax = Math.max(hMax, s.height);
            relief |= s.cliff > 0.15 || s.steepness > 0.3 || s.canyon > 0.2 || s.coastCliff > 0.2 || s.fjord > 0.2 || s.plateau > 0.2 || s.mountain > 0.25;
            edges |= s.canyon > 0.2 || s.coastCliff > 0.2 || s.fjord > 0.2 || s.plateau > 0.3;
        }
        relief |= hMax - hMin >= 24.0;
        if (humMax < -0.25 || !relief || hMax < sl + 8) {
            return null;
        }
        double chance = (0.08 + 0.34 * smoothstep(-0.1, 0.6, humMax)) * (edges ? 1.3 : 1.0);
        if (roll > chance) {
            return null;
        }
        // candidates on a 16-block grid: high ground, level or rising behind, falling steeply ahead.
        // A coarse look first, in eight directions over the grid's own samples (which every
        // candidate shares), then the best of them in sixteen directions at block resolution
        List<double[]> coarse = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int px = x0 + 8 + 16 * i;
                int pz = z0 + 8 + 16 * j;
                ColumnSample s = ps.corner(px, pz);
                if (s.height < sl + 6 || s.bodyInside || s.swamp > 0.5 || s.volcano > 0.3 || s.humidity < -0.3
                    || s.trailDistance < 8.0) {
                    continue;
                }
                double h0 = s.height;
                // a stream running over the edge makes the most natural fall
                double wet = smoothstep(-0.2, 0.7, s.humidity) * (s.stream > 0.3 ? 1.6 : 1.0);
                for (int d = 0; d < 8; d++) {
                    int dx = AXES8[d][0];
                    int dz = AXES8[d][1];
                    double len = dx != 0 && dz != 0 ? 22.6 : 16.0;
                    double hb = ps.corner(px - 16 * dx, pz - 16 * dz).height;
                    double ha = ps.corner(px + 16 * dx, pz + 16 * dz).height;
                    double ha2 = ps.corner(px + 32 * dx, pz + 32 * dz).height;
                    if ((hb - h0) / len < -0.6 || (hb - h0) / len > 1.4 || (h0 - ha) / len < 0.55) {
                        continue;
                    }
                    double steep = Math.min(5.0, (h0 - ha) / len);
                    coarse.add(new double[]{(h0 - Math.min(ha, ha2)) * steep * (0.5 + wet), px, pz, 2 * d, wet});
                }
            }
        }
        if (coarse.isEmpty()) {
            return null;
        }
        coarse.sort((a, b) -> Double.compare(b[0], a[0]));
        List<double[]> cands = new ArrayList<>();
        for (int c = 0; c < Math.min(16, coarse.size()); c++) {
            double[] k = coarse.get(c);
            int px = (int) k[1];
            int pz = (int) k[2];
            double h0 = ps.height(px, pz);
            for (int d = (int) k[3] - 1; d <= k[3] + 1; d++) {
                int dir = Math.floorMod(d, 16);
                double ax = Math.cos(dir * Math.PI / 8.0);
                double az = Math.sin(dir * Math.PI / 8.0);
                double hb = ps.height((int) Math.round(px - 12 * ax), (int) Math.round(pz - 12 * az));
                double ha = ps.height((int) Math.round(px + 12 * ax), (int) Math.round(pz + 12 * az));
                double ha2 = ps.height((int) Math.round(px + 24 * ax), (int) Math.round(pz + 24 * az));
                if (hb - h0 < -6.0 || hb - h0 > 16.0 || h0 - ha < 9.0) {
                    continue;
                }
                // steep ground right ahead counts most: that is where water falls rather than runs,
                // and a real step (a cliff, a canyon's or a plateau's edge) most of all
                double steep = Math.min(5.0, (h0 - ha) / 12.0);
                double step = 0.0;
                for (int u = -6; u <= 10; u += 2) {
                    double hu = ps.height((int) Math.round(px + u * ax), (int) Math.round(pz + u * az));
                    double hd = ps.height((int) Math.round(px + (u + 4) * ax), (int) Math.round(pz + (u + 4) * az));
                    step = Math.max(step, hu - hd);
                }
                cands.add(new double[]{(h0 - Math.min(ha, ha2)) * steep * (0.5 + k[4]) * (1.0 + 0.06 * Math.max(0.0, step - 6.0)), px, pz, dir});
            }
        }
        if (cands.isEmpty()) {
            return null;
        }
        cands.sort((a, b) -> Double.compare(b[0], a[0]));
        // spots in batches of six, best first (see above)
        List<FallPlan> order = new ArrayList<>();
        List<double[]> done = new ArrayList<>();
        int next = 0;
        for (int batch = 0; batch < 3 && next < cands.size(); batch++) {
            List<FallPlan> laid = new ArrayList<>();
            int tried = 0;
            for (; next < cands.size() && tried < 6; next++) {
                double[] c = cands.get(next);
                boolean near = false;
                for (double[] o : done) {
                    near |= Math.abs(o[1] - c[1]) + Math.abs(o[2] - c[2]) < 32;
                }
                if (near) {
                    continue;
                }
                done.add(c);
                tried++;
                FallPlan p = refineFall(ps, cx, cz, (int) c[1], (int) c[2], (int) c[3]);
                if (p != null) {
                    laid.add(p);
                }
            }
            laid.sort((a, b) -> Double.compare(b.score, a.score));
            order.addAll(laid);
        }
        return order.isEmpty() ? null : new Laid(ps, order);
    }

    /**
     * Fits a waterfall to the slope through {@code (px, pz)} in compass direction {@code dir}
     * (sixteenths of a turn): finds the lip and the foot of the slope, chooses a style the slope
     * suits, lays the course down the face (see {@link FallCourse}) and scores it; what it would
     * build is checked afterwards ({@link #checked}).
     */
    private FallPlan refineFall(PlanSampler ps, int cx, int cz, int px, int pz, int dir) {
        int sl = model.seaLevel();
        double ax = Math.cos(dir * Math.PI / 8.0);
        double az = Math.sin(dir * Math.PI / 8.0);
        // water gathers in the low line of a slope: the axis moves across to the bottom of the
        // hollow ahead (a gully, the head of a valley), so the fall comes down between natural
        // walls instead of over a spur
        int shift = 0;
        double low = Double.MAX_VALUE;
        for (int v = -10; v <= 10; v += 2) {
            double h = 0.25 * Math.abs(v);
            for (int u = 6; u <= 18; u += 6) {
                h += ps.height((int) Math.round(px + u * ax - v * az), (int) Math.round(pz + u * az + v * ax));
            }
            if (h < low) {
                low = h;
                shift = v;
            }
        }
        px = (int) Math.round(px - shift * az);
        pz = (int) Math.round(pz + shift * ax);
        final int o = 40;
        double[] prof = new double[o + 81];
        for (int k = -o; k <= 80; k++) {
            prof[k + o] = ps.height((int) Math.round(px + k * ax), (int) Math.round(pz + k * az));
        }
        // the lip: the steepest edge near the candidate with level or rising ground behind it
        int lip = Integer.MIN_VALUE;
        double bestEdge = 0.0;
        for (int k = -14; k <= 14; k++) {
            double ahead = (prof[k + o] - prof[k + o + 3]) / 3.0;
            double behind = (prof[k + o - 4] - prof[k + o]) / 4.0;
            // the edge of a steep stretch: the ground behind it level or rising gently, not the
            // middle of a long slope
            if (ahead < 0.9 || behind < -0.6 || behind > 1.4) {
                continue;
            }
            double edge = ahead - 0.5 * Math.max(0.0, -behind) - 0.05 * Math.abs(k);
            if (edge > bestEdge) {
                bestEdge = edge;
                lip = k;
            }
        }
        if (lip == Integer.MIN_VALUE) {
            return null;
        }
        int lipX = (int) Math.round(px + lip * ax);
        int lipZ = (int) Math.round(pz + lip * az);
        // the channel's water lies a block below the lowest ground of its last few blocks
        int top = Integer.MAX_VALUE;
        for (int u = 0; u <= 3; u++) {
            top = Math.min(top, ps.ground((int) Math.round(lipX - u * ax), (int) Math.round(lipZ - u * az)) - 1);
        }
        // the foot: where the slope flattens out, or the first water
        int foot = Integer.MIN_VALUE;
        int waterLevel = WaterSolver.NONE;
        // on a long slope that never flattens, the gentlest bench well below the lip: the pool
        // sits there and its stream runs on down the slope in rapids
        int bench = Integer.MIN_VALUE;
        double benchSlope = 0.7;
        for (int k = lip + 1; k <= Math.min(80 - 6, lip + MAX_RUN + 4); k++) {
            int fx = (int) Math.round(px + k * ax);
            int fz = (int) Math.round(pz + k * az);
            double h = prof[k + o];
            ColumnSample c = ps.nearest(fx, fz);
            if (h < sl - 0.5 || c.bodyInside && h < c.bodyLevel - 0.5) {
                foot = k;
                waterLevel = h < sl - 0.5 || !c.bodyInside ? sl : (int) c.bodyLevel;
                break;
            }
            double ahead = (h - prof[k + o + 4]) / 4.0;
            if (h < top - 6 && (ahead < 0.45 || top - h >= MAX_DROP)) {
                foot = k;
                break;
            }
            if (k - lip >= 10 && k - lip <= MAX_RUN && top - h >= 14 && ahead < benchSlope) {
                benchSlope = ahead;
                bench = k;
            }
        }
        if (foot == Integer.MIN_VALUE) {
            foot = bench;
        }
        if (foot == Integer.MIN_VALUE) {
            return null;
        }
        int run = foot - lip;
        double drop = top - (waterLevel != WaterSolver.NONE ? waterLevel - 1 : prof[foot + o]);
        if (drop < 10 || run > MAX_RUN || drop / Math.max(1, run) < (waterLevel != WaterSolver.NONE ? MIN_STEEPNESS_INTO_WATER : MIN_STEEPNESS)) {
            return null;
        }
        double steep = drop / Math.max(1, run);
        // how far the ground six blocks to either side of the face stands above it: a fall comes
        // down a hollow or a cliff, never along a spur, where its water would have to be walled in
        double hollow = 0.0;
        int samples = 0;
        for (int k = lip + 2; k <= foot - 1; k += 3) {
            double kx = px + k * ax;
            double kz = pz + k * az;
            double left = ps.height((int) Math.round(kx - 6 * az), (int) Math.round(kz + 6 * ax));
            double right = ps.height((int) Math.round(kx + 6 * az), (int) Math.round(kz - 6 * ax));
            hollow += 0.5 * (left + right) - prof[k + o];
            samples++;
        }
        hollow /= Math.max(1, samples);
        if (hollow < -1.5) {
            return null;
        }
        ColumnSample lipSample = ps.nearest(lipX, lipZ);
        double wet = smoothstep(-0.2, 0.7, lipSample.humidity);
        long seed = SeedMixer.hash2(fallSeed, cx, cz) + dir;
        FallCourse.Dice dice = new FallCourse.Dice(seed);
        FallCourse course = new FallCourse(model, seed, lipX, lipZ, ax, az);
        // the tallest natural step of the face: its drop over three blocks of run
        int step = 0;
        for (int k = lip; k + 3 <= foot; k++) {
            step = Math.max(step, (int) Math.floor(prof[k + o] - prof[k + 3 + o]));
        }
        // the style the face suits: a plunge only leaps from a real step (a cliff, a canyon's or a
        // plateau's edge), long ledges on gentler faces, a horsetail only on steep ones, where its
        // short steps still fall; wet climates feed wider water, dry ones thin threads
        double r = dice.next();
        int style;
        if (step >= PLUNGE_STEP || steep >= 3.0) {
            style = r < 0.75 ? FallCourse.PLUNGE : FallCourse.TIERED;
        } else if (steep >= HORSETAIL_STEEPNESS) {
            style = r < 0.55 ? FallCourse.TIERED : FallCourse.HORSETAIL;
        } else {
            style = FallCourse.TIERED;
        }
        double hw = 0.5 + wet * (0.6 + 1.6 * dice.next());
        if (style == FallCourse.PLUNGE) {
            // a plunge from a cleft spreads into a broad, ragged column where the climate is wet
            hw += 0.4 + wet * (1.0 + 1.6 * dice.next());
        }
        if (wet > 0.45 && steep >= 2.3 && dice.next() < 0.45) {
            // a long cliff edge in a wet climate: many strands over it, if the edge carries them:
            // a real cliff band, falling steeply all along it (on gentler slopes a wide sheet of
            // water would read as a slide, not a fall)
            double want = 4.0 + 5.0 * dice.next();
            double fan = 0.0;
            for (double w = want; w >= 4.0; w -= 1.0) {
                boolean ok = true;
                for (double v = -w; v <= w && ok; v += 1.0) {
                    int g = ps.ground((int) Math.round(course.worldX(0, v)), (int) Math.round(course.worldZ(0, v)));
                    int front = ps.ground((int) Math.round(course.worldX(4, v)), (int) Math.round(course.worldZ(4, v)));
                    ok = g >= top && g <= top + 5 && front <= top - 6;
                }
                if (ok) {
                    fan = w;
                    break;
                }
            }
            if (fan > 0.0) {
                style = FallCourse.FAN;
                hw = fan;
            }
        }
        course.style = style;
        course.incise = 1 + (dice.next() < 0.6 * wet ? 1 : 0);
        course.channelHalf = style == FallCourse.FAN ? hw : Math.min(hw, 0.7 + 0.8 * dice.next());
        // a fan's water spreads thin over the ledge behind its edge
        course.channelDepth = style == FallCourse.FAN ? 1 : 2;
        course.wallSlope = 1.7 + 0.9 * dice.next();
        course.outWallSlope = 1.2 + 0.6 * dice.next();
        // the feeding stream: joined to a stream of the land's network if one passes close behind
        // the lip at about its level, otherwise 8-16 blocks over level or gently rising ground
        // from a small spring-fed pool
        int link = streamLink(ps, course, top);
        // how far the land rises behind the lip: a spring needs ground above it to feed it, and
        // near a summit or along a crest there is none
        double catchment = -Double.MAX_VALUE;
        for (int u = 16; u <= 48; u += 8) {
            for (int v = -8; v <= 8; v += 8) {
                catchment = Math.max(catchment, ps.height((int) Math.round(course.worldX(-u, v)), (int) Math.round(course.worldZ(-u, v))) - top);
            }
        }
        if (link <= 0 && catchment < MIN_CATCHMENT) {
            return null;
        }
        int channel = link > 0 ? link : 8 + (int) Math.round(8 * SeedMixer.cellUnit(fallSeed, cx, cz, 22));
        double headR = link > 0 ? 0.0 : clamp(2.5 + 0.35 * course.channelHalf, 2.5, 5.0);
        // the channel steps up with the ground behind the lip, its water a block below the ground
        // and at most three blocks higher from one block to the next, cut at most a few blocks; it
        // ends (at its spring) where the ground rises faster than that or falls away
        int reach = channel + (int) Math.ceil(2.0 * headR);
        int[] levels = new int[reach + 1];
        levels[0] = top + 1;
        int ok = 0;
        for (int j = 1; j <= reach; j++) {
            int g = ps.ground((int) Math.round(course.worldX(-j, 0)), (int) Math.round(course.worldZ(-j, 0)));
            int lv = Math.max(levels[j - 1], Math.min(g, levels[j - 1] + 3));
            if (g < lv - 2 || g - (lv - 1) > FallCourse.MAX_TREAD_CUT) {
                break;
            }
            levels[j] = lv;
            ok = j;
        }
        channel = Math.min(channel, ok - (int) Math.ceil(2.0 * headR));
        if (channel < 2 && link <= 0 && ok >= 4) {
            // the ground rises steeply behind the edge: the water breaks out of the rock in a small
            // spring just behind the lip
            headR = 1.6;
            channel = Math.min(2, ok - 4);
        }
        if (channel < 1 || link > 0 && channel < link) {
            return null;
        }
        course.channelLevel = java.util.Arrays.copyOf(levels, channel + 1);
        course.channel = channel;
        course.headR = headR;
        // the outflow may run on until the plan reaches as far from its cell as the chunks it
        // covers look for it, less the banks either side of the stream
        int room = FALL_REACH - FallCourse.WALL_REACH - 6;
        int[] limits = {cx * FALL_CELL - room, cz * FALL_CELL - room, (cx + 1) * FALL_CELL + room, (cz + 1) * FALL_CELL + room};
        if (!course.fit(ps, top, run, waterLevel, hw, dice, limits)) {
            return null;
        }
        if (style == FallCourse.PLUNGE && course.maxRiser() < PLUNGE_LEAP) {
            // no tall free drop: ledges laid down the face instead
            course.style = style = FallCourse.TIERED;
            if (!course.fit(ps, top, run, waterLevel, hw, dice, limits)) {
                return null;
            }
        }
        if (style == FallCourse.FAN) {
            placeOutcrops(course, dice);
        } else if (course.maxHalfWidth() >= 1.4) {
            // broad water hits a rock or two on its way down, splits around it and rejoins below
            int n = (dice.next() < 0.6 ? 1 : 0) + (course.maxHalfWidth() >= 2.5 && dice.next() < 0.5 ? 1 : 0);
            for (int i = 0; i < n; i++) {
                addOutcrop(course, dice, 0.15 + 0.7 * dice.next(), 1.5 + 2.5 * dice.next());
            }
        }
        course.bounds();
        int type = course.intoWater ? FallPlan.SEA : course.outN >= 9 ? FallPlan.GORGE : FallPlan.CURTAIN;
        FallPlan p = new FallPlan(model, type, seed, cx, cz);
        p.course = course;
        p.lipX = lipX;
        p.lipZ = lipZ;
        p.dirX = Math.abs(ax) >= Math.abs(az) ? (ax > 0 ? 1 : -1) : 0;
        p.dirZ = Math.abs(ax) >= Math.abs(az) ? 0 : (az > 0 ? 1 : -1);
        p.top = course.top();
        p.pool = course.pool();
        p.halfWidth = (int) Math.round(style == FallCourse.FAN ? hw : course.maxHalfWidth());
        double t = lipSample.temperature;
        p.material = lipSample.geology == Geology.VOLCANIC ? FallPlan.MAT_VOLCANIC : t < -0.15 ? FallPlan.MAT_COLD : FallPlan.MAT_FOREST;
        p.minX = course.minX;
        p.minZ = course.minZ;
        p.maxX = course.maxX;
        p.maxZ = course.maxZ;
        // steep faces make the finest falls, a tall free drop above all; those fed by a stream or
        // with high ground behind them, whose stream runs on to a river, lake or the sea, and in a
        // hollow between walls of their own look the most natural
        p.score = p.drop() * (2.0 + course.maxHalfWidth()) * (0.5 + wet) * Math.pow(Math.min(5.0, steep), 1.5)
            * (1.0 + 0.06 * course.maxRiser()) * (course.outJoins || course.intoWater ? 2.0 : 1.0)
            * (link > 0 ? 1.4 : 0.7 + 0.3 * smoothstep(MIN_CATCHMENT, 20.0, catchment)) * (1.0 + 0.3 * clamp(hollow, -1.5, 6.0))
            * (1.0 + 0.08 * Math.max(0, step - 6));
        return p;
    }

    /** Whether a laid-out fall fits its surroundings (see {@link #fits}) and has a place to watch it from. */
    private boolean checked(PlanSampler ps, FallPlan p) {
        int fit = fits(ps, p);
        FallCourse c = p.course;
        if (fit == CUT_OFF && c.rocks > 0) {
            if (c.style == FallCourse.FAN) {
                // a rib closes a strand off where the band narrows: the ribs thinner, then every
                // other one; a fan never comes down as one sheet of water without them
                for (int r = 0; r < c.rocks; r++) {
                    c.rockB[r] *= 0.6;
                }
                fit = fits(ps, p);
                if (fit == CUT_OFF && c.rocks >= 4) {
                    int kept = 0;
                    for (int r = 0; r < c.rocks; r += 2) {
                        c.rockU[kept] = c.rockU[r];
                        c.rockV[kept] = c.rockV[r];
                        c.rockA[kept] = c.rockA[r];
                        c.rockB[kept] = c.rockB[r];
                        c.rockRib[kept] = c.rockRib[r];
                        kept++;
                    }
                    c.rocks = kept;
                    fit = fits(ps, p);
                }
            } else {
                // an outcrop closes the water off where the band is narrow: the fall comes down
                // without its outcrops instead
                c.rocks = 0;
                fit = fits(ps, p);
            }
        }
        if (c.style == FallCourse.FAN && c.rocks < 2) {
            return false;
        }
        return fit == FITS && viewpoint(ps, p);
    }

    /**
     * Ribs of a fan: rock standing between strands of different widths along the edge, from just
     * behind the lip down different lengths of the face. Where a rib ends the strands either side
     * of it meet; ribs reaching the foot keep them apart as separate falls into the pool.
     */
    private static void placeOutcrops(FallCourse c, FallCourse.Dice dice) {
        c.rocks = 0;
        double w = c.halfAt(0.6);
        c.ribHalf = w;
        int strands = (int) clamp(Math.round(2.0 * w / 3.5), 3, 6);
        double[] parts = new double[2 * strands - 1];
        double sum = 0.0;
        for (int i = 0; i < parts.length; i++) {
            // strands one to three and a half blocks wide, ribs between one and a half and four
            parts[i] = i % 2 == 0 ? 1.0 + 2.5 * dice.next() : 1.5 + 2.5 * dice.next();
            sum += parts[i];
        }
        double k = 2.0 * w / sum;
        double at = -w;
        for (int i = 0; i < parts.length && c.rocks < c.rockU.length; i++) {
            double size = parts[i] * k;
            if (i % 2 == 1) {
                double top = -1.5 - dice.next();
                // most ribs reach well down the face, some to its foot, so the strands stay apart
                double end = c.faceEnd * (0.75 + 0.45 * dice.next());
                int r = c.rocks++;
                c.rockU[r] = 0.5 * (top + end);
                c.rockA[r] = Math.max(1.5, 0.5 * (end - top));
                c.rockV[r] = at + 0.5 * size;
                c.rockB[r] = Math.max(0.7, 0.5 * size);
                c.rockRib[r] = true;
            }
            at += size;
        }
    }

    /**
     * An outcrop a fraction {@code f} of the way down the face, {@code len} blocks long, inside the
     * water there and never so wide that it closes the water off: the water parts around it and
     * rejoins below.
     */
    private static void addOutcrop(FallCourse c, FallCourse.Dice dice, double f, double len) {
        if (c.rocks >= c.rockU.length) {
            return;
        }
        double u = 0.6 + f * (c.faceEnd - 0.6);
        double w = c.halfAt(u);
        int r = c.rocks++;
        c.rockU[r] = u;
        c.rockV[r] = (dice.next() - 0.5) * 1.6 * Math.max(0.0, w - 1.0);
        c.rockA[r] = 0.5 * len;
        c.rockB[r] = clamp(0.6 + 0.8 * dice.next(), 0.6, Math.max(0.6, w * 0.45));
        c.rockRib[r] = false;
    }

    /**
     * Distance behind the lip (3-28 blocks) at which the centre line of a stream crosses the
     * fall's axis with its water at or above the channel's, or -1.
     */
    private int streamLink(PlanSampler ps, FallCourse c, int top) {
        double[] info = new double[2];
        for (int u = 3; u <= 28; u++) {
            int x = (int) Math.round(c.worldX(-u, 0));
            int z = (int) Math.round(c.worldZ(-u, 0));
            if (ps.nearest(x, z).stream <= 0.05) {
                continue;
            }
            model.streamInfo(x, z, info);
            if (info[0] < 1.5) {
                return info[1] >= top ? u : -1;
            }
        }
        return -1;
    }

    /**
     * Most blocks a waterfall dropping {@code drop} blocks may cut away below the natural surface,
     * channel, ledges, pool, cleft and outflow together: a few dozen per block of drop, spread
     * along its course. A plan needing more would be digging a hole into the slope. The devtools
     * check the built result against it.
     */
    public static int cutCap(int drop) {
        return CUT_BASE + CUT_PER_DROP * drop;
    }

    /**
     * {@link #cutCap(int)} for a planned fall, with the channel of its outflow on top: a stream two
     * or three blocks wide sunk a block or two into the ground, {@value #CUT_PER_OUT_BLOCK} blocks
     * per block of its length, so a stream that runs a long way to the water it joins is not
     * counted as a hole.
     */
    public static int cutCap(FallPlan p) {
        return cutCap(p.drop()) + (p.course == null ? 0 : (int) Math.round(CUT_PER_OUT_BLOCK * p.course.outLength));
    }

    /** Ground the channel of an outflow may cut, per block of its length (see {@link #cutCap(FallPlan)}). */
    static final int CUT_PER_OUT_BLOCK = 10;

    /** What {@link #fits} finds: the plan fits, it does not, or its water is cut off on the way down. */
    private static final int FITS = 0;
    private static final int MISFIT = 1;
    private static final int CUT_OFF = 2;

    /**
     * Whether a waterfall's water runs all the way from its channel down to its pool (or the sea or
     * lake below): followed downhill over the plan's water columns, from every column at or above
     * the lip's level, it must reach the pool's level. A ledge, a knob of high ground or an outcrop
     * closing the band off would leave the fall ending on a ledge above a separate pool.
     */
    private static boolean connected(FallPlan p, int[] level, int w, int h) {
        int goal = p.course.poolLevel;
        boolean[] seen = new boolean[w * h];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int k = 0; k < w * h; k++) {
            if (level[k] != WaterSolver.NONE && level[k] >= p.top + 1) {
                seen[k] = true;
                queue.add(k);
            }
        }
        while (!queue.isEmpty()) {
            int k = queue.poll();
            if (level[k] <= goal) {
                return true;
            }
            int x = k % w;
            int z = k / w;
            for (int d = 0; d < 4; d++) {
                int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
                int nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (nx < 0 || nz < 0 || nx >= w || nz >= h) {
                    continue;
                }
                int n = nz * w + nx;
                if (!seen[n] && level[n] != WaterSolver.NONE && level[n] <= level[k]) {
                    seen[n] = true;
                    queue.add(n);
                }
            }
        }
        return false;
    }

    /**
     * Deepest a column may be cut: {@value #MAX_CUT_DEPTH}, a little deeper under water, and under
     * a plunge's leap as deep as its basin is cut back under the lip.
     */
    static int maxDepth(FallCourse c, int x, int z, int level) {
        if (level == WaterSolver.NONE) {
            return MAX_CUT_DEPTH;
        }
        return c.inLeap(x, z) ? MAX_LEAP_DEPTH : MAX_CUT_DEPTH + 2;
    }

    /** Deepest a column under a plunge's leap may be cut (see {@link FallCourse#MAX_LEAP_CUT}). */
    public static final int MAX_LEAP_DEPTH = FallCourse.MAX_LEAP_CUT + 2;

    /** How far above a carved column the 3D terrain's rock is looked for (see {@link #fits}). */
    private static final int OVERHANG_SCAN = 64;
    /** Widest arc around the foot of a fall in which cut walls six blocks or more may stand. */
    static final int MAX_WALL_DEGREES = 90;

    /**
     * Whether every column cut three blocks or more lies within {@code WALL_REACH + 2} blocks of the
     * plan's own water: a cut further out would be a quarry in the slope, not the side of a cleft.
     * Distances are chamfer distances over the plan's box (1 along an axis, 1.41 diagonally).
     */
    private static boolean cutNearWater(int[] level, int[] top, int[] nat, boolean[] shaped, int w, int h) {
        float[] dist = new float[w * h];
        for (int k = 0; k < w * h; k++) {
            dist[k] = level[k] != WaterSolver.NONE ? 0.0f : Float.MAX_VALUE;
        }
        for (int pass = 0; pass < 2; pass++) {
            int dir = pass == 0 ? 1 : -1;
            for (int j = pass == 0 ? 0 : h - 1; j >= 0 && j < h; j += dir) {
                for (int i = pass == 0 ? 0 : w - 1; i >= 0 && i < w; i += dir) {
                    int k = j * w + i;
                    float d = dist[k];
                    int pj = j - dir;
                    int pi = i - dir;
                    if (pi >= 0 && pi < w) {
                        d = Math.min(d, dist[j * w + pi] + 1.0f);
                    }
                    if (pj >= 0 && pj < h) {
                        d = Math.min(d, dist[pj * w + i] + 1.0f);
                        if (pi >= 0 && pi < w) {
                            d = Math.min(d, dist[pj * w + pi] + 1.4142f);
                        }
                        if (i + dir >= 0 && i + dir < w) {
                            d = Math.min(d, dist[pj * w + i + dir] + 1.4142f);
                        }
                    }
                    dist[k] = d;
                }
            }
        }
        for (int k = 0; k < w * h; k++) {
            if (shaped[k] && level[k] == WaterSolver.NONE && nat[k] - top[k] >= 3 && dist[k] > FallCourse.WALL_REACH + 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * Degrees around the foot of the fall (the middle of the plunge pool, where the falling water
     * lands) in which dry columns cut six blocks or more stand within 20 blocks: rays every ten
     * degrees. Cut walls all around the foot are the amphitheatre of a quarried bowl.
     */
    private static int wallDegrees(FallPlan p, int[] level, int[] top, int[] nat, int w, int h) {
        double[] foot = p.foot();
        double fx = foot[0];
        double fz = foot[1];
        int rays = 0;
        for (int deg = 0; deg < 360; deg += 10) {
            double ca = Math.cos(Math.toRadians(deg));
            double sa = Math.sin(Math.toRadians(deg));
            for (double d = 2.0; d <= 20.0; d += 0.5) {
                int i = (int) Math.floor(fx + ca * d) - p.minX;
                int j = (int) Math.floor(fz + sa * d) - p.minZ;
                if (i < 0 || j < 0 || i >= w || j >= h) {
                    break;
                }
                int k = j * w + i;
                if (level[k] == WaterSolver.NONE && nat[k] - top[k] >= 6) {
                    rays++;
                    break;
                }
            }
        }
        return rays * 10;
    }

    /**
     * Whether the plan fits its surroundings and stays an erosion-sized change: no river, lake or
     * trail inside it (a fall may reach the sea or lake it drops into, and the outflow the water
     * it joins), no rock formation or big overhang near, nothing built up more than a few blocks,
     * no bank beside the water higher than two, no column cut deeper than {@value #MAX_CUT_DEPTH},
     * nothing built over open air in the 3D terrain, no more cut away in all than {@link #cutCap}
     * allows, and water that runs all the way down to the pool ({@link #CUT_OFF} if it does not).
     */
    private int fits(PlanSampler ps, FallPlan p) {
        int cellX = p.cellX * FALL_CELL;
        int cellZ = p.cellZ * FALL_CELL;
        if (p.minX < cellX - FALL_REACH || p.minZ < cellZ - FALL_REACH || p.maxX > cellX + FALL_CELL + FALL_REACH || p.maxZ > cellZ + FALL_CELL + FALL_REACH) {
            // the chunks it would cover could not find it
            return MISFIT;
        }
        int w = p.maxX - p.minX + 1;
        int h = p.maxZ - p.minZ + 1;
        int[] top = new int[w * h];
        int[] nat = new int[w * h];
        int[] level = new int[w * h];
        boolean[] shapedAt = new boolean[w * h];
        WaterPlan.Shape shape = new WaterPlan.Shape();
        FallCourse c = p.course;
        int lastOut = c.outN > 0 ? c.outLevel[c.outN - 1] : WaterSolver.NONE;
        long cut = 0;
        int cap = cutCap(p);
        p.beginGuard();
        for (int z = p.minZ; z <= p.maxZ; z++) {
            for (int x = p.minX; x <= p.maxX; x++) {
                int k = (z - p.minZ) * w + (x - p.minX);
                if (!c.mayShape(x, z)) {
                    // far from every part of the course (beside a long outflow's bounding box)
                    level[k] = WaterSolver.NONE;
                    continue;
                }
                int g = ps.ground(x, z);
                shape.reset(g);
                boolean shaped = p.shape(x, z, g, shape);
                nat[k] = g;
                top[k] = shaped ? shape.ground : g;
                level[k] = shaped ? shape.level : WaterSolver.NONE;
                shapedAt[k] = shaped;
                if (!shaped) {
                    continue;
                }
                p.markGuard(x, z, shape.level, shape.ground, g);
                if (shape.ground < g && (x == p.minX || x == p.maxX || z == p.minZ || z == p.maxZ)) {
                    // a cut reaching the plan's bounds would stop there in a straight step
                    return MISFIT;
                }
                if (shape.level != WaterSolver.NONE) {
                    // the sea or lake below the fall, or the water the outflow runs into
                    boolean foot = c.intoWater && shape.level == c.poolLevel || c.outJoins && shape.level == lastOut;
                    if (ps.bodyWater(x, z) && !foot) {
                        return MISFIT;
                    }
                }
                if (ps.nearest(x, z).trailDistance < 4.0) {
                    return MISFIT;
                }
                if (shape.ground - g > MAX_FILL) {
                    return MISFIT;
                }
                if (shape.ground > g && !ps.solid(x, g - 1, z)) {
                    // rock built up from the model surface where the 3D terrain is open below it
                    // (an overhang's underside, a hollow) would stand over a gap
                    return MISFIT;
                }
                int d = g - shape.ground;
                if (d > 0) {
                    // a pool's basin and a ledge's water count, but only to the depth of the water
                    if (d > maxDepth(c, x, z, shape.level)) {
                        return MISFIT;
                    }
                    cut += d;
                    if (cut > cap) {
                        return MISFIT;
                    }
                }
            }
        }
        // banks: dry ground beside the water may be raised by at most two blocks to hold it
        for (int z = p.minZ + 1; z < p.maxZ; z++) {
            for (int x = p.minX + 1; x < p.maxX; x++) {
                int k = (z - p.minZ) * w + (x - p.minX);
                if (level[k] == WaterSolver.NONE || level[k] <= model.seaLevel()) {
                    continue;
                }
                for (int n : new int[]{k - 1, k + 1, k - w, k + w}) {
                    if (level[n] == WaterSolver.NONE && level[k] - 1 - top[n] > MAX_BANK) {
                        return MISFIT;
                    }
                }
            }
        }
        if (!connected(p, level, w, h)) {
            return CUT_OFF;
        }
        if (!cutNearWater(level, top, nat, shapedAt, w, h) || wallDegrees(p, level, top, nat, w, h) > MAX_WALL_DEGREES) {
            return MISFIT;
        }
        // the 3D terrain's rock standing over the model surface (overhangs, ledges, crags) goes
        // too where the plan carves a column: it counts towards the cut and each column's depth
        for (int z = p.minZ; z <= p.maxZ; z++) {
            for (int x = p.minX; x <= p.maxX; x++) {
                int k = (z - p.minZ) * w + (x - p.minX);
                if (!shapedAt[k]) {
                    continue;
                }
                int above = ps.solidAbove(x, z, Math.max(nat[k], top[k]), OVERHANG_SCAN);
                if (above == 0) {
                    continue;
                }
                if (Math.max(0, nat[k] - top[k]) + above > maxDepth(c, x, z, level[k])) {
                    return MISFIT;
                }
                cut += above;
                if (cut > cap) {
                    return MISFIT;
                }
            }
        }
        // big curved overhangs of the 3D terrain would be cut off at the edge of the cleft
        for (int x = p.minX & ~3; x <= p.maxX; x += 8) {
            for (int z = p.minZ & ~3; z <= p.maxZ; z += 8) {
                if (TerrainDetail.curvedOverhang(model, x, z) != null) {
                    return MISFIT;
                }
            }
        }
        double cell = TerrainModel.FORMATION_CELL;
        int fx = (int) Math.floor(p.lipX / cell);
        int fz = (int) Math.floor(p.lipZ / cell);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                TerrainModel.FormationSite f = model.formationSite(fx + dx, fz + dz);
                if (f != null && Math.abs(f.x - p.lipX) + Math.abs(f.z - p.lipZ) < 110) {
                    return MISFIT;
                }
            }
        }
        return FITS;
    }

    /**
     * A place to watch the fall from: dry ground in front of the pool, beside it, on the far bank
     * of the outflow or beside the lip, chosen by how much of the fall is in plain sight over the
     * shaped ground, preferring a distance from which the whole fall fits the view.
     */
    private boolean viewpoint(PlanSampler ps, FallPlan p) {
        FallCourse c = p.course;
        double mid = c.faceEnd * 0.5;
        p.lookX = (int) Math.floor(c.worldX(mid, c.centreAt(mid)));
        p.lookZ = (int) Math.floor(c.worldZ(mid, c.centreAt(mid)));
        p.lookY = (p.top + p.pool) / 2;
        // targets on the water: the lip, the middle of the face and where it meets the pool
        List<double[]> targets = new ArrayList<>();
        double[] us = {0.6, c.faceEnd * 0.33, c.faceEnd * 0.66, c.faceEnd - 0.5};
        for (int t = 0; t < us.length; t++) {
            for (double side : new double[]{-0.6, 0.0, 0.6}) {
                double v = c.centreAt(us[t]) + side * c.halfAt(us[t]);
                int level = c.level(c.treadAt(us[t], v), us[t]);
                targets.add(new double[]{c.worldX(us[t], v) + 0.5, level - 0.5 + (t == 0 ? 0.5 : 0.0), c.worldZ(us[t], v) + 0.5, t == 3 ? 2 : 1});
            }
        }
        double front = c.intoWater ? c.poolU + c.poolA + 1.0 : c.poolU + c.poolA + 2.0;
        List<double[]> spots = new ArrayList<>();
        for (double du : new double[]{2, 5, 9, 14, 20, 27}) {
            spots.add(new double[]{front + du, c.poolV});
            spots.add(new double[]{front + du, c.poolV + 0.5 * c.poolB + 3.0});
            spots.add(new double[]{front + du, c.poolV - 0.5 * c.poolB - 3.0});
        }
        for (double side : new double[]{3, 7, 12}) {
            spots.add(new double[]{c.poolU, c.poolV + c.poolB + side});
            spots.add(new double[]{c.poolU, c.poolV - c.poolB - side});
        }
        // off to the side, along the slope or cliff the water comes down: the view across the
        // face, and from a fall into the sea the only dry ground near
        double wide = c.maxHalfWidth() + Math.abs(c.poolV);
        for (double u : new double[]{-4.0, c.faceEnd * 0.3, c.faceEnd * 0.6, c.faceEnd + 2.0}) {
            for (double side : new double[]{6, 11, 17, 24}) {
                spots.add(new double[]{u, wide + side});
                spots.add(new double[]{u, -wide - side});
            }
        }
        java.util.HashMap<Long, Integer> grounds = new java.util.HashMap<>();
        WaterPlan.Shape shape = new WaterPlan.Shape();
        double best = -1.0;
        int drop = p.drop();
        for (double[] spot : spots) {
            int x = (int) Math.floor(c.worldX(spot[0], spot[1]));
            int z = (int) Math.floor(c.worldZ(spot[0], spot[1]));
            int g = shaped(ps, p, shape, x, z);
            // (on natural ground above the sea too: rock the plan builds up by the shore is no
            // place to send a player)
            if (shape.level != WaterSolver.NONE && shape.level > g + 1 || Math.min(g, ps.ground(x, z)) < model.seaLevel() || ps.bodyWater(x, z)) {
                continue;
            }
            // a place to stand on gentle ground: on a steep slope the overhangs and ledges of the
            // 3D terrain may stand where the model has open air
            double h0 = ps.height(x, z);
            if (Math.abs(ps.height(x + 3, z) - h0) > 3.5 || Math.abs(ps.height(x - 3, z) - h0) > 3.5 || Math.abs(ps.height(x, z + 3) - h0) > 3.5
                || Math.abs(ps.height(x, z - 3) - h0) > 3.5) {
                continue;
            }
            // the finder sends the player here: open air for feet and head and ground just below
            // in the 3D terrain, unless the plan reshapes the column (it clears everything above)
            if (g == ps.ground(x, z) && (ps.solid(x, g + 1, z) || ps.solid(x, g + 2, z)
                || !ps.solid(x, g, z) && !ps.solid(x, g - 1, z) && !ps.solid(x, g - 2, z))) {
                continue;
            }
            double ex = x + 0.5;
            double ey = g + 2.62;
            double ez = z + 0.5;
            int seen = 0;
            double weight = 0.0;
            for (double[] t : targets) {
                if (visibleCached(ps, p, shape, grounds, ex, ey, ez, t[0], t[1], t[2])) {
                    seen++;
                    weight += t[3];
                }
            }
            double dist = Math.hypot(ex - (p.lookX + 0.5), ez - (p.lookZ + 0.5));
            double score = weight / 15.0 - 0.2 * Math.abs(Math.log(Math.max(4.0, dist) / Math.max(12.0, 1.1 * drop)));
            if (seen * 2 >= targets.size() && score > best) {
                best = score;
                p.viewX = x;
                p.viewY = g + 1;
                p.viewZ = z;
            }
        }
        return best > -1.0;
    }

    /**
     * Whether the straight line between two points passes over the shaped ground, its top block
     * included (a block fills its whole height, so a line through its upper part is blocked), and
     * clear of the 3D terrain's own rock (an overhang, a ledge) where the plan leaves the column
     * as it is: the plan clears everything above the columns it shapes. Columns are looked up once.
     */
    private static boolean visibleCached(PlanSampler ps, FallPlan p, WaterPlan.Shape shape, java.util.Map<Long, Integer> grounds,
                                         double ex, double ey, double ez, double tx, double ty, double tz) {
        double len = Math.sqrt((tx - ex) * (tx - ex) + (ty - ey) * (ty - ey) + (tz - ez) * (tz - ez));
        for (double s = 0.25; s < len - 1.5; s += 0.25) {
            double f = s / len;
            int cx = (int) Math.floor(ex + (tx - ex) * f);
            int cz = (int) Math.floor(ez + (tz - ez) * f);
            double ry = ey + (ty - ey) * f;
            int known = shapedCached(ps, p, shape, grounds, cx, cz);
            int ground = known >> 1;
            if (ground + 1.0 > ry) {
                return false;
            }
            if ((known & 1) == 0 && ry < ground + OVERHANG_SCAN && ps.solid(cx, (int) Math.floor(ry), cz)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the straight line between two points passes above the shaped ground. */
    private static boolean visible(PlanSampler ps, FallPlan p, WaterPlan.Shape shape, double ex, double ey, double ez, double tx, double ty, double tz) {
        double len = Math.sqrt((tx - ex) * (tx - ex) + (ty - ey) * (ty - ey) + (tz - ez) * (tz - ez));
        for (double s = 0.25; s < len - 1.5; s += 0.25) {
            double f = s / len;
            int cx = (int) Math.floor(ex + (tx - ex) * f);
            int cz = (int) Math.floor(ez + (tz - ez) * f);
            if (shaped(ps, p, shape, cx, cz) >= ey + (ty - ey) * f - 0.3) {
                return false;
            }
        }
        return true;
    }

    /**
     * How many of four points on a waterfall's centre line (the lip, a third and two thirds of the
     * way down, the foot) can be seen from its viewpoint, through the shaped ground and the 3D
     * terrain (for the finder's audit).
     */
    public int visibleFromViewpoint(FallPlan p) {
        PlanSampler ps = new PlanSampler(model);
        FallCourse c = p.course;
        WaterPlan.Shape shape = new WaterPlan.Shape();
        java.util.HashMap<Long, Integer> grounds = new java.util.HashMap<>();
        int seen = 0;
        for (double u : new double[]{0.6, c.faceEnd * 0.33, c.faceEnd * 0.66, c.faceEnd - 0.5}) {
            double v = c.centreAt(u);
            int level = c.level(c.treadAt(u, v), u);
            if (visibleCached(ps, p, shape, grounds, p.viewX + 0.5, p.viewY + 1.62, p.viewZ + 0.5, c.worldX(u, v) + 0.5, level - 0.5, c.worldZ(u, v) + 0.5)) {
                seen++;
            }
        }
        return seen;
    }

    /** {@link #shaped} remembered per column, shifted left by one, with the low bit set if the plan shapes it. */
    private static int shapedCached(PlanSampler ps, FallPlan p, WaterPlan.Shape shape, java.util.Map<Long, Integer> grounds, int x, int z) {
        long key = (((long) x << 32) ^ (z & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L;
        Integer known = grounds.get(key);
        if (known != null) {
            return known;
        }
        int g = ps.ground(x, z);
        shape.reset(g);
        boolean shaped = p.shape(x, z, g, shape);
        int r = ((shaped ? shape.ground : g) << 1) | (shaped ? 1 : 0);
        grounds.put(key, r);
        return r;
    }

    /** Ground of a column after the plan shaped it. */
    private static int shaped(PlanSampler ps, FallPlan p, WaterPlan.Shape shape, int x, int z) {
        int g = ps.ground(x, z);
        shape.reset(g);
        return p.shape(x, z, g, shape) ? shape.ground : g;
    }

    // =========================================================================================
    // cascades
    // =========================================================================================

    /** Most pools in one cascade. */
    private static final int MAX_POOLS = 8;
    /** Most points of a cascade's course (two blocks apart). */
    private static final int MAX_COURSE = 46;

    /**
     * Above the highest chance a cell can have to hold a cascade ({@code 0.06 + 0.30}, see
     * {@link #layCascades}), a roll rules the cell out before anything is sampled.
     */
    private static final double MAX_CASCADE_CHANCE = 0.37;

    /**
     * The cascade of a cell: the first of its laid-out attempts (see {@link #layCascades}) that
     * fits its surroundings and has a place to watch it from.
     */
    private FallPlan planCascade(int cx, int cz) {
        Laid laid = laid(true, cx, cz);
        if (laid == null) {
            return null;
        }
        for (FallPlan p : laid.plans) {
            if (fitsCascade(laid.ps, p) && cascadeView(laid.ps, p)) {
                p.score = p.tiers;
                return p;
            }
        }
        return null;
    }

    /**
     * Lays out a cell's cascade attempts, in the order {@link #planCascade} checks them, or returns
     * {@code null} if the cell has none. All eight attempts are laid out, so the bounds of all of
     * them are known before any is checked (see {@link #reach}).
     */
    private Laid layCascades(int cx, int cz) {
        double roll = SeedMixer.cellUnit(cascadeSeed, cx, cz, 0);
        if (roll > MAX_CASCADE_CHANCE) {
            return null;
        }
        PlanSampler ps = new PlanSampler(model);
        int sl = model.seaLevel();
        int x0 = cx * CASCADE_CELL;
        int z0 = cz * CASCADE_CELL;
        ColumnSample centre = ps.corner(x0 + CASCADE_CELL / 2, z0 + CASCADE_CELL / 2);
        double hum = centre.humidity;
        double aridity = smoothstep(0.1, 0.6, centre.baseTemperature) * smoothstep(0.1, -0.5, hum);
        if (hum < 0.05 || centre.temperature < -0.45 || aridity > 0.2 || centre.height < sl + 8 || centre.peakness > 0.7) {
            return null;
        }
        double chance = 0.06 + 0.30 * smoothstep(0.1, 0.6, hum);
        if (roll > chance) {
            return null;
        }
        List<FallPlan> order = new ArrayList<>();
        for (int attempt = 0; attempt < 8; attempt++) {
            int sx = x0 + 24 + (int) ((CASCADE_CELL - 48) * SeedMixer.cellUnit(cascadeSeed, cx, cz, 1 + 2 * attempt));
            int sz = z0 + 24 + (int) ((CASCADE_CELL - 48) * SeedMixer.cellUnit(cascadeSeed, cx, cz, 2 + 2 * attempt));
            FallPlan p = layCascade(ps, cx, cz, sx, sz, attempt);
            if (p != null) {
                order.add(p);
            }
        }
        return order.isEmpty() ? null : new Laid(ps, order);
    }

    /**
     * Lays out a cascade from a spring at {@code (sx, sz)}: its course, pools, rocks and bounds;
     * whether it fits its surroundings is checked afterwards ({@link #planCascade}).
     */
    private FallPlan layCascade(PlanSampler ps, int cx, int cz, int sx, int sz, int attempt) {
        int sl = model.seaLevel();
        ColumnSample s = ps.nearest(sx, sz);
        double h0 = ps.height(sx, sz);
        if (h0 < sl + 6 || s.bodyInside || s.trailDistance < 8.0 || s.swamp > 0.4 || s.cliff > 0.3) {
            return null;
        }
        // a cascade needs a moderate slope: the steepest of eight directions over 24 blocks
        double bestSlope = 0.0;
        for (int d = 0; d < 8; d++) {
            double a = d * Math.PI / 4.0;
            double slope = (h0 - ps.height(sx + (int) Math.round(Math.cos(a) * 24), sz + (int) Math.round(Math.sin(a) * 24))) / 24.0;
            bestSlope = Math.max(bestSlope, slope);
        }
        if (bestSlope < 0.18 || bestSlope > 0.7) {
            return null;
        }
        long seed = SeedMixer.hash2(cascadeSeed, cx, cz) + attempt;
        List<double[]> course = course(ps, seed, sx, sz);
        if (course.size() < 6) {
            return null;
        }
        FallPlan p = new FallPlan(model, FallPlan.CASCADE, seed, cx, cz);
        p.layout = new CascadeLayout(model, course);
        layPools(ps, p, course, seed);
        p.tiers = p.layout.poolCount();
        // a cascade is at least three pools, or two with a tall drop between them
        if (p.tiers < 2 || p.tiers == 2 && p.layout.pools[0].level - p.layout.pools[1].level < 3) {
            return null;
        }
        placeRocks(p, seed);
        // a rock in a lip widens it
        p.layout.prepare();
        double t = ps.nearest(sx, sz).temperature;
        int geology = ps.nearest(sx, sz).geology;
        p.material = t < -0.1 ? FallPlan.MAT_COLD : geology == Geology.LIMESTONE || hum(ps, sx, sz) > 0.35 && t > 0.0 ? FallPlan.MAT_TRAVERTINE : FallPlan.MAT_FOREST;
        cascadeBounds(p);
        p.pool = p.layout.pools[p.tiers - 1].level - 1;
        p.top = p.layout.pools[0].level - 1;
        return p;
    }

    /**
     * The course water takes from a spring down the slope: downhill, but swinging from side to
     * side in bends of varying length and sharpness (sweeping meanders, doglegs, short runs along
     * a ledge across the slope, now and then two bends the same way), wavering a little from one
     * step to the next, and further across the slope where it is steep, but never climbing. It
     * ends where the slope levels out, at water, near a trail, or where it would run back into
     * itself.
     */
    private List<double[]> course(PlanSampler ps, long seed, int sx, int sz) {
        int sl = model.seaLevel();
        List<double[]> pts = new ArrayList<>();
        double x = sx + 0.5;
        double z = sz + 0.5;
        double h = ps.height(sx, sz);
        pts.add(new double[]{x, z, h});
        double swing = 0.0;
        double target = 0.0;
        double legLeft = 0.0;
        double side = unit(seed, 0, 40) < 0.5 ? -1.0 : 1.0;
        int leg = 0;
        int flat = 0;
        while (pts.size() < MAX_COURSE) {
            if (legLeft <= 0.0) {
                // a new bend: mostly to the other side than the last; a sweeping bend, a short
                // run along a ledge, or now and then a nearly straight stretch
                double r = unit(seed, leg, 41);
                double mag;
                if (r < 0.1) {
                    mag = 0.25 * unit(seed, leg, 42);
                    legLeft = 4.0 + 6.0 * unit(seed, leg, 44);
                } else if (r < 0.4) {
                    mag = 1.15 + 0.3 * unit(seed, leg, 42);
                    legLeft = 4.0 + 7.0 * unit(seed, leg, 44);
                } else {
                    mag = 0.55 + 0.55 * unit(seed, leg, 42);
                    legLeft = 8.0 + 12.0 * unit(seed, leg, 44);
                }
                if (unit(seed, leg, 43) < 0.72) {
                    side = -side;
                }
                target = side * mag;
                leg++;
            }
            int ix = (int) Math.floor(x);
            int iz = (int) Math.floor(z);
            double gx = (ps.height(ix + 4, iz) - ps.height(ix - 4, iz)) / 8.0;
            double gz = (ps.height(ix, iz + 4) - ps.height(ix, iz - 4)) / 8.0;
            double slope = Math.hypot(gx, gz);
            if (slope < 0.07) {
                if (++flat >= 2) {
                    break;
                }
            } else {
                flat = 0;
            }
            double down = Math.atan2(-gz, -gx);
            double steep = clamp((slope - 0.45) * 2.0, 0.0, 0.7);
            // the water wavers around rocks and roots from one step to the next
            double waver = 0.4 * (unit(seed, pts.size(), 47) - 0.5);
            double want = target + Math.copySign(steep, target == 0.0 ? side : target) + waver;
            swing = clamp(swing + 0.55 * (want - swing), -1.45, 1.45);
            double nx = x;
            double nz = z;
            double nh = Double.MAX_VALUE;
            for (int tr = 0; tr < 6; tr++) {
                double a = down + swing;
                nx = x + Math.cos(a) * CascadeLayout.COURSE_STEP;
                nz = z + Math.sin(a) * CascadeLayout.COURSE_STEP;
                nh = ps.height((int) Math.floor(nx), (int) Math.floor(nz));
                if (nh <= h + 0.1) {
                    break;
                }
                swing *= 0.6;
            }
            if (nh > h + 0.1) {
                break;
            }
            int jx = (int) Math.floor(nx);
            int jz = (int) Math.floor(nz);
            ColumnSample c = ps.nearest(jx, jz);
            if (ps.bodyWater(jx, jz) || nh < sl + 2 || c.trailDistance < 6.0 || c.swamp > 0.4 || c.cliff > 0.4) {
                break;
            }
            // a bend may come back towards the course above it, but not into it
            boolean back = false;
            for (int j = 0; j < pts.size() - 6 && !back; j++) {
                back = Math.hypot(pts.get(j)[0] - nx, pts.get(j)[1] - nz) < 6.5;
            }
            if (back) {
                break;
            }
            x = nx;
            z = nz;
            h = nh;
            pts.add(new double[]{x, z, h});
            legLeft -= CascadeLayout.COURSE_STEP;
        }
        return pts;
    }

    /**
     * Lays the pools along the course. Each spans a stretch of it down to its lip, which stands
     * one to four blocks above the next pool; the next pool starts just beyond. Pools differ in
     * kind (round, wide, a long narrow run, small) and in outline. A pool that would need a rim
     * more than three blocks above the ground, or whose edge would be cut deep into the slope, is
     * set a block lower or higher or narrowed, or the cascade ends above it.
     */
    private void layPools(PlanSampler ps, FallPlan p, List<double[]> course, long seed) {
        int m = course.size();
        CascadeLayout lay = p.layout;
        int from = 0;
        int prevLevel = Integer.MAX_VALUE;
        double prevLip = 0.0;
        for (int i = 0; i < MAX_POOLS && from < m - 1; i++) {
            CascadeLayout.Pool q = new CascadeLayout.Pool();
            double kind = unit(seed, i, 50);
            double a = unit(seed, i, 51);
            double b = unit(seed, i, 52);
            double c = unit(seed, i, 53);
            int minSeg;
            int maxSeg;
            if (kind < 0.3) {
                // round
                q.wMid = 2.8 + 2.2 * a;
                q.wIn = 1.3 + 0.8 * b;
                q.wOut = 1.2 + 1.0 * c;
                minSeg = 1;
                maxSeg = 3;
            } else if (kind < 0.52) {
                // wide
                q.wMid = 3.6 + 2.0 * a;
                q.wIn = 1.8 + 1.2 * b;
                q.wOut = 1.4 + 1.6 * c;
                minSeg = 2;
                maxSeg = 5;
            } else if (kind < 0.8) {
                // a long narrow run
                q.wMid = 1.4 + 1.0 * a;
                q.wIn = 1.2 + 0.6 * b;
                q.wOut = 1.0 + 0.8 * c;
                minSeg = 3;
                maxSeg = 7;
            } else {
                // small
                q.wMid = 1.8 + 1.0 * a;
                q.wIn = 1.2 + 0.5 * b;
                q.wOut = 1.0 + 0.6 * c;
                minSeg = 1;
                maxSeg = 2;
            }
            int to;
            double drop = drop(seed, i);
            if (i == 0) {
                to = from + minSeg + (int) ((maxSeg - minSeg + 1) * unit(seed, i, 54));
            } else {
                // down to where the ground has dropped by the lip's drop below the water above; on
                // gentle ground the pool stretches further, along the slope as the course swings
                to = from + minSeg;
                while (to < Math.min(m - 1, from + maxSeg + 3) && course.get(to)[2] > prevLevel - drop
                    && (to < from + maxSeg || prevLip - course.get(to)[2] < 1.0)) {
                    to++;
                }
            }
            to = Math.min(to, m - 1);
            if (to <= from) {
                break;
            }
            q.from = from;
            q.to = to;
            // the water stands level with the ground at the lip, or lower where the ground falls
            // less than the drop (the pool is then sunk into the slope)
            int level = (int) Math.ceil(course.get(to)[2]);
            q.level = i == 0 ? level : Math.max(prevLevel - 4, Math.min(prevLevel - (int) drop, level));
            q.depth = 1 + (int) Math.round(1.5 * unit(seed, i, 55)) + (q.wMid > 4.0 ? 1 : 0);
            q.skew = 0.7 * (unit(seed, i, 56) - 0.5);
            q.wobble = 0.06 + 0.12 * unit(seed, i, 57);
            q.phase = 2.0 * Math.PI * unit(seed, i, 58);
            q.lipHalf = Math.max(0.6, q.wOut * (0.45 + 0.4 * unit(seed, i, 59)));
            q.bankA = 0.35 + 0.35 * unit(seed, i, 61);
            q.bankB = 0.05 + 0.08 * unit(seed, i, 62);
            q.apron = 0.8 + 0.6 * unit(seed, i, 63);
            lay.pools = Arrays.copyOf(lay.pools, i + 1);
            lay.pools[i] = q;
            if (!fitPool(ps, p, i, false, i == 0 ? Integer.MAX_VALUE : prevLevel)) {
                lay.pools = Arrays.copyOf(lay.pools, i);
                break;
            }
            prevLevel = q.level;
            prevLip = course.get(to)[2];
            from = to + Math.max(1, (int) Math.round((q.wOut + 1.0) / CascadeLayout.COURSE_STEP));
        }
        // the lowest pool holds its water all round, its lip included
        while (lay.pools.length >= 2 && !fitPool(ps, p, lay.pools.length - 1, true, lay.pools[lay.pools.length - 2].level)) {
            lay.pools = Arrays.copyOf(lay.pools, lay.pools.length - 1);
        }
        lay.prepare();
    }

    /** Drop at a lip: mostly two or three blocks, sometimes one or four. */
    private static double drop(long seed, int i) {
        double u = unit(seed, i, 60);
        return u < 0.15 ? 1.0 : u < 0.5 ? 2.0 : u < 0.82 ? 3.0 : 4.0;
    }

    private static double unit(long seed, int i, int channel) {
        return SeedMixer.unit(SeedMixer.hash2(seed, i, channel));
    }

    /**
     * Fits pool {@code i} to the ground: its rim may stand at most three blocks above the ground
     * and its edge be cut at most four into the slope (deeper, the hollow around it would become a
     * slot). Sets the pool a block lower or higher (its drop below the pool above staying one to
     * four blocks) or narrows it until it fits. The lip is left out unless the pool is the lowest.
     */
    private boolean fitPool(PlanSampler ps, FallPlan p, int i, boolean last, int above) {
        CascadeLayout.Pool q = p.layout.pools[i];
        for (int tries = 0; tries < 6; tries++) {
            p.layout.prepare();
            int[] need = rimNeed(ps, p, i, last);
            if (need[0] <= 3 && need[1] <= 4) {
                return true;
            }
            boolean top = above == Integer.MAX_VALUE;
            if (need[0] > 3 && need[1] < 4 && (top || q.level - 1 >= above - 4)) {
                q.level--;
            } else if (need[1] > 4 && need[0] < 3 && (top || q.level + 1 <= above - 1)) {
                q.level++;
            } else {
                q.wIn = Math.max(1.0, q.wIn * 0.75);
                q.wMid = Math.max(1.2, q.wMid * 0.75);
                q.wOut = Math.max(1.0, q.wOut * 0.8);
                q.lipHalf = Math.min(q.lipHalf, q.wOut);
            }
        }
        return false;
    }

    /**
     * How high pool {@code i}'s rim must be built above the ground and how deep its edge is cut
     * into the slope: the most of each over the columns just beyond its outline, leaving out those
     * held by a pool above and, unless it is the lowest pool, those in front of its lip.
     */
    private static int[] rimNeed(PlanSampler ps, FallPlan p, int i, boolean last) {
        CascadeLayout.Pool q = p.layout.pools[i];
        double reach = CascadeLayout.OUTLINE_NOISE + CascadeLayout.RIM + Math.max(q.wMid, Math.max(q.wIn, q.wOut)) * 1.6;
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (int k = q.from; k <= q.to; k++) {
            x0 = Math.min(x0, (int) Math.floor(p.layout.courseX[k] - reach));
            z0 = Math.min(z0, (int) Math.floor(p.layout.courseZ[k] - reach));
            x1 = Math.max(x1, (int) Math.ceil(p.layout.courseX[k] + reach));
            z1 = Math.max(z1, (int) Math.ceil(p.layout.courseZ[k] + reach));
        }
        int rim = 0;
        int cut = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double px = x + 0.5;
                double pz = z + 0.5;
                // the outline without its noise first, so the noise is only sampled near the outline
                double e0 = p.layout.edge(i, px, pz, 0.0);
                if (e0 < -CascadeLayout.OUTLINE_NOISE || e0 > 1.0 + CascadeLayout.OUTLINE_NOISE) {
                    continue;
                }
                double n = p.layout.outlineNoise(x, z);
                double e = e0 - CascadeLayout.OUTLINE_NOISE * n;
                if (e <= 0.0 || e > 1.0) {
                    continue;
                }
                boolean skip = !last && p.layout.inFront(i, px, pz);
                for (int j = 0; j < i && !skip; j++) {
                    skip = p.layout.edge(j, px, pz, n) <= CascadeLayout.GUARD;
                }
                if (skip) {
                    continue;
                }
                int g = ps.ground(x, z);
                rim = Math.max(rim, q.level - 1 - g);
                cut = Math.max(cut, g - (q.level - 1));
            }
        }
        return new int[]{rim, cut};
    }

    /**
     * Rocks of a cascade: one in some lips, splitting the fall in two; one standing out of some
     * wide pools; and boulders lying on the banks.
     */
    private static void placeRocks(FallPlan p, long seed) {
        List<double[]> stones = new ArrayList<>();
        List<double[]> boulders = new ArrayList<>();
        for (int i = 0; i < p.tiers; i++) {
            CascadeLayout.Pool q = p.layout.pools[i];
            if (i < p.tiers - 1 && q.wOut >= 1.6 && unit(seed, i, 70) < 0.45) {
                // a rock in the lip, with water falling on both sides of it
                double r = 0.6 + 0.5 * unit(seed, i, 71);
                q.lipHalf = Math.min(q.wOut + 0.4, Math.max(q.lipHalf, r + 1.4));
                double v = (unit(seed, i, 72) - 0.5) * Math.max(0.0, q.lipHalf - r - 1.0);
                double[] at = p.layout.courseAt(p.layout.courseS[q.to] + q.wOut - 0.2);
                stones.add(new double[]{at[0] - at[3] * v, at[1] + at[2] * v, r, q.level + (unit(seed, i, 73) < 0.45 ? 1 : 0)});
            }
            double[] mid = p.layout.courseAt(0.5 * (p.layout.courseS[q.from] + p.layout.courseS[q.to]));
            if (q.wMid >= 3.2 && unit(seed, i, 74) < 0.4) {
                // a rock standing out of a wide pool, off its middle
                double side = unit(seed, i, 75) < 0.5 ? -1.0 : 1.0;
                double off = side * (0.25 + 0.35 * unit(seed, i, 76)) * q.wMid;
                double along = (unit(seed, i, 77) - 0.5) * 2.0;
                stones.add(new double[]{mid[0] + mid[2] * along - mid[3] * off, mid[1] + mid[3] * along + mid[2] * off,
                    0.6 + 0.6 * unit(seed, i, 78), q.level + (unit(seed, i, 79) < 0.5 ? 1 : 0)});
            }
            int count = (int) (2.6 * unit(seed, i, 80));
            for (int b = 0; b < count; b++) {
                // a boulder on the bank, just beyond the water
                double t = unit(seed, i * 8 + b, 81);
                double side = unit(seed, i * 8 + b, 82) < 0.5 ? -1.0 : 1.0;
                double s = p.layout.courseS[q.from] + t * (p.layout.courseS[q.to] - p.layout.courseS[q.from]);
                double[] at = p.layout.courseAt(s);
                double w = CascadeLayout.reach(q, t, side) + 0.8 + 2.4 * unit(seed, i * 8 + b, 83);
                boulders.add(new double[]{at[0] - at[3] * side * w, at[1] + at[2] * side * w, 0.9 + 0.9 * unit(seed, i * 8 + b, 84),
                    1 + (unit(seed, i * 8 + b, 85) < 0.4 ? 1 : 0)});
            }
        }
        p.layout.stoneX = new double[stones.size()];
        p.layout.stoneZ = new double[stones.size()];
        p.layout.stoneR = new double[stones.size()];
        p.layout.stoneTop = new int[stones.size()];
        for (int k = 0; k < stones.size(); k++) {
            p.layout.stoneX[k] = stones.get(k)[0];
            p.layout.stoneZ[k] = stones.get(k)[1];
            p.layout.stoneR[k] = stones.get(k)[2];
            p.layout.stoneTop[k] = (int) stones.get(k)[3];
        }
        p.layout.boulderX = new double[boulders.size()];
        p.layout.boulderZ = new double[boulders.size()];
        p.layout.boulderR = new double[boulders.size()];
        p.layout.boulderH = new int[boulders.size()];
        for (int k = 0; k < boulders.size(); k++) {
            p.layout.boulderX[k] = boulders.get(k)[0];
            p.layout.boulderZ[k] = boulders.get(k)[1];
            p.layout.boulderR[k] = boulders.get(k)[2];
            p.layout.boulderH[k] = (int) boulders.get(k)[3];
        }
    }

    /**
     * Where to watch a cascade from: below its lowest pool looking up the chain, or beside a pool,
     * on dry ground; the spot that sees the middles of the most pools over the shaped ground.
     */
    private boolean cascadeView(PlanSampler ps, FallPlan p) {
        int mid = p.tiers / 2;
        double[] c = p.layout.poolMiddle(mid);
        p.lookX = (int) Math.floor(c[0]);
        p.lookZ = (int) Math.floor(c[1]);
        p.lookY = p.layout.pools[mid].level;
        List<double[]> spots = new ArrayList<>();
        CascadeLayout.Pool last = p.layout.pools[p.tiers - 1];
        for (double d : new double[]{7.0, 11.0, 15.0}) {
            for (int side = -1; side <= 1; side++) {
                double u = last.wOut + d;
                spots.add(new double[]{last.lipX + last.lipDx * u - last.lipDz * side * 4.0, last.lipZ + last.lipDz * u + last.lipDx * side * 4.0, 0.5});
            }
        }
        for (int i = 0; i < p.tiers; i++) {
            double[] m = p.layout.poolMiddle(i);
            for (int side = -1; side <= 1; side += 2) {
                for (double d : new double[]{6.0, 10.0}) {
                    double off = side * (p.layout.pools[i].wMid + d);
                    spots.add(new double[]{m[0] - m[3] * off, m[1] + m[2] * off, 0.0});
                }
            }
        }
        WaterPlan.Shape shape = new WaterPlan.Shape();
        // the rays cross the same columns again and again: their shaped ground is kept
        Map<Long, Integer> grounds = new java.util.HashMap<>();
        double best = -1.0;
        for (double[] spot : spots) {
            int ix = (int) Math.floor(spot[0]);
            int iz = (int) Math.floor(spot[1]);
            int g = shaped(ps, p, shape, ix, iz);
            if (shape.level != WaterSolver.NONE || g < model.seaLevel() || ps.bodyWater(ix, iz) || nearRock(p, ix + 0.5, iz + 0.5)) {
                continue;
            }
            int seen = 0;
            for (int t = 0; t < p.tiers; t++) {
                double[] m = p.layout.poolMiddle(t);
                if (sees(ps, p, shape, grounds, ix + 0.5, g + 2.62, iz + 0.5, m[0], p.layout.pools[t].level - 0.5, m[1])) {
                    seen++;
                }
            }
            double score = seen + spot[2];
            if (seen >= 2 && score > best) {
                best = score;
                p.viewX = ix;
                p.viewY = g + 1;
                p.viewZ = iz;
            }
        }
        return best >= 0.0;
    }

    /**
     * Whether the straight line between two points passes above a cascade's shaped ground; the
     * shaped ground of the columns crossed is kept in {@code grounds}, since the lines from the
     * spots tried cross the same columns again and again.
     */
    private static boolean sees(PlanSampler ps, FallPlan p, WaterPlan.Shape shape, Map<Long, Integer> grounds, double ex, double ey, double ez,
                                double tx, double ty, double tz) {
        double len = Math.sqrt((tx - ex) * (tx - ex) + (ty - ey) * (ty - ey) + (tz - ez) * (tz - ez));
        for (double s = 0.25; s < len - 1.5; s += 0.25) {
            double f = s / len;
            int cx = (int) Math.floor(ex + (tx - ex) * f);
            int cz = (int) Math.floor(ez + (tz - ez) * f);
            int g = grounds.computeIfAbsent(((long) cx << 32) ^ (cz & 0xFFFFFFFFL), k -> shaped(ps, p, shape, cx, cz));
            if (g >= ey + (ty - ey) * f - 0.3) {
                return false;
            }
        }
        return true;
    }

    /** Whether a rock or boulder of the cascade stands within a few blocks, blocking the view. */
    private static boolean nearRock(FallPlan p, double x, double z) {
        for (int b = 0; b < p.layout.boulderX.length; b++) {
            if (Math.hypot(x - p.layout.boulderX[b], z - p.layout.boulderZ[b]) < p.layout.boulderR[b] + 3.0) {
                return true;
            }
        }
        for (int s = 0; s < p.layout.stoneX.length; s++) {
            if (Math.hypot(x - p.layout.stoneX[s], z - p.layout.stoneZ[s]) < p.layout.stoneR[s] + 3.0) {
                return true;
            }
        }
        return false;
    }

    private static double hum(PlanSampler ps, int x, int z) {
        return ps.nearest(x, z).humidity;
    }

    /** A cascade's footprint: everything its pools, rims and hollows can reach. */
    private static void cascadeBounds(FallPlan p) {
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < p.tiers; i++) {
            CascadeLayout.Pool q = p.layout.pools[i];
            minX = Math.min(minX, q.minX);
            minZ = Math.min(minZ, q.minZ);
            maxX = Math.max(maxX, q.maxX);
            maxZ = Math.max(maxZ, q.maxZ);
        }
        p.minX = (int) Math.floor(minX);
        p.minZ = (int) Math.floor(minZ);
        p.maxX = (int) Math.ceil(maxX);
        p.maxZ = (int) Math.ceil(maxZ);
    }

    /**
     * Whether a stream (see {@link WaterSolver}) runs within two blocks of a column: a cascade's
     * hollow or rim there would cut the stream off.
     */
    private boolean streamNear(PlanSampler ps, int x, int z, double[] buf) {
        ColumnSample c = ps.nearest(x, z);
        if (c.stream <= 0.0 || c.lake > 0.0) {
            return false;
        }
        model.streamInfo(x, z, buf);
        return buf[0] < 1.25 + 1.5 * c.stream + 2.0;
    }

    /**
     * Whether a cascade fits its surroundings: inside the area chunks search, no river, lake,
     * stream or trail in its footprint, no big curved overhang or rock formation its hollow could
     * cut into, no ground built up more than four blocks or cut more than nine, and no wall of more
     * than four blocks left where the plan shapes the ground (between pools the water falls at
     * most four).
     */
    private boolean fitsCascade(PlanSampler ps, FallPlan p) {
        int cellX = p.cellX * CASCADE_CELL;
        int cellZ = p.cellZ * CASCADE_CELL;
        if (p.minX < cellX - REACH || p.minZ < cellZ - REACH || p.maxX > cellX + CASCADE_CELL + REACH || p.maxZ > cellZ + CASCADE_CELL + REACH) {
            return false;
        }
        for (int x = p.minX & ~3; x <= p.maxX; x += 8) {
            for (int z = p.minZ & ~3; z <= p.maxZ; z += 8) {
                if (TerrainDetail.curvedOverhang(model, x, z) != null) {
                    return false;
                }
            }
        }
        int fc = TerrainModel.FORMATION_CELL;
        for (int fx = Math.floorDiv(p.minX - 40, fc); fx <= Math.floorDiv(p.maxX + 40, fc); fx++) {
            for (int fz = Math.floorDiv(p.minZ - 40, fc); fz <= Math.floorDiv(p.maxZ + 40, fc); fz++) {
                TerrainModel.FormationSite f = model.formationSite(fx, fz);
                if (f != null && f.x >= p.minX - 40 && f.x <= p.maxX + 40 && f.z >= p.minZ - 40 && f.z <= p.maxZ + 40) {
                    return false;
                }
            }
        }
        int w = p.maxX - p.minX + 1;
        int h = p.maxZ - p.minZ + 1;
        int[] surface = new int[w * h];
        int[] natural = new int[w * h];
        boolean[] shaped = new boolean[w * h];
        WaterPlan.Shape shape = new WaterPlan.Shape();
        double[] buf = new double[2];
        p.beginGuard();
        for (int x = p.minX; x <= p.maxX; x++) {
            for (int z = p.minZ; z <= p.maxZ; z++) {
                int k = (z - p.minZ) * w + (x - p.minX);
                int g = ps.ground(x, z);
                natural[k] = g;
                surface[k] = g;
                shape.reset(g);
                if (!p.shape(x, z, g, shape)) {
                    continue;
                }
                p.markGuard(x, z, shape.level, shape.ground, g);
                if (ps.bodyWater(x, z) || ps.nearest(x, z).trailDistance < 4.0 || shape.ground - g > 4 || g - shape.ground > 9 || streamNear(ps, x, z, buf)) {
                    return false;
                }
                shaped[k] = true;
                surface[k] = shape.level != WaterSolver.NONE ? shape.level - 1 : shape.ground;
            }
        }
        int walls = 0;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                int k = z * w + x;
                for (int d = 0; d < 2; d++) {
                    int nx = x + (d == 0 ? 1 : 0);
                    int nz = z + (d == 1 ? 1 : 0);
                    if (nx >= w || nz >= h) {
                        continue;
                    }
                    int n = nz * w + nx;
                    if (!shaped[k] && !shaped[n]) {
                        continue;
                    }
                    int rise = Math.abs(surface[n] - surface[k]);
                    if (rise > 4 && rise > Math.abs(natural[n] - natural[k]) + 1) {
                        walls++;
                    }
                }
            }
        }
        return walls <= 2;
    }
}
