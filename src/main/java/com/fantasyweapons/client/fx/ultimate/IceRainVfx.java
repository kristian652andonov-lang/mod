package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * ICE RAIN. A storm cloud gathers over the caster, dark and churning, lit from within; sleet streams down, and out of it
 * fall spikes of ice as big as houses, trailing frost, each bursting into shards and freezing mist where it strikes.
 * The spikes themselves stay, driven into the ground (the server builds them).
 */
public class IceRainVfx extends Vfx {
    private static final int CLOUD = 0x2A3440, CLOUD_LIT = 0x8FB8D8, ICE = 0xBFEFFF, CORE = 0xEAF9FF, FROST = 0xDDF4FF;
    private static final int FALL = 16, GATHER = 30;
    private static final float CLOUD_HEIGHT = 85;

    private final Vec3 center;
    private final float radius;
    private final int duration;
    private final List<Vec3> strikes;
    private final int[] times;
    private final float[][] tilt;

    public IceRainVfx(Vec3 center, float radius, int duration, List<Vec3> strikes, int[] times, long seed) {
        super(duration + 40);
        this.center = center;
        this.radius = radius;
        this.duration = duration;
        this.strikes = strikes;
        this.times = times;
        tilt = new float[strikes.size()][3];
        for (int i = 0; i < strikes.size(); i++) {
            RandomSource r = RandomSource.create(seed + i * 31L);
            double length = 12 + r.nextDouble() * 14, base = 2.2 + r.nextDouble() * 1.6;
            r.nextDouble();
            tilt[i][0] = (float) ((r.nextDouble() - 0.5) * 0.5);
            tilt[i][1] = (float) ((r.nextDouble() - 0.5) * 0.5);
            tilt[i][2] = (float) (length * 100 + base);
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float cloud = clamp01(t / GATHER) * (1 - clamp01((t - duration) / 40f));
        if (cloud <= 0) return;
        if (ctx.cam.distanceTo(center) < radius * 2) com.fantasyweapons.client.fx.SkyDarkness.request(0.65f * cloud);
        ctx.farClamp = VfxContext.viewReach() * 0.86;
        drawCloud(ctx, t, cloud);
        drawSleet(ctx, t, cloud);
        var ice = ctx.translucent(VfxTextures.ICE);
        var glow = ctx.additive(VfxTextures.GLOW);
        var streak = ctx.additive(VfxTextures.STREAK);
        var mist = ctx.translucent(VfxTextures.MIST);
        var spark = ctx.additive(VfxTextures.SPARK);
        for (int i = 0; i < strikes.size(); i++) {
            float k = (t - (times[i] - FALL)) / FALL;
            if (k < 0) continue;
            Vec3 hit = strikes.get(i);
            Vec3 dir = new Vec3(tilt[i][0], -1, tilt[i][1]).normalize();
            float length = (int) tilt[i][2] / 100f, base = tilt[i][2] % 100;
            if (k <= 1) {
                // falling out of the cloud, faster and faster
                Vec3 from = hit.subtract(dir.scale(CLOUD_HEIGHT));
                Vec3 tipPos = from.lerp(hit, k * k);
                crystal(ctx, ice, glow, tipPos, dir, length, base);
                ctx.streak(streak, tipPos.subtract(dir.scale(length + 8)), dir, length * 1.6f, base * 1.4f, Colors.alpha(0.6f, FROST));
            } else {
                // the burst where it struck
                float b = (t - times[i]) / 14f;
                if (b > 1) continue;
                ctx.billboard(mist, hit.add(0, 2, 0), 10 + 18 * easeOut(b), i, Colors.alpha((1 - b) * 0.75f, FROST));
                ctx.billboard(glow, hit.add(0, 1, 0), 14 * (1 - b * 0.5f), 0, Colors.alpha((1 - b) * 0.7f, ICE));
                for (int s = 0; s < 8; s++) {
                    double a = s * Math.PI / 4 + i;
                    Vec3 out = new Vec3(Math.cos(a), 0.6 + (s % 3) * 0.3, Math.sin(a));
                    ctx.stretched(spark, hit.add(out.scale(easeOut(b) * 12)), out, 3, 0.5f, Colors.alpha(1 - b, CORE));
                }
            }
        }
        ctx.farClamp = 0;
    }

    /** A spike of ice: a long six-sided point with a glow down its heart. The tip leads. */
    private static void crystal(VfxContext ctx, VertexConsumer ice, VertexConsumer glow, Vec3 tip, Vec3 dir, float length, float base) {
        Vec3[] b = VfxContext.basis(dir);
        Vec3 heel = tip.subtract(dir.scale(length));
        Vec3 belly = tip.subtract(dir.scale(length * 0.25));
        int sides = 6;
        Vec3[] ring = new Vec3[sides];
        for (int s = 0; s < sides; s++) {
            double a = s * Math.PI * 2 / sides;
            ring[s] = belly.add(b[0].scale(Math.cos(a) * base)).add(b[1].scale(Math.sin(a) * base));
        }
        for (int s = 0; s < sides; s++) {
            Vec3 p = ring[s], q = ring[(s + 1) % sides];
            int lit = Colors.alpha(0.82f, Colors.scale(ICE, 0.75f + 0.25f * (s % 3)));
            ctx.quad(ice, tip, tip, q, p, 0, 0, 1, 1, lit, lit, lit, lit);
            ctx.quad(ice, p, q, heel, heel, 0, 0, 1, 1, lit, lit, lit, lit);
        }
        ctx.softBeam(glow, heel, tip, base * 1.6f, Colors.alpha(0.6f, CORE), 0, 1, 0.2f, 0.2f);
    }

    private void drawCloud(VfxContext ctx, float t, float a) {
        var mist = ctx.translucent(VfxTextures.MIST);
        var glow = ctx.additive(VfxTextures.GLOW);
        int n = ctx.segments(90);
        float flash = (float) Math.max(0, Math.sin(t * 0.7) * Math.sin(t * 0.23)) > 0.92 ? 1 : 0;
        for (int i = 0; i < n; i++) {
            double ang = i * 2.39996 + t * 0.003;
            float rr = radius * 1.1f * (float) Math.sqrt(((i * 0.618f) % 1f));
            Vec3 p = center.add(Math.cos(ang) * rr, CLOUD_HEIGHT + (i % 5) * 3, Math.sin(ang) * rr);
            ctx.billboard(mist, p, 34 + (i % 4) * 10, i * 0.4f + t * 0.004f, Colors.alpha(a * 0.8f, i % 7 == 0 ? CLOUD_LIT : CLOUD));
            if (flash > 0 && i % 9 == 0) ctx.billboard(glow, p.add(0, -4, 0), 40, 0, Colors.alpha(a * 0.5f, 0xDDEEFF));
        }
    }

    private void drawSleet(VfxContext ctx, float t, float a) {
        var streak = ctx.additive(VfxTextures.STREAK);
        int n = ctx.segments(120);
        Vec3 down = new Vec3(0.15, -1, 0.05);
        for (int i = 0; i < n; i++) {
            double ang = i * 2.39996;
            float rr = radius * (float) Math.sqrt(((i * 0.618f) % 1f));
            float fall = ((i * 0.37f) + t * 0.05f) % 1f;
            Vec3 p = center.add(Math.cos(ang) * rr, CLOUD_HEIGHT * (1 - fall), Math.sin(ang) * rr);
            ctx.streak(streak, p, down, 5, 0.3f, Colors.alpha(a * 0.5f, FROST));
        }
    }
}
