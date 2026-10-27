package com.fantasyweapons.command;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.material.MaterialOreBlock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code /fw orestats}: a world generation survey for tall or reshaped worlds. Generates the chunks around a spot and
 * logs, for every legendary ore, how deep under the ground its blocks lie and in which biomes, next to how much of each
 * biome the area has (by the ground's biome and by every biome down the column), so its rarity in a biome can be
 * compared between world types.
 */
final class OreStats {
    private OreStats() {
    }

    static int run(CommandSourceStack source, BlockPos at, int radius) {
        ServerLevel level = source.getLevel();
        ChunkPos center = new ChunkPos(at);
        Map<Material, List<int[]>> ores = new EnumMap<>(Material.class);
        Map<Material, Map<String, Integer>> oreBiomes = new EnumMap<>(Material.class);
        Map<Material, Map<String, Integer>> oreGround = new EnumMap<>(Material.class);
        Map<Material, Integer> oreChunks = new EnumMap<>(Material.class);
        Map<String, Integer> ground = new TreeMap<>(), column = new TreeMap<>();
        List<Integer> surfaces = new ArrayList<>();
        Map<String, List<Integer>> groundHeights = new TreeMap<>();
        Map<String, Map<String, Integer>[]> rock = new TreeMap<>();
        int chunks = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(center.x + dx, center.z + dz);
                chunks++;
                ChunkPos cp = chunk.getPos();
                int surface = ground(level, cp.getMiddleBlockX(), cp.getMiddleBlockZ());
                surfaces.add(surface);
                String groundBiome = biome(level, new BlockPos(cp.getMiddleBlockX(), surface, cp.getMiddleBlockZ()));
                ground.merge(groundBiome, 1, Integer::sum);
                groundHeights.computeIfAbsent(groundBiome, k -> new ArrayList<>()).add(surface);
                // what the ground is made of, down to 200 blocks under it, in 20-block layers
                Map<String, Integer>[] layers = rock.computeIfAbsent(groundBiome, k -> newLayers());
                for (int d = 0; d < 200; d++) {
                    int y = surface - d;
                    if (y < level.getMinBuildHeight()) break;
                    var st = level.getBlockState(new BlockPos(cp.getMiddleBlockX(), y, cp.getMiddleBlockZ()));
                    layers[d / 20].merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath(), 1, Integer::sum);
                }
                Set<String> down = new HashSet<>();
                for (int y = level.getMinBuildHeight(); y <= surface; y += 8) down.add(biome(level, new BlockPos(cp.getMiddleBlockX(), y, cp.getMiddleBlockZ())));
                down.forEach(b -> column.merge(b, 1, Integer::sum));
                Set<Material> here = new HashSet<>();
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (!section.getStates().maybeHas(st -> st.getBlock() instanceof MaterialOreBlock)) continue;
                    int baseY = level.getSectionYFromSectionIndex(i) << 4;
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                        if (!(section.getBlockState(x, y, z).getBlock() instanceof MaterialOreBlock ore)) continue;
                        Material m = ore.material();
                        int wy = baseY + y;
                        ores.computeIfAbsent(m, k -> new ArrayList<>()).add(new int[]{wy, ground(level, cp.getMinBlockX() + x, cp.getMinBlockZ() + z) - wy});
                        oreBiomes.computeIfAbsent(m, k -> new TreeMap<>()).merge(biome(level, new BlockPos(cp.getMinBlockX() + x, wy, cp.getMinBlockZ() + z)), 1,
                                Integer::sum);
                        here.add(m);
                    }
                }
                for (Material m : here) {
                    oreChunks.merge(m, 1, Integer::sum);
                    oreGround.computeIfAbsent(m, k -> new TreeMap<>()).merge(groundBiome, 1, Integer::sum);
                }
            }
        }
        surfaces.sort(null);
        String head = "[orestats] " + level.dimension().location() + " at " + at.getX() + " " + at.getZ() + " r" + radius + ": " + chunks
                + " chunks; ground y " + spread(surfaces) + "; ground biomes " + ground + "; column biomes " + column;
        log(source, head);
        StringBuilder heights = new StringBuilder("[orestats] ground y by biome:");
        groundHeights.forEach((b, list) -> {
            list.sort(null);
            heights.append(' ').append(b).append('=').append(spread(list));
        });
        log(source, heights.toString());
        rock.forEach((b, layers) -> {
            StringBuilder line = new StringBuilder("[orestats] rock under " + b + ":");
            for (int i = 0; i < layers.length; i++) {
                int total = layers[i].values().stream().mapToInt(Integer::intValue).sum();
                if (total == 0) continue;
                line.append(" [").append(i * 20).append('-').append(i * 20 + 19).append("] ");
                layers[i].entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue()).limit(4)
                        .forEach(e -> line.append(e.getKey()).append(' ').append(Math.round(100f * e.getValue() / total)).append("% "));
            }
            log(source, line.toString());
        });
        ores.forEach((m, list) -> {
            List<Integer> ys = list.stream().map(a -> a[0]).sorted().toList(), depths = list.stream().map(a -> a[1]).sorted().toList();
            log(source, "[orestats] " + m.name() + ": " + list.size() + " ores in " + oreChunks.get(m) + " chunks; y " + spread(ys) + "; depth " + spread(depths)
                    + "; ore biomes " + oreBiomes.get(m) + "; chunk ground biomes " + oreGround.get(m));
        });
        return ores.size();
    }

    /**
     * The ground about a column as the legendary ores' fitted height takes it at generation (see
     * FittedHeightPlacement#ground): the middle one of the ground's heights in it and in four columns around it, the
     * trees grown on it since left out.
     */
    private static int ground(ServerLevel level, int x, int z) {
        int[] h = {top(level, x, z), top(level, x + 8, z), top(level, x - 8, z), top(level, x, z + 8), top(level, x, z - 8)};
        java.util.Arrays.sort(h);
        return h[2];
    }

    private static int top(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1, z);
        while (pos.getY() > level.getMinBuildHeight()) {
            var state = level.getBlockState(pos);
            if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && !state.is(Blocks.BROWN_MUSHROOM_BLOCK) && !state.is(Blocks.RED_MUSHROOM_BLOCK)
                    && !state.is(Blocks.MUSHROOM_STEM) && !state.is(Blocks.CACTUS) && !state.is(Blocks.BAMBOO)) break;
            pos.move(0, -1, 0);
        }
        return pos.getY();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer>[] newLayers() {
        Map<String, Integer>[] layers = new Map[10];
        for (int i = 0; i < layers.length; i++) layers[i] = new TreeMap<>();
        return layers;
    }

    /** Min / tenth / median / ninetieth percentile / max of a sorted list. */
    private static String spread(List<Integer> sorted) {
        int n = sorted.size();
        return sorted.getFirst() + "/" + sorted.get(n / 10) + "/" + sorted.get(n / 2) + "/" + sorted.get(n * 9 / 10) + "/" + sorted.getLast();
    }

    private static String biome(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(k -> k.location().getPath()).orElse("?");
    }

    private static void log(CommandSourceStack source, String line) {
        FantasyWeapons.LOGGER.info(line);
        source.sendSuccess(() -> Component.literal(line), false);
    }
}
