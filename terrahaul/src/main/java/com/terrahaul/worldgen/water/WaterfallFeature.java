package com.terrahaul.worldgen.water;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

/**
 * Waterfalls at the deterministic sites of {@link TerrainModel#waterfallSite(int, int)}.
 * <p>
 * Each waterfall gets a spring-fed pool cut into the cliff top, a lip, a pre-placed column of
 * falling water down the face and a plunge pool at the foot. Pre-placing the falling column keeps
 * even very tall waterfalls stable and visible immediately, instead of relying on hundreds of
 * fluid ticks; only the bottom block is ticked so the water spreads naturally around the pool.
 */
public final class WaterfallFeature extends Feature<NoneFeatureConfiguration> {
    public WaterfallFeature(Codec<NoneFeatureConfiguration> codec) {
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
        int cell = TerrainModel.WATERFALL_CELL;
        int minCellX = Math.floorDiv(chunk.getMinBlockX(), cell);
        int maxCellX = Math.floorDiv(chunk.getMaxBlockX(), cell);
        int minCellZ = Math.floorDiv(chunk.getMinBlockZ(), cell);
        int maxCellZ = Math.floorDiv(chunk.getMaxBlockZ(), cell);
        boolean placed = false;
        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                TerrainModel.WaterfallSite site = model.waterfallSite(cx, cz);
                if (site != null && site.x >= chunk.getMinBlockX() && site.x <= chunk.getMaxBlockX()
                    && site.z >= chunk.getMinBlockZ() && site.z <= chunk.getMaxBlockZ()) {
                    placed |= build(level, site);
                }
            }
        }
        return placed;
    }

    private boolean build(WorldGenLevel level, TerrainModel.WaterfallSite site) {
        int dx = site.dirX;
        int dz = site.dirZ;
        int x = site.x;
        int z = site.z;
        int topY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
        // walk towards the drop until the ground falls away: the last high column is the lip
        int lipX = x;
        int lipZ = z;
        int fallX = 0;
        int fallZ = 0;
        int fallGround = 0;
        boolean found = false;
        for (int step = 1; step <= 10; step++) {
            int px = x + dx * step;
            int pz = z + dz * step;
            int g = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, px, pz) - 1;
            if (g < topY - 5) {
                fallX = px;
                fallZ = pz;
                fallGround = g;
                found = true;
                break;
            }
            if (g > topY + 2) {
                return false;
            }
            lipX = px;
            lipZ = pz;
            topY = Math.min(topY, g);
        }
        if (!found || topY - fallGround < 10) {
            return false;
        }
        // perpendicular axis for wide waterfalls
        int px = -dz;
        int pz = dx;
        int half = (site.width - 1) / 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState source = Blocks.WATER.defaultBlockState();
        BlockState falling = Fluids.FLOWING_WATER.getFlowing(8, true).createLegacyBlock();
        for (int w = -half; w <= site.width - 1 - half; w++) {
            int ox = px * w;
            int oz = pz * w;
            // spring pool behind the lip, carved one or two blocks into the cliff top
            for (int back = 0; back <= 3; back++) {
                int bx = lipX - dx * back + ox;
                int bz = lipZ - dz * back + oz;
                int g = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, bx, bz) - 1;
                if (Math.abs(g - topY) > 3) {
                    break;
                }
                level.setBlock(pos.set(bx, topY - 1, bz), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
                level.setBlock(pos.set(bx, topY, bz), source, 2);
                for (int y = topY + 1; y <= g + 1; y++) {
                    level.setBlock(pos.set(bx, y, bz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            // falling column down the face
            int fx = fallX + ox;
            int fz = fallZ + oz;
            int bottom = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, fx, fz) - 1;
            for (int y = topY; y > bottom; y--) {
                pos.set(fx, y, fz);
                BlockState existing = level.getBlockState(pos);
                if (!existing.isAir() && !existing.is(BlockTags.REPLACEABLE) && existing.getFluidState().isEmpty()) {
                    break;
                }
                level.setBlock(pos, y == topY ? Fluids.FLOWING_WATER.getFlowing(7, false).createLegacyBlock() : falling, 2);
            }
            // plunge pool
            for (int ax = -2; ax <= 2; ax++) {
                for (int az = -2; az <= 2; az++) {
                    if (ax * ax + az * az > 5) {
                        continue;
                    }
                    int qx = fx + ax + dx;
                    int qz = fz + az + dz;
                    int g = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, qx, qz) - 1;
                    if (Math.abs(g - bottom) > 2) {
                        continue;
                    }
                    int depth = ax * ax + az * az <= 2 ? 2 : 1;
                    for (int d = 0; d < depth; d++) {
                        level.setBlock(pos.set(qx, g - d, qz), source, 2);
                    }
                    level.setBlock(pos.set(qx, g - depth, qz), Blocks.GRAVEL.defaultBlockState(), 2);
                }
            }
            BlockPos base = new BlockPos(fx, bottom + 1, fz);
            level.scheduleTick(base, Fluids.WATER, 1);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                level.scheduleTick(base.relative(d), Fluids.WATER, 1);
            }
        }
        return true;
    }
}
