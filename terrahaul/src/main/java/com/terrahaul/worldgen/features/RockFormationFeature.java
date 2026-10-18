package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Natural rock formations at the deterministic sites of {@link TerrainModel#formationSite}:
 * <ul>
 *   <li>arches: an upper half elliptical ring of layered rock standing on two buried legs;</li>
 *   <li>spires: tall, slightly leaning, tapering pillars with ledges;</li>
 *   <li>hoodoos: clusters of thin columns under wider cap rocks of a harder layer;</li>
 *   <li>sea stacks: pillars rising from shallow water off rocky coasts.</li>
 * </ul>
 * Every formation is built from vanilla blocks in the local geology's strata, is anchored in the
 * ground and stays within 15 blocks of its centre.
 */
public final class RockFormationFeature extends Feature<NoneFeatureConfiguration> {
    public RockFormationFeature(Codec<NoneFeatureConfiguration> codec) {
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
        int cell = TerrainModel.FORMATION_CELL;
        boolean placed = false;
        for (int cx = Math.floorDiv(chunk.getMinBlockX(), cell); cx <= Math.floorDiv(chunk.getMaxBlockX(), cell); cx++) {
            for (int cz = Math.floorDiv(chunk.getMinBlockZ(), cell); cz <= Math.floorDiv(chunk.getMaxBlockZ(), cell); cz++) {
                TerrainModel.FormationSite site = model.formationSite(cx, cz);
                if (site != null && (site.x >> 4) == chunk.x && (site.z >> 4) == chunk.z) {
                    RandomSource random = new LegacyRandomSource(site.seed);
                    placed |= switch (site.type) {
                        case TerrainModel.ARCH -> arch(level, site, random);
                        case TerrainModel.SPIRE -> spire(level, site, random, false);
                        case TerrainModel.HOODOO -> hoodoos(level, site, random);
                        case TerrainModel.SEA_STACK -> spire(level, site, random, true);
                        default -> false;
                    };
                }
            }
        }
        return placed;
    }

    private static boolean replaceable(BlockState state) {
        return !state.is(Blocks.BEDROCK) && !state.hasBlockEntity();
    }

    private static int ground(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    private boolean arch(WorldGenLevel level, TerrainModel.FormationSite site, RandomSource random) {
        double a = 6.0 + site.size * 5.0;            // half span (inner)
        double b = 7.0 + site.size * 12.0;           // inner height
        double t = 2.5 + random.nextDouble() * 2.0;  // ring thickness
        double depth = 2.0 + random.nextDouble() * 1.5;
        double cos = Math.cos(site.angle);
        double sin = Math.sin(site.angle);
        int baseY = ground(level, site.x, site.z);
        // both legs need ground: skip arches straddling a cliff
        int leg1 = ground(level, site.x + (int) (cos * (a + t / 2)), site.z + (int) (sin * (a + t / 2)));
        int leg2 = ground(level, site.x - (int) (cos * (a + t / 2)), site.z - (int) (sin * (a + t / 2)));
        if (Math.abs(leg1 - baseY) > 5 || Math.abs(leg2 - baseY) > 5) {
            return false;
        }
        int y0 = Math.min(leg1, leg2);
        int ext = Mth.ceil(Math.min(15.0, a + t + 1));
        int seed = (int) (site.seed & 0xFF);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -ext; dx <= ext; dx++) {
            for (int dz = -ext; dz <= ext; dz++) {
                double u = dx * cos + dz * sin;
                double v = -dx * sin + dz * cos;
                double halfDepth = depth * (0.8 + 0.2 * Math.cos(u * 0.4));
                if (Math.abs(v) > halfDepth) {
                    continue;
                }
                for (int dy = -4; dy <= (int) (b + t) + 1; dy++) {
                    double y = dy;
                    boolean solid;
                    if (y < 0) {
                        // buried legs
                        solid = Math.abs(u) >= a && Math.abs(u) <= a + t;
                    } else {
                        double inner = (u / a) * (u / a) + (y / b) * (y / b);
                        double outer = (u / (a + t)) * (u / (a + t)) + (y / (b + t)) * (y / (b + t));
                        solid = inner >= 1.0 && outer <= 1.0;
                    }
                    if (!solid || random.nextFloat() < 0.04) {
                        continue;
                    }
                    pos.set(site.x + dx, y0 + dy, site.z + dz);
                    if (replaceable(level.getBlockState(pos))) {
                        level.setBlock(pos, GeologyPalette.band(site.geology, site.climate, y0 + dy, seed), 2);
                    }
                }
            }
        }
        return true;
    }

    private boolean spire(WorldGenLevel level, TerrainModel.FormationSite site, RandomSource random, boolean seaStack) {
        int baseY = ground(level, site.x, site.z);
        int topY = seaStack ? Math.max(baseY, level.getSeaLevel()) + 8 + (int) (site.size * 24)
            : baseY + 16 + (int) (site.size * 30);
        double r0 = seaStack ? 4.0 + site.size * 2.5 : 2.8 + site.size * 2.0;
        double r1 = seaStack ? 2.5 : 1.0 + random.nextDouble() * 0.8;
        double leanX = (random.nextDouble() - 0.5) * (seaStack ? 2.0 : 5.0);
        double leanZ = (random.nextDouble() - 0.5) * (seaStack ? 2.0 : 5.0);
        int seed = (int) (site.seed & 0xFF);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int start = baseY - 4;
        for (int y = start; y <= topY; y++) {
            double t = (double) (y - baseY) / Math.max(1, topY - baseY);
            t = Mth.clamp(t, 0.0, 1.0);
            double radius = Mth.lerp(Math.pow(t, 0.8), r0, r1);
            // ledges every few blocks
            radius += Math.max(0.0, Math.sin(y * 0.9 + seed) * 0.8 - 0.3);
            if (y < baseY + 2) {
                radius += 1.2;
            }
            double cx = site.x + leanX * t * t;
            double cz = site.z + leanZ * t * t;
            int r = Mth.ceil(radius);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double ddx = dx + (site.x - cx);
                    double ddz = dz + (site.z - cz);
                    double d = ddx * ddx + ddz * ddz;
                    if (d > radius * radius || d > (radius - 0.6) * (radius - 0.6) && random.nextFloat() < 0.35) {
                        continue;
                    }
                    if (Math.abs(dx) > 15 || Math.abs(dz) > 15) {
                        continue;
                    }
                    pos.set(site.x + dx, y, site.z + dz);
                    if (replaceable(level.getBlockState(pos))) {
                        level.setBlock(pos, GeologyPalette.band(site.geology, site.climate, y, seed), 2);
                    }
                }
            }
        }
        if (seaStack) {
            // a grassy cap on top of the stack
            pos.set(site.x, topY + 1, site.z);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    pos.set(site.x + (int) leanX + dx, topY, site.z + (int) leanZ + dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    }
                }
            }
        }
        return true;
    }

    private boolean hoodoos(WorldGenLevel level, TerrainModel.FormationSite site, RandomSource random) {
        int count = 2 + random.nextInt(4);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState cap = site.climate == 2 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
        int seed = (int) (site.seed & 0xFF);
        for (int i = 0; i < count; i++) {
            int x = site.x + random.nextInt(13) - 6;
            int z = site.z + random.nextInt(13) - 6;
            int baseY = ground(level, x, z);
            if (!level.getBlockState(pos.set(x, baseY, z)).getFluidState().isEmpty()) {
                continue;
            }
            int height = 5 + random.nextInt(6) + (int) (site.size * 8);
            double radius = 1.0 + random.nextDouble() * 0.9;
            for (int y = baseY - 2; y <= baseY + height + 2; y++) {
                boolean capLayer = y > baseY + height;
                // columns pinch in the middle, like real hoodoos
                double t = (double) (y - baseY) / height;
                double r = capLayer ? radius + 1.3 : radius * (1.0 - 0.3 * Math.sin(Math.PI * Mth.clamp(t, 0, 1))) + (y < baseY + 1 ? 0.8 : 0);
                int ri = Mth.ceil(r);
                for (int dx = -ri; dx <= ri; dx++) {
                    for (int dz = -ri; dz <= ri; dz++) {
                        if (dx * dx + dz * dz > r * r + 0.3) {
                            continue;
                        }
                        pos.set(x + dx, y, z + dz);
                        if (replaceable(level.getBlockState(pos))) {
                            level.setBlock(pos, capLayer ? cap : GeologyPalette.band(site.geology, Math.max(1, site.climate), y, seed), 2);
                        }
                    }
                }
            }
        }
        return true;
    }

    static long mixSeed(long seed, int salt) {
        return SeedMixer.derive(seed, salt);
    }
}
