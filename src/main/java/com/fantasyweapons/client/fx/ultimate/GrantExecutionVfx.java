package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/**
 * THE BEAM OF GRANT EXECUTION. An enormous magic circle opens so high above the target that it fades into the sky:
 * a great rune circle ringed by bands of runes that draw themselves, thirty-two lesser circles and magic triangles
 * around its rim, and smaller circles stacked in tiers on top of it. A thin beam marks the target, then the full beam
 * falls - a column of white and golden light as wide as the land it erases - rings racing down it, shockwaves rolling
 * out over the ground; at the end it narrows to a thread and the circle comes apart.
 */
public class GrantExecutionVfx extends Vfx {
    private static final Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1), UP = new Vec3(0, 1, 0);
    private static final int WHITE = 0xFFFFFF, PALE = 0xFFF6D6, GOLD = 0xFFD970, AMBER = 0xFFB43A, YELLOW = 0xFFC72E;
    private static final int SATELLITES = 32, TIERS = 4;
    /** Ticks the circle lingers and dissolves after the beam. */
    private static final int AFTER = 60;

    private final Vec3 ground, sky;
    private final float radius, circle;
    private final int delay, beam;
    private final double bottom;

    /**
     * @param ground the ground at the target
     * @param radius the radius the beam erases
     * @param bottom the bottom of the world (the beam reaches down to it)
     */
    public GrantExecutionVfx(Vec3 ground, float radius, int delay, int beam, double bottom) {
        super(delay + beam + AFTER);
        this.ground = ground;
        this.radius = radius;
        this.delay = delay;
        this.beam = beam;
        this.bottom = bottom;
        this.circle = radius * 1.45f;
        this.sky = ground.add(0, Math.max(220, radius * 2.3f), 0);
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float end = clamp01((t - delay - beam) / AFTER);
        double reach = VfxContext.viewReach();
        // the sky dims as the circle draws its power, so the beam blazes against it
        if (ctx.cam.distanceTo(ground) < radius * 7) {
            float dark = 0.78f * easeInOut(clamp01(t / (delay * 0.8f))) * (1 - clamp01((t - delay - beam) / 40f));
            if (dark > 0) com.fantasyweapons.client.fx.SkyDarkness.request(dark);
        }
        drawSky(ctx, t, end, reach);
        ctx.squash = 1;
        ctx.farClamp = reach * 0.86;
        drawCharge(ctx, t);
        drawClouds(ctx, t);
        if (t >= delay) drawBeam(ctx, t - delay);
        ctx.farClamp = 0;
        drawGround(ctx, t);
    }

    // ------------------------------------------------------------------------------------------------------------
    // the circle in the sky
    // ------------------------------------------------------------------------------------------------------------

    private void drawSky(VfxContext ctx, float t, float end, double reach) {
        // so far away it fades a little into the sky
        float a = clamp01(t / 14f) * (1 - easeIn(end)) * 0.82f;
        if (a <= 0.01f) return;
        ctx.squash = ctx.squashFor(sky, reach * 0.8);
        boolean firing = t >= delay && t < delay + beam;
        float flare = firing ? 1 : t >= delay + beam ? 1 - clamp01((t - delay - beam) / 25f) : 0;
        float open = easeOut(clamp01((t - 4) / 40f));
        float rMain = circle * (0.15f + 0.85f * open);

        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.disc(glow, sky.add(0, 2, 0), X, Z, circle * 2.4f * open, 0, Colors.alpha(a * (0.22f + 0.25f * flare), GOLD));
        ctx.disc(glow, sky.add(0, 1, 0), X, Z, circle * 0.9f * open, 0, Colors.alpha(a * (0.18f + 0.6f * flare), WHITE));

        // the great circle and its inner layers, turning against each other
        var grand = ctx.additive(VfxTextures.GRAND_CIRCLE);
        var sub = ctx.additive(VfxTextures.SUB_CIRCLE);
        ctx.disc(grand, sky, X, Z, rMain, t * 0.0025f, Colors.alpha(a, PALE));
        ctx.disc(grand, sky.add(0, -1.5, 0), X, Z, rMain * 0.985f, -t * 0.0015f, Colors.alpha(a * 0.35f, GOLD));
        float inner = easeOut(clamp01((t - 14) / 30f));
        if (inner > 0) {
            ctx.disc(sub, sky.add(0, -3, 0), X, Z, rMain * 0.58f * inner, -t * 0.006f, Colors.alpha(a * inner, GOLD));
            ctx.disc(grand, sky.add(0, -4.5, 0), X, Z, rMain * 0.3f * inner, t * 0.011f, Colors.alpha(a * inner, WHITE));
        }

        // bands of runes that write themselves around the rim
        var band = ctx.additive(VfxTextures.RUNE_BAND);
        runeRing(ctx, band, sky.add(0, 0.5, 0), circle * 1.03f, circle * 1.1f, -t * 0.002f, clamp01(t / 35f), 18, Colors.alpha(a, PALE));
        runeRing(ctx, band, sky.add(0, 0.8, 0), circle * 1.29f, circle * 1.34f, t * 0.0016f, clamp01((t - 20) / 40f), 26, Colors.alpha(a * 0.85f, GOLD));
        var ring = ctx.additive(VfxTextures.RING);
        int segs = ctx.segments(96);
        ctx.ring(ring, sky.add(0, 0.3, 0), X, Z, circle * 1.115f, circle * 1.13f, segs, Colors.alpha(a * open * 0.9f, WHITE));
        ctx.ring(ring, sky.add(0, 0.3, 0), X, Z, circle * 1.255f, circle * 1.27f, segs, Colors.alpha(a * open * 0.7f, GOLD));
        ctx.ring(ring, sky.add(0, 0.3, 0), X, Z, circle * 1.38f, circle * 1.4f, segs, Colors.alpha(a * clamp01((t - 40) / 20f) * 0.6f, AMBER));

        // two great triangles across the circle
        var line = ctx.additive(VfxTextures.WHITE);
        float tri = easeOut(clamp01((t - 10) / 30f));
        if (tri > 0) {
            for (int k = 0; k < 2; k++) {
                float rot = (k == 0 ? 1 : -1) * t * 0.003f + k * (float) Math.PI;
                triangle(ctx, line, sky.add(0, -0.6, 0), circle * 1.0f * tri, rot, circle * 0.004f, Colors.alpha(a * tri * 0.8f, k == 0 ? PALE : GOLD));
            }
        }

        // thirty-two lesser circles round the rim, each holding a triangle, with triangles between them
        float orbit = circle * 1.195f, size = circle * 0.066f, phase = -t * 0.0018f;
        for (int i = 0; i < SATELLITES; i++) {
            float k = easeOut(clamp01((t - 24 - i * 1.4f) / 10f));
            if (k <= 0) continue;
            float ang = phase + (float) (i * Math.PI * 2 / SATELLITES);
            Vec3 at = sky.add(X.scale(Math.cos(ang) * orbit)).add(Z.scale(Math.sin(ang) * orbit));
            int c = i % 4 == 0 ? WHITE : i % 2 == 0 ? PALE : GOLD;
            ctx.disc(glow, at.add(0, 0.6, 0), X, Z, size * 2.2f * k, 0, Colors.alpha(a * k * (0.2f + 0.4f * flare), c));
            ctx.disc(sub, at, X, Z, size * k, t * 0.03f * (i % 2 == 0 ? 1 : -1) + i, Colors.alpha(a * k, c));
            float ang2 = ang + (float) (Math.PI / SATELLITES);
            Vec3 mid = sky.add(X.scale(Math.cos(ang2) * orbit)).add(Z.scale(Math.sin(ang2) * orbit));
            triangle(ctx, line, mid, size * 0.55f * k, ang2, circle * 0.0025f, Colors.alpha(a * k * 0.85f, GOLD));
        }

        // smaller circles stacked in tiers on top
        for (int k = 1; k <= TIERS; k++) {
            float p = easeOut(clamp01((t - 36 - k * 8) / 18f));
            if (p <= 0) continue;
            float r = circle * new float[]{0.8f, 0.6f, 0.42f, 0.26f}[k - 1];
            Vec3 c = sky.add(0, circle * 0.085f * k * p, 0);
            ctx.disc(k % 2 == 1 ? sub : grand, c, X, Z, r * p, t * 0.004f * (k % 2 == 1 ? -1.4f : 1.7f) * k, Colors.alpha(a * p * (0.85f - k * 0.08f), k % 2 == 1 ? GOLD : PALE));
            ctx.ring(ring, c, X, Z, r * p * 1.02f, r * p * 1.05f, segs / 2, Colors.alpha(a * p * 0.6f, WHITE));
        }
    }

    /** A ring of runes written around a circle, {@code repeats} strips of the band texture, drawn up to {@code progress}. */
    private static void runeRing(VfxContext ctx, VertexConsumer vc, Vec3 c, float rIn, float rOut, float rot, float progress, int repeats, int argb) {
        if (progress <= 0) return;
        float full = (float) (Math.PI * 2), step = full / repeats;
        float upto = full * progress;
        for (int i = 0; i < repeats; i++) {
            float a0 = rot + i * step, a1 = a0 + step;
            if (i * step >= upto) break;
            float cut = Math.min(1, (upto - i * step) / step);
            ctx.arc(vc, c, X, Z, rIn, rOut, a0, a0 + step * cut, 6, (u, outer) -> argb);
        }
    }

    /** A triangle of soft lines in the horizontal plane, its corners on a circle of {@code r}. */
    private static void triangle(VfxContext ctx, VertexConsumer vc, Vec3 c, float r, float rot, float width, int argb) {
        Vec3[] p = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            double a = rot + i * Math.PI * 2 / 3;
            p[i] = c.add(X.scale(Math.cos(a) * r)).add(Z.scale(Math.sin(a) * r));
        }
        for (int i = 0; i < 3; i++) flatLine(ctx, vc, p[i], p[(i + 1) % 3], width, argb);
    }

    /** A line lying in the horizontal plane, soft at its edges. */
    static void flatLine(VfxContext ctx, VertexConsumer vc, Vec3 a, Vec3 b, float width, int argb) {
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(UP);
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(width * 0.5);
        int clear = argb & 0xFFFFFF;
        ctx.quad(vc, a, b, b.add(side), a.add(side), 0, 0, 1, 1, argb, argb, clear, clear);
        ctx.quad(vc, a.subtract(side), b.subtract(side), b, a, 0, 0, 1, 1, clear, clear, argb, argb);
    }

    // ------------------------------------------------------------------------------------------------------------
    // the charge: motes rising into the circle, then the marking beam
    // ------------------------------------------------------------------------------------------------------------

    private void drawCharge(VfxContext ctx, float t) {
        if (t > delay + 4) return;
        float a = clamp01(t / 20f);
        var star = ctx.additive(VfxTextures.STAR);
        int motes = ctx.segments(90);
        for (int i = 0; i < motes; i++) {
            float cyc = ((i * 0.618f) + t * 0.01f) % 1f;
            double ang = i * 2.39996;
            float rr = radius * (0.15f + 1.1f * ((i * 0.37f) % 1f));
            Vec3 p = ground.add(Math.cos(ang) * rr, (sky.y - ground.y) * cyc * cyc, Math.sin(ang) * rr);
            float ma = a * Math.min(1, cyc * 5) * Math.min(1, (1 - cyc) * 4);
            ctx.billboard(star, p, 2.2f + (i % 3), t * 0.1f + i, Colors.alpha(ma * 0.9f, i % 2 == 0 ? PALE : GOLD));
        }
        // the marking beam
        float mark = clamp01((t - delay * 0.6f) / (delay * 0.4f));
        if (mark > 0) {
            boolean flicker = ((int) (t * 1.7f)) % 5 == 0;
            float w = 0.6f + 2.2f * mark * mark;
            shaded(ctx, ctx.translucent(VfxTextures.WHITE), sky, new Vec3(ground.x, bottom, ground.z), w, 12, mark * (flicker ? 0.6f : 1f));
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the beam
    // ------------------------------------------------------------------------------------------------------------

    private void drawBeam(VfxContext ctx, float f) {
        // it falls from the circle in a few ticks, burns, then narrows to a thread and goes out
        float fall = easeIn(clamp01(f / 4f));
        float narrow = clamp01((f - beam) / 14f);
        float out = clamp01((f - beam - 14) / 10f);
        if (out >= 1) return;
        float width = (1 - easeInOut(narrow)) * (1 + 0.01f * (float) Math.sin(f * 0.9f)) + 0.006f;
        double front = sky.y - (sky.y - bottom) * fall;
        Vec3 top = sky.add(0, -2, 0), foot = new Vec3(ground.x, front, ground.z);
        float a = 1 - out;
        float r = radius * 0.9f * width;
        // one pure column of light, nothing see-through about it
        shaded(ctx, ctx.translucent(VfxTextures.WHITE), top, foot, r, ctx.segments(64), a);
        // the air it throws off: rings born at its surface all up its height, racing outward and fading as they spread
        if (narrow >= 1) return;
        var ring = ctx.additive(VfxTextures.RING);
        int rings = ctx.segments(28), segs = ctx.segments(72);
        double span = Math.min(sky.y, ground.y + radius * 1.6) - ground.y;
        for (int k = 0; k < rings; k++) {
            float s = ((f * 0.028f) + k * 0.6180339f) % 1f;
            double y = ground.y + 4 + span * ((k * 0.3819660f) % 1f);
            if (y < front) continue;
            float rr = r + radius * 1.4f * easeOut(s);
            float fadeIn = Math.min(1, s * 8);
            ctx.ring(ring, new Vec3(ground.x, y + s * 6, ground.z), X, Z, rr - 1.5f - 6 * s, rr, segs,
                    Colors.alpha(a * fadeIn * (1 - s) * (1 - s) * 0.55f * (1 - narrow), k % 3 == 0 ? PALE : WHITE));
        }
    }

    /**
     * The clouds the beam drags round itself: rings of billowing cumulus all up its height, a tunnel of cloud round the
     * light, glowing gold where they face it and browner low down. They gather and spiral in as the circle charges,
     * churn and turn slowly while the beam burns, then spread out and thin away.
     */
    private void drawClouds(VfxContext ctx, float t) {
        float gather = easeOut(clamp01((t - delay * 0.35f) / (delay * 0.65f)));
        float spread = clamp01((t - delay - beam) / 50f);
        float a = gather * (1 - spread);
        if (a <= 0.01f) return;
        var cloud = ctx.translucent(VfxTextures.CLOUD);
        float core = radius * 0.9f;
        int rings = ctx.segments(16);
        double span = Math.min(sky.y - 20, ground.y + radius * 2.2) - ground.y;
        for (int k = 0; k < rings; k++) {
            float h = (k + 0.5f) / rings;
            double y = ground.y + 6 + span * h;
            // the tunnel narrows a little upward, and breathes in and out
            float ringR = core * (1.08f + 0.22f * (1 - h)) + radius * 0.5f * (1 - gather) + radius * 0.9f * spread
                    + (float) Math.sin(t * 0.04f + k * 1.7f) * radius * 0.03f;
            float size = radius * (0.22f + 0.12f * (1 - h));
            int puffs = Math.max(10, (int) (ringR * Math.PI * 2 / (size * 0.55f)));
            float turn = t * 0.004f * (k % 2 == 0 ? 1 : -0.7f) + k * 0.61f + (1 - gather) * 2.5f;
            int lit = Colors.lerpRgb(0x9A7048, 0xFFE4AE, 0.35f + 0.65f * h);
            for (int i = 0; i < puffs; i++) {
                double ang = turn + i * Math.PI * 2 / puffs;
                float wob = (float) Math.sin(i * 2.3 + k * 1.1 + t * 0.05) * size * 0.18f;
                Vec3 p = new Vec3(ground.x + Math.cos(ang) * (ringR + wob), y + Math.sin(i * 1.7 + k) * size * 0.25f, ground.z + Math.sin(ang) * (ringR + wob));
                int c = Colors.alpha(a * 0.9f, Colors.scale(lit, 0.85f + 0.15f * (float) Math.sin(i * 3.1 + k)));
                ctx.billboard(cloud, p, size * (0.85f + 0.3f * (float) ((i * 0.618 + k * 0.37) % 1.0)), i * 1.3f + k, c);
            }
        }
    }

    /**
     * A solid column of light from {@code from} to {@code to}: a pure white middle (where it faces the eye) fading out
     * into deep yellow over the rest of its width, so it reads as a white-hot core inside a great yellow beam.
     */
    private static void shaded(VfxContext ctx, VertexConsumer vc, Vec3 from, Vec3 to, float r, int segs, float alpha) {
        if (r <= 0.01f || alpha <= 0) return;
        Vec3 mid = from.add(to).scale(0.5);
        Vec3 view = new Vec3(ctx.cam.x - mid.x, 0, ctx.cam.z - mid.z);
        view = view.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : view.normalize();
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
            Vec3 o0 = new Vec3(Math.cos(a0), 0, Math.sin(a0)), o1 = new Vec3(Math.cos(a1), 0, Math.sin(a1));
            float face = (float) Math.max(0, (o0.dot(view) + o1.dot(view)) * 0.5);
            float core = clamp01((face - 0.5f) / 0.42f);
            int col = Colors.alpha(alpha, Colors.lerpRgb(YELLOW, WHITE, core * core * (3 - 2 * core)));
            ctx.quad(vc, to.add(o0.scale(r)), to.add(o1.scale(r)), from.add(o1.scale(r)), from.add(o0.scale(r)), 0, 0, 1, 1, col, col, col, col);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the ground: the target sigil, then the impact
    // ------------------------------------------------------------------------------------------------------------

    private void drawGround(VfxContext ctx, float t) {
        float mark = clamp01((t - 10) / 30f) * (1 - clamp01((t - delay) / 6f));
        Vec3 g = ground.add(0, 0.8, 0);
        if (mark > 0) {
            var grand = ctx.additive(VfxTextures.GRAND_CIRCLE);
            ctx.disc(grand, g, X, Z, radius * easeOut(clamp01((t - 10) / 40f)), -t * 0.004f, Colors.alpha(mark * 0.5f, GOLD));
            var ring = ctx.additive(VfxTextures.RING);
            float pulse = 0.6f + 0.4f * (float) Math.sin(t * 0.4f);
            ctx.ring(ring, g, X, Z, radius * 0.97f, radius * 1.03f, ctx.segments(96), Colors.alpha(mark * pulse, PALE));
        }
        float f = t - delay;
        if (f < 0) return;
        var glow = ctx.additive(VfxTextures.GLOW);
        float hot = clamp01(1 - (f - beam) / 30f);
        ctx.disc(glow, g, X, Z, radius * 2.2f, 0, Colors.alpha(0.55f * hot, GOLD));
        // shockwaves rolling out over the land
        var shock = ctx.additive(VfxTextures.SHOCKWAVE);
        for (int k = 0; k < 3; k++) {
            float s = (f - k * 9) / 40f;
            if (s < 0 || s > 1) continue;
            float r = radius * (1 + 2.6f * easeOut(s));
            ctx.disc(shock, g.add(0, 1 + k, 0), X, Z, r, k, Colors.alpha((1 - s) * 0.85f, k == 0 ? WHITE : PALE));
        }
        // embers and smoke rising from the shaft once the beam has gone
        float smoke = clamp01((f - beam + 10) / 20f);
        if (smoke > 0) {
            var mist = ctx.translucent(VfxTextures.MIST);
            var star = ctx.additive(VfxTextures.STAR);
            int n = ctx.segments(40);
            for (int i = 0; i < n; i++) {
                float cyc = ((i * 0.618f) + f * 0.008f) % 1f;
                double ang = i * 2.39996;
                float rr = radius * (0.9f + 0.15f * ((i * 0.37f) % 1f));
                Vec3 p = ground.add(Math.cos(ang) * rr, 4 + cyc * 70, Math.sin(ang) * rr);
                float ma = smoke * Math.min(1, cyc * 4) * (1 - cyc);
                ctx.billboard(mist, p, 26 + cyc * 30, i, Colors.alpha(ma * 0.45f, 0x6A6460));
                ctx.billboard(star, p.add(0, -10, 0), 1.6f, i, Colors.alpha(ma, AMBER));
            }
        }
    }
}
