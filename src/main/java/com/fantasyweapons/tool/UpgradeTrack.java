package com.fantasyweapons.tool;

/**
 * What the Legendary Upgrading Table can raise on a legendary tool, each a level at a time: how far it digs round the
 * block it strikes (3x3 up to 13x13), its Fortune (III up to X) and its Efficiency (V up to X). Every step costs some of
 * the tool's refined material, set in the table's copper cup, and experience levels, more for each step.
 */
public enum UpgradeTrack {
    AREA("Mining Area", 1, 6, new int[]{2, 3, 4, 6, 8}, new int[]{5, 10, 15, 20, 30}),
    FORTUNE("Fortune", 3, 10, new int[]{1, 2, 2, 3, 4, 5, 6}, new int[]{5, 8, 10, 12, 15, 20, 25}),
    EFFICIENCY("Efficiency", 5, 10, new int[]{1, 2, 3, 4, 5}, new int[]{5, 8, 12, 15, 20});

    private final String displayName;
    private final int min, max;
    /** Refined cores and experience levels each step costs: index 0 is the step from {@code min} to {@code min + 1}. */
    private final int[] cores, levels;

    UpgradeTrack(String displayName, int min, int max, int[] cores, int[] levels) {
        this.displayName = displayName;
        this.min = min;
        this.max = max;
        this.cores = cores;
        this.levels = levels;
        if (cores.length != max - min || levels.length != max - min) throw new IllegalStateException(name() + ": one cost per step");
    }

    public String displayName() {
        return displayName;
    }

    /** The level a new tool has. */
    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    /** Refined cores the step up from {@code level} costs. */
    public int cores(int level) {
        return cores[Math.max(0, Math.min(cores.length - 1, level - min))];
    }

    /** Experience levels the step up from {@code level} costs. */
    public int levels(int level) {
        return this.levels[Math.max(0, Math.min(this.levels.length - 1, level - min))];
    }

    /** How a level reads: "5x5" for the mining area, a roman numeral for the enchantments. */
    public String describe(int level) {
        return this == AREA ? (2 * level + 1) + "x" + (2 * level + 1) : roman(level);
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }
}
