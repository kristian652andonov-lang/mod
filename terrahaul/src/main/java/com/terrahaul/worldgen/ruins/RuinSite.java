package com.terrahaul.worldgen.ruins;

/**
 * A planned ruin, signpost, lamp-post stretch or sunken ruin: where it is, how far it reaches
 * and the seed of everything about it. Sites are immutable once created (apart from the cached
 * world-filter verdict) and are shared by the feature, which builds them, and the finder, which
 * reports them, so the two always agree.
 */
public abstract class RuinSite {
    public final int type;
    /** Position reported by the finder (a good place to look at the site from). */
    public final int x;
    public final int y;
    public final int z;
    public final long seed;
    /** Columns the site may write, inclusive. */
    public final int minX;
    public final int minZ;
    public final int maxX;
    public final int maxZ;
    /** 0 unknown, 1 allowed, 2 refused by the world filter; see {@link RuinSites#allowed}. */
    volatile byte verdict;

    RuinSite(int type, int x, int y, int z, long seed, int minX, int minZ, int maxX, int maxZ) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.seed = seed;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public boolean overlaps(int x0, int z0, int x1, int z1) {
        return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1;
    }

    /** Half the larger side of the site's box, in blocks. */
    public int radius() {
        return Math.max(maxX - minX, maxZ - minZ) / 2 + 1;
    }

    public int centreX() {
        return (minX + maxX) / 2;
    }

    public int centreZ() {
        return (minZ + maxZ) / 2;
    }
}
