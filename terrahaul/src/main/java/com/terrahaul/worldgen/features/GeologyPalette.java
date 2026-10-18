package com.terrahaul.worldgen.features;

import com.terrahaul.worldgen.terrain.Geology;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Rock materials of each geological province, shared by boulders and rock formations. */
public final class GeologyPalette {
    private static final Block[][] BOULDER = {
        {Blocks.STONE, Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.STONE},
        {Blocks.GRANITE, Blocks.GRANITE, Blocks.STONE, Blocks.GRANITE, Blocks.ANDESITE},
        {Blocks.DEEPSLATE, Blocks.TUFF, Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE, Blocks.SMOOTH_BASALT},
        {Blocks.DIORITE, Blocks.CALCITE, Blocks.DIORITE, Blocks.STONE, Blocks.CALCITE},
        {Blocks.SANDSTONE, Blocks.STONE, Blocks.SANDSTONE, Blocks.ANDESITE, Blocks.SMOOTH_SANDSTONE},
        {Blocks.BASALT, Blocks.BLACKSTONE, Blocks.TUFF, Blocks.BASALT, Blocks.SMOOTH_BASALT},
    };
    private static final Block[] HOT = {Blocks.RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE, Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.RED_SANDSTONE};
    private static final Block[] DESERT = {Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE};

    private GeologyPalette() {
    }

    /**
     * @param climate 0 = temperate, 1 = desert, 2 = hot red-rock country
     */
    public static BlockState rock(int geology, int climate, RandomSource random) {
        Block[] palette = climate == 2 ? HOT : climate == 1 ? DESERT : BOULDER[Math.max(0, Math.min(Geology.COUNT - 1, geology))];
        return palette[random.nextInt(palette.length)].defaultBlockState();
    }

    /** Horizontal strata band for formations: a stable material per layer. */
    public static BlockState band(int geology, int climate, int y, int seed) {
        Block[] palette = climate == 2 ? HOT : climate == 1 ? DESERT : BOULDER[Math.max(0, Math.min(Geology.COUNT - 1, geology))];
        int band = Math.floorMod(y / 3 + seed, palette.length);
        return palette[band].defaultBlockState();
    }
}
