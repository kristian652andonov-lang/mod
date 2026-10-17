package com.aigirlfriend.companion;

import net.minecraft.nbt.CompoundTag;

/**
 * One remembered event. {@code summary} is a short past-tense phrase written from her perspective
 * ("you gave me a poppy"), {@code detail} is a machine key (item id, place, request type) for matching.
 */
public final class MemoryEntry {
    public MemoryType type;
    public String summary;
    public String detail;
    public long day;
    public long gameTime;
    public float importance;
    public int count;
    public boolean resolved;
    public boolean mentioned;

    public MemoryEntry(MemoryType type, String summary, String detail, long day, long gameTime, float importance) {
        this.type = type;
        this.summary = summary;
        this.detail = detail == null ? "" : detail;
        this.day = day;
        this.gameTime = gameTime;
        this.importance = importance;
        this.count = 1;
    }

    /** Retention score: important and recent memories survive, repeated ones are reinforced. */
    public float score(long currentDay) {
        float age = Math.max(0, currentDay - day);
        float recency = 1f / (1f + age * 0.15f);
        float reinforcement = Math.min(1.5f, 1f + (count - 1) * 0.1f);
        return importance * reinforcement * (0.35f + 0.65f * recency) + (type == MemoryType.FIRST_MEETING || type == MemoryType.MILESTONE ? 1f : 0f);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", type.name());
        t.putString("summary", summary);
        t.putString("detail", detail);
        t.putLong("day", day);
        t.putLong("time", gameTime);
        t.putFloat("importance", importance);
        t.putInt("count", count);
        t.putBoolean("resolved", resolved);
        t.putBoolean("mentioned", mentioned);
        return t;
    }

    public static MemoryEntry load(CompoundTag t) {
        MemoryEntry e = new MemoryEntry(MemoryType.byName(t.getString("type")), t.getString("summary"), t.getString("detail"),
                t.getLong("day"), t.getLong("time"), t.getFloat("importance"));
        e.count = Math.max(1, t.getInt("count"));
        e.resolved = t.getBoolean("resolved");
        e.mentioned = t.getBoolean("mentioned");
        return e;
    }
}
