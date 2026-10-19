package com.terrahaul.dev;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finds floating terrain fragments: connected groups of solid blocks near the surface that do not
 * connect to the ground. Pure Java, so the in-game {@link AutoTest} (on generated blocks) and the
 * offline devtools (on a simulated density field) share the same definition.
 * <p>
 * The scan covers, in every column of a rectangular area, a window from the column's highest
 * solid block down to {@code depth} blocks below the lowest top of the column and its four
 * neighbours, so that on steep faces the windows of neighbouring columns overlap and real
 * connections are followed. Solid blocks are joined across faces (6-connectivity). A group is
 * grounded when it contains a block at the bottom of its column's window (the solid ground
 * continues below) or touches the side of the area (it may continue outside, so it is not judged).
 * Every other group is followed once more through all solid blocks of the area, below the windows
 * too, and is a floating fragment only if it reaches neither the side of the area nor 64 blocks
 * below its lowest block.
 */
public final class FloatingFragments {
    /** Solidity of a block, {@code true} for terrain material. */
    @FunctionalInterface
    public interface Solid {
        boolean test(int x, int y, int z);
    }

    /** One floating group: its block count, bounding box and one of its blocks. */
    public record Fragment(int blocks, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int seedX, int seedY, int seedZ) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%d blocks at %d %d %d (box %dx%dx%d)", blocks, (minX + maxX) / 2, maxY, (minZ + maxZ) / 2,
                maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        }
    }

    /** Scan result: number of floating groups, their total size, a size histogram and the largest groups. */
    public record Result(int fragments, long blocks, int[] histogram, List<Fragment> largest) {
        /** Histogram buckets: 1 block, 2-9, 10-99, 100-999, 1000+. */
        public static final String[] BUCKETS = {"1", "2-9", "10-99", "100-999", "1000+"};

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(fragments).append(" floating fragments, ").append(blocks).append(" blocks; sizes");
            for (int i = 0; i < BUCKETS.length; i++) {
                sb.append(' ').append(BUCKETS[i]).append(':').append(histogram[i]);
            }
            return sb.toString();
        }
    }

    private FloatingFragments() {
    }

    /**
     * Scans the area {@code [x0, x0 + sizeX) x [z0, z0 + sizeZ)}.
     *
     * @param top   highest solid block of each column (indexed {@code dx * sizeZ + dz}), or
     *              {@link Integer#MIN_VALUE} for a column without one
     * @param depth window depth below the lowest top of the column and its four neighbours
     * @param keep  number of largest fragments to report
     */
    public static Result scan(Solid solid, int x0, int z0, int sizeX, int sizeZ, int[] top, int depth, int keep) {
        int columns = sizeX * sizeZ;
        int[] bottom = new int[columns];
        long[] offset = new long[columns + 1];
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                int c = dx * sizeZ + dz;
                int low = top[c];
                if (low != Integer.MIN_VALUE) {
                    low = lower(low, dx > 0 ? top[c - sizeZ] : Integer.MIN_VALUE);
                    low = lower(low, dx < sizeX - 1 ? top[c + sizeZ] : Integer.MIN_VALUE);
                    low = lower(low, dz > 0 ? top[c - 1] : Integer.MIN_VALUE);
                    low = lower(low, dz < sizeZ - 1 ? top[c + 1] : Integer.MIN_VALUE);
                    bottom[c] = low - depth;
                }
                offset[c + 1] = offset[c] + (top[c] == Integer.MIN_VALUE ? 0 : top[c] - bottom[c] + 1);
            }
        }
        int total = (int) offset[columns];
        // bit per window block: solid, then visited
        long[] solidBits = new long[(total + 63) >> 6];
        long[] seen = new long[solidBits.length];
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                int c = dx * sizeZ + dz;
                for (int y = bottom[c], i = (int) offset[c]; i < offset[c + 1]; y++, i++) {
                    if (solid.test(x0 + dx, y, z0 + dz)) {
                        solidBits[i >> 6] |= 1L << i;
                    }
                }
            }
        }
        int[] queue = new int[total];
        List<Fragment> found = new ArrayList<>();
        int[] histogram = new int[5];
        long blocks = 0;
        int count = 0;
        int c = 0;
        for (int start = 0; start < total; start++) {
            if ((solidBits[start >> 6] & (1L << start)) == 0 || (seen[start >> 6] & (1L << start)) != 0) {
                continue;
            }
            seen[start >> 6] |= 1L << start;
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            boolean grounded = false;
            int size = 0;
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            while (head < tail) {
                int i = queue[head++];
                size++;
                c = column(offset, i, c);
                int k = i - (int) offset[c];
                int dx = c / sizeZ;
                int dz = c - dx * sizeZ;
                int y = bottom[c] + k;
                minX = Math.min(minX, dx);
                maxX = Math.max(maxX, dx);
                minZ = Math.min(minZ, dz);
                maxZ = Math.max(maxZ, dz);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                if (k == 0 || dx == 0 || dz == 0 || dx == sizeX - 1 || dz == sizeZ - 1) {
                    grounded = true;
                }
                // vertical neighbours in the same column
                if (k > 0) {
                    tail = push(solidBits, seen, queue, tail, i - 1);
                }
                if (i + 1 < offset[c + 1]) {
                    tail = push(solidBits, seen, queue, tail, i + 1);
                }
                // horizontal neighbours at the same absolute height, if inside their window
                if (dx > 0) {
                    tail = pushAt(solidBits, seen, queue, tail, c - sizeZ, y, top, bottom, offset);
                }
                if (dx < sizeX - 1) {
                    tail = pushAt(solidBits, seen, queue, tail, c + sizeZ, y, top, bottom, offset);
                }
                if (dz > 0) {
                    tail = pushAt(solidBits, seen, queue, tail, c - 1, y, top, bottom, offset);
                }
                if (dz < sizeZ - 1) {
                    tail = pushAt(solidBits, seen, queue, tail, c + 1, y, top, bottom, offset);
                }
            }
            if (!grounded) {
                // the windows hide connections that dip below them (around the foot of an overhang,
                // say): follow the group through the whole area before calling it floating
                int sc = column(offset, start, c);
                int sdx = sc / sizeZ;
                grounded = reachesGround(solid, x0 + sdx, bottom[sc] + start - (int) offset[sc], z0 + sc - sdx * sizeZ,
                    x0, z0, sizeX, sizeZ, minY - CONFIRM_DEPTH);
            }
            if (!grounded) {
                count++;
                blocks += size;
                histogram[size == 1 ? 0 : size < 10 ? 1 : size < 100 ? 2 : size < 1000 ? 3 : 4]++;
                int sc = column(offset, start, c);
                int sdx = sc / sizeZ;
                found.add(new Fragment(size, x0 + minX, minY, z0 + minZ, x0 + maxX, maxY, z0 + maxZ,
                    x0 + sdx, bottom[sc] + start - (int) offset[sc], z0 + sc - sdx * sizeZ));
            }
        }
        found.sort(Comparator.comparingInt(Fragment::blocks).reversed());
        return new Result(count, blocks, histogram, new ArrayList<>(found.subList(0, Math.min(keep, found.size()))));
    }

    /** How far below its lowest block a group must reach, through any solid blocks, to be grounded. */
    private static final int CONFIRM_DEPTH = 64;
    /** Most blocks the confirmation visits; a group that large is taken to be grounded. */
    private static final int CONFIRM_BUDGET = 200_000;

    /**
     * Whether the solid blocks connected to (x, y, z) reach down to {@code groundY} or the side of
     * the area, following them anywhere in the area (not only in the scan windows).
     */
    private static boolean reachesGround(Solid solid, int x, int y, int z, int x0, int z0, int sizeX, int sizeZ, int groundY) {
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<long[]> queue = new java.util.ArrayDeque<>();
        queue.add(new long[]{x, y, z});
        seen.add(key(x, y, z));
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            long[] p = queue.poll();
            int px = (int) p[0];
            int py = (int) p[1];
            int pz = (int) p[2];
            if (py <= groundY || px <= x0 || pz <= z0 || px >= x0 + sizeX - 1 || pz >= z0 + sizeZ - 1 || seen.size() > CONFIRM_BUDGET) {
                return true;
            }
            for (int[] d : dirs) {
                int nx = px + d[0];
                int ny = py + d[1];
                int nz = pz + d[2];
                if (solid.test(nx, ny, nz) && seen.add(key(nx, ny, nz))) {
                    queue.add(new long[]{nx, ny, nz});
                }
            }
        }
        return false;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int lower(int a, int b) {
        return b == Integer.MIN_VALUE ? a : Math.min(a, b);
    }

    /** Column of window block {@code i}: a search from the last column found (queues stay local). */
    private static int column(long[] offset, int i, int hint) {
        if (i >= offset[hint] && i < offset[hint + 1]) {
            return hint;
        }
        int lo = 0;
        int hi = offset.length - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (offset[mid] <= i) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private static int push(long[] solidBits, long[] seen, int[] queue, int tail, int i) {
        long bit = 1L << i;
        if ((solidBits[i >> 6] & bit) != 0 && (seen[i >> 6] & bit) == 0) {
            seen[i >> 6] |= bit;
            queue[tail++] = i;
        }
        return tail;
    }

    private static int pushAt(long[] solidBits, long[] seen, int[] queue, int tail, int c, int y, int[] top, int[] bottom, long[] offset) {
        if (top[c] == Integer.MIN_VALUE || y < bottom[c] || y > top[c]) {
            return tail;
        }
        return push(solidBits, seen, queue, tail, (int) offset[c] + y - bottom[c]);
    }
}
