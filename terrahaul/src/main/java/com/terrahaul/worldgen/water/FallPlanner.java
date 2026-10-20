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
 * level or rising behind, falling steeply ahead. The lip is placed at block resolution, the
 * channel's water one block below the lip's top, and the foot where the slope flattens out or
 * reaches the sea or a lake. The bowl the plan cuts makes any steep slope a free-hanging fall:
 * curtains where the foot is close, falls at the head of a gorge where it is farther. Falls are
 * more frequent and wider in wet climates, and where a canyon, fjord or sea cliff gives the land
 * a sudden edge.
 * <p>
 * <b>Cascades</b> ({@value #CASCADE_CELL}-block cells) follow a winding course down a moderate
 * humid slope, swinging from side to side and running along the slope for a stretch now and
 * then, and lay two to eight pools of different kinds along it, each one to four blocks below the
 * last.
 * <p>
 * Plans never overlap: a plan that overlaps a better one in a neighbouring cell is dropped, and a
 * cascade gives way to any waterfall. The decision uses only the unfiltered plans of the cells
 * around, so it never depends on the order in which cells are computed.
 */
public final class FallPlanner {
    public static final int FALL_CELL = 256;
    public static final int CASCADE_CELL = 224;
    /** Furthest any part of a plan reaches from the cell holding its lip or first pool. */
    private static final int REACH = 120;
    private static final FallPlan NONE = new FallPlan(null, -1, 0L, 0, 0);
    private static final Map<TerrainModel, FallPlanner> PLANNERS = Collections.synchronizedMap(new WeakHashMap<>());

    private final TerrainModel model;
    private final ConcurrentHashMap<Long, FallPlan> rawFalls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, FallPlan> falls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, FallPlan> rawCascades = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, FallPlan> cascades = new ConcurrentHashMap<>();
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

    /** Adds the active plans whose bounds intersect the given block rectangle. */
    public static void plansNear(TerrainModel model, int minX, int minZ, int maxX, int maxZ, List<WaterPlan> out) {
        FallPlanner planner = of(model);
        for (int cx = Math.floorDiv(minX - REACH, FALL_CELL); cx <= Math.floorDiv(maxX + REACH, FALL_CELL); cx++) {
            for (int cz = Math.floorDiv(minZ - REACH, FALL_CELL); cz <= Math.floorDiv(maxZ + REACH, FALL_CELL); cz++) {
                FallPlan p = planner.fall(cx, cz);
                if (p != null && p.minX <= maxX && p.maxX >= minX && p.minZ <= maxZ && p.maxZ >= minZ) {
                    out.add(p);
                }
            }
        }
        for (int cx = Math.floorDiv(minX - REACH, CASCADE_CELL); cx <= Math.floorDiv(maxX + REACH, CASCADE_CELL); cx++) {
            for (int cz = Math.floorDiv(minZ - REACH, CASCADE_CELL); cz <= Math.floorDiv(maxZ + REACH, CASCADE_CELL); cz++) {
                FallPlan p = planner.cascade(cx, cz);
                if (p != null && p.minX <= maxX && p.maxX >= minX && p.minZ <= maxZ && p.maxZ >= minZ) {
                    out.add(p);
                }
            }
        }
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
            map.put(key, p);
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
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
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
            for (int fx = Math.floorDiv(raw.minX - REACH, FALL_CELL); fx <= Math.floorDiv(raw.maxX + REACH, FALL_CELL); fx++) {
                for (int fz = Math.floorDiv(raw.minZ - REACH, FALL_CELL); fz <= Math.floorDiv(raw.maxZ + REACH, FALL_CELL); fz++) {
                    FallPlan o = rawFall(fx, fz);
                    if (o != null && o.overlaps(raw, 8)) {
                        return null;
                    }
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
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

    private static final int[][] AXES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** Highest fall on a slope that is not a cliff (the bowl in front is cut into the slope). */
    private static final int MAX_SLOPE_DROP = 40;

    private FallPlan planFall(int cx, int cz) {
        double roll = SeedMixer.cellUnit(fallSeed, cx, cz, 0);
        if (roll > 0.75) {
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
        // candidates on a 16-block grid: high ground with a steep drop ahead along an axis
        List<double[]> cands = new ArrayList<>();
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
                for (int d = 0; d < 4; d++) {
                    int dx = AXES[d][0];
                    int dz = AXES[d][1];
                    double hb = ps.corner(px - 16 * dx, pz - 16 * dz).height;
                    double ha = ps.corner(px + 16 * dx, pz + 16 * dz).height;
                    double ha2 = ps.corner(px + 32 * dx, pz + 32 * dz).height;
                    if (hb - h0 < -6.0 || hb - h0 > 16.0 || h0 - ha < 10.0) {
                        continue;
                    }
                    cands.add(new double[]{(h0 - Math.min(ha, ha2)) * (0.5 + wet), px, pz, d});
                }
            }
        }
        if (cands.isEmpty()) {
            return null;
        }
        cands.sort((a, b) -> Double.compare(b[0], a[0]));
        FallPlan best = null;
        int tried = 0;
        List<double[]> done = new ArrayList<>();
        for (double[] c : cands) {
            if (tried >= 5) {
                break;
            }
            boolean near = false;
            for (double[] o : done) {
                near |= Math.abs(o[1] - c[1]) + Math.abs(o[2] - c[2]) < 32;
            }
            if (near) {
                continue;
            }
            done.add(c);
            tried++;
            FallPlan p = refineFall(ps, cx, cz, (int) c[1], (int) c[2], AXES[(int) c[3]][0], AXES[(int) c[3]][1]);
            if (p != null && (best == null || p.score > best.score)) {
                best = p;
            }
        }
        return best;
    }

    /** Fits a waterfall to the slope through {@code (px, pz)} in direction {@code (dx, dz)}. */
    private FallPlan refineFall(PlanSampler ps, int cx, int cz, int px, int pz, int dx, int dz) {
        int sl = model.seaLevel();
        final int o = 40;
        double[] prof = new double[o + 81];
        for (int k = -o; k <= 80; k++) {
            prof[k + o] = ps.height(px + k * dx, pz + k * dz);
        }
        // the lip: the steepest edge near the candidate with level or rising ground behind it
        int lip = Integer.MIN_VALUE;
        double bestEdge = 0.0;
        for (int k = -14; k <= 14; k++) {
            double ahead = (prof[k + o] - prof[k + o + 3]) / 3.0;
            double behind = (prof[k + o - 4] - prof[k + o]) / 4.0;
            if (ahead < 1.0 || behind < -0.6) {
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
        int lipX = px + lip * dx;
        int lipZ = pz + lip * dz;
        int top = (int) Math.ceil(prof[lip + o]) - 2;
        // falls on slopes (not cliffs) vary in height from cell to cell
        int slopeDrop = 14 + (int) Math.round((MAX_SLOPE_DROP - 14) * SeedMixer.cellUnit(fallSeed, cx, cz, 24));
        // the foot: where the slope flattens out, or the first water
        int foot = Integer.MIN_VALUE;
        int pool = 0;
        boolean waterFoot = false;
        for (int k = lip + 1; k <= Math.min(80 - 4, lip + 60); k++) {
            int fx = px + k * dx;
            int fz = pz + k * dz;
            double h = prof[k + o];
            ColumnSample c = ps.nearest(fx, fz);
            if (h < sl - 0.5 || c.bodyInside && h < c.bodyLevel - 0.5) {
                waterFoot = true;
                foot = k;
                pool = (h < sl - 0.5 || !c.bodyInside ? sl : (int) c.bodyLevel) - 1;
                break;
            }
            // where the slope flattens out, or on a long slope once the drop is big enough: a fall
            // at the head of a deeper gorge would mean cutting away a whole hillside
            if (h < top - 6 && ((h - prof[k + o + 4]) / 4.0 < 0.35 || top - h >= slopeDrop)) {
                foot = k;
                pool = (int) Math.floor(h) - 1;
                break;
            }
        }
        if (foot == Integer.MIN_VALUE) {
            return null;
        }
        int drop = top - pool;
        int run = foot - lip;
        if (drop < 8 || drop > (waterFoot ? 120 : 70)) {
            return null;
        }
        int type;
        if (waterFoot) {
            if (run > 1.1 * drop + 6) {
                return null;
            }
            type = FallPlan.SEA;
        } else if (run <= 0.35 * drop + 2) {
            type = FallPlan.CURTAIN;
        } else if (run <= 1.1 * drop + 6 && run <= 48) {
            type = FallPlan.GORGE;
        } else {
            return null;
        }
        ColumnSample lipSample = ps.nearest(lipX, lipZ);
        double wet = smoothstep(-0.2, 0.7, lipSample.humidity);
        double unit = SeedMixer.cellUnit(fallSeed, cx, cz, 21);
        long seed = SeedMixer.hash2(fallSeed, cx, cz);
        FallPlan p = new FallPlan(model, type, seed, cx, cz);
        p.lipX = lipX;
        p.lipZ = lipZ;
        p.dirX = dx;
        p.dirZ = dz;
        p.top = top;
        p.pool = pool;
        // wet climates feed wide curtains (up to 29 blocks); in dry ones falls are thin threads
        int hw = (int) Math.min(14, Math.round(wet * (2.0 + 8.0 * unit) + Math.min(4.0, drop / 12.0) + (type == FallPlan.CURTAIN ? 2.0 : 0.0)));
        // the edge must carry the whole curtain: the lip's ground within reach of the water's
        // level (up to two blocks below it the plan builds the lip up) and the slope falling away
        // in front of it, all the way across
        int px2 = -dz;
        int pz2 = dx;
        while (hw > 0) {
            boolean ok = true;
            for (int v = -hw; v <= hw && ok; v++) {
                double lipH = ps.height(lipX + v * px2, lipZ + v * pz2);
                double front = ps.height(lipX + v * px2 + 4 * dx, lipZ + v * pz2 + 4 * dz);
                ok = lipH >= top - 1.5 && lipH <= top + 10 && front <= top - 1;
            }
            if (ok) {
                break;
            }
            hw--;
        }
        if (type == FallPlan.CURTAIN && hw < 1) {
            type = FallPlan.GORGE;
            p = new FallPlan(model, type, seed, cx, cz);
            p.lipX = lipX;
            p.lipZ = lipZ;
            p.dirX = dx;
            p.dirZ = dz;
            p.top = top;
            p.pool = pool;
        }
        p.halfWidth = hw;
        // the feeding stream: joined to a stream of the land's network if one passes close behind
        // the lip at about its level, otherwise 8-16 blocks over level or gently rising ground
        // from a small spring-fed pool
        int link = streamLink(ps, lipX, lipZ, dx, dz, top);
        int channel = link > 0 ? link : 8 + (int) Math.round(8 * SeedMixer.cellUnit(fallSeed, cx, cz, 22));
        for (int u = 1; u <= channel; u++) {
            double h = ps.height(lipX - u * dx, lipZ - u * dz);
            if (h > top + 9 || h < top - 0.5) {
                channel = u - 1;
                break;
            }
        }
        if (channel < 3) {
            return null;
        }
        p.channel = channel;
        p.headR = channel == link ? 0.0 : clamp(2.5 + 0.35 * hw, 2.5, 6.0);
        p.ru = clamp(3.0 + 0.12 * drop + 0.25 * hw, 3.0, 15.0);
        p.rv = clamp(hw + 3.5 + 0.06 * drop, 3.0, 20.0);
        p.uc = 0.5 + p.ru;
        p.depth = (int) clamp(Math.round(2 + drop / 10.0), 2, 7);
        p.shoreW = 2 + (wet > 0.5 ? 1 : 0);
        p.wallSlope = 2.2 + 0.8 * SeedMixer.cellUnit(fallSeed, cx, cz, 23);
        p.slotHalf = Math.max(1.5, hw + 1.0);
        if (type == FallPlan.SEA) {
            p.slotEnd = run;
        } else {
            // the bowl opens into a slot down to where the natural ground is no higher than the
            // pool's shore
            int end = Integer.MIN_VALUE;
            for (int u = (int) Math.ceil(p.uc); u <= 60; u++) {
                if (ps.height(lipX + u * dx, lipZ + u * dz) <= pool + 2) {
                    end = u;
                    break;
                }
            }
            if (end == Integer.MIN_VALUE) {
                return null;
            }
            p.slotEnd = end;
        }
        if (type == FallPlan.CURTAIN && hw >= 3) {
            boolean prev = false;
            for (int v = -hw + 1; v <= hw - 1; v++) {
                boolean rib = !prev && SeedMixer.unit(SeedMixer.hash2(seed, v, 7)) < 0.18;
                p.ribs[v + 32] = rib;
                prev = rib;
            }
        }
        double t = lipSample.temperature;
        p.material = lipSample.geology == Geology.VOLCANIC ? FallPlan.MAT_VOLCANIC : t < -0.15 ? FallPlan.MAT_COLD : FallPlan.MAT_FOREST;
        bounds(p);
        if (!fits(ps, p)) {
            return null;
        }
        if (!viewpoint(ps, p)) {
            return null;
        }
        p.score = drop * (2 * hw + 1) * (0.5 + wet) * (type == FallPlan.CURTAIN ? 1.3 : 1.0);
        return p;
    }

    /**
     * Distance behind the lip (3-28 blocks) at which the centre line of a stream crosses the
     * fall's axis with its water at or above the channel's, or -1.
     */
    private int streamLink(PlanSampler ps, int lipX, int lipZ, int dx, int dz, int top) {
        double[] info = new double[2];
        for (int u = 3; u <= 28; u++) {
            int x = lipX - u * dx;
            int z = lipZ - u * dz;
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

    /** World-space bounds of a waterfall's footprint. */
    private static void bounds(FallPlan p) {
        double shore = 1.0 + p.shoreW / Math.min(p.ru, p.rv);
        int maxU = (int) Math.ceil(Math.max(p.slotEnd + 1, p.uc + p.ru * shore + FallPlan.WALL_REACH));
        double slotWidest = p.slotHalf + 2.0 + 0.2 * Math.max(0, p.slotEnd - p.uc) + 3.0;
        int maxV = (int) Math.ceil(Math.max(p.rv * shore + FallPlan.WALL_REACH, Math.max(slotWidest + FallPlan.WALL_REACH, p.halfWidth + 3)));
        int minU = -p.channel - 2 - (int) Math.ceil(2.0 * p.headR + 2.0);
        int ax = p.lipX + minU * p.dirX;
        int az = p.lipZ + minU * p.dirZ;
        int bx = p.lipX + maxU * p.dirX;
        int bz = p.lipZ + maxU * p.dirZ;
        p.minX = Math.min(ax, bx) - maxV * Math.abs(p.dirZ);
        p.maxX = Math.max(ax, bx) + maxV * Math.abs(p.dirZ);
        p.minZ = Math.min(az, bz) - maxV * Math.abs(p.dirX);
        p.maxZ = Math.max(az, bz) + maxV * Math.abs(p.dirX);
    }

    /**
     * Whether the plan fits its surroundings: no river, lake or trail inside its footprint (a
     * fall into the sea or a lake may reach that water), no rock formation near, and no shore or
     * bank that would have to be built up more than a few blocks.
     */
    private boolean fits(PlanSampler ps, FallPlan p) {
        int cellX = p.cellX * FALL_CELL;
        int cellZ = p.cellZ * FALL_CELL;
        if (p.minX < cellX - REACH || p.minZ < cellZ - REACH || p.maxX > cellX + FALL_CELL + REACH || p.maxZ > cellZ + FALL_CELL + REACH) {
            // the chunks it would cover could not find it
            return false;
        }
        WaterPlan.Shape shape = new WaterPlan.Shape();
        for (int x = p.minX; x <= p.maxX; x += 2) {
            for (int z = p.minZ; z <= p.maxZ; z += 2) {
                int g = ps.ground(x, z);
                shape.reset(g);
                if (!p.shape(x, z, g, shape)) {
                    continue;
                }
                ColumnSample c = ps.nearest(x, z);
                boolean foot = p.type == FallPlan.SEA && p.u(x, z) >= 1 && g <= p.pool - 1;
                if (ps.bodyWater(x, z) && !foot || c.trailDistance < 4.0) {
                    return false;
                }
                if (shape.ground - g > 4) {
                    return false;
                }
                // beyond the pool the bowl and the gorge may only trim the slope, not cut a
                // trench through high ground
                if (p.type != FallPlan.SEA && p.u(x, z) > p.uc + p.ru + p.shoreW + 4 && g - shape.ground > 8 + 0.2 * p.drop()) {
                    return false;
                }
            }
        }
        if (p.type == FallPlan.GORGE) {
            // the gorge opens towards lower ground: its floor never has to be cut through a rise
            int front = (int) Math.ceil(p.uc + p.ru);
            if (p.slotEnd - front > 10 + 0.4 * p.drop()) {
                return false;
            }
            double start = ps.height(p.lipX + front * p.dirX, p.lipZ + front * p.dirZ);
            for (int u = front; u <= p.slotEnd; u++) {
                int off = (int) Math.round(p.slotOffset(u));
                if (ps.height(p.lipX + u * p.dirX - off * p.dirZ, p.lipZ + u * p.dirZ + off * p.dirX) > start + 2.0) {
                    return false;
                }
            }
        }
        // big curved overhangs of the 3D terrain would be cut off at the edge of the bowl
        for (int x = p.minX & ~3; x <= p.maxX; x += 8) {
            for (int z = p.minZ & ~3; z <= p.maxZ; z += 8) {
                if (TerrainDetail.curvedOverhang(model, x, z) != null) {
                    return false;
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
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A place to watch the fall from: dry ground in front of the pool, in the gorge, on the rim of
     * the bowl or beside the lip, chosen by how much of the curtain is in plain sight over the
     * shaped ground, preferring a distance from which the whole fall fits the view.
     */
    private boolean viewpoint(PlanSampler ps, FallPlan p) {
        p.lookX = p.lipX + p.dirX;
        p.lookZ = p.lipZ + p.dirZ;
        p.lookY = (p.top + p.pool) / 2;
        int hw = p.halfWidth;
        int[][] targets = {{-hw, p.pool + 1}, {0, p.pool + 1}, {hw, p.pool + 1}, {-hw, p.lookY}, {0, p.lookY}, {hw, p.lookY},
            {-hw, p.top}, {0, p.top}, {hw, p.top}};
        double front = p.type == FallPlan.SEA ? p.slotEnd + 1 : p.uc + p.ru + p.shoreW;
        List<double[]> spots = new ArrayList<>();
        for (double du : new double[]{2, 5, 9, 14, 20}) {
            spots.add(new double[]{front + du, p.type == FallPlan.GORGE ? p.slotOffset((int) Math.round(front + du)) : 0});
        }
        spots.add(new double[]{front + 3, 0.7 * p.rv});
        spots.add(new double[]{front + 3, -0.7 * p.rv});
        for (double side : new double[]{3, 7, 12}) {
            spots.add(new double[]{p.uc, p.rv + p.shoreW + side});
            spots.add(new double[]{p.uc, -p.rv - p.shoreW - side});
        }
        spots.add(new double[]{-2, hw + 4});
        spots.add(new double[]{-2, -hw - 4});
        WaterPlan.Shape shape = new WaterPlan.Shape();
        double best = -1.0;
        int drop = p.drop();
        for (double[] spot : spots) {
            int x = p.lipX + (int) Math.round(spot[0] * p.dirX - spot[1] * p.dirZ);
            int z = p.lipZ + (int) Math.round(spot[0] * p.dirZ + spot[1] * p.dirX);
            int g = shaped(ps, p, shape, x, z);
            if (shape.level != WaterSolver.NONE && shape.level > g + 1 || g < model.seaLevel() || ps.bodyWater(x, z)) {
                continue;
            }
            double ex = x + 0.5;
            double ey = g + 2.62;
            double ez = z + 0.5;
            // the foot of the fall, where it meets the pool, counts double
            int seen = 0;
            int weight = 0;
            for (int t = 0; t < targets.length; t++) {
                double tx = p.lipX + p.dirX - targets[t][0] * p.dirZ + 0.5;
                double tz = p.lipZ + p.dirZ + targets[t][0] * p.dirX + 0.5;
                if (visible(ps, p, shape, ex, ey, ez, tx, targets[t][1] + 0.5, tz)) {
                    seen++;
                    weight += t < 3 ? 2 : 1;
                }
            }
            double dist = Math.hypot(ex - (p.lookX + 0.5), ez - (p.lookZ + 0.5));
            double score = weight / 12.0 - 0.2 * Math.abs(Math.log(Math.max(4.0, dist) / Math.max(10.0, 1.2 * drop)));
            if (seen * 2 >= targets.length && score > best) {
                best = score;
                p.viewX = x;
                p.viewY = g + 1;
                p.viewZ = z;
            }
        }
        return best > -1.0;
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

    private FallPlan planCascade(int cx, int cz) {
        double roll = SeedMixer.cellUnit(cascadeSeed, cx, cz, 0);
        if (roll > 0.4) {
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
        for (int attempt = 0; attempt < 8; attempt++) {
            int sx = x0 + 24 + (int) ((CASCADE_CELL - 48) * SeedMixer.cellUnit(cascadeSeed, cx, cz, 1 + 2 * attempt));
            int sz = z0 + 24 + (int) ((CASCADE_CELL - 48) * SeedMixer.cellUnit(cascadeSeed, cx, cz, 2 + 2 * attempt));
            FallPlan p = tryCascade(ps, cx, cz, sx, sz, attempt);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    private FallPlan tryCascade(PlanSampler ps, int cx, int cz, int sx, int sz, int attempt) {
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
        if (!fitsCascade(ps, p)) {
            return null;
        }
        if (!cascadeView(ps, p)) {
            return null;
        }
        p.score = p.tiers;
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
