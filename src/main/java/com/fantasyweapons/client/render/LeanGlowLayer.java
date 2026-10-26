package com.fantasyweapons.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** GeckoLib's glow layer, telling a {@link LeanGeoRenderer} it is the glow being drawn (so it draws only what glows). */
public class LeanGlowLayer<T extends GeoAnimatable> extends AutoGlowingGeoLayer<T> {
    public LeanGlowLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType, MultiBufferSource bufferSource,
                       @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        ModelCulling.glowBegin();
        try {
            super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
        } finally {
            ModelCulling.glowEnd();
        }
    }
}
