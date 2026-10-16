package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.VfxContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A shape built of whole blocks, drawn with the game's own block models, so an effect looks like the world it will
 * become: each block appears at its own moment {@code at} (0 to 1, e.g. how far along a growing root it lies), and only
 * the faces that can be seen are drawn - those open to the air, or to a block of the shape not there yet (the face of a
 * growing tip). Blocks buried in the ground are left out, and a block stops being drawn once the world has a real block
 * in its place (when the server builds the shape for good).
 */
public final class BlockVoxels {
    private static final Direction[] DIRS = Direction.values();
    /** A face that is never seen (against the ground). */
    private static final float NEVER = -1;
    /** A face open to the air: seen as soon as its block is there. */
    private static final float OPEN = Float.MAX_VALUE;

    private final int n;
    private final int[] xs, ys, zs, light, tint;
    private final float[] at, faces;
    private final BlockState[] states;
    private final boolean[] built;
    private int checked;

    private BlockVoxels(int n) {
        this.n = n;
        xs = new int[n];
        ys = new int[n];
        zs = new int[n];
        light = new int[n];
        tint = new int[n];
        at = new float[n];
        faces = new float[n * 6];
        states = new BlockState[n];
        built = new boolean[n];
    }

    public int size() {
        return n;
    }

    /** Collects the blocks of a shape; the first block claimed at a place keeps it. */
    public static final class Builder {
        private final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
        private final List<int[]> pos = new ArrayList<>();
        private final List<BlockState> states = new ArrayList<>();
        private final List<Float> at = new ArrayList<>();

        public Builder() {
            index.defaultReturnValue(-1);
        }

        public void add(int x, int y, int z, BlockState state, float when) {
            long key = BlockPos.asLong(x, y, z);
            if (index.get(key) >= 0) return;
            index.put(key, pos.size());
            pos.add(new int[]{x, y, z});
            states.add(state);
            at.add(when);
        }

        /** Works out what can be seen, against the world as it is now (blocks in solid ground are dropped). */
        public BlockVoxels build(Level level) {
            int count = pos.size();
            boolean[] buried = new boolean[count];
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int i = 0; i < count; i++) {
                int[] p = pos.get(i);
                buried[i] = level.getBlockState(m.set(p[0], p[1], p[2])).isSolidRender(level, m);
            }
            Integer[] order = new Integer[count];
            int kept = 0;
            for (int i = 0; i < count; i++) if (!buried[i]) order[kept++] = i;
            Integer[] sorted = java.util.Arrays.copyOf(order, kept);
            java.util.Arrays.sort(sorted, (a, b) -> Float.compare(at.get(a), at.get(b)));
            BlockVoxels v = new BlockVoxels(kept);
            var colors = Minecraft.getInstance().getBlockColors();
            for (int k = 0; k < kept; k++) {
                int i = sorted[k];
                int[] p = pos.get(i);
                v.xs[k] = p[0];
                v.ys[k] = p[1];
                v.zs[k] = p[2];
                v.states[k] = states.get(i);
                v.at[k] = at.get(i);
                m.set(p[0], p[1], p[2]);
                v.light[k] = LevelRenderer.getLightColor(level, m);
                v.tint[k] = colors.getColor(v.states[k], level, m, 0);
                for (int d = 0; d < 6; d++) {
                    Direction dir = DIRS[d];
                    int nx = p[0] + dir.getStepX(), ny = p[1] + dir.getStepY(), nz = p[2] + dir.getStepZ();
                    int j = index.get(BlockPos.asLong(nx, ny, nz));
                    float face;
                    if (j >= 0) face = buried[j] ? NEVER : at.get(j);
                    else face = level.getBlockState(m.set(nx, ny, nz)).isSolidRender(level, m) ? NEVER : OPEN;
                    v.faces[k * 6 + d] = face;
                }
            }
            return v;
        }
    }

    /** Looks at a few blocks each call to see whether the world has built them for real yet. */
    public void checkBuilt(Level level, int count) {
        if (n == 0) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int c = 0; c < count; c++) {
            int i = checked++ % n;
            if (!built[i] && !level.getBlockState(m.set(xs[i], ys[i], zs[i])).isAir()) built[i] = true;
        }
    }

    /** Draws every block that has appeared by {@code upto}. */
    public void render(VfxContext ctx, float upto) {
        if (n == 0) return;
        Minecraft mc = Minecraft.getInstance();
        VertexConsumer vc = ctx.buffers().getBuffer(Sheets.cutoutBlockSheet());
        PoseStack pose = new PoseStack();
        double far = VfxContext.viewReach() * 1.1, far2 = far * far;
        for (int i = 0; i < n && at[i] <= upto; i++) {
            if (built[i]) continue;
            int mask = 0;
            for (int d = 0; d < 6; d++) {
                float f = faces[i * 6 + d];
                if (f != NEVER && f > upto) mask |= 1 << d;
            }
            if (mask == 0) continue;
            double dx = xs[i] + 0.5 - ctx.cam.x, dy = ys[i] + 0.5 - ctx.cam.y, dz = zs[i] + 0.5 - ctx.cam.z;
            if (dx * dx + dy * dy + dz * dz > far2) continue;
            Quads q = quads(mc, states[i]);
            float r = 1, g = 1, b = 1;
            pose.pushPose();
            pose.translate(xs[i] - ctx.cam.x, ys[i] - ctx.cam.y, zs[i] - ctx.cam.z);
            for (int d = 0; d < 6; d++) {
                if ((mask & (1 << d)) == 0) continue;
                for (BakedQuad quad : q.sides[d]) emit(vc, pose, quad, i, r, g, b);
            }
            for (BakedQuad quad : q.loose) emit(vc, pose, quad, i, r, g, b);
            pose.popPose();
        }
    }

    private void emit(VertexConsumer vc, PoseStack pose, BakedQuad quad, int i, float r, float g, float b) {
        if (quad.isTinted() && tint[i] != -1) {
            r = (tint[i] >> 16 & 0xFF) / 255f;
            g = (tint[i] >> 8 & 0xFF) / 255f;
            b = (tint[i] & 0xFF) / 255f;
        }
        vc.putBulkData(pose.last(), quad, r, g, b, 1f, light[i], OverlayTexture.NO_OVERLAY);
    }

    /** A block model's quads: those on each side (drawn when that side is seen) and those on no side. */
    private record Quads(List<BakedQuad>[] sides, List<BakedQuad> loose) {
    }

    private static final Map<BlockState, Quads> QUADS = new HashMap<>();

    @SuppressWarnings("unchecked")
    private static Quads quads(Minecraft mc, BlockState state) {
        return QUADS.computeIfAbsent(state, s -> {
            var model = mc.getBlockRenderer().getBlockModel(s);
            RandomSource rand = RandomSource.create(42);
            List<BakedQuad>[] sides = new List[6];
            for (int d = 0; d < 6; d++) {
                rand.setSeed(42);
                sides[d] = model.getQuads(s, DIRS[d], rand, ModelData.EMPTY, null);
            }
            rand.setSeed(42);
            return new Quads(sides, model.getQuads(s, null, rand, ModelData.EMPTY, null));
        });
    }

    /** Forgets the cached models (after a resource reload). */
    public static void reset() {
        QUADS.clear();
    }
}
