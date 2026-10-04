package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.ChainVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.ModelPartVfx;
import com.fantasyweapons.client.vfx.effects.RibbonTrailVfx;
import com.fantasyweapons.client.vfx.effects.SeekerVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/** Client visuals for Gravebite. Palette: spectral green, ghost white, grave purple-black. */
public final class GravebiteFx {
    static final int SOUL = 0x5CFFB8;
    static final int GHOST = 0xC9FFE9;
    static final int GRAVE = 0x1B0F2E;
    static final int DEEP = 0x1E8F6A;
    /** Jaw hinge of the Gravebite skull in baked model space (blocks). */
    static final Vec3 HINGE = new Vec3(-1 / 16.0, 33 / 16.0, 0);

    private GravebiteFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.GRAVEBITE_SOUL_VOLLEY, GravebiteFx::soulVolley);
        FxDispatcher.register(FxIds.GRAVEBITE_SOUL_HIT, GravebiteFx::soulHit);
        FxDispatcher.register(FxIds.GRAVEBITE_HARVEST, GravebiteFx::harvest);
        FxDispatcher.register(FxIds.GRAVEBITE_CHAINS, GravebiteFx::chains);
        FxDispatcher.register(FxIds.GRAVEBITE_MAW, GravebiteFx::maw);
        FxDispatcher.register(FxIds.GRAVEBITE_LEGION, GravebiteFx::legion);
        FxDispatcher.register(FxIds.GRAVEBITE_LEGION_LAUNCH, GravebiteFx::legionLaunch);
        FxDispatcher.register(FxIds.GRAVEBITE_LEGION_END, GravebiteFx::legionEnd);
    }

    private static Entity entity(int id) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }

    /** A wailing soul: the artist's soul model riding a homing seeker, with a ghostly trail. */
    static void soul(Vec3 start, Vec3 vel, int target, long seed, int index) {
        SeekerVfx seeker = VfxManager.add(new SeekerVfx(start, vel, target, 0.22, 0.32f, SOUL, GHOST, 80).sprite(VfxTextures.SOUL));
        ModelPartVfx model = VfxManager.add(new ModelPartVfx("gravebite", start, 80, "soul_" + (index % 6))
                .position(t -> {
                    Vec3 n = seeker.now();
                    return n == null ? start : n;
                })
                .rotation(t -> {
                    Vec3 v = seeker.velocity();
                    return new Quaternionf().rotationY((float) Math.atan2(-v.z, v.x));
                })
                .scale(t -> 2.2f).spectral(GHOST, 0.85f, 0.5f));
        FollowTrailVfx trail = VfxManager.add(new FollowTrailVfx(seeker::now, 0.45f, Colors.argb(220, SOUL), Colors.argb(0, DEEP), 10, 100)
                .texture(VfxTextures.STREAK, true));
        FxProjectiles.track(seed, seeker, model);
    }

    private static void soulVolley(FxPayload p) {
        Vec3 start = p.pos();
        int n = p.level();
        List<Vec3> dirs = p.points();
        int[] targets = p.entities();
        for (int i = 0; i < n; i++) {
            Vec3 vel = i < dirs.size() ? dirs.get(i) : p.dir().scale(p.power());
            int target = i < targets.length ? targets[i] : -1;
            int idx = i;
            FxScheduler.after(i / 2, () -> soul(start, vel, target, p.seed() + idx * 7919L, idx));
        }
        VfxManager.add(new FlashVfx(start, 0.4f, 2.2f, Colors.argb(220, SOUL), 10).energy());
        VfxManager.add(new ShardBurstVfx(start, p.dir(), 0.7f, 0.15f, 12, 0.3f, Colors.argb(230, GHOST), Colors.argb(0, SOUL), 14, p.seed())
                .texture(VfxTextures.SOUL, false).physics(-0.005f, 0.9f));
    }

    private static void soulHit(FxPayload p) {
        FxProjectiles.end(p.seed());
        Vec3 at = p.pos();
        VfxManager.add(new FlashVfx(at, 0.3f, 1.8f, Colors.argb(230, SOUL), 8).energy());
        VfxManager.add(new ShardBurstVfx(at, new Vec3(0, 1, 0), 1f, 0.18f, 10, 0.25f, Colors.argb(255, GHOST), Colors.argb(0, SOUL), 14, p.seed())
                .texture(VfxTextures.SOUL, false).physics(-0.01f, 0.88f));
        if (p.level() == 1) VfxManager.add(new ShockwaveVfx(at, new Vec3(0, 1, 0), 0.1f, 1.4f, 0.2f, Colors.argb(200, SOUL), 7).energy());
    }

    private static void harvest(FxPayload p) {
        Entity caster = entity(p.caster());
        if (caster == null) return;
        Vec3 from = p.pos();
        SeekerVfx s = VfxManager.add(new SeekerVfx(from, new Vec3(0, 0.35, 0), caster.getId(), 0.25, 0.25f, SOUL, GHOST, 24).sprite(VfxTextures.SOUL));
        VfxManager.add(new FollowTrailVfx(s::now, 0.3f, Colors.argb(200, SOUL), Colors.argb(0, DEEP), 8, 40));
    }

    private static void chains(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int bind = Math.round(p.power());
        VfxManager.add(new DecalVfx(c.add(0, 0.04, 0), new Vec3(0, 1, 0), r, Colors.argb(220, SOUL), VfxTextures.RUNE_CIRCLE, bind + 10)
                .spin(-0.04f).energy().timing(0.1f, 0.2f));
        VfxManager.add(new DecalVfx(c.add(0, 0.03, 0), new Vec3(0, 1, 0), r * 1.1f, Colors.argb(200, GRAVE), VfxTextures.CRACK, bind + 20)
                .timing(0.05f, 0.3f).translucent());
        RandomSource rnd = RandomSource.create(p.seed());
        for (int id : p.entities()) {
            Entity e = entity(id);
            if (e == null) continue;
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + rnd.nextDouble() * 0.6;
                Vec3 anchor = FrostrendFx.ground(e.position().add(Math.cos(a) * (1.6 + rnd.nextDouble()), 0, Math.sin(a) * (1.6 + rnd.nextDouble())));
                VfxManager.add(new ChainVfx(anchor, () -> e.isAlive() ? e.getPosition(1f).add(0, e.getBbHeight() * 0.55, 0) : null,
                        Colors.argb(230, SOUL), Colors.argb(240, GHOST), bind).shoot(3 + k));
            }
            VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, e.getBbHeight() * 1.6f, Colors.argb(180, SOUL), 10).follow(e, new Vec3(0, e.getBbHeight() * 0.5, 0)).energy());
        }
        // chains lashing up from empty ground for drama
        for (int k = 0; k < 6; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * r;
            Vec3 base = FrostrendFx.ground(c.add(Math.cos(a) * d, 0, Math.sin(a) * d));
            Vec3 top = base.add(rnd.nextGaussian() * 0.6, 1.8 + rnd.nextDouble() * 1.5, rnd.nextGaussian() * 0.6);
            VfxManager.add(new ChainVfx(base, () -> top, Colors.argb(200, SOUL), Colors.argb(220, GHOST), 14 + rnd.nextInt(8)).shoot(4));
        }
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.08, 0), new Vec3(0, 1, 0), 0.3f, r * 1.1f, 0.4f, Colors.argb(220, SOUL), 12).energy());
        CameraShake.add(c, 0.3f, 12);
    }

    private static void maw(FxPayload p) {
        Vec3 o = p.pos();
        Vec3 fwd = new Vec3(p.dir().x, 0, p.dir().z).normalize();
        float range = p.scale();
        float yaw = (float) Math.atan2(-fwd.z, fwd.x);
        int life = 20;
        // a colossal spectral version of the axe's skull lunges forward and bites down
        java.util.function.Function<Float, Vec3> lunge = t -> o.add(fwd.scale(1.2 + range * 0.45 * Vfx.easeOut(Math.min(1, t * 2.2f)))).add(0, 0.3, 0);
        java.util.function.Function<Float, Float> alpha = t -> t < 0.15f ? t / 0.15f : (t > 0.7f ? (1 - t) / 0.3f : 1f);
        VfxManager.add(new ModelPartVfx("gravebite", o, life, "skull", "horn_l", "horn_r", "eye_flame_l", "eye_flame_r", "maw_fire", "horn_l_back",
                "eye_flame_l_back").pivot(HINGE).position(lunge).rotation(t -> new Quaternionf().rotationY(yaw)).scale(t -> 3.4f).alpha(alpha)
                .spectral(SOUL, 0.45f, 0.4f));
        VfxManager.add(new ModelPartVfx("gravebite", o, life, "jaw_blade", "fang_0", "fang_1", "fang_2", "fang_3", "fang_4", "fang_5", "fang_6")
                .pivot(HINGE).position(lunge)
                .rotation(t -> {
                    float open = t < 0.35f ? 0.75f * Math.min(1, t / 0.2f) : 0.75f * (1 - Math.min(1, (t - 0.35f) / 0.1f));
                    return new Quaternionf().rotationY(yaw).rotateZ(-open);
                })
                .scale(t -> 3.4f).alpha(alpha).spectral(SOUL, 0.45f, 0.4f));
        FxScheduler.after(8, () -> {
            Vec3 bite = o.add(fwd.scale(range * 0.6));
            VfxManager.add(new FlashVfx(bite, 0.6f, range * 0.8f, Colors.argb(220, SOUL), 10).energy());
            VfxManager.add(new ShockwaveVfx(FrostrendFx.ground(bite).add(0, 0.08, 0), new Vec3(0, 1, 0), 0.3f, range * 0.7f, 0.4f, Colors.argb(220, GHOST), 12).energy());
            VfxManager.add(new ShardBurstVfx(bite, fwd, 0.9f, 0.25f, 20, 0.3f, Colors.argb(255, GHOST), Colors.argb(0, SOUL), 18, p.seed())
                    .texture(VfxTextures.SOUL, false).physics(-0.008f, 0.9f));
            CameraShake.add(bite, 0.6f, 14);
            // stolen souls stream back into the caster
            Entity caster = entity(p.caster());
            for (int id : p.entities()) {
                Entity v = entity(id);
                if (v == null || caster == null) continue;
                List<Vec3> path = new ArrayList<>();
                Vec3 a = v.position().add(0, v.getBbHeight() * 0.6, 0), b = caster.position().add(0, 1.4, 0);
                for (int i = 0; i <= 10; i++) path.add(a.lerp(b, i / 10.0).add(0, Math.sin(i / 10.0 * Math.PI) * 1.2, 0));
                VfxManager.add(new RibbonTrailVfx(path, 0.5f, Colors.argb(220, SOUL), Colors.argb(120, DEEP), 14));
            }
        });
    }

    private static void legion(FxPayload p) {
        Entity caster = entity(p.caster());
        if (caster == null) return;
        int duration = Math.round(p.power());
        int n = Math.min(24, p.level());
        // the circling host of souls (purely visual; the server launches the real ones)
        for (int i = 0; i < n; i++) {
            float phase = i * 6.283f / n;
            float h = 0.8f + (i % 4) * 0.45f;
            float rad = 1.8f + (i % 3) * 0.6f;
            com.fantasyweapons.client.vfx.effects.OrbVfx orb = VfxManager.add(new com.fantasyweapons.client.vfx.effects.OrbVfx(
                    t -> caster.isAlive() ? caster.getPosition(1f).add(Math.cos(phase + t * 0.15) * rad, h + Math.sin(t * 0.2 + phase) * 0.3,
                            Math.sin(phase + t * 0.15) * rad) : null,
                    0.22f, SOUL, GHOST, duration).sprite(VfxTextures.SOUL, GHOST, 0f).fades(10, 10));
            VfxManager.add(new FollowTrailVfx(orb::now, 0.25f, Colors.argb(160, SOUL), Colors.argb(0, DEEP), 8, duration + 10));
        }
        VfxManager.add(new DecalVfx(caster.position().add(0, 0.04, 0), new Vec3(0, 1, 0), 4f, Colors.argb(220, SOUL), VfxTextures.RUNE_CIRCLE, duration)
                .spin(0.05f).energy().timing(0.1f, 0.1f));
        ScreenFx.zoneVignette(GRAVE, 0.35f, duration, caster.position(), 20);
    }

    private static void legionLaunch(FxPayload p) {
        int[] t = p.entities();
        soul(p.pos(), p.dir(), t.length > 0 ? t[0] : -1, p.seed(), (int) (p.seed() & 5));
    }

    private static void legionEnd(FxPayload p) {
        Vec3 c = p.pos();
        VfxManager.add(new ShockwaveVfx(c.add(0, 1, 0), new Vec3(0, 1, 0), 0.5f, 6f, 0.5f, Colors.argb(200, SOUL), 14).energy());
        VfxManager.add(new ShardBurstVfx(c.add(0, 1.2, 0), new Vec3(0, 1, 0), 1f, 0.25f, 30, 0.3f, Colors.argb(255, GHOST), Colors.argb(0, SOUL), 22, 7L)
                .texture(VfxTextures.SOUL, false).physics(-0.008f, 0.9f));
    }
}
