package com.terrahaul.worldgen.structure;

import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Terrain-aware placement of villages, pillager outposts, woodland mansions and the small temples
 * (desert pyramid, jungle temple, swamp hut, igloo) in Terrahaul worlds.
 * <p>
 * Vanilla picks one random chunk in every structure region. When the terrain model says that chunk
 * is steep, a cliff or under water, a few more candidates from the same region are scored and the
 * flattest dry one is used instead. Candidates stay in the region's legal range, so spacing and
 * separation are unchanged, and the choice depends only on the seed and the terrain, so
 * {@code /locate} and generation always agree. The structure's own biome checks still apply.
 * <p>
 * Ocean monuments use the same mechanism with their own score: the flattest deep sea floor under
 * the building's footprint.
 * <p>
 * Vanilla surface structure sets are recognised by their salt (so data packs that only change
 * spacing keep working). Other worlds, and Terrahaul worlds with {@code terrainAwareStructures}
 * off, use the vanilla choice.
 */
public final class TerrainAwareStructures {
    /**
     * Salt of the ocean monument set. Monuments are not on the surface, but they need a flat sea
     * floor: in Terrahaul's deep, uneven oceans a monument settled on a slope cuts a square pit with
     * sheer walls into it (see OceanMonumentStructureMixin), so their region also picks its flattest
     * deep candidate.
     */
    public static final int MONUMENT_SALT = 10387313;
    /** Half the width of a monument; the building starts this far west and north of its chunk. */
    public static final int MONUMENT_HALF = 29;
    /** Salts of the vanilla structure sets that sit on the surface. */
    private static final IntSet SALTS = IntSet.of(
        10387312,   // villages
        165745296,  // pillager outposts
        10387319,   // woodland mansions
        14357617,   // desert pyramids
        14357618,   // igloos
        14357619,   // jungle temples
        14357620,   // swamp huts
        MONUMENT_SALT
    );
    private static final int CANDIDATES = 8;
    private static final double GOOD_ENOUGH = 10.0;
    private static final int CACHE_LIMIT = 50_000;
    private static final double SEA_LEVEL = 63.0;

    private static volatile long activeSeed;
    private static volatile TerrainModel activeModel;
    private static final Map<RegionKey, ChunkPos> CACHE = new ConcurrentHashMap<>();

    private TerrainAwareStructures() {
    }

    /**
     * Called when a Terrahaul chunk generator creates its structure state, before any chunk of the
     * world is generated.
     */
    public static void activate(long seed, TerrainModel model) {
        CACHE.clear();
        activeSeed = seed;
        activeModel = model;
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        activeModel = null;
        CACHE.clear();
    }

    /**
     * Returns the chunk to use for a structure region, or {@code null} to keep vanilla's choice.
     *
     * @param chunkX any chunk x in the region (as passed to {@code getPotentialStructureChunk})
     */
    public static ChunkPos choose(long seed, int chunkX, int chunkZ, int salt, int spacing, int separation, RandomSpreadType spread, ChunkPos vanilla) {
        TerrainModel model = activeModel;
        if (model == null || seed != activeSeed || !SALTS.contains(salt) || !TerrahaulConfig.terrainAwareStructures()) {
            return null;
        }
        int regionX = Math.floorDiv(chunkX, spacing);
        int regionZ = Math.floorDiv(chunkZ, spacing);
        RegionKey key = new RegionKey(salt, regionX, regionZ);
        ChunkPos cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        ChunkPos best = vanilla;
        ColumnSample sample = new ColumnSample();
        double bestScore = score(model, vanilla, sample, salt);
        if (bestScore > GOOD_ENOUGH) {
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureWithSalt(seed, regionX, regionZ, salt ^ 0x7E44A);
            int range = spacing - separation;
            for (int i = 1; i < CANDIDATES; i++) {
                ChunkPos candidate = new ChunkPos(regionX * spacing + spread.evaluate(random, range), regionZ * spacing + spread.evaluate(random, range));
                double score = score(model, candidate, sample, salt);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        if (CACHE.size() > CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(key, best);
        return best;
    }

    private static double score(TerrainModel model, ChunkPos chunk, ColumnSample s, int salt) {
        return salt == MONUMENT_SALT ? monumentScore(model, chunk, s) : surfaceScore(model, chunk, s);
    }

    /**
     * Lower is better: the sea floor's height range over the monument's footprint, plus a penalty
     * where the water is too shallow to hold the building (vanilla sets it 24 blocks below sea level).
     */
    private static double monumentScore(TerrainModel model, ChunkPos chunk, ColumnSample s) {
        int x0 = chunk.getMinBlockX() - MONUMENT_HALF;
        int z0 = chunk.getMinBlockZ() - MONUMENT_HALF;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double penalty = 0.0;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                model.sampleInto(x0 + i * (2 * MONUMENT_HALF - 1) / 4, z0 + j * (2 * MONUMENT_HALF - 1) / 4, s);
                min = Math.min(min, s.height);
                max = Math.max(max, s.height);
                if (s.height > SEA_LEVEL - 24.0) {
                    penalty += 20.0;
                }
            }
        }
        return (max - min) + penalty;
    }

    /** Lower is better: height range across ~60 blocks plus penalties for water and cliffs. */
    private static double surfaceScore(TerrainModel model, ChunkPos chunk, ColumnSample s) {
        int cx = chunk.getMiddleBlockX();
        int cz = chunk.getMiddleBlockZ();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double penalty = 0.0;
        for (int i = 0; i < 9; i++) {
            int x = cx;
            int z = cz;
            if (i > 0) {
                double angle = (i - 1) * Math.PI / 4.0;
                x += (int) Math.round(Math.cos(angle) * 28.0);
                z += (int) Math.round(Math.sin(angle) * 28.0);
            }
            model.sampleInto(x, z, s);
            min = Math.min(min, s.height);
            max = Math.max(max, s.height);
            if (s.waterLevel > s.height + 0.5) {
                penalty += 25.0;
            } else if (s.height < SEA_LEVEL + 1.0) {
                // ground at or below sea level is flooded: open sea or a sea-level lake
                penalty += 40.0;
            }
            penalty += s.cliff * 15.0;
        }
        return (max - min) + penalty;
    }

    private record RegionKey(int salt, int x, int z) {
    }
}
