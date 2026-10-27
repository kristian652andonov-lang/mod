package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.weapons.ultimate.CrackNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * THE EARTHSPLITTING ERUPTION. Cracks race out across the land, glowing, light and embers welling up out of them; then
 * the earth explodes: a fireball swells out of the ground and climbs into a towering column of fire and smoke capped by
 * a spreading mushroom cloud, a shockwave rolls out, lava rains down and smoke hangs over the burning crater.
 */
public class EruptionVfx extends Vfx {
    private static final int WHITE = 0xFFF6E0, YELLOW = 0xFFD050, ORANGE = 0xFF7A1A, RED = 0xE0300A, SMOKE = 0x2A2220, ASH = 0x4A403A;
    private static final int AFTER = 170;

    private final Vec3 ground;
    private final float radius;
    private final int delay;
    private final CrackNetwork cracks;
    /** Ground heights at each crack segment's two ends. */
    private final float[][] heights;
    private final float[][] blobs;

    public EruptionVfx(Vec3 ground, float radius, int delay, CrackNetwork cracks, long seed) {
        super(delay + AFTER);
        this.ground = ground;
        this.radius = radius;
        this.delay = delay;
        this.cracks = cracks;
        var level = Minecraft.getInstance().level;
        List<CrackNetwork.Segment> segs = cracks.segments();
        heights = new float[segs.size()][2];
        for (int i = 0; i < segs.size(); i++) {
            var s = segs.get(i);
            heights[i][0] = height(level, ground.x + s.x0(), ground.z + s.z0());
            heights[i][1] = height(level, ground.x + s.x1(), ground.z + s.z1());
        }
        RandomSource r = RandomSource.create(seed);
        blobs = new float[90][6];
        for (float[] b : blobs) {
            double a = r.nextDouble() * Math.PI * 2;
            float out = 0.4f + r.nextFloat() * 1.8f;
            b[0] = (float) Math.cos(a) * out;
            b[1] = 1.6f + r.nextFloat() * 2.4f;
            b[2] = (float) Math.sin(a) * out;
            b[3] = r.nextFloat() * 14;
            b[4] = 2 + r.nextFloat() * 4;
            b[5] = r.nextFloat();
        }
    }

    private float height(net.minecraft.client.multiplayer.ClientLevel level, double x, double z) {
        if (level == null || !level.hasChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) return (float) ground.y;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        double reach = VfxContext.viewReach();
        ctx.farClamp = reach * 0.86;
        drawCracks(ctx, t);
        if (t >= delay) {
            float f = t - delay;
            if (ctx.cam.distanceTo(ground) < radius * 5) {
                com.fantasyweapons.client.fx.SkyDarkness.request(0.6f * clamp01(f / 10f) * clamp01((AFTER - f) / 40f));
            }
            drawBlast(ctx, f);
            drawColumn(ctx, f);
            drawLavaRain(ctx, f);
        }
        ctx.farClamp = 0;
    }

    // ------------------------------------------------------------------------------------------------------------
    // the cracks
    // ------------------------------------------------------------------------------------------------------------

    private void drawCracks(VfxContext ctx, float t) {
        float grown = (float) cracks.reach() * clamp01(t / (delay * 0.85f));
        float fade = t < delay ? 1 : 1 - clamp01((t - delay - 40) / 60f);
        if (fade <= 0) return;
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 0.5f);
        var glow = ctx.additive(VfxTextures.GLOW);
        var white = ctx.additive(VfxTextures.WHITE);
        var soft = ctx.additive(VfxTextures.BEAM_SOFT);
        List<CrackNetwork.Segment> segs = cracks.segments();
        for (int i = 0; i < segs.size(); i++) {
            var s = segs.get(i);
            if (s.from() > grown) continue;
            double k = Math.min(1, (grown - s.from()) / Math.max(1e-3, s.length()));
            Vec3 a = new Vec3(ground.x + s.x0(), heights[i][0] + 0.25, ground.z + s.z0());
            Vec3 b0 = new Vec3(ground.x + s.x1(), heights[i][1] + 0.25, ground.z + s.z1());
            Vec3 b = a.lerp(b0, k);
            float w = (float) s.width();
            int core = Colors.alpha(fade * pulse, YELLOW), rim = Colors.alpha(fade * 0.6f * pulse, ORANGE);
            GrantExecutionVfx.flatLine(ctx, white, a, b, w * 0.9f, core);
            GrantExecutionVfx.flatLine(ctx, glow, a, b, w * 3.2f, rim);
            // light welling up out of the crack
            Vec3 mid = a.lerp(b, 0.5);
            ctx.softBeam(soft, mid.add(0, -2, 0), mid.add(0, 8 + w * 2, 0), (float) a.distanceTo(b) + w, Colors.alpha(fade * 0.25f, ORANGE), 0, 1, 0, 1);
        }
        // embers rising from the cracks
        var star = ctx.additive(VfxTextures.STAR);
        int n = Math.min(segs.size(), ctx.segments(160));
        for (int j = 0; j < n; j++) {
            int i = (j * 7919) % segs.size();
            var s = segs.get(i);
            if (s.from() > grown) continue;
            float cyc = ((j * 0.618f) + t * 0.025f) % 1f;
            Vec3 p = new Vec3(ground.x + (s.x0() + s.x1()) / 2, (heights[i][0] + heights[i][1]) / 2 + cyc * 22, ground.z + (s.z0() + s.z1()) / 2);
            ctx.billboard(star, p.add(Math.sin(j + t * 0.1) * 1.5, 0, Math.cos(j * 1.3 + t * 0.1) * 1.5), 0.7f, j, Colors.alpha(fade * (1 - cyc), j % 3 == 0 ? YELLOW : ORANGE));
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the explosion
    // ------------------------------------------------------------------------------------------------------------

    private void drawBlast(VfxContext ctx, float f) {
        var glow = ctx.additive(VfxTextures.GLOW);
        var flame = ctx.additive(VfxTextures.FLAME);
        var shock = ctx.additive(VfxTextures.SHOCKWAVE);
        Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
        // the flash and the fireball swelling out of the ground
        float swell = easeOut(clamp01(f / 18f));
        float cool = clamp01((f - 10) / 50f);
        float fa = 1 - clamp01((f - 30) / 50f);
        if (fa > 0) {
            float r = radius * 0.65f * swell;
            Vec3 c = ground.add(0, r * 0.4, 0);
            ctx.billboard(glow, c, r * 3.2f, 0, Colors.alpha(fa * (1 - cool * 0.6f), Colors.lerpRgb(WHITE, ORANGE, cool)));
            int puffs = ctx.segments(40);
            for (int i = 0; i < puffs; i++) {
                double a = i * 2.39996, e = (i * 0.37) % 1 - 0.3;
                Vec3 dir = new Vec3(Math.cos(a) * Math.cos(e), Math.sin(e) + 0.4, Math.sin(a) * Math.cos(e)).normalize();
                Vec3 p = c.add(dir.scale(r * (0.5 + 0.5 * ((i * 0.61) % 1))));
                int col = Colors.lerpRgb(i % 3 == 0 ? WHITE : YELLOW, i % 2 == 0 ? RED : ORANGE, cool);
                ctx.billboard(flame, p, r * 0.9f, i + f * 0.03f, Colors.alpha(fa * 0.85f, col));
            }
        }
        // shockwaves over the land
        for (int k = 0; k < 3; k++) {
            float s = (f - k * 7) / 45f;
            if (s < 0 || s > 1) continue;
            ctx.disc(shock, ground.add(0, 1.5 + k, 0), X, Z, radius * (0.3f + 2.2f * easeOut(s)), k, Colors.alpha((1 - s) * 0.9f, k == 0 ? WHITE : ORANGE));
        }
    }

    private void drawColumn(VfxContext ctx, float f) {
        float rise = easeOut(clamp01(f / 70f));
        float a = clamp01(f / 6f) * (1 - clamp01((f - AFTER + 50) / 50f));
        if (a <= 0.01f) return;
        float height = radius * 2.1f * rise;
        var flame = ctx.additive(VfxTextures.FLAME);
        var mist = ctx.translucent(VfxTextures.MIST);
        var glow = ctx.additive(VfxTextures.GLOW);
        float heat = 1 - clamp01((f - 20) / 90f);
        int layers = ctx.segments(26);
        for (int i = 0; i < layers; i++) {
            float u = i / (float) (layers - 1);
            float y = height * u;
            float w = radius * (0.32f - 0.12f * u) * (0.85f + 0.15f * (float) Math.sin(f * 0.2f + i));
            Vec3 p = ground.add(Math.sin(i * 1.7 + f * 0.05) * w * 0.15, y, Math.cos(i * 1.3 + f * 0.05) * w * 0.15);
            ctx.billboard(mist, p, w * 2.4f, i * 0.7f + f * 0.01f, Colors.alpha(a * 0.75f, Colors.lerpRgb(SMOKE, ASH, u)));
            if (heat > 0 && u < 0.8f) {
                ctx.billboard(flame, p, w * 1.9f, i + f * 0.04f, Colors.alpha(a * heat * (1 - u) * 0.9f, u < 0.3f ? YELLOW : ORANGE));
                ctx.billboard(glow, p, w * 3, 0, Colors.alpha(a * heat * 0.35f * (1 - u), RED));
            }
        }
        // the mushroom cap rolling outward at the top
        Vec3 top = ground.add(0, height, 0);
        float capR = radius * (0.4f + 0.7f * rise);
        int puffs = ctx.segments(30);
        for (int i = 0; i < puffs; i++) {
            double ang = i * Math.PI * 2 / puffs + f * 0.004;
            Vec3 p = top.add(Math.cos(ang) * capR * 0.8, Math.sin(i * 2.1) * capR * 0.1, Math.sin(ang) * capR * 0.8);
            ctx.billboard(mist, p, capR * 0.9f, i, Colors.alpha(a * 0.8f, i % 2 == 0 ? SMOKE : ASH));
            if (heat > 0.2f) ctx.billboard(flame, p.add(0, -capR * 0.2, 0), capR * 0.5f, i, Colors.alpha(a * (heat - 0.2f) * 0.7f, ORANGE));
        }
        ctx.billboard(mist, top, capR * 1.6f, f * 0.003f, Colors.alpha(a * 0.85f, SMOKE));
    }

    private void drawLavaRain(VfxContext ctx, float f) {
        var glow = ctx.additive(VfxTextures.GLOW);
        var streak = ctx.additive(VfxTextures.STREAK);
        for (float[] b : blobs) {
            float t = f - b[3];
            if (t < 0 || t > 60) continue;
            double x = b[0] * t, y = b[1] * t - 0.06 * t * t, z = b[2] * t;
            if (y < -4) continue;
            Vec3 p = ground.add(x, y + 4, z);
            Vec3 v = new Vec3(b[0], b[1] - 0.12 * t, b[2]);
            float a = Math.min(1, t / 3f);
            ctx.streak(streak, p, v, b[4] * 2, b[4], Colors.alpha(a, b[5] < 0.5f ? ORANGE : YELLOW));
            ctx.billboard(glow, p, b[4] * 2.5f, 0, Colors.alpha(a * 0.5f, RED));
        }
    }
}
