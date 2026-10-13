package com.fantasyweapons.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The solid shape of a machine, read from the artist's GeckoLib model (the geo file is in the mod jar on both sides): the
 * cubes of its still bones, turned and placed as GeckoLib draws them and widened to whole half pixels. Specks and the
 * moving parts (floating stars, sparks, flames) are left out. {@link #cell} cuts out what lies inside one block of the machine,
 * as boxes merged along the half-pixel grid, so each block's outline and collision follow the model's edges.
 */
public final class ModelShape {
    /** Half pixels per block. */
    private static final int RES = 32;
    /** Cubes thinner than this (in pixels) are left out: specks too small to aim at. */
    private static final float MIN_THICKNESS = 0.3f;

    private ModelShape() {
    }

    /**
     * The model's boxes, facing north in the core's block coordinates (the model's origin is the bottom centre of the core),
     * from the cubes of {@code bones} (by name).
     */
    public static List<AABB> boxes(String model, Set<String> bones) {
        JsonObject geo = read("/assets/fantasyweapons/geo/block/" + model + ".geo.json");
        Map<String, JsonObject> byName = new HashMap<>();
        for (JsonElement b : geo.getAsJsonArray("bones")) byName.put(b.getAsJsonObject().get("name").getAsString(), b.getAsJsonObject());
        List<AABB> boxes = new ArrayList<>();
        for (String name : bones) {
            JsonObject bone = byName.get(name);
            if (bone == null) throw new IllegalStateException(model + " has no bone " + name);
            if (!bone.has("cubes")) continue;
            for (JsonElement c : bone.getAsJsonArray("cubes")) {
                AABB box = cube(c.getAsJsonObject(), bone, byName);
                if (box != null) boxes.add(box);
            }
        }
        return boxes;
    }

    /** One cube as GeckoLib places it (x mirrored, turned about its pivot and its bones'), in the core's block coordinates. */
    private static AABB cube(JsonObject cube, JsonObject bone, Map<String, JsonObject> bones) {
        float[] o = floats(cube.getAsJsonArray("origin")), s = floats(cube.getAsJsonArray("size"));
        float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0;
        if (Math.min(s[0], Math.min(s[1], s[2])) + 2 * inflate < MIN_THICKNESS) return null;
        float[] lo = {-(o[0] + s[0]) - inflate, o[1] - inflate, o[2] - inflate}, hi = {-o[0] + inflate, o[1] + s[1] + inflate, o[2] + s[2] + inflate};
        Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE), p = new Vector3f();
        for (int k = 0; k < 8; k++) {
            p.set((k & 1) == 0 ? lo[0] : hi[0], (k & 2) == 0 ? lo[1] : hi[1], (k & 4) == 0 ? lo[2] : hi[2]);
            turn(p, cube);
            for (JsonObject b = bone; b != null; b = b.has("parent") ? bones.get(b.get("parent").getAsString()) : null) turn(p, b);
            min.min(p);
            max.max(p);
        }
        // model pixels about the core's bottom centre -> the core's block coordinates, out to the half-pixel grid (so the
        // shape holds the whole cube and its outline is drawn on or just outside the model, never hidden inside it)
        return new AABB(down(8 + min.x), down(min.y), down(8 + min.z), up(8 + max.x), up(max.y), up(8 + max.z));
    }

    /** Turns {@code p} by a cube's or bone's rotation about its pivot, as GeckoLib does (x mirrored: x and y angles negated; z, y, then x). */
    private static void turn(Vector3f p, JsonObject part) {
        if (!part.has("rotation")) return;
        float[] r = floats(part.getAsJsonArray("rotation"));
        if (r[0] == 0 && r[1] == 0 && r[2] == 0) return;
        float[] pivot = part.has("pivot") ? floats(part.getAsJsonArray("pivot")) : new float[3];
        Vector3f q = new Vector3f(-pivot[0], pivot[1], pivot[2]);
        Matrix3f m = new Matrix3f().rotateZ((float) Math.toRadians(r[2])).rotateY((float) Math.toRadians(-r[1])).rotateX((float) Math.toRadians(-r[0]));
        p.sub(q).mul(m).add(q);
    }

    private static double down(float pixels) {
        return Math.floor(pixels * 2 + 1e-3) / RES;
    }

    private static double up(float pixels) {
        return Math.ceil(pixels * 2 - 1e-3) / RES;
    }

    /**
     * What of {@code boxes} (block coordinates relative to the cell's corner) lies inside the cell from (0, 0, 0) to
     * (1, 1, 1): filled on the half-pixel grid, then merged into as few boxes as the grid allows.
     */
    public static VoxelShape cell(List<AABB> boxes) {
        return cell(boxes, 0, 0, 1, 1);
    }

    /**
     * What of {@code boxes} lies inside the cell's height and between x0..x1 and z0..z1 (which may reach past the cell,
     * where the model hangs over the edge of its machine): filled on the half-pixel grid, merged into boxes.
     */
    public static VoxelShape cell(List<AABB> boxes, double x0, double z0, double x1, double z1) {
        int gx0 = (int) Math.floor(x0 * RES), gz0 = (int) Math.floor(z0 * RES), nx = (int) Math.ceil(x1 * RES) - gx0, nz = (int) Math.ceil(z1 * RES) - gz0;
        boolean[][][] filled = new boolean[nx][RES][nz];
        boolean any = false;
        for (AABB b : boxes) {
            int bx0 = clamp(b.minX, gx0, nx), bx1 = clamp(b.maxX, gx0, nx), y0 = clamp(b.minY, 0, RES), y1 = clamp(b.maxY, 0, RES);
            int bz0 = clamp(b.minZ, gz0, nz), bz1 = clamp(b.maxZ, gz0, nz);
            for (int x = bx0; x < bx1; x++) {
                for (int y = y0; y < y1; y++) {
                    for (int z = bz0; z < bz1; z++) {
                        filled[x][y][z] = true;
                        any = true;
                    }
                }
            }
        }
        if (!any) return Shapes.empty();
        // greedy merge: runs along z, grown along x, then along y
        VoxelShape shape = Shapes.empty();
        for (int y = 0; y < RES; y++) {
            for (int x = 0; x < nx; x++) {
                for (int z = 0; z < nz; z++) {
                    if (!filled[x][y][z]) continue;
                    int z1e = z;
                    while (z1e + 1 < nz && filled[x][y][z1e + 1]) z1e++;
                    int x1e = x;
                    while (x1e + 1 < nx && run(filled, x1e + 1, y, y, z, z1e)) x1e++;
                    int y1e = y;
                    while (y1e + 1 < RES && slab(filled, x, x1e, y1e + 1, z, z1e)) y1e++;
                    for (int xx = x; xx <= x1e; xx++) for (int yy = y; yy <= y1e; yy++) for (int zz = z; zz <= z1e; zz++) filled[xx][yy][zz] = false;
                    shape = Shapes.joinUnoptimized(shape, Shapes.box((gx0 + x) / (double) RES, y / (double) RES, (gz0 + z) / (double) RES,
                            (gx0 + x1e + 1) / (double) RES, (y1e + 1) / (double) RES, (gz0 + z1e + 1) / (double) RES), BooleanOp.OR);
                }
            }
        }
        return shape.optimize();
    }

    private static boolean run(boolean[][][] filled, int x, int y0, int y1, int z0, int z1) {
        for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) if (!filled[x][y][z]) return false;
        return true;
    }

    private static boolean slab(boolean[][][] filled, int x0, int x1, int y, int z0, int z1) {
        for (int x = x0; x <= x1; x++) if (!run(filled, x, y, y, z0, z1)) return false;
        return true;
    }

    /** A coordinate (blocks) as a grid index from {@code origin}, kept within {@code 0..size}. */
    private static int clamp(double v, int origin, int size) {
        return Math.max(0, Math.min(size, (int) Math.round(v * RES) - origin));
    }

    private static float[] floats(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static JsonObject read(String path) {
        try (InputStream in = ModelShape.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
