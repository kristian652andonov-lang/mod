package com.terrahaul.worldgen.surface;

import com.mojang.serialization.MapCodec;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.LakeType;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * Material rule {@code terrahaul:terrain_surface}: surface materials that follow Terrahaul's
 * landforms. It runs before the vanilla overworld surface rule and only answers for the blocks it
 * is responsible for (returning {@code null} otherwise), so the vanilla biome surfaces (grass,
 * sand, snow, badlands bands, ...) remain the default everywhere else.
 * <ul>
 *   <li>cliffs, canyon walls and plateau escarpments expose the rock of the local geological
 *       province, banded into strata;</li>
 *   <li>mountains show their province rock near the surface and scree on steep alpine slopes;</li>
 *   <li>river, lake and canyon beds get gravel, sand, clay or mud depending on climate;</li>
 *   <li>volcanoes are covered with basalt, blackstone, tuff and ash-grey gravel;</li>
 *   <li>forest floors get coarse dirt, podzol, moss and rooted dirt patches;</li>
 *   <li>wetland margins turn to mud.</li>
 * </ul>
 */
public final class TerrainSurfaceRule implements SurfaceRules.RuleSource {
    public static final TerrainSurfaceRule INSTANCE = new TerrainSurfaceRule();
    public static final MapCodec<TerrainSurfaceRule> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final KeyDispatchDataCodec<TerrainSurfaceRule> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private TerrainSurfaceRule() {
    }

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        TerrainModel model = TerrahaulGeneration.model(context.randomState);
        if (model == null) {
            return (x, y, z) -> null;
        }
        return new Rule(context, model);
    }

    private static final class Rule implements SurfaceRules.SurfaceRule {
        private static final BlockState STONE = Blocks.STONE.defaultBlockState();
        private static final BlockState[][] STRATA = {
            // TEMPERATE
            {STONE, STONE, Blocks.ANDESITE.defaultBlockState(), STONE, STONE, Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState()},
            // GRANITE
            {Blocks.GRANITE.defaultBlockState(), Blocks.GRANITE.defaultBlockState(), STONE, Blocks.GRANITE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState()},
            // DARK
            {Blocks.DEEPSLATE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(), Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState()},
            // LIMESTONE
            {Blocks.CALCITE.defaultBlockState(), Blocks.DIORITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), STONE, Blocks.DIORITE.defaultBlockState()},
            // SEDIMENTARY (temperate climates)
            {Blocks.SANDSTONE.defaultBlockState(), STONE, Blocks.TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
                Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState()},
            // VOLCANIC
            {Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.SMOOTH_BASALT.defaultBlockState()},
        };
        private static final BlockState[] HOT_SEDIMENTARY = {
            Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(),
            Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState()};

        private final SurfaceRules.Context ctx;
        private final TerrainModel model;
        private final int seaLevel;

        private int lastX = Integer.MIN_VALUE;
        private int lastZ = Integer.MIN_VALUE;
        private final ColumnSample col = new ColumnSample();
        private double height;
        private int slope;

        Rule(SurfaceRules.Context ctx, TerrainModel model) {
            this.ctx = ctx;
            this.model = model;
            this.seaLevel = model.seaLevel();
        }

        private void updateColumn(int x, int z) {
            lastX = x;
            lastZ = z;
            // categorical fields come from the nearest interpolation corner, the height is interpolated
            col.copyFrom(model.sample((x + 2) & ~3, (z + 2) & ~3));
            height = model.interpolatedHeight(x, z);
            int lx = x & 15;
            int lz = z & 15;
            int h = ctx.chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz);
            int max = 0;
            for (int i = -2; i <= 2; i += 4) {
                int ax = Math.max(0, Math.min(15, lx + i));
                int az = Math.max(0, Math.min(15, lz + i));
                max = Math.max(max, Math.abs(ctx.chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, ax, lz) - h));
                max = Math.max(max, Math.abs(ctx.chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, az) - h));
            }
            slope = max;
        }

        private double patch(int x, int z, double scale, int channel) {
            return model.detail3(channel, x / scale, channel * 17.0, z / scale);
        }

        @Override
        public BlockState tryApply(int x, int y, int z) {
            int depth = ctx.stoneDepthAbove;
            if (depth > 64) {
                return null;
            }
            if (x != lastX || z != lastZ) {
                updateColumn(x, z);
            }
            if (y < height - 72.0) {
                return null;
            }
            boolean underFluid = ctx.waterHeight != Integer.MIN_VALUE;
            boolean planned = col.waterLevel != ColumnSample.NO_WATER && y < col.waterLevel;

            // ---- beds of rivers and lakes --------------------------------------------------------
            if (depth <= 3 && (col.river > 0.0 || col.lake > 0.0 || planned) && (underFluid || planned || y < seaLevel)) {
                return bedMaterial(x, y, z, depth);
            }
            // ---- volcanoes ----------------------------------------------------------------------
            if (col.volcano > 0.3) {
                BlockState v = volcanic(x, y, z, depth);
                if (v != null) {
                    return v;
                }
            }
            // ---- cliffs, canyon walls and escarpments --------------------------------------------
            boolean steep = slope >= 6 || (slope >= 4 && col.cliff > 0.3);
            if (steep && depth <= 6 || col.cliff > 0.35 && depth <= 48 && slope >= 3) {
                return strata(x, y, z);
            }
            if (col.canyon > 0.25 && depth <= 4 && y <= col.canyonFloor + 4.0) {
                return canyonFloor(x, z, depth);
            }
            // ---- mountains: province rock near the surface, scree on steep slopes ----------------
            if (col.mountain > 0.4 && col.geology != Geology.TEMPERATE) {
                if (depth > 1 && depth <= 14 && patch(x + y * 3, z, 9.0, 1) > -0.2) {
                    return strata(x, y, z);
                }
            }
            if (col.mountain > 0.3 && slope >= 3 && depth <= 2 && !underFluid && col.peakness > 0.35) {
                double n = patch(x, z, 7.0, 2);
                if (n > 0.15) {
                    return n > 0.5 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                }
                if (n < -0.45) {
                    return Blocks.ANDESITE.defaultBlockState();
                }
            }
            // ---- coasts: pebbly beaches under rocky cliffs ---------------------------------------
            if (col.coastCliff > 0.3 && depth <= 3 && y <= seaLevel + 2 && y >= seaLevel - 6) {
                return patch(x, z, 5.0, 3) > 0.0 ? Blocks.GRAVEL.defaultBlockState() : STONE;
            }
            // ---- river banks: gravel bars and sandbars --------------------------------------------
            if (col.riverValley > 0.85 && col.river <= 0.0 && depth <= 2 && y <= col.riverLevel + 2.0 && !underFluid) {
                double n = patch(x, z, 6.0, 0);
                if (n > 0.35) {
                    return depth == 1 && n > 0.6 ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                }
            }
            // ---- wetland margins --------------------------------------------------------------
            if (col.swamp > 0.4 && depth <= 2 && y >= seaLevel - 3 && y <= seaLevel + 1) {
                return Blocks.MUD.defaultBlockState();
            }
            // ---- forest floors ----------------------------------------------------------------
            if (depth == 1 && !underFluid && slope < 4) {
                return forestFloor(x, z);
            }
            return null;
        }

        private BlockState strata(int x, int y, int z) {
            int geology = col.geology;
            double offset = col.strata * 37.0 + patch(x, z, 60.0, 1) * 3.0;
            int band = (int) Math.floor((y + offset) / (geology == Geology.SEDIMENTARY ? 3.0 : 5.0));
            BlockState[] palette = STRATA[Math.max(0, Math.min(STRATA.length - 1, geology))];
            if (geology == Geology.SEDIMENTARY && col.baseTemperature > 0.45) {
                palette = HOT_SEDIMENTARY;
            }
            return palette[Math.floorMod(band, palette.length)];
        }

        private BlockState bedMaterial(int x, int y, int z, int depth) {
            double n = patch(x, z, 5.0, 0);
            double hot = col.baseTemperature;
            double wet = col.humidity;
            if (col.lake > 0.0) {
                switch (col.lakeType) {
                    case LakeType.MOUNTAIN, LakeType.GLACIAL, LakeType.CRATER -> {
                        return n > 0.2 ? Blocks.GRAVEL.defaultBlockState() : (n < -0.4 ? STONE : Blocks.COBBLESTONE.defaultBlockState());
                    }
                    case LakeType.MARSH -> {
                        return depth == 1 && n > 0.5 ? Blocks.CLAY.defaultBlockState() : Blocks.MUD.defaultBlockState();
                    }
                    case LakeType.GREAT -> {
                        return n > -0.3 ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                    }
                    default -> {
                        if (n > 0.55) {
                            return Blocks.CLAY.defaultBlockState();
                        }
                        return n > 0.0 ? Blocks.SAND.defaultBlockState() : (wet > 0.3 ? Blocks.MUD.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                    }
                }
            }
            if (col.mountain > 0.3) {
                return n > -0.1 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
            }
            if (col.dryRiver) {
                return hot > 0.55 ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
            }
            if (hot > 0.5 && wet > 0.3 && n > -0.2) {
                return Blocks.MUD.defaultBlockState();
            }
            if (n > 0.6 && depth == 1) {
                return Blocks.CLAY.defaultBlockState();
            }
            return n > -0.15 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
        }

        private BlockState volcanic(int x, int y, int z, int depth) {
            double n = patch(x, z, 8.0, 2);
            if (col.crater > 0.0 && depth <= 2 && n > 0.45) {
                return Blocks.MAGMA_BLOCK.defaultBlockState();
            }
            if (depth <= 3) {
                if (n > 0.3) {
                    return Blocks.BASALT.defaultBlockState();
                }
                if (n > -0.1) {
                    return Blocks.BLACKSTONE.defaultBlockState();
                }
                if (n > -0.45) {
                    return Blocks.TUFF.defaultBlockState();
                }
                return Blocks.GRAVEL.defaultBlockState();
            }
            if (depth <= 24) {
                return strata(x, y, z);
            }
            return null;
        }

        private BlockState canyonFloor(int x, int z, int depth) {
            double n = patch(x, z, 9.0, 3);
            if (col.baseTemperature > 0.45) {
                return n > -0.3 ? Blocks.RED_SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
            }
            if (depth == 1 && n < -0.2) {
                return null;
            }
            return n > 0.2 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        }

        private BlockState forestFloor(int x, int z) {
            double n = patch(x, z, 11.0, 1);
            if (n < 0.42 && n > -0.55) {
                return null;
            }
            Holder<Biome> biome = ctx.biome.get();
            if (biome.is(BiomeTags.IS_TAIGA) && !biome.is(Biomes.SNOWY_TAIGA)) {
                return n > 0.42 ? Blocks.PODZOL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
            }
            if (biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
                return n > 0.55 ? Blocks.MOSS_BLOCK.defaultBlockState() : (n > 0.42 ? Blocks.ROOTED_DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
            }
            if (biome.is(BiomeTags.IS_FOREST) || biome.is(Biomes.FLOWER_FOREST)) {
                return n > 0.6 && col.humidity > 0.1 ? Blocks.MOSS_BLOCK.defaultBlockState() : (n < -0.55 ? Blocks.COARSE_DIRT.defaultBlockState() : null);
            }
            if (biome.is(BiomeTags.IS_JUNGLE)) {
                return n > 0.5 ? Blocks.MOSS_BLOCK.defaultBlockState() : null;
            }
            if (biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU) || biome.is(Biomes.WINDSWEPT_SAVANNA)) {
                return n < -0.6 ? Blocks.COARSE_DIRT.defaultBlockState() : null;
            }
            return null;
        }
    }
}
