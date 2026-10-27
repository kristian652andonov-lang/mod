package com.terrahaul.worldgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrahaul.registry.TerrahaulPlacements;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code terrahaul:buried}: passes a feature only where ground lies at least
 * {@code depth} blocks over the top of the box it fills, all over that box.
 * <p>
 * It is meant for vanilla's underground lava lakes. A lake fills a box of 16 by 16 blocks east and
 * south of its origin, its lava up to a block below the origin and its air up to 2 blocks above.
 * Vanilla's own test, {@code surface_relative_threshold_filter}, only asks that the origin lie 5
 * blocks under the ground of its own column; on a hillside or beside a river bank the box then
 * reaches under ground up to 15 blocks lower, and its air opens the lava onto the surface. This
 * filter reads the generation heightmap ({@code OCEAN_FLOOR_WG}) on a 4 by 4 grid over the box
 * (every 5 blocks, corners included) and asks that the lowest ground be at least {@code depth}
 * blocks over the box's top, so a lake keeps a roof of rock and soil wherever it lies. The box
 * reaches at most 15 blocks into the next chunks east and south, inside the area a feature may
 * read and write.
 *
 * @param size  width of the box east and south of the origin, in blocks
 * @param top   top of the box above the origin, in blocks
 * @param depth ground required over the top of the box, in blocks
 */
public final class BuriedFilter extends PlacementFilter {
    public static final MapCodec<BuriedFilter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.intRange(1, 32).optionalFieldOf("size", 16).forGetter(f -> f.size),
        Codec.intRange(0, 32).optionalFieldOf("top", 2).forGetter(f -> f.top),
        Codec.intRange(0, 64).fieldOf("depth").forGetter(f -> f.depth)
    ).apply(i, BuriedFilter::new));

    private final int size;
    private final int top;
    private final int depth;

    public BuriedFilter(int size, int top, int depth) {
        this.size = size;
        this.top = top;
        this.depth = depth;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        int need = pos.getY() + top + depth;
        int last = size - 1;
        for (int i = 0; i < 4; i++) {
            int x = pos.getX() + last * i / 3;
            for (int j = 0; j < 4; j++) {
                int z = pos.getZ() + last * j / 3;
                // the heightmap gives the Y above the topmost solid block
                if (context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1 < need) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public PlacementModifierType<?> type() {
        return TerrahaulPlacements.BURIED.get();
    }
}
