package com.fantasyweapons.datagen;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.world.gen.FittedHeightPlacement;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Where the legendary ores generate. They are split across the three dimensions by element - void and cosmic in the End;
 * fire, blood and soul in the Nether; earth, nature, ice, storm, death, sun, light and aether in the Overworld - and every
 * one is rare: a small vein (one to three ores) in only one chunk in a few of its own biomes.
 */
public final class FWWorldGen {
    private FWWorldGen() {
    }

    /**
     * @param size   ore feature size (3 gives veins of one to three ores)
     * @param chunks the base rarity: one vein in this many chunks of the right biomes (generated at one and a half times it)
     * @param ground for an ore that belongs under particular ground: the height of its biomes' ground in a vanilla world,
     *               which its height moves with in taller worlds (see FittedHeightPlacement); 0 for a fixed height
     */
    record Spec(Function<HolderGetter<Biome>, HolderSet<Biome>> biomes, List<RuleTest> replaces, int size, int chunks, int minY, int maxY,
                float airDiscard, boolean nearLava, int ground) {
        Spec(Function<HolderGetter<Biome>, HolderSet<Biome>> biomes, List<RuleTest> replaces, int size, int chunks, int minY, int maxY,
             float airDiscard, boolean nearLava) {
            this(biomes, replaces, size, chunks, minY, maxY, airDiscard, nearLava, 0);
        }
    }

    /** Where each ore generates: one or more placements (a different biome or height each). */
    static final Map<Material, List<Spec>> SPECS = new EnumMap<>(Material.class);

    private static void put(Material m, Spec... specs) {
        SPECS.put(m, List.of(specs));
    }

    static {
        RuleTest stone = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), deepslate = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
        RuleTest netherStone = new TagMatchTest(BlockTags.BASE_STONE_NETHER), endStone = new BlockMatchTest(Blocks.END_STONE);
        // the End
        put(Material.VOID_CRYSTAL, new Spec(b -> biomes(b, Biomes.END_HIGHLANDS, Biomes.END_MIDLANDS, Biomes.END_BARRENS, Biomes.SMALL_END_ISLANDS),
                List.of(endStone), 3, 3, 25, 65, 0f, false));
        // the island tops, under the open night sky
        put(Material.ASTRALITE, new Spec(b -> biomes(b, Biomes.END_HIGHLANDS), List.of(endStone), 3, 4, 35, 70, 0f, false));
        // the Nether
        put(Material.INFERNITE, new Spec(b -> biomes(b, Biomes.BASALT_DELTAS), List.of(netherStone), 3, 0, 10, 117, 0f, true));
        put(Material.BLOODSTONE, new Spec(b -> biomes(b, Biomes.CRIMSON_FOREST), List.of(netherStone), 3, 5, 10, 117, 0.3f, false));
        put(Material.SOULSHARD, new Spec(b -> biomes(b, Biomes.SOUL_SAND_VALLEY),
                List.of(netherStone, new BlockMatchTest(Blocks.SOUL_SOIL), new BlockMatchTest(Blocks.SOUL_SAND)), 3, 5, 10, 117, 0.3f, false));
        // the Overworld. The ores that belong under particular ground carry the height of their biomes' ground in a vanilla
        // world (the middle of what a survey of vanilla worlds found), so in taller worlds they move with the ground (see
        // FittedHeightPlacement); Eclipsite and Titanstone keep to their heights by the deepslate, which those worlds share
        put(Material.SOLARITE, new Spec(b -> biomes(b, Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS, Biomes.DESERT),
                List.of(stone, new TagMatchTest(BlockTags.TERRACOTTA), new BlockMatchTest(Blocks.SANDSTONE)), 3, 6, 0, 60, 0.3f, false, 110));
        List<RuleTest> frost = List.of(stone, new BlockMatchTest(Blocks.PACKED_ICE), new BlockMatchTest(Blocks.SNOW_BLOCK));
        // the ice spikes plains are low: there it lies just under the surface
        put(Material.FROSTHEART, new Spec(b -> biomes(b, Biomes.FROZEN_PEAKS), frost, 3, 4, 60, 200, 0f, false, 125),
                new Spec(b -> biomes(b, Biomes.ICE_SPIKES), frost, 3, 7, 40, 75, 0f, false, 67));
        put(Material.THUNDERCRYST, new Spec(b -> biomes(b, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST,
                Biomes.STONY_PEAKS, Biomes.JAGGED_PEAKS), List.of(stone), 3, 3, 90, 180, 0f, false, 140));
        put(Material.NECROSTONE, new Spec(b -> biomes(b, Biomes.DEEP_DARK), List.of(deepslate, new BlockMatchTest(Blocks.SCULK)), 3, 3, -60, -10,
                0f, false, 120));
        put(Material.VERDANT, new Spec(b -> biomes(b, Biomes.LUSH_CAVES),
                List.of(stone, deepslate, new BlockMatchTest(Blocks.MOSS_BLOCK), new BlockMatchTest(Blocks.CLAY)), 3, 2, -40, 40, 0f, false, 90));
        put(Material.ECLIPSITE, new Spec(b -> b.getOrThrow(BiomeTags.IS_OVERWORLD), List.of(stone, deepslate), 3, 12, -20, 20, 0.5f, false));
        put(Material.AETHERITE, new Spec(b -> biomes(b, Biomes.MEADOW, Biomes.CHERRY_GROVE), List.of(stone), 3, 3, 70, 160, 0f, false, 105));
        put(Material.TITANSTONE, new Spec(b -> b.getOrThrow(BiomeTags.IS_OVERWORLD), List.of(deepslate), 3, 10, -64, -30, 0.5f, false));
    }

    /** How deep under the ground its ore goes at most, in a world where its height moves with the ground. */
    public static int deepest(Material m) {
        return SPECS.getOrDefault(m, List.of()).stream().filter(s -> s.ground() != 0).mapToInt(s -> s.ground() - s.minY()).max().orElse(0);
    }

    @SafeVarargs
    private static HolderSet<Biome> biomes(HolderGetter<Biome> getter, ResourceKey<Biome>... keys) {
        return HolderSet.direct(java.util.Arrays.stream(keys).map(getter::getOrThrow).toList());
    }

    private static String name(Material m, int i) {
        return i == 0 ? m.oreId() : m.oreId() + "_" + (i + 1);
    }

    public static ResourceKey<ConfiguredFeature<?, ?>> configuredKey(Material m, int i) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, FantasyWeapons.id(name(m, i)));
    }

    public static ResourceKey<PlacedFeature> placedKey(Material m, int i) {
        return ResourceKey.create(Registries.PLACED_FEATURE, FantasyWeapons.id(name(m, i)));
    }

    static ResourceKey<BiomeModifier> modifierKey(Material m, int i) {
        return ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, FantasyWeapons.id("add_" + name(m, i)));
    }

    public static RegistrySetBuilder builder() {
        return new RegistrySetBuilder()
                .add(Registries.CONFIGURED_FEATURE, FWWorldGen::configured)
                .add(Registries.PLACED_FEATURE, FWWorldGen::placed)
                .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, FWWorldGen::modifiers);
    }

    private static void configured(BootstrapContext<ConfiguredFeature<?, ?>> ctx) {
        SPECS.forEach((m, specs) -> {
            Block ore = ModBlocks.ORES.get(m).get();
            for (int i = 0; i < specs.size(); i++) {
                Spec spec = specs.get(i);
                List<OreConfiguration.TargetBlockState> targets = spec.replaces().stream().map(r -> OreConfiguration.target(r, ore.defaultBlockState())).toList();
                ctx.register(configuredKey(m, i), new ConfiguredFeature<>(Feature.ORE, new OreConfiguration(targets, spec.size(), spec.airDiscard())));
            }
        });
    }

    private static void placed(BootstrapContext<PlacedFeature> ctx) {
        HolderGetter<ConfiguredFeature<?, ?>> features = ctx.lookup(Registries.CONFIGURED_FEATURE);
        SPECS.forEach((m, specs) -> {
          for (int i = 0; i < specs.size(); i++) {
            Spec spec = specs.get(i);
            List<PlacementModifier> mods = new ArrayList<>();
            if (spec.nearLava()) {
                // tries a few spots in each chunk of the deltas (four and a half on average) and keeps only those right beside lava
                mods.add(CountPlacement.of(9));
                mods.add(RarityFilter.onAverageOnceEvery(2));
            } else {
                // three tries a chunk, each at half the base chance: half again as many veins as one try at the base chance
                mods.add(CountPlacement.of(3));
                mods.add(RarityFilter.onAverageOnceEvery(spec.chunks() * 2));
            }
            mods.add(InSquarePlacement.spread());
            var height = UniformHeight.of(VerticalAnchor.absolute(spec.minY()), VerticalAnchor.absolute(spec.maxY()));
            mods.add(spec.ground() != 0 ? new FittedHeightPlacement(height, spec.ground()) : HeightRangePlacement.of(height));
            if (spec.nearLava()) mods.add(BlockPredicateFilter.forPredicate(lavaNearby()));
            mods.add(BiomeFilter.biome());
            ctx.register(placedKey(m, i), new PlacedFeature(features.getOrThrow(configuredKey(m, i)), mods));
          }
        });
    }

    /** Lava within two blocks of the spot, in any of the six directions. */
    private static BlockPredicate lavaNearby() {
        List<BlockPredicate> any = new ArrayList<>();
        for (int d = 1; d <= 2; d++) {
            for (Vec3i dir : new Vec3i[]{new Vec3i(1, 0, 0), new Vec3i(-1, 0, 0), new Vec3i(0, 1, 0), new Vec3i(0, -1, 0), new Vec3i(0, 0, 1),
                    new Vec3i(0, 0, -1)}) {
                any.add(BlockPredicate.matchesFluids(dir.multiply(d), Fluids.LAVA, Fluids.FLOWING_LAVA));
            }
        }
        return BlockPredicate.anyOf(any);
    }

    private static void modifiers(BootstrapContext<BiomeModifier> ctx) {
        HolderGetter<Biome> biomes = ctx.lookup(Registries.BIOME);
        HolderGetter<PlacedFeature> placed = ctx.lookup(Registries.PLACED_FEATURE);
        SPECS.forEach((m, specs) -> {
            for (int i = 0; i < specs.size(); i++) {
                ctx.register(modifierKey(m, i), new BiomeModifiers.AddFeaturesBiomeModifier(specs.get(i).biomes().apply(biomes),
                        HolderSet.direct(placed.getOrThrow(placedKey(m, i))), GenerationStep.Decoration.UNDERGROUND_ORES));
            }
        });
    }
}
