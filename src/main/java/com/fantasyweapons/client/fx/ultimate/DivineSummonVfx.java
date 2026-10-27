package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.fx.CameraShake;
import com.fantasyweapons.client.fx.SkyDarkness;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A power of the Weapon of God being summoned. Round the caster, lifted into the air: a great circle drawn on the ground
 * under them, writing its runes as it turns, six lesser circles round it in the colours of the six powers; the six powers'
 * lights circling them, nearer and faster as the power comes; a crown of light over their head and, seen from outside, a
 * vast turning mandala behind them; light drawn in from all round; and a pillar of light climbing out of the blade into
 * the sky, thickening. Over the place it will strike, the clouds turn in a slow vortex round a shaft of light, and the
 * ground there is marked as wide as the power. At the last, the six lights fly into the blade - and it is loosed.
 */
public class DivineSummonVfx extends Vfx {
    static final int[] POWERS = {0xFFD870, 0xA066FF, 0xFF5A1E, 0xD49A52, 0x7BDA62, 0x9EE6FF};
    private static final int WHITE = 0xFFFFFF, PALE = 0xFFF4D6, GOLD = 0xFFD970;
    private static final Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1), UP = new Vec3(0, 1, 0);
    private static final int MOTES = 80, CLOUDS = 46;

    private final int caster;
    private final Vec3 start, target;
    private final float size;
    private final int ticks, color;
    private final float[] ang, dist, rise, spin;

    public DivineSummonVfx(int caster, Vec3 feet, Vec3 target, float size, int ticks, int color, long seed) {
        super(ticks + 16);
        this.caster = caster;
        this.start = feet;
        this.target = target;
        this.size = size;
        this.ticks = ticks;
        this.color = color;
        RandomSource r = RandomSource.create(seed);
        int n = MOTES + CLOUDS;
        ang = new float[n];
        dist = new float[n];
        rise = new float[n];
        spin = new float[n];
        for (int i = 0; i < n; i++) {
            ang[i] = r.nextFloat() * 6.283f;
            dist[i] = 0.35f + r.nextFloat() * 0.65f;
            rise[i] = r.nextFloat();
            spin[i] = 0.6f + r.nextFloat() * 0.8f;
        }
    }

    /** Where the caster is now (they rise as they summon), else where they stood. */
    private Vec3 body(float partial) {
        var level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(caster);
        return e == null ? start : e.getPosition(partial);
    }

    @Override
    public void tick() {
        super.tick();
        float k = age / (float) ticks;
        if (age % 4 == 0 && age < ticks) {
            CameraShake.add(start, 0.2f + 1.4f * k * k, 120);
            if (target.distanceTo(start) > 30) CameraShake.add(target, 0.3f + 1.8f * k * k, size * 2.5);
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float k = clamp01(t / ticks);
        float tight = clamp01((t - (ticks - 24)) / 24f);
        float fade = 1 - clamp01((t - ticks) / 14f);
        if (fade <= 0) return;
        double near = Math.min(ctx.cam.distanceTo(start), ctx.cam.distanceTo(target));
        if (near < Math.max(260, size * 4)) SkyDarkness.request(0.85f * easeInOut(clamp01(t / (ticks * 0.6f))) * fade);
        Vec3 body = body(ctx.partial);
        Vec3 chest = body.add(0, 1.3, 0);
        float a = Math.min(1, t / 30f) * fade;
        drawSigil(ctx, t, k, a, tight);
        drawPowers(ctx, chest, t, k, a, tight);
        drawCrown(ctx, body, t, a);
        boolean firstPerson = ctx.cam.distanceTo(chest) < 2.5;
        if (!firstPerson) drawMandala(ctx, chest, t, k, a);
        drawInflow(ctx, chest, t, k, a, tight);
        drawPillar(ctx, chest, t, k, a, tight);
        if (target.distanceTo(start) > 24) drawTarget(ctx, t, k, a);
        if (tight > 0) {
            var glow = ctx.additive(VfxTextures.GLOW);
            ctx.billboard(glow, chest, 2 + 10 * tight, 0, Colors.alpha(fade * tight, WHITE));
            ctx.billboard(glow, chest, 6 + 30 * tight, 0, Colors.alpha(fade * tight * 0.5f, color));
        }
    }

    /** The great circle on the ground under where they stood. */
    private void drawSigil(VfxContext ctx, float t, float k, float a, float tight) {
        Vec3 g = start.add(0, 0.3, 0);
        float r = 7 + 9 * easeOut(clamp01(t / 70f));
        float turn = t * (0.006f + 0.03f * k * k);
        var grand = ctx.additive(VfxTextures.GRAND_CIRCLE);
        ctx.disc(grand, g, X, Z, r, turn, Colors.alpha(a * 0.9f, GOLD));
        ctx.disc(ctx.additive(VfxTextures.RUNE_CIRCLE), g.add(0, 0.05, 0), X, Z, r * 0.62f, -turn * 1.5f, Colors.alpha(a * 0.9f, PALE));
        ctx.disc(ctx.additive(VfxTextures.GLOW), g.add(0, 0.1, 0), X, Z, r * 1.6f, 0, Colors.alpha(a * (0.25f + 0.25f * k), color));
        var band = ctx.additive(VfxTextures.RUNE_BAND);
        float writ = clamp01(t / 60f);
        int repeats = 16;
        float step = (float) (Math.PI * 2 / repeats);
        for (int i = 0; i < repeats && i < writ * repeats; i++) {
            float cut = Math.min(1, writ * repeats - i);
            ctx.runeBand(band, g.add(0, 0.15, 0), X, Z, r * 1.04f, r * 1.14f, -turn * 0.6f + i * step, -turn * 0.6f + (i + 1) * step, cut, 4, i,
                    Colors.alpha(a, PALE));
        }
        var ring = ctx.additive(VfxTextures.RING);
        ctx.ring(ring, g.add(0, 0.2, 0), X, Z, r * 1.16f, r * 1.19f, ctx.segments(96), Colors.alpha(a, WHITE));
        // the six powers' circles round it
        var sub = ctx.additive(VfxTextures.SUB_CIRCLE);
        for (int i = 0; i < 6; i++) {
            float p = easeOut(clamp01((t - 20 - i * 8) / 20f));
            if (p <= 0) continue;
            float an = turn * 0.5f + (float) (i * Math.PI / 3);
            Vec3 c = g.add(Math.cos(an) * r * 1.35f, 0.25, Math.sin(an) * r * 1.35f);
            ctx.disc(sub, c, X, Z, r * 0.22f * p * (1 - 0.6f * tight), -t * 0.05f + i, Colors.alpha(a * p, POWERS[i]));
            ctx.disc(ctx.additive(VfxTextures.GLOW), c, X, Z, r * 0.4f * p, 0, Colors.alpha(a * p * 0.35f, POWERS[i]));
        }
    }

    /** The six powers' lights circling them, drawing in; at the last they fly into the blade. */
    private void drawPowers(VfxContext ctx, Vec3 chest, float t, float k, float a, float tight) {
        var glow = ctx.additive(VfxTextures.GLOW);
        var star = ctx.additive(VfxTextures.STAR);
        var streak = ctx.energy(VfxTextures.STREAK);
        for (int i = 0; i < 6; i++) {
            float p = easeOut(clamp01((t - 30 - i * 10) / 25f));
            if (p <= 0) continue;
            float orbit = (3.6f - 1.2f * k) * (1 - easeIn(tight));
            float speed = 0.05f + 0.16f * k * k;
            int col = POWERS[i];
            Vec3[] pts = new Vec3[12];
            float[] w = new float[12];
            int[] c = new int[12];
            for (int s = 0; s < 12; s++) {
                float tt = t - s * 0.8f;
                double an = tt * speed + i * Math.PI / 3;
                double bob = Math.sin(tt * 0.08f + i) * 0.8;
                pts[s] = chest.add(Math.cos(an) * orbit, bob + 0.4, Math.sin(an) * orbit);
                w[s] = 0.5f * (1 - s / 12f);
                c[s] = Colors.alpha(a * p * (1 - s / 12f), col);
            }
            ctx.ribbon(streak, pts, w, c, 0, 0.1f);
            ctx.billboard(glow, pts[0], 2.4f, 0, Colors.alpha(a * p, col));
            ctx.billboard(star, pts[0], 1.1f, t * 0.1f + i, Colors.alpha(a * p, WHITE));
        }
    }

    /** A crown of light over their head. */
    private void drawCrown(VfxContext ctx, Vec3 body, float t, float a) {
        float p = easeOut(clamp01((t - 10) / 40f));
        if (p <= 0) return;
        Vec3 c = body.add(0, 2.5 + 0.1 * Math.sin(t * 0.1f), 0);
        ctx.ring(ctx.additive(VfxTextures.RING), c, X, Z, 0.55f * p, 0.7f * p, 32, Colors.alpha(a * p, WHITE));
        ctx.disc(ctx.additive(VfxTextures.RUNE_CIRCLE), c, X, Z, 0.75f * p, t * 0.04f, Colors.alpha(a * p * 0.8f, GOLD));
        ctx.disc(ctx.additive(VfxTextures.GLOW), c, X, Z, 1.6f * p, 0, Colors.alpha(a * p * 0.4f, GOLD));
    }

    /** A vast turning mandala of light behind them, as those watching see it. */
    private void drawMandala(VfxContext ctx, Vec3 chest, float t, float k, float a) {
        float p = easeOut(clamp01((t - 20) / 60f));
        if (p <= 0) return;
        Vec3 behind = chest.add(ctx.camLook.scale(1.5)).add(0, 0.6, 0);
        Vec3 ax = ctx.camLeft, ay = ctx.camUp;
        float r = (4 + 5 * k) * p;
        ctx.disc(ctx.additive(VfxTextures.GRAND_CIRCLE), behind, ax, ay, r, t * 0.01f, Colors.alpha(a * p * 0.55f, GOLD));
        ctx.disc(ctx.additive(VfxTextures.SUB_CIRCLE), behind, ax, ay, r * 0.6f, -t * 0.02f, Colors.alpha(a * p * 0.6f, PALE));
        ctx.disc(ctx.additive(VfxTextures.FLASH), behind, ax, ay, r * 1.4f, t * 0.004f, Colors.alpha(a * p * 0.25f, color));
    }

    /** Light drawn in from all round, spiralling into them. */
    private void drawInflow(VfxContext ctx, Vec3 chest, float t, float k, float a, float tight) {
        var streak = ctx.additive(VfxTextures.STREAK);
        int motes = ctx.segments(MOTES);
        for (int i = 0; i < motes; i++) {
            float cyc = (t * 0.015f * spin[i] + rise[i]) % 1f;
            float rr = 40 * dist[i] * (1 - cyc) * (1 - 0.6f * tight) + 0.8f;
            double an = ang[i] + cyc * 4 * spin[i] + t * 0.01f;
            Vec3 p = chest.add(Math.cos(an) * rr, (dist[i] - 0.5) * 24 * (1 - cyc), Math.sin(an) * rr);
            Vec3 to = chest.subtract(p).normalize();
            Vec3 tangent = new Vec3(-Math.sin(an), 0, Math.cos(an));
            float ma = a * Math.min(1, cyc * 5) * Math.min(1, (1 - cyc) * 6) * (0.3f + 0.7f * k);
            ctx.streak(streak, p, to.add(tangent.scale(0.6)), 2.5f + 3 * dist[i], 0.35f, Colors.alpha(ma, i % 4 == 0 ? WHITE : i % 6 < 3 ? GOLD : POWERS[i % 6]));
        }
    }

    /** The pillar of light climbing out of the blade into the sky, thickening. */
    private void drawPillar(VfxContext ctx, Vec3 chest, float t, float k, float a, float tight) {
        float p = clamp01((t - ticks * 0.2f) / (ticks * 0.3f));
        if (p <= 0) return;
        float w = (0.3f + 2.4f * k * k + 4 * tight) * p;
        Vec3 top = chest.add(0, 40 + 400 * easeOut(p), 0);
        var beam = ctx.additive(VfxTextures.BEAM_SOFT);
        ctx.farClamp = VfxContext.viewReach() * 0.86;
        ctx.softBeam(beam, chest.add(0, 1, 0), top, w, Colors.alpha(a * (0.5f + 0.4f * k), WHITE), 0, 1, 0.02f, 0.5f);
        ctx.softBeam(ctx.additive(VfxTextures.GLOW), chest.add(0, 1, 0), top, w * 5, Colors.alpha(a * 0.3f * k, color), 0, 1, 0.02f, 0.6f);
        // motes racing up it
        var star = ctx.additive(VfxTextures.STAR);
        for (int i = 0; i < 24; i++) {
            float cyc = (t * 0.03f + i * 0.618f) % 1f;
            Vec3 m = chest.add(Math.cos(i * 2.4) * w * 0.6, 2 + cyc * 120, Math.sin(i * 2.4) * w * 0.6);
            ctx.billboard(star, m, 0.9f + w * 0.2f, i, Colors.alpha(a * p * (1 - cyc), i % 3 == 0 ? WHITE : POWERS[i % 6]));
        }
        ctx.farClamp = 0;
    }

    /** Over the place it will strike: clouds turning in a vortex round a shaft of light, the ground marked. */
    private void drawTarget(VfxContext ctx, float t, float k, float a) {
        double reach = VfxContext.viewReach();
        ctx.squash = ctx.squashFor(target, Math.max(60, size * 0.7) + 150 + size * 0.15, reach * 0.8);
        Vec3 eye = target.add(0, 150 + size * 0.15, 0);
        float vr = Math.max(60, size * 0.7f) * (0.4f + 0.6f * easeOut(clamp01(t / 80f)));
        var mist = ctx.translucent(VfxTextures.MIST);
        for (int i = MOTES; i < MOTES + CLOUDS; i++) {
            double an = ang[i] + t * 0.004f * spin[i] * (1 + k * 2);
            float rr = vr * (0.25f + 0.75f * dist[i]);
            Vec3 p = eye.add(Math.cos(an) * rr, (rise[i] - 0.5) * 20 - rr * 0.08, Math.sin(an) * rr);
            ctx.billboard(mist, p, vr * 0.35f, i + t * 0.002f, Colors.alpha(a * 0.55f, 0x2A2236));
        }
        // flashes in the clouds
        var bolt = ctx.additive(VfxTextures.LIGHTNING);
        int frame = (int) (t / 3);
        RandomSource r = RandomSource.create(frame * 31L + 7);
        if (r.nextFloat() < 0.35f + 0.5f * k) {
            double an = r.nextDouble() * Math.PI * 2, rr = vr * r.nextDouble();
            Vec3 p = eye.add(Math.cos(an) * rr, 0, Math.sin(an) * rr);
            ctx.streak(bolt, p, new Vec3(r.nextDouble() - 0.5, -0.4, r.nextDouble() - 0.5), vr * 0.5f, vr * 0.03f, Colors.alpha(a, PALE));
        }
        // the shaft of light from the eye of the vortex, and the mark on the ground
        float shaft = clamp01((t - ticks * 0.35f) / (ticks * 0.4f));
        if (shaft > 0) {
            ctx.softBeam(ctx.additive(VfxTextures.BEAM_SOFT), eye, target, 2 + 12 * shaft * shaft, Colors.alpha(a * shaft, WHITE), 0, 1, 0.1f, 0.05f);
            ctx.softBeam(ctx.additive(VfxTextures.GLOW), eye, target, 20 + 40 * shaft, Colors.alpha(a * 0.3f * shaft, color), 0, 1, 0.1f, 0.05f);
        }
        Vec3 g = target.add(0, 0.6, 0);
        float mark = clamp01((t - 20) / 60f);
        ctx.disc(ctx.additive(VfxTextures.GRAND_CIRCLE), g, X, Z, size * easeOut(mark), -t * 0.003f, Colors.alpha(a * mark * 0.45f, color));
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * 0.3f);
        ctx.ring(ctx.additive(VfxTextures.RING), g, X, Z, size * 0.98f, size * 1.02f, ctx.segments(128), Colors.alpha(a * mark * pulse, PALE));
        ctx.squash = 1;
    }
}
