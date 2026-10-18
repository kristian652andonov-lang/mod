package com.terrahaul.worldgen.water;

/**
 * A planned water feature (a waterfall with its plunge pool, a forest cascade) that reshapes the
 * ground and sets the water of the columns it covers. Plans are pure functions of the world seed
 * and the terrain model, so every chunk a plan spans builds its own columns of it and the parts
 * fit together. The {@link WaterSolver} treats planned water like any other body: where planned
 * levels meet, it places the falls and rapids between them.
 */
public interface WaterPlan {
    int minX();

    int minZ();

    int maxX();

    int maxZ();

    /**
     * Shapes the column at {@code (x, z)}, whose model ground (top solid block) is
     * {@code natural}. Returns {@code false} if the plan does not cover the column.
     */
    boolean shape(int x, int z, int natural, Shape out);

    /** What a plan makes of one column. */
    final class Shape {
        /** Top solid block of the shaped ground (the column is carved above it and filled below). */
        public int ground;
        /** Exclusive top of the planned water, or {@link WaterSolver#NONE}. */
        public int level;
        /** Look of the water's steps, one of the {@code WaterSolver.STYLE_*} constants. */
        public byte style;
        /** A rib of rock splitting a waterfall's curtain: never a chute. */
        public boolean rib;
        /** Material role of the shaped ground, one of the {@code WaterSolver.ROLE_*} constants. */
        public byte role;

        public void reset(int natural) {
            ground = natural;
            level = WaterSolver.NONE;
            style = WaterSolver.STYLE_FALL;
            rib = false;
            role = WaterSolver.ROLE_NATURAL;
        }
    }
}
