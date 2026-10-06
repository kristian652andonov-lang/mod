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

import java.util.List;

/** Client visuals for Bloomfall. Palette: living green, pollen gold, blossom pink, bark brown. */
public final class BloomfallFx {
    static final int LEAF = 0x5BE063;
    static final int DEEP = 0x1F6B2A;
    static final int POLLEN = 0xFFE08A;
    static final int BLOSSOM = 0xFF8FC8;
    static final int BARK = 0x5C4532;
    static final int VINE = 0x45602F;
    /** Ground rotted by creeping vines. */
    static final int ROT = 0x1C1A0C;
    /** The colour of roots splitting the ground. */
    static final int ROOT = 0x1E2A12;

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
        GroundShatter.cracks(FrostrendFx.ground(c), r * 1.1f, ROOT, root + 30, -1, 0, p.seed() * 3);
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
        GroundShatter.cracks(FrostrendFx.ground(c), r, ROOT, duration, LEAF, POLLEN, p.seed() * 3);
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
        GroundShatter.cracks(FrostrendFx.ground(c), r, ROOT, pull + 30, LEAF, POLLEN, p.seed() * 3);
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), new Vec3(0, 1, 0), r, Colors.argb(140, LEAF), VfxTextures.SWIRL, pull)
                .spin(0.08f).energy().timing(0.1f, 0.1f));
        // vines creep in over the ground from all round the rim towards the bloom, each by its own wandering way,
        // throwing off tendrils and rotting the ground they pass over
        RandomSource rnd = RandomSource.create(p.seed());
        float bloomR = Math.min(6f, r * 0.45f);
        int count = 9 + rnd.nextInt(4);
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count + rnd.nextGaussian() * 0.25;
            Vec3 start = FrostrendFx.ground(c.add(Math.cos(a) * r * (0.75 + 0.3 * rnd.nextDouble()), 0, Math.sin(a) * r * (0.75 + 0.3 * rnd.nextDouble())));
            double ea = a + rnd.nextGaussian() * 0.5;
            Vec3 end = FrostrendFx.ground(c.add(Math.cos(ea) * bloomR * 0.45, 0, Math.sin(ea) * bloomR * 0.45));
            float width = 0.18f + 0.2f * rnd.nextFloat();
            int grow = pull / 2 + rnd.nextInt(8);
            Vec3[] path = creep(start, end, 1.0 + rnd.nextDouble() * 1.4, rnd);
            VfxManager.add(new VineVfx(path, width, Colors.argb(255, VINE), Colors.argb(255, LEAF), grow, pull + 6, rnd.nextLong()));
            corrode(path, width, grow, pull + 50, rnd);
            // a tendril or two wandering off to the side
            int tendrils = rnd.nextInt(3);
            for (int k = 0; k < tendrils; k++) {
                int from = 3 + rnd.nextInt(path.length - 6);
                double ta = a + Math.PI + (rnd.nextBoolean() ? 1 : -1) * (0.7 + rnd.nextDouble() * 0.8);
                Vec3 tEnd = FrostrendFx.ground(path[from].add(Math.cos(ta) * (1.2 + rnd.nextDouble() * 1.8), 0, Math.sin(ta) * (1.2 + rnd.nextDouble() * 1.8)));
                Vec3[] tp = creep(path[from], tEnd, 0.4, rnd);
                int delay = grow * from / path.length;
                long sd = rnd.nextLong();
                float tw = width * 0.55f;
                FxScheduler.after(delay, () -> {
                    VfxManager.add(new VineVfx(tp, tw, Colors.argb(255, VINE), Colors.argb(255, LEAF), grow / 2, pull + 6 - delay, sd));
                    corrode(tp, tw, grow / 2, pull + 40 - delay, RandomSource.create(sd));
                });
            }
        }
        ScreenFx.zoneVignette(DEEP, 0.3f, pull + 10, c, r);
        CameraShake.add(c, 0.3f, r * 2);
    }

    /**
     * A path creeping over the ground from {@code a} to {@code b}: it meanders from side to side by {@code wander}
     * blocks at most, follows the lie of the land and now and then arches up off it.
     */
    private static Vec3[] creep(Vec3 a, Vec3 b, double wander, RandomSource rnd) {
        int n = Math.max(6, (int) (a.distanceTo(b) * 1.6));
        Vec3 d = new Vec3(b.x - a.x, 0, b.z - a.z);
        Vec3 side = d.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : new Vec3(-d.z, 0, d.x).normalize();
        double f1 = 1.2 + rnd.nextDouble() * 2, f2 = 3 + rnd.nextDouble() * 3, p1 = rnd.nextDouble() * 6.28, p2 = rnd.nextDouble() * 6.28;
        double hump = rnd.nextDouble() * 6.28;
        Vec3[] out = new Vec3[n];
        for (int k = 0; k < n; k++) {
            double t = k / (double) (n - 1);
            double sway = (Math.sin(t * Math.PI * f1 + p1) * 0.75 + Math.sin(t * Math.PI * f2 + p2) * 0.25) * wander * Math.sin(Math.PI * t);
            Vec3 at = a.lerp(b, t).add(side.scale(sway + rnd.nextGaussian() * 0.08));
            double lift = Math.pow(Math.max(0, Math.sin(t * Math.PI * 2.3 + hump)), 4) * 0.45;
            out[k] = FrostrendFx.ground(at).add(0, 0.07 + lift, 0);
        }
        out[0] = a.add(0, -0.05, 0);
        return out;
    }

    /** The rot a vine leaves in the ground under its path: a dark, cracked groove and blotches of decay. */
    private static void corrode(Vec3[] path, float width, int grow, int life, RandomSource rnd) {
        List<Vec3> line = new java.util.ArrayList<>();
        for (Vec3 v : path) line.add(FrostrendFx.ground(v));
        VfxManager.add(new com.fantasyweapons.client.vfx.effects.FissureVfx(line, width * 5f, 0, 0, grow, life, rnd.nextLong(), FrostrendFx::ground)
                .glowless().darkColor(ROT));
        for (int k = 1; k < path.length; k += 2 + rnd.nextInt(3)) {
            Vec3 g = FrostrendFx.ground(path[k]).add(rnd.nextGaussian() * 0.25, 0.03, rnd.nextGaussian() * 0.25);
            int delay = grow * k / path.length;
            float size = width * (2.5f + 3f * rnd.nextFloat());
            int col = Colors.argb(150 + rnd.nextInt(60), Colors.lerpRgb(ROT, 0x2E3410, rnd.nextFloat() * 0.5f));
            FxScheduler.after(delay, () -> VfxManager.add(new DecalVfx(g, new Vec3(0, 1, 0), size, col, VfxTextures.MIST, life - delay)
                    .translucent().satellites(0).timing(0.15f, 0.2f)));
        }
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
