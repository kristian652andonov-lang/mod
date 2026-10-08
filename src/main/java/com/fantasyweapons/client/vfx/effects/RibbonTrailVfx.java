package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** A camera-facing energy ribbon along a fixed path that fades from tail to head (teleport trails, beams of travel). */
public class RibbonTrailVfx extends Vfx {
    private final Vec3[] pts;
    private final float width;
    private final int colorHead;
    private final int colorTail;
    private ResourceLocation texture = VfxTextures.STREAK;
    private float scroll = 0.15f;

    public RibbonTrailVfx(List<Vec3> path, float width, int colorHead, int colorTail, int lifetime) {
        super(lifetime);
        this.pts = path.toArray(new Vec3[0]);
        this.width = width;
        this.colorHead = colorHead;
        this.colorTail = colorTail;
    }

    public RibbonTrailVfx texture(ResourceLocation tex) {
        this.texture = tex;
        return this;
    }

    public RibbonTrailVfx scroll(float s) {
        this.scroll = s;
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        int n = pts.length;
        float[] widths = new float[n];
        int[] colors = new int[n];
        for (int i = 0; i < n; i++) {
            float along = n == 1 ? 1 : i / (float) (n - 1);
            float fade = clamp01(1 - t * 1.4f + along * 0.4f);
            widths[i] = width * (0.35f + 0.65f * along) * (1 - t * 0.6f);
            int c = Colors.lerp(colorTail, colorHead, along);
            colors[i] = Colors.alpha(fade * ((c >>> 24) & 255) / 255f, c);
        }
        float u0 = -(age + ctx.partial) * scroll;
        ctx.ribbon(ctx.energy(texture), pts, widths, colors, u0, 1f / Math.max(1, n - 1) * 2f);
        float[] cw = new float[n];
        int[] cc = new int[n];
        for (int i = 0; i < n; i++) {
            cw[i] = widths[i] * 0.3f;
            cc[i] = Colors.alpha(((colors[i] >>> 24) & 255) / 255f * 0.9f, 0xFFFFFF);
        }
        ctx.ribbon(ctx.additive(VfxTextures.LIGHTNING), pts, cw, cc, 0, 1f / Math.max(1, n - 1));
    }
}
