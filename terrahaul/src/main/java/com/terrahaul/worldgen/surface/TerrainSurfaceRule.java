package com.terrahaul.worldgen.surface;

import com.mojang.serialization.MapCodec;
import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.LakeType;
import com.terrahaul.worldgen.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * Material rule {@code terrahaul:terrain_surface}: surface materials that follow Terrahaul's
 * landforms, with gradual fades between materials.
 * <p>
 * The rule runs before the vanilla Overworld surface rule. The topmost block of ordinary ground
 * (grass, sand, podzol, ...) is mostly left to vanilla's biome rules; Terrahaul decides what lies
 * beneath, what covers special landforms, and where snow and bare rock meet on mountains.
 * <p>
 * <b>Fades stay within one colour.</b> Every gradient runs through a single family of similar
 * blocks, for example tuff, andesite and stone for grey rock, or calcite, diorite and stone below
 * snow. Boundaries between the stops of a gradient wander with smooth 3D noise, so they form
 * wavy bands and broad patches instead of speckles. Families:
 * <ul>
 *   <li>grey rock: tuff, andesite, stone (all mountains, temperate and granite provinces);</li>
 *   <li>dark rock: tuff, smooth basalt, deepslate;</li>
 *   <li>pale rock: calcite, diorite, stone (limestone provinces);</li>
 *   <li>sandstone and red sandstone (sedimentary lowlands; badlands keep vanilla's bands);</li>
 *   <li>volcanic rock: blackstone, basalt, smooth basalt;</li>
 *   <li>soil: dirt and coarse dirt over grey rock; snow over calcite and diorite; sand over
 *       sandstone.</li>
 * </ul>
 * Exposed rock shows slow, wavy strata cycling through its family. Snow and rock on peaks, and
 * grass and rock on steep hillsides, are separated by the slope of the smoothed terrain, so rock
 * outcrops and snowfields are coherent areas.
 * <p>
 * Fades never reach deeper than {@value #MAX_DEPTH} blocks below the nearest exposed surface.
 * That distance is measured both vertically and sideways, so cliff and mountain faces, trench
 * walls and overhangs get the same treatment as flat ground, while solid rock deep inside a
 * mountain stays plain.
 * <p>
 * <b>Landforms.</b>
 * <ul>
 *   <li>river, lake and canyon beds get gravel, sand, clay or mud depending on climate;</li>
 *   <li>volcanoes are covered with basalt, blackstone and tuff;</li>
 *   <li>deep-sea trenches are lined with magma, blackstone and basalt, and mid-ocean ridges are
 *       patched with basalt;</li>
 *   <li>forest floors get coarse dirt, podzol, moss and rooted dirt patches, and wetland margins
 *       turn to mud;</li>
 *   <li>trails of dirt path with frayed coarse-dirt edges wind across gentle ground (config
 *       {@code trails}).</li>
 * </ul>
 */
public final class TerrainSurfaceRule implements SurfaceRules.RuleSource {
    public static final TerrainSurfaceRule INSTANCE = new TerrainSurfaceRule();
    public static final MapCodec<TerrainSurfaceRule> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final KeyDispatchDataCodec<TerrainSurfaceRule> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** Material fades never reach deeper than this below the nearest exposed surface. */
    public static final int MAX_DEPTH = 15;

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
        /** Air pockets this close below the column top count as open ground (overhang floors, cave mouths). */
        private static final int OPEN_POCKET = 24;
        /** Horizontal distances checked for sideways exposure. */
        private static final int[] RING = {1, 2, 3, 4, 6, 8, 11, 15};

        private static final BlockState STONE = Blocks.STONE.defaultBlockState();
        private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
        private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
        private static final BlockState ROOTED_DIRT = Blocks.ROOTED_DIRT.defaultBlockState();
        private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
        private static final BlockState SAND = Blocks.SAND.defaultBlockState();
        private static final BlockState RED_SAND = Blocks.RED_SAND.defaultBlockState();
        private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
        private static final BlockState SMOOTH_SANDSTONE = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        private static final BlockState RED_SANDSTONE = Blocks.RED_SANDSTONE.defaultBlockState();
        private static final BlockState SMOOTH_RED_SANDSTONE = Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState();
        private static final BlockState ORANGE_TERRACOTTA = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        private static final BlockState CLAY = Blocks.CLAY.defaultBlockState();
        private static final BlockState MUD = Blocks.MUD.defaultBlockState();
        private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
        private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
        private static final BlockState DIORITE = Blocks.DIORITE.defaultBlockState();
        private static final BlockState GRANITE = Blocks.GRANITE.defaultBlockState();
        private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
        private static final BlockState COBBLESTONE = Blocks.COBBLESTONE.defaultBlockState();
        private static final BlockState MOSSY_COBBLESTONE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
        private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
        private static final BlockState DIRT_PATH = Blocks.DIRT_PATH.defaultBlockState();
        private static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
        private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
        private static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
        private static final BlockState SMOOTH_BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();
        private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
        private static final BlockState COBBLED_DEEPSLATE = Blocks.COBBLED_DEEPSLATE.defaultBlockState();

        // single-colour families, ordered from the weathered outside to fresh rock
        private static final BlockState[] GREY = {TUFF, ANDESITE, STONE};
        private static final BlockState[] DARK = {TUFF, SMOOTH_BASALT, DEEPSLATE};
        private static final BlockState[] PALE = {CALCITE, DIORITE, STONE};
        private static final BlockState[] SANDY = {SMOOTH_SANDSTONE, SANDSTONE, SANDSTONE};
        private static final BlockState[] RED = {SMOOTH_RED_SANDSTONE, RED_SANDSTONE, ORANGE_TERRACOTTA};
        private static final BlockState[] VOLCANIC = {BLACKSTONE, BASALT, SMOOTH_BASALT};
        private static final BlockState[] UNDER_SNOW = {CALCITE, DIORITE, STONE};
        private static final BlockState[] UNDER_SAND = {SANDSTONE, SMOOTH_SANDSTONE, SANDSTONE};
        private static final BlockState[] SEDIMENT = {SAND, GRAVEL};
        private static final BlockState[] TRENCH_WALL = {BLACKSTONE, BASALT, SMOOTH_BASALT};
        private static final BlockState[] TRENCH_FLOOR = {BLACKSTONE, BASALT, BLACKSTONE};

        private final SurfaceRules.Context ctx;
        private final TerrainModel model;
        private final int seaLevel;
        private final ChunkPos chunkPos;
        private final boolean trails;
        private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        private int lastX = Integer.MIN_VALUE;
        private int lastZ = Integer.MIN_VALUE;
        private final ColumnSample col = new ColumnSample();
        private int slope;
        /** Gradient of the smoothed terrain (rise per block over 8 blocks); steep ground is bare rock. */
        private double smoothSlope;
        /** Y of the topmost solid block of the column. */
        private int top;
        /** Whether this column can have faces (only then is sideways exposure computed). */
        private boolean rugged;
        /** Lowest neighbouring top at each {@link #RING} distance. */
        private final int[] ringTop = new int[RING.length];
        private double soilDepth;
        private double trailDistance;
        private boolean trailGround;
        private BlockState[] family;

        Rule(SurfaceRules.Context ctx, TerrainModel model) {
            this.ctx = ctx;
            this.model = model;
            this.seaLevel = model.seaLevel();
            this.chunkPos = ctx.chunk.getPos();
            this.trails = TerrahaulConfig.trails();
        }

        private void updateColumn(int x, int z) {
            lastX = x;
            lastZ = z;
            // categorical fields come from the nearest interpolation corner, the height is interpolated
            col.copyFrom(model.sample((x + 2) & ~3, (z + 2) & ~3));
            top = topAt(x, z);
            int max = 0;
            for (int i = -2; i <= 2; i += 4) {
                max = Math.max(max, Math.abs(topAt(x + i, z) - top));
                max = Math.max(max, Math.abs(topAt(x, z + i) - top));
            }
            slope = max;
            double gx = model.interpolatedHeight(x + 4, z) - model.interpolatedHeight(x - 4, z);
            double gz = model.interpolatedHeight(x, z + 4) - model.interpolatedHeight(x, z - 4);
            smoothSlope = Math.sqrt(gx * gx + gz * gz) / 8.0;
            rugged = slope >= 3 || col.steepness > 0.3 || col.cliff > 0.1 || col.trench > 0.15 || col.canyon > 0.1 || col.coastCliff > 0.2;
            if (rugged) {
                for (int i = 0; i < RING.length; i++) {
                    int k = RING[i];
                    ringTop[i] = Math.min(Math.min(topAt(x + k, z), topAt(x - k, z)), Math.min(topAt(x, z + k), topAt(x, z - k)));
                }
            }
            double thin = col.mountain > 0.4 ? 0.6 : 1.0;
            soilDepth = Math.max(1.5, (3.0 + 4.0 * Math.max(0.0, 1.0 - slope / 5.0) + 1.5 * patch(x, z, 23.0, 5)) * thin);
            trailDistance = trails ? model.trailDistance(x, z) : 1.0E9;
            trailGround = trailDistance < 4.0 && TerrainModel.trailGround(col, seaLevel);
            family = family();
        }

        /** The rock family of this column: grey on every mountain, otherwise the province's own. */
        private BlockState[] family() {
            if (col.volcano > 0.3 || col.geology == Geology.VOLCANIC) {
                return VOLCANIC;
            }
            boolean mountainous = col.mountain > 0.3 || col.peakness > 0.55;
            return switch (col.geology) {
                case Geology.DARK -> DARK;
                case Geology.LIMESTONE -> PALE;
                case Geology.SEDIMENTARY -> mountainous ? GREY : col.baseTemperature > 0.45 ? RED : SANDY;
                default -> GREY;
            };
        }

        /** Y of the topmost solid block: exact inside this chunk, from the terrain model outside. */
        private int topAt(int x, int z) {
            if (x >> 4 == chunkPos.x && z >> 4 == chunkPos.z) {
                return ctx.chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
            }
            return (int) Math.floor(model.interpolatedHeight(x, z));
        }

        /** Smooth 2D noise in about [-1, 1] at the given feature size. */
        private double patch(int x, int z, double scale, int channel) {
            return model.detail3(channel, x / scale, channel * 17.0, z / scale);
        }

        /** Smooth 3D noise in about [-1, 1] that makes the boundaries between gradient stops wavy. */
        private double wobble(int x, int y, int z) {
            return model.detail3(6, x / 3.5, y / 3.5, z / 3.5);
        }

        /**
         * Picks a stop of a gradient at position {@code t} (0..1). The boundaries between stops move
         * by up to {@code 0.45} of a stop with the smooth noise {@code n}, so neighbouring blocks
         * almost always agree and the fade forms patches and wavy bands rather than speckles.
         */
        private static BlockState gradient(BlockState[] stops, double t, double n) {
            double f = Math.max(0.0, Math.min(1.0, t)) * (stops.length - 1) + n * 0.45;
            int i = (int) Math.floor(f + 0.5);
            return stops[Math.max(0, Math.min(stops.length - 1, i))];
        }

        @Override
        public BlockState tryApply(int x, int y, int z) {
            if (x != lastX || z != lastZ) {
                updateColumn(x, z);
            }
            int above = ctx.stoneDepthAbove - 1;
            int vertical = top - y <= OPEN_POCKET ? above : top - y;
            int lateral = rugged ? lateral(y) : Integer.MAX_VALUE;
            // rock above open air: the underside of an overhang or arch, or a cave roof
            int below = ctx.stoneDepthBelow - 1;
            int side = Math.min(lateral, below);
            int d = Math.min(vertical, side);
            if (d > MAX_DEPTH) {
                return y < 8 ? deep(STONE, x, y, z) : null;
            }
            boolean face = side < vertical - 1 || slope >= 6;
            BlockState state = choose(x, y, z, d, face);
            return y < 8 ? deep(state == null ? STONE : state, x, y, z) : state;
        }

        /** Horizontal distance to open air at height {@code y}, or a large value. */
        private int lateral(int y) {
            for (int i = 0; i < RING.length; i++) {
                if (ringTop[i] < y) {
                    return RING[i] - 1;
                }
            }
            return Integer.MAX_VALUE;
        }

        private BlockState choose(int x, int y, int z, int d, boolean face) {
            boolean underFluid = ctx.waterHeight != Integer.MIN_VALUE && ctx.waterHeight > y;
            boolean planned = col.waterLevel != ColumnSample.NO_WATER && y < col.waterLevel;
            Holder<Biome> biome = ctx.biome.get();
            if (biome.is(BiomeTags.IS_BADLANDS) && col.river <= 0.0 && col.lake <= 0.0) {
                // keep vanilla's terracotta bands
                return null;
            }
            double n = wobble(x, y, z);

            // ---- deep-sea trenches and mid-ocean ridges -----------------------------------------
            if (underFluid && col.trench > 0.3 && y < col.trenchFloor + 60.0) {
                return trench(x, y, z, d, face, n);
            }
            // ---- beds of rivers and lakes --------------------------------------------------------
            if (d <= 2 && (col.river > 0.0 || col.lake > 0.0 || planned) && (underFluid || planned || y < seaLevel)) {
                return bedMaterial(x, y, z, d);
            }
            // ---- volcanoes ----------------------------------------------------------------------
            if (col.volcano > 0.3) {
                BlockState v = volcanic(x, y, z, d, n);
                if (v != null) {
                    return v;
                }
            }
            // ---- cliffs, canyon walls, escarpments and other exposed faces ------------------------
            if (face && !underFluid || slope >= 4 && col.cliff > 0.3 && d <= 6) {
                return strata(x, y, z, n);
            }
            if (col.canyon > 0.25 && d <= 3 && y <= col.canyonFloor + 4.0) {
                return canyonFloor(x, z, d);
            }
            // ---- sea floors ---------------------------------------------------------------------
            if (underFluid && !planned && col.river <= 0.0 && col.lake <= 0.0) {
                return seabed(x, y, z, d, face, n);
            }
            // ---- snow and bare rock on mountains ------------------------------------------------
            if (isSnowy(biome)) {
                return snowy(x, y, z, d, biome, n);
            }
            if (isRocky(biome) && !biome.is(Biomes.WINDSWEPT_GRAVELLY_HILLS)) {
                return d <= 3 ? bareRock(x, z, d, n) : bedrock(x, y, z, d, n);
            }
            // ---- scree on steep alpine slopes -----------------------------------------------------
            if (col.mountain > 0.3 && slope >= 3 && d <= 1 && !underFluid && col.peakness > 0.35) {
                double p = patch(x, z, 9.0, 2);
                if (p > 0.35) {
                    return supported(x, y, z) ? GRAVEL : family[1];
                }
            }
            // ---- coasts: pebbly beaches under rocky cliffs ---------------------------------------
            if (col.coastCliff > 0.3 && d <= 2 && y <= seaLevel + 2 && y >= seaLevel - 6) {
                return patch(x, z, 6.0, 3) > 0.0 && supported(x, y, z) ? GRAVEL : family[2];
            }
            // ---- river banks: gravel bars and sandbars --------------------------------------------
            if (col.riverValley > 0.85 && col.river <= 0.0 && d <= 1 && y <= col.riverLevel + 2.0 && !underFluid) {
                double p = patch(x, z, 7.0, 0);
                if (p > 0.35 && supported(x, y, z)) {
                    return d == 0 && p > 0.6 ? SAND : GRAVEL;
                }
            }
            // ---- wetland margins --------------------------------------------------------------
            if (col.swamp > 0.4 && d <= 1 && y >= seaLevel - 3 && y <= seaLevel + 1) {
                return MUD;
            }
            if (d == 0) {
                return underFluid ? null : topBlock(x, y, z, biome, n);
            }
            return profile(x, y, z, d, biome, n);
        }

        // =====================================================================================
        // top blocks: trails and the fringes between surface materials; null keeps vanilla's top
        // =====================================================================================

        /** How far past the slope at which ground turns to bare rock this column is (negative: below it). */
        private double rockiness(int x, int z, double limit) {
            return smoothSlope - limit - 0.3 * patch(x, z, 12.0, 4);
        }

        private BlockState topBlock(int x, int y, int z, Holder<Biome> biome, double n) {
            boolean soil = !isSandy(biome) && !isRocky(biome);
            if (soil && trailGround && slope <= 3) {
                BlockState trail = trail(x, y, z, n);
                if (trail != null) {
                    return trail;
                }
            }
            if (soil && biome.value().getBaseTemperature() > 0.1) {
                // in temperate biomes just below the snow line, snowfields grow as one climbs
                double t = col.temperature;
                if (t < -0.3 && smoothSlope < 1.0) {
                    double cover = Math.min(0.9, (-0.3 - t) / 0.17 * 0.8);
                    if (0.5 + 0.5 * patch(x, z, 16.0, 6) < cover) {
                        return SNOW_BLOCK;
                    }
                }
            }
            if (soil) {
                // grass gives way to rock outcrops on steep ground, with a coarse-dirt fringe
                double r = rockiness(x, z, 0.95);
                if (r > 0.0) {
                    return bareRock(x, z, 0, n);
                }
                if (r > -0.12) {
                    return COARSE_DIRT;
                }
                // sand creeps inland behind beaches
                if (col.beach > 0.05 && y <= seaLevel + 4) {
                    double reach = col.beach * 1.2 + (seaLevel + 4 - y) * 0.12 - 0.2;
                    if (0.5 + 0.5 * patch(x, z, 8.0, 5) < reach) {
                        return supported(x, y, z) ? SAND : SANDSTONE;
                    }
                }
                if (slope < 4) {
                    return forestFloor(x, z, biome);
                }
            }
            return null;
        }

        /** Dirt path down the middle of a trail, frayed coarse dirt, gravel or rooted dirt along its edges. */
        private BlockState trail(int x, int y, int z, double n) {
            double half = 1.0 + 0.9 * (0.5 + 0.5 * patch(x, z, 90.0, 7));
            double edge = trailDistance - half + 0.6 * patch(x, z, 3.0, 2);
            if (edge < 0.0) {
                if (slope >= 2 && n > 0.45) {
                    return slope >= 3 ? GRAVEL : COARSE_DIRT;
                }
                return DIRT_PATH;
            }
            if (edge < 1.5) {
                if (col.mountain > 0.2 || slope >= 2) {
                    return supported(x, y, z) ? GRAVEL : COARSE_DIRT;
                }
                return n > 0.3 && col.humidity > 0.2 ? ROOTED_DIRT : COARSE_DIRT;
            }
            return null;
        }

        // =====================================================================================
        // subsurface profiles with fades
        // =====================================================================================

        /** Snowy mountains: snow on gentle ground over calcite and diorite, bare grey rock where steep. */
        private BlockState snowy(int x, int y, int z, int d, Holder<Biome> biome, double n) {
            if (rockiness(x, z, 1.05) > 0.0) {
                // too steep for snow to stay
                return d <= 3 ? bareRock(x, z, d, n) : bedrock(x, y, z, d, n);
            }
            double t = d / Math.max(1.5, soilDepth * 0.6);
            if (t < 1.0) {
                if (d == 0 && biome.is(Biomes.FROZEN_PEAKS) && patch(x, z, 14.0, 7) > 0.55) {
                    return PACKED_ICE;
                }
                return SNOW_BLOCK;
            }
            if (t < 3.0) {
                return gradient(UNDER_SNOW, (t - 1.0) / 2.0, n);
            }
            return bedrock(x, y, z, d, n);
        }

        private BlockState profile(int x, int y, int z, int d, Holder<Biome> biome, double n) {
            if (isSandy(biome)) {
                double t = d / Math.max(2.0, soilDepth * 0.8);
                if (t < 0.8 + 0.25 * n) {
                    return supported(x, y, z) ? SAND : SANDSTONE;
                }
                if (t < 2.6) {
                    return gradient(UNDER_SAND, (t - 0.8) / 1.8, n);
                }
                return bedrock(x, y, z, d, n);
            }
            // soil: dirt, coarse dirt, then the rock family from weathered to fresh
            double t = d / soilDepth + 0.2 * n;
            if (t < 0.75) {
                return DIRT;
            }
            if (t < 1.1) {
                return COARSE_DIRT;
            }
            if (t < 2.2) {
                return gradient(family, (t - 1.1) / 1.1, n);
            }
            return bedrock(x, y, z, d, n);
        }

        /** Bare rock at the surface: broad patches of the family, turning to fresh rock a few blocks in. */
        private BlockState bareRock(int x, int z, int d, double n) {
            return gradient(family, 0.65 + 0.45 * patch(x, z, 15.0, 5) + d * 0.15, n);
        }

        /** Rock below the fades: the family's fresh rock, with broad patches of its middle stop. */
        private BlockState bedrock(int x, int y, int z, int d, double n) {
            if (col.mountain > 0.4) {
                return strata(x, y, z, n);
            }
            return n > 0.55 ? family[1] : family[2];
        }

        /**
         * Exposed rock: strata that cycle smoothly through the family (for grey rock: stone,
         * andesite, tuff, andesite, stone, ...). The layers are 5-9 blocks thick, tilt and wave
         * with low-frequency noise, and their boundaries are softened by the 3D noise.
         */
        private BlockState strata(int x, int y, int z, double n) {
            double period = col.geology == Geology.SEDIMENTARY ? 16.0 : 24.0;
            double warp = col.strata * 37.0 + patch(x, z, 70.0, 1) * 6.0 + patch(x, z, 19.0, 3) * 1.5;
            double phase = (y + warp) / period;
            double tri = Math.abs(phase - Math.floor(phase) - 0.5) * 2.0;
            return gradient(family, tri, n * 0.8);
        }

        /** Sea floor: sand in the shallows giving way to gravel with depth; clay and tuff in the abyss. */
        private BlockState seabed(int x, int y, int z, int d, boolean face, double n) {
            if (face) {
                return strata(x, y, z, n);
            }
            double depth = ctx.waterHeight - y;
            boolean cold = col.temperature < -0.2;
            boolean warm = col.baseTemperature > 0.5;
            double p = patch(x, z, 10.0, 4);
            if (col.oceanRidge > 0.3 && depth > 60.0 && d <= 1 && p > 0.6 - 0.5 * col.oceanRidge) {
                return p > 0.9 - 0.2 * col.oceanRidge ? MAGMA : BASALT;
            }
            BlockState topMaterial;
            if (depth < 14.0) {
                topMaterial = warm ? SAND : gradient(SEDIMENT, cold ? 0.7 : 0.25, p);
                if (!warm && patch(x, z, 9.0, 3) > 0.62) {
                    topMaterial = CLAY;
                }
            } else if (depth < 70.0) {
                topMaterial = gradient(SEDIMENT, (depth - 14.0) / 56.0 + (cold ? 0.3 : 0.0) - (warm ? 0.3 : 0.0), p);
            } else {
                topMaterial = p > 0.45 ? CLAY : p < -0.5 ? TUFF : GRAVEL;
            }
            double t = d / 4.0 + 0.2 * n;
            if (t < 0.6) {
                return falling(topMaterial) && !supported(x, y, z) ? consolidated(topMaterial) : topMaterial;
            }
            if (t < 1.6) {
                return consolidated(topMaterial);
            }
            return bedrock(x, y, z, d, n);
        }

        /** Deep-sea trenches: magma vents in blackstone and basalt on the floor, dark banded walls. */
        private BlockState trench(int x, int y, int z, int d, boolean face, double n) {
            if (face || y > col.trenchFloor + 8.0) {
                double phase = (y + col.strata * 23.0 + patch(x, z, 40.0, 1) * 4.0) / 18.0;
                double tri = Math.abs(phase - Math.floor(phase) - 0.5) * 2.0;
                return gradient(TRENCH_WALL, tri, n * 0.8);
            }
            if (d <= 1) {
                double vent = patch(x, z, 7.0, 7);
                if (vent > 0.35) {
                    return MAGMA;
                }
                return gradient(TRENCH_FLOOR, 0.5 + 0.5 * patch(x, z, 5.0, 2), n * 0.5);
            }
            return d <= 6 ? gradient(TRENCH_FLOOR, (d - 1) / 5.0, n) : null;
        }

        // =====================================================================================
        // helpers
        // =====================================================================================

        /** True if the block below is solid, so sand or gravel placed here will not fall. */
        private boolean supported(int x, int y, int z) {
            BlockState below = ctx.chunk.getBlockState(probe.set(x, y - 1, z));
            return !below.isAir() && below.getFluidState().isEmpty();
        }

        private static boolean falling(BlockState state) {
            return state == SAND || state == GRAVEL || state == RED_SAND;
        }

        private static BlockState consolidated(BlockState state) {
            if (state == SAND) {
                return SANDSTONE;
            }
            if (state == RED_SAND) {
                return RED_SANDSTONE;
            }
            if (state == GRAVEL || state == CLAY) {
                return TUFF;
            }
            return state;
        }

        /** Deepslate-zone counterpart of a block below Y 8 (with vanilla's 0..8 gradient). */
        private static BlockState deep(BlockState state, int x, int y, int z) {
            if (y >= 8 || y > 0 && SeedMixer.unit(SeedMixer.hash3(0xDEE95L, x, y, z)) >= (8 - y) / 8.0) {
                return state == STONE ? null : state;
            }
            if (state == STONE || state == GRANITE) {
                return DEEPSLATE;
            }
            if (state == ANDESITE || state == DIORITE || state == SANDSTONE || state == CALCITE) {
                return TUFF;
            }
            if (state == COBBLESTONE || state == MOSSY_COBBLESTONE) {
                return COBBLED_DEEPSLATE;
            }
            return state;
        }

        private static boolean isSandy(Holder<Biome> biome) {
            return biome.is(Biomes.DESERT) || biome.is(Biomes.BEACH) || biome.is(Biomes.SNOWY_BEACH);
        }

        private static boolean isSnowy(Holder<Biome> biome) {
            return biome.is(Biomes.SNOWY_SLOPES) || biome.is(Biomes.FROZEN_PEAKS) || biome.is(Biomes.JAGGED_PEAKS) || biome.is(Biomes.ICE_SPIKES);
        }

        private static boolean isRocky(Holder<Biome> biome) {
            return biome.is(Biomes.STONY_PEAKS) || biome.is(Biomes.STONY_SHORE) || biome.is(Biomes.WINDSWEPT_GRAVELLY_HILLS);
        }

        // =====================================================================================
        // landform materials
        // =====================================================================================

        private BlockState bedMaterial(int x, int y, int z, int d) {
            double n = patch(x, z, 6.0, 0);
            double hot = col.baseTemperature;
            double wet = col.humidity;
            BlockState result;
            if (col.lake > 0.0) {
                switch (col.lakeType) {
                    case LakeType.MOUNTAIN, LakeType.GLACIAL, LakeType.CRATER -> result = n > 0.0 ? GRAVEL : family[2];
                    case LakeType.MARSH -> result = d == 0 && n > 0.5 ? CLAY : MUD;
                    case LakeType.GREAT -> result = n > -0.3 ? SAND : GRAVEL;
                    default -> {
                        if (n > 0.55) {
                            result = CLAY;
                        } else {
                            result = n > 0.0 ? SAND : (wet > 0.3 ? MUD : DIRT);
                        }
                    }
                }
            } else if (col.mountain > 0.3) {
                result = n > -0.3 ? GRAVEL : family[2];
            } else if (col.dryRiver) {
                result = hot > 0.55 ? RED_SAND : SAND;
            } else if (hot > 0.5 && wet > 0.3 && n > -0.2) {
                result = MUD;
            } else if (n > 0.6 && d == 0) {
                result = CLAY;
            } else {
                result = n > -0.15 ? GRAVEL : SAND;
            }
            if (falling(result) && !supported(x, y, z)) {
                return consolidated(result);
            }
            return result;
        }

        private BlockState volcanic(int x, int y, int z, int d, double n) {
            double p = patch(x, z, 9.0, 2);
            if (col.crater > 0.0 && d <= 1 && p > 0.5) {
                return MAGMA;
            }
            if (d <= 3) {
                return gradient(VOLCANIC, 0.5 + 0.5 * p, n * 0.6);
            }
            return strata(x, y, z, n);
        }

        private BlockState canyonFloor(int x, int z, int d) {
            double p = patch(x, z, 10.0, 3);
            if (col.baseTemperature > 0.45) {
                return p > -0.3 ? RED_SAND : RED_SANDSTONE;
            }
            if (d == 0 && p < -0.2) {
                return null;
            }
            return p > 0.2 ? GRAVEL : COARSE_DIRT;
        }

        private BlockState forestFloor(int x, int z, Holder<Biome> biome) {
            double p = patch(x, z, 11.0, 1);
            if (p < 0.42 && p > -0.55) {
                return null;
            }
            if (biome.is(BiomeTags.IS_TAIGA) && !biome.is(Biomes.SNOWY_TAIGA)) {
                return p > 0.42 ? Blocks.PODZOL.defaultBlockState() : COARSE_DIRT;
            }
            if (biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
                return p > 0.55 ? Blocks.MOSS_BLOCK.defaultBlockState() : (p > 0.42 ? ROOTED_DIRT : COARSE_DIRT);
            }
            if (biome.is(BiomeTags.IS_FOREST) || biome.is(Biomes.FLOWER_FOREST)) {
                return p > 0.6 && col.humidity > 0.1 ? Blocks.MOSS_BLOCK.defaultBlockState() : (p < -0.55 ? COARSE_DIRT : null);
            }
            if (biome.is(BiomeTags.IS_JUNGLE)) {
                return p > 0.5 ? Blocks.MOSS_BLOCK.defaultBlockState() : null;
            }
            if (biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU) || biome.is(Biomes.WINDSWEPT_SAVANNA)) {
                return p < -0.6 ? COARSE_DIRT : null;
            }
            return null;
        }
    }
}
