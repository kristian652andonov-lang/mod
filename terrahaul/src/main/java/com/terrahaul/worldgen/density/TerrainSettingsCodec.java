package com.terrahaul.worldgen.density;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrahaul.worldgen.terrain.TerrainSettings;

/** Codec for {@link TerrainSettings}; every field is optional and defaults to the built-in value. */
public final class TerrainSettingsCodec {
    private static final TerrainSettings D = TerrainSettings.DEFAULT;

    public static final MapCodec<TerrainSettings> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.doubleRange(0.25, 4.0).optionalFieldOf("horizontal_scale", D.horizontalScale()).forGetter(TerrainSettings::horizontalScale),
        Codec.doubleRange(500.0, 100000.0).optionalFieldOf("continent_scale", D.continentScale()).forGetter(TerrainSettings::continentScale),
        Codec.doubleRange(200.0, 50000.0).optionalFieldOf("erosion_scale", D.erosionScale()).forGetter(TerrainSettings::erosionScale),
        Codec.doubleRange(100.0, 20000.0).optionalFieldOf("mountain_scale", D.mountainScale()).forGetter(TerrainSettings::mountainScale),
        Codec.doubleRange(0.0, 1900.0).optionalFieldOf("mountain_height", D.mountainHeight()).forGetter(TerrainSettings::mountainHeight),
        Codec.doubleRange(0.0, 1900.0).optionalFieldOf("colossal_height", D.colossalHeight()).forGetter(TerrainSettings::colossalHeight),
        Codec.doubleRange(128.0, 2020.0).optionalFieldOf("max_terrain_height", D.maxTerrainHeight()).forGetter(TerrainSettings::maxTerrainHeight),
        Codec.doubleRange(200.0, 50000.0).optionalFieldOf("river_scale", D.riverScale()).forGetter(TerrainSettings::riverScale),
        Codec.doubleRange(0.0, 8.0).optionalFieldOf("river_width", D.riverWidth()).forGetter(TerrainSettings::riverWidth),
        Codec.doubleRange(0.0, 8.0).optionalFieldOf("lake_frequency", D.lakeFrequency()).forGetter(TerrainSettings::lakeFrequency),
        Codec.doubleRange(0.0, 8.0).optionalFieldOf("canyon_frequency", D.canyonFrequency()).forGetter(TerrainSettings::canyonFrequency),
        Codec.doubleRange(0.0, 8.0).optionalFieldOf("volcano_frequency", D.volcanoFrequency()).forGetter(TerrainSettings::volcanoFrequency),
        Codec.doubleRange(200.0, 50000.0).optionalFieldOf("climate_scale", D.climateScale()).forGetter(TerrainSettings::climateScale),
        Codec.doubleRange(0.0, 4.0).optionalFieldOf("altitude_cooling", D.altitudeCooling()).forGetter(TerrainSettings::altitudeCooling),
        Codec.intRange(-2032, 2031).optionalFieldOf("sea_level", D.seaLevel()).forGetter(TerrainSettings::seaLevel)
    ).apply(i, TerrainSettings::new));

    private TerrainSettingsCodec() {
    }
}
