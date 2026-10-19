package com.terrahaul.worldgen.ruins;

import com.terrahaul.worldgen.noise.SeedMixer;

/**
 * Old lamp posts along a trail. Each stretch has one style, chosen by climate:
 * <ul>
 *   <li>{@link LightStretch#LAMP_ARM}: a stone footing, a fence post and an arm from which a
 *       lantern hangs over the path edge at head height;</li>
 *   <li>{@link LightStretch#CHAIN_LAMP}: a taller post, the lantern on a chain (cold and upland);</li>
 *   <li>{@link LightStretch#STONE_LANTERN}: a stone-brick pedestal, a lantern and a slab cap;</li>
 *   <li>{@link LightStretch#POST_TOP}: a short post with the lantern on top (dry country);</li>
 *   <li>{@link LightStretch#BAMBOO}: a bamboo pole with a lantern on top (jungle).</li>
 * </ul>
 * Most posts are neglected: the lantern is gone, lies on the ground or is wrapped in cobwebs,
 * the post is bent at a joint or has fallen over, the footing is mossy. At least a third of every
 * stretch still carries its lantern. A truly leaning post cannot be drawn with blocks without
 * floating diagonal steps, so "leaning" is drawn as a post bent at a joint.
 */
public final class LampPostPlanner {
    static final int INTACT = 0;
    static final int BARE = 1;
    static final int FALLEN_LANTERN = 2;
    static final int WEBBED = 3;
    static final int BENT = 4;
    static final int FALLEN_POST = 5;
    public static final String[] STATE_NAMES = {"intact", "bare", "fallen_lantern", "webbed", "bent", "fallen_post"};

    private LampPostPlanner() {
    }

    /** Condition of post {@code i} of a stretch. */
    public static int state(LightStretch s, int i) {
        long h = SeedMixer.hash2(s.seed, i, 77);
        double r = SeedMixer.unit(h);
        double intact = Math.max(0.15, 0.6 - 0.5 * s.decay);
        // keep at least every third post lit
        if (i % 3 == 1 || r < intact) {
            return INTACT;
        }
        double t = (r - intact) / (1.0 - intact);
        if (t < 0.32) {
            return BARE;
        }
        if (t < 0.46) {
            return FALLEN_LANTERN;
        }
        if (t < 0.6) {
            return WEBBED;
        }
        if (t < 0.75 && s.style != LightStretch.STONE_LANTERN) {
            return BENT;
        }
        return s.style == LightStretch.STONE_LANTERN ? BARE : FALLEN_POST;
    }

    /** Post {@code i} as a grouped plan around its column; heights are relative to the ground there. */
    public static RuinPlan plan(LightStretch s, int i) {
        int[] post = s.posts[i];
        int x = post[0];
        int z = post[1];
        int n = post[2];
        int along = RuinCell.clockwise(n);
        long h = SeedMixer.hash2(s.seed, i, 78);
        int state = state(s, i);
        RuinMaterials m = s.materials;
        if (s.style == LightStretch.STONE_LANTERN) {
            m = new RuinMaterials(RuinMaterials.ASHLAR, m.wood, m.roofWood, m.roofCover, m.leaves);
        } else if (s.style == LightStretch.BAMBOO) {
            m = new RuinMaterials(m.stone, RuinMaterials.BAMBOO, m.roofWood, m.roofCover, m.leaves);
        }
        RuinPlan p = new RuinPlan(RuinPlan.GROUPED, x - 3, -1, z - 3, 7, 9, 7, 0, m, false);
        p.anchorX = x;
        p.anchorZ = z;
        boolean mossy = s.wetness > 0.45 && s.decay > 0.35;
        int footing = RuinCell.withWeathering(RuinCell.of(RuinPart.FOUNDATION), mossy ? RuinCell.MOSSY : RuinCell.PLAIN);
        int fence = RuinCell.of(RuinPart.FENCE);
        int lantern = RuinCell.withAux(RuinCell.of(RuinPart.LANTERN), s.soulLanterns ? 1 : 0);
        int hanging = lantern | RuinCell.FLAG;
        int nx = RuinCell.DX[n];
        int nz = RuinCell.DZ[n];
        int ax = RuinCell.DX[along];
        int az = RuinCell.DZ[along];
        // where the lantern would be, as {x, y, z, hanging}
        int[] lamp;
        switch (s.style) {
            case LightStretch.CHAIN_LAMP, LightStretch.LAMP_ARM -> {
                boolean chain = s.style == LightStretch.CHAIN_LAMP;
                int top = chain ? 5 : 4;
                p.set(x, 1, z, footing);
                if (state == FALLEN_POST) {
                    p.set(x, 2, z, fence);
                    fallen(p, x, z, -nx, -nz, 3 + (chain ? 1 : 0), lantern, h);
                    break;
                }
                int px = x;
                int pz = z;
                for (int y = 2; y <= top; y++) {
                    if (state == BENT && y == top - 1) {
                        // the post gave way at a joint: it jogs sideways and carries on
                        p.set(px + ax, y, pz + az, fence);
                        px += ax;
                        pz += az;
                    }
                    p.set(px, y, pz, fence);
                }
                p.set(px + nx, top, pz + nz, fence);
                if (chain) {
                    p.set(px + nx, top - 1, pz + nz, RuinCell.of(RuinPart.CHAIN));
                    lamp = new int[]{px + nx, top - 2, pz + nz, 1};
                } else {
                    lamp = new int[]{px + nx, top - 1, pz + nz, 1};
                }
                lantern(p, s, state, lamp, lantern, hanging, x, z, nx, nz, h);
            }
            case LightStretch.STONE_LANTERN -> {
                p.set(x, 1, z, RuinCell.withWeathering(RuinCell.of(RuinPart.MASONRY), mossy ? RuinCell.MOSSY : RuinCell.PLAIN));
                p.set(x, 2, z, RuinCell.withWeathering(RuinCell.of(RuinPart.MASONRY_WALL), mossy ? RuinCell.MOSSY : RuinCell.PLAIN));
                lamp = new int[]{x, 3, z, 0};
                lantern(p, s, state, lamp, lantern, hanging, x, z, nx, nz, h);
                if (state != FALLEN_LANTERN || SeedMixer.unit(SeedMixer.mix(h)) < 0.5) {
                    p.set(x, 4, z, RuinCell.of(RuinPart.MASONRY_SLAB));
                }
            }
            default -> {
                // post-top lamps: a short wooden post, or a bamboo pole
                int top = s.style == LightStretch.BAMBOO ? 3 : 2;
                if (state == FALLEN_POST) {
                    p.set(x, 1, z, fence);
                    fallen(p, x, z, -nx, -nz, top - 1, lantern, h);
                    break;
                }
                int px = x;
                int pz = z;
                for (int y = 1; y <= top; y++) {
                    if (state == BENT && y == top && top >= 3) {
                        p.set(px + ax, y - 1, pz + az, fence);
                        px += ax;
                        pz += az;
                    }
                    p.set(px, y, pz, fence);
                }
                lamp = new int[]{px, top + 1, pz, 0};
                lantern(p, s, state, lamp, lantern, hanging, x, z, nx, nz, h);
            }
        }
        if (mossy) {
            for (int d = 0; d < 4; d++) {
                if (d != n && SeedMixer.unit(SeedMixer.mix(h + 20 + d)) < 0.4) {
                    p.decals.add(new RuinPlan.Decal(x + RuinCell.DX[d], z + RuinCell.DZ[d], 1, RuinCell.of(RuinPart.MOSS_CARPET)));
                }
            }
        }
        return p;
    }

    /** The lantern of a standing post, or what is left of it. */
    private static void lantern(RuinPlan p, LightStretch s, int state, int[] lamp, int lantern, int hanging, int x, int z, int nx, int nz, long h) {
        int cell = lamp[3] == 1 ? hanging : lantern;
        switch (state) {
            case INTACT, BENT -> p.set(lamp[0], lamp[1], lamp[2], cell);
            case WEBBED -> {
                if (SeedMixer.unit(SeedMixer.mix(h + 7)) < 0.5) {
                    p.set(lamp[0], lamp[1], lamp[2], RuinCell.of(RuinPart.COBWEB));
                } else {
                    p.set(lamp[0], lamp[1], lamp[2], cell);
                    p.set(lamp[0] - nx, lamp[1], lamp[2] - nz, RuinCell.of(RuinPart.COBWEB) | RuinCell.SOFT);
                }
            }
            case FALLEN_LANTERN -> {
                // it fell and lies beside the post (or the path edge under the arm)
                int side = SeedMixer.unit(SeedMixer.mix(h + 9)) < 0.5 ? 1 : -1;
                int along = RuinCell.clockwise(RuinCell.dirOf(nx, nz));
                int fx = lamp[0] + side * RuinCell.DX[along];
                int fz = lamp[2] + side * RuinCell.DZ[along];
                p.decals.add(new RuinPlan.Decal(fx, fz, 1, lantern));
            }
            default -> {
            }
        }
    }

    /** A post that fell away from the trail: its pieces lie on the ground, the lantern at the end. */
    private static void fallen(RuinPlan p, int x, int z, int dx, int dz, int length, int lantern, long h) {
        int fence = RuinCell.of(RuinPart.FENCE);
        for (int k = 1; k <= length; k++) {
            p.decals.add(new RuinPlan.Decal(x + dx * k, z + dz * k, 1, fence));
        }
        if (SeedMixer.unit(SeedMixer.mix(h + 11)) < 0.5) {
            int side = RuinCell.clockwise(RuinCell.dirOf(dx, dz));
            p.decals.add(new RuinPlan.Decal(x + dx * length + RuinCell.DX[side], z + dz * length + RuinCell.DZ[side], 1, lantern));
        }
    }
}
