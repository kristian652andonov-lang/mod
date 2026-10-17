package com.fantasyweapons.client.fx;

import java.util.ArrayList;
import java.util.List;

/** Runs effect callbacks a number of client ticks later (multi-beat effects: implode → explode). */
public final class FxScheduler {
    private record Task(int[] remaining, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();

    private FxScheduler() {
    }

    public static void after(int ticks, Runnable action) {
        TASKS.add(new Task(new int[]{Math.max(0, ticks)}, action));
    }

    public static void tick() {
        if (TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (--t.remaining()[0] <= 0) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) t.action().run();
    }

    public static void clear() {
        TASKS.clear();
    }
}
