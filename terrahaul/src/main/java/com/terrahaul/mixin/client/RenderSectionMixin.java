package com.terrahaul.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.terrahaul.client.render.SectionBufferMap;
import com.terrahaul.client.render.TerrahaulSkyEffects;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every render section a {@link SectionBufferMap} instead of a hash map of five vertex
 * buffers created up front, and frees a section's buffers when it is moved out of drawing range.
 * <p>
 * The view area keeps render sections over the full world height, but the section graph only
 * draws sections within one render distance above and below the camera. When the camera crosses
 * a chunk border, vanilla moves a whole row of sections to new positions; they must be compiled
 * again, but vanilla keeps their old geometry on the GPU until then, and a section moved out of
 * drawing range is not compiled again until the camera comes near its height. In a 159-section
 * Terrahaul world, travelling at a different height than earlier would therefore leave stale
 * meshes from every height visited in video memory. In Terrahaul worlds the buffers of a section
 * moved further than render distance + 2 sections from the camera height are closed instead.
 * This is safe at any time: a moved section is reset to "not compiled" right after, so nothing
 * draws from it, and uploads still queued for the closed buffers are dropped.
 */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class RenderSectionMixin {
    @Shadow
    @Final
    private Map<RenderType, VertexBuffer> buffers;

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Object terrahaul$lazyBuffers(Stream<?> layers, Collector<?, ?, ?> collector, Operation<Object> original) {
        return new SectionBufferMap();
    }

    @Inject(method = "releaseBuffers()V", at = @At("HEAD"))
    private void terrahaul$release(CallbackInfo ci) {
        if (this.buffers instanceof SectionBufferMap map) {
            map.release();
        }
    }

    @Inject(method = "setOrigin(III)V", at = @At("HEAD"))
    private void terrahaul$discardOutOfRange(int x, int y, int z, CallbackInfo ci) {
        if (this.buffers instanceof SectionBufferMap map && map.hasBuffers() && terrahaul$beyondDrawRange(y)) {
            map.discard();
        }
    }

    /** True in a Terrahaul world when a section at {@code originY} is too far above or below the camera to be drawn. */
    @Unique
    private static boolean terrahaul$beyondDrawRange(int originY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.effects() instanceof TerrahaulSkyEffects)) {
            return false;
        }
        double reach = (mc.options.getEffectiveRenderDistance() + 2) * 16.0;
        return Math.abs(originY + 8.0 - mc.gameRenderer.getMainCamera().getPosition().y) > reach;
    }
}
