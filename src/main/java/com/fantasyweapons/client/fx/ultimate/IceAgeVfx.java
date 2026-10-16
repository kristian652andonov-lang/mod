package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * THE ICE AGE. The sea heaves up at the target in a great dome, collapses, and rolls out as a tsunami in every
 * direction: a curling wall of dark water crested in foam and spray, riding over the land. Behind it everything is
 * frozen: an icy fog lies over the new ice and snow drifts down.
 */
public class IceAgeVfx extends Vfx {
    private static final int DEEP = 0x0E3A5A, WATER = 0x2A78A8, CREST = 0x9FE6FF, FOAM = 0xF4FCFF, FROST = 0xDDF4FF;
    private static final int ANGLES = 96;
    /** Wave rows from the front at its foot, up over the curling crest, down its long back: radial offset and height. */
    private static final float[][] PROFILE = {{0f, 0.02f}, {-0.05f, 0.45f}, {0.15f, 0.9f}, {0.25f, 1.0f}, {0.05f, 0.92f}, {-0.35f, 0.7f},
            {-0.8f, 0.42f}, {-1.4f, 0.18f}, {-2.1f, 0.02f}};

    private final Vec3 center;
    private final float radius, height, speed;
    private final int surge;
    private final float[] base = new float[ANGLES];
    private boolean based;

    public IceAgeVfx(Vec3 center, float radius, float height, float speed, int surge) {
        super(surge + (int) (radius / speed) + 120);
        this.center = center;
        this.radius = radius;
        this.height = height;
        this.speed = speed;
        this.surge = surge;
    }

    private float wave(float t) {
        return Math.max(0, (t - surge) * speed);
    }

    @Override
    public void tick() {
        super.tick();
        // the wave rides over the land: its foot follows the ground (never below the sea) where it is now
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        float rw = wave(age);
        for (int i = 0; i < ANGLES; i++) {
            double a = i * Math.PI * 2 / ANGLES;
            int x = (int) Math.floor(center.x + Math.cos(a) * rw), z = (int) Math.floor(center.z + Math.sin(a) * rw);
            float g = level.hasChunk(x >> 4, z >> 4) ? Math.max(level.getSeaLevel(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)) : (float) center.y;
            base[i] = based ? base[i] + (g - base[i]) * 0.25f : g;
        }
        based = true;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        ctx.farClamp = VfxContext.viewReach() * 0.86;
        if (t < surge + 10) drawSurge(ctx, t);
        float rw = wave(t);
        float alive = 1 - clamp01((rw - radius) / 30f);
        if (t >= surge && alive > 0) drawWave(ctx, t, rw, alive);
        drawFrost(ctx, t, rw);
        ctx.farClamp = 0;
    }

    /** The sea heaving up into a dome at the target, then falling outward into the wave. */
    private void drawSurge(VfxContext ctx, float t) {
        float rise = t < surge ? easeOut(t / surge) : 1 - clamp01((t - surge) / 10f);
        float h = height * 1.4f * rise, r = height * (0.6f + 0.8f * (t / (surge + 10)));
        var water = ctx.translucent(VfxTextures.NOISE);
        var mist = ctx.translucent(VfxTextures.MIST);
        int segs = ctx.segments(32), rows = 6;
        for (int k = 0; k < rows; k++) {
            float u0 = k / (float) rows, u1 = (k + 1) / (float) rows;
            float r0 = r * (float) Math.cos(u0 * Math.PI / 2), r1 = r * (float) Math.cos(u1 * Math.PI / 2);
            float y0 = h * (float) Math.sin(u0 * Math.PI / 2), y1 = h * (float) Math.sin(u1 * Math.PI / 2);
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                Vec3 p00 = center.add(Math.cos(a0) * r0, y0, Math.sin(a0) * r0), p01 = center.add(Math.cos(a1) * r0, y0, Math.sin(a1) * r0);
                Vec3 p11 = center.add(Math.cos(a1) * r1, y1, Math.sin(a1) * r1), p10 = center.add(Math.cos(a0) * r1, y1, Math.sin(a0) * r1);
                int c0 = Colors.alpha(0.8f, Colors.lerpRgb(DEEP, WATER, u0)), c1 = Colors.alpha(0.8f, Colors.lerpRgb(DEEP, CREST, u1));
                ctx.quad(water, p00, p01, p11, p10, i * 0.2f + t * 0.02f, u0, i * 0.2f + 0.2f + t * 0.02f, u1, c0, c0, c1, c1);
            }
        }
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16 + t * 0.03;
            ctx.billboard(mist, center.add(Math.cos(a) * r * 0.6, h * 0.9, Math.sin(a) * r * 0.6), r * 0.7f, i, Colors.alpha(0.6f * rise, FOAM));
        }
    }

    private void drawWave(VfxContext ctx, float t, float rw, float alive) {
        var water = ctx.translucent(VfxTextures.NOISE);
        var glint = ctx.additive(VfxTextures.NOISE);
        var mist = ctx.translucent(VfxTextures.MIST);
        var streak = ctx.additive(VfxTextures.STREAK);
        // the wave grows to full height as it leaves, and wears down near the end of its run
        float h = height * Math.min(1, 0.4f + (t - surge) / 30f) * (0.55f + 0.45f * (1 - clamp01((rw - radius * 0.7f) / (radius * 0.3f))));
        float depth = h * 1.5f;
        int n = PROFILE.length;
        Vec3[][] grid = new Vec3[ANGLES + 1][n];
        for (int i = 0; i <= ANGLES; i++) {
            double a = i * Math.PI * 2 / ANGLES;
            double cos = Math.cos(a), sin = Math.sin(a);
            float g = base[i % ANGLES];
            float wobble = 1 + 0.08f * (float) Math.sin(a * 7 + t * 0.15) + 0.05f * (float) Math.sin(a * 13 - t * 0.2);
            for (int k = 0; k < n; k++) {
                float rr = Math.max(0, rw + PROFILE[k][0] * depth);
                grid[i][k] = new Vec3(center.x + cos * rr, g + PROFILE[k][1] * h * wobble, center.z + sin * rr);
            }
        }
        for (int i = 0; i < ANGLES; i++) {
            for (int k = 0; k < n - 1; k++) {
                float u0 = k / (float) (n - 1), u1 = (k + 1) / (float) (n - 1);
                int c0 = Colors.alpha(alive * (k >= 5 ? 0.55f : 0.88f), k <= 1 ? DEEP : k <= 3 ? Colors.lerpRgb(WATER, CREST, 0.5f) : WATER);
                int c1 = Colors.alpha(alive * (k + 1 >= 5 ? 0.45f : 0.88f), k + 1 <= 1 ? DEEP : k + 1 <= 3 ? CREST : WATER);
                float s0 = i * 0.35f + t * 0.03f, s1 = s0 + 0.35f;
                ctx.quad(water, grid[i][k], grid[i + 1][k], grid[i + 1][k + 1], grid[i][k + 1], s0, u0, s1, u1, c0, c0, c1, c1);
                if (k == 2 || k == 3) {
                    int gc = Colors.alpha(alive * 0.25f, CREST);
                    ctx.quad(glint, grid[i][k], grid[i + 1][k], grid[i + 1][k + 1], grid[i][k + 1], s0 * 2, u0 - t * 0.05f, s1 * 2, u1 - t * 0.05f, gc, gc, gc, gc);
                }
            }
            // foam boiling along the crest, spray thrown off its front
            if (i % 2 == 0) {
                Vec3 crest = grid[i][3];
                ctx.billboard(mist, crest.add(0, h * 0.06, 0), h * 0.55f, i + t * 0.02f, Colors.alpha(alive * 0.75f, FOAM));
                Vec3 out = grid[i][3].subtract(center).multiply(1, 0, 1).normalize();
                float cyc = ((i * 0.37f) + t * 0.05f) % 1f;
                ctx.streak(streak, crest.add(out.scale(cyc * h * 0.5)).add(0, -cyc * h * 0.3, 0), out.add(0, -0.4, 0), h * 0.4f, h * 0.06f,
                        Colors.alpha(alive * (1 - cyc) * 0.7f, FOAM));
            }
        }
    }

    /** Icy fog over the frozen land behind the wave, and snow drifting down. */
    private void drawFrost(VfxContext ctx, float t, float rw) {
        float a = clamp01((t - surge) / 20f) * (1 - clamp01((t - lifetime + 50) / 50f));
        if (a <= 0.01f || rw < 10) return;
        var mist = ctx.translucent(VfxTextures.MIST);
        var flake = ctx.additive(VfxTextures.SNOWFLAKE);
        int n = ctx.segments(70);
        float inner = Math.min(rw, radius) - 8;
        for (int i = 0; i < n; i++) {
            double ang = i * 2.39996;
            float rr = inner * (float) Math.sqrt(((i * 0.618f) % 1f));
            Vec3 p = center.add(Math.cos(ang) * rr, 3 + (i % 4), Math.sin(ang) * rr);
            ctx.billboard(mist, p, 22 + (i % 5) * 4, i * 0.3f + t * 0.002f, Colors.alpha(a * 0.32f, FROST));
            float fall = ((i * 0.37f) + t * 0.006f) % 1f;
            ctx.billboard(flake, p.add(Math.sin(i + t * 0.05) * 3, 40 * (1 - fall), Math.cos(i * 1.7 + t * 0.05) * 3), 0.9f, i + t * 0.05f,
                    Colors.alpha(a * 0.9f, 0xFFFFFF));
        }
    }
}
