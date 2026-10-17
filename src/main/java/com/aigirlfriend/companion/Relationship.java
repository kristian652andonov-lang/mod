package com.aigirlfriend.companion;

import com.aigirlfriend.config.GFConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Affection (how much she loves you) and trust (how safe she feels with you).
 * Gains for the same kind of interaction diminish within a day, so progression needs variety and time.
 */
public final class Relationship {
    public static final float MAX_AFFECTION = 2500f;

    public float affection = 0f;
    public float trust = 10f;
    public int daysTogether = 0;
    public long lastTogetherDay = -1;
    public long firstMetDay = -1;
    /** -1 = computed normally, otherwise a debug override. */
    public int stageOverride = -1;
    private RelationshipStage cachedStage = RelationshipStage.ACQUAINTANCE;
    private final Map<String, Integer> todayCounts = new HashMap<>();
    private long countsDay = -1;
    public final Set<String> milestones = new LinkedHashSet<>();
    /** Set when she accepted a wedding ring proposal. Independent of the stage. */
    public boolean married = false;
    public long marriedDay = -1;

    /** Applies a positive change with same-day diminishing returns. Returns the amount actually gained. */
    public float gain(String key, float amount, float trustAmount, long day) {
        if (countsDay != day) {
            todayCounts.clear();
            countsDay = day;
        }
        int n = todayCounts.getOrDefault(key, 0);
        todayCounts.put(key, n + 1);
        float factor = (float) (1.0 / (1.0 + n * GFConfig.REPEAT_DIMINISHING.get()));
        float mult = GFConfig.PROGRESSION_MULTIPLIER.get().floatValue() * factor;
        float a = amount * mult;
        affection = Mth.clamp(affection + a, 0f, MAX_AFFECTION);
        trust = Mth.clamp(trust + trustAmount * mult, 0f, 100f);
        markTogether(day);
        return a;
    }

    /** Negative events. Never drops below a floor that depends on the long-term relationship, so recovery stays possible. */
    public void lose(float amount, float trustAmount) {
        float mult = GFConfig.NEGATIVE_MULTIPLIER.get().floatValue();
        float floor = Math.max(0f, stageFloor() - 40f);
        affection = Math.max(floor, affection - amount * mult);
        trust = Mth.clamp(trust - trustAmount * mult, 0f, 100f);
    }

    private float stageFloor() {
        return cachedStage.affection;
    }

    public void markTogether(long day) {
        if (lastTogetherDay != day) {
            lastTogetherDay = day;
            daysTogether++;
        }
        if (firstMetDay < 0) firstMetDay = day;
    }

    /** Recomputes the stage. Returns the new stage if it changed upwards (a milestone), else null. */
    public RelationshipStage refreshStage() {
        RelationshipStage old = cachedStage;
        RelationshipStage s;
        if (stageOverride >= 0) {
            s = RelationshipStage.byId(stageOverride);
        } else {
            s = RelationshipStage.ACQUAINTANCE;
            for (RelationshipStage st : RelationshipStage.values()) {
                if (affection >= st.affection && trust >= st.trust && daysTogether >= st.days) s = st;
            }
            // Stages only drop if affection falls well below the threshold (hysteresis).
            if (s.ordinal() < old.ordinal() && affection > old.affection - 60f && trust > old.trust - 15f) s = old;
        }
        cachedStage = s;
        return s.ordinal() > old.ordinal() ? s : null;
    }

    public RelationshipStage stage() {
        return cachedStage;
    }

    /** Progress 0..1 towards the next stage, for UI. */
    public float progressToNext() {
        RelationshipStage s = cachedStage;
        RelationshipStage n = s.next();
        if (n == s) return 1f;
        return Mth.clamp((affection - s.affection) / Math.max(1f, n.affection - s.affection), 0f, 1f);
    }

    /** Debug helper: puts the relationship exactly at the start of the given stage. */
    public void forceStage(RelationshipStage s) {
        affection = s.affection + 1f;
        trust = Math.max(trust, s.trust + 1f);
        daysTogether = Math.max(daysTogether, s.days);
        stageOverride = -1;
        cachedStage = s;
    }

    /** Fills affection, trust and days well past the top stage's thresholds, so the stage survives hysteresis and small losses. */
    public void maxOut() {
        RelationshipStage[] all = RelationshipStage.values();
        RelationshipStage top = all[all.length - 1];
        affection = Math.max(affection, Math.min(MAX_AFFECTION, top.affection + 150f));
        trust = 100f;
        daysTogether = Math.max(daysTogether, top.days);
        stageOverride = -1;
    }

    public void setMarried(boolean value, long day) {
        married = value;
        marriedDay = value ? day : -1;
        if (value) milestones.add("married");
        else milestones.remove("married");
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("affection", affection);
        t.putFloat("trust", trust);
        t.putInt("daysTogether", daysTogether);
        t.putLong("lastTogetherDay", lastTogetherDay);
        t.putLong("firstMetDay", firstMetDay);
        t.putInt("stageOverride", stageOverride);
        t.putInt("stage", cachedStage.ordinal());
        CompoundTag counts = new CompoundTag();
        todayCounts.forEach(counts::putInt);
        t.put("todayCounts", counts);
        t.putLong("countsDay", countsDay);
        ListTag ms = new ListTag();
        milestones.forEach(m -> ms.add(StringTag.valueOf(m)));
        t.put("milestones", ms);
        t.putBoolean("married", married);
        t.putLong("marriedDay", marriedDay);
        return t;
    }

    public void load(CompoundTag t) {
        affection = t.getFloat("affection");
        trust = t.getFloat("trust");
        daysTogether = t.getInt("daysTogether");
        lastTogetherDay = t.getLong("lastTogetherDay");
        firstMetDay = t.getLong("firstMetDay");
        stageOverride = t.contains("stageOverride") ? t.getInt("stageOverride") : -1;
        cachedStage = RelationshipStage.byId(t.getInt("stage"));
        todayCounts.clear();
        CompoundTag counts = t.getCompound("todayCounts");
        for (String k : counts.getAllKeys()) todayCounts.put(k, counts.getInt(k));
        countsDay = t.getLong("countsDay");
        milestones.clear();
        ListTag ms = t.getList("milestones", Tag.TAG_STRING);
        for (int i = 0; i < ms.size(); i++) milestones.add(ms.getString(i));
        married = t.getBoolean("married");
        marriedDay = t.contains("marriedDay") ? t.getLong("marriedDay") : -1;
    }
}
