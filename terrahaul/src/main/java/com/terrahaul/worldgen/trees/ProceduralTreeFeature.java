package com.terrahaul.worldgen.trees;

import com.mojang.serialization.Codec;
import com.terrahaul.config.TerrahaulConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.material.Fluids;

/**
 * Feature {@code terrahaul:procedural_tree}: builds a tree with {@link TreeShapes} and places it.
 * <p>
 * Placement is conservative: the tree is skipped (or retried one size smaller) when the trunk is
 * obstructed or too much of the crown would cut into terrain. Wood obstructs a trunk, with one
 * exception in world generation: in a closed stand the straight stem of a conifer grows up through
 * the limbs of the trees around it (which stay attached, because the stem is wood too), but never
 * through anything in the {@link #ROOT_ZONE} (root flares, the feet of trunks, fallen logs, rubble)
 * or resting on the ground, and never up against another trunk: its foot keeps a block clear of
 * every other stem. On slopes the trunk's foot may sink into the uphill soil (the outer part of a
 * conifer's thick foot deeper, like a root flare) and is extended down to solid ground on the
 * downhill side, and roots are laid relative to the ground of each column, so nothing floats.
 * Every leaf gets the exact vanilla {@code distance} value and leaves out of reach of wood are
 * dropped, so canopies do not decay.
 * <p>
 * A tree grown from a player's sapling ({@link ProceduralTreeConfiguration#sapling()}) is placed
 * like a vanilla tree: it replaces only air, leaves, water and plants. Its stem passes no wood, its
 * foot sinks into nothing, and its roots are laid on the ground rather than into it, so a planter's
 * border, a path or a beam next to the sapling stays as it is.
 * <p>
 * Writes stay within the blueprint's reach (15 blocks; 18 for giant pines, whose trunk is kept
 * three blocks inside its chunk; 22 for ancient trees, whose trunk is moved to the middle of its
 * chunk), plus one block for vines hanging beside the outermost leaves: inside the 3x3 chunk area a
 * feature may write to.
 */
public final class ProceduralTreeFeature extends Feature<ProceduralTreeConfiguration> {
    private static final int SET_FLAGS = Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE;
    /** Trunk and root blocks this far above the trunk base may sink into the soil of a slope. */
    private static final int BURIED_BASE = 2;
    /**
     * The lowest layers of a stem, where trees have their root flares, buttresses and the flared feet
     * of their trunks, and where fallen logs and the rubble of ruins lie: a stem never grows through
     * wood here, and a conifer in a stand keeps its foot a block clear of every other trunk here.
     */
    private static final int ROOT_ZONE = 4;

    public ProceduralTreeFeature(Codec<ProceduralTreeConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<ProceduralTreeConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        ProceduralTreeConfiguration config = context.config();
        TreeSpecies species = config.species();
        BlockPos origin = context.origin();

        if (species == TreeSpecies.MANGROVE) {
            // mangroves may stand in shallow water: grow from the bottom
            int depth = 0;
            while (depth < 4 && !level.getFluidState(origin.below()).isEmpty()) {
                origin = origin.below();
                depth++;
            }
        }
        BlockState ground = level.getBlockState(origin.below());
        if (!TreeMaterials.canGrowOn(species, ground)) {
            return false;
        }
        TreeSize size = config.sizes().getRandomValue(random).orElse(TreeSize.MEDIUM);
        size = adjustForAltitude(level, origin, species, size);
        double sizeBias = TerrahaulConfig.treeSizeVariation();
        if (sizeBias < 1.0 && size.ordinal() > TreeSize.MEDIUM.ordinal() && random.nextDouble() > sizeBias) {
            size = TreeSize.MEDIUM;
        }

        if (size == TreeSize.ANCIENT) {
            // ancient trees reach further than 15 blocks: move the trunk to the middle of its chunk,
            // where up to TreeBlueprint.ANCIENT_REACH blocks stay inside the writable area
            // (chunk-local 7 - 22 = -15 and 8 + 22 = 30, plus one for vines: within -16..31)
            int cx = (origin.getX() & ~15) + 7 + random.nextInt(2);
            int cz = (origin.getZ() & ~15) + 7 + random.nextInt(2);
            BlockPos moved = moveTo(level, species, origin, cx, cz);
            if (moved != null) {
                origin = moved;
            } else {
                size = TreeSize.GIANT;
            }
        }
        if (size != TreeSize.ANCIENT && TreeShapes.reach(species, size) > TreeBlueprint.MAX_REACH) {
            // a wide crown (giant pines): squeeze the trunk's chunk-local position from 0..15 into
            // 3..12, a move of at most three blocks, where up to TreeBlueprint.WIDE_REACH blocks stay
            // inside the writable area (3 - 18 = -15 and 12 + 18 = 30, plus one for vines)
            int cx = (origin.getX() & ~15) + 3 + (origin.getX() & 15) * 10 / 16;
            int cz = (origin.getZ() & ~15) + 3 + (origin.getZ() & 15) * 10 / 16;
            BlockPos moved = cx == origin.getX() && cz == origin.getZ() ? origin : moveTo(level, species, origin, cx, cz);
            if (moved != null) {
                origin = moved;
            } else {
                size = size.smaller();
            }
        }
        // the shapes use plain Java randomness, seeded from the feature's own random source
        SplittableRandom shapeRandom = new SplittableRandom(random.nextLong());
        // conifers in a stand grow through their neighbours' limbs (see fits), saplings never
        boolean sapling = config.sapling();
        boolean throughBranches = !sapling && species.isConifer();
        for (int attempt = 0; attempt < 2; attempt++) {
            TreeBlueprint bp = TreeShapes.create(species, size, shapeRandom);
            if (fits(level, origin, bp, sapling, throughBranches)) {
                placeBlueprint(level, origin, bp, config, random, sapling, throughBranches);
                return true;
            }
            if (size == TreeSize.YOUNG) {
                break;
            }
            // a smaller tree keeps within the normal reach, even when it was moved for an ancient one
            size = size.smaller();
        }
        return false;
    }

    /**
     * The ground at chunk column {@code (x, z)} for a trunk moved there from {@code origin}, or
     * {@code null} when it lies more than three blocks higher or lower, under fluid or on ground the
     * species cannot grow on.
     */
    private static BlockPos moveTo(WorldGenLevel level, TreeSpecies species, BlockPos origin, int x, int z) {
        BlockPos moved = new BlockPos(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z), z);
        if (Math.abs(moved.getY() - origin.getY()) <= 3 && level.getFluidState(moved).isEmpty()
            && TreeMaterials.canGrowOn(species, level.getBlockState(moved.below()))) {
            return moved;
        }
        return null;
    }

    /**
     * Trees shrink towards the tree line ({@link TreeLine#cap}); krummholz is the stunted form
     * itself and keeps its size above it.
     */
    private static TreeSize adjustForAltitude(WorldGenLevel level, BlockPos pos, TreeSpecies species, TreeSize size) {
        return species == TreeSpecies.KRUMMHOLZ ? size : TreeLine.cap(level, pos, size);
    }

    private static boolean isFree(BlockState state) {
        return state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.LEAVES)
            || state.is(Blocks.WATER) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.REPLACEABLE);
    }

    /** An upright log (not a bark block, which stands upright too): a trunk, a stump or the upswept end of a limb. */
    private static boolean isUpright(BlockState state) {
        return state.is(BlockTags.LOGS) && !TreeMaterials.isWoodBlock(state) && state.hasProperty(BlockStateProperties.AXIS)
            && state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
    }

    /**
     * Whether one of the eight blocks around {@code pos} in its layer holds an upright log. Blocks of
     * the new tree's own stem at that layer ({@code (x, y, z)} in blueprint coordinates) are skipped:
     * they are checked themselves, and while the tree is placed they hold its own trunk.
     */
    private static boolean besideUpright(WorldGenLevel level, BlockPos pos, TreeBlueprint bp, int x, int y, int z) {
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0 || bp.isTrunkBase(x + dx, z + dz) && TreeBlueprint.isWoody(bp.get(x + dx, y, z + dz))) {
                    continue;
                }
                if (isUpright(level.getBlockState(n.setWithOffset(pos, dx, 0, dz)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A neighbour's limb that a conifer's stem may grow through: a tree's log lying on its side (not
     * a stripped one) or a bark block, from {@link #ROOT_ZONE} above the stem's base up, held up by
     * air, leaves or wood rather than the ground, with no upright log beside it. Lower down lie root
     * flares, the feet of trunks, fallen logs and rubble; wood on the ground or on a wall (a fallen
     * log uphill, a ruin's beam) is no limb; an upright log may be another trunk, whose taper steps
     * and limb junctions are bark blocks too, so a stem never ends up against it.
     */
    private static boolean isLimb(WorldGenLevel level, BlockPos pos, BlockState state, TreeBlueprint bp, int x, int y, int z) {
        if (y < ROOT_ZONE) {
            return false;
        }
        boolean limb = TreeMaterials.isWoodBlock(state) || TreeMaterials.isTreeLog(state)
            && state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y;
        if (!limb) {
            return false;
        }
        BlockState below = level.getBlockState(pos.below());
        return (isFree(below) || below.is(BlockTags.LOGS)) && !besideUpright(level, pos, bp, x, y, z);
    }

    /**
     * Whether the foot of a conifer's stem would stand against another trunk: an upright log in the
     * {@link #ROOT_ZONE} next to or diagonally next to the stem's footprint (the flared foot of a
     * thick trunk is upright logs too, with bark only on top). Conifers in a stand keep at least a
     * block between their feet, so their stems never fuse; a neighbour's ground roots may run
     * beside them.
     */
    private static boolean againstTrunk(WorldGenLevel level, BlockPos origin, TreeBlueprint bp) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean[] hit = {false};
        bp.forEachTrunkBase((x, z, unused) -> {
            for (int dx = -1; dx <= 1 && !hit[0]; dx++) {
                for (int dz = -1; dz <= 1 && !hit[0]; dz++) {
                    for (int y = 0; y < ROOT_ZONE && !hit[0]; y++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (bp.isTrunkBase(nx, nz) && TreeBlueprint.isWoody(bp.get(nx, y, nz))) {
                            continue;
                        }
                        pos.set(origin.getX() + nx, origin.getY() + y, origin.getZ() + nz);
                        hit[0] = isUpright(level.getBlockState(pos));
                    }
                }
            }
        });
        return hit[0];
    }

    /** The centre of the trunk footprint, in blueprint coordinates. */
    private static double[] footCentre(TreeBlueprint bp) {
        double[] sum = new double[3];
        bp.forEachTrunkBase((x, z, unused) -> {
            sum[0] += x;
            sum[1] += z;
            sum[2]++;
        });
        return sum[2] == 0 ? new double[]{0.0, 0.0} : new double[]{sum[0] / sum[2], sum[1] / sum[2]};
    }

    /**
     * Whether a block of the tree at layer {@code y} of column {@code (x, z)} may sink into the
     * existing ground block {@code state} on a slope. Any block up to {@link #BURIED_BASE} above the
     * trunk base may sink into soil. The outer part of a conifer's foot wider than a 2x2 trunk sinks
     * further, 2.5 blocks more per block beyond one from the centre of the foot (a little steeper than
     * the steepest slope trees grow on), and into rock as well as soil: like a root flare it
     * disappears into the uphill side, so thick old conifers still fit on the slopes their forests
     * grow on. A cliff still stops the trunk. Broadleaf trees keep the shallow rule, so on hills
     * their biggest trees still give way to smaller ones.
     */
    private static boolean sinks(TreeBlueprint bp, double[] centre, int x, int y, int z, BlockState state) {
        if (y <= BURIED_BASE && isSoil(state)) {
            return true;
        }
        double d = Math.hypot(x - centre[0], z - centre[1]);
        if (!bp.species().isConifer() || !bp.isTrunkBase(x, z) || d <= 1.0) {
            return false;
        }
        return y <= BURIED_BASE + Math.ceil(2.5 * (d - 1.0)) && (isSoil(state) || state.is(BlockTags.BASE_STONE_OVERWORLD));
    }

    /** Loose ground that a trunk foot or a root may displace. */
    private static boolean isSoil(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
            || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
    }

    /**
     * The lower trunk: footprint columns below this layer must be free (or, for a conifer in a
     * stand, hold a neighbour's limb); above it the stem may meet other trees' wood, like branches.
     */
    private static int trunkLimit(TreeBlueprint bp) {
        return Math.max(2, (int) (bp.height() * 0.6));
    }

    private static boolean fits(WorldGenLevel level, BlockPos origin, TreeBlueprint bp, boolean sapling, boolean throughBranches) {
        if (throughBranches && againstTrunk(level, origin, bp)) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int trunkLimit = trunkLimit(bp);
        double[] centre = footCentre(bp);
        int maxBuild = level.getMaxBuildHeight();
        int[] counts = new int[3]; // checked, blocked, sampled leaves
        boolean[] ok = {true};
        bp.forEachCell((x, y, z, code) -> {
            if (!ok[0] || y < 0) {
                return;
            }
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (pos.getY() >= maxBuild) {
                ok[0] = false;
                return;
            }
            if (TreeBlueprint.isLeaf(code)) {
                if ((counts[2]++ & 3) != 0) {
                    return;
                }
                counts[0]++;
                if (!isFree(level.getBlockState(pos))) {
                    counts[1]++;
                }
                return;
            }
            BlockState state = level.getBlockState(pos);
            boolean trunk = bp.isTrunkBase(x, z) && y < trunkLimit;
            if (!sapling && sinks(bp, centre, x, y, z, state)) {
                return; // the foot of the trunk sinks into a slope
            }
            // branches may meet other trees' wood; a conifer's stem only their limbs
            boolean free = isFree(state) || (trunk ? throughBranches && isLimb(level, pos, state, bp, x, y, z) : state.is(BlockTags.LOGS));
            if (!free && trunk) {
                ok[0] = false;
                return;
            }
            counts[0]++;
            if (!free) {
                counts[1]++;
            }
        });
        // krummholz lies on the ground: on a slope its mat runs into the uphill ground, and the
        // needles there are left out
        double blocked = bp.species() == TreeSpecies.KRUMMHOLZ ? 0.45 : 0.15;
        return ok[0] && (counts[0] == 0 || counts[1] <= counts[0] * blocked);
    }

    private static void placeBlueprint(WorldGenLevel level, BlockPos origin, TreeBlueprint bp, ProceduralTreeConfiguration config, RandomSource random,
                                       boolean sapling, boolean throughBranches) {
        TreeSpecies species = bp.species();
        bp.thinLeaves(config.dryLeaves());
        bp.finish();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double[] centre = footCentre(bp);

        // the neighbours' limbs the stem grows through, chosen as the fit test saw the world, before
        // the tree's own wood stands beside them
        LongOpenHashSet through = new LongOpenHashSet();
        if (throughBranches) {
            int trunkLimit = trunkLimit(bp);
            bp.forEachTrunkBase((x, z, unused) -> {
                for (int y = ROOT_ZONE; y < trunkLimit; y++) {
                    if (TreeBlueprint.isWoody(bp.get(x, y, z))) {
                        pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        if (isLimb(level, pos, level.getBlockState(pos), bp, x, y, z)) {
                            through.add(pos.asLong());
                        }
                    }
                }
            });
        }

        // read the ground under the roots before anything is written
        LongArrayList rootColumns = new LongArrayList();
        bp.forEachGroundRoot((x, z, rise) -> {
            int ground = findGround(level, pos, origin.getX() + x, origin.getY(), origin.getZ() + z);
            if (ground != Integer.MIN_VALUE) {
                rootColumns.add(BlockPos.asLong(x, ground, z));
            }
        });

        // anchor the trunk footprint on uneven ground
        bp.forEachTrunkBase((x, z, unused) -> {
            int wx = origin.getX() + x;
            int wz = origin.getZ() + z;
            for (int y = origin.getY() - 1; y > origin.getY() - 9; y--) {
                pos.set(wx, y, wz);
                BlockState state = level.getBlockState(pos);
                if (!isFree(state)) {
                    if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL)) {
                        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), SET_FLAGS);
                    }
                    break;
                }
                level.setBlock(pos, TreeMaterials.log(species, Direction.Axis.Y), SET_FLAGS);
            }
        });

        // wood, prop roots and rooted dirt
        bp.forEachCell((x, y, z, code) -> {
            if (TreeBlueprint.isLeaf(code)) {
                return;
            }
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            BlockState existing = level.getBlockState(pos);
            if (code == TreeBlueprint.ROOTED_DIRT) {
                if (!sapling && existing.is(BlockTags.DIRT) && !existing.is(Blocks.MUD)) {
                    level.setBlock(pos, Blocks.ROOTED_DIRT.defaultBlockState(), SET_FLAGS);
                }
                return;
            }
            if (code == TreeBlueprint.PROP_ROOT) {
                placePropRoot(level, pos, existing, y, sapling);
                return;
            }
            boolean sunk = !sapling && isSoil(existing) && sinks(bp, centre, x, y, z, existing);
            if (isFree(existing) || sunk || through.contains(pos.asLong())) {
                level.setBlock(pos, TreeMaterials.woody(species, code), SET_FLAGS);
                if (y <= BURIED_BASE || sunk) {
                    // soil under a buried foot is no longer lit
                    BlockPos below = pos.below();
                    if (level.getBlockState(below).is(Blocks.GRASS_BLOCK)) {
                        level.setBlock(below, Blocks.DIRT.defaultBlockState(), SET_FLAGS);
                    }
                }
            }
        });

        placeGroundRoots(level, origin, bp, rootColumns, sapling);

        // leaves with exact distances
        BlockState leaf = TreeMaterials.leaves(species);
        BlockState alt = TreeMaterials.altLeaves(species);
        LongArrayList placedLeaves = new LongArrayList();
        bp.forEachCell((x, y, z, code) -> {
            if (!TreeBlueprint.isLeaf(code)) {
                return;
            }
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            BlockState existing = level.getBlockState(pos);
            if (!isFree(existing) || existing.is(BlockTags.LOGS)) {
                return;
            }
            int d = Math.max(1, Math.min(6, bp.distance(x, y, z)));
            BlockState state = (code == TreeBlueprint.ALT_LEAF ? alt : leaf)
                .setValue(LeavesBlock.DISTANCE, d)
                .setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.WATERLOGGED, existing.getFluidState().is(Fluids.WATER));
            level.setBlock(pos, state, SET_FLAGS);
            placedLeaves.add(pos.asLong());
        });
        decorate(level, origin, bp, config, random, placedLeaves);
    }

    /**
     * Mangrove roots: they replace mud as muddy mangrove roots (not those of a sapling's tree, which
     * stop on the mud), and the lowest ones continue down through water and air until they reach the
     * ground.
     */
    private static void placePropRoot(WorldGenLevel level, BlockPos.MutableBlockPos pos, BlockState existing, int y, boolean sapling) {
        BlockState root = Blocks.MANGROVE_ROOTS.defaultBlockState();
        if (existing.is(Blocks.MUD)) {
            if (sapling) {
                return;
            }
            level.setBlock(pos, Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(), SET_FLAGS);
            return;
        }
        if (!isFree(existing)) {
            return;
        }
        level.setBlock(pos, root.trySetValue(BlockStateProperties.WATERLOGGED, existing.getFluidState().is(Fluids.WATER)), SET_FLAGS);
        if (y > 0) {
            return;
        }
        int x = pos.getX();
        int z = pos.getZ();
        for (int yy = pos.getY() - 1; yy > pos.getY() - 8; yy--) {
            pos.set(x, yy, z);
            BlockState below = level.getBlockState(pos);
            if (below.is(Blocks.MUD)) {
                if (!sapling) {
                    level.setBlock(pos, Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(), SET_FLAGS);
                }
                break;
            }
            if (!isFree(below)) {
                break;
            }
            level.setBlock(pos, root.trySetValue(BlockStateProperties.WATERLOGGED, below.getFluidState().is(Fluids.WATER)), SET_FLAGS);
        }
    }

    /**
     * The ground of a root column: the first solid block scanning down from 6 above to 6 below the
     * trunk base; {@code Integer.MIN_VALUE} when there is none or the column holds another tree's
     * wood.
     */
    private static int findGround(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int baseY, int z) {
        for (int y = baseY + 6; y >= baseY - 6; y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.is(BlockTags.LOGS)) {
                return Integer.MIN_VALUE;
            }
            if (!isFree(state)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Roots that follow the ground: each column's root rises the planned height above that
     * column's own ground (so roots run down slopes with it), replacing the soil block itself; roots
     * just below the ground show as rooted dirt. A sapling's tree lays its roots on the ground only:
     * the parts at and below the surface are left out, the ground stays as it was.
     */
    private static void placeGroundRoots(WorldGenLevel level, BlockPos origin, TreeBlueprint bp, LongArrayList columns, boolean sapling) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState wood = TreeMaterials.wood(bp.species());
        for (int i = 0; i < columns.size(); i++) {
            long k = columns.getLong(i);
            int rx = BlockPos.getX(k);
            int rz = BlockPos.getZ(k);
            int ground = BlockPos.getY(k);
            double rise = bp.groundRise(rx, rz);
            int x = origin.getX() + rx;
            int z = origin.getZ() + rz;
            BlockState soil = level.getBlockState(pos.set(x, ground, z));
            if (rise < 0.0) {
                if (!sapling && rise > -1.2 && soil.is(BlockTags.DIRT) && !soil.is(Blocks.MUD)) {
                    level.setBlock(pos, Blocks.ROOTED_DIRT.defaultBlockState(), SET_FLAGS);
                }
                continue;
            }
            boolean earthy = isSoil(soil) || soil.is(Blocks.MUD);
            if (!earthy && !soil.is(BlockTags.BASE_STONE_OVERWORLD)) {
                continue;
            }
            int top = ground + (int) Math.floor(rise);
            for (int y = sapling ? ground + 1 : ground; y <= top; y++) {
                pos.set(x, y, z);
                BlockState existing = level.getBlockState(pos);
                if (y == ground || isFree(existing)) {
                    level.setBlock(pos, wood, SET_FLAGS);
                }
            }
            if (sapling) {
                continue;
            }
            // the soil under a root is no longer lit: no grass beneath the wood
            BlockState below = level.getBlockState(pos.set(x, ground - 1, z));
            if (below.is(Blocks.GRASS_BLOCK)) {
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), SET_FLAGS);
            }
        }
    }

    /** Longest vine curtain a species grows (weeping swamp oaks and jungle giants trail long ones). */
    private static int maxVineLength(TreeSpecies species) {
        return switch (species) {
            case SWAMP_OAK -> 9;
            case JUNGLE -> 7;
            default -> 4;
        };
    }

    private static void decorate(WorldGenLevel level, BlockPos origin, TreeBlueprint bp, ProceduralTreeConfiguration config, RandomSource random,
                                 LongArrayList placedLeaves) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        TreeSpecies species = bp.species();
        if (config.vines() > 0) {
            int maxLen = maxVineLength(species);
            for (int i = 0; i < placedLeaves.size(); i++) {
                long k = placedLeaves.getLong(i);
                if (random.nextFloat() >= config.vines()) {
                    continue;
                }
                Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                pos.set(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k)).move(dir);
                if (!level.isEmptyBlock(pos)) {
                    continue;
                }
                BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir.getOpposite()), true);
                int length = 1 + random.nextInt(2 + random.nextInt(maxLen));
                for (int j = 0; j < length && level.isEmptyBlock(pos); j++) {
                    level.setBlock(pos, vine, SET_FLAGS);
                    pos.move(Direction.DOWN);
                }
            }
            if (species == TreeSpecies.JUNGLE || species == TreeSpecies.SWAMP_OAK) {
                // vines climbing the trunk
                trunkSides(bp, (x, y, z, dir) -> {
                    if (random.nextFloat() < config.vines() * 0.6f) {
                        pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z).move(dir);
                        if (level.isEmptyBlock(pos)) {
                            level.setBlock(pos, Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir.getOpposite()), true), SET_FLAGS);
                        }
                    }
                });
            }
        }
        if (species == TreeSpecies.MANGROVE) {
            for (int i = 0; i < placedLeaves.size(); i++) {
                long k = placedLeaves.getLong(i);
                if (random.nextFloat() < 0.12f) {
                    pos.set(BlockPos.getX(k), BlockPos.getY(k) - 1, BlockPos.getZ(k));
                    if (level.isEmptyBlock(pos)) {
                        level.setBlock(pos, Blocks.MANGROVE_PROPAGULE.defaultBlockState()
                            .setValue(MangrovePropaguleBlock.HANGING, true)
                            .setValue(MangrovePropaguleBlock.AGE, random.nextInt(5)), SET_FLAGS);
                    }
                }
            }
        }
        boolean hive = config.beehive() > 0 && random.nextFloat() < config.beehive();
        if (config.cocoa() > 0 || hive) {
            boolean[] hivePlaced = {false};
            trunkSides(bp, (x, y, z, dir) -> {
                if (y < 2) {
                    return;
                }
                pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z).move(dir);
                if (!level.isEmptyBlock(pos)) {
                    return;
                }
                if (hive && !hivePlaced[0] && y <= 6 && random.nextFloat() < 0.3f) {
                    level.setBlock(pos, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING, dir), SET_FLAGS);
                    level.getBlockEntity(pos, BlockEntityType.BEEHIVE).ifPresent(be -> {
                        int bees = 2 + random.nextInt(2);
                        for (int i = 0; i < bees; i++) {
                            be.storeBee(BeehiveBlockEntity.Occupant.create(random.nextInt(599)));
                        }
                    });
                    hivePlaced[0] = true;
                } else if (config.cocoa() > 0 && species == TreeSpecies.JUNGLE && random.nextFloat() < config.cocoa()) {
                    level.setBlock(pos, Blocks.COCOA.defaultBlockState()
                        .setValue(CocoaBlock.FACING, dir.getOpposite())
                        .setValue(CocoaBlock.AGE, random.nextInt(3)), SET_FLAGS);
                }
            });
        }
    }

    @FunctionalInterface
    private interface SideVisitor {
        void visit(int x, int y, int z, Direction outward);
    }

    /**
     * Visits the outward faces of the lower trunk: vertical trunk logs above the footprint whose
     * horizontal neighbour is not part of the tree.
     */
    private static void trunkSides(TreeBlueprint bp, SideVisitor visitor) {
        int top = Math.max(3, bp.height() - 3);
        bp.forEachTrunkBase((x, z, unused) -> {
            for (int y = 1; y < top; y++) {
                int code = bp.get(x, y, z);
                if (!TreeBlueprint.isWoody(code)) {
                    break;
                }
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    if (bp.get(x + dir.getStepX(), y, z + dir.getStepZ()) == TreeBlueprint.EMPTY) {
                        visitor.visit(x, y, z, dir);
                    }
                }
            }
        });
    }
}
