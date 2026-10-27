package com.terrahaul.mixin;

import com.terrahaul.worldgen.placement.FeatureBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Widens the {@code minecraft:biome} placement filter for ores under Terrahaul's taller ground:
 * where the 3D biome turns an ore away, the biome vanilla's terrain of the same climate would
 * have at that height may still accept it (see {@link FeatureBiomes}). The filter is only ever
 * widened, and only in Terrahaul worlds with {@code oreDepthFix} on; everything else returns
 * vanilla's answer untouched.
 */
@Mixin(BiomeFilter.class)
public abstract class BiomeFilterMixin {
    @Inject(method = "shouldPlace", at = @At("RETURN"), cancellable = true)
    private void terrahaul$vanillaHeightBiome(PlacementContext context, RandomSource random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && context.topFeature().isPresent() && FeatureBiomes.widens(context.topFeature().get(), context.generator())
            && FeatureBiomes.vanillaHeightBiome(context.getLevel(), context.generator(),
                context.getLevel().getLevel().getChunkSource().randomState().sampler(), pos, context.topFeature().get(),
                Heightmap.Types.OCEAN_FLOOR_WG)) {
            cir.setReturnValue(true);
        }
    }
}
