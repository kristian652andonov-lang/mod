package com.terrahaul.worldgen.features;

import com.terrahaul.worldgen.noise.SimplexNoise;
import com.terrahaul.worldgen.surface.Fades;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.IntBinaryOperator;
import java.util.function.IntToDoubleFunction;

/**
 * Shapes of the rock formations built by {@link RockFormationFeature}: arches, hoodoo fields,
 * spires and sea stacks.
 * <p>
 * Every shape is planned once from the terrain model and the site's seed, then evaluated as a pure
 * function of the block position: {@code material(...)} returns {@link #AIR} or the material code
 * of one block. Nothing depends on blocks of other chunks, so each chunk a formation overlaps can
 * build its own columns of it and the pieces fit together exactly. The class uses no Minecraft
 * types, so the development tool {@code FormationStudy} renders and checks the very shapes the game
 * builds.
 * <p>
 * Natural roughness comes from smooth 3D noise, never from per-block random numbers. Each shape has
 * a <b>core</b> that the noise cannot remove (the middle of an arch span, the axis of a pillar), so
 * roughness can thin a formation but never cut it in two, and the rare lump of a few blocks that
 * the noise would still detach at the surface is found once per plan by a flood fill over the model
 * terrain ({@link #floatingBlocks}) and left out: no block of a formation floats.
 * Material codes 0..2 are the stops of the site's rock family, from the palest, most weathered
 * stop to the freshest (see {@link GeologyPalette#family}); rock is laid in the same horizontal
 * strata as the surrounding terrain ({@link Fades#strata}).
 */
public final class FormationShapes {
    /** No block. */
    public static final int AIR = -1;
    /** Grass on top of a sea stack. */
    public static final int GRASS = 3;
    /** Soil below the grass of a sea stack. */
    public static final int DIRT = 4;

    /** Coherent roughness shared by all formations; offsets per site keep them different. */
    static final SimplexNoise ROUGH = new SimplexNoise(0x7E77A0C4L);

    private FormationShapes() {
    }

    /** Strata period of formations in a province. */
    static double period(int geology) {
        return geology == Geology.SEDIMENTARY ? Fades.SEDIMENTARY_PERIOD : Fades.ROCK_PERIOD;
    }

    /** Rock family stop of the terrain strata at a block (the column warp is passed in, see {@link Fades#strataWarp}). */
    static int band(TerrainModel model, int x, int y, int z, double warp, double period) {
        return Fades.strataStop(model, 3, x, y, z, warp, period, Fades.wobble(model, x, y, z));
    }

    /**
     * Approximate signed distance in blocks from {@code (du, dy)} to the superellipse
     * {@code |du/a|^p + |dy/h|^p = 1}: negative inside, positive outside. The field value is divided
     * by its gradient, which is exact on the curve and good to a block or two nearby.
     */
    static double superellipse(double du, double dy, double a, double h, double p) {
        double ax = Math.abs(du) / a;
        double ay = Math.abs(dy) / h;
        double sx = Math.pow(ax, p);
        double sy = Math.pow(ay, p);
        double r = Math.pow(sx + sy, 1.0 / p);
        if (r < 1.0E-6) {
            return -Math.min(a, h);
        }
        double k = Math.pow(r, 1.0 - p);
        double gx = k * Math.pow(ax, p - 1.0) / a;
        double gy = k * Math.pow(ay, p - 1.0) / h;
        double g = Math.sqrt(gx * gx + gy * gy);
        return Math.max((r - 1.0) / Math.max(g, 1.0E-6), -Math.min(a, h));
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Block position as one long (26 bits x, 26 bits z, 12 bits y), for sets of positions. */
    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    /** Whether a planned shape has a block at a position, for {@link #floatingBlocks}. */
    interface Solid {
        boolean at(int x, int y, int z, int ground);
    }

    /**
     * The blocks of a shape that would hang in the air: a 6-connected flood fill through the shape,
     * starting from its blocks that touch the model terrain, misses them. The roughness noise is
     * gentle and every shape has a solid core, so these are rare single blocks or tiny lumps on
     * the surface; computing them once per plan (the shape is a pure function, so every chunk
     * gets the same answer) lets the builders drop them and guarantees that nothing floats.
     */
    static Set<Long> floatingBlocks(TerrainModel model, int minX, int maxX, int minZ, int maxZ, int maxY, Solid solid) {
        return floatingBlocks((x, z) -> (int) Math.floor(model.interpolatedHeight(x, z)), minX, maxX, minZ, maxZ, maxY, solid);
    }

    /**
     * {@link #floatingBlocks(TerrainModel, int, int, int, int, int, Solid)} on a given terrain:
     * {@code groundAt} gives the Y of the top solid block of a column.
     */
    static Set<Long> floatingBlocks(IntBinaryOperator groundAt, int minX, int maxX, int minZ, int maxZ, int maxY, Solid solid) {
        int x0 = minX - 1;
        int z0 = minZ - 1;
        int sx = maxX - minX + 3;
        int sz = maxZ - minZ + 3;
        int[] ground = new int[sx * sz];
        int minY = Integer.MAX_VALUE;
        for (int i = 0; i < sx; i++) {
            for (int k = 0; k < sz; k++) {
                int g = groundAt.applyAsInt(x0 + i, z0 + k);
                ground[k * sx + i] = g;
                minY = Math.min(minY, g);
            }
        }
        minY -= 3;
        int sy = Math.max(1, maxY - minY + 2);
        byte[] cell = new byte[sx * sy * sz];
        int blocks = 0;
        for (int i = 0; i < sx; i++) {
            for (int k = 0; k < sz; k++) {
                int g = ground[k * sx + i];
                for (int j = 0; j < sy; j++) {
                    int y = minY + j;
                    int idx = (j * sz + k) * sx + i;
                    if (solid.at(x0 + i, y, z0 + k, g)) {
                        cell[idx] = 2;
                        blocks++;
                    } else if (y <= g) {
                        cell[idx] = 1;
                    }
                }
            }
        }
        if (blocks == 0) {
            return Set.of();
        }
        // seeds: shape blocks next to the terrain or at the bottom of the box
        int[] queue = new int[blocks];
        int head = 0;
        int tail = 0;
        int plane = sx * sz;
        for (int idx = 0; idx < cell.length; idx++) {
            if (cell[idx] != 2) {
                continue;
            }
            int i = idx % sx;
            int k = (idx / sx) % sz;
            int j = idx / plane;
            if (j == 0 || cell[idx - plane] == 1 || j + 1 < sy && cell[idx + plane] == 1
                || i > 0 && cell[idx - 1] == 1 || i + 1 < sx && cell[idx + 1] == 1
                || k > 0 && cell[idx - sx] == 1 || k + 1 < sz && cell[idx + sx] == 1) {
                cell[idx] = 3;
                queue[tail++] = idx;
            }
        }
        while (head < tail) {
            int idx = queue[head++];
            int i = idx % sx;
            int k = (idx / sx) % sz;
            int j = idx / plane;
            if (i > 0 && cell[idx - 1] == 2) {
                cell[idx - 1] = 3;
                queue[tail++] = idx - 1;
            }
            if (i + 1 < sx && cell[idx + 1] == 2) {
                cell[idx + 1] = 3;
                queue[tail++] = idx + 1;
            }
            if (k > 0 && cell[idx - sx] == 2) {
                cell[idx - sx] = 3;
                queue[tail++] = idx - sx;
            }
            if (k + 1 < sz && cell[idx + sx] == 2) {
                cell[idx + sx] = 3;
                queue[tail++] = idx + sx;
            }
            if (j > 0 && cell[idx - plane] == 2) {
                cell[idx - plane] = 3;
                queue[tail++] = idx - plane;
            }
            if (j + 1 < sy && cell[idx + plane] == 2) {
                cell[idx + plane] = 3;
                queue[tail++] = idx + plane;
            }
        }
        if (tail == blocks) {
            return Set.of();
        }
        Set<Long> out = new HashSet<>();
        for (int idx = 0; idx < cell.length; idx++) {
            if (cell[idx] == 2) {
                out.add(pack(x0 + idx % sx, minY + idx / plane, z0 + (idx / sx) % sz));
            }
        }
        return out;
    }

    /** Number of blocks a plan dropped because they would have floated (for the development tools). */
    public static int dropped(Object plan) {
        if (plan instanceof Arch a) {
            return a.floating.size();
        }
        if (plan instanceof Spire p) {
            return p.floating.size();
        }
        return plan instanceof HoodooField f ? f.floating.size() : 0;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // =========================================================================================
    // arches
    // =========================================================================================

    /**
     * A natural arch, in a frame whose {@code u} axis runs along the span, {@code v} across it and
     * whose base is {@link #baseY}.
     * <p>
     * The opening and the outline of the rock are two lopsided superellipses sharing one apex: each
     * half has its own width and exponent (rounder, pointier or boxier), so the span is off centre,
     * one leg is broader than the other and the crown may be flat or peaked. The outline always
     * encloses the opening with a margin (the crown thickness above, the leg width at the feet), and
     * the band between them, shrunk by 0.75 blocks on each side, is the core. Along the span the
     * outline swells and pinches (never below 2.7 blocks of rock) and the underside sags into
     * hanging lumps; 3D noise roughens every surface by up to about 1.4 blocks, softer strata are
     * eroded back and harder ones stand out as ledges, and the span bows and leans sideways and
     * thins towards the crown. About a third of
     * the arches are fins: a tall, thin wall with a broad, eroded crest pierced by the opening, like
     * the windows of Arches National Park. Below the base the legs continue down to the ground of
     * every column, spreading into a talus apron; rubble blocks lie around the feet.
     */
    public static final class Arch {
        public final int x;
        public final int z;
        public final int baseY;
        public final boolean fin;
        public final int minX;
        public final int maxX;
        public final int minZ;
        public final int maxZ;
        public final int maxY;
        final double cos;
        final double sin;
        // opening: apex at u = uc, half widths aL (u < uc) and aR, height h, exponents
        final double uc;
        final double aL;
        final double aR;
        final double h;
        final double pL;
        final double pR;
        // outline: same apex, wider and higher
        final double bL;
        final double bR;
        final double outerH;
        final double qL;
        final double qR;
        // across the span
        final double depthFoot;
        final double depthCrown;
        final double bend;
        /** Sideways lean of the crown over the feet. */
        final double lean;
        /** Amplitudes of the thickness and underside variations along the span. */
        final double crownVar;
        final double thinLimit;
        final double sagVar;
        final double off;
        final double period;
        /** Rubble blocks as {x, y, z, radius}. */
        final double[][] rubble;
        /** Blocks the noise would leave unattached (see {@link #floatingBlocks}); never built. */
        private Set<Long> floating = Set.of();

        private Arch(TerrainModel model, TerrainModel.FormationSite site) {
            SplittableRandom r = new SplittableRandom(site.seed);
            this.x = site.x;
            this.z = site.z;
            this.fin = r.nextDouble() < 0.35;
            double a = 6.0 + site.size * 9.0 + r.nextDouble() * 3.0;
            double shortSide = 0.5 + r.nextDouble() * 0.5;
            boolean leftShort = r.nextBoolean();
            this.aL = leftShort ? a * shortSide : a;
            this.aR = leftShort ? a : a * shortSide;
            this.h = (0.85 + r.nextDouble() * 0.75) * a * (fin ? 0.8 : 1.0);
            this.pL = 1.6 + r.nextDouble() * 1.2;
            this.pR = 1.6 + r.nextDouble() * 1.2;
            double crown = fin ? 6.0 + r.nextDouble() * 9.0 : 3.0 + r.nextDouble() * 2.8 + site.size * 1.2;
            double legL = (fin ? 6.0 + r.nextDouble() * 8.0 : 3.0 + r.nextDouble() * 5.5) + a * 0.12;
            double legR = (fin ? 6.0 + r.nextDouble() * 8.0 : 3.0 + r.nextDouble() * 5.5) + a * 0.12;
            // thickness swells and pinches along the span, never below 2.7 blocks; the underside
            // sags into hanging lumps here and there
            this.crownVar = fin ? 3.5 : 0.45 * crown;
            this.thinLimit = Math.min(crown, Math.min(legL, legR)) - 2.7;
            this.sagVar = 0.8 + r.nextDouble() * 1.4;
            this.lean = (r.nextDouble() - 0.5) * (fin ? 1.5 : 4.0);
            this.bL = aL + legL;
            this.bR = aR + legR;
            this.outerH = h + crown;
            // a higher exponent than the opening's keeps the outline outside the opening everywhere
            this.qL = Math.max(pL, fin ? 3.0 + r.nextDouble() * 2.0 : 2.0 + r.nextDouble());
            this.qR = Math.max(pR, fin ? 3.0 + r.nextDouble() * 2.0 : 2.0 + r.nextDouble());
            this.depthFoot = fin ? 2.2 + r.nextDouble() * 1.4 : 3.0 + r.nextDouble() * 2.2 + site.size * 1.5;
            this.depthCrown = fin ? depthFoot * (0.8 + r.nextDouble() * 0.2) : 1.6 + r.nextDouble() * 1.2;
            this.bend = (r.nextDouble() - 0.5) * (fin ? 2.0 : 6.0);
            this.uc = (r.nextDouble() - 0.5) * 3.0;
            this.off = (site.seed & 0xFFFF) * 0.731;
            this.period = period(site.geology);
            int rubbleCount = 3 + r.nextInt(5);
            double[] rolls = new double[rubbleCount * 4 + 4];
            for (int i = 0; i < rolls.length; i++) {
                rolls[i] = r.nextDouble();
            }

            // orientation: of eight directions, the one whose feet stand on the most similar,
            // dry ground (rather than dropping the arch, so every arch site holds an arch)
            double footL = uc - aL - legL * 0.5;
            double footR = uc + aR + legR * 0.5;
            ColumnSample s = new ColumnSample();
            double bestScore = Double.MAX_VALUE;
            double bestAngle = site.angle;
            double bestBase = site.ground;
            for (int k = 0; k < 8; k++) {
                double angle = site.angle + k * Math.PI / 8.0;
                double c = Math.cos(angle);
                double sn = Math.sin(angle);
                model.sampleInto((int) Math.round(site.x + c * footL), (int) Math.round(site.z + sn * footL), s);
                double h1 = s.height;
                boolean wet1 = s.waterLevel != ColumnSample.NO_WATER || s.height < model.seaLevel();
                model.sampleInto((int) Math.round(site.x + c * footR), (int) Math.round(site.z + sn * footR), s);
                double h2 = s.height;
                boolean wet2 = s.waterLevel != ColumnSample.NO_WATER || s.height < model.seaLevel();
                // highest ground under the opening
                double under = -Double.MAX_VALUE;
                for (double f = -0.5; f <= 0.5; f += 0.5) {
                    double u = uc + f * (f < 0.0 ? aL : aR);
                    model.sampleInto((int) Math.round(site.x + c * u), (int) Math.round(site.z + sn * u), s);
                    under = Math.max(under, s.height);
                }
                double score = Math.abs(h1 - h2) + (wet1 ? 25.0 : 0.0) + (wet2 ? 25.0 : 0.0)
                    + Math.max(0.0, under - Math.max(h1, h2)) + k * 0.01;
                if (score < bestScore) {
                    bestScore = score;
                    bestAngle = angle;
                    // the opening stands clear of the ground under it; a lower foot reaches down
                    bestBase = Math.max(Math.min(h1, h2), under - 1.0);
                }
            }
            this.cos = Math.cos(bestAngle);
            this.sin = Math.sin(bestAngle);
            this.baseY = (int) Math.floor(bestBase);

            // rubble: blocks fallen from the legs and, sometimes, a slab fallen from the span
            List<double[]> blocks = new ArrayList<>();
            for (int i = 0; i < rubbleCount; i++) {
                boolean left = rolls[i * 4] < 0.5;
                double footU = left ? footL : footR;
                double footW = left ? legL : legR;
                double outward = rolls[i * 4 + 3] < 0.3 ? 1.0 : -1.0;
                double ru = footU + (left ? outward : -outward) * (footW * 0.5 + 1.0 + rolls[i * 4 + 1] * 5.0);
                double rv = (rolls[i * 4 + 2] - 0.5) * 2.0 * (depthFoot + 3.0);
                blocks.add(rubbleBlock(model, ru, rv, 0.9 + rolls[i * 4 + 3] * 1.5));
            }
            int j = rubbleCount * 4;
            if (rolls[j] < 0.4) {
                blocks.add(rubbleBlock(model, uc + (rolls[j + 1] - 0.5) * a, (rolls[j + 2] - 0.5) * depthCrown, 1.6 + rolls[j + 3] * 1.2));
            }
            this.rubble = blocks.toArray(new double[0][]);

            // bounds: the rotated rectangle of the shape plus the rubble
            double u0 = uc - bL - 3.0;
            double u1 = uc + bR + 3.0;
            double vr = depthFoot + Math.abs(bend) + Math.abs(lean) + 3.0;
            double minXd = Double.MAX_VALUE;
            double maxXd = -Double.MAX_VALUE;
            double minZd = Double.MAX_VALUE;
            double maxZd = -Double.MAX_VALUE;
            for (double u : new double[]{u0, u1}) {
                for (double v : new double[]{-vr, vr}) {
                    double wx = x + cos * u - sin * v;
                    double wz = z + sin * u + cos * v;
                    minXd = Math.min(minXd, wx);
                    maxXd = Math.max(maxXd, wx);
                    minZd = Math.min(minZd, wz);
                    maxZd = Math.max(maxZd, wz);
                }
            }
            for (double[] b : rubble) {
                minXd = Math.min(minXd, b[0] - b[3] - 2.0);
                maxXd = Math.max(maxXd, b[0] + b[3] + 2.0);
                minZd = Math.min(minZd, b[2] - b[3] - 2.0);
                maxZd = Math.max(maxZd, b[2] + b[3] + 2.0);
            }
            this.minX = (int) Math.floor(minXd);
            this.maxX = (int) Math.ceil(maxXd);
            this.minZ = (int) Math.floor(minZd);
            this.maxZ = (int) Math.ceil(maxZd);
            int top = baseY + (int) Math.ceil(outerH + crownVar) + 3;
            for (double[] b : rubble) {
                top = Math.max(top, (int) Math.ceil(b[1] + b[3]) + 2);
            }
            this.maxY = top;
            this.floating = floatingBlocks(model, minX, maxX, minZ, maxZ, maxY,
                (bx, y, bz, ground) -> material(model, bx, y, bz, ground, Fades.strataWarp(model, bx, bz)) != AIR);
        }

        private double[] rubbleBlock(TerrainModel model, double u, double v, double radius) {
            double wx = x + cos * u - sin * v;
            double wz = z + sin * u + cos * v;
            double wy = model.interpolatedHeight((int) Math.round(wx), (int) Math.round(wz)) + radius * 0.25;
            return new double[]{wx, wy, wz, radius};
        }

        public static Arch plan(TerrainModel model, TerrainModel.FormationSite site) {
            return new Arch(model, site);
        }

        /**
         * Material at a block: {@link #AIR} or a stop of the rock family (0..2).
         *
         * @param ground Y of the top solid block of the column before the arch was built
         * @param warp   the column's {@link Fades#strataWarp}
         */
        public int material(TerrainModel model, int bx, int y, int bz, int ground, double warp) {
            if (y < ground - 2) {
                // anchored two blocks deep; below that the terrain is left alone
                return AIR;
            }
            double dx = bx - x;
            double dz = bz - z;
            double u = dx * cos + dz * sin;
            double v = -dx * sin + dz * cos;
            double yy = y - baseY;
            double du = u - uc;
            boolean left = du < 0.0;
            double bu = left ? bL : bR;
            double yp = Math.max(0.0, yy);
            double dOut = Math.abs(du) > bu + 6.0 ? 99.0 : superellipse(du, yp, bu, outerH, left ? qL : qR);
            if (yy < 0.0) {
                // below the base the legs spread into a talus apron
                dOut -= Math.min(3.0, -yy * 0.4);
            }
            // thickness variation along the span: the outline swells or pinches (always leaving
            // 2.7 blocks), the underside hangs lower in places; both are smooth in u only, so
            // they shape the arch the same way through its whole depth
            double swell = 0.75 * ROUGH.noise3(du * 0.09 + off, 400.0, 17.0) + 0.25 * ROUGH.noise3(du * 0.23 + off, 400.0, 53.0);
            dOut -= Math.max(-thinLimit, crownVar * swell);
            double cv = bend * Math.max(0.0, 1.0 - sq(du / bu)) + lean * Math.min(1.0, yp / outerH);
            double across = Math.abs(v - cv);
            // the noise moves surfaces by at most about 1.35 blocks, ledges by 0.35 more
            boolean near = dOut < 2.2 && across < depthFoot + 2.2;
            double[] block = null;
            for (double[] b : rubble) {
                if (Math.abs(bx - b[0]) <= b[3] + 1.5 && Math.abs(bz - b[2]) <= b[3] + 1.5 && y <= b[1] + b[3] + 1.5 && y >= ground - 1) {
                    block = b;
                    break;
                }
            }
            if (!near && block == null) {
                return AIR;
            }
            // roughness: gentle enough (gradient well below one block per block) that it can
            // dent and bulge the surfaces but not pinch off separate lumps
            double n = 1.1 * ROUGH.noise3(bx * 0.11 + off, y * 0.12, bz * 0.11)
                + 0.25 * ROUGH.noise3(bx * 0.26 - off, y * 0.26 + 50.0, bz * 0.26);
            double stratum = Fades.strata(model, bx, y, bz, warp, period);
            if (near) {
                double dOpen = superellipse(du, yp, left ? aL : aR, h, left ? pL : pR)
                    + sagVar * Math.max(0.0, ROUGH.noise3(du * 0.12 - off, 600.0, 91.0));
                double foot = clamp(1.0 - yp / (outerH * 0.9), 0.0, 1.0);
                double depth = depthCrown + (depthFoot - depthCrown) * Math.pow(foot, 1.5);
                // harder (fresher) beds stand out, softer ones are eroded back
                double bed = (stratum - 0.5) * 0.7;
                // the core, the noise-free band between opening and outline shrunk by 0.75
                // blocks, is always rock, so the span stays joined to both feet
                boolean core = dOpen >= 0.75 && dOut <= -0.75 && across <= Math.max(0.75, Math.min(depth - 0.75, 1.6));
                if (core || dOpen > -n && dOut < n + bed && across < depth + 0.8 * n + bed) {
                    return floating.contains(pack(bx, y, bz)) ? AIR : Fades.stop(3, stratum, Fades.wobble(model, bx, y, bz) * Fades.STRATA_WOBBLE);
                }
            }
            if (block != null) {
                double ex = bx - block[0];
                double ey = y - block[1];
                double ez = bz - block[2];
                double hd = ex * ex + ez * ez;
                double rr = block[3] + 0.4 * n;
                // the block itself, flattened a little, and a footing down to the ground
                if (hd + ey * ey * 1.3 <= rr * rr || ey < 0.0 && hd <= sq(block[3] * 0.8)) {
                    return floating.contains(pack(bx, y, bz)) ? AIR : Fades.stop(3, stratum, 0.0);
                }
            }
            return AIR;
        }
    }

    // =========================================================================================
    // hoodoos
    // =========================================================================================

    /**
     * A field of hoodoos as in Bryce Canyon: 14 to 45 pillars, up to about 45 blocks tall, standing
     * in two to five slightly curved rows, some joined at the base by eroded fins. The field shares
     * one sequence of beds: harder, paler beds bulge and softer ones are pinched in, at the same
     * heights on every pillar, which gives the beaded profiles their order. Every pillar is worn to
     * a point (see {@link Hoodoo}); there is no cap rock, since a pale slab on a narrow neck made the
     * pillars look like mushrooms or lamp posts. Pillars are shorter towards the edge of the field,
     * flare into a talus skirt at the foot and are skipped on steep or wet ground, except the pillar
     * at the site itself, so every hoodoo site holds hoodoos.
     */
    public static final class HoodooField {
        public final List<Hoodoo> pillars;
        final List<Fin> fins;
        public final int minX;
        public final int maxX;
        public final int minZ;
        public final int maxZ;
        public final int maxY;
        final double beadPeriod;
        final double beadOffset;
        final double off;
        /** Blocks the noise would leave unattached (see {@link #floatingBlocks}); never built. */
        private Set<Long> floating = Set.of();

        private HoodooField(TerrainModel model, TerrainModel.FormationSite site) {
            SplittableRandom r = new SplittableRandom(site.seed ^ 0x40D00D5L);
            int rows = 2 + r.nextInt(4);
            double fieldR = 30.0 + site.size * 40.0;
            int count = 14 + r.nextInt(10) + (int) (site.size * 21.0);
            double spacing = 8.5 + r.nextDouble() * 3.5;
            double curve = (r.nextDouble() - 0.5) * 14.0;
            this.beadPeriod = 5.5 + r.nextDouble() * 3.0;
            this.beadOffset = r.nextDouble() * 100.0;
            this.off = ((site.seed >>> 16) & 0xFFFF) * 0.517;
            double cos = Math.cos(site.angle);
            double sin = Math.sin(site.angle);
            int perRow = (count + rows - 1) / rows;
            double slot = 2.0 * fieldR / perRow;
            ColumnSample s = new ColumnSample();
            List<Hoodoo> placed = new ArrayList<>();
            List<List<Hoodoo>> byRow = new ArrayList<>();
            for (int i = 0; i < rows; i++) {
                byRow.add(new ArrayList<>());
            }
            for (int i = 0; i < count; i++) {
                // every parameter is drawn before the pillar may be skipped, so the sequence of
                // random numbers never depends on the terrain
                int row = i % rows;
                double along = -fieldR + (i / rows + 0.15 + 0.7 * r.nextDouble()) * slot;
                double across = (row - (rows - 1) * 0.5) * spacing + (r.nextDouble() - 0.5) * 3.0 + curve * sq(along / fieldR);
                if (i == 0) {
                    along = 0.0;
                    across = 0.0;
                }
                double rowSpan = Math.max(1.0, (rows - 1) * 0.5 * spacing + 2.0);
                double edge = 1.0 - 0.5 * sq(along / fieldR) - 0.25 * sq(across / rowSpan);
                double height = (14.0 + r.nextDouble() * 18.0 + site.size * 16.0) * edge;
                boolean stump = r.nextDouble() < 0.2;
                double r0 = 1.9 + r.nextDouble() * 1.3 + height * 0.04;
                double shoulderRoll = r.nextDouble();
                double beadAmp = 0.14 + r.nextDouble() * 0.16;
                double shoulderRound = 1.3 + r.nextDouble() * 0.9;
                double pointRoll = r.nextDouble();
                double flute = 0.12 + r.nextDouble() * 0.18;
                double leanX = (r.nextDouble() - 0.5) * 0.14;
                double leanZ = (r.nextDouble() - 0.5) * 0.14;
                double aspect = 0.72 + 0.28 * r.nextDouble();
                double rot = r.nextDouble() * Math.PI;
                double tipX = (r.nextDouble() - 0.5) * 2.0;
                double tipZ = (r.nextDouble() - 0.5) * 2.0;
                double flare = 1.0 + r.nextDouble() * 1.4;
                double finRoll = r.nextDouble();
                double finHeight = 0.3 + r.nextDouble() * 0.25;
                // the taper begins on a shoulder a third to two thirds of the way up and runs to a
                // slender needle or a blunter point; stumps taper almost from the foot to a short,
                // blunt point
                double shoulder = stump ? 0.1 + 0.2 * shoulderRoll : 0.32 + 0.33 * shoulderRoll;
                double point = stump ? 0.55 + 0.25 * pointRoll : 0.65 + 0.45 * pointRoll;
                if (stump) {
                    height *= 0.45;
                }
                int hx = site.x + (int) Math.round(along * cos - across * sin);
                int hz = site.z + (int) Math.round(along * sin + across * cos);
                model.sampleInto(hx, hz, s);
                boolean wet = s.waterLevel != ColumnSample.NO_WATER || s.height < model.seaLevel() + 1 || s.river > 0.0 || s.lake > 0.0;
                double base = s.height;
                if (i > 0 && (wet || height < 5.0 || steep(model, s, hx, hz, (int) Math.ceil(r0 + 2.0), base))) {
                    continue;
                }
                Hoodoo p = new Hoodoo(hx, hz, (int) Math.floor(base), height, r0, beadAmp, shoulder, shoulderRound, point, flute,
                    tipX, tipZ, leanX * height, leanZ * height, aspect, rot, flare, this::bead);
                p.along = along;
                p.finRoll = finRoll;
                p.finHeight = finHeight;
                placed.add(p);
                byRow.get(row).add(p);
            }
            // fins: some neighbours in a row stay joined by a low, eroded wall
            List<Fin> joints = new ArrayList<>();
            for (List<Hoodoo> line : byRow) {
                line.sort(Comparator.comparingDouble(p -> p.along));
                for (int i = 0; i + 1 < line.size(); i++) {
                    Hoodoo a = line.get(i);
                    Hoodoo b = line.get(i + 1);
                    double gap = Math.sqrt(sq(a.x - b.x) + sq(a.z - b.z));
                    if (a.finRoll < 0.45 && gap < (a.r0 + b.r0) * 2.2 + 6.0 && Math.abs(a.base - b.base) < 6) {
                        joints.add(new Fin(a, b, a.finHeight));
                    }
                }
            }
            this.pillars = placed;
            this.fins = joints;
            int mnX = Integer.MAX_VALUE;
            int mxX = Integer.MIN_VALUE;
            int mnZ = Integer.MAX_VALUE;
            int mxZ = Integer.MIN_VALUE;
            int top = Integer.MIN_VALUE;
            for (Hoodoo p : placed) {
                mnX = Math.min(mnX, p.x - p.ext);
                mxX = Math.max(mxX, p.x + p.ext);
                mnZ = Math.min(mnZ, p.z - p.ext);
                mxZ = Math.max(mxZ, p.z + p.ext);
                top = Math.max(top, p.top);
            }
            this.minX = mnX;
            this.maxX = mxX;
            this.minZ = mnZ;
            this.maxZ = mxZ;
            this.maxY = top;
            if (!placed.isEmpty()) {
                // the flood fill walks each column bottom to top: reuse the column's parts
                Column[] last = new Column[1];
                int[] lastXz = {Integer.MIN_VALUE, Integer.MIN_VALUE};
                this.floating = floatingBlocks(model, minX, maxX, minZ, maxZ, maxY, (bx, y, bz, ground) -> {
                    if (bx != lastXz[0] || bz != lastXz[1]) {
                        last[0] = column(bx, bz);
                        lastXz[0] = bx;
                        lastXz[1] = bz;
                    }
                    return material(model, bx, y, bz, ground, last[0]) != AIR;
                });
            }
        }

        public static HoodooField plan(TerrainModel model, TerrainModel.FormationSite site) {
            return new HoodooField(model, site);
        }

        /** Hardness of the bed at height {@code y}, -1 (soft) .. 1 (hard); the same on every pillar. */
        double bead(int y) {
            double phase = (y + beadOffset) / beadPeriod + 0.22 * ROUGH.noise3(off, y / 9.0, 31.0);
            return Math.cos(phase * Math.PI * 2.0);
        }

        /** The pillars and fins whose bounds cover a column; evaluate a column with one of these. */
        public Column column(int bx, int bz) {
            List<Hoodoo> ps = new ArrayList<>();
            for (Hoodoo p : pillars) {
                if (Math.abs(bx - p.x) <= p.ext && Math.abs(bz - p.z) <= p.ext) {
                    ps.add(p);
                }
            }
            List<Fin> fs = new ArrayList<>();
            for (Fin f : fins) {
                if (bx >= f.minX && bx <= f.maxX && bz >= f.minZ && bz <= f.maxZ) {
                    fs.add(f);
                }
            }
            return new Column(ps.toArray(new Hoodoo[0]), fs.toArray(new Fin[0]));
        }

        /** Material at a block (see {@link #material(TerrainModel, int, int, int, int, Column)}). */
        public int material(TerrainModel model, int bx, int y, int bz, int ground) {
            return material(model, bx, y, bz, ground, column(bx, bz));
        }

        /**
         * Material at a block: {@link #AIR} or a stop of the rock family (0 for the pale hard
         * beds, 2 for the soft dark ones).
         *
         * @param ground Y of the top solid block of the column before the field was built
         * @param column {@link #column} of this block's column
         */
        public int material(TerrainModel model, int bx, int y, int bz, int ground, Column column) {
            if (y < ground - 2 || column.pillars.length == 0 && column.fins.length == 0) {
                // anchored two blocks deep; below that the terrain is left alone
                return AIR;
            }
            // cheap bounds first: the noise is only evaluated next to a pillar or fin
            boolean any = false;
            for (Hoodoo p : column.pillars) {
                if (p.near(bx, y, bz)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                for (Fin f : column.fins) {
                    if (f.near(bx, y, bz)) {
                        any = true;
                        break;
                    }
                }
            }
            if (!any) {
                return AIR;
            }
            double bead = bead(y);
            double n = 0.55 * ROUGH.noise3(bx * 0.3 + off, y * 0.25, bz * 0.3) + 0.25 * ROUGH.noise3(bx * 0.7 - off, y * 0.7, bz * 0.7);
            int result = AIR;
            for (Hoodoo p : column.pillars) {
                if (p.near(bx, y, bz) && p.solid(bx, y, bz, n)) {
                    result = 1;
                    break;
                }
            }
            if (result == AIR) {
                for (Fin f : column.fins) {
                    if (f.near(bx, y, bz) && f.contains(bx, y, bz, ground, n)) {
                        result = 1;
                        break;
                    }
                }
            }
            if (result == AIR || floating.contains(pack(bx, y, bz))) {
                return AIR;
            }
            return Fades.stop(3, 0.5 - 0.5 * bead, Fades.wobble(model, bx, y, bz) * 0.5);
        }
    }

    /** The parts of a hoodoo field that can reach one column (see {@link HoodooField#column}). */
    public record Column(Hoodoo[] pillars, Fin[] fins) {
    }

    /**
     * One hoodoo of a field: a beaded pillar worn to a point.
     * <p>
     * Up to its shoulder the pillar is a column that narrows a little with height; through the
     * taper above it the radius falls to nothing at the tip as {@code (1 - s^round)^point}, where
     * {@code s} runs from 0 at the shoulder to 1 at the tip. A higher {@code round} keeps the
     * shoulder full for longer; a {@code point} above 1 draws the tip out into a needle, one below
     * 1 leaves it blunt. The whole pillar leans a little and the taper bends further, towards an
     * offset tip, so one face of the point is steeper than the other. Around the axis the radius
     * swells into vertical ribs and flutes, with broad bulges and hollows on top, so no pillar is a
     * turned cone and the point comes out jagged. The beds' beads fade out through the taper, and
     * from a little below the shoulder up the radius never grows again, so there the beds wear
     * into steps: a hard bed bulging over a soft one under the point would read as a small cap
     * on a neck.
     * <p>
     * The spine, one block per layer from the foot to the tip, joined face to face, is always
     * rock, so the noise can fray the point but never cut it off.
     */
    public static final class Hoodoo {
        /** Largest relative swelling of the broad bulges. */
        private static final double BULGE = 0.16;
        /** Least widening of the point per layer below the tip, in blocks of radius... */
        private static final double TIP_SLOPE = 0.4;
        /** ...up to this radius, where a layer holds a block and its neighbours. */
        private static final double TIP_FLOOR = 1.3;
        public final int x;
        public final int z;
        public final int base;
        public final double height;
        /** Layer of the tip block, counted from {@link #base}. */
        public final int apex;
        public final int top;
        final double r0;
        final double beadAmp;
        /** Fraction of the height where the taper to the point begins. */
        final double shoulder;
        /** Exponents of the taper (see the class comment). */
        final double round;
        final double point;
        /** Relative depth of the ribs and flutes around the axis. */
        final double flute;
        /** Offset of the tip from the leaning axis, reached through the taper. */
        final double tipX;
        final double tipZ;
        final double leanX;
        final double leanZ;
        final double aspect;
        final double rc;
        final double rs;
        final double flare;
        /** Noise offset of this pillar's ribs and bulges, so neighbours differ. */
        final double off;
        /** Furthest a block above the foot can lie from the axis, the noise included. */
        final double reach;
        final int ext;
        /** Radius of every layer from the base to the tip, before the ribs, bulges and roughness. */
        private final double[] profile;
        double along;
        double finRoll;
        double finHeight;

        /**
         * @param tipU direction and size of the tip offset along x, -1..1 of the largest offset
         * @param tipV the same along z
         * @param bead the field's bed hardness by height (see {@link HoodooField#bead})
         */
        Hoodoo(int x, int z, int base, double height, double r0, double beadAmp, double shoulder, double round, double point,
               double flute, double tipU, double tipV, double leanX, double leanZ, double aspect, double rot, double flare,
               IntToDoubleFunction bead) {
            this.x = x;
            this.z = z;
            this.base = base;
            this.height = height;
            this.r0 = r0;
            this.beadAmp = beadAmp;
            this.shoulder = shoulder;
            this.round = round;
            this.point = point;
            this.flute = flute;
            this.leanX = leanX;
            this.leanZ = leanZ;
            this.aspect = aspect;
            this.rc = Math.cos(rot);
            this.rs = Math.sin(rot);
            this.flare = flare;
            this.apex = Math.max(1, (int) Math.floor(height));
            this.top = base + apex + 1;
            // the tip leans off the axis by up to about half the shoulder's radius, but never so
            // steeply over the taper that the spine would have to step more than a block a layer
            double offMax = Math.min(0.55 * r0 * (1.0 - 0.3 * shoulder), 0.3 * (1.0 - shoulder) * apex);
            this.tipX = tipU * offMax;
            this.tipZ = tipV * offMax;
            this.off = x * 0.173 + z * 0.311;
            this.reach = r0 * (1.0 + beadAmp) * (1.0 + flute) * (1.0 + BULGE) + flare + 1.0;
            double drift = Math.max(Math.abs(leanX), Math.abs(leanZ)) + Math.max(Math.abs(tipX), Math.abs(tipZ));
            this.ext = (int) Math.ceil(Math.max(r0 + flare + 5.0, reach) + drift + 1.0);
            this.profile = new double[apex + 1];
            int calm = (int) Math.floor((shoulder - 0.12) * apex);
            for (int yy = 0; yy <= apex; yy++) {
                double t = rel(yy);
                double s = taperAt(t);
                double r = r0 * (1.0 - 0.3 * t) * (1.0 + beadAmp * bead.applyAsDouble(base + yy) * (1.0 - s));
                if (s > 0.0) {
                    r *= Math.pow(1.0 - Math.pow(s, round), point);
                }
                profile[yy] = yy > 0 && yy > calm ? Math.min(r, profile[yy - 1]) : r;
            }
            // a slender taper would end in a rod of single blocks several layers long: under the
            // tip the radius widens by at least TIP_SLOPE a layer up to TIP_FLOOR (this floor
            // never grows upwards either, so the profile stays free of waists)
            for (int yy = 0; yy <= apex; yy++) {
                profile[yy] = Math.max(profile[yy], Math.min(TIP_SLOPE * (apex - yy), TIP_FLOOR));
            }
        }

        /**
         * Relative height of the layer {@code yy} blocks above the base, 1 at the tip block. The
         * taper is measured to the tip block itself, not to the fractional height, so even a
         * short stump ends in one block rather than in the flat cut of its top layer.
         */
        private double rel(double yy) {
            return clamp(yy / apex, 0.0, 1.0);
        }

        /** Position through the taper at relative height {@code t}: 0 up to the shoulder, 1 at the tip. */
        private double taperAt(double t) {
            return t > shoulder ? (t - shoulder) / (1.0 - shoulder) : 0.0;
        }

        /** X of the axis {@code yy} blocks above the base. */
        private double axisX(double yy) {
            double t = rel(yy);
            double s = taperAt(t);
            return x + leanX * t * t + tipX * s * s;
        }

        /** Z of the axis {@code yy} blocks above the base. */
        private double axisZ(double yy) {
            double t = rel(yy);
            double s = taperAt(t);
            return z + leanZ * t * t + tipZ * s * s;
        }

        /** Whether the pillar can have a block at {@code (bx, y, bz)}: a bound that ignores the noise. */
        boolean near(int bx, int y, int bz) {
            int yy = y - base;
            if (Math.abs(bx - x) > ext || Math.abs(bz - z) > ext || yy > apex) {
                return false;
            }
            double ex = bx - axisX(yy);
            double ez = bz - axisZ(yy);
            // the elliptical distance is never below the plain one (aspect <= 1)
            double bound = yy < 0 ? r0 + flare + 5.0 : reach;
            return ex * ex + ez * ez <= bound * bound;
        }

        /**
         * Whether a block is on the spine: in every layer from the base to the tip, the block
         * nearest the axis and the step towards the next layer's, so the spine is one chain of
         * face-joined blocks however the axis leans (it moves less than a block per layer).
         */
        private boolean spine(int bx, int yy, int bz) {
            if (yy < 0 || yy > apex) {
                return false;
            }
            int ax = (int) Math.round(axisX(yy));
            int az = (int) Math.round(axisZ(yy));
            if (bx == ax && bz == az) {
                return true;
            }
            if (yy == apex) {
                return false;
            }
            int nx = (int) Math.round(axisX(yy + 1));
            int nz = (int) Math.round(axisZ(yy + 1));
            return bx == nx && (bz == az || bz == nz);
        }

        /**
         * Whether this pillar has rock at a block.
         *
         * @param n the field's fine roughness at this block
         */
        boolean solid(int bx, int y, int bz, double n) {
            int yy = y - base;
            if (yy > apex) {
                return false;
            }
            if (spine(bx, yy, bz)) {
                return true;
            }
            double ex = bx - axisX(yy);
            double ez = bz - axisZ(yy);
            double pu = ex * rc + ez * rs;
            double pv = (-ex * rs + ez * rc) / aspect;
            double d = Math.sqrt(pu * pu + pv * pv);
            if (yy < 0) {
                // below the base the foot spreads into a talus cone down to the ground
                return d <= r0 + flare + Math.min(4.0, -yy * 0.6) + n;
            }
            double r = profile[yy];
            if (d <= 1.0 && r >= 1.6) {
                // a sturdy core wherever the pillar is broad, so the noise never leaves a thin stalk
                return true;
            }
            if (d > 1.0E-6) {
                // ribs and flutes: the radius changes with the direction from the axis, slowly
                // with height, so they run up the pillar and break the point into jags. Where
                // the pillar is thin they are weaker, or they would pinch it into a stalk under
                // a lump
                double rib = ROUGH.noise3(pu / d * 0.8 + off, yy * 0.1, pv / d * 0.8 - off);
                double bulge = ROUGH.noise3(bx * 0.09 - off, y * 0.07, bz * 0.09 + off);
                double k = Math.min(1.0, r * 0.5);
                r *= (1.0 + k * flute * rib) * (1.0 + k * BULGE * bulge);
            }
            double f = Math.max(0.0, 1.0 - rel(yy) / 0.18);
            r += flare * f * f;
            // near the tip the fine roughness frays the point rather than burying it in lumps
            return d <= r + n * Math.min(1.0, 0.4 + 0.3 * r);
        }
    }

    /** A low wall that joins two neighbouring hoodoos of a row: the remains of the fin they were cut from. */
    static final class Fin {
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;
        final double ax;
        final double az;
        final double bx;
        final double bz;
        final double topA;
        final double topB;
        final double sag;
        final double width;
        final double baseLow;

        Fin(Hoodoo a, Hoodoo b, double fraction) {
            this.ax = a.x;
            this.az = a.z;
            this.bx = b.x;
            this.bz = b.z;
            this.topA = a.base + a.height * fraction;
            this.topB = b.base + b.height * fraction;
            this.sag = 1.5 + 2.0 * fraction;
            this.width = 0.55 * Math.min(a.r0, b.r0) + 0.6;
            this.baseLow = Math.min(a.base, b.base);
            double reach = width + 3.0;
            this.minX = (int) Math.floor(Math.min(ax, bx) - reach);
            this.maxX = (int) Math.ceil(Math.max(ax, bx) + reach);
            this.minZ = (int) Math.floor(Math.min(az, bz) - reach);
            this.maxZ = (int) Math.ceil(Math.max(az, bz) + reach);
        }

        /** Whether the fin can have a block at {@code (x, y, z)}: a bound that ignores the noise. */
        boolean near(int x, int y, int z) {
            if (y > Math.max(topA, topB) + 1.5) {
                return false;
            }
            double ex = bx - ax;
            double ez = bz - az;
            double len2 = ex * ex + ez * ez;
            double s = len2 < 1.0E-6 ? 0.0 : clamp(((x - ax) * ex + (z - az) * ez) / len2, 0.0, 1.0);
            double px = ax + ex * s - x;
            double pz = az + ez * s - z;
            double reach = width + 2.5;
            return px * px + pz * pz <= reach * reach;
        }

        boolean contains(int x, int y, int z, int ground, double n) {
            double ex = bx - ax;
            double ez = bz - az;
            double len2 = ex * ex + ez * ez;
            if (len2 < 1.0E-6) {
                return false;
            }
            double s = clamp(((x - ax) * ex + (z - az) * ez) / len2, 0.0, 1.0);
            double px = ax + ex * s - x;
            double pz = az + ez * s - z;
            double dist = Math.sqrt(px * px + pz * pz);
            double top = topA + (topB - topA) * s - sag * 4.0 * s * (1.0 - s);
            if (y > top + 1.2 * n || y < ground - 2) {
                return false;
            }
            double rel = clamp((y - baseLow) / Math.max(1.0, top - baseLow), 0.0, 1.0);
            return dist <= width * (1.0 - 0.35 * rel) + 0.8 * n + (y < baseLow ? 1.5 : 0.0);
        }
    }

    /** Whether the model ground around a pillar's foot spans more than 6 blocks (a cliff edge or gully). */
    static boolean steep(TerrainModel model, ColumnSample s, int x, int z, int r, double centre) {
        int[][] offsets = {{r, 0}, {-r, 0}, {0, r}, {0, -r}};
        for (int[] o : offsets) {
            model.sampleInto(x + o[0], z + o[1], s);
            if (Math.abs(s.height - centre) > 6.0) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================================
    // spires and sea stacks
    // =========================================================================================

    /**
     * A spire (a tall, slightly leaning, tapering pillar with irregular ledges and a pointed top)
     * or a sea stack (a broad pillar with steep sides rising from shallow water, a notch cut by the
     * waves at sea level and a flat, grassy top). Both stay within 15 blocks of the site so the
     * chunk holding the site can build them alone.
     * <p>
     * Neither may end in a cap: a sea stack's notch is cut only as deep as the stack is tall above
     * it (under a short head it would leave a cap on a neck), and above the notch the stack's
     * radius never grows again, so no bed and no grassy top juts out over a narrower layer.
     */
    public static final class Spire {
        public static final int MAX_RADIUS = 15;
        public final int x;
        public final int z;
        public final int baseY;
        public final int topY;
        public final boolean seaStack;
        final int seaLevel;
        final double r0;
        final double r1;
        final double leanX;
        final double leanZ;
        final double aspect;
        final double rc;
        final double rs;
        final double off;
        final double period;
        /** Depth of a sea stack's wave notch. */
        final double notch;
        /** First layer of {@link #upper}. */
        private final int upperFrom;
        /** Radius of every layer of a sea stack from above the notch to the top (see {@link #stackRadius}). */
        private final double[] upper;
        /** Blocks that would hang in the air (see {@link #settle}); never built. */
        private Set<Long> floating = Set.of();

        private Spire(TerrainModel.FormationSite site, int groundY, int seaLevel, boolean seaStack) {
            SplittableRandom r = new SplittableRandom(site.seed ^ 0x5D1BEL);
            this.x = site.x;
            this.z = site.z;
            this.seaStack = seaStack;
            this.seaLevel = seaLevel;
            this.baseY = groundY;
            this.topY = seaStack ? Math.max(groundY, seaLevel) + 8 + (int) (site.size * 24.0)
                : groundY + 16 + (int) (site.size * 30.0);
            this.r0 = seaStack ? 4.0 + site.size * 2.5 : 2.8 + site.size * 2.0;
            this.r1 = seaStack ? r0 * (0.7 + r.nextDouble() * 0.15) : 0.9 + r.nextDouble() * 0.7;
            this.leanX = (r.nextDouble() - 0.5) * (seaStack ? 2.0 : 5.0);
            this.leanZ = (r.nextDouble() - 0.5) * (seaStack ? 2.0 : 5.0);
            this.aspect = 0.7 + r.nextDouble() * 0.3;
            double rot = r.nextDouble() * Math.PI;
            this.rc = Math.cos(rot);
            this.rs = Math.sin(rot);
            this.off = (site.seed & 0xFFFF) * 0.613;
            this.period = period(site.geology);
            // the full notch under at least 20 blocks of stack, none under 10 or fewer
            this.notch = 1.2 * clamp((topY - seaLevel - 10) / 10.0, 0.0, 1.0);
            this.upperFrom = Math.max(baseY, seaLevel + 3);
            this.upper = new double[seaStack ? Math.max(0, topY - upperFrom + 1) : 0];
            for (int i = 0; i < upper.length; i++) {
                double radius = stackRadius(upperFrom + i);
                upper[i] = i > 0 ? Math.min(radius, upper[i - 1]) : radius;
            }
        }

        /**
         * Radius of a sea stack at a height before the roughness: tapering from the foot, with
         * jutting and undercut beds, the wave notch and a slightly narrower grassy top.
         */
        private double stackRadius(int y) {
            double t = clamp((double) (y - baseY) / Math.max(1, topY - baseY), 0.0, 1.0);
            // irregular ledges: some beds jut out, others are undercut
            double radius = r0 + (r1 - r0) * t + 0.7 * ROUGH.noise3(off, y * 0.32, 77.0);
            if (y >= seaLevel - 1 && y <= seaLevel + 2) {
                // the notch cut by the waves
                radius -= notch;
            }
            if (y > topY - 2) {
                radius -= 0.8;
            }
            return radius;
        }

        public static Spire plan(TerrainModel.FormationSite site, int groundY, int seaLevel, boolean seaStack) {
            return new Spire(site, groundY, seaLevel, seaStack);
        }

        /**
         * Finds the blocks that the noise would leave unattached on the given terrain (see
         * {@link #floatingBlocks}) so that {@link #material} leaves them out. The chunk that builds
         * the spire calls this with the real ground heights around it.
         */
        public Spire settle(TerrainModel model, IntBinaryOperator groundAt) {
            double[] warp = new double[1];
            int[] at = {Integer.MIN_VALUE, Integer.MIN_VALUE};
            this.floating = floatingBlocks(groundAt, x - MAX_RADIUS, x + MAX_RADIUS, z - MAX_RADIUS, z + MAX_RADIUS, topY + 1, (bx, y, bz, ground) -> {
                if (bx != at[0] || bz != at[1]) {
                    warp[0] = Fades.strataWarp(model, bx, bz);
                    at[0] = bx;
                    at[1] = bz;
                }
                return material(model, bx, y, bz, ground, warp[0]) != AIR;
            });
            return this;
        }

        /**
         * Material at a block: {@link #AIR}, a stop of the rock family, or soil on a sea stack's top.
         *
         * @param ground Y of the top solid block of the column before the spire was built
         * @param warp   the column's {@link Fades#strataWarp}
         */
        public int material(TerrainModel model, int bx, int y, int bz, int ground, double warp) {
            if (!solid(bx, y, bz, ground) || floating.contains(pack(bx, y, bz))) {
                return AIR;
            }
            if (seaStack && y > seaLevel + 3 && y >= topY - 2) {
                // a grassy top: grass where open to the sky, soil under it
                return solid(bx, y + 1, bz, ground) ? DIRT : GRASS;
            }
            return band(model, bx, y, bz, warp, period);
        }

        private boolean solid(int bx, int y, int bz, int ground) {
            if (y > topY || Math.abs(bx - x) > MAX_RADIUS || Math.abs(bz - z) > MAX_RADIUS) {
                return false;
            }
            double t = clamp((double) (y - baseY) / Math.max(1, topY - baseY), 0.0, 1.0);
            double cx = x + leanX * t * t;
            double cz = z + leanZ * t * t;
            double ex = bx - cx;
            double ez = bz - cz;
            double pu = ex * rc + ez * rs;
            double pv = (-ex * rs + ez * rc) / aspect;
            double d = Math.sqrt(pu * pu + pv * pv);
            if (d <= 1.0 && y < topY) {
                // the axis: noise never cuts the pillar
                return true;
            }
            if (y < ground - 3) {
                return false;
            }
            double n = 0.6 * ROUGH.noise3(bx * 0.27 + off, y * 0.3, bz * 0.27);
            double radius;
            if (seaStack) {
                radius = y >= upperFrom ? upper[y - upperFrom] : stackRadius(y);
            } else {
                // irregular ledges: some beds jut out
                double ledge = ROUGH.noise3(off, y * 0.32, 77.0);
                radius = r0 + (r1 - r0) * Math.pow(t, 0.8) + 0.45 * Math.max(0.0, ledge) * (1.0 - t);
                if (t > 0.85) {
                    // a pointed top
                    radius *= 1.0 - (t - 0.85) / 0.15 * 0.6;
                }
            }
            if (y < baseY + 2) {
                // a talus foot, spreading down to the ground
                radius += 1.2 + Math.min(3.0, (baseY + 2 - y) * 0.5);
            }
            return d <= radius + n;
        }
    }
}
