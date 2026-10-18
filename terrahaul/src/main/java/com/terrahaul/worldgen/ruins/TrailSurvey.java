package com.terrahaul.worldgen.ruins;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Surveys one {@value RuinSites#TRAIL_CELL}-block cell of the trail network and plans what stands
 * beside its trails: at most one ruined house, signposts at junctions and trail ends, and at most
 * one stretch of old lamp posts.
 * <ol>
 *   <li>A lattice scan every 8 blocks finds where trails run (most cells are done after this).</li>
 *   <li>Lattice points near a trail are snapped to its centre line and classified by how many
 *       branches cross a ring of radius 14 around them: one is a trail end, three or more a
 *       junction. Candidates are clustered, and clusters are validated with wider rings, which
 *       rejects the false ends and blobs around junction plazas. The scan reaches 48 blocks into
 *       the neighbouring cells, so a junction near a border is seen identically from both sides
 *       and gets exactly one signpost.</li>
 *   <li>A house is tried beside a straight, laid stretch running within 30 degrees of an axis:
 *       its front faces the trail across a small yard, on dry, level ground away from water,
 *       cliffs, rock formations and waterfalls.</li>
 *   <li>Lamp posts follow the trail from the house (or a random stretch) both ways.</li>
 * </ol>
 * Every site lies inside its cell; posts are moved so that each lies wholly inside one chunk.
 */
public final class TrailSurvey {
    static final int CELL = RuinSites.TRAIL_CELL;
    private static final int LATTICE = 8;
    private static final int MARGIN = 48;
    /** Chance that a cell with trails gets a house (if a good spot is found). */
    static final double P_RUIN = 0.6;
    static final double P_JUNCTION_SIGN = 0.7;
    static final double P_END_SIGN = 0.45;
    static final double P_LIGHTS_WITH_RUIN = 0.35;
    static final double P_LIGHTS = 0.07;

    // statistics slots in RuinSites.stats
    static final int ST_CELLS = 0;
    static final int ST_TRAIL_CELLS = 1;
    static final int ST_ATTEMPTS = 2;
    static final int ST_NO_TANGENT = 3;
    static final int ST_NOT_AXIS = 4;
    static final int ST_BIOME = 5;
    static final int ST_ON_TRAIL = 6;
    static final int ST_SLOPE = 7;
    static final int ST_LAND = 8;
    static final int ST_BORDER = 9;
    static final int ST_FEATURE = 10;
    static final int ST_RUINS = 11;
    static final int ST_JUNCTIONS = 12;
    static final int ST_ENDS = 13;
    static final int ST_SIGNS = 14;
    static final int ST_LIGHTS = 15;
    static final int ST_POSTS = 16;
    static final int ST_NANOS = 17;
    static final int ST_MAX_NANOS = 18;
    static final int ST_LAID_POINTS = 19;

    /** What a trail cell holds. */
    public record Cell(TrailRuin ruin, List<Signpost> signposts, LightStretch lights) {
    }

    static final Cell EMPTY = new Cell(null, List.of(), null);

    private final RuinSites sites;
    private final TerrainModel m;
    private final int sl;
    private final int cx;
    private final int cz;
    private final int x0;
    private final int z0;
    private final long seed;

    private TrailSurvey(RuinSites sites, int cx, int cz) {
        this.sites = sites;
        this.m = sites.model;
        this.sl = sites.seaLevel;
        this.cx = cx;
        this.cz = cz;
        this.x0 = cx * CELL;
        this.z0 = cz * CELL;
        this.seed = sites.trailSeed;
    }

    static Cell survey(RuinSites sites, int cx, int cz) {
        long t0 = System.nanoTime();
        Cell cell = new TrailSurvey(sites, cx, cz).run();
        long dt = System.nanoTime() - t0;
        sites.stats.incrementAndGet(ST_CELLS);
        sites.stats.addAndGet(ST_NANOS, dt);
        sites.stats.accumulateAndGet(ST_MAX_NANOS, dt, Math::max);
        return cell;
    }

    private boolean inCell(int x, int z, int margin) {
        return x >= x0 + margin && x < x0 + CELL - margin && z >= z0 + margin && z < z0 + CELL - margin;
    }

    private Cell run() {
        // 1. where do trails run? (lattice points sit at 4 mod 8, the same in every cell)
        List<int[]> points = new ArrayList<>();
        for (int x = x0 + LATTICE / 2; x < x0 + CELL; x += LATTICE) {
            for (int z = z0 + LATTICE / 2; z < z0 + CELL; z += LATTICE) {
                double d = m.trailDistance(x, z);
                if (d < 6.0) {
                    points.add(new int[]{x, z, (int) Math.round(d * 100)});
                }
            }
        }
        if (points.isEmpty()) {
            return EMPTY;
        }
        sites.stats.incrementAndGet(ST_TRAIL_CELLS);
        // the same lattice reaches into the neighbouring cells, so junctions near a border are
        // clustered identically from both sides
        List<int[]> near = new ArrayList<>();
        for (int x = x0 - MARGIN + LATTICE / 2; x < x0 + CELL + MARGIN; x += LATTICE) {
            for (int z = z0 - MARGIN + LATTICE / 2; z < z0 + CELL + MARGIN; z += LATTICE) {
                boolean inside = x >= x0 && x < x0 + CELL && z >= z0 && z < z0 + CELL;
                if (!inside && m.trailDistance(x, z) < 4.0) {
                    near.add(new int[]{x, z});
                }
            }
        }
        for (int[] p : points) {
            if (p[2] < 400) {
                near.add(new int[]{p[0], p[1]});
            }
            if (p[2] < 200 && TrailGeometry.laid(m, p[0], p[1])) {
                sites.stats.incrementAndGet(ST_LAID_POINTS);
            }
        }
        // 2. junctions and trail ends
        List<Node> nodes = nodes(near);
        // 3. a ruined house
        TrailRuin ruin = null;
        if (SeedMixer.cellUnit(seed, cx, cz, 0) < P_RUIN) {
            ruin = ruin(points);
        }
        // 4. signposts
        List<Signpost> signs = new ArrayList<>();
        for (Node n : nodes) {
            if (!inCell(n.x, n.z, 0)) {
                continue;
            }
            double roll = SeedMixer.unit(SeedMixer.hash2(seed ^ 0x516E5L, n.x, n.z));
            if (roll >= (n.junction ? P_JUNCTION_SIGN : P_END_SIGN)) {
                continue;
            }
            Signpost s = signpost(n, ruin, signs);
            if (s != null) {
                signs.add(s);
                sites.stats.incrementAndGet(ST_SIGNS);
            }
        }
        // 5. lamp posts
        LightStretch lights = null;
        if (SeedMixer.cellUnit(seed, cx, cz, 1) < (ruin != null ? P_LIGHTS_WITH_RUIN : P_LIGHTS)) {
            lights = lights(points, ruin, signs, nodes);
        }
        if (ruin == null && signs.isEmpty() && lights == null) {
            return EMPTY;
        }
        return new Cell(ruin, List.copyOf(signs), lights);
    }

    // =========================================================================================
    // Junctions and ends
    // =========================================================================================

    /** A validated junction or trail end; {@code arms} are the directions of its branches. */
    record Node(int x, int z, boolean junction, double[] arms) {
    }

    private record Candidate(int x, int z, boolean junction, double arm) {
    }

    private List<Node> nodes(List<int[]> near) {
        List<Candidate> cands = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int[] p : near) {
            int[] c = TrailGeometry.centre(m, p[0], p[1]);
            if (c == null || !seen.add((long) c[0] << 32 | c[1] & 0xFFFFFFFFL)) {
                continue;
            }
            TrailGeometry.Arcs a = TrailGeometry.ring(m, c[0], c[1], 14.0);
            boolean junction = a.count() >= 3 || a.count() == -1;
            if (!junction && a.count() != 1 || a.count() > 6) {
                continue;
            }
            if (!TrailGeometry.laid(m, c[0], c[1])) {
                continue;
            }
            cands.add(new Candidate(c[0], c[1], junction, a.count() == 1 ? a.mids()[0] : 0.0));
        }
        // cluster candidates closer than 24 blocks (union-find)
        int n = cands.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Candidate a = cands.get(i);
                Candidate b = cands.get(j);
                if (Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z)) <= 24) {
                    parent[find(parent, i)] = find(parent, j);
                }
            }
        }
        List<List<Candidate>> clusters = new ArrayList<>();
        int[] slot = new int[n];
        Arrays.fill(slot, -1);
        for (int i = 0; i < n; i++) {
            int r = find(parent, i);
            if (slot[r] < 0) {
                slot[r] = clusters.size();
                clusters.add(new ArrayList<>());
            }
            clusters.get(slot[r]).add(cands.get(i));
        }
        List<Node> junctions = new ArrayList<>();
        List<Node> ends = new ArrayList<>();
        for (List<Candidate> cl : clusters) {
            boolean junction = cl.stream().anyMatch(Candidate::junction);
            Candidate rep = null;
            if (junction) {
                double sx = 0;
                double sz = 0;
                int k = 0;
                for (Candidate c : cl) {
                    if (c.junction) {
                        sx += c.x;
                        sz += c.z;
                        k++;
                    }
                }
                sx /= k;
                sz /= k;
                double best = Double.MAX_VALUE;
                for (Candidate c : cl) {
                    double d = (c.x - sx) * (c.x - sx) + (c.z - sz) * (c.z - sz);
                    if (c.junction && (d < best || d == best && (c.x < rep.x || c.x == rep.x && c.z < rep.z))) {
                        best = d;
                        rep = c;
                    }
                }
                Node node = validateJunction(rep);
                if (node != null) {
                    junctions.add(node);
                }
            } else {
                // the tip: the candidate furthest out against the direction the trail leaves in
                double best = -Double.MAX_VALUE;
                for (Candidate c : cl) {
                    double proj = -(c.x * Math.cos(c.arm) + c.z * Math.sin(c.arm));
                    if (proj > best || proj == best && (c.x < rep.x || c.x == rep.x && c.z < rep.z)) {
                        best = proj;
                        rep = c;
                    }
                }
                Node node = validateEnd(rep);
                if (node != null) {
                    ends.add(node);
                }
            }
        }
        List<Node> out = new ArrayList<>(junctions);
        for (Node e : ends) {
            boolean clear = true;
            for (Node j : junctions) {
                if (Math.abs(j.x - e.x) < 48 && Math.abs(j.z - e.z) < 48) {
                    clear = false;
                }
            }
            if (clear) {
                out.add(e);
            }
        }
        for (Node node : out) {
            if (inCell(node.x, node.z, 0)) {
                sites.stats.incrementAndGet(node.junction ? ST_JUNCTIONS : ST_ENDS);
            }
        }
        out.sort((a, b) -> a.x != b.x ? Integer.compare(a.x, b.x) : Integer.compare(a.z, b.z));
        return out;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** A junction needs three or more laid branches leaving it, still distinct 24 blocks out. */
    private Node validateJunction(Candidate rep) {
        TrailGeometry.Arcs a = TrailGeometry.ring(m, rep.x, rep.z, 24.0);
        if (a.count() < 3 || a.count() > 5) {
            return null;
        }
        for (int i = 0; i < a.count(); i++) {
            int px = (int) Math.round(rep.x + 24.0 * a.dx(i));
            int pz = (int) Math.round(rep.z + 24.0 * a.dz(i));
            int[] c = TrailGeometry.centre(m, px, pz);
            if (c == null || !TrailGeometry.laid(m, c[0], c[1])) {
                return null;
            }
        }
        return new Node(rep.x, rep.z, true, a.mids());
    }

    /**
     * A trail end needs a single branch at 24 and 36 blocks (not the stub of a plaza), and the
     * trail must stay laid for at least 48 blocks, so short fragments get no sign.
     */
    private Node validateEnd(Candidate rep) {
        TrailGeometry.Arcs a = TrailGeometry.ring(m, rep.x, rep.z, 24.0);
        if (a.count() != 1 || TrailGeometry.ring(m, rep.x, rep.z, 36.0).count() != 1) {
            return null;
        }
        int[] c = TrailGeometry.centre(m, rep.x + 24.0 * a.dx(0), rep.z + 24.0 * a.dz(0));
        if (c == null || !TrailGeometry.laid(m, c[0], c[1])) {
            return null;
        }
        List<double[]> line = TrailGeometry.march(m, c, new double[]{a.dx(0), a.dz(0)}, 8,
            x0 - 2 * CELL, z0 - 2 * CELL, x0 + 3 * CELL, z0 + 3 * CELL);
        if (line.size() < 7) {
            return null;
        }
        return new Node(rep.x, rep.z, false, a.mids());
    }

    // =========================================================================================
    // Houses
    // =========================================================================================

    private TrailRuin ruin(List<int[]> points) {
        List<int[]> cands = new ArrayList<>();
        for (int[] p : points) {
            if (p[2] <= 300 && inCell(p[0], p[1], 24)) {
                cands.add(p);
            }
        }
        if (cands.isEmpty()) {
            return null;
        }
        for (int a = 0; a < 10; a++) {
            int[] p = cands.get((int) (SeedMixer.cellUnit(seed, cx, cz, 10 + a) * cands.size()));
            sites.stats.incrementAndGet(ST_ATTEMPTS);
            int[] c = TrailGeometry.centre(m, p[0], p[1]);
            if (c == null || !TrailGeometry.laid(m, c[0], c[1])) {
                sites.stats.incrementAndGet(ST_NO_TANGENT);
                continue;
            }
            double[] t = TrailGeometry.tangent(m, c[0], c[1]);
            if (t == null) {
                sites.stats.incrementAndGet(ST_NO_TANGENT);
                continue;
            }
            // houses are axis-aligned, so the trail must run close to an axis to face it squarely
            double ang = Math.atan2(t[1], t[0]);
            double off = Math.abs(Math.IEEEremainder(ang, Math.PI / 2.0));
            if (off > Math.toRadians(30)) {
                sites.stats.incrementAndGet(ST_NOT_AXIS);
                continue;
            }
            ColumnSample sc = new ColumnSample();
            sc.copyFrom(m.sample(c[0] & ~3, c[1] & ~3));
            int biome = sites.biome(c[0], (int) sc.height, c[1], sc);
            if (!RuinBiomes.trailVisible(biome)) {
                sites.stats.incrementAndGet(ST_BIOME);
                continue;
            }
            int climate = RuinSites.climateClass(sc, biome, sl);
            int template = pickTemplate(climate, SeedMixer.cellUnit(seed, cx, cz, 20 + a));
            for (int alt = 0; alt < 2; alt++) {
                if (alt == 1) {
                    if (template == HousePlanner.LEAN_TO) {
                        break;
                    }
                    template = HousePlanner.LEAN_TO;
                }
                for (int sideTry = 0; sideTry < 2; sideTry++) {
                    boolean first = SeedMixer.cellUnit(seed, cx, cz, 30 + a) < 0.5;
                    int side = first ^ sideTry == 1 ? 1 : -1;
                    TrailRuin r = place(c, t, side, template, climate, biome, a);
                    if (r != null) {
                        sites.stats.incrementAndGet(ST_RUINS);
                        return r;
                    }
                }
            }
        }
        return null;
    }

    private static int pickTemplate(int climate, double r) {
        int[] types;
        double[] weights;
        switch (climate) {
            case RuinSites.COLD -> {
                types = new int[]{HousePlanner.LOG_CABIN, HousePlanner.BOTHY, HousePlanner.COTTAGE, HousePlanner.CHIMNEY_RUIN,
                    HousePlanner.WATCHTOWER, HousePlanner.LEAN_TO};
                weights = new double[]{40, 18, 12, 12, 8, 10};
            }
            case RuinSites.UPLAND -> {
                types = new int[]{HousePlanner.BOTHY, HousePlanner.LOG_CABIN, HousePlanner.WATCHTOWER, HousePlanner.CHIMNEY_RUIN,
                    HousePlanner.LEAN_TO};
                weights = new double[]{38, 26, 16, 10, 10};
            }
            case RuinSites.HOT_DRY -> {
                types = new int[]{HousePlanner.ADOBE, HousePlanner.LEAN_TO, HousePlanner.WATCHTOWER};
                weights = new double[]{70, 16, 14};
            }
            case RuinSites.HOT_WET -> {
                types = new int[]{HousePlanner.STILT_HUT, HousePlanner.COTTAGE, HousePlanner.LEAN_TO, HousePlanner.WATCHTOWER};
                weights = new double[]{50, 22, 18, 10};
            }
            default -> {
                types = new int[]{HousePlanner.COTTAGE, HousePlanner.FARMSTEAD, HousePlanner.LOG_CABIN, HousePlanner.CHIMNEY_RUIN,
                    HousePlanner.BOTHY, HousePlanner.WATCHTOWER, HousePlanner.LEAN_TO};
                weights = new double[]{30, 14, 14, 10, 8, 8, 8};
            }
        }
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double x = r * total;
        for (int i = 0; i < types.length; i++) {
            x -= weights[i];
            if (x < 0) {
                return types[i];
            }
        }
        return types[types.length - 1];
    }

    /** Tries one house on one side of the trail; returns the site or {@code null}. */
    private TrailRuin place(int[] c, double[] t, int side, int template, int climate, int biome, int a) {
        HousePlanner.Spec spec = HousePlanner.spec(template);
        int vdir = RuinCell.dirOf(-t[1] * side, t[0] * side);
        int vx = RuinCell.DX[vdir];
        int vz = RuinCell.DZ[vdir];
        boolean mirror = SeedMixer.cellUnit(seed, cx, cz, 45 + a) < 0.5;
        int ux = mirror ? -vz : vz;
        int uz = mirror ? vx : -vx;
        // the front wall stands a small yard's width beyond the frayed edge of the path
        int f0 = -1;
        for (int o = 2; o <= 14; o++) {
            if (m.trailDistance(c[0] + vx * o, c[1] + vz * o) >= 4.5) {
                f0 = o;
                break;
            }
        }
        if (f0 < 0) {
            sites.stats.incrementAndGet(ST_ON_TRAIL);
            return null;
        }
        int gap = f0 + 1 + (int) (3.0 * SeedMixer.cellUnit(seed, cx, cz, 40 + a));
        if (template == HousePlanner.LOG_CABIN || template == HousePlanner.STILT_HUT) {
            gap += 2;
        }
        int shift = (int) Math.round((SeedMixer.cellUnit(seed, cx, cz, 50 + a) - 0.5) * 4.0);
        int u0 = spec.doorU() + shift;
        Canvas.Frame f = new Canvas.Frame(c[0] + vx * gap - ux * u0, c[1] + vz * gap - uz * u0, ux, uz, vx, vz);
        // the footpath runs from the door to the trail
        int path = gap;
        for (int k = 1; k <= gap + 4; k++) {
            if (m.trailDistance(f.x(spec.doorU(), -k), f.z(spec.doorU(), -k)) < 2.0) {
                path = k;
                break;
            }
        }
        int minV = Math.min(spec.minV(), -path) - 1;
        int bx0 = Math.min(f.x(spec.minU() - 1, minV), f.x(spec.maxU() + 1, spec.maxV() + 1));
        int bx1 = Math.max(f.x(spec.minU() - 1, minV), f.x(spec.maxU() + 1, spec.maxV() + 1));
        int bz0 = Math.min(f.z(spec.minU() - 1, minV), f.z(spec.maxU() + 1, spec.maxV() + 1));
        int bz1 = Math.max(f.z(spec.minU() - 1, minV), f.z(spec.maxU() + 1, spec.maxV() + 1));
        if (bx0 < x0 + 8 || bx1 >= x0 + CELL - 8 || bz0 < z0 + 8 || bz1 >= z0 + CELL - 8) {
            sites.stats.incrementAndGet(ST_BORDER);
            return null;
        }
        // the house itself: never on the path, on level ground
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        int vBack = spec.d();
        int vFront = template == HousePlanner.LOG_CABIN || template == HousePlanner.STILT_HUT ? -3 : -1;
        for (int u = -1; u <= spec.w(); u++) {
            for (int v = vFront; v <= vBack; v++) {
                int x = f.x(u, v);
                int z = f.z(u, v);
                if (m.trailDistance(x, z) < 4.0) {
                    sites.stats.incrementAndGet(ST_ON_TRAIL);
                    return null;
                }
                if ((u & 1) == 0 && (v & 1) == 0 || u == spec.w() || v == vBack) {
                    double h = m.interpolatedHeight(x, z);
                    lo = Math.min(lo, h);
                    hi = Math.max(hi, h);
                }
            }
        }
        if (hi - lo > spec.maxRange()) {
            sites.stats.incrementAndGet(ST_SLOPE);
            return null;
        }
        // the yard: dry land, off the path except for the footpath, no steps or drops
        for (int u = spec.minU(); u <= spec.maxU(); u += 3) {
            for (int v = spec.minV(); v <= spec.maxV(); v += 3) {
                int x = f.x(u, v);
                int z = f.z(u, v);
                if (!TrailGeometry.land(m.sample(x & ~3, z & ~3), sl)) {
                    sites.stats.incrementAndGet(ST_LAND);
                    return null;
                }
                boolean pathStrip = v < 0 && Math.abs(u - spec.doorU()) <= 1;
                if (!pathStrip && m.trailDistance(x, z) < 3.0) {
                    sites.stats.incrementAndGet(ST_ON_TRAIL);
                    return null;
                }
                if (Math.abs(m.interpolatedHeight(x, z) - (lo + hi) * 0.5) > spec.maxRange() + 2.5) {
                    sites.stats.incrementAndGet(ST_SLOPE);
                    return null;
                }
            }
        }
        int hx = f.x(spec.w() / 2, spec.d() / 2);
        int hz = f.z(spec.w() / 2, spec.d() / 2);
        if (sites.formationNear(hx, hz, 100, 0) || sites.waterfallNear(hx, hz, 56)) {
            sites.stats.incrementAndGet(ST_FEATURE);
            return null;
        }
        ColumnSample hs = new ColumnSample();
        hs.copyFrom(m.sample(hx & ~3, hz & ~3));
        int houseBiome = sites.biome(hx, (int) hs.height, hz, hs);
        if (!RuinBiomes.trailVisible(houseBiome)) {
            sites.stats.incrementAndGet(ST_BIOME);
            return null;
        }
        // floor course: the median of the ground under the body
        double[] hs9 = new double[9];
        int k = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                hs9[k++] = m.interpolatedHeight(f.x(i * (spec.w() - 1) / 2, j * (spec.d() - 1) / 2), f.z(i * (spec.w() - 1) / 2, j * (spec.d() - 1) / 2));
            }
        }
        Arrays.sort(hs9);
        int floorY = (int) Math.floor(hs9[4]);
        long siteSeed = SeedMixer.hash2(seed, cx, cz);
        RuinMaterials mat = RuinSites.houseMaterials(climate, houseBiome, hs, template, siteSeed);
        double u = SeedMixer.unit(SeedMixer.mix(siteSeed ^ 0xDECA1L));
        double decay = Math.max(0.2, Math.min(0.92, 0.25 + 0.65 * u + 0.08 * hs.humidity));
        double wet = Math.max(0.0, Math.min(1.0, (hs.humidity + 0.6) / 1.2));
        if (climate == RuinSites.HOT_WET) {
            wet = Math.max(wet, 0.7);
        } else if (climate == RuinSites.HOT_DRY) {
            wet = Math.min(wet, 0.25);
        }
        String name = RuinNames.name(siteSeed);
        String loot = RuinSites.lootTable(climate, houseBiome, template, mat);
        return new TrailRuin(hx, floorY + 1, hz, siteSeed, bx0, bz0, bx1, bz1, template, climate, mat, f, floorY, decay, wet, name,
            c[0], c[1], loot, path);
    }

    // =========================================================================================
    // Signposts
    // =========================================================================================

    /** Moves a column by at most two blocks so that it lies at local x and z in [lo, hi] of its chunk. */
    static int[] own(int x, int z, int lo, int hi) {
        int lx = x & 15;
        int lz = z & 15;
        int dx = lx < lo ? lo - lx : lx > hi ? hi - lx : 0;
        int dz = lz < lo ? lo - lz : lz > hi ? hi - lz : 0;
        return new int[]{x + dx, z + dz};
    }

    private boolean nearBox(RuinSite s, int x, int z, int pad) {
        return s != null && x >= s.minX - pad && x <= s.maxX + pad && z >= s.minZ - pad && z <= s.maxZ + pad;
    }

    private Signpost signpost(Node n, TrailRuin ruin, List<Signpost> others) {
        int px;
        int pz;
        if (n.junction) {
            // stand in the widest gap between the branches, just off the junction
            double[] arms = n.arms.clone();
            Arrays.sort(arms);
            double bestGap = -1;
            double bisector = 0;
            for (int i = 0; i < arms.length; i++) {
                double a0 = arms[i];
                double a1 = i + 1 < arms.length ? arms[i + 1] : arms[0] + Math.PI * 2.0;
                if (a1 - a0 > bestGap) {
                    bestGap = a1 - a0;
                    bisector = (a0 + a1) * 0.5;
                }
            }
            int[] q = null;
            for (int d = 3; d <= 20; d++) {
                int x = (int) Math.round(n.x + d * Math.cos(bisector));
                int z = (int) Math.round(n.z + d * Math.sin(bisector));
                if (m.trailDistance(x, z) >= 3.2) {
                    q = new int[]{x, z};
                    break;
                }
            }
            if (q == null) {
                return null;
            }
            px = q[0];
            pz = q[1];
        } else {
            // in line with the path, just past its end, facing walkers who arrive along it
            double ax = Math.cos(n.arms[0]);
            double az = Math.sin(n.arms[0]);
            int[] q = null;
            for (int o = 2; o <= 6; o++) {
                int x = (int) Math.round(n.x - ax * o);
                int z = (int) Math.round(n.z - az * o);
                if (m.trailDistance(x, z) >= 3.0) {
                    q = new int[]{x, z};
                    break;
                }
            }
            if (q == null) {
                return null;
            }
            px = q[0];
            pz = q[1];
        }
        int[] o = own(px, pz, 2, 13);
        px = o[0];
        pz = o[1];
        double td = m.trailDistance(px, pz);
        if (td < 2.6 || !inCell(px, pz, 0) || TrailGeometry.slope(m, px, pz) > 2.0
            || !TrailGeometry.land(m.sample(px & ~3, pz & ~3), sl) || nearBox(ruin, px, pz, 4)) {
            return null;
        }
        for (Signpost s : others) {
            if (Math.abs(s.x - px) < 24 && Math.abs(s.z - pz) < 24) {
                return null;
            }
        }
        ColumnSample s = new ColumnSample();
        s.copyFrom(m.sample(px & ~3, pz & ~3));
        int biome = sites.biome(px, (int) s.height, pz, s);
        if (!RuinBiomes.trailVisible(biome)) {
            return null;
        }
        int climate = RuinSites.climateClass(s, biome, sl);
        long sseed = SeedMixer.hash2(seed ^ 0x5165L, px, pz);
        RuinMaterials mat = RuinSites.houseMaterials(climate, biome, s, HousePlanner.COTTAGE, sseed);
        double wet = Math.max(0.0, Math.min(1.0, (s.humidity + 0.6) / 1.2));
        int y = (int) Math.floor(m.interpolatedHeight(px, pz)) + 2;
        return new Signpost(px, y, pz, sseed, n.junction ? Signpost.JUNCTION : Signpost.END, n.x, n.z, n.arms, mat, wet);
    }

    // =========================================================================================
    // Lamp posts
    // =========================================================================================

    private LightStretch lights(List<int[]> points, TrailRuin ruin, List<Signpost> signs, List<Node> nodes) {
        int[] start = null;
        double[] t = null;
        if (ruin != null) {
            start = new int[]{ruin.trailX, ruin.trailZ};
            t = TrailGeometry.tangent(m, start[0], start[1]);
        }
        for (int k = 0; k < 8 && t == null; k++) {
            int[] p = points.get((int) (SeedMixer.cellUnit(seed, cx, cz, 60 + k) * points.size()));
            if (p[2] > 300) {
                continue;
            }
            int[] c = TrailGeometry.centre(m, p[0], p[1]);
            if (c == null || !TrailGeometry.laid(m, c[0], c[1])) {
                continue;
            }
            t = TrailGeometry.tangent(m, c[0], c[1]);
            start = c;
        }
        if (t == null) {
            return null;
        }
        List<double[]> fwd = TrailGeometry.march(m, start, t, 40, x0, z0, x0 + CELL - 1, z0 + CELL - 1);
        List<double[]> back = TrailGeometry.march(m, start, new double[]{-t[0], -t[1]}, 40, x0, z0, x0 + CELL - 1, z0 + CELL - 1);
        List<double[]> line = new ArrayList<>();
        for (int i = back.size() - 1; i >= 1; i--) {
            double[] q = back.get(i);
            line.add(new double[]{q[0], q[1], -q[2], -q[3]});
        }
        line.addAll(fwd);
        if (line.size() < 8) {
            return null;
        }
        double spacing = 16 + Math.floor(17.0 * SeedMixer.cellUnit(seed, cx, cz, 70));
        boolean alternate = SeedMixer.cellUnit(seed, cx, cz, 71) < 0.3;
        int side = SeedMixer.cellUnit(seed, cx, cz, 72) < 0.5 ? 1 : -1;
        List<int[]> posts = new ArrayList<>();
        double travelled = 0;
        double next = 0;
        for (int i = 0; i < line.size() && posts.size() < 12; i++) {
            double[] q = line.get(i);
            if (i > 0) {
                double[] p = line.get(i - 1);
                travelled += Math.hypot(q[0] - p[0], q[1] - p[1]);
            }
            if (travelled < next) {
                continue;
            }
            int[] post = lampPost(q, side, ruin, signs, nodes, posts);
            if (post != null) {
                posts.add(post);
                double jitter = (SeedMixer.unit(SeedMixer.hash2(seed, post[0], post[1])) - 0.5) * 4.0;
                next = travelled + spacing + jitter;
                if (alternate) {
                    side = -side;
                }
            }
        }
        if (posts.size() < 4) {
            return null;
        }
        // style and decay from the climate where the stretch starts
        ColumnSample s = new ColumnSample();
        s.copyFrom(m.sample(start[0] & ~3, start[1] & ~3));
        int biome = sites.biome(start[0], (int) s.height, start[1], s);
        int climate = RuinSites.climateClass(s, biome, sl);
        double r = SeedMixer.cellUnit(seed, cx, cz, 73);
        int style = switch (climate) {
            case RuinSites.COLD, RuinSites.UPLAND -> r < 0.6 ? LightStretch.CHAIN_LAMP : LightStretch.LAMP_ARM;
            case RuinSites.HOT_DRY -> LightStretch.POST_TOP;
            case RuinSites.HOT_WET -> LightStretch.BAMBOO;
            default -> biome == RuinBiomes.CHERRY || biome == RuinBiomes.MEADOW ? (r < 0.6 ? LightStretch.STONE_LANTERN : LightStretch.LAMP_ARM)
                : r < 0.55 ? LightStretch.LAMP_ARM : r < 0.75 ? LightStretch.STONE_LANTERN : LightStretch.POST_TOP;
        };
        double decay = 0.2 + 0.65 * SeedMixer.cellUnit(seed, cx, cz, 74);
        double wet = Math.max(0.0, Math.min(1.0, (s.humidity + 0.6) / 1.2));
        boolean soul = (biome == RuinBiomes.DARK_FOREST || biome == RuinBiomes.TAIGA || biome == RuinBiomes.SWAMP)
            && SeedMixer.cellUnit(seed, cx, cz, 75) < 0.3;
        long lseed = SeedMixer.hash2(seed ^ 0x11647L, cx, cz);
        RuinMaterials mat = RuinSites.houseMaterials(climate, biome, s, HousePlanner.COTTAGE, lseed);
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] p : posts) {
            minX = Math.min(minX, p[0] - 3);
            minZ = Math.min(minZ, p[1] - 3);
            maxX = Math.max(maxX, p[0] + 3);
            maxZ = Math.max(maxZ, p[1] + 3);
        }
        int[] mid = posts.get(posts.size() / 2);
        int y = (int) Math.floor(m.interpolatedHeight(mid[0], mid[1])) + 2;
        sites.stats.incrementAndGet(ST_LIGHTS);
        sites.stats.addAndGet(ST_POSTS, posts.size());
        return new LightStretch(mid[0], y, mid[1], lseed, minX, minZ, maxX, maxZ, posts.toArray(new int[0][]), style, decay, wet, soul, mat);
    }

    /** A lamp post beside the trail point {x, z, tx, tz}, or {@code null} if no good spot. */
    private int[] lampPost(double[] q, int side, TrailRuin ruin, List<Signpost> signs, List<Node> nodes, List<int[]> posts) {
        double nx = -q[3] * side;
        double nz = q[2] * side;
        for (int o = 3; o <= 7; o++) {
            int x = (int) Math.round(q[0] + nx * o);
            int z = (int) Math.round(q[1] + nz * o);
            double td = m.trailDistance(x, z);
            if (td < 3.5) {
                continue;
            }
            int[] own = own(x, z, 3, 12);
            x = own[0];
            z = own[1];
            td = m.trailDistance(x, z);
            if (td < 3.2 || td > 7.0 || !inCell(x, z, 0)) {
                return null;
            }
            if (TrailGeometry.slope(m, x, z) > 2.0 || !TrailGeometry.land(m.sample(x & ~3, z & ~3), sl) || nearBox(ruin, x, z, 4)) {
                return null;
            }
            for (Signpost s : signs) {
                if (Math.abs(s.x - x) < 8 && Math.abs(s.z - z) < 8) {
                    return null;
                }
            }
            for (Node n : nodes) {
                if (n.junction && Math.abs(n.x - x) < 20 && Math.abs(n.z - z) < 20) {
                    return null;
                }
            }
            for (int[] p : posts) {
                if ((p[0] >> 4) == (x >> 4) && (p[1] >> 4) == (z >> 4) || Math.abs(p[0] - x) + Math.abs(p[1] - z) < 10) {
                    return null;
                }
            }
            int toTrail = RuinCell.dirOf(-nx, -nz);
            return new int[]{x, z, toTrail};
        }
        return null;
    }
}
