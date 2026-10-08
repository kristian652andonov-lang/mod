package com.fantasyweapons.client.render;

import com.fantasyweapons.weapon.WeaponClass;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First-person attacks as keyframed motions of the weapon hand. Each attack has a short wind-up that pulls the weapon
 * back to the side its cut starts from, a fast strike through the middle of the view with the edge leading, a
 * follow-through that carries it past and slows it down, and an eased recovery back to the idle hold - every channel
 * on a smooth (Catmull-Rom) curve through its keys, so nothing snaps.
 * <p>
 * A key says where the hand is (offset x right, y up, z towards the viewer) and where the blade points: {@code yaw}
 * (0 straight ahead, + to the left, - to the right) and {@code elev} (0 level, + up, - down), plus {@code roll} about
 * the blade so its edge leads. The blade's resting direction comes from the weapon model's first-person display
 * transform, and each frame the weapon is turned from there onto the keyed direction about the grip - so the same
 * attack reads the same on every weapon. Three attacks per swing style (variants 0-2, the same as the third-person
 * ones and the slashes they leave).
 */
public final class FirstPersonAttacks {
    /** s (0..1 of the swing), hand x, y, z, blade yaw, elevation, roll; NaN yaw/elev means the idle direction. */
    private record Key(float s, float x, float y, float z, float yaw, float elev, float roll) {
    }

    private static Key k(float s, float x, float y, float z, float yaw, float elev, float roll) {
        return new Key(s, x, y, z, yaw, elev, roll);
    }

    private static final Key REST0 = k(0, 0, 0, 0, Float.NaN, Float.NaN, 0), REST1 = k(1, 0, 0, 0, Float.NaN, Float.NaN, 0);

    // ---- one-handed blade: forehand cut right to left, backhand left to right, diagonal from high right ----
    private static final Key[] SLASH_FORE = {REST0,
            k(0.14f, 0.12f, 0.16f, 0.06f, -80, 40, -30),
            k(0.26f, 0.08f, 0.22f, -0.06f, -40, 18, -70),
            k(0.36f, -0.10f, 0.32f, -0.18f, 15, 12, -85),
            k(0.46f, -0.32f, 0.26f, -0.14f, 60, 4, -85),
            k(0.58f, -0.40f, 0.10f, -0.04f, 85, -5, -70),
            k(0.80f, -0.14f, 0.02f, 0.00f, 65, 35, -25), REST1};
    private static final Key[] SLASH_BACK = {REST0,
            k(0.14f, -0.22f, 0.16f, 0.04f, 100, 35, 30),
            k(0.26f, -0.18f, 0.22f, -0.06f, 60, 15, 70),
            k(0.36f, 0.00f, 0.32f, -0.18f, 5, 12, 85),
            k(0.46f, 0.16f, 0.26f, -0.14f, -45, 4, 85),
            k(0.58f, 0.20f, 0.10f, -0.04f, -75, -5, 70),
            k(0.80f, 0.06f, 0.02f, 0.00f, 10, 40, 25), REST1};
    private static final Key[] SLASH_DIAG = {REST0,
            k(0.14f, 0.12f, 0.24f, 0.06f, -55, 65, -40),
            k(0.26f, 0.06f, 0.28f, -0.06f, -25, 40, -60),
            k(0.36f, -0.10f, 0.20f, -0.18f, 15, 10, -75),
            k(0.46f, -0.26f, 0.06f, -0.12f, 50, -20, -80),
            k(0.58f, -0.30f, 0.00f, -0.02f, 65, -25, -70),
            k(0.80f, -0.10f, 0.00f, 0.02f, 55, 25, -25), REST1};

    // ---- greatsword: the same cuts, a longer wind-up and a heavier follow-through ----
    private static final Key[] HEAVY_FORE = retime(SLASH_FORE);
    private static final Key[] HEAVY_BACK = retime(SLASH_BACK);
    private static final Key[] HEAVY_DIAG = retime(SLASH_DIAG);

    // ---- axe: an overhead chop straight down the middle ----
    private static final Key[] CHOP_OVER = {REST0,
            k(0.18f, -0.08f, 0.30f, 0.10f, 25, 95, 0),
            k(0.34f, -0.18f, 0.32f, -0.04f, 15, 55, 0),
            k(0.44f, -0.36f, 0.24f, -0.20f, 8, 8, 0),
            k(0.54f, -0.40f, 0.12f, -0.16f, 8, -22, 0),
            k(0.66f, -0.36f, 0.08f, -0.08f, 8, -18, 0),
            k(0.85f, -0.08f, 0.00f, 0.00f, 35, 35, 0), REST1};

    // ---- hammer: an overhead slam that ends in the ground, and an uppercut ----
    private static final Key[] SLAM_OVER = {REST0,
            k(0.22f, -0.08f, 0.34f, 0.12f, 25, 100, 0),
            k(0.38f, -0.18f, 0.34f, -0.02f, 15, 55, 0),
            k(0.48f, -0.36f, 0.24f, -0.20f, 8, 2, 0),
            k(0.56f, -0.40f, 0.10f, -0.18f, 8, -30, 0),
            k(0.72f, -0.36f, 0.06f, -0.10f, 8, -26, 0),
            k(0.88f, -0.08f, 0.00f, 0.00f, 35, 35, 0), REST1};
    private static final Key[] SLAM_UPPER = {REST0,
            k(0.22f, 0.00f, 0.00f, 0.06f, 15, -40, 0),
            k(0.38f, -0.12f, 0.12f, -0.12f, 10, -5, 0),
            k(0.48f, -0.22f, 0.28f, -0.20f, 10, 40, 0),
            k(0.58f, -0.22f, 0.34f, -0.12f, 15, 80, 0),
            k(0.80f, -0.06f, 0.10f, 0.00f, 40, 60, 0), REST1};

    // ---- scythe: wide reaping pulls with the haft kept up so the blade hooks round, and a downward reap ----
    private static final Key[] REAP_FORE = {REST0,
            k(0.16f, 0.14f, 0.16f, 0.08f, -85, 45, -20),
            k(0.30f, 0.08f, 0.22f, -0.08f, -45, 30, -45),
            k(0.42f, -0.12f, 0.24f, -0.18f, 15, 25, -55),
            k(0.54f, -0.36f, 0.16f, -0.06f, 65, 25, -55),
            k(0.66f, -0.38f, 0.08f, 0.04f, 95, 35, -40),
            k(0.85f, -0.12f, 0.02f, 0.02f, 60, 45, -10), REST1};
    private static final Key[] REAP_BACK = {REST0,
            k(0.16f, -0.22f, 0.16f, 0.06f, 105, 45, 20),
            k(0.30f, -0.16f, 0.22f, -0.08f, 65, 30, 45),
            k(0.42f, 0.02f, 0.24f, -0.18f, 5, 25, 55),
            k(0.54f, 0.20f, 0.16f, -0.06f, -50, 25, 55),
            k(0.66f, 0.20f, 0.08f, 0.04f, -75, 35, 40),
            k(0.85f, 0.04f, 0.02f, 0.02f, 0, 45, 10), REST1};

    // ---- lance: real stabs, straight and high ----
    private static final Key[] THRUST_STRAIGHT = {REST0,
            k(0.16f, 0.00f, 0.18f, 0.18f, 12, 6, 0),
            k(0.30f, -0.20f, 0.24f, -0.50f, 8, 5, 0),
            k(0.42f, -0.22f, 0.24f, -0.60f, 8, 5, 0),
            k(0.60f, -0.10f, 0.12f, -0.20f, 15, 15, 0),
            k(0.85f, -0.02f, 0.02f, 0.00f, 35, 45, 0), REST1};
    private static final Key[] THRUST_HIGH = {REST0,
            k(0.16f, 0.00f, 0.22f, 0.18f, 12, 15, 0),
            k(0.30f, -0.20f, 0.34f, -0.50f, 8, 18, 0),
            k(0.42f, -0.22f, 0.34f, -0.60f, 8, 18, 0),
            k(0.60f, -0.10f, 0.16f, -0.20f, 15, 25, 0),
            k(0.85f, -0.02f, 0.02f, 0.00f, 35, 45, 0), REST1};

    /** The same keys on a heavier rhythm: a longer wind-up, the strike a little later, a slower follow-through. */
    private static Key[] retime(Key[] keys) {
        float[] from = {0, 0.14f, 0.26f, 0.36f, 0.46f, 0.58f, 0.80f, 1};
        float[] to = {0, 0.20f, 0.32f, 0.42f, 0.52f, 0.66f, 0.85f, 1};
        Key[] out = new Key[keys.length];
        for (int i = 0; i < keys.length; i++) {
            Key key = keys[i];
            float s = key.s();
            for (int j = 0; j < from.length; j++) if (Math.abs(from[j] - s) < 1e-4f) s = to[j];
            out[i] = new Key(s, key.x() * 1.1f, key.y(), key.z(), key.yaw(), key.elev(), key.roll());
        }
        return out;
    }

    private FirstPersonAttacks() {
    }

    /** DEVELOPMENT ONLY: a fixed pose {x, y, z, blade yaw, elevation, roll} held instead of the idle one (null: off). */
    public static float[] debugPose;

    /** Applies {@link #debugPose} if set. */
    public static void debug(PoseStack pose, int side) {
        float[] d = debugPose;
        if (d == null) return;
        pose.translate(d[0] * side, d[1], d[2]);
        orient(pose, d[3], d[4], d[5], side);
    }

    private static Key[] motion(WeaponClass.SwingStyle style, int variant) {
        return switch (style) {
            case SLASH -> variant == 1 ? SLASH_BACK : variant == 2 ? SLASH_DIAG : SLASH_FORE;
            case HEAVY_SLASH -> variant == 1 ? HEAVY_BACK : variant == 2 ? HEAVY_DIAG : HEAVY_FORE;
            case CHOP -> variant == 1 ? HEAVY_FORE : variant == 2 ? HEAVY_DIAG : CHOP_OVER;
            case SLAM -> variant == 1 ? HEAVY_FORE : variant == 2 ? SLAM_UPPER : SLAM_OVER;
            case REAP -> variant == 1 ? REAP_BACK : variant == 2 ? CHOP_OVER : REAP_FORE;
            case THRUST -> variant == 1 ? SLASH_FORE : variant == 2 ? THRUST_HIGH : THRUST_STRAIGHT;
        };
    }

    /**
     * Applies attack {@code variant} of {@code style} at progress {@code s} (0..1) to the hand pose. {@code side} is 1
     * for the right hand (-1 mirrors it); {@code heavy} above 1 swings a little wider.
     */
    public static void apply(PoseStack pose, WeaponClass.SwingStyle style, int variant, int side, float heavy, float s) {
        Key[] keys = motion(style, variant);
        s = Math.max(0, Math.min(1, s));
        int i = 0;
        while (i < keys.length - 2 && s > keys[i + 1].s()) i++;
        Key k0 = keys[Math.max(0, i - 1)], k1 = keys[i], k2 = keys[i + 1], k3 = keys[Math.min(keys.length - 1, i + 2)];
        float u = (s - k1.s()) / Math.max(1e-4f, k2.s() - k1.s());
        float x = cr(k0.x(), k1.x(), k2.x(), k3.x(), u), y = cr(k0.y(), k1.y(), k2.y(), k3.y(), u), z = cr(k0.z(), k1.z(), k2.z(), k3.z(), u);
        float wide = 1 + (heavy - 1) * 0.4f;
        float yaw = cr(yaw(k0), yaw(k1), yaw(k2), yaw(k3), u);
        yaw = IDLE_YAW + (yaw - IDLE_YAW) * wide;
        float elev = cr(elev(k0), elev(k1), elev(k2), elev(k3), u);
        float roll = cr(k0.roll(), k1.roll(), k2.roll(), k3.roll(), u);
        pose.translate(x * side, y, z);
        orient(pose, yaw, elev, roll, side);
    }

    /** Turns the weapon about the grip from its resting direction onto (yaw, elev), rolled about itself by roll. */
    private static void orient(PoseStack pose, float yaw, float elev, float roll, int side) {
        Vector3f rest = new Vector3f(REST_DIR.x * side, REST_DIR.y, REST_DIR.z);
        double cy = Math.toRadians(yaw * side), ce = Math.toRadians(elev);
        Vector3f dir = new Vector3f((float) (-Math.sin(cy) * Math.cos(ce)), (float) Math.sin(ce), (float) (-Math.cos(cy) * Math.cos(ce))).normalize();
        Quaternionf turn = new Quaternionf().rotationTo(rest, dir);
        Quaternionf spin = new Quaternionf().rotationAxis((float) Math.toRadians(roll * side), dir.x, dir.y, dir.z);
        pose.mulPose(spin);
        pose.mulPose(turn);
    }

    /**
     * Which way the blade points in the hand at rest: model up turned by the weapons' first-person display rotation
     * (-35, 20, 25), as {@code ItemTransform} applies it (X, then Y, then Z, about the model's own axes).
     */
    private static final Vector3f REST_DIR = new Quaternionf().rotationXYZ((float) Math.toRadians(-35), (float) Math.toRadians(20), (float) Math.toRadians(25))
            .transform(new Vector3f(0, 1, 0)).normalize();
    private static final float IDLE_YAW = (float) Math.toDegrees(Math.atan2(-REST_DIR.x, -REST_DIR.z));
    private static final float IDLE_ELEV = (float) Math.toDegrees(Math.asin(REST_DIR.y));

    private static float yaw(Key k) {
        return Float.isNaN(k.yaw()) ? IDLE_YAW : k.yaw();
    }

    private static float elev(Key k) {
        return Float.isNaN(k.elev()) ? IDLE_ELEV : k.elev();
    }

    /** Catmull-Rom through p1 (u = 0) and p2 (u = 1). */
    private static float cr(float p0, float p1, float p2, float p3, float u) {
        float u2 = u * u, u3 = u2 * u;
        return 0.5f * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }
}
