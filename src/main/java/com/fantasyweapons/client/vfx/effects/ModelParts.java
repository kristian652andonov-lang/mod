package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.client.vfx.VfxContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Low-level drawing of the artist's weapon bones for effects made of many parts (a chain of links, a drake built from
 * blade segments). Callers draw every part with one vertex consumer before switching render type.
 */
public final class ModelParts {
    private ModelParts() {
    }

    @Nullable
    public static BakedGeoModel model(String weaponGeo) {
        return GeckoLibCache.getBakedModels().get(FantasyWeapons.id("geo/item/" + weaponGeo + ".geo.json"));
    }

    public static ResourceLocation texture(String weaponGeo) {
        return FantasyWeapons.id("textures/item/" + weaponGeo + ".png");
    }

    public static List<GeoBone> bones(@Nullable BakedGeoModel model, String... names) {
        List<GeoBone> out = new ArrayList<>();
        if (model != null) for (String n : names) model.getBone(n).ifPresent(out::add);
        return out;
    }

    public static Vec3 center(List<GeoBone> bones) {
        return ModelPartVfx.centerOf(bones);
    }

    /**
     * Draws bones in their rest pose (ignoring whatever animation the held item last applied to them):
     * {@code translate(pos) · rot · scale · translate(-pivot)}.
     */
    public static void draw(VfxContext ctx, VertexConsumer vc, List<GeoBone> bones, Vec3 pos, Quaternionf rot, float scale, Vec3 pivot, int color) {
        PoseStack pose = new PoseStack();
        pose.translate(pos.x - ctx.cam.x, pos.y - ctx.cam.y, pos.z - ctx.cam.z);
        pose.mulPose(rot);
        pose.scale(scale, scale, scale);
        pose.translate(-pivot.x, -pivot.y, -pivot.z);
        for (GeoBone bone : bones) drawRest(pose, bone, vc, ctx.fade(color));
    }

    /** Like {@link #draw} but additionally rotates one bone about its own pivot (e.g. a jaw opening). */
    public static void drawHinged(VfxContext ctx, VertexConsumer vc, GeoBone bone, Vec3 pos, Quaternionf rot, float scale, Vec3 pivot,
                                  Quaternionf hinge, int color) {
        PoseStack pose = new PoseStack();
        pose.translate(pos.x - ctx.cam.x, pos.y - ctx.cam.y, pos.z - ctx.cam.z);
        pose.mulPose(rot);
        pose.scale(scale, scale, scale);
        pose.translate(-pivot.x, -pivot.y, -pivot.z);
        RenderUtil.translateToPivotPoint(pose, bone);
        pose.mulPose(hinge);
        RenderUtil.translateAwayFromPivotPoint(pose, bone);
        drawRest(pose, bone, vc, ctx.fade(color));
    }

    private static void drawRest(PoseStack pose, GeoBone bone, VertexConsumer vc, int color) {
        for (GeoCube cube : bone.getCubes()) {
            pose.pushPose();
            RenderUtil.translateToPivotPoint(pose, cube);
            RenderUtil.rotateMatrixAroundCube(pose, cube);
            RenderUtil.translateAwayFromPivotPoint(pose, cube);
            Matrix4f m = pose.last().pose();
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) continue;
                for (GeoVertex v : quad.vertices()) {
                    Vector4f w = m.transform(new Vector4f(v.position().x(), v.position().y(), v.position().z(), 1f));
                    vc.addVertex(w.x(), w.y(), w.z()).setUv(v.texU(), v.texV()).setColor(color);
                }
            }
            pose.popPose();
        }
    }
}
