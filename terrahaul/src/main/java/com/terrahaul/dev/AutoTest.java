package com.terrahaul.dev;

import com.terrahaul.Terrahaul;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.surface.MountainFlora;
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
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * counts floating terrain fragments, walls of water and blocks that cannot stay where they are
 * (plants that would drop off at the first block update), the stones lying on sand (see
 * {@link StonesOnSand}) and the lava near the surface (see {@link SurfaceLava}), renders top-down
 * maps of the generated blocks, writes a text report and stops the server.
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
            net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure> located = null;
            if (p[0].startsWith("find:")) {
                // find:<target>[@x/z],r: the landform nearest to x, z (default 0, 0)
                String name = p[0].substring(5);
                int[] origin = origin(name);
                name = name.contains("@") ? name.substring(0, name.indexOf('@')) : name;
                radius = Integer.parseInt(p[1].trim());
                com.terrahaul.worldgen.terrain.TerrainFinder.Target target = com.terrahaul.worldgen.terrain.TerrainFinder.targets().get(name);
                long f0 = System.nanoTime();
                com.terrahaul.worldgen.terrain.TerrainFinder.Result found = model == null || target == null ? null
                    : com.terrahaul.worldgen.terrain.TerrainFinder.find(model, target, origin[0], origin[1], 30000, () -> false);
                if (found == null) {
                    report.add("area " + areaIndex + ": " + name + " not found");
                    areaIndex++;
                    continue;
                }
                report.add(String.format(Locale.ROOT, "area %d: found %s at %d %d %d in %.0f ms", areaIndex, name, found.x(), found.y(), found.z(), (System.nanoTime() - f0) / 1e6));
                cx = found.x() >> 4;
                cz = found.z() >> 4;
            } else if (p[0].startsWith("structure:")) {
                // structure:<id>@x/z,r: the structure nearest to x, z as /locate finds it
                String id = p[0].substring(10, p[0].indexOf('@'));
                String[] o = p[0].substring(p[0].indexOf('@') + 1).split("/");
                radius = Integer.parseInt(p[1].trim());
                var holder = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .getHolder(net.minecraft.resources.ResourceLocation.parse(id));
                long f0 = System.nanoTime();
                var found = holder.isEmpty() ? null : level.getChunkSource().getGenerator().findNearestMapStructure(level,
                    net.minecraft.core.HolderSet.direct(holder.get()), new BlockPos(Integer.parseInt(o[0].trim()), 0, Integer.parseInt(o[1].trim())), 64, false);
                if (found == null) {
                    report.add("area " + areaIndex + ": structure " + id + " not found");
                    areaIndex++;
                    continue;
                }
                report.add(String.format(Locale.ROOT, "area %d: located %s at %s in %.0f ms", areaIndex, id, found.getFirst().toShortString(), (System.nanoTime() - f0) / 1e6));
                cx = found.getFirst().getX() >> 4;
                cz = found.getFirst().getZ() >> 4;
                located = found.getSecond();
            } else if (p[0].startsWith("biome:")) {
                // biome:<id>[@x/z],r: the biome nearest to x, z (default 0, 0)
                String id = p[0].substring(6);
                int[] origin = origin(id);
                id = id.contains("@") ? id.substring(0, id.indexOf('@')) : id;
                radius = Integer.parseInt(p[1].trim());
                var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.parse(id));
                var found = level.findClosestBiome3d(h -> h.is(key), new BlockPos(origin[0], 0, origin[1]), 12000, 32, 64);
                if (found == null) {
                    report.add("area " + areaIndex + ": biome " + id + " not found");
                    areaIndex++;
                    continue;
                }
                report.add("area " + areaIndex + ": biome " + id + " at " + found.getFirst().toShortString());
                cx = found.getFirst().getX() >> 4;
                cz = found.getFirst().getZ() >> 4;
            } else if (p[0].startsWith("water:")) {
                // water:<biome id>,r: the nearest stream or river (by the terrain model) in that biome
                String id = p[0].substring(6);
                radius = Integer.parseInt(p[1].trim());
                int[] found = model == null ? null : waterInBiome(level, model, id);
                if (found == null) {
                    report.add("area " + areaIndex + ": no stream or river in biome " + id + " found");
                    areaIndex++;
                    continue;
                }
                report.add("area " + areaIndex + ": stream or river in biome " + id + " at " + found[0] + " " + found[1] + " " + found[2]);
                cx = found[0] >> 4;
                cz = found[2] >> 4;
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
            unsupportedBlocks(level, cx, cz, radius, report);
            StonesOnSand.check(level, cx, cz, radius, report);
            SurfaceLava.check(level, model, cx, cz, radius, report);
            TreeDump.audit(level, cx, cz, radius, report);
            if (located != null) {
                var chunk = level.getChunk(cx, cz);
                var start = level.structureManager().getStartForStructure(net.minecraft.core.SectionPos.of(chunk.getPos(), 0), located.value(), chunk);
                if (start != null && start.isValid()) {
                    UndergroundStructureCheck.check(level, start, "start at chunk " + cx + " " + cz, report);
                    UndergroundStructureCheck.compareWithFresh(level, located, start, report);
                } else {
                    report.add("    no start of the located structure in chunk " + cx + " " + cz);
                }
            }
            mountainFlora(level, model, cx, cz, radius, report);
            areaIndex++;
        }
        StonesOnSand.summary(report);
        SurfaceLava.summary(report);
        long cleaned = com.terrahaul.worldgen.features.UnsupportedPlantCleanup.CHUNKS.sum();
        report.add(String.format(Locale.ROOT, "plant cleanup: %d chunks, %.3f ms/chunk, %d plants removed",
            cleaned, com.terrahaul.worldgen.features.UnsupportedPlantCleanup.NANOS.sum() / 1e6 / Math.max(1, cleaned),
            com.terrahaul.worldgen.features.UnsupportedPlantCleanup.REMOVED.sum()));
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
        // -Dterrahaul.autotest.survey=<structure id>@x/z/radius[/limit];... : how far below the
        // natural floor the starts near x, z are generated (see UndergroundStructureCheck)
        String survey = System.getProperty("terrahaul.autotest.survey", "");
        if (!survey.isEmpty()) {
            for (String one : survey.split(";")) {
                UndergroundStructureCheck.survey(level, one.trim(), report);
            }
        }
        if (Boolean.parseBoolean(System.getProperty("terrahaul.autotest.structures", "true"))) {
            checkStructures(level, report);
        }
        UndergroundStructureCheck.reportModelExcess(report);
        Files.write(dir.resolve("autotest-report.txt"), report);
        for (String line : report) {
            Terrahaul.LOGGER.info("[autotest] {}", line);
        }
    }

    /** The search origin {x, z} of an area name ending in {@code @x/z}, or {0, 0}. */
    private static int[] origin(String name) {
        int at = name.indexOf('@');
        if (at < 0) {
            return new int[]{0, 0};
        }
        String[] o = name.substring(at + 1).split("/");
        return new int[]{Integer.parseInt(o[0].trim()), Integer.parseInt(o[1].trim())};
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
        // fins: a single solid block at the water's surface with open water on both sides of it
        // along x or z; a chunk-border share near 100% means something sealed a neighbour too early
        int fins = 0;
        int borderFins = 0;
        List<String> finExamples = new ArrayList<>();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int x = (cx - radius) << 4; x < (cx + radius + 1) << 4; x++) {
            for (int z = (cz - radius) << 4; z < (cz + radius + 1) << 4; z++) {
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (!level.getFluidState(pos.set(x, y, z)).isSource()) {
                    continue;
                }
                for (int axis = 0; axis < 2; axis++) {
                    int ax = axis == 0 ? 1 : 0;
                    int az = axis == 0 ? 0 : 1;
                    BlockState mid = level.getBlockState(q.set(x + ax, y, z + az));
                    if (mid.getFluidState().isEmpty() && mid.isSolid() && level.getBlockState(q.set(x + ax, y + 1, z + az)).isAir()
                        && level.getFluidState(q.set(x + 2 * ax, y, z + 2 * az)).isSource()
                        && level.getBlockState(q.set(x + 2 * ax, y + 1, z + 2 * az)).isAir()) {
                        fins++;
                        int bx = x + ax;
                        int bz = z + az;
                        if (axis == 0 ? ((bx & 15) == 0 || (bx & 15) == 15) : ((bz & 15) == 0 || (bz & 15) == 15)) {
                            borderFins++;
                        }
                        if (finExamples.size() < 4) {
                            finExamples.add(bx + " " + y + " " + bz);
                        }
                    }
                }
            }
        }
        report.add("  water fins: " + fins + " single blocks in open water at the surface (" + borderFins + " on chunk-border columns)"
            + (finExamples.isEmpty() ? "" : ", e.g. " + String.join("; ", finExamples)));
    }

    /**
     * Counts blocks that would drop off at the first block update beside them: every block of the
     * area, at any height, whose {@link BlockState#canSurvive} is false (a petal mat or grass tuft
     * over air or water, a flower on rock, the top half of tall grass without its foot), plus the
     * lower halves of double plants without their upper half, which survive the test but break as
     * soon as a neighbour changes. Mushrooms and crops whose ground is right but whose light is
     * not are counted apart (their light test is not something generation decides). Logs the count
     * per block type, what lies below them, how many are plants the {@link
     * com.terrahaul.worldgen.features.UnsupportedPlantCleanup} would remove, and a few positions.
     * Only sections whose palettes hold a block that overrides {@code canSurvive} are scanned.
     */
    private static void unsupportedBlocks(ServerLevel level, int cx, int cz, int radius, List<String> report) {
        long t0 = System.nanoTime();
        java.util.Set<net.minecraft.world.level.block.Block> conditional = conditionalBlocks();
        java.util.function.Predicate<BlockState> mayFail = s -> conditional.contains(s.getBlock());
        java.util.Map<String, Integer> byType = new java.util.TreeMap<>();
        java.util.Map<String, Integer> below = new java.util.TreeMap<>();
        java.util.Map<String, Integer> examplesPerType = new java.util.HashMap<>();
        List<String> examples = new ArrayList<>();
        int total = 0;
        int plants = 0;
        int nearSurface = 0;
        int halves = 0;
        int lightOnly = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                var sections = chunk.getSections();
                int bx = chunk.getPos().getMinBlockX();
                int bz = chunk.getPos().getMinBlockZ();
                for (int i = 0; i < sections.length; i++) {
                    var section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(mayFail)) {
                        continue;
                    }
                    int y0 = level.getSectionYFromSectionIndex(i) << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState st = section.getBlockState(x, y, z);
                                if (!mayFail.test(st)) {
                                    continue;
                                }
                                pos.set(bx + x, y0 + y, bz + z);
                                boolean half = false;
                                if (st.canSurvive(level, pos)) {
                                    if (!(st.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock)
                                        || st.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                                        continue;
                                    }
                                    BlockState up = level.getBlockState(pos.above());
                                    if (up.is(st.getBlock()) && up.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                                        continue;
                                    }
                                    half = true;
                                }
                                BlockState ground = level.getBlockState(pos.below());
                                if (st.getBlock() instanceof net.minecraft.world.level.block.MushroomBlock
                                    && (ground.is(BlockTags.MUSHROOM_GROW_BLOCK) || ground.isSolidRender(level, pos.below()))
                                    || st.getBlock() instanceof net.minecraft.world.level.block.CropBlock && ground.is(Blocks.FARMLAND)) {
                                    lightOnly++;
                                    continue;
                                }
                                total++;
                                halves += half ? 1 : 0;
                                String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath() + (half ? "(lower half alone)" : "");
                                byType.merge(id, 1, Integer::sum);
                                String g = ground.isAir() ? "air" : BuiltInRegistries.BLOCK.getKey(ground.getBlock()).getPath();
                                below.merge(g, 1, Integer::sum);
                                if (com.terrahaul.worldgen.features.UnsupportedPlantCleanup.isPlant(st)) {
                                    plants++;
                                }
                                if (pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 16) {
                                    nearSurface++;
                                }
                                if (examples.size() < 16 && examplesPerType.merge(id, 1, Integer::sum) <= 3) {
                                    examples.add(pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + id + " on " + g);
                                }
                            }
                        }
                    }
                }
            }
        }
        report.add(String.format(Locale.ROOT, "  unsupported blocks (canSurvive false or a lone lower half; %.0f ms): %d, of which plants %d, near the surface %d, lone lower halves %d; light only (not counted): %d",
            (System.nanoTime() - t0) / 1e6, total, plants, nearSurface, halves, lightOnly));
        if (total > 0) {
            report.add("    by type: " + topEntries(byType, 16));
            report.add("    on: " + topEntries(below, 12));
            report.add("    e.g. " + String.join("; ", examples));
        }
    }

    private static java.util.Set<net.minecraft.world.level.block.Block> conditionalBlocks;

    /**
     * The blocks whose class overrides {@code canSurvive} (every other block always survives), or
     * every block if that cannot be told by reflection.
     */
    private static java.util.Set<net.minecraft.world.level.block.Block> conditionalBlocks() {
        if (conditionalBlocks == null) {
            java.util.Set<net.minecraft.world.level.block.Block> set = new java.util.HashSet<>();
            java.util.Set<net.minecraft.world.level.block.Block> all = new java.util.HashSet<>();
            for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
                all.add(block);
                for (Class<?> c = block.getClass(); c != null && c != net.minecraft.world.level.block.state.BlockBehaviour.class; c = c.getSuperclass()) {
                    try {
                        c.getDeclaredMethod("canSurvive", BlockState.class, net.minecraft.world.level.LevelReader.class, BlockPos.class);
                        set.add(block);
                        break;
                    } catch (NoSuchMethodException e) {
                        // not declared here
                    }
                }
            }
            conditionalBlocks = set.size() < 20 ? all : set;
            Terrahaul.LOGGER.info("[autotest] {} block types override canSurvive", set.size());
        }
        return conditionalBlocks;
    }

    /**
     * The nearest column (on a 32-block grid, in rings around the origin out to 8000 blocks) where
     * the terrain model has a stream or a river channel and the surface biome is {@code id}, as
     * {x, y, z}, or {@code null}.
     */
    private static int[] waterInBiome(ServerLevel level, TerrainModel model, String id) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.parse(id));
        var source = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        for (int ring = 0; ring <= 250; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    int x = i * 32;
                    int z = j * 32;
                    ColumnSample s = model.sample(x, z);
                    if (!(s.stream > 0.5 || s.river > 0.6)) {
                        continue;
                    }
                    int y = (int) s.height;
                    if (source.getNoiseBiome(x >> 2, y >> 2, z >> 2, sampler).is(key)) {
                        return new int[]{x, y, z};
                    }
                }
            }
        }
        return null;
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
            // mud's collision box is 14/16 of a block high, but it is ground: patches of clay, moss or
            // gravel lying on a mud lake floor are not floating
            && (state.isCollisionShapeFullBlock(level, pos) || state.is(net.minecraft.world.level.block.Blocks.MUD));
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

    /**
     * Locates structures and checks that they generate where located: surface structures on
     * suitable ground, those in {@code terrahaul:keep_underground} below the terrain (see
     * UndergroundStructureCheck).
     */
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
            if (present && found.getSecond().is(com.terrahaul.worldgen.structure.UndergroundStructures.KEEP_UNDERGROUND)) {
                UndergroundStructureCheck.check(level, start, check[0], report);
            }
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

    /** Distance bands from the tree line of {@link MountainFlora} (blocks below it; negative above), for {@link #mountainFlora}. */
    private static final int[] FLORA_BANDS = {100, 50, 0, -50, -150, Integer.MIN_VALUE};
    private static final String[] FLORA_BAND_NAMES = {">100 below", "50-100 below", "0-50 below", "0-50 above", "50-150 above", ">150 above"};

    /**
     * Census of the vegetation on mountain flanks (columns where {@link MountainFlora} gives a
     * share above 0.5), independent of how it was placed: per distance band from the tree line,
     * the share of columns under a tree canopy (leaves or wood above the ground), with a plant on
     * the ground, and with soil, snow or bare rock as their ground. It also counts tree trunks by
     * the ground they stand on: the steepest rise over three blocks around the foot (more than 5
     * is about 60 degrees or steeper) and snow (the ground under the foot, or most of the 5x5
     * ground around it, snow or ice).
     */
    private static void mountainFlora(ServerLevel level, TerrainModel model, int cx, int cz, int radius, List<String> report) {
        if (model == null) {
            return;
        }
        int bands = FLORA_BANDS.length;
        int[] columns = new int[bands];
        int[] canopy = new int[bands];
        int[] plants = new int[bands];
        int[] soil = new int[bands];
        int[] snow = new int[bands];
        int[] rock = new int[bands];
        int x0 = (cx - radius) << 4;
        int z0 = (cz - radius) << 4;
        int size = (2 * radius + 1) << 4;
        int[] ground = new int[size * size];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int x = x0 + i;
                int z = z0 + j;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                int top = y;
                while (y > level.getMinBuildHeight() && !floraGround(level.getBlockState(pos.set(x, y, z)))) {
                    y--;
                }
                ground[i * size + j] = y;
                MountainFlora.Zone f = MountainFlora.at(model, x, z, y);
                if (f.share <= 0.5) {
                    ground[i * size + j] = Integer.MIN_VALUE;
                    continue;
                }
                int b = 0;
                while (f.margin < FLORA_BANDS[b]) {
                    b++;
                }
                columns[b]++;
                BlockState g = level.getBlockState(pos.set(x, y, z));
                BlockState above = level.getBlockState(pos.set(x, y + 1, z));
                if (top > y + 1 || above.is(BlockTags.LOGS)) {
                    canopy[b]++;
                } else if (!above.isAir() && !above.is(Blocks.SNOW) && above.getFluidState().isEmpty()) {
                    plants[b]++;
                }
                if (snowy(g)) {
                    snow[b]++;
                } else if (g.is(BlockTags.DIRT)) {
                    soil[b]++;
                } else {
                    rock[b]++;
                }
            }
        }
        report.add("  mountain flanks (flora share > 0.5) by distance from the tree line: columns, under trees, plants, soil, snow, rock ground");
        for (int b = 0; b < bands; b++) {
            if (columns[b] == 0) {
                continue;
            }
            double n = columns[b];
            report.add(String.format(Locale.ROOT, "    %-13s %6d: trees %5.1f%%, plants %5.1f%%, soil %5.1f%%, snow %5.1f%%, rock %5.1f%%",
                FLORA_BAND_NAMES[b], columns[b], 100.0 * canopy[b] / n, 100.0 * plants[b] / n, 100.0 * soil[b] / n, 100.0 * snow[b] / n,
                100.0 * rock[b] / n));
        }
        // trunk feet: an upright log standing on the ground of its column
        int feet = 0;
        int steep = 0;
        int onSnow = 0;
        int inSnowfield = 0;
        List<String> examples = new ArrayList<>();
        for (int i = 3; i < size - 3; i++) {
            for (int j = 3; j < size - 3; j++) {
                int y = ground[i * size + j];
                if (y == Integer.MIN_VALUE) {
                    continue;
                }
                int x = x0 + i;
                int z = z0 + j;
                if (!uprightLog(level.getBlockState(pos.set(x, y + 1, z)))) {
                    continue;
                }
                feet++;
                int rise = 0;
                for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                    int n = ground[(i + d[0]) * size + j + d[1]];
                    if (n != Integer.MIN_VALUE) {
                        rise = Math.max(rise, Math.abs(n - y));
                    }
                }
                if (rise > 5) {
                    steep++;
                    if (examples.size() < 4) {
                        examples.add(x + " " + (y + 1) + " " + z + " rise " + rise);
                    }
                }
                if (snowy(level.getBlockState(pos.set(x, y, z)))) {
                    onSnow++;
                }
                int snowCols = 0;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int n = ground[(i + dx) * size + j + dz];
                        if (n != Integer.MIN_VALUE && snowy(level.getBlockState(pos.set(x + dx, n, z + dz)))) {
                            snowCols++;
                        }
                    }
                }
                if (snowCols > 12) {
                    inSnowfield++;
                }
            }
        }
        report.add(String.format(Locale.ROOT, "  mountain trunks: %d, on ground steeper than 5 over 3 blocks (60+ degrees): %d %s, on snow: %d, in snowfields: %d",
            feet, steep, examples, onSnow, inSnowfield));
    }

    /**
     * Ground under vegetation: a solid block that is not wood, leaves or a plant. (Roots laid into
     * the ground are wood too, so beside a root flare the ground can read a block lower than it
     * was.)
     */
    private static boolean floraGround(BlockState state) {
        return state.blocksMotion() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
            || !state.getFluidState().isEmpty();
    }

    /** An upright log (not a bark block): a tree trunk. */
    private static boolean uprightLog(BlockState state) {
        return state.is(BlockTags.LOGS) && state.hasProperty(BlockStateProperties.AXIS)
            && state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y
            && !BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_wood");
    }

    private static boolean snowy(BlockState state) {
        return state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.PACKED_ICE) || state.is(Blocks.ICE)
            || state.is(Blocks.BLUE_ICE);
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
