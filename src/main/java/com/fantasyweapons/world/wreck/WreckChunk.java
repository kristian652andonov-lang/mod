package com.fantasyweapons.world.wreck;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.Map;

/**
 * One chunk column opened for a {@link WorldWreck} rewrite. It is a copy: the shaper works on copies of the chunk's
 * sections, which lets the work run off the main thread, on every core at once; then, on the main thread,
 * {@link #apply} swaps the changed sections in whole - no block updates, no physics, no lighting block by block - and
 * the column is relit. Coordinates are world coordinates inside this chunk. Unbreakable blocks (bedrock, barriers,
 * portal frames, the Worldforge...) are never replaced.
 */
public final class WreckChunk {
    private static final Direction[] ALL = Direction.values(), SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private final ServerLevel level;
    private final int chunkX, chunkZ;
    /** The copies the shaper works on, and which of them it changed. */
    private final LevelChunkSection[] sections;
    private final boolean[] touched;
    private final int minX;
    private final int minZ;
    private final int minY;
    private final int maxY;
    private final int[] top = new int[256];
    private final int[] ground = new int[256];
    private final RandomSource random;
    private int changed;
    /** The columns this job may change (null: all). */
    private boolean[] mask;
    /** Changed blocks with a block entity or a point of interest on either side: the main thread sees to them. */
    private final LongOpenHashSet special = new LongOpenHashSet();
    /** Which sections may hold water; the water beside blocks opened up; and opened blocks on the chunk's edge. */
    private final boolean[] wet;
    private final LongOpenHashSet wake = new LongOpenHashSet();
    private final LongArrayList rim = new LongArrayList();
    /** The heightmaps this chunk keeps, and once planned: their new data, and the changed sections as sent to players. */
    private final Heightmap.Types[] heightmaps;
    private long[][] heights;
    private int[] sentY;
    private byte[][] sent;

    /** Opens a copy of {@code chunk} for a rewrite (call on the main thread; the copy may then be shaped on any thread). */
    WreckChunk(ServerLevel level, LevelChunk chunk, long seed) {
        this.level = level;
        this.chunkX = chunk.getPos().x;
        this.chunkZ = chunk.getPos().z;
        this.minX = chunk.getPos().getMinBlockX();
        this.minZ = chunk.getPos().getMinBlockZ();
        this.minY = level.getMinBuildHeight();
        this.maxY = level.getMaxBuildHeight();
        this.random = RandomSource.create(seed ^ chunk.getPos().toLong() * 0x9E3779B97F4A7C15L);
        LevelChunkSection[] live = chunk.getSections();
        sections = new LevelChunkSection[live.length];
        touched = new boolean[live.length];
        wet = new boolean[live.length];
        for (int i = 0; i < live.length; i++) {
            sections[i] = new LevelChunkSection(own(live[i].getStates()), live[i].getBiomes());
            wet[i] = !live[i].hasOnlyAir() && live[i].maybeHas(s -> s.getFluidState().is(FluidTags.WATER));
        }
        for (int i = 0; i < 256; i++) {
            // a chunk's getHeight is already the highest block (one below the level's "first free" height)
            top[i] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i & 15, i >> 4);
            ground[i] = Integer.MIN_VALUE;
        }
        heightmaps = chunk.getHeightmaps().stream().map(Map.Entry::getKey).toArray(Heightmap.Types[]::new);
    }

    /**
     * A copy of a section's blocks that is wholly its own. ({@link PalettedContainer#copy()} is not: its palette still
     * calls back to the original when it has to grow, which would resize the live chunk's blocks - from another thread -
     * and leave the copy unable to take the new block.)
     */
    private static PalettedContainer<BlockState> own(PalettedContainer<BlockState> live) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        live.write(buf);
        PalettedContainer<BlockState> copy = live.recreate();
        copy.read(buf);
        return copy;
    }

    /** The level (read only what never changes, such as its height: the shaper may be running off the main thread). */
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
        int si = (y - minY) >> 4;
        LevelChunkSection section = sections[si];
        BlockState old = section.getBlockState(x & 15, y & 15, z & 15);
        if (old == state || unbreakable(old)) return false;
        section.setBlockState(x & 15, y & 15, z & 15, state);
        touched[si] = true;
        long at = BlockPos.asLong(x, y, z);
        if (state.isAir()) opened(x, y, z);
        if (old.hasBlockEntity() || state.hasBlockEntity() || PoiTypes.hasPoi(old) || PoiTypes.hasPoi(state)) special.add(at);
        changed++;
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

    public boolean clear(int x, int y, int z) {
        return set(x, y, z, Blocks.AIR.defaultBlockState());
    }

    /** {@link #clear}, bedrock too (only bedrock: other unbreakable blocks stay), opening the bottom of the world. */
    public boolean erase(int x, int y, int z) {
        if (y >= minY && y < maxY && get(x, y, z).is(Blocks.BEDROCK) && (mask == null || mask[(x & 15) | (z & 15) << 4])) {
            int si = (y - minY) >> 4;
            sections[si].setBlockState(x & 15, y & 15, z & 15, Blocks.AIR.defaultBlockState());
            touched[si] = true;
            opened(x, y, z);
            changed++;
            return true;
        }
        return clear(x, y, z);
    }

    /**
     * Clears the whole section at {@code index} (counted from the bottom of the world) in one go, for a section lying
     * wholly inside what a shaper takes away - when it can: not with water in it (that must be woken as the land opens),
     * nor block entities, points of interest or unbreakable blocks (those go block by block, or stay).
     *
     * @return whether the section is now empty; if not, nothing was changed and the shaper clears it block by block
     */
    public boolean clearSection(int index) {
        if (mask != null || index < 0 || index >= sections.length) return false;
        LevelChunkSection s = sections[index];
        if (s.hasOnlyAir()) return true;
        if (wet[index] || s.maybeHas(st -> st.hasBlockEntity() || PoiTypes.hasPoi(st) || unbreakable(st) || !st.getFluidState().isEmpty())) return false;
        sections[index] = new LevelChunkSection(new PalettedContainer<>(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES), s.getBiomes());
        touched[index] = true;
        changed += 4096;
        return true;
    }

    /** How many sections the chunk has, bottom to top ({@link #clearSection} counts them from {@link #minY()}). */
    public int sectionCount() {
        return sections.length;
    }

    /**
     * A block was opened up: water beside it is noted, to be woken when the rewrite is applied - blocks changed this way
     * tell nothing round them, so without that the sea would stand cut off in a wall instead of pouring in. Water in the
     * next chunk is looked for on the main thread, from the opened blocks on this one's edge.
     */
    private void opened(int x, int y, int z) {
        int si = (y - minY) >> 4, ly = y & 15, lx = x & 15, lz = z & 15;
        if (lx == 0 || lx == 15 || lz == 0 || lz == 15) rim.add(BlockPos.asLong(x, y, z));
        boolean here = wet[si] || ly == 0 && si > 0 && wet[si - 1] || ly == 15 && si + 1 < wet.length && wet[si + 1];
        if (!here) return;
        for (Direction d : ALL) {
            int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
            if (ny < minY || ny >= maxY || !contains(nx, nz)) continue;
            if (get(nx, ny, nz).getFluidState().is(FluidTags.WATER)) wake.add(BlockPos.asLong(nx, ny, nz));
        }
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

    /** A copy of every section's blocks as they are now in this rewrite, for shapers that move blocks between chunks. */
    @SuppressWarnings("unchecked")
    public PalettedContainer<BlockState>[] snapshot() {
        PalettedContainer<BlockState>[] out = new PalettedContainer[sections.length];
        for (int i = 0; i < sections.length; i++) out[i] = own(sections[i].getStates());
        return out;
    }

    /** A copy of every section's blocks of a loaded chunk as they are now (call on the main thread). */
    @SuppressWarnings("unchecked")
    public static PalettedContainer<BlockState>[] snapshot(LevelChunk chunk) {
        LevelChunkSection[] secs = chunk.getSections();
        PalettedContainer<BlockState>[] out = new PalettedContainer[secs.length];
        for (int i = 0; i < secs.length; i++) out[i] = own(secs[i].getStates());
        return out;
    }

    public int changed() {
        return changed;
    }

    /**
     * Finishes the plan once the shaper is done (on the same thread): the chunk's heightmaps worked out afresh from the
     * new blocks, and the changed sections packed as they will be sent to the players who see the chunk.
     */
    void finish() {
        if (changed == 0) return;
        heights = new long[heightmaps.length][];
        int bits = Mth.ceillog2(maxY - minY + 1);
        SimpleBitStorage[] store = new SimpleBitStorage[heightmaps.length];
        for (int k = 0; k < heightmaps.length; k++) store[k] = new SimpleBitStorage(bits, 256);
        int highest = sections.length - 1;
        while (highest >= 0 && sections[highest].hasOnlyAir()) highest--;
        int startY = minY + (highest + 1) * 16 - 1;
        for (int i = 0; i < 256; i++) {
            int x = i & 15, z = i >> 4, left = heightmaps.length;
            boolean[] found = new boolean[heightmaps.length];
            for (int y = startY; y >= minY && left > 0; y--) {
                BlockState s = sections[(y - minY) >> 4].getBlockState(x, y & 15, z);
                if (s.isAir()) continue;
                for (int k = 0; k < heightmaps.length; k++) {
                    if (found[k] || !heightmaps[k].isOpaque().test(s)) continue;
                    found[k] = true;
                    left--;
                    store[k].set(i, y + 1 - minY);
                }
            }
        }
        for (int k = 0; k < heightmaps.length; k++) heights[k] = store[k].getRaw();
        int n = 0;
        for (boolean t : touched) if (t) n++;
        sentY = new int[n];
        sent = new byte[n][];
        int j = 0;
        for (int i = 0; i < sections.length; i++) {
            if (!touched[i]) continue;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            sections[i].getStates().write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            sentY[j] = SectionPos.blockToSectionCoord(minY) + i;
            sent[j++] = bytes;
        }
    }

    /** The section y of each changed section, with {@link #sentSections()}. */
    int[] sentSectionYs() {
        return sentY;
    }

    /** The changed sections' blocks, packed for the players who see the chunk. */
    byte[][] sentSections() {
        return sent;
    }

    /**
     * Puts the planned rewrite into the chunk (main thread): the changed sections swapped in whole, the block entities
     * and points of interest of the blocks that changed seen to, the heightmaps set and the water beside the new space
     * woken.
     */
    void apply(LevelChunk chunk) {
        if (changed == 0) return;
        if (heights == null) finish();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        // what stood where block entities or points of interest change (a block entity still waiting in the chunk's
        // saved data is brought to life first, so it is removed properly and not later loaded onto a block not there)
        Map<Long, BlockState> before = new java.util.HashMap<>();
        var it = special.iterator();
        while (it.hasNext()) {
            long p = it.nextLong();
            BlockState old = chunk.getBlockState(at.set(p));
            before.put(p, old);
            if (old.hasBlockEntity()) chunk.getBlockEntity(at, LevelChunk.EntityCreationType.CHECK);
        }
        LevelChunkSection[] live = chunk.getSections();
        var light = level.getChunkSource().getLightEngine();
        for (int i = 0; i < live.length; i++) {
            if (!touched[i]) continue;
            boolean wasEmpty = live[i].hasOnlyAir();
            sections[i].recalcBlockCounts();
            live[i] = sections[i];
            if (wasEmpty != sections[i].hasOnlyAir())
                light.updateSectionStatus(SectionPos.of(chunkX, SectionPos.blockToSectionCoord(minY) + i, chunkZ), sections[i].hasOnlyAir());
        }
        for (Map.Entry<Long, BlockState> e : before.entrySet()) {
            BlockPos p = BlockPos.of(e.getKey());
            BlockState old = e.getValue(), now = chunk.getBlockState(p);
            if (old == now) continue;
            if (old.hasBlockEntity()) chunk.removeBlockEntity(p);
            if (now.hasBlockEntity()) chunk.getBlockEntity(p, LevelChunk.EntityCreationType.IMMEDIATE);
            if (PoiTypes.hasPoi(old) || PoiTypes.hasPoi(now)) level.onBlockStateChange(p, old, now);
        }
        for (int k = 0; k < heightmaps.length; k++) chunk.setHeightmap(heightmaps[k], heights[k]);
        wakeWater();
    }

    /** Wakes the water beside what this rewrite opened up, in this chunk and the next, so it flows into the new space. */
    private void wakeWater() {
        LevelChunk beside = null;
        for (int i = 0; i < rim.size(); i++) {
            long p = rim.getLong(i);
            int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
            for (Direction d : SIDES) {
                int nx = x + d.getStepX(), nz = z + d.getStepZ();
                if (contains(nx, nz)) continue;
                if (beside == null || beside.getPos().x != nx >> 4 || beside.getPos().z != nz >> 4) beside = level.getChunkSource().getChunkNow(nx >> 4, nz >> 4);
                if (beside != null && beside.getFluidState(nx, y, nz).is(FluidTags.WATER)) wake.add(BlockPos.asLong(nx, y, nz));
            }
        }
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        var it = wake.iterator();
        while (it.hasNext()) {
            at.set(it.nextLong());
            if (level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null) continue;
            FluidState f = level.getFluidState(at);
            if (f.is(FluidTags.WATER)) level.scheduleTick(at.immutable(), f.getType(), f.getType().getTickDelay(level));
        }
    }
}
