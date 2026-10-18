package com.terrahaul.mixin;

import com.terrahaul.worldgen.TerrahaulFluidPicker;
import com.terrahaul.worldgen.density.TerrainDensityFunction;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Terrahaul-only noise chunk changes (active when the chunk uses Terrahaul's fluid picker or
 * terrain function):
 * <ul>
 *   <li>The preliminary surface level is read from the terrain model in closed form instead of
 *       scanning the initial density down from the top of a 2,544-block world.</li>
 *   <li>Deepslate (Y&lt;8, same vertical gradient and random as the vanilla surface rule) and the
 *       bedrock floor are produced while filling noise. Vanilla converts stone to deepslate in the
 *       surface pass, which in a world reaching Y=-512 meant revisiting ~520 extra blocks of every
 *       column; with the materials already in place the surface pass can stop near the surface
 *       (see SurfaceSystemMixin). The result is block-for-block what the vanilla rules produce.</li>
 * </ul>
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMixin {
    @Unique
    private static final BlockState TERRAHAUL_DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    @Unique
    private static final BlockState TERRAHAUL_BEDROCK = Blocks.BEDROCK.defaultBlockState();

    @Shadow
    @Final
    private DensityFunction initialDensityNoJaggedness;

    @Shadow
    public abstract int blockX();

    @Shadow
    public abstract int blockY();

    @Shadow
    public abstract int blockZ();

    @Unique
    private boolean terrahaul$deepMaterials;
    @Unique
    private int terrahaul$minY;
    @Unique
    private PositionalRandomFactory terrahaul$deepslateRandom;
    @Unique
    private PositionalRandomFactory terrahaul$bedrockRandom;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void terrahaul$init(int cellCountXZ, RandomState random, int firstNoiseX, int firstNoiseZ, NoiseSettings noiseSettings,
                                DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings settings, Aquifer.FluidPicker fluidPicker,
                                Blender blender, CallbackInfo ci) {
        if (fluidPicker instanceof TerrahaulFluidPicker) {
            this.terrahaul$deepMaterials = true;
            this.terrahaul$minY = settings.noiseSettings().minY();
            this.terrahaul$deepslateRandom = random.getOrCreateRandomFactory(ResourceLocation.withDefaultNamespace("deepslate"));
            this.terrahaul$bedrockRandom = random.getOrCreateRandomFactory(ResourceLocation.withDefaultNamespace("bedrock_floor"));
        }
    }

    @Inject(method = "computePreliminarySurfaceLevel", at = @At("HEAD"), cancellable = true)
    private void terrahaul$closedFormSurface(long packedPos, CallbackInfoReturnable<Integer> cir) {
        if (this.initialDensityNoJaggedness instanceof TerrainDensityFunction terrain) {
            TerrainModel model = terrain.model();
            if (model != null) {
                cir.setReturnValue(model.preliminarySurface(ColumnPos.getX(packedPos), ColumnPos.getZ(packedPos)));
            }
        }
    }

    @Inject(method = "getInterpolatedState", at = @At("RETURN"), cancellable = true)
    private void terrahaul$deepMaterials(CallbackInfoReturnable<BlockState> cir) {
        if (!this.terrahaul$deepMaterials || cir.getReturnValue() != null) {
            return;
        }
        int y = this.blockY();
        if (y >= 8) {
            return;
        }
        int floor = this.terrahaul$minY;
        if (y < floor + 5 && (y <= floor || this.terrahaul$bedrockRandom.at(this.blockX(), y, this.blockZ()).nextFloat() < (floor + 5 - y) / 5.0)) {
            cir.setReturnValue(TERRAHAUL_BEDROCK);
            return;
        }
        if (y <= 0 || this.terrahaul$deepslateRandom.at(this.blockX(), y, this.blockZ()).nextFloat() < (8 - y) / 8.0) {
            cir.setReturnValue(TERRAHAUL_DEEPSLATE);
        }
    }
}
