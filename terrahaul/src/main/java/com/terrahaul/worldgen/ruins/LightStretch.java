package com.terrahaul.worldgen.ruins;

/**
 * A row of old lamp posts along a trail, 16-32 blocks apart, many of them broken. Each post sits
 * just off the path edge in a column chosen so that the post and everything that fell off it lie
 * inside one chunk.
 */
public final class LightStretch extends RuinSite {
    /** Lamp post styles, chosen by climate. */
    public static final int LAMP_ARM = 0;
    public static final int CHAIN_LAMP = 1;
    public static final int STONE_LANTERN = 2;
    public static final int POST_TOP = 3;
    public static final int BAMBOO = 4;
    public static final String[] STYLE_NAMES = {"lamp_arm", "chain_lamp", "stone_lantern", "post_top", "bamboo"};

    /** Posts as {x, z, direction towards the trail (a RuinCell facing)}. */
    public final int[][] posts;
    public final int style;
    public final double decay;
    public final double wetness;
    public final boolean soulLanterns;
    public final RuinMaterials materials;

    LightStretch(int x, int y, int z, long seed, int minX, int minZ, int maxX, int maxZ, int[][] posts, int style, double decay,
                 double wetness, boolean soulLanterns, RuinMaterials materials) {
        super(RuinSites.PATH_LIGHTS, x, y, z, seed, minX, minZ, maxX, maxZ);
        this.posts = posts;
        this.style = style;
        this.decay = decay;
        this.wetness = wetness;
        this.soulLanterns = soulLanterns;
        this.materials = materials;
    }
}
