package com.fantasyweapons.world.wreck;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * One chunk column opened for a {@link WorldWreck} rewrite. Reads and writes go straight into the chunk sections (no
 * neighbour updates, no physics, no lighting per block): the whole column is relit and resent once the shaper is done.
 * Coordinates are world coordinates inside this chunk. Unbreakable blocks (bedrock, barriers, portal frames...) are
 * never replaced.
 */
public final class WreckChunk {
    private final ServerLevel level;
    private final LevelChunk chunk;
    private final LevelChunkSection[] sections;
    private final int minX;
    private final int minZ;
    private final int minY;
    private final int maxY;
    private final int[] top = new int[256];
    private final int[] ground = new int[256];
    private final RandomSource random;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private int changed;
    /** The columns this job may change (null: all), and every block it changed (packed positions). */
    private boolean[] mask;
    private final it.unimi.dsi.fastutil.longs.LongArrayList changes = new it.unimi.dsi.fastutil.longs.LongArrayList();
    private static final Direction[] ALL = Direction.values(), SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    /** Which sections may hold water, and the water beside blocks this rewrite opened up (woken afterwards so it flows). */
    private final boolean[] wet;
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet wake = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    @org.jetbrains.annotations.Nullable
    private LevelChunk beside;

    WreckChunk(ServerLevel level, LevelChunk chunk, long seed) {
        this.level = level;
        this.chunk = chunk;
        this.sections = chunk.getSections();
        this.minX = chunk.getPos().getMinBlockX();
        this.minZ = chunk.getPos().getMinBlockZ();
        this.minY = level.getMinBuildHeight();
        this.maxY = level.getMaxBuildHeight();
        this.random = RandomSource.create(seed ^ chunk.getPos().toLong() * 0x9E3779B97F4A7C15L);
        for (int i = 0; i < 256; i++) {
            // a chunk's getHeight is already the highest block (one below the level's "first free" height)
            top[i] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i & 15, i >> 4);
            ground[i] = Integer.MIN_VALUE;
        }
        wet = new boolean[sections.length];
        for (int i = 0; i < sections.length; i++) wet[i] = !sections[i].hasOnlyAir() && sections[i].maybeHas(s -> s.getFluidState().is(FluidTags.WATER));
    }

    public ServerLevel level() {
        return level;
    }

    public RandomSource random() {
        return random;
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    /** Lowest buildable y (the bedrock floor in the overworld). */
    public int minY() {
        return minY;
    }

    /** One above the highest buildable y. */
    public int maxY() {
        return maxY;
    }

    public boolean contains(int x, int z) {
        return x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16;
    }

    public BlockState get(int x, int y, int z) {
        if (y < minY || y >= maxY) return Blocks.AIR.defaultBlockState();
        return sections[(y - minY) >> 4].getBlockState(x & 15, y & 15, z & 15);
    }

    /** @return whether the block changed (unbreakable blocks and out-of-range heights are left alone) */
    public boolean set(int x, int y, int z, BlockState state) {
        if (y < minY || y >= maxY) return false;
        if (mask != null && !mask[(x & 15) | (z & 15) << 4]) return false;
        LevelChunkSection section = sections[(y - minY) >> 4];
        BlockState old = section.getBlockState(x & 15, y & 15, z & 15);
        if (old == state || unbreakable(old)) return false;
        cursor.set(x, y, z);
        // a block entity still waiting in the chunk's saved data (a chunk loaded for the rewrite) is brought to life first,
        // or it would later be loaded onto a block that is no longer there
        if (old.hasBlockEntity()) chunk.getBlockEntity(cursor, LevelChunk.EntityCreationType.CHECK);
        section.setBlockState(x & 15, y & 15, z & 15, state);
        if (state.isAir()) opened(x, y, z);
        if (old.hasBlockEntity()) chunk.removeBlockEntity(cursor);
        if (state.hasBlockEntity()) chunk.getBlockEntity(cursor.immutable(), LevelChunk.EntityCreationType.IMMEDIATE);
        if (PoiTypes.hasPoi(old) || PoiTypes.hasPoi(state)) level.onBlockStateChange(cursor.immutable(), old, state);
        changed++;
        changes.add(BlockPos.asLong(x, y, z));
        return true;
    }

    /** Limits this job to the columns marked in {@code columns} (indexed {@code x | z << 4}), or lifts the limit (null). */
    public void mask(boolean[] columns) {
        mask = columns;
    }

    /** Whether the column is outside this job's part of the chunk: shapers skip it at once. */
    public boolean masked(int x, int z) {
        return mask != null && !mask[(x & 15) | (z & 15) << 4];
    }

    /** The blocks changed, as packed positions (some more than once). */
    it.unimi.dsi.fastutil.longs.LongArrayList changes() {
        return changes;
    }

    /**
     * Brings the chunk's heightmaps up to date with the blocks changed (only the columns touched, each scanning down
     * only where its top was taken away), cheaper than rebuilding them all.
     */
    void updateHeightmaps() {
        var maps = chunk.getHeightmaps().stream().map(java.util.Map.Entry::getValue).toArray(Heightmap[]::new);
        for (int i = 0; i < changes.size(); i++) {
            long p = changes.getLong(i);
            int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
            BlockState state = get(x, y, z);
            for (Heightmap map : maps) map.update(x & 15, y, z & 15, state);
        }
    }

    /**
     * The blocks changed as runs down their columns, for {@link com.fantasyweapons.network.WreckPayload}: column
     * ({@code x | z << 4}), first y, length and block state id, four ints a run.
     */
    int[] runs() {
        long[] keys = new long[changes.size()];
        for (int i = 0; i < keys.length; i++) {
            long p = changes.getLong(i);
            keys[i] = (long) ((BlockPos.getX(p) & 15) | (BlockPos.getZ(p) & 15) << 4) << 32 | (BlockPos.getY(p) - minY);
        }
        java.util.Arrays.sort(keys);
        it.unimi.dsi.fastutil.ints.IntArrayList out = new it.unimi.dsi.fastutil.ints.IntArrayList();
        int col = -1, y0 = 0, len = 0, id = -1;
        long last = -1;
        for (long k : keys) {
            if (k == last) continue;
            last = k;
            int c = (int) (k >>> 32), y = (int) k + minY;
            int s = net.minecraft.world.level.block.Block.getId(get(minX + (c & 15), y, minZ + (c >> 4)));
            if (c == col && y == y0 + len && s == id) {
                len++;
                continue;
            }
            if (len > 0) out.addAll(it.unimi.dsi.fastutil.ints.IntList.of(col, y0, len, id));
            col = c;
            y0 = y;
            len = 1;
            id = s;
        }
        if (len > 0) out.addAll(it.unimi.dsi.fastutil.ints.IntList.of(col, y0, len, id));
        return out.toIntArray();
    }

    public boolean clear(int x, int y, int z) {
        return set(x, y, z, Blocks.AIR.defaultBlockState());
    }

    /** {@link #clear}, bedrock too (only bedrock: other unbreakable blocks stay), opening the bottom of the world. */
    public boolean erase(int x, int y, int z) {
        if (y >= minY && y < maxY && get(x, y, z).is(Blocks.BEDROCK) && (mask == null || mask[(x & 15) | (z & 15) << 4])) {
            sections[(y - minY) >> 4].setBlockState(x & 15, y & 15, z & 15, Blocks.AIR.defaultBlockState());
            opened(x, y, z);
            changed++;
            changes.add(BlockPos.asLong(x, y, z));
            return true;
        }
        return clear(x, y, z);
    }

    /**
     * A block was opened up: water beside it is noted, to be woken when the rewrite is done - blocks changed this way
     * tell nothing round them, so without that the sea would stand cut off in a wall instead of pouring in.
     */
    private void opened(int x, int y, int z) {
        int si = (y - minY) >> 4, ly = y & 15, lx = x & 15, lz = z & 15;
        boolean here = wet[si] || ly == 0 && si > 0 && wet[si - 1] || ly == 15 && si + 1 < wet.length && wet[si + 1];
        boolean edge = lx == 0 || lx == 15 || lz == 0 || lz == 15;
        if (!here && !edge) return;
        for (Direction d : here ? ALL : SIDES) {
            int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
            if (ny < minY || ny >= maxY) continue;
            FluidState f;
            if (contains(nx, nz)) {
                if (!here) continue;
                f = get(nx, ny, nz).getFluidState();
            } else {
                int cx = nx >> 4, cz = nz >> 4;
                if (beside == null || beside.getPos().x != cx || beside.getPos().z != cz) beside = level.getChunkSource().getChunkNow(cx, cz);
                if (beside == null) continue;
                f = beside.getFluidState(nx, ny, nz);
            }
            if (f.is(FluidTags.WATER)) wake.add(BlockPos.asLong(nx, ny, nz));
        }
    }

    /** Wakes the water beside what this rewrite opened up, so it flows into the new space (call once it is done). */
    void wakeWater() {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        var it = wake.iterator();
        while (it.hasNext()) {
            at.set(it.nextLong());
            if (level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null) continue;
            FluidState f = level.getFluidState(at);
            if (f.is(FluidTags.WATER)) level.scheduleTick(at.immutable(), f.getType(), f.getType().getTickDelay(level));
        }
        wake.clear();
    }

    /** Wakes a fluid placed by the shaper so it starts flowing (placed fluids otherwise sit still, which is cheaper). */
    public void flow(int x, int y, int z) {
        FluidState fluid = get(x, y, z).getFluidState();
        if (!fluid.isEmpty()) level.scheduleTick(new BlockPos(x, y, z), fluid.getType(), fluid.getType().getTickDelay(level));
    }

    /** Y of the highest block of the column (air above), as it was before this rewrite; {@code minY - 1} if empty. */
    public int top(int x, int z) {
        return top[(x & 15) | (z & 15) << 4];
    }

    /**
     * Y of the highest solid ground block of the column before this rewrite: below trees, plants, snow layers and
     * water. {@code minY - 1} if there is none.
     */
    public int ground(int x, int z) {
        int i = (x & 15) | (z & 15) << 4;
        if (ground[i] != Integer.MIN_VALUE) return ground[i];
        int y = top[i];
        while (y >= minY && !terrain(get(x, y, z))) y--;
        ground[i] = y;
        return y;
    }

    /** Solid ground: not air, fluid, leaves, logs or a plant. */
    public static boolean terrain(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && state.blocksMotion() && !state.canBeReplaced()
                && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS);
    }

    public static boolean unbreakable(BlockState state) {
        // the Worldforge outlasts the end of the world (and is never left half standing)
        return state.getBlock().defaultDestroyTime() < 0 || state.getBlock() instanceof com.fantasyweapons.forge.WorldforgeBlock
                || state.getBlock() instanceof com.fantasyweapons.forge.WorldforgePartBlock;
    }

    /** A copy of every section's blocks as they are now, for shapers that move blocks from one chunk to another. */
    public net.minecraft.world.level.chunk.PalettedContainer<BlockState>[] snapshot() {
        return snapshot(chunk);
    }

    @SuppressWarnings("unchecked")
    public static net.minecraft.world.level.chunk.PalettedContainer<BlockState>[] snapshot(LevelChunk chunk) {
        LevelChunkSection[] secs = chunk.getSections();
        net.minecraft.world.level.chunk.PalettedContainer<BlockState>[] out = new net.minecraft.world.level.chunk.PalettedContainer[secs.length];
        for (int i = 0; i < secs.length; i++) out[i] = secs[i].getStates().copy();
        return out;
    }

    public int changed() {
        return changed;
    }
}
