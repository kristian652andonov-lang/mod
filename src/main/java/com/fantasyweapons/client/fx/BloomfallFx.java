package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FlowerVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SlashArcVfx;
import com.fantasyweapons.client.vfx.effects.SpikeVfx;
import com.fantasyweapons.client.vfx.effects.VineVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Client visuals for Bloomfall. Palette: living green, pollen gold, blossom pink, bark brown. */
public final class BloomfallFx {
    static final int LEAF = 0x5BE063;
    static final int DEEP = 0x1F6B2A;
    static final int POLLEN = 0xFFE08A;
    static final int BLOSSOM = 0xFF8FC8;
    static final int BARK = 0x6B4A2B;
    static final int VINE = 0x3E7A2E;

    private BloomfallFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.BLOOMFALL_THORN_SWEEP, BloomfallFx::thornSweep);
        FxDispatcher.register(FxIds.BLOOMFALL_ROOTS, BloomfallFx::roots);
        FxDispatcher.register(FxIds.BLOOMFALL_SPORES, BloomfallFx::spores);
        FxDispatcher.register(FxIds.BLOOMFALL_OVERGROWTH, BloomfallFx::overgrowth);
        FxDispatcher.register(FxIds.BLOOMFALL_WRATH, BloomfallFx::wrath);
        FxDispatcher.register(FxIds.BLOOMFALL_WRATH_END, BloomfallFx::wrathEnd);
    }

    private static Entity entity(int id) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }

    /** A vine sprouting from the ground at {@code at}, optionally flowering. */
    static void sprout(Vec3 at, double height, double curl, int grow, int life, long seed, boolean flower) {
        RandomSource r = RandomSource.create(seed);
        Vec3 dir = new Vec3(r.nextGaussian() * 0.25, 1, r.nextGaussian() * 0.25);
        VineVfx v = new VineVfx(VineVfx.curl(at, dir, height, curl, 10, seed), 0.22f, Colors.argb(255, VINE), Colors.argb(255, LEAF), grow, life, seed);
        if (flower) v.flower(r.nextBoolean() ? BLOSSOM : POLLEN);
        VfxManager.add(v);
    }

    private static void thornSweep(FxPayload p) {
        Vec3 o = p.pos();
        Vec3 fwd = new Vec3(p.dir().x, 0, p.dir().z).normalize();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
        float range = p.scale();
        float half = (float) Math.toRadians(p.power() / 2);
        float a0 = (float) (Math.PI / 2 - half), a1 = (float) (Math.PI / 2 + half);
        VfxManager.add(new SlashArcVfx(o.add(0, 1, 0), right, fwd, range * 0.8f, range * 0.32f, a0, a1, Colors.argb(225, LEAF), Colors.argb(255, POLLEN), 12));
        RandomSource r = RandomSource.create(p.seed());
        SpikeVfx thorns = new SpikeVfx(SpikeVfx.Style.THORN, Colors.argb(255, 0x5C4A2A), 0xC8E06A, 40).timing(3, 10);
        for (int i = 0; i <= 12; i++) {
            double a = a0 + (a1 - a0) * i / 12.0;
            Vec3 dir = right.scale(Math.cos(a)).add(fwd.scale(Math.sin(a)));
            Vec3 at = FrostrendFx.ground(o.add(dir.scale(range * (0.55 + r.nextDouble() * 0.4))));
            thorns.add(at, dir.scale(0.6).add(0, 1, 0), 0.7f + r.nextFloat() * 0.8f, 0.13f, i / 2);
            if (i % 3 == 0) sprout(at, 1.0 + r.nextDouble(), 0.3, 6, 40, p.seed() + i, i % 6 == 0);
        }
        VfxManager.add(thorns);
        VfxManager.add(new ShardBurstVfx(o.add(fwd.scale(2)).add(0, 1, 0), fwd, 0.9f, 0.25f, 18, 0.25f, Colors.argb(255, BLOSSOM), Colors.argb(0, LEAF), 26,
                p.seed()).texture(VfxTextures.PETAL, false).physics(0.004f, 0.93f));
    }

    private static void roots(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int root = Math.round(p.power());
        VfxManager.add(new DecalVfx(c.add(0, 0.04, 0), new Vec3(0, 1, 0), r * 1.1f, Colors.argb(230, DEEP), VfxTextures.FROST, root + 30)
                .timing(0.15f, 0.25f).translucent());
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), new Vec3(0, 1, 0), r, Colors.argb(150, LEAF), VfxTextures.RUNE_CIRCLE, root)
                .spin(0.03f).energy().timing(0.1f, 0.2f));
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.08, 0), new Vec3(0, 1, 0), 0.3f, r * 1.1f, 0.4f, Colors.argb(200, LEAF), 12).energy());
        RandomSource rnd = RandomSource.create(p.seed());
        // roots coil up around every entangled enemy
        for (int id : p.entities()) {
            Entity e = entity(id);
            if (e == null) continue;
            Vec3 base = e.position();
            double w = e.getBbWidth() * 0.6;
            for (int k = 0; k < 5; k++) {
                double a = k * Math.PI * 2 / 5 + rnd.nextDouble() * 0.4;
                Vec3[] path = new Vec3[10];
                for (int i = 0; i < 10; i++) {
                    double t = i / 9.0;
                    double aa = a + t * 2.6;
                    double rr = w * (1.4 - t * 0.6);
                    path[i] = base.add(Math.cos(aa) * rr, e.getBbHeight() * 0.85 * t, Math.sin(aa) * rr);
                }
                VfxManager.add(new VineVfx(path, 0.28f, Colors.argb(255, BARK), Colors.argb(255, LEAF), 6, root, rnd.nextLong()));
            }
        }
        // thick roots breaking the surface across the area
        SpikeVfx spikes = new SpikeVfx(SpikeVfx.Style.THORN, Colors.argb(255, BARK), 0xD9C38A, root + 10).timing(4, 10);
        for (int i = 0; i < r * 3; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
            Vec3 at = FrostrendFx.ground(c.add(Math.cos(a) * d, 0, Math.sin(a) * d));
            spikes.add(at, new Vec3(rnd.nextGaussian() * 0.4, 1, rnd.nextGaussian() * 0.4), 0.6f + rnd.nextFloat() * 0.9f, 0.18f, rnd.nextInt(6));
        }
        VfxManager.add(spikes);
        CameraShake.add(c, 0.3f, 12);
    }

    private static void spores(FxPayload p) {
        Vec3 at = p.pos();
        float r = p.scale();
        VfxManager.add(new ShardBurstVfx(at, Vec3.ZERO, 1f, r * 0.05f, 24, 0.2f, Colors.argb(255, 0xC8FF6A), Colors.argb(0, LEAF), 30, p.seed())
                .texture(VfxTextures.GLOW, false).physics(-0.002f, 0.9f));
        VfxManager.add(new ShardBurstVfx(at, Vec3.ZERO, 1f, r * 0.03f, 8, 1.2f, Colors.argb(140, LEAF), Colors.argb(0, DEEP), 34, p.seed() * 3)
                .texture(VfxTextures.MIST, false).physics(-0.001f, 0.92f));
        VfxManager.add(new FlashVfx(at, 0.4f, r, Colors.argb(180, 0xC8FF6A), 8).energy());
    }

    private static void overgrowth(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int duration = Math.round(p.power());
        // a root network spreads over the ground, then the wild erupts
        VfxManager.add(new DecalVfx(c.add(0, 0.04, 0), new Vec3(0, 1, 0), r, Colors.argb(235, DEEP), VfxTextures.FROST, duration)
                .timing(0.2f, 0.15f).translucent());
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), new Vec3(0, 1, 0), r * 1.05f, Colors.argb(110, LEAF), VfxTextures.GLOW, duration)
                .timing(0.2f, 0.15f));
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4f, r, 0.6f, Colors.argb(220, LEAF), 18).energy());
        RandomSource rnd = RandomSource.create(p.seed());
        int vines = Math.round(r * 2.4f);
        for (int i = 0; i < vines; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = (0.2 + 0.8 * Math.sqrt(rnd.nextDouble())) * r;
            Vec3 at = FrostrendFx.ground(c.add(Math.cos(a) * d, 0, Math.sin(a) * d));
            int delay = (int) (d / r * 12) + rnd.nextInt(6);
            long s = rnd.nextLong();
            boolean flower = rnd.nextInt(3) == 0;
            double h = 1.0 + rnd.nextDouble() * 1.6;
            FxScheduler.after(delay, () -> sprout(at, h, 0.45, 12, duration - 4, s, flower));
        }
        SpikeVfx thorns = new SpikeVfx(SpikeVfx.Style.THORN, Colors.argb(255, 0x56482A), 0xB8E06A, duration).timing(6, 12);
        for (int i = 0; i < r * 1.6f; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
            Vec3 at = FrostrendFx.ground(c.add(Math.cos(a) * d, 0, Math.sin(a) * d));
            thorns.add(at, new Vec3(rnd.nextGaussian() * 0.3, 1, rnd.nextGaussian() * 0.3), 0.5f + rnd.nextFloat() * 0.6f, 0.12f, (int) (d / r * 12) + 4);
        }
        VfxManager.add(thorns);
        // drifting petals, leaves and pollen for the whole duration
        for (int t = 0; t < duration; t += 12) {
            int tt = t;
            FxScheduler.after(t, () -> {
                RandomSource rr = RandomSource.create(p.seed() + tt);
                VfxManager.add(new ShardBurstVfx(c.add(0, 2.5, 0), Vec3.ZERO, 1f, r * 0.03f, 8, 0.25f, Colors.argb(255, BLOSSOM), Colors.argb(0, LEAF), 40,
                        rr.nextLong()).texture(VfxTextures.PETAL, false).physics(0.003f, 0.92f));
                VfxManager.add(new ShardBurstVfx(c.add(0, 2, 0), Vec3.ZERO, 1f, r * 0.03f, 6, 0.25f, Colors.argb(255, LEAF), Colors.argb(0, DEEP), 40,
                        rr.nextLong()).texture(VfxTextures.LEAF, false).physics(0.003f, 0.92f));
                VfxManager.add(new ShardBurstVfx(c.add(0, 0.6, 0), Vec3.ZERO, 1f, r * 0.025f, 10, 0.16f, Colors.argb(255, POLLEN), Colors.argb(0, LEAF), 30,
                        rr.nextLong()).texture(VfxTextures.GLOW, false).physics(-0.002f, 0.92f));
            });
        }
        ScreenFx.zoneVignette(DEEP, 0.25f, duration, c, r);
    }

    private static void wrath(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int pull = Math.round(p.power());
        VfxManager.add(new FlowerVfx(c.add(0, 0.05, 0), Math.min(6f, r * 0.45f), BLOSSOM, 0xFFE6F2, POLLEN, pull + 14).timing(pull / 2, 8));
        VfxManager.add(new DecalVfx(c.add(0, 0.04, 0), new Vec3(0, 1, 0), r, Colors.argb(230, DEEP), VfxTextures.FROST, pull + 30)
                .timing(0.25f, 0.2f).translucent());
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), new Vec3(0, 1, 0), r, Colors.argb(140, LEAF), VfxTextures.SWIRL, pull)
                .spin(0.08f).energy().timing(0.1f, 0.1f));
        // vines reaching inward from the rim, dragging enemies toward the bloom
        RandomSource rnd = RandomSource.create(p.seed());
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14 + rnd.nextDouble() * 0.2;
            Vec3 rim = FrostrendFx.ground(c.add(Math.cos(a) * r * 0.9, 0, Math.sin(a) * r * 0.9));
            Vec3[] path = new Vec3[12];
            for (int k = 0; k < 12; k++) {
                double t = k / 11.0;
                path[k] = rim.lerp(c, t * 0.85).add(0, Math.sin(t * Math.PI) * 1.2, 0);
            }
            VfxManager.add(new VineVfx(path, 0.3f, Colors.argb(255, VINE), Colors.argb(255, LEAF), pull / 2, pull + 6, rnd.nextLong()));
        }
        ScreenFx.zoneVignette(DEEP, 0.3f, pull + 10, c, r);
        CameraShake.add(c, 0.3f, r * 2);
    }

    private static void wrathEnd(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        RandomSource rnd = RandomSource.create(p.seed());
        SpikeVfx thorns = new SpikeVfx(SpikeVfx.Style.THORN, Colors.argb(255, 0x5A4428), 0xE0B0C8, 50).timing(3, 12);
        for (int ring = 0; ring < 4; ring++) {
            float d = r * 0.2f * (ring + 1);
            int n = 8 + ring * 6;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n + ring * 0.4;
                Vec3 at = FrostrendFx.ground(c.add(Math.cos(a) * d, 0, Math.sin(a) * d));
                thorns.add(at, new Vec3(Math.cos(a) * 0.5, 1, Math.sin(a) * 0.5), 2.2f - ring * 0.3f + rnd.nextFloat() * 0.6f, 0.35f - ring * 0.04f, ring * 2);
            }
        }
        VfxManager.add(thorns);
        Blast.explode(c, r * 0.6f, new Blast.Palette(POLLEN, LEAF, DEEP), p.seed(), 2, VfxTextures.PETAL);
        VfxManager.add(new ShardBurstVfx(c.add(0, 1, 0), new Vec3(0, 1, 0), 1f, 0.5f, 50, 0.35f, Colors.argb(255, BLOSSOM), Colors.argb(0, LEAF), 40,
                p.seed()).texture(VfxTextures.PETAL, false).physics(0.01f, 0.95f));
    }
}
