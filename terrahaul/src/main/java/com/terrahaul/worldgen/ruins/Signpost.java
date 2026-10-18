package com.terrahaul.worldgen.ruins;

/**
 * A wooden signpost beside a trail junction (a fingerpost with an arm per branch) or at the end
 * of a trail (a board facing walkers). The post column is chosen so that the whole post, arms
 * included, lies inside one chunk, which then builds it alone.
 */
public final class Signpost extends RuinSite {
    public static final int JUNCTION = 0;
    public static final int END = 1;

    public final int kind;
    /** Junction centre or trail end. */
    public final int nodeX;
    public final int nodeZ;
    /** Directions (radians, atan2(dz, dx)) of the trail branches leaving the node. */
    public final double[] arms;
    public final RuinMaterials materials;
    public final double wetness;
    /** Sign text, worked out when the post is first needed (see {@link SignpostPlanner}). */
    volatile SignpostPlanner.Board[] boards;

    Signpost(int x, int y, int z, long seed, int kind, int nodeX, int nodeZ, double[] arms, RuinMaterials materials, double wetness) {
        super(RuinSites.SIGNPOST, x, y, z, seed, x - 2, z - 2, x + 2, z + 2);
        this.kind = kind;
        this.nodeX = nodeX;
        this.nodeZ = nodeZ;
        this.arms = arms;
        this.materials = materials;
        this.wetness = wetness;
    }
}
