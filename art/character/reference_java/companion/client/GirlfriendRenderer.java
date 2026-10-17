package com.example.companion.client;

import com.example.companion.entity.GirlfriendEntity;
import com.example.companion.outfit.Outfit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class GirlfriendRenderer extends GeoEntityRenderer<GirlfriendEntity> {
    /** Every body bone any outfit may hide; each frame they're shown or hidden for the outfit she's wearing. */
    private static final Set<String> HIDEABLE = Arrays.stream(Outfit.values())
            .flatMap(o -> o.hiddenBodyBones.stream()).collect(Collectors.toUnmodifiableSet());

    public GirlfriendRenderer(EntityRendererProvider.Context context) {
        super(context, new GirlfriendModel());
        this.shadowRadius = 0.45f;
        withScale(0.9375f); // player scale
        addRenderLayer(new OutfitLayer(this));
    }

    @Override
    public void preRender(PoseStack poseStack, GirlfriendEntity entity, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                          @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        if (isReRender) return;
        // The baked model is shared by every companion, so visibility is set per entity, per frame.
        Set<String> hidden = entity.getOutfit().hiddenBodyBones;
        for (String name : HIDEABLE) model.getBone(name).ifPresent(b -> b.setHidden(hidden.contains(name)));
    }

    @Override
    protected void applyRotations(GirlfriendEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        if (entity.hasPose(Pose.SLEEPING)) {
            // sleep/cuddle/lie_down lay her on her side themselves (head toward model +X),
            // so skip vanilla's lie-on-back tilt and only align her head with the bed's head.
            Direction bed = entity.getBedOrientation();
            float yaw = bed == null ? 180f - rotationYaw : switch (bed) {
                case EAST -> 0f;
                case NORTH -> 90f;
                case WEST -> 180f;
                default -> 270f;
            };
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            return;
        }
        super.applyRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
    }
}
