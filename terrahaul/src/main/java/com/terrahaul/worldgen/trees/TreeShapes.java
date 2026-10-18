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
 * the pipe model; trees with a central leader (birch, jungle, pine) send short branches from the
 * trunk to the sites instead. Every cluster sits on a twig tip, which keeps all leaves within six
 * blocks of wood. Conifers are drawn as whorls of drooping needle skirts on branches that run along
 * their undersides; Scots pines carry flat needle pads instead.
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

    /** Flat-bottomed cloud pads (acacia, pine, bonsai-like oaks). */
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
    // Conifers
    // =========================================================================================

    /**
     * Layers of drooping branches from {@code start} to the top. Each whorl is a skirt of needles,
     * two blocks thick near the trunk and one at its ragged rim, sagging towards the rim and carried
     * by branches along its underside that fork towards the rim. The skirt radius follows
     * {@code maxR * (1 - s)^profile} from the lowest whorl ({@code s = 0}) to the top, giving the
     * layered cone of spruces and firs with darker gaps between the layers.
     *
     * @param droop      how far a skirt sags per block of radius
     * @param thickLimbs radius of the long lower branches of big trees (0: thin)
     */
    private static void whorls(TreeBlueprint bp, Trunk t, int start, int h, double maxR, int spacing, double droop, double profile,
                               double density, double thickLimbs) {
        RandomGenerator r = bp.random();
        double a0 = angle(r);
        int y = start;
        while (y < h - 2) {
            double s = (double) (y - start) / Math.max(1, h - 1 - start);
            double rad = maxR * Math.pow(1.0 - s, profile) * range(r, 0.88, 1.08) + 0.6;
            V o = t.at(y);
            if (rad < 1.6) {
                bp.disc(o.x, y, o.z, rad + 0.4, 0.92);
                bp.disc(o.x, y + 1, o.z, rad * 0.6, 0.85);
            } else {
                skirt(bp, o, y, rad, droop, density);
                int spokes = Math.max(4, Math.min(11, (int) Math.round(rad * 1.1)));
                a0 += 2.4;
                for (int i = 0; i < spokes; i++) {
                    double a = a0 + i * Math.PI * 2.0 / spokes + range(r, -0.2, 0.2);
                    double len = rad * range(r, 0.62, 0.78);
                    double sag = droop * len * 0.6;
                    V end = o.add(horizontal(a, len)).add(new V(0, -sag, 0));
                    bp.limb(o.x, y, o.z, end.x, end.y, end.z, len > 5.0 ? thickLimbs : 0.0, 0.0);
                    if (rad > 4.0) {
                        V mid = o.add(horizontal(a, len * 0.55)).add(new V(0, -sag * 0.4, 0));
                        V side = mid.add(horizontal(a + (r.nextBoolean() ? 0.6 : -0.6), len * 0.45)).add(new V(0, -sag * 0.5, 0));
                        bp.path(mid.x, mid.y, mid.z, side.x, side.y, side.z, (byte) 0);
                    }
                }
            }
            // needles close around the trunk between the layers
            bp.disc(o.x, y + 2, o.z, 1.2, 0.8);
            y += spacing + (spacing > 1 && r.nextFloat() < 0.3f ? 1 : 0);
        }
        // the leader above the last small whorls
        V top = t.at(h - 1);
        int tx = TreeBlueprint.round(top.x);
        int tz = TreeBlueprint.round(top.z);
        bp.disc(top.x, h - 2, top.z, 1.7, 0.95);
        bp.disc(top.x, h - 1, top.z, 1.4, 0.95);
        bp.leaf(tx, h, tz);
        bp.leaf(tx, h + 1, tz);
        if (h > 12) {
            bp.leaf(tx, h + 2, tz);
        }
    }

    /** One drooping skirt of needles around {@code o}, its top at {@code y + 1} by the trunk. */
    private static void skirt(TreeBlueprint bp, V o, int y, double rad, double droop, double density) {
        RandomGenerator r = bp.random();
        double p1 = angle(r);
        double p2 = angle(r);
        int ri = TreeBlueprint.ceil(rad * 1.25) + 1;
        int ix = TreeBlueprint.round(o.x);
        int iz = TreeBlueprint.round(o.z);
        for (int x = ix - ri; x <= ix + ri; x++) {
            for (int z = iz - ri; z <= iz + ri; z++) {
                double dx = x - o.x;
                double dz = z - o.z;
                double a = Math.atan2(dz, dx);
                double edge = rad * (1.0 + 0.14 * Math.sin(3.0 * a + p1) + 0.08 * Math.sin(5.0 * a + p2));
                double d = Math.sqrt(dx * dx + dz * dz) / edge;
                if (d > 1.0 || d > 0.8 && r.nextDouble() < (d - 0.8) / 0.2 * 0.6) {
                    continue;
                }
                int top = TreeBlueprint.round(y + 1 - droop * rad * Math.pow(d, 1.6));
                int thick = d < 0.75 ? 2 : 1;
                for (int yy = top - thick + 1; yy <= top; yy++) {
                    if (r.nextDouble() < density) {
                        bp.leaf(x, yy, z);
                    }
                }
            }
        }
    }

    private static void spruce(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int h;
        double r0;
        double r1 = 0.0;
        double c = 0.0;
        double bare;
        int spacing;
        double thick = 0.0;
        switch (size) {
            case YOUNG -> {
                h = range(r, 3, 5);
                r0 = 0;
                bare = 0.1;
                spacing = 1;
            }
            case SMALL -> {
                h = range(r, 6, 9);
                r0 = 0;
                bare = 0.12;
                spacing = 2;
            }
            case MEDIUM -> {
                h = range(r, 11, 17);
                r0 = 0;
                bare = 0.16;
                spacing = 2;
            }
            case LARGE -> {
                h = range(r, 18, 27);
                r0 = 0.7;
                c = 0.5;
                bare = 0.2;
                spacing = 3;
            }
            case GIANT -> {
                h = range(r, 28, 40);
                r0 = 1.6;
                bare = 0.26;
                spacing = 3;
                thick = 0.62;
            }
            default -> {
                h = range(r, 48, 60);
                r0 = range(r, 2.3, 2.7);
                bare = 0.3;
                spacing = 3;
                thick = 0.9;
            }
        }
        Trunk t = trunk(r, h - 1, c, r0, r1, range(r, 0.0, 0.03 * h), range(r, 0.0, 0.5), 0.0);
        t.draw(bp);
        double maxR = 0.16 * h + 1.2;
        whorls(bp, t, Math.max(1, (int) (h * bare)), h, maxR, spacing, 0.3, 0.95, 0.94, thick);
        if (size.atLeast(TreeSize.LARGE)) {
            roots(bp, c, size, r0, 0.9);
        } else if (size.atLeast(TreeSize.SMALL)) {
            roots(bp, c, size.smaller(), r0, 0.9);
        }
    }

    private static void fir(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int h = switch (size) {
            case YOUNG -> range(r, 3, 5);
            case SMALL -> range(r, 6, 9);
            case MEDIUM -> range(r, 10, 15);
            case LARGE -> range(r, 15, 22);
            default -> range(r, 22, 30);
        };
        double r0 = size.atLeast(TreeSize.GIANT) ? 0.7 : 0.0;
        double c = r0 > 0 ? 0.5 : 0.0;
        Trunk t = trunk(r, h - 1, c, r0, 0.0, range(r, 0.0, 0.02 * h), 0.0, 0.0);
        t.draw(bp);
        // a narrow, dense spire
        double maxR = 0.12 * h + 1.3;
        whorls(bp, t, Math.max(1, (int) (h * 0.08)), h, maxR, size.atLeast(TreeSize.MEDIUM) ? 2 : 1, 0.22, 0.9, 0.97, 0.0);
        if (size.atLeast(TreeSize.MEDIUM)) {
            roots(bp, c, size.smaller(), r0, 0.8);
        }
    }

    /** Scots-pine-like: a tall, bare, straight trunk under an irregular crown of flat needle tufts. */
    private static void pine(TreeBlueprint bp, TreeSize size) {
        RandomGenerator r = bp.random();
        int h;
        double r0;
        double c = 0.0;
        switch (size) {
            case YOUNG -> {
                h = range(r, 4, 6);
                r0 = 0;
            }
            case SMALL -> {
                h = range(r, 8, 11);
                r0 = 0;
            }
            case MEDIUM -> {
                h = range(r, 14, 20);
                r0 = 0;
            }
            case LARGE -> {
                h = range(r, 20, 27);
                r0 = 0.7;
                c = 0.5;
            }
            case GIANT -> {
                h = range(r, 27, 36);
                r0 = 1.5;
            }
            default -> {
                h = range(r, 40, 50);
                r0 = range(r, 2.0, 2.4);
            }
        }
        if (size == TreeSize.YOUNG) {
            // young pines are still conical
            Trunk t = trunk(r, h - 1, 0, 0, 0, 0, 0, 0);
            t.draw(bp);
            whorls(bp, t, 1, h, 1.6, 1, 0.1, 0.9, 0.92, 0.0);
            return;
        }
        double r1 = r0 >= 0.95 ? 0.7 : r0;
        Trunk t = trunk(r, h - 1, c, r0, r1, range(r, 0.0, 0.05 * h), range(r, 0.0, 0.8), 0.05);
        t.draw(bp);
        // an irregular, tiered crown of flat needle pads on long, upswept branches
        double rx = 0.17 * h + 2.0;
        double ry = 0.14 * h + 1.5;
        double pr = 2.0 + 0.3 * Math.min(4, size.ordinal());
        V centre = t.at(h - ry * 0.85);
        List<V> sites = crownSites(bp, centre, rx, ry, clusterCount(rx, ry, pr, 0.6), pr * 1.35, 0.35, -0.9, 0.0, 0.35, pr + 0.6);
        Style st = new Style(0.4, 0.12, 0.15, 0.6, 0.5, 1);
        double radius = size.atLeast(TreeSize.GIANT) ? 0.7 : 0.0;
        excurrent(bp, t, sites, Math.toRadians(range(r, 18, 32)), h * 0.55, h - 2.5, radius, st, pads(pr, 1.3, 0.95));
        V top = t.at(h - 1);
        bp.pad(top.x, h - 1, top.z, pr * 1.05, 1.6, 0.95);
        roots(bp, c, size, r0, 0.9);
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
