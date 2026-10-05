package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Snowflakes drifting down inside a circle, swaying and turning, fading in at the top and out at the ground. */
public class SnowfallVfx extends Vfx {
    private final Vec3 ground;
    private final float height;
    private final int color;
    private final int count;
    private final float[] x, z, phase, speed, size, offset;

    public SnowfallVfx(Vec3 ground, float radius, float height, int count, int color, int lifetime, long seed) {
        super(lifetime);
        this.ground = ground;
        this.height = height;
        this.color = color;
        this.count = count;
        x = new float[count];
        z = new float[count];
        phase = new float[count];
        speed = new float[count];
        size = new float[count];
        offset = new float[count];
        RandomSource r = RandomSource.create(seed);
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius;
            x[i] = (float) (Math.cos(a) * d);
            z[i] = (float) (Math.sin(a) * d);
            phase[i] = r.nextFloat() * 6.28f;
            speed[i] = 0.035f + r.nextFloat() * 0.04f;
            size[i] = 0.18f + r.nextFloat() * 0.3f;
            offset[i] = r.nextFloat();
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        float k = Math.min(1f, time / 15f);
        var vc = ctx.additive(VfxTextures.SNOWFLAKE);
        for (int i = 0; i < count; i++) {
            float f = (offset[i] + time * speed[i] / height * 1.0f) % 1f; // 0 at the top .. 1 at the ground
            float y = height * (1 - f);
            float a = k * Math.min(1f, f * 6f) * Math.min(1f, (1 - f) * 8f);
            if (a <= 0.02f) continue;
            float sway = (float) Math.sin(time * 0.05f + phase[i]) * 0.45f;
            Vec3 p = ground.add(x[i] + sway, y, z[i] + (float) Math.cos(time * 0.04f + phase[i]) * 0.3f);
            ctx.billboard(vc, p, size[i], time * 0.03f + phase[i], Colors.alpha(0.9f * a, color));
        }
    }
}
