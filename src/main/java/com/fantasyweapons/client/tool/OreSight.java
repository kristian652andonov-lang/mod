package com.fantasyweapons.client.tool;

import com.fantasyweapons.client.render.FWRenderTypes;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.tool.OreSightPayload;
import com.fantasyweapons.tool.ToolAbilities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * A pickaxe's Ore Sight on the client: a wave of light runs out from where it was used, and every legendary ore it passes
 * lights up through the stone in its material's colour - a glowing outline round a faint glow - for ten seconds, pulsing
 * gently, then fades.
 */
public final class OreSight {
    /** Ticks the wave takes to reach the edge of the sight. */
    private static final float WAVE = 24;
    private static long[] positions;
    private static byte[] materials;
    private static float[] distances;
    private static Vec3 center;
    private static long start;
    private static int ticks;

    private OreSight() {
    }

    public static void show(OreSightPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        positions = p.positions();
        materials = p.materials();
        center = Vec3.atCenterOf(BlockPos.of(p.center()));
        distances = new float[positions.length];
        for (int i = 0; i < positions.length; i++) distances[i] = (float) Math.sqrt(BlockPos.of(positions[i]).distToCenterSqr(center));
        start = mc.level.getGameTime();
        ticks = p.ticks();
    }

    public static void clear() {
        positions = null;
    }

    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || positions == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            positions = null;
            return;
        }
        float age = mc.level.getGameTime() - start + e.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (age > ticks || age < -20) {
            positions = null;
            return;
        }
        float reach = ToolAbilities.SIGHT_RADIUS + 1;
        float wave = Math.min(1f, age / WAVE) * reach;
        float fade = Math.min(1f, Math.max(0f, (ticks - age) / 30f));
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = new PoseStack();
        Matrix4f m = pose.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // seen through everything: the render types' "always" depth test leaves the depth test as it finds it, and here
        // it is on
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        VertexConsumer fill = buffers.getBuffer(FWRenderTypes.XRAY_FILL);
        for (int i = 0; i < positions.length; i++) {
            float appear = appear(i, wave);
            if (appear <= 0) continue;
            int c = color(i);
            float pulse = 0.7f + 0.3f * (float) Math.sin(age * 0.2f + distances[i] * 0.35f);
            box(fill, m, box(i, cam), c, 0.28f * appear * fade * pulse);
        }
        buffers.endBatch(FWRenderTypes.XRAY_FILL);

        VertexConsumer lines = buffers.getBuffer(FWRenderTypes.XRAY_LINES);
        for (int i = 0; i < positions.length; i++) {
            float appear = appear(i, wave);
            if (appear <= 0) continue;
            int c = color(i);
            // a flash as the wave reaches it, settling to a steady glow
            float flash = 1f + 0.8f * Math.max(0f, 1f - (wave - distances[i]) / 4f);
            float r = Math.min(1f, ((c >> 16) & 255) / 255f * flash), g = Math.min(1f, ((c >> 8) & 255) / 255f * flash), b = Math.min(1f, (c & 255) / 255f * flash);
            LevelRenderer.renderLineBox(pose, lines, box(i, cam), r, g, b, 0.95f * appear * fade);
        }
        // the wave itself, a ring of light spreading out at the height it was used
        if (age < WAVE + 6) {
            float a = 0.8f * Math.min(1f, (WAVE + 6 - age) / 8f);
            int segments = 96;
            for (int s = 0; s < segments; s++) {
                double a0 = s * Math.PI * 2 / segments, a1 = (s + 1) * Math.PI * 2 / segments;
                float x0 = (float) (center.x + Math.cos(a0) * wave - cam.x), z0 = (float) (center.z + Math.sin(a0) * wave - cam.z);
                float x1 = (float) (center.x + Math.cos(a1) * wave - cam.x), z1 = (float) (center.z + Math.sin(a1) * wave - cam.z);
                float y = (float) (center.y - 0.4 - cam.y);
                float nx = x1 - x0, nz = z1 - z0, len = (float) Math.sqrt(nx * nx + nz * nz);
                lines.addVertex(m, x0, y, z0).setColor(0.85f, 0.75f, 1f, a).setNormal(nx / len, 0, nz / len);
                lines.addVertex(m, x1, y, z1).setColor(0.85f, 0.75f, 1f, a).setNormal(nx / len, 0, nz / len);
            }
        }
        buffers.endBatch(FWRenderTypes.XRAY_LINES);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    /** How far ore {@code i} has lit up as the wave passed it (0 before, 1 a few blocks after). */
    private static float appear(int i, float wave) {
        return Math.min(1f, Math.max(0f, (wave - distances[i]) / 2.5f));
    }

    private static int color(int i) {
        int m = materials[i];
        return m >= 0 && m < Material.values().length ? Material.values()[m].color() : 0xFFFFFF;
    }

    private static AABB box(int i, Vec3 cam) {
        BlockPos p = BlockPos.of(positions[i]);
        return new AABB(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z, p.getX() + 1 - cam.x, p.getY() + 1 - cam.y, p.getZ() + 1 - cam.z).inflate(0.01);
    }

    /** The six faces of {@code b} as flat glowing quads. */
    private static void box(VertexConsumer v, Matrix4f m, AABB b, int c, float alpha) {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, bl = (c & 255) / 255f;
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        quad(v, m, r, g, bl, alpha, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(v, m, r, g, bl, alpha, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);
        quad(v, m, r, g, bl, alpha, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
        quad(v, m, r, g, bl, alpha, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        quad(v, m, r, g, bl, alpha, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0);
        quad(v, m, r, g, bl, alpha, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
    }

    private static void quad(VertexConsumer v, Matrix4f m, float r, float g, float b, float a, float... p) {
        for (int k = 0; k < 12; k += 3) v.addVertex(m, p[k], p[k + 1], p[k + 2]).setColor(r, g, b, a);
    }
}
