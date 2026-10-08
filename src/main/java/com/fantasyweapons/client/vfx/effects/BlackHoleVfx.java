package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A black hole: a lightless core with a thin bright photon ring, a tilted spinning accretion disk shading from white-hot
 * to deep colour, and matter spiralling in. Grows in, then collapses.
 */
public class BlackHoleVfx extends Vfx {
    private final Vec3 center;
    private final float radius;
    private final int hot;
    private final int cold;
    private final float[] ang;
    private final float[] dist;
    private final float[] speed;

    public BlackHoleVfx(Vec3 center, float radius, int hot, int cold, int lifetime, long seed) {
        super(lifetime);
        this.center = center;
        this.radius = radius;
        this.hot = hot;
        this.cold = cold;
        RandomSource r = RandomSource.create(seed);
        ang = new float[40];
        dist = new float[40];
        speed = new float[40];
        for (int i = 0; i < 40; i++) {
            ang[i] = r.nextFloat() * 6.283f;
            dist[i] = r.nextFloat();
            speed[i] = 0.4f + r.nextFloat() * 0.8f;
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        float grow = easeOut(Math.min(1, time / 10f));
        float collapse = clamp01((time - (lifetime - 6)) / 6f);
        float s = radius * grow * (1 - easeIn(collapse) * 0.9f);
        if (s <= 0.02f) return;
        // tilted accretion disk
        Vec3 ax = new Vec3(1, 0.25, 0).normalize();
        Vec3 ay = new Vec3(0, 0.15, 1).normalize();
        float spin = time * 0.12f;
        Vec3 rx = ax.scale(Math.cos(spin)).add(ay.scale(Math.sin(spin)));
        Vec3 ry = ax.scale(-Math.sin(spin)).add(ay.scale(Math.cos(spin)));
        var disk = ctx.energy(VfxTextures.SWIRL);
        ctx.disc(disk, center, rx, ry, s * 3.2f, 0, Colors.alpha(0.8f, cold));
        ctx.ring(ctx.additive(VfxTextures.RING), center, rx, ry, s * 1.1f, s * 2.2f, 40, Colors.alpha(0.9f, hot));
        ctx.ring(ctx.additive(VfxTextures.RING), center, rx, ry, s * 2.0f, s * 3.4f, 40, Colors.alpha(0.5f, cold));
        // spiralling matter
        var mote = ctx.additive(VfxTextures.SPARK);
        for (int i = 0; i < ang.length; i++) {
            float cyc = (time * 0.012f * speed[i] + dist[i]) % 1f;
            float d = s * (3.6f - 3.0f * cyc);
            double a = ang[i] + cyc * 9;
            Vec3 p = center.add(rx.scale(Math.cos(a) * d)).add(ry.scale(Math.sin(a) * d));
            ctx.billboard(mote, p, 0.25f, (float) a, Colors.alpha(Math.min(1, cyc * 3), Colors.lerpRgb(cold, hot, cyc)));
        }
        ctx.billboard(ctx.additive(VfxTextures.GLOW), center, s * 2.2f, 0, Colors.alpha(0.16f, cold));
        // the core: lightless sphere with a photon ring facing the camera
        int lat = ctx.segments(10), lon = ctx.segments(16);
        ctx.sphere(ctx.voidInterior(VfxTextures.WHITE), center, s, lat, lon, Colors.alpha(1f, 0x000000), false);
        ctx.ring(ctx.additive(VfxTextures.RING), center, ctx.camLeft, ctx.camUp, s * 0.98f, s * 1.25f, 36, Colors.alpha(1f, hot));
    }
}
