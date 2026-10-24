package com.fantasyweapons.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds a whole tree from one of its logs, for the legendary axes: every log joined to it (corners too, for branches and
 * big trunks) and the leaves that grew on them. It is only a tree if some of those leaves grew there (leaves placed by
 * hand never count), so a log cabin is not felled as one. Nether trees count too: their stems, with wart blocks and
 * shroomlights for leaves.
 */
public final class TreeFelling {
    /** The most logs, and leaves, one tree may have (far beyond the biggest that grow). */
    public static final int MOST_LOGS = 600, MOST_LEAVES = 4000;
    /** How far leaves reach from the logs (natural leaves are at most six blocks from one). */
    private static final int LEAF_REACH = 7;

    /** A tree's logs (lowest first) and leaves. */
    public record Tree(List<BlockPos> logs, List<BlockPos> leaves) {
        /** Every block of it, logs first, the lowest first. */
        public List<BlockPos> blocks() {
            List<BlockPos> all = new ArrayList<>(logs.size() + leaves.size());
            all.addAll(logs);
            all.addAll(leaves);
            return all;
        }

        public BlockPos base() {
            return logs.get(0);
        }
    }

    private TreeFelling() {
    }

    public static boolean isLog(BlockState s) {
        return s.is(BlockTags.LOGS);
    }

    /** Leaves that grew on a tree (not placed by hand), or a nether tree's wart blocks and shroomlights. */
    public static boolean isNaturalLeaf(BlockState s) {
        if (s.getBlock() instanceof LeavesBlock) return s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT);
        return s.is(BlockTags.WART_BLOCKS) || s.is(Blocks.SHROOMLIGHT);
    }

    /**
     * The tree the log at {@code start} belongs to, or null if it is not a tree's. Logs already in {@code taken} (another
     * tree's) are left out, and this tree's are added to it.
     */
    @Nullable
    public static Tree find(Level level, BlockPos start, @Nullable Set<BlockPos> taken) {
        if (!isLog(level.getBlockState(start)) || (taken != null && taken.contains(start))) return null;
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        seen.add(start.immutable());
        open.add(start.immutable());
        while (!open.isEmpty() && logs.size() < MOST_LOGS) {
            BlockPos p = open.poll();
            logs.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (seen.contains(n) || (taken != null && taken.contains(n))) continue;
                        if (!isLog(level.getBlockState(n))) continue;
                        seen.add(n);
                        open.add(n);
                    }
                }
            }
        }
        List<BlockPos> leaves = leaves(level, logs);
        if (leaves.isEmpty()) return null;
        logs.sort(Comparator.comparingInt(BlockPos::getY));
        if (taken != null) taken.addAll(logs);
        return new Tree(logs, leaves);
    }

    /** The natural leaves within reach of the logs, spreading leaf to leaf. */
    private static List<BlockPos> leaves(Level level, List<BlockPos> logs) {
        List<BlockPos> leaves = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>(logs);
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        ArrayDeque<Integer> depth = new ArrayDeque<>();
        for (BlockPos log : logs) {
            for (Direction d : Direction.values()) {
                BlockPos n = log.relative(d);
                if (seen.add(n) && isNaturalLeaf(level.getBlockState(n))) {
                    open.add(n);
                    depth.add(1);
                }
            }
        }
        while (!open.isEmpty() && leaves.size() < MOST_LEAVES) {
            BlockPos p = open.poll();
            int d = depth.poll();
            leaves.add(p);
            if (d >= LEAF_REACH) continue;
            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                if (seen.add(n) && isNaturalLeaf(level.getBlockState(n))) {
                    open.add(n);
                    depth.add(d + 1);
                }
            }
        }
        return leaves;
    }
}
