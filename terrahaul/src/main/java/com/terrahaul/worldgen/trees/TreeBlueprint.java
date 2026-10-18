package com.terrahaul.worldgen.trees;

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
 * Every primitive clips to {@link #MAX_REACH} horizontally: features may only write within the
 * 3x3 chunk area around the chunk being decorated, and 15 blocks from the origin is always safe.
 */
public final class TreeBlueprint {
    public static final int MAX_REACH = 15;

    private final TreeSpecies species;
    private final RandomSource random;
    final Long2ObjectLinkedOpenHashMap<BlockState> logs = new Long2ObjectLinkedOpenHashMap<>();
    final LongOpenHashSet leaves = new LongOpenHashSet();
    final LongOpenHashSet altLeaves = new LongOpenHashSet();
    final Long2ObjectLinkedOpenHashMap<BlockState> roots = new Long2ObjectLinkedOpenHashMap<>();
    final Long2ObjectLinkedOpenHashMap<BlockState> decorations = new Long2ObjectLinkedOpenHashMap<>();
    /** Trunk footprint at the base, used to anchor the tree on slopes. */
    final LongOpenHashSet trunkBase = new LongOpenHashSet();
    int height;

    public TreeBlueprint(TreeSpecies species, RandomSource random) {
        this.species = species;
        this.random = random;
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

    private static boolean inReach(int x, int z) {
        return x >= -MAX_REACH && x <= MAX_REACH && z >= -MAX_REACH && z <= MAX_REACH;
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
        for (int i = 0; i < count; i++) {
            double angle = (i + random.nextDouble() * 0.6) * Math.PI * 2.0 / count;
            double sx = Math.cos(angle) * (trunkRadius + 0.5);
            double sz = Math.sin(angle) * (trunkRadius + 0.5);
            int len = length + random.nextInt(2);
            double ex = Math.cos(angle) * (trunkRadius + len);
            double ez = Math.sin(angle) * (trunkRadius + len);
            // the root leaves the trunk a little above ground and sinks below it at its tip
            branch(sx, 0.6 + random.nextDouble() * 0.8, sz, ex, -1.5, ez, 0.0);
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
