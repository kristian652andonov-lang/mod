package com.aigirlfriend.client.render;

import com.aigirlfriend.client.ClientState;
import com.aigirlfriend.companion.Outfit;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import com.aigirlfriend.config.GFClientConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.List;

/** GeckoLib renderer plus her nameplate and speech bubbles. */
public class GirlfriendRenderer extends GeoEntityRenderer<GirlfriendEntity> {
    /** Every body bone any outfit may hide; shown or hidden per entity, per frame. */
    private static final Set<String> HIDEABLE = Arrays.stream(Outfit.values()).flatMap(o -> o.hiddenBodyBones.stream()).collect(Collectors.toUnmodifiableSet());

    public GirlfriendRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GirlfriendModel());
        this.shadowRadius = 0.45f;
        withScale(0.9375f); // player scale: the model is 32 px tall
        OutfitLayer outfits = new OutfitLayer(this);
        addRenderLayer(outfits);
        addRenderLayer(new FaceExpressionLayer(this, outfits));
    }

    @Override
    public void preRender(PoseStack poseStack, GirlfriendEntity gf, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                          @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, gf, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        if (isReRender) return;
        Set<String> hidden = gf.getOutfitType().hiddenBodyBones;
        for (String name : HIDEABLE) model.getBone(name).ifPresent(b -> b.setHidden(hidden.contains(name)));
    }

    private static final float SLEEP_HEAD_SHIFT = 0.95f;
    private static final float SLEEP_DROP = 0.28f;

    /** Sleeping clips lay her on her side themselves (head toward model +X): only align her with the bed. */
    @Override
    protected void applyRotations(GirlfriendEntity gf, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        if (gf.hasPose(Pose.SLEEPING)) {
            Direction bed = gf.getBedOrientation();
            float yaw = bed == null || bed.getAxis().isVertical() ? 180f - rotationYaw : switch (bed) {
                case EAST -> 0f;
                case NORTH -> 90f;
                case WEST -> 180f;
                default -> 270f;
            };
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            // GeckoLib already moved her towards the foot like a vanilla sleeper; the clips lie her down around her
            // hips instead, so slide her back up to the pillow and down onto the mattress (model +X = bed head)
            poseStack.translate(SLEEP_HEAD_SHIFT, -SLEEP_DROP, 0);
            // the clips lie her on her right side, facing the bed's counter-clockwise side; when her partner is on
            // the other side (e.g. she took the bed to his left), mirror her so she still faces him
            if (bed != null && bed.getAxis().isHorizontal() && partnerOnBackSide(gf, bed)) poseStack.scale(1f, 1f, -1f);
            return;
        }
        if (gf.getVehicle() instanceof net.minecraft.world.entity.player.Player) {
            // like vanilla, GeckoLib renders a passenger of a living entity with the vehicle's body rotation, so the
            // carried / lap facing offset has to be added here
            boolean lap = gf.getSyncedInteraction() == com.aigirlfriend.interaction.InteractionType.LAP_SIT;
            rotationYaw += (float) (lap ? com.aigirlfriend.entity.PoseTuning.lapYaw(true) : com.aigirlfriend.entity.PoseTuning.carryYaw(true));
        }
        super.applyRotations(gf, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
    }

    private static boolean partnerOnBackSide(GirlfriendEntity gf, Direction bed) {
        net.minecraft.world.entity.Entity partner = gf.level().getEntity(gf.getSyncedPartnerId());
        if (partner == null) return false;
        Direction facing = bed.getCounterClockWise();
        double dot = (partner.getX() - gf.getX()) * facing.getStepX() + (partner.getZ() - gf.getZ()) * facing.getStepZ();
        return dot < -0.05;
    }

    @Override
    public void render(GirlfriendEntity gf, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(gf, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (gf.isInvisible() || !Minecraft.renderNames()) return; // F1 hides names and bubbles like vanilla nametags
        double distSq = entityRenderDispatcher.distanceToSqr(gf);
        if (distSq > 32 * 32) return;
        float y = gf.getBbHeight() + 0.45f;
        if (gf.isSleeping()) y = 1.0f;
        ClientState.Speech speech = GFClientConfig.SPEECH_BUBBLES.get() ? ClientState.speech(gf.getId()) : null;
        boolean looking = Minecraft.getInstance().crosshairPickEntity == gf;
        if (speech != null) {
            renderBubble(gf, speech, y, poseStack, bufferSource, packedLight, partialTick);
        } else if (looking && distSq < 12 * 12) {
            renderLine(Component.literal(gf.getCompanionName()).withStyle(s -> s.withColor(0xFFB6D9)), y - 0.2f, poseStack, bufferSource, packedLight, 1f);
        }
    }

    private void renderLine(Component text, float y, PoseStack poseStack, MultiBufferSource buffers, int light, float alpha) {
        poseStack.pushPose();
        poseStack.translate(0, y, 0);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.025f, -0.025f, 0.025f);
        Font font = getFont();
        Matrix4f m = poseStack.last().pose();
        float x = -font.width(text) / 2f;
        int bg = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255f * alpha) << 24;
        font.drawInBatch(text, x, 0, 0x20FFFFFF, false, m, buffers, Font.DisplayMode.SEE_THROUGH, bg, light);
        font.drawInBatch(text, x, 0, 0xFFFFFFFF, false, m, buffers, Font.DisplayMode.NORMAL, 0, light);
        poseStack.popPose();
    }

    private void renderBubble(GirlfriendEntity gf, ClientState.Speech speech, float y, PoseStack poseStack, MultiBufferSource buffers, int light, float partialTick) {
        Font font = getFont();
        String text = speech.typing() ? typingDots() : speech.text();
        List<FormattedCharSequence> lines = font.split(Component.literal(text), 170);
        if (lines.size() > 6) lines = lines.subList(0, 6);
        long now = ClientState.clientTicks;
        float age = now - speech.startTick() + partialTick;
        float left = speech.endTick() - now - partialTick;
        float alpha = Mth.clamp(Math.min(age / 5f, left / 10f), 0.05f, 1f);
        int lineHeight = font.lineHeight + 1;
        float baseY = y + 0.12f + lines.size() * lineHeight * 0.025f;
        poseStack.pushPose();
        poseStack.translate(0, baseY, 0);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        float pop = 0.85f + 0.15f * Mth.clamp(age / 4f, 0f, 1f);
        poseStack.scale(0.025f * pop, -0.025f * pop, 0.025f * pop);
        Matrix4f m = poseStack.last().pose();
        int bgAlpha = (int) (0.55f * 255f * alpha);
        int textColor = ((int) (255 * alpha) << 24) | 0xFFFFFF;
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            float x = -font.width(line) / 2f;
            float ly = i * lineHeight;
            font.drawInBatch(line, x, ly, textColor, false, m, buffers, Font.DisplayMode.NORMAL, (bgAlpha << 24) | 0x3A1F33, light);
        }
        String name = gf.getCompanionName();
        float nx = -font.width(name) / 2f;
        font.drawInBatch(name, nx, -lineHeight - 1, ((int) (255 * alpha) << 24) | 0xFFB6D9, false, m, buffers, Font.DisplayMode.NORMAL, 0, light);
        poseStack.popPose();
    }

    private static String typingDots() {
        int n = (int) (ClientState.clientTicks / 6 % 3) + 1;
        return ".".repeat(n);
    }
}
