package com.terrahaul.worldgen.trees;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A tree under construction, in coordinates relative to the trunk base. Shape generators add
 * logs, leaves, roots and decorations through the primitives here; {@link ProceduralTreeFeature}
 * then validates and places the result.
 * <p>
 * Every primitive clips to the blueprint's reach horizontally: features may only write within the
 * 3x3 chunk area around the chunk being decorated. {@link #MAX_REACH} (15 blocks from the origin)
 * is always safe; ancient trees, whose origin is moved near the middle of its chunk, may reach
 * up to {@link #ANCIENT_REACH}.
 */
public final class TreeBlueprint {
    public static final int MAX_REACH = 15;
    public static final int ANCIENT_REACH = 22;

    private final TreeSpecies species;
    private final RandomSource random;
    final Long2ObjectLinkedOpenHashMap<BlockState> logs = new Long2ObjectLinkedOpenHashMap<>();
    final LongOpenHashSet leaves = new LongOpenHashSet();
    final LongOpenHashSet altLeaves = new LongOpenHashSet();
    final Long2ObjectLinkedOpenHashMap<BlockState> roots = new Long2ObjectLinkedOpenHashMap<>();
    final Long2ObjectLinkedOpenHashMap<BlockState> decorations = new Long2ObjectLinkedOpenHashMap<>();
    /** Trunk footprint at the base, used to anchor the tree on slopes. */
    final LongOpenHashSet trunkBase = new LongOpenHashSet();
    /**
     * Roots that follow the ground: for each column (key with y = 0), how far the root rises above
     * the local ground (negative: just below it, shown as rooted dirt).
     */
    final Long2DoubleOpenHashMap groundRoots = new Long2DoubleOpenHashMap();
    int height;
    private final int reach;

    public TreeBlueprint(TreeSpecies species, RandomSource random) {
        this(species, random, MAX_REACH);
    }

    public TreeBlueprint(TreeSpecies species, RandomSource random, int reach) {
        this.species = species;
        this.random = random;
        this.reach = reach;
        groundRoots.defaultReturnValue(Double.NEGATIVE_INFINITY);
    }

    public TreeSpecies species() {
        return species;
    }

    public RandomSource random() {
        return random;
    }

    static long key(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    private boolean inReach(int x, int z) {
        return x >= -reach && x <= reach && z >= -reach && z <= reach;
    }

    // ------------------------------------------------------------------------------------------
    // Logs
    // ------------------------------------------------------------------------------------------

    public void log(int x, int y, int z, Direction.Axis axis) {
        if (!inReach(x, z)) {
            return;
        }
        long k = key(x, y, z);
        BlockState existing = logs.get(k);
        if (existing != null && existing.is(species.wood().getBlock())) {
            return;
        }
        logs.put(k, species.log().setValue(RotatedPillarBlock.AXIS, axis));
        height = Math.max(height, y + 1);
    }

    /** Bark on all six sides: used where branches join the trunk and for exposed roots. */
    public void wood(int x, int y, int z) {
        if (!inReach(x, z)) {
            return;
        }
        logs.put(key(x, y, z), species.wood());
    }

    /**
     * Straight vertical trunk of the given footprint radius. {@code radius} 0 is a single column,
     * 0.5 a 2x2 trunk (footprint x/z in {0,1}), larger values a round trunk.
     */
    public void trunk(int baseY, int topY, double radius) {
        for (int y = baseY; y <= topY; y++) {
            trunkLayer(0, y, 0, radius);
        }
    }

    /**
     * Round trunk that narrows from {@code baseRadius} to {@code topRadius}, slightly faster near
     * the ground, like the trunk of an old tree.
     */
    public void taperedTrunk(int topY, double baseRadius, double topRadius) {
        for (int y = 0; y <= topY; y++) {
            double t = (double) y / Math.max(1, topY);
            trunkLayer(0, y, 0, topRadius + (baseRadius - topRadius) * Math.pow(1.0 - t, 1.3));
        }
    }

    void trunkLayer(int cx, int y, int cz, double radius) {
        if (radius <= 0.01) {
            log(cx, y, cz, Direction.Axis.Y);
            if (y == 0) {
                trunkBase.add(key(cx, 0, cz));
            }
            return;
        }
        if (radius <= 0.6) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    log(cx + dx, y, cz + dz, Direction.Axis.Y);
                    if (y == 0) {
                        trunkBase.add(key(cx + dx, 0, cz + dz));
                    }
                }
            }
            return;
        }
        int r = Mth.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= radius * radius + 0.5) {
                    log(cx + dx, y, cz + dz, Direction.Axis.Y);
                    if (y == 0) {
                        trunkBase.add(key(cx + dx, 0, cz + dz));
                    }
                }
            }
        }
    }

    /**
     * Branch from {@code (x0,y0,z0)} to {@code (x1,y1,z1)} with the given thickness radius. Log
     * axes follow the dominant direction of the branch, like vanilla fancy oaks.
     */
    public void branch(double x0, double y0, double z0, double x1, double y1, double z1, double radius) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01) {
            return;
        }
        Direction.Axis axis;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ay >= ax && ay >= az) {
            axis = Direction.Axis.Y;
        } else {
            axis = ax >= az ? Direction.Axis.X : Direction.Axis.Z;
        }
        int steps = Mth.ceil(len * 2.0);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double px = x0 + dx * t;
            double py = y0 + dy * t;
            double pz = z0 + dz * t;
            double r = radius * (1.0 - 0.35 * t);
            if (r < 0.6) {
                log(Mth.floor(px + 0.5), Mth.floor(py + 0.5), Mth.floor(pz + 0.5), axis);
            } else {
                int ri = Mth.ceil(r);
                for (int ox = -ri; ox <= ri; ox++) {
                    for (int oy = -ri; oy <= ri; oy++) {
                        for (int oz = -ri; oz <= ri; oz++) {
                            if (ox * ox + oy * oy + oz * oz <= r * r + 0.3) {
                                log(Mth.floor(px + 0.5) + ox, Mth.floor(py + 0.5) + oy, Mth.floor(pz + 0.5) + oz, axis);
                            }
                        }
                    }
                }
            }
        }
    }

    /** A curved branch through a control point (quadratic Bezier), for cherry and acacia limbs. */
    public void curvedBranch(double x0, double y0, double z0, double cx, double cy, double cz, double x1, double y1, double z1, double radius) {
        int segments = 5;
        double px = x0;
        double py = y0;
        double pz = z0;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double u = 1.0 - t;
            double nx = u * u * x0 + 2 * u * t * cx + t * t * x1;
            double ny = u * u * y0 + 2 * u * t * cy + t * t * y1;
            double nz = u * u * z0 + 2 * u * t * cz + t * t * z1;
            branch(px, py, pz, nx, ny, nz, radius * (1.0 - 0.3 * (t - 1.0 / segments)));
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Leaves
    // ------------------------------------------------------------------------------------------

    public void leaf(int x, int y, int z) {
        if (inReach(x, z)) {
            leaves.add(key(x, y, z));
        }
    }

    /**
     * Irregular ellipsoidal leaf cluster. Edge blocks are dropped with increasing probability so
     * clusters never look like perfect spheres; {@code density} below 1 thins the interior too.
     */
    public void blob(double cx, double cy, double cz, double rx, double ry, double rz, double density) {
        int minX = Mth.floor(cx - rx);
        int maxX = Mth.ceil(cx + rx);
        int minY = Mth.floor(cy - ry);
        int maxY = Mth.ceil(cy + ry);
        int minZ = Mth.floor(cz - rz);
        int maxZ = Mth.ceil(cz + rz);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double nx = (x - cx) / rx;
                    double ny = (y - cy) / ry;
                    double nz = (z - cz) / rz;
                    double d = nx * nx + ny * ny + nz * nz;
                    if (d > 1.0) {
                        continue;
                    }
                    double edge = d > 0.55 ? (d - 0.55) / 0.45 : 0.0;
                    if (random.nextDouble() < edge * 0.75 || random.nextDouble() > density + (1.0 - density) * (1.0 - d)) {
                        continue;
                    }
                    leaf(x, y, z);
                }
            }
        }
    }

    /** Flat leaf pad (acacia, jungle layers, conifer whorls). */
    public void disc(double cx, int y, double cz, double radius, double density) {
        int r = Mth.ceil(radius);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = (x * x + z * z) / (radius * radius);
                if (d > 1.0) {
                    continue;
                }
                double edge = d > 0.6 ? (d - 0.6) / 0.4 : 0.0;
                if (random.nextDouble() < edge * 0.7 || random.nextDouble() > density) {
                    continue;
                }
                leaf(Mth.floor(cx) + x, y, Mth.floor(cz) + z);
            }
        }
    }

    /** Marks some of the existing leaves as the species' alternative leaves (flowering azalea). */
    public void sprinkleAltLeaves(double fraction) {
        leaves.forEach((long k) -> {
            if (random.nextDouble() < fraction) {
                altLeaves.add(k);
            }
        });
    }

    // ------------------------------------------------------------------------------------------
    // Roots and decorations
    // ------------------------------------------------------------------------------------------

    public void root(int x, int y, int z, BlockState state) {
        if (inReach(x, z)) {
            roots.put(key(x, y, z), state);
        }
    }

    public void decoration(int x, int y, int z, BlockState state) {
        if (inReach(x, z)) {
            decorations.put(key(x, y, z), state);
        }
    }

    /** Exposed surface roots radiating from the trunk base and diving into the ground. */
    public void surfaceRoots(int count, int length, double trunkRadius) {
        rootSystem(count, trunkRadius, length + 1.0 + 1.5 * trunkRadius, 0.7 + 0.9 * trunkRadius, 0.45 + 0.35 * trunkRadius);
    }

    /**
     * A root system that follows the ground. Each main root leaves the trunk as a buttress fin
     * {@code buttress} blocks high that drops away steeply, then runs on as a low, meandering ridge
     * with an occasional knee, narrowing as it goes, and dives under the soil near its end, where
     * it shows as rooted dirt. Most main roots fork once. Heights are relative to the ground under
     * each column, so roots hug slopes instead of floating over them.
     *
     * @param halfWidth half the width of a root where it leaves the trunk
     */
    public void rootSystem(int count, double trunkRadius, double length, double buttress, double halfWidth) {
        double c = trunkRadius > 0.4 && trunkRadius < 0.6 ? 0.5 : 0.0;
        double start = trunkRadius < 0.6 ? 0.6 : trunkRadius;
        double a0 = random.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < count; i++) {
            double a = a0 + i * Math.PI * 2.0 / count + (random.nextDouble() - 0.5) * 0.6;
            double len = length * (0.65 + 0.5 * random.nextDouble());
            double sx = c + Math.cos(a) * start * 0.85;
            double sz = c + Math.sin(a) * start * 0.85;
            double h0 = buttress * (0.7 + 0.5 * random.nextDouble());
            traceRoot(sx, sz, a, len, h0, halfWidth, random.nextFloat() < 0.75);
        }
    }

    private void traceRoot(double x, double z, double angle, double len, double h0, double w0, boolean fork) {
        double wander = (random.nextDouble() - 0.5) * 0.9;
        double phase = random.nextDouble() * Math.PI * 2.0;
        double forkAt = 0.3 + 0.3 * random.nextDouble();
        int forkSide = random.nextBoolean() ? 1 : -1;
        double step = 0.4;
        for (double s = 0.0; s <= len; s += step) {
            double t = s / len;
            double a = angle + wander * t + 0.3 * Math.sin(phase + t * 5.0);
            x += Math.cos(a) * step;
            z += Math.sin(a) * step;
            // a tall fin at the trunk, a low ridge with knees further out, below ground at the tip
            double rise = h0 * Math.pow(1.0 - t, 2.6) + 0.7 * (1.0 - t) - 1.6 * t * t
                + 0.6 * Math.max(0.0, Math.sin(phase * 1.7 + t * 11.0)) * (1.0 - t);
            double w = Math.max(0.45, w0 * (1.0 - 0.7 * t));
            markRoot(x, z, w, rise);
            if (fork && t >= forkAt) {
                fork = false;
                traceRoot(x, z, a + forkSide * (0.55 + 0.4 * random.nextDouble()), (len - s) * 0.8, Math.max(0.0, rise) * 0.5, w * 0.75, false);
            }
        }
    }

    private void markRoot(double x, double z, double w, double rise) {
        int r = Mth.ceil(w);
        for (int ix = Mth.floor(x) - r; ix <= Mth.floor(x) + r; ix++) {
            for (int iz = Mth.floor(z) - r; iz <= Mth.floor(z) + r; iz++) {
                double dx = ix + 0.5 - x;
                double dz = iz + 0.5 - z;
                if (dx * dx + dz * dz > w * w + 0.15 || !inReach(ix, iz)) {
                    continue;
                }
                long k = key(ix, 0, iz);
                if (trunkBase.contains(k)) {
                    continue;
                }
                // the root's crest is lower towards its edges
                double edge = Math.sqrt(dx * dx + dz * dz) / Math.max(w, 0.5);
                double h = rise - (rise > 1.0 ? (rise - 1.0) * 0.35 * edge : 0.0) - 0.4 * edge;
                if (h > groundRoots.get(k)) {
                    groundRoots.put(k, h);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Leaf distances
    // ------------------------------------------------------------------------------------------

    /**
     * Computes the vanilla {@code distance} property of every leaf by a breadth-first search from
     * the logs, and removes leaves farther than six blocks from any log, which would otherwise
     * decay away immediately after generation.
     *
     * @return map from leaf position to its distance (1..6)
     */
    Long2IntOpenHashMap leafDistances() {
        Long2IntOpenHashMap dist = new Long2IntOpenHashMap();
        dist.defaultReturnValue(Integer.MAX_VALUE);
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongSet logSet = logs.keySet();
        for (long k : logSet) {
            for (Direction d : Direction.values()) {
                long n = BlockPos.offset(k, d);
                if (leaves.contains(n) && dist.get(n) > 1) {
                    dist.put(n, 1);
                    queue.enqueue(n);
                }
            }
        }
        while (!queue.isEmpty()) {
            long k = queue.dequeueLong();
            int d = dist.get(k);
            if (d >= 6) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                long n = BlockPos.offset(k, dir);
                if (leaves.contains(n) && !logSet.contains(n) && dist.get(n) > d + 1) {
                    dist.put(n, d + 1);
                    queue.enqueue(n);
                }
            }
        }
        leaves.removeIf((long k) -> dist.get(k) > 6 || logs.containsKey(k));
        return dist;
    }
}
