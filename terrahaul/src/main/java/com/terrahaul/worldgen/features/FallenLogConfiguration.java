package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Configuration of {@code terrahaul:fallen_log}.
 *
 * @param log      the log block (any block with an {@code axis} property)
 * @param stripped stripped variant mixed into rotten logs
 * @param length   log length
 * @param rot      fraction of segments that are rotten (stripped and sunk into the ground)
 * @param moss     chance of a moss carpet on each segment
 * @param mushroom chance of a mushroom on each segment
 * @param stump    chance of a broken stump standing at one end
 */
public record FallenLogConfiguration(BlockState log, BlockState stripped, IntProvider length, float rot, float moss, float mushroom, float stump)
    implements FeatureConfiguration {
    public static final Codec<FallenLogConfiguration> CODEC = RecordCodecBuilder.create(i -> i.group(
        BlockState.CODEC.fieldOf("log").forGetter(FallenLogConfiguration::log),
        BlockState.CODEC.fieldOf("stripped").forGetter(FallenLogConfiguration::stripped),
        IntProvider.codec(2, 14).fieldOf("length").forGetter(FallenLogConfiguration::length),
        Codec.floatRange(0, 1).optionalFieldOf("rot", 0.2f).forGetter(FallenLogConfiguration::rot),
        Codec.floatRange(0, 1).optionalFieldOf("moss", 0.4f).forGetter(FallenLogConfiguration::moss),
        Codec.floatRange(0, 1).optionalFieldOf("mushroom", 0.1f).forGetter(FallenLogConfiguration::mushroom),
        Codec.floatRange(0, 1).optionalFieldOf("stump", 0.3f).forGetter(FallenLogConfiguration::stump)
    ).apply(i, FallenLogConfiguration::new));
}
