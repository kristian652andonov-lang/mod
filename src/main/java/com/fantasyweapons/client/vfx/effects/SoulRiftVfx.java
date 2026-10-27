package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

/**
 * A small rift torn open in the ground, the place a spectral chain comes out of: it splits open with a flash, shows a
 * dark, bottomless hole ringed by a glowing rim and a turning circle of runes, breathes wisps of soul light up out of
 * itself while it is open, and seals shut again at the end.
 */
public class SoulRiftVfx extends Vfx {
    private static final Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
    private final Vec3 center;
    private final float radius;
    private final int color;
    private final int light;
    private final float spin;

    public SoulRiftVfx(Vec3 center, float radius, int color, int light, int lifetime) {
        super(lifetime);
        this.center = center.add(0, 0.05, 0);
        this.radius = radius;
        this.color = color;
        this.light = light;
        this.spin = (float) ((center.x * 3.7 + center.z * 1.3) % 6.283);
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        float open = easeOut(Math.min(1, time / 5f));
        float close = clamp01((lifetime - time) / 8f);
        float k = open * easeOut(close);
        if (k <= 0.01f) return;
        float r = radius * k;

        // the hole itself: dark and deep, its edge eaten away
        ctx.disc(ctx.translucent(VfxTextures.GLOW), center, X, Z, r * 1.3f, spin + time * 0.01f, Colors.alpha(0.95f * k, 0x05010A));
        // glowing rim and the light it throws on the ground around it
        ctx.disc(ctx.additive(VfxTextures.GLOW), center.add(0, 0.01, 0), X, Z, r * 2.6f, 0, Colors.alpha(0.35f * k, color));
        ctx.ring(ctx.additive(VfxTextures.RING), center.add(0, 0.02, 0), X, Z, r * 0.72f, r * 1.05f, ctx.segments(32), Colors.alpha(k, light));
        ctx.ring(ctx.additive(VfxTextures.RING), center.add(0, 0.025, 0), X, Z, r * 0.85f, r * 0.95f, ctx.segments(32), Colors.alpha(k, 0xFFFFFF));
        // light welling up out of it where the chain comes through
        var col = ctx.additive(VfxTextures.GLOW);
        ctx.billboard(col, center.add(0, 0.25, 0), r * 1.8f, 0, Colors.alpha(0.45f * k, light));
        // a circle of runes turning around it
        ctx.disc(ctx.energy(VfxTextures.RUNE_CIRCLE), center.add(0, 0.03, 0), X, Z, r * 1.8f, spin - time * 0.04f, Colors.alpha(0.75f * k, color));
        // the flash of it tearing open
        if (time < 6) {
            float f = 1 - time / 6f;
            ctx.billboard(ctx.additive(VfxTextures.FLASH), center.add(0, 0.3, 0), radius * 3f * (1 - f * 0.4f), spin, Colors.alpha(f * 0.8f, light));
        }
        // wisps of soul light rising out of it
        var mist = ctx.additive(VfxTextures.MIST);
        for (int i = 0; i < 5; i++) {
            float cyc = (time * 0.04f + i / 5f + spin) % 1f;
            double a = spin + i * 2.4 + time * 0.03;
            Vec3 p = center.add(Math.cos(a) * r * 0.5 * (1 - cyc), cyc * 1.6, Math.sin(a) * r * 0.5 * (1 - cyc));
            ctx.billboard(mist, p, radius * (0.6f + 0.8f * cyc), time * 0.02f + i, Colors.alpha(k * 0.35f * (1 - cyc), color));
        }
    }
}
