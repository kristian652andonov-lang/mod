package com.terrahaul.worldgen.ruins;

/**
 * Drowned stonework on a mid-depth sea floor: a colonnaded temple, a city gate with a great arch,
 * a quarter of house foundations along a paved street, or the stump of a round tower.
 */
public final class SunkenRuin extends RuinSite {
    public static final int COLONNADE = 0;
    public static final int GATE = 1;
    public static final int QUARTER = 2;
    public static final int TOWER = 3;
    public static final String[] KIND_NAMES = {"colonnade", "gate", "quarter", "tower"};

    public final int kind;
    final Canvas.Frame frame;
    /** Y of the platform's lowest course, on the sea floor. */
    public final int floorY;
    public final double decay;
    /** Warm sea: coral and sea pickles instead of kelp. */
    public final boolean warm;
    public final boolean frozen;
    public final RuinMaterials materials;
    /** Canonical size of the layout (u along the front, v front to back). */
    public final int sizeU;
    public final int sizeV;
    /** Water depth above the floor course; sea growth stays a couple of blocks below the surface. */
    public final int depth;

    SunkenRuin(int x, int y, int z, long seed, int minX, int minZ, int maxX, int maxZ, int kind, Canvas.Frame frame, int floorY,
               double decay, boolean warm, boolean frozen, RuinMaterials materials, int sizeU, int sizeV,
               int depth) {
        super(RuinSites.SUNKEN_RUIN, x, y, z, seed, minX, minZ, maxX, maxZ);
        this.kind = kind;
        this.frame = frame;
        this.floorY = floorY;
        this.decay = decay;
        this.warm = warm;
        this.frozen = frozen;
        this.materials = materials;
        this.sizeU = sizeU;
        this.sizeV = sizeV;
        this.depth = depth;
    }
}
