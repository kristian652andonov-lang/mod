package com.aigirlfriend.companion;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;

public final class DailyLog {
    public long day = -1;
    private final EnumMap<LogKey, Float> values = new EnumMap<>(LogKey.class);

    public void add(LogKey key, float v) {
        values.merge(key, v, Float::sum);
    }

    public void max(LogKey key, float v) {
        values.merge(key, v, Math::max);
    }

    public float get(LogKey key) {
        return values.getOrDefault(key, 0f);
    }

    public float averageAttention() {
        float n = get(LogKey.ATTENTION_SAMPLES);
        return n <= 0 ? 50f : get(LogKey.ATTENTION_SUM) / n;
    }

    /** Number of distinct affectionate things that happened (variety matters more than repetition). */
    public int affectionVariety() {
        int n = 0;
        if (get(LogKey.TALKS) > 0) n++;
        if (get(LogKey.HUGS) > 0) n++;
        if (get(LogKey.KISSES) > 0) n++;
        if (get(LogKey.CUDDLES) > 0) n++;
        if (get(LogKey.HAND_HOLD_SECONDS) > 20) n++;
        if (get(LogKey.GIFTS_LIKED) > 0) n++;
        if (get(LogKey.FEEDINGS) > 0) n++;
        if (get(LogKey.SLEPT_TOGETHER) > 0) n++;
        return n;
    }

    public void reset(long newDay) {
        values.clear();
        day = newDay;
    }

    public void copyFrom(DailyLog other) {
        values.clear();
        values.putAll(other.values);
        day = other.day;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("day", day);
        values.forEach((k, v) -> t.putFloat(k.name(), v));
        return t;
    }

    public void load(CompoundTag t) {
        values.clear();
        day = t.getLong("day");
        for (LogKey k : LogKey.values()) if (t.contains(k.name())) values.put(k, t.getFloat(k.name()));
    }
}
