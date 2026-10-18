package com.terrahaul.worldgen.water;

import static com.terrahaul.worldgen.noise.TMath.clamp;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
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
 * <b>Cascades</b> ({@value #CASCADE_CELL}-block cells) follow the steepest descent down a moderate
 * humid slope and lay two to six pools along it, each a few blocks below the last.
 * <p>
 * Plans never overlap: a plan that overlaps a better one in a neighbouring cell is dropped, and a
 * cascade gives way to any waterfall. The decision uses only the unfiltered plans of the cells
 * around, so it never depends on the order in which cells are computed.
 */
public final class FallPlanner {
    public static final int FALL_CELL = 256;
    public static final int CASCADE_CELL = 224;
    /** Furthest any part of a plan reaches from the cell holding its lip or first pool. */
    private static final int REACH = 96;
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

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
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
    private static final int MAX_SLOPE_DROP = 34;

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
                    || s.trailDistance < 8.0 || s.stream > 0.3) {
                    continue;
                }
                double h0 = s.height;
                double wet = smoothstep(-0.2, 0.7, s.humidity);
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
            if (h < top - 6 && ((h - prof[k + o + 4]) / 4.0 < 0.35 || top - h >= MAX_SLOPE_DROP)) {
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
        // wet climates feed wide curtains (up to 25 blocks); in dry ones falls are thin threads
        int hw = (int) Math.min(12, Math.round(wet * (1.0 + 7.0 * unit) + Math.min(3.0, drop / 20.0) + (type == FallPlan.CURTAIN ? 1.5 : 0.0)));
        // the edge must carry the whole curtain: the lip's ground within reach of the water's
        // level and the slope falling away in front of it, all the way across
        int px2 = -dz;
        int pz2 = dx;
        while (hw > 0) {
            boolean ok = true;
            for (int v = -hw; v <= hw && ok; v++) {
                double lipH = ps.height(lipX + v * px2, lipZ + v * pz2);
                double front = ps.height(lipX + v * px2 + 4 * dx, lipZ + v * pz2 + 4 * dz);
                ok = lipH >= top + 0.5 && lipH <= top + 10 && front <= top - 1;
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
        // the feeding stream: up to 16 blocks over level or gently rising ground
        int channel = 8 + (int) Math.round(8 * SeedMixer.cellUnit(fallSeed, cx, cz, 22));
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
        p.ru = clamp(2.5 + 0.10 * drop + 0.2 * hw, 3.0, 13.0);
        p.rv = clamp(hw + 2.5 + 0.05 * drop, 3.0, 16.0);
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

    /** World-space bounds of a waterfall's footprint. */
    private static void bounds(FallPlan p) {
        double shore = 1.0 + p.shoreW / Math.min(p.ru, p.rv);
        int maxU = (int) Math.ceil(Math.max(p.slotEnd + 1, p.uc + p.ru * shore + FallPlan.WALL_REACH));
        int maxV = (int) Math.ceil(Math.max(p.rv * shore + FallPlan.WALL_REACH, Math.max(p.slotHalf + 6 + FallPlan.WALL_REACH, p.halfWidth + 3)));
        int minU = -p.channel - 2;
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
            int seen = 0;
            for (int[] t : targets) {
                double tx = p.lipX + p.dirX - t[0] * p.dirZ + 0.5;
                double tz = p.lipZ + p.dirZ + t[0] * p.dirX + 0.5;
                if (visible(ps, p, shape, ex, ey, ez, tx, t[1] + 0.5, tz)) {
                    seen++;
                }
            }
            double dist = Math.hypot(ex - (p.lookX + 0.5), ez - (p.lookZ + 0.5));
            double score = seen / (double) targets.length - 0.2 * Math.abs(Math.log(Math.max(4.0, dist) / Math.max(10.0, 1.2 * drop)));
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
        // steepest of eight directions over 24 blocks; a cascade needs a moderate slope
        double bestSlope = 0.0;
        double hx = 0.0;
        double hz = 0.0;
        for (int d = 0; d < 8; d++) {
            double a = d * Math.PI / 4.0;
            double slope = (h0 - ps.height(sx + (int) Math.round(Math.cos(a) * 24), sz + (int) Math.round(Math.sin(a) * 24))) / 24.0;
            if (slope > bestSlope) {
                bestSlope = slope;
                hx = Math.cos(a);
                hz = Math.sin(a);
            }
        }
        if (bestSlope < 0.18 || bestSlope > 0.7) {
            return null;
        }
        // follow the steepest descent, turning at most 45 degrees per step
        List<double[]> path = new ArrayList<>();
        double x = sx + 0.5;
        double z = sz + 0.5;
        path.add(new double[]{x, z, ps.height(sx, sz)});
        for (int step = 0; step < 18; step++) {
            double h = ps.height((int) Math.floor(x), (int) Math.floor(z));
            double bestH = Double.MAX_VALUE;
            double bx = 0.0;
            double bz = 0.0;
            for (int t = -2; t <= 2; t++) {
                double a = Math.atan2(hz, hx) + t * Math.PI / 8.0;
                double nx = x + Math.cos(a) * 4.0;
                double nz = z + Math.sin(a) * 4.0;
                double nh = ps.height((int) Math.floor(nx), (int) Math.floor(nz));
                if (nh < bestH) {
                    bestH = nh;
                    bx = Math.cos(a);
                    bz = Math.sin(a);
                }
            }
            if ((h - bestH) / 4.0 < 0.08) {
                break;
            }
            x += bx * 4.0;
            z += bz * 4.0;
            hx = bx;
            hz = bz;
            int ix = (int) Math.floor(x);
            int iz = (int) Math.floor(z);
            if (ps.bodyWater(ix, iz) || bestH < sl + 2) {
                break;
            }
            path.add(new double[]{x, z, bestH});
        }
        if (path.size() < 4) {
            return null;
        }
        // tiers: each pool spans a stretch of the path over which the ground drops 2-5 blocks
        long seed = SeedMixer.hash2(cascadeSeed, cx, cz) + attempt;
        List<double[]> tiers = new ArrayList<>();
        int a = 0;
        int lastLevel = Integer.MAX_VALUE;
        while (a < path.size() - 1 && tiers.size() < 6) {
            double want = 2 + Math.round(3 * SeedMixer.unit(SeedMixer.hash2(seed, tiers.size(), 3)));
            int b = a + 1;
            while (b < path.size() - 1 && (path.get(a)[2] - path.get(b)[2] < want || dist(path.get(a), path.get(b)) < 5.0)
                && dist(path.get(a), path.get(b + 1)) <= 13.0) {
                b++;
            }
            double[] pa = path.get(a);
            double[] pb = path.get(b);
            double len = dist(pa, pb);
            if (len < 4.0) {
                break;
            }
            int level = (int) Math.floor((pa[2] + pb[2]) / 2.0);
            if (level > lastLevel - 2) {
                level = lastLevel - 2;
            }
            double dxn = (pb[0] - pa[0]) / len;
            double dzn = (pb[1] - pa[1]) / len;
            // round pools rather than long troughs
            double across = clamp(Math.max(2.0 + 3.0 * SeedMixer.unit(SeedMixer.hash2(seed, tiers.size(), 5)), 0.6 * (len / 2.0 + 1.5)), 2.0, 8.0);
            int depth = 1 + (int) Math.round(1.5 * SeedMixer.unit(SeedMixer.hash2(seed, tiers.size(), 9)));
            tiers.add(new double[]{(pa[0] + pb[0]) / 2.0, (pa[1] + pb[1]) / 2.0, dxn, dzn, len / 2.0 + 1.5, across, level, depth});
            lastLevel = level;
            a = b;
        }
        if (tiers.size() < 2) {
            return null;
        }
        FallPlan p = new FallPlan(model, FallPlan.CASCADE, seed, cx, cz);
        int n = tiers.size();
        p.tierX = new double[n];
        p.tierZ = new double[n];
        p.tierDx = new double[n];
        p.tierDz = new double[n];
        p.tierA = new double[n];
        p.tierB = new double[n];
        p.tierLevel = new int[n];
        p.tierDepth = new int[n];
        for (int i = 0; i < n; i++) {
            double[] t = tiers.get(i);
            p.tierX[i] = t[0];
            p.tierZ[i] = t[1];
            p.tierDx[i] = t[2];
            p.tierDz[i] = t[3];
            p.tierA[i] = t[4];
            p.tierB[i] = t[5];
            p.tierLevel[i] = (int) t[6];
            p.tierDepth[i] = (int) t[7];
        }
        p.tiers = n;
        // a rim higher than three blocks above the ground would be a wall: lower that pool (keeping
        // the steps between pools) or end the cascade above it
        for (int i = 0; i < p.tiers; i++) {
            int lowered = 0;
            while (rimNeed(ps, p, i) > 3 && lowered < 2 && (i == p.tiers - 1 || p.tierLevel[i] - 1 >= p.tierLevel[i + 1] + 2)) {
                p.tierLevel[i]--;
                lowered++;
            }
            if (rimNeed(ps, p, i) > 3) {
                p.tiers = i;
                break;
            }
        }
        if (p.tiers < 2) {
            return null;
        }
        double t = ps.nearest(sx, sz).temperature;
        int geology = ps.nearest(sx, sz).geology;
        p.material = t < -0.1 ? FallPlan.MAT_COLD : geology == Geology.LIMESTONE || hum(ps, sx, sz) > 0.35 && t > 0.0 ? FallPlan.MAT_TRAVERTINE : FallPlan.MAT_FOREST;
        cascadeBounds(p);
        p.pool = p.tierLevel[p.tiers - 1] - 1;
        p.top = p.tierLevel[0] - 1;
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
     * Where to watch a cascade from: beside or below its pools on dry ground, the spot that sees
     * the most pool centres over the shaped ground.
     */
    private boolean cascadeView(PlanSampler ps, FallPlan p) {
        int mid = p.tiers / 2;
        p.lookX = (int) Math.floor(p.tierX[mid]);
        p.lookZ = (int) Math.floor(p.tierZ[mid]);
        p.lookY = p.tierLevel[mid];
        WaterPlan.Shape shape = new WaterPlan.Shape();
        double best = -1.0;
        int last = p.tiers - 1;
        for (int i = -1; i < p.tiers; i++) {
            for (int side = -1; side <= 1; side++) {
                double x;
                double z;
                if (i < 0) {
                    // below the last pool, looking up the chain
                    double d = p.tierA[last] + 6.0;
                    x = p.tierX[last] + p.tierDx[last] * d - p.tierDz[last] * side * 4.0;
                    z = p.tierZ[last] + p.tierDz[last] * d + p.tierDx[last] * side * 4.0;
                } else {
                    if (side == 0) {
                        continue;
                    }
                    double d = p.tierB[i] + 4.0;
                    x = p.tierX[i] - p.tierDz[i] * side * d;
                    z = p.tierZ[i] + p.tierDx[i] * side * d;
                }
                int ix = (int) Math.floor(x);
                int iz = (int) Math.floor(z);
                int g = shaped(ps, p, shape, ix, iz);
                if (shape.level != WaterSolver.NONE || g < model.seaLevel() || ps.bodyWater(ix, iz)) {
                    continue;
                }
                int seen = 0;
                for (int t = 0; t < p.tiers; t++) {
                    if (visible(ps, p, shape, ix + 0.5, g + 2.62, iz + 0.5, p.tierX[t], p.tierLevel[t] - 0.5, p.tierZ[t])) {
                        seen++;
                    }
                }
                double score = seen + (i < 0 ? 0.5 : 0.0);
                if (seen >= 2 && score > best) {
                    best = score;
                    p.viewX = ix;
                    p.viewY = g + 1;
                    p.viewZ = iz;
                }
            }
        }
        return best >= 0.0;
    }

    private static double hum(PlanSampler ps, int x, int z) {
        return ps.nearest(x, z).humidity;
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    /** Highest a pool's rim must be raised above the ground, over sixteen points around it. */
    private static int rimNeed(PlanSampler ps, FallPlan p, int i) {
        int need = 0;
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8.0;
            double ca = Math.cos(a) * p.tierA[i] * 1.15;
            double cb = Math.sin(a) * p.tierB[i] * 1.15;
            int x = (int) Math.floor(p.tierX[i] + ca * p.tierDx[i] - cb * p.tierDz[i]);
            int z = (int) Math.floor(p.tierZ[i] + ca * p.tierDz[i] + cb * p.tierDx[i]);
            boolean inNext = false;
            for (int j = 0; j < p.tiers; j++) {
                inNext |= j != i && p.tierRadius(j, x, z, 0.0) <= 1.0;
            }
            if (!inNext) {
                need = Math.max(need, p.tierLevel[i] - 1 - ps.ground(x, z));
            }
        }
        return need;
    }

    private static void cascadeBounds(FallPlan p) {
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < p.tiers; i++) {
            double r = Math.max(p.tierA[i], p.tierB[i]) * 1.6 + 3.0;
            minX = Math.min(minX, p.tierX[i] - r);
            minZ = Math.min(minZ, p.tierZ[i] - r);
            maxX = Math.max(maxX, p.tierX[i] + r);
            maxZ = Math.max(maxZ, p.tierZ[i] + r);
        }
        p.minX = (int) Math.floor(minX);
        p.minZ = (int) Math.floor(minZ);
        p.maxX = (int) Math.ceil(maxX);
        p.maxZ = (int) Math.ceil(maxZ);
    }

    private boolean fitsCascade(PlanSampler ps, FallPlan p) {
        WaterPlan.Shape shape = new WaterPlan.Shape();
        for (int x = p.minX; x <= p.maxX; x++) {
            for (int z = p.minZ; z <= p.maxZ; z++) {
                int g = ps.ground(x, z);
                shape.reset(g);
                if (!p.shape(x, z, g, shape)) {
                    continue;
                }
                if (ps.bodyWater(x, z) || ps.nearest(x, z).trailDistance < 4.0 || shape.ground - g > 4 || g - shape.ground > 9) {
                    return false;
                }
            }
        }
        return true;
    }
}
