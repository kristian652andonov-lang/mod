package com.fantasyweapons.world.wreck;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.Vec3;

/** The shapes the Weapon of God leaves on the world, beyond those of the two calamities ({@link WreckShapes}). */
public final class DivineShapes {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private DivineShapes() {
    }

    /**
     * {@code inner}, but sparing every column within {@code radius} of {@code keep} (the ground the caster stands on is
     * left standing as a pillar, so the power that ends the land around them does not drop them into the void).
     */
    public static ChunkShaper sparing(ChunkShaper inner, Vec3 keep, double radius) {
        return new ChunkShaper() {
            @Override
            public void shape(WreckChunk c) {
                boolean[] m = new boolean[256];
                boolean any = false;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        double dx = c.minX() + x + 0.5 - keep.x, dz = c.minZ() + z + 0.5 - keep.z;
                        m[x | z << 4] = dx * dx + dz * dz > radius * radius;
                        if (!m[x | z << 4]) any = true;
                    }
                }
                if (any) c.mask(m);
                inner.shape(c);
                if (any) c.mask(null);
            }

            @Override
            public boolean reaches(int chunkX, int chunkZ) {
                return inner.reaches(chunkX, chunkZ);
            }
        };
    }

    // ------------------------------------------------------------------------------------------------------------
    // Dimension Breakage: the land folded into an enormous cube of chunks
    // ------------------------------------------------------------------------------------------------------------

    /** How a tile of the cube is turned from the land it was taken from. */
    public enum Turn {
        /** Hung upside down (the cube's top: the land faces down, into the cube). */
        FLIP,
        /** On its side, the land facing in toward -x, +x, -z or +z (the cube's walls). */
        FACE_NEG_X, FACE_POS_X, FACE_NEG_Z, FACE_POS_Z
    }

    /** One 16-block cube of the fold: a section of the land, taken from a chunk outside, turned and set in place. */
    public record Tile(int section, Turn turn, PalettedContainer<BlockState> source) {
    }

    /**
     * A chunk inside the cube's footprint: its ground stays as it is (the cube's floor), its tiles - its piece of the walls
     * and of the top - are set in their sections, and everything above the cube is taken away.
     *
     * @param tiles the tiles of this chunk's column
     * @param above the lowest section cleared above the cube (everything from there up goes)
     */
    public static ChunkShaper fold(java.util.List<Tile> tiles, int above) {
        return c -> {
            for (int i = Math.max(0, above); i < c.sectionCount(); i++) {
                if (c.clearSection(i)) continue;
                int y0 = c.minY() + i * 16;
                for (int y = y0; y < y0 + 16; y++) for (int x = c.minX(); x < c.minX() + 16; x++) for (int z = c.minZ(); z < c.minZ() + 16; z++) c.clear(x, y, z);
            }
            for (Tile t : tiles) {
                int y0 = c.minY() + t.section() * 16;
                for (int ly = 0; ly < 16; ly++) {
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            int sx, sy, sz;
                            switch (t.turn()) {
                                case FLIP -> { sx = lx; sy = 15 - ly; sz = lz; }
                                // the land's top faces the way named: its up is that way, its depth along x or z
                                case FACE_NEG_X -> { sx = ly; sy = 15 - lx; sz = lz; }
                                case FACE_POS_X -> { sx = ly; sy = lx; sz = lz; }
                                case FACE_NEG_Z -> { sx = lx; sy = 15 - lz; sz = ly; }
                                default -> { sx = lx; sy = lz; sz = ly; }
                            }
                            BlockState s = t.source().get(sx, sy, sz);
                            if (WreckChunk.unbreakable(s) || s.hasBlockEntity() || !s.getFluidState().isEmpty()) s = AIR;
                            c.set(c.minX() + lx, y0 + ly, c.minZ() + lz, s);
                        }
                    }
                }
            }
        };
    }

    /**
     * The land outside the cube, torn off where it went into it: the ground scraped down a ragged ten to twenty-four
     * blocks below where it was, everything on it gone.
     */
    public static ChunkShaper torn(long salt) {
        return c -> {
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    if (c.masked(x, z)) continue;
                    int top = c.top(x, z), ground = c.ground(x, z);
                    if (top < c.minY()) continue;
                    double n = WreckShapes.noise(x * 0.07 + salt, z * 0.07 - salt) * 0.7 + WreckShapes.noise(x * 0.23 - salt, z * 0.23 + salt) * 0.3;
                    int down = Math.max(c.minY() + 1, ground - 10 - (int) Math.round(14 * n));
                    for (int y = top; y > down; y--) c.clear(x, y, z);
                }
            }
        };
    }
}
