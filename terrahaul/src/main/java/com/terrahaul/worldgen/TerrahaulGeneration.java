package com.terrahaul.worldgen;

import com.terrahaul.worldgen.density.ColumnBoundedDensity;
import com.terrahaul.worldgen.density.TerrainModels;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Helpers that decide whether a generator, noise settings or level belongs to a Terrahaul world.
 * <p>
 * Detection is structural (the final density is a {@code terrahaul:column_bounded} function)
 * rather than by registry key, so copies of the Terrahaul settings in data packs are recognised,
 * while vanilla and other mods' generators are always left untouched by Terrahaul's mixins.
 */
public final class TerrahaulGeneration {
    private static final Map<RandomState, TerrainModel> MODEL_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private TerrahaulGeneration() {
    }

    public static boolean isTerrahaul(NoiseGeneratorSettings settings) {
        return TerrainModels.unwrap(settings.noiseRouter().finalDensity()) instanceof ColumnBoundedDensity;
    }

    public static boolean isTerrahaul(ChunkGenerator generator) {
        return generator instanceof NoiseBasedChunkGenerator noise && isTerrahaul(noise.generatorSettings().value());
    }

    /** The terrain model of a seeded router, or {@code null} if the world is not a Terrahaul world. */
    public static TerrainModel model(RandomState randomState) {
        TerrainModel cached = MODEL_CACHE.get(randomState);
        if (cached != null) {
            return cached;
        }
        TerrainModel found = TerrainModels.find(randomState);
        if (found != null) {
            MODEL_CACHE.put(randomState, found);
        }
        return found;
    }

    public static TerrainModel model(ServerLevel level) {
        if (!isTerrahaul(level.getChunkSource().getGenerator())) {
            return null;
        }
        return model(level.getChunkSource().randomState());
    }

    public static TerrainModel model(WorldGenLevel level) {
        return model(level.getLevel());
    }
}
