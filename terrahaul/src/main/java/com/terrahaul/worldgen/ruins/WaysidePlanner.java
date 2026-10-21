package com.terrahaul.worldgen.ruins;

/**
 * Plans the small things travellers left beside the road:
 * <ul>
 *   <li>an abandoned well: with its {@link #WINCH} frame, {@link #COLLAPSED} and filled with
 *       rubble, under a little {@link #ROOFED} well house, or with a stone {@link #TROUGH};</li>
 *   <li>a broken {@link #WAGON} with a lost wheel and its load spilled, a {@link #HAND_CART}
 *       tipped on its side, a {@link #COVERED_WAGON} with torn canvas, or the overgrown
 *       {@link #WRECK} of a chassis with its wheels lying in the grass;</li>
 *   <li>a wayside shrine: a stone {@link #NICHE} sheltering a lantern, a {@link #ROOFED_POST}
 *       with a shelf for candles, or a {@link #CAIRN} of stacked stones; flower pots, candles
 *       and wild flowers lie at its foot;</li>
 *   <li>an old campsite: a fire ring with log seats and a tattered {@link #TENT}, a
 *       {@link #LEAN_TO} shelter, a {@link #HUNTERS} camp with a drying rack, or a camp whose
 *       tent has {@link #FALLEN} flat;</li>
 *   <li>field walls: {@link #DRY_STONE} walls or {@link #RAILS} of a rotten fence around an old
 *       field, a {@link #HEDGEROW} between stone gate piers, or a ruined {@link #GATEWAY} arch
 *       with stubs of wall; inside, the furrows still show, and a scarecrow stands.</li>
 * </ul>
 * Walls and fences of fields, rubble and spilled loads are laid on the ground of their own column,
 * so they follow the terrain and never float.
 */
public final class WaysidePlanner extends RuinBuilder {
    // wells
    public static final int WINCH = 0;
    public static final int COLLAPSED = 1;
    public static final int ROOFED = 2;
    public static final int TROUGH = 3;
    public static final String[] WELL_NAMES = {"winch", "collapsed", "roofed", "trough"};
    // wagons
    public static final int WAGON = 0;
    public static final int HAND_CART = 1;
    public static final int COVERED_WAGON = 2;
    public static final int WRECK = 3;
    public static final String[] WAGON_NAMES = {"wagon", "hand_cart", "covered_wagon", "wreck"};
    // shrines
    public static final int NICHE = 0;
    public static final int ROOFED_POST = 1;
    public static final int CAIRN = 2;
    public static final String[] SHRINE_NAMES = {"niche", "roofed_post", "cairn"};
    // campsites
    public static final int TENT = 0;
    public static final int LEAN_TO = 1;
    public static final int HUNTERS = 2;
    public static final int FALLEN = 3;
    public static final String[] CAMP_NAMES = {"tent", "lean_to", "hunters", "fallen_tent"};
    // fields
    public static final int DRY_STONE = 0;
    public static final int RAILS = 1;
    public static final int HEDGEROW = 2;
    public static final int GATEWAY = 3;
    public static final String[] FIELD_NAMES = {"dry_stone", "rails", "hedgerow", "gateway"};

    private WaysidePlanner(TrailRuin site) {
        super(site);
    }

    public static RuinPlan plan(TrailRuin site) {
        return new WaysidePlanner(site).build();
    }

    /** The canonical box of a wayside ruin. */
    public static RuinSpec spec(int kind, int variant) {
        return switch (kind) {
            case TrailRuin.WELL -> RuinSpec.of(5, 5, 8, -3, -2, 8, 7, 2, 2.5, 1);
            // a wagon stands on the verge, its long side along the trail; no footpath
            case TrailRuin.WAGON -> new RuinSpec(7, 3, 7, -3, -1, 10, 7, 3, 2.0, 0, false, 3.2);
            case TrailRuin.SHRINE -> RuinSpec.of(3, 3, 7, -3, -2, 6, 5, 1, 2.0, 0);
            case TrailRuin.CAMPSITE -> RuinSpec.of(9, 8, 6, -2, -2, 12, 11, 4, 3.0, 1);
            default -> new RuinSpec(17, 13, 6, -1, -1, 18, 14, 8, 6.0, 1, false, 4.0);
        };
    }

    private RuinPlan build() {
        switch (site.kind) {
            case TrailRuin.WELL -> well();
            case TrailRuin.WAGON -> wagon();
            case TrailRuin.SHRINE -> shrine();
            case TrailRuin.CAMPSITE -> campsite();
            default -> field();
        }
        return finish();
    }

    // =========================================================================================
    // Wells
    // =========================================================================================

    private void well() {
        int cu = 2;
        int cv = 2;
        nameBoard = 0.0;
        switch (site.variant) {
            case COLLAPSED -> {
                well(cu, cv, false, true);
                // the shaft fell in: rubble and gravel fill it to the brim
                for (int y = -4; y <= 0; y++) {
                    c.set(cu, y, cv, RuinCell.of(y == 0 ? RuinPart.GRAVEL : RuinPart.MASONRY));
                }
                // the winch post lies where it fell
                for (int k = 1; k <= 3; k++) {
                    c.decal(cu + 1 + k, cv + 1, 1, RuinCell.withAxis(RuinCell.of(RuinPart.STRIPPED_LOG), RuinCell.AXIS_X));
                }
                rubbleField(cu - 2, cv - 2, cu + 2, cv + 3, 0.25);
            }
            case ROOFED -> {
                well(cu, cv, false, false);
                wellHouse(cu, cv);
            }
            case TROUGH -> {
                well(cu, cv, decay < 0.5 && chance(0.5), false);
                trough(cu + 2, cv + 1);
            }
            default -> well(cu, cv, true, decay > 0.75);
        }
        // a bucket left by the rim, the ground worn bare around it
        if (site.variant != COLLAPSED && chance(0.6)) {
            c.decal(cu - 2, cv + 1, 1, RuinCell.of(RuinPart.CAULDRON));
        }
        for (int u = cu - 2; u <= cu + 2; u++) {
            for (int v = cv - 2; v <= cv + 2; v++) {
                boolean ring = Math.max(Math.abs(u - cu), Math.abs(v - cv)) == 2;
                if (ring && n2(u * 0.6 + 4.0, v * 0.6) > 0.1) {
                    c.decal(u, v, 0, RuinCell.of(RuinPart.COARSE_DIRT));
                }
            }
        }
        if (chance(0.5)) {
            logSeat(cu - 3, cv - 1, RuinCell.AXIS_Z);
        }
        if (chance(0.35)) {
            // a coin or a pot dropped long ago, under the trodden ground
            buried(cu + (chance(0.5) ? 2 : -2), cv + 2);
        }
        meadow(-2, -1, 7, 6, 0.2);
        weather(-1, -1, 6, 6, -4, 5, 0);
    }

    /** A little gabled well house: masonry piers at the corners of the ring, posts and a roof. */
    private void wellHouse(int cu, int cv) {
        for (int du = -1; du <= 1; du += 2) {
            for (int dv = -1; dv <= 1; dv += 2) {
                c.set(cu + du, 1, cv + dv, RuinCell.of(RuinPart.MASONRY));
                c.set(cu + du, 2, cv + dv, RuinCell.of(RuinPart.FENCE));
                c.set(cu + du, 3, cv + dv, RuinCell.of(RuinPart.FENCE));
            }
        }
        // the roof ridge runs along v; rows of stairs fall to the eaves on both sides
        for (int v = cv - 2; v <= cv + 2; v++) {
            boolean alive = n2(v * 0.8 + 101.0, 3.0) + 0.45 > decay;
            if (!alive && v != cv - 1 && v != cv + 1) {
                continue;
            }
            c.set(cu - 2, 4, v, RuinCell.of(RuinPart.ROOF_STAIR, RuinCell.EAST));
            c.set(cu + 2, 4, v, RuinCell.of(RuinPart.ROOF_STAIR, RuinCell.WEST));
            c.set(cu - 1, 4, v, RuinCell.of(RuinPart.PLANKS));
            c.set(cu + 1, 4, v, RuinCell.of(RuinPart.PLANKS));
            c.set(cu - 1, 5, v, RuinCell.of(RuinPart.ROOF_STAIR, RuinCell.EAST));
            c.set(cu + 1, 5, v, RuinCell.of(RuinPart.ROOF_STAIR, RuinCell.WEST));
            c.set(cu, 5, v, RuinCell.of(RuinPart.ROOF_SLAB));
            c.set(cu, 4, v, RuinCell.of(RuinPart.AIR));
        }
        c.set(cu, 4, cv, RuinCell.withAxis(RuinCell.of(RuinPart.STRIPPED_LOG), RuinCell.AXIS_Z));
        if (chance(0.6)) {
            c.set(cu, 3, cv, RuinCell.of(RuinPart.CHAIN));
        }
    }

    /** A stone trough beside the well: two cauldrons between slab ends. */
    private void trough(int u, int v) {
        c.decal(u, v - 1, 1, RuinCell.of(RuinPart.MASONRY_SLAB));
        c.decal(u, v, 1, RuinCell.of(RuinPart.CAULDRON));
        c.decal(u, v + 1, 1, RuinCell.of(RuinPart.CAULDRON));
        c.decal(u, v + 2, 1, RuinCell.of(RuinPart.MASONRY_SLAB));
    }

    // =========================================================================================
    // Wagons
    // =========================================================================================

    private void wagon() {
        // worn ground under the wagon: the course its parts stand on
        for (int u = -1; u <= 7; u++) {
            for (int v = -1; v <= 3; v++) {
                if (u >= 0 && u <= 6 && v >= 0 && v <= 2 || n2(u * 0.7 + 8.0, v * 0.7) > 0.2) {
                    ground(u, v, n2(u * 0.5, v * 0.5 + 9.0) > 0 ? 1 : 0);
                }
            }
        }
        switch (site.variant) {
            case HAND_CART -> handCart();
            case COVERED_WAGON -> {
                wagonBed(true);
                canvasHoops();
            }
            case WRECK -> wreck();
            default -> wagonBed(true);
        }
        if (site.variant != WRECK) {
            spill();
        }
        meadow(-3, -1, 10, 6, 0.15);
    }

    /**
     * A four-wheeled wagon: two axles on the ground course, a slab bed on them, side rails, the
     * shafts lying in front, and open trapdoors for wheels. One wheel broke: that corner of the
     * bed sank and the wheel lies flat beside it.
     */
    private void wagonBed(boolean rails) {
        int broken = rnd.nextInt(4);
        int[][] wheels = {{1, -1}, {5, -1}, {1, 3}, {5, 3}};
        for (int u : new int[]{1, 5}) {
            for (int v = 0; v <= 2; v++) {
                c.set(u, 1, v, RuinCell.withAxis(RuinCell.of(RuinPart.STRIPPED_LOG), RuinCell.AXIS_Z));
            }
        }
        int bu = wheels[broken][0];
        int bv = wheels[broken][1];
        for (int u = 0; u <= 6; u++) {
            for (int v = 0; v <= 2; v++) {
                boolean sunk = Math.abs(u - bu) <= 1 && Math.abs(v - (bv < 0 ? 0 : 2)) <= 0;
                if (n2(u * 0.9 + 25.0, v * 0.9) + 0.6 < decay) {
                    continue;
                }
                c.set(u, sunk ? 1 : 2, v, sunk ? RuinCell.of(RuinPart.PLANK_SLAB) | RuinCell.TOP : RuinCell.of(RuinPart.PLANK_SLAB));
            }
        }
        for (int k = 0; k < 4; k++) {
            int u = wheels[k][0];
            int v = wheels[k][1];
            if (k == broken) {
                c.decal(u + (rnd.nextInt(3) - 1), v + (v < 0 ? -1 : 1), 1, RuinCell.of(RuinPart.TRAPDOOR, rnd.nextInt(4)));
                continue;
            }
            int facing = v < 0 ? RuinCell.NORTH : RuinCell.SOUTH;
            c.set(u, 1, v, RuinCell.of(RuinPart.TRAPDOOR, facing) | RuinCell.FLAG);
        }
        if (rails) {
            for (int u = 0; u <= 6; u++) {
                for (int v : new int[]{0, 2}) {
                    if (RuinCell.part(c.get(u, 2, v)) == RuinPart.PLANK_SLAB && (u == 0 || u == 6 || n2(u * 0.8, v + 40.0) + decay < 0.9)) {
                        c.set(u, 3, v, RuinCell.of(RuinPart.FENCE));
                    }
                }
            }
        }
        // the shafts lie on the ground in front of the wagon
        for (int v : new int[]{0, 2}) {
            ground(-1, v, 1);
            c.set(-1, 1, v, RuinCell.of(RuinPart.FENCE));
            if (chance(0.6)) {
                c.decal(-2, v, 1, RuinCell.of(RuinPart.FENCE));
            }
        }
        // what is left of the load
        for (int u = 1; u <= 5; u++) {
            int v = 1;
            if (c.part(u, 2, v) == RuinPart.PLANK_SLAB && c.empty(u, 3, v) && chance(0.45)) {
                double r = rnd.nextDouble();
                c.set(u, 3, v, r < 0.4 ? RuinCell.of(RuinPart.BARREL, RuinCell.NORTH) | RuinCell.TOP
                    : r < 0.7 ? RuinCell.of(RuinPart.COMPOSTER) : RuinCell.withAxis(RuinCell.of(RuinPart.HAY), RuinCell.AXIS_X));
            }
        }
    }

    /**
     * Torn canvas on hoops over a wagon bed: fence hoops rise from the bed over both axles and in
     * the middle, and the canvas runs over them as a tunnel, two courses down each side and one
     * across the top. The canvas tears between the hoops first; where the bed sank the hoop went
     * with it, and the canvas that hung from it is gone.
     */
    private void canvasHoops() {
        int colour = rnd.nextInt(3);
        boolean[] hoop = new boolean[7];
        for (int u = 1; u <= 5; u += 2) {
            // a hoop stands on the bed, or over an axle where the boards rotted away on the axle itself
            hoop[u] = true;
            for (int v : new int[]{0, 2}) {
                hoop[u] &= c.part(u, 2, v) == RuinPart.PLANK_SLAB || u != 3 && c.structural(u, 1, v);
            }
            if (!hoop[u]) {
                continue;
            }
            for (int v : new int[]{0, 2}) {
                if (c.part(u, 2, v) != RuinPart.PLANK_SLAB) {
                    c.set(u, 2, v, RuinCell.of(RuinPart.FENCE));
                }
                c.set(u, 3, v, RuinCell.of(RuinPart.FENCE));
            }
        }
        // the cross-section of the cover: {v, y}
        int[][] section = {{0, 4}, {0, 5}, {1, 5}, {2, 5}, {2, 4}};
        int first = hoop[1] ? 1 : hoop[3] ? 3 : 5;
        int last = hoop[5] ? 5 : hoop[3] ? 3 : 1;
        if (!hoop[first]) {
            return;
        }
        // the canvas spans the hoops that stand and hangs a block beyond the end ones
        for (int u = first - 1; u <= last + 1; u++) {
            for (int[] cell : section) {
                // over a hoop the canvas holds longest
                double hold = hoop[u] ? 0.75 : 0.5;
                if (n3(u * 0.8 + 66.0, cell[0] * 0.9, cell[1] * 0.9) + hold < decay) {
                    continue;
                }
                c.set(u, cell[1], cell[0], RuinCell.withAux(RuinCell.of(RuinPart.WOOL), colour));
            }
        }
    }

    /** A two-wheeled hand cart tipped on its side, its handles in the air and a wheel rolled away. */
    private void handCart() {
        for (int u = 1; u <= 3; u++) {
            for (int y = 1; y <= 2; y++) {
                if (n2(u * 0.9 + 3.0, y) + 0.7 > decay) {
                    c.set(u, y, 1, RuinCell.of(RuinPart.PLANKS));
                }
            }
            c.set(u, 1, 2, RuinCell.of(RuinPart.PLANK_SLAB));
        }
        c.set(2, 1, 1, RuinCell.of(RuinPart.PLANKS));
        // one wheel still on its axle, facing up the slope of the bed; the other lies away
        c.set(2, 1, 0, RuinCell.of(RuinPart.TRAPDOOR, RuinCell.NORTH) | RuinCell.FLAG);
        c.decal(5 + rnd.nextInt(2), -1 + rnd.nextInt(3), 1, RuinCell.of(RuinPart.TRAPDOOR, rnd.nextInt(4)));
        // the handles
        c.set(0, 1, 1, RuinCell.of(RuinPart.FENCE));
        c.set(-1, 1, 1, RuinCell.of(RuinPart.FENCE));
        ground(-1, 1, 1);
    }

    /**
     * A course of earth at the floor level, filled with dirt down to the actual ground, so the
     * parts standing on it never hang over a dip.
     */
    private void ground(int u, int v, int soil) {
        c.set(u, 0, v, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), soil));
        c.setFill(u, v, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 3));
    }

    /** The chassis of a wagon long gone: axles, a few boards, wheels in the grass, all grown over. */
    private void wreck() {
        growth = Math.max(growth, 0.5);
        for (int u : new int[]{1, 5}) {
            for (int v = 0; v <= 2; v++) {
                if (chance(0.8)) {
                    c.set(u, 1, v, RuinCell.withAxis(RuinCell.of(RuinPart.STRIPPED_LOG), RuinCell.AXIS_Z));
                }
            }
        }
        for (int u = 0; u <= 6; u++) {
            for (int v = 0; v <= 2; v++) {
                if (u != 1 && u != 5 && chance(0.35)) {
                    c.set(u, 1, v, RuinCell.of(RuinPart.PLANK_SLAB));
                }
            }
        }
        for (int k = 0; k < 3; k++) {
            c.decal(rnd.nextInt(8) - 1, chance(0.5) ? -1 : 4, 1, RuinCell.of(RuinPart.TRAPDOOR, rnd.nextInt(4)));
        }
        for (int u = -1; u <= 7; u++) {
            for (int v = -1; v <= 3; v++) {
                if (c.empty(u, 1, v) && c.structural(u, 0, v) && chance(0.35)) {
                    c.set(u, 1, v, chance(0.3) ? RuinCell.of(RuinPart.FERN) : RuinCell.of(RuinPart.SHORT_GRASS));
                } else if (c.part(u, 1, v) == RuinPart.PLANK_SLAB && chance(0.25)) {
                    c.set(u, 2, v, RuinCell.of(RuinPart.MOSS_CARPET));
                }
            }
        }
        if (chance(0.5)) {
            c.decal(3, 4, 1, RuinCell.of(RuinPart.LEAVES));
        }
    }

    /** The load spilled on the ground away from the trail: barrels rolled off, crates, hay, a chest. */
    private void spill() {
        String loot = "minecraft:chests/village/village_plains_house";
        int count = 3 + rnd.nextInt(4);
        for (int k = 0; k < count; k++) {
            int u = rnd.nextInt(9) - 1;
            int v = 3 + rnd.nextInt(3);
            double r = rnd.nextDouble();
            int cell = r < 0.4 ? RuinCell.of(RuinPart.BARREL, rnd.nextInt(4)) : r < 0.65 ? RuinCell.of(RuinPart.COMPOSTER)
                : r < 0.85 ? RuinCell.withAxis(RuinCell.of(RuinPart.HAY), rnd.nextInt(2) + 1)
                : RuinCell.of(RuinPart.DECORATED_POT, rnd.nextInt(4)) | RuinCell.FLAG;
            c.decal(u, v, 1, cell);
        }
        groundContainer(7, 4, RuinCell.WEST, chance(0.5), chance(0.7) ? site.lootTable : loot);
    }

    // =========================================================================================
    // Shrines
    // =========================================================================================

    private void shrine() {
        nameBoard = 0.0;
        drawFootpath = chance(0.7);
        switch (site.variant) {
            case ROOFED_POST -> roofedPost();
            case CAIRN -> cairn();
            default -> niche();
        }
        offerings();
        weather(-1, -1, 3, 3, 0, 6, 0);
    }

    /** A stone niche: a back wall, two cheeks and a slab roof sheltering a lantern on a pedestal. */
    private void niche() {
        for (int u = 0; u <= 2; u++) {
            c.setFill(u, 2, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 0, 2, RuinCell.of(RuinPart.FOUNDATION));
            for (int y = 1; y <= 3; y++) {
                c.set(u, y, 2, RuinCell.of(RuinPart.MASONRY));
            }
            c.set(u, 4, 2, u == 1 ? RuinCell.of(RuinPart.MASONRY) : RuinCell.of(RuinPart.MASONRY_SLAB));
        }
        for (int u : new int[]{0, 2}) {
            c.setFill(u, 1, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 0, 1, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 1, 1, RuinCell.of(RuinPart.MASONRY));
            c.set(u, 2, 1, RuinCell.of(RuinPart.MASONRY_WALL));
        }
        c.setFill(1, 1, RuinCell.of(RuinPart.FOUNDATION));
        c.set(1, 0, 1, RuinCell.of(RuinPart.FOUNDATION));
        c.set(1, 1, 1, RuinCell.of(RuinPart.MASONRY_SLAB) | RuinCell.TOP);
        for (int u = 0; u <= 2; u++) {
            if (u == 1 || n2(u + 30.0, 2.0) + 0.6 > decay) {
                c.set(u, 3, 1, RuinCell.of(RuinPart.MASONRY_SLAB) | RuinCell.TOP);
            }
        }
        double r = rnd.nextDouble();
        if (r < 0.45) {
            c.set(1, 2, 1, RuinCell.of(RuinPart.LANTERN));
        } else if (r < 0.8) {
            c.set(1, 2, 1, RuinCell.withAux(RuinCell.of(RuinPart.CANDLE), 1 + rnd.nextInt(3)) | (chance(0.1) ? RuinCell.FLAG : 0));
        } else {
            c.set(1, 2, 1, RuinCell.of(RuinPart.DECORATED_POT, RuinCell.NORTH));
        }
        if (decay > 0.5 && chance(0.6)) {
            c.set(1, 2, 0, RuinCell.of(RuinPart.COBWEB) | RuinCell.SOFT);
        }
    }

    /** A post with a little gable roof and a shelf for candles, a lantern hanging under the eave. */
    private void roofedPost() {
        c.setFill(1, 1, RuinCell.of(RuinPart.FOUNDATION));
        c.set(1, 0, 1, RuinCell.of(RuinPart.FOUNDATION));
        for (int y = 1; y <= 3; y++) {
            c.set(1, y, 1, RuinCell.of(RuinPart.LOG));
        }
        boolean roofKept = decay < 0.8 || chance(0.5);
        if (roofKept) {
            c.set(1, 4, 1, RuinCell.of(RuinPart.PLANKS));
            c.set(0, 4, 1, RuinCell.of(RuinPart.ROOF_STAIR, RuinCell.EAST));
            c.set(2, 4, 1, RuinCell.of(RuinPart.ROOF_STAIR, RuinCell.WEST));
            c.set(1, 5, 1, RuinCell.of(RuinPart.ROOF_SLAB));
            c.set(1, 4, 0, RuinCell.of(RuinPart.PLANK_SLAB) | RuinCell.TOP);
            if (chance(0.6)) {
                c.set(1, 3, 0, RuinCell.of(RuinPart.LANTERN) | RuinCell.FLAG);
            }
        } else {
            c.decal(2, 3, 1, RuinCell.of(RuinPart.ROOF_STAIR, rnd.nextInt(4)));
        }
        // the shelf on the front of the post
        c.set(1, 2, 0, RuinCell.of(RuinPart.PLANK_SLAB) | RuinCell.TOP);
        if (chance(0.7) && c.get(1, 3, 0) == 0) {
            c.set(1, 3, 0, RuinCell.withAux(RuinCell.of(RuinPart.CANDLE), rnd.nextInt(4)) | (chance(0.1) ? RuinCell.FLAG : 0));
        }
    }

    /** A cairn: stones stacked into a cone, candles in the gaps, loose stones around. */
    private void cairn() {
        for (int u = 0; u <= 2; u++) {
            for (int v = 0; v <= 2; v++) {
                boolean centre = u == 1 && v == 1;
                boolean edge = !centre && (u == 1 || v == 1);
                c.setFill(u, v, RuinCell.of(RuinPart.FOUNDATION));
                c.set(u, 0, v, RuinCell.of(RuinPart.FOUNDATION));
                if (centre || edge) {
                    c.set(u, 1, v, RuinCell.of(RuinPart.MASONRY));
                } else if (chance(0.7)) {
                    c.set(u, 1, v, RuinCell.of(RuinPart.MASONRY_SLAB));
                }
            }
        }
        c.set(1, 2, 1, RuinCell.of(RuinPart.MASONRY));
        c.set(1, 3, 1, RuinCell.of(RuinPart.MASONRY_WALL));
        if (chance(0.5)) {
            c.set(1, 4, 1, RuinCell.withAux(RuinCell.of(RuinPart.ROCK, rnd.nextInt(4)), 0));
        }
        int[][] around = {{1, 0}, {0, 1}, {2, 1}, {1, 2}};
        for (int[] a : around) {
            if (chance(0.45) && c.empty(a[0], 2, a[1])) {
                c.set(a[0], 2, a[1], RuinCell.withAux(RuinCell.of(RuinPart.CANDLE), rnd.nextInt(3)));
            }
        }
        rubbleField(-2, -1, 4, 4, 0.18);
    }

    /** Flower pots, candles and flowers at the foot of a shrine. */
    private void offerings() {
        int[][] spots = {{0, -1}, {2, -1}, {-1, 0}, {3, 0}, {1, -1}};
        for (int[] s : spots) {
            double r = rnd.nextDouble();
            if (r < 0.3) {
                c.decal(s[0], s[1], 1, RuinCell.withAux(RuinCell.of(RuinPart.FLOWER_POT), rnd.nextInt(3)));
            } else if (r < 0.45) {
                c.decal(s[0], s[1], 1, RuinCell.withAux(RuinCell.of(RuinPart.CANDLE), rnd.nextInt(2)));
            } else if (r < 0.7 && !hot) {
                c.decal(s[0], s[1], 1, RuinCell.withAux(RuinCell.of(RuinPart.FLOWER), rnd.nextInt(6)));
            } else if (r < 0.78) {
                c.decal(s[0], s[1], 1, RuinCell.of(RuinPart.DECORATED_POT, rnd.nextInt(4)) | (chance(0.5) ? RuinCell.FLAG : 0));
            }
        }
        meadow(-2, -1, 4, 4, 0.2);
    }

    // =========================================================================================
    // Campsites
    // =========================================================================================

    private void campsite() {
        nameBoard = 0.0;
        drawFootpath = chance(0.5);
        int fu = 4;
        int fv = 3;
        // the trodden ground of the camp
        for (int u = 1; u <= 7; u++) {
            for (int v = 0; v <= 6; v++) {
                if (Math.hypot(u - fu, v - fv) < 3.2 && n2(u * 0.6 + 12.0, v * 0.6) > -0.2) {
                    c.decal(u, v, 0, RuinCell.of(RuinPart.COARSE_DIRT));
                }
            }
        }
        reserveRect(fu - 1, fv - 1, fu + 1, fv + 1);
        ring(fu, fv, chance(0.06));
        logSeat(fu - 1, fv - 2, RuinCell.AXIS_X);
        if (chance(0.7)) {
            logSeat(fu - 3, fv - 1, RuinCell.AXIS_Z);
        }
        if (chance(0.5)) {
            logSeat(fu + 2, fv - 1, RuinCell.AXIS_Z);
        }
        switch (site.variant) {
            case LEAN_TO -> leanTo(fu - 2, fv + 2);
            case HUNTERS -> {
                dryingRack(0, fv + 3);
                tent(fu + 1, fv + 2, true);
            }
            case FALLEN -> tent(fu - 1, fv + 2, true);
            default -> tent(fu - 1, fv + 2, false);
        }
        // the supplies: a barrel or crate, a woodpile, an axe block
        groundContainer(fu + 4, fv, RuinCell.WEST, chance(0.6), chance(0.6) ? site.lootTable : null);
        if (chance(0.5)) {
            c.decal(fu + 4, fv + 1, 1, RuinCell.of(RuinPart.COMPOSTER));
        }
        if (chance(0.6)) {
            woodpile(fu - 4, fv + 1, 2);
        }
        if (chance(0.4)) {
            c.decal(fu + 3, fv - 2, 1, RuinCell.of(RuinPart.LOG));
        }
        meadow(-2, -2, 11, 10, 0.18);
    }

    private void reserveRect(int u0, int v0, int u1, int v1) {
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                reserve(u, v);
            }
        }
    }

    /**
     * A tattered ridge tent: two low canvas walls and a canvas roof on poles, three wide and four
     * long, holes torn in it; a bedroll inside. A fallen tent lies flat as cloth on the ground,
     * one pole still standing.
     */
    private void tent(int u0, int v0, boolean fallen) {
        int colour = rnd.nextInt(4);
        int len = 4;
        if (fallen) {
            for (int u = u0; u <= u0 + 2; u++) {
                for (int v = v0; v < v0 + len; v++) {
                    if (n2(u * 0.9 + 81.0, v * 0.9) > -0.35) {
                        c.decal(u, v, 1, RuinCell.withAux(RuinCell.of(RuinPart.CARPET), colour == 2 ? 1 : 0));
                    }
                }
            }
            c.decal(u0 + 1, v0 - 1, 1, RuinCell.of(RuinPart.FENCE));
            c.decal(u0 + 1, v0 - 1, 2, RuinCell.of(RuinPart.FENCE));
            c.decal(u0 + 3, v0 + 1, 1, RuinCell.withAxis(RuinCell.of(RuinPart.STRIPPED_LOG), RuinCell.AXIS_Z));
            return;
        }
        for (int u = u0; u <= u0 + 2; u++) {
            for (int v = v0 - 1; v <= v0 + len; v++) {
                c.set(u, 0, v, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 1));
                c.setFill(u, v, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 3));
            }
        }
        // poles at both ends carry the ridge
        for (int v : new int[]{v0 - 1, v0 + len}) {
            c.set(u0 + 1, 1, v, RuinCell.of(RuinPart.FENCE));
            c.set(u0 + 1, 2, v, RuinCell.of(RuinPart.FENCE));
        }
        for (int v = v0; v < v0 + len; v++) {
            for (int[] p : new int[][]{{0, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}}) {
                int u = u0 + p[0];
                int y = p[1];
                boolean torn = n2(u * 1.1 + 47.0, v * 1.1 + y) + 0.55 < decay;
                if (!torn) {
                    c.set(u, y, v, RuinCell.withAux(RuinCell.of(RuinPart.WOOL), colour));
                }
            }
            c.set(u0 + 1, 1, v, RuinCell.of(RuinPart.AIR));
        }
        // the open front end and a bedroll inside
        c.set(u0 + 1, 1, v0 + len - 1, RuinCell.withAux(RuinCell.of(RuinPart.CARPET), 1 + rnd.nextInt(2)));
        if (chance(0.4)) {
            c.set(u0 + 1, 1, v0 + 1, RuinCell.of(RuinPart.BARREL, RuinCell.NORTH) | RuinCell.TOP);
        }
    }

    /** A lean-to shelter: a back wall of logs, short sides, posts at the open front and a slab roof. */
    private void leanTo(int u0, int v0) {
        int w = 4;
        for (int u = u0; u < u0 + w; u++) {
            c.setFill(u, v0 + 2, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 0, v0 + 2, RuinCell.of(RuinPart.FOUNDATION));
            for (int y = 1; y <= 2; y++) {
                if (y == 1 || !chance(decay * 0.5)) {
                    c.set(u, y, v0 + 2, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_X));
                }
            }
            c.set(u, 0, v0, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 1));
            c.set(u, 0, v0 + 1, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 1));
            c.setFill(u, v0, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 3));
            c.setFill(u, v0 + 1, RuinCell.withAux(RuinCell.of(RuinPart.SOIL), 3));
        }
        for (int u : new int[]{u0, u0 + w - 1}) {
            c.setFill(u, v0 + 1, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 0, v0 + 1, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 1, v0 + 1, RuinCell.withAxis(RuinCell.of(RuinPart.LOG), RuinCell.AXIS_Z));
            c.setFill(u, v0, RuinCell.of(RuinPart.FOUNDATION));
            c.set(u, 0, v0, RuinCell.of(RuinPart.FOUNDATION));
            for (int y = 1; y <= 3; y++) {
                c.set(u, y, v0, RuinCell.of(RuinPart.FENCE));
            }
        }
        for (int u = u0 - 1; u <= u0 + w; u++) {
            boolean keep = n2(u * 0.6, 3.0) + decay <= 1.05;
            if (!keep && u > u0 && u < u0 + w - 1) {
                continue;
            }
            c.set(u, 4, v0, RuinCell.of(RuinPart.ROOF_SLAB));
            c.set(u, 3, v0 + 1, RuinCell.of(RuinPart.ROOF_SLAB) | RuinCell.TOP);
            c.set(u, 3, v0 + 2, RuinCell.of(RuinPart.ROOF_SLAB));
        }
        for (int u = u0 + 1; u < u0 + w - 1; u++) {
            for (int y = 1; y <= 3; y++) {
                c.add(u, y, v0, RuinCell.of(RuinPart.AIR));
                c.add(u, y, v0 + 1, RuinCell.of(RuinPart.AIR));
            }
        }
        if (chance(0.6)) {
            c.set(u0 + 1, 1, v0 + 1, RuinCell.of(RuinPart.HAY));
        }
        if (chance(0.5)) {
            c.set(u0 + 2, 1, v0 + 1, RuinCell.withAux(RuinCell.of(RuinPart.CARPET), 1));
        }
    }

    /** A drying rack: two posts and a rail, a few skins (brown cloth) folded on the ground beneath. */
    private void dryingRack(int u0, int v0) {
        for (int y = 1; y <= 2; y++) {
            c.decal(u0, v0, y, RuinCell.of(RuinPart.FENCE));
            c.decal(u0 + 2, v0, y, RuinCell.of(RuinPart.FENCE));
        }
        if (chance(0.7)) {
            c.decal(u0 + 1, v0, 1, RuinCell.withAux(RuinCell.of(RuinPart.CARPET), 1));
        }
        c.decal(u0 + 1, v0 + 1, 1, RuinCell.of(RuinPart.CAULDRON));
    }

    // =========================================================================================
    // Fields
    // =========================================================================================

    private void field() {
        int w = spec.w();
        int d = spec.d();
        int gate = spec.doorU();
        nameBoard = 0.0;
        for (int u = 0; u < w; u++) {
            for (int v = 0; v < d; v++) {
                boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
                if (edge) {
                    boundary(u, v, gate);
                } else {
                    furrow(u, v);
                }
            }
        }
        gatePiers(gate);
        // a scarecrow in the middle of the field, a forgotten bale in a corner
        int su = w / 2 + rnd.nextInt(5) - 2;
        int sv = d / 2 + rnd.nextInt(3) - 1;
        if (chance(0.7)) {
            c.decal(su, sv, 1, RuinCell.of(RuinPart.FENCE));
            c.decal(su, sv, 2, RuinCell.of(RuinPart.FENCE));
            if (chance(0.8)) {
                c.decal(su, sv, 3, RuinCell.of(RuinPart.CARVED_PUMPKIN, RuinCell.NORTH));
            }
        }
        if (chance(0.6)) {
            c.decal(w - 3, d - 3, 1, RuinCell.withAxis(RuinCell.of(RuinPart.HAY), rnd.nextInt(3)));
        }
        if (chance(0.3)) {
            buried(2 + rnd.nextInt(w - 4), 2 + rnd.nextInt(d - 4));
        }
    }

    /** One cell of the field's boundary: wall, rails or hedge, with gaps where it fell. */
    private void boundary(int u, int v, int gate) {
        if (v == 0 && Math.abs(u - gate) <= 1) {
            return;
        }
        double n = n2(u * 0.28 + 3.0, v * 0.28 + 17.0);
        boolean stands = n > decay - 0.55;
        boolean low = n < decay - 0.2;
        switch (site.variant) {
            case RAILS -> {
                boolean post = (u + v) % 3 == 0;
                if (stands || post && n > decay - 0.8) {
                    c.decal(u, v, 1, RuinCell.of(RuinPart.FENCE));
                } else if (chance(0.3)) {
                    c.decal(u, v, 1, RuinCell.withAxis(RuinCell.of(RuinPart.STRIPPED_LOG), v == 0 || v == spec.d() - 1 ? RuinCell.AXIS_X
                        : RuinCell.AXIS_Z));
                }
            }
            case HEDGEROW -> {
                if (stands) {
                    c.decal(u, v, 1, RuinCell.of(RuinPart.LEAVES));
                    if (!low && n2(u * 0.5 + 9.0, v * 0.5) > -0.2) {
                        c.decal(u, v, 2, RuinCell.of(RuinPart.LEAVES));
                    }
                } else if (chance(0.4)) {
                    c.decal(u, v, 1, RuinCell.of(RuinPart.SWEET_BERRY_BUSH));
                }
            }
            case GATEWAY -> {
                // only stubs of the wall reach out from the gate piers
                if (v == 0 && Math.abs(u - gate) <= 5 && stands) {
                    c.decal(u, v, 1, RuinCell.of(RuinPart.MASONRY));
                    if (!low && Math.abs(u - gate) <= 3) {
                        c.decal(u, v, 2, RuinCell.of(RuinPart.MASONRY_WALL));
                    }
                } else if (chance(0.12)) {
                    c.decal(u, v, 1, RuinCell.withAux(RuinCell.of(RuinPart.ROCK, rnd.nextInt(4)), wet > 0.6 ? 1 : 0));
                }
            }
            default -> {
                if (stands) {
                    c.decal(u, v, 1, RuinCell.of(RuinPart.MASONRY));
                    if (!low) {
                        c.decal(u, v, 2, RuinCell.of(RuinPart.MASONRY_WALL));
                    }
                } else if (chance(0.5)) {
                    // the wall slumped into a line of loose stones
                    c.decal(u, v, 1, chance(0.5) ? RuinCell.of(RuinPart.MASONRY_SLAB)
                        : RuinCell.withAux(RuinCell.of(RuinPart.ROCK, rnd.nextInt(4)), wet > 0.6 ? 1 : 0));
                }
            }
        }
    }

    /** The gate: stone piers (wooden posts for rails), a gate hanging open or lying in the grass, an arch over a gateway. */
    private void gatePiers(int gate) {
        boolean wood = site.variant == RAILS;
        int pierH = site.variant == GATEWAY ? 4 : 3;
        for (int u : new int[]{gate - 2, gate + 2}) {
            for (int y = 1; y <= pierH; y++) {
                c.decal(u, 0, y, wood ? RuinCell.of(RuinPart.FENCE) : RuinCell.of(RuinPart.MASONRY));
            }
            if (!wood) {
                c.decal(u, 0, pierH + 1, RuinCell.of(RuinPart.MASONRY_SLAB));
            }
        }
        if (site.variant == GATEWAY) {
            // the arch fell: its stones lie in the gateway
            rubbleField(gate - 1, -1, gate + 1, 2, 0.4);
        } else if (chance(0.5)) {
            c.decal(gate - 1, 0, 1, RuinCell.of(RuinPart.FENCE_GATE, RuinCell.NORTH) | RuinCell.FLAG);
        } else {
            c.decal(gate + 1, 1, 1, RuinCell.of(RuinPart.TRAPDOOR, RuinCell.NORTH));
        }
    }

    /** The field: old furrows in rows, gone back to grass, flowers and berry bushes. */
    private void furrow(int u, int v) {
        if (v % 2 == 0 && n2(u * 0.3 + 70.0, v * 0.3) > -0.3) {
            c.decal(u, v, 0, RuinCell.of(RuinPart.COARSE_DIRT));
            if (chance(0.1) && !hot) {
                c.decal(u, v, 1, RuinCell.of(RuinPart.SWEET_BERRY_BUSH));
            }
            return;
        }
        double r = rnd.nextDouble();
        if (hot) {
            if (r < 0.05) {
                c.decal(u, v, 1, RuinCell.of(RuinPart.DEAD_BUSH));
            }
        } else if (r < 0.2) {
            c.decal(u, v, 1, RuinCell.of(RuinPart.SHORT_GRASS));
        } else if (r < 0.26) {
            c.decal(u, v, 1, RuinCell.withAux(RuinCell.of(RuinPart.FLOWER), rnd.nextInt(6)));
        }
    }
}
