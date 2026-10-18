package com.terrahaul.worldgen;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

/**
 * A {@link WorldGenerationContext} describing the vanilla Overworld height range (Y -64..319),
 * used to resolve vanilla height anchors in Terrahaul worlds.
 */
public final class VanillaHeightContext {
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;

    private static final LevelHeightAccessor VANILLA = LevelHeightAccessor.create(MIN_Y, HEIGHT);
    private static final Map<ChunkGenerator, WorldGenerationContext> CACHE = new WeakHashMap<>();

    private VanillaHeightContext() {
    }

    public static WorldGenerationContext get(ChunkGenerator generator) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(generator, g -> new WorldGenerationContext(g, VANILLA));
        }
    }
}
