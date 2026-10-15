package com.fantasyweapons.client.fx.mage;

import com.fantasyweapons.client.fx.CameraShake;
import com.fantasyweapons.client.fx.FxDispatcher;
import com.fantasyweapons.client.fx.FxScheduler;
import com.fantasyweapons.client.fx.ScreenFx;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.BeamVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.LightColumnVfx;
import com.fantasyweapons.client.vfx.effects.OrbVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SpellVfx;
import com.fantasyweapons.client.vfx.effects.SunVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.fantasyweapons.client.fx.mage.MageFx.LIGHT;
import static com.fantasyweapons.client.fx.mage.MageFx.UP;
import static com.fantasyweapons.client.fx.mage.MageFx.hash;

/** Client visuals for the light mage: the Sunwarden's hallowed ground and dawn; the Lumen Psalter's hymn and ascension. */
final class LightMageFx {
    static final int GOLD = 0xFFC94A, WHITE_GOLD = 0xFFF6D0;

    private LightMageFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.SUN_SANCTIFY, LightMageFx::sanctify);
        FxDispatcher.register(FxIds.SUN_DAWN, LightMageFx::dawn);
        FxDispatcher.register(FxIds.LUMEN_HYMN, LightMageFx::hymn);
        FxDispatcher.register(FxIds.LUMEN_ASCENSION, LightMageFx::ascension);
    }

    /** A golden circle hallowed into the ground: turning runes, slender pillars of light around it, motes rising. */
    private static void sanctify(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int life = Math.round(p.power()) + 10;
        long seed = p.seed();
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r, Colors.argb(230, GOLD), VfxTextures.RUNE_CIRCLE, life).spin(0.01f).energy().timing(0.06f, 0.1f).satellites(6));
        VfxManager.add(new DecalVfx(g.add(0, 0.06, 0), UP, r * 0.55f, Colors.argb(230, WHITE_GOLD), VfxTextures.HEX, life).spin(-0.02f).energy().timing(0.08f, 0.1f));
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r * 1.1f, Colors.argb(130, GOLD), VfxTextures.GLOW, life).timing(0.08f, 0.12f));
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            Vec3 foot = MageFx.ground(g.add(Math.cos(a) * r, 0, Math.sin(a) * r));
            FxScheduler.after(i * 2, () -> VfxManager.add(new LightColumnVfx(foot, 6f, 0.5f, Colors.argb(200, WHITE_GOLD), life - 10)));
        }
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            float k = SpellVfx.envelope(t, 0.08f, 0.1f);
            var glow = ctx.additive(VfxTextures.GLOW);
            var star = ctx.additive(VfxTextures.STAR);
            for (int i = 0; i < 30; i++) {
                float ph = hash(seed, i);
                float cyc = (ticks * 0.02f + ph) % 1f;
                double a = ph * 6.283 + ticks * 0.01, rr = r * Math.sqrt(hash(seed, 40 + i));
                Vec3 q = g.add(Math.cos(a) * rr, 0.2 + cyc * 3.5, Math.sin(a) * rr);
                float al = k * (float) Math.sin(cyc * Math.PI);
                ctx.billboard(glow, q, 0.3f, 0, Colors.alpha(al * 0.7f, GOLD));
                if (i % 3 == 0) ctx.billboard(star, q, 0.25f, ticks * 0.05f, Colors.alpha(al, WHITE_GOLD));
            }
        }));
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.07, 0), UP, 0.3f, r * 1.1f, 0.4f, Colors.argb(220, WHITE_GOLD), 14).energy());
        MageFx.hit(p, GOLD);
    }

    /** The dawn: a vast column of sunlight pouring down, a sun rising in it, light spreading over the ground, rays fanning out. */
    private static void dawn(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        long seed = p.seed();
        VfxManager.add(new LightColumnVfx(g, 34f, r * 0.55f, Colors.argb(200, WHITE_GOLD), 46));
        VfxManager.add(new LightColumnVfx(g, 34f, r * 0.25f, Colors.argb(240, 0xFFFFFF), 32));
        VfxManager.add(new SunVfx(g.add(0, 14, 0), 2.2f, GOLD, WHITE_GOLD, 50).grow(10).rays(18).path(t -> g.add(0, 6 + Math.min(1, t / 30f) * 10, 0)));
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r, Colors.argb(220, GOLD), VfxTextures.RUNE_CIRCLE, 60).spin(0.02f).energy().timing(0.1f, 0.4f).satellites(8));
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r * 1.2f, Colors.argb(150, WHITE_GOLD), VfxTextures.GLOW, 60).timing(0.1f, 0.5f));
        for (int i = 0; i < 3; i++) {
            int d = i * 5;
            FxScheduler.after(d, () -> VfxManager.add(new ShockwaveVfx(g.add(0, 0.08, 0), UP, 0.4f, r * 1.2f, 0.5f, Colors.argb(220, d == 0 ? 0xFFFFFF : GOLD), 18).energy()));
        }
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            Vec3 d = new Vec3(Math.cos(a), 0.08, Math.sin(a)).normalize();
            VfxManager.add(new BeamVfx(g.add(0, 1, 0), g.add(0, 1, 0).add(d.scale(r * 1.1)), 0.5f, Colors.argb(220, i % 2 == 0 ? GOLD : WHITE_GOLD), 12 + i % 4));
        }
        VfxManager.add(new ShardBurstVfx(g.add(0, 1, 0), UP, 1.4f, 0.3f, 40, 0.18f, Colors.argb(255, WHITE_GOLD), Colors.argb(0, GOLD), 40, seed)
                .texture(VfxTextures.STAR, false).physics(-0.004f, 0.94f));
        for (int id : p.entities()) {
            Entity e = MageFx.entity(id);
            if (e == null) continue;
            VfxManager.add(new BeamVfx(e.position().add(0, 12, 0), e.position(), 0.8f, Colors.argb(230, WHITE_GOLD), 14));
            VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, e.getBbHeight() * 1.8f, Colors.argb(220, GOLD), 14).follow(e, new Vec3(0, e.getBbHeight() * 0.5, 0)).energy());
        }
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.position().distanceTo(g) < r * 1.5) ScreenFx.flash(WHITE_GOLD, 0.35f, 16);
        CameraShake.add(g, 0.6f, r * 3);
    }

    /** The hymn: a ribbon of light sung out to the ally, a ring of notes spreading round them, wind about everyone who hears. */
    private static void hymn(FxPayload p) {
        Vec3 from = p.pos(), to = p.points().isEmpty() ? from : p.points().get(0);
        float r = p.scale();
        long seed = p.seed();
        int travel = 7;
        OrbVfx orb = VfxManager.add(new OrbVfx(MageFx.arcTo(from, () -> to, travel, 1.4), 0.3f, LIGHT.main(), WHITE_GOLD, travel + 1).sprite(VfxTextures.STAR, WHITE_GOLD, 0.5f));
        VfxManager.add(new FollowTrailVfx(orb::now, 0.45f, Colors.argb(230, LIGHT.main()), Colors.argb(0, GOLD), 14, travel + 16).texture(VfxTextures.STREAK, true));
        FxScheduler.after(travel, () -> {
            Vec3 g = MageFx.ground(to.subtract(0, 0.8, 0));
            VfxManager.add(new SpellVfx(30, (ctx, t, ticks) -> {
                float k = SpellVfx.envelope(t, 0.05f, 0.5f);
                var star = ctx.additive(VfxTextures.STAR);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI * 2 / 12 + ticks * 0.08;
                    double rr = r * Math.min(1, ticks / 10f);
                    Vec3 q = g.add(Math.cos(a) * rr, 1 + 0.4 * Math.sin(ticks * 0.3 + i), Math.sin(a) * rr);
                    ctx.billboard(star, q, 0.4f, ticks * 0.1f + i, Colors.alpha(k, i % 2 == 0 ? WHITE_GOLD : LIGHT.main()));
                }
                ctx.ring(ctx.additive(VfxTextures.RING), g.add(0, 0.07, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), r * 0.9f * Math.min(1, ticks / 10f),
                        r * Math.min(1, ticks / 10f), 48, Colors.alpha(k * 0.6f, LIGHT.main()));
            }));
            for (int id : p.entities()) {
                Entity e = MageFx.entity(id);
                if (e == null) continue;
                java.util.function.Supplier<Vec3> at = MageFx.chest(e);
                VfxManager.add(new SpellVfx(20, (ctx, t, ticks) -> {
                    Vec3 c = at.get();
                    if (c == null) return;
                    var streak = ctx.additive(VfxTextures.STREAK);
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3 + ticks * 0.4;
                        Vec3 q = c.add(Math.cos(a) * 0.7, -0.6 + i * 0.2, Math.sin(a) * 0.7);
                        ctx.stretched(streak, q, new Vec3(-Math.sin(a), 0.2, Math.cos(a)), 1f, 0.07f, Colors.alpha(SpellVfx.envelope(t, 0.1f, 0.5f) * 0.8f, WHITE_GOLD));
                    }
                }));
            }
        });
    }

    /** Ascension: a great circle of light, feathers falling, and a ray reaching down onto every ally lifted up. */
    private static void ascension(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int life = Math.round(p.power());
        long seed = p.seed();
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r, Colors.argb(220, WHITE_GOLD), VfxTextures.RUNE_CIRCLE, 70).spin(0.015f).energy().timing(0.08f, 0.4f).satellites(12));
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.07, 0), UP, 0.4f, r * 1.1f, 0.5f, Colors.argb(220, WHITE_GOLD), 18).energy());
        VfxManager.add(new SpellVfx(Math.min(life, 120), (ctx, t, ticks) -> {
            float k = SpellVfx.envelope(t, 0.05f, 0.3f);
            var petal = ctx.translucent(VfxTextures.PETAL_SOFT);
            var glow = ctx.additive(VfxTextures.GLOW);
            for (int i = 0; i < 40; i++) {
                float ph = hash(seed, i);
                float cyc = (ticks * 0.012f + ph) % 1f;
                double a = ph * 6.283, rr = r * Math.sqrt(hash(seed, 50 + i));
                Vec3 q = g.add(Math.cos(a) * rr + Math.sin(cyc * 9 + ph) * 0.5, 9 * (1 - cyc), Math.sin(a) * rr);
                ctx.billboard(petal, q, 0.35f, cyc * 10 + ph * 6, Colors.alpha(k * (float) Math.sin(cyc * Math.PI), 0xFFFFFF));
                ctx.billboard(glow, q, 0.4f, 0, Colors.alpha(k * 0.4f * (float) Math.sin(cyc * Math.PI), WHITE_GOLD));
            }
        }));
        for (int id : p.entities()) {
            Entity e = MageFx.entity(id);
            if (e == null) continue;
            VfxManager.add(new BeamVfx(e.position().add(0, 16, 0), e.position(), 1.2f, Colors.argb(220, WHITE_GOLD), 18));
            VfxManager.add(new ShockwaveVfx(e.position().add(0, 0.06, 0), UP, 0.2f, 2f, 0.3f, Colors.argb(200, LIGHT.main()), 12).energy());
        }
        VfxManager.add(new FlashVfx(g.add(0, 1, 0), 0.5f, 4f, Colors.argb(200, WHITE_GOLD), 14).energy());
        CameraShake.add(g, 0.3f, r * 2);
    }
}
