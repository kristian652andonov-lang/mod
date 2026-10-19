package com.terrahaul.worldgen.ruins;

/**
 * A ruined house a few blocks off a trail, its front door facing the path. The house is drawn
 * in a canonical frame ({@link Canvas}) whose front row faces the trail; {@link #frame} places it
 * in the world.
 */
public final class TrailRuin extends RuinSite {
    /** One of the {@link HousePlanner} types (cottage, cabin, ...). */
    public final int template;
    /** Climate class, see {@link RuinSites#CLIMATE_NAMES}. */
    public final int climate;
    public final RuinMaterials materials;
    final Canvas.Frame frame;
    /** Y of the floor course. */
    public final int floorY;
    /** Decay 0..1: how much of the walls and roof has fallen. */
    public final double decay;
    /** Wetness 0..1: moss, vines and plants. */
    public final double wetness;
    public final String name;
    /** Centre column of the trail in front of the house. */
    public final int trailX;
    public final int trailZ;
    public final String lootTable;
    /** Length of the footpath from the front wall to the trail, in blocks. */
    public final int pathLength;

    TrailRuin(int x, int y, int z, long seed, int minX, int minZ, int maxX, int maxZ, int template, int climate, RuinMaterials materials,
              Canvas.Frame frame, int floorY, double decay, double wetness, String name, int trailX, int trailZ, String lootTable,
              int pathLength) {
        super(RuinSites.TRAIL_RUIN, x, y, z, seed, minX, minZ, maxX, maxZ);
        this.template = template;
        this.climate = climate;
        this.materials = materials;
        this.frame = frame;
        this.floorY = floorY;
        this.decay = decay;
        this.wetness = wetness;
        this.name = name;
        this.trailX = trailX;
        this.trailZ = trailZ;
        this.lootTable = lootTable;
        this.pathLength = pathLength;
    }
}
