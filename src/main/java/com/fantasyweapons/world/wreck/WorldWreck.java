package com.fantasyweapons.world.wreck;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.config.ServerConfig;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Really destroys the world for the ultimate weapons. A job rewrites whole chunk columns: a {@link ChunkShaper} edits
 * the chunk sections directly, then the column's heightmaps and light are rebuilt and the chunk is resent to the players
 * who see it. Work is spread over the ticks under a time budget ({@code destruction.ms_per_tick}) and each chunk can
 * wait a delay, so destruction can sweep outwards like a wave. Chunks out of view are loaded for the rewrite and saved
 * with it, so nothing in reach is spared.
 */
public final class WorldWreck {
    /** Keeps a chunk loaded until its rewrite is done. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("fantasyweapons_wreck", Comparator.comparingLong(ChunkPos::toLong), 400);
    private static final Set<Heightmap.Types> HEIGHTMAPS = EnumSet.of(Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR,
            Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
    /** Give up on a chunk that will not load (in ticks of retries). */
    private static final int MAX_WAIT = 600;

    private record Task(ResourceKey<Level> dimension, int x, int z, long due, long order, long seed, ChunkShaper shaper, int waited) {
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

    /** A destruction radius scaled by the server's {@code destruction.radius_scale}. */
    public static double radius(double radius) {
        return radius * ServerConfig.DESTRUCTION_RADIUS_SCALE.getOrDefault();
    }

    /** Rewrites the chunk at chunk coordinates {@code (cx, cz)} after {@code delay} ticks. Does nothing when destruction is off. */
    public static void submit(ServerLevel level, int cx, int cz, int delay, long seed, ChunkShaper shaper) {
        if (!enabled()) return;
        QUEUE.add(new Task(level.dimension(), cx, cz, clock + Math.max(0, delay), order++, seed, shaper, 0));
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
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                // nearest point of the chunk to the centre
                double nx = Math.max(cx * 16, Math.min(center.x, cx * 16 + 16)) - center.x;
                double nz = Math.max(cz * 16, Math.min(center.z, cz * 16 + 16)) - center.z;
                double near = Math.sqrt(nx * nx + nz * nz);
                if (near > radius) continue;
                double mid = Math.hypot(cx * 16 + 8 - center.x, cz * 16 + 8 - center.z);
                submit(level, cx, cz, delay.ticks(mid), seed, shaper);
                n++;
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
                retry.add(new Task(t.dimension(), t.x(), t.z(), clock + 10, t.order(), t.seed(), t.shaper(), t.waited() + 10));
                continue;
            }
            rewrite(level, chunk, t.seed(), t.shaper());
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
        Heightmap.primeHeightmaps(chunk, HEIGHTMAPS);
        chunk.initializeLightSources();
        chunk.setUnsaved(true);
        relight(level, chunk);
        return edit.changed();
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
                resend(level, chunk);
            }, server);
        }, server);
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
