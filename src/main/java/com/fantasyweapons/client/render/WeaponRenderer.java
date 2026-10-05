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
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * GeckoLib renderer for all fantasy weapons. Adds:
 * <ul>
 *   <li>the artist's {@code _glowmask} emissive layer (always on)</li>
 *   <li>an extra additive glow pass whose intensity follows the ability charge (the weapon visibly powers up)</li>
 *   <li>a subtle breathing glow once the weapon is fully mastered</li>
 *   <li>hiding the in-hand model while the weapon is thrown (the thrown entity renders it instead)</li>
 * </ul>
 */
public class WeaponRenderer extends GeoItemRenderer<FantasyWeaponItem> {
    public WeaponRenderer() {
        super(new WeaponGeoModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        addRenderLayer(new ChargeGlowLayer(this));
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
        try {
            super.renderByItem(stack, ctx, pose, buffers, light, overlay);
        } finally {
            if (tracked != null) ChainTracker.end();
        }
    }

    /** DEVELOPMENT ONLY (screenshot director): overrides the first-person / third-person handle yaw. */
    public static Float debugFpYaw, debugTpYaw;

    /**
     * First person: weapons are turned about their handle so the striking side (hammer faces, axe and scythe blades,
     * sword edges) points into the screen instead of showing the flat side.
     */
    private static final float FP_YAW_HEAVY = 55f, FP_YAW_BLADE = 35f;
    /** Models whose business end sits on the other side of the handle (or reads as backwards) are turned around. */
    private static final java.util.Map<String, Float> FACING = java.util.Map.of("soulreaper", 180f, "eclipse_reaper", 180f, "starforge", 180f,
            "bloomfall", 180f);

    /** Rotation of the weapon about its handle for the given view, in degrees. */
    private static float handleYaw(ItemStack stack, ItemDisplayContext ctx) {
        boolean fp = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean tp = ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!fp && !tp || !(stack.getItem() instanceof FantasyWeaponItem item)) return 0;
        float facing = FACING.getOrDefault(item.definition().id(), 0f);
        // blades keep a sliver of their flat side in view; axes, hammers and scythes point their head into the screen
        float fpYaw = switch (item.definition().weaponClass().swingStyle()) {
            case CHOP, SLAM, REAP -> FP_YAW_HEAVY;
            default -> FP_YAW_BLADE;
        };
        if (fp) return debugFpYaw != null ? debugFpYaw : facing + fpYaw;
        return debugTpYaw != null ? debugTpYaw : facing;
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
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (ChainTracker.active()) ChainTracker.bone(bone, poseStack.last().pose());
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
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
            float intensity = charge * (charge >= 1f ? 1.0f + 0.35f * (float) Math.sin(Util.getMillis() / 90.0) : 0.85f);
            if (ProgressionMath.mastery(animatable.definition(), data) >= 0.9999f) {
                intensity = Math.max(intensity, 0.18f + 0.12f * (float) Math.sin(Util.getMillis() / 600.0));
            }
            if (intensity <= 0.02f) return;
            RenderType glow = RenderType.eyes(AutoGlowingTexture.getEmissiveResource(getTextureResource(animatable)));
            int passes = intensity > 1f ? 2 : 1;
            for (int i = 0; i < passes; i++) {
                float k = Math.min(1f, intensity - i);
                int v = Math.max(0, Math.min(255, Math.round(k * 255)));
                int color = 0xFF000000 | (v << 16) | (v << 8) | v;
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
                        0xF000F0, packedOverlay, color);
            }
        }
    }
}
