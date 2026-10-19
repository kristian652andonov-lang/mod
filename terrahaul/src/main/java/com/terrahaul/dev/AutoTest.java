package com.terrahaul.dev;

import com.terrahaul.Terrahaul;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Automated in-game validation, used during development and for the test report. It only runs when
 * the JVM is started with {@code -Dterrahaul.autotest=<output dir>}; otherwise it does nothing.
 * <p>
 * The routine generates chunks at several locations of the Overworld (lowlands, mountains,
 * rivers, coasts), measures generation time, checks the vertical range, lighting and heightmaps,
 * counts floating terrain fragments, renders top-down maps of the generated blocks, writes a text
 * report and stops the server.
 */
public final class AutoTest {
    private AutoTest() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        String out = System.getProperty("terrahaul.autotest");
        if (out == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        Path dir = Path.of(out);
        try {
            Files.createDirectories(dir);
            run(server, dir);
        } catch (Throwable t) {
            Terrahaul.LOGGER.error("Terrahaul autotest failed", t);
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve("autotest-error.txt")))) {
                t.printStackTrace(w);
            } catch (IOException ignored) {
            }
        } finally {
            if (!Boolean.getBoolean("terrahaul.autotest.keepRunning")) {
                server.halt(false);
            }
        }
    }

    private static void run(MinecraftServer server, Path dir) throws IOException {
        ServerLevel level = server.overworld();
        List<String> report = new ArrayList<>();
        report.add("Terrahaul autotest report");
        report.add("dimension type min_y=" + level.getMinBuildHeight() + " max_build=" + level.getMaxBuildHeight()
            + " height=" + level.getHeight() + " sections=" + level.getSectionsCount());
        report.add("generator=" + level.getChunkSource().getGenerator().getClass().getSimpleName()
            + " terrahaul=" + TerrahaulGeneration.isTerrahaul(level.getChunkSource().getGenerator())
            + " genDepth=" + level.getChunkSource().getGenerator().getGenDepth()
            + " minY=" + level.getChunkSource().getGenerator().getMinY()
            + " seaLevel=" + level.getChunkSource().getGenerator().getSeaLevel());
        TerrainModel model = TerrahaulGeneration.model(level);
        report.add("terrain model bound=" + (model != null));

        String spec = System.getProperty("terrahaul.autotest.areas", "0,0,6");
        int areaIndex = 0;
        for (String area : spec.split(";")) {
            String[] p = area.split(",");
            int cx;
            int cz;
            int radius;
            if (p[0].startsWith("find:")) {
                String name = p[0].substring(5);
                radius = Integer.parseInt(p[1].trim());
                com.terrahaul.worldgen.terrain.TerrainFinder.Target target = com.terrahaul.worldgen.terrain.TerrainFinder.targets().get(name);
                long f0 = System.nanoTime();
                com.terrahaul.worldgen.terrain.TerrainFinder.Result found = model == null || target == null ? null
                    : com.terrahaul.worldgen.terrain.TerrainFinder.find(model, target, 0, 0, 30000, () -> false);
                if (found == null) {
                    report.add("area " + areaIndex + ": " + name + " not found");
                    areaIndex++;
                    continue;
                }
                report.add(String.format(Locale.ROOT, "area %d: found %s at %d %d %d in %.0f ms", areaIndex, name, found.x(), found.y(), found.z(), (System.nanoTime() - f0) / 1e6));
                cx = found.x() >> 4;
                cz = found.z() >> 4;
            } else if (p[0].startsWith("biome:")) {
                String id = p[0].substring(6);
                radius = Integer.parseInt(p[1].trim());
                var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.parse(id));
                var found = level.findClosestBiome3d(h -> h.is(key), BlockPos.ZERO, 12000, 32, 64);
                if (found == null) {
                    report.add("area " + areaIndex + ": biome " + id + " not found");
                    areaIndex++;
                    continue;
                }
                report.add("area " + areaIndex + ": biome " + id + " at " + found.getFirst().toShortString());
                cx = found.getFirst().getX() >> 4;
                cz = found.getFirst().getZ() >> 4;
            } else {
                cx = Integer.parseInt(p[0].trim()) >> 4;
                cz = Integer.parseInt(p[1].trim()) >> 4;
                radius = Integer.parseInt(p[2].trim());
            }
            long t0 = System.nanoTime();
            int count = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    level.getChunk(cx + dx, cz + dz, ChunkStatus.FULL, true);
                    count++;
                }
            }
            long t1 = System.nanoTime();
            report.add(String.format(Locale.ROOT, "area %d at chunk (%d,%d) r=%d: %d chunks in %.1f s (%.1f ms/chunk incl. neighbours)",
                areaIndex, cx, cz, radius, count, (t1 - t0) / 1e9, (t1 - t0) / 1e6 / count));
            describeArea(level, model, cx, cz, radius, report);
            renderArea(level, cx, cz, radius, dir.resolve("area" + areaIndex + ".png").toFile());
            renderSection(level, cx, cz, radius, dir.resolve("section" + areaIndex + ".png").toFile());
            dumpRiverColumn(level, model, cx, cz, radius, report);
            oreHistogram(level, cx, cz, radius, report);
            surfaceCensus(level, cx, cz, radius, report);
            floatingFragments(level, cx, cz, radius, report);
            waterWalls(level, cx, cz, radius, report);
            TreeDump.audit(level, cx, cz, radius, report);
            areaIndex++;
        }
        // -Dterrahaul.autotest.biomeFeatures=<biome id>: the features the biome holds after biome modifiers
        String biomeFeatures = System.getProperty("terrahaul.autotest.biomeFeatures", "");
        if (!biomeFeatures.isEmpty()) {
            var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            var biome = biomes.get(net.minecraft.resources.ResourceLocation.parse(biomeFeatures));
            if (biome != null) {
                var steps = biome.getGenerationSettings().features();
                for (int i = 0; i < steps.size(); i++) {
                    List<String> names = new ArrayList<>();
                    for (var f : steps.get(i)) {
                        names.add(f.unwrapKey().map(k -> k.location().toString()).orElse("?"));
                    }
                    report.add("biome " + biomeFeatures + " step " + i + ": " + names);
                }
            }
        }
        // -Dterrahaul.autotest.heightAt=x,z;... : the generator's height estimate against the generated ground
        String heightAt = System.getProperty("terrahaul.autotest.heightAt", "");
        if (!heightAt.isEmpty()) {
            var generator = level.getChunkSource().getGenerator();
            var randomState = level.getChunkSource().randomState();
            for (String p : heightAt.split(";")) {
                String[] c = p.split(",");
                int x = Integer.parseInt(c[0].trim());
                int z = Integer.parseInt(c[1].trim());
                int estimate = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
                level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
                report.add("height at " + x + " " + z + ": estimate " + estimate + ", generated " + level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z)
                    + ", model " + (model == null ? "-" : String.format(Locale.ROOT, "%.1f", model.sample(x, z).height)));
            }
        }
        // -Dterrahaul.autotest.probe=x,y,z;... : print the blocks around each position, layer by layer
        String probe = System.getProperty("terrahaul.autotest.probe", "");
        if (!probe.isEmpty()) {
            for (String p : probe.split(";")) {
                String[] c = p.split(",");
                probe(level, Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), Integer.parseInt(c[2].trim()), report);
                section(level, Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), Integer.parseInt(c[2].trim()), report);
            }
        }
        if (Boolean.parseBoolean(System.getProperty("terrahaul.autotest.structures", "true"))) {
            checkStructures(level, report);
        }
        Files.write(dir.resolve("autotest-report.txt"), report);
        for (String line : report) {
            Terrahaul.LOGGER.info("[autotest] {}", line);
        }
    }

    /**
     * Counts floating terrain fragments: groups of terrain blocks near the surface (from each
     * column's top down to 60 blocks below the lowest top of it and its neighbours) that do not
     * connect to the ground (see {@link FloatingFragments}), and logs the largest ones with their
     * position. Terrain is any full, solid, fluid-free block except logs, leaves and bee nests, so
     * trees neither count as fragments nor hold one up.
     */
    private static void floatingFragments(ServerLevel level, int cx, int cz, int radius, List<String> report) {
        int size = (radius * 2 + 1) * 16;
        int x0 = (cx - radius) << 4;
        int z0 = (cz - radius) << 4;
        int[] top = new int[size * size];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int floor = level.getMinBuildHeight();
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                while (y > floor && !terrain(level, pos.set(x, y, z))) {
                    y--;
                }
                top[dx * size + dz] = y > floor ? y : Integer.MIN_VALUE;
            }
        }
        long t0 = System.nanoTime();
        FloatingFragments.Result r = FloatingFragments.scan((x, y, z) -> terrain(level, pos.set(x, y, z)), x0, z0, size, size, top, 60, 100_000);
        // fragments made of natural terrain only are generation faults; the others are icebergs,
        // structure pieces and the like, placed on purpose
        List<String> natural = new ArrayList<>();
        List<String> other = new ArrayList<>();
        int naturalBlocks = 0;
        int[] histogram = new int[5];
        for (FloatingFragments.Fragment f : r.largest()) {
            java.util.Map<String, Integer> blocks = new java.util.TreeMap<>();
            String detail = fragmentBlocks(level, f, blocks);
            boolean terrain = blocks.keySet().stream().allMatch(id -> com.terrahaul.worldgen.features.FloatingTerrainCleanup.isTerrain(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(id)).defaultBlockState()))
                // packed ice in water is an iceberg (or a piece of one), which the cleanup keeps
                && !(blocks.containsKey("packed_ice") && detail.contains("water="));
            if (terrain) {
                naturalBlocks += f.blocks();
                histogram[f.blocks() == 1 ? 0 : f.blocks() < 10 ? 1 : f.blocks() < 100 ? 2 : f.blocks() < 1000 ? 3 : 4]++;
                if (natural.size() < 10) {
                    natural.add("    fragment: " + f + " " + detail);
                }
            } else {
                if (other.size() < 3) {
                    other.add("    other: " + f + " " + detail);
                }
            }
        }
        int otherCount = r.fragments() - (histogram[0] + histogram[1] + histogram[2] + histogram[3] + histogram[4]);
        report.add(String.format(Locale.ROOT, "  floating terrain (surface windows of 60 blocks, %dx%d columns, %.0f ms): %d terrain fragments, %d blocks; sizes 1:%d 2-9:%d 10-99:%d 100-999:%d 1000+:%d; %d other floating groups (icebergs, structures)",
            size, size, (System.nanoTime() - t0) / 1e6, r.fragments() - otherCount, naturalBlocks,
            histogram[0], histogram[1], histogram[2], histogram[3], histogram[4], otherCount));
        report.addAll(natural);
        report.addAll(other);
    }

    /** Prints a vertical section along x through a position, 24 blocks each way and 12 up and down. */
    private static void section(ServerLevel level, int px, int py, int pz, List<String> report) {
        level.getChunk(px >> 4, pz >> 4, ChunkStatus.FULL, true);
        java.util.Map<String, Character> legend = new java.util.LinkedHashMap<>();
        legend.put("air", '.');
        String codes = "#ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        report.add("section along x through " + px + " " + py + " " + pz + " (x from -24 to 24)");
        for (int dy = 12; dy >= -12; dy--) {
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "  y=%4d: ", py + dy));
            for (int dx = -24; dx <= 24; dx++) {
                BlockState st = level.getBlockState(new BlockPos(px + dx, py + dy, pz));
                String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                Character ch = legend.get(id);
                if (ch == null) {
                    ch = codes.charAt(Math.min(codes.length() - 1, legend.size()));
                    legend.put(id, ch);
                }
                sb.append(ch);
            }
            report.add(sb.toString());
        }
        report.add("  legend: " + legend);
    }

    /** Prints the 9x9 blocks around a position on each layer from 4 below to 4 above it, with a legend. */
    private static void probe(ServerLevel level, int px, int py, int pz, List<String> report) {
        level.getChunk(px >> 4, pz >> 4, ChunkStatus.FULL, true);
        java.util.Map<String, Character> legend = new java.util.LinkedHashMap<>();
        legend.put("air", '.');
        String codes = "#ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        report.add("probe " + px + " " + py + " " + pz + " (rows: z from -4 to 4, columns: x from -4 to 4)");
        for (int dy = 4; dy >= -4; dy--) {
            StringBuilder sb = new StringBuilder("  y=" + (py + dy) + ": ");
            for (int dz = -4; dz <= 4; dz++) {
                for (int dx = -4; dx <= 4; dx++) {
                    BlockState st = level.getBlockState(new BlockPos(px + dx, py + dy, pz + dz));
                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                    Character ch = legend.get(id);
                    if (ch == null) {
                        ch = codes.charAt(Math.min(codes.length() - 1, legend.size()));
                        legend.put(id, ch);
                    }
                    sb.append(ch);
                }
                sb.append(' ');
            }
            report.add(sb.toString());
        }
        report.add("  legend: " + legend);
    }

    /**
     * Counts walls of water: faces of still water (or lava) source blocks above the ground of
     * their column that touch air beside or below them, and logs a few of them.
     */
    private static void waterWalls(ServerLevel level, int cx, int cz, int radius, List<String> report) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int faces = 0;
        List<String> examples = new ArrayList<>();
        net.minecraft.core.Direction[] sides = {net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.NORTH,
            net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST};
        for (int x = (cx - radius) << 4; x < (cx + radius + 1) << 4; x++) {
            for (int z = (cz - radius) << 4; z < (cz + radius + 1) << 4; z++) {
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
                for (int y = top; y > floor; y--) {
                    var fluid = level.getFluidState(pos.set(x, y, z));
                    if (fluid.isEmpty() || !fluid.isSource()) {
                        continue;
                    }
                    for (var d : sides) {
                        if (level.getBlockState(pos.set(x, y, z).move(d)).isAir()) {
                            faces++;
                            if (examples.size() < 6) {
                                examples.add(x + " " + y + " " + z + " " + d.getName());
                            }
                        }
                    }
                }
            }
        }
        report.add("  water walls: " + faces + " faces of still water facing air" + (examples.isEmpty() ? "" : ", e.g. " + String.join("; ", examples)));
    }

    /** The block types of a floating fragment and what lies around it, found from its seed block. */
    private static String fragmentBlocks(ServerLevel level, FloatingFragments.Fragment f, java.util.Map<String, Integer> blocks) {
        java.util.Map<String, Integer> around = new java.util.TreeMap<>();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        BlockPos seed = new BlockPos(f.seedX(), f.seedY(), f.seedZ());
        queue.add(seed);
        seen.add(seed.asLong());
        while (!queue.isEmpty() && seen.size() < 4000) {
            BlockPos p = queue.poll();
            blocks.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath(), 1, Integer::sum);
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.add(n.asLong())) {
                    continue;
                }
                if (terrain(level, n)) {
                    queue.add(n);
                } else {
                    BlockState st = level.getBlockState(n);
                    String key = st.isAir() ? "air" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                    around.merge(key, 1, Integer::sum);
                }
            }
        }
        return "blocks " + blocks + " around " + around;
    }

    private static boolean terrain(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(net.minecraft.tags.BlockTags.LOGS)
            && !state.is(net.minecraft.tags.BlockTags.LEAVES) && !state.is(net.minecraft.tags.BlockTags.BEEHIVES)
            && state.isCollisionShapeFullBlock(level, pos);
    }

    /** Counts the top solid block of every column and the blocks 1..15 below it. */
    private static void surfaceCensus(ServerLevel level, int cx, int cz, int radius, List<String> report) {
        java.util.Map<String, Integer> tops = new java.util.HashMap<>();
        java.util.Map<String, Integer> below = new java.util.HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = (cx - radius) * 16; x < (cx + radius + 1) * 16; x++) {
            for (int z = (cz - radius) * 16; z < (cz + radius + 1) * 16; z++) {
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
                String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.set(x, top, z)).getBlock()).getPath();
                tops.merge(name, 1, Integer::sum);
                for (int d = 1; d <= 15; d++) {
                    String b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.set(x, top - d, z)).getBlock()).getPath();
                    below.merge(b, 1, Integer::sum);
                }
            }
        }
        report.add("  top blocks: " + topEntries(tops, 14));
        report.add("  blocks 1-15 below top: " + topEntries(below, 14));
    }

    private static String topEntries(java.util.Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream().sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed()).limit(limit)
            .map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining(", "));
    }

    private static final int[] ORE_BANDS = {-512, -64, 0, 64, 320, 2032};

    /** Counts ore blocks per vertical band, to check vanilla depths and the extra layers. */
    private static void oreHistogram(ServerLevel level, int cx, int cz, int radius, List<String> report) {
        java.util.Map<String, long[]> counts = new java.util.TreeMap<>();
        long[] caveAir = new long[2];
        long[] deepBlocks = new long[2];
        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> isOre =
            st -> st.is(net.neoforged.neoforge.common.Tags.Blocks.ORES);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                var chunk = level.getChunk(cx + dx, cz + dz);
                var sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    var section = sections[i];
                    int baseY = level.getSectionYFromSectionIndex(i) << 4;
                    if (baseY < 0) {
                        // cave volume below Y 0: [-512,-64) and [-64,0)
                        int b = baseY < -64 ? 0 : 1;
                        deepBlocks[b] += 4096;
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                for (int x = 0; x < 16; x++) {
                                    var st = section.getBlockState(x, y, z);
                                    if (st.isAir() || st.is(net.minecraft.world.level.block.Blocks.LAVA) || st.is(net.minecraft.world.level.block.Blocks.WATER)) {
                                        caveAir[b]++;
                                    }
                                }
                            }
                        }
                    }
                    if (section.hasOnlyAir() || !section.getStates().maybeHas(isOre)) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                var st = section.getBlockState(x, y, z);
                                if (!isOre.test(st)) {
                                    continue;
                                }
                                String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath()
                                    .replace("deepslate_", "");
                                int band = 0;
                                while (band < ORE_BANDS.length - 2 && baseY + y >= ORE_BANDS[band + 1]) {
                                    band++;
                                }
                                counts.computeIfAbsent(name, k -> new long[ORE_BANDS.length - 1])[band]++;
                            }
                        }
                    }
                }
            }
        }
        report.add(String.format(Locale.ROOT, "  cave volume (air/water/lava): Y -512..-64 %.1f%%, Y -64..0 %.1f%%",
            100.0 * caveAir[0] / Math.max(1, deepBlocks[0]), 100.0 * caveAir[1] / Math.max(1, deepBlocks[1])));
        report.add("  ores per band [-512,-64) [-64,0) [0,64) [64,320) [320,2032):");
        counts.forEach((name, c) -> report.add(String.format(Locale.ROOT, "    %-14s %6d %6d %6d %6d %6d", name, c[0], c[1], c[2], c[3], c[4])));
    }

    /** Locates surface structures and checks that they generate where located, on suitable ground. */
    private static void checkStructures(ServerLevel level, List<String> report) {
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        String[][] checks = {
            {"#minecraft:village", "0,0"}, {"#minecraft:village", "3000,0"}, {"#minecraft:village", "0,-3000"},
            {"minecraft:pillager_outpost", "0,0"}, {"minecraft:desert_pyramid", "0,0"}, {"minecraft:swamp_hut", "0,0"},
            {"minecraft:mansion", "0,0"}, {"minecraft:stronghold", "0,0"}, {"minecraft:mineshaft", "0,0"},
            {"minecraft:ancient_city", "0,0"}, {"minecraft:trial_chambers", "0,0"}, {"minecraft:monument", "0,0"}
        };
        report.add("structures:");
        for (String[] check : checks) {
            net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.structure.Structure> set;
            if (check[0].startsWith("#")) {
                var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE,
                    net.minecraft.resources.ResourceLocation.parse(check[0].substring(1)));
                var named = registry.getTag(tag);
                if (named.isEmpty()) {
                    report.add("  " + check[0] + ": tag missing");
                    continue;
                }
                set = named.get();
            } else {
                var holder = registry.getHolder(net.minecraft.resources.ResourceLocation.parse(check[0]));
                if (holder.isEmpty()) {
                    report.add("  " + check[0] + ": not registered");
                    continue;
                }
                set = net.minecraft.core.HolderSet.direct(holder.get());
            }
            String[] o = check[1].split(",");
            BlockPos origin = new BlockPos(Integer.parseInt(o[0]), 0, Integer.parseInt(o[1]));
            long t0 = System.nanoTime();
            var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, set, origin, 64, false);
            double ms = (System.nanoTime() - t0) / 1e6;
            if (found == null) {
                report.add(String.format(Locale.ROOT, "  %s near %s: none within 64 regions (%.0f ms)", check[0], check[1], ms));
                continue;
            }
            BlockPos pos = found.getFirst();
            var structure = found.getSecond().value();
            var chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
            var start = level.structureManager().getStartForStructure(net.minecraft.core.SectionPos.of(chunk.getPos(), 0), structure, chunk);
            boolean present = start != null && start.isValid();
            String terrain = "";
            boolean surface = check[0].contains("village") || check[0].contains("outpost") || check[0].contains("pyramid")
                || check[0].contains("swamp_hut") || check[0].contains("mansion");
            if (present && surface) {
                var box = start.getBoundingBox();
                for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
                    for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                        level.getChunk(x, z, ChunkStatus.FULL, true);
                    }
                }
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                int water = 0;
                int samples = 0;
                for (int x = box.minX(); x <= box.maxX(); x += 4) {
                    for (int z = box.minZ(); z <= box.maxZ(); z += 4) {
                        int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z);
                        min = Math.min(min, h);
                        max = Math.max(max, h);
                        if (!level.getFluidState(new BlockPos(x, h, z)).isEmpty()) {
                            water++;
                        }
                        samples++;
                    }
                }
                terrain = String.format(Locale.ROOT, " box %dx%d y%d..%d, ground in box y%d..%d, water columns %d/%d",
                    box.getXSpan(), box.getZSpan(), box.minY(), box.maxY(), min, max, water, samples);
            }
            report.add(String.format(Locale.ROOT, "  %s near %s: %s at %d %d %d (%.0f ms), start present: %s%s", check[0], check[1],
                found.getSecond().unwrapKey().map(k -> k.location().toString()).orElse("?"), pos.getX(), pos.getY(), pos.getZ(), ms, present, terrain));
        }
    }

    private static void describeArea(ServerLevel level, TerrainModel model, int cx, int cz, int radius, List<String> report) {
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        int mismatches = 0;
        int samples = 0;
        java.util.Map<String, Integer> biomes = new java.util.TreeMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                for (int lx = 0; lx < 16; lx += 4) {
                    for (int lz = 0; lz < 16; lz += 4) {
                        int x = chunk.getPos().getMinBlockX() + lx;
                        int z = chunk.getPos().getMinBlockZ() + lz;
                        int h = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz);
                        minH = Math.min(minH, h);
                        maxH = Math.max(maxH, h);
                        if (model != null) {
                            ColumnSample s = model.sample(x, z);
                            if (Math.abs(s.height - h) > 12 && s.cliff < 0.2 && s.lake <= 0 && s.river <= 0) {
                                mismatches++;
                            }
                            samples++;
                        }
                        Holder<Biome> b = level.getBiome(new BlockPos(x, h + 1, z));
                        String key = b.unwrapKey().map(k -> k.location().getPath()).orElse("?");
                        biomes.merge(key, 1, Integer::sum);
                    }
                }
            }
        }
        report.add(String.format(Locale.ROOT, "  surface height range %d..%d; model/height mismatches >12 blocks: %d of %d", minH, maxH, mismatches, samples));
        report.add("  biomes: " + biomes);
        LevelChunk center = level.getChunk(cx, cz);
        int top = center.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
        BlockPos above = new BlockPos(center.getPos().getMinBlockX() + 8, top + 2, center.getPos().getMinBlockZ() + 8);
        BlockPos high = above.atY(Math.min(level.getMaxBuildHeight() - 1, top + 600));
        report.add("  sky light 2 above surface=" + level.getBrightness(LightLayer.SKY, above)
            + ", 600 above=" + level.getBrightness(LightLayer.SKY, high)
            + ", block at max build height-1: " + level.getBlockState(above.atY(level.getMaxBuildHeight() - 1)).getBlock());
    }

    /** Side view along the x axis through the centre of the area: terrain, water, caves. */
    private static void renderSection(ServerLevel level, int cx, int cz, int radius, File file) throws IOException {
        int width = (radius * 2 + 1) * 16;
        int z = (cz << 4) + 8;
        int baseX = (cx - radius) << 4;
        int top = Integer.MIN_VALUE;
        int bottom = Integer.MAX_VALUE;
        for (int x = 0; x < width; x++) {
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + x, z);
            top = Math.max(top, h);
            bottom = Math.min(bottom, h);
        }
        top = Math.min(level.getMaxBuildHeight() - 1, top + 20);
        int minY = Math.max(level.getMinBuildHeight(), bottom - 120);
        int heightPx = top - minY + 1;
        BufferedImage img = new BufferedImage(width, heightPx, BufferedImage.TYPE_INT_RGB);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < width; x++) {
            for (int y = minY; y <= top; y++) {
                BlockState state = level.getBlockState(pos.set(baseX + x, y, z));
                int rgb;
                if (state.isAir()) {
                    rgb = y > level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + x, z) ? 0xA8C8FF : 0x202020;
                } else {
                    MapColor color = state.getMapColor(level, pos);
                    rgb = color == MapColor.NONE ? 0xFF00FF : color.col;
                }
                img.setRGB(x, top - y, rgb);
            }
        }
        ImageIO.write(img, "png", file);
    }

    private static void dumpRiverColumn(ServerLevel level, TerrainModel model, int cx, int cz, int radius, List<String> report) {
        if (model == null) {
            return;
        }
        for (int x = (cx - radius) << 4; x < (cx + radius + 1) << 4; x += 2) {
            for (int z = (cz - radius) << 4; z < (cz + radius + 1) << 4; z += 2) {
                ColumnSample s = model.sample(x & ~3, z & ~3);
                if (s.river > 0.6) {
                    StringBuilder sb = new StringBuilder();
                    int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    for (int y = h + 1; y >= h - 8; y--) {
                        sb.append(y).append('=').append(level.getBlockState(new BlockPos(x, y, z)).getBlock().getName().getString()).append(' ');
                    }
                    report.add(String.format(Locale.ROOT, "  river column (%d,%d) model h=%.1f riverLevel=%.1f waterLevel=%s width=%.1f dry=%s: %s",
                        x, z, s.height, s.riverLevel, s.waterLevel == ColumnSample.NO_WATER ? "-" : String.format(Locale.ROOT, "%.1f", s.waterLevel),
                        s.riverWidth, s.dryRiver, sb));
                    return;
                }
            }
        }
    }

    private static void renderArea(ServerLevel level, int cx, int cz, int radius, File file) throws IOException {
        int size = (radius * 2 + 1) * 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int[][] heights = new int[size][size];
        int baseX = (cx - radius) << 4;
        int baseZ = (cz - radius) << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                heights[x][z] = level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + x, baseZ + z) - 1;
            }
        }
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int h = heights[x][z];
                BlockState state = level.getBlockState(pos.set(baseX + x, h, baseZ + z));
                MapColor color = state.getMapColor(level, pos);
                int rgb = color == MapColor.NONE ? 0 : color.col;
                int north = z > 0 ? heights[x][z - 1] : h;
                double shade = h > north ? 1.12 : h < north ? 0.84 : 1.0;
                if (!state.getFluidState().isEmpty()) {
                    shade = 1.0;
                }
                int r = (int) Math.min(255, ((rgb >> 16) & 255) * shade);
                int g = (int) Math.min(255, ((rgb >> 8) & 255) * shade);
                int b = (int) Math.min(255, (rgb & 255) * shade);
                img.setRGB(x, z, (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(img, "png", file);
    }
}
