package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.weapons.ultimate.CrackNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * THE GRAND EARTHQUAKE. The sword strikes the ground and the land breaks outward from it: cracks race out glowing, a
 * wall of dust rolls out with the quake's front, the ground there is torn up in real blocks hurled into the air, and
 * a circle of runes holds the little ground around the caster still.
 */
public class GrandEarthquakeVfx extends Vfx {
    private static final int GLOW = 0xFFB040, LAVA = 0xFF5A1A, DUST = 0x8A7A68, RUNE = 0xE8C26A;
    private static final Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);

    private final Vec3 center;
    private final float radius, safe;
    private final int duration;
    private final CrackNetwork cracks;
    private final float[][] heights;
    private final RandomSource rng;
    private BlockDebrisVfx debris;

    public GrandEarthquakeVfx(Vec3 center, float radius, float safe, int duration, CrackNetwork cracks, long seed) {
        super(duration + 60);
        this.center = center;
        this.radius = radius;
        this.safe = safe;
        this.duration = duration;
        this.cracks = cracks;
        this.rng = RandomSource.create(seed);
        var level = Minecraft.getInstance().level;
        List<CrackNetwork.Segment> segs = cracks.segments();
        heights = new float[segs.size()][2];
        for (int i = 0; i < segs.size(); i++) {
            var s = segs.get(i);
            heights[i][0] = height(level, center.x + s.x0(), center.z + s.z0());
            heights[i][1] = height(level, center.x + s.x1(), center.z + s.z1());
        }
    }

    public GrandEarthquakeVfx tearing(BlockDebrisVfx debris) {
        this.debris = debris;
        return this;
    }

    private float height(net.minecraft.client.multiplayer.ClientLevel level, double x, double z) {
        if (level == null || !level.hasChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) return (float) center.y;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }

    private float front(float t) {
        return safe + (radius - safe) * Math.min(1, t / (duration * 0.85f));
    }

    @Override
    public void tick() {
        super.tick();
        if (debris == null) return;
        if (age > duration) {
            debris.close();
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        float f = front(age);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int n = Math.round(18 * com.fantasyweapons.config.ClientConfig.quality().density());
        for (int i = 0; i < n; i++) {
            double a = rng.nextDouble() * Math.PI * 2, d = f + rng.nextDouble() * 10;
            int x = (int) Math.floor(center.x + Math.cos(a) * d), z = (int) Math.floor(center.z + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1 - rng.nextInt(3);
            var state = level.getBlockState(m.set(x, top, z));
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 vel = out.scale(0.2 + rng.nextDouble() * 0.5).add(0, 0.6 + rng.nextDouble() * 1.1, 0);
            debris.throwBlock(state, new Vec3(x + 0.5, top + 1, z + 0.5), vel, rng.nextFloat() < 0.12f ? 2.2f : 1f, 40 + rng.nextInt(30));
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float fade = 1 - clamp01((t - duration) / 60f);
        if (fade <= 0) return;
        if (ctx.cam.distanceTo(center) < radius * 2) com.fantasyweapons.client.fx.SkyDarkness.request(0.4f * fade * clamp01(t / 20f));
        ctx.farClamp = VfxContext.viewReach() * 0.86;
        float f = front(t);
        var white = ctx.additive(VfxTextures.WHITE);
        var glow = ctx.additive(VfxTextures.GLOW);
        var mist = ctx.translucent(VfxTextures.MIST);
        var shock = ctx.additive(VfxTextures.SHOCKWAVE);
        // the cracks racing out
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 0.45f);
        List<CrackNetwork.Segment> segs = cracks.segments();
        for (int i = 0; i < segs.size(); i++) {
            var s = segs.get(i);
            if (s.from() > f) continue;
            double k = Math.min(1, (f - s.from()) / Math.max(1e-3, s.length()));
            Vec3 a = new Vec3(center.x + s.x0(), heights[i][0] + 0.3, center.z + s.z0());
            Vec3 b = a.lerp(new Vec3(center.x + s.x1(), heights[i][1] + 0.3, center.z + s.z1()), k);
            float w = (float) s.width();
            GrantExecutionVfx.flatLine(ctx, white, a, b, w * 0.8f, Colors.alpha(fade * pulse, GLOW));
            GrantExecutionVfx.flatLine(ctx, glow, a, b, w * 3f, Colors.alpha(fade * 0.45f * pulse, LAVA));
        }
        // the wall of dust rolling out with the quake
        int n = ctx.segments(64);
        if (t < duration) {
            for (int i = 0; i < n; i++) {
                double ang = i * Math.PI * 2 / n + t * 0.002;
                float cyc = ((i * 0.37f) + t * 0.03f) % 1f;
                Vec3 p = center.add(Math.cos(ang) * (f + cyc * 6), 4 + cyc * 26, Math.sin(ang) * (f + cyc * 6));
                ctx.billboard(mist, p, 16 + cyc * 22, i + t * 0.01f, Colors.alpha(fade * 0.55f * (1 - cyc) * Math.min(1, cyc * 5), DUST));
            }
        }
        // shockwaves from the blow
        for (int k = 0; k < 4; k++) {
            float s = (t - k * 12) / 50f;
            if (s < 0 || s > 1) continue;
            ctx.disc(shock, center.add(0, 0.6 + k * 0.2, 0), X, Z, safe + (radius * 0.6f) * easeOut(s), k, Colors.alpha((1 - s) * 0.85f, k == 0 ? 0xFFF4E0 : GLOW));
        }
        ctx.farClamp = 0;
        // the circle of runes that holds the ground around the caster
        float hold = fade * clamp01(t / 8f);
        var runes = ctx.additive(VfxTextures.GRAND_CIRCLE);
        ctx.disc(runes, center.add(0, 0.08, 0), X, Z, safe, t * 0.01f, Colors.alpha(hold * 0.9f, RUNE));
        ctx.disc(glow, center.add(0, 0.1, 0), X, Z, safe * 1.6f, 0, Colors.alpha(hold * 0.35f, RUNE));
        ctx.ring(ctx.additive(VfxTextures.RING), center.add(0, 0.12, 0), X, Z, safe * 0.98f, safe * 1.06f, 64, Colors.alpha(hold, 0xFFF4C8));
    }
}
