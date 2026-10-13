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
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws the Codex. In first person it is animated on its own (opening and held open, while the inventory icon and the
 * book in other hands stay shut); while it is read it is fully lit, and the pages of the open spread are drawn onto it,
 * placed by where its two page parts were just drawn.
 */
public final class CodexRenderer extends GeoItemRenderer<WayfarersCodexItem> {
    /** Animation instances: the reader's first-person book, and every other view of it. */
    public static final long FIRST_PERSON = 1, ELSEWHERE = 2;

    /** Where the book is held for reading, before the eyes: its spine 17 pixels ahead and 1.5 below, tipped up towards the reader. */
    private static final Matrix4f READING = new Matrix4f().translation(0, -1.5f / 16, -17 / 16f)
            .rotateX((float) Math.toRadians(35)).rotateY((float) Math.PI);

    private final Matrix4f leftPage = new Matrix4f(), rightPage = new Matrix4f(), leaf = new Matrix4f();
    private boolean capturing, gotLeft, gotRight, gotLeaf;

    public CodexRenderer() {
        super(new CodexModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
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
        super.renderByItem(stack, context, poseStack, buffers, reading ? LightTexture.FULL_BRIGHT : light, overlay);
        capturing = false;
        if (reading && gotLeft && gotRight) CodexPages.render(leftPage, rightPage, gotLeaf ? leaf : null, buffers);
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

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour) {
        if (capturing) {
            if (bone.getName().equals(CodexModel.LEFT_PAGE)) {
                leftPage.set(poseStack.last().pose());
                gotLeft = true;
            } else if (bone.getName().equals(CodexModel.RIGHT_PAGE)) {
                rightPage.set(poseStack.last().pose());
                gotRight = true;
            } else if (bone.getName().equals(CodexModel.LEAF) && bone.getScaleX() > 0.5f) {
                leaf.set(poseStack.last().pose());
                gotLeaf = true;
            }
        }
        super.renderCubesOfBone(poseStack, bone, buffer, light, overlay, colour);
    }
}
