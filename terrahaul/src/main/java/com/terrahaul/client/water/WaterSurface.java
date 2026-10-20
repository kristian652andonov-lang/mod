package com.terrahaul.client.water;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Where Minecraft draws the top of the water, for the foam that floats on it.
 * <p>
 * A water block's own height (8/9 of a block for still water) is not where its surface is drawn.
 * The liquid renderer draws each corner of the top face at a weighted average of the four blocks
 * that share the corner, and at the full block wherever one of them has water above it. Around the
 * foot of every fall the pool's surface therefore rises to the full block towards the falling
 * water. Foam placed at the own height lies under the drawn water there, and the water, drawn first
 * and writing depth, hides it. This class repeats the renderer's corner rule and interpolates across
 * the face the way it is drawn: as two triangles split along the north-west to south-east diagonal.
 * <p>
 * The corners of a block are kept for a few ticks in a small table, so the foam particles that read
 * them every tick share the work. Client thread only.
 */
public final class WaterSurface {
    /** Particles float this far above the drawn surface, enough for the depth buffer at foam range. */
    public static final double LIFT = 0.02;

    private static final int SLOTS = 1024;
    private static final int KEEP_TICKS = 10;
    private static final long NEVER = Long.MIN_VALUE / 2;
    private static final long[] KEYS = new long[SLOTS];
    private static final long[] STAMPS = new long[SLOTS];
    /** North-west, north-east, south-west, south-east corner heights, or NaN if no top face is drawn. */
    private static final float[] CORNERS = new float[SLOTS * 4];
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static Level cachedLevel;

    static {
        Arrays.fill(STAMPS, NEVER);
    }

    private WaterSurface() {
    }

    /** Forgets the kept corners (and the world they belong to), when the player leaves a world. */
    public static void clear() {
        cachedLevel = null;
        Arrays.fill(STAMPS, NEVER);
    }

    /**
     * The highest point of the drawn water surface under a patch of the given half width centred
     * on {@code x, z}, on the water block at height {@code by}: the drawn height at the centre and at
     * eight points around it, {@code radius} away, so that a flat patch lying at that height is not
     * cut by a sloping surface. Points over the bank, under falling water or off the edge of the
     * water do not count. NaN if the centre has no drawn top face.
     */
    public static double highest(Level level, double x, int by, double z, double radius) {
        double centre = at(level, x, by, z);
        if (Double.isNaN(centre) || radius <= 0.0) {
            return centre;
        }
        double top = centre;
        double d = radius * Mth.SQRT_OF_TWO * 0.5;
        top = max(top, at(level, x + radius, by, z));
        top = max(top, at(level, x - radius, by, z));
        top = max(top, at(level, x, by, z + radius));
        top = max(top, at(level, x, by, z - radius));
        top = max(top, at(level, x + d, by, z + d));
        top = max(top, at(level, x + d, by, z - d));
        top = max(top, at(level, x - d, by, z + d));
        top = max(top, at(level, x - d, by, z - d));
        return top;
    }

    private static double max(double a, double b) {
        return Double.isNaN(b) ? a : Math.max(a, b);
    }

    /** The drawn height of the top face of the water block at {@code floor(x), by, floor(z)}, NaN if none is drawn there. */
    public static double at(Level level, double x, int by, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int slot = corners(level, bx, by, bz);
        float nw = CORNERS[slot];
        if (Float.isNaN(nw)) {
            return Double.NaN;
        }
        float ne = CORNERS[slot + 1];
        float sw = CORNERS[slot + 2];
        float se = CORNERS[slot + 3];
        double fx = x - bx;
        double fz = z - bz;
        double h = fz >= fx
            ? nw + fz * (sw - nw) + fx * (se - sw)
            : nw + fx * (ne - nw) + fz * (se - ne);
        return by + h;
    }

    /** Index of the block's four corner heights in {@link #CORNERS}, computed if not kept. */
    private static int corners(Level level, int bx, int by, int bz) {
        long now = level.getGameTime();
        if (level != cachedLevel) {
            clear();
            cachedLevel = level;
        }
        long key = BlockPos.asLong(bx, by, bz);
        int slot = (int) (HashCommon.mix(key) & SLOTS - 1);
        long age = now - STAMPS[slot];
        if (KEYS[slot] == key && age >= 0 && age < KEEP_TICKS) {
            return slot * 4;
        }
        KEYS[slot] = key;
        STAMPS[slot] = now;
        int i = slot * 4;
        float cur = height(level, bx, by, bz);
        BlockState above = level.getBlockState(POS.set(bx, by + 1, bz));
        // no top face: not water, water above it (the inside of a fall), or a block resting on it
        if (!(cur > 0.0F) || cur >= 1.0F || above.blocksMotion()) {
            CORNERS[i] = Float.NaN;
            return i;
        }
        float n = height(level, bx, by, bz - 1);
        float s = height(level, bx, by, bz + 1);
        float e = height(level, bx + 1, by, bz);
        float w = height(level, bx - 1, by, bz);
        CORNERS[i] = average(level, cur, n, w, bx - 1, by, bz - 1);
        CORNERS[i + 1] = average(level, cur, n, e, bx + 1, by, bz - 1);
        CORNERS[i + 2] = average(level, cur, s, w, bx - 1, by, bz + 1);
        CORNERS[i + 3] = average(level, cur, s, e, bx + 1, by, bz + 1);
        return i;
    }

    /**
     * A block's height for the corner rule, as the liquid renderer reads it: the full block if water
     * is above it, its own height if it is water, -1 for a solid block and 0 for anything else.
     */
    private static float height(Level level, int x, int y, int z) {
        BlockState state = level.getBlockState(POS.set(x, y, z));
        FluidState fluid = state.getFluidState();
        if (fluid.is(FluidTags.WATER)) {
            return level.getFluidState(POS.set(x, y + 1, z)).is(FluidTags.WATER) ? 1.0F : fluid.getOwnHeight();
        }
        return state.isSolid() ? -1.0F : 0.0F;
    }

    /** One corner: the full block if a block around it has water above, else a weighted average. */
    private static float average(Level level, float cur, float side1, float side2, int dx, int y, int dz) {
        if (side1 >= 1.0F || side2 >= 1.0F) {
            return 1.0F;
        }
        float diagonal = -1.0F;
        if (side1 > 0.0F || side2 > 0.0F) {
            diagonal = height(level, dx, y, dz);
            if (diagonal >= 1.0F) {
                return 1.0F;
            }
        }
        return (weighted(diagonal) + weighted(cur) + weighted(side1) + weighted(side2))
            / (weight(diagonal) + weight(cur) + weight(side1) + weight(side2));
    }

    /** High water counts ten times as much as low water, so a still pool's surface stays level; solid blocks not at all. */
    private static float weight(float height) {
        return height >= 0.8F ? 10.0F : height >= 0.0F ? 1.0F : 0.0F;
    }

    private static float weighted(float height) {
        return height * weight(height);
    }
}
