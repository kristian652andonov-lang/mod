package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * THE ICE AGE. The sea heaves up at the target in a great dome of water, collapses, and rolls out as a tsunami in every
 * direction - all of it built of blocks: a wall of water blocks riding over the land, its crest of white foam curling
 * forward and hanging over its front, and behind the crest the water turning to ice as you watch, clear ice and then
 * packed and blue ice down its long back. Behind it the land freezes for real (the server builds it), an icy fog lies
 * over it and snow drifts down.
 */
public class IceAgeVfx extends Vfx {
    private static final int FOAM = 0xF4FCFF, FROST = 0xDDF4FF, WATER_TINT = 0x3F76E4;
    /** How far the wave reaches ahead of its centre line (its overhanging lip) and behind it (its back), in blocks. */
    private static final int LIP = 4, BACK = 14;

    private final Vec3 center;
    private final float radius, height, speed;
    private final int surge;

    public IceAgeVfx(Vec3 center, float radius, float height, float speed, int surge) {
        super(surge + (int) (radius / speed) + 120);
        this.center = center;
        this.radius = radius;
        this.height = height;
        this.speed = speed;
        this.surge = surge;
    }

    private float wave(float t) {
        return Math.max(0, (t - surge) * speed);
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Blocks b = new Blocks(ctx);
        if (t < surge + 10) drawSurge(ctx, b, level, t);
        float rw = wave(t);
        float alive = 1 - clamp01((rw - radius) / 30f);
        if (t >= surge && alive > 0) drawWave(ctx, b, level, t, rw, alive);
        ctx.farClamp = VfxContext.viewReach() * 0.86;
        drawFrost(ctx, t, rw);
        ctx.farClamp = 0;
    }

    /** The ground (never below the sea) a column of the wave stands on. */
    private static int ground(net.minecraft.world.level.Level level, int x, int z, int fallback) {
        if (!level.hasChunk(x >> 4, z >> 4)) return fallback;
        return Math.max(level.getSeaLevel() - 1, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1);
    }

    /** The sea heaving up into a dome of water at the target, then falling outward into the wave. */
    private void drawSurge(VfxContext ctx, Blocks b, net.minecraft.world.level.Level level, float t) {
        float rise = t < surge ? easeOut(t / surge) : 1 - clamp01((t - surge) / 10f);
        float h = height * 1.3f * rise, r = height * (0.7f + 0.7f * (t / (surge + 10)));
        if (h < 1) return;
        int cx = (int) Math.floor(center.x), cz = (int) Math.floor(center.z), base = (int) Math.floor(center.y) - 1;
        int ri = (int) Math.ceil(r);
        Blocks.Range dome = (x, z) -> {
            double d = Math.hypot(x + 0.5 - center.x, z + 0.5 - center.z);
            if (d > r) return null;
            int top = base + (int) Math.round(h * Math.sqrt(1 - (d / r) * (d / r)));
            return top <= base ? null : new int[]{base + 1, top};
        };
        for (int x = cx - ri; x <= cx + ri; x++) {
            for (int z = cz - ri; z <= cz + ri; z++) {
                b.column(level, x, z, dome, (y, top) -> top ? Blocks.FOAM_BLOCK : Blocks.WATER_BLOCK);
            }
        }
    }

    private void drawWave(VfxContext ctx, Blocks b, net.minecraft.world.level.Level level, float t, float rw, float alive) {
        // the wave grows to full height as it leaves, and wears down near the end of its run
        float h = height * Math.min(1, 0.4f + (t - surge) / 30f) * (0.55f + 0.45f * (1 - clamp01((rw - radius * 0.7f) / (radius * 0.3f)))) * alive;
        if (h < 2) return;
        int cx = (int) Math.floor(center.x), cz = (int) Math.floor(center.z);
        int outer = (int) Math.ceil(rw + LIP + 1), inner = (int) Math.max(0, Math.floor(rw - BACK - 1));
        Vec3 look = new Vec3(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector());
        int fallback = (int) Math.floor(center.y) - 1;
        // where the wave stands in a column: from lo to hi, or nowhere
        Blocks.Range wave = (x, z) -> {
            double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z, d = Math.sqrt(dx * dx + dz * dz);
            if (d > rw + LIP || d < rw - BACK) return null;
            double s = d - rw, a = Math.atan2(dz, dx);
            float hh = h * (1 + 0.1f * (float) Math.sin(a * 7 + t * 0.15) + 0.06f * (float) Math.sin(a * 17 - t * 0.21));
            int g = ground(level, x, z, fallback);
            int lo, hi;
            if (s > 0) {
                // the lip, curling forward over the front
                double k = s / LIP;
                lo = g + (int) Math.round(hh * (0.5 + 0.38 * k));
                hi = g + (int) Math.round(hh * (1 - 0.18 * k * k));
            } else {
                double shape = s > -2 ? 1 : Math.pow(Math.max(0, (s + BACK) / (BACK - 2)), 1.5);
                lo = g + 1;
                hi = g + (int) Math.round(hh * shape);
            }
            return hi < lo ? null : new int[]{lo, hi};
        };
        var mist = ctx.translucent(VfxTextures.MIST);
        for (int x = cx - outer; x <= cx + outer; x++) {
            for (int z = cz - outer; z <= cz + outer; z++) {
                double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z, d = Math.sqrt(dx * dx + dz * dz);
                if (d > rw + LIP || d < inner) continue;
                // behind the eye: not drawn
                double ex = x + 0.5 - ctx.cam.x, ez = z + 0.5 - ctx.cam.z;
                if (ex * look.x + ez * look.z < -12 && ex * ex + ez * ez > 400) continue;
                double s = d - rw;
                int[] here = wave.at(x, z);
                if (here == null) continue;
                final int bx = x, bz = z, crest = here[1];
                int[] col = b.column(level, x, z, wave, (y, top) -> {
                    // foam on the crest; water at the front; behind the crest it freezes: clear ice, then packed and blue
                    if (s > -3.5) return top || s > 0.5 && y >= crest - 1 ? Blocks.FOAM_BLOCK : Blocks.WATER_BLOCK;
                    if (s > -7) return Blocks.ICE_BLOCK;
                    return hash(bx, y, bz) < 0.5 ? Blocks.PACKED_BLOCK : Blocks.BLUE_BLOCK;
                });
                // spray thrown off the crest
                if (col != null && s > -1 && s <= 0 && ((x * 31 + z * 17) & 7) == 0) {
                    float cyc = ((x * 0.37f + z * 0.61f) + t * 0.05f) % 1f;
                    Vec3 out = new Vec3(dx / d, 0, dz / d);
                    Vec3 at = new Vec3(x + 0.5, col[1] + 1.2, z + 0.5).add(out.scale(cyc * 4)).add(0, -cyc * cyc * 5, 0);
                    ctx.billboard(mist, at, 2.4f + cyc * 3, x + z, Colors.alpha((1 - cyc) * 0.6f * alive, FOAM));
                }
            }
        }
    }

    private static double hash(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        h = (h ^ (h >>> 13)) * 0x5DEECE66DL;
        return ((h >>> 20) & 0xFFFF) / 65535.0;
    }

    /**
     * Columns of blocks drawn from block textures: each column a run of blocks from {@code lo} to {@code hi}, its top,
     * bottom and the sides no neighbouring column hides.
     */
    private static final class Blocks {
        static final int WATER_BLOCK = 0, FOAM_BLOCK = 1, ICE_BLOCK = 2, PACKED_BLOCK = 3, BLUE_BLOCK = 4;
        private final VfxContext ctx;
        private final TextureAtlasSprite[] sprites = new TextureAtlasSprite[5];
        private final VertexConsumer clear, solid;
        private final PoseStack.Pose pose = new PoseStack().last();

        Blocks(VfxContext ctx) {
            this.ctx = ctx;
            var atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
            sprites[WATER_BLOCK] = atlas.getSprite(ResourceLocation.withDefaultNamespace("block/water_still"));
            sprites[FOAM_BLOCK] = atlas.getSprite(ResourceLocation.withDefaultNamespace("block/snow"));
            sprites[ICE_BLOCK] = atlas.getSprite(ResourceLocation.withDefaultNamespace("block/ice"));
            sprites[PACKED_BLOCK] = atlas.getSprite(ResourceLocation.withDefaultNamespace("block/packed_ice"));
            sprites[BLUE_BLOCK] = atlas.getSprite(ResourceLocation.withDefaultNamespace("block/blue_ice"));
            clear = ctx.buffers().getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
            solid = ctx.buffers().getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        }

        interface Kind {
            int at(int y, boolean top);
        }

        /** Where a shape stands in a column: {lo, hi}, or null if not there. */
        interface Range {
            int[] at(int x, int z);
        }

        private static final int[][] SIDES = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

        /**
         * Draws the column at (x, z) of a shape: its top, its bottom, and each side where the neighbouring column has no
         * block of the shape at that height. Returns its range (null if it has none).
         */
        int[] column(net.minecraft.world.level.Level level, int x, int z, Range range, Kind kind) {
            int[] r = range.at(x, z);
            if (r == null) return null;
            int[][] nb = new int[4][];
            for (int i = 0; i < 4; i++) nb[i] = range.at(x + SIDES[i][0], z + SIDES[i][1]);
            int light = LevelRenderer.getLightColor(level, new net.minecraft.core.BlockPos(x, r[1] + 1, z));
            float fx = (float) (x - ctx.cam.x), fz = (float) (z - ctx.cam.z);
            for (int y = r[0]; y <= r[1]; y++) {
                int k = kind.at(y, y == r[1]);
                boolean water = k == WATER_BLOCK, see = water || k == ICE_BLOCK;
                VertexConsumer vc = see ? clear : solid;
                int argb = water ? 0xD0000000 | WATER_TINT : see ? 0xC8FFFFFF : 0xFFFFFFFF;
                TextureAtlasSprite sp = sprites[k];
                float fy = (float) (y - ctx.cam.y);
                if (y == r[1]) face(vc, sp, fx, fy, fz, 0, argb, light);
                if (y == r[0]) face(vc, sp, fx, fy, fz, 1, argb, light);
                for (int i = 0; i < 4; i++) {
                    if (nb[i] != null && y >= nb[i][0] && y <= nb[i][1]) continue;
                    face(vc, sp, fx, fy, fz, 2 + i, argb, light);
                }
            }
            return r;
        }

        /** One face of the unit block at (x, y, z) relative to the eye: 0 up, 1 down, 2 north, 3 south, 4 west, 5 east. */
        private void face(VertexConsumer vc, TextureAtlasSprite sp, float x, float y, float z, int f, int argb, int light) {
            float[][] c = CORNERS[f];
            float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
            float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
            float[] n = NORMALS[f];
            for (int i = 0; i < 4; i++) {
                vc.addVertex(pose, x + c[i][0], y + c[i][1], z + c[i][2]).setColor(argb).setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
            }
        }

        private static final float[][][] CORNERS = {
                {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
                {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
                {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
                {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
                {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
                {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}};
        private static final float[][] NORMALS = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    }

    /** Icy fog over the frozen land behind the wave, and snow drifting down. */
    private void drawFrost(VfxContext ctx, float t, float rw) {
        float a = clamp01((t - surge) / 20f) * (1 - clamp01((t - lifetime + 50) / 50f));
        if (a <= 0.01f || rw < 10) return;
        var mist = ctx.translucent(VfxTextures.MIST);
        var flake = ctx.additive(VfxTextures.SNOWFLAKE);
        int n = ctx.segments(70);
        float inner = Math.min(rw, radius) - 8;
        for (int i = 0; i < n; i++) {
            double ang = i * 2.39996;
            float rr = inner * (float) Math.sqrt(((i * 0.618f) % 1f));
            Vec3 p = center.add(Math.cos(ang) * rr, 3 + (i % 4), Math.sin(ang) * rr);
            ctx.billboard(mist, p, 22 + (i % 5) * 4, i * 0.3f + t * 0.002f, Colors.alpha(a * 0.32f, FROST));
            float fall = ((i * 0.37f) + t * 0.006f) % 1f;
            ctx.billboard(flake, p.add(Math.sin(i + t * 0.05) * 3, 40 * (1 - fall), Math.cos(i * 1.7 + t * 0.05) * 3), 0.9f, i + t * 0.05f,
                    Colors.alpha(a * 0.9f, 0xFFFFFF));
        }
    }
}
