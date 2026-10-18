package com.terrahaul.worldgen.water;

import com.terrahaul.worldgen.noise.TMath;
import com.terrahaul.worldgen.terrain.TerrainModel;

/**
 * A planned waterfall or forest cascade: a pure description of how it reshapes every column it
 * covers, so each chunk can build its part independently (see {@link FallPlanner}).
 * <p>
 * Waterfalls are described in a frame anchored at the lip: {@code u} runs along the direction of
 * the fall (the lip column is {@code u = 0}, the curtain falls in {@code u = 1}) and {@code v}
 * across it. A stream channel runs over the high ground to the lip; in front of the lip a
 * horseshoe-shaped bowl is cut into the slope, with a vertical headwall behind the curtain, a
 * plunge pool deepest under the curtain, a shore, and steep rock walls rising to the natural
 * ground. Where the foot of the slope is far away, the bowl opens into a slot (a gorge) down to
 * it; where the foot is the sea or a lake, a notch through the beach lets the fall drop straight
 * into it. Wide curtains are split into strands by ribs of rock. The water itself, the curtain
 * included, follows from the levels: the {@link WaterSolver} turns every pool column next to the
 * channel into falling water.
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

    /** How far beyond the shore the walls of a bowl are cut back. */
    static final int WALL_REACH = 30;

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
    /** The curtain spans {@code v} in {@code [-halfWidth, halfWidth]}. */
    public int halfWidth;
    int channel;
    double ru;
    double rv;
    double uc;
    int depth;
    double shoreW;
    double wallSlope;
    int slotEnd;
    double slotHalf;
    final boolean[] ribs = new boolean[65];

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
        return switch (type) {
            case CURTAIN -> "curtain";
            case GORGE -> "gorge";
            case SEA -> "into the water";
            default -> "cascade";
        };
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

    /** Along-fall coordinate of a column. */
    int u(int x, int z) {
        return (x - lipX) * dirX + (z - lipZ) * dirZ;
    }

    /** Across-fall coordinate of a column. */
    int v(int x, int z) {
        return (z - lipZ) * dirX - (x - lipX) * dirZ;
    }

    boolean isRib(int v) {
        return v > -32 && v < 32 && ribs[v + 32];
    }

    /** Half-width of the feeding channel, narrowing upstream from the curtain's width. */
    int channelHalf(int u) {
        return Math.max(0, (int) Math.round(halfWidth * (1.0 - 0.5 * (-u) / Math.max(1, channel))));
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
        int u = u(x, z);
        int v = v(x, z);
        int av = Math.abs(v);
        if (u <= 0) {
            // the stream channel over the high ground, and banks where the ground beside it or
            // beside the lip is lower than the water
            if (u < -channel - 1) {
                return false;
            }
            int ch = channelHalf(u);
            if (u >= -channel && av <= ch) {
                out.ground = top - 2;
                out.level = top + 1;
                out.role = WaterSolver.ROLE_BED;
                return true;
            }
            boolean bank = av <= ch + 1 || u >= -2 && av <= halfWidth + 2;
            if (bank && natural < top) {
                out.ground = top;
                out.role = WaterSolver.ROLE_NATURAL;
                return true;
            }
            return false;
        }
        double side = Math.max(0, av - halfWidth - 1);
        if (u < 0.5 + 0.12 * side * side) {
            // behind the horseshoe-shaped headwall: the cliff stays as it is
            return false;
        }
        double n = model.detail3(6, x / 7.0, 13.0, z / 7.0);
        if (type == SEA) {
            return shapeSeaFall(u, av, natural, n, out);
        }
        // the plunge pool spans the whole curtain and rounds off beside it; deepest under the curtain
        double vv = Math.max(0, av - halfWidth) / Math.max(1.5, rv - halfWidth);
        double uu = (u - uc) / ru;
        double rn = Math.sqrt(uu * uu + vv * vv);
        if (rn > 0.7) {
            rn += 0.12 * n;
        }
        if (u == 1 && av <= halfWidth || rn <= 1.0) {
            double ud = (u - 1.5) / (1.3 * ru);
            double rd = Math.sqrt(ud * ud + vv * vv);
            int g = pool - (int) Math.round(depth * Math.sqrt(Math.max(0.0, 1.0 - rd * rd)));
            out.ground = Math.min(g, pool - 1);
            out.level = pool + 1;
            out.role = WaterSolver.ROLE_BED;
            out.rib = u == 1 && isRib(v);
            return true;
        }
        double edge = Math.min(ru, rv);
        double shore = 1.0 + shoreW / edge;
        if (rn <= shore) {
            out.ground = pool + 1;
            out.role = WaterSolver.ROLE_SHORE;
            return true;
        }
        // walls of the bowl, and of the gorge where the bowl opens into a winding slot
        double wall = Double.MAX_VALUE;
        double dist = (rn - shore) * edge;
        if (dist <= WALL_REACH) {
            wall = pool + 1 + rockWall(wallSlope * dist, x, z, n);
        }
        if (u >= uc && u <= slotEnd) {
            double sw = slotHalf + 1.2 * n;
            double dv = Math.abs(v - slotOffset(u));
            if (dv <= sw) {
                if (natural <= pool + 1) {
                    return false;
                }
                out.ground = pool + 1;
                out.role = WaterSolver.ROLE_SHORE;
                return true;
            }
            wall = Math.min(wall, pool + 1 + rockWall(wallSlope * (dv - sw), x, z, n));
        }
        if (wall < natural) {
            out.ground = (int) Math.floor(wall);
            out.role = WaterSolver.ROLE_ROCK;
            return true;
        }
        return false;
    }

    /**
     * Height of a carved rock wall above the pool's shore for a smooth rise {@code h}: benches a
     * couple of blocks wide between near-vertical risers of about six blocks, displaced by noise,
     * the way rock walls of a plunge basin or gorge break up, instead of one even slope.
     */
    private double rockWall(double h, int x, int z, double n) {
        double n2 = model.detail3(5, x / 9.0, 21.0, z / 9.0);
        return Math.max(0.0, TMath.terrace(Math.max(0.0, h) + 2.5 * n2 + 1.5 * n, 7.0, 0.72));
    }

    /** Sideways offset of a gorge's slot from the fall's axis: the slot winds a little. */
    double slotOffset(int u) {
        double cx = lipX + u * dirX;
        double cz = lipZ + u * dirZ;
        return 3.0 * model.detail3(7, cx / 15.0, 3.0, cz / 15.0) * TMath.smoothstep(uc, uc + 8.0, u);
    }

    /** A fall into the sea or a lake: a notch through the beach or talus down to the water. */
    private boolean shapeSeaFall(int u, int av, int natural, double n, Shape out) {
        if (u > slotEnd + 1) {
            return false;
        }
        double sw = halfWidth + 1 + 0.8 * n;
        if (av <= sw || u == 1 && av <= halfWidth) {
            if (natural <= pool - 1 && u > 1) {
                return false;
            }
            out.ground = Math.min(natural, pool - 1);
            out.level = pool + 1;
            out.role = WaterSolver.ROLE_BED;
            return true;
        }
        double wall = pool + 1 + wallSlope * (av - sw);
        if (wall < natural && av - sw < WALL_REACH) {
            out.ground = (int) Math.floor(wall);
            out.role = WaterSolver.ROLE_ROCK;
            return true;
        }
        return false;
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
