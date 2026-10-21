package com.terrahaul.worldgen.ruins;

/**
 * The canonical footprint of a trailside ruin, in the frame of its {@link Canvas} ({@code u} along
 * the side facing the trail, {@code v} away from it). The survey uses it to place the ruin and the
 * planner to size its canvas.
 *
 * @param w         width of the body that must stand on level ground, off the path
 * @param d         depth of that body
 * @param hw        tallest part above the floor course, for the canvas
 * @param minU      the box of everything the ruin places apart from its footpath, inclusive
 * @param minV      (yards, rubble, campfires, sheds and gardens included)
 * @param maxU
 * @param maxV
 * @param doorU     column where the footpath leaves the front, and where the trail is met
 * @param maxRange  largest height difference allowed under the body
 * @param gap       extra blocks of yard between the frayed edge of the path and the front
 * @param footpath  whether a trodden footpath runs from the front to the trail
 * @param minTrail  the nearest the body may come to the centre of the trail
 */
record RuinSpec(int w, int d, int hw, int minU, int minV, int maxU, int maxV, int doorU, double maxRange, int gap, boolean footpath,
                double minTrail) {
    /** A spec with the usual clearances: a footpath, and the body at least 4 blocks from the centre of the trail. */
    static RuinSpec of(int w, int d, int hw, int minU, int minV, int maxU, int maxV, int doorU, double maxRange, int gap) {
        return new RuinSpec(w, d, hw, minU, minV, maxU, maxV, doorU, maxRange, gap, true, 4.0);
    }
}
