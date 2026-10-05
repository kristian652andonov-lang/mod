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

    private static void total(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int duration = Math.round(p.power());
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), new Vec3(0, 1, 0), r, Colors.argb(200, GOLD), VfxTextures.RUNE_CIRCLE, duration)
                .spin(0.02f).energy().timing(0.1f, 0.1f));
        VfxManager.add(new DecalVfx(c.add(0, 0.06, 0), new Vec3(0, 1, 0), r * 0.6f, Colors.argb(200, VIOLET), VfxTextures.RUNE_CIRCLE, duration)
                .spin(-0.035f).energy().satellites(0).timing(0.1f, 0.1f));
        ScreenFx.zoneVignette(BLACK, 0.55f, duration, c, r + 6);
        CameraShake.add(c, 0.3f, r * 2);
    }

    private static void beam(FxPayload p) {
        Vec3 g = p.pos();
        Vec3 from = p.points().isEmpty() ? g.add(0, 13, 0) : p.points().get(0);
        boolean dark = p.level() == 1;
        int col = dark ? VIOLET : GOLD;
        int core = dark ? CRIMSON : WHITE;
        VfxManager.add(new BeamVfx(from, g, 1.1f, Colors.argb(255, col), 9));
        VfxManager.add(new BeamVfx(from, g, 0.4f, Colors.argb(255, core), 7));
        VfxManager.add(new FlashVfx(g.add(0, 0.6, 0), 0.5f, 2.6f, Colors.argb(230, col), 9).energy());
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.06, 0), new Vec3(0, 1, 0), 0.2f, 2.6f, 0.3f, Colors.argb(220, core), 9).energy());
        if (dark) {
            VfxManager.add(new SunVfx(g.add(0, 0.8, 0), 0.8f, VIOLET, CRIMSON, 10).darkCore().grow(2).rays(8));
        }
    }

    private static void totalEnd(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        Vec3 sun = p.points().isEmpty() ? c.add(0, 13, 0) : p.points().get(0);
        VfxManager.add(new BeamVfx(sun, c, 3f, Colors.argb(230, GOLD), 14));
        Blast.explode(c, r * 0.85f, new Blast.Palette(GOLD, 0xC98A2A, VIOLET), p.seed(), 2, VfxTextures.STAR);
        VfxManager.add(new ShockwaveVfx(c.add(0, 1, 0), new Vec3(0, 1, 0), 0.5f, r * 1.2f, 0.8f, Colors.argb(230, VIOLET), 18).energy().spin(0.1f));
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.position().distanceTo(c) < r + 8) ScreenFx.flash(GOLD, 0.3f, 10);
    }
}
