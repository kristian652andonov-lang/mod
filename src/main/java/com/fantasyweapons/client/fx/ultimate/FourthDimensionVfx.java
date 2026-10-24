package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.weapons.ultimate.ShufflePlan;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 4TH DIMENSION ASCENSION. A tesseract forms in the air over the target, turning through a direction that does not
 * exist, its edges split into red and cyan; curtains of broken light rise along the chunk borders of the region, the
 * ground's blocks are drawn up into the tesseract, and it spins faster and faster. Then space breaks: the tesseract bursts
 * outward and every chunk inside flashes as it is replaced by another, turned and tipped (the server moves the blocks).
 */
public class FourthDimensionVfx extends Vfx {
    private static final int VOID = 0x9B4DFF, PALE = 0xE2C8FF, RED = 0xFF3050, CYAN = 0x30E8FF, WHITE = 0xFFFFFF;
    private static final int[][] VERTS = new int[16][4];
    private static final List<int[]> EDGES = new ArrayList<>();

    static {
        for (int i = 0; i < 16; i++) for (int k = 0; k < 4; k++) VERTS[i][k] = ((i >> k) & 1) == 1 ? 1 : -1;
        for (int i = 0; i < 16; i++) for (int k = 0; k < 4; k++) if (((i >> k) & 1) == 0) EDGES.add(new int[]{i, i | (1 << k)});
    }

    private final Vec3 center, heart;
    private final float radius;
    private final int delay;
    private final List<ShufflePlan.Move> moves;
    private final List<Vec3[]> border = new ArrayList<>();
    private final double floor, ceiling;
    private final RandomSource rng;
    private BlockDebrisVfx debris;

    public FourthDimensionVfx(Vec3 center, float radius, int delay, ShufflePlan plan, int minY, long seed) {
        super(delay + 90);
        this.center = center;
        this.radius = radius;
        this.delay = delay;
        this.moves = plan.moves();
        this.heart = center.add(0, radius * 0.95, 0);
        this.floor = Math.max(minY, center.y - 50);
        this.ceiling = center.y + radius * 2.2;
        this.rng = RandomSource.create(seed);
        Set<Long> in = new HashSet<>();
        for (var m : moves) in.add(m.dest().toLong());
        for (var m : moves) {
            ChunkPos p = m.dest();
            double x0 = p.getMinBlockX(), z0 = p.getMinBlockZ(), x1 = x0 + 16, z1 = z0 + 16;
            if (!in.contains(ChunkPos.asLong(p.x - 1, p.z))) border.add(new Vec3[]{new Vec3(x0, 0, z0), new Vec3(x0, 0, z1)});
            if (!in.contains(ChunkPos.asLong(p.x + 1, p.z))) border.add(new Vec3[]{new Vec3(x1, 0, z1), new Vec3(x1, 0, z0)});
            if (!in.contains(ChunkPos.asLong(p.x, p.z - 1))) border.add(new Vec3[]{new Vec3(x1, 0, z0), new Vec3(x0, 0, z0)});
            if (!in.contains(ChunkPos.asLong(p.x, p.z + 1))) border.add(new Vec3[]{new Vec3(x0, 0, z1), new Vec3(x1, 0, z1)});
        }
    }

    public FourthDimensionVfx lifting(BlockDebrisVfx debris) {
        this.debris = debris;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (debris == null) return;
        if (age >= delay) {
            debris.close();
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null || moves.isEmpty()) return;
        // the ground's blocks are drawn up into the tesseract
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int n = Math.round((4 + 14 * age / (float) delay) * com.fantasyweapons.config.ClientConfig.quality().density());
        for (int i = 0; i < n; i++) {
            ChunkPos p = moves.get(rng.nextInt(moves.size())).dest();
            int x = p.getMinBlockX() + rng.nextInt(16), z = p.getMinBlockZ() + rng.nextInt(16);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            var state = level.getBlockState(m.set(x, y, z));
            if (state.isAir()) continue;
            debris.suck(state, new Vec3(x + 0.5, y + 1, z + 0.5), heart, rng.nextFloat() < 0.15f ? 2f : 1f, 0.4f, 50 + rng.nextInt(30));
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float end = 1 - clamp01((t - delay - 40) / 50f);
        if (end <= 0) return;
        if (ctx.cam.distanceTo(center) < radius * 4) com.fantasyweapons.client.fx.SkyDarkness.request(0.7f * end * clamp01(t / 15f));
        ctx.farClamp = VfxContext.viewReach() * 0.86;
        drawCurtains(ctx, t, end);
        if (t < delay) drawGlitches(ctx, t);
        drawTesseract(ctx, t);
        if (t >= delay) drawBreak(ctx, t - delay);
        ctx.farClamp = 0;
    }

    /** The 4D cube turned through two planes that reach into the fourth direction, and seen in perspective from it. */
    private void drawTesseract(VfxContext ctx, float t) {
        float grow, alpha;
        if (t < delay) {
            grow = easeOut(clamp01(t / 30f));
            alpha = grow;
        } else {
            float k = (t - delay) / 22f;
            if (k >= 1) return;
            grow = 1 + 2.2f * easeOut(k);
            alpha = 1 - k;
        }
        float spin = t < delay ? (float) (t * 0.02 + Math.pow(t / delay, 3) * delay * 0.06) : (float) (delay * 0.08 + (t - delay) * 0.15);
        double a = spin, b = spin * 0.73, c = t * 0.006;
        float size = radius * 0.55f * grow;
        Vec3[] p = new Vec3[16];
        for (int i = 0; i < 16; i++) {
            double x = VERTS[i][0], y = VERTS[i][1], z = VERTS[i][2], w = VERTS[i][3];
            double x1 = x * Math.cos(a) - w * Math.sin(a), w1 = x * Math.sin(a) + w * Math.cos(a);
            double z1 = z * Math.cos(b) - w1 * Math.sin(b), w2 = z * Math.sin(b) + w1 * Math.cos(b);
            double x2 = x1 * Math.cos(c) - z1 * Math.sin(c), z2 = x1 * Math.sin(c) + z1 * Math.cos(c);
            double k = 2.6 / (2.6 - w2);
            p[i] = heart.add(x2 * k * size * 0.5, y * k * size * 0.5, z2 * k * size * 0.5);
        }
        var white = ctx.additive(VfxTextures.WHITE);
        var glow = ctx.additive(VfxTextures.GLOW);
        Vec3 split = ctx.camLeft.scale(Math.max(0.6, size * 0.02));
        float flick = 0.85f + 0.15f * (float) Math.sin(t * 2.3);
        for (int[] e : EDGES) {
            Vec3 u = p[e[0]], v = p[e[1]];
            float wd = Math.max(0.5f, size * 0.012f);
            ctx.beam(white, u.add(split), v.add(split), wd, Colors.alpha(alpha * 0.55f, RED), 0, 1);
            ctx.beam(white, u.subtract(split), v.subtract(split), wd, Colors.alpha(alpha * 0.55f, CYAN), 0, 1);
            ctx.beam(white, u, v, wd * 0.8f, Colors.alpha(alpha * flick, WHITE), 0, 1);
            ctx.beam(glow, u, v, wd * 6, Colors.alpha(alpha * 0.25f, VOID), 0, 1);
        }
        for (Vec3 v : p) ctx.billboard(glow, v, size * 0.06f + 1.5f, 0, Colors.alpha(alpha, PALE));
        ctx.billboard(glow, heart, size * 1.4f, 0, Colors.alpha(alpha * 0.35f, VOID));
        ctx.billboard(ctx.additive(VfxTextures.FLASH), heart, size * 0.35f, t * 0.05f, Colors.alpha(alpha * 0.8f, WHITE));
    }

    /** Curtains of broken light up the region's chunk borders, scanlines climbing them. */
    private void drawCurtains(VfxContext ctx, float t, float end) {
        float a = clamp01(t / 25f) * end * (t > delay ? 0.6f + 0.4f * (1 - clamp01((t - delay) / 10f)) : 1);
        if (a <= 0) return;
        var noise = ctx.additive(VfxTextures.NOISE);
        var white = ctx.additive(VfxTextures.WHITE);
        for (int i = 0; i < border.size(); i++) {
            Vec3[] s = border.get(i);
            Vec3 b0 = new Vec3(s[0].x, floor, s[0].z), b1 = new Vec3(s[1].x, floor, s[1].z);
            Vec3 t0 = new Vec3(s[0].x, ceiling, s[0].z), t1 = new Vec3(s[1].x, ceiling, s[1].z);
            float glitch = (Math.floorMod(i * 7 + (int) (t * 0.5f), 11) == 0) ? 1.6f : 1;
            int lo = Colors.alpha(a * 0.32f * glitch, VOID), hi = Colors.alpha(0, VOID);
            float scroll = -t * 0.03f;
            ctx.quad(noise, b0, b1, t1, t0, i * 0.3f, scroll, i * 0.3f + 0.3f, scroll + 2.5f, lo, lo, hi, hi);
            // the seam itself at the ground
            Vec3 g0 = new Vec3(s[0].x, center.y + 0.3, s[0].z), g1 = new Vec3(s[1].x, center.y + 0.3, s[1].z);
            ctx.beam(white, g0, g1, 0.6f, Colors.alpha(a * 0.8f, i % 2 == 0 ? CYAN : RED), 0, 1);
        }
    }

    /** Chunks flickering out of true: wire boxes split red and cyan, here and gone. */
    private void drawGlitches(VfxContext ctx, float t) {
        var white = ctx.additive(VfxTextures.WHITE);
        int frame = (int) (t / 2);
        float urgency = clamp01(t / delay);
        int n = 2 + (int) (urgency * 8);
        for (int k = 0; k < n && !moves.isEmpty(); k++) {
            RandomSource r = RandomSource.create(frame * 131L + k * 977L);
            ChunkPos p = moves.get(r.nextInt(moves.size())).dest();
            double y0 = center.y - 10 + r.nextInt(30), y1 = y0 + 16 + r.nextInt(48);
            double off = (r.nextDouble() - 0.5) * 3;
            box(ctx, white, p.getMinBlockX() + off, y0, p.getMinBlockZ(), y1, Colors.alpha(0.7f, r.nextBoolean() ? CYAN : RED));
        }
    }

    private void box(VfxContext ctx, VertexConsumer vc, double x0, double y0, double z0, double y1, int col) {
        double x1 = x0 + 16, z1 = z0 + 16;
        Vec3[] lo = {new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1)};
        Vec3[] hi = {new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1)};
        for (int i = 0; i < 4; i++) {
            ctx.beam(vc, lo[i], lo[(i + 1) % 4], 0.5f, col, 0, 1);
            ctx.beam(vc, hi[i], hi[(i + 1) % 4], 0.5f, col, 0, 1);
            ctx.beam(vc, lo[i], hi[i], 0.5f, col, 0, 1);
        }
    }

    /** Space breaking: a white burst, shock rings, and every chunk flashing as it is swapped. */
    private void drawBreak(VfxContext ctx, float tb) {
        var glow = ctx.additive(VfxTextures.GLOW);
        var shock = ctx.additive(VfxTextures.SHOCKWAVE);
        var white = ctx.additive(VfxTextures.WHITE);
        float k = clamp01(tb / 14f);
        if (k < 1) ctx.billboard(glow, heart, radius * (1 + 3 * k), 0, Colors.alpha(1 - k, WHITE));
        Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
        for (int w = 0; w < 3; w++) {
            float s = (tb - w * 5) / 26f;
            if (s < 0 || s > 1) continue;
            ctx.disc(shock, center.add(0, 1 + w * 12, 0), X, Z, radius * (0.4f + 1.6f * easeOut(s)), w, Colors.alpha((1 - s) * 0.85f, w == 1 ? CYAN : w == 2 ? RED : PALE));
        }
        for (var m : moves) {
            float f = (tb - m.lag()) / 12f;
            if (f < 0 || f > 1) continue;
            ChunkPos p = m.dest();
            box(ctx, white, p.getMinBlockX(), center.y - 30, p.getMinBlockZ(), center.y + 90, Colors.alpha((1 - f), f < 0.3f ? WHITE : VOID));
            ctx.billboard(glow, new Vec3(p.getMiddleBlockX(), center.y + 20, p.getMiddleBlockZ()), 40 * (1 - f) + 8, 0, Colors.alpha((1 - f) * 0.5f, PALE));
        }
    }
}
