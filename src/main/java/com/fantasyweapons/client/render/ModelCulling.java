package com.fantasyweapons.client.render;

import com.fantasyweapons.client.perf.Optimisations;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoCube;

/**
 * What the mod's GeckoLib models leave out (see {@link LeanGeoRenderer}), each by its switch in the settings menu's
 * Performance section:
 * <ul>
 *     <li>parts that do not show in the pass being drawn ({@link QuadCull}): clear in the texture, or not glowing in the
 *     glow layer, which otherwise draws the whole model a second time, mostly in invisible pixels;</li>
 *     <li>parts smaller than a pixel on the screen, on models far away (a suit of armor is hundreds of little cubes).</li>
 * </ul>
 */
public final class ModelCulling {
    /** How small on the screen (pixels) a part may be before it is left out. */
    private static final float SMALLEST_PIXELS = 0.75f;
    /** Glow layers being drawn now (they draw the model again, with its glowmask). */
    private static int glowing;
    private static int fovFor = -1;
    private static float pixelsPerUnit;

    private ModelCulling() {
    }

    static void glowBegin() {
        glowing++;
    }

    static void glowEnd() {
        glowing = Math.max(0, glowing - 1);
    }

    /** Whether a glow layer is being drawn. */
    public static boolean inGlow() {
        return glowing > 0;
    }

    /**
     * Which faces of {@code cube}, of a model drawn with {@code texture} (in the glow layer, if {@code glow}), are drawn:
     * bit {@code i} for its {@code i}th face; 0 for none of the cube, -1 for all of it.
     */
    static int faces(@Nullable ResourceLocation texture, boolean glow, Matrix4f pose, GeoCube cube) {
        if (Optimisations.skipTinyDetails() && tooSmall(pose, cube)) return 0;
        if (texture == null || !Optimisations.onlyWhatShows()) return -1;
        return QuadCull.faces(texture, cube, glow);
    }

    /**
     * Whether the cube, drawn with {@code pose} (the camera at its origin), is smaller than {@link #SMALLEST_PIXELS} on
     * the screen. Never on a flat picture (an inventory icon, the player in the inventory): only in the world, seen in
     * perspective.
     */
    static boolean tooSmall(Matrix4f pose, GeoCube cube) {
        // an orthographic projection (the interface) has no perspective term
        if (RenderSystem.getProjectionMatrix().m23() == 0) return false;
        float sx = pose.m00() * pose.m00() + pose.m01() * pose.m01() + pose.m02() * pose.m02();
        float sy = pose.m10() * pose.m10() + pose.m11() * pose.m11() + pose.m12() * pose.m12();
        float sz = pose.m20() * pose.m20() + pose.m21() * pose.m21() + pose.m22() * pose.m22();
        float scale2 = Math.max(sx, Math.max(sy, sz));
        var size = cube.size();
        // the cube's largest side, in blocks (its size is in the model's pixels, sixteen to a block)
        double side = (Math.max(size.x, Math.max(size.y, size.z)) + 2 * cube.inflate()) / 16.0;
        double d2 = (double) pose.m30() * pose.m30() + (double) pose.m31() * pose.m31() + (double) pose.m32() * pose.m32();
        double px = SMALLEST_PIXELS / pixelsPerUnit();
        // side * scale / distance < px, squared
        return side * side * scale2 < px * px * d2;
    }

    /** Screen pixels a block covers one block from the camera (the window's height over the view's height there). */
    private static float pixelsPerUnit() {
        Minecraft mc = Minecraft.getInstance();
        int fov = mc.options.fov().get();
        int height = mc.getWindow().getHeight();
        int key = fov * 100_000 + height;
        if (key != fovFor) {
            fovFor = key;
            pixelsPerUnit = (float) (height / (2 * Math.tan(Math.toRadians(fov) / 2)));
        }
        return pixelsPerUnit;
    }
}
