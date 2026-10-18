package com.terrahaul.worldgen.noise;

/**
 * Deterministic 64-bit mixing helpers (SplitMix64 finaliser) used to derive independent seeds and
 * stable per-cell random values. Everything Terrahaul places at a fixed "site" (lakes, volcanoes,
 * waterfalls, arches) is derived from these hashes so the same site can be recomputed later, for
 * example by {@code /terrahaul find}, without generating any chunks.
 */
public final class SeedMixer {
    private SeedMixer() {
    }

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long next(long state) {
        return mix(state + 0x9E3779B97F4A7C15L);
    }

    public static long derive(long seed, long salt) {
        return mix(seed ^ mix(salt * 0x632BE59BD9B4E019L + 0x9E3779B97F4A7C15L));
    }

    /**
     * Hash of a 2D cell. The coordinates are mixed in one after the other: combining them with a
     * single XOR made (x, z) and (-x, -z) collide for about a third of all cells, so mirrored
     * cells shared site cache entries and random rolls.
     */
    public static long hash2(long seed, int x, int z) {
        return mix(mix(seed + x * 0x9E3779B97F4A7C15L) + z * 0xC2B2AE3D27D4EB4FL);
    }

    public static long hash3(long seed, int x, int y, int z) {
        return mix(hash2(seed, x, z) ^ (y * 0x165667B19E3779F9L));
    }

    /** Uniform double in [0, 1) from a hash value. */
    public static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    /** Uniform double in [0, 1) for a 2D cell and a channel index. */
    public static double cellUnit(long seed, int x, int z, int channel) {
        return unit(mix(hash2(seed, x, z) + channel * 0xD1B54A32D192ED03L));
    }
}
