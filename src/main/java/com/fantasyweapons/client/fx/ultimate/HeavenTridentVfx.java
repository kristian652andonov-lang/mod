package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.fx.SkyDarkness;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.weapons.god.HeavenTrident;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * TRIDENT FROM HEAVEN. The sky darkens and tears open high over the target: a jagged wound in the clouds, blazing white
 * and gold inside, its edge crackling, great circles of runes turning round it, shafts of light falling from it to the
 * land, the clouds wheeling round it. A giant trident slowly comes out of it, prongs first - a weapon of gold light, its
 * edges white-hot - then falls along its line onto the target, a trail of light behind it and rings of air bursting off
 * it; where it strikes, a dome of light swells over the crater and shockwaves race out across the land, the trident
 * bursting into shards of light, and the wound in the sky closes.
 */
public class HeavenTridentVfx extends Vfx {
    private static final int WHITE = 0xFFFFFF, PALE = 0xFFF4D6, GOLD = 0xFFD970, AMBER = 0xFFB43A, DEEP = 0xE8A21E;
    private static final Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1), UP = new Vec3(0, 1, 0);
    /** The opening's radius when fully open, and how many points its jagged edge has. */
    private static final float OPENING = 190;
    private static final int RIM = 56, CLOUDS = 60, RAYS = 18, SHARDS = 40;

    private final Vec3 target, sky, f, u, v;
    private final float radius;
    private final int open, emerge, strike;
    private final float[] jag, cloudAng, cloudDist, cloudSpin;
    private final Vec3[] shardDir;

    public HeavenTridentVfx(Vec3 target, Vec3 sky, float radius, int open, int emerge, int strike, long seed) {
        super(strike + 110);
        this.target = target;
        this.sky = sky;
        this.radius = radius;
        this.open = open;
        this.emerge = emerge;
        this.strike = strike;
        this.f = target.subtract(sky).normalize();
        Vec3 side = f.cross(UP);
        this.u = side.lengthSqr() < 1e-6 ? X : side.normalize();
        this.v = u.cross(f).normalize();
        RandomSource r = RandomSource.create(seed);
        // a torn edge: wide lobes, small ragged teeth along them, and here and there a crack running back into the sky
        jag = new float[RIM];
        double p1 = r.nextDouble() * 6.283, p2 = r.nextDouble() * 6.283;
        for (int i = 0; i < RIM; i++) {
            double an = i * Math.PI * 2 / RIM;
            jag[i] = (float) (0.86 + 0.08 * Math.sin(an * 3 + p1) + 0.05 * Math.sin(an * 7 + p2)) + (r.nextFloat() - 0.5f) * 0.06f;
            if (r.nextFloat() < 0.12f) jag[i] += 0.22f + r.nextFloat() * 0.2f;
        }
        cloudAng = new float[CLOUDS];
        cloudDist = new float[CLOUDS];
        cloudSpin = new float[CLOUDS];
        for (int i = 0; i < CLOUDS; i++) {
            cloudAng[i] = r.nextFloat() * 6.283f;
            cloudDist[i] = 1.1f + r.nextFloat() * 1.6f;
            cloudSpin[i] = 0.5f + r.nextFloat();
        }
        shardDir = new Vec3[SHARDS];
        for (int i = 0; i < SHARDS; i++) {
            double a = r.nextDouble() * Math.PI * 2, up = 0.2 + r.nextDouble() * 0.9;
            shardDir[i] = new Vec3(Math.cos(a), up, Math.sin(a)).normalize().scale(4 + r.nextDouble() * 8);
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float end = clamp01((t - strike - 40) / 70f);
        if (ctx.cam.distanceTo(target) < radius * 6 || ctx.cam.distanceTo(sky) < radius * 6) {
            SkyDarkness.request(0.8f * easeInOut(clamp01(t / 60f)) * (1 - end));
        }
        double reach = VfxContext.viewReach();
        ctx.farClamp = reach * 0.86;
        drawOpening(ctx, t);
        drawTrident(ctx, t);
        ctx.farClamp = 0;
        if (t >= strike) drawImpact(ctx, t - strike);
    }

    // ------------------------------------------------------------------------------------------------------------
    // the opening in the sky
    // ------------------------------------------------------------------------------------------------------------

    /** How far open the sky is (0 shut, 1 open), and how it closes after the strike. */
    private float openness(float t) {
        float opening = easeOut(clamp01(t / open));
        float closing = clamp01((t - strike - 20) / 60f);
        return opening * (1 - easeIn(closing));
    }

    private void drawOpening(VfxContext ctx, float t) {
        float k = openness(t);
        if (k <= 0.01f) return;
        float a = clamp01(t / 12f) * (1 - clamp01((t - strike - 70) / 20f));
        // it tears open as a slit first, then gapes
        float ru = OPENING * k, rv = OPENING * (float) Math.pow(k, 1.8) * 0.95f + 4;
        var core = ctx.additive(VfxTextures.WHITE);
        Vec3 c = sky;
        Vec3[] rim = new Vec3[RIM];
        for (int i = 0; i < RIM; i++) {
            double an = i * Math.PI * 2 / RIM;
            float j = jag[i] * (0.95f + 0.05f * (float) Math.sin(t * 0.3f + i));
            rim[i] = c.add(u.scale(Math.cos(an) * ru * j)).add(v.scale(Math.sin(an) * rv * j));
        }
        // the light inside: a soft wash of gold, brighter toward the heart, so the edge reads as torn
        for (int i = 0; i < RIM; i++) {
            Vec3 p0 = rim[i], p1 = rim[(i + 1) % RIM];
            ctx.quad(core, c, c, p0, p1, 0, 0, 1, 1, Colors.alpha(a * 0.4f, PALE), Colors.alpha(a * 0.4f, PALE), Colors.alpha(a * 0.16f, GOLD), Colors.alpha(a * 0.16f, GOLD));
        }
        // heaven beyond it: light turning in a slow whirl, rays from its heart, and a white heart
        Vec3 eu = u.scale(ru * 0.92), ev = v.scale(rv * 0.92);
        var swirl = ctx.additive(VfxTextures.SWIRL);
        ctx.plane(swirl, c.subtract(f.scale(1)), rotate(eu, ev, t * 0.006f), rotate(ev, eu.scale(-1), t * 0.006f), Colors.alpha(a * 0.55f, GOLD));
        ctx.plane(swirl, c.subtract(f.scale(1.5)), rotate(eu, ev, -t * 0.004f + 1).scale(0.7), rotate(ev, eu.scale(-1), -t * 0.004f + 1).scale(0.7), Colors.alpha(a * 0.45f, PALE));
        var flashTex = ctx.additive(VfxTextures.FLASH);
        ctx.plane(flashTex, c.subtract(f.scale(2)), rotate(eu, ev, t * 0.002f), rotate(ev, eu.scale(-1), t * 0.002f), Colors.alpha(a * 0.6f, WHITE));
        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.disc(glow, c.subtract(f.scale(2.5)), u, v, Math.min(ru, rv) * 0.7f, 0, Colors.alpha(a * 0.7f, WHITE));
        ctx.disc(glow, c.subtract(f.scale(2)), u, v, ru * 2.2f, 0, Colors.alpha(a * 0.45f, GOLD));
        ctx.disc(glow, c.subtract(f.scale(3)), u, v, ru * 4.2f, 0, Colors.alpha(a * 0.18f, AMBER));
        // the torn edge, crackling
        var white = ctx.additive(VfxTextures.WHITE);
        for (int i = 0; i < RIM; i++) {
            Vec3 p0 = rim[i], p1 = rim[(i + 1) % RIM];
            ctx.beam(white, p0, p1, 2.6f, Colors.alpha(a, PALE), 0, 1);
            ctx.beam(glow, p0, p1, 12f, Colors.alpha(a * 0.35f, GOLD), 0, 1);
        }
        var bolt = ctx.additive(VfxTextures.LIGHTNING);
        int frame = (int) (t / 2);
        for (int n = 0; n < 6; n++) {
            RandomSource r = RandomSource.create(frame * 977L + n * 131L);
            int i = r.nextInt(RIM);
            Vec3 out = rim[i].subtract(c).normalize();
            ctx.streak(bolt, rim[i].add(out.scale(10)), out.add(f.scale(r.nextDouble() * 0.6)), 30 + r.nextFloat() * 40, 4, Colors.alpha(a * 0.9f, n % 2 == 0 ? WHITE : PALE));
        }
        // great circles of runes turning round the wound
        float rings = easeOut(clamp01((t - 20) / 60f)) * k;
        if (rings > 0) {
            Vec3 back = c.subtract(f.scale(4));
            ctx.disc(ctx.additive(VfxTextures.GRAND_CIRCLE), back, u, v, OPENING * 1.6f * rings, t * 0.003f, Colors.alpha(a * rings * 0.75f, GOLD));
            ctx.disc(ctx.additive(VfxTextures.SUB_CIRCLE), back.subtract(f.scale(2)), u, v, OPENING * 2.1f * rings, -t * 0.002f, Colors.alpha(a * rings * 0.5f, PALE));
            ctx.ring(ctx.additive(VfxTextures.RING), back, u, v, OPENING * 2.3f * rings, OPENING * 2.36f * rings, ctx.segments(128), Colors.alpha(a * rings * 0.8f, WHITE));
            ctx.satellites(ctx.additive(VfxTextures.SUB_CIRCLE), back, u, v, OPENING * 2.05f * rings, OPENING * 0.16f * rings, 12, t * 0.004f, -t * 0.03f,
                    Colors.alpha(a * rings * 0.8f, GOLD));
        }
        // shafts of light falling from it to the land
        var beam = ctx.additive(VfxTextures.BEAM_SOFT);
        for (int i = 0; i < RAYS; i++) {
            double an = i * Math.PI * 2 / RAYS + t * 0.002;
            Vec3 from = c.add(u.scale(Math.cos(an) * ru * 0.6)).add(v.scale(Math.sin(an) * rv * 0.6));
            Vec3 to = target.add(Math.cos(an) * radius * 0.9, 0, Math.sin(an) * radius * 0.9);
            float flick = 0.6f + 0.4f * (float) Math.sin(t * 0.17f + i * 1.7f);
            ctx.softBeam(beam, from, to, 10 + 18 * k, Colors.alpha(a * k * 0.22f * flick, i % 3 == 0 ? WHITE : GOLD), 0, 1, 0.05f, 0.4f);
        }
        // the clouds wheeling round it
        var mist = ctx.translucent(VfxTextures.MIST);
        for (int i = 0; i < CLOUDS; i++) {
            double an = cloudAng[i] + t * 0.004f * cloudSpin[i];
            float rr = OPENING * cloudDist[i];
            Vec3 p = c.add(u.scale(Math.cos(an) * rr)).add(v.scale(Math.sin(an) * rr * 0.9)).subtract(f.scale(10 + i % 5 * 4));
            ctx.billboard(mist, p, OPENING * 0.7f, i + t * 0.001f, Colors.alpha(clamp01(t / 40f) * 0.6f * (1 - clamp01((t - strike - 40) / 60f)), 0x2E2838));
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the trident
    // ------------------------------------------------------------------------------------------------------------

    private void drawTrident(VfxContext ctx, float t) {
        if (t < open) return;
        float gone = clamp01((t - strike) / 10f);
        if (gone >= 1) return;
        Vec3 tips = HeavenTrident.tips(sky, target, open, emerge, strike, t);
        // only what has come out of the sky shows
        double out = tips.subtract(sky).dot(f);
        float a = (1 - gone) * clamp01((t - open) / 10f);
        float L = (float) HeavenTrident.LENGTH;
        var body = ctx.translucent(VfxTextures.WHITE);
        var add = ctx.additive(VfxTextures.WHITE);
        // the middle prong, the two side prongs bending in to the head, and their barbs
        float k = 2.2f;
        prism(ctx, body, add, tips, 0, 0, 0f, 62 * k, 0, 4.6f * k, out, a);
        for (int side = -1; side <= 1; side += 2) {
            prism(ctx, body, add, tips, 14 * k, side * 26 * k, 0f, 40 * k, side * 22 * k, 3.4f * k, out, a);
            prism(ctx, body, add, tips, 40 * k, side * 22 * k, 3.4f * k, 62 * k, side * 18 * k, 3.8f * k, out, a);
            // a barb on each side prong, its point turned back and out
            prism(ctx, body, add, tips, 30 * k, side * 23.5f * k, 2.2f * k, 40 * k, side * 30 * k, 0f, out, a);
        }
        prism(ctx, body, add, tips, 30 * k, 0, 2f * k, 38 * k, 7 * k, 0f, out, a);
        prism(ctx, body, add, tips, 30 * k, 0, 2f * k, 38 * k, -7 * k, 0f, out, a);
        // the head across the prongs, the collar, the shaft and its rings, the pommel
        bar(ctx, body, add, tips, 64 * k, -26 * k, 26 * k, 4.8f * k, out, a);
        prism(ctx, body, add, tips, 66 * k, 0, 7f * k, 76 * k, 0, 5f * k, out, a);
        prism(ctx, body, add, tips, 76 * k, 0, 3.4f * k, L - 14 * k, 0, 3.4f * k, out, a);
        for (float s = 100 * k; s < L - 20 * k; s += 30) prism(ctx, body, add, tips, s, 0, 4.4f * k, s + 4 * k, 0, 4.4f * k, out, a);
        prism(ctx, body, add, tips, L - 14 * k, 0, 3.4f * k, L - 7 * k, 0, 7f * k, out, a);
        prism(ctx, body, add, tips, L - 7 * k, 0, 7f * k, L, 0, 0f, out, a);
        // its glow
        var glow = ctx.additive(VfxTextures.GLOW);
        for (float s = 0; s < L; s += 18) {
            if (s > out) break;
            Vec3 p = tips.subtract(f.scale(s));
            ctx.billboard(glow, p, s < 150 ? 150 : 70, 0, Colors.alpha(a * 0.3f, GOLD));
        }
        ctx.billboard(ctx.additive(VfxTextures.FLASH), tips, 80, t * 0.05f, Colors.alpha(a * 0.8f, WHITE));
        // falling: a trail of light behind it, rings of air bursting off its prongs
        float fall = clamp01((t - open - emerge) / (strike - open - emerge));
        if (fall > 0) {
            Vec3 butt = tips.subtract(f.scale(L));
            ctx.softBeam(ctx.additive(VfxTextures.BEAM_SOFT), sky, butt, 30, Colors.alpha(a * 0.5f * fall, PALE), 0, 1, 0.05f, 0.02f);
            var ring = ctx.additive(VfxTextures.RING);
            for (int n = 0; n < 6; n++) {
                float s = ((t * 0.12f) + n / 6f) % 1f;
                Vec3 at = tips.subtract(f.scale(20 + s * 160));
                ctx.ring(ring, at, u, v, 24 + 70 * s, 30 + 76 * s, 48, Colors.alpha(a * fall * (1 - s) * 0.7f, WHITE));
            }
        }
    }

    /** The axis {@code a} turned by {@code ang} toward {@code b} (both axes of one ellipse). */
    private static Vec3 rotate(Vec3 a, Vec3 b, float ang) {
        return a.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
    }

    /** A point on the trident: {@code s} back from the tips, {@code l} out to the side. */
    private Vec3 at(Vec3 tips, float s, float l) {
        return tips.subtract(f.scale(s)).add(u.scale(l));
    }

    /**
     * A tapered six-sided piece of the trident from (s0, l0) at radius r0 to (s1, l1) at r1, cut off where it has not yet
     * come out of the sky: gold light, its edges white-hot.
     */
    private void prism(VfxContext ctx, VertexConsumer body, VertexConsumer add, Vec3 tips, float s0, float l0, float r0, float s1, float l1, float r1,
                       double out, float a) {
        if (s0 >= out || a <= 0) return;
        if (s1 > out) {
            float k = (float) ((out - s0) / (s1 - s0));
            l1 = l0 + (l1 - l0) * k;
            r1 = r0 + (r1 - r0) * k;
            s1 = (float) out;
        }
        Vec3 p0 = at(tips, s0, l0), p1 = at(tips, s1, l1);
        Vec3 axis = p1.subtract(p0);
        if (axis.lengthSqr() < 1e-6) return;
        Vec3[] b = VfxContext.basis(axis);
        int n = 6;
        Vec3[] q0 = new Vec3[n], q1 = new Vec3[n];
        for (int i = 0; i < n; i++) {
            double an = i * Math.PI * 2 / n;
            Vec3 d = b[0].scale(Math.cos(an)).add(b[1].scale(Math.sin(an)));
            q0[i] = p0.add(d.scale(r0));
            q1[i] = p1.add(d.scale(r1));
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double an = (i + 0.5) * Math.PI * 2 / n;
            Vec3 normal = b[0].scale(Math.cos(an)).add(b[1].scale(Math.sin(an)));
            // lit from above: faces turned up brighter, those underneath a deeper gold
            float lit = (float) (0.55 + 0.45 * normal.y);
            int col = Colors.alpha(a * 0.92f, Colors.lerpRgb(DEEP, PALE, lit));
            ctx.quad(body, q0[i], q0[j], q1[j], q1[i], col);
            ctx.beam(add, q0[i], q1[i], 0.9f, Colors.alpha(a * 0.9f, WHITE), 0, 1);
        }
    }

    /** The head: a bar across the prongs, along the side axis, at {@code s}. */
    private void bar(VfxContext ctx, VertexConsumer body, VertexConsumer add, Vec3 tips, float s, float l0, float l1, float r, double out, float a) {
        if (s - r > out) return;
        Vec3 p0 = at(tips, s, l0), p1 = at(tips, s, l1);
        Vec3 dir = p1.subtract(p0).normalize();
        int n = 6;
        Vec3[] b = VfxContext.basis(dir);
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            Vec3 d0 = b[0].scale(Math.cos(a0)).add(b[1].scale(Math.sin(a0))).scale(r), d1 = b[0].scale(Math.cos(a1)).add(b[1].scale(Math.sin(a1))).scale(r);
            Vec3 nrm = d0.add(d1).normalize();
            int col = Colors.alpha(a * 0.92f, Colors.lerpRgb(DEEP, PALE, (float) (0.55 + 0.45 * nrm.y)));
            ctx.quad(body, p0.add(d0), p1.add(d0), p1.add(d1), p0.add(d1), col);
            ctx.beam(add, p0.add(d0), p1.add(d0), 0.9f, Colors.alpha(a * 0.9f, WHITE), 0, 1);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the strike
    // ------------------------------------------------------------------------------------------------------------

    private void drawImpact(VfxContext ctx, float s) {
        Vec3 g = target.add(0, 1, 0);
        var glow = ctx.additive(VfxTextures.GLOW);
        // the flash, and the dome of light swelling over the crater
        float flash = clamp01(1 - s / 14f);
        if (flash > 0) ctx.billboard(glow, g.add(0, 30, 0), radius * (1.2f + 2.5f * (1 - flash)), 0, Colors.alpha(flash, WHITE));
        float dome = clamp01(s / 18f);
        float domeA = (1 - clamp01((s - 4) / 30f));
        if (domeA > 0) {
            ctx.squash = ctx.squashFor(target, radius * 1.2, VfxContext.viewReach() * 0.8);
            ctx.sphere(ctx.additive(VfxTextures.WHITE), g, radius * 1.1f * easeOut(dome), 18, 36, Colors.alpha(domeA * 0.28f, PALE), true);
            var ringTex = ctx.additive(VfxTextures.RING);
            for (int k = 0; k < 3; k++) {
                float p = clamp01((s - k * 4) / 26f);
                if (p <= 0 || p >= 1) continue;
                ctx.ring(ringTex, g.add(0, 20 + k * 40, 0), X, Z, radius * (0.4f + 1.2f * easeOut(p)), radius * (0.43f + 1.25f * easeOut(p)), 96,
                        Colors.alpha((1 - p) * 0.8f, k == 0 ? WHITE : GOLD));
            }
            ctx.squash = 1;
        }
        // shockwaves racing out across the land
        var shock = ctx.additive(VfxTextures.SHOCKWAVE);
        for (int k = 0; k < 4; k++) {
            float p = (s - k * 7) / 45f;
            if (p < 0 || p > 1) continue;
            ctx.disc(shock, g.add(0, 2 + k * 3, 0), X, Z, radius * (0.5f + 2.6f * easeOut(p)), k, Colors.alpha((1 - p) * 0.9f, k == 0 ? WHITE : k % 2 == 0 ? PALE : GOLD));
        }
        float hot = clamp01(1 - (s - 20) / 70f);
        ctx.disc(glow, g, X, Z, radius * 1.8f, 0, Colors.alpha(0.5f * hot, AMBER));
        // the trident bursting into shards of light
        float burst = clamp01(s / 40f);
        if (burst < 1) {
            var shard = ctx.additive(VfxTextures.SHARD);
            for (int i = 0; i < SHARDS; i++) {
                Vec3 p = g.add(0, 20 + i % 7 * 12, 0).add(shardDir[i].scale(s * 2.2f)).add(0, -s * s * 0.03f, 0);
                ctx.billboard(shard, p, 6 + i % 4 * 3, s * 0.2f + i, Colors.alpha((1 - burst), i % 3 == 0 ? WHITE : GOLD));
            }
        }
        // the light lingering up the path it came down
        float trail = clamp01(1 - s / 50f);
        if (trail > 0) {
            ctx.farClamp = VfxContext.viewReach() * 0.86;
            ctx.softBeam(ctx.additive(VfxTextures.BEAM_SOFT), sky, target, 60 * trail + 8, Colors.alpha(trail * 0.6f, PALE), 0, 1, 0.05f, 0.0f);
            ctx.farClamp = 0;
        }
    }
}
