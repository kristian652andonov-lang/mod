package com.terrahaul.worldgen.features.marine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrahaul.worldgen.TerrahaulGeneration;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
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
 * <p>
 * Vanilla icebergs go the same way, with one difference: they may also form in Terrahaul worlds,
 * but only where the sea under the whole iceberg is at most {@code terrahaul_max_depth} blocks
 * deep. Their keels reach about 25 blocks below the surface, which in vanilla's shallow oceans
 * comes close to the floor; over Terrahaul's deep oceans (floors down to Y -160) they would hang
 * as huge blobs a hundred blocks above the sea floor.
 */
public final class VanillaFallbackFeature extends Feature<VanillaFallbackFeature.Config> {
    public VanillaFallbackFeature(Codec<Config> codec) {
        super(codec);
    }

    /**
     * @param feature  the vanilla feature
     * @param maxDepth deepest sea (blocks below sea level, around the origin) over which the feature
     *                 is also placed in Terrahaul worlds, or -1 for never
     */
    public record Config(Holder<ConfiguredFeature<?, ?>> feature, int maxDepth) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
            ConfiguredFeature.CODEC.fieldOf("feature").forGetter(Config::feature),
            Codec.INT.optionalFieldOf("terrahaul_max_depth", -1).forGetter(Config::maxDepth)
        ).apply(i, Config::new));

        @Override
        public Stream<ConfiguredFeature<?, ?>> getFeatures() {
            return feature.value().getFeatures();
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        if (TerrahaulGeneration.model(context.level()) != null
            && !shallowAround(context.level(), context.origin(), context.chunkGenerator().getSeaLevel(), context.config().maxDepth())) {
            return false;
        }
        return context.config().feature().value().place(context.level(), context.chunkGenerator(), context.random(), context.origin());
    }

    /** Whether the sea floor at the origin and 16 blocks around it lies at most {@code maxDepth} below sea level. */
    private static boolean shallowAround(WorldGenLevel level, BlockPos origin, int seaLevel, int maxDepth) {
        if (maxDepth < 0) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI / 4.0;
            int x = origin.getX() + (i == 8 ? 0 : (int) Math.round(Math.cos(a) * 16.0));
            int z = origin.getZ() + (i == 8 ? 0 : (int) Math.round(Math.sin(a) * 16.0));
            if (seaLevel - level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) > maxDepth) {
                return false;
            }
        }
        return true;
    }
}
