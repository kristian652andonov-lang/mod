package com.terrahaul.worldgen.ruins;

import static com.terrahaul.worldgen.noise.TMath.clamp;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Plans the ruined houses beside trails: hand-designed footprints (cottage, log cabin, stone
 * bothy, farmstead, adobe house, stilt hut, burned cabin, watchtower and lean-to shelter) built
 * intact in a {@link Canvas} and then ruined by a few coherent processes, the way abandoned houses
 * really decay:
 * <ol>
 *   <li>walls lose height along the perimeter following smooth noise plus one or two breaches
 *       (stepped V-notches, never straight cuts); corners and chimneys stand longest, and at least
 *       a quarter of every perimeter stays near full height so the house still reads as one;</li>
 *   <li>the roof falls in patches; a roof cell only stays if a chain of roof cells connects it to
 *       a wall that survived to full height, so no roof piece hangs in the air;</li>
 *   <li>floors rot to earth where the sky is open; fallen roof pieces and beams lie inside and
 *       masonry rubble spills outward below the gaps;</li>
 *   <li>moss grows on low and wet stone in coherent patches, vines hang from wall tops, leaves
 *       overgrow corners and crowns, grass and ferns colonise the earth floor.</li>
 * </ol>
 * Furniture, loot, a footpath to the trail, campfire rings and woodpiles make them lived-in once.
 * The finished plan goes through {@link RuinPlan#prune()}, the final guarantee that nothing floats.
 * Every choice comes from the site seed, so all chunks of a ruin draw the same house.
 */
public final class HousePlanner {
    public static final int COTTAGE = 0;
    public static final int LOG_CABIN = 1;
    public static final int BOTHY = 2;
    public static final int FARMSTEAD = 3;
    public static final int ADOBE = 4;
    public static final int STILT_HUT = 5;
    public static final int CHIMNEY_RUIN = 6;
    public static final int WATCHTOWER = 7;
    public static final int LEAN_TO = 8;
    public static final String[] NAMES = {"cottage", "log_cabin", "bothy", "farmstead", "adobe", "stilt_hut", "chimney_ruin",
        "watchtower", "lean_to"};

    // wall styles
    private static final int STONE_WALLS = 0;
    private static final int TIMBER = 1;
    private static final int LOGS = 2;
    private static final int PLANK_WALLS = 3;
    // roofs
    private static final int GABLE_U = 0;
    private static final int GABLE_V = 1;
    private static final int FLAT = 2;
    private static final int PYRAMID = 3;
    private static final int PARAPET = 4;
    private static final int NO_ROOF = 5;

    /*
     * Footprints, front row (v = 0) facing the trail. C corner, # wall, W window (2 high on tall
     * walls), w small window, D doorway, H hearth with the chimney stack outside, . floor,
     * , earth floor, L ladder.
     */
    private static final String[] COTTAGE_ROWS = {
        "C#W#D#W#C",
        "#.......#",
        "W.......#",
        "#.......H",
        "#.......#",
        "#.......W",
        "C##W#W##C"};
    private static final String[] CABIN_ROWS = {
        "CW#D#WC",
        "#.....#",
        "H.....#",
        "#.....w",
        "#.....#",
        "C##W##C"};
    private static final String[] BOTHY_ROWS = {
        "C##D##C",
        "#.....#",
        "#.....H",
        "#.....#",
        "C#w#w#C"};
    private static final String[] ADOBE_ROWS = {
        "#W#D#W#",
        "#.....#",
        "#.....#",
        "W.....W",
        "#.....#",
        "#.....#",
        "##w#w##"};
    private static final String[] STILT_ROWS = {
        "C#D#C",
        "#...#",
        "W...W",
        "#...#",
        "C#W#C"};
    private static final String[] CHIMNEY_ROWS = {
        "C##D##C",
        "#,,,,,#",
        "#,,,,,H",
        "#,,,,,#",
        "#,,,,,#",
        "C#####C"};
    private static final String[] TOWER_ROWS = {
        "C#D#C",
        "#...#",
        "w...w",
        "#.L.#",
        "C###C"};

    /**
     * Size of a template: its main body ({@code w} x {@code d}, walls {@code hw} high) and the
     * canonical box of everything it places apart from the footpath to the trail.
     */
    public record Spec(int w, int d, int hw, int minU, int minV, int maxU, int maxV, int doorU, double maxRange) {
    }

    private static final Spec[] SPECS = {
        new Spec(9, 7, 4, -7, -3, 15, 10, 4, 4.0),
        new Spec(7, 6, 4, -7, -4, 13, 9, 3, 4.0),
        new Spec(7, 5, 3, -7, -3, 13, 8, 3, 4.0),
        new Spec(9, 7, 4, -7, -3, 18, 15, 4, 4.0),
        new Spec(7, 7, 4, -7, -3, 13, 10, 3, 4.0),
        new Spec(5, 5, 3, -6, -4, 10, 8, 2, 3.0),
        new Spec(7, 6, 4, -7, -3, 13, 9, 3, 4.0),
        new Spec(5, 5, 10, -6, -3, 10, 8, 2, 3.0),
        new Spec(4, 3, 2, -4, -6, 7, 5, 1, 3.0)};

    public static Spec spec(int template) {
        return SPECS[template];
    }

    private static final SimplexNoise NOISE = new SimplexNoise(0x52554E53L);

    private final TrailRuin site;
    private final Spec spec;
    private final SplittableRandom rnd;
    private final Canvas c;
    private final double wet;
    private final double o1;
    private final double o2;
    private final double o3;
    private final boolean hot;
    private double decay;
    /** Cells (u, v) at floor level that already hold a prop or must stay free. */
    private final List<int[]> reserved = new ArrayList<>();
    /** Where the footpath starts in front of the door (further out past a porch or a ladder). */
    private int pathStart = -1;

    private HousePlanner(TrailRuin site) {
        this.site = site;
        this.spec = SPECS[site.template];
        this.rnd = new SplittableRandom(site.seed);
        this.wet = site.wetness;
        this.decay = site.decay;
        this.o1 = SeedMixer.unit(site.seed * 31L) * 200.0;
        this.o2 = SeedMixer.unit(site.seed * 37L) * 200.0;
        this.o3 = SeedMixer.unit(site.seed * 41L) * 200.0;
        this.hot = site.climate == RuinSites.HOT_DRY;
        this.c = new Canvas(spec.minU() - 1, Math.min(spec.minV(), -site.pathLength) - 1, spec.maxU() + 1, spec.maxV() + 1, -5, spec.hw() + 12);
    }

    /** The finished, pruned plan of a trail ruin. */
    public static RuinPlan plan(TrailRuin site) {
        return new HousePlanner(site).build();
    }

    private RuinPlan build() {
        switch (site.template) {
            case COTTAGE -> cottage(0, 0, COTTAGE_ROWS);
            case LOG_CABIN -> cabin();
            case BOTHY -> {
                Body b = new Body(0, 0, BOTHY_ROWS, 3, STONE_WALLS, GABLE_U, 0);
                b.build();
                bothyProps(b);
                yard(b);
            }
            case FARMSTEAD -> farmstead();
            case ADOBE -> adobe();
            case STILT_HUT -> stiltHut();
            case CHIMNEY_RUIN -> chimneyRuin();
            case WATCHTOWER -> watchtower();
            default -> leanTo();
        }
        footpath();
        RuinPlan plan = c.toPlan(site.frame, RuinPlan.ABSOLUTE, site.floorY, site.materials, false);
        plan.prune();
        return plan;
    }

    // =========================================================================================
    // Noise helpers: coherent fields offset per site, so no two ruins share a pattern
    // =========================================================================================

    private double n2(double a, double b) {
        return NOISE.noise2(a + o1, b + o2);
    }

    private double n3(double a, double b, double cc) {
        return NOISE.noise3(a + o2, b + o3, cc + o1);
    }

    private boolean chance(double p) {
        return rnd.nextDouble() < p;
    }

    /** Overgrowth vigour: wet sites and older ruins are greener. */
    private double vigour() {
        return clamp(wet, 0.05, 1.0) * (0.45 + 0.55 * decay);
    }

    // =========================================================================================
    // Templates
    // =========================================================================================

    private void cottage(int ou, int ov, String[] rows) {
        boolean timber = site.materials.stone != RuinMaterials.MUD && site.materials.stone != RuinMaterials.MOSSY
            && site.materials.stone != RuinMaterials.ASHLAR && chance(0.6);
        Body b = new Body(ou, ov, rows, 4, timber ? TIMBER : STONE_WALLS, GABLE_U, 0);
        b.thatch = site.climate == RuinSites.TEMPERATE && chance(0.2);
        b.build();
        houseProps(b, true);
        yard(b);
    }

    private void cabin() {
        Body b = new Body(0, 0, CABIN_ROWS, 4, LOGS, GABLE_U, 0);
        b.plinth = site.climate == RuinSites.UPLAND;
        boolean porch = chance(0.5);
        b.build();
        if (porch) {
            porch(b);
        }
        houseProps(b, false);
        yard(b);
    }

    private void farmstead() {
        cottage(0, 0, COTTAGE_ROWS);
        shed(11, 1);
        well(-5, 2);
        garden(0, 9);
    }

    private void adobe() {
        Body b = new Body(0, 0, ADOBE_ROWS, 4, STONE_WALLS, FLAT, 0);
        b.build();
        adobeProps(b);
        horno(b.w + 2, 2);
        yard(b);
    }

    private void stiltHut() {
        Body b = new Body(0, 0, STILT_ROWS, 3, PLANK_WALLS, GABLE_V, 3);
        b.thatch = true;
        b.build();
        // stilts: corners and side midpoints from the ground to the platform, corners up to the eaves
        int[][] stilts = {{0, 0}, {4, 0}, {0, 4}, {4, 4}, {2, 0}, {0, 2}, {4, 2}, {2, 4}, {0, -1}, {4, -1}, {2, -1}};
        for (int[] s : stilts) {
            boolean corner = (s[0] == 0 || s[0] == 4) && (s[1] == 0 || s[1] == 4);
            int top = s[1] < 0 ? 2 : corner ? b.y0 + b.h(s[0], s[1]) : 2;
            for (int y = 0; y <= top; y++) {
                if (y < b.y0 || corner || s[1] < 0) {
                    c.set(s[0], y, s[1], RuinCell.of(RuinPart.LOG));
                }
            }
            c.setFill(s[0], s[1], RuinCell.of(RuinPart.LOG));
        }
        // balcony along the front, with a ladder up from the ground
        for (int u = 0; u < 5; u++) {
            if (u == 1 || u == 3 || n2(u * 0.7, 5.0) + decay * 0.8 < 0.8) {
                c.set(u, 3, -1, RuinCell.of(RuinPart.PLANK_SLAB) | RuinCell.TOP);
            }
            if ((u == 0 || u == 4) && !chance(decay * 0.6)) {
                c.set(u, 4, -1, RuinCell.of(RuinPart.FENCE));
            }
        }
        c.set(2, 3, -1, RuinCell.of(RuinPart.PLANKS));
        pathStart = -3;
        for (int y = 1; y <= 3; y++) {
            c.set(2, y, -2, RuinCell.of(RuinPart.LADDER, RuinCell.NORTH));
        }
        // under the hut: leave the ground, but keep the space under the platform open
        for (int u = 0; u < 5; u++) {
            for (int v = 0; v < 5; v++) {
                for (int y = 1; y < b.y0; y++) {
                    c.add(u, y, v, RuinCell.of(RuinPart.AIR) | RuinCell.SOFT);
                }
            }
        }
        reserve(2, 1);
        int[] cell = propCell(b, false);
        if (cell != null) {
            container(cell[0], b.y0 + 1, cell[1], cell[2], chance(0.5));
        }
        cell = propCell(b, false);
        if (cell != null && chance(0.4)) {
            c.set(cell[0], b.y0 + 1, cell[1], RuinCell.of(RuinPart.FLOWER_POT));
        }
        campfireRing(b, chance(0.5), 0);
        woodpileUnder(b);
    }

    private void chimneyRuin() {
        decay = Math.max(decay, 0.75);
        Body b = new Body(0, 0, CHIMNEY_ROWS, 4, STONE_WALLS, NO_ROOF, 0);
        b.burned = true;
        b.build();
        // charred beams on the earth floor
        int beams = 2 + rnd.nextInt(3);
        for (int i = 0; i < beams; i++) {
            int v = 1 + rnd.nextInt(b.d - 2);
            int u0 = 1 + rnd.nextInt(2);
            int len = 2 + rnd.nextInt(3);
            boolean stripped = chance(0.4);
            for (int u = u0; u < Math.min(b.w - 1, u0 + len); u++) {
                if (c.empty(u, 1, v)) {
                    int cell = RuinCell.withAxis(RuinCell.withAux(RuinCell.of(stripped ? RuinPart.STRIPPED_LOG : RuinPart.LOG), 1), RuinCell.AXIS_X);
                    c.set(u, 1, v, cell);
                }
            }
        }
        for (int u = 1; u < b.w - 1; u++) {
            for (int v = 1; v < b.d - 1; v++) {
                if (RuinCell.part(c.get(u, 0, v)) == RuinPart.SOIL && n2(u * 0.4, v * 0.4) > -0.1) {
                    c.set(u, 0, v, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 1));
                }
            }
        }
        if (chance(0.5)) {
            int[] cell = propCell(b, false);
            if (cell != null) {
                c.set(cell[0], 1, cell[1], RuinCell.of(RuinPart.CAULDRON));
            }
        }
        floorPlants(b);
        yard(b);
    }

    private void watchtower() {
        Body b = new Body(0, 0, TOWER_ROWS, 10, STONE_WALLS, chance(0.4) ? PYRAMID : PARAPET, 0);
        b.tower = true;
        b.build();
        // arrow slits higher up the walls
        int[][] slits = {{0, 2, 6}, {4, 2, 9}, {2, 0, 7}, {4, 1, 5}};
        for (int[] s : slits) {
            if (b.h(s[0], s[1]) > s[2]) {
                c.set(s[0], s[2], s[1], RuinCell.of(RuinPart.AIR));
            }
        }
        // ladder up the inside of the back wall
        for (int y = 1; y <= b.hw; y++) {
            if (b.h(2, 4) >= y) {
                c.set(2, y, 3, RuinCell.of(RuinPart.LADDER, RuinCell.NORTH));
            }
        }
        reserve(2, 3);
        int[] cell = propCell(b, false);
        if (cell != null && chance(0.6)) {
            container(cell[0], 1, cell[1], cell[2], false);
        }
        cell = propCell(b, false);
        if (cell != null) {
            c.set(cell[0], 1, cell[1], RuinCell.of(RuinPart.BARREL, RuinCell.NORTH) | RuinCell.TOP);
        }
        yard(b);
    }

    private void leanTo() {
        int w = 4;
        // back wall of logs, short side walls, two posts at the open front and a slab roof
        for (int u = 0; u < w; u++) {
            c.setFill(u, 2, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 0, 2, RuinCell.of(RuinPart.FOUNDATION));
            for (int y = 1; y <= 2; y++) {
                if (y == 1 || !chance(decay * 0.5)) {
                    c.set(u, y, 2, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_X));
                }
            }
            c.set(u, 0, 0, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 1));
            c.set(u, 0, 1, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 1));
            c.setFill(u, 0, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 3));
            c.setFill(u, 1, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 3));
        }
        for (int u : new int[]{0, w - 1}) {
            c.setFill(u, 1, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 0, 1, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 1, 1, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_Z));
            c.setFill(u, 0, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 0, 0, RuinCell.of(RuinPart.FOUNDATION));
            for (int y = 1; y <= 3; y++) {
                c.set(u, y, 0, RuinCell.of(RuinPart.FENCE));
            }
        }
        for (int u = -1; u <= w; u++) {
            boolean keep = n2(u * 0.6, 3.0) + decay > 1.05 ? false : true;
            if (!keep && u > 0 && u < w - 1) {
                continue;
            }
            c.set(u, 4, 0, RuinCell.of(RuinPart.ROOF_SLAB));
            c.set(u, 3, 1, RuinCell.of(RuinPart.ROOF_SLAB) | RuinCell.TOP);
            c.set(u, 3, 2, RuinCell.of(RuinPart.ROOF_SLAB));
        }
        for (int u = 1; u < w - 1; u++) {
            for (int y = 1; y <= 3; y++) {
                c.add(u, y, 0, RuinCell.of(RuinPart.AIR));
                c.add(u, y, 1, RuinCell.of(RuinPart.AIR));
            }
        }
        // a bed of leaves or hay inside, a barrel, and always a fire ring in front
        if (chance(0.5)) {
            c.set(1, 1, 1, RuinCell.of(RuinPart.HAY));
        }
        if (chance(0.5)) {
            c.set(2, 1, 1, RuinCell.of(RuinPart.BARREL, RuinCell.NORTH) | RuinCell.TOP);
            if (chance(0.5)) {
                c.extras.add(new RuinPlan.Extra(2, 1, 1, false, site.lootTable, rnd.nextLong(), null));
            }
        }
        int fx = 1 + rnd.nextInt(2);
        ring(fx, -3, chance(0.12));
        logSeat(fx - 3, -3, RuinCell.AXIS_Z);
        if (chance(0.6)) {
            logSeat(fx + 3, -4, RuinCell.AXIS_Z);
        }
    }

    // =========================================================================================
    // The house body: walls, openings, roof and floor, ruined
    // =========================================================================================

    /** One rectangular building drawn from a footprint, with its own wall heights. */
    private final class Body {
        final int ou;
        final int ov;
        final String[] rows;
        final int w;
        final int d;
        final int hw;
        final int style;
        final int roof;
        /** Floor course height (3 for the stilt hut's platform). */
        final int y0;
        boolean thatch;
        boolean plinth;
        boolean burned;
        boolean tower;
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
                top = burned ? 7 + rnd.nextInt(3) : ridge + 1 + (ridge > hw + 2 ? 0 : 1);
            }
            return top;
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
            heights();
            floor();
            walls();
            interiorAir();
            switch (roof) {
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
            weather();
        }

        /** Surviving wall heights: smooth loss along the perimeter, breaches, sturdy corners. */
        private void heights() {
            int per = pu.length;
            double d0 = decay;
            for (int attempt = 0; attempt < 4; attempt++) {
                h = new int[per];
                int minH = burned ? 0 : d0 < 0.8 ? 1 : 0;
                for (int i = 0; i < per; i++) {
                    char ch = at(pu[i], pv[i]);
                    double n = 0.5 + 0.55 * n2(i / 5.0, 3.1) + 0.25 * n2(i / 2.0 - 7.3, 11.7);
                    double cv = smoothstep(0.3, 0.7, n);
                    // corners, chimneys, log walls and thick tower walls outlast plain walls
                    double k = corner(pu[i], pv[i]) ? 0.6 : ch == 'H' ? 0.3 : tower ? 0.65 : style == LOGS ? 0.8 : 1.0;
                    if (burned) {
                        h[i] = ch == 'H' ? full[i] : n2(i / 2.5, 9.0) > 0.25 ? 0 : 1;
                        continue;
                    }
                    // whole courses fall: a lightly decayed wall keeps its top unless the noise is high
                    h[i] = Math.max(minH, full[i] - (int) Math.floor(full[i] * d0 * cv * k + 0.25));
                }
                if (!burned) {
                    int breaches = d0 < 0.45 ? 0 : d0 < 0.7 ? 1 : 2;
                    for (int b = 0; b < breaches; b++) {
                        int centre = (int) (SeedMixer.unit(SeedMixer.hash2(site.seed, attempt, 200 + b)) * per);
                        int r = 1 + (int) (2.0 * SeedMixer.unit(SeedMixer.hash2(site.seed, attempt, 210 + b)));
                        for (int i = 0; i < per; i++) {
                            if (at(pu[i], pv[i]) == 'H') {
                                continue;
                            }
                            int dist = Math.min(Math.abs(i - centre), per - Math.abs(i - centre));
                            int cap = Math.max(minH, (int) Math.floor(1.3 * Math.max(0, dist - r)));
                            h[i] = Math.min(h[i], cap);
                        }
                    }
                    // corners stand longest, unless a breach went through them
                    for (int i = 0; i < per; i++) {
                        if (corner(pu[i], pv[i]) && h[i] >= hw - 3 && SeedMixer.unit(SeedMixer.hash2(site.seed, i, 300)) < 0.6) {
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
                if (burned || tall * 4 >= per) {
                    break;
                }
                d0 *= 0.85;
            }
            // nothing may stand above an opening that lost both of its carrying neighbours
            for (int i = 0; i < pu.length; i++) {
                int top = openingTop(at(pu[i], pv[i]));
                if (top > 0 && h[i] > top) {
                    int l = h[(i + pu.length - 1) % pu.length];
                    int r = h[(i + 1) % pu.length];
                    if (Math.min(l, r) <= top) {
                        h[i] = top;
                    }
                }
            }
        }

        int openingTop(char ch) {
            return switch (ch) {
                case 'D' -> 2;
                case 'W' -> hw >= 4 ? 3 : 2;
                case 'w' -> 2;
                default -> 0;
            };
        }

        int openingBottom(char ch) {
            return ch == 'D' ? 1 : ch == 'W' || ch == 'w' ? 2 : 99;
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
                    } else if (ch == ',') {
                        cell = RuinCell.of(RuinPart.SOIL);
                    } else if (site.template == BOTHY || tower) {
                        cell = RuinCell.of(RuinPart.FOUNDATION);
                    } else if (site.template == ADOBE) {
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
                    } else if (y >= openingBottom(ch) && y <= openingTop(ch)) {
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
                        c.add(ou + u + du, y0 + y, ov + v + dv, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), axis));
                    }
                }
            }
        }

        private int wallCell(int u, int v, int y, boolean corner, boolean alongU) {
            int axisAlong = alongU ? RuinCell.AXIS_X : RuinCell.AXIS_Z;
            int log = RuinCell.withAux(RuinCell.of(RuinPart.LOG), burned ? 1 : 0);
            switch (style) {
                case TIMBER -> {
                    if (y <= 2) {
                        return RuinCell.of(corner ? RuinPart.QUOIN : RuinPart.MASONRY);
                    }
                    if (corner || at(u - 1, v) == 'D' || at(u + 1, v) == 'D' || at(u, v - 1) == 'D' || at(u, v + 1) == 'D') {
                        return log;
                    }
                    if (y == hw && !(roof == GABLE_U && !alongU)) {
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
                    return RuinCell.of(corner && site.template != ADOBE ? RuinPart.QUOIN : RuinPart.MASONRY);
                }
            }
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
            } else if (burned) {
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
                if (h[i] >= 2) {
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
            int lo = openingBottom(ch);
            int hi = Math.min(openingTop(ch), h[i]);
            if (hi < lo) {
                return;
            }
            boolean stone = style == STONE_WALLS || site.template == BOTHY;
            double r = rnd.nextDouble();
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
            if (ch == 'W' && style != STONE_WALLS && chance(0.25)) {
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
            return (decay + rot) * 1.25 + 0.45 * n2(a / 3.0 + 20.0, k / 3.0) - 0.35 * gableEnd <= 0.75;
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
                        if (chance(0.15 + decay * 0.3)) {
                            continue;
                        }
                    }
                    c.set(ou + u, top, ov + v, RuinCell.withAxis(RuinCell.of(RuinPart.STRIPPED_LOG), RuinCell.AXIS_Z));
                }
            }
            for (int u = 1; u < w - 1; u++) {
                for (int v = 1; v < d - 1; v++) {
                    if (n2(u / 2.5 + 60.0, v / 2.5) > 0.9 * decay - 0.45) {
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
                        if (ring == 0 && !edgeSupported || n2(u / 2.0 + 80.0, v / 2.0 + ring) + 0.4 < decay * 1.2) {
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

        /** Battlemented top of a watchtower: a lookout deck, parapet and merlons. */
        private void towerTop() {
            int y = y0 + hw;
            for (int u = 1; u < w - 1; u++) {
                for (int v = 1; v < d - 1; v++) {
                    if (u == 2 && v == 3) {
                        c.set(ou + u, y, ov + v, RuinCell.of(RuinPart.TRAPDOOR, RuinCell.NORTH) | RuinCell.FLAG | RuinCell.TOP);
                    } else if (n2(u * 0.8 + 90.0, v * 0.8) + 0.3 > decay) {
                        c.set(ou + u, y, ov + v, RuinCell.of(RuinPart.FLOOR));
                    } else {
                        c.set(ou + u, y, ov + v, RuinCell.of(RuinPart.AIR));
                    }
                }
            }
        }

        /** Plank floors inside the tower every four blocks, with a hole for the ladder. */
        private void towerFloors() {
            for (int level = 4; level < hw; level += 4) {
                for (int u = 1; u < w - 1; u++) {
                    for (int v = 1; v < d - 1; v++) {
                        if (u == 2 && v == 3) {
                            continue;
                        }
                        if (n2(u * 0.9 + level * 7.0, v * 0.9) + 0.35 > decay) {
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
                    if (r < 0.14) {
                        c.set(ou + u, y0 + 1, ov + v, thatch ? RuinCell.of(RuinPart.HAY) : RuinCell.of(RuinPart.ROOF_SLAB));
                    } else if (r < 0.26 && !thatch) {
                        c.set(ou + u, y0 + 1, ov + v, RuinCell.of(RuinPart.ROOF_STAIR, rnd.nextInt(4)));
                    }
                }
            }
            if (decay > 0.45 && style != STONE_WALLS || decay > 0.6) {
                int beams = 1 + (decay > 0.7 ? 1 : 0);
                for (int b = 0; b < beams; b++) {
                    int v = 1 + rnd.nextInt(Math.max(1, d - 2));
                    int len = Math.min(w - 2, 3 + rnd.nextInt(3));
                    int u0 = 1 + rnd.nextInt(Math.max(1, w - 1 - len));
                    for (int u = u0; u < u0 + len; u++) {
                        if (c.empty(ou + u, y0 + 1, ov + v) && !reservedCell(ou + u, ov + v)) {
                            c.set(ou + u, y0 + 1, ov + v, RuinCell.withAxis(RuinCell.of(chance(0.5) ? RuinPart.STRIPPED_LOG : RuinPart.LOG), RuinCell.AXIS_X));
                        }
                    }
                }
            }
        }

        /** Masonry, or planks from timber walls, spilled outward below the gaps. */
        private void rubble() {
            for (int i = 0; i < pu.length; i++) {
                int lost = Math.min(full[i], hw) - h[i];
                if (lost < 2 || corner(pu[i], pv[i])) {
                    continue;
                }
                int out = out(pu[i], pv[i]);
                for (int dist = 1; dist <= 2; dist++) {
                    double p = dist == 1 ? Math.min(0.8, 0.2 * lost) : Math.min(0.5, 0.1 * lost);
                    if (!chance(p)) {
                        continue;
                    }
                    int u = ou + pu[i] + RuinCell.DX[out] * dist;
                    int v = ov + pv[i] + RuinCell.DZ[out] * dist;
                    c.decal(u, v, 1, rubbleCell(i));
                }
                // and some fell inside
                if (chance(0.35)) {
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

        /** Moss and cracks on stone in coherent patches, more of it low down and on wet sites. */
        private void weather() {
            for (int u = -2; u <= w + 1; u++) {
                for (int v = -2; v <= d + 1; v++) {
                    for (int y = 0; y <= hw + 6; y++) {
                        int cu = ou + u;
                        int cv = ov + v;
                        int cell = c.get(cu, y0 + y, cv);
                        RuinPart p = RuinCell.part(cell);
                        if (!stoneLike(p)) {
                            continue;
                        }
                        double moss = n3(cu / 4.0, (y0 + y) / 4.0, cv / 4.0) + 0.35 * (y <= 2 ? 1 : 0) + 0.45 * (wet - 0.5) + 0.25 * decay;
                        if (moss > 0.42) {
                            c.set(cu, y0 + y, cv, RuinCell.withWeathering(cell, RuinCell.MOSSY));
                        } else if (NOISE.noise3(cu / 3.5 + o3, (y0 + y) / 3.5 - o1, cv / 3.5) > 0.5 - 0.2 * decay) {
                            c.set(cu, y0 + y, cv, RuinCell.withWeathering(cell, RuinCell.CRACKED));
                        }
                    }
                }
            }
        }

        boolean stoneLike(RuinPart p) {
            return p == RuinPart.FOUNDATION || p == RuinPart.MASONRY || p == RuinPart.QUOIN || p == RuinPart.CHIMNEY
                || p == RuinPart.MASONRY_STAIR || p == RuinPart.MASONRY_SLAB || p == RuinPart.MASONRY_WALL;
        }
    }

    // =========================================================================================
    // Shared helpers
    // =========================================================================================

    /** True when nothing structural lies above (u, y, v) inside the canvas. */
    private boolean openSky(int u, int v, int y) {
        for (int yy = y; yy < c.minY + c.sizeY; yy++) {
            if (c.structural(u, yy, v)) {
                return false;
            }
        }
        return true;
    }

    private void reserve(int u, int v) {
        reserved.add(new int[]{u, v});
    }

    private boolean reservedCell(int u, int v) {
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
    private int[] propCell(Body b, boolean backOnly) {
        List<int[]> cells = new ArrayList<>();
        for (int u = 1; u < b.w - 1; u++) {
            for (int v = 1; v < b.d - 1; v++) {
                boolean wall = u == 1 || u == b.w - 2 || v == 1 || v == b.d - 2;
                if (!wall || backOnly && v != b.d - 2) {
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
                int facing = v == b.d - 2 ? RuinCell.NORTH : u == 1 ? RuinCell.EAST : u == b.w - 2 ? RuinCell.WEST : RuinCell.SOUTH;
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

    private void container(int u, int y, int v, int facing, boolean barrel) {
        if (barrel) {
            c.set(u, y, v, RuinCell.of(RuinPart.BARREL, RuinCell.NORTH) | RuinCell.TOP);
        } else {
            c.set(u, y, v, RuinCell.of(RuinPart.CHEST, facing));
        }
        if (!barrel || chance(0.5)) {
            c.extras.add(new RuinPlan.Extra(u, y, v, false, site.lootTable, rnd.nextLong(), null));
        }
    }

    /** Furniture of a cottage or cabin. */
    private void houseProps(Body b, boolean cottage) {
        int y = b.y0 + 1;
        // a bed against the back wall: a real one in the better kept houses, else a rotten frame
        if (chance(0.55)) {
            int u = b.ou + 1 + rnd.nextInt(b.w - 2);
            int head = b.ov + b.d - 2;
            int foot = head - 1;
            if (!reservedCell(u, head) && !reservedCell(u, foot) && c.empty(u, y, head) && c.empty(u, y, foot)
                && c.part(u, b.y0, head).structural && c.part(u, b.y0, foot).structural) {
                reserve(u, head);
                reserve(u, foot);
                if (decay < 0.5 && chance(0.35)) {
                    int colour = rnd.nextInt(3);
                    c.set(u, y, head, RuinCell.withAux(RuinCell.of(RuinPart.BED, RuinCell.SOUTH) | RuinCell.TOP, colour));
                    c.set(u, y, foot, RuinCell.withAux(RuinCell.of(RuinPart.BED, RuinCell.SOUTH), colour));
                } else {
                    c.set(u, y, head, RuinCell.of(RuinPart.PLANK_SLAB));
                    c.set(u, y, foot, RuinCell.of(RuinPart.PLANK_SLAB));
                    if (chance(0.6)) {
                        c.set(u, y + 1, foot, RuinCell.withAux(RuinCell.of(RuinPart.CARPET), rnd.nextInt(3)));
                    }
                }
            }
        }
        int[] cell;
        if (chance(0.45)) {
            cell = propCell(b, false);
            if (cell != null) {
                container(cell[0], y, cell[1], cell[2], false);
            }
        }
        if (chance(0.2)) {
            cell = propCell(b, false);
            if (cell != null) {
                container(cell[0], y, cell[1], cell[2], true);
            }
        }
        if (chance(0.45) && (cell = propCell(b, false)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.CRAFTING_TABLE));
        }
        if (chance(0.3) && (cell = propCell(b, false)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.FURNACE, cell[2]));
        }
        if (cottage && chance(0.35) && (cell = propCell(b, true)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.BOOKSHELF));
            if (c.empty(cell[0] + 1, y, cell[1]) && !reservedCell(cell[0] + 1, cell[1]) && c.part(cell[0] + 1, b.y0, cell[1]).structural) {
                c.set(cell[0] + 1, y, cell[1], RuinCell.of(RuinPart.BOOKSHELF));
                reserve(cell[0] + 1, cell[1]);
            }
        }
        if (chance(0.12) && (cell = propCell(b, false)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.CAULDRON));
        }
        if (chance(0.18) && (cell = propCell(b, false)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.withAux(RuinCell.of(RuinPart.FLOWER_POT), rnd.nextInt(3)));
        }
        if (chance(0.12) && (cell = propCell(b, false)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.LANTERN));
        }
        floorPlants(b);
        overgrowth(b);
    }

    private void bothyProps(Body b) {
        int y = b.y0 + 1;
        int[] cell;
        int bales = 1 + rnd.nextInt(3);
        for (int i = 0; i < bales; i++) {
            if ((cell = propCell(b, false)) != null) {
                c.set(cell[0], y, cell[1], RuinCell.withAxis(RuinCell.of(RuinPart.HAY), rnd.nextInt(3)));
                if (i == 0 && chance(0.4) && c.empty(cell[0], y + 1, cell[1])) {
                    c.set(cell[0], y + 1, cell[1], RuinCell.of(RuinPart.HAY));
                }
            }
        }
        if ((cell = propCell(b, false)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.MASONRY_SLAB));
        }
        if (chance(0.4) && (cell = propCell(b, false)) != null) {
            container(cell[0], y, cell[1], cell[2], false);
        }
        if (chance(0.3) && (cell = propCell(b, false)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.CAULDRON));
        }
        floorPlants(b);
        overgrowth(b);
    }

    private void adobeProps(Body b) {
        int y = b.y0 + 1;
        int[] cell;
        int pots = 1 + rnd.nextInt(3);
        for (int i = 0; i < pots; i++) {
            if ((cell = propCell(b, false)) != null) {
                c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.DECORATED_POT, rnd.nextInt(4)) | (chance(0.5) ? RuinCell.FLAG : 0));
            }
        }
        if (chance(0.3) && (cell = propCell(b, false)) != null) {
            container(cell[0], y, cell[1], cell[2], true);
        }
        if (chance(0.3) && (cell = propCell(b, false)) != null) {
            container(cell[0], y, cell[1], cell[2], false);
        }
        if (chance(0.4) && (cell = propCell(b, false)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.FURNACE, cell[2]));
        }
        floorPlants(b);
        overgrowth(b);
    }

    /** Grass, ferns and flowers on open earth floors, mushrooms in the shade. */
    private void floorPlants(Body b) {
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
                }
                if (!c.empty(cu, b.y0 + 1, cv) || reservedCell(cu, cv)) {
                    continue;
                }
                double r = rnd.nextDouble();
                int plant = 0;
                if (open) {
                    if (wet > 0.55 && n2(cu / 3.0 + 7.0, cv / 3.0) > 0.62 - 0.45 * wet) {
                        plant = RuinCell.of(RuinPart.MOSS_CARPET);
                    } else if (r < 0.45 * (0.5 + vig)) {
                        plant = RuinCell.of(hot ? RuinPart.DEAD_BUSH : RuinPart.SHORT_GRASS);
                        if (hot && r > 0.08) {
                            plant = 0;
                        }
                    } else if (r < 0.58 && !hot) {
                        plant = RuinCell.of(wet > 0.5 ? RuinPart.FERN : RuinPart.TALL_GRASS);
                    } else if (r < 0.62) {
                        plant = RuinCell.withAux(RuinCell.of(RuinPart.FLOWER), rnd.nextInt(6));
                    } else if (r < 0.72) {
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
    private void overgrowth(Body b) {
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
                if (wallTop.fullCube() && wet > 0.3 && n2(i / 4.0 + 70.0, 2.0) > 0.75 - 0.35 * wet) {
                    c.set(cu, top, cv, RuinCell.of(RuinPart.MOSS_CARPET) | RuinCell.SOFT);
                } else if (wallTop.fullCube() && hi < b.full[i] && decay > 0.45 && n2(i / 4.0 + 90.0, 3.0) > 0.85 - 0.4 * vig) {
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
    // Extras: porch, shed, well, garden, oven, campfire ring, yard and footpath
    // =========================================================================================

    private void porch(Body b) {
        int y = b.y0;
        for (int u = 0; u < b.w; u++) {
            for (int v = -2; v <= -1; v++) {
                if (n2(u * 0.7 + 30.0, v * 0.7) + decay < 1.15) {
                    c.set(u, y, v, RuinCell.of(RuinPart.PLANK_SLAB) | RuinCell.TOP);
                    c.setFill(u, v, RuinCell.of(RuinPart.FOUNDATION));
                }
            }
        }
        for (int u : new int[]{0, b.w / 2, b.w - 1}) {
            if (u == b.w / 2 && Math.abs(u - spec.doorU()) == 0) {
                continue;
            }
            boolean standing = !chance(decay * 0.5);
            c.setFill(u, -2, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, y, -2, RuinCell.of(RuinPart.FOUNDATION));
            if (standing) {
                for (int yy = 1; yy <= b.hw - 2; yy++) {
                    c.set(u, y + yy, -2, RuinCell.of(RuinPart.FENCE));
                }
            }
        }
        // the roof slope continues over the porch, carried by the eave and the posts
        for (int u = -1; u <= b.w; u++) {
            boolean postBelow = c.part(u, y + b.hw - 2, -2) == RuinPart.FENCE;
            if (c.part(u, y + b.hw, -1) == RuinPart.ROOF_STAIR && (postBelow || n2(u * 0.5, 44.0) > decay - 0.3)) {
                c.set(u, y + b.hw - 1, -2, RuinCell.of(RuinPart.ROOF_STAIR, RuinCell.SOUTH));
            }
        }
        pathStart = -3;
    }

    /** Open-fronted farm shed on posts, with hay inside. */
    private void shed(int u0, int v0) {
        int w = 6;
        int d = 4;
        for (int u = u0; u < u0 + w; u++) {
            for (int v = v0; v < v0 + d; v++) {
                boolean post = (u == u0 || u == u0 + w - 1) && (v == v0 || v == v0 + d - 1);
                boolean back = v == v0 + d - 1;
                c.set(u, 0, v, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 1));
                c.setFill(u, v, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 3));
                if (post || back) {
                    c.setFill(u, v, RuinCell.of(RuinPart.FOUNDATION));
                    c.set(u, 0, v, RuinCell.of(RuinPart.FOUNDATION));
                }
                if (post) {
                    for (int y = 1; y <= 3; y++) {
                        c.set(u, y, v, RuinCell.of(RuinPart.STRIPPED_LOG));
                    }
                } else if (back) {
                    for (int y = 1; y <= 2; y++) {
                        if (n2(u * 0.6 + 11.0, y) + decay < 1.0) {
                            c.set(u, y, v, RuinCell.of(RuinPart.PLANKS));
                        }
                    }
                }
                if (n2(u / 2.0 + 13.0, v / 2.0) + decay * 1.1 < 0.95) {
                    c.set(u, 4, v, RuinCell.of(RuinPart.PLANK_SLAB));
                }
            }
        }
        int hay = 2 + rnd.nextInt(3);
        for (int i = 0; i < hay; i++) {
            int u = u0 + 1 + rnd.nextInt(w - 2);
            int v = v0 + 1 + rnd.nextInt(d - 2);
            c.set(u, 1, v, RuinCell.withAxis(RuinCell.of(RuinPart.HAY), rnd.nextInt(3)));
        }
        if (chance(0.5)) {
            c.set(u0 + 1, 1, v0 + d - 2, RuinCell.of(RuinPart.COMPOSTER));
        }
        if (chance(0.25)) {
            c.set(u0 + w - 2, 1, v0 + d - 2, RuinCell.withAux(RuinCell.of(RuinPart.ANVIL, RuinCell.EAST), 1 + rnd.nextInt(2)));
        }
    }

    /** A stone well: a ring with a rim, a lined shaft and still water at the bottom. */
    private void well(int cu, int cv) {
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
                    boolean broken = n2(u * 0.9 + 21.0, v * 0.9) + decay > 1.15;
                    c.set(u, 1, v, broken ? RuinCell.of(RuinPart.MASONRY_SLAB) : RuinCell.of(RuinPart.MASONRY_WALL));
                } else {
                    c.set(u, 1, v, RuinCell.of(RuinPart.AIR));
                }
                c.set(u, 2, v, RuinCell.of(RuinPart.AIR) | RuinCell.SOFT);
            }
        }
        if (decay < 0.6 && chance(0.6)) {
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
    }

    /** Fenced kitchen garden behind the house, its fence half fallen in coherent runs. */
    private void garden(int u0, int v0) {
        int w = 9;
        int d = 5;
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

    /** A beehive bread oven of mud bricks beside an adobe house. */
    private void horno(int u0, int v0) {
        for (int du = 0; du < 3; du++) {
            for (int dv = 0; dv < 3; dv++) {
                int u = u0 + du;
                int v = v0 + dv;
                boolean centre = du == 1 && dv == 1;
                c.setFill(u, v, RuinCell.of(RuinPart.FOUNDATION));
                c.set(u, 0, v, RuinCell.of(RuinPart.FOUNDATION));
                if (centre) {
                    c.set(u, 1, v, RuinCell.of(RuinPart.CAMPFIRE, RuinCell.NORTH));
                    c.set(u, 2, v, RuinCell.of(RuinPart.MASONRY));
                    if (decay < 0.7) {
                        c.set(u, 3, v, RuinCell.of(RuinPart.MASONRY_SLAB));
                    }
                } else if (!(du == 1 && dv == 0)) {
                    c.set(u, 1, v, RuinCell.of(RuinPart.MASONRY));
                    if ((du == 1 || dv == 1) && n2(u + 5.0, v * 1.3) + decay < 1.2) {
                        c.set(u, 2, v, RuinCell.of(RuinPart.MASONRY_SLAB));
                    }
                }
            }
        }
    }

    /** A ring of stones around a cold campfire, with log seats. */
    private void ring(int cu, int cv, boolean lit) {
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

    private void logSeat(int u, int v, int axis) {
        int cell = RuinCell.withAxis(RuinCell.of(RuinPart.STRIPPED_LOG), axis);
        c.decal(u, v, 1, cell);
        int du = axis == RuinCell.AXIS_X ? 1 : 0;
        int dv = axis == RuinCell.AXIS_Z ? 1 : 0;
        c.decal(u + du, v + dv, 1, cell);
    }

    private void campfireRing(Body b, boolean force, int side) {
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

    /** Yard of an ordinary house: a campfire ring, a woodpile and maybe something to dig up. */
    private void yard(Body b) {
        if (site.template != STILT_HUT && site.template != FARMSTEAD) {
            // the adobe's oven stands to the east, so its fire ring goes west
            campfireRing(b, false, site.template == ADOBE ? -1 : 0);
        }
        if (chance(0.25) && site.template != CHIMNEY_RUIN) {
            int v = b.ov + b.d;
            boolean alongU = true;
            for (int u = b.ou + 2; u <= b.ou + Math.min(4, b.w - 2); u++) {
                c.decal(u, v, 1, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), alongU ? RuinCell.AXIS_Z : RuinCell.AXIS_X));
                if (chance(0.5)) {
                    c.decal(u, v, 2, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_Z));
                }
            }
        }
        if (chance(0.4)) {
            // something lost long ago lies under the earth floor or in the yard
            int count = 1 + rnd.nextInt(2);
            for (int i = 0; i < count; i++) {
                String loot = chance(0.1) ? "minecraft:archaeology/trail_ruins_rare" : "minecraft:archaeology/trail_ruins_common";
                int u = b.ou + 1 + rnd.nextInt(Math.max(1, b.w - 2));
                int v = b.ov + 1 + rnd.nextInt(Math.max(1, b.d - 2));
                if (c.part(u, b.y0, v) == RuinPart.SOIL && b.y0 == 0 && c.empty(u, 1, v)) {
                    c.set(u, 0, v, RuinCell.of(RuinPart.SUSPICIOUS_GRAVEL));
                    c.extras.add(new RuinPlan.Extra(u, 0, v, false, loot, rnd.nextLong(), null));
                } else {
                    int du = b.ou - 2 + rnd.nextInt(b.w + 4);
                    int dv = b.ov + b.d + 1;
                    c.decal(du, dv, 0, RuinCell.of(RuinPart.SUSPICIOUS_GRAVEL));
                    c.extras.add(new RuinPlan.Extra(du, 0, dv, true, loot, rnd.nextLong(), null));
                }
            }
        }
    }

    /** A woodpile kept dry under the stilt hut's platform. */
    private void woodpileUnder(Body b) {
        if (chance(0.4)) {
            c.decal(1, 2, 1, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_X));
            c.decal(2, 2, 1, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_X));
        }
    }

    /**
     * The trodden footpath from the door to the trail, and sometimes a weathered name board
     * where it leaves the trail.
     */
    private void footpath() {
        int door = spec.doorU();
        if (site.template == LEAN_TO) {
            return;
        }
        if (site.template != CHIMNEY_RUIN && chance(0.9)) {
            for (int v = pathStart; v >= -site.pathLength; v--) {
                double n = n2(door * 0.3 + 55.0, v / 3.0);
                int u = door + (n > 0.45 ? 1 : n < -0.45 ? -1 : 0);
                c.decal(u, v, 0, RuinCell.of(n > 0.2 || n < -0.6 ? RuinPart.COARSE_DIRT : RuinPart.DIRT_PATH));
            }
        }
        if (chance(0.35) && site.pathLength >= 4) {
            int v = -site.pathLength + 2;
            int u = door + 2;
            c.decal(u, v, 1, RuinCell.of(RuinPart.FENCE));
            c.decal(u, v, 2, RuinCell.of(RuinPart.STANDING_SIGN, RuinCell.NORTH));
            boolean blank = chance(0.35);
            String[] front = blank ? new String[]{"", "", "", ""}
                : new String[]{"", site.name, site.template == FARMSTEAD ? "@farm|Farm" : "", ""};
            c.extras.add(new RuinPlan.Extra(u, 2, v, true, null, 0L,
                new RuinPlan.SignSpec(front, new String[]{"", "", "", ""}, site.materials.darkWood(), false)));
        }
    }
}
