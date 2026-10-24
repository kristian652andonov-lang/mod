package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

/**
 * A soft column of light rising from the ground: it shoots up to its full height, then thins and fades. It has no
 * hard ends - brightest a little above the ground, it melts into the ground at its foot and dissolves into the air
 * long before its tip, so it never looks cut off.
 */
public class LightColumnVfx extends Vfx {
    private static final int POINTS = 14;
    private final Vec3 foot;
    private final float height;
    private final float width;
    private final int color;

    public LightColumnVfx(Vec3 foot, float height, float width, int color, int lifetime) {
        super(lifetime);
        this.foot = foot;
        this.height = height;
        this.width = width;
        this.color = color;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        float rise = easeOut(Math.min(1, t / 0.3f));
        float fade = t < 0.35f ? 1 : 1 - easeIn((t - 0.35f) / 0.65f);
        float base = ((color >>> 24) & 255) / 255f * fade;
        if (base <= 0.01f) return;
        float h = height * (0.35f + 0.65f * rise);
        float w = width * (t < 0.12f ? 0.4f + 0.6f * t / 0.12f : 1 - 0.55f * easeIn((t - 0.12f) / 0.88f));

        Vec3[] pts = new Vec3[POINTS];
        float[] glowW = new float[POINTS], coreW = new float[POINTS];
        int[] glowC = new int[POINTS], coreC = new int[POINTS];
        int core = Colors.brighten(color, 0.6f);
        for (int i = 0; i < POINTS; i++) {
            float s = i / (float) (POINTS - 1);
            pts[i] = foot.add(0, h * s, 0);
            // melts into the ground at the foot, strongest just above it, gone well before the tip
            float a = smoothstep(0, 0.08f, s) * (float) Math.pow(1 - s, 1.7);
            float taper = 1 - 0.3f * s;
            glowW[i] = w * 3.0f * taper;
            coreW[i] = w * 0.8f * taper;
            glowC[i] = Colors.alpha(base * 0.6f * a, color);
            coreC[i] = Colors.alpha(base * a, core);
        }
        float scroll = -(age + ctx.partial) * 0.12f;
        ctx.ribbon(ctx.energy(VfxTextures.STREAK), pts, glowW, glowC, scroll, 0.6f / (POINTS - 1));
        ctx.ribbon(ctx.additive(VfxTextures.STREAK), pts, coreW, coreC, scroll * 1.6f, 0.9f / (POINTS - 1));
    }

    private static float smoothstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }
}
