package com.terrahaul.client.water;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Finds the falling and running water around the camera for the water effects, a few chunk
 * sections per tick, and keeps what it found per section.
 * <p>
 * Water that falls or runs is never a source block, and a section's block palette lists every
 * state it may contain, so most sections are ruled out without reading a single block: those of
 * air, of rock, and those holding only still water (seas, lakes, pools). Only the rest are read
 * block by block. A section's result is kept and read again every few seconds (sooner near the
 * camera), when its chunk is loaded again, and never more than a few sections per tick, so the
 * cost stays small and flat however much water is around. What it finds:
 * <ul>
 *   <li>every column of falling water, at its foot: how tall it is, whether it lands in water or
 *       on the ground, which of its sides are open to the air and which way the water leaves the
 *       lip above it;</li>
 *   <li>running (flowing, not falling) water open to the sky within {@value #RAPID_REACH} blocks
 *       of falling water, a sample of up to {@value #MAX_RAPIDS} cells per section: the run-up to
 *       the lip of a drop, the spill over each step of a cascade or a stream down a slope, and the
 *       water running away from a fall's foot. Running water far from any drop (a spill on level
 *       ground, a farm's channels, the spreading edge of a spring) does not count.</li>
 * </ul>
 * {@link #rebuild} joins neighbouring columns into {@link Fall}s and the running water of each
 * section into a {@link RapidPatch}, for the particles and sounds to use.
 */
final class WaterScanner {
    static final byte BASE_POOL = 0;
    static final byte BASE_GROUND = 1;
    static final int[] DX = {0, 1, 0, -1};
    static final int[] DZ = {-1, 0, 1, 0};

    private static final int MAX_RAPIDS = 48;
    /** Running water counts as rapids this many blocks (across) from falling water at its level or one below. */
    private static final int RAPID_REACH = 2;
    private static final int MAX_RUN = 512;
    /** Furthest reach in chunks horizontally and in sections vertically. */
    static final int MAX_RADIUS = 7;
    static final int MAX_VERTICAL = 6;
    private static final int CHECKS_PER_TICK = 240;
    private static final int SCANS_PER_TICK = 4;
    private static final Column[] NO_COLUMNS = new Column[0];
    private static final long[] NO_CELLS = new long[0];
    /** Section offsets around the camera, nearest first. */
    private static final int[][] OFFSETS = offsets();

    /**
     * One column of falling water, described at its foot (the lowest falling block).
     *
     * @param lipY    the running water the column pours from, or the column's top block if it has none
     * @param open    sides open to the air half way down, a bit per direction of {@link #DX}/{@link #DZ}
     * @param dirX    downstream direction (the flow of the lip, or away from the rock behind)
     * @param surface height of the water surface (or ground) the column lands on
     */
    record Column(int x, int y, int z, int height, int lipY, byte base, byte open, float dirX, float dirZ, float surface) {
    }

    /** What a section holds. */
    private record Section(long scanned, Column[] columns, long[] rapids) {
        boolean empty() {
            return columns.length == 0 && rapids.length == 0;
        }

        boolean sameAs(Section other) {
            return Arrays.equals(columns, other.columns) && Arrays.equals(rapids, other.rapids);
        }
    }

    /** Running water of one section: cells (packed block positions) and their centre. */
    record RapidPatch(long key, long[] cells, double x, double y, double z) {
    }

    private final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private int cursor;
    private int version;

    /** Changes whenever a section's falls or rapids change, so the caller knows when to rebuild. */
    int version() {
        return version;
    }

    void clear() {
        if (!sections.isEmpty()) {
            sections.clear();
            version++;
        }
        cursor = 0;
    }

    /** Forgets the sections of a chunk (it was unloaded, or replaced by new data from the server). */
    void forgetChunk(int chunkX, int chunkZ, int minSection, int maxSection) {
        for (int sy = minSection; sy < maxSection; sy++) {
            Section old = sections.remove(SectionPos.asLong(chunkX, sy, chunkZ));
            if (old != null && !old.empty()) {
                version++;
            }
        }
    }

    /**
     * Checks the next sections around the camera: up to {@value #CHECKS_PER_TICK} palette checks
     * and {@value #SCANS_PER_TICK} block scans per tick.
     *
     * @param radius   horizontal reach in chunks
     * @param vertical vertical reach in sections
     */
    void tick(ClientLevel level, Vec3 camera, int radius, int vertical, long now) {
        int csx = SectionPos.blockToSectionCoord(camera.x);
        int csy = SectionPos.blockToSectionCoord(camera.y);
        int csz = SectionPos.blockToSectionCoord(camera.z);
        int minSection = level.getMinSection();
        int maxSection = level.getMaxSection();
        int scans = 0;
        for (int checks = 0; checks < CHECKS_PER_TICK && scans < SCANS_PER_TICK; checks++) {
            if (cursor >= OFFSETS.length) {
                cursor = 0;
                evict(csx, csy, csz, radius + 2, vertical + 2);
            }
            int[] o = OFFSETS[cursor];
            if (Math.abs(o[0]) > radius || Math.abs(o[2]) > radius || Math.abs(o[1]) > vertical) {
                cursor++;
                continue;
            }
            int sx = csx + o[0];
            int sy = csy + o[1];
            int sz = csz + o[2];
            if (sy < minSection || sy >= maxSection) {
                cursor++;
                continue;
            }
            long key = SectionPos.asLong(sx, sy, sz);
            Section old = sections.get(key);
            if (old != null && now - old.scanned < refresh(old, o)) {
                cursor++;
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, false);
            if (chunk == null) {
                if (old != null) {
                    sections.remove(key);
                    version += old.empty() ? 0 : 1;
                }
                cursor++;
                continue;
            }
            LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sy));
            Section result;
            if (section.hasOnlyAir() || !section.maybeHas(WaterScanner::movingWater)) {
                result = new Section(now, NO_COLUMNS, NO_CELLS);
            } else {
                result = scan(level, section, sx, sy, sz, now);
                scans++;
            }
            if (old == null ? !result.empty() : !old.sameAs(result)) {
                version++;
            }
            sections.put(key, result);
            cursor++;
        }
    }

    /** Ticks until a section is looked at again: water near the camera more often. */
    private static int refresh(Section section, int[] offset) {
        if (section.empty()) {
            return 60;
        }
        boolean near = Math.abs(offset[0]) <= 1 && Math.abs(offset[1]) <= 1 && Math.abs(offset[2]) <= 1;
        return near ? 40 : 100;
    }

    private void evict(int csx, int csy, int csz, int radius, int vertical) {
        var it = sections.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<Section> e = it.next();
            long key = e.getLongKey();
            if (Math.abs(SectionPos.x(key) - csx) > radius || Math.abs(SectionPos.z(key) - csz) > radius
                || Math.abs(SectionPos.y(key) - csy) > vertical) {
                version += e.getValue().empty() ? 0 : 1;
                it.remove();
            }
        }
    }

    private static boolean movingWater(BlockState state) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !fluid.isSource() && fluid.is(FluidTags.WATER);
    }

    static boolean falling(FluidState fluid) {
        return !fluid.isEmpty() && !fluid.isSource() && fluid.is(FluidTags.WATER)
            && fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING);
    }

    private Section scan(ClientLevel level, LevelChunkSection section, int sx, int sy, int sz, long now) {
        int bx = sx << 4;
        int by = sy << 4;
        int bz = sz << 4;
        List<Column> columns = new ArrayList<>();
        // falling water of the section, and its running water open to the sky (local indices)
        BitSet fallingCells = new BitSet(4096);
        IntArrayList open = new IntArrayList();
        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    FluidState fluid = section.getFluidState(lx, ly, lz);
                    if (fluid.isEmpty() || fluid.isSource() || !fluid.is(FluidTags.WATER)) {
                        continue;
                    }
                    int x = bx + lx;
                    int y = by + ly;
                    int z = bz + lz;
                    if (falling(fluid)) {
                        fallingCells.set(index(lx, ly, lz));
                        FluidState below = ly > 0 ? section.getFluidState(lx, ly - 1, lz) : level.getFluidState(pos.set(x, y - 1, z));
                        if (!falling(below)) {
                            Column c = measure(level, x, y, z, below);
                            if (c != null) {
                                columns.add(c);
                            }
                        }
                        continue;
                    }
                    BlockState above = ly < 15 ? section.getBlockState(lx, ly + 1, lz) : level.getBlockState(pos.set(x, y + 1, z));
                    if (above.isAir()) {
                        open.add(index(lx, ly, lz));
                    }
                }
            }
        }
        // the running water near a drop: keep an even random sample, seeded by the section, so
        // unchanged water gives the same sample when it is scanned again
        LongArrayList rapids = new LongArrayList();
        SplittableRandom random = new SplittableRandom(SectionPos.asLong(sx, sy, sz) * 0x9E3779B97F4A7C15L);
        int running = 0;
        for (int k = 0; k < open.size(); k++) {
            int i = open.getInt(k);
            int lx = i & 15;
            int lz = i >> 4 & 15;
            int ly = i >> 8;
            if (!nearFalling(level, fallingCells, bx, by, bz, lx, ly, lz)) {
                continue;
            }
            long cell = BlockPos.asLong(bx + lx, by + ly, bz + lz);
            running++;
            if (rapids.size() < MAX_RAPIDS) {
                rapids.add(cell);
            } else {
                int j = random.nextInt(running);
                if (j < MAX_RAPIDS) {
                    rapids.set(j, cell);
                }
            }
        }
        long[] cells = rapids.toLongArray();
        Arrays.sort(cells);
        return new Section(now, columns.isEmpty() ? NO_COLUMNS : columns.toArray(NO_COLUMNS), cells.length == 0 ? NO_CELLS : cells);
    }

    private static int index(int lx, int ly, int lz) {
        return ly << 8 | lz << 4 | lx;
    }

    /**
     * Whether falling water lies within {@value #RAPID_REACH} blocks across of a cell, at its level
     * (beside a fall's foot) or one below (the top of the fall it runs into). Inside the section
     * from the scan, outside it from the level.
     */
    private boolean nearFalling(ClientLevel level, BitSet fallingCells, int bx, int by, int bz, int lx, int ly, int lz) {
        for (int y = ly; y >= ly - 1; y--) {
            for (int dz = -RAPID_REACH; dz <= RAPID_REACH; dz++) {
                for (int dx = -RAPID_REACH; dx <= RAPID_REACH; dx++) {
                    int x = lx + dx;
                    int z = lz + dz;
                    boolean found = x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0
                        ? fallingCells.get(index(x, y, z))
                        : falling(level.getFluidState(pos.set(bx + x, by + y, bz + z)));
                    if (found) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Describes the column of falling water whose foot is at {@code x, y, z}; null while it is still settling. */
    private Column measure(ClientLevel level, int x, int y, int z, FluidState below) {
        int top = y;
        while (top - y < MAX_RUN && falling(level.getFluidState(pos.set(x, top + 1, z)))) {
            top++;
        }
        int height = top - y + 1;
        byte base;
        float surface;
        if (below.is(FluidTags.WATER)) {
            base = BASE_POOL;
            surface = y - 1 + below.getOwnHeight();
        } else if (level.getBlockState(pos.set(x, y - 1, z)).blocksMotion()) {
            base = BASE_GROUND;
            surface = y;
        } else {
            return null;
        }
        // the running water that pours over the edge, and the way it goes
        FluidState lip = level.getFluidState(pos.set(x, top + 1, z));
        int lipY = top;
        float dirX = 0.0F;
        float dirZ = 0.0F;
        if (lip.is(FluidTags.WATER)) {
            lipY = top + 1;
            Vec3 flow = lip.getFlow(level, pos);
            dirX = (float) flow.x;
            dirZ = (float) flow.z;
        }
        // sides open to the air half way down; spray leaves the curtain there
        int mid = y + height / 2;
        byte open = 0;
        int ox = 0;
        int oz = 0;
        for (int i = 0; i < 4; i++) {
            BlockState side = level.getBlockState(pos.set(x + DX[i], mid, z + DZ[i]));
            if (side.isAir() || !side.blocksMotion() && side.getFluidState().isEmpty()) {
                open |= (byte) (1 << i);
                ox += DX[i];
                oz += DZ[i];
            }
        }
        if (dirX * dirX + dirZ * dirZ < 0.01F) {
            // no lip flow (a source spilling straight down): away from the rock behind
            dirX = ox;
            dirZ = oz;
        }
        float len = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len > 1e-3F) {
            dirX /= len;
            dirZ /= len;
        } else {
            dirX = 0.0F;
            dirZ = 0.0F;
        }
        return new Column(x, y, z, height, lipY, base, open, dirX, dirZ, surface);
    }

    /**
     * Joins the columns of all kept sections into falls (columns whose feet touch, including
     * diagonally, and differ by at most three blocks in height) and collects the running water.
     */
    void rebuild(List<Fall> falls, List<RapidPatch> patches) {
        falls.clear();
        patches.clear();
        List<Column> all = new ArrayList<>();
        for (Long2ObjectMap.Entry<Section> e : sections.long2ObjectEntrySet()) {
            Section s = e.getValue();
            all.addAll(Arrays.asList(s.columns));
            long[] cells = s.rapids;
            if (cells.length > 0) {
                double x = 0.0;
                double y = 0.0;
                double z = 0.0;
                for (long cell : cells) {
                    x += BlockPos.getX(cell) + 0.5;
                    y += BlockPos.getY(cell) + 0.5;
                    z += BlockPos.getZ(cell) + 0.5;
                }
                patches.add(new RapidPatch(e.getLongKey(), cells, x / cells.length, y / cells.length, z / cells.length));
            }
        }
        int n = all.size();
        if (n == 0) {
            return;
        }
        // columns by their horizontal position; several may share one (a fall onto a ledge below another)
        Long2IntOpenHashMap first = new Long2IntOpenHashMap(n * 2);
        first.defaultReturnValue(-1);
        int[] next = new int[n];
        for (int i = 0; i < n; i++) {
            long xz = xz(all.get(i).x(), all.get(i).z());
            next[i] = first.get(xz);
            first.put(xz, i);
        }
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            Column c = all.get(i);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int j = first.get(xz(c.x() + dx, c.z() + dz)); j >= 0; j = next[j]) {
                        if (j != i && Math.abs(all.get(j).y() - c.y()) <= 3) {
                            union(parent, i, j);
                        }
                    }
                }
            }
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingInt(i -> find(parent, i)));
        int start = 0;
        while (start < n) {
            int root = find(parent, order[start]);
            int end = start;
            while (end < n && find(parent, order[end]) == root) {
                end++;
            }
            Column[] members = new Column[end - start];
            for (int k = start; k < end; k++) {
                members[k - start] = all.get(order[k]);
            }
            falls.add(new Fall(members));
            start = end;
        }
    }

    private static long xz(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[Math.max(ra, rb)] = Math.min(ra, rb);
        }
    }

    private static int[][] offsets() {
        List<int[]> list = new ArrayList<>();
        for (int dx = -MAX_RADIUS; dx <= MAX_RADIUS; dx++) {
            for (int dy = -MAX_VERTICAL; dy <= MAX_VERTICAL; dy++) {
                for (int dz = -MAX_RADIUS; dz <= MAX_RADIUS; dz++) {
                    list.add(new int[]{dx, dy, dz});
                }
            }
        }
        list.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2]));
        return list.toArray(new int[0][]);
    }
}
