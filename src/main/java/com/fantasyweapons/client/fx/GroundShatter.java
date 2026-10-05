package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.GroundMaterial;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.EarthChunkVfx;
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
        Vec3 g = FrostrendFx.ground(at);
        GroundMaterial m = GroundMaterial.at(g);
        RandomSource r = RandomSource.create(seed);
        int life = 50 + Math.round(radius * 4);
        cracks(g, radius * 1.15f, m, life + 30);

        EarthChunkVfx e = new EarthChunkVfx(m, life);
        int plates = Math.max(5, Math.min(22, Math.round(radius * 2.2f)));
        float plateW = Math.max(0.55f, Math.min(1.5f, radius * 0.26f));
        for (int i = 0; i < plates; i++) {
            double a = i * Math.PI * 2 / plates + r.nextDouble() * 0.35;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            double dist = radius * (0.3 + 0.45 * r.nextDouble());
            Vec3 base = FrostrendFx.ground(g.add(dir.scale(dist)));
            float w = plateW * (0.75f + 0.5f * r.nextFloat());
            e.slab(base, dir, w, 0.32f + 0.14f * r.nextFloat(), w * (0.65f + 0.3f * r.nextFloat()),
                    (0.3f + 0.4f * r.nextFloat()) * Math.min(1.2f, power), 0.18f + 0.22f * power * r.nextFloat(), (int) (dist / radius * 4), r.nextLong());
        }
        int chunks = Math.max(6, Math.min(40, Math.round((8 + radius * 3) * power)));
        double speed = Math.sqrt(Math.max(1, radius) / 3.0);
        for (int i = 0; i < chunks; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            double h = (0.08 + 0.18 * r.nextDouble()) * power * speed;
            double vy = (0.3 + 0.35 * r.nextDouble()) * power;
            Vec3 from = g.add(dir.scale(radius * 0.3 * r.nextDouble())).add(0, 0.15, 0);
            e.chunk(from, dir.scale(h).add(0, vy, 0), 0.16f + 0.28f * r.nextFloat() * Math.min(1.5f, power), g.y + 0.08, r.nextLong());
        }
        VfxManager.add(e);
        dust(g, radius, power, m, r.nextLong());
    }

    /**
     * The ground tearing open along a line: plates heave up on both sides, tipped away from the tear, with chunks
     * popping out of it and dust along its length. {@code delayPerBlock} lets the tear travel from {@code from}.
     */
    public static void line(Vec3 from, Vec3 to, float width, float power, float delayPerBlock, long seed) {
        Vec3 g0 = FrostrendFx.ground(from);
        GroundMaterial m = GroundMaterial.at(g0);
        RandomSource r = RandomSource.create(seed);
        Vec3 d = to.subtract(from);
        double len = Math.max(0.5, d.horizontalDistance());
        Vec3 dir = new Vec3(d.x, 0, d.z).normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        int life = 60 + (int) (len * delayPerBlock);
        EarthChunkVfx e = new EarthChunkVfx(m, life);
        for (double s = 0.6; s < len; s += 0.75 + r.nextDouble() * 0.4) {
            int delay = (int) (s * delayPerBlock);
            for (int k = -1; k <= 1; k += 2) {
                Vec3 base = FrostrendFx.ground(from.add(dir.scale(s)).add(side.scale(k * (width * 0.55 + r.nextDouble() * 0.25))));
                float w = 0.6f + 0.35f * r.nextFloat();
                e.slab(base, side.scale(k), w, 0.3f + 0.12f * r.nextFloat(), 0.55f + 0.25f * r.nextFloat(),
                        (0.35f + 0.35f * r.nextFloat()) * Math.min(1.2f, power), 0.15f + 0.2f * power * r.nextFloat(), delay, r.nextLong());
            }
            if (r.nextFloat() < 0.6f) {
                Vec3 at = FrostrendFx.ground(from.add(dir.scale(s)));
                Vec3 v = side.scale((r.nextDouble() - 0.5) * 0.25).add(0, (0.25 + 0.25 * r.nextDouble()) * power, 0);
                e.chunk(at.add(0, 0.1, 0), v, 0.15f + 0.2f * r.nextFloat(), at.y + 0.08, r.nextLong());
            }
            if (r.nextFloat() < 0.35f) {
                Vec3 at = FrostrendFx.ground(from.add(dir.scale(s)));
                int dl = delay;
                long sd = r.nextLong();
                FxScheduler.after(dl, () -> puff(at, 1.2f + width * 0.5f, m, sd));
            }
        }
        VfxManager.add(e);
    }

    /** Fine fracture cracks radiating from {@code g}. */
    public static void cracks(Vec3 g, float radius, GroundMaterial m, int life) {
        int dark = Colors.lerpRgb(Colors.darken(m.dust(), 0.82f), 0x0E0A07, 0.5f);
        VfxManager.add(new DecalVfx(g.add(0, 0.02, 0), UP, radius, Colors.argb(235, dark), VfxTextures.CRACK, life)
                .timing(0.03f, 0.35f).translucent().satellites(0));
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
