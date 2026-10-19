package com.terrahaul.worldgen.ruins;

import com.terrahaul.worldgen.noise.SeedMixer;

/**
 * Names for abandoned homesteads, built from old English place-name parts ("Ashford",
 * "Thornley"). A ruin's name appears on its weathered name board and on the arms of signposts
 * that point to it, so following a sign leads to a real place.
 */
final class RuinNames {
    private static final String[] PREFIX = {"Ash", "Oak", "Elm", "Thorn", "Mill", "Stone", "Black", "Red", "Wolf", "Hare", "Fern",
        "Birch", "Holly", "Long", "Cold", "High", "Green", "Moss", "Old", "Brook", "Hazel", "Rush", "Alder", "Crow", "Lark", "Wil",
        "Ember", "Rowan", "Heath", "Marsh"};
    private static final String[] SUFFIX = {"ford", "ley", "ton", "wick", "by", "field", "stead", "dale", "moor", "mere", "holm", "well",
        "cross", "hurst", "worth", "combe", "brook", "thorpe", "gate", "fold"};
    /** Longest name that fits on a hanging sign at the default font. */
    static final int MAX_LENGTH = 10;

    private RuinNames() {
    }

    static String name(long seed) {
        long h = SeedMixer.mix(seed ^ 0x4E414D45L);
        String prefix = PREFIX[(int) (SeedMixer.unit(h) * PREFIX.length)];
        int s = (int) (SeedMixer.unit(SeedMixer.mix(h)) * SUFFIX.length);
        for (int i = 0; i < SUFFIX.length; i++) {
            String suffix = SUFFIX[(s + i) % SUFFIX.length];
            if (prefix.length() + suffix.length() <= MAX_LENGTH && !(prefix.endsWith("l") && suffix.startsWith("l"))) {
                return prefix + suffix;
            }
        }
        return prefix + "by";
    }
}
