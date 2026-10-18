package com.terrahaul.worldgen.density;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density function type {@code terrahaul:column_bounded}: a transparent wrapper around the final
 * density of a Terrahaul world. It returns its argument unchanged, but tells
 * {@code NoiseBasedChunkGeneratorMixin} which terrain model produced the world so that the noise
 * fill can stop above the highest possible solid block of a chunk instead of interpolating
 * 2,500 blocks of air in every column. {@code margin} is the extra height (in blocks) above the
 * 2D terrain surface that 3D detail and structure terrain adaptation may still fill.
 */
public record ColumnBoundedDensity(DensityFunction argument, DensityFunction terrain, int margin) implements DensityFunction {
    public static final MapCodec<ColumnBoundedDensity> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(ColumnBoundedDensity::argument),
        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("terrain").forGetter(ColumnBoundedDensity::terrain),
        Codec.intRange(0, 512).optionalFieldOf("margin", 48).forGetter(ColumnBoundedDensity::margin)
    ).apply(i, ColumnBoundedDensity::new));
    public static final KeyDispatchDataCodec<ColumnBoundedDensity> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    public TerrainModel model() {
        DensityFunction t = TerrainModels.unwrap(terrain);
        return t instanceof TerrainDensityFunction terrainFunction ? terrainFunction.model() : null;
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        return argument.compute(context);
    }

    @Override
    public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
        argument.fillArray(array, contextProvider);
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new ColumnBoundedDensity(argument.mapAll(visitor), terrain.mapAll(visitor), margin));
    }

    @Override
    public double minValue() {
        return argument.minValue();
    }

    @Override
    public double maxValue() {
        return argument.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
