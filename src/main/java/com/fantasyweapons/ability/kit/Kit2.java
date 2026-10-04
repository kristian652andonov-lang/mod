package com.fantasyweapons.ability.kit;

import net.minecraft.world.phys.Vec3;

/** Vector helpers that do not depend on client classes (usable on a dedicated server). */
public final class Kit2 {
    private Kit2() {
    }

    /** Two perpendicular unit vectors orthogonal to {@code n}. */
    public static Vec3[] basis(Vec3 n) {
        Vec3 nn = n.normalize();
        Vec3 ref = Math.abs(nn.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 x = ref.cross(nn).normalize();
        Vec3 y = nn.cross(x).normalize();
        return new Vec3[]{x, y};
    }
}
