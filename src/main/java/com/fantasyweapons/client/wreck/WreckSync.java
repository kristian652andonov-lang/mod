package com.fantasyweapons.client.wreck;

import com.fantasyweapons.network.WreckPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Puts the sections the world's destruction rewrote straight into the client's chunk, whole: no block update for each
 * block, no light worked out block by block (both of which, for the hundreds of thousands of blocks a power swallows,
 * would stall the game for seconds). Where the land was opened the sky's light is let in at once, so the new walls are
 * lit, not black; the true light arrives soon after, once the server has lit the chunk. Block entities whose blocks are
 * gone go with them.
 */
public final class WreckSync {
    /**
     * At the lower effect qualities, the land waiting to go in (by chunk, in the order it came), a share of it each
     * tick ({@link com.fantasyweapons.config.ClientConfig.VfxQuality#sectionsPerTick()}): a great power rewrites tens
     * of thousands of sections, and a weak computer taking them all in one frame freezes for seconds.
     */
    private static final java.util.LinkedHashMap<Long, List<WreckPayload>> WAITING = new java.util.LinkedHashMap<>();
    /** The world the waiting land is for. */
    private static ClientLevel waitingIn;

    private WreckSync() {
    }

    public static void apply(WreckPayload p) {
        if (com.fantasyweapons.config.ClientConfig.quality().sectionsPerTick() <= 0 && WAITING.isEmpty()) {
            land(p);
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != waitingIn) {
            WAITING.clear();
            waitingIn = level;
        }
        WAITING.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(p.chunkX(), p.chunkZ()), k -> new ArrayList<>()).add(p);
    }

    /** Every client tick: puts in a share of the land waiting, oldest first. */
    public static void tick() {
        if (WAITING.isEmpty()) return;
        if (Minecraft.getInstance().level != waitingIn) {
            // another world (or none): the land was for the one left behind
            WAITING.clear();
            return;
        }
        int share = com.fantasyweapons.config.ClientConfig.quality().sectionsPerTick();
        if (share <= 0) share = Integer.MAX_VALUE;
        var it = WAITING.values().iterator();
        while (share > 0 && it.hasNext()) {
            List<WreckPayload> chunk = it.next();
            it.remove();
            for (WreckPayload p : chunk) {
                land(p);
                share -= p.sectionYs().length;
            }
        }
    }

    /** Whether any land waits to go in. */
    public static boolean waiting() {
        return !WAITING.isEmpty();
    }

    /** Puts in at once whatever waits for the chunk (before the game changes one of its blocks). */
    public static void flush(int chunkX, int chunkZ) {
        if (WAITING.isEmpty()) return;
        List<WreckPayload> chunk = WAITING.remove(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
        if (chunk != null) for (WreckPayload p : chunk) land(p);
    }

    /** Forgets what waits for the chunk (it has come whole, as it is now, or gone out of sight). */
    public static void drop(int chunkX, int chunkZ) {
        if (!WAITING.isEmpty()) WAITING.remove(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
    }

    private static void land(WreckPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        LevelChunk chunk = level.getChunkSource().getChunk(p.chunkX(), p.chunkZ(), ChunkStatus.FULL, false);
        if (chunk == null) return;
        var skyLight = level.getLightEngine().getLayerListener(LightLayer.SKY);
        List<BlockPos> gone = new ArrayList<>();
        for (int k = 0; k < p.sectionYs().length; k++) {
            int sy = p.sectionYs()[k], index = chunk.getSectionIndexFromSectionY(sy);
            if (index < 0 || index >= chunk.getSectionsCount()) continue;
            LevelChunkSection section = chunk.getSection(index);
            PalettedContainer<BlockState> states = section.getStates();
            PalettedContainer<BlockState> fresh = states.recreate();
            fresh.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(p.sections()[k])));
            // the sky let straight into the light the game already holds for the section, where the land opened: handing
            // the light engine new data instead would set it checking every edge of every section, the very work this
            // spares; the true light replaces it when the server has lit the chunk
            DataLayer sky = skyLight.getDataLayerData(SectionPos.of(p.chunkX(), sy, p.chunkZ()));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState was = states.get(x, y, z), now = fresh.get(x, y, z);
                        if (was == now) continue;
                        if (sky != null && now.isAir()) sky.set(x, y, z, 15);
                        if (was.hasBlockEntity() && was.getBlock() != now.getBlock())
                            gone.add(new BlockPos((p.chunkX() << 4) + x, (sy << 4) + y, (p.chunkZ() << 4) + z));
                    }
                }
            }
            states.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(p.sections()[k])));
            section.recalcBlockCounts();
            level.setSectionDirtyWithNeighbors(p.chunkX(), sy, p.chunkZ());
        }
        for (BlockPos pos : gone) chunk.removeBlockEntity(pos);
        EnumSet<Heightmap.Types> maps = EnumSet.noneOf(Heightmap.Types.class);
        for (Map.Entry<Heightmap.Types, Heightmap> e : chunk.getHeightmaps()) maps.add(e.getKey());
        if (!maps.isEmpty()) Heightmap.primeHeightmaps(chunk, maps);
    }
}
