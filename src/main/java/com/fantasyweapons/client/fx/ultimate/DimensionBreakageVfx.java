package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.fx.SkyDarkness;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * DIMENSION BREAKAGE. The sky goes dark and the figures of the fourth dimension come out of it - tesseracts, 16-cells,
 * 24-cells and 5-cells, turning through directions that do not exist, their edges split red and cyan - and wheel round
 * a vast tesseract forming over the target; curtains of broken light rise round the whole radius, the chunks inside
 * flicker out of true and the ground's blocks are drawn up into the tesseract. Then space breaks, and the land folds
 * into the cube (the server moves the blocks); the tesseract hangs in its middle, its light pouring out through the gaps
 * between the cubes of land, turning faster and burning brighter for half a minute. At the last everything is drawn into
 * it, it shrinks to a point - and explodes: a sphere of white and violet light swells out over the whole radius, its rim
 * split red and cyan, and when it fades nothing is left.
 */
public class DimensionBreakageVfx extends Vfx {
    private static final int VOID = Polytopes.VOID, PALE = Polytopes.PALE, RED = Polytopes.RED, CYAN = Polytopes.CYAN, WHITE = Polytopes.WHITE;
    private static final Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
    private static final Polytopes.Figure[] KINDS = {Polytopes.TESSERACT, Polytopes.SIXTEEN_CELL, Polytopes.TWENTY_FOUR_CELL, Polytopes.FIVE_CELL};
    private static final int FIGURES = 12, CURTAIN = 120, AFTER = 80;

    private final Vec3 center, low, high, heart;
    private final float radius, size;
    private final int delay, hold;
    private final float[] figAng, figHeight, figSize, figSpin, figRadius;
    private final RandomSource rng;
    private BlockDebrisVfx debris;

    public DimensionBreakageVfx(Vec3 center, float radius, int delay, int hold, Vec3 low, Vec3 high, long seed) {
        super(delay + hold + AFTER);
        this.center = center;
        this.radius = radius;
        this.delay = delay;
        this.hold = hold;
        this.low = low;
        this.high = high;
        this.heart = low.add(high).scale(0.5);
        this.size = (float) (high.x - low.x) * 0.62f;
        this.rng = RandomSource.create(seed);
        RandomSource r = RandomSource.create(seed ^ 0x4DL);
        figAng = new float[FIGURES];
        figHeight = new float[FIGURES];
        figSize = new float[FIGURES];
        figSpin = new float[FIGURES];
        figRadius = new float[FIGURES];
        for (int i = 0; i < FIGURES; i++) {
            figAng[i] = (float) (i * Math.PI * 2 / FIGURES + r.nextFloat() * 0.4f);
            figHeight[i] = -60 + r.nextFloat() * 220;
            figSize[i] = 36 + r.nextFloat() * 60;
            figSpin[i] = 0.6f + r.nextFloat() * 0.9f;
            figRadius[i] = radius * (0.45f + r.nextFloat() * 0.35f);
        }
    }

    public DimensionBreakageVfx lifting(BlockDebrisVfx debris) {
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
        if (level == null) return;
        // the ground's blocks are drawn up into the tesseract
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int n = Math.round((6 + 22 * age / (float) delay) * com.fantasyweapons.config.ClientConfig.quality().density());
        for (int i = 0; i < n; i++) {
            double an = rng.nextDouble() * Math.PI * 2, d = Math.sqrt(rng.nextDouble()) * radius;
            int x = (int) Math.floor(center.x + Math.cos(an) * d), z = (int) Math.floor(center.z + Math.sin(an) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            var state = level.getBlockState(m.set(x, y, z));
            if (state.isAir()) continue;
            debris.suck(state, new Vec3(x + 0.5, y + 1, z + 0.5), heart, rng.nextFloat() < 0.2f ? 2.5f : 1.2f, 0.5f, 60 + rng.nextInt(40));
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        int boom = delay + hold;
        float end = clamp01((t - boom - 30) / (AFTER - 30f));
        if (end >= 1) return;
        if (ctx.cam.distanceTo(center) < radius * 4) {
            float dark = 0.92f * easeInOut(clamp01(t / 40f)) * (1 - end);
            // the moment it explodes the sky goes white instead
            if (t > boom) dark *= clamp01((t - boom - 6) / 20f);
            SkyDarkness.request(dark);
        }
        ctx.farClamp = VfxContext.viewReach() * 0.86;
        drawCurtains(ctx, t, end);
        if (t < delay) drawGlitches(ctx, t);
        drawFigures(ctx, t);
        drawTesseract(ctx, t);
        if (t >= delay && t < boom + 4) drawLight(ctx, t);
        if (t >= delay && t < delay + 40) drawBreak(ctx, t - delay);
        ctx.farClamp = 0;
        if (t >= boom) drawExplosion(ctx, t - boom);
    }

    /** How hard it is straining toward the end (0 when space breaks, 1 when it explodes). */
    private float strain(float t) {
        return clamp01((t - delay) / hold);
    }

    /** At the last, everything is drawn into it: 0 until the last three seconds, then to 1 at the end. */
    private float drawIn(float t) {
        return easeIn(clamp01((t - (delay + hold - 60)) / 60f));
    }

    // ------------------------------------------------------------------------------------------------------------
    // the tesseract and the figures
    // ------------------------------------------------------------------------------------------------------------

    private void drawTesseract(VfxContext ctx, float t) {
        int boom = delay + hold;
        if (t > boom + 2) return;
        float grow = easeOut(clamp01(t / 50f));
        float s = strain(t), in = drawIn(t);
        float pulse = 1 + 0.06f * (float) Math.sin(t * (0.2f + 0.6f * s));
        double sz = size * grow * pulse * (1 - 0.95 * in);
        double spin = t * 0.015 + Math.pow(t / (double) boom, 3) * boom * 0.08;
        Polytopes.draw(ctx, Polytopes.TESSERACT, heart, sz, spin, spin * 0.73, t * 0.005, grow, t);
        // a tesseract inside it, turning the other way
        Polytopes.draw(ctx, Polytopes.TESSERACT, heart, sz * 0.45, -spin * 1.3, spin * 0.5, -t * 0.008, grow * 0.8f, t);
        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.billboard(glow, heart, (float) (sz * 1.5 + 20), 0, Colors.alpha(grow * (0.3f + 0.3f * s), VOID));
        float core = 0.4f + 0.6f * s + 2 * in;
        ctx.billboard(ctx.additive(VfxTextures.FLASH), heart, (float) (size * 0.3 * core), t * 0.05f, Colors.alpha(Math.min(1, grow * core), WHITE));
    }

    /** The figures of the fourth dimension wheeling round it, drawn in at the last. */
    private void drawFigures(VfxContext ctx, float t) {
        int boom = delay + hold;
        if (t > boom + 2) return;
        float in = drawIn(t), s = strain(t);
        for (int i = 0; i < FIGURES; i++) {
            float arrive = easeOut(clamp01((t - i * 6) / 60f));
            if (arrive <= 0) continue;
            double an = figAng[i] + t * 0.004 * figSpin[i] * (1 + 3 * s);
            double rr = figRadius[i] * (2.2 - 1.2 * arrive) * (1 - in);
            Vec3 at = heart.add(Math.cos(an) * rr, figHeight[i] * (1 - in), Math.sin(an) * rr);
            double spin = t * 0.02 * figSpin[i] * (1 + 2 * s) + i;
            Polytopes.draw(ctx, KINDS[i % KINDS.length], at, figSize[i] * (1 - 0.8 * in), spin, spin * 0.6, spin * 0.3, arrive, t + i);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // before the break
    // ------------------------------------------------------------------------------------------------------------

    /** Curtains of broken light rising round the whole radius. */
    private void drawCurtains(VfxContext ctx, float t, float end) {
        int boom = delay + hold;
        float a = clamp01(t / 30f) * (1 - end) * (t > delay ? 0.45f + 0.55f * (1 - clamp01((t - delay) / 20f)) : 1) * (t > boom ? 0 : 1);
        if (a <= 0) return;
        var noise = ctx.additive(VfxTextures.NOISE);
        var white = ctx.additive(VfxTextures.WHITE);
        double floor = center.y - 60, ceiling = center.y + 360;
        for (int i = 0; i < CURTAIN; i++) {
            double a0 = i * Math.PI * 2 / CURTAIN, a1 = (i + 1) * Math.PI * 2 / CURTAIN;
            Vec3 b0 = new Vec3(center.x + Math.cos(a0) * radius, floor, center.z + Math.sin(a0) * radius);
            Vec3 b1 = new Vec3(center.x + Math.cos(a1) * radius, floor, center.z + Math.sin(a1) * radius);
            Vec3 t0 = new Vec3(b0.x, ceiling, b0.z), t1 = new Vec3(b1.x, ceiling, b1.z);
            float glitch = (Math.floorMod(i * 7 + (int) (t * 0.5f), 13) == 0) ? 1.7f : 1;
            int lo = Colors.alpha(a * 0.3f * glitch, VOID), hi = Colors.alpha(0, VOID);
            float scroll = -t * 0.025f;
            ctx.quad(noise, b0, b1, t1, t0, i * 0.3f, scroll, i * 0.3f + 0.3f, scroll + 3f, lo, lo, hi, hi);
            Vec3 g0 = new Vec3(b0.x, center.y + 0.5, b0.z), g1 = new Vec3(b1.x, center.y + 0.5, b1.z);
            ctx.beam(white, g0, g1, 1.2f, Colors.alpha(a * 0.8f, i % 2 == 0 ? CYAN : RED), 0, 1);
        }
    }

    /** Chunks flickering out of true: wire boxes split red and cyan, here and gone. */
    private void drawGlitches(VfxContext ctx, float t) {
        var white = ctx.additive(VfxTextures.WHITE);
        int frame = (int) (t / 2);
        int n = 4 + (int) (clamp01(t / delay) * 18);
        for (int k = 0; k < n; k++) {
            RandomSource r = RandomSource.create(frame * 131L + k * 977L);
            double an = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius;
            double x0 = Math.floor((center.x + Math.cos(an) * d) / 16) * 16, z0 = Math.floor((center.z + Math.sin(an) * d) / 16) * 16;
            double y0 = center.y - 10 + r.nextInt(40), y1 = y0 + 16 + r.nextInt(64);
            box(ctx, white, x0 + (r.nextDouble() - 0.5) * 3, y0, z0, x0 + 16, y1, z0 + 16, 0.6f, Colors.alpha(0.7f, r.nextBoolean() ? CYAN : RED));
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the break, and the half minute in the cube
    // ------------------------------------------------------------------------------------------------------------

    /** Space breaking: a white burst, shock rings, and the cube's cubes flashing as they are set. */
    private void drawBreak(VfxContext ctx, float tb) {
        var glow = ctx.additive(VfxTextures.GLOW);
        var shock = ctx.additive(VfxTextures.SHOCKWAVE);
        float k = clamp01(tb / 16f);
        if (k < 1) ctx.billboard(glow, heart, size * (1.5f + 5 * k), 0, Colors.alpha(1 - k, WHITE));
        for (int w = 0; w < 3; w++) {
            float s = (tb - w * 5) / 30f;
            if (s < 0 || s > 1) continue;
            ctx.disc(shock, center.add(0, 2 + w * 16, 0), X, Z, radius * (0.3f + 1.2f * easeOut(s)), w, Colors.alpha((1 - s) * 0.85f, w == 1 ? CYAN : w == 2 ? RED : PALE));
        }
        // the cube's frame flashing white, then violet
        float f = clamp01(tb / 30f);
        var white = ctx.additive(VfxTextures.WHITE);
        box(ctx, white, low.x, low.y, low.z, high.x, high.y, high.z, 3f, Colors.alpha(1 - f, f < 0.3f ? WHITE : VOID));
    }

    /** The tesseract's light pouring out through the gaps between the cubes of land. */
    private void drawLight(VfxContext ctx, float t) {
        float s = strain(t), in = drawIn(t);
        float a = clamp01((t - delay) / 20f) * (1 - in * 0.5f);
        var beam = ctx.additive(VfxTextures.BEAM_SOFT);
        float len = radius * (0.7f + 0.3f * s);
        int frame = (int) (t / 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (dy < 0) continue;
                    Vec3 d = new Vec3(dx, dy * 0.8, dz).normalize();
                    float flick = 0.65f + 0.35f * (float) Math.sin(t * (0.3f + 0.5f * s) + dx * 3 + dz * 5 + dy * 7);
                    int col = (dx + dz + frame) % 3 == 0 ? CYAN : (dx - dz + frame) % 3 == 0 ? RED : PALE;
                    ctx.softBeam(beam, heart, heart.add(d.scale(len)), 18 + 40 * s, Colors.alpha(a * 0.4f * flick, col), 0, 1, 0.05f, 0.6f);
                }
            }
        }
        // the glow of it through the walls
        ctx.billboard(ctx.additive(VfxTextures.GLOW), heart, size * (2.2f + 1.5f * s), 0, Colors.alpha(a * (0.25f + 0.35f * s), VOID));
    }

    // ------------------------------------------------------------------------------------------------------------
    // the end
    // ------------------------------------------------------------------------------------------------------------

    private void drawExplosion(VfxContext ctx, float e) {
        var glow = ctx.additive(VfxTextures.GLOW);
        float flash = clamp01(1 - e / 18f);
        if (flash > 0) ctx.billboard(glow, heart, radius * (0.8f + 2.5f * (1 - flash)), 0, Colors.alpha(flash, WHITE));
        float k = easeOut(clamp01(e / 26f));
        float fade = 1 - clamp01((e - 18) / 50f);
        if (fade > 0) {
            ctx.squash = ctx.squashFor(heart, radius * 1.1, VfxContext.viewReach() * 0.8);
            var white = ctx.additive(VfxTextures.WHITE);
            float r = radius * 1.05f * k;
            Vec3 split = ctx.camLeft.scale(r * 0.02);
            ctx.sphere(white, heart.add(split), r, 20, 40, Colors.alpha(fade * 0.45f, RED), true);
            ctx.sphere(white, heart.subtract(split), r, 20, 40, Colors.alpha(fade * 0.45f, CYAN), true);
            ctx.sphere(white, heart, r * 0.99f, 20, 40, Colors.alpha(fade * 0.6f, PALE), true);
            ctx.sphere(white, heart, r * 0.7f, 16, 32, Colors.alpha(fade * 0.35f, WHITE), false);
            ctx.squash = 1;
        }
        // shockwaves over the land, split red and cyan
        ctx.squash = ctx.squashFor(center, radius * 2.4, VfxContext.viewReach() * 0.85);
        var shock = ctx.additive(VfxTextures.SHOCKWAVE);
        for (int w = 0; w < 4; w++) {
            float s = (e - w * 6) / 50f;
            if (s < 0 || s > 1) continue;
            ctx.disc(shock, center.add(0, 2 + w * 5, 0), X, Z, radius * (0.6f + 1.8f * easeOut(s)), w, Colors.alpha((1 - s) * 0.9f, w % 3 == 0 ? WHITE : w % 3 == 1 ? CYAN : RED));
        }
        ctx.squash = 1;
        // what is left: motes of violet light drifting down through the empty air
        float motes = clamp01((e - 10) / 20f) * fade;
        if (motes > 0) {
            var star = ctx.additive(VfxTextures.STAR);
            for (int i = 0; i < 90; i++) {
                double an = i * 2.39996, d = Math.sqrt((i * 0.618034) % 1) * radius;
                Vec3 p = center.add(Math.cos(an) * d, 40 + ((i * 37) % 200) - e * 0.4, Math.sin(an) * d);
                ctx.billboard(star, p, 3 + i % 4, i, Colors.alpha(motes * 0.8f, i % 3 == 0 ? WHITE : i % 3 == 1 ? PALE : VOID));
            }
        }
    }

    private static void box(VfxContext ctx, VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1, float w, int col) {
        Vec3[] lo = {new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1)};
        Vec3[] hi = {new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1)};
        for (int i = 0; i < 4; i++) {
            ctx.beam(vc, lo[i], lo[(i + 1) % 4], w, col, 0, 1);
            ctx.beam(vc, hi[i], hi[(i + 1) % 4], w, col, 0, 1);
            ctx.beam(vc, lo[i], hi[i], w, col, 0, 1);
        }
    }
}
