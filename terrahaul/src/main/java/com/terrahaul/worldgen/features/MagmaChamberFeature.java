package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.MagmaChamber;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.VolcanoZones;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

/**
 * Feature {@code terrahaul:magma_chamber}: builds the magma chamber under each volcano and the
 * conduit that joins it to the crater (see {@link MagmaChamber}), sealed in a wall of basalt,
 * blackstone and magma.
 * <p>
 * Each chunk builds only its own blocks, from the plan alone: it marks the cavity in its columns
 * and in a margin of {@link MagmaChamber#WALL} blocks around them, and every cell of the chunk
 * within that distance of the cavity (Chebyshev, so the wall is at least that thick in every
 * direction, along the diagonals thicker) becomes wall, whatever was there: rock, the air of a cave
 * or a carver's ravine, the water of an aquifer, a vanilla lava pool. Neighbouring chunks mark the
 * same cavity, so the wall closes across chunk borders, and lava never meets anything but lava,
 * the wall, or the cavern's own air above the chamber's lake. Two things are left alone: the open
 * air and lava above the ground of a column (the crater's lake around the vent and the sky over
 * it: the wall stops at the column's top solid block, read from its ocean-floor heightmap before
 * anything is changed), and blocks that features may not replace (spawners, chests). Lava that
 * lies higher than a few blocks under the lowest crater floor is the crater's lake and is kept too;
 * lower down, lava in the wall is sealed over like everything else. Plants that a neighbour
 * decorated earlier grew on this chunk's rock lose their hold when that rock becomes cavity, and go
 * ({@link #clearAcross}).
 * <p>
 * Runs in the {@code underground_decoration} step: after the carvers, the crater's lava (placed
 * in the {@code lakes} step), ores, dungeons and geodes of the chunk, before springs (which cannot
 * open in basalt or blackstone) and the second floating-terrain pass. The lava is placed still, as
 * vanilla's lakes are; the fall from the conduit's mouth is placed as falling lava over the
 * chamber's lake, which is how the game itself settles it. Does nothing outside Terrahaul worlds
 * or away from volcanoes (one cached model sample per chunk).
 */
public final class MagmaChamberFeature extends Feature<NoneFeatureConfiguration> {
    /** Time spent and chunks built, for the test report (chunks that touch a chamber only). */
    public static final LongAdder NANOS = new LongAdder();
    public static final LongAdder CHUNKS = new LongAdder();
    /** Blocks placed, and all calls with their time (most return after the first test). */
    public static final LongAdder BLOCKS = new LongAdder();
    public static final LongAdder CALLS = new LongAdder();
    public static final LongAdder CALL_NANOS = new LongAdder();

    /** Development switch ({@code -Dterrahaul.debug.noMagmaChamber=true}): build no chambers, for a baseline to compare with. */
    private static final boolean DISABLED = Boolean.getBoolean("terrahaul.debug.noMagmaChamber");
    /** Plants of finished neighbours taken away because the rock they grew on became the cavity. */
    public static final LongAdder PLANTS = new LongAdder();

    /** Search radius for the volcano a chunk's chamber belongs to (well beyond any conduit's reach). */
    private static final double REACH = 1200.0;
    private static final int P = MagmaChamber.WALL;
    private static final int SIDE = 16 + 2 * P;
    /** Lava this far below the lowest crater floor or deeper lies in the wall only if it leaked in. */
    private static final int CRATER_MARGIN = 6;

    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    private static final BlockState FALL = Fluids.FLOWING_LAVA.getFlowing(8, true).createLegacyBlock();
    private static final BlockState AIR = Blocks.CAVE_AIR.defaultBlockState();
    private static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState BASALT = Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    private static final BlockState SMOOTH_BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();

    private static final ThreadLocal<Raster> RASTER = ThreadLocal.withInitial(Raster::new);

    public MagmaChamberFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        long t0 = System.nanoTime();
        try {
            return build(context, t0);
        } finally {
            CALLS.increment();
            CALL_NANOS.add(System.nanoTime() - t0);
        }
    }

    private static boolean build(FeaturePlaceContext<NoneFeatureConfiguration> context, long t0) {
        WorldGenLevel level = context.level();
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model == null || DISABLED) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX();
        int z0 = chunk.getMinBlockZ();
        // every chamber lies near the centre of its volcano, well inside the cone
        if (!VolcanoZones.near(model, x0 + 8, z0 + 8)) {
            return false;
        }
        TerrainModel.VolcanoSite v = model.nearestVolcano(x0 + 8, z0 + 8, REACH * model.settings().horizontalScale());
        if (v == null) {
            return false;
        }
        MagmaChamber chamber = model.magmaChamber(v);
        if (!chamber.touches(x0, z0, x0 + 15, z0 + 15)) {
            return false;
        }
        Raster r = RASTER.get();
        int ylo = Math.max(chamber.minY - P, level.getMinBuildHeight());
        int yhi = Math.min(chamber.maxY + P, level.getMaxBuildHeight() - 1);
        if (yhi < ylo || !r.mark(chamber, x0, z0, ylo, yhi)) {
            return false;
        }
        r.dilate();
        int placed = emit(level, chamber, r, x0, z0);
        BLOCKS.add(placed);
        CHUNKS.increment();
        NANOS.add(System.nanoTime() - t0);
        return placed > 0;
    }

    /** Places the cavity and the wall in the chunk's columns; returns the number of blocks changed. */
    private static int emit(WorldGenLevel level, MagmaChamber chamber, Raster r, int x0, int z0) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int lavaKeep = chamber.craterFloor - CRATER_MARGIN;
        int placed = 0;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx;
                int z = z0 + dz;
                if (!r.columnUsed(dx + P, dz + P)) {
                    continue;
                }
                // the top solid block of the column before anything changes: the wall stays below it
                int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                for (int y = r.ylo; y <= r.yhi; y++) {
                    int i = r.index(dx + P, y, dz + P);
                    int cavity = r.cavity[i];
                    if (cavity == MagmaChamber.NONE && !r.near[i]) {
                        continue;
                    }
                    BlockState current = level.getBlockState(pos.set(x, y, z));
                    if (current.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
                        continue;
                    }
                    BlockState target;
                    if (cavity != MagmaChamber.NONE) {
                        target = cavity == MagmaChamber.LAVA ? LAVA : cavity == MagmaChamber.FALL ? FALL : AIR;
                    } else {
                        if (y >= ground || y >= lavaKeep && current.getFluidState().is(FluidTags.LAVA) || isWall(current)) {
                            continue;
                        }
                        target = wall(chamber, r, dx + P, y, dz + P, x, z);
                    }
                    if (current != target) {
                        level.setBlock(pos, target, 2);
                        placed++;
                        if (cavity != MagmaChamber.NONE && (dx == 0 || dx == 15 || dz == 0 || dz == 15)) {
                            clearAcross(level, pos, x, y, z, dx, dz);
                        }
                    }
                }
            }
        }
        return placed;
    }

    /**
     * A neighbouring chunk decorated before this one has already hollowed out its side of the
     * chamber and may have grown glow lichen, sculk veins (where the chamber lies in a deep dark
     * biome) or vines in its air on the faces of this chunk's rock, which has just become cavity:
     * they lose the face that hung on that rock, and go if they have none left, as at the first
     * block update; other plants that cannot stay go too (see {@link UnsupportedPlantCleanup}).
     * Only the cells across the chunk's border from a changed cell are looked at; they lie in the
     * 3x3 chunk area the feature may write.
     */
    private static void clearAcross(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int dx, int dz) {
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        BlockState here = level.getBlockState(pos.set(x, y, z));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int lx = dx + d.getStepX();
            int lz = dz + d.getStepZ();
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                continue;
            }
            side.set(x + d.getStepX(), y, z + d.getStepZ());
            BlockState there = level.getBlockState(side);
            if (there.getBlock() instanceof MultifaceBlock || there.getBlock() instanceof VineBlock) {
                // what the block would do when told its neighbour changed: drop that face
                BlockState updated = there.updateShape(d.getOpposite(), here, level, side, pos);
                if (updated != there) {
                    level.setBlock(side, updated, 2);
                    PLANTS.increment();
                }
            } else if (UnsupportedPlantCleanup.isPlant(there) && !UnsupportedPlantCleanup.stays(there, level, side)) {
                UnsupportedPlantCleanup.clearColumn(level, side, side.getX(), y, y, side.getZ());
                PLANTS.increment();
            }
        }
        pos.set(x, y, z);
    }

    /** Volcanic rock that already seals as well as the wall would (the crater's own floor, mostly). */
    private static boolean isWall(BlockState state) {
        return state.is(Blocks.BASALT) || state.is(Blocks.BLACKSTONE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.SMOOTH_BASALT)
            || state.is(Blocks.OBSIDIAN);
    }

    /**
     * The wall's block at a cell: glowing magma and blackstone where it touches lava (the lake's
     * bed, the conduit's sides), a hot shore of magma at the lake's edge, columnar basalt,
     * blackstone and smooth basalt in patches on the shore and the dome, with veins of magma, and
     * blackstone and basalt deeper in.
     */
    private static BlockState wall(MagmaChamber chamber, Raster r, int i, int y, int j, int x, int z) {
        int touch = r.touching(i, y, j);
        double n = chamber.noise3(x / 7.0, y / 7.0, z / 7.0);
        if ((touch & Raster.TOUCH_LAVA) != 0) {
            return n > -0.15 ? MAGMA : n > -0.5 ? BLACKSTONE : BASALT;
        }
        if ((touch & Raster.TOUCH_AIR) != 0) {
            if ((touch & Raster.AIR_ABOVE) != 0 && y <= chamber.lake) {
                // the shore right at the lava's edge
                return n > -0.4 ? MAGMA : BLACKSTONE;
            }
            double vein = chamber.noise3(x / 3.0 + 40.0, y / 9.0, z / 3.0 - 40.0);
            if (vein > 0.62) {
                return MAGMA;
            }
            return n > 0.25 ? BASALT : n > -0.3 ? BLACKSTONE : SMOOTH_BASALT;
        }
        return n > 0.35 ? BASALT : BLACKSTONE;
    }

    /**
     * The cavity of a chamber in one chunk and a margin around it, and the cells within the wall's
     * thickness of it. Reused per thread; arrays grow to the tallest chunk met.
     */
    private static final class Raster {
        static final int TOUCH_LAVA = 1;
        static final int TOUCH_AIR = 2;
        /** The cavity's air lies right above the cell (the cell is ground under the cavern). */
        static final int AIR_ABOVE = 4;

        byte[] cavity = new byte[0];
        boolean[] near = new boolean[0];
        private boolean[] pass = new boolean[0];
        /** Columns of the chunk whose cells the cavity or its wall reach. */
        private final boolean[] used = new boolean[SIDE * SIDE];
        private final int[] col = new int[MagmaChamber.COLUMN_OUT];
        private final double[] axis = new double[2];
        int ylo;
        int yhi;
        private int height;

        int index(int i, int y, int j) {
            return ((y - ylo) * SIDE + j) * SIDE + i;
        }

        boolean columnUsed(int i, int j) {
            return used[j * SIDE + i];
        }

        /**
         * Marks the cavity in the chunk at {@code (x0, z0)} and its margin, between {@code ylo}
         * and {@code yhi}; false if none of it lies there.
         */
        boolean mark(MagmaChamber chamber, int x0, int z0, int ylo, int yhi) {
            this.ylo = ylo;
            this.yhi = yhi;
            height = yhi - ylo + 1;
            int size = SIDE * SIDE * height;
            if (cavity.length < size) {
                cavity = new byte[size];
                near = new boolean[size];
                pass = new boolean[size];
            } else {
                java.util.Arrays.fill(cavity, 0, size, (byte) 0);
            }
            boolean any = false;
            int bx0 = x0 - P;
            int bz0 = z0 - P;
            // the chamber, column by column
            for (int j = 0; j < SIDE; j++) {
                for (int i = 0; i < SIDE; i++) {
                    chamber.column(bx0 + i, bz0 + j, col);
                    int from = Math.max(col[MagmaChamber.FLOOR], ylo);
                    int to = Math.min(col[MagmaChamber.CEILING] - 1, yhi);
                    for (int y = from; y <= to; y++) {
                        cavity[index(i, y, j)] = (byte) (y < chamber.lake ? MagmaChamber.LAVA
                            : col[MagmaChamber.UNDER_MOUTH] != 0 ? MagmaChamber.FALL : MagmaChamber.AIR);
                        any = true;
                    }
                }
            }
            // the conduit, layer by layer
            for (int y = Math.max(chamber.top, ylo); y <= Math.min(chamber.conduitTop, yhi); y++) {
                chamber.axis(y, axis);
                double rc = chamber.conduitRadius(y);
                int i0 = Math.max(0, (int) Math.floor(axis[0] - rc) - bx0);
                int i1 = Math.min(SIDE - 1, (int) Math.ceil(axis[0] + rc) - bx0);
                int j0 = Math.max(0, (int) Math.floor(axis[1] - rc) - bz0);
                int j1 = Math.min(SIDE - 1, (int) Math.ceil(axis[1] + rc) - bz0);
                for (int j = j0; j <= j1; j++) {
                    for (int i = i0; i <= i1; i++) {
                        double dx = bx0 + i + 0.5 - axis[0];
                        double dz = bz0 + j + 0.5 - axis[1];
                        if (dx * dx + dz * dz < rc * rc) {
                            cavity[index(i, y, j)] = MagmaChamber.LAVA;
                            any = true;
                        }
                    }
                }
            }
            return any;
        }

        /**
         * Marks every cell of the chunk within {@link MagmaChamber#WALL} blocks (Chebyshev) of the
         * cavity: a running count of cavity cells over a window along x, then z, then y. Only the
         * cells each later pass reads are computed.
         */
        void dilate() {
            int w = P;
            // along x: every row of the margin, for the chunk's own columns
            for (int y = 0; y < height; y++) {
                for (int j = 0; j < SIDE; j++) {
                    int row = (y * SIDE + j) * SIDE;
                    int count = 0;
                    for (int i = 0; i < w; i++) {
                        count += cavity[row + i] != 0 ? 1 : 0;
                    }
                    for (int i = 0; i < SIDE; i++) {
                        if (i + w < SIDE && cavity[row + i + w] != 0) {
                            count++;
                        }
                        if (i - w - 1 >= 0 && cavity[row + i - w - 1] != 0) {
                            count--;
                        }
                        pass[row + i] = count > 0;
                    }
                }
            }
            // along z, then along y, for the chunk's own columns
            java.util.Arrays.fill(used, false);
            boolean[] zPass = near;
            for (int y = 0; y < height; y++) {
                for (int i = P; i < P + 16; i++) {
                    int count = 0;
                    for (int j = 0; j < w; j++) {
                        count += pass[(y * SIDE + j) * SIDE + i] ? 1 : 0;
                    }
                    for (int j = 0; j < SIDE; j++) {
                        if (j + w < SIDE && pass[(y * SIDE + j + w) * SIDE + i]) {
                            count++;
                        }
                        if (j - w - 1 >= 0 && pass[(y * SIDE + j - w - 1) * SIDE + i]) {
                            count--;
                        }
                        zPass[(y * SIDE + j) * SIDE + i] = count > 0;
                    }
                }
            }
            for (int j = P; j < P + 16; j++) {
                for (int i = P; i < P + 16; i++) {
                    int stride = SIDE * SIDE;
                    int base = j * SIDE + i;
                    int count = 0;
                    for (int y = 0; y < w && y < height; y++) {
                        count += zPass[y * stride + base] ? 1 : 0;
                    }
                    boolean any = false;
                    // the result goes into pass, which the y window no longer reads
                    for (int y = 0; y < height; y++) {
                        if (y + w < height && zPass[(y + w) * stride + base]) {
                            count++;
                        }
                        if (y - w - 1 >= 0 && zPass[(y - w - 1) * stride + base]) {
                            count--;
                        }
                        pass[y * stride + base] = count > 0;
                        any |= count > 0;
                    }
                    used[base] = any;
                }
            }
            // near holds the z pass; swap so near holds the result
            boolean[] t = near;
            near = pass;
            pass = t;
        }

        /** Which cavity contents touch a cell across its six faces (bits of the TOUCH_ constants). */
        int touching(int i, int y, int j) {
            int bits = 0;
            if (y + 1 <= yhi) {
                int c = cavity[index(i, y + 1, j)];
                bits |= c == MagmaChamber.AIR ? TOUCH_AIR | AIR_ABOVE : c != 0 ? TOUCH_LAVA : 0;
            }
            if (y - 1 >= ylo) {
                bits |= kind(cavity[index(i, y - 1, j)]);
            }
            bits |= kind(cavity[index(i - 1, y, j)]) | kind(cavity[index(i + 1, y, j)]);
            bits |= kind(cavity[index(i, y, j - 1)]) | kind(cavity[index(i, y, j + 1)]);
            return bits;
        }

        private static int kind(byte c) {
            return c == 0 ? 0 : c == MagmaChamber.AIR ? TOUCH_AIR : TOUCH_LAVA;
        }
    }
}
