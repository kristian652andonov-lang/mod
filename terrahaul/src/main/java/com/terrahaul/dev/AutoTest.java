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
 * renders top-down maps of the generated blocks, writes a text report and stops the server.
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
            areaIndex++;
        }
        Files.write(dir.resolve("autotest-report.txt"), report);
        for (String line : report) {
            Terrahaul.LOGGER.info("[autotest] {}", line);
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
