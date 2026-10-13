package com.fantasyweapons.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/**
 * The five blocks of a machine besides its core: the core is the bottom middle of a structure three wide (across its
 * front), two high and one deep. {@code side} counts along the facing's clockwise direction.
 */
public enum MachinePart implements StringRepresentable {
    LEFT(-1, 0), RIGHT(1, 0), TOP(0, 1), TOP_LEFT(-1, 1), TOP_RIGHT(1, 1);

    private final int side;
    private final int up;

    MachinePart(int side, int up) {
        this.side = side;
        this.up = up;
    }

    /** Where this part is for a core at {@code core} facing {@code facing}. */
    public BlockPos from(BlockPos core, Direction facing) {
        return core.relative(facing.getClockWise(), side).above(up);
    }

    /** Where the core is for this part at {@code pos}. */
    public BlockPos core(BlockPos pos, Direction facing) {
        return pos.relative(facing.getClockWise(), -side).below(up);
    }

    /** -1 for the parts left of the core (as the machine faces), 1 for those right of it, 0 above it. */
    public int side() {
        return side;
    }

    public boolean top() {
        return up > 0;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
