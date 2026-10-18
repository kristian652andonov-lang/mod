package com.terrahaul.mixin.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.terrahaul.client.render.TerrahaulSkyEffects;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In Terrahaul worlds the cylindrical terrain fog is reported to shaders as shape 2, which
 * Terrahaul's {@code fog.glsl} treats as a cylinder that weighs vertical distance less. Vanilla's
 * cylinder fog counts vertical distance fully, which hides everything more than one render
 * distance below the camera: from a 1000-block peak the valleys would vanish. The index is only
 * used for the shader uniform; other worlds keep 0 and 1.
 */
@Mixin(FogShape.class)
public abstract class FogShapeMixin {
    /** Development switch to compare against vanilla fog ({@code -Dterrahaul.debug.vanillaFog=true}). */
    private static final boolean VANILLA_FOG = Boolean.getBoolean("terrahaul.debug.vanillaFog");

    @Inject(method = "getIndex", at = @At("HEAD"), cancellable = true)
    private void terrahaul$tallWorldFog(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this == FogShape.CYLINDER && !VANILLA_FOG) {
            var level = Minecraft.getInstance().level;
            if (level != null && level.effects() instanceof TerrahaulSkyEffects) {
                cir.setReturnValue(2);
            }
        }
    }
}
