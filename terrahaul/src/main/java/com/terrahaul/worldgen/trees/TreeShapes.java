package com.terrahaul.worldgen.trees;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shape grammars for every species. Each generator receives a size class and draws the tree into a
 * {@link TreeBlueprint} using trunks, branches, leaf clusters and roots. Sizes are chosen so that
 * even giant specimens stay within {@link TreeBlueprint#MAX_REACH} of the trunk.
 */
public final class TreeShapes {
    private TreeShapes() {
    }

    public static void build(TreeBlueprint bp, TreeSize size) {
        if (size == TreeSize.ANCIENT) {
            switch (bp.species()) {
                case OAK -> ancientOak(bp, false);
                case SWAMP_OAK -> ancientOak(bp, true);
                case DARK_OAK -> ancientDarkOak(bp);
                case SPRUCE -> ancientConifer(bp, false);
                case PINE -> ancientConifer(bp, true);
                case JUNGLE -> ancientJungle(bp);
                case ACACIA -> ancientAcacia(bp);
                case CHERRY -> ancientCherry(bp);
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
            case DEAD, DEAD_SPRUCE, BURNED -> dead(bp, size);
        }
    }

    private static int range(RandomSource r, int min, int max) {
        return min + r.nextInt(max - min + 1);
    }

    private static double range(RandomSource r, double min, double max) {
        return min + r.nextDouble() * (max - min);
    }

    // =========================================================================================
    // Ancient trees: rare specimens about twice the size of a giant, with root systems to match
    // =========================================================================================

    /** A huge spreading oak: a trunk 5-6 blocks wide splitting into massive limbs. */
    private static void ancientOak(TreeBlueprint bp, boolean swamp) {
        RandomSource r = bp.random();
        double flat = swamp ? 0.7 : 1.0;
        int h = range(r, 13, 18);
        double tr = range(r, 2.0, 2.6);
        bp.taperedTrunk(h, tr, tr * 0.55);
        flare(bp, tr + 0.6, 2);
        int limbs = range(r, 6, 8);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < limbs; i++) {
            double a = a0 + i * Math.PI * 2 / limbs + range(r, -0.3, 0.3);
            double sy = range(r, h * 0.55, h);
            double len = range(r, 9.5, 13.5);
            double ex = Math.cos(a) * len;
            double ez = Math.sin(a) * len;
            double ey = sy + range(r, 3.0, 7.0);
            // limbs leave the trunk rising steeply, then level out
            double mx = Math.cos(a) * len * 0.4;
            double mz = Math.sin(a) * len * 0.4;
            bp.curvedBranch(0, sy, 0, mx, ey + 1.0, mz, ex, ey, ez, range(r, 1.0, 1.4));
            double br = range(r, 4.0, 5.0);
            bp.blob(ex, ey + 1.2, ez, br, br * 0.62 * flat, br, 0.9);
            for (int k = 0; k < 2; k++) {
                double t = range(r, 0.45, 0.75);
                double px = ex * t;
                double pz = ez * t;
                double py = sy + (ey - sy) * t + 1.0;
                double sa = a + (k == 0 ? 1 : -1) * range(r, 0.6, 1.0);
                double sl = range(r, 4.5, 6.5);
                double sx = px + Math.cos(sa) * sl;
                double sz = pz + Math.sin(sa) * sl;
                double sY = py + range(r, 1.5, 3.5);
                bp.branch(px, py, pz, sx, sY, sz, 0.5);
                double sbr = range(r, 3.4, 4.2);
                bp.blob(sx, sY + 0.8, sz, sbr, sbr * 0.65 * flat, sbr, 0.9);
            }
        }
        bp.blob(0, h + 4, 0, 6.0, 3.6 * flat, 6.0, 0.9);
        bp.rootSystem(range(r, 8, 10), tr, range(r, 9.0, 12.0), 3.5, 1.2);
    }

    /** A massive dark oak under a heavy dome 30 blocks across. */
    private static void ancientDarkOak(TreeBlueprint bp) {
        RandomSource r = bp.random();
        int h = range(r, 12, 17);
        double tr = range(r, 2.3, 2.9);
        bp.taperedTrunk(h, tr, tr * 0.6);
        flare(bp, tr + 0.7, 2);
        crown(bp, 0, h, range(r, 9, 11), 0.42, range(r, 9.0, 12.0), 0.5, 3.0, 1.0, range(r, 4.8, 5.6), 0.72);
        bp.blob(0, h + 2, 0, 7.0, 3.6, 7.0, 0.95);
        bp.rootSystem(range(r, 8, 11), tr, range(r, 9.0, 12.0), 3.0, 1.3);
    }

    /** Towering spruces and pines, 45-60 blocks tall, with trunks 4-5 blocks wide at the base. */
    private static void ancientConifer(TreeBlueprint bp, boolean pine) {
        RandomSource r = bp.random();
        int h = pine ? range(r, 42, 52) : range(r, 46, 60);
        double tr = range(r, 1.8, 2.4);
        bp.taperedTrunk(h - 1, tr, 0.0);
        flare(bp, tr + 0.5, 2);
        if (pine) {
            int crownStart = (int) (h * range(r, 0.66, 0.74));
            for (int y = (int) (h * 0.3); y < crownStart; y += 3) {
                if (r.nextFloat() < 0.5) {
                    double a = r.nextDouble() * Math.PI * 2;
                    bp.branch(0, y, 0, Math.cos(a) * (tr + 1.5), y + 0.5, Math.sin(a) * (tr + 1.5), 0);
                }
            }
            for (int i = 0; i < 9; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double sy = range(r, crownStart, h - 3.0);
                double len = range(r, 4.0, 7.0);
                double ex = Math.cos(a) * len;
                double ez = Math.sin(a) * len;
                double ey = sy + range(r, 0.5, 2.0);
                bp.branch(0, sy, 0, ex, ey, ez, 0.5);
                bp.blob(ex, ey + 0.4, ez, range(r, 3.6, 4.6), 2.0, range(r, 3.6, 4.6), 0.88);
            }
            bp.blob(0, h, 0, 5.0, 2.8, 5.0, 0.9);
        } else {
            double maxR = range(r, 8.0, 9.5);
            int start = (int) (h * 0.36);
            boolean whorl = true;
            for (int y = start; y <= h; y++) {
                double t = (double) (y - start) / Math.max(1, h - start);
                double radius = maxR * Math.pow(1.0 - t, 0.9) + 0.6;
                bp.disc(0, y, 0, whorl ? radius : radius * 0.55, whorl ? 0.92 : 0.8);
                if (whorl && radius > 3.6) {
                    int spokes = 5 + r.nextInt(3);
                    double s0 = r.nextDouble() * Math.PI;
                    for (int i = 0; i < spokes; i++) {
                        double a = s0 + i * Math.PI * 2 / spokes;
                        bp.branch(0, y, 0, Math.cos(a) * (radius - 2.0), y - 0.6, Math.sin(a) * (radius - 2.0), 0);
                    }
                }
                whorl = !whorl;
            }
            bp.leaf(0, h + 1, 0);
            bp.leaf(0, h + 2, 0);
        }
        bp.rootSystem(range(r, 7, 9), tr, range(r, 8.0, 11.0), 3.0, 1.0);
    }

    /** An emergent jungle giant with great plank buttresses and an umbrella canopy. */
    private static void ancientJungle(TreeBlueprint bp) {
        RandomSource r = bp.random();
        int h = range(r, 38, 48);
        double tr = range(r, 1.7, 2.2);
        bp.taperedTrunk(h - 1, tr, 1.0);
        bp.rootSystem(range(r, 6, 8), tr, range(r, 10.0, 13.0), range(r, 6.0, 8.0), 0.5);
        canopyPads(bp, 0, h, range(r, 5, 6), range(r, 9.0, 12.0), range(r, 5.8, 6.8), 1.0);
        canopyPads(bp, 0, (int) (h * 0.7), range(r, 3, 4), range(r, 6.0, 8.0), range(r, 4.2, 5.0), 0.9);
        bp.disc(0, h + 1, 0, 6.0, 0.9);
        bp.disc(0, h + 2, 0, 3.5, 0.85);
    }

    /** A broad, flat-topped acacia on a thick, short trunk, like an old savanna landmark. */
    private static void ancientAcacia(TreeBlueprint bp) {
        RandomSource r = bp.random();
        int straight = range(r, 5, 7);
        double tr = range(r, 1.4, 1.8);
        bp.taperedTrunk(straight, tr, tr * 0.7);
        int forks = range(r, 3, 4);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < forks; i++) {
            double a = a0 + i * Math.PI * 2 / forks + range(r, -0.4, 0.4);
            double len = range(r, 7.0, 10.0);
            double ex = Math.cos(a) * len;
            double ez = Math.sin(a) * len;
            double ey = straight + range(r, 5.0, 8.0);
            bp.curvedBranch(0, straight, 0, ex * 0.3, ey - 1.0, ez * 0.3, ex, ey, ez, 0.8);
            int py = Mth.floor(ey) + 1;
            double pr = range(r, 6.0, 7.5);
            bp.disc(ex, py, ez, pr, 0.95);
            bp.disc(ex, py + 1, ez, pr * 0.6, 0.9);
        }
        bp.rootSystem(range(r, 6, 8), tr, range(r, 8.0, 10.0), 2.0, 0.9);
    }

    /** A wide old cherry whose arching limbs carry clouds of blossom. */
    private static void ancientCherry(TreeBlueprint bp) {
        RandomSource r = bp.random();
        int h = range(r, 7, 10);
        double tr = range(r, 1.3, 1.7);
        bp.taperedTrunk(h, tr, tr * 0.6);
        int limbs = range(r, 5, 7);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < limbs; i++) {
            double a = a0 + i * Math.PI * 2 / limbs + range(r, -0.3, 0.3);
            double len = range(r, 9.0, 12.5);
            double ex = Math.cos(a) * len;
            double ez = Math.sin(a) * len;
            double sy = range(r, h * 0.6, h);
            double ey = sy + range(r, 2.0, 5.0);
            bp.curvedBranch(0, sy, 0, ex * 0.35, ey + 3.0, ez * 0.35, ex, ey, ez, 0.7);
            double br = range(r, 4.2, 5.2);
            bp.blob(ex, ey + 0.5, ez, br, br * 0.6, br, 0.92);
        }
        bp.blob(0, h + 4, 0, 5.5, 3.0, 5.5, 0.92);
        bp.rootSystem(range(r, 6, 8), tr, range(r, 7.0, 9.0), 1.8, 0.9);
    }

    // =========================================================================================
    // Oak: broad rounded crowns of several leaf clusters on spreading limbs
    // =========================================================================================

    private static void oak(TreeBlueprint bp, TreeSize size, boolean swamp) {
        RandomSource r = bp.random();
        double flat = swamp ? 0.65 : 1.0;
        switch (size) {
            case YOUNG -> {
                int h = range(r, 2, 4);
                bp.trunk(0, h - 1, 0);
                bp.blob(0, h, 0, range(r, 1.6, 2.2), 1.6 * flat, range(r, 1.6, 2.2), 0.95);
            }
            case SMALL -> {
                int h = range(r, 4, 6);
                bp.trunk(0, h - 1, 0);
                int branches = range(r, 2, 3);
                double a0 = r.nextDouble() * Math.PI * 2;
                for (int i = 0; i < branches; i++) {
                    double a = a0 + i * Math.PI * 2 / branches + range(r, -0.4, 0.4);
                    double len = range(r, 1.5, 2.5);
                    double sy = range(r, h * 0.55, h - 1.0);
                    double ex = Math.cos(a) * len;
                    double ez = Math.sin(a) * len;
                    double ey = sy + range(r, 1.0, 2.0);
                    bp.branch(0, sy, 0, ex, ey, ez, 0);
                    bp.blob(ex, ey + 0.5, ez, range(r, 2.0, 2.6), 1.8 * flat, range(r, 2.0, 2.6), 0.92);
                }
                bp.blob(0, h + 0.5, 0, 2.4, 2.0 * flat, 2.4, 0.95);
            }
            case MEDIUM -> {
                int h = range(r, 6, 10);
                bp.trunk(0, h - 1, 0);
                crown(bp, 0, h, range(r, 3, 5), 0.42, range(r, 3.0, 5.5), 1.5, 3.2, 0.0, range(r, 2.6, 3.3), flat);
                bp.blob(0, h + 0.5, 0, 3.0, 2.3 * flat, 3.0, 0.92);
                if (r.nextFloat() < 0.4) {
                    bp.surfaceRoots(3, 2, 0.0);
                }
            }
            case LARGE -> {
                int h = range(r, 9, 14);
                bp.trunk(0, h - 1, 0.5);
                flare(bp, 0.5, 1);
                crown(bp, 0.5, h, range(r, 5, 7), 0.38, range(r, 5.0, 7.5), 1.0, 3.5, 0.55, range(r, 3.0, 3.8), flat);
                bp.blob(0.5, h + 1, 0.5, 3.6, 2.6 * flat, 3.6, 0.9);
                bp.surfaceRoots(range(r, 4, 6), 3, 1.0);
            }
            case GIANT, ANCIENT -> {
                int h = range(r, 12, 18);
                bp.trunk(0, h - 1, 1.3);
                flare(bp, 2.0, 2);
                crown(bp, 0, h, range(r, 6, 9), 0.33, range(r, 7.5, 10.0), 0.6, 3.0, 0.85, range(r, 3.6, 4.4), flat);
                bp.blob(0, h + 2, 0, 4.4, 3.0 * flat, 4.4, 0.9);
                bp.surfaceRoots(range(r, 6, 8), 4, 1.8);
            }
        }
    }

    /**
     * Spreading crown: limbs leave the trunk between {@code startFrac * h} and the top and end in
     * leaf clusters; thick limbs also grow a sub-branch with its own cluster.
     */
    private static void crown(TreeBlueprint bp, double c, int h, int limbs, double startFrac, double length, double riseMin, double riseMax,
                              double thickness, double blobRadius, double flat) {
        RandomSource r = bp.random();
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < limbs; i++) {
            double a = a0 + i * Math.PI * 2 / limbs + range(r, -0.35, 0.35);
            double sy = range(r, h * startFrac, h - 1.0);
            double len = length * range(r, 0.75, 1.05);
            double ex = c + Math.cos(a) * len;
            double ez = c + Math.sin(a) * len;
            double ey = sy + range(r, riseMin, riseMax) * (len / Math.max(1.0, length)) + 1.0;
            bp.branch(c, sy, c, ex, ey, ez, thickness);
            double br = blobRadius * range(r, 0.85, 1.1);
            bp.blob(ex, ey + 0.8, ez, br, br * 0.68 * flat, br, 0.9);
            if (thickness > 0.3 || r.nextFloat() < 0.35) {
                double t = range(r, 0.45, 0.7);
                double mx = c + (ex - c) * t;
                double mz = c + (ez - c) * t;
                double my = sy + (ey - sy) * t;
                double sa = a + (r.nextBoolean() ? 1 : -1) * range(r, 0.6, 1.1);
                double sl = len * range(r, 0.35, 0.5);
                double sx = mx + Math.cos(sa) * sl;
                double sz = mz + Math.sin(sa) * sl;
                double sY = my + range(r, 1.0, 2.5);
                bp.branch(mx, my, mz, sx, sY, sz, 0);
                double sbr = blobRadius * range(r, 0.7, 0.9);
                bp.blob(sx, sY + 0.6, sz, sbr, sbr * 0.7 * flat, sbr, 0.9);
            }
        }
    }

    /** Bark-covered root flare around the base of a thick trunk. */
    private static void flare(TreeBlueprint bp, double radius, int height) {
        int r = Mth.ceil(radius) + 1;
        for (int y = 0; y < height; y++) {
            double rr = radius + 0.9 - y * 0.5;
            for (int dx = -r; dx <= r + 1; dx++) {
                for (int dz = -r; dz <= r + 1; dz++) {
                    double ddx = dx - 0.5 * (radius <= 0.6 ? 1 : 0);
                    double ddz = dz - 0.5 * (radius <= 0.6 ? 1 : 0);
                    if (ddx * ddx + ddz * ddz <= rr * rr && bp.random().nextFloat() < 0.85) {
                        bp.wood(dx, y, dz);
                    }
                }
            }
        }
    }

    // =========================================================================================
    // Birch: slender white trunks with narrow, airy crowns
    // =========================================================================================

    private static void birch(TreeBlueprint bp, TreeSize size) {
        RandomSource r = bp.random();
        int h = switch (size) {
            case YOUNG -> range(r, 3, 4);
            case SMALL -> range(r, 5, 7);
            case MEDIUM -> range(r, 8, 12);
            case LARGE -> range(r, 12, 17);
            case GIANT, ANCIENT -> range(r, 17, 23);
        };
        bp.trunk(0, h - 1, 0);
        double crownStart = h * (size == TreeSize.YOUNG ? 0.4 : 0.5);
        int branches = switch (size) {
            case YOUNG, SMALL -> 1;
            case MEDIUM -> range(r, 3, 4);
            case LARGE -> range(r, 4, 6);
            case GIANT, ANCIENT -> range(r, 5, 7);
        };
        double baseR = switch (size) {
            case YOUNG -> 1.4;
            case SMALL -> 1.8;
            case MEDIUM -> 2.1;
            case LARGE -> 2.4;
            case GIANT, ANCIENT -> 2.7;
        };
        double density = range(r, 0.78, 0.95);
        for (int i = 0; i < branches; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double sy = range(r, crownStart, h - 1.0);
            double len = range(r, 1.0, size.ordinal() >= 3 ? 3.5 : 2.2);
            double ex = Math.cos(a) * len;
            double ez = Math.sin(a) * len;
            double ey = sy + range(r, 1.0, 2.5);
            bp.branch(0, sy, 0, ex, ey, ez, 0);
            bp.blob(ex, ey + 0.5, ez, baseR, baseR * 1.2, baseR, density);
        }
        // column of foliage hugging the upper trunk
        for (double y = crownStart + 1; y <= h; y += 2.2) {
            bp.blob(range(r, -0.5, 0.5), y, range(r, -0.5, 0.5), baseR * 0.85, 1.6, baseR * 0.85, density);
        }
        bp.blob(0, h + 0.5, 0, baseR * 0.8, baseR, baseR * 0.8, 0.95);
    }

    // =========================================================================================
    // Conifers
    // =========================================================================================

    private static void spruce(TreeBlueprint bp, TreeSize size) {
        RandomSource r = bp.random();
        int h;
        double maxR;
        double trunkR;
        double bare;
        switch (size) {
            case YOUNG -> { h = range(r, 3, 5); maxR = 1.4; trunkR = 0; bare = 0.15; }
            case SMALL -> { h = range(r, 6, 9); maxR = range(r, 2.0, 2.7); trunkR = 0; bare = 0.18; }
            case MEDIUM -> { h = range(r, 10, 16); maxR = range(r, 3.0, 3.9); trunkR = 0; bare = 0.22; }
            case LARGE -> { h = range(r, 17, 26); maxR = range(r, 4.2, 5.2); trunkR = 0.5; bare = 0.28; }
            default -> { h = range(r, 27, 42); maxR = range(r, 5.8, 7.0); trunkR = 1.0; bare = 0.34; }
        }
        bp.trunk(0, h - 1, trunkR);
        double cx = trunkR == 0.5 ? 0.5 : 0.0;
        int start = (int) (h * bare);
        boolean whorl = true;
        for (int y = start; y <= h; y++) {
            double t = (double) (y - start) / Math.max(1, h - start);
            double radius = maxR * Math.pow(1.0 - t, 0.85) + 0.6;
            // alternating full whorls and thin interlayers give the layered spruce silhouette
            double layerR = whorl ? radius : radius * 0.55;
            bp.disc(cx, y, cx, layerR, whorl ? 0.92 : 0.8);
            if (whorl && radius > 2.2) {
                // droop: the rim of a whorl hangs one block lower
                int rim = Mth.ceil(layerR);
                for (int i = 0; i < 6; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    bp.leaf(Mth.floor(cx + Math.cos(a) * (rim - 0.8)), y - 1, Mth.floor(cx + Math.sin(a) * (rim - 0.8)));
                }
                if (radius > 3.6) {
                    // branch logs inside wide whorls keep every needle within six blocks of wood
                    int spokes = 4 + r.nextInt(3);
                    double a0 = r.nextDouble() * Math.PI;
                    for (int i = 0; i < spokes; i++) {
                        double a = a0 + i * Math.PI * 2 / spokes;
                        double len = radius - 2.2;
                        bp.branch(cx, y, cx, cx + Math.cos(a) * len, y - 0.4, cx + Math.sin(a) * len, 0);
                    }
                }
            }
            whorl = !whorl;
        }
        bp.leaf(0, h + 1, 0);
        bp.leaf(0, h + 2, 0);
        if (size.ordinal() >= TreeSize.LARGE.ordinal()) {
            bp.surfaceRoots(range(r, 4, 6), 3, trunkR + 0.5);
        }
    }

    private static void pine(TreeBlueprint bp, TreeSize size) {
        RandomSource r = bp.random();
        int h;
        double trunkR;
        double crownR;
        switch (size) {
            case YOUNG -> { h = range(r, 4, 6); trunkR = 0; crownR = 1.5; }
            case SMALL -> { h = range(r, 8, 12); trunkR = 0; crownR = 2.2; }
            case MEDIUM -> { h = range(r, 14, 20); trunkR = 0; crownR = 3.0; }
            case LARGE -> { h = range(r, 20, 28); trunkR = 0.5; crownR = 3.8; }
            default -> { h = range(r, 28, 38); trunkR = 1.0; crownR = 4.6; }
        }
        bp.trunk(0, h - 1, trunkR);
        double c = trunkR == 0.5 ? 0.5 : 0.0;
        int crownStart = (int) (h * range(r, 0.62, 0.72));
        // a tall bare trunk with a few dead stubs, then an irregular rounded crown
        for (int y = (int) (h * 0.3); y < crownStart; y += 3) {
            if (r.nextFloat() < 0.45) {
                double a = r.nextDouble() * Math.PI * 2;
                bp.branch(c, y, c, c + Math.cos(a) * 1.5, y + 0.5, c + Math.sin(a) * 1.5, 0);
            }
        }
        int limbs = 3 + size.ordinal();
        for (int i = 0; i < limbs; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double sy = range(r, crownStart, h - 2.0);
            double len = crownR * range(r, 0.5, 0.9);
            double ex = c + Math.cos(a) * len;
            double ez = c + Math.sin(a) * len;
            double ey = sy + range(r, 0.5, 1.5);
            bp.branch(c, sy, c, ex, ey, ez, 0);
            bp.blob(ex, ey + 0.3, ez, crownR * 0.65, 1.5, crownR * 0.65, 0.88);
        }
        bp.blob(c, h, c, crownR * 0.75, 2.2, crownR * 0.75, 0.9);
        bp.leaf(0, h + 1, 0);
        if (size.ordinal() >= TreeSize.LARGE.ordinal()) {
            bp.surfaceRoots(4, 3, trunkR + 0.5);
        }
    }

    private static void fir(TreeBlueprint bp, TreeSize size) {
        RandomSource r = bp.random();
        int h = switch (size) {
            case YOUNG -> range(r, 3, 5);
            case SMALL -> range(r, 6, 9);
            case MEDIUM -> range(r, 10, 15);
            case LARGE -> range(r, 15, 21);
            case GIANT, ANCIENT -> range(r, 21, 28);
        };
        double maxR = 1.2 + size.ordinal() * 0.45;
        bp.trunk(0, h - 1, 0);
        int start = Math.max(1, (int) (h * 0.12));
        for (int y = start; y <= h; y++) {
            double t = (double) (y - start) / Math.max(1, h - start);
            double radius = maxR * (1.0 - t * 0.75) + 0.4;
            bp.disc(0, y, 0, radius, 0.95);
        }
        bp.leaf(0, h + 1, 0);
        bp.leaf(0, h + 2, 0);
    }

    // =========================================================================================
    // Jungle: tall trunks, buttress roots and broad elevated canopies
    // =========================================================================================

    private static void jungle(TreeBlueprint bp, TreeSize size) {
        RandomSource r = bp.random();
        switch (size) {
            case YOUNG -> {
                bp.log(0, 0, 0, Direction.Axis.Y);
                bp.blob(0, 1, 0, range(r, 2.0, 2.6), 1.5, range(r, 2.0, 2.6), 0.95);
            }
            case SMALL -> {
                int h = range(r, 5, 8);
                bp.trunk(0, h - 1, 0);
                bp.blob(0, h, 0, range(r, 2.5, 3.0), 1.8, range(r, 2.5, 3.0), 0.92);
            }
            case MEDIUM -> {
                int h = range(r, 10, 16);
                bp.trunk(0, h - 1, 0);
                canopyPads(bp, 0, h, 3, range(r, 2.5, 4.0), range(r, 3.0, 3.8), 1.0);
                bp.blob(0, h + 1, 0, 3.2, 1.8, 3.2, 0.9);
            }
            case LARGE -> {
                int h = range(r, 18, 26);
                bp.trunk(0, h - 1, 0.5);
                buttresses(bp, range(r, 4, 5), 3, 0.5);
                canopyPads(bp, 0.5, h, range(r, 3, 4), range(r, 4.0, 6.0), range(r, 4.0, 5.0), 1.0);
                canopyPads(bp, 0.5, (int) (h * 0.6), 1, 3.5, 3.0, 0.9);
                bp.disc(0.5, h + 1, 0.5, 3.5, 0.9);
            }
            case GIANT, ANCIENT -> {
                int h = range(r, 26, 36);
                bp.trunk(0, h - 1, 1.2);
                buttresses(bp, range(r, 5, 6), 4, 1.2);
                canopyPads(bp, 0, h, range(r, 4, 5), range(r, 6.0, 8.5), range(r, 4.8, 5.8), 1.0);
                canopyPads(bp, 0, (int) (h * 0.68), range(r, 2, 3), range(r, 4.0, 6.0), range(r, 3.5, 4.2), 0.9);
                bp.disc(0, h + 1, 0, 4.5, 0.9);
                bp.disc(0, h + 2, 0, 2.5, 0.85);
            }
        }
    }

    /** Branches ending in two-layer leaf pads (jungle and giant canopies). */
    private static void canopyPads(TreeBlueprint bp, double c, int y0, int count, double length, double padR, double density) {
        RandomSource r = bp.random();
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double a = a0 + i * Math.PI * 2 / count + range(r, -0.3, 0.3);
            double len = length * range(r, 0.8, 1.05);
            double sy = y0 - range(r, 1.0, 3.0);
            double ex = c + Math.cos(a) * len;
            double ez = c + Math.sin(a) * len;
            double ey = y0 + range(r, 0.0, 1.5);
            bp.branch(c, sy, c, ex, ey, ez, len > 5 ? 0.6 : 0.0);
            int py = Mth.floor(ey);
            bp.disc(ex, py, ez, padR, density);
            bp.disc(ex, py + 1, ez, padR * 0.65, density * 0.9);
        }
    }

    /** Tall, thin buttress roots radiating from a thick trunk, curving away over the ground. */
    private static void buttresses(TreeBlueprint bp, int count, int length, double trunkR) {
        bp.rootSystem(count, trunkR, length + 2.0 + trunkR, 2.2 + 1.6 * trunkR, 0.5);
    }

    // =========================================================================================
    // Acacia: angled, forking trunks with flat umbrella pads
    // =========================================================================================

    private static void acacia(TreeBlueprint bp, TreeSize size) {
        RandomSource r = bp.random();
        int straight;
        int lean;
        int forks;
        double padR;
        switch (size) {
            case YOUNG -> { straight = 2; lean = 1; forks = 1; padR = 1.8; }
            case SMALL -> { straight = range(r, 2, 3); lean = range(r, 1, 2); forks = 1; padR = range(r, 2.5, 3.0); }
            case MEDIUM -> { straight = range(r, 3, 4); lean = range(r, 2, 3); forks = 2; padR = range(r, 3.0, 3.8); }
            case LARGE -> { straight = range(r, 4, 5); lean = range(r, 2, 4); forks = 3; padR = range(r, 3.8, 4.6); }
            default -> { straight = range(r, 5, 6); lean = range(r, 3, 4); forks = 3; padR = range(r, 4.6, 5.4); }
        }
        if (size == TreeSize.GIANT) {
            bp.trunk(0, 2, 0.5);
        }
        bp.trunk(0, straight - 1, 0);
        double a = r.nextDouble() * Math.PI * 2;
        double x = 0;
        double z = 0;
        double y = straight - 1;
        double nx = Math.cos(a) * lean;
        double nz = Math.sin(a) * lean;
        bp.branch(x, y, z, nx, y + lean, nz, 0);
        x = nx;
        z = nz;
        y += lean;
        double forkBase = a + Math.PI * range(r, 0.6, 1.4);
        for (int i = 0; i < forks; i++) {
            double fa = forks == 1 ? a + range(r, -0.5, 0.5) : forkBase + i * Math.PI * 2 / forks * range(r, 0.7, 1.0);
            double len = range(r, 1.5, 2.5) + size.ordinal() * 0.6;
            double ex = x + Math.cos(fa) * len;
            double ez = z + Math.sin(fa) * len;
            double ey = y + range(r, 1.0, 2.5) + (forks > 1 ? i * 0.5 : 0);
            bp.branch(x, y, z, ex, ey, ez, 0);
            int py = Mth.floor(ey) + 1;
            double pr = padR * range(r, 0.85, 1.1);
            bp.disc(ex, py, ez, pr, 0.95);
            bp.disc(ex, py + 1, ez, pr * 0.62, 0.9);
        }
    }

    // =========================================================================================
    // Dark oak: thick short trunks under heavy, wide domes
    // =========================================================================================

    private static void darkOak(TreeBlueprint bp, TreeSize size) {
        RandomSource r = bp.random();
        switch (size) {
            case YOUNG -> {
                int h = range(r, 3, 4);
                bp.trunk(0, h - 1, 0);
                bp.blob(0, h, 0, 2.3, 1.6, 2.3, 0.95);
            }
            case SMALL -> {
                int h = range(r, 4, 6);
                bp.trunk(0, h - 1, 0.5);
                bp.blob(0.5, h, 0.5, 3.6, 2.2, 3.6, 0.95);
            }
            case MEDIUM -> {
                int h = range(r, 6, 9);
                bp.trunk(0, h - 1, 0.5);
                crown(bp, 0.5, h, range(r, 3, 5), 0.6, range(r, 2.5, 4.0), 0.5, 1.5, 0.0, range(r, 3.2, 4.0), 0.75);
                bp.blob(0.5, h + 0.5, 0.5, 4.0, 2.4, 4.0, 0.95);
            }
            case LARGE -> {
                int h = range(r, 8, 12);
                bp.trunk(0, h - 1, 0.5);
                flare(bp, 0.5, 2);
                crown(bp, 0.5, h, range(r, 5, 7), 0.5, range(r, 4.0, 6.0), 0.5, 2.0, 0.55, range(r, 3.8, 4.6), 0.72);
                bp.blob(0.5, h + 1, 0.5, 4.8, 2.6, 4.8, 0.95);
                bp.surfaceRoots(range(r, 4, 6), 3, 1.0);
            }
            case GIANT, ANCIENT -> {
                int h = range(r, 10, 15);
                bp.trunk(0, h - 1, 1.3);
                flare(bp, 2.0, 2);
                crown(bp, 0, h, range(r, 7, 9), 0.45, range(r, 6.0, 8.5), 0.5, 2.5, 0.85, range(r, 4.2, 5.0), 0.72);
                bp.blob(0, h + 1.5, 0, 5.4, 3.0, 5.4, 0.95);
                bp.surfaceRoots(range(r, 6, 8), 4, 1.8);
            }
        }
    }

    // =========================================================================================
    // Mangrove: trunks raised on arching prop roots
    // =========================================================================================

    private static void mangrove(TreeBlueprint bp, TreeSize size) {
        RandomSource r = bp.random();
        int rootHeight;
        int h;
        int roots;
        double canopyR;
        double trunkR = 0;
        switch (size) {
            case YOUNG -> { rootHeight = 1; h = range(r, 3, 4); roots = 3; canopyR = 2.0; }
            case SMALL -> { rootHeight = 2; h = range(r, 5, 7); roots = range(r, 4, 5); canopyR = 2.8; }
            case MEDIUM -> { rootHeight = range(r, 2, 3); h = range(r, 7, 10); roots = range(r, 5, 7); canopyR = 3.4; }
            case LARGE -> { rootHeight = range(r, 3, 4); h = range(r, 10, 13); roots = range(r, 7, 9); canopyR = 4.0; }
            default -> { rootHeight = range(r, 4, 5); h = range(r, 12, 16); roots = range(r, 9, 12); canopyR = 4.6; trunkR = 0.5; }
        }
        BlockState rootBlock = Blocks.MANGROVE_ROOTS.defaultBlockState();
        double c = trunkR == 0.5 ? 0.5 : 0.0;
        // the trunk starts on top of the root cluster
        for (int y = 0; y < rootHeight; y++) {
            bp.root(0, y, 0, rootBlock);
            if (trunkR == 0.5) {
                bp.root(1, y, 0, rootBlock);
                bp.root(0, y, 1, rootBlock);
                bp.root(1, y, 1, rootBlock);
            }
        }
        for (int y = rootHeight; y < h; y++) {
            bp.trunkLayer(0, y, 0, trunkR);
        }
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < roots; i++) {
            double a = a0 + i * Math.PI * 2 / roots + range(r, -0.3, 0.3);
            double reach = range(r, 2.0, 2.0 + rootHeight * 0.9 + size.ordinal() * 0.4);
            double startY = range(r, rootHeight * 0.6, rootHeight + 1.5);
            double ex = c + Math.cos(a) * reach;
            double ez = c + Math.sin(a) * reach;
            double mx = c + Math.cos(a) * reach * 0.55;
            double mz = c + Math.sin(a) * reach * 0.55;
            int segments = 8;
            for (int s = 0; s <= segments; s++) {
                double t = s / (double) segments;
                double u = 1 - t;
                double px = u * u * c + 2 * u * t * mx + t * t * ex;
                double pz = u * u * c + 2 * u * t * mz + t * t * ez;
                double py = u * u * startY + 2 * u * t * (startY + 0.8) + t * t * -1.0;
                bp.root(Mth.floor(px + 0.5), Mth.floor(py + 0.5), Mth.floor(pz + 0.5), rootBlock);
            }
        }
        int limbs = 2 + size.ordinal() / 2;
        for (int i = 0; i < limbs; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double sy = range(r, h * 0.55, h - 1.0);
            double len = range(r, 1.5, 2.5 + size.ordinal());
            double ex = c + Math.cos(a) * len;
            double ez = c + Math.sin(a) * len;
            double ey = sy + range(r, 1.0, 2.0);
            bp.branch(c, sy, c, ex, ey, ez, 0);
            bp.blob(ex, ey + 0.5, ez, canopyR * 0.8, 1.8, canopyR * 0.8, 0.9);
        }
        bp.blob(c, h, c, canopyR, 2.2, canopyR, 0.92);
    }

    // =========================================================================================
    // Cherry: graceful curving limbs under wide pink clouds
    // =========================================================================================

    private static void cherry(TreeBlueprint bp, TreeSize size) {
        RandomSource r = bp.random();
        int h;
        int limbs;
        double len;
        double blobR;
        switch (size) {
            case YOUNG -> { h = range(r, 2, 3); limbs = 1; len = 1.2; blobR = 1.9; }
            case SMALL -> { h = range(r, 3, 5); limbs = 2; len = range(r, 2.5, 3.5); blobR = range(r, 2.5, 3.0); }
            case MEDIUM -> { h = range(r, 4, 6); limbs = 3; len = range(r, 4.0, 5.5); blobR = range(r, 3.0, 3.8); }
            case LARGE -> { h = range(r, 5, 8); limbs = range(r, 3, 4); len = range(r, 5.5, 7.5); blobR = range(r, 3.6, 4.4); }
            default -> { h = range(r, 7, 9); limbs = range(r, 4, 5); len = range(r, 7.0, 9.0); blobR = range(r, 4.2, 5.0); }
        }
        bp.trunk(0, h - 1, 0);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < limbs; i++) {
            double a = a0 + i * Math.PI * 2 / limbs + range(r, -0.35, 0.35);
            double l = len * range(r, 0.8, 1.05);
            double sy = h - range(r, 0.5, 2.0);
            double ex = Math.cos(a) * l;
            double ez = Math.sin(a) * l;
            double ey = sy + l * range(r, 0.45, 0.7);
            // limb sweeps out first, then turns upward
            double cxp = Math.cos(a) * l * 0.65;
            double czp = Math.sin(a) * l * 0.65;
            bp.curvedBranch(0, sy, 0, cxp, sy + 0.4, czp, ex, ey, ez, 0);
            double br = blobR * range(r, 0.85, 1.1);
            bp.blob(ex, ey + 0.6, ez, br, br * 0.62, br, 0.9);
            // weeping fringe below the cluster
            for (int k = 0; k < 5 + size.ordinal() * 2; k++) {
                double fa = r.nextDouble() * Math.PI * 2;
                double fr = br * range(r, 0.6, 0.95);
                int fx = Mth.floor(ex + Math.cos(fa) * fr);
                int fz = Mth.floor(ez + Math.sin(fa) * fr);
                int fy = Mth.floor(ey + 0.6 - br * 0.62);
                for (int d = 0; d < 1 + r.nextInt(2); d++) {
                    bp.leaf(fx, fy - d, fz);
                }
            }
        }
        bp.blob(0, h + 1, 0, blobR * 0.8, blobR * 0.55, blobR * 0.8, 0.85);
    }

    // =========================================================================================
    // Azalea: small gnarled trees with flowering foliage
    // =========================================================================================

    private static void azalea(TreeBlueprint bp, TreeSize size) {
        RandomSource r = bp.random();
        int h = switch (size) {
            case YOUNG -> 2;
            case SMALL -> range(r, 3, 4);
            case MEDIUM -> range(r, 4, 5);
            case LARGE -> range(r, 5, 6);
            case GIANT, ANCIENT -> range(r, 6, 7);
        };
        double a = r.nextDouble() * Math.PI * 2;
        double ox = Math.cos(a) * (h > 3 ? 1 : 0);
        double oz = Math.sin(a) * (h > 3 ? 1 : 0);
        bp.branch(0, 0, 0, 0, h * 0.5, 0, 0);
        bp.branch(0, h * 0.5, 0, ox, h - 1, oz, 0);
        double cr = 1.8 + size.ordinal() * 0.45;
        bp.blob(ox, h, oz, cr, cr * 0.7, cr, 0.9);
        if (size.ordinal() >= TreeSize.MEDIUM.ordinal()) {
            for (int i = 0; i < 2; i++) {
                double ba = a + Math.PI * (0.6 + i * 0.8);
                double ex = Math.cos(ba) * cr * 0.9;
                double ez = Math.sin(ba) * cr * 0.9;
                bp.branch(0, h * 0.6, 0, ex, h - 0.5, ez, 0);
                bp.blob(ex, h, ez, cr * 0.7, cr * 0.55, cr * 0.7, 0.9);
            }
        }
        bp.sprinkleAltLeaves(0.4);
        for (int y = -1; y >= -3; y--) {
            bp.root(0, y, 0, Blocks.ROOTED_DIRT.defaultBlockState());
        }
    }

    // =========================================================================================
    // Dead trees and snags
    // =========================================================================================

    private static void dead(TreeBlueprint bp, TreeSize size) {
        RandomSource r = bp.random();
        int h = switch (size) {
            case YOUNG -> range(r, 2, 3);
            case SMALL -> range(r, 3, 5);
            case MEDIUM -> range(r, 5, 8);
            case LARGE -> range(r, 8, 12);
            case GIANT, ANCIENT -> range(r, 12, 16);
        };
        bp.trunk(0, h - 1, size == TreeSize.GIANT ? 0.5 : 0);
        boolean conifer = bp.species() == TreeSpecies.DEAD_SPRUCE;
        int branches = conifer ? h / 2 : range(r, 1, 3 + size.ordinal() / 2);
        for (int i = 0; i < branches; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double sy = conifer ? range(r, h * 0.25, h - 1.0) : range(r, h * 0.45, h - 1.0);
            double len = conifer ? range(r, 1.0, 2.0) : range(r, 1.5, 3.5);
            bp.branch(0, sy, 0, Math.cos(a) * len, sy + (conifer ? -0.3 : range(r, 0.5, 2.0)), Math.sin(a) * len, 0);
        }
    }
}
