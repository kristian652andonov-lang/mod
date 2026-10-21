package com.terrahaul.worldgen.ruins;

/**
 * Old lamp posts along a trail: the posts of one lit section of trail that stand in one trail
 * cell. Lit sections are not confined to cells: whether a stretch of trail is lit, the style of
 * its lamps and where each post stands are pure functions of position (see {@link TrailSurvey}),
 * so a section running across a cell border carries on in the next cell with the same spacing and
 * the same lamps. Each post sits just off the path edge in a column chosen so that the post and
 * everything that fell off it lie inside one chunk.
 */
public final class LightStretch extends RuinSite {
    /** Lamp post styles, chosen by climate. */
    public static final int LAMP_ARM = 0;
    public static final int CHAIN_LAMP = 1;
    public static final int STONE_LANTERN = 2;
    public static final int POST_TOP = 3;
    public static final int BAMBOO = 4;
    public static final String[] STYLE_NAMES = {"lamp_arm", "chain_lamp", "stone_lantern", "post_top", "bamboo"};

    /**
     * One lamp post.
     *
     * @param toTrail     the direction from the post towards the trail (a {@link RuinCell} facing)
     * @param keeper      a post that always keeps its lantern, so a stretch never goes dark
     */
    public record Post(int x, int z, int toTrail, int style, double decay, double wetness, boolean soul, RuinMaterials materials, long seed,
                       boolean keeper) {
    }

    public final Post[] posts;

    LightStretch(int x, int y, int z, long seed, int minX, int minZ, int maxX, int maxZ, Post[] posts) {
        super(RuinSites.PATH_LIGHTS, x, y, z, seed, minX, minZ, maxX, maxZ);
        this.posts = posts;
    }

    /** The style of the stretch: that of its middle post. */
    public int style() {
        return posts[posts.length / 2].style();
    }

    /** Mean decay of the posts. */
    public double decay() {
        double s = 0;
        for (Post p : posts) {
            s += p.decay();
        }
        return s / posts.length;
    }
}
