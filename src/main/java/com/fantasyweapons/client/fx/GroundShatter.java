package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.GroundMaterial;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.EarthChunkVfx;
import com.fantasyweapons.client.vfx.effects.FissureVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Shared broken-ground effects, built from the terrain that was struck (see {@link GroundMaterial}): fine fracture
 * cracks, plates of ground buckling up around the blow, chunks of it thrown out on ballistic arcs, and dust in the
 * ground's own colour. Purely visual: the terrain itself is never modified.
 */
public final class GroundShatter {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private GroundShatter() {
    }

    /**
     * A heavy blow into the ground at {@code at}: cracks out to about {@code radius}, a ring of buckled plates, thrown
     * chunks and a rolling ring of dust. {@code power} (about 0.5 - 2) scales how violently the ground breaks.
     */
    public static void impact(Vec3 at, float radius, float power, long seed) {
        Vec3 g = FrostrendFx.groundOrNull(at, 6.5);
        if (g == null) return; // no ground to break
        GroundMaterial m = GroundMaterial.at(g);
        RandomSource r = RandomSource.create(seed);
        int life = 50 + Math.round(radius * 4);
        cracks(g, radius * 1.15f, m, life + 30);

        EarthChunkVfx e = new EarthChunkVfx(m, life);
        // plates of ground heaved up around the blow: uneven spacing, gaps, odd sizes, the odd one tipped inwards,
        // and broken shards beside some of them; nothing in neat rings
        int plates = Math.max(4, Math.min(24, Math.round(radius * 2.2f * (0.7f + 0.6f * r.nextFloat()))));
        float plateW = Math.max(0.5f, Math.min(1.5f, radius * 0.26f));
        for (int i = 0; i < plates; i++) {
            if (r.nextFloat() < 0.2f) continue;
            double a = (i + r.nextDouble() * 0.9) * Math.PI * 2 / plates;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            double dist = radius * (0.22 + 0.65 * r.nextDouble());
            Vec3 base = FrostrendFx.groundOrNull(g.add(dir.scale(dist)), 6.5);
            if (base == null) continue; // over a drop
            float w = plateW * (0.5f + r.nextFloat());
            float tilt = (r.nextFloat() < 0.15f ? -0.25f : 0.15f + 0.65f * r.nextFloat()) * Math.min(1.2f, power);
            int delay = (int) (dist / radius * 4) + r.nextInt(3);
            e.slab(base, dir, w, 0.22f + 0.28f * r.nextFloat(), w * (0.5f + 0.5f * r.nextFloat()), tilt, 0.05f + 0.4f * power * r.nextFloat(), delay,
                    r.nextLong());
            if (r.nextFloat() < 0.3f) {
                Vec3 nb = FrostrendFx.groundOrNull(base.add(dir.scale(w * 0.6)).add(-dir.z * (r.nextDouble() - 0.5), 0, dir.x * (r.nextDouble() - 0.5)), 6.5);
                if (nb != null) e.slab(nb, dir, w * 0.45f, 0.18f, w * 0.35f, tilt * 1.3f, 0.1f + 0.2f * r.nextFloat(), delay + 1, r.nextLong());
            }
        }
        int chunks = Math.max(6, Math.min(40, Math.round((8 + radius * 3) * power * (0.8f + 0.4f * r.nextFloat()))));
        double speed = Math.sqrt(Math.max(1, radius) / 3.0);
        for (int i = 0; i < chunks; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            double h = (0.08 + 0.18 * r.nextDouble()) * power * speed;
            double vy = (0.3 + 0.35 * r.nextDouble()) * power;
            Vec3 from = g.add(dir.scale(radius * 0.3 * r.nextDouble())).add(0, 0.15, 0);
            // mostly small clods, the odd big lump
            float size = 0.12f + 0.42f * r.nextFloat() * r.nextFloat() * Math.min(1.5f, power);
            e.chunk(from, dir.scale(h).add(0, vy, 0), size, g.y + 0.08, r.nextLong());
        }
        VfxManager.add(e);
        dust(g, radius, power, m, r.nextLong());
    }

    /**
     * The ground tearing open along a line: plates heave up on both sides, tipped away from the tear, with chunks
     * popping out of it and dust along its length. {@code delayPerBlock} lets the tear travel from {@code from}.
     */
    public static void line(Vec3 from, Vec3 to, float width, float power, float delayPerBlock, long seed) {
        Vec3 g0 = FrostrendFx.groundOrNull(from, 6.5);
        if (g0 == null) return;
        GroundMaterial m = GroundMaterial.at(g0);
        RandomSource r = RandomSource.create(seed);
        Vec3 d = to.subtract(from);
        double len = Math.max(0.5, d.horizontalDistance());
        Vec3 dir = new Vec3(d.x, 0, d.z).normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        int life = 60 + (int) (len * delayPerBlock);
        EarthChunkVfx e = new EarthChunkVfx(m, life);
        // plates lifted along the tear: uneven steps, sometimes one side only, sometimes a gap, all different
        for (double s = 0.4 + r.nextDouble() * 0.6; s < len; s += 0.55 + r.nextDouble() * 1.05) {
            int delay = (int) (s * delayPerBlock);
            float roll = r.nextFloat();
            if (roll < 0.08f) continue;
            for (int k = -1; k <= 1; k += 2) {
                if (roll < 0.33f && k == 1 || roll >= 0.33f && roll < 0.58f && k == -1) continue;
                float w = 0.35f + 0.75f * r.nextFloat();
                float dep = 0.3f + 0.5f * r.nextFloat();
                double off = width * 0.18 + dep * 0.45 + r.nextDouble() * 0.35;
                Vec3 base = FrostrendFx.groundOrNull(from.add(dir.scale(s + (r.nextDouble() - 0.5) * 0.4)).add(side.scale(k * off)), 6.5);
                if (base == null) continue;
                e.slab(base, side.scale(k), w, 0.2f + 0.2f * r.nextFloat(), dep, (0.2f + 0.55f * r.nextFloat()) * Math.min(1.2f, power),
                        0.08f + 0.27f * power * r.nextFloat(), delay + r.nextInt(2), r.nextLong());
            }
            Vec3 at = FrostrendFx.groundOrNull(from.add(dir.scale(s)), 6.5);
            if (at == null) continue;
            if (r.nextFloat() < 0.5f) {
                Vec3 v = side.scale((r.nextDouble() - 0.5) * 0.3).add(0, (0.2 + 0.3 * r.nextDouble()) * power, 0);
                e.chunk(at.add(0, 0.1, 0), v, 0.1f + 0.22f * r.nextFloat() * r.nextFloat(), at.y + 0.08, r.nextLong());
            }
            if (r.nextFloat() < 0.3f) {
                int dl = delay;
                long sd = r.nextLong();
                FxScheduler.after(dl, () -> puff(at, 1.2f + width * 0.5f, m, sd));
            }
        }
        VfxManager.add(e);
    }

    /** Fine fracture cracks radiating from {@code g}: plain dark fractures in the ground's own darkest colour. */
    public static void cracks(Vec3 g, float radius, GroundMaterial m, int life) {
        cracks(g, radius, m, life, -1, 0, (long) (g.x * 7919 + g.z * 104729));
    }

    /**
     * Thin fractal cracks radiating from {@code g} at uneven angles and lengths, forking into hairlines. With a
     * {@code glow} colour (not -1) embers smoulder deep inside them (fire, ground energy); otherwise they are plain.
     */
    public static void cracks(Vec3 g, float radius, GroundMaterial m, int life, int glow, int core, long seed) {
        cracks(g, radius, Colors.lerpRgb(Colors.darken(m.dust(), 0.82f), 0x0E0A07, 0.5f), life, glow, core, seed);
    }

    /** As above with an explicit colour for the gash (roots, frost, blood...). */
    public static void cracks(Vec3 g, float radius, int dark, int life, int glow, int core, long seed) {
        RandomSource r = RandomSource.create(seed);
        int n = Math.max(3, Math.min(10, Math.round(radius * 1.6f + r.nextFloat() * 2)));
        int grow = Math.max(3, Math.round(radius * 1.2f));
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = a0 + (i + (r.nextDouble() - 0.5) * 0.8) * Math.PI * 2 / n;
            double len = radius * (0.45 + 0.6 * r.nextDouble());
            Vec3 end = g.add(Math.cos(a) * len, 0, Math.sin(a) * len);
            float w = (0.5f + 0.5f * r.nextFloat()) * Math.min(1.4f, 0.6f + radius * 0.15f);
            FissureVfx f = new FissureVfx(java.util.List.of(g, end), w, glow < 0 ? 0 : glow, glow < 0 ? 0 : core, grow, life, r.nextLong(), FrostrendFx::ground)
                    .darkColor(dark);
            VfxManager.add(glow < 0 ? f.glowless() : f.curtain(0.4f));
        }
    }

    /** A ring of dust in the ground's colour rolling outward, and a cloud rising from the middle. */
    public static void dust(Vec3 g, float radius, float power, GroundMaterial m, long seed) {
        RandomSource r = RandomSource.create(seed);
        int col = Colors.lerpRgb(m.dust(), 0xE8E0D0, 0.2f);
        int puffs = Math.max(6, Math.min(18, Math.round(radius * 2.2f)));
        for (int i = 0; i < puffs; i++) {
            double a = i * Math.PI * 2 / puffs + r.nextDouble() * 0.4;
            Vec3 dir = new Vec3(Math.cos(a), 0.1, Math.sin(a));
            VfxManager.add(new ShardBurstVfx(g.add(dir.x * 0.5, 0.35, dir.z * 0.5), dir, 0.25f, radius * 0.05f * Math.min(1.5f, power), 3,
                    1.8f + radius * 0.12f, Colors.argb(Math.round(190 * m.light()), m.lit(col, 1f)), Colors.argb(0, col), 34 + r.nextInt(12), r.nextLong())
                    .texture(VfxTextures.MIST, false).physics(-0.003f, 0.9f).translucent());
        }
        puff(g, 1.5f + radius * 0.25f, m, r.nextLong());
    }

    private static void puff(Vec3 g, float size, GroundMaterial m, long seed) {
        int col = Colors.lerpRgb(m.dust(), 0xE8E0D0, 0.2f);
        VfxManager.add(new ShardBurstVfx(g.add(0, 0.5, 0), UP, 0.5f, 0.06f, 5, size, Colors.argb(Math.round(170 * m.light()), m.lit(col, 1f)),
                Colors.argb(0, col), 40, seed).texture(VfxTextures.MIST, false).physics(-0.004f, 0.92f).translucent());
    }
}
