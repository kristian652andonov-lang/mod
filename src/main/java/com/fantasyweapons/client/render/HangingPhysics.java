package com.fantasyweapons.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Real swinging for things that hang off a held weapon (Gravebite's lantern on its chain). Each holder's hanging
 * weight is a little Verlet pendulum in world space: its pivot is wherever the model put the hook this frame, gravity
 * pulls the weight down, inertia lets it lag behind and swing past when the weapon is swung, turned or carried, and it
 * settles again. The weapon renderer says who is holding the weapon being drawn ({@link #begin}) and with what pose.
 */
public final class HangingPhysics {
    private static final double GRAVITY = 16;
    private static final double DAMPING = 0.986;
    private static final double SUBSTEP = 1 / 120.0;

    private static final class Pendulum {
        Vec3 bob, prevBob, pivot;
        long nanos;
    }

    private static final Map<Long, Pendulum> PENDULUMS = new HashMap<>();
    private static int holder = -1;
    private static boolean firstPerson;
    private static Matrix4f pose;

    private HangingPhysics() {
    }

    /** The weapon about to be drawn is held by {@code entityId} (-1: not held, e.g. in the inventory). */
    static void begin(int entityId, boolean fp) {
        holder = entityId;
        firstPerson = fp;
        pose = null;
    }

    static void end() {
        holder = -1;
        pose = null;
    }

    /** The model's transform into render space, captured just before its bones are animated. */
    static void pose(Matrix4f m) {
        if (holder >= 0) pose = new Matrix4f(m);
    }

    /** Whether a held weapon is being drawn, so hanging parts can swing. */
    static boolean active() {
        return holder >= 0 && pose != null;
    }

    /**
     * Steps the pendulum hanging from {@code pivotModel} (in the space {@code modelToRender} maps from) and returns the
     * direction it hangs in, in that same space. {@code lengthModel} is the length of the hanging part there.
     */
    static Vector3f hang(Matrix4f modelToRender, Vector3f pivotModel, float lengthModel) {
        Matrix4f m = new Matrix4f(pose).mul(modelToRender);
        Vector3f pr = m.transformPosition(new Vector3f(pivotModel));
        Matrix3f rot = m.get3x3(new Matrix3f());
        Matrix3f toWorld = new Matrix3f();
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (firstPerson) toWorld.set(cam.rotation());
        Vector3f pw = toWorld.transform(new Vector3f(pr));
        Vec3 pivot = cam.getPosition().add(pw.x, pw.y, pw.z);
        Matrix3f modelToWorld = new Matrix3f(toWorld).mul(rot);
        double len = Math.max(0.05, modelToWorld.transform(new Vector3f(0, lengthModel, 0)).length());

        long key = ((long) holder << 1) | (firstPerson ? 1 : 0);
        Pendulum p = PENDULUMS.computeIfAbsent(key, k -> new Pendulum());
        long now = System.nanoTime();
        double dt = p.nanos == 0 ? 1 : (now - p.nanos) / 1e9;
        if (p.bob == null || dt > 0.3 || p.pivot.distanceToSqr(pivot) > 9) {
            p.bob = pivot.add(0, -len, 0);
            p.prevBob = p.bob;
        } else {
            dt = Math.min(dt, 0.05);
            int steps = Math.max(1, (int) Math.ceil(dt / SUBSTEP));
            double h = dt / steps;
            for (int i = 1; i <= steps; i++) {
                Vec3 at = p.pivot.lerp(pivot, i / (double) steps);
                Vec3 vel = p.bob.subtract(p.prevBob).scale(DAMPING);
                p.prevBob = p.bob;
                Vec3 next = p.bob.add(vel).add(0, -GRAVITY * h * h, 0);
                Vec3 arm = next.subtract(at);
                double l = arm.length();
                p.bob = l < 1e-6 ? at.add(0, -len, 0) : at.add(arm.scale(len / l));
            }
        }
        p.pivot = pivot;
        p.nanos = now;
        if (PENDULUMS.size() > 64) PENDULUMS.clear();

        Vec3 d = p.bob.subtract(pivot).normalize();
        Vector3f dm = new Matrix3f(modelToWorld).invert().transform(new Vector3f((float) d.x, (float) d.y, (float) d.z));
        return dm.normalize();
    }

    public static void clear() {
        PENDULUMS.clear();
    }
}
