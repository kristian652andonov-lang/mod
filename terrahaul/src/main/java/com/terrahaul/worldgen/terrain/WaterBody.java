package com.terrahaul.worldgen.terrain;

/**
 * Kinds of planned surface water bodies a model corner can belong to (see
 * {@link ColumnSample#bodyKind}). The water solver turns them into blocks.
 */
public final class WaterBody {
    public static final int NONE = 0;
    /** A river channel; its level may step down along the course. */
    public static final int RIVER = 1;
    /** A lake (any {@link LakeType} except craters); one level over the whole lake. */
    public static final int LAKE = 2;
    /** The lake in a volcano's crater. */
    public static final int CRATER = 3;
    /** A lava pool in a volcano's crater. */
    public static final int LAVA = 4;

    private WaterBody() {
    }
}
