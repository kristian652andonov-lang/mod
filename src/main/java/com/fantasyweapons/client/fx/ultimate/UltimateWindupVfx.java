package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.fx.CameraShake;
import com.fantasyweapons.client.fx.SkyDarkness;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * An ultimate power gathering before it strikes. The sky darkens and the ground shakes harder and harder; on the spot
 * it will strike, a great circle of runes is drawn on the ground, turning faster as it fills; the dust of the ground
 * lifts, its light is drawn in from all around, spiralling down into the circle, and a thread of light climbs out of
 * it to the sky; round the caster the power coils in too. At the last it all draws tight into a point of light - and
 * the power is loosed.
 */
public class UltimateWindupVfx extends Vfx {
    private static final int MOTES = 90, DUST = 40;

    private final Vec3 at, caster;
    private final float size;
    private final int ticks, color, light;
    private final float[] ang, dist, rise, spin;

    public UltimateWindupVfx(Vec3 at, Vec3 caster, float size, int ticks, int color, long seed) {
        super(ticks + 4);
        this.at = at;
        this.caster = caster;
        this.size = size;
        this.ticks = ticks;
        this.color = color;
        this.light = Colors.lerpRgb(color, 0xFFFFFF, 0.6f);
        RandomSource r = RandomSource.create(seed);
        ang = new float[MOTES + DUST];
        dist = new float[MOTES + DUST];
        rise = new float[MOTES + DUST];
        spin = new float[MOTES + DUST];
        for (int i = 0; i < ang.length; i++) {
            ang[i] = r.nextFloat() * 6.283f;
            dist[i] = 0.35f + r.nextFloat() * 0.65f;
            rise[i] = r.nextFloat();
            spin[i] = 0.6f + r.nextFloat() * 0.8f;
        }
    }

    @Override
    public void tick() {
        super.tick();
        float k = age / (float) ticks;
        if (age % 4 == 0 && age < ticks) CameraShake.add(at, 0.25f + 1.6f * k * k, size * 3);
        if (age == 1) {
            UltimateFx.sound(SoundEvents.BEACON_ACTIVATE, 1f, 0.5f);
            UltimateFx.sound(SoundEvents.WARDEN_EMERGE, 0.7f, 0.6f);
        }
        // a heartbeat, quickening and rising as it gathers
        if (age < ticks - 4 && age % Math.max(4, 12 - age / 5) == 0) UltimateFx.sound(SoundEvents.WARDEN_HEARTBEAT, 0.6f + 0.6f * k, 0.6f + 0.5f * k);
        if (age == ticks - 14) UltimateFx.sound(SoundEvents.BEACON_POWER_SELECT, 1f, 0.5f);
        if (age == ticks - 6) UltimateFx.sound(SoundEvents.ELYTRA_FLYING, 0.5f, 2f);
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float k = clamp01(t / ticks);
        // the last few ticks: everything draws tight into the middle
        float tight = clamp01((t - (ticks - 8)) / 8f);
        float fade = 1 - clamp01((t - ticks) / 4f);
        if (fade <= 0) return;
        if (ctx.cam.distanceTo(at) < size * 6) SkyDarkness.request(0.6f * easeInOut(k) * fade);
        ctx.squash = ctx.squashFor(at, size, VfxContext.viewReach() * 0.85);
        Vec3 ground = at.add(0, 0.35, 0);
        Vec3 ax = new Vec3(1, 0, 0), az = new Vec3(0, 0, 1);
        float r = size * (0.25f + 0.45f * easeOut(k)) * (1 - 0.85f * easeIn(tight));
        float a = Math.min(1, k * 2.5f) * fade;
        float turn = t * (0.01f + 0.05f * k * k);
        // the circle on the ground: a great one, a ring of runes inside turning the other way, small ones orbiting
        var additive = ctx.additive(VfxTextures.GRAND_CIRCLE);
        ctx.disc(additive, ground, ax, az, r, turn, Colors.alpha(a * 0.85f, color));
        ctx.disc(ctx.additive(VfxTextures.RUNE_CIRCLE), ground.add(0, 0.05, 0), ax, az, r * 0.62f, -turn * 1.6f, Colors.alpha(a * 0.9f, light));
        ctx.satellites(ctx.additive(VfxTextures.SUB_CIRCLE), ground.add(0, 0.1, 0), ax, az, r * 1.08f, r * 0.16f, 6, turn * 0.7f, -t * 0.08f,
                Colors.alpha(a * 0.7f, color));
        ctx.ring(ctx.additive(VfxTextures.RING), ground, ax, az, r * 1.0f, r * 1.06f, ctx.segments(96), Colors.alpha(a, light));
        // the ground's glow, swelling
        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.disc(glow, ground.add(0, 0.15, 0), ax, az, r * 1.4f, 0, Colors.alpha(a * 0.35f * (0.6f + 0.4f * k), color));
        // the thread of light up out of it, thickening
        float thread = 0.6f + 5f * k * k;
        ctx.softBeam(ctx.additive(VfxTextures.BEAM_SOFT), ground, ground.add(0, 160 + 200 * k, 0), thread, Colors.alpha(a * (0.4f + 0.5f * k), light), 0, 1, 0.02f, 0.6f);
        ctx.softBeam(glow, ground, ground.add(0, 60 + 80 * k, 0), thread * 4, Colors.alpha(a * 0.3f * k, color), 0, 1, 0.05f, 0.7f);
        // light drawn in from all round, spiralling down into the circle
        var streak = ctx.additive(VfxTextures.STREAK);
        int motes = ctx.segments(MOTES);
        for (int i = 0; i < motes; i++) {
            float cyc = (t * 0.02f * spin[i] + rise[i]) % 1f;
            float rr = size * (1.3f * dist[i]) * (1 - cyc) * (1 - 0.7f * tight) + 1;
            double an = ang[i] + cyc * 4.5 * spin[i] + turn;
            Vec3 p = ground.add(Math.cos(an) * rr, 2 + size * 0.35f * (1 - cyc) * dist[i], Math.sin(an) * rr);
            Vec3 to = ground.add(0, 1, 0).subtract(p).normalize();
            Vec3 tangent = new Vec3(-Math.sin(an), 0, Math.cos(an));
            float ma = a * Math.min(1, cyc * 5) * Math.min(1, (1 - cyc) * 6) * (0.4f + 0.6f * k);
            ctx.streak(streak, p, to.add(tangent.scale(0.7)), 3 + size * 0.06f, 0.5f + size * 0.006f, Colors.alpha(ma, i % 3 == 0 ? 0xFFFFFF : light));
        }
        // the dust of the ground lifting, dark against the glow
        var mist = ctx.translucent(VfxTextures.MIST);
        for (int i = MOTES; i < MOTES + DUST; i++) {
            float cyc = (t * 0.012f * spin[i] + rise[i]) % 1f;
            float rr = size * 0.95f * dist[i];
            double an = ang[i] + t * 0.004f;
            Vec3 p = ground.add(Math.cos(an) * rr, cyc * size * 0.3f, Math.sin(an) * rr);
            float da = a * k * Math.min(1, cyc * 4) * (1 - cyc) * 0.45f;
            ctx.billboard(mist, p, size * 0.12f + cyc * size * 0.1f, i, Colors.alpha(da, 0x3A3230));
        }
        // round the caster, the power coiling in
        if (caster != null && caster.distanceTo(at) > 6) {
            for (int i = 0; i < 24; i++) {
                float cyc = (t * 0.035f * spin[i] + rise[i]) % 1f;
                float rr = 7 * (1 - cyc) + 0.6f;
                double an = ang[i] + cyc * 6;
                Vec3 p = caster.add(Math.cos(an) * rr, 1 + 2.5 * (1 - cyc) * dist[i], Math.sin(an) * rr);
                Vec3 tangent = new Vec3(-Math.sin(an), 0.2, Math.cos(an));
                ctx.streak(streak, p, tangent, 1.6f, 0.25f, Colors.alpha(a * 0.8f * Math.min(1, cyc * 4) * Math.min(1, (1 - cyc) * 5), light));
            }
        }
        // at the last, a point of light where it all drew in
        if (tight > 0) {
            ctx.billboard(glow, ground.add(0, 2, 0), size * (0.1f + 0.5f * tight), 0, Colors.alpha(fade * tight, 0xFFFFFF));
            ctx.billboard(glow, ground.add(0, 2, 0), size * (0.3f + 1.2f * tight), 0, Colors.alpha(fade * tight * 0.5f, color));
        }
        ctx.squash = 1;
    }
}
