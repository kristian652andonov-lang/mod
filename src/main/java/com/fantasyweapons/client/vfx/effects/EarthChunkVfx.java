package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.GroundMaterial;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Broken ground made of the terrain's own material: irregular chunks thrown out of an impact that tumble, bounce and
 * settle, and plates of earth that buckle up out of the ground, tilted away from the blow, then sink back. Each piece
 * is a jittered block textured with the real block's top and side textures (so grass breaks into green-topped sods,
 * stone into stone). Pieces shrink into the ground at the end instead of popping out.
 */
public class EarthChunkVfx extends Vfx {
    private static final Vec3 LIGHT = new Vec3(0.35, 0.9, 0.25).normalize();
    /** Corner indices (bit 0: +x, bit 1: +y, bit 2: +z) per face; sides run bottom, bottom, top, top. */
    private static final int[][] FACES = {
            {2, 3, 7, 6}, // +Y (top)
            {0, 1, 5, 4}, // -Y
            {1, 5, 7, 3}, // +X
            {4, 0, 2, 6}, // -X
            {5, 4, 6, 7}, // +Z
            {0, 1, 3, 2}, // -Z
    };

    private final GroundMaterial mat;
    private final List<Piece> pieces = new ArrayList<>();

    private static final class Piece {
        final boolean slab;
        final Vector3f[] corners = new Vector3f[8];
        final float[] uvOff = new float[2];
        final int delay;
        final int life;
        // flying chunks
        Vec3 pos, prev, vel;
        final Vector3f spin;
        final Quaternionf rot = new Quaternionf(), prevRot = new Quaternionf();
        final double floor;
        boolean resting;
        // hovering chunks: rise from hoverBase, bob, then launched with launchVel at tick `launch`
        int launch = -1;
        Vec3 hoverBase, launchVel;
        double hoverH;
        float bob;
        // slabs
        Vec3 base;
        Vec3 axis;
        float tilt, rise;

        Piece(boolean slab, int delay, int life, Vector3f spin, double floor) {
            this.slab = slab;
            this.delay = delay;
            this.life = life;
            this.spin = spin;
            this.floor = floor;
        }
    }

    public EarthChunkVfx(GroundMaterial mat, int lifetime) {
        super(lifetime);
        this.mat = mat;
    }

    /** A chunk thrown from {@code pos} with velocity {@code vel} (blocks/tick); it lands on the ground at {@code floorY}. */
    public EarthChunkVfx chunk(Vec3 pos, Vec3 vel, float size, double floorY, long seed) {
        RandomSource r = RandomSource.create(seed);
        Piece p = new Piece(false, 0, lifetime - r.nextInt(Math.max(1, lifetime / 4)),
                new Vector3f((r.nextFloat() - 0.5f) * 0.7f, (r.nextFloat() - 0.5f) * 0.5f, (r.nextFloat() - 0.5f) * 0.7f), floorY);
        box(p, size * (0.8f + 0.4f * r.nextFloat()), size * (0.6f + 0.5f * r.nextFloat()), size * (0.8f + 0.4f * r.nextFloat()), 0.28f, r);
        p.pos = p.prev = pos;
        p.vel = vel;
        p.rot.rotationXYZ(r.nextFloat() * 6.28f, r.nextFloat() * 6.28f, r.nextFloat() * 6.28f);
        p.prevRot.set(p.rot);
        pieces.add(p);
        return this;
    }

    /**
     * A chunk torn loose at {@code base}: it floats up to {@code height} and bobs there until tick {@code launch}, then
     * flies off with {@code vel} and falls like any other chunk.
     */
    public EarthChunkVfx hover(Vec3 base, double height, int launch, Vec3 vel, float size, long seed) {
        RandomSource r = RandomSource.create(seed);
        Piece p = new Piece(false, 0, lifetime, new Vector3f((r.nextFloat() - 0.5f) * 0.12f, (r.nextFloat() - 0.5f) * 0.1f, (r.nextFloat() - 0.5f) * 0.12f),
                base.y);
        box(p, size * (0.8f + 0.4f * r.nextFloat()), size * (0.6f + 0.5f * r.nextFloat()), size * (0.8f + 0.4f * r.nextFloat()), 0.3f, r);
        p.pos = p.prev = base;
        p.vel = Vec3.ZERO;
        p.hoverBase = base;
        p.hoverH = height;
        p.launch = launch;
        p.launchVel = vel;
        p.bob = r.nextFloat() * 6.28f;
        p.rot.rotationXYZ(r.nextFloat() * 6.28f, r.nextFloat() * 6.28f, r.nextFloat() * 6.28f);
        p.prevRot.set(p.rot);
        pieces.add(p);
        return this;
    }

    /**
     * A plate of ground at {@code base} (on the surface) that heaves up by {@code rise} and tips {@code tilt} radians
     * away from the impact (along {@code outward}), after {@code delay} ticks.
     */
    public EarthChunkVfx slab(Vec3 base, Vec3 outward, float w, float h, float d, float tilt, float rise, int delay, long seed) {
        RandomSource r = RandomSource.create(seed);
        Piece p = new Piece(true, delay, lifetime - delay, new Vector3f(), base.y);
        box(p, w, h, d, 0.22f, r);
        Vec3 out = new Vec3(outward.x, 0, outward.z);
        out = out.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : out.normalize();
        p.base = base;
        p.axis = new Vec3(-out.z, 0, out.x);
        p.tilt = tilt;
        p.rise = rise;
        // the plate is oriented with its depth along the outward direction, never quite square to it
        float yaw = (float) Math.atan2(out.x, out.z) + (r.nextFloat() - 0.5f) * 0.9f;
        p.rot.rotationY(yaw).rotateZ((r.nextFloat() - 0.5f) * 0.35f).rotateX((r.nextFloat() - 0.5f) * 0.15f);
        pieces.add(p);
        return this;
    }

    private static void box(Piece p, float w, float h, float d, float jitter, RandomSource r) {
        for (int i = 0; i < 8; i++) {
            float x = ((i & 1) != 0 ? 0.5f : -0.5f) * w * (1 + (r.nextFloat() - 0.5f) * 2 * jitter);
            float y = ((i & 2) != 0 ? 0.5f : -0.5f) * h * (1 + (r.nextFloat() - 0.5f) * 2 * jitter);
            float z = ((i & 4) != 0 ? 0.5f : -0.5f) * d * (1 + (r.nextFloat() - 0.5f) * 2 * jitter);
            p.corners[i] = new Vector3f(x, y, z);
        }
        p.uvOff[0] = r.nextFloat() * 0.5f;
        p.uvOff[1] = r.nextFloat() * 0.5f;
    }

    @Override
    public void tick() {
        super.tick();
        for (Piece p : pieces) {
            if (p.slab || p.resting) continue;
            p.prev = p.pos;
            p.prevRot.set(p.rot);
            if (p.launch >= 0 && age < p.launch) {
                float k = easeOut(Math.min(1f, age / (p.launch * 0.6f)));
                p.pos = p.hoverBase.add(0, p.hoverH * k + Math.sin(age * 0.15 + p.bob) * 0.15, 0);
                p.rot.rotateXYZ(p.spin.x, p.spin.y, p.spin.z);
                continue;
            }
            if (p.launch >= 0 && age == p.launch) {
                p.vel = p.launchVel;
                p.spin.mul(5f);
            }
            Vec3 v = p.vel.add(0, -0.055, 0).scale(0.985);
            Vec3 next = p.pos.add(v);
            if (next.y < p.floor && v.y < 0) {
                // bounce, losing most of the energy; settle once slow
                next = new Vec3(next.x, p.floor, next.z);
                v = new Vec3(v.x * 0.55, -v.y * 0.3, v.z * 0.55);
                p.spin.mul(0.5f);
                if (Math.abs(v.y) < 0.05 && v.horizontalDistanceSqr() < 0.004) p.resting = true;
            }
            p.vel = v;
            p.pos = next;
            p.rot.rotateXYZ(p.spin.x, p.spin.y, p.spin.z);
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        Vector3f tmp = new Vector3f();
        List<Vec3[]> shapes = new ArrayList<>();
        List<Piece> shown = new ArrayList<>();
        for (Piece p : pieces) {
            float local = time - p.delay;
            if (local <= 0) continue;
            float end = clamp01((local - (p.life - 10)) / 10f);
            if (end >= 1) continue;
            Quaternionf q;
            Vec3 at;
            float scale = 1 - easeIn(end);
            if (p.slab) {
                // heave up with a small overshoot, hold, then sink back into the ground
                float up = local < 5 ? easeOut(local / 5f) * 1.08f : (local < 8 ? 1.08f - 0.08f * (local - 5) / 3f : 1f);
                float sink = easeIn(end);
                float lift = p.rise * up * (1 - sink) - 0.15f;
                double ang = p.tilt * up;
                q = new Quaternionf().rotateAxis((float) ang, (float) p.axis.x, (float) p.axis.y, (float) p.axis.z).mul(p.rot);
                at = p.base.add(0, lift, 0);
                scale = 1;
            } else {
                q = new Quaternionf(p.prevRot).slerp(p.rot, ctx.partial);
                at = p.prev.lerp(p.pos, ctx.partial).subtract(0, 0.3 * easeIn(end), 0);
            }
            Vec3[] w = new Vec3[9];
            for (int i = 0; i < 8; i++) {
                q.transform(tmp.set(p.corners[i]).mul(scale));
                w[i] = at.add(tmp.x, tmp.y, tmp.z);
            }
            w[8] = at;
            shapes.add(w);
            shown.add(p);
        }
        if (shapes.isEmpty()) return;
        // smooth earth on every face (and on top unless the ground is turf), then the turf tops in their own pass
        VertexConsumer soil = ctx.solid(com.fantasyweapons.client.vfx.VfxTextures.SOIL);
        for (int k = 0; k < shapes.size(); k++) {
            for (int f = 0; f < 6; f++) if (f != 0 || !mat.turf()) face(ctx, soil, shown.get(k), shapes.get(k), f);
        }
        if (mat.turf()) {
            VertexConsumer turf = ctx.solid(com.fantasyweapons.client.vfx.VfxTextures.TURF);
            for (int k = 0; k < shapes.size(); k++) face(ctx, turf, shown.get(k), shapes.get(k), 0);
        }
    }

    private void face(VfxContext ctx, VertexConsumer vc, Piece p, Vec3[] w, int f) {
        int[] k = FACES[f];
        Vec3 n = w[k[1]].subtract(w[k[0]]).cross(w[k[3]].subtract(w[k[0]]));
        n = n.lengthSqr() < 1e-12 ? new Vec3(0, 1, 0) : n.normalize();
        Vec3 faceMid = w[k[0]].add(w[k[2]]).scale(0.5);
        if (n.dot(faceMid.subtract(w[8])) < 0) n = n.scale(-1);
        float shade = 0.5f + 0.5f * (float) Math.max(0, n.dot(LIGHT));
        boolean top = f == 0;
        int base = top ? mat.topColor() : mat.sideColor();
        int c0 = Colors.argb(255, mat.lit(base, shade));
        // sides of a torn sod shade up into the turf colour along their top edge
        int c1 = !top && f >= 2 && mat.turf() ? Colors.argb(255, mat.lit(Colors.lerpRgb(base, mat.topColor(), 0.5f), shade)) : c0;
        // the texture is laid on at world scale (about 1.8 blocks per repeat) from a random offset, so no two pieces match
        float fu = (float) w[k[0]].distanceTo(w[k[1]]) * 0.55f, fv = (float) w[k[0]].distanceTo(w[k[3]]) * 0.55f;
        float u0 = p.uvOff[0] * 4, v0 = p.uvOff[1] * 4;
        if (f >= 2) {
            ctx.vertex(vc, w[k[0]], u0, v0 + fv, c0);
            ctx.vertex(vc, w[k[1]], u0 + fu, v0 + fv, c0);
            ctx.vertex(vc, w[k[2]], u0 + fu, v0, c1);
            ctx.vertex(vc, w[k[3]], u0, v0, c1);
        } else {
            ctx.vertex(vc, w[k[0]], u0, v0, c0);
            ctx.vertex(vc, w[k[1]], u0 + fu, v0, c0);
            ctx.vertex(vc, w[k[2]], u0 + fu, v0 + fv, c0);
            ctx.vertex(vc, w[k[3]], u0, v0 + fv, c0);
        }
    }
}
