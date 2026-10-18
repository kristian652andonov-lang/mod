package com.terrahaul.worldgen.terrain;

/** Lake categories; they decide depth, shoreline material and the {@code /terrahaul find} names. */
public final class LakeType {
    public static final int NONE = 0;
    public static final int FOREST = 1;
    public static final int MOUNTAIN = 2;
    public static final int GREAT = 3;
    public static final int MARSH = 4;
    public static final int CRATER = 5;
    public static final int PLATEAU = 6;
    public static final int GLACIAL = 7;

    private static final String[] NAMES = {"none", "forest_lake", "mountain_lake", "great_lake", "marsh_lake", "crater_lake", "plateau_lake", "glacial_lake"};

    private LakeType() {
    }

    public static String name(int id) {
        return id >= 0 && id < NAMES.length ? NAMES[id] : "lake";
    }
}
