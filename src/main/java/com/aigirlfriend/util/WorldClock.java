package com.aigirlfriend.util;

import net.minecraft.world.level.Level;

/** Helpers for Minecraft time. All values come from the world, never the real clock. */
public final class WorldClock {
    public static final long DAY = 24000L;

    private WorldClock() {}

    public static long day(Level level) {
        return Math.floorDiv(level.getDayTime(), DAY);
    }

    /** 0..23999, 0 = sunrise (06:00). */
    public static long timeOfDay(Level level) {
        return Math.floorMod(level.getDayTime(), DAY);
    }

    public static boolean isNight(Level level) {
        long t = timeOfDay(level);
        return t >= 13000 && t < 23000;
    }

    /** Late evening: the time she starts thinking about bed. */
    public static boolean isBedtime(Level level) {
        long t = timeOfDay(level);
        return t >= 12500 && t < 23000;
    }

    public static boolean isMorning(Level level) {
        long t = timeOfDay(level);
        return t >= 23000 || t < 3000;
    }

    public static String partOfDay(Level level) {
        long t = timeOfDay(level);
        if (t < 1000 || t >= 23000) return "early morning";
        if (t < 5000) return "morning";
        if (t < 7000) return "midday";
        if (t < 11000) return "afternoon";
        if (t < 13000) return "evening";
        if (t < 18000) return "night";
        return "late night";
    }

    /** "hh:mm" in-game clock. */
    public static String clock(Level level) {
        long t = timeOfDay(level);
        long hours = (t / 1000 + 6) % 24;
        long minutes = (t % 1000) * 60 / 1000;
        return String.format("%02d:%02d", hours, minutes);
    }
}
