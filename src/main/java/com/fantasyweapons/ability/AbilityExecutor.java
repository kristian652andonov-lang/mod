package com.fantasyweapons.ability;

/**
 * Server-side gameplay of an ability. Runs only on the logical server after all validation passed.
 * Returning {@code false} means the ability could not be performed (e.g. no valid target) and no cooldown is applied.
 */
@FunctionalInterface
public interface AbilityExecutor {
    boolean execute(AbilityContext ctx);
}
