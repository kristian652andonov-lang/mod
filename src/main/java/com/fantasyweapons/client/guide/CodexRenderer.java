package com.fantasyweapons.client.guide;

import com.fantasyweapons.guide.WayfarersCodexItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import com.fantasyweapons.client.render.LeanGeoRenderer;
import com.fantasyweapons.client.render.LeanGlowLayer;

/**
 * Draws the Codex. In first person it is animated on its own (opening and held open, while the inventory icon and the
 * book in other hands stay shut); while it is read it is fully lit, and the pages of the open spread are drawn onto it,
 * placed by where its two page parts were just drawn.
 */
public final class CodexRenderer extends GeoItemRenderer<WayfarersCodexItem> implements LeanGeoRenderer<WayfarersCodexItem> {
    /** Animation instances: the reader's first-person book, and every other view of it. */
    public static final long FIRST_PERSON = 1, ELSEWHERE = 2;

    /** Where the book is held for reading, before the eyes: its spine 17 pixels ahead and 1.5 below, tipped up towards the reader. */
    private static final Matrix4f READING = new Matrix4f().translation(0, -1.5f / 16, -17 / 16f)
            .rotateX((float) Math.toRadians(35)).rotateY((float) Math.PI);

    private final Matrix4f leftPage = new Matrix4f(), rightPage = new Matrix4f(), leaf = new Matrix4f();
    /** Where the pages' gilded frames were drawn this frame (right, left). */
    private final Matrix4f[] frames = {new Matrix4f(), new Matrix4f()};
    /** How far a frame stands off the leaf's paper (blocks), as it stands off its page. */
    private static final float FRAME_LIFT = 0.12f / 16;
    private boolean capturing, gotLeft, gotRight, gotLeaf;

    public CodexRenderer() {
        super(new CodexModel());
        addRenderLayer(new LeanGlowLayer<>(this));
    }

    @Override
    public long getInstanceId(WayfarersCodexItem animatable) {
        return renderPerspective != null && renderPerspective.firstPerson() ? FIRST_PERSON : ELSEWHERE;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        boolean reading = context.firstPerson() && Codex.reading();
        if (context.firstPerson()) raise(poseStack, Codex.raised());
        capturing = reading;
        gotLeft = gotRight = gotLeaf = false;
        if (reading) Codex.holdFrame(true);
        try {
            super.renderByItem(stack, context, poseStack, buffers, reading ? LightTexture.FULL_BRIGHT : light, overlay);
            capturing = false;
            if (reading && gotLeft && gotRight) CodexPages.render(leftPage, rightPage, gotLeaf ? leaf : null, buffers);
        } finally {
            capturing = false;
            if (reading) Codex.holdFrame(false);
        }
    }

    /**
     * Brings the book from where it is held (the item's first-person placement) up to {@link #READING}, by {@code t}: both
     * poses are taken in the camera's frame and blended (place, turn and size), so it glides between them.
     */
    private static void raise(PoseStack poseStack, float t) {
        if (t <= 0) return;
        Matrix4f view = com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix();
        // the model's origin: ItemRenderer moves by -0.5 before this renderer, GeckoLib by +0.5 (and 0.01 up) after it
        Matrix4f held = new Matrix4f(view).mul(poseStack.last().pose()).translate(0.5f, 0.51f, 0.5f);
        org.joml.Vector3f p0 = held.getTranslation(new org.joml.Vector3f()), p1 = READING.getTranslation(new org.joml.Vector3f());
        org.joml.Vector3f s0 = held.getScale(new org.joml.Vector3f());
        org.joml.Quaternionf q0 = held.getNormalizedRotation(new org.joml.Quaternionf()), q1 = READING.getNormalizedRotation(new org.joml.Quaternionf());
        Matrix4f blended = new Matrix4f().translation(p0.lerp(p1, t)).rotate(q0.slerp(q1, t)).scale(s0.lerp(new org.joml.Vector3f(1), t));
        Matrix4f pose = new Matrix4f(view).invert().mul(blended).translate(-0.5f, -0.51f, -0.5f);
        poseStack.last().pose().set(pose);
        poseStack.last().normal().set(new org.joml.Matrix3f(pose).invert().transpose());
    }

    /** The turning leaf is laid on every frame, so it turns smoothly (the animations move on once a tick). */
    @Override
    public void preRender(PoseStack poseStack, WayfarersCodexItem animatable, software.bernie.geckolib.cache.object.BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        if (capturing) ((CodexModel) getGeoModel()).poseLeaf();
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour) {
        if (capturing) {
            if (bone.getName().equals(CodexModel.LEFT_PAGE)) {
                leftPage.set(poseStack.last().pose());
                gotLeft = true;
            } else if (bone.getName().equals(CodexModel.RIGHT_PAGE)) {
                rightPage.set(poseStack.last().pose());
                gotRight = true;
            }
            for (int i = 0; i < CodexModel.FRAMES.length; i++) {
                if (bone.getName().equals(CodexModel.FRAMES[i])) frames[i].set(poseStack.last().pose());
            }
            if (capturing && bone.getName().equals(CodexModel.LEAF) && bone.getScaleX() > 0.2f) {
                leaf.set(poseStack.last().pose());
                gotLeaf = true;
                super.renderCubesOfBone(poseStack, bone, buffer, light, overlay, colour);
                framesOnLeaf(poseStack, buffer, light, overlay, colour);
                return;
            }
        }
        super.renderCubesOfBone(poseStack, bone, buffer, light, overlay, colour);
    }

    /**
     * The turning leaf carries the pages' gilded frames, as the pages themselves do: the right page's on the face it lifts
     * from the right, the left page's on the face it lays down on the left. The leaf's own space is the right page's as it
     * lifts and the left page's as it lands, so each frame is drawn in it just as on its page (the leaf's paper hides the
     * face turned away). Without them the frames, and the writing inside them, seemed to come only once the leaf landed.
     */
    private void framesOnLeaf(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int colour) {
        CodexModel model = (CodexModel) getGeoModel();
        GeoBone leafBone = model.getAnimationProcessor().getBone(CodexModel.LEAF);
        Matrix4f now = new Matrix4f(poseStack.last().pose());
        // the leaf's parent, and the leaf as it lay on each page
        Matrix4f parent = new Matrix4f(now).mul(local(leafBone, leafBone.getRotX(), leafBone.getRotY(), leafBone.getRotZ(),
                leafBone.getScaleX(), leafBone.getScaleY(), leafBone.getScaleZ()).invert());
        Matrix4f[] lying = {
                new Matrix4f(parent).mul(local(leafBone, model.leafStart.x, model.leafStart.y, model.leafStart.z, 1, 1, 1)),
                new Matrix4f(parent).mul(local(leafBone, model.leafEnd.x, model.leafEnd.y, model.leafEnd.z, 1, 1, 1))};
        for (int i = 0; i < CodexModel.FRAMES.length; i++) {
            GeoBone frame = model.getAnimationProcessor().getBone(CodexModel.FRAMES[i]);
            if (frame == null || frame.isHidden()) continue;
            // the frame as it sits against the leaf lying on its page, carried along by the leaf, just off its paper
            // (the right page's frame on the face towards the reader at the start, the left page's on the other)
            Matrix4f pose = new Matrix4f(now).translate(0, 0, i == 0 ? -FRAME_LIFT : FRAME_LIFT)
                    .mul(new Matrix4f(lying[i]).invert()).mul(frames[i]);
            poseStack.pushPose();
            poseStack.last().pose().set(pose);
            poseStack.last().normal().set(new org.joml.Matrix3f(pose).invert().transpose());
            super.renderCubesOfBone(poseStack, frame, buffer, light, overlay, colour);
            poseStack.popPose();
        }
    }

    /** A part's own placement as GeckoLib makes it: moved, then turned (z, y, x) and sized about its pivot. */
    private static Matrix4f local(GeoBone b, float rx, float ry, float rz, float sx, float sy, float sz) {
        return new Matrix4f().translate(-b.getPosX() / 16f, b.getPosY() / 16f, b.getPosZ() / 16f)
                .translate(b.getPivotX() / 16f, b.getPivotY() / 16f, b.getPivotZ() / 16f)
                .rotateZYX(rz, ry, rx).scale(sx, sy, sz)
                .translate(-b.getPivotX() / 16f, -b.getPivotY() / 16f, -b.getPivotZ() / 16f);
    }
}
