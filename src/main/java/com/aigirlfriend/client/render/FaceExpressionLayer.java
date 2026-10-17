package com.aigirlfriend.client.render;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.companion.Expression;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Texture-based facial expressions: overlays textures/entity/girlfriend/face/&lt;expression&gt;.png (same UV layout as
 * the skin, transparent except the face) on the body and on the outfit (the hood covers the body's head), and blinks
 * with face/blink.png.
 */
public class FaceExpressionLayer extends GeoRenderLayer<GirlfriendEntity> {
    private final OutfitLayer outfitLayer;

    public FaceExpressionLayer(GeoRenderer<GirlfriendEntity> renderer, OutfitLayer outfitLayer) {
        super(renderer);
        this.outfitLayer = outfitLayer;
    }

    private static ResourceLocation face(String id) {
        return ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "textures/entity/girlfriend/face/" + id + ".png");
    }

    @Override
    public void render(PoseStack poseStack, GirlfriendEntity gf, BakedGeoModel bakedModel, @Nullable RenderType renderType,
                       MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        Expression e = gf.getExpression();
        boolean blinking = e != Expression.SLEEPY && !gf.isSleeping() && !gf.isFainted() && (gf.tickCount + gf.getId() * 13) % 90 < 3;
        ResourceLocation tex = blinking ? face("blink") : face(e.id());
        if (!ResourceCheck.exists(tex)) return;
        RenderType type = RenderType.entityCutoutNoCull(tex);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, gf, type, bufferSource.getBuffer(type), partialTick, packedLight, OverlayTexture.NO_OVERLAY, -1);
        BakedGeoModel outfit = OutfitLayer.outfitModel(gf);
        if (outfit != null) {
            outfitLayer.syncBones(bakedModel, outfit);
            getRenderer().reRender(outfit, poseStack, bufferSource, gf, type, bufferSource.getBuffer(type), partialTick, packedLight, OverlayTexture.NO_OVERLAY, -1);
        }
    }
}
