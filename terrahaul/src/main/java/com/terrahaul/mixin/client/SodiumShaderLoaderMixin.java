package com.terrahaul.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.terrahaul.client.sodium.SodiumShaders;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hands each shader source Sodium loads to {@link SodiumShaders}, which adds Terrahaul's fog and
 * vegetation sway to Sodium's terrain shaders. Applied only when Sodium is installed.
 * <p>
 * The source is taken where {@code loadShader} calls {@code getShaderSource}, not inside
 * {@code getShaderSource}: other mods (Shine, for one) edit the source in a cancellable hook at
 * the end of that method, and two such hooks there exclude each other, because the first that
 * sets the source returns at once. Out here Terrahaul sees the source with their edits made and
 * never stops them. {@link SodiumShaderParserMixin} does the same for imported files.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class SodiumShaderLoaderMixin {
    @WrapOperation(method = "loadShader", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderLoader;getShaderSource(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;"))
    private static String terrahaul$patch(ResourceLocation name, Operation<String> original) {
        return SodiumShaders.patch(name, original.call(name));
    }
}
