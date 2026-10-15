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
import com.fantasyweapons.client.vfx.effects.OrbVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SnowfallVfx;
import com.fantasyweapons.client.vfx.effects.SpellVfx;
import com.fantasyweapons.client.vfx.effects.SpikeVfx;
import com.fantasyweapons.client.vfx.effects.StormCloudVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.fantasyweapons.client.fx.mage.MageFx.ICE;
import static com.fantasyweapons.client.fx.mage.MageFx.UP;
import static com.fantasyweapons.client.fx.mage.MageFx.hash;

/** Client visuals for the ice mage: the Rimeheart's rampart and bastion; the Frostbound Grimoire's tomb and winter. */
final class IceMageFx {
    static final int FROST = 0xCFF4FF, DEEP_ICE = 0x5AA8E0;

    private IceMageFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.RIME_RAMPART, IceMageFx::rampart);
        FxDispatcher.register(FxIds.RIME_BASTION, IceMageFx::bastion);
        FxDispatcher.register(FxIds.FROST_TOMB, IceMageFx::tomb);
        FxDispatcher.register(FxIds.FROST_TOMB_SHATTER, IceMageFx::tombShatter);
        FxDispatcher.register(FxIds.FROST_WINTER, IceMageFx::winter);
        FxDispatcher.register(FxIds.FROST_WINTER_FREEZE, IceMageFx::winterFreeze);
    }

    /** A wall of ice columns bursting up in a line, frost spreading at its foot, then sinking away. */
    private static void rampart(FxPayload p) {
        Vec3 c = p.pos(), across = p.dir().lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : p.dir().normalize();
        float width = p.scale();
        int life = Math.round(p.power());
        long seed = p.seed();
        int n = Math.max(5, Math.round(width * 1.6f));
        SpikeVfx wall = new SpikeVfx(SpikeVfx.Style.ICE, ICE.main(), ICE.core(), life + 10).timing(5, 10);
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1) - 0.5f;
            Vec3 base = MageFx.ground(c.add(across.scale(u * width)));
            float h = 2.8f + 0.9f * hash(seed, i) - Math.abs(u) * 0.8f;
            Vec3 dir = UP.add(across.scale((hash(seed, 50 + i) - 0.5) * 0.25)).normalize();
            wall.add(base, dir, h, 0.55f + 0.2f * hash(seed, 80 + i), i);
            if (i % 2 == 0) wall.add(base.add(across.scale(0.3)), UP.add(across.scale(0.4)).normalize(), h * 0.5f, 0.3f, i + 1);
        }
        VfxManager.add(wall);
        for (int i = 0; i < n; i += 2) {
            Vec3 base = MageFx.ground(c.add(across.scale((i / (float) (n - 1) - 0.5f) * width)));
            VfxManager.add(new DecalVfx(base.add(0, 0.04, 0), UP, 1.4f, Colors.argb(200, FROST), VfxTextures.FROST, life + 20).timing(0.05f, 0.3f));
        }
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            float k = SpellVfx.envelope(t, 0.05f, 0.1f);
            var mist = ctx.translucent(VfxTextures.MIST);
            for (int i = 0; i < n; i++) {
                float ph = hash(seed, 200 + i);
                Vec3 q = MageFx.ground(c.add(across.scale((i / (float) (n - 1) - 0.5f) * width))).add(0, 0.3 + 0.2 * Math.sin(ticks * 0.05 + ph * 9), 0);
                ctx.billboard(mist, q, 1.6f, ph * 6 + ticks * 0.01f, Colors.alpha(k * 0.3f, FROST));
            }
        }));
        VfxManager.add(new ShardBurstVfx(c.add(0, 1, 0), UP, 1.5f, 0.2f, 24, 0.2f, Colors.argb(240, ICE.core()), Colors.argb(0, ICE.main()), 18, seed)
                .texture(VfxTextures.SHARD, true).physics(0.03f, 0.94f).translucent());
        CameraShake.add(c, 0.4f, 14);
        MageFx.hit(p, ICE.main());
    }

    /** A ring of towering ice spires around a great snowflake rune, snow falling inside, a shimmering ring between them. */
    private static void bastion(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int life = Math.round(p.power()) + 10;
        long seed = p.seed();
        SpikeVfx spires = new SpikeVfx(SpikeVfx.Style.CRYSTAL, ICE.main(), ICE.core(), life).timing(10, 14);
        int towers = 8;
        for (int i = 0; i < towers; i++) {
            double a = i * Math.PI * 2 / towers;
            Vec3 base = MageFx.ground(g.add(Math.cos(a) * r, 0, Math.sin(a) * r));
            Vec3 lean = new Vec3(Math.cos(a), 0, Math.sin(a)).scale(0.12).add(UP).normalize();
            spires.add(base, lean, 7.5f + 1.5f * hash(seed, i), 1.1f, i * 2);
            // smaller spurs at each tower's foot and between them
            spires.add(base.add(Math.cos(a + 0.5) * 0.8, 0, Math.sin(a + 0.5) * 0.8), lean.add(Math.cos(a) * 0.4, 0, Math.sin(a) * 0.4).normalize(), 3.2f, 0.5f, i * 2 + 3);
            double b = a + Math.PI / towers;
            Vec3 mid = MageFx.ground(g.add(Math.cos(b) * r, 0, Math.sin(b) * r));
            spires.add(mid, UP, 3.5f + hash(seed, 40 + i), 0.6f, i * 2 + 2);
        }
        VfxManager.add(spires);
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r * 0.95f, Colors.argb(220, ICE.main()), VfxTextures.SNOWFLAKE, life).spin(0.01f).energy().timing(0.06f, 0.1f));
        VfxManager.add(new DecalVfx(g.add(0, 0.06, 0), UP, r * 1.05f, Colors.argb(160, FROST), VfxTextures.RUNE_CIRCLE, life).spin(-0.006f).energy().timing(0.08f, 0.1f).satellites(8));
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r * 1.1f, Colors.argb(190, FROST), VfxTextures.FROST, life).translucent().timing(0.1f, 0.15f));
        VfxManager.add(new SnowfallVfx(g, r * 0.95f, 9, 70, Colors.argb(230, 0xFFFFFF), life, seed));
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            float k = SpellVfx.envelope(t, 0.1f, 0.08f);
            // a shimmering curtain of light joining the spires
            var streak = ctx.additive(VfxTextures.STREAK);
            for (int band = 0; band < 2; band++) {
                double h = 1.5 + band * 2.2;
                ctx.ring(streak, g.add(0, h + 0.15 * Math.sin(ticks * 0.08 + band), 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), r - 0.1f, r + 0.1f, 64,
                        Colors.alpha(k * (0.35f - band * 0.1f), ICE.core()));
            }
            var glow = ctx.additive(VfxTextures.GLOW);
            for (int i = 0; i < 24; i++) {
                float ph = hash(seed, 300 + i);
                double a = ph * 6.283 + ticks * 0.01, rr = r * Math.sqrt(hash(seed, 330 + i));
                float cyc = (ticks * 0.02f + ph) % 1f;
                ctx.billboard(glow, g.add(Math.cos(a) * rr, 0.3 + cyc * 4, Math.sin(a) * rr), 0.25f, 0, Colors.alpha(k * (float) Math.sin(cyc * Math.PI), ICE.core()));
            }
        }));
        ScreenFx.zoneVignette(ICE.main(), 0.18f, life, g, r);
        CameraShake.add(g, 0.6f, r * 3);
    }

    /** A lance of frost to the foe, then crystals closing over it. */
    private static void tomb(FxPayload p) {
        Vec3 from = p.pos(), to = p.points().isEmpty() ? from : p.points().get(0);
        int ticks = Math.max(10, Math.round(p.power()));
        VfxManager.add(new BeamVfx(from, to, 0.5f, Colors.argb(230, ICE.main()), 8));
        OrbVfx shard = VfxManager.add(new OrbVfx(t -> from.lerp(to, Math.min(1, t / 3)), 0.35f, ICE.main(), ICE.core(), 4).sprite(VfxTextures.SHARD, ICE.core(), 0));
        VfxManager.add(new FollowTrailVfx(shard::now, 0.35f, Colors.argb(240, ICE.core()), Colors.argb(0, ICE.main()), 8, 14).texture(VfxTextures.LIGHTNING, false));
        Entity e = (p.entities().length == 0) ? null : MageFx.entity(p.entities()[0]);
        if (e == null) return;
        FxScheduler.after(3, () -> {
            Vec3 feet = e.position();
            float w = e.getBbWidth(), h = e.getBbHeight();
            SpikeVfx crystals = new SpikeVfx(SpikeVfx.Style.CRYSTAL, ICE.main(), ICE.core(), ticks).timing(3, 4);
            for (int i = 0; i < 7; i++) {
                double a = i * Math.PI * 2 / 7 + p.seed() % 7;
                Vec3 base = feet.add(Math.cos(a) * (w * 0.7 + 0.25), 0, Math.sin(a) * (w * 0.7 + 0.25));
                Vec3 dir = new Vec3(-Math.cos(a) * 0.35, 1, -Math.sin(a) * 0.35).normalize();
                crystals.add(base, dir, h * (0.7f + 0.4f * hash(p.seed(), i)), 0.35f, i % 3);
            }
            VfxManager.add(crystals);
            VfxManager.add(new FlashVfx(feet.add(0, h * 0.5, 0), 0.3f, h * 1.6f, Colors.argb(220, ICE.core()), 8).energy());
            VfxManager.add(new DecalVfx(feet.add(0, 0.04, 0), UP, w + 1.2f, Colors.argb(220, FROST), VfxTextures.FROST, ticks + 10).timing(0.05f, 0.3f));
        });
    }

    private static void tombShatter(FxPayload p) {
        Vec3 c = p.pos();
        VfxManager.add(new ShardBurstVfx(c, UP, 1.6f, 0.3f, 34, 0.3f, Colors.argb(240, ICE.core()), Colors.argb(0, ICE.main()), 26, p.seed())
                .texture(VfxTextures.SHARD, true).physics(0.035f, 0.95f).translucent());
        VfxManager.add(new FlashVfx(c, 0.3f, 2.4f, Colors.argb(220, ICE.main()), 8).energy());
        VfxManager.add(new ShockwaveVfx(MageFx.ground(c).add(0, 0.06, 0), UP, 0.2f, 2.6f, 0.35f, Colors.argb(200, FROST), 12).energy());
        CameraShake.add(c, 0.3f, 12);
    }

    /** The blizzard: a heavy pale cloud overhead, snow driving round in a vortex, frost creeping across the ground. */
    private static void winter(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int life = Math.round(p.power()) + 10;
        long seed = p.seed();
        VfxManager.add(new StormCloudVfx(() -> g.add(0, 9, 0), r * 1.05f, 0x8CA6B8, 0xE8FBFF, life, seed).dense(1.4f));
        VfxManager.add(new SnowfallVfx(g, r, 9, 140, Colors.argb(240, 0xFFFFFF), life, seed));
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r, Colors.argb(200, FROST), VfxTextures.FROST, life).translucent().timing(0.25f, 0.15f));
        VfxManager.add(new DecalVfx(g.add(0, 0.06, 0), UP, r * 0.7f, Colors.argb(110, ICE.main()), VfxTextures.SNOWFLAKE, life).spin(0.02f).energy().timing(0.15f, 0.1f));
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            float k = SpellVfx.envelope(t, 0.1f, 0.1f);
            var mist = ctx.translucent(VfxTextures.MIST);
            var streak = ctx.additive(VfxTextures.STREAK);
            // the vortex: drifts of snow and wind streaks wheeling round the middle
            for (int i = 0; i < 26; i++) {
                float ph = hash(seed, i);
                double a = ph * 6.283 + ticks * (0.05 + 0.04 * ph);
                double rr = r * (0.2 + 0.8 * hash(seed, 40 + i));
                double h = 0.4 + 5 * hash(seed, 80 + i);
                Vec3 q = g.add(Math.cos(a) * rr, h, Math.sin(a) * rr);
                Vec3 tangent = new Vec3(-Math.sin(a), -0.15, Math.cos(a));
                ctx.billboard(mist, q, 2.2f + 1.5f * ph, ph * 6 + ticks * 0.02f, Colors.alpha(k * 0.28f, 0xE8F6FF));
                ctx.streak(streak, q.add(0, 0.5, 0), tangent, 2.4f, 0.08f, Colors.alpha(k * 0.5f, 0xFFFFFF));
            }
        }));
        ScreenFx.zoneVignette(0xBFE6FF, 0.3f, life, g, r);
        CameraShake.add(g, 0.4f, r * 3);
    }

    /** Enemies freezing solid in the blizzard: a burst of crystal round each. */
    private static void winterFreeze(FxPayload p) {
        int ticks = Math.max(10, Math.round(p.power()));
        for (int id : p.entities()) {
            Entity e = MageFx.entity(id);
            if (e == null) continue;
            Vec3 feet = e.position();
            float w = e.getBbWidth(), h = e.getBbHeight();
            SpikeVfx crystals = new SpikeVfx(SpikeVfx.Style.CRYSTAL, ICE.main(), ICE.core(), ticks).timing(2, 4);
            for (int i = 0; i < 5; i++) {
                double a = i * Math.PI * 2 / 5 + id;
                crystals.add(feet.add(Math.cos(a) * (w * 0.6 + 0.2), 0, Math.sin(a) * (w * 0.6 + 0.2)), new Vec3(-Math.cos(a) * 0.3, 1, -Math.sin(a) * 0.3).normalize(),
                        h * 0.8f, 0.3f, i);
            }
            VfxManager.add(crystals);
            VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, h * 1.6f, Colors.argb(220, ICE.core()), 8).follow(e, new Vec3(0, h * 0.5, 0)).energy());
        }
    }
}
