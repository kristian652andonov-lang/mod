package com.terrahaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.terrahaul.worldgen.TerrahaulGeneration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Two changes to the surface pass, both only in Terrahaul worlds:
 * <ul>
 *   <li>The surface rules only act on the upper part of each column (deepslate and bedrock are
 *       already placed during the noise fill, see NoiseChunkMixin), so the pass stops 112 blocks
 *       below the lowest surface of the chunk instead of walking down to Y=-512.</li>
 *   <li>Frozen oceans get no noise icebergs. Vanilla raises pillars of packed ice and snow out of
 *       frozen seas, each reaching 7 blocks deeper below sea level than it stands above it (at
 *       most about 60 blocks): in vanilla's shallow oceans their keels meet the floor, but over
 *       Terrahaul's deep oceans they hang as huge blobs a hundred blocks above the sea floor, and
 *       cutting them off where the sea gets deep would leave walls of ice. Frozen seas keep their
 *       sea ice, and the iceberg features, which only form over shallow water there.</li>
 * </ul>
 */
@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemMixin {
    @Unique
    private static final ThreadLocal<Object[]> TERRAHAUL_FLOOR_CACHE = ThreadLocal.withInitial(() -> new Object[2]);

    @WrapOperation(method = "buildSurface(Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/core/Registry;ZLnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getMinBuildHeight()I"))
    private int terrahaul$stopNearSurface(ChunkAccess chunk, Operation<Integer> original, @Local(argsOnly = true) RandomState randomState) {
        int min = original.call(chunk);
        if (TerrahaulGeneration.model(randomState) == null) {
            return min;
        }
        Object[] cache = TERRAHAUL_FLOOR_CACHE.get();
        if (cache[0] != chunk) {
            int lowest = Integer.MAX_VALUE;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    lowest = Math.min(lowest, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z));
                }
            }
            cache[0] = chunk;
            cache[1] = lowest - 112;
        }
        return Math.max(min, (Integer) cache[1]);
    }

    @WrapOperation(method = "buildSurface(Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/core/Registry;ZLnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceSystem;frozenOceanExtension(ILnet/minecraft/world/level/biome/Biome;Lnet/minecraft/world/level/chunk/BlockColumn;Lnet/minecraft/core/BlockPos$MutableBlockPos;III)V"))
    private void terrahaul$noFloatingIcebergs(SurfaceSystem system, int minSurfaceLevel, Biome biome, BlockColumn column, BlockPos.MutableBlockPos pos,
                                              int x, int z, int height, Operation<Void> original, @Local(argsOnly = true) RandomState randomState) {
        if (TerrahaulGeneration.model(randomState) == null) {
            original.call(system, minSurfaceLevel, biome, column, pos, x, z, height);
        }
    }
}
