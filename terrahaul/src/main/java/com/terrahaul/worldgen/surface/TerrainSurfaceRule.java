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
 * landforms, with gradual, dithered fades between materials.
 * <p>
 * The rule runs before the vanilla Overworld surface rule. The topmost block of ordinary ground
 * (grass, sand, snow, podzol, ...) is left to vanilla's biome rules; Terrahaul decides what lies
 * beneath and what covers special landforms.
 * <p>
 * <b>Fades.</b> Below the surface, materials blend into each other with a speckled, noise-dithered
 * gradient instead of hard layers:
 * <ul>
 *   <li>soil: dirt, coarse dirt, gravel, weathered rock, then bedrock of the geological
 *       province;</li>
 *   <li>sand: sand, sandstone, then rock;</li>
 *   <li>snow: snow, calcite, then rock;</li>
 *   <li>sea floors: sand, sand mixed with gravel, then gravel, clay and tuff with depth.</li>
 * </ul>
 * Strata bands on cliffs have fuzzy edges. At the surface, grass breaks into stone on steepening
 * slopes, snow patches thicken towards the snow line, and sand creeps inland at beaches.
 * <p>
 * Fades never reach deeper than {@value #MAX_DEPTH} blocks below the nearest exposed surface.
 * That distance is measured both vertically and sideways, so cliff and mountain faces, trench
 * walls and overhangs get the same treatment as flat ground, while solid rock deep inside a
 * mountain stays plain.
 * <p>
 * <b>Landforms.</b>
 * <ul>
 *   <li>cliffs and canyon walls expose banded province rock, and scree covers steep alpine
 *       slopes;</li>
 *   <li>river, lake and canyon beds get gravel, sand, clay or mud depending on climate;</li>
 *   <li>volcanoes are covered with basalt, blackstone, tuff and ash;</li>
 *   <li>deep-sea trenches are lined with magma, blackstone and basalt, and mid-ocean ridges are
 *       flecked with basalt;</li>
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
        private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
        private static final BlockState CLAY = Blocks.CLAY.defaultBlockState();
        private static final BlockState MUD = Blocks.MUD.defaultBlockState();
        private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
        private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
        private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
        private static final BlockState COBBLESTONE = Blocks.COBBLESTONE.defaultBlockState();
        private static final BlockState MOSSY_COBBLESTONE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
        private static final BlockState DIRT_PATH = Blocks.DIRT_PATH.defaultBlockState();
        private static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
        private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
        private static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
        private static final BlockState SMOOTH_BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();
        private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
        private static final BlockState COBBLED_DEEPSLATE = Blocks.COBBLED_DEEPSLATE.defaultBlockState();

        private static final BlockState[][] STRATA = {
            // TEMPERATE
            {STONE, STONE, ANDESITE, STONE, STONE, ANDESITE, TUFF},
            // GRANITE
            {Blocks.GRANITE.defaultBlockState(), Blocks.GRANITE.defaultBlockState(), STONE, Blocks.GRANITE.defaultBlockState(), ANDESITE},
            // DARK
            {DEEPSLATE, TUFF, DEEPSLATE, SMOOTH_BASALT, COBBLED_DEEPSLATE},
            // LIMESTONE
            {CALCITE, Blocks.DIORITE.defaultBlockState(), CALCITE, STONE, Blocks.DIORITE.defaultBlockState()},
            // SEDIMENTARY (temperate climates)
            {SANDSTONE, STONE, Blocks.TERRACOTTA.defaultBlockState(), SANDSTONE,
                Blocks.BROWN_TERRACOTTA.defaultBlockState(), ANDESITE, Blocks.SMOOTH_SANDSTONE.defaultBlockState()},
            // VOLCANIC
            {BASALT, BLACKSTONE, TUFF, BASALT, SMOOTH_BASALT},
        };
        private static final BlockState[] HOT_SEDIMENTARY = {
            Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(),
            Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState()};
        /** Weathered rock between subsoil and bedrock, per geological province. */
        private static final BlockState[] ROCK_ALT = {
            ANDESITE, Blocks.GRANITE.defaultBlockState(), TUFF, Blocks.DIORITE.defaultBlockState(), SANDSTONE, TUFF};

        private final SurfaceRules.Context ctx;
        private final TerrainModel model;
        private final int seaLevel;
        private final ChunkPos chunkPos;
        private final boolean trails;
        private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        private int lastX = Integer.MIN_VALUE;
        private int lastZ = Integer.MIN_VALUE;
        private final ColumnSample col = new ColumnSample();
        private double height;
        private int slope;
        /** Y of the topmost solid block of the column. */
        private int top;
        /** Whether this column can have faces (only then is sideways exposure computed). */
        private boolean rugged;
        /** Lowest neighbouring top at each {@link #RING} distance. */
        private final int[] ringTop = new int[RING.length];
        private double soilDepth;
        private double trailDistance;
        private boolean trailGround;

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
            height = model.interpolatedHeight(x, z);
            top = topAt(x, z);
            int max = 0;
            for (int i = -2; i <= 2; i += 4) {
                max = Math.max(max, Math.abs(topAt(x + i, z) - top));
                max = Math.max(max, Math.abs(topAt(x, z + i) - top));
            }
            slope = max;
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
        }

        /** Y of the topmost solid block: exact inside this chunk, from the terrain model outside. */
        private int topAt(int x, int z) {
            if (x >> 4 == chunkPos.x && z >> 4 == chunkPos.z) {
                return ctx.chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
            }
            return (int) Math.floor(model.interpolatedHeight(x, z));
        }

        private double patch(int x, int z, double scale, int channel) {
            return model.detail3(channel, x / scale, channel * 17.0, z / scale);
        }

        /** Per-block dither in about [-1, 1]: white noise blended with small-scale 3D noise. */
        private double dither(int x, int y, int z) {
            double white = SeedMixer.unit(SeedMixer.hash3(0x6A3F11C5L, x, y, z)) * 2.0 - 1.0;
            return white * 0.6 + model.detail3(6, x / 6.0, y / 4.0, z / 6.0) * 0.55;
        }

        private static double hash01(int x, int y, int z, long salt) {
            return SeedMixer.unit(SeedMixer.hash3(salt, x, y, z));
        }

        /** Picks a stop of a gradient at position {@code t} (0..1), with the boundaries speckled by {@code dither}. */
        private static BlockState gradient(BlockState[] stops, double t, double dither) {
            double f = Math.max(0.0, Math.min(1.0, t)) * (stops.length - 1) + dither * 0.6;
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
            int d = Math.min(vertical, lateral);
            if (d > MAX_DEPTH) {
                return y < 8 ? deep(STONE, x, y, z) : null;
            }
            boolean face = lateral < vertical - 1 || slope >= 6;
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
            double dz = dither(x, y, z);

            // ---- deep-sea trenches and mid-ocean ridges -----------------------------------------
            if (underFluid && col.trench > 0.3 && y < col.trenchFloor + 60.0) {
                return trench(x, y, z, d, face, dz);
            }
            // ---- beds of rivers and lakes --------------------------------------------------------
            if (d <= 2 && (col.river > 0.0 || col.lake > 0.0 || planned) && (underFluid || planned || y < seaLevel)) {
                return bedMaterial(x, y, z, d);
            }
            // ---- volcanoes ----------------------------------------------------------------------
            if (col.volcano > 0.3) {
                BlockState v = volcanic(x, y, z, d, dz);
                if (v != null) {
                    return v;
                }
            }
            // ---- cliffs, canyon walls, escarpments and other exposed faces ------------------------
            if (face && !underFluid || slope >= 4 && col.cliff > 0.3 && d <= 6) {
                return rockFace(x, y, z, d, dz);
            }
            if (col.canyon > 0.25 && d <= 3 && y <= col.canyonFloor + 4.0) {
                return canyonFloor(x, z, d);
            }
            // ---- sea floors ---------------------------------------------------------------------
            if (underFluid && !planned && col.river <= 0.0 && col.lake <= 0.0) {
                return seabed(x, y, z, d, face, dz);
            }
            // ---- scree on steep alpine slopes -----------------------------------------------------
            if (col.mountain > 0.3 && slope >= 3 && d <= 1 && !underFluid && col.peakness > 0.35) {
                double n = patch(x, z, 7.0, 2);
                if (n > 0.15) {
                    return n > 0.5 ? COBBLESTONE : supported(x, y, z) ? GRAVEL : COBBLESTONE;
                }
                if (n < -0.45) {
                    return ANDESITE;
                }
            }
            // ---- coasts: pebbly beaches under rocky cliffs ---------------------------------------
            if (col.coastCliff > 0.3 && d <= 2 && y <= seaLevel + 2 && y >= seaLevel - 6) {
                return patch(x, z, 5.0, 3) > 0.0 && supported(x, y, z) ? GRAVEL : STONE;
            }
            // ---- river banks: gravel bars and sandbars --------------------------------------------
            if (col.riverValley > 0.85 && col.river <= 0.0 && d <= 1 && y <= col.riverLevel + 2.0 && !underFluid) {
                double n = patch(x, z, 6.0, 0);
                if (n > 0.35 && supported(x, y, z)) {
                    return d == 0 && n > 0.6 ? SAND : GRAVEL;
                }
            }
            // ---- wetland margins --------------------------------------------------------------
            if (col.swamp > 0.4 && d <= 1 && y >= seaLevel - 3 && y <= seaLevel + 1) {
                return MUD;
            }
            if (d == 0) {
                return underFluid ? null : topBlock(x, y, z, biome, dz);
            }
            return profile(x, y, z, d, biome, dz);
        }

        // =====================================================================================
        // top blocks: trails and the fringes between surface materials; null keeps vanilla's top
        // =====================================================================================

        private BlockState topBlock(int x, int y, int z, Holder<Biome> biome, double dz) {
            boolean soil = !isSandy(biome) && !isSnowy(biome) && !isRocky(biome);
            if (soil && trailGround && slope <= 3) {
                BlockState trail = trail(x, y, z, dz);
                if (trail != null) {
                    return trail;
                }
            }
            if ((soil || isRocky(biome)) && biome.value().getBaseTemperature() > 0.1) {
                // in temperate biomes just below the snow line, snow patches thicken towards it
                double t = col.temperature;
                if (t < -0.3 && slope < 7) {
                    double chance = Math.min(0.85, (-0.3 - t) / 0.17 * 0.8);
                    if (0.5 * hash01(x, y, z, 0x5A0L) + 0.25 * (patch(x, z, 13.0, 6) + 1.0) < chance) {
                        return SNOW_BLOCK;
                    }
                }
            }
            if (soil) {
                // grass breaks up into stone as slopes steepen
                if (slope >= 4 && hash01(x, y, z, 0x5107EL) < (slope - 3.5) / 2.5 + dz * 0.2) {
                    return rockFace(x, y, z, 0, dz);
                }
                // sand creeps inland behind beaches
                if (col.beach > 0.05 && y <= seaLevel + 4 && hash01(x, y, z, 0xBEAC4L) < col.beach * 1.2 + (seaLevel + 4 - y) * 0.12 - 0.2) {
                    return supported(x, y, z) ? SAND : SANDSTONE;
                }
                if (slope < 4) {
                    return forestFloor(x, z, biome);
                }
            }
            return null;
        }

        /** Dirt path down the middle of a trail, frayed coarse dirt, gravel or rooted dirt along its edges. */
        private BlockState trail(int x, int y, int z, double dz) {
            double half = 1.0 + 0.9 * (0.5 + 0.5 * patch(x, z, 90.0, 7));
            double edge = trailDistance - half;
            if (edge < 0.0) {
                if (slope >= 2 && dz > 0.45) {
                    return slope >= 3 ? GRAVEL : COARSE_DIRT;
                }
                return dz > 0.85 ? COARSE_DIRT : DIRT_PATH;
            }
            if (edge < 2.0) {
                double p = (1.0 - edge / 2.0) * 0.8;
                if (hash01(x, y, z, 0x7A11L) < p) {
                    if (col.mountain > 0.2 || slope >= 2) {
                        return supported(x, y, z) ? GRAVEL : COARSE_DIRT;
                    }
                    return dz > 0.3 && col.humidity > 0.2 ? ROOTED_DIRT : COARSE_DIRT;
                }
            }
            return null;
        }

        // =====================================================================================
        // subsurface profiles with fades
        // =====================================================================================

        private BlockState profile(int x, int y, int z, int d, Holder<Biome> biome, double dz) {
            if (isSnowy(biome)) {
                double t = d / Math.max(1.5, soilDepth * 0.5);
                if (t < 1.0) {
                    return SNOW_BLOCK;
                }
                if (t < 2.2) {
                    return gradient(new BlockState[]{SNOW_BLOCK, CALCITE, CALCITE, rockAlt()}, (t - 1.0) / 1.2, dz);
                }
                return bedrock(x, y, z, d);
            }
            if (isSandy(biome)) {
                double t = d / Math.max(2.0, soilDepth * 0.8);
                if (t < 0.8) {
                    return supported(x, y, z) ? SAND : SANDSTONE;
                }
                if (t < 1.6) {
                    BlockState s = gradient(new BlockState[]{SAND, SANDSTONE, SANDSTONE}, (t - 0.8) / 0.8, dz);
                    return s == SAND && !supported(x, y, z) ? SANDSTONE : s;
                }
                if (t < 2.6) {
                    return gradient(new BlockState[]{SANDSTONE, SANDSTONE, rockAlt(), STONE}, (t - 1.6), dz);
                }
                return bedrock(x, y, z, d);
            }
            if (isRocky(biome)) {
                return d <= 2 ? gradient(new BlockState[]{rockAlt(), STONE}, d / 2.0, dz) : bedrock(x, y, z, d);
            }
            // soil: dirt, coarse dirt, gravel, weathered rock, bedrock
            double t = d / soilDepth;
            if (t < 0.75) {
                return dz > 0.9 ? COARSE_DIRT : DIRT;
            }
            if (t < 1.35) {
                BlockState s = gradient(new BlockState[]{DIRT, COARSE_DIRT, GRAVEL}, (t - 0.75) / 0.6, dz);
                return s == GRAVEL && !supported(x, y, z) ? COARSE_DIRT : s;
            }
            if (t < 2.0) {
                BlockState s = gradient(new BlockState[]{GRAVEL, rockAlt(), STONE}, (t - 1.35) / 0.65, dz);
                return s == GRAVEL && !supported(x, y, z) ? rockAlt() : s;
            }
            return bedrock(x, y, z, d);
        }

        private BlockState rockAlt() {
            return ROCK_ALT[Math.max(0, Math.min(ROCK_ALT.length - 1, col.geology))];
        }

        /** Rock under the soil: flecks of weathered rock that thin out with depth, province rock in mountains. */
        private BlockState bedrock(int x, int y, int z, int d) {
            if (col.mountain > 0.4 && col.geology != Geology.TEMPERATE) {
                return strata(x, y, z, 0.0);
            }
            return hash01(x, y, z, 0xBED0L) < 0.22 * (1.0 - d / (double) (MAX_DEPTH + 1)) ? rockAlt() : STONE;
        }

        /** Exposed rock: strata bands with fuzzy edges, weathered on the outer face. */
        private BlockState rockFace(int x, int y, int z, int d, double dz) {
            if (d == 0) {
                double w = hash01(x, y, z, 0xFACEL);
                if (col.humidity > 0.35 && w < 0.07) {
                    return MOSSY_COBBLESTONE;
                }
                if (w > 0.965) {
                    return COBBLESTONE;
                }
            }
            return strata(x, y, z, dz);
        }

        private BlockState strata(int x, int y, int z, double dz) {
            int geology = col.geology;
            double offset = col.strata * 37.0 + patch(x, z, 60.0, 1) * 3.0 + dz * 1.4;
            int band = (int) Math.floor((y + offset) / (geology == Geology.SEDIMENTARY ? 3.0 : 5.0));
            BlockState[] palette = STRATA[Math.max(0, Math.min(STRATA.length - 1, geology))];
            if (geology == Geology.SEDIMENTARY && col.baseTemperature > 0.45) {
                palette = HOT_SEDIMENTARY;
            }
            return palette[Math.floorMod(band, palette.length)];
        }

        /** Sea floor: sand in the shallows fading through sand and gravel to gravel, clay and tuff in the abyss. */
        private BlockState seabed(int x, int y, int z, int d, boolean face, double dz) {
            if (face) {
                return strata(x, y, z, dz);
            }
            double depth = ctx.waterHeight - y;
            boolean cold = col.temperature < -0.2;
            boolean warm = col.baseTemperature > 0.5;
            if (col.oceanRidge > 0.3 && depth > 60.0 && d <= 1) {
                double v = hash01(x, y, z, 0x21D6EL);
                if (v < 0.05 * col.oceanRidge) {
                    return MAGMA;
                }
                if (v < 0.4 * col.oceanRidge) {
                    return dz > 0.0 ? BASALT : TUFF;
                }
            }
            BlockState topMaterial;
            if (depth < 14.0) {
                topMaterial = warm ? SAND : gradient(new BlockState[]{SAND, SAND, GRAVEL}, cold ? 0.7 : 0.25, dz);
                if (!warm && patch(x, z, 9.0, 3) > 0.62) {
                    topMaterial = CLAY;
                }
            } else if (depth < 70.0) {
                topMaterial = gradient(new BlockState[]{SAND, SAND, GRAVEL, GRAVEL}, (depth - 14.0) / 56.0 + (cold ? 0.3 : 0.0) - (warm ? 0.3 : 0.0), dz);
            } else {
                double n = patch(x, z, 14.0, 4);
                topMaterial = n > 0.45 ? CLAY : n < -0.5 ? TUFF : GRAVEL;
            }
            double t = d / 4.0;
            if (t < 0.6) {
                return falling(topMaterial) && !supported(x, y, z) ? consolidated(topMaterial) : topMaterial;
            }
            if (t < 1.6) {
                return gradient(new BlockState[]{consolidated(topMaterial), consolidated(topMaterial), rockAlt()}, (t - 0.6), dz);
            }
            return bedrock(x, y, z, d);
        }

        /** Deep-sea trenches: magma vents and blackstone on the floor, basalt-banded walls. */
        private BlockState trench(int x, int y, int z, int d, boolean face, double dz) {
            if (face || y > col.trenchFloor + 8.0) {
                int band = (int) Math.floor((y + col.strata * 23.0 + dz * 1.5) / 6.0);
                return switch (Math.floorMod(band, 5)) {
                    case 0 -> BLACKSTONE;
                    case 1 -> BASALT;
                    case 2 -> TUFF;
                    case 3 -> SMOOTH_BASALT;
                    default -> DEEPSLATE;
                };
            }
            if (d == 0) {
                double vent = patch(x, z, 7.0, 7);
                if (vent > 0.35 || dz > 0.75) {
                    return MAGMA;
                }
                return dz > 0.1 ? BLACKSTONE : dz > -0.45 ? BASALT : GRAVEL;
            }
            if (d <= 3) {
                return gradient(new BlockState[]{BLACKSTONE, BASALT, MAGMA, BLACKSTONE}, hash01(x, y, z, 0x7EECL), dz * 0.3);
            }
            return d <= 6 ? gradient(new BlockState[]{BLACKSTONE, TUFF, DEEPSLATE}, (d - 3) / 3.0, dz) : null;
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
            return state == SAND || state == GRAVEL;
        }

        private static BlockState consolidated(BlockState state) {
            if (state == SAND) {
                return SANDSTONE;
            }
            if (state == GRAVEL || state == CLAY) {
                return TUFF;
            }
            return state;
        }

        /** Deepslate-zone counterpart of a block below Y 8 (with vanilla's 0..8 gradient). */
        private static BlockState deep(BlockState state, int x, int y, int z) {
            if (y >= 8 || y > 0 && hash01(x, y, z, 0xDEE95L) >= (8 - y) / 8.0) {
                return state == STONE ? null : state;
            }
            if (state == STONE || state == Blocks.GRANITE.defaultBlockState()) {
                return DEEPSLATE;
            }
            if (state == ANDESITE || state == Blocks.DIORITE.defaultBlockState() || state == SANDSTONE) {
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
            double n = patch(x, z, 5.0, 0);
            double hot = col.baseTemperature;
            double wet = col.humidity;
            BlockState result;
            if (col.lake > 0.0) {
                switch (col.lakeType) {
                    case LakeType.MOUNTAIN, LakeType.GLACIAL, LakeType.CRATER ->
                        result = n > 0.2 ? GRAVEL : (n < -0.4 ? STONE : COBBLESTONE);
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
                result = n > -0.1 ? GRAVEL : COBBLESTONE;
            } else if (col.dryRiver) {
                result = hot > 0.55 ? Blocks.RED_SAND.defaultBlockState() : SAND;
            } else if (hot > 0.5 && wet > 0.3 && n > -0.2) {
                result = MUD;
            } else if (n > 0.6 && d == 0) {
                result = CLAY;
            } else {
                result = n > -0.15 ? GRAVEL : SAND;
            }
            if ((falling(result) || result == Blocks.RED_SAND.defaultBlockState()) && !supported(x, y, z)) {
                return result == GRAVEL ? COBBLESTONE : SANDSTONE;
            }
            return result;
        }

        private BlockState volcanic(int x, int y, int z, int d, double dz) {
            double n = patch(x, z, 8.0, 2);
            if (col.crater > 0.0 && d <= 1 && n > 0.45) {
                return MAGMA;
            }
            if (d <= 2) {
                double v = n + dz * 0.25;
                if (v > 0.3) {
                    return BASALT;
                }
                if (v > -0.1) {
                    return BLACKSTONE;
                }
                if (v > -0.45) {
                    return TUFF;
                }
                return supported(x, y, z) ? GRAVEL : TUFF;
            }
            return strata(x, y, z, dz);
        }

        private BlockState canyonFloor(int x, int z, int d) {
            double n = patch(x, z, 9.0, 3);
            if (col.baseTemperature > 0.45) {
                return n > -0.3 ? Blocks.RED_SAND.defaultBlockState() : GRAVEL;
            }
            if (d == 0 && n < -0.2) {
                return null;
            }
            return n > 0.2 ? GRAVEL : COARSE_DIRT;
        }

        private BlockState forestFloor(int x, int z, Holder<Biome> biome) {
            double n = patch(x, z, 11.0, 1);
            if (n < 0.42 && n > -0.55) {
                return null;
            }
            if (biome.is(BiomeTags.IS_TAIGA) && !biome.is(Biomes.SNOWY_TAIGA)) {
                return n > 0.42 ? Blocks.PODZOL.defaultBlockState() : COARSE_DIRT;
            }
            if (biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
                return n > 0.55 ? Blocks.MOSS_BLOCK.defaultBlockState() : (n > 0.42 ? ROOTED_DIRT : COARSE_DIRT);
            }
            if (biome.is(BiomeTags.IS_FOREST) || biome.is(Biomes.FLOWER_FOREST)) {
                return n > 0.6 && col.humidity > 0.1 ? Blocks.MOSS_BLOCK.defaultBlockState() : (n < -0.55 ? COARSE_DIRT : null);
            }
            if (biome.is(BiomeTags.IS_JUNGLE)) {
                return n > 0.5 ? Blocks.MOSS_BLOCK.defaultBlockState() : null;
            }
            if (biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU) || biome.is(Biomes.WINDSWEPT_SAVANNA)) {
                return n < -0.6 ? COARSE_DIRT : null;
            }
            return null;
        }
    }
}
