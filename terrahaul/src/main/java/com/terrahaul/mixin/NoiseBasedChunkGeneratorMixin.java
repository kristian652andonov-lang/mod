package com.terrahaul.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.terrahaul.worldgen.TerrahaulFluidPicker;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.density.ColumnBoundedDensity;
import com.terrahaul.worldgen.density.TerrainModels;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Terrahaul-only adjustments to the noise chunk generator. Every change first checks that the
 * generator uses Terrahaul's noise router, so vanilla and other mods' dimensions are unaffected.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @Shadow
    @Final
    private Holder<NoiseGeneratorSettings> settings;

    /** Lava level relative to the world bottom instead of the hard-coded Y=-54 (see TerrahaulFluidPicker). */
    @Inject(method = "createFluidPicker", at = @At("HEAD"), cancellable = true)
    private static void terrahaul$deepWorldFluidPicker(NoiseGeneratorSettings settings, CallbackInfoReturnable<Aquifer.FluidPicker> cir) {
        if (TerrahaulGeneration.isTerrahaul(settings)) {
            cir.setReturnValue(new TerrahaulFluidPicker(settings.noiseSettings().minY() + 10, settings.seaLevel(), settings.defaultFluid()));
        }
    }

    /**
     * Stops the noise fill one cell above the highest block the terrain of this chunk can reach.
     * The cells above would be interpolated, run through the aquifer and discarded as air; in a
     * 2,544-block-tall world that was the single largest cost of chunk generation.
     */
    @ModifyVariable(method = "doFill", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int terrahaul$limitFillToTerrain(int cellCountY, @Local(argsOnly = true) RandomState random,
                                             @Local(argsOnly = true) ChunkAccess chunk, @Local(argsOnly = true, ordinal = 0) int minCellY) {
        DensityFunction finalDensity = TerrainModels.unwrap(random.router().finalDensity());
        if (!(finalDensity instanceof ColumnBoundedDensity bounded)) {
            return cellCountY;
        }
        TerrainModel model = bounded.model();
        if (model == null) {
            return cellCountY;
        }
        ChunkPos pos = chunk.getPos();
        // overhangs hang out up to 16 blocks from the top of a cliff that may stand in the next
        // chunk, so the highest surface within 16 blocks of the chunk bounds the fill
        double maxSurface = model.maxHeightInArea(pos.getMinBlockX() - 16, pos.getMinBlockZ() - 16, pos.getMaxBlockX() + 17, pos.getMaxBlockZ() + 17);
        int cellHeight = this.settings.value().noiseSettings().getCellHeight();
        int topY = (int) Math.ceil(Math.max(maxSurface, this.settings.value().seaLevel() + 2) + bounded.margin());
        int topCell = Mth.floorDiv(topY, cellHeight) + 1;
        int limited = topCell - minCellY + 1;
        return Mth.clamp(limited, 1, cellCountY);
    }
}
