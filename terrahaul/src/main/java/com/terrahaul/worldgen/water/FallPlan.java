package com.terrahaul.worldgen.water;

import com.terrahaul.worldgen.terrain.TerrainModel;

/**
 * A planned waterfall or forest cascade: a pure description of how it reshapes every column it
 * covers, so each chunk can build its part independently (see {@link FallPlanner}).
 * <p>
 * A waterfall comes down the slope it was planned on rather than being carved into it: a channel
 * over the high ground, a staircase of ledges and falling water fitted to the natural face, a modest
 * irregular plunge pool and an outflow stream to the water it joins, with a narrow, jagged cleft cut
 * beside the water only where the ground stands higher (see {@link FallCourse}). The water itself,
 * every riser included, follows from the levels: the {@link WaterSolver} turns each column next to
 * higher water into falling water.
 * <p>
 * Cascades are chains of two to six pools stepping down a slope, each held by low dams; where a
 * pool meets the next one down the solver builds the travertine-like steps the water spills over.
 */
public final class FallPlan implements WaterPlan {
    public static final int CURTAIN = 0;
    public static final int GORGE = 1;
    public static final int SEA = 2;
    public static final int CASCADE = 3;

    /** Materials of dams, sills and beds. */
    public static final byte MAT_FOREST = 0;
    public static final byte MAT_TRAVERTINE = 1;
    public static final byte MAT_COLD = 2;
    public static final byte MAT_VOLCANIC = 3;

    private final TerrainModel model;
    public final int type;
    public final long seed;
    public final int cellX;
    public final int cellZ;
    public byte material;
    int minX;
    int minZ;
    int maxX;
    int maxZ;

    // ---- waterfalls ---------------------------------------------------------------------------
    public int lipX;
    public int lipZ;
    public int dirX;
    public int dirZ;
    /** Top water block of the channel at the lip. */
    public int top;
    /** Top water block of the plunge pool, or of the sea or lake a fall drops into. */
    public int pool;
    /** Half-width of the widest water on the face. */
    public int halfWidth;
    /** The course of a waterfall's water and the ground it shapes ({@code null} for cascades). */
    FallCourse course;

    // ---- cascades -----------------------------------------------------------------------------
    int tiers;
    double[] tierX;
    double[] tierZ;
    double[] tierDx;
    double[] tierDz;
    double[] tierA;
    double[] tierB;
    int[] tierLevel;
    int[] tierDepth;

    // ---- what the finder reports ----------------------------------------------------------------
    public int viewX;
    public int viewY;
    public int viewZ;
    public int lookX;
    public int lookY;
    public int lookZ;
    public double score;

    FallPlan(TerrainModel model, int type, long seed, int cellX, int cellZ) {
        this.model = model;
        this.type = type;
        this.seed = seed;
        this.cellX = cellX;
        this.cellZ = cellZ;
    }

    @Override
    public int minX() {
        return minX;
    }

    @Override
    public int minZ() {
        return minZ;
    }

    @Override
    public int maxX() {
        return maxX;
    }

    @Override
    public int maxZ() {
        return maxZ;
    }

    /** Height of the fall from the lip's water to the pool's surface. */
    public int drop() {
        return top - pool;
    }

    /** Width of the curtain, or the number of pools of a cascade. */
    public int width() {
        return type == CASCADE ? tiers : 2 * halfWidth + 1;
    }

    public String typeName() {
        if (type == CASCADE) {
            return "cascade";
        }
        String style = switch (course.style) {
            case FallCourse.PLUNGE -> "plunge";
            case FallCourse.TIERED -> "tiered fall";
            case FallCourse.HORSETAIL -> "horsetail";
            default -> "fan of strands";
        };
        return type == SEA ? style + " into the water" : style;
    }

    /** Axis (a unit vector along x or z) the water flows along, for side views. */
    public int[] flowAxis() {
        if (type != CASCADE) {
            return new int[]{dirX, dirZ};
        }
        double dx = tierX[tiers - 1] - tierX[0];
        double dz = tierZ[tiers - 1] - tierZ[0];
        return Math.abs(dx) >= Math.abs(dz) ? new int[]{dx >= 0 ? 1 : -1, 0} : new int[]{0, dz >= 0 ? 1 : -1};
    }

    boolean overlaps(FallPlan o, int margin) {
        return minX - margin <= o.maxX && maxX + margin >= o.minX && minZ - margin <= o.maxZ && maxZ + margin >= o.minZ;
    }

    /**
     * Distance from a column to a waterfall's falling water or plunge pool, for the spray that keeps
     * rock wet and plants green near it.
     */
    public double spray(int x, int z) {
        return course.spray(x, z);
    }

    /** Whether a column lies on a waterfall's face, between the lip and the pool. */
    public boolean onFace(int x, int z) {
        return course.onFace(x, z);
    }

    /** The part of a waterfall a column belongs to: channel, face, pool or outflow (for devtools). */
    public String part(int x, int z) {
        return course.region(x, z);
    }

    @Override
    public boolean shape(int x, int z, int natural, Shape out) {
        out.material = material;
        if (type == CASCADE) {
            out.style = WaterSolver.STYLE_CASCADE;
            return shapeCascade(x, z, natural, out);
        }
        out.style = WaterSolver.STYLE_FALL;
        return shapeFall(x, z, natural, out);
    }

    private boolean shapeFall(int x, int z, int natural, Shape out) {
        return course.shape(x, z, natural, out);
    }

    private boolean shapeCascade(int x, int z, int natural, Shape out) {
        double n = model.detail3(6, x / 5.0, 31.0, z / 5.0);
        // pools first (an upper pool owns the columns it shares with the next), then their rims
        for (int i = 0; i < tiers; i++) {
            double r = tierRadius(i, x, z, n);
            if (r <= 1.0) {
                int g = tierLevel[i] - 2 - (int) Math.round(tierDepth[i] * Math.sqrt(Math.max(0.0, 1.0 - r * r)));
                out.ground = g;
                out.level = tierLevel[i];
                out.role = WaterSolver.ROLE_BED;
                return true;
            }
        }
        for (int i = 0; i < tiers; i++) {
            double r = tierRadius(i, x, z, n);
            double rim = 1.0 + 1.6 / Math.min(tierA[i], tierB[i]);
            if (r <= rim) {
                int hold = tierLevel[i] - 1;
                if (natural >= hold + 2) {
                    return false;
                }
                out.ground = Math.max(natural, hold);
                out.role = WaterSolver.ROLE_DAM;
                return true;
            }
        }
        return false;
    }

    /** Normalised elliptical radius of a column in cascade pool {@code i}. */
    double tierRadius(int i, int x, int z, double n) {
        double dx = x + 0.5 - tierX[i];
        double dz = z + 0.5 - tierZ[i];
        double a = (dx * tierDx[i] + dz * tierDz[i]) / tierA[i];
        double b = (dz * tierDx[i] - dx * tierDz[i]) / tierB[i];
        return Math.sqrt(a * a + b * b) + 0.1 * n;
    }
}
