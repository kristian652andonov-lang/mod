package com.fantasyweapons.client.render;

import com.fantasyweapons.client.ClientState;
import com.fantasyweapons.client.anim.WeaponPoses;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * GeckoLib renderer for all fantasy weapons. Adds:
 * <ul>
 *   <li>the artist's {@code _glowmask} emissive layer (always on)</li>
 *   <li>an extra additive glow pass whose intensity follows the ability charge (the weapon visibly powers up)</li>
 *   <li>a subtle breathing glow once the weapon is fully mastered</li>
 *   <li>hiding the in-hand model while the weapon is thrown (the thrown entity renders it instead)</li>
 * </ul>
 * Big models are drawn as cheaply as they can be without looking any different (see {@link QuadCull}): faces with nothing
 * on them are skipped, the glow passes draw only the faces that glow, bones the animation has shrunk to nothing are not
 * drawn, a model's see-through part alone is drawn translucent (the rest as an ordinary cut-out model), and the heaviest
 * models leave out their glow passes as inventory icons.
 */
public class WeaponRenderer extends GeoItemRenderer<FantasyWeaponItem> implements LeanGeoRenderer<FantasyWeaponItem> {
    /** Which pass is drawing: the model itself, a glow pass (only the faces that glow), or the see-through part alone. */
    enum Pass { MAIN, GLOW, EDGE }

    /** Models with a see-through part (the Weapon of God's radiant edge): that bone, drawn translucent on its own. */
    private static final java.util.Map<String, String> TRANSLUCENT_BONE = java.util.Map.of("weapon_of_god", "radiant_edge");
    /** Models with more cubes than this leave out their glow passes as inventory icons (16 pixels show no difference). */
    private static final int HEAVY_CUBES = 500;
    /** A bone scaled below this (on two axes or more) is hidden. */
    private static final float HIDDEN_SCALE = 0.005f;
    private static final java.util.Map<BakedGeoModel, Integer> CUBES = new java.util.IdentityHashMap<>();

    private Pass pass = Pass.MAIN;
    /** The model being drawn: its texture, its see-through bone (or null), and whether the pass is inside that bone. */
    @Nullable
    private net.minecraft.resources.ResourceLocation drawnTexture;
    @Nullable
    private String translucentBone;
    private boolean insideTranslucent;
    private boolean heavy;

    public WeaponRenderer() {
        super(new WeaponGeoModel());
        addRenderLayer(new LeanGlowLayer<>(this) {
            @Override
            public void render(PoseStack poseStack, FantasyWeaponItem animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
                               MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                if (iconOfHeavyModel()) return;
                pass = Pass.GLOW;
                try {
                    super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
                } finally {
                    pass = Pass.MAIN;
                }
            }
        });
        addRenderLayer(new ChargeGlowLayer(this));
        addRenderLayer(new TranslucentBoneLayer(this));
    }

    /** DEVELOPMENT ONLY (screenshot director): icons drawn in full, glow and all (for capturing a picture of one). */
    public static boolean fullIcons;
    /** DEVELOPMENT ONLY (screenshot director): every face drawn in every pass, for measuring what the culling saves. */
    public static boolean drawAllFaces;
    /** DEVELOPMENT ONLY: time spent drawing weapons on this thread (nanoseconds) and how many draws, for measuring. */
    public static long debugNanos, debugDraws;

    /** Whether this draw is a heavy model as an inventory icon (its glow passes left out). */
    private boolean iconOfHeavyModel() {
        return heavy && renderPerspective == ItemDisplayContext.GUI && !fullIcons;
    }

    private static int cubes(BakedGeoModel model) {
        return CUBES.computeIfAbsent(model, m -> {
            int n = 0;
            java.util.ArrayDeque<GeoBone> todo = new java.util.ArrayDeque<>(m.topLevelBones());
            while (!todo.isEmpty()) {
                GeoBone b = todo.pop();
                n += b.getCubes().size();
                todo.addAll(b.getChildBones());
            }
            return n;
        });
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (ctx != ItemDisplayContext.GUI && ctx != ItemDisplayContext.FIXED && ctx != ItemDisplayContext.GROUND
                && ClientState.isThrown(FantasyWeaponItem.data(stack).idOrNil())) {
            return;
        }
        if (ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            WeaponPoses.Pose p = WeaponPoses.renderingPose(stack);
            if (p != null && (p.gx() != 0 || p.gz() != 0)) applyGrip(stack, ctx, pose, p);
        }
        float yaw = handleYaw(stack, ctx);
        if (yaw != 0) {
            // spin the model about its own handle (GeckoLib draws the model origin at (0.5, 0.51, 0.5))
            pose.translate(0.5f, 0, 0.5f);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
            pose.translate(-0.5f, 0, -0.5f);
        }
        LivingEntity tracked = chainHolder(stack, ctx);
        if (tracked != null) ChainTracker.begin(tracked, ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
        LivingEntity holder = holder(stack, ctx);
        HangingPhysics.begin(holder == null ? -1 : holder.getId(), ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        long t0 = System.nanoTime();
        try {
            super.renderByItem(stack, ctx, pose, buffers, light, overlay);
        } finally {
            debugNanos += System.nanoTime() - t0;
            debugDraws++;
            if (tracked != null) ChainTracker.end();
            HangingPhysics.end();
        }
    }

    @Override
    public void actuallyRender(PoseStack poseStack, FantasyWeaponItem animatable, BakedGeoModel model, @Nullable RenderType renderType, MultiBufferSource bufferSource,
                               @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        // the model's place in the world, for parts that hang and swing (bones are animated inside this call)
        if (!isReRender) {
            HangingPhysics.pose(poseStack.last().pose());
            drawnTexture = getTextureLocation(animatable);
            translucentBone = TRANSLUCENT_BONE.get(animatable.definition().geoName());
            heavy = cubes(model) > HEAVY_CUBES;
            insideTranslucent = false;
        }
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    /** The entity holding this stack in hand for this draw, or null (inventory, item frame, dropped). */
    @Nullable
    private static LivingEntity holder(ItemStack stack, ItemDisplayContext ctx) {
        if (ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            LivingEntity p = Minecraft.getInstance().player;
            return p != null && (p.getMainHandItem() == stack || p.getOffhandItem() == stack) ? p : null;
        }
        if (ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            LivingEntity e = WeaponPoses.renderingEntity();
            return e != null && (e.getMainHandItem() == stack || e.getOffhandItem() == stack) ? e : null;
        }
        return null;
    }

    /** DEVELOPMENT ONLY (screenshot director): overrides the first-person / third-person handle yaw. */
    public static Float debugFpYaw, debugTpYaw;

    /**
     * First person: weapons are turned about their handle so the striking side (hammer faces, axe and scythe blades,
     * sword edges) points into the screen instead of showing the flat side.
     */
    private static final float FP_YAW_HEAVY = 55f, FP_YAW_BLADE = 35f;
    /**
     * Models whose business end sits on the other side of the handle (or reads as backwards) are turned around; the
     * first- and third-person holds differ, so each view has its own table.
     */
    private static final java.util.Map<String, Float> TP_FACING = java.util.Map.of("doomcleaver", 180f, "gravebite", 180f, "bloomfall", 180f);
    private static final java.util.Map<String, Float> FP_FACING = java.util.Map.of("starforge", 180f, "soulreaper", 180f, "eclipse_reaper", 180f,
            "the_weapon_of_god", 180f);
    /**
     * Forms whose model carries its business end on the other side of the handle from the weapon's base form (Eclipse
     * Reaper's dark blade grows out of the far side of the staff): turned round in both views so it faces the same way.
     */
    private static final java.util.Map<String, Float> FORM_FACING = java.util.Map.of("eclipse_reaper/dark", 180f);

    /** Rotation of the weapon about its handle for the given view, in degrees. */
    private static float handleYaw(ItemStack stack, ItemDisplayContext ctx) {
        boolean fp = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean tp = ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!fp && !tp || !(stack.getItem() instanceof FantasyWeaponItem item)) return 0;
        String id = item.definition().id();
        // blades keep a sliver of their flat side in view; axes, hammers and scythes point their head into the screen
        float fpYaw = switch (item.definition().weaponClass().swingStyle()) {
            case CHOP, SLAM, REAP -> FP_YAW_HEAVY;
            default -> FP_YAW_BLADE;
        };
        float formYaw = formFacing(item, stack);
        if (fp) return debugFpYaw != null ? debugFpYaw : FP_FACING.getOrDefault(id, 0f) + fpYaw + formYaw;
        return debugTpYaw != null ? debugTpYaw : TP_FACING.getOrDefault(id, 0f) + formYaw;
    }

    /** Each held weapon's form turn as last drawn (degrees) and when (nanoseconds), by GeckoLib instance id. */
    private static final java.util.Map<Long, double[]> FORM_TURN = new java.util.HashMap<>();
    /** How fast the form turn catches up when an animation is cut short, degrees per second. */
    private static final double FORM_TURN_SPEED = 450;

    /**
     * How far the weapon's form turns it round (see {@link #FORM_FACING}), read from the animation playing rather than
     * the form the weapon is in, so the turn changes exactly when the parts' layout does: a form's own animations
     * hold its turn, the animation that brings a form in turns the weapon round as it plays, and the weapon's other
     * animations hold the turn of the form that plays them as they were made. (The form itself changes a moment before
     * or after its animation arrives.) If an animation is cut short the turn catches up over a few ticks, never at once.
     */
    private static float formFacing(FantasyWeaponItem item, ItemStack stack) {
        var def = item.definition();
        String id = def.id();
        if (!def.hasForms() || def.forms().stream().noneMatch(f -> FORM_FACING.containsKey(id + "/" + f.id()))) return 0;
        var form = def.form(FantasyWeaponItem.data(stack));
        double target = form == null ? 0 : FORM_FACING.getOrDefault(id + "/" + form.id(), 0f);
        long instance = software.bernie.geckolib.animatable.GeoItem.getId(stack);
        var manager = item.getAnimatableInstanceCache().getManagerForId(instance);
        var controller = manager == null ? null : manager.getAnimationControllers().get(FantasyWeaponItem.CONTROLLER);
        if (controller instanceof com.fantasyweapons.weapon.RigidAnimationController<?> rigid && !rigid.currentName().isEmpty()) {
            String name = rigid.currentName();
            com.fantasyweapons.weapon.WeaponForm entering = null, owner = null, plain = null;
            for (var f : def.forms()) {
                if (f.enterAnimation() != null && name.equals(def.anim(f.enterAnimation()))) entering = f;
                else if (f.owns(name)) owner = f;
                if (f.animationVariant() == null && plain == null) plain = f;
            }
            if (entering != null) {
                var into = entering;
                var from = def.forms().stream().filter(f -> f != into).findFirst().orElse(into);
                double a = FORM_FACING.getOrDefault(id + "/" + from.id(), 0f), b = FORM_FACING.getOrDefault(id + "/" + into.id(), 0f);
                double p = rigid.progress();
                target = a + (b - a) * p * p * (3 - 2 * p);
            } else if (owner != null) {
                target = FORM_FACING.getOrDefault(id + "/" + owner.id(), 0f);
            } else if (plain != null) {
                target = FORM_FACING.getOrDefault(id + "/" + plain.id(), 0f);
            }
        }
        long now = System.nanoTime();
        double[] turn = FORM_TURN.get(instance);
        if (turn == null) {
            if (FORM_TURN.size() > 64) FORM_TURN.values().removeIf(t -> now - (long) t[1] > 10_000_000_000L);
            turn = new double[]{target, now};
            FORM_TURN.put(instance, turn);
        }
        double step = Math.min(0.25, (now - (long) turn[1]) / 1e9) * FORM_TURN_SPEED;
        turn[0] += Math.max(-step, Math.min(step, target - turn[0]));
        turn[1] = now;
        return (float) turn[0];
    }

    /** The entity whose held Infernochain is being drawn in hand right now (its segments get tracked), or null. */
    @Nullable
    private static LivingEntity chainHolder(ItemStack stack, ItemDisplayContext ctx) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item) || !"infernochain".equals(item.definition().id())) return null;
        if (ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            LivingEntity p = Minecraft.getInstance().player;
            return p != null && p.getMainHandItem() == stack ? p : null;
        }
        if (ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            LivingEntity e = WeaponPoses.renderingEntity();
            return e != null && e.getMainHandItem() == stack ? e : null;
        }
        return null;
    }

    @Override
    public void renderRecursively(PoseStack poseStack, FantasyWeaponItem animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        // a big model's bone the animation has shrunk to nothing (the radiant edge outside its clips) draws nothing, nor do its
        // children (only big models: the lighter ones' hanging chains follow their bones even while hidden)
        // (the artist hides parts by scaling them to a thousandth, too small to see on any model)
        int flat = (Math.abs(bone.getScaleX()) < HIDDEN_SCALE ? 1 : 0) + (Math.abs(bone.getScaleY()) < HIDDEN_SCALE ? 1 : 0)
                + (Math.abs(bone.getScaleZ()) < HIDDEN_SCALE ? 1 : 0);
        if (heavy && flat >= 2) return;
        boolean was = insideTranslucent;
        if (!was && translucentBone != null && translucentBone.equals(bone.getName())) insideTranslucent = true;
        try {
            super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        } finally {
            insideTranslucent = was;
        }
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (ChainTracker.active()) ChainTracker.bone(bone, poseStack.last().pose());
        // the see-through part is drawn by its own pass, and that pass draws nothing else
        if (translucentBone != null && (pass == Pass.MAIN && insideTranslucent || pass == Pass.EDGE && !insideTranslucent)) return;
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    /** The texture the model is being drawn with (what {@link ModelCulling} leaves out is worked out from it). */
    @Override
    public net.minecraft.resources.ResourceLocation drawnTexture() {
        return drawAllFaces ? null : drawnTexture;
    }

    @Override
    public boolean drawingGlow() {
        return pass == Pass.GLOW;
    }

    /** The model's see-through part (the Weapon of God's radiant edge) alone, drawn translucent so it blends. */
    static final class TranslucentBoneLayer extends GeoRenderLayer<FantasyWeaponItem> {
        private final WeaponRenderer renderer;

        TranslucentBoneLayer(WeaponRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void render(PoseStack poseStack, FantasyWeaponItem animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
                           MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (renderer.translucentBone == null) return;
            RenderType see = RenderType.entityTranslucent(getTextureResource(animatable));
            renderer.pass = Pass.EDGE;
            try {
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, see, bufferSource.getBuffer(see), partialTick,
                        packedLight, packedOverlay, 0xFFFFFFFF);
            } finally {
                renderer.pass = Pass.MAIN;
            }
        }
    }

    /**
     * Re-grips the weapon in the hand: rotates it about the hand point (before the item's display transform) so
     * poses can rest a greatsword on the shoulder or swing a blade past the forearm. The pose stack here is
     * {@code hand · display · translate(-0.5)}; we turn it into {@code hand · grip · display · translate(-0.5)}.
     */
    private static void applyGrip(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, WeaponPoses.Pose p) {
        boolean left = ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        BakedModel model = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(stack);
        PoseStack tmp = new PoseStack();
        model.applyTransform(ctx, tmp, left);
        Matrix4f display = new Matrix4f(tmp.last().pose());
        Matrix4f m = new Matrix4f().translation(0.5f, 0.5f, 0.5f)
                .mul(new Matrix4f(display).invert())
                .rotateX(p.gx())
                .rotateZ(left ? -p.gz() : p.gz())
                .mul(display)
                .translate(-0.5f, -0.5f, -0.5f);
        pose.mulPose(m);
    }

    /** Additive re-render of the emissive parts, scaled by charge (and mastery). */
    static final class ChargeGlowLayer extends GeoRenderLayer<FantasyWeaponItem> {
        private final WeaponRenderer renderer;

        ChargeGlowLayer(WeaponRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void render(PoseStack poseStack, FantasyWeaponItem animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
                           MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            ItemStack stack = renderer.getCurrentItemStack();
            if (stack == null || stack.isEmpty()) return;
            WeaponData data = FantasyWeaponItem.data(stack);
            float charge = ClientState.chargeOf(data.idOrNil());
            // the emissive parts brighten towards the weapon's own colour; capped so pale blades (Frostrend's ice)
            // keep their detail instead of washing out to white
            float intensity = charge * (charge >= 1f ? 0.62f + 0.13f * (float) Math.sin(Util.getMillis() / 110.0) : 0.55f);
            if (ProgressionMath.mastery(animatable.definition(), data) >= 0.9999f) {
                intensity = Math.max(intensity, 0.16f + 0.1f * (float) Math.sin(Util.getMillis() / 600.0));
            }
            if (intensity <= 0.02f || renderer.iconOfHeavyModel()) return;
            int theme = animatable.definition().themePrimary(data);
            int r = Math.round(((theme >> 16) & 255) * intensity), g = Math.round(((theme >> 8) & 255) * intensity), b = Math.round((theme & 255) * intensity);
            int color = 0xFF000000 | (r << 16) | (g << 8) | b;
            RenderType glow = RenderType.eyes(AutoGlowingTexture.getEmissiveResource(getTextureResource(animatable)));
            renderer.pass = Pass.GLOW;
            try {
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
                        0xF000F0, packedOverlay, color);
            } finally {
                renderer.pass = Pass.MAIN;
            }
        }
    }
}
