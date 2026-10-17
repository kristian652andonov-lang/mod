package com.aigirlfriend.client.render;

import com.aigirlfriend.companion.Outfit;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws her current outfit as a second GeckoLib model on the body's skeleton: every outfit bone copies the
 * transform of the same-named body bone each frame, so outfits follow every animation and switching outfits
 * never restarts anything.
 */
public class OutfitLayer extends GeoRenderLayer<GirlfriendEntity> {
    private final Map<BakedGeoModel, Pairs> pairCache = new IdentityHashMap<>();

    public OutfitLayer(GeoRenderer<GirlfriendEntity> renderer) {
        super(renderer);
    }

    @Nullable
    public static BakedGeoModel outfitModel(GirlfriendEntity gf) {
        return GeckoLibCache.getBakedModels().get(gf.getOutfitType().model);
    }

    @Override
    public void render(PoseStack poseStack, GirlfriendEntity gf, BakedGeoModel bodyModel, @Nullable RenderType renderType,
                       MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        Outfit outfit = gf.getOutfitType();
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(outfit.model);
        if (model == null) return;
        syncBones(bodyModel, model);
        RenderType type = RenderType.entityCutoutNoCull(outfit.texture);
        getRenderer().reRender(model, poseStack, bufferSource, gf, type, bufferSource.getBuffer(type), partialTick,
                packedLight, packedOverlay, getRenderer().getRenderColor(gf, partialTick, packedLight).argbInt());
    }

    public void syncBones(BakedGeoModel body, BakedGeoModel outfit) {
        for (GeoBone[] pair : pairs(body, outfit)) copy(pair[0], pair[1]);
    }

    private List<GeoBone[]> pairs(BakedGeoModel body, BakedGeoModel outfit) {
        Pairs cached = pairCache.get(outfit);
        if (cached != null && cached.body == body) return cached.list;
        if (pairCache.size() > 16) pairCache.clear();
        List<GeoBone[]> list = new ArrayList<>();
        collect(outfit.topLevelBones(), body, list);
        pairCache.put(outfit, new Pairs(body, list));
        return list;
    }

    private static void collect(List<GeoBone> bones, BakedGeoModel body, List<GeoBone[]> out) {
        for (GeoBone bone : bones) {
            body.getBone(bone.getName()).ifPresent(src -> out.add(new GeoBone[]{src, bone}));
            collect(bone.getChildBones(), body, out);
        }
    }

    private static void copy(GeoBone from, GeoBone to) {
        to.setRotX(from.getRotX());
        to.setRotY(from.getRotY());
        to.setRotZ(from.getRotZ());
        to.setPosX(from.getPosX());
        to.setPosY(from.getPosY());
        to.setPosZ(from.getPosZ());
        to.setScaleX(from.getScaleX());
        to.setScaleY(from.getScaleY());
        to.setScaleZ(from.getScaleZ());
    }

    private record Pairs(BakedGeoModel body, List<GeoBone[]> list) {}
}
