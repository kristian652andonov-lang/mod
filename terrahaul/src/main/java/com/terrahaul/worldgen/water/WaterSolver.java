package com.terrahaul.worldgen.water;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.WaterBody;
import java.util.List;

/**
 * Decides, for one chunk, where surface water goes and how it is held: the water of elevated
 * rivers, lakes, crater lakes and lava pools, mountain streams and forest creeks, and planned
 * waterfalls and cascades. Pure Java, so development tools run exactly the code the game runs.
 * <p>
 * Every decision is made from the terrain model alone (model ground, water bodies, plans), never
 * from blocks, and depends only on data within four columns. The chunk is solved together with a
 * margin of {@value #MARGIN} columns, so neighbouring chunks, which solve the same columns
 * independently, agree exactly along their border.
 * <p>
 * The rules, in order:
 * <ol>
 *   <li>Each column takes the level of the body its model corners are inside (interpolated along
 *       rivers, so their steps fall at block resolution across the channel), of a stream (dug
 *       into the ground) or of a plan, and holds water where its ground is below that level.</li>
 *   <li>Stream columns that would stand above the ground are dropped.</li>
 *   <li>Where water meets water of a higher level, the lower column becomes a step: a sill of
 *       rock up to the upper surface, or a chute of falling water, mixed in coherent runs so a
 *       step reads as a rapid; drops of three blocks or more always fall.</li>
 *   <li>Dry columns next to water above sea level are raised to hold it (banks).</li>
 * </ol>
 * The result keeps the invariants the devtool checks: every source block has solid ground or
 * water below and on all four sides; only falling and flowing water of chutes faces air.
 */
public final class WaterSolver {
    public static final int MARGIN = 5;
    public static final int SIZE = 16 + 2 * MARGIN;
    public static final int NONE = Integer.MIN_VALUE;

    // column kinds
    public static final byte DRY = 0;
    /** Water at sea level, which the water table provides; the solver never banks it. */
    public static final byte SEA = 1;
    public static final byte RIVER = 2;
    public static final byte LAKE = 3;
    public static final byte CRATER = 4;
    public static final byte LAVA = 5;
    public static final byte STREAM = 6;
    /** Water of a plan: a plunge pool, a cascade pool, a waterfall's feeding channel. */
    public static final byte PLAN = 7;

    // steps
    public static final byte FLAT = 0;
    /** Rock up to the upper surface (a small weir in a rapid). */
    public static final byte SILL = 1;
    /** Falling water from the upper surface down to this column's own. */
    public static final byte CHUTE = 2;

    // styles: how the steps of a body look
    public static final byte STYLE_RIVER = 0;
    public static final byte STYLE_LAKE = 1;
    public static final byte STYLE_STREAM = 2;
    public static final byte STYLE_CASCADE = 3;
    public static final byte STYLE_FALL = 4;

    // material roles of shaped ground
    public static final byte ROLE_NATURAL = 0;
    public static final byte ROLE_ROCK = 1;
    public static final byte ROLE_BED = 2;
    public static final byte ROLE_SHORE = 3;
    public static final byte ROLE_DAM = 4;
    public static final byte ROLE_RIB = 5;
    /** A rock or boulder of a cascade, standing in its water or lying on its banks. */
    public static final byte ROLE_BOULDER = 6;

    private static final int N = SIZE * SIZE;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    /** Block coordinates of the window's first column. */
    public int x0;
    public int z0;
    /** Model ground: top solid block of the terrain model. */
    public final int[] natural = new int[N];
    /** Ground after plans and digging (streams, plunge basins), before banks and sills. */
    public final int[] ground = new int[N];
    /** Exclusive top of the water ({@link #NONE}: dry). */
    public final int[] level = new int[N];
    public final byte[] kind = new byte[N];
    public final byte[] style = new byte[N];
    public final byte[] role = new byte[N];
    /** The ground was set by a plan or dug: the column is carved above it and filled below it. */
    public final boolean[] shaped = new boolean[N];
    public final boolean[] rib = new boolean[N];
    /** {@link #FLAT}, {@link #SILL} or {@link #CHUTE}. */
    public final byte[] step = new byte[N];
    /** For steps: the level of the highest neighbouring water. */
    public final int[] upper = new int[N];
    /** Top solid block after banks and sills. */
    public final int[] solidTop = new int[N];
    /** A boulder sits on the sill, one block above the water. */
    public final boolean[] boulder = new boolean[N];
    /** The column belongs to a plan, whose material family is {@link #material}. */
    public final boolean[] planned = new boolean[N];
    public final byte[] material = new byte[N];

    private final WaterPlan.Shape shape = new WaterPlan.Shape();
    private final double[] streamBuf = new double[2];
    private final boolean[] feasible = new boolean[N];
    private final boolean[] feasible2 = new boolean[N];
    private final double[] cornerConts = new double[4];
    private final double[] cornerLevels = new double[4];
    private int seaLevel;

    public static int index(int i, int j) {
        return j * SIZE + i;
    }

    /** Index of block column {@code (x, z)} in this solve's window. */
    public int indexOf(int x, int z) {
        return index(x - x0, z - z0);
    }

    public boolean water(int k) {
        return level[k] != NONE;
    }

    /**
     * Solves the chunk at {@code (chunkX, chunkZ)} with its margin.
     *
     * @param plans the plans whose bounds touch the window
     */
    public void solve(TerrainModel model, int chunkX, int chunkZ, List<? extends WaterPlan> plans) {
        seaLevel = model.seaLevel();
        x0 = chunkX * 16 - MARGIN;
        z0 = chunkZ * 16 - MARGIN;
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++) {
                column(model, index(i, j), x0 + i, z0 + j, plans);
            }
        }
        streams();
        steps(model);
        banks();
    }

    // -----------------------------------------------------------------------------------------
    // 1. levels per column
    // -----------------------------------------------------------------------------------------

    private void column(TerrainModel model, int k, int x, int z, List<? extends WaterPlan> plans) {
        int g = (int) Math.ceil(model.interpolatedHeight(x, z)) - 1;
        natural[k] = g;
        ground[k] = g;
        level[k] = NONE;
        kind[k] = DRY;
        style[k] = STYLE_RIVER;
        role[k] = ROLE_NATURAL;
        shaped[k] = false;
        rib[k] = false;
        step[k] = FLAT;
        upper[k] = NONE;
        boulder[k] = false;
        planned[k] = false;
        material[k] = 0;
        for (int p = 0; p < plans.size(); p++) {
            WaterPlan plan = plans.get(p);
            if (x >= plan.minX() && x <= plan.maxX() && z >= plan.minZ() && z <= plan.maxZ()) {
                shape.reset(g);
                if (plan.shape(x, z, g, shape)) {
                    ground[k] = shape.ground;
                    level[k] = shape.level;
                    style[k] = shape.style;
                    rib[k] = shape.rib;
                    role[k] = shape.role;
                    material[k] = shape.material;
                    planned[k] = true;
                    shaped[k] = true;
                    kind[k] = shape.level == NONE ? DRY : shape.level <= seaLevel ? SEA : PLAN;
                    if (kind[k] == SEA || kind[k] == DRY && ground[k] <= seaLevel - 2) {
                        // carved below sea level: the sea or a lake at sea level fills it
                        kind[k] = SEA;
                        level[k] = seaLevel;
                    }
                    finish(k);
                    return;
                }
            }
        }
        if (!body(model, k, x, z)) {
            stream(model, k, x, z, g);
        }
        if (kind[k] == DRY && g <= seaLevel - 2) {
            kind[k] = SEA;
            level[k] = seaLevel;
        }
        finish(k);
    }

    /** Water only where the ground is below the surface; bodies at sea level are the water table's. */
    private void finish(int k) {
        if (level[k] != NONE && (kind[k] == RIVER || kind[k] == LAKE) && level[k] <= seaLevel) {
            kind[k] = SEA;
            level[k] = seaLevel;
        }
        if (level[k] != NONE && ground[k] + 1 >= level[k]) {
            level[k] = NONE;
            kind[k] = DRY;
        }
    }

    /**
     * Body water from the four model corners around the column. Any corner inside a body counts,
     * so no column next to the body is left as a dry hole below its surface. Along a river the
     * unrounded level is interpolated, so steps fall at block resolution; where corners differ by
     * a big drop (a river falling into a canyon or a lake), the column snaps to the nearest one,
     * and on the low side the bed is dug out below the fall.
     */
    private boolean body(TerrainModel model, int k, int x, int z) {
        int cx = x & ~3;
        int cz = z & ~3;
        double fx = (x - cx) / 4.0;
        double fz = (z - cz) / 4.0;
        double wSum = 0.0;
        double cSum = 0.0;
        double cMin = Double.MAX_VALUE;
        double cMax = -Double.MAX_VALUE;
        double bestW = -1.0;
        int bestKind = WaterBody.NONE;
        boolean anyLava = false;
        boolean anyWater = false;
        double[] conts = cornerConts;
        double[] levels = cornerLevels;
        int n = 0;
        for (int c = 0; c < 4; c++) {
            int ox = c & 1;
            int oz = c >> 1;
            ColumnSample s = model.sample(cx + ox * 4, cz + oz * 4);
            if (!s.bodyInside) {
                continue;
            }
            if (s.bodyKind == WaterBody.LAVA) {
                anyLava = true;
            } else {
                anyWater = true;
            }
        }
        if (!anyLava && !anyWater) {
            return false;
        }
        // lava and water never share a column: the more strongly weighted fluid wins
        boolean lava = anyLava;
        if (anyLava && anyWater) {
            double wl = 0.0;
            double ww = 0.0;
            for (int c = 0; c < 4; c++) {
                ColumnSample s = model.sample(cx + (c & 1) * 4, cz + (c >> 1) * 4);
                double w = ((c & 1) == 1 ? fx : 1.0 - fx) * ((c >> 1) == 1 ? fz : 1.0 - fz);
                if (s.bodyInside) {
                    if (s.bodyKind == WaterBody.LAVA) {
                        wl += w;
                    } else {
                        ww += w;
                    }
                }
            }
            lava = wl > ww;
        }
        for (int c = 0; c < 4; c++) {
            ColumnSample s = model.sample(cx + (c & 1) * 4, cz + (c >> 1) * 4);
            if (!s.bodyInside || (s.bodyKind == WaterBody.LAVA) != lava) {
                continue;
            }
            double w = ((c & 1) == 1 ? fx : 1.0 - fx) * ((c >> 1) == 1 ? fz : 1.0 - fz) + 1.0E-3;
            wSum += w;
            cSum += w * s.bodyLevelCont;
            cMin = Math.min(cMin, s.bodyLevelCont);
            cMax = Math.max(cMax, s.bodyLevelCont);
            conts[n] = s.bodyLevelCont;
            levels[n] = s.bodyLevel;
            n++;
            if (w > bestW) {
                bestW = w;
                bestKind = s.bodyKind;
            }
        }
        double cont = cSum / wSum;
        int lv;
        boolean dig = false;
        if (cMax - cMin < 2.5) {
            // the tolerance keeps equal integer levels exact despite rounding in the average
            lv = (int) Math.floor(cont + 1.0E-6);
        } else {
            double bestD = Double.MAX_VALUE;
            double chosen = levels[0];
            for (int c = 0; c < n; c++) {
                double d = Math.abs(conts[c] - cont);
                if (d < bestD - 1.0E-9 || d < bestD + 1.0E-9 && levels[c] > chosen) {
                    bestD = d;
                    chosen = levels[c];
                }
            }
            lv = (int) chosen;
            for (int c = 0; c < n; c++) {
                dig |= levels[c] >= lv + 3;
            }
        }
        level[k] = lv;
        switch (bestKind) {
            case WaterBody.LAKE -> {
                kind[k] = LAKE;
                style[k] = STYLE_LAKE;
            }
            case WaterBody.CRATER -> {
                kind[k] = CRATER;
                style[k] = STYLE_LAKE;
            }
            case WaterBody.LAVA -> {
                kind[k] = LAVA;
                style[k] = STYLE_LAKE;
            }
            default -> {
                kind[k] = RIVER;
                style[k] = STYLE_RIVER;
            }
        }
        if (dig && !lava && ground[k] > lv - 3) {
            // the foot of a fall into a canyon or a lake: a plunge pool two blocks deep
            ground[k] = lv - 3;
            shaped[k] = true;
            role[k] = ROLE_BED;
        }
        return true;
    }

    /** A stream: water dug into the ground at the level of its foot point. */
    private void stream(TerrainModel model, int k, int x, int z, int g) {
        if (g <= seaLevel) {
            return;
        }
        ColumnSample nc = model.sample((x + 2) & ~3, (z + 2) & ~3);
        double factor = nc.stream;
        if (factor <= 0.0 || nc.lake > 0.0) {
            return;
        }
        double halfWidth = 1.25 + 1.5 * factor;
        model.streamInfo(x, z, streamBuf);
        double d = streamBuf[0];
        int lv = (int) streamBuf[1];
        if (d >= halfWidth || lv <= seaLevel + 1 || g > lv + 3) {
            return;
        }
        int depth = d < halfWidth * 0.5 ? 2 : 1;
        ground[k] = Math.min(g, lv - 1 - depth);
        level[k] = lv;
        kind[k] = STREAM;
        style[k] = STYLE_STREAM;
        role[k] = ROLE_BED;
        shaped[k] = true;
    }

    // -----------------------------------------------------------------------------------------
    // 2. streams that would stand above the ground are dropped
    // -----------------------------------------------------------------------------------------

    private void streams() {
        for (int k = 0; k < N; k++) {
            feasible[k] = kind[k] != STREAM || streamHolds(k, false);
        }
        for (int k = 0; k < N; k++) {
            feasible2[k] = feasible[k] && (kind[k] != STREAM || streamHolds(k, true));
        }
        for (int k = 0; k < N; k++) {
            if (!feasible2[k]) {
                level[k] = NONE;
                kind[k] = DRY;
                ground[k] = natural[k];
                shaped[k] = false;
                role[k] = ROLE_NATURAL;
            }
        }
    }

    /**
     * A stream column holds if its water is at most a block above the natural ground and the dry
     * columns beside it would need banks of at most two blocks; in the second pass the stream
     * columns dropped by the first count as dry.
     */
    private boolean streamHolds(int k, boolean second) {
        int lv = level[k];
        if (natural[k] < lv - 2) {
            return false;
        }
        int i = k % SIZE;
        int j = k / SIZE;
        for (int d = 0; d < 4; d++) {
            int ni = i + DX[d];
            int nj = j + DZ[d];
            if (ni < 0 || nj < 0 || ni >= SIZE || nj >= SIZE) {
                continue;
            }
            int n = index(ni, nj);
            boolean dry = kind[n] == DRY || second && kind[n] == STREAM && !feasible[n];
            if (dry && (kind[n] == STREAM ? natural[n] : ground[n]) < lv - 3) {
                return false;
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------------------------
    // 3. steps between water of different levels
    // -----------------------------------------------------------------------------------------

    private void steps(TerrainModel model) {
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++) {
                int k = index(i, j);
                if (level[k] == NONE) {
                    continue;
                }
                int up = NONE;
                boolean lower = false;
                for (int d = 0; d < 4; d++) {
                    int ni = i + DX[d];
                    int nj = j + DZ[d];
                    if (ni < 0 || nj < 0 || ni >= SIZE || nj >= SIZE) {
                        continue;
                    }
                    int n = index(ni, nj);
                    if (level[n] == NONE || (kind[n] == LAVA) != (kind[k] == LAVA)) {
                        continue;
                    }
                    if (level[n] > level[k]) {
                        up = Math.max(up, level[n]);
                    } else if (level[n] < level[k]) {
                        lower = true;
                    }
                }
                if (up == NONE) {
                    continue;
                }
                upper[k] = up;
                step[k] = chooseStep(model, k, x0 + i, z0 + j, up - level[k], lower);
                if (step[k] == SILL && up - level[k] <= 2
                    && SeedMixer.unit(SeedMixer.hash2(model.siteSeed() ^ 0xB0B0L, x0 + i, z0 + j)) < 0.16) {
                    boulder[k] = true;
                }
            }
        }
    }

    /**
     * Whether a step column is a sill or a chute. Columns that also step down to lower water
     * always pass the water on, and so do tall steps and deep columns; otherwise rivers mix sills
     * and chutes in coherent runs along the step (a rapid) and streams per column (a step pool).
     * A cascade's pools meet only at their lips (its plan keeps them apart elsewhere), so every
     * step of a cascade is water spilling over a lip.
     */
    private byte chooseStep(TerrainModel model, int k, int x, int z, int drop, boolean lower) {
        if (rib[k]) {
            return SILL;
        }
        if (lower || kind[k] == LAVA || upper[k] - 1 - ground[k] > 5) {
            return CHUTE;
        }
        return switch (style[k]) {
            case STYLE_CASCADE -> CHUTE;
            case STYLE_STREAM -> drop <= 2 && SeedMixer.unit(SeedMixer.hash2(model.siteSeed() ^ 0x57EAL, x, z)) < 0.55 ? SILL : CHUTE;
            case STYLE_RIVER -> drop <= 2 && model.detail3(5, x / 6.0, 7.0, z / 6.0) < -0.15 ? SILL : CHUTE;
            default -> CHUTE;
        };
    }

    // -----------------------------------------------------------------------------------------
    // 4. banks
    // -----------------------------------------------------------------------------------------

    private void banks() {
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++) {
                int k = index(i, j);
                if (level[k] != NONE && step[k] != SILL) {
                    solidTop[k] = ground[k];
                    continue;
                }
                int top = step[k] == SILL ? upper[k] - 1 : ground[k];
                int need = holdLevel(i, j);
                solidTop[k] = Math.max(top, need);
            }
        }
    }

    /**
     * Highest top source block of elevated water next to column {@code (i, j)}, which the column
     * must hold ({@link #NONE} if none). Water at sea level is the water table's and needs no bank.
     */
    public int holdLevel(int i, int j) {
        int need = NONE;
        for (int d = 0; d < 4; d++) {
            int ni = i + DX[d];
            int nj = j + DZ[d];
            if (ni < 0 || nj < 0 || ni >= SIZE || nj >= SIZE) {
                continue;
            }
            int n = index(ni, nj);
            if (level[n] != NONE && step[n] != SILL && kind[n] != SEA) {
                need = Math.max(need, level[n] - 1);
            }
        }
        return need;
    }

    /**
     * Lowest water block next to column {@code (i, j)} whose water must be held ({@link #NONE}
     * if none): the bottom of the range a dry column must be solid over.
     */
    public int holdFrom(int i, int j) {
        int from = NONE;
        for (int d = 0; d < 4; d++) {
            int ni = i + DX[d];
            int nj = j + DZ[d];
            if (ni < 0 || nj < 0 || ni >= SIZE || nj >= SIZE) {
                continue;
            }
            int n = index(ni, nj);
            if (level[n] != NONE && step[n] != SILL && kind[n] != SEA) {
                int b = ground[n] + 1;
                from = from == NONE ? b : Math.min(from, b);
            }
        }
        return from;
    }
}
