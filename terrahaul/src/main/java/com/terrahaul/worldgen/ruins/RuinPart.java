package com.terrahaul.worldgen.ruins;

/**
 * The roles a block can play in a ruin plan. Plans are pure data (the finder and the devtools read
 * them without Minecraft), so they store roles instead of block states; {@code RuinPalette} turns a
 * role plus the site's materials into the actual block. Roles fall into three groups:
 * <ul>
 *   <li>{@linkplain #structural structural} parts: walls, floors, roofs, posts and columns. They
 *       carry each other, and the connectivity check of {@link RuinPlan#prune()} removes any that
 *       are not connected to the ground, so nothing in a ruin floats;</li>
 *   <li>attachments: doors, panes, vines, plants, props, lanterns and signs. They need a carrying
 *       neighbour and are dropped with it;</li>
 *   <li>{@link #AIR}, which clears the cell (water under the sea), and {@link #NONE}, which keeps
 *       whatever the world has there.</li>
 * </ul>
 */
public enum RuinPart {
    NONE(false),
    AIR(false),

    // ---- masonry (from the site's stone family) ------------------------------------------------
    FOUNDATION(true),
    MASONRY(true),
    QUOIN(true),
    MASONRY_STAIR(true),
    MASONRY_SLAB(true),
    MASONRY_WALL(true),
    CHIMNEY(true),

    // ---- timber (from the site's wood) ----------------------------------------------------------
    PLANKS(true),
    FLOOR(true),
    PLANK_STAIR(true),
    PLANK_SLAB(true),
    LOG(true),
    STRIPPED_LOG(true),
    FENCE(true),
    /** Gate in a fence line or gateway (facing; FLAG: open). Hangs between its posts. */
    FENCE_GATE(false),
    /** Tent canvas (aux: colour). */
    WOOL(true),

    // ---- roofs ----------------------------------------------------------------------------------
    ROOF(true),
    ROOF_STAIR(true),
    ROOF_SLAB(true),
    THATCH(true),
    PACKED_MUD(true),

    // ---- classical stonework of sunken ruins ----------------------------------------------------
    PILLAR(true),
    CAPITAL(true),
    COLUMN_BASE(true),
    TRIM(true),
    TRIM_STAIR(true),
    TRIM_SLAB(true),
    PAVING(true),
    SEA_LANTERN(true),

    // ---- ground ---------------------------------------------------------------------------------
    /** Earth floor where planks rotted: grass, coarse dirt, podzol or moss (aux). */
    SOIL(true),
    /** Sand, gravel, clay or mud copied from the sea floor the ruin sits on. */
    SEDIMENT(true),
    DIRT_PATH(true),
    COARSE_DIRT(true),
    GRAVEL(true),
    MOSS_BLOCK(true),
    WATER(false),
    SUSPICIOUS_GRAVEL(false),
    SUSPICIOUS_SAND(false),

    // ---- openings -------------------------------------------------------------------------------
    DOOR(false),
    TRAPDOOR(false),
    GLASS_PANE(false),
    IRON_BARS(false),
    LADDER(false),

    // ---- overgrowth -----------------------------------------------------------------------------
    LEAVES(false),
    VINE(false),
    COBWEB(false),
    MOSS_CARPET(false),
    SHORT_GRASS(false),
    FERN(false),
    TALL_GRASS(false),
    FLOWER(false),
    DEAD_BUSH(false),
    MUSHROOM(false),
    SWEET_BERRY_BUSH(false),
    LITTER(false),
    ROCK(false),

    // ---- furnishings ----------------------------------------------------------------------------
    CAMPFIRE(false),
    CRAFTING_TABLE(false),
    CHEST(false),
    BARREL(false),
    FURNACE(false),
    BOOKSHELF(false),
    CAULDRON(false),
    FLOWER_POT(false),
    HAY(false),
    COMPOSTER(false),
    ANVIL(false),
    BED(false),
    CARPET(false),
    DECORATED_POT(false),
    LANTERN(false),
    CHAIN(false),
    STANDING_SIGN(false),
    WALL_HANGING_SIGN(false),
    /** A sign flat against the block behind it (facing away from that block). */
    WALL_SIGN(false),
    /** Candles (aux: count - 1; FLAG: lit). */
    CANDLE(false),
    /** A bell (FLAG: hanging from the block above, else lying on the floor). */
    BELL(false),
    LECTERN(false),
    /** The head of a scarecrow. */
    CARVED_PUMPKIN(false),

    // ---- the sea --------------------------------------------------------------------------------
    SEAGRASS(false),
    TALL_SEAGRASS(false),
    KELP(false),
    KELP_PLANT(false),
    SEA_PICKLE(false),
    CORAL_FAN(false),
    WET_SPONGE(false),
    GLOW_LICHEN(false);

    private static final RuinPart[] VALUES = values();

    /** Structural parts carry their neighbours and must themselves connect to the ground. */
    public final boolean structural;

    RuinPart(boolean structural) {
        this.structural = structural;
    }

    public static RuinPart of(int ordinal) {
        return VALUES[ordinal];
    }

    /** Parts that are a full, solid cube (they hold vines, ladders and wall fans on their faces). */
    public boolean fullCube() {
        return switch (this) {
            case FOUNDATION, MASONRY, QUOIN, CHIMNEY, PLANKS, FLOOR, LOG, STRIPPED_LOG, ROOF, THATCH, PACKED_MUD, PILLAR, CAPITAL,
                COLUMN_BASE, TRIM, PAVING, SEA_LANTERN, SOIL, SEDIMENT, COARSE_DIRT, GRAVEL, MOSS_BLOCK, HAY, BOOKSHELF,
                CRAFTING_TABLE, BARREL, FURNACE, WET_SPONGE, WOOL -> true;
            default -> false;
        };
    }

    /** Parts that stand on the cell below and need it to be solid. */
    public boolean standsOnFloor() {
        return switch (this) {
            case DOOR, SHORT_GRASS, FERN, TALL_GRASS, FLOWER, DEAD_BUSH, MUSHROOM, SWEET_BERRY_BUSH, LITTER, ROCK, CAMPFIRE,
                CRAFTING_TABLE, CHEST, BARREL, FURNACE, BOOKSHELF, CAULDRON, FLOWER_POT, HAY, COMPOSTER, ANVIL, BED, CARPET,
                DECORATED_POT, STANDING_SIGN, MOSS_CARPET, SEAGRASS, TALL_SEAGRASS, KELP, KELP_PLANT, SEA_PICKLE,
                SUSPICIOUS_GRAVEL, SUSPICIOUS_SAND, CANDLE, LECTERN, CARVED_PUMPKIN -> true;
            default -> false;
        };
    }
}
