package com.fantasyweapons.client.fx.mage;

import com.fantasyweapons.client.fx.CameraShake;
import com.fantasyweapons.client.fx.FxDispatcher;
import com.fantasyweapons.client.fx.FxScheduler;
import com.fantasyweapons.client.fx.GroundShatter;
import com.fantasyweapons.client.fx.ScreenFx;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.BeamVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.OrbVfx;
import com.fantasyweapons.client.vfx.effects.SeekerVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SoulRiftVfx;
import com.fantasyweapons.client.vfx.effects.SpellVfx;
import com.fantasyweapons.client.vfx.effects.TetherVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.fantasyweapons.client.fx.mage.MageFx.UP;
import static com.fantasyweapons.client.fx.mage.MageFx.VOID;
import static com.fantasyweapons.client.fx.mage.MageFx.hash;

/** Client visuals for the void mage: the Gloamreach's curse, miasma and dirge; the Umbral Codex's hex, seeds and plague. */
final class VoidMageFx {
    static final int ROT = 0x6A8A3A, BLIGHT = 0xA6D86A, SHADOW = 0x140620;

    private VoidMageFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.GLOAM_WITHER, VoidMageFx::wither);
        FxDispatcher.register(FxIds.GLOAM_MIASMA, VoidMageFx::miasma);
        FxDispatcher.register(FxIds.GLOAM_DIRGE, VoidMageFx::dirge);
        FxDispatcher.register(FxIds.UMBRAL_HEX, VoidMageFx::hex);
        FxDispatcher.register(FxIds.UMBRAL_SEED, VoidMageFx::seed);
        FxDispatcher.register(FxIds.UMBRAL_CONTAGION, VoidMageFx::contagion);
        FxDispatcher.register(FxIds.UMBRAL_PLAGUE, VoidMageFx::plague);
    }

    /** A sigil of withering flies to the foe and sinks into it; the curse coils round it while it lasts. */
    private static void wither(FxPayload p) {
        Vec3 from = p.pos();
        Entity e = (p.entities().length == 0) ? null : MageFx.entity(p.entities()[0]);
        if (e == null) return;
        SeekerVfx s = VfxManager.add(new SeekerVfx(from, MageFx.chest(e).get().subtract(from).normalize().scale(0.9), e.getId(), 0.3, 0.4f, VOID.main(), VOID.core(), 16)
                .sprite(VfxTextures.SWIRL));
        VfxManager.add(new FollowTrailVfx(s::now, 0.4f, Colors.argb(220, VOID.main()), Colors.argb(0, SHADOW), 14, 30).texture(VfxTextures.STREAK, true));
        int travel = Math.max(3, (int) (from.distanceTo(e.position()) / 1.2));
        FxScheduler.after(travel, () -> {
            if (e.isRemoved()) return;
            float h = e.getBbHeight();
            VfxManager.add(new DecalVfx(e.position().add(0, h + 0.6, 0), MageFx.camFacing(e.position()), 0.8f, Colors.argb(230, VOID.main()), VfxTextures.RUNE_CIRCLE, 22)
                    .spin(0.2f).energy().timing(0.1f, 0.5f));
            VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, h * 1.6f, Colors.argb(220, VOID.main()), 10).follow(e, new Vec3(0, h * 0.5, 0)).energy());
            VfxManager.add(new ShardBurstVfx(e.position().add(0, h * 0.5, 0), UP, 1.4f, 0.12f, 14, 0.2f, Colors.argb(240, VOID.core()), Colors.argb(0, SHADOW), 18, p.seed())
                    .texture(VfxTextures.SWIRL, false).attract(e.position().add(0, h * 0.5, 0), 0.05f));
        });
    }

    /** A low cloud of void miasma: dark puffs turning slowly, motes of blight in it, decay spreading on the ground beneath. */
    private static void miasma(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int life = Math.round(p.power()) + 10;
        long seed = p.seed();
        GroundShatter.cracks(g, r * 0.9f, 0x0A0410, life, VOID.main(), VOID.core(), seed);
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r, Colors.argb(180, SHADOW), VfxTextures.GLOW, life).translucent().timing(0.1f, 0.15f));
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            float k = SpellVfx.envelope(t, 0.12f, 0.15f);
            var mist = ctx.translucent(VfxTextures.MIST);
            var glow = ctx.additive(VfxTextures.GLOW);
            var soul = ctx.additive(VfxTextures.SOUL);
            for (int i = 0; i < 22; i++) {
                float ph = hash(seed, i);
                double a = ph * 6.283 + ticks * 0.01 * (i % 2 == 0 ? 1 : -1), rr = r * 0.8 * Math.sqrt(hash(seed, 30 + i));
                Vec3 q = g.add(Math.cos(a) * rr, 0.6 + 1.6 * hash(seed, 60 + i) + 0.2 * Math.sin(ticks * 0.03 + ph * 8), Math.sin(a) * rr);
                int col = Colors.lerpRgb(0x2A1440, 0x3A4A20, hash(seed, 90 + i));
                ctx.billboard(mist, q, 3.4f + 1.6f * ph, ph * 6 + ticks * 0.008f, Colors.alpha(k * 0.55f, col));
            }
            for (int i = 0; i < 40; i++) {
                float ph = hash(seed, 100 + i);
                float cyc = (ticks * 0.02f + ph) % 1f;
                double a = ph * 6.283 + ticks * 0.02, rr = r * Math.sqrt(hash(seed, 150 + i));
                Vec3 q = g.add(Math.cos(a) * rr, 0.3 + cyc * 2.6, Math.sin(a) * rr);
                ctx.billboard(glow, q, 0.14f, 0, Colors.alpha(k * (float) Math.sin(cyc * Math.PI), i % 3 == 0 ? BLIGHT : VOID.main()));
            }
            for (int i = 0; i < 5; i++) {
                float ph = hash(seed, 200 + i);
                float cyc = (ticks * 0.015f + ph) % 1f;
                double a = ph * 6.283 + cyc * 3, rr = r * 0.6 * hash(seed, 220 + i);
                ctx.billboard(soul, g.add(Math.cos(a) * rr, 0.5 + cyc * 3.5, Math.sin(a) * rr), 0.6f, 0, Colors.alpha(k * 0.7f * (float) Math.sin(cyc * Math.PI), 0xC8A0FF));
            }
        }));
        MageFx.hit(p, VOID.main());
    }

    /** The dirge: a vast dark sigil on the ground, three tolls ringing out across it, a pall falling on every one marked. */
    private static void dirge(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        long seed = p.seed();
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r, Colors.argb(230, VOID.main()), VfxTextures.RUNE_CIRCLE, 80).spin(-0.012f).energy().timing(0.1f, 0.4f).satellites(9));
        VfxManager.add(new DecalVfx(g.add(0, 0.06, 0), UP, r * 0.5f, Colors.argb(230, VOID.core()), VfxTextures.HEX, 80).spin(0.03f).energy().timing(0.12f, 0.4f));
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r * 1.15f, Colors.argb(200, SHADOW), VfxTextures.GLOW, 80).translucent().timing(0.1f, 0.4f));
        for (int i = 0; i < 3; i++) {
            int d = i * 10;
            FxScheduler.after(d, () -> {
                VfxManager.add(new ShockwaveVfx(g.add(0, 0.08, 0), UP, 0.4f, r * 1.2f, 0.6f, Colors.argb(220, VOID.main()), 20).energy());
                CameraShake.add(g, 0.35f, r * 2.5);
            });
        }
        for (int id : p.entities()) {
            Entity e = MageFx.entity(id);
            if (e == null) continue;
            float h = e.getBbHeight();
            VfxManager.add(new BeamVfx(e.position().add(0, 10, 0), e.position(), 0.7f, Colors.argb(220, VOID.main()), 14));
            VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, h * 1.6f, Colors.argb(220, SHADOW), 14).follow(e, new Vec3(0, h * 0.5, 0)));
        }
        GroundShatter.cracks(g, r * 0.8f, 0x0A0410, 80, VOID.main(), VOID.core(), seed);
        ScreenFx.zoneVignette(VOID.deep(), 0.3f, 60, g, r);
    }

    /** The leeching hex: a thread of shadow from the book to the foe, its life flowing back along it. */
    private static void hex(FxPayload p) {
        Entity caster = MageFx.entity(p.caster());
        Entity e = (p.entities().length == 0) ? null : MageFx.entity(p.entities()[0]);
        if (e == null || caster == null) return;
        int life = Math.max(10, Math.round(p.power()));
        VfxManager.add(new TetherVfx(MageFx.focus(caster), MageFx.chest(e), 0.3f, VOID.main(), 0xFF6A9A, life).motes(VfxTextures.SOUL, 6, -1.4f).shape(0.4f, 0.3f).grow(5));
        VfxManager.add(new TetherVfx(MageFx.focus(caster), MageFx.chest(e), 0.14f, 0xC8203A, 0xFFB0C0, life).motes(VfxTextures.GLOW, 8, -2f).shape(0.4f, 0.3f).grow(5));
        VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, e.getBbHeight() * 1.5f, Colors.argb(220, VOID.main()), 10).follow(e, new Vec3(0, e.getBbHeight() * 0.5, 0)).energy());
    }

    /** A bane seed: a dark mote curving out from the book to bury itself in its host. */
    private static void seed(FxPayload p) {
        Entity e = (p.entities().length == 0) ? null : MageFx.entity(p.entities()[0]);
        if (e == null) return;
        int flight = Math.max(4, Math.round(p.power()));
        Vec3 from = p.pos();
        int side = p.level() % 3 - 1;
        java.util.function.Supplier<Vec3> to = MageFx.chest(e);
        java.util.function.Function<Float, Vec3> path = t -> {
            Vec3 b = to.get();
            if (b == null) b = from;
            float u = Math.min(1, t / flight);
            Vec3 d = b.subtract(from);
            Vec3 lat = d.cross(UP);
            lat = lat.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : lat.normalize();
            return from.lerp(b, u * u * (3 - 2 * u)).add(lat.scale(Math.sin(u * Math.PI) * 2.2 * side)).add(0, Math.sin(u * Math.PI) * 1.4, 0);
        };
        OrbVfx orb = VfxManager.add(new OrbVfx(path, 0.28f, VOID.main(), VOID.core(), flight + 1).sprite(VfxTextures.SOUL, 0xD8B0FF, 0.3f));
        VfxManager.add(new FollowTrailVfx(orb::now, 0.3f, Colors.argb(220, VOID.main()), Colors.argb(0, SHADOW), 12, flight + 14).texture(VfxTextures.STREAK, true));
        FxScheduler.after(flight, () -> {
            if (e.isRemoved()) return;
            VfxManager.add(new FlashVfx(Vec3.ZERO, 0.2f, e.getBbHeight(), Colors.argb(220, VOID.main()), 8).follow(e, new Vec3(0, e.getBbHeight() * 0.5, 0)).energy());
            VfxManager.add(new ShardBurstVfx(e.position().add(0, e.getBbHeight() * 0.5, 0), UP, 1.2f, 0.12f, 8, 0.18f, Colors.argb(240, VOID.core()), Colors.argb(0, SHADOW), 14,
                    e.getId() + p.level()).texture(VfxTextures.SHARD, true));
        });
    }

    private static void contagion(FxPayload p) {
        Vec3 from = p.pos();
        VfxManager.add(new FlashVfx(from, 0.3f, 2.4f, Colors.argb(220, VOID.main()), 10).energy());
        for (int id : p.entities()) {
            Entity e = MageFx.entity(id);
            if (e == null) continue;
            SeekerVfx s = VfxManager.add(new SeekerVfx(from, UP.scale(0.6), id, 0.25, 0.32f, VOID.main(), VOID.core(), 20).sprite(VfxTextures.SOUL));
            VfxManager.add(new FollowTrailVfx(s::now, 0.3f, Colors.argb(200, VOID.main()), Colors.argb(0, SHADOW), 12, 32).texture(VfxTextures.STREAK, true));
        }
    }

    /** The plague of night: a rift tearing open, a sickly cloud bursting out of it, wisps of plague leaping to every foe near. */
    private static void plague(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        long seed = p.seed();
        VfxManager.add(new SoulRiftVfx(g.add(0, 2.2, 0), 2.4f, VOID.main(), 0xD8B0FF, 70));
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.08, 0), UP, 0.4f, r * 1.2f, 0.6f, Colors.argb(220, ROT), 20).energy());
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.09, 0), UP, 0.3f, r * 0.9f, 0.4f, Colors.argb(220, VOID.main()), 16).energy());
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r, Colors.argb(180, 0x1A2410), VfxTextures.GLOW, 80).translucent().timing(0.1f, 0.4f));
        VfxManager.add(new SpellVfx(60, (ctx, t, ticks) -> {
            float k = SpellVfx.envelope(t, 0.05f, 0.5f);
            var mist = ctx.translucent(VfxTextures.MIST);
            for (int i = 0; i < 24; i++) {
                float ph = hash(seed, i);
                double a = ph * 6.283;
                double rr = r * Math.min(1, ticks / 20f) * (0.3 + 0.7 * hash(seed, 30 + i));
                Vec3 q = g.add(Math.cos(a) * rr, 0.6 + 1.5 * hash(seed, 60 + i), Math.sin(a) * rr);
                ctx.billboard(mist, q, 3f + 2f * ph, ph * 6 + ticks * 0.01f, Colors.alpha(k * 0.45f, Colors.lerpRgb(0x3A4A20, 0x2A1440, ph)));
            }
        }));
        for (int id : p.entities()) {
            Entity e = MageFx.entity(id);
            if (e == null) continue;
            OrbVfx wisp = VfxManager.add(new OrbVfx(MageFx.arcTo(g.add(0, 2.2, 0), MageFx.chest(e), 12, 2), 0.3f, ROT, BLIGHT, 13).sprite(VfxTextures.SOUL, BLIGHT, 0.2f));
            VfxManager.add(new FollowTrailVfx(wisp::now, 0.3f, Colors.argb(200, ROT), Colors.argb(0, SHADOW), 12, 26).texture(VfxTextures.STREAK, true));
        }
        CameraShake.add(g, 0.5f, r * 3);
        ScreenFx.zoneVignette(0x3A4A20, 0.25f, 50, g, r);
    }
}
