package com.terrahaul.worldgen.ruins;

import com.terrahaul.worldgen.noise.SeedMixer;

/**
 * Plans the broken houses beside trails. A house is a combination of three independent choices,
 * so neighbouring houses rarely look alike:
 * <ul>
 *   <li>its layout, by size: a one-room {@link #HUT}, a {@link #COTTAGE}, a {@link #TWO_ROOM}
 *       house with an inner wall, a {@link #FARMHOUSE} with a barn or shed, well and garden, a
 *       {@link #TWO_STOREY} house whose upper floor has fallen in, or a stone
 *       {@link #TOWER_HOUSE} of three floors;</li>
 *   <li>its style, by climate: a {@link #TIMBER_FRAME} on a stone plinth in temperate land, a
 *       {@link #LOG_CABIN} with saddle-notched corners in cold and taiga country, {@link #STONE}
 *       in the uplands, {@link #ADOBE} (mud brick or sandstone, flat roofs on vigas) in hot dry
 *       land, and a {@link #RAISED} bamboo or jungle-wood house on stilts in hot wet land;</li>
 *   <li>its state of decay: the {@link #ROOF_FALLEN} in with walls still standing, a
 *       {@link #WALL_COLLAPSED} into a mound of rubble, a {@link #BURNED} shell (timber burns to
 *       its plinth, stone walls stand blackened and empty), only {@link #FOUNDATIONS} with the
 *       chimney standing, or {@link #OVERGROWN} with moss, vines, leaves and grass.</li>
 * </ul>
 * Inside lie the remains of the household: a chest with a vanilla village loot table, barrels,
 * a bed or its rotten frame, a table and chairs, a crafting table, flower pots, the hearth,
 * cobwebs under what is left of the roof. Outside: what is left of the yard fence, a woodpile,
 * a fire ring, sometimes a well or a garden patch, and something buried to dig up.
 */
public final class HousePlanner extends RuinBuilder {
    // layouts
    public static final int HUT = 0;
    public static final int COTTAGE = 1;
    public static final int TWO_ROOM = 2;
    public static final int FARMHOUSE = 3;
    public static final int TWO_STOREY = 4;
    public static final int TOWER_HOUSE = 5;
    public static final String[] LAYOUT_NAMES = {"hut", "cottage", "two_room", "farmhouse", "two_storey", "tower_house"};
    // styles
    public static final int TIMBER_FRAME = 0;
    public static final int LOG_CABIN = 1;
    public static final int STONE = 2;
    public static final int ADOBE = 3;
    public static final int RAISED = 4;
    public static final String[] STYLE_NAMES = {"timber_frame", "log_cabin", "stone", "adobe", "raised"};
    // states of decay
    public static final int ROOF_FALLEN = 0;
    public static final int WALL_COLLAPSED = 1;
    public static final int BURNED = 2;
    public static final int FOUNDATIONS = 3;
    public static final int OVERGROWN = 4;
    public static final String[] STATE_NAMES = {"roof_fallen", "wall_collapsed", "burned", "foundations", "overgrown"};

    private HousePlanner(TrailRuin site) {
        super(site);
    }

    /** The finished, pruned plan of a broken house. */
    public static RuinPlan plan(TrailRuin site) {
        return new HousePlanner(site).build();
    }

    /** Whether a layout can be built in a style (raised houses have one floor, towers are of stone or adobe). */
    public static boolean fits(int layout, int style) {
        return switch (layout) {
            case TWO_STOREY -> style != RAISED;
            case TOWER_HOUSE -> style == STONE || style == ADOBE;
            default -> true;
        };
    }

    /** Whether a state can befall a style (a house on stilts does not burn down to foundations). */
    public static boolean fitsState(int style, int state) {
        return style != RAISED || state != BURNED && state != FOUNDATIONS;
    }

    /** Body size {w, d, wall height} of a layout in a style. */
    static int[] body(int layout, int style) {
        return switch (layout) {
            case HUT -> style == ADOBE || style == RAISED ? new int[]{5, 5, 3} : new int[]{6, 5, 3};
            case COTTAGE -> switch (style) {
                case LOG_CABIN -> new int[]{7, 6, 4};
                case STONE -> new int[]{8, 6, 4};
                case ADOBE -> new int[]{7, 7, 4};
                case RAISED -> new int[]{7, 5, 3};
                default -> new int[]{9, 7, 4};
            };
            case TWO_ROOM -> switch (style) {
                case LOG_CABIN -> new int[]{12, 6, 4};
                case ADOBE -> new int[]{11, 7, 4};
                case RAISED -> new int[]{9, 5, 3};
                default -> new int[]{13, 7, 4};
            };
            case FARMHOUSE -> body(COTTAGE, style);
            case TWO_STOREY -> switch (style) {
                case LOG_CABIN -> new int[]{7, 6, 7};
                case STONE -> new int[]{8, 6, 8};
                case ADOBE -> new int[]{7, 7, 8};
                default -> new int[]{9, 7, 8};
            };
            default -> style == ADOBE ? new int[]{6, 6, 11} : new int[]{7, 7, 12};
        };
    }

    /** The canonical box of a house with its yard, outbuildings and garden. */
    public static RuinSpec spec(int layout, int style) {
        int[] b = body(layout, style);
        int w = b[0];
        int d = b[1];
        int door = layout == TWO_ROOM ? 3 : (w - 1) / 2;
        boolean raised = style == RAISED;
        // a porch or the ladder up to a raised floor stands in front of the door
        int gap = raised || style == LOG_CABIN ? 2 : 0;
        int maxU = w + (layout == FARMHOUSE ? (style == RAISED ? 7 : style == ADOBE ? 9 : 10) : 6);
        int maxV = d + (layout == FARMHOUSE ? 9 : 7);
        int height = b[2] + (raised ? 3 : 0) + 5;
        double range = layout == HUT || raised || layout == TOWER_HOUSE ? 3.0 : layout == TWO_ROOM ? 3.5 : 4.0;
        return RuinSpec.of(w, d, height, -7, -3 - gap, maxU, maxV, door, range, gap);
    }

    private RuinPlan build() {
        if (site.state == FOUNDATIONS) {
            decay = Math.max(decay, 0.75);
        } else if (site.state == OVERGROWN) {
            growth = 0.6 + 0.4 * SeedMixer.unit(SeedMixer.mix(site.seed * 31L + 3));
        }
        switch (site.variant) {
            case HUT -> hut();
            case COTTAGE -> cottage();
            case TWO_ROOM -> twoRoom();
            case FARMHOUSE -> farmhouse();
            case TWO_STOREY -> twoStorey();
            default -> towerHouse();
        }
        if (site.state == FOUNDATIONS || site.state == BURNED) {
            drawFootpath = chance(0.4);
        }
        return finish();
    }

    // =========================================================================================
    // Bodies
    // =========================================================================================

    private int wallStyle() {
        return switch (site.style) {
            case TIMBER_FRAME -> TIMBER;
            case LOG_CABIN -> LOGS;
            case RAISED -> PLANK_WALLS;
            default -> STONE_WALLS;
        };
    }

    /**
     * A footprint: a door in the front, windows spaced along the walls (small ones in stone huts
     * and towers) and a hearth on one wall: 1 left, 2 right, 3 back, 0 none.
     */
    static String[] footprint(int w, int d, int doorU, int hearth, boolean small) {
        char[][] g = new char[d][w];
        for (int v = 0; v < d; v++) {
            for (int u = 0; u < w; u++) {
                boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
                boolean corner = (u == 0 || u == w - 1) && (v == 0 || v == d - 1);
                g[v][u] = corner ? 'C' : edge ? '#' : '.';
            }
        }
        char win = small ? 'w' : 'W';
        for (int u = 1; u < w - 1; u++) {
            int off = Math.abs(u - doorU);
            if (off >= 2 && off % 2 == 0 && u >= 1 && u <= w - 2) {
                g[0][u] = win;
            }
        }
        g[0][doorU] = 'D';
        int mid = d / 2;
        if (d >= 7) {
            g[2][0] = win;
            g[d - 3][w - 1] = win;
        } else if (d >= 5) {
            g[mid][0] = win;
            g[mid][w - 1] = win;
        }
        int bm = w / 2;
        if (w >= 8) {
            g[d - 1][bm - 2] = win;
            g[d - 1][bm + 1] = win;
        } else if (w >= 5) {
            g[d - 1][bm] = win;
        }
        switch (hearth) {
            case 1 -> g[mid][0] = 'H';
            case 2 -> g[mid][w - 1] = 'H';
            case 3 -> g[d - 1][bm] = 'H';
            default -> {
            }
        }
        String[] rows = new String[d];
        for (int v = 0; v < d; v++) {
            rows[v] = new String(g[v]);
        }
        return rows;
    }

    /** A body of the house's style and state at canvas offset (ou, ov). */
    private Body body(int ou, int ov, int w, int d, int hw, int doorU, int hearth, boolean small, int y0) {
        int roof = switch (site.style) {
            case ADOBE -> FLAT;
            case RAISED -> GABLE_V;
            default -> GABLE_U;
        };
        if (site.state == FOUNDATIONS && hearth == 0) {
            // what stands of a house reduced to its foundations is its chimney
            hearth = 2;
        }
        Body b = new Body(ou, ov, footprint(w, d, doorU, hearth, small), hw, wallStyle(), roof, y0);
        b.thatch = site.style == RAISED || site.style == TIMBER_FRAME && site.climate == RuinSites.TEMPERATE && chance(0.25);
        b.plinth = site.style == LOG_CABIN && (site.climate == RuinSites.UPLAND || chance(0.3));
        b.floorKind = switch (site.style) {
            case STONE -> chance(0.5) ? FLOOR_STONE : FLOOR_PLANKS;
            case ADOBE -> FLOOR_MUD;
            default -> FLOOR_PLANKS;
        };
        applyState(b);
        return b;
    }

    /** The state of decay as wall, roof and floor decay, collapse, fire and growth. */
    private void applyState(Body b) {
        switch (site.state) {
            case ROOF_FALLEN -> {
                b.wallDecay = 0.1 + 0.35 * decay;
                b.roofDecay = Math.max(0.62, decay + 0.2);
                b.floorDecay = Math.max(0.6, decay + 0.1);
            }
            case WALL_COLLAPSED -> {
                b.wallDecay = 0.15 + 0.4 * decay;
                b.roofDecay = Math.max(0.5, decay);
                b.floorDecay = 0.75;
                b.collapseSide = 1 + rnd.nextInt(3);
            }
            case BURNED -> {
                b.burned = true;
                b.wallDecay = 0.2 + 0.35 * decay;
                b.roofDecay = 1.0;
                b.floorDecay = 1.0;
            }
            case FOUNDATIONS -> b.foundationsOnly = true;
            default -> {
                b.wallDecay = 0.2 + 0.5 * decay;
                b.roofDecay = Math.min(1.0, decay + 0.15);
                b.floorDecay = Math.min(1.0, decay + 0.2);
            }
        }
    }

    private int hearthSide() {
        double r = rnd.nextDouble();
        return r < 0.4 ? 2 : r < 0.7 ? 1 : r < 0.85 ? 3 : 0;
    }

    // =========================================================================================
    // Layouts
    // =========================================================================================

    private void hut() {
        int[] s = body(HUT, site.style);
        if (site.style == RAISED) {
            Body b = raisedBody(s[0], s[1], s[2], -1);
            raisedProps(b);
            return;
        }
        boolean small = site.style == STONE || site.style == LOG_CABIN && chance(0.5);
        Body b = body(0, 0, s[0], s[1], s[2], spec.doorU(), chance(0.6) ? hearthSide() : 0, small, 0);
        b.build();
        if (site.style == LOG_CABIN && chance(0.4) && !b.burned && !b.foundationsOnly) {
            porch(b);
        }
        furnish(b, 0, b.w, true);
        yard(b);
    }

    private void cottage() {
        int[] s = body(COTTAGE, site.style);
        if (site.style == RAISED) {
            Body b = raisedBody(s[0], s[1], s[2], -1);
            raisedProps(b);
            return;
        }
        int hearth = hearthSide();
        Body b = body(0, 0, s[0], s[1], s[2], spec.doorU(), hearth == 0 ? 2 : hearth, false, 0);
        b.build();
        if (site.style == LOG_CABIN && chance(0.5) && !b.burned && !b.foundationsOnly) {
            porch(b);
        }
        furnish(b, 0, b.w, true);
        if (site.style == ADOBE && !b.foundationsOnly) {
            horno(b.w + 2, 2);
        }
        yard(b);
    }

    private void twoRoom() {
        int[] s = body(TWO_ROOM, site.style);
        if (site.style == RAISED) {
            Body b = raisedBody(s[0], s[1], s[2], s[0] / 2);
            raisedProps(b);
            return;
        }
        int w = s[0];
        // the hearth on an end wall, never against the inner wall
        Body b = body(0, 0, w, s[1], s[2], spec.doorU(), chance(0.5) ? 2 : 1, false, 0);
        b.partition = w / 2;
        b.partitionDoor = 1 + rnd.nextInt(s[1] - 2);
        b.build();
        // the hearth room by the door, the bedroom beyond the inner wall
        furnish(b, 0, b.partition, true);
        bedroom(b, b.partition + 1, w - 1);
        yard(b);
    }

    private void farmhouse() {
        int[] s = body(COTTAGE, site.style);
        Body b;
        if (site.style == RAISED) {
            b = raisedBody(s[0], s[1], s[2], -1);
            raisedProps(b);
            granary(b.w + 2, 1);
        } else {
            b = body(0, 0, s[0], s[1], s[2], spec.doorU(), 2, false, 0);
            b.build();
            furnish(b, 0, b.w, true);
            switch (site.style) {
                case ADOBE -> {
                    horno(b.w + 2, 1);
                    corral(b.w + 2, 5, 6, 5);
                }
                case LOG_CABIN -> shed(b.w + 2, 1, 6, 4);
                default -> {
                    if (chance(0.55)) {
                        barn(b.w + 3, 0);
                    } else {
                        shed(b.w + 2, 1, 6, 4);
                    }
                }
            }
        }
        well(-4, 2, decay < 0.6 && chance(0.6), decay > 0.65);
        garden(0, b.d + 2, Math.min(9, b.w), 5);
        fenceRemains(-6, -2, b.w + 1, b.d + 1, spec.doorU());
        if (chance(0.4)) {
            buried(b.ou + 1 + rnd.nextInt(Math.max(1, b.w - 2)), b.ov + b.d + 1);
        }
    }

    private void twoStorey() {
        int[] s = body(TWO_STOREY, site.style);
        Body b = body(0, 0, s[0], s[1], s[2], spec.doorU(), chance(0.6) ? 2 : 1, site.style == STONE, 0);
        b.levels = new int[]{4};
        // the upper floor is what fell first in a tall house
        b.floorDecay = Math.max(b.floorDecay, 0.7 + 0.2 * decay);
        int lu = 1;
        int lv = b.d - 2;
        b.ladderU = lu;
        b.ladderV = lv;
        b.build();
        // a ladder up the back wall to what is left of the upper floor
        if (!b.foundationsOnly && !b.burned) {
            for (int y = 1; y <= 4; y++) {
                c.add(lu, y, lv, RuinCell.of(RuinPart.LADDER, RuinCell.NORTH));
            }
        }
        reserve(lu, lv);
        furnish(b, 0, b.w, true);
        // what slid off the upper floor: a bed frame, a chest that fell through
        if (!b.foundationsOnly && !b.burned && chance(0.6)) {
            int[] cell = propCell(b, false);
            if (cell != null) {
                c.set(cell[0], 1, cell[1], RuinCell.of(RuinPart.PLANK_STAIR, rnd.nextInt(4)) | RuinCell.TOP);
            }
        }
        yard(b);
    }

    private void towerHouse() {
        int[] s = body(TOWER_HOUSE, site.style);
        Body b = new Body(0, 0, footprint(s[0], s[1], spec.doorU(), 0, true), s[2], STONE_WALLS,
            site.style == ADOBE ? FLAT : chance(0.5) ? PYRAMID : PARAPET, 0);
        b.tower = true;
        b.floorKind = FLOOR_STONE;
        b.levels = new int[]{4, 8};
        applyState(b);
        if (b.foundationsOnly) {
            // a tower house does not burn to its footing: it stands as a stump instead
            b.foundationsOnly = false;
            b.wallDecay = 0.85;
        }
        b.build();
        for (int y = 1; y <= b.hw; y++) {
            if (b.h(b.w / 2, b.d - 1) >= y) {
                c.set(b.w / 2, y, b.d - 2, RuinCell.of(RuinPart.LADDER, RuinCell.NORTH));
            }
        }
        reserve(b.w / 2, b.d - 2);
        // arrow slits up the walls
        int[][] slits = {{0, b.d / 2, 6}, {b.w - 1, b.d / 2, 9}, {b.w / 2, 0, 7}, {b.w - 1, 1, 5}};
        for (int[] sl : slits) {
            if (b.h(sl[0], sl[1]) > sl[2]) {
                c.set(sl[0], sl[2], sl[1], RuinCell.of(RuinPart.AIR));
            }
        }
        int[] cell;
        if (!b.burned && (cell = propCell(b, false)) != null) {
            container(cell[0], 1, cell[1], cell[2], false);
        }
        for (int i = 0; i < 2; i++) {
            if ((cell = propCell(b, false)) != null) {
                container(cell[0], 1, cell[1], cell[2], true);
            }
        }
        if (chance(0.5) && (cell = propCell(b, false)) != null) {
            c.set(cell[0], 1, cell[1], RuinCell.of(RuinPart.CAULDRON));
        }
        floorPlants(b);
        overgrowth(b);
        // a lean-to against the tower, where the household kept its stores
        if (chance(0.5)) {
            shed(b.w + 1, 1, 4, 4);
        }
        yard(b);
    }

    // =========================================================================================
    // Raised houses (hot, wet country)
    // =========================================================================================

    /** A plank house on stilts with a thatched roof, a balcony along the front and a ladder up. */
    private Body raisedBody(int w, int d, int hw, int partition) {
        int y0 = 3;
        Body b = body(0, 0, w, d, hw, spec.doorU(), 0, false, y0);
        if (partition > 0) {
            b.partition = partition;
            b.partitionDoor = d / 2;
        }
        b.build();
        // stilts: corners and every other wall cell, from the ground to the platform, corners up to the eaves
        for (int u = 0; u < w; u += 2) {
            stilt(b, u, 0);
            stilt(b, u, d - 1);
        }
        stilt(b, w - 1, 0);
        stilt(b, w - 1, d - 1);
        for (int v = 2; v < d - 1; v += 2) {
            stilt(b, 0, v);
            stilt(b, w - 1, v);
        }
        // balcony along the front, with a ladder up from the ground
        for (int u = 0; u < w; u++) {
            if (u % 2 == 0 || u == w - 1) {
                stilt(b, u, -1, 2);
            }
            if (u % 2 == 1 || n2(u * 0.7, 5.0) + decay * 0.8 < 0.8) {
                c.set(u, y0, -1, RuinCell.of(RuinPart.PLANK_SLAB) | RuinCell.TOP);
            }
            if ((u == 0 || u == w - 1) && !chance(decay * 0.6)) {
                c.set(u, y0 + 1, -1, RuinCell.of(RuinPart.FENCE));
            }
        }
        int door = spec.doorU();
        c.set(door, y0, -1, RuinCell.of(RuinPart.PLANKS));
        pathStart = -3;
        for (int y = 1; y <= y0; y++) {
            c.set(door, y, -2, RuinCell.of(RuinPart.LADDER, RuinCell.NORTH));
        }
        // under the house: leave the ground, but keep the space under the platform open
        for (int u = 0; u < w; u++) {
            for (int v = 0; v < d; v++) {
                for (int y = 1; y < y0; y++) {
                    c.add(u, y, v, RuinCell.of(RuinPart.AIR) | RuinCell.SOFT);
                }
            }
        }
        reserve(door, 1);
        return b;
    }

    private void stilt(Body b, int u, int v) {
        boolean corner = (u == 0 || u == b.w - 1) && (v == 0 || v == b.d - 1);
        stilt(b, u, v, corner ? b.y0 + b.h(u, v) : b.y0 - 1);
    }

    private void stilt(Body b, int u, int v, int top) {
        for (int y = 0; y <= top; y++) {
            if (y < b.y0 || v >= 0) {
                if (c.empty(u, y, v) || y < b.y0) {
                    c.set(u, y, v, RuinCell.of(RuinPart.LOG));
                }
            }
        }
        c.setFill(u, v, RuinCell.of(RuinPart.LOG));
    }

    private void raisedProps(Body b) {
        int y = b.y0 + 1;
        int[] cell = propCell(b, false);
        if (cell != null) {
            container(cell[0], y, cell[1], cell[2], chance(0.4));
        }
        if ((cell = propCell(b, false)) != null && chance(0.6)) {
            c.set(cell[0], y, cell[1], RuinCell.withAux(RuinCell.of(RuinPart.FLOWER_POT), rnd.nextInt(3)));
        }
        if ((cell = propCell(b, true)) != null && chance(0.5)) {
            // a sleeping mat
            c.set(cell[0], y, cell[1], RuinCell.withAux(RuinCell.of(RuinPart.CARPET), 1 + rnd.nextInt(2)));
        }
        if (b.partition > 0 && (cell = propCell(b, false)) != null) {
            container(cell[0], y, cell[1], cell[2], true);
        }
        overgrowth(b);
        campfireRing(b, chance(0.5), 0);
        if (chance(0.4)) {
            // firewood kept dry under the platform
            c.decal(1, 2, 1, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_X));
            c.decal(2, 2, 1, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_X));
        }
    }

    /** A little granary on stilts beside a raised farmhouse. */
    private void granary(int u0, int v0) {
        for (int u = u0; u < u0 + 3; u++) {
            for (int v = v0; v < v0 + 3; v++) {
                boolean corner = (u == u0 || u == u0 + 2) && (v == v0 || v == v0 + 2);
                if (corner) {
                    c.setFill(u, v, RuinCell.of(RuinPart.LOG));
                    for (int y = 0; y <= 4; y++) {
                        c.set(u, y, v, RuinCell.of(RuinPart.LOG));
                    }
                } else {
                    c.set(u, 2, v, RuinCell.of(RuinPart.PLANKS));
                    if (n2(u * 0.8 + 70.0, v * 0.8) + decay < 1.1) {
                        c.set(u, 3, v, RuinCell.of(RuinPart.HAY));
                    }
                }
                if (n2(u * 0.6 + 77.0, v * 0.6) + decay < 1.0) {
                    c.set(u, 5, v, RuinCell.withAxis(RuinCell.of(RuinPart.THATCH), RuinCell.AXIS_X));
                }
            }
        }
        for (int u = u0; u < u0 + 3; u++) {
            for (int v = v0; v < v0 + 3; v++) {
                for (int y = 1; y < 2; y++) {
                    c.add(u, y, v, RuinCell.of(RuinPart.AIR) | RuinCell.SOFT);
                }
            }
        }
    }

    // =========================================================================================
    // Furniture
    // =========================================================================================

    /**
     * Furnishes the room between {@code u0} and {@code u1} (body columns): what the household
     * left behind, fewer things the further the house has gone.
     */
    private void furnish(Body b, int u0, int u1, boolean hearthRoom) {
        int y = b.y0 + 1;
        if (b.foundationsOnly || b.burned) {
            ashes(b);
            return;
        }
        int[] cell;
        // the chest: every lived-in house had one
        if ((cell = propCellIn(b, u0, u1)) != null) {
            container(cell[0], y, cell[1], cell[2], false);
        }
        if (chance(0.5) && (cell = propCellIn(b, u0, u1)) != null) {
            container(cell[0], y, cell[1], cell[2], true);
            if (chance(0.4) && c.empty(cell[0], y + 1, cell[1])) {
                c.set(cell[0], y + 1, cell[1], RuinCell.of(RuinPart.BARREL, RuinCell.NORTH) | RuinCell.TOP);
            }
        }
        if (chance(0.5) && (cell = propCellIn(b, u0, u1)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.CRAFTING_TABLE));
        }
        if (hearthRoom && chance(0.35) && (cell = propCellIn(b, u0, u1)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.FURNACE, cell[2]));
        }
        if (hearthRoom && chance(0.25) && (cell = propCellIn(b, u0, u1)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.CAULDRON));
        }
        if (site.style == ADOBE) {
            int pots = 1 + rnd.nextInt(3);
            for (int i = 0; i < pots; i++) {
                if ((cell = propCellIn(b, u0, u1)) != null) {
                    c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.DECORATED_POT, rnd.nextInt(4)) | (chance(0.5) ? RuinCell.FLAG : 0));
                }
            }
        }
        if (b.w >= 7 && chance(0.35) && (cell = propCellIn(b, u0, u1)) != null && cell[1] == b.ov + b.d - 2) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.BOOKSHELF));
        }
        if (chance(0.45) && (cell = propCellIn(b, u0, u1)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.withAux(RuinCell.of(RuinPart.FLOWER_POT), rnd.nextInt(3)));
        }
        if (chance(0.15) && (cell = propCellIn(b, u0, u1)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.LANTERN));
        }
        table(b, u0, u1);
        if (b.partition < 0) {
            bed(b, u0, u1);
        }
        floorPlants(b);
        overgrowth(b);
    }

    /** A bedroom: the bed or its rotten frame, a chest at its foot, a rug, a pot on the sill. */
    private void bedroom(Body b, int u0, int u1) {
        if (b.foundationsOnly || b.burned) {
            return;
        }
        bed(b, u0, u1);
        int y = b.y0 + 1;
        int[] cell;
        if (chance(0.6) && (cell = propCellIn(b, u0, u1)) != null) {
            container(cell[0], y, cell[1], cell[2], false);
        }
        if (chance(0.4) && (cell = propCellIn(b, u0, u1)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.withAux(RuinCell.of(RuinPart.FLOWER_POT), rnd.nextInt(3)));
        }
        if (chance(0.3) && (cell = propCellIn(b, u0, u1)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.withAux(RuinCell.of(RuinPart.CARPET), rnd.nextInt(3)));
        }
    }

    /** A prop cell against a wall between two body columns (inclusive of u0, exclusive of u1). */
    private int[] propCellIn(Body b, int u0, int u1) {
        for (int tries = 0; tries < 6; tries++) {
            int[] cell = propCell(b, false);
            if (cell == null) {
                return null;
            }
            int u = cell[0] - b.ou;
            if (u >= u0 && u < u1) {
                return cell;
            }
            // not in this room: free the cell again for the other room
            reserved.remove(reserved.size() - 1);
            if (tries == 5) {
                return null;
            }
        }
        return null;
    }

    /** A bed against the back wall: a real one in the better kept houses, else a rotten frame. */
    private void bed(Body b, int u0, int u1) {
        if (!chance(0.75)) {
            return;
        }
        int y = b.y0 + 1;
        int lo = Math.max(1, u0 + (u0 > 0 ? 0 : 1));
        int hi = Math.min(b.w - 2, u1 - 1);
        if (hi < lo) {
            return;
        }
        int u = b.ou + lo + rnd.nextInt(hi - lo + 1);
        int head = b.ov + b.d - 2;
        int foot = head - 1;
        if (reservedCell(u, head) || reservedCell(u, foot) || !c.empty(u, y, head) || !c.empty(u, y, foot)
            || !c.part(u, b.y0, head).structural || !c.part(u, b.y0, foot).structural) {
            return;
        }
        reserve(u, head);
        reserve(u, foot);
        if (decay < 0.5 && site.state != OVERGROWN && chance(0.4)) {
            int colour = rnd.nextInt(3);
            c.set(u, y, head, RuinCell.withAux(RuinCell.of(RuinPart.BED, RuinCell.SOUTH) | RuinCell.TOP, colour));
            c.set(u, y, foot, RuinCell.withAux(RuinCell.of(RuinPart.BED, RuinCell.SOUTH), colour));
        } else {
            // the broken bed: a rotten frame of slabs, the blanket still on it
            c.set(u, y, head, RuinCell.of(RuinPart.PLANK_SLAB));
            c.set(u, y, foot, chance(0.3) ? RuinCell.of(RuinPart.PLANK_STAIR, RuinCell.SOUTH) : RuinCell.of(RuinPart.PLANK_SLAB));
            if (RuinCell.part(c.get(u, y, foot)) == RuinPart.PLANK_SLAB && chance(0.6)) {
                c.set(u, y + 1, foot, RuinCell.withAux(RuinCell.of(RuinPart.CARPET), rnd.nextInt(3)));
            }
        }
    }

    /** A table (a post with a cloth on it) and a chair or two, somewhere in the middle of the room. */
    private void table(Body b, int u0, int u1) {
        if (!chance(0.55) || b.d < 5) {
            return;
        }
        int y = b.y0 + 1;
        for (int tries = 0; tries < 6; tries++) {
            int lo = Math.max(2, u0 + 1);
            int hi = Math.min(b.w - 3, u1 - 2);
            if (hi < lo) {
                return;
            }
            int u = b.ou + lo + rnd.nextInt(hi - lo + 1);
            int v = b.ov + 2 + rnd.nextInt(Math.max(1, b.d - 4));
            if (reservedCell(u, v) || !c.empty(u, y, v) || !c.part(u, b.y0, v).structural || u - b.ou == b.partition) {
                continue;
            }
            reserve(u, v);
            c.set(u, y, v, RuinCell.of(RuinPart.FENCE));
            if (chance(0.7)) {
                c.set(u, y + 1, v, RuinCell.withAux(RuinCell.of(RuinPart.CARPET), rnd.nextInt(3)));
            }
            int chairs = 1 + rnd.nextInt(2);
            for (int k = 0; k < 4 && chairs > 0; k++) {
                int dir = rnd.nextInt(4);
                int su = u + RuinCell.DX[dir];
                int sv = v + RuinCell.DZ[dir];
                if (reservedCell(su, sv) || !c.empty(su, y, sv) || !c.part(su, b.y0, sv).structural || su - b.ou == b.partition) {
                    continue;
                }
                reserve(su, sv);
                // a chair faces the table, or lies tipped over
                boolean tipped = chance(decay * 0.5);
                c.set(su, y, sv, RuinCell.of(RuinPart.PLANK_STAIR, tipped ? rnd.nextInt(4) : RuinCell.opposite(dir)) | (tipped ? RuinCell.TOP : 0));
                chairs--;
            }
            return;
        }
    }

    /** What is left inside a burned house or bare foundations: a cauldron, charred litter, a buried chest. */
    private void ashes(Body b) {
        int y = b.y0 + 1;
        int[] cell;
        if (chance(0.5) && (cell = propCell(b, false)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.of(RuinPart.CAULDRON));
        }
        if (chance(0.3) && (cell = propCell(b, false)) != null) {
            c.set(cell[0], y, cell[1], RuinCell.withAux(RuinCell.of(RuinPart.ANVIL, cell[2]), 2));
        }
        if (chance(0.5) && (cell = propCell(b, false)) != null) {
            // the chest survived under the rubble
            container(cell[0], y, cell[1], cell[2], false);
        }
        for (int u = 1; u < b.w - 1; u++) {
            for (int v = 1; v < b.d - 1; v++) {
                int cu = b.ou + u;
                int cv = b.ov + v;
                if (b.burned && RuinCell.part(c.get(cu, b.y0, cv)) == RuinPart.SOIL && n2(cu * 0.4, cv * 0.4) > -0.1) {
                    c.set(cu, b.y0, cv, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 1));
                }
            }
        }
        floorPlants(b);
        if (site.state == FOUNDATIONS) {
            overgrowth(b);
        }
    }

    // =========================================================================================
    // Porch, outbuildings, oven, corral, yard
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
            if (u == spec.doorU()) {
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
    private void shed(int u0, int v0, int w, int d) {
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
            int v = v0 + 1 + rnd.nextInt(Math.max(1, d - 2));
            c.set(u, 1, v, RuinCell.withAxis(RuinCell.of(RuinPart.HAY), rnd.nextInt(3)));
        }
        if (chance(0.5)) {
            c.set(u0 + 1, 1, v0 + d - 2, RuinCell.of(RuinPart.COMPOSTER));
        }
        if (chance(0.25)) {
            c.set(u0 + w - 2, 1, v0 + d - 2, RuinCell.withAux(RuinCell.of(RuinPart.ANVIL, RuinCell.EAST), 1 + rnd.nextInt(2)));
        }
    }

    /**
     * A barn: a tall gabled timber building with a wide doorway at the gable end, its roof fallen
     * in, hay and a cart wheel inside.
     */
    private void barn(int u0, int v0) {
        String[] rows = {"C#DDD#C", "#.....#", "#.....#", "w.....w", "#.....#", "#.....#", "#.....#", "C##w##C"};
        Body barn = new Body(u0, v0, rows, 4, site.style == STONE ? STONE_WALLS : TIMBER, GABLE_V, 0);
        barn.floorKind = FLOOR_EARTH;
        barn.wallDecay = Math.min(0.85, decay + 0.1);
        barn.roofDecay = Math.max(0.6, decay + 0.15);
        barn.burned = site.state == BURNED;
        barn.foundationsOnly = site.state == FOUNDATIONS;
        barn.build();
        int hay = 2 + rnd.nextInt(4);
        for (int i = 0; i < hay; i++) {
            int u = u0 + 1 + rnd.nextInt(5);
            int v = v0 + 3 + rnd.nextInt(4);
            if (c.empty(u, 1, v) && c.part(u, 0, v).structural) {
                c.set(u, 1, v, RuinCell.withAxis(RuinCell.of(RuinPart.HAY), rnd.nextInt(3)));
                if (chance(0.3) && c.empty(u, 2, v)) {
                    c.set(u, 2, v, RuinCell.of(RuinPart.HAY));
                }
            }
        }
        if (chance(0.5) && c.empty(u0 + 1, 1, v0 + 6)) {
            c.set(u0 + 1, 1, v0 + 6, RuinCell.of(RuinPart.COMPOSTER));
        }
        floorPlants(barn);
        overgrowth(barn);
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

    /** A low mud-brick corral wall, broken in places, with a gap for the gate. */
    private void corral(int u0, int v0, int w, int d) {
        for (int u = u0; u < u0 + w; u++) {
            for (int v = v0; v < v0 + d; v++) {
                boolean edge = u == u0 || v == v0 || u == u0 + w - 1 || v == v0 + d - 1;
                if (!edge || v == v0 && u == u0 + w / 2) {
                    continue;
                }
                if (n2(u * 0.4 + 90.0, v * 0.4) > decay - 0.45) {
                    c.decal(u, v, 1, RuinCell.of(RuinPart.MASONRY_WALL));
                } else if (chance(0.5)) {
                    c.decal(u, v, 1, RuinCell.of(RuinPart.MASONRY_SLAB));
                }
            }
        }
    }

    /** The yard of an ordinary house: fence remains, a fire ring, a woodpile, a well, a garden patch, buried things. */
    private void yard(Body b) {
        if (site.variant != FARMHOUSE) {
            if (site.style != ADOBE || site.variant != COTTAGE) {
                campfireRing(b, false, 0);
            } else {
                // the adobe's oven stands to the east, so its fire ring goes west
                campfireRing(b, false, -1);
            }
            if (chance(0.45)) {
                fenceRemains(b.ou - 2, b.ov - 2, b.ou + b.w + 1, b.ov + b.d + 1, spec.doorU());
            }
            if (chance(0.25)) {
                garden(b.ou, b.ov + b.d + 2, Math.min(9, b.w), 4);
            } else if (chance(0.15) && !b.foundationsOnly) {
                well(b.ou + b.w + 3, b.ov + b.d - 2, decay < 0.6 && chance(0.5), decay > 0.7);
            }
        }
        if (chance(0.35) && !b.foundationsOnly && !b.burned) {
            woodpile(b.ou + 2, b.ov + b.d, Math.min(3, b.w - 3));
        }
        if (chance(0.4)) {
            int count = 1 + rnd.nextInt(2);
            for (int i = 0; i < count; i++) {
                int u = b.ou + 1 + rnd.nextInt(Math.max(1, b.w - 2));
                int v = b.ov + 1 + rnd.nextInt(Math.max(1, b.d - 2));
                if (c.part(u, b.y0, v) == RuinPart.SOIL && b.y0 == 0 && c.empty(u, 1, v)) {
                    String loot = chance(0.1) ? "minecraft:archaeology/trail_ruins_rare" : "minecraft:archaeology/trail_ruins_common";
                    c.set(u, 0, v, RuinCell.of(RuinPart.SUSPICIOUS_GRAVEL));
                    c.extras.add(new RuinPlan.Extra(u, 0, v, false, loot, rnd.nextLong(), null));
                } else {
                    buried(b.ou - 2 + rnd.nextInt(b.w + 4), b.ov + b.d + 1);
                }
            }
        }
    }

    @Override
    String[] boardText() {
        return new String[]{"", site.name, site.variant == FARMHOUSE ? "@farm|Farm" : "", ""};
    }
}
