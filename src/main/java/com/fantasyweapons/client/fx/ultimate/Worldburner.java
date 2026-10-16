package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.client.vfx.VfxContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Worldburner, the Arch Dragon of Hell: the artist's model, 256 blocks from snout to tail, flown by the Arch Dragon's
 * effect along its path. It is not an entity - the effect places it each frame (its mouth on the path's head, its body
 * along the path behind) and chooses its animations: flapping as it climbs, gliding high and in the dive, roaring as it
 * breaks out of the ground and breathing fire as it falls on its prey.
 */
public final class Worldburner implements GeoAnimatable {
    private static final ResourceLocation MODEL = FantasyWeapons.id("geo/entity/worldburner.geo.json");
    private static final ResourceLocation TEXTURE = FantasyWeapons.id("textures/entity/worldburner.png");
    private static final ResourceLocation ANIMATIONS = FantasyWeapons.id("animations/entity/worldburner.animation.json");
    /** Its mouth and the tip of its tail, in blocks from the model's origin (it faces north, -z, at rest). */
    public static final Vec3 MOUTH = new Vec3(0, 27.5, -100.5), TAIL = new Vec3(0, 20, 156);
    /** Snout to tail tip, in blocks. */
    public static final double LENGTH = 256;

    public static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.worldburner.fly");
    public static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.worldburner.glide");
    public static final RawAnimation HOVER = RawAnimation.begin().thenLoop("animation.worldburner.idle");
    public static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.worldburner.roar");
    public static final RawAnimation FIRE_BREATH = RawAnimation.begin().thenPlay("animation.worldburner.fire_breath");

    private static Renderer renderer;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private double tick;
    private RawAnimation movement = FLY;
    @Nullable
    private RawAnimation action;

    /** What its body and wings do. */
    public void move(RawAnimation movement) {
        this.movement = movement;
    }

    /** What its head and jaws do (each played once), or null for nothing. */
    public void act(@Nullable RawAnimation action) {
        this.action = action;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 12, state -> state.setAndContinue(movement)));
        // registered second, so it wins on the neck and head
        controllers.add(new AnimationController<>(this, "action", 6, state -> action == null ? PlayState.STOP : state.setAndContinue(action)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return tick;
    }

    /**
     * Draws it with its mouth at {@code mouth}, heading along {@code forward} with its back towards {@code up}, at
     * {@code scale} times its size; {@code ticks} drive its animations. Far off, it is drawn nearer and smaller in
     * proportion (the same picture), so it shows past the fog.
     */
    public void render(VfxContext ctx, Vec3 mouth, Vec3 forward, Vec3 up, double scale, int light, float ticks) {
        tick = ticks;
        Vec3 back = forward.normalize().scale(-1);
        Vec3 u = up.subtract(back.scale(up.dot(back)));
        u = u.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : u.normalize();
        Vec3 right = u.cross(back).normalize();
        double squash = ctx.squashFor(mouth, VfxContext.viewReach() * 0.85);
        PoseStack pose = new PoseStack();
        pose.translate((mouth.x - ctx.cam.x) * squash, (mouth.y - ctx.cam.y) * squash, (mouth.z - ctx.cam.z) * squash);
        Matrix3f turn = new Matrix3f((float) right.x, (float) right.y, (float) right.z, (float) u.x, (float) u.y, (float) u.z, (float) back.x,
                (float) back.y, (float) back.z);
        pose.last().pose().mul(new Matrix4f(turn));
        pose.last().normal().mul(turn);
        float s = (float) (scale * squash);
        pose.scale(s, s, s);
        pose.translate(-MOUTH.x, -MOUTH.y, -MOUTH.z);
        if (renderer == null) renderer = new Renderer();
        renderer.render(pose, this, ctx.buffers(), null, null, light, ctx.partial);
    }

    /** Where a point of the model (blocks from its origin) is in the world, placed as {@link #render} places it. */
    public static Vec3 point(Vec3 model, Vec3 mouth, Vec3 forward, Vec3 up, double scale) {
        Vec3 back = forward.normalize().scale(-1);
        Vec3 u = up.subtract(back.scale(up.dot(back)));
        u = u.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : u.normalize();
        Vec3 right = u.cross(back).normalize();
        Vec3 d = model.subtract(MOUTH).scale(scale);
        return mouth.add(right.scale(d.x)).add(u.scale(d.y)).add(back.scale(d.z));
    }

    /** Forgets the renderer (after a resource reload). */
    public static void reset() {
        renderer = null;
    }

    private static final class Renderer extends GeoObjectRenderer<Worldburner> {
        Renderer() {
            super(new GeoModel<>() {
                @Override
                public ResourceLocation getModelResource(Worldburner animatable) {
                    return MODEL;
                }

                @Override
                public ResourceLocation getTextureResource(Worldburner animatable) {
                    return TEXTURE;
                }

                @Override
                public ResourceLocation getAnimationResource(Worldburner animatable) {
                    return ANIMATIONS;
                }
            });
            // its magma crust, gills, wing veins and eyes glow
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        /** Placed exactly where the effect put it (the object renderer otherwise centres its model on a block). */
        @Override
        public void preRender(PoseStack poseStack, Worldburner animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                              @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            this.objectRenderTranslations = new Matrix4f(poseStack.last().pose());
            scaleModelForRender(this.scaleWidth, this.scaleHeight, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
        }
    }
}
