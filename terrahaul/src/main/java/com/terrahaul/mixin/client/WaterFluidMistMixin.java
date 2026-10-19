package com.terrahaul.mixin.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spray and mist where a waterfall meets its pool: when the client animates the lowest block of
 * a column of falling water at least three blocks tall standing on still water, it throws up a
 * few splash droplets and now and then a drifting puff of mist. Purely visual and cheap: the
 * client only animates a few hundred random blocks around the player per tick.
 */
@Mixin(WaterFluid.class)
public abstract class WaterFluidMistMixin {
    @Inject(method = "animateTick", at = @At("TAIL"))
    private void terrahaul$waterfallMist(Level level, BlockPos pos, FluidState state, RandomSource random, CallbackInfo ci) {
        if (state.isSource() || !state.getValue(FlowingFluid.FALLING)) {
            return;
        }
        FluidState below = level.getFluidState(pos.below());
        if (!below.is(FluidTags.WATER) || !below.isSource() || !level.getFluidState(pos.above(2)).is(FluidTags.WATER)) {
            return;
        }
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.SPLASH, x + random.nextDouble(), y + 0.1, z + random.nextDouble(),
                (random.nextDouble() - 0.5) * 0.4, 0.3, (random.nextDouble() - 0.5) * 0.4);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.CLOUD, x - 0.5 + 2.0 * random.nextDouble(), y + 0.4 + random.nextDouble(), z - 0.5 + 2.0 * random.nextDouble(),
                (random.nextDouble() - 0.5) * 0.02, 0.025, (random.nextDouble() - 0.5) * 0.02);
        }
    }
}
