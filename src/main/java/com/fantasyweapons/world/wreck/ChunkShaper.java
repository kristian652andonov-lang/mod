package com.fantasyweapons.world.wreck;

/** Rewrites the blocks of one chunk column for a {@link WorldWreck} job. Runs on the server thread. */
@FunctionalInterface
public interface ChunkShaper {
    void shape(WreckChunk chunk);
}
