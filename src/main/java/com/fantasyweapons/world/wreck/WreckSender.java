package com.fantasyweapons.world.wreck;

import com.fantasyweapons.network.WreckPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends the sections the destruction rewrote to the players who see them (see {@link WreckPayload}).
 *
 * <p>A player in the same game as the server (the one playing a single-player world) gets them at once, whole. A
 * player over the network gets them as fast as their connection takes them: a great power rewrites thousands of
 * chunks, tens of megabytes of blocks, and handed to the connection all in the same instant that would pile up in the
 * server's memory and hold back everything else the player is sent (their keep-alive with it) until they drop. So
 * the sections wait here, each chunk once however often it is rewritten, and go out a share every tick while the
 * connection keeps up, as they are by then (the newest blocks, never an older picture).
 */
public final class WreckSender {
    /** The most bytes of sections sent to one player in a tick over the network (about 20 MB a second). */
    private static final int BYTES_PER_TICK = 1 << 20;

    /** For each player over the network: the chunks waiting (in the order they were rewritten), with their sections. */
    private static final Map<UUID, Waiting> WAITING = new HashMap<>();

    private WreckSender() {
    }

    private static final class Waiting {
        final ResourceKey<Level> dimension;
        final LinkedHashMap<Long, BitSet> chunks = new LinkedHashMap<>();
        /** Chunks and bytes sent since the queue was last empty, and the ticks it took. */
        int sentChunks, ticks;
        long sentBytes;

        Waiting(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    /** The rewritten sections ({@code ys}, packed in {@code data}) of {@code chunk}, to everyone who sees it. */
    static void send(ServerLevel level, LevelChunk chunk, int[] ys, byte[][] data) {
        var players = level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false);
        if (players.isEmpty()) return;
        List<ServerPlayer> remote = new ArrayList<>();
        for (ServerPlayer p : players) {
            if (p.connection.getConnection().isMemoryConnection()) sendNow(p, chunk.getPos(), ys, data);
            else remote.add(p);
        }
        for (ServerPlayer p : remote) {
            Waiting w = WAITING.get(p.getUUID());
            if (w == null || w.dimension != level.dimension()) {
                w = new Waiting(level.dimension());
                WAITING.put(p.getUUID(), w);
            }
            BitSet sections = w.chunks.computeIfAbsent(chunk.getPos().toLong(), k -> new BitSet());
            for (int y : ys) sections.set(chunk.getSectionIndexFromSectionY(y));
        }
    }

    /** Every tick: each player over the network is sent a share of what waits for them, while their connection keeps up. */
    public static void tick(MinecraftServer server) {
        if (WAITING.isEmpty()) return;
        Iterator<Map.Entry<UUID, Waiting>> it = WAITING.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Waiting w = e.getValue();
            if (p == null || p.level().dimension() != w.dimension || w.chunks.isEmpty()) {
                if (p != null && w.sentChunks >= 256) {
                    com.fantasyweapons.FantasyWeapons.LOGGER.info("Sent the destruction of {} chunks ({} MB) to {} over {} ticks", w.sentChunks,
                            String.format("%.1f", w.sentBytes / 1048576.0), p.getGameProfile().getName(), w.ticks);
                }
                it.remove();
                continue;
            }
            w.ticks++;
            var channel = p.connection.getConnection().channel();
            // still sending what it was given before: the rest waits for the next tick
            if (channel != null && !channel.isWritable()) continue;
            ServerLevel level = p.serverLevel();
            int budget = BYTES_PER_TICK;
            var chunks = w.chunks.entrySet().iterator();
            while (budget > 0 && chunks.hasNext()) {
                var c = chunks.next();
                chunks.remove();
                ChunkPos pos = new ChunkPos(c.getKey());
                // a chunk the player no longer sees is sent whole when they see it again
                if (!p.getChunkTrackingView().contains(pos)) continue;
                LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk == null) continue;
                BitSet set = c.getValue();
                int[] ys = new int[set.cardinality()];
                byte[][] data = new byte[ys.length][];
                int n = 0;
                for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i + 1)) {
                    if (i >= chunk.getSectionsCount()) break;
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    chunk.getSection(i).getStates().write(buf);
                    data[n] = new byte[buf.readableBytes()];
                    buf.readBytes(data[n]);
                    ys[n++] = chunk.getSectionYFromSectionIndex(i);
                    budget -= data[n - 1].length;
                }
                if (n < ys.length) {
                    ys = java.util.Arrays.copyOf(ys, n);
                    data = java.util.Arrays.copyOf(data, n);
                }
                sendNow(p, pos, ys, data);
                w.sentChunks++;
                for (int k = 0; k < n; k++) w.sentBytes += data[k].length;
            }
        }
    }

    private static void sendNow(ServerPlayer p, ChunkPos pos, int[] ys, byte[][] data) {
        for (WreckPayload packet : packets(pos, ys, data)) PacketDistributor.sendToPlayer(p, packet);
    }

    /** The sections in packets of at most {@link WreckPayload#MAX_BYTES} of blocks each. */
    public static List<WreckPayload> packets(ChunkPos pos, int[] ys, byte[][] data) {
        List<WreckPayload> out = new ArrayList<>();
        int from = 0;
        while (from < ys.length) {
            int to = from, bytes = 0;
            while (to < ys.length && (to == from || bytes + data[to].length <= WreckPayload.MAX_BYTES)) bytes += data[to++].length;
            out.add(new WreckPayload(pos.x, pos.z, java.util.Arrays.copyOfRange(ys, from, to), java.util.Arrays.copyOfRange(data, from, to)));
            from = to;
        }
        return out;
    }

    /** How many chunks wait to be sent, over every player. */
    public static int waiting() {
        int n = 0;
        for (Waiting w : WAITING.values()) n += w.chunks.size();
        return n;
    }

    public static void clear() {
        WAITING.clear();
    }
}
