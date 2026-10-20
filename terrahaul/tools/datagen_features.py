#!/usr/bin/env python3
"""Generates Terrahaul's configured features, placed features and NeoForge biome modifiers.

Usage: python3 tools/datagen_features.py <vanilla-data-dir>
  <vanilla-data-dir> is the extracted "data/minecraft" directory of the 1.21.1 client resources;
  it is read to copy a few vanilla feature definitions that Terrahaul adapts.
Output goes to src/main/resources/data and is committed.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'data')
VANILLA = None  # extracted vanilla data/minecraft directory, set from the command line


def write(path, obj):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def configured(name, ftype, config):
    write(f'terrahaul/worldgen/configured_feature/{name}.json', {"type": ftype, "config": config})


def placed(name, feature, placement):
    write(f'terrahaul/worldgen/placed_feature/{name}.json', {"feature": feature, "placement": placement})


def add_features(name, biomes, features, step):
    write(f'terrahaul/neoforge/biome_modifier/{name}.json',
          {"type": "neoforge:add_features", "biomes": biomes, "features": features, "step": step})


def remove_features(name, biomes, features, steps=None):
    obj = {"type": "neoforge:remove_features", "biomes": biomes, "features": features}
    if steps:
        obj["steps"] = steps
    write(f'terrahaul/neoforge/biome_modifier/{name}.json', obj)


def terrain_cleanup():
    # floating pieces of terrain are removed first in every chunk (no-op outside Terrahaul worlds)
    configured('terrain_cleanup', 'terrahaul:terrain_cleanup', {})
    placed('terrain_cleanup', 'terrahaul:terrain_cleanup', [])
    add_features('terrain_cleanup', '#minecraft:is_overworld', 'terrahaul:terrain_cleanup', 'raw_generation')
    # and again after everything that carves or builds terrain, before any plant
    configured('terrain_cleanup_late', 'terrahaul:terrain_cleanup_late', {})
    placed('terrain_cleanup_late', 'terrahaul:terrain_cleanup_late', [])
    add_features('terrain_cleanup_late', '#minecraft:is_overworld', 'terrahaul:terrain_cleanup_late', 'fluid_springs')


def oceans():
    # hydrothermal vents on trench floors and ridge crests (no-op outside Terrahaul worlds)
    configured('ocean_vents', 'terrahaul:ocean_vents', {})
    placed('ocean_vents', 'terrahaul:ocean_vents', [])
    add_features('ocean_vents', '#minecraft:is_ocean', 'terrahaul:ocean_vents', 'local_modifications')
    with_conditions('terrahaul/neoforge/biome_modifier/ocean_vents.json', [{"type": "terrahaul:config", "key": "ocean_vents"}])
    sea_floor()


OCEAN_BIOMES = ["minecraft:ocean", "minecraft:deep_ocean", "minecraft:warm_ocean", "minecraft:lukewarm_ocean",
                "minecraft:deep_lukewarm_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean",
                "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean"]
# vanilla ocean decoration that Terrahaul's sea floors replace: coral, sea pickles, kelp and seagrass,
# and the round sand, clay and gravel disks (the surface rule already patches sediment coherently)
OCEAN_REPLACED = {
    "vegetal_decoration": ["warm_ocean_vegetation", "sea_pickle", "kelp_cold", "kelp_warm", "seagrass_warm", "seagrass_normal",
                           "seagrass_cold", "seagrass_deep", "seagrass_deep_cold", "seagrass_deep_warm"],
    "underground_ores": ["disk_sand", "disk_clay", "disk_gravel"],
}
STEP_INDEX = {"raw_generation": 0, "lakes": 1, "local_modifications": 2, "underground_structures": 3,
              "surface_structures": 4, "strongholds": 5, "underground_ores": 6, "underground_decoration": 7,
              "fluid_springs": 8, "vegetal_decoration": 9, "top_layer_modification": 10}


def vanilla_biome_step(biome, step):
    with open(os.path.join(VANILLA, 'worldgen', 'biome', biome.split(':')[1] + '.json')) as f:
        steps = json.load(f)["features"]
    i = STEP_INDEX[step]
    return steps[i] if i < len(steps) else []


def sea_floor():
    # Terrahaul sea floors; no-ops outside Terrahaul worlds (they need the terrain model). Added to every
    # overworld biome: grotto chambers and sea columns can lie in chunks without an ocean biome nearby,
    # and the features return at once away from the sea
    for name, step, conditions in (("seafloor_rocks", "local_modifications", CONFIG_DETAILS),
                                   ("sea_grottos", "underground_decoration", CONFIG_FORMATIONS),
                                   ("coral_reefs", "vegetal_decoration", None),
                                   ("sea_vegetation", "vegetal_decoration", None)):
        configured(name, 'terrahaul:' + name, {})
        placed(name, 'terrahaul:' + name, [])
        add_features('sea/' + name, '#minecraft:is_overworld', 'terrahaul:' + name, step)
        if conditions:
            with_conditions(f'terrahaul/neoforge/biome_modifier/sea/{name}.json', conditions)
    # vanilla coral, kelp, seagrass and disks leave the ocean biomes; terrahaul:vanilla_fallback puts each
    # back with its vanilla placement but only places it outside Terrahaul worlds, because biome
    # modifiers apply to every world using these biomes
    for step, features in OCEAN_REPLACED.items():
        holders = {}
        for feature in features:
            holders[feature] = [b for b in OCEAN_BIOMES if 'minecraft:' + feature in vanilla_biome_step(b, step)]
        biomes = sorted({b for bs in holders.values() for b in bs})
        remove_features(f'sea/remove_vanilla_{step}', biomes, ['minecraft:' + f for f in features], [step])
        for feature in features:
            with open(os.path.join(VANILLA, 'worldgen', 'placed_feature', feature + '.json')) as f:
                vanilla = json.load(f)
            configured(f'sea/vanilla/{feature}', 'terrahaul:vanilla_fallback', {"feature": vanilla["feature"]})
            placed(f'sea/vanilla/{feature}', f'terrahaul:sea/vanilla/{feature}', vanilla["placement"])
            add_features(f'sea/vanilla/{feature}', holders[feature], f'terrahaul:sea/vanilla/{feature}', step)


def water():
    # both features are no-ops outside Terrahaul worlds (they need the terrain model)
    configured('surface_water', 'terrahaul:surface_water', {})
    placed('surface_water', 'terrahaul:surface_water', [])
    add_features('surface_water', '#minecraft:is_overworld', 'terrahaul:surface_water', 'lakes')
    configured('waterfall', 'terrahaul:waterfall', {})
    placed('waterfall', 'terrahaul:waterfall', [])
    # decoration of planned waterfalls and cascades (built in the lakes step by surface_water)
    add_features('waterfall', '#minecraft:is_overworld', 'terrahaul:waterfall', 'vegetal_decoration')


CONFIG_TREES = [{"type": "terrahaul:config", "key": "replace_vanilla_trees"}]


def with_conditions(path, conditions):
    full = os.path.join(ROOT, path)
    with open(full) as f:
        obj = json.load(f)
    obj = {"neoforge:conditions": conditions, **obj}
    write(path, obj)


def tree_config(name, species, sizes, vines=0.0, cocoa=0.0, beehive=0.0, dry=0.0, sapling=False):
    # weights are integers in vanilla weighted lists: scale by 100 so fractional weights survive
    cfg = {"species": species, "sizes": [{"data": k, "weight": max(1, int(round(w * 100)))} for k, w in sizes.items()]}
    if vines:
        cfg["vines"] = vines
    if cocoa:
        cfg["cocoa"] = cocoa
    if beehive:
        cfg["beehive"] = beehive
    if dry:
        cfg["dry_leaves"] = dry
    if sapling:
        cfg["sapling"] = True
    configured(name, 'terrahaul:procedural_tree', cfg)


def ref(feature):
    return {"feature": feature, "placement": []}


def selector(name, entries, default):
    """entries: list of (configured feature id, chance) evaluated in order; default otherwise."""
    configured(name, 'minecraft:random_selector', {
        "features": [{"chance": c, "feature": ref(f)} for f, c in entries],
        "default": ref(default),
    })


def tree_placement(count, scale=260, variation=0.7, clearing=-2.0, water_depth=0, slope=7, density="forest"):
    return [
        {"type": "terrahaul:density_count", "count": count, "density": density, "scale": scale,
         "variation": variation, "clearing": clearing},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": water_depth},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "terrahaul:tree_line", "max_slope": slope},
        # living trees keep out of volcano burn scars, lava flows and bare cones (see VolcanoZones)
        {"type": "terrahaul:volcano_zone", "zone": "living"},
        {"type": "minecraft:biome"},
    ]


SMALLISH = {"young": 2, "small": 5, "medium": 3}
# ancient trees are landmarks: their trunk moves to the middle of its chunk (see ProceduralTreeFeature),
# so their weights stay low enough that they never line up on the chunk grid
MIXED = {"small": 3, "medium": 6, "large": 2, "giant": 0.4, "ancient": 0.03}

VANILLA_TREE_FEATURES = [
    "minecraft:trees_birch", "minecraft:trees_cherry", "minecraft:trees_water", "minecraft:dark_forest_vegetation",
    "minecraft:trees_flower_forest", "minecraft:trees_birch_and_oak", "minecraft:trees_grove", "minecraft:trees_snowy",
    "minecraft:trees_jungle", "minecraft:trees_mangrove", "minecraft:trees_meadow", "minecraft:birch_tall",
    "minecraft:trees_old_growth_pine_taiga", "minecraft:trees_old_growth_spruce_taiga", "minecraft:trees_plains",
    "minecraft:trees_savanna", "minecraft:trees_taiga", "minecraft:trees_sparse_jungle", "minecraft:trees_swamp",
    "minecraft:trees_windswept_forest", "minecraft:trees_windswept_hills", "minecraft:trees_windswept_savanna",
    "minecraft:trees_badlands", "minecraft:bamboo_vegetation",
]


def trees():
    T = 'terrahaul:tree/'
    # ---- species / size mixes --------------------------------------------------------------
    tree_config('tree/oak', 'oak', MIXED)
    tree_config('tree/oak_bees', 'oak', MIXED, beehive=0.02)
    tree_config('tree/oak_lone', 'oak', {"medium": 3, "large": 4, "giant": 2, "ancient": 1}, beehive=0.05)
    tree_config('tree/oak_meadow', 'oak', {"medium": 2, "large": 3}, beehive=0.6)
    tree_config('tree/oak_young', 'oak', {"young": 3, "small": 2})
    tree_config('tree/oak_small_dry', 'oak', {"young": 2, "small": 5, "medium": 2}, dry=0.15)
    tree_config('tree/swamp_oak', 'swamp_oak', {"small": 2, "medium": 6, "large": 3, "giant": 0.6, "ancient": 0.1}, vines=0.12)
    tree_config('tree/birch', 'birch', {"small": 3, "medium": 6, "large": 2})
    tree_config('tree/birch_bees', 'birch', {"small": 3, "medium": 6, "large": 2}, beehive=0.02)
    tree_config('tree/birch_meadow', 'birch', {"medium": 2, "large": 3}, beehive=0.6)
    tree_config('tree/birch_tall', 'birch', {"medium": 2, "large": 5, "giant": 3})
    tree_config('tree/birch_young', 'birch', {"young": 3, "small": 2})
    tree_config('tree/spruce', 'spruce', MIXED)
    tree_config('tree/spruce_small', 'spruce', SMALLISH)
    tree_config('tree/spruce_old_growth', 'spruce', {"medium": 2, "large": 5, "giant": 3, "ancient": 0.4})
    tree_config('tree/spruce_young', 'spruce', {"young": 3, "small": 2})
    tree_config('tree/pine', 'pine', {"medium": 4, "large": 4, "giant": 1.5})
    tree_config('tree/pine_old_growth', 'pine', {"large": 5, "giant": 3, "ancient": 0.35})
    tree_config('tree/fir', 'fir', {"small": 3, "medium": 5, "large": 2, "giant": 0.4})
    tree_config('tree/jungle_giant', 'jungle', {"large": 4, "giant": 3, "ancient": 0.5}, vines=0.15, cocoa=0.06)
    tree_config('tree/jungle_medium', 'jungle', {"small": 2, "medium": 5}, vines=0.25, cocoa=0.12)
    tree_config('tree/jungle_bush', 'jungle', {"young": 1})
    tree_config('tree/acacia', 'acacia', {"small": 3, "medium": 5, "large": 3, "giant": 1, "ancient": 0.3})
    tree_config('tree/acacia_small', 'acacia', {"young": 2, "small": 4, "medium": 2})
    tree_config('tree/dark_oak', 'dark_oak', {"medium": 4, "large": 5, "giant": 2, "ancient": 0.15})
    tree_config('tree/mangrove', 'mangrove', {"small": 3, "medium": 5, "large": 3, "giant": 1}, vines=0.05)
    tree_config('tree/cherry', 'cherry', {"small": 2, "medium": 5, "large": 3, "giant": 1, "ancient": 0.15}, beehive=0.05)
    tree_config('tree/cherry_young', 'cherry', {"young": 3, "small": 1})
    tree_config('tree/azalea', 'azalea', {"small": 3, "medium": 3, "large": 1})
    tree_config('tree/dead', 'dead', {"small": 3, "medium": 3, "large": 1})
    tree_config('tree/dead_spruce', 'dead_spruce', {"small": 2, "medium": 3, "large": 2})
    # fire-killed snags for burned ground (placed by the volcano ecology)
    tree_config('tree/burned', 'burned', {"small": 3, "medium": 4, "large": 3, "giant": 1})
    tree_config('tree/burned_small', 'burned', {"young": 2, "small": 4, "medium": 1})

    # ---- biome mixes ---------------------------------------------------------------------------
    groups = {
        # name: (biomes, [(selector entries)], default, placement kwargs)
        'plains_lone': (['minecraft:plains', 'minecraft:sunflower_plains'], [(T + 'birch', 0.15)], T + 'oak_lone',
                        dict(count=0.16, variation=1.4, scale=900)),
        'plains_groves': (['minecraft:plains', 'minecraft:sunflower_plains'], [(T + 'birch', 0.25), (T + 'oak_young', 0.2)], T + 'oak_bees',
                          dict(count=2.5, variation=1.0, scale=380, clearing=0.42)),
        'forest': (['minecraft:forest'], [(T + 'birch', 0.27)], T + 'oak_bees', dict(count=6.0, scale=230, variation=0.7, clearing=-0.72)),
        'forest_understory': (['minecraft:forest', 'minecraft:flower_forest'], [(T + 'birch_young', 0.35)], T + 'oak_young',
                              dict(count=2.5, scale=230, variation=0.6)),
        'flower_forest': (['minecraft:flower_forest'], [(T + 'birch_bees', 0.4)], T + 'oak_bees', dict(count=3.5, scale=200, variation=0.8, clearing=-0.6)),
        'birch_forest': (['minecraft:birch_forest'], [(T + 'birch_young', 0.2)], T + 'birch_bees', dict(count=7.0, scale=220, variation=0.6, clearing=-0.8)),
        'old_growth_birch_forest': (['minecraft:old_growth_birch_forest'], [(T + 'birch', 0.35)], T + 'birch_tall',
                                    dict(count=6.5, scale=240, variation=0.5, clearing=-0.85)),
        'dark_forest': (['minecraft:dark_forest'], [(T + 'oak', 0.12), (T + 'birch', 0.09), (T + 'dark_oak', 1.0)], T + 'dark_oak',
                        dict(count=7.5, scale=260, variation=0.45, clearing=-0.9)),
        # conifer stands: an old-growth conifer's crown covers about twice the ground of a broadleaf of
        # the same size class, so these stands plant fewer, bigger trees and keep a light floor
        # (see the treeStudy "stand" mode)
        'taiga': (['minecraft:taiga'], [(T + 'pine', 0.25), (T + 'fir', 0.18), (T + 'dead_spruce', 0.04)], T + 'spruce',
                  dict(count=4.0, scale=240, variation=0.6, clearing=-0.75)),
        'taiga_understory': (['minecraft:taiga', 'minecraft:snowy_taiga', 'minecraft:old_growth_spruce_taiga', 'minecraft:old_growth_pine_taiga'],
                             [], T + 'spruce_young', dict(count=2.0, scale=240, variation=0.6)),
        'snowy_taiga': (['minecraft:snowy_taiga'], [(T + 'fir', 0.3)], T + 'spruce', dict(count=4.0, scale=240, variation=0.6, clearing=-0.75)),
        'old_growth_spruce_taiga': (['minecraft:old_growth_spruce_taiga'], [(T + 'spruce', 0.35)], T + 'spruce_old_growth',
                                    dict(count=2.6, scale=280, variation=0.45, clearing=-0.9)),
        'old_growth_pine_taiga': (['minecraft:old_growth_pine_taiga'], [(T + 'spruce_old_growth', 0.35)], T + 'pine_old_growth',
                                  dict(count=3.2, scale=280, variation=0.45, clearing=-0.9)),
        'grove': (['minecraft:grove'], [(T + 'spruce', 0.4)], T + 'fir', dict(count=4.0, scale=200, variation=0.7, clearing=-0.6, slope=9)),
        'snowy_plains': (['minecraft:snowy_plains', 'minecraft:ice_spikes'], [(T + 'dead_spruce', 0.1)], T + 'spruce_small',
                         dict(count=0.3, scale=600, variation=1.2)),
        'windswept_hills': (['minecraft:windswept_hills', 'minecraft:windswept_gravelly_hills'], [(T + 'oak_small_dry', 0.45)], T + 'spruce_small',
                            dict(count=0.8, scale=300, variation=1.0, slope=9)),
        # fewer spruces than before the conifers grew (2.6 instead of 3.25 per chunk), as many oaks (1.75)
        'windswept_forest': (['minecraft:windswept_forest'], [(T + 'oak', 0.4)], T + 'spruce', dict(count=4.35, scale=240, variation=0.6, slope=9)),
        'meadow': (['minecraft:meadow'], [(T + 'birch_meadow', 0.4)], T + 'oak_meadow', dict(count=0.12, scale=600, variation=1.2)),
        'cherry_grove': (['minecraft:cherry_grove'], [(T + 'cherry_young', 0.15)], T + 'cherry', dict(count=3.5, scale=180, variation=0.8, clearing=-0.7)),
        'jungle_giants': (['minecraft:jungle'], [(T + 'oak', 0.06)], T + 'jungle_giant', dict(count=2.0, scale=220, variation=0.6)),
        'jungle_medium': (['minecraft:jungle', 'minecraft:bamboo_jungle'], [], T + 'jungle_medium', dict(count=4.0, scale=180, variation=0.6)),
        'jungle_bushes': (['minecraft:jungle', 'minecraft:bamboo_jungle', 'minecraft:sparse_jungle'], [], T + 'jungle_bush',
                          dict(count=7.0, scale=150, variation=0.5)),
        'sparse_jungle': (['minecraft:sparse_jungle'], [(T + 'jungle_giant', 0.2)], T + 'jungle_medium', dict(count=1.4, scale=300, variation=1.0)),
        'bamboo_jungle': (['minecraft:bamboo_jungle'], [], T + 'jungle_giant', dict(count=0.5, scale=300, variation=1.0)),
        'savanna': (['minecraft:savanna', 'minecraft:savanna_plateau'], [(T + 'dead', 0.05), (T + 'oak_small_dry', 0.06)], T + 'acacia',
                    dict(count=1.3, scale=420, variation=1.1)),
        'windswept_savanna': (['minecraft:windswept_savanna'], [(T + 'oak_small_dry', 0.3)], T + 'acacia_small', dict(count=1.0, scale=300, variation=1.0, slope=9)),
        'wooded_badlands': (['minecraft:wooded_badlands'], [(T + 'dead', 0.3)], T + 'oak_small_dry', dict(count=4.0, scale=220, variation=0.8)),
        'swamp': (['minecraft:swamp'], [], T + 'swamp_oak', dict(count=2.8, scale=220, variation=0.7, water_depth=2)),
        'mangrove_swamp': (['minecraft:mangrove_swamp'], [], T + 'mangrove', dict(count=7.0, scale=200, variation=0.5, water_depth=4)),
        'river_banks': (['minecraft:river'], [(T + 'birch', 0.3)], T + 'oak', dict(count=0.06, scale=300, variation=1.0)),
        'azaleas': (['minecraft:forest', 'minecraft:flower_forest', 'minecraft:meadow', 'minecraft:birch_forest'], [], T + 'azalea',
                    dict(count=0.12, scale=300, variation=1.2)),
    }
    for name, (biomes, entries, default, kw) in groups.items():
        if entries:
            selector('trees/' + name, entries, default)
            feature = 'terrahaul:trees/' + name
        else:
            feature = default
        placed('trees/' + name, feature, tree_placement(**kw))
        add_features('trees/' + name, biomes, 'terrahaul:trees/' + name, 'vegetal_decoration')
        with_conditions(f'terrahaul/neoforge/biome_modifier/trees/{name}.json', CONFIG_TREES)

    # dark forests keep their huge mushrooms
    configured('dark_forest_mushrooms', 'minecraft:random_boolean_selector', {
        "feature_true": ref('minecraft:huge_brown_mushroom'), "feature_false": ref('minecraft:huge_red_mushroom')})
    placed('dark_forest_mushrooms', 'terrahaul:dark_forest_mushrooms', tree_placement(count=0.35, scale=300, variation=1.0))
    add_features('trees/dark_forest_mushrooms', ['minecraft:dark_forest'], 'terrahaul:dark_forest_mushrooms', 'vegetal_decoration')
    with_conditions('terrahaul/neoforge/biome_modifier/trees/dark_forest_mushrooms.json', CONFIG_TREES)

    # remove vanilla trees
    remove_features('trees/remove_vanilla', '#minecraft:is_overworld', VANILLA_TREE_FEATURES, ['vegetal_decoration'])
    with_conditions('terrahaul/neoforge/biome_modifier/trees/remove_vanilla.json', CONFIG_TREES)

    # lush caves: vanilla root system, growing a Terrahaul azalea
    with open(os.path.join(VANILLA, 'worldgen', 'configured_feature', 'rooted_azalea_tree.json')) as f:
        rooted = json.load(f)
    rooted['config']['feature'] = ref(T + 'azalea')
    write('terrahaul/worldgen/configured_feature/rooted_azalea_tree.json', rooted)
    with open(os.path.join(VANILLA, 'worldgen', 'placed_feature', 'rooted_azalea_tree.json')) as f:
        rooted_placed = json.load(f)
    rooted_placed['feature'] = 'terrahaul:rooted_azalea_tree'
    write('terrahaul/worldgen/placed_feature/rooted_azalea_tree.json', rooted_placed)
    remove_features('trees/remove_vanilla_azalea', ['minecraft:lush_caves'], ['minecraft:rooted_azalea_tree'], ['vegetal_decoration'])
    with_conditions('terrahaul/neoforge/biome_modifier/trees/remove_vanilla_azalea.json', CONFIG_TREES)
    add_features('trees/rooted_azalea', ['minecraft:lush_caves'], 'terrahaul:rooted_azalea_tree', 'vegetal_decoration')
    with_conditions('terrahaul/neoforge/biome_modifier/trees/rooted_azalea.json', CONFIG_TREES)

    # saplings grown by players (see SaplingGrowthHandler): like vanilla trees they replace only air,
    # leaves, water and plants, so the blocks around a sapling are never destroyed
    sapling = dict(sapling=True)
    tree_config('sapling/oak', 'oak', {"small": 3, "medium": 4, "large": 1}, **sapling)
    tree_config('sapling/birch', 'birch', {"small": 3, "medium": 4, "large": 1}, **sapling)
    tree_config('sapling/spruce', 'spruce', {"small": 4, "medium": 4}, **sapling)
    tree_config('sapling/spruce_large', 'spruce', {"large": 1}, **sapling)
    tree_config('sapling/jungle', 'jungle', {"small": 3, "medium": 3}, vines=0.15, cocoa=0.1, **sapling)
    tree_config('sapling/jungle_large', 'jungle', {"large": 1}, vines=0.2, cocoa=0.05, **sapling)
    tree_config('sapling/acacia', 'acacia', {"small": 3, "medium": 4, "large": 1}, **sapling)
    tree_config('sapling/dark_oak', 'dark_oak', {"small": 3, "medium": 4, "large": 2}, **sapling)
    tree_config('sapling/cherry', 'cherry', {"small": 3, "medium": 4, "large": 1}, beehive=0.05, **sapling)
    tree_config('sapling/mangrove', 'mangrove', {"small": 3, "medium": 4}, **sapling)
    tree_config('sapling/azalea', 'azalea', {"small": 3, "medium": 3}, **sapling)


CONFIG_DETAILS = [{"type": "terrahaul:config", "key": "ground_details"}]
CONFIG_FORMATIONS = [{"type": "terrahaul:config", "key": "geological_formations"}]

GROUPS = {
    "forest": ["minecraft:forest", "minecraft:flower_forest"],
    "birch_forest": ["minecraft:birch_forest", "minecraft:old_growth_birch_forest"],
    "dark_forest": ["minecraft:dark_forest"],
    "taiga": ["minecraft:taiga", "minecraft:snowy_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:old_growth_pine_taiga",
              "minecraft:grove", "minecraft:windswept_forest"],
    "jungle": ["minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle"],
    "plains": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"],
    "savanna": ["minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna"],
    "desert": ["minecraft:desert"],
    "badlands": ["minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands"],
    "mountains": ["minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:stony_peaks", "minecraft:jagged_peaks",
                  "minecraft:frozen_peaks", "minecraft:snowy_slopes", "minecraft:stony_shore"],
    "wetland": ["minecraft:swamp", "minecraft:mangrove_swamp"],
    "cherry": ["minecraft:cherry_grove"],
    "water_edge": ["minecraft:river", "minecraft:frozen_river", "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore"],
    "snowy": ["minecraft:snowy_plains", "minecraft:ice_spikes"],
}


def state(block, **props):
    st = {"Name": block}
    if props:
        st["Properties"] = {k: str(v).lower() for k, v in props.items()}
    return st


def weighted(entries):
    return {"type": "minecraft:weighted_state_provider",
            "entries": [{"data": st, "weight": w} for st, w in entries]}


def facings_states(block, extra_list, base_weight=1, **fixed):
    """All four facings of a block, combined with each dict of extra properties in extra_list."""
    out = []
    for extra in extra_list:
        for facing in ["north", "east", "south", "west"]:
            out.append((state(block, facing=facing, **fixed, **extra[0]), extra[1] * base_weight))
    return out


def ground_predicate(on=None, water=False):
    """Air (or water) at the position, and one of `on` (tags prefixed with '#', else block ids) below."""
    preds = [{"type": "minecraft:matching_fluids", "fluids": "minecraft:water"} if water
             else {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}]
    if on:
        options = []
        for t in on:
            if t.startswith('#'):
                options.append({"type": "minecraft:matching_block_tag", "offset": [0, -1, 0], "tag": t[1:]})
            else:
                options.append({"type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": t})
        preds.append({"type": "minecraft:any_of", "predicates": options})
    return {"type": "minecraft:all_of", "predicates": preds}


def patch(name, provider, tries, spread, on_tags=None, water=False, y_spread=2):
    inner = {"feature": {"type": "minecraft:simple_block", "config": {"to_place": provider}},
             "placement": [{"type": "minecraft:block_predicate_filter", "predicate": ground_predicate(on_tags, water)}]}
    configured(name, 'minecraft:random_patch', {"tries": tries, "xz_spread": spread, "y_spread": y_spread, "feature": inner})


def detail_placement(count, density="ground_detail", scale=160, variation=0.8, clearing=-2.0, heightmap="MOTION_BLOCKING_NO_LEAVES"):
    return [
        {"type": "terrahaul:density_count", "count": count, "density": density, "scale": scale, "variation": variation, "clearing": clearing},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": heightmap},
        {"type": "minecraft:biome"},
    ]


def add_detail(name, groups, count, density="ground_detail", conditions=None, **kw):
    biomes = sorted({b for g in groups for b in GROUPS[g]})
    placed('details/' + name, 'terrahaul:details/' + name, detail_placement(count, density, **kw))
    add_features('details/' + name, biomes, 'terrahaul:details/' + name, 'vegetal_decoration')
    with_conditions(f'terrahaul/neoforge/biome_modifier/details/{name}.json', conditions or CONFIG_DETAILS)


SOIL = ["#minecraft:dirt"]
GROUND = ["#minecraft:dirt", "#minecraft:sand", "#minecraft:base_stone_overworld", "minecraft:gravel", "#minecraft:terracotta"]


def pebble_states(stones, wet=False):
    out = []
    for stone, w in stones:
        for count, cw in ((1, 4), (2, 3), (3, 2), (4, 1)):
            for facing in ["north", "east", "south", "west"]:
                out.append((state(f"terrahaul:{stone}_pebbles", count=count, facing=facing, waterlogged=wet), w * cw))
    return weighted(out)


def rock_states(stones):
    out = []
    for stone, w in stones:
        for variant in (0, 1):
            for facing in ["north", "east", "south", "west"]:
                out.append((state(f"terrahaul:{stone}_rock", variant=variant, facing=facing, waterlogged=False), w))
    return weighted(out)


def branch_states(woods, mossy_weight=1):
    out = []
    for wood, w in woods:
        block = "terrahaul:twigs" if wood == "twigs" else f"terrahaul:{wood}_branch"
        for shape in (0, 1, 2):
            for mossy, mw in ((False, 4), (True, mossy_weight)):
                for facing in ["north", "east", "south", "west"]:
                    out.append((state(block, shape=shape, mossy=mossy, facing=facing, waterlogged=False), w * mw))
    return weighted(out)


def litter_states(litters):
    out = []
    for name, w in litters:
        for facing in ["north", "east", "south", "west"]:
            out.append((state(f"terrahaul:{name}", facing=facing, waterlogged=False), w))
    return weighted(out)


def details():
    D = 'terrahaul:details/'
    # ---- pebbles -------------------------------------------------------------------------------
    pebble_sets = {
        "temperate": [("stone", 3), ("andesite", 2), ("mossy", 1)],
        "forest": [("stone", 2), ("mossy", 3), ("andesite", 1)],
        "taiga": [("stone", 3), ("mossy", 2), ("andesite", 2), ("granite", 1)],
        "mountain": [("andesite", 3), ("stone", 3), ("granite", 1), ("diorite", 1), ("tuff", 1)],
        "desert": [("sandstone", 4), ("stone", 1)],
        "badlands": [("red_sandstone", 4), ("sandstone", 1)],
        "savanna": [("stone", 2), ("granite", 2), ("sandstone", 1)],
        "river": [("stone", 3), ("andesite", 2), ("granite", 1), ("diorite", 1)],
    }
    for name, stones in pebble_sets.items():
        patch(f'details/pebbles_{name}', pebble_states(stones), 10, 5, GROUND)
    add_detail('pebbles_temperate', ["plains", "snowy"], 0.6)
    add_detail('pebbles_forest', ["forest", "birch_forest", "dark_forest", "jungle", "wetland", "cherry"], 0.8)
    add_detail('pebbles_taiga', ["taiga"], 1.4)
    add_detail('pebbles_mountain', ["mountains"], 3.0)
    add_detail('pebbles_desert', ["desert"], 1.2)
    add_detail('pebbles_badlands', ["badlands"], 1.4)
    add_detail('pebbles_savanna', ["savanna"], 1.0)
    add_detail('pebbles_river', ["water_edge"], 2.5)
    # submerged river pebbles
    patch('details/pebbles_underwater', pebble_states(pebble_sets["river"], wet=True), 14, 6, ["#minecraft:sand", "minecraft:gravel", "#minecraft:dirt", "minecraft:clay"], water=True, y_spread=1)
    add_detail('pebbles_underwater', ["water_edge"], 2.0, heightmap="OCEAN_FLOOR")

    # ---- medium rocks ----------------------------------------------------------------------------
    rock_sets = {
        "temperate": [("stone", 3), ("andesite", 2), ("mossy", 2)],
        "mountain": [("andesite", 3), ("stone", 3), ("granite", 1), ("diorite", 1), ("tuff", 1)],
        "desert": [("sandstone", 1)],
        "badlands": [("red_sandstone", 3), ("sandstone", 1)],
        "mossy": [("mossy", 3), ("stone", 1)],
    }
    for name, stones in rock_sets.items():
        patch(f'details/rocks_{name}', rock_states(stones), 3, 4, GROUND)
    add_detail('rocks_temperate', ["plains", "taiga", "savanna", "water_edge", "snowy"], 0.3, density="rocks")
    add_detail('rocks_mossy', ["forest", "birch_forest", "dark_forest", "jungle", "wetland"], 0.3, density="rocks")
    add_detail('rocks_mountain', ["mountains"], 1.0, density="rocks")
    add_detail('rocks_desert', ["desert"], 0.3, density="rocks")
    add_detail('rocks_badlands', ["badlands"], 0.5, density="rocks")

    # ---- boulders --------------------------------------------------------------------------------
    def boulder(name, rmin, rmax, material, moss=0.0, bury=0.35):
        configured(f'details/{name}', 'terrahaul:boulder', {
            "radius": {"type": "minecraft:uniform", "min_inclusive": rmin, "max_exclusive": rmax},
            "material": {"type": "minecraft:weighted_state_provider", "entries": [{"data": state(m), "weight": w} for m, w in material]},
            "moss": moss, "bury": bury})
    boulder('boulder_small', 1.2, 2.6, [("minecraft:stone", 3), ("minecraft:andesite", 2), ("minecraft:cobblestone", 1)])
    boulder('boulder_mossy', 1.5, 3.4, [("minecraft:stone", 3), ("minecraft:mossy_cobblestone", 2), ("minecraft:andesite", 1)], moss=0.5)
    boulder('boulder_large', 2.8, 5.0, [("minecraft:stone", 3), ("minecraft:andesite", 2), ("minecraft:granite", 1)], moss=0.15, bury=0.45)
    boulder('boulder_desert', 1.5, 3.5, [("minecraft:sandstone", 3), ("minecraft:smooth_sandstone", 1)])
    add_detail('boulder_small', ["mountains", "taiga", "snowy", "savanna"], 0.25, density="rocks", scale=300, variation=1.2)
    add_detail('boulder_field', ["mountains"], 1.0, density="rocks", scale=220, variation=1.6, clearing=0.35)
    add_detail('boulder_mossy', ["forest", "birch_forest", "dark_forest", "jungle", "taiga"], 0.12, density="rocks", scale=300, variation=1.2)
    add_detail('boulder_large', ["plains", "savanna", "mountains"], 0.035, density="rocks", scale=500, variation=1.0)
    add_detail('boulder_desert', ["desert", "badlands"], 0.06, density="rocks", scale=400, variation=1.0)
    # boulder_field reuses the small boulder configuration
    write('terrahaul/worldgen/placed_feature/details/boulder_field.json',
          {"feature": D + 'boulder_small', "placement": detail_placement(1.0, "rocks", scale=220, variation=1.6, clearing=0.35)})

    # ---- branches, twigs, pine cones -------------------------------------------------------------
    branch_sets = {
        "forest": [("oak", 3), ("birch", 1), ("twigs", 2)],
        "birch": [("birch", 3), ("twigs", 1)],
        "dark_oak": [("dark_oak", 3), ("oak", 1), ("twigs", 2)],
        "spruce": [("spruce", 3), ("twigs", 2)],
        "jungle": [("jungle", 3), ("twigs", 1)],
        "acacia": [("acacia", 3), ("twigs", 1)],
        "cherry": [("cherry", 3), ("twigs", 1)],
        "wetland": [("mangrove", 2), ("oak", 2), ("twigs", 1)],
        "plains": [("oak", 2), ("twigs", 2)],
    }
    for name, woods in branch_sets.items():
        patch(f'details/branches_{name}', branch_states(woods, mossy_weight=2 if name in ("dark_oak", "wetland", "jungle") else 1), 6, 6, SOIL)
    add_detail('branches_forest', ["forest"], 1.4)
    add_detail('branches_birch', ["birch_forest"], 1.2)
    add_detail('branches_dark_oak', ["dark_forest"], 1.6)
    add_detail('branches_spruce', ["taiga"], 1.4)
    add_detail('branches_jungle', ["jungle"], 1.2)
    add_detail('branches_acacia', ["savanna"], 0.35)
    add_detail('branches_cherry', ["cherry"], 0.7)
    add_detail('branches_wetland', ["wetland"], 0.6)
    add_detail('branches_plains', ["plains"], 0.12)
    cones = []
    for count, w in ((1, 4), (2, 3), (3, 2), (4, 1)):
        for facing in ["north", "east", "south", "west"]:
            cones.append((state("terrahaul:pine_cone", count=count, facing=facing, waterlogged=False), w))
    patch('details/pine_cones', weighted(cones), 8, 6, SOIL)
    add_detail('pine_cones', ["taiga"], 1.2)

    # ---- leaf litter -----------------------------------------------------------------------------
    litter_sets = {
        "forest": [("oak_leaf_litter", 4), ("birch_leaf_litter", 1), ("dry_leaf_litter", 1)],
        "birch": [("birch_leaf_litter", 4), ("dry_leaf_litter", 1)],
        "dark_oak": [("dark_oak_leaf_litter", 4), ("dry_leaf_litter", 1)],
        "spruce": [("spruce_needle_litter", 1)],
        "jungle": [("jungle_leaf_litter", 3), ("dry_leaf_litter", 1)],
        "acacia": [("acacia_leaf_litter", 2), ("dry_leaf_litter", 1)],
        "wetland": [("mangrove_leaf_litter", 2), ("oak_leaf_litter", 1)],
        "azalea": [("azalea_leaf_litter", 1)],
    }
    for name, litters in litter_sets.items():
        patch(f'details/litter_{name}', litter_states(litters), 24, 6, SOIL)
    add_detail('litter_forest', ["forest"], 2.2)
    add_detail('litter_birch', ["birch_forest"], 2.0)
    add_detail('litter_dark_oak', ["dark_forest"], 3.0)
    add_detail('litter_spruce', ["taiga"], 2.6)
    add_detail('litter_jungle', ["jungle"], 2.0)
    add_detail('litter_acacia', ["savanna"], 0.4)
    add_detail('litter_wetland', ["wetland"], 0.8)
    add_detail('litter_azalea', ["forest", "plains"], 0.08)

    # ---- bushes and low vegetation ---------------------------------------------------------------
    def bush_patch(name, entries, tries, spread):
        provider = {"type": "minecraft:weighted_state_provider", "entries": [{"data": state(b), "weight": w} for b, w in entries]}
        patch(f'details/{name}', provider, tries, spread, ["#minecraft:dirt", "#minecraft:sand", "#minecraft:terracotta", "minecraft:gravel"])
    bush_patch('bushes_forest', [("terrahaul:leafy_bush", 3), ("terrahaul:dense_shrub", 1)], 6, 5)
    bush_patch('bushes_dense', [("terrahaul:dense_shrub", 3), ("terrahaul:leafy_bush", 2)], 8, 5)
    bush_patch('bushes_alpine', [("terrahaul:alpine_shrub", 4), ("terrahaul:leafy_bush", 1)], 6, 5)
    bush_patch('bushes_dry', [("terrahaul:dry_shrub", 4), ("minecraft:dead_bush", 1)], 5, 6)
    bush_patch('bushes_savanna', [("terrahaul:dry_shrub", 2), ("terrahaul:leafy_bush", 2)], 5, 6)
    add_detail('bushes_forest', ["forest", "birch_forest", "plains", "cherry"], 1.0, density="vegetation", scale=200, variation=1.0)
    add_detail('bushes_dense', ["dark_forest", "jungle", "wetland"], 1.6, density="vegetation", scale=200, variation=0.8)
    add_detail('bushes_alpine', ["taiga", "mountains", "snowy"], 0.9, density="vegetation", scale=200, variation=1.0)
    add_detail('bushes_dry', ["desert", "badlands"], 0.5, density="vegetation", scale=260, variation=1.0)
    add_detail('bushes_savanna', ["savanna"], 0.9, density="vegetation", scale=260, variation=1.0)

    moss = {"type": "minecraft:weighted_state_provider", "entries": [{"data": state("minecraft:moss_carpet"), "weight": 1}]}
    patch('details/moss_patch', moss, 32, 5, SOIL, y_spread=1)
    add_detail('moss_patch', ["dark_forest", "jungle", "wetland", "taiga"], 0.5, density="vegetation", scale=180, variation=1.2)
    ferns = {"type": "minecraft:weighted_state_provider", "entries": [{"data": state("minecraft:fern"), "weight": 3}]}
    patch('details/fern_cluster', ferns, 24, 4, SOIL)
    add_detail('fern_cluster', ["forest", "dark_forest", "taiga", "jungle", "birch_forest"], 0.8, density="vegetation", scale=180, variation=1.2)
    mushrooms = {"type": "minecraft:weighted_state_provider", "entries": [{"data": state("minecraft:brown_mushroom"), "weight": 3},
                                                                        {"data": state("minecraft:red_mushroom"), "weight": 1}]}
    patch('details/mushroom_cluster', mushrooms, 12, 3, SOIL, y_spread=1)
    add_detail('mushroom_cluster', ["dark_forest", "taiga", "wetland"], 0.3, density="vegetation", scale=200, variation=1.0)

    # ---- fallen logs -----------------------------------------------------------------------------
    def fallen(wood, lmin, lmax, rot=0.25, moss=0.35, mushroom=0.1, stump=0.35):
        log = "minecraft:mangrove_log" if wood == "mangrove" else f"minecraft:{wood}_log"
        configured(f'details/fallen_log_{wood}', 'terrahaul:fallen_log', {
            "log": state(log, axis="x"), "stripped": state(f"minecraft:stripped_{wood}_log", axis="x"),
            "length": {"type": "minecraft:uniform", "min_inclusive": lmin, "max_inclusive": lmax},
            "rot": rot, "moss": moss, "mushroom": mushroom, "stump": stump})
    for wood in ["oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "mangrove", "cherry"]:
        fallen(wood, 4, 9 if wood in ("spruce", "jungle", "dark_oak") else 7)
    fallen_groups = [("oak", ["forest", "plains"], 0.25), ("birch", ["birch_forest", "forest"], 0.2), ("spruce", ["taiga"], 0.4),
                     ("jungle", ["jungle"], 0.3), ("acacia", ["savanna"], 0.05), ("dark_oak", ["dark_forest"], 0.45),
                     ("mangrove", ["wetland"], 0.1), ("cherry", ["cherry"], 0.12)]
    for wood, groups, count in fallen_groups:
        add_detail(f'fallen_log_{wood}', groups, count, density="forest", scale=300, variation=1.0)

    # ---- rock formations (Terrahaul worlds only: sites come from the terrain model) --------------
    configured('rock_formations', 'terrahaul:rock_formations', {})
    placed('rock_formations', 'terrahaul:rock_formations', [])
    add_features('rock_formations', '#minecraft:is_overworld', 'terrahaul:rock_formations', 'local_modifications')
    with_conditions('terrahaul/neoforge/biome_modifier/rock_formations.json', CONFIG_FORMATIONS)


def volcano_zone(zone, threshold=0.0):
    f = {"type": "terrahaul:volcano_zone", "zone": zone}
    if threshold:
        f["threshold"] = threshold
    return f


# first modifiers of every volcano feature: Terrahaul worlds only, and one cheap test per chunk
# that skips all chunks away from volcanoes
VOLCANO_GATE = [{"type": "terrahaul:terrahaul_only"}, volcano_zone("any")]


def volcano_tree_placement(count, zone, slope=7, scale=200, variation=0.5):
    return VOLCANO_GATE + [
        {"type": "terrahaul:density_count", "count": count, "density": "forest", "scale": scale,
         "variation": variation, "clearing": -2.0},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        volcano_zone(zone),
        {"type": "terrahaul:tree_line", "max_slope": slope},
        {"type": "minecraft:biome"},
    ]


def volcano_detail_placement(count, zone, density="vegetation", threshold=0.0, heightmap="MOTION_BLOCKING_NO_LEAVES",
                             scale=160, variation=0.6):
    return VOLCANO_GATE + [
        {"type": "terrahaul:density_count", "count": count, "density": density, "scale": scale,
         "variation": variation, "clearing": -2.0},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": heightmap},
        volcano_zone(zone, threshold),
        {"type": "minecraft:biome"},
    ]


def volcano_feature(name, biomes, placement, step='vegetal_decoration', conditions=None, feature=None):
    placed('volcano/' + name, feature or 'terrahaul:volcano/' + name, placement)
    add_features('volcano/' + name, biomes, 'terrahaul:volcano/' + name, step)
    with_conditions(f'terrahaul/neoforge/biome_modifier/volcano/{name}.json', conditions or CONFIG_DETAILS)


VOLCANO_FORESTS = {
    # group: (biomes, tree selector entries, default tree, extra trees per chunk,
    #         young tree selector entries, default young tree)
    'temperate': (['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:forest', 'minecraft:flower_forest',
                   'minecraft:birch_forest', 'minecraft:old_growth_birch_forest', 'minecraft:meadow', 'minecraft:windswept_hills',
                   'minecraft:windswept_gravelly_hills', 'minecraft:windswept_forest'],
                  [('birch', 0.25)], 'oak', 4.5, [('birch_young', 0.4)], 'oak_young'),
    'dark_forest': (['minecraft:dark_forest'], [('oak', 0.3)], 'dark_oak', 3.5, [], 'oak_young'),
    'taiga': (['minecraft:taiga', 'minecraft:snowy_taiga', 'minecraft:old_growth_spruce_taiga', 'minecraft:old_growth_pine_taiga',
               'minecraft:grove', 'minecraft:snowy_plains'],
              [('fir', 0.3), ('pine', 0.15)], 'spruce', 4.5, [], 'spruce_young'),
    'jungle': (['minecraft:jungle', 'minecraft:sparse_jungle', 'minecraft:bamboo_jungle'],
               [('jungle_giant', 0.2)], 'jungle_medium', 4.5, [], 'jungle_bush'),
    'savanna': (['minecraft:savanna', 'minecraft:savanna_plateau', 'minecraft:windswept_savanna'],
                [('oak_small_dry', 0.15)], 'acacia', 2.5, [], 'acacia_small'),
    'cherry': (['minecraft:cherry_grove'], [('birch', 0.1)], 'cherry', 3.5, [], 'cherry_young'),
}


def volcano_vegetation():
    """Ecology of volcanoes (see VolcanoZones): a denser forest on the fertile flanks, young trees
    and shrubs below the bare cone, burn scars of snags, charred logs and dead bushes, and loose
    volcanic rock on the cone. Every feature passes terrahaul:volcano_zone, so it only acts in
    Terrahaul worlds and agrees with the ground the surface rule lays."""
    T = 'terrahaul:tree/'
    forest_biomes = sorted({b for g in VOLCANO_FORESTS.values() for b in g[0]})

    # ---- living forest on fertile soil, young trees in the pioneer belt -----------------------
    for name, (biomes, entries, default, count, young_entries, young_default) in VOLCANO_FORESTS.items():
        selector(f'volcano/forest_{name}', [(T + t, c) for t, c in entries], T + default)
        volcano_feature(f'forest_{name}', biomes, volcano_tree_placement(count, "forest"), conditions=CONFIG_TREES)
        young = T + young_default
        if young_entries:
            selector(f'volcano/pioneer_{name}', [(T + t, c) for t, c in young_entries], young)
            young = f'terrahaul:volcano/pioneer_{name}'
        volcano_feature(f'pioneer_{name}', biomes, volcano_tree_placement(1.6, "pioneer", slope=9), conditions=CONFIG_TREES,
                        feature=young)

    # ---- burn scars ---------------------------------------------------------------------------
    # fire-killed snags (configured features of the tree overhaul)
    selector('volcano/burned_snags', [(T + 'burned_small', 0.4)], T + 'burned')
    volcano_feature('burned_snags', forest_biomes, volcano_tree_placement(5.0, "burn", slope=9), conditions=CONFIG_TREES)
    # charred trunks: dark oak bark, burned through to the bare wood in places, no moss or fungi
    configured('volcano/charred_log', 'terrahaul:fallen_log', {
        "log": state("minecraft:dark_oak_log", axis="x"), "stripped": state("minecraft:stripped_dark_oak_log", axis="x"),
        "length": {"type": "minecraft:uniform", "min_inclusive": 3, "max_inclusive": 8},
        "rot": 0.3, "moss": 0.0, "mushroom": 0.0, "stump": 0.5})
    volcano_feature('charred_log', forest_biomes, volcano_detail_placement(1.4, "burn", density="forest", scale=220))
    patch('volcano/dead_bushes', weighted([(state("minecraft:dead_bush"), 3), (state("terrahaul:dry_shrub"), 2)]), 6, 5,
          ["#minecraft:dirt", "minecraft:gravel"])
    volcano_feature('dead_bushes', forest_biomes, volcano_detail_placement(2.5, "burn"))
    # fireweed-like regrowth: drifts of allium in the scars
    patch('volcano/fireweed', weighted([(state("minecraft:allium"), 1)]), 12, 4, SOIL)
    volcano_feature('fireweed', forest_biomes, volcano_detail_placement(0.6, "burn", scale=220, variation=1.0))
    # the biomes' own grass and flowers grow on scorched soil too: clear most small plants (but
    # not the fireweed) from the cores of the scars afterwards, leaving a few dead bushes
    configured('volcano/scorch', 'minecraft:random_patch', {
        "tries": 48, "xz_spread": 6, "y_spread": 2,
        "feature": {"feature": {"type": "minecraft:simple_block", "config": {"to_place": weighted(
            [(state("minecraft:air"), 7), (state("minecraft:dead_bush"), 1)])}},
            "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:any_of", "predicates": [
                {"type": "minecraft:matching_blocks", "blocks": ["minecraft:short_grass", "minecraft:fern"]},
                {"type": "minecraft:all_of", "predicates": [
                    {"type": "minecraft:matching_block_tag", "tag": "minecraft:small_flowers"},
                    {"type": "minecraft:not", "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:allium"}}]}]}}]}})
    volcano_feature('scorch', forest_biomes, VOLCANO_GATE + [
        {"type": "minecraft:count", "count": 4},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
        volcano_zone("burn", 0.55),
        {"type": "minecraft:biome"},
    ], step='top_layer_modification')

    # ---- lush flanks --------------------------------------------------------------------------
    patch('volcano/ferns', weighted([(state("minecraft:fern"), 4), (state("minecraft:large_fern"), 1)]), 20, 4, SOIL)
    volcano_feature('ferns', forest_biomes, volcano_detail_placement(2.0, "forest", scale=200))
    patch('volcano/flowers', weighted([(state("minecraft:dandelion"), 2), (state("minecraft:poppy"), 2), (state("minecraft:oxeye_daisy"), 2),
                                       (state("minecraft:azure_bluet"), 2), (state("minecraft:cornflower"), 1),
                                       (state("minecraft:lily_of_the_valley"), 1)]), 16, 5, SOIL)
    volcano_feature('flowers', forest_biomes, volcano_detail_placement(0.8, "forest", scale=240, variation=1.0))
    patch('volcano/understory', weighted([(state("terrahaul:leafy_bush"), 3), (state("terrahaul:dense_shrub"), 2)]), 6, 5, SOIL)
    volcano_feature('understory', forest_biomes, volcano_detail_placement(1.5, "forest"))
    # pioneer belt: hardy shrubs on grass and ash
    patch('volcano/pioneer_shrubs', weighted([(state("terrahaul:alpine_shrub"), 3), (state("terrahaul:dry_shrub"), 1)]), 8, 5,
          ["#minecraft:dirt", "minecraft:gravel"])
    volcano_feature('pioneer_shrubs', forest_biomes, volcano_detail_placement(2.0, "pioneer"))

    # ---- the bare cone: lava bombs, rocks and scoria pebbles (any biome a cone reaches) ----------
    cone_ground = ["#minecraft:dirt", "minecraft:gravel", "minecraft:tuff", "minecraft:blackstone", "minecraft:basalt",
                   "minecraft:smooth_basalt"]
    configured('volcano/bomb', 'terrahaul:boulder', {
        "radius": {"type": "minecraft:uniform", "min_inclusive": 1.4, "max_exclusive": 3.6},
        "material": {"type": "minecraft:simple_state_provider", "state": state("minecraft:basalt", axis="y")},
        "moss": 0.0, "bury": 0.4})
    volcano_feature('bomb', '#minecraft:is_overworld', volcano_detail_placement(0.35, "cone", density="rocks", scale=300, variation=1.0))
    patch('volcano/rocks', rock_states([("blackstone", 3), ("tuff", 2)]), 4, 5, cone_ground)
    volcano_feature('rocks', '#minecraft:is_overworld', volcano_detail_placement(1.0, "cone", density="rocks"))
    patch('volcano/pebbles', pebble_states([("blackstone", 3), ("tuff", 2), ("deepslate", 1)]), 10, 5, cone_ground)
    volcano_feature('pebbles', '#minecraft:is_overworld', volcano_detail_placement(1.6, "cone", density="ground_detail"))


CONFIG_ORES = [{"type": "terrahaul:config", "key": "ore_depth_fix"}]


def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}


def trapezoid(lo, hi, plateau=0):
    return {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}, "plateau": plateau}


def layer(name, feature, count, height, step, biomes='#minecraft:is_overworld', rarity=None, extra=None):
    """A resource layer for Terrahaul's deep (Y -512..-64) or high (Y 320+) zone. It reuses a vanilla
    configured feature and only places in Terrahaul worlds (terrahaul:terrahaul_only)."""
    placement = []
    if rarity:
        placement.append({"type": "minecraft:rarity_filter", "chance": rarity})
    if count != 1:
        placement.append({"type": "minecraft:count", "count": count})
    placement += [{"type": "minecraft:in_square"}, {"type": "minecraft:height_range", "height": height}]
    placement += extra or []
    placement += [{"type": "terrahaul:terrahaul_only"}, {"type": "minecraft:biome"}]
    placed(f'resources/{name}', feature, placement)
    add_features(f'resources/{name}', biomes, f'terrahaul:resources/{name}', step)
    with_conditions(f'terrahaul/neoforge/biome_modifier/resources/{name}.json', CONFIG_ORES)


def resources():
    """Ores and cave decoration for the zones a vanilla world does not have. Vanilla features keep
    their vanilla depths through HeightRangePlacementMixin (also behind ore_depth_fix)."""
    ores = 'underground_ores'
    # ---- deep zone: Y -512..-64, all deepslate ---------------------------------------------------
    layer('deep_diamond', 'minecraft:ore_diamond_small', 6, trapezoid(-500, -100), ores)
    layer('deep_diamond_large', 'minecraft:ore_diamond_large', 1, uniform(-500, -80), ores, rarity=5)
    layer('deep_diamond_buried', 'minecraft:ore_diamond_buried', 3, trapezoid(-480, -120), ores)
    layer('deep_redstone', 'minecraft:ore_redstone', 6, uniform(-500, -64), ores)
    layer('deep_gold', 'minecraft:ore_gold_buried', 5, trapezoid(-480, -96), ores)
    layer('deep_iron', 'minecraft:ore_iron', 8, uniform(-510, -64), ores)
    layer('deep_lapis', 'minecraft:ore_lapis_buried', 3, trapezoid(-400, -64), ores)
    layer('deep_copper', 'minecraft:ore_copper_small', 4, uniform(-300, -64), ores)
    layer('deep_tuff', 'minecraft:ore_tuff', 3, uniform(-510, -64), ores)
    layer('deep_granite', 'minecraft:ore_granite', 1, uniform(-510, -64), ores)
    layer('deep_geode', 'minecraft:amethyst_geode', 1, uniform(-490, -80), 'local_modifications', rarity=36)
    layer('deep_lava_spring', 'minecraft:spring_lava_overworld', 8, uniform(-500, -80), 'fluid_springs')
    layer('deep_water_spring', 'minecraft:spring_water', 4, uniform(-400, -64), 'fluid_springs')
    layer('deep_glow_lichen', 'minecraft:glow_lichen', 40, uniform(-510, -64), 'vegetal_decoration')
    # ---- high zone: Y 320+, the cores of Terrahaul's mountains -----------------------------------
    layer('high_coal', 'minecraft:ore_coal', 14, uniform(320, 1400), ores)
    layer('high_iron', 'minecraft:ore_iron', 9, trapezoid(320, 1100), ores)
    layer('high_copper', 'minecraft:ore_copper_small', 6, uniform(320, 900), ores)
    layer('high_gold', 'minecraft:ore_gold', 2, uniform(480, 1700), ores)
    layer('high_emerald', 'minecraft:ore_emerald', 40, uniform(320, 1800), ores, biomes='#minecraft:is_mountain')
    layer('high_lapis', 'minecraft:ore_lapis', 1, uniform(700, 1900), ores, rarity=2)
    layer('high_water_spring', 'minecraft:spring_water', 10, uniform(320, 1600), 'fluid_springs')


def ruins():
    # ruined houses, signposts and lamp posts beside trails, sunken ruins on mid-depth sea floors.
    # Sites come from the terrain model (no-op outside Terrahaul worlds); every overworld chunk runs
    # the feature, so ruins spanning chunk borders are always completed. Trail types follow the
    # trails config at runtime (there is no data-pack condition for it).
    configured('ruins', 'terrahaul:ruins', {})
    placed('ruins', 'terrahaul:ruins', [])
    add_features('ruins', '#minecraft:is_overworld', 'terrahaul:ruins', 'surface_structures')


def main():
    terrain_cleanup()
    water()
    oceans()
    trees()
    details()
    volcano_vegetation()
    resources()
    ruins()


if __name__ == '__main__':
    import sys
    VANILLA = sys.argv[1]
    main()
