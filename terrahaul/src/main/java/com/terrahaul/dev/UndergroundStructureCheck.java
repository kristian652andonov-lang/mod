package com.terrahaul.dev;

import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.TerrainModel;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Development checks for structures that belong underground (trial chambers, ancient cities,
 * strongholds, mineshafts): how far each one lies below the terrain over it. Used by
 * {@link AutoTest}.
 * <p>
 * The floor is measured in two ways. The <em>natural floor</em> is the chunk generator's own
 * estimate ({@code OCEAN_FLOOR_WG}, from the noise without structures), which shows where the
 * terrain would be without the structure; it is all a survey needs, so a survey never generates a
 * chunk. The check of a generated structure also reads the blocks: water inside the pieces, water
 * under them, how much rock covers them (the highest solid block, which on a structure floating in
 * the sea is its own shell) and how open the rock around the pieces is.
 */
final class UndergroundStructureCheck {
    /** Grid step of the natural-floor estimate, in blocks. */
    private static final int STEP = 4;
    /** Cover thinner than this lets the structure's terrain adaptation reach the floor. */
    private static final int THIN_COVER = 12;

    private UndergroundStructureCheck() {
    }

    /**
     * {@code <structure id>@x/z/radius}: generates (without placing) every start of the structure
     * whose chunk lies within {@code radius} blocks of x, z, the same way chunk generation does, and
     * reports how far the pieces lie below the natural floor.
     */
    static void survey(ServerLevel level, String spec, List<String> report) {
        String id = spec.substring(0, spec.indexOf('@'));
        String[] p = spec.substring(spec.indexOf('@') + 1).split("/");
        int cx = Integer.parseInt(p[0].trim());
        int cz = Integer.parseInt(p[1].trim());
        int radius = Integer.parseInt(p[2].trim());
        int limit = p.length > 3 ? Integer.parseInt(p[3].trim()) : 80;
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder<Structure> holder = registry.getHolder(ResourceLocation.parse(id)).orElse(null);
        if (holder == null) {
            report.add("survey " + id + ": not registered");
            return;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        List<ChunkPos> chunks = new ArrayList<>();
        int minChunkX = (cx - radius) >> 4;
        int maxChunkX = (cx + radius) >> 4;
        int minChunkZ = (cz - radius) >> 4;
        int maxChunkZ = (cz + radius) >> 4;
        for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
            if (placement instanceof RandomSpreadStructurePlacement spread) {
                int spacing = spread.spacing();
                for (int rx = Math.floorDiv(minChunkX, spacing); rx <= Math.floorDiv(maxChunkX, spacing); rx++) {
                    for (int rz = Math.floorDiv(minChunkZ, spacing); rz <= Math.floorDiv(maxChunkZ, spacing); rz++) {
                        ChunkPos c = spread.getPotentialStructureChunk(state.getLevelSeed(), rx * spacing, rz * spacing);
                        if (c.x >= minChunkX && c.x <= maxChunkX && c.z >= minChunkZ && c.z <= maxChunkZ && spread.isStructureChunk(state, c.x, c.z)) {
                            chunks.add(c);
                        }
                    }
                }
            } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
                for (ChunkPos c : state.getRingPositionsFor(rings)) {
                    if (c.x >= minChunkX && c.x <= maxChunkX && c.z >= minChunkZ && c.z <= maxChunkZ) {
                        chunks.add(c);
                    }
                }
            }
        }
        chunks.sort(Comparator.comparingDouble(c -> Math.hypot(c.getMiddleBlockX() - cx, c.getMiddleBlockZ() - cz)));
        if (chunks.size() > limit) {
            chunks = chunks.subList(0, limit);
        }
        Long2IntOpenHashMap floors = new Long2IntOpenHashMap();
        int valid = 0;
        int pokesOut = 0;
        int floatingPieces = 0;
        int thin = 0;
        long generateNanos = 0;
        long floorNanos = 0;
        List<String> worst = new ArrayList<>();
        List<int[]> clearances = new ArrayList<>();
        for (ChunkPos c : chunks) {
            long t0 = System.nanoTime();
            StructureStart start = holder.value().generate(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), state.getLevelSeed(), c, 0, level,
                holder.value().biomes()::contains);
            long t1 = System.nanoTime();
            generateNanos += t1 - t0;
            if (!start.isValid()) {
                continue;
            }
            valid++;
            Clearance clearance = naturalClearance(level, start.getPieces(), floors);
            floorNanos += System.nanoTime() - t1;
            if (clearance.min < 0) {
                pokesOut++;
            }
            if (clearance.floatingPieces > 0) {
                floatingPieces++;
            }
            if (clearance.min < THIN_COVER) {
                thin++;
            }
            BoundingBox box = StructurePiece.createBoundingBox(start.getPieces().stream());
            clearances.add(new int[] {clearance.min, c.getMinBlockX(), c.getMinBlockZ()});
            worst.add(String.format(Locale.ROOT, "%6d|    start chunk %d %d (locate %d ~ %d): %d pieces, y%d..%d, natural floor over it y%d..%d, top %d below the floor (min over pieces), %d of %d pieces partly above the floor, %d wholly above",
                clearance.min, c.x, c.z, c.getMinBlockX(), c.getMinBlockZ(), start.getPieces().size(), box.minY(), box.maxY(),
                clearance.floorMin, clearance.floorMax, clearance.min, clearance.piecesAbove, start.getPieces().size(), clearance.floatingPieces));
        }
        report.add(String.format(Locale.ROOT, "survey %s within %d blocks of %d %d: %d placement chunks (limit %d), %d starts; top above the natural floor: %d; some piece wholly above it: %d; cover under %d blocks: %d (generate %.0f ms, floor estimate %.0f ms, %d probes)",
            id, radius, cx, cz, chunks.size(), limit, valid, pokesOut, floatingPieces, THIN_COVER, thin, generateNanos / 1e6, floorNanos / 1e6, floors.size()));
        worst.sort(Comparator.comparingInt(s -> Integer.parseInt(s.substring(0, s.indexOf('|')).trim())));
        for (int i = 0; i < Math.min(8, worst.size()); i++) {
            report.add(worst.get(i).substring(worst.get(i).indexOf('|') + 1));
        }
        if (!clearances.isEmpty()) {
            clearances.sort(Comparator.comparingInt(a -> a[0]));
            report.add(String.format(Locale.ROOT, "    cover (natural floor minus top): min %d, median %d, max %d",
                clearances.get(0)[0], clearances.get(clearances.size() / 2)[0], clearances.get(clearances.size() - 1)[0]));
        }
    }

    /** Distances of the pieces below the natural floor. */
    private record Clearance(int min, int floorMin, int floorMax, int piecesAbove, int floatingPieces) {
    }

    private static Clearance naturalClearance(ServerLevel level, List<StructurePiece> pieces, Long2IntOpenHashMap floors) {
        int min = Integer.MAX_VALUE;
        int floorMin = Integer.MAX_VALUE;
        int floorMax = Integer.MIN_VALUE;
        int above = 0;
        int floating = 0;
        for (StructurePiece piece : pieces) {
            BoundingBox b = piece.getBoundingBox();
            int pieceFloorMin = Integer.MAX_VALUE;
            int pieceFloorMax = Integer.MIN_VALUE;
            for (int x = b.minX(); ; x = Math.min(x + STEP, b.maxX())) {
                for (int z = b.minZ(); ; z = Math.min(z + STEP, b.maxZ())) {
                    int f = naturalFloor(level, x, z, floors);
                    pieceFloorMin = Math.min(pieceFloorMin, f);
                    pieceFloorMax = Math.max(pieceFloorMax, f);
                    if (z == b.maxZ()) {
                        break;
                    }
                }
                if (x == b.maxX()) {
                    break;
                }
            }
            min = Math.min(min, pieceFloorMin - b.maxY());
            floorMin = Math.min(floorMin, pieceFloorMin);
            floorMax = Math.max(floorMax, pieceFloorMax);
            if (pieceFloorMin < b.maxY()) {
                above++;
            }
            if (pieceFloorMax < b.minY()) {
                floating++;
            }
        }
        return new Clearance(min, floorMin, floorMax, above, floating);
    }

    /**
     * The largest amount by which the terrain model's 2D surface lay above the generator's height
     * in the columns sampled so far (UndergroundStructures skips columns whose model surface is far
     * above what a structure needs, on the assumption that this stays small).
     */
    private static double maxModelExcess = Double.NEGATIVE_INFINITY;
    private static int maxExcessX;
    private static int maxExcessZ;
    private static long modelColumns;
    /** Sampled columns whose model surface lay more than 4, 8, 16, 24 and 32 blocks above the generator's height. */
    private static final long[] EXCESS_OVER = new long[5];
    private static final int[] EXCESS_LIMITS = {4, 8, 16, 24, 32};

    /** The highest solid block of the natural terrain (no structures), memoised per column. */
    private static int naturalFloor(ServerLevel level, int x, int z, Long2IntOpenHashMap floors) {
        long key = ChunkPos.asLong(x, z);
        if (floors.containsKey(key)) {
            return floors.get(key);
        }
        int y = level.getChunkSource().getGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level,
            level.getChunkSource().randomState());
        floors.put(key, y);
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model != null) {
            double excess = model.sample(x, z).height - y;
            if (excess > maxModelExcess) {
                maxModelExcess = excess;
                maxExcessX = x;
                maxExcessZ = z;
            }
            for (int i = 0; i < EXCESS_LIMITS.length; i++) {
                if (excess > EXCESS_LIMITS[i]) {
                    EXCESS_OVER[i]++;
                }
            }
            modelColumns++;
        }
        return y;
    }

    /** Reports {@link #maxModelExcess}. */
    static void reportModelExcess(List<String> report) {
        if (modelColumns > 0) {
            report.add(String.format(Locale.ROOT, "terrain model surface minus generator height (OCEAN_FLOOR_WG) over %d sampled columns: at most %.1f blocks (at %d %d); more than 4/8/16/24/32 blocks in %d/%d/%d/%d/%d columns",
                modelColumns, maxModelExcess, maxExcessX, maxExcessZ, EXCESS_OVER[0], EXCESS_OVER[1], EXCESS_OVER[2], EXCESS_OVER[3], EXCESS_OVER[4]));
        }
    }

    /**
     * Checks a generated structure against the blocks of the world (generating the chunks under
     * it first): its natural floor clearance, the rock covering it, water inside and under its
     * pieces, and the share of open blocks (air, water, lava) in a three-block shell around them.
     */
    static void check(ServerLevel level, StructureStart start, String label, List<String> report) {
        List<StructurePiece> pieces = start.getPieces();
        BoundingBox box = StructurePiece.createBoundingBox(pieces.stream());
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                level.getChunk(x, z, ChunkStatus.FULL, true);
            }
        }
        Clearance natural = naturalClearance(level, pieces, new Long2IntOpenHashMap());
        // per column: the highest piece top and the lowest piece bottom
        Long2IntOpenHashMap tops = new Long2IntOpenHashMap();
        Long2IntOpenHashMap bottoms = new Long2IntOpenHashMap();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long waterInside = 0;
        long volume = 0;
        // trial spawners and vaults, and how many of them carry their block entity
        int spawners = 0;
        int spawnerEntities = 0;
        int vaults = 0;
        int vaultEntities = 0;
        LongOpenHashSet counted = new LongOpenHashSet();
        BlockPos firstVault = null;
        for (StructurePiece piece : pieces) {
            BoundingBox b = piece.getBoundingBox();
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    long key = ChunkPos.asLong(x, z);
                    tops.put(key, Math.max(tops.getOrDefault(key, Integer.MIN_VALUE), b.maxY()));
                    bottoms.put(key, Math.min(bottoms.getOrDefault(key, Integer.MAX_VALUE), b.minY()));
                    for (int y = b.minY(); y <= b.maxY(); y++) {
                        volume++;
                        BlockState state = level.getBlockState(pos.set(x, y, z));
                        if (state.getFluidState().is(FluidTags.WATER)) {
                            waterInside++;
                        }
                        if ((state.is(Blocks.TRIAL_SPAWNER) || state.is(Blocks.VAULT)) && counted.add(pos.asLong())) {
                            var entity = level.getBlockEntity(pos);
                            if (state.is(Blocks.TRIAL_SPAWNER)) {
                                spawners++;
                                spawnerEntities += entity instanceof TrialSpawnerBlockEntity ? 1 : 0;
                            } else {
                                if (firstVault == null) {
                                    firstVault = pos.immutable();
                                }
                                vaults++;
                                vaultEntities += entity instanceof VaultBlockEntity ? 1 : 0;
                            }
                        }
                    }
                }
            }
        }
        int minCover = Integer.MAX_VALUE;
        int thinColumns = 0;
        int exposedColumns = 0;
        int waterBelowColumns = 0;
        for (var e : tops.long2IntEntrySet()) {
            int x = ChunkPos.getX(e.getLongKey());
            int z = ChunkPos.getZ(e.getLongKey());
            int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
            int cover = top - e.getIntValue();
            minCover = Math.min(minCover, cover);
            if (cover < THIN_COVER) {
                thinColumns++;
            }
            if (cover < 0) {
                exposedColumns++;
            }
            int bottom = bottoms.get(e.getLongKey());
            for (int y = bottom - 16; y < bottom; y++) {
                if (level.getFluidState(pos.set(x, y, z)).is(FluidTags.WATER)) {
                    waterBelowColumns++;
                    break;
                }
            }
        }
        // the shell: blocks within three blocks of a piece that belong to no piece
        LongOpenHashSet seen = new LongOpenHashSet();
        long shellSize = 0;
        long open = 0;
        long openWater = 0;
        for (StructurePiece piece : pieces) {
            BoundingBox b = piece.getBoundingBox();
            for (int x = b.minX() - 3; x <= b.maxX() + 3; x++) {
                for (int z = b.minZ() - 3; z <= b.maxZ() + 3; z++) {
                    for (int y = b.minY() - 3; y <= b.maxY() + 3; y++) {
                        if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ() && y >= b.minY() && y <= b.maxY()) {
                            continue;
                        }
                        pos.set(x, y, z);
                        if (!seen.add(pos.asLong()) || insideAny(pieces, pos)) {
                            continue;
                        }
                        shellSize++;
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || !state.getFluidState().isEmpty()) {
                            open++;
                            if (state.getFluidState().is(FluidTags.WATER)) {
                                openWater++;
                            }
                        }
                    }
                }
            }
        }
        shellSize = Math.max(1, shellSize);
        report.add(String.format(Locale.ROOT, "    underground %s: %d pieces, box %d %d %d .. %d %d %d; natural floor y%d..%d, top %d below it (%d pieces partly, %d wholly above it); cover by real blocks min %d, columns with cover <%d: %d, exposed: %d of %d; water in pieces %d of %d blocks; columns with water under the pieces: %d; shell (3 blocks) open %.1f%% (water %.1f%%)",
            label, pieces.size(), box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(),
            natural.floorMin, natural.floorMax, natural.min, natural.piecesAbove, natural.floatingPieces,
            minCover, THIN_COVER, thinColumns, exposedColumns, tops.size(), waterInside, volume, waterBelowColumns,
            100.0 * open / shellSize, 100.0 * openWater / shellSize));
        if (spawners + vaults > 0) {
            report.add(String.format(Locale.ROOT, "      trial spawners %d (%d with their block entity), vaults %d (%d with their block entity), first vault at %s",
                spawners, spawnerEntities, vaults, vaultEntities, firstVault == null ? "-" : firstVault.toShortString()));
        }
    }

    /**
     * Compares a structure start held by the world (after a restart: loaded from disk) with the
     * same start generated afresh: the pieces' bounding boxes and, for jigsaw pieces, the heights
     * of their junctions must agree, or the parts of the structure generated before and after the
     * restart would not line up.
     */
    static void compareWithFresh(ServerLevel level, Holder<Structure> structure, StructureStart held, List<String> report) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        StructureStart fresh = structure.value().generate(level.registryAccess(), generator, generator.getBiomeSource(),
            level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), held.getChunkPos(), 0, level,
            structure.value().biomes()::contains);
        List<StructurePiece> a = held.getPieces();
        List<StructurePiece> b = fresh.getPieces();
        int boxDiffs = 0;
        int junctionDiffs = 0;
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            if (!a.get(i).getBoundingBox().equals(b.get(i).getBoundingBox())) {
                boxDiffs++;
            }
            if (a.get(i) instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pa
                && b.get(i) instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pb) {
                for (int j = 0; j < Math.min(pa.getJunctions().size(), pb.getJunctions().size()); j++) {
                    if (pa.getJunctions().get(j).getSourceGroundY() != pb.getJunctions().get(j).getSourceGroundY()) {
                        junctionDiffs++;
                    }
                }
            }
        }
        report.add(String.format(Locale.ROOT, "      held start vs. generated afresh: %d / %d pieces, %d bounding boxes differ, %d junction heights differ",
            a.size(), b.size(), boxDiffs, junctionDiffs));
    }

    private static boolean insideAny(List<StructurePiece> pieces, BlockPos pos) {
        for (StructurePiece piece : pieces) {
            if (piece.getBoundingBox().isInside(pos)) {
                return true;
            }
        }
        return false;
    }
}
