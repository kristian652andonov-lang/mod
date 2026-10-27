package com.fantasyweapons.world.gen;

import com.fantasyweapons.registry.ModPlacements;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * The height of a legendary ore's vein, for the ores that belong under particular ground. In a world of the vanilla height
 * (-64 to 320) it is exactly {@link net.minecraft.world.level.levelgen.placement.HeightRangePlacement}: the same height
 * from the same random draws, so those worlds generate just as they always have. A taller or deeper world (Terrahaul's
 * reaches from -512 to 2032, its mountains rising far above vanilla's) no longer has the ground a vanilla height was
 * chosen for: under a meadow or a frozen peak a thousand blocks high, Y 70 to 200 lies deep down in other biomes. There
 * the height is moved with the ground: by how far the ground about the vein's column lies above (or below) {@code ground},
 * the height of the ground of its biomes in a vanilla world. So the vein lies as deep under its biome's ground as it does
 * in vanilla, the same share of its tries falling above the ground or into another biome below, and it is as rare.
 */
public final class FittedHeightPlacement extends PlacementModifier {
    public static final MapCodec<FittedHeightPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            HeightProvider.CODEC.fieldOf("height").forGetter(p -> p.height),
            Codec.INT.fieldOf("ground").forGetter(p -> p.ground)).apply(i, FittedHeightPlacement::new));

    /** How far from a column (blocks) the columns around it are that its ground is taken with (within the chunks being decorated). */
    private static final int SPREAD = 8;

    private final HeightProvider height;
    private final int ground;

    public FittedHeightPlacement(HeightProvider height, int ground) {
        this.height = height;
        this.ground = ground;
    }

    /** Whether a world is built from {@code minY}, {@code height} high, as the vanilla overworld is (so are superflat and amplified ones). */
    public static boolean vanillaHeight(int minY, int height) {
        return minY == -64 && height == 384;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int y = height.sample(random, context);
        if (vanillaHeight(context.getMinBuildHeight(), context.getLevel().getHeight())) return Stream.of(pos.atY(y));
        int moved = y + ground(context, pos.getX(), pos.getZ()) - ground;
        return moved >= context.getMinGenY() && moved < context.getMinGenY() + context.getGenDepth() ? Stream.of(pos.atY(moved)) : Stream.empty();
    }

    /**
     * The height of the ground about a column: the middle one of the heights of the ground in it and in four columns
     * around it, so that what already stands on the ground there (an ice spike, a boulder) or a pit in it does not count,
     * and a slope does not move it.
     */
    public static int ground(PlacementContext context, int x, int z) {
        int[] h = {top(context, x, z), top(context, x + SPREAD, z), top(context, x - SPREAD, z), top(context, x, z + SPREAD), top(context, x, z - SPREAD)};
        Arrays.sort(h);
        return h[2];
    }

    private static int top(PlacementContext context, int x, int z) {
        // the height map gives the first free block above the ground
        return context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModPlacements.FITTED_HEIGHT.get();
    }
}
