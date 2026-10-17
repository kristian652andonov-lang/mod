package com.aigirlfriend.companion;

import com.aigirlfriend.config.GFConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * Menstrual-cycle simulation based on persistent world-clock timestamps.
 * <p>
 * The phase is a pure function of (clock - anchor) mod length, so it is identical after reloads,
 * restarts and chunk unloads. With the DAY_TIME clock, sleeping through the night advances the cycle exactly
 * like the visible day counter; with GAME_TIME only simulated ticks count. Switching clock modes re-anchors
 * so the current position in the cycle is preserved.
 */
public final class CycleState {
    public static final long DAY = 24000L;

    public long anchor;
    public boolean initialized;
    public int clockMode = -1;
    public long lastPosition;

    public void ensure(long now, int mode, RandomSource random) {
        long length = lengthTicks();
        if (!initialized) {
            anchor = now - (long) (random.nextDouble() * length);
            initialized = true;
            clockMode = mode;
        } else if (clockMode != mode) {
            anchor = now - lastPosition;
            clockMode = mode;
        }
        lastPosition = position(now);
    }

    public long position(long now) {
        return Math.floorMod(now - anchor, lengthTicks());
    }

    /** Day index inside the cycle, starting at 1. */
    public int dayOfCycle(long now) {
        return (int) (position(now) / DAY) + 1;
    }

    public CyclePhase phase(long now) {
        if (!GFConfig.CYCLE_ENABLED.get() || !initialized) return CyclePhase.DISABLED;
        return phaseAt(position(now));
    }

    public static long lengthTicks() {
        return Math.max(DAY / 4, (long) (GFConfig.CYCLE_LENGTH_DAYS.get() * DAY));
    }

    /** Phase lengths in ticks, scaled down proportionally if the configured phases do not fit into the cycle. */
    public static long[] phaseLengths() {
        long len = lengthTicks();
        double m = GFConfig.CYCLE_MENSTRUAL_DAYS.get() * DAY;
        double f = GFConfig.CYCLE_FOLLICULAR_DAYS.get() * DAY;
        double o = GFConfig.CYCLE_OVULATION_DAYS.get() * DAY;
        double sum = m + f + o;
        if (sum > len) {
            double scale = len / sum;
            m *= scale;
            f *= scale;
            o *= scale;
        }
        long lm = (long) m, lf = (long) f, lo = (long) o;
        return new long[]{lm, lf, lo, Math.max(0, len - lm - lf - lo)};
    }

    public static CyclePhase phaseAt(long pos) {
        long[] l = phaseLengths();
        if (pos < l[0]) return CyclePhase.MENSTRUAL;
        pos -= l[0];
        if (pos < l[1]) return CyclePhase.FOLLICULAR;
        pos -= l[1];
        if (pos < l[2]) return CyclePhase.OVULATION;
        return CyclePhase.LUTEAL;
    }

    /** Debug: jump to the start of a phase. */
    public void setPhase(long now, CyclePhase phase) {
        long[] l = phaseLengths();
        long offset = switch (phase) {
            case MENSTRUAL, DISABLED -> 0;
            case FOLLICULAR -> l[0];
            case OVULATION -> l[0] + l[1];
            case LUTEAL -> l[0] + l[1] + l[2];
        };
        anchor = now - offset;
        initialized = true;
        lastPosition = position(now);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("anchor", anchor);
        t.putBoolean("init", initialized);
        t.putInt("mode", clockMode);
        t.putLong("lastPos", lastPosition);
        return t;
    }

    public void load(CompoundTag t) {
        anchor = t.getLong("anchor");
        initialized = t.getBoolean("init");
        clockMode = t.contains("mode") ? t.getInt("mode") : -1;
        lastPosition = t.getLong("lastPos");
    }
}
