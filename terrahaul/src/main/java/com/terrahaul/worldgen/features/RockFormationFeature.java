package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.surface.Fades;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Natural rock formations at the deterministic sites of {@link TerrainModel#formationSite}:
 * <ul>
 *   <li>arches: rough, lopsided spans of uneven thickness or windows through fins, standing on
 *       legs that reach down to the ground, with rubble around their feet;</li>
 *   <li>spires: tall, slightly leaning, tapering pillars with irregular ledges;</li>
 *   <li>hoodoos: fields of beaded pillars in rows, each worn to a jagged point, some joined by fins;</li>
 *   <li>sea stacks: steep pillars with a wave-cut notch and a grassy top, off rocky coasts.</li>
 * </ul>
 * The shapes are defined in {@link FormationShapes} and are built from vanilla blocks of the local
 * rock family (see {@link GeologyPalette}) in the strata of the surrounding terrain. Spires and sea
 * stacks stay within 15 blocks of their centre and are built by the chunk holding it. Arches and
 * hoodoo fields are larger: they are planned once from the terrain model and the site seed, and
 * every chunk they overlap builds its own columns of the same plan, so the parts meet exactly.
 */
public final class RockFormationFeature extends Feature<NoneFeatureConfiguration> {
    /** Furthest a formation reaches from its site, in blocks. */
    private static final int REACH = 112;
    /** Plans by site, shared by all chunks a formation covers (planning samples the terrain model). */
    private static final ConcurrentHashMap<Long, Object> PLANS = new ConcurrentHashMap<>();

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
                    case TerrainModel.SPIRE -> inChunk && spire(level, model, site, false);
                    case TerrainModel.SEA_STACK -> inChunk && spire(level, model, site, true);
                    default -> false;
                };
            }
        }
        return placed;
    }

    @SuppressWarnings("unchecked")
    private static <T> T plan(TerrainModel model, TerrainModel.FormationSite site, BiFunction<TerrainModel, TerrainModel.FormationSite, T> planner) {
        long key = SeedMixer.mix(site.seed ^ model.seed() * 31L);
        Object plan = PLANS.get(key);
        if (plan == null) {
            plan = planner.apply(model, site);
            if (PLANS.size() > 512) {
                PLANS.clear();
            }
            PLANS.put(key, plan);
        }
        return (T) plan;
    }

    private static boolean replaceable(BlockState state) {
        return !state.is(Blocks.BEDROCK) && !state.hasBlockEntity();
    }

    private static int ground(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    /** Rock family of a site as the surrounding terrain shows it (grey on mountains). */
    private static BlockState[] family(TerrainModel model, TerrainModel.FormationSite site) {
        ColumnSample s = model.sample(site.x & ~3, site.z & ~3);
        return GeologyPalette.family(site.geology, site.climate, site.climate == 0 && (s.mountain > 0.3 || s.alpine > 0.3));
    }

    /** Builds the columns of an arch that lie in this chunk (see {@link FormationShapes.Arch}). */
    private boolean arch(WorldGenLevel level, TerrainModel model, TerrainModel.FormationSite site, ChunkPos chunk) {
        FormationShapes.Arch arch = plan(model, site, FormationShapes.Arch::plan);
        int x0 = Math.max(chunk.getMinBlockX(), arch.minX);
        int x1 = Math.min(chunk.getMaxBlockX(), arch.maxX);
        int z0 = Math.max(chunk.getMinBlockZ(), arch.minZ);
        int z1 = Math.min(chunk.getMaxBlockZ(), arch.maxZ);
        if (x0 > x1 || z0 > z1) {
            return false;
        }
        BlockState[] family = family(model, site);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int ground = ground(level, x, z);
                double warp = Fades.strataWarp(model, x, z);
                for (int y = Math.min(ground, arch.baseY) - 2; y <= arch.maxY; y++) {
                    int m = arch.material(model, x, y, z, ground, warp);
                    if (m == FormationShapes.AIR) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (replaceable(level.getBlockState(pos))) {
                        level.setBlock(pos, family[m], 2);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }

    /** Builds the columns of a hoodoo field that lie in this chunk (see {@link FormationShapes.HoodooField}). */
    private boolean hoodooField(WorldGenLevel level, TerrainModel model, TerrainModel.FormationSite site, ChunkPos chunk) {
        FormationShapes.HoodooField field = plan(model, site, FormationShapes.HoodooField::plan);
        if (field.pillars.isEmpty()) {
            return false;
        }
        int x0 = Math.max(chunk.getMinBlockX(), field.minX);
        int x1 = Math.min(chunk.getMaxBlockX(), field.maxX);
        int z0 = Math.max(chunk.getMinBlockZ(), field.minZ);
        int z1 = Math.min(chunk.getMaxBlockZ(), field.maxZ);
        if (x0 > x1 || z0 > z1) {
            return false;
        }
        // hoodoos only stand in dry country: sandstone or red rock, banded to the tip
        int climate = Math.max(1, site.climate);
        BlockState[] family = GeologyPalette.family(site.geology, climate);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int ground = ground(level, x, z);
                if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) {
                    continue;
                }
                FormationShapes.Column column = field.column(x, z);
                if (column.pillars().length == 0 && column.fins().length == 0) {
                    continue;
                }
                int lowest = ground;
                for (FormationShapes.Hoodoo p : column.pillars()) {
                    lowest = Math.min(lowest, p.base);
                }
                for (int y = lowest - 2; y <= field.maxY; y++) {
                    int m = field.material(model, x, y, z, ground, column);
                    if (m == FormationShapes.AIR) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (replaceable(level.getBlockState(pos))) {
                        level.setBlock(pos, family[m], 2);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }

    /** Builds a spire or sea stack around a site in this chunk (see {@link FormationShapes.Spire}). */
    private boolean spire(WorldGenLevel level, TerrainModel model, TerrainModel.FormationSite site, boolean seaStack) {
        FormationShapes.Spire spire = FormationShapes.Spire.plan(site, ground(level, site.x, site.z), model.seaLevel(), seaStack)
            .settle(model, (x, z) -> ground(level, x, z));
        BlockState[] family = family(model, site);
        int r = FormationShapes.Spire.MAX_RADIUS;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int x = site.x - r; x <= site.x + r; x++) {
            for (int z = site.z - r; z <= site.z + r; z++) {
                int ground = ground(level, x, z);
                double warp = Fades.strataWarp(model, x, z);
                for (int y = Math.min(ground, spire.baseY) - 4; y <= spire.topY; y++) {
                    int m = spire.material(model, x, y, z, ground, warp);
                    if (m == FormationShapes.AIR) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (!replaceable(level.getBlockState(pos))) {
                        continue;
                    }
                    BlockState state = switch (m) {
                        case FormationShapes.GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
                        case FormationShapes.DIRT -> Blocks.DIRT.defaultBlockState();
                        default -> family[m];
                    };
                    level.setBlock(pos, state, 2);
                    placed = true;
                }
            }
        }
        return placed;
    }

    static long mixSeed(long seed, int salt) {
        return SeedMixer.derive(seed, salt);
    }
}
