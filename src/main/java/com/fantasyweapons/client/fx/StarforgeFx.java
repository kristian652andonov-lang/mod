package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.BlackHoleVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.ModelPartVfx;
import com.fantasyweapons.client.vfx.effects.OrbVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.function.Function;

/** Client visuals for Starforge. Palette: cosmic violet, starlight white, meteor fire orange, deep space. */
public final class StarforgeFx {
    static final int COSMIC = 0x8A6CFF;
    static final int STAR = 0xF0EDFF;
    static final int FIRE = 0xFF8A3D;
    static final int SPACE = 0x150A3A;
    static final Blast.Palette PALETTE = new Blast.Palette(0xFFD2A0, COSMIC, SPACE);

    private StarforgeFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.STARFORGE_WELL, StarforgeFx::well);
        FxDispatcher.register(FxIds.STARFORGE_SLAM, StarforgeFx::slam);
        FxDispatcher.register(FxIds.STARFORGE_SLAM_IMPACT, StarforgeFx::slamImpact);
        FxDispatcher.register(FxIds.STARFORGE_METEOR, StarforgeFx::meteor);
        FxDispatcher.register(FxIds.STARFORGE_METEOR_IMPACT, StarforgeFx::meteorImpact);
        FxDispatcher.register(FxIds.STARFORGE_HORIZON, StarforgeFx::horizon);
        FxDispatcher.register(FxIds.STARFORGE_HORIZON_END, StarforgeFx::horizonEnd);
        FxDispatcher.register(FxIds.STARFORGE_STARFALL, StarforgeFx::starfall);
    }

    /** Matter drawn into a point from all around for {@code ticks}. */
    static void inward(Vec3 c, float r, int ticks, long seed) {
        for (int t = 0; t < ticks; t += 3) {
            int tt = t;
            FxScheduler.after(t, () -> {
                RandomSource rr = RandomSource.create(seed + tt);
                for (int k = 0; k < 3; k++) {
                    double a = rr.nextDouble() * Math.PI * 2;
                    Vec3 from = c.add(Math.cos(a) * r, rr.nextDouble() * 2 - 0.5, Math.sin(a) * r);
                    VfxManager.add(new ShardBurstVfx(from, Vec3.ZERO, 1f, 0.02f, 2, 0.22f, Colors.argb(255, STAR), Colors.argb(0, COSMIC), 20,
                            rr.nextLong()).texture(VfxTextures.SPARK, false).attract(c, 0.05f).physics(0, 0.9f));
                }
            });
        }
    }

    private static void well(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int ticks = Math.round(p.power());
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), new Vec3(0, 1, 0), r, Colors.argb(200, COSMIC), VfxTextures.SWIRL, ticks).spin(-0.2f).energy());
        VfxManager.add(new ShockwaveVfx(c.add(0, 1, 0), new Vec3(0, 1, 0), r * 1.2f, 0.2f, 0.3f, Colors.argb(200, STAR), 10).energy());
        inward(c.add(0, 1, 0), r, Math.min(ticks, 30), p.seed());
    }

    private static void slam(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        // space folds inward before the blow lands
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.2, 0), new Vec3(0, 1, 0), r * 1.4f, 0.3f, 0.6f, Colors.argb(220, COSMIC), 5).energy());
        VfxManager.add(new ShockwaveVfx(c.add(0, 1.2, 0), new Vec3(0, 1, 0), r * 1.2f, 0.3f, 0.3f, Colors.argb(200, STAR), 5).energy());
        inward(c.add(0, 1, 0), r * 1.3f, 5, p.seed());
    }

    private static void slamImpact(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        Blast.explode(c, r, PALETTE, p.seed(), 1, VfxTextures.STAR);
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), new Vec3(0, 1, 0), r * 0.8f, Colors.argb(230, COSMIC), VfxTextures.RUNE_CIRCLE, 30).spin(0.1f).energy());
        RandomSource rnd = RandomSource.create(p.seed());
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + rnd.nextDouble() * 0.4;
            Vec3 from = c.add(Math.cos(a) * r * 0.5, 0.2, Math.sin(a) * r * 0.5);
            Vec3 up = from.add(0, 1.2 + rnd.nextDouble(), 0);
            int frag = i;
            VfxManager.add(new ModelPartVfx("starforge", from, 30, "meteor_fragment_" + frag)
                    .position(t -> from.lerp(up, Vfx.easeOut(Math.min(1, t * 2.5f))))
                    .rotation(t -> new Quaternionf().rotationXYZ(t * 6 + frag, t * 4, t * 3)).scale(t -> 2.2f)
                    .alpha(t -> t > 0.7f ? (1 - t) / 0.3f : 1f));
        }
    }

    private static void meteor(FxPayload p) {
        Vec3 target = p.pos();
        Vec3 sky = p.points().isEmpty() ? target.add(-4, 24, 3) : p.points().get(0);
        int fall = Math.max(4, Math.round(p.power()));
        float scale = Math.max(0.3f, p.scale());
        float radius = p.level() / 10f;
        // formation (first 35%): the meteor condenses out of gathering starlight; then it plunges, accelerating
        Function<Float, Vec3> path = t -> {
            float f = t / fall;
            if (f < 0.35f) return sky.add(0, Math.sin(f * 20) * 0.1, 0);
            float k = (f - 0.35f) / 0.65f;
            return sky.lerp(target.add(0, 0.6, 0), k * k);
        };
        ModelPartVfx rock = VfxManager.add(new ModelPartVfx("starforge", sky, fall + 1, "meteor_projectile")
                .position(t -> path.apply(t * (fall + 1)))
                .rotation(t -> new Quaternionf().rotationXYZ(t * 9, t * 5, t * 2))
                .scale(t -> 5.5f * scale * Math.min(1, t * (fall + 1) / (fall * 0.3f) + 0.2f)));
        OrbVfx glow = VfxManager.add(new OrbVfx(path, 0.6f * scale, FIRE, 0xFFE0B0, fall + 1).fades(Math.max(1, fall / 3), 1));
        FollowTrailVfx trail = VfxManager.add(new FollowTrailVfx(glow::now, 1.6f * scale, Colors.argb(230, FIRE), Colors.argb(0, COSMIC), 14,
                fall + 30));
        FollowTrailVfx smoke = VfxManager.add(new FollowTrailVfx(glow::now, 2.8f * scale, Colors.argb(110, COSMIC), Colors.argb(0, SPACE), 18,
                fall + 30).texture(VfxTextures.GLOW, false));
        // floating fragments orbit the forming meteor
        for (int i = 0; i < 4; i++) {
            float ph = i * 1.57f;
            int frag = i;
            VfxManager.add(new ModelPartVfx("starforge", sky, (int) (fall * 0.4f), "meteor_fragment_" + frag)
                    .position(t -> sky.add(Math.cos(ph + t * 8) * 1.6 * scale, Math.sin(ph * 2 + t * 6) * 0.5, Math.sin(ph + t * 8) * 1.6 * scale))
                    .rotation(t -> new Quaternionf().rotationXYZ(t * 7, t * 3 + frag, 0)).scale(t -> 2f * scale)
                    .alpha(t -> 1 - t * t));
        }
        // warning sigil at the impact point
        VfxManager.add(new DecalVfx(target.add(0, 0.05, 0), new Vec3(0, 1, 0), radius, Colors.argb(220, COSMIC), VfxTextures.RUNE_CIRCLE, fall + 2)
                .spin(0.08f).energy().timing(0.9f, 0.02f));
        FxProjectiles.track(p.seed(), rock, glow);
    }

    private static void meteorImpact(FxPayload p) {
        FxProjectiles.end(p.seed());
        Vec3 c = p.pos();
        float r = p.scale();
        int field = Math.round(p.power());
        Blast.explode(c, r, PALETTE, p.seed(), r > 4 ? 2 : 1, VfxTextures.STAR);
        // a crater: the blast already breaks the ground; the fractures linger while the gravity field holds
        Vec3 floor = FrostrendFx.ground(c);
        GroundShatter.cracks(floor, r * 1.2f, com.fantasyweapons.client.vfx.GroundMaterial.at(floor), 120 + field);
        GroundShatter.impact(floor, r * 0.5f, 1.4f, p.seed() * 7);
        VfxManager.add(new ShardBurstVfx(c.add(0, 0.6, 0), new Vec3(0, 1, 0), 0.8f, 0.5f, 26, 0.3f, Colors.argb(255, FIRE), Colors.argb(0, COSMIC), 26,
                p.seed() * 11).texture(VfxTextures.FLAME, false).physics(0.02f, 0.94f));
        if (field > 0) {
            VfxManager.add(new DecalVfx(c.add(0, 0.06, 0), new Vec3(0, 1, 0), r * 1.5f, Colors.argb(180, COSMIC), VfxTextures.SWIRL, field)
                    .spin(-0.15f).energy().timing(0.15f, 0.2f));
            inward(c.add(0, 0.8, 0), r * 1.5f, field, p.seed());
            ScreenFx.zoneVignette(SPACE, 0.25f, field, c, r * 1.5);
        }
    }

    private static void horizon(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int duration = Math.round(p.power());
        VfxManager.add(new BlackHoleVfx(c, 1.1f, 0xFFE6C8, COSMIC, duration + 6, p.seed()));
        VfxManager.add(new DecalVfx(FrostrendFx.ground(c).add(0, 0.05, 0), new Vec3(0, 1, 0), r, Colors.argb(200, COSMIC), VfxTextures.SWIRL, duration)
                .spin(-0.2f).energy().timing(0.1f, 0.1f));
        inward(c, r, duration, p.seed());
        ScreenFx.zoneVignette(SPACE, 0.35f, duration, c, r + 2);
        CameraShake.add(c, 0.2f, r * 2);
    }

    private static void horizonEnd(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        VfxManager.add(new ShockwaveVfx(c, new Vec3(0, 1, 0), r, 0.2f, 0.8f, Colors.argb(230, STAR), 5).energy());
        FxScheduler.after(4, () -> Blast.explode(FrostrendFx.ground(c), r * 0.7f, PALETTE, p.seed(), 2, VfxTextures.STAR));
    }

    private static void starfall(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int duration = Math.round(p.power());
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), new Vec3(0, 1, 0), r, Colors.argb(200, COSMIC), VfxTextures.RUNE_CIRCLE, duration)
                .spin(0.02f).energy().timing(0.1f, 0.1f));
        ScreenFx.zoneVignette(SPACE, 0.45f, duration, c, r + 6);
        // the sky fills with stars
        for (int t = 0; t < duration; t += 8) {
            int tt = t;
            FxScheduler.after(t, () -> VfxManager.add(new ShardBurstVfx(c.add(0, 18, 0), Vec3.ZERO, 1f, 0.02f, 10, 0.35f, Colors.argb(255, STAR),
                    Colors.argb(0, COSMIC), 30, p.seed() + tt).texture(VfxTextures.STAR, false).physics(0, 0.99f)));
        }
    }
}
