package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.fx.voidfang.VoidWaveVfx;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SpikeVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Client visuals for Frostrend. Palette: glacier blue, frost white, deep ice. */
public final class FrostrendFx {
    static final int ICE = 0x6FD8FF;
    static final int FROST = 0xE8FBFF;
    static final int DEEP = 0x2A7FCF;
    static final int CRYSTAL = 0xA8E8FF;

    private FrostrendFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.FROSTREND_FROST_SLASH, FrostrendFx::frostSlash);
        FxDispatcher.register(FxIds.FROSTREND_ICE_SPIKES, FrostrendFx::iceSpikes);
        FxDispatcher.register(FxIds.FROSTREND_FREEZE, FrostrendFx::freeze);
        FxDispatcher.register(FxIds.FROSTREND_GLACIAL_DOMAIN, FrostrendFx::glacialDomain);
        FxDispatcher.register(FxIds.FROSTREND_ABSOLUTE_ZERO, FrostrendFx::absoluteZero);
        FxDispatcher.register(FxIds.FROSTREND_SHATTER, FrostrendFx::shatter);
        FxDispatcher.register(FxIds.FROSTREND_ABSOLUTE_ZERO_END, FrostrendFx::absoluteZeroEnd);
    }

    /** Ground height under a point on the client (for placing crystals). */
    static Vec3 ground(Vec3 p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return p;
        Vec3 from = p.add(0, 1.5, 0);
        HitResult hit = level.clip(new ClipContext(from, from.subtract(0, 8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? p : hit.getLocation();
    }

    private static void frostSlash(FxPayload p) {
        Vec3 end = p.points().isEmpty() ? p.pos().add(p.dir().scale(15)) : p.points().get(0);
        VoidWaveVfx wave = VfxManager.add(new VoidWaveVfx(p.pos(), p.dir(), end, p.power(), p.scale() * 1.15f, Colors.argb(230, ICE),
                Colors.argb(255, FROST)));
        VfxManager.add(new FlashVfx(p.pos().add(p.dir().scale(0.8)), 0.4f, 2.2f, Colors.argb(220, ICE), 8).energy());
        // crystals sprouting along the wave's path, in step with it
        double dist = end.distanceTo(p.pos());
        RandomSource r = RandomSource.create(p.seed());
        SpikeVfx crystals = new SpikeVfx(SpikeVfx.Style.ICE, Colors.argb(200, CRYSTAL), FROST, (int) (dist / p.power()) + 40).timing(3, 10);
        for (double d = 2; d < dist; d += 1.4) {
            Vec3 at = ground(p.pos().add(p.dir().scale(d)));
            int delay = (int) (d / p.power());
            Vec3 side = p.dir().cross(new Vec3(0, 1, 0)).normalize().scale((r.nextDouble() - 0.5) * 1.6);
            crystals.add(at.add(side), new Vec3(r.nextGaussian() * 0.25, 1, r.nextGaussian() * 0.25), 0.5f + r.nextFloat() * 0.6f,
                    0.15f + r.nextFloat() * 0.1f, delay);
        }
        VfxManager.add(crystals);
        FxScheduler.after(wave.travelTicks(), () -> {
            VfxManager.add(new FlashVfx(end, 0.5f, 2.4f, Colors.argb(220, ICE), 10).energy());
            VfxManager.add(new ShardBurstVfx(end, p.dir(), 0.85f, 0.3f, 18, 0.28f, Colors.argb(255, FROST), Colors.argb(0, DEEP), 18, p.seed())
                    .texture(VfxTextures.SHARD, false).physics(0.02f, 0.94f));
        });
    }

    private static void iceSpikes(FxPayload p) {
        Vec3 start = p.pos();
        Vec3 dir = p.dir();
        float length = p.scale();
        float speed = Math.max(0.2f, p.power());
        RandomSource r = RandomSource.create(p.seed());
        SpikeVfx spikes = new SpikeVfx(SpikeVfx.Style.ICE, Colors.argb(225, CRYSTAL), FROST, (int) (length / speed) + 34).timing(2, 8);
        Vec3 side = dir.cross(new Vec3(0, 1, 0)).normalize();
        for (double d = 0; d <= length; d += 0.75) {
            Vec3 at = ground(start.add(dir.scale(d)));
            int delay = (int) (d / speed);
            float scale = 1f + (float) (d / length) * 0.6f;
            // a tall central spike flanked by smaller ones, all leaning outward
            spikes.add(at, new Vec3(dir.x * 0.25 + r.nextGaussian() * 0.12, 1, dir.z * 0.25 + r.nextGaussian() * 0.12),
                    (1.4f + r.nextFloat() * 0.9f) * scale, 0.32f * scale, delay);
            for (int s = -1; s <= 1; s += 2) {
                double off = 0.5 + r.nextDouble() * 0.5;
                spikes.add(at.add(side.scale(s * off)), side.scale(s * 0.6).add(0, 1, 0), (0.6f + r.nextFloat() * 0.6f) * scale,
                        0.2f * scale, delay + 1);
            }
            if (((int) (d / 0.75)) % 3 == 0) {
                Vec3 g = at;
                FxScheduler.after(delay, () -> {
                    VfxManager.add(new ShardBurstVfx(g.add(0, 0.3, 0), new Vec3(0, 1, 0), 0.7f, 0.2f, 6, 0.22f, Colors.argb(255, FROST),
                            Colors.argb(0, ICE), 14, (long) (g.x * 31 + g.z)).texture(VfxTextures.SHARD, false).physics(0.03f, 0.92f));
                    VfxManager.add(new ShardBurstVfx(g.add(0, 0.2, 0), new Vec3(0, 1, 0), 1f, 0.04f, 3, 0.9f, Colors.argb(120, FROST),
                            Colors.argb(0, ICE), 24, (long) (g.z * 17 + g.x)).texture(VfxTextures.MIST, false).physics(-0.002f, 0.93f));
                    CameraShake.add(g, 0.12f, 10);
                });
            }
        }
        VfxManager.add(spikes);
        VfxManager.add(new DecalVfx(ground(start.add(dir.scale(length * 0.5))).add(0, 0.03, 0), new Vec3(0, 1, 0), length * 0.55f,
                Colors.argb(150, ICE), VfxTextures.FROST, (int) (length / speed) + 50).timing(0.3f, 0.3f));
    }

    private static void freeze(FxPayload p) {
        Vec3 base = p.pos();
        float w = Math.max(0.4f, p.scale()), h = Math.max(0.4f, p.power());
        VfxManager.add(new FlashVfx(base.add(0, h * 0.5, 0), 0.3f, (w + h) * 1.2f, Colors.argb(220, FROST), 8).energy());
        VfxManager.add(new ShardBurstVfx(base.add(0, h * 0.5, 0), Vec3.ZERO, 1f, 0.15f, 14, 0.25f, Colors.argb(255, FROST), Colors.argb(0, ICE),
                14, p.seed()).texture(VfxTextures.SHARD, false).physics(0.02f, 0.9f));
        RandomSource r = RandomSource.create(p.seed());
        SpikeVfx crust = new SpikeVfx(SpikeVfx.Style.ICE, Colors.argb(200, CRYSTAL), FROST, 30).timing(2, 8);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + r.nextDouble() * 0.5;
            crust.add(base.add(Math.cos(a) * w * 0.55, 0, Math.sin(a) * w * 0.55), new Vec3(Math.cos(a) * 0.6, 1, Math.sin(a) * 0.6),
                    h * (0.35f + r.nextFloat() * 0.25f), 0.15f + w * 0.08f, 0);
        }
        VfxManager.add(crust);
    }

    private static void glacialDomain(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int duration = Math.round(p.power());
        VfxManager.add(new DecalVfx(c.add(0, 0.04, 0), new Vec3(0, 1, 0), r, Colors.argb(230, FROST), VfxTextures.FROST, duration)
                .timing(0.25f, 0.15f).spin(0.002f));
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), new Vec3(0, 1, 0), r * 0.98f, Colors.argb(160, ICE), VfxTextures.HEX, duration)
                .timing(0.3f, 0.15f).energy());
        VfxManager.add(new DecalVfx(c.add(0, 0.03, 0), new Vec3(0, 1, 0), r * 1.1f, Colors.argb(120, DEEP), VfxTextures.GLOW, duration)
                .timing(0.2f, 0.15f));
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4f, r, 0.6f, Colors.argb(230, FROST), 16).energy());
        // crystals around the rim and scattered inside, growing as the frost spreads
        RandomSource rnd = RandomSource.create(p.seed());
        SpikeVfx crystals = new SpikeVfx(SpikeVfx.Style.ICE, Colors.argb(215, CRYSTAL), FROST, duration).timing(4, 12);
        int rim = Math.round(r * 3.2f);
        for (int i = 0; i < rim; i++) {
            double a = i * Math.PI * 2 / rim + rnd.nextDouble() * 0.2;
            Vec3 at = ground(c.add(Math.cos(a) * r * (0.9 + rnd.nextDouble() * 0.1), 0, Math.sin(a) * r * (0.9 + rnd.nextDouble() * 0.1)));
            crystals.add(at, new Vec3(Math.cos(a) * 0.4, 1, Math.sin(a) * 0.4), 0.9f + rnd.nextFloat() * 1.3f, 0.25f + rnd.nextFloat() * 0.15f,
                    4 + rnd.nextInt(5));
        }
        for (int i = 0; i < r * 1.5f; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = (0.3 + 0.6 * rnd.nextDouble()) * r;
            Vec3 at = ground(c.add(Math.cos(a) * d, 0, Math.sin(a) * d));
            crystals.add(at, new Vec3(rnd.nextGaussian() * 0.3, 1, rnd.nextGaussian() * 0.3), 0.4f + rnd.nextFloat() * 0.6f, 0.15f, (int) (d / r * 8));
        }
        VfxManager.add(crystals);
        // drifting frost mist and glittering snow for the whole duration
        for (int t = 0; t < duration; t += 10) {
            int tt = t;
            FxScheduler.after(t, () -> {
                RandomSource rr = RandomSource.create(p.seed() + tt);
                for (int k = 0; k < 3; k++) {
                    double a = rr.nextDouble() * Math.PI * 2, d = Math.sqrt(rr.nextDouble()) * r;
                    Vec3 at = c.add(Math.cos(a) * d, 0.3, Math.sin(a) * d);
                    VfxManager.add(new ShardBurstVfx(at, new Vec3(0, 1, 0), 1f, 0.02f, 2, 1.4f, Colors.argb(110, FROST), Colors.argb(0, ICE), 30,
                            rr.nextLong()).texture(VfxTextures.MIST, false).physics(-0.001f, 0.95f));
                }
                VfxManager.add(new ShardBurstVfx(c.add(0, 2.5, 0), Vec3.ZERO, 1f, r * 0.03f, 10, 0.12f, Colors.argb(255, 0xFFFFFF),
                        Colors.argb(0, FROST), 30, rr.nextLong()).texture(VfxTextures.SPARK, false).physics(0.006f, 0.9f));
            });
        }
        ScreenFx.zoneVignette(ICE, 0.25f, duration, c, r);
    }

    private static void absoluteZero(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int freeze = Math.round(p.power());
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.12, 0), new Vec3(0, 1, 0), 0.5f, r * 1.1f, 1.2f, Colors.argb(240, FROST), 14).energy());
        VfxManager.add(new ShockwaveVfx(c.add(0, 1.2, 0), new Vec3(0, 1, 0), 0.5f, r, 0.6f, Colors.argb(200, ICE), 18));
        VfxManager.add(new FlashVfx(c.add(0, 1, 0), 1f, r * 1.5f, Colors.argb(230, FROST), 12).energy());
        VfxManager.add(new DecalVfx(c.add(0, 0.04, 0), new Vec3(0, 1, 0), r, Colors.argb(240, FROST), VfxTextures.FROST, freeze + 30)
                .timing(0.08f, 0.2f));
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), new Vec3(0, 1, 0), r * 0.6f, Colors.argb(200, ICE), VfxTextures.RUNE_CIRCLE, freeze)
                .spin(-0.03f).energy());
        RandomSource rnd = RandomSource.create(p.seed());
        SpikeVfx crown = new SpikeVfx(SpikeVfx.Style.ICE, Colors.argb(225, CRYSTAL), FROST, freeze + 16).timing(3, 6);
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2 / 18;
            Vec3 at = ground(c.add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2));
            crown.add(at, new Vec3(Math.cos(a) * 0.5, 1, Math.sin(a) * 0.5), 2.5f + rnd.nextFloat() * 1.5f, 0.45f, i % 3);
        }
        VfxManager.add(crown);
        ScreenFx.flash(FROST, 0.35f, 14);
        ScreenFx.zoneVignette(ICE, 0.35f, freeze, c, r);
        CameraShake.add(c, 0.6f, r * 2);
    }

    private static void shatter(FxPayload p) {
        Vec3 base = p.pos();
        float w = Math.max(0.4f, p.scale()), h = Math.max(0.4f, p.power());
        Vec3 mid = base.add(0, h * 0.5, 0);
        VfxManager.add(new ShardBurstVfx(mid, Vec3.ZERO, 1f, 0.35f + w * 0.1f, 26, 0.3f + w * 0.1f, Colors.argb(255, FROST), Colors.argb(0, ICE),
                22, p.seed()).texture(VfxTextures.SHARD, false).physics(0.035f, 0.95f));
        VfxManager.add(new FlashVfx(mid, 0.4f, (w + h) * 1.4f, Colors.argb(230, FROST), 8).energy());
        VfxManager.add(new ShockwaveVfx(base.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.2f, w * 2 + 1, 0.3f, Colors.argb(220, ICE), 10).energy());
    }

    private static void absoluteZeroEnd(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.2, 0), new Vec3(0, 1, 0), 0.5f, r * 1.2f, 1f, Colors.argb(230, FROST), 16).energy());
        VfxManager.add(new FlashVfx(c.add(0, 1.5, 0), 1f, r * 1.2f, Colors.argb(200, ICE), 12).energy());
        CameraShake.add(c, 0.9f, r * 2.5);
    }
}
