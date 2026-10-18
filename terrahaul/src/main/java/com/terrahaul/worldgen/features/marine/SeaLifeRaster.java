package com.terrahaul.worldgen.features.marine;

import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;

/**
 * Kelp forests and seagrass meadows on the sea floors outside coral reefs. Pure Java.
 * <p>
 * The decision is made per column from the plan (see {@link MarinePlanner}) and what the column's
 * top actually is, because kelp needs rock or cobble for its holdfast while seagrass roots in
 * sand and mud. The feature passes the real top block it finds in the world; the devtools pass
 * their planned one.
 * <ul>
 *   <li>Kelp forests: dense stands whose canopy reaches the surface, with a shorter understory
 *       that may still grow a little, thinning at the forest edge and broken by bare gaps.
 *       Outside the forests, a few scattered stalks.</li>
 *   <li>Seagrass meadows: a carpet of short and tall seagrass with ragged edges and bare blowouts;
 *       sparse tufts elsewhere on shallow sand.</li>
 * </ul>
 */
public final class SeaLifeRaster {
    /** Floor kinds, by the block at the top of the column. */
    public static final int SOFT = 0;
    public static final int GRAVELLY = 1;
    public static final int HARD = 2;

    public static final int NONE = 0;
    public static final int SEAGRASS = 1;
    public static final int TALL_SEAGRASS = 2;
    public static final int KELP = 3;

    private SeaLifeRaster() {
    }

    /** What grows on one column. */
    public static final class Plant {
        public int kind;
        /** Y of the kelp head (the top kelp block). */
        public int head;
        /** Whether the kelp head has stopped growing (canopy kelp at the surface). */
        public boolean mature;
        /** Growth age (0-25) of a kelp head that may still grow. */
        public int age;
    }

    /** True if the reef feature, not this one, decides the life of the column. */
    public static boolean reefColumn(MarineColumn c) {
        return c.reefObjects > 0.02;
    }

    /**
     * Decides the plant of a column.
     *
     * @param top the top solid block of the column (the floor, or a rock on it)
     * @param floor the kind of that block
     */
    public static Plant decide(MarinePlanner p, MarineColumn c, int top, int floor, Plant out) {
        out.kind = NONE;
        int water = p.surf - top;
        if (!c.sea || water < 1 || reefColumn(c) || c.trench > 0.2) {
            return out;
        }
        long h = SeedMixer.hash2(p.salt(50), c.x, c.z);
        double r = SeedMixer.unit(h);
        double k = c.kelp;
        if (water >= 3) {
            double clump = 0.45 + 0.5 * smoothstep(-0.2, 0.5, p.noise(MarinePlanner.CLUMP, c.x / 5.0, c.z / 5.0));
            double holdfast = floor == HARD ? 1.6 : floor == GRAVELLY ? 1.1 : 0.7;
            double chance = k * clump * holdfast;
            boolean sparse = k < 0.05 && c.temperature > -0.45 && c.temperature < 0.35 && water <= 40;
            if (sparse) {
                chance = floor == SOFT ? 0.004 : 0.012;
            }
            if (r < chance) {
                out.kind = KELP;
                if (!sparse && k >= 0.45 && SeedMixer.cellUnit(h, 0, 0, 1) < 0.3 + 0.6 * k) {
                    // canopy kelp reaches the surface and stops growing there
                    double u = SeedMixer.cellUnit(h, 0, 0, 2);
                    out.head = p.surf - (int) Math.floor(3.0 * u * u);
                    out.mature = true;
                    out.age = 25;
                } else {
                    double u = SeedMixer.cellUnit(h, 0, 0, 3);
                    int length = sparse ? 3 + (int) (7.0 * u) : Math.max(2, (int) Math.round((water - 1) * (0.3 + 0.7 * k) * (0.6 + 0.4 * u)));
                    out.head = Math.min(p.surf - 1, top + length);
                    out.mature = false;
                    out.age = 20 + (int) (SeedMixer.cellUnit(h, 0, 0, 4) * 5.0);
                }
                if (c.temperature < -0.4) {
                    // under sea ice the canopy stays below the frozen surface
                    out.head = Math.min(out.head, p.surf - 2);
                }
                if (out.head <= top) {
                    out.kind = NONE;
                }
                return out;
            }
        }
        if (floor == HARD || c.temperature < -0.5) {
            return out;
        }
        double m = c.meadow * (floor == GRAVELLY ? 0.4 : 1.0);
        double chance = k > 0.3 ? 0.12 * m : 0.95 * smoothstep(0.1, 0.6, m);
        if (c.temperature > -0.45 && water <= 20 && floor == SOFT) {
            chance += 0.02;
        }
        double r2 = SeedMixer.cellUnit(h, 0, 0, 5);
        if (r2 < chance) {
            out.kind = r2 < 0.35 * m && water >= 2 ? TALL_SEAGRASS : SEAGRASS;
        }
        return out;
    }

    /**
     * The kind of an untouched floor, approximating the surface rule's seabed (sand in shallow and
     * temperate water, gravel in cold water and with depth). The feature uses the real block.
     */
    public static int naturalFloor(MarineColumn c) {
        boolean cold = c.temperature < -0.2;
        if (c.depth < 14) {
            return cold ? GRAVELLY : SOFT;
        }
        double t = (c.depth - 14) / 56.0 + (cold ? 0.3 : 0.0) - (c.temperature > 0.5 ? 0.3 : 0.0);
        return t < 0.5 ? SOFT : GRAVELLY;
    }
}
