package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * THE ULTRAMASSIVE BLACKHOLE. A point of nothing tears open over the land and swells into a black sphere - a hole in
 * the world - edged by a thin photon ring. Round it turns a thin accretion disk hundreds of blocks across, white-hot at
 * its inner edge and cooling out through orange to deep red, the inner part turning fastest, the side swinging towards
 * the eye brighter than the side swinging away. Its gravity bends the light of the disk's far side up over the top of
 * the hole in a blazing arc, and a fainter one under it. Dim spiral arms of gas wind in around it, and matter streams
 * down onto the disk. At the end it collapses to a point and bursts.
 */
public class UltramassiveBlackholeVfx extends Vfx {
    private static final int WHITE_HOT = 0xFFF8EE, HOT = 0xFFE0A8, ORANGE = 0xFF9636, RED = 0xC8401A, DUST = 0x4A2C22, GAS = 0x7A8494;
    private static final int STREAMS = 70;

    private final Vec3 center;
    private final float horizon, reach;
    private final int form, collapse;
    private final float[] ang, dist, speed, tilt;
    private final RandomSource rng;
    private BlockDebrisVfx debris;

    /**
     * @param horizon  radius of the black sphere
     * @param reach    radius of everything it swallows
     * @param form     ticks it takes to open
     * @param collapse tick at which it collapses
     */
    public UltramassiveBlackholeVfx(Vec3 center, float horizon, float reach, int form, int collapse, long seed) {
        super(collapse + 50);
        this.center = center;
        this.horizon = horizon;
        this.reach = reach;
        this.form = form;
        this.collapse = collapse;
        RandomSource r = RandomSource.create(seed);
        rng = RandomSource.create(seed ^ 0x5DEECE66DL);
        ang = new float[STREAMS];
        dist = new float[STREAMS];
        speed = new float[STREAMS];
        tilt = new float[STREAMS];
        for (int i = 0; i < STREAMS; i++) {
            ang[i] = r.nextFloat() * 6.283f;
            dist[i] = r.nextFloat();
            speed[i] = 0.5f + r.nextFloat();
            tilt[i] = (r.nextFloat() - 0.5f) * 0.5f;
        }
    }

    /** The land it eats is seen torn up and dragged in: real blocks from just ahead of the wave spiral into the hole. */
    public UltramassiveBlackholeVfx feeding(BlockDebrisVfx debris) {
        this.debris = debris;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (debris == null) return;
        if (age >= collapse) {
            debris.close();
            return;
        }
        if (age < form) return;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        float wave = reach * (age - form) / (float) (collapse - form);
        int n = Math.round(16 * com.fantasyweapons.config.ClientConfig.quality().density());
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = Math.max(6, wave + 4 + rng.nextDouble() * 14);
            int x = (int) Math.floor(center.x + Math.cos(a) * d), z = (int) Math.floor(center.z + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (top <= level.getMinBuildHeight()) continue;
            int y = top - (rng.nextFloat() < 0.55f ? 0 : rng.nextInt(14));
            var state = level.getBlockState(m.set(x, y, z));
            int life = Math.max(12, Math.min(collapse - age, 26 + (int) (d * 0.22) + rng.nextInt(18)));
            float size = rng.nextFloat() < 0.12f ? 2f + rng.nextFloat() * 1.5f : 1f;
            debris.suck(state, new Vec3(x + 0.5, y + 0.5, z + 0.5), center, size, 0.8f + rng.nextFloat() * 1.2f, life);
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float grow = easeOut(clamp01(t / form));
        float crush = clamp01((t - collapse) / 12f);
        float burst = clamp01((t - collapse - 12) / 38f);
        double far = VfxContext.viewReach();
        if (ctx.cam.distanceTo(center) < reach * 2.5) {
            float dark = 0.92f * clamp01(t / 30f) * (1 - burst);
            com.fantasyweapons.client.fx.SkyDarkness.request(dark);
        }
        ctx.squash = ctx.squashFor(center, far * 0.85);
        float r = horizon * (0.05f + 0.95f * grow) * (1 - easeIn(crush) * 0.97f);
        float a = grow * (1 - crush);
        if (burst <= 0) {
            drawCore(ctx, t, r);
            drawDisk(ctx, t, r, a);
            drawLensed(ctx, t, r, a);
            drawGas(ctx, t, a);
            drawStreams(ctx, t, r, a);
        }
        ctx.squash = 1;
        if (crush > 0) drawBurst(ctx, t - collapse, crush, burst);
    }

    /** The disk's axis: nearly upright, leaning a little. */
    private static final Vec3 AXIS = new Vec3(0.09, 1, 0.16).normalize();

    /** The hole: a solid black sphere (it hides the far side of the disk), edged by a thin, blazing photon ring. */
    private void drawCore(VfxContext ctx, float t, float r) {
        ctx.sphere(ctx.solid(VfxTextures.WHITE), center, r, 20, 32, 0xFF000000, false);
        var ring = ctx.additive(VfxTextures.RING);
        float flicker = 0.9f + 0.1f * (float) Math.sin(t * 1.7f);
        int segs = ctx.segments(96);
        ctx.ring(ring, center, ctx.camLeft, ctx.camUp, r * 1.0f, r * 1.035f, segs, Colors.alpha(flicker, WHITE_HOT));
        ctx.ring(ring, center, ctx.camLeft, ctx.camUp, r * 1.0f, r * 1.12f, segs, Colors.alpha(0.45f * flicker, HOT));
    }

    /** The colour of the disk {@code f} of the way out (0 inner edge, 1 outer), as hot as it is there. */
    private static int heat(float f) {
        if (f < 0.07f) return Colors.lerpRgb(WHITE_HOT, HOT, f / 0.07f);
        if (f < 0.3f) return Colors.lerpRgb(HOT, ORANGE, (f - 0.07f) / 0.23f);
        if (f < 0.7f) return Colors.lerpRgb(ORANGE, RED, (f - 0.3f) / 0.4f);
        return Colors.lerpRgb(RED, DUST, (f - 0.7f) / 0.3f);
    }

    /**
     * The accretion disk: a thin annulus in its plane, glowing smoothly from its white-hot inner edge to its dim outer
     * rim, streaked along its orbits by turbulence that turns fastest inside; the side moving towards the eye is brighter.
     * A second, fainter sheet just above it gives it a little thickness.
     */
    private void drawDisk(VfxContext ctx, float t, float r, float a) {
        if (a <= 0.01f) return;
        Vec3[] b = VfxContext.basis(AXIS);
        var glow = ctx.additive(VfxTextures.WHITE);
        var streaks = ctx.additive(VfxTextures.NOISE);
        float rin = r * 1.9f, rout = Math.max(rin + 10, reach * 0.55f * a);
        int rings = 18, segs = ctx.segments(128);
        Vec3 toCam = ctx.cam.subtract(center).normalize();
        for (int layer = 0; layer < 2; layer++) {
            float la = layer == 0 ? 1f : 0.3f;
            Vec3 lift = AXIS.scale(layer * r * 0.035);
            for (int k = 0; k < rings; k++) {
                float f0 = k / (float) rings, f1 = (k + 1) / (float) rings;
                float r0 = rin + (rout - rin) * f0 * f0, r1 = rin + (rout - rin) * f1 * f1;
                // the inner disk turns fastest
                float s0 = t * 0.012f / (0.15f + f0);
                for (int i = 0; i < segs; i++) {
                    double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                    Vec3 d0 = b[0].scale(Math.cos(a0)).add(b[1].scale(Math.sin(a0))), d1 = b[0].scale(Math.cos(a1)).add(b[1].scale(Math.sin(a1)));
                    Vec3 p00 = center.add(lift).add(d0.scale(r0)), p01 = center.add(lift).add(d1.scale(r0));
                    Vec3 p11 = center.add(lift).add(d1.scale(r1)), p10 = center.add(lift).add(d0.scale(r1));
                    // a smooth glow, then the streaks over it: long along the orbit, fine across it
                    float g = a * la * 0.55f;
                    ctx.quad(glow, p00, p01, p11, p10, 0, 0, 1, 1, col(f0, d0, toCam, g), col(f0, d1, toCam, g), col(f1, d1, toCam, g), col(f1, d0, toCam, g));
                    float u0 = (float) (a0 / (Math.PI * 2)) * 3 + s0, u1 = (float) (a1 / (Math.PI * 2)) * 3 + s0;
                    float h = a * la * 0.75f;
                    ctx.quad(streaks, p00, p01, p11, p10, u0, f0 * 14, u1, f1 * 14,
                            col(f0, d0, toCam, h), col(f0, d1, toCam, h), col(f1, d1, toCam, h), col(f1, d0, toCam, h));
                }
            }
        }
        // the blazing inner edge
        var ring = ctx.additive(VfxTextures.RING);
        ctx.ring(ring, center, b[0], b[1], rin * 0.97f, rin * 1.06f, segs, Colors.alpha(a, WHITE_HOT));
        ctx.ring(ring, center, b[0], b[1], rin * 0.95f, rin * 1.2f, segs, Colors.alpha(a * 0.5f, HOT));
    }

    /** A point of the disk {@code f} of the way out, in direction {@code d}: hot, faded out at the rim, brighter where it comes towards the eye. */
    private static int col(float f, Vec3 d, Vec3 toCam, float a) {
        Vec3 velocity = AXIS.cross(d);
        float doppler = (float) Math.max(0.35, Math.min(1.6, 1 + 0.6 * velocity.dot(toCam)));
        float fade = (float) Math.pow(1 - f, 1.5);
        return Colors.alpha(Math.min(1, a * fade * doppler * 1.7f), Colors.scale(heat(f), Math.min(1.35f, doppler)));
    }

    /**
     * The light of the disk's far side bent round the hole by its gravity: a thin, blazing band hugging the hole over
     * its top (as the eye sees it, the side away from the disk's plane) and a thinner one under it, which close into a
     * whole ring when the disk is seen from above.
     */
    private void drawLensed(VfxContext ctx, float t, float r, float a) {
        if (a <= 0.01f) return;
        Vec3 toCam = ctx.cam.subtract(center).normalize();
        double facing = Math.abs(AXIS.dot(toCam));
        Vec3 up = AXIS.subtract(toCam.scale(AXIS.dot(toCam)));
        if (up.lengthSqr() < 1e-6) up = ctx.camUp;
        up = up.normalize();
        Vec3 side = up.cross(toCam).normalize();
        var glow = ctx.additive(VfxTextures.WHITE);
        var streaks = ctx.additive(VfxTextures.NOISE);
        int segs = ctx.segments(96);
        float edgeOn = (float) (1 - facing);
        // over the top: the disk's far side
        lensArc(ctx, glow, streaks, side, up, r * 1.04f, r * (1.24f + 0.12f * edgeOn), 0, (float) Math.PI, segs, a, t);
        // under it: its underside, thinner
        lensArc(ctx, glow, streaks, side, up, r * 1.04f, r * (1.12f + 0.06f * edgeOn), (float) Math.PI, (float) Math.PI * 2, segs, a * (0.55f + 0.3f * (1 - edgeOn)), t);
    }

    private void lensArc(VfxContext ctx, VertexConsumer glow, VertexConsumer streaks, Vec3 side, Vec3 up, float rIn, float rOut, float from, float to, int segs,
                         float a, float t) {
        int rows = 4;
        for (int k = 0; k < rows; k++) {
            float f0 = k / (float) rows, f1 = (k + 1) / (float) rows;
            float r0 = rIn + (rOut - rIn) * f0, r1 = rIn + (rOut - rIn) * f1;
            for (int i = 0; i < segs; i++) {
                float u0 = i / (float) segs, u1 = (i + 1) / (float) segs;
                double b0 = from + (to - from) * u0, b1 = from + (to - from) * u1;
                Vec3 d0 = side.scale(Math.cos(b0)).add(up.scale(Math.sin(b0))), d1 = side.scale(Math.cos(b1)).add(up.scale(Math.sin(b1)));
                // brightest at its inner edge and over the top, thinning towards its ends where it meets the disk
                float e0 = 0.45f + 0.55f * (float) Math.sin(Math.PI * u0), e1 = 0.45f + 0.55f * (float) Math.sin(Math.PI * u1);
                float g0 = Math.min(1, a * 1.3f) * (float) Math.pow(1 - f0, 1.6), g1 = Math.min(1, a * 1.3f) * (float) Math.pow(1 - f1, 1.6);
                int h0 = heat(0.02f + f0 * 0.22f), h1 = heat(0.02f + f1 * 0.22f);
                Vec3 p00 = center.add(d0.scale(r0)), p01 = center.add(d1.scale(r0)), p11 = center.add(d1.scale(r1)), p10 = center.add(d0.scale(r1));
                ctx.quad(glow, p00, p01, p11, p10, 0, 0, 1, 1, Colors.alpha(g0 * e0 * 0.8f, h0), Colors.alpha(g0 * e1 * 0.8f, h0),
                        Colors.alpha(g1 * e1 * 0.8f, h1), Colors.alpha(g1 * e0 * 0.8f, h1));
                ctx.quad(streaks, p00, p01, p11, p10, u0 * 3 + t * 0.02f, f0 * 4, u1 * 3 + t * 0.02f, f1 * 4, Colors.alpha(g0 * e0, h0), Colors.alpha(g0 * e1, h0),
                        Colors.alpha(g1 * e1, h1), Colors.alpha(g1 * e0, h1));
            }
        }
    }

    /** Dim spiral arms of gas winding in round the hole, far out, in the disk's plane. */
    private void drawGas(VfxContext ctx, float t, float a) {
        if (a <= 0.01f) return;
        Vec3[] b = VfxContext.basis(AXIS);
        var mist = ctx.translucent(VfxTextures.MIST);
        int arms = 3, per = ctx.segments(26);
        for (int arm = 0; arm < arms; arm++) {
            for (int i = 0; i < per; i++) {
                float f = i / (float) per;
                double rr = reach * (0.35f + 0.95f * f);
                double ang = arm * Math.PI * 2 / arms + f * 4.2 - t * 0.0015;
                Vec3 p = center.add(b[0].scale(Math.cos(ang) * rr)).add(b[1].scale(Math.sin(ang) * rr));
                ctx.billboard(mist, p, (float) (reach * (0.16f + 0.2f * f)), i + arm * 3, Colors.alpha(a * 0.22f * (1 - f * 0.6f), i % 3 == 0 ? DUST : GAS));
            }
        }
    }

    private void drawStreams(VfxContext ctx, float t, float r, float a) {
        if (a <= 0.01f) return;
        Vec3[] axes = VfxContext.basis(AXIS);
        var spark = ctx.additive(VfxTextures.STREAK);
        for (int i = 0; i < STREAMS; i++) {
            float cyc = (t * 0.008f * speed[i] + dist[i]) % 1f;
            float rr = reach * 0.9f * (1 - cyc) + r * 2;
            double an = ang[i] + cyc * cyc * 9;
            Vec3 dir = axes[0].scale(Math.cos(an)).add(axes[1].scale(Math.sin(an)));
            Vec3 p = center.add(dir.scale(rr)).add(AXIS.scale(rr * tilt[i] * (1 - cyc) * 0.3));
            Vec3 tangent = axes[0].scale(-Math.sin(an)).add(axes[1].scale(Math.cos(an)));
            float ma = a * Math.min(1, cyc * 6) * Math.min(1, (1 - cyc) * 8);
            int col = cyc > 0.8f ? WHITE_HOT : cyc > 0.5f ? HOT : i % 3 == 0 ? ORANGE : RED;
            ctx.streak(spark, p, tangent.add(dir.scale(-0.4)), 6 + rr * 0.08f, 1.2f + rr * 0.01f, Colors.alpha(ma * 0.8f, col));
        }
    }

    private void drawBurst(VfxContext ctx, float f, float crush, float burst) {
        var glow = ctx.additive(VfxTextures.GLOW);
        var shock = ctx.additive(VfxTextures.SHOCKWAVE);
        float flash = burst <= 0 ? crush : 1 - burst;
        ctx.billboard(glow, center, horizon * (2 + 40 * easeOut(burst)), 0, Colors.alpha(flash, HOT));
        ctx.billboard(glow, center, horizon * (4 + 80 * easeOut(burst)), 0, Colors.alpha(flash * 0.5f, ORANGE));
        if (burst > 0) {
            float rr = reach * 1.4f * easeOut(burst);
            ctx.disc(shock, center, new Vec3(1, 0, 0), new Vec3(0, 0, 1), rr, 0, Colors.alpha((1 - burst) * 0.9f, HOT));
            ctx.disc(shock, center, new Vec3(1, 0, 0), new Vec3(0, 1, 0), rr * 0.8f, 0, Colors.alpha((1 - burst) * 0.5f, ORANGE));
        }
    }
}
