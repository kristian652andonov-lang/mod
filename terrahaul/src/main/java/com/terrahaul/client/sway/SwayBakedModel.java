package com.terrahaul.client.sway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Wraps a plant model and stores each vertex's sway weight in the alpha byte of its vertex colour.
 * <p>
 * The weight is the vertex height above the plant's base (0 at the ground, 1 at the top of a
 * one-block plant, 2 at the top of a two-block plant) times the plant's flexibility. Because both
 * halves of a double plant agree on the weight where they meet, tall grass bends as one piece.
 * The cutout core shader decodes the weight, displaces the vertex and restores an opaque alpha;
 * every other block keeps alpha 255 and is left untouched.
 */
final class SwayBakedModel extends BakedModelWrapper<BakedModel> {
    /** Alpha steps per block of sway height; the alpha never drops below 255 - 2 * 48 = 159. */
    static final float STEPS_PER_BLOCK = 48.0F;
    private static final int STRIDE = 8;
    private static final int POSITION_Y = 1;
    private static final int COLOUR = 3;

    private final float base;
    private final float flexibility;
    private final Map<BakedQuad, BakedQuad> marked = new ConcurrentHashMap<>();

    SwayBakedModel(BakedModel original, float base, float flexibility) {
        super(original);
        this.base = base;
        this.flexibility = flexibility;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return mark(super.getQuads(state, side, rand));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        return mark(super.getQuads(state, side, rand, data, renderType));
    }

    private List<BakedQuad> mark(List<BakedQuad> quads) {
        if (quads.isEmpty()) {
            return quads;
        }
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            // BakedQuad uses identity equality, so this caches per quad instance
            out.add(marked.computeIfAbsent(quad, this::mark));
        }
        return out;
    }

    private BakedQuad mark(BakedQuad quad) {
        int[] vertices = quad.getVertices().clone();
        int count = vertices.length / STRIDE;
        for (int i = 0; i < count; i++) {
            float y = Float.intBitsToFloat(vertices[i * STRIDE + POSITION_Y]);
            float height = Mth.clamp((base + Mth.clamp(y, 0.0F, 1.0F)) * flexibility, 0.0F, 2.0F);
            int alpha = 255 - Math.round(height * STEPS_PER_BLOCK);
            int colour = vertices[i * STRIDE + COLOUR];
            // vertex colours are stored as little-endian RGBA bytes: alpha is the high byte
            vertices[i * STRIDE + COLOUR] = (colour & 0x00FFFFFF) | (alpha << 24);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }
}
