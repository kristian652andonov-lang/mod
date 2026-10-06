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
 * inside by random lightning flashes. Gathers in, then disperses. A {@link #dense} storm is a whole thunderhead:
 * a thick, dark underbelly, billowing tops, ragged scud racing beneath it and tendrils hanging down towards the
 * ground.
 */
public class StormCloudVfx extends Vfx {
    private final Supplier<Vec3> center;
    private final float radius;
    private final int color;
    private final int flashColor;
    private final long seed;
    private int puffs;
    private float[] ang, dist, height, size, phase, speed, shade;
    private int flashes = 4;
    private float spinDir = 1;

    public StormCloudVfx(Supplier<Vec3> center, float radius, int color, int flashColor, int lifetime, long seed) {
        super(lifetime);
        this.center = center;
        this.radius = radius;
        this.color = color;
        this.flashColor = flashColor;
        this.seed = seed;
        build(Math.round(18 + radius * 3), false);
    }

    /** A full thunderhead with {@code k} times the cloud (layers, scud and tendrils). */
    public StormCloudVfx dense(float k) {
        build(Math.round((18 + radius * 3) * k), true);
        flashes = 8;
        return this;
    }

    /** Turns the other way round (for a second cloud bank). */
    public StormCloudVfx reverse() {
        spinDir = -1;
        return this;
    }

    private void build(int count, boolean layered) {
        RandomSource r = RandomSource.create(seed);
        puffs = count;
        ang = new float[count];
        dist = new float[count];
        height = new float[count];
        size = new float[count];
        phase = new float[count];
        speed = new float[count];
        shade = new float[count];
        for (int i = 0; i < count; i++) {
            ang[i] = r.nextFloat() * 6.283f;
            phase[i] = r.nextFloat() * 100;
            speed[i] = 0.7f + r.nextFloat() * 0.6f;
            if (!layered) {
                dist[i] = (float) Math.sqrt(r.nextFloat()) * radius;
                height[i] = (r.nextFloat() - 0.5f) * 1.6f;
                size[i] = 2.5f + r.nextFloat() * 3f;
                shade[i] = 1f;
                continue;
            }
            float kind = r.nextFloat();
            if (kind < 0.45f) {
                // the dark, heavy underbelly
                dist[i] = (float) Math.sqrt(r.nextFloat()) * radius * 1.1f;
                height[i] = -0.6f + r.nextFloat() * 1.2f;
                size[i] = 3.5f + r.nextFloat() * 3.5f;
                shade[i] = 0.55f + r.nextFloat() * 0.2f;
            } else if (kind < 0.75f) {
                // billowing tops, paler where the light catches them
                dist[i] = (float) Math.sqrt(r.nextFloat()) * radius * 0.85f;
                height[i] = 1.2f + r.nextFloat() * 2.6f;
                size[i] = 3.5f + r.nextFloat() * 4f;
                shade[i] = 0.9f + r.nextFloat() * 0.35f;
            } else if (kind < 0.9f) {
                // ragged scud racing round underneath
                dist[i] = radius * (0.5f + 0.75f * r.nextFloat());
                height[i] = -2.0f - r.nextFloat() * 1.4f;
                size[i] = 2.8f + r.nextFloat() * 2.6f;
                shade[i] = 0.6f + r.nextFloat() * 0.25f;
                speed[i] *= 2.4f;
            } else {
                // tendrils hanging down towards the ground
                dist[i] = (float) Math.sqrt(r.nextFloat()) * radius * 0.8f;
                height[i] = -3.2f - r.nextFloat() * 3f;
                size[i] = 2.2f + r.nextFloat() * 1.8f;
                shade[i] = 0.5f + r.nextFloat() * 0.2f;
            }
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
            double an = ang[i] + spinDir * time * 0.01 * speed[i] * (1.2 - Math.min(1, dist[i] / radius));
            float bob = 0.25f * (float) Math.sin(phase[i] + time * 0.03f);
            Vec3 p = c.add(Math.cos(an) * dist[i] * in, height[i] * (0.4f + 0.6f * in) + bob, Math.sin(an) * dist[i] * in);
            float tone = shade[i] * (0.75f + 0.25f * (float) Math.sin(phase[i] + time * 0.05f));
            float pa = height[i] < -3 ? 0.4f : height[i] < -1.5f ? 0.6f : 0.85f;
            ctx.billboard(mist, p, size[i] * (0.6f + 0.4f * in), phase[i] + time * 0.004f * speed[i], Colors.alpha(a * pa, Colors.scale(color, tone)));
        }
        // internal lightning flashes
        var glow = ctx.additive(VfxTextures.GLOW);
        for (int i = 0; i < flashes; i++) {
            float f = (float) Math.sin(time * (0.7 + i * 0.37) + phase[i]);
            if (f < 0.85f) continue;
            double an = ang[i * 3 % puffs] + time * 0.02;
            float d = dist[i * 5 % puffs];
            Vec3 p = c.add(Math.cos(an) * d, -0.3 + (i % 3) * 0.8, Math.sin(an) * d);
            ctx.billboard(glow, p, radius * 0.5f, 0, Colors.alpha(a * (f - 0.85f) * 5, flashColor));
        }
    }
}
