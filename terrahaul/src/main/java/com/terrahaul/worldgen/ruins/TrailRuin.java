package com.terrahaul.worldgen.ruins;

/**
 * A ruin beside a trail: a broken house, or one of the other things that are left along old roads
 * (a watchtower, a well, a wagon, a shrine, a campsite, field walls with a gateway, the abutments
 * of a lost bridge, a chapel). It is drawn in a canonical frame ({@link Canvas}) whose front faces
 * the trail; {@link #frame} places it in the world.
 */
public final class TrailRuin extends RuinSite {
    public static final int HOUSE = 0;
    public static final int WATCHTOWER = 1;
    public static final int WELL = 2;
    public static final int WAGON = 3;
    public static final int SHRINE = 4;
    public static final int CAMPSITE = 5;
    public static final int FIELD = 6;
    public static final int BRIDGE = 7;
    public static final int CHAPEL = 8;
    public static final String[] KIND_NAMES = {"house", "watchtower", "well", "wagon", "shrine", "campsite", "field_walls", "bridge", "chapel"};

    public final int kind;
    /** Layout within the kind: the size of a house, the type of a tower or well, ... (see the planners). */
    public final int variant;
    /** Building style, by climate: {@link HousePlanner#TIMBER_FRAME} ... {@link HousePlanner#RAISED}. */
    public final int style;
    /** State of decay of a house: {@link HousePlanner#ROOF_FALLEN} ... {@link HousePlanner#OVERGROWN}. */
    public final int state;
    /** Climate class, see {@link RuinSites#CLIMATE_NAMES}. */
    public final int climate;
    public final RuinMaterials materials;
    final Canvas.Frame frame;
    final RuinSpec spec;
    /** Y of the floor course. */
    public final int floorY;
    /** Decay 0..1: how much of the walls and roof has fallen. */
    public final double decay;
    /** Wetness 0..1: moss, vines and plants. */
    public final double wetness;
    public final String name;
    /** Centre column of the trail in front of the ruin. */
    public final int trailX;
    public final int trailZ;
    public final String lootTable;
    /** Length of the footpath from the front to the trail, in blocks. */
    public final int pathLength;

    TrailRuin(int x, int y, int z, long seed, int minX, int minZ, int maxX, int maxZ, int kind, int variant, int style, int state, int climate,
              RuinMaterials materials, Canvas.Frame frame, RuinSpec spec, int floorY, double decay, double wetness, String name, int trailX,
              int trailZ, String lootTable, int pathLength) {
        super(RuinSites.TRAIL_RUIN, x, y, z, seed, minX, minZ, maxX, maxZ);
        this.kind = kind;
        this.variant = variant;
        this.style = style;
        this.state = state;
        this.climate = climate;
        this.materials = materials;
        this.frame = frame;
        this.spec = spec;
        this.floorY = floorY;
        this.decay = decay;
        this.wetness = wetness;
        this.name = name;
        this.trailX = trailX;
        this.trailZ = trailZ;
        this.lootTable = lootTable;
        this.pathLength = pathLength;
    }

    /** A short description for the devtools: kind and variant, style and state of a house. */
    public String describe() {
        if (kind == HOUSE) {
            return HousePlanner.LAYOUT_NAMES[variant] + " " + HousePlanner.STYLE_NAMES[style] + " " + HousePlanner.STATE_NAMES[state];
        }
        return KIND_NAMES[kind] + " " + RuinSites.variantName(kind, variant);
    }
}
