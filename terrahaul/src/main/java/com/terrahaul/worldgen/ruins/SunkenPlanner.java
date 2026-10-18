package com.terrahaul.worldgen.ruins;

import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Plans drowned stonework on mid-depth sea floors, modelled on real submerged sites (Pavlopetri,
 * Baiae, Heracleion): instead of vanilla's small boxy rooms, whole pieces of a sunken town.
 * <ul>
 *   <li>{@link SunkenRuin#COLONNADE}: a temple on a stepped platform, a ring of columns with
 *       base, shaft and capital around a walled cella; toppled columns lie as rows of drums;</li>
 *   <li>{@link SunkenRuin#GATE}: a stretch of city wall with a round arch between two towers;</li>
 *   <li>{@link SunkenRuin#QUARTER}: house foundations one to three courses high along a paved
 *       street, with amphorae and a cistern;</li>
 *   <li>{@link SunkenRuin#TOWER}: the stump of a round tower, collapsed to one side into a fan
 *       of rubble.</li>
 * </ul>
 * Everything is partly buried: sand drifts against the walls on the lee side of a current, and
 * the stone is overgrown by seagrass and kelp in cool seas, by coral fans, sea pickles and
 * prismarine encrustation in warm ones. Cells are never cleared, so the ruin never leaves air
 * pockets: open space keeps the sea water, and buried parts stay buried.
 */
public final class SunkenPlanner {
    /** How far debris and drifts reach beyond the layout. */
    static final int REACH = 7;
    private static final SimplexNoise NOISE = new SimplexNoise(0x5EA51DEL);

    private final SunkenRuin site;
    private final SplittableRandom rnd;
    private final Canvas c;
    private final double decay;
    private final double o1;
    private final double o2;
    /** Direction the current comes from; drifts pile up on the far (lee) side of walls. */
    private final int current;
    private final int maxGrowth;

    private SunkenPlanner(SunkenRuin site) {
        this.site = site;
        this.rnd = new SplittableRandom(site.seed);
        this.decay = site.decay;
        this.o1 = SeedMixer.unit(site.seed * 43L) * 200.0;
        this.o2 = SeedMixer.unit(site.seed * 47L) * 200.0;
        this.current = (int) (SeedMixer.unit(SeedMixer.mix(site.seed + 19)) * 4);
        this.maxGrowth = Math.max(0, site.depth - 3);
        this.c = new Canvas(-REACH, -REACH, site.sizeU + REACH, site.sizeV + REACH, 0, Math.min(18, Math.max(4, site.depth - 2)));
    }

    /** Layout size {sizeU, sizeV, height} of a kind, from the site seed. */
    static int[] size(int kind, long seed) {
        SplittableRandom r = new SplittableRandom(seed ^ 0x51E5L);
        return switch (kind) {
            case SunkenRuin.COLONNADE -> new int[]{13 + 2 * r.nextInt(3), 9 + 2 * r.nextInt(2), 10};
            case SunkenRuin.GATE -> new int[]{21 + 2 * r.nextInt(3), 7, 13};
            case SunkenRuin.QUARTER -> new int[]{19 + 2 * r.nextInt(3), 17, 4};
            default -> new int[]{9, 9, 10};
        };
    }

    public static RuinPlan plan(SunkenRuin site) {
        return new SunkenPlanner(site).build();
    }

    private RuinPlan build() {
        switch (site.kind) {
            case SunkenRuin.COLONNADE -> colonnade();
            case SunkenRuin.GATE -> gate();
            case SunkenRuin.QUARTER -> quarter();
            default -> tower();
        }
        burial();
        growth();
        archaeology();
        RuinPlan plan = c.toPlan(site.frame, RuinPlan.ABSOLUTE, site.floorY, site.materials, true);
        plan.prune();
        return plan;
    }

    private double n2(double a, double b) {
        return NOISE.noise2(a + o1, b + o2);
    }

    private double n3(double a, double b, double cc) {
        return NOISE.noise3(a + o2, b + o1, cc);
    }

    private boolean chance(double p) {
        return rnd.nextDouble() < p;
    }

    /** Fills a column of the layout's floor from the actual sea floor up to the floor course. */
    private void foot(int u, int v, RuinPart part) {
        c.setFill(u, v, RuinCell.of(RuinPart.MASONRY));
        c.set(u, 0, v, RuinCell.of(part));
    }

    // =========================================================================================
    // Colonnade
    // =========================================================================================

    private void colonnade() {
        int lx = site.sizeU;
        int lz = site.sizeV;
        // the stylobate: a solid course, then paving with a ring of steps around it
        for (int u = 0; u < lx; u++) {
            for (int v = 0; v < lz; v++) {
                foot(u, v, RuinPart.MASONRY);
                boolean edge = u == 0 || v == 0 || u == lx - 1 || v == lz - 1;
                if (!edge) {
                    c.set(u, 1, v, RuinCell.of(RuinPart.PAVING));
                } else if (n2(u * 0.5, v * 0.5) + 0.5 > decay * 0.9) {
                    int inward = v == 0 ? RuinCell.SOUTH : v == lz - 1 ? RuinCell.NORTH : u == 0 ? RuinCell.EAST : RuinCell.WEST;
                    c.set(u, 1, v, RuinCell.of(RuinPart.TRIM_STAIR, inward));
                }
            }
        }
        int hc = 5 + rnd.nextInt(3);
        List<int[]> columns = new ArrayList<>();
        for (int u = 1; u <= lx - 2; u += 2) {
            columns.add(new int[]{u, 1, RuinCell.NORTH});
            columns.add(new int[]{u, lz - 2, RuinCell.SOUTH});
        }
        for (int v = 3; v <= lz - 4; v += 2) {
            columns.add(new int[]{1, v, RuinCell.WEST});
            columns.add(new int[]{lx - 2, v, RuinCell.EAST});
        }
        int top = 2 + hc;
        boolean[] full = new boolean[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            int[] col = columns.get(i);
            full[i] = column(col[0], col[1], col[2], hc);
        }
        // architrave beams between neighbouring columns that still stand to full height
        for (int i = 0; i < columns.size(); i++) {
            for (int j = i + 1; j < columns.size(); j++) {
                int[] a = columns.get(i);
                int[] b = columns.get(j);
                boolean neighbours = a[1] == b[1] && Math.abs(a[0] - b[0]) == 2 || a[0] == b[0] && Math.abs(a[1] - b[1]) == 2;
                if (!neighbours || !full[i] || !full[j] || !chance(0.75)) {
                    continue;
                }
                int du = Integer.signum(b[0] - a[0]);
                int dv = Integer.signum(b[1] - a[1]);
                int axis = du != 0 ? RuinCell.AXIS_X : RuinCell.AXIS_Z;
                for (int k = 0; k <= 2; k++) {
                    c.set(a[0] + du * k, top + 1, a[1] + dv * k, RuinCell.withAxis(RuinCell.of(RuinPart.TRIM), axis));
                }
            }
        }
        // the cella: a walled inner room with a doorway at the front
        int cu0 = 4;
        int cu1 = lx - 5;
        int cv0 = 3;
        int cv1 = lz - 4;
        if (cu1 - cu0 >= 2 && cv1 - cv0 >= 2) {
            room(cu0, cv0, cu1, cv1, 2, 4, (cu0 + cu1) / 2, true);
            if (chance(0.35)) {
                chest((cu0 + cu1) / 2, 2, cv1 - 1, RuinCell.NORTH, "minecraft:chests/underwater_ruin_big");
            }
        }
    }

    /**
     * One column: base, shaft and capital. Returns whether it still stands to full height; a
     * toppled column leaves a stub, its drums lying in a row away from the temple.
     */
    private boolean column(int u, int v, int out, int hc) {
        c.set(u, 2, v, RuinCell.of(RuinPart.COLUMN_BASE));
        double r1 = rnd.nextDouble();
        double r2 = rnd.nextDouble();
        if (r1 < 0.08 + 0.45 * decay * decay) {
            int stub = rnd.nextInt(2);
            for (int y = 3; y < 3 + stub; y++) {
                c.set(u, y, v, RuinCell.of(RuinPart.PILLAR));
            }
            int fall = chance(0.75) ? out : RuinCell.clockwise(out + (chance(0.5) ? 0 : 2));
            int du = RuinCell.DX[fall];
            int dv = RuinCell.DZ[fall];
            int axis = du != 0 ? RuinCell.AXIS_X : RuinCell.AXIS_Z;
            int len = hc - 1 - stub;
            int pos = 1;
            for (int k = 0; k < len; k++) {
                int x = u + du * pos;
                int z = v + dv * pos;
                if (onPlatform(x, z)) {
                    c.add(x, 2, z, RuinCell.withAxis(RuinCell.of(RuinPart.PILLAR), axis));
                } else {
                    c.decal(x, z, 1, RuinCell.withAxis(RuinCell.of(RuinPart.PILLAR), axis));
                }
                // drums break apart and roll a little
                pos += chance(0.3) ? 2 : 1;
            }
            int x = u + du * pos;
            int z = v + dv * pos;
            if (onPlatform(x, z)) {
                c.add(x, 2, z, RuinCell.of(RuinPart.CAPITAL));
            } else {
                c.decal(x, z, 1, RuinCell.of(RuinPart.CAPITAL));
            }
            return false;
        }
        int h = Math.max(2, (int) Math.round(hc * (1.0 - 0.6 * decay * r2)));
        boolean intact = r2 < 0.45;
        if (intact) {
            h = hc;
        }
        for (int y = 3; y <= 1 + h; y++) {
            c.set(u, y, v, RuinCell.of(RuinPart.PILLAR));
        }
        if (h == hc) {
            c.set(u, 2 + hc, v, RuinCell.of(RuinPart.CAPITAL));
            return true;
        }
        return false;
    }

    private boolean onPlatform(int u, int v) {
        return u >= 1 && v >= 1 && u <= site.sizeU - 2 && v <= site.sizeV - 2;
    }

    /**
     * A rectangular room of masonry whose walls broke down along the perimeter like the houses'
     * (smooth loss plus a breach), with a doorway on its front wall.
     */
    private void room(int u0, int v0, int u1, int v1, int y0, int hw, int doorU, boolean front) {
        int w = u1 - u0 + 1;
        int d = v1 - v0 + 1;
        int per = 2 * (w + d) - 4;
        int[] pu = new int[per];
        int[] pv = new int[per];
        int k = 0;
        for (int u = 0; u < w; u++) {
            pu[k] = u0 + u;
            pv[k++] = v0;
        }
        for (int v = 1; v < d; v++) {
            pu[k] = u1;
            pv[k++] = v0 + v;
        }
        for (int u = w - 2; u >= 0; u--) {
            pu[k] = u0 + u;
            pv[k++] = v1;
        }
        for (int v = d - 2; v >= 1; v--) {
            pu[k] = u0;
            pv[k++] = v0 + v;
        }
        double phase = rnd.nextDouble() * 100.0;
        int breach = rnd.nextInt(per);
        for (int i = 0; i < per; i++) {
            double n = 0.5 + 0.55 * n2(i / 5.0 + phase, 7.0) + 0.25 * n2(i / 2.0 - phase, 3.0);
            int h = (int) Math.round(hw * (1.0 - decay * smoothstep(0.3, 0.7, n)));
            int dist = Math.min(Math.abs(i - breach), per - Math.abs(i - breach));
            if (decay > 0.5) {
                h = Math.min(h, (int) Math.floor(1.3 * Math.max(0, dist - 1)));
            }
            h = Math.max(1, Math.min(hw, h));
            boolean door = front && pv[i] == v0 && pu[i] == doorU;
            for (int y = 0; y < h; y++) {
                if (door && y < 3) {
                    continue;
                }
                c.set(pu[i], y0 + y, pv[i], RuinCell.of(RuinPart.MASONRY));
            }
            if (h >= 2 && chance(0.25)) {
                int top = y0 + h - 1;
                if (!door && RuinCell.part(c.get(pu[i], top, pv[i])) == RuinPart.MASONRY) {
                    c.set(pu[i], top, pv[i], RuinCell.of(RuinPart.MASONRY_SLAB));
                }
            }
        }
    }

    // =========================================================================================
    // City gate
    // =========================================================================================

    private void gate() {
        int len = site.sizeU;
        int hw = 9 + rnd.nextInt(3);
        int span = chance(0.5) ? 7 : 5;
        int cu = len / 2;
        double radius = span / 2.0 + 0.5;
        int pier = 3 + rnd.nextInt(2);
        int crown = pier + (int) Math.ceil(radius);
        // wall heights along the wall: smooth loss, but the arch's piers stand
        int[] h = new int[len];
        for (int u = 0; u < len; u++) {
            double n = 0.5 + 0.55 * n2(u / 5.0, 21.0) + 0.25 * n2(u / 2.0, 33.0);
            h[u] = Math.max(2, (int) Math.round(hw * (1.0 - decay * 0.8 * smoothstep(0.3, 0.7, n))));
        }
        // the arch and its piers are the strongest part of the wall; it falls only in old ruins
        boolean archStands = decay < 0.6 || chance(0.45);
        for (int u = cu - span / 2 - 2; u <= cu + span / 2 + 2; u++) {
            if (u >= 0 && u < len && (archStands || Math.abs(u - cu) > span / 2)) {
                h[u] = Math.max(h[u], crown + 2);
            }
        }
        for (int u = 0; u < len; u++) {
            for (int v = 2; v <= 4; v++) {
                foot(u, v, RuinPart.MASONRY);
                boolean opening = Math.abs(u - cu) <= span / 2;
                for (int y = 1; y < h[u]; y++) {
                    double du = u - cu;
                    double dy = y - pier;
                    boolean inArch = opening && (y <= pier || du * du + dy * dy <= radius * radius);
                    if (inArch) {
                        continue;
                    }
                    if (opening && !archStands && y > pier) {
                        continue;
                    }
                    c.set(u, y, v, RuinCell.of(RuinPart.MASONRY));
                }
                // battlements along the top of intact stretches
                if (h[u] >= hw - 1 && (u & 1) == 0 && !opening) {
                    c.set(u, h[u], v, RuinCell.of(v == 3 ? RuinPart.MASONRY : RuinPart.MASONRY_WALL));
                }
            }
        }
        // smooth the arch's underside with upside-down stairs where the curve cuts a block
        for (int u = cu - span / 2; u <= cu + span / 2 && archStands; u++) {
            for (int y = pier + 1; y <= crown; y++) {
                double du = u - cu;
                double dy = y - pier;
                boolean open = du * du + dy * dy <= radius * radius;
                boolean above = (du * du + (dy + 1) * (dy + 1)) > radius * radius;
                if (open && above && Math.abs(du) >= 1) {
                    for (int v = 2; v <= 4; v++) {
                        c.set(u, y, v, RuinCell.of(RuinPart.TRIM_STAIR, du < 0 ? RuinCell.WEST : RuinCell.EAST) | RuinCell.TOP);
                    }
                }
            }
        }
        if (!archStands) {
            // the fallen arch lies in heaps on both sides of the gateway
            for (int k = 0; k < 6 + rnd.nextInt(5); k++) {
                int u = cu - span / 2 + rnd.nextInt(span);
                int v = chance(0.5) ? rnd.nextInt(2) : 5 + rnd.nextInt(2);
                c.decal(u, v, 1, chance(0.5) ? RuinCell.of(RuinPart.MASONRY) : RuinCell.of(RuinPart.TRIM_STAIR, rnd.nextInt(4)));
            }
        }
        // towers at both ends: hollow, slit windows, a chest at the foot of one
        tower(0, 0, 5, hw + 2);
        tower(len - 5, 0, 5, hw + 2);
        if (chance(0.25)) {
            chest(2, 1, 2, RuinCell.SOUTH, "minecraft:chests/underwater_ruin_big");
        }
        // the road through the gate
        for (int v = -3; v <= 9; v++) {
            for (int u = cu - span / 2; u <= cu + span / 2; u++) {
                if (v >= 2 && v <= 4) {
                    c.set(u, 0, v, RuinCell.of(RuinPart.PAVING));
                } else if (n2(u * 0.4 + 7.0, v * 0.4) > -0.2) {
                    c.decal(u, v, 0, RuinCell.of(RuinPart.PAVING));
                }
            }
        }
        // talus below collapsed stretches
        for (int u = 0; u < len; u++) {
            int lost = hw - h[u];
            if (lost >= 3) {
                for (int v : new int[]{1, 5}) {
                    if (chance(0.6)) {
                        c.decal(u, v, 1, RuinCell.of(RuinPart.MASONRY));
                    }
                    if (lost >= 5 && chance(0.4)) {
                        c.decal(u, v == 1 ? 0 : 6, 1, RuinCell.of(RuinPart.MASONRY_SLAB));
                    }
                }
            }
        }
    }

    /** A hollow square tower with slit windows and a broken top. */
    private void tower(int u0, int v0, int size, int ht) {
        for (int u = u0; u < u0 + size; u++) {
            for (int v = v0; v < v0 + size; v++) {
                boolean edge = u == u0 || v == v0 || u == u0 + size - 1 || v == v0 + size - 1;
                foot(u, v, RuinPart.MASONRY);
                if (!edge) {
                    c.set(u, 0, v, RuinCell.of(RuinPart.PAVING));
                    for (int y = 1; y <= ht; y++) {
                        c.set(u, y, v, 0);
                    }
                    continue;
                }
                double n = n2(u / 3.0 + 50.0, v / 3.0);
                int h = Math.max(2, (int) Math.round(ht * (1.0 - decay * 0.7 * smoothstep(-0.3, 0.6, n))));
                boolean corner = (u == u0 || u == u0 + size - 1) && (v == v0 || v == v0 + size - 1);
                boolean slitColumn = !corner && (u == u0 + size / 2 || v == v0 + size / 2);
                for (int y = 1; y < h; y++) {
                    if (slitColumn && (y == 4 || y == 8)) {
                        continue;
                    }
                    c.set(u, y, v, RuinCell.of(corner ? RuinPart.TRIM : RuinPart.MASONRY));
                }
            }
        }
    }

    // =========================================================================================
    // Drowned quarter
    // =========================================================================================

    private void quarter() {
        int len = site.sizeU;
        int s0 = 7;
        int s1 = 9;
        // the street: paved, mostly covered in sand
        for (int u = 0; u < len; u++) {
            for (int v = s0; v <= s1; v++) {
                foot(u, v, RuinPart.PAVING);
                if (n2(u * 0.3, v * 0.3 + 40.0) > 0.45) {
                    c.set(u, 1, v, RuinCell.of(RuinPart.SEDIMENT));
                } else if (chance(0.2)) {
                    c.set(u, 1, v, RuinCell.of(RuinPart.TRIM_SLAB));
                }
            }
        }
        // houses on both sides, doorways onto the street
        for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
            boolean north = sideIndex == 0;
            int u = rnd.nextInt(3);
            int rooms = 0;
            while (u < len - 4 && rooms < 3) {
                int w = 5 + rnd.nextInt(3);
                int d = 5 + rnd.nextInt(3);
                int u1 = Math.min(len - 1, u + w - 1);
                if (u1 - u < 3) {
                    break;
                }
                int v0 = north ? s0 - d : s1 + 1;
                int v1 = north ? s0 - 1 : s1 + d;
                for (int x = u; x <= u1; x++) {
                    for (int v = v0; v <= v1; v++) {
                        foot(x, v, RuinPart.PAVING);
                    }
                }
                int door = u + 1 + rnd.nextInt(Math.max(1, u1 - u - 1));
                roomWithDoor(u, v0, u1, v1, 1, 2 + rnd.nextInt(3), door, north ? v1 : v0);
                // amphorae inside, some broken
                int pots = 1 + rnd.nextInt(3);
                for (int p = 0; p < pots; p++) {
                    int pu = u + 1 + rnd.nextInt(Math.max(1, u1 - u - 1));
                    int pv = v0 + 1 + rnd.nextInt(Math.max(1, v1 - v0 - 1));
                    if (c.get(pu, 1, pv) == 0) {
                        c.set(pu, 1, pv, RuinCell.of(RuinPart.DECORATED_POT, rnd.nextInt(4)) | (chance(0.5) ? RuinCell.FLAG : 0));
                    }
                }
                if (chance(0.25)) {
                    int pu = u + 1 + rnd.nextInt(Math.max(1, u1 - u - 1));
                    int pv = north ? v0 + 1 : v1 - 1;
                    if (c.get(pu, 1, pv) == 0) {
                        chest(pu, 1, pv, north ? RuinCell.SOUTH : RuinCell.NORTH, "minecraft:chests/underwater_ruin_small");
                    }
                }
                rooms++;
                u = u1 + 1 + rnd.nextInt(3);
            }
        }
        // a cistern at one end of the street
        int cu = chance(0.5) ? 1 : len - 2;
        for (int du = -1; du <= 1; du++) {
            for (int dv = -1; dv <= 1; dv++) {
                if (du != 0 || dv != 0) {
                    c.set(cu + du, 1, s0 + 1 + dv, RuinCell.of(RuinPart.MASONRY_WALL));
                } else {
                    c.set(cu, 0, s0 + 1, RuinCell.of(RuinPart.MASONRY));
                    c.set(cu, 1, s0 + 1, 0);
                }
            }
        }
    }

    /** A low ruined house wall around a room, with a doorway on the street side. */
    private void roomWithDoor(int u0, int v0, int u1, int v1, int y0, int hw, int doorU, int doorV) {
        double phase = rnd.nextDouble() * 100.0;
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                boolean edge = u == u0 || v == v0 || u == u1 || v == v1;
                if (!edge) {
                    continue;
                }
                if (u == doorU && v == doorV) {
                    continue;
                }
                double n = 0.5 + 0.6 * n2((u + v) / 4.0 + phase, phase);
                int h = Math.max(1, (int) Math.round(hw * (1.0 - decay * smoothstep(0.35, 0.75, n))));
                for (int y = 0; y < h; y++) {
                    c.set(u, y0 + y, v, RuinCell.of(RuinPart.MASONRY));
                }
            }
        }
    }

    // =========================================================================================
    // Round tower
    // =========================================================================================

    private void tower() {
        int r = 4 - rnd.nextInt(2);
        int thick = r == 4 ? 2 : 1;
        int ht = 7 + rnd.nextInt(4);
        int cu = site.sizeU / 2;
        int cv = site.sizeV / 2;
        double theta0 = rnd.nextDouble() * Math.PI * 2.0;
        for (int u = cu - r; u <= cu + r; u++) {
            for (int v = cv - r; v <= cv + r; v++) {
                double dist = Math.hypot(u - cu, v - cv);
                if (dist > r + 0.4) {
                    continue;
                }
                foot(u, v, RuinPart.MASONRY);
                if (dist < r - thick + 0.4) {
                    c.set(u, 0, v, RuinCell.of(RuinPart.PAVING));
                    continue;
                }
                double theta = Math.atan2(v - cv, u - cu);
                int h = Math.max(1, (int) Math.round(ht * (1.0 - decay * smoothstep(-0.2, 0.8, Math.cos(theta - theta0)))));
                boolean door = v == cv - r && u == cu;
                for (int y = 1; y < h; y++) {
                    if (door && y <= 2 || y == 5 && (u == cu || v == cv) && !door) {
                        continue;
                    }
                    c.set(u, y, v, RuinCell.of(RuinPart.MASONRY));
                }
            }
        }
        // the collapsed side lies in a fan of rubble
        double fx = Math.cos(theta0);
        double fz = Math.sin(theta0);
        for (int k = 1; k <= 6; k++) {
            for (int s = -2; s <= 2; s++) {
                int u = (int) Math.round(cu + fx * (r + k) - fz * s);
                int v = (int) Math.round(cv + fz * (r + k) + fx * s);
                double p = 1.0 - k / 7.0 - Math.abs(s) * 0.15;
                if (!chance(p)) {
                    continue;
                }
                int height = k <= 2 ? 2 : 1;
                for (int y = 1; y <= height; y++) {
                    c.decal(u, v, y, k >= 5 ? RuinCell.of(RuinPart.MASONRY_SLAB) : RuinCell.of(RuinPart.MASONRY));
                }
            }
        }
        if (chance(0.3)) {
            chest(cu, 1, cv + 1, RuinCell.NORTH, "minecraft:chests/underwater_ruin_small");
        }
    }

    // =========================================================================================
    // Burial, sea growth, loot
    // =========================================================================================

    private void chest(int u, int y, int v, int facing, String loot) {
        c.set(u, y, v, RuinCell.of(RuinPart.CHEST, facing));
        c.extras.add(new RuinPlan.Extra(u, y, v, false, loot, rnd.nextLong(), null));
    }

    /**
     * Sand drifts into the ruin: a coherent depth per column, deeper on the lee side, laid only
     * onto the ruin's own floors so it never hangs in the water.
     */
    private void burial() {
        int du = RuinCell.DX[current];
        int dv = RuinCell.DZ[current];
        for (int u = 0; u < site.sizeU; u++) {
            for (int v = 0; v < site.sizeV; v++) {
                int floor = -1;
                for (int y = 0; y <= 2; y++) {
                    RuinPart p = c.part(u, y, v);
                    if (p.structural && p != RuinPart.PILLAR && p != RuinPart.COLUMN_BASE) {
                        floor = y;
                    } else {
                        break;
                    }
                }
                if (floor < 0 || c.part(u, floor, v) == RuinPart.MASONRY && c.part(u, floor + 1, v).structural) {
                    continue;
                }
                // lee: a wall upstream shelters this column
                boolean lee = c.structural(u - du, floor + 1, v - dv) || c.structural(u - 2 * du, floor + 1, v - 2 * dv);
                double depth = 0.2 + 1.7 * n2(u / 7.0 + 90.0, v / 7.0) + (lee ? 0.7 : 0.0);
                int s = Math.min(2, (int) Math.round(depth));
                for (int y = floor + 1; y <= floor + s; y++) {
                    if (c.get(u, y, v) != 0) {
                        break;
                    }
                    c.set(u, y, v, RuinCell.of(RuinPart.SEDIMENT));
                }
            }
        }
        // low drifts around the ruin on the open floor
        for (int u = -3; u < site.sizeU + 3; u++) {
            for (int v = -3; v < site.sizeV + 3; v++) {
                boolean inside = u >= 0 && v >= 0 && u < site.sizeU && v < site.sizeV;
                if (!inside && n2(u / 5.0 + 20.0, v / 5.0) > 0.25) {
                    c.decal(u, v, 1, RuinCell.of(RuinPart.SEDIMENT));
                }
            }
        }
    }

    /** Growth on the stone: kelp and seagrass in cool seas; coral, sea pickles and prismarine in warm ones. */
    private void growth() {
        int pickles = 0;
        int fans = 0;
        int kelp = 0;
        int lichen = 0;
        for (int u = -1; u <= site.sizeU; u++) {
            for (int v = -1; v <= site.sizeV; v++) {
                for (int y = 0; y < c.minY + c.sizeY - 1; y++) {
                    int cell = c.get(u, y, v);
                    RuinPart p = RuinCell.part(cell);
                    if (!p.structural || p == RuinPart.SEDIMENT) {
                        if (p == RuinPart.SEDIMENT && c.get(u, y + 1, v) == 0 && !site.frozen) {
                            double r = rnd.nextDouble();
                            if (r < 0.05) {
                                if (c.get(u, y + 2, v) == 0) {
                                    c.set(u, y + 1, v, RuinCell.of(RuinPart.TALL_SEAGRASS) | RuinCell.SOFT);
                                    c.set(u, y + 2, v, RuinCell.of(RuinPart.TALL_SEAGRASS) | RuinCell.TOP | RuinCell.SOFT);
                                }
                            } else if (r < 0.22) {
                                c.set(u, y + 1, v, RuinCell.of(RuinPart.SEAGRASS) | RuinCell.SOFT);
                            }
                        }
                        continue;
                    }
                    boolean exposedTop = c.get(u, y + 1, v) == 0;
                    // weathering of the stone in coherent patches
                    double n = n3(u / 4.0, y / 4.0, v / 4.0);
                    if (site.warm && exposedTop && n > 0.25 - 0.3 * decay) {
                        c.set(u, y, v, RuinCell.withWeathering(cell, RuinCell.ENCRUSTED));
                    } else if (!site.warm && n + 0.3 * (y <= 2 ? 1 : 0) > 0.15) {
                        c.set(u, y, v, RuinCell.withWeathering(cell, RuinCell.MOSSY));
                    } else if (NOISE.noise3(u / 3.0 - o1, y / 3.0, v / 3.0 + o2) > 0.45) {
                        c.set(u, y, v, RuinCell.withWeathering(cell, RuinCell.CRACKED));
                    }
                    if (exposedTop && y + 1 < maxGrowth && !site.frozen) {
                        if (site.warm && pickles < 12 && chance(0.04)) {
                            c.set(u, y + 1, v, RuinCell.withAux(RuinCell.of(RuinPart.SEA_PICKLE), 1 + rnd.nextInt(4)) | RuinCell.SOFT);
                            pickles++;
                        } else if (!site.warm && kelp < 10 && chance(p == RuinPart.PILLAR || p == RuinPart.CAPITAL ? 0.12 : 0.025)) {
                            int height = 3 + rnd.nextInt(7);
                            int topY = Math.min(y + height, maxGrowth - 1);
                            for (int k = y + 1; k <= topY; k++) {
                                if (c.get(u, k, v) != 0) {
                                    break;
                                }
                                c.set(u, k, v, RuinCell.of(k == topY ? RuinPart.KELP : RuinPart.KELP_PLANT) | RuinCell.SOFT);
                            }
                            kelp++;
                        }
                    }
                    // fans and lichen on open side faces
                    if (p.fullCube() && y >= 1 && y < maxGrowth) {
                        for (int d = 0; d < 4; d++) {
                            int su = u + RuinCell.DX[d];
                            int sv = v + RuinCell.DZ[d];
                            if (c.get(su, y, sv) != 0 || !c.inside(su, y, sv)) {
                                continue;
                            }
                            if (site.warm && fans < 14 && chance(0.06)) {
                                c.set(su, y, sv, RuinCell.withAux(RuinCell.of(RuinPart.CORAL_FAN, d), rnd.nextInt(5)) | RuinCell.SOFT);
                                fans++;
                            } else if (!site.warm && lichen < 6 && chance(0.03)) {
                                c.set(su, y, sv, RuinCell.withAux(RuinCell.of(RuinPart.GLOW_LICHEN), 1 << RuinCell.opposite(d)) | RuinCell.SOFT);
                                lichen++;
                            }
                        }
                    }
                }
            }
        }
        if (site.warm && chance(0.3)) {
            int u = rnd.nextInt(site.sizeU);
            int v = rnd.nextInt(site.sizeV);
            for (int y = 1; y < maxGrowth; y++) {
                if (c.get(u, y, v) == 0 && c.part(u, y - 1, v).structural) {
                    c.set(u, y, v, RuinCell.of(RuinPart.WET_SPONGE));
                    break;
                }
            }
        }
    }

    /** One to three suspicious blocks in the drifts, each resting on stone or sand. */
    private void archaeology() {
        int count = 1 + rnd.nextInt(3);
        String loot = site.warm ? "minecraft:archaeology/ocean_ruin_warm" : "minecraft:archaeology/ocean_ruin_cold";
        for (int i = 0; i < count * 6 && count > 0; i++) {
            int u = rnd.nextInt(site.sizeU);
            int v = rnd.nextInt(site.sizeV);
            for (int y = 3; y >= 1; y--) {
                if (c.part(u, y, v) == RuinPart.SEDIMENT && c.part(u, y - 1, v).structural) {
                    c.set(u, y, v, RuinCell.of(site.warm ? RuinPart.SUSPICIOUS_SAND : RuinPart.SUSPICIOUS_GRAVEL));
                    c.extras.add(new RuinPlan.Extra(u, y, v, false, loot, rnd.nextLong(), null));
                    count--;
                    break;
                }
            }
        }
    }
}
