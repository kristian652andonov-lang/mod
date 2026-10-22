package com.terrahaul.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.terrahaul.client.sodium.SodiumShaders;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The files Sodium's shaders import ({@code #import <sodium:include/fog.glsl>}) are read by its
 * shader parser; like {@link SodiumShaderLoaderMixin}, this hands them to {@link SodiumShaders}
 * where the parser reads them. Sodium 0.8 reads them in {@code processImport}, Sodium 0.6 in the
 * static {@code resolveImport}; each hook applies to whichever exists. Applied only when Sodium is
 * installed.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderParser", remap = false)
public abstract class SodiumShaderParserMixin {
    private static final String GET_SOURCE =
        "Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderLoader;getShaderSource(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;";

    @WrapOperation(method = "processImport", require = 0, at = @At(value = "INVOKE", target = GET_SOURCE))
    private String terrahaul$patchImport(ResourceLocation name, Operation<String> original) {
        return SodiumShaders.patch(name, original.call(name));
    }

    @WrapOperation(method = "resolveImport", require = 0, at = @At(value = "INVOKE", target = GET_SOURCE))
    private static String terrahaul$patchImportStatic(ResourceLocation name, Operation<String> original) {
        return SodiumShaders.patch(name, original.call(name));
    }
}
