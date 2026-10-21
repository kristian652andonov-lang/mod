package com.terrahaul.worldgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrahaul.registry.TerrahaulPlacements;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.surface.MountainFlora;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code terrahaul:mountain_flora}: keeps positions in one vegetation zone of the
 * mountain flanks ({@link MountainFlora}), passing each with a probability equal to the zone's
 * weight there, so trees, krummholz, shrubs and flowers thin out across the same soft boundaries
 * along which the surface rule lays their soil: forests thin out into the tree line, krummholz
 * fades into the alpine meadows.
 * <p>
 * Every test starts with a cheap look at the cached terrain column in the middle of the
 * position's chunk; zone {@code any} is only that test, used as the first modifier of a mountain
 * feature so that it costs next to nothing away from mountains. Outside Terrahaul worlds nothing
 * passes.
 * <p>
 * With {@code max_slope} set, a position also needs ground no steeper than that many blocks of
 * rise over three blocks in each of the four directions (5 is about 59 degrees), measured on the
 * generated ground, so trees keep off cliff faces even where the smoothed slope is gentler.
 *
 * @param zone      a {@link MountainFlora.Kind}
 * @param threshold positions where the zone's weight is at or below this never pass
 * @param maxSlope  steepest rise over three blocks (0: not checked)
 */
public final class MountainFloraFilter extends PlacementFilter {
    private static final Codec<MountainFlora.Kind> KIND_CODEC = Codec.STRING.comapFlatMap(name -> {
        MountainFlora.Kind kind = MountainFlora.Kind.byName(name);
        return kind != null ? DataResult.success(kind) : DataResult.error(() -> "Unknown mountain flora zone: " + name);
    }, MountainFlora.Kind::serializedName);

    public static final MapCodec<MountainFloraFilter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        KIND_CODEC.fieldOf("zone").forGetter(f -> f.zone),
        Codec.floatRange(0, 1).optionalFieldOf("threshold", 0f).forGetter(f -> f.threshold),
        Codec.intRange(0, 32).optionalFieldOf("max_slope", 0).forGetter(f -> f.maxSlope)
    ).apply(i, MountainFloraFilter::new));

    private final MountainFlora.Kind zone;
    private final float threshold;
    private final int maxSlope;

    public MountainFloraFilter(MountainFlora.Kind zone, float threshold, int maxSlope) {
        this.zone = zone;
        this.threshold = threshold;
        this.maxSlope = maxSlope;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        TerrainModel model = TerrahaulGeneration.model(context.getLevel());
        if (model == null || !MountainFlora.near(model, (pos.getX() & ~15) + 8, (pos.getZ() & ~15) + 8)) {
            return false;
        }
        if (zone == MountainFlora.Kind.ANY) {
            return true;
        }
        double weight = MountainFlora.at(model, pos.getX(), pos.getZ(), pos.getY() - 1).weight(zone);
        if (weight <= threshold || weight < 1.0 && random.nextDouble() >= weight) {
            return false;
        }
        return maxSlope <= 0 || steepestRise(context, pos, 3) <= maxSlope;
    }

    /**
     * The largest height difference between the ground at {@code pos} and the ground
     * {@code distance} blocks away in the four axis directions.
     */
    static int steepestRise(PlacementContext context, BlockPos pos, int distance) {
        int h = context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, pos.getX(), pos.getZ());
        int max = 0;
        for (int[] d : new int[][]{{distance, 0}, {-distance, 0}, {0, distance}, {0, -distance}}) {
            max = Math.max(max, Math.abs(context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, pos.getX() + d[0], pos.getZ() + d[1]) - h));
        }
        return max;
    }

    @Override
    public PlacementModifierType<?> type() {
        return TerrahaulPlacements.MOUNTAIN_FLORA.get();
    }
}
