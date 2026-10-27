package com.terrahaul.dev;

import com.terrahaul.registry.TerrahaulBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * AutoTest check of the stones that lie on sand: whether the pebbles, knee-high rocks and
 * boulders that come to rest on a beach (or on any other sand) are of the sand's own sandstone,
 * as asked, or of stone, andesite and the other rocks of the land behind it.
 * <p>
 * Two counts, by material:
 * <ul>
 *   <li>every Terrahaul pebble and rock of the area, at any height, by the block it lies on: sand
 *       (dry, under at most 5 blocks of water, or deeper), red sand or anything else, and apart
 *       from that the dry ones in beach biomes. Sea and lake floors keep the rock of their own
 *       province under deeper water (see the marine and lake features), so the dry ones and those
 *       in the surf on sand are the beach's;</li>
 *   <li>rock standing on sand: columns above water whose top is a run of rock (a boulder, a sea
 *       stack, a ruin) with loose sand right under it. Natural ground never has rock over sand
 *       (the surface rule lays sand over sandstone, and sandstone where sand would hang over
 *       air), so these are things placed on the sand. Moss on a boulder belongs to the run but is
 *       not counted as its material;</li>
 *   <li>rock on sand in the surf: the same under at most 5 blocks of water (a boulder or ledge on
 *       the sea floor off a beach, but also the rocky strip the surface rule lays at the foot of
 *       coast cliffs down to 6 blocks under sea level).</li>
 * </ul>
 * Totals over all areas are kept for {@link #summary}.
 */
final class StonesOnSand {
    private static final Map<String, Integer> TOTAL_ON_SAND = new TreeMap<>();
    private static final Map<String, Integer> TOTAL_ON_SAND_WET = new TreeMap<>();
    private static final Map<String, Integer> TOTAL_ON_SAND_DEEP = new TreeMap<>();
    private static final Map<String, Integer> TOTAL_ON_OTHER = new TreeMap<>();
    private static final Map<String, Integer> TOTAL_ROCK_ON_SAND = new TreeMap<>();
    private static final Map<String, Integer> TOTAL_ROCK_IN_SURF = new TreeMap<>();
    private static Set<Block> decor;

    private StonesOnSand() {
    }

    static void check(ServerLevel level, int cx, int cz, int radius, List<String> report) {
        long t0 = System.nanoTime();
        Set<Block> details = decor();
        Map<String, Integer> onSand = new TreeMap<>();
        Map<String, Integer> onSandWet = new TreeMap<>();
        Map<String, Integer> onSandDeep = new TreeMap<>();
        Map<String, Integer> onRedSand = new TreeMap<>();
        Map<String, Integer> onOther = new TreeMap<>();
        Map<String, Integer> onBeach = new TreeMap<>();
        List<String> stoneExamples = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                var sections = chunk.getSections();
                int bx = chunk.getPos().getMinBlockX();
                int bz = chunk.getPos().getMinBlockZ();
                for (int i = 0; i < sections.length; i++) {
                    var section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(s -> details.contains(s.getBlock()))) {
                        continue;
                    }
                    int y0 = level.getSectionYFromSectionIndex(i) << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState st = section.getBlockState(x, y, z);
                                if (!details.contains(st.getBlock())) {
                                    continue;
                                }
                                String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                                pos.set(bx + x, y0 + y, bz + z);
                                BlockState ground = level.getBlockState(pos.below());
                                boolean dry = st.getFluidState().isEmpty();
                                if (dry && ground.is(Blocks.SAND) && !id.startsWith("sandstone") && stoneExamples.size() < 4) {
                                    stoneExamples.add(pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + id);
                                }
                                Map<String, Integer> into = ground.is(Blocks.SAND) ? dry ? onSand : waterDepth(level, pos) <= 5 ? onSandWet : onSandDeep
                                    : ground.is(Blocks.RED_SAND) ? onRedSand : onOther;
                                into.merge(id, 1, Integer::sum);
                                if (dry && level.getBiome(pos).is(BiomeTags.IS_BEACH)) {
                                    onBeach.merge(id, 1, Integer::sum);
                                }
                            }
                        }
                    }
                }
            }
        }
        // rock standing on sand, above water and in the surf
        Map<String, Integer> rockOnSand = new HashMap<>();
        Map<String, Integer> rockOnBeachSand = new HashMap<>();
        Map<String, Integer> rockInSurf = new HashMap<>();
        List<String> examples = new ArrayList<>();
        List<String> surfExamples = new ArrayList<>();
        for (int x = (cx - radius) << 4; x < (cx + radius + 1) << 4; x++) {
            for (int z = (cz - radius) << 4; z < (cz + radius + 1) << 4; z++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockState st = level.getBlockState(pos.set(x, top, z));
                if (!st.getFluidState().isEmpty()) {
                    // under water: the rock on the floor, if at most 5 blocks of water lie over it
                    int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
                    if (!st.getFluidState().is(net.minecraft.tags.FluidTags.WATER) || top - floor > 5) {
                        continue;
                    }
                    String material = null;
                    st = level.getBlockState(pos.set(x, floor, z));
                    int y = floor;
                    for (int run = 0; run < 12 && rock(st); run++) {
                        if (material == null && !st.is(Blocks.MOSS_BLOCK)) {
                            material = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                        }
                        st = level.getBlockState(pos.set(x, --y, z));
                    }
                    if (material != null && st.is(BlockTags.SAND)) {
                        rockInSurf.merge(material, 1, Integer::sum);
                        if (surfExamples.size() < 4 && (surfExamples.isEmpty() || !surfExamples.get(surfExamples.size() - 1).endsWith(material))) {
                            surfExamples.add(x + " " + floor + " " + z + " " + material);
                        }
                    }
                    continue;
                }
                String material = null;
                int y = top;
                for (int run = 0; run < 12 && rock(st); run++) {
                    if (material == null && !st.is(Blocks.MOSS_BLOCK)) {
                        material = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                    }
                    st = level.getBlockState(pos.set(x, --y, z));
                }
                if (material != null && st.is(BlockTags.SAND)) {
                    rockOnSand.merge(material, 1, Integer::sum);
                    if (examples.size() < 4 && (examples.isEmpty() || !examples.get(examples.size() - 1).endsWith(material))) {
                        examples.add(x + " " + top + " " + z + " " + material);
                    }
                    if (level.getBiome(pos.set(x, top, z)).is(BiomeTags.IS_BEACH)) {
                        rockOnBeachSand.merge(material, 1, Integer::sum);
                    }
                }
            }
        }
        onSand.forEach((k, v) -> TOTAL_ON_SAND.merge(k, v, Integer::sum));
        onSandWet.forEach((k, v) -> TOTAL_ON_SAND_WET.merge(k, v, Integer::sum));
        onSandDeep.forEach((k, v) -> TOTAL_ON_SAND_DEEP.merge(k, v, Integer::sum));
        onRedSand.forEach((k, v) -> TOTAL_ON_SAND.merge(k + " (red sand)", v, Integer::sum));
        onOther.forEach((k, v) -> TOTAL_ON_OTHER.merge(k, v, Integer::sum));
        rockOnSand.forEach((k, v) -> TOTAL_ROCK_ON_SAND.merge(k, v, Integer::sum));
        rockInSurf.forEach((k, v) -> TOTAL_ROCK_IN_SURF.merge(k, v, Integer::sum));
        report.add(String.format(Locale.ROOT, "  stones on sand (%.0f ms): dry pebbles and rocks on sand %d [%s]; under 1-5 blocks of water on sand %d [%s];"
                + " deeper on sand %d [%s]; on red sand %d [%s];"
                + " on other ground %d [%s]; dry in beach biomes %d [%s]%s", (System.nanoTime() - t0) / 1e6,
            sum(onSand), entries(onSand), sum(onSandWet), entries(onSandWet), sum(onSandDeep), entries(onSandDeep), sum(onRedSand), entries(onRedSand),
            sum(onOther), entries(onOther),
            sum(onBeach), entries(onBeach), stoneExamples.isEmpty() ? "" : "; dry stone on sand e.g. " + String.join("; ", stoneExamples)));
        report.add(String.format(Locale.ROOT, "    rock standing on sand (columns): %d [%s]; of them in beach biomes %d [%s]%s",
            sum(rockOnSand), entries(rockOnSand), sum(rockOnBeachSand), entries(rockOnBeachSand),
            examples.isEmpty() ? "" : "; e.g. " + String.join("; ", examples)));
        report.add(String.format(Locale.ROOT, "    rock on sand under 1-5 blocks of water (columns): %d [%s]%s",
            sum(rockInSurf), entries(rockInSurf), surfExamples.isEmpty() ? "" : "; e.g. " + String.join("; ", surfExamples)));
    }

    /** Totals over every area checked so far. */
    static void summary(List<String> report) {
        report.add("stones on sand, all areas: dry pebbles and rocks on sand " + sum(TOTAL_ON_SAND) + " [" + entries(TOTAL_ON_SAND) + "]");
        report.add("  under 1-5 blocks of water on sand " + sum(TOTAL_ON_SAND_WET) + " [" + entries(TOTAL_ON_SAND_WET) + "]");
        report.add("  under deeper water on sand " + sum(TOTAL_ON_SAND_DEEP) + " [" + entries(TOTAL_ON_SAND_DEEP) + "]");
        report.add("  pebbles and rocks on other ground " + sum(TOTAL_ON_OTHER) + " [" + entries(TOTAL_ON_OTHER) + "]");
        report.add("  rock standing on sand (columns) " + sum(TOTAL_ROCK_ON_SAND) + " [" + entries(TOTAL_ROCK_ON_SAND) + "]");
        report.add("  rock on sand under 1-5 blocks of water (columns) " + sum(TOTAL_ROCK_IN_SURF) + " [" + entries(TOTAL_ROCK_IN_SURF) + "]");
    }

    /** Water blocks from {@code pos} (a waterlogged stone counts as the first) up to the surface. */
    private static int waterDepth(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        int depth = 0;
        while (level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER) && depth < 64) {
            depth++;
            p.move(0, 1, 0);
        }
        return depth;
    }

    /** Rock as a boulder, formation or ruin wall is made of: stone of any kind, never soil, sand or plants. */
    private static boolean rock(BlockState st) {
        return st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.TERRACOTTA) || st.is(Blocks.COBBLESTONE) || st.is(Blocks.MOSSY_COBBLESTONE)
            || st.is(Blocks.SANDSTONE) || st.is(Blocks.SMOOTH_SANDSTONE) || st.is(Blocks.CUT_SANDSTONE) || st.is(Blocks.CHISELED_SANDSTONE)
            || st.is(Blocks.RED_SANDSTONE) || st.is(Blocks.SMOOTH_RED_SANDSTONE) || st.is(Blocks.CUT_RED_SANDSTONE)
            || st.is(Blocks.CALCITE) || st.is(Blocks.BASALT) || st.is(Blocks.SMOOTH_BASALT) || st.is(Blocks.BLACKSTONE)
            || st.is(Blocks.STONE_BRICKS) || st.is(Blocks.MOSSY_STONE_BRICKS) || st.is(Blocks.CRACKED_STONE_BRICKS) || st.is(Blocks.MOSS_BLOCK);
    }

    private static Set<Block> decor() {
        if (decor == null) {
            Set<Block> set = new HashSet<>();
            TerrahaulBlocks.PEBBLES.values().forEach(b -> set.add(b.get()));
            TerrahaulBlocks.ROCKS.values().forEach(b -> set.add(b.get()));
            decor = set;
        }
        return decor;
    }

    private static int sum(Map<String, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static String entries(Map<String, Integer> counts) {
        return counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(12)
            .map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining(", "));
    }
}
