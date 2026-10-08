package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Floating damage number drawn in world space with the font renderer (not a particle). */
public class DamageNumberVfx extends Vfx {
    private final Vec3 pos;
    private final String text;
    private final int color;
    private final float scale;
    private final double driftX;
    private final double driftZ;

    public DamageNumberVfx(Vec3 pos, String text, int color, float scale, long seed) {
        super(26);
        this.pos = pos;
        this.text = text;
        this.color = color;
        this.scale = scale;
        java.util.Random r = new java.util.Random(seed);
        this.driftX = (r.nextDouble() - 0.5) * 0.6;
        this.driftZ = (r.nextDouble() - 0.5) * 0.6;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float rise = easeOut(t) * 0.9f;
        Vec3 p = pos.add(driftX * t, rise, driftZ * t);
        float pop = t < 0.12f ? 0.6f + 3.3f * t : 1f;
        float alpha = t > 0.65f ? 1 - (t - 0.65f) / 0.35f : 1f;
        if (alpha <= 0.02f) return;
        double dist = p.distanceTo(ctx.cam);
        float s = 0.022f * scale * pop * (float) Math.max(1.0, dist / 9.0);

        PoseStack pose = new PoseStack();
        pose.translate(p.x - ctx.cam.x, p.y - ctx.cam.y, p.z - ctx.cam.z);
        Quaternionf rot = mc.gameRenderer.getMainCamera().rotation();
        pose.mulPose(rot);
        pose.scale(-s, -s, s);
        Matrix4f m = pose.last().pose();
        int a = Math.max(4, Math.round(alpha * 255));
        float w = font.width(text);
        int argb = (a << 24) | (color & 0xFFFFFF);
        int outline = (a << 24);
        var buffers = mc.renderBuffers().bufferSource();
        font.drawInBatch8xOutline(net.minecraft.network.chat.Component.literal(text).getVisualOrderText(), -w / 2f, 0, argb, outline, m,
                buffers, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
    }
}
