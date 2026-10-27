package com.fantasyweapons.world.wreck;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the wall of a shaft or crater is lined with: the block of each big chunk of rock jutting out of it (by the chunk's
 * own random value in [0, 1) and where the block is), and of the seams set back between the chunks. {@code null} leaves a
 * gap in the wall. Shapers run on many threads, so a wall must only work from what it is given; and nothing in a wall may
 * fall, melt or flow (it is set in place without block updates).
 */
public interface Wall {
    BlockState rock(double v, int x, int y, int z);

    BlockState seam(double v, int x, int y, int z);

    /** The ultimate weapons' beams: seared blackstone, basalt, deepslate and netherrack, split by glowing magma. */
    Wall SEARED = new Wall() {
        @Override
        public BlockState rock(double v, int x, int y, int z) {
            return v < 0.5 ? WreckShapes.BLACKSTONE : v < 0.7 ? WreckShapes.BASALT : v < 0.85 ? WreckShapes.DEEPSLATE : WreckShapes.NETHERRACK;
        }

        @Override
        public BlockState seam(double v, int x, int y, int z) {
            return WreckShapes.MAGMA;
        }
    };

    /** A crater blasted out from above: obsidian, blackstone and deepslate, split by crying obsidian. */
    Wall OBSIDIAN = new Wall() {
        @Override
        public BlockState rock(double v, int x, int y, int z) {
            return v < 0.45 ? WreckShapes.OBSIDIAN : v < 0.8 ? WreckShapes.BLACKSTONE : WreckShapes.DEEPSLATE;
        }

        @Override
        public BlockState seam(double v, int x, int y, int z) {
            return WreckShapes.CRYING;
        }
    };

    /**
     * Trident from Heaven: the crater burned bright with the light that struck it - yellow concrete, gold and yellow
     * glazed tiles, here and there glowstone, split by seams of glowing light.
     */
    Wall GOLDEN = new Wall() {
        private static final BlockState CONCRETE = Blocks.YELLOW_CONCRETE.defaultBlockState(), GOLD = Blocks.GOLD_BLOCK.defaultBlockState(),
                TILE = Blocks.YELLOW_GLAZED_TERRACOTTA.defaultBlockState(), GLOW = Blocks.GLOWSTONE.defaultBlockState(),
                RAW = Blocks.RAW_GOLD_BLOCK.defaultBlockState(), LIGHT = Blocks.SHROOMLIGHT.defaultBlockState();

        @Override
        public BlockState rock(double v, int x, int y, int z) {
            // each chunk mostly one gold, a few of its blocks another
            double speck = hash(x, y, z, 11);
            if (speck < 0.06) return GLOW;
            if (speck < 0.14) return RAW;
            return v < 0.42 ? CONCRETE : v < 0.7 ? GOLD : v < 0.9 ? TILE : GLOW;
        }

        @Override
        public BlockState seam(double v, int x, int y, int z) {
            return hash(x, y, z, 12) < 0.5 ? GLOW : LIGHT;
        }
    };

    /**
     * Dimension Breakage: the colours of the broken dimension - the wall striped in bands of the rainbow running round
     * it, each chunk of rock its band's colour or the next, split by seams of pale light.
     */
    Wall RAINBOW = new Wall() {
        private static final BlockState[] BANDS = states(Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE,
                Blocks.LIGHT_BLUE_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.MAGENTA_CONCRETE);
        private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState(), CRYSTAL = Blocks.AMETHYST_BLOCK.defaultBlockState();

        @Override
        public BlockState rock(double v, int x, int y, int z) {
            // bands six blocks high, a chunk shifting into the next colour now and then so they do not run ruler-straight
            int band = Math.floorDiv(y, 6) + (v > 0.7 ? 1 : 0);
            return BANDS[Math.floorMod(band, BANDS.length)];
        }

        @Override
        public BlockState seam(double v, int x, int y, int z) {
            return hash(x, y, z, 13) < 0.7 ? LIGHT : CRYSTAL;
        }
    };

    /**
     * The Combined Power of the 6 Gods: what is left at its edge is not one rock but every kind of block there is, shaken
     * apart - each chunk of the wall a different block, many cracked or crumbled, with gaps broken through it.
     */
    Wall SHATTERED = new Wall() {
        private static final BlockState[] KINDS = states(Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.PRISMARINE_BRICKS, Blocks.PURPUR_BLOCK,
                Blocks.END_STONE_BRICKS, Blocks.QUARTZ_BRICKS, Blocks.MUD_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS,
                Blocks.CUT_COPPER, Blocks.WEATHERED_CUT_COPPER, Blocks.OXIDIZED_CUT_COPPER, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE,
                Blocks.CALCITE, Blocks.TUFF_BRICKS, Blocks.PACKED_ICE, Blocks.AMETHYST_BLOCK, Blocks.TERRACOTTA, Blocks.CYAN_TERRACOTTA,
                Blocks.WHITE_TERRACOTTA, Blocks.BRICKS, Blocks.NETHER_BRICKS, Blocks.DARK_PRISMARINE, Blocks.OBSIDIAN, Blocks.GRANITE,
                Blocks.POLISHED_DIORITE, Blocks.POLISHED_ANDESITE);
        /** The broken forms, by the same index as {@link #KINDS} where there is one. */
        private static final BlockState[] CRACKED = states(Blocks.CRACKED_STONE_BRICKS, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.PRISMARINE,
                Blocks.PURPUR_PILLAR, Blocks.END_STONE, Blocks.QUARTZ_BLOCK, Blocks.PACKED_MUD, Blocks.NETHERRACK,
                Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.COPPER_BLOCK, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER, Blocks.SANDSTONE,
                Blocks.RED_SANDSTONE, Blocks.DRIPSTONE_BLOCK, Blocks.TUFF, Blocks.BLUE_ICE, Blocks.BUDDING_AMETHYST, Blocks.BROWN_TERRACOTTA,
                Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.CALCITE, Blocks.TERRACOTTA, Blocks.CRACKED_NETHER_BRICKS, Blocks.PRISMARINE, Blocks.CRYING_OBSIDIAN,
                Blocks.COBBLESTONE, Blocks.DIORITE, Blocks.ANDESITE);
        private static final BlockState[] SEAMS = states(Blocks.GLOWSTONE, Blocks.SEA_LANTERN, Blocks.SHROOMLIGHT, Blocks.OCHRE_FROGLIGHT,
                Blocks.PEARLESCENT_FROGLIGHT, Blocks.VERDANT_FROGLIGHT);

        @Override
        public BlockState rock(double v, int x, int y, int z) {
            double roll = hash(x, y, z, 14);
            // gaps broken right through
            if (roll < 0.1) return null;
            int kind = (int) (v * KINDS.length) % KINDS.length;
            // some blocks of a chunk are another chunk's, knocked loose and lodged there
            if (roll > 0.93) kind = (int) (hash(x, y, z, 15) * KINDS.length) % KINDS.length;
            return roll < 0.38 ? CRACKED[kind] : KINDS[kind];
        }

        @Override
        public BlockState seam(double v, int x, int y, int z) {
            // the powers' light still burning in the cracks, each seam its own colour
            return SEAMS[(int) (v * SEAMS.length) % SEAMS.length];
        }
    };

    private static BlockState[] states(Block... blocks) {
        BlockState[] out = new BlockState[blocks.length];
        for (int i = 0; i < blocks.length; i++) out[i] = blocks[i].defaultBlockState();
        return out;
    }

    /** A random value in [0, 1) for a block, the same every time it is asked. */
    private static double hash(int x, int y, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ salt * 0x27D4EB2F165667C5L;
        h ^= h >>> 31;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }
}
