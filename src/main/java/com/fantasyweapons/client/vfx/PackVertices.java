package com.fantasyweapons.client.vfx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The effects write position, texture and colour only. The game's entity shaders, which they draw with while a shader
 * pack is on, also take an overlay, a light level and a normal: each vertex gets those filled in, fully lit and facing
 * up, so the effects keep their own colours as near as the pack's lighting allows.
 */
final class PackVertices implements VertexConsumer {
    private final VertexConsumer to;

    PackVertices(VertexConsumer to) {
        this.to = to;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        to.addVertex(x, y, z).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        to.setColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        to.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        to.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        to.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        to.setNormal(x, y, z);
        return this;
    }
}
