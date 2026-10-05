package com.fantasyweapons.client.fx.voidfang;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Void Dimension: a translucent dome of void around the battlefield with an energy rim, two counter-rotating rune
 * circles on the ground, vertical rift seams around the boundary and drifting void fragments inside.
 */
public class VoidDomeVfx extends Vfx {
    private static final int SEAMS = 10;
    private static final int MOTES = 60;
    private final Vec3 center;
    private final float radius;
    private final int color;
    private final int light;
    private final float[] seamAngle = new float[SEAMS];
    private final float[] moteA = new float[MOTES], moteR = new float[MOTES], moteY = new float[MOTES], moteS = new float[MOTES];

    public VoidDomeVfx(Vec3 center, float radius, int color, int light, int lifetime, long seed) {
        super(lifetime);
        this.center = center;
        this.radius = radius;
        this.color = color;
        this.light = light;
        RandomSource r = RandomSource.create(seed);
        for (int i = 0; i < SEAMS; i++) seamAngle[i] = (float) (i / (double) SEAMS * Math.PI * 2 + r.nextFloat() * 0.3);
        for (int i = 0; i < MOTES; i++) {
            moteA[i] = r.nextFloat() * 6.283f;
            moteR[i] = (float) Math.sqrt(r.nextFloat()) * radius * 0.95f;
            moteY[i] = r.nextFloat();
            moteS[i] = 0.5f + r.nextFloat();
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        float time = age + ctx.partial;
        float open = easeOut(Math.min(1, time / 12f));
        float close = t > 0.92f ? 1 - (t - 0.92f) / 0.08f : 1f;
        float k = open * close;
        if (k <= 0.01f) return;
        float r = radius * (0.25f + 0.75f * open);
        Vec3 ground = center.subtract(0, 0.95, 0);

        // dome shell
        int lat = ctx.segments(16), lon = ctx.segments(28);
        ctx.sphere(ctx.voidInterior(VfxTextures.WHITE), center, r, lat, lon, Colors.alpha(0.42f * k, color), false);
        ctx.sphere(ctx.energy(VfxTextures.WHITE), center, r * 1.005f, lat, lon, Colors.alpha(0.75f * k, color), true);

        // ground runes
        Vec3 ax = new Vec3(1, 0, 0), az = new Vec3(0, 0, 1);
        ctx.disc(ctx.energy(VfxTextures.RUNE_CIRCLE), ground.add(0, 0.05, 0), ax, az, r * 0.98f, time * 0.02f, Colors.alpha(0.85f * k, color));
        ctx.disc(ctx.energy(VfxTextures.RUNE_CIRCLE), ground.add(0, 0.07, 0), ax, az, r * 0.55f, -time * 0.035f, Colors.alpha(0.8f * k, light));
        ctx.disc(ctx.additive(VfxTextures.GLOW), ground.add(0, 0.06, 0), ax, az, r * 1.1f, 0, Colors.alpha(0.25f * k, color));
        ctx.satellites(ctx.energy(VfxTextures.RUNE_CIRCLE), ground.add(0, 0.08, 0), ax, az, r * 0.77f, r * 0.13f, 4, -time * 0.012f, time * 0.06f,
                Colors.alpha(0.8f * k, light));

        // vertical rift seams on the boundary
        var seam = ctx.energy(VfxTextures.RIFT_EDGE);
        for (int i = 0; i < SEAMS; i++) {
            double a = seamAngle[i] + time * 0.004;
            Vec3 b = ground.add(Math.cos(a) * r * 0.97, 0, Math.sin(a) * r * 0.97);
            float h = r * (0.5f + 0.3f * (float) Math.sin(time * 0.1 + i));
            ctx.beam(seam, b, b.add(0, h, 0), 0.35f, Colors.alpha(0.7f * k, light), -time * 0.05f, -time * 0.05f + 1.5f);
        }

        // drifting fragments
        var mote = ctx.additive(VfxTextures.SHARD);
        int n = Math.round(MOTES * Math.min(1, ctx.density));
        for (int i = 0; i < n; i++) {
            float y = ((moteY[i] + time * 0.004f * moteS[i]) % 1f);
            double a = moteA[i] + time * 0.01 * moteS[i];
            Vec3 p = ground.add(Math.cos(a) * moteR[i], y * r * 0.9, Math.sin(a) * moteR[i]);
            float fadeY = Math.min(1, y * 5) * Math.min(1, (1 - y) * 3);
            ctx.billboard(mote, p, 0.35f * moteS[i], (float) a * 3, Colors.alpha(0.8f * k * fadeY, i % 3 == 0 ? light : color));
        }
    }
}
