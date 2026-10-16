package com.fantasyweapons.world.wreck;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Shapers shared by the ultimate weapons' abilities. */
public final class WreckShapes {
    private WreckShapes() {
    }

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();
    static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
    static final BlockState COAL = Blocks.COAL_BLOCK.defaultBlockState();

    /**
     * A perfect cylinder erased from the sky to the bottom of the world: its walls are seared into glowing magma with
     * seams of lava, and a lava sea pools on the floor.
     *
     * @param lavaDepth layers of lava over the floor
     */
    public static ChunkShaper shaft(Vec3 center, double radius, int lavaDepth) {
        double r2 = radius * radius, wall = radius + 2.5, wall2 = wall * wall;
        return c -> {
            int floor = c.minY() + 1;
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z, d2 = dx * dx + dz * dz;
                    if (d2 > wall2) continue;
                    int top = c.top(x, z);
                    if (d2 <= r2) {
                        for (int y = floor; y <= top; y++) c.set(x, y, z, y < floor + lavaDepth ? LAVA : AIR);
                        if (top < floor + lavaDepth) for (int y = Math.max(floor, top + 1); y < floor + lavaDepth; y++) c.set(x, y, z, LAVA);
                    } else {
                        // the seared wall: magma, threaded with veins of lava where the beam's edge bit hardest
                        double edge = Math.sqrt(d2) - radius;
                        for (int y = floor; y <= top; y++) {
                            BlockState old = c.get(x, y, z);
                            if (!old.getFluidState().isEmpty()) {
                                // water and lava at the edge are seared into a wall of obsidian that holds back the rest
                                c.set(x, y, z, OBSIDIAN);
                                continue;
                            }
                            if (!WreckChunk.terrain(old)) {
                                if (!old.isAir()) c.set(x, y, z, AIR);
                                continue;
                            }
                            float roll = c.random().nextFloat();
                            BlockState seared = edge < 1.2 && roll < 0.08 ? LAVA : roll < 0.75 ? MAGMA : roll < 0.9 ? BLACKSTONE : OBSIDIAN;
                            c.set(x, y, z, seared);
                        }
                    }
                }
            }
        };
    }

    /**
     * A sphere erased from the world, the bowl it leaves scarred: a crust of blackstone, obsidian and crying obsidian two
     * blocks thick, and water or lava at its edge crushed into obsidian so nothing pours in.
     */
    public static ChunkShaper sphere(Vec3 center, double radius) {
        double shell = radius + 2.2;
        return c -> {
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z, d2 = dx * dx + dz * dz;
                    if (d2 > shell * shell) continue;
                    double half = Math.sqrt(shell * shell - d2);
                    int y0 = Math.max(c.minY(), (int) Math.floor(center.y - half)), y1 = Math.min(c.top(x, z), (int) Math.ceil(center.y + half));
                    for (int y = y0; y <= y1; y++) {
                        double dy = y + 0.5 - center.y, r = Math.sqrt(d2 + dy * dy);
                        if (r <= radius) {
                            c.clear(x, y, z);
                            continue;
                        }
                        if (r > shell) continue;
                        BlockState old = c.get(x, y, z);
                        if (!old.getFluidState().isEmpty()) c.set(x, y, z, OBSIDIAN);
                        else if (WreckChunk.terrain(old)) {
                            float roll = c.random().nextFloat();
                            c.set(x, y, z, roll < 0.15f ? CRYING : roll < 0.5f ? OBSIDIAN : BLACKSTONE);
                        } else if (!old.isAir()) c.clear(x, y, z);
                    }
                }
            }
        };
    }
}
