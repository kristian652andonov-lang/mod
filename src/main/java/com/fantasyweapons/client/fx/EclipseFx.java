package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.BeamVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.LightningVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SunVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Client visuals for Eclipse Reaper. Light: gold and white. Dark: violet and crimson over black. */
public final class EclipseFx {
    static final int GOLD = 0xFFD978;
    static final int WHITE = 0xFFFDF2;
    static final int VIOLET = 0x8B3DFF;
    static final int CRIMSON = 0xB0123A;
    static final int BLACK = 0x0A0412;

    private EclipseFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.ECLIPSE_DISC_HIT, EclipseFx::discHit);
        FxDispatcher.register(FxIds.ECLIPSE_RESONANCE, EclipseFx::resonance);
        FxDispatcher.register(FxIds.ECLIPSE_SOLAR_FLARE, EclipseFx::solarFlare);
        FxDispatcher.register(FxIds.ECLIPSE_UMBRAL_VORTEX, EclipseFx::umbralVortex);
        FxDispatcher.register(FxIds.ECLIPSE_TOTAL, EclipseFx::total);
        FxDispatcher.register(FxIds.ECLIPSE_BEAM, EclipseFx::beam);
        FxDispatcher.register(FxIds.ECLIPSE_TOTAL_END, EclipseFx::totalEnd);
    }

    private static void discHit(FxPayload p) {
        Vec3 at = p.pos();
        boolean dark = p.level() == 1;
        if (dark) {
            VfxManager.add(new SunVfx(at, 0.7f, VIOLET, CRIMSON, 12).darkCore().grow(2).rays(8));
            VfxManager.add(new ShardBurstVfx(at, Vec3.ZERO, 1f, 0.18f, 12, 0.25f, Colors.argb(255, VIOLET), Colors.argb(0, CRIMSON), 14, p.seed())
                    .texture(VfxTextures.SHARD, false).physics(0.01f, 0.88f));
        } else {
            VfxManager.add(new FlashVfx(at, 0.4f, 2.4f, Colors.argb(240, GOLD), 8).energy());
            VfxManager.add(new FlashVfx(at, 0.3f, 1.3f, Colors.argb(255, WHITE), 6, VfxTextures.STAR).spin(0.2f));
            VfxManager.add(new ShardBurstVfx(at, Vec3.ZERO, 1f, 0.3f, 12, 0.22f, Colors.argb(255, WHITE), Colors.argb(0, GOLD), 12, p.seed())
                    .texture(VfxTextures.SPARK, true).physics(0.01f, 0.85f));
        }
    }

    private static void resonance(FxPayload p) {
        Vec3 at = p.pos();
        float h = Math.max(0.6f, p.scale());
        // half gold, half violet: two rings spinning apart and a black-cored corona flash
        VfxManager.add(new ShockwaveVfx(at, new Vec3(0, 1, 0), 0.2f, h * 2.2f, 0.35f, Colors.argb(240, GOLD), 12).energy().spin(0.3f));
        VfxManager.add(new ShockwaveVfx(at, new Vec3(0.3, 1, 0), 0.2f, h * 2.0f, 0.3f, Colors.argb(240, VIOLET), 14).energy().spin(-0.3f));
        VfxManager.add(new SunVfx(at, h * 0.45f, GOLD, WHITE, 12).darkCore().grow(2).rays(12));
        VfxManager.add(new FlashVfx(at, 0.4f, h * 3f, Colors.argb(200, WHITE), 8).energy());
        CameraShake.add(at, 0.3f, 10);
    }

    private static void solarFlare(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        Blast.explode(g, r, new Blast.Palette(WHITE, GOLD, 0xC98A2A), p.seed(), 1, VfxTextures.SPARK);
        VfxManager.add(new SunVfx(g.add(0, 1.2, 0), 1.1f, GOLD, WHITE, 16).grow(3).rays(18));
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            Vec3 d = new Vec3(Math.cos(a), 0.15, Math.sin(a)).normalize();
            VfxManager.add(new BeamVfx(g.add(0, 1.2, 0), g.add(0, 1.2, 0).add(d.scale(r)), 0.35f, Colors.argb(230, i % 2 == 0 ? GOLD : WHITE), 9));
        }
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), new Vec3(0, 1, 0), r * 0.7f, Colors.argb(220, GOLD), VfxTextures.RUNE_CIRCLE, 30).spin(0.1f).energy());
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.position().distanceTo(g) < r + 6) ScreenFx.flash(WHITE, 0.35f, 8);
    }

    private static void umbralVortex(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int duration = Math.round(p.power());
        VfxManager.add(new SunVfx(c, Math.max(0.8f, r * 0.26f), VIOLET, CRIMSON, duration).darkCore().grow(6).rays(10));
        VfxManager.add(new DecalVfx(FrostrendFx.ground(c).add(0, 0.05, 0), new Vec3(0, 1, 0), r, Colors.argb(220, VIOLET), VfxTextures.SWIRL, duration)
                .spin(0.25f).energy().timing(0.1f, 0.2f));
        VfxManager.add(new DecalVfx(FrostrendFx.ground(c).add(0, 0.04, 0), new Vec3(0, 1, 0), r * 1.1f, Colors.argb(200, BLACK), VfxTextures.GLOW, duration)
                .timing(0.1f, 0.2f).translucent());
        // shadow streams spiralling inward for the whole duration
        for (int t = 0; t < duration; t += 3) {
            int tt = t;
            FxScheduler.after(t, () -> {
                RandomSource rr = RandomSource.create(p.seed() + tt);
                for (int k = 0; k < 3; k++) {
                    double a = rr.nextDouble() * Math.PI * 2;
                    Vec3 from = c.add(Math.cos(a) * r, (rr.nextDouble() - 0.3) * 2, Math.sin(a) * r);
                    VfxManager.add(new ShardBurstVfx(from, Vec3.ZERO, 1f, 0.02f, 3, 0.3f, Colors.argb(230, VIOLET), Colors.argb(0, CRIMSON), 18,
                            rr.nextLong()).texture(VfxTextures.SHARD, false).attract(c, 0.06f).physics(0, 0.9f));
                }
                if (tt % 9 == 0) {
                    double a = rr.nextDouble() * Math.PI * 2;
                    VfxManager.add(new LightningVfx(c, c.add(Math.cos(a) * r * 0.8, rr.nextGaussian(), Math.sin(a) * r * 0.8), 0.12f,
                            Colors.argb(220, CRIMSON), 4, rr.nextLong()).branches(1));
                }
            });
        }
        CameraShake.add(c, 0.25f, r * 2);
    }

    /** The eclipse hanging over each caster's Total Eclipse, which the crescents fall out of. */
    private static final java.util.Map<Integer, com.fantasyweapons.client.vfx.effects.EclipseSunVfx> ECLIPSES = new java.util.HashMap<>();

    /**
     * Total Eclipse: high above, a black moon slides across a blazing sun until only its corona is left; beneath it
     * day turns to night - a shadow spreads over the ground, the light dies and stars come out in the darkened air.
     */
    private static void total(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int duration = Math.round(p.power());
        Vec3 sky = p.points().isEmpty() ? c.add(0, 18, 0) : p.points().get(0);
        int contact = 24;
        ECLIPSES.put(p.caster(), VfxManager.add(new com.fantasyweapons.client.vfx.effects.EclipseSunVfx(sky, Math.max(3f, r * 0.2f), WHITE, GOLD, BLACK,
                contact, duration + contact + 10)));
        VfxManager.add(new Umbra(c, r, sky, duration + 20, p.seed()));
        ScreenFx.zoneVignette(BLACK, 0.6f, duration + 10, c, r + 6);
        CameraShake.add(c, 0.25f, r * 2);
    }

    /** The shadow of the eclipse over the battlefield: the ground darkens, a twilight rim glows, stars come out. */
    private static final class Umbra extends com.fantasyweapons.client.vfx.Vfx {
        private static final int STARS = 70;
        private final Vec3 c, sky;
        private final float r;
        private final float[] sx = new float[STARS], sy = new float[STARS], sz = new float[STARS], ph = new float[STARS];

        Umbra(Vec3 c, float r, Vec3 sky, int lifetime, long seed) {
            super(lifetime);
            this.c = c;
            this.r = r;
            this.sky = sky;
            RandomSource rnd = RandomSource.create(seed);
            for (int i = 0; i < STARS; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
                sx[i] = (float) (Math.cos(a) * d);
                sz[i] = (float) (Math.sin(a) * d);
                sy[i] = 3 + rnd.nextFloat() * (float) Math.max(4, sky.y - c.y - 4);
                ph[i] = rnd.nextFloat() * 6.283f;
            }
        }

        @Override
        public void render(com.fantasyweapons.client.vfx.VfxContext ctx) {
            float time = age + ctx.partial;
            // the shadow sweeps in as the moon covers the sun and lifts as it leaves
            float dark = easeInOut(clamp01((time - 4) / 22f)) * clamp01((lifetime - time) / 16f);
            if (dark <= 0.01f) return;
            // the sky darkens for anyone standing in (or near) the shadow
            double away = ctx.cam.distanceTo(new Vec3(c.x, ctx.cam.y, c.z)) - r;
            SkyDarkness.request(dark * (float) Math.max(0, Math.min(1, 1 - away / 12)));
            Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
            Vec3 g = c.add(0, 0.05, 0);
            ctx.disc(ctx.translucent(VfxTextures.GLOW), g, X, Z, r * 1.35f, 0, Colors.alpha(0.62f * dark, BLACK));
            ctx.disc(ctx.translucent(VfxTextures.GLOW), g.add(0, 0.01, 0), X, Z, r * 0.8f, 0, Colors.alpha(0.35f * dark, 0x1A0830));
            // the twilight rim where the shadow ends: gold on the outside, violet within
            var ring = ctx.additive(VfxTextures.RING);
            int segs = ctx.segments(64);
            ctx.ring(ring, g.add(0, 0.03, 0), X, Z, r * 0.98f, r * 1.04f, segs, Colors.alpha(0.55f * dark, GOLD));
            ctx.ring(ring, g.add(0, 0.03, 0), X, Z, r * 0.9f, r * 0.94f, segs, Colors.alpha(0.4f * dark, VIOLET));
            // stars coming out in the darkened air, twinkling
            var star = ctx.additive(VfxTextures.STAR);
            for (int i = 0; i < STARS; i++) {
                float tw = 0.55f + 0.45f * (float) Math.sin(time * 0.15f + ph[i]);
                float show = clamp01((dark - (i % 7) * 0.08f) * 2);
                if (show <= 0) continue;
                ctx.billboard(star, c.add(sx[i], sy[i], sz[i]), 0.25f + 0.2f * (i % 3), ph[i], Colors.alpha(show * tw * 0.9f, i % 4 == 0 ? GOLD : 0xE8E4FF));
            }
        }
    }

    /** A crescent of light or shadow curves down out of the corona onto its target and cuts it with a crossed slash. */
    private static void beam(FxPayload p) {
        Vec3 g = p.pos();
        var ecl = ECLIPSES.get(p.caster());
        Vec3 sun = ecl != null && !ecl.isDead() ? ecl.center() : (p.points().isEmpty() ? g.add(0, 18, 0) : p.points().get(0));
        boolean dark = p.level() == 1;
        int col = dark ? VIOLET : WHITE;
        int edge = dark ? CRIMSON : GOLD;
        RandomSource rnd = RandomSource.create(p.seed());
        Vec3 toward = new Vec3(g.x - sun.x, 0, g.z - sun.z);
        Vec3 flat = toward.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : toward.normalize();
        Vec3 from = sun.add(flat.scale(ecl != null ? ecl.radius() * 1.3 : 3));
        Vec3 side = new Vec3(-flat.z, 0, flat.x).scale(rnd.nextBoolean() ? 1 : -1);
        Vec3 hit = g.add(0, 1.0, 0);
        int flight = 12;
        VfxManager.add(new com.fantasyweapons.client.vfx.effects.CrescentStrikeVfx(from, hit, side, 2.2f, col, edge, flight));
        FxScheduler.after(flight, () -> {
            Vec3 face = new Vec3(-flat.x, 0, -flat.z);
            Vec3 across = new Vec3(-face.z, 0, face.x);
            Vec3 d1 = across.add(0, 1, 0).normalize(), d2 = across.scale(-1).add(0, 1, 0).normalize();
            VfxManager.add(new com.fantasyweapons.client.vfx.effects.SlashArcVfx(hit, d1, face, 1.5f, 0.35f, -1.2f, 1.2f, Colors.argb(255, col), Colors.argb(255, edge), 8));
            VfxManager.add(new com.fantasyweapons.client.vfx.effects.SlashArcVfx(hit, d2, face, 1.5f, 0.35f, -1.2f, 1.2f, Colors.argb(255, col), Colors.argb(255, edge), 8));
            VfxManager.add(new FlashVfx(hit, 0.5f, 2.4f, Colors.argb(230, edge), 8).energy());
            VfxManager.add(new ShockwaveVfx(g.add(0, 0.06, 0), new Vec3(0, 1, 0), 0.2f, 2.4f, 0.3f, Colors.argb(220, edge), 9).energy());
            VfxManager.add(new ShardBurstVfx(hit, new Vec3(0, 1, 0), 1.2f, 0.18f, 10, 0.18f, Colors.argb(255, col), Colors.argb(0, edge), 14, p.seed())
                    .texture(VfxTextures.STAR, false).physics(-0.004f, 0.9f).energy());
        });
    }

    /** The sun comes back: the moon slides off with a flash of the diamond ring and the corona blazes across the field. */
    private static void totalEnd(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        ECLIPSES.remove(p.caster());
        Blast.explode(c, r * 0.6f, new Blast.Palette(GOLD, 0xC98A2A, VIOLET), p.seed(), 2, VfxTextures.STAR);
        // light and shadow sweeping out across the ground together
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.3, 0), new Vec3(0, 1, 0), 0.5f, r * 1.25f, 0.9f, Colors.argb(240, GOLD), 20).energy().spin(0.08f));
        FxScheduler.after(3, () -> VfxManager.add(new ShockwaveVfx(c.add(0, 0.25, 0), new Vec3(0, 1, 0), 0.5f, r * 1.1f, 0.7f, Colors.argb(220, VIOLET), 20)
                .energy().spin(-0.08f)));
        VfxManager.add(new FlashVfx(c.add(0, 1.5, 0), 1f, r * 0.9f, Colors.argb(220, WHITE), 12).energy());
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.position().distanceTo(c) < r + 8) ScreenFx.flash(GOLD, 0.35f, 12);
        CameraShake.add(c, 0.5f, r * 2);
    }
}
