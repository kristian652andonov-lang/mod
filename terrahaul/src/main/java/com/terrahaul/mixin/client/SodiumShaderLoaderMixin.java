package com.terrahaul.mixin.client;

import com.terrahaul.client.sodium.SodiumShaders;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hands every shader source Sodium loads to {@link SodiumShaders}, which adds Terrahaul's fog and
 * vegetation sway to Sodium's terrain shaders. Applied only when Sodium is installed; if a Sodium
 * version renames the method nothing is changed.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class SodiumShaderLoaderMixin {
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, require = 0)
    private static void terrahaul$patch(ResourceLocation name, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(SodiumShaders.patch(name, cir.getReturnValue()));
    }
}
