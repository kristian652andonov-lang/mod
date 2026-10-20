package com.terrahaul.worldgen.water;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * Materials of everything the water features build: banks copy the soil of the column they
 * stand in, rapids and sills are rock suited to the climate (mossy where it is humid, plain stone
 * where it is cold, basalt on volcanoes), stream and pool beds are gravel, sand or clay. Choices
 * vary by coherent noise over several blocks, never block by block, so nothing looks speckled.
 * One instance per thread is reused for every column.
 */
final class WaterPalette {
    private static final ThreadLocal<WaterPalette> INSTANCE = ThreadLocal.withInitial(WaterPalette::new);

    private TerrainModel model;
    private BlockState top;
    private double humidity;
    private double temperature;
    private double mountain;
    private int geology;
    private double patch;

    private WaterPalette() {
    }

    /** The palette for column {@code (x, z)}, whose original surface block is {@code top}. */
    static WaterPalette at(TerrainModel model, int x, int z, BlockState top) {
        WaterPalette p = INSTANCE.get();
        ColumnSample s = model.sample((x + 2) & ~3, (z + 2) & ~3);
        p.model = model;
        p.top = top;
        p.humidity = s.humidity;
        p.temperature = s.temperature;
        p.mountain = s.mountain;
        p.geology = s.geology;
        p.patch = model.detail3(0, x / 9.0, 5.0, z / 9.0);
        return p;
    }

    /** Ground a water feature may carve away: rock, soil, sand, snow, ice and plants, never wood or anything built. */
    static boolean natural(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) {
            return true;
        }
        if (s.hasBlockEntity() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) {
            return false;
        }
        return s.canBeReplaced() || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(BlockTags.DIRT)
            || s.is(BlockTags.SAND) || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE) || s.is(Tags.Blocks.ORES)
            || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.SANDSTONE)
            || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.SMOOTH_SANDSTONE) || s.is(Blocks.SMOOTH_RED_SANDSTONE) || s.is(Blocks.CUT_SANDSTONE)
            || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.BASALT) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.OBSIDIAN)
            || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.COBBLED_DEEPSLATE) || s.is(Blocks.PACKED_MUD);
    }

    /** Loose soil that should give way to a proper bed under water. */
    static boolean soil(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SNOW);
    }

    boolean isGrass(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MYCELIUM) || s.is(Blocks.PODZOL) || s.is(Blocks.DIRT_PATH);
    }

    private boolean volcanic() {
        return geology == Geology.VOLCANIC;
    }

    private boolean cold() {
        return temperature < -0.25;
    }

    private boolean humid() {
        return humidity > 0.2 && temperature > -0.2;
    }

    /** Rock of rapids, sills and lips. */
    BlockState sill(int x, int y, int z, boolean lava) {
        if (lava || volcanic()) {
            return Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        if (cold()) {
            return Blocks.STONE.defaultBlockState();
        }
        double n = model.detail3(1, x / 7.0, y / 5.0, z / 7.0);
        if (humid()) {
            return n > 0.3 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        return n > 0.2 ? Blocks.STONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
    }

    BlockState boulder(int x, int z) {
        if (volcanic()) {
            return Blocks.BASALT.defaultBlockState();
        }
        if (humid()) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        return cold() || patch > 0.0 ? Blocks.STONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
    }

    /** A bank raised to hold water: the column's own soil, grass on top where there was grass. */
    BlockState bank(boolean isTop, boolean dam) {
        if (dam) {
            return isTop && humid() ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
        }
        if (isGrass(top) || top.is(BlockTags.DIRT)) {
            return isTop && isGrass(top) ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        }
        if (top.is(BlockTags.SAND) || top.is(Blocks.GRAVEL) || top.is(Blocks.CLAY) || top.is(Blocks.MUD)) {
            return top;
        }
        if (top.is(BlockTags.SNOW)) {
            return isTop ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
        }
        if (top.is(BlockTags.BASE_STONE_OVERWORLD) || top.is(BlockTags.TERRACOTTA) || top.is(Blocks.SANDSTONE) || top.is(Blocks.RED_SANDSTONE)
            || top.is(Blocks.CALCITE) || top.is(Blocks.SMOOTH_BASALT) || top.is(Blocks.BLACKSTONE) || top.is(Blocks.BASALT)) {
            return top;
        }
        return Blocks.STONE.defaultBlockState();
    }

    /** Bed of a stream, a dug pool or a plunge basin. */
    BlockState bed(int x, int y, int z) {
        if (volcanic()) {
            return patch > 0.2 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        if (mountain > 0.3 || cold()) {
            return patch > 0.45 ? Blocks.STONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        }
        if (humid() && temperature > 0.45) {
            return patch > 0.3 ? Blocks.CLAY.defaultBlockState() : Blocks.MUD.defaultBlockState();
        }
        return patch > 0.35 ? Blocks.CLAY.defaultBlockState() : patch > -0.1 ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
    }

    /** Rock closing caves and overhang hollows that would otherwise let water escape. */
    BlockState seal(boolean lava) {
        return lava || volcanic() ? Blocks.BASALT.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    /**
     * Rock of the steps of a planned cascade or fall: travertine (calcite) in limestone country
     * and humid warm forests, mossy stone in other forests, plain stone in the cold, basalt on
     * volcanoes.
     */
    BlockState planSill(byte material, int x, int y, int z, boolean isTop) {
        return switch (material) {
            case FallPlan.MAT_TRAVERTINE -> isTop && humid() && model.detail3(1, x / 4.0, y / 4.0, z / 4.0) > 0.0
                ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
            case FallPlan.MAT_COLD -> Blocks.STONE.defaultBlockState();
            case FallPlan.MAT_VOLCANIC -> Blocks.SMOOTH_BASALT.defaultBlockState();
            default -> isTop ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        };
    }

    /**
     * Rock of a cascade: the outcrops of its hollow and the boulders on its banks and in its
     * water. In forests mossy cobblestone boulders and stone outcrops, both mostly mossy on top;
     * calcite in travertine country, bare stone in the cold, basalt on volcanoes. In patches a
     * few blocks across.
     */
    BlockState cascadeRock(byte material, boolean boulder, int x, int y, int z, boolean isTop) {
        double n = model.detail3(1, x / 3.5, y / 3.5 + 40.0, z / 3.5);
        return switch (material) {
            case FallPlan.MAT_TRAVERTINE -> isTop && humid() && n > -0.1 ? Blocks.MOSS_BLOCK.defaultBlockState()
                : n > 0.3 && !boulder ? Blocks.STONE.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
            case FallPlan.MAT_COLD -> n > 0.25 ? Blocks.ANDESITE.defaultBlockState() : n > -0.2 || !boulder ? Blocks.STONE.defaultBlockState()
                : Blocks.COBBLESTONE.defaultBlockState();
            case FallPlan.MAT_VOLCANIC -> n > 0.0 ? Blocks.BASALT.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
            default -> {
                if (boulder) {
                    yield isTop && n > -0.2 ? Blocks.MOSS_BLOCK.defaultBlockState()
                        : n > -0.15 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                }
                yield isTop && n > 0.05 ? Blocks.MOSS_BLOCK.defaultBlockState() : n > 0.2 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                    : n > -0.25 ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
            }
        };
    }

    /** Bed of a planned pool: light sand, calcite and clay under travertine cascades (turquoise water). */
    BlockState planBed(byte material, int x, int y, int z) {
        if (material == FallPlan.MAT_TRAVERTINE) {
            return patch > 0.3 ? Blocks.CALCITE.defaultBlockState() : patch > -0.2 ? Blocks.SAND.defaultBlockState() : Blocks.CLAY.defaultBlockState();
        }
        if (material == FallPlan.MAT_VOLCANIC) {
            return Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        return patch > 0.0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
    }

    /** Ground a plan shaped: beds, rock, shores, dams, ribs and boulders. */
    BlockState shaped(byte role, byte material, boolean planned, int x, int y, int z, boolean isTop, boolean wet) {
        return switch (role) {
            case WaterSolver.ROLE_BED -> isTop ? (planned ? planBed(material, x, y, z) : bed(x, y, z)) : Blocks.STONE.defaultBlockState();
            case WaterSolver.ROLE_ROCK -> rock(x, y, z);
            case WaterSolver.ROLE_RIB -> planSill(material, x, y, z, false);
            case WaterSolver.ROLE_DAM -> planSill(material, x, y, z, isTop && !wet);
            case WaterSolver.ROLE_BOULDER -> cascadeRock(material, true, x, y, z, isTop);
            case WaterSolver.ROLE_SHORE -> isTop ? (wet ? bed(x, y, z) : shoreTop()) : Blocks.DIRT.defaultBlockState();
            default -> isTop ? (wet ? bed(x, y, z) : bank(true, false)) : bank(false, false);
        };
    }

    private BlockState shoreTop() {
        if (cold()) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (humid()) {
            return patch > 0.25 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        return isGrass(top) ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
    }

    /** Bare rock of cliff faces cut by a plan: the local stone, in coherent bands. */
    BlockState rock(int x, int y, int z) {
        if (volcanic()) {
            return Blocks.BASALT.defaultBlockState();
        }
        if (top.is(BlockTags.BASE_STONE_OVERWORLD) || top.is(BlockTags.TERRACOTTA) || top.is(Blocks.SANDSTONE) || top.is(Blocks.RED_SANDSTONE)) {
            return top;
        }
        double n = model.detail3(2, x / 11.0, y / 4.0, z / 11.0);
        return n > 0.35 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }
}
