package com.fantasyweapons.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.HashMap;
import java.util.Map;

/**
 * Records where Infernochain's blade segments were actually drawn this frame (world space), straight from the
 * GeckoLib render of the held weapon — so fire trails and flames follow the real, animated chain (transform, whip,
 * retract) instead of a guess. Points 0..6 are the pivots of {@code segment_0..6}; point 7 is the blade's tip.
 */
public final class ChainTracker {
    public static final int POINTS = 8;
    /** Tip of {@code segment_6} in model space (blocks). */
    private static final float TIP_Y = 61.4f / 16f;

    public static final class Sample {
        public final Vec3[] pts = new Vec3[POINTS];
        /** First person only: the same points in view space (they move with the camera). */
        public final Vec3[] view = new Vec3[POINTS];
        public boolean firstPerson;
        public long nanos;
        int found;
    }

    private static final Map<Integer, Sample> SAMPLES = new HashMap<>();
    @Nullable
    private static Sample building;
    private static int buildingId = -1;
    private static boolean firstPerson;

    private ChainTracker() {
    }

    static void begin(LivingEntity entity, boolean fp) {
        building = new Sample();
        building.firstPerson = fp;
        buildingId = entity.getId();
        firstPerson = fp;
    }

    static boolean active() {
        return building != null;
    }

    static void bone(GeoBone bone, Matrix4f pose) {
        Sample s = building;
        if (s == null) return;
        String name = bone.getName();
        if (!name.startsWith("segment_")) return;
        int i;
        try {
            i = Integer.parseInt(name.substring(8));
        } catch (NumberFormatException e) {
            return;
        }
        if (i < 0 || i > 6) return;
        if (s.pts[i] == null) s.found++;
        Vector3f v = pose.transformPosition(new Vector3f(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f));
        s.view[i] = new Vec3(v.x, v.y, v.z);
        s.pts[i] = toWorld(v);
        if (i == 6) {
            Vector3f tip = pose.transformPosition(new Vector3f(bone.getPivotX() / 16f, TIP_Y, bone.getPivotZ() / 16f));
            s.view[7] = new Vec3(tip.x, tip.y, tip.z);
            s.pts[7] = toWorld(tip);
        }
    }

    static void end() {
        Sample s = building;
        building = null;
        if (s == null || s.found < 7 || s.pts[7] == null) return;
        s.nanos = System.nanoTime();
        SAMPLES.put(buildingId, s);
    }

    /** A first-person view-space point placed in the world through the camera as it is now. */
    public static Vec3 viewToWorld(Vec3 v) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f p = new Vector3f((float) v.x, (float) v.y, (float) v.z);
        cam.rotation().transform(p);
        return cam.getPosition().add(p.x, p.y, p.z);
    }

    /** Render space → world: third-person item poses are camera-relative and world-aligned, first-person ones are in view space. */
    private static Vec3 toWorld(Vector3f p) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (firstPerson) cam.rotation().transform(p);
        return cam.getPosition().add(p.x, p.y, p.z);
    }

    /** The latest sample for an entity if it is fresh (drawn within the last {@code maxMillis}). */
    @Nullable
    public static Sample get(int entityId, long maxMillis) {
        Sample s = SAMPLES.get(entityId);
        return s == null || System.nanoTime() - s.nanos > maxMillis * 1_000_000L ? null : s;
    }

    public static void clear() {
        SAMPLES.clear();
    }
}
