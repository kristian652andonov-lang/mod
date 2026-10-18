package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;
import com.terrahaul.worldgen.terrain.ColumnSample;
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
 *   <li>arches: rough, lopsided spans or windows through fins, sunk into the ground;</li>
 *   <li>spires: tall, slightly leaning, tapering pillars with ledges;</li>
 *   <li>hoodoos: fields of beaded pillars, most under a cap of a harder layer;</li>
 *   <li>sea stacks: pillars rising from shallow water off rocky coasts.</li>
 * </ul>
 * Every formation is built from vanilla blocks in the local geology's strata and is anchored in
 * the ground. Spires and sea stacks stay within 15 blocks of their centre and are built by the
 * chunk holding it; arches and hoodoo fields can be larger and are built column by column by each
 * chunk they cover.
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
        // arches and hoodoo fields are larger than a chunk: every chunk they overlap builds the
        // columns it owns, from the same deterministic shape, so they can span several chunks
        int minCx = Math.floorDiv(chunk.getMinBlockX() - REACH, cell);
        int maxCx = Math.floorDiv(chunk.getMaxBlockX() + REACH, cell);
        int minCz = Math.floorDiv(chunk.getMinBlockZ() - REACH, cell);
        int maxCz = Math.floorDiv(chunk.getMaxBlockZ() + REACH, cell);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                TerrainModel.FormationSite site = model.formationSite(cx, cz);
                if (site == null) {
                    continue;
                }
                boolean inChunk = (site.x >> 4) == chunk.x && (site.z >> 4) == chunk.z;
                placed |= switch (site.type) {
                    case TerrainModel.ARCH -> arch(level, model, site, chunk);
                    case TerrainModel.HOODOO -> hoodooField(level, model, site, chunk);
                    case TerrainModel.SPIRE -> inChunk && spire(level, site, new LegacyRandomSource(site.seed), false);
                    case TerrainModel.SEA_STACK -> inChunk && spire(level, site, new LegacyRandomSource(site.seed), true);
                    default -> false;
                };
            }
        }
        return placed;
    }

    /** Furthest a formation reaches from its site, in blocks. */
    private static final int REACH = 80;
    /** Coherent roughness shared by all formations; offsets per site keep them different. */
    private static final SimplexNoise ROUGH = new SimplexNoise(0x7E77A0C4L);

    private static boolean inChunk(ChunkPos chunk, int x, int z) {
        return (x >> 4) == chunk.x && (z >> 4) == chunk.z;
    }

    private static boolean overlaps(ChunkPos chunk, double cx, double cz, double radius) {
        return cx + radius >= chunk.getMinBlockX() && cx - radius <= chunk.getMaxBlockX()
            && cz + radius >= chunk.getMinBlockZ() && cz - radius <= chunk.getMaxBlockZ();
    }

    private static boolean replaceable(BlockState state) {
        return !state.is(Blocks.BEDROCK) && !state.hasBlockEntity();
    }

    private static int ground(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    /**
     * A natural arch: a rough, uneven band of rock along a lopsided curve, thick at the feet and
     * thin at the crown, slightly bowed in plan, with feet sunk into the ground and rubble around
     * them. About a third are windows eroded through a fin of rock instead of free-standing spans.
     * The surface is roughened by coherent 3D noise, so no two arches share a profile.
     */
    private boolean arch(WorldGenLevel level, TerrainModel model, TerrainModel.FormationSite site, ChunkPos chunk) {
        RandomSource random = new LegacyRandomSource(site.seed);
        double a = 7.0 + site.size * 10.0 + random.nextDouble() * 3.0;  // half span of the centre line
        double b = 8.0 + site.size * 12.0 + random.nextDouble() * 5.0;  // crown height of the centre line
        double legT = 2.8 + random.nextDouble() * 2.0;                  // half thickness at the feet
        double topT = 1.2 + random.nextDouble() * 1.4;                  // half thickness at the crown
        double legD = 3.0 + random.nextDouble() * 2.5;                  // half depth at the feet
        double topD = 1.5 + random.nextDouble() * 1.5;
        double skew = (random.nextDouble() - 0.5) * 1.2;                // moves the crown off centre
        double sag = 0.5 + random.nextDouble() * 0.5;                   // lower = flatter, boxier crown
        double bend = (random.nextDouble() - 0.5) * 5.0;                // bow in plan view
        boolean fin = random.nextFloat() < 0.35;
        double finTop = b + topT + 2.0 + random.nextDouble() * 6.0;
        double reach = a + legT + 4.0;
        if (!overlaps(chunk, site.x, site.z, reach + Math.abs(bend) + legD)) {
            return false;
        }
        double cos = Math.cos(site.angle);
        double sin = Math.sin(site.angle);
        // both feet need ground at similar heights: skip arches straddling a cliff or a river
        ColumnSample s = new ColumnSample();
        model.sampleInto((int) Math.round(site.x + cos * a), (int) Math.round(site.z + sin * a), s);
        double foot1 = s.height;
        boolean wet1 = s.waterLevel != ColumnSample.NO_WATER;
        model.sampleInto((int) Math.round(site.x - cos * a), (int) Math.round(site.z - sin * a), s);
        double foot2 = s.height;
        if (wet1 || s.waterLevel != ColumnSample.NO_WATER || Math.abs(foot1 - foot2) > 9.0) {
            return false;
        }
        int y0 = (int) Math.floor(Math.min(foot1, foot2));
        double off = (site.seed & 0xFFFF) * 0.731;
        // centre line: n samples along the curve, then both feet continued straight down
        int n = 72;
        int sink = 8;
        double[] cu = new double[n + 1 + 2 * sink];
        double[] cy = new double[cu.length];
        double[] ct = new double[cu.length];
        double[] cd = new double[cu.length];
        double[] cv = new double[cu.length];
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            double w = t + skew * t * (1.0 - t);
            double rise = Math.pow(Math.max(0.0, Math.sin(Math.PI * w)), sag);
            cu[i] = -a + 2.0 * a * t;
            cy[i] = b * rise;
            ct[i] = Mth.lerp(rise, legT, topT);
            cd[i] = Mth.lerp(rise, legD, topD);
            cv[i] = bend * Math.sin(Math.PI * t);
        }
        for (int k = 0; k < sink; k++) {
            for (int side = 0; side < 2; side++) {
                int i = n + 1 + 2 * k + side;
                cu[i] = side == 0 ? -a : a;
                cy[i] = -1.0 - k;
                ct[i] = legT + 0.3 * k;
                cd[i] = legD + 0.3 * k;
                cv[i] = 0.0;
            }
        }
        int seed = (int) (site.seed & 0xFF);
        int ext = Mth.ceil(reach + Math.abs(bend) + legD);
        int top = y0 + (int) Math.ceil(Math.max(b + topT, fin ? finTop : 0.0)) + 3;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int x = Math.max(chunk.getMinBlockX(), site.x - ext); x <= Math.min(chunk.getMaxBlockX(), site.x + ext); x++) {
            for (int z = Math.max(chunk.getMinBlockZ(), site.z - ext); z <= Math.min(chunk.getMaxBlockZ(), site.z + ext); z++) {
                double dx = x - site.x;
                double dz = z - site.z;
                double u = dx * cos + dz * sin;
                double v = -dx * sin + dz * cos;
                if (Math.abs(u) > reach || Math.abs(v) > legD + Math.abs(bend) + 3.0) {
                    continue;
                }
                int ground = ground(level, x, z);
                boolean any = false;
                for (int y = Math.min(ground, y0) - sink; y <= top; y++) {
                    double yy = y - y0;
                    int best = 0;
                    double bestD = Double.MAX_VALUE;
                    for (int i = 0; i < cu.length; i++) {
                        double du = u - cu[i];
                        double dy = yy - cy[i];
                        double d = du * du + dy * dy;
                        if (d < bestD) {
                            bestD = d;
                            best = i;
                        }
                    }
                    double rough = ROUGH.noise3(x * 0.19 + off, y * 0.21, z * 0.19);
                    double rough2 = ROUGH.noise3(x * 0.13 - off, y * 0.17 + 40.0, z * 0.13);
                    boolean solid = Math.sqrt(bestD) <= ct[best] + 1.1 * rough
                        && Math.abs(v - cv[best]) <= cd[best] + 0.9 * rough2;
                    if (!solid && fin && Math.abs(u) <= a + legT * 0.7 && Math.abs(v) <= legD * 0.8 + 0.8 * rough2) {
                        // fin: solid rock above the opening, up to an eroded, uneven crest
                        double t = Mth.clamp((u + a) / (2.0 * a), 0.0, 1.0);
                        double w = t + skew * t * (1.0 - t);
                        double opening = b * Math.pow(Math.max(0.0, Math.sin(Math.PI * w)), sag);
                        double crest = finTop - 4.0 * Math.pow(Math.abs(u) / (a + legT), 2.0) + 2.5 * rough;
                        solid = yy > opening && yy <= crest;
                    }
                    if (!solid) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (replaceable(level.getBlockState(pos))) {
                        level.setBlock(pos, GeologyPalette.band(site.geology, site.climate, y + (int) (rough2 * 2.0), seed), 2);
                        any = true;
                    }
                }
                // fallen rubble around the feet
                if (!any && Math.abs(Math.abs(u) - a) < legT + 5.0 && Math.abs(v) < legD + 5.0
                    && SeedMixer.unit(SeedMixer.hash2(site.seed, x, z)) < 0.05) {
                    pos.set(x, ground + 1, z);
                    if (level.getBlockState(pos).isAir() && level.getFluidState(pos.below()).isEmpty()) {
                        level.setBlock(pos, GeologyPalette.band(site.geology, site.climate, ground, seed), 2);
                    }
                }
                placed |= any;
            }
        }
        return placed;
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

    /**
     * A field of hoodoos: 7-24 knobbly, beaded pillars of banded rock, 9-37 blocks tall and 4-10
     * wide, standing in rows along eroded fins (as in Bryce Canyon). Most wear a cap of a harder,
     * paler layer above a pinched neck; the others are worn to rounded stumps. Pillars near the edge
     * of a field are shorter, and each one flares into a talus skirt at its foot.
     */
    private boolean hoodooField(WorldGenLevel level, TerrainModel model, TerrainModel.FormationSite site, ChunkPos chunk) {
        RandomSource random = new LegacyRandomSource(site.seed);
        int count = 7 + random.nextInt(8) + (int) (site.size * 10.0);
        double fieldR = 22.0 + site.size * 34.0;
        int rows = 2 + random.nextInt(3);
        double cos = Math.cos(site.angle);
        double sin = Math.sin(site.angle);
        int climate = Math.max(1, site.climate);
        BlockState cap = climate == 2 ? Blocks.WHITE_TERRACOTTA.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        ColumnSample s = new ColumnSample();
        boolean placed = false;
        for (int i = 0; i < count; i++) {
            // draw every parameter before deciding whether this chunk builds the pillar, so the
            // sequence is the same in every chunk
            double along = (random.nextDouble() * 2.0 - 1.0) * fieldR;
            double across = (random.nextInt(rows) - (rows - 1) * 0.5) * 9.0 + (random.nextDouble() - 0.5) * 4.0;
            int hx = site.x + (int) Math.round(along * cos - across * sin);
            int hz = site.z + (int) Math.round(along * sin + across * cos);
            double edge = 1.0 - 0.55 * (along / fieldR) * (along / fieldR);
            double height = (9.0 + random.nextDouble() * 12.0 + site.size * 16.0) * edge;
            double r0 = 1.9 + random.nextDouble() * 2.0 + height * 0.045;
            int beads = 2 + random.nextInt(3);
            double phase = random.nextDouble() * Math.PI * 2.0;
            boolean capped = random.nextFloat() < 0.65;
            double capH = 2.0 + random.nextDouble() * 1.5;
            double capR = r0 * 0.55 + 1.2 + random.nextDouble() * 1.0;
            double leanX = (random.nextDouble() - 0.5) * 0.12 * height;
            double leanZ = (random.nextDouble() - 0.5) * 0.12 * height;
            double aspect = 0.7 + 0.3 * random.nextDouble();
            double rot = random.nextDouble() * Math.PI;
            double ext = Math.max(r0 + 3.2, capR + 1.0) + Math.max(Math.abs(leanX), Math.abs(leanZ)) + 1.0;
            if (!overlaps(chunk, hx, hz, ext)) {
                continue;
            }
            model.sampleInto(hx, hz, s);
            if (s.waterLevel != ColumnSample.NO_WATER || s.height < model.seaLevel() + 1 || s.river > 0.0 || s.lake > 0.0) {
                continue;
            }
            double baseH = s.height;
            if (steep(model, s, hx, hz, (int) Math.ceil(r0 + 1.0), baseH)) {
                continue;
            }
            placed |= hoodoo(level, site, chunk, hx, hz, (int) Math.floor(baseH), height, r0, beads, phase, capped, capH, capR,
                leanX, leanZ, aspect, rot, (int) Math.ceil(ext), climate, cap);
        }
        return placed;
    }

    private boolean hoodoo(WorldGenLevel level, TerrainModel.FormationSite site, ChunkPos chunk, int hx, int hz, int baseY,
                           double height, double r0, int beads, double phase, boolean capped, double capH, double capR,
                           double leanX, double leanZ, double aspect, double rot, int ext, int climate, BlockState cap) {
        double off = ((site.seed >>> 16) & 0xFFFF) * 0.517 + hx * 0.031;
        double rc = Math.cos(rot);
        double rs = Math.sin(rot);
        int seed = (int) (site.seed & 0xFF);
        int top = baseY + (int) Math.ceil(height + (capped ? capH : 0.0)) + 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int x = Math.max(chunk.getMinBlockX(), hx - ext); x <= Math.min(chunk.getMaxBlockX(), hx + ext); x++) {
            for (int z = Math.max(chunk.getMinBlockZ(), hz - ext); z <= Math.min(chunk.getMaxBlockZ(), hz + ext); z++) {
                int ground = ground(level, x, z);
                if (!level.getFluidState(pos.set(x, ground, z)).isEmpty()) {
                    continue;
                }
                for (int y = Math.min(ground, baseY) - 2; y <= top; y++) {
                    double yy = y - baseY;
                    double t = Mth.clamp(yy / height, 0.0, 1.2);
                    double cx = hx + leanX * t * t;
                    double cz = hz + leanZ * t * t;
                    double ex = x - cx;
                    double ez = z - cz;
                    double pu = ex * rc + ez * rs;
                    double pv = (-ex * rs + ez * rc) / aspect;
                    double d = Math.sqrt(pu * pu + pv * pv);
                    double rough = 0.5 * ROUGH.noise3(x * 0.3 + off, y * 0.24, z * 0.3);
                    boolean capBlock = false;
                    double r;
                    if (yy < 0.0) {
                        r = r0 + 2.6;
                    } else if (capped && yy > height - 0.5) {
                        // the cap: a flattened, rounded lump of the harder layer
                        double c = (yy - (height - 0.5)) / capH;
                        if (c > 1.0) {
                            continue;
                        }
                        r = capR * Math.sqrt(Math.max(0.0, 1.0 - c * c * c));
                        capBlock = true;
                    } else {
                        r = r0 * (1.0 - 0.42 * t);
                        r *= 1.0 + 0.2 * Math.sin(Math.PI * 2.0 * beads * t + phase);
                        if (capped) {
                            r *= 1.0 - 0.32 * smooth(0.7, 0.95, t);
                        } else if (t > 0.8) {
                            r *= Math.sqrt(Math.max(0.0, 1.0 - Math.pow((t - 0.8) / 0.2, 2.0)));
                        }
                        double flare = Math.max(0.0, 1.0 - t / 0.2);
                        r += 2.6 * flare * flare;
                    }
                    if (d > r + rough) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (replaceable(level.getBlockState(pos))) {
                        BlockState state = capBlock ? cap : GeologyPalette.band(site.geology, climate, y + (int) (rough * 2.0), seed);
                        level.setBlock(pos, state, 2);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }

    /** Whether the ground around a pillar's foot spans more than 6 blocks (a cliff edge or gully). */
    private static boolean steep(TerrainModel model, ColumnSample s, int x, int z, int r, double centre) {
        int[][] offsets = {{r, 0}, {-r, 0}, {0, r}, {0, -r}};
        for (int[] o : offsets) {
            model.sampleInto(x + o[0], z + o[1], s);
            if (Math.abs(s.height - centre) > 6.0) {
                return true;
            }
        }
        return false;
    }

    private static double smooth(double e0, double e1, double v) {
        double t = Mth.clamp((v - e0) / (e1 - e0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    static long mixSeed(long seed, int salt) {
        return SeedMixer.derive(seed, salt);
    }
}
