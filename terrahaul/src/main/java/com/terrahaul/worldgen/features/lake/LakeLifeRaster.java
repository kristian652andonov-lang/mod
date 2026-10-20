package com.terrahaul.worldgen.features.lake;

import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;

/**
 * What grows in and around a lake, per column. Pure Java.
 * <p>
 * The decision is made from the plan (see {@link LakePlanner}) and what the column's top actually
 * is: seagrass roots in sand, mud and clay, thins out on gravel and only finds a foothold here and
 * there in the sediment between bare stones; weed (kelp) holds on to rock, wood and gravel best;
 * dripleaf needs mud, clay, moss or soil. The feature passes the real top block it finds in the
 * world, the devtools the planned one (the surface rule's bed where nothing changed it).
 * <ul>
 *   <li>under water: meadows of short and tall seagrass with ragged edges, weed beds whose tallest
 *       stems reach the surface, weed along the tops of sunken trunks, dripleaf in lush
 *       shallows;</li>
 *   <li>on the water: lily pads in colonies over calm water up to four blocks deep, and moss
 *       carpets on the trunks that lie awash in the shallows of humid country;</li>
 *   <li>on the shore: reeds (sugar cane) in clumps where the ground meets the water, moss carpets
 *       on the humid banks.</li>
 * </ul>
 */
public final class LakeLifeRaster {
    /** Kinds of the top block of a column. */
    public static final int SOFT = 0;
    public static final int GRAVELLY = 1;
    public static final int HARD = 2;
    /** Mud, clay, moss and soil: where dripleaf roots. */
    public static final int MUDDY = 3;
    public static final int WOOD = 4;

    public static final int NONE = 0;
    public static final int SEAGRASS = 1;
    public static final int TALL_SEAGRASS = 2;
    public static final int KELP = 3;
    public static final int SMALL_DRIPLEAF = 4;
    public static final int BIG_DRIPLEAF = 5;
    public static final int REEDS = 6;
    public static final int MOSS_CARPET = 7;

    private LakeLifeRaster() {
    }

    /** What grows on one column. */
    public static final class Plant {
        public int kind;
        /** Y of the kelp head, or the number of reed blocks. */
        public int head;
        /** Whether the kelp head has stopped growing. */
        public boolean mature;
        /** Growth age (0-25) of a kelp head that may still grow. */
        public int age;
        /** A lily pad floats over the column. */
        public boolean lily;
        /** Facing (0-3) of dripleaf. */
        public int facing;
    }

    /**
     * Decides the plants of a lake column.
     *
     * @param top the top solid block of the column (the bed, or a rock or log on it)
     * @param floor the kind of that block
     */
    public static Plant decide(LakePlanner p, LakeColumn c, int top, int floor, Plant out) {
        out.kind = NONE;
        out.lily = false;
        int water = c.surf - top;
        if (!c.wet || water < 0) {
            return out;
        }
        long h = SeedMixer.hash2(p.salt(60), c.x, c.z);
        double r = SeedMixer.unit(h);
        if (water == 0) {
            // a trunk lying awash in the shallows: moss takes hold on its dry top in humid country
            if (floor == WOOD && r < 0.7 * p.shoreMoss(c.x, c.z, c.temperature, c.humidity + 0.2)) {
                out.kind = MOSS_CARPET;
            }
            return out;
        }
        out.facing = (int) (SeedMixer.cellUnit(h, 0, 0, 7) * 4.0);
        // dripleaf in the lush shallows, its big leaves held just above the water
        if (floor == MUDDY && water <= 2 && c.lush > 0.05 && r < 0.45 * smoothstep(0.05, 0.6, c.lush)) {
            out.kind = SeedMixer.cellUnit(h, 0, 0, 1) < 0.45 ? BIG_DRIPLEAF : SMALL_DRIPLEAF;
            return out;
        }
        if (water >= 4 && c.weed > 0.0) {
            double clump = 0.4 + 0.5 * smoothstep(-0.2, 0.5, p.noise(LakePlanner.CLUMP, c.x / 5.0, c.z / 5.0));
            double hold = floor == HARD ? 1.3 : floor == WOOD ? 1.2 : floor == GRAVELLY ? 1.0 : 0.75;
            if (r < 0.3 * c.weed * clump * hold) {
                out.kind = KELP;
                double u = SeedMixer.cellUnit(h, 0, 0, 2);
                if (c.weed > 0.45 && u < 0.35 * c.weed) {
                    // the tallest stems reach the surface and stop growing there
                    out.head = c.surf - (SeedMixer.cellUnit(h, 0, 0, 3) < 0.5 ? 0 : 1);
                    out.mature = true;
                    out.age = 25;
                } else if (water > 14 && u < 0.15) {
                    // in a deep bed some stems reach up to just under the surface, so the bed
                    // shows from a boat as dark stands in the open water
                    out.head = c.surf - 1 - (int) (SeedMixer.cellUnit(h, 0, 0, 3) * 3.0);
                    out.mature = true;
                    out.age = 25;
                } else {
                    // the others stop at different heights; in deep water they reach far up
                    // towards the light, so a deep bed shows from the surface
                    double reach = water > 14 ? 0.45 + 0.4 * u : (0.25 + 0.55 * c.weed) * (0.6 + 0.4 * u);
                    int length = Math.max(2, (int) Math.round((water - 1) * reach));
                    out.head = Math.min(c.surf - 2, top + length);
                    out.mature = false;
                    out.age = 20 + (int) (SeedMixer.cellUnit(h, 0, 0, 4) * 5.0);
                }
                if (c.temperature < -0.4) {
                    // under winter ice the weed stays below the frozen surface
                    out.head = Math.min(out.head, c.surf - 2);
                }
                if (out.head <= top) {
                    out.kind = NONE;
                } else {
                    lily(c, h, out);
                    return out;
                }
            }
        }
        // how well the meadow roots in the bed; the curve keeps small strengths as sparse tufts
        double m = c.meadow * (floor == GRAVELLY ? 0.6 : floor == HARD ? 0.25 : floor == WOOD ? 0.0 : 1.0);
        double chance = 0.92 * smoothstep(0.0, 0.5, m);
        if (floor == WOOD && c.temperature > -0.5 && water <= 14) {
            // weed grows along the tops of sunken trunks, in stretches
            chance = 0.55 * smoothstep(-0.1, 0.3, p.noise(LakePlanner.MOSS, c.x / 6.0, c.z / 6.0));
        } else if ((floor == SOFT || floor == MUDDY) && water <= 6 && c.temperature > -0.45 && c.meadow > 0.0) {
            chance += 0.03;
        }
        double r2 = SeedMixer.cellUnit(h, 0, 0, 5);
        if (r2 < chance) {
            // tall grass where the meadow is thick, and most of it in the deeper beds (tape grass)
            double tall = Math.min(1.0, 0.4 * m / Math.max(1.0E-6, chance) + 0.45 * smoothstep(4.0, 9.0, water));
            out.kind = r2 < tall * chance && water >= 2 ? TALL_SEAGRASS : SEAGRASS;
        }
        lily(c, h, out);
        return out;
    }

    private static void lily(LakeColumn c, long h, Plant out) {
        if (c.lilies > 0.0 && (out.kind != KELP || out.head < c.surf)
            && SeedMixer.cellUnit(h, 0, 0, 6) < 0.8 * smoothstep(0.05, 0.6, c.lilies)) {
            out.lily = true;
        }
    }

    /**
     * Decides the plant of a dry shore column at the water's edge.
     *
     * @param type the lake type of the water beside it
     * @param grassy whether the ground is soil or grass (moss carpets need it; reeds also take sand)
     */
    public static Plant shore(LakePlanner p, LakeColumn c, int type, boolean grassy, Plant out) {
        out.kind = NONE;
        out.lily = false;
        long h = SeedMixer.hash2(p.salt(61), c.x, c.z);
        double r = SeedMixer.unit(h);
        double reeds = p.reeds(c.x, c.z, type, c.temperature);
        if (r < 0.65 * smoothstep(0.1, 0.55, reeds)) {
            out.kind = REEDS;
            out.head = 1 + (int) (SeedMixer.cellUnit(h, 0, 0, 1) * (c.temperature > 0.3 ? 3.0 : 2.2));
            return out;
        }
        if (grassy && SeedMixer.cellUnit(h, 0, 0, 2) < 0.5 * p.shoreMoss(c.x, c.z, c.temperature, c.humidity)) {
            out.kind = MOSS_CARPET;
        }
        return out;
    }

    /** The kind of a bed block given as a {@code LakeVolume} code, or -1 if nothing roots on it. */
    public static int kindOf(byte code) {
        if (code == LakeVolume.SAND) {
            return SOFT;
        }
        if (code == LakeVolume.GRAVEL) {
            return GRAVELLY;
        }
        if (code == LakeVolume.CLAY || code == LakeVolume.MUD || code == LakeVolume.MOSS) {
            return MUDDY;
        }
        if (code >= LakeVolume.LOG_X && code <= LakeVolume.PLANKS) {
            return WOOD;
        }
        if (code == LakeVolume.SLAB || code == LakeVolume.SLAB_TOP || code >= LakeVolume.STAIRS && code < LakeVolume.CHEST + 4
            || code == LakeVolume.BRICK_SLAB) {
            // not a full face to root on
            return -1;
        }
        return HARD;
    }

    /** The kind of an untouched bed: the surface rule's sediment (see {@link LakePlanner#naturalBed}). */
    public static int naturalBed(LakePlanner p, LakeColumn c) {
        return kindOf(p.naturalBed(c));
    }
}
