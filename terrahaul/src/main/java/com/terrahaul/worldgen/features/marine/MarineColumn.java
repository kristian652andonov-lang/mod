package com.terrahaul.worldgen.features.marine;

/**
 * What the marine planners know about one block column of the sea floor: the planned floor (the
 * terrain model's interpolated height, which the noise generator reproduces exactly away from 3D
 * cliff detail), the climate and coast fields that decide which sea-floor life grows there, and the
 * per-column parts of the plans (reef platform, kelp, seagrass, rock ledges). Instances are filled
 * by {@link MarinePlanner#column} and are plain mutable data.
 */
public final class MarineColumn {
    public int x;
    public int z;
    /** Terrain model height at block resolution. */
    public double height;
    /** Top solid block of the untouched floor. */
    public int floor;
    /** Water blocks above the floor, up to the top water block at sea level - 1 (0 or less on land). */
    public int depth;
    /** Open sea: below sea level and not part of a lake or river. */
    public boolean sea;
    public double temperature;
    /** Steepness of the floor in blocks per block, and its gradient. */
    public double slope;
    public double gradX;
    public double gradZ;
    public double coastCliff;
    public double fjord;
    public double riverValley;
    public double trench;
    public double oceanRidge;
    public double beach;
    public double volcano;
    public int geology;
    /** Rock family of boulders and ledges (see {@link MarinePlanner#FAMILY_NAMES}). */
    public int family;

    // --- coral reefs ---------------------------------------------------------------------------
    /** Strength of the fringing reef platform, 0..1. */
    public double reef;
    /** Strength of free-standing reef objects (coral heads, tables, thickets), 0..1. */
    public double reefObjects;
    /** Water depth at which the reef flat ends in the reef front. */
    public double front;
    /** Water depth below which the sandy back-reef lagoon gives way to the reef flat. */
    public double back;
    /** Top block of the planned reef platform or front slope; equal to {@link #floor} where there is none. */
    public int platform;

    // --- kelp, seagrass and rocks ----------------------------------------------------------------
    /** Kelp forest strength, 0..1. */
    public double kelp;
    /** Seagrass meadow strength, 0..1 (0 inside bare-sand blowouts). */
    public double meadow;
    /** How rocky the floor is (cliff talus, scoured slopes, glaciated shelves), 0..1. */
    public double rocky;
    /** Height of the bedrock ledge planned on this column, 0 if none. */
    public int outcrop;

    public boolean hasPlatform() {
        return platform > floor;
    }
}
