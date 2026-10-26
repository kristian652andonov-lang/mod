package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Real blocks torn out of the world and flung about: each piece is the block's own model (grass, stone, logs, ore...),
 * tumbling. Pieces either fly a ballistic arc ({@link #throwBlock}) or are dragged along a tightening spiral into a point
 * ({@link #suck}), stretching as they near it. The effect lives as long as its pieces do; more can be added while it
 * runs.
 */
public class BlockDebrisVfx extends Vfx {
    /** The most blocks in the air at once (fewer at the lower effect qualities). */
    private static int max() {
        return com.fantasyweapons.config.ClientConfig.quality().debris();
    }

    private static final class Piece {
        BlockState state;
        Vec3 start, vel, sink;
        float spin, spinSpeed, size, swirl;
        Vec3 axis;
        int born, life;
        boolean sucked;
    }

    private final List<Piece> pieces = new ArrayList<>();
    private final RandomSource random;
    private boolean closed;

    public BlockDebrisVfx(long seed) {
        super(Integer.MAX_VALUE / 2);
        this.random = RandomSource.create(seed);
    }

    /** No more pieces will come: the effect ends when the last one does. */
    public void close() {
        closed = true;
    }

    public int count() {
        return pieces.size();
    }

    /** A block thrown from {@code at} with velocity {@code vel} (blocks/tick), falling under gravity for {@code life} ticks. */
    public void throwBlock(BlockState state, Vec3 at, Vec3 vel, float size, int life) {
        if (!usable(state) || pieces.size() >= max()) return;
        Piece p = base(state, at, size, life);
        p.vel = vel;
        pieces.add(p);
    }

    /** A block dragged from {@code at} into {@code sink} along a spiral, over {@code life} ticks. */
    public void suck(BlockState state, Vec3 at, Vec3 sink, float size, float swirl, int life) {
        if (!usable(state) || pieces.size() >= max()) return;
        Piece p = base(state, at, size, life);
        p.sink = sink;
        p.swirl = swirl;
        p.sucked = true;
        pieces.add(p);
    }

    private Piece base(BlockState state, Vec3 at, float size, int life) {
        Piece p = new Piece();
        p.state = state;
        p.start = at;
        p.size = size;
        p.life = Math.max(4, life);
        p.born = age;
        p.spinSpeed = (random.nextFloat() - 0.5f) * 0.6f;
        p.axis = new Vec3(random.nextFloat() - 0.5, random.nextFloat() - 0.5, random.nextFloat() - 0.5).normalize();
        return p;
    }

    private static boolean usable(BlockState state) {
        return state != null && !state.isAir() && state.getRenderShape() == RenderShape.MODEL && state.getFluidState().isEmpty();
    }

    @Override
    public void tick() {
        age++;
        pieces.removeIf(p -> age - p.born >= p.life);
        if (closed && pieces.isEmpty()) dead = true;
    }

    /** Where a piece is at {@code t} ticks into its flight, and how much of it is left (it shrinks into the sink). */
    private static Vec3 position(Piece p, float t, float[] scale) {
        if (!p.sucked) {
            scale[0] = 1;
            return p.start.add(p.vel.scale(t)).add(0, -0.04 * t * t, 0);
        }
        float k = Math.min(1, t / p.life);
        float pull = k * k * (3 - 2 * k);
        Vec3 off = p.start.subtract(p.sink);
        // the spiral tightens: the piece turns about the sink faster and faster as it falls in
        double ang = p.swirl * pull * pull * 6;
        double cos = Math.cos(ang), sin = Math.sin(ang);
        Vec3 turned = new Vec3(off.x * cos - off.z * sin, off.y, off.x * sin + off.z * cos);
        double lift = Math.sin(pull * Math.PI) * off.length() * 0.12;
        scale[0] = 1 - pull * pull;
        return p.sink.add(turned.scale(1 - pull)).add(0, lift, 0);
    }

    @Override
    public void render(VfxContext ctx) {
        if (pieces.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var dispatcher = mc.getBlockRenderer();
        PoseStack pose = new PoseStack();
        float[] scale = new float[1];
        BlockPos.MutableBlockPos lightAt = new BlockPos.MutableBlockPos();
        double far = VfxContext.viewReach();
        for (Piece p : pieces) {
            float t = age - p.born + ctx.partial;
            Vec3 at = position(p, t, scale);
            float s = p.size * scale[0];
            if (s <= 0.02f) continue;
            float fadeIn = Math.min(1, t / 3f);
            s *= fadeIn;
            // beyond the view distance a piece is drawn nearer and smaller in proportion: the same on screen
            double dist = at.distanceTo(ctx.cam), limit = far * 0.85, k = dist > limit ? limit / dist : 1;
            if (dist > far * 4) continue;
            s *= (float) k;
            pose.pushPose();
            pose.translate((at.x - ctx.cam.x) * k, (at.y - ctx.cam.y) * k, (at.z - ctx.cam.z) * k);
            if (p.sucked && scale[0] < 0.9f) {
                // stretched towards the sink as it falls in
                Vec3 to = p.sink.subtract(at);
                if (to.lengthSqr() > 1e-4) {
                    Vec3 d = to.normalize();
                    float yaw = (float) Math.atan2(d.x, d.z), pitch = (float) Math.asin(Math.max(-1, Math.min(1, -d.y)));
                    pose.mulPose(Axis.YP.rotation(yaw));
                    pose.mulPose(Axis.XP.rotation(pitch));
                    pose.scale(1, 1, 1 + (1 - scale[0]) * 3);
                    pose.mulPose(Axis.XP.rotation(-pitch));
                    pose.mulPose(Axis.YP.rotation(-yaw));
                }
            }
            pose.mulPose(Axis.of(new org.joml.Vector3f((float) p.axis.x, (float) p.axis.y, (float) p.axis.z)).rotation(p.spinSpeed * t));
            pose.scale(s, s, s);
            pose.translate(-0.5, -0.5, -0.5);
            int light = LevelRenderer.getLightColor(mc.level, lightAt.set(at.x, at.y, at.z));
            try {
                dispatcher.renderSingleBlock(p.state, pose, ctx.buffers(), light, OverlayTexture.NO_OVERLAY,
                        net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
            } catch (RuntimeException e) {
                p.life = 0;
            }
            pose.popPose();
        }
    }
}
