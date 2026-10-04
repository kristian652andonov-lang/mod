package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A batch of custom fragments (crystal shards, embers, motes...) simulated as one object: velocity, gravity, drag,
 * spin, size and colour over life. One VFX object, many fragments — no entity or particle per fragment.
 */
public class ShardBurstVfx extends Vfx {
    private final int count;
    private final double[] x, y, z, px, py, pz, vx, vy, vz;
    private final float[] size, rot, spin, lifeScale;
    private final int colorStart;
    private final int colorEnd;
    private ResourceLocation texture = VfxTextures.SHARD;
    private float gravity = 0.02f;
    private float drag = 0.9f;
    private boolean stretch = true;
    private boolean energy;
    private Vec3 attractor;
    private float attractStrength;

    /**
     * @param origin    emission point
     * @param dir       main direction (zero vector = spherical)
     * @param spread    0 = all along dir, 1 = full sphere
     * @param speed     initial speed (blocks/tick)
     */
    public ShardBurstVfx(Vec3 origin, Vec3 dir, float spread, float speed, int count, float size, int colorStart, int colorEnd, int lifetime, long seed) {
        super(lifetime);
        this.count = count;
        x = new double[count];
        y = new double[count];
        z = new double[count];
        px = new double[count];
        py = new double[count];
        pz = new double[count];
        vx = new double[count];
        vy = new double[count];
        vz = new double[count];
        this.size = new float[count];
        rot = new float[count];
        spin = new float[count];
        lifeScale = new float[count];
        this.colorStart = colorStart;
        this.colorEnd = colorEnd;
        RandomSource rnd = RandomSource.create(seed);
        Vec3 d = dir.lengthSqr() < 1e-6 ? Vec3.ZERO : dir.normalize();
        for (int i = 0; i < count; i++) {
            Vec3 r = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
            Vec3 v = d == Vec3.ZERO ? r : d.scale(1 - spread).add(r.scale(spread)).normalize();
            double s = speed * (0.4 + rnd.nextDouble() * 0.9);
            x[i] = px[i] = origin.x;
            y[i] = py[i] = origin.y;
            z[i] = pz[i] = origin.z;
            vx[i] = v.x * s;
            vy[i] = v.y * s;
            vz[i] = v.z * s;
            this.size[i] = size * (0.5f + rnd.nextFloat());
            rot[i] = rnd.nextFloat() * 6.28f;
            spin[i] = (rnd.nextFloat() - 0.5f) * 0.6f;
            lifeScale[i] = 0.6f + rnd.nextFloat() * 0.4f;
        }
    }

    public ShardBurstVfx texture(ResourceLocation tex, boolean stretch) {
        this.texture = tex;
        this.stretch = stretch;
        return this;
    }

    public ShardBurstVfx physics(float gravity, float drag) {
        this.gravity = gravity;
        this.drag = drag;
        return this;
    }

    public ShardBurstVfx energy() {
        this.energy = true;
        return this;
    }

    /** Pull fragments towards a point (implosions, charge gathering). */
    public ShardBurstVfx attract(Vec3 point, float strength) {
        this.attractor = point;
        this.attractStrength = strength;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        for (int i = 0; i < count; i++) {
            px[i] = x[i];
            py[i] = y[i];
            pz[i] = z[i];
            if (attractor != null) {
                double dx = attractor.x - x[i], dy = attractor.y - y[i], dz = attractor.z - z[i];
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-4;
                vx[i] += dx / len * attractStrength;
                vy[i] += dy / len * attractStrength;
                vz[i] += dz / len * attractStrength;
            }
            vy[i] -= gravity;
            vx[i] *= drag;
            vy[i] *= drag;
            vz[i] *= drag;
            x[i] += vx[i];
            y[i] += vy[i];
            z[i] += vz[i];
            rot[i] += spin[i];
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        var vc = energy ? ctx.energy(texture) : ctx.additive(texture);
        float p = ctx.partial;
        int n = Math.max(1, Math.round(count * Math.min(1f, ctx.density)));
        for (int i = 0; i < n; i++) {
            float lt = Math.min(1f, t / lifeScale[i]);
            if (lt >= 1f) continue;
            float alpha = (1 - easeIn(lt)) * Math.min(1f, lt * 8f + 0.3f);
            int c = Colors.lerp(colorStart, colorEnd, lt);
            int col = Colors.alpha(alpha * ((c >>> 24) & 255) / 255f, c);
            Vec3 pos = new Vec3(px[i] + (x[i] - px[i]) * p, py[i] + (y[i] - py[i]) * p, pz[i] + (z[i] - pz[i]) * p);
            float s = size[i] * (1 - lt * 0.5f);
            if (stretch) {
                Vec3 vel = new Vec3(vx[i], vy[i], vz[i]);
                float len = (float) Math.max(s, vel.length() * 3.5);
                ctx.stretched(vc, pos, vel.lengthSqr() < 1e-8 ? ctx.camUp : vel, len, s * 0.6f, col);
            } else {
                ctx.billboard(vc, pos, s, rot[i] + spin[i] * p, col);
            }
        }
    }
}
