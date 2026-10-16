package com.fantasyweapons.world.wreck;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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
        LevelChunkSection section = sections[(y - minY) >> 4];
        BlockState old = section.getBlockState(x & 15, y & 15, z & 15);
        if (old == state || unbreakable(old)) return false;
        cursor.set(x, y, z);
        // a block entity still waiting in the chunk's saved data (a chunk loaded for the rewrite) is brought to life first,
        // or it would later be loaded onto a block that is no longer there
        if (old.hasBlockEntity()) chunk.getBlockEntity(cursor, LevelChunk.EntityCreationType.CHECK);
        section.setBlockState(x & 15, y & 15, z & 15, state);
        if (old.hasBlockEntity()) chunk.removeBlockEntity(cursor);
        if (state.hasBlockEntity()) chunk.getBlockEntity(cursor.immutable(), LevelChunk.EntityCreationType.IMMEDIATE);
        if (PoiTypes.hasPoi(old) || PoiTypes.hasPoi(state)) level.onBlockStateChange(cursor.immutable(), old, state);
        changed++;
        return true;
    }

    public boolean clear(int x, int y, int z) {
        return set(x, y, z, Blocks.AIR.defaultBlockState());
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
        return state.getBlock().defaultDestroyTime() < 0;
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
