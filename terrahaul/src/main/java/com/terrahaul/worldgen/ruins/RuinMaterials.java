package com.terrahaul.worldgen.ruins;

/**
 * The material families of one ruin, chosen by its site from climate, geology and biome. Plans
 * only store roles ({@link RuinPart}); {@code RuinPalette} combines a role with these families into
 * a block. Each family stays within one colour (a stone, its mossy and cracked forms, and the
 * matching stairs, slabs and walls), so ruins never look speckled.
 */
public final class RuinMaterials {
    // ---- stone families -----------------------------------------------------------------------
    /** Cobblestone walls, stone-brick quoins. */
    public static final int COBBLE = 0;
    /** Dressed stone bricks throughout. */
    public static final int ASHLAR = 1;
    /** Cobblestone walls with polished granite quoins and sills. */
    public static final int GRANITE = 2;
    /** Cobbled deepslate walls, deepslate-brick quoins: a slate cottage. */
    public static final int SLATE = 3;
    /** Whitewashed calcite walls with polished diorite trim. */
    public static final int LIMESTONE = 4;
    public static final int SANDSTONE = 5;
    public static final int RED_SANDSTONE = 6;
    /** Mud bricks over packed mud: adobe. */
    public static final int MUD = 7;
    /** Mossy cobblestone with mossy stone-brick quoins (jungle ruins). */
    public static final int MOSSY = 8;
    /** Tuff bricks with polished tuff trim (volcanic provinces). */
    public static final int TUFF = 9;
    /** Sunken: stone bricks with chiseled capitals and polished andesite shafts. */
    public static final int SEA_STONE = 10;
    /** Sunken: tuff bricks, polished tuff shafts and chiseled tuff capitals. */
    public static final int SEA_TUFF = 11;
    /** Sunken: quartz pillars, chiseled quartz capitals, calcite and smooth sandstone. */
    public static final int MARBLE = 12;
    /** Sunken: cut, chiseled and smooth sandstone. */
    public static final int SEA_SANDSTONE = 13;
    public static final String[] STONE_NAMES = {"cobble", "ashlar", "granite", "slate", "limestone", "sandstone", "red_sandstone",
        "mud", "mossy", "tuff", "sea_stone", "sea_tuff", "marble", "sea_sandstone"};

    // ---- woods --------------------------------------------------------------------------------
    public static final int OAK = 0;
    public static final int SPRUCE = 1;
    public static final int BIRCH = 2;
    public static final int DARK_OAK = 3;
    public static final int ACACIA = 4;
    public static final int JUNGLE = 5;
    public static final int MANGROVE = 6;
    public static final int CHERRY = 7;
    public static final int BAMBOO = 8;
    /** Leaves only: flowering azalea bushes. */
    public static final int AZALEA = 9;
    public static final String[] WOOD_NAMES = {"oak", "spruce", "birch", "dark_oak", "acacia", "jungle", "mangrove", "cherry", "bamboo", "azalea"};

    // ---- roof coverings -----------------------------------------------------------------------
    /** Wooden stairs and slabs of {@link #roofWood}. */
    public static final int ROOF_WOOD = 0;
    /** Stairs and slabs of the stone family. */
    public static final int ROOF_STONE = 1;
    /** Deepslate tiles. */
    public static final int ROOF_TILE = 2;

    public final int stone;
    public final int wood;
    public final int roofWood;
    public final int roofCover;
    public final int leaves;

    public RuinMaterials(int stone, int wood, int roofWood, int roofCover, int leaves) {
        this.stone = stone;
        this.wood = wood;
        this.roofWood = roofWood;
        this.roofCover = roofCover;
        this.leaves = leaves;
    }

    /** Dark woods get white sign text, light woods black. */
    public boolean darkWood() {
        return wood == SPRUCE || wood == DARK_OAK || wood == MANGROVE;
    }

    @Override
    public String toString() {
        return STONE_NAMES[stone] + "/" + WOOD_NAMES[wood] + " roof " + (roofCover == ROOF_WOOD ? WOOD_NAMES[roofWood]
            : roofCover == ROOF_STONE ? "stone" : "tiles");
    }
}
