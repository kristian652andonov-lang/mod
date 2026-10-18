package com.terrahaul.client.render;

import com.terrahaul.registry.TerrahaulBlocks;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Foliage tints for leaf litter and shrubs, matching the leaves they come from. */
public final class TerrahaulColors {
    private TerrahaulColors() {
    }

    private static Block litter(String name) {
        return TerrahaulBlocks.LITTER.get(name).get();
    }

    private static Block shrub(String name) {
        return TerrahaulBlocks.SHRUB.get(name).get();
    }

    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> level != null && pos != null ? BiomeColors.getAverageFoliageColor(level, pos) : FoliageColor.getDefaultColor(),
            litter("oak_leaf_litter"), litter("jungle_leaf_litter"), litter("acacia_leaf_litter"), litter("dark_oak_leaf_litter"),
            shrub("leafy_bush"), shrub("dense_shrub"));
        event.register((state, level, pos, tint) -> FoliageColor.getBirchColor(), litter("birch_leaf_litter"));
        event.register((state, level, pos, tint) -> FoliageColor.getEvergreenColor(), litter("spruce_needle_litter"), shrub("alpine_shrub"));
        event.register((state, level, pos, tint) -> FoliageColor.getMangroveColor(), litter("mangrove_leaf_litter"));
    }

    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? FoliageColor.getDefaultColor() : -1, shrub("leafy_bush"), shrub("dense_shrub"));
        event.register((stack, tint) -> tint == 0 ? FoliageColor.getEvergreenColor() : -1, shrub("alpine_shrub"));
    }
}
