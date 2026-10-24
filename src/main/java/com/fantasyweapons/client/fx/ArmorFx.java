package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.BeamVfx;
import com.fantasyweapons.client.vfx.effects.BlackHoleVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.EclipseSunVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FlowerVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.GhostVfx;
import com.fantasyweapons.client.vfx.effects.LightningVfx;
import com.fantasyweapons.client.vfx.effects.OrbVfx;
import com.fantasyweapons.client.vfx.effects.SeekerVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SlashArcVfx;
import com.fantasyweapons.client.vfx.effects.SunVfx;
import com.fantasyweapons.client.vfx.effects.VineVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Client visuals for the full-set armor abilities, each in the palette of its set's weapon. */
public final class ArmorFx {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private ArmorFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.SET_RIFT_PRIMED, ArmorFx::riftPrimed);
        FxDispatcher.register(FxIds.SET_RIFT_ECHO, ArmorFx::riftEcho);
        FxDispatcher.register(FxIds.SET_DAWN_BURST, ArmorFx::dawnBurst);
        FxDispatcher.register(FxIds.SET_WINTER_CHILL, ArmorFx::winterChill);
        FxDispatcher.register(FxIds.SET_FRENZY, ArmorFx::frenzy);
        FxDispatcher.register(FxIds.SET_FRENZY_PULSE, ArmorFx::frenzyPulse);
        FxDispatcher.register(FxIds.SET_STORM_BOLT, ArmorFx::stormBolt);
        FxDispatcher.register(FxIds.SET_GRAVE_ALLY, ArmorFx::graveAlly);
        FxDispatcher.register(FxIds.SET_SOUL_HARVEST, ArmorFx::soulHarvest);
        FxDispatcher.register(FxIds.SET_SOUL_RELEASE, ArmorFx::soulRelease);
        FxDispatcher.register(FxIds.SET_GROVE, ArmorFx::grove);
        FxDispatcher.register(FxIds.SET_GROVE_PULSE, ArmorFx::grovePulse);
        FxDispatcher.register(FxIds.SET_ECLIPSE_WAVE, ArmorFx::eclipseWave);
        FxDispatcher.register(FxIds.SET_GRAVITY_WELL, ArmorFx::gravityWell);
        FxDispatcher.register(FxIds.SET_PRECISION_BOLT, ArmorFx::precisionBolt);
        FxDispatcher.register(FxIds.SET_PRECISION_BOLT_END, ArmorFx::precisionBoltEnd);
        FxDispatcher.register(FxIds.SET_SHOCKWAVE, ArmorFx::shockwave);
        FxDispatcher.register(FxIds.SET_HELLFIRE_TRAIL, ArmorFx::hellfireTrail);
        FxDispatcher.register(FxIds.SET_PYRE_NOVA, ArmorFx::pyreNova);
        FxDispatcher.register(FxIds.SET_DEEP_FREEZE, ArmorFx::deepFreeze);
        FxDispatcher.register(FxIds.SET_TIDAL_WAVE, ArmorFx::tidalWave);
        FxDispatcher.register(FxIds.SET_WILD_ROOTS, ArmorFx::wildRoots);
        FxDispatcher.register(FxIds.SET_WILD_GROWTH, ArmorFx::wildGrowth);
        FxDispatcher.register(FxIds.SET_JUDGEMENT_RAY, ArmorFx::judgementRay);
        FxDispatcher.register(FxIds.SET_SHADOW_PACT, ArmorFx::shadowPact);
    }

    public static void clear() {
        PRIMED.clear();
        ALLIES.clear();
    }

    @Nullable
    private static Entity entity(int id) {
        var level = Minecraft.getInstance().level;
        return level == null || id < 0 ? null : level.getEntity(id);
    }

    private static boolean isLocal(int casterId) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getId() == casterId;
    }

    private static Vec3 flat(Vec3 v, Vec3 fallback) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? fallback : f.normalize();
    }

    /** A short flash on every entity the server reported as hit. */
    private static void hitFlashes(FxPayload p, int color) {
        for (int id : p.entities()) {
            Entity e = entity(id);
            if (e == null) continue;
            VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, e.getBbHeight() * 1.3f, Colors.argb(200, color), 8)
                    .follow(e, new Vec3(0, e.getBbHeight() * 0.5, 0)).energy());
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Voidfang: Master of the Void
    // ------------------------------------------------------------------------------------------------------------

    /** The violet sigil under a Voidfang wearer whose next strike will tear a rift. */
    private static final Map<Integer, DecalVfx> PRIMED = new HashMap<>();

    private static void riftPrimed(FxPayload p) {
        Entity caster = entity(p.caster());
        if (caster == null) return;
        int life = Math.round(p.power());
        DecalVfx old = PRIMED.remove(p.caster());
        if (old != null) old.kill();
        PRIMED.put(p.caster(), VfxManager.add(new DecalVfx(caster.position().add(0, 0.05, 0), UP, 0.9f, Colors.argb(170, VoidfangFx.VOID),
                VfxTextures.RIFT_EDGE, life).spin(0.12f).energy().timing(0.1f, 0.2f).follow(t -> caster.position().add(0, 0.05, 0))));
        VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, 1.6f, Colors.argb(200, VoidfangFx.VOID), 10).follow(caster, new Vec3(0, 1, 0)).energy());
        VfxManager.add(new ShockwaveVfx(caster.position().add(0, 0.06, 0), UP, 0.2f, 1.6f, 0.2f, Colors.argb(200, VoidfangFx.EDGE), 9).energy());
    }

    private static void riftEcho(FxPayload p) {
        DecalVfx sigil = PRIMED.remove(p.caster());
        if (sigil != null) sigil.kill();
        Vec3 at = p.pos();
        int delay = Math.max(1, Math.round(p.power()));
        float r = p.scale();
        Vec3 look = flat(p.dir(), new Vec3(0, 0, 1));
        Vec3 side = look.cross(UP).normalize();
        // a slanted tear opens where the strike landed, just in front of the target...
        Vec3 front = at.subtract(look.scale(0.55));
        Vec3 a = front.add(side.scale(1.0)).add(0, 1.0, 0), b = front.subtract(side.scale(1.0)).subtract(0, 1.0, 0);
        VfxManager.add(new BeamVfx(a, b, 0.9f, Colors.argb(200, VoidfangFx.DEEP), delay + 3));
        VfxManager.add(new BeamVfx(a, b, 0.5f, Colors.argb(240, VoidfangFx.VOID), delay + 3));
        VfxManager.add(new BeamVfx(a, b, 0.16f, Colors.argb(255, VoidfangFx.HOT), delay + 3));
        VfxManager.add(new FlashVfx(front, 0.3f, 1.8f, Colors.argb(200, VoidfangFx.VOID), delay + 2).energy());
        VfxManager.add(new ShardBurstVfx(at, UP, 1f, 0.12f, 10, 0.12f, Colors.argb(255, VoidfangFx.EDGE), Colors.argb(0, VoidfangFx.VOID), delay + 2,
                p.seed()).energy().attract(at, 0.08f));
        // ...and snaps shut on it again: the hit echoes
        FxScheduler.after(delay, () -> {
            Vec3 c = at.subtract(0, 0.6, 0);
            VfxManager.add(new SlashArcVfx(c, side.scale(-1), UP.add(look.scale(0.3)).normalize(), 1.5f, 0.5f, 0.3f, 2.8f,
                    Colors.argb(235, VoidfangFx.VOID), Colors.argb(255, VoidfangFx.HOT), 9));
            VfxManager.add(new FlashVfx(at, 0.5f, r * 1.1f, Colors.argb(230, VoidfangFx.VOID), 8).energy());
            VfxManager.add(new ShockwaveVfx(at, look, 0.2f, r, 0.3f, Colors.argb(230, VoidfangFx.EDGE), 9).energy());
            VfxManager.add(new ShardBurstVfx(at, look, 1.2f, 0.35f, 14, 0.18f, Colors.argb(255, VoidfangFx.EDGE), Colors.argb(0, VoidfangFx.DEEP), 14,
                    p.seed() + 1).texture(VfxTextures.SHARD, true).energy());
            CameraShake.add(at, 0.25f, 10);
        });
    }

    // ------------------------------------------------------------------------------------------------------------
    // Solaris: Crown of Dawn
    // ------------------------------------------------------------------------------------------------------------

    private static void dawnBurst(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        VfxManager.add(new SunVfx(c, r * 0.3f, SolarisFx.SUN, SolarisFx.CORE, 12).grow(3).rays(12));
        VfxManager.add(new FlashVfx(c, 0.5f, r * 1.4f, Colors.argb(230, SolarisFx.SUN), 9).energy());
        VfxManager.add(new ShockwaveVfx(c, UP, 0.3f, r * 1.1f, 0.45f, Colors.argb(230, SolarisFx.CORE), 10).energy());
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            Vec3 d = new Vec3(Math.cos(a), 0.2, Math.sin(a)).normalize();
            VfxManager.add(new BeamVfx(c, c.add(d.scale(r)), 0.35f, Colors.argb(220, i % 2 == 0 ? SolarisFx.SUN : SolarisFx.CORE), 7 + i % 3));
        }
        VfxManager.add(new ShardBurstVfx(c, UP, 1.4f, 0.3f, 16, 0.25f, Colors.argb(255, SolarisFx.CORE), Colors.argb(0, SolarisFx.EMBER), 18, p.seed())
                .texture(VfxTextures.FLAME, false).physics(0.01f, 0.9f));
        hitFlashes(p, SolarisFx.SUN);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Frostrend: Heart of Winter
    // ------------------------------------------------------------------------------------------------------------

    private static void winterChill(FxPayload p) {
        Vec3 at = p.pos();
        float h = Math.max(0.8f, p.scale());
        Vec3 from = p.points().isEmpty() ? at : p.points().get(0);
        // a gust of frost leaps from the wearer to whoever struck them, and crusts them with ice
        VfxManager.add(new BeamVfx(from, at, 0.25f, Colors.argb(200, FrostrendFx.ICE), 6));
        VfxManager.add(new FlashVfx(at, 0.3f, h * 1.2f, Colors.argb(210, FrostrendFx.ICE), 8).energy());
        VfxManager.add(new ShockwaveVfx(at, UP, 0.2f, h * 0.9f, 0.25f, Colors.argb(220, FrostrendFx.FROST), 9).energy());
        VfxManager.add(new ShardBurstVfx(at, UP, 1.6f, 0.22f, 12, 0.16f, Colors.argb(255, FrostrendFx.FROST), Colors.argb(0, FrostrendFx.ICE), 16, p.seed())
                .texture(VfxTextures.ICE, true).physics(0.02f, 0.9f));
        VfxManager.add(new ShardBurstVfx(at, UP, 2f, 0.05f, 10, 0.12f, Colors.argb(230, FrostrendFx.FROST), Colors.argb(0, FrostrendFx.CRYSTAL), 30,
                p.seed() + 7).texture(VfxTextures.SNOWFLAKE, false).physics(0.003f, 0.95f));
        Vec3 g = FrostrendFx.ground(at);
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, h * 0.8f, Colors.argb(190, FrostrendFx.CRYSTAL), VfxTextures.FROST, 50).timing(0.1f, 0.5f));
        VfxManager.add(new ShockwaveVfx(from, UP, 0.2f, 1.2f, 0.2f, Colors.argb(180, FrostrendFx.ICE), 8).energy());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Doomcleaver: Blood Frenzy
    // ------------------------------------------------------------------------------------------------------------

    private static void frenzy(FxPayload p) {
        Entity caster = entity(p.caster());
        if (caster == null) return;
        Vec3 c = caster.position();
        VfxManager.add(new FlashVfx(Vec3.ZERO, 0.4f, 3f, Colors.argb(230, DoomcleaverFx.BLOOD), 12).follow(caster, new Vec3(0, 1, 0)).energy());
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.08, 0), UP, 0.3f, 3.2f, 0.4f, Colors.argb(220, DoomcleaverFx.BLOOD), 12).energy());
        VfxManager.add(new ShardBurstVfx(c.add(0, 1, 0), UP, 1.5f, 0.3f, 20, 0.18f, Colors.argb(255, DoomcleaverFx.LIGHT), Colors.argb(0, DoomcleaverFx.DARK),
                20, p.seed()).physics(0.03f, 0.9f));
        if (isLocal(p.caster())) ScreenFx.flash(DoomcleaverFx.BLOOD, 0.25f, 10);
    }

    private static void frenzyPulse(FxPayload p) {
        Entity caster = entity(p.caster());
        if (caster == null) return;
        // a red haze clings to a frenzied wearer, and blood mist rises off them
        VfxManager.add(new FlashVfx(Vec3.ZERO, 1.6f, 2f, Colors.argb(70, DoomcleaverFx.BLOOD), 20).follow(caster, new Vec3(0, 1, 0)).peak(0.5f));
        VfxManager.add(new ShardBurstVfx(caster.position().add(0, 0.4, 0), UP, 1.2f, 0.06f, 6, 0.14f, Colors.argb(200, DoomcleaverFx.BLOOD),
                Colors.argb(0, DoomcleaverFx.DARK), 22, p.seed()).texture(VfxTextures.MIST, false).physics(-0.004f, 0.95f));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Stormbreaker: Eye of the Storm
    // ------------------------------------------------------------------------------------------------------------

    private static void stormBolt(FxPayload p) {
        Vec3 g = p.pos();
        Vec3 top = p.points().isEmpty() ? g.add(0, 16, 0) : p.points().get(0);
        float r = p.scale();
        VfxManager.add(new LightningVfx(top, g, 0.8f, Colors.argb(255, StormbreakerFx.BOLT), 12, p.seed()).branches(5).jag(0.18f).restrike(4));
        VfxManager.add(new LightningVfx(top, g, 0.35f, Colors.argb(255, StormbreakerFx.CORE), 8, p.seed() + 1).branches(2).jag(0.12f));
        VfxManager.add(new FlashVfx(g.add(0, 0.6, 0), 0.6f, r * 1.8f, Colors.argb(230, StormbreakerFx.BOLT), 9).energy());
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.06, 0), UP, 0.2f, r * 1.3f, 0.35f, Colors.argb(220, StormbreakerFx.CORE), 9).energy());
        RandomSource rnd = RandomSource.create(p.seed());
        for (int i = 0; i < 5; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            Vec3 end = g.add(Math.cos(a) * r * 1.2, 0.1, Math.sin(a) * r * 1.2);
            VfxManager.add(new LightningVfx(g.add(0, 0.2, 0), end, 0.14f, Colors.argb(230, StormbreakerFx.BOLT), 6, p.seed() + i * 11).branches(1).jag(0.35f));
        }
        GroundShatter.impact(g, r * 0.4f, 0.5f, p.seed() * 3);
        hitFlashes(p, StormbreakerFx.BOLT);
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.position().distanceTo(g) < 24) ScreenFx.flash(StormbreakerFx.CORE, 0.18f, 5);
        CameraShake.add(g, 0.3f, 14);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Gravebite: Legion of the Grave
    // ------------------------------------------------------------------------------------------------------------

    /** A risen ally the server is moving: the ghost glides between the positions it sends. */
    private static final class Ally {
        Vec3 from, to;
        long at;
        GhostVfx ghost;

        Vec3 now() {
            float k = Math.min(1f, (Util.getMillis() - at) / 150f);
            return from.lerp(to, k);
        }
    }

    private static final Map<Long, Ally> ALLIES = new HashMap<>();

    private static void graveAlly(FxPayload p) {
        long id = p.seed();
        Ally ally = ALLIES.get(id);
        if (p.level() == 2) {
            // it fades back into the grave
            if (ally != null) {
                ALLIES.remove(id);
                Vec3 at = ally.now();
                ally.ghost.kill();
                GravebiteFx.ghostBurst(at, UP, 2, 0.6f, id);
                VfxManager.add(new FlashVfx(at, 0.3f, 1.4f, Colors.argb(200, GravebiteFx.SOUL), 10).energy());
            }
            return;
        }
        if (ally == null || ally.ghost.isDead()) {
            Ally a = new Ally();
            a.from = a.to = p.pos();
            a.at = Util.getMillis();
            int life = Math.round(p.power()) + 20;
            float size = Math.max(0.6f, p.scale());
            a.ghost = VfxManager.add(new GhostVfx(t -> a.now(), size, GravebiteFx.SOUL, GravebiteFx.GHOST, life, id).fades(0.08f, 0.15f));
            VfxManager.add(new FollowTrailVfx(() -> a.ghost.isDead() ? null : a.now(), 0.35f * size, Colors.argb(180, GravebiteFx.SOUL),
                    Colors.argb(0, GravebiteFx.DEEP), 10, life + 10).texture(VfxTextures.STREAK, true));
            // it claws its way up out of the fallen
            VfxManager.add(new FlashVfx(p.pos(), 0.3f, 2f, Colors.argb(210, GravebiteFx.SOUL), 12).energy());
            VfxManager.add(new ShockwaveVfx(FrostrendFx.ground(p.pos()).add(0, 0.05, 0), UP, 0.2f, 1.8f, 0.3f, Colors.argb(200, GravebiteFx.SOUL), 12)
                    .energy());
            GravebiteFx.ghostBurst(p.pos(), UP, 3, 0.7f, id + 1);
            ALLIES.put(id, a);
            return;
        }
        ally.from = ally.now();
        ally.to = p.pos();
        ally.at = Util.getMillis();
        if (p.level() == 1 && p.entities().length > 0) {
            Entity t = entity(p.entities()[0]);
            if (t != null) {
                Vec3 c = t.getBoundingBox().getCenter();
                VfxManager.add(new FlashVfx(c, 0.3f, t.getBbHeight() * 1.2f, Colors.argb(220, GravebiteFx.SOUL), 8).energy());
                VfxManager.add(new ShockwaveVfx(c, p.pos().subtract(c).normalize(), 0.1f, 1.2f, 0.2f, Colors.argb(200, GravebiteFx.GHOST), 7).energy());
                VfxManager.add(new ShardBurstVfx(c, c.subtract(p.pos()), 1f, 0.2f, 8, 0.12f, Colors.argb(255, GravebiteFx.GHOST),
                        Colors.argb(0, GravebiteFx.SOUL), 12, id ^ Util.getMillis()).energy());
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Soulreaper: Harvester of Souls
    // ------------------------------------------------------------------------------------------------------------

    private static void soulHarvest(FxPayload p) {
        Entity caster = entity(p.caster());
        if (caster == null) return;
        Vec3 from = p.pos();
        SeekerVfx s = VfxManager.add(new SeekerVfx(from, new Vec3(0, 0.4, 0), caster.getId(), 0.22, 0.16f, SoulreaperFx.SOUL, SoulreaperFx.SPIRIT, 30));
        VfxManager.add(new FollowTrailVfx(s::now, 0.3f, Colors.argb(210, SoulreaperFx.SOUL), Colors.argb(0, SoulreaperFx.ABYSS), 9, 45)
                .texture(VfxTextures.STREAK, true));
        Vec3[] last = {from};
        VfxManager.add(new GhostVfx(t -> {
            Vec3 n = s.now();
            if (n != null) last[0] = n;
            return last[0];
        }, 0.6f, SoulreaperFx.SOUL, SoulreaperFx.SPIRIT, 30, p.seed()).heading(s::velocity).fades(0.12f, 0.2f));
        VfxManager.add(new FlashVfx(from, 0.3f, 1.5f, Colors.argb(200, SoulreaperFx.SOUL), 9).energy());
        // the soul joins the others circling the wearer
        FxScheduler.after(16, () -> {
            if (caster.isAlive()) VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, 1.4f, Colors.argb(200, SoulreaperFx.SPIRIT), 8)
                    .follow(caster, new Vec3(0, 1.1, 0)).energy());
        });
    }

    private static void soulRelease(FxPayload p) {
        Entity caster = entity(p.caster());
        if (caster == null) return;
        int n = Math.max(1, p.level());
        Vec3 c = caster.position().add(0, 1.1, 0);
        RandomSource rnd = RandomSource.create(p.seed());
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + rnd.nextDouble() * 0.3;
            Vec3 end = c.add(Math.cos(a) * 2.2, 0.8 + rnd.nextDouble() * 0.6, Math.sin(a) * 2.2);
            VfxManager.add(new GhostVfx(t -> c.lerp(end, t), 0.55f, SoulreaperFx.SOUL, SoulreaperFx.SPIRIT, 18, p.seed() + i).fades(0.1f, 0.5f));
        }
        VfxManager.add(new FlashVfx(Vec3.ZERO, 0.5f, 2.6f, Colors.argb(220, SoulreaperFx.SOUL), 10).follow(caster, new Vec3(0, 1.1, 0)).energy());
        VfxManager.add(new ShockwaveVfx(caster.position().add(0, 0.06, 0), UP, 0.3f, 2.8f, 0.35f, Colors.argb(210, SoulreaperFx.SPIRIT), 11).energy());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Bloomfall: Heart of the Grove
    // ------------------------------------------------------------------------------------------------------------

    private static void grove(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        VfxManager.add(new FlowerVfx(g.add(0, 0.05, 0), 1.3f, BloomfallFx.BLOSSOM, BloomfallFx.POLLEN, BloomfallFx.LEAF, 40).timing(10, 12));
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r, Colors.argb(150, BloomfallFx.LEAF), VfxTextures.RUNE_CIRCLE, 45).spin(0.02f).energy()
                .timing(0.2f, 0.3f));
        VfxManager.add(new ShardBurstVfx(g.add(0, 0.3, 0), UP, 1.6f, 0.12f, 14, 0.2f, Colors.argb(255, BloomfallFx.LEAF), Colors.argb(0, BloomfallFx.DEEP),
                30, p.seed()).texture(VfxTextures.LEAF_SOFT, false).physics(0.004f, 0.94f));
    }

    private static void grovePulse(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r, Colors.argb(110, BloomfallFx.LEAF), VfxTextures.RUNE_CIRCLE, 42).spin(0.02f).energy()
                .timing(0.15f, 0.3f));
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.07, 0), UP, 0.3f, r, 0.3f, Colors.argb(160, BloomfallFx.LEAF), 14).energy());
        // the wearer heals: pollen drifts up around them
        VfxManager.add(new ShardBurstVfx(g.add(0, 0.2, 0), UP, 1.3f, 0.07f, 10, 0.12f, Colors.argb(230, BloomfallFx.POLLEN), Colors.argb(0, BloomfallFx.LEAF),
                30, p.seed()).texture(VfxTextures.SPARK, false).physics(-0.003f, 0.95f));
        RandomSource rnd = RandomSource.create(p.seed());
        // roots coil up around every enemy held in place
        for (int id : p.entities()) {
            Entity e = entity(id);
            if (e == null) continue;
            Vec3 base = e.position();
            double w = e.getBbWidth() * 0.6;
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + rnd.nextDouble() * 0.5;
                Vec3[] path = new Vec3[9];
                for (int i = 0; i < path.length; i++) {
                    double t = i / (double) (path.length - 1);
                    double aa = a + t * 2.4;
                    double rr = w * (1.4 - t * 0.6);
                    path[i] = base.add(Math.cos(aa) * rr, e.getBbHeight() * 0.6 * t, Math.sin(aa) * rr);
                }
                VfxManager.add(new VineVfx(path, 0.22f, Colors.argb(255, BloomfallFx.BARK), Colors.argb(255, BloomfallFx.LEAF), 6, 34, rnd.nextLong()));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Eclipse Reaper: Totality
    // ------------------------------------------------------------------------------------------------------------

    private static void eclipseWave(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        VfxManager.add(new EclipseSunVfx(g.add(0, 2.6, 0), 0.9f, EclipseFx.GOLD, EclipseFx.WHITE, EclipseFx.BLACK, EclipseFx.VIOLET, 34));
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.08, 0), UP, 0.3f, r * 1.1f, 0.5f, Colors.argb(230, EclipseFx.GOLD), 12).energy());
        FxScheduler.after(3, () -> VfxManager.add(new ShockwaveVfx(g.add(0, 0.1, 0), UP, 0.3f, r, 0.45f, Colors.argb(230, EclipseFx.VIOLET), 13).energy()));
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r, Colors.argb(160, EclipseFx.GOLD), VfxTextures.RUNE_CIRCLE, 30).spin(0.05f).energy()
                .timing(0.1f, 0.4f));
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r * 0.7f, Colors.argb(160, EclipseFx.VIOLET), VfxTextures.RUNE_CIRCLE, 30).spin(-0.07f).energy()
                .timing(0.1f, 0.4f));
        VfxManager.add(new FlashVfx(g.add(0, 1, 0), 0.6f, r * 1.2f, Colors.argb(220, EclipseFx.WHITE), 9).energy());
        hitFlashes(p, EclipseFx.VIOLET);
        if (isLocal(p.caster())) ScreenFx.flash(EclipseFx.VIOLET, 0.2f, 10);
        CameraShake.add(g, 0.3f, 12);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Starforge: Gravity Well
    // ------------------------------------------------------------------------------------------------------------

    private static void gravityWell(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        Vec3 core = g.add(0, 0.8, 0);
        VfxManager.add(new BlackHoleVfx(core, 0.6f, StarforgeFx.COSMIC, StarforgeFx.SPACE, 20, p.seed()));
        // the pull: rings collapsing inwards and stars streaming into the impact
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.08, 0), UP, r, 0.3f, 0.35f, Colors.argb(220, StarforgeFx.COSMIC), 12).energy());
        FxScheduler.after(4, () -> VfxManager.add(new ShockwaveVfx(g.add(0, 0.1, 0), UP, r * 0.8f, 0.2f, 0.3f, Colors.argb(220, StarforgeFx.STAR), 10).energy()));
        RandomSource rnd = RandomSource.create(p.seed());
        for (int i = 0; i < 18; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = r * (0.6 + rnd.nextDouble() * 0.4);
            Vec3 from = g.add(Math.cos(a) * d, 0.3 + rnd.nextDouble() * 1.6, Math.sin(a) * d);
            VfxManager.add(new ShardBurstVfx(from, core.subtract(from), 0.1f, 0.35f, 1, 0.14f, Colors.argb(255, StarforgeFx.STAR),
                    Colors.argb(0, StarforgeFx.COSMIC), 12, p.seed() + i).texture(VfxTextures.STAR, false).energy().attract(core, 0.05f));
        }
        for (int id : p.entities()) {
            Entity e = entity(id);
            if (e != null) VfxManager.add(new BeamVfx(e.getBoundingBox().getCenter(), core, 0.14f, Colors.argb(200, StarforgeFx.COSMIC), 8));
        }
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r * 0.6f, Colors.argb(150, StarforgeFx.COSMIC), VfxTextures.SWIRL, 22).spin(-0.25f).energy()
                .timing(0.1f, 0.4f));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Aetherlance: Perfect Precision
    // ------------------------------------------------------------------------------------------------------------

    private static void precisionBolt(FxPayload p) {
        Vec3 start = p.pos();
        Vec3 vel = p.dir();
        int life = (int) Math.ceil(p.power() / Math.max(0.1, vel.length())) + 2;
        OrbVfx orb = VfxManager.add(new OrbVfx(t -> start.add(vel.scale(t)), 0.3f, AetherlanceFx.AETHER, AetherlanceFx.LIGHT, life).fades(1, 1));
        FollowTrailVfx trail = VfxManager.add(new FollowTrailVfx(orb::now, 0.45f, Colors.argb(230, AetherlanceFx.AETHER), Colors.argb(0, AetherlanceFx.DEEP),
                8, life + 10));
        FollowTrailVfx core = VfxManager.add(new FollowTrailVfx(orb::now, 0.16f, Colors.argb(255, AetherlanceFx.LIGHT), Colors.argb(0, AetherlanceFx.AETHER),
                6, life + 10).texture(VfxTextures.LIGHTNING, false));
        VfxManager.add(new FlashVfx(start, 0.2f, 1.2f, Colors.argb(220, AetherlanceFx.AETHER), 6).energy());
        FxProjectiles.track(p.seed(), orb, trail, core);
    }

    private static void precisionBoltEnd(FxPayload p) {
        FxProjectiles.end(p.seed());
        Vec3 at = p.pos();
        VfxManager.add(new FlashVfx(at, 0.3f, 1.4f, Colors.argb(220, AetherlanceFx.AETHER), 7).energy());
        VfxManager.add(new ShardBurstVfx(at, UP, 1.6f, 0.2f, 8, 0.1f, Colors.argb(255, AetherlanceFx.LIGHT), Colors.argb(0, AetherlanceFx.AETHER), 10,
                p.seed()).energy());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Monolith: Immovable
    // ------------------------------------------------------------------------------------------------------------

    private static void shockwave(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        GroundShatter.impact(g, r * 0.45f, 0.8f, p.seed());
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.08, 0), UP, 0.4f, r * 1.1f, 0.6f, Colors.argb(220, MonolithFx.DUST), 13).texture(VfxTextures.SOIL));
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.1, 0), UP, 0.3f, r, 0.25f, Colors.argb(220, MonolithFx.ENERGY), 10).energy());
        VfxManager.add(new FlashVfx(g.add(0, 0.3, 0), 0.4f, r * 0.9f, Colors.argb(200, MonolithFx.ENERGY), 8).energy());
        hitFlashes(p, MonolithFx.ENERGY);
        CameraShake.add(g, 0.45f, 14);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Infernochain: Hellforged
    // ------------------------------------------------------------------------------------------------------------

    private static void hellfireTrail(FxPayload p) {
        if (p.points().size() < 2) return;
        Vec3 a = p.points().get(0), b = p.points().get(1);
        int life = Math.round(p.power());
        double len = a.distanceTo(b);
        int n = Math.max(2, (int) Math.ceil(len / 0.7));
        Vec3[] spots = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) spots[i] = FrostrendFx.ground(a.lerp(b, i / (double) n));
        Vec3 along = b.subtract(a).normalize();
        // a scorched, glowing seam along the ground...
        for (int i = 0; i <= n; i++) {
            VfxManager.add(new DecalVfx(spots[i].add(0, 0.04, 0), UP, 0.65f, Colors.argb(200, InfernochainFx.EMBER), VfxTextures.GLOW, life).timing(0.05f, 0.3f));
        }
        VfxManager.add(new FlashVfx(b.add(0, 0.4, 0), 0.3f, 1.6f, Colors.argb(220, InfernochainFx.FIRE), 8).energy());
        // ...with flames licking up out of it until it dies down
        RandomSource rnd = RandomSource.create(p.seed());
        for (int tick = 0; tick < life - 6; tick += 4) {
            int at = tick;
            long s = rnd.nextLong();
            FxScheduler.after(at, () -> {
                RandomSource r = RandomSource.create(s);
                float fade = 1f - at / (float) life;
                for (Vec3 spot : spots) {
                    if (r.nextFloat() > 0.55f) continue;
                    Vec3 o = spot.add(along.scale((r.nextDouble() - 0.5) * 0.6)).add(0, 0.1, 0);
                    VfxManager.add(new ShardBurstVfx(o, UP, 0.3f, 0.08f, 2, 0.2f * (0.5f + fade * 0.5f), Colors.argb(255, InfernochainFx.CORE),
                            Colors.argb(0, InfernochainFx.CRIMSON), 12, r.nextLong()).texture(VfxTextures.FLAME, false).physics(-0.004f, 0.93f));
                }
            });
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the Mage's regalia
    // ------------------------------------------------------------------------------------------------------------

    private static final int PYRE = 0xFF6A2A, PYRE_CORE = 0xFFD27A, RIME = 0x9FE6FF, RIME_CORE = 0xEAFBFF, TIDE = 0x4FB4FF, FOAM = 0xDFF6FF,
            DRUID = 0x6BE873, BARK = 0x5A4030, LUMEN = 0xFFF0B0, LUMEN_CORE = 0xFFFFFF, UMBRA = 0x9B4DFF, NIGHT = 0x1A0633;

    /** Pyromancer: a nova of fire bursting from the struck. */
    private static void pyreNova(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        VfxManager.add(new SunVfx(c, r * 0.25f, PYRE, PYRE_CORE, 10).grow(2).rays(10));
        VfxManager.add(new FlashVfx(c, 0.5f, r * 1.5f, Colors.argb(230, PYRE), 9).energy());
        VfxManager.add(new ShockwaveVfx(c, UP, 0.3f, r * 1.1f, 0.5f, Colors.argb(230, PYRE_CORE), 11).energy());
        VfxManager.add(new ShockwaveVfx(c.add(0, -0.4, 0), UP, 0.2f, r * 0.9f, 0.35f, Colors.argb(200, PYRE), 14).energy());
        VfxManager.add(new ShardBurstVfx(c, UP, 2.2f, 0.35f, 22, 0.3f, Colors.argb(255, PYRE_CORE), Colors.argb(0, PYRE), 20, p.seed())
                .texture(VfxTextures.FLAME, false).physics(-0.005f, 0.9f));
        hitFlashes(p, PYRE);
        CameraShake.add(c, 0.25f, 14);
    }

    /** Cryomancer: a creature locked in a block of ice, and the ice shattering. */
    private static void deepFreeze(FxPayload p) {
        Vec3 at = p.pos();
        float h = Math.max(0.8f, p.scale());
        if (p.level() == 1) {
            VfxManager.add(new FlashVfx(at.add(0, h * 0.5, 0), 0.4f, h * 1.8f, Colors.argb(230, RIME_CORE), 8).energy());
            VfxManager.add(new ShardBurstVfx(at.add(0, h * 0.5, 0), UP, 3f, 0.35f, 24, 0.22f, Colors.argb(255, RIME_CORE), Colors.argb(0, RIME), 22, p.seed())
                    .texture(VfxTextures.ICE, true).physics(0.025f, 0.9f));
            VfxManager.add(new ShockwaveVfx(at.add(0, 0.05, 0), UP, 0.3f, h * 2f, 0.3f, Colors.argb(210, RIME), 10).energy());
            return;
        }
        int ticks = Math.max(10, (int) p.power());
        VfxManager.add(new FlashVfx(at.add(0, h * 0.5, 0), 0.3f, h * 1.4f, Colors.argb(220, RIME), 10).energy());
        // a column of frost crystals growing up around the frozen
        RandomSource rnd = RandomSource.create(p.seed());
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + rnd.nextDouble() * 0.4;
            Vec3 base = at.add(Math.cos(a) * 0.45, 0, Math.sin(a) * 0.45);
            VfxManager.add(new BeamVfx(base, base.add(Math.cos(a) * 0.15, h * (0.8 + rnd.nextDouble() * 0.4), Math.sin(a) * 0.15), 0.35f,
                    Colors.argb(150, RIME), ticks));
        }
        VfxManager.add(new DecalVfx(at.add(0, 0.04, 0), UP, h, Colors.argb(140, RIME), VfxTextures.FROST, ticks).timing(0.1f, 0.3f).energy());
        VfxManager.add(new ShardBurstVfx(at.add(0, h * 0.5, 0), UP, 1.2f, 0.05f, 10, 0.12f, Colors.argb(230, RIME_CORE), Colors.argb(0, RIME), 30, p.seed())
                .texture(VfxTextures.SNOWFLAKE, false).physics(-0.002f, 0.95f));
    }

    /** Tidemancer: a wave rolling out from the healed. */
    private static void tidalWave(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.1, 0), UP, 0.4f, r, 0.9f, Colors.argb(200, TIDE), 16).energy());
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.3, 0), UP, 0.3f, r * 0.85f, 0.4f, Colors.argb(230, FOAM), 13).energy());
        VfxManager.add(new DecalVfx(c.add(0, 0.04, 0), UP, r, Colors.argb(120, TIDE), VfxTextures.RUNE_CIRCLE, 26).spin(0.04f).energy().timing(0.1f, 0.4f));
        VfxManager.add(new ShardBurstVfx(c.add(0, 0.3, 0), UP, 1.6f, 0.3f, 26, 0.16f, Colors.argb(240, FOAM), Colors.argb(0, TIDE), 18, p.seed())
                .texture(VfxTextures.SPARK, false).physics(0.03f, 0.92f));
        hitFlashes(p, TIDE);
    }

    /** Archdruid: roots coiling up around the struck. */
    private static void wildRoots(FxPayload p) {
        Entity e = entity(p.entities().length > 0 ? p.entities()[0] : -1);
        Vec3 base = p.pos();
        double w = Math.max(0.4, p.scale() * 0.6), height = e != null ? e.getBbHeight() : 1.6;
        int life = Math.max(12, (int) p.power() + 10);
        RandomSource rnd = RandomSource.create(p.seed());
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + rnd.nextDouble() * 0.5;
            Vec3[] path = new Vec3[9];
            for (int i = 0; i < path.length; i++) {
                double t = i / (double) (path.length - 1);
                double aa = a + t * 2.4, rr = w * (1.4 - t * 0.6);
                path[i] = base.add(Math.cos(aa) * rr, height * 0.6 * t, Math.sin(aa) * rr);
            }
            VfxManager.add(new VineVfx(path, 0.2f, Colors.argb(255, BARK), Colors.argb(255, DRUID), 5, life, rnd.nextLong()));
        }
        VfxManager.add(new ShardBurstVfx(base.add(0, 0.2, 0), UP, 1.4f, 0.12f, 10, 0.14f, Colors.argb(230, DRUID), Colors.argb(0, BARK), 20, p.seed())
                .texture(VfxTextures.LEAF, false).physics(0.01f, 0.9f));
    }

    /** Archdruid: the allies around heal; spores drift up off them. */
    private static void wildGrowth(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r, Colors.argb(80, DRUID), VfxTextures.RUNE_CIRCLE, 30).spin(0.02f).energy().timing(0.2f, 0.4f));
        for (int id : p.entities()) {
            Entity e = entity(id);
            if (e == null) continue;
            VfxManager.add(new ShardBurstVfx(e.position().add(0, 0.2, 0), UP, 1.2f, 0.05f, 8, 0.12f, Colors.argb(230, 0xC8FFA8), Colors.argb(0, DRUID), 26,
                    p.seed() + id).texture(VfxTextures.SPARK, false).physics(-0.004f, 0.95f));
        }
    }

    /** Luminarch: a ray of light falling on the struck, a thread of light from the ally it answers. */
    private static void judgementRay(FxPayload p) {
        Vec3 foot = p.pos();
        float h = Math.max(1, p.scale());
        Vec3 sky = foot.add(0, 14, 0);
        VfxManager.add(new BeamVfx(sky, foot, 1.1f, Colors.argb(200, LUMEN), 10).fade(0.2f, 1f));
        VfxManager.add(new BeamVfx(sky, foot, 0.4f, Colors.argb(255, LUMEN_CORE), 8));
        VfxManager.add(new FlashVfx(foot.add(0, h * 0.5, 0), 0.4f, h * 1.8f, Colors.argb(230, LUMEN), 9).energy());
        VfxManager.add(new ShockwaveVfx(foot.add(0, 0.06, 0), UP, 0.2f, 2.2f, 0.35f, Colors.argb(220, LUMEN), 11).energy());
        VfxManager.add(new DecalVfx(foot.add(0, 0.04, 0), UP, 1.6f, Colors.argb(170, LUMEN), VfxTextures.RUNE_CIRCLE, 18).spin(0.08f).energy().timing(0.1f, 0.4f));
        if (!p.points().isEmpty()) VfxManager.add(new BeamVfx(p.points().get(0), foot.add(0, h * 0.5, 0), 0.12f, Colors.argb(160, LUMEN), 8).fade(0.3f, 1f));
    }

    /** Umbramancer: the slain's life streaming into the wearer; a withering bloom around the body. */
    private static void shadowPact(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        VfxManager.add(new SunVfx(c, 0.6f, UMBRA, NIGHT, 14).darkCore().grow(3));
        VfxManager.add(new ShockwaveVfx(c.add(0, -0.5, 0), UP, 0.3f, r, 0.5f, Colors.argb(200, UMBRA), 14).energy());
        VfxManager.add(new ShardBurstVfx(c, UP, 2f, 0.2f, 16, 0.2f, Colors.argb(230, UMBRA), Colors.argb(0, NIGHT), 22, p.seed())
                .texture(VfxTextures.SOUL, false).physics(-0.004f, 0.92f));
        Entity caster = entity(p.caster());
        if (caster != null) {
            SeekerVfx s = VfxManager.add(new SeekerVfx(c, new Vec3(0, 0.3, 0), caster.getId(), 0.25, 0.15f, UMBRA, NIGHT, 26));
            VfxManager.add(new FollowTrailVfx(s::now, 0.25f, Colors.argb(200, UMBRA), Colors.argb(0, NIGHT), 8, 40).texture(VfxTextures.STREAK, true));
        }
        hitFlashes(p, UMBRA);
    }
}
