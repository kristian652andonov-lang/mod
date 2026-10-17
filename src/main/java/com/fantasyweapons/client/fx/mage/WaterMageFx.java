package com.fantasyweapons.client.fx.mage;

import com.fantasyweapons.client.fx.CameraShake;
import com.fantasyweapons.client.fx.FxDispatcher;
import com.fantasyweapons.client.fx.FxScheduler;
import com.fantasyweapons.client.fx.ScreenFx;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.OrbVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SpellVfx;
import com.fantasyweapons.client.vfx.effects.SphereVfx;
import com.fantasyweapons.client.vfx.effects.StormCloudVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.fantasyweapons.client.fx.mage.MageFx.UP;
import static com.fantasyweapons.client.fx.mage.MageFx.WATER;
import static com.fantasyweapons.client.fx.mage.MageFx.hash;

/** Client visuals for the water mage: the Tidecaller's spring, surge and maelstrom; the Codex of Tides' rain and embrace. */
final class WaterMageFx {
    static final int FOAM = 0xF2FCFF, TEAL = 0x2FD0D8;

    private WaterMageFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.TIDE_SPRING, WaterMageFx::spring);
        FxDispatcher.register(FxIds.TIDE_RIPTIDE, WaterMageFx::riptide);
        FxDispatcher.register(FxIds.TIDE_MAELSTROM, WaterMageFx::maelstrom);
        FxDispatcher.register(FxIds.TIDES_RAIN, WaterMageFx::rain);
        FxDispatcher.register(FxIds.TIDES_EMBRACE, WaterMageFx::embrace);
        FxDispatcher.register(FxIds.TIDES_BREAK, WaterMageFx::tideBreak);
    }

    /** Clear water welling up: a glowing pool rippling outwards, a fountain at its heart, bubbles and glints rising. */
    private static void spring(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int life = Math.round(p.power());
        long seed = p.seed();
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r, Colors.argb(170, WATER.main()), VfxTextures.GLOW, life).timing(0.1f, 0.15f));
        VfxManager.add(new DecalVfx(g.add(0, 0.06, 0), UP, r * 0.9f, Colors.argb(200, TEAL), VfxTextures.RUNE_CIRCLE, life).spin(0.02f).energy().timing(0.1f, 0.15f));
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            float k = SpellVfx.envelope(t, 0.08f, 0.12f);
            // ripples running out across the pool
            var ring = ctx.additive(VfxTextures.RING);
            for (int i = 0; i < 3; i++) {
                float u = ((ticks / 25f) + i / 3f) % 1f;
                ctx.ring(ring, g.add(0, 0.07, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), r * u * 0.9f, r * u, 48, Colors.alpha(k * 0.6f * (1 - u), FOAM));
            }
            // the fountain: jets rising and falling back as droplets
            var drop = ctx.additive(VfxTextures.GLOW);
            var streak = ctx.additive(VfxTextures.STREAK);
            for (int i = 0; i < 26; i++) {
                float ph = hash(seed, i);
                float cyc = (ticks * 0.05f + ph) % 1f;
                double a = ph * 6.283;
                double out = 0.25 + 1.4 * cyc * hash(seed, 40 + i);
                double y = 2.6 * (4 * cyc * (1 - cyc));
                Vec3 q = g.add(Math.cos(a) * out, 0.1 + y, Math.sin(a) * out);
                ctx.billboard(drop, q, 0.18f, 0, Colors.alpha(k * 0.9f, FOAM));
                if (cyc < 0.5f) ctx.streak(streak, q, UP, 0.5f, 0.07f, Colors.alpha(k * 0.6f, WATER.core()));
            }
            ctx.softBeam(ctx.additive(VfxTextures.STREAK), g, g.add(0, 2.2, 0), 0.35f, Colors.alpha(k * 0.6f, WATER.core()), -ticks * 0.4f, -ticks * 0.4f + 2, 0.05f, 0.5f);
            // bubbles and glints rising out of the pool
            for (int i = 0; i < 18; i++) {
                float ph = hash(seed, 100 + i);
                float cyc = (ticks * 0.03f + ph) % 1f;
                double a = ph * 6.283 + ticks * 0.02, rr = r * Math.sqrt(hash(seed, 130 + i)) * 0.9;
                ctx.billboard(drop, g.add(Math.cos(a) * rr, 0.2 + cyc * 2.5, Math.sin(a) * rr), 0.2f, 0, Colors.alpha(k * (float) Math.sin(cyc * Math.PI), WATER.core()));
            }
        }));
        VfxManager.add(new ShardBurstVfx(g.add(0, 0.3, 0), UP, 0.6f, 0.3f, 22, 0.18f, Colors.argb(240, FOAM), Colors.argb(0, WATER.main()), 22, seed)
                .texture(VfxTextures.GLOW, false).physics(0.025f, 0.95f));
    }

    /** A ring of surf bursting out from the point: a wall of water rolling outwards, foam on its crest. */
    private static void riptide(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int travel = Math.max(4, Math.round(p.power()));
        long seed = p.seed();
        VfxManager.add(new SpellVfx(travel + 12, (ctx, t, ticks) -> {
            float u = Math.min(1, ticks / travel);
            float rr = r * (0.15f + 0.85f * (1 - (1 - u) * (1 - u)));
            float k = ticks < travel ? 1 : Math.max(0, 1 - (ticks - travel) / 12f);
            float h = 1.6f * (float) Math.sin(Math.min(1, ticks / (travel + 6f)) * Math.PI) + 0.2f;
            int segs = 56;
            var water = ctx.additive(VfxTextures.STREAK);
            var foam = ctx.additive(VfxTextures.GLOW);
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                Vec3 b0 = g.add(Math.cos(a0) * rr, 0.05, Math.sin(a0) * rr), b1 = g.add(Math.cos(a1) * rr, 0.05, Math.sin(a1) * rr);
                double wob0 = 0.25 * Math.sin(a0 * 5 + ticks * 0.6), wob1 = 0.25 * Math.sin(a1 * 5 + ticks * 0.6);
                Vec3 t0 = b0.add(Math.cos(a0) * 0.4, h + wob0, Math.sin(a0) * 0.4), t1 = b1.add(Math.cos(a1) * 0.4, h + wob1, Math.sin(a1) * 0.4);
                ctx.quad(water, b0, b1, t1, t0, 0, 0, 1, 1, Colors.alpha(k * 0.55f, WATER.main()), Colors.alpha(k * 0.55f, WATER.main()),
                        Colors.alpha(k * 0.9f, WATER.core()), Colors.alpha(k * 0.9f, WATER.core()));
                if (i % 2 == 0) ctx.billboard(foam, t0, 0.6f, 0, Colors.alpha(k * 0.8f, FOAM));
            }
        }));
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.06, 0), UP, 0.3f, r * 1.1f, 0.5f, Colors.argb(200, FOAM), travel + 6).energy());
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r, Colors.argb(140, 0x0A2A4A), VfxTextures.GLOW, 50).translucent().timing(0.1f, 0.6f));
        VfxManager.add(new ShardBurstVfx(g.add(0, 0.5, 0), UP, 2.2f, 0.4f, 40, 0.22f, Colors.argb(240, FOAM), Colors.argb(0, WATER.main()), 26, seed)
                .texture(VfxTextures.GLOW, false).physics(0.03f, 0.95f));
        CameraShake.add(g, 0.45f, 16);
        MageFx.hit(p, WATER.main());
    }

    /** The whirlpool: spiralling currents across the ground, a funnel of water turning in the middle, spray in the air. */
    private static void maelstrom(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int life = Math.round(p.power()) + 10;
        long seed = p.seed();
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r * 1.05f, Colors.argb(160, WATER.deep()), VfxTextures.GLOW, life).timing(0.1f, 0.12f));
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r, Colors.argb(200, WATER.main()), VfxTextures.SWIRL, life).spin(0.08f).energy().timing(0.1f, 0.12f));
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            float k = SpellVfx.envelope(t, 0.08f, 0.1f);
            var streak = ctx.additive(VfxTextures.STREAK);
            var foam = ctx.additive(VfxTextures.GLOW);
            // spiral arms of current drawn in towards the centre
            int arms = 5, pts = 24;
            for (int arm = 0; arm < arms; arm++) {
                Vec3[] ps = new Vec3[pts];
                float[] ws = new float[pts];
                int[] cs = new int[pts];
                for (int i = 0; i < pts; i++) {
                    float u = i / (float) (pts - 1);
                    double a = arm * Math.PI * 2 / arms + u * 4.2 - ticks * 0.12;
                    double rr = r * (1 - u * 0.85);
                    ps[i] = g.add(Math.cos(a) * rr, 0.12 + u * 0.3, Math.sin(a) * rr);
                    ws[i] = 0.9f * (1 - u * 0.5f);
                    cs[i] = Colors.alpha(k * (0.3f + 0.6f * u) * Math.min(1, u * 5) * Math.min(1, (1 - u) * 6), Colors.lerpRgb(WATER.main(), FOAM, u));
                }
                ctx.ribbon(streak, ps, ws, cs, -ticks * 0.1f, 0.2f);
            }
            // the funnel: rings of water turning, narrowing as they climb
            var ring = ctx.additive(VfxTextures.RING);
            for (int i = 0; i < 9; i++) {
                double h = 0.4 + i * 0.75;
                float rr = 2.6f - i * 0.22f + 0.15f * (float) Math.sin(ticks * 0.2 + i);
                Vec3 c = g.add(0.3 * Math.sin(ticks * 0.1 + i * 0.6), h, 0.3 * Math.cos(ticks * 0.1 + i * 0.6));
                ctx.ring(ring, c, new Vec3(Math.cos(ticks * 0.15 + i), 0, Math.sin(ticks * 0.15 + i)), new Vec3(-Math.sin(ticks * 0.15 + i), 0.05, Math.cos(ticks * 0.15 + i)),
                        rr * 0.8f, rr, 32, Colors.alpha(k * 0.45f, i % 2 == 0 ? WATER.core() : TEAL));
            }
            // spray flung out of the current
            for (int i = 0; i < 30; i++) {
                float ph = hash(seed, i);
                float cyc = (ticks * 0.04f + ph) % 1f;
                double a = ph * 6.283 + ticks * 0.1;
                double rr = r * (0.3 + 0.7 * hash(seed, 50 + i));
                ctx.billboard(foam, g.add(Math.cos(a) * rr, 0.2 + 1.8 * cyc * (1 - cyc) * 4, Math.sin(a) * rr), 0.22f, 0, Colors.alpha(k * 0.8f * (1 - cyc), FOAM));
            }
        }));
        ScreenFx.zoneVignette(WATER.main(), 0.18f, life, g, r);
        CameraShake.add(g, 0.4f, r * 3);
    }

    /** A raincloud gathering over the place, then gentle rain falling from it, rippling where it lands. */
    private static void rain(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int life = Math.round(p.power()) + 10;
        long seed = p.seed();
        double top = 6.5;
        VfxManager.add(new StormCloudVfx(() -> g.add(0, top + 0.5, 0), r * 1.1f, 0x7C8FA8, 0xCDEFFF, life, seed).dense(1.2f));
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r, Colors.argb(150, WATER.main()), VfxTextures.GLOW, life).timing(0.2f, 0.15f));
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            float k = ticks < 15 ? 0 : SpellVfx.envelope(t, 0.15f, 0.1f);
            if (k <= 0) return;
            var streak = ctx.additive(VfxTextures.STREAK);
            var ring = ctx.additive(VfxTextures.RING);
            for (int i = 0; i < 90; i++) {
                float ph = hash(seed, i);
                float cyc = (ticks * (0.09f + 0.03f * ph) + ph) % 1f;
                double a = ph * 6.283, rr = r * Math.sqrt(hash(seed, 200 + i));
                Vec3 col = g.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
                Vec3 q = col.add(0, top * (1 - cyc), 0);
                ctx.streak(streak, q, UP, 0.9f, 0.05f, Colors.alpha(k * 0.6f, WATER.core()));
                if (cyc > 0.9f) {
                    float s = (cyc - 0.9f) / 0.1f;
                    ctx.ring(ring, MageFx.ground(col).add(0, 0.06, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.05f + s * 0.3f, 0.1f + s * 0.35f, 10,
                            Colors.alpha(k * 0.5f * (1 - s), FOAM));
                }
            }
        }));
    }

    /** The living wave: an orb of water leaping from ally to ally, splashing and healing at each. */
    private static void embrace(FxPayload p) {
        List<Vec3> pts = p.points();
        int hop = Math.max(2, Math.round(p.power()));
        if (pts.size() < 2) return;
        int total = (pts.size() - 1) * hop;
        java.util.function.Function<Float, Vec3> path = t -> {
            float f = Math.min(t / hop, pts.size() - 1.0001f);
            int i = (int) f;
            float u = f - i;
            Vec3 a = pts.get(i), b = pts.get(i + 1);
            return a.lerp(b, u).add(0, Math.sin(u * Math.PI) * (1.2 + a.distanceTo(b) * 0.15), 0);
        };
        OrbVfx orb = VfxManager.add(new OrbVfx(path, 0.55f, WATER.main(), FOAM, total + 4).sprite(VfxTextures.GLOW, FOAM, 0.2f));
        VfxManager.add(new FollowTrailVfx(orb::now, 0.9f, Colors.argb(230, WATER.main()), Colors.argb(0, WATER.deep()), 18, total + 24).texture(VfxTextures.STREAK, true));
        VfxManager.add(new FollowTrailVfx(orb::now, 0.3f, Colors.argb(255, FOAM), Colors.argb(0, WATER.main()), 10, total + 14).texture(VfxTextures.LIGHTNING, false));
        for (int i = 0; i < p.entities().length; i++) {
            Entity e = MageFx.entity(p.entities()[i]);
            int at = (i + 1) * hop;
            FxScheduler.after(at, () -> {
                if (e == null || e.isRemoved()) return;
                Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
                VfxManager.add(new SphereVfx(c, 0.3f, Math.max(e.getBbWidth(), e.getBbHeight()) * 0.8f, Colors.argb(140, WATER.core()), 10, SphereVfx.Mode.GROW));
                VfxManager.add(new ShardBurstVfx(c, UP, 1.4f, 0.22f, 20, 0.2f, Colors.argb(240, FOAM), Colors.argb(0, WATER.main()), 20, e.getId())
                        .texture(VfxTextures.GLOW, false).physics(0.03f, 0.94f));
                VfxManager.add(new FlashVfx(c, 0.3f, 2.2f, Colors.argb(200, WATER.main()), 9).energy());
                MageFx.healSparkle(e, WATER.main());
            });
        }
    }

    /**
     * The wave breaking as a great tide: a column of water bursts up, then a curling wall rolls out across the ground,
     * its crest leaning over and crashing down in foam; every ally it washes is left veiled in turning water.
     */
    private static void tideBreak(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int veil = Math.max(20, Math.round(p.power()));
        long seed = p.seed();
        int roll = 26;
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r * 1.05f, Colors.argb(150, WATER.deep()), VfxTextures.GLOW, roll + 30).timing(0.1f, 0.5f));
        VfxManager.add(new DecalVfx(g.add(0, 0.06, 0), UP, r * 0.8f, Colors.argb(210, TEAL), VfxTextures.RUNE_CIRCLE, roll + 24).spin(-0.03f).energy()
                .timing(0.12f, 0.4f));
        VfxManager.add(new SpellVfx(roll + 14, (ctx, t, ticks) -> {
            var water = ctx.additive(VfxTextures.STREAK);
            var foam = ctx.additive(VfxTextures.GLOW);
            var spray = ctx.translucent(VfxTextures.MIST);
            // the column: a burst of water straight up, falling back
            float col = Math.max(0, 1 - ticks / 14f);
            if (col > 0) {
                for (int i = 0; i < 10; i++) {
                    float ph = hash(seed, i);
                    double a = ph * 6.283;
                    Vec3 q = g.add(Math.cos(a) * 0.5 * ph, 0, Math.sin(a) * 0.5 * ph);
                    float h = (float) Math.sin(Math.min(1, ticks / 14f) * Math.PI) * (5 + 3 * ph);
                    ctx.streak(water, q.add(0, h * 0.5, 0), UP, h, 0.7f + ph * 0.5f, Colors.alpha(col * 0.8f, Colors.lerpRgb(WATER.main(), FOAM, ph)));
                }
                ctx.billboard(foam, g.add(0, 1.2, 0), 4.5f * col, 0, Colors.alpha(col * 0.7f, FOAM));
            }
            // the tide: a ring wall rolling out, its crest curling over and crashing down outside it
            float u = Math.min(1, Math.max(0, (ticks - 4) / roll));
            if (ticks < 4) return;
            float rr = r * (0.12f + 0.88f * (1 - (1 - u) * (1 - u)));
            float k = u < 0.8f ? 1 : (1 - u) / 0.2f;
            float rise = (float) Math.sin(Math.min(1, u * 1.6f) * Math.PI * 0.5) * 2.8f;
            float curl = u * u;
            int segs = 64;
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                double w0 = 0.35 * Math.sin(a0 * 6 + ticks * 0.5 + seed), w1 = 0.35 * Math.sin(a1 * 6 + ticks * 0.5 + seed);
                Vec3 o0 = new Vec3(Math.cos(a0), 0, Math.sin(a0)), o1 = new Vec3(Math.cos(a1), 0, Math.sin(a1));
                Vec3 b0 = g.add(o0.scale(rr)).add(0, 0.05, 0), b1 = g.add(o1.scale(rr)).add(0, 0.05, 0);
                Vec3 m0 = b0.add(o0.scale(0.3)).add(0, rise + w0, 0), m1 = b1.add(o1.scale(0.3)).add(0, rise + w1, 0);
                // the crest leans out and, as the wave rolls on, falls over and down
                Vec3 c0 = m0.add(o0.scale(0.6 + 1.6 * curl)).add(0, 0.6 - 2.4 * curl, 0), c1 = m1.add(o1.scale(0.6 + 1.6 * curl)).add(0, 0.6 - 2.4 * curl, 0);
                int deep = Colors.alpha(k * 0.45f, WATER.deep()), mid = Colors.alpha(k * 0.75f, WATER.main()), top = Colors.alpha(k * 0.95f, FOAM);
                ctx.quad(water, b0, b1, m1, m0, 0, 0, 1, 1, deep, deep, mid, mid);
                ctx.quad(water, m0, m1, c1, c0, 0, 0, 1, 1, mid, mid, top, top);
                if (i % 2 == 0) ctx.billboard(foam, c0, 0.9f, 0, Colors.alpha(k * 0.75f, FOAM));
                if (i % 4 == 0) ctx.billboard(spray, c0.add(0, 0.4, 0), 1.8f + curl * 1.5f, a0 > 3 ? 1 : 0, Colors.alpha(k * 0.25f, 0xDDF4FF));
            }
        }));
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.07, 0), UP, 0.4f, r * 1.25f, 0.6f, Colors.argb(210, FOAM), roll + 8).energy());
        VfxManager.add(new FlashVfx(g.add(0, 1, 0), 0.4f, r * 0.6f, Colors.argb(220, WATER.core()), 10).energy());
        VfxManager.add(new ShardBurstVfx(g.add(0, 1.0, 0), UP, 3.2f, 0.5f, 60, 0.25f, Colors.argb(240, FOAM), Colors.argb(0, WATER.main()), 34, seed)
                .texture(VfxTextures.GLOW, false).physics(0.035f, 0.95f));
        // the veil: two rings of water turning round each ally it washed, glints running along them
        for (int id : p.entities()) {
            Entity e = MageFx.entity(id);
            if (e == null) continue;
            float size = Math.max(e.getBbWidth(), 0.6f) * 0.9f + 0.3f;
            float h = e.getBbHeight();
            VfxManager.add(new SpellVfx(veil, (ctx, t, ticks) -> {
                if (e.isRemoved()) return;
                float k = SpellVfx.envelope(t, 0.06f, 0.2f);
                Vec3 c = e.getPosition(ctx.partial).add(0, h * 0.5, 0);
                var ring = ctx.additive(VfxTextures.RING);
                var glint = ctx.additive(VfxTextures.GLOW);
                for (int j = 0; j < 2; j++) {
                    double tilt = (j == 0 ? 0.5 : -0.5), spin = ticks * (j == 0 ? 0.09 : -0.07) + id;
                    // each ring lies in a tilted plane whose tilt swings round as it turns
                    Vec3[] b = com.fantasyweapons.client.vfx.VfxContext.basis(new Vec3(Math.sin(tilt) * Math.cos(spin), Math.cos(tilt), Math.sin(tilt) * Math.sin(spin)));
                    Vec3 ax = b[0], ay = b[1];
                    float rad = size + 0.15f * j;
                    ctx.ring(ring, c, ax, ay, rad * 0.86f, rad, 40, Colors.alpha(k * 0.55f, j == 0 ? WATER.main() : TEAL));
                    for (int g2 = 0; g2 < 3; g2++) {
                        double a = ticks * 0.25 * (j == 0 ? 1 : -1) + g2 * 2.094;
                        Vec3 q = c.add(ax.scale(Math.cos(a) * rad)).add(ay.scale(Math.sin(a) * rad));
                        ctx.billboard(glint, q, 0.35f, 0, Colors.alpha(k * 0.9f, FOAM));
                    }
                }
            }));
            MageFx.healSparkle(e, WATER.main());
        }
        ScreenFx.zoneVignette(0x9FE8FF, 0.25f, 30, g, r);
        CameraShake.add(g, 0.5f, r * 2.5f);
    }
}
