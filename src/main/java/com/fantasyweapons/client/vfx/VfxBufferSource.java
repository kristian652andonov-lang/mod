package com.fantasyweapons.client.vfx;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.LinkedHashMap;

/**
 * The effects' buffer source: every render type gets its own buffer, so an effect can hold several consumers at once
 * (glow, flame, mist...) and interleave them freely; everything is drawn together, in first-use order, at the end of
 * the pass. A vanilla immediate source shares one buffer and flushes it whenever the type changes, which leaves the
 * other consumers dead ("Not building!").
 */
final class VfxBufferSource extends MultiBufferSource.BufferSource {
    /** Render types beyond this many fall back to the shared, flush-on-switch buffer. */
    private static final int MAX_POOLED = 192;

    VfxBufferSource() {
        super(new ByteBufferBuilder(1 << 20), new LinkedHashMap<>());
    }

    /** Draws the opaque, depth-writing geometry first, so the glows and see-through layers drawn after it are hidden where it covers them. */
    @Override
    public void endBatch() {
        endLastBatch();
        for (RenderType type : fixedBuffers.keySet()) if (com.fantasyweapons.client.render.FWRenderTypes.opaque(type)) endBatch(type);
        super.endBatch();
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (!fixedBuffers.containsKey(type) && fixedBuffers.size() < MAX_POOLED) fixedBuffers.put(type, new ByteBufferBuilder(1 << 14));
        return super.getBuffer(type);
    }
}
