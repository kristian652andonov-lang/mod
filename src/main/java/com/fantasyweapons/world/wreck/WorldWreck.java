package com.fantasyweapons.world.wreck;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.config.ServerConfig;
import com.fantasyweapons.network.WreckPayload;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Really destroys the world for the ultimate weapons. A job rewrites whole chunk columns: a {@link ChunkShaper} edits
 * the chunk sections directly, the blocks it changed go straight to the players who see it (packed, see
 * {@link WreckPayload}), and once the column is relit its true light follows. Work is spread over the ticks under a time budget ({@code destruction.ms_per_tick}) and each chunk can
 * wait a delay, so destruction can sweep outwards like a wave. Chunks out of view are loaded for the rewrite and saved
 * with it, so nothing in reach is spared.
 */
public final class WorldWreck {
    /** Keeps a chunk loaded until its rewrite is done. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("fantasyweapons_wreck", Comparator.comparingLong(ChunkPos::toLong), 400);
    /** Give up on a chunk that will not load (in ticks of retries). */
    private static final int MAX_WAIT = 600;

    /**
     * @param partial one band of a chunk swept in several: its changed blocks are sent as they are, and only the chunk's
     *                last band relights and resends it whole
     */
    private record Task(ResourceKey<Level> dimension, int x, int z, long due, long order, long seed, ChunkShaper shaper, int waited, boolean partial) {
    }

    private static final PriorityQueue<Task> QUEUE = new PriorityQueue<>(Comparator.comparingLong(Task::due).thenComparingLong(Task::order));
    private static long clock;
    private static long order;
    private static int rewritten;
    /** Chunks whose relight finished, and chunks resent to at least one player (for diagnostics). */
    private static int relit, resent;

    /** DEVELOPMENT ONLY (GameTests): every task runs the moment it is due, without the time budget. */
    public static volatile boolean unbudgeted;

    private WorldWreck() {
    }

    /** Whether the server lets the ultimate weapons destroy the world. */
    public static boolean enabled() {
        return ServerConfig.DESTRUCTION.getOrDefault();
    }

    /**
     * The GameTest server runs many tests side by side in one world: there the abilities' destruction would tear up the
     * other tests' arenas, so it is left out (shapers are tested directly through {@link #rewrite}).
     */
    private static boolean testServer(ServerLevel level) {
        return level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer;
    }

    /** A destruction radius scaled by the server's {@code destruction.radius_scale}. */
    public static double radius(double radius) {
        return radius * ServerConfig.DESTRUCTION_RADIUS_SCALE.getOrDefault();
    }

    /** Rewrites the chunk at chunk coordinates {@code (cx, cz)} after {@code delay} ticks. Does nothing when destruction is off. */
    public static void submit(ServerLevel level, int cx, int cz, int delay, long seed, ChunkShaper shaper) {
        if (!enabled() || testServer(level)) return;
        submit(level, cx, cz, delay, seed, shaper, false);
    }

    private static void submit(ServerLevel level, int cx, int cz, int delay, long seed, ChunkShaper shaper, boolean partial) {
        if (!enabled() || testServer(level)) return;
        QUEUE.add(new Task(level.dimension(), cx, cz, clock + Math.max(0, delay), order++, seed, shaper, 0, partial));
    }

    /** Keeps a chunk loaded (and loads it) for the next 20 seconds, so a job due later finds it ready. */
    public static void hold(ServerLevel level, int cx, int cz) {
        if (!enabled() || testServer(level) || !ServerConfig.DESTRUCTION_LOADS_CHUNKS.getOrDefault()) return;
        ChunkPos pos = new ChunkPos(cx, cz);
        level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
    }

    /** Delay of a chunk from the distance (in blocks) of its centre to the centre of the job. */
    @FunctionalInterface
    public interface Delay {
        int ticks(double distance);
    }

    /**
     * Rewrites every chunk that touches the disc around {@code center} (horizontal distance), each after the delay of its
     * distance. The shaper still decides block by block what changes.
     *
     * @return how many chunks were queued
     */
    public static int disc(ServerLevel level, Vec3 center, double radius, long seed, Delay delay, ChunkShaper shaper) {
        if (!enabled() || radius <= 0) return 0;
        int c0x = SectionPos.blockToSectionCoord(center.x - radius), c1x = SectionPos.blockToSectionCoord(center.x + radius);
        int c0z = SectionPos.blockToSectionCoord(center.z - radius), c1z = SectionPos.blockToSectionCoord(center.z + radius);
        int n = 0;
        // the front sweeps block by block: a chunk it takes a while to cross is rewritten in rings, each when the front
        // reaches it, about one tick's travel wide (2 to 8 blocks)
        double slope = (delay.ticks(radius) - delay.ticks(0)) / Math.max(1, radius);
        double band = slope <= 1e-6 ? Double.MAX_VALUE : Math.max(2, Math.min(8, 1 / slope));
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                // nearest and farthest point of the chunk to the centre
                double nx = Math.max(cx * 16, Math.min(center.x, cx * 16 + 16)) - center.x;
                double nz = Math.max(cz * 16, Math.min(center.z, cz * 16 + 16)) - center.z;
                double near = Math.sqrt(nx * nx + nz * nz);
                if (near > radius) continue;
                double fx = Math.max(Math.abs(cx * 16 - center.x), Math.abs(cx * 16 + 16 - center.x));
                double fz = Math.max(Math.abs(cz * 16 - center.z), Math.abs(cz * 16 + 16 - center.z));
                double far = Math.sqrt(fx * fx + fz * fz);
                n++;
                if (far - near <= band || delay.ticks(far) == delay.ticks(near)) {
                    submit(level, cx, cz, delay.ticks(Math.hypot(cx * 16 + 8 - center.x, cz * 16 + 8 - center.z)), seed, shaper);
                    continue;
                }
                int bands = (int) Math.ceil((far - near) / band);
                for (int b = 0; b < bands; b++) {
                    double from = near + b * band, to = b == bands - 1 ? Double.MAX_VALUE : from + band;
                    boolean[] columns = new boolean[256];
                    boolean any = false;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            double d = Math.hypot(cx * 16 + x + 0.5 - center.x, cz * 16 + z + 0.5 - center.z);
                            if (d >= from && d < to) {
                                columns[x | z << 4] = true;
                                any = true;
                            }
                        }
                    }
                    if (!any) continue;
                    ChunkShaper part = c -> {
                        c.mask(columns);
                        try {
                            shaper.shape(c);
                        } finally {
                            c.mask(null);
                        }
                    };
                    submit(level, cx, cz, delay.ticks(Math.min(from + band * 0.5, far)), seed, part, b < bands - 1);
                }
            }
        }
        return n;
    }

    /** Chunks waiting to be rewritten. */
    public static int pending() {
        return QUEUE.size();
    }

    /** Chunks rewritten since the server started. */
    public static int rewritten() {
        return rewritten;
    }

    public static String stats() {
        return rewritten + " rewritten, " + relit + " relit, " + resent + " resent, " + QUEUE.size() + " pending";
    }

    public static void tick(MinecraftServer server) {
        clock++;
        if (QUEUE.isEmpty()) return;
        long budget = unbudgeted ? Long.MAX_VALUE : ServerConfig.DESTRUCTION_MS_PER_TICK.getOrDefault() * 1_000_000L;
        long start = System.nanoTime();
        boolean load = ServerConfig.DESTRUCTION_LOADS_CHUNKS.getOrDefault();
        java.util.List<Task> retry = new java.util.ArrayList<>();
        while (!QUEUE.isEmpty() && QUEUE.peek().due() <= clock && System.nanoTime() - start < budget) {
            Task t = QUEUE.poll();
            ServerLevel level = server.getLevel(t.dimension());
            if (level == null) continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(t.x(), t.z());
            if (chunk == null) {
                if (!load || t.waited() >= MAX_WAIT) continue;
                ChunkPos pos = new ChunkPos(t.x(), t.z());
                if (t.waited() % 200 == 0) level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
                retry.add(new Task(t.dimension(), t.x(), t.z(), clock + 10, t.order(), t.seed(), t.shaper(), t.waited() + 10, t.partial()));
                continue;
            }
            if (t.partial()) rewritePart(level, chunk, t.seed(), t.shaper());
            else rewrite(level, chunk, t.seed(), t.shaper());
        }
        QUEUE.addAll(retry);
    }

    /** Runs a shaper on a loaded chunk right now (also used by GameTests). */
    public static int rewrite(ServerLevel level, LevelChunk chunk, long seed, ChunkShaper shaper) {
        WreckChunk edit = new WreckChunk(level, chunk, seed);
        try {
            shaper.shape(edit);
        } catch (RuntimeException e) {
            FantasyWeapons.LOGGER.error("World destruction failed in chunk {}", chunk.getPos(), e);
        }
        if (edit.changed() == 0) return 0;
        rewritten++;
        edit.updateHeightmaps();
        edit.wakeWater();
        send(level, chunk, edit);
        chunk.initializeLightSources();
        chunk.setUnsaved(true);
        relight(level, chunk);
        return edit.changed();
    }

    /**
     * Runs one band of a chunk swept in several: its blocks change now and are sent to the players who see them (with
     * the sky let in where it opened); the chunk's last band relights it and sends them its true light.
     */
    private static void rewritePart(ServerLevel level, LevelChunk chunk, long seed, ChunkShaper shaper) {
        WreckChunk edit = new WreckChunk(level, chunk, seed);
        try {
            shaper.shape(edit);
        } catch (RuntimeException e) {
            FantasyWeapons.LOGGER.error("World destruction failed in chunk {}", chunk.getPos(), e);
        }
        if (edit.changed() == 0) return;
        rewritten++;
        edit.updateHeightmaps();
        edit.wakeWater();
        send(level, chunk, edit);
        chunk.setUnsaved(true);
    }

    /**
     * Sends the blocks a rewrite changed to the players who see the chunk, packed as runs down its columns: their game
     * writes them straight in (see {@link WreckPayload}) instead of taking each as a block update and lighting it.
     */
    private static void send(ServerLevel level, LevelChunk chunk, WreckChunk edit) {
        var players = level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false);
        if (players.isEmpty()) return;
        int[] runs = edit.runs();
        int step = WreckPayload.MAX_RUNS * 4;
        for (int from = 0; from < runs.length; from += step) {
            WreckPayload p = new WreckPayload(chunk.getPos().x, chunk.getPos().z, java.util.Arrays.copyOfRange(runs, from, Math.min(runs.length, from + step)));
            for (ServerPlayer pl : players) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(pl, p);
        }
    }

    /**
     * Throws away the column's light and lights it again from scratch, the way a freshly loaded chunk is lit, then sends
     * the rebuilt chunk to everyone who sees it. The light engine runs on its own thread, so this finishes a few ticks
     * later.
     */
    private static void relight(ServerLevel level, LevelChunk chunk) {
        ThreadedLevelLightEngine light = level.getChunkSource().getLightEngine();
        ChunkPos pos = chunk.getPos();
        light.retainData(pos, false);
        light.setLightEnabled(pos, false);
        for (int i = light.getMinLightSection(); i < light.getMaxLightSection(); i++) {
            light.queueSectionData(LightLayer.BLOCK, SectionPos.of(pos, i), null);
            light.queueSectionData(LightLayer.SKY, SectionPos.of(pos, i), null);
        }
        for (int i = level.getMinSection(); i < level.getMaxSection(); i++) light.updateSectionStatus(SectionPos.of(pos, i), true);
        MinecraftServer server = level.getServer();
        light.waitForPendingTasks(pos.x, pos.z).thenRunAsync(() -> {
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) != chunk) {
                resend(level, chunk);
                return;
            }
            light.initializeLight(chunk, true);
            light.lightChunk(chunk, false).thenRunAsync(() -> {
                relit++;
                sendLight(level, chunk);
            }, server);
        }, server);
    }

    /** Sends the chunk's light, worked out afresh, to everyone who sees it (they already have its blocks). */
    private static void sendLight(ServerLevel level, LevelChunk chunk) {
        var players = level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false);
        if (players.isEmpty()) return;
        var packet = new net.minecraft.network.protocol.game.ClientboundLightUpdatePacket(chunk.getPos(), level.getLightEngine(), null, null);
        for (ServerPlayer p : players) p.connection.send(packet);
        resent++;
    }

    private static void resend(ServerLevel level, LevelChunk chunk) {
        var players = level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false);
        if (players.isEmpty()) return;
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
        for (ServerPlayer p : players) p.connection.send(packet);
        resent++;
    }

    public static void clear() {
        QUEUE.clear();
    }
}
