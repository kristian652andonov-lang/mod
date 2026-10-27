package com.fantasyweapons.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * A GeckoLib renderer of the mod's that leaves out of each pass what would not show there (see {@link ModelCulling}).
 * Its glow layer is a {@link LeanGlowLayer}.
 */
public interface LeanGeoRenderer<T extends GeoAnimatable> extends GeoRenderer<T> {
    /** The texture the model is being drawn with (its glowmask is the glow layer's), or null to draw it all. */
    @Nullable
    default ResourceLocation drawnTexture() {
        T animatable = getAnimatable();
        return animatable == null ? null : getTextureLocation(animatable);
    }

    /** Whether the glow layer is being drawn. */
    default boolean drawingGlow() {
        return ModelCulling.inGlow();
    }

    /** GeckoLib's own drawing of a cube, but only of the faces {@link ModelCulling#faces} leaves in. */
    @Override
    default void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        int faces = ModelCulling.faces(drawnTexture(), drawingGlow(), poseStack.last().pose(), cube);
        if (faces == 0) return;
        RenderUtil.translateToPivotPoint(poseStack, cube);
        RenderUtil.rotateMatrixAroundCube(poseStack, cube);
        RenderUtil.translateAwayFromPivotPoint(poseStack, cube);
        Matrix3f normalisedPoseState = poseStack.last().normal();
        Matrix4f poseState = new Matrix4f(poseStack.last().pose());
        GeoQuad[] quads = cube.quads();
        for (int i = 0; i < quads.length; i++) {
            GeoQuad quad = quads[i];
            if (quad == null || (faces & (1 << Math.min(i, 30))) == 0) continue;
            Vector3f normal = normalisedPoseState.transform(new Vector3f(quad.normal()));
            RenderUtil.fixInvertedFlatCube(cube, normal);
            createVerticesOfQuad(quad, poseState, normal, buffer, packedLight, packedOverlay, colour);
        }
    }
}
