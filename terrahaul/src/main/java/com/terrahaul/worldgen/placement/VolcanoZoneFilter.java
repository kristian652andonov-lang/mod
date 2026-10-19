package com.terrahaul.worldgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrahaul.registry.TerrahaulPlacements;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.VolcanoZones;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code terrahaul:volcano_zone}: keeps positions in one ecological zone of a
 * volcano ({@link VolcanoZones}), passing each with a probability equal to the zone's weight
 * there. Vegetation therefore thins out across the same soft boundaries along which the surface
 * rule changes the ground: living trees fade out into a burn scar while burned snags fade in, and
 * young trees give way to bare ash below the cone.
 * <p>
 * Every test starts with a cheap look at the cached terrain column at the centre of the
 * position's chunk. Zone {@code any} is only that test: used as the first modifier of a volcano
 * feature it skips everything else in chunks away from volcanoes, so those features cost next to
 * nothing elsewhere. Zone {@code living} is the opposite, for the biomes' own trees: it passes
 * everywhere away from volcanoes (and in every world that is not a Terrahaul world), and on a
 * volcano keeps trees out of burn scars, lava flows and the bare cone. Every other zone passes
 * nothing outside Terrahaul worlds.
 *
 * @param zone      {@code any}, {@code cone}, {@code pioneer}, {@code forest}, {@code burn} or
 *                  {@code living}
 * @param threshold positions where the zone's weight is at or below this never pass
 */
public final class VolcanoZoneFilter extends PlacementFilter {
    private static final Codec<VolcanoZones.Kind> KIND_CODEC = Codec.STRING.comapFlatMap(name -> {
        VolcanoZones.Kind kind = VolcanoZones.Kind.byName(name);
        return kind != null ? DataResult.success(kind) : DataResult.error(() -> "Unknown volcano zone: " + name);
    }, VolcanoZones.Kind::serializedName);

    public static final MapCodec<VolcanoZoneFilter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        KIND_CODEC.fieldOf("zone").forGetter(f -> f.zone),
        Codec.floatRange(0, 1).optionalFieldOf("threshold", 0f).forGetter(f -> f.threshold)
    ).apply(i, VolcanoZoneFilter::new));

    private final VolcanoZones.Kind zone;
    private final float threshold;

    public VolcanoZoneFilter(VolcanoZones.Kind zone, float threshold) {
        this.zone = zone;
        this.threshold = threshold;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        TerrainModel model = TerrahaulGeneration.model(context.getLevel());
        if (model == null || !VolcanoZones.near(model, (pos.getX() & ~15) + 8, (pos.getZ() & ~15) + 8)) {
            // away from volcanoes, and outside Terrahaul worlds, only the biomes' own trees grow
            return zone == VolcanoZones.Kind.LIVING;
        }
        if (zone == VolcanoZones.Kind.ANY) {
            return true;
        }
        double weight = VolcanoZones.at(model, pos.getX(), pos.getZ()).weight(zone);
        return weight > threshold && (weight >= 1.0 || random.nextDouble() < weight);
    }

    @Override
    public PlacementModifierType<?> type() {
        return TerrahaulPlacements.VOLCANO_ZONE.get();
    }
}
