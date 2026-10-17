package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.weapons.ultimate.CrackNetwork;
import com.fantasyweapons.weapons.ultimate.DragonPath;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * THE ARCH DRAGON OF HELL. A rift of hellfire tears open in the ground behind the caster and the Worldburner bursts up out
 * of it roaring - the artist's dragon, 256 blocks from snout to tail, its magma crust, gills and wing veins glowing. It
 * climbs on beating wings, sweeps round high over the land, glides down into the dive breathing fire, and ploughs into
 * the target; the ground explodes into a mushroom of fire and smoke, splits open in glowing cracks for the whole radius
 * and spits lava.
 */
public class ArchDragonVfx extends Vfx {
    private static final int LAVA = 0xFF5A1A, FIRE = 0xFFA040, HOT = 0xFFE6A0, SMOKE = 0x2A2020, RUNE = 0xFF4A2A;
    private static final int SIDES = 12, SPLIT = 40;
    private static final Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1), UP = new Vec3(0, 1, 0);

    private final DragonPath path;
    private final float length, radius, crater;
    /** The dragon the effect flies (its own animations). */
    private final Worldburner dragon = new Worldburner();
    private final int flight, riftClose, plunged;
    private final CrackNetwork cracks;
    private final RandomSource rng;
    private float[][] heights;
    private BlockDebrisVfx debris;

    public ArchDragonVfx(DragonPath path, float length, float radius, CrackNetwork cracks, long seed) {
        super(path.flight() + Math.max(SPLIT + 70, (int) (length / path.strikeSpeed()) + 30));
        this.path = path;
        this.length = length;
        this.radius = radius;
        this.crater = radius * 0.3f;
        this.flight = path.flight();
        this.cracks = cracks;
        this.rng = RandomSource.create(seed);
        int close = flight;
        for (int t = 0; t < flight * 2; t++) {
            if (path.head(t) - length > 6) {
                close = t;
                break;
            }
        }
        riftClose = close;
        plunged = flight + (int) (length / path.strikeSpeed()) + 4;
    }

    public ArchDragonVfx tearing(BlockDebrisVfx debris) {
        this.debris = debris;
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------
    // tick: real blocks torn out where it bursts up and where it strikes
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (age == flight) sampleHeights(level);
        if (debris == null) return;
        if (age > flight + SPLIT) {
            debris.close();
            return;
        }
        float density = com.fantasyweapons.config.ClientConfig.quality().density();
        if (age >= 1 && age <= 8) burst(level, path.rift(), 14, Math.round(22 * density), 1.2, 1.0);
        int ts = age - flight;
        if (ts >= 0 && ts <= 6) burst(level, path.target(), crater, Math.round(40 * density), 1.6, 1.4);
        if (ts > 0 && ts <= SPLIT) {
            float front = crater + (radius - crater) * ts / SPLIT;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int i = 0; i < Math.round(12 * density); i++) {
                double a = rng.nextDouble() * Math.PI * 2, d = front + rng.nextDouble() * 8;
                Vec3 at = path.target().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                throwFrom(level, m, at, new Vec3(Math.cos(a) * 0.4, 0.8 + rng.nextDouble(), Math.sin(a) * 0.4), 1f);
            }
        }
    }

    private void burst(net.minecraft.client.multiplayer.ClientLevel level, Vec3 at, float r, int n, double up, double out) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            double a = rng.nextDouble() * Math.PI * 2, d = rng.nextDouble() * r;
            Vec3 p = at.add(Math.cos(a) * d, 0, Math.sin(a) * d);
            Vec3 vel = new Vec3(Math.cos(a) * out * (0.3 + rng.nextDouble() * 0.7), up * (1 + rng.nextDouble()), Math.sin(a) * out * (0.3 + rng.nextDouble() * 0.7));
            throwFrom(level, m, p, vel, rng.nextFloat() < 0.2f ? 2.4f : 1.2f);
        }
    }

    private void throwFrom(net.minecraft.client.multiplayer.ClientLevel level, BlockPos.MutableBlockPos m, Vec3 at, Vec3 vel, float size) {
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1 - rng.nextInt(3);
        var state = level.getBlockState(m.set(x, y, z));
        if (state.isAir()) return;
        debris.throwBlock(state, new Vec3(x + 0.5, y + 1, z + 0.5), vel, size, 40 + rng.nextInt(30));
    }

    private void sampleHeights(net.minecraft.client.multiplayer.ClientLevel level) {
        List<CrackNetwork.Segment> segs = cracks.segments();
        heights = new float[segs.size()][2];
        Vec3 c = path.target();
        for (int i = 0; i < segs.size(); i++) {
            var s = segs.get(i);
            heights[i][0] = height(level, c.x + s.x0(), c.z + s.z0());
            heights[i][1] = height(level, c.x + s.x1(), c.z + s.z1());
        }
    }

    private float height(net.minecraft.client.multiplayer.ClientLevel level, double x, double z) {
        if (!level.hasChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) return (float) path.target().y;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }

    // ------------------------------------------------------------------------------------------------------------
    // render
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float end = 1 - clamp01((t - lifetime + 50) / 50f);
        if (end <= 0) return;
        double near = Math.min(ctx.cam.distanceTo(path.target()), ctx.cam.distanceTo(path.rift()));
        if (near < radius * 3) com.fantasyweapons.client.fx.SkyDarkness.request(0.6f * end * clamp01(t / 12f));
        ctx.farClamp = VfxContext.viewReach() * 0.86;
        drawRift(ctx, t);
        if (t < plunged) drawWorldburner(ctx, t);
        if (t >= flight) drawStrike(ctx, t - flight, end);
        ctx.farClamp = 0;
    }

    /** The rift of hellfire the dragon bursts out of. */
    private void drawRift(VfxContext ctx, float t) {
        float a = easeOut(clamp01(t / 10f)) * (1 - clamp01((t - riftClose) / 24f));
        if (a <= 0) return;
        Vec3 c = path.rift().add(0, 0.2, 0);
        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.disc(ctx.additive(VfxTextures.GRAND_CIRCLE), c, X, Z, 28 * a, t * 0.02f, Colors.alpha(a * 0.95f, RUNE));
        ctx.disc(ctx.additive(VfxTextures.RUNE_CIRCLE), c.add(0, 0.1, 0), X, Z, 17 * a, -t * 0.035f, Colors.alpha(a, FIRE));
        ctx.satellites(ctx.additive(VfxTextures.SUB_CIRCLE), c.add(0, 0.15, 0), X, Z, 34 * a, 6, 8, t * 0.01f, t * 0.05f, Colors.alpha(a * 0.85f, LAVA));
        ctx.disc(glow, c.add(0, 0.3, 0), X, Z, 40 * a, 0, Colors.alpha(a * 0.4f, LAVA));
        var flame = ctx.additive(VfxTextures.FLAME);
        for (int i = 0; i < 24; i++) {
            double ang = i * Math.PI * 2 / 24 + t * 0.02;
            float cyc = ((i * 0.37f) + t * 0.06f) % 1f;
            Vec3 p = c.add(Math.cos(ang) * 13 * a, cyc * 14, Math.sin(ang) * 13 * a);
            ctx.billboard(flame, p, 6 * (1 - cyc) + 2, i, Colors.alpha(a * (1 - cyc), i % 3 == 0 ? HOT : FIRE));
        }
        // the pillar of hellfire as it bursts out
        float burst = clamp01(t / 20f);
        if (burst < 1) {
            float k = 1 - burst;
            ctx.softBeam(glow, c, c.add(0, 160, 0), 44 * (0.4f + burst), Colors.alpha(k * 0.8f, FIRE), 0, 1, 0.05f, 0.4f);
            ctx.softBeam(ctx.additive(VfxTextures.WHITE), c, c.add(0, 120, 0), 14 * (0.4f + burst), Colors.alpha(k * 0.7f, HOT), 0, 1, 0.05f, 0.4f);
            ctx.disc(ctx.additive(VfxTextures.SHOCKWAVE), c.add(0, 0.5, 0), X, Z, 20 + 80 * easeOut(burst), 0, Colors.alpha(k, 0xFFE0C0));
        }
    }

    /**
     * The Worldburner itself, the artist's dragon: its mouth on the head of the path, its body along the path behind,
     * bending with it; it roars as it breaks out of the ground, beats its wings as it climbs, glides along the top of its
     * arc and down into the dive, and breathes fire as it falls on its prey. Its tail trails flame, and a glow along it
     * reads from far off.
     */
    private void drawWorldburner(VfxContext ctx, float t) {
        double scale = length / Worldburner.LENGTH;
        double head = path.head(t);
        Worldburner.Spine spine = new Worldburner.Spine() {
            @Override
            public Vec3 at(double back) {
                return path.at(head - back);
            }

            @Override
            public Vec3 heading(double back) {
                return path.direction(head - back);
            }

            @Override
            public Vec3 up(double back) {
                return path.up(head - back);
            }
        };
        float u = t / flight;
        int breath = breathStarts();
        dragon.move(u < 0.42f ? Worldburner.FLY : Worldburner.GLIDE);
        dragon.act(t < Worldburner.ROAR_LENGTH ? Worldburner.ROAR : t >= breath ? Worldburner.FIRE_BREATH : null);
        int light = net.minecraft.client.renderer.LightTexture.pack(12, 15);
        dragon.render(ctx, spine, scale, light, t);

        var flame = ctx.additive(VfxTextures.FLAME);
        var glow = ctx.additive(VfxTextures.GLOW);
        // the tail's flame
        Vec3 tail = dragon.tailTip();
        float flick = 0.75f + 0.25f * (float) Math.sin(t * 0.9f);
        ctx.billboard(flame, tail, (float) (14 * scale) * flick, t * 0.2f, Colors.alpha(0.9f, FIRE));
        ctx.billboard(glow, tail, (float) (30 * scale), 0, Colors.alpha(0.5f, LAVA));
        // a glow along its body, so it reads from far away
        for (int i = 0; i < 6; i++) {
            double back = (40 + i * 40) * scale;
            Vec3 p = spine.at(back).add(spine.up(back).scale(3 * scale));
            ctx.billboard(glow, p, (float) (60 * scale), 0, Colors.alpha(0.14f, LAVA));
        }
        // fire from its jaws once they open, a widening torrent licking ahead towards its prey
        float open = t - (breath + Worldburner.BREATH_OPENS);
        float k = clamp01(open / 6f) * (1 - clamp01((open - (Worldburner.BREATH_CLOSES - Worldburner.BREATH_OPENS - 6)) / 6f));
        if (k > 0 && t < flight + 2) {
            Vec3 mouth = dragon.mouth();
            Vec3 jaws = dragon.jaws();
            Vec3 aim = path.target().subtract(mouth);
            aim = aim.lengthSqr() < 1 ? jaws : aim.normalize().add(jaws.scale(1.5)).normalize();
            double len = Math.min(140 * scale, mouth.distanceTo(path.target()) + 20);
            Vec3[] b = VfxContext.basis(aim);
            for (int i = 0; i < 26; i++) {
                float f = i / 25f;
                double jx = Math.sin(t * 0.7 + i * 1.3) * f * 7 * scale, jy = Math.cos(t * 0.6 + i * 0.9) * f * 7 * scale;
                Vec3 q = mouth.add(aim.scale(f * len)).add(b[0].scale(jx)).add(b[1].scale(jy));
                ctx.billboard(flame, q, (float) ((3 + 30 * f) * scale), i + t * 0.3f, Colors.alpha(k * (1 - f * 0.55f), f < 0.2f ? HOT : f < 0.6f ? FIRE : LAVA));
            }
            ctx.softBeam(glow, mouth, mouth.add(aim.scale(len)), (float) (22 * scale), Colors.alpha(k * 0.6f, FIRE), 0, 1, 0.05f, 0.3f);
        }
    }

    /** When it starts its fire breath: after its roar, timed so its jaws open as it tips into the dive. */
    private int breathStarts() {
        return Math.max(Worldburner.ROAR_LENGTH + 2, Math.round(flight * 0.55f));
    }

    // ------------------------------------------------------------------------------------------------------------
    // the strike
    // ------------------------------------------------------------------------------------------------------------

    private void drawStrike(VfxContext ctx, float ts, float end) {
        Vec3 c = path.target();
        var glow = ctx.additive(VfxTextures.GLOW);
        var flame = ctx.additive(VfxTextures.FLAME);
        var mist = ctx.translucent(VfxTextures.MIST);
        var white = ctx.additive(VfxTextures.WHITE);
        // the fireball
        float k = clamp01(ts / 26f);
        if (k < 1) {
            ctx.billboard(glow, c.add(0, crater * 0.3, 0), crater * (1.5f + 3f * easeOut(k)), 0, Colors.alpha((1 - k) * 0.95f, HOT));
            for (int i = 0; i < 26; i++) {
                RandomSource r = RandomSource.create(i * 977L);
                Vec3 d = new Vec3(r.nextDouble() - 0.5, r.nextDouble() * 0.8 + 0.1, r.nextDouble() - 0.5).normalize();
                Vec3 q = c.add(d.scale(crater * 1.1 * easeOut(k))).add(0, crater * 0.5 * k, 0);
                ctx.billboard(flame, q, crater * (0.5f + 0.4f * k), i, Colors.alpha(1 - k, k < 0.4f ? HOT : FIRE));
            }
        }
        // the column of fire and smoke, its cap spreading
        float rise = easeOut(clamp01(ts / 60f));
        float smoke = clamp01(ts / 6f) * end * (1 - clamp01((ts - SPLIT - 40) / 40f));
        if (smoke > 0) {
            float top = 150 * rise;
            for (int i = 0; i < 26; i++) {
                float u = i / 25f;
                double ang = i * 2.39996 + ts * 0.01;
                float spread = crater * (0.25f + 0.2f * u) + (u > 0.8f ? crater * 0.9f * (u - 0.8f) / 0.2f : 0);
                Vec3 q = c.add(Math.cos(ang) * spread, top * u, Math.sin(ang) * spread);
                int col = u < 0.3f ? Colors.lerpRgb(FIRE, SMOKE, u / 0.3f) : SMOKE;
                ctx.billboard(u < 0.3f ? flame : mist, q, crater * (0.6f + 0.6f * u), i + ts * 0.01f, Colors.alpha(smoke * (u < 0.3f ? 0.8f : 0.75f), col));
            }
            for (int i = 0; i < 14; i++) {
                double ang = i * Math.PI * 2 / 14 + ts * 0.005;
                Vec3 q = c.add(Math.cos(ang) * crater * 1.1 * rise, top, Math.sin(ang) * crater * 1.1 * rise);
                ctx.billboard(mist, q, crater * 1.1f, i, Colors.alpha(smoke * 0.7f, SMOKE));
            }
        }
        // shockwaves
        var shock = ctx.additive(VfxTextures.SHOCKWAVE);
        for (int w = 0; w < 3; w++) {
            float s = (ts - w * 8) / 32f;
            if (s < 0 || s > 1) continue;
            ctx.disc(shock, c.add(0, 0.6 + w * 0.2, 0), X, Z, crater + radius * 1.1f * easeOut(s), w, Colors.alpha((1 - s) * 0.9f, w == 0 ? 0xFFF0D8 : FIRE));
        }
        // the cracks racing out and glowing
        if (heights != null) {
            float front = crater + (radius - crater) * Math.min(1, ts / SPLIT);
            float pulse = (0.75f + 0.25f * (float) Math.sin(ts * 0.5f)) * end;
            List<CrackNetwork.Segment> segs = cracks.segments();
            for (int i = 0; i < segs.size(); i++) {
                var s = segs.get(i);
                if (s.from() > front) continue;
                double kk = Math.min(1, (front - s.from()) / Math.max(1e-3, s.length()));
                Vec3 a = new Vec3(c.x + s.x0(), heights[i][0] + 0.3, c.z + s.z0());
                Vec3 b = a.lerp(new Vec3(c.x + s.x1(), heights[i][1] + 0.3, c.z + s.z1()), kk);
                GrantExecutionVfx.flatLine(ctx, white, a, b, (float) s.width() * 0.8f, Colors.alpha(pulse, FIRE));
                GrantExecutionVfx.flatLine(ctx, glow, a, b, (float) s.width() * 3f, Colors.alpha(pulse * 0.5f, LAVA));
                // lava spat up out of the cracks once they are open
                if (i % 3 == 0 && kk >= 1) {
                    float g = (ts - SPLIT * 0.5f - (i % 7) * 3) / 50f;
                    if (g > 0 && g < 1) {
                        Vec3 vent = new Vec3(c.x + s.x1(), heights[i][1], c.z + s.z1());
                        float h = 40 * (float) Math.sin(g * Math.PI);
                        for (int j = 0; j < 10; j++) {
                            float u = ((j * 0.1f) + ts * 0.05f) % 1f;
                            Vec3 q = vent.add(Math.sin(j + ts * 0.2) * 2 * u, h * u, Math.cos(j * 1.3 + ts * 0.2) * 2 * u);
                            ctx.flame(flame, q, UP, 6 * (1 - u) + 2, 3 * (1 - u) + 1.2f, Colors.alpha((1 - g) * (1 - u * 0.7f), j % 3 == 0 ? HOT : j % 3 == 1 ? FIRE : LAVA));
                        }
                    }
                }
            }
        }
        // embers drifting up over the burning land
        var spark = ctx.additive(VfxTextures.SPARK);
        int n = ctx.segments(90);
        float alive = clamp01(ts / 10f) * end;
        for (int i = 0; i < n; i++) {
            double ang = i * 2.39996;
            float rr = radius * (float) Math.sqrt(((i * 0.618f) % 1f));
            float u = ((i * 0.37f) + ts * 0.012f) % 1f;
            Vec3 q = c.add(Math.cos(ang) * rr + Math.sin(ts * 0.04 + i) * 2, 2 + u * 40, Math.sin(ang) * rr);
            ctx.billboard(spark, q, 0.8f, i, Colors.alpha(alive * (1 - u), i % 3 == 0 ? HOT : FIRE));
        }
    }
}
