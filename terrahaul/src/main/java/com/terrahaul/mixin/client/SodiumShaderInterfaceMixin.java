package com.terrahaul.mixin.client;

import com.terrahaul.client.sodium.SodiumShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Uploads the vegetation sway uniforms whenever Sodium sets up its terrain program (the program is
 * bound at that point). Applied only when Sodium is installed.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.shader.DefaultShaderInterface", remap = false)
public abstract class SodiumShaderInterfaceMixin {
    @Inject(method = "setupState", at = @At("RETURN"), require = 0)
    private void terrahaul$uploadSway(CallbackInfo ci) {
        SodiumShaders.uploadUniforms();
    }
}
