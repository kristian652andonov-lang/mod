package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.Tags;

/**
 * Removes floating pieces of terrain: small groups of rock, soil and sand near the surface that
 * touch nothing but air and water. They are left where cave tunnels and carvers cut just under the
 * surface, at the tips of the sharpest peaks, where the 3D terrain detail is interpolated on
 * Minecraft's noise cells, and where streams and pools are carved from the model's surface while
 * the real ground stands a block or two higher; none of them is meant to be there.
 * <p>
 * The feature runs twice in every chunk. The first pass is the only feature of the
 * {@code raw_generation} step, before any vegetation, structure or Terrahaul feature of the chunk
 * exists; the second runs in the {@code fluid_springs} step, after every feature that carves or
 * builds terrain (surface water, formations, sea grottos) and before any plant or tree. A
 * neighbouring chunk is at least carved at that time, so the terrain around is final; it may
 * already be decorated, but its features only ever keep a group (see {@link #isTerrain}).
 * <p>
 * The search starts from every terrain block of the chunk within 64 blocks of the column's top
 * that has air or fluid right below it, and follows the connected solid blocks, lowest first, so
 * that a group standing on the ground reaches the ground after a few steps. A group is floating
 * when it ends before it spans 16 blocks across or 48 blocks from top to bottom, or holds
 * {@value #MAX_BLOCKS} blocks. These tests depend on the group alone, so every chunk the group
 * touches reaches the same verdict, and a group narrower than a chunk always lies within the 3x3
 * chunk area this chunk may read and write. All its blocks are removed, in this chunk and its
 * neighbours: below sea level they become water where water is beside or above them (a rock
 * adrift in the sea), elsewhere air (the surface water seals any open hold of its lakes later),
 * and the worldgen heightmaps follow, so later features do not build on the vanished blocks. The
 * second pass keeps every group that touches water or lava from above or the side, since the
 * surface water seals rock around the water it places. Chunks under a shipwreck or an ocean ruin
 * are left alone: those place every part at a height measured from the heightmaps of the whole
 * footprint, which must not change between the chunks. Does nothing outside Terrahaul worlds.
 */
public final class FloatingTerrainCleanup extends Feature<NoneFeatureConfiguration> {
    /** Search window below the top solid block of each column. */
    private static final int WINDOW = 64;
    /** How far from its top block down a group must reach to stand on the ground. */
    private static final int ANCHOR_DEPTH = 48;
    /** Groups this large are taken to be part of the ground. */
    private static final int MAX_BLOCKS = 4096;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final Heightmap.Types[] WORLDGEN_HEIGHTMAPS = {Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG};

    /** Whether this is the second pass, which keeps groups beside fluids. */
    private final boolean late;

    public FloatingTerrainCleanup(Codec<NoneFeatureConfiguration> codec, boolean late) {
        super(codec);
        this.late = late;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (TerrahaulGeneration.model(level) == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        for (Structure structure : level.getChunk(chunk.x, chunk.z).getAllReferences().keySet()) {
            if (structure.type() == StructureType.SHIPWRECK || structure.type() == StructureType.OCEAN_RUIN) {
                return false;
            }
        }
        int x0 = chunk.getMinBlockX();
        int z0 = chunk.getMinBlockZ();
        int floor = level.getMinBuildHeight();
        int seaLevel = context.chunkGenerator().getSeaLevel();
        Search search = new Search(level, floor, late);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                // features keep the final heightmaps up to date, not the worldgen ones
                int top = Math.max(level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z), level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z)) - 1;
                int bottom = Math.max(floor + 1, top - WINDOW);
                boolean solidAbove = false;
                BlockState above = null;
                // walk down the column: a solid block with a non-solid block right below it is the
                // underside of something, the only place where a floating group can be found
                for (int y = top; y >= bottom - 1; y--) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    boolean solid = isSolid(state);
                    if (solidAbove && !solid && isTerrain(above)) {
                        long start = BlockPos.asLong(x, y + 1, z);
                        LongArrayList group = search.floating(start);
                        if (group != null) {
                            remove(level, group, pos, seaLevel);
                            changed = true;
                        }
                    }
                    solidAbove = solid;
                    above = state;
                }
            }
        }
        return changed;
    }

    /**
     * Removes the blocks of a floating group, all of which lie within the 3x3 chunk area this chunk
     * may write (see {@link Search#floating}). Every block becomes air first; then, below sea level,
     * water spreads into the cleared blocks from the water beside and above them, so a rock adrift
     * in the sea leaves no air pocket however its blocks were ordered.
     */
    private static void remove(WorldGenLevel level, LongArrayList group, BlockPos.MutableBlockPos pos, int seaLevel) {
        for (int i = 0; i < group.size(); i++) {
            pos.set(group.getLong(i));
            level.setBlock(pos, AIR, 2);
            updateWorldgenHeightmaps(level, pos, AIR);
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = group.size() - 1; i >= 0; i--) {
                pos.set(group.getLong(i));
                if (pos.getY() < seaLevel && level.getBlockState(pos).isAir() && besideWater(level, pos)) {
                    level.setBlock(pos, WATER, 2);
                    updateWorldgenHeightmaps(level, pos, WATER);
                    grew = true;
                }
            }
        }
    }

    /**
     * Keeps the worldgen heightmaps of the block's chunk in step: during decoration a chunk only
     * updates its final heightmaps, but features (rock formations, fallen logs, ocean vents) place
     * their blocks on the worldgen ones. A map the chunk does not hold yet (a chunk reloaded from
     * disk keeps only the final ones) is left to be built from the blocks when first read; creating
     * it here would make it report the bottom of the world.
     */
    private static void updateWorldgenHeightmaps(WorldGenLevel level, BlockPos pos, BlockState state) {
        ChunkAccess chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        for (Heightmap.Types type : WORLDGEN_HEIGHTMAPS) {
            if (chunk.hasPrimedHeightmap(type)) {
                chunk.getOrCreateHeightmapUnprimed(type).update(pos.getX() & 15, pos.getY(), pos.getZ() & 15, state);
            }
        }
    }

    /** Whether water lies beside or above the position. */
    private static boolean besideWater(WorldGenLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (d != Direction.DOWN) {
                FluidState fluid = level.getFluidState(pos.relative(d));
                if (fluid.is(Fluids.WATER) && fluid.isSource()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Anything that can hold a block up: every block except air and pure fluids. */
    public static boolean isSolid(BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlock);
    }

    /**
     * Natural terrain materials that the noise fill and the surface rule produce. A group holding
     * anything else (wood, leaves, plants, structure blocks, ice from an iceberg) is always kept:
     * it was placed on purpose by a neighbouring chunk's feature or structure.
     */
    public static boolean isTerrain(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
            || state.is(BlockTags.TERRACOTTA) || state.is(Tags.Blocks.ORES) || state.is(Tags.Blocks.SANDSTONE_BLOCKS)
            || state.is(Blocks.GRAVEL) || state.is(Blocks.CALCITE) || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.BASALT)
            || state.is(Blocks.BLACKSTONE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.CLAY) || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE)
            || state.is(Blocks.DIRT_PATH);
    }

    /** One chunk's search state: groups already known to stand on the ground. */
    private static final class Search {
        /** A group this wide or deep stands on the ground, or reaches beyond the readable area. */
        private static final int SPAN = 16;

        private final WorldGenLevel level;
        private final int floor;
        private final boolean keepWet;
        private final LongOpenHashSet grounded = new LongOpenHashSet();
        private final LongOpenHashSet visited = new LongOpenHashSet();
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Search(WorldGenLevel level, int floor, boolean keepWet) {
            this.level = level;
            this.floor = floor;
            this.keepWet = keepWet;
        }

        /**
         * The blocks of the group holding {@code start} if it floats, or {@code null} if it stands
         * on the ground or holds anything but terrain. The start lies in the chunk being decorated;
         * the search stops before the group spans {@value #SPAN} blocks across, so it never reads
         * beyond the neighbouring chunks.
         */
        LongArrayList floating(long start) {
            if (grounded.contains(start)) {
                return null;
            }
            int minX = BlockPos.getX(start);
            int maxX = minX;
            int minZ = BlockPos.getZ(start);
            int maxZ = minZ;
            int maxY = BlockPos.getY(start);
            visited.clear();
            LongArrayList group = new LongArrayList();
            // lowest block first: on the ground the search heads straight down
            LongHeapPriorityQueue queue = new LongHeapPriorityQueue((a, b) -> Integer.compare(BlockPos.getY(a), BlockPos.getY(b)));
            visited.add(start);
            queue.enqueue(start);
            boolean anchored = false;
            while (!queue.isEmpty()) {
                long k = queue.dequeueLong();
                group.add(k);
                int x = BlockPos.getX(k);
                int y = BlockPos.getY(k);
                int z = BlockPos.getZ(k);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                maxY = Math.max(maxY, y);
                if (grounded.contains(k) || maxY - y >= ANCHOR_DEPTH || y <= floor + 1 || maxX - minX >= SPAN || maxZ - minZ >= SPAN
                    || group.size() > MAX_BLOCKS || !isTerrain(level.getBlockState(pos.set(x, y, z)))) {
                    anchored = true;
                    break;
                }
                for (Direction d : Direction.values()) {
                    long n = BlockPos.offset(k, d);
                    if (!visited.add(n)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos.set(n));
                    if (isSolid(state)) {
                        queue.enqueue(n);
                    } else if (keepWet && d != Direction.DOWN && !state.getFluidState().isEmpty()) {
                        // may hold a lake or river placed by the surface water
                        anchored = true;
                    }
                }
                if (anchored) {
                    break;
                }
            }
            if (anchored) {
                // everything reached so far is connected to the anchor (or to a kept block)
                grounded.addAll(group);
                return null;
            }
            return group;
        }
    }
}
