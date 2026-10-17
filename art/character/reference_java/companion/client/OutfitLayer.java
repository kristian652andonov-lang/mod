package com.example.companion.client;

import com.example.companion.entity.GirlfriendEntity;
import com.example.companion.outfit.Outfit;
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
 * Draws the current outfit as a second GeckoLib model on the body's skeleton. Every outfit bone copies
 * the transform of the same-named body bone each frame, so outfits follow every animation without
 * owning one, and swapping outfits never restarts anything.
 */
public class OutfitLayer extends GeoRenderLayer<GirlfriendEntity> {
    private final Map<BakedGeoModel, Pairs> pairCache = new IdentityHashMap<>();

    public OutfitLayer(GeoRenderer<GirlfriendEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, GirlfriendEntity entity, BakedGeoModel bodyModel, @Nullable RenderType renderType,
                       MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        Outfit outfit = entity.getOutfit();
        BakedGeoModel outfitModel = GeckoLibCache.getBakedModels().get(outfit.model); // read the cache directly: no processor side effects
        if (outfitModel == null) return;

        for (GeoBone[] pair : pairs(bodyModel, outfitModel)) copy(pair[0], pair[1]);

        RenderType type = RenderType.entityCutoutNoCull(outfit.texture);
        getRenderer().reRender(outfitModel, poseStack, bufferSource, entity, type, bufferSource.getBuffer(type), partialTick,
                packedLight, packedOverlay, getRenderer().getRenderColor(entity, partialTick, packedLight).argbInt());
    }

    private List<GeoBone[]> pairs(BakedGeoModel body, BakedGeoModel outfit) {
        Pairs cached = pairCache.get(outfit);
        if (cached != null && cached.body == body) return cached.list;
        if (pairCache.size() > 16) pairCache.clear(); // resource reloads create new baked models
        List<GeoBone[]> list = new ArrayList<>();
        collect(outfit.topLevelBones(), body, list);
        pairCache.put(outfit, new Pairs(body, list));
        return list;
    }

    private static void collect(List<GeoBone> bones, BakedGeoModel body, List<GeoBone[]> out) {
        for (GeoBone bone : bones) {
            body.getBone(bone.getName()).ifPresent(src -> out.add(new GeoBone[] {src, bone}));
            collect(bone.getChildBones(), body, out);
        }
    }

    private static void copy(GeoBone from, GeoBone to) {
        to.setRotX(from.getRotX()); to.setRotY(from.getRotY()); to.setRotZ(from.getRotZ());
        to.setPosX(from.getPosX()); to.setPosY(from.getPosY()); to.setPosZ(from.getPosZ());
        to.setScaleX(from.getScaleX()); to.setScaleY(from.getScaleY()); to.setScaleZ(from.getScaleZ());
    }

    private record Pairs(BakedGeoModel body, List<GeoBone[]> list) {}
}
