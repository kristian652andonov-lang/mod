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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Really destroys the world for the ultimate weapons. A job rewrites whole chunk columns, and is built to land all at
 * once: each chunk's {@link ChunkShaper} runs on a copy of the chunk off the main thread, on every core together; the
 * main thread then swaps the rewritten sections in whole, sends them whole to the players who see them (see
 * {@link WreckPayload}), and the column is relit, its true light following. Everything a power sets going in one tick
 * is a batch that lands together, all of it at once, the moment the first of it is due ({@code destruction.instant}):
 * it is worked out ahead of time where it can be, so it is ready then. Chunks out of view are loaded for the rewrite
 * and saved with it, so nothing in reach is spared.
 */
public final class WorldWreck {
    /** Keeps a chunk loaded until its rewrite is done. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("fantasyweapons_wreck", Comparator.comparingLong(ChunkPos::toLong), 400);
    /** Give up on a chunk that will not load (or be generated) within this many ticks of first being wanted. */
    private static final int MAX_WAIT = 1200;

    /**
     * @param waited how many times it found its chunk not yet loaded
     * @param since  when it first found its chunk not loaded (-1: not yet)
     */
    private record Task(ResourceKey<Level> dimension, int x, int z, long due, long order, long seed, ChunkShaper shaper, int waited, long since, Batch batch) {
        Task at(long when) {
            return new Task(dimension, x, z, when, order, seed, shaper, waited, since, batch);
        }

        Task retry(long when, long now) {
            return new Task(dimension, x, z, when, order, seed, shaper, waited + 1, since < 0 ? now : since, batch);
        }
    }

    /**
     * Everything a power set going in one tick: it lands together, once every chunk of it is worked out (or, if some
     * cannot be, a few seconds after it was due).
     */
    private static final class Batch {
        int size, ready;
        long due;
    }

    /** A chunk being rewritten off the main thread: its plan (one job, or several of one batch in turn), and its chunk. */
    private static final class Flight {
        final Key key;
        final ServerLevel level;
        final LevelChunk chunk;
        final Batch batch;
        final List<Task> tasks = new ArrayList<>();
        CompletableFuture<WreckChunk> plan;
        long due;
        boolean counted;

        Flight(Key key, ServerLevel level, LevelChunk chunk, Task first) {
            this.key = key;
            this.level = level;
            this.chunk = chunk;
            this.batch = first.batch();
            tasks.add(first);
            due = first.due();
        }
    }

    private record Key(ResourceKey<Level> dimension, long chunk) {
    }

    /** How far ahead of its time a job may start being worked out (ticks), so it is ready to land the moment it is due. */
    private static final int AHEAD = 600;
    /**
     * How long a batch waits past its time for the last of its chunks, before what is ready lands anyway: long, so a slow
     * machine still has it land all at once, if a little late, rather than in pieces.
     */
    private static final int GIVE_UP = 300;
    /** The most chunks worked out and waiting at once (each holds a copy of its chunk), past which they land as they are ready. */
    private static final int MOST_WAITING = 1500;
    /** Milliseconds a tick may spend landing a batch that is all ready, so it lands in a tick or two. */
    private static final long BATCH_MS = 150;

    private static final PriorityQueue<Task> QUEUE = new PriorityQueue<>(Comparator.comparingLong(Task::due).thenComparingLong(Task::order));
    /** Jobs set going this tick, gathered so they land together. */
    private static final List<Task> FRESH = new ArrayList<>();
    private static final ArrayDeque<Flight> FLIGHTS = new ArrayDeque<>();
    /**
     * Chunks with a rewrite in flight: a job of the same batch carries on from where it got to, one of a later batch
     * waits for it to land, so each works on what the last one left.
     */
    private static final java.util.Map<Key, Flight> BUSY = new java.util.HashMap<>();
    private static ExecutorService pool;
    private static long clock;
    private static long order;
    private static int rewritten;
    /** Chunks whose relight finished, and chunks whose light was sent to at least one player (for diagnostics). */
    private static int relit, resent;
    /** Jobs given up on: their chunk would not load in time, or its level went away (for diagnostics). */
    private static int dropped, untouched;

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

    /** Whether jobs submitted in this level will really be done (destruction on, and not the GameTest server). */
    public static boolean active(ServerLevel level) {
        return enabled() && !testServer(level);
    }

    /** A destruction radius scaled by the server's {@code destruction.radius_scale}. */
    public static double radius(double radius) {
        return radius * ServerConfig.DESTRUCTION_RADIUS_SCALE.getOrDefault();
    }

    /** Rewrites the chunk at chunk coordinates {@code (cx, cz)} after {@code delay} ticks. Does nothing when destruction is off. */
    public static void submit(ServerLevel level, int cx, int cz, int delay, long seed, ChunkShaper shaper) {
        if (!enabled() || testServer(level)) return;
        FRESH.add(new Task(level.dimension(), cx, cz, clock + Math.max(0, delay), order++, seed, shaper, 0, -1, null));
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
     * distance (all together, at the first, when the destruction is instant). The shaper still decides block by block
     * what changes.
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
                double nx = Math.max(cx * 16, Math.min(center.x, cx * 16 + 16)) - center.x;
                double nz = Math.max(cz * 16, Math.min(center.z, cz * 16 + 16)) - center.z;
                if (Math.sqrt(nx * nx + nz * nz) > radius || !shaper.reaches(cx, cz)) continue;
                submit(level, cx, cz, delay.ticks(Math.hypot(cx * 16 + 8 - center.x, cz * 16 + 8 - center.z)), seed, shaper);
                n++;
            }
        }
        return n;
    }

    /** Chunks waiting to be rewritten, or being rewritten. */
    public static int pending() {
        return QUEUE.size() + FRESH.size() + FLIGHTS.size();
    }

    /** Chunks rewritten since the server started. */
    public static int rewritten() {
        return rewritten;
    }

    public static String stats() {
        return rewritten + " rewritten, " + relit + " relit, " + resent + " resent, " + pending() + " pending, " + dropped + " dropped, " + untouched + " untouched";
    }

    /** Worker threads: every core but the two the game needs for itself, at least one ({@code destruction.threads}). */
    private static ExecutorService pool() {
        if (pool == null) {
            int threads = ServerConfig.DESTRUCTION_THREADS.getOrDefault();
            if (threads <= 0) threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
            AtomicInteger n = new AtomicInteger();
            pool = Executors.newFixedThreadPool(threads, r -> {
                Thread t = new Thread(r, "Fantasy Weapons destruction " + n.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });
        }
        return pool;
    }

    public static void tick(MinecraftServer server) {
        clock++;
        gather();
        if (QUEUE.isEmpty() && FLIGHTS.isEmpty()) return;
        long budget = unbudgeted ? Long.MAX_VALUE : ServerConfig.DESTRUCTION_MS_PER_TICK.getOrDefault() * 1_000_000L;
        long start = System.nanoTime();
        land(budget, start);
        launch(server, budget, start);
    }

    /**
     * What was set going this tick joins the queue as one batch: due together, at its first, when the destruction is
     * instant (otherwise each chunk at its own time, landing as it is ready).
     */
    private static void gather() {
        if (FRESH.isEmpty()) return;
        if (ServerConfig.DESTRUCTION_INSTANT.getOrDefault()) {
            Batch batch = new Batch();
            batch.size = FRESH.size();
            batch.due = Long.MAX_VALUE;
            for (Task t : FRESH) batch.due = Math.min(batch.due, t.due());
            for (Task t : FRESH) QUEUE.add(new Task(t.dimension(), t.x(), t.z(), batch.due, t.order(), t.seed(), t.shaper(), 0, -1, batch));
        } else {
            for (Task t : FRESH) {
                Batch alone = new Batch();
                alone.size = 1;
                alone.due = t.due();
                QUEUE.add(new Task(t.dimension(), t.x(), t.z(), t.due(), t.order(), t.seed(), t.shaper(), 0, -1, alone));
            }
        }
        FRESH.clear();
    }

    /** A task given up on (its chunk will not load): its batch no longer waits for it. */
    private static void drop(Task t) {
        t.batch().size--;
        dropped++;
    }

    /**
     * Puts the rewrites that are worked out into their chunks, a batch at a time once all of it is ready and due - in a
     * tick or two, so it lands all at once - within the budget.
     */
    private static void land(long budget, long start) {
        boolean complete = false;
        for (Flight f : FLIGHTS) {
            if (!f.counted && f.plan.isDone()) {
                f.counted = true;
                f.batch.ready += f.tasks.size();
            }
        }
        for (Flight f : FLIGHTS) {
            if (f.counted && clock >= f.due && (f.batch.ready >= f.batch.size || clock > f.batch.due + GIVE_UP)) complete = true;
        }
        long allowed = complete ? Math.max(budget, BATCH_MS * 1_000_000L) : budget;
        boolean crowded = FLIGHTS.size() >= MOST_WAITING;
        int n = FLIGHTS.size();
        for (int i = 0; i < n && System.nanoTime() - start < allowed; i++) {
            Flight f = FLIGHTS.poll();
            boolean ready = f.counted && clock >= f.due
                    && (f.batch.ready >= f.batch.size || clock > f.batch.due + GIVE_UP || crowded);
            if (!ready) {
                FLIGHTS.add(f);
                continue;
            }
            BUSY.remove(f.key);
            WreckChunk edit = f.plan.getNow(null);
            if (edit == null) continue;
            // the chunk went away while it was worked on: start again on it as it is now
            if (f.level.getChunkSource().getChunkNow(ChunkPos.getX(f.key.chunk()), ChunkPos.getZ(f.key.chunk())) != f.chunk) {
                f.batch.ready -= f.tasks.size();
                for (Task t : f.tasks) QUEUE.add(t.at(clock));
                continue;
            }
            land(f.level, f.chunk, edit);
        }
    }

    /**
     * Sets going the rewrites due soon (up to {@link #AHEAD} ticks early, so they are ready when due), each worked out
     * on a copy of its chunk off the main thread.
     */
    private static void launch(MinecraftServer server, long budget, long start) {
        boolean load = ServerConfig.DESTRUCTION_LOADS_CHUNKS.getOrDefault();
        int most = Math.max(8, ((java.util.concurrent.ThreadPoolExecutor) pool()).getMaximumPoolSize() * 4);
        int working = 0;
        for (Flight f : FLIGHTS) if (!f.plan.isDone()) working++;
        List<Task> later = new ArrayList<>();
        while (!QUEUE.isEmpty() && QUEUE.peek().due() - clock <= AHEAD && working < most && FLIGHTS.size() < MOST_WAITING
                && System.nanoTime() - start < budget) {
            Task t = QUEUE.poll();
            ServerLevel level = server.getLevel(t.dimension());
            if (level == null) {
                drop(t);
                continue;
            }
            Key key = new Key(t.dimension(), ChunkPos.asLong(t.x(), t.z()));
            Flight busy = BUSY.get(key);
            if (busy != null) {
                if (busy.batch == t.batch()) {
                    // the same batch: carry on from where the chunk's last job got to, before it lands
                    if (busy.counted) {
                        busy.counted = false;
                        busy.batch.ready -= busy.tasks.size();
                    }
                    busy.tasks.add(t);
                    busy.due = Math.max(busy.due, t.due());
                    busy.plan = busy.plan.thenApplyAsync(edit -> plan(t, edit, busy.chunk), pool());
                    working++;
                } else {
                    later.add(t);
                }
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(t.x(), t.z());
            if (chunk == null) {
                // (a task waiting for its chunk is looked at again every tick it is in reach of AHEAD: its wait is
                // measured in ticks, not in tries)
                if (!load || (t.since() >= 0 && clock - t.since() >= MAX_WAIT)) {
                    drop(t);
                    continue;
                }
                ChunkPos pos = new ChunkPos(t.x(), t.z());
                if (t.waited() % 100 == 0) level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
                later.add(t.retry(Math.max(t.due(), clock + 10), clock));
                continue;
            }
            WreckChunk edit = new WreckChunk(level, chunk, t.seed());
            Flight f = new Flight(key, level, chunk, t);
            f.plan = CompletableFuture.supplyAsync(() -> plan(t, edit, chunk), pool());
            BUSY.put(key, f);
            FLIGHTS.add(f);
            working++;
        }
        QUEUE.addAll(later);
    }

    /** Runs a job's shaper on the chunk's copy and gets it ready to land (off the main thread); null if it failed. */
    @org.jetbrains.annotations.Nullable
    private static WreckChunk plan(Task t, @org.jetbrains.annotations.Nullable WreckChunk edit, LevelChunk chunk) {
        if (edit == null) return null;
        try {
            t.shaper().shape(edit);
            edit.finish();
            return edit;
        } catch (RuntimeException e) {
            FantasyWeapons.LOGGER.error("World destruction failed in chunk {}", chunk.getPos(), e);
            return null;
        }
    }

    /** Runs a shaper on a loaded chunk right now, on this thread (GameTests, commands). */
    public static int rewrite(ServerLevel level, LevelChunk chunk, long seed, ChunkShaper shaper) {
        WreckChunk edit = new WreckChunk(level, chunk, seed);
        try {
            shaper.shape(edit);
        } catch (RuntimeException e) {
            FantasyWeapons.LOGGER.error("World destruction failed in chunk {}", chunk.getPos(), e);
        }
        edit.finish();
        land(level, chunk, edit);
        return edit.changed();
    }

    /** Puts a worked-out rewrite into its chunk, sends it to the players who see it and relights the column. */
    private static void land(ServerLevel level, LevelChunk chunk, WreckChunk edit) {
        if (edit.changed() == 0) {
            untouched++;
            return;
        }
        rewritten++;
        edit.apply(chunk);
        send(level, chunk, edit);
        chunk.initializeLightSources();
        chunk.setUnsaved(true);
        relight(level, chunk);
    }

    /** Sends the rewritten sections whole to the players who see the chunk (see {@link WreckPayload}). */
    private static void send(ServerLevel level, LevelChunk chunk, WreckChunk edit) {
        var players = level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false);
        if (players.isEmpty()) return;
        int[] ys = edit.sentSectionYs();
        byte[][] data = edit.sentSections();
        int from = 0;
        while (from < ys.length) {
            int to = from, bytes = 0;
            while (to < ys.length && (to == from || bytes + data[to].length <= WreckPayload.MAX_BYTES)) bytes += data[to++].length;
            WreckPayload p = new WreckPayload(chunk.getPos().x, chunk.getPos().z, java.util.Arrays.copyOfRange(ys, from, to),
                    java.util.Arrays.copyOfRange(data, from, to));
            for (ServerPlayer pl : players) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(pl, p);
            from = to;
        }
    }

    /**
     * Throws away the column's light and lights it again from scratch, the way a freshly loaded chunk is lit, then sends
     * the new light to everyone who sees it. The light engine runs on its own thread, so this finishes a few ticks later.
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
        FRESH.clear();
        FLIGHTS.clear();
        BUSY.clear();
    }
}
