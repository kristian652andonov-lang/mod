package com.terrahaul.worldgen.trees;

import com.mojang.serialization.Codec;
import com.terrahaul.config.TerrahaulConfig;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
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
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.material.Fluids;

/**
 * Feature {@code terrahaul:procedural_tree}: builds a tree from {@link TreeShapes} and places it.
 * <p>
 * Placement is conservative: the tree is skipped (or retried one size smaller) when the trunk is
 * obstructed or too much of the crown would cut into terrain; trunks and roots are extended down
 * to solid ground on slopes so nothing floats; every leaf gets the exact vanilla {@code distance}
 * value and leaves that would be out of reach of a log are dropped, so canopies do not decay.
 */
public final class ProceduralTreeFeature extends Feature<ProceduralTreeConfiguration> {
    private static final int SET_FLAGS = Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE;

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
        if (!species.canGrowOn(ground)) {
            return false;
        }
        TreeSize size = config.sizes().getRandomValue(random).orElse(TreeSize.MEDIUM);
        size = adjustForAltitude(level, origin, size, species);
        double sizeBias = TerrahaulConfig.treeSizeVariation();
        if (sizeBias < 1.0 && size.ordinal() > TreeSize.MEDIUM.ordinal() && random.nextDouble() > sizeBias) {
            size = TreeSize.MEDIUM;
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            TreeBlueprint bp = new TreeBlueprint(species, random);
            TreeShapes.build(bp, size);
            if (fits(level, origin, bp)) {
                placeBlueprint(level, origin, bp, config, random);
                return true;
            }
            if (size == TreeSize.YOUNG) {
                break;
            }
            size = size.smaller();
        }
        return false;
    }

    private static TreeSize adjustForAltitude(WorldGenLevel level, BlockPos pos, TreeSize size, TreeSpecies species) {
        int treeline = TreeLine.treelineY(level, pos);
        int y = pos.getY();
        if (y > treeline - 40) {
            return TreeSize.YOUNG;
        }
        if (y > treeline - 110 && size.ordinal() > TreeSize.SMALL.ordinal()) {
            return TreeSize.SMALL;
        }
        return size;
    }

    private static boolean isFree(BlockState state) {
        return state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.LEAVES)
            || state.is(Blocks.WATER) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.REPLACEABLE);
    }

    private static boolean fits(WorldGenLevel level, BlockPos origin, TreeBlueprint bp) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int trunkLimit = Math.max(2, (int) (bp.height * 0.6));
        int blocked = 0;
        int checked = 0;
        for (var entry : bp.logs.long2ObjectEntrySet()) {
            long k = entry.getLongKey();
            int y = BlockPos.getY(k);
            if (y < 0) {
                continue;
            }
            pos.set(origin.getX() + BlockPos.getX(k), origin.getY() + y, origin.getZ() + BlockPos.getZ(k));
            if (pos.getY() >= level.getMaxBuildHeight()) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            boolean free = isFree(state) || state.is(BlockTags.LOGS);
            if (!free && y < trunkLimit && bp.trunkBase.contains(BlockPos.asLong(BlockPos.getX(k), 0, BlockPos.getZ(k)))) {
                return false;
            }
            checked++;
            if (!free) {
                blocked++;
            }
        }
        int sampled = 0;
        for (long k : bp.leaves) {
            if ((sampled++ & 3) != 0) {
                continue;
            }
            pos.set(origin.getX() + BlockPos.getX(k), origin.getY() + BlockPos.getY(k), origin.getZ() + BlockPos.getZ(k));
            checked++;
            if (!isFree(level.getBlockState(pos))) {
                blocked++;
            }
        }
        return checked == 0 || blocked <= checked * 0.15;
    }

    private static void placeBlueprint(WorldGenLevel level, BlockPos origin, TreeBlueprint bp, ProceduralTreeConfiguration config, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (config.dryLeaves() > 0) {
            bp.leaves.removeIf((long k) -> random.nextFloat() < config.dryLeaves());
        }
        Long2IntOpenHashMap distances = bp.leafDistances();

        // logs
        for (var entry : bp.logs.long2ObjectEntrySet()) {
            long k = entry.getLongKey();
            pos.set(origin.getX() + BlockPos.getX(k), origin.getY() + BlockPos.getY(k), origin.getZ() + BlockPos.getZ(k));
            BlockState existing = level.getBlockState(pos);
            if (isFree(existing) || BlockPos.getY(k) < 0 && (existing.is(BlockTags.DIRT) || existing.is(BlockTags.SAND))) {
                level.setBlock(pos, entry.getValue(), SET_FLAGS);
            }
        }
        // anchor the trunk footprint on uneven ground
        for (long k : bp.trunkBase) {
            int x = origin.getX() + BlockPos.getX(k);
            int z = origin.getZ() + BlockPos.getZ(k);
            for (int y = origin.getY() - 1; y > origin.getY() - 9; y--) {
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (!isFree(state)) {
                    if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL)) {
                        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), SET_FLAGS);
                    }
                    break;
                }
                level.setBlock(pos, bp.species().log(), SET_FLAGS);
            }
        }
        // roots (mangrove prop roots reach down through water to the mud; rooted dirt only replaces soil)
        for (var entry : bp.roots.long2ObjectEntrySet()) {
            long k = entry.getLongKey();
            int x = origin.getX() + BlockPos.getX(k);
            int y = origin.getY() + BlockPos.getY(k);
            int z = origin.getZ() + BlockPos.getZ(k);
            BlockState root = entry.getValue();
            if (root.is(Blocks.ROOTED_DIRT)) {
                pos.set(x, y, z);
                if (level.getBlockState(pos).is(BlockTags.DIRT)) {
                    level.setBlock(pos, root, SET_FLAGS);
                }
                continue;
            }
            pos.set(x, y, z);
            BlockState existing = level.getBlockState(pos);
            if (existing.is(Blocks.MUD)) {
                level.setBlock(pos, Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(), SET_FLAGS);
                continue;
            }
            if (!isFree(existing)) {
                continue;
            }
            level.setBlock(pos, root.trySetValue(BlockStateProperties.WATERLOGGED, existing.getFluidState().is(Fluids.WATER)), SET_FLAGS);
            if (BlockPos.getY(k) <= 0) {
                // root tips continue down through water and air until they reach the ground
                for (int yy = y - 1; yy > y - 8; yy--) {
                    pos.set(x, yy, z);
                    existing = level.getBlockState(pos);
                    if (existing.is(Blocks.MUD)) {
                        level.setBlock(pos, Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(), SET_FLAGS);
                        break;
                    }
                    if (!isFree(existing)) {
                        break;
                    }
                    level.setBlock(pos, root.trySetValue(BlockStateProperties.WATERLOGGED, existing.getFluidState().is(Fluids.WATER)), SET_FLAGS);
                }
            }
        }
        // leaves with exact distances
        BlockState leaf = bp.species().leaves();
        BlockState alt = bp.species().altLeaves();
        LongArrayList placedLeaves = new LongArrayList();
        for (long k : bp.leaves) {
            pos.set(origin.getX() + BlockPos.getX(k), origin.getY() + BlockPos.getY(k), origin.getZ() + BlockPos.getZ(k));
            BlockState existing = level.getBlockState(pos);
            if (!isFree(existing) || existing.is(BlockTags.LOGS)) {
                continue;
            }
            int d = Math.max(1, Math.min(6, distances.get(k)));
            BlockState state = (bp.altLeaves.contains(k) ? alt : leaf)
                .setValue(LeavesBlock.DISTANCE, d)
                .setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.WATERLOGGED, existing.getFluidState().is(Fluids.WATER));
            level.setBlock(pos, state, SET_FLAGS);
            placedLeaves.add(pos.asLong());
        }
        decorate(level, origin, bp, config, random, placedLeaves);
    }

    private static void decorate(WorldGenLevel level, BlockPos origin, TreeBlueprint bp, ProceduralTreeConfiguration config, RandomSource random,
                                 LongArrayList placedLeaves) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (config.vines() > 0) {
            for (long k : placedLeaves) {
                if (random.nextFloat() >= config.vines()) {
                    continue;
                }
                Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                pos.set(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k)).move(dir);
                if (!level.isEmptyBlock(pos)) {
                    continue;
                }
                BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir.getOpposite()), true);
                int length = 1 + random.nextInt(4);
                for (int i = 0; i < length && level.isEmptyBlock(pos); i++) {
                    level.setBlock(pos, vine, SET_FLAGS);
                    pos.move(Direction.DOWN);
                }
            }
        }
        if (bp.species() == TreeSpecies.MANGROVE) {
            for (long k : placedLeaves) {
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
            boolean hivePlaced = false;
            for (long k : bp.trunkBase) {
                for (int y = 2; y < Math.max(3, bp.height - 3); y++) {
                    int x = origin.getX() + BlockPos.getX(k);
                    int z = origin.getZ() + BlockPos.getZ(k);
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        pos.set(x, origin.getY() + y, z).move(dir);
                        if (!level.isEmptyBlock(pos)) {
                            continue;
                        }
                        if (hive && !hivePlaced && y >= 2 && y <= 6 && random.nextFloat() < 0.3f) {
                            level.setBlock(pos, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING, dir), SET_FLAGS);
                            level.getBlockEntity(pos, BlockEntityType.BEEHIVE).ifPresent(be -> {
                                int bees = 2 + random.nextInt(2);
                                for (int i = 0; i < bees; i++) {
                                    be.storeBee(BeehiveBlockEntity.Occupant.create(random.nextInt(599)));
                                }
                            });
                            hivePlaced = true;
                        } else if (config.cocoa() > 0 && random.nextFloat() < config.cocoa()) {
                            level.setBlock(pos, Blocks.COCOA.defaultBlockState()
                                .setValue(CocoaBlock.FACING, dir.getOpposite())
                                .setValue(CocoaBlock.AGE, random.nextInt(3)), SET_FLAGS);
                        }
                    }
                }
            }
        }
    }
}
