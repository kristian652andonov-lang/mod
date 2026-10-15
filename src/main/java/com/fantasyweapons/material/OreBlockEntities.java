package com.fantasyweapons.material;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Gives naturally generated ores their block entity. The ores are drawn entirely by it (an animated model), but the
 * vanilla ore feature writes blocks straight into the chunk's sections, so ores placed by world generation had none and
 * were invisible. Whenever a chunk loads on the server - freshly generated or from an old save - every ore in it that
 * lacks one gets it before the chunk is sent to anyone. Only sections whose palette holds an ore are searched.
 */
public final class OreBlockEntities {
    private OreBlockEntities() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        boolean added = false;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof MaterialOreBlock)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!(section.getBlockState(x, y, z).getBlock() instanceof MaterialOreBlock)) continue;
                        pos.set(minX + x, baseY + y, minZ + z);
                        if (chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) != null) continue;
                        chunk.getBlockEntity(pos.immutable(), LevelChunk.EntityCreationType.IMMEDIATE);
                        added = true;
                    }
                }
            }
        }
        if (added) chunk.setUnsaved(true);
    }
}
