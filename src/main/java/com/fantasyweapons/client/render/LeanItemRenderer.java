package com.fantasyweapons.client.render;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** GeckoLib's item renderer, leaving out what would not show (see {@link LeanGeoRenderer}), with its glow layer. */
public class LeanItemRenderer<T extends net.minecraft.world.item.Item & GeoAnimatable> extends GeoItemRenderer<T> implements LeanGeoRenderer<T> {
    public LeanItemRenderer(GeoModel<T> model) {
        super(model);
    }

    /** With GeckoLib's glow layer (the model's {@code _glowmask} texture). */
    public LeanItemRenderer<T> glowing() {
        addRenderLayer(new LeanGlowLayer<>(this));
        return this;
    }
}
