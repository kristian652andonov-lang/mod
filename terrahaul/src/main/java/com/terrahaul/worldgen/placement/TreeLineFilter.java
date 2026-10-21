package com.terrahaul.worldgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrahaul.registry.TerrahaulPlacements;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.surface.MountainFlora;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.trees.TreeLine;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code terrahaul:tree_line}: thins vegetation out towards the climate-dependent
 * tree line (see {@link TreeLine}) and keeps it off slopes steeper than {@code max_slope} blocks of
 * height difference across three blocks, so cliffs and scree are not covered in trees. On the
 * flanks of Terrahaul's mountains, where the surface rule lays soil on ledges of steep faces too,
 * the limit is at most {@value #MOUNTAIN_SLOPE} (about 59 degrees), as for the mountain forests
 * (see {@link MountainFloraFilter}).
 */
public final class TreeLineFilter extends PlacementFilter {
    public static final MapCodec<TreeLineFilter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.intRange(0, 400).optionalFieldOf("fade", 80).forGetter(f -> f.fade),
        Codec.intRange(1, 64).optionalFieldOf("max_slope", 7).forGetter(f -> f.maxSlope),
        Codec.intRange(-200, 600).optionalFieldOf("offset", 0).forGetter(f -> f.offset)
    ).apply(i, TreeLineFilter::new));

    /** Steepest rise over three blocks for trees on mountain flanks. */
    public static final int MOUNTAIN_SLOPE = 5;

    private final int fade;
    private final int maxSlope;
    private final int offset;

    public TreeLineFilter(int fade, int maxSlope, int offset) {
        this.fade = fade;
        this.maxSlope = maxSlope;
        this.offset = offset;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        WorldGenLevel level = context.getLevel();
        int line = TreeLine.treelineY(level, pos) + offset;
        int y = pos.getY();
        if (y > line) {
            return false;
        }
        if (y > line - fade && random.nextInt(fade) < y - (line - fade)) {
            return false;
        }
        int limit = maxSlope;
        if (limit > MOUNTAIN_SLOPE) {
            TerrainModel model = TerrahaulGeneration.model(level);
            if (model != null && MountainFlora.near(model, pos.getX(), pos.getZ())
                && MountainFlora.at(model, pos.getX(), pos.getZ(), y - 1).share > 0.3) {
                limit = MOUNTAIN_SLOPE;
            }
        }
        return MountainFloraFilter.steepestRise(context, pos, 3) <= limit;
    }

    @Override
    public PlacementModifierType<?> type() {
        return TerrahaulPlacements.TREE_LINE.get();
    }
}
