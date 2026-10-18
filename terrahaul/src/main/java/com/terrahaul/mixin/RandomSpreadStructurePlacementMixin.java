package com.terrahaul.mixin;

import com.terrahaul.worldgen.structure.TerrainAwareStructures;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets {@link TerrainAwareStructures} move surface structures within their region to suitable
 * terrain in Terrahaul worlds. Both structure generation and {@code /locate} go through this
 * method, so they stay consistent.
 */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadStructurePlacementMixin {
    @Shadow
    @Final
    private int spacing;
    @Shadow
    @Final
    private int separation;
    @Shadow
    @Final
    private RandomSpreadType spreadType;

    @Inject(method = "getPotentialStructureChunk", at = @At("RETURN"), cancellable = true)
    private void terrahaul$terrainAware(long seed, int regionX, int regionZ, CallbackInfoReturnable<ChunkPos> cir) {
        int salt = ((StructurePlacementAccessor) this).terrahaul$salt();
        ChunkPos chosen = TerrainAwareStructures.choose(seed, regionX, regionZ, salt, spacing, separation, spreadType, cir.getReturnValue());
        if (chosen != null) {
            cir.setReturnValue(chosen);
        }
    }
}
