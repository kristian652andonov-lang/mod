package com.terrahaul.dev;

import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.VanillaHeightContext;
import com.terrahaul.worldgen.placement.FeatureBiomes;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.neoforged.neoforge.common.Tags;

/**
 * Ore census for {@link AutoTest}: counts every ore block of every mod in the generated areas and
 * checks, for the ores of other mods, how much of the volume they are meant to spawn in can take
 * them, so the same report from a Terrahaul world and from a normal world shows whether (and why)
 * an ore is rarer in one of them.
 * <p>
 * <b>Ore blocks</b> are blocks in the {@code c:ores} tag or whose id path is {@code ore}, ends in
 * {@code _ore}, starts with {@code ore_} or contains {@code _ore_}. They are counted by full id
 * (namespace and path), by height band and by the <i>surface biome</i> of their column (the biome
 * at the column's top solid block), with the number of columns of each surface biome, so a
 * biome-bound ore can be given per chunk of its biomes. Each chunk is counted once, however many
 * areas overlap it.
 * <p>
 * <b>Probes</b> are the placed features of other mods (and {@code minecraft:ore_emerald}, plus
 * {@code terrahaul:resources/high_emerald} in Terrahaul worlds; more with
 * {@code -Dterrahaul.autotest.oreProbes=<id>,...}) whose configured feature is an ore and which
 * some biome of the Overworld holds. For each one the census walks its height range (resolved as
 * the game resolves it, see {@code HeightRangePlacementMixin}) on a 2-block grid in the columns
 * whose surface biome holds it, and wherever else the game's biome check accepts it. Each sampled
 * cell is checked for what the ore feature and the {@code minecraft:biome} filter would see: open
 * (air or fluid), host rock matched by a target of the ore, the 3D biome at the cell holding the
 * feature (vanilla's check), the climate's surface biome (the biome of the same climate at depth
 * 0, which differs from the 3D biome only through the depth parameter) holding it, and the check
 * the game actually makes in this world ({@link FeatureBiomes}).
 * Host rocks that match no target and 3D biomes that reject the feature are listed. The cells
 * that pass both the rock and the game's biome check are the volume the ore can spawn in.
 */
final class OreCensus {
    private static final int[] BANDS = {-512, -64, -40, -20, 0, 20, 40, 60, 80, 100, 130, 160, 200, 256, 320, 640, 2032};
    private static final int GRID = 2;

    private static final Set<Long> COUNTED = new HashSet<>();
    private static final Map<String, long[]> ORE_BANDS = new TreeMap<>();
    private static final Map<String, Map<String, Long>> ORE_SURFACE = new TreeMap<>();
    private static final Map<String, Long> SURFACE_COLUMNS = new TreeMap<>();
    private static final Map<Block, String> ORE_IDS = new IdentityHashMap<>();
    private static final Map<String, Probe> PROBES = new LinkedHashMap<>();
    private static long chunks;
    private static long censusNanos;
    /** The census's own share of the counters of {@link FeatureBiomes}. */
    private static long censusWideningCalls;
    private static long censusWideningAccepted;
    private static long censusWideningNanos;
    private static boolean initialised;

    private OreCensus() {
    }

    /** Counts the ores of the area's chunks that no earlier area counted, and logs the area's share. */
    static void area(ServerLevel level, int cx, int cz, int radius, List<String> report) {
        init(level);
        long t0 = System.nanoTime();
        long calls0 = FeatureBiomes.WIDENING_CALLS.sum();
        long accepted0 = FeatureBiomes.WIDENING_ACCEPTED.sum();
        long nanos0 = FeatureBiomes.WIDENING_NANOS.sum();
        Map<String, Long> areaOres = new TreeMap<>();
        int fresh = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                if (!COUNTED.add(chunk.getPos().toLong())) {
                    continue;
                }
                fresh++;
                chunks++;
                countChunk(level, chunk, areaOres);
                for (Probe probe : PROBES.values()) {
                    probe.scan(level, chunk);
                }
            }
        }
        censusNanos += System.nanoTime() - t0;
        censusWideningCalls += FeatureBiomes.WIDENING_CALLS.sum() - calls0;
        censusWideningAccepted += FeatureBiomes.WIDENING_ACCEPTED.sum() - accepted0;
        censusWideningNanos += FeatureBiomes.WIDENING_NANOS.sum() - nanos0;
        report.add("  ore census: " + fresh + " new chunks; " + (areaOres.isEmpty() ? "no ores"
            : areaOres.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "))));
    }

    /** All counted chunks: ores per chunk, height band and surface biome, and the probes. */
    static void summary(ServerLevel level, List<String> report) {
        if (!initialised) {
            return;
        }
        if (!TerrahaulGeneration.isTerrahaul(level.getChunkSource().getGenerator())) {
            // the vanilla offset FeatureBiomes computes from the climate must be the offset of
            // vanilla's own depth router (depth at Y 0 = offset + 1)
            Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
            RandomSource random = RandomSource.create(3L);
            double worst = 0.0;
            for (int i = 0; i < 2000; i++) {
                int qx = random.nextInt(4000) - 2000;
                int qz = random.nextInt(4000) - 2000;
                double depth = sampler.depth().compute(new DensityFunction.SinglePointContext(
                    QuartPos.toBlock(qx), 0, QuartPos.toBlock(qz)));
                worst = Math.max(worst, Math.abs(depth - 1.0 - FeatureBiomes.vanillaOffset(sampler, qx, qz)));
            }
            report.add(String.format(Locale.ROOT, "vanilla offset check: largest difference to the router's depth over 2000 columns %.6f", worst));
        }
        report.add(String.format(Locale.ROOT, "ore census: %d chunks, %.1f ms/chunk for the census", chunks, censusNanos / 1e6 / Math.max(1, chunks)));
        long calls = FeatureBiomes.WIDENING_CALLS.sum() - censusWideningCalls;
        long accepted = FeatureBiomes.WIDENING_ACCEPTED.sum() - censusWideningAccepted;
        long nanos = FeatureBiomes.WIDENING_NANOS.sum() - censusWideningNanos;
        report.add(String.format(Locale.ROOT, "  ore biome check under taller ground (generation, all decorated chunks): %d vanilla-height biome"
                + " lookups, %d accepted; %.1f ms in all (%.3f ms per counted chunk)", calls, accepted, nanos / 1e6,
            nanos / 1e6 / Math.max(1, chunks)));
        StringBuilder bands = new StringBuilder("  bands:");
        for (int i = 0; i + 1 < BANDS.length; i++) {
            bands.append(' ').append(i).append("=[").append(BANDS[i]).append(',').append(BANDS[i + 1]).append(')');
        }
        report.add(bands.toString());
        report.add("  surface biomes (columns): " + SURFACE_COLUMNS.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
        for (Map.Entry<String, long[]> e : ORE_BANDS.entrySet()) {
            long total = 0;
            StringBuilder b = new StringBuilder();
            long[] c = e.getValue();
            for (int i = 0; i < c.length; i++) {
                total += c[i];
                if (c[i] > 0) {
                    b.append(' ').append(i).append('=').append(c[i]);
                }
            }
            report.add(String.format(Locale.ROOT, "  ore %s: %d blocks, %.3f per chunk; bands%s", e.getKey(), total, total / (double) Math.max(1, chunks), b));
            if (!e.getKey().startsWith("minecraft:")) {
                Map<String, Long> bySurface = ORE_SURFACE.getOrDefault(e.getKey(), Map.of());
                report.add("    by surface biome (blocks, per chunk of that biome): " + bySurface.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .map(s -> String.format(Locale.ROOT, "%s=%d (%.3f)", s.getKey(), s.getValue(),
                        s.getValue() * 256.0 / Math.max(1, SURFACE_COLUMNS.getOrDefault(s.getKey(), 0L))))
                    .collect(Collectors.joining(", ")));
            }
        }
        for (Probe probe : PROBES.values()) {
            probe.report(report);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static void init(ServerLevel level) {
        if (initialised) {
            return;
        }
        initialised = true;
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Set<Holder<Biome>> possible = generator.getBiomeSource().possibleBiomes();
        boolean terrahaul = TerrahaulGeneration.isTerrahaul(generator);
        Set<String> extra = new HashSet<>(List.of(System.getProperty("terrahaul.autotest.oreProbes",
            "minecraft:ore_emerald" + (terrahaul ? ",terrahaul:resources/high_emerald" : "")).split(",")));
        var registry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        List<Holder.Reference<PlacedFeature>> holders = registry.holders()
            .sorted((a, b) -> a.key().location().toString().compareTo(b.key().location().toString())).toList();
        for (Holder.Reference<PlacedFeature> holder : holders) {
            ResourceLocation id = holder.key().location();
            boolean modded = !id.getNamespace().equals("minecraft") && !id.getNamespace().equals("terrahaul");
            if (!modded && !extra.contains(id.toString())) {
                continue;
            }
            PlacedFeature feature = holder.value();
            if (!(feature.feature().value().config() instanceof OreConfiguration ore)) {
                continue;
            }
            Set<Holder<Biome>> holding = new HashSet<>();
            for (Holder<Biome> biome : possible) {
                if (generator.getBiomeGenerationSettings(biome).hasFeature(feature)) {
                    holding.add(biome);
                }
            }
            if (holding.isEmpty()) {
                continue;
            }
            int[] range = heightRange(level, generator, feature);
            if (range == null) {
                continue;
            }
            PROBES.put(id.toString(), new Probe(id.toString(), feature, ore, holding, range[0], range[1]));
        }
    }

    /** The lowest and highest Y the feature's height range gives in this world, or null. */
    private static int[] heightRange(ServerLevel level, ChunkGenerator generator, PlacedFeature feature) {
        for (PlacementModifier modifier : feature.placement()) {
            if (modifier instanceof HeightRangePlacement range) {
                try {
                    Field f = HeightRangePlacement.class.getDeclaredField("height");
                    f.setAccessible(true);
                    HeightProvider provider = (HeightProvider) f.get(range);
                    WorldGenerationContext context = TerrahaulConfig.oreDepthFix() && TerrahaulGeneration.isTerrahaul(generator)
                        ? VanillaHeightContext.get(generator) : new WorldGenerationContext(generator, level);
                    RandomSource random = RandomSource.create(1L);
                    int lo = Integer.MAX_VALUE;
                    int hi = Integer.MIN_VALUE;
                    for (int i = 0; i < 4000; i++) {
                        int y = provider.sample(random, context);
                        lo = Math.min(lo, y);
                        hi = Math.max(hi, y);
                    }
                    return new int[]{Math.max(lo, level.getMinBuildHeight()), Math.min(hi, level.getMaxBuildHeight() - 1)};
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String oreId(Block block) {
        String id = ORE_IDS.get(block);
        if (id == null) {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            String path = key.getPath();
            boolean ore = block.defaultBlockState().is(Tags.Blocks.ORES) || path.equals("ore") || path.endsWith("_ore")
                || path.startsWith("ore_") || path.contains("_ore_");
            id = ore ? key.toString() : "";
            ORE_IDS.put(block, id);
        }
        return id.isEmpty() ? null : id;
    }

    private static int band(int y) {
        int b = 0;
        while (b < BANDS.length - 2 && y >= BANDS[b + 1]) {
            b++;
        }
        return b;
    }

    /** The biome at the top solid block of a column, without the biome zoom's jitter. */
    static Holder<Biome> surfaceBiome(LevelChunk chunk, int lx, int lz) {
        int top = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz);
        return chunk.getNoiseBiome(QuartPos.fromBlock(chunk.getPos().getMinBlockX() + lx), QuartPos.fromBlock(top),
            QuartPos.fromBlock(chunk.getPos().getMinBlockZ() + lz));
    }

    static String name(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> k.location().getPath()).orElse("?");
    }

    private static void countChunk(ServerLevel level, LevelChunk chunk, Map<String, Long> areaOres) {
        String[] surface = new String[256];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                String s = name(surfaceBiome(chunk, lx, lz));
                surface[lx * 16 + lz] = s;
                SURFACE_COLUMNS.merge(s, 1L, Long::sum);
            }
        }
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.getStates().maybeHas(st -> oreId(st.getBlock()) != null)) {
                continue;
            }
            int baseY = level.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState st = section.getBlockState(x, y, z);
                        String id = oreId(st.getBlock());
                        if (id == null) {
                            continue;
                        }
                        ORE_BANDS.computeIfAbsent(id, k -> new long[BANDS.length - 1])[band(baseY + y)]++;
                        ORE_SURFACE.computeIfAbsent(id, k -> new TreeMap<>()).merge(surface[x * 16 + z], 1L, Long::sum);
                        areaOres.merge(id, 1L, Long::sum);
                    }
                }
            }
        }
    }

    /** One placed ore feature of another mod and the volume it can spawn in. */
    private static final class Probe {
        final String id;
        final PlacedFeature feature;
        final OreConfiguration ore;
        final Set<Holder<Biome>> biomes;
        final Set<Block> oreBlocks = new HashSet<>();
        final int lo;
        final int hi;
        long surfaceColumns;
        long cells;
        long open;
        long rock;
        long biome3d;
        long surfaceOk;
        long rockAnd3d;
        long rockAndAccepted;
        long accepted;
        long blocksInBiomes;
        long blocksInRange;
        final Map<String, Long> hosts = new HashMap<>();
        final Map<String, Long> rejecting = new HashMap<>();
        final Map<String, Long> accepting = new HashMap<>();
        final RandomSource random = RandomSource.create(7L);

        Probe(String id, PlacedFeature feature, OreConfiguration ore, Set<Holder<Biome>> biomes, int lo, int hi) {
            this.id = id;
            this.feature = feature;
            this.ore = ore;
            this.biomes = biomes;
            this.lo = lo;
            this.hi = hi;
            for (OreConfiguration.TargetBlockState t : ore.targetStates) {
                oreBlocks.add(t.state.getBlock());
            }
        }

        void scan(ServerLevel level, LevelChunk chunk) {
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int x0 = chunk.getPos().getMinBlockX();
            int z0 = chunk.getPos().getMinBlockZ();
            // ore blocks of this feature in its height range (a vein reaches a few blocks beyond
            // it), in all columns and in those whose surface biome holds the feature
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    boolean inBiome = biomes.contains(surfaceBiome(chunk, lx, lz));
                    for (int y = lo - 4; y <= hi + 4; y++) {
                        if (oreBlocks.contains(chunk.getBlockState(pos.set(x0 + lx, y, z0 + lz)).getBlock())) {
                            blocksInRange++;
                            if (inBiome) {
                                blocksInBiomes++;
                            }
                        }
                    }
                }
            }
            for (int lx = 0; lx < 16; lx += GRID) {
                for (int lz = 0; lz < 16; lz += GRID) {
                    int x = x0 + lx;
                    int z = z0 + lz;
                    boolean surfaceHolds = biomes.contains(surfaceBiome(chunk, lx, lz));
                    if (surfaceHolds) {
                        surfaceColumns++;
                    }
                    Holder<Biome> climateSurface = climateSurfaceBiome(generator, sampler, x, z);
                    boolean climateHolds = climateSurface != null && biomes.contains(climateSurface);
                    for (int y = lo; y <= hi; y += GRID) {
                        pos.set(x, y, z);
                        Holder<Biome> b3 = level.getBiome(pos);
                        boolean holds3d = biomes.contains(b3);
                        // the game's check: the 3D biome, or Terrahaul's widening where that rejects
                        boolean accepts = holds3d || FeatureBiomes.vanillaHeightBiome(
                            level, generator, sampler, pos, feature, Heightmap.Types.OCEAN_FLOOR);
                        if (!surfaceHolds && !accepts) {
                            continue;
                        }
                        cells++;
                        BlockState st = chunk.getBlockState(pos);
                        boolean solid = !st.isAir() && st.getFluidState().isEmpty();
                        if (!solid) {
                            open++;
                        }
                        boolean matches = false;
                        if (solid) {
                            for (OreConfiguration.TargetBlockState t : ore.targetStates) {
                                if (t.target.test(st, random)) {
                                    matches = true;
                                    break;
                                }
                            }
                            if (!matches) {
                                hosts.merge(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath(), 1L, Long::sum);
                            }
                        }
                        if (matches) {
                            rock++;
                        }
                        if (holds3d) {
                            biome3d++;
                        } else {
                            rejecting.merge(name(b3), 1L, Long::sum);
                        }
                        if (climateHolds) {
                            surfaceOk++;
                        }
                        if (accepts) {
                            accepted++;
                            if (!holds3d) {
                                accepting.merge(name(b3), 1L, Long::sum);
                            }
                        }
                        if (matches && holds3d) {
                            rockAnd3d++;
                        }
                        if (matches && accepts) {
                            rockAndAccepted++;
                        }
                    }
                }
            }
        }

        void report(List<String> report) {
            double per = GRID * GRID * GRID;
            double biomeChunks = surfaceColumns * GRID * GRID / 256.0;
            report.add(String.format(Locale.ROOT, "probe %s: Y %d..%d, %d biomes (%s), targets %s", id, lo, hi, biomes.size(),
                biomes.stream().map(OreCensus::name).sorted().limit(8).collect(Collectors.joining(" ")) + (biomes.size() > 8 ? " ..." : ""),
                ore.targetStates.stream().map(t -> t.target.getClass().getSimpleName().replace("Test", "")).collect(Collectors.joining("+"))));
            report.add(String.format(Locale.ROOT,
                "    %.1f chunks of its surface biomes; candidate volume %.0f blocks: open %.1f%%, host rock ok %.1f%%, 3D biome ok %.1f%%,"
                    + " climate surface biome ok %.1f%%, game's biome check ok %.1f%%; rock+3D biome %.0f blocks, rock+game's check %.0f blocks"
                    + " (%s per chunk of its surface biomes)",
                biomeChunks, cells * per, pct(open), pct(rock), pct(biome3d), pct(surfaceOk), pct(accepted), rockAnd3d * per, rockAndAccepted * per,
                biomeChunks > 0 ? String.format(Locale.ROOT, "%.0f", rockAndAccepted * per / biomeChunks) : "-"));
            report.add(String.format(Locale.ROOT,
                "    ore blocks in its height range %d (%.2f per 10k blocks of rock+game's check); in columns of its surface biomes %d (%s per chunk of those biomes)",
                blocksInRange, blocksInRange * 1e4 / Math.max(1.0, rockAndAccepted * per), blocksInBiomes,
                biomeChunks > 0 ? String.format(Locale.ROOT, "%.3f", blocksInBiomes / biomeChunks) : "-"));
            report.add("    host blocks not matched: " + top(hosts, 8) + "; 3D biomes rejecting: " + top(rejecting, 8)
                + "; accepted by the game though the 3D biome rejects: " + top(accepting, 6));
        }

        private double pct(long n) {
            return 100.0 * n / Math.max(1, cells);
        }
    }

    /**
     * The biome of the column's climate at depth 0 (where the climate is two-dimensional, as in
     * vanilla's and Terrahaul's routers, this is the biome at the surface), or null if the biome
     * source is not a multi-noise source.
     */
    static Holder<Biome> climateSurfaceBiome(ChunkGenerator generator, Climate.Sampler sampler, int x, int z) {
        if (!(generator.getBiomeSource() instanceof MultiNoiseBiomeSource source)) {
            return null;
        }
        Climate.TargetPoint t = sampler.sample(QuartPos.fromBlock(x), 0, QuartPos.fromBlock(z));
        return source.getNoiseBiome(new Climate.TargetPoint(t.temperature(), t.humidity(), t.continentalness(), t.erosion(), 0L, t.weirdness()));
    }

    /**
     * The nearest column to x, z, on a grid of {@code step} blocks searched in square rings, whose
     * climate's surface biome (see {@link #climateSurfaceBiome}) is {@code key}; null if none
     * within {@code radius} blocks.
     */
    static int[] findSurfaceBiome(ServerLevel level, ResourceKey<Biome> key, int x, int z, int radius, int step) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        for (int r = 0; r * step <= radius; r++) {
            for (int i = -r; i <= r; i++) {
                for (int j = -r; j <= r; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != r) {
                        continue;
                    }
                    int px = x + i * step;
                    int pz = z + j * step;
                    Holder<Biome> b = climateSurfaceBiome(generator, sampler, px, pz);
                    if (b != null && b.is(key)) {
                        return new int[]{px, pz};
                    }
                }
            }
        }
        return null;
    }

    private static String top(Map<String, Long> counts, int limit) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return "none";
        }
        List<String> out = new ArrayList<>();
        counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(limit)
            .forEach(e -> out.add(String.format(Locale.ROOT, "%s %.1f%%", e.getKey(), 100.0 * e.getValue() / total)));
        return String.join(", ", out);
    }
}
