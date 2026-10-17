package com.aigirlfriend.fear;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Runtime fear bookkeeping (not persisted: danger is re-evaluated after reloads). */
public final class FearState {
    public float level;
    public float target;
    @Nullable public LivingEntity mainThreat;
    @Nullable public Vec3 threatCenter;
    public int threatCount;
    public boolean inDanger;
    public long dangerStartTick;
    public float peak;
    public boolean protectedDuringEncounter;
    public long lastAlarmTick = -100000L;

    public boolean uneasy() { return level >= 0.15f; }
    public boolean afraid() { return level >= 0.4f; }
    public boolean panic() { return level >= 0.72f; }
}
