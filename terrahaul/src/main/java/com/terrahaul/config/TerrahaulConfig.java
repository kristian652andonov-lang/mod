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
 * wind, vegetation sway, and the spray, mist, foam and sound of falling water. They never affect the
 * world, the server or other players.
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
    static final ModConfigSpec.BooleanValue TRAILS;
    static final ModConfigSpec.BooleanValue OCEAN_VENTS;
    static final ModConfigSpec.DoubleValue TREE_SIZE_VARIATION;
    static final ModConfigSpec.DoubleValue FOREST_DENSITY;
    static final ModConfigSpec.DoubleValue VEGETATION_DENSITY;
    static final ModConfigSpec.DoubleValue GROUND_DETAIL_DENSITY;
    static final ModConfigSpec.DoubleValue ROCK_DENSITY;

    // client
    public static final ModConfigSpec.BooleanValue SELECT_TERRAHAUL_BY_DEFAULT;
    public static final ModConfigSpec.IntValue CLOUD_HEIGHT;
    public static final ModConfigSpec.BooleanValue DISTANCE_FOG;
    public static final ModConfigSpec.EnumValue<AnimationQuality> ANIMATION_QUALITY;
    public static final ModConfigSpec.BooleanValue FALLING_LEAVES;
    public static final ModConfigSpec.DoubleValue FALLING_LEAF_DENSITY;
    public static final ModConfigSpec.BooleanValue CHERRY_PETALS;
    public static final ModConfigSpec.BooleanValue DUST;
    public static final ModConfigSpec.BooleanValue AUTUMN_LEAF_FALL;
    public static final ModConfigSpec.BooleanValue VEGETATION_SWAY;
    public static final ModConfigSpec.DoubleValue WIND_STRENGTH;
    public static final ModConfigSpec.BooleanValue WATERFALL_SPRAY;
    public static final ModConfigSpec.BooleanValue WATERFALL_MIST;
    public static final ModConfigSpec.BooleanValue WATER_FOAM;
    public static final ModConfigSpec.BooleanValue WATER_SOUNDS;
    public static final ModConfigSpec.DoubleValue WATER_EFFECT_DENSITY;
    public static final ModConfigSpec.DoubleValue WATER_SOUND_VOLUME;
    public static final ModConfigSpec.DoubleValue WATER_SOUND_RANGE;

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
        ORE_DEPTH_FIX = c.comment("In Terrahaul worlds, keep vanilla ores, geodes, springs and cave decorations at their vanilla depths",
                "(vanilla anchors many of them to the world bottom, which is 448 blocks deeper here) and add extra resource layers",
                "in the deep (Y -512..-64) and high (above Y 320) zones. The extra layers need a restart.")
            .define("oreDepthFix", true);
        TERRAIN_AWARE_STRUCTURES = c.comment("Move villages, outposts, mansions and temples away from steep slopes, cliffs and water in Terrahaul",
                "worlds (the position inside each structure region is chosen by terrain). Set it before creating a world: changing it",
                "later moves structures that have not been generated yet.")
            .define("terrainAwareStructures", true);
        SAPLINGS_GROW_TERRAHAUL_TREES = c.comment("Saplings grown by players also produce Terrahaul trees.")
            .define("saplingsGrowTerrahaulTrees", true);
        TRAILS = c.comment("Lay winding dirt trails across gentle ground in Terrahaul worlds.")
            .define("trails", true);
        OCEAN_VENTS = c.comment("Build hydrothermal vent chimneys on the floors of deep-sea trenches and mid-ocean ridges in Terrahaul worlds. Restart required.")
            .define("oceanVents", true);
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
        CLOUD_HEIGHT = b.comment("Height of the cloud layer in Terrahaul worlds (vanilla clouds are at 192, inside Terrahaul's hills).")
            .defineInRange("cloudHeight", 448, 192, 2000);
        DISTANCE_FOG = b.comment("Distance fog, the haze at the edge of the view. Set to false to turn it off in every world; fog under",
            "water or lava, in powder snow and from blindness or darkness stays. A shader pack draws its own fog.")
            .define("distanceFog", true);
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
        b.comment("Effects of falling and running water: waterfalls, cascades and rapids, in every world (vanilla falls too).",
            "The particles follow the animation quality above and Minecraft's Particles option (none at Minimal).").push("water");
        WATERFALL_SPRAY = b.comment("Spray drifts off falling water, drops fly from the lip and the water splashes up where it lands.")
            .define("waterfallSpray", true);
        WATERFALL_MIST = b.comment("Mist rolls off the foot of falls; tall falls raise a large, slowly billowing cloud.")
            .define("waterfallMist", true);
        WATER_FOAM = b.comment("White foam floats on the water below falls and on rapids and drifts downstream.")
            .define("waterFoam", true);
        WATER_SOUNDS = b.comment("Tall falls roar, cascades and rapids rush (Ambient/Environment sound category).")
            .define("waterSounds", true);
        WATER_EFFECT_DENSITY = b.comment("Multiplier for the number of water particles.").defineInRange("waterEffectDensity", 1.0, 0.0, 3.0);
        WATER_SOUND_VOLUME = b.comment("Multiplier for the volume of the water sounds.").defineInRange("waterSoundVolume", 1.0, 0.0, 2.0);
        WATER_SOUND_RANGE = b.comment("Multiplier for how far the water sounds are heard (the largest falls reach 48 blocks at 1.0).")
            .defineInRange("waterSoundRange", 1.0, 0.25, 1.3);
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

    public static boolean trails() {
        return bool(TRAILS);
    }

    public static boolean oceanVents() {
        return bool(OCEAN_VENTS);
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

    // client options; only read on the client

    private static <T extends Enum<T>> T enumValue(ModConfigSpec.EnumValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static boolean selectTerrahaulByDefault() {
        return bool(SELECT_TERRAHAUL_BY_DEFAULT);
    }

    public static boolean distanceFog() {
        return bool(DISTANCE_FOG);
    }

    public static int cloudHeight() {
        try {
            return CLOUD_HEIGHT.get();
        } catch (IllegalStateException e) {
            return CLOUD_HEIGHT.getDefault();
        }
    }

    public static AnimationQuality animationQuality() {
        return enumValue(ANIMATION_QUALITY);
    }

    public static boolean fallingLeaves() {
        return bool(FALLING_LEAVES);
    }

    public static double fallingLeafDensity() {
        return number(FALLING_LEAF_DENSITY);
    }

    public static boolean cherryPetals() {
        return bool(CHERRY_PETALS);
    }

    public static boolean dust() {
        return bool(DUST);
    }

    public static boolean autumnLeafFall() {
        return bool(AUTUMN_LEAF_FALL);
    }

    public static boolean vegetationSway() {
        return bool(VEGETATION_SWAY);
    }

    public static double windStrength() {
        return number(WIND_STRENGTH);
    }

    public static boolean waterfallSpray() {
        return bool(WATERFALL_SPRAY);
    }

    public static boolean waterfallMist() {
        return bool(WATERFALL_MIST);
    }

    public static boolean waterFoam() {
        return bool(WATER_FOAM);
    }

    public static boolean waterSounds() {
        return bool(WATER_SOUNDS);
    }

    public static double waterEffectDensity() {
        return number(WATER_EFFECT_DENSITY);
    }

    public static double waterSoundVolume() {
        return number(WATER_SOUND_VOLUME);
    }

    public static double waterSoundRange() {
        return number(WATER_SOUND_RANGE);
    }

    /** Looks up a boolean option by its key, for the {@code terrahaul:config} data pack condition. */
    public static boolean flag(String key) {
        return switch (key) {
            case "replace_vanilla_trees" -> replaceVanillaTrees();
            case "ground_details" -> groundDetails();
            case "geological_formations" -> geologicalFormations();
            case "ore_depth_fix" -> oreDepthFix();
            case "terrain_aware_structures" -> terrainAwareStructures();
            case "ocean_vents" -> oceanVents();
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
