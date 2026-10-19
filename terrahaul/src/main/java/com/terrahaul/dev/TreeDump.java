package com.terrahaul.dev;

import com.terrahaul.worldgen.trees.TreeBlueprint;
import com.terrahaul.worldgen.trees.TreeShapes;
import com.terrahaul.worldgen.trees.TreeSize;
import com.terrahaul.worldgen.trees.TreeSpecies;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Development checks of the procedural trees, meant to be called from {@link AutoTest} (or a
 * debugger) while a server runs:
 * <ul>
 *     <li>{@link #dump(Path, int)} builds blueprints of every species and size exactly as the
 *     feature does and writes ASCII side and top views with statistics (height, crown width, leaf
 *     and wood counts, leaves dropped for being out of reach of wood, wood pruned for being
 *     detached, cells clipped by the reach limit) to {@code trees.txt}.</li>
 *     <li>{@link #audit(ServerLevel, int, int, int, List)} inspects generated chunks for wood that
 *     floats (a face-connected group of logs, wood and mangrove roots resting on nothing) and for
 *     leaves that would decay (distance 7, not persistent).</li>
 * </ul>
 * The offline tool {@code TreeStudy} renders the same blueprints as pictures.
 */
public final class TreeDump {
    private TreeDump() {
    }

    /** Writes ASCII projections and statistics of {@code samples} trees per species and size. */
    public static void dump(Path dir, int samples) throws IOException {
        List<String> out = new ArrayList<>();
        for (TreeSpecies species : TreeSpecies.values()) {
            for (TreeSize size : TreeSize.values()) {
                for (int i = 0; i < samples; i++) {
                    TreeBlueprint bp = TreeShapes.create(species, size, new SplittableRandom(species.ordinal() * 7919L + size.ordinal() * 104_729L + i));
                    bp.finish();
                    int[] counts = new int[2];
                    bp.forEachCell((x, y, z, code) -> {
                        if (TreeBlueprint.isLeaf(code)) {
                            counts[0]++;
                        } else if (TreeBlueprint.isWoody(code)) {
                            counts[1]++;
                        }
                    });
                    out.add(String.format(Locale.ROOT, "== %s %s #%d: height %d, crown %dx%d, leaves %d, wood %d, orphan leaves %d, detached wood %d, clipped %d",
                        species.serializedName(), size.serializedName(), i, bp.maxY() + 1, bp.maxX() - bp.minX() + 1, bp.maxZ() - bp.minZ() + 1,
                        counts[0], counts[1], bp.orphans(), bp.detached(), bp.clipped()));
                    if (i == 0) {
                        out.addAll(sideView(bp));
                        out.addAll(topView(bp));
                    }
                }
            }
        }
        Files.createDirectories(dir);
        Files.write(dir.resolve("trees.txt"), out);
    }

    private static char glyph(int code) {
        if (TreeBlueprint.isWoody(code)) {
            return '#';
        }
        if (TreeBlueprint.isLeaf(code)) {
            return '*';
        }
        return code == TreeBlueprint.PROP_ROOT ? '+' : code == TreeBlueprint.ROOTED_DIRT ? ':' : ' ';
    }

    /** Side view (x across, y up): the nearest non-empty cell along z, wood drawn over leaves. */
    private static List<String> sideView(TreeBlueprint bp) {
        List<String> lines = new ArrayList<>();
        for (int y = bp.maxY(); y >= bp.minY(); y--) {
            StringBuilder sb = new StringBuilder("  ");
            for (int x = bp.minX(); x <= bp.maxX(); x++) {
                char c = ' ';
                for (int z = bp.minZ(); z <= bp.maxZ(); z++) {
                    char g = glyph(bp.get(x, y, z));
                    if (g == '#' || c == ' ') {
                        c = g;
                    }
                }
                sb.append(c);
            }
            lines.add(sb.toString().stripTrailing());
        }
        return lines;
    }

    /** Top view (x across, z down) with ground roots: R raised, r flush, : rooted dirt. */
    private static List<String> topView(TreeBlueprint bp) {
        List<String> lines = new ArrayList<>();
        lines.add("  -- top --");
        int x0 = Math.min(bp.minX(), -bp.reach());
        for (int z = -bp.reach(); z <= bp.reach(); z++) {
            StringBuilder sb = new StringBuilder("  ");
            for (int x = x0; x <= bp.reach(); x++) {
                char c = ' ';
                for (int y = bp.maxY(); y >= Math.max(0, bp.minY()) && c == ' '; y--) {
                    c = glyph(bp.get(x, y, z));
                }
                double rise = bp.groundRise(x, z);
                if (c == ' ' && !Double.isNaN(rise)) {
                    c = rise >= 1.0 ? 'R' : rise >= 0.0 ? 'r' : rise > -1.2 ? ':' : ' ';
                }
                sb.append(c);
            }
            String line = sb.toString().stripTrailing();
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static boolean isTreeWood(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
    }

    /**
     * Audits the generated chunks within {@code radius} chunks of {@code (cx, cz)}: floating wood
     * groups (none of their blocks rests on or touches ground) and leaves that would decay. Groups
     * touching the edge of the area count as supported, since their support may lie outside it.
     */
    public static void audit(ServerLevel level, int cx, int cz, int radius, List<String> report) {
        int minX = (cx - radius) << 4;
        int maxX = ((cx + radius + 1) << 4) - 1;
        int minZ = (cz - radius) << 4;
        int maxZ = ((cz + radius + 1) << 4) - 1;
        LongOpenHashSet wood = new LongOpenHashSet();
        long leaves = 0;
        long decaying = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.getStates().maybeHas(s -> isTreeWood(s) || s.is(BlockTags.LEAVES))) {
                        continue;
                    }
                    int baseY = level.getSectionYFromSectionIndex(i) << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState st = section.getBlockState(x, y, z);
                                if (isTreeWood(st)) {
                                    wood.add(BlockPos.asLong(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z));
                                } else if (st.is(BlockTags.LEAVES) && st.hasProperty(LeavesBlock.DISTANCE)) {
                                    leaves++;
                                    if (st.getValue(LeavesBlock.DISTANCE) >= 7 && !st.getValue(LeavesBlock.PERSISTENT)) {
                                        decaying++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int groups = 0;
        int floatingGroups = 0;
        long floatingBlocks = 0;
        List<String> examples = new ArrayList<>();
        for (long start : wood) {
            if (!seen.add(start)) {
                continue;
            }
            groups++;
            queue.enqueue(start);
            boolean supported = false;
            int size = 0;
            while (!queue.isEmpty()) {
                long k = queue.dequeueLong();
                size++;
                int x = BlockPos.getX(k);
                int y = BlockPos.getY(k);
                int z = BlockPos.getZ(k);
                if (x <= minX || x >= maxX || z <= minZ || z >= maxZ) {
                    supported = true;
                }
                for (Direction d : Direction.values()) {
                    long n = BlockPos.offset(k, d);
                    if (wood.contains(n)) {
                        if (seen.add(n)) {
                            queue.enqueue(n);
                        }
                    } else if (!supported && d != Direction.UP) {
                        BlockState st = level.getBlockState(pos.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ()));
                        if (!st.isAir() && !st.is(BlockTags.LEAVES) && st.getFluidState().isEmpty() && !st.is(Blocks.VINE)
                            && st.isCollisionShapeFullBlock(level, pos)) {
                            supported = true;
                        }
                    }
                }
            }
            if (!supported) {
                floatingGroups++;
                floatingBlocks += size;
                if (examples.size() < 8) {
                    examples.add(BlockPos.of(start).toShortString() + " (" + size + " blocks)");
                }
            }
        }
        report.add(String.format(Locale.ROOT, "  trees: %d wood groups, %d floating (%d blocks)%s; leaves %d, decaying %d",
            groups, floatingGroups, floatingBlocks, examples.isEmpty() ? "" : " e.g. " + String.join(", ", examples), leaves, decaying));
    }
}
