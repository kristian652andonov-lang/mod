package com.fantasyweapons.ability;

/** Thrown by an executor to abort a cast with a player-facing reason (no cooldown beyond the fizzle cooldown). */
public class AbilityFailedException extends RuntimeException {
    public AbilityFailedException(String reason) {
        super(reason, null, false, false);
    }
}
