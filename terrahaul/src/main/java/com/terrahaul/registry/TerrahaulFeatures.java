package com.terrahaul.registry;

import com.terrahaul.Terrahaul;
import com.terrahaul.worldgen.water.SurfaceWaterFeature;
import com.terrahaul.worldgen.water.WaterfallFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Feature types. Their configured and placed variants live in the mod's data pack. */
public final class TerrahaulFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Terrahaul.MOD_ID);

    public static final DeferredHolder<Feature<?>, SurfaceWaterFeature> SURFACE_WATER =
        FEATURES.register("surface_water", () -> new SurfaceWaterFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, WaterfallFeature> WATERFALL =
        FEATURES.register("waterfall", () -> new WaterfallFeature(NoneFeatureConfiguration.CODEC));

    private TerrahaulFeatures() {
    }

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
