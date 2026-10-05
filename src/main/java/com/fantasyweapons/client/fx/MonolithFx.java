package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.anim.AnimTracker;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.GroundMaterial;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.EarthChunkVfx;
import com.fantasyweapons.client.vfx.effects.FissureVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SpikeVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Client visuals for Monolith. Everything is custom geometry built from the terrain that is struck (see
 * {@link GroundShatter}): boulders and clods of the real ground, pillars wearing its texture, plates of earth heaving
 * along glowing fissures, dust in its colour and shockwaves; no vanilla block particles.
 * Palette: quarried stone, pale limestone, molten amber ground energy, deep earth.
 */
public final class MonolithFx {
    static final int STONE = 0xB08A5A;
    static final int LIME = 0xE8D9C0;
    static final int EARTH = 0x1E1812;
    static final int ENERGY = 0xFFA640;
    static final int CORE = 0xFFE7B8;
    static final int DUST = 0x8C7458;
    static final int ROCK = 0x6E5C48;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private MonolithFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.MONOLITH_PLANT, MonolithFx::plant);
        FxDispatcher.register(FxIds.MONOLITH_SHATTER, MonolithFx::shatter);
        FxDispatcher.register(FxIds.MONOLITH_FISSURE, MonolithFx::fissure);
        FxDispatcher.register(FxIds.MONOLITH_AFTERSHOCK, MonolithFx::aftershock);
        FxDispatcher.register(FxIds.MONOLITH_LEAP, MonolithFx::leap);
        FxDispatcher.register(FxIds.MONOLITH_SLAM, MonolithFx::slam);
        FxDispatcher.register(FxIds.MONOLITH_WORLDBREAKER, MonolithFx::worldbreaker);
        FxDispatcher.register(FxIds.MONOLITH_ERUPTION, MonolithFx::eruption);
    }

    // ------------------------------------------------------------------------------------------------------------
    // building blocks
    // ------------------------------------------------------------------------------------------------------------

    static Vec3 ground(Vec3 p) {
        return FrostrendFx.ground(p);
    }

    /** Boulders of the ground itself thrown out on ballistic arcs; they tumble, bounce, settle and sink away. */
    static void debris(Vec3 c, float spread, int count, float power, long seed) {
        Vec3 g = ground(c);
        RandomSource r = RandomSource.create(seed);
        EarthChunkVfx e = new EarthChunkVfx(GroundMaterial.at(g), 50 + r.nextInt(10));
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double h = (0.1 + r.nextDouble() * 0.18) * power;
            Vec3 from = g.add(Math.cos(a) * spread * r.nextDouble(), 0.3, Math.sin(a) * spread * r.nextDouble());
            e.chunk(from, new Vec3(Math.cos(a) * h, (0.4 + r.nextDouble() * 0.3) * power, Math.sin(a) * h), 0.45f + r.nextFloat() * 0.4f, g.y + 0.2,
                    r.nextLong());
        }
        VfxManager.add(e);
    }

    /** Small chips and clods of the ground flung out of a blow. */
    static void rubble(Vec3 c, float speed, int count, long seed) {
        Vec3 g = ground(c);
        RandomSource r = RandomSource.create(seed);
        EarthChunkVfx e = new EarthChunkVfx(GroundMaterial.at(g), 36 + r.nextInt(8));
        for (int i = 0; i < Math.max(3, count / 2); i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double h = (0.25 + r.nextDouble() * 0.6) * speed;
            e.chunk(g.add(0, 0.2, 0), new Vec3(Math.cos(a) * h, (0.3 + r.nextDouble() * 0.5) * speed * 1.4, Math.sin(a) * h),
                    0.12f + r.nextFloat() * 0.16f, g.y + 0.06, r.nextLong());
        }
        VfxManager.add(e);
    }

    /** A ring of dust rolling outward along the ground. */
    static void dust(Vec3 c, float radius, int puffs, long seed) {
        RandomSource r = RandomSource.create(seed);
        for (int i = 0; i < puffs; i++) {
            double a = i * Math.PI * 2 / puffs + r.nextDouble() * 0.4;
            Vec3 dir = new Vec3(Math.cos(a), 0.12, Math.sin(a));
            VfxManager.add(new ShardBurstVfx(c.add(dir.x * 0.6, 0.35, dir.z * 0.6), dir, 0.25f, radius * 0.055f, 4, 2.4f + radius * 0.08f,
                    Colors.argb(210, dustColor(c)), Colors.argb(0, LIME), 34 + r.nextInt(10), r.nextLong())
                    .texture(VfxTextures.MIST, false).physics(-0.004f, 0.9f).translucent());
        }
    }

    /** Dust takes the colour of the ground it is kicked up from. */
    static int dustColor(Vec3 c) {
        GroundMaterial m = GroundMaterial.at(ground(c));
        return m.lit(Colors.lerpRgb(m.dust(), 0xE8E0D0, 0.2f), 1f);
    }

    /** Ground shockwave: a pale stone ring hugging the ground and a molten energy ring inside it. */
    static void shockwave(Vec3 c, float radius, int ticks) {
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.12, 0), UP, 0.6f, radius * 1.15f, 0.45f, Colors.argb(210, LIME), ticks));
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.18, 0), UP, 0.4f, radius * 0.9f, 0.22f, Colors.argb(160, ENERGY), ticks + 2).energy());
    }

    /** Cracks from {@code c} to every end point, glowing with ground energy. */
    static void cracks(Vec3 c, List<Vec3> ends, float width, int grow, int life, float curtain, long seed) {
        for (int i = 0; i < ends.size(); i++) {
            VfxManager.add(new FissureVfx(List.of(c, ends.get(i)), width, ENERGY, CORE, grow, life, seed + i * 31L, MonolithFx::ground).curtain(curtain));
            float len = (float) Math.max(1, c.distanceTo(ends.get(i)));
            GroundShatter.line(c, ends.get(i), width, 1f, grow / len, seed + i * 57L);
        }
    }

    /** Jagged rock pillars bursting out of the ground, leaning away from {@code from}. */
    static SpikeVfx pillars(Vec3 at, int life) {
        return new SpikeVfx(SpikeVfx.Style.ROCK, Colors.argb(255, 0xA89070), Colors.argb(255, ENERGY), life).timing(3, 12)
                .material(GroundMaterial.at(ground(at)));
    }

    static void pillarRing(SpikeVfx s, Vec3 c, float ringRadius, int count, float h0, float h1, int delay, RandomSource r) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count + r.nextDouble() * 0.5;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 base = ground(c.add(out.scale(ringRadius * (0.85 + r.nextDouble() * 0.3))));
            Vec3 dir = UP.add(out.scale(0.35 + r.nextDouble() * 0.25));
            s.add(base.subtract(0, 0.3, 0), dir, h0 + r.nextFloat() * (h1 - h0), 0.45f + r.nextFloat() * 0.35f, delay + r.nextInt(3));
        }
    }

    /** Pillars erupting along a crack, in order from its start, shrinking toward its end. */
    static void pillarLine(SpikeVfx s, Vec3 from, Vec3 to, float step, float h0, float h1, float ticksPerBlock, RandomSource r) {
        Vec3 d = to.subtract(from);
        double len = Math.sqrt(d.x * d.x + d.z * d.z);
        if (len < 0.5) return;
        Vec3 dir = new Vec3(d.x / len, 0, d.z / len);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        int i = 0;
        for (double x = step * 0.6; x < len; x += step * (0.8 + r.nextDouble() * 0.4), i++) {
            float k = (float) (x / len);
            double off = (i % 2 == 0 ? 1 : -1) * (0.3 + r.nextDouble() * 0.4);
            Vec3 base = ground(from.add(dir.scale(x)).add(side.scale(off)));
            Vec3 lean = UP.add(side.scale(off * 0.5)).add(dir.scale(0.2));
            float h = h1 + (h0 - h1) * k + r.nextFloat() * 0.5f;
            s.add(base.subtract(0, 0.3, 0), lean, h, 0.35f + 0.25f * (1 - k) + r.nextFloat() * 0.15f, Math.round((float) x * ticksPerBlock));
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // effects
    // ------------------------------------------------------------------------------------------------------------

    private static void plant(FxPayload p) {
        int ticks = Math.round(p.power());
        int drive = Math.max(1, p.level());
        AnimTracker.onPlant(p.caster(), ticks, drive);
        Vec3 c = p.pos();
        // the blade biting into the ground: dust and a seam of light the moment it lands
        FxScheduler.after(drive, () -> {
            VfxManager.add(new FlashVfx(c.add(0, 0.3, 0), 0.5f, 2.6f, Colors.argb(255, CORE), 6).energy());
            GroundShatter.cracks(ground(c), 1.6f, GroundMaterial.at(ground(c)), Math.max(20, ticks - drive));
            VfxManager.add(new DecalVfx(c.add(0, 0.06, 0), UP, 1.1f, Colors.argb(200, ENERGY), VfxTextures.GLOW, Math.max(20, ticks - drive))
                    .timing(0.05f, 0.4f));
        });
    }

    private static void shatter(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        RandomSource rnd = RandomSource.create(p.seed());
        cracks(c, p.points(), 1.0f, 6, 80, 0.8f, p.seed());
        GroundShatter.impact(c, r * 0.45f, 1.1f, p.seed() * 3 + 1);
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), UP, r * 0.45f, Colors.argb(200, ENERGY), VfxTextures.RUNE_CIRCLE, 40).spin(0.03f).energy()
                .timing(0.08f, 0.5f));
        shockwave(c, r, 9);
        dust(c, r, 14, p.seed());
        rubble(c, 0.5f, 26, p.seed() * 3);
        debris(c, 1.2f, 7, 1f, p.seed() * 5);
        SpikeVfx s = pillars(c, 44);
        pillarRing(s, c, r * 0.4f, 7, 1.3f, 2.3f, 0, rnd);
        for (Vec3 end : p.points()) pillarLine(s, c, end, 2.2f, 1.6f, 0.7f, 0.6f, rnd);
        VfxManager.add(s);
        VfxManager.add(new FlashVfx(c.add(0, 0.6, 0), 1f, r * 0.55f, Colors.argb(200, CORE), 7).energy());
        CameraShake.add(c, 0.9f, r * 4);
    }

    private static void fissure(FxPayload p) {
        Vec3 start = p.pos();
        Vec3 dir = new Vec3(p.dir().x, 0, p.dir().z).normalize();
        float length = p.scale();
        float speed = Math.max(0.2f, p.power());
        float width = Math.max(0.6f, p.level() / 10f);
        int grow = Math.max(2, Math.round(length / speed));
        Vec3 end = start.add(dir.scale(length));
        RandomSource rnd = RandomSource.create(p.seed());
        VfxManager.add(new FissureVfx(List.of(start, start.add(dir.scale(length * 0.5)), end), width * 1.15f, ENERGY, CORE, grow, grow + 60, p.seed(),
                MonolithFx::ground).curtain(1.1f));
        GroundShatter.line(start, end, width, 1.2f, 1f / speed, p.seed() * 3);
        SpikeVfx s = pillars(start, grow + 40);
        pillarLine(s, start, end, 1.5f, 3.0f, 1.8f, 1f / speed, rnd);
        VfxManager.add(s);
        // dust, rubble, debris and tremor travelling with the head of the fissure
        for (int t = 0; t <= grow; t += 3) {
            int tt = t;
            Vec3 at = ground(start.add(dir.scale(Math.min(length, tt * speed))));
            FxScheduler.after(tt, () -> {
                long sd = p.seed() + tt;
                VfxManager.add(new ShardBurstVfx(at.add(0, 0.4, 0), UP, 0.55f, 0.14f, 3, 1.9f, Colors.argb(140, DUST), Colors.argb(0, LIME), 30, sd)
                        .texture(VfxTextures.MIST, false).physics(-0.004f, 0.9f).translucent());
                rubble(at, 0.35f, 6, sd * 7);
                if (tt % 6 == 0) debris(at, 0.5f, 1, 0.8f, sd * 13);
                CameraShake.add(at, 0.45f, 10);
            });
        }
        FxScheduler.after(grow, () -> {
            Vec3 e = ground(end);
            SpikeVfx fin = pillars(e, 40);
            pillarRing(fin, e, 1.3f, 5, 2.2f, 3.2f, 0, rnd);
            VfxManager.add(fin);
            dust(e, 3f, 8, p.seed() * 17);
        });
        shockwave(start, 3f, 7);
        VfxManager.add(new FlashVfx(start.add(0, 0.5, 0), 0.8f, 3f, Colors.argb(220, CORE), 6).energy());
    }

    private static void aftershock(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        boolean big = p.power() > 0;
        GroundShatter.cracks(ground(c), r * 0.6f, GroundMaterial.at(ground(c)), 40);
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.12, 0), UP, 0.4f, r, 0.35f, Colors.argb(190, LIME), 8));
        dust(c, r, big ? 12 : 7, p.seed());
        rubble(c, 0.3f, big ? 14 : 8, p.seed() * 3);
        if (big) {
            RandomSource rnd = RandomSource.create(p.seed());
            SpikeVfx s = pillars(c, 34);
            for (int i = 0; i < 6; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = 2 + rnd.nextDouble() * (r - 2);
                Vec3 base = ground(c.add(Math.cos(a) * d, 0, Math.sin(a) * d));
                s.add(base.subtract(0, 0.3, 0), UP.add(Math.cos(a) * 0.3, 0, Math.sin(a) * 0.3), 1.2f + rnd.nextFloat() * 1.4f, 0.4f, rnd.nextInt(4));
            }
            VfxManager.add(s);
            debris(c, r * 0.5f, 3, 0.8f, p.seed() * 11);
            CameraShake.add(c, 0.7f, r * 3);
        } else {
            CameraShake.add(c, 0.25f, r * 3);
        }
    }

    private static void leap(FxPayload p) {
        Vec3 c = p.pos();
        dust(c, 3.5f, 10, p.seed());
        rubble(c, 0.4f, 14, p.seed() * 3);
        GroundShatter.impact(c, 1.6f, 0.7f, p.seed() * 3 + 1);
        Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(p.caster());
        if (e != null) {
            VfxManager.add(new FollowTrailVfx(() -> e.isRemoved() ? null : e.position().add(0, 1.2, 0), 1.2f, Colors.argb(160, ENERGY),
                    Colors.argb(0, STONE), 12, 40).texture(VfxTextures.GLOW, false));
            VfxManager.add(new FollowTrailVfx(() -> e.isRemoved() ? null : e.position().add(0, 0.8, 0), 2.2f, Colors.argb(110, DUST),
                    Colors.argb(0, LIME), 16, 40).texture(VfxTextures.MIST, false));
        }
    }

    private static void slam(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        RandomSource rnd = RandomSource.create(p.seed());
        cracks(c, p.points(), 1.1f, 7, 90, 0.9f, p.seed());
        GroundShatter.impact(c, r * 0.55f, 1.4f, p.seed() * 3 + 1);
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), UP, r * 0.5f, Colors.argb(210, ENERGY), VfxTextures.RUNE_CIRCLE, 46).spin(-0.04f).energy()
                .timing(0.06f, 0.5f));
        shockwave(c, r, 10);
        FxScheduler.after(3, () -> VfxManager.add(new ShockwaveVfx(c.add(0, 0.3, 0), UP, 1f, r * 1.4f, 0.6f, Colors.argb(150, DUST), 12)));
        dust(c, r, 18, p.seed());
        rubble(c, 0.6f, 34, p.seed() * 3);
        debris(c, 1.5f, 10, 1.2f, p.seed() * 5);
        SpikeVfx s = pillars(c, 50);
        pillarRing(s, c, r * 0.45f, 8, 2.0f, 3.0f, 0, rnd);
        pillarRing(s, c, r * 0.8f, 11, 1.4f, 2.4f, 3, rnd);
        VfxManager.add(s);
        VfxManager.add(new FlashVfx(c.add(0, 0.8, 0), 1.2f, r * 0.6f, Colors.argb(210, CORE), 8).energy());
        CameraShake.add(c, 1.2f, r * 4);
    }

    private static void worldbreaker(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int duration = Math.max(20, Math.round(p.power()));
        int eruption = Math.max(10, p.level());
        long seed = p.seed();
        cracks(c, p.points(), 1.7f, 12, duration + 40, 1.3f, seed);
        GroundShatter.cracks(ground(c), r * 0.75f, GroundMaterial.at(ground(c)), duration + 50);
        VfxManager.add(new DecalVfx(c.add(0, 0.05, 0), UP, r * 0.65f, Colors.argb(220, ENERGY), VfxTextures.RUNE_CIRCLE, duration).spin(0.02f).energy()
                .timing(0.05f, 0.15f));
        VfxManager.add(new DecalVfx(c.add(0, 0.07, 0), UP, r * 0.35f, Colors.argb(200, CORE), VfxTextures.RUNE_CIRCLE, duration).spin(-0.05f).energy().satellites(0)
                .timing(0.1f, 0.15f));
        shockwave(c, r, 12);
        dust(c, r, 20, seed);
        rubble(c, 0.6f, 40, seed * 3);
        VfxManager.add(new FlashVfx(c.add(0, 1, 0), 1.5f, r * 0.5f, Colors.argb(210, CORE), 9).energy());
        ScreenFx.zoneVignette(EARTH, 0.35f, duration, c, r + 6);
        // the earth heaves: tremors and ground energy welling up out of the cracks until the eruption
        for (int t = 0; t < eruption; t += 4) {
            int tt = t;
            FxScheduler.after(tt, () -> {
                CameraShake.add(c, 0.35f + 0.5f * tt / eruption, r * 3);
                RandomSource rr = RandomSource.create(seed + tt);
                for (Vec3 end : p.points()) {
                    Vec3 at = ground(c.lerp(end, rr.nextDouble()));
                    VfxManager.add(new ShardBurstVfx(at.add(0, 0.2, 0), UP, 0.2f, 0.12f, 2, 0.3f, Colors.argb(255, CORE), Colors.argb(0, ENERGY), 22,
                            rr.nextLong()).texture(VfxTextures.SPARK, true).physics(-0.004f, 0.95f));
                }
            });
        }
        // boulders of the ground torn loose float up around the blade, then the eruption flings them away
        RandomSource rnd = RandomSource.create(seed * 7);
        EarthChunkVfx rocks = new EarthChunkVfx(GroundMaterial.at(ground(c)), eruption + 60);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + rnd.nextDouble() * 0.4, d = 2.5 + rnd.nextDouble() * (r * 0.5);
            Vec3 base = ground(c.add(Math.cos(a) * d, 0, Math.sin(a) * d));
            Vec3 out = new Vec3(Math.cos(a) * 0.55, 0.45 + rnd.nextDouble() * 0.3, Math.sin(a) * 0.55);
            rocks.hover(base, 1.5 + rnd.nextDouble() * 2.5, eruption, out, 0.7f + rnd.nextFloat() * 0.6f, rnd.nextLong());
        }
        VfxManager.add(rocks);
    }

    private static void eruption(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        RandomSource rnd = RandomSource.create(p.seed() * 3);
        SpikeVfx s = pillars(c, 60).timing(3, 16);
        pillarRing(s, c, 2.2f, 7, 3.8f, 5.5f, 0, rnd);
        pillarRing(s, c, r * 0.5f, 12, 2.6f, 3.8f, 2, rnd);
        pillarRing(s, c, r * 0.9f, 16, 1.8f, 2.8f, 4, rnd);
        for (Vec3 end : p.points()) pillarLine(s, c, end, 1.8f, 3.5f, 1.6f, 0.25f, rnd);
        VfxManager.add(s);
        shockwave(c, r * 1.2f, 12);
        FxScheduler.after(2, () -> VfxManager.add(new ShockwaveVfx(c.add(0, 0.4, 0), UP, 1f, r * 1.6f, 0.8f, Colors.argb(160, DUST), 14)));
        dust(c, r * 1.2f, 24, p.seed());
        dust(c, r * 0.6f, 12, p.seed() * 5);
        rubble(c, 0.8f, 50, p.seed() * 7);
        debris(c, r * 0.4f, 14, 1.5f, p.seed() * 11);
        VfxManager.add(new FlashVfx(c.add(0, 1.2, 0), 2f, r * 0.65f, Colors.argb(210, CORE), 10).energy());
        ScreenFx.flash(LIME, 0.12f, 6);
        CameraShake.add(c, 1.6f, r * 4);
    }
}
