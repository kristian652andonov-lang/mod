package com.terrahaul.worldgen.density;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Shares one {@link TerrainModel} between all Terrahaul density functions of the same world.
 * <p>
 * A density function cannot see the world seed directly; the only seeded object Minecraft hands
 * it is a {@link NormalNoise} created from the seed and the noise's registry key. Sampling that
 * noise at fixed points yields a value that is stable for a given world seed, which is folded into
 * the 64-bit seed of the terrain model. The router of every Terrahaul world references the same
 * {@code terrahaul:terrain_seed} noise, so all functions of one world resolve to one model.
 */
public final class TerrainModels {
    private static final Map<Key, TerrainModel> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, TerrainModel> eldest) {
            return size() > 8;
        }
    };

    private TerrainModels() {
    }

    public static TerrainModel get(long seed, TerrainSettings settings) {
        Key key = new Key(seed, settings);
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(key, k -> new TerrainModel(k.seed, k.settings));
        }
    }

    public static long seedFrom(NormalNoise noise) {
        long h = 0x51ED27A3C0FFEE11L;
        for (int i = 0; i < 6; i++) {
            double v = noise.getValue(i * 31.73 + 0.371, i * 17.31 + 0.253, i * 11.17 + 0.127);
            h = SeedMixer.mix(h ^ Double.doubleToLongBits(v) + i);
        }
        return h;
    }

    /** Finds the terrain model used by a seeded noise router, or {@code null} for non-Terrahaul worlds. */
    public static TerrainModel find(RandomState randomState) {
        return find(randomState.router().finalDensity());
    }

    public static TerrainModel find(DensityFunction function) {
        DensityFunction f = unwrap(function);
        if (f instanceof ColumnBoundedDensity bounded) {
            return bounded.model();
        }
        if (f instanceof TerrainDensityFunction terrain) {
            return terrain.model();
        }
        return null;
    }

    public static DensityFunction unwrap(DensityFunction function) {
        DensityFunction f = function;
        while (f instanceof DensityFunctions.HolderHolder holder) {
            f = holder.function().value();
        }
        return f;
    }

    private record Key(long seed, TerrainSettings settings) {
    }
}
