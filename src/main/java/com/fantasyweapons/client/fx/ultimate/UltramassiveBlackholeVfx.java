package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * THE ULTRAMASSIVE BLACKHOLE. A point of nothing tears open over the land and swells into a black sphere ringed by a
 * blazing photon ring; an accretion disk hundreds of blocks across turns about it, white-hot at the heart and burning
 * out through gold into violet, its far side lensed up over the top of the sphere; jets of light lance from its poles,
 * streams of matter spiral in, and rings of distorted space contract towards it. At the end it collapses to a point
 * and bursts.
 */
public class UltramassiveBlackholeVfx extends Vfx {
    private static final int HOT = 0xFFF4E0, GOLD = 0xFFB040, EMBER = 0xFF5A2A, VIOLET = 0x8A3CFF, DEEP = 0x2A0A5A;
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
        if (burst <= 0) {
            drawDisk(ctx, t, r, grow * (1 - crush));
            drawCore(ctx, t, r);
            drawJets(ctx, t, r, grow * (1 - crush));
            drawStreams(ctx, t, r, grow * (1 - crush));
        }
        ctx.squash = 1;
        drawSpaceRings(ctx, t, grow * (1 - crush));
        if (crush > 0) drawBurst(ctx, t - collapse, crush, burst);
    }

    /** The disk's plane, tilted a little, turning slowly. */
    private Vec3[] diskAxes() {
        Vec3 ax = new Vec3(1, 0.18, 0).normalize();
        Vec3 ay = new Vec3(0, 0.45, 1).normalize();
        return new Vec3[]{ax, ay};
    }

    private void drawCore(VfxContext ctx, float t, float r) {
        // the sphere of nothing: drawn opaque black, so it blots out the sky and the disk behind it
        var black = ctx.translucent(VfxTextures.WHITE);
        ctx.sphere(black, center, r, 16, 24, 0xFF000000, false);
        // the photon ring, always facing the eye
        var ring = ctx.additive(VfxTextures.RING);
        Vec3 left = ctx.camLeft, up = ctx.camUp;
        float flicker = 0.85f + 0.15f * (float) Math.sin(t * 1.3f);
        ctx.ring(ring, center, left, up, r * 1.0f, r * 1.12f, ctx.segments(64), Colors.alpha(flicker, HOT));
        ctx.ring(ring, center, left, up, r * 1.05f, r * 1.4f, ctx.segments(64), Colors.alpha(0.6f, GOLD));
        ctx.ring(ring, center, left, up, r * 1.3f, r * 2.1f, ctx.segments(64), Colors.alpha(0.3f, VIOLET));
        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.billboard(glow, center, r * 6f, 0, Colors.alpha(0.35f, VIOLET));
    }

    private void drawDisk(VfxContext ctx, float t, float r, float a) {
        if (a <= 0.01f) return;
        Vec3[] axes = diskAxes();
        Vec3 ax = axes[0], ay = axes[1];
        var swirl = ctx.energy(VfxTextures.SWIRL);
        var ring = ctx.additive(VfxTextures.RING);
        int segs = ctx.segments(96);
        float spin = t * 0.05f;
        float outer = reach * 0.62f * a;
        // layers of the disk: white-hot at the heart, gold, ember, violet at the rim
        ctx.disc(swirl, center, ax, ay, outer, spin * 0.6f, Colors.alpha(a * 0.55f, VIOLET));
        ctx.disc(swirl, center.add(0, 0.2, 0), ax, ay, outer * 0.62f, spin, Colors.alpha(a * 0.7f, EMBER));
        ctx.disc(swirl, center.add(0, 0.4, 0), ax, ay, outer * 0.36f, spin * 1.6f, Colors.alpha(a * 0.85f, GOLD));
        ctx.ring(ring, center, ax, ay, r * 1.15f, r * 2.6f, segs, Colors.alpha(a * 0.95f, HOT));
        ctx.ring(ring, center, ax, ay, r * 2.2f, outer * 0.5f, segs, Colors.alpha(a * 0.5f, GOLD));
        ctx.ring(ring, center, ax, ay, outer * 0.45f, outer, segs, Colors.alpha(a * 0.3f, VIOLET));
        // the far side of the disk, bent up over the sphere by its gravity (and under it)
        Vec3 toCam = ctx.cam.subtract(center).normalize();
        Vec3 side = toCam.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 vup = side.cross(toCam).normalize();
        ctx.arc(ring, center, side, vup, r * 1.1f, r * 1.9f, 0, (float) Math.PI, segs / 2, (u, o) -> Colors.alpha(a * (o ? 0.25f : 0.8f), HOT));
        ctx.arc(ring, center, side, vup, r * 1.1f, r * 1.6f, (float) Math.PI, (float) Math.PI * 2, segs / 2, (u, o) -> Colors.alpha(a * (o ? 0.15f : 0.5f), GOLD));
    }

    private void drawJets(VfxContext ctx, float t, float r, float a) {
        if (a <= 0.01f) return;
        Vec3 axis = diskAxes()[0].cross(diskAxes()[1]).normalize();
        if (axis.y < 0) axis = axis.scale(-1);
        float len = reach * 1.6f * a;
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 0.7f);
        var soft = ctx.additive(VfxTextures.BEAM_SOFT);
        var energy = ctx.energy(VfxTextures.BEAM_SOFT);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 from = center.add(axis.scale(s * r * 1.2)), to = center.add(axis.scale(s * len));
            ctx.softBeam(soft, from, to, r * 2.4f, Colors.alpha(a * 0.4f * pulse, VIOLET), 0, 1, 0.02f, 0.6f);
            ctx.softBeam(energy, from, to, r * 0.9f, Colors.alpha(a * 0.9f * pulse, HOT), t * 0.08f, t * 0.08f + 6, 0.02f, 0.5f);
        }
    }

    private void drawStreams(VfxContext ctx, float t, float r, float a) {
        if (a <= 0.01f) return;
        Vec3[] axes = diskAxes();
        var spark = ctx.additive(VfxTextures.STREAK);
        for (int i = 0; i < STREAMS; i++) {
            float cyc = (t * 0.008f * speed[i] + dist[i]) % 1f;
            float rr = reach * 0.9f * (1 - cyc) + r;
            double an = ang[i] + cyc * cyc * 9;
            Vec3 dir = axes[0].scale(Math.cos(an)).add(axes[1].scale(Math.sin(an)));
            Vec3 p = center.add(dir.scale(rr)).add(0, rr * tilt[i] * (1 - cyc), 0);
            Vec3 tangent = axes[0].scale(-Math.sin(an)).add(axes[1].scale(Math.cos(an)));
            float ma = a * Math.min(1, cyc * 6) * Math.min(1, (1 - cyc) * 8);
            int col = cyc > 0.8f ? HOT : cyc > 0.5f ? GOLD : i % 3 == 0 ? VIOLET : EMBER;
            ctx.streak(spark, p, tangent.add(dir.scale(-0.4)), 6 + rr * 0.08f, 1.2f + rr * 0.01f, Colors.alpha(ma * 0.9f, col));
        }
    }

    /** Rings of bent space on the land, closing in on the hole. */
    private void drawSpaceRings(VfxContext ctx, float t, float a) {
        if (a <= 0.01f) return;
        Vec3 ground = new Vec3(center.x, center.y - 95, center.z);
        var shock = ctx.additive(VfxTextures.SHOCKWAVE);
        Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
        for (int k = 0; k < 4; k++) {
            float cyc = ((t * 0.006f) + k / 4f) % 1f;
            float rr = reach * (1 - cyc);
            ctx.disc(shock, ground.add(0, 1 + k * 0.5, 0), X, Z, rr, k, Colors.alpha(a * 0.35f * Math.min(1, cyc * 4) * (1 - cyc), VIOLET));
        }
    }

    private void drawBurst(VfxContext ctx, float f, float crush, float burst) {
        var glow = ctx.additive(VfxTextures.GLOW);
        var shock = ctx.additive(VfxTextures.SHOCKWAVE);
        float flash = burst <= 0 ? crush : 1 - burst;
        ctx.billboard(glow, center, horizon * (2 + 40 * easeOut(burst)), 0, Colors.alpha(flash, HOT));
        ctx.billboard(glow, center, horizon * (4 + 80 * easeOut(burst)), 0, Colors.alpha(flash * 0.5f, VIOLET));
        if (burst > 0) {
            float rr = reach * 1.4f * easeOut(burst);
            ctx.disc(shock, center, new Vec3(1, 0, 0), new Vec3(0, 0, 1), rr, 0, Colors.alpha((1 - burst) * 0.9f, HOT));
            ctx.disc(shock, center, new Vec3(1, 0, 0), new Vec3(0, 1, 0), rr * 0.8f, 0, Colors.alpha((1 - burst) * 0.5f, VIOLET));
        }
    }
}
