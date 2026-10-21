package com.terrahaul.devtools;

import com.terrahaul.worldgen.density.TerrainModels;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * The world a devtool studies, from its seed argument.
 * <p>
 * The game does not use the world seed as the terrain model's seed: the model's seed is derived
 * from the world seed through the {@code terrahaul:terrain_seed} noise ({@link TerrainModels#seedFrom}),
 * and the router's cave noises are seeded from the world seed itself. A seed argument {@code N}
 * therefore means the world a player gets with seed {@code N}: its model seed is derived as the
 * game derives it. {@code model:N} gives the terrain model seed {@code N} directly (what the
 * devtools took a plain seed for before), with the caves of world seed {@code N}: a world no
 * player can create, but the same terrain as older runs of the tools.
 */
final class GameWorld {
    final long worldSeed;
    final long modelSeed;
    /** Whether {@link #modelSeed} was derived from {@link #worldSeed} as in the game. */
    final boolean derived;
    final TerrainModel model;
    private RouterTerrain router;

    private GameWorld(long worldSeed, long modelSeed, boolean derived) {
        this.worldSeed = worldSeed;
        this.modelSeed = modelSeed;
        this.derived = derived;
        this.model = new TerrainModel(modelSeed, TerrainSettings.DEFAULT);
    }

    /** The world of a seed argument: {@code N} (a world seed) or {@code model:N} (a terrain model seed). */
    static GameWorld of(String arg) {
        if (arg.startsWith("model:")) {
            long seed = Long.parseLong(arg.substring("model:".length()));
            return new GameWorld(seed, seed, false);
        }
        long seed = Long.parseLong(arg);
        return new GameWorld(seed, modelSeed(seed), true);
    }

    /** The terrain model seed the game uses for a world seed. */
    static long modelSeed(long worldSeed) {
        // RandomState: the overworld's (non-legacy) positional random, the noise by its name
        return TerrainModels.seedFrom(RouterTerrain.noise(new XoroshiroRandomSource(worldSeed).forkPositional(), "terrahaul:terrain_seed"));
    }

    /** The noise fill with the router's caves (built on first use; it reads the router files). */
    synchronized RouterTerrain router() {
        if (router == null) {
            router = new RouterTerrain(worldSeed, model);
        }
        return router;
    }

    /** Short name for file names: the world seed, or {@code model-N}. */
    String label() {
        return derived ? Long.toString(worldSeed) : "model" + modelSeed;
    }

    @Override
    public String toString() {
        return derived ? "world seed " + worldSeed + " (terrain model seed " + modelSeed + ")"
            : "terrain model seed " + modelSeed + " (caves of world seed " + worldSeed + ")";
    }
}
