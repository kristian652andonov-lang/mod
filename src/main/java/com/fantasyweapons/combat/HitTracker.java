package com.fantasyweapons.combat;

import java.util.UUID;

/**
 * Transient record on a victim of the last fantasy-weapon hit. Used to give kill credit to the right weapon even when
 * the killing blow came from a lingering ability, a projectile or a damage-over-time status.
 */
public final class HitTracker {
    public UUID player;
    public UUID weapon;
    public long gameTime = Long.MIN_VALUE;
    public boolean rewarded;
}
