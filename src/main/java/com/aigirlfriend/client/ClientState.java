package com.aigirlfriend.client;

import com.aigirlfriend.network.StatusSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Client-side caches: latest status per companion and active speech bubbles. Purely presentational. */
public final class ClientState {
    public record Speech(String text, int emotion, boolean typing, long startTick, long endTick) {}

    public record LogLine(boolean fromPlayer, String text) {}

    private static final Map<Integer, StatusSnapshot> STATUS = new HashMap<>();
    private static final Map<Integer, Long> STATUS_TIME = new HashMap<>();
    private static final Map<Integer, Speech> SPEECH = new HashMap<>();
    private static final Map<Integer, Deque<LogLine>> LOG = new HashMap<>();
    public static long clientTicks;

    private ClientState() {}

    public static void putStatus(int entityId, StatusSnapshot s) {
        STATUS.put(entityId, s);
        STATUS_TIME.put(entityId, clientTicks);
    }

    @Nullable
    public static StatusSnapshot status(int entityId) {
        return STATUS.get(entityId);
    }

    public static boolean statusFresh(int entityId, int maxAgeTicks) {
        Long t = STATUS_TIME.get(entityId);
        return t != null && clientTicks - t <= maxAgeTicks;
    }

    public static void putSpeech(int entityId, String text, int emotion, boolean typing, int duration) {
        Speech prev = SPEECH.get(entityId);
        if (typing && prev != null && !prev.typing() && prev.endTick() > clientTicks) return;
        SPEECH.put(entityId, new Speech(text, emotion, typing, clientTicks, clientTicks + duration));
        if (!typing) log(entityId, false, text);
    }

    @Nullable
    public static Speech speech(int entityId) {
        Speech s = SPEECH.get(entityId);
        if (s != null && clientTicks > s.endTick()) {
            SPEECH.remove(entityId);
            return null;
        }
        return s;
    }

    public static void log(int entityId, boolean fromPlayer, String text) {
        Deque<LogLine> d = LOG.computeIfAbsent(entityId, k -> new ArrayDeque<>());
        d.addLast(new LogLine(fromPlayer, text));
        while (d.size() > 40) d.removeFirst();
    }

    public static Deque<LogLine> log(int entityId) {
        return LOG.computeIfAbsent(entityId, k -> new ArrayDeque<>());
    }

    public static void clear() {
        STATUS.clear();
        STATUS_TIME.clear();
        SPEECH.clear();
        LOG.clear();
    }
}
