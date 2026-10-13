package com.fantasyweapons.client.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The bounding box of a GeckoLib model's cubes, in model pixels (read from the geo file on the classpath), with x turned
 * the way model space runs. Used to fit the artist's models to a block, a slot or a hand.
 */
public record GeoBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, List<float[]> corners) {
    public static GeoBounds of(String path) {
        return of(path, b -> true);
    }

    /** Bounds of only the cubes under the given top-level bones. */
    public static GeoBounds of(String path, Collection<String> roots) {
        return of(path, roots::contains, true);
    }

    /** Bounds of the cubes of the bones {@code keep} accepts, together with all of their ancestors. */
    public static GeoBounds of(String path, Predicate<String> keep) {
        return of(path, keep, false);
    }

    /**
     * @param keep      which bones count, by name
     * @param rootsOnly whether {@code keep} is asked only about each bone's top-level ancestor, rather than about the
     *                  bone and every ancestor (all of which must be kept)
     */
    private static GeoBounds of(String path, Predicate<String> keep, boolean rootsOnly) {
        try (InputStream in = GeoBounds.class.getResourceAsStream("/assets/fantasyweapons/" + path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            JsonObject geo = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            Map<String, String> parent = new HashMap<>();
            for (JsonElement b : geo.getAsJsonArray("bones")) {
                JsonObject o = b.getAsJsonObject();
                if (o.has("parent")) parent.put(o.get("name").getAsString(), o.get("parent").getAsString());
            }
            List<float[]> corners = new ArrayList<>();
            for (JsonElement b : geo.getAsJsonArray("bones")) {
                if (!b.getAsJsonObject().has("cubes")) continue;
                String name = b.getAsJsonObject().get("name").getAsString();
                if (!kept(name, parent, keep, rootsOnly)) continue;
                for (JsonElement c : b.getAsJsonObject().getAsJsonArray("cubes")) {
                    JsonObject cube = c.getAsJsonObject();
                    var o = cube.getAsJsonArray("origin");
                    var s = cube.getAsJsonArray("size");
                    float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0;
                    float[] a = new float[3], z = new float[3];
                    for (int i = 0; i < 3; i++) {
                        a[i] = o.get(i).getAsFloat() - inflate;
                        z[i] = o.get(i).getAsFloat() + s.get(i).getAsFloat() + inflate;
                        lo[i] = Math.min(lo[i], a[i]);
                        hi[i] = Math.max(hi[i], z[i]);
                    }
                    for (int k = 0; k < 8; k++) {
                        corners.add(new float[]{-((k & 1) == 0 ? a[0] : z[0]), (k & 2) == 0 ? a[1] : z[1], (k & 4) == 0 ? a[2] : z[2]});
                    }
                }
            }
            if (corners.isEmpty()) throw new IllegalStateException("no cubes in " + path);
            // geo x runs the other way to model space
            return new GeoBounds(-hi[0], lo[1], lo[2], -lo[0], hi[1], hi[2], List.copyOf(corners));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean kept(String bone, Map<String, String> parent, Predicate<String> keep, boolean rootsOnly) {
        if (rootsOnly) {
            String root = bone;
            while (parent.containsKey(root)) root = parent.get(root);
            return keep.test(root);
        }
        for (String b = bone; b != null; b = parent.get(b)) if (!keep.test(b)) return false;
        return true;
    }

    /** These bounds moved by (dx, dy, dz) pixels (where an animation holds the model off its origin). */
    public GeoBounds shifted(float dx, float dy, float dz) {
        List<float[]> moved = new ArrayList<>(corners.size());
        for (float[] c : corners) moved.add(new float[]{c[0] + dx, c[1] + dy, c[2] + dz});
        return new GeoBounds(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz, List.copyOf(moved));
    }

    public float sizeX() {
        return maxX - minX;
    }

    public float sizeY() {
        return maxY - minY;
    }

    public float sizeZ() {
        return maxZ - minZ;
    }

    public float maxSize() {
        return Math.max(sizeX(), Math.max(sizeY(), sizeZ()));
    }

    public float centerX() {
        return (minX + maxX) / 2;
    }

    public float centerY() {
        return (minY + maxY) / 2;
    }

    public float centerZ() {
        return (minZ + maxZ) / 2;
    }

    /**
     * The model's extent on screen when turned like an item display (rotations in degrees, as Minecraft applies them):
     * {width, height, centre x, centre y, centre depth} in model pixels, from every cube corner.
     */
    public float[] projected(float pitch, float yaw, float roll) {
        org.joml.Quaternionf rot = new org.joml.Quaternionf().rotationXYZ((float) Math.toRadians(pitch), (float) Math.toRadians(yaw),
                (float) Math.toRadians(roll));
        float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z0 = Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        org.joml.Vector3f v = new org.joml.Vector3f();
        for (float[] p : corners) {
            rot.transform(v.set(p[0], p[1], p[2]));
            x0 = Math.min(x0, v.x);
            x1 = Math.max(x1, v.x);
            y0 = Math.min(y0, v.y);
            y1 = Math.max(y1, v.y);
            z0 = Math.min(z0, v.z);
            z1 = Math.max(z1, v.z);
        }
        return new float[]{x1 - x0, y1 - y0, (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2};
    }
}
