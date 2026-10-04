package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * A churning thundercloud: layered dark mist puffs swirling around a centre that can follow the caster, lit from
 * inside by random lightning flashes. Gathers in, then disperses.
 */
public class StormCloudVfx extends Vfx {
    private final Supplier<Vec3> center;
    private final float radius;
    private final int color;
    private final int flashColor;
    private final int puffs;
    private final float[] ang, dist, height, size, phase;

    public StormCloudVfx(Supplier<Vec3> center, float radius, int color, int flashColor, int lifetime, long seed) {
        super(lifetime);
        this.center = center;
        this.radius = radius;
        this.color = color;
        this.flashColor = flashColor;
        this.puffs = Math.round(18 + radius * 3);
        RandomSource r = RandomSource.create(seed);
        ang = new float[puffs];
        dist = new float[puffs];
        height = new float[puffs];
        size = new float[puffs];
        phase = new float[puffs];
        for (int i = 0; i < puffs; i++) {
            ang[i] = r.nextFloat() * 6.283f;
            dist[i] = (float) Math.sqrt(r.nextFloat()) * radius;
            height[i] = (r.nextFloat() - 0.5f) * 1.6f;
            size[i] = 2.5f + r.nextFloat() * 3f;
            phase[i] = r.nextFloat() * 100;
        }
    }

    @Override
    public void render(VfxContext ctx) {
        Vec3 c = center.get();
        if (c == null) return;
        float time = age + ctx.partial;
        float in = easeOut(Math.min(1, time / 15f));
        float out = clamp01((lifetime - time) / 15f);
        float a = in * out;
        if (a <= 0.01f) return;
        int n = Math.round(puffs * Math.min(1f, ctx.density));
        var mist = ctx.translucent(VfxTextures.MIST);
        for (int i = 0; i < n; i++) {
            double an = ang[i] + time * 0.01 * (1.2 - dist[i] / radius);
            Vec3 p = c.add(Math.cos(an) * dist[i] * in, height[i], Math.sin(an) * dist[i] * in);
            float shade = 0.6f + 0.4f * (float) Math.sin(phase[i] + time * 0.05f);
            ctx.billboard(mist, p, size[i] * (0.6f + 0.4f * in), phase[i] + time * 0.004f, Colors.alpha(a * 0.85f, Colors.scale(color, shade)));
        }
        // internal lightning flashes
        var glow = ctx.additive(VfxTextures.GLOW);
        for (int i = 0; i < 4; i++) {
            float f = (float) Math.sin(time * (0.7 + i * 0.37) + phase[i]);
            if (f < 0.85f) continue;
            double an = ang[i * 3 % puffs] + time * 0.02;
            Vec3 p = c.add(Math.cos(an) * dist[i * 5 % puffs], -0.3, Math.sin(an) * dist[i * 5 % puffs]);
            ctx.billboard(glow, p, radius * 0.5f, 0, Colors.alpha(a * (f - 0.85f) * 5, flashColor));
        }
    }
}
