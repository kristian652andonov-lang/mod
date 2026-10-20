package com.terrahaul.worldgen.features.lake;

/**
 * What the lake planners know about one block column: the planned floor (the terrain model's
 * interpolated height, which the noise generator reproduces away from 3D cliff detail), the lake
 * the column belongs to and its surface, the climate, and the per-column parts of the plan (bed
 * materials, shelves, rock ledges and how strongly each kind of plant grows there). Instances are
 * filled by {@link LakePlanner#column} and are plain mutable data.
 */
public final class LakeColumn {
    /** {@link #surf} of a column that belongs to no lake. */
    public static final int NO_SURF = Integer.MIN_VALUE;

    public int x;
    public int z;
    /** Terrain model height at block resolution. */
    public double height;
    /** Top solid block of the untouched floor. */
    public int floor;
    /** The column lies in a lake (one of its model corners is inside a lake or crater lake). */
    public boolean lake;
    /** Top water block of that lake, or {@link #NO_SURF}. */
    public int surf;
    /** Lake water stands over the floor: {@link #lake} and at least one block of water. */
    public boolean wet;
    /** Water blocks above the untouched floor (0 or less where the ground reaches the surface). */
    public int depth;
    /** {@code LakeType} of the lake. */
    public int type;
    /** 0 at the shoreline, 1 at the lake's centre. */
    public double inner;
    /** Radius of the lake in blocks, and the lake's size from 0 (a pond) to 1 (100 blocks across and more). */
    public double radius;
    public double size;
    public double temperature;
    /** Climate temperature before altitude cooling, which the surface rule's lake beds go by. */
    public double baseTemperature;
    public double humidity;
    /** How mountainous the column is (blended like the surface rule's). */
    public double mountain;
    /** Lake field blended like the surface rule's (0 outside lakes), and the lake type it picks the bed by. */
    public double lakeField;
    public int bedType;
    public int geology;
    /** Rock family of boulders and ledges ({@code MarinePlanner} families). */
    public int family;
    /** Steepness of the floor in blocks per block, and its gradient (pointing uphill, to the shore). */
    public double slope;
    public double gradX;
    public double gradZ;

    // --- the plan ----------------------------------------------------------------------------
    /** Top of the planned bed: the floor, raised by a littoral shelf or a gravel bar. */
    public int bed;
    /** Material of the shelf or bar fill between {@link #floor} and {@link #bed} ({@code LakeVolume} code). */
    public byte fill;
    /** Material the top block of the bed is turned into ({@code LakeVolume} code), 0 to keep it. */
    public byte patch;
    /** Height of the bedrock ledge on this column, 0 if none. */
    public int outcrop;
    /** How rocky the floor is (talus under steep shores, glacial floors, rock provinces), 0..1. */
    public double rocky;
    /** Seagrass meadow strength, 0..1 (0 inside bare blowouts). */
    public double meadow;
    /** Strength of the weed beds (kelp standing in for pondweed), 0..1. */
    public double weed;
    /** Lily pad colonies on calm shallow water, 0..1. */
    public double lilies;
    /** Lush shallows with dripleaf and moss, 0..1. */
    public double lush;

    public void copyFrom(LakeColumn o) {
        x = o.x;
        z = o.z;
        height = o.height;
        floor = o.floor;
        lake = o.lake;
        surf = o.surf;
        wet = o.wet;
        depth = o.depth;
        type = o.type;
        inner = o.inner;
        radius = o.radius;
        size = o.size;
        temperature = o.temperature;
        baseTemperature = o.baseTemperature;
        humidity = o.humidity;
        mountain = o.mountain;
        lakeField = o.lakeField;
        bedType = o.bedType;
        geology = o.geology;
        family = o.family;
        slope = o.slope;
        gradX = o.gradX;
        gradZ = o.gradZ;
        bed = o.bed;
        fill = o.fill;
        patch = o.patch;
        outcrop = o.outcrop;
        rocky = o.rocky;
        meadow = o.meadow;
        weed = o.weed;
        lilies = o.lilies;
        lush = o.lush;
    }

    /** Water blocks above the planned bed. */
    public int water() {
        return wet ? surf - bed : 0;
    }
}
