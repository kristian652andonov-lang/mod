package com.fantasyweapons.client.wreck;

import com.fantasyweapons.network.WreckPayload;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.Map;

/**
 * Writes the blocks the world's destruction changed straight into the client's chunk: no block update for each, no
 * light worked out block by block (both of which, for the hundreds of thousands of blocks a hole swallows, would stall
 * the game for seconds). Where a block was opened up the sky's light is let in at once, so the new walls are lit, not
 * black; the true light arrives soon after, once the server has lit the chunk. A block with a block entity on either
 * side of the change goes the ordinary way, so nothing is left behind.
 */
public final class WreckSync {
    private WreckSync() {
    }

    public static void apply(WreckPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        LevelChunk chunk = level.getChunkSource().getChunk(p.chunkX(), p.chunkZ(), ChunkStatus.FULL, false);
        if (chunk == null) return;
        LevelLightEngine light = level.getLightEngine();
        Map<Long, DataLayer> sky = new Long2ObjectOpenHashMap<>();
        LongOpenHashSet dirty = new LongOpenHashSet();
        Heightmap[] maps = chunk.getHeightmaps().stream().map(Map.Entry::getValue).toArray(Heightmap[]::new);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int[] runs = p.runs();
        int minX = p.chunkX() << 4, minZ = p.chunkZ() << 4;
        for (int i = 0; i + 3 < runs.length; i += 4) {
            int col = runs[i], lx = col & 15, lz = (col >> 4) & 15;
            BlockState state = Block.stateById(runs[i + 3]);
            boolean open = state.isAir();
            for (int y = runs[i + 1], end = y + runs[i + 2]; y < end; y++) {
                if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
                int si = chunk.getSectionIndex(y);
                LevelChunkSection section = chunk.getSection(si);
                BlockState old = section.getBlockState(lx, y & 15, lz);
                if (old == state) continue;
                at.set(minX + lx, y, minZ + lz);
                if (old.hasBlockEntity() || state.hasBlockEntity()) {
                    level.setBlock(at, state, 19);
                    continue;
                }
                section.setBlockState(lx, y & 15, lz, state);
                for (Heightmap map : maps) map.update(lx, y, lz, state);
                long sp = SectionPos.asLong(p.chunkX(), SectionPos.blockToSectionCoord(y), p.chunkZ());
                dirty.add(sp);
                if (open) {
                    // the sky let straight into the light the game already holds for the section: handing the light
                    // engine new data instead would set it checking every edge of every section, the very work this
                    // spares; the true light replaces it when the server has lit the chunk
                    DataLayer layer = sky.computeIfAbsent(sp, k -> light.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(k)));
                    if (layer != null) layer.set(lx, y & 15, lz, 15);
                }
            }
        }
        var it = dirty.iterator();
        while (it.hasNext()) {
            long sp = it.nextLong();
            level.setSectionDirtyWithNeighbors(SectionPos.x(sp), SectionPos.y(sp), SectionPos.z(sp));
        }
    }
}
