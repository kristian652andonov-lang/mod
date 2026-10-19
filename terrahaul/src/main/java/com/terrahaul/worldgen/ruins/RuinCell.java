package com.terrahaul.worldgen.ruins;

/**
 * Packs one plan cell into an {@code int}: the {@link RuinPart}, a horizontal facing, an axis,
 * weathering (mossy, cracked, encrusted) and a few flags. Plans store cells in world orientation,
 * so the palette never has to rotate block states.
 * <pre>
 *   bits  0-7   part ordinal
 *   bits  8-9   facing: 0 north (-z), 1 east (+x), 2 south (+z), 3 west (-x)
 *   bit   10    TOP: upper half, top slab, upside-down stair, bed head, door top
 *   bit   11    FLAG: open (doors, trapdoors), hanging (lanterns), lit (campfires), cracked pot
 *   bits 12-13  axis: 0 y, 1 x, 2 z (logs, pillars, chains, hay)
 *   bits 14-15  weathering: 0 none, 1 mossy, 2 cracked, 3 encrusted (sea growth)
 *   bits 16-23  aux: variant (flower, colour, coral, pickle count, vine faces, stone, ...)
 *   bit   24    SOFT: only placed into air, water or replaceable plants, never into terrain
 * </pre>
 */
public final class RuinCell {
    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;
    public static final int[] DX = {0, 1, 0, -1};
    public static final int[] DZ = {-1, 0, 1, 0};

    public static final int AXIS_Y = 0;
    public static final int AXIS_X = 1;
    public static final int AXIS_Z = 2;

    public static final int PLAIN = 0;
    public static final int MOSSY = 1;
    public static final int CRACKED = 2;
    public static final int ENCRUSTED = 3;

    public static final int TOP = 1 << 10;
    public static final int FLAG = 1 << 11;
    public static final int SOFT = 1 << 24;

    private RuinCell() {
    }

    public static int of(RuinPart part) {
        return part.ordinal();
    }

    public static int of(RuinPart part, int dir) {
        return part.ordinal() | (dir & 3) << 8;
    }

    public static RuinPart part(int cell) {
        return RuinPart.of(cell & 0xFF);
    }

    public static int dir(int cell) {
        return cell >> 8 & 3;
    }

    public static int withDir(int cell, int dir) {
        return cell & ~(3 << 8) | (dir & 3) << 8;
    }

    public static boolean top(int cell) {
        return (cell & TOP) != 0;
    }

    public static boolean flag(int cell) {
        return (cell & FLAG) != 0;
    }

    public static boolean soft(int cell) {
        return (cell & SOFT) != 0;
    }

    public static int axis(int cell) {
        return cell >> 12 & 3;
    }

    public static int withAxis(int cell, int axis) {
        return cell & ~(3 << 12) | (axis & 3) << 12;
    }

    public static int weathering(int cell) {
        return cell >> 14 & 3;
    }

    public static int withWeathering(int cell, int weathering) {
        return cell & ~(3 << 14) | (weathering & 3) << 14;
    }

    public static int aux(int cell) {
        return cell >> 16 & 0xFF;
    }

    public static int withAux(int cell, int aux) {
        return cell & ~(0xFF << 16) | (aux & 0xFF) << 16;
    }

    public static int opposite(int dir) {
        return (dir + 2) & 3;
    }

    public static int clockwise(int dir) {
        return (dir + 1) & 3;
    }

    /** Horizontal direction of a vector, by its dominant component. */
    public static int dirOf(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? EAST : WEST;
        }
        return dz >= 0 ? SOUTH : NORTH;
    }
}
