package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * A celestial body: an energy sphere with a white-hot (or black) core, a flickering glow and rotating corona rays.
 * Grows in, can move along a path (e.g. falling at the end) and collapses out.
 */
public class SunVfx extends Vfx {
    private Function<Float, Vec3> path;
    private final float radius;
    private final int color;
    private final int coreColor;
    private boolean darkCore;
    private int growTicks = 20;
    private int rays = 14;

    public SunVfx(Vec3 pos, float radius, int color, int coreColor, int lifetime) {
        super(lifetime);
        this.path = t -> pos;
        this.radius = radius;
        this.color = color;
        this.coreColor = coreColor;
    }

    public SunVfx path(Function<Float, Vec3> fn) {
        this.path = fn;
        return this;
    }

    public SunVfx darkCore() {
        this.darkCore = true;
        return this;
    }

    public SunVfx grow(int ticks) {
        this.growTicks = Math.max(1, ticks);
        return this;
    }

    public SunVfx rays(int n) {
        this.rays = n;
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        float g = easeOut(Math.min(1, time / growTicks));
        float out = clamp01((lifetime - time) / 4f);
        float r = radius * g * (0.96f + 0.04f * (float) Math.sin(time * 0.7f));
        if (r <= 0.01f || out <= 0) return;
        Vec3 p = path.apply(time);
        float a = out;
        // corona rays in the camera plane
        var rayVc = ctx.additive(VfxTextures.FLAME);
        for (int i = 0; i < rays; i++) {
            double ang = i * Math.PI * 2 / rays + time * 0.012 * (i % 2 == 0 ? 1 : -1);
            Vec3 dir = ctx.camLeft.scale(Math.cos(ang)).add(ctx.camUp.scale(Math.sin(ang)));
            float len = r * (1.1f + 0.45f * (float) Math.sin(time * 0.3f + i * 1.7f));
            ctx.stretched(rayVc, p.add(dir.scale(r * 0.95 + len * 0.5)), dir, len, r * 0.45f, Colors.alpha(a * 0.6f, color));
        }
        ctx.billboard(ctx.additive(VfxTextures.GLOW), p, r * 5.5f, 0, Colors.alpha(a * 0.45f, color));
        int lat = ctx.segments(12), lon = ctx.segments(18);
        if (darkCore) {
            ctx.sphere(ctx.voidInterior(VfxTextures.WHITE), p, r * 0.92f, lat, lon, Colors.alpha(a * 0.95f, 0x050208), false);
            ctx.sphere(ctx.energy(VfxTextures.WHITE), p, r, lat, lon, Colors.alpha(a, color), true);
        } else {
            ctx.sphere(ctx.energy(VfxTextures.NOISE), p, r, lat, lon, Colors.alpha(a, color), true);
            ctx.billboard(ctx.additive(VfxTextures.FLASH), p, r * 2.2f, time * 0.02f, Colors.alpha(a, coreColor));
        }
        ctx.ring(ctx.additive(VfxTextures.RING), p, ctx.camLeft, ctx.camUp, r * 1.05f, r * 1.5f, 32, Colors.alpha(a * 0.5f, coreColor));
    }
}
