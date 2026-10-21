package com.terrahaul.worldgen.features;

import com.terrahaul.worldgen.surface.Fades;
import com.terrahaul.worldgen.terrain.Geology;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rock materials of each geological province, shared by boulders and rock formations.
 * <p>
 * Like the surface rule's fades, every palette is one single-colour family of three stops,
 * ordered from the palest, most weathered block to the freshest: grey (tuff, andesite, stone) in
 * temperate and granite country and on every mountain, dark (tuff, smooth basalt, deepslate), pale
 * (calcite, diorite, stone), sandstone in deserts and red sandstone in hot red-rock country, and
 * volcanic (blackstone, basalt, smooth basalt). Formations and boulders therefore have the colour
 * of the rock around them; which stop a block gets is decided by smooth noise or by the strata
 * (see {@link FormationShapes}), never block by block at random.
 */
public final class GeologyPalette {
    private static final BlockState[] GREY = {
        Blocks.TUFF.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.STONE.defaultBlockState()};
    private static final BlockState[] DARK = {
        Blocks.TUFF.defaultBlockState(), Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState()};
    private static final BlockState[] PALE = {
        Blocks.CALCITE.defaultBlockState(), Blocks.DIORITE.defaultBlockState(), Blocks.STONE.defaultBlockState()};
    private static final BlockState[] SANDY = {
        Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()};
    private static final BlockState[] RED = {
        Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState()};
    private static final BlockState[] VOLCANIC = {
        Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.SMOOTH_BASALT.defaultBlockState()};

    private GeologyPalette() {
    }

    /**
     * The rock family of a province: three stops of one colour, palest first.
     *
     * @param climate 0 = temperate, 1 = desert, 2 = hot red-rock country
     */
    public static BlockState[] family(int geology, int climate) {
        if (climate == 2) {
            return RED;
        }
        if (climate == 1) {
            return SANDY;
        }
        return switch (geology) {
            case Geology.DARK -> DARK;
            case Geology.LIMESTONE -> PALE;
            case Geology.SEDIMENTARY -> SANDY;
            case Geology.VOLCANIC -> VOLCANIC;
            default -> GREY;
        };
    }

    /**
     * The family of a province as the surface rule exposes it: on mountains every province but
     * the dark and pale ones shows grey rock, and volcanic provinces always show volcanic rock.
     */
    public static BlockState[] family(int geology, int climate, boolean mountainous) {
        if (geology == Geology.VOLCANIC) {
            return VOLCANIC;
        }
        if (mountainous) {
            return geology == Geology.DARK ? DARK : geology == Geology.LIMESTONE ? PALE : GREY;
        }
        return family(geology, climate);
    }

    /**
     * The second rock of a province on mountains, which the surface rule mixes into the family in
     * large blobs (see {@code Fades.rock}): pink granite in granite provinces, so their mountains
     * finally show it; dark basalt among the dark rock (light andesite read as camouflage spots);
     * grey andesite among the pale limestone; clay-brown terracotta among sedimentary beds; and
     * lenses of pale diorite in the grey rock elsewhere. Cobblestone is kept for the small rubble
     * blobs: in large patches it looked like man-made rubble.
     */
    public static BlockState mountainRock(int geology) {
        return switch (geology) {
            case Geology.GRANITE -> Blocks.GRANITE.defaultBlockState();
            case Geology.DARK -> Blocks.BASALT.defaultBlockState();
            case Geology.LIMESTONE -> Blocks.ANDESITE.defaultBlockState();
            case Geology.SEDIMENTARY -> Blocks.TERRACOTTA.defaultBlockState();
            default -> Blocks.DIORITE.defaultBlockState();
        };
    }

    /** A stop (0..2) of a province's family. */
    public static BlockState stop(int geology, int climate, int stop) {
        BlockState[] family = family(geology, climate);
        return family[Math.max(0, Math.min(family.length - 1, stop))];
    }

    /**
     * A random member of the province's family. All members share one colour, but a caller that
     * picks one per block still gets a patchwork; boulders and formations use smooth noise or the
     * strata instead.
     *
     * @param climate 0 = temperate, 1 = desert, 2 = hot red-rock country
     */
    public static BlockState rock(int geology, int climate, RandomSource random) {
        BlockState[] family = family(geology, climate);
        return family[random.nextInt(family.length)];
    }

    /**
     * Horizontal strata band for formations: the family cycled with height in beds of irregular
     * thickness (about 3-5 blocks), the same at every block of one height.
     */
    public static BlockState band(int geology, int climate, int y, int seed) {
        double phase = (y + seed * 7.3) / 14.0 + 0.2 * Math.sin(y * 0.37 + seed);
        double tri = Math.abs(phase - Math.floor(phase) - 0.5) * 2.0;
        return family(geology, climate)[Fades.stop(3, tri, 0.0)];
    }
}
