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
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Worldburner, the Arch Dragon of Hell: the artist's model, 256 blocks from snout to tail, flown by the Arch Dragon's
 * effect along its path. It is not an entity - the effect hands it its path each frame and chooses its animations:
 * flapping as it climbs, gliding high and in the dive, roaring as it breaks out of the ground and breathing fire as it
 * falls on its prey. Its body sits on the path and its neck and tail bend joint by joint to follow it, so it snakes
 * through its turns rather than sliding round them stiff.
 */
public final class Worldburner implements GeoAnimatable {
    private static final ResourceLocation MODEL = FantasyWeapons.id("geo/entity/worldburner.geo.json");
    private static final ResourceLocation TEXTURE = FantasyWeapons.id("textures/entity/worldburner.png");
    private static final ResourceLocation ANIMATIONS = FantasyWeapons.id("animations/entity/worldburner.animation.json");
    /** Its mouth, the root of its body and the tip of its tail, in blocks from the model's origin (it faces north, -z). */
    public static final Vec3 MOUTH = new Vec3(0, 27.5, -100.5), BODY = new Vec3(0, 23, 0), TAIL = new Vec3(0, 20, 156);
    /** Snout to tail tip, in blocks. */
    public static final double LENGTH = 256;
    /** Ticks the roar lasts; ticks into the fire breath its jaws open, and close again. */
    public static final int ROAR_LENGTH = 80, BREATH_OPENS = 30, BREATH_CLOSES = 103;

    public static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.worldburner.fly");
    public static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.worldburner.glide");
    public static final RawAnimation HOVER = RawAnimation.begin().thenLoop("animation.worldburner.idle");
    public static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.worldburner.roar");
    public static final RawAnimation FIRE_BREATH = RawAnimation.begin().thenPlay("animation.worldburner.fire_breath");

    /** The joints of its neck out to its head, and of its tail; each chain ends at a marker bone. */
    private static final String[] NECK = {"neck_0", "neck_1", "neck_2", "neck_3", "neck_4", "neck_5", "neck_6", "neck_7", "head"};
    private static final String[] TAILS = {"tail_0", "tail_1", "tail_2", "tail_3", "tail_4", "tail_5", "tail_6", "tail_7", "tail_8",
            "tail_9", "tail_10", "tail_11", "tail_12", "tail_13"};

    private static Renderer renderer;

    /**
     * Its path, measured back from its mouth: where the point {@code back} blocks behind the mouth is, which way it is
     * flying there and which way its back faces.
     */
    public interface Spine {
        Vec3 at(double back);

        Vec3 heading(double back);

        Vec3 up(double back);
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private double tick;
    private RawAnimation movement = FLY;
    @Nullable
    private RawAnimation action;
    // this frame's placing: its path, its size, and where its body is
    @Nullable
    private Spine spine;
    private double scale = 1;
    private Vec3 anchor = Vec3.ZERO;
    private Matrix3f root = new Matrix3f();
    // where its jaws and tail tip ended up (model space), and which way its head points
    @Nullable
    private Vector3f mouth, tailTip;
    private final Vector3f jaws = new Vector3f(0, 0, -1);

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
        controllers.add(new AnimationController<>(this, "movement", 20, state -> state.setAndContinue(movement)));
        // registered second, so it wins on the neck and head
        controllers.add(new AnimationController<>(this, "action", 10, state -> action == null ? PlayState.STOP : state.setAndContinue(action)));
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
     * Draws it along {@code spine} at {@code scale} times its size; {@code ticks} (the effect's own clock) drive its
     * animations, so they keep step with its flight and stop when the game is paused. Far off, it is drawn nearer and
     * smaller in proportion (the same picture), so it shows past the fog.
     */
    public void render(VfxContext ctx, Spine spine, double scale, int light, float ticks) {
        this.tick = ticks;
        this.spine = spine;
        this.scale = scale;
        anchor = spine.at(back(BODY.z));
        root = frame(BODY.z);
        double squash = ctx.squashFor(anchor, VfxContext.viewReach() * 0.85);
        PoseStack pose = new PoseStack();
        pose.translate((anchor.x - ctx.cam.x) * squash, (anchor.y - ctx.cam.y) * squash, (anchor.z - ctx.cam.z) * squash);
        pose.last().pose().mul(new Matrix4f(root));
        pose.last().normal().mul(root);
        float s = (float) (scale * squash);
        pose.scale(s, s, s);
        pose.translate(-BODY.x, -BODY.y, -BODY.z);
        if (renderer == null) renderer = new Renderer();
        renderer.render(pose, this, ctx.buffers(), null, null, light, ctx.partial);
    }

    /** Where its jaws are in the world (as last drawn). */
    public Vec3 mouth() {
        return mouth == null ? spine == null ? Vec3.ZERO : spine.at(0) : world(mouth);
    }

    /** Where the tip of its tail is in the world (as last drawn). */
    public Vec3 tailTip() {
        return tailTip == null ? spine == null ? Vec3.ZERO : spine.at(back(TAIL.z)) : world(tailTip);
    }

    /** Which way its head points (as last drawn). */
    public Vec3 jaws() {
        Vector3f d = root.transform(new Vector3f(jaws));
        return new Vec3(d.x, d.y, d.z).normalize();
    }

    private Vec3 world(Vector3f model) {
        Vector3f d = root.transform(new Vector3f(model).sub((float) BODY.x, (float) BODY.y, (float) BODY.z));
        return anchor.add(d.x * scale, d.y * scale, d.z * scale);
    }

    /** Blocks along its path behind its mouth of the point {@code z} blocks back along the model. */
    private double back(double z) {
        return (z - MOUTH.z) * scale;
    }

    /** Its frame at model depth {@code z}: columns right, up and back (the model's x, y and z) in the world. */
    private Matrix3f frame(double z) {
        double d = back(z);
        Vec3 fwd = spine.heading(d);
        Vec3 back = fwd.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : fwd.normalize().scale(-1);
        Vec3 up = spine.up(d);
        Vec3 u = up.subtract(back.scale(up.dot(back)));
        u = u.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : u.normalize();
        Vec3 right = u.cross(back).normalize();
        return new Matrix3f((float) right.x, (float) right.y, (float) right.z, (float) u.x, (float) u.y, (float) u.z, (float) back.x,
                (float) back.y, (float) back.z);
    }

    /**
     * Bends its neck and tail along its path, on top of what its animations do: each joint turns by as much as the path
     * turns between the middle of the bone before it and the middle of its own. Then notes where its jaws and tail tip
     * have ended up.
     */
    private void bend(AnimationProcessor<Worldburner> bones) {
        if (spine == null) return;
        GeoBone body = bones.getBone("body");
        if (body == null) return;
        Matrix4f trunk = local(body);
        Matrix4f head = new Matrix4f(trunk);
        mouth = chain(bones, NECK, "mouth", head);
        if (mouth != null) head.transformDirection(jaws.set(0, 0, -1)).normalize();
        tailTip = chain(bones, TAILS, "tail_tip", new Matrix4f(trunk));
    }

    @Nullable
    private Vector3f chain(AnimationProcessor<Worldburner> bones, String[] names, String end, Matrix4f m) {
        Matrix3f parent = root;
        Quaternionf q = new Quaternionf();
        Vector3f euler = new Vector3f();
        for (int i = 0; i < names.length; i++) {
            GeoBone bone = bones.getBone(names[i]);
            GeoBone next = bones.getBone(i + 1 < names.length ? names[i + 1] : end);
            if (bone == null || next == null) return null;
            Matrix3f here = frame((bone.getPivotZ() + next.getPivotZ()) / 32.0);
            q.setFromNormalized(new Matrix3f(parent).transpose().mul(here)).rotateZYX(bone.getRotZ(), bone.getRotY(), bone.getRotX());
            q.getEulerAnglesZYX(euler);
            bone.setRotX(euler.x);
            bone.setRotY(euler.y);
            bone.setRotZ(euler.z);
            m.mul(local(bone));
            parent = here;
        }
        GeoBone tip = bones.getBone(end);
        return tip == null ? null : m.transformPosition(new Vector3f(tip.getPivotX() / 16f, tip.getPivotY() / 16f, tip.getPivotZ() / 16f));
    }

    /** A bone's own transform (blocks), as GeckoLib applies it. */
    private static Matrix4f local(GeoBone b) {
        float px = b.getPivotX() / 16f, py = b.getPivotY() / 16f, pz = b.getPivotZ() / 16f;
        return new Matrix4f().translate(-b.getPosX() / 16f, b.getPosY() / 16f, b.getPosZ() / 16f).translate(px, py, pz)
                .rotateZYX(b.getRotZ(), b.getRotY(), b.getRotX()).scale(b.getScaleX(), b.getScaleY(), b.getScaleZ()).translate(-px, -py, -pz);
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

                @Override
                public void handleAnimations(Worldburner animatable, long instanceId, AnimationState<Worldburner> state, float partialTick) {
                    // on the effect's clock, not the wall clock
                    state.setData(DataTickets.TICK, animatable.tick);
                    super.handleAnimations(animatable, instanceId, state, partialTick);
                }

                @Override
                public void setCustomAnimations(Worldburner animatable, long instanceId, AnimationState<Worldburner> state) {
                    animatable.bend(getAnimationProcessor());
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
