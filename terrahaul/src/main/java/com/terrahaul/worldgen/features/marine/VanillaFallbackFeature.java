package com.terrahaul.worldgen.features.marine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrahaul.worldgen.TerrahaulGeneration;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Places a wrapped vanilla feature only outside Terrahaul worlds.
 * <p>
 * Biome modifiers apply to every world that uses a biome, so removing vanilla's coral, kelp and
 * seagrass from the ocean biomes (which Terrahaul's sea-floor features replace) would also strip
 * them from vanilla and other mods' worlds. The data pack therefore adds each removed feature back,
 * wrapped in this one, with vanilla's own placement: other worlds keep their vanilla oceans, and
 * Terrahaul worlds get only the new sea floors. This is the one deliberate exception to the rule
 * that Terrahaul features do nothing outside Terrahaul worlds.
 */
public final class VanillaFallbackFeature extends Feature<VanillaFallbackFeature.Config> {
    public VanillaFallbackFeature(Codec<Config> codec) {
        super(codec);
    }

    public record Config(Holder<ConfiguredFeature<?, ?>> feature) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
            ConfiguredFeature.CODEC.fieldOf("feature").forGetter(Config::feature)
        ).apply(i, Config::new));

        @Override
        public Stream<ConfiguredFeature<?, ?>> getFeatures() {
            return feature.value().getFeatures();
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        if (TerrahaulGeneration.model(context.level()) != null) {
            return false;
        }
        return context.config().feature().value().place(context.level(), context.chunkGenerator(), context.random(), context.origin());
    }
}
