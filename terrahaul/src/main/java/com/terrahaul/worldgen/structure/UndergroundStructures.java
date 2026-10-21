package com.terrahaul.worldgen.structure;

import com.terrahaul.Terrahaul;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;

/**
 * Keeps underground structures under the ground in Terrahaul worlds.
 * <p>
 * Trial chambers start at a fixed height (Y -40 to -20, their corridors climb to about Y 30),
 * ancient cities at Y -27, and strongholds and mineshafts at a random depth below sea level, all
 * chosen for vanilla terrain, whose deep-ocean floor lies around Y 30. Terrahaul's deep oceans are
 * far deeper (their floors lie around Y -60 to -160, trenches deeper still), so such a structure
 * could end up in open water. Its terrain adaptation then built the terrain around it there:
 * trial chambers are encapsulated in rock, so they hung in the sea as huge stone blobs with the
 * corridors inside, up to two hundred blocks above the floor.
 * <p>
 * A structure in the tag {@link #KEEP_UNDERGROUND} is therefore moved down, as a whole, until
 * every piece lies at least {@value #COVER} blocks below the terrain over it and within
 * {@value #COVER} blocks around it. That is the reach of the terrain adaptation
 * ({@link Beardifier#BEARD_KERNEL_RADIUS}), so the rock it adds or carves never reaches the floor:
 * the sea floor (or the land surface) looks exactly as it would without the structure. The move
 * happens when the structure start is created, before the noise fill reads the pieces, and it
 * never changes the start's chunk, so {@code /locate} still finds it there. The moved pieces are
 * saved as they are, and none of these structures is rebuilt when it is loaded (the ocean monument
 * is, see OceanMonumentStructureMixin, and it is not in the tag). Structures that are already deep
 * enough, which is nearly all of them away from deep water, keep their vanilla height. A structure
 * is never moved below {@value #BOTTOM_MARGIN} blocks above the world bottom (bedrock, and the lava
 * of the deepest caves), even if that leaves it short of its cover in the deepest trenches.
 * <p>
 * The terrain over the structure is the chunk generator's own height ({@code OCEAN_FLOOR_WG}, the
 * highest block of the noise terrain that is not water or air, without structures), sampled on a
 * {@value #STEP}-block grid over the pieces and {@value #COVER} blocks around them. That height
 * costs a few milliseconds per column in Terrahaul's tall world, so a column whose terrain model
 * surface lies more than {@value #MODEL_SLACK} blocks above what the structure needs is not
 * sampled: the noise surface hardly ever lies that far below the model's (in 2 of 135,469 columns
 * sampled around deep oceans on one seed, by at most 38 blocks), and where it does, the structure
 * only keeps a few blocks less cover there. Over a deep sea floor every column is sampled, which
 * made a trial chamber about 0.2 s and a mineshaft about 0.5 s slower to generate on a busy
 * four-core machine (see TESTING.md).
 * <p>
 * The tag holds trial chambers, ancient cities, strongholds and (ordinary) mineshafts; data packs
 * can add their own underground structures to it. Badlands mineshafts are left out: vanilla places
 * them between sea level and the badlands surface on purpose, to break out of the ground, and
 * their pieces are not built where they touch water. Nothing changes outside Terrahaul worlds.
 */
public final class UndergroundStructures {
    /** Structures that must lie fully underground in Terrahaul worlds. */
    public static final TagKey<Structure> KEEP_UNDERGROUND = TagKey.create(Registries.STRUCTURE, Terrahaul.id("keep_underground"));
    /** Rock between a piece and the terrain surface over it or around it, in blocks. */
    public static final int COVER = Beardifier.BEARD_KERNEL_RADIUS;
    /** Lowest a moved structure may reach, in blocks above the world bottom. */
    public static final int BOTTOM_MARGIN = 16;
    /** Spacing of the sampled columns, in blocks. */
    private static final int STEP = 8;
    /**
     * How far the noise terrain's surface may lie below the terrain model's 2D surface (its 3D
     * detail recesses cliff faces and notches rims). Columns whose model surface is higher than
     * the structure needs by more than this are not sampled.
     */
    private static final int MODEL_SLACK = 32;

    private UndergroundStructures() {
    }

    /** True if the structure is in {@link #KEEP_UNDERGROUND}. */
    public static boolean keepsUnderground(RegistryAccess registryAccess, Structure structure) {
        return registryAccess.registry(Registries.STRUCTURE)
            .map(registry -> registry.wrapAsHolder(structure).is(KEEP_UNDERGROUND))
            .orElse(false);
    }

    /**
     * Moves the pieces of a structure start down so that they lie {@link #COVER} blocks under the
     * terrain, if they do not already; does nothing outside Terrahaul worlds.
     *
     * @return how far the pieces were moved (zero or negative)
     */
    public static int bury(List<StructurePiece> pieces, ChunkGenerator generator, RandomState randomState, LevelHeightAccessor heightAccessor) {
        if (pieces.isEmpty() || !TerrahaulGeneration.isTerrahaul(generator)) {
            return 0;
        }
        int dy = requiredShift(pieces, generator, randomState, heightAccessor);
        if (dy >= 0) {
            return 0;
        }
        BoundingBox all = StructurePiece.createBoundingBox(pieces.stream());
        dy = Math.max(dy, heightAccessor.getMinBuildHeight() + BOTTOM_MARGIN - all.minY());
        if (dy >= 0) {
            return 0;
        }
        for (StructurePiece piece : pieces) {
            move(piece, dy);
        }
        return dy;
    }

    /**
     * The (negative) shift that gives every piece its cover: the smallest, over the sampled columns,
     * of the terrain height there minus {@link #COVER} minus the highest piece top within
     * {@link #COVER} blocks of the column.
     * <p>
     * The columns are visited from the one whose model surface lies least above the pieces, and the
     * visit stops at the first whose model surface is {@link #MODEL_SLACK} blocks higher than the
     * shift found so far needs: none after it can need more.
     */
    private static int requiredShift(List<StructurePiece> pieces, ChunkGenerator generator, RandomState randomState, LevelHeightAccessor heightAccessor) {
        BoundingBox all = StructurePiece.createBoundingBox(pieces.stream());
        TerrainModel model = TerrahaulGeneration.model(randomState);
        int x0 = Math.floorDiv(all.minX() - COVER, STEP) * STEP;
        int z0 = Math.floorDiv(all.minZ() - COVER, STEP) * STEP;
        // per column: x, z, the level the terrain must reach, and the model surface minus that level
        List<int[]> columns = new ArrayList<>();
        for (int x = x0; x <= all.maxX() + COVER; x += STEP) {
            for (int z = z0; z <= all.maxZ() + COVER; z += STEP) {
                int top = Integer.MIN_VALUE;
                for (StructurePiece piece : pieces) {
                    BoundingBox b = piece.getBoundingBox();
                    if (x >= b.minX() - COVER && x <= b.maxX() + COVER && z >= b.minZ() - COVER && z <= b.maxZ() + COVER) {
                        top = Math.max(top, b.maxY());
                    }
                }
                if (top != Integer.MIN_VALUE) {
                    int needed = top + COVER;
                    int margin = model == null ? Integer.MIN_VALUE : (int) Math.floor(model.sample(x, z).height) - needed;
                    columns.add(new int[] {x, z, needed, margin});
                }
            }
        }
        columns.sort(Comparator.comparingInt(c -> c[3]));
        int shift = 0;
        for (int[] c : columns) {
            if (model != null && c[3] - MODEL_SLACK >= shift) {
                break;
            }
            int ground = generator.getFirstOccupiedHeight(c[0], c[1], Heightmap.Types.OCEAN_FLOOR_WG, heightAccessor, randomState);
            shift = Math.min(shift, ground - c[2]);
        }
        return shift;
    }

    /**
     * Moves one piece vertically. A jigsaw piece also records the junctions to its neighbours,
     * which the terrain adaptation reads and {@link StructurePiece#move} does not move.
     */
    private static void move(StructurePiece piece, int dy) {
        piece.move(0, dy, 0);
        if (piece instanceof PoolElementStructurePiece pool) {
            pool.getJunctions().replaceAll(j -> new JigsawJunction(j.getSourceX(), j.getSourceGroundY() + dy, j.getSourceZ(), j.getDeltaY(),
                j.getDestProjection()));
        }
    }
}
