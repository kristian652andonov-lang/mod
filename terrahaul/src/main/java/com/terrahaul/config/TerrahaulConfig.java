package com.terrahaul.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Terrahaul configuration.
 * <p>
 * <b>Common (world generation)</b> — {@code config/terrahaul-common.toml}. These settings change
 * how <em>new</em> chunks are generated. Already generated chunks are never rewritten. Changing
 * them in an existing world makes new chunks differ from old ones (for example forest density at
 * the border between old and new chunks), so they are best set before creating a world. Options
 * marked "restart" are read while data packs load and need a world reload.
 * <p>
 * <b>Client (visual only)</b> — {@code config/terrahaul-client.toml}. Falling leaves, petals, dust,
 * wind and vegetation sway. They never affect the world, the server or other players.
 */
public final class TerrahaulConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // common
    static final ModConfigSpec.BooleanValue REPLACE_VANILLA_TREES;
    static final ModConfigSpec.BooleanValue GROUND_DETAILS;
    static final ModConfigSpec.BooleanValue GEOLOGICAL_FORMATIONS;
    static final ModConfigSpec.BooleanValue ORE_DEPTH_FIX;
    static final ModConfigSpec.BooleanValue TERRAIN_AWARE_STRUCTURES;
    static final ModConfigSpec.BooleanValue SAPLINGS_GROW_TERRAHAUL_TREES;
    static final ModConfigSpec.DoubleValue TREE_SIZE_VARIATION;
    static final ModConfigSpec.DoubleValue FOREST_DENSITY;
    static final ModConfigSpec.DoubleValue VEGETATION_DENSITY;
    static final ModConfigSpec.DoubleValue GROUND_DETAIL_DENSITY;
    static final ModConfigSpec.DoubleValue ROCK_DENSITY;

    // client
    public static final ModConfigSpec.BooleanValue SELECT_TERRAHAUL_BY_DEFAULT;
    public static final ModConfigSpec.EnumValue<AnimationQuality> ANIMATION_QUALITY;
    public static final ModConfigSpec.BooleanValue FALLING_LEAVES;
    public static final ModConfigSpec.DoubleValue FALLING_LEAF_DENSITY;
    public static final ModConfigSpec.BooleanValue CHERRY_PETALS;
    public static final ModConfigSpec.BooleanValue DUST;
    public static final ModConfigSpec.BooleanValue AUTUMN_LEAF_FALL;
    public static final ModConfigSpec.BooleanValue VEGETATION_SWAY;
    public static final ModConfigSpec.DoubleValue WIND_STRENGTH;

    static {
        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        c.comment("World generation settings. They apply to newly generated chunks only; existing chunks are never",
            "rewritten. Set them before creating a world: changing them mid-save makes new chunks differ from old ones.").push("worldgen");
        REPLACE_VANILLA_TREES = c.comment("Replace vanilla Overworld trees with Terrahaul's procedural trees (all worlds). Restart required.")
            .define("replaceVanillaTrees", true);
        GROUND_DETAILS = c.comment("Generate decorative ground details: pebbles, rocks, branches, twigs, leaf litter, bushes, fallen logs and boulders. Restart required.")
            .define("groundDetails", true);
        GEOLOGICAL_FORMATIONS = c.comment("Generate natural arches, rock spires, hoodoos and boulder fields. Restart required.")
            .define("geologicalFormations", true);
        ORE_DEPTH_FIX = c.comment("Keep vanilla ores and cave decorations that are anchored to the world bottom at their vanilla depths,",
                "and add extra resource layers in Terrahaul's deep (Y -512..-64) and high (above Y 320) zones. Restart required.")
            .define("oreDepthFix", true);
        TERRAIN_AWARE_STRUCTURES = c.comment("Move villages, outposts, mansions and temples away from steep slopes and water in Terrahaul worlds",
                "(the candidate position inside each structure region is chosen by terrain). Restart required.")
            .define("terrainAwareStructures", true);
        SAPLINGS_GROW_TERRAHAUL_TREES = c.comment("Saplings grown by players also produce Terrahaul trees.")
            .define("saplingsGrowTerrahaulTrees", true);
        TREE_SIZE_VARIATION = c.comment("Chance (0-1) that a tree keeps a large or giant size when one is rolled. Lower values give more uniform, smaller trees.")
            .defineInRange("treeSizeVariation", 1.0, 0.0, 1.0);
        FOREST_DENSITY = c.comment("Multiplier for the number of trees in forests and woodlands.")
            .defineInRange("forestDensity", 1.0, 0.1, 3.0);
        VEGETATION_DENSITY = c.comment("Multiplier for bushes, shrubs and other low vegetation added by Terrahaul.")
            .defineInRange("vegetationDensity", 1.0, 0.0, 3.0);
        GROUND_DETAIL_DENSITY = c.comment("Multiplier for pebbles, branches, twigs, pine cones and leaf litter.")
            .defineInRange("groundDetailDensity", 1.0, 0.0, 3.0);
        ROCK_DENSITY = c.comment("Multiplier for medium rocks and boulders.")
            .defineInRange("rockDensity", 1.0, 0.0, 3.0);
        c.pop();
        COMMON_SPEC = c.build();

        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Client-side visual settings. These never change the world and are not sent to servers.").push("client");
        SELECT_TERRAHAUL_BY_DEFAULT = b.comment("Pre-select the Terrahaul world type on the Create World screen.")
            .define("selectTerrahaulByDefault", true);
        b.pop();
        b.comment("Environmental animations: falling leaves and petals, wind, dust and vegetation sway.").push("animations");
        ANIMATION_QUALITY = b.comment("Overall animation quality. OFF disables every Terrahaul animation; LOW reduces range and particle counts.")
            .defineEnum("quality", AnimationQuality.HIGH);
        FALLING_LEAVES = b.comment("Leaves drift down from tree canopies.").define("fallingLeaves", true);
        FALLING_LEAF_DENSITY = b.comment("Multiplier for the number of falling leaves.").defineInRange("fallingLeafDensity", 1.0, 0.0, 4.0);
        CHERRY_PETALS = b.comment("Cherry trees shed pink petals.").define("cherryPetals", true);
        DUST = b.comment("Wind lifts dust in deserts, badlands and dry savannas.").define("dust", true);
        AUTUMN_LEAF_FALL = b.comment("Autumn mode: much heavier leaf fall in temperate forests (purely visual, no seasonal cycle).").define("autumnLeafFall", false);
        VEGETATION_SWAY = b.comment("Grass, flowers, ferns, saplings and bushes sway in the wind (core shader; has no effect while a shader pack",
            "or a renderer replacement such as Sodium/Iris is active).").define("vegetationSway", true);
        WIND_STRENGTH = b.comment("Multiplier for wind strength in all animations.").defineInRange("windStrength", 1.0, 0.0, 3.0);
        b.pop();
        CLIENT_SPEC = b.build();
    }

    private TerrahaulConfig() {
    }

    public enum AnimationQuality {
        OFF, LOW, MEDIUM, HIGH
    }

    // Safe accessors: world generation can run before the config file has been read (for example
    // while a data generator or test bootstrap runs); fall back to defaults then.

    private static boolean bool(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    private static double number(ModConfigSpec.DoubleValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static boolean replaceVanillaTrees() {
        return bool(REPLACE_VANILLA_TREES);
    }

    public static boolean groundDetails() {
        return bool(GROUND_DETAILS);
    }

    public static boolean geologicalFormations() {
        return bool(GEOLOGICAL_FORMATIONS);
    }

    public static boolean oreDepthFix() {
        return bool(ORE_DEPTH_FIX);
    }

    public static boolean terrainAwareStructures() {
        return bool(TERRAIN_AWARE_STRUCTURES);
    }

    public static boolean saplingsGrowTerrahaulTrees() {
        return bool(SAPLINGS_GROW_TERRAHAUL_TREES);
    }

    public static double treeSizeVariation() {
        return number(TREE_SIZE_VARIATION);
    }

    public static double forestDensity() {
        return number(FOREST_DENSITY);
    }

    public static double vegetationDensity() {
        return number(VEGETATION_DENSITY);
    }

    public static double groundDetailDensity() {
        return number(GROUND_DETAIL_DENSITY);
    }

    public static double rockDensity() {
        return number(ROCK_DENSITY);
    }

    /** Looks up a boolean option by its key, for the {@code terrahaul:config} data pack condition. */
    public static boolean flag(String key) {
        return switch (key) {
            case "replace_vanilla_trees" -> replaceVanillaTrees();
            case "ground_details" -> groundDetails();
            case "geological_formations" -> geologicalFormations();
            case "ore_depth_fix" -> oreDepthFix();
            case "terrain_aware_structures" -> terrainAwareStructures();
            default -> false;
        };
    }

    /** Looks up a density multiplier by name, for the {@code terrahaul:density_count} placement modifier. */
    public static double density(String key) {
        return switch (key) {
            case "forest" -> forestDensity();
            case "vegetation" -> vegetationDensity();
            case "ground_detail" -> groundDetailDensity();
            case "rocks" -> rockDensity();
            default -> 1.0;
        };
    }
}
