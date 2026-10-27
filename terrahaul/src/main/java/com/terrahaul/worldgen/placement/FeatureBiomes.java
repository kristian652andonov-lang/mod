package com.terrahaul.worldgen.placement;

import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.worldgen.TerrahaulGeneration;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.TerrainProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.Mth;
import net.minecraft.util.ToFloatFunction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Terrahaul's widening of the {@code minecraft:biome} placement filter for ores under ground that
 * stands taller than vanilla's (applied by {@code BiomeFilterMixin}).
 * <p>
 * <b>Why.</b> Minecraft's biomes are three-dimensional: the multi-noise biome source picks the
 * biome of a position from the column's climate and a depth parameter, about one unit per 128
 * blocks below the terrain. Terrahaul computes that depth from its own, much taller terrain, as
 * vanilla does from its own, so under a Terrahaul meadow at Y 330 or a peak at Y 1200 the biome at
 * Y 70..180 is that of a position hundreds of blocks underground: mostly the deep dark, or
 * dripstone and lush caves, where vanilla's terrain of the same climate (whose ground lies at
 * most at about Y 256) would still have the meadow or the peak. Ores bound to a biome at a fixed
 * height (vanilla's emeralds, other mods' mountain, desert or snow ores) then find their biome in
 * only a fraction of the volume they were made for.
 * <p>
 * <b>What.</b> In Terrahaul worlds, while {@code oreDepthFix} is on, an ore feature (any feature
 * configured with an {@link OreConfiguration}: {@code minecraft:ore}, {@code minecraft:scattered_ore}
 * and other mods' ores built on them) that the 3D biome at its position rejects is also accepted
 * if the position lies inside the ground, the ground of its column stands taller there than
 * vanilla's terrain of the same climate would, and the biome vanilla would have at that height
 * holds the ore. That biome is the one the biome source gives for the column's climate with the
 * depth vanilla's router computes at that height: its offset spline ({@link
 * TerrainProvider#overworldOffset}, on the continentalness, erosion and folded weirdness that
 * Terrahaul's model hands to the biome source) plus vanilla's height gradient. So an ore of a
 * mountain biome is found in the mountain's root as in a vanilla mountain, and an ore of a cave
 * biome where vanilla would have that cave biome at that height (the 3D biome itself still counts
 * too, so nothing that passed before is turned away). Features of other kinds (trees, plants,
 * cave decoration) are not affected, nor is anything above the ground or in other worlds.
 * <p>
 * Ores in the placed-feature tag {@code terrahaul:strict_biome} keep the 3D biome's answer. The
 * generated tag holds vanilla's ores that only some Overworld biomes have (emeralds, infested
 * stone, the badlands' extra gold, the clay of lush caves, the large copper veins of dripstone
 * caves and the copper ore those caves lack) and Terrahaul's mountain emerald layer: Terrahaul's
 * deep and high resource layers were tuned against the amounts of vanilla's ores, which the
 * widening would have changed (it tripled the emeralds of the test areas and added half again to
 * their copper and gold). Data packs can add other mods' ores to the tag.
 */
public final class FeatureBiomes {
    /**
     * Lookups of the vanilla-height biome and those that accepted the ore; the time spent in every
     * call past {@link #widens} (for AutoTest).
     */
    public static final LongAdder WIDENING_CALLS = new LongAdder();
    public static final LongAdder WIDENING_ACCEPTED = new LongAdder();
    public static final LongAdder WIDENING_NANOS = new LongAdder();

    /** Ores that keep the 3D biome's answer (see the class comment). */
    public static final TagKey<PlacedFeature> STRICT_BIOME = TagKey.create(Registries.PLACED_FEATURE,
        ResourceLocation.fromNamespaceAndPath("terrahaul", "strict_biome"));

    /** Development switch (-Dterrahaul.debug.noOreBiomeWidening=true) to measure worlds without the widening. */
    private static final boolean DISABLED = Boolean.getBoolean("terrahaul.debug.noOreBiomeWidening");

    /** Vanilla's offset spline on {continentalness, erosion, folded weirdness}, without vanilla's constant. */
    private static final CubicSpline<float[], Coordinate> VANILLA_OFFSET = TerrainProvider.overworldOffset(
        new Coordinate(0), new Coordinate(1), new Coordinate(2), false);
    /** The constant vanilla's router adds to the spline ({@code NoiseRouterData.GLOBAL_OFFSET}). */
    private static final double GLOBAL_OFFSET = -0.50375;

    private static final int CACHE = 64;
    private static final ThreadLocal<Column[]> COLUMNS = ThreadLocal.withInitial(() -> {
        Column[] columns = new Column[CACHE];
        for (int i = 0; i < CACHE; i++) {
            columns[i] = new Column();
        }
        return columns;
    });

    private FeatureBiomes() {
    }

    /** Whether the widening can apply to this feature in this generator's world at all. */
    public static boolean widens(PlacedFeature feature, ChunkGenerator generator) {
        return !DISABLED && feature.feature().value().config() instanceof OreConfiguration && TerrahaulConfig.oreDepthFix()
            && generator.getBiomeSource() instanceof MultiNoiseBiomeSource && TerrahaulGeneration.isTerrahaul(generator);
    }

    /** Terrahaul's widening of the filter (see the class comment); false wherever it does not apply. */
    public static boolean vanillaHeightBiome(LevelReader level, ChunkGenerator generator, Climate.Sampler sampler, BlockPos pos,
                                             PlacedFeature feature, Heightmap.Types surface) {
        if (!widens(feature, generator)) {
            return false;
        }
        long t0 = System.nanoTime();
        // a position outside the chunks being generated (a feature that spreads its positions far)
        // is left to the 3D biome: its ground cannot be read
        if (!level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
            WIDENING_NANOS.add(System.nanoTime() - t0);
            return false;
        }
        MultiNoiseBiomeSource source = (MultiNoiseBiomeSource) generator.getBiomeSource();
        int top = level.getHeight(surface, pos.getX(), pos.getZ());
        if (pos.getY() >= top || level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).wrapAsHolder(feature).is(STRICT_BIOME)) {
            WIDENING_NANOS.add(System.nanoTime() - t0);
            return false;
        }
        int qx = QuartPos.fromBlock(pos.getX());
        int qy = QuartPos.fromBlock(pos.getY());
        int qz = QuartPos.fromBlock(pos.getZ());
        Column column = column(sampler, qx, qz);
        int y = QuartPos.toBlock(qy);
        // vanilla's depth router: offset + a gradient of 1.5 at Y -64 to -1.5 at Y 320
        double vanillaDepth = column.offset + Mth.clampedMap(y, -64.0, 320.0, 1.5, -1.5);
        boolean accepted = false;
        // only where the ground stands taller than vanilla's of this climate (Terrahaul's depth,
        // about (ground - y) / 128, is the larger one); elsewhere the 3D biome is what vanilla has
        if ((top - y) / 128.0 > vanillaDepth) {
            WIDENING_CALLS.increment();
            Holder<Biome> biome = source.getNoiseBiome(new Climate.TargetPoint(column.temperature, column.humidity, column.continentalness,
                column.erosion, Climate.quantizeCoord((float) vanillaDepth), column.weirdness));
            accepted = generator.getBiomeGenerationSettings(biome).hasFeature(feature);
            if (accepted) {
                WIDENING_ACCEPTED.increment();
            }
        }
        WIDENING_NANOS.add(System.nanoTime() - t0);
        return accepted;
    }

    /** Vanilla's terrain offset for the climate of a quart column (vanilla's depth is this plus its height gradient). */
    public static double vanillaOffset(Climate.Sampler sampler, int qx, int qz) {
        return column(sampler, qx, qz).offset;
    }

    /** The climate of a quart column and vanilla's offset for it, cached per thread. */
    private static Column column(Climate.Sampler sampler, int qx, int qz) {
        Column c = COLUMNS.get()[((qx & 7) << 3) | (qz & 7)];
        if (c.sampler != sampler || c.qx != qx || c.qz != qz) {
            // the climate is two-dimensional (as in vanilla's router), so it is read at Y 0
            DensityFunction.SinglePointContext at = new DensityFunction.SinglePointContext(QuartPos.toBlock(qx), 0, QuartPos.toBlock(qz));
            float continentalness = (float) sampler.continentalness().compute(at);
            float erosion = (float) sampler.erosion().compute(at);
            float weirdness = (float) sampler.weirdness().compute(at);
            c.temperature = Climate.quantizeCoord((float) sampler.temperature().compute(at));
            c.humidity = Climate.quantizeCoord((float) sampler.humidity().compute(at));
            c.continentalness = Climate.quantizeCoord(continentalness);
            c.erosion = Climate.quantizeCoord(erosion);
            c.weirdness = Climate.quantizeCoord(weirdness);
            c.offset = GLOBAL_OFFSET + VANILLA_OFFSET.apply(new float[]{continentalness, erosion, NoiseRouterData.peaksAndValleys(weirdness)});
            c.sampler = sampler;
            c.qx = qx;
            c.qz = qz;
        }
        return c;
    }

    private static final class Column {
        Climate.Sampler sampler;
        int qx;
        int qz;
        long temperature;
        long humidity;
        long continentalness;
        long erosion;
        long weirdness;
        double offset;
    }

    /** One climate value of the point handed to the offset spline. */
    private record Coordinate(int index) implements ToFloatFunction<float[]> {
        @Override
        public float apply(float[] point) {
            return point[index];
        }

        @Override
        public float minValue() {
            return -2.0F;
        }

        @Override
        public float maxValue() {
            return 2.0F;
        }
    }
}
