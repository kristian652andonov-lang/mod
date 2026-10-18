package com.terrahaul.worldgen.ruins;

/**
 * Coarse biome categories as plain ints, so the pure site code can reason about biomes (where
 * trails are drawn, which wood a cabin is built of, whether a sea is warm) without Minecraft
 * classes. In game the world filter maps the biome source's biome to a category; without a world
 * (devtools) the categories are estimated from the terrain model's climate.
 */
public final class RuinBiomes {
    public static final int UNKNOWN = -1;
    public static final int PLAINS = 0;
    public static final int FOREST = 1;
    public static final int BIRCH = 2;
    public static final int DARK_FOREST = 3;
    public static final int TAIGA = 4;
    public static final int SNOWY = 5;
    public static final int MEADOW = 6;
    public static final int WINDSWEPT = 7;
    public static final int CHERRY = 8;
    public static final int JUNGLE = 9;
    public static final int SAVANNA = 10;
    public static final int SWAMP = 11;
    public static final int MANGROVE = 12;
    public static final int DESERT = 13;
    public static final int BADLANDS = 14;
    public static final int BEACH = 15;
    public static final int STONY = 16;
    public static final int SNOWY_PEAKS = 17;
    public static final int RIVER = 18;
    public static final int MUSHROOM = 19;
    public static final int OCEAN = 20;
    public static final int OCEAN_WARM = 21;
    public static final int OCEAN_LUKEWARM = 22;
    public static final int OCEAN_COLD = 23;
    public static final int OCEAN_FROZEN = 24;

    private RuinBiomes() {
    }

    /**
     * Whether the surface rule draws trails in this biome: not on sand (deserts, beaches), bare
     * rock (stony peaks and shores, gravelly hills), snow-covered peaks or badlands.
     */
    public static boolean trailVisible(int biome) {
        return switch (biome) {
            case DESERT, BADLANDS, BEACH, STONY, SNOWY_PEAKS, MUSHROOM, OCEAN, OCEAN_WARM, OCEAN_LUKEWARM, OCEAN_COLD, OCEAN_FROZEN -> false;
            default -> true;
        };
    }

    public static boolean warmSea(int biome) {
        return biome == OCEAN_WARM || biome == OCEAN_LUKEWARM;
    }
}
