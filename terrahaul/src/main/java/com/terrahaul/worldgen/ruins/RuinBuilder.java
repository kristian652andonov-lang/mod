package com.terrahaul.worldgen.ruins;

import static com.terrahaul.worldgen.noise.TMath.clamp;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * What the planners of trailside ruins ({@link HousePlanner}, {@link LandmarkPlanner},
 * {@link WaysidePlanner}) have in common: a {@link Canvas} in the site's canonical frame sized from
 * its {@link RuinSpec}, the site's random stream and noise fields, and the pieces ruins are made of.
 * <p>
 * The main piece is the {@link Body}: a rectangular building drawn from a footprint and ruined by
 * a few coherent processes, the way abandoned buildings really decay:
 * <ol>
 *   <li>walls lose height along the perimeter following smooth noise plus one or two breaches
 *       (stepped V-notches, never straight cuts); corners and chimneys stand longest, and at least
 *       a quarter of every perimeter stays near full height so the building still reads as one.
 *       A collapsed wall section falls to its footing over a long run and lies outside as a mound
 *       of rubble; fire takes timber and logs down to their stone plinth but leaves stone shells
 *       standing; a ruin reduced to its foundations keeps only its chimney stack;</li>
 *   <li>the roof falls in patches; a roof cell only stays if a chain of roof cells connects it to
 *       a wall that survived to full height, so no roof piece hangs in the air. Upper floors rot
 *       the same way and their boards lie on the floor below;</li>
 *   <li>floors rot to earth where the sky is open; fallen roof pieces and beams lie inside and
 *       masonry rubble spills outward below the gaps;</li>
 *   <li>moss grows on low and wet stone in coherent patches, vines hang from wall tops, leaves
 *       overgrow corners and crowns, grass and ferns colonise the earth floor.</li>
 * </ol>
 * The finished plan goes through {@link RuinPlan#prune()}, the final guarantee that nothing floats.
 * Every choice comes from the site seed, so all chunks of a ruin draw the same thing.
 */
abstract class RuinBuilder {
    // wall styles
    static final int STONE_WALLS = 0;
    static final int TIMBER = 1;
    static final int LOGS = 2;
    static final int PLANK_WALLS = 3;
    // roofs
    static final int GABLE_U = 0;
    static final int GABLE_V = 1;
    static final int FLAT = 2;
    static final int PYRAMID = 3;
    static final int PARAPET = 4;
    static final int NO_ROOF = 5;
    // floors
    static final int FLOOR_PLANKS = 0;
    static final int FLOOR_STONE = 1;
    static final int FLOOR_MUD = 2;
    static final int FLOOR_EARTH = 3;

    static final SimplexNoise NOISE = new SimplexNoise(0x52554E53L);

    final TrailRuin site;
    final RuinSpec spec;
    final SplittableRandom rnd;
    final Canvas c;
    final double wet;
    final double o1;
    final double o2;
    final double o3;
    /** Hot, dry country: dead bushes instead of grass, no vines. */
    final boolean hot;
    double decay;
    /** Extra overgrowth on top of what wetness and age give: an overgrown ruin. */
    double growth;
    /** Cells (u, v) at floor level that already hold a prop or must stay free. */
    final List<int[]> reserved = new ArrayList<>();
    /** Where the footpath starts in front of the door (further out past a porch or a ladder). */
    int pathStart = -1;
    boolean drawFootpath = true;
    /** Chance of a weathered name board where the footpath leaves the trail. */
    double nameBoard = 0.35;

    RuinBuilder(TrailRuin site) {
        this.site = site;
        this.spec = site.spec;
        this.rnd = new SplittableRandom(site.seed);
        this.wet = site.wetness;
        this.decay = site.decay;
        this.o1 = SeedMixer.unit(site.seed * 31L) * 200.0;
        this.o2 = SeedMixer.unit(site.seed * 37L) * 200.0;
        this.o3 = SeedMixer.unit(site.seed * 41L) * 200.0;
        this.hot = site.climate == RuinSites.HOT_DRY;
        // the canvas is the site's box: the spec's box with a block all round, reaching back to the
        // trail where a footpath leads there (a bridge's path length is its span, not a footpath)
        int pathV = spec.footpath() ? -site.pathLength : spec.minV();
        this.c = new Canvas(spec.minU() - 1, Math.min(spec.minV(), pathV) - 1, spec.maxU() + 1, spec.maxV() + 1, -6, spec.hw() + 12);
    }

    /** Draws the footpath, converts the canvas into a world plan and prunes everything that would float. */
    RuinPlan finish() {
        if (spec.footpath() && drawFootpath) {
            footpath();
        }
        RuinPlan plan = c.toPlan(site.frame, RuinPlan.ABSOLUTE, site.floorY, site.materials, false);
        plan.prune();
        return plan;
    }

    // =========================================================================================
    // Noise helpers: coherent fields offset per site, so no two ruins share a pattern
    // =========================================================================================

    double n2(double a, double b) {
        return NOISE.noise2(a + o1, b + o2);
    }

    double n3(double a, double b, double cc) {
        return NOISE.noise3(a + o2, b + o3, cc + o1);
    }

    boolean chance(double p) {
        return rnd.nextDouble() < p;
    }

    /** Overgrowth vigour: wet sites, older and overgrown ruins are greener. */
    double vigour() {
        return Math.min(1.2, clamp(wet, 0.05, 1.0) * (0.45 + 0.55 * decay) + 0.6 * growth);
    }

    static boolean stoneLike(RuinPart p) {
        return p == RuinPart.FOUNDATION || p == RuinPart.MASONRY || p == RuinPart.QUOIN || p == RuinPart.CHIMNEY
            || p == RuinPart.MASONRY_STAIR || p == RuinPart.MASONRY_SLAB || p == RuinPart.MASONRY_WALL;
    }

    // =========================================================================================
    // The building body: walls, openings, floors, roof, ruined
    // =========================================================================================

    /**
     * One rectangular building drawn from a footprint, with its own wall heights. Footprint rows
     * run from the front (v = 0) to the back: C corner, # wall, W window (two high on tall walls),
     * w small window, D doorway, H hearth with the chimney stack outside, . floor, , earth floor.
     */
    final class Body {
        final int ou;
        final int ov;
        final String[] rows;
        final int w;
        final int d;
        final int hw;
        final int style;
        final int roof;
        /** Floor course height (raised houses stand on a platform). */
        final int y0;
        boolean thatch;
        boolean plinth;
        boolean burned;
        boolean tower;
        boolean foundationsOnly;
        int floorKind = FLOOR_PLANKS;
        /** Upper floor levels above the floor course (two storeys, tower floors). */
        int[] levels = {};
        /** An inner wall across the body at this u (-1: none), with a doorway at {@link #partitionDoor}. */
        int partition = -1;
        int partitionDoor = -1;
        double wallDecay = decay;
        double roofDecay = decay;
        double floorDecay = decay;
        /** Perimeter index around which a whole wall section collapsed (-1: none), and its half width. */
        int collapse = -1;
        int collapseRadius = 2;
        /** The side whose middle collapsed (0 front, 1 right, 2 back, 3 left; -1: none), resolved in {@link #build()}. */
        int collapseSide = -1;
        /** A ladder up to the upper floors: the floors leave a hole for it. */
        int ladderU = -1;
        int ladderV = -1;
        int[] pu;
        int[] pv;
        int[] full;
        int[] h;

        Body(int ou, int ov, String[] rows, int hw, int style, int roof, int y0) {
            this.ou = ou;
            this.ov = ov;
            this.rows = rows;
            this.w = rows[0].length();
            this.d = rows.length;
            this.hw = hw;
            this.style = style;
            this.roof = roof;
            this.y0 = y0;
        }

        char at(int u, int v) {
            return u < 0 || v < 0 || u >= w || v >= d ? ' ' : rows[v].charAt(u);
        }

        boolean corner(int u, int v) {
            return (u == 0 || u == w - 1) && (v == 0 || v == d - 1);
        }

        /** Index of a perimeter cell, or -1. */
        int index(int u, int v) {
            for (int i = 0; i < pu.length; i++) {
                if (pu[i] == u && pv[i] == v) {
                    return i;
                }
            }
            return -1;
        }

        /** Surviving height of the wall at (u, v), relative to the floor course. */
        int h(int u, int v) {
            int i = index(u, v);
            return i < 0 ? 0 : h[i];
        }

        /** Outward direction of a perimeter cell (canonical), for non-corner cells. */
        int out(int u, int v) {
            if (v == 0) {
                return RuinCell.NORTH;
            }
            if (v == d - 1) {
                return RuinCell.SOUTH;
            }
            return u == 0 ? RuinCell.WEST : RuinCell.EAST;
        }

        /** Rise of the gable roof row at {@code k} across {@code n} cells (0 at the eaves). */
        int rise(int k, int n) {
            return k < 0 || k >= n ? 0 : Math.min(k + 1, n - k);
        }

        int fullHeight(int u, int v) {
            int top = hw;
            if (roof == GABLE_U && (u == 0 || u == w - 1)) {
                top = hw + rise(v, d) - 1;
            } else if (roof == GABLE_V && (v == 0 || v == d - 1)) {
                top = hw + rise(u, w) - 1;
            } else if (roof == PARAPET) {
                // a parapet with merlons on the corners and in the middle of each side
                boolean merlon = corner(u, v) || u == w / 2 || v == d / 2;
                top = hw + (merlon ? 2 : 1);
            }
            if (at(u, v) == 'H') {
                int ridge = roof == GABLE_U ? hw + rise(v, d) : roof == GABLE_V ? hw + rise(u, w) : hw;
                top = burned || foundationsOnly ? Math.max(6, hw + 2) + rnd.nextInt(3) : ridge + 1 + (ridge > hw + 2 ? 0 : 1);
            }
            return top;
        }

        /** Whether fire took this body's walls down (stone walls survive a fire as a shell). */
        boolean burnedDown() {
            return burned && style != STONE_WALLS;
        }

        void build() {
            int per = 2 * (w + d) - 4;
            pu = new int[per];
            pv = new int[per];
            int k = 0;
            for (int u = 0; u < w; u++) {
                pu[k] = u;
                pv[k++] = 0;
            }
            for (int v = 1; v < d; v++) {
                pu[k] = w - 1;
                pv[k++] = v;
            }
            for (int u = w - 2; u >= 0; u--) {
                pu[k] = u;
                pv[k++] = d - 1;
            }
            for (int v = d - 2; v >= 1; v--) {
                pu[k] = 0;
                pv[k++] = v;
            }
            full = new int[per];
            for (int i = 0; i < per; i++) {
                full[i] = fullHeight(pu[i], pv[i]);
            }
            if (collapseSide >= 0 && collapse < 0) {
                int mu = collapseSide == 1 ? w - 1 : collapseSide == 3 ? 0 : w / 2;
                int mv = collapseSide == 0 ? 0 : collapseSide == 2 ? d - 1 : d / 2;
                collapse = index(mu, mv);
                collapseRadius = Math.max(1, (collapseSide % 2 == 0 ? w : d) / 3);
            }
            heights();
            floor();
            walls();
            upperFloors();
            interiorAir();
            int r = foundationsOnly || burned ? NO_ROOF : roof;
            switch (r) {
                case GABLE_U, GABLE_V -> gable();
                case FLAT -> flat();
                case PYRAMID -> pyramid();
                case PARAPET -> towerTop();
                default -> {
                }
            }
            if (tower) {
                towerFloors();
            }
            roofDebris();
            rubble();
            weather(this);
        }

        /** Surviving wall heights: smooth loss along the perimeter, breaches, sturdy corners. */
        private void heights() {
            int per = pu.length;
            double d0 = wallDecay;
            for (int attempt = 0; attempt < 4; attempt++) {
                h = new int[per];
                int minH = burnedDown() || foundationsOnly ? 0 : d0 < 0.8 ? 1 : 0;
                for (int i = 0; i < per; i++) {
                    char ch = at(pu[i], pv[i]);
                    if (foundationsOnly) {
                        h[i] = ch == 'H' ? full[i] : n2(i / 2.5, 9.0) > 0.3 ? 0 : 1;
                        continue;
                    }
                    if (burnedDown()) {
                        // the stone plinth of a timber frame survives the fire, a charred course or two of logs
                        int base = style == TIMBER ? 2 : plinth ? 1 : 0;
                        h[i] = ch == 'H' ? full[i] : Math.min(full[i], base + (n2(i / 2.5, 9.0) > 0.2 ? 0 : 1));
                        continue;
                    }
                    double n = 0.5 + 0.55 * n2(i / 5.0, 3.1) + 0.25 * n2(i / 2.0 - 7.3, 11.7);
                    double cv = smoothstep(0.3, 0.7, n);
                    // corners, chimneys, log walls and thick tower walls outlast plain walls
                    double k = corner(pu[i], pv[i]) ? 0.6 : ch == 'H' ? 0.3 : tower ? 0.65 : style == LOGS ? 0.8 : 1.0;
                    // whole courses fall: a lightly decayed wall keeps its top unless the noise is high
                    h[i] = Math.max(minH, full[i] - (int) Math.floor(full[i] * d0 * cv * k + 0.25));
                }
                if (!burnedDown() && !foundationsOnly) {
                    int breaches = d0 < 0.45 ? 0 : d0 < 0.7 ? 1 : 2;
                    for (int b = 0; b < breaches; b++) {
                        int centre = (int) (SeedMixer.unit(SeedMixer.hash2(site.seed, attempt, 200 + b)) * per);
                        int r = 1 + (int) (2.0 * SeedMixer.unit(SeedMixer.hash2(site.seed, attempt, 210 + b)));
                        notch(centre, r, minH, 1.3);
                    }
                    if (collapse >= 0) {
                        // a whole run of wall fell outward: down to its footing, ramping up at both ends
                        notch(collapse, collapseRadius, 0, 1.0);
                    }
                    // corners stand longest, unless a breach went through them
                    for (int i = 0; i < per; i++) {
                        if (corner(pu[i], pv[i]) && h[i] >= hw - 3 && SeedMixer.unit(SeedMixer.hash2(site.seed, i, 300)) < 0.6
                            && !inCollapse(i)) {
                            h[i] = Math.max(h[i], Math.min(full[i], hw - 1));
                        }
                    }
                    // no lone spikes: a column stands at most one block above its higher neighbour
                    for (int pass = 0; pass < 2; pass++) {
                        for (int i = 0; i < per; i++) {
                            if (at(pu[i], pv[i]) == 'H' || corner(pu[i], pv[i])) {
                                continue;
                            }
                            int l = h[(i + per - 1) % per];
                            int r = h[(i + 1) % per];
                            h[i] = Math.min(h[i], Math.max(l, r) + 1);
                        }
                    }
                }
                int tall = 0;
                for (int i = 0; i < per; i++) {
                    if (h[i] >= Math.min(full[i], hw) - 1) {
                        tall++;
                    }
                }
                if (burnedDown() || foundationsOnly || tall * 4 >= per || collapse >= 0 && attempt == 3) {
                    break;
                }
                d0 *= 0.85;
            }
            // nothing may stand above an opening that lost one of its carrying neighbours
            for (int i = 0; i < pu.length; i++) {
                for (int[] range : openings(at(pu[i], pv[i]))) {
                    int top = range[1];
                    if (h[i] > top) {
                        int l = h[(i + pu.length - 1) % pu.length];
                        int r = h[(i + 1) % pu.length];
                        if (Math.min(l, r) <= top) {
                            h[i] = top;
                            break;
                        }
                    }
                }
            }
        }

        /** Caps the wall heights around a perimeter index to a V-notch of the given slope. */
        private void notch(int centre, int r, int minH, double slope) {
            int per = pu.length;
            for (int i = 0; i < per; i++) {
                if (at(pu[i], pv[i]) == 'H') {
                    continue;
                }
                int dist = Math.min(Math.abs(i - centre), per - Math.abs(i - centre));
                int cap = Math.max(minH, (int) Math.floor(slope * Math.max(0, dist - r)));
                h[i] = Math.min(h[i], cap);
            }
        }

        boolean inCollapse(int i) {
            if (collapse < 0) {
                return false;
            }
            int per = pu.length;
            int dist = Math.min(Math.abs(i - collapse), per - Math.abs(i - collapse));
            return dist <= collapseRadius + 1;
        }

        /** The {bottom, top} ranges an opening of this kind cuts into the wall, one per storey. */
        int[][] openings(char ch) {
            int lo;
            int hi;
            switch (ch) {
                case 'D' -> {
                    return new int[][]{{1, 2}};
                }
                case 'W' -> {
                    lo = 2;
                    hi = hw >= 4 ? 3 : 2;
                }
                case 'w' -> {
                    lo = 2;
                    hi = 2;
                }
                default -> {
                    return new int[0][];
                }
            }
            int[][] out = new int[1 + levels.length][];
            out[0] = new int[]{lo, hi};
            for (int k = 0; k < levels.length; k++) {
                int l = levels[k];
                out[k + 1] = l + hi < hw ? new int[]{l + lo, l + hi} : new int[]{l + lo, l + lo};
            }
            return out;
        }

        private void floor() {
            for (int u = 0; u < w; u++) {
                for (int v = 0; v < d; v++) {
                    int cu = ou + u;
                    int cv = ov + v;
                    char ch = at(u, v);
                    boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
                    if (y0 == 0) {
                        c.setFill(cu, cv, RuinCell.of(RuinPart.FOUNDATION));
                    }
                    int cell;
                    if (edge) {
                        cell = RuinCell.of(y0 > 0 ? RuinPart.PLANKS : RuinPart.FOUNDATION);
                    } else if (ch == ',' || foundationsOnly || burned || floorKind == FLOOR_EARTH) {
                        cell = RuinCell.withAux(RuinCell.of(RuinPart.SOIL), burned ? 1 : 0);
                        if (y0 > 0) {
                            cell = RuinCell.of(RuinPart.PLANKS);
                        }
                    } else if (floorKind == FLOOR_STONE) {
                        cell = RuinCell.of(RuinPart.FOUNDATION);
                    } else if (floorKind == FLOOR_MUD) {
                        cell = RuinCell.of(RuinPart.PACKED_MUD);
                    } else {
                        cell = RuinCell.of(RuinPart.FLOOR);
                        // rotten planks give way to earth
                        if (y0 == 0 && n2(u / 2.5 + 40.0, v / 2.5) + decay > 1.1) {
                            cell = RuinCell.of(RuinPart.SOIL);
                        }
                    }
                    c.set(cu, y0, cv, cell);
                }
            }
        }

        private void walls() {
            for (int i = 0; i < pu.length; i++) {
                int u = pu[i];
                int v = pv[i];
                char ch = at(u, v);
                int cu = ou + u;
                int cv = ov + v;
                boolean corner = corner(u, v);
                boolean alongU = v == 0 || v == d - 1;
                for (int y = 1; y <= h[i]; y++) {
                    int cell;
                    if (ch == 'H') {
                        cell = RuinCell.of(RuinPart.CHIMNEY);
                    } else if (inOpening(ch, y)) {
                        cell = RuinCell.of(RuinPart.AIR);
                    } else {
                        cell = wallCell(u, v, y, corner, alongU);
                    }
                    c.set(cu, y0 + y, cv, cell);
                }
                // a broken masonry top steps down towards the lower neighbour
                if (h[i] < full[i] && h[i] >= 1 && RuinCell.part(c.get(cu, y0 + h[i], cv)) == RuinPart.MASONRY && !corner) {
                    int per = pu.length;
                    int li = (i + per - 1) % per;
                    int ri = (i + 1) % per;
                    double r = SeedMixer.unit(SeedMixer.hash2(site.seed, i, 400));
                    if (r < 0.35) {
                        int hi = h[li] > h[ri] ? li : ri;
                        int dir = RuinCell.dirOf(pu[hi] - u, pv[hi] - v);
                        c.set(cu, y0 + h[i], cv, RuinCell.of(RuinPart.MASONRY_STAIR, dir));
                    } else if (r < 0.6) {
                        c.set(cu, y0 + h[i], cv, RuinCell.of(RuinPart.MASONRY_SLAB));
                    }
                }
                if (ch == 'H') {
                    chimneyStack(i, cu, cv);
                }
                openings(i, ch, cu, cv);
            }
            // log cabins: the logs of alternate courses run past the corners (saddle notches)
            if (style == LOGS) {
                for (int i = 0; i < pu.length; i++) {
                    int u = pu[i];
                    int v = pv[i];
                    if (!corner(u, v)) {
                        continue;
                    }
                    for (int y = 1; y <= h[i]; y++) {
                        if (plinth && y <= 1) {
                            continue;
                        }
                        boolean even = (y & 1) == 0;
                        int du = even ? (u == 0 ? -1 : 1) : 0;
                        int dv = even ? 0 : (v == 0 ? -1 : 1);
                        int axis = even ? RuinCell.AXIS_X : RuinCell.AXIS_Z;
                        c.add(ou + u + du, y0 + y, ov + v + dv, RuinCell.withAxis(log(), axis));
                    }
                }
            }
            if (partition > 0) {
                partitionWall();
            }
        }

        boolean inOpening(char ch, int y) {
            for (int[] r : openings(ch)) {
                if (y >= r[0] && y <= r[1]) {
                    return true;
                }
            }
            return false;
        }

        int log() {
            return RuinCell.withAux(RuinCell.of(RuinPart.LOG), burned ? 1 : 0);
        }

        private int wallCell(int u, int v, int y, boolean corner, boolean alongU) {
            int axisAlong = alongU ? RuinCell.AXIS_X : RuinCell.AXIS_Z;
            int log = log();
            switch (style) {
                case TIMBER -> {
                    if (y <= 2) {
                        return RuinCell.of(corner ? RuinPart.QUOIN : RuinPart.MASONRY);
                    }
                    boolean storey = false;
                    for (int l : levels) {
                        storey |= y == l;
                    }
                    if (corner || at(u - 1, v) == 'D' || at(u + 1, v) == 'D' || at(u, v - 1) == 'D' || at(u, v + 1) == 'D'
                        || (alongU ? u : v) % 4 == 0 && y < hw) {
                        return log;
                    }
                    if (y == hw && !(roof == GABLE_U && !alongU) || storey) {
                        return RuinCell.withAxis(log, axisAlong);
                    }
                    return RuinCell.of(RuinPart.PLANKS);
                }
                case LOGS -> {
                    if (plinth && y <= 1) {
                        return RuinCell.of(corner ? RuinPart.QUOIN : RuinPart.MASONRY);
                    }
                    if (corner) {
                        return RuinCell.withAxis(log, (y & 1) == 0 ? RuinCell.AXIS_X : RuinCell.AXIS_Z);
                    }
                    return RuinCell.withAxis(log, axisAlong);
                }
                case PLANK_WALLS -> {
                    return corner ? log : RuinCell.of(RuinPart.PLANKS);
                }
                default -> {
                    return RuinCell.of(corner && site.materials.stone != RuinMaterials.MUD ? RuinPart.QUOIN : RuinPart.MASONRY);
                }
            }
        }

        /** The inner wall between two rooms, with a doorway; it decays like the outer walls. */
        private void partitionWall() {
            int u = partition;
            for (int v = 1; v < d - 1; v++) {
                double n = 0.5 + 0.55 * n2(v / 3.0 + 120.0, 5.5);
                int top = Math.max(0, hw - (int) Math.floor(hw * wallDecay * smoothstep(0.3, 0.7, n) + 0.25));
                if (burnedDown() || foundationsOnly) {
                    top = Math.min(top, style == STONE_WALLS ? 1 : 0);
                }
                for (int y = 1; y <= top; y++) {
                    int cell;
                    if (v == partitionDoor && y <= 2) {
                        cell = RuinCell.of(RuinPart.AIR);
                    } else if (style == STONE_WALLS) {
                        cell = RuinCell.of(RuinPart.MASONRY);
                    } else if (style == LOGS) {
                        cell = RuinCell.withAxis(log(), RuinCell.AXIS_Z);
                    } else {
                        cell = RuinCell.of(RuinPart.PLANKS);
                    }
                    c.set(ou + u, y0 + y, ov + v, cell);
                }
            }
            reserve(ou + u - 1, ov + partitionDoor);
            reserve(ou + u + 1, ov + partitionDoor);
        }

        private void chimneyStack(int i, int cu, int cv) {
            int u = pu[i];
            int v = pv[i];
            int out = out(u, v);
            int su = cu + RuinCell.DX[out];
            int sv = cv + RuinCell.DZ[out];
            c.setFill(su, sv, RuinCell.of(RuinPart.FOUNDATION));
            c.set(su, y0, sv, RuinCell.of(RuinPart.FOUNDATION));
            for (int y = 1; y <= h[i]; y++) {
                c.set(su, y0 + y, sv, RuinCell.of(RuinPart.CHIMNEY));
            }
            // the hearth inside: a cold fire on a stone hearth
            int iu = cu - RuinCell.DX[out];
            int iv = cv - RuinCell.DZ[out];
            c.set(iu, y0, iv, RuinCell.of(RuinPart.FOUNDATION));
            if (!burned && h[i] >= 2) {
                c.set(cu, y0 + 1, cv, RuinCell.of(RuinPart.CAMPFIRE, RuinCell.opposite(out)) | (decay < 0.45 && chance(0.1) ? RuinCell.FLAG : 0));
            } else if (burned || foundationsOnly) {
                c.set(cu, y0 + 1, cv, RuinCell.of(RuinPart.CAMPFIRE, RuinCell.opposite(out)));
            }
            reserve(iu, iv);
        }

        private void openings(int i, char ch, int cu, int cv) {
            int u = pu[i];
            int v = pv[i];
            if (ch == 'D') {
                int inward = RuinCell.opposite(out(u, v));
                reserve(cu + RuinCell.DX[inward], cv + RuinCell.DZ[inward]);
                if (h[i] >= 2 && !burned) {
                    double r = rnd.nextDouble();
                    int hinge = rnd.nextInt(2);
                    if (r < 0.15 + 0.2 * (1.0 - decay)) {
                        int door = RuinCell.withAux(RuinCell.of(RuinPart.DOOR, inward), hinge) | (r < 0.1 ? 0 : RuinCell.FLAG);
                        c.set(cu, y0 + 1, cv, door);
                        c.set(cu, y0 + 2, cv, door | RuinCell.TOP);
                    } else if (chance(0.3)) {
                        // the door fell in and lies on the floor
                        c.set(cu + RuinCell.DX[inward], y0 + 1, cv + RuinCell.DZ[inward], RuinCell.of(RuinPart.TRAPDOOR, inward));
                    }
                }
                return;
            }
            if (ch != 'W' && ch != 'w') {
                return;
            }
            int out = out(u, v);
            boolean stone = style == STONE_WALLS;
            for (int[] range : openings(ch)) {
                int lo = range[0];
                int hi = Math.min(range[1], h[i]);
                if (hi < lo) {
                    continue;
                }
                double r = burned ? 0.99 : rnd.nextDouble();
                for (int y = lo; y <= hi; y++) {
                    int cell;
                    if (r < 0.3 * (1.0 - decay) + 0.1) {
                        cell = RuinCell.of(ch == 'w' && stone ? RuinPart.IRON_BARS : RuinPart.GLASS_PANE);
                    } else if (r < 0.45) {
                        // boarded up from the outside
                        cell = RuinCell.of(RuinPart.TRAPDOOR, RuinCell.opposite(out)) | RuinCell.FLAG | (y == hi ? RuinCell.TOP : 0);
                    } else if (r < 0.55) {
                        cell = RuinCell.of(RuinPart.COBWEB);
                    } else {
                        cell = RuinCell.of(RuinPart.AIR);
                    }
                    if (r >= 0.3 * (1.0 - decay) + 0.1 && r < 0.45 && y != hi) {
                        cell = RuinCell.of(RuinPart.AIR);
                    }
                    c.set(cu, y0 + y, cv, cell);
                }
                // open shutters beside some windows, one of them hanging askew
                if (ch == 'W' && style != STONE_WALLS && !burned && chance(0.25)) {
                    int side = RuinCell.clockwise(out);
                    for (int s = -1; s <= 1; s += 2) {
                        int su = cu + RuinCell.DX[out] + s * RuinCell.DX[side];
                        int sv = cv + RuinCell.DZ[out] + s * RuinCell.DZ[side];
                        if (chance(0.7) && h[i] >= hi) {
                            boolean askew = chance(0.25);
                            int facing = askew ? (s > 0 ? side : RuinCell.opposite(side)) : RuinCell.opposite(out);
                            c.add(su, y0 + hi, sv, RuinCell.of(RuinPart.TRAPDOOR, facing) | RuinCell.FLAG | RuinCell.TOP | RuinCell.SOFT);
                        }
                    }
                }
            }
        }

        /**
         * Upper floors: boards on joists that rot from the middle of the room outwards; what is
         * left clings to the walls, and the fallen boards lie on the floor below.
         */
        private void upperFloors() {
            for (int l : levels) {
                if (l >= hw) {
                    continue;
                }
                for (int u = 1; u < w - 1; u++) {
                    for (int v = 1; v < d - 1; v++) {
                        if (u == partition || ladderAt(u, v)) {
                            continue;
                        }
                        int edge = Math.min(Math.min(u - 1, w - 2 - u), Math.min(v - 1, d - 2 - v));
                        double keep = n2(u * 0.7 + l * 7.0, v * 0.7) + 0.55 - floorDecay * 1.2 - 0.12 * edge;
                        int cu = ou + u;
                        int cv = ov + v;
                        if (keep > 0.0 && !burned) {
                            c.set(cu, y0 + l, cv, RuinCell.of(RuinPart.FLOOR));
                        } else if (!reservedCell(cu, cv) && chance(0.3)) {
                            // the boards that fell, on the floor below
                            c.add(cu, y0 + 1, cv, chance(0.6) ? RuinCell.of(RuinPart.PLANK_SLAB)
                                : RuinCell.withAxis(RuinCell.of(burned ? RuinPart.LOG : RuinPart.STRIPPED_LOG),
                                rnd.nextInt(2) == 0 ? RuinCell.AXIS_X : RuinCell.AXIS_Z) | (burned ? 1 << 16 : 0));
                        }
                    }
                }
            }
        }

        /** The ladder hole of the upper floors (in towers up the middle of the back wall). */
        boolean ladderAt(int u, int v) {
            return tower ? u == w / 2 && v == d - 2 : u == ladderU && v == ladderV;
        }

        /** Clears the inside of the house up to the underside of its roof. */
        private void interiorAir() {
            for (int u = 1; u < w - 1; u++) {
                for (int v = 1; v < d - 1; v++) {
                    int ceiling = switch (roof) {
                        case GABLE_U -> hw + rise(v, d) - 1;
                        case GABLE_V -> hw + rise(u, w) - 1;
                        default -> hw;
                    };
                    for (int y = 1; y <= ceiling; y++) {
                        c.add(ou + u, y0 + y, ov + v, RuinCell.of(RuinPart.AIR));
                    }
                }
            }
        }

        /**
         * Pitched roof: rows of stairs (or thatch) rising to a ridge, with eave and gable overhangs.
         * It is carried in slices, like rafters: each half of a slice rests on its eave wall, which
         * must still stand to full height; a gable standing to its apex carries the end slices, and
         * purlins bridge a single missing slice. In a carried half, rows survive from the eave up to
         * the first rotten patch, the ridge only where both halves reach it. So a roof falls in where
         * the walls under it fell, and no piece of it hangs out over a gap.
         */
        private void gable() {
            boolean alongU = roof == GABLE_U;
            int across = alongU ? d : w;
            int along = alongU ? w : d;
            int mid = across / 2;
            boolean odd = (across & 1) == 1;
            boolean[][] carried = new boolean[along + 2][2];
            for (int a = 0; a < along; a++) {
                for (int side = 0; side < 2; side++) {
                    int kc = side == 0 ? 0 : across - 1;
                    boolean eave = h(alongU ? a : kc, alongU ? kc : a) >= hw;
                    boolean end = (a == 0 || a == along - 1) && gableStands(a, mid, alongU);
                    carried[a + 1][side] = eave || end;
                }
            }
            boolean[][] held = new boolean[along + 2][2];
            for (int side = 0; side < 2; side++) {
                for (int a = 0; a < along; a++) {
                    held[a + 1][side] = carried[a + 1][side] || a > 0 && a < along - 1 && carried[a][side] && carried[a + 2][side];
                }
                held[0][side] = held[1][side];
                held[along + 1][side] = held[along][side];
            }
            boolean[][] kept = new boolean[along + 2][across + 2];
            for (int a = -1; a <= along; a++) {
                for (int side = 0; side < 2; side++) {
                    if (!held[a + 1][side]) {
                        continue;
                    }
                    if (side == 0) {
                        for (int k = -1; k <= mid - 1; k++) {
                            if (!roofAlive(a, k, along)) {
                                break;
                            }
                            kept[a + 1][k + 1] = true;
                        }
                    } else {
                        for (int k = across; k >= (odd ? mid + 1 : mid); k--) {
                            if (!roofAlive(a, k, along)) {
                                break;
                            }
                            kept[a + 1][k + 1] = true;
                        }
                    }
                }
                if (odd && kept[a + 1][mid] && kept[a + 1][mid + 2] && roofAlive(a, mid, along)) {
                    kept[a + 1][mid + 1] = true;
                }
            }
            // stairs face up the slope, towards the ridge; an odd span ends in a slab ridge
            int upPlus = alongU ? RuinCell.SOUTH : RuinCell.EAST;
            for (int a = -1; a <= along; a++) {
                for (int k = -1; k <= across; k++) {
                    int u = alongU ? a : k;
                    int v = alongU ? k : a;
                    int y = y0 + hw + rise(k, across);
                    if (!kept[a + 1][k + 1]) {
                        continue;
                    }
                    int cell;
                    if (thatch) {
                        cell = RuinCell.withAxis(RuinCell.of(RuinPart.THATCH), alongU ? RuinCell.AXIS_X : RuinCell.AXIS_Z);
                    } else if (odd && k == mid) {
                        cell = RuinCell.of(RuinPart.ROOF_SLAB);
                    } else {
                        cell = RuinCell.of(RuinPart.ROOF_STAIR, k < mid ? upPlus : RuinCell.opposite(upPlus));
                    }
                    if (RuinCell.part(c.get(ou + u, y, ov + v)) != RuinPart.CHIMNEY) {
                        c.set(ou + u, y, ov + v, cell);
                    }
                }
            }
        }

        /** Whether the roof cell at slice {@code a}, row {@code k} has not rotted away. */
        private boolean roofAlive(int a, int k, int along) {
            double gableEnd = a <= 0 || a >= along - 1 ? 1.0 : 0.0;
            double rot = thatch ? 0.15 : 0.0;
            return (roofDecay + rot) * 1.25 + 0.45 * n2(a / 3.0 + 20.0, k / 3.0) - 0.35 * gableEnd <= 0.75;
        }

        /** Whether the gable wall at slice {@code a} still stands to its apex. */
        private boolean gableStands(int a, int mid, boolean alongU) {
            int u = alongU ? a : mid;
            int v = alongU ? mid : a;
            int i = index(u, v);
            return i >= 0 && h[i] >= full[i];
        }

        /** Flat adobe roof: vigas through the walls, a packed-mud deck and a parapet. */
        private void flat() {
            int top = y0 + hw;
            for (int u = 1; u < w - 1; u += 2) {
                boolean ends = h(u, 0) >= hw && h(u, d - 1) >= hw;
                if (!ends) {
                    continue;
                }
                for (int v = -1; v <= d; v++) {
                    if (v == -1 || v == d) {
                        if (chance(0.15 + roofDecay * 0.3)) {
                            continue;
                        }
                    }
                    c.set(ou + u, top, ov + v, RuinCell.withAxis(RuinCell.of(RuinPart.STRIPPED_LOG), RuinCell.AXIS_Z));
                }
            }
            for (int u = 1; u < w - 1; u++) {
                for (int v = 1; v < d - 1; v++) {
                    if (n2(u / 2.5 + 60.0, v / 2.5) > 0.9 * roofDecay - 0.45) {
                        c.set(ou + u, top + 1, ov + v, RuinCell.of(RuinPart.PACKED_MUD));
                    } else if (RuinCell.part(c.get(ou + u, top, ov + v)) != RuinPart.STRIPPED_LOG) {
                        c.set(ou + u, top, ov + v, RuinCell.of(RuinPart.AIR));
                    }
                }
            }
            for (int i = 0; i < pu.length; i++) {
                if (h[i] >= hw && !corner(pu[i], pv[i])) {
                    c.set(ou + pu[i], top + 1, ov + pv[i], RuinCell.of(RuinPart.MASONRY_WALL));
                }
            }
        }

        /** Pyramid roof of stair rings on a tower. */
        private void pyramid() {
            // ring 0 hangs beside the top course like eaves, ring 1 rests on the walls
            int y = y0 + hw;
            for (int ring = 0; ring < 4; ring++) {
                int lo = -1 + ring;
                int hiU = w - ring;
                int hiV = d - ring;
                if (lo > hiU || lo > hiV) {
                    break;
                }
                for (int u = lo; u <= hiU; u++) {
                    for (int v = lo; v <= hiV; v++) {
                        if (u != lo && u != hiU && v != lo && v != hiV) {
                            continue;
                        }
                        int dir = v == lo ? RuinCell.SOUTH : v == hiV ? RuinCell.NORTH : u == lo ? RuinCell.EAST : RuinCell.WEST;
                        boolean apex = lo == hiU || lo == hiV;
                        boolean edgeSupported = h(Math.max(0, Math.min(w - 1, u)), Math.max(0, Math.min(d - 1, v))) >= hw;
                        if (ring == 0 && !edgeSupported || n2(u / 2.0 + 80.0, v / 2.0 + ring) + 0.4 < roofDecay * 1.2) {
                            continue;
                        }
                        c.set(ou + u, y + ring, ov + v, apex ? RuinCell.of(RuinPart.ROOF_SLAB) : RuinCell.of(RuinPart.ROOF_STAIR, dir));
                    }
                }
                if (lo + 1 > hiU - 1) {
                    break;
                }
            }
            for (int u = 1; u < w - 1; u++) {
                for (int v = 1; v < d - 1; v++) {
                    for (int yy = y0 + hw + 1; yy < y0 + hw + 4; yy++) {
                        c.add(ou + u, yy, ov + v, RuinCell.of(RuinPart.AIR));
                    }
                }
            }
        }

        /** Battlemented top of a tower: a lookout deck, parapet and merlons. */
        private void towerTop() {
            int y = y0 + hw;
            for (int u = 1; u < w - 1; u++) {
                for (int v = 1; v < d - 1; v++) {
                    if (ladderAt(u, v)) {
                        c.set(ou + u, y, ov + v, RuinCell.of(RuinPart.TRAPDOOR, RuinCell.NORTH) | RuinCell.FLAG | RuinCell.TOP);
                    } else if (n2(u * 0.8 + 90.0, v * 0.8) + 0.3 > roofDecay) {
                        c.set(ou + u, y, ov + v, RuinCell.of(RuinPart.FLOOR));
                    } else {
                        c.set(ou + u, y, ov + v, RuinCell.of(RuinPart.AIR));
                    }
                }
            }
        }

        /** Plank floors inside a tower every four blocks, with a hole for the ladder. */
        private void towerFloors() {
            if (levels.length > 0) {
                return;
            }
            for (int level = 4; level < hw; level += 4) {
                for (int u = 1; u < w - 1; u++) {
                    for (int v = 1; v < d - 1; v++) {
                        if (ladderAt(u, v)) {
                            continue;
                        }
                        if (n2(u * 0.9 + level * 7.0, v * 0.9) + 0.35 > floorDecay) {
                            c.set(ou + u, y0 + level, ov + v, RuinCell.of(RuinPart.FLOOR));
                        }
                    }
                }
            }
        }

        /** Roof pieces that fell inside and a fallen beam or two. */
        private void roofDebris() {
            if (roof != GABLE_U && roof != GABLE_V) {
                return;
            }
            for (int u = 1; u < w - 1; u++) {
                for (int v = 1; v < d - 1; v++) {
                    if (!openSky(ou + u, ov + v, y0 + 1) || !c.empty(ou + u, y0 + 1, ov + v) || reservedCell(ou + u, ov + v)) {
                        continue;
                    }
                    double r = rnd.nextDouble();
                    if (burned || foundationsOnly) {
                        if (r < 0.1) {
                            c.set(ou + u, y0 + 1, ov + v, RuinCell.of(RuinPart.LITTER));
                        }
                    } else if (r < 0.14) {
                        c.set(ou + u, y0 + 1, ov + v, thatch ? RuinCell.of(RuinPart.HAY) : RuinCell.of(RuinPart.ROOF_SLAB));
                    } else if (r < 0.26 && !thatch) {
                        c.set(ou + u, y0 + 1, ov + v, RuinCell.of(RuinPart.ROOF_STAIR, rnd.nextInt(4)));
                    }
                }
            }
            if (roofDecay > 0.45 && style != STONE_WALLS || roofDecay > 0.6 || burned) {
                int beams = 1 + (roofDecay > 0.7 || burned ? 1 : 0) + (burned ? 1 : 0);
                for (int b = 0; b < beams; b++) {
                    int v = 1 + rnd.nextInt(Math.max(1, d - 2));
                    int len = Math.min(w - 2, 3 + rnd.nextInt(3));
                    int u0 = 1 + rnd.nextInt(Math.max(1, w - 1 - len));
                    boolean stripped = !burned && chance(0.5);
                    for (int u = u0; u < u0 + len; u++) {
                        if (c.empty(ou + u, y0 + 1, ov + v) && !reservedCell(ou + u, ov + v)) {
                            int beam = RuinCell.withAxis(RuinCell.of(stripped ? RuinPart.STRIPPED_LOG : RuinPart.LOG), RuinCell.AXIS_X);
                            c.set(ou + u, y0 + 1, ov + v, burned ? RuinCell.withAux(beam, 1) : beam);
                        }
                    }
                }
            }
        }

        /** Masonry, or planks from timber walls, spilled outward below the gaps; a mound where a wall collapsed. */
        private void rubble() {
            for (int i = 0; i < pu.length; i++) {
                int lost = Math.min(full[i], hw) - h[i];
                if (lost < 2 || corner(pu[i], pv[i]) || foundationsOnly) {
                    continue;
                }
                if (burnedDown()) {
                    // charred timbers fell inward and outward
                    if (chance(0.25)) {
                        int out = out(pu[i], pv[i]);
                        int sign = chance(0.5) ? 1 : -1;
                        c.decal(ou + pu[i] + RuinCell.DX[out] * sign, ov + pv[i] + RuinCell.DZ[out] * sign, 1,
                            RuinCell.withAxis(RuinCell.withAux(RuinCell.of(RuinPart.LOG), 1), (pv[i] == 0 || pv[i] == d - 1) ? RuinCell.AXIS_X
                                : RuinCell.AXIS_Z));
                    }
                    continue;
                }
                int out = out(pu[i], pv[i]);
                boolean mound = inCollapse(i);
                int reach = mound ? 3 : 2;
                for (int dist = 1; dist <= reach; dist++) {
                    double p = mound ? (dist == 1 ? 0.95 : dist == 2 ? 0.7 : 0.35)
                        : dist == 1 ? Math.min(0.8, 0.2 * lost) : Math.min(0.5, 0.1 * lost);
                    if (!chance(p)) {
                        continue;
                    }
                    int u = ou + pu[i] + RuinCell.DX[out] * dist;
                    int v = ov + pv[i] + RuinCell.DZ[out] * dist;
                    c.decal(u, v, 1, mound && dist == 1 ? RuinCell.of(style == STONE_WALLS || style == TIMBER ? RuinPart.MASONRY : RuinPart.PLANKS)
                        : rubbleCell(i));
                    if (mound && dist == 1 && chance(0.55)) {
                        c.decal(u, v, 2, rubbleCell(i));
                    }
                }
                // and some fell inside
                if (chance(mound ? 0.7 : 0.35)) {
                    int u = ou + pu[i] - RuinCell.DX[out];
                    int v = ov + pv[i] - RuinCell.DZ[out];
                    if (c.empty(u, y0 + 1, v) && !reservedCell(u, v)) {
                        c.set(u, y0 + 1, v, rubbleCell(i));
                    }
                }
            }
        }

        private int rubbleCell(int i) {
            double r = rnd.nextDouble();
            boolean timberTop = style == TIMBER || style == PLANK_WALLS;
            if (style == LOGS || timberTop && r < 0.5) {
                boolean alongU = pv[i] == 0 || pv[i] == d - 1;
                return r < 0.25 ? RuinCell.of(RuinPart.PLANK_SLAB)
                    : RuinCell.withAxis(RuinCell.of(style == LOGS ? RuinPart.LOG : RuinPart.STRIPPED_LOG), alongU ? RuinCell.AXIS_X : RuinCell.AXIS_Z);
            }
            if (r < 0.4) {
                return RuinCell.of(RuinPart.MASONRY_SLAB);
            }
            if (r < 0.75) {
                return RuinCell.withAux(RuinCell.of(RuinPart.ROCK, rnd.nextInt(4)), rnd.nextInt(2));
            }
            return RuinCell.of(RuinPart.MASONRY);
        }
    }

    /** Moss and cracks on the stone of a body in coherent patches, more of it low down and on wet sites. */
    void weather(Body b) {
        weather(b.ou - 2, b.ov - 2, b.ou + b.w + 1, b.ov + b.d + 1, b.y0, b.y0 + b.hw + 6, b.y0);
    }

    /** Moss and cracks on all stone in a canvas box. */
    void weather(int u0, int v0, int u1, int v1, int ya, int yb, int ground) {
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                for (int y = ya; y <= yb; y++) {
                    int cell = c.get(u, y, v);
                    if (!stoneLike(RuinCell.part(cell)) || RuinCell.weathering(cell) != RuinCell.PLAIN) {
                        continue;
                    }
                    double moss = n3(u / 4.0, y / 4.0, v / 4.0) + 0.35 * (y - ground <= 2 ? 1 : 0) + 0.45 * (wet - 0.5) + 0.25 * decay
                        + 0.45 * growth;
                    if (moss > 0.42) {
                        c.set(u, y, v, RuinCell.withWeathering(cell, RuinCell.MOSSY));
                    } else if (NOISE.noise3(u / 3.5 + o3, y / 3.5 - o1, v / 3.5) > 0.5 - 0.2 * decay) {
                        c.set(u, y, v, RuinCell.withWeathering(cell, RuinCell.CRACKED));
                    }
                }
            }
        }
    }

    // =========================================================================================
    // Shared helpers
    // =========================================================================================

    /** True when nothing structural lies above (u, y, v) inside the canvas. */
    boolean openSky(int u, int v, int y) {
        for (int yy = y; yy < c.minY + c.sizeY; yy++) {
            if (c.structural(u, yy, v)) {
                return false;
            }
        }
        return true;
    }

    void reserve(int u, int v) {
        reserved.add(new int[]{u, v});
    }

    boolean reservedCell(int u, int v) {
        for (int[] r : reserved) {
            if (r[0] == u && r[1] == v) {
                return true;
            }
        }
        return false;
    }

    /**
     * A free floor cell against a wall, in seeded order: {u, v, facing into the room}. Cells in
     * front of doors and hearths stay free.
     */
    int[] propCell(Body b, boolean backOnly) {
        List<int[]> cells = new ArrayList<>();
        for (int u = 1; u < b.w - 1; u++) {
            for (int v = 1; v < b.d - 1; v++) {
                boolean wall = u == 1 || u == b.w - 2 || v == 1 || v == b.d - 2 || b.partition > 0 && Math.abs(u - b.partition) == 1;
                if (!wall || backOnly && v != b.d - 2 || u == b.partition) {
                    continue;
                }
                int cu = b.ou + u;
                int cv = b.ov + v;
                if (reservedCell(cu, cv) || !c.empty(cu, b.y0 + 1, cv)) {
                    continue;
                }
                RuinPart floor = c.part(cu, b.y0, cv);
                if (!floor.structural) {
                    continue;
                }
                int facing = v == b.d - 2 ? RuinCell.NORTH : u == 1 ? RuinCell.EAST : u == b.w - 2 ? RuinCell.WEST : v == 1 ? RuinCell.SOUTH
                    : u < b.partition ? RuinCell.WEST : RuinCell.EAST;
                cells.add(new int[]{cu, cv, facing});
            }
        }
        if (cells.isEmpty()) {
            return null;
        }
        int[] pick = cells.get(rnd.nextInt(cells.size()));
        reserve(pick[0], pick[1]);
        return pick;
    }

    /** A chest (always with the site's loot) or a barrel (half of them with loot). */
    void container(int u, int y, int v, int facing, boolean barrel) {
        container(u, y, v, facing, barrel, site.lootTable);
    }

    void container(int u, int y, int v, int facing, boolean barrel, String loot) {
        if (barrel) {
            c.set(u, y, v, RuinCell.of(RuinPart.BARREL, RuinCell.NORTH) | RuinCell.TOP);
        } else {
            c.set(u, y, v, RuinCell.of(RuinPart.CHEST, facing));
        }
        if (loot != null && (!barrel || chance(0.5))) {
            c.extras.add(new RuinPlan.Extra(u, y, v, false, loot, rnd.nextLong(), null));
        }
    }

    /** A container on the ground (relative to the ground of its column), with loot. */
    void groundContainer(int u, int v, int facing, boolean barrel, String loot) {
        c.decal(u, v, 1, barrel ? RuinCell.of(RuinPart.BARREL, facing) : RuinCell.of(RuinPart.CHEST, facing));
        if (loot != null) {
            c.extras.add(new RuinPlan.Extra(u, 1, v, true, loot, rnd.nextLong(), null));
        }
    }

    /** Grass, ferns and flowers on open earth floors, mushrooms in the shade. */
    void floorPlants(Body b) {
        double vig = vigour();
        for (int u = 1; u < b.w - 1; u++) {
            for (int v = 1; v < b.d - 1; v++) {
                int cu = b.ou + u;
                int cv = b.ov + v;
                int floor = c.get(cu, b.y0, cv);
                if (RuinCell.part(floor) != RuinPart.SOIL) {
                    continue;
                }
                boolean open = openSky(cu, cv, b.y0 + 1);
                if (!open && RuinCell.aux(floor) == 0) {
                    c.set(cu, b.y0, cv, RuinCell.withAux(floor, n2(cu * 0.5, cv * 0.5) > 0 ? 1 : 3));
                } else if (open && growth > 0.3 && n2(cu / 3.0 + 31.0, cv / 3.0) > 0.35) {
                    c.set(cu, b.y0, cv, RuinCell.withAux(floor, 4));
                }
                if (!c.empty(cu, b.y0 + 1, cv) || reservedCell(cu, cv)) {
                    continue;
                }
                double r = rnd.nextDouble();
                int plant = 0;
                if (open) {
                    if ((wet + growth) > 0.55 && n2(cu / 3.0 + 7.0, cv / 3.0) > 0.62 - 0.45 * (wet + growth)) {
                        plant = RuinCell.of(RuinPart.MOSS_CARPET);
                    } else if (r < 0.45 * (0.5 + vig)) {
                        plant = RuinCell.of(hot ? RuinPart.DEAD_BUSH : RuinPart.SHORT_GRASS);
                        if (hot && r > 0.08) {
                            plant = 0;
                        }
                    } else if (r < 0.58 + 0.15 * growth && !hot) {
                        plant = RuinCell.of(wet > 0.5 ? RuinPart.FERN : RuinPart.TALL_GRASS);
                    } else if (r < 0.62 + 0.15 * growth) {
                        plant = RuinCell.withAux(RuinCell.of(RuinPart.FLOWER), rnd.nextInt(6));
                    } else if (r < 0.72 + 0.1 * growth) {
                        plant = RuinCell.of(RuinPart.LITTER);
                    }
                } else if (wet > 0.5 && r < 0.06) {
                    plant = RuinCell.withAux(RuinCell.of(RuinPart.MUSHROOM), rnd.nextInt(2));
                } else if (r < 0.1) {
                    plant = RuinCell.of(RuinPart.LITTER);
                }
                if (plant != 0) {
                    c.set(cu, b.y0 + 1, cv, plant);
                    if (RuinCell.part(plant) == RuinPart.TALL_GRASS && c.empty(cu, b.y0 + 2, cv)) {
                        c.set(cu, b.y0 + 2, cv, plant | RuinCell.TOP);
                    } else if (RuinCell.part(plant) == RuinPart.TALL_GRASS) {
                        c.set(cu, b.y0 + 1, cv, RuinCell.of(RuinPart.SHORT_GRASS));
                    }
                }
            }
        }
    }

    /** Vines on outer walls, leaves in corners and on broken crowns, moss on wall tops, cobwebs. */
    void overgrowth(Body b) {
        double vig = vigour();
        int per = b.pu.length;
        for (int i = 0; i < per; i++) {
            int u = b.pu[i];
            int v = b.pv[i];
            if (b.corner(u, v)) {
                continue;
            }
            int cu = b.ou + u;
            int cv = b.ov + v;
            int out = b.out(u, v);
            int hi = b.h[i];
            // vines hang from the wall top down the outer face
            if (hi >= 2 && n2(i / 3.0 + 50.0, 1.0) > 0.78 - 0.5 * vig && !hot) {
                int len = 1 + (int) ((hi - 1) * SeedMixer.unit(SeedMixer.hash2(site.seed, i, 500)));
                for (int y = hi; y > hi - len && y >= 1; y--) {
                    int wall = c.get(cu, b.y0 + y, cv);
                    if (wall == 0 || !RuinCell.part(wall).fullCube()) {
                        break;
                    }
                    int vu = cu + RuinCell.DX[out];
                    int vv = cv + RuinCell.DZ[out];
                    if (!c.empty(vu, b.y0 + y, vv)) {
                        break;
                    }
                    c.set(vu, b.y0 + y, vv, RuinCell.withAux(RuinCell.of(RuinPart.VINE), 1 << RuinCell.opposite(out)) | RuinCell.SOFT);
                }
            }
            // moss carpets and leafy crowns on broken wall tops under the open sky
            int top = b.y0 + hi + 1;
            if (hi >= 1 && c.empty(cu, top, cv) && openSky(cu, cv, top)) {
                RuinPart wallTop = c.part(cu, top - 1, cv);
                if (wallTop.fullCube() && wet + growth > 0.3 && n2(i / 4.0 + 70.0, 2.0) > 0.75 - 0.35 * (wet + growth)) {
                    c.set(cu, top, cv, RuinCell.of(RuinPart.MOSS_CARPET) | RuinCell.SOFT);
                } else if (wallTop.fullCube() && hi < b.full[i] && decay + growth > 0.45 && n2(i / 4.0 + 90.0, 3.0) > 0.85 - 0.4 * vig) {
                    c.set(cu, top, cv, RuinCell.of(RuinPart.LEAVES) | RuinCell.SOFT);
                    if (chance(0.4) && c.empty(cu, top + 1, cv)) {
                        c.set(cu, top + 1, cv, RuinCell.of(RuinPart.LEAVES) | RuinCell.SOFT);
                    }
                }
            }
            // bushes at the foot of the outer walls
            if (chance(0.07 + 0.12 * vig)) {
                c.decal(cu + RuinCell.DX[out], cv + RuinCell.DZ[out], 1, RuinCell.of(RuinPart.LEAVES));
            }
        }
        // leafy bushes in the inside corners
        int[][] corners = {{1, 1}, {b.w - 2, 1}, {1, b.d - 2}, {b.w - 2, b.d - 2}};
        int bushes = (int) Math.round(0.5 + 3.0 * vig);
        for (int[] k : corners) {
            if (bushes <= 0) {
                break;
            }
            int cu = b.ou + k[0];
            int cv = b.ov + k[1];
            if (reservedCell(cu, cv) || !c.empty(cu, b.y0 + 1, cv) || !openSky(cu, cv, b.y0 + 1) && decay < 0.6) {
                continue;
            }
            if (chance(0.6)) {
                c.set(cu, b.y0 + 1, cv, RuinCell.of(RuinPart.LEAVES));
                if (chance(0.4) && c.empty(cu, b.y0 + 2, cv)) {
                    c.set(cu, b.y0 + 2, cv, RuinCell.of(RuinPart.LEAVES));
                }
                bushes--;
            }
        }
        // cobwebs in the upper corners under what is left of the roof
        int webs = 1 + (int) Math.round(3 * decay);
        for (int[] k : corners) {
            if (webs <= 0) {
                break;
            }
            int cu = b.ou + k[0];
            int cv = b.ov + k[1];
            int y = b.y0 + b.hw;
            if (c.empty(cu, y, cv) && !openSky(cu, cv, y + 1) && chance(0.6)) {
                c.set(cu, y, cv, RuinCell.of(RuinPart.COBWEB));
                webs--;
            }
        }
    }

    // =========================================================================================
    // Outdoor pieces: fire rings, seats, wells, gardens, fences, rubble, plants
    // =========================================================================================

    /** A ring of stones around a cold campfire. */
    void ring(int cu, int cv, boolean lit) {
        c.decal(cu, cv, 1, RuinCell.of(RuinPart.CAMPFIRE, RuinCell.NORTH) | (lit ? RuinCell.FLAG : 0));
        int gaps = 1 + rnd.nextInt(2);
        int[][] around = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        int skip = rnd.nextInt(8);
        for (int k = 0; k < 8; k++) {
            if (k == skip || gaps > 1 && k == (skip + 4) % 8) {
                continue;
            }
            int stone = wet > 0.6 && chance(0.5) ? 1 : 0;
            c.decal(cu + around[k][0], cv + around[k][1], 1, RuinCell.withAux(RuinCell.of(RuinPart.ROCK, rnd.nextInt(4)), stone));
        }
    }

    /** A log seat two blocks long. */
    void logSeat(int u, int v, int axis) {
        int cell = RuinCell.withAxis(RuinCell.of(RuinPart.STRIPPED_LOG), axis);
        c.decal(u, v, 1, cell);
        int du = axis == RuinCell.AXIS_X ? 1 : 0;
        int dv = axis == RuinCell.AXIS_Z ? 1 : 0;
        c.decal(u + du, v + dv, 1, cell);
    }

    /** A fire ring with log seats beside a body, on its left or right side. */
    void campfireRing(Body b, boolean force, int side) {
        if (!force && !chance(0.35)) {
            return;
        }
        boolean west = side == 0 ? chance(0.5) : side < 0;
        int cu = west ? b.ou - 4 : b.ou + b.w + 3;
        int cv = b.ov + b.d / 2;
        ring(cu, cv, chance(0.1));
        logSeat(cu - 1, cv + (west ? -3 : 2), RuinCell.AXIS_X);
        if (chance(0.5)) {
            logSeat(west ? cu - 3 : cu + 2, cv - 1, RuinCell.AXIS_Z);
        }
    }

    /** A stack of logs against a wall, along u. */
    void woodpile(int u0, int v, int len) {
        for (int u = u0; u < u0 + len; u++) {
            c.decal(u, v, 1, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_Z));
            if (chance(0.5)) {
                c.decal(u, v, 2, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_Z));
            }
        }
    }

    /**
     * A well: a stone ring with a rim, a lined shaft and still water at the bottom. {@code frame}
     * adds the winch posts and their little roof, {@code broken} breaks the rim open.
     */
    void well(int cu, int cv, boolean frame, boolean broken) {
        for (int du = -1; du <= 1; du++) {
            for (int dv = -1; dv <= 1; dv++) {
                int u = cu + du;
                int v = cv + dv;
                boolean centre = du == 0 && dv == 0;
                c.setFill(u, v, RuinCell.of(RuinPart.FOUNDATION));
                for (int y = -4; y <= 0; y++) {
                    int cell;
                    if (!centre) {
                        cell = RuinCell.of(RuinPart.MASONRY);
                    } else if (y == -4) {
                        cell = RuinCell.of(RuinPart.MASONRY);
                    } else if (y <= -2) {
                        cell = RuinCell.of(RuinPart.WATER);
                    } else {
                        cell = RuinCell.of(RuinPart.AIR);
                    }
                    c.set(u, y, v, cell);
                }
                if (!centre) {
                    boolean gone = broken && n2(u * 0.9 + 21.0, v * 0.9) > 0.1;
                    boolean low = n2(u * 0.9 + 21.0, v * 0.9) + decay > 1.15;
                    if (!gone) {
                        c.set(u, 1, v, low || broken ? RuinCell.of(RuinPart.MASONRY_SLAB) : RuinCell.of(RuinPart.MASONRY_WALL));
                    }
                } else {
                    c.set(u, 1, v, RuinCell.of(RuinPart.AIR));
                }
                c.set(u, 2, v, RuinCell.of(RuinPart.AIR) | RuinCell.SOFT);
            }
        }
        if (frame) {
            // the winch frame: two posts and a little roof
            for (int y = 2; y <= 3; y++) {
                c.set(cu - 1, y, cv, RuinCell.of(RuinPart.FENCE));
                c.set(cu + 1, y, cv, RuinCell.of(RuinPart.FENCE));
            }
            c.set(cu - 1, 1, cv, RuinCell.of(RuinPart.MASONRY));
            c.set(cu + 1, 1, cv, RuinCell.of(RuinPart.MASONRY));
            for (int u = cu - 1; u <= cu + 1; u++) {
                c.set(u, 4, cv, RuinCell.of(RuinPart.PLANK_SLAB));
            }
            if (chance(0.5)) {
                c.set(cu, 3, cv, RuinCell.of(RuinPart.CHAIN));
            }
        }
        if (broken) {
            // the stones of the rim lie around it
            for (int k = 0; k < 4; k++) {
                int d = rnd.nextInt(4);
                c.decal(cu + RuinCell.DX[d] * 2 + (rnd.nextInt(3) - 1) * (RuinCell.DZ[d] != 0 ? 1 : 0),
                    cv + RuinCell.DZ[d] * 2 + (rnd.nextInt(3) - 1) * (RuinCell.DX[d] != 0 ? 1 : 0), 1,
                    chance(0.5) ? RuinCell.of(RuinPart.MASONRY_SLAB) : RuinCell.withAux(RuinCell.of(RuinPart.ROCK, rnd.nextInt(4)), 0));
            }
        }
    }

    /** A fenced kitchen garden, its fence half fallen in coherent runs. */
    void garden(int u0, int v0, int w, int d) {
        int gate = u0 + 1 + rnd.nextInt(w - 2);
        for (int u = u0; u < u0 + w; u++) {
            for (int v = v0; v < v0 + d; v++) {
                boolean edge = u == u0 || v == v0 || u == u0 + w - 1 || v == v0 + d - 1;
                if (edge) {
                    if (v == v0 && u == gate) {
                        continue;
                    }
                    if (n2(u * 0.35 + 3.0, v * 0.35 + 17.0) > decay - 0.35) {
                        c.decal(u, v, 1, RuinCell.of(RuinPart.FENCE));
                    }
                } else if ((v - v0) % 2 == 1) {
                    c.decal(u, v, 0, RuinCell.of(RuinPart.COARSE_DIRT));
                    if (chance(0.25)) {
                        c.decal(u, v, 1, RuinCell.of(hot ? RuinPart.DEAD_BUSH : RuinPart.SWEET_BERRY_BUSH));
                    }
                }
            }
        }
    }

    /**
     * What is left of a fence around a yard: runs of posts with gaps where it rotted, and an open
     * gateway towards the trail.
     */
    void fenceRemains(int u0, int v0, int u1, int v1, int gateU) {
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                boolean edge = u == u0 || u == u1 || v == v0 || v == v1;
                if (!edge || v == v0 && Math.abs(u - gateU) <= 1) {
                    continue;
                }
                if (n2(u * 0.3 + 140.0, v * 0.3) > decay * 0.9 - 0.3) {
                    c.decal(u, v, 1, RuinCell.of(RuinPart.FENCE));
                }
            }
        }
    }

    /** Something lost long ago, buried in the ground: suspicious gravel with archaeology loot. */
    void buried(int u, int v) {
        String loot = chance(0.1) ? "minecraft:archaeology/trail_ruins_rare" : "minecraft:archaeology/trail_ruins_common";
        c.decal(u, v, 0, RuinCell.of(RuinPart.SUSPICIOUS_GRAVEL));
        c.extras.add(new RuinPlan.Extra(u, 0, v, true, loot, rnd.nextLong(), null));
    }

    /** Masonry rubble, slabs and loose stones scattered over a box of ground. */
    void rubbleField(int u0, int v0, int u1, int v1, double density) {
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                if (!chance(density)) {
                    continue;
                }
                double r = rnd.nextDouble();
                int cell = r < 0.35 ? RuinCell.of(RuinPart.MASONRY_SLAB) : r < 0.8 ? RuinCell.withAux(RuinCell.of(RuinPart.ROCK, rnd.nextInt(4)),
                    wet > 0.6 && chance(0.4) ? 1 : 0) : RuinCell.of(RuinPart.MASONRY);
                c.decal(u, v, 1, cell);
            }
        }
    }

    /** Grass, ferns and flowers over a box of open ground (decals), denser on wet sites. */
    void meadow(int u0, int v0, int u1, int v1, double density) {
        double vig = vigour();
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                double r = rnd.nextDouble();
                if (r > density * (0.5 + vig)) {
                    continue;
                }
                int plant;
                if (hot) {
                    plant = chance(0.3) ? RuinCell.of(RuinPart.DEAD_BUSH) : 0;
                } else if (r < 0.1) {
                    plant = RuinCell.withAux(RuinCell.of(RuinPart.FLOWER), rnd.nextInt(6));
                } else if (r < 0.2 && wet > 0.5) {
                    plant = RuinCell.of(RuinPart.FERN);
                } else {
                    plant = RuinCell.of(RuinPart.SHORT_GRASS);
                }
                if (plant != 0) {
                    c.decal(u, v, 1, plant);
                }
            }
        }
    }

    /**
     * The trodden footpath from the door to the trail, and sometimes a weathered name board
     * where it leaves the trail.
     */
    void footpath() {
        int door = spec.doorU();
        for (int v = pathStart; v >= -site.pathLength; v--) {
            double n = n2(door * 0.3 + 55.0, v / 3.0);
            int u = door + (n > 0.45 ? 1 : n < -0.45 ? -1 : 0);
            c.decal(u, v, 0, RuinCell.of(n > 0.2 || n < -0.6 ? RuinPart.COARSE_DIRT : RuinPart.DIRT_PATH));
        }
        if (chance(nameBoard) && site.pathLength >= 4) {
            int v = -site.pathLength + 2;
            int u = door + 2;
            c.decal(u, v, 1, RuinCell.of(RuinPart.FENCE));
            c.decal(u, v, 2, RuinCell.of(RuinPart.STANDING_SIGN, RuinCell.NORTH));
            boolean blank = chance(0.35);
            String[] front = blank ? new String[]{"", "", "", ""} : boardText();
            c.extras.add(new RuinPlan.Extra(u, 2, v, true, null, 0L,
                new RuinPlan.SignSpec(front, new String[]{"", "", "", ""}, site.materials.darkWood(), false)));
        }
    }

    /** The text of the name board by the trail. */
    String[] boardText() {
        return new String[]{"", site.name, "", ""};
    }
}
