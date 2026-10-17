package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * THORNSPIRE CATACLYSM. A green fire runs out from the planted sword along the ground in a spiral; wherever it passes,
 * a thorn as big as a tower bursts up out of the earth with a spray of real torn-up blocks, spores and leaves, and stays
 * (the server builds it). A circle of runes keeps the caster's ground clear.
 */
public class ThornspireVfx extends Vfx {
    private static final int SAP = 0x9CFF6A, DEEP = 0x2E7A36, BARK = 0x5A4030, SPORE = 0xD8FFB0, RUNE = 0x7BE86B;
    private static final Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
    /** Ticks a thorn takes to burst up, and ticks its light stays while the blocks catch up. */
    private static final int RISE = 5, LINGER = 14;

    private final Vec3 center;
    private final float radius, safe;
    private final List<Vec3> pts;
    private final int[] ticks;
    private final float[] radii;
    private final int last;
    private final RandomSource rng;
    private BlockDebrisVfx debris;

    public ThornspireVfx(Vec3 center, float radius, float safe, List<Vec3> pts, int[] codes, long seed) {
        super(maxTick(codes) + 60);
        this.center = center;
        this.radius = radius;
        this.safe = safe;
        this.pts = pts;
        int n = Math.min(codes.length, pts.size() / 2);
        ticks = new int[n];
        radii = new float[n];
        for (int i = 0; i < n; i++) {
            ticks[i] = codes[i] / 1000;
            radii[i] = (codes[i] % 1000) / 10f;
        }
        last = maxTick(codes);
        rng = RandomSource.create(seed);
    }

    private static int maxTick(int[] codes) {
        int m = 0;
        for (int c : codes) m = Math.max(m, c / 1000);
        return m;
    }

    public ThornspireVfx bursting(BlockDebrisVfx debris) {
        this.debris = debris;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (debris == null) return;
        if (age > last + RISE + 4) {
            debris.close();
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        float density = com.fantasyweapons.config.ClientConfig.quality().density();
        for (int i = 0; i < ticks.length; i++) {
            int since = age - (ticks[i] - RISE);
            if (since < 0 || since > RISE) continue;
            // the ground torn open where the thorn comes up
            Vec3 foot = pts.get(i * 2);
            int n = Math.round((since == 0 ? 26 : 6) * density);
            for (int k = 0; k < n; k++) {
                double a = rng.nextDouble() * Math.PI * 2, d = rng.nextDouble() * (radii[i] + 3);
                int x = (int) Math.floor(foot.x + Math.cos(a) * d), z = (int) Math.floor(foot.z + Math.sin(a) * d);
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1 - rng.nextInt(2);
                var state = level.getBlockState(m.set(x, y, z));
                if (state.isAir()) continue;
                Vec3 vel = new Vec3(Math.cos(a) * (0.2 + rng.nextDouble() * 0.4), 0.7 + rng.nextDouble() * 0.9, Math.sin(a) * (0.2 + rng.nextDouble() * 0.4));
                debris.throwBlock(state, new Vec3(x + 0.5, y + 1, z + 0.5), vel, rng.nextFloat() < 0.15f ? 2f : 1f, 30 + rng.nextInt(25));
            }
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float fade = 1 - clamp01((t - last - 20) / 40f);
        if (fade <= 0) return;
        if (ctx.cam.distanceTo(center) < radius * 2.5) com.fantasyweapons.client.fx.SkyDarkness.request(0.45f * fade * clamp01(t / 10f));
        ctx.farClamp = VfxContext.viewReach() * 0.86;
        var white = ctx.additive(VfxTextures.WHITE);
        var glow = ctx.additive(VfxTextures.GLOW);
        var bark = ctx.translucent(VfxTextures.BARK);
        var mist = ctx.translucent(VfxTextures.MIST);
        var leaf = ctx.translucent(VfxTextures.LEAF);
        var spore = ctx.additive(VfxTextures.SPARK);
        // the green fire running out along the spiral, from thorn to thorn, ahead of them
        Vec3 prev = center;
        for (int i = 0; i < ticks.length; i++) {
            Vec3 foot = pts.get(i * 2).add(0, 3.2, 0);
            float reach = (t - (ticks[i] - RISE - 6)) / 6f;
            if (reach <= 0) break;
            Vec3 to = prev.lerp(foot, Math.min(1, reach));
            float hot = 1 - clamp01((t - ticks[i]) / 30f);
            GrantExecutionVfx.flatLine(ctx, white, prev, to, 1.2f, Colors.alpha(fade * (0.35f + 0.65f * hot), SAP));
            GrantExecutionVfx.flatLine(ctx, glow, prev, to, 6f, Colors.alpha(fade * 0.4f * (0.3f + 0.7f * hot), DEEP));
            prev = foot;
        }
        // the thorns bursting up
        for (int i = 0; i < ticks.length; i++) {
            float since = t - (ticks[i] - RISE);
            if (since < 0 || since > RISE + LINGER + 20) continue;
            Vec3 foot = pts.get(i * 2), tip = pts.get(i * 2 + 1);
            float grow = easeOut(clamp01(since / RISE));
            float solid = 1 - clamp01((since - RISE - LINGER) / 10f);
            if (solid > 0) thorn(ctx, bark, glow, foot, foot.lerp(tip, grow), radii[i] * (0.6f + 0.4f * grow), solid);
            float b = clamp01((since - RISE + 2) / 22f);
            if (b > 0 && b < 1) {
                Vec3 up = foot.add(0, 4, 0);
                ctx.billboard(glow, up, radii[i] * 6 * (1 - b * 0.5f), 0, Colors.alpha((1 - b) * 0.7f, SAP));
                ctx.billboard(mist, up.add(0, 2 + b * 6, 0), radii[i] * 3 + 16 * easeOut(b), i, Colors.alpha((1 - b) * 0.6f, 0x6B5A40));
                for (int s = 0; s < 10; s++) {
                    double a = s * Math.PI / 5 + i;
                    Vec3 out = new Vec3(Math.cos(a), 0.9 + (s % 3) * 0.5, Math.sin(a));
                    Vec3 p = up.add(out.scale(easeOut(b) * (radii[i] + 10))).add(0, -b * b * 8, 0);
                    ctx.billboard(s % 2 == 0 ? leaf : spore, p, s % 2 == 0 ? 1.4f : 0.8f, s + t * 0.2f, Colors.alpha(1 - b, s % 2 == 0 ? 0x4FAF3A : SPORE));
                }
            }
        }
        // spores drifting up over the new forest of thorns
        int n = ctx.segments(80);
        float alive = clamp01(t / 20f) * fade;
        for (int i = 0; i < n; i++) {
            double ang = i * 2.39996;
            float rr = safe + (radius - safe) * (float) Math.sqrt(((i * 0.618f) % 1f));
            float rise = ((i * 0.37f) + t * 0.01f) % 1f;
            Vec3 p = center.add(Math.cos(ang) * rr + Math.sin(t * 0.03 + i) * 2, 2 + rise * 50, Math.sin(ang) * rr);
            ctx.billboard(spore, p, 0.7f, i, Colors.alpha(alive * (1 - rise) * 0.8f, SPORE));
        }
        ctx.farClamp = 0;
        // the runes that keep the caster's ground clear
        float hold = fade * clamp01(t / 8f);
        ctx.disc(ctx.additive(VfxTextures.GRAND_CIRCLE), center.add(0, 0.08, 0), X, Z, safe, -t * 0.012f, Colors.alpha(hold * 0.9f, RUNE));
        ctx.disc(glow, center.add(0, 0.1, 0), X, Z, safe * 1.7f, 0, Colors.alpha(hold * 0.35f, RUNE));
        ctx.ring(ctx.additive(VfxTextures.RING), center.add(0, 0.12, 0), X, Z, safe * 0.98f, safe * 1.06f, 64, Colors.alpha(hold, 0xE0FFC8));
    }

    /** A thorn of bark from foot to tip, with sap glowing in the cracks of its skin. */
    private static void thorn(VfxContext ctx, VertexConsumer bark, VertexConsumer glow, Vec3 foot, Vec3 tip, float rad, float alpha) {
        Vec3 axis = tip.subtract(foot);
        double len = axis.length();
        if (len < 0.5) return;
        Vec3 d = axis.scale(1 / len);
        Vec3[] b = VfxContext.basis(d);
        int sides = 8, rows = 6;
        for (int r = 0; r < rows; r++) {
            double u0 = r / (double) rows, u1 = (r + 1) / (double) rows;
            double r0 = rad * (1 - u0) * (1 - u0 * 0.2), r1 = rad * (1 - u1) * (1 - u1 * 0.2);
            Vec3 c0 = foot.add(d.scale(len * u0)), c1 = foot.add(d.scale(len * u1));
            for (int s = 0; s < sides; s++) {
                double a0 = s * Math.PI * 2 / sides, a1 = (s + 1) * Math.PI * 2 / sides;
                Vec3 p00 = c0.add(b[0].scale(Math.cos(a0) * r0)).add(b[1].scale(Math.sin(a0) * r0));
                Vec3 p01 = c0.add(b[0].scale(Math.cos(a1) * r0)).add(b[1].scale(Math.sin(a1) * r0));
                Vec3 p11 = c1.add(b[0].scale(Math.cos(a1) * r1)).add(b[1].scale(Math.sin(a1) * r1));
                Vec3 p10 = c1.add(b[0].scale(Math.cos(a0) * r1)).add(b[1].scale(Math.sin(a0) * r1));
                int col = Colors.alpha(alpha, Colors.scale(BARK, 0.7f + 0.3f * (s % 3) / 2f));
                ctx.quad(bark, p00, p01, p11, p10, s / (float) sides, (float) u0 * 3, (s + 1) / (float) sides, (float) u1 * 3, col, col, col, col);
            }
        }
        ctx.softBeam(glow, foot, tip, (float) rad * 1.2f, Colors.alpha(alpha * 0.45f, SAP), 0, 1, 0.1f, 0.3f);
    }
}
