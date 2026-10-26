package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import com.terrahaul.blocks.GroundDecorBlock;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.BigDripleafStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The last feature of every chunk's decoration: removes the plants of the chunk that cannot stay
 * where they are, the ones that would drop off at the first block update next to them (a petal
 * mat or a tuft of grass hanging in the air over a stream, a flower on bare rock, the top half of
 * tall grass whose foot was replaced).
 * <p>
 * Why a net is needed besides fixing the features that undermine plants: a chunk's features may
 * write into the 3x3 chunk area around it, and vanilla's flower, grass and petal patches spread
 * up to seven blocks from their centre, so a neighbour decorated earlier plants on this chunk's
 * ground before this chunk's own features have shaped it (dug its streams, filled its pools,
 * built its banks and rocks). Each Terrahaul step that lowers or replaces ground clears the plants
 * it undermines (the surface water, trees, boulders, fallen logs, ruins), but any feature placed
 * on ground that changes later, other mods' and vanilla ones included, can leave a plant behind,
 * and one missed case shows as a plant floating in mid-air. This pass runs after every other
 * feature of the chunk (it is listed after the water wall sealer in the same biome modifier, in
 * the last step, {@code top_layer_modification}), so the chunk's own terrain is final by then.
 * What a neighbouring chunk's features change here afterwards is that feature's to clear: a
 * vanilla feature that does not (an ice spike under a snow layer, a sand disk under grass) can
 * still leave a plant that cannot stay.
 * <p>
 * Only plant-like blocks are touched (see {@link #isPlant}): bushes, flowers, grass, ferns,
 * petals, saplings and other {@link BushBlock}s, both halves of double plants, snow layers, moss
 * carpets, vines, glow lichen, hanging roots, sugar cane, cactus, bamboo, kelp and cave vines,
 * dripleaf, coral, and Terrahaul's ground decorations. Terrain, logs, leaves and structure blocks
 * (crops, carpets of wool, candles, torches) are never touched. A plant is removed when {@link
 * BlockState#canSurvive} is false, as vanilla decides when a neighbour changes, or when it is the
 * lower half of a double plant without its upper half. Mushrooms only need ground: their light
 * test is left out, since a chunk is not lit while it is decorated. A removed plant leaves the
 * fluid it held (water for seagrass or a waterlogged pebble), otherwise air.
 * <p>
 * It is cheap (a fraction of a millisecond per chunk): only the sections whose palettes hold a
 * plant are scanned, from the bottom up, so that a plant standing on a removed one (the top half
 * of tall grass, a bamboo stalk) is judged after it. It reads only the blocks of the chunk and
 * next to it, writes only the chunk's own, and its result depends on the blocks alone. Like the
 * trees and ground details it runs in every Overworld-like world.
 */
public final class UnsupportedPlantCleanup extends Feature<NoneFeatureConfiguration> {
    /** Development switch ({@code -Dterrahaul.debug.noPlantCleanup=true}): skip this pass, to measure what reaches it. */
    private static final boolean DISABLED = Boolean.getBoolean("terrahaul.debug.noPlantCleanup");
    /** Time spent in this pass, chunks passed and plants removed, for the development report. */
    public static final java.util.concurrent.atomic.LongAdder NANOS = new java.util.concurrent.atomic.LongAdder();
    public static final java.util.concurrent.atomic.LongAdder CHUNKS = new java.util.concurrent.atomic.LongAdder();
    public static final java.util.concurrent.atomic.LongAdder REMOVED = new java.util.concurrent.atomic.LongAdder();

    public UnsupportedPlantCleanup(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (DISABLED) {
            return false;
        }
        long t0 = System.nanoTime();
        boolean changed = scan(context.level(), context.origin());
        NANOS.add(System.nanoTime() - t0);
        CHUNKS.increment();
        return changed;
    }

    private static boolean scan(WorldGenLevel level, BlockPos origin) {
        ChunkAccess chunk = level.getChunk(origin);
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        LevelChunkSection[] sections = chunk.getSections();
        Set<Block> plants = plants();
        java.util.function.Predicate<BlockState> plant = s -> plants.contains(s.getBlock());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(plant)) {
                continue;
            }
            int y0 = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (plants.contains(state.getBlock()) && !stays(state, level, pos.set(x0 + x, y0 + y, z0 + z))) {
                            remove(level, pos, state);
                            REMOVED.increment();
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Removes the plants of one column from {@code fromY} up that cannot stay, for a feature that
     * has reshaped the column from {@code fromY} to {@code toY} after a neighbouring chunk may have
     * planted on it; above {@code toY} only a stack whose foot went (a cane, a bamboo stalk).
     */
    public static boolean clearColumn(LevelAccessor level, BlockPos.MutableBlockPos pos, int x, int fromY, int toY, int z) {
        boolean changed = false;
        boolean removed = false;
        for (int y = fromY; y <= toY || removed; y++) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            removed = isPlant(state) && !stays(state, level, pos);
            if (removed) {
                remove(level, pos, state);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Removes the plants on and around a block a feature has just replaced: those standing on it
     * that can no longer stay, and the lower half of a double plant below it whose upper half it
     * was. Leaves {@code pos} at the block.
     */
    public static void clearAround(LevelAccessor level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        BlockState below = level.getBlockState(pos.set(x, y - 1, z));
        if (isPlant(below) && !stays(below, level, pos)) {
            remove(level, pos, below);
        }
        for (int yy = y + 1; ; yy++) {
            BlockState state = level.getBlockState(pos.set(x, yy, z));
            if (!isPlant(state) || stays(state, level, pos)) {
                break;
            }
            level.setBlock(pos, state.getFluidState().createLegacyBlock(), 2);
        }
        pos.set(x, y, z);
    }

    /**
     * Removes a plant, then the plants hanging from it or standing under it that cannot stay
     * without it (cave vines, the lower half of a double plant); those standing on it are met
     * later by the upward scan. Leaves {@code pos} where it was.
     */
    private static void remove(LevelAccessor level, BlockPos.MutableBlockPos pos, BlockState state) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 2);
        for (int yy = y - 1; ; yy--) {
            BlockState below = level.getBlockState(pos.set(x, yy, z));
            if (!isPlant(below) || stays(below, level, pos)) {
                break;
            }
            level.setBlock(pos, below.getFluidState().createLegacyBlock(), 2);
        }
        pos.set(x, y, z);
    }

    /** Whether a plant may stay at {@code pos} (see the class description). */
    public static boolean stays(BlockState state, LevelReader level, BlockPos pos) {
        Block block = state.getBlock();
        if (block instanceof MushroomBlock) {
            BlockPos below = pos.below();
            BlockState ground = level.getBlockState(below);
            return ground.is(BlockTags.MUSHROOM_GROW_BLOCK) || ground.isSolidRender(level, below);
        }
        if (block instanceof DoublePlantBlock && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            BlockState above = level.getBlockState(pos.above());
            if (!above.is(block) || above.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.UPPER) {
                return false;
            }
        }
        return state.canSurvive(level, pos);
    }

    /** Whether the block is one this cleanup may remove (see the class description). */
    public static boolean isPlant(BlockState state) {
        return plants().contains(state.getBlock());
    }

    private static volatile Set<Block> plants;

    private static Set<Block> plants() {
        Set<Block> set = plants;
        if (set == null) {
            ReferenceOpenHashSet<Block> found = new ReferenceOpenHashSet<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (plantBlock(block)) {
                    found.add(block);
                }
            }
            plants = set = found;
        }
        return set;
    }

    private static boolean plantBlock(Block block) {
        if (block instanceof BushBlock) {
            // crops, stems and nether wart grow on farms and depend on light
            return !(block instanceof CropBlock || block instanceof StemBlock || block instanceof NetherWartBlock);
        }
        return block instanceof SnowLayerBlock || block instanceof GroundDecorBlock || block instanceof VineBlock
            || block instanceof SugarCaneBlock || block instanceof CactusBlock || block instanceof BambooStalkBlock
            || block instanceof BambooSaplingBlock || block instanceof GrowingPlantBlock
            || block instanceof BigDripleafBlock || block instanceof BigDripleafStemBlock || block instanceof BaseCoralPlantTypeBlock
            || block == Blocks.MOSS_CARPET || block == Blocks.GLOW_LICHEN || block == Blocks.HANGING_ROOTS || block == Blocks.SPORE_BLOSSOM;
    }
}
