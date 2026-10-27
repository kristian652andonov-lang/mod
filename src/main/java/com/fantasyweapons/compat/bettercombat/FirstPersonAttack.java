package com.fantasyweapons.compat.bettercombat;

import org.joml.Matrix4f;

/**
 * A Better Combat attack as the first person sees it (see BetterCombatFirstPerson): {@code frame} is where the player
 * model holds the weapon, seen from the eye (the frame its held item is drawn in, before the item's third-person display
 * transform), and {@code weight} how far the first-person weapon has gone over to it from its own rest (0 to 1).
 */
public record FirstPersonAttack(Matrix4f frame, float weight) {
}
