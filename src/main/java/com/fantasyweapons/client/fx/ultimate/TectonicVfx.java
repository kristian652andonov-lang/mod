package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * TECTONIC BREAKAGE. A glowing crack tears straight across the land, then the plates pull apart: the canyon widens
 * for many seconds, the light of the lava rising from the bottom of the world, dust boiling off its lips and the land at
 * its edges breaking away in real blocks that tumble into the abyss.
 */
public class TectonicVfx extends Vfx {
    private static final int GLOW = 0xFFB040, LAVA = 0xFF5A1A, DUST = 0x8A7A68;
    private static final int SAMPLES = 41;

    private final Vec3 center, dir, across;
    private final float length, width;
    private final int duration;
    private final float[] heights = new float[SAMPLES];
    private final RandomSource rng;
    private BlockDebrisVfx debris;

    public TectonicVfx(Vec3 center, Vec3 dir, float length, float width, int duration, long seed) {
        super(duration + 80);
        this.center = center;
        this.dir = dir.normalize();
        this.across = new Vec3(-this.dir.z, 0, this.dir.x);
        this.length = length;
        this.width = width;
        this.duration = duration;
        this.rng = RandomSource.create(seed);
        var level = Minecraft.getInstance().level;
        for (int i = 0; i < SAMPLES; i++) {
            Vec3 p = point(i / (float) (SAMPLES - 1));
            int x = (int) Math.floor(p.x), z = (int) Math.floor(p.z);
            heights[i] = level != null && level.hasChunk(x >> 4, z >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : (float) center.y;
        }
    }

    public TectonicVfx shedding(BlockDebrisVfx debris) {
        this.debris = debris;
        return this;
    }

    /** A point on the canyon's line, {@code u} from 0 (one end) to 1 (the other), at the target's height. */
    private Vec3 point(float u) {
        return center.add(dir.scale((u - 0.5) * length));
    }

    private float halfWidth(float t) {
        return width / 2 * Math.max(0.06f, Math.min(1, t / duration));
    }

    /** How far from the middle the tear has run (0..1 of the half-length). */
    private float tear(float t) {
        return easeOut(clamp01(t / 30f));
    }

    @Override
    public void tick() {
        super.tick();
        if (debris == null) return;
        if (age > duration + 20) {
            debris.close();
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null || age < 12) return;
        float half = halfWidth(age);
        int n = Math.round(10 * com.fantasyweapons.config.ClientConfig.quality().density());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            float u = 0.5f + (rng.nextFloat() - 0.5f) * tear(age) * 0.9f;
            int side = rng.nextBoolean() ? 1 : -1;
            Vec3 lip = point(u).add(across.scale(side * (half + 1 + rng.nextFloat() * 4)));
            int x = (int) Math.floor(lip.x), z = (int) Math.floor(lip.z);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1 - rng.nextInt(4);
            var state = level.getBlockState(m.set(x, top, z));
            Vec3 in = across.scale(-side * (0.15 + rng.nextFloat() * 0.35)).add(0, 0.1 + rng.nextFloat() * 0.3, 0);
            debris.throwBlock(state, new Vec3(x + 0.5, top + 0.5, z + 0.5), in, rng.nextFloat() < 0.15f ? 2f : 1f, 50 + rng.nextInt(40));
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float fade = 1 - clamp01((t - duration - 20) / 60f);
        if (fade <= 0) return;
        ctx.farClamp = VfxContext.viewReach() * 0.86;
        float half = halfWidth(t), run = tear(t);
        float open = clamp01(t / duration);
        var white = ctx.additive(VfxTextures.WHITE);
        var glow = ctx.additive(VfxTextures.GLOW);
        var soft = ctx.additive(VfxTextures.BEAM_SOFT);
        var mist = ctx.translucent(VfxTextures.MIST);
        float pulse = 0.8f + 0.2f * (float) Math.sin(t * 0.4f);
        for (int i = 0; i < SAMPLES - 1; i++) {
            float u0 = i / (float) (SAMPLES - 1), u1 = (i + 1) / (float) (SAMPLES - 1);
            if (Math.abs(u0 - 0.5f) > run * 0.5f + 0.02f) continue;
            float taper = (float) Math.sqrt(Math.min(1, (0.5f - Math.abs((u0 + u1) / 2 - 0.5f)) / 0.18f));
            Vec3 a = point(u0).with(net.minecraft.core.Direction.Axis.Y, heights[i] + 0.3), b = point(u1).with(net.minecraft.core.Direction.Axis.Y, heights[i + 1] + 0.3);
            // the crack's glowing line (it becomes the light at the canyon's lips as it widens)
            float w = Math.max(1.5f, half * taper);
            GrantExecutionVfx.flatLine(ctx, white, a, b, Math.min(w * 2, 6), Colors.alpha(fade * pulse * (1 - open * 0.7f), GLOW));
            GrantExecutionVfx.flatLine(ctx, glow, a, b, w * 2.6f, Colors.alpha(fade * 0.5f * pulse, LAVA));
            // light rising from the lava far below
            Vec3 mid = a.lerp(b, 0.5);
            ctx.softBeam(soft, mid.add(0, -70, 0), mid.add(0, 6 + 10 * open, 0), (float) a.distanceTo(b) * 1.2f, Colors.alpha(fade * 0.35f * open, LAVA), 0, 1, 0, 1);
            // dust boiling off both lips
            for (int side = -1; side <= 1; side += 2) {
                float cyc = ((i * 0.37f + side * 0.21f) + t * 0.01f) % 1f;
                Vec3 lip = mid.add(across.scale(side * (w + 2))).add(0, cyc * 22, 0);
                ctx.billboard(mist, lip, 10 + cyc * 18, i + side, Colors.alpha(fade * 0.4f * (1 - cyc) * Math.min(1, cyc * 5), DUST));
            }
        }
        ctx.farClamp = 0;
    }
}
