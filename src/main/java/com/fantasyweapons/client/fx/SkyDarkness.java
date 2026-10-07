package com.fantasyweapons.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * Day turning to night under an eclipse: while an effect asks for it ({@link #request}), the sky is veiled in dusk
 * right after it is drawn - terrain, entities and effects are drawn over the veil untouched, so only the sky darkens -
 * and the fog dims with it so the horizon fades into twilight.
 */
public final class SkyDarkness {
    private static final int COLOR = 0x0B0818;
    private static float requested, current;

    private SkyDarkness() {
    }

    /** Asks for the sky to be this dark (0..1) this frame; the strongest request wins. */
    public static void request(float darkness) {
        requested = Math.max(requested, Mth.clamp(darkness, 0, 1));
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        // ease towards the strongest request of the previous frame so the dusk never pops
        current += (requested - current) * 0.2f;
        requested = 0;
        if (current < 0.01f) return;
        Matrix4f proj = new Matrix4f(event.getProjectionMatrix());
        Matrix4f inv = new Matrix4f(proj).invert();
        float a = 0.78f * current;
        int r = (COLOR >> 16) & 255, g = (COLOR >> 8) & 255, b = COLOR & 255, al = Math.round(255 * a);
        // a full-screen quad at the far plane, unprojected so it covers the view whatever the projection
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(proj, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        var mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] c : corners) {
            org.joml.Vector4f v = inv.transform(new org.joml.Vector4f(c[0], c[1], 0.999f, 1));
            buf.addVertex(v.x / v.w, v.y / v.w, v.z / v.w).setColor(r, g, b, al);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (current < 0.01f) return;
        float k = 1 - 0.72f * current;
        event.setRed(event.getRed() * k + 0.04f * current);
        event.setGreen(event.getGreen() * k + 0.03f * current);
        event.setBlue(event.getBlue() * k + 0.09f * current);
    }
}
