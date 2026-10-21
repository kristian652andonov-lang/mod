package com.terrahaul.worldgen.ruins;

import com.mojang.serialization.Codec;
import com.terrahaul.blocks.GroundDecorBlock;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Builds the trail ruins (broken houses, watchtowers, wells, wagons, shrines, campsites, field
 * walls, bridge abutments and chapels), signposts, lamp posts and sunken ruins planned by
 * {@link RuinSites}.
 * <p>
 * Every overworld chunk runs this feature once, in the surface-structures step. A chunk only
 * looks at the trail cell and the sea cell containing it (sites never cross their cell) and
 * writes only its own 16x16 columns: houses and sunken ruins span several chunks, each of which
 * builds its part from the same cached plan; signposts and lamp posts were placed so that each
 * lies wholly inside one chunk. Ground heights are read only in the chunk's own columns, so the
 * result never depends on the order in which chunks are generated.
 * <p>
 * Building a plan column: the ground is found below any trees, plants and snow; foundation
 * columns are filled from it up to the floor course (at most {@link RuinPlan#MAX_FILL} blocks);
 * then the cells are written, leaving tree trunks, block entities and bedrock alone; finally
 * decals are laid on the ground, each only where something solid carries it. Containers get
 * vanilla loot tables, brushable blocks archaeology loot, and signs their text.
 */
public final class RuinsFeature extends Feature<NoneFeatureConfiguration> {
    public RuinsFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model == null) {
            return false;
        }
        RuinSites sites = RuinSites.of(model);
        RuinWorldFilter.ensure(level.getLevel(), sites);
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX();
        int z0 = chunk.getMinBlockZ();
        int x1 = chunk.getMaxBlockX();
        int z1 = chunk.getMaxBlockZ();
        boolean placed = false;
        TrailSurvey.Cell cell = sites.trailCell(Math.floorDiv(x0, RuinSites.TRAIL_CELL), Math.floorDiv(z0, RuinSites.TRAIL_CELL));
        for (TrailRuin ruin : cell.ruins()) {
            if (ruin.overlaps(x0, z0, x1, z1) && sites.allowed(ruin)) {
                placed |= stamp(level, sites.plan(ruin), chunk);
            }
        }
        for (Signpost s : cell.signposts()) {
            if (inChunk(chunk, s.x, s.z) && sites.allowed(s)) {
                placed |= stamp(level, SignpostPlanner.plan(sites, s), chunk);
            }
        }
        for (LightStretch lights : cell.lights()) {
            if (lights.overlaps(x0, z0, x1, z1) && sites.allowed(lights)) {
                for (LightStretch.Post post : lights.posts) {
                    if (inChunk(chunk, post.x(), post.z())) {
                        placed |= stamp(level, LampPostPlanner.plan(post), chunk);
                    }
                }
            }
        }
        SunkenRuin sunken = sites.sunken(Math.floorDiv(x0, RuinSites.SUNKEN_CELL), Math.floorDiv(z0, RuinSites.SUNKEN_CELL));
        if (sunken != null && sunken.overlaps(x0, z0, x1, z1) && sites.allowed(sunken)) {
            placed |= stamp(level, sites.plan(sunken), chunk);
        }
        return placed;
    }

    private static boolean inChunk(ChunkPos chunk, int x, int z) {
        return (x >> 4) == chunk.x && (z >> 4) == chunk.z;
    }

    /** Builds the part of a plan that lies in the chunk's columns. */
    private static boolean stamp(WorldGenLevel level, RuinPlan p, ChunkPos chunk) {
        int x0 = Math.max(chunk.getMinBlockX(), p.minX);
        int x1 = Math.min(chunk.getMaxBlockX(), p.maxX());
        int z0 = Math.max(chunk.getMinBlockZ(), p.minZ);
        int z1 = Math.min(chunk.getMaxBlockZ(), p.maxZ());
        if (x0 > x1 || z0 > z1) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // read the ground of every column before writing anything
        int[][] ground = new int[16][16];
        BlockState[][] floor = new BlockState[16][16];
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int g = ground(level, pos, x, z);
                ground[x & 15][z & 15] = g;
                floor[x & 15][z & 15] = p.underwater ? sediment(level.getBlockState(pos.set(x, g, z))) : null;
            }
        }
        boolean grouped = p.mode == RuinPlan.GROUPED;
        int anchor = grouped ? ground(level, pos, p.anchorX, p.anchorZ) : 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int g = ground[x & 15][z & 15];
                BlockState sed = floor[x & 15][z & 15];
                if (!grouped) {
                    int fill = p.fill(x, z);
                    if (fill != 0) {
                        for (int y = Math.max(g + 1, p.baseY - RuinPlan.MAX_FILL); y < p.baseY; y++) {
                            put(level, pos.set(x, y, z), fill, p, sed, false);
                        }
                    }
                }
                for (int y = p.minY; y <= p.maxY(); y++) {
                    int c = p.get(x, y, z);
                    if (c != 0) {
                        // grouped posts never cut into the ground beside them
                        put(level, pos.set(x, grouped ? anchor + y : y, z), c, p, sed, grouped && y > 0);
                    }
                }
            }
        }
        List<RuinPlan.Decal> decals = new ArrayList<>(p.decals);
        decals.sort(Comparator.comparingInt(RuinPlan.Decal::dy));
        for (RuinPlan.Decal d : decals) {
            if (d.x() < x0 || d.x() > x1 || d.z() < z0 || d.z() > z1) {
                continue;
            }
            decal(level, pos, d, ground[d.x() & 15][d.z() & 15], p, floor[d.x() & 15][d.z() & 15]);
        }
        for (RuinPlan.Extra e : p.extras) {
            if (e.x() < x0 || e.x() > x1 || e.z() < z0 || e.z() > z1) {
                continue;
            }
            int y = e.groundRelative() ? ground[e.x() & 15][e.z() & 15] + e.y() : grouped ? anchor + e.y() : e.y();
            extra(level, pos.set(e.x(), y, e.z()), e);
        }
        return true;
    }

    /**
     * Y of the top block of the terrain: the first block below the surface heightmap that is
     * neither a tree, a plant, snow nor one of the ground decorations.
     */
    private static int ground(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
        for (int i = 0; i < 40 && y > level.getMinBuildHeight(); i++) {
            BlockState s = level.getBlockState(pos.set(x, y, z));
            if (isGround(s)) {
                return y;
            }
            y--;
        }
        return y;
    }

    private static boolean isGround(BlockState s) {
        return !s.isAir() && s.blocksMotion() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && !s.canBeReplaced()
            && !s.is(Blocks.BAMBOO) && !(s.getBlock() instanceof GroundDecorBlock) && !s.is(Blocks.CACTUS);
    }

    /** What may be replaced by soft cells and decals: air, water, plants, leaves, snow, decorations. */
    private static boolean replaceable(BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(Blocks.WATER) || s.getBlock() instanceof GroundDecorBlock
            || s.is(Blocks.SNOW) || !s.getFluidState().isEmpty() && s.getFluidState().is(FluidTags.WATER) && !s.blocksMotion();
    }

    /** Top blocks a footpath, paving or a buried find may replace. */
    private static boolean soilLike(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(Blocks.DIRT_PATH) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY)
            || s.is(Blocks.SNOW_BLOCK);
    }

    /** The loose floor material sunken ruins are buried in: the column's own, or sand. */
    private static BlockState sediment(BlockState top) {
        if (top.is(Blocks.SAND) || top.is(Blocks.RED_SAND) || top.is(Blocks.GRAVEL) || top.is(Blocks.CLAY) || top.is(Blocks.MUD)) {
            return top.getBlock().defaultBlockState();
        }
        return Blocks.SAND.defaultBlockState();
    }

    private static void put(WorldGenLevel level, BlockPos pos, int cell, RuinPlan p, BlockState sediment, boolean forceSoft) {
        BlockState existing = level.getBlockState(pos);
        if (existing.is(Blocks.BEDROCK) || existing.hasBlockEntity()) {
            return;
        }
        RuinPart part = RuinCell.part(cell);
        boolean soft = forceSoft || RuinCell.soft(cell);
        if (soft && !replaceable(existing)) {
            return;
        }
        // a neighbour's tree trunk stays whole rather than leaving its crown hanging in the air
        if (existing.is(BlockTags.LOGS) && !soft) {
            return;
        }
        // never break coral that a reef already grew on the sea floor
        if (p.underwater && (existing.is(BlockTags.CORALS) || existing.is(BlockTags.CORAL_BLOCKS) || existing.is(BlockTags.WALL_CORALS))) {
            return;
        }
        boolean wet = p.underwater || existing.getFluidState().is(FluidTags.WATER);
        BlockState state;
        if (part == RuinPart.AIR) {
            state = wet ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            if (existing.isAir() || wet && existing.is(Blocks.WATER)) {
                return;
            }
        } else {
            state = RuinPalette.state(cell, p.materials, sediment);
            if (state == null) {
                return;
            }
            if (wet && state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                state = state.setValue(BlockStateProperties.WATERLOGGED, true);
            }
        }
        level.setBlock(pos, state, 2);
        if (RuinPalette.shaped(part)) {
            level.getChunk(pos).markPosForPostprocessing(pos);
        }
    }

    /** A cell laid on the ground: dy 0 replaces the top block if it is soil, above it needs support. */
    private static void decal(WorldGenLevel level, BlockPos.MutableBlockPos pos, RuinPlan.Decal d, int g, RuinPlan p, BlockState sediment) {
        int x = d.x();
        int z = d.z();
        int y = g + d.dy();
        RuinPart part = RuinCell.part(d.cell());
        BlockState state = RuinPalette.state(d.cell(), p.materials, sediment);
        if (state == null) {
            return;
        }
        if (d.dy() <= 0) {
            BlockState existing = level.getBlockState(pos.set(x, y, z));
            if (!soilLike(existing) && !(p.underwater && existing.is(BlockTags.BASE_STONE_OVERWORLD))) {
                return;
            }
            level.setBlock(pos, state, 2);
            return;
        }
        BlockState below = level.getBlockState(pos.set(x, y - 1, z));
        if (!below.isSolid() || below.is(BlockTags.LEAVES)) {
            return;
        }
        BlockState existing = level.getBlockState(pos.set(x, y, z));
        if (!replaceable(existing) || existing.hasBlockEntity()) {
            return;
        }
        boolean wet = p.underwater || existing.getFluidState().is(FluidTags.WATER);
        if (wet && state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, true);
        }
        if (wet && !state.hasProperty(BlockStateProperties.WATERLOGGED) && !state.isSolid() && !p.underwater) {
            // land decor does not go into a puddle or a stream
            return;
        }
        level.setBlock(pos, state, 2);
        if (RuinPalette.shaped(part)) {
            level.getChunk(pos).markPosForPostprocessing(pos);
        }
    }

    /** Loot for containers and brushable blocks, text for signs. */
    private static void extra(WorldGenLevel level, BlockPos pos, RuinPlan.Extra e) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        if (e.sign() != null && be instanceof SignBlockEntity sign) {
            // SignBlockEntity#setText needs a level, which block entities in generating chunks lack;
            // loading the text from NBT works anywhere
            RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            CompoundTag tag = new CompoundTag();
            tag.put("front_text", SignText.DIRECT_CODEC.encodeStart(ops, text(e.sign().front(), e.sign())).getOrThrow());
            tag.put("back_text", SignText.DIRECT_CODEC.encodeStart(ops, text(e.sign().back(), e.sign())).getOrThrow());
            tag.putBoolean("is_waxed", true);
            sign.loadWithComponents(tag, level.registryAccess());
        } else if (e.lootTable() != null) {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(e.lootTable()));
            if (be instanceof RandomizableContainer container) {
                container.setLootTable(key, e.seed());
            } else if (be instanceof BrushableBlockEntity brushable) {
                brushable.setLootTable(key, e.seed());
            }
        }
    }

    private static SignText text(String[] lines, RuinPlan.SignSpec spec) {
        SignText t = new SignText();
        for (int i = 0; i < 4 && i < lines.length; i++) {
            t = t.setMessage(i, component(lines[i]));
        }
        return t.setColor(spec.white() ? DyeColor.WHITE : DyeColor.BLACK).setHasGlowingText(spec.glowing());
    }

    /** {@code @key|Fallback} becomes a translatable line with an English fallback, anything else literal text. */
    private static Component component(String line) {
        if (line.startsWith("@")) {
            int bar = line.indexOf('|');
            if (bar > 1) {
                return Component.translatableWithFallback("sign.terrahaul." + line.substring(1, bar), line.substring(bar + 1));
            }
        }
        return Component.literal(line);
    }
}
