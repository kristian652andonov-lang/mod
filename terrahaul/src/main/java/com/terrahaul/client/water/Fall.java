package com.terrahaul.client.water;

import net.minecraft.core.BlockPos;

/**
 * A fall: neighbouring columns of falling water whose feet lie close together, such as the strands
 * of one curtain, the width of a step across a river, or a single trickle down a cliff.
 * <p>
 * Built from the columns the {@link WaterScanner} found; the effects scale with its width (the
 * number of columns) and its height (the tallest column).
 */
final class Fall {
    final WaterScanner.Column[] columns;
    final int width;
    final int height;
    final int footY;
    final int topY;
    /** Centre of the feet, in block coordinates (the middle of the blocks). */
    final double cx;
    final double cz;
    /** Horizontal radius of the feet around the centre. */
    final double radius;
    /** Water surface (or ground) where the water lands, the highest of the feet. */
    final double surface;
    /** Downstream direction, the way the water leaves the lip; zero when it has none. */
    final float dirX;
    final float dirZ;
    /** Whether any of the columns lands in water. */
    final boolean pool;
    /** Identity of the fall across rescans: the lowest packed position of its feet. */
    final long key;
    /** Distance from the camera this tick, for the effects. */
    double cameraDistance;

    Fall(WaterScanner.Column[] columns) {
        this.columns = columns;
        this.width = columns.length;
        int h = 0;
        int foot = Integer.MAX_VALUE;
        int top = Integer.MIN_VALUE;
        double sx = 0.0;
        double sz = 0.0;
        double surf = Double.NEGATIVE_INFINITY;
        double dx = 0.0;
        double dz = 0.0;
        boolean anyPool = false;
        long k = Long.MAX_VALUE;
        for (WaterScanner.Column c : columns) {
            h = Math.max(h, c.height());
            foot = Math.min(foot, c.y());
            top = Math.max(top, c.y() + c.height() - 1);
            sx += c.x() + 0.5;
            sz += c.z() + 0.5;
            surf = Math.max(surf, c.surface());
            dx += c.dirX();
            dz += c.dirZ();
            anyPool |= c.base() == WaterScanner.BASE_POOL;
            k = Math.min(k, BlockPos.asLong(c.x(), c.y(), c.z()));
        }
        this.height = h;
        this.footY = foot;
        this.topY = top;
        this.cx = sx / columns.length;
        this.cz = sz / columns.length;
        double r = 0.5;
        for (WaterScanner.Column c : columns) {
            r = Math.max(r, Math.hypot(c.x() + 0.5 - cx, c.z() + 0.5 - cz) + 0.5);
        }
        this.radius = r;
        this.surface = surf;
        double len = Math.hypot(dx, dz);
        this.dirX = len > 1e-3 ? (float) (dx / len) : 0.0F;
        this.dirZ = len > 1e-3 ? (float) (dz / len) : 0.0F;
        this.pool = anyPool;
        this.key = k;
    }

    /** Distance from a point to the nearest part of the fall (its vertical extent around the feet). */
    double distance(double x, double y, double z) {
        double ny = Math.max(footY, Math.min(topY + 1, y));
        double dx = cx - x;
        double dy = ny - y;
        double dz = cz - z;
        return Math.max(0.0, Math.sqrt(dx * dx + dy * dy + dz * dz) - radius);
    }
}
