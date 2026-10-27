package com.fantasyweapons.ability;

/**
 * A tunable number belonging to an ability. Every param becomes an entry in the server config under
 * {@code [abilities.<weapon>.<ability>]}, so balancing never requires code changes.
 */
public record AbilityParam(String name, double defaultValue, double min, double max, String comment) {

    public static AbilityParam of(String name, double defaultValue, String comment) {
        return new AbilityParam(name, defaultValue, 0, Math.max(1_000_000, defaultValue * 100), comment);
    }

    public static AbilityParam fraction(String name, double defaultValue, String comment) {
        return new AbilityParam(name, defaultValue, 0, 1, comment);
    }
}
