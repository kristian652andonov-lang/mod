package com.terrahaul.mixin.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creates a vertex buffer's OpenGL objects when it is first bound instead of in the constructor.
 *
 * <p>Minecraft builds a render section for every 16-block section within render distance, and
 * each section eagerly creates five vertex buffers with three OpenGL objects each, whether or not
 * the section ever holds a block. A Terrahaul world is 159 sections tall instead of 24, so at
 * render distance 16 that is 2.6 million OpenGL objects (10 million at 32), almost all for empty
 * air above the terrain. That exhausts the graphics driver's memory and crashes the game without
 * a Java error. With lazy creation only buffers that receive geometry exist. Buffers are always
 * bound on the render thread before they are uploaded to or drawn, so the objects are still
 * created there.
 */
@Mixin(VertexBuffer.class)
public abstract class VertexBufferMixin {
    /** Marks an id that has not been created yet; vanilla uses -1 for "deleted". */
    @Unique
    private static final int TERRAHAUL_LAZY = -2;

    @Shadow
    private int vertexBufferId;
    @Shadow
    private int indexBufferId;
    @Shadow
    private int arrayObjectId;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenBuffers()I"))
    private int terrahaul$deferBuffer() {
        return TERRAHAUL_LAZY;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenVertexArrays()I"))
    private int terrahaul$deferVertexArray() {
        return TERRAHAUL_LAZY;
    }

    @Inject(method = {"bind", "upload", "uploadIndexBuffer"}, at = @At("HEAD"))
    private void terrahaul$create(CallbackInfo ci) {
        if (this.arrayObjectId == TERRAHAUL_LAZY) {
            this.vertexBufferId = GlStateManager._glGenBuffers();
            this.indexBufferId = GlStateManager._glGenBuffers();
            this.arrayObjectId = GlStateManager._glGenVertexArrays();
        }
    }

    /** A buffer closed before it was used has nothing to delete; it still becomes invalid. */
    @Inject(method = "close", at = @At("HEAD"))
    private void terrahaul$closeUnused(CallbackInfo ci) {
        if (this.arrayObjectId == TERRAHAUL_LAZY) {
            this.vertexBufferId = -1;
            this.indexBufferId = -1;
            this.arrayObjectId = -1;
        }
    }
}
