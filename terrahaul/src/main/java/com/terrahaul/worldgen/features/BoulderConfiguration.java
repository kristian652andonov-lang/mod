package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Configuration of {@code terrahaul:boulder}.
 *
 * @param radius   overall boulder radius in blocks
 * @param material fallback material provider; in Terrahaul worlds the local geology is used instead
 *                 unless {@code geology} is false
 * @param geology  use the geological province of the terrain model for the material
 * @param moss     chance of moss on exposed top blocks (scaled by humidity in Terrahaul worlds)
 * @param bury     fraction of the radius that is sunk into the ground
 */
public record BoulderConfiguration(FloatProvider radius, BlockStateProvider material, boolean geology, float moss, float bury)
    implements FeatureConfiguration {
    public static final Codec<BoulderConfiguration> CODEC = RecordCodecBuilder.create(i -> i.group(
        FloatProvider.codec(0.5f, 8.0f).fieldOf("radius").forGetter(BoulderConfiguration::radius),
        BlockStateProvider.CODEC.fieldOf("material").forGetter(BoulderConfiguration::material),
        Codec.BOOL.optionalFieldOf("geology", true).forGetter(BoulderConfiguration::geology),
        Codec.floatRange(0, 1).optionalFieldOf("moss", 0.0f).forGetter(BoulderConfiguration::moss),
        Codec.floatRange(0, 1).optionalFieldOf("bury", 0.35f).forGetter(BoulderConfiguration::bury)
    ).apply(i, BoulderConfiguration::new));
}
