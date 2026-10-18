package com.terrahaul.worldgen.trees;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Configuration of {@code terrahaul:procedural_tree}.
 *
 * @param species   tree species
 * @param sizes     weighted size classes; the actual size shrinks near the tree line
 * @param vines     chance per canopy edge block to grow hanging vines
 * @param cocoa     chance per suitable trunk block to carry a cocoa pod
 * @param beehive   chance for the tree to carry a bee nest with bees (vanilla gameplay)
 * @param dryLeaves fraction of leaves removed, for sparse, sun-scorched crowns
 */
public record ProceduralTreeConfiguration(TreeSpecies species, SimpleWeightedRandomList<TreeSize> sizes, float vines, float cocoa,
                                          float beehive, float dryLeaves) implements FeatureConfiguration {
    public static final Codec<ProceduralTreeConfiguration> CODEC = RecordCodecBuilder.create(i -> i.group(
        TreeSpecies.CODEC.fieldOf("species").forGetter(ProceduralTreeConfiguration::species),
        SimpleWeightedRandomList.wrappedCodec(TreeSize.CODEC).fieldOf("sizes").forGetter(ProceduralTreeConfiguration::sizes),
        Codec.floatRange(0, 1).optionalFieldOf("vines", 0.0f).forGetter(ProceduralTreeConfiguration::vines),
        Codec.floatRange(0, 1).optionalFieldOf("cocoa", 0.0f).forGetter(ProceduralTreeConfiguration::cocoa),
        Codec.floatRange(0, 1).optionalFieldOf("beehive", 0.0f).forGetter(ProceduralTreeConfiguration::beehive),
        Codec.floatRange(0, 0.9f).optionalFieldOf("dry_leaves", 0.0f).forGetter(ProceduralTreeConfiguration::dryLeaves)
    ).apply(i, ProceduralTreeConfiguration::new));
}
