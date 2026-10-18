package com.terrahaul.worldgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.registry.TerrahaulPlacements;
import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement modifier {@code terrahaul:density_count}: repeats a feature a number of times that
 * depends on a configurable density multiplier and on a smooth, seeded noise field. The noise
 * creates groves, dense thickets and open clearings instead of a uniform scatter; where it falls
 * below {@code clearing} nothing is placed at all.
 *
 * @param count     base count per chunk (fractions are rounded stochastically)
 * @param density   which configuration multiplier applies ({@code forest}, {@code vegetation},
 *                  {@code ground_detail}, {@code rocks})
 * @param scale     size of the density patches in blocks
 * @param variation strength of the noise modulation (0 = uniform)
 * @param clearing  noise value below which the count drops to zero
 */
public final class DensityCountPlacement extends PlacementModifier {
    public static final MapCodec<DensityCountPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        FloatProvider.codec(0.0f, 256.0f).fieldOf("count").forGetter(DensityCountPlacement::count),
        Codec.STRING.optionalFieldOf("density", "forest").forGetter(DensityCountPlacement::density),
        Codec.floatRange(8, 10000).optionalFieldOf("scale", 300f).forGetter(DensityCountPlacement::scale),
        Codec.floatRange(0, 4).optionalFieldOf("variation", 0.6f).forGetter(DensityCountPlacement::variation),
        Codec.floatRange(-2, 2).optionalFieldOf("clearing", -2f).forGetter(DensityCountPlacement::clearing)
    ).apply(i, DensityCountPlacement::new));

    private static final Map<Long, SimplexNoise> NOISES = new ConcurrentHashMap<>();

    private final FloatProvider count;
    private final String density;
    private final float scale;
    private final float variation;
    private final float clearing;

    public DensityCountPlacement(FloatProvider count, String density, float scale, float variation, float clearing) {
        this.count = count;
        this.density = density;
        this.scale = scale;
        this.variation = variation;
        this.clearing = clearing;
    }

    public FloatProvider count() {
        return count;
    }

    public String density() {
        return density;
    }

    public float scale() {
        return scale;
    }

    public float variation() {
        return variation;
    }

    public float clearing() {
        return clearing;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        long seed = context.getLevel().getSeed();
        SimplexNoise noise = NOISES.computeIfAbsent(SeedMixer.derive(seed, density.hashCode()), SimplexNoise::new);
        if (NOISES.size() > 64) {
            NOISES.clear();
        }
        double n = 0.7 * noise.noise2(pos.getX() / (double) scale, pos.getZ() / (double) scale)
            + 0.3 * noise.noise2(pos.getX() / (scale * 0.35), pos.getZ() / (scale * 0.35));
        if (n < clearing) {
            return Stream.empty();
        }
        double factor = Math.max(0.0, 1.0 + variation * n) * TerrahaulConfig.density(density);
        double expected = count.sample(random) * factor;
        int k = (int) expected;
        if (random.nextDouble() < expected - k) {
            k++;
        }
        return IntStream.range(0, k).mapToObj(i -> pos);
    }

    @Override
    public PlacementModifierType<?> type() {
        return TerrahaulPlacements.DENSITY_COUNT.get();
    }
}
