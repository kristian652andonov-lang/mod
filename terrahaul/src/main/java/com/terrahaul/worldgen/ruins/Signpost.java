package com.terrahaul.worldgen.ruins;

/**
 * A signpost beside a trail: a fingerpost at a junction with an arm per branch, a board where a
 * trail ends, or a marker along a long trail between junctions: a direction post with an arm each
 * way, or a milestone with a plate facing the path. The post column is chosen so that the whole
 * post, arms included, lies inside one chunk, which then builds it alone.
 */
public final class Signpost extends RuinSite {
    public static final int JUNCTION = 0;
    public static final int END = 1;
    public static final int WAYPOST = 2;
    public static final int MILESTONE = 3;
    public static final String[] KIND_NAMES = {"junction", "trail_end", "waypost", "milestone"};

    /** Post styles, by climate: a stripped log, a log in its bark, a stone pillar, a bamboo pole. */
    public static final int STRIPPED_POST = 0;
    public static final int BARK_POST = 1;
    public static final int STONE_POST = 2;
    public static final int BAMBOO_POST = 3;
    public static final String[] STYLE_NAMES = {"stripped_log", "log", "stone", "bamboo"};

    public final int kind;
    public final int style;
    /** Junction centre, trail end, or the point of the trail the marker stands beside. */
    public final int nodeX;
    public final int nodeZ;
    /** Directions (radians, atan2(dz, dx)) of the trail branches leaving the node. */
    public final double[] arms;
    public final RuinMaterials materials;
    public final double wetness;
    /** Direction from the post towards the trail (a {@link RuinCell} facing), for milestones. */
    public final int toTrail;
    /** Sign text, worked out when the post is first needed (see {@link SignpostPlanner}). */
    volatile SignpostPlanner.Board[] boards;

    Signpost(int x, int y, int z, long seed, int kind, int style, int nodeX, int nodeZ, double[] arms, RuinMaterials materials, double wetness,
             int toTrail) {
        super(RuinSites.SIGNPOST, x, y, z, seed, x - 2, z - 2, x + 2, z + 2);
        this.kind = kind;
        this.style = style;
        this.nodeX = nodeX;
        this.nodeZ = nodeZ;
        this.arms = arms;
        this.materials = materials;
        this.wetness = wetness;
        this.toTrail = toTrail;
    }
}
