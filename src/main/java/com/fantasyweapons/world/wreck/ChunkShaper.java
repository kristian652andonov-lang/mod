package com.fantasyweapons.world.wreck;

/** Rewrites the blocks of one chunk column for a {@link WorldWreck} job. Runs off the main thread, on a copy of the chunk. */
@FunctionalInterface
public interface ChunkShaper {
    void shape(WreckChunk chunk);

    /**
     * Whether it may change anything in the chunk at chunk coordinates {@code (chunkX, chunkZ)}: a chunk it cannot reach
     * is left out of the job altogether, not even copied.
     */
    default boolean reaches(int chunkX, int chunkZ) {
        return true;
    }
}
