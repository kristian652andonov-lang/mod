package com.terrahaul.registry;

import com.terrahaul.Terrahaul;
import com.terrahaul.worldgen.features.BoulderConfiguration;
import com.terrahaul.worldgen.features.BoulderFeature;
import com.terrahaul.worldgen.features.FallenLogConfiguration;
import com.terrahaul.worldgen.features.FallenLogFeature;
import com.terrahaul.worldgen.features.OceanVentFeature;
import com.terrahaul.worldgen.features.RockFormationFeature;
import com.terrahaul.worldgen.ruins.RuinsFeature;
import com.terrahaul.worldgen.trees.ProceduralTreeConfiguration;
import com.terrahaul.worldgen.trees.ProceduralTreeFeature;
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
    public static final DeferredHolder<Feature<?>, OceanVentFeature> OCEAN_VENTS =
        FEATURES.register("ocean_vents", () -> new OceanVentFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, WaterfallFeature> WATERFALL =
        FEATURES.register("waterfall", () -> new WaterfallFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, ProceduralTreeFeature> PROCEDURAL_TREE =
        FEATURES.register("procedural_tree", () -> new ProceduralTreeFeature(ProceduralTreeConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, FallenLogFeature> FALLEN_LOG =
        FEATURES.register("fallen_log", () -> new FallenLogFeature(FallenLogConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, BoulderFeature> BOULDER =
        FEATURES.register("boulder", () -> new BoulderFeature(BoulderConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, RockFormationFeature> ROCK_FORMATIONS =
        FEATURES.register("rock_formations", () -> new RockFormationFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, RuinsFeature> RUINS =
        FEATURES.register("ruins", () -> new RuinsFeature(NoneFeatureConfiguration.CODEC));

    private TerrahaulFeatures() {
    }

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
