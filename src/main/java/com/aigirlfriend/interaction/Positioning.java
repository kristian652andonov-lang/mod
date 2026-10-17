package com.aigirlfriend.interaction;

/** How the two characters are arranged during an interaction. */
public enum Positioning {
    /** No forced placement (e.g. hand-holding while walking). */
    FREE,
    /** Facing each other at the interaction's distance. */
    FACE,
    /** She stands behind the partner, facing the same way (back hug). */
    BEHIND,
    /** She stands at the partner's side, facing the same way (lean). */
    SIDE,
    /** Both seated next to each other on seats. */
    SEATED,
    /** She rides the player in their arms. */
    CARRIED,
    /** Both on a bed. */
    BED
}
