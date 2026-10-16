package com.fantasyweapons.weapons.ultimate;

import net.minecraft.world.phys.Vec3;

/**
 * One giant root of the Great Tree: a cubic curve that bursts out of the ground, arches high and plunges back in,
 * thick at its base and tapering to its tip. Built by the server from the real terrain and sent to clients, so the root
 * they see growing is the root that is then built into the world.
 *
 * @param thickness radius at the base (blocks)
 */
public record RootSpline(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float thickness) {
    public Vec3 at(double t) {
        double u = 1 - t;
        return p0.scale(u * u * u).add(p1.scale(3 * u * u * t)).add(p2.scale(3 * u * t * t)).add(p3.scale(t * t * t));
    }

    public Vec3 tangent(double t) {
        double u = 1 - t;
        Vec3 d = p1.subtract(p0).scale(3 * u * u).add(p2.subtract(p1).scale(6 * u * t)).add(p3.subtract(p2).scale(3 * t * t));
        return d.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : d.normalize();
    }

    /** Radius along the root: full at the base, a third of it at the tip. */
    public double radius(double t) {
        return thickness * (1 - 0.68 * t);
    }

    /** The root sampled into {@code n} straight pieces (n + 1 points). */
    public Vec3[] samples(int n) {
        Vec3[] out = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) out[i] = at(i / (double) n);
        return out;
    }

    public double minX() {
        return Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x)) - thickness;
    }

    public double maxX() {
        return Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x)) + thickness;
    }

    public double minY() {
        return Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y)) - thickness;
    }

    public double maxY() {
        return Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y)) + thickness;
    }

    public double minZ() {
        return Math.min(Math.min(p0.z, p1.z), Math.min(p2.z, p3.z)) - thickness;
    }

    public double maxZ() {
        return Math.max(Math.max(p0.z, p1.z), Math.max(p2.z, p3.z)) + thickness;
    }
}
