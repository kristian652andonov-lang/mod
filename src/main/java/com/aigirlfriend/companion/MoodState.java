package com.aigirlfriend.companion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Primary mood (decided once per Minecraft day, with understandable causes), a continuous wellbeing value
 * that drifts with events, and a short-lived secondary emotion.
 */
public final class MoodState {
    public Mood primary = Mood.RELAXED;
    public final List<String> causes = new ArrayList<>();
    public float wellbeing = 20f;
    public Emotion secondary = Emotion.NONE;
    public float secondaryIntensity = 0f;
    public long secondaryUntil = 0L;
    public long lastEvaluatedDay = Long.MIN_VALUE;
    public long lastReevaluationTick = 0L;
    public final Deque<HistoryEntry> history = new ArrayDeque<>();

    public record HistoryEntry(long day, Mood mood, String cause) {}

    public void setPrimary(Mood mood, List<String> newCauses, long day) {
        primary = mood;
        causes.clear();
        causes.addAll(newCauses);
        history.addFirst(new HistoryEntry(day, mood, newCauses.isEmpty() ? "" : newCauses.get(0)));
        while (history.size() > 14) history.removeLast();
    }

    public void feel(Emotion emotion, float intensity, long now, int durationTicks) {
        if (emotion == Emotion.NONE) return;
        if (secondary == emotion || now >= secondaryUntil || intensity >= secondaryIntensity * 0.8f) {
            secondary = emotion;
            secondaryIntensity = Mth.clamp(Math.max(intensity, secondary == emotion ? secondaryIntensity : 0f), 0f, 1f);
            secondaryUntil = now + durationTicks;
        }
    }

    public Emotion activeEmotion(long now) {
        if (now >= secondaryUntil) {
            secondary = Emotion.NONE;
            secondaryIntensity = 0f;
        }
        return secondary;
    }

    public void shiftWellbeing(float delta) {
        wellbeing = Mth.clamp(wellbeing + delta, -100f, 100f);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("primary", primary.name());
        ListTag c = new ListTag();
        causes.forEach(s -> c.add(StringTag.valueOf(s)));
        t.put("causes", c);
        t.putFloat("wellbeing", wellbeing);
        t.putString("secondary", secondary.name());
        t.putFloat("secondaryIntensity", secondaryIntensity);
        t.putLong("secondaryUntil", secondaryUntil);
        t.putLong("lastEvalDay", lastEvaluatedDay);
        t.putLong("lastReeval", lastReevaluationTick);
        ListTag h = new ListTag();
        for (HistoryEntry e : history) {
            CompoundTag et = new CompoundTag();
            et.putLong("day", e.day());
            et.putString("mood", e.mood().name());
            et.putString("cause", e.cause());
            h.add(et);
        }
        t.put("history", h);
        return t;
    }

    public void load(CompoundTag t) {
        try { primary = Mood.valueOf(t.getString("primary")); } catch (Exception e) { primary = Mood.RELAXED; }
        causes.clear();
        ListTag c = t.getList("causes", Tag.TAG_STRING);
        for (int i = 0; i < c.size(); i++) causes.add(c.getString(i));
        wellbeing = t.getFloat("wellbeing");
        secondary = Emotion.byName(t.getString("secondary"));
        secondaryIntensity = t.getFloat("secondaryIntensity");
        secondaryUntil = t.getLong("secondaryUntil");
        lastEvaluatedDay = t.contains("lastEvalDay") ? t.getLong("lastEvalDay") : Long.MIN_VALUE;
        lastReevaluationTick = t.getLong("lastReeval");
        history.clear();
        ListTag h = t.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < h.size(); i++) {
            CompoundTag et = h.getCompound(i);
            Mood m;
            try { m = Mood.valueOf(et.getString("mood")); } catch (Exception e) { m = Mood.RELAXED; }
            history.addLast(new HistoryEntry(et.getLong("day"), m, et.getString("cause")));
        }
    }
}
