package com.terrahaul.devtools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.terrahaul.worldgen.density.CaveGuards;
import com.terrahaul.worldgen.density.TerrainDetail;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The noise fill of a Terrahaul world, offline: the final density of the overworld noise router
 * evaluated block by block as the noise chunk does, so the terrain the devtools look at has the
 * router's caves (cave entrances, spaghetti and noodle tunnels, cheese caverns, pillars) with the
 * cave guards of {@link CaveGuards}, not only the 3D terrain of {@link TerrainDetail}.
 * <p>
 * The router is read from the mod's noise settings and density function files and the vanilla
 * ones they refer to (on the devtools' classpath), and interpreted: the noises are seeded from the
 * world seed as {@code RandomState} seeds them, {@code minecraft:interpolated} interpolates its
 * argument over the cells of the noise grid (four blocks across, eight high, in the noise chunk's
 * order: up, then along x, then along z) and the {@code terrahaul:terrain} outputs come from the
 * world's terrain model. Blocks are solid where the final density is positive (the aquifer then
 * keeps the default block). Not covered: the vanilla carvers, aquifer fluids, structures and the
 * features that run after the noise fill.
 * <p>
 * Thread-safe: noises and the expression tree are built up front, and every interpolation keeps
 * its cell corners in a per-thread cache.
 */
final class RouterTerrain {
    /** A density function of block coordinates. */
    interface Fn {
        double at(int x, int y, int z);
    }

    private final TerrainModel model;
    private final PositionalRandomFactory random;
    private final int minY;
    private final int cellWidth;
    private final int cellHeight;
    private final Map<String, Fn> refs = new HashMap<>();
    private final Map<String, NormalNoise> noises = new HashMap<>();
    private final Fn finalDensity;

    RouterTerrain(long worldSeed, TerrainModel model) {
        this.model = model;
        JsonObject settings = json("data/terrahaul/worldgen/noise_settings/overworld.json").getAsJsonObject();
        if (settings.has("legacy_random_source") && settings.get("legacy_random_source").getAsBoolean()) {
            throw new IllegalStateException("the router uses the legacy random source");
        }
        JsonObject noise = settings.getAsJsonObject("noise");
        minY = noise.get("min_y").getAsInt();
        cellWidth = 4 * noise.get("size_horizontal").getAsInt();
        cellHeight = 4 * noise.get("size_vertical").getAsInt();
        random = new XoroshiroRandomSource(worldSeed).forkPositional();
        finalDensity = build(settings.getAsJsonObject("noise_router").get("final_density"));
    }

    /** Whether the noise fill leaves solid ground at a block. */
    boolean solid(int x, int y, int z) {
        return finalDensity.at(x, y, z) > 0.0;
    }

    /** The final density at a block. */
    double density(int x, int y, int z) {
        return finalDensity.at(x, y, z);
    }

    // =========================================================================================
    // the expression tree
    // =========================================================================================

    private static JsonElement json(String path) {
        try (InputStream in = RouterTerrain.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("not on the classpath: " + path + " (the devtools need the mod's and Minecraft's resources)");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String path(String name, String kind) {
        String[] p = name.split(":", 2);
        return p.length == 1 ? "data/minecraft/worldgen/" + kind + "/" + p[0] + ".json" : "data/" + p[0] + "/worldgen/" + kind + "/" + p[1] + ".json";
    }

    private Fn ref(String name) {
        Fn f = refs.get(name);
        if (f == null) {
            f = build(json(path(name, "density_function")));
            refs.put(name, f);
        }
        return f;
    }

    /** A noise seeded as {@code RandomState} seeds it: from the hash of its registry name. */
    static NormalNoise noise(PositionalRandomFactory random, String name) {
        String full = name.contains(":") ? name : "minecraft:" + name;
        JsonObject o = json(path(full, "noise")).getAsJsonObject();
        JsonArray amps = o.getAsJsonArray("amplitudes");
        double[] a = new double[amps.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = amps.get(i).getAsDouble();
        }
        return NormalNoise.create(random.fromHashOf(full), o.get("firstOctave").getAsInt(), a);
    }

    private NormalNoise noise(String name) {
        return noises.computeIfAbsent(name, n -> noise(random, n));
    }

    private Fn arg(JsonObject o, String key) {
        return build(o.get(key));
    }

    private Fn build(JsonElement e) {
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isNumber()) {
                double v = p.getAsDouble();
                return (x, y, z) -> v;
            }
            return ref(p.getAsString());
        }
        JsonObject o = e.getAsJsonObject();
        String type = o.get("type").getAsString();
        switch (type) {
            case "minecraft:add" -> {
                Fn a = arg(o, "argument1");
                Fn b = arg(o, "argument2");
                return (x, y, z) -> a.at(x, y, z) + b.at(x, y, z);
            }
            case "minecraft:mul" -> {
                Fn a = arg(o, "argument1");
                Fn b = arg(o, "argument2");
                return (x, y, z) -> {
                    double v = a.at(x, y, z);
                    return v == 0.0 ? 0.0 : v * b.at(x, y, z);
                };
            }
            case "minecraft:min" -> {
                Fn a = arg(o, "argument1");
                Fn b = arg(o, "argument2");
                return (x, y, z) -> Math.min(a.at(x, y, z), b.at(x, y, z));
            }
            case "minecraft:max" -> {
                Fn a = arg(o, "argument1");
                Fn b = arg(o, "argument2");
                return (x, y, z) -> Math.max(a.at(x, y, z), b.at(x, y, z));
            }
            case "minecraft:clamp" -> {
                Fn a = arg(o, "input");
                double lo = o.get("min").getAsDouble();
                double hi = o.get("max").getAsDouble();
                return (x, y, z) -> Mth.clamp(a.at(x, y, z), lo, hi);
            }
            case "minecraft:abs" -> {
                Fn a = arg(o, "argument");
                return (x, y, z) -> Math.abs(a.at(x, y, z));
            }
            case "minecraft:square" -> {
                Fn a = arg(o, "argument");
                return (x, y, z) -> {
                    double v = a.at(x, y, z);
                    return v * v;
                };
            }
            case "minecraft:cube" -> {
                Fn a = arg(o, "argument");
                return (x, y, z) -> {
                    double v = a.at(x, y, z);
                    return v * v * v;
                };
            }
            case "minecraft:half_negative" -> {
                Fn a = arg(o, "argument");
                return (x, y, z) -> {
                    double v = a.at(x, y, z);
                    return v > 0.0 ? v : v * 0.5;
                };
            }
            case "minecraft:quarter_negative" -> {
                Fn a = arg(o, "argument");
                return (x, y, z) -> {
                    double v = a.at(x, y, z);
                    return v > 0.0 ? v : v * 0.25;
                };
            }
            case "minecraft:squeeze" -> {
                Fn a = arg(o, "argument");
                return (x, y, z) -> {
                    double v = Mth.clamp(a.at(x, y, z), -1.0, 1.0);
                    return v / 2.0 - v * v * v / 24.0;
                };
            }
            case "minecraft:cache_once", "minecraft:cache_2d", "minecraft:cache_all_in_cell", "minecraft:blend_density" -> {
                // caches only; no blending in freshly generated chunks
                return arg(o, "argument");
            }
            case "minecraft:flat_cache" -> {
                // the noise chunk fills it at the quart positions of the column, at y 0
                Fn a = arg(o, "argument");
                return (x, y, z) -> a.at(x & ~3, 0, z & ~3);
            }
            case "minecraft:interpolated" -> {
                return new Interpolated(arg(o, "argument"));
            }
            case "terrahaul:column_bounded" -> {
                return arg(o, "argument");
            }
            case "minecraft:noise" -> {
                NormalNoise n = noise(o.get("noise").getAsString());
                double xz = o.get("xz_scale").getAsDouble();
                double ys = o.get("y_scale").getAsDouble();
                return (x, y, z) -> n.getValue(x * xz, y * ys, z * xz);
            }
            case "minecraft:weird_scaled_sampler" -> {
                Fn in = arg(o, "input");
                NormalNoise n = noise(o.get("noise").getAsString());
                boolean type1 = o.get("rarity_value_mapper").getAsString().equals("type_1");
                return (x, y, z) -> {
                    double v = in.at(x, y, z);
                    // NoiseRouterData.QuantizedSpaghettiRarity: 3D (type 1) and 2D (type 2) rarity
                    double r = type1 ? (v < -0.5 ? 0.75 : v < 0.0 ? 1.0 : v < 0.5 ? 1.5 : 2.0)
                        : (v < -0.75 ? 0.5 : v < -0.5 ? 0.75 : v < 0.5 ? 1.0 : v < 0.75 ? 2.0 : 3.0);
                    return r * Math.abs(n.getValue(x / r, y / r, z / r));
                };
            }
            case "minecraft:y_clamped_gradient" -> {
                int fromY = o.get("from_y").getAsInt();
                int toY = o.get("to_y").getAsInt();
                double fromValue = o.get("from_value").getAsDouble();
                double toValue = o.get("to_value").getAsDouble();
                return (x, y, z) -> Mth.clampedMap(y, fromY, toY, fromValue, toValue);
            }
            case "minecraft:range_choice" -> {
                Fn in = arg(o, "input");
                Fn inside = arg(o, "when_in_range");
                Fn outside = arg(o, "when_out_of_range");
                double lo = o.get("min_inclusive").getAsDouble();
                double hi = o.get("max_exclusive").getAsDouble();
                return (x, y, z) -> {
                    double v = in.at(x, y, z);
                    return v >= lo && v < hi ? inside.at(x, y, z) : outside.at(x, y, z);
                };
            }
            case "terrahaul:terrain" -> {
                return terrain(o.get("output").getAsString());
            }
            default -> throw new IllegalArgumentException("density function type not interpreted: " + type);
        }
    }

    /** The outputs of {@code TerrainDensityFunction} the final density uses, from the same code. */
    private Fn terrain(String output) {
        return switch (output) {
            case "density" -> (x, y, z) -> TerrainDetail.density(model, model.sample(x, z), x, y, z);
            // TerrainDensityFunction.SLOPE: an eighth per block
            case "preliminary" -> (x, y, z) -> (model.sample(x, z).height - y) * 0.125;
            case "surface_height" -> (x, y, z) -> model.sample(x, z).height;
            // the guards use the interpolation grid's columns, as in the game
            case "cave_allowance" -> (x, y, z) -> CaveGuards.caveAllowance(model, x & ~3, y, z & ~3);
            case "cavern_allowance" -> (x, y, z) -> Math.min(CaveGuards.caveAllowance(model, x & ~3, y, z & ~3),
                CaveGuards.cavernAllowance(model, x & ~3, y, z & ~3));
            default -> throw new IllegalArgumentException("terrain output not interpreted: " + output);
        };
    }

    /**
     * {@code minecraft:interpolated}: its argument at the eight corners of the noise cell holding
     * the block, interpolated as the noise chunk does it.
     */
    private final class Interpolated implements Fn {
        private static final int BITS = 15;
        private final Fn argument;
        private final ThreadLocal<long[]> keys = ThreadLocal.withInitial(() -> {
            long[] k = new long[1 << BITS];
            Arrays.fill(k, Long.MIN_VALUE);
            return k;
        });
        private final ThreadLocal<double[]> values = ThreadLocal.withInitial(() -> new double[1 << BITS]);

        Interpolated(Fn argument) {
            this.argument = argument;
        }

        private double corner(long[] k, double[] v, int x, int y, int z) {
            // corner indices, 21 bits each; the key never reaches the empty marker's sign bit
            long key = ((long) Math.floorDiv(x, cellWidth) & 0x1FFFFFL) << 42 | ((long) Math.floorDiv(z, cellWidth) & 0x1FFFFFL) << 21
                | (long) Math.floorDiv(y - minY, cellHeight) & 0x1FFFFFL;
            int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> (64 - BITS));
            if (k[slot] == key) {
                return v[slot];
            }
            double d = argument.at(x, y, z);
            k[slot] = key;
            v[slot] = d;
            return d;
        }

        @Override
        public double at(int x, int y, int z) {
            int x0 = Math.floorDiv(x, cellWidth) * cellWidth;
            int z0 = Math.floorDiv(z, cellWidth) * cellWidth;
            int y0 = Math.floorDiv(y - minY, cellHeight) * cellHeight + minY;
            int x1 = x0 + cellWidth;
            int y1 = y0 + cellHeight;
            int z1 = z0 + cellWidth;
            long[] k = keys.get();
            double[] v = values.get();
            double fx = (double) (x - x0) / cellWidth;
            double fy = (double) (y - y0) / cellHeight;
            double fz = (double) (z - z0) / cellWidth;
            // NoiseChunk.NoiseInterpolator: along y first, then x, then z
            double xz00 = Mth.lerp(fy, corner(k, v, x0, y0, z0), corner(k, v, x0, y1, z0));
            double xz10 = Mth.lerp(fy, corner(k, v, x1, y0, z0), corner(k, v, x1, y1, z0));
            double xz01 = Mth.lerp(fy, corner(k, v, x0, y0, z1), corner(k, v, x0, y1, z1));
            double xz11 = Mth.lerp(fy, corner(k, v, x1, y0, z1), corner(k, v, x1, y1, z1));
            double z0v = Mth.lerp(fx, xz00, xz10);
            double z1v = Mth.lerp(fx, xz01, xz11);
            return Mth.lerp(fz, z0v, z1v);
        }
    }
}
