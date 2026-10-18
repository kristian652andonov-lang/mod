package com.terrahaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.terrahaul.worldgen.TerrahaulGeneration;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps mineshafts within their vanilla depth range in Terrahaul worlds (see StrongholdStructureMixin). */
@Mixin(MineshaftStructure.class)
public abstract class MineshaftStructureMixin {
    @WrapOperation(method = "generatePiecesAndAdjust", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getMinY()I"))
    private int terrahaul$vanillaDepthRange(ChunkGenerator generator, Operation<Integer> original) {
        int minY = original.call(generator);
        return TerrahaulGeneration.isTerrahaul(generator) ? Math.max(minY, -64) : minY;
    }
}
