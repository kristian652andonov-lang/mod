package com.fantasyweapons.client.render.material;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.MaterialItem;
import com.fantasyweapons.material.MaterialOreItem;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** GeckoLib item renderers for the legendary materials: their animated, glowing models in the hand and inventory. */
public final class MaterialItemRenderers {
    private MaterialItemRenderers() {
    }

    /** Raw and refined materials ({@code geo/item/<weapon>_raw|refined}). */
    public static GeoItemRenderer<MaterialItem> material(MaterialItem item) {
        GeoItemRenderer<MaterialItem> r = new GeoItemRenderer<>(new DefaultedItemGeoModel<>(FantasyWeapons.id(item.asset())));
        r.addRenderLayer(new AutoGlowingGeoLayer<>(r));
        return r;
    }

    /** Ore blocks as items, drawn with the block's model ({@code geo/block/<weapon>_ore}). */
    public static GeoItemRenderer<MaterialOreItem> ore(MaterialOreItem item) {
        GeoItemRenderer<MaterialOreItem> r = new GeoItemRenderer<>(new DefaultedBlockGeoModel<>(FantasyWeapons.id(item.material().oreAsset())));
        r.addRenderLayer(new AutoGlowingGeoLayer<>(r));
        return r;
    }
}
