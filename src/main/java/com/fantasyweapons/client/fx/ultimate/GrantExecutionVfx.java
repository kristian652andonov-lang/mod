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
 * falls - a column of golden mana as wide as the land it erases, a white-hot thread down its middle, streams of light
 * pouring down its skin, arcs of power crackling over it, bands of power winding round it and rune circles sliding down
 * it - pulses racing down,
 * sparks spraying from where it bites, shockwaves rolling out over the ground; at the end it narrows to a thread and the
 * circle comes apart.
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
        ctx.squash = ctx.squashFor(sky, circle * 1.5, reach * 0.8);
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
        float width = (1 - easeInOut(narrow)) * (1 + 0.012f * (float) Math.sin(f * 0.9f)) + 0.006f;
        double front = sky.y - (sky.y - bottom) * fall;
        Vec3 top = sky.add(0, -2, 0), foot = new Vec3(ground.x, front, ground.z);
        float a = 1 - out;
        float r = radius * 0.9f * width;
        // the column itself: golden and see-through, a thin white-hot thread down its middle
        shaded(ctx, ctx.translucent(VfxTextures.WHITE), top, foot, r, ctx.segments(64), a * 0.7f);
        // the mana pouring down it: living streams of light flowing over its skin, brightest at its edges
        manaSkin(ctx, top, foot, r * 1.012f, f, a);
        if (narrow >= 1) return;
        float live = a * (1 - narrow);
        double low = Math.max(foot.y, ground.y);
        // arcs of raw power crackling over its skin
        crackle(ctx, top, low, r, f, live);
        // three bands of power winding down round it
        spirals(ctx, top, low, r, f, live);
        // rune circles sliding down it from the great circle to the ground, turning as they go
        runes(ctx, top, low, r, f, live);
        // pulses of light racing down its skin, and motes of mana streaming down with them
        pulses(ctx, top, low, r, f, live);
        // the air it throws off: rings born at its surface all up its height, racing outward and fading as they spread
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
        // where it bites into the ground, sparks of mana spray out from its rim
        if (front <= ground.y) splash(ctx, r, f, live);
    }

    /**
     * Streams of mana flowing down the column's skin: the energy shader's noise scrolls along it, so bright veins of
     * light pour downward; strongest at the edges as seen (where the skin is seen edge-on), fading into the circle above.
     */
    private void manaSkin(VfxContext ctx, Vec3 top, Vec3 foot, float r, float f, float alpha) {
        if (r <= 0.05f || alpha <= 0) return;
        var vc = ctx.energy(VfxTextures.WHITE);
        Vec3 view = new Vec3(ctx.cam.x - top.x, 0, ctx.cam.z - top.z);
        view = view.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : view.normalize();
        Vec3 side = new Vec3(-view.z, 0, view.x);
        int segs = Math.max(48, ctx.segments(72));
        double h = top.y - foot.y;
        // texture repeats: features about half the beam across, round it and down it
        float tile = Math.max(4, r * 0.9f);
        float around = (float) (Math.PI * 2 * r / tile);
        int rows = 6;
        for (int j = 0; j < rows; j++) {
            double y0 = top.y - h * j / rows, y1 = top.y - h * (j + 1) / rows;
            float u0 = (float) ((top.y - y0) / tile), u1 = (float) ((top.y - y1) / tile);
            // it melts into the circle at the top
            float k0 = j == 0 ? 0 : 1, k1 = 1;
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                // only the near half: the far half seen through it would double the light down the middle
                if (Math.cos((a0 + a1) / 2) * view.x + Math.sin((a0 + a1) / 2) * view.z < 0) continue;
                int c0 = skin(a0, view, side, alpha), c1 = skin(a1, view, side, alpha);
                Vec3 o0 = new Vec3(Math.cos(a0) * r, 0, Math.sin(a0) * r), o1 = new Vec3(Math.cos(a1) * r, 0, Math.sin(a1) * r);
                float v0 = around * i / segs, v1 = around * (i + 1) / segs;
                ctx.vertex(vc, new Vec3(top.x, y1, top.z).add(o0), u1, v0, Colors.scaleAlpha(c0, k1));
                ctx.vertex(vc, new Vec3(top.x, y1, top.z).add(o1), u1, v1, Colors.scaleAlpha(c1, k1));
                ctx.vertex(vc, new Vec3(top.x, y0, top.z).add(o1), u0, v1, Colors.scaleAlpha(c1, k0));
                ctx.vertex(vc, new Vec3(top.x, y0, top.z).add(o0), u0, v0, Colors.scaleAlpha(c0, k0));
            }
        }
    }

    private static int skin(double ang, Vec3 view, Vec3 side, float alpha) {
        double ox = Math.cos(ang), oz = Math.sin(ang);
        float across = (float) Math.abs(ox * side.x + oz * side.z);
        float edge = (float) Math.pow(across, 1.6);
        // added light in the gold of the column, mostly towards its edges: the streams read as bright veins pouring down
        // it, and its middle stays yellow round the thin white thread
        return Colors.alpha(alpha * (0.07f + 0.55f * edge), Colors.lerpRgb(AMBER, GOLD, 0.2f + 0.6f * edge));
    }

    /**
     * Arcs of raw power crackling over the column's skin: short forked bolts on the side facing the eye, each living a
     * few ticks in one place before it jumps to another.
     */
    private void crackle(VfxContext ctx, Vec3 top, double low, float r, float f, float alpha) {
        var vc = ctx.additive(VfxTextures.LIGHTNING);
        Vec3 view = new Vec3(ctx.cam.x - top.x, 0, ctx.cam.z - top.z);
        view = view.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : view.normalize();
        double h = top.y - low, base = Math.atan2(view.z, view.x);
        int n = ctx.segments(14), life = 4;
        for (int i = 0; i < n; i++) {
            int gen = (int) (f / life) + i * 7;
            net.minecraft.util.RandomSource rng = net.minecraft.util.RandomSource.create(gen * 341873128712L + i * 132897987541L);
            float age = (f % life) / life;
            double ang = base + (rng.nextDouble() - 0.5) * Math.PI * 0.9;
            double y = low + h * (0.05 + 0.9 * rng.nextDouble());
            double len = Math.max(8, r * (0.5 + rng.nextDouble()));
            Vec3 at = new Vec3(top.x + Math.cos(ang) * r * 1.02, y, top.z + Math.sin(ang) * r * 1.02);
            // mostly down the column, a little round it
            Vec3 dir = new Vec3(-Math.sin(ang) * (rng.nextDouble() - 0.5), -1, Math.cos(ang) * (rng.nextDouble() - 0.5));
            float k = alpha * (1 - age) * (0.6f + 0.4f * rng.nextFloat());
            ctx.streak(vc, at, dir, (float) len, Math.max(1.5f, r * 0.05f), Colors.alpha(k, i % 3 == 0 ? WHITE : PALE));
        }
    }

    /** Bands of power winding down round the column, turning as they fall; dimmer where they pass behind it. */
    private void spirals(VfxContext ctx, Vec3 top, double low, float r, float f, float alpha) {
        var vc = ctx.energy(VfxTextures.STREAK);
        Vec3 view = new Vec3(ctx.cam.x - top.x, 0, ctx.cam.z - top.z);
        view = view.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : view.normalize();
        double h = top.y - low;
        int n = Math.max(24, ctx.segments(90));
        double pitch = Math.max(r * 3.2, 40);
        for (int k = 0; k < 3; k++) {
            Vec3[] pts = new Vec3[n + 1];
            float[] widths = new float[n + 1];
            int[] cols = new int[n + 1];
            for (int i = 0; i <= n; i++) {
                double down = h * i / n;
                double ang = k * Math.PI * 2 / 3 + down / pitch * Math.PI * 2 - f * 0.09;
                double ox = Math.cos(ang), oz = Math.sin(ang);
                float rr = r * 1.05f + 1.2f;
                pts[i] = new Vec3(top.x + ox * rr, top.y - down, top.z + oz * rr);
                float ends = Math.min(1, Math.min(i / (n * 0.06f), (n - i) / (n * 0.04f)));
                float facing = ox * view.x + oz * view.z > 0 ? 1 : 0.28f;
                widths[i] = Math.max(1.2f, r * 0.075f) * (0.5f + 0.5f * ends);
                cols[i] = Colors.alpha(alpha * 0.85f * ends * facing, k == 0 ? WHITE : PALE);
            }
            ctx.ribbon(vc, pts, widths, cols, -f * 0.04f, (float) (h / n / Math.max(6, r * 0.5)));
        }
    }

    /** Rune circles sliding down the column, from the great circle to the ground, each turning its own way. */
    private void runes(VfxContext ctx, Vec3 top, double low, float r, float f, float alpha) {
        var rune = ctx.additive(VfxTextures.RUNE_CIRCLE);
        var sub = ctx.additive(VfxTextures.SUB_CIRCLE);
        var ring = ctx.additive(VfxTextures.RING);
        double h = top.y - low;
        int n = 6, segs = ctx.segments(72);
        for (int k = 0; k < n; k++) {
            float s = (f * 0.011f + k / (float) n) % 1f;
            float k1 = (float) Math.sin(Math.PI * s);
            if (k1 <= 0.02f) continue;
            Vec3 c = new Vec3(top.x, top.y - h * s * s, top.z);
            float size = r * (1.45f + 0.15f * (k % 2));
            float spin = f * 0.02f * (k % 2 == 0 ? 1 : -1) + k;
            ctx.disc(k % 2 == 0 ? rune : sub, c, X, Z, size, spin, Colors.alpha(alpha * k1 * 0.75f, k % 3 == 0 ? WHITE : GOLD));
            ctx.ring(ring, c.add(0, 0.2, 0), X, Z, size * 1.02f, size * 1.07f, segs, Colors.alpha(alpha * k1 * 0.55f, PALE));
        }
    }

    /** Pulses of light racing down the column's skin, and motes of mana streaming down with them. */
    private void pulses(VfxContext ctx, Vec3 top, double low, float r, float f, float alpha) {
        var ring = ctx.additive(VfxTextures.RING);
        double h = top.y - low;
        int segs = ctx.segments(72);
        for (int k = 0; k < 4; k++) {
            float s = (f * 0.045f + k * 0.25f) % 1f;
            Vec3 c = new Vec3(top.x, top.y - h * s, top.z);
            ctx.ring(ring, c, X, Z, r * 0.99f, r * 1.1f, segs, Colors.alpha(alpha * 0.8f * (1 - s * 0.6f), WHITE));
        }
        var star = ctx.additive(VfxTextures.STAR);
        int motes = ctx.segments(70);
        for (int i = 0; i < motes; i++) {
            float cyc = ((i * 0.618034f) + f * 0.018f) % 1f;
            double ang = i * 2.39996 + cyc * 5 - f * 0.05;
            float rr = r * (1.03f + 0.12f * ((i * 0.37f) % 1f));
            Vec3 p = new Vec3(top.x + Math.cos(ang) * rr, top.y - h * cyc, top.z + Math.sin(ang) * rr);
            float ma = Math.min(1, cyc * 6) * Math.min(1, (1 - cyc) * 5);
            ctx.billboard(star, p, 2.2f + r * 0.03f + (i % 3), f * 0.1f + i, Colors.alpha(alpha * ma, i % 3 == 0 ? WHITE : PALE));
        }
    }

    /** Sparks of mana spraying out from the rim where the column bites into the ground. */
    private void splash(VfxContext ctx, float r, float f, float alpha) {
        var spark = ctx.additive(VfxTextures.SPARK);
        int n = ctx.segments(48);
        for (int i = 0; i < n; i++) {
            float cyc = ((i * 0.618034f) + f * 0.035f) % 1f;
            double ang = i * 2.39996;
            Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            float lift = 0.4f + 0.6f * ((i * 0.37f) % 1f);
            Vec3 dir = out.add(0, lift, 0).normalize();
            Vec3 p = ground.add(out.scale(r)).add(dir.scale(cyc * (18 + r * 0.3f))).add(0, -cyc * cyc * 8, 0);
            ctx.streak(spark, p, dir, 6 + r * 0.05f, 1.4f, Colors.alpha(alpha * (1 - cyc), i % 2 == 0 ? WHITE : GOLD));
        }
    }

    /**
     * A solid column of light from {@code from} to {@code to}: deep yellow, a little deeper towards its edges so it reads
     * round, with a thin white-hot thread down its middle (as the eye sees it) melting into the yellow.
     */
    private static void shaded(VfxContext ctx, VertexConsumer vc, Vec3 from, Vec3 to, float r, int segs, float alpha) {
        if (r <= 0.01f || alpha <= 0) return;
        Vec3 mid = from.add(to).scale(0.5);
        Vec3 view = new Vec3(ctx.cam.x - mid.x, 0, ctx.cam.z - mid.z);
        view = view.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : view.normalize();
        Vec3 side = new Vec3(-view.z, 0, view.x);
        segs = Math.max(segs, 96);
        int[] cols = new int[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double ang = i * Math.PI * 2 / segs;
            double ox = Math.cos(ang), oz = Math.sin(ang);
            // how far across the beam this edge lies, as seen (0 the middle, 1 its rim), and whether it faces the eye
            float across = (float) Math.abs(ox * side.x + oz * side.z);
            boolean front = ox * view.x + oz * view.z > 0;
            int col = Colors.lerpRgb(YELLOW, AMBER, clamp01((across - 0.55f) / 0.45f));
            if (front) {
                float core = 1 - clamp01((across - 0.05f) / 0.12f);
                col = Colors.lerpRgb(col, WHITE, core * core * (3 - 2 * core));
            }
            cols[i] = Colors.alpha(alpha, col);
        }
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
            Vec3 o0 = new Vec3(Math.cos(a0), 0, Math.sin(a0)), o1 = new Vec3(Math.cos(a1), 0, Math.sin(a1));
            ctx.quad(vc, to.add(o0.scale(r)), to.add(o1.scale(r)), from.add(o1.scale(r)), from.add(o0.scale(r)), 0, 0, 1, 1,
                    cols[i], cols[i + 1], cols[i + 1], cols[i]);
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
