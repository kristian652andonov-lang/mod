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
 * beside its trails: broken houses and other ruins, signposts and old lamp posts.
 * <ol>
 *   <li>A lattice scan every 8 blocks finds where trails run (most cells are done after this).</li>
 *   <li>Lattice points near a trail are snapped to its centre line and classified by how many
 *       branches cross a ring of radius 14 around them: one is a trail end, three or more a
 *       junction. Candidates are clustered, and clusters are validated with wider rings, which
 *       rejects the false ends and blobs around junction plazas. The scan reaches 48 blocks into
 *       the neighbouring cells, so a junction near a border is seen identically from both sides
 *       and gets exactly one signpost.</li>
 *   <li>The laid trail is traced into pieces between junctions, ends and the cell border. Ruins
 *       stand in slots spaced {@value #SLOT} blocks apart along each piece: a broken house in
 *       seven of ten, one of the other ruins (a watchtower, a well, a wagon, a shrine,
 *       a campsite, field walls, a chapel) in the rest, chosen by climate. A ruin is tried at its
 *       slot and a few blocks either way, on either side of the trail, beside a straight stretch
 *       on dry, level ground away from water, cliffs, rock formations and waterfalls; a house
 *       that finds no room is tried a size smaller, down to a hut, and is owed to the next slot
 *       if even that fails, and a slot whose ruin found none may get a small one instead. Where the trail fords a stream, or a piece runs
 *       into one and the trail goes on beyond it, the abutments of a lost bridge stand on the
 *       banks.</li>
 *   <li>Junctions and trail ends get signposts; long trails get a direction post or milestone
 *       where they cross the marker lines, away from junctions and ends and from each other.</li>
 *   <li>Lamp posts follow lit sections of trail. Whether a stretch of trail is lit is a smooth
 *       field over the world, and posts stand where the trail crosses a fixed grid of lines
 *       {@value #LAMP_LINE} blocks apart (the lines running across it, x lines for trails that
 *       run east and west, z lines for the others). Both are pure functions of position, so a lit
 *       section runs on across cell borders with even spacing, its posts seen identically from
 *       either side; each cell keeps the posts that stand inside it. A trail that crosses a line
 *       twice close together gets one post, no two posts stand closer than
 *       {@value #LAMP_MIN_GAP} blocks, and scraps of fewer than three posts stay dark.</li>
 * </ol>
 * Every site lies inside its cell; each signpost and lamp post stands in a column chosen so that
 * it lies wholly inside one chunk.
 */
public final class TrailSurvey {
    static final int CELL = RuinSites.TRAIL_CELL;
    private static final int LATTICE = 8;
    private static final int MARGIN = 48;
    /** Lattice columns of the scanned area (the cell and its margins). */
    private static final int GRID = (CELL + 2 * MARGIN) / LATTICE;
    /** Trail length between two ruin slots. */
    static final int SLOT = 80;
    /** Chance that a slot stays empty. */
    static final double P_EMPTY_SLOT = 0.1;
    /** Share of the filled slots that hold a broken house. */
    static final double P_HOUSE = 0.7;
    /** Chance that a slot whose ruin found no room gets a small one (a well, a wagon, a shrine, a campsite) instead. */
    static final double P_FALLBACK = 0.6;
    static final double P_JUNCTION_SIGN = 0.95;
    static final double P_END_SIGN = 0.9;
    /** Chance that a crossing of a marker line gets a direction post or milestone. */
    static final double P_MARKER = 0.9;
    /** Least distance between two direction posts or milestones. */
    public static final int MARKER_SPACING = 100;
    static final double P_BRIDGE = 0.8;
    /**
     * Widest run of stream water, along a trail or across the lane of an old bridge, that a
     * bridge spanned; over other water (a pond, the edge of a lake) the limit is
     * {@value #SPAN_MAX_BROAD}, and anything wider is a river, a lake or the sea.
     */
    static final int SPAN_MAX = 12;
    static final int SPAN_MAX_BROAD = 8;
    /** Distance from the centre of the trail to the near side of a bridge's lane. */
    static final int BRIDGE_LANE = 4;
    /** Spacing of the lamp grid lines. */
    static final int LAMP_LINE = 22;

    // statistics slots in RuinSites.stats (20-23 belong to the sunken ruins)
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
    static final int ST_SLOTS = 24;
    static final int ST_OVERLAP = 25;
    static final int ST_PIECE_BLOCKS = 26;
    static final int ST_BRIDGE_CROSSINGS = 27;
    static final int ST_MARKER_CROSSINGS = 28;
    static final int ST_LAMP_CROSSINGS = 29;
    static final int ST_LAMP_LIT = 30;
    static final int ST_LAMP_NODE = 31;
    static final int ST_LAMP_NO_SPOT = 32;
    static final int ST_LAMP_OWN = 33;
    static final int ST_LAMP_GROUND = 34;
    static final int ST_LAMP_RUIN = 35;
    static final int ST_LAMP_SIGN = 36;
    static final int ST_LAMP_BIOME = 37;
    // why stream crossings get no bridge abutments
    static final int ST_BRIDGE_ROLL = 38;
    static final int ST_BRIDGE_LANE = 39;
    static final int ST_BRIDGE_SPAN = 40;
    static final int ST_BRIDGE_BANKS = 41;
    static final int ST_BRIDGE_GROUND = 42;
    static final int ST_BRIDGE_DECK = 43;
    static final int ST_BRIDGE_ROOM = 44;
    /** House layouts as first drawn for their slots, before any falls back to a smaller one (6 slots). */
    static final int ST_LAYOUT_CHOSEN = 48;
    /** Lattice points on laid trail in biomes where the trail is drawn (deserts and beaches show none). */
    static final int ST_SHOWN_POINTS = 54;

    /** What a trail cell holds. */
    public record Cell(List<TrailRuin> ruins, List<Signpost> signposts, List<LightStretch> lights) {
    }

    static final Cell EMPTY = new Cell(List.of(), List.of(), List.of());

    private final RuinSites sites;
    private final TerrainModel m;
    private final int sl;
    private final int cx;
    private final int cz;
    private final int x0;
    private final int z0;
    private final long seed;
    /** Trail distance at the lattice points of the cell and its margins. */
    private final double[] grid = new double[GRID * GRID];
    private final ColumnSample scratch = new ColumnSample();

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
        List<int[]> near = new ArrayList<>();
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                int x = x0 - MARGIN + LATTICE / 2 + i * LATTICE;
                int z = z0 - MARGIN + LATTICE / 2 + j * LATTICE;
                double d = m.trailDistance(x, z);
                grid[i * GRID + j] = d;
                boolean inside = x >= x0 && x < x0 + CELL && z >= z0 && z < z0 + CELL;
                if (inside && d < 6.0) {
                    points.add(new int[]{x, z, (int) Math.round(d * 100)});
                }
                if (d < 4.0) {
                    near.add(new int[]{x, z});
                }
            }
        }
        if (points.isEmpty()) {
            return EMPTY;
        }
        sites.stats.incrementAndGet(ST_TRAIL_CELLS);
        for (int[] p : points) {
            if (p[2] < 200 && TrailGeometry.laid(m, p[0], p[1])) {
                sites.stats.incrementAndGet(ST_LAID_POINTS);
                if (sites.trailShown(p[0], p[1])) {
                    sites.stats.incrementAndGet(ST_SHOWN_POINTS);
                }
            }
        }
        // 2. junctions and trail ends
        List<Node> nodes = nodes(near);
        // 3. laid pieces of trail, and the ruins beside them
        List<Piece> pieces = pieces(points);
        List<TrailRuin> ruins = new ArrayList<>();
        bridges(pieces, ruins);
        slots(pieces, ruins);
        // 4. signposts at junctions and ends, markers along long trails
        List<Signpost> signs = new ArrayList<>();
        for (Node n : nodes) {
            if (!inCell(n.x, n.z, 0)) {
                continue;
            }
            double roll = SeedMixer.unit(SeedMixer.hash2(seed ^ 0x516E5L, n.x, n.z));
            if (roll >= (n.junction ? P_JUNCTION_SIGN : P_END_SIGN)) {
                continue;
            }
            Signpost s = signpost(n, ruins, signs);
            if (s != null) {
                signs.add(s);
                sites.stats.incrementAndGet(ST_SIGNS);
            }
        }
        markers(nodes, ruins, signs);
        // 5. lamp posts
        List<LightStretch> lights = lamps(nodes, ruins, signs);
        if (ruins.isEmpty() && signs.isEmpty() && lights.isEmpty()) {
            return EMPTY;
        }
        return new Cell(List.copyOf(ruins), List.copyOf(signs), List.copyOf(lights));
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
    // Pieces of laid trail
    // =========================================================================================

    /** A laid stretch of trail inside the cell: centre points {x, z, tx, tz} about 4 blocks apart. */
    record Piece(List<double[]> pts, double[] at) {
        double length() {
            return at[at.length - 1];
        }

        /** Index of the point nearest an arc length. */
        int index(double s) {
            int best = 0;
            for (int i = 0; i < at.length; i++) {
                if (Math.abs(at[i] - s) < Math.abs(at[best] - s)) {
                    best = i;
                }
            }
            return best;
        }
    }

    /**
     * Traces the laid trail of the cell into pieces: from each lattice point near a plain stretch
     * not yet covered, the trail is marched both ways until it forks, ends, stops being laid or
     * leaves the cell. Lattice points are visited in a fixed order, so the pieces are the same
     * whoever asks.
     */
    private List<Piece> pieces(List<int[]> points) {
        int n = CELL / LATTICE;
        int[] index = new int[n * n];
        Arrays.fill(index, -1);
        for (int i = 0; i < points.size(); i++) {
            int[] p = points.get(i);
            index[(p[0] - x0) / LATTICE * n + (p[1] - z0) / LATTICE] = i;
        }
        boolean[] used = new boolean[points.size()];
        List<Piece> out = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            int[] p = points.get(i);
            if (used[i] || p[2] > 200) {
                continue;
            }
            used[i] = true;
            int[] c = TrailGeometry.centre(m, p[0], p[1]);
            if (c == null || !inCell(c[0], c[1], 0) || !TrailGeometry.laid(m, c[0], c[1])) {
                continue;
            }
            double[] t = TrailGeometry.tangent(m, c[0], c[1]);
            if (t == null) {
                continue;
            }
            List<double[]> fwd = TrailGeometry.march(m, c, t, 80, x0, z0, x0 + CELL - 1, z0 + CELL - 1);
            List<double[]> back = TrailGeometry.march(m, c, new double[]{-t[0], -t[1]}, 80, x0, z0, x0 + CELL - 1, z0 + CELL - 1);
            List<double[]> line = new ArrayList<>();
            for (int k = back.size() - 1; k >= 1; k--) {
                double[] q = back.get(k);
                line.add(new double[]{q[0], q[1], -q[2], -q[3]});
            }
            line.addAll(fwd);
            for (double[] q : line) {
                int li = (int) Math.floor((q[0] - x0) / LATTICE);
                int lj = (int) Math.floor((q[1] - z0) / LATTICE);
                for (int a = li - 1; a <= li + 1; a++) {
                    for (int b = lj - 1; b <= lj + 1; b++) {
                        if (a >= 0 && b >= 0 && a < n && b < n && index[a * n + b] >= 0) {
                            int[] lp = points.get(index[a * n + b]);
                            if (Math.abs(lp[0] - q[0]) <= 8 && Math.abs(lp[1] - q[1]) <= 8) {
                                used[index[a * n + b]] = true;
                            }
                        }
                    }
                }
            }
            if (line.size() < 2) {
                continue;
            }
            double[] at = new double[line.size()];
            for (int k = 1; k < line.size(); k++) {
                at[k] = at[k - 1] + Math.hypot(line.get(k)[0] - line.get(k - 1)[0], line.get(k)[1] - line.get(k - 1)[1]);
            }
            out.add(new Piece(line, at));
            sites.stats.addAndGet(ST_PIECE_BLOCKS, (long) at[at.length - 1]);
        }
        return out;
    }

    // =========================================================================================
    // Ruins in slots along the pieces
    // =========================================================================================

    /** Kinds of ruin by climate, weights per {@link TrailRuin} kind (houses are drawn separately). */
    private static double[] kindWeights(int climate) {
        // watchtower, well, wagon, shrine, campsite, field, chapel
        return switch (climate) {
            case RuinSites.COLD -> new double[]{12, 10, 14, 14, 22, 8, 6};
            case RuinSites.UPLAND -> new double[]{20, 10, 10, 20, 16, 14, 8};
            case RuinSites.HOT_DRY -> new double[]{12, 26, 16, 18, 14, 8, 6};
            case RuinSites.HOT_WET -> new double[]{8, 16, 10, 26, 30, 0, 6};
            default -> new double[]{10, 14, 14, 14, 14, 20, 12};
        };
    }

    private static final int[] OTHER_KINDS = {TrailRuin.WATCHTOWER, TrailRuin.WELL, TrailRuin.WAGON, TrailRuin.SHRINE, TrailRuin.CAMPSITE,
        TrailRuin.FIELD, TrailRuin.CHAPEL};
    /** The small kinds that may stand in a slot where the planned ruin found no room. */
    private static final int[] SMALL_KINDS = {TrailRuin.WELL, TrailRuin.WAGON, TrailRuin.SHRINE, TrailRuin.CAMPSITE};

    /** Weights of the small kinds by climate, in the proportions of {@link #kindWeights}. */
    private static double[] smallWeights(int climate) {
        double[] w = kindWeights(climate);
        return new double[]{w[1], w[2], w[3], w[4]};
    }

    private static int pick(double[] weights, double r) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double x = r * total;
        for (int i = 0; i < weights.length; i++) {
            x -= weights[i];
            if (x < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    /**
     * One slot every {@value #SLOT} blocks along each piece, from a seeded offset. A house that
     * finds no room at its slot is owed: the next slot along the piece holds a house whatever it
     * drew, so stretches of awkward ground do not leave long gaps between houses.
     */
    private void slots(List<Piece> pieces, List<TrailRuin> ruins) {
        for (Piece pc : pieces) {
            double[] first = pc.pts.get(0);
            double off = SLOT * SeedMixer.unit(SeedMixer.hash2(seed ^ 0x5107L, (int) first[0], (int) first[1]));
            boolean owed = false;
            for (double s = off; s < pc.length(); s += SLOT) {
                double[] q = pc.pts.get(pc.index(s));
                long ss = SeedMixer.hash2(seed ^ 0x51075L, (int) q[0], (int) q[1]);
                sites.stats.incrementAndGet(ST_SLOTS);
                if (SeedMixer.unit(ss) < P_EMPTY_SLOT && !owed) {
                    continue;
                }
                int climate = climateAt((int) q[0], (int) q[1]);
                if (climate < 0) {
                    continue;
                }
                double r = SeedMixer.unit(SeedMixer.mix(ss + 1));
                int kind = r < P_HOUSE || owed ? TrailRuin.HOUSE : OTHER_KINDS[pick(kindWeights(climate), (r - P_HOUSE) / (1.0 - P_HOUSE))];
                TrailRuin site = slot(pc, s, kind, climate, ss, ruins);
                if (kind == TrailRuin.HOUSE) {
                    owed = site == null;
                }
                if (site == null && kind != TrailRuin.SHRINE && kind != TrailRuin.WELL && SeedMixer.unit(SeedMixer.mix(ss + 2)) < P_FALLBACK) {
                    // too big for the spot: something small may stand beside the trail instead
                    int small = SMALL_KINDS[pick(smallWeights(climate), SeedMixer.unit(SeedMixer.mix(ss + 4)))];
                    site = slot(pc, s, small, climate, ss + 3, ruins);
                }
                if (site != null) {
                    ruins.add(site);
                    sites.stats.incrementAndGet(ST_RUINS);
                }
            }
        }
    }

    /** Climate class at a trail point, or -1 where trails are not drawn (by the biome). */
    private int climateAt(int x, int z) {
        ColumnSample s = new ColumnSample();
        s.copyFrom(m.sample(x & ~3, z & ~3));
        int biome = sites.biome(x, (int) s.height, z, s);
        return RuinBiomes.trailVisible(biome) ? RuinSites.climateClass(s, biome, sl) : -1;
    }

    /**
     * Tries a ruin of one kind at a slot: its variant, style and state are fixed for the slot,
     * then it is tried at the slot point and 8, 16 and 24 blocks either way along the trail (a
     * house up to 32), on both sides. A house that does not fit is tried again a size smaller
     * ({@link #smaller}), down to a hut, so the big houses that find no room give cottages
     * rather than all falling to huts.
     */
    private TrailRuin slot(Piece pc, double s, int kind, int climate, long ss, List<TrailRuin> ruins) {
        long vs = SeedMixer.mix(ss ^ 0x7A21A7L);
        int variant;
        int style = 0;
        int state = 0;
        if (kind == TrailRuin.HOUSE) {
            style = houseStyle(climate, SeedMixer.unit(vs));
            variant = houseLayout(style, SeedMixer.unit(SeedMixer.mix(vs + 1)));
            state = houseState(style, climate, SeedMixer.unit(SeedMixer.mix(vs + 2)));
            sites.stats.incrementAndGet(ST_LAYOUT_CHOSEN + variant);
        } else {
            variant = variant(kind, climate, SeedMixer.unit(vs));
        }
        TrailRuin r = trySlot(pc, s, kind, variant, style, state, climate, ss, ruins);
        for (int v = smaller(variant); r == null && kind == TrailRuin.HOUSE && v >= 0; v = smaller(v)) {
            r = trySlot(pc, s, kind, v, style, state, climate, ss + 7 + v, ruins);
        }
        return r;
    }

    /** The next smaller house layout to try where one does not fit, or -1 after a hut. */
    private static int smaller(int layout) {
        return switch (layout) {
            case HousePlanner.FARMHOUSE, HousePlanner.TWO_ROOM, HousePlanner.TWO_STOREY -> HousePlanner.COTTAGE;
            case HousePlanner.COTTAGE, HousePlanner.TOWER_HOUSE -> HousePlanner.HUT;
            default -> -1;
        };
    }

    /** Offsets along the trail at which a ruin is tried, nearest the slot first. */
    private static final int[] OFFSETS = {0, 8, -8, 16, -16, 24, -24};
    private static final int[] HOUSE_OFFSETS = {0, 8, -8, 16, -16, 24, -24, 32, -32};

    private TrailRuin trySlot(Piece pc, double s, int kind, int variant, int style, int state, int climate, long ss, List<TrailRuin> ruins) {
        RuinSpec spec = RuinSites.spec(kind, variant, style);
        boolean square = kind == TrailRuin.HOUSE || kind == TrailRuin.CHAPEL || kind == TrailRuin.WAGON || kind == TrailRuin.FIELD;
        // a house may move further along its slot than the small ruins, which find room easily
        int[] offsets = kind == TrailRuin.HOUSE ? HOUSE_OFFSETS : OFFSETS;
        for (int a = 0; a < offsets.length; a++) {
            double sa = s + offsets[a];
            if (sa < 0 || sa > pc.length()) {
                continue;
            }
            sites.stats.incrementAndGet(ST_ATTEMPTS);
            double[] q = pc.pts.get(pc.index(sa));
            int[] c = {(int) q[0], (int) q[1]};
            double[] t = TrailGeometry.tangent(m, c[0], c[1]);
            if (t == null) {
                sites.stats.incrementAndGet(ST_NO_TANGENT);
                continue;
            }
            // square buildings face the trail squarely: it must run close to an axis
            double ang = Math.atan2(t[1], t[0]);
            double off = Math.abs(Math.IEEEremainder(ang, Math.PI / 2.0));
            if (square && off > Math.toRadians(kind == TrailRuin.HOUSE ? 35 : 30)) {
                sites.stats.incrementAndGet(ST_NOT_AXIS);
                continue;
            }
            long rs = SeedMixer.hash2(ss, a, kind);
            for (int sideTry = 0; sideTry < 2; sideTry++) {
                boolean first = SeedMixer.unit(SeedMixer.mix(rs + 5)) < 0.5;
                int side = first ^ sideTry == 1 ? 1 : -1;
                TrailRuin r = place(kind, variant, style, state, spec, c, t, side, rs + sideTry * 101L, climate, ruins);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /** House style by climate: timber frames in temperate land, log cabins in the cold, stone in the uplands. */
    private static int houseStyle(int climate, double r) {
        return switch (climate) {
            case RuinSites.COLD -> r < 0.75 ? HousePlanner.LOG_CABIN : r < 0.95 ? HousePlanner.STONE : HousePlanner.TIMBER_FRAME;
            case RuinSites.UPLAND -> r < 0.75 ? HousePlanner.STONE : HousePlanner.LOG_CABIN;
            case RuinSites.HOT_DRY -> HousePlanner.ADOBE;
            case RuinSites.HOT_WET -> r < 0.8 ? HousePlanner.RAISED : HousePlanner.STONE;
            default -> r < 0.7 ? HousePlanner.TIMBER_FRAME : r < 0.88 ? HousePlanner.STONE : HousePlanner.LOG_CABIN;
        };
    }

    /**
     * House layout as first drawn for a slot. The big layouts find room less often (a third of
     * the time) and fall back a size when they do not, so they are drawn far more often than they
     * should stand: what stands beside the trails is then about a fifth huts, a quarter cottages,
     * and two-room houses, farmhouses and two-storey houses a sixth or so each; a fifth of the
     * stone houses are tower houses.
     */
    private static int houseLayout(int style, double r) {
        double[] w = style == HousePlanner.STONE ? new double[]{8, 12, 22, 18, 20, 30} : new double[]{10, 14, 30, 28, 18, 10};
        for (int layout = 0; layout < w.length; layout++) {
            if (!HousePlanner.fits(layout, style)) {
                w[layout] = 0;
            }
        }
        return pick(w, r);
    }

    /** State of decay: wet country overgrows, dry country keeps its walls. */
    private static int houseState(int style, int climate, double r) {
        double[] w = {28, 22, 12, 12, 26};
        if (climate == RuinSites.HOT_WET) {
            w[HousePlanner.OVERGROWN] = 45;
        } else if (climate == RuinSites.HOT_DRY) {
            w[HousePlanner.OVERGROWN] = 6;
            w[HousePlanner.WALL_COLLAPSED] = 30;
        }
        for (int state = 0; state < w.length; state++) {
            if (!HousePlanner.fitsState(style, state)) {
                w[state] = 0;
            }
        }
        return pick(w, r);
    }

    /** Variant of a ruin that is not a house, by climate. */
    private static int variant(int kind, int climate, double r) {
        boolean wood = climate == RuinSites.COLD || climate == RuinSites.HOT_WET;
        return switch (kind) {
            case TrailRuin.WATCHTOWER -> pick(wood ? new double[]{30, 50, 20} : new double[]{45, 25, 30}, r);
            case TrailRuin.WELL -> pick(climate == RuinSites.HOT_DRY ? new double[]{25, 20, 15, 40} : new double[]{30, 25, 25, 20}, r);
            case TrailRuin.WAGON -> pick(new double[]{35, 25, 20, 20}, r);
            case TrailRuin.SHRINE -> pick(climate == RuinSites.UPLAND || climate == RuinSites.COLD ? new double[]{25, 25, 50}
                : climate == RuinSites.HOT_WET ? new double[]{20, 60, 20} : new double[]{45, 35, 20}, r);
            case TrailRuin.CAMPSITE -> pick(new double[]{35, 25, 20, 20}, r);
            case TrailRuin.FIELD -> pick(climate == RuinSites.UPLAND || climate == RuinSites.COLD ? new double[]{55, 20, 5, 20}
                : new double[]{30, 25, 25, 20}, r);
            case TrailRuin.CHAPEL -> pick(wood ? new double[]{25, 50, 25} : new double[]{50, 15, 35}, r);
            default -> 0;
        };
    }

    /**
     * Tries one ruin on one side of the trail point {@code c}; returns the site or {@code null}.
     * The front of the ruin faces the trail a small yard's width beyond the frayed edge of the
     * path, the body stands on level ground off the path, the yard on dry land.
     */
    private TrailRuin place(int kind, int variant, int style, int state, RuinSpec spec, int[] c, double[] t, int side, long rs, int climate,
                            List<TrailRuin> ruins) {
        int vdir = RuinCell.dirOf(-t[1] * side, t[0] * side);
        int vx = RuinCell.DX[vdir];
        int vz = RuinCell.DZ[vdir];
        boolean mirror = SeedMixer.unit(SeedMixer.mix(rs + 11)) < 0.5;
        int ux = mirror ? -vz : vz;
        int uz = mirror ? vx : -vx;
        double clear = spec.minTrail() + 0.5;
        int f0 = -1;
        for (int o = 2; o <= 14; o++) {
            if (m.trailDistance(c[0] + vx * o, c[1] + vz * o) >= clear) {
                f0 = o;
                break;
            }
        }
        if (f0 < 0) {
            sites.stats.incrementAndGet(ST_ON_TRAIL);
            return null;
        }
        // a wagon's near wheels stand at the first clear column; everything else leaves a small yard
        int gap = f0 + spec.gap() + (kind == TrailRuin.WAGON ? 1 : 1 + (int) (3.0 * SeedMixer.unit(SeedMixer.mix(rs + 12))));
        int shift = (int) Math.round((SeedMixer.unit(SeedMixer.mix(rs + 13)) - 0.5) * 4.0);
        int u0 = spec.doorU() + shift;
        Canvas.Frame f = new Canvas.Frame(c[0] + vx * gap - ux * u0, c[1] + vz * gap - uz * u0, ux, uz, vx, vz);
        // the footpath runs from the door to the trail
        int path = 0;
        if (spec.footpath()) {
            path = gap;
            for (int k = 1; k <= gap + 4; k++) {
                if (m.trailDistance(f.x(spec.doorU(), -k), f.z(spec.doorU(), -k)) < 2.0) {
                    path = k;
                    break;
                }
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
        for (TrailRuin o : ruins) {
            if (o.overlaps(bx0 - 4, bz0 - 4, bx1 + 4, bz1 + 4)) {
                sites.stats.incrementAndGet(ST_OVERLAP);
                return null;
            }
        }
        // the body itself: never on the path, on level ground
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        int vFront = -1 - spec.gap();
        for (int u = -1; u <= spec.w(); u++) {
            for (int v = vFront; v <= spec.d(); v++) {
                int x = f.x(u, v);
                int z = f.z(u, v);
                if (m.trailDistance(x, z) < spec.minTrail()) {
                    sites.stats.incrementAndGet(ST_ON_TRAIL);
                    return null;
                }
                if ((u & 1) == 0 && (v & 1) == 0 || u == spec.w() || v == spec.d()) {
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
                if (!pathStrip && m.trailDistance(x, z) < Math.min(3.0, spec.minTrail())) {
                    sites.stats.incrementAndGet(ST_ON_TRAIL);
                    return null;
                }
                if (Math.abs(m.interpolatedHeight(x, z) - (lo + hi) * 0.5) > spec.maxRange() + 2.5) {
                    sites.stats.incrementAndGet(ST_SLOPE);
                    return null;
                }
            }
        }
        // no part of it in a stream, a pond or the sea (the coarse test above misses narrow streams)
        for (int u = spec.minU() - 1; u <= spec.maxU() + 1; u++) {
            for (int v = Math.min(spec.minV(), -path) - 1; v <= spec.maxV() + 1; v++) {
                if (water(f.x(u, v), f.z(u, v))) {
                    sites.stats.incrementAndGet(ST_LAND);
                    return null;
                }
            }
        }
        int hx = f.x(spec.w() / 2, spec.d() / 2);
        int hz = f.z(spec.w() / 2, spec.d() / 2);
        boolean big = kind == TrailRuin.HOUSE || kind == TrailRuin.CHAPEL || kind == TrailRuin.WATCHTOWER || kind == TrailRuin.FIELD;
        if (sites.formationNear(hx, hz, big ? 100 : 60, 0) || sites.waterfallNear(hx, hz, 56)) {
            sites.stats.incrementAndGet(ST_FEATURE);
            return null;
        }
        ColumnSample hs = new ColumnSample();
        hs.copyFrom(m.sample(hx & ~3, hz & ~3));
        int biome = sites.biome(hx, (int) hs.height, hz, hs);
        if (!RuinBiomes.trailVisible(biome)) {
            sites.stats.incrementAndGet(ST_BIOME);
            return null;
        }
        // floor course: the median of the ground under the body
        double[] h9 = new double[9];
        int k = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int u = i * (spec.w() - 1) / 2;
                int v = j * (spec.d() - 1) / 2;
                h9[k++] = m.interpolatedHeight(f.x(u, v), f.z(u, v));
            }
        }
        Arrays.sort(h9);
        int floorY = (int) Math.floor(h9[4]);
        long siteSeed = SeedMixer.hash2(seed ^ kind * 0x9E37L, hx, hz);
        return site(kind, variant, style, state, climate, biome, hs, spec, f, hx, hz, floorY, siteSeed, bx0, bz0, bx1, bz1, c, path);
    }

    /** Materials, decay, wetness, name and loot of a placed ruin. */
    private TrailRuin site(int kind, int variant, int style, int state, int climate, int biome, ColumnSample hs, RuinSpec spec, Canvas.Frame f,
                           int hx, int hz, int floorY, long siteSeed, int bx0, int bz0, int bx1, int bz1, int[] c, int path) {
        RuinMaterials mat = RuinSites.materials(kind, variant, style, climate, biome, hs, siteSeed);
        double u = SeedMixer.unit(SeedMixer.mix(siteSeed ^ 0xDECA1L));
        double decay = Math.max(0.2, Math.min(0.92, 0.25 + 0.65 * u + 0.08 * hs.humidity));
        double wet = Math.max(0.0, Math.min(1.0, (hs.humidity + 0.6) / 1.2));
        if (climate == RuinSites.HOT_WET) {
            wet = Math.max(wet, 0.7);
        } else if (climate == RuinSites.HOT_DRY) {
            wet = Math.min(wet, 0.25);
        }
        String name = RuinNames.name(siteSeed);
        String loot = RuinSites.lootTable(kind, variant, climate, biome, mat);
        return new TrailRuin(hx, floorY + 1, hz, siteSeed, bx0, bz0, bx1, bz1, kind, variant, style, state, climate, mat, f, spec, floorY, decay,
            wet, name, c[0], c[1], loot, path, m);
    }

    // =========================================================================================
    // Bridges
    // =========================================================================================

    /** Whether a column holds the water of a stream, a river or a lake. */
    private boolean water(int x, int z) {
        return RuinSites.water(m, x, z);
    }

    /** What water a column holds (see {@link RuinSites#waterKind}). */
    private int waterKind(int x, int z) {
        return RuinSites.waterKind(m, x, z);
    }

    /**
     * The abutments of lost bridges stand on both banks of a stream the trail crosses, in a lane
     * beside the trail (walkers ford the stream where the bridge once stood). A crossing is found
     * two ways: a piece of laid trail runs through a narrow run of water (trails are laid up to
     * a stream on both banks and the survey follows them across it, so most streams are forded
     * within a piece), or a piece of trail stops at the water and is laid again beyond it.
     */
    private void bridges(List<Piece> pieces, List<TrailRuin> ruins) {
        for (Piece pc : pieces) {
            fords(pc, ruins);
            for (int end = 0; end < 2; end++) {
                double[] q = end == 0 ? pc.pts.get(0) : pc.pts.get(pc.pts.size() - 1);
                double tx = end == 0 ? -q[2] : q[2];
                double tz = end == 0 ? -q[3] : q[3];
                // the crossing runs close to an axis, so the abutments face each other squarely
                int vdir = RuinCell.dirOf(tx, tz);
                double dot = RuinCell.DX[vdir] * tx + RuinCell.DZ[vdir] * tz;
                if (dot < Math.cos(Math.toRadians(35))) {
                    continue;
                }
                int px = (int) Math.round(q[0]);
                int pz = (int) Math.round(q[1]);
                int wetAt = -1;
                for (int k = 1; k <= 10; k++) {
                    if (water(px + RuinCell.DX[vdir] * k, pz + RuinCell.DZ[vdir] * k)) {
                        wetAt = k;
                        break;
                    }
                }
                if (wetAt < 0) {
                    continue;
                }
                sites.stats.incrementAndGet(ST_BRIDGE_CROSSINGS);
                TrailRuin b = bridge(px, pz, vdir, laneSide(vdir, tx, tz), ruins);
                if (b != null) {
                    ruins.add(b);
                    sites.stats.incrementAndGet(ST_RUINS);
                }
            }
        }
    }

    /**
     * Streams forded by a laid piece of trail: the piece is walked a block at a time, and a run
     * of at most {@value #SPAN_MAX} wet columns ({@value #SPAN_MAX_BROAD} where the water is not a
     * stream) with dry trail on both sides is a ford.
     */
    private void fords(Piece pc, List<TrailRuin> ruins) {
        int n = (int) pc.length() + 1;
        if (n < 12) {
            return;
        }
        // the piece resampled a block apart, and which of those columns hold water
        double[][] at = new double[n][];
        int[] kind = new int[n];
        boolean[] wet = new boolean[n];
        int seg = 0;
        for (int i = 0; i < n; i++) {
            while (seg + 2 < pc.at.length && pc.at[seg + 1] < i) {
                seg++;
            }
            double[] a = pc.pts.get(seg);
            double[] b = pc.pts.get(Math.min(seg + 1, pc.pts.size() - 1));
            double len = pc.at[Math.min(seg + 1, pc.at.length - 1)] - pc.at[seg];
            double f = len > 0 ? Math.max(0.0, Math.min(1.0, (i - pc.at[seg]) / len)) : 0.0;
            at[i] = new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f};
            kind[i] = waterKind((int) Math.round(at[i][0]), (int) Math.round(at[i][1]));
            wet[i] = kind[i] != RuinSites.DRY;
        }
        int i = 4;
        while (i < n - 4) {
            if (!wet[i] || wet[i - 1]) {
                i++;
                continue;
            }
            int j = i;
            boolean broad = false;
            while (j < n && wet[j]) {
                broad |= kind[j] == RuinSites.BROAD_WATER;
                j++;
            }
            // a narrow run of water with dry trail for four blocks on both sides
            boolean ford = j - i <= (broad ? SPAN_MAX_BROAD : SPAN_MAX) && j + 4 <= n;
            for (int k = 1; k <= 4 && ford; k++) {
                ford = !wet[i - k] && !wet[j - 1 + k];
            }
            if (ford) {
                // the direction of the crossing: the chord from bank to bank
                double tx = at[j + 3][0] - at[i - 4][0];
                double tz = at[j + 3][1] - at[i - 4][1];
                double l = Math.hypot(tx, tz);
                int vdir = RuinCell.dirOf(tx, tz);
                if (l > 1.0 && (RuinCell.DX[vdir] * tx + RuinCell.DZ[vdir] * tz) / l >= Math.cos(Math.toRadians(35))) {
                    sites.stats.incrementAndGet(ST_BRIDGE_CROSSINGS);
                    // the last dry column of the trail before the water
                    int px = (int) Math.round(at[i - 1][0]);
                    int pz = (int) Math.round(at[i - 1][1]);
                    TrailRuin b = bridge(px, pz, vdir, laneSide(vdir, tx / l, tz / l), ruins);
                    if (b != null) {
                        ruins.add(b);
                        sites.stats.incrementAndGet(ST_RUINS);
                    }
                }
            }
            i = j;
        }
    }

    /**
     * The side of the trail (+1 or -1, as {@link #bridge} counts it) that the trail bends away
     * from as it crosses: a trail that crosses at an angle to the axis drifts sideways along the
     * lane, so the lane goes on the side it drifts away from. 0 where the trail runs straight
     * along the axis and either side will do.
     */
    private static int laneSide(int vdir, double tx, double tz) {
        // the lane of side +1 lies towards u = (-vz, vx)
        double tu = -RuinCell.DZ[vdir] * tx + RuinCell.DX[vdir] * tz;
        return Math.abs(tu) < 0.08 ? 0 : tu > 0 ? -1 : 1;
    }

    /**
     * The abutments of a crossing whose near bank lies just ahead of the trail column (px, pz)
     * in direction {@code vdir}, in a lane on side {@code prefer} of the trail (0: either), or
     * {@code null}.
     */
    private TrailRuin bridge(int px, int pz, int vdir, int prefer, List<TrailRuin> ruins) {
        int vx = RuinCell.DX[vdir];
        int vz = RuinCell.DZ[vdir];
        long bs = SeedMixer.hash2(seed ^ 0xB121D6EL, px >> 3, pz >> 3);
        if (SeedMixer.unit(bs) >= P_BRIDGE) {
            sites.stats.incrementAndGet(ST_BRIDGE_ROLL);
            return null;
        }
        int side = prefer != 0 ? prefer : SeedMixer.unit(SeedMixer.mix(bs + 1)) < 0.5 ? 1 : -1;
        int ux = -vz * side;
        int uz = vx * side;
        int lane = BRIDGE_LANE;
        int w = LandmarkPlanner.BRIDGE_WIDTH;
        int depth = LandmarkPlanner.ABUTMENT_DEPTH;
        // rows across the lane, counted from the end of the laid trail: wet if any lane column is water
        int maxK = 24;
        boolean[] wetRow = new boolean[maxK + depth + 2];
        boolean[] broadRow = new boolean[maxK + depth + 2];
        for (int k = -depth; k <= maxK; k++) {
            for (int u = 0; u < w; u++) {
                int kind = waterKind(px + ux * (lane + u) + vx * k, pz + uz * (lane + u) + vz * k);
                wetRow[k + depth] |= kind != RuinSites.DRY;
                broadRow[k + depth] |= kind == RuinSites.BROAD_WATER;
            }
        }
        int first = -1;
        for (int k = 1; k <= maxK; k++) {
            if (wetRow[k + depth]) {
                first = k;
                break;
            }
        }
        if (first < 0) {
            sites.stats.incrementAndGet(ST_BRIDGE_LANE);
            return null;
        }
        int span = 0;
        boolean broad = false;
        while (first + span <= maxK && wetRow[first + span + depth]) {
            broad |= broadRow[first + span + depth];
            span++;
        }
        if (span < 1 || span > (broad ? SPAN_MAX_BROAD : SPAN_MAX) || first + span + depth - 1 > maxK) {
            sites.stats.incrementAndGet(ST_BRIDGE_SPAN);
            return null;
        }
        for (int k = first - depth; k < first; k++) {
            if (k < -depth || wetRow[k + depth]) {
                sites.stats.incrementAndGet(ST_BRIDGE_BANKS);
                return null;
            }
        }
        for (int k = first + span; k < first + span + depth; k++) {
            if (wetRow[k + depth]) {
                sites.stats.incrementAndGet(ST_BRIDGE_BANKS);
                return null;
            }
        }
        Canvas.Frame f = new Canvas.Frame(px + ux * lane + vx * first, pz + uz * lane + vz * first, ux, uz, vx, vz);
        // every abutment column dry, off the path and on firm ground; the banks within a few blocks of each other
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (int u = 0; u < w; u++) {
            for (int v = -depth; v < span + depth; v++) {
                if (v >= 0 && v < span) {
                    continue;
                }
                int x = f.x(u, v);
                int z = f.z(u, v);
                m.sampleInto(x & ~3, z & ~3, scratch);
                if (scratch.height < sl + 1.5 || scratch.cliff > 0.3 || scratch.river > 0.0 || m.trailDistance(x, z) < 2.6) {
                    sites.stats.incrementAndGet(ST_BRIDGE_GROUND);
                    return null;
                }
                double h = m.interpolatedHeight(x, z);
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
        }
        if (hi - lo > 4.0) {
            sites.stats.incrementAndGet(ST_BRIDGE_GROUND);
            return null;
        }
        int floorY = (int) Math.floor(hi) + 1;
        // the deck stubs over the water stay above it
        double[] info = new double[2];
        for (int u = 0; u < w; u++) {
            for (int v = 0; v < span; v++) {
                int x = f.x(u, v);
                int z = f.z(u, v);
                m.streamInfo(x, z, info);
                m.sampleInto(x & ~3, z & ~3, scratch);
                double top = Math.max(scratch.waterLevel == ColumnSample.NO_WATER ? -1e9 : scratch.waterLevel, info[0] < 4.5 ? info[1] : -1e9);
                if (top > floorY) {
                    sites.stats.incrementAndGet(ST_BRIDGE_DECK);
                    return null;
                }
            }
        }
        RuinSpec spec = LandmarkPlanner.bridgeSpec(span);
        int bx0 = Math.min(f.x(-2, -depth - 4), f.x(w + 1, span + depth + 2));
        int bx1 = Math.max(f.x(-2, -depth - 4), f.x(w + 1, span + depth + 2));
        int bz0 = Math.min(f.z(-2, -depth - 4), f.z(w + 1, span + depth + 2));
        int bz1 = Math.max(f.z(-2, -depth - 4), f.z(w + 1, span + depth + 2));
        if (bx0 < x0 + 8 || bx1 >= x0 + CELL - 8 || bz0 < z0 + 8 || bz1 >= z0 + CELL - 8) {
            sites.stats.incrementAndGet(ST_BRIDGE_ROOM);
            return null;
        }
        for (TrailRuin o : ruins) {
            if (o.overlaps(bx0 - 4, bz0 - 4, bx1 + 4, bz1 + 4)) {
                sites.stats.incrementAndGet(ST_BRIDGE_ROOM);
                return null;
            }
        }
        int hx = f.x(1, span / 2);
        int hz = f.z(1, span / 2);
        ColumnSample hs = new ColumnSample();
        hs.copyFrom(m.sample(f.x(1, -2) & ~3, f.z(1, -2) & ~3));
        int biome = sites.biome(hx, floorY, hz, hs);
        if (!RuinBiomes.trailVisible(biome)) {
            return null;
        }
        int climate = RuinSites.climateClass(hs, biome, sl);
        int variant = pick(climate == RuinSites.COLD || climate == RuinSites.HOT_WET ? new double[]{30, 50, 20} : new double[]{55, 25, 20},
            SeedMixer.unit(SeedMixer.mix(bs + 2)));
        long siteSeed = SeedMixer.hash2(seed ^ 0xB121D6E5L, hx, hz);
        return site(TrailRuin.BRIDGE, variant, 0, 0, climate, biome, hs, spec, f, hx, hz, floorY, siteSeed, bx0, bz0, bx1, bz1, new int[]{px, pz},
            span);
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

    private static boolean nearBox(RuinSite s, int x, int z, int pad) {
        return s != null && x >= s.minX - pad && x <= s.maxX + pad && z >= s.minZ - pad && z <= s.maxZ + pad;
    }

    private static boolean nearAny(List<TrailRuin> ruins, int x, int z, int pad) {
        for (TrailRuin r : ruins) {
            if (nearBox(r, x, z, pad)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a post may stand at a column: beside the path, inside the cell, on level dry land,
     * clear of ruins, with no water under it or under its arms, plate or board (a block out on
     * every side; the water solver digs even weak streams, which the coarse land test misses).
     */
    private boolean postSpot(int x, int z, List<TrailRuin> ruins) {
        double td = m.trailDistance(x, z);
        if (td < 2.6 || !inCell(x, z, 0) || TrailGeometry.slope(m, x, z) > 2.0 || !TrailGeometry.land(m.sample(x & ~3, z & ~3), sl)
            || nearAny(ruins, x, z, 4)) {
            return false;
        }
        for (int d = -1; d < 4; d++) {
            if (water(x + (d < 0 ? 0 : RuinCell.DX[d]), z + (d < 0 ? 0 : RuinCell.DZ[d]))) {
                return false;
            }
        }
        return true;
    }

    private Signpost signpost(Node n, List<TrailRuin> ruins, List<Signpost> others) {
        List<int[]> spots = new ArrayList<>();
        if (n.junction) {
            // stand in the widest gap between the branches, just off the junction; else in the next widest
            double[] arms = n.arms.clone();
            Arrays.sort(arms);
            List<double[]> gaps = new ArrayList<>();
            for (int i = 0; i < arms.length; i++) {
                double a0 = arms[i];
                double a1 = i + 1 < arms.length ? arms[i + 1] : arms[0] + Math.PI * 2.0;
                gaps.add(new double[]{a1 - a0, (a0 + a1) * 0.5});
            }
            gaps.sort((a, b) -> Double.compare(b[0], a[0]));
            for (double[] g : gaps) {
                for (int d = 3; d <= 20; d++) {
                    int x = (int) Math.round(n.x + d * Math.cos(g[1]));
                    int z = (int) Math.round(n.z + d * Math.sin(g[1]));
                    if (m.trailDistance(x, z) >= 3.2) {
                        spots.add(new int[]{x, z});
                        break;
                    }
                }
            }
        } else {
            // in line with the path, just past its end, facing walkers who arrive along it; else beside the end
            double ax = Math.cos(n.arms[0]);
            double az = Math.sin(n.arms[0]);
            for (int o = 2; o <= 6; o++) {
                int x = (int) Math.round(n.x - ax * o);
                int z = (int) Math.round(n.z - az * o);
                if (m.trailDistance(x, z) >= 3.0) {
                    spots.add(new int[]{x, z});
                    break;
                }
            }
            for (int sd = -1; sd <= 1; sd += 2) {
                for (int o = 3; o <= 6; o++) {
                    int x = (int) Math.round(n.x + 2 * ax - az * o * sd);
                    int z = (int) Math.round(n.z + 2 * az + ax * o * sd);
                    if (m.trailDistance(x, z) >= 3.2) {
                        spots.add(new int[]{x, z});
                        break;
                    }
                }
            }
        }
        for (int[] q : spots) {
            int[] o = own(q[0], q[1], 2, 13);
            int px = o[0];
            int pz = o[1];
            if (!postSpot(px, pz, ruins)) {
                continue;
            }
            boolean crowded = false;
            for (Signpost s : others) {
                crowded |= Math.abs(s.x - px) < 24 && Math.abs(s.z - pz) < 24;
            }
            if (crowded) {
                continue;
            }
            ColumnSample s = new ColumnSample();
            s.copyFrom(m.sample(px & ~3, pz & ~3));
            int biome = sites.biome(px, (int) s.height, pz, s);
            if (!RuinBiomes.trailVisible(biome)) {
                return null;
            }
            int climate = RuinSites.climateClass(s, biome, sl);
            long sseed = SeedMixer.hash2(seed ^ 0x5165L, px, pz);
            RuinMaterials mat = RuinSites.materials(TrailRuin.HOUSE, HousePlanner.COTTAGE, HousePlanner.TIMBER_FRAME, climate, biome, s, sseed);
            double wet = Math.max(0.0, Math.min(1.0, (s.humidity + 0.6) / 1.2));
            int y = (int) Math.floor(m.interpolatedHeight(px, pz)) + 2;
            int style = postStyle(climate, SeedMixer.unit(SeedMixer.mix(sseed + 3)));
            return new Signpost(px, y, pz, sseed, n.junction ? Signpost.JUNCTION : Signpost.END, style, n.x, n.z, n.arms, mat, wet,
                RuinCell.dirOf(n.x - px, n.z - pz));
        }
        return null;
    }

    /** Post style by climate: stone in the uplands, bamboo in the jungle, logs in the north. */
    private static int postStyle(int climate, double r) {
        return switch (climate) {
            case RuinSites.UPLAND -> r < 0.6 ? Signpost.STONE_POST : Signpost.BARK_POST;
            case RuinSites.COLD -> r < 0.65 ? Signpost.BARK_POST : Signpost.STRIPPED_POST;
            case RuinSites.HOT_WET -> r < 0.75 ? Signpost.BAMBOO_POST : Signpost.STRIPPED_POST;
            case RuinSites.HOT_DRY -> r < 0.5 ? Signpost.STONE_POST : Signpost.STRIPPED_POST;
            default -> r < 0.6 ? Signpost.STRIPPED_POST : r < 0.85 ? Signpost.BARK_POST : Signpost.STONE_POST;
        };
    }

    /**
     * Markers along long trails: where a trail crosses one of the marker lines (x and z a quarter
     * and three quarters of the way across every cell, so 128 blocks apart), at least
     * {@value #MARKER_NODE_GAP} blocks from junctions and trail ends, a direction post with an arm
     * each way or a milestone. Markers keep {@value #MARKER_SPACING} blocks from each other: of
     * two candidate crossings that could put their posts closer, the one with the lower roll
     * stands. Candidates are pure functions of position, so the rule reads the same on both sides
     * of a cell border; a long trail gets a marker every 130 to 250 blocks or so.
     */
    private void markers(List<Node> nodes, List<TrailRuin> ruins, List<Signpost> signs) {
        List<long[]> own = markerCandidates(x0, z0, x0 + CELL - 1, z0 + CELL - 1);
        if (own.isEmpty()) {
            return;
        }
        int rival = MARKER_SPACING + 2 * MARKER_REACH;
        List<long[]> rivals = markerCandidates(x0 - rival, z0 - rival, x0 + CELL - 1 + rival, z0 + CELL - 1 + rival);
        for (long[] cand : own) {
            int cxp = (int) cand[0];
            int czp = (int) cand[1];
            sites.stats.incrementAndGet(ST_MARKER_CROSSINGS);
            boolean clear = true;
            for (Node n : nodes) {
                clear &= Math.hypot(n.x - cxp, n.z - czp) >= MARKER_NODE_GAP;
            }
            for (long[] r : rivals) {
                if (r != cand && Math.hypot(r[0] - cxp, r[1] - czp) < rival && precedes(r, cand)) {
                    clear = false;
                }
            }
            if (!clear) {
                continue;
            }
            double[] t = TrailGeometry.tangent(m, cxp, czp);
            long ms = cand[2];
            int side = SeedMixer.unit(SeedMixer.mix(ms + 1)) < 0.5 ? 1 : -1;
            double nx = -t[1] * side;
            double nz = t[0] * side;
            for (int o = 3; o <= 6; o++) {
                int x = (int) Math.round(cxp + nx * o);
                int z = (int) Math.round(czp + nz * o);
                if (m.trailDistance(x, z) < 3.0) {
                    continue;
                }
                int[] at = own(x, z, 2, 13);
                if (!postSpot(at[0], at[1], ruins)) {
                    break;
                }
                int px = at[0];
                int pz = at[1];
                ColumnSample s = new ColumnSample();
                s.copyFrom(m.sample(px & ~3, pz & ~3));
                int biome = sites.biome(px, (int) s.height, pz, s);
                if (!RuinBiomes.trailVisible(biome)) {
                    break;
                }
                int climate = RuinSites.climateClass(s, biome, sl);
                long sseed = SeedMixer.hash2(seed ^ 0x3A115L, px, pz);
                double r = SeedMixer.unit(SeedMixer.mix(sseed + 1));
                boolean stoneCountry = climate == RuinSites.UPLAND || climate == RuinSites.COLD || climate == RuinSites.HOT_DRY;
                int kind = r < (stoneCountry ? 0.55 : 0.35) ? Signpost.MILESTONE : Signpost.WAYPOST;
                RuinMaterials mat = RuinSites.materials(TrailRuin.HOUSE, HousePlanner.COTTAGE, HousePlanner.TIMBER_FRAME, climate, biome, s, sseed);
                double wet = Math.max(0.0, Math.min(1.0, (s.humidity + 0.6) / 1.2));
                int y = (int) Math.floor(m.interpolatedHeight(px, pz)) + 2;
                double a = Math.atan2(t[1], t[0]);
                signs.add(new Signpost(px, y, pz, sseed, kind, postStyle(climate, SeedMixer.unit(SeedMixer.mix(sseed + 3))), cxp, czp,
                    new double[]{a, a + Math.PI}, mat, wet, RuinCell.dirOf(cxp - px, czp - pz)));
                sites.stats.incrementAndGet(ST_SIGNS);
                break;
            }
        }
    }

    /** Edge strip of each cell where a marker line's crossings are not candidates. */
    private static final int MARKER_EDGE = 32;
    /** Least distance from a marker's crossing to a junction or a trail end. */
    static final int MARKER_NODE_GAP = 80;
    /** Farthest a marker post stands from its crossing: 6 blocks off the trail, 2 more to fit its chunk. */
    private static final int MARKER_REACH = 9;

    /**
     * The marker candidates on the lines within a box: crossings of a marker line at least
     * {@value #MARKER_EDGE} blocks inside their own cell (so the nodes a candidate keeps away from,
     * found up to {@value #MARGIN} blocks beyond the cell, are all known to it) that win the
     * marker roll on a laid, straight stretch of trail. Each is {x, z, roll seed}. They depend on
     * position alone, whichever cell asks.
     */
    private List<long[]> markerCandidates(int bx0, int bz0, int bx1, int bz1) {
        List<long[]> out = new ArrayList<>();
        int quarter = CELL / 4;
        for (int family = 0; family < 2; family++) {
            boolean alongZ = family == 0;
            int lo = alongZ ? bx0 : bz0;
            int hi = alongZ ? bx1 : bz1;
            // marker lines lie at a quarter and three quarters of each cell: quarter mod 2 * quarter
            for (int k = Math.floorDiv(lo - quarter + 2 * quarter - 1, 2 * quarter); k * 2 * quarter + quarter <= hi; k++) {
                int line = k * 2 * quarter + quarter;
                int p0 = alongZ ? bz0 : bx0;
                int p1 = alongZ ? bz1 : bx1;
                for (int[] c : crossings(alongZ, line, p0, p1 + 1)) {
                    int pos = alongZ ? c[1] : c[0];
                    int local = Math.floorMod(pos, CELL);
                    if (local < MARKER_EDGE || local >= CELL - MARKER_EDGE) {
                        continue;
                    }
                    long ms = SeedMixer.hash2(seed ^ 0x3A11L, c[0], c[1]);
                    if (SeedMixer.unit(ms) >= P_MARKER || !TrailGeometry.laid(m, c[0], c[1]) || TrailGeometry.tangent(m, c[0], c[1]) == null) {
                        continue;
                    }
                    out.add(new long[]{c[0], c[1], ms});
                }
            }
        }
        return out;
    }

    /** Whether marker candidate a takes precedence over b: the lower roll, then the lower column. */
    private static boolean precedes(long[] a, long[] b) {
        double ua = SeedMixer.unit(a[2]);
        double ub = SeedMixer.unit(b[2]);
        return ua != ub ? ua < ub : a[0] != b[0] ? a[0] < b[0] : a[1] < b[1];
    }

    /** A crossing is the lowest trail distance within this many blocks either way along its line. */
    private static final int CROSS_WINDOW = 3;
    /**
     * Crossings of one line closer than this are one crossing: a trail that bends back over a line
     * (a hairpin) or splits into a braided loop crosses it twice a few blocks apart, and would
     * get two posts side by side.
     */
    private static final int CROSS_MERGE = LAMP_LINE / 2;

    /**
     * Where trails cross a line: {@code alongZ} scans the x = {@code line} line along z from
     * {@code lo} to {@code hi} (else the z = {@code line} line along x), and returns the columns
     * on the line nearest the trail's centre, {x, z}: the lowest trail distance (below 1.5) within
     * {@value #CROSS_WINDOW} blocks either way, and of crossings within {@link #CROSS_MERGE} blocks
     * of each other only the one nearest the centre. The tests look only at their own
     * neighbourhood, so two cells scanning the same stretch of a line find the same crossings.
     * Stretches of the line far from every lattice point near a trail are skipped.
     */
    private List<int[]> crossings(boolean alongZ, int line, int lo, int hi) {
        List<int[]> out = new ArrayList<>();
        int reach = CROSS_MERGE + CROSS_WINDOW;
        int a = lo - reach;
        double[] d = new double[hi - lo + 2 * reach];
        for (int pos = a; pos < hi + reach; pos++) {
            d[pos - a] = latticeDistance(alongZ, line, pos) > 12.0 ? Double.MAX_VALUE
                : alongZ ? m.trailDistance(line, pos) : m.trailDistance(pos, line);
        }
        List<Integer> minima = new ArrayList<>();
        for (int pos = lo - CROSS_MERGE; pos < hi + CROSS_MERGE; pos++) {
            double v = d[pos - a];
            if (v >= 1.5) {
                continue;
            }
            boolean min = true;
            for (int q = pos - CROSS_WINDOW; q <= pos + CROSS_WINDOW && min; q++) {
                double o = d[q - a];
                min = q < pos ? v < o : q == pos || v <= o;
            }
            if (min) {
                minima.add(pos);
            }
        }
        for (int pos : minima) {
            if (pos < lo || pos >= hi) {
                continue;
            }
            boolean keep = true;
            for (int q : minima) {
                if (q != pos && Math.abs(q - pos) <= CROSS_MERGE && (d[q - a] < d[pos - a] || d[q - a] == d[pos - a] && q < pos)) {
                    keep = false;
                    break;
                }
            }
            if (keep) {
                out.add(alongZ ? new int[]{line, pos} : new int[]{pos, line});
            }
        }
        return out;
    }

    /**
     * Least trail distance at the two lattice points beside a position on a line, or 0 outside
     * the scanned lattice (which leaves the position to be measured).
     */
    private double latticeDistance(boolean alongZ, int line, int pos) {
        int gi = Math.floorDiv(line - (alongZ ? x0 : z0) + MARGIN - LATTICE / 2, LATTICE);
        int gj = Math.floorDiv(pos - (alongZ ? z0 : x0) + MARGIN, LATTICE);
        if (gi < 0 || gi + 1 >= GRID || gj < 0 || gj >= GRID) {
            return 0.0;
        }
        return Math.min(alongZ ? grid[gi * GRID + gj] : grid[gj * GRID + gi], alongZ ? grid[(gi + 1) * GRID + gj] : grid[gj * GRID + gi + 1]);
    }

    // =========================================================================================
    // Lamp posts
    // =========================================================================================

    /** A crossing of a lamp line: centre {x, z}, unit tangent, the line's index and family. */
    private record LampCrossing(int x, int z, double tx, double tz, int line, boolean xFamily) {
    }

    /**
     * Lamp posts on the lit sections of the cell's trails. Candidates are the crossings of the
     * lamp lines; a trail running more east-west than north-south takes the x lines, the others
     * the z lines, and where the two families meet near a diagonal a z-line post gives way to an
     * x-line post close by. Every test reads only the terrain and fields around the candidate
     * (and, for posts inside the cell, the cell's own ruins and signs), so the posts of a lit
     * section near a cell border are the same whichever cell computes them.
     * <p>
     * Posts just outside the cell are worked out too, though the neighbouring cell places them:
     * of two posts closer than {@value #LAMP_MIN_GAP} blocks only one stays, and a lit run of
     * fewer than {@value RuinSites#MIN_FOUND_POSTS} posts is dropped as a scrap of a lit patch
     * unless it carries on across the border.
     */
    private List<LightStretch> lamps(List<Node> nodes, List<TrailRuin> ruins, List<Signpost> signs) {
        // a post stands at most 10 blocks from its crossing, and a z-line crossing looks for
        // x-line crossings within 14 blocks: x lines are scanned 14 blocks further out
        int reach = LAMP_REACH;
        int xReach = reach + 14;
        List<LampCrossing> xs = new ArrayList<>();
        for (int k = Math.floorDiv(x0 - xReach, LAMP_LINE); k <= Math.floorDiv(x0 + CELL + xReach - 1, LAMP_LINE); k++) {
            for (int[] c : crossings(true, k * LAMP_LINE, z0 - xReach, z0 + CELL + xReach)) {
                double[] t = lineTangent(true, c);
                if (t != null && Math.abs(t[0]) >= Math.abs(t[1])) {
                    xs.add(new LampCrossing(c[0], c[1], t[0], t[1], k, true));
                }
            }
        }
        List<LampCrossing> all = new ArrayList<>();
        for (LampCrossing x : xs) {
            if (inCell(x.x, x.z, -reach)) {
                all.add(x);
            }
        }
        for (int k = Math.floorDiv(z0 - reach, LAMP_LINE); k <= Math.floorDiv(z0 + CELL + reach - 1, LAMP_LINE); k++) {
            for (int[] c : crossings(false, k * LAMP_LINE, x0 - reach, x0 + CELL + reach)) {
                double[] t = lineTangent(false, c);
                if (t == null || Math.abs(t[1]) <= Math.abs(t[0])) {
                    continue;
                }
                boolean taken = false;
                for (LampCrossing x : xs) {
                    taken |= Math.hypot(x.x - c[0], x.z - c[1]) < LAMP_LINE * 0.6;
                }
                if (!taken) {
                    all.add(new LampCrossing(c[0], c[1], t[0], t[1], k, false));
                }
            }
        }
        List<LightStretch.Post> own = new ArrayList<>();
        List<LightStretch.Post> foreign = new ArrayList<>();
        for (LampCrossing lc : all) {
            if (inCell(lc.x, lc.z, 0)) {
                sites.stats.incrementAndGet(ST_LAMP_CROSSINGS);
            }
            LightStretch.Post p = lampPost(lc, nodes, ruins, signs);
            if (p != null) {
                (inCell(p.x(), p.z(), 0) ? own : foreign).add(p);
            }
        }
        return stretches(spaced(own, foreign), foreign);
    }

    /** How far beyond the cell lamp-line crossings are worked out. */
    private static final int LAMP_REACH = 32;
    /** Fewest blocks between two lamp posts. */
    public static final int LAMP_MIN_GAP = 8;

    /**
     * The cell's posts without those that stand too close to another: of two posts closer than
     * {@value #LAMP_MIN_GAP} blocks the one with the lower seed stays. Posts just outside the cell
     * take part, so the neighbouring cell, which sees the same two posts, makes the same choice.
     */
    private static List<LightStretch.Post> spaced(List<LightStretch.Post> own, List<LightStretch.Post> foreign) {
        List<LightStretch.Post> out = new ArrayList<>();
        for (LightStretch.Post p : own) {
            boolean yields = false;
            for (List<LightStretch.Post> list : List.of(own, foreign)) {
                for (LightStretch.Post q : list) {
                    if (q != p && Math.hypot(q.x() - p.x(), q.z() - p.z()) < LAMP_MIN_GAP && before(q, p)) {
                        yields = true;
                    }
                }
            }
            if (!yields) {
                out.add(p);
            }
        }
        return out;
    }

    /** Whether post a takes precedence over post b: the lower seed, then the lower column. */
    private static boolean before(LightStretch.Post a, LightStretch.Post b) {
        double ua = SeedMixer.unit(a.seed());
        double ub = SeedMixer.unit(b.seed());
        return ua != ub ? ua < ub : a.x() != b.x() ? a.x() < b.x() : a.z() < b.z();
    }

    /**
     * Tangent of the trail where it crosses a lamp line, from its centre on the parallel lines
     * three blocks either side, or {@code null} if the trail is lost there.
     */
    private double[] lineTangent(boolean alongZ, int[] c) {
        int line = alongZ ? c[0] : c[1];
        int pos = alongZ ? c[1] : c[0];
        int[] at = new int[2];
        for (int s = 0; s < 2; s++) {
            int l = line + (s == 0 ? -3 : 3);
            double best = Double.MAX_VALUE;
            for (int p = pos - 6; p <= pos + 6; p++) {
                double d = alongZ ? m.trailDistance(l, p) : m.trailDistance(p, l);
                if (d < best) {
                    best = d;
                    at[s] = p;
                }
            }
            if (best > 2.0) {
                return null;
            }
        }
        double da = 6.0;
        double db = at[1] - at[0];
        double len = Math.hypot(da, db);
        return alongZ ? new double[]{da / len, db / len} : new double[]{db / len, da / len};
    }

    /**
     * The post of a lamp-line crossing, or {@code null} where the trail is not lit or there is no
     * good spot. The post stands 3 to 6 blocks from the centre of the trail on the side the
     * section keeps to, in the first column off the path whose post and lantern fit inside one
     * chunk (local x and z between 1 and 14: the arm and anything that fell reach one block, and
     * the planner keeps a fallen post inside the chunk). Where the crossing itself has no such
     * column, the post moves a block or two along the trail, which keeps the spacing even.
     */
    private LightStretch.Post lampPost(LampCrossing lc, List<Node> nodes, List<TrailRuin> ruins, List<Signpost> signs) {
        if (!sites.lit(lc.x, lc.z) || !TrailGeometry.laid(m, lc.x, lc.z)) {
            return null;
        }
        boolean counted = inCell(lc.x, lc.z, 0);
        if (counted) {
            sites.stats.incrementAndGet(ST_LAMP_LIT);
        }
        for (Node n : nodes) {
            double d = Math.hypot(n.x - lc.x, n.z - lc.z);
            if (n.junction ? d < 20 : d < 10) {
                count(counted, ST_LAMP_NODE);
                return null;
            }
        }
        // posts keep to one side over long runs; in some sections they alternate sides
        int side = sites.lampSide(lc.x, lc.z);
        if (sites.alternating(lc.x, lc.z) && (lc.line & 1) == 1) {
            side = -side;
        }
        // the side the section keeps to first; where a ruin, a slope or a bend leaves no room, the
        // post stands across the path
        int refused = ST_LAMP_NO_SPOT;
        for (int pass = 0; pass < 2; pass++) {
            int sd = pass == 0 ? side : -side;
            double nx = -lc.tz * sd;
            double nz = lc.tx * sd;
            int toTrail = RuinCell.dirOf(-nx, -nz);
            for (int slide : LAMP_SLIDES) {
                double bx = lc.x + lc.tx * slide;
                double bz = lc.z + lc.tz * slide;
                int verdict = 0;
                for (int o = 3; o <= 7 && verdict == 0; o++) {
                    int x = (int) Math.round(bx + nx * o);
                    int z = (int) Math.round(bz + nz * o);
                    double td = m.trailDistance(x, z);
                    if (td < 3.2) {
                        continue;
                    }
                    if (td > 6.5) {
                        break;
                    }
                    if (!lampColumn(x, z)) {
                        refused = Math.max(refused, ST_LAMP_OWN);
                        continue;
                    }
                    if (TrailGeometry.slope(m, x, z) > 2.0 || !TrailGeometry.land(m.sample(x & ~3, z & ~3), sl) || lampWater(x, z, toTrail)) {
                        refused = Math.max(refused, ST_LAMP_GROUND);
                        verdict = 1;
                        continue;
                    }
                    // a post outside the cell is the neighbouring cell's, which tests it against its own ruins and signs
                    boolean own = inCell(x, z, 0);
                    if (own && nearAny(ruins, x, z, 4)) {
                        refused = Math.max(refused, ST_LAMP_RUIN);
                        verdict = 1;
                        continue;
                    }
                    if (own) {
                        for (Signpost s : signs) {
                            if (Math.abs(s.x - x) < 8 && Math.abs(s.z - z) < 8) {
                                count(counted, ST_LAMP_SIGN);
                                return null;
                            }
                        }
                    }
                    ColumnSample s = new ColumnSample();
                    s.copyFrom(m.sample(x & ~3, z & ~3));
                    int biome = sites.biome(x, (int) s.height, z, s);
                    if (!RuinBiomes.trailVisible(biome)) {
                        count(counted, ST_LAMP_BIOME);
                        return null;
                    }
                    int climate = RuinSites.climateClass(s, biome, sl);
                    return sites.lampPost(x, z, toTrail, climate, biome, s, (lc.line % 3 + 3) % 3 == 1);
                }
            }
        }
        count(counted, refused);
        return null;
    }

    /**
     * Whether water lies where a lamp post or its parts would stand: the post, the lantern over
     * the path edge, the ground beside it, and the line a fallen post lies along (away from the
     * trail). The water solver digs even weak streams, which the coarse land test misses.
     */
    private boolean lampWater(int x, int z, int toTrail) {
        int dx = RuinCell.DX[toTrail];
        int dz = RuinCell.DZ[toTrail];
        if (water(x, z) || water(x + dx, z + dz) || water(x + dz, z + dx) || water(x - dz, z - dx)) {
            return true;
        }
        for (int k = 1; k <= 4; k++) {
            if (water(x - dx * k, z - dz * k)) {
                return true;
            }
        }
        return false;
    }

    /** Offsets along the trail at which a lamp post is tried, nearest the crossing first. */
    private static final int[] LAMP_SLIDES = {0, 1, -1, 2, -2, 3, -3};

    /** Whether a lamp post at a column fits inside its chunk with a block to spare all round. */
    static boolean lampColumn(int x, int z) {
        int lx = x & 15;
        int lz = z & 15;
        return lx >= 1 && lx <= 14 && lz >= 1 && lz <= 14;
    }

    private void count(boolean counted, int slot) {
        if (counted) {
            sites.stats.incrementAndGet(slot);
        }
    }

    /**
     * Groups the cell's posts into stretches: posts within three line spacings of each other
     * (one post may be missing beside a ruin or on a bank) light one stretch. A run of fewer than
     * {@value RuinSites#MIN_FOUND_POSTS} posts, counting those just across the border, is a scrap
     * where a trail grazes a lit patch and is left dark, unless it carries on into the next cell.
     */
    private List<LightStretch> stretches(List<LightStretch.Post> own, List<LightStretch.Post> foreign) {
        if (own.isEmpty()) {
            return List.of();
        }
        own.sort((a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
        // runs over the cell's posts and those just outside it
        List<LightStretch.Post> both = new ArrayList<>(own);
        both.addAll(foreign);
        int[] runOf = runs(both);
        int[] size = new int[both.size()];
        boolean[] open = new boolean[both.size()];
        for (int i = 0; i < both.size(); i++) {
            size[runOf[i]]++;
            open[runOf[i]] |= i >= own.size();
        }
        List<LightStretch.Post> kept = new ArrayList<>();
        for (int i = 0; i < own.size(); i++) {
            if (size[runOf[i]] >= RuinSites.MIN_FOUND_POSTS || open[runOf[i]]) {
                kept.add(own.get(i));
            }
        }
        int n = kept.size();
        int[] group = runs(kept);
        List<LightStretch> out = new ArrayList<>();
        boolean[] done = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (done[i]) {
                continue;
            }
            List<LightStretch.Post> posts = new ArrayList<>();
            for (int j = i; j < n; j++) {
                if (!done[j] && group[j] == group[i]) {
                    posts.add(kept.get(j));
                    done[j] = true;
                }
            }
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (LightStretch.Post p : posts) {
                minX = Math.min(minX, p.x() - 3);
                minZ = Math.min(minZ, p.z() - 3);
                maxX = Math.max(maxX, p.x() + 3);
                maxZ = Math.max(maxZ, p.z() + 3);
            }
            // the stretch is found at its middle post: the one nearest the mean of them all
            double mx = 0;
            double mz = 0;
            for (LightStretch.Post p : posts) {
                mx += p.x() / (double) posts.size();
                mz += p.z() / (double) posts.size();
            }
            LightStretch.Post mid = posts.get(0);
            for (LightStretch.Post p : posts) {
                if (Math.hypot(p.x() - mx, p.z() - mz) < Math.hypot(mid.x() - mx, mid.z() - mz)) {
                    mid = p;
                }
            }
            int y = (int) Math.floor(m.interpolatedHeight(mid.x(), mid.z())) + 2;
            out.add(new LightStretch(mid.x(), y, mid.z(), mid.seed(), minX, minZ, maxX, maxZ, posts.toArray(new LightStretch.Post[0])));
            sites.stats.incrementAndGet(ST_LIGHTS);
            sites.stats.addAndGet(ST_POSTS, posts.size());
        }
        return out;
    }

    /** The run each post belongs to (the index of a representative): posts within three line spacings chain. */
    private static int[] runs(List<LightStretch.Post> posts) {
        int n = posts.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                LightStretch.Post a = posts.get(i);
                LightStretch.Post b = posts.get(j);
                if (Math.hypot(a.x() - b.x(), a.z() - b.z()) <= LAMP_LINE * 3.0) {
                    parent[find(parent, i)] = find(parent, j);
                }
            }
        }
        for (int i = 0; i < n; i++) {
            parent[i] = find(parent, i);
        }
        return parent;
    }
}
