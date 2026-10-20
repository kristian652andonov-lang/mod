package com.terrahaul.worldgen.surface;

import com.mojang.serialization.MapCodec;
import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.features.GeologyPalette;
import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.LakeType;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.VolcanoZones;
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
 * wavy bands and broad patches instead of speckles; no material choice uses per-block random
 * numbers (the arithmetic lives in {@link Fades}). Families:
 * <ul>
 *   <li>grey rock: tuff, andesite, stone (mountains, temperate and granite provinces);</li>
 *   <li>dark rock: tuff, smooth basalt, deepslate;</li>
 *   <li>pale rock: calcite, diorite, stone (limestone provinces);</li>
 *   <li>sandstone and red sandstone (sedimentary lowlands and deserts; badlands keep vanilla's
 *       bands);</li>
 *   <li>volcanic rock: blackstone, basalt, smooth basalt;</li>
 *   <li>soil: dirt and coarse dirt over grey rock; snow over calcite and diorite; sand over
 *       sandstone.</li>
 * </ul>
 * All rock below the soil is one continuous field of strata that cycle through the family in
 * horizontal beds of irregular thickness, so cliffs, canyon walls, overhang undersides and cave
 * roofs show level bands that line up from column to column instead of vertical seams. Snow and
 * rock on peaks, and grass and rock on steep hillsides, are separated by the slope of the smoothed
 * terrain, so rock outcrops and snowfields are coherent areas, and an outcrop is rock all the way
 * down rather than a rock crust on dirt. Below Y 8 rock turns into tuff and deepslate along a
 * smooth, wavy boundary.
 * <p>
 * <b>Mountains have no level beds.</b> Level strata drew contour lines all the way round a
 * mountain, so mountain rock (grey, dark and pale provinces, desert mountains included, and the
 * high plateau massifs, windswept peaks and steep high hills of those provinces) is a mosaic of
 * tilted blobs of the family, a second rock of the province, rubble and gravel instead, with
 * patchy snow that fills gullies, clings to high faces and leaves ridges bare, earth showing
 * through the snow, and grass, moss, dirt and gravel patches on ledges (see
 * {@link Fades#mountainBlock}). Foothills meet the strata in interlocking blobs; canyons,
 * badlands, sea cliffs and volcanoes keep their own bands, and so do cave roofs deep inside a
 * mountain. Trails run on across the mosaic.
 * <p>
 * Fades never reach deeper than {@value #MAX_DEPTH} blocks below the nearest exposed surface.
 * That distance is measured both vertically and sideways, so cliff and mountain faces, trench
 * walls and overhangs get the same treatment as flat ground, while solid rock deep inside a
 * mountain stays plain.
 * <p>
 * <b>Landforms.</b>
 * <ul>
 *   <li>river, lake and canyon beds get gravel, sand, clay or mud depending on climate;</li>
 *   <li>volcanoes have a bare cone of ash and volcanic rock, lava flows and scorched burn scars
 *       on otherwise forested flanks (see {@link VolcanoZones});</li>
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
        private static final BlockState TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
        private static final BlockState MOSS_BLOCK = Blocks.MOSS_BLOCK.defaultBlockState();
        private static final BlockState GRASS_BLOCK = Blocks.GRASS_BLOCK.defaultBlockState();
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

        /** Slope beyond which desert sand gives way to sandstone outcrops. */
        private static final double SAND_ROCK = 1.1;

        private final SurfaceRules.Context ctx;
        private final TerrainModel model;
        private final int seaLevel;
        private final ChunkPos chunkPos;
        private final boolean trails;
        private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        private final double[] blendScratch = new double[Fades.BLEND_FIELDS];
        /** The model's height field, for the gullies and ridges of the mountain mosaic. */
        private final Fades.Heights heights;

        private int lastX = Integer.MIN_VALUE;
        private int lastZ = Integer.MIN_VALUE;
        /** Column fields: categorical ones from the nearest model corner, continuous ones interpolated. */
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
        /** Biome at the top of the column, which picks sandstone families in deserts. */
        private Holder<Biome> topBiome;
        /** {@link Fades#rockiness} of grassy ground; positive: the column is a rock outcrop. */
        private double outcrop;
        /** The same for desert sand, which slides off at a steeper slope. */
        private double sandOutcrop;
        /** Per-column values of the mountain mosaic ({@code share = 0}: not a mountain). */
        private final Fades.Mountain mountain = new Fades.Mountain();
        /** The province's second rock in the mosaic (see {@link GeologyPalette#mountainRock}). */
        private BlockState mountainRock;
        /** Whether the current block lies in the mountain mosaic rather than the strata. */
        private boolean mosaic;
        /** Whether the nearest open air of the current block is below it: a cave roof or an overhang's underside. */
        private boolean roof;
        /** Column part of the strata height (see {@link Fades#strataWarp}). */
        private double strataWarp;
        /** Depth of the current block below the top of its column, or below open air above it. */
        private int vertical;

        Rule(SurfaceRules.Context ctx, TerrainModel model) {
            this.ctx = ctx;
            this.model = model;
            this.seaLevel = model.seaLevel();
            this.chunkPos = ctx.chunk.getPos();
            this.trails = TerrahaulConfig.trails();
            this.heights = model::interpolatedHeight;
        }

        private void updateColumn(int x, int z) {
            lastX = x;
            lastZ = z;
            // categorical fields come from the nearest interpolation corner; continuous ones are
            // interpolated like the height, so landform boundaries follow smooth curves
            col.copyFrom(model.sample((x + 2) & ~3, (z + 2) & ~3));
            Fades.blend(model, x, z, col, blendScratch);
            // province boundaries wander a few blocks instead of following the 4-block model grid
            int gx = x + (int) Math.round(3.0 * patch(x, z, 13.0, 2));
            int gz = z + (int) Math.round(3.0 * patch(x, z, 13.0, 5));
            col.geology = model.sample((gx + 2) & ~3, (gz + 2) & ~3).geology;
            top = topAt(x, z);
            int max = 0;
            for (int i = -2; i <= 2; i += 4) {
                max = Math.max(max, Math.abs(topAt(x + i, z) - top));
                max = Math.max(max, Math.abs(topAt(x, z + i) - top));
            }
            slope = max;
            smoothSlope = Fades.smoothSlope(model, x, z);
            rugged = slope >= 3 || col.steepness > 0.3 || col.cliff > 0.1 || col.trench > 0.15 || col.canyon > 0.1 || col.coastCliff > 0.2;
            if (rugged) {
                for (int i = 0; i < RING.length; i++) {
                    int k = RING[i];
                    ringTop[i] = Math.min(Math.min(topAt(x + k, z), topAt(x - k, z)), Math.min(topAt(x, z + k), topAt(x, z - k)));
                }
            }
            // thinner soil on mountains and steep ground; both terms are smooth so the base of
            // the soil does not jump from column to column
            double thin = 1.0 - 0.4 * Fades.smoothstep(0.25, 0.55, col.mountain);
            soilDepth = Math.max(1.5, (3.0 + 4.0 * Math.max(0.0, 1.0 - smoothSlope / 2.5) + 1.5 * patch(x, z, 23.0, 5)) * thin);
            trailDistance = trails ? model.trailDistance(x, z) : 1.0E9;
            trailGround = trailDistance < 4.0 && TerrainModel.trailGround(col, seaLevel);
            outcrop = rockiness(x, z, 0.95);
            // the same noise as the outcrops, at the steeper limit of sand
            sandOutcrop = outcrop + 0.95 - SAND_ROCK;
            strataWarp = Fades.strataWarp(model, x, z);
            family = family();
            // mountains and other high, steep ground: rock in chaotic blobs instead of level beds
            // (see Fades.mountainBlock); sandstone country keeps its beds, and volcanic rock its
            // own bands
            mountain.share = 0.0;
            double share = family == GREY || family == DARK || family == PALE ? Fades.mountainShare(col, smoothSlope, top - seaLevel) : 0.0;
            if (share > 0.0) {
                int ground = ground(topBiome);
                Fades.mountain(model, x, z, col, share, ground, topBiome.value().getBaseTemperature(), smoothSlope, heights, mountain);
                mountainRock = GeologyPalette.mountainRock(col.geology);
                if (ground == Fades.GROUND_SOIL || ground == Fades.GROUND_SAND) {
                    // outcrops with ragged edges and small rock blobs poking through
                    outcrop = mountain.rock;
                    sandOutcrop = mountain.rock;
                }
            }
        }

        /** What covers the rock of a mountain column, by its biome. */
        private static int ground(Holder<Biome> biome) {
            if (isSnowy(biome)) {
                return Fades.GROUND_SNOW;
            }
            if (isRocky(biome) && !biome.is(Biomes.WINDSWEPT_GRAVELLY_HILLS)) {
                return Fades.GROUND_ROCK;
            }
            return isSandy(biome) ? Fades.GROUND_SAND : Fades.GROUND_SOIL;
        }

        /**
         * The rock family of this column: grey on every mountain (or the dark or pale variant of
         * its province), otherwise the province's own; sandstone in deserts and on beaches.
         */
        private BlockState[] family() {
            if (col.volcano > 0.3 || col.geology == Geology.VOLCANIC) {
                return VOLCANIC;
            }
            boolean mountainous = col.mountain > 0.3 || col.alpine > 0.3;
            return switch (col.geology) {
                case Geology.DARK -> DARK;
                case Geology.LIMESTONE -> PALE;
                case Geology.SEDIMENTARY -> mountainous ? GREY : col.baseTemperature > 0.45 ? RED : SANDY;
                default -> !mountainous && isSandy(topBiome) && !topBiome.is(Biomes.SNOWY_BEACH) ? SANDY : GREY;
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
            return Fades.patch(model, x, z, scale, channel);
        }

        /** Smooth 3D noise in about [-1, 1] that makes the boundaries between gradient stops wavy. */
        private double wobble(int x, int y, int z) {
            return Fades.wobble(model, x, y, z);
        }

        /**
         * Picks a stop of a gradient at position {@code t} (0..1). The boundaries between stops move
         * with the smooth noise {@code n} (see {@link Fades#stop}), so neighbouring blocks almost
         * always agree and the fade forms patches and wavy bands rather than speckles.
         */
        private static BlockState gradient(BlockState[] stops, double t, double n) {
            return stops[Fades.stop(stops.length, t, n)];
        }

        @Override
        public BlockState tryApply(int x, int y, int z) {
            if (x != lastX || z != lastZ) {
                topBiome = ctx.biome.get();
                updateColumn(x, z);
            }
            int above = ctx.stoneDepthAbove - 1;
            vertical = top - y <= OPEN_POCKET ? above : top - y;
            int lateral = rugged ? lateral(y) : Integer.MAX_VALUE;
            // rock above open air: the underside of an overhang or arch, or a cave roof
            int below = ctx.stoneDepthBelow - 1;
            int side = Math.min(lateral, below);
            int d = Math.min(vertical, side);
            if (d > MAX_DEPTH) {
                return y < 8 ? (Fades.deep(model, x, y, z) ? DEEPSLATE : STONE) : null;
            }
            // a face: exposed sideways or from below, or on a very steep column. The two blocks
            // above open air are always rock, so undersides and cave roofs never show soil.
            boolean face = side < vertical - 1 || slope >= 6 || vertical > 0 && below <= 1;
            // the lip of an overhang counts too: its underside is open below and to the side
            roof = vertical > 0 && (below == 0 || below < Math.min(vertical, lateral));
            BlockState state = choose(x, y, z, d, face);
            return y < 8 ? deep(state, x, y, z, d) : state;
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
            // ---- dry riverbeds: a wash of gravel bars and pale sand, in the badlands too -------------
            if (col.dryRiver && d <= 1 && !face && !underFluid && !planned) {
                BlockState wash = dryWash(x, y, z);
                if (wash != null) {
                    return wash;
                }
            }
            if (biome.is(BiomeTags.IS_BADLANDS) && !(d <= 2 && (underFluid || planned) && (col.river > 0.0 || col.lake > 0.0 || planned))) {
                // keep vanilla's terracotta bands everywhere except on the beds of rivers and lakes
                return null;
            }
            // cave roofs deep inside a mountain keep the strata: the mosaic is a surface pattern
            mosaic = mountain.share > 0.0 && !(roof && vertical > MAX_DEPTH) && Fades.inMosaic(model, mountain, x, y, z);
            // the wobble is read only once a path needs it: the mountain mosaic does without
            double n = Double.NaN;

            // ---- deep-sea trenches and mid-ocean ridges -----------------------------------------
            if (underFluid && col.trench > 0.3 && y < col.trenchFloor + 60.0) {
                return trench(x, y, z, d, face, wobble(x, y, z));
            }
            // ---- beds of rivers and lakes --------------------------------------------------------
            if (d <= 2 && (col.river > 0.0 || col.lake > 0.0 || planned) && (underFluid || planned || y < seaLevel)) {
                return bedMaterial(x, y, z, d);
            }
            // ---- volcanoes: bare cone, lava flows and burn scars (forests keep the normal soil) -----
            if (col.volcano > 0.0) {
                n = wobble(x, y, z);
                BlockState v = volcanic(x, y, z, d, face, underFluid, biome, n);
                if (v != null) {
                    return v;
                }
            }
            // ---- mountains: rock in chaotic blobs, patchy snow, plants on ledges ---------------------
            if (mosaic && !underFluid) {
                if (!face) {
                    // trails, pebble beaches and river bars run on across the mosaic as they did
                    // across the strata
                    BlockState landform = mosaicLandform(x, y, z, d, biome);
                    if (landform != null) {
                        return landform;
                    }
                }
                // roofs of caves and overhangs stay solid rock (no snow, earth or rubble hanging
                // from them: those lie only in the top few blocks)
                int code = roof ? Fades.rock(model, mountain, x, y, z, Math.max(d, 4))
                    : Fades.mountainBlock(model, mountain, x, y, z, d, d == 0 && vertical == 0, face);
                if (code != Fades.SOIL) {
                    return mosaicBlock(code, x, y, z, d, biome);
                }
            }
            if (Double.isNaN(n)) {
                n = wobble(x, y, z);
            }
            // ---- cliffs, canyon walls, escarpments and other exposed faces ------------------------
            if (face && !underFluid || slope >= 4 && col.cliff > 0.3 && d <= 6) {
                return strata(x, y, z, d, n);
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
                return d <= 3 ? bareRock(x, y, z, d, n) : bedrock(x, y, z, d, n);
            }
            // ---- scree on steep alpine slopes (smoothed slope: whole aprons, not single columns) ----
            if (col.mountain > 0.3 && smoothSlope > 0.75 && d <= 1 && !underFluid && col.peakness > 0.35) {
                double p = patch(x, z, 9.0, 2);
                if (p > 0.35) {
                    return supported(x, y, z) ? loose() : family[1];
                }
            }
            // ---- coasts: pebbly beaches under rocky cliffs ---------------------------------------
            if (col.coastCliff > 0.3 && d <= 2 && y <= seaLevel + 2 && y >= seaLevel - 6) {
                return patch(x, z, 6.0, 3) > 0.0 && supported(x, y, z) ? loose() : family[2];
            }
            // ---- river banks: gravel bars and sandbars --------------------------------------------
            if (col.riverValley > 0.85 && col.river <= 0.0 && d <= 1 && y <= col.riverLevel + 2.0 && !underFluid) {
                double p = patch(x, z, 7.0, 0);
                if (p > 0.35 && supported(x, y, z)) {
                    return barMaterial(x, z);
                }
            }
            // ---- wetland margins --------------------------------------------------------------
            if (col.swamp > 0.4 && d <= 1 && y >= seaLevel - 3 && y <= seaLevel + 1) {
                return MUD;
            }
            if (d == 0 && vertical == 0) {
                return underFluid ? null : topBlock(x, y, z, biome, n);
            }
            // blocks exposed only sideways near the top (the riser of a small step) show the soil
            // profile like a cut through the ground, never top materials such as paths or podzol
            return profile(x, y, z, Math.max(d, 1), biome, n);
        }

        /**
         * What the rule's landform paths would lay on a block of the mountain mosaic before the
         * mosaic itself: trails on soil ground, pebbles under coastal cliffs and bars along river
         * banks, the same tests as further down in {@link #choose}; null where none applies.
         */
        private BlockState mosaicLandform(int x, int y, int z, int d, Holder<Biome> biome) {
            if (mountain.ground != Fades.GROUND_SOIL && mountain.ground != Fades.GROUND_SAND) {
                // snowy and stony peaks came before these paths
                return null;
            }
            if (d == 0 && vertical == 0 && trailGround && slope <= 3 && mountain.ground == Fades.GROUND_SOIL && !isRocky(biome)) {
                BlockState trail = trail(x, y, z, wobble(x, y, z));
                if (trail != null) {
                    return trail;
                }
            }
            if (col.coastCliff > 0.3 && d <= 2 && y <= seaLevel + 2 && y >= seaLevel - 6) {
                return patch(x, z, 6.0, 3) > 0.0 && supported(x, y, z) ? loose() : family[2];
            }
            if (col.riverValley > 0.85 && col.river <= 0.0 && d <= 1 && y <= col.riverLevel + 2.0
                && patch(x, z, 7.0, 0) > 0.35 && supported(x, y, z)) {
                return barMaterial(x, z);
            }
            return null;
        }

        // =====================================================================================
        // top blocks: trails and the fringes between surface materials; null keeps vanilla's top
        // =====================================================================================

        /** How far past the slope at which ground turns to bare rock this column is (negative: below it). */
        private double rockiness(int x, int z, double limit) {
            return Fades.rockiness(model, x, z, smoothSlope, limit);
        }

        private BlockState topBlock(int x, int y, int z, Holder<Biome> biome, double n) {
            if (isSandy(biome) && sandOutcrop > 0.0) {
                // sand slides off steep desert slopes and leaves sandstone outcrops
                return bareRock(x, y, z, 0, n);
            }
            boolean soil = !isSandy(biome) && !isRocky(biome);
            if (soil && trailGround && slope <= 3) {
                BlockState trail = trail(x, y, z, n);
                if (trail != null) {
                    return trail;
                }
            }
            if (soil && biome.value().getBaseTemperature() > 0.1 && !mosaic) {
                // in temperate biomes just below the snow line, snowfields grow as one climbs
                // (on mountains the mosaic lays its own snow patches)
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
                double r = outcrop;
                if (r > 0.0) {
                    return bareRock(x, y, z, 0, n);
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
                    return slope >= 3 && supported(x, y, z) ? GRAVEL : COARSE_DIRT;
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
                return d <= 3 ? bareRock(x, y, z, d, n) : bedrock(x, y, z, d, n);
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
                if (sandOutcrop > 0.0) {
                    return d <= 3 ? bareRock(x, y, z, d, n) : bedrock(x, y, z, d, n);
                }
                double t = d / Math.max(2.0, soilDepth * 0.8);
                if (t < 0.8 + 0.25 * n) {
                    return supported(x, y, z) ? SAND : SANDSTONE;
                }
                if (t < 2.6) {
                    return gradient(UNDER_SAND, (t - 0.8) / 1.8, n);
                }
                return bedrock(x, y, z, d, n);
            }
            if (outcrop > 0.0) {
                // a rock outcrop is rock all the way down, not a crust of rock on dirt
                return d <= 3 ? bareRock(x, y, z, d, n) : bedrock(x, y, z, d, n);
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

        /**
         * Bare rock at the surface: broad patches of the family, turning to fresh rock a few blocks
         * in; on mountains the rock mosaic.
         */
        private BlockState bareRock(int x, int y, int z, int d, double n) {
            if (mosaic) {
                return mosaicBlock(Fades.rock(model, mountain, x, y, z, d), x, y, z, d, null);
            }
            return family[Fades.bareRockStop(model, family.length, x, z, d, n)];
        }

        /**
         * Rock below the fades. It is the same strata field as on exposed faces everywhere, so a
         * cliff, a canyon wall or a ravine cut shows the same level beds whichever way each column
         * was classified (a different material below non-face columns drew vertical seams).
         */
        private BlockState bedrock(int x, int y, int z, int d, double n) {
            return strata(x, y, z, d, n);
        }

        /**
         * Exposed rock: strata that cycle smoothly through the family (for grey rock: stone,
         * andesite, tuff, andesite, stone, ...). Beds are about 4-6 blocks thick but vary, tilt and
         * wave with low-frequency noise, and their boundaries are softened by the 3D noise. The
         * layers are a continuous function of the block position (see {@link Fades#strata}).
         * Mountains have no level beds: there the rock is the mosaic of blobs (see
         * {@link Fades#rock}), which is just as continuous, so faces and the rock behind them
         * still agree.
         */
        private BlockState strata(int x, int y, int z, int d, double n) {
            if (mosaic) {
                return mosaicBlock(Fades.rock(model, mountain, x, y, z, d), x, y, z, d, null);
            }
            double period = col.geology == Geology.SEDIMENTARY ? Fades.SEDIMENTARY_PERIOD : Fades.ROCK_PERIOD;
            return family[Fades.strataStop(model, family.length, x, y, z, strataWarp, period, n)];
        }

        /** Sea floor: sand in the shallows giving way to gravel with depth; clay and tuff in the abyss. */
        private BlockState seabed(int x, int y, int z, int d, boolean face, double n) {
            if (face) {
                return strata(x, y, z, d, n);
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
                return TRENCH_WALL[Fades.strataStop(model, TRENCH_WALL.length, x, y, z, strataWarp, 18.0, n)];
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

        /**
         * The block of a mosaic material ({@link Fades#mountainBlock}). Gravel and sand that would
         * fall turn into cobblestone and sandstone; frozen peaks keep their patches of packed ice
         * on gentle snow ({@code biome} may be null where no snow can be asked for).
         */
        private BlockState mosaicBlock(int code, int x, int y, int z, int d, Holder<Biome> biome) {
            BlockState cobble = family == DARK ? COBBLED_DEEPSLATE : COBBLESTONE;
            return switch (code) {
                case Fades.ROCK_PALE, Fades.ROCK_MID, Fades.ROCK_FRESH -> family[code];
                case Fades.ROCK_PROVINCE -> mountainRock;
                case Fades.COBBLE -> cobble;
                case Fades.MOSSY_COBBLE -> MOSSY_COBBLESTONE;
                case Fades.GRAVEL -> supported(x, y, z) ? GRAVEL : cobble;
                case Fades.EARTH -> col.geology == Geology.GRANITE ? GRANITE : TERRACOTTA;
                case Fades.COARSE_DIRT -> COARSE_DIRT;
                case Fades.DIRT -> DIRT;
                case Fades.MOSS -> MOSS_BLOCK;
                case Fades.GRASS -> GRASS_BLOCK;
                case Fades.SAND -> supported(x, y, z) ? SAND : SANDSTONE;
                case Fades.SANDSTONE -> SANDSTONE;
                default -> d == 0 && smoothSlope < 1.05 && biome != null && biome.is(Biomes.FROZEN_PEAKS)
                    && patch(x, z, 14.0, 7) > 0.55 ? PACKED_ICE : SNOW_BLOCK;
            };
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

        /** Loose sediment of the rock family: gravel for grey rock, sand for sandstone. */
        private BlockState loose() {
            if (family == SANDY) {
                return SAND;
            }
            return family == RED ? RED_SAND : GRAVEL;
        }

        /**
         * Material below Y 8. Rock in the deepslate zone (whose upper boundary is a smooth surface,
         * see {@link Fades#deep}) turns into its dark counterpart; sediments stay. Where the rule
         * has no material of its own ({@code state == null}) below the top block, it still decides
         * between stone and deepslate, so vanilla's random deepslate gradient never speckles an
         * exposed surface.
         */
        private BlockState deep(BlockState state, int x, int y, int z, int d) {
            boolean deep = Fades.deep(model, x, y, z);
            if (state == null) {
                return d == 0 ? null : deep ? DEEPSLATE : STONE;
            }
            if (!deep) {
                return state;
            }
            if (state == STONE || state == GRANITE) {
                return DEEPSLATE;
            }
            if (state == ANDESITE || state == DIORITE || state == CALCITE || state == SANDSTONE || state == SMOOTH_SANDSTONE
                || state == RED_SANDSTONE || state == SMOOTH_RED_SANDSTONE || state == ORANGE_TERRACOTTA || state == TERRACOTTA) {
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

        /** Gravel or sand bars along a river bank; one material per bar, sand in the warm lowlands. */
        private BlockState barMaterial(int x, int z) {
            double sandy = patch(x, z, 90.0, 7) + (col.baseTemperature > 0.4 ? 0.3 : 0.0) - (col.mountain > 0.2 ? 0.6 : 0.0);
            return sandy > 0.0 ? SAND : GRAVEL;
        }

        /**
         * Beds of rivers and lakes. Each lake, and each stretch of river, is lined with one
         * sediment chosen by climate and a slowly varying selector (hundreds of blocks), so beds
         * read as sandy, gravelly, muddy or clayey as a whole; small patches only mix materials of
         * one colour (gravel and grey rock in the mountains).
         */
        private BlockState bedMaterial(int x, int y, int z, int d) {
            double n = patch(x, z, 6.0, 0);
            double reach = patch(x, z, 80.0, 6);
            double hot = col.baseTemperature;
            double wet = col.humidity;
            BlockState result;
            if (col.lake > 0.0) {
                switch (col.lakeType) {
                    case LakeType.MOUNTAIN, LakeType.GLACIAL, LakeType.CRATER -> result = n > 0.0 ? GRAVEL : family[2];
                    case LakeType.MARSH -> result = MUD;
                    case LakeType.GREAT -> result = hot < -0.25 || reach < -0.45 ? GRAVEL : SAND;
                    default -> {
                        if (hot > 0.3 && wet > 0.3) {
                            result = MUD;
                        } else if (reach > 0.3 && hot < 0.5) {
                            result = CLAY;
                        } else {
                            result = reach > -0.25 ? SAND : GRAVEL;
                        }
                    }
                }
            } else if (col.mountain > 0.3) {
                result = n > -0.3 ? GRAVEL : family[2];
            } else if (col.dryRiver) {
                result = hot > 0.55 ? RED_SAND : SAND;
            } else if (hot > 0.5 && wet > 0.3) {
                result = MUD;
            } else {
                result = reach + (hot > 0.35 ? 0.3 : -0.1) > 0.0 ? SAND : GRAVEL;
            }
            if (falling(result) && !supported(x, y, z)) {
                return consolidated(result);
            }
            return result;
        }

        /**
         * The floor of a river that has run dry: gravel bars and sheets of pale sand with a few
         * cobbles, in patches a few blocks across, and patches of the same on the banks right beside
         * it, so the old channel shows against the land around it (red sand and terracotta in
         * the badlands, sand or grass elsewhere). The bed is otherwise above any water, so the bed
         * rule (which needs water) never reaches it. Returns {@code null} where the ground keeps
         * its normal surface.
         */
        private BlockState dryWash(int x, int y, int z) {
            double bars = patch(x, z, 5.0, 3);
            if (col.river <= 0.0) {
                // the banks right beside the old channel (out to 80% of its half-width), in patches
                // that fray out away from it
                double out = col.riverDistance - 1.0;
                if (col.riverWidth <= 0.0 || out > 0.8 || bars < 0.1 + 0.9 * out) {
                    return null;
                }
            }
            BlockState result;
            if (bars > 0.55 && patch(x, z, 2.0, 7) > 0.35) {
                result = COBBLESTONE;
            } else if (bars > -0.05) {
                result = GRAVEL;
            } else {
                result = SAND;
            }
            if (falling(result) && !supported(x, y, z)) {
                return consolidated(result);
            }
            return result;
        }

        /**
         * Volcanoes, by the ecological zones of {@link VolcanoZones} (shared with the vegetation
         * filter, so snags stand on scorched ground and forests on soil):
         * <ul>
         *   <li>crater, upper cone and lava flows are bare rock and ash a few blocks deep over
         *       banded volcanic rock, in single-colour patches and radial streaks; above the snow
         *       line the cone of a snowy biome keeps its snow cap, in colder air of other biomes
         *       snowfields lie on its gentle ground;</li>
         *   <li>burn scars have a scorched top block (charred litter, scorched soil, root mats,
         *       ash) over the normal soil profile;</li>
         *   <li>the forested flanks, and any terrain that rises above the cone, return
         *       {@code null}: grass and soil as everywhere else, so animals spawn there too.</li>
         * </ul>
         * Exposed faces also return {@code null} and get the banded rock of the face rule.
         */
        private BlockState volcanic(int x, int y, int z, int d, boolean face, boolean underFluid, Holder<Biome> biome, double n) {
            VolcanoZones.Zone zone = VolcanoZones.at(model, x, z);
            if (face || zone.presence <= 0.0) {
                return null;
            }
            VolcanoZones.Ground ground = VolcanoZones.ground(model, zone, x, top, z);
            if (!ground.bare()) {
                if (ground == VolcanoZones.Ground.SOIL || d > 0 || underFluid || isSnowy(biome) || isSandy(biome)) {
                    return null;
                }
                return switch (ground) {
                    case CHARRED -> Blocks.PODZOL.defaultBlockState();
                    case BURNT_ROOTS -> ROOTED_DIRT;
                    case CINDERS -> supported(x, y, z) ? GRAVEL : COARSE_DIRT;
                    default -> COARSE_DIRT;
                };
            }
            if (isSnowy(biome) && !zone.inFlow()) {
                // snow-capped cone: snow on gentle ground over volcanic rock (see snowy)
                return null;
            }
            if (d > 3) {
                return strata(x, y, z, d, n);
            }
            if (d == 0 && !underFluid && col.temperature < -0.3 && smoothSlope < 1.0
                && 0.5 + 0.5 * patch(x, z, 16.0, 6) < Math.min(0.9, (-0.3 - col.temperature) / 0.17 * 0.8)) {
                return SNOW_BLOCK;
            }
            return switch (ground) {
                case ASH -> d == 0 && supported(x, y, z) ? GRAVEL : TUFF;
                case SCORIA -> SMOOTH_BASALT;
                case LAVA_ROCK -> BLACKSTONE;
                case MAGMA -> d <= 1 ? MAGMA : BLACKSTONE;
                default -> TUFF;
            };
        }

        private BlockState canyonFloor(int x, int z, int d) {
            // dry canyon floors: sand of the wall's colour in sandstone canyons, otherwise grass
            // with coarse-dirt patches and gravel washes; loose material only on solid ground
            int y = ctx.blockY;
            double p = patch(x, z, 10.0, 3);
            if (family == RED || family == SANDY) {
                BlockState sand = family == RED ? RED_SAND : SAND;
                return p > -0.3 && supported(x, y, z) ? sand : consolidated(sand);
            }
            if (d == 0 && p < -0.2) {
                return null;
            }
            return p > 0.2 && supported(x, y, z) ? GRAVEL : COARSE_DIRT;
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
