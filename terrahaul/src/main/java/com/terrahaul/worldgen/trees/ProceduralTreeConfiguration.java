package com.terrahaul.worldgen.trees;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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
 * @param sapling   whether the tree grows from a player's sapling: like a vanilla tree it then
 *                  replaces only air, leaves, water and plants (no stem through other trees' limbs, no
 *                  foot sunk into a slope, no root laid into the ground), so the blocks around a
 *                  sapling are never destroyed; false for world generation (see {@code ProceduralTreeFeature})
 */
public record ProceduralTreeConfiguration(TreeSpecies species, SimpleWeightedRandomList<TreeSize> sizes, float vines, float cocoa,
                                          float beehive, float dryLeaves, boolean sapling) implements FeatureConfiguration {
    /** Species by serialized name ({@link TreeSpecies} is plain Java and carries no codec itself). */
    public static final Codec<TreeSpecies> SPECIES_CODEC = Codec.STRING.comapFlatMap(name -> {
        TreeSpecies s = TreeSpecies.byName(name);
        return s != null ? DataResult.success(s) : DataResult.error(() -> "Unknown tree species: " + name);
    }, TreeSpecies::serializedName);

    public static final Codec<TreeSize> SIZE_CODEC = Codec.STRING.comapFlatMap(name -> {
        TreeSize s = TreeSize.byName(name);
        return s != null ? DataResult.success(s) : DataResult.error(() -> "Unknown tree size: " + name);
    }, TreeSize::serializedName);

    public static final Codec<ProceduralTreeConfiguration> CODEC = RecordCodecBuilder.create(i -> i.group(
        SPECIES_CODEC.fieldOf("species").forGetter(ProceduralTreeConfiguration::species),
        SimpleWeightedRandomList.wrappedCodec(SIZE_CODEC).fieldOf("sizes").forGetter(ProceduralTreeConfiguration::sizes),
        Codec.floatRange(0, 1).optionalFieldOf("vines", 0.0f).forGetter(ProceduralTreeConfiguration::vines),
        Codec.floatRange(0, 1).optionalFieldOf("cocoa", 0.0f).forGetter(ProceduralTreeConfiguration::cocoa),
        Codec.floatRange(0, 1).optionalFieldOf("beehive", 0.0f).forGetter(ProceduralTreeConfiguration::beehive),
        Codec.floatRange(0, 0.9f).optionalFieldOf("dry_leaves", 0.0f).forGetter(ProceduralTreeConfiguration::dryLeaves),
        Codec.BOOL.optionalFieldOf("sapling", false).forGetter(ProceduralTreeConfiguration::sapling)
    ).apply(i, ProceduralTreeConfiguration::new));
}
