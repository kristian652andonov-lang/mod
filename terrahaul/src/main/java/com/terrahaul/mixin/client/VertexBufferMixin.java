package com.terrahaul.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.terrahaul.client.render.RenderMemory;
import javax.annotation.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creates a vertex buffer's OpenGL objects when it is first bound instead of in the constructor.
 *
 * <p>Minecraft builds a render section for every 16-block section within render distance, and
 * each section eagerly creates five vertex buffers with three OpenGL objects each, whether or not
 * the section ever holds a block. A Terrahaul world is 159 sections tall instead of 24, so at
 * render distance 16 that is 2.6 million OpenGL objects (10 million at 32), almost all for empty
 * air above the terrain. That exhausts the graphics driver's memory and crashes the game without
 * a Java error. With lazy creation only buffers that receive geometry exist.
 *
 * <p>Every vanilla user binds a buffer on the render thread before it uploads to or draws from
 * it: section geometry is uploaded by the render thread's upload queue right after {@code bind()},
 * and the sky, star, cloud and immediate-mode buffers are bound right after they are created. So
 * the objects are still created on the render thread. {@code upload} and
 * {@code uploadIndexBuffer} also create them for callers that upload without binding first; off the
 * render thread they leave it to vanilla's own assertion to reject the call and free the data.
 * Because the constructor no longer touches OpenGL it may run on any thread, which
 * {@link com.terrahaul.client.render.SectionBufferMap} relies on to create a section's buffers on
 * the worker that compiled it. A buffer closed before it was ever bound has nothing to delete and
 * simply becomes invalid, like any closed buffer.
 *
 * <p>Compatibility: ImmediatelyFast only replaces the {@code glBufferData} calls inside the upload
 * methods, which run after the objects exist; Sodium and Embeddium do not draw terrain with these
 * buffers. A mod that read the private ids directly before the first bind would see
 * {@link #TERRAHAUL_LAZY}; neither vanilla nor NeoForge does.
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
    @Shadow
    @Nullable
    private VertexFormat.Mode mode;

    /** The constructor only stores the usage now, so it no longer needs the render thread. */
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V"))
    private void terrahaul$anyThread(Operation<Void> original) {
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenBuffers()I"))
    private int terrahaul$deferBuffer(Operation<Integer> original) {
        return TERRAHAUL_LAZY;
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenVertexArrays()I"))
    private int terrahaul$deferVertexArray(Operation<Integer> original) {
        return TERRAHAUL_LAZY;
    }

    @Inject(method = "bind()V", at = @At("HEAD"))
    private void terrahaul$createOnBind(CallbackInfo ci) {
        this.terrahaul$create();
    }

    @Inject(method = {"upload(Lcom/mojang/blaze3d/vertex/MeshData;)V",
        "uploadIndexBuffer(Lcom/mojang/blaze3d/vertex/ByteBufferBuilder$Result;)V"}, at = @At("HEAD"))
    private void terrahaul$createOnUpload(CallbackInfo ci) {
        if (RenderSystem.isOnRenderThreadOrInit()) {
            this.terrahaul$create();
        }
    }

    @Unique
    private void terrahaul$create() {
        if (this.arrayObjectId == TERRAHAUL_LAZY) {
            this.vertexBufferId = GlStateManager._glGenBuffers();
            this.indexBufferId = GlStateManager._glGenBuffers();
            this.arrayObjectId = GlStateManager._glGenVertexArrays();
            RenderMemory.glBufferCreated();
        }
    }

    /**
     * A buffer that never received data draws nothing instead of failing on its missing draw mode.
     * Vanilla never draws such a buffer, but a section compile that completes on a worker just as
     * the render thread moves the section away and frees its buffers can mark it compiled again
     * (vanilla would draw stale geometry for a frame); the section is recompiled anyway.
     */
    @Inject(method = "draw()V", at = @At("HEAD"), cancellable = true)
    private void terrahaul$skipEmpty(CallbackInfo ci) {
        if (this.mode == null) {
            ci.cancel();
        }
    }

    /** A buffer closed before it was used has nothing to delete; it still becomes invalid. */
    @Inject(method = "close()V", at = @At("HEAD"))
    private void terrahaul$close(CallbackInfo ci) {
        if (this.arrayObjectId == TERRAHAUL_LAZY) {
            this.vertexBufferId = -1;
            this.indexBufferId = -1;
            this.arrayObjectId = -1;
        } else if (this.arrayObjectId >= 0) {
            RenderMemory.glBufferDeleted();
        }
    }
}
