package com.terrahaul.worldgen.trees;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Shape grammars for every species. Each generator receives a size class and draws the tree into a
 * {@link TreeBlueprint} (plain Java, so the development tool {@code TreeStudy} can render them).
 * <p>
 * Broadleaf crowns are built backwards from their outline: leaf-cluster sites are scattered through
 * the crown's envelope (a lumpy ellipsoid, a flat slab for acacias, an umbrella for jungle giants),
 * then a branch system is grown to reach every site. In decurrent trees (oak, cherry, dark oak,
 * acacia, mangrove) the trunk divides into a few main limbs that each head for one group of sites
 * and fork again on the way, so limbs leave at natural angles, fork twice or more and thin out by
 * the pipe model; trees with a central leader (birch, jungle) send short branches from the trunk to
 * the sites instead. Every cluster sits on a twig tip, which keeps all leaves within six blocks of
 * wood. Spruces and firs are layered cones of whorled branches, each clothed in its own drooping
 * spray of needles; Scots pines carry rounded needle cushions on upswept, forking limbs, old ones as
 * a flat-topped umbrella high on a long, bare stem.
 * <p>
 * Trunks taper, lean, bow and twist a little, and stand on a ground-following root flare. Sizes are
 * chosen so that every tree stays within {@link TreeBlueprint#MAX_REACH} of its trunk, and ancient
 * trees within {@link TreeBlueprint#ANCIENT_REACH}; the development tool reports any clipping.
 */
public final class TreeShapes {
    private static final V UP = new V(0, 1, 0);

    private TreeShapes() {
    }

    /** Builds a tree of the given species and size into a new blueprint. */
    public static TreeBlueprint create(TreeSpecies species, TreeSize size, RandomGenerator random) {
        TreeBlueprint bp = new TreeBlueprint(species, random, size == TreeSize.ANCIENT ? TreeBlueprint.ANCIENT_REACH : TreeBlueprint.MAX_REACH);
        build(bp, size);
        return bp;
    }

    public static void build(TreeBlueprint bp, TreeSize size) {
        if (size == TreeSize.ANCIENT) {
            switch (bp.species()) {
                case OAK -> ancientOak(bp, false);
                case SWAMP_OAK -> ancientOak(bp, true);
                case DARK_OAK -> ancientDarkOak(bp);
                case SPRUCE -> spruce(bp, TreeSize.ANCIENT);
                case PINE -> pine(bp, TreeSize.ANCIENT);
                case JUNGLE -> jungle(bp, TreeSize.ANCIENT);
                case ACACIA -> acacia(bp, TreeSize.ANCIENT);
                case CHERRY -> cherry(bp, TreeSize.ANCIENT);
                default -> build(bp, TreeSize.GIANT);
            }
            return;
        }
        switch (bp.species()) {
            case OAK -> oak(bp, size, false);
            case SWAMP_OAK -> oak(bp, size, true);
            case BIRCH -> birch(bp, size);
            case SPRUCE -> spruce(bp, size);
            case PINE -> pine(bp, size);
            case FIR -> fir(bp, size);
            case JUNGLE -> jungle(bp, size);
            case ACACIA -> acacia(bp, size);
            case DARK_OAK -> darkOak(bp, size);
            case MANGROVE -> mangrove(bp, size);
            case CHERRY -> cherry(bp, size);
            case AZALEA -> azalea(bp, size);
            case DEAD -> dead(bp, size);
            case DEAD_SPRUCE -> deadSpruce(bp, size);
            case BURNED -> burned(bp, size);
        }
    }

    // =========================================================================================
    // Geometry helpers
    // =========================================================================================

    private record V(double x, double y, double z) {
        V add(V o) {
            return new V(x + o.x, y + o.y, z + o.z);
        }

        V sub(V o) {
            return new V(x - o.x, y - o.y, z - o.z);
        }

        V mul(double s) {
            return new V(x * s, y * s, z * s);
        }

        double len() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        V norm() {
            double l = len();
            return l < 1e-9 ? UP : mul(1.0 / l);
        }

        V cross(V o) {
            return new V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
        }

        double dist2(V o) {
            double dx = x - o.x;
            double dy = y - o.y;
            double dz = z - o.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static int range(RandomGenerator r, int min, int max) {
        return min + r.nextInt(max - min + 1);
    }

    private static double range(RandomGenerator r, double min, double max) {
        return min + r.nextDouble() * (max - min);
    }

    private static double angle(RandomGenerator r) {
        return r.nextDouble() * Math.PI * 2.0;
    }

    private static V horizontal(double a, double len) {
        return new V(Math.cos(a) * len, 0, Math.sin(a) * len);
    }

    private static V centroid(List<V> pts) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (V p : pts) {
            x += p.x;
            y += p.y;
            z += p.z;
        }
        return new V(x / pts.size(), y / pts.size(), z / pts.size());
    }

    /** A random unit vector perpendicular to {@code n}. */
    private static V perpendicular(V n, RandomGenerator r) {
        V a = Math.abs(n.y) < 0.9 ? n.cross(UP).norm() : n.cross(new V(1, 0, 0)).norm();
        V b = n.cross(a).norm();
        double t = angle(r);
        return a.mul(Math.cos(t)).add(b.mul(Math.sin(t)));
    }

    // -----------------------------------------------------------------------------------------
    // Trunks
    // -----------------------------------------------------------------------------------------

    /** A trunk as one centre point and radius per layer, from the base (layer 0) to the top. */
    private static final class Trunk {
        final double[] cx;
        final double[] cz;
        final double[] r;

        Trunk(int layers) {
            cx = new double[layers];
            cz = new double[layers];
            r = new double[layers];
        }

        int top() {
            return cx.length - 1;
        }

        /**
         * The trunk centre at height {@code y} (clamped to the trunk). Branches start at whole
         * layers, where this is exactly the centre of the drawn layer, so they always begin inside
         * a trunk block.
         */
        V at(double y) {
            double yy = Math.max(0.0, Math.min(top(), y));
            int i = (int) Math.floor(yy);
            int j = Math.min(top(), i + 1);
            double f = yy - i;
            return new V(cx[i] + (cx[j] - cx[i]) * f, y, cz[i] + (cz[j] - cz[i]) * f);
        }

        double radius(double y) {
            int i = (int) Math.max(0, Math.min(top(), Math.round(y)));
            return r[i];
        }

        /** The direction the trunk grows in at its top. */
        V topDirection() {
            int t = top();
            int b = Math.max(0, t - 3);
            return new V(cx[t] - cx[b], Math.max(1, t - b), cz[t] - cz[b]).norm();
        }

        /**
         * Draws the layers. Where the centre shifts sideways from one layer to the next, a joint
         * runs along the upper layer from above the old centre to the new one, so a crooked trunk
         * stays face-connected instead of touching only at edges.
         */
        void draw(TreeBlueprint bp) {
            draw(bp, 0);
        }

        /** Draws the layers from {@code from} up (mangrove trunks start on their root cluster). */
        void draw(TreeBlueprint bp, int from) {
            for (int y = from; y <= top(); y++) {
                bp.trunkLayer(cx[y], y, cz[y], r[y]);
                if (y == from) {
                    continue;
                }
                int px = TreeBlueprint.round(cx[y - 1]);
                int pz = TreeBlueprint.round(cz[y - 1]);
                int x = TreeBlueprint.round(cx[y]);
                int z = TreeBlueprint.round(cz[y]);
                while (px != x || pz != z) {
                    bp.log(px, y, pz, TreeBlueprint.LOG_Y);
                    if (px != x) {
                        px += Integer.signum(x - px);
                    } else {
                        pz += Integer.signum(z - pz);
                    }
                }
            }
        }
    }

    /**
     * A trunk up to layer {@code top} standing at {@code (c, 0, c)}: it leans by {@code lean} blocks
     * in total, bows sideways by up to {@code bend} blocks and sways irregularly by up to
     * {@code wobble} blocks (gnarled trunks), and narrows from {@code r0} at the foot to {@code r1}
     * at the top, faster near the ground like the trunk of an old tree.
     */
    private static Trunk trunk(RandomGenerator r, int top, double c, double r0, double r1, double lean, double bend, double wobble) {
        Trunk t = new Trunk(Math.max(1, top + 1));
        double la = angle(r);
        double ba = la + (r.nextBoolean() ? 1 : -1) * range(r, 0.9, 2.2);
        double p1 = angle(r);
        double p2 = angle(r);
        double f1 = range(r, 0.45, 0.7);
        double f2 = range(r, 0.35, 0.6);
        int n = t.top();
        for (int y = 0; y <= n; y++) {
            double s = (double) y / Math.max(1, n);
            double off = lean * Math.pow(s, 1.4);
            double bow = bend * Math.sin(Math.PI * s);
            double wx = wobble * (Math.sin(y * f1 + p1) - Math.sin(p1));
            double wz = wobble * (Math.sin(y * f2 + p2) - Math.sin(p2));
            t.cx[y] = c + Math.cos(la) * off + Math.cos(ba) * bow + wx;
            t.cz[y] = c + Math.sin(la) * off + Math.sin(ba) * bow + wz;
            t.r[y] = r1 + (r0 - r1) * Math.pow(1.0 - s, 1.6);
        }
        return t;
    }

    // -----------------------------------------------------------------------------------------
    // Crowns
    // -----------------------------------------------------------------------------------------

    /** Draws a leaf cluster (or other ending) at a twig tip; {@code dir} is the twig's direction. */
    @FunctionalInterface
    private interface Leafer {
        void grow(TreeBlueprint bp, V tip, V dir);
    }

    /**
     * Branching style.
     *
     * @param follow    how far a limb first continues its parent's direction before turning to its goal (0..1)
     * @param arch      upward bow of limbs as a fraction of their length (negative: sagging)
     * @param gnarl     sideways kink of limbs as a fraction of their length
     * @param forkFrac  where between its start and its group of sites a limb forks again
     * @param pipe      radius split exponent (0.5: area-preserving pipe model; lower keeps limbs thicker)
     * @param mainLimbs number of main limbs the trunk divides into
     */
    private record Style(double follow, double arch, double gnarl, double forkFrac, double pipe, int mainLimbs) {
    }

    /** Approximate number of clusters of radius {@code rc} needed to clothe an ellipsoidal crown. */
    private static int clusterCount(double rx, double ry, double rc, double coverage) {
        double p = 1.6075;
        double a = Math.pow(rx * ry, p);
        double b = Math.pow(rx * rx, p);
        double area = 4.0 * Math.PI * Math.pow((2.0 * a + b) / 3.0, 1.0 / p);
        return Math.max(1, (int) Math.round(coverage * area / (Math.PI * rc * rc)));
    }

    /**
     * Scatters up to {@code count} cluster sites through an ellipsoidal crown envelope with a lumpy
     * outline, at least {@code spacing} apart.
     *
     * @param shellMin minimum normalised distance from the centre (sites sit in the outer shell)
     * @param bottom   minimum normalised height (cuts off the crown's underside)
     * @param hollow   raises that cut towards the middle, so the crown's underside arches like an
     *                 umbrella over the limbs instead of hiding them
     * @param lump     amplitude of the outline's lobes
     * @param margin   distance kept from the blueprint's reach
     */
    private static List<V> crownSites(TreeBlueprint bp, V c, double rx, double ry, int count, double spacing, double shellMin, double bottom,
                                      double hollow, double lump, double margin) {
        RandomGenerator r = bp.random();
        double p1 = angle(r);
        double p2 = angle(r);
        double limit = bp.reach() - margin;
        double s2 = spacing * spacing;
        List<V> pts = new ArrayList<>();
        int attempts = count * 40;
        while (pts.size() < count && attempts-- > 0) {
            double u = r.nextDouble() * 2.0 - 1.0;
            double v = r.nextDouble() * 2.0 - 1.0;
            double w = r.nextDouble() * 2.0 - 1.0;
            double d2 = u * u + v * v + w * w;
            if (d2 > 1.0 || d2 < shellMin * shellMin || v < bottom + hollow * (1.0 - u * u - w * w)) {
                continue;
            }
            double a = Math.atan2(w, u);
            double k = 1.0 + lump * (0.6 * Math.sin(2.0 * a + p1) + 0.4 * Math.sin(3.0 * a + p2));
            V p = new V(c.x + u * rx * k, c.y + v * ry, c.z + w * rx * k);
            if (Math.abs(p.x) > limit || Math.abs(p.z) > limit) {
                continue;
            }
            boolean free = true;
            for (V q : pts) {
                if (p.dist2(q) < s2) {
                    free = false;
                    break;
                }
            }
            if (free) {
                pts.add(p);
            }
        }
        return pts;
    }

    /** Groups points by proximity (k-means seeded with mutually distant points). */
    private static List<List<V>> cluster(List<V> pts, int k) {
        List<List<V>> groups = new ArrayList<>();
        if (k <= 1 || pts.size() <= 1) {
            groups.add(new ArrayList<>(pts));
            return groups;
        }
        k = Math.min(k, pts.size());
        V[] seeds = new V[k];
        V c = centroid(pts);
        for (int i = 0; i < k; i++) {
            V best = pts.get(0);
            double bestD = -1;
            for (V p : pts) {
                double d = Double.MAX_VALUE;
                if (i == 0) {
                    d = p.dist2(c);
                } else {
                    for (int j = 0; j < i; j++) {
                        d = Math.min(d, p.dist2(seeds[j]));
                    }
                }
                if (d > bestD) {
                    bestD = d;
                    best = p;
                }
            }
            seeds[i] = best;
        }
        int[] assign = new int[pts.size()];
        for (int it = 0; it < 6; it++) {
            for (int i = 0; i < pts.size(); i++) {
                double bestD = Double.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    double d = pts.get(i).dist2(seeds[j]);
                    if (d < bestD) {
                        bestD = d;
                        assign[i] = j;
                    }
                }
            }
            for (int j = 0; j < k; j++) {
                List<V> members = new ArrayList<>();
                for (int i = 0; i < pts.size(); i++) {
                    if (assign[i] == j) {
                        members.add(pts.get(i));
                    }
                }
                if (!members.isEmpty()) {
                    seeds[j] = centroid(members);
                }
            }
        }
        for (int j = 0; j < k; j++) {
            List<V> members = new ArrayList<>();
            for (int i = 0; i < pts.size(); i++) {
                if (assign[i] == j) {
                    members.add(pts.get(i));
                }
            }
            if (!members.isEmpty()) {
                groups.add(members);
            }
        }
        return groups;
    }

    /**
     * Draws a limb from {@code s} to {@code e} that leaves {@code s} partly along {@code dir} and
     * curves towards its goal, bowed and kinked by the style; returns its direction at the end.
     */
    private static V limbTo(TreeBlueprint bp, V s, V dir, V e, double r0, double r1, Style st) {
        RandomGenerator r = bp.random();
        V chord = e.sub(s);
        double len = chord.len();
        if (len < 0.4) {
            // still join the two blocks, which may differ although the points are close
            bp.path(s.x, s.y, s.z, e.x, e.y, e.z, (byte) 0);
            return dir;
        }
        V cn = chord.mul(1.0 / len);
        V start = dir.norm().mul(st.follow).add(cn.mul(1.0 - st.follow)).norm();
        V ctrl = s.add(start.mul(len * 0.5)).add(new V(0, st.arch * len, 0)).add(perpendicular(cn, r).mul(st.gnarl * len * r.nextDouble()));
        bp.curve(s.x, s.y, s.z, ctrl.x, ctrl.y, ctrl.z, e.x, e.y, e.z, r0, r1);
        return e.sub(ctrl).norm();
    }

    /**
     * Decurrent branching: from {@code s}, the sites are split into groups; a limb heads for each
     * group, forks part way and the process repeats until single twigs reach single sites.
     */
    private static void decurrent(TreeBlueprint bp, V s, V dir, List<V> sites, double radius, int depth, Style st, Leafer leafer) {
        int n = sites.size();
        if (n == 0) {
            return;
        }
        RandomGenerator r = bp.random();
        if (depth > 0 && n <= 2 || depth >= 5) {
            for (V t : sites) {
                double rr = radius * Math.pow(1.0 / n, st.pipe);
                V d = limbTo(bp, s, dir, t, rr, rr * 0.6, st);
                leafer.grow(bp, t, d);
            }
            return;
        }
        int k = depth == 0 ? Math.min(n, st.mainLimbs) : Math.min(n, n >= 7 ? 3 : 2);
        for (List<V> g : cluster(sites, k)) {
            double rc = radius * Math.pow(g.size() / (double) n, st.pipe);
            if (g.size() == 1) {
                V d = limbTo(bp, s, dir, g.get(0), rc, rc * 0.6, st);
                leafer.grow(bp, g.get(0), d);
                continue;
            }
            V c = centroid(g);
            V f = s.add(c.sub(s).mul(st.forkFrac * range(r, 0.8, 1.15)));
            V d = limbTo(bp, s, dir, f, rc, rc * 0.85, st);
            decurrent(bp, f, d, g, rc * 0.85, depth + 1, st, leafer);
        }
    }

    /**
     * Branches from a central leader: neighbouring sites share a branch that leaves the trunk below
     * them, rising at {@code elevation} radians, and forks into one twig per site.
     */
    private static void excurrent(TreeBlueprint bp, Trunk trunk, List<V> sites, double elevation, double minY, double maxY, double radius,
                                  Style st, Leafer leafer) {
        if (sites.isEmpty()) {
            return;
        }
        double tan = Math.tan(elevation);
        for (List<V> g : cluster(sites, Math.max(1, (sites.size() + 1) / 2))) {
            V c = centroid(g);
            V axis = trunk.at(c.y);
            double hd = Math.sqrt((c.x - axis.x) * (c.x - axis.x) + (c.z - axis.z) * (c.z - axis.z));
            double oy = Math.rint(Math.max(minY, Math.min(maxY, c.y - hd * tan)));
            V o = trunk.at(oy);
            V out = new V(c.x - o.x, 0, c.z - o.z).norm();
            V dir = out.mul(Math.cos(elevation)).add(new V(0, Math.sin(elevation), 0));
            if (g.size() == 1) {
                V d = limbTo(bp, o, dir, c, radius, 0, st);
                leafer.grow(bp, c, d);
            } else {
                V f = o.add(c.sub(o).mul(0.6));
                V d = limbTo(bp, o, dir, f, radius, radius * 0.7, st);
                for (V t : g) {
                    leafer.grow(bp, t, limbTo(bp, f, d, t, radius * 0.5, 0, st));
                }
            }
        }
    }

    /** Short hidden twigs inside a big cluster, so its far side stays within six blocks of wood. */
    private static void innerTwigs(TreeBlueprint bp, V tip, double r) {
        RandomGenerator rnd = bp.random();
        int n = 3;
        double a0 = angle(rnd);
        for (int i = 0; i < n; i++) {
            double a = a0 + i * Math.PI * 2.0 / n;
            V e = tip.add(horizontal(a, r * 0.5)).add(new V(0, r * 0.35, 0));
            bp.path(tip.x, tip.y, tip.z, e.x, e.y, e.z, (byte) 0);
        }
    }

    /** Round, puffy clusters sitting on their twig tips. */
    private static Leafer puffs(double rc, double flat, double density) {
        return (bp, tip, dir) -> {
            double rr = rc * range(bp.random(), 0.88, 1.12);
            bp.puff(tip.x, tip.y + rr * flat * 0.4, tip.z, rr, flat, density);
            if (rr > 2.9) {
                innerTwigs(bp, tip, rr);
            }
        };
    }

    /** Puffy clusters with hanging strands of leaves below (weeping crowns, cherry fringes). */
    private static Leafer weeping(double rc, double flat, double density, int strands, int maxLen) {
        Leafer puff = puffs(rc, flat, density);
        return (bp, tip, dir) -> {
            puff.grow(bp, tip, dir);
            RandomGenerator r = bp.random();
            // strands hang from the outer part of the cluster, most of them short
            for (int i = 0; i < strands; i++) {
                double a = angle(r);
                double d = rc * range(r, 0.5, 0.95);
                int len = r.nextBoolean() ? 1 : range(r, 1, maxLen);
                bp.strand(TreeBlueprint.round(tip.x + Math.cos(a) * d), TreeBlueprint.round(tip.y), TreeBlueprint.round(tip.z + Math.sin(a) * d), len);
            }
        };
    }

    /** Flat-bottomed cloud pads (acacia umbrellas). */
    private static Leafer pads(double pr, double thick, double density) {
        return (bp, tip, dir) -> bp.pad(tip.x, TreeBlueprint.round(tip.y) + 1, tip.z, pr * range(bp.random(), 0.85, 1.12), thick, density);
    }

    /** A bare twig fork at a limb end (dead trees). */
    private static Leafer bareTwigs(double len) {
        return (bp, tip, dir) -> {
            RandomGenerator r = bp.random();
            if (r.nextFloat() < 0.35f) {
                return; // broken off
            }
            int n = 1 + r.nextInt(2);
            for (int i = 0; i < n; i++) {
                V d = dir.add(perpendicular(dir, r).mul(0.8)).add(new V(0, 0.3, 0)).norm();
                V e = tip.add(d.mul(len * range(r, 0.6, 1.1)));
                bp.path(tip.x, tip.y, tip.z, e.x, e.y, e.z, (byte) 0);
            }
        };
    }

    // -----------------------------------------------------------------------------------------
    // Roots
    // -----------------------------------------------------------------------------------------

    /** The usual ground-following root system for a trunk of the given size; {@code scale} widens it. */
    private static void roots(TreeBlueprint bp, double c, TreeSize size, double trunkR, double scale) {
        RandomGenerator r = bp.random();
        switch (size) {
            case YOUNG -> {
                if (r.nextFloat() < 0.5f) {
                    bp.rootSystem(c, 2, trunkR, 1.6, 0.0, 0.45);
                }
            }
            case SMALL -> bp.rootSystem(c, 3, trunkR, 2.4 * scale, 0.35 * scale, 0.45);
            case MEDIUM -> bp.rootSystem(c, range(r, 3, 5), trunkR, 3.4 * scale, 0.8 * scale, 0.5);
            case LARGE -> bp.rootSystem(c, range(r, 4, 6), trunkR, 4.8 * scale, 1.4 * scale, 0.7);
            case GIANT -> bp.rootSystem(c, range(r, 6, 8), trunkR, 6.5 * scale, 2.3 * scale, 1.0);
            case ANCIENT -> bp.rootSystem(c, range(r, 8, 11), trunkR, range(r, 10.0, 13.0) * scale, range(r, 3.4, 4.4) * scale, 1.5);
        }
    }

    // =========================================================================================
    // Oak: broad rounded crowns; swamp oaks weep; a few oaks grow like bonsai
    // =========================================================================================

    private static void oak(TreeBlueprint bp, TreeSize size, boolean swamp) {
        RandomGenerator r = bp.random();
        if (!swamp && size.atLeast(TreeSize.SMALL) && !size.atLeast(TreeSize.GIANT) && r.nextFloat() < 0.12f) {
            bonsaiOak(bp, size);
            return;
        }
        int fork;
        double r0;
        double r1;
        double rx;
        double rc;
        double c = 0.0;
        int limbs;
        switch (size) {
            case YOUNG -> {
                fork = range(r, 1, 2);
                r0 = 0;
                r1 = 0;
                rx = range(r, 1.8, 2.3);
                rc = 1.7;
                limbs = 2;
            }
            case SMALL -> {
                fork = range(r, 2, 3);
                r0 = 0;
                r1 = 0;
                rx = range(r, 2.8, 3.6);
                rc = 2.1;
                limbs = 2;
            }
            case MEDIUM -> {
                fork = range(r, 3, 5);
                r0 = swamp ? 0.7 : 0;
                r1 = 0;
                c = swamp ? 0.5 : 0.0;
                rx = range(r, 4.6, 6.0);
                rc = 2.6;
                limbs = range(r, 2, 3);
            }
            case LARGE -> {
                fork = range(r, 4, 6);
                r0 = swamp ? 1.3 : 0.7;
                r1 = swamp ? 0.7 : 0.7;
                c = swamp ? 0.0 : 0.5;
                rx = range(r, 6.5, 8.0);
                rc = 2.9;
                limbs = 3;
            }
            default -> {
                fork = range(r, 5, 7);
                r0 = 1.7;
                r1 = 1.1;
                rx = range(r, 9.0, 10.8);
                rc = 3.0;
                limbs = range(r, 3, 4);
            }
        }
        if (swamp && size.atLeast(TreeSize.MEDIUM)) {
            // the weeping crown needs headroom above the water
            fork += range(r, 1, 2);
        }
        double ry = rx * (swamp ? range(r, 0.55, 0.65) : range(r, 0.62, 0.74));
        double lean = swamp ? range(r, 0.4, 1.4) + fork * 0.1 : range(r, 0.0, 0.22 * fork);
        Trunk t = trunk(r, fork, c, r0, r1, lean, range(r, 0.0, swamp ? 0.9 : 0.5), swamp ? 0.3 : 0.12);
        t.draw(bp);
        V top = t.at(fork);
        V centre = new V(top.x + (top.x - c) * 0.5, fork + ry * 0.62, top.z + (top.z - c) * 0.5);
        List<V> sites = crownSites(bp, centre, rx, ry, clusterCount(rx, ry, rc, 0.75), rc * 1.2, 0.45, -0.6, 0.4, 0.25, rc + 0.6);
        Style st = new Style(0.45, swamp ? -0.06 : 0.08, 0.14, 0.45, 0.42, limbs);
        Leafer leafer = swamp ? weeping(rc * 0.95, 0.68, 0.92, 3 + (int) (rc * 0.5), 3) : puffs(rc, 0.75, 0.92);
        decurrent(bp, top, t.topDirection(), sites, r1, 0, st, leafer);
        if (swamp) {
            // buttressed, flared wet base
            if (size.atLeast(TreeSize.SMALL)) {
                bp.rootSystem(c, range(r, 5, 7), r0, 2.5 + 1.2 * size.ordinal(), 0.9 + 0.55 * size.ordinal(), 0.6 + 0.1 * size.ordinal());
            }
        } else {
            roots(bp, c, size, r0, 1.0);
        }
    }

    /** Bonsai-like oak: a twisting trunk with long, nearly level limbs ending in layered cloud pads. */
    private static void bonsaiOak(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int h = switch (size) {
            case SMALL -> range(r, 6, 8);
            case MEDIUM -> range(r, 8, 11);
            default -> range(r, 11, 14);
        };
        double scale = h / 10.0;
        double r0 = size == TreeSize.LARGE ? 0.7 : 0.0;
        double c = size == TreeSize.LARGE ? 0.5 : 0.0;
        Trunk t = trunk(r, h - 2, c, r0, 0.0, range(r, 1.2, 2.4) * scale, range(r, 1.0, 2.0) * scale, 0.45);
        t.draw(bp);
        Style st = new Style(0.2, -0.12, 0.18, 0.5, 0.45, 1);
        int padsN = size == TreeSize.SMALL ? 2 : size == TreeSize.MEDIUM ? 3 : 4;
        double a = angle(r);
        double y0 = h * 0.38;
        double y1 = h - 3.0;
        for (int i = 0; i < padsN; i++) {
            double y = Math.rint(y0 + (y1 - y0) * i / Math.max(1, padsN - 1) + range(r, -0.4, 0.4));
            a += Math.PI * range(r, 0.55, 1.0) * (i % 2 == 0 ? 1 : -1) + Math.PI;
            V o = t.at(y);
            double len = h * range(r, 0.42, 0.55) * (1.0 - 0.35 * i / Math.max(1, padsN));
            V end = o.add(horizontal(a, len)).add(new V(0, range(r, 0.5, 1.5), 0));
            limbTo(bp, o, horizontal(a, 1).add(new V(0, -0.3, 0)), end, 0, 0, st);
            bp.pad(end.x, TreeBlueprint.round(end.y) + 1, end.z, range(r, 2.4, 3.3) * Math.max(0.85, scale), range(r, 1.5, 2.2), 0.95);
        }
        // the apex pad above the leaning crown
        V top = t.at(h - 2);
        V apex = top.add(new V((top.x - c) * 0.3, 1.0, (top.z - c) * 0.3));
        limbTo(bp, top, t.topDirection(), apex, 0, 0, st);
        bp.pad(apex.x, TreeBlueprint.round(apex.y) + 1, apex.z, range(r, 2.6, 3.4) * Math.max(0.85, scale), range(r, 1.8, 2.4), 0.95);
        roots(bp, c, size, r0, 1.1);
    }

    /** A huge spreading oak: a trunk 6-7 blocks wide dividing into massive limbs under a vast dome. */
    private static void ancientOak(TreeBlueprint bp, boolean swamp) {
        RandomGenerator r = bp.random();
        int fork = range(r, 7, 10);
        double r0 = range(r, 2.8, 3.3);
        double r1 = r0 * 0.66;
        double rx = range(r, 15.0, 17.5);
        double ry = rx * (swamp ? 0.5 : range(r, 0.56, 0.64));
        double rc = 3.2;
        Trunk t = trunk(r, fork, 0.0, r0, r1, range(r, 0.0, 1.2), range(r, 0.2, 0.8), 0.2);
        t.draw(bp);
        V top = t.at(fork);
        V centre = new V(top.x, fork + ry * 0.55, top.z);
        List<V> sites = crownSites(bp, centre, rx, ry, clusterCount(rx, ry, rc, 0.8), rc * 1.15, 0.4, -0.6, 0.45, 0.18, rc + 0.6);
        Style st = new Style(0.5, swamp ? -0.04 : 0.1, 0.12, 0.42, 0.4, range(r, 4, 5));
        Leafer leafer = swamp ? weeping(rc, 0.68, 0.92, 7, 3) : puffs(rc, 0.72, 0.92);
        decurrent(bp, top, UP, sites, r1, 0, st, leafer);
        roots(bp, 0.0, TreeSize.ANCIENT, r0, swamp ? 1.15 : 1.0);
    }

    // =========================================================================================
    // Birch: slender white trunk, a few short side branches with tufts, full irregular crown
    // =========================================================================================

    private static void birch(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int h;
        double rx;
        double rc;
        double r0 = 0.0;
        double c = 0.0;
        int tufts;
        switch (size) {
            case YOUNG -> {
                h = range(r, 3, 4);
                rx = 1.5;
                rc = 1.4;
                tufts = 0;
            }
            case SMALL -> {
                h = range(r, 6, 8);
                rx = range(r, 1.9, 2.3);
                rc = 1.6;
                tufts = 1;
            }
            case MEDIUM -> {
                h = range(r, 9, 13);
                rx = range(r, 2.5, 3.1);
                rc = 1.8;
                tufts = range(r, 1, 2);
            }
            case LARGE -> {
                h = range(r, 13, 18);
                rx = range(r, 3.0, 3.7);
                rc = 2.0;
                tufts = range(r, 2, 3);
            }
            default -> {
                h = range(r, 18, 24);
                rx = range(r, 3.6, 4.3);
                rc = 2.2;
                tufts = range(r, 2, 3);
                r0 = 0.7;
                c = 0.5;
            }
        }
        Trunk t = trunk(r, h - 1, c, r0, 0.0, range(r, 0.0, 0.07 * h), range(r, 0.0, 0.6), 0.12);
        t.draw(bp);
        double base = h * range(r, 0.42, 0.52);
        double ry = (h - base) * 0.5 + 0.8;
        V centre = t.at(base + ry - 0.3);
        List<V> sites = crownSites(bp, centre, rx, ry, clusterCount(rx, ry, rc, 0.95), rc * 1.05, 0.2, -0.85, 0.0, 0.3, rc + 0.6);
        Style st = new Style(0.25, 0.04, 0.1, 0.5, 0.5, 1);
        excurrent(bp, t, sites, Math.toRadians(range(r, 52, 64)), base - 1.0, h - 1.5, 0.0, st, puffs(rc, 0.95, 0.9));
        V top = t.at(h - 1);
        bp.puff(top.x, h, top.z, rc * 0.9, 1.1, 0.95);
        // a couple of short side branches below the crown, each with a small tuft
        for (int i = 0; i < tufts; i++) {
            double y = Math.rint(range(r, h * 0.22, base - 0.5));
            V o = t.at(y);
            double a = angle(r);
            double len = range(r, 1.4, 2.4) + size.ordinal() * 0.2;
            V e = o.add(horizontal(a, len)).add(new V(0, len * range(r, 0.7, 1.1), 0));
            limbTo(bp, o, horizontal(a, 1).add(UP).norm(), e, 0, 0, st);
            bp.puff(e.x, e.y + 0.5, e.z, range(r, 1.1, 1.5), 0.8, 0.9);
        }
        if (size.atLeast(TreeSize.MEDIUM)) {
            roots(bp, c, size.smaller(), r0, 0.9);
        }
    }

    // =========================================================================================
    // Conifers: spruce, fir and Scots pine
    // =========================================================================================

    /**
     * A conifer stem standing at {@code (c, 0, c)}: straight in its lower part, leaning by up to
     * {@code lean} blocks in total and bowed by up to {@code bend}; above {@code crookFrom} (a fraction
     * of its height) it also sways from side to side by up to {@code crook} blocks, like the crooked
     * upper stem of an old pine. The radius narrows almost evenly from {@code r0} at the foot to
     * {@code r1} at the top, as the stem of a conifer does, rather than with the quick taper of a
     * broadleaf trunk.
     */
    private static Trunk coniferTrunk(RandomGenerator r, int top, double c, double r0, double r1, double lean, double bend, double crook,
                                      double crookFrom) {
        Trunk t = new Trunk(Math.max(1, top + 1));
        double la = angle(r);
        double ba = la + (r.nextBoolean() ? 1 : -1) * range(r, 0.9, 2.2);
        double p1 = angle(r);
        double p2 = angle(r);
        double f1 = range(r, 0.28, 0.45);
        double f2 = range(r, 0.22, 0.4);
        int n = t.top();
        for (int y = 0; y <= n; y++) {
            double s = (double) y / Math.max(1, n);
            double off = lean * Math.pow(s, 1.4);
            double bow = bend * Math.sin(Math.PI * s);
            double k = crook * Math.pow(Math.max(0.0, (s - crookFrom) / (1.0 - crookFrom)), 1.2);
            t.cx[y] = c + Math.cos(la) * off + Math.cos(ba) * bow + k * Math.sin(y * f1 + p1);
            t.cz[y] = c + Math.sin(la) * off + Math.sin(ba) * bow + k * Math.sin(y * f2 + p2);
            t.r[y] = r1 + (r0 - r1) * Math.pow(1.0 - s, 1.1);
        }
        return t;
    }

    /**
     * The longest a horizontal run from {@code o} towards {@code a} may be (at most {@code len}) for its
     * end to stay inside a circle of radius {@code reach - margin} around the stem's foot. A circle
     * rather than the reach square, so a crown that runs into the limit stays round instead of
     * reaching further along its diagonals.
     */
    private static double maxRun(TreeBlueprint bp, V o, double a, double len, double margin) {
        double limit = bp.reach() - margin;
        double ca = Math.cos(a);
        double sa = Math.sin(a);
        // |o + l * (ca, sa)| = limit, solved for the positive l
        double b = o.x * ca + o.z * sa;
        double c = o.x * o.x + o.z * o.z - limit * limit;
        double d = b * b - c;
        double l = d < 0.0 ? 0.0 : -b + Math.sqrt(d);
        return Math.max(0.5, Math.min(len, l));
    }

    /**
     * Dead branches on the bare lower stem of an old conifer, from layer {@code from} up to below
     * {@code to}: short, slightly drooping stubs where shaded limbs died and broke off, a few still
     * carrying a twig. {@code chance} is the chance of a stub per layer.
     */
    private static void deadStubs(TreeBlueprint bp, Trunk t, int from, int to, double chance, double maxLen) {
        RandomGenerator r = bp.random();
        double a = angle(r);
        for (int y = Math.max(1, from); y < Math.min(to, t.top()); y++) {
            if (r.nextDouble() >= chance) {
                continue;
            }
            a += range(r, 1.8, 2.9);
            V o = t.at(y);
            double out = range(r, 0.8, maxLen);
            double len = t.radius(y) + out;
            V e = o.add(horizontal(a, len)).add(new V(0, -len * range(r, 0.05, 0.4), 0));
            bp.path(o.x, y, o.z, e.x, e.y, e.z, (byte) 0);
            if (out > 1.6 && r.nextFloat() < 0.4f) {
                V tw = e.add(horizontal(a + (r.nextBoolean() ? 0.9 : -0.9), range(r, 0.8, 1.4))).add(new V(0, range(r, -0.6, 0.4), 0));
                bp.path(e.x, e.y, e.z, tw.x, tw.y, tw.z, (byte) 0);
            }
        }
    }

    /**
     * Needles on the sides and tops of all wood outside the stem between layers {@code from} and
     * {@code to}, so the branches of a spruce or fir crown are clothed in needles instead of showing
     * as bare logs; {@code chance} is the chance of each needle block.
     */
    private static void sheathe(TreeBlueprint bp, Trunk t, int from, int to, double chance) {
        RandomGenerator r = bp.random();
        List<int[]> wood = new ArrayList<>();
        bp.forEachCell((x, y, z, code) -> {
            if (y >= from && y <= to && TreeBlueprint.isWoody(code)) {
                V c = t.at(y);
                double reach = t.radius(y) + 0.8;
                if ((x - c.x) * (x - c.x) + (z - c.z) * (z - c.z) > reach * reach) {
                    wood.add(new int[]{x, y, z});
                }
            }
        });
        for (int[] w : wood) {
            // the sides and the top: the undersides stay dark between the layers
            for (int[] n : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                if (r.nextDouble() < chance) {
                    bp.leaf(w[0] + n[0], w[1] + n[1], w[2] + n[2]);
                }
            }
        }
    }

    /**
     * Branching of a spruce or fir crown.
     *
     * @param profile   exponent of the cone: branch length follows {@code (1 - s)^profile} from the
     *                  lowest whorl ({@code s = 0}) to the top
     * @param slopeLow  rise per block of the lowest branches as they leave the stem (negative: they
     *                  slope down from the start)
     * @param slopeHigh rise per block of the branches just below the spire
     * @param droop     how far a branch of the lower crown sags towards its tip, per block of length
     * @param upturn    the part of that sag its tip turns up again (the upturned tips of spruce branches)
     * @param width     half-width of a branch's needle spray per block of branch length
     * @param maxWidth  the widest half-width of a spray
     * @param curtain   chance per step along the outer part of a lower branch to hang a curtain of
     *                  needles below its spray
     * @param density   chance of each needle block
     * @param spokes    least number of branches per whorl
     * @param spokesMax most branches per whorl
     * @param spire     the top part of the tree, as a fraction of its height, that carries only short tufts
     */
    private record Conifer(double profile, double slopeLow, double slopeHigh, double droop, double upturn, double width, double maxWidth,
                           double curtain, double density, int spokes, int spokesMax, double spire) {
    }

    /** The point at fraction {@code t} along a {@link #frond} branch. */
    private static V frondPoint(V o, V dir, V side, double len, double slope, double droop, double up, double wig, double t) {
        double y = len * (slope * t - droop * t * t) + up * t * t * t * t;
        double w = wig * Math.sin(Math.PI * t);
        return new V(o.x + dir.x * len * t + side.x * w, o.y + y, o.z + dir.z * len * t + side.z * w);
    }

    /** Half-width of a needle spray at fraction {@code t} along its branch: widest before the middle, pointed at the tip. */
    private static double sprayWidth(double width, double t) {
        return width * Math.pow(Math.sin(Math.PI * (0.12 + 0.8 * t)), 0.8);
    }

    /**
     * One branch of a spruce or fir with its spray of needles. The branch leaves the stem at
     * {@code o} towards {@code a}, rising by {@code slope} per block at first and sagging by
     * {@code droop} per block towards its tip, which turns up again by the fraction {@code upturn} of
     * that sag. Needles clothe its upper side as a spray up to {@code width} blocks to either side,
     * widest before the middle and pointed at the tip, arched like a roof so that its edges hang
     * lower than its spine; along the outer part of drooping branches short curtains of needles
     * hang below the spray, like the comb of hanging branchlets of an old spruce. Longer branches
     * carry side branchlets under the spray, so its edges stay within reach of wood.
     *
     * @param radius        radius of the branch where it leaves the stem (0: a line of logs)
     * @param curtain       chance per step along the outer spray to hang a curtain
     * @param curtainLength longest curtain
     */
    private static void frond(TreeBlueprint bp, V o, double a, double len, double slope, double droop, double upturn, double width,
                              double radius, double curtain, int curtainLength, double density) {
        RandomGenerator r = bp.random();
        V dir = horizontal(a, 1.0);
        V side = new V(-dir.z, 0, dir.x);
        double wig = range(r, -0.6, 0.6) * Math.min(1.0, len / 6.0);
        double up = upturn * droop * len;
        int segs = Math.max(1, (int) Math.ceil(len / 1.4));
        V[] joints = new V[segs + 1];
        joints[0] = o;
        for (int i = 1; i <= segs; i++) {
            joints[i] = frondPoint(o, dir, side, len, slope, droop, up, wig, i / (double) segs);
            V p = joints[i - 1];
            V q = joints[i];
            bp.limb(p.x, p.y, p.z, q.x, q.y, q.z, radius * (1.0 - (i - 1) / (double) segs), radius * (1.0 - i / (double) segs));
        }
        // the spray: a roof of needles over the branch, one block above its spine and two thick in the middle
        double sag = 0.4 + 4.0 * droop;
        double step = 0.45 / len;
        for (double t = Math.min(0.45, 1.0 / len); t <= 1.0 + 1e-9; t += step) {
            V p = frondPoint(o, dir, side, len, slope, droop, up, wig, Math.min(1.0, t));
            double w = Math.max(0.5, sprayWidth(width, t));
            for (double u = -w; u <= w + 1e-9; u += 0.5) {
                double e = Math.abs(u) / w;
                int x = TreeBlueprint.round(p.x + side.x * u);
                int z = TreeBlueprint.round(p.z + side.z * u);
                double top = p.y + 1.0 - sag * e * e;
                if (r.nextDouble() < density - 0.3 * e * e) {
                    bp.leaf(x, TreeBlueprint.round(top), z);
                }
                if (e < 0.5 && r.nextDouble() < density) {
                    bp.leaf(x, TreeBlueprint.round(top - 1.0), z);
                }
                if (e < 0.3 && t > 0.3 && t < 0.85 && r.nextDouble() < density) {
                    // needles hang under the spine too, hiding the branch
                    bp.leaf(x, TreeBlueprint.round(p.y - 1.0), z);
                }
            }
            if (t > 0.35 && curtain > 0 && r.nextDouble() < curtain) {
                double u = (r.nextBoolean() ? 1 : -1) * w * range(r, 0.35, 1.0);
                double e = Math.abs(u) / w;
                bp.strand(TreeBlueprint.round(p.x + side.x * u), TreeBlueprint.round(p.y + 1.0 - sag * e * e), TreeBlueprint.round(p.z + side.z * u),
                    range(r, 1, curtainLength));
            }
        }
        // a tuft beyond the tip, on the face of the tip block that points furthest outwards (not on
        // the stubby branches of saplings, whose spray already covers the tip)
        V tip = joints[segs];
        int tx = TreeBlueprint.round(tip.x);
        int ty = TreeBlueprint.round(tip.y);
        int tz = TreeBlueprint.round(tip.z);
        if (len >= 1.6) {
            if (Math.abs(dir.x) >= Math.abs(dir.z)) {
                bp.leaf(tx + (dir.x > 0 ? 1 : -1), ty, tz);
            } else {
                bp.leaf(tx, ty, tz + (dir.z > 0 ? 1 : -1));
            }
        }
        // side branchlets under the spray, alternating left and right
        boolean left = r.nextBoolean();
        for (int i = 1; i < segs; i++) {
            double w = sprayWidth(width, i / (double) segs);
            if (w < 1.4) {
                continue;
            }
            V p = joints[i];
            V e = p.add(side.mul((left ? 1 : -1) * w * 0.7)).add(dir.mul(w * 0.4)).add(new V(0, -sag * 0.5, 0));
            bp.path(p.x, p.y, p.z, e.x, e.y, e.z, (byte) 0);
            left = !left;
        }
    }

    /**
     * The crown of a spruce or fir: whorls of branches every {@code spacing} blocks (sometimes one
     * more) from layer {@code base} up to a narrow spire, each branch a {@link #frond} whose length
     * follows the cone {@code maxR * (1 - s)^profile}. The whorls turn by the golden angle and lose a
     * branch here and there; lower branches sag and hang curtains of needles, upper ones sweep
     * upwards. Near the stem the sprays of a whorl merge into a skirt, and a collar of needles fills
     * the stem between whorls, so the crown reads as a stack of drooping layers with dark gaps
     * between their rims. The spire carries short upswept tufts, and all branch wood is clothed in
     * needles.
     *
     * @param thick radius of long branches (over five blocks) where they leave the stem
     */
    private static void coniferCrown(TreeBlueprint bp, Trunk t, int base, int h, double maxR, int spacing, Conifer cf, double thick) {
        RandomGenerator r = bp.random();
        double a0 = angle(r);
        int spire = Math.max(2, (int) Math.round(h * cf.spire));
        int top = h - spire;
        // even the shortest branches reach this far out, less on saplings
        double least = Math.min(0.8, 0.15 * h);
        int y = base;
        while (y < top) {
            double s = (double) (y - base) / Math.max(1, h - 1 - base);
            double rad = maxR * Math.pow(1.0 - s, cf.profile) + least;
            V o = t.at(y);
            int n = range(r, cf.spokes, cf.spokesMax);
            double slope = cf.slopeLow + (cf.slopeHigh - cf.slopeLow) * s;
            double droop = cf.droop * (1.0 - 0.65 * s);
            a0 += 2.4;
            for (int i = 0; i < n; i++) {
                if (r.nextFloat() < (s < 0.2 ? 0.18f : 0.07f)) {
                    continue;
                }
                double a = a0 + i * Math.PI * 2.0 / n + range(r, -0.3, 0.3);
                double len = rad * range(r, 0.82, 1.1);
                double w = Math.min(cf.maxWidth, 0.7 + cf.width * len);
                len = maxRun(bp, o, a, len, w + 1.5);
                frond(bp, o, a, len, slope + range(r, -0.04, 0.04), droop, cf.upturn, w, len > 5.0 ? thick : 0.0, cf.curtain * (1.0 - s), 2,
                    cf.density);
            }
            // the sprays merge into a skirt near the stem
            bp.disc(o.x, y + 1, o.z, t.radius(y) + 0.3 * rad, 0.92);
            bp.disc(o.x, y, o.z, t.radius(y) + 0.22 * rad, 0.92);
            int next = y + spacing + (spacing > 1 && r.nextFloat() < 0.3f ? 1 : 0);
            if (y + 2 < Math.min(next, top)) {
                V m = t.at(y + 2);
                bp.disc(m.x, y + 2, m.z, t.radius(y + 2) + 1.1, 0.8);
            }
            y = next;
        }
        // the spire: short upswept tufts all along the leader
        for (int yy = Math.max(base, top); yy < h - 1; yy++) {
            double s = (double) (yy - base) / Math.max(1, h - 1 - base);
            double len = Math.max(0.8, maxR * Math.pow(1.0 - s, cf.profile) + 0.5);
            V o = t.at(yy);
            bp.disc(o.x, yy, o.z, 1.2, 0.9);
            int n = range(r, 2, 4);
            for (int i = 0; i < n; i++) {
                a0 += 2.4;
                frond(bp, o, a0, len * range(r, 0.8, 1.1), 0.45, 0.05, 0.0, 0.6, 0.0, 0.0, 1, cf.density);
            }
        }
        sheathe(bp, t, base - 1, h, 0.75);
        V tip = t.at(h - 1);
        int tx = TreeBlueprint.round(tip.x);
        int tz = TreeBlueprint.round(tip.z);
        bp.leaf(tx, h, tz);
        bp.leaf(tx, h + 1, tz);
        if (h > 14) {
            bp.leaf(tx, h + 2, tz);
        }
    }

    /**
     * Spruce: a dense, layered cone of drooping branches on a straight stem. Old trees grow very
     * tall on a thick, flared foot; in a closed stand their lower stem is bare but for dead
     * branches, so trunks show and light reaches the forest floor. Medium spruces keep the single
     * stem of the sapling they grow from; large ones the 2x2 stem of a sapling group.
     */
    private static void spruce(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int h;
        double r0;
        double c = 0.0;
        double bare;
        int spacing;
        double thick = 0.0;
        int spokes;
        switch (size) {
            case YOUNG -> {
                h = range(r, 3, 5);
                r0 = 0;
                bare = 0.0;
                spacing = 1;
                spokes = 3;
            }
            case SMALL -> {
                h = range(r, 7, 10);
                r0 = 0;
                bare = 0.08;
                spacing = 2;
                spokes = 4;
            }
            case MEDIUM -> {
                h = range(r, 14, 22);
                r0 = 0;
                bare = 0.16;
                spacing = 3;
                spokes = 5;
            }
            case LARGE -> {
                // a 2x2 stem on a foot four blocks wide, centred like the 2x2 sapling group it may grow from
                h = range(r, 27, 40);
                r0 = 2.0;
                c = 0.5;
                bare = 0.32;
                spacing = 4;
                thick = 0.62;
                spokes = 6;
            }
            case GIANT -> {
                h = range(r, 42, 58);
                r0 = range(r, 2.3, 2.6);
                bare = 0.36;
                spacing = 5;
                thick = 0.8;
                spokes = 7;
            }
            default -> {
                h = range(r, 70, 86);
                r0 = range(r, 3.4, 3.9);
                bare = 0.4;
                spacing = 5;
                thick = 1.05;
                spokes = 8;
            }
        }
        Trunk t = coniferTrunk(r, h - 1, c, r0, 0.0, range(r, 0.0, 0.03 * h), range(r, 0.0, 0.5), 0.0, 0.5);
        t.draw(bp);
        int base = Math.max(1, (int) (h * bare));
        // young spruces sweep their branches up; they sag more, and hang more needles, with age
        double age = switch (size) {
            case YOUNG -> 0.15;
            case SMALL -> 0.45;
            case MEDIUM -> 0.75;
            default -> 1.0;
        };
        // branch length and spray width grow with the tree's height, so small spruces keep a slender
        // cone and grown ones a crown about twice as tall as it is wide
        double crownR = switch (size) {
            case YOUNG -> 0.12 * h;
            case SMALL -> 0.13 * h + 0.2;
            default -> 0.17 * h + 0.6;
        };
        double maxWidth = Math.min(2.6, 0.2 + 0.075 * h);
        Conifer cf = new Conifer(1.0 + 0.15 * age, 0.3 - 0.42 * age, 0.45, 0.38 * age, 0.5, 0.3, maxWidth, 0.12 * age, 0.92, spokes, spokes + 2, 0.1);
        coniferCrown(bp, t, base, h, crownR, spacing, cf, thick);
        if (size.atLeast(TreeSize.MEDIUM)) {
            deadStubs(bp, t, 2, base, size.atLeast(TreeSize.LARGE) ? 0.5 : 0.25, 1.0 + 0.3 * size.ordinal());
        }
        if (size.atLeast(TreeSize.LARGE)) {
            roots(bp, c, size, r0, 1.0);
        } else if (size.atLeast(TreeSize.SMALL)) {
            roots(bp, c, size.smaller(), r0, 0.9);
        }
    }

    /** Fir: a narrow, dense spire of level branches with flat sprays and upturned tips, clothed nearly to the ground. */
    private static void fir(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int h = switch (size) {
            case YOUNG -> range(r, 3, 5);
            case SMALL -> range(r, 7, 10);
            case MEDIUM -> range(r, 13, 19);
            case LARGE -> range(r, 22, 32);
            default -> range(r, 33, 45);
        };
        double r0 = size == TreeSize.LARGE ? 0.7 : size.atLeast(TreeSize.GIANT) ? 1.9 : 0.0;
        double c = size == TreeSize.LARGE ? 0.5 : 0.0;
        Trunk t = coniferTrunk(r, h - 1, c, r0, 0.0, range(r, 0.0, 0.02 * h), range(r, 0.0, 0.3), 0.0, 0.5);
        t.draw(bp);
        int spokes = size.atLeast(TreeSize.MEDIUM) ? 5 : 3;
        // level branches; branch length and spray width grow with the tree's height, so young firs
        // stay slim, and the whorls stand apart so each tier keeps a dark gap under its rim
        Conifer cf = new Conifer(1.0, 0.0, 0.4, 0.08, 0.5, 0.25, Math.min(1.8, 0.3 + 0.055 * h), 0.0, 0.95, spokes, spokes + 2, 0.16);
        int spacing = size.atLeast(TreeSize.LARGE) ? 4 : size == TreeSize.MEDIUM ? 3 : size == TreeSize.SMALL ? 2 : 1;
        double crownR = size == TreeSize.YOUNG ? 0.12 * h : 0.13 * h + 0.5;
        coniferCrown(bp, t, Math.max(1, (int) (h * 0.07)), h, crownR, spacing, cf, size.atLeast(TreeSize.GIANT) ? 0.62 : 0.0);
        if (size.atLeast(TreeSize.MEDIUM)) {
            roots(bp, c, size.smaller(), r0, 0.8);
        }
    }

    /**
     * Rounded, irregular needle cushions on twig tips, the foliage of a Scots pine: a domed,
     * slightly flattened clump with two or three smaller lobes, each on a short twig fanning out from
     * the tip, so a cushion is lumpy rather than a plate and shows its twigs from below. Lobes reach
     * up to {@code 1.45} times the cushion's radius from the tip; cushions near the edge of the
     * blueprint's reach are made smaller to fit.
     */
    private static Leafer cushions(double rc, double flat, double density) {
        return (bp, tip, dir) -> {
            RandomGenerator r = bp.random();
            double room = bp.reach() - 0.6 - Math.max(Math.abs(tip.x), Math.abs(tip.z));
            double rr = Math.max(1.2, Math.min(rc * range(r, 0.85, 1.12), room / 1.45));
            double ha = Math.abs(dir.x) + Math.abs(dir.z) < 1e-6 ? angle(r) : Math.atan2(dir.z, dir.x);
            bp.ellipsoid(tip.x, tip.y + rr * flat * 0.35, tip.z, rr, rr * flat, rr, density);
            int lobes = 2 + r.nextInt(2);
            for (int i = 0; i < lobes; i++) {
                double a = ha + range(r, -1.4, 1.4);
                V e = tip.add(horizontal(a, rr * range(r, 0.45, 0.75))).add(new V(0, rr * flat * range(r, -0.1, 0.5), 0));
                bp.path(tip.x, tip.y, tip.z, e.x, e.y, e.z, (byte) 0);
                double lr = rr * range(r, 0.5, 0.68);
                bp.ellipsoid(e.x, e.y + lr * flat * 0.3, e.z, lr, lr * flat * 1.1, lr, density);
            }
        };
    }

    /**
     * One limb of a pine: it leaves the stem at {@code o} towards {@code a}, starting out at
     * {@code elev} radians above level and sweeping up towards {@code end}. Unless it carries a single
     * cushion, it forks part way and its branches spread in a fan around {@code end}, each ending in a
     * needle cushion, so the limb carries a loose, lumpy tier of foliage.
     */
    private static void pineLimb(TreeBlueprint bp, V o, double a, double elev, V end, int cushions, double radius, double rc, Style st,
                                 Leafer leafer) {
        RandomGenerator r = bp.random();
        V dir = horizontal(a, Math.cos(elev)).add(new V(0, Math.sin(elev), 0));
        if (cushions <= 1) {
            leafer.grow(bp, end, limbTo(bp, o, dir, end, radius, radius * 0.4, st));
            return;
        }
        V f = o.add(end.sub(o).mul(range(r, 0.5, 0.65)));
        V d = limbTo(bp, o, dir, f, radius, radius * 0.8, st);
        double out = Math.max(1.0, Math.hypot(end.x - f.x, end.z - f.z));
        double fan = range(r, 0.45, 0.8);
        double limit = bp.reach() - (rc + 1.0);
        // cushions stand apart, so sky shows through the crown between them
        double spacing2 = rc * rc * 2.0;
        List<V> sites = new ArrayList<>();
        int attempts = cushions * 30;
        while (sites.size() < cushions && attempts-- > 0) {
            double dist = out * range(r, 0.3, 1.15);
            // nearer cushions sit a little higher, so the tier is domed
            V p = f.add(horizontal(a + fan * (2.0 * r.nextDouble() - 1.0), dist))
                .add(new V(0, end.y - f.y + range(r, -0.8, 0.8) + (1.0 - dist / out) * 0.8, 0));
            if (p.x * p.x + p.z * p.z > limit * limit) {
                continue; // within a circle, like the limbs (see maxRun)
            }
            boolean free = true;
            for (V q : sites) {
                if (p.dist2(q) < spacing2) {
                    free = false;
                    break;
                }
            }
            if (free) {
                sites.add(p);
            }
        }
        if (sites.isEmpty()) {
            sites.add(end);
        }
        decurrent(bp, f, d, sites, radius * 0.8, 1, st, leafer);
    }

    /**
     * Scots pine. Young pines are conical; small ones carry a crown of cushions on upswept whorled
     * limbs around a leader; from medium size on, pines stand on a long, bare, slightly crooked stem
     * under a flat-topped crown of needle cushions on a few long limbs, an umbrella on old trees.
     */
    private static void pine(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        if (size == TreeSize.YOUNG) {
            int h = range(r, 4, 6);
            Trunk t = coniferTrunk(r, h - 1, 0, 0, 0, 0, 0, 0, 0.5);
            t.draw(bp);
            coniferCrown(bp, t, 1, h, 0.55, 1, new Conifer(0.9, 0.35, 0.5, 0.04, 0.0, 0.3, 0.7, 0.0, 0.92, 3, 3, 0.15), 0.0);
            return;
        }
        int h;
        double r0;
        double r1;
        double c = 0.0;
        double rc;
        double limbR;
        switch (size) {
            case SMALL -> {
                h = range(r, 9, 13);
                r0 = 0;
                r1 = 0;
                rc = 2.0;
                limbR = 0;
            }
            case MEDIUM -> {
                // a 2x2 stem all the way up: a pine of this size already stands on a long bole
                h = range(r, 18, 26);
                r0 = 0.7;
                r1 = 0.7;
                c = 0.5;
                rc = 2.5;
                limbR = 0;
            }
            case LARGE -> {
                h = range(r, 30, 40);
                r0 = 1.9;
                r1 = 0.7;
                rc = 2.9;
                limbR = 0.75;
            }
            case GIANT -> {
                h = range(r, 40, 54);
                r0 = range(r, 2.3, 2.6);
                r1 = 1.0;
                rc = 3.2;
                limbR = 1.0;
            }
            default -> {
                h = range(r, 60, 74);
                r0 = range(r, 3.3, 3.7);
                r1 = 1.5;
                rc = 3.5;
                limbR = 1.3;
            }
        }
        if (size == TreeSize.SMALL) {
            leaderPine(bp, h, rc);
        } else {
            umbrellaPine(bp, size, h, c, r0, r1, rc, limbR);
        }
        roots(bp, c, size, r0, 1.0);
    }

    /** A small pine: whorls of upswept limbs with cushions around a leader, in an ovoid crown. */
    private static void leaderPine(TreeBlueprint bp, int h, double rc) {
        RandomGenerator r = bp.random();
        int stemTop = h - 2;
        Trunk t = coniferTrunk(r, stemTop, 0.0, 0.0, 0.0, range(r, 0.0, 0.04 * h), range(r, 0.0, 0.4), h * range(r, 0.01, 0.03), 0.4);
        t.draw(bp);
        double yb = h * range(r, 0.28, 0.36);
        double rx = h * 0.3 + 0.6;
        Style st = new Style(0.45, 0.08, 0.14, 0.55, 0.5, 1);
        Leafer cushion = cushions(rc, 0.75, 0.95);
        double a = angle(r);
        for (int y = (int) Math.round(yb); y < stemTop - 1; y += 2) {
            double s = (y - yb) / Math.max(1.0, stemTop - yb);
            V o = t.at(y);
            int n = range(r, 3, 4);
            for (int i = 0; i < n; i++) {
                a += 2.4 + range(r, -0.3, 0.3);
                double len = maxRun(bp, o, a, rx * (0.2 + 0.8 * Math.pow(1.0 - s, 0.8)) * range(r, 0.8, 1.1), rc + 1.0);
                double rise = len * range(r, 0.35, 0.6);
                V end = o.add(horizontal(a, len)).add(new V(0, rise, 0));
                pineLimb(bp, o, a, Math.atan2(rise, len) - 0.2, end, len > rc * 2.6 ? 2 : 1, 0.0, rc, st, cushion);
            }
        }
        // the leader ends in a cushion taller than wide
        V top = t.at(stemTop);
        V e = top.add(new V(0, 1.0, 0));
        bp.path(top.x, top.y, top.z, e.x, e.y, e.z, (byte) 0);
        bp.ellipsoid(e.x, e.y + 0.6, e.z, rc * 0.8, rc * 1.05, rc * 0.8, 0.95);
    }

    /**
     * A grown pine: the stem rises bare and slightly crooked to {@code 0.6..0.68} of the height
     * ({@code 0.5..0.6} on medium pines, whose crown is deeper and narrower), carries a few long
     * limbs that sweep up and fan out into tiers of cushions, longest low down so the crown is broad
     * and flat-topped, and divides at its top into upswept leaders under the crown's highest
     * cushions. The crown may lean to one side, as in a wind-shaped tree. The widest crowns are kept
     * just inside the blueprint's reach, so few limbs need shortening and the crown stays round.
     */
    private static void umbrellaPine(TreeBlueprint bp, TreeSize size, int h, double c, double r0, double r1, double rc, double limbR) {
        RandomGenerator r = bp.random();
        int stem = (int) Math.round(h * range(r, 0.84, 0.9));
        Trunk t = coniferTrunk(r, stem, c, r0, r1, range(r, 0.0, 0.04 * h), range(r, 0.0, 0.04 * h), h * range(r, 0.02, 0.04), 0.45);
        t.draw(bp);
        boolean medium = size == TreeSize.MEDIUM;
        double yb = h * (medium ? range(r, 0.5, 0.6) : range(r, 0.6, 0.68));
        double spread = switch (size) {
            case MEDIUM -> range(r, 0.24, 0.28);
            case LARGE -> range(r, 0.26, 0.3);
            default -> range(r, 0.3, 0.34);
        };
        double rx = Math.min(h * spread, (bp.reach() - rc - 1.0) / 1.05);
        int limbs = switch (size) {
            case MEDIUM -> range(r, 5, 6);
            case LARGE -> range(r, 6, 8);
            case GIANT -> range(r, 8, 10);
            default -> range(r, 11, 14);
        };
        Style st = new Style(0.5, 0.05, 0.16, 0.55, 0.5, 1);
        Leafer cushion = cushions(rc, 0.72, 0.95);
        double a = angle(r);
        double wind = angle(r);
        double asym = range(r, 0.0, 0.25);
        for (int i = 0; i < limbs; i++) {
            double s = (i + range(r, 0.2, 0.8)) / limbs;
            double y = Math.rint(yb + (stem - 1.5 - yb) * s);
            a += 2.4 + range(r, -0.35, 0.35);
            V o = t.at(y);
            double len = rx * (1.0 - 0.5 * Math.pow(s, 1.3)) * range(r, 0.78, 1.08) * (1.0 + asym * Math.cos(a - wind));
            len = maxRun(bp, o, a, len, rc + 1.0);
            double rise = len * (0.3 + 0.35 * s) + range(r, -0.5, 1.0);
            V end = o.add(horizontal(a, len)).add(new V(0, rise, 0));
            // limbs leave flatter than their chord and sweep up towards their ends
            double elev = Math.atan2(rise, len) - range(r, 0.15, 0.35);
            int k = 1 + (int) Math.round(len / (rc * 1.4));
            pineLimb(bp, o, a, elev, end, k, limbR * Math.sqrt(len / rx), rc, st, cushion);
        }
        // the stem divides into a few upswept leaders that carry the top of the crown
        V top = t.at(stem);
        int leaders = range(r, 2, 3);
        double la = angle(r);
        for (int j = 0; j < leaders; j++) {
            la += Math.PI * 2.0 / leaders + range(r, -0.4, 0.4);
            double out = range(r, 1.0, 1.5) + rx * range(r, 0.12, 0.25);
            V e = top.add(horizontal(la, out)).add(new V(0, h - 1.5 - stem + range(r, -0.6, 0.4), 0));
            cushion.grow(bp, e, limbTo(bp, top, t.topDirection(), e, r1 * 0.75, 0.0, st));
        }
        // the bare stem: stubs of self-pruned branches and a dead limb or two below the crown
        deadStubs(bp, t, 3, (int) yb - 2, 0.15, 1.6);
        int dead = size.atLeast(TreeSize.LARGE) ? range(r, 0, 2) : 0;
        for (int j = 0; j < dead; j++) {
            double y = Math.rint(yb - range(r, 1.0, 4.0));
            V o = t.at(y);
            double da = angle(r);
            double len = maxRun(bp, o, da, rx * range(r, 0.25, 0.45), 2.0);
            V e = o.add(horizontal(da, len)).add(new V(0, len * range(r, -0.1, 0.25), 0));
            bareTwigs(1.5).grow(bp, e, limbTo(bp, o, horizontal(da, 1.0), e, 0.0, 0.0, st));
        }
    }

    // =========================================================================================
    // Jungle: tall straight trunks on buttress roots under umbrella canopies
    // =========================================================================================

    private static void jungle(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        if (size == TreeSize.YOUNG) {
            // jungle bush
            bp.log(0, 0, 0, TreeBlueprint.LOG_Y);
            double br = range(r, 2.0, 2.6);
            bp.puff(0, 1.0, 0, br, 0.6, 0.95);
            return;
        }
        int h;
        double r0;
        double r1;
        double c = 0.0;
        double rx;
        double rc;
        int laterals;
        switch (size) {
            case SMALL -> {
                h = range(r, 5, 8);
                r0 = 0;
                r1 = 0;
                rx = range(r, 2.6, 3.2);
                rc = 2.0;
                laterals = 0;
            }
            case MEDIUM -> {
                h = range(r, 10, 15);
                r0 = 0;
                r1 = 0;
                rx = range(r, 4.0, 5.0);
                rc = 2.4;
                laterals = range(r, 1, 2);
            }
            case LARGE -> {
                h = range(r, 18, 25);
                r0 = 0.7;
                r1 = 0.7;
                c = 0.5;
                rx = range(r, 6.0, 7.5);
                rc = 2.7;
                laterals = range(r, 2, 3);
            }
            case GIANT -> {
                h = range(r, 26, 34);
                r0 = 1.6;
                r1 = 1.0;
                rx = range(r, 9.0, 10.8);
                rc = 3.0;
                laterals = range(r, 3, 4);
            }
            default -> {
                h = range(r, 42, 52);
                r0 = range(r, 2.5, 2.8);
                r1 = 1.5;
                rx = range(r, 15.0, 17.5);
                rc = 3.2;
                laterals = range(r, 4, 5);
            }
        }
        int crownBase = (int) (h * (size == TreeSize.SMALL ? 0.7 : 0.8));
        Trunk t = trunk(r, crownBase, c, r0, r1, range(r, 0.0, 0.04 * h), range(r, 0.0, 0.08 * h), 0.08);
        t.draw(bp);
        double ry = Math.max(1.8, rx * 0.4);
        V top = t.at(crownBase);
        V centre = new V(top.x, h - ry * 0.3, top.z);
        List<V> sites = crownSites(bp, centre, rx, ry, clusterCount(rx, ry, rc, 0.85), rc * 1.15, 0.3, -0.5, 0.45, 0.22, rc + 0.6);
        Style st = new Style(0.6, 0.15, 0.1, 0.5, 0.45, range(r, 3, 5));
        Leafer puff = puffs(rc, 0.62, 0.93);
        decurrent(bp, top, UP, sites, r1, 0, st, puff);
        // lower lateral branches with their own clusters
        Style lat = new Style(0.3, 0.12, 0.12, 0.6, 0.5, 1);
        double la = angle(r);
        for (int i = 0; i < laterals; i++) {
            double y = Math.rint(range(r, h * 0.55, h * 0.72));
            V o = t.at(y);
            la += range(r, 1.7, 2.7);
            double len = rx * range(r, 0.45, 0.65);
            V e = o.add(horizontal(la, len)).add(new V(0, len * range(r, 0.35, 0.6), 0));
            V d = limbTo(bp, o, horizontal(la, 1), e, size.atLeast(TreeSize.GIANT) ? 0.7 : 0.0, 0.0, lat);
            puffs(rc * 0.9, 0.62, 0.92).grow(bp, e, d);
        }
        if (size.atLeast(TreeSize.LARGE)) {
            double fin = size == TreeSize.LARGE ? 2.6 : size == TreeSize.GIANT ? 4.0 : range(r, 6.5, 8.0);
            double len = size == TreeSize.LARGE ? 4.5 : size == TreeSize.GIANT ? 7.0 : range(r, 11.0, 13.0);
            bp.rootSystem(c, range(r, 5, 7) + (size == TreeSize.ANCIENT ? 1 : 0), r0, len, fin, 0.55);
        } else {
            roots(bp, c, size, r0, 1.0);
        }
    }

    // =========================================================================================
    // Acacia: a short trunk forking into sinuous limbs under flat, layered umbrella pads
    // =========================================================================================

    private static void acacia(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int fork;
        double r0;
        double r1;
        double c = 0.0;
        double rx;
        double pr;
        int limbs;
        switch (size) {
            case YOUNG -> {
                fork = range(r, 1, 2);
                r0 = 0;
                r1 = 0;
                rx = 2.0;
                pr = 1.9;
                limbs = 1;
            }
            case SMALL -> {
                fork = range(r, 2, 3);
                r0 = 0;
                r1 = 0;
                rx = range(r, 3.4, 4.0);
                pr = 2.5;
                limbs = 2;
            }
            case MEDIUM -> {
                fork = range(r, 3, 4);
                r0 = 0;
                r1 = 0;
                rx = range(r, 5.0, 6.0);
                pr = 3.0;
                limbs = range(r, 2, 3);
            }
            case LARGE -> {
                fork = range(r, 3, 5);
                r0 = 0.7;
                r1 = 0.7;
                c = 0.5;
                rx = range(r, 7.0, 8.5);
                pr = 3.4;
                limbs = 3;
            }
            case GIANT -> {
                fork = range(r, 4, 5);
                r0 = 1.4;
                r1 = 0.9;
                rx = range(r, 9.5, 11.0);
                pr = 3.8;
                limbs = range(r, 3, 4);
            }
            default -> {
                fork = range(r, 5, 6);
                r0 = range(r, 1.7, 2.0);
                r1 = 1.3;
                rx = range(r, 15.5, 17.5);
                pr = 4.2;
                limbs = range(r, 4, 5);
            }
        }
        Trunk t = trunk(r, fork, c, r0, r1, range(r, 0.4, 0.3 * fork + 0.6), range(r, 0.0, 0.6), 0.15);
        t.draw(bp);
        V top = t.at(fork);
        double lift = rx * range(r, 0.5, 0.65) + 1.0;
        V centre = new V(top.x + (top.x - c) * 0.6, fork + lift, top.z + (top.z - c) * 0.6);
        double ry = 0.6 + 0.12 * size.ordinal();
        // overlapping pads at nearly one height make the flat umbrella
        int count = Math.max(1, (int) Math.round(1.3 * rx * rx / (pr * pr)));
        List<V> sites = crownSites(bp, centre, rx, ry, count, pr * 1.2, 0.0, -1.0, 0.0, 0.15, pr + 0.4);
        Style st = new Style(0.25, 0.12, 0.22, 0.5, 0.45, limbs);
        decurrent(bp, top, t.topDirection(), sites, r1, 0, st, pads(pr, 1.3, 0.95));
        // savanna trees spread their roots wide but shallow: a low flare
        roots(bp, c, size, r0, 0.65);
    }

    // =========================================================================================
    // Dark oak: thick short trunks under heavy, wide domes
    // =========================================================================================

    private static void darkOak(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int fork;
        double r0;
        double r1;
        double c = 0.0;
        double rx;
        double rc;
        int limbs;
        switch (size) {
            case YOUNG -> {
                fork = range(r, 1, 2);
                r0 = 0;
                r1 = 0;
                rx = 2.3;
                rc = 1.9;
                limbs = 2;
            }
            case SMALL -> {
                fork = 2;
                r0 = 0.7;
                r1 = 0.7;
                c = 0.5;
                rx = range(r, 3.4, 4.0);
                rc = 2.3;
                limbs = 2;
            }
            case MEDIUM -> {
                fork = range(r, 2, 3);
                r0 = 0.7;
                r1 = 0.7;
                c = 0.5;
                rx = range(r, 5.0, 6.0);
                rc = 2.8;
                limbs = 3;
            }
            case LARGE -> {
                fork = range(r, 3, 4);
                r0 = 1.6;
                r1 = 1.0;
                rx = range(r, 7.0, 8.5);
                rc = 3.0;
                limbs = range(r, 3, 4);
            }
            default -> {
                fork = range(r, 4, 5);
                r0 = 2.1;
                r1 = 1.4;
                rx = range(r, 9.5, 11.0);
                rc = 3.1;
                limbs = range(r, 4, 5);
            }
        }
        heavyDome(bp, fork, c, r0, r1, rx, rc, limbs);
        roots(bp, c, size, r0, 1.1);
    }

    private static void heavyDome(TreeBlueprint bp, int fork, double c, double r0, double r1, double rx, double rc, int limbs) {
        RandomGenerator r = bp.random();
        double ry = rx * range(r, 0.52, 0.6);
        Trunk t = trunk(r, fork, c, r0, r1, range(r, 0.0, 0.5), range(r, 0.0, 0.4), 0.1);
        t.draw(bp);
        V top = t.at(fork);
        V centre = new V(top.x, fork + ry * 0.55, top.z);
        List<V> sites = crownSites(bp, centre, rx, ry, clusterCount(rx, ry, rc, 0.95), rc * 1.1, 0.3, -0.5, 0.25, 0.12, rc + 0.6);
        Style st = new Style(0.4, 0.06, 0.12, 0.42, 0.4, limbs);
        decurrent(bp, top, UP, sites, r1, 0, st, puffs(rc, 0.7, 0.96));
    }

    /** A massive dark oak under a heavy dome over 30 blocks across. */
    private static void ancientDarkOak(TreeBlueprint bp) {
        RandomGenerator r = bp.random();
        double r0 = range(r, 3.0, 3.4);
        heavyDome(bp, range(r, 5, 7), 0.0, r0, r0 * 0.7, range(r, 16.0, 17.5), 3.3, range(r, 5, 6));
        roots(bp, 0.0, TreeSize.ANCIENT, r0, 1.1);
    }

    // =========================================================================================
    // Mangrove: trunks raised on arching prop roots
    // =========================================================================================

    private static void mangrove(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int rootHeight;
        int h;
        int props;
        double rx;
        double r0 = 0.0;
        double c = 0.0;
        switch (size) {
            case YOUNG -> {
                rootHeight = 1;
                h = range(r, 3, 4);
                props = 3;
                rx = 2.2;
            }
            case SMALL -> {
                rootHeight = 2;
                h = range(r, 5, 7);
                props = range(r, 4, 5);
                rx = range(r, 3.0, 3.5);
            }
            case MEDIUM -> {
                rootHeight = range(r, 2, 3);
                h = range(r, 7, 10);
                props = range(r, 5, 7);
                rx = range(r, 4.0, 4.8);
            }
            case LARGE -> {
                rootHeight = range(r, 3, 4);
                h = range(r, 10, 13);
                props = range(r, 7, 9);
                rx = range(r, 5.2, 6.2);
            }
            default -> {
                rootHeight = range(r, 4, 5);
                h = range(r, 12, 16);
                props = range(r, 9, 12);
                rx = range(r, 6.5, 7.5);
                r0 = 0.7;
                c = 0.5;
            }
        }
        // the trunk starts on top of a cluster of roots
        for (int y = 0; y < rootHeight; y++) {
            bp.propRoot(0, y, 0);
            if (r0 > 0) {
                bp.propRoot(1, y, 0);
                bp.propRoot(0, y, 1);
                bp.propRoot(1, y, 1);
            }
        }
        int fork = Math.max(rootHeight + 1, (int) (h * 0.6));
        Trunk t = trunk(r, fork, c, r0, r0, range(r, 0.0, 0.8), range(r, 0.0, 0.6), 0.2);
        t.draw(bp, rootHeight);
        // join the leaning trunk's foot to the top of the root cluster
        bp.path(c, rootHeight, c, t.cx[rootHeight], rootHeight, t.cz[rootHeight], (byte) 0);
        // prop roots arch out from the trunk and land in the mud
        double a0 = angle(r);
        for (int i = 0; i < props; i++) {
            double a = a0 + i * Math.PI * 2.0 / props + range(r, -0.3, 0.3);
            double reach = range(r, 2.0, 2.4 + rootHeight * 0.9 + size.ordinal() * 0.4);
            double sy = range(r, rootHeight * 0.6, rootHeight + 1.5);
            V o = t.at(sy);
            V end = new V(c + Math.cos(a) * reach, -1.0, c + Math.sin(a) * reach);
            V ctrl = o.add(horizontal(a, reach * 0.55)).add(new V(0, 0.8, 0));
            propCurve(bp, o, ctrl, end);
        }
        double ry = rx * 0.55;
        V top = t.at(fork);
        V centre = new V(top.x, h - ry * 0.4, top.z);
        double rc = 2.0 + 0.15 * size.ordinal();
        List<V> sites = crownSites(bp, centre, rx, ry, clusterCount(rx, ry, rc, 0.85), rc * 1.15, 0.35, -0.6, 0.2, 0.28, rc + 0.6);
        Style st = new Style(0.4, 0.05, 0.2, 0.45, 0.45, range(r, 2, 4));
        decurrent(bp, top, UP, sites, 0.0, 0, st, puffs(rc, 0.7, 0.92));
        // aerial roots dropping from low limbs
        if (size.atLeast(TreeSize.LARGE)) {
            int drops = range(r, 2, 4);
            for (int i = 0; i < drops; i++) {
                double a = angle(r);
                V o = t.at(fork - 1.0);
                V e = o.add(horizontal(a, range(r, 3.0, rx * 0.7))).add(new V(0, range(r, 0.5, 1.5), 0));
                limbTo(bp, o, horizontal(a, 1), e, 0, 0, st);
                puffs(rc * 0.85, 0.7, 0.92).grow(bp, e, UP);
                bp.path(e.x, e.y - 1.0, e.z, e.x + range(r, -0.6, 0.6), -1.0, e.z + range(r, -0.6, 0.6), TreeBlueprint.PROP_ROOT);
            }
        }
    }

    private static void propCurve(TreeBlueprint bp, V s, V ctrl, V e) {
        int segments = 8;
        V p = s;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double u = 1.0 - t;
            V n = s.mul(u * u).add(ctrl.mul(2 * u * t)).add(e.mul(t * t));
            bp.path(p.x, p.y, p.z, n.x, n.y, n.z, TreeBlueprint.PROP_ROOT);
            p = n;
        }
    }

    // =========================================================================================
    // Cherry: a dark gnarled trunk forking into thick limbs under a huge dome of blossom
    // =========================================================================================

    private static void cherry(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int fork;
        double r0;
        double r1;
        double c = 0.0;
        double rx;
        double rc;
        int limbs;
        switch (size) {
            case YOUNG -> {
                fork = range(r, 1, 2);
                r0 = 0;
                r1 = 0;
                rx = 2.2;
                rc = 1.8;
                limbs = 2;
            }
            case SMALL -> {
                fork = 2;
                r0 = 0;
                r1 = 0;
                rx = range(r, 3.2, 3.8);
                rc = 2.2;
                limbs = 2;
            }
            case MEDIUM -> {
                fork = 3;
                r0 = 0.7;
                r1 = 0.7;
                c = 0.5;
                rx = range(r, 5.2, 6.2);
                rc = 2.6;
                limbs = 3;
            }
            case LARGE -> {
                fork = range(r, 4, 5);
                r0 = 1.4;
                r1 = 1.0;
                rx = range(r, 7.5, 8.8);
                rc = 2.9;
                limbs = range(r, 3, 4);
            }
            case GIANT -> {
                fork = range(r, 5, 6);
                r0 = 1.9;
                r1 = 1.4;
                rx = range(r, 10.0, 11.0);
                rc = 3.0;
                limbs = 4;
            }
            default -> {
                fork = range(r, 6, 8);
                r0 = range(r, 2.6, 3.0);
                r1 = 2.1;
                rx = range(r, 15.5, 17.5);
                rc = 3.2;
                limbs = range(r, 4, 5);
            }
        }
        double ry = rx * range(r, 0.42, 0.5);
        Trunk t = trunk(r, fork, c, r0, r1, range(r, 0.2, 0.25 * fork + 0.4), range(r, 0.2, 0.6), 0.3);
        t.draw(bp);
        V top = t.at(fork);
        // the dome rides high on the limbs and arches over them; its rim hangs lower, fringed with blossom
        V centre = new V(top.x + (top.x - c) * 0.5, fork + ry + 1.5, top.z + (top.z - c) * 0.5);
        List<V> sites = crownSites(bp, centre, rx, ry, clusterCount(rx, ry, rc, 0.9), rc * 1.12, 0.35, -0.3, 0.6, 0.12, rc + 0.6);
        Style st = new Style(0.3, 0.14, 0.24, 0.5, 0.36, limbs);
        decurrent(bp, top, t.topDirection(), sites, r1, 0, st, weeping(rc, 0.7, 0.95, size.atLeast(TreeSize.MEDIUM) ? 3 : 1, 2));
        roots(bp, c, size, r0, 1.0);
    }

    // =========================================================================================
    // Azalea: small gnarled trees with flowering foliage
    // =========================================================================================

    private static void azalea(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int fork = switch (size) {
            case YOUNG -> 1;
            case SMALL -> 2;
            case MEDIUM -> range(r, 2, 3);
            default -> range(r, 3, 4);
        };
        double rx = 2.0 + 0.5 * Math.min(4, size.ordinal());
        Trunk t = trunk(r, fork, 0.0, 0.0, 0.0, range(r, 0.3, 1.2), range(r, 0.3, 0.8), 0.35);
        t.draw(bp);
        V top = t.at(fork);
        double ry = rx * 0.62;
        double rc = 1.8 + 0.12 * size.ordinal();
        V centre = new V(top.x, fork + ry * 0.75, top.z);
        List<V> sites = crownSites(bp, centre, rx, ry, clusterCount(rx, ry, rc, 0.8), rc * 1.1, 0.3, -0.5, 0.2, 0.2, rc + 0.6);
        Style st = new Style(0.35, 0.05, 0.25, 0.5, 0.45, 2);
        decurrent(bp, top, t.topDirection(), sites, 0.0, 0, st, puffs(rc, 0.72, 0.92));
        bp.sprinkleAltLeaves(0.4);
        for (int y = -1; y >= -3; y--) {
            bp.rootedDirt(0, y, 0);
        }
    }

    // =========================================================================================
    // Dead trees and snags
    // =========================================================================================

    /** A leafless broadleaf: a leaning trunk with a few crooked, partly broken limbs. */
    private static void dead(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int h = switch (size) {
            case YOUNG -> range(r, 2, 3);
            case SMALL -> range(r, 4, 6);
            case MEDIUM -> range(r, 6, 9);
            case LARGE -> range(r, 9, 13);
            default -> range(r, 13, 17);
        };
        double r0 = size == TreeSize.LARGE ? 0.7 : size.atLeast(TreeSize.GIANT) ? 1.3 : 0.0;
        double c = size == TreeSize.LARGE ? 0.5 : 0.0;
        int fork = Math.max(1, (int) (h * range(r, 0.45, 0.65)));
        Trunk t = trunk(r, fork, c, r0, r0 >= 0.95 ? 0.7 : r0, range(r, 0.0, 0.15 * h), range(r, 0.0, 0.7), 0.2);
        t.draw(bp);
        if (size == TreeSize.YOUNG) {
            return;
        }
        V top = t.at(fork);
        double rx = h * 0.4;
        double ry = (h - fork) * 0.6 + 0.5;
        V centre = new V(top.x, fork + ry * 0.7, top.z);
        List<V> ends = crownSites(bp, centre, rx, ry, 3 + size.ordinal(), 2.2, 0.6, -0.3, 0.0, 0.3, 1.0);
        Style st = new Style(0.3, 0.05, 0.3, 0.5, 0.45, range(r, 2, 3));
        decurrent(bp, top, t.topDirection(), ends, r0 >= 0.95 ? 0.7 : 0.0, 0, st, bareTwigs(1.5 + 0.3 * size.ordinal()));
        roots(bp, c, size.smaller(), r0, 0.9);
    }

    /** A dead conifer: short bare whorl stubs, more of them low down, and a broken top. */
    private static void deadSpruce(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int h = switch (size) {
            case YOUNG -> range(r, 3, 4);
            case SMALL -> range(r, 5, 8);
            case MEDIUM -> range(r, 8, 13);
            case LARGE -> range(r, 13, 19);
            default -> range(r, 18, 26);
        };
        double r0 = size.atLeast(TreeSize.LARGE) ? 0.7 : 0.0;
        double c = r0 > 0 ? 0.5 : 0.0;
        Trunk t = trunk(r, h - 1, c, r0, r0, range(r, 0.0, 0.06 * h), range(r, 0.0, 0.5), 0.0);
        t.draw(bp);
        // break the top first, so the stubs below never hang on blocks that broke away
        jaggedTop(bp, h, 2);
        for (int y = Math.max(1, h / 5); y < h - 2; y += range(r, 2, 3)) {
            double s = (double) y / h;
            int spokes = range(r, 1, 3);
            for (int i = 0; i < spokes; i++) {
                if (r.nextFloat() < 0.3f + 0.4f * s) {
                    continue;
                }
                double a = angle(r);
                double len = range(r, 1.0, 1.0 + 2.2 * (1.0 - s)) + (r0 > 0 ? 0.6 : 0.0);
                V o = t.at(y);
                V e = o.add(horizontal(a, len + (r0 > 0 ? 0.7 : 0.0))).add(new V(0, -0.3 * len, 0));
                bp.path(o.x, y, o.z, e.x, e.y, e.z, (byte) 0);
            }
        }
        roots(bp, c, size.smaller(), r0, 0.8);
    }

    /**
     * A fire-killed snag: a charred trunk with a broken, jagged top, a few short stubs where limbs
     * burned off, sometimes one broken limb or a dead second leader, and a fire scar at the foot of
     * thick trunks. No leaves.
     */
    private static void burned(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int h;
        double r0;
        double c = 0.0;
        switch (size) {
            case YOUNG -> {
                h = range(r, 2, 4);
                r0 = 0;
            }
            case SMALL -> {
                h = range(r, 4, 7);
                r0 = 0;
            }
            case MEDIUM -> {
                h = range(r, 7, 11);
                r0 = r.nextFloat() < 0.4f ? 0.7 : 0.0;
                c = r0 > 0 ? 0.5 : 0.0;
            }
            case LARGE -> {
                h = range(r, 10, 15);
                r0 = 0.7;
                c = 0.5;
            }
            default -> {
                h = range(r, 14, 20);
                r0 = range(r, 1.3, 1.7);
            }
        }
        Trunk t = trunk(r, h - 1, c, r0, r0 >= 0.95 ? 0.7 : r0, range(r, 0.0, 0.1 * h), range(r, 0.0, 0.6), 0.1);
        t.draw(bp);
        jaggedTop(bp, h, r0 > 0 ? 3 : 1);
        Style st = new Style(0.3, 0.05, 0.2, 0.5, 0.5, 1);
        int stubs = size == TreeSize.YOUNG ? 0 : range(r, 1, 2 + Math.min(3, size.ordinal()));
        double sa = angle(r);
        for (int i = 0; i < stubs; i++) {
            // short, nearly level stubs spread around and along the stem
            double y = Math.rint(range(r, h * 0.3, h - 2.5));
            V o = t.at(y);
            sa += range(r, 1.6, 2.8);
            double len = range(r, 0.9, 1.7) + t.radius(y);
            V e = o.add(horizontal(sa, len)).add(new V(0, len * range(r, -0.25, 0.3), 0));
            bp.path(o.x, o.y, o.z, e.x, e.y, e.z, (byte) 0);
        }
        if (size.atLeast(TreeSize.MEDIUM) && r.nextFloat() < 0.5f) {
            // one limb that broke off further out, with a short side twig
            double y = Math.rint(range(r, h * 0.45, h - 3.0));
            V o = t.at(y);
            double a = sa + range(r, 1.6, 2.8);
            double len = range(r, 2.5, 4.0) + t.radius(y);
            V e = o.add(horizontal(a, len)).add(new V(0, len * range(r, 0.3, 0.6), 0));
            V d = limbTo(bp, o, horizontal(a, 1).add(UP).norm(), e, 0, 0, st);
            V twig = e.add(perpendicular(d, r).mul(1.4)).add(new V(0, 0.8, 0));
            bp.path(e.x, e.y, e.z, twig.x, twig.y, twig.z, (byte) 0);
        }
        if (size.atLeast(TreeSize.MEDIUM) && r.nextFloat() < 0.25f) {
            // a dead second leader from a fork, broken shorter than the main stem
            double y = Math.rint(h * range(r, 0.5, 0.65));
            V o = t.at(y);
            double a = angle(r);
            V e = o.add(horizontal(a, range(r, 1.5, 2.5))).add(new V(0, (h - y) * range(r, 0.4, 0.7), 0));
            limbTo(bp, o, UP, e, 0, 0, st);
        }
        if (r0 >= 0.7 && r.nextFloat() < 0.6f) {
            fireScar(bp, t, c, r0);
        }
        if (size.atLeast(TreeSize.SMALL)) {
            bp.rootSystem(c, range(r, 3, 5), r0, 2.0 + size.ordinal() * 0.9, 0.3 + 0.3 * size.ordinal(), 0.5);
        }
    }

    /** Breaks the top of a trunk off unevenly: each column of the top {@code depth} layers may end early. */
    private static void jaggedTop(TreeBlueprint bp, int h, int depth) {
        RandomGenerator r = bp.random();
        for (int x = bp.minX(); x <= bp.maxX(); x++) {
            for (int z = bp.minZ(); z <= bp.maxZ(); z++) {
                int cut = h - 1 - r.nextInt(depth + 1);
                if (TreeBlueprint.isWoody(bp.get(x, h - 1, z)) || TreeBlueprint.isWoody(bp.get(x, cut, z))) {
                    for (int y = cut + 1; y <= h + 1; y++) {
                        if (bp.get(x, y, z) == TreeBlueprint.LOG_Y) {
                            bp.clear(x, y, z);
                        }
                    }
                }
            }
        }
    }

    /** A fire scar ("cat face"): a hollow notch burned into one side of the trunk's foot. */
    private static void fireScar(TreeBlueprint bp, Trunk t, double c, double r0) {
        RandomGenerator r = bp.random();
        double a = angle(r);
        int top = range(r, 2, 3);
        for (int y = 1; y <= top; y++) {
            V o = t.at(y);
            double depth = Math.max(0.6, t.radius(y)) * (1.0 - 0.25 * (y - 1));
            int x = TreeBlueprint.round(o.x + Math.cos(a) * depth);
            int z = TreeBlueprint.round(o.z + Math.sin(a) * depth);
            bp.clear(x, y, z);
        }
    }
}
