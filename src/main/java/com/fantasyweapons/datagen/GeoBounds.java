package com.fantasyweapons.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** The bounding box of a GeckoLib model's cubes, in model pixels (read from the geo file on the classpath). */
record GeoBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    static GeoBounds of(String path) {
        return of(path, null);
    }

    /** Bounds of only the cubes under the given top-level bones (all cubes when {@code roots} is null). */
    static GeoBounds of(String path, java.util.Collection<String> roots) {
        try (InputStream in = GeoBounds.class.getResourceAsStream("/assets/fantasyweapons/" + path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            JsonObject geo = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            java.util.Map<String, String> parent = new java.util.HashMap<>();
            for (JsonElement b : geo.getAsJsonArray("bones")) {
                JsonObject o = b.getAsJsonObject();
                if (o.has("parent")) parent.put(o.get("name").getAsString(), o.get("parent").getAsString());
            }
            for (JsonElement b : geo.getAsJsonArray("bones")) {
                if (!b.getAsJsonObject().has("cubes")) continue;
                if (roots != null) {
                    String root = b.getAsJsonObject().get("name").getAsString();
                    while (parent.containsKey(root)) root = parent.get(root);
                    if (!roots.contains(root)) continue;
                }
                for (JsonElement c : b.getAsJsonObject().getAsJsonArray("cubes")) {
                    var o = c.getAsJsonObject().getAsJsonArray("origin");
                    var s = c.getAsJsonObject().getAsJsonArray("size");
                    for (int i = 0; i < 3; i++) {
                        lo[i] = Math.min(lo[i], o.get(i).getAsFloat());
                        hi[i] = Math.max(hi[i], o.get(i).getAsFloat() + s.get(i).getAsFloat());
                    }
                }
            }
            // geo x runs the other way to model space
            return new GeoBounds(-hi[0], lo[1], lo[2], -lo[0], hi[1], hi[2]);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    float sizeX() {
        return maxX - minX;
    }

    float sizeY() {
        return maxY - minY;
    }

    float sizeZ() {
        return maxZ - minZ;
    }

    float maxSize() {
        return Math.max(sizeX(), Math.max(sizeY(), sizeZ()));
    }

    float centerX() {
        return (minX + maxX) / 2;
    }

    float centerY() {
        return (minY + maxY) / 2;
    }

    float centerZ() {
        return (minZ + maxZ) / 2;
    }
}
