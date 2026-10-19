package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

/**
 * Hydrothermal vents: chimneys of basalt and blackstone with glowing magma, rising from the floors
 * of deep-sea trenches and, more rarely, from the crests of mid-ocean ridges. Each sits on a low
 * mound of blackstone and basalt, is 3-14 blocks tall and is only built in open water. The magma
 * is banded inside the chimney, never under open water, because magma placed by a feature turns
 * the water above it into a bubble column reaching all the way to the surface. The feature needs
 * the terrain model and does nothing outside Terrahaul worlds.
 */
public final class OceanVentFeature extends Feature<NoneFeatureConfiguration> {
    private static final BlockState BASALT = Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState SMOOTH_BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();

    public OceanVentFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        RandomSource random = new LegacyRandomSource(level.getSeed() ^ chunk.toLong() * 0x2545F4914F6CDD1DL);
        ColumnSample s = new ColumnSample();
        boolean placed = false;
        for (int i = 0; i < 4; i++) {
            int x = chunk.getMinBlockX() + 2 + random.nextInt(12);
            int z = chunk.getMinBlockZ() + 2 + random.nextInt(12);
            model.sampleInto(x, z, s);
            boolean trench = s.trench > 0.75 && random.nextFloat() < 0.55;
            boolean ridge = !trench && s.oceanRidge > 0.5 && s.height < model.seaLevel() - 60 && random.nextFloat() < 0.1;
            if (trench || ridge) {
                placed |= chimney(level, x, z, trench ? 5 + random.nextInt(10) : 3 + random.nextInt(5), random);
            }
        }
        return placed;
    }

    private static boolean isWater(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).is(Fluids.WATER);
    }

    private boolean chimney(WorldGenLevel level, int x, int z, int height, RandomSource random) {
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        BlockPos base = new BlockPos(x, floor, z);
        // the whole chimney and two blocks above it must be open water
        for (int dy = 0; dy < height + 2; dy++) {
            if (!isWater(level, base.above(dy))) {
                return false;
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // mound around the foot: a magma core under the chimney (covered by it), a blackstone ring
        // and a rim of smooth basalt. Magma is never left with water right above it: placed by a
        // feature it would turn the water above into a bubble column pulling down to the floor.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > 2.3) {
                    continue;
                }
                int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + dx, z + dz);
                int rise = r < 1.0 ? 1 : r < 1.8 ? 1 : random.nextInt(2);
                for (int dy = 0; dy < rise; dy++) {
                    pos.set(x + dx, ground + dy, z + dz);
                    if (isWater(level, pos)) {
                        setBlock(level, pos, r < 1.8 ? BLACKSTONE : SMOOTH_BASALT);
                    }
                }
            }
        }
        // chimney: a basalt core with bands of glowing magma inside it, always capped by basalt,
        // a blackstone collar around its lower half and a blackstone crust on top
        int phase = random.nextInt(4);
        for (int dy = 0; dy < height; dy++) {
            pos.set(x, floor + dy, z);
            boolean top = dy == height - 1;
            boolean band = (dy + phase) % 4 == 0 && dy < height - 2;
            setBlock(level, pos, top ? BLACKSTONE : band ? MAGMA : BASALT);
            if (dy < height / 2) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos side = pos.relative(dir);
                    if (isWater(level, side)) {
                        setBlock(level, side, BLACKSTONE);
                    }
                }
            }
        }
        return true;
    }
}
