package com.fantasyweapons.tool;

import com.fantasyweapons.material.Material;
import software.bernie.geckolib.animatable.GeoItem;

/**
 * A legendary pickaxe, shovel or axe: made of one of the 13 legendary materials, drawn with the artist's glowing GeckoLib
 * model, digging a square round the block it strikes and carrying its own Fortune and Efficiency (see {@link
 * LegendaryTools}), all raised at the Legendary Upgrading Table.
 */
public interface LegendaryTool extends GeoItem {
    Material material();

    ToolKind kind();

    /** Asset name of its model, animation and texture, e.g. {@code void_crystal_pickaxe}; also its item id. */
    default String asset() {
        return material().toolPrefix() + "_" + kind().id();
    }
}
