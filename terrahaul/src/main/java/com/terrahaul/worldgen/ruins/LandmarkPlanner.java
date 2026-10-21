package com.terrahaul.worldgen.ruins;

/**
 * Plans the larger trailside ruins that once served the road itself:
 * <ul>
 *   <li>a collapsed watchtower: a square {@link #STONE_TOWER} whose top fell in, a timber
 *       {@link #LOOKOUT} on log legs with one leg broken and its platform half gone, or the
 *       {@link #ROUND_STUMP} of a round tower collapsed to one side into a fan of rubble;</li>
 *   <li>a ruined chapel with its gable end to the trail: a stone chapel with a
 *       {@link #BELLCOTE} over the door, a {@link #TIMBER_CHAPEL} beside a free-standing belfry,
 *       or a roofless {@link #SHELL}. Pews face the altar, candles and a lectern stand at the
 *       back, and a walled churchyard of leaning headstones lies beside it;</li>
 *   <li>the abutments of a lost bridge where the trail fords a stream: {@link #STONE_ABUTMENTS}
 *       with the springers of a fallen arch, {@link #TIMBER_PILES} with stubs of the deck, or
 *       {@link #ONE_BANK} left standing with the other washed down to rubble. Abutments stand on
 *       the dry banks only, and the deck stubs reach out over the water without touching it.</li>
 * </ul>
 */
public final class LandmarkPlanner extends RuinBuilder {
    // watchtowers
    public static final int STONE_TOWER = 0;
    public static final int LOOKOUT = 1;
    public static final int ROUND_STUMP = 2;
    public static final String[] TOWER_NAMES = {"stone_tower", "lookout", "round_stump"};
    // chapels
    public static final int BELLCOTE = 0;
    public static final int TIMBER_CHAPEL = 1;
    public static final int SHELL = 2;
    public static final String[] CHAPEL_NAMES = {"bellcote", "timber_chapel", "shell"};
    // bridges
    public static final int STONE_ABUTMENTS = 0;
    public static final int TIMBER_PILES = 1;
    public static final int ONE_BANK = 2;
    public static final String[] BRIDGE_NAMES = {"stone_abutments", "timber_piles", "one_bank"};

    /** Width of a bridge lane (u), and how far its abutments reach back from the water's edge (v). */
    static final int BRIDGE_WIDTH = 3;
    static final int ABUTMENT_DEPTH = 3;

    private LandmarkPlanner(TrailRuin site) {
        super(site);
    }

    public static RuinPlan plan(TrailRuin site) {
        return new LandmarkPlanner(site).build();
    }

    /** The canonical box of the abutments of a bridge over a stream {@code span} blocks wide. */
    static RuinSpec bridgeSpec(int span) {
        return new RuinSpec(BRIDGE_WIDTH, span + 2 * ABUTMENT_DEPTH, 6, -1, -ABUTMENT_DEPTH - 3, BRIDGE_WIDTH, span + ABUTMENT_DEPTH + 1, 1, 4.0, 0,
            false, 2.6);
    }

    /** The canonical box of a watchtower or chapel (bridges are measured by the survey). */
    public static RuinSpec spec(int kind, int variant) {
        if (kind == TrailRuin.WATCHTOWER) {
            return switch (variant) {
                case LOOKOUT -> RuinSpec.of(5, 5, 14, -6, -3, 11, 11, 2, 3.5, 0);
                case ROUND_STUMP -> RuinSpec.of(7, 7, 12, -6, -3, 13, 13, 3, 3.0, 0);
                default -> RuinSpec.of(5, 5, 16, -6, -3, 10, 9, 2, 3.0, 0);
            };
        }
        return switch (variant) {
            case TIMBER_CHAPEL -> RuinSpec.of(5, 9, 12, -6, -3, 14, 12, 2, 3.0, 1);
            default -> RuinSpec.of(7, 12, 14, -4, -3, 17, 15, 3, 3.5, 1);
        };
    }

    private RuinPlan build() {
        switch (site.kind) {
            case TrailRuin.WATCHTOWER -> {
                switch (site.variant) {
                    case LOOKOUT -> lookout();
                    case ROUND_STUMP -> roundStump();
                    default -> stoneTower();
                }
            }
            case TrailRuin.CHAPEL -> chapel();
            default -> bridge();
        }
        return finish();
    }

    // =========================================================================================
    // Watchtowers
    // =========================================================================================

    private void stoneTower() {
        Body b = new Body(0, 0, HousePlanner.footprint(5, 5, 2, 0, true), 10, STONE_WALLS, chance(0.4) ? PYRAMID : PARAPET, 0);
        b.tower = true;
        b.floorKind = FLOOR_STONE;
        // the top falls first: the upper courses carry the most decay
        b.wallDecay = Math.max(0.35, decay);
        b.build();
        int[][] slits = {{0, 2, 6}, {4, 2, 9}, {2, 0, 7}, {4, 1, 5}};
        for (int[] s : slits) {
            if (b.h(s[0], s[1]) > s[2]) {
                c.set(s[0], s[2], s[1], RuinCell.of(RuinPart.AIR));
            }
        }
        for (int y = 1; y <= b.hw; y++) {
            if (b.h(2, 4) >= y) {
                c.set(2, y, 3, RuinCell.of(RuinPart.LADDER, RuinCell.NORTH));
            }
        }
        reserve(2, 3);
        int[] cell = propCell(b, false);
        if (cell != null && chance(0.7)) {
            container(cell[0], 1, cell[1], cell[2], false);
        }
        cell = propCell(b, false);
        if (cell != null) {
            c.set(cell[0], 1, cell[1], RuinCell.of(RuinPart.BARREL, RuinCell.NORTH) | RuinCell.TOP);
        }
        // the stones of the fallen top lie around the foot
        rubbleField(-2, -1, 6, 6, 0.08 + 0.15 * decay);
        overgrowth(b);
        campfireRing(b, false, 0);
    }

    /**
     * A timber lookout: four log legs braced with fences, a plank platform with a rail and a
     * pyramid roof, a ladder up one side. One leg rotted through, so the platform hangs on three
     * (the part over the broken leg fell) and the leg lies in the grass.
     */
    private void lookout() {
        int top = 7;
        int broken = rnd.nextInt(4);
        int[][] legs = {{0, 0}, {4, 0}, {0, 4}, {4, 4}};
        for (int k = 0; k < 4; k++) {
            int u = legs[k][0];
            int v = legs[k][1];
            c.setFill(u, v, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 0, v, RuinCell.of(RuinPart.FOUNDATION));
            int h = k == broken ? 1 + rnd.nextInt(2) : top + 3;
            for (int y = 1; y <= h; y++) {
                c.set(u, y, v, RuinCell.of(RuinPart.LOG));
            }
        }
        // the broken leg lies beside its stump, pointing away from the tower
        int bu = legs[broken][0];
        int bv = legs[broken][1];
        int du = bu == 0 ? -1 : 1;
        for (int k = 1; k <= 5; k++) {
            c.decal(bu + du * k, bv, 1, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_X));
        }
        // rails between the legs at head height, missing next to the broken leg
        for (int u = 1; u < 4; u++) {
            for (int v : new int[]{0, 4}) {
                boolean leftOk = !(legs[broken][0] == 0 && legs[broken][1] == v);
                boolean rightOk = !(legs[broken][0] == 4 && legs[broken][1] == v);
                if ((u < 2 ? leftOk : u > 2 ? rightOk : leftOk || rightOk) && !chance(decay * 0.3)) {
                    c.set(u, 3, v, RuinCell.of(RuinPart.FENCE));
                }
            }
        }
        // the platform: only what three legs still carry
        for (int u = 0; u <= 4; u++) {
            for (int v = 0; v <= 4; v++) {
                double dist = Math.hypot(u - bu, v - bv);
                if (dist < 2.3 || n2(u * 0.8 + 33.0, v * 0.8) + 0.4 < decay) {
                    if (dist < 2.3 && chance(0.6)) {
                        // the boards that fell with the corner
                        c.decal(u + du, v, 1, RuinCell.of(RuinPart.PLANK_SLAB));
                    }
                    continue;
                }
                if (RuinCell.part(c.get(u, top, v)) != RuinPart.LOG) {
                    c.set(u, top, v, RuinCell.of(RuinPart.PLANKS));
                }
                boolean rim = u == 0 || v == 0 || u == 4 || v == 4;
                if (rim && RuinCell.part(c.get(u, top + 1, v)) != RuinPart.LOG && !chance(decay * 0.5)) {
                    c.set(u, top + 1, v, RuinCell.of(RuinPart.FENCE));
                }
            }
        }
        // a pyramid roof on the three standing legs
        for (int ring = 0; ring < 3; ring++) {
            int lo = ring;
            int hi = 4 - ring;
            for (int u = lo; u <= hi; u++) {
                for (int v = lo; v <= hi; v++) {
                    if (u != lo && u != hi && v != lo && v != hi) {
                        continue;
                    }
                    if (Math.hypot(u - bu, v - bv) < 2.8 || n2(u / 2.0 + 15.0, v / 2.0 + ring) + 0.45 < decay) {
                        continue;
                    }
                    int dir = v == lo ? RuinCell.SOUTH : v == hi ? RuinCell.NORTH : u == lo ? RuinCell.EAST : RuinCell.WEST;
                    c.set(u, top + 3 + ring, v, lo == hi ? RuinCell.of(RuinPart.ROOF_SLAB) : RuinCell.of(RuinPart.ROOF_STAIR, dir));
                }
            }
        }
        // the ladder up the front, between the front legs (unless the broken leg took it)
        if (bv != 0 || chance(0.5)) {
            c.setFill(2, 0, RuinCell.of(RuinPart.FOUNDATION));
            c.set(2, 0, 0, RuinCell.of(RuinPart.FOUNDATION));
            for (int y = 1; y < top; y++) {
                if (chance(0.15 * decay)) {
                    break;
                }
                c.set(2, y, 0, RuinCell.of(RuinPart.PLANKS));
                c.set(2, y, -1, RuinCell.of(RuinPart.LADDER, RuinCell.NORTH));
            }
        }
        // a lantern hanging under the platform, a barrel at the foot
        if (chance(0.5) && c.structural(3, top, 2)) {
            c.set(3, top - 1, 2, RuinCell.of(RuinPart.LANTERN) | RuinCell.FLAG);
        }
        if (chance(0.6)) {
            groundContainer(-2, 2, RuinCell.EAST, true, chance(0.5) ? site.lootTable : null);
        }
        campfireRing(new Body(0, 0, HousePlanner.footprint(5, 5, 2, 0, false), 3, PLANK_WALLS, NO_ROOF, 0), true, bu == 0 ? 1 : -1);
        meadow(-2, -2, 7, 7, 0.25);
    }

    /**
     * The stump of a round tower: its wall stands three to seven courses on one side and falls
     * away to its footing on the other, where the stones lie in a fan down the slope.
     */
    private void roundStump() {
        int r = 3;
        int cu = 3;
        int cv = 3;
        double fallDir = rnd.nextDouble() * Math.PI * 2.0;
        int maxH = 4 + rnd.nextInt(4);
        for (int u = 0; u <= 6; u++) {
            for (int v = 0; v <= 6; v++) {
                double dx = u - cu;
                double dz = v - cv;
                double dist = Math.hypot(dx, dz);
                if (dist > r + 0.5) {
                    continue;
                }
                c.setFill(u, v, RuinCell.of(RuinPart.FOUNDATION));
                c.set(u, 0, v, RuinCell.of(dist < r - 0.5 ? RuinPart.SOIL : RuinPart.FOUNDATION));
                if (dist < r - 0.5) {
                    continue;
                }
                // the wall is tallest away from the side it fell to
                double a = Math.atan2(dz, dx);
                double side = 0.5 + 0.5 * Math.cos(a - fallDir - Math.PI);
                int h = (int) Math.round(maxH * side + 0.8 * n2(u * 0.7 + 50.0, v * 0.7));
                boolean door = u == cu && v == 0;
                for (int y = 1; y <= h; y++) {
                    if (door && y <= 2) {
                        continue;
                    }
                    c.set(u, y, v, RuinCell.of(RuinPart.MASONRY));
                }
                if (h >= 4 && n2(u + 7.0, v + 3.0) > 0.4) {
                    // an arrow slit
                    c.set(u, 3, v, RuinCell.of(RuinPart.AIR));
                }
            }
        }
        for (int u = 1; u <= 5; u++) {
            for (int v = 1; v <= 5; v++) {
                if (Math.hypot(u - cu, v - cv) < r - 0.5) {
                    for (int y = 1; y <= maxH + 2; y++) {
                        c.add(u, y, v, RuinCell.of(RuinPart.AIR));
                    }
                }
            }
        }
        reserve(cu, 1);
        // the fan of rubble down the side it fell to
        double fx = Math.cos(fallDir);
        double fz = Math.sin(fallDir);
        for (int k = 0; k < 26; k++) {
            double t = 3.5 + rnd.nextDouble() * 6.0;
            double spread = (rnd.nextDouble() - 0.5) * (1.5 + t * 0.6);
            int u = (int) Math.round(cu + fx * t - fz * spread);
            int v = (int) Math.round(cv + fz * t + fx * spread);
            if (Math.hypot(u - cu, v - cv) <= r + 0.5 || v < -2) {
                continue;
            }
            double q = rnd.nextDouble();
            c.decal(u, v, 1, q < 0.4 ? RuinCell.of(RuinPart.MASONRY) : q < 0.7 ? RuinCell.of(RuinPart.MASONRY_SLAB)
                : RuinCell.withAux(RuinCell.of(RuinPart.ROCK, rnd.nextInt(4)), wet > 0.6 ? 1 : 0));
            if (q < 0.15 && t < 6) {
                c.decal(u, v, 2, RuinCell.of(RuinPart.MASONRY_SLAB));
            }
        }
        // a chest that the collapse buried in the floor, and grass over the inside
        int[] inside = {cu + rnd.nextInt(3) - 1, cv + 1};
        if (chance(0.6)) {
            c.set(inside[0], 1, inside[1], RuinCell.of(RuinPart.CHEST, RuinCell.NORTH));
            c.extras.add(new RuinPlan.Extra(inside[0], 1, inside[1], false, site.lootTable, rnd.nextLong(), null));
            reserve(inside[0], inside[1]);
        }
        for (int u = 1; u <= 5; u++) {
            for (int v = 1; v <= 5; v++) {
                if (Math.hypot(u - cu, v - cv) < r - 0.5 && !reservedCell(u, v) && c.empty(u, 1, v) && chance(0.35 + 0.3 * vigour())) {
                    c.set(u, 1, v, chance(0.15) ? RuinCell.of(RuinPart.FERN) : RuinCell.of(RuinPart.SHORT_GRASS));
                }
            }
        }
        weather(-1, -1, 7, 7, 0, maxH + 2, 0);
    }

    // =========================================================================================
    // Chapels
    // =========================================================================================

    private void chapel() {
        boolean timber = site.variant == TIMBER_CHAPEL;
        int w = timber ? 5 : 7;
        int d = timber ? 9 : 12;
        int hw = timber ? 4 : 5;
        String[] rows = chapelRows(w, d);
        Body b = new Body(0, 0, rows, hw, timber ? TIMBER : STONE_WALLS, GABLE_V, 0);
        b.floorKind = timber ? FLOOR_PLANKS : FLOOR_STONE;
        if (site.variant == SHELL) {
            b.wallDecay = 0.25 + 0.3 * decay;
            b.roofDecay = 1.0;
        } else {
            b.wallDecay = 0.15 + 0.4 * decay;
            b.roofDecay = Math.max(0.55, decay + 0.1);
        }
        b.build();
        int door = w / 2;
        // a bellcote of two piers over the front gable, the bell hanging between them
        int apex = b.h(door, 0);
        boolean bell = !timber && apex >= b.fullHeight(door, 0) && site.variant == BELLCOTE;
        if (bell) {
            int y = apex + 1;
            // the piers rise from the gable's raking tops, through the roof
            for (int u : new int[]{door - 1, door + 1}) {
                for (int yy = b.h(u, 0) + 1; yy < y + 3; yy++) {
                    c.set(u, yy, 0, RuinCell.of(RuinPart.MASONRY));
                }
            }
            c.set(door - 1, y + 3, 0, RuinCell.of(RuinPart.MASONRY_SLAB));
            c.set(door, y + 3, 0, RuinCell.of(RuinPart.MASONRY_SLAB));
            c.set(door + 1, y + 3, 0, RuinCell.of(RuinPart.MASONRY_SLAB));
            c.set(door, y, 0, RuinCell.of(RuinPart.MASONRY));
            if (chance(0.6)) {
                c.set(door, y + 2, 0, RuinCell.of(RuinPart.BELL, RuinCell.EAST) | RuinCell.FLAG);
            } else {
                // the bell fell and lies in front of the door
                c.decal(door + 1, -2, 1, RuinCell.of(RuinPart.BELL, RuinCell.EAST));
            }
        } else if (!timber && chance(0.5)) {
            c.decal(door + 2, -2, 1, RuinCell.of(RuinPart.BELL, RuinCell.NORTH));
        }
        // the altar at the back, candles on it, a lectern before it
        int av = d - 2;
        reserve(door, av);
        c.set(door, 1, av, RuinCell.of(timber ? RuinPart.PLANKS : RuinPart.MASONRY));
        if (!timber) {
            c.set(door - 1, 1, av, RuinCell.of(RuinPart.MASONRY_SLAB) | RuinCell.TOP);
            c.set(door + 1, 1, av, RuinCell.of(RuinPart.MASONRY_SLAB) | RuinCell.TOP);
            reserve(door - 1, av);
            reserve(door + 1, av);
        }
        if (chance(0.7)) {
            c.set(door, 2, av, RuinCell.withAux(RuinCell.of(RuinPart.CANDLE), rnd.nextInt(4)) | (chance(0.08) ? RuinCell.FLAG : 0));
        }
        if (chance(0.6)) {
            c.set(door, 1, av - 1, RuinCell.of(RuinPart.LECTERN, RuinCell.NORTH));
            reserve(door, av - 1);
        }
        if (chance(0.5) && !timber) {
            c.set(door, 1, av - 2, RuinCell.withAux(RuinCell.of(RuinPart.CARPET), 2));
        }
        // pews: rows of benches facing the altar, an aisle down the middle, some fallen over
        for (int v = 2; v < av - 2; v += 2) {
            for (int u = 1; u < w - 1; u++) {
                if (u == door || reservedCell(u, v) || !c.empty(u, 1, v) || !c.part(u, 0, v).structural) {
                    continue;
                }
                if (chance(0.25 + 0.4 * decay)) {
                    if (chance(0.4)) {
                        c.set(u, 1, v, RuinCell.of(RuinPart.PLANK_SLAB));
                    }
                    continue;
                }
                c.set(u, 1, v, RuinCell.of(RuinPart.PLANK_STAIR, RuinCell.NORTH));
            }
        }
        // the chest of the sacristy, candles on the sills of the side walls
        int[] cell = propCell(b, true);
        if (cell != null && chance(0.7)) {
            container(cell[0], 1, cell[1], cell[2], false);
        }
        for (int k = 0; k < 3; k++) {
            if ((cell = propCell(b, false)) != null && chance(0.6)) {
                c.set(cell[0], 1, cell[1], RuinCell.withAux(RuinCell.of(RuinPart.CANDLE), rnd.nextInt(3)));
            }
        }
        if ((cell = propCell(b, false)) != null && chance(0.4)) {
            c.set(cell[0], 1, cell[1], RuinCell.withAux(RuinCell.of(RuinPart.FLOWER_POT), rnd.nextInt(3)));
        }
        floorPlants(b);
        overgrowth(b);
        if (timber) {
            // the belfry beside the chapel, the churchyard behind it
            belfry(w + 2, 1);
            churchyard(w + 2, 5, 8, 8);
        } else {
            churchyard(w + 2, 1, 9, 12);
        }
    }

    /** A chapel footprint: door in the front gable, tall windows down both sides, a small window over the altar. */
    private static String[] chapelRows(int w, int d) {
        String[] rows = new String[d];
        for (int v = 0; v < d; v++) {
            StringBuilder s = new StringBuilder();
            for (int u = 0; u < w; u++) {
                boolean corner = (u == 0 || u == w - 1) && (v == 0 || v == d - 1);
                boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
                char ch = corner ? 'C' : edge ? '#' : '.';
                if (v == 0 && u == w / 2) {
                    ch = 'D';
                } else if ((u == 0 || u == w - 1) && v >= 2 && v <= d - 3 && v % 3 == 2) {
                    ch = 'W';
                } else if (v == d - 1 && u == w / 2) {
                    ch = 'w';
                }
                s.append(ch);
            }
            rows[v] = s.toString();
        }
        return rows;
    }

    /** A free-standing timber belfry beside a small chapel: four posts, a little roof and the bell. */
    private void belfry(int u0, int v0) {
        int top = 6;
        for (int[] p : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
            int u = u0 + p[0];
            int v = v0 + p[1];
            c.setFill(u, v, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 0, v, RuinCell.of(RuinPart.FOUNDATION));
            for (int y = 1; y <= top; y++) {
                c.set(u, y, v, RuinCell.of(RuinPart.LOG));
            }
        }
        for (int u = u0; u <= u0 + 2; u++) {
            for (int v = v0; v <= v0 + 2; v++) {
                if (n2(u * 0.9 + 61.0, v * 0.9) + 0.5 > decay) {
                    c.add(u, top + 1, v, RuinCell.of(RuinPart.ROOF_SLAB));
                }
            }
        }
        c.set(u0 + 1, top, v0 + 1, RuinCell.of(RuinPart.PLANKS));
        c.set(u0, top, v0 + 1, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_Z));
        c.set(u0 + 2, top, v0 + 1, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_Z));
        if (chance(0.55)) {
            c.set(u0 + 1, top - 1, v0 + 1, RuinCell.of(RuinPart.BELL, RuinCell.NORTH) | RuinCell.FLAG);
        } else {
            c.decal(u0 + 1, v0 + 1, 1, RuinCell.of(RuinPart.BELL, RuinCell.NORTH));
        }
    }

    /**
     * A churchyard: a low stone wall, broken in places, around rows of headstones, some of them
     * leaning or fallen, and a dark bush where a yew once grew.
     */
    private void churchyard(int u0, int v0, int w, int d) {
        int gate = v0 + 1 + rnd.nextInt(Math.max(1, d - 2));
        for (int u = u0; u < u0 + w; u++) {
            for (int v = v0; v < v0 + d; v++) {
                boolean edge = u == u0 || v == v0 || u == u0 + w - 1 || v == v0 + d - 1;
                if (edge) {
                    if (u == u0 && v == gate) {
                        continue;
                    }
                    if (n2(u * 0.35 + 9.0, v * 0.35 + 41.0) > decay - 0.5) {
                        c.decal(u, v, 1, RuinCell.of(RuinPart.MASONRY_WALL));
                    } else if (chance(0.4)) {
                        c.decal(u, v, 1, RuinCell.withAux(RuinCell.of(RuinPart.ROCK, rnd.nextInt(4)), wet > 0.6 ? 1 : 0));
                    }
                    continue;
                }
                // headstones in rows across the yard, facing the chapel
                boolean row = (v - v0) % 3 == 1 && (u - u0) % 2 == 0 && u > u0 + 1;
                if (row && chance(0.7)) {
                    double r = rnd.nextDouble();
                    int stone = r < 0.55 ? RuinCell.of(RuinPart.MASONRY_WALL)
                        : r < 0.8 ? RuinCell.of(RuinPart.MASONRY_STAIR, rnd.nextInt(4)) : RuinCell.of(RuinPart.MASONRY_SLAB);
                    c.decal(u, v, 1, stone);
                    if (chance(0.2)) {
                        c.decal(u, v + 1, 1, RuinCell.withAux(RuinCell.of(RuinPart.FLOWER), rnd.nextInt(6)));
                    }
                } else if (chance(0.25 * (0.5 + vigour()))) {
                    c.decal(u, v, 1, hot ? RuinCell.of(RuinPart.DEAD_BUSH) : RuinCell.of(RuinPart.SHORT_GRASS));
                }
            }
        }
        // the yew
        int yu = u0 + w - 2;
        int yv = v0 + d - 2;
        c.decal(yu, yv, 1, RuinCell.of(RuinPart.LEAVES));
        c.decal(yu, yv, 2, RuinCell.of(RuinPart.LEAVES));
        c.decal(yu - 1, yv, 1, RuinCell.of(RuinPart.LEAVES));
        c.decal(yu, yv - 1, 1, RuinCell.of(RuinPart.LEAVES));
    }

    // =========================================================================================
    // Bridge abutments
    // =========================================================================================

    /**
     * The abutments of a lost bridge. The canvas runs along the trail: {@code v} crosses the
     * stream, the near bank's abutment ends at the water's edge at v = -1 and the far one starts
     * at v = span. The survey measured the span and checked that every abutment column is dry.
     */
    private void bridge() {
        int span = site.pathLength;
        boolean stone = site.variant != TIMBER_PILES;
        abutment(-ABUTMENT_DEPTH, -1, stone, true);
        if (site.variant == ONE_BANK) {
            // the far abutment was washed out: its stones lie scattered on the bank
            rubbleField(0, span, BRIDGE_WIDTH - 1, span + ABUTMENT_DEPTH, 0.45);
        } else {
            abutment(span, span + ABUTMENT_DEPTH - 1, stone, false);
        }
        rubbleField(-1, -ABUTMENT_DEPTH - 3, BRIDGE_WIDTH, -ABUTMENT_DEPTH - 1, 0.12);
        weather(-1, -ABUTMENT_DEPTH - 1, BRIDGE_WIDTH, span + ABUTMENT_DEPTH, -4, 4, 0);
        drawFootpath = false;
    }

    /**
     * One abutment between v0 and v1: a block of masonry (or a crib of logs) from the bank up to
     * the deck, wing walls down its sides, and the stub of the deck or the springers of the arch
     * reaching out over the water by one block.
     */
    private void abutment(int v0, int v1, boolean stone, boolean near) {
        int toWater = near ? 1 : -1;
        int edge = near ? v1 : v0;
        for (int u = 0; u < BRIDGE_WIDTH; u++) {
            for (int v = v0; v <= v1; v++) {
                c.setFill(u, v, RuinCell.of(stone ? RuinPart.FOUNDATION : RuinPart.LOG));
                boolean side = u == 0 || u == BRIDGE_WIDTH - 1;
                if (stone) {
                    c.set(u, 0, v, RuinCell.of(RuinPart.MASONRY));
                    // the wing walls rise one course higher as a parapet, broken in places
                    if (side && n2(u * 0.9 + 17.0, v * 0.9) + decay < 1.05) {
                        c.set(u, 1, v, RuinCell.of(RuinPart.MASONRY_WALL));
                    } else if (!side) {
                        c.set(u, 1, v, RuinCell.of(RuinPart.AIR) | RuinCell.SOFT);
                    }
                } else {
                    // a crib of logs: courses laid crosswise
                    boolean cross = ((v - v0) & 1) == 0;
                    c.set(u, 0, v, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), cross ? RuinCell.AXIS_X : RuinCell.AXIS_Z));
                    if (side && v == edge) {
                        c.set(u, 1, v, RuinCell.of(RuinPart.LOG));
                        if (!chance(decay)) {
                            c.set(u, 2, v, RuinCell.of(RuinPart.FENCE));
                        }
                    }
                }
            }
        }
        // the deck stub or the springer of the arch over the water's edge
        int over = edge + toWater;
        for (int u = 0; u < BRIDGE_WIDTH; u++) {
            if (stone) {
                if (n2(u + 3.0, over * 0.7) + decay < 1.1) {
                    c.set(u, 0, over, RuinCell.of(RuinPart.MASONRY_STAIR, near ? RuinCell.NORTH : RuinCell.SOUTH) | RuinCell.TOP);
                }
            } else if (chance(0.6 - 0.3 * decay)) {
                c.set(u, 0, over, RuinCell.of(RuinPart.PLANK_SLAB) | RuinCell.TOP);
            }
        }
        // planks of the old deck lie on the bank behind
        if (chance(0.5)) {
            int back = near ? v0 - 1 : v1 + 1;
            c.decal(rnd.nextInt(BRIDGE_WIDTH), back - toWater, 1, RuinCell.withAxis(RuinCell.of(RuinPart.STRIPPED_LOG), RuinCell.AXIS_Z));
        }
    }
}
