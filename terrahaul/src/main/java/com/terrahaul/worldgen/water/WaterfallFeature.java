package com.terrahaul.worldgen.water;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
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
 * Each waterfall is a stream that rises from a spring in the high ground, runs a few blocks to the
 * lip of a cliff and falls as a curtain, 1-13 blocks wide, into a plunge pool at the foot. Wide
 * curtains are split into strands by ribs of mossy rock, the wet cliff behind the water is mossy,
 * and where the ground at the foot slopes away a dam of tumbled rock holds the pool. The falling
 * water is placed as a complete column, so even tall falls are visible immediately and stay
 * stable without fluid ticks.
 * <p>
 * The plan comes entirely from the site, and every chunk the waterfall covers builds the columns
 * it owns, so a waterfall may span several chunks.
 */
public final class WaterfallFeature extends Feature<NoneFeatureConfiguration> {
    /** Furthest a waterfall's blocks reach from its lip. */
    private static final int REACH = 32;

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
        boolean placed = false;
        for (int cx = Math.floorDiv(chunk.getMinBlockX() - REACH, cell); cx <= Math.floorDiv(chunk.getMaxBlockX() + REACH, cell); cx++) {
            for (int cz = Math.floorDiv(chunk.getMinBlockZ() - REACH, cell); cz <= Math.floorDiv(chunk.getMaxBlockZ() + REACH, cell); cz++) {
                TerrainModel.WaterfallSite site = model.waterfallSite(cx, cz);
                if (site != null && site.x + REACH >= chunk.getMinBlockX() && site.x - REACH <= chunk.getMaxBlockX()
                    && site.z + REACH >= chunk.getMinBlockZ() && site.z - REACH <= chunk.getMaxBlockZ()) {
                    placed |= build(level, site, chunk);
                }
            }
        }
        return placed;
    }

    private static boolean build(WorldGenLevel level, TerrainModel.WaterfallSite site, ChunkPos chunk) {
        int top = (int) site.top;
        int half = site.width / 2;
        int curtainU = 1;
        double poolCentre = site.faceDist + site.poolAlong * 0.45;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                int u = (x - site.x) * site.dirX + (z - site.z) * site.dirZ;
                int v = (x - site.x) * -site.dirZ + (z - site.z) * site.dirX;
                if (u < -site.channel - 2 || u > REACH || Math.abs(v) > REACH) {
                    continue;
                }
                int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                // ---- the feeding stream, from a spring in the high ground to the lip ----------
                if (u <= 0) {
                    double t = (double) -u / site.channel;
                    int chanHalf = (int) Math.round(half * (1.0 - 0.55 * t) + jitter(site.seed, u, 3) * 0.6);
                    if (u >= -site.channel && Math.abs(v) <= chanHalf) {
                        placed |= stream(level, pos, x, z, ground, top, u == -site.channel);
                    } else if (u >= -site.channel - 1 && Math.abs(v) <= chanHalf + 1) {
                        placed |= bank(level, pos, x, z, ground, top);
                    }
                    if (u == 0 && Math.abs(v) <= half) {
                        mossFace(level, pos, x + site.dirX, z + site.dirZ, x, z, top, site.poolSurface);
                    }
                    continue;
                }
                // ---- the curtain, split into strands by ribs of rock on wide falls ---------------
                if (u == curtainU && Math.abs(v) <= half) {
                    boolean rib = site.width >= 7 && Math.abs(v) < half && SeedMixer.unit(SeedMixer.hash2(site.seed, v, 7)) < 0.18;
                    placed |= rib ? rib(level, pos, x, z, top, site.poolSurface) : curtain(level, pos, x, z, top, site.poolSurface);
                    continue;
                }
                if (u == curtainU && Math.abs(v) == half + 1) {
                    // keep the stream's water from spilling sideways off the lip
                    placed |= bank(level, pos, x - site.dirX, z - site.dirZ, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x - site.dirX, z - site.dirZ) - 1, top);
                }
                // ---- the plunge pool and its rim ------------------------------------------------
                double ru = (u - poolCentre) / site.poolAlong;
                double rv = v / site.poolAcross;
                double r = Math.sqrt(ru * ru + rv * rv) + 0.12 * jitter(site.seed, x, z);
                if (u >= curtainU && r <= 1.0) {
                    if (ground <= site.poolSurface + 4 || u == curtainU) {
                        int depth = Math.max(1, (int) Math.round(site.poolDepth * Math.sqrt(1.0 - r * r)));
                        placed |= pool(level, pos, x, z, ground, site.poolSurface, depth);
                    }
                } else if (u >= curtainU && r <= 1.4 && ground < site.poolSurface) {
                    placed |= dam(level, pos, x, z, ground, site.poolSurface, site.seed);
                }
            }
        }
        return placed;
    }

    /** Stream bed: water two blocks deep at the lip's level, the hillside dug back above it. */
    private static boolean stream(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int top, boolean spring) {
        if (ground < top - 1) {
            // low ground behind the lip: build the bed up instead of leaving water in the air
            for (int y = ground + 1; y <= top - 2; y++) {
                level.setBlock(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        level.setBlock(pos.set(x, top - 2, z), spring ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState(), 2);
        level.setBlock(pos.set(x, top - 1, z), Blocks.WATER.defaultBlockState(), 2);
        level.setBlock(pos.set(x, top, z), Blocks.WATER.defaultBlockState(), 2);
        // clear the hillside above the stream; a spring leaves a low rock roof over its source
        int clearTo = spring ? top + 2 : Math.min(ground, top + 12);
        for (int y = top + 1; y <= clearTo; y++) {
            BlockState existing = level.getBlockState(pos.set(x, y, z));
            if (!existing.isAir() && !existing.is(BlockTags.LOGS)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        return true;
    }

    /** Raises a bank to the water surface where the ground beside the stream is lower. */
    private static boolean bank(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int top) {
        if (ground >= top) {
            return false;
        }
        for (int y = ground + 1; y <= top; y++) {
            BlockState existing = level.getBlockState(pos.set(x, y, z));
            if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || !existing.getFluidState().isEmpty()) {
                level.setBlock(pos, y == top ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
            }
        }
        return true;
    }

    /** A falling column from the lip down to the pool, cutting a shallow notch where rock is in the way. */
    private static boolean curtain(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, int poolSurface) {
        BlockState falling = Fluids.FLOWING_WATER.getFlowing(8, true).createLegacyBlock();
        for (int y = top; y > poolSurface; y--) {
            BlockState existing = level.getBlockState(pos.set(x, y, z));
            if (existing.is(Blocks.BEDROCK)) {
                break;
            }
            level.setBlock(pos, y == top ? Fluids.FLOWING_WATER.getFlowing(7, false).createLegacyBlock() : falling, 2);
        }
        return true;
    }

    /** A rib of mossy rock dividing the curtain, from the pool up to the lip. */
    private static boolean rib(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, int poolSurface) {
        for (int y = poolSurface + 1; y <= top; y++) {
            level.setBlock(pos.set(x, y, z), (x + y + z & 3) == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
        }
        return true;
    }

    /** The wet cliff behind the curtain turns mossy. */
    private static void mossFace(WorldGenLevel level, BlockPos.MutableBlockPos pos, int curtainX, int curtainZ, int x, int z, int top, int poolSurface) {
        for (int y = top - 1; y > poolSurface; y--) {
            BlockState existing = level.getBlockState(pos.set(x, y, z));
            if (existing.is(BlockTags.BASE_STONE_OVERWORLD) || existing.is(BlockTags.DIRT)) {
                level.setBlock(pos, (x * 7 + y * 3 + z & 3) == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
            }
        }
    }

    /** A pool column: dug out below the surface, water to the surface, gravel and sand on the bed. */
    private static boolean pool(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int surface, int depth) {
        int bed = surface - depth;
        level.setBlock(pos.set(x, bed, z), (x + z & 1) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState(), 2);
        for (int y = bed + 1; y <= surface; y++) {
            level.setBlock(pos.set(x, y, z), Blocks.WATER.defaultBlockState(), 2);
        }
        for (int y = surface + 1; y <= ground; y++) {
            BlockState existing = level.getBlockState(pos.set(x, y, z));
            if (!existing.isAir() && existing.getFluidState().isEmpty()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        return true;
    }

    /** Tumbled rock raised to the pool's surface where the ground at the foot falls away. */
    private static boolean dam(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int surface, long seed) {
        for (int y = ground + 1; y <= surface; y++) {
            double n = SeedMixer.unit(SeedMixer.hash3(seed, x, y, z));
            BlockState rock = n < 0.35 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.75 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            level.setBlock(pos.set(x, y, z), y == surface && n < 0.5 ? Blocks.MOSS_BLOCK.defaultBlockState() : rock, 2);
        }
        return true;
    }

    private static double jitter(long seed, int a, int b) {
        return SeedMixer.unit(SeedMixer.hash2(seed ^ 0x51DE, a, b)) * 2.0 - 1.0;
    }
}
