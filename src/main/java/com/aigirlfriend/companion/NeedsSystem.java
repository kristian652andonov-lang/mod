package com.aigirlfriend.companion;

import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.util.WorldClock;
import net.minecraft.world.level.Level;

/**
 * Advances hunger, energy and attention based on elapsed world time (never real time).
 * Elapsed time is measured from persistent timestamps, so needs keep progressing consistently across
 * reloads; huge jumps (time commands) are capped to one day and backwards jumps count as zero.
 */
public final class NeedsSystem {
    private NeedsSystem() {}

    public static void update(GirlfriendEntity gf, boolean sleeping, float activityLevel) {
        Level level = gf.level();
        Needs n = gf.data().needs;
        long dayTime = level.getDayTime();
        long gameTime = level.getGameTime();
        long elapsed;
        if (GFConfig.NEEDS_USE_DAYTIME.get()) {
            elapsed = n.lastDayTime == Long.MIN_VALUE ? 0 : dayTime - n.lastDayTime;
            // the day clock can stand still (doDaylightCycle=false): fall back to simulated ticks then
            long gameElapsed = n.lastGameTime == Long.MIN_VALUE ? 0 : gameTime - n.lastGameTime;
            if (elapsed <= 0 && gameElapsed > 0) elapsed = gameElapsed;
        } else {
            elapsed = n.lastGameTime == Long.MIN_VALUE ? 0 : gameTime - n.lastGameTime;
        }
        n.lastDayTime = dayTime;
        n.lastGameTime = gameTime;
        if (elapsed <= 0) return;
        elapsed = Math.min(elapsed, WorldClock.DAY);
        float days = elapsed / (float) WorldClock.DAY;

        CompanionData d = gf.data();
        boolean menstrual = d.cycle.phase(cycleClock(level)) == CyclePhase.MENSTRUAL;

        float hungerRate = GFConfig.HUNGER_PER_DAY.get().floatValue() * (0.8f + activityLevel * 0.4f);
        if (menstrual) hungerRate *= GFConfig.CYCLE_HUNGER_MULT.get().floatValue();
        if (sleeping) hungerRate *= 0.5f;
        n.addHunger(hungerRate * days);

        if (sleeping) {
            n.addEnergy(GFConfig.SLEEP_RECOVERY_PER_DAY.get().floatValue() * days);
        } else {
            float fatigue = GFConfig.FATIGUE_PER_DAY.get().floatValue() * (0.55f + activityLevel * 0.9f);
            if (WorldClock.isNight(level)) fatigue *= 1.3f;
            if (menstrual) fatigue *= GFConfig.CYCLE_FATIGUE_MULT.get().floatValue();
            if (n.isStarving()) fatigue *= 1.3f;
            if (gf.isSeated()) fatigue *= 0.35f;
            n.addEnergy(-fatigue * days);
        }

        float decay = GFConfig.ATTENTION_DECAY_PER_DAY.get().floatValue() * (1.3f - d.personality.independence * 0.6f);
        if (sleeping) decay *= 0.3f;
        n.addAttention(-decay * days);
    }

    public static long cycleClock(Level level) {
        return GFConfig.CYCLE_CLOCK.get() == GFConfig.CycleClockMode.DAY_TIME ? level.getDayTime() : level.getGameTime();
    }
}
