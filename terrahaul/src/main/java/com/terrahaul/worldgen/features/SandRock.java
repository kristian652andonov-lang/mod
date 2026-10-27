package com.terrahaul.worldgen.features;

import com.terrahaul.registry.TerrahaulBlocks;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stones that come to rest on sand. A beach is sand over sandstone (see the surface rule), so the
 * pebbles, knee-high rocks and boulders lying on it are sandstone too, and red sandstone on red
 * sand. Before, they were the grey stone, andesite, granite and mossy rock of the land behind the
 * beach and looked carried there.
 * <p>
 * The test is the block the stone lies on, not the biome, so it also covers the sand that creeps
 * inland behind beaches, sandbars and the sandy water's edge, in any world. Rocky shores keep
 * their rock: on gravel, stone or soil (a stony shore, the pebble beach under a sea cliff) nothing
 * changes. Sandstone and red sandstone details keep their own colour on either sand, so deserts
 * and badlands keep their mix of the two.
 */
public final class SandRock {
    private SandRock() {
    }

    /** True for sand and red sand, the ground on which stones turn to sandstone. */
    public static boolean sand(BlockState ground) {
        return ground.is(Blocks.SAND) || ground.is(Blocks.RED_SAND);
    }

    /**
     * The detail to place on {@code ground}: a Terrahaul pebble or rock of any stone but sandstone
     * becomes the sandstone pebble or rock of the sand under it, with its count, variant, facing
     * and water kept. Any other block, or any other ground, is returned unchanged.
     */
    public static BlockState detail(BlockState detail, BlockState ground) {
        if (!sand(ground)) {
            return detail;
        }
        Block swap = (ground.is(Blocks.RED_SAND) ? Swaps.RED_SAND : Swaps.SAND).get(detail.getBlock());
        return swap == null ? detail : swap.withPropertiesOf(detail);
    }

    /**
     * The rock of a boulder whose foot is {@code ground}: the sandstone family on sand, the red
     * sandstone family on red sand (palest first, as in {@link GeologyPalette}), otherwise null.
     */
    public static BlockState[] family(BlockState ground) {
        if (ground.is(Blocks.SAND)) {
            return GeologyPalette.sandstone(false);
        }
        return ground.is(Blocks.RED_SAND) ? GeologyPalette.sandstone(true) : null;
    }

    /**
     * The swaps, built on first use (after the blocks are registered); class initialisation
     * publishes them safely to every worldgen thread.
     */
    private static final class Swaps {
        static final Map<Block, Block> SAND = swaps("sandstone");
        static final Map<Block, Block> RED_SAND = swaps("red_sandstone");
    }

    /** Every pebble and rock but the sandstone ones, to the pebble and rock of {@code target}. */
    private static Map<Block, Block> swaps(String target) {
        Map<Block, Block> map = new IdentityHashMap<>();
        Block pebbles = TerrahaulBlocks.PEBBLES.get(target).get();
        Block rock = TerrahaulBlocks.ROCKS.get(target).get();
        for (String stone : TerrahaulBlocks.STONES) {
            if (stone.equals("sandstone") || stone.equals("red_sandstone")) {
                continue;
            }
            map.put(TerrahaulBlocks.PEBBLES.get(stone).get(), pebbles);
            map.put(TerrahaulBlocks.ROCKS.get(stone).get(), rock);
        }
        return map;
    }
}
