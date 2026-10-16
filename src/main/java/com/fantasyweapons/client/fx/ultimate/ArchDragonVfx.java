package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.weapons.ultimate.CrackNetwork;
import com.fantasyweapons.weapons.ultimate.DragonPath;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * THE ARCH DRAGON OF HELL (a placeholder until its real model arrives). A rift of hellfire tears open in the ground behind
 * the caster and the dragon bursts up out of it: a serpent of black scales 256 blocks long, lava pulsing through the
 * seams between them, spines down its back, wings of burning membrane, horns, glowing eyes and a jaw full of fire. It
 * climbs, sweeps round high over the land, rears up above the target and dives into it breathing fire; the ground
 * explodes into a mushroom of fire and smoke, splits open in glowing cracks for the whole radius and spits lava.
 */
public class ArchDragonVfx extends Vfx {
    private static final int SCALE_TOP = 0x2E1C1E, SCALE_BELLY = 0x7A3A22, SPINE = 0x160C0C, HORN = 0x3A2C22, HORN_TIP = 0x9A8A6A;
    private static final int MEMBRANE = 0x5A1612, TOOTH = 0xE8DCC8, LAVA = 0xFF5A1A, FIRE = 0xFFA040, HOT = 0xFFE6A0, SMOKE = 0x2A2020, RUNE = 0xFF4A2A;
    private static final float SPACING = 3f;
    private static final int SIDES = 12, SPLIT = 40;
    private static final Vec3 LIGHT = new Vec3(0.35, 1, 0.25).normalize();
    private static final Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1), UP = new Vec3(0, 1, 0);

    private final DragonPath path;
    private final float length, radius, crater, body;
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
        this.body = Math.max(3, length / 256f * 10f);
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
        if (t < plunged) drawDragon(ctx, t);
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

    private float profile(float u) {
        if (u < 0.08f) return 0.55f + 0.45f * (u / 0.08f);
        if (u < 0.35f) return 1;
        return 1 - 0.9f * (float) Math.pow((u - 0.35f) / 0.65f, 1.3);
    }

    /** The body: rings of scales along the path behind the head, then the wings and the head itself. */
    private void drawDragon(VfxContext ctx, float t) {
        double head = path.head(t);
        float headLen = body * 2.4f;
        double total = path.total();
        int n = (int) ((length - headLen) / SPACING);
        Vec3[] centers = new Vec3[n + 1];
        Vec3[][] frames = new Vec3[n + 1][];
        float[] radii = new float[n + 1];
        double[] ss = new double[n + 1];
        Vec3[] hf = path.frame(head, null);
        Vec3 prevUp = hf[2];
        for (int i = 0; i <= n; i++) {
            double s = head - headLen * 0.85 - i * SPACING;
            ss[i] = s;
            Vec3[] f = path.frame(s, prevUp);
            prevUp = f[2];
            frames[i] = f;
            float u = (float) ((head - s) / length);
            radii[i] = body * profile(u);
            double free = clamp01((float) (s / 30)) * clamp01((float) ((total - s) / 30)) * clamp01((float) ((head - s) / 50));
            double sway = Math.sin(s * 0.045 - t * 0.35) * body * 0.7 * free;
            centers[i] = path.at(s).add(f[1].scale(sway));
        }
        var scales = ctx.solid(VfxTextures.DRAGON_SCALE);
        var veins = ctx.additive(VfxTextures.DRAGON_VEINS);
        var flame = ctx.additive(VfxTextures.FLAME);
        var mist = ctx.translucent(VfxTextures.MIST);
        var glow = ctx.additive(VfxTextures.GLOW);
        for (int i = 0; i < n; i++) {
            // below the rift it is still coming up the shaft; past the strike it has gone into the ground
            if (ss[i] < -110 || ss[i + 1] > total + 30) continue;
            band(ctx, scales, veins, centers[i], frames[i], radii[i], centers[i + 1], frames[i + 1], radii[i + 1], ss[i], t);
            if (i % 2 == 0 && i < n * 0.92) spine(ctx, scales, centers[i], frames[i], radii[i], (float) i / n);
            if (i % 3 == 0) {
                Vec3 back = centers[i].add(frames[i][2].scale(radii[i] * 1.1));
                float fl = 0.6f + 0.4f * (float) Math.sin(t * 0.9 + i);
                ctx.billboard(flame, back, radii[i] * 1.3f * fl, i + t * 0.1f, Colors.alpha(0.85f, i % 2 == 0 ? FIRE : LAVA));
            }
            if (i % 4 == 0 && i > n * 0.6) {
                // smoke rolling off the tail
                Vec3 p = centers[i].subtract(frames[i][0].scale(6)).add(0, 3, 0);
                ctx.billboard(mist, p, radii[i] * 3 + 6, i * 0.7f + t * 0.01f, Colors.alpha(0.35f, SMOKE));
            }
        }
        // a glow around it so it reads from far away
        for (int i = 0; i < n; i += 6) {
            if (ss[i] < 0 || ss[i] > total) continue;
            ctx.billboard(glow, centers[i], radii[i] * 4.5f, 0, Colors.alpha(0.18f, LAVA));
        }
        int w = Math.min(n, Math.round(0.12f * length / SPACING));
        if (ss[w] > 2 && ss[w] < total) {
            wing(ctx, centers[w], frames[w], radii[w], t, 1);
            wing(ctx, centers[w], frames[w], radii[w], t, -1);
        }
        if (head > -headLen && head < total + headLen) drawHead(ctx, path.at(head), hf, t);
    }

    /** One band of the body between two rings: shaded scales, and lava glowing in their seams in pulses down the body. */
    private void band(VfxContext ctx, VertexConsumer scales, VertexConsumer veins, Vec3 c0, Vec3[] f0, float r0, Vec3 c1, Vec3[] f1, float r1, double s, float t) {
        float v0 = (float) (s * 0.09), v1 = (float) ((s - SPACING) * 0.09);
        double wave = Math.max(0, Math.sin(s * 0.06 + t * 0.6));
        float pulse = 0.15f + 0.85f * (float) (wave * wave);
        for (int k = 0; k < SIDES; k++) {
            double a0 = k * Math.PI * 2 / SIDES, a1 = (k + 1) * Math.PI * 2 / SIDES;
            Vec3 p00 = ring(c0, f0, r0, a0), p01 = ring(c0, f0, r0, a1), p11 = ring(c1, f1, r1, a1), p10 = ring(c1, f1, r1, a0);
            double am = (a0 + a1) / 2;
            Vec3 nrm = f0[1].scale(Math.cos(am)).add(f0[2].scale(Math.sin(am)));
            float shade = 0.3f + 0.7f * (float) Math.max(0, nrm.dot(LIGHT));
            float belly = clamp01((float) (-Math.sin(am) - 0.2) * 1.6f);
            int col = Colors.argb(255, Colors.scale(Colors.lerpRgb(SCALE_TOP, SCALE_BELLY, belly), 0.45f + shade * 0.75f));
            float u0 = k / (float) SIDES * 3, u1 = (k + 1) / (float) SIDES * 3;
            ctx.quad(scales, p00, p01, p11, p10, u0, v0, u1, v1, col, col, col, col);
            int vc = Colors.alpha(pulse * (0.35f + 0.45f * belly), LAVA);
            ctx.quad(veins, p00, p01, p11, p10, u0, v0, u1, v1, vc, vc, vc, vc);
        }
    }

    private static Vec3 ring(Vec3 c, Vec3[] f, float r, double a) {
        return c.add(f[1].scale(Math.cos(a) * r)).add(f[2].scale(Math.sin(a) * r * 0.88));
    }

    /** A spine down the middle of its back, swept toward the tail. */
    private void spine(VfxContext ctx, VertexConsumer vc, Vec3 c, Vec3[] f, float r, float u) {
        Vec3 base = c.add(f[2].scale(r * 0.8));
        float h = r * 1.1f * (1 - u * 0.7f);
        Vec3 apex = base.add(f[2].scale(h)).subtract(f[0].scale(h * 0.7));
        Vec3 fw = f[0].scale(r * 0.32), sd = f[1].scale(r * 0.16);
        Vec3[] q = {base.add(fw), base.add(sd), base.subtract(fw), base.subtract(sd)};
        for (int k = 0; k < 4; k++) {
            Vec3 a = q[k], b = q[(k + 1) % 4];
            Vec3 nrm = b.subtract(a).cross(apex.subtract(a)).normalize();
            int col = Colors.argb(255, Colors.scale(SPINE, 0.6f + 0.8f * (float) Math.max(0, nrm.dot(LIGHT))));
            ctx.quad(vc, a, b, apex, apex, 0, 0, 0.2f, 0.2f, col, col, col, col);
        }
    }

    /** A wing of burning membrane stretched over four finger bones, beating slowly. */
    private void wing(VfxContext ctx, Vec3 c, Vec3[] f, float r, float t, int side) {
        float span = body * 8.5f;
        double beat = Math.sin(t * 0.28);
        double angle = 0.2 + 0.6 * beat;
        Vec3 out = f[1].scale(side);
        Vec3 shoulder = c.add(out.scale(r * 0.75)).add(f[2].scale(r * 0.45));
        Vec3 arm = out.scale(Math.cos(angle)).add(f[2].scale(Math.sin(angle))).normalize();
        Vec3 wrist = shoulder.add(arm.scale(span * 0.48)).subtract(f[0].scale(span * 0.06));
        Vec3[] tips = new Vec3[4];
        for (int j = 0; j < 4; j++) {
            double b = 0.2 + j * 0.42;
            Vec3 dir = arm.scale(Math.cos(b)).subtract(f[0].scale(Math.sin(b))).add(f[2].scale(-0.12 * beat)).normalize();
            tips[j] = wrist.add(dir.scale(span * 0.55 * (1 - j * 0.14)));
        }
        Vec3 root = c.subtract(f[0].scale(span * 0.42)).add(out.scale(r * 0.6));
        var membrane = ctx.translucent(VfxTextures.NOISE);
        var bone = ctx.solid(VfxTextures.DRAGON_SCALE);
        Vec3 nrm = arm.cross(f[0]).normalize();
        float shade = 0.45f + 0.55f * (float) Math.abs(nrm.dot(LIGHT));
        int mc = Colors.alpha(0.9f, Colors.scale(MEMBRANE, 0.6f + shade * 0.7f));
        int edge = Colors.alpha(0.75f, Colors.scale(MEMBRANE, 1.6f));
        for (int j = 0; j < 3; j++) ctx.quad(membrane, wrist, tips[j], tips[j + 1], wrist, 0, 0, 1, 1, mc, edge, edge, mc);
        ctx.quad(membrane, shoulder, wrist, tips[3], root, 0, 0, 1, 1, mc, mc, edge, mc);
        tube(ctx, bone, shoulder, wrist, r * 0.32f, r * 0.2f, SPINE);
        for (Vec3 tip : tips) tube(ctx, bone, wrist, tip, r * 0.16f, r * 0.04f, SPINE);
        // the trailing edge smoulders
        var glow = ctx.additive(VfxTextures.GLOW);
        for (int j = 0; j < 3; j++) {
            Vec3 mid = tips[j].lerp(tips[j + 1], 0.5);
            ctx.billboard(glow, mid, r * 1.4f, 0, Colors.alpha(0.5f, LAVA));
        }
        ctx.billboard(ctx.additive(VfxTextures.FLAME), tips[0], r * 1.2f, t * 0.2f, Colors.alpha(0.8f, FIRE));
    }

    /** A tapering four-sided tube, flat shaded. */
    private void tube(VfxContext ctx, VertexConsumer vc, Vec3 a, Vec3 b, float ra, float rb, int color) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) return;
        Vec3[] bs = VfxContext.basis(d.normalize());
        for (int k = 0; k < 4; k++) {
            double a0 = k * Math.PI / 2 + Math.PI / 4, a1 = a0 + Math.PI / 2;
            Vec3 o0 = bs[0].scale(Math.cos(a0)).add(bs[1].scale(Math.sin(a0))), o1 = bs[0].scale(Math.cos(a1)).add(bs[1].scale(Math.sin(a1)));
            Vec3 nrm = o0.add(o1).normalize();
            int col = Colors.argb(255, Colors.scale(color, 0.6f + 0.8f * (float) Math.max(0, nrm.dot(LIGHT))));
            ctx.quad(vc, a.add(o0.scale(ra)), a.add(o1.scale(ra)), b.add(o1.scale(rb)), b.add(o0.scale(rb)), 0, 0, 0.3f, 1, col, col, col, col);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the head
    // ------------------------------------------------------------------------------------------------------------

    private static final double[][] SKULL = {{-1, 0}, {-0.92, 0.55}, {-0.45, 1}, {0.45, 1}, {0.92, 0.55}, {1, 0}};
    private static final double[][] JAW = {{1, 0}, {0.7, -0.6}, {0, -0.85}, {-0.7, -0.6}, {-1, 0}};

    private void drawHead(VfxContext ctx, Vec3 tip, Vec3[] f, float t) {
        float L = body * 2.4f;
        Vec3 fwd = f[0], right = f[1], up = f[2];
        Vec3 back = tip.subtract(fwd.scale(L));
        float p = path.progress(t);
        double jaw = 0.18 + 0.12 * Math.sin(t * 0.2) + (p > 0.7f ? 0.45 * clamp01((p - 0.7f) / 0.15f) : 0);
        Vec3 jf = fwd.scale(Math.cos(jaw)).subtract(up.scale(Math.sin(jaw)));
        Vec3 ju = up.scale(Math.cos(jaw)).add(fwd.scale(Math.sin(jaw)));
        var scales = ctx.solid(VfxTextures.DRAGON_SCALE);
        int rows = 6;
        Vec3[][] skull = new Vec3[rows + 1][SKULL.length];
        Vec3[][] jawRings = new Vec3[rows + 1][JAW.length];
        for (int i = 0; i <= rows; i++) {
            double x = i / (double) rows;
            double w = body * (1.15 - 0.62 * x), h = body * (0.95 - 0.62 * x) + body * 0.18 * Math.exp(-Math.pow((x - 0.32) / 0.1, 2));
            Vec3 c = back.add(fwd.scale(L * x));
            for (int k = 0; k < SKULL.length; k++) skull[i][k] = c.add(right.scale(SKULL[k][0] * w)).add(up.scale(SKULL[k][1] * h));
            Vec3 cj = back.add(jf.scale(L * 0.92 * x));
            double hj = body * (0.5 - 0.3 * x);
            for (int k = 0; k < JAW.length; k++) jawRings[i][k] = cj.add(right.scale(JAW[k][0] * w * 0.9)).add(ju.scale(JAW[k][1] * hj));
        }
        loft(ctx, scales, skull, SCALE_TOP, true);
        loft(ctx, scales, jawRings, SCALE_BELLY, true);
        // teeth along both jaws
        var teeth = ctx.solid(VfxTextures.WHITE);
        for (int i = 0; i < 6; i++) {
            double x = 0.38 + i * 0.1;
            for (int sd = -1; sd <= 1; sd += 2) {
                double w = body * (1.15 - 0.62 * x) * 0.92;
                Vec3 top = back.add(fwd.scale(L * x)).add(right.scale(sd * w));
                tooth(ctx, teeth, top, up.scale(-1), fwd, body * 0.5f);
                Vec3 bot = back.add(jf.scale(L * 0.92 * x)).add(right.scale(sd * w * 0.92));
                tooth(ctx, teeth, bot, ju, fwd, body * 0.4f);
            }
        }
        // horns sweeping back from the crown, and spikes at the cheeks
        var bone = ctx.solid(VfxTextures.DRAGON_SCALE);
        for (int sd = -1; sd <= 1; sd += 2) {
            Vec3 at = back.add(fwd.scale(L * 0.08)).add(up.scale(body * 0.82)).add(right.scale(sd * body * 0.62));
            Vec3 dir = fwd.scale(-0.75).add(up.scale(0.55)).add(right.scale(sd * 0.3)).normalize();
            float rad = body * 0.32f, seg = body * 0.95f;
            for (int k = 0; k < 3; k++) {
                Vec3 next = at.add(dir.scale(seg));
                float r1 = rad * (1 - (k + 1) / 3.2f);
                tube(ctx, bone, at, next, rad, Math.max(0.05f, r1), Colors.lerpRgb(HORN, HORN_TIP, k / 2f));
                at = next;
                rad = r1;
                dir = dir.add(fwd.scale(-0.25)).add(up.scale(-0.12)).normalize();
            }
            Vec3 cheek = back.add(fwd.scale(L * 0.18)).add(right.scale(sd * body * 1.05));
            tube(ctx, bone, cheek, cheek.add(right.scale(sd * body * 0.5)).subtract(fwd.scale(body * 0.9)), body * 0.18f, 0.05f, HORN);
        }
        // eyes, the fire in its mouth, and the fire it breathes as it dives
        var glow = ctx.additive(VfxTextures.GLOW);
        var flash = ctx.additive(VfxTextures.FLASH);
        var streak = ctx.additive(VfxTextures.STREAK);
        for (int sd = -1; sd <= 1; sd += 2) {
            double w = body * (1.15 - 0.62 * 0.32);
            Vec3 eye = back.add(fwd.scale(L * 0.32)).add(right.scale(sd * w * 0.9)).add(up.scale(body * 0.55));
            ctx.billboard(glow, eye, body * 0.9f, 0, Colors.alpha(0.95f, HOT));
            ctx.billboard(flash, eye, body * 0.4f, t * 0.1f, Colors.alpha(1, 0xFFFFFF));
            ctx.streak(streak, eye.subtract(fwd.scale(body * 1.5)), fwd, body * 3f, body * 0.3f, Colors.alpha(0.7f, FIRE));
        }
        Vec3 mouth = back.add(fwd.scale(L * 0.55)).subtract(up.scale(body * 0.1));
        ctx.billboard(glow, mouth, body * 2.2f, 0, Colors.alpha(0.85f, FIRE));
        ctx.billboard(glow, back.add(fwd.scale(L * 0.5)), body * 6, 0, Colors.alpha(0.3f, LAVA));
        if (p > 0.72f && p < 1) {
            float k = clamp01((p - 0.72f) / 0.08f);
            float len = 75 * k;
            Vec3 from = tip.subtract(up.scale(body * 0.2));
            var flame = ctx.additive(VfxTextures.FLAME);
            for (int i = 0; i < 28; i++) {
                float u = i / 27f;
                double jx = Math.sin(i * 2.3 + t * 0.9) * u * body * 1.2, jy = Math.cos(i * 1.7 + t * 0.8) * u * body * 1.2;
                Vec3 q = from.add(fwd.scale(u * len)).add(right.scale(jx)).add(up.scale(jy));
                ctx.billboard(flame, q, body * (0.7f + 3.2f * u), i + t * 0.3f, Colors.alpha(k * (1 - u * 0.6f), u < 0.25f ? HOT : u < 0.6f ? FIRE : LAVA));
            }
            ctx.softBeam(glow, from, from.add(fwd.scale(len)), body * 2.5f, Colors.alpha(k * 0.7f, FIRE), 0, 1, 0.05f, 0.3f);
        }
    }

    /** Rings of points joined into a shaded skin; {@code capped} closes the front with a fan. */
    private void loft(VfxContext ctx, VertexConsumer vc, Vec3[][] rings, int color, boolean capped) {
        int m = rings[0].length;
        for (int i = 0; i < rings.length - 1; i++) {
            for (int k = 0; k < m; k++) {
                int k1 = (k + 1) % m;
                Vec3 a = rings[i][k], b = rings[i][k1], c = rings[i + 1][k1], d = rings[i + 1][k];
                Vec3 nrm = b.subtract(a).cross(d.subtract(a));
                if (nrm.lengthSqr() < 1e-8) nrm = c.subtract(b).cross(d.subtract(b));
                nrm = nrm.normalize();
                float shade = 0.35f + 0.65f * (float) Math.abs(nrm.dot(LIGHT));
                int col = Colors.argb(255, Colors.scale(color, 0.6f + shade * 0.8f));
                ctx.quad(vc, a, b, c, d, k / (float) m, i * 0.3f, (k + 1) / (float) m, (i + 1) * 0.3f, col, col, col, col);
            }
        }
        if (!capped) return;
        Vec3[] last = rings[rings.length - 1];
        Vec3 mid = Vec3.ZERO;
        for (Vec3 v : last) mid = mid.add(v);
        mid = mid.scale(1.0 / last.length);
        int col = Colors.argb(255, Colors.scale(color, 0.9f));
        for (int k = 0; k < m; k++) ctx.quad(vc, last[k], last[(k + 1) % m], mid, mid, 0, 0, 0.2f, 0.2f, col, col, col, col);
    }

    private void tooth(VfxContext ctx, VertexConsumer vc, Vec3 root, Vec3 dir, Vec3 fwd, float len) {
        Vec3 side = dir.cross(fwd).normalize().scale(len * 0.18);
        Vec3 f = fwd.scale(len * 0.18);
        Vec3 apex = root.add(dir.scale(len));
        Vec3[] q = {root.add(f), root.add(side), root.subtract(f), root.subtract(side)};
        for (int k = 0; k < 4; k++) {
            int col = Colors.argb(255, Colors.scale(TOOTH, 0.7f + 0.15f * k));
            ctx.quad(vc, q[k], q[(k + 1) % 4], apex, apex, 0, 0, 1, 1, col, col, col, col);
        }
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
