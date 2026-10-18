package com.terrahaul.mixin;

import com.terrahaul.Terrahaul;
import com.terrahaul.worldgen.TerrahaulGeneration;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla treats every Overworld whose dimension type is not {@code minecraft:overworld} as an
 * "experimental" world and shows the "Worlds using Experimental Settings are not supported" warning.
 * Terrahaul needs its own dimension type for the expanded height, so its own preset (the
 * {@code terrahaul:overworld} dimension type with Terrahaul's generator and the vanilla Overworld
 * biome parameter list) is reported as stable. Nothing else changes: any other custom dimension
 * still gets the vanilla warning.
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {
    @Inject(method = "isStableOverworld", at = @At("HEAD"), cancellable = true)
    private static void terrahaul$terrahaulPresetIsStable(LevelStem stem, CallbackInfoReturnable<Boolean> cir) {
        if (stem.type().is(Terrahaul.id("overworld")) && TerrahaulGeneration.isTerrahaul(stem.generator())
            && (!(stem.generator().getBiomeSource() instanceof MultiNoiseBiomeSource source) || source.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD))) {
            cir.setReturnValue(true);
        }
    }
}
