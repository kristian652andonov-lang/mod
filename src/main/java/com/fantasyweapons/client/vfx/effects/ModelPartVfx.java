package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
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
import java.util.function.Function;

/**
 * Renders selected bones of an artist-made GeckoLib weapon model as a free-floating world effect, e.g. Voidfang's
 * {@code dimension_rift}, Aetherlance's {@code projectile}, Starforge's {@code meteor_projectile}. The geometry is the
 * artist's; it is drawn with the weapon's own texture through the custom translucent/additive render types, so it
 * appears exactly as designed but full-bright and fade-able.
 */
public class ModelPartVfx extends Vfx {
    private final ResourceLocation geo;
    private final ResourceLocation texture;
    private final String[] bones;
    private Function<Float, Vec3> position;
    private Function<Float, Quaternionf> rotation = t -> new Quaternionf();
    private Function<Float, Float> scale = t -> 1f;
    private Function<Float, Float> alpha = t -> 1f;
    private boolean glow = true;
    private int tint = 0xFFFFFF;
    private float bodyAlpha = 1f;
    private float glowAlpha = 0.55f;
    @Nullable
    private Vec3 pivotCache;

    public ModelPartVfx(String weaponGeo, Vec3 pos, int lifetime, String... bones) {
        super(lifetime);
        this.geo = FantasyWeapons.id("geo/item/" + weaponGeo + ".geo.json");
        this.texture = FantasyWeapons.id("textures/item/" + weaponGeo + ".png");
        this.bones = bones;
        this.position = t -> pos;
    }

    public ModelPartVfx position(Function<Float, Vec3> fn) {
        this.position = fn;
        return this;
    }

    public ModelPartVfx rotation(Function<Float, Quaternionf> fn) {
        this.rotation = fn;
        return this;
    }

    public ModelPartVfx scale(Function<Float, Float> fn) {
        this.scale = fn;
        return this;
    }

    public ModelPartVfx alpha(Function<Float, Float> fn) {
        this.alpha = fn;
        return this;
    }

    /** Explicit pivot in model space (blocks), e.g. a hinge, instead of the bones' geometric centre. */
    public ModelPartVfx pivot(Vec3 modelSpace) {
        this.pivotCache = modelSpace;
        return this;
    }

    /** Spectral look: tints the model and makes it see-through (body alpha / glow alpha multipliers). */
    public ModelPartVfx spectral(int rgb, float bodyAlpha, float glowAlpha) {
        this.tint = rgb;
        this.bodyAlpha = bodyAlpha;
        this.glowAlpha = glowAlpha;
        return this;
    }

    public ModelPartVfx noGlow() {
        this.glow = false;
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(geo);
        if (model == null) return;
        List<GeoBone> found = new ArrayList<>();
        for (String name : bones) model.getBone(name).ifPresent(found::add);
        if (found.isEmpty()) return;
        if (pivotCache == null) pivotCache = centerOf(found);

        float t = progress(ctx.partial);
        float a = clamp01(alpha.apply(t));
        if (a <= 0.01f) return;
        Vec3 p = position.apply(t);
        PoseStack pose = new PoseStack();
        pose.translate(p.x - ctx.cam.x, p.y - ctx.cam.y, p.z - ctx.cam.z);
        pose.mulPose(rotation.apply(t));
        float s = scale.apply(t);
        pose.scale(s, s, s);
        pose.translate(-pivotCache.x, -pivotCache.y, -pivotCache.z);

        int color = Colors.alpha(a * bodyAlpha, tint);
        VertexConsumer body = ctx.translucent(texture);
        for (GeoBone bone : found) renderBone(pose, bone, body, color);
        if (glow) {
            VertexConsumer add = ctx.additive(texture);
            int gc = Colors.alpha(a * glowAlpha, tint);
            for (GeoBone bone : found) renderBone(pose, bone, add, gc);
        }
    }

    private static void renderBone(PoseStack pose, GeoBone bone, VertexConsumer vc, int color) {
        pose.pushPose();
        RenderUtil.translateToPivotPoint(pose, bone);
        RenderUtil.rotateMatrixAroundBone(pose, bone);
        RenderUtil.translateAwayFromPivotPoint(pose, bone);
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
        for (GeoBone child : bone.getChildBones()) renderBone(pose, child, vc, color);
        pose.popPose();
    }

    /** Geometric centre of the bones' cubes (model space, blocks) so the effect is placed around its middle. */
    private static Vec3 centerOf(List<GeoBone> bones) {
        AABB box = null;
        for (GeoBone b : bones) {
            for (GeoCube c : b.getCubes()) {
                for (GeoQuad q : c.quads()) {
                    if (q == null) continue;
                    for (GeoVertex v : q.vertices()) {
                        Vec3 p = new Vec3(v.position().x(), v.position().y(), v.position().z());
                        box = box == null ? new AABB(p, p) : box.minmax(new AABB(p, p));
                    }
                }
            }
        }
        return box == null ? Vec3.ZERO : box.getCenter();
    }
}
