package com.terrahaul.worldgen.ruins;

import com.terrahaul.Terrahaul;
import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * What the pure ruin sites need from the running world, answered without reading any chunk:
 * <ul>
 *   <li>the biome a column gets, straight from the biome source (deterministic and thread-safe,
 *       the same call {@code /locate} uses), so materials follow the biome and houses only stand
 *       where the surface rule actually draws trails;</li>
 *   <li>whether a vanilla surface structure (village, outpost, temple, ruined portal, shipwreck,
 *       ocean ruin, ...) may stand nearby: the start chunks of every random-spread structure set
 *       around a site are predicted exactly as vanilla places them (including Terrahaul's
 *       terrain-aware choice), and a start counts if one of the set's structures accepts the
 *       biome there. This errs on the safe side (a start whose assembly fails still keeps ruins
 *       away), and because the finder asks the same question, {@code /terrahaul find} never
 *       reports a ruin that was skipped;</li>
 *   <li>the {@code trails} and {@code geologicalFormations} config.</li>
 * </ul>
 * The filter is installed when a Terrahaul level loads, before any chunk generates, and dropped
 * with the level.
 */
@EventBusSubscriber(modid = Terrahaul.MOD_ID)
public final class RuinWorldFilter implements RuinSites.WorldFilter {
    private final BiomeSource biomes;
    private final Climate.Sampler sampler;
    private final ChunkGeneratorStructureState structures;
    private final TerrainModel model;
    private final List<SetInfo> sets = new ArrayList<>();

    /** A structure set that can put something on the surface, and how far its structures reach (chunks). */
    private record SetInfo(RandomSpreadStructurePlacement placement, List<Holder<Structure>> structures, int reach) {
    }

    private RuinWorldFilter(ServerLevel level, TerrainModel model) {
        ServerChunkCache cache = level.getChunkSource();
        this.biomes = cache.getGenerator().getBiomeSource();
        this.sampler = cache.randomState().sampler();
        this.structures = cache.getGeneratorState();
        this.model = model;
        for (Holder<StructureSet> set : structures.possibleStructureSets()) {
            if (!(set.value().placement() instanceof RandomSpreadStructurePlacement placement)) {
                continue;
            }
            List<Holder<Structure>> surface = new ArrayList<>();
            int reach = 0;
            for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
                Structure s = entry.structure().value();
                if (s.step() == GenerationStep.Decoration.SURFACE_STRUCTURES) {
                    surface.add(entry.structure());
                    reach = Math.max(reach, reach(s.type()));
                }
            }
            if (!surface.isEmpty()) {
                sets.add(new SetInfo(placement, surface, reach));
            }
        }
    }

    /** How many chunks a structure of this type may extend from its start chunk. */
    private static int reach(StructureType<?> type) {
        if (type == StructureType.JIGSAW) {
            return 6;
        }
        if (type == StructureType.WOODLAND_MANSION) {
            return 5;
        }
        if (type == StructureType.OCEAN_MONUMENT) {
            return 4;
        }
        if (type == StructureType.OCEAN_RUIN) {
            return 3;
        }
        return 2;
    }

    @Override
    public int biome(int x, int y, int z) {
        return category(biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler));
    }

    @Override
    public boolean structureNear(int x, int z, int radius) {
        int cx = x >> 4;
        int cz = z >> 4;
        int r = (radius + 15) >> 4;
        long seed = structures.getLevelSeed();
        for (SetInfo set : sets) {
            int reach = r + set.reach();
            int spacing = set.placement().spacing();
            for (int rx = Math.floorDiv(cx - reach, spacing); rx <= Math.floorDiv(cx + reach, spacing); rx++) {
                for (int rz = Math.floorDiv(cz - reach, spacing); rz <= Math.floorDiv(cz + reach, spacing); rz++) {
                    ChunkPos start = set.placement().getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
                    if (Math.max(Math.abs(start.x - cx), Math.abs(start.z - cz)) > reach) {
                        continue;
                    }
                    if (!set.placement().isStructureChunk(structures, start.x, start.z)) {
                        continue;
                    }
                    int bx = start.getMiddleBlockX();
                    int bz = start.getMiddleBlockZ();
                    int by = (int) Math.max(model.seaLevel(), model.interpolatedHeight(bx, bz));
                    Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(by), QuartPos.fromBlock(bz), sampler);
                    for (Holder<Structure> s : set.structures()) {
                        if (s.value().biomes().contains(biome)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean trails() {
        return TerrahaulConfig.trails();
    }

    @Override
    public boolean formations() {
        return TerrahaulConfig.geologicalFormations();
    }

    /** Maps a biome to the coarse category the pure site code reasons with. */
    static int category(Holder<Biome> b) {
        if (b.is(BiomeTags.IS_BADLANDS)) {
            return RuinBiomes.BADLANDS;
        }
        if (b.is(Biomes.DESERT)) {
            return RuinBiomes.DESERT;
        }
        if (b.is(Biomes.BEACH) || b.is(Biomes.SNOWY_BEACH)) {
            return RuinBiomes.BEACH;
        }
        if (b.is(Biomes.STONY_SHORE) || b.is(Biomes.STONY_PEAKS) || b.is(Biomes.WINDSWEPT_GRAVELLY_HILLS)) {
            return RuinBiomes.STONY;
        }
        if (b.is(Biomes.SNOWY_SLOPES) || b.is(Biomes.FROZEN_PEAKS) || b.is(Biomes.JAGGED_PEAKS) || b.is(Biomes.ICE_SPIKES)) {
            return RuinBiomes.SNOWY_PEAKS;
        }
        if (b.is(Biomes.WARM_OCEAN)) {
            return RuinBiomes.OCEAN_WARM;
        }
        if (b.is(Biomes.LUKEWARM_OCEAN) || b.is(Biomes.DEEP_LUKEWARM_OCEAN)) {
            return RuinBiomes.OCEAN_LUKEWARM;
        }
        if (b.is(Biomes.COLD_OCEAN) || b.is(Biomes.DEEP_COLD_OCEAN)) {
            return RuinBiomes.OCEAN_COLD;
        }
        if (b.is(Biomes.FROZEN_OCEAN) || b.is(Biomes.DEEP_FROZEN_OCEAN)) {
            return RuinBiomes.OCEAN_FROZEN;
        }
        if (b.is(BiomeTags.IS_OCEAN)) {
            return RuinBiomes.OCEAN;
        }
        if (b.is(BiomeTags.IS_RIVER)) {
            return RuinBiomes.RIVER;
        }
        if (b.is(Biomes.MUSHROOM_FIELDS)) {
            return RuinBiomes.MUSHROOM;
        }
        if (b.is(Biomes.MANGROVE_SWAMP)) {
            return RuinBiomes.MANGROVE;
        }
        if (b.is(Biomes.SWAMP)) {
            return RuinBiomes.SWAMP;
        }
        if (b.is(BiomeTags.IS_JUNGLE)) {
            return RuinBiomes.JUNGLE;
        }
        if (b.is(BiomeTags.IS_SAVANNA)) {
            return RuinBiomes.SAVANNA;
        }
        if (b.is(Biomes.CHERRY_GROVE)) {
            return RuinBiomes.CHERRY;
        }
        if (b.is(Biomes.MEADOW)) {
            return RuinBiomes.MEADOW;
        }
        if (b.is(Biomes.WINDSWEPT_HILLS) || b.is(Biomes.WINDSWEPT_FOREST)) {
            return RuinBiomes.WINDSWEPT;
        }
        if (b.is(Biomes.SNOWY_PLAINS) || b.is(Biomes.SNOWY_TAIGA) || b.is(Biomes.GROVE)) {
            return RuinBiomes.SNOWY;
        }
        if (b.is(BiomeTags.IS_TAIGA)) {
            return RuinBiomes.TAIGA;
        }
        if (b.is(Biomes.BIRCH_FOREST) || b.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
            return RuinBiomes.BIRCH;
        }
        if (b.is(Biomes.DARK_FOREST)) {
            return RuinBiomes.DARK_FOREST;
        }
        if (b.is(BiomeTags.IS_FOREST)) {
            return RuinBiomes.FOREST;
        }
        return RuinBiomes.PLAINS;
    }

    /** Installs the filter of a Terrahaul level into its sites (replacing a stale one). */
    static synchronized void install(ServerLevel level) {
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model != null) {
            RuinSites.of(model).install(new RuinWorldFilter(level, model));
        }
    }

    /** Installs the filter if the level-load event has not done so (for example in a test harness). */
    static void ensure(ServerLevel level, RuinSites sites) {
        if (sites.filter() == null) {
            synchronized (RuinWorldFilter.class) {
                if (sites.filter() == null) {
                    sites.install(new RuinWorldFilter(level, sites.model()));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            install(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            TerrainModel model = TerrahaulGeneration.model(level);
            if (model != null) {
                RuinSites.forget(model);
            }
        }
    }
}
