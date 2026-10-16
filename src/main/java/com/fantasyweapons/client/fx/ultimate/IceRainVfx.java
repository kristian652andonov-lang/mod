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
    private static final int FALL = com.fantasyweapons.weapons.obliteration.ObliterationAbilities.ICE_FALL,
            GATHER = com.fantasyweapons.weapons.obliteration.ObliterationAbilities.ICE_GATHER;
    private static final float CLOUD_HEIGHT = 85;

    private final Vec3 center;
    private final float radius;
    private final int duration;
    private final List<Vec3> strikes;
    private final int[] times;
    /** Per spike: its lean (x, z), its length and its width at the base (blocks), as the server builds it. */
    private final float[][] shape;

    public IceRainVfx(Vec3 center, float radius, int duration, List<Vec3> strikes, int[] times, long seed) {
        super(duration + 40);
        this.center = center;
        this.radius = radius;
        this.duration = duration;
        this.strikes = strikes;
        this.times = times;
        shape = new float[strikes.size()][4];
        for (int i = 0; i < strikes.size(); i++) {
            // the same draws, in the same order, as the spike the server drives into the ground
            RandomSource r = RandomSource.create(seed + i * 31L);
            double length = 12 + r.nextDouble() * 14, base = 2.2 + r.nextDouble() * 1.6;
            r.nextDouble();
            shape[i][0] = (float) ((r.nextDouble() - 0.5) * 0.5);
            shape[i][1] = (float) ((r.nextDouble() - 0.5) * 0.5);
            shape[i][2] = (float) length;
            shape[i][3] = (float) base;
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
            Vec3 dir = new Vec3(shape[i][0], -1, shape[i][1]).normalize();
            float length = shape[i][2], base = shape[i][3];
            if (k <= 1) {
                // falling out of the cloud, faster and faster
                Vec3 from = hit.subtract(dir.scale(CLOUD_HEIGHT));
                Vec3 tipPos = from.lerp(hit, k * k);
                crystal(ctx, ice, glow, tipPos, dir, length, base, i);
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

    /**
     * A spike of ice, falling point first like a huge icicle: six-sided, thickest near its top, which is cut off in a
     * low faceted cap, and tapering the rest of its length to a needle point; a glow runs down its heart, and two
     * smaller shards grow out of its shoulders.
     */
    private static void crystal(VfxContext ctx, VertexConsumer ice, VertexConsumer glow, Vec3 tip, Vec3 dir, float length, float base, int seed) {
        Vec3[] b = VfxContext.basis(dir);
        shard(ctx, ice, tip, dir, b[0], b[1], length, base, seed * 0.7);
        // the shoulder shards, splayed out a little from the main spike
        for (int k = 0; k < 2; k++) {
            double a = seed * 1.3 + k * Math.PI;
            Vec3 side = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
            Vec3 d = dir.add(side.scale(-0.22)).normalize();
            Vec3 at = tip.subtract(dir.scale(length * 0.62)).add(side.scale(base * 0.7));
            Vec3[] bb = VfxContext.basis(d);
            shard(ctx, ice, at, d, bb[0], bb[1], length * 0.38f, base * 0.42f, seed + k);
        }
        ctx.softBeam(glow, tip.subtract(dir.scale(length * 0.9)), tip.subtract(dir.scale(length * 0.08)), base * 1.1f, Colors.alpha(0.5f, CORE), 0, 1, 0.2f, 0.2f);
    }

    /** One six-sided shard: the point at {@code tip}, widest {@code length * 0.82} back from it, capped beyond. */
    private static void shard(VfxContext ctx, VertexConsumer ice, Vec3 tip, Vec3 dir, Vec3 u, Vec3 v, float length, float base, double turn) {
        int sides = 6;
        Vec3 shoulder = tip.subtract(dir.scale(length * 0.82)), crown = tip.subtract(dir.scale(length));
        Vec3[] ring = new Vec3[sides], cap = new Vec3[sides];
        for (int s = 0; s < sides; s++) {
            double a = turn + s * Math.PI * 2 / sides;
            Vec3 off = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            ring[s] = shoulder.add(off.scale(base));
            cap[s] = crown.add(off.scale(base * 0.55));
        }
        for (int s = 0; s < sides; s++) {
            int n = (s + 1) % sides;
            int face = Colors.alpha(0.86f, Colors.scale(ICE, 0.7f + 0.3f * ((s * 2) % 3) / 2f));
            int edge = Colors.alpha(0.92f, CORE);
            // the long taper to the point, lighter along its edges
            ctx.quad(ice, tip, tip, ring[n], ring[s], 0, 0, 1, 1, face, face, edge, edge);
            // the short slope up to the cap, and the cap
            ctx.quad(ice, ring[s], ring[n], cap[n], cap[s], 0, 0, 1, 1, edge, edge, face, face);
            ctx.quad(ice, cap[s], cap[n], crown, crown, 0, 0, 1, 1, face, face, face, face);
        }
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
