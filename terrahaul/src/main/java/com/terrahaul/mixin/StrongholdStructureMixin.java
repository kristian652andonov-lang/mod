package com.terrahaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.terrahaul.worldgen.TerrahaulGeneration;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Strongholds are moved to a random depth between the world bottom and sea level. In a Terrahaul
 * world that would put most of them hundreds of blocks below their vanilla range, so the bottom
 * used for that roll is kept at the vanilla Y=-64.
 */
@Mixin(StrongholdStructure.class)
public abstract class StrongholdStructureMixin {
    @WrapOperation(method = "generatePieces", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getMinY()I"))
    private static int terrahaul$vanillaDepthRange(ChunkGenerator generator, Operation<Integer> original) {
        int minY = original.call(generator);
        return TerrahaulGeneration.isTerrahaul(generator) ? Math.max(minY, -64) : minY;
    }
}
