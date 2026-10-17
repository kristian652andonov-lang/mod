package com.aigirlfriend.companion;

import com.aigirlfriend.config.GFConfig;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Bounded long-term memory. Similar events on the same day are merged rather than duplicated,
 * and the lowest scoring memories are forgotten when the configured limit is reached.
 * She only ever references entries that exist here, so she never "remembers" things that did not happen.
 */
public final class MemoryBank {
    private final List<MemoryEntry> entries = new ArrayList<>();

    public MemoryEntry remember(MemoryType type, String summary, String detail, long day, long gameTime) {
        return remember(type, summary, detail, day, gameTime, type.baseImportance);
    }

    public MemoryEntry remember(MemoryType type, String summary, String detail, long day, long gameTime, float importance) {
        for (MemoryEntry e : entries) {
            if (e.type == type && e.detail.equals(detail == null ? "" : detail) && Math.abs(e.day - day) <= 1 && !e.resolved) {
                e.count++;
                e.gameTime = gameTime;
                e.day = day;
                e.summary = summary;
                e.importance = Math.min(1.5f, Math.max(e.importance, importance) + 0.03f);
                return e;
            }
        }
        MemoryEntry e = new MemoryEntry(type, summary, detail, day, gameTime, importance);
        entries.add(e);
        prune(day);
        return e;
    }

    private void prune(long day) {
        int max = GFConfig.MAX_MEMORIES.get();
        if (entries.size() <= max) return;
        entries.sort(Comparator.comparingDouble((MemoryEntry m) -> m.score(day)).reversed());
        while (entries.size() > max) entries.remove(entries.size() - 1);
        entries.sort(Comparator.comparingLong(m -> m.gameTime));
    }

    public List<MemoryEntry> all() {
        return entries;
    }

    public boolean has(MemoryType type) {
        return entries.stream().anyMatch(e -> e.type == type);
    }

    @Nullable
    public MemoryEntry latest(MemoryType type) {
        MemoryEntry best = null;
        for (MemoryEntry e : entries) if (e.type == type && (best == null || e.gameTime > best.gameTime)) best = e;
        return best;
    }

    @Nullable
    public MemoryEntry find(Predicate<MemoryEntry> p) {
        for (int i = entries.size() - 1; i >= 0; i--) if (p.test(entries.get(i))) return entries.get(i);
        return null;
    }

    /** Most relevant memories for context building, newest and most important first. */
    public List<MemoryEntry> relevant(int n, long day) {
        List<MemoryEntry> copy = new ArrayList<>(entries);
        copy.sort(Comparator.comparingDouble((MemoryEntry m) -> m.score(day)).reversed());
        return copy.subList(0, Math.min(n, copy.size()));
    }

    /** A memory worth bringing up naturally: older than today, not yet talked about much. */
    @Nullable
    public MemoryEntry pickReminiscence(long day, RandomSource random) {
        List<MemoryEntry> candidates = new ArrayList<>();
        for (MemoryEntry e : entries) {
            if (e.type == MemoryType.CONVERSATION || e.type == MemoryType.HURT) continue;
            if (e.mentioned && random.nextFloat() < 0.75f) continue;
            if (day - e.day >= 1 || e.type == MemoryType.PROTECTED || e.type == MemoryType.MILESTONE) candidates.add(e);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingDouble((MemoryEntry m) -> m.score(day)).reversed());
        int pool = Math.min(4, candidates.size());
        return candidates.get(random.nextInt(pool));
    }

    public List<MemoryEntry> openPromises() {
        List<MemoryEntry> list = new ArrayList<>();
        for (MemoryEntry e : entries) if (e.type == MemoryType.PROMISE && !e.resolved) list.add(e);
        return list;
    }

    public void clear() {
        entries.clear();
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (MemoryEntry e : entries) list.add(e.save());
        return list;
    }

    public void load(ListTag list) {
        entries.clear();
        for (int i = 0; i < list.size(); i++) entries.add(MemoryEntry.load(list.getCompound(i)));
    }
}
