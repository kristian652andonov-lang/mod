package com.terrahaul.registry;

import com.terrahaul.Terrahaul;
import com.terrahaul.worldgen.features.BoulderConfiguration;
import com.terrahaul.worldgen.features.BoulderFeature;
import com.terrahaul.worldgen.features.FallenLogConfiguration;
import com.terrahaul.worldgen.features.FallenLogFeature;
import com.terrahaul.worldgen.features.FloatingTerrainCleanup;
import com.terrahaul.worldgen.features.MagmaChamberFeature;
import com.terrahaul.worldgen.features.OceanVentFeature;
import com.terrahaul.worldgen.features.RockFormationFeature;
import com.terrahaul.worldgen.features.UnsupportedPlantCleanup;
import com.terrahaul.worldgen.features.VolcanoThawFeature;
import com.terrahaul.worldgen.features.WaterWallSealer;
import com.terrahaul.worldgen.features.lake.LakeFloorFeature;
import com.terrahaul.worldgen.features.lake.LakeLifeFeature;
import com.terrahaul.worldgen.features.marine.CoralReefFeature;
import com.terrahaul.worldgen.features.marine.SeaGrottoFeature;
import com.terrahaul.worldgen.features.marine.SeaVegetationFeature;
import com.terrahaul.worldgen.features.marine.SeafloorRocksFeature;
import com.terrahaul.worldgen.features.marine.VanillaFallbackFeature;
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

    public static final DeferredHolder<Feature<?>, FloatingTerrainCleanup> TERRAIN_CLEANUP =
        FEATURES.register("terrain_cleanup", () -> new FloatingTerrainCleanup(NoneFeatureConfiguration.CODEC, false));
    public static final DeferredHolder<Feature<?>, FloatingTerrainCleanup> TERRAIN_CLEANUP_LATE =
        FEATURES.register("terrain_cleanup_late", () -> new FloatingTerrainCleanup(NoneFeatureConfiguration.CODEC, true));
    public static final DeferredHolder<Feature<?>, WaterWallSealer> WATER_WALL_SEALER =
        FEATURES.register("water_wall_sealer", () -> new WaterWallSealer(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, UnsupportedPlantCleanup> PLANT_CLEANUP =
        FEATURES.register("plant_cleanup", () -> new UnsupportedPlantCleanup(NoneFeatureConfiguration.CODEC));
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
    public static final DeferredHolder<Feature<?>, VolcanoThawFeature> VOLCANO_THAW =
        FEATURES.register("volcano_thaw", () -> new VolcanoThawFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, MagmaChamberFeature> MAGMA_CHAMBER =
        FEATURES.register("magma_chamber", () -> new MagmaChamberFeature(NoneFeatureConfiguration.CODEC));

    // sea floors (see worldgen/features/marine)
    public static final DeferredHolder<Feature<?>, SeafloorRocksFeature> SEAFLOOR_ROCKS =
        FEATURES.register("seafloor_rocks", () -> new SeafloorRocksFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, SeaGrottoFeature> SEA_GROTTOS =
        FEATURES.register("sea_grottos", () -> new SeaGrottoFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, CoralReefFeature> CORAL_REEFS =
        FEATURES.register("coral_reefs", () -> new CoralReefFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, SeaVegetationFeature> SEA_VEGETATION =
        FEATURES.register("sea_vegetation", () -> new SeaVegetationFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, VanillaFallbackFeature> VANILLA_FALLBACK =
        FEATURES.register("vanilla_fallback", () -> new VanillaFallbackFeature(VanillaFallbackFeature.Config.CODEC));

    // lake floors (see worldgen/features/lake)
    public static final DeferredHolder<Feature<?>, LakeFloorFeature> LAKE_FLOOR =
        FEATURES.register("lake_floor", () -> new LakeFloorFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, LakeLifeFeature> LAKE_LIFE =
        FEATURES.register("lake_life", () -> new LakeLifeFeature(NoneFeatureConfiguration.CODEC));

    private TerrahaulFeatures() {
    }

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
