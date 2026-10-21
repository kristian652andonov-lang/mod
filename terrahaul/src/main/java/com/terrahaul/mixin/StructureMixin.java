package com.terrahaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.structure.UndergroundStructures;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Moves the pieces of an underground structure below the terrain in Terrahaul worlds (see
 * {@link UndergroundStructures}), right after they are built and before the structure start is
 * made from them. {@code Structure.generate} is where chunk generation and {@code /place structure}
 * create every structure start; {@code /locate} only asks for the generation point, which this does
 * not change.
 */
@Mixin(Structure.class)
public abstract class StructureMixin {
    @WrapOperation(method = "generate", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;build()Lnet/minecraft/world/level/levelgen/structure/pieces/PiecesContainer;"))
    private PiecesContainer terrahaul$keepUnderground(StructurePiecesBuilder builder, Operation<PiecesContainer> original,
                                                      @Local(argsOnly = true) RegistryAccess registryAccess,
                                                      @Local(argsOnly = true) ChunkGenerator generator,
                                                      @Local(argsOnly = true) RandomState randomState,
                                                      @Local(argsOnly = true) LevelHeightAccessor heightAccessor) {
        PiecesContainer pieces = original.call(builder);
        if (TerrahaulGeneration.isTerrahaul(generator) && UndergroundStructures.keepsUnderground(registryAccess, (Structure) (Object) this)) {
            UndergroundStructures.bury(pieces.pieces(), generator, randomState, heightAccessor);
        }
        return pieces;
    }
}
