package com.terrahaul.worldgen.terrain;

/**
 * Tunable parameters of the Terrahaul terrain model. The values are read from the
 * {@code terrahaul:terrain} density function JSON (see {@code TerrainSettingsCodec}) so pack makers
 * can rescale the world without code changes. All lengths are in blocks.
 *
 * @param horizontalScale  global multiplier applied to every horizontal feature size
 * @param continentScale   typical size of continents and oceans
 * @param erosionScale     size of the regions that decide flat versus mountainous terrain
 * @param mountainScale    spacing of individual ridges and peaks inside mountain regions
 * @param mountainHeight   typical relief of an ordinary mountain range above its foothills
 * @param colossalHeight   additional relief of rare colossal massifs
 * @param maxTerrainHeight soft ceiling for the terrain surface
 * @param riverScale       spacing of the major river network
 * @param riverWidth       multiplier for river widths
 * @param lakeFrequency    multiplier for the number of lakes
 * @param canyonFrequency  multiplier for the area covered by canyons
 * @param volcanoFrequency multiplier for the number of volcanoes
 * @param climateScale     size of temperature and humidity zones
 * @param altitudeCooling  how strongly altitude cools the climate parameter used for biomes
 * @param seaLevel         must match the noise settings' sea level
 */
public record TerrainSettings(
    double horizontalScale,
    double continentScale,
    double erosionScale,
    double mountainScale,
    double mountainHeight,
    double colossalHeight,
    double maxTerrainHeight,
    double riverScale,
    double riverWidth,
    double lakeFrequency,
    double canyonFrequency,
    double volcanoFrequency,
    double climateScale,
    double altitudeCooling,
    int seaLevel
) {
    public static final TerrainSettings DEFAULT = new TerrainSettings(
        1.0, 15000.0, 3600.0, 1100.0, 640.0, 1450.0, 1960.0, 3400.0, 1.0, 1.0, 1.0, 1.0, 7600.0, 1.0, 63
    );
}
