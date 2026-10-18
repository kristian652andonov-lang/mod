package com.terrahaul.mixin;

import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.structure.TerrainAwareStructures;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Registers a Terrahaul world's terrain for terrain-aware structure placement. */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "createState", at = @At("RETURN"))
    private void terrahaul$registerTerrain(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed,
                                           CallbackInfoReturnable<ChunkGeneratorStructureState> cir) {
        if (TerrahaulGeneration.isTerrahaul((ChunkGenerator) (Object) this)) {
            TerrainModel model = TerrahaulGeneration.model(randomState);
            if (model != null) {
                TerrainAwareStructures.activate(seed, model);
            }
        }
    }
}
