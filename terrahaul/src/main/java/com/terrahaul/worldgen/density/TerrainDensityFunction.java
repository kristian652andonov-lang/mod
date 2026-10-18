package com.terrahaul.worldgen.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.util.Objects;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density function type {@code terrahaul:terrain}: exposes one output of the Terrahaul
 * {@link TerrainModel} to the vanilla noise router.
 * <p>
 * The terrain-shaped outputs ({@link Output#DENSITY}, {@link Output#PRELIMINARY}) return
 * {@code (surface - y) / 8}, a signed distance to the terrain surface with the same slope vanilla's
 * {@code sloped_cheese} has near the surface, so the vanilla cave expressions that consume it keep
 * their intended behaviour. The climate outputs are what the multi-noise biome source reads.
 */
public final class TerrainDensityFunction implements DensityFunction.SimpleFunction {
    /** Density change per block of vertical distance to the surface. */
    public static final double SLOPE = 0.125;

    public static final MapCodec<TerrainDensityFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Output.CODEC.fieldOf("output").forGetter(TerrainDensityFunction::output),
        DensityFunction.NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(f -> f.seedNoise),
        TerrainSettingsCodec.MAP_CODEC.forGetter(TerrainDensityFunction::settings)
    ).apply(i, (output, noise, settings) -> new TerrainDensityFunction(output, noise, settings, null)));
    public static final KeyDispatchDataCodec<TerrainDensityFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private final Output output;
    private final DensityFunction.NoiseHolder seedNoise;
    private final TerrainSettings settings;
    private final TerrainModel model;

    public TerrainDensityFunction(Output output, DensityFunction.NoiseHolder seedNoise, TerrainSettings settings, TerrainModel model) {
        this.output = output;
        this.seedNoise = seedNoise;
        this.settings = settings;
        this.model = model;
    }

    public Output output() {
        return output;
    }

    public TerrainSettings settings() {
        return settings;
    }

    /** The seeded model, or {@code null} while the router has not been bound to a world seed. */
    public TerrainModel model() {
        return model;
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        if (model == null) {
            return output == Output.DENSITY || output == Output.PRELIMINARY ? (settings.seaLevel() - context.blockY()) * SLOPE : 0.0;
        }
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        ColumnSample s = model.sample(x, z);
        return switch (output) {
            case DENSITY -> TerrainDetail.density(model, s, x, y, z);
            case PRELIMINARY -> (s.height - y) * SLOPE;
            case CONTINENTALNESS -> s.continentalness;
            case EROSION -> s.erosion;
            case WEIRDNESS -> s.weirdness;
            case TEMPERATURE -> s.temperature;
            case HUMIDITY -> s.humidity;
            case DEPTH -> (s.height - y) / 128.0;
            case SURFACE_HEIGHT -> s.height;
            case CAVE_ALLOWANCE -> caveAllowance(s, y);
        };
    }

    /**
     * 1 where caves may form, fading to 0 under lake and river beds and near the sea floor, so cave
     * systems do not drain water bodies into the ground.
     */
    private double caveAllowance(ColumnSample s, int y) {
        double depthBelow = s.height - y;
        double guard = 1.0;
        if (s.lake > 0.0 || s.river > 0.0 || s.waterLevel != ColumnSample.NO_WATER) {
            guard = Math.min(1.0, Math.max(0.0, (depthBelow - 6.0) / 10.0));
        } else if (s.height < settings.seaLevel()) {
            guard = Math.min(1.0, Math.max(0.0, (depthBelow - 4.0) / 8.0));
        }
        return guard;
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        DensityFunction.NoiseHolder seeded = visitor.visitNoise(seedNoise);
        TerrainModel bound = model;
        if (seeded.noise() != null) {
            bound = TerrainModels.get(TerrainModels.seedFrom(seeded.noise()), settings);
        }
        return visitor.apply(new TerrainDensityFunction(output, seeded, settings, bound));
    }

    @Override
    public double minValue() {
        return switch (output) {
            case DENSITY, PRELIMINARY -> -400.0;
            case DEPTH -> -40.0;
            case SURFACE_HEIGHT -> -2032.0;
            case CAVE_ALLOWANCE -> 0.0;
            default -> -1.2;
        };
    }

    @Override
    public double maxValue() {
        return switch (output) {
            case DENSITY, PRELIMINARY -> 400.0;
            case DEPTH -> 40.0;
            case SURFACE_HEIGHT -> 2031.0;
            case CAVE_ALLOWANCE -> 1.0;
            default -> 1.2;
        };
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof TerrainDensityFunction other
            && output == other.output && settings.equals(other.settings) && model == other.model
            && Objects.equals(seedNoise.noiseData(), other.seedNoise.noiseData());
    }

    @Override
    public int hashCode() {
        return Objects.hash(output, settings, System.identityHashCode(model));
    }

    @Override
    public String toString() {
        return "terrahaul:terrain[" + output.getSerializedName() + "]";
    }

    public enum Output implements StringRepresentable {
        DENSITY("density"),
        PRELIMINARY("preliminary"),
        CONTINENTALNESS("continentalness"),
        EROSION("erosion"),
        WEIRDNESS("weirdness"),
        TEMPERATURE("temperature"),
        HUMIDITY("humidity"),
        DEPTH("depth"),
        SURFACE_HEIGHT("surface_height"),
        CAVE_ALLOWANCE("cave_allowance");

        public static final com.mojang.serialization.Codec<Output> CODEC = StringRepresentable.fromEnum(Output::values);

        private final String name;

        Output(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
