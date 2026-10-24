package com.fantasyweapons.client.fx.mage;

import com.fantasyweapons.client.fx.Blast;
import com.fantasyweapons.client.fx.CameraShake;
import com.fantasyweapons.client.fx.FxDispatcher;
import com.fantasyweapons.client.fx.FxScheduler;
import com.fantasyweapons.client.fx.GroundShatter;
import com.fantasyweapons.client.fx.ScreenFx;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.BeamVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.LightningVfx;
import com.fantasyweapons.client.vfx.effects.OrbVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SpellVfx;
import com.fantasyweapons.client.vfx.effects.SunVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.weapons.pyrecodex.PyreCodexAbilities;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.fantasyweapons.client.fx.mage.MageFx.FIRE;
import static com.fantasyweapons.client.fx.mage.MageFx.UP;
import static com.fantasyweapons.client.fx.mage.MageFx.hash;

/** Client visuals for the fire mage: the Cinderspire's meteors, geysers and volcano; the Pyre Codex's chain, wall and phoenix. */
final class FireMageFx {
    static final int EMBER = 0xFF9A3C, SMOKE = 0x2A2220, MOLTEN = 0xFF4A10;

    private FireMageFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.CINDER_BRAND, FireMageFx::brand);
        FxDispatcher.register(FxIds.CINDER_METEOR, FireMageFx::meteor);
        FxDispatcher.register(FxIds.CINDER_COMBUST, FireMageFx::combust);
        FxDispatcher.register(FxIds.CINDER_GEYSER, FireMageFx::geyser);
        FxDispatcher.register(FxIds.CINDER_VOLCANO, FireMageFx::volcano);
        FxDispatcher.register(FxIds.CINDER_BOMB, FireMageFx::bomb);
        FxDispatcher.register(FxIds.CINDER_ERUPTION, FireMageFx::eruption);
        FxDispatcher.register(FxIds.PYRE_CHAIN, FireMageFx::chain);
        FxDispatcher.register(FxIds.PYRE_WILDFIRE, FireMageFx::wildfire);
        FxDispatcher.register(FxIds.PYRE_WALL, FireMageFx::wall);
        FxDispatcher.register(FxIds.PYRE_PHOENIX, FireMageFx::phoenix);
        FxDispatcher.register(FxIds.PYRE_PHOENIX_CRASH, FireMageFx::phoenixCrash);
    }

    /**
     * A column of flame rising from {@code base}: tongues of fire licking up and thinning towards the top, a white-hot
     * heart at the root. {@code k} 0..1 is how fierce it burns now.
     */
    static void flameColumn(VfxContext ctx, Vec3 base, float height, float width, float ticks, long seed, float k) {
        if (k <= 0.01f) return;
        var flame = ctx.additive(VfxTextures.FLAME);
        int n = Math.max(6, (int) (height * 3.5f));
        for (int i = 0; i < n; i++) {
            float ph = hash(seed, i);
            float cyc = (ticks * (0.07f + 0.04f * ph) + ph) % 1f;
            float y = cyc * height;
            double a = ph * 6.283 + ticks * 0.15;
            float spread = width * 0.35f * (1 - cyc * 0.6f);
            Vec3 p = base.add(Math.cos(a) * spread, y, Math.sin(a) * spread);
            float s = width * (1.1f - cyc * 0.7f) * (0.8f + 0.4f * ph);
            int col = Colors.lerpRgb(FIRE.core(), cyc < 0.4f ? FIRE.main() : FIRE.deep(), Math.min(1, cyc * 1.4f));
            ctx.flame(flame, p.subtract(0, s * 0.6, 0), UP, s * 1.9f, s * 1.1f, Colors.alpha(k * (1 - cyc) * 0.9f, col));
        }
        ctx.billboard(ctx.additive(VfxTextures.GLOW), base.add(0, height * 0.25, 0), width * 2.2f, 0, Colors.alpha(k * 0.45f, FIRE.main()));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Cinderspire
    // ------------------------------------------------------------------------------------------------------------

    /** The burning brand on the ground, then the meteor tearing down out of the sky onto it. */
    private static void brand(FxPayload p) {
        Vec3 g = p.pos();
        Vec3 sky = p.points().isEmpty() ? g.add(0, 26, 0) : p.points().get(0);
        float r = p.scale();
        int delay = Math.max(4, Math.round(p.power()));
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r * 0.85f, Colors.argb(230, FIRE.main()), VfxTextures.RUNE_CIRCLE, delay + 12)
                .spin(0.09f).energy().timing(0.25f, 0.2f));
        VfxManager.add(new DecalVfx(g.add(0, 0.06, 0), UP, r * 0.45f, Colors.argb(230, FIRE.core()), VfxTextures.HEX, delay + 12)
                .round().spin(-0.14f).energy().timing(0.3f, 0.2f));
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r * 1.1f, Colors.argb(150, MOLTEN), VfxTextures.GLOW, delay + 12).timing(0.4f, 0.2f));
        // a thin thread of light marks where it will fall
        VfxManager.add(new SpellVfx(delay, (ctx, t, ticks) -> {
            float a = 0.25f + 0.35f * t;
            ctx.softBeam(ctx.additive(VfxTextures.STREAK), sky, g, 0.12f + 0.2f * t, Colors.alpha(a, FIRE.main()), -ticks * 0.3f, -ticks * 0.3f + 4, 0.35f, 0.04f);
        }));
        // the meteor: a burning star accelerating down its slant, wrapped in fire and dragging smoke
        int fall = Math.min(delay, 16), wait = delay - fall;
        FxScheduler.after(wait, () -> {
            java.util.function.Function<Float, Vec3> path = t -> sky.lerp(g, Math.min(1, (t / fall) * (t / fall)));
            VfxManager.add(new SunVfx(sky, 0.9f, FIRE.main(), FIRE.core(), fall + 1).rays(8).path(path));
            OrbVfx core = VfxManager.add(new OrbVfx(path, 1.1f, MOLTEN, 0xFFFFFF, fall + 1).sprite(VfxTextures.FLAME, FIRE.core(), 0.6f));
            VfxManager.add(new FollowTrailVfx(core::now, 2.4f, Colors.argb(230, FIRE.main()), Colors.argb(0, FIRE.deep()), 18, fall + 22).texture(VfxTextures.FLAME, false));
            VfxManager.add(new FollowTrailVfx(core::now, 3.2f, Colors.argb(150, SMOKE), Colors.argb(0, SMOKE), 26, fall + 40).texture(VfxTextures.MIST, false));
            VfxManager.add(new FollowTrailVfx(core::now, 0.7f, Colors.argb(255, FIRE.core()), Colors.argb(0, FIRE.main()), 10, fall + 10).texture(VfxTextures.LIGHTNING, false));
        });
    }

    private static void meteor(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        Blast.explode(g, r, FIRE.blast(), p.seed(), 2, VfxTextures.FLAME);
        GroundShatter.impact(g, r * 0.9f, 1.2f, p.seed() * 7);
        // the crater glows molten a while, smoking
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r * 0.6f, Colors.argb(230, MOLTEN), VfxTextures.GLOW, 80).timing(0.02f, 0.7f));
        VfxManager.add(new SpellVfx(80, (ctx, t, ticks) -> {
            float k = SpellVfx.envelope(t, 0.05f, 0.6f);
            flameColumn(ctx, g, 1.6f, 0.9f, ticks, p.seed(), k * 0.7f);
            var mist = ctx.translucent(VfxTextures.MIST);
            for (int i = 0; i < 7; i++) {
                float ph = hash(p.seed(), 40 + i);
                float cyc = (ticks * 0.02f + ph) % 1f;
                Vec3 q = g.add((ph - 0.5) * r * 0.6, 0.5 + cyc * 6, (hash(p.seed(), 60 + i) - 0.5) * r * 0.6);
                ctx.billboard(mist, q, 1.2f + cyc * 3f, ph * 6f + ticks * 0.01f, Colors.alpha(k * 0.45f * (1 - cyc), SMOKE));
            }
        }));
        VfxManager.add(new ShardBurstVfx(g.add(0, 0.6, 0), UP, 0.8f, 0.55f, 20, 0.35f, Colors.argb(255, FIRE.core()), Colors.argb(0, MOLTEN), 30, p.seed() * 3)
                .texture(VfxTextures.ROCK_CHIP, false).physics(0.04f, 0.97f));
        CameraShake.add(g, 1.1f, r * 4 + 10);
        MageFx.hit(p, FIRE.main());
    }

    private static void combust(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        VfxManager.add(new FlashVfx(c, 0.4f, r * 1.4f, Colors.argb(230, FIRE.main()), 9).energy());
        VfxManager.add(new FlashVfx(c, 0.2f, r * 0.8f, Colors.argb(255, FIRE.core()), 6, VfxTextures.FLASH));
        VfxManager.add(new ShardBurstVfx(c, UP, 1.5f, 0.3f, 22, 0.32f, Colors.argb(255, FIRE.core()), Colors.argb(0, FIRE.deep()), 18, p.seed())
                .texture(VfxTextures.FLAME, false).physics(-0.004f, 0.88f));
        VfxManager.add(new ShockwaveVfx(c, MageFx.camFacing(c), 0.2f, r * 1.2f, 0.35f, Colors.argb(220, EMBER), 10).energy());
        CameraShake.add(c, 0.3f, 12);
    }

    private static void geyser(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        long seed = p.seed();
        VfxManager.add(new SpellVfx(26, (ctx, t, ticks) -> {
            // bursts up, roars, then falls back into the ground
            float rise = Math.min(1, ticks / 3f);
            float k = SpellVfx.envelope(t, 0.05f, 0.5f);
            flameColumn(ctx, g, 5.5f * rise * (0.6f + 0.4f * k), r * 0.9f, ticks, seed, k);
            ctx.softBeam(ctx.additive(VfxTextures.STREAK), g, g.add(0, 5.5 * rise, 0), r * 0.7f * k, Colors.alpha(0.6f * k, FIRE.core()), -ticks * 0.5f, -ticks * 0.5f + 2, 0.06f, 0.5f);
        }));
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.06, 0), UP, 0.2f, r * 1.8f, 0.4f, Colors.argb(220, FIRE.main()), 12).energy());
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r * 1.2f, Colors.argb(200, MOLTEN), VfxTextures.GLOW, 40).timing(0.02f, 0.7f));
        GroundShatter.cracks(g, r * 1.3f, 0x241008, 50, MOLTEN, FIRE.core(), seed);
        VfxManager.add(new ShardBurstVfx(g.add(0, 0.4, 0), UP, 0.35f, 0.45f, 18, 0.22f, Colors.argb(255, FIRE.core()), Colors.argb(0, FIRE.deep()), 24, seed)
                .texture(VfxTextures.SPARK, true).physics(0.02f, 0.95f));
        CameraShake.add(g, 0.35f, 14);
        MageFx.hit(p, FIRE.main());
    }

    /** The lake of fire: the ground splits and glows, lava heaves, smoke and heat rise, embers stream up out of it. */
    private static void volcano(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int life = Math.round(p.power()) + 20;
        long seed = p.seed();
        GroundShatter.cracks(g, r * 1.05f, 0x1A0804, life, MOLTEN, FIRE.core(), seed);
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r * 1.05f, Colors.argb(200, MOLTEN), VfxTextures.NOISE, life).round().spin(0.004f).timing(0.12f, 0.12f));
        VfxManager.add(new DecalVfx(g.add(0, 0.06, 0), UP, r * 0.8f, Colors.argb(230, FIRE.main()), VfxTextures.GLOW, life).timing(0.1f, 0.15f));
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            float k = SpellVfx.envelope(t, 0.06f, 0.08f);
            var glow = ctx.additive(VfxTextures.GLOW);
            var flame = ctx.additive(VfxTextures.FLAME);
            var mist = ctx.translucent(VfxTextures.MIST);
            var spark = ctx.additive(VfxTextures.SPARK);
            // heaving lava blisters across the lake
            for (int i = 0; i < 18; i++) {
                float ph = hash(seed, i);
                double a = ph * 6.283, rr = Math.sqrt(hash(seed, 100 + i)) * r * 0.9;
                Vec3 q = g.add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr);
                float pulse = 0.5f + 0.5f * (float) Math.sin(ticks * 0.15 + ph * 20);
                ctx.billboard(glow, q, 0.8f + pulse * 1.2f, 0, Colors.alpha(k * 0.5f * pulse, FIRE.main()));
                ctx.flame(flame, q, UP, 1.1f * pulse, 0.6f * pulse, Colors.alpha(k * 0.7f * pulse, EMBER));
            }
            // the mountain's heart glows in the middle
            flameColumn(ctx, g, 3f, 1.6f, ticks, seed, k * 0.8f);
            // smoke billowing up, and embers streaming out of it
            for (int i = 0; i < 12; i++) {
                float ph = hash(seed, 200 + i);
                float cyc = (ticks * 0.012f + ph) % 1f;
                double a = ph * 6.283 + cyc * 1.5, rr = r * 0.5 * hash(seed, 230 + i);
                Vec3 q = g.add(Math.cos(a) * rr, 1 + cyc * 12, Math.sin(a) * rr);
                ctx.billboard(mist, q, 2f + cyc * 5f, ph * 9 + ticks * 0.01f, Colors.alpha(k * 0.5f * (1 - cyc), SMOKE));
            }
            for (int i = 0; i < 30; i++) {
                float ph = hash(seed, 300 + i);
                float cyc = (ticks * (0.03f + 0.02f * ph) + ph) % 1f;
                double a = ph * 6.283, rr = r * hash(seed, 340 + i);
                Vec3 q = g.add(Math.cos(a) * rr, cyc * 8, Math.sin(a) * rr);
                ctx.stretched(spark, q, UP, 0.35f, 0.08f, Colors.alpha(k * (1 - cyc), Colors.lerpRgb(FIRE.core(), FIRE.deep(), cyc)));
            }
        }));
        CameraShake.add(g, 0.5f, r * 3);
        ScreenFx.zoneVignette(MOLTEN, 0.22f, life, g, r);
    }

    private static void bomb(FxPayload p) {
        Vec3 from = p.pos(), land = p.points().isEmpty() ? from : p.points().get(0);
        int flight = Math.max(4, Math.round(p.power()));
        float r = p.scale();
        long seed = p.seed();
        double peak = 5 + from.distanceTo(land) * 0.35;
        java.util.function.Function<Float, Vec3> path = t -> {
            float u = Math.min(1, t / flight);
            return from.lerp(land, u).add(0, Math.sin(u * Math.PI) * peak, 0);
        };
        OrbVfx orb = VfxManager.add(new OrbVfx(path, 0.55f, MOLTEN, FIRE.core(), flight + 1).sprite(VfxTextures.ROCK_CHIP, 0x3A1A0A, 0.5f));
        VfxManager.add(new FollowTrailVfx(orb::now, 0.8f, Colors.argb(230, FIRE.main()), Colors.argb(0, FIRE.deep()), 12, flight + 14).texture(VfxTextures.FLAME, false));
        VfxManager.add(new FollowTrailVfx(orb::now, 1.1f, Colors.argb(140, SMOKE), Colors.argb(0, SMOKE), 16, flight + 26).texture(VfxTextures.MIST, false));
        FxScheduler.after(flight, () -> {
            Blast.explode(land, r, FIRE.blast(), seed, 0, VfxTextures.FLAME);
            VfxManager.add(new DecalVfx(land.add(0, 0.05, 0), UP, r * 0.5f, Colors.argb(200, MOLTEN), VfxTextures.GLOW, 40).timing(0.02f, 0.7f));
        });
    }

    private static void eruption(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        long seed = p.seed();
        Blast.explode(g, r * 0.8f, FIRE.blast(), seed, 2, VfxTextures.FLAME);
        // the mountain's heart bursts up in a towering column of fire
        VfxManager.add(new SpellVfx(40, (ctx, t, ticks) -> {
            float rise = Math.min(1, ticks / 5f);
            float k = SpellVfx.envelope(t, 0.02f, 0.6f);
            flameColumn(ctx, g, 18 * rise, 4.5f, ticks, seed, k);
            flameColumn(ctx, g, 11 * rise, 2.5f, ticks * 1.3f, seed * 3, k);
            ctx.softBeam(ctx.energy(VfxTextures.STREAK), g, g.add(0, 18 * rise, 0), 5f * k, Colors.alpha(0.55f * k, FIRE.main()), -ticks * 0.4f, -ticks * 0.4f + 3, 0.05f, 0.5f);
        }));
        for (int i = 0; i < 3; i++) {
            int d = i * 4;
            FxScheduler.after(d, () -> VfxManager.add(new ShockwaveVfx(g.add(0, 0.08, 0), UP, 0.5f, r * 1.4f, 0.6f, Colors.argb(220, d == 4 ? FIRE.core() : FIRE.main()), 16).energy()));
        }
        VfxManager.add(new ShardBurstVfx(g.add(0, 1, 0), UP, 0.7f, 0.9f, 36, 0.5f, Colors.argb(255, FIRE.core()), Colors.argb(0, MOLTEN), 40, seed)
                .texture(VfxTextures.ROCK_CHIP, false).physics(0.045f, 0.98f));
        GroundShatter.impact(g, r, 1.6f, seed * 11);
        CameraShake.add(g, 1.6f, r * 4);
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.position().distanceTo(g) < r * 2.5) ScreenFx.flash(FIRE.core(), 0.35f, 12);
        MageFx.hit(p, FIRE.main());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Pyre Codex
    // ------------------------------------------------------------------------------------------------------------

    private static void chain(FxPayload p) {
        List<Vec3> pts = p.points();
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 a = pts.get(i), b = pts.get(i + 1);
            int idx = i;
            FxScheduler.after(i * 2, () -> {
                VfxManager.add(new LightningVfx(a, b, 0.32f, Colors.argb(240, FIRE.main()), 8, p.seed() + idx).branches(1).jag(0.45f));
                VfxManager.add(new BeamVfx(a, b, 0.5f, Colors.argb(220, EMBER), 7));
                VfxManager.add(new FlashVfx(b, 0.3f, 1.8f, Colors.argb(230, FIRE.main()), 9).energy());
                VfxManager.add(new ShardBurstVfx(b, b.subtract(a).normalize(), 0.8f, 0.2f, 12, 0.26f, Colors.argb(255, FIRE.core()), Colors.argb(0, FIRE.deep()), 16,
                        p.seed() * 3 + idx).texture(VfxTextures.FLAME, false).physics(-0.004f, 0.9f));
            });
        }
        if (!pts.isEmpty()) VfxManager.add(new FlashVfx(pts.get(0), 0.2f, 1f, Colors.argb(220, FIRE.core()), 6, VfxTextures.STAR).spin(0.3f));
    }

    private static void wildfire(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        VfxManager.add(new FlashVfx(c, 0.4f, r * 1.2f, Colors.argb(220, FIRE.main()), 10).energy());
        Vec3 g = MageFx.ground(c);
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.08, 0), UP, 0.3f, r * 1.3f, 0.5f, Colors.argb(220, FIRE.main()), 14).energy());
        VfxManager.add(new ShardBurstVfx(c, UP, 2.4f, 0.32f, 30, 0.34f, Colors.argb(255, FIRE.core()), Colors.argb(0, FIRE.deep()), 20, p.seed())
                .texture(VfxTextures.FLAME, false).physics(0.0f, 0.88f));
        MageFx.hit(p, FIRE.main());
    }

    /** A wall of roaring flame along the ground, rising, burning and dying down. */
    private static void wall(FxPayload p) {
        Vec3 c = p.pos(), across = p.dir().lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : p.dir().normalize();
        float len = p.scale();
        int life = Math.round(p.power());
        long seed = p.seed();
        int cols = Math.max(4, Math.round(len * 1.4f));
        Vec3[] base = new Vec3[cols];
        for (int i = 0; i < cols; i++) base[i] = MageFx.ground(c.add(across.scale((i / (float) (cols - 1) - 0.5f) * len)));
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            float rise = Math.min(1, ticks / 6f);
            float k = SpellVfx.envelope(t, 0.02f, 0.12f);
            for (int i = 0; i < cols; i++) {
                float ph = hash(seed, i);
                float h = (2.4f + 0.8f * (float) Math.sin(ticks * 0.2 + ph * 9)) * rise;
                flameColumn(ctx, base[i], h, 0.9f, ticks + ph * 40, seed + i, k);
            }
            var glow = ctx.additive(VfxTextures.GLOW);
            for (int i = 0; i < cols; i += 2) ctx.billboard(glow, base[i].add(0, 0.6, 0), 2.4f, 0, Colors.alpha(0.3f * k, FIRE.main()));
        }));
        for (int i = 0; i < cols; i += 2) {
            VfxManager.add(new DecalVfx(base[i].add(0, 0.04, 0), UP, 1.3f, Colors.argb(160, 0x1A0A04), VfxTextures.GLOW, life + 20).translucent().timing(0.05f, 0.3f));
        }
        GroundShatter.line(base[0], base[cols - 1], 0.7f, 0.4f, 0.4f, seed);
        CameraShake.add(c, 0.3f, 16);
    }

    /** The phoenix: a bird of living flame wheeling over the battle, wings beating, a long fiery tail streaming behind. */
    private static void phoenix(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int life = Math.round(p.power());
        long seed = p.seed();
        java.util.function.Function<Float, Vec3> at = t -> t < 10 ? c.add(0, 0.3 + t * 0.25, 0).lerp(PyreCodexAbilities.phoenixAt(c, r, 10), t / 10f)
                : PyreCodexAbilities.phoenixAt(c, r, t);
        OrbVfx body = VfxManager.add(new OrbVfx(at, 1.4f, FIRE.main(), FIRE.core(), life + 1).sprite(VfxTextures.FLAME, FIRE.core(), 0.3f).fades(6, 2));
        VfxManager.add(new FollowTrailVfx(body::now, 2.8f, Colors.argb(230, FIRE.main()), Colors.argb(0, FIRE.deep()), 26, life + 30).texture(VfxTextures.FLAME, false));
        VfxManager.add(new FollowTrailVfx(body::now, 0.6f, Colors.argb(255, FIRE.core()), Colors.argb(0, FIRE.main()), 14, life + 20).texture(VfxTextures.LIGHTNING, false));
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            Vec3 b = at.apply(ticks), ahead = at.apply(ticks + 1);
            Vec3 fwd = ahead.subtract(b);
            fwd = fwd.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : fwd.normalize();
            Vec3 side = fwd.cross(UP).normalize();
            float grow = Math.min(1, ticks / 10f);
            float k = SpellVfx.envelope(t, 0.03f, 0.05f);
            float flap = (float) Math.sin(ticks * 0.55);
            var flame = ctx.additive(VfxTextures.FLAME);
            var streak = ctx.additive(VfxTextures.STREAK);
            for (int s = -1; s <= 1; s += 2) {
                // a wing: a fan of flame feathers swept back from the shoulder, rising and falling
                int feathers = 7;
                Vec3[] spine = new Vec3[feathers + 1];
                float[] ws = new float[feathers + 1];
                int[] cs = new int[feathers + 1];
                for (int i = 0; i <= feathers; i++) {
                    float u = i / (float) feathers;
                    double out = u * 5.2 * grow;
                    Vec3 q = b.add(side.scale(s * out)).add(UP.scale(flap * u * 2.1 * grow + u * 0.4)).subtract(fwd.scale(u * u * 1.8));
                    spine[i] = q;
                    ws[i] = 2.2f * (1 - u * 0.7f) * grow;
                    cs[i] = Colors.alpha(k * 0.9f * (1 - u * u), Colors.lerpRgb(FIRE.core(), FIRE.main(), u));
                    if (i > 0) ctx.flame(flame, q, fwd.scale(-1), 2.5f * grow, 1.1f * grow, Colors.alpha(k * 0.8f, Colors.lerpRgb(FIRE.main(), FIRE.deep(), u)));
                }
                ctx.ribbon(streak, spine, ws, cs, -ticks * 0.2f, 0.3f);
            }
            // head and crest
            ctx.billboard(ctx.additive(VfxTextures.GLOW), b, 4.8f * grow, 0, Colors.alpha(k * 0.5f, FIRE.main()));
            ctx.flame(flame, b.add(0, 0.4, 0), fwd, 1.9f * grow, 0.9f * grow, Colors.alpha(k, FIRE.core()));
            // its glow on the ground beneath
            Vec3 g = MageFx.ground(b.subtract(0, 0.5, 0));
            ctx.disc(ctx.additive(VfxTextures.GLOW), g.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 2.5f, 0, Colors.alpha(k * 0.35f, FIRE.main()));
        }));
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), UP, r, Colors.argb(150, FIRE.main()), VfxTextures.RUNE_CIRCLE, life).spin(0.02f).energy().timing(0.08f, 0.1f));
        VfxManager.add(new FlashVfx(c.add(0, 0.5, 0), 0.3f, 3f, Colors.argb(230, FIRE.core()), 12).energy());
    }

    private static void phoenixCrash(FxPayload p) {
        Vec3 g = p.pos(), from = p.points().isEmpty() ? g.add(0, 6, 0) : p.points().get(0);
        float r = p.scale();
        OrbVfx dive = VfxManager.add(new OrbVfx(t -> from.lerp(g, Math.min(1, t / 4)), 1.4f, FIRE.main(), FIRE.core(), 5).sprite(VfxTextures.FLAME, FIRE.core(), 0.3f));
        VfxManager.add(new FollowTrailVfx(dive::now, 2.6f, Colors.argb(240, FIRE.main()), Colors.argb(0, FIRE.deep()), 10, 16).texture(VfxTextures.FLAME, false));
        FxScheduler.after(4, () -> {
            Blast.explode(g, r, FIRE.blast(), p.seed(), 2, VfxTextures.FLAME);
            VfxManager.add(new SunVfx(g.add(0, 1, 0), r * 0.4f, FIRE.main(), FIRE.core(), 16).grow(3).rays(14));
            VfxManager.add(new SpellVfx(30, (ctx, t, ticks) -> flameColumn(ctx, g, 9 * Math.min(1, ticks / 4f), 3f, ticks, p.seed(), SpellVfx.envelope(t, 0.02f, 0.6f))));
            VfxManager.add(new ShardBurstVfx(g.add(0, 1, 0), UP, 1.6f, 0.45f, 50, 0.4f, Colors.argb(255, FIRE.core()), Colors.argb(0, FIRE.deep()), 30, p.seed())
                    .texture(VfxTextures.FLAME, false).physics(-0.002f, 0.92f));
            CameraShake.add(g, 1.2f, r * 4);
        });
        MageFx.hit(p, FIRE.main());
    }
}
