package com.fantasyweapons.weapons.ultimate;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * How 4th Dimension Ascension breaks space, worked out the same way on the server (which rewrites the chunks) and the
 * client (which draws them breaking): every chunk whose middle lies inside the radius takes the whole column of another
 * one, from bedrock to the sky, turned to face another way, often hung upside down over a pit to the bottom of the world,
 * shifted up or down, with some of its 16-block cubes tipped over on their sides.
 */
public final class ShufflePlan {
    /** No tumble, or a quarter turn about X or Z one way or the other. */
    public static final int NONE = 0, X_POS = 1, X_NEG = 2, Z_POS = 3, Z_NEG = 4;
    /** World heights that may have cubes tipped over: around the ground. */
    private static final int BAND_LOW = 40, BAND_HIGH = 140;

    /**
     * One chunk of the region: what it is filled from and how.
     *
     * @param rot    quarter turns about the vertical
     * @param flip   hung upside down
     * @param dy     blocks the column is moved up (negative: down) after turning it over
     * @param tumble per section (index from the bottom of the world), how that cube is tipped
     * @param lag    ticks after the break before this chunk changes
     */
    public record Move(ChunkPos dest, ChunkPos source, int rot, boolean flip, int dy, int[] tumble, int lag) {
    }

    private final List<Move> moves = new ArrayList<>();

    public ShufflePlan(Vec3 center, double radius, long seed, int minY, int height) {
        List<ChunkPos> region = region(center, radius);
        RandomSource r = RandomSource.create(seed ^ 0x4D1E5L);
        List<ChunkPos> sources = new ArrayList<>(region);
        for (int i = sources.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            ChunkPos t = sources.get(i);
            sources.set(i, sources.get(j));
            sources.set(j, t);
        }
        int sections = height >> 4;
        int lo = Math.max(0, (BAND_LOW - minY) >> 4), hi = Math.min(sections - 1, (BAND_HIGH - minY) >> 4);
        for (int i = 0; i < region.size(); i++) {
            ChunkPos d = region.get(i);
            boolean flip = r.nextFloat() < 0.3f;
            int dy = flip ? -80 - r.nextInt(25) : (r.nextInt(9) - 3) * 8;
            int[] tumble = new int[sections];
            for (int s = lo; s <= hi; s++) if (r.nextFloat() < 0.25f) tumble[s] = 1 + r.nextInt(4);
            double dist = Math.hypot(d.getMiddleBlockX() - center.x, d.getMiddleBlockZ() - center.z);
            int lag = (int) (dist / Math.max(1, radius) * 10) + r.nextInt(4);
            moves.add(new Move(d, sources.get(i), r.nextInt(4), flip, dy, tumble, lag));
        }
    }

    public List<Move> moves() {
        return moves;
    }

    /** The chunks whose middle lies within {@code radius} of {@code center} (horizontally). */
    public static List<ChunkPos> region(Vec3 center, double radius) {
        List<ChunkPos> out = new ArrayList<>();
        int c0x = (int) Math.floor((center.x - radius) / 16), c1x = (int) Math.floor((center.x + radius) / 16);
        int c0z = (int) Math.floor((center.z - radius) / 16), c1z = (int) Math.floor((center.z + radius) / 16);
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                if (Math.hypot(cx * 16 + 8 - center.x, cz * 16 + 8 - center.z) <= radius) out.add(new ChunkPos(cx, cz));
            }
        }
        return out;
    }

    /** Whether a block position is inside the region. */
    public static boolean inside(List<ChunkPos> region, double x, double z) {
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
        for (ChunkPos p : region) if (p.x == cx && p.z == cz) return true;
        return false;
    }

    /**
     * Where the block at local {@code (x, h, z)} of a moved chunk ({@code h} counted up from the bottom of the world)
     * comes from in its source: {x, h, z} there, written into {@code out}; false if from outside the world (air).
     */
    public static boolean source(Move m, int x, int h, int z, int height, int[] out) {
        int si = h >> 4, y = h & 15;
        int t = si < m.tumble().length ? m.tumble()[si] : NONE;
        if (t != NONE) {
            int u = 2 * x - 15, v = 2 * y - 15, w = 2 * z - 15, a;
            switch (t) {
                case X_POS -> { a = v; v = -w; w = a; }
                case X_NEG -> { a = v; v = w; w = -a; }
                case Z_POS -> { a = u; u = -v; v = a; }
                default -> { a = u; u = v; v = -a; }
            }
            x = (u + 15) >> 1;
            y = (v + 15) >> 1;
            z = (w + 15) >> 1;
        }
        int h1 = si * 16 + y - m.dy();
        int h2 = m.flip() ? height - 1 - h1 : h1;
        if (h2 < 0 || h2 >= height) return false;
        int u = 2 * x - 15, w = 2 * z - 15;
        for (int k = 0; k < m.rot(); k++) {
            int a = u;
            u = -w;
            w = a;
        }
        out[0] = (u + 15) >> 1;
        out[1] = h2;
        out[2] = (w + 15) >> 1;
        return true;
    }
}
