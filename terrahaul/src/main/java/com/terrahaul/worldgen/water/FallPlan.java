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
 * Cascades are chains of two to eight pools of different sizes and outlines stepping down a slope
 * along a winding course, each spilling one to four blocks into the next over its lip, sometimes
 * split in two by a rock; around them the slope is cut back into uneven, partly rocky banks or
 * built up into a sloping rim (see {@link CascadeLayout}).
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
    /** The course, pools and rocks of a cascade and the ground they shape ({@code null} for waterfalls). */
    CascadeLayout layout;

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
        double[][] c = layout.course();
        double dx = c[c.length - 1][0] - c[0][0];
        double dz = c[c.length - 1][1] - c[0][1];
        return Math.abs(dx) >= Math.abs(dz) ? new int[]{dx >= 0 ? 1 : -1, 0} : new int[]{0, dz >= 0 ? 1 : -1};
    }

    /**
     * The course of a cascade from its head to the lip of its lowest pool, as {@code {x, z}}
     * points, or an empty array for a waterfall.
     */
    public double[][] course() {
        return type == CASCADE ? layout.course() : new double[0][];
    }

    /**
     * Whether two plans come within {@code margin} blocks of each other: their bounds, and for a
     * waterfall only the part of them its course may shape (a long outflow's bounds are mostly
     * open ground). The test is symmetric, so the cells on either side decide alike.
     */
    boolean overlaps(FallPlan o, int margin) {
        if (minX - margin > o.maxX || maxX + margin < o.minX || minZ - margin > o.maxZ || maxZ + margin < o.minZ) {
            return false;
        }
        if (course != null) {
            return course.touches(o, margin);
        }
        return o.course == null || o.course.touches(this, margin);
    }

    /** Whether some part of the plan's own bounds meets the given rectangle. */
    boolean meets(int x0, int z0, int x1, int z1) {
        if (x0 > maxX || x1 < minX || z0 > maxZ || z1 < minZ) {
            return false;
        }
        return course == null || course.meets(x0, z0, x1, z1);
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

    /** Style of a waterfall's water: 0 plunge, 1 tiered, 2 horsetail, 3 fan of strands (for devtools). */
    public int style() {
        return course.style;
    }

    /**
     * The drops of a waterfall's water from the lip down to its pool, ledge by ledge (for devtools):
     * each is the difference of the water's surface between one ledge and the next.
     */
    public int[] drops() {
        FallCourse c = course;
        int[] d = new int[c.treads];
        int prev = c.channelLevel[0];
        for (int i = 1; i <= c.treads; i++) {
            int lv = i >= c.treads ? c.poolLevel : c.treadLevel[i];
            d[i - 1] = prev - lv;
            prev = lv;
        }
        return d;
    }

    /** Rock ribs or outcrops standing in a waterfall's water (for devtools). */
    public int outcrops() {
        return course.rocks;
    }

    /** Whether a waterfall's water ends in a river, lake, the sea or a stream (for devtools). */
    public boolean joinsWater() {
        return course.intoWater || course.outJoins;
    }

    /**
     * Where a waterfall's outflow ends, {@code {x, z, top water block}}, or {@code null} if it drops
     * into the sea or a lake (for devtools).
     */
    public double[] outflowEnd() {
        FallCourse c = course;
        if (c.intoWater || c.outN == 0) {
            return null;
        }
        return new double[]{c.outX[c.outN - 1], c.outZ[c.outN - 1], c.outLevel[c.outN - 1] - 1};
    }

    /** Where a waterfall's water lands: the middle of its plunge pool, {@code {x, z}} (for devtools). */
    public double[] foot() {
        FallCourse c = course;
        return new double[]{c.worldX(c.poolU, c.poolV) + 0.5, c.worldZ(c.poolU, c.poolV) + 0.5};
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
        return layout.shape(x, z, natural, out);
    }
}
