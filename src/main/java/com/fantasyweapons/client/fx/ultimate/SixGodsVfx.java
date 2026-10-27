package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/**
 * COMBINED POWER OF THE 6 GODS. An enormous magic circle opens in the sky, the circle of Grant Execution grown far
 * larger and drawn in the colours of the fourth dimension - violet and pale light, its lines split red and cyan as the
 * tesseract's edges are. Six circles, each as large as Grant Execution's own, open round it one after another, each in
 * the colour of one power, tilted in toward the middle, turning slowly round it. One by one they pour their power into
 * it in streams of their colours; then the beam falls: a column of every colour at once, the six mixed into a rainbow
 * turning round it, a white-hot thread down its middle and its edges split red and cyan, the six circles still pouring
 * into it - erasing everything under it.
 */
public class SixGodsVfx extends Vfx {
    private static final Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1), UP = new Vec3(0, 1, 0);
    private static final int VOID = 0x9B4DFF, PALE = 0xE2C8FF, RED = 0xFF3050, CYAN = 0x30E8FF, WHITE = 0xFFFFFF;
    private static final int[] POWERS = DivineSummonVfx.POWERS;
    /** How far the six circles lean in toward the middle. */
    private static final double TILT = Math.toRadians(55);
    /** Grant Execution's circle: its radius erased (128) times how much wider its circle is. */
    private static final float SMALL = 128 * 1.45f;
    private static final int AFTER = 90;

    private final Vec3 ground, sky;
    private final float radius, circle, orbit;
    private final int delay, beam;
    private final double bottom;

    public SixGodsVfx(Vec3 ground, float radius, int delay, int beam, double bottom) {
        super(delay + beam + AFTER);
        this.ground = ground;
        this.radius = radius;
        this.delay = delay;
        this.beam = beam;
        this.bottom = bottom;
        this.circle = radius * 1.45f;
        this.orbit = circle * 1.2f + SMALL;
        this.sky = ground.add(0, Math.max(260, radius * 2.3f), 0);
    }

    /** A colour of the rainbow the six powers make, {@code k} round it (0 to 6, wrapping). */
    static int rainbow(float k) {
        float w = ((k % 6) + 6) % 6;
        int i = (int) w;
        return Colors.lerpRgb(POWERS[i], POWERS[(i + 1) % 6], w - i);
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float end = clamp01((t - delay - beam) / AFTER);
        double reach = VfxContext.viewReach();
        if (ctx.cam.distanceTo(ground) < radius * 6) {
            float dark = 0.88f * easeInOut(clamp01(t / (delay * 0.6f))) * (1 - clamp01((t - delay - beam) / 50f));
            if (dark > 0) com.fantasyweapons.client.fx.SkyDarkness.request(dark);
        }
        // the whole of it in view, the six circles at its edge too, from however far off
        ctx.squash = ctx.squashFor(sky, orbit + SMALL * 1.1, reach * 0.8);
        float a = clamp01(t / 16f) * (1 - easeIn(end)) * 0.85f;
        drawGreatCircle(ctx, t, a);
        for (int i = 0; i < 6; i++) drawSmallCircle(ctx, i, t, a);
        ctx.squash = 1;
        ctx.farClamp = reach * 0.86;
        drawStreams(ctx, t, a);
        if (t >= delay) drawBeam(ctx, t - delay);
        ctx.farClamp = 0;
        drawGround(ctx, t);
    }

    // ------------------------------------------------------------------------------------------------------------
    // the circles
    // ------------------------------------------------------------------------------------------------------------

    /** How bright the circles burn: brighter as they fill, blazing while the beam falls. */
    private float flare(float t) {
        if (t >= delay && t < delay + beam) return 1;
        if (t >= delay + beam) return 1 - clamp01((t - delay - beam) / 30f);
        return 0.3f * clamp01((t - delay * 0.55f) / (delay * 0.45f));
    }

    private void drawGreatCircle(VfxContext ctx, float t, float a) {
        if (a <= 0.01f) return;
        float open = easeOut(clamp01((t - 4) / 70f));
        float r = circle * (0.12f + 0.88f * open);
        float flare = flare(t);
        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.disc(glow, sky.add(0, 3, 0), X, Z, circle * 2.6f * open, 0, Colors.alpha(a * (0.2f + 0.3f * flare), VOID));
        ctx.disc(glow, sky.add(0, 2, 0), X, Z, circle * 1.0f * open, 0, Colors.alpha(a * (0.15f + 0.6f * flare), WHITE));
        var grand = ctx.additive(VfxTextures.GRAND_CIRCLE);
        var sub = ctx.additive(VfxTextures.SUB_CIRCLE);
        // the circle's lines split red and cyan, as the tesseract's are
        Vec3 split = new Vec3(ctx.camLeft.x, 0, ctx.camLeft.z);
        split = split.lengthSqr() < 1e-6 ? X : split.normalize();
        split = split.scale(circle * 0.008f);
        ctx.disc(grand, sky.add(split), X, Z, r, t * 0.002f, Colors.alpha(a * 0.5f, RED));
        ctx.disc(grand, sky.subtract(split), X, Z, r, t * 0.002f, Colors.alpha(a * 0.5f, CYAN));
        ctx.disc(grand, sky, X, Z, r * 0.995f, t * 0.002f, Colors.alpha(a, PALE));
        float inner = easeOut(clamp01((t - 16) / 40f));
        if (inner > 0) {
            ctx.disc(sub, sky.add(0, -3, 0), X, Z, r * 0.6f * inner, -t * 0.005f, Colors.alpha(a * inner, VOID));
            ctx.disc(grand, sky.add(0, -5, 0), X, Z, r * 0.32f * inner, t * 0.009f, Colors.alpha(a * inner, WHITE));
            ctx.disc(sub, sky.add(0, -7, 0), X, Z, r * 0.16f * inner, -t * 0.02f, Colors.alpha(a * inner, CYAN));
        }
        var band = ctx.additive(VfxTextures.RUNE_BAND);
        runeRing(ctx, band, sky.add(0, 0.5, 0), circle * 1.03f, circle * 1.1f, -t * 0.0016f, clamp01(t / 40f), 24, Colors.alpha(a, PALE));
        runeRing(ctx, band, sky.add(0, 0.8, 0), circle * 1.32f, circle * 1.38f, t * 0.0012f, clamp01((t - 20) / 50f), 32, Colors.alpha(a * 0.8f, VOID));
        var ring = ctx.additive(VfxTextures.RING);
        int segs = ctx.segments(128);
        ctx.ring(ring, sky.add(split.scale(0.6)), X, Z, circle * 1.12f, circle * 1.135f, segs, Colors.alpha(a * open * 0.8f, RED));
        ctx.ring(ring, sky.subtract(split.scale(0.6)), X, Z, circle * 1.12f, circle * 1.135f, segs, Colors.alpha(a * open * 0.8f, CYAN));
        ctx.ring(ring, sky, X, Z, circle * 1.26f, circle * 1.275f, segs, Colors.alpha(a * open * 0.8f, WHITE));
        // triangles across it, and lesser circles round its rim
        var line = ctx.additive(VfxTextures.WHITE);
        float tri = easeOut(clamp01((t - 10) / 30f));
        for (int k = 0; k < 2 && tri > 0; k++) {
            float rot = (k == 0 ? 1 : -1) * t * 0.0025f + k * (float) Math.PI;
            triangle(ctx, line, sky.add(0, -0.6, 0), circle * tri, rot, circle * 0.004f, Colors.alpha(a * tri * 0.8f, k == 0 ? PALE : VOID));
        }
        int n = 48;
        for (int i = 0; i < n; i++) {
            float k = easeOut(clamp01((t - 24 - i * 0.9f) / 10f));
            if (k <= 0) continue;
            float an = -t * 0.0015f + (float) (i * Math.PI * 2 / n);
            Vec3 at = sky.add(Math.cos(an) * circle * 1.2f, 0, Math.sin(an) * circle * 1.2f);
            ctx.disc(sub, at, X, Z, circle * 0.05f * k, t * 0.03f * (i % 2 == 0 ? 1 : -1) + i, Colors.alpha(a * k, rainbow(i / 8f + t * 0.01f)));
        }
    }

    /** Where the {@code i}th circle is, and how it leans: its middle, and the two axes of its plane. */
    private Vec3[] small(int i, float t) {
        double an = t * 0.0011 + i * Math.PI / 3;
        Vec3 radial = new Vec3(Math.cos(an), 0, Math.sin(an));
        Vec3 tangent = new Vec3(-Math.sin(an), 0, Math.cos(an));
        Vec3 c = sky.add(radial.scale(orbit)).add(0, -orbit * 0.18, 0);
        // a level circle faces the ground; this one is turned in toward the middle
        Vec3 normal = new Vec3(0, -1, 0).scale(Math.cos(TILT)).add(radial.scale(-Math.sin(TILT))).normalize();
        Vec3 axisY = normal.cross(tangent).normalize();
        return new Vec3[]{c, tangent, axisY, normal};
    }

    private void drawSmallCircle(VfxContext ctx, int i, float t, float a) {
        float p = easeOut(clamp01((t - 30 - i * 18) / 40f));
        if (p <= 0 || a <= 0.01f) return;
        Vec3[] s = small(i, t);
        Vec3 c = s[0], ax = s[1], ay = s[2], n = s[3];
        int col = POWERS[i], light = Colors.lerpRgb(col, WHITE, 0.55f);
        float r = SMALL * p;
        float flare = flare(t);
        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.disc(glow, c.subtract(n.scale(2)), ax, ay, r * 2.2f, 0, Colors.alpha(a * p * (0.25f + 0.35f * flare), col));
        ctx.disc(ctx.additive(VfxTextures.GRAND_CIRCLE), c, ax, ay, r, t * 0.004f * (i % 2 == 0 ? 1 : -1), Colors.alpha(a * p, light));
        ctx.disc(ctx.additive(VfxTextures.SUB_CIRCLE), c.add(n.scale(1.5)), ax, ay, r * 0.58f, -t * 0.008f, Colors.alpha(a * p, col));
        ctx.disc(ctx.additive(VfxTextures.RUNE_CIRCLE), c.add(n.scale(3)), ax, ay, r * 0.3f, t * 0.015f, Colors.alpha(a * p, WHITE));
        ctx.ring(ctx.additive(VfxTextures.RING), c, ax, ay, r * 1.1f, r * 1.13f, ctx.segments(96), Colors.alpha(a * p * 0.9f, light));
        runeRing(ctx, ctx.additive(VfxTextures.RUNE_BAND), c, ax, ay, r * 1.15f, r * 1.24f, t * 0.003f, p, 14, Colors.alpha(a * p, col));
        ctx.satellites(ctx.additive(VfxTextures.SUB_CIRCLE), c, ax, ay, r * 1.36f, r * 0.08f, 8, -t * 0.004f, t * 0.04f, Colors.alpha(a * p * 0.85f, light));
    }

    /** Each circle pours its power into the middle in a stream of its colour; while the beam falls, into the beam. */
    private void drawStreams(VfxContext ctx, float t, float a) {
        float fill = clamp01((t - delay * 0.55f) / (delay * 0.3f));
        if (fill <= 0 || a <= 0.01f) return;
        boolean firing = t >= delay;
        double low = Math.max(bottom, ground.y);
        var beamTex = ctx.additive(VfxTextures.BEAM_SOFT);
        var glow = ctx.additive(VfxTextures.GLOW);
        var star = ctx.additive(VfxTextures.STAR);
        for (int i = 0; i < 6; i++) {
            float own = clamp01((t - delay * 0.55f - i * 8) / 30f);
            if (own <= 0) continue;
            Vec3[] s = small(i, t);
            Vec3 from = s[0];
            Vec3 into = firing ? new Vec3(sky.x, low + (sky.y - low) * 0.42, sky.z) : sky.add(0, -10, 0);
            int col = POWERS[i];
            float w = SMALL * (firing ? 0.32f : 0.14f + 0.1f * own);
            float flick = 0.85f + 0.15f * (float) Math.sin(t * 0.4f + i);
            ctx.softBeam(beamTex, from, into, w, Colors.alpha(a * own * flick, col), -t * 0.02f, 1 - t * 0.02f, 0.02f, 0.1f);
            ctx.softBeam(glow, from, into, w * 3, Colors.alpha(a * own * 0.3f, col), 0, 1, 0.02f, 0.1f);
            ctx.softBeam(beamTex, from, into, w * 0.25f, Colors.alpha(a * own, WHITE), 0, 1, 0.02f, 0.15f);
            // motes racing along it
            Vec3 d = into.subtract(from);
            for (int m = 0; m < 10; m++) {
                float cyc = (t * 0.02f + m * 0.1f + i * 0.13f) % 1f;
                ctx.billboard(star, from.add(d.scale(cyc)), w * 0.25f, t * 0.1f + m, Colors.alpha(a * own * (1 - cyc), m % 2 == 0 ? WHITE : col));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the beam
    // ------------------------------------------------------------------------------------------------------------

    private void drawBeam(VfxContext ctx, float f) {
        float fall = easeIn(clamp01(f / 6f));
        float narrow = clamp01((f - beam) / 18f);
        float out = clamp01((f - beam - 18) / 12f);
        if (out >= 1) return;
        float width = (1 - easeInOut(narrow)) * (1 + 0.01f * (float) Math.sin(f * 0.7f)) + 0.005f;
        double front = sky.y - (sky.y - bottom) * fall;
        Vec3 top = sky.add(0, -2, 0), foot = new Vec3(ground.x, front, ground.z);
        float a = 1 - out;
        float r = radius * 0.92f * width;
        rainbowColumn(ctx, top, foot, r, f, a);
        if (narrow >= 1) return;
        float live = a * (1 - narrow);
        double low = Math.max(foot.y, ground.y);
        spirals(ctx, top, low, r, f, live);
        runes(ctx, top, low, r, f, live);
        // the air it throws off: rings racing outward all up its height
        var ring = ctx.additive(VfxTextures.RING);
        int rings = ctx.segments(30), segs = ctx.segments(96);
        double span = Math.min(sky.y, ground.y + radius * 1.6) - ground.y;
        for (int k = 0; k < rings; k++) {
            float s = ((f * 0.024f) + k * 0.6180339f) % 1f;
            double y = ground.y + 4 + span * ((k * 0.3819660f) % 1f);
            if (y < front) continue;
            float rr = r + radius * 1.2f * easeOut(s);
            ctx.ring(ring, new Vec3(ground.x, y + s * 8, ground.z), X, Z, rr - 2 - 10 * s, rr, segs,
                    Colors.alpha(live * Math.min(1, s * 8) * (1 - s) * (1 - s) * 0.5f, rainbow(k + f * 0.05f)));
        }
        if (front <= ground.y) splash(ctx, r, f, live);
    }

    /**
     * The column: every colour at once, the six powers' colours turning round it as a rainbow, a white-hot thread down its
     * middle (as the eye sees it), and its two edges split red and cyan like the tesseract's.
     */
    private void rainbowColumn(VfxContext ctx, Vec3 from, Vec3 to, float r, float f, float alpha) {
        if (r <= 0.01f || alpha <= 0) return;
        Vec3 mid = from.add(to).scale(0.5);
        Vec3 view = new Vec3(ctx.cam.x - mid.x, 0, ctx.cam.z - mid.z);
        view = view.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : view.normalize();
        Vec3 side = new Vec3(-view.z, 0, view.x);
        int segs = Math.max(96, ctx.segments(128));
        int[] cols = new int[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double ang = i * Math.PI * 2 / segs;
            double ox = Math.cos(ang), oz = Math.sin(ang);
            float across = (float) Math.abs(ox * side.x + oz * side.z);
            boolean front = ox * view.x + oz * view.z > 0;
            int col = rainbow((float) (ang / (Math.PI * 2) * 6 + f * 0.08f));
            col = Colors.lerpRgb(col, WHITE, 0.25f * (1 - across));
            if (front) {
                float core = 1 - clamp01((across - 0.04f) / 0.12f);
                col = Colors.lerpRgb(col, WHITE, core * core * (3 - 2 * core));
            }
            cols[i] = Colors.alpha(alpha * 0.75f, col);
        }
        VertexConsumer vc = ctx.translucent(VfxTextures.WHITE);
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
            Vec3 o0 = new Vec3(Math.cos(a0), 0, Math.sin(a0)), o1 = new Vec3(Math.cos(a1), 0, Math.sin(a1));
            ctx.quad(vc, to.add(o0.scale(r)), to.add(o1.scale(r)), from.add(o1.scale(r)), from.add(o0.scale(r)), 0, 0, 1, 1,
                    cols[i], cols[i + 1], cols[i + 1], cols[i]);
        }
        // the edges split red and cyan, a little outside the column
        var add = ctx.additive(VfxTextures.BEAM_SOFT);
        float edge = Math.max(3, r * 0.04f);
        Vec3 left = side.scale(r * 1.02 + edge * 0.5), right = side.scale(-(r * 1.02 + edge * 0.5));
        ctx.softBeam(add, from.add(left), to.add(left), edge, Colors.alpha(alpha * 0.8f, RED), 0, 1, 0.02f, 0.02f);
        ctx.softBeam(add, from.add(right), to.add(right), edge, Colors.alpha(alpha * 0.8f, CYAN), 0, 1, 0.02f, 0.02f);
        ctx.softBeam(add, from, to, Math.max(6, r * 0.12f), Colors.alpha(alpha, WHITE), 0, 1, 0.02f, 0.02f);
    }

    /** Six bands of the six colours winding down round it. */
    private void spirals(VfxContext ctx, Vec3 top, double low, float r, float f, float alpha) {
        var vc = ctx.energy(VfxTextures.STREAK);
        Vec3 view = new Vec3(ctx.cam.x - top.x, 0, ctx.cam.z - top.z);
        view = view.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : view.normalize();
        double h = top.y - low;
        int n = Math.max(24, ctx.segments(90));
        double pitch = Math.max(r * 3, 60);
        for (int k = 0; k < 6; k++) {
            Vec3[] pts = new Vec3[n + 1];
            float[] widths = new float[n + 1];
            int[] cols = new int[n + 1];
            for (int i = 0; i <= n; i++) {
                double down = h * i / n;
                double ang = k * Math.PI / 3 + down / pitch * Math.PI * 2 - f * 0.07;
                double ox = Math.cos(ang), oz = Math.sin(ang);
                float rr = r * 1.05f + 2;
                pts[i] = new Vec3(top.x + ox * rr, top.y - down, top.z + oz * rr);
                float ends = Math.min(1, Math.min(i / (n * 0.06f), (n - i) / (n * 0.04f)));
                float facing = ox * view.x + oz * view.z > 0 ? 1 : 0.28f;
                widths[i] = Math.max(2f, r * 0.07f) * (0.5f + 0.5f * ends);
                cols[i] = Colors.alpha(alpha * 0.9f * ends * facing, POWERS[k]);
            }
            ctx.ribbon(vc, pts, widths, cols, -f * 0.04f, (float) (h / n / Math.max(6, r * 0.5)));
        }
    }

    /** Rune circles of every colour sliding down it, turning each its own way. */
    private void runes(VfxContext ctx, Vec3 top, double low, float r, float f, float alpha) {
        var rune = ctx.additive(VfxTextures.RUNE_CIRCLE);
        var sub = ctx.additive(VfxTextures.SUB_CIRCLE);
        double h = top.y - low;
        for (int k = 0; k < 6; k++) {
            float s = (f * 0.01f + k / 6f) % 1f;
            float k1 = (float) Math.sin(Math.PI * s);
            if (k1 <= 0.02f) continue;
            Vec3 c = new Vec3(top.x, top.y - h * s * s, top.z);
            ctx.disc(k % 2 == 0 ? rune : sub, c, X, Z, r * 1.5f, f * 0.02f * (k % 2 == 0 ? 1 : -1) + k, Colors.alpha(alpha * k1 * 0.8f, POWERS[k]));
        }
    }

    /** Sparks of every colour spraying from the rim where it bites. */
    private void splash(VfxContext ctx, float r, float f, float alpha) {
        var spark = ctx.additive(VfxTextures.SPARK);
        int n = ctx.segments(60);
        for (int i = 0; i < n; i++) {
            float cyc = ((i * 0.618034f) + f * 0.03f) % 1f;
            double ang = i * 2.39996;
            Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 dir = out.add(0, 0.4 + 0.6 * ((i * 0.37f) % 1f), 0).normalize();
            Vec3 p = ground.add(out.scale(r)).add(dir.scale(cyc * (24 + r * 0.2f))).add(0, -cyc * cyc * 10, 0);
            ctx.streak(spark, p, dir, 8 + r * 0.03f, 2f, Colors.alpha(alpha * (1 - cyc), i % 3 == 0 ? WHITE : POWERS[i % 6]));
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the ground
    // ------------------------------------------------------------------------------------------------------------

    private void drawGround(VfxContext ctx, float t) {
        ctx.squash = ctx.squashFor(ground, radius, VfxContext.viewReach() * 0.85);
        Vec3 g = ground.add(0, 0.8, 0);
        float mark = clamp01((t - 10) / 40f) * (1 - clamp01((t - delay) / 6f));
        if (mark > 0) {
            ctx.disc(ctx.additive(VfxTextures.GRAND_CIRCLE), g, X, Z, radius * easeOut(clamp01((t - 10) / 60f)), -t * 0.003f, Colors.alpha(mark * 0.45f, PALE));
            float pulse = 0.6f + 0.4f * (float) Math.sin(t * 0.3f);
            ctx.ring(ctx.additive(VfxTextures.RING), g, X, Z, radius * 0.98f, radius * 1.02f, ctx.segments(160), Colors.alpha(mark * pulse, rainbow(t * 0.03f)));
        }
        float f = t - delay;
        if (f >= 0) {
            var glow = ctx.additive(VfxTextures.GLOW);
            float hot = clamp01(1 - (f - beam) / 40f);
            ctx.disc(glow, g, X, Z, radius * 2.2f, 0, Colors.alpha(0.5f * hot, VOID));
            var shock = ctx.additive(VfxTextures.SHOCKWAVE);
            for (int k = 0; k < 6; k++) {
                float s = (f - k * 8) / 55f;
                if (s < 0 || s > 1) continue;
                ctx.disc(shock, g.add(0, 1 + k, 0), X, Z, radius * (1 + 1.8f * easeOut(s)), k, Colors.alpha((1 - s) * 0.85f, POWERS[k]));
            }
        }
        ctx.squash = 1;
    }

    // ------------------------------------------------------------------------------------------------------------
    // drawing helpers
    // ------------------------------------------------------------------------------------------------------------

    private static void runeRing(VfxContext ctx, VertexConsumer vc, Vec3 c, float rIn, float rOut, float rot, float progress, int repeats, int argb) {
        runeRing(ctx, vc, c, X, Z, rIn, rOut, rot, progress, repeats, argb);
    }

    /** A ring of runes written round a circle in the plane of {@code ax}, {@code ay}, drawn up to {@code progress}. */
    private static void runeRing(VfxContext ctx, VertexConsumer vc, Vec3 c, Vec3 ax, Vec3 ay, float rIn, float rOut, float rot, float progress, int repeats,
                                 int argb) {
        if (progress <= 0) return;
        float full = (float) (Math.PI * 2), step = full / repeats, upto = full * progress;
        for (int i = 0; i < repeats; i++) {
            float a0 = rot + i * step;
            if (i * step >= upto) break;
            float cut = Math.min(1, (upto - i * step) / step);
            ctx.runeBand(vc, c, ax, ay, rIn, rOut, a0, a0 + step, cut, 6, i, argb);
        }
    }

    private static void triangle(VfxContext ctx, VertexConsumer vc, Vec3 c, float r, float rot, float width, int argb) {
        Vec3[] p = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            double a = rot + i * Math.PI * 2 / 3;
            p[i] = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
        }
        for (int i = 0; i < 3; i++) GrantExecutionVfx.flatLine(ctx, vc, p[i], p[(i + 1) % 3], width, argb);
    }
}
