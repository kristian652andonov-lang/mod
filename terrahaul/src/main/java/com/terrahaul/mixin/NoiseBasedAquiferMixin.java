package com.terrahaul.mixin;

import com.terrahaul.worldgen.TerrahaulFluidPicker;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Terrahaul-only aquifer behaviour, active only with Terrahaul's fluid picker:
 * <ul>
 *   <li>open air far above the terrain: every nearby aquifer cell resolves to the global status,
 *       so the result is plain air, but vanilla would still run the 12-cell aquifer search per
 *       block of a 2,544-block column;</li>
 *   <li>open air below sea level and above the terrain surface is sea water. Vanilla only floods
 *       such channels when a sampled surface lies more than eight blocks below sea level, which
 *       left Terrahaul's wide, shallow river channels dry.</li>
 * </ul>
 */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class NoiseBasedAquiferMixin {
    @Shadow
    @Final
    private NoiseChunk noiseChunk;

    @Shadow
    @Final
    private Aquifer.FluidPicker globalFluidPicker;

    @Shadow
    protected boolean shouldScheduleFluidUpdate;

    @Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
    private void terrahaul$skipOpenAir(DensityFunction.FunctionContext context, double substance, CallbackInfoReturnable<BlockState> cir) {
        if (substance <= 0.0 && this.globalFluidPicker instanceof TerrahaulFluidPicker picker) {
            int y = context.blockY();
            int surface = this.noiseChunk.preliminarySurfaceLevel(context.blockX(), context.blockZ()) + 3;
            if (y >= picker.seaLevel()) {
                if (y > surface + 45) {
                    this.shouldScheduleFluidUpdate = false;
                    cir.setReturnValue(Blocks.AIR.defaultBlockState());
                }
            } else if (y >= surface) {
                // open air below sea level and above the terrain surface (river channels, lowland
                // basins, the sea itself) is sea water; enclosed caves still go through the aquifer
                this.shouldScheduleFluidUpdate = false;
                cir.setReturnValue(picker.seaWater());
            }
        }
    }
}
