package com.terrahaul.worldgen.features.marine;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Kelp forests and seagrass meadows on Terrahaul sea floors outside the coral reefs (which plant
 * their own seagrass), replacing vanilla's scattered kelp and seagrass. The plan comes from
 * {@link SeaLifeRaster}; this feature finds each column's real top (the floor, or a rock placed
 * on it earlier) and grows the plant there: kelp holds on to rock and cobble, seagrass roots in
 * sand and mud. Every plant goes into a water source above a sturdy block, so all of it survives.
 * Does nothing outside Terrahaul worlds.
 */
public final class SeaVegetationFeature extends Feature<NoneFeatureConfiguration> {
    public SeaVegetationFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model == null) {
            return false;
        }
        MarinePlanner planner = MarinePlanner.of(model);
        ChunkPos chunk = new ChunkPos(context.origin());
        if (!planner.mayHaveShelf(chunk.getMinBlockX(), chunk.getMinBlockZ())) {
            return false;
        }
        int sl = model.seaLevel();
        MarineColumn c = new MarineColumn();
        SeaLifeRaster.Plant plant = new SeaLifeRaster.Plant();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                double h = model.interpolatedHeight(x, z);
                if (h >= sl - 0.5 || h < sl - 44.0) {
                    continue;
                }
                planner.column(x, z, c);
                if (!c.sea || SeaLifeRaster.reefColumn(c)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                // climb over rocks placed on the floor earlier
                BlockState above = level.getBlockState(pos.set(x, top + 1, z));
                while (top < planner.surf && !MarineBlocks.waterSource(above) && above.isCollisionShapeFullBlock(level, pos)) {
                    top++;
                    above = level.getBlockState(pos.set(x, top + 1, z));
                }
                if (!MarineBlocks.waterSource(above)) {
                    continue;
                }
                BlockState ground = level.getBlockState(pos.set(x, top, z));
                int kind;
                if (ground.is(BlockTags.SAND) || ground.is(Blocks.CLAY) || ground.is(Blocks.MUD) || ground.is(BlockTags.DIRT)) {
                    kind = SeaLifeRaster.SOFT;
                } else if (ground.is(Blocks.GRAVEL)) {
                    kind = SeaLifeRaster.GRAVELLY;
                } else if (ground.isFaceSturdy(level, pos, Direction.UP) && !ground.is(Blocks.MAGMA_BLOCK)) {
                    kind = SeaLifeRaster.HARD;
                } else {
                    continue;
                }
                SeaLifeRaster.decide(planner, c, top, kind, plant);
                placed |= grow(level, pos, x, z, top, plant);
            }
        }
        return placed;
    }

    private static boolean grow(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, SeaLifeRaster.Plant plant) {
        switch (plant.kind) {
            case SeaLifeRaster.SEAGRASS -> {
                level.setBlock(pos.set(x, top + 1, z), Blocks.SEAGRASS.defaultBlockState(), 2);
                return true;
            }
            case SeaLifeRaster.TALL_SEAGRASS -> {
                if (!MarineBlocks.waterSource(level.getBlockState(pos.set(x, top + 2, z)))) {
                    level.setBlock(pos.set(x, top + 1, z), Blocks.SEAGRASS.defaultBlockState(), 2);
                    return true;
                }
                level.setBlock(pos.set(x, top + 1, z), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.LOWER), 2);
                level.setBlock(pos.set(x, top + 2, z), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER), 2);
                return true;
            }
            case SeaLifeRaster.KELP -> {
                int head = top;
                while (head < plant.head && MarineBlocks.waterSource(level.getBlockState(pos.set(x, head + 1, z)))) {
                    head++;
                }
                if (head <= top) {
                    return false;
                }
                for (int y = top + 1; y < head; y++) {
                    level.setBlock(pos.set(x, y, z), Blocks.KELP_PLANT.defaultBlockState(), 2);
                }
                int age = plant.mature && head == plant.head ? KelpBlock.MAX_AGE : plant.age;
                level.setBlock(pos.set(x, head, z), Blocks.KELP.defaultBlockState().setValue(KelpBlock.AGE, age), 2);
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
