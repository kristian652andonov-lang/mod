package com.terrahaul.worldgen.trees;

import com.terrahaul.Terrahaul;
import com.terrahaul.config.TerrahaulConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;

/**
 * Makes saplings grow into Terrahaul trees: the vanilla tree a sapling would grow is swapped for the
 * matching {@code terrahaul:sapling/*} configured feature. 2x2 sapling groups (dark oak, mega
 * spruce, mega jungle) map to large trees with 2x2 trunks, which vanilla then places from the
 * north-west sapling exactly as it would its own mega trees. Those features are marked
 * {@code "sapling": true}, so like vanilla trees they replace only air, leaves, water and plants
 * and never the blocks a player put around the sapling. Controlled by
 * {@code saplingsGrowTerrahaulTrees} in the common config.
 */
public final class SaplingGrowthHandler {
    private SaplingGrowthHandler() {
    }

    public static void onGrow(BlockGrowFeatureEvent event) {
        Holder<ConfiguredFeature<?, ?>> feature = event.getFeature();
        if (feature == null || !TerrahaulConfig.saplingsGrowTerrahaulTrees()) {
            return;
        }
        String path = feature.unwrapKey().map(k -> k.location().getNamespace().equals("minecraft") ? k.location().getPath() : null).orElse(null);
        if (path == null) {
            return;
        }
        String replacement = switch (path) {
            case "oak", "fancy_oak", "oak_bees_0002", "oak_bees_002", "oak_bees_005", "fancy_oak_bees_0002", "fancy_oak_bees_002",
                 "fancy_oak_bees_005", "fancy_oak_bees", "oak_bees" -> "oak";
            case "birch", "birch_bees_0002", "birch_bees_002", "birch_bees_005", "super_birch_bees_0002", "super_birch_bees", "birch_bees" -> "birch";
            case "spruce", "pine" -> "spruce";
            case "mega_spruce", "mega_pine" -> "spruce_large";
            case "jungle_tree", "jungle_tree_no_vine" -> "jungle";
            case "mega_jungle_tree" -> "jungle_large";
            case "acacia" -> "acacia";
            case "dark_oak" -> "dark_oak";
            case "cherry", "cherry_bees_005" -> "cherry";
            case "mangrove", "tall_mangrove" -> "mangrove";
            case "azalea_tree" -> "azalea";
            default -> null;
        };
        if (replacement != null) {
            event.setFeature(ResourceKey.create(Registries.CONFIGURED_FEATURE, Terrahaul.id("sapling/" + replacement)));
        }
    }
}
