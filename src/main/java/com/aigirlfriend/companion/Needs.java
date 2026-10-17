package com.aigirlfriend.companion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** Survival needs on 0-100 scales. Hunger 0 = full, energy 100 = rested, attention 100 = cherished. */
public final class Needs {
    public float hunger = 15f;
    public float energy = 90f;
    public float attention = 50f;
    /** Clock values from the last update, so elapsed time survives reloads. */
    public long lastDayTime = Long.MIN_VALUE;
    public long lastGameTime = Long.MIN_VALUE;

    public void addHunger(float v) { hunger = Mth.clamp(hunger + v, 0f, 100f); }
    public void addEnergy(float v) { energy = Mth.clamp(energy + v, 0f, 100f); }
    public void addAttention(float v) { attention = Mth.clamp(attention + v, 0f, 100f); }

    public boolean isHungry() { return hunger >= 55f; }
    public boolean isStarving() { return hunger >= 85f; }
    public boolean isTired() { return energy <= 35f; }
    public boolean isExhausted() { return energy <= 12f; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("hunger", hunger);
        t.putFloat("energy", energy);
        t.putFloat("attention", attention);
        t.putLong("lastDayTime", lastDayTime);
        t.putLong("lastGameTime", lastGameTime);
        return t;
    }

    public void load(CompoundTag t) {
        hunger = t.getFloat("hunger");
        energy = t.getFloat("energy");
        attention = t.getFloat("attention");
        lastDayTime = t.contains("lastDayTime") ? t.getLong("lastDayTime") : Long.MIN_VALUE;
        lastGameTime = t.contains("lastGameTime") ? t.getLong("lastGameTime") : Long.MIN_VALUE;
    }
}
