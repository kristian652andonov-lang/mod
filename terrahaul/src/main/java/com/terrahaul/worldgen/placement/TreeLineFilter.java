package com.terrahaul.worldgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrahaul.registry.TerrahaulPlacements;
import com.terrahaul.worldgen.trees.TreeLine;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code terrahaul:tree_line}: thins vegetation out towards the climate-dependent
 * tree line (see {@link TreeLine}) and keeps it off slopes steeper than {@code max_slope} blocks of
 * height difference across five blocks, so cliffs and scree are not covered in trees.
 */
public final class TreeLineFilter extends PlacementFilter {
    public static final MapCodec<TreeLineFilter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.intRange(0, 400).optionalFieldOf("fade", 80).forGetter(f -> f.fade),
        Codec.intRange(1, 64).optionalFieldOf("max_slope", 7).forGetter(f -> f.maxSlope),
        Codec.intRange(-200, 600).optionalFieldOf("offset", 0).forGetter(f -> f.offset)
    ).apply(i, TreeLineFilter::new));

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
        int h = context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, pos.getX(), pos.getZ());
        int max = 0;
        for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            max = Math.max(max, Math.abs(context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, pos.getX() + d[0], pos.getZ() + d[1]) - h));
        }
        return max <= maxSlope;
    }

    @Override
    public PlacementModifierType<?> type() {
        return TerrahaulPlacements.TREE_LINE.get();
    }
}
