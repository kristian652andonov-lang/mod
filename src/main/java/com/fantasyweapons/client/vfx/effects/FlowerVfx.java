package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

/**
 * A giant custom flower: two rings of petals that unfurl from a closed bud to a wide-open bloom, a glowing pistil and
 * drifting pollen; at the end the petals snap shut and sink.
 */
public class FlowerVfx extends Vfx {
    private final Vec3 center;
    private final float radius;
    private final int petalColor;
    private final int tipColor;
    private final int coreColor;
    private int openTicks = 20;
    private int closeTicks = 8;

    public FlowerVfx(Vec3 center, float radius, int petalColor, int tipColor, int coreColor, int lifetime) {
        super(lifetime);
        this.center = center;
        this.radius = radius;
        this.petalColor = petalColor;
        this.tipColor = tipColor;
        this.coreColor = coreColor;
    }

    public FlowerVfx timing(int open, int close) {
        this.openTicks = Math.max(1, open);
        this.closeTicks = Math.max(1, close);
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        float open = easeOut(Math.min(1, time / openTicks));
        float close = clamp01((time - (lifetime - closeTicks)) / closeTicks);
        float bloom = open * (1 - easeIn(close));
        float alpha = 1 - close * 0.6f;
        var petal = ctx.translucent(VfxTextures.PETAL);
        for (int ring = 0; ring < 2; ring++) {
            int n = ring == 0 ? 10 : 8;
            float len = radius * (ring == 0 ? 1f : 0.7f);
            float lift = ring == 0 ? 0.05f : 0.25f;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n + ring * 0.3 + time * 0.004;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                // closed: petals point up; open: they lie out flat, curling slightly at the tips
                double tilt = (1 - bloom) * Math.PI * 0.48 + 0.08 + ring * 0.2;
                Vec3 dir = out.scale(Math.cos(tilt)).add(0, Math.sin(tilt), 0);
                Vec3 side = new Vec3(-out.z, 0, out.x);
                Vec3 base = center.add(0, lift, 0);
                Vec3 mid = base.add(dir.scale(len * 0.5));
                int c0 = Colors.alpha(alpha, petalColor), c1 = Colors.alpha(alpha, tipColor);
                Vec3 hx = side.scale(len * 0.22), hy = dir.scale(len * 0.5);
                ctx.quad(petal, mid.subtract(hx).subtract(hy), mid.add(hx).subtract(hy), mid.add(hx).add(hy), mid.subtract(hx).add(hy), 0, 0, 1, 1,
                        c0, c0, c1, c1);
            }
        }
        ctx.billboard(ctx.additive(VfxTextures.GLOW), center.add(0, 0.6, 0), radius * 0.9f * bloom + 0.3f, 0, Colors.alpha(alpha * 0.8f, coreColor));
        ctx.billboard(ctx.additive(VfxTextures.STAR), center.add(0, 0.6, 0), radius * 0.35f * bloom + 0.2f, time * 0.03f, Colors.alpha(alpha, 0xFFFFFF));
        // pollen motes rising from the heart
        var mote = ctx.additive(VfxTextures.GLOW);
        for (int i = 0; i < 14; i++) {
            float cyc = (time * 0.02f + i / 14f) % 1f;
            double a = i * 2.4 + time * 0.03;
            Vec3 p = center.add(Math.cos(a) * radius * 0.3 * cyc, 0.5 + cyc * radius * 0.6, Math.sin(a) * radius * 0.3 * cyc);
            ctx.billboard(mote, p, 0.25f, 0, Colors.alpha(alpha * bloom * (1 - cyc), coreColor));
        }
    }
}
