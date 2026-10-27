package com.terrahaul.client.lava;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Finds the lava open to the sky around the camera for the lava smoke, a few chunks per tick, and
 * keeps what it found per chunk.
 * <p>
 * Lava is open to the sky where it is the top of its column: the client's own motion-blocking
 * heightmap counts fluids, so a chunk is read in 256 lookups, one per column, without reading any
 * block below the top. Lava under a roof (a cave's lava, the magma chambers deep inside volcanoes)
 * never counts. For each chunk it keeps the number of such cells, their centre, and an even sample
 * of up to {@value #SAMPLES} of them (up to {@value #FLOW_SAMPLES} kept for flowing lava, so a flow
 * beside a lake is not drowned out by the lake's cells), each marked when it is flowing or falling
 * lava, or a lone cell with no open lava beside it. A chunk is read again every few seconds (sooner near the camera) and
 * when it is loaded again; never more than {@value #SCANS_PER_TICK} chunks per tick.
 */
final class LavaScanner {
    static final int MAX_RADIUS = 10;
    static final int SAMPLES = 32;
    /** Most slots of the sample flowing lava may take. */
    static final int FLOW_SAMPLES = 12;
    /** Flags of a sample. */
    static final byte FLOWING = 1;
    static final byte LONE = 2;
    private static final int SCANS_PER_TICK = 6;
    private static final int CHECKS_PER_TICK = 160;
    private static final Patch EMPTY = new Patch(0L, 0L, 0, new long[0], new byte[0], new float[0], 0.0, 0.0, 0.0);
    /** Chunk offsets around the camera, nearest first. */
    private static final int[][] OFFSETS = offsets();

    /**
     * The open lava of one chunk: when it was read, how many cells, an even sample of them (packed
     * block positions, their flags and the height of the lava's surface in the block) and the centre.
     */
    record Patch(long key, long scanned, int count, long[] cells, byte[] flags, float[] surface, double x, double y, double z) {
        boolean empty() {
            return count == 0;
        }

        Patch rescanned(long now) {
            return new Patch(key, now, count, cells, flags, surface, x, y, z);
        }
    }

    private final Long2ObjectOpenHashMap<Patch> chunks = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private int cursor;
    private int version;

    /** Changes whenever a chunk's lava changes, so the caller knows when to rebuild. */
    int version() {
        return version;
    }

    void clear() {
        if (!chunks.isEmpty()) {
            chunks.clear();
            version++;
        }
        cursor = 0;
    }

    /** Forgets a chunk (it was unloaded, or replaced by new data from the server). */
    void forgetChunk(int chunkX, int chunkZ) {
        Patch old = chunks.remove(ChunkPos.asLong(chunkX, chunkZ));
        if (old != null && !old.empty()) {
            version++;
        }
    }

    /** The chunks with open lava. */
    List<Patch> patches() {
        List<Patch> list = new ArrayList<>();
        for (Patch p : chunks.values()) {
            if (!p.empty()) {
                list.add(p);
            }
        }
        return list;
    }

    /** Reads the next chunks around the camera, up to {@code radius} chunks away. */
    void tick(ClientLevel level, Vec3 camera, int radius, long now) {
        int ccx = SectionPos.blockToSectionCoord(camera.x);
        int ccz = SectionPos.blockToSectionCoord(camera.z);
        int scans = 0;
        for (int checks = 0; checks < CHECKS_PER_TICK && scans < SCANS_PER_TICK; checks++) {
            if (cursor >= OFFSETS.length) {
                cursor = 0;
                evict(ccx, ccz, radius + 2);
            }
            int[] o = OFFSETS[cursor++];
            if (Math.abs(o[0]) > radius || Math.abs(o[1]) > radius) {
                continue;
            }
            int cx = ccx + o[0];
            int cz = ccz + o[1];
            long key = ChunkPos.asLong(cx, cz);
            Patch old = chunks.get(key);
            boolean near = Math.abs(o[0]) <= 1 && Math.abs(o[1]) <= 1;
            if (old != null && now - old.scanned() < (old.empty() ? 100 : near ? 40 : 80)) {
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
            if (chunk == null) {
                if (old != null) {
                    chunks.remove(key);
                    version += old.empty() ? 0 : 1;
                }
                continue;
            }
            Patch result = scan(level, chunk, key, now);
            scans++;
            if (old == null ? !result.empty() : !same(old, result)) {
                version++;
                chunks.put(key, result);
            } else {
                // unchanged: keep the old sample, read again later
                chunks.put(key, old == null ? result : old.rescanned(now));
            }
        }
    }

    private static boolean same(Patch a, Patch b) {
        return a.count() == b.count() && java.util.Arrays.equals(a.cells(), b.cells()) && java.util.Arrays.equals(a.flags(), b.flags());
    }

    private void evict(int ccx, int ccz, int radius) {
        var it = chunks.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<Patch> e = it.next();
            long key = e.getLongKey();
            if (Math.abs(ChunkPos.getX(key) - ccx) > radius || Math.abs(ChunkPos.getZ(key) - ccz) > radius) {
                version += e.getValue().empty() ? 0 : 1;
                it.remove();
            }
        }
    }

    private Patch scan(ClientLevel level, LevelChunk chunk, long key, long now) {
        int bx = chunk.getPos().getMinBlockX();
        int bz = chunk.getPos().getMinBlockZ();
        // the top of every column: open lava there, and its height
        int[] top = new int[256];
        boolean[] open = new boolean[256];
        boolean[] still = new boolean[256];
        int count = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz);
                int i = lz << 4 | lx;
                top[i] = y;
                FluidState fluid = y >= level.getMinBuildHeight() ? chunk.getFluidState(bx + lx, y, bz + lz) : null;
                if (fluid != null && fluid.is(FluidTags.LAVA)) {
                    open[i] = true;
                    still[i] = fluid.isSource();
                    count++;
                }
            }
        }
        if (count == 0) {
            return new Patch(key, now, 0, EMPTY.cells(), EMPTY.flags(), EMPTY.surface(), 0.0, 0.0, 0.0);
        }
        // an even sample of the still lava and one of the flowing lava, seeded by the chunk, so
        // unchanged lava gives the same sample again. A lava flow beside a crater's lake is a few
        // cells among hundreds of the lake's: it keeps up to FLOW_SAMPLES of the slots, so it smokes
        // as visibly as its own stretch of lava
        SplittableRandom random = new SplittableRandom(key * 0x9E3779B97F4A7C15L);
        int[] stillChosen = new int[SAMPLES];
        int[] flowChosen = new int[FLOW_SAMPLES];
        int ns = 0;
        int nf = 0;
        int seenStill = 0;
        int seenFlow = 0;
        double sx = 0.0;
        double sy = 0.0;
        double sz = 0.0;
        for (int i = 0; i < 256; i++) {
            if (!open[i]) {
                continue;
            }
            sx += bx + (i & 15) + 0.5;
            sy += top[i] + 1.0;
            sz += bz + (i >> 4) + 0.5;
            if (still[i]) {
                seenStill++;
                if (ns < SAMPLES) {
                    stillChosen[ns++] = i;
                } else {
                    int j = random.nextInt(seenStill);
                    if (j < SAMPLES) {
                        stillChosen[j] = i;
                    }
                }
            } else {
                seenFlow++;
                if (nf < FLOW_SAMPLES) {
                    flowChosen[nf++] = i;
                } else {
                    int j = random.nextInt(seenFlow);
                    if (j < FLOW_SAMPLES) {
                        flowChosen[j] = i;
                    }
                }
            }
        }
        int seen = seenStill + seenFlow;
        int n = Math.min(SAMPLES, nf + ns);
        int[] chosen = new int[n];
        System.arraycopy(flowChosen, 0, chosen, 0, nf);
        System.arraycopy(stillChosen, 0, chosen, nf, n - nf);
        java.util.Arrays.sort(chosen, 0, n);
        long[] cells = new long[n];
        byte[] flags = new byte[n];
        float[] surface = new float[n];
        for (int k = 0; k < n; k++) {
            int i = chosen[k];
            int lx = i & 15;
            int lz = i >> 4;
            int x = bx + lx;
            int z = bz + lz;
            int y = top[i];
            FluidState fluid = chunk.getFluidState(x, y, z);
            cells[k] = BlockPos.asLong(x, y, z);
            byte f = 0;
            if (!fluid.isSource() || fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING)) {
                f |= FLOWING;
            }
            if (!openNeighbour(level, open, top, lx, lz, x, y, z)) {
                f |= LONE;
            }
            flags[k] = f;
            surface[k] = fluid.getHeight(level, pos.set(x, y, z));
        }
        return new Patch(key, now, count, cells, flags, surface, sx / seen, sy / seen, sz / seen);
    }

    /** Whether open lava lies beside a cell, at its level or a block from it (inside the chunk from the scan, outside it from the level). */
    private boolean openNeighbour(ClientLevel level, boolean[] open, int[] top, int lx, int lz, int x, int y, int z) {
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : sides) {
            int nx = lx + d[0];
            int nz = lz + d[1];
            if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                int i = nz << 4 | nx;
                if (open[i] && Math.abs(top[i] - y) <= 1) {
                    return true;
                }
            } else {
                int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + d[0], z + d[1]) - 1;
                if (Math.abs(ny - y) <= 1 && level.getFluidState(pos.set(x + d[0], ny, z + d[1])).is(FluidTags.LAVA)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int[][] offsets() {
        List<int[]> list = new ArrayList<>();
        for (int dx = -MAX_RADIUS; dx <= MAX_RADIUS; dx++) {
            for (int dz = -MAX_RADIUS; dz <= MAX_RADIUS; dz++) {
                list.add(new int[]{dx, dz});
            }
        }
        list.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return list.toArray(new int[0][]);
    }
}
