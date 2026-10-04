package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.BeamVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.OrbVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SlashArcVfx;
import com.fantasyweapons.client.vfx.effects.SunVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Client visuals for Solaris. Palette: molten gold, white-hot core, deep orange. */
public final class SolarisFx {
    static final int SUN = 0xFFB627;
    static final int CORE = 0xFFF4C2;
    static final int DEEP = 0xFF6A00;
    static final int EMBER = 0xD9480F;
    static final Blast.Palette PALETTE = new Blast.Palette(CORE, SUN, EMBER);

    private SolarisFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.SOLARIS_RADIANT_SLASH, SolarisFx::radiantSlash);
        FxDispatcher.register(FxIds.SOLARIS_SOLAR_BURST, SolarisFx::solarBurst);
        FxDispatcher.register(FxIds.SOLARIS_SUPERNOVA, SolarisFx::supernova);
        FxDispatcher.register(FxIds.SOLARIS_SUPERNOVA_IMPACT, SolarisFx::supernovaImpact);
        FxDispatcher.register(FxIds.SOLARIS_INFERNO, SolarisFx::inferno);
        FxDispatcher.register(FxIds.SOLARIS_INFERNO_STRIKE, SolarisFx::infernoStrike);
        FxDispatcher.register(FxIds.SOLARIS_INFERNO_COLLAPSE, SolarisFx::infernoCollapse);
    }

    private static void radiantSlash(FxPayload p) {
        Vec3 o = p.pos();
        float range = p.scale();
        float half = (float) Math.toRadians(p.power() / 2);
        Vec3 fwd = new Vec3(p.dir().x, 0, p.dir().z);
        fwd = fwd.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : fwd.normalize();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
        float a0 = (float) (Math.PI / 2 - half), a1 = (float) (Math.PI / 2 + half);
        // three stacked crescents: a wide molten body, a white-hot edge and a lower ember wave
        VfxManager.add(new SlashArcVfx(o, right, fwd, range * 0.78f, range * 0.42f, a0, a1, Colors.argb(235, SUN), Colors.argb(255, CORE), 12));
        VfxManager.add(new SlashArcVfx(o.add(0, 0.25, 0), right, fwd.add(0, 0.12, 0).normalize(), range * 0.62f, range * 0.2f, a0 + 0.1f, a1 - 0.1f,
                Colors.argb(220, CORE), Colors.argb(255, 0xFFFFFF), 9));
        VfxManager.add(new SlashArcVfx(o.add(0, -0.45, 0), right, fwd, range * 0.92f, range * 0.3f, a0 - 0.1f, a1 + 0.1f,
                Colors.argb(170, DEEP), Colors.argb(230, SUN), 15));
        RandomSource r = RandomSource.create(p.seed());
        for (int i = 0; i <= 8; i++) {
            double a = a0 + (a1 - a0) * i / 8.0;
            Vec3 dir = right.scale(Math.cos(a)).add(fwd.scale(Math.sin(a)));
            Vec3 pt = o.add(dir.scale(range * (0.6 + r.nextDouble() * 0.35)));
            VfxManager.add(new ShardBurstVfx(pt, dir.add(0, 0.6, 0), 0.6f, 0.18f, 6, 0.3f, Colors.argb(255, CORE), Colors.argb(0, EMBER), 16,
                    p.seed() + i).texture(VfxTextures.FLAME, false).physics(-0.01f, 0.9f));
        }
        VfxManager.add(new FlashVfx(o.add(fwd.scale(1.5)), 0.5f, range * 0.6f, Colors.argb(200, SUN), 8).energy());
        CameraShake.add(o, 0.35f, 10);
        hitFlashes(p);
    }

    private static void solarBurst(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        Blast.explode(g, r, PALETTE, p.seed(), 1, VfxTextures.FLAME);
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), new Vec3(0, 1, 0), r * 0.65f, Colors.argb(230, SUN), VfxTextures.RUNE_CIRCLE, 30)
                .spin(0.08f).energy().timing(0.1f, 0.5f));
        // radiant rays fanning out at waist height
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14;
            Vec3 d = new Vec3(Math.cos(a), 0.12, Math.sin(a)).normalize();
            VfxManager.add(new BeamVfx(g.add(0, 0.7, 0), g.add(0, 0.7, 0).add(d.scale(r * 1.05)), 0.45f, Colors.argb(230, i % 2 == 0 ? SUN : CORE), 9 + i % 3));
        }
        VfxManager.add(new BeamVfx(g, g.add(0, 7, 0), 1.6f, Colors.argb(230, CORE), 12));
        hitFlashes(p);
    }

    private static void supernova(FxPayload p) {
        Vec3 start = p.pos();
        Vec3 vel = p.dir();
        int life = (int) Math.ceil(p.power() / Math.max(0.05, vel.length())) + 4;
        SunVfx star = VfxManager.add(new SunVfx(start, 0.75f, SUN, CORE, life).grow(5).rays(10)
                .path(t -> start.add(vel.scale(t))));
        OrbVfx core = VfxManager.add(new OrbVfx(t -> start.add(vel.scale(t)), 0.7f, SUN, 0xFFFFFF, life).fades(2, 1));
        FollowTrailVfx trail = VfxManager.add(new FollowTrailVfx(core::now, 1.5f, Colors.argb(230, SUN), Colors.argb(0, EMBER), 16, life + 20));
        FollowTrailVfx trail2 = VfxManager.add(new FollowTrailVfx(core::now, 0.6f, Colors.argb(255, CORE), Colors.argb(0, SUN), 9, life + 20)
                .texture(VfxTextures.LIGHTNING, false));
        VfxManager.add(new FlashVfx(start, 0.4f, 2.5f, Colors.argb(220, SUN), 8).energy());
        FxProjectiles.track(p.seed(), star, core);
    }

    private static void supernovaImpact(FxPayload p) {
        FxProjectiles.end(p.seed());
        Vec3 pos = p.pos();
        float r = p.scale();
        Blast.explode(pos, r, PALETTE, p.seed(), 2, VfxTextures.FLAME);
        VfxManager.add(new SunVfx(pos.add(0, 0.5, 0), r * 0.45f, SUN, CORE, 14).grow(3).rays(16));
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            Vec3 d = new Vec3(Math.cos(a), 0.35, Math.sin(a)).normalize();
            VfxManager.add(new BeamVfx(pos.add(0, 0.5, 0), pos.add(d.scale(r * 1.2)), 0.6f, Colors.argb(230, CORE), 10));
        }
        hitFlashes(p);
    }

    private static void inferno(FxPayload p) {
        Vec3 center = p.pos();
        Vec3 sun = p.points().isEmpty() ? center.add(0, 10, 0) : p.points().get(0);
        float r = p.scale();
        int duration = Math.round(p.power());
        // the second sun forms overhead, burns, and falls onto the zone at the very end
        VfxManager.add(new SunVfx(sun, 2.6f, SUN, CORE, duration + 2).grow(20).rays(18).path(t -> {
            float fall = Math.max(0, (t - (duration - 8)) / 8f);
            return sun.lerp(center.add(0, 1, 0), fall * fall);
        }));
        VfxManager.add(new DecalVfx(center.add(0, 0.05, 0), new Vec3(0, 1, 0), r, Colors.argb(220, SUN), VfxTextures.RUNE_CIRCLE, duration)
                .spin(0.02f).energy().timing(0.08f, 0.1f));
        VfxManager.add(new DecalVfx(center.add(0, 0.04, 0), new Vec3(0, 1, 0), r * 1.1f, Colors.argb(130, DEEP), VfxTextures.GLOW, duration)
                .timing(0.1f, 0.1f));
        VfxManager.add(new BeamVfx(sun, center, 2.5f, Colors.argb(90, SUN), 24));
        ScreenFx.zoneVignette(SUN, 0.22f, duration, center, r);
        CameraShake.add(center, 0.3f, r * 2);
    }

    private static void infernoStrike(FxPayload p) {
        Vec3 g = p.pos();
        Vec3 from = p.points().isEmpty() ? g.add(0, 10, 0) : p.points().get(0);
        float r = p.scale();
        VfxManager.add(new BeamVfx(from, g, 1.3f, Colors.argb(255, SUN), 9));
        VfxManager.add(new BeamVfx(from, g, 0.5f, Colors.argb(255, CORE), 6));
        VfxManager.add(new FlashVfx(g.add(0, 0.5, 0), 0.6f, r * 1.8f, Colors.argb(230, SUN), 9).energy());
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.06, 0), new Vec3(0, 1, 0), 0.2f, r * 1.4f, 0.4f, Colors.argb(220, CORE), 10).energy());
        VfxManager.add(new DecalVfx(g.add(0, 0.03, 0), new Vec3(0, 1, 0), r * 0.7f, Colors.argb(200, EMBER), VfxTextures.CRACK, 50)
                .timing(0.05f, 0.4f).translucent());
        VfxManager.add(new ShardBurstVfx(g.add(0, 0.3, 0), new Vec3(0, 1, 0), 0.7f, 0.32f, 14, 0.3f, Colors.argb(255, CORE), Colors.argb(0, EMBER), 18,
                p.seed()).texture(VfxTextures.FLAME, false).physics(0.01f, 0.92f));
        CameraShake.add(g, 0.35f, 14);
    }

    private static void infernoCollapse(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        Blast.explode(g, r * 0.9f, PALETTE, p.seed(), 2, VfxTextures.FLAME);
        VfxManager.add(new SunVfx(g.add(0, 1, 0), r * 0.35f, SUN, CORE, 16).grow(3).rays(20));
        VfxManager.add(new BeamVfx(g, g.add(0, 18, 0), 4f, Colors.argb(230, CORE), 16));
        hitFlashes(p);
    }

    /** Short solar flash on every entity the server reported as hit. */
    static void hitFlashes(FxPayload p) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        for (int id : p.entities()) {
            var e = level.getEntity(id);
            if (e == null) continue;
            VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, e.getBbHeight() * 1.4f, Colors.argb(200, SUN), 8).follow(e, new Vec3(0, e.getBbHeight() * 0.5, 0)).energy());
        }
    }
}
